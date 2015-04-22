/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.io._
import java.nio.file._
import java.util.{ List => JList, Map => JMap, UUID }
import java.util.concurrent.TimeUnit
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.apache.commons.exec._
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib._
import org.eclipse.jgit.revwalk._
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.joda.time._
import scala.collection.JavaConversions
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import Exec._
import PruneGit._

object BuildPlay {
  def buildPlay(playBranch: String, playCommit: String)(implicit ctx: Context): Option[(UUID, PlayBuildRecord)] = {

    val description = s"${playCommit.substring(0, 7)} [$playBranch]"

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    def newPlayBuild(): (UUID, PlayBuildRecord) = {
      val newPlayBuildId = UUID.randomUUID()
      println(s"Starting new Play $description build: $newPlayBuildId")

      gitCheckout(
        localDir = ctx.playHome,
        branch = playBranch,
        commit = playCommit)

      val executions: Seq[Execution] = buildPlayDirectly(errorOnNonZeroExit = false)
      val newPlayBuildRecord = PlayBuildRecord(
        pruneInstanceId = ctx.pruneInstanceId,
        playCommit = playCommit,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = executions
      )
      PlayBuildRecord.write(newPlayBuildId, newPlayBuildRecord)
      val oldPersistentState: PrunePersistentState = PrunePersistentState.readOrElse
      PrunePersistentState.write(oldPersistentState.copy(lastPlayBuild = Some(newPlayBuildId)))
      (newPlayBuildId, newPlayBuildRecord)
    }

    def lastBuild(): Option[(UUID, PlayBuildRecord)] = {
      for {
        persistentState <- PrunePersistentState.read
        lastPlayBuildId <- persistentState.lastPlayBuild
        lastPlayBuildRecord <- PlayBuildRecord.read(lastPlayBuildId)
      } yield (lastPlayBuildId, lastPlayBuildRecord)
    }

    def buildSuccessAndFailuresForCommit(): (Int, Int) = {
      Records.iteratorAll[PlayBuildRecord](Paths.get(ctx.dbHome, "play-builds")).foldLeft((0, 0)) {
        case ((successes, failures), (uuid, record)) if (record.playCommit == playCommit) =>
          if (record.successfulBuild) (successes+1, failures) else (successes, failures+1)
        case (acc, _) => acc
      }
    }

    def buildUnlessTooManyFailures(): Option[(UUID, PlayBuildRecord)] = {
      val (successes, failures) = buildSuccessAndFailuresForCommit()
      if (failures - successes >= ctx.args.maxBuildFailures) {
        println(s"Too many previous build failures for ${playCommit.substring(0,7)} ($successes successes, $failures failures), aborting build")
        None
      } else {
        Some(newPlayBuild())
      }
    }

    lastBuild().fold[Option[(UUID, PlayBuildRecord)]] {
      println("No current build for Play")
      buildUnlessTooManyFailures()
    } {
      case (lastPlayBuildId, lastPlayBuildRecord) =>
        // We hava previously built version of Play. Can we use it?
        if (lastPlayBuildRecord.playCommit != playCommit) {
          println(s"New Play build needed: Play commit has changed to ${playCommit.substring(0,7)}")
          buildUnlessTooManyFailures()
        } else if (Files.exists(localIvyRepository)) {
          println("New Play build needed: Local Ivy repository is missing")
          buildUnlessTooManyFailures()
        } else {
          println("Using existing Play build")
          Some((lastPlayBuildId, lastPlayBuildRecord))
        }
    }
  }

  private def localIvyRepository(implicit ctx: Context): Path = {
    val ivyHome: String = ctx.config.getString("ivy.home")
    Paths.get(ivyHome).resolve("local")
  }

  def buildPlayDirectly(errorOnNonZeroExit: Boolean = true)(implicit ctx: Context): Seq[Execution] = {

    // While we're building there won't be a current Play build for this app
    val oldPersistentState: PrunePersistentState = PrunePersistentState.readOrElse
    PrunePersistentState.write(oldPersistentState.copy(lastPlayBuild = None))

    // Clear target directories and local Ivy repository to ensure an isolated build
    Seq(
      localIvyRepository,
      Paths.get(ctx.playHome, "framework/target"),
      Paths.get(ctx.playHome, "framework/project/target")
    ) foreach { p =>
      if (Files.exists(p)) {
        FileUtils.deleteDirectory(p.toFile)
      }
    }

    val buildCommands: Seq[Command] = Seq(
      Command(
        program = "./build",
        args = Seq("-Dsbt.ivy.home=<ivy.home>", "publish-local"),
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "LANG" -> "en_US.UTF-8"
        ),
        workingDir = "<play.home>/framework"
      )
    )

    buildCommands.map(run(_, Pump, errorOnNonZeroExit = errorOnNonZeroExit))
  }

}