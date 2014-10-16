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
      appsCommit = testTask.appsCommit,
      appName = appName
    )

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val testConfig = ctx.testConfig.get(testName).getOrElse(sys.error(s"No test config for test $testName"))

    val testRunId = UUID.randomUUID()
    println(s"Starting test ${testName} run $testRunId")
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

  case class TestExecutions(serverExecution: Execution, wrkExecutions: Seq[Execution])

  def runTestDirectly(appName: String, wrkArgs: Seq[String])(implicit ctx: Context): TestExecutions = {

    def withServer[A](body: => (Execution => A)): A = {
      val stageDirRelativePath = "target/universal/stage"
      val pidFile: Path = Paths.get(ctx.appsHome, appName, stageDirRelativePath, "RUNNING_PID")

      println(pidFile)
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

      val terminateServer: () => Execution = {
        val directTerminate: () => Execution = runAsync(
          Command(
            s"bin/${appName}",
            args = Seq(),
            workingDir = s"<apps.home>/${appName}/$stageDirRelativePath",
            env = Map(
              "JAVA_HOME" -> "<java8.home>",
              "JAVA_OPTS" -> ctx.config.getString("java8.opts")
            )
          ),
          Capture,
          errorOnNonZeroExit = false
        )

        @volatile
        var terminatedExecution: Option[Execution] = None
        val guardedTerminate: () => Execution = { () =>
          terminatedExecution.getOrElse {
            val e = directTerminate()
            terminatedExecution = Some(e)
            e
          }
        }

        guardedTerminate
      }
      try {

        val serverStarted = pollFor(max = 10000, interval = 50) { canConnectTo("localhost", 9000, timeout = 50) }
        if (!serverStarted) {
          val e: Execution = terminateServer()
          println(s"Server execution: $e")
          sys.error("Server didn't start, aborting test")
        }

        val f = body
        val serverExecution = terminateServer()
        f(serverExecution)
      } finally {
        terminateServer()
      }
    }

    def runWrk(durationConfigName: String): Execution = {
      val wrkConfig = ctx.config.getConfig("wrk")
      val threads = wrkConfig.getInt("threads")
      val connections = wrkConfig.getInt("connections")
      val duration = {
        val configuredDuration = wrkConfig.getDuration(durationConfigName, TimeUnit.SECONDS)
        ctx.args.maxWrkDuration.fold(configuredDuration) { i =>
          if (i < configuredDuration) {
            println(s"Overriding wrk duration from $configuredDuration down to $i")
            i
          } else configuredDuration
        }
      }
      println(s"Running wrk with "+wrkArgs.mkString(" ")+s" for ${duration}s")
      run(
        Command(
          program = "wrk",
          args = Seq(s"-t$threads", s"-c$connections", s"-d${duration}s", "-s<assets.home>/wrk_report.lua") ++ wrkArgs,
          env = Map(),
          workingDir = "<prune.home>"
        ),
        Capture,
        timeout = Some((duration + 10) * 1000)
      )
    }

    withServer[TestExecutions] {
      val warmupExecution: Execution = runWrk("warmupTime")
      val testExecution: Execution = runWrk("testTime")
      (serverExecution: Execution) => TestExecutions(serverExecution, Seq(warmupExecution, testExecution))
    }
  }

}