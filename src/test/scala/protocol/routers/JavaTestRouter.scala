package protocol.routers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.javadsl._
import play.mvc.Http.RequestHeader
import play.sockjs._
import play.sockjs.api.libs.streams.ActorFlow

class JavaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings: SockJSSettings = cfg
}

sealed trait JavaTestRouters extends TestRouters {

  object Settings {
    val base = new SockJSSettings().withStreamingQuota(4096)
    val noWebSocket = base.withWebsocket(false)
    val withJSessionid = base.withCookies(CookieFunctions.jessionid)
  }

  /**
    * responds with identical data as received
    */
  def Echo(prefix: String) = new JavaTestRouter(echo, prefix, Settings.base)

  /**
    * same as echo, but with websockets disabled
    */
  def EchoWithNoWebsocket(prefix: String) = new JavaTestRouter(echo, prefix, Settings.noWebSocket)

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def EchoWithJSessionId(prefix: String) = new JavaTestRouter(echo, prefix, Settings.withJSessionid)

  /**
    * server immediately closes the session
    */
  def Closed(prefix: String) = new JavaTestRouter(closed, prefix, Settings.base)

  def echo: SockJS

  def closed: SockJS
}

final class JavaFlowTestRouters extends JavaTestRouters {

  def echo = SockJS.Text.accept((request: RequestHeader) => Flow.create[String])

  def closed = SockJS.Text.accept((request: RequestHeader) => Flow.fromSinkAndSource(Sink.ignore, Source.empty[String]))
}

final class JavaActorTestRouters extends JavaTestRouters {
  implicit val as = ActorSystem("JavaActorFlowTestRouters")

  def echo = SockJS.Text.accept((request: RequestHeader) => ActorFlow.actorRef[String, String]((out: ActorRef) => Props(new Actor {
    override def receive = {
      case msg => out ! msg
    }
  })).asInstanceOf[Flow[String, String, Any]])

  def closed = SockJS.Text.accept((request: RequestHeader) => ActorFlow.actorRef[String, String]((out: ActorRef) => Props(new Actor {
    context.stop(self)

    override def receive = {
      case msg =>
    }
  })).asInstanceOf[Flow[String, String, Any]])
}