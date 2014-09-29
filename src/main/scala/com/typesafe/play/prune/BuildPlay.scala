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
  def buildPlay(playCommit: String)(implicit ctx: Context): PlayBuildRecord = {

    def playBuildRecordPath(id: UUID): Path = {
      Paths.get(ctx.config.getString("db-repo.local-dir"), "builds", id.toString+".json")
    }
    def writePlayBuildRecord(id: UUID, record: PlayBuildRecord): Unit = {
      println(s"Writing Play build record $id")
      Records.writeFile(playBuildRecordPath(id), record)
    }
    def readPlayBuildRecord(id: UUID): Option[PlayBuildRecord] = {
      Records.readFile[PlayBuildRecord](playBuildRecordPath(id))
    }

    val buildCommands: Seq[Command] = Seq(
      Command(
        program = "./build",
        args = Seq("-Dsbt.ivy.home=<ivy.home>", "publish-local"),
        env = Map(
          "JAVA_HOME" -> "<java8.home>"
        ),
        workingDir = "<play.home>/framework"
      )
    )

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val ivyHome: String = ctx.config.getString("ivy.home")
    val localIvyRepository: Path = Paths.get(ivyHome).resolve("local")
    // println(Paths.get(ivyHome).resolve("local"))
    // println(Files.exists(Paths.get(ivyHome).resolve("local")))

    val lastPlayBuildId: Option[UUID] = PrunePersistentState.read.flatMap(_.lastPlayBuild)
    val lastPlayBuildRecord: Option[PlayBuildRecord] = lastPlayBuildId.flatMap(readPlayBuildRecord)
    val reasonsToBuild: Seq[String] = lastPlayBuildRecord.fold(Seq("No existing build record")) { buildRecord =>
      val differentCommit = if (buildRecord.playCommit == playCommit) Seq() else Seq("Play commit has changed")
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

      gitCheckout(ctx.playRepoConfig, playCommit)

      // Clear local Ivy repository to ensure an isolated build
      if (Files.exists(localIvyRepository)) {
        FileUtils.deleteDirectory(localIvyRepository.toFile)
      }

      val executions: Seq[Execution] = buildCommands.map(run(_, Pump))
      val newPlayBuildRecord = PlayBuildRecord(
        pruneInstanceId = ctx.pruneInstanceId,
        playCommit = playCommit,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = executions
      )
      writePlayBuildRecord(newPlayBuildId, newPlayBuildRecord)
      PrunePersistentState.write(PrunePersistentState.read.get.copy(lastPlayBuild = Some(newPlayBuildId)))
      newPlayBuildRecord
    }
  }
}