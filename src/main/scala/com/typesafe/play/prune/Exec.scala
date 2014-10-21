/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.io._
import java.nio.file._
import java.util.{ Map => JMap }
import java.util.concurrent.TimeUnit
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.apache.commons.exec._
import org.joda.time._
import scala.collection.JavaConversions
import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object Exec {

  private case class Prepared(
    executor: DefaultExecutor,
    commandLine: CommandLine,
    environment: JMap[String, String],
    watchdog: ExecuteWatchdog,
    streamResultGetter: () => Option[(String, String)]
  )

  private def prepare(
    command: Command,
    streamHandling: StreamHandling,
    timeout: Option[Long] = None)(implicit ctx: Context): Prepared = {

    def configureCommand(command: Command): Command = {
      val replacements: Map[String,String] = Map(
        "assets.home" -> ctx.assetsHome,
        "prune.home" -> ctx.pruneHome,
        "play.home" -> ctx.playHome,
        "apps.home" -> ctx.appsHome,
        "java8.home" -> ctx.java8Home,
        "ivy.home" -> ctx.ivyHome,
        "server.url" -> "http://localhost:9000"
      )
      replacements.foldLeft(command) {
        case (c, (name, value)) => c.replace("<"+name+">", value)
      }
    }

    val configuredCommand = configureCommand(command)
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

  def runAsync(
    command: Command,
    streamHandling: StreamHandling,
    errorOnNonZeroExit: Boolean = true,
    timeout: Option[Long] = None)(implicit ctx: Context): () => Try[Execution] = {
    val prepared = prepare(command, streamHandling, timeout)
    import prepared._
    val resultHandler = new DefaultExecuteResultHandler()
    val startTime = DateTime.now
    executor.execute(commandLine, environment, resultHandler)

    @volatile
    var destroyResult: Option[Try[Execution]] = None
    () => {
      destroyResult match {
        case Some(e) => e
        case None =>
          val r = Try {
            watchdog.destroyProcess()
            resultHandler.waitFor(10000)
            val endTime = DateTime.now
            val returnCode = if (resultHandler.hasResult) resultHandler.getExitValue else resultHandler.getException.getExitValue
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
          destroyResult = Some(r)
          r
      }
    }
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
    override def start() = {
      stdoutConsumer.foreach { is =>
        stdoutOutput.completeWith(Future {
          //println("Consuming stdout")
          IOUtils.toString(is)
        })
      }
      stderrConsumer.foreach { is =>
        stderrOutput.completeWith(Future {
          //println("Consuming stderr")
          IOUtils.toString(is)
        })
      }
      stdinProducer.foreach { os =>
        //println("Closing stdin")
        Future { os.close() }
      }
    }
    override def stop() = {}
  }

}