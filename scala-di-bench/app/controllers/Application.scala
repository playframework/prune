package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import scala.concurrent.Future

import play.api.libs.concurrent.Execution.Implicits.defaultContext

class Application extends Controller {

  def simple = Action(parse.empty) { request =>
    Ok("Hello world.")
  }

  def download(length: Int) = Action(parse.empty) { request =>
    Ok(new Array[Byte](length))
  }

  def downloadChunked(length: Int) = Action(parse.empty) { request =>

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