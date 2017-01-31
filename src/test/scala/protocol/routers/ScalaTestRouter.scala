package protocol.routers

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl._
import play.sockjs.api._
import play.sockjs.api.libs.streams.ActorFlow

class ScalaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

sealed trait ScalaTestRouters extends TestRouters {

  object Settings {
    val base = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = base.websocket(false)
    val withJSessionid = base.cookies(CookieFunctions.jsessionid)
  }

  /**
    * responds with identical data as received
    */
  def Echo(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.base)

  /**
    * same as echo, but with websockets disabled
    */
  def EchoWithNoWebsocket(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.noWebSocket)

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def EchoWithJSessionId(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.withJSessionid)

  /**
    * server immediately closes the session
    */
  def Closed(prefix: String) = new ScalaTestRouter(closed, prefix, Settings.base)

  def echo: SockJS

  def closed: SockJS
}

final class ScalaFlowTestRouters extends ScalaTestRouters {

  def echo = SockJS.accept[Frame, Frame](_ => Flow[Frame])

  def closed = SockJS.accept[Frame, Frame] { _ =>
    Flow.fromSinkAndSource(Sink.ignore, Source.empty[Frame])
  }
}

final class ScalaActorTestRouters extends ScalaTestRouters {
  implicit val as = ActorSystem("ActorFlowTestRouters")

  def echo = SockJS.accept { _ =>
    ActorFlow.actorRef[Frame, Frame](out => Props(new Actor {
      override def receive = {
        case msg => out ! msg
      }
    }))
  }

  def closed = SockJS.accept { _ =>
    ActorFlow.actorRef[Frame, Frame](_ => Props(new Actor {
      context.stop(self)
      override def receive = {
        case msg =>
      }
    }))
  }
}