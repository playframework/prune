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
    import testTask._
    BuildTest.buildTestProject(
      playCommit = playCommit,
      testsCommit = testsCommit,
      testProject = testProject
    )

    val stageDirRelativePath = "target/universal/stage"
    // val stageDir: Path = testDir.resolve())
    // val pidFile: Path = stageDir.resolve("RUNNING_PID")
    val pidFile: Path = ctx.testsRepoConfig.localDirPath.resolve(Paths.get(testProject, stageDirRelativePath, "RUNNING_PID"))

    if (Files.exists(pidFile)) {
      println(s"Can't run test app $testProject because $pidFile already exists")
      return
    }

    val javaVersionExecution: Execution = JavaVersion.captureJavaVersion()

    val testRunId = UUID.randomUUID()
    println(s"Starting test ${testTask.name} run $testRunId")

    // println("Starting test app scala-bench")
    val terminateServer: () => Execution = runAsync(
    // val serverE = run(
      Command(
        s"bin/$testProject",
        args = Seq(),
        workingDir = s"<tests.home>/$testProject/$stageDirRelativePath",
        env = Map(
          "JAVA_HOME" -> "<java8.home>",
          "JAVA_OPTS" -> "-Xms1g -Xmx1g -verbose:gc" // -XX:+PrintFlagsFinal
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
          ctx.pruneInstanceId,
          PrunePersistentState.read.flatMap(_.lastTestBuilds.get(testProject)).get,
          testTask.name,
          javaVersionExecution,
          serverExecution,
          Seq(
            warmupExecution,
            testExecution
          )
        )

        def testRunRecordPath(id: UUID): Path = {
          Paths.get(ctx.config.getString("db-repo.local-dir"), "test-runs", id.toString+".json")
        }
        def writeTestRunRecord(id: UUID, record: TestRunRecord): Unit = {
          println(s"Writing test run record $id")
          Records.writeFile(testRunRecordPath(id), record)
        }
        def readTestRunRecord(id: UUID): Option[TestRunRecord] = {
          Records.readFile[TestRunRecord](testRunRecordPath(id))
        }

        writeTestRunRecord(testRunId, testRunRecord)
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