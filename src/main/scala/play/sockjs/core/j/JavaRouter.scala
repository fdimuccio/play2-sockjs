package play.sockjs.core.j

import java.util.Optional
import java.util.concurrent.TimeUnit

import akka.stream.scaladsl.Flow
import play.api.mvc.{Result, RequestHeader}

import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.routing.Router
import play.core.j._

abstract class JavaRouter(cfg: Optional[play.sockjs.SockJS.Settings]) extends Router {
  self =>

  def withPrefix(prefix: String) = scalaRouter.withPrefix(prefix)

  def documentation = scalaRouter.documentation

  def routes = scalaRouter.routes

  def sockjs: play.sockjs.SockJS

  private lazy val scalaRouter = new play.sockjs.api.SockJSRouter {
    import play.sockjs.api._

    lazy val materializer = play.api.Play.current.materializer

    def sockjs: SockJS = new SockJS {

      /**
        * SockJS settings for this handler
        */
      lazy val settings: SockJSSettings = {
        val settings = Option(cfg.orElse(null)).orElse(for {
          method <- Option(self.getClass.getMethod("sockjs"))
          cfg <- Option(method.getAnnotation(classOf[play.sockjs.SockJS.Settings]))
        } yield cfg).map { cfg =>
          val cookieCalculator = cfg.cookies().newInstance() match {
            case _: play.sockjs.CookieCalculator.None => None
            case jcalculator =>
              Some(SockJSSettings.CookieCalculator { req =>
                val jcookie = jcalculator.cookie(new RequestHeaderImpl(req))
                play.api.mvc.Cookie(
                  jcookie.name(),
                  jcookie.value(),
                  if (jcookie.maxAge() == null) None else Some(jcookie.maxAge()),
                  jcookie.path(),
                  Option(jcookie.domain()),
                  jcookie.secure(),
                  jcookie.httpOnly())
              })
          }
          SockJSSettings(
            scriptSRC = req => cfg.script().newInstance().src(new RequestHeaderImpl(req)),
            cookies = cookieCalculator,
            websocket = cfg.websocket(),
            heartbeat = Duration(cfg.heartbeat(), TimeUnit.MILLISECONDS),
            sessionTimeout = Duration(cfg.sessionTimeout(), TimeUnit.MILLISECONDS),
            streamingQuota = cfg.streamingQuota(),
            sendBufferSize = cfg.sendBufferSize(),
            sessionBufferSize = cfg.sessionBufferSize())
        }
        settings.getOrElse(SockJSSettings.default)
      }

      /**
        * Execute the SockJS handler.
        *
        * The return value is either a result to reject the SockJS connection with,
        * or a flow that will handle the SockJS messages.
        *
        * The flow inlet receives the messages coming from the client, that in case
        * of SockJS protocol, are plain (unframed) strings. The flow outlet emits
        * SockJS frames.
        */
      override def apply(request: RequestHeader) = JavaSockJS.sockjsWrapper(self.sockjs)(request)
    }
  }
}