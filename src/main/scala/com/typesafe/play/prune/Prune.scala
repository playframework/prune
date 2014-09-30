/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import com.typesafe.config.{ Config, ConfigFactory }
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
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scopt.OptionParser

import Exec._
import PruneGit._

case class TestTask(
  info: TestTaskInfo,
  playBranch: String,
  testsBranch: String
)

case class TestTaskInfo(
  testName: String,
  playCommit: String,
  testsCommit: String,
  testProject: String
)

object Prune {

  def main(rawArgs: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Args]("prune") {
      opt[String]("config-file") action { (s, c) =>
        c.copy(configFile = Some(s))
      }
      opt[Unit]("skip-fetches") action { (_, c) =>
        c.copy(dbFetch = false, playFetch = false, testsFetch = false)
      }
      opt[Unit]("skip-db-fetch") action { (_, c) =>
        c.copy(dbFetch = false)
      }
      opt[Unit]("skip-play-fetch") action { (_, c) =>
        c.copy(playFetch = false)
      }
      opt[Unit]("skip-tests-fetch") action { (_, c) =>
        c.copy(testsFetch = false)
      }
    }
    val args = parser.parse(rawArgs, Args()).getOrElse(noReturnExit(1))

    val defaultConfig = ConfigFactory.load().getConfig("com.typesafe.play.prune")
    val userConfigFile = Paths.get(args.configFile.getOrElse(defaultConfig.getString("defaultUserConfig")))
    if (Files.notExists(userConfigFile)) {
      println(s"""
        |Please provide a Prune configuration file at $userConfigFile. You can override
        |the configuration file location with --config-file=<path>. The file should
        |include an instance id for this instance of Prune. An example configuration
        |file with a new instance id is shown below. You will be able to use Prune once
        |you've added a configuration file at $userConfigFile.
        |
        |# The UUID used to identify this instance of Prune in test records.
        |pruneInstanceId = ${UUID.randomUUID()}
        |
        |# The location of Java 8 on the system.
        |#java8.home = /usr/lib/jvm/java-8-oracle/jre""".stripMargin)
      System.exit(1)
    }
    val userConfig: Config = ConfigFactory.parseFile(userConfigFile.toFile)
    val config = userConfig.withFallback(defaultConfig)

    if (!config.hasPath("pruneInstanceId")) {
      println(s"""
        |Please provide a value for "pruneInstanceId" in your configuration file at
        |$userConfigFile. An example value with a randomly generated UUID is shown
        |below.
        |
        |# The UUID used to identify this instance of Prune in test records.
        |pruneInstanceId = ${UUID.randomUUID()}
        |""".stripMargin)
      System.exit(1)
    }

    if (!config.hasPath("java8.home")) {
      println(s"""
        |Please provide a value for "java8.home" in your configuration file at
        |$userConfigFile.
        |
        |# The location of Java 8 on the system.
        |#java8.home = /usr/lib/jvm/java-8-oracle/jre
        |""".stripMargin)
      System.exit(1)
    }

    val ctx = Context(
      args = args,
      config = config
    )

    main(ctx)
  }

  private def noReturnExit(code: Int): Nothing = {
    System.exit(code)
    throw new Exception("This code is never reached, but is used to give the method a type of Nothing")
  }

  def main(implicit ctx: Context): Unit = {

    val playBranches = ctx.playTests.map(_.playBranch).distinct
    val testsBranches = ctx.playTests.map(_.testsBranch).distinct

    println(s"Prune instance id is ${ctx.pruneInstanceId}")

    {
      def fetch(desc: String, switch: Boolean, remote: String, branches: Seq[String], localDir: String): Unit = {
        if (switch) {
          println(s"Fetching $desc from remote")
          gitSync(
            remote = remote,
            branches = branches,
            localDir = localDir)
        } else {
          println(s"Skipping fetch of $desc from remote")
        }
      }
      fetch("Prune database records", ctx.args.dbFetch, ctx.dbRemote, Seq(ctx.dbBranch), ctx.dbHome)
      fetch("Play source code", ctx.args.playFetch, ctx.playRemote, playBranches, ctx.playHome)
      fetch("tests source code", ctx.args.testsFetch, ctx.testsRemote, testsBranches, ctx.testsHome)
    }

    val neededTasks: Seq[TestTask] = ctx.playTests.flatMap { playTest =>
      //println(s"Working out tests to run for $playTest")

      val testsId: AnyObjectId = resolveId(ctx.testsHome, playTest.testsBranch, playTest.testsRevision)
      val revisions = gitLog(ctx.playHome, playTest.playBranch, playTest.playRevisionRange._1, playTest.playRevisionRange._2)
      val nonMergeRevisions = revisions.filter(_.parentCount == 1)
      nonMergeRevisions.flatMap { revision =>
        playTest.testNames.map { testName =>
          TestTask(
            info = TestTaskInfo(
              testName = testName,
              playCommit = revision.id,
              testsCommit = testsId.getName,
              testProject = "scala-bench"
            ),
            playBranch = playTest.playBranch,
            testsBranch = playTest.testsBranch
          )
        }
      }
    }
    val neededPlayCommitCount: Int = neededTasks.map(_.info.playCommit).distinct.size

    val completedTaskInfos: Seq[TestTaskInfo] = {
      val db = DB.read
      //println(s"DB: $db")
      db.testRuns.foldLeft[Seq[TestTaskInfo]](Seq.empty) {
        case (tasks, (_, testRunRecord)) =>
          val optTask: Option[TestTaskInfo] = for {
            testBuildRecord <- db.testBuilds.get(testRunRecord.testBuildId)
            playBuildRecord <- db.playBuilds.get(testBuildRecord.playBuildId)
            if playBuildRecord.pruneInstanceId == ctx.pruneInstanceId
          } yield TestTaskInfo(
            testName = testRunRecord.testName,
            playCommit = playBuildRecord.playCommit,
            testsCommit = testBuildRecord.testsCommit,
            testProject = testBuildRecord.testProject
          )
          tasks ++ optTask.toSeq
      }
    }
    val completedPlayCommitCount: Int = completedTaskInfos.map(_.playCommit).distinct.size

    val tasksToRun: Seq[TestTask] = neededTasks.filter(task => !completedTaskInfos.contains(task.info))
    val playCommitsToRunCount: Int = tasksToRun.map(_.info.playCommit).distinct.size

    println(s"Prune tests already executed: ${completedPlayCommitCount} Play revisions, ${completedTaskInfos.size}  test runs")
    println(s"Prune tests needed: ${neededPlayCommitCount} Play revisions, ${neededTasks.size} test runs")
    println(s"Prune tests remaining: ${playCommitsToRunCount} Play revisions, ${tasksToRun.size} test runs")

    tasksToRun.foreach(RunTest.runTestTask)
  }

}