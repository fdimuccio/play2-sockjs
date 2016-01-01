package play.sockjs.core

import scala.concurrent.Future

import akka.stream.scaladsl._
import akka.util.ByteString

import play.api.mvc._

import play.sockjs.api.Frame

/**
  * Helper to bind a request to a session
  */
abstract class SockJSRequest(request: Request[AnyContent]) extends WrappedRequest[AnyContent](request) {

  /**
    * Bind this request to the given `Source`
    */
  def bind(ctype: String)(f: Source[Frame, _] => Source[ByteString, _]): Future[Result]
}
