/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import com.typesafe.config.Config
import java.util.UUID
import scala.collection.convert.WrapAsScala._

case class Context(
  args: Args,
  config: Config
) {
  val pruneInstanceId = UUID.fromString(config.getString("pruneInstanceId"))
  val pruneHome = config.getString("home")
  val java8Home = config.getString("java8.home")
  val ivyHome = config.getString("ivy.home")

  val playRemote = config.getString("playRemote")
  val playHome = config.getString("playHome")

  val appsRemote = config.getString("appsRemote")
  val appsHome = config.getString("appsHome")

  val dbRemote = config.getString("dbRemote")
  val dbBranch = config.getString("dbBranch")
  val dbHome = config.getString("dbHome")

  val assetsHome = config.getString("assetsHome")

  val playTests: Seq[PlayTestsConfig] = {
    asScalaBuffer(config.getConfigList("playTests")).map { c: Config =>
      PlayTestsConfig(
        playBranch = c.getString("playBranch"),
        playRevisionRange = {
          val split = c.getString("playRevisionRange").split("\\.\\.")
          if (split.length != 2) {
            sys.error(s"Play revision range must contain a single '..': $split")
          }
          (split(0), split(1))
        },
        appsBranch = c.getString("appsBranch"),
        appsRevision = c.getString("appsRevision"),
        testNames = asScalaBuffer(c.getStringList("testNames"))
      )
    }
  }

  def playBranches = playTests.map(_.playBranch).distinct
  def appsBranches = playTests.map(_.appsBranch).distinct

//  private def getEntryNames(c: Config): Seq[String] = iterableAsScalaIterable(config.entrySet).to[Seq].foldLeft[Seq[String]](Seq.empty) {
//    case (names, entry) => names :+ entry.getKey
//  }

  val testConfig: Map[String, TestConfig] = {
    asScalaBuffer(config.getConfigList("tests")).foldLeft[Map[String, TestConfig]](Map.empty) {
      case (m, entry) =>
        val name: String = entry.getString("name")
        assert(!m.contains(name))
        m.updated(name, TestConfig(
        app = entry.getString("app"),
        description = entry.getString("description"),
        wrkArgs = asScalaBuffer(entry.getStringList("wrkArgs"))
      ))
    }
  }
}

sealed trait CommandArg
case object Pull extends CommandArg
case object Test extends CommandArg
case object PushTestResults extends CommandArg
case object PrintReport extends CommandArg
case object GenerateJsonReport extends CommandArg

case class Args(
  command: Option[CommandArg] = None,
  configFile: Option[String] = None,
  dbFetch: Boolean = true,
  playFetch: Boolean = true,
  appsFetch: Boolean = true,
  maxTestRuns: Option[Int] = None,
  maxWrkDuration: Option[Int] = None)
object Args {
  def parse(rawArgs: Seq[String]) = {
    val parser = new scopt.OptionParser[Args]("prune") {
      head("prune")
      opt[String]("config-file") action { (s, c) =>
        c.copy(configFile = Some(s))
      }
      cmd("pull") action { (_, c) =>
        c.copy(command = Some(Pull))
      } text("Pull from remote repositories") children(
        opt[Unit]("skip-db-fetch") action { (_, c) =>
          c.copy(dbFetch = false)
        },
        opt[Unit]("skip-play-fetch") action { (_, c) =>
          c.copy(playFetch = false)
        },
        opt[Unit]("skip-apps-fetch") action { (_, c) =>
          c.copy(appsFetch = false)
        }
      )
      cmd("test") action { (_, c) =>
        c.copy(command = Some(Test))
      } text("Run tests") children(
        opt[Int]("max-test-runs") action { (i, c) =>
          c.copy(maxTestRuns = Some(i))
        },
        opt[Int]("max-wrk-duration") action { (i, c) =>
          c.copy(maxWrkDuration = Some(i))
        }
      )
      cmd("push-test-results") action { (_, c) =>
        c.copy(command = Some(PushTestResults))
      } text("Push test results to remote database repository")
      cmd("print-report") action { (_, c) =>
        c.copy(command = Some(PrintReport))
      } text("Output a simple report of test results")
      cmd("generate-json-report") action { (_, c) =>
        c.copy(command = Some(GenerateJsonReport))
      } text("Generate a report of test results to a JSON file")
    }
    parser.parse(rawArgs, Args()).getOrElse(sys.error("Arg parse error"))
  }
}

case class PlayTestsConfig(
  playBranch: String,
  playRevisionRange: (String, String),
  appsBranch: String,
  appsRevision: String,
  testNames: Seq[String]
)

case class TestConfig(
  app: String,
  description: String,
  wrkArgs: Seq[String]
)