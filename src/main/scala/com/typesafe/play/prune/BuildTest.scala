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

object BuildTest {
  def buildTestProject(
    playBranch: String,
    playCommit: String,
    testsBranch: String,
    testsCommit: String,
    testProject: String)(implicit ctx: Context): TestBuildRecord = {

    BuildPlay.buildPlay(playBranch = playBranch, playCommit = playCommit)

    val buildCommands: Seq[Command] = Seq(
      Command(
        "sbt",
        Seq("-Dsbt.ivy.home=<ivy.home>", "stage"),
        workingDir = s"<tests.home>/$testProject",
        env = Map(
          "JAVA_HOME" -> "<java8.home>"
        )
      )
    )

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val lastPlayBuildId: UUID = PrunePersistentState.read.flatMap(_.lastPlayBuild).getOrElse {
      sys.error("Play must be built before tests can be built")
    }

    val lastTestBuildId: Option[UUID] = PrunePersistentState.read.flatMap(_.lastTestBuilds.get(testProject))
    val lastTestBuildRecord: Option[TestBuildRecord] = lastTestBuildId.flatMap(TestBuildRecord.read)
    val reasonsToBuild: Seq[String] = lastTestBuildRecord.fold(Seq("No existing build record")) { buildRecord =>
      val differentPlayBuild = if (buildRecord.playBuildId == lastPlayBuildId) Seq() else Seq("Play build has changed")
      val differentCommit = if (buildRecord.testsCommit == testsCommit) Seq() else Seq("Tests commit has changed")
      val differentJavaVersion = if (buildRecord.javaVersionExecution.stderr == javaVersionExecution.stderr) Seq() else Seq("Java version has changed")
      val testBinary = Paths.get(ctx.testsHome, testProject, "target/universal/stage/bin", testProject)
      val missingTestBinary = if (Files.exists(testBinary)) Seq() else Seq("Test binary is missing")
      // TODO: Check previous build commands are OK
      differentPlayBuild ++ differentCommit ++ differentJavaVersion ++ missingTestBinary
    }

    if (reasonsToBuild.isEmpty) {
      println(s"Test $testProject already built: ${lastTestBuildId.get}")
      lastTestBuildRecord.get
    } else {
      val newTestBuildId = UUID.randomUUID()
      println(s"Starting build for $testProject test $newTestBuildId: "+(reasonsToBuild.mkString(", ")))

      gitCheckout(
        localDir = ctx.testsHome,
        branch = testsBranch,
        commit = testsCommit)

      // Clear local target directory to ensure an isolated build
      {
        val targetDir = Paths.get(ctx.testsHome, testProject, "target")
        if (Files.exists(targetDir)) {
          FileUtils.deleteDirectory(targetDir.toFile)
        }
      }

      val buildExecutions = buildCommands.map(run(_, Pump))

      val newTestBuildRecord = TestBuildRecord(
        playBuildId = lastPlayBuildId,
        testsCommit = testsCommit,
        testProject = testProject,
        javaVersionExecution = javaVersionExecution,
        buildExecutions = buildExecutions
      )
      TestBuildRecord.write(newTestBuildId, newTestBuildRecord)
      val oldPersistentState = PrunePersistentState.read.get
      PrunePersistentState.write(oldPersistentState.copy(lastTestBuilds = oldPersistentState.lastTestBuilds.updated(testProject, newTestBuildId)))
      newTestBuildRecord
    }

  }
}