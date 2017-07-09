package controllers

import javax.inject.Inject

import scala.concurrent.Future

import play.api.mvc._
import play.api.libs.json._

import play.sockjs.api._

import models._

class Application @Inject() (chatRoom: ChatRoom) extends InjectedController with InjectedSockJSRouter {

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
  override protected def settings = SockJSSettings(websocket = false)

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