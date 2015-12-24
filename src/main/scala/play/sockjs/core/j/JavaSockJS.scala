package play.sockjs.core.j

import scala.collection.JavaConverters._
import scala.concurrent.Future

import akka.actor.Status
import akka.stream.scaladsl._
import akka.stream.OverflowStrategy

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.streams.ActorFlow
import play.core.j.JavaHelpers
import play.mvc.Http.{Context => JContext}

object JavaSockJS extends JavaHelpers {

  def sockjsWrapper(retrieveSockJS: => play.sockjs.SockJS): play.sockjs.api.SockJS =  play.sockjs.api.SockJS { request =>

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

      implicit val system = Akka.system
      implicit val mat = current.materializer

      Right(
        if (javaSockJS.isActor) {
          ActorFlow.actorRef(javaSockJS.actorProps)
        } else {

          val socketIn = new play.sockjs.SockJS.In

          val sink = Flow[String].map { msg =>
            socketIn.callbacks.asScala.foreach(_.accept(msg))
          }.to(Sink.onComplete { _ =>
            socketIn.closeCallbacks.asScala.foreach(_.run())
          })

          val source = Source.actorRef[String](256, OverflowStrategy.dropNew).mapMaterializedValue { actor =>
            val socketOut = new play.sockjs.SockJS.Out {
              def write(message: String): Unit = actor ! message
              def close(): Unit = actor ! Status.Success(())
            }

            javaSockJS.onReady(socketIn, socketOut)
          }

          play.sockjs.api.SockJS.MessageFlowTransformer.stringFrameFlowTransformer
            .transform(Flow.wrap(sink, source)(Keep.none))
        }
      )
    })
  }

}