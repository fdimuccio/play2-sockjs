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
    username match {
      case Some(value) if !value.isEmpty =>
        Ok(views.html.chatRoom(value))
      case _ =>
        Redirect(controllers.routes.Application.index)
          .flashing("error" -> "Please choose a valid username.")
    }
  }

  /**
    * Override this method to specify different settings
    */
  override protected def settings = SockJSSettings(websocket = false, heartbeat = 55.seconds)

  /**
    * SockJS handler
    */
  def sockjs = SockJS.acceptOrResult[JsValue, JsValue] { request =>
    request.getQueryString("username") match {
      case Some(username) => Future.successful(Right(chatRoom.join(username)))
      case _ => Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }
}