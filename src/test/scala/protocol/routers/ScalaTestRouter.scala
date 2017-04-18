package protocol.routers

import akka.actor.ActorSystem
import play.sockjs.api._

class ScalaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings) extends SockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

abstract class ScalaTestRouters(echo: SockJS, closed: SockJS) extends TestRouters {

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
}

final class ScalaFlowTestRouters extends ScalaTestRouters(
  echo = SockJS.accept(_ => Flows.echo[Frame]),
  closed = SockJS.accept(_ => Flows.closed[Frame])
)

final class ScalaActorTestRouters(implicit val as: ActorSystem) extends ScalaTestRouters(
  echo = SockJS.accept(_ => ActorFlows.echo[Frame]),
  closed = SockJS.accept(_ => ActorFlows.closed[Frame])
)