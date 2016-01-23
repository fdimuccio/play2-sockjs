package controllers

import akka.stream.scaladsl._

import play.sockjs.api._

object Application {

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = default.websocket(false)
    val withjsessionid = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  /**
    * responds with identical data as received
    */
  def echo = SockJSRouter(Settings.default).accept { req =>
    Flow[String]
  }

  /**
    * same as echo, but with websockets disabled
    */
  def disabledWebSocketEcho = SockJSRouter(Settings.noWebSocket).accept { req =>
    Flow[String]
  }

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def jsessionEcho = SockJSRouter(Settings.withjsessionid).accept { req =>
    Flow[String]
  }

  /**
    * server immediately closes the session
    */
  def closed = SockJSRouter(Settings.default).accept { req =>
    Flow.fromSinkAndSource(Sink.ignore, Source.empty[String])
  }
}