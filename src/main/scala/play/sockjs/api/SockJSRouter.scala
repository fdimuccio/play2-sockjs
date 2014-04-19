package play.sockjs.api

import scala.runtime.AbstractPartialFunction

import play.core.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.core._

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