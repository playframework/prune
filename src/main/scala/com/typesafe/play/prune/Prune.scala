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
import scala.collection.JavaConversions
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scopt.OptionParser

import Exec._
import PruneGit._

case class TestTask(
  name: String,
  playCommit: String,
  testsCommit: String,
  testProject: String,
  requestPath: String
)

object Prune {

  def main(rawArgs: Array[String]): Unit = {
    val parser = new scopt.OptionParser[Args]("prune") {
      opt[String]("config-file") action { (s, c) =>
        c.copy(configFile = Some(s))
      }
      opt[String]("play-revision") action { (s, c) =>
        c.copy(playRevision = Some(s))
      }
      opt[String]("tests-revision") action { (s, c) =>
        c.copy(testsRevision = Some(s))
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

    println(s"Prune instance id is ${ctx.pruneInstanceId}")

    {
      def fetch(switch: Boolean, repoConfig: RepoConfig): Unit = {
        if (switch) {
          println(s"Fetching ${repoConfig.description} from remote")
          gitSync(
            remote = repoConfig.remote,
            branches = repoConfig.branches,
            localDir = repoConfig.localDir)
        } else {
          println(s"Skipping fetch of ${repoConfig.description} from remote")
        }
      }
      fetch(ctx.args.dbFetch, ctx.dbRepoConfig)
      fetch(ctx.args.playFetch, ctx.playRepoConfig)
      fetch(ctx.args.testsFetch, ctx.testsRepoConfig)
    }

    def getCommitId(forceRevision: Option[String], repoConfig: RepoConfig): String = {
      val revision: String = forceRevision.getOrElse(repoConfig.mainBranchRef)
      val commitId: String = withRepository(repoConfig.localDir)(_.resolve(revision)).name
      println(s"Selecting ${repoConfig.description} at commit $commitId")
      commitId
    }
    val playCommitId = getCommitId(ctx.args.playRevision, ctx.playRepoConfig)
    val testsCommitId = getCommitId(ctx.args.testsRevision, ctx.testsRepoConfig)

    val testTasks = Seq(
      TestTask(
        "tiny",
        playCommit = playCommitId,
        testsCommit = testsCommitId,
        testProject = "scala-bench",
        requestPath = "/helloworld"
      ),
      TestTask(
        "download-50k",
        playCommit = playCommitId,
        testsCommit = testsCommitId,
        testProject = "scala-bench",
        requestPath = "/download/51200"
      ),
      TestTask(
        "download-chunked-50k",
        playCommit = playCommitId,
        testsCommit = testsCommitId,
        testProject = "scala-bench",
        requestPath = "/download-chunked/51200"
      )
    )

    testTasks.foreach(RunTest.runTestTask)
  }

}