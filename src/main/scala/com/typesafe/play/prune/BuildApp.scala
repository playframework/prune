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

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    def newAppBuild(playBuildId: UUID): (UUID, AppBuildRecord) = {
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

    def lastBuild(): Option[(UUID, AppBuildRecord)] = {
      for {
        persistentState <- PrunePersistentState.read
        lastAppBuildId <- persistentState.lastAppBuilds.get(appName)
        lastAppBuildRecord <- AppBuildRecord.read(lastAppBuildId)
      } yield (lastAppBuildId, lastAppBuildRecord)
    }

    def buildSuccessAndFailuresForCommit(): (Int, Int) = {
      Records.iteratorAll[AppBuildRecord](Paths.get(ctx.dbHome, "app-builds")).foldLeft((0, 0)) {
        case (acc@(successes, failures), (uuid, record)) if (record.appsCommit == appsCommit) =>
          PlayBuildRecord.read(record.playBuildId).fold {
            // If we can't load the Play build then we don't know if the build for that Play commit failed
            acc
          } { playBuildRecord =>
            if (playBuildRecord.playCommit == playCommit) {
              if (record.successfulBuild) (successes+1, failures) else (successes, failures+1)
            } else acc
          }
        case (acc, _) => acc
      }
    }

    val optPlayBuild: Option[(UUID, PlayBuildRecord)] = BuildPlay.buildPlay(playBranch = playBranch, playCommit = playCommit)

    optPlayBuild.fold[Option[(UUID, AppBuildRecord)]] {
      println(s"No Play build: skipping build of app $description")
      None
    } {
      case (playBuildId, playBuildRecord) =>

        def buildUnlessTooManyFailures(): Option[(UUID, AppBuildRecord)] = {
          val (successes, failures) = buildSuccessAndFailuresForCommit()
          if (failures - successes >= ctx.args.maxBuildFailures) {
            println(s"Too many previous build failures for app $description ($successes successes, $failures failures), aborting build")
            None
          } else {
            Some(newAppBuild(playBuildId))
          }
        }

        if (!playBuildRecord.successfulBuild) {
          println(s"Play build $playBuildId was unsuccessful: skipping build of app $description")
          None
        } else {
          lastBuild().fold[Option[(UUID, AppBuildRecord)]] {
            println(s"No existing app build record for $description")
            buildUnlessTooManyFailures()
          } {
            case (lastAppBuildId, lastAppBuildRecord) =>
              // We have a previously built app. Can we use it?
              if (lastAppBuildRecord.playBuildId != playBuildId) {
                println("Need new app build: Play build has changed")
                buildUnlessTooManyFailures()
              } else if (lastAppBuildRecord.appsCommit != appsCommit) {
                println("Need new app build: apps commit has changed")
                buildUnlessTooManyFailures()
              } else if (!Files.exists(Paths.get(ctx.appsHome, appName, "target/universal/stage/bin", appName))) {
                println("Need new app build: app binary not found")
                buildUnlessTooManyFailures()
              } else {
                println(s"App $description already built: ${lastAppBuildId}")
                Some((lastAppBuildId, lastAppBuildRecord))
              }
          }
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

    // Scan the Play repo to get the current Play version (assume this is what we need)
    val playVersion: String = PlayVersion.readPlayVersion()

    val buildCommands: Seq[Command] = Seq(
      Command(
        "sbt",
        Seq("-Dsbt.ivy.home=<ivy.home>", "-Dplay.version="+playVersion, ";clean;stage"),
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