package play.sockjs.core.j

import akka.actor.ActorRef
import akka.actor.Props

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

import play.libs.F
import play.core.Router
import play.core.j.JavaHelpers
import play.mvc.Result
import play.mvc.Http.Request

abstract class JavaRouter(cfg: F.Option[play.sockjs.SockJS.Settings]) extends Router.Routes {
  self =>

  def prefix = scalaRouter.prefix

  def setPrefix(prefix: String) = scalaRouter.setPrefix(prefix)

  def documentation = scalaRouter.documentation

  def routes = scalaRouter.routes

  def sockjs: play.sockjs.SockJS

  private lazy val scalaRouter = new play.sockjs.api.SockJSRouter {
    import play.sockjs.api._
    override lazy val server = {
      val settings = Option(cfg.getOrElse(null)).orElse(for {
        method <- Option(self.getClass.getMethod("sockjs"))
        cfg <- Option(method.getAnnotation(classOf[play.sockjs.SockJS.Settings]))
      } yield cfg).map { cfg =>
        val cookieCalculator = cfg.cookies().newInstance() match {
          case _: play.sockjs.CookieCalculator.None => None
          case jcalculator =>
            Some(SockJSSettings.CookieCalculator { req =>
              val jcookie = jcalculator.cookie(JavaHelpers.createJavaRequest(req))
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
          scriptSRC = req => cfg.script().newInstance().src(JavaHelpers.createJavaRequest(req)),
          cookies = cookieCalculator,
          websocket = cfg.websocket(),
          heartbeat = Duration(cfg.heartbeat(), TimeUnit.MILLISECONDS),
          sessionTimeout = Duration(cfg.sessionTimeout(), TimeUnit.MILLISECONDS),
          streamingQuota = cfg.streamingQuota())
      }
      SockJSServer(settings.getOrElse(SockJSSettings.default))
    }
    def sockjs = JavaSockJS.sockjsWrapper(self.sockjs)
  }
}