package play.sockjs.core.streams

import scala.concurrent.Promise
import scala.concurrent.duration._

import akka.actor._
import akka.stream.actor._
import akka.stream.scaladsl.{Source, Sink}

import play.sockjs.api.Frame
import play.sockjs.core.FrameBuffer

private[streams] object SessionSubscriber {

  def apply(maxBufferSize: Int, timeout: FiniteDuration, quota: Long, binding: Promise[Unit]): Sink[Frame, Source[Frame, _]] = {
    Sink.actorSubscriber[Frame](Props(new SessionSubscriber(maxBufferSize, timeout, quota, binding)))
      .mapMaterializedValue(ref => ConnectionPublisher(ref))
  }

  case class Connect(actorRef: ActorRef)
  case object Connected
  case class CantConnect(frame: Frame.CloseFrame)
  case class AbortConnection(actorRef: ActorRef)
  case class RequestNext(n: Long)
}

private[streams] class SessionSubscriber(maxBufferSize: Int, timeout: FiniteDuration, quota: Long, binding: Promise[Unit]) extends ActorSubscriber {
  import SessionSubscriber._

  private[this] val buffer = new FrameBuffer
  private[this] var eof = false

  protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy(maxBufferSize) {
    def inFlightInternally: Int = buffer.size
  }

  context.setReceiveTimeout(timeout)

  def receive = disconnected

  private def disconnected: Receive = {

    // -- from upstream

    case ActorSubscriberMessage.OnNext(frame: Frame) =>
      buffer.enqueue(frame)

    case ActorSubscriberMessage.OnComplete =>
      if (buffer.isEmpty) {
        context.setReceiveTimeout(timeout)
        context.become(terminating)
      } else eof = true

    case ActorSubscriberMessage.OnError(err) =>
      binding.failure(err)
      context.stop(self)

    // -- from downstream

    case Connect(ref) =>
      ref ! Connected
      context.watch(ref)
      context.setReceiveTimeout(Duration.Undefined)
      context.become(connected(ref, 0, quota))

    case ReceiveTimeout =>
      cancel()
  }

  private def connected(connection: ActorRef, demand: Long, quota: Long): Receive = {

    // -- from upstream

    case ActorSubscriberMessage.OnNext(frame: Frame) =>
      if (buffer.isEmpty && demand > 0) {
        connection ! frame
        val remaining = quota - frame.encode.size
        if (remaining < 1) {
          connection ! Status.Success(())
          context.become(disconnecting(connection))
        } else context.become(connected(connection, demand - 1, remaining))
      } else buffer.enqueue(frame)

    case ActorSubscriberMessage.OnComplete =>
      if (buffer.isEmpty) {
        connection ! Status.Success(())
        context.become(terminating)
      } else eof = true

    case ActorSubscriberMessage.OnError(err) =>
      connection ! Status.Failure(err)
      binding.failure(err)
      context.stop(self)

    // -- from downstream

    case Connect(ref) =>
      ref ! CantConnect(Frame.CloseFrame.AnotherConnectionStillOpen)

    case RequestNext(n) =>
      var requested = demand + n
      var remaining = quota
      while(requested > 0 && remaining > 0 && buffer.nonEmpty) {
        val frame = buffer.dequeue()
        connection ! frame
        remaining -= frame.encode.size
        requested -= 1
      }
      if (buffer.isEmpty && eof) {
        connection ! Status.Success(())
        context.become(terminating)
      } else if (remaining < 1) {
        connection ! Status.Success(())
        context.become(disconnecting(connection))
      } else {
        context.become(connected(connection, requested, remaining))
      }

    case AbortConnection(`connection`) =>
      context.unwatch(connection)
      cancel()

    case Terminated(`connection`) =>
      cancel()
  }

  private def disconnecting(connection: ActorRef): Receive = {

    // -- from upstream

    case ActorSubscriberMessage.OnNext(frame: Frame) =>
      buffer.enqueue(frame)

    case ActorSubscriberMessage.OnComplete =>
      if (buffer.isEmpty) context.become(terminating)
      else eof = true

    case ActorSubscriberMessage.OnError(err) =>
      binding.failure(err)
      context.stop(self)

    // -- from downstream

    case Connect(ref) =>
      ref ! CantConnect(Frame.CloseFrame.AnotherConnectionStillOpen)

    case Terminated(`connection`) =>
      context.setReceiveTimeout(timeout)
      context.become(disconnected)
  }

  private def terminating: Receive = {

    // -- from downstream

    case Connect(ref) =>
      ref ! CantConnect(Frame.CloseFrame.GoAway)

    case Terminated(_) =>
      context.setReceiveTimeout(timeout)

    case ReceiveTimeout =>
      context.stop(self)
  }

  override def postStop(): Unit = {
    binding.trySuccess(())
  }
}
