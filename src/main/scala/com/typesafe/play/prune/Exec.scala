/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.io._
import java.nio.file._
import java.util.{Map => JMap}
import java.util.concurrent.TimeUnit

import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.commons.exec._
import org.joda.time._

import scala.collection.JavaConversions
import scala.concurrent._
import scala.concurrent.duration.{Deadline, Duration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}

object Exec {

  private case class Prepared(
    executor: DefaultExecutor,
    commandLine: CommandLine,
    environment: JMap[String, String],
    watchdog: ExecuteWatchdog,
    streamResultGetter: () => Option[(String, String)]
  )

  def replaceContextValues(s: String)(implicit ctx: Context): String = {
    val replacements = Map(
      "<assets.home>" -> ctx.assetsHome,
      "<prune.home>" -> ctx.pruneHome,
      "<play.home>" -> ctx.playHome,
      "<apps.home>" -> ctx.appsHome,
      "<java8.home>" -> ctx.java8Home,
      "<ivy.home>" -> ctx.ivyHome,
      "<server.url>" -> "http://localhost:9000",
      "<yourkit.agent>" -> ctx.yourkitAgent,
      "<yourkit.home>" -> ctx.yourkitHome
    )
    replacements.foldLeft(s) {
      case (s, (name, value)) => s.replace(name, value)
    }
  }

  private def prepare(
    command: Command,
    streamHandling: StreamHandling,
    timeout: Option[Long] = None)(implicit ctx: Context): Prepared = {

    val configuredCommand = command.mapStrings(replaceContextValues(_))
    //println(s"Configured command: $configuredCommand")
    val commandLine = new CommandLine(configuredCommand.program)
    for (arg <- configuredCommand.args) { commandLine.addArgument(arg) }

    //Files.createDirectories(Paths.get(configuredCommand.workingDir))

    val executor = new DefaultExecutor()
    executor.setWorkingDirectory(new File(configuredCommand.workingDir))
    //println(s"Set working directory: ${configuredCommand.workingDir}")
    val watchdog = new ExecuteWatchdog(timeout.getOrElse(ExecuteWatchdog.INFINITE_TIMEOUT))
    executor.setWatchdog(watchdog)
    //println(s"Attaching stream handling: $streamHandling")
    val streamResultGetter = streamHandling.attach(executor)

    val prepared = Prepared(
      executor,
      commandLine,
      JavaConversions.mapAsJavaMap(configuredCommand.env),
      watchdog,
      streamResultGetter
    )
    //println(s"Prepared run: $prepared")
    prepared
  }

  trait StreamHandling {
    def attach(executor: Executor): () => Option[(String, String)]
  }
  object Pump extends StreamHandling {
    def attach(executor: Executor) = {
      executor.setStreamHandler(new PumpStreamHandler)
      () => None
    }
  }
  object Capture extends StreamHandling {
    def attach(executor: Executor) = {
      val sh = new CapturingStreamHandler
      executor.setStreamHandler(sh)
      () => Some((sh.stdout, sh.stderr))
    }
  }

  def run(
    command: Command,
    streamHandling: StreamHandling,
    errorOnNonZeroExit: Boolean = true,
    timeout: Option[Long] = None)(implicit ctx: Context): Execution = {
    val prepared = prepare(command, streamHandling, timeout)
    import prepared._
    val startTime = DateTime.now
    val returnCode = try {
      executor.execute(commandLine, environment)
    } catch {
      case ee: ExecuteException => ee.getExitValue
    }
    val endTime = DateTime.now
    val streamResult = streamResultGetter()

    val execution = Execution(
      command = command,
      stdout = streamResult.map(_._1),
      stderr = streamResult.map(_._2),
      returnCode = Some(returnCode),
      startTime = startTime,
      endTime = endTime
    )
    if (errorOnNonZeroExit && execution.returnCode.fold(false)(_ != 0)) {
      sys.error(s"Execution failed: $execution")
    } else execution
  }

  trait RunAsyncHandle {
    def destroyProcess(): Execution
    def result: Future[Execution]
  }

