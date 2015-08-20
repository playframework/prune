package controllers

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import play.api.libs.json.Json
import akka.stream.scaladsl.Source
import akka.util.ByteString

@Singleton
class Application extends Controller {

  def simple = Action { request =>
    Ok("Hello world.")
  }

  def download(length: Int) = Action { request =>
    Ok(ByteString(new Array[Byte](length)))
  }

  def downloadChunked(length: Int) = Action { request =>

    val maxArraySize = 4 * 1024
    val numFullChunks = length / maxArraySize
    val lastChunkSize = length % maxArraySize

    val fullChunks = Source(1 to numFullChunks).map { _ =>
      ByteString(new Array[Byte](maxArraySize))
    }

    val allChunks = if (lastChunkSize > 0) {
      fullChunks ++ Source.single(ByteString(new Array[Byte](lastChunkSize)))
    } else {
      fullChunks
    }

    Ok.chunked(allChunks)
  }

  def upload = Action(parse.raw) { request =>
    Ok("upload")
  }

  def templateSimple = Action { request =>
    Ok(views.html.simple("simple"))
  }

  def templateLang = Action { request =>
    Ok(views.html.lang())
  }

  def jsonEncode = Action { request =>
    Ok(Json.obj("message" -> "Hello, World!"))
  }

}
