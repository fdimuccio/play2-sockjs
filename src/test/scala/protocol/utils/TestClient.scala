package protocol.utils

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.Timeout

import org.scalatest.{Args, Status, TestSuite}

import scala.util.control.NonFatal

trait TestClient extends org.scalatest.TestSuiteMixin { this: TestSuite with TestServer =>

  abstract override def run(testName: Option[String], args: Args): Status = {
    try {
      val status = super.run(testName, args)
      status.whenCompleted(_ => http.close())
      status
    } catch {
      case NonFatal(e) =>
        http.close()
        throw e
    }
  }

  implicit lazy val as  = ActorSystem("TestClient")
  implicit lazy val mat = ActorMaterializer()

  lazy val http: HttpClient = new HttpClient

  class HttpClient {
    private val _http = Http(as)

    def apply(request: HttpRequest)(implicit timeout: Timeout): HttpResponse = {
      val req = request
        .withEffectiveUri(securedConnection = false, akka.http.scaladsl.model.headers.Host("localhost", port))
      Await.result(_http.singleRequest(req), timeout.duration)
    }

    def ws[T](url: String)(implicit timeout: Timeout): (WebSocketUpgradeResponse, (TestSubscriber.Probe[Message], TestPublisher.Probe[Message])) = {
      val flow = Flow.fromSinkAndSourceMat(TestSink.probe[Message], TestSource.probe[Message])(Keep.both)
      val req = WebSocketRequest(s"ws://localhost:$port" + url)
      val (res, t) = _http.singleWebSocketRequest(req, flow)
      Await.result(res, timeout.duration) -> t
    }

    def close() = {
      Await.result(_http.shutdownAllConnectionPools(), Duration.Inf)
      Await.result(as.terminate(), Duration.Inf)
    }
  }
}
