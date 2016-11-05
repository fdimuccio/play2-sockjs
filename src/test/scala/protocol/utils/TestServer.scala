package protocol.utils

import scala.concurrent.Await
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Keep, Flow}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.Timeout
import org.scalatestplus.play._
import play.api.mvc._
import play.api.inject.guice._
import play.api.routing.Router

trait TestServer extends PlaySpec with OneServerPerSuite {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  implicit override lazy val app = new GuiceApplicationBuilder()
    .router(new Router {
      val routers = List(
        new TestRouter.Echo(baseURL),
        new TestRouter.Closed(closeBaseURL),
        new TestRouter.EchoWithNoWebsocket(wsOffBaseURL),
        new TestRouter.EchoWithJSessionId(cookieBaseURL))
      def withPrefix(prefix: String): Router = this
      def documentation: Seq[(String, String, String)] = Seq.empty
      def routes = routers.foldRight(PartialFunction.empty[RequestHeader, Handler])(_.routes.orElse(_))
    })
    .build()

  implicit def as  = app.actorSystem
  implicit def mat = app.materializer

  lazy val http = new {
    private val _http = Http(app.actorSystem)

    def apply(request: HttpRequest)(implicit timeout: Timeout): HttpResponse = {
      val req = request
        .withEffectiveUri(securedConnection = false, headers.Host("localhost", port))
      Await.result(_http.singleRequest(req), timeout.duration)
    }

    def ws[T](url: String)(implicit timeout: Timeout): (WebSocketUpgradeResponse, (TestPublisher.Probe[Message], TestSubscriber.Probe[Message])) = {
      val flow = Flow.fromSinkAndSourceMat(TestSink.probe[Message], TestSource.probe[Message])(Keep.both)
      val req = WebSocketRequest(s"ws://localhost:$port" + url)
      val (res, t) = _http.singleWebSocketRequest(req, flow)
      Await.result(res, timeout.duration) -> t.swap
    }
  }
}
