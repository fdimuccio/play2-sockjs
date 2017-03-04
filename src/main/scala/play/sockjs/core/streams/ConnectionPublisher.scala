package play.sockjs.core.streams

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern.ask
import akka.util.{Timeout, ByteString}

import org.reactivestreams.{Subscription, Subscriber, Publisher}

import play.sockjs.api.Frame._

private[streams] case class ConnectionPublisher(session: ActorRef) extends Publisher[ByteString] {
  import SessionSubscriber._
  private[this] implicit val timeout = Timeout(5.seconds)

  def subscribe(s: Subscriber[_ >: ByteString]) = (session ? Subscribe(s)).onComplete {

    case Success(Subscribed) =>
      s.onSubscribe(new Subscription {
        def request(n: Long): Unit = session ! Request(n)
        def cancel(): Unit = session ! Abort
      })

    case Success(AlreadySubscribed) =>
      s.onSubscribe(new Subscription {
        def request(l: Long): Unit = {
          s.onNext(Close.AnotherConnectionStillOpen.encode)
          s.onComplete()
        }
        def cancel(): Unit = ()
      })

    case Success(Closing) =>
      s.onSubscribe(new Subscription {
        def request(l: Long): Unit = {
          s.onNext(Close.GoAway.encode)
          s.onComplete()
        }
        def cancel(): Unit = ()
      })

    case _ =>
      s.onSubscribe(new Subscription {
        def request(l: Long): Unit = {
          s.onNext(Close.ConnectionInterrupted.encode)
          s.onComplete()
        }
        def cancel(): Unit = ()
      })
  }(play.core.Execution.trampoline)
}