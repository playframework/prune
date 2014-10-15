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
    appName: String)(implicit ctx: Context): AppBuildRecord = {

    BuildPlay.buildPlay(playBranch = playBranch, playCommit = playCommit)

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val lastPlayBuildId: UUID = PrunePersistentState.read.flatMap(_.lastPlayBuild).getOrElse {
      sys.error("Play must be built before tests can be built")
    }

    val lastAppBuildId: Option[UUID] = PrunePersistentState.read.flatMap(_.lastAppBuilds.get(appName))
    val lastAppBuildRecord: Option[AppBuildRecord] = lastAppBuildId.flatMap(AppBuildRecord.read)
    val reasonsToBuild: Seq[String] = lastAppBuildRecord.fold(Seq("No existing app build record")) { buildRecord =>
      val differentPlayBuild = if (buildRecord.playBuildId == lastPlayBuildId) Seq() else Seq("Play build has changed")
      val differentCommit = if (buildRecord.appsCommit == appsCommit) Seq() else Seq("Apps commit has changed")
      val differentJavaVersion = if (buildRecord.javaVersionExecution.stderr == javaVersionExecution.stderr) Seq() else Seq("Java version has changed")
      val testBinary = Paths.get(ctx.appsHome, appName, "target/universal/stage/bin", appName)
      val missingTestBinary = if (Files.exists(testBinary)) Seq() else Seq("App binary is missing")
      // TODO: Check previous build commands are OK
      differentPlayBuild ++ differentCommit ++ differentJavaVersion ++ missingTestBinary
    }

    if (reasonsToBuild.isEmpty) {
      println(s"App $appName already built: ${lastAppBuildId.get}")
      lastAppBuildRecord.get
    } else {
      val newAppBuildId = UUID.randomUUID()
      println(s"Starting build for app $appName $newAppBuildId: "+(reasonsToBuild.mkString(", ")))

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
        playBuildId = lastPlayBuildId,
        appsCommit = appsCommit,
        appName = appName,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = buildExecutions
      )
      AppBuildRecord.write(newAppBuildId, newAppBuildRecord)
      val oldPersistentState = PrunePersistentState.readOrElse
      PrunePersistentState.write(oldPersistentState.copy(lastAppBuilds = oldPersistentState.lastAppBuilds.updated(appName, newAppBuildId)))
      newAppBuildRecord
    }

  }

  def buildAppDirectly(appName: String)(implicit ctx: Context): Seq[Execution] = {

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
        workingDir = s"<apps.dir>/$appName",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "LANG" -> "en_US.UTF-8"
        )
      )
    )

    buildCommands.map(run(_, Pump))
  }

}