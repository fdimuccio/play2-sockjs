package play.sockjs.core
package transports

import scala.concurrent.Future

import play.api.mvc._
import play.api.http._
import play.api.libs.json._

private[sockjs] class Jsonp(transport: Transport) extends HeaderNames with Results {

  /**
   * handler for jsonp_send
   */
  def send = transport.send { req =>
    Ok("ok").as("text/plain; charset=UTF-8").notcached
  }

  /**
   * Handler for jsonp polling transport
   */
  def polling = transport.polling { req =>
    req.getQueryString("c").orElse(req.getQueryString("callback")).map { callback =>
      if (callback.matches("[a-zA-Z0-9-_.]*") && callback.length <= 32)
        req.bind { source =>
          source.map { frame =>
            s"/**/$callback(${JsString(frame.encode)});\r\n"
          }
        }.map(_.withHeaders("X-Content-Type-Options" -> "nosniff"))(play.api.libs.iteratee.Execution.trampoline)
      else
        Future.successful(InternalServerError("invalid \"callback\" parameter"))
    }.getOrElse(Future.successful(InternalServerError("\"callback\" parameter required")))
  }

  implicit val writeableOf_JsonpTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("application/javascript; charset=UTF-8"))

}