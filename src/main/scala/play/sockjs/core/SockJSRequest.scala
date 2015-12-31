package play.sockjs.core

import scala.concurrent.Future

import akka.stream.scaladsl._

import play.api.mvc._
import play.api.http.Writeable

import play.sockjs.api.Frame

abstract class SockJSRequest(request: Request[AnyContent]) extends WrappedRequest[AnyContent](request) {

  def bind[A](f: Source[Frame, _] => Source[A, _])(implicit writeable: Writeable[A]): Future[Result]

}
