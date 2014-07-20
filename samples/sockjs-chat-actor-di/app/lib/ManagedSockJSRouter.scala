package lib

import scala.reflect.ClassTag

import play.api.Play
import play.api.mvc._

import play.sockjs.api._

/**
 * Trait to mixin a controller that implements a SockJS endpoint
 */
trait SockJSController extends Controller {
  def settings: SockJSSettings = SockJSSettings.default
  def sockjs: SockJS[_, _]
}

/**
 * A SockJS router that uses managed SockJS controller
 *
 * {{{
 *   class MySockJSController(dbService: DbService) extends SockJSController {
 *     def sockjs = ...
 *
 *     ...
 *   }
 *   object MySockJSController extends ManagedSockJSRouter[MySockJSController]
 * }}}
 *
 * Then from a routes file can be used like so:
 *
 * {{{
 *   ->   /sockjs     controllers.MySockJSController
 * }}}
 */
class ManagedSockJSRouter[T <: SockJSController](implicit ct: ClassTag[T]) extends SockJSRouter {

  private lazy val getControllerInstance: T = {
    Play.maybeApplication.map { app =>
      app.global.getControllerInstance(ct.runtimeClass.asInstanceOf[Class[T]])
    } getOrElse {
      sys.error("No running application!")
    }
  }

  override def server = SockJSServer(getControllerInstance.settings)

  def sockjs = getControllerInstance.sockjs

}