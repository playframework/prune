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
  val java8Opts: Seq[String] = asScalaBuffer(config.getStringList("java8.opts"))

  val ivyHome = config.getString("ivy.home")

  val playRemote = config.getString("playRemote")
  val playHome = args.playHome.getOrElse(config.getString("playHome"))

  val appsRemote = config.getString("appsRemote")
  val appsHome = args.appsHome.getOrElse(config.getString("appsHome"))

  val dbRemote = config.getString("dbRemote")
  val dbBranch = config.getString("dbBranch")
  val dbHome = config.getString("dbHome")

  val siteRemote = config.getString("siteRemote")
  val siteBranch = config.getString("siteBranch")
  val siteHome = config.getString("siteHome")

  val assetsHome = config.getString("assetsHome")

  val playTests: Seq[PlayTestsConfig] = {
    asScalaBuffer(config.getConfigList("playTests")).map { c: Config =>
      val sampling = c.getDouble("playRevisionSampling")
      assert(sampling >= 0 && sampling <= 1.0)
      PlayTestsConfig(
        playBranch = c.getString("playBranch"),
        playRevisionRange = {
          val split = c.getString("playRevisionRange").split("\\.\\.")
          if (split.length != 2) {
            sys.error(s"Play revision range must contain a single '..': $split")
          }
          (split(0), split(1))
        },
        playRevisionSampling = sampling,
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

  val yourkitHome: String = config.getString("yourkit.home")
  val yourkitAgent: String = config.getString("yourkit.agent")
  val yourkitJavaOpts: Seq[String] = asScalaBuffer(config.getStringList("yourkit.javaOpts"))

}

sealed trait CommandArg
case object Pull extends CommandArg
case object Test extends CommandArg
case object PushTestResults extends CommandArg
case object PrintReport extends CommandArg
case object GenerateJsonReport extends CommandArg
case object PullSite extends CommandArg
case object GenerateSiteFiles extends CommandArg
case object PushSite extends CommandArg
case object Wrk extends CommandArg
case object Profile extends CommandArg

case class Args(
  command: Option[CommandArg] = None,
  configFile: Option[String] = None,
  dbFetch: Boolean = true,
  playFetch: Boolean = true,
  appsFetch: Boolean = true,
  maxTestRuns: Option[Int] = None,
  maxBuildFailures: Int = 2,
  maxWrkDuration: Option[Int] = None,
  playBranches: Seq[String] = Seq.empty,
  playRevs: Seq[String] = Seq.empty,
  testNames: Seq[String] = Seq.empty,
  maxTotalMinutes: Option[Int] = None,
  outputFile: Option[String] = None,
  playHome: Option[String] = None,
  appsHome: Option[String] = None,
  testOrAppName: Option[String] = None,
  wrkArgs: Seq[String] = Seq.empty,
  playBuild: Boolean = true,
  appBuild: Boolean = true)
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
        },
        opt[Int]("max-total-minutes") action { (i, c) =>
          c.copy(maxTotalMinutes = Some(i))
        },
        opt[Int]("max-build-failures") action { (i, c) =>
          c.copy(maxBuildFailures = i)
        },
        opt[String]("play-branch") optional() unbounded() action { (s, c) =>
          c.copy(playBranches = c.playBranches :+ s)
        },
        opt[String]("play-rev") optional() unbounded() action { (s, c) =>
          c.copy(playRevs = c.playRevs :+ s)
        },
        opt[String]("test-name") optional() unbounded() action { (s, c) =>
          c.copy(testNames = c.testNames :+ s)
        }
      )
      cmd("push-test-results") action { (_, c) =>
        c.copy(command = Some(PushTestResults))
      } text("Push test results to remote database repository")
      cmd("print-report") action { (_, c) =>
        c.copy(command = Some(PrintReport))
      } text("Output a simple report of test results") children(
        opt[String]("play-branch") optional() unbounded() action { (s, c) =>
          c.copy(playBranches = c.playBranches :+ s)
        },
        opt[String]("test-name") optional() unbounded() action { (s, c) =>
          c.copy(testNames = c.testNames :+ s)
        }
      )
      cmd("generate-json-report") action { (_, c) =>
        c.copy(command = Some(GenerateJsonReport))
      } text("Generate a report of test results to a JSON file") children(
        arg[String]("<output-file>") action { (s, c) =>
          c.copy(outputFile = Some(s))
        }
      )
      cmd("pull-site") action { (_, c) =>
        c.copy(command = Some(PullSite))
      } text("Pull site from remote repository")
      cmd("generate-site-files") action { (_, c) =>
        c.copy(command = Some(GenerateSiteFiles))
      } text("Generate site files based on test results")
      cmd("push-site") action { (_, c) =>
        c.copy(command = Some(PushSite))
      } text("Push site to remote repository")
      cmd("wrk") action { (_, c) =>
        c.copy(command = Some(Wrk))
      } text("Run wrk on your local Play code") children(
        arg[String]("<play-home>") action { (s, c) => c.copy(playHome = Some(s)) } text("Play directory"),
        arg[String]("<apps-home>") action { (s, c) => c.copy(appsHome = Some(s)) } text("App directory"),
        arg[String]("<app-name>") action { (s, c) => c.copy(testOrAppName = Some(s)) } text("Test name or app name"),
        arg[String]("[<wrk arg>...]") optional() unbounded() action { (s, c) => c.copy(wrkArgs = c.wrkArgs :+ s) } text("Wrk arguments"),
        opt[Int]("max-wrk-duration") action { (i, c) =>
          c.copy(maxWrkDuration = Some(i))
        },
        opt[Unit]("skip-play-build") action { (_, c) =>
          c.copy(playBuild = false)
        },
        opt[Unit]("skip-app-build") action { (_, c) =>
          c.copy(appBuild = false)
        }
      )
      cmd("profile") action { (_, c) =>
        c.copy(command = Some(Profile))
      } text("Run profiling agent on your local Play code") children(
        arg[String]("<play-home>") action { (s, c) => c.copy(playHome = Some(s)) } text("Play directory"),
        arg[String]("<apps-home>") action { (s, c) => c.copy(appsHome = Some(s)) } text("App directory"),
        arg[String]("<app-name>") action { (s, c) => c.copy(testOrAppName = Some(s)) } text("Test name or app name"),
        arg[String]("[<wrk arg>...]") optional() unbounded() action { (s, c) => c.copy(wrkArgs = c.wrkArgs :+ s) } text("Wrk arguments"),
        opt[Int]("max-wrk-duration") action { (i, c) =>
          c.copy(maxWrkDuration = Some(i))
        },
        opt[Unit]("skip-play-build") action { (_, c) =>
          c.copy(playBuild = false)
        },
        opt[Unit]("skip-app-build") action { (_, c) =>
          c.copy(appBuild = false)
        }
      )
    }
    parser.parse(rawArgs, Args()).getOrElse(sys.error("Arg parse error"))
  }
}

case class PlayTestsConfig(
  playBranch: String,
  playRevisionRange: (String, String),
  playRevisionSampling: Double,
  appsBranch: String,
  appsRevision: String,
  testNames: Seq[String]
)

case class TestConfig(
  app: String,
  description: String,
  wrkArgs: Seq[String]
)