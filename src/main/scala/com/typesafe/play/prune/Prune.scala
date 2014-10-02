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
  appsBranch: String,
  appsCommit: String
)

case class TestTaskInfo(
  testName: String,
  playCommit: String,
  appName: String
)

object Prune {

  def main(rawArgs: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Args]("prune") {
      opt[String]("config-file") action { (s, c) =>
        c.copy(configFile = Some(s))
      }
      opt[Unit]("skip-fetches") action { (_, c) =>
        c.copy(dbFetch = false, playFetch = false, appsFetch = false)
      }
      opt[Unit]("skip-db-fetch") action { (_, c) =>
        c.copy(dbFetch = false)
      }
      opt[Unit]("skip-play-fetch") action { (_, c) =>
        c.copy(playFetch = false)
      }
      opt[Unit]("skip-apps-fetch") action { (_, c) =>
        c.copy(appsFetch = false)
      }
      opt[Int]("max-test-runs") action { (i, c) =>
        c.copy(maxTestRuns = Some(i))
      }
      opt[Int]("max-wrk-duration") action { (i, c) =>
        c.copy(maxWrkDuration = Some(i))
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
    val appsBranches = ctx.playTests.map(_.appsBranch).distinct

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
      fetch("apps source code", ctx.args.appsFetch, ctx.appsRemote, appsBranches, ctx.appsHome)
    }

    def playCommitsToTest(playTestConfig: PlayTestsConfig): Seq[String] = {
      val revisions = gitLog(ctx.playHome, playTestConfig.playBranch, playTestConfig.playRevisionRange._1, playTestConfig.playRevisionRange._2)
      revisions.collect {
        case LogEntry(id, 1, _) => id
      }
    }

    val neededTasks: Seq[TestTask] = ctx.playTests.flatMap { playTest =>
      //println(s"Working out tests to run for $playTest")

      val appsId: AnyObjectId = resolveId(ctx.appsHome, playTest.appsBranch, playTest.appsRevision)
      val playCommits = playCommitsToTest(playTest)
      playCommits.flatMap { playCommit =>
        playTest.testNames.map { testName =>
          TestTask(
            info = TestTaskInfo(
              testName = testName,
              playCommit = playCommit,
              appName = "scala-bench"
            ),
            playBranch = playTest.playBranch,
            appsBranch = playTest.appsBranch,
            appsCommit = appsId.getName
          )
        }
      }
    }
    val neededPlayCommitCount: Int = neededTasks.map(_.info.playCommit).distinct.size

    val completedTaskInfos: Seq[TestTaskInfo] = {
      DB.foldLeft[Seq[TestTaskInfo]](Seq.empty) {
        case (infos, join) =>
          val info = TestTaskInfo(
            testName = join.testRunRecord.testName,
            playCommit = join.playBuildRecord.playCommit,
            appName = join.appBuildRecord.appName
          )
          infos :+ info
      }
    }
    val completedPlayCommitCount: Int = completedTaskInfos.map(_.playCommit).distinct.size

    val tasksToRun: Seq[TestTask] = neededTasks.filter(task => !completedTaskInfos.contains(task.info))
    val playCommitsToRunCount: Int = tasksToRun.map(_.info.playCommit).distinct.size

    println(s"Prune tests already executed: ${completedPlayCommitCount} Play revisions, ${completedTaskInfos.size} test runs")
    println(s"Prune tests needed: ${neededPlayCommitCount} Play revisions, ${neededTasks.size} test runs")
    println(s"Prune tests remaining: ${playCommitsToRunCount} Play revisions, ${tasksToRun.size} test runs")

    val truncatedTasksToRun = ctx.args.maxTestRuns.fold(tasksToRun) { i =>
      if (tasksToRun.size > i) {
        println(s"Overriding number of test runs down to $i")
        tasksToRun.take(i)
      } else tasksToRun
    }

    Assets.extractAssets

    truncatedTasksToRun.foreach(RunTest.runTestTask)

    {
      type PlayRev = String
      case class TestResult(
        testRunId: UUID,
        wrkOutput: Option[String]
      )

      def getResults(playCommits: Seq[PlayRev], testName: String): Map[PlayRev, TestResult] = {
        DB.foldLeft[Map[PlayRev,TestResult]](Map.empty) {
          case (results, join) =>
            if (
              join.pruneInstanceId == ctx.pruneInstanceId &&
              join.testRunRecord.testName == testName &&
              playCommits.contains(join.playBuildRecord.playCommit)) {
              results.updated(join.playBuildRecord.playCommit, TestResult(
                testRunId = join.testRunId,
                wrkOutput = join.testRunRecord.wrkExecutions.last.stdout
              ))
            } else results
        }
      }

      for {
        playTestConfig <- ctx.playTests
        testName <- playTestConfig.testNames
      } {
        val playCommits = playCommitsToTest(playTestConfig)
        val resultMap = getResults(playCommits, testName)
        println(s"Test $testName on ${playTestConfig.playBranch}")
        for (playCommit <- playCommits) {
          val wrkOutput: Option[String] = resultMap.get(playCommit).flatMap(_.wrkOutput)
          val wrkResult: Option[WrkResult] = wrkOutput.flatMap(Results.parseWrkOutput)
          val resultDisplay: String = wrkResult.map { wr =>
            s"Requests/s: ${wr.requestsPerSecond.mean}, "+
            s"Latency 50%: ${wr.latency.percentiles(50)}, " +
            s"Latency 95%: ${wr.latency.percentiles(95)}"
          }.getOrElse("-")
          println(s"${playCommit.substring(0,7)} $resultDisplay")
        }
      }
    }
  }

}