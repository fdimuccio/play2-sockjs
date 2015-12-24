package play.sockjs.api

import akka.stream.scaladsl.Flow

import scala.runtime.AbstractPartialFunction
import scala.concurrent.Future

import play.api.routing.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.api.SockJS._
import play.sockjs.core._

trait SockJSRouter extends Router {

  def server: SockJSServer = SockJSServer.default

  private lazy val dispatcher = server.dispatcher(prefix)

  private var prefix: String = ""

  def withPrefix(prefix: String): Router = {
    //TODO: return a copy of the router with updated prefix
    this.prefix = prefix
    this
  }

  def documentation: Seq[(String, String, String)] = Seq.empty

  def routes = new AbstractPartialFunction[RequestHeader, Handler] {

    override def applyOrElse[A <: RequestHeader, B >: Handler](rh: A, default: A => B): B = {
      if (rh.path.startsWith(prefix)) {
        (rh.method, rh.path.drop(prefix.length)) match {
          case dispatcher(SockJSAction(handler)) => handler
          case dispatcher(SockJSTransport(transport)) => transport(sockjs)
          case dispatcher(SockJSWebSocket(transport)) => transport(rh).f(sockjs)
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
    Builder().acceptOrResult(f)
  }

  /**
   * Creates a SockJS router with default server and given settings
   */
  def apply(settings: SockJSSettings): Builder = Builder(SockJSServer(settings = settings))

  /**
   * Creates a SockJS router with default server and a function to modify the default settings
   */
  def apply(f: SockJSSettings => SockJSSettings): Builder = Builder(SockJSServer.default.reconfigure(f))

  /**
   * Creates a SockJS router with the given server
   */
  def apply(server: SockJSServer): Builder = Builder(server)

  case class Builder private[SockJSRouter](server: SockJSServer = SockJSServer.default) extends SockJSOps {

    type Repr = SockJSRouter

    /**
      * Creates an action that will either accept the SockJS connection, using the given flow to handle the in and out stream, or
      * return a result to reject the request.
      */
    def acceptOrResult[In, Out](f: (RequestHeader) => Future[Either[Result, Flow[In, Out, _]]])(implicit transformer: MessageFlowTransformer[In, Out]): SockJSRouter = {
      SockJSRouter(server, SockJS.acceptOrResult(f))
    }
  }

  private[sockjs] def apply(server: SockJSServer, sockjs: SockJS): SockJSRouter = {
    val (_server, _sockjs) = (server, sockjs)
    new SockJSRouter {
      override val server = _server
      def sockjs = _sockjs
    }
  }
}