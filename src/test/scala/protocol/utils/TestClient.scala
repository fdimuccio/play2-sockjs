package protocol.utils

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import org.apache.pekko.http.scaladsl.model.ws.{Message, WebSocketRequest, WebSocketUpgradeResponse}
import org.apache.pekko.stream.scaladsl.{Flow, Keep}
import org.apache.pekko.stream.testkit.{TestPublisher, TestSubscriber}
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalactic.source.Position
import org.scalatest.time._
import org.scalatest.concurrent._
import org.scalatest.{Args, Status, TestSuite}
import org.scalatest.matchers.must.Matchers

trait TestClient extends org.scalatest.TestSuiteMixin with ScalaFutures with Matchers { this: TestSuite with TestServer =>

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

  implicit val patienceConfiguration: PatienceConfig = PatienceConfig(
    timeout  = scaled(Span(10, Seconds)),
    interval = scaled(Span(50, Millis)))

  implicit lazy val as: ActorSystem = ActorSystem("TestClient")

  lazy val http: HttpClient = new HttpClient

  class HttpClient {
    private val _http = Http(as)

    def apply(request: HttpRequest)(implicit pos: Position): HttpResponse = {
      val req = request.withEffectiveUri(
        securedConnection = false,
        org.apache.pekko.http.scaladsl.model.headers.Host("localhost", runningHttpPort))
      _http.singleRequest(req).futureValue
    }

    def ws[T](url: String)(implicit pos: Position): (WebSocketUpgradeResponse, (TestSubscriber.Probe[Message], TestPublisher.Probe[Message])) = {
      val flow = Flow.fromSinkAndSourceMat(TestSink.probe[Message], TestSource.probe[Message])(Keep.both)
      val req = WebSocketRequest(s"ws://localhost:$runningHttpPort" + url)
      val (res, t) = _http.singleWebSocketRequest(req, flow)
      res.futureValue -> t
    }

    def close() = {
      Await.result(_http.shutdownAllConnectionPools(), Duration.Inf)
      Await.result(as.terminate(), Duration.Inf)
    }
  }
}
