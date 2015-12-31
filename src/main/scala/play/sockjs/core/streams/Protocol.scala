package play.sockjs.core.streams

import akka.stream.scaladsl._
import akka.stream.stage._

import play.sockjs.api.Frame
import play.sockjs.api.Frame.{CloseFrame, CloseAbruptly}

import scala.concurrent.duration.FiniteDuration

private[core] object Protocol {

  def apply[T](heartbeat: FiniteDuration, encoder: Frame => T): Flow[Frame, T, _] = {
    Flow[Frame].transform(() => new DetachedStage[Frame, T] {
      var buffer = Vector[Frame](Frame.OpenFrame)
      var closed = false

      def onPush(frame: Frame, ctx: DetachedContext[T]) = {
        if (buffer.nonEmpty || !ctx.isHoldingDownstream) {
          buffer :+= frame
          ctx.holdUpstream()
        } else frame match {
          case CloseAbruptly =>
            ctx.finish()
          case close: CloseFrame =>
            closed = true
            ctx.holdUpstreamAndPush(encoder(close))
          case other =>
            ctx.pushAndPull(encoder(other))
        }
      }

      def onPull(ctx: DetachedContext[T]) = {
        if (closed) ctx.finish()
        else if (buffer.nonEmpty) {
          val (frame, rest) = buffer.splitAt(1)
          buffer = rest
          frame(0) match {
            case CloseAbruptly =>
              ctx.finish()
            case close: CloseFrame =>
              ctx.pushAndFinish(encoder(close))
            case other =>
              if (ctx.isHoldingUpstream) ctx.pushAndPull(encoder(other))
              else ctx.push(encoder(other))
          }
        } else ctx.holdDownstream()
      }

      override def onUpstreamFinish(ctx: DetachedContext[T]) = {
        buffer :+= Frame.CloseFrame.GoAway
        ctx.absorbTermination()
      }
    })//TODO:.via(Flow.keepAlive(heartbeat))
  }
}
