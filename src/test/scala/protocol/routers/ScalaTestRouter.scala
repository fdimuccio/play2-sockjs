package protocol.routers

import org.apache.pekko.actor.ActorSystem
import play.sockjs.api._

class ScalaTestRouter(val sockjs: SockJS, prefix: String, cfg: SockJSSettings, val components: SockJSRouterComponents) extends BaseSockJSRouter {
  withPrefix(prefix)
  override protected def settings = cfg
}

abstract class ScalaTestRouters(echo: SockJS, closed: SockJS, components: SockJSRouterComponents) extends TestRouters {

  object Settings {
    val base = SockJSSettings(streamingQuota = 4096)
    val noWebSocket = base.websocket(false)
    val withJSessionid = base.cookies(CookieFunctions.jsessionid)
  }

  /**
    * responds with identical data as received
    */
  def Echo(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.base, components)

  /**
    * same as echo, but with websockets disabled
    */
  def EchoWithNoWebsocket(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.noWebSocket, components)

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def EchoWithJSessionId(prefix: String) = new ScalaTestRouter(echo, prefix, Settings.withJSessionid, components)

  /**
    * server immediately closes the session
    */
  def Closed(prefix: String) = new ScalaTestRouter(closed, prefix, Settings.base, components)
}

final class ScalaFlowTestRouters(components: SockJSRouterComponents) extends ScalaTestRouters(
  echo = SockJS.accept(_ => Flows.echo[Frame]),
  closed = SockJS.accept(_ => Flows.closed[Frame]),
  components
)

final class ScalaActorTestRouters(components: SockJSRouterComponents)(implicit val as: ActorSystem) extends ScalaTestRouters(
  echo = SockJS.accept(_ => ActorFlows.echo[Frame]),
  closed = SockJS.accept(_ => ActorFlows.closed[Frame]),
  components
)