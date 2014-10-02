/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.UUID
import java.util.concurrent.TimeUnit

import Exec._

object RunTest {

  def runTestTask(testTask: TestTask)(implicit ctx: Context): Unit = {
    import testTask.info.{ appName, testName }
    BuildApp.buildApp(
      playBranch = testTask.playBranch,
      playCommit = testTask.info.playCommit,
      appsBranch = testTask.appsBranch,
      appsCommit = testTask.info.appsCommit,
      appName = appName
    )

    val stageDirRelativePath = "target/universal/stage"
    // val stageDir: Path = testDir.resolve())
    // val pidFile: Path = stageDir.resolve("RUNNING_PID")
    val pidFile: Path = Paths.get(ctx.appsHome, appName, stageDirRelativePath, "RUNNING_PID")

    if (Files.exists(pidFile)) {
      println(s"Can't run test app ${appName} because $pidFile already exists")
      return
    }

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val testRunId = UUID.randomUUID()
    println(s"Starting test ${testName} run $testRunId")
    
    val testConfig = ctx.testConfig.get(testName).getOrElse(sys.error(s"No test config for test $testName"))

    if (canConnectTo("localhost", 9000, timeout = 50)) {
      sys.error("Can't start server: port already in use")
    }

      val terminateServer: () => Execution = runAsync(
    // val serverE = run(
      Command(
        s"bin/${appName}",
        args = Seq(),
        workingDir = s"<tests.home>/${appName}/$stageDirRelativePath",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "JAVA_OPTS" -> ctx.config.getString("java8.opts")
        )
      ),
      Capture
      //timeout = Option(20000) // TODO: Timeout
    )
    var terminated = false // Mark termination so we can terminate in finally clause if there's an error
    try {

      val serverStarted = pollFor(max = 10000, interval = 50) { canConnectTo("localhost", 9000, timeout = 50) }
      if (!serverStarted) {
        println("Server didn't start, aborting test")
        return
      }

      {

        // TODO: Capture wrk version

        def runWrk(durationConfigName: String): Execution = {
          val wrkConfig = ctx.config.getConfig("wrk")
          val threads = wrkConfig.getInt("threads")
          val connections = wrkConfig.getInt("connections")
          val duration = if (ctx.args.quickTests) {
            println("Overriding wrk duration so it runs more quickly")
            2
          } else wrkConfig.getDuration(durationConfigName, TimeUnit.SECONDS)

          println(s"Running wrk with "+testConfig.wrkArgs.mkString(" ")+s" for ${duration}s")
          run(
            Command(
              program = "wrk",
              args = Seq(s"-t$threads", s"-c$connections", s"-d${duration}s", "-s<assets.home>/wrk_report.lua") ++ testConfig.wrkArgs,
              env = Map(),
              workingDir = "<prune.home>"
            ),
            Capture,
            timeout = Some((duration + 10) * 1000)
          )
        }
        val warmupExecution: Execution = runWrk("warmupTime")
        val testExecution: Execution = runWrk("testTime")

        terminated = true
        val serverExecution = terminateServer()

        val testRunRecord = TestRunRecord(
          appBuildId = PrunePersistentState.read.flatMap(_.lastAppBuilds.get(appName)).get,
          testName = testName,
          javaVersionExecution = javaVersionExecution,
          serverExecution = serverExecution,
          wrkExecutions = Seq(
            warmupExecution,
            testExecution
          )
        )

        TestRunRecord.write(testRunId, testRunRecord)
      }
    } finally {
      if (!terminated) terminateServer()
    }
  }

  private def canConnectTo(host: String, port: Int, timeout: Int): Boolean = {
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

  private def pollFor(max: Long = 5000, interval: Long = 100)(condition: => Boolean): Boolean = {
    val deadlineTime = System.currentTimeMillis + max
    while (System.currentTimeMillis < deadlineTime) {
      if (condition) return true
      Thread.sleep(interval)
    }
    return false
  }

}