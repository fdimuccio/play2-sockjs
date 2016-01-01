package play.sockjs.core.streams

import akka.actor._
import akka.stream.actor._
import akka.stream.scaladsl.Source

import play.sockjs.api.Frame

private[streams] object ConnectionPublisher {

  def apply(subscriber: ActorRef): Source[Frame, _] = {
    Source.actorPublisher[Frame](Props(new ConnectionPublisher(subscriber)))
  }
}

private[streams] class ConnectionPublisher(subscriber: ActorRef) extends ActorPublisher[Frame] {

  context.watch(subscriber)

  def receive = {

    case ActorPublisherMessage.Request(_) =>
      subscriber ! SessionSubscriber.Connect(self)

    case SessionSubscriber.Connected =>
      context.become(connected)
      subscriber ! SessionSubscriber.RequestNext(totalDemand)

    case SessionSubscriber.CantConnect(frame) =>
      onNext(frame)
      onCompleteThenStop()

    case Terminated(`subscriber`) =>
      onCompleteThenStop() //TODO: onErrorThenStop(???)
  }

  def connected: Receive = {

    case frame: Frame =>
      onNext(frame)

    case ActorPublisherMessage.Request(n) =>
      subscriber ! SessionSubscriber.RequestNext(n)

    case ActorPublisherMessage.Cancel =>
      subscriber ! SessionSubscriber.AbortConnection(self)
      context.stop(self)

    case Terminated(`subscriber`) =>
      onCompleteThenStop() //TODO: onErrorThenStop(???)

    case Status.Success(_) =>
      onCompleteThenStop()

    case Status.Failure(ex) =>
      onErrorThenStop(ex)
  }
}
