package protocol.routers

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import play.sockjs.api.libs.streams.ActorFlow

object Flows {

  def echo[T]: Flow[T, T, Any] = Flow[T]

  def closed[T]: Flow[T, T, Any] = Flow.fromSinkAndSource(Sink.ignore, Source.empty[T])
}

object ActorFlows {

  def echo[T](implicit as: ActorSystem): Flow[T, T, Any] = ActorFlow.actorRef(out => Props(new Actor {
    override def receive = {
      case msg => out ! msg
    }
  }))

  def closed[T](implicit as: ActorSystem): Flow[T, T, Any] = ActorFlow.actorRef(_ => Props(new Actor {
    context.stop(self)
    override def receive = {
      case msg =>
    }
  }))
}
