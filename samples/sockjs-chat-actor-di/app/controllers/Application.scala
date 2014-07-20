package controllers

import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.mvc._

import play.api.libs.json._
import play.api.Play.current

import play.sockjs.api._

import lib._
import models._

class Application(chatRoom: ChatRoom) extends SockJSController {
  
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

  /**
   * Sample settings
   */
  override def settings = SockJSSettings(websocket = false, heartbeat = 55 seconds)

  def sockjs = SockJS.tryAcceptWithActor[JsValue, JsValue] { request =>
    Future.successful(request.cookies.get("username").map { cookie =>
      Right(chatRoom.join(cookie.value))
    }.getOrElse(Left(BadRequest)))
  }

}

object Application extends ManagedSockJSRouter[Application]