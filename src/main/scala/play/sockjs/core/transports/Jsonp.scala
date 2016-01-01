package play.sockjs.core
package transports

import scala.concurrent.Future

import akka.util.ByteString

import play.api.mvc._
import play.api.http._

import play.sockjs.core.json.JsonByteStringEncoder

private[sockjs] class Jsonp(transport: Transport) extends HeaderNames with Results {
  import Jsonp._

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
        req.bind("application/javascript; charset=UTF-8") { source =>
          val prelude = ByteString(s"/**/$callback(")
          source.map { frame =>
            prelude ++ JsonByteStringEncoder.encodeFrame(frame) ++ crlf
          }
        }.map(_.withHeaders("X-Content-Type-Options" -> "nosniff"))(play.api.libs.iteratee.Execution.trampoline)
      else
        Future.successful(InternalServerError("invalid \"callback\" parameter"))
    }.getOrElse(Future.successful(InternalServerError("\"callback\" parameter required")))
  }
}

private[sockjs] object Jsonp {
  private val crlf = ByteString(");\r\n")
}