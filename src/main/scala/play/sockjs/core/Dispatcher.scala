package play.sockjs.core

import akka.actor.{ActorRef, ActorSystem}

import play.sockjs.core.transports._
import play.sockjs.api._
import play.sockjs.core.actors.SockJSActor._

/**
 * The dispatcher that will handle SockJS request.
 */
class Dispatcher(sessionMaster: ActorRef, settings: SockJSSettings) {

  import Dispatcher._

  private[this] implicit val (sm, cfg) = (sessionMaster, settings)

  def unapply(mp: (String, String)): Option[SockJSHandler] = PartialFunction.condOpt(mp) {
    case ("GET", MaybeSlash()) => Utils.greet
    case ("GET", IframePage()) => Utils.iframe(settings.scriptSRC)
    case ("GET" | "OPTIONS", "/info") => Utils.info(settings.websocket, settings.cookies.isDefined)
    case (_, Transport(_, "websocket")) if settings.websocket => WebSocket.sockjs
    case ("POST", Transport(sessionID, "xhr_send")) => Xhr.send(sessionID)
    case ("POST", Transport(sessionID, "xhr")) => Xhr.polling(sessionID)
    case ("POST", Transport(sessionID, "xhr_streaming")) => Xhr.streaming(sessionID)
    case ("OPTIONS", Transport(_, "xhr_send" | "xhr" | "xhr_streaming")) => Xhr.options
    case ("GET", Transport(sessionID, "eventsource")) => EventSource.transport(sessionID)
    case ("GET", Transport(sessionID, "htmlfile")) => HtmlFile.transport(sessionID)
    case ("GET", Transport(sessionID, "jsonp")) => Jsonp.polling(sessionID)
    case ("POST", Transport(sessionID, "jsonp_send")) => Jsonp.send(sessionID)
    case (_, "/websocket") if settings.websocket => WebSocket.raw
  }

}

object Dispatcher {

  // -- Path extractors

  val MaybeSlash = """/?""".r
  val IframePage = """/(?:iframe)[^/]*(?:\.html)""".r

}