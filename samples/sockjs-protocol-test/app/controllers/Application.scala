package controllers

import play.api.mvc._

import play.api.libs.json._
import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext.Implicits.global

import play.sockjs.api._
import play.sockjs.core.IterateeX

abstract class SockJSTestRouter(websocket: Boolean = true, cookies: Boolean = false) extends SockJSRouter with Controller {
  override def server = {
    val settings = SockJSSettings(
      websocket = websocket,
      cookies = if (cookies) Some(SockJSSettings.CookieCalculator.jsessionid) else None,
      streamingQuota = 4096)
    SockJSServer(settings)
  }
}

/**
 * responds with identical data as received
 */
object Echo extends SockJSTestRouter {

  def sockjs = SockJS.using { req =>
    IterateeX.joined[String]
  }

}

/**
 * identical to echo, but with websockets disabled
 */
object DisabledWebSocketEcho extends SockJSTestRouter(false) {

  def sockjs = SockJS.using { req =>
    IterateeX.joined[String]
  }

}

/**
 * identical to echo, but with JSESSIONID cookies sent
 */
object CookieNeededEchoController extends SockJSTestRouter(cookies = true) {

  def sockjs = SockJS.using { req =>
    IterateeX.joined[String]
  }

}

/**
 * server immediately closes the session
 */
object Closed extends SockJSTestRouter {

  def sockjs = SockJS.using { req =>
    (Iteratee.ignore[String], Enumerator.eof[String])
  }

}