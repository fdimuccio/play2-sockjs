package play.sockjs.api

import akka.stream.Materializer

import play.api.routing.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.core._

//TODO: this class should become an abstract class with two parameters:
//      - SockJSComponents (this one injected)
//      - SockJSSettings (this one optional, if not specified use default)
trait SockJSRouter extends Router {

  private var prefix: String = ""

  final def withPrefix(prefix: String): Router = {
    this.prefix = prefix
    this
  }

  final def documentation: Seq[(String, String, String)] = Seq.empty

  private lazy val dispatcher = new Dispatcher(
    new Server(settings, materializer, Action, BodyParsers.parse))

  final def routes = {
    case rh: RequestHeader if rh.path.startsWith(prefix) =>
      (rh.method, rh.path.drop(prefix.length)) match {
        case dispatcher(SockJSHandler(handler)) => handler(rh, sockjs)
        case dispatcher(handler) => handler
        case _ => Action(NotFound)
      }
  }

  /**
    * Override this method to inject a different materializer
    */
  protected def materializer: Materializer = play.api.Play.privateMaybeApplication
    .getOrElse(sys.error("No application started")).materializer

  /**
    * Override this method to specify different settings
    */
  protected def settings: SockJSSettings = SockJSSettings()

  /**
   * SockJS handler
   */
  def sockjs: SockJS

}