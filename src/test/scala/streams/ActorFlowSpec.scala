package streams

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Random

import akka.Done
import akka.actor._
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl._
import akka.testkit._

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.concurrent.ScalaFutures

import play.sockjs.api.libs.streams._

class ActorFlowSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers with ScalaFutures {

  def EchoActor(out: ActorRef, done: Promise[Done]) = Props(new Actor {
    def receive: Receive = {
      case message => out ! message
    }

    override def postStop(): Unit = done.success(Done)
  })

  // -- play2-sockjs ActorFlow tests

  "play2-sockjs ActorFlow" must {

    "terminate the supplied actor when upstream completes" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      w.sendComplete()
      done.future.futureValue mustBe Done
      r.expectSubscriptionAndComplete()
    }

    "terminate the supplied actor when upstream fails" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      w.sendError(new RuntimeException("something went wrong"))
      done.future.futureValue mustBe Done
      r.expectSubscriptionAndError()
    }

    "terminate the supplied actor when downstream cancels" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.cancel()
      done.future.futureValue mustBe Done
      w.expectCancellation()
    }

    "not drop any element in normal conditions" in {
      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]())))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      for (el <- List.fill(100)(Random.alphanumeric.take(10).mkString)) {
        w.sendNext(el)
        r.requestNext(el)
      }

      w.sendComplete()
      r.expectComplete()
    }

    "drop elements if downstream doesn't keep up with upstream" in {
      val bufferSize = 16

      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]()), bufferSize))
          .initialDelay(1.seconds)
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      val els = List.fill(bufferSize + 2)(Random.alphanumeric.take(10).mkString)

      els.foreach(w.sendNext)

      els.take(bufferSize).foreach(r.requestNext)

      w.sendComplete()
      r.expectComplete()
    }

    "let the supplied actor process enqueued elements before signaling onComplete" in {
      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]())))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      val els = List.fill(8)(Random.alphanumeric.take(10).mkString)

      els.foreach(w.sendNext)
      w.sendComplete()

      els.foreach(r.requestNext)
      r.expectComplete()
    }

    "terminate the stream if the supplied actor stops immediately" in {
      val (w, r) =
        TestSource.probe[String]
          .via(ActorFlow.actorRef[String, String](ou => Props(new Actor {
            context.stop(self)
            def receive = { case message => }
          })))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.expectSubscriptionAndComplete()
      w.expectCancellation()
    }
  }

  // -- Play ActorFlow tests

  /*
  "PlayActorFlow" must {

    "terminate the supplied actor when upstream completes" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      w.sendComplete()
      done.future.futureValue mustBe Done
      r.expectSubscriptionAndComplete()
    }

    "terminate the supplied actor when upstream fails" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      w.sendError(new RuntimeException("something went wrong"))
      done.future.futureValue mustBe Done
      r.expectSubscriptionAndError()
    }

    "terminate the supplied actor when downstream cancels" in {
      val done = Promise[Done]()

      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, done)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.cancel()
      done.future.futureValue mustBe Done
      w.expectCancellation()
    }

    "not drop any element in normal conditions" in {
      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]())))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      for (el <- List.fill(100)(Random.alphanumeric.take(10).mkString)) {
        w.sendNext(el)
        r.requestNext(el)
      }

      w.sendComplete()
      r.expectComplete()
    }

    "drop elements if downstream doesn't keep up with upstream" in {
      val bufferSize = 16

      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]()), bufferSize))
          .initialDelay(1.seconds)
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      val els = List.fill(bufferSize + 2)(Random.alphanumeric.take(10).mkString)

      els.foreach(w.sendNext)

      els.take(bufferSize).foreach(r.requestNext)

      w.sendComplete()
      r.expectComplete()
    }

    "let the supplied actor process enqueued elements before signaling onComplete" in {
      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](EchoActor(_, Promise[Done]())))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      val els = List.fill(8)(Random.alphanumeric.take(10).mkString)

      els.foreach(w.sendNext)
      w.sendComplete()

      els.foreach(r.requestNext)
      r.expectComplete()
    }

    "terminate the stream if the supplied actor stops immediately" in {
      val (w, r) =
        TestSource.probe[String]
          .via(PlayActorFlow.actorRef[String, String](ou => Props(new Actor {
            context.stop(self)
            def receive = { case message => }
          })))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.expectSubscriptionAndComplete()
      w.expectCancellation()
    }
  }
  */
}
