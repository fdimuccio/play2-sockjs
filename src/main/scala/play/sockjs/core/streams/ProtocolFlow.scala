package play.sockjs.core.streams

import akka.stream.{Outlet, Inlet, Attributes, FlowShape}
import akka.stream.stage._
import akka.stream.scaladsl._

import play.sockjs.api.Frame
import play.sockjs.api.Frame._

import scala.concurrent.duration.FiniteDuration

private[core] object ProtocolFlow {

  def apply[T](heartbeat: FiniteDuration): Flow[Frame, Frame, _] = {
    Flow[Frame].via(new GraphStage[FlowShape[Frame, Frame]] {
      val in = Inlet[Frame]("ProtocolFlow.in")
      val out = Outlet[Frame]("ProtocolFlow.out")
      val shape = FlowShape(in, out)

      def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {

        setHandler(in, new InHandler {
          def onPush() = {
            grab(in) match {
              case CloseAbruptly =>
                complete(out)
              case close: CloseFrame =>
                push(out, close)
                complete(out)
              case other =>
                push(out, other)
            }
          }

          override def onUpstreamFinish(): Unit = {
            emit(out, CloseFrame.GoAway)
            complete(out)
          }
        })

        setHandler(out, new OutHandler {
          def onPull() = {
            push(out, OpenFrame)
            setHandler(out, new OutHandler {
              def onPull(): Unit = pull(in)
            })
          }
        })
      }
    }).keepAlive(heartbeat, () => HeartbeatFrame)
  }
}
