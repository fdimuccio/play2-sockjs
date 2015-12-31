package play.sockjs.core.streams

import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import play.sockjs.api.Frame

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

private[core] trait Session {

  def push(data: Seq[String]): Future[Boolean]

  def source: Source[Frame, _]
}

private[core] object Session {

  def flow(
      heartbeat: FiniteDuration,
      timeout: FiniteDuration,
      quota: Long,
      inputBufferSize: Int,
      outputBufferSize: Int): Flow[Frame, String, (Session, Future[Unit])] = {

    val binding = Promise[Unit]()

    //TODO: change to queue when akka-streams-2.0 is available
    val source =
      Source.actorRef[Seq[String]](inputBufferSize, OverflowStrategy.fail)
        .mapConcat[String](identity)

    val sink =
      Flow[Frame]
        .via(Protocol(heartbeat, identity))
        .toMat(SessionSubscriber(outputBufferSize, timeout, quota, binding))(Keep.right)

    Flow.wrap(sink, source) { (subscriber, publisher) =>
      (new Session {
        def push(data: Seq[String]): Future[Boolean] = {
          publisher ! data
          Future.successful(true)
        }
        def source: Source[Frame, _] = SessionPublisher(subscriber)
      }, binding.future)
    }
  }
}