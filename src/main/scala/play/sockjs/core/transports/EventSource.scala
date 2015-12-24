package play.sockjs.core
package transports

import akka.stream.scaladsl._

import play.api.http._
import play.api.mvc._

/**
 * EventSource transport
 */
private[sockjs] object EventSource extends HeaderNames with Results {

  def transport = Transport.Streaming { (req, session) =>
    session.bind { source =>
      // Here I should have used play.api.libs.EventSource, but I couldn't since
      // sockjs protocol tests expect "\r\n" as data terminator, and play.api.libs.EventSource
      // uses just "\n" (and that's correct, blame sockjs tests)
      Source.single("\r\n").concat(source.map { frame =>
        val sb = new StringBuilder
        for (line <- frame.encode.split("(\r?\n)|\r")) {
          sb.append("data: ").append(line).append("\r\n")
        }
        sb.append("\r\n")
        sb.toString()
      })
    }
  }

  implicit val writeableOf_EventSourceTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("text/event-stream"))

}
