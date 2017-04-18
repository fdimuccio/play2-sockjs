package play.sockjs.core.j

import scala.compat.java8.FutureConverters

import akka.stream.scaladsl._

import play.api.mvc.{Handler, RequestHeader}
import play.core.j.{JavaHandler, JavaHandlerComponents, JavaHelpers, RequestHeaderImpl => JRequestHeaderImpl}
import play.mvc.Http.{Context => JContext}

import play.sockjs.api.{Frame, SockJS}
import play.sockjs.core.SockJSHandler
import play.sockjs.{Frame => JFrame, SockJS => JSockJS}

class JavaSockJS(request: RequestHeader, handler: SockJSHandler, call: => JSockJS) extends JavaHandler {

  def withComponents(handlerComponents: JavaHandlerComponents): Handler = {
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