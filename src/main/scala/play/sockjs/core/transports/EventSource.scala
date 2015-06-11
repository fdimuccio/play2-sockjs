package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.http._
import play.api.mvc._

/**
 * EventSource transport
 */
private[sockjs] object EventSource extends HeaderNames with Results {

  def transport = Transport.Streaming { (req, session) =>
    session.bind { enumerator =>
      // Here I should have used play.api.libs.EventSource, but I couldn't since
      // sockjs protocol tests expect "\r\n" as data terminator, and play.api.libs.EventSource
      // uses just "\n" (and that's correct, blame sockjs tests)
      Transport.Res(Enumerator("\r\n") >>> (enumerator &> Enumeratee.map[Frame] { frame =>
        val sb = new StringBuilder
        for (line <- frame.text.split("(\r?\n)|\r")) {
          sb.append("data: ").append(line).append("\r\n")
        }
        sb.append("\r\n")
        sb.toString()
      }(play.api.libs.iteratee.Execution.trampoline)))
    }
  }

  implicit def writeableOf_EventSourceTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("text/event-stream; charset=UTF-8"))(play.api.libs.iteratee.Execution.trampoline)

}
