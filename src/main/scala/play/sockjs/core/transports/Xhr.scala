package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.mvc._
import play.api.http._

object Xhr extends HeaderNames with Results {

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
    session.bind { (enumerator, _) =>
      Ok.chunked(enumerator &> Frame.toTextN)
        .enableCORS(req)
        .notcached
        .as("application/javascript; charset=UTF-8")
    }
  }

  /**
   * handler for xhr_streaming transport
   */
  def streaming = Transport.Streaming { (req, session) =>
    session.bind { (enumerator, error) =>
      val preludeE = if (error) Enumerator.enumInput[String](Input.Empty) else Enumerator(prelude)
      Ok.chunked(preludeE >>> (enumerator &> Frame.toTextN))
        .enableCORS(req)
        .notcached
        .as("application/javascript; charset=UTF-8")
    }
  }

}
