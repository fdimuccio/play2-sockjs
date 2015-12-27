package controllers

import akka.stream.scaladsl._

import play.api.libs.iteratee._

import play.sockjs.api._
import streams._

object Application {

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val nowebsocket = default.websocket(false)
    val withjsessionid = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter(Settings.default).accept(req => FlowX.echo)

  /**
   * identical to echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter(Settings.nowebsocket).accept(req => FlowX.echo)

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter(Settings.withjsessionid).accept(req => FlowX.echo)

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter(Settings.default).accept(req => FlowX.closed)
}