package play.sockjs.api

import scala.concurrent.Future
import scala.reflect.ClassTag

import akka.actor.{ActorRef, Props}

import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._

import play.core.Execution.Implicits.internalContext

import play.sockjs.core.actors._
import play.sockjs.core.actors.SockJSActor.SockJSExtension

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

  /**
   * Accepts a SockJS connection using the given inbound/outbound channels.
   */
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    tryAccept[A](f.andThen(handler => Future.successful(Right(handler))))
  }

  /**
   * Creates a SockJS handler that will adapt the incoming stream and send it back out.
   */
  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    SockJS[A, A](h => Future.successful(Right((in, out) => { in &> f(h) |>> out })))
  }

  /**
   * Accepts a SockJS connection using the given inbound/outbound channels asynchronously.
   */
  @deprecated("Use SockJS.tryAccept instead", "0.3")
  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    tryAccept(f.andThen(_.map(Right.apply)))
  }

  /**
   * Creates a SockJS handler that will either reject the connection with the given result, or will be handled by the given
   * inbound and outbound channels, asynchronously
   */
  def tryAccept[A](f: RequestHeader => Future[Either[Result, (Iteratee[A, _], Enumerator[A])]])(implicit formatter: MessageFormatter[A]): SockJS[A, A] = {
    SockJS[A, A](f.andThen(_.map { resultOrSocket =>
      resultOrSocket.right.map {
        case (readIn, writeOut) => (e, i) => { e |>> readIn; writeOut |>> i }
      }
    }))
  }

  /**
   * A function that, given an actor to send upstream messages to, returns actor props to create an actor to handle
   * the SockJS connection
   */
  type HandlerProps = ActorRef => Props

  /**
   * Create a SockJS handler that will pass messages to/from the actor created by the given props asynchronously.
   *
   * Given a request, this method should return a future of either:
   *
   * - A result to reject the WebSocket with, or
   * - A function that will take the sending actor, and create the props that describe the actor to handle this SockJS connection
   *
   * For example:
   *
   * {{{
   *   def subscribe = SockJS.acceptWithActor[JsValue, JsValue] { req =>
   *     val isAuthenticated: Future[Boolean] = authenticate(req)
   *     val isAuthenticated.map {
   *       case false => Left(Forbidden)
   *       case true => Right(MySockJSActor.props)
   *     }
   *   }
   * }}}
   */
  def tryAcceptWithActor[In, Out](f: RequestHeader => Future[Either[Result, HandlerProps]])(implicit in: MessageFormatter[In], out: MessageFormatter[Out], app: play.Application, outMessageType: ClassTag[Out]): SockJS[In, Out] = {
    SockJS[In, Out] { request =>
      f(request).map { resultOrProps =>
        resultOrProps.right.map { props =>
          (enumerator, iteratee) =>
            ???
            //SockJSExtension(play.api.libs.concurrent.Akka.system).actor !
            //  SockJSActor.Connect(request.id, enumerator, iteratee, props)
        }
      }
    }
  }
}