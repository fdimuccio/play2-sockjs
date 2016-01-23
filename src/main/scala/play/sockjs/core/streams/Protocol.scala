package play.sockjs.core.streams

import akka.stream.scaladsl._
import akka.stream.stage._

import play.sockjs.api.Frame
import play.sockjs.api.Frame.{CloseFrame, CloseAbruptly}

import scala.concurrent.duration.FiniteDuration

private[core] object Protocol {

  def apply[T](heartbeat: FiniteDuration): Flow[Frame, Frame, _] = {
    Flow[Frame].transform(() => new DetachedStage[Frame, Frame] {
      var buffer = Vector[Frame](Frame.OpenFrame)
      var closed = false

      def onPush(frame: Frame, ctx: DetachedContext[Frame]) = {
        if (buffer.nonEmpty || !ctx.isHoldingDownstream) {
          buffer :+= frame
          ctx.holdUpstream()
        } else frame match {
          case CloseAbruptly =>
            ctx.finish()
          case close: CloseFrame =>
            closed = true
            ctx.holdUpstreamAndPush(close)
          case other =>
            ctx.pushAndPull(other)
        }
      }

      def onPull(ctx: DetachedContext[Frame]) = {
        if (closed) ctx.finish()
        else if (buffer.nonEmpty) {
          val (frame, rest) = buffer.splitAt(1)
          buffer = rest
          frame(0) match {
            case CloseAbruptly =>
              ctx.finish()
            case close: CloseFrame =>
              ctx.pushAndFinish(close)
            case other =>
              if (ctx.isHoldingUpstream) ctx.pushAndPull(other)
              else ctx.push(other)
          }
        } else ctx.holdDownstream()
      }

      override def onUpstreamFinish(ctx: DetachedContext[Frame]) = {
        buffer :+= Frame.CloseFrame.GoAway
        ctx.absorbTermination()
      }
    }).keepAlive(heartbeat, () => Frame.HeartbeatFrame)
  }
}
