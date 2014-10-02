/*
 * Copyright (C) 2014 Typesafe Inc. <http://www.typesafe.com>
 */
package com.typesafe.play.prune

import java.nio.file._
import java.util.UUID
import org.apache.commons.io.FileUtils
import org.joda.time._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import scala.collection.convert.WrapAsScala._

object Records {

  def readFile[T](p: Path)(implicit reads: Reads[T]): Option[T] = {
    if (Files.exists(p)) {
      val bytes = Files.readAllBytes(p)
      val json = Json.parse(bytes)
      val t = reads.reads(json).get
      //println("Read: "+new String(bytes)+"->"+t)
      Some(t)
    } else None
  }

  def writeFile[T](p: Path, t: T)(implicit writes: Writes[T]): Unit = {
    val json = writes.writes(t)
    val bytes = Json.prettyPrint(json).getBytes("UTF-8")
    //println("Write: "+t+"->"+new String(bytes))
    Files.createDirectories(p.getParent)
    Files.write(p, bytes)
  }

  def readAll[T](dir: Path)(implicit ctx: Context, reads: Reads[T]): Map[UUID,T] = {
    foldLeftAll[T, Map[UUID,T]](dir, Map.empty) {
      case (m, id, record) => m.updated(id, record)
    }
  }

  def foldLeftAll[T,A](dir: Path, x0: A)(f: (A, UUID, T) => A)(implicit ctx: Context, reads: Reads[T]): A = {
    val files = collectionAsScalaIterable(FileUtils.listFiles(dir.toFile, Array("json"), true))
    files.foldLeft(x0) {
      case (x, file) =>
        val p = file.toPath
        val id: UUID = {
          val fileName = p.getFileName.toString
          val dotIndex = fileName.lastIndexOf('.')
          assert(dotIndex != -1)
          val baseFileName = fileName.substring(0, dotIndex)
          UUID.fromString(baseFileName)
        }
        val record: T = Records.readFile[T](p).getOrElse(sys.error(s"Expected record at $p"))
        f(x, id, record)
    }
  }

}

object Implicits {

  val DateFormat = "yyyy-MM-dd'T'HH:mm:ssz"

  implicit def dateReads = Reads.jodaDateReads(DateFormat)

  implicit def dateWrites = Writes.jodaDateWrites(DateFormat)

  implicit def uuidWrites = new Writes[UUID] {
    def writes(uuid: UUID) = JsString(uuid.toString)
  }
  implicit def uuidReads = new Reads[UUID] {
    def reads(json: JsValue): JsResult[UUID] = json match {
      case JsString(s) => try {
          JsSuccess(UUID.fromString(s))
        } catch {
          case _: IllegalArgumentException => JsError("Invalid UUID string")
        }
      case _ => JsError("UUIDs must be encoded as JSON strings")
    }
  }
}

case class PrunePersistentState(
  lastPlayBuild: Option[UUID],
  lastAppBuilds: Map[String,UUID]
)

object PrunePersistentState {

  implicit val writes = new Writes[PrunePersistentState] {
    def writes(prunePersistentState: PrunePersistentState) = Json.obj(
      "lastPlayBuild" -> prunePersistentState.lastPlayBuild,
      "lastAppBuilds" -> prunePersistentState.lastAppBuilds
    )
  }

  implicit val reads: Reads[PrunePersistentState] = (
    (JsPath \ "lastPlayBuild").readNullable[UUID] and
    (JsPath \ "lastAppBuilds").read[Map[String,UUID]]
  )(PrunePersistentState.apply _)

  def path(implicit ctx: Context): Path = {
    Paths.get(ctx.pruneHome).resolve("state.json")
  }

  def read(implicit ctx: Context): Option[PrunePersistentState] = {
    Records.readFile[PrunePersistentState](path)
  }

  def readOrElse(implicit ctx: Context): PrunePersistentState = {
    read.getOrElse(PrunePersistentState(
      lastPlayBuild = None, lastAppBuilds = Map.empty
    ))
  }

  def write(state: PrunePersistentState)(implicit ctx: Context): Unit = {
    Records.writeFile(path, state)
  }

}

object Command {

  implicit val writes = new Writes[Command] {
    def writes(command: Command) = Json.obj(
      "program" -> command.program,
      "args" -> command.args,
      "workingDir" -> command.workingDir,
      "env" -> command.env
    )
  }

  implicit val reads: Reads[Command] = (
    (JsPath \ "program").read[String] and
    (JsPath \ "args").read[Seq[String]] and
    (JsPath \ "workingDir").read[String] and
    (JsPath \ "env").read[Map[String, String]]
  )(Command.apply _)

}

