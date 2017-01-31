package protocol.routers

import akka.actor.{Actor, ActorRef, Props}
import play.sockjs.SockJS.{In, Out}
import play.sockjs._

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

final class JavaCallbackTestRouters extends JavaTestRouters {

  def echo = SockJS.whenReady(new java.util.function.BiConsumer[SockJS.In, SockJS.Out] {
    def accept(in: In, out: Out): Unit = in.onMessage(new java.util.function.Consumer[String] {
      def accept(msg: String): Unit = out.write(msg)
    })
  })

  def closed = SockJS.whenReady(new java.util.function.BiConsumer[SockJS.In, SockJS.Out] {
    def accept(in: In, out: Out): Unit = out.close()
  })
}

final class JavaActorTestRouters extends JavaTestRouters {

  def echo: SockJS = SockJS.withActor(new java.util.function.Function[ActorRef, Props] {
    def apply(out: ActorRef): Props = Props(new Actor {
      override def receive = {
        case msg => out ! msg
      }
    })
  })

  def closed: SockJS = SockJS.withActor(new java.util.function.Function[ActorRef, Props] {
    def apply(out: ActorRef): Props = Props(new Actor {
      context.stop(self)
      override def receive = {
        case msg =>
      }
    })
  })
}