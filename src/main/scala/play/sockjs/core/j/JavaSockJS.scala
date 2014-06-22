package play.sockjs.core.j

import akka.actor.ActorRef
import akka.actor.Props

import scala.collection.JavaConverters._
import scala.concurrent.Future

import play.core.j.JavaHelpers
import play.libs.F
import play.mvc.Http.{Context => JContext}
import play.mvc.Result
import play.mvc.Http.Request

import play.core.Execution.Implicits.internalContext
import play.api.Play.current

import play.sockjs.core.actors.SockJSActor._

object JavaSockJS extends JavaHelpers {

  def sockjsWrapper(retrieveSockJS: => play.sockjs.SockJS): play.sockjs.api.SockJS[String, String] =  play.sockjs.api.SockJS[String, String] { request =>

    val javaContext = createJavaContext(request)

    val javaSockJS = try {
      JContext.current.set(javaContext)
      retrieveSockJS
    } finally {
      JContext.current.remove()
    }

    val reject = Option(javaSockJS.rejectWith())
    Future.successful(reject.map { result =>
      Left(createResult(javaContext, result))
    }.getOrElse {
      Right((in, out) => {
        if (javaSockJS.isActor) {
          SockJSExtension(play.api.libs.concurrent.Akka.system).actor !
            SockJSActor.Connect(request.id, in, out, actorRef => javaSockJS.actorProps(actorRef))
        } else {
          import play.api.libs.iteratee._

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
      })
    })
  }

}