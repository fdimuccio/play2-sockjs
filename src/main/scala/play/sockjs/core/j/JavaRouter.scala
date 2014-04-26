package play.sockjs.core.j

import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._
import scala.concurrent.duration._

import play.core.Router
import play.mvc.Http.{Context => JContext}
import play.core.j.JavaHelpers

import play.core.Execution.Implicits.internalContext

abstract class JavaRouter extends Router.Routes {
  self =>

  def prefix = scalaRouter.prefix

  def setPrefix(prefix: String) = scalaRouter.setPrefix(prefix)

  def documentation = scalaRouter.documentation

  def routes = scalaRouter.routes

  def sockjs[A]: play.sockjs.SockJS[A]

  private lazy val scalaRouter = new play.sockjs.api.SockJSRouter {
    override lazy val server = {
      val settings = for {
        method <- Option(self.getClass.getMethod("sockjs"))
        cfg <- Option(method.getAnnotation(classOf[play.sockjs.SockJS.Settings]))
      } yield play.sockjs.api.SockJSSettings(
        websocket = cfg.websocket(),
        heartbeat = Duration(cfg.heartbeat(), TimeUnit.MILLISECONDS),
        sessionTimeout = Duration(cfg.sessionTimeout(), TimeUnit.MILLISECONDS),
        streamingQuota = cfg.streamingQuota())
      play.sockjs.api.SockJSServer(settings.getOrElse(play.sockjs.api.SockJSSettings.default))
    }
    def sockjs = JavaSockJS.invoke(self.sockjs)
  }

}

object JavaSockJS extends JavaHelpers {

  trait Invoker[T] {
    def call(sockjs: => play.sockjs.SockJS[T]): play.sockjs.api.SockJS[T]
  }

  object Invoker {

    implicit def javaStringSockJS: JavaSockJS.Invoker[String] = new Invoker[String] {
      def call(sockjs: => play.sockjs.SockJS[String]) = JavaSockJS.sockjsWrapper[String](sockjs)
    }

  }

  def invoke[A](sockjs: play.sockjs.SockJS[A])(implicit invoker: JavaSockJS.Invoker[A]): play.sockjs.api.SockJS[A] = invoker.call(sockjs)

  def sockjsWrapper[A](retrieveSockJS: => play.sockjs.SockJS[A])(implicit formatter: play.sockjs.api.SockJS.MessageFormatter[A]): play.sockjs.api.SockJS[A] =  play.sockjs.api.SockJS[A] { request =>
    (in, out) =>

      import play.api.libs.iteratee._

      val javaSockJS = try {
        JContext.current.set(createJavaContext(request))
        retrieveSockJS
      } finally {
        JContext.current.remove()
      }

      val (enumerator, channel) = Concurrent.broadcast[A]

      val socketOut = new play.sockjs.SockJS.Out[A] {
        def write(frame: A) = channel.push(frame)
        def close() = channel.eofAndEnd()
      }

      val socketIn = new play.sockjs.SockJS.In[A]

      in |>> Iteratee.foreach[A](msg => socketIn.callbacks.asScala.foreach(_.invoke(msg))).map { _ =>
        socketIn.closeCallbacks.asScala.foreach(_.invoke())
      }

      enumerator |>> out

      javaSockJS.onReady(socketIn, socketOut)
  }

}