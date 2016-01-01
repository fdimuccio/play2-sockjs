package controllers

import akka.actor._

import play.api.libs.streams.ActorFlow

import play.sockjs.api._

object ApplicationActor {

  implicit val as = play.api.Play.current.actorSystem
  implicit val mat = play.api.Play.current.materializer

  object Settings {
    val default = SockJSSettings(streamingQuota = 4096)
    val nowebsocket = default.websocket(false)
    val withjsessionid = default.cookies(SockJSSettings.CookieCalculator.jsessionid)
  }

  object Echo {
    def apply() = ActorFlow.actorRef[String, String](out => Props(new Actor {
      def receive = {
        case message => out ! message
      }
    }))
  }

  object Closed {
    def apply() = ActorFlow.actorRef[String, Frame](out => Props(new Actor {
      out ! PoisonPill
      def receive = {
        case _ =>
      }
    }))
  }

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter(Settings.default).accept(req => Echo())

  /**
   * identical to echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter(Settings.nowebsocket).accept(req => Echo())

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter(Settings.withjsessionid).accept(req => Echo())

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter(Settings.default).accept(req => Closed())
}