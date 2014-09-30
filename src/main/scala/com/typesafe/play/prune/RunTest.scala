/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

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

import Exec._
import PruneGit._

object RunTest {

  def runTestTask(testTask: TestTask)(implicit ctx: Context): Unit = {
    // println(s"Preparing test ${testTask.name}")
    BuildTest.buildTestProject(
      playBranch = testTask.playBranch,
      playCommit = testTask.info.playCommit,
      testsBranch = testTask.testsBranch,
      testsCommit = testTask.info.testsCommit,
      testProject = testTask.info.testProject
    )

    val stageDirRelativePath = "target/universal/stage"
    // val stageDir: Path = testDir.resolve())
    // val pidFile: Path = stageDir.resolve("RUNNING_PID")
    val pidFile: Path = Paths.get(ctx.testsHome, testTask.info.testProject, stageDirRelativePath, "RUNNING_PID")

    if (Files.exists(pidFile)) {
      println(s"Can't run test app ${testTask.info.testProject} because $pidFile already exists")
      return
    }

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val testRunId = UUID.randomUUID()
    println(s"Starting test ${testTask.info.testName} run $testRunId")

    // println("Starting test app scala-bench")
    val terminateServer: () => Execution = runAsync(
    // val serverE = run(
      Command(
        s"bin/${testTask.info.testProject}",
        args = Seq(),
        workingDir = s"<tests.home>/${testTask.info.testProject}/$stageDirRelativePath",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "JAVA_OPTS" -> ctx.config.getString("java8.opts")
        )
      ),
      Capture
      //timeout = Option(20000) // TODO: Timeout
    )
    // val terminateServer: () => Execution = () => serverE
    // System.exit(1)
    var terminated = false // Mark termination so we can terminate in finally clause if there's an error
    try {

      val serverStarted = pollFor(max = 10000, interval = 50) { canConnectTo("localhost", 9000, timeout = 50) }
      if (!serverStarted) {
        println("Server didn't start, aborting test")
        return
      }

      {
        // wrk {
        //   warmupTime: 2 seconds
        //   testTime: 2 seconds
        //   connections: 32
        //   threads: 16
        // }

        // TODO: Move test details to configuration
        val requestPath: String = testTask.info.testName match {
          case "scala-hello-world" => "/helloworld"
          case "scala-download-50k" => "/download/51200"
          case "scala-download-chunked-50k" => "/download-chunked/51200"
          case name => sys.error(s"Unknown test name: $name")
        }

        // TODO: Capture wrk version

        def runWrk(durationConfigName: String): Execution = {
          val wrkConfig = ctx.config.getConfig("wrk")
          val threads = wrkConfig.getInt("threads")
          val connections = wrkConfig.getInt("connections")
          val duration = wrkConfig.getDuration(durationConfigName, TimeUnit.SECONDS)

          println(s"Running wrk on $requestPath for ${duration}s")
          run(
            Command(
              program = "wrk",
              args = Seq(s"-t$threads", s"-c$connections", s"-d${duration}s", s"http://localhost:9000$requestPath"),
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
          PrunePersistentState.read.flatMap(_.lastTestBuilds.get(testTask.info.testProject)).get,
          testTask.info.testName,
          javaVersionExecution,
          serverExecution,
          Seq(
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