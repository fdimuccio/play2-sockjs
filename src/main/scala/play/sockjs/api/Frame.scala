package play.sockjs.api

import akka.util.ByteString

import play.api.http.websocket.CloseCodes

import play.sockjs.core.json.JsonByteStringEncoder._

/**
 * SockJS frames
 */
sealed abstract class Frame {

  /**
    * Encode this frame as specified by SockJS specs.
    */
  private[sockjs] def encode: ByteString
}

object Frame {

  /**
    * Open frame.  Every time a new session is established, the server must immediately
    * send the open frame. This is required, as some protocols (mostly polling) can't
    * distinguish between a properly established connection and a broken one - we must
    * convince the client that it is indeed a valid url and it can be expecting further
    * messages in the future on that url.
    *
    * This frame is used internally by the protocol.
    */
  private[sockjs] case object Open extends Frame {
    private[sockjs] val encode = ByteString("o")
  }

  /**
    * Heartbeat frame. Most loadbalancers have arbitrary timeouts on connections.
    * In order to keep connections from breaking, the server must send a heartbeat frame
    * every now and then. The typical delay is 25 seconds.
    *
    * This frame is used internally by the protocol.
    */
  private[sockjs] case object Heartbeat extends Frame {
    private[sockjs] val encode = ByteString("h")
  }

  /**
    * Encode messages to a SockJS message frame, it is an array of json encoded
    * messages. For example: a["message1", "message2"].
    */
  case class Text(data: Vector[String]) extends Frame {
    private[sockjs] def encode: ByteString = {
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
        *
        * So... when encoding strings we make sure all unicode chars are escaped
        * according to this regexp rule [\x00-\x1f\u200c-\u200f\u2028-\u202f\u2060-\u206f\ufff0-\uffff]
        *
        * This code adapted from http://wiki.fasterxml.com/JacksonSampleQuoteChars
        *
        * Refs:
        *  - https://github.com/netty/netty/pull/1615/files#L29R71
        *  - https://github.com/eclipse/vert.x/blob/master/vertx-core/src/main/java/org/vertx/java/core/sockjs/impl/JsonCodec.java#L32
        *
        * NOTE: this method has been replaced by jackson ASCII_ESCAPE, it is
        *       kept here for reference
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
            buffer.append(f"\\u$ch%04x")
          } else
            buffer.append(ch)
        }
        buffer.result()
      }
      Text.prelude ++ asJsonArray(this)
    }
  }

  object Text {
    private val prelude = ByteString("a")

    def apply(data: String): Text = Text(Vector(data))
  }

  /**
   * Close frame. This frame is send to the browser every time the client asks for data
   * on closed connection. This may happen multiple times. Close frame contains a code
   * and a string explaining a reason of closure, like: c[3000,"Go away!"].
   *
   * @param code
   * @param reason
   */
  final case class Close(code: Int, reason: String) extends Frame {
    private[sockjs] lazy val encode = Close.prelude ++ asJsonArray(this)
  }

  object Close {
    private val prelude = ByteString("c")

    val GoAway = Close(3000, "Go away!")
    val AnotherConnectionStillOpen = Close(2010, "Another connection still open")
    val ConnectionInterrupted = Close(1002, "Connection interrupted")
  }

  /**
    * Special frame to signal the underlying flow to close the connection abruptly.
    * This frame doesn't contain any payload, an exception will be thrown if
    * encode is called.
    */
  case object CloseAbruptly extends Frame {
    private[sockjs] def encode = throw SockJSCloseException(Close(CloseCodes.ConnectionAbort, "Connection aborted due to a fatal error"))
  }
}

/**
  * An exception that, if thrown by a SockJS source, will cause the SockJS to be closed with the given close
  * message. This is a convenience that allows the SockJS to close with a particular close code without having
  * to produce generic Messages.
  */
case class SockJSCloseException(message: Frame.Close) extends RuntimeException(message.reason, null, false, false)