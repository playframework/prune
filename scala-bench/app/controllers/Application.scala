package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.mvc._
import play.api.libs.json.Json

object Application extends Controller {

  def simple = Action(parse.empty) { request =>
    Ok("Hello world.")
  }

  def download(length: Int) = Action(parse.empty) { request =>
    Ok(ByteString(new Array[Byte](length)))
  }

  def downloadChunked(length: Int) = Action(parse.empty) { request =>

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

  def templateSimple = Action(parse.empty) { request =>
    Ok(views.html.simple("simple"))
  }

  def templateLang = Action(parse.empty) { request =>
    Ok(views.html.lang())
  }

  def jsonEncode = Action(parse.empty) { request =>
    Ok(Json.obj("message" -> "Hello, World!"))
  }

}