package protocol.utils

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl._
import play.sockjs.api._
import play.sockjs.api.libs.streams.ActorFlow

sealed abstract class TestRouter(prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

sealed trait TestRouters {

  object Settings {
    val base = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = base.websocket(false)
    val withJSessionid = base.cookies(CookieFunctions.jsessionid)
  }

  def echo[T]: Flow[T, T, _]

  def closed[T]: Flow[T, T, _]

  /**
    * responds with identical data as received
    */
  final class Echo(prefix: String) extends TestRouter(prefix, Settings.base) {
    def sockjs: SockJS = SockJS.accept[Frame, Frame](_ => echo[Frame])
  }

  /**
    * same as echo, but with websockets disabled
    */
  final class EchoWithNoWebsocket(prefix: String) extends TestRouter(prefix, Settings.noWebSocket) {
    def sockjs: SockJS = SockJS.accept[Frame, Frame](_ => echo[Frame])
  }

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  final class EchoWithJSessionId(prefix: String) extends TestRouter(prefix, Settings.withJSessionid) {
    def sockjs: SockJS = SockJS.accept[Frame, Frame](_ => echo[Frame])
  }

  /**
    * server immediately closes the session
    */
  final class Closed(prefix: String) extends TestRouter(prefix, Settings.base) {
    def sockjs: SockJS = SockJS.accept[Frame, Frame](_ => closed[Frame])
  }
}

final class PlainFlowTestRouters extends TestRouters {
  def echo[T]: Flow[T, T, _] = Flow[T]
  def closed[T]: Flow[T, T, _] = Flow.fromSinkAndSource(Sink.ignore, Source.empty[T])
}

final class ActorFlowTestRouters extends TestRouters {
  implicit val as = ActorSystem("ActorFlowTestRouters")

  def echo[T] = ActorFlow.actorRef[T, T](out => Props(new Actor {
    override def receive: Receive = {
      case msg => out ! msg
    }
  }))

  def closed[T] = ActorFlow.actorRef[T, T](out => Props(new Actor {
    context.stop(self)
    override def receive: Receive = {
      case msg =>
    }
  }))
}