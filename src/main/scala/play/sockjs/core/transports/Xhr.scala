package play.sockjs.core
package transports

import akka.stream.scaladsl._

import play.api.mvc._
import play.api.http._

private[sockjs] object Xhr extends HeaderNames with Results {

  private[this] val prelude = Array.fill(2048)('h').mkString + '\n'

  /**
   * handler for xhr options req
   */
  def options = SockJSAction(Action { implicit req =>
    OptionsResult("OPTIONS", "POST")
  })

  /**
   * handler for xhr_send
   */
  def send = Transport.Send(req => NoContent.enableCORS(req).notcached.as("text/plain; charset=UTF-8"))

  /**
   * handler for xhr polling transport
   */
  def polling = Transport.Polling { (req, session) =>
    session.bind { source =>
      source.map(_.encode + "\n")
    }.map(_.enableCORS(req))(play.api.libs.iteratee.Execution.trampoline)
  }

  /**
   * handler for xhr_streaming transport
   */
  def streaming = Transport.Streaming { (req, session) =>
    session.bind { source =>
      Source.single(prelude).concat(source.map(_.encode + "\n"))
    }.map(_.enableCORS(req))(play.api.libs.iteratee.Execution.trampoline)
  }

  implicit def writeableOf_XhrTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("application/javascript; charset=UTF-8"))

}
