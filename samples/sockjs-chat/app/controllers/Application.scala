package controllers

import play.api._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import models._

import akka.actor._
import scala.concurrent.Future
import scala.concurrent.duration._

import play.sockjs.api._

object Application extends Controller {
  
  /**
   * Just display the home page.
   */
  def index = Action { implicit request =>
    Ok(views.html.index())
  }
  
  /**
   * Display the chat room page.
   */
  def chatRoom(username: Option[String]) = Action { implicit request =>
    username.filterNot(_.isEmpty).map { username =>
      Ok(views.html.chatRoom(username)).withCookies(Cookie("username", username, httpOnly = false))
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Please choose a valid username."
      )
    }
  }

  val chat = SockJSRouter(_.websocket(true)).tryAccept[JsValue] { request =>
    request.cookies.get("username").map { cookie =>
      ChatRoom.join(cookie.value).map(Right(_))
    }.getOrElse {
      Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }
}
