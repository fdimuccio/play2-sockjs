package controllers

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.stream.Materializer

import play.api.mvc._

import play.api.libs.json._

import play.sockjs.api._

import models._

class Application(chatRoom: ChatRoom, mat: Materializer) extends Controller with SockJSRouter {
  import Application._
  
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

  def materializer = mat

  /**
    * SockJS handler
    */
  val sockjs = SockJS(settings).acceptOrResult[JsValue, JsValue] { request =>
    request.getQueryString("username").map { username =>
      Future.successful(Right(chatRoom.join(username)))
    }.getOrElse {
      Future.successful(Left(BadRequest(Json.obj("error" -> "unknown username"))))
    }
  }
}

object Application {

  val settings = SockJSSettings(websocket = false, heartbeat = 55.seconds)
}