package protocol.utils

import javax.inject.{Inject, Provider}

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.Timeout

import org.scalatest._

import play.api.Application
import play.api.mvc._
import play.api.inject.guice._
import play.api.routing.Router

import protocol.routers.TestRouters

abstract class TestServer(testRoutersFactory: ActorSystem => TestRouters)
  extends WordSpec with MustMatchers with OptionValues with org.scalatest.TestSuiteMixin {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  private lazy val app: Application =
    new GuiceApplicationBuilder()
      .router(new Router {
        @Inject
        private var actorSystem: ActorSystem = _
        private val testRouters = testRoutersFactory(actorSystem)
        private val routers = List(
          testRouters.Echo(baseURL),
          testRouters.Closed(closeBaseURL),
          testRouters.EchoWithNoWebsocket(wsOffBaseURL),
          testRouters.EchoWithJSessionId(cookieBaseURL))
        def withPrefix(prefix: String): Router = this
        def documentation: Seq[(String, String, String)] = Seq.empty
        def routes = routers.foldRight(PartialFunction.empty[RequestHeader, Handler])(_.routes.orElse(_))
      })
      .configure(
        "akka.stream.materializer.debug.fuzzing-mode" -> "on",
        "play.http.filters" -> "play.api.http.NoHttpFilters",
        "akka.http.client.connecting-timeout" -> "10s",
        "akka.http.client.idle-timeout" -> "60s",
        "akka.http.client.host-connection-pool.max-connections" -> 32
      )
      .build()

  /**
    * The port used by the `TestServer`.  By default this will be set to the result returned from
    * `Helpers.testServerPort`. You can override this to provide a different port number.
    */
  lazy val port: Int = play.api.test.Helpers.testServerPort

  lazy val http: HttpClient = new HttpClient

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
    val testServer = play.api.test.TestServer(port, app, serverProvider = Some(new play.core.server.NettyServerProvider))
    //val testServer = play.api.test.TestServer(port, app)
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
        http.close()
        testServer.stop()
        throw ex
    }
  }

  implicit def as  = http.actorSystem
  implicit def mat = http.materializer

  class HttpClient {
    implicit val actorSystem = ActorSystem("HttpClient")
    implicit val materializer = ActorMaterializer()(actorSystem)

    private val _http = Http(actorSystem)

    def apply(request: HttpRequest)(implicit timeout: Timeout): HttpResponse = {
      val req = request
        .withEffectiveUri(securedConnection = false, headers.Host("localhost", port))
      Await.result(_http.singleRequest(req)(materializer), timeout.duration)
    }

    def ws[T](url: String)(implicit timeout: Timeout): (WebSocketUpgradeResponse, (TestSubscriber.Probe[Message], TestPublisher.Probe[Message])) = {
      val flow = Flow.fromSinkAndSourceMat(TestSink.probe[Message](actorSystem), TestSource.probe[Message](actorSystem))(Keep.both)
      val req = WebSocketRequest(s"ws://localhost:$port" + url)
      val (res, t) = _http.singleWebSocketRequest(req, flow)(materializer)
      Await.result(res, timeout.duration) -> t
    }

    def close() = {
      Await.result(_http.shutdownAllConnectionPools(), Duration.Inf)
      Await.result(actorSystem.terminate(), Duration.Inf)
    }
  }
}
