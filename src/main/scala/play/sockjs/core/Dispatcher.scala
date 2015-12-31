package play.sockjs.core

import play.api.mvc.Handler

import play.sockjs.core.transports._

/**
 * The dispatcher that will handle SockJS request.
 */
class Dispatcher(transport: Transport) {

  import Dispatcher._

  private[this] val utils = new Utils(transport)
  private[this] val websocket = new WebSocket(transport)
  private[this] val xhr = new Xhr(transport)
  private[this] val eventsource = new EventSource(transport)
  private[this] val htmlfile = new HtmlFile(transport)
  private[this] val jsonp = new Jsonp(transport)

  def unapply(mp: (String, String)): Option[Handler] = PartialFunction.condOpt(mp) {
    case ("GET", MaybeSlash()) => utils.greet
    case ("GET", IframePage()) => utils.iframe
    case ("GET" | "OPTIONS", "/info") => utils.info
    case (_, Transport(_, "websocket")) => websocket.sockjs
    case ("POST", Transport(sessionID, "xhr_send")) => xhr.send(sessionID)
    case ("POST", Transport(sessionID, "xhr")) => xhr.polling(sessionID)
    case ("POST", Transport(sessionID, "xhr_streaming")) => xhr.streaming(sessionID)
    case ("OPTIONS", Transport(_, "xhr_send" | "xhr" | "xhr_streaming")) => xhr.options
    case ("GET", Transport(sessionID, "eventsource")) => eventsource.streaming(sessionID)
    case ("GET", Transport(sessionID, "htmlfile")) => htmlfile.streaming(sessionID)
    case ("GET", Transport(sessionID, "jsonp")) => jsonp.polling(sessionID)
    case ("POST", Transport(sessionID, "jsonp_send")) => jsonp.send(sessionID)
    case (_, "/websocket") => websocket.raw
  }
}

object Dispatcher {

  // -- Path extractors

  val MaybeSlash = """/?""".r
  val IframePage = """/(?:iframe)[^/]*(?:\.html)""".r

}