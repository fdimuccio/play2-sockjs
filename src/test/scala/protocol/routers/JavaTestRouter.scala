package protocol.routers

import scala.compat.java8.FunctionConverters._

import akka.actor.ActorSystem

import play.api.mvc.{Action, BodyParsers}

import play.mvc.Http.RequestHeader

import play.sockjs._
import play.sockjs.api.DefaultSockJSRouterComponents

class JavaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def components = DefaultSockJSRouterComponents(materializer, Action, BodyParsers.parse)
  override protected def settings = cfg
}

abstract class JavaTestRouters(echo: SockJS, closed: SockJS) extends TestRouters {

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
}

final class JavaFlowTestRouters extends JavaTestRouters(
  echo = SockJS.Text.accept(asJavaFunction((_: RequestHeader) => Flows.echo[String].asJava)),
  closed = SockJS.Text.accept(asJavaFunction((_: RequestHeader) => Flows.closed[String].asJava))
)

final class JavaActorTestRouters(implicit as: ActorSystem) extends JavaTestRouters(
  echo = SockJS.Text.accept(asJavaFunction((_: RequestHeader) => ActorFlows.echo[String].asJava)),
  closed = SockJS.Text.accept(asJavaFunction((_: RequestHeader) => ActorFlows.closed[String].asJava))
)