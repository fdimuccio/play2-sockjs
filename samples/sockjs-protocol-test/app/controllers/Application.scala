package controllers

import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext.Implicits.global

import play.sockjs.api._

object Application {

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val nowebsocket = default.websocket(false)
    val withjsessionid = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter(Settings.default).using(req => Concurrent.joined[String])

  /**
   * identical to echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter(Settings.nowebsocket).using(req => Concurrent.joined[String])

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter(Settings.withjsessionid).using(req => Concurrent.joined[String])

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter(Settings.default).using(req => (Iteratee.ignore[String], Enumerator.eof[String]))
}