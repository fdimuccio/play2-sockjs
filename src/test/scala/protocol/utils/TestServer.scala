package protocol.utils

import javax.inject.{Inject, Provider}

import scala.concurrent.Await

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.Timeout

import org.scalatest.TestData
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneServerPerTest

import play.api.Application
import play.api.mvc._
import play.api.inject._
import play.api.inject.guice._
import play.api.routing.Router

trait TestServer extends PlaySpec with GuiceOneServerPerTest {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder()
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
      .bindings(bind[HttpClient].to(new Provider[HttpClient] {
        @Inject var actorSystem: ActorSystem = _
        lazy val get: HttpClient = new HttpClient(actorSystem)
      }))
      .build()

  implicit def as  = app.actorSystem
  implicit def mat = app.materializer

  def http = app.injector.instanceOf[HttpClient]

  class HttpClient(actorSystem: ActorSystem) {
    private val _http = Http(actorSystem)

    def apply(request: HttpRequest)(implicit timeout: Timeout): HttpResponse = {
      val req = request
        .withEffectiveUri(securedConnection = false, headers.Host("localhost", port))
      Await.result(_http.singleRequest(req), timeout.duration)
    }

    def ws[T](url: String)(implicit timeout: Timeout): (WebSocketUpgradeResponse, (TestSubscriber.Probe[Message], TestPublisher.Probe[Message])) = {
      val flow = Flow.fromSinkAndSourceMat(TestSink.probe[Message], TestSource.probe[Message])(Keep.both)
      val req = WebSocketRequest(s"ws://localhost:$port" + url)
      val (res, t) = _http.singleWebSocketRequest(req, flow)
      Await.result(res, timeout.duration) -> t
    }
  }
}
