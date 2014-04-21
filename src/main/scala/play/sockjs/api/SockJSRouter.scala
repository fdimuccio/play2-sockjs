package play.sockjs.api

import scala.runtime.AbstractPartialFunction
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorSystem

import play.core.Router
import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.core._
import play.sockjs.api.SockJS._
import play.sockjs.api.SockJSSettings._

trait SockJSRouter extends Router.Routes {

  def server: SockJSServer = SockJSServer.default

  private lazy val dispatcher = server.dispatcher(prefix.split("/").drop(1).mkString("_"))

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
  def sockjs: SockJS[_]

}

object SockJSRouter {

  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit formatter: MessageFormatter[A]): Builder = Builder(SockJS.using(f))

  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit formatter: MessageFormatter[A]): Builder = Builder(SockJS.adapter(f))

  def async[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit formatter: MessageFormatter[A]): Builder = Builder(SockJS.async(f))

  case class Builder private(sockjs: SockJS[_], override val server: SockJSServer = SockJSServer.default) extends SockJSRouter {

    def script(f: RequestHeader => String) = copy(server = server.reconfigure(_.scriptSRC(f)))

    def websocket(enabled: Boolean) = copy(server = server.reconfigure(_.websocket(enabled)))

    def jsessionid(enabled: Boolean) = cookies(if (enabled) Some(CookieCalculator.jsessionid) else None)

    def cookies(calculator: CookieCalculator): Builder = cookies(Some(calculator))

    def cookies(calculator: Option[CookieCalculator]): Builder = copy(server = server.reconfigure(_.cookies(calculator)))

    def heartbeat(interval: FiniteDuration) = copy(server = server.reconfigure(_.heartbeat(interval)))

    def sessionTimeout(timeout: FiniteDuration) = copy(server = server.reconfigure(_.sessionTimeout(timeout)))

    def streamingQuota(quota: Long) = copy(server = server.reconfigure(_.streamingQuota(quota)))

    def serverName(name: String) = copy(server = server.copy(name = Some(name)))

    def actorSystem(system: ActorSystem) = copy(server = server.copy(actorSystem = system))

  }

}