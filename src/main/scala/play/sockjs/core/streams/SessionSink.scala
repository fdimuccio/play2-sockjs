package play.sockjs.core.streams

import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.{Done, NotUsed}
import akka.actor._
import akka.stream.{Inlet, Attributes, SinkShape}
import akka.stream.scaladsl.Source
import akka.stream.stage.{TimerGraphStageLogic, InHandler, GraphStageLogic, GraphStageWithMaterializedValue}
import akka.util.ByteString

import org.reactivestreams.{Publisher, Subscriber}

import play.sockjs.api.Frame

private[streams] object SessionSink {

  // Sent by ConnectionPublisher to subscribe
  case class Subscribe(subscriber: Subscriber[_ >: ByteString])
  // Sent to ConnectionPublisher to notify a successful subscription
  case object SubscriptionSuccessful
  // Sent to ConnectionPublisher to notify a failed subscription
  case class SubscriptionFailed(frame: Frame.Close)

  // Sent by ConnectionPublisher to request elements
  case class Request(n: Long)
  // Sent by ConnectionPublisher to cancel subscription
  case object Abort

  private val SessionTimeoutTimer = "SessionTimeoutTimer"
}

private[streams] class SessionSink(timeout: FiniteDuration, quota: Long)
  extends GraphStageWithMaterializedValue[SinkShape[Frame], (Promise[Done], Source[ByteString, NotUsed])] {
  val in = Inlet[Frame]("SessionSubscriber.in")
  def shape: SinkShape[Frame] = SinkShape(in)

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
    val publisher = Promise[Publisher[ByteString]]()
    val binding = Promise[Done]()

    val logic: GraphStageLogic = new TimerGraphStageLogic(shape) {
      import SessionSink._
      private[this] var subscriber: Subscriber[ByteString] = _
      private[this] var demand = 0L
      private[this] var remaining = quota

      override def preStart(): Unit = {
        // This is needed in order to unlink the request
        setKeepGoing(true)
        val stageActor = getStageActor {

          case (sender, Subscribe(_)) if isClosed(in) =>
            sender ! SubscriptionFailed(Frame.Close.GoAway)

          case (sender, Subscribe(connection)) =>
            if (subscriber == null) {
              cancelTimer(SessionTimeoutTimer)
              remaining = quota
              demand = 0
              subscriber = connection.asInstanceOf[Subscriber[ByteString]]
              sender ! SubscriptionSuccessful
            } else {
              sender ! SubscriptionFailed(Frame.Close.AnotherConnectionStillOpen)
            }

          case (_, Request(n)) =>
            demand += n
            if (isAvailable(in)) send()
            else if (!hasBeenPulled(in)) pull(in)

          case (_, Abort) =>
            cancel(in)
            completeStage()
        }
        publisher.success(ConnectionPublisher(stageActor.ref))
        scheduleOnce(SessionTimeoutTimer, timeout)
      }

      setHandler(in, new InHandler {
        def onPush(): Unit = {
          if (subscriber != null)
            send()
        }

        override def onUpstreamFinish(): Unit = {
          if (subscriber != null)
            subscriber.onComplete()
          scheduleOnce(SessionTimeoutTimer, timeout)
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          if (subscriber != null)
            subscriber.onComplete()
          binding.failure(ex)
          failStage(ex)
        }
      })

      private def send(): Unit = {
        val encoded = grab(in).encode
        subscriber.onNext(encoded)
        demand -= 1
        remaining -= encoded.size
        if (remaining < 1) {
          subscriber.onComplete()
          subscriber = null
          scheduleOnce(SessionTimeoutTimer, timeout)
        } else if (demand > 0) pull(in)
      }

      override protected def onTimer(timerKey: Any) = completeStage()

      override def postStop() = binding.trySuccess(Done)
    }

    (logic, (binding, Source.fromFuture(publisher.future).flatMapConcat(Source.fromPublisher)))
  }
}
