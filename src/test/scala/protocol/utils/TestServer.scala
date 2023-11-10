package protocol.utils

import org.apache.pekko.actor.ActorSystem

import org.scalatest._

import play.api.Application
import play.api.mvc._
import play.api.inject.guice._
import play.api.routing.Router
import play.api.test.{Helpers, TestServer => PlayTestServer}

import protocol.routers.TestRouters

trait TestServer extends org.scalatest.TestSuiteMixin { this: TestSuite =>

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  def TestRoutersBuilder(as: ActorSystem): TestRouters

  private lazy val app: Application =
    new GuiceApplicationBuilder()
      .router(new Router {
        private val testRouters = TestRoutersBuilder(ActorSystem("TestRouters"))
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
        "org.apache.pekko.stream.materializer.debug.fuzzing-mode" -> "on",
        "play.http.filters" -> "play.api.http.NoHttpFilters"
      )
      .build()

  private var testServer: PlayTestServer = _

  def runningHttpPort: Int = {
    if (testServer == null)
      throw new IllegalStateException("Test server not running")
    testServer.runningHttpPort
      .getOrElse(throw new IllegalStateException("Can't retrieve http port from test server"))
  }

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
  abstract override def run(testName: Option[String], args: Args): Status = {
    testServer = PlayTestServer(Helpers.testServerPort, app, serverProvider = Some(new play.core.server.NettyServerProvider))
    testServer.start()
    try {
      val newConfigMap =
        args.configMap +
        ("org.scalatestplus.play.app" -> app) +
        ("org.scalatestplus.play.port" -> testServer.runningHttpPort.getOrElse(0))
      val newArgs = args.copy(configMap = newConfigMap)
      val status = super.run(testName, newArgs)
      status.whenCompleted { _ =>
        testServer.stop()
        testServer = null
      }
      status
    }
    catch { // In case the suite aborts, ensure the server is stopped
      case ex: Throwable =>
        testServer.stop()
        testServer = null
        throw ex
    }
  }
}
