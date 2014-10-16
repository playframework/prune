/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import com.typesafe.config.{ Config, ConfigFactory }
import java.nio.file._
import java.util.UUID
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
        |# The UUID used to identify this instance of Prune in test records.
        |# Each Prune instance needs a unique id. To generate a unique id, go
        |# to http://www.famkruithof.net/uuid/uuidgen.
        |#pruneInstanceId: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        |
        |# The location of Java 8 on the system. Prune will use this JDK when
        |# building and running tests.
        |#java8.home: /usr/lib/jvm/java-8-oracle/jre
        |
        |# The remote git repository and branch to use for storing test
        |# results. This could be a Github repository or it could just be a
        |# path to a local repository that you've created with `git init`. If
        |# it's a remote repository and you want to push to that repository,
        |# be sure to configure appropriate SSH keys in `~/.ssh`.
        |#dbRemote: "https://github.com/playframework/prune.git"
        |#dbBranch: database
        |
        |# The remote git repository and branch to use as a results website.
        |# This could be a Github repository or it could just be a path to a
        |# local repository that you've created with `git init`. If it's a
        |# remote repository and you want to push to that repository, be sure
        |# to configure appropriate SSH keys in `~/.ssh`.
        |#siteRemote: "https://github.com/playframework/prune.git"
        |#siteBranch: gh-pages""".stripMargin)
      System.exit(1)
    }
    if (Files.notExists(userConfigFile)) configError("Please create a Prune configuration file.")

    val userConfig: Config = ConfigFactory.parseFile(userConfigFile.toFile)
    val config = userConfig.withFallback(defaultConfig)

    Seq("pruneInstanceId", "java8.home", "dbRemote", "dbBranch", "siteRemote", "siteBranch").foreach { path =>
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
      case Some(GenerateJsonReport) => generateJsonReport
      case Some(PullSite) => pullSite
      case Some(GenerateSiteFiles) => generateSiteFiles
      case Some(PushSite) => pushSite
      case Some(Wrk) => wrk
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

  private def playCommitsToTest(playTestConfig: PlayTestsConfig)(implicit ctx: Context): Seq[String] = {
    val commitsInLog = PruneGit.gitFirstParentsLog(
      ctx.playHome,
      playTestConfig.playBranch,
      playTestConfig.playRevisionRange._1,
      playTestConfig.playRevisionRange._2)

    if (commitsInLog.length <= 1) commitsInLog else {
      // Use randomness of hashes to do random sampling. Using hashes for sampling is
      // better than using a random number because it makes sampling stable across
      // multiple runs of Prune. It also means that changing the sampling value
      // will still make use of any commits that have already been sampled.
      val maxPrefix = (1 << (7 * 4)) - 1 // fffffff or 268435455
      val samplePrefixCeiling = Math.round(maxPrefix * playTestConfig.playRevisionSampling).toInt
      val filteredPart: Seq[String] = commitsInLog.init.filter { commit =>
        val commitPrefix = java.lang.Integer.parseInt(commit.substring(0, 7), 16)
        commitPrefix <= samplePrefixCeiling
      }
      // Force the last commit onto the list so we can see the start point of the sampling
      val unfilteredPart: String = commitsInLog.last
      filteredPart :+ unfilteredPart
    }
  }

  private def filterBySeq[A,B](input: Seq[A], filters: Seq[B], extract: A => B): Seq[A] = {
    if (filters.isEmpty) input else input.filter(a => filters.contains(extract(a)))
  }

  def test(implicit ctx: Context): Unit = {
    
    val deadline: Option[DateTime] = ctx.args.maxTotalMinutes.map { mins =>
      DateTime.now.plusMinutes(mins)
    }

    val filteredPlayTests = filterBySeq(ctx.playTests, ctx.args.playBranches, (_: PlayTestsConfig).playBranch)
    val neededTasks: Seq[TestTask] = filteredPlayTests.flatMap { playTest =>
      //println(s"Working out tests to run for $playTest")

      val appsId: AnyObjectId = PruneGit.resolveId(ctx.appsHome, playTest.appsBranch, playTest.appsRevision)
      val playCommits: Seq[String] = playCommitsToTest(playTest)
      val playCommitFilter: Seq[String] = ctx.args.playRevs.map(r => PruneGit.resolveId(ctx.playHome, playTest.playBranch, r).name)
      val filteredPlayCommits: Seq[String] = filterBySeq(playCommits, playCommitFilter, identity[String])
      filteredPlayCommits.flatMap { playCommit =>
        val filteredTestNames = filterBySeq(playTest.testNames, ctx.args.testNames, identity[String])
        filteredTestNames.map { testName =>
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

//    type TestFilter = Seq[TestTask] => Seq[TestTask]
//
//    val filters: Seq[Seq[TestTask] => Seq[TestTask]] =
//      ctx.args.maxTestRuns.map(i => ((s: Seq[TestTask]) => s.take(i))).toSeq ++
//      ctx.args.playRev.map(r => ((s: Seq[TestTask]) => s.filter(_.playCommit.startsWith(r))).toSeq ++

    val truncatedTasksToRun = ctx.args.maxTestRuns.fold(tasksToRun) { i =>
      if (tasksToRun.size > i) {
        println(s"Overriding number of test runs down to $i")
        tasksToRun.take(i)
      } else tasksToRun
    }

    Assets.extractAssets

    @tailrec
    def loop(taskQueue: Seq[TestTask]): Unit = {
      if (taskQueue.isEmpty) () else {
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
      playTestConfig <- filterBySeq(ctx.playTests, ctx.args.playBranches, (_: PlayTestsConfig).playBranch)
      testName <- filterBySeq(playTestConfig.testNames, ctx.args.testNames, identity[String])
    } {
      val playCommits: Seq[String] = playCommitsToTest(playTestConfig)
      val resultMap = getResults(playCommits, testName)
      println(s"=== Test $testName on ${playTestConfig.playBranch} - ${playCommits.size} commits ===")
      for (playCommit <- playCommits) {
        val testResult: Either[String, TestResult] = resultMap.get(playCommit).toRight("<no result for commit>")
        val wrkOutput: Either[String, String] = testResult.right.flatMap(_.wrkOutput.toRight("<no wrk stdout for test run>"))
        val wrkResult: Either[String, WrkResult] = wrkOutput.right.flatMap(Results.parseWrkOutput)
        val resultDisplay: String = wrkResult.right.flatMap(_.summary.right.map(_.display)).merge
        println(s"${playCommit.substring(0,7)} $resultDisplay")
      }
    }
  }

  def pushTestResults(implicit ctx: Context): Unit = {
    PruneGit.gitPushChanges(
      remote = ctx.dbRemote,
      branch = ctx.dbBranch,
      localDir = ctx.dbHome,
      commitMessage = "Added records")
  }

  def generateJsonReport(implicit ctx: Context): Unit = {
    val outputFile: String = ctx.args.outputFile.getOrElse(sys.error("Please provide an output file"))
    val jsonString = JsonReport.generateJsonReport
    Files.write(Paths.get(outputFile), jsonString.getBytes("UTF-8"))
  }

  def pullSite(implicit ctx: Context): Unit = {
    PruneGit.gitCloneOrRebaseBranches(
      remote = ctx.siteRemote,
      branches = Seq(ctx.siteBranch),
      checkedOutBranch = Some(ctx.siteBranch),
      localDir = ctx.siteHome)
  }

  def generateSiteFiles(implicit ctx: Context): Unit = {
    val jsonString = JsonReport.generateJsonReport
    val jsString = s"var report = $jsonString;"
    val outputFile: Path = Paths.get(ctx.siteHome, "prune-data.js")
    Files.write(outputFile, jsString.getBytes("UTF-8"))
  }

  def pushSite(implicit ctx: Context): Unit = {
    PruneGit.gitPushChanges(
      remote = ctx.siteRemote,
      branch = ctx.siteBranch,
      localDir = ctx.siteHome,
      commitMessage = "Updated generated files")
  }

  def wrk(implicit ctx: Context): Unit = {
    val testOrAppName: String = ctx.args.testOrAppName.get
    val testConfig: Option[TestConfig] = ctx.testConfig.get(testOrAppName)
    val appName = testConfig.fold(testOrAppName)(_.app)
    val wrkArgs = testConfig.fold(Seq.empty[String])(_.wrkArgs) ++ ctx.args.wrkArgs
    if (ctx.args.playBuild) {
      BuildPlay.buildPlayDirectly()
    }
    if (ctx.args.appBuild) {
      BuildApp.buildAppDirectly(appName)
    }
    val testExecutions = RunTest.runTestDirectly(appName, wrkArgs)
  }

}