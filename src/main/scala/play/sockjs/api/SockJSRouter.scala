package play.sockjs.api

import scala.runtime.AbstractPartialFunction
import scala.concurrent.Future

import play.api.routing.Router
import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.api.SockJS._
import play.sockjs.core._
import scala.reflect.ClassTag

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
  def sockjs: SockJS[_, _]

}

object SockJSRouter {

  /**
   * Create a SockJS router that accepts a SockJS connection using the given inbound/outbound channels.
   */
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]) = Builder().using(f)

  /**
   * Create a SockJS router that will adapt the incoming stream and send it back out.
   */
  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]) = Builder().adapter(f)

  /**
   * Create a SockJS router that accepts a connection using the given inbound/outbound channels asynchronously.
   */
  @deprecated("Use SockJSRouter.tryAccept instead", "0.3")
  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]) = Builder().async(f)

  /**
   * Creates a SockJS router that will either reject the connection with the given result, or will be handled by the given
   * inbound and outbound channels, asynchronously
   */
  def tryAccept[A](f: RequestHeader => Future[Either[Result, (Iteratee[A, _], Enumerator[A])]])(implicit formatter: MessageFormatter[A]) = Builder().tryAccept(f)

  /**
   * Create a SockJS router that will pass messages to/from the actor created by the given props.
   *
   * Given a request and an actor ref to send messages to, the function passed should return the props for an actor
   * to create to handle this SockJS connection.
   *
   * For example:
   *
   * {{{
   *   lazy val sockjs = SockJSRouter.acceptWithActor[JsValue, JsValue] { req => out =>
   *     MySockJSActor.props(out)
   *   }
   * }}}
   */
  def acceptWithActor[In, Out](f: RequestHeader => HandlerProps)(implicit in: MessageFormatter[In], out: MessageFormatter[Out], app: play.api.Application, outMessageType: ClassTag[Out]) = Builder().acceptWithActor[In, Out](f)

  /**
   * Create a SockJS router that will pass messages to/from the actor created by the given props asynchronously.
   *
   * Given a request, this method should return a future of either:
   *
   * - A result to reject the WebSocket with, or
   * - A function that will take the sending actor, and create the props that describe the actor to handle this SockJS connection
   *
   * For example:
   *
   * {{{
   *   lazy val sockjs = SockJSRouter.acceptWithActor[JsValue, JsValue] { req =>
   *     val isAuthenticated: Future[Boolean] = authenticate(req)
   *     val isAuthenticated.map {
   *       case false => Left(Forbidden)
   *       case true => Right(MySockJSActor.props)
   *     }
   *   }
   * }}}
   */
  def tryAcceptWithActor[In, Out](f: RequestHeader => Future[Either[Result, HandlerProps]])(implicit in: MessageFormatter[In], out: MessageFormatter[Out], app: play.api.Application, outMessageType: ClassTag[Out]) = Builder().tryAcceptWithActor[In, Out](f)

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

  case class Builder private[SockJSRouter](server: SockJSServer = SockJSServer.default) {

    /**
     * Create a SockJS router that accepts a SockJS connection using the given inbound/outbound channels.
     */
    def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.using(f))

    /**
     * Create a SockJS router that will adapt the incoming stream and send it back out.
     */
    def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.adapter(f))

    /**
     * Create a SockJS router that accepts a connection using the given inbound/outbound channels asynchronously.
     */
    @deprecated("Use SockJSRouter.tryAccept instead", "0.3")
    def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.async(f))

    /**
     * Creates a SockJS router that will either reject the connection with the given result, or will be handled by the given
     * inbound and outbound channels, asynchronously
     */
    def tryAccept[A](f: RequestHeader => Future[Either[Result, (Iteratee[A, _], Enumerator[A])]])(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.tryAccept(f))

    /**
     * Create a SockJS router that will pass messages to/from the actor created by the given props.
     *
     * Given a request and an actor ref to send messages to, the function passed should return the props for an actor
     * to create to handle this SockJS connection.
     *
     * For example:
     *
     * {{{
     *   lazy val sockjs = SockJSRouter.acceptWithActor[JsValue, JsValue] { req => out =>
     *     MySockJSActor.props(out)
     *   }
     * }}}
     */
    def acceptWithActor[In, Out](f: RequestHeader => HandlerProps)(implicit in: MessageFormatter[In], out: MessageFormatter[Out], app: play.api.Application, outMessageType: ClassTag[Out]) = SockJSRouter(server, SockJS.acceptWithActor[In, Out](f))

    /**
     * Create a SockJS router that will pass messages to/from the actor created by the given props asynchronously.
     *
     * Given a request, this method should return a future of either:
     *
     * - A result to reject the WebSocket with, or
     * - A function that will take the sending actor, and create the props that describe the actor to handle this SockJS connection
     *
     * For example:
     *
     * {{{
     *   lazy val sockjs = SockJSRouter.acceptWithActor[JsValue, JsValue] { req =>
     *     val isAuthenticated: Future[Boolean] = authenticate(req)
     *     val isAuthenticated.map {
     *       case false => Left(Forbidden)
     *       case true => Right(MySockJSActor.props)
     *     }
     *   }
     * }}}
     */
    def tryAcceptWithActor[In, Out](f: RequestHeader => Future[Either[Result, HandlerProps]])(implicit in: MessageFormatter[In], out: MessageFormatter[Out], app: play.api.Application, outMessageType: ClassTag[Out]) = SockJSRouter(server, SockJS.tryAcceptWithActor[In, Out](f))
  }

  private[sockjs] def apply(server: SockJSServer, sockjs: SockJS[_, _]): SockJSRouter = {
    val (_server, _sockjs) = (server, sockjs)
    new SockJSRouter {
      override val server = _server
      def sockjs = _sockjs
    }
  }

}