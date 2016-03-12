package play.sockjs.core
package transports

import akka.stream.scaladsl._
import akka.util.ByteString

import play.api.http._
import play.api.mvc._

/**
 * EventSource transport
 */
private[sockjs] class EventSource(server: Server) extends HeaderNames with Results {
  import EventSource._

  def streaming = server.streaming { req =>
    req.bind("text/event-stream") { source =>
      Source.single(crlf).concat(source.map { frame =>
        data ++ frame ++ crlf ++ crlf
      })
    }
  }
}

private[sockjs] object EventSource {
  private val data = ByteString("data: ")
  private val crlf = ByteString("\r\n")
}