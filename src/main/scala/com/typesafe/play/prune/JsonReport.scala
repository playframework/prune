/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file.{Paths, Files}
import java.util.UUID
import java.util.concurrent.TimeUnit

import org.joda.time.DateTime

import scala.collection.convert.WrapAsScala._

object JsonReport {
  def generateJsonReport(implicit ctx: Context): Unit = {
    val outputFile: String = ctx.args.outputFile.getOrElse(sys.error("Please provide an output file"))

    // Use HOURS because MILLISECONDS can overflow DateTime.minusMillis()
    val hours = ctx.config.getDuration("jsonReport.duration", TimeUnit.HOURS)
    val endTime: DateTime = DateTime.now
    val startTime = endTime.minusHours(hours.toInt)
    println(s"Generating report from $startTime until $endTime")

    type BranchName = String
    type Commit = String
    case class CommitInfo(
                           commit: Commit,
                           time: DateTime
                           )
    type TestName = String
    case class TestResult(
      testRunId: UUID,
      requestsPerSecond: Double,
      latencyMean: Double,
      latency95: Double
    )
    type TestDescription = String
    case class Output(
      start: DateTime,
      end: DateTime,
      branches: Map[BranchName,Seq[CommitInfo]],
      tests: Map[TestName,TestDescription],
      results: Map[Commit,Map[TestName,TestResult]]
    )

    val branches: Map[BranchName, Seq[CommitInfo]] = {
      val branchNames: Seq[BranchName] = asScalaBuffer(ctx.config.getStringList("jsonReport.playBranches"))
      branchNames.map { branch =>
        val commits: Seq[(Commit, DateTime)] = PruneGit.gitFirstParentsLogToDate(ctx.playHome, branch, "HEAD", startTime)
        val commitInfos: Seq[CommitInfo] = commits.map { case (commit, time) => CommitInfo(commit, time) }
        (branch, commitInfos)
      }.toMap
    }

    val tests: Map[TestName,TestDescription] = ctx.testConfig.mapValues(_.description)

    val results: Map[Commit,Map[TestName,TestResult]] = {
      // Flatten commits into a set for fast lookup
      val commitSet: Set[Commit] =
        (for ((branch, commitInfos) <- branches; commitInfo <- commitInfos) yield commitInfo.commit).to[Set]
      val flatCommitResults: Iterator[(Commit,TestName,TestResult)] = DB.iterator.flatMap { join =>
        if (join.pruneInstanceId == ctx.pruneInstanceId &&
          commitSet.contains(join.playBuildRecord.playCommit)) {
          val optWrkResult: Option[WrkResult] = join.testRunRecord.wrkExecutions.last.stdout.flatMap(Results.parseWrkOutput)
          optWrkResult.fold[Iterator[(Commit,TestName,TestResult)]](Iterator.empty) { wr =>
            val testResult = TestResult(
              testRunId = join.testRunId,
              requestsPerSecond = wr.requests.toDouble / wr.duration.toDouble * 1000000,
              latencyMean = wr.latency.mean / 1000,
              latency95 = wr.latency.percentiles(95).toDouble / 1000
            )
            Iterator((join.playBuildRecord.playCommit, join.testRunRecord.testName, testResult))
          }
        } else Iterator.empty
      }
      flatCommitResults.foldLeft[Map[Commit,Map[TestName,TestResult]]](Map.empty) {
        case (commitMap, (commit, testName, testResult)) =>
          val testNameMap: Map[TestName,TestResult] = commitMap.getOrElse(commit, Map.empty)
          if (testNameMap.contains(testName)) println(s"Overwriting existing test result for $commit $testName")
          commitMap + (commit -> (testNameMap + (testName -> testResult)))
      }
    }

    val output = Output(
      start = startTime,
      end = endTime,
      branches = branches,
      tests = tests,
      results = results
    )

    import play.api.libs.json._

    implicit val writesTestResult = new Writes[TestResult] {
      def writes(tr: TestResult) = Json.obj(
        "run" -> tr.testRunId,
        "req/s" -> tr.requestsPerSecond,
        "latMean" -> tr.latencyMean,
        "lat95" -> tr.latency95
      )
    }
    implicit val writesCommitInfo = new Writes[CommitInfo] {
      def writes(ci: CommitInfo) = Json.obj(
        "commit" -> ci.commit,
        "time" -> ci.time
      )
    }
    implicit val writesOutput = new Writes[Output] {
      def writes(o: Output) = Json.obj(
        "start" -> o.start,
        "end" -> o.end,
        "branches" -> o.branches,
        "tests" -> o.tests,
        "results" -> o.results
      )
    }
    val json = writesOutput.writes(output)
    val jsonString = Json.stringify(json)
    Files.write(Paths.get(outputFile), jsonString.getBytes("UTF-8"))
  }

}
