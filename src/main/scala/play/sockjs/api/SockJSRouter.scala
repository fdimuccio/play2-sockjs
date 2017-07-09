package play.sockjs.api

import javax.inject.Inject

import akka.stream.Materializer

import play.api.routing.Router
import play.api.mvc._
import play.api.mvc.Results._

import play.sockjs.core._

/**
  * Base implementation of a SockJS router.
  *
  * To implement a SockJS handler You have to extend this trait or one of the
  * classes or traits that inherits this one.
  *
  * For example:
  * {{{
  *   class MySockJSRouter @Inject() (val components: SockJSRouterComponents) extends BaseSockJSRouter {
  *     def sockjs = SockJS.acceptOrResult[JsValue, JsValue] { request =>
  *       ...
  *     }
  *   }
  * }}}
  */
trait BaseSockJSRouter extends Router {

  private var prefix: String = ""

  final def withPrefix(prefix: String): Router = {
    this.prefix = prefix
    this
  }

  final def documentation: Seq[(String, String, String)] = Seq.empty

  private lazy val dispatcher = new Dispatcher(
    new Server(settings, components.materializer, components.actionBuilder, components.parser))

  final def routes = {
    case rh: RequestHeader if rh.path.startsWith(prefix) =>
      (rh.method, rh.path.drop(prefix.length)) match {
        case dispatcher(SockJSHandler(handler)) => handler(rh, sockjs)
        case dispatcher(handler) => handler
        case _ => components.actionBuilder(NotFound)
      }
  }

  /**
    * Provides the components needed by the underlying router and SockJS server
    */
  protected def components: SockJSRouterComponents

  /**
    * Override this method to specify different settings
    */
  protected def settings: SockJSSettings = SockJSSettings()

  /**
   * SockJS handler
   */
  def sockjs: SockJS
}

/**
  * An abstract implementation of [[BaseSockJSRouter]] to make it easier to use
  */
abstract class AbstractSockJSRouter(protected val components: SockJSRouterComponents) extends BaseSockJSRouter

/**
  * A variation of [[BaseSockJSRouter]] that gets its components via method injection.
  *
  * For example:
  *   class MySockJSRouter @Inject() () extends InjectedSockJSRouter {
  *     def sockjs = SockJS.acceptOrResult[JsValue, JsValue] { request =>
  *       ...
  *     }
  *   }
  */
trait InjectedSockJSRouter extends BaseSockJSRouter {

  private[this] var _components: SockJSRouterComponents = _

  override protected def components: SockJSRouterComponents = {
    if (_components == null) fallbackComponents else _components
  }

  /**
    * Call this method to set the [[SockJSRouterComponents]] instance.
    */
  @Inject
  def setComponents(components: SockJSRouterComponents): Unit = {
    _components = components
  }

  /**
    * Defines fallback components to use in case setComponents has not been called.
    */
  protected def fallbackComponents: SockJSRouterComponents = {
    throw new NoSuchElementException(
      "SockJSRouterComponents not set! Call setComponents or create the instance with dependency injection.")
  }
}

trait SockJSRouterComponents {
  def materializer: Materializer
  def actionBuilder: ActionBuilder[Request, AnyContent]
  def parser: PlayBodyParsers
}

case class DefaultSockJSRouterComponents @Inject() (
  materializer: Materializer,
  actionBuilder: DefaultActionBuilder,
  parser: PlayBodyParsers
) extends SockJSRouterComponents

/**
  * It is reccomended to use one of the classes or traits extending [[BaseSockJSRouter]] instead.
  */
@deprecated(
  "Your SockJS router should extend BaseSockJSRouter, AbstractSockJSRouter, or InjectedSockJSRouter instead.",
  "0.6.0")
trait SockJSRouter extends BaseSockJSRouter {

  /**
    * Override this method to inject a different materializer
    */
  protected def materializer: Materializer = play.api.Play.privateMaybeApplication
    .getOrElse(sys.error("No application started")).materializer

  /**
    * Provides the components needed by the underlying router and SockJS server
    */
  override protected def components: SockJSRouterComponents =
    DefaultSockJSRouterComponents(materializer, Action, BodyParsers.parse)
}