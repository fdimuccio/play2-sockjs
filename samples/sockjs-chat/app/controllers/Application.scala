package controllers

import play.api.mvc._

import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import models._

import scala.concurrent.Future

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
      Ok(views.html.chatRoom(username))
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Please choose a valid username."
      )
    }
  }

  val chat = SockJSRouter(_.websocket(true)).acceptOrResult[JsValue, JsValue] { request =>
    request.getQueryString("username").map { username =>
      ChatRoom.join(username).map(Right(_))
    }.getOrElse {
      Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }
}
