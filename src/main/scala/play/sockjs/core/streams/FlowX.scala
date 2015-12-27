package play.sockjs.core.streams

import akka.stream.scaladsl._
import akka.stream.stage._

private[core] object FlowX {

  def onUpstreamFinish[T](callback: () => Unit): Flow[T, T, _] = {
    Flow[T].transform(() => new PushStage[T, T] {
      def onPush(elem: T, ctx: Context[T]) = ctx.push(elem)
      override def onUpstreamFinish(ctx: Context[T]): TerminationDirective = {
        callback()
        super.onUpstreamFinish(ctx)
      }
    })
  }

  def onPostStop[T](callback: () => Unit): Flow[T, T, _] = {
    Flow[T].transform(() => new PushStage[T, T] {
      def onPush(elem: T, ctx: Context[T]) = ctx.push(elem)
      override def postStop(): Unit = {
        callback()
      }
    })
  }
}
