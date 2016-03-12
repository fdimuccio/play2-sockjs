package controllers

import akka.stream.scaladsl._

import play.api.mvc.{Results, Action}

import play.sockjs.api._

object Application {

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = default.websocket(false)
    val withJSessionid = default.cookies(CookieFunctions.jsessionid)
  }

  abstract class TestRouter(prefix: String, cfg: SockJSSettings = Settings.default) extends SockJSRouter {
    withPrefix(prefix)
    override protected def settings = cfg
  }

  /**
    * responds with identical data as received
    */
  class Echo(prefix: String) extends TestRouter(prefix) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * same as echo, but with websockets disabled
    */
  class EchoWithNoWebsocket(prefix: String) extends TestRouter(prefix, Settings.noWebSocket) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  class EchoWithJSessionId(prefix: String) extends TestRouter(prefix, Settings.withJSessionid) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * server immediately closes the session
    */
  class Closed(prefix: String) extends TestRouter(prefix) {
    def sockjs: SockJS = SockJS.accept(_ => Flow.fromSinkAndSource(Sink.ignore, Source.empty[Frame]))
  }
}