package play.sockjs.core.j

import scala.collection.JavaConverters._

import play.core.j.JavaHelpers
import play.mvc.Http.{Context => JContext}

import play.core.Execution.Implicits.internalContext

object JavaSockJS extends JavaHelpers {

  def sockjsWrapper(retrieveSockJS: => play.sockjs.SockJS): play.sockjs.api.SockJS[String] =  play.sockjs.api.SockJS[String] { request =>
    (in, out) =>

      import play.api.libs.iteratee._

      val javaSockJS = try {
        JContext.current.set(createJavaContext(request))
        retrieveSockJS
      } finally {
        JContext.current.remove()
      }

      val (enumerator, channel) = Concurrent.broadcast[String]

      val socketOut = new play.sockjs.SockJS.Out {
        def write(frame: String) = channel.push(frame)
        def close() = channel.eofAndEnd()
      }

      val socketIn = new play.sockjs.SockJS.In

      in |>> Iteratee.foreach[String](msg => socketIn.callbacks.asScala.foreach(_.invoke(msg))).map { _ =>
        socketIn.closeCallbacks.asScala.foreach(_.invoke())
      }

      enumerator |>> out

      javaSockJS.onReady(socketIn, socketOut)
  }

}