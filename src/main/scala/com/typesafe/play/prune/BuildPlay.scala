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
  def buildPlay(playBranch: String, playCommit: String)(implicit ctx: Context): PlayBuildRecord = {

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val lastPlayBuildId: Option[UUID] = PrunePersistentState.read.flatMap(_.lastPlayBuild)
    val lastPlayBuildRecord: Option[PlayBuildRecord] = lastPlayBuildId.flatMap(PlayBuildRecord.read)
    val reasonsToBuild: Seq[String] = lastPlayBuildRecord.fold(Seq("No existing build record")) { buildRecord =>
      val differentCommit = if (buildRecord.playCommit == playCommit) Seq() else Seq(s"Play commit has changed to ${playCommit.substring(0,7)}")
      val differentJavaVersion = if (buildRecord.javaVersionExecution.stderr == javaVersionExecution.stderr) Seq() else Seq("Java version has changed")
      val emptyIvyDirectory = if (Files.exists(localIvyRepository)) Seq() else Seq("Local Ivy repository is missing")
      // TODO: Check previous build commands are OK
      differentCommit ++ differentJavaVersion ++ emptyIvyDirectory
    }

    if (reasonsToBuild.isEmpty) {
      println(s"Play already built: ${lastPlayBuildId.get}")
      lastPlayBuildRecord.get
    } else {
      val newPlayBuildId = UUID.randomUUID()
      println(s"Starting new Play build $newPlayBuildId: "+(reasonsToBuild.mkString(", ")))

      gitCheckout(
        localDir = ctx.playHome,
        branch = playBranch,
        commit = playCommit)

      val executions: Seq[Execution] = buildPlayDirectly()
      val newPlayBuildRecord = PlayBuildRecord(
        pruneInstanceId = ctx.pruneInstanceId,
        playCommit = playCommit,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = executions
      )
      PlayBuildRecord.write(newPlayBuildId, newPlayBuildRecord)
      val oldPersistentState: PrunePersistentState = PrunePersistentState.readOrElse
      PrunePersistentState.write(oldPersistentState.copy(lastPlayBuild = Some(newPlayBuildId)))
      newPlayBuildRecord
    }
  }

  private def localIvyRepository(implicit ctx: Context): Path = {
    val ivyHome: String = ctx.config.getString("ivy.home")
    Paths.get(ivyHome).resolve("local")
  }

  def buildPlayDirectly()(implicit ctx: Context): Seq[Execution] = {
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

    buildCommands.map(run(_, Pump))
  }

}