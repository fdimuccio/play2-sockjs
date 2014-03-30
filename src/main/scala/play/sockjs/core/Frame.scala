package play.sockjs.core

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.Comet
import play.api.http.{ContentTypeOf, Writeable}
import play.api.mvc._
import play.api.templates.Html

import play.core.Execution.Implicits.internalContext

/**
 * Frame accepted by SockJS clients
 */
private[sockjs] sealed abstract class Frame {
  def text: String
  lazy val size: Long = text.size
}

private[sockjs] object Frame {

  /**
   * Open frame.  Every time a new session is established, the server must immediately
   * send the open frame. This is required, as some protocols (mostly polling) can't
   * distinguish between a properly established connection and a broken one - we must
   * convince the client that it is indeed a valid url and it can be expecting further
   * messages in the future on that url.
   */
  case object OpenFrame extends Frame {
    def text = "o"
  }

  /**
   * Heartbeat frame. Most loadbalancers have arbitrary timeouts on connections.
   * In order to keep connections from breaking, the server must send a heartbeat frame
   * every now and then. The typical delay is 25 seconds and should be configurable.
   */
  case object HeartbeatFrame extends Frame {
    def text = "h"
  }

  /**
   * Array of json-encoded messages. For example: a["message"].
   */
  final case class MessageFrame private(payload: Seq[String]) extends Frame {
    def ++(frame: MessageFrame) = copy(payload = payload ++ frame.payload)
    lazy val text = {
      /**
       * SockJS requires a special JSON codec - it requires that many other characters,
       * over and above what is required by the JSON spec are escaped.
       * To satisfy this we escape any character that escapable with short escapes and
       * any other non ASCII character we unicode escape it
       *
       * By default, Jackson does not escape unicode characters in JSON strings
       * This should be ok, since a valid JSON string can contain unescaped JSON
       * characters.
       * However, SockJS requires that many unicode chars are escaped. This may
       * be due to browsers barfing over certain unescaped characters
       * So... when encoding strings we make sure all unicode chars are escaped
       *
       * This code adapted from http://wiki.fasterxml.com/JacksonSampleQuoteChars
       *
       * Refs:
       *  - https://github.com/netty/netty/pull/1615/files#L29R71
       *  - https://github.com/eclipse/vert.x/blob/master/vertx-core/src/main/java/org/vertx/java/core/sockjs/impl/JsonCodec.java#L32
       */
      def escape(message: String): String = {
        val buffer = new StringBuilder(message.length)
        message.foreach { ch =>
          if ((ch >= '\u0000' && ch <= '\u001F') ||
              (ch >= '\uD800' && ch <= '\uDFFF') ||
              (ch >= '\u200C' && ch <= '\u200F') ||
              (ch >= '\u2028' && ch <= '\u202F') ||
              (ch >= '\u2060' && ch <= '\u206F') ||
              (ch >= '\uFFF0' && ch <= '\uFFFF')) {
            buffer.append('\\')
                  .append('u')
                  .append(Integer.toHexString(ch).toLowerCase)
          } else
            buffer.append(ch)
        }
        buffer.result()
      }
      s"a${escape(Json.stringify(Json.toJson(payload)))}"
    }
  }

  object MessageFrame {
    def apply(message: String): MessageFrame = MessageFrame(Seq(message))
  }

  /**
   * Close frame. This frame is send to the browser every time the client asks for data
   * on closed connection. This may happen multiple times. Close frame contains a code
   * and a string explaining a reason of closure, like: c[3000,"Go away!"].
   *
   * @param code
   * @param reason
   */
  final case class CloseFrame(code: Int, reason: String) extends Frame {
    lazy val text = s"c${Json.stringify(Json.arr(code, reason))}"
  }

  object CloseFrame {
    val GoAway = CloseFrame(3000, "Go away!")
    val AnotherConnectionStillOpen = CloseFrame(2010, "Another connection still open")
    val ConnectionInterrupted = CloseFrame(1002, "Connection interrupted!")
  }

  /**
   * Transform a stream of Frame to a stream of text
   */
  def toText= Enumeratee.map[Frame](_.text)

  /**
   * Transform a stream of Frame to a stream of text, each element terminated with \n
   */
  def toTextN = Enumeratee.map[Frame](_.text + "\n")

  /**
   * Transform a stream of Frame to a stream of jsonp encoded text
   */
  def toJsonp(callback: String) = Enumeratee.map[Frame](JsonpFrame(callback, _))

  /**
   * Transform a stream of Frame to a stream to be transmitted over HTMLfile transport
   */
  def toHTMLfile = Enumeratee.map[Frame] { frame =>
    Html(s"<script>\np(${JsString(frame.text)});\n</script>\r\n")
  }

  /**
   * EventSource SockJS Frame encoder
   */
  implicit val eventsourceOf_Frame = Comet.CometMessage[Frame](_.text)

}

/**
 * JSONP Helper: used to write frames when using jsonp transport
 */
private[sockjs] case class JsonpFrame(padding: String, frame: Frame)

private[sockjs] object JsonpFrame {

  implicit def contentTypeOf_JsonpFrame: ContentTypeOf[JsonpFrame] = {
    ContentTypeOf[JsonpFrame](Some("application/javascript; charset=UTF-8"))
  }

  implicit def writeableOf_JsonpFrame: Writeable[JsonpFrame] = Writeable { jsonp =>
    Codec.utf_8.encode(s"${jsonp.padding}(${JsString(jsonp.frame.text)});\r\n")
  }

}