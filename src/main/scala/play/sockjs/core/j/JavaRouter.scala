package play.sockjs.core.j

import scala.compat.java8.OptionConverters._

import akka.stream.Materializer

import play.api.mvc.Cookie
import play.api.routing.Router
import play.core.j

abstract class JavaRouter extends play.mvc.Controller with Router {

  final def withPrefix(prefix: String) = scalaRouter.withPrefix(prefix)

  final def documentation = scalaRouter.documentation

  final def routes = scalaRouter.routes

  protected def materializer: Materializer = play.api.Play.privateMaybeApplication.get.materializer

  protected def settings: play.sockjs.SockJSSettings = new play.sockjs.SockJSSettings()

  def sockjs: play.sockjs.SockJS

  private lazy val scalaRouter = new play.sockjs.api.SockJSRouter {
    import play.sockjs.api._

    /**
      * Override this method to use a different materializer
      */
    override protected def materializer: Materializer = JavaRouter.this.materializer

    /**
      * SockJS settings for this handler
      */
    override protected lazy val settings: SockJSSettings = {
      val cfg = JavaRouter.this.settings
      SockJSSettings(
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
    }

    lazy val sockjs: SockJS = JavaSockJS.run(JavaRouter.this.sockjs)
  }
}