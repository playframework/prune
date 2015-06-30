package controllers

import javax.inject.{Inject, Singleton}
import play.api._
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
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

    @volatile
    var remaining = length
    val maxArraySize = 4 * 1024

    val arrayEnum = Enumerator.generateM {
      val arraySize = Math.min(remaining, maxArraySize)
      val optArray = if (arraySize == 0) None else {
        remaining -= arraySize
        Some(new Array[Byte](arraySize))
      }
      Future.successful(optArray)
    }

    Result(
      header = ResponseHeader(200),
      body = arrayEnum
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