  def runAsync(
    command: Command,
    streamHandling: StreamHandling,
    errorOnNonZeroExit: Boolean = true,
    timeout: Option[Long] = None)(implicit ctx: Context): RunAsyncHandle = {
    val prepared: Prepared = prepare(command, streamHandling, timeout)

    val handle = new RunAsyncHandle with ExecuteResultHandler {
      private val startTime: DateTime = DateTime.now
      private val resultPromise: Promise[Execution] = Promise[Execution]

      override def result: Future[Execution] = resultPromise.future

      override def onProcessComplete(exitValue: Int): Unit = synchronized {
        val endTime: DateTime = DateTime.now

        if (errorOnNonZeroExit && exitValue != 0) {
          resultPromise.failure(new IOException(s"Execution failed: $command"))
        } else {

          // Get the contents of stdout/stderr
          val streamResult: Option[(String, String)] = prepared.streamResultGetter()

          val execution = Execution(
            command = command,
            stdout = streamResult.map(_._1),
            stderr = streamResult.map(_._2),
            returnCode = Some(exitValue),
            startTime = startTime,
            endTime = endTime
          )
          resultPromise.success(execution)
        }
      }

      override def onProcessFailed(e: ExecuteException): Unit = synchronized {
        resultPromise.failure(e)
      }

      override def destroyProcess(): Execution = synchronized {
        if (!result.isCompleted) {
          prepared.watchdog.destroyProcess()
          val deadline = Deadline.now + Duration(ctx.testShutdownSeconds, TimeUnit.SECONDS)
          while (!result.isCompleted && deadline.hasTimeLeft()) { Thread.sleep(50) }
        }
        result.value.get.get
      }

    }

    try {
      prepared.executor.execute(
        prepared.commandLine,
        prepared.environment,
        handle)
    } catch {
      case e: ExecutionException =>
    }

    handle
  }

  private[Exec] class CapturingStreamHandler extends ExecuteStreamHandler {
    private var stdoutConsumer: Option[InputStream] = None
    private var stderrConsumer: Option[InputStream] = None
    private var stdinProducer: Option[OutputStream] = None
    private val stdoutOutput = Promise[String]()
    private val stderrOutput = Promise[String]()
    def stdout = Await.result(stdoutOutput.future, Duration.Inf)
    def stderr = Await.result(stderrOutput.future, Duration.Inf)
    override def setProcessOutputStream(is: InputStream) = stdoutConsumer = Some(is)
    override def setProcessErrorStream(is: InputStream) = stderrConsumer = Some(is)
    override def setProcessInputStream(os: OutputStream) = stdinProducer = Some(os)

    private def safeButInefficientToString(is: InputStream): String = {

      val maxSize = 1024 * 1024 // Set max size of output to 1mb
      val baos = new ByteArrayOutputStream()

      /**
       * Recursive function to copy stream up to a limit. We need to apply a limit
       * to avoid 
       */
      @scala.annotation.tailrec
      def copyAll(size: Int, truncate: Boolean): Unit = {

        /** Write to the buffer and log a message if an OOME is caught. */
        def logOutOfMemoryError[A](f: => A): A = {
          try f catch {
            case oome: OutOfMemoryError  =>
              // Print out some diagnostic information
              print("OOME hit at size ")
              println(size)
              throw oome
          }
        }

        val c = try is.read() catch {
          // Work around JVM concurrency bug: http://bugs.java.com/view_bug.do?bug_id=5101298
          case ioe: IOException if ioe.getMessage.toLowerCase.contains("stream closed") => -1
        }

        if (c == -1) {
          // Do nothing and terminate the loop
        } else if (size < maxSize) {
          logOutOfMemoryError {
            baos.write(c)
          }
          copyAll(size + 1, truncate = false)
        } else if (!truncate) {
          // We've captured more than maxSize, ignore the rest
          println(s"Process output exceeds $maxSize bytes, truncating.")
          val truncateMessage = s"\n--- Truncated output to $maxSize bytes ---"
          logOutOfMemoryError {
            baos.write(truncateMessage.getBytes("ASCII"))
          }
          copyAll(size, truncate = true)
        } else if (truncate) {
          // We've already started truncating, just keep doing it

          copyAll(size, truncate = true)
        }
      }
      copyAll(0, truncate = false)

      baos.toString("ASCII")
    }

    override def start() = {
      stdoutConsumer.foreach { is =>
        stdoutOutput.completeWith(Future {
          safeButInefficientToString(is)
        })
      }
      stderrConsumer.foreach { is =>
        stderrOutput.completeWith(Future {
          safeButInefficientToString(is)
        })
      }
      stdinProducer.foreach { os =>
        Future { os.close() }
      }
    }
    override def stop() = {}
  }

}