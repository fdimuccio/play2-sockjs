package play.sockjs.core.streams

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern.ask
import akka.util.{Timeout, ByteString}

import org.reactivestreams.{Subscription, Subscriber, Publisher}

import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[streams] case class ConnectionPublisher(session: ActorRef) extends Publisher[ByteString] {
  import SessionSubscriber._
  import ConnectionPublisher._
  private[this] implicit val timeout: Timeout = Timeout(5.seconds)

  def subscribe(subscriber: Subscriber[_ >: ByteString]) = {
    (session ? Subscribe(subscriber)).onComplete {

      case Success(SubscriptionSuccessful) =>
        subscriber.onSubscribe(new Subscription {
          def request(n: Long): Unit = session ! Request(n)
          def cancel(): Unit = session ! Abort
        })

      case Success(SubscriptionFailed(frame)) => subscriber.close(frame)

      case _ => subscriber.close(Close.ConnectionInterrupted)

    }(play.core.Execution.trampoline)
  }
}

object ConnectionPublisher {

  implicit class SubscriberEnricher(val s: Subscriber[_ >: ByteString]) extends AnyVal {

    def close(frame: Frame): Unit = {
      s.onSubscribe(new Subscription {
        def request(l: Long): Unit = {
          s.onNext(frame.encode)
          s.onComplete()
        }
        def cancel(): Unit = ()
      })
    }
  }
}
