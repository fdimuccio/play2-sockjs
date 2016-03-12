package controllers

import play.api.mvc._

import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future

import play.sockjs.api._

import models._

class Application extends SockJSRouter with Controller {
  
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
    * Override this method to specify different settings
    */
  override protected def settings = SockJSSettings(websocket = false)

  /**
    * SockJS handler
    */
  def sockjs: SockJS = SockJS.acceptOrResult[JsValue, JsValue] { request =>
    request.getQueryString("username").map { username =>
      ChatRoom.join(username).map(Right(_))
    }.getOrElse {
      Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }
}
