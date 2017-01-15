package play.sockjs.core.streams

import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._

import play.sockjs.api.Frame
import play.sockjs.api.Frame._

import scala.collection.mutable

private[sockjs] class FrameBufferStage(maxBufferSize: Int) extends GraphStage[FlowShape[Frame, Frame]] {
  val in = Inlet[Frame]("FrameBuffer.in")
  val out = Outlet[Frame]("FrameBuffer.out")
  def shape: FlowShape[Frame, Frame] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    private[this] val queue = mutable.Queue[Frame]()
    private[this] var tail: Frame = _
    private[this] var size: Int = 0

    private[this] var isHoldingDownstream = false

    override def preStart(): Unit = pull(in)

    setHandler(in, new InHandler {
      def onPush(): Unit = {
        val frame = grab(in)
        if (isHoldingDownstream) {
          push(out, frame)
          pull(in)
          isHoldingDownstream = false
        } else {
          (tail, frame) match {
            case (Text(d1), Text(d2)) => tail = Text(d1 ++ d2)
            case (_, Heartbeat) => // drop
            case (null, f) => tail = f
            case (f1, f2) => queue.enqueue(f1); tail = f2
          }
          size += weight(frame)
          drain()
        }
      }

      override def onUpstreamFinish(): Unit = {
        if (size < 1) completeStage()
      }
    })

    setHandler(out, new OutHandler {
      def onPull(): Unit = {
        if (queue.nonEmpty) dequeue(queue.dequeue())
        else if (tail != null) {dequeue(tail); tail = null}
        else isHoldingDownstream = true
      }
    })

    private def dequeue(frame: Frame): Unit = {
      push(out, frame)
      size -= weight(frame)
      if (isClosed(in)) {
        if (size < 1) completeStage()
      } else drain()
    }

    private def drain(): Unit = {
      if (size < maxBufferSize && !hasBeenPulled(in))
        pull(in)
    }

    private def weight(frame: Frame) = frame match {
      case Text(data) => data.map(_.length).sum * java.lang.Byte.BYTES
      case Heartbeat => 0
      case _ => 1
    }
  }
}
