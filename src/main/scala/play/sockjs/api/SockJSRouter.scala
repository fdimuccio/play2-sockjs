package play.sockjs.api

import akka.stream.Materializer

import play.api.routing.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.core._

trait SockJSRouter extends Router {

  private var prefix: String = ""

  final def withPrefix(prefix: String): Router = {
    this.prefix = prefix
    this
  }

  final def documentation: Seq[(String, String, String)] = Seq.empty

  private lazy val dispatcher = new Dispatcher(new Server(settings, materializer))

  final def routes = {
    case rh: RequestHeader if rh.path.startsWith(prefix) =>
      (rh.method, rh.path.drop(prefix.length)) match {
        case dispatcher(SockJSHandler(handler)) => handler(rh, sockjs)
        case dispatcher(handler) => handler
        case _ => Action(NotFound)
      }
  }

  /**
    * Override this method to use a different materializer
    */
  protected def materializer: Materializer = play.api.Play.privateMaybeApplication
    .getOrElse(sys.error("There is no started application")).materializer

  /**
    * Override this method to specify different settings
    */
  protected def settings: SockJSSettings = SockJSSettings()

  /**
   * SockJS handler
   */
  def sockjs: SockJS

}