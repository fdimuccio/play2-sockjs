package protocol.utils

import akka.stream.scaladsl._
import play.sockjs.api._

sealed abstract class TestRouter(prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

object TestRouter {

  object Settings {
    val base = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = base.websocket(false)
    val withJSessionid = base.cookies(CookieFunctions.jsessionid)
  }

  /**
    * responds with identical data as received
    */
  final class Echo(prefix: String) extends TestRouter(prefix, Settings.base) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * same as echo, but with websockets disabled
    */
  final class EchoWithNoWebsocket(prefix: String) extends TestRouter(prefix, Settings.noWebSocket) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  final class EchoWithJSessionId(prefix: String) extends TestRouter(prefix, Settings.withJSessionid) {
    def sockjs: SockJS = SockJS.accept(_ => Flow[Frame])
  }

  /**
    * server immediately closes the session
    */
  final class Closed(prefix: String) extends TestRouter(prefix, Settings.base) {
    def sockjs: SockJS = SockJS.accept(_ => Flow.fromSinkAndSource(Sink.ignore, Source.empty[Frame]))
  }
}