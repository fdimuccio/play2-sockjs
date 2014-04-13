package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.mvc._
import play.api.http._

import play.core.Execution.Implicits.internalContext

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
  def send = {
    Transport.Send(
      ok = req => NoContent.enableCORS(req).notcached.as("text/plain; charset=UTF-8"),
      ko = NotFound)
  }

  /**
   * handler for xhr polling transport
   */
  def polling = Transport.Polling { (req, session) =>
    session.bind { enumerator =>
      Transport.Res(enumerator &> Enumeratee.map(_.text + "\n"), true)
    }
  }

  /**
   * handler for xhr_streaming transport
   */
  def streaming = Transport.Streaming { (req, session) =>
    session.bind { enumerator =>
      val preludeE = Enumerator(prelude)
      Transport.Res(preludeE >>> (enumerator &> Enumeratee.map(_.text + "\n")), true)
    }
  }

  implicit def writeableOf_XhrTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("application/javascript; charset=UTF-8"))

}
