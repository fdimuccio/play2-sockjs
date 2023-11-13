package play.sockjs.api.libs.streams

import org.apache.pekko.actor._
import org.apache.pekko.stream._
import org.apache.pekko.stream.stage._
import org.apache.pekko.stream.scaladsl._

/**
  * Provides a flow that is handled by an actor.
  *
  * Alternative implementation of [[play.api.libs.streams.ActorFlow]] backed up
  * by a graph stage actor
  */
object ActorFlow {

  /**
    * Create a flow that is handled by an actor.
    *
    * Messages can be sent downstream by sending them to the actor passed into the props function.  This actor meets
    * the contract of the actor returned by [[org.apache.pekko.stream.scaladsl.Source.actorRef]].
    *
    * The props function should return the props for an actor to handle the flow. This actor will be created using the
    * passed in [[org.apache.pekko.actor.ActorRefFactory]]. Each message received will be sent to the actor - there is no back pressure,
    * if the actor is unable to process the messages, they will queue up in the actors mailbox. The upstream can be
    * cancelled by the actor terminating itself.
    *
    * @param props A function that creates the props for actor to handle the flow.
    * @param bufferSize The maximum number of elements to buffer.
    * @param overflowStrategy The strategy for how to handle a buffer overflow.
    */
  def actorRef[In, Out](props: ActorRef => Props, bufferSize: Int = 16, overflowStrategy: OverflowStrategy = OverflowStrategy.dropNew)(implicit factory: ActorRefFactory): Flow[In, Out, _] = {
    require(overflowStrategy != OverflowStrategy.backpressure, "Backpressure overflowStrategy not supported")

    val stage = new GraphStage[FlowShape[In, Out]] {
      val in = Inlet[In]("ActorFlow.in")
      val out = Outlet[Out]("ActorFlow.out")
      val shape = FlowShape(in, out)

      def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
        private[this] var flowRef: ActorRef = _

        override def preStart(): Unit = {
          val stageActor = getStageActor({
            case (_, Terminated(_)) => completeStage()
            // TODO: use a class tag to type check element?
            case (_, element) =>
              // It's safe to push without further checks because we are
              // protected by the buffer (that doesn't backpressure)
              push(out, element.asInstanceOf[Out])
          })
          flowRef = factory.actorOf(props(stageActor.ref))
          stageActor.watch(flowRef)
          pull(in)
        }

        setHandler(in, new InHandler {
          def onPush(): Unit = {
            flowRef ! grab(in)
            pull(in)
          }

          override def onUpstreamFinish(): Unit = flowRef ! PoisonPill
        })

        setHandler(out, new OutHandler {
          // On downstream demand we must not pull here because our upstream
          // is the flowRef
          def onPull(): Unit = ()
        })

        override def postStop(): Unit = flowRef ! PoisonPill
      }
    }

    Flow[In].via(stage).buffer(bufferSize, overflowStrategy)
  }
}
