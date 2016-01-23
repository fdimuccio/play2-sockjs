package play.sockjs.core

import play.api.mvc.Handler

import play.sockjs.core.transports._

/**
 * The dispatcher that will handle SockJS request.
 */
private[sockjs] final class Dispatcher(transport: Transport) {

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
    case (_, Transport("websocket", _)) => websocket.sockjs
    case ("POST", Transport("xhr_send", sessionID)) => xhr.send(sessionID)
    case ("POST", Transport("xhr", sessionID)) => xhr.polling(sessionID)
    case ("POST", Transport("xhr_streaming", sessionID)) => xhr.streaming(sessionID)
    case ("OPTIONS", Transport("xhr_send" | "xhr" | "xhr_streaming", _)) => xhr.options
    case ("GET", Transport("eventsource", sessionID)) => eventsource.streaming(sessionID)
    case ("GET", Transport("htmlfile", sessionID)) => htmlfile.streaming(sessionID)
    case ("GET", Transport("jsonp", sessionID)) => jsonp.polling(sessionID)
    case ("POST", Transport("jsonp_send", sessionID)) => jsonp.send(sessionID)
    case (_, "/websocket") => websocket.raw
  }
}

private[sockjs] object Dispatcher {

  // -- Path extractors

  val MaybeSlash = """/?""".r
  val IframePage = """/(?:iframe)[^/]*(?:\.html)""".r

}