package controllers

import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.mvc._

import play.api.libs.json._

import play.sockjs.api._

import models._

class Application(chatRoom: ChatRoom) extends Controller with SockJSRouter {
  
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
      Redirect(controllers.routes.Application.index).flashing(
        "error" -> "Please choose a valid username."
      )
    }
  }

  /**
    * SockJS server with sample settings
    */
  override def server = SockJSServer(SockJSSettings(websocket = false, heartbeat = 55 seconds))

  def sockjs = SockJS.acceptOrResult[JsValue, JsValue] { request =>
    request.getQueryString("username").map { username =>
      Future.successful(Right(chatRoom.join(username)))
    }.getOrElse {
      Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }

}