case class Execution(
  command: Command,
  startTime: DateTime,
  endTime: DateTime,
  stdout: Option[String],
  stderr: Option[String],
  returnCode: Option[Int]
)


object Execution {

  implicit val writes = new Writes[Execution] {
    def writes(execution: Execution) = Json.obj(
      "command" -> execution.command,
      "startTime" -> execution.startTime,
      "endTime" -> execution.endTime,
      "stdout" -> execution.stdout,
      "stderr" -> execution.stderr,
      "returnCode" -> execution.returnCode
    )
  }

  implicit val reads: Reads[Execution] = (
    (JsPath \ "command").read[Command] and
    (JsPath \ "startTime").read[DateTime] and
    (JsPath \ "endTime").read[DateTime] and
    (JsPath \ "stdout").readNullable[String] and
    (JsPath \ "stderr").readNullable[String] and
    (JsPath \ "returnCode").readNullable[Int]
  )(Execution.apply _)

}

// {
//   "playCommit": "a1b2...",
//   "javaVersion": "java version \"1.8.0_05\"\nJava(TM) SE Runtime Environment (build 1.8.0_05-b13)Java HotSpot(TM) 64-Bit Server VM (build 25.5-b02, mixed mode)",
//   "buildCommands": [
//     ["./build", "-Dsbt.ivy.home=~/.prune/ivy", "clean"],
//     ["./build", "-Dsbt.ivy.home=~/.prune/ivy", "publish-local"],
//     ["./build", "-Dsbt.ivy.home=~/.prune/ivy", "-Dscala.version=2.11.2", "publish-local"]
//   ]
// }

case class PlayBuildRecord(
  pruneInstanceId: UUID,
  playCommit: String,
  javaVersionExecution: Execution,
  buildExecutions: Seq[Execution]
)

object PlayBuildRecord {

  implicit val writes = new Writes[PlayBuildRecord] {
    def writes(playBuildRecord: PlayBuildRecord) = Json.obj(
      "pruneInstanceId" -> playBuildRecord.pruneInstanceId,
      "playCommit" -> playBuildRecord.playCommit,
      "javaVersionExecution" -> playBuildRecord.javaVersionExecution,
      "buildExecutions" -> playBuildRecord.buildExecutions
    )
  }

  implicit val reads: Reads[PlayBuildRecord] = (
    (JsPath \ "pruneInstanceId").read[UUID] and
    (JsPath \ "playCommit").read[String] and
    (JsPath \ "javaVersionExecution").read[Execution] and
    (JsPath \ "buildExecutions").read[Seq[Execution]]
  )(PlayBuildRecord.apply _)

  def path(id: UUID)(implicit ctx: Context): Path = {
    Paths.get(ctx.dbHome, "play-builds", id.toString+".json")
  }
  def write(id: UUID, record: PlayBuildRecord)(implicit ctx: Context): Unit = {
    Records.writeFile(path(id), record)
  }
  def read(id: UUID)(implicit ctx: Context): Option[PlayBuildRecord] = {
    Records.readFile[PlayBuildRecord](path(id))
  }
  def readAll(implicit ctx: Context): Map[UUID,PlayBuildRecord] = {
    Records.readAll[PlayBuildRecord](Paths.get(ctx.dbHome, "play-builds"))
  }

}

case class AppBuildRecord(
  playBuildId: UUID,
  appName: String,
  appsCommit: String,
  javaVersionExecution: Execution,
  buildExecutions: Seq[Execution]
)

object AppBuildRecord {

  implicit val writes = new Writes[AppBuildRecord] {
    def writes(testBuildRecord: AppBuildRecord) = Json.obj(
      "playBuildId" -> testBuildRecord.playBuildId,
      "appName" -> testBuildRecord.appName,
      "appsCommit" -> testBuildRecord.appsCommit,
      "javaVersionExecution" -> testBuildRecord.javaVersionExecution,
      "buildExecutions" -> testBuildRecord.buildExecutions
    )
  }

  implicit val reads: Reads[AppBuildRecord] = (
    (JsPath \ "playBuildId").read[UUID] and
    (JsPath \ "appName").read[String] and
    (JsPath \ "appsCommit").read[String] and
    (JsPath \ "javaVersionExecution").read[Execution] and
    (JsPath \ "buildExecutions").read[Seq[Execution]]
  )(AppBuildRecord.apply _)

