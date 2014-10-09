/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import com.typesafe.config.{ Config, ConfigFactory }
import java.nio.file._
import java.util.UUID
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.eclipse.jgit.lib._
import org.joda.time.{Duration, DateTime}
import scala.annotation.tailrec

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
    val args = Args.parse(rawArgs)

    val defaultConfig = ConfigFactory.load().getConfig("com.typesafe.play.prune")
    val userConfigFile = Paths.get(args.configFile.getOrElse(defaultConfig.getString("defaultUserConfig")))

    def configError(message: String): Unit = {
      println(s"""
        |$message
        |
        |Prune is looking for a configuration file at $userConfigFile. You can override
        |the configuration file location with --config-file=<path>.
        |
        |An example configuration file with a random instance id is shown below:
        |
        |---- prune.config ----
        |# The UUID used to identify this instance of Prune in test records.
        |pruneInstanceId: ${UUID.randomUUID()}
        |
        |# The location of Java 8 on the system.
        |#java8.home: /usr/lib/jvm/java-8-oracle/jre
        |
        |# The location of the remote database repository to use for storing test results
        |#dbRemote: "https://github.com/playframework/prune.git"
        |
        |# The branch of the database repository to use for database results
        |#dbBranch: database""".stripMargin)
      System.exit(1)
    }
    if (Files.notExists(userConfigFile)) configError("Please create a Prune configuration file.")

    val userConfig: Config = ConfigFactory.parseFile(userConfigFile.toFile)
    val config = userConfig.withFallback(defaultConfig)

    Seq("pruneInstanceId", "java8.home", "dbRemote", "dbBranch").foreach { path =>
      if (!config.hasPath(path)) configError(s"Missing setting `$path` from your Prune configuration file.")
    }

    implicit val ctx = Context(
      args = args,
      config = config
    )

    println(s"Prune instance id is ${ctx.pruneInstanceId}")

    args.command match {
      case None => println("Please provide a command for Prune to execute.")
      case Some(Pull) => pull
      case Some(Test) => test
      case Some(PushTestResults) => pushTestResults
      case Some(PrintReport) => printReport
      case Some(GenerateJsonReport) => JsonReport.generateJsonReport
    }

  }

  def pull(implicit ctx: Context): Unit = {
    def pull0(desc: String, switch: Boolean, remote: String, branches: Seq[String], checkedOutBranch: Option[String], localDir: String): Unit = {
      if (switch) {
        println(s"Fetching $desc from remote")
        PruneGit.gitCloneOrRebaseBranches(
          remote = remote,
          branches = branches,
          checkedOutBranch = checkedOutBranch,
          localDir = localDir)
      } else {
        println(s"Skipping fetch of $desc from remote")
      }
    }
    pull0("Prune database records", ctx.args.dbFetch, ctx.dbRemote, Seq(ctx.dbBranch), Some(ctx.dbBranch), ctx.dbHome)
    pull0("Play source code", ctx.args.playFetch, ctx.playRemote, ctx.playBranches, None, ctx.playHome)
    pull0("apps source code", ctx.args.appsFetch, ctx.appsRemote, ctx.appsBranches, None, ctx.appsHome)
  }

  private def playCommitsToTest(playTestConfig: PlayTestsConfig)(implicit ctx: Context): Seq[String] =
    PruneGit.gitFirstParentsLog(
      ctx.playHome,
      playTestConfig.playBranch,
      playTestConfig.playRevisionRange._1,
      playTestConfig.playRevisionRange._2)

  def test(implicit ctx: Context): Unit = {
    
    val deadline: Option[DateTime] = ctx.args.maxTotalMinutes.map { mins =>
      DateTime.now.plusMinutes(mins)
    }

    val neededTasks: Seq[TestTask] = ctx.playTests.flatMap { playTest =>
      //println(s"Working out tests to run for $playTest")

      val appsId: AnyObjectId = PruneGit.resolveId(ctx.appsHome, playTest.appsBranch, playTest.appsRevision)
      val playCommits = playCommitsToTest(playTest)
      playCommits.flatMap { playCommit =>
        playTest.testNames.map { testName =>
          val testApp = ctx.testConfig.get(testName).map(_.app).getOrElse(sys.error(s"No test config for $testName"))
          TestTask(
            info = TestTaskInfo(
              testName = testName,
              playCommit = playCommit,
              appName = testApp
            ),
            playBranch = playTest.playBranch,
            appsBranch = playTest.appsBranch,
            appsCommit = appsId.getName
          )
        }
      }
    }
    val neededPlayCommitCount: Int = neededTasks.map(_.info.playCommit).distinct.size

    val completedTaskInfos: Seq[TestTaskInfo] = DB.iterator.map { join =>
          TestTaskInfo(
            testName = join.testRunRecord.testName,
            playCommit = join.playBuildRecord.playCommit,
            appName = join.appBuildRecord.appName
          )
    }.toSeq
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

    @tailrec
    def loop(taskQueue: Seq[TestTask]): Unit = {
      val now = DateTime.now
      deadline match {
        case Some(d) if now.isAfter(d) =>
          val targetMins: Int = ctx.args.maxTotalMinutes.get
          val actualMins: Int = new Duration(d, now).getStandardMinutes.toInt
          println(s"Stopping tests after ${actualMins} minutes because --max-total-minutes ${targetMins} exceeded: ${taskQueue.size} tests remaining")
        case _ =>
          RunTest.runTestTask(taskQueue.head)
          loop(taskQueue.tail)
      }
      
    }
    loop(truncatedTasksToRun)
  }

  def printReport(implicit ctx: Context): Unit = {
    type PlayRev = String
    case class TestResult(
                           testRunId: UUID,
                           wrkOutput: Option[String]
                           )

    def getResults(playCommits: Seq[PlayRev], testName: String): Map[PlayRev, TestResult] = {
      DB.iterator.flatMap { join =>
        if (
          join.pruneInstanceId == ctx.pruneInstanceId &&
            join.testRunRecord.testName == testName &&
            playCommits.contains(join.playBuildRecord.playCommit)) {
          Iterator((join.playBuildRecord.playCommit, TestResult(
            testRunId = join.testRunId,
            wrkOutput = join.testRunRecord.wrkExecutions.last.stdout
          )))
        } else Iterator.empty
      }.toMap
    }

    for {
      playTestConfig <- ctx.playTests
      testName <- playTestConfig.testNames
    } {
      val playCommits: Seq[String] = playCommitsToTest(playTestConfig)
      val resultMap = getResults(playCommits, testName)
      println(s"Test $testName on ${playTestConfig.playBranch}")
      for (playCommit <- playCommits) {
        val wrkOutput: Option[String] = resultMap.get(playCommit).flatMap(_.wrkOutput)
        val wrkResult: Option[WrkResult] = wrkOutput.flatMap(Results.parseWrkOutput)
        val resultDisplay: String = wrkResult.map { wr =>
          s"Requests/s: ${wr.requests.toDouble / wr.duration.toDouble * 1000000}, "+
            s"Mean latency: ${wr.latency.mean}, " +
            s"Latency 95%: ${wr.latency.percentiles(95)}"
        }.getOrElse("-")
        println(s"${playCommit.substring(0,7)} $resultDisplay")
      }
    }
  }

  def pushTestResults(implicit ctx: Context): Unit = {
    PruneGit.gitPushChanges(
      remote = ctx.dbRemote,
      branch = ctx.dbBranch,
      localDir = ctx.dbHome)
  }

}