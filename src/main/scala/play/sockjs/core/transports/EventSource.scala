package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.libs.{EventSource => PlayEventSource, Comet}
import play.api.http._
import play.api.mvc._

/**
 * EventSource transport
 */
private[sockjs] object EventSource extends HeaderNames with Results {

  implicit val eventsourceOf_Frame = Comet.CometMessage[Frame](_.text)

  def transport = Transport.Streaming { (req, session) =>
    session.bind { enumerator =>
      Transport.Res(Enumerator("\r\n") >>> (enumerator &> PlayEventSource[Frame]()))
    }
  }

  implicit def writeableOf_EventSourceTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("text/event-stream; charset=UTF-8"))

}
