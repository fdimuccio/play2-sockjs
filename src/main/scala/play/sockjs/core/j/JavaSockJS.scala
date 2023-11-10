package play.sockjs.core.j

import scala.compat.java8.FutureConverters

import org.apache.pekko.stream.scaladsl._

import play.api.mvc.{Handler, RequestHeader}
import play.core.j.{JavaHandler, JavaHandlerComponents, RequestHeaderImpl => JRequestHeaderImpl}

import play.sockjs.api.{Frame, SockJS}
import play.sockjs.core.SockJSHandler
import play.sockjs.{Frame => JFrame, SockJS => JSockJS}

class JavaSockJS(request: RequestHeader, handler: SockJSHandler, call: => JSockJS) extends JavaHandler {

  def withComponents(handlerComponents: JavaHandlerComponents): Handler = {
    val sockjs = SockJS { request =>
      val future = FutureConverters.toScala(call(new JRequestHeaderImpl(request)))

      future.map { resultOrFlow =>
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
