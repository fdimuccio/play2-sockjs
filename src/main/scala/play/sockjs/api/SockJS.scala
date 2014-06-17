package play.sockjs.api

import scala.concurrent.Future

import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._

import play.core.Execution.Implicits.internalContext

case class SockJS[IN, OUT](f: RequestHeader => Future[Either[Result, (Enumerator[IN], Iteratee[OUT, Unit]) => Unit]])(implicit val inFormatter: SockJS.MessageFormatter[IN], val outFormatter: SockJS.MessageFormatter[OUT]) {

  type FramesIN = IN
  type FramesOUT = OUT

}

object SockJS {

  /**
   * Typeclass to handle SockJS message format.
   *
   * @param read Convert a text message sent from SockJS client into an A
   * @param write Convert an A in a text message to be sent to SockJS client
   * @tparam A
   */
  case class MessageFormatter[A](read: String => A, write: A => String) {

    /**
     * Transform a MessageFormatter[A] to a MessageFormatter[B]
     */
    def transform[B](fba: B => A, fab: A => B) = MessageFormatter[B](read.andThen(fab), write.compose(fba))

  }

  object MessageFormatter {

    implicit val textMessage: MessageFormatter[String] = MessageFormatter(identity, identity)

    implicit val jsonMessage: MessageFormatter[JsValue] = MessageFormatter(Json.parse, Json.stringify)

  }

  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    tryAccept[A](f.andThen(handler => Future.successful(Right(handler))))
  }

  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    SockJS[A, A](h => Future.successful(Right((in, out) => { in &> f(h) |>> out })))
  }

  @deprecated("Use SockJS.tryAccept instead", "0.3")
  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    tryAccept(f.andThen(_.map(Right.apply)))
  }

  def tryAccept[A](f: RequestHeader => Future[Either[Result, (Iteratee[A, _], Enumerator[A])]])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    SockJS[A, A](f.andThen(_.map { resultOrSocket =>
      resultOrSocket.right.map {
        case (readIn, writeOut) => (e, i) => { e |>> readIn; writeOut |>> i }
      }
    }))
  }

  //TODO: actors api

}