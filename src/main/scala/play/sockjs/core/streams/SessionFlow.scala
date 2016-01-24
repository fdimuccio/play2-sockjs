package play.sockjs.core.streams

import scala.collection.immutable.Seq
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.stream.OverflowStrategy
import akka.stream.stage.{Context, PushStage}
import akka.stream.scaladsl._

import play.sockjs.api.Frame

private[core] trait Session {

  def push(data: Seq[String]): Future[Boolean]

  def source: Source[Frame, _]
}

private[core] object SessionFlow {

  def apply(
      heartbeat: FiniteDuration,
      timeout: FiniteDuration,
      quota: Long,
      sendBufferSize: Int,
      sessionBufferSize: Int): Flow[Frame, String, (Session, Future[Unit])] = {

    val binding = Promise[Unit]()

    //TODO: Remove the completion stage when Source.queue will support completion
    val source =
      Source.queue[AnyRef](sendBufferSize, OverflowStrategy.backpressure, 30.seconds)
        .transform(() => new PushStage[AnyRef, Seq[String]] {
          def onPush(elem: AnyRef, ctx: Context[Seq[String]]) = elem match {
            case t: Try[_] => t match {
              case Success(_) => ctx.finish()
              case Failure(th) => ctx.fail(th)
            }
            case el: Seq[_] => ctx.push(el.asInstanceOf[Seq[String]])
          }
        })
        .mapConcat[String](identity)

    val sink =
      Flow[Frame]
        .via(ProtocolFlow(heartbeat))
        .toMat(SessionSubscriber(sessionBufferSize, timeout, quota, binding))(Keep.right)

    Flow.fromSinkAndSourceMat(sink, source) { (subscriber, publisher) =>
      (new Session {
        def push(data: Seq[String]): Future[Boolean] = publisher.offer(data)
        def source: Source[Frame, _] = subscriber
      }, binding.future.andThen {
        case r => publisher.offer(r)
      }(play.api.libs.iteratee.Execution.trampoline))
    }
  }
}