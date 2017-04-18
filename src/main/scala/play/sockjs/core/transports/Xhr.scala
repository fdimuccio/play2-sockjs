package play.sockjs.core
package transports

import akka.stream.scaladsl._
import akka.util.ByteString

import play.api.mvc._
import play.api.http._

private[sockjs] class Xhr(server: Server) extends HeaderNames with Results {
  import Xhr._

  /**
   * handler for xhr options req
   */
  def options = server.action { implicit req: Request[AnyContent] =>
    OptionsResult("OPTIONS", "POST")
  }

  /**
   * handler for xhr_send
   */
  def send = server.send { req =>
    NoContent.enableCORS(req).notcached.as("text/plain; charset=UTF-8")
  }

  /**
   * handler for xhr polling transport
   */
  def polling = server.polling { req =>
    req.bind("application/javascript; charset=UTF-8") { source =>
      source.map(_ ++ newLine)
    }.map(_.enableCORS(req))(play.core.Execution.trampoline)
  }

  /**
   * handler for xhr_streaming transport
   */
  def streaming = server.streaming { req =>
    req.bind("application/javascript; charset=UTF-8") { source =>
      Source.single(prelude).concat(source.map(_ ++ newLine))
    }.map(_.enableCORS(req))(play.core.Execution.trampoline)
  }
}

private[sockjs] object Xhr {
  private val prelude = ByteString(Array.fill(2048)('h').mkString + '\n')
  private val newLine = ByteString('\n')
}