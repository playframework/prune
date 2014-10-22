/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.UUID
import java.util.concurrent.TimeUnit

import Exec._

import scala.util.Try

object RunTest {

  def runTestTask(testTask: TestTask)(implicit ctx: Context): Unit = {
    import testTask.playBranch
    import testTask.info.{ appName, testName, playCommit }
    BuildApp.buildApp(
      playBranch = testTask.playBranch,
      playCommit = testTask.info.playCommit,
      appsBranch = testTask.appsBranch,
      appsCommit = testTask.appsCommit,
      appName = appName
    )

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val testConfig = ctx.testConfig.get(testName).getOrElse(sys.error(s"No test config for test $testName"))

    val testRunId = UUID.randomUUID()
    println(s"Running test ${testName} on app $appName for Play ${playCommit.substring(0, 7)} [$playBranch] run: $testRunId")
    val testExecutions = runTestDirectly(appName, testConfig.wrkArgs)

    val testRunRecord = TestRunRecord(
      appBuildId = PrunePersistentState.read.flatMap(_.lastAppBuilds.get(appName)).get,
      testName = testName,
      javaVersionExecution = javaVersionExecution,
      serverExecution = testExecutions.serverExecution,
      wrkExecutions = testExecutions.wrkExecutions
    )

    TestRunRecord.write(testRunId, testRunRecord)
  }

  private def withServer[A](appName: String, extraJavaOpts: Seq[String])(body: => A)(implicit ctx: Context): (Execution, A) = {
    val stageDirRelativePath = "target/universal/stage"
    val pidFile: Path = Paths.get(ctx.appsHome, appName, stageDirRelativePath, "RUNNING_PID")

    if (Files.exists(pidFile)) {
      sys.error(s"Can't run test app ${appName} because $pidFile already exists")
    }

    def canConnectTo(host: String, port: Int, timeout: Int): Boolean = {
      import java.io.IOException
      import java.net._
      val addr = new InetSocketAddress(host, port)
      val socket = new Socket()
      try {
        socket.connect(addr, timeout)
        socket.close()
        true
      } catch {
        case _: IOException => false
      }
    }

    def pollFor(max: Long = 5000, interval: Long = 100)(condition: => Boolean): Boolean = {
      val deadlineTime = System.currentTimeMillis + max
      while (System.currentTimeMillis < deadlineTime) {
        if (condition) return true
        Thread.sleep(interval)
      }
      return false
    }

    if (canConnectTo("localhost", 9000, timeout = 50)) {
      sys.error("Can't start server: port already in use")
    }

    val terminateServer: () => Try[Execution] = runAsync(
      Command(
        s"bin/${appName}",
        args = Seq(),
        workingDir = s"<apps.home>/${appName}/$stageDirRelativePath",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "JAVA_OPTS" -> (ctx.java8Opts ++ extraJavaOpts).mkString(" ")
        )
      ),
      Capture,
      errorOnNonZeroExit = false
    )

    try {

      val serverStarted = pollFor(max = 10000, interval = 50) { canConnectTo("localhost", 9000, timeout = 50) }
      if (!serverStarted) {
        val e: Try[Execution] = terminateServer()
        println(s"Server execution: $e")
        sys.error("Server didn't start, aborting test")
      }

      val bodyResult = body
      val serverExecution = terminateServer().get
      (serverExecution, bodyResult)
    } finally {
      terminateServer()
    }
  }

  private def runWrk(durationSeconds: Long, wrkArgs: Seq[String])(implicit ctx: Context): Execution = {
    val wrkConfig = ctx.config.getConfig("wrk")
    val threads = wrkConfig.getInt("threads")
    val connections = wrkConfig.getInt("connections")
    println(s"Running wrk with "+wrkArgs.mkString(" ")+s" for ${durationSeconds}s")
    run(
      Command(
        program = "wrk",
        args = Seq(s"-t$threads", s"-c$connections", s"-d${durationSeconds}s", "-s<assets.home>/wrk_report.lua") ++ wrkArgs,
        env = Map(),
        workingDir = "<prune.home>"
      ),
      Capture,
      timeout = Some((durationSeconds + 10) * 1000)
    )
  }

  private def getOverriddenWrkDuration(durationConfigName: String)(implicit ctx: Context): Long = {
    val configuredDuration = ctx.config.getDuration(durationConfigName, TimeUnit.SECONDS)
    ctx.args.maxWrkDuration.fold(configuredDuration) { i =>
      if (i < configuredDuration) {
        println(s"Overriding wrk duration from $configuredDuration down to $i")
        i
      } else configuredDuration
    }
  }

  case class TestExecutions(serverExecution: Execution, wrkExecutions: Seq[Execution])

  private def runServerAndWrk(appName: String, extraJavaOpts: Seq[String], wrkArgs: Seq[String], wrkDurations: Seq[Long])(implicit ctx: Context): TestExecutions = {
    val (serverExecution, wrkExecutions): (Execution, Seq[Execution]) = withServer[Seq[Execution]](appName, extraJavaOpts) {
      wrkDurations.map(runWrk(_, wrkArgs))
    }
    val eitherStdout: Either[String, String] = wrkExecutions.last.stdout.toRight("No stdout from wrk")
    val wrkResult: Either[String, WrkResult] = eitherStdout.right.flatMap(Results.parseWrkOutput)
    val message: String = wrkResult.right.flatMap(_.summary.right.map(_.display)).merge
    println(message)
    TestExecutions(serverExecution, wrkExecutions)
  }

  def runTestDirectly(appName: String, wrkArgs: Seq[String])(implicit ctx: Context): TestExecutions = {
    val warmupTime: Long = getOverriddenWrkDuration("wrk.warmupTime")
    val testTime: Long = getOverriddenWrkDuration("wrk.testTime")
    runServerAndWrk(appName, Seq.empty, wrkArgs, Seq(warmupTime, testTime))
  }

  def runProfileDirectly(appName: String, wrkArgs: Seq[String], sessionName: String)(implicit ctx: Context): TestExecutions = {
    val warmupTime: Long = getOverriddenWrkDuration("wrk.warmupTime")
    val unpaddedTestTime: Long = getOverriddenWrkDuration("yourkit.testTime")
    val paddingTime: Long = ctx.config.getDuration("yourkit.wrkDelayPaddingTime", TimeUnit.SECONDS)
    println(s"Padding wrk test duration by ${paddingTime}s to allow time for YourKit agent to start")


    val snapshotDir = Paths.get(ctx.yourkitHome, "snapshots")
    val logsDir = Paths.get(ctx.yourkitHome, "logs")

    val testTime: Long = unpaddedTestTime + paddingTime
    val delayTime: Long = warmupTime + paddingTime
    val extraJavaOpts = {
      val replacements: Map[String,String] = Map(
        "session.name" -> sessionName,
        "delay" -> delayTime.toString
      )
      replacements.foldLeft(ctx.yourkitJavaOpts) {
        case (opts, (name, value)) => opts.map(_.replace("#"+name+"#", value))
      }
    }

    if (Files.notExists(snapshotDir)) Files.createDirectories(snapshotDir)
    if (Files.notExists(logsDir)) Files.createDirectories(logsDir)

    runServerAndWrk(appName, extraJavaOpts, wrkArgs, Seq(warmupTime, testTime))
  }

}