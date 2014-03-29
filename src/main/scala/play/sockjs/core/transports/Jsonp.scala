package play.sockjs.core
package transports

import play.api.mvc._
import play.api.http._

object Jsonp extends HeaderNames with Results {

  /**
   * handler for jsonp_send
   */
  def send = {
    Transport.Send(
      ok = req => Ok("ok").withHeaders(CONTENT_TYPE -> "text/plain; charset=UTF-8").notcached,
      ko = NotFound)
  }

  /**
   * Handler for jsonp polling transport
   */
  def polling = Transport.Polling { (req, session) =>
    req.getQueryString("c").orElse(req.getQueryString("callback")).map { callback =>
      if (callback.matches("[^a-zA-Z0-9-_.]")) InternalServerError("invalid \"callback\" parameter")
      else session.bind((en, _) =>
        Ok.stream(en &> Frame.toJsonp(callback))
          .notcached)
    }.getOrElse(InternalServerError("\"callback\" parameter required"))
  }

}