package play.sockjs.core.j

import akka.actor.ActorRef
import akka.actor.Props

import scala.collection.JavaConverters._

import play.api.Play.current

import play.core.j.JavaHelpers
import play.mvc.Http.{Context => JContext}

import play.core.Execution.Implicits.internalContext
import play.libs.F
import play.mvc.Result
import play.mvc.Http.Request

import scala.concurrent.Future


object JavaSockJS extends JavaHelpers {

  def sockjsWrapper(retrieveSockJS: => play.sockjs.SockJS): play.sockjs.api.SockJS[String, String] =  play.sockjs.api.SockJS[String, String] { request =>
    Future.successful(Right((in, out) => {

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
    }))
  }
  
  def propsWrapper(f: F.Function[Request, F.Either[Result, F.Function[ActorRef, Props]]]): play.sockjs.api.SockJS[String, String] = 
    play.sockjs.api.SockJS.tryAcceptWithActor[String, String] { request => 
      Future {
        try {
          val context = createJavaContext(request)
          JContext.current.set(context)
          val resultOrProps = f(context.request())
          resultOrProps.left match {
            case _: F.Some[Result] => Left(createResult(context, resultOrProps.left.get()))
            case _ => Right(out => resultOrProps.right.get().apply(out))
          }
        } finally {
          JContext.current.remove()
        }        
      }
    }

}