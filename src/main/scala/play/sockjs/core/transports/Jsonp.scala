package play.sockjs.core
package transports

import scala.concurrent.Future

import play.api.mvc._
import play.api.http._
import play.api.libs.iteratee._
import play.api.libs.json._

private[sockjs] object Jsonp extends HeaderNames with Results {

  /**
   * handler for jsonp_send
   */
  def send = Transport.Send(req => Ok("ok").withHeaders(CONTENT_TYPE -> "text/plain; charset=UTF-8").notcached)

  /**
   * Handler for jsonp polling transport
   */
  def polling = Transport.Polling { (req, session) =>
    req.getQueryString("c").orElse(req.getQueryString("callback")).map { callback =>
      if (!callback.matches("[^a-zA-Z0-9-_.]"))
        session.bind { enumerator =>
          Transport.Res(enumerator &> Enumeratee.map { (frame: Frame) =>
            s"$callback(${JsString(frame.text)});\r\n"
          }(play.api.libs.iteratee.Execution.trampoline))
        }
      else
        Future.successful(InternalServerError("invalid \"callback\" parameter"))
    }.getOrElse(Future.successful(InternalServerError("\"callback\" parameter required")))
  }

  implicit def writeableOf_JsonpTransport: Writeable[String] = Writeable[String] (
    txt => Codec.utf_8.encode(txt),
    Some("application/javascript; charset=UTF-8"))

}