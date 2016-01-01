package play.sockjs.api

import scala.runtime.AbstractPartialFunction
import scala.concurrent.Future

import akka.stream.Materializer
import akka.stream.scaladsl.Flow

import play.api.routing.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.api.SockJS._
import play.sockjs.core._

trait SockJSRouter extends Router {

  implicit def materializer: Materializer

  private lazy val dispatcher = new Dispatcher(new Transport(materializer))

  private var prefix: String = ""

  def withPrefix(prefix: String): Router = {
    //TODO: return a copy of the router with updated prefix
    this.prefix = prefix
    this
  }

  def documentation: Seq[(String, String, String)] = Seq.empty

  final def routes = new AbstractPartialFunction[RequestHeader, Handler] {

    override def applyOrElse[A <: RequestHeader, B >: Handler](rh: A, default: A => B): B = {
      if (rh.path.startsWith(prefix)) {
        (rh.method, rh.path.drop(prefix.length)) match {
          case dispatcher(SockJSHandler(handler)) => handler(rh, sockjs)
          case dispatcher(handler: Handler) => handler
          case _ => Action(NotFound)
        }
      } else default(rh)
    }

    override def isDefinedAt(rh: RequestHeader): Boolean = {
      if (rh.path.startsWith(prefix)) {
        (rh.method, rh.path.drop(prefix.length)) match {
          case dispatcher(_) => true
          case _ => false
        }
      } else false
    }

  }

  /**
   * SockJS handler
   */
  def sockjs: SockJS

}

object SockJSRouter extends SockJSOps {

  type Repr = SockJSRouter

  /**
    * Creates an action that will either accept the SockJS connection, using the given flow to handle the in and out stream, or
    * return a result to reject the request.
    */
  def acceptOrResult[In, Out](f: (RequestHeader) => Future[Either[Result, Flow[In, Out, _]]])(implicit transformer: MessageFlowTransformer[In, Out]): SockJSRouter = {
    apply(SockJSSettings.default).acceptOrResult(f)
  }

  /**
   * Creates a SockJS router with application materializer and given settings
   */
  def apply(settings: SockJSSettings): Builder = Builder(play.api.Play.current.materializer, settings)

  /**
   * Creates a SockJS router with the given materializer and the given settings
   */
  def apply(materializer: Materializer, settings: SockJSSettings): Builder = Builder(materializer, settings)

  case class Builder private[SockJSRouter](materializer: Materializer, settings: SockJSSettings) extends SockJSOps {

    type Repr = SockJSRouter

    /**
      * Creates an action that will either accept the SockJS connection, using the given flow to handle the in and out stream, or
      * return a result to reject the request.
      */
    def acceptOrResult[In, Out](f: (RequestHeader) => Future[Either[Result, Flow[In, Out, _]]])(implicit transformer: MessageFlowTransformer[In, Out]): SockJSRouter = {
      SockJSRouter(materializer, SockJS(settings).acceptOrResult(f))
    }
  }

  private[sockjs] def apply(materializer: Materializer, sockjs: SockJS): SockJSRouter = {
    val (_materializer, _sockjs) = (materializer, sockjs)
    new SockJSRouter {
      def materializer = _materializer
      def sockjs = _sockjs
    }
  }
}