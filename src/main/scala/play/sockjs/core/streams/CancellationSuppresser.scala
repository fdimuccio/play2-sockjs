package play.sockjs.core.streams

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

/**
  * This stage suppress the Cancel signal emitted by the wrapped flow and
  * emits an onComplete signal downstream. If the Cancel signals comes from
  * downstream then it is propagated upstream.
  *
  * Note: this is mainly needed for WebSocket transports which completion
  * must be controlled. This stage could potentially keep upstream from completing,
  * use this only when upstream and downstream are part of a joined flow.
  *
  * @param flow the inner flow which cancel should be suppressed
  * @tparam In
  * @tparam Out
  */
private[sockjs] class CancellationSuppresser[In, Out](flow: Flow[In, Out, _])
  extends GraphStage[FlowShape[In, Out]] {
  val in  = Inlet[In]("CancellationSuppresser.in")
  val out = Outlet[Out]("CancellationSuppresser.out")
  val shape = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes) = {
    new GraphStageLogic(shape) with InHandler with OutHandler {
      val subSource: SubSourceOutlet[In] = new SubSourceOutlet[In]("CancellationSuppresserSubSource")
      val subSink  : SubSinkInlet[Out]   = new SubSinkInlet[Out]("CancellationSuppresserSubSink")
      var downstreamCancelled: Boolean = false

      subSource.setHandler(new OutHandler {
        def onPull(): Unit = pull(in)
        override def onDownstreamFinish(cause: Throwable): Unit = {
          // cancel must be propagated upstream only if downstream cancels
          if (downstreamCancelled) cancel(in, cause)
          // otherwise swallow it
          else if (!isClosed(out)) complete(out)
        }
      })

      subSink.setHandler(new InHandler {
        def onPush(): Unit = push(out, subSink.grab())
        override def onUpstreamFinish(): Unit = complete(out)
        override def onUpstreamFailure(ex: Throwable): Unit = failStage(ex)
      })

      // -- InHandler
      def onPush(): Unit = subSource.push(grab(in))
      override def onUpstreamFinish(): Unit = subSource.complete()
      override def onUpstreamFailure(ex: Throwable): Unit = subSource.fail(ex)
      // --

      // -- OutHandler
      def onPull(): Unit = subSink.pull()
      override def onDownstreamFinish(cause: Throwable): Unit = {
        downstreamCancelled = true
        subSink.cancel(cause)
      }
      // --

      override def preStart(): Unit = {
        Source.fromGraph(subSource.source)
          .via(flow)
          .runWith(subSink.sink)(subFusingMaterializer)
      }

      setHandlers(in, out, this)
    }
  }
}
