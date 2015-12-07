package controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api._
import play.api.http.{HttpChunk, HttpEntity}
import play.api.mvc._
import play.api.libs.json.Json
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Application @Inject() (implicit
  ec: ExecutionContext
) extends Controller {

  def simple = Action { request =>
    Ok("Hello world.")
  }

  def download(length: Int) = Action { request =>
    Ok(new Array[Byte](length))
  }

  def downloadChunked(length: Int) = Action { request =>

    val maxArraySize = 4 * 1024

    val itr = new Iterator[HttpChunk.Chunk] {
      private var remaining = length
      private def nextArraySize = Math.min(remaining, maxArraySize)
      override def hasNext = nextArraySize > 0
      override def next = {
        val size = nextArraySize
        assert(size > 0)
        HttpChunk.Chunk(ByteString(new Array[Byte](size)))
      }
    }

    Result(
      header = ResponseHeader(200),
      body = HttpEntity.Chunked(
        chunks = Source(() => itr),
        contentType = None
      )
    )
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