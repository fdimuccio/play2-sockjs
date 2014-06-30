package controllers

import play.api.libs.iteratee._

import akka.actor._

import play.sockjs.api._
import play.sockjs.core.iteratee.IterateeX

object ApplicationActor {

  import scala.concurrent.ExecutionContext.Implicits.global
  import play.api.Play.current

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val nowebsocket = default.websocket(false)
    val withjsessionid = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  class Echo(out: ActorRef) extends Actor {
    def receive = {
      case message => out ! message
    }
  }

  class Closed extends Actor {
    context.stop(self)
    def receive = {
      case _ =>
    }
  }

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter(Settings.default).acceptWithActor[String, String](req => out => Props(classOf[Echo], out))

  /**
   * identical to echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter(Settings.nowebsocket).acceptWithActor[String, String](req => out => Props(classOf[Echo], out))

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter(Settings.withjsessionid).acceptWithActor[String, String](req => out => Props(classOf[Echo], out))

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter(Settings.default).acceptWithActor[String, String](req => out => Props(classOf[Closed]))
}