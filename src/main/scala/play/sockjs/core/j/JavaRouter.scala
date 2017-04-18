package play.sockjs.core.j

import scala.compat.java8.OptionConverters._
import akka.stream.Materializer
import play.api.mvc._
import play.api.routing.Router
import play.core.j
import play.sockjs.core.{Dispatcher, Server, SockJSHandler}

abstract class JavaRouter extends play.mvc.Controller with Router {

  private var prefix: String = ""

  final def withPrefix(prefix: String) = {
    this.prefix = prefix
    this
  }

  final def documentation: Seq[(String, String, String)] = Seq.empty

  private lazy val dispatcher = new Dispatcher(new Server({
    val cfg = JavaRouter.this.settings
    play.sockjs.api.SockJSSettings(
      scriptSRC = req => cfg.scriptSRC()(new j.RequestHeaderImpl(req)),
      cookies = cfg.cookies().asScala.map { f =>
        req =>
          val jcookie = f(new j.RequestHeaderImpl(req))
          Cookie(
            jcookie.name(),
            jcookie.value(),
            if (jcookie.maxAge() == null) None else Some(jcookie.maxAge()),
            jcookie.path(),
            Option(jcookie.domain()),
            jcookie.secure(),
            jcookie.httpOnly())
      },
      websocket = cfg.websocket(),
      heartbeat = cfg.heartbeat(),
      sessionTimeout = cfg.sessionTimeout(),
      streamingQuota = cfg.streamingQuota(),
      sendBufferSize = cfg.sendBufferSize(),
      sessionBufferSize = cfg.sessionBufferSize())
  }, materializer, Action, BodyParsers.parse))

  final def routes = {
    case rh if rh.path.startsWith(prefix) =>
      (rh.method, rh.path.drop(prefix.length)) match {
        case dispatcher(handler: SockJSHandler) => new JavaSockJS(rh, handler, sockjs)
        case dispatcher(handler) => handler
        case _ => Action(Results.NotFound)
      }
  }

  /**
    * Override this method to use a different materializer
    */
  protected def materializer: Materializer = play.api.Play.privateMaybeApplication.get.materializer

  protected def settings: play.sockjs.SockJSSettings = new play.sockjs.SockJSSettings()

  def sockjs: play.sockjs.SockJS
}