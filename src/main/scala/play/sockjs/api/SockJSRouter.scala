package play.sockjs.api

import scala.runtime.AbstractPartialFunction
import scala.concurrent.Future

import play.core.Router
import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.api.SockJS._
import play.sockjs.core._

trait SockJSRouter extends Router.Routes {

  def server: SockJSServer = SockJSServer.default

  private lazy val dispatcher = server.dispatcher(prefix)

  private var path: String = ""

  def prefix: String = path

  def setPrefix(prefix: String): Unit = path = prefix

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

  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]) = Builder().using(f)

  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]) = Builder().adapter(f)

  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]) = Builder().async(f)

  def apply(settings: SockJSSettings): Builder = Builder(SockJSServer(settings = settings))

  def apply(f: SockJSSettings => SockJSSettings): Builder = Builder(SockJSServer.default.reconfigure(f))

  def apply(server: SockJSServer): Builder = Builder(server)

  case class Builder private[SockJSRouter](server: SockJSServer = SockJSServer.default) {

    def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.using(f))

    def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.adapter(f))

    def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]) = SockJSRouter(server, SockJS.async(f))

  }

  private[sockjs] def apply(server: SockJSServer, sockjs: SockJS[_, _]): SockJSRouter = {
    val (_server, _sockjs) = (server, sockjs)
    new SockJSRouter {
      override val server = _server
      def sockjs = _sockjs
    }
  }

}