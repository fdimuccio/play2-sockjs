package protocol.routers

import org.apache.pekko.actor.ActorSystem

import play.api.mvc.{Action, BodyParsers}

import play.mvc.Http.RequestHeader

import play.sockjs._
import play.sockjs.api.{ DefaultSockJSRouterComponents, SockJSRouterComponents }

class JavaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings, override val components: SockJSRouterComponents) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

abstract class JavaTestRouters(echo: SockJS, closed: SockJS, components: SockJSRouterComponents) extends TestRouters {

  object Settings {
    val base = new SockJSSettings().withStreamingQuota(4096)
    val noWebSocket = base.withWebsocket(false)
    val withJSessionid = base.withCookies(CookieFunctions.jessionid)
  }

  /**
    * responds with identical data as received
    */
  def Echo(prefix: String) = new JavaTestRouter(echo, prefix, Settings.base, components)

  /**
    * same as echo, but with websockets disabled
    */
  def EchoWithNoWebsocket(prefix: String) = new JavaTestRouter(echo, prefix, Settings.noWebSocket, components)

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def EchoWithJSessionId(prefix: String) = new JavaTestRouter(echo, prefix, Settings.withJSessionid, components)

  /**
    * server immediately closes the session
    */
  def Closed(prefix: String) = new JavaTestRouter(closed, prefix, Settings.base, components)
}

final class JavaFlowTestRouters(components: SockJSRouterComponents) extends JavaTestRouters(
  echo = SockJS.Text.accept((_: RequestHeader) => Flows.echo[String].asJava),
  closed = SockJS.Text.accept((_: RequestHeader) => Flows.closed[String].asJava),
  components
)

final class JavaActorTestRouters(components: SockJSRouterComponents)(implicit as: ActorSystem) extends JavaTestRouters(
  echo = SockJS.Text.accept((_: RequestHeader) => ActorFlows.echo[String].asJava),
  closed = SockJS.Text.accept((_: RequestHeader) => ActorFlows.closed[String].asJava),
  components
)