package play.sockjs.core.streams

import scala.collection.immutable.Seq
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.Done
import akka.util.ByteString
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._

import play.sockjs.api.Frame

private[core] trait Session {
  def push(data: Frame): Future[QueueOfferResult]
  def source: Source[ByteString, _]
}

private[core] object SessionFlow {

  def apply(heartbeat: FiniteDuration, timeout: FiniteDuration, quota: Long,
            sendBufferSize: Int, sessionBufferSize: Int): Flow[Frame, Frame, (Session, Future[Done])] = {

    //TODO: Remove the completion stage when Source.queue will support completion
    val source =
      Source.queue[AnyRef](sendBufferSize, OverflowStrategy.backpressure)
        .via(new GraphStage[FlowShape[AnyRef, Frame]] {
          private[this] val in = Inlet[AnyRef]("SourceTerminationStage.in")
          private[this] val out = Outlet[Frame]("SourceTerminationStage.out")
          def shape: FlowShape[AnyRef, Frame] = FlowShape(in, out)

          def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
            setHandler(in, new InHandler {
              def onPush(): Unit = grab(in) match {
                case el: Frame => push(out, el.asInstanceOf[Frame])
                case t: Try[_] => t match {
                  case Success(_) => complete(out)
                  case Failure(th) => fail(out, th)
                }
              }
            })
            setHandler(out, new OutHandler {
              def onPull(): Unit = pull(in)
            })
          }
        })

    val sink =
      Flow[Frame]
        .via(ProtocolFlow(heartbeat))
        .via(new FrameBufferStage(sessionBufferSize))
        .toMat(Sink.fromGraph(new SessionSubscriber(timeout, quota)))(Keep.right)

    Flow.fromSinkAndSourceMat(sink, source) { case ((binding, subscriber), publisher) =>
      (new Session {
        def push(data: Frame): Future[QueueOfferResult] = publisher.offer(data)
        def source: Source[ByteString, _] = subscriber
      }, binding.future.andThen {
        case r => publisher.offer(r)
      }(play.api.libs.iteratee.Execution.trampoline))
    }
  }
}