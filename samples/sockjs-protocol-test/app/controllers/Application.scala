package controllers

import akka.stream.scaladsl._

import play.api.libs.iteratee._

import play.sockjs.api._
import streams._

object Application {

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = default.websocket(false)
    val withJSessionId = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter(Settings.default).accept(req => FlowX.echo)

  /**
   * same as echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter(Settings.noWebSocket).accept(req => FlowX.echo)

  /**
   * same as echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter(Settings.withJSessionId).accept(req => FlowX.echo)

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter(Settings.default).accept(req => FlowX.closed)
}