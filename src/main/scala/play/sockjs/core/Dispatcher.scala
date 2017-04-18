package play.sockjs.core

import play.api.http.HttpVerbs
import play.api.mvc.Handler

import play.sockjs.core.transports._

/**
 * Dispatch SockJS request to the right protocol handler.
 */
private[sockjs] final class Dispatcher(server: Server) extends HttpVerbs {

  import Dispatcher._

  private[this] val websocket = new WebSocket(server)
  private[this] val xhr = new Xhr(server)
  private[this] val eventsource = new EventSource(server)
  private[this] val htmlfile = new HtmlFile(server)
  private[this] val jsonp = new Jsonp(server)
  private[this] val utils = new Utils(server)

  def unapply(mp: (String, String)): Option[Handler] = PartialFunction.condOpt(mp) {
    case (GET | OPTIONS, "/info") => utils.info
    case (_, Session(_, "websocket")) => websocket.framed
    case (POST, Session(sessionID, "xhr_send")) => xhr.send(sessionID)
    case (POST, Session(sessionID, "xhr_streaming")) => xhr.streaming(sessionID)
    case (POST, Session(sessionID, "xhr")) => xhr.polling(sessionID)
    case (OPTIONS, Session(_, "xhr_send" | "xhr" | "xhr_streaming")) => xhr.options
    case (GET, Session(sessionID, "eventsource")) => eventsource.streaming(sessionID)
    case (GET, Session(sessionID, "htmlfile")) => htmlfile.streaming(sessionID)
    case (GET, Session(sessionID, "jsonp")) => jsonp.polling(sessionID)
    case (POST, Session(sessionID, "jsonp_send")) => jsonp.send(sessionID)
    case (_, "/websocket") => websocket.unframed
    case (GET, IframePage()) => utils.iframe
    case (GET, MaybeSlash()) => utils.greet
  }
}

private[sockjs] object Dispatcher {

  // -- Path extractors

  val MaybeSlash = """/?""".r
  val IframePage = """/(?:iframe)[^/]*(?:\.html)""".r
  val Session = """/(?:[^/.]+)/([^/.]+)/([^/.]+)""".r
}