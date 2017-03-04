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
import org.scalatest.{Args, Status, TestData}
import org.scalatestplus.play._
import org.scalatestplus.play.guice.{GuiceOneServerPerSuite, GuiceOneServerPerTest}
import play.api.Application
import play.api.mvc._
import play.api.inject._
import play.api.inject.guice._
import play.api.routing.Router
import play.api.test.{Helpers, TestServer}
import protocol.routers.TestRouters

abstract class TestServer(testRoutersFactory: () => TestRouters) extends PlaySpec with org.scalatest.TestSuiteMixin {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  implicit lazy val app: Application =
    new GuiceApplicationBuilder()
      .router(new Router {
        val testRouters = testRoutersFactory()
        val routers = List(
          testRouters.Echo(baseURL),
          testRouters.Closed(closeBaseURL),
          testRouters.EchoWithNoWebsocket(wsOffBaseURL),
          testRouters.EchoWithJSessionId(cookieBaseURL))
        def withPrefix(prefix: String): Router = this
        def documentation: Seq[(String, String, String)] = Seq.empty
        def routes = routers.foldRight(PartialFunction.empty[RequestHeader, Handler])(_.routes.orElse(_))
      })
      .configure("akka.stream.materializer.debug.fuzzing-mode" -> "on")
      .bindings(bind[HttpClient].to(new Provider[HttpClient] {
        @Inject var actorSystem: ActorSystem = _
        lazy val get: HttpClient = new HttpClient(actorSystem)
      }))
      .build()

  /**
    * The port used by the `TestServer`.  By default this will be set to the result returned from
    * `Helpers.testServerPort`. You can override this to provide a different port number.
    */
  lazy val port: Int = Helpers.testServerPort

  /**
    * Invokes `start` on a new `TestServer` created with the `Application` provided by `app` and the
    * port number defined by `port`, places the `Application` and port number into the `ConfigMap` under the keys
    * `org.scalatestplus.play.app` and `org.scalatestplus.play.port`, respectively, to make
    * them available to nested suites; calls `super.run`; and lastly ensures the `Application` and test server are stopped after
    * all tests and nested suites have completed.
    *
    * @param testName an optional name of one test to run. If `None`, all relevant tests should be run.
    *                 I.e., `None` acts like a wildcard that means run all relevant tests in this `Suite`.
    * @param args the `Args` for this run
    * @return a `Status` object that indicates when all tests and nested suites started by this method have completed, and whether or not a failure occurred.
    */
  override def run(testName: Option[String], args: Args): Status = {
    val testServer = TestServer(port, app, serverProvider = Some(new play.core.server.NettyServerProvider))
    //val testServer = TestServer(port, app)
    println("***************** START SERVER")
    testServer.start()
    try {
      val newConfigMap = args.configMap + ("org.scalatestplus.play.app" -> app) + ("org.scalatestplus.play.port" -> port)
      val newArgs = args.copy(configMap = newConfigMap)
      val status = super.run(testName, newArgs)
      status.whenCompleted { _ => testServer.stop() }
      status
    }
    catch { // In case the suite aborts, ensure the server is stopped
      case ex: Throwable =>
        testServer.stop()
        throw ex
    }
  }

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
