package play.sockjs.api

import scala.concurrent.Future

import play.core.Execution.internalContext
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._
import akka.actor.ActorSystem

case class SockJS[A](f: RequestHeader => (Enumerator[A], Iteratee[A, Unit]) => Unit)(implicit val formatter: SockJS.MessageFormatter[A]) {

  type MESSAGES_TYPE = A

}

object SockJS {

  /**
   * Typeclass to handle SockJS message format.
   *
   * @param read Convert a text message sent from SockJS client into an A
   * @param write Convert an A in a text message to be sent to SockJS clien
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

  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]): SockJS[A] = {
    SockJS(h => (e, i) => {val (rIn, wOut) = f(h); e |>> rIn; wOut |>> i})
  }

  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]): SockJS[A] = {
    SockJS[A](h => (in, out) => {in &> f(h) |>> out})
  }

  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]): SockJS[A] = {
    using { rh =>
      val p = f(rh)
      val it = Iteratee.flatten(p.map(_._1)(internalContext))
      val enum = Enumerator.flatten(p.map(_._2)(internalContext))
      (it, enum)
    }
  }

}