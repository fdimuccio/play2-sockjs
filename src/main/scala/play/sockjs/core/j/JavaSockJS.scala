package play.sockjs.core.j

import scala.collection.JavaConverters._
import scala.concurrent.Future
import akka.stream.scaladsl._
import akka.stream.OverflowStrategy
import play.api.mvc.RequestHeader
import play.core.j.{JavaHandler, JavaHandlerComponents, JavaHelpers, RequestHeaderImpl => JRequestHeaderImpl}
import play.mvc.Http
import play.mvc.Http.{Context => JContext}
import play.sockjs.api.{Frame, SockJS}
import play.sockjs.api.Frame._
import play.sockjs.api.SockJS._
import play.sockjs.api.libs.streams.ActorFlow
import play.sockjs.core.SockJSHandler
import play.sockjs.{Frame => JFrame, SockJS => JSockJS}

import scala.compat.java8.FutureConverters

object JavaSockJS {

  def run(request: RequestHeader, handler: SockJSHandler, call: => JSockJS) = new JavaHandler {
    def withComponents(handlerComponents: JavaHandlerComponents) = {
      val sockjs = SockJS { request =>
        val javaContext = JavaHelpers.createJavaContext(request, handlerComponents.contextComponents)

        val callWithContext = try {
          JContext.current.set(javaContext)
          FutureConverters.toScala(call(new JRequestHeaderImpl(request)))
        } finally {
          JContext.current.remove()
        }

        callWithContext.map { resultOrFlow =>
          if (resultOrFlow.left.isPresent) {
            Left(resultOrFlow.left.get.asScala())
          } else {
            Right(Flow[Frame].mapConcat[JFrame] {
              case Frame.Text(texts) => texts.map(new JFrame.Text(_))
              case Frame.Close(code, reason) => List(new JFrame.Close(code, reason))
              case _ => List.empty
            }.via(resultOrFlow.right.get.asScala).map {
              case text: JFrame.Text => Frame.Text(text.data())
              case close: JFrame.Close => Frame.Close(close.code(), close.reason())
            })
          }
        }(play.core.Execution.trampoline)
      }
      handler.f(request, sockjs)
    }
  }
}