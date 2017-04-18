package play.sockjs.core.streams

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.Done
import akka.util.ByteString
import akka.stream._
import akka.stream.scaladsl._

import play.sockjs.api.Frame

private[core] trait Session {
  def push(data: Frame): Future[QueueOfferResult]
  def source: Source[ByteString, _]
}

private[core] object SessionFlow {

  def apply(heartbeat: FiniteDuration, timeout: FiniteDuration, quota: Long,
            sendBufferSize: Int, sessionBufferSize: Int): Flow[Frame, Frame, (Session, Future[Done])] = {

    val source =
      Source.queue[Frame](sendBufferSize, OverflowStrategy.backpressure)

    val sink =
      Flow[Frame]
        .via(ProtocolFlow(heartbeat))
        .via(new FrameBufferStage(sessionBufferSize))
        .toMat(Sink.fromGraph(new SessionSubscriber(timeout, quota)))(Keep.right)

    Flow.fromSinkAndSourceMat(sink, source) { case ((binding, out), in) =>
      (new Session {
        def push(data: Frame): Future[QueueOfferResult] = in.offer(data)
        def source: Source[ByteString, _] = out
      }, binding.future.andThen {
        case Success(_) => in.complete()
        case Failure(th) => in.fail(th)
      }(play.core.Execution.trampoline))
    }
  }
}