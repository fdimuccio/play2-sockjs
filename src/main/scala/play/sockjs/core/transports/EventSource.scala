package play.sockjs.core
package transports

import akka.stream.scaladsl._
import akka.util.ByteString

import play.api.http._
import play.api.mvc._

/**
 * EventSource transport
 */
private[sockjs] class EventSource(transport: Transport) extends HeaderNames with Results {
  import EventSource._

  def streaming = transport.streaming { req =>
    req.bind("text/event-stream") { source =>
      Source.single(crlf).concat(source.map { frame =>
        data ++ frame.encode ++ crlf ++ crlf
      })
    }
  }
}

private[sockjs] object EventSource {
  private val data = ByteString("data: ")
  private val crlf = ByteString("\r\n")
}