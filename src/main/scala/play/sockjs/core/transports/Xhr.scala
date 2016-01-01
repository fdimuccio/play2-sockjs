package play.sockjs.core
package transports

import akka.stream.scaladsl._
import akka.util.ByteString

import play.api.mvc._
import play.api.http._

private[sockjs] class Xhr(transport: Transport) extends HeaderNames with Results {
  import Xhr._

  /**
   * handler for xhr options req
   */
  def options = Action { implicit req =>
    OptionsResult("OPTIONS", "POST")
  }

  /**
   * handler for xhr_send
   */
  def send = transport.send { req =>
    NoContent.enableCORS(req).notcached.as("text/plain; charset=UTF-8")
  }

  /**
   * handler for xhr polling transport
   */
  def polling = transport.polling { req =>
    req.bind("application/javascript; charset=UTF-8") { source =>
      source.map(_.encode ++ newLine)
    }.map(_.enableCORS(req))(play.api.libs.iteratee.Execution.trampoline)
  }

  /**
   * handler for xhr_streaming transport
   */
  def streaming = transport.streaming { req =>
    req.bind("application/javascript; charset=UTF-8") { source =>
      Source.single(prelude).concat(source.map(_.encode ++ newLine))
    }.map(_.enableCORS(req))(play.api.libs.iteratee.Execution.trampoline)
  }
}

private[sockjs] object Xhr {
  private val prelude = ByteString(Array.fill(2048)('h').mkString + '\n')
  private val newLine = ByteString('\n')
}