  def path(id: UUID)(implicit ctx: Context): Path = {
    Paths.get(ctx.dbHome, "app-builds", id.toString+".json")
  }
  def write(id: UUID, record: AppBuildRecord)(implicit ctx: Context): Unit = {
    Records.writeFile(path(id), record)
  }
  def read(id: UUID)(implicit ctx: Context): Option[AppBuildRecord] = {
    Records.readFile[AppBuildRecord](path(id))
  }
  def readAll(implicit ctx: Context): Map[UUID,AppBuildRecord] = {
    Records.readAll[AppBuildRecord](Paths.get(ctx.dbHome, "app-builds"))
  }

}

case class Command(
  program: String,
  args: Seq[String] = Nil,
  workingDir: String,
  env: Map[String, String]
) {
  private def mapStrings(f: String => String): Command = {
    Command(
      program = f(program),
      args = args.map(f),
      workingDir = f(workingDir),
      env = env.mapValues(f)
    )
  }
  def replace(original: String, replacement: String): Command = {
    mapStrings(_.replace(original, replacement))
  }  
}

case class TestRunRecord(
  appBuildId: UUID,
  testName: String,
  javaVersionExecution: Execution,
  serverExecution: Execution,
  wrkExecutions: Seq[Execution]
)

object TestRunRecord {

  implicit val writes = new Writes[TestRunRecord] {
    def writes(testBuildRecord: TestRunRecord) = Json.obj(
      "appBuildId" -> testBuildRecord.appBuildId,
      "testName" -> testBuildRecord.testName,
      "javaVersionExecution" -> testBuildRecord.javaVersionExecution,
      "serverExecution" -> testBuildRecord.serverExecution,
      "wrkExecutions" -> testBuildRecord.wrkExecutions
    )
  }

  implicit val reads: Reads[TestRunRecord] = (
    (JsPath \ "appBuildId").read[UUID] and
    (JsPath \ "testName").read[String] and
    (JsPath \ "javaVersionExecution").read[Execution] and
    (JsPath \ "serverExecution").read[Execution] and
    (JsPath \ "wrkExecutions").read[Seq[Execution]]
  )(TestRunRecord.apply _)

  def path(id: UUID)(implicit ctx: Context): Path = {
    Paths.get(ctx.dbHome, "test-runs", id.toString+".json")
  }
  def write(id: UUID, record: TestRunRecord)(implicit ctx: Context): Unit = {
    Records.writeFile(path(id), record)
  }
  def read(id: UUID)(implicit ctx: Context): Option[TestRunRecord] = {
    Records.readFile[TestRunRecord](path(id))
  }
  def readAll(implicit ctx: Context): Map[UUID,TestRunRecord] = {
    Records.readAll[TestRunRecord](Paths.get(ctx.dbHome, "test-runs"))
  }

}

case class DB(
  playBuilds: Map[UUID, PlayBuildRecord],
  appBuilds: Map[UUID, AppBuildRecord],
  testRuns: Map[UUID, TestRunRecord]
)
object DB {
  def read(implicit ctx: Context): DB = {
    val dbPath = Paths.get(ctx.dbHome)
    FileUtils.forceMkdir(dbPath.resolve("play-builds").toFile)
    FileUtils.forceMkdir(dbPath.resolve("app-builds").toFile)
    FileUtils.forceMkdir(dbPath.resolve("test-runs").toFile)
    DB(
      PlayBuildRecord.readAll,
      AppBuildRecord.readAll,
      TestRunRecord.readAll
    )
  }
  case class Join(
    pruneInstanceId: UUID,
    playBuildId: UUID,
    playBuildRecord: PlayBuildRecord,
    appBuildId: UUID,
    appBuildRecord: AppBuildRecord,
    testRunId: UUID,
    testRunRecord: TestRunRecord
  )
  def foldLeft[A](x0: A)(f: (A, Join) => A)(implicit ctx: Context): A = {
    val testRunsDir = Paths.get(ctx.dbHome, "test-runs")
    Records.foldLeftAll[TestRunRecord, A](testRunsDir, x0) {
      case (x, testRunId, testRunRecord) =>
        val appBuildId = testRunRecord.appBuildId
        val optJoin: Option[Join] = for {
          appBuildRecord <- AppBuildRecord.read(appBuildId)
          playBuildId = appBuildRecord.playBuildId
          playBuildRecord <- PlayBuildRecord.read(playBuildId)
          pruneInstanceId = playBuildRecord.pruneInstanceId
        } yield Join(
          pruneInstanceId,
          playBuildId,
          playBuildRecord,
          appBuildId,
          appBuildRecord,
          testRunId,
          testRunRecord
        )
        optJoin.fold(x)(join => f(x, join))
    }
  }

}
