package play.sockjs.streams

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import play.sockjs.core.streams.CancellationSuppresser

class CancellationSuppressorSpec extends TestKit(ActorSystem()) with AnyWordSpecLike with Matchers with ScalaFutures {

  "CancellationSuppressor" must {

    "not change the flow behavior under normal conditions" in {
      val (w, r) =
        TestSource.probe[String]
          .via(new CancellationSuppresser[String, String](Flow[String]))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      w.sendNext("hello")
      r.requestNext("hello")

      w.sendNext("world")
      w.sendComplete()
      r.requestNext("world")
      r.expectComplete()
    }

    "suppress Cancel and emit onComplete" in {
      val CompletedStage = new GraphStage[FlowShape[String, String]] {
        val in = Inlet[String]("CompletedStage.in")
        val out = Outlet[String]("CompletedStage.out")
        val shape: FlowShape[String, String] = FlowShape(in, out)

        def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
          setHandler(in, new InHandler {
            def onPush(): Unit = ()
          })
          setHandler(out, new OutHandler {
            def onPull(): Unit = ()
          })
          override def preStart(): Unit = completeStage()
        }
      }

      val (w, r) =
        TestSource.probe[String]
          .via(new CancellationSuppresser[String, String](Flow[String].via(CompletedStage)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.expectSubscriptionAndComplete()
      assertThrows[java.lang.AssertionError] {
        w.expectCancellation()
      }
      w.sendComplete()
    }

    "make sure to emit onComplete if the inner flow emits only Cancel" in {
      val CancelledStage = new GraphStage[FlowShape[String, String]] {
        val in = Inlet[String]("CancelledStage.in")
        val out = Outlet[String]("CancelledStage.out")
        val shape: FlowShape[String, String] = FlowShape(in, out)

        def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
          setHandler(in, new InHandler {
            def onPush(): Unit = ()
          })
          setHandler(out, new OutHandler {
            def onPull(): Unit = ()
          })
          override def preStart(): Unit = cancel(in)
        }
      }

      val (w, r) =
        TestSource.probe[String]
          .via(new CancellationSuppresser[String, String](Flow[String].via(CancelledStage)))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.expectSubscriptionAndComplete()
      assertThrows[java.lang.AssertionError] {
        w.expectCancellation()
      }
      w.sendComplete()
    }

    "propagate Cancel if it is coming from downstream" in {
      val (w, r) =
        TestSource.probe[String]
          .via(new CancellationSuppresser[String, String](Flow[String]))
          .toMat(TestSink.probe[String])(Keep.both)
          .run()

      r.cancel()
      w.expectCancellation()
    }
  }
}
