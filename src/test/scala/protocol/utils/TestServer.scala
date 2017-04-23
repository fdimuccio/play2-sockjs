package protocol.utils

import akka.actor.ActorSystem

import org.scalatest._

import play.api.Application
import play.api.mvc._
import play.api.inject.guice._
import play.api.routing.Router

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
        "akka.stream.materializer.debug.fuzzing-mode" -> "on",
        "play.http.filters" -> "play.api.http.NoHttpFilters"
      )
      .build()

  /**
    * The port used by the `TestServer`.  By default this will be set to the result returned from
    * `Helpers.testServerPort`. You can override this to provide a different port number.
    */
  lazy val port: Int = play.api.test.Helpers.testServerPort

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
        testServer.stop()
        throw ex
    }
  }
}
