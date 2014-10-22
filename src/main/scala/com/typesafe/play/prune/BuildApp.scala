/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.UUID
import org.apache.commons.io.FileUtils

import Exec._
import PruneGit._

object BuildApp {
  def buildApp(
    playBranch: String,
    playCommit: String,
    appsBranch: String,
    appsCommit: String,
    appName: String)(implicit ctx: Context): Option[(UUID, AppBuildRecord)] = {

    val description = s"$appName for Play ${playCommit.substring(0, 7)} [$playBranch]"

    val (playBuildId, playBuildRecord) = BuildPlay.buildPlay(playBranch = playBranch, playCommit = playCommit)

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    def newAppBuild(): (UUID, AppBuildRecord) = {
      val newAppBuildId = UUID.randomUUID()
      println(s"Starting new app $description build: $newAppBuildId")

      gitCheckout(
        localDir = ctx.appsHome,
        branch = appsBranch,
        commit = appsCommit)

      // Clear local target directory to ensure an isolated build
      {
        val targetDir = Paths.get(ctx.appsHome, appName, "target")
        if (Files.exists(targetDir)) {
          FileUtils.deleteDirectory(targetDir.toFile)
        }
      }

      val buildExecutions = buildAppDirectly(appName)

      val newAppBuildRecord = AppBuildRecord(
        playBuildId = playBuildId,
        appsCommit = appsCommit,
        appName = appName,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = buildExecutions
      )
      AppBuildRecord.write(newAppBuildId, newAppBuildRecord)
      val oldPersistentState = PrunePersistentState.readOrElse
      PrunePersistentState.write(oldPersistentState.copy(lastAppBuilds = oldPersistentState.lastAppBuilds.updated(appName, newAppBuildId)))
      (newAppBuildId, newAppBuildRecord)
    }

    if (!playBuildRecord.successfulBuild) {
      println(s"Play build $playBuildId was unsuccessful: skipping build of app $description")
      None
    } else {
      val o: Option[(UUID, AppBuildRecord)] = for {
        persistentState <- PrunePersistentState.read
        lastAppBuildId <- persistentState.lastAppBuilds.get(appName)
        lastAppBuildRecord <- AppBuildRecord.read(lastAppBuildId)
      } yield {
        val reasonsToBuild: Seq[String] = {
          val differentPlayBuild = if (lastAppBuildRecord.playBuildId == playBuildId) Seq() else Seq("Play build has changed")
          val differentCommit = if (lastAppBuildRecord.appsCommit == appsCommit) Seq() else Seq("Apps commit has changed")
          val differentJavaVersion = if (lastAppBuildRecord.javaVersionExecution.stderr == javaVersionExecution.stderr) Seq() else Seq("Java version has changed")
          val testBinary = Paths.get(ctx.appsHome, appName, "target/universal/stage/bin", appName)
          val missingTestBinary = if (Files.exists(testBinary)) Seq() else Seq("App binary is missing")
          // TODO: Check previous build commands are OK
          differentPlayBuild ++ differentCommit ++ differentJavaVersion ++ missingTestBinary
        }
        if (reasonsToBuild.isEmpty) {
          println(s"App $description already built: ${lastAppBuildId}")
          (lastAppBuildId, lastAppBuildRecord)
        } else {
          println("Can't use existing app build: " + (reasonsToBuild.mkString(", ")))
          newAppBuild()
        }
      }
      o.orElse {
        println(s"No existing build record for Play, skipping build of app $description")
        Some(newAppBuild())
      }
    }
  }

  def buildAppDirectly(appName: String, errorOnNonZeroExit: Boolean = false)(implicit ctx: Context): Seq[Execution] = {

    // While we're building there won't be a current build for this app
    val oldPersistentState = PrunePersistentState.readOrElse
    PrunePersistentState.write(oldPersistentState.copy(lastAppBuilds = oldPersistentState.lastAppBuilds - appName))

    // Clear local target directory to ensure an isolated build
    val targetDir = Paths.get(ctx.appsHome, appName, "target")
    if (Files.exists(targetDir)) {
      FileUtils.deleteDirectory(targetDir.toFile)
    }

    val buildCommands: Seq[Command] = Seq(
      Command(
        "sbt",
        Seq("-Dsbt.ivy.home=<ivy.home>", "stage"),
        workingDir = s"<apps.home>/$appName",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "LANG" -> "en_US.UTF-8"
        )
      )
    )

    buildCommands.map(run(_, Pump, errorOnNonZeroExit = errorOnNonZeroExit))
  }

}