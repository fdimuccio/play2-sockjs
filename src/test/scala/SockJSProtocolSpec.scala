import java.util.UUID

import scala.concurrent.Future

import org.scalatestplus.play._

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.routing.Router
import play.api.Application
import play.api.mvc._
import play.api.libs.json._
import play.api.test._
import play.api.test.Helpers._

//Here goes the tests for sockjs protocol
class SockJSProtocolSpec extends PlaySpec {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  "SockJS" should {

    "greet client" in new WithApplication(FakeApp) {
      for (url <- List(baseURL, baseURL + "/")) {
        val Some(result) = route(app, FakeRequest(GET, url))
        result.verify200
        contentType(result) mustBe Some("text/plain")
        charset(result) mustBe Some("UTF-8")
        contentAsString(result) must include("Welcome to SockJS!\n")
        result.verifyNoCookie
      }
    }

    "respond 404 notFound" in new WithApplication(FakeApp) {
      for (suffix <- List("/a", "/a.html", "//", "///", "/a/a", "/a/a/", "/a", "/a/")) {
        val Some(result) = route(app, FakeRequest(GET, baseURL + suffix))
        result.verify404
      }
    }

    "respond with iframe to simple iframe url" in new WithApplication(FakeApp) {
      testIFrame(baseURL + "/iframe.html")
    }

    "respond with iframe to iframe versioned url" in new WithApplication(FakeApp) {
      val urls = List("/iframe-a.html", "/iframe-.html", "/iframe-0.1.2.html", "/iframe-0.1.2abc-dirty.2144.html")
      for (url <- urls) testIFrame(baseURL + url)
    }

    "respond with iframe to iframe queried url" in new WithApplication(FakeApp) {
      val urls = List("/iframe-a.html?t=1234", "/iframe-0.1.2.html?t=123414", "/iframe-0.1.2abc-dirty.2144.html?t=qweqweq123")
      for (url <- urls) testIFrame(baseURL + url)
    }

    "respond with 404 notFound to malformed iframe request" in new WithApplication(FakeApp) {
      val urls = List("/iframe.htm", "/iframe", "/IFRAME.HTML", "/IFRAME", "/iframe.HTML", "/iframe.xml", "/iframe-/.html")
      for (url <- urls) {
        val Some(result) = route(app, FakeRequest(GET, baseURL + url))
        result.verify404
      }
    }

    //TODO: iframe test cacheability

    "respond with correct json to info request" in new WithApplication(FakeApp) {
      val Some(result) = route(app, FakeRequest(GET, baseURL + "/info"))
      result.verifyNoCookie
      //result.verifyNotCached
      //result.verifyCORS
      val json = contentAsJson(result)
      (json \ "websocket").validate[Boolean] mustBe JsSuccess(true)
      (json \ "cookie_needed").validate[Boolean] must (be(JsSuccess(true)) or be(JsSuccess(false)))
      (json \ "origins").validate[List[String]] mustBe JsSuccess(List("*:*"))
      (json \ "entropy").validate[Long] mustBe a[JsSuccess[_]]
    }

    "respond with good entropy to info request" in new WithApplication(FakeApp) {
      val Some(result1) = route(app, FakeRequest(GET, baseURL + "/info"))
      val entropy1 = contentAsJson(result1) \ "entropy"
      val Some(result2) = route(app, FakeRequest(GET, baseURL + "/info"))
      val entropy2 = contentAsJson(result2) \ "entropy"
      entropy1.validate[Long] mustBe a[JsSuccess[_]]
      entropy2.validate[Long] mustBe a[JsSuccess[_]]
      entropy1 must not equal entropy2
    }

    "respond correctly to info request with OPTIONS method" in new WithApplication(FakeApp) {
      testOptions(baseURL + "/info", "OPTIONS, GET")
    }

    "respond with disabled websocket" in new WithApplication(FakeApp) {
      val Some(result) = route(app, FakeRequest(GET, wsOffBaseURL + "/info"))
      result.verify200
      val json = contentAsJson(result)
      (json \ "websocket").as[Boolean] mustBe false
    }

    "pass simple session test" in new WithServer(FakeApp, 3333) {
      val ws = app.injector.instanceOf[WSClient]

      val transURL = "http://localhost:3333" + baseURL + "/000/" + UUID.randomUUID().toString

      val r1 = await(ws.url(transURL + "/xhr").post(""))
      r1.status mustBe OK
      r1.body mustBe "o\n"

      val payload = "[\"a\"]"
      val r2 = await(ws.url(transURL + "/xhr_send").post(payload))
      r2.status mustBe NO_CONTENT
      r2.body mustBe empty

      val r3 = await(ws.url(transURL + "/xhr").post(""))
      r3.status mustBe OK
      r3.body mustBe "a[\"a\"]\n"

      val r4 = await(ws.url("http://localhost:3333" + baseURL + "/000/bad_session/xhr_send").post(payload))
      r4.status mustBe NOT_FOUND

      // waiting for session timeout
      Thread.sleep(5100)

      val r5 = await(ws.url(transURL + "/xhr").post(""))
      r5.status mustBe OK
      r5.body mustBe "o\n"

      val r6 = ws.url(transURL + "/xhr").post("")
      val r7 = await(ws.url(transURL + "/xhr").post(""))
      r7.status mustBe OK
      r7.body mustBe "c[2010,\"Another connection still open\"]\n"

      ws.url(transURL + "/xhr_send").post(payload)
      await(r6).body mustBe "a[\"a\"]\n"

    }

    "respond to a closed session with close frame till session timeout" in new WithServer(FakeApp, 3333) {
      val ws = app.injector.instanceOf[WSClient]

      val transURL = "http://localhost:3333" + closeBaseURL + "/000/" + UUID.randomUUID().toString

      val r1 = await(ws.url(transURL + "/xhr").post(""))
      r1.status mustBe OK
      r1.body mustBe "o\n"

      val r2 = await(ws.url(transURL + "/xhr").post(""))
      r2.status mustBe OK
      r2.body mustBe "c[3000,\"Go away!\"]\n"

      val r3 = await(ws.url(transURL + "/xhr").post(""))
      r3.status mustBe OK
      r3.body mustBe "c[3000,\"Go away!\"]\n"
    }

    //TODO websocket tests

    //TODO: xhr polling tests

    //TODO: xhr_streaming options test

    /*
    "pass xhr_streaming transport test" in new WithServer(FakeApp, 3333) {
      val url = "http://localhost:3333" + baseURL + "/000/" + UUID.randomUUID().toString

      /*WS.url(url + "/xhr_streaming").postAndRetrieveStream("").map { r1 =>
        r1.status must equalTo(OK)
        r1.header(CONTENT_TYPE) must equalTo(Some("application/javascript; charset=\"UTF-8\""))
        //TODO: verify cors
        //TODO: verify not cached
        //contentAsString(r1) must equalTo(Array.fill(2048)('h')+"\no\n")
      }*/

      //Bug (forse nei test) per cui quando interrompo la connessione il server non se ne accorge
      val f = WS.url(url + "/xhr_streaming").postAndRetrieveStream("") { headers =>
        def consumer(counter: Int): Iteratee[Array[Byte], Unit] = Cont {
          case e @ Input.EOF => Done((), e)
          case Input.El(data) => println(counter, new String(data))
            if (new String(data).startsWith("o")) {
              //WS.url(url + "/xhr_send").post(Json.arr(Array.fill(3000)("a")))
              WS.url(url + "/xhr_send").post(Json.arr("a", "b", "c"))
              WS.url(url + "/xhr_send").post(Json.arr("e"))
              WS.url(url + "/xhr_send").post(Json.arr("f"))
            }
            if (counter == 0) Done((), Input.EOF)
            else consumer(counter - 1)
          case Input.Empty => println("empty", counter);consumer(counter)
        }
        //consumer(List(Array.fill(2048)('h').mkString("","","\n"), "o\n"))
        consumer(6)
      }.map(_.run)

      Helpers.await(f, 60000)

      Helpers.await(WS.url(url + "/xhr_streaming").postAndRetrieveStream("") { headers =>
        def consumer(counter: Int): Iteratee[Array[Byte], Unit] = Cont {
          case e @ Input.EOF => Done((), e)
          case Input.El(data) => println(counter, new String(data))
            if (new String(data).startsWith("o")) {
              //WS.url(url + "/xhr_send").post(Json.arr(Array.fill(3000)("a")))
              WS.url(url + "/xhr_send").post(Json.arr("a", "b", "c"))
              WS.url(url + "/xhr_send").post(Json.arr("e"))
              WS.url(url + "/xhr_send").post(Json.arr("f"))
            }
            if (counter == 0) Done((), Input.EOF)
            else consumer(counter - 1)
          case Input.Empty => println("empty", counter);consumer(counter)
        }
        //consumer(List(Array.fill(2048)('h').mkString("","","\n"), "o\n"))
        consumer(6)
      }.map(_.run), 25500)

      //Thread.sleep(35000)
    }
    */

  }

  def FakeApp = new GuiceApplicationBuilder()
    .router(new Router {
      import controllers.Application._
      val routers = List(
        new Echo(baseURL),
        new Closed(closeBaseURL),
        new EchoWithNoWebsocket(wsOffBaseURL),
        new EchoWithJSessionId(cookieBaseURL))
      def withPrefix(prefix: String): Router = this
      def documentation: Seq[(String, String, String)] = Seq.empty
      def routes = routers.foldRight(PartialFunction.empty[RequestHeader, Handler])(_.routes.orElse(_))
    })
    .build

  implicit class Verifier(val result: Future[Result]) {
    def verify200 = status(result) mustEqual OK
    def verify204 = status(result) mustEqual NO_CONTENT
    def verify404 = status(result) mustEqual NOT_FOUND
    def verify405 = {
      status(result) mustEqual METHOD_NOT_ALLOWED
      contentType(result) mustBe None
      header(ALLOW, result) mustBe a [Some[_]]
      contentAsBytes(result) mustBe empty
    }
    def verifyNoCookie = header(SET_COOKIE, result) mustBe None
    def verifyCORS(origin: Option[String]) = origin match {
      case Some(value) =>
        header(ACCESS_CONTROL_ALLOW_ORIGIN, result) mustBe Some(value)
        header(ACCESS_CONTROL_ALLOW_CREDENTIALS, result) mustBe Some("true")
      case _ =>
        header(ACCESS_CONTROL_ALLOW_ORIGIN, result) mustBe Some("*")
        header(ACCESS_CONTROL_ALLOW_CREDENTIALS, result) mustBe None
    }
    def verifyNotCached = {
      header(CACHE_CONTROL, result) mustBe Some("no-store, no-cache, must-revalidate, max-age=0")
      header(EXPIRES, result) mustBe None
      header(LAST_MODIFIED, result) mustBe None
    }
  }

  def testIFrame(url: String)(implicit app: Application) = {
    val Some(result) = route(app, FakeRequest(GET, url))
    result.verify200
    contentType(result) mustBe Some("text/html")
    charset(result) mustBe Some("UTF-8")
    header(CACHE_CONTROL, result).getOrElse("") must include("public")
    header(CACHE_CONTROL, result).getOrElse("") must include regex "max-age=[1-9][0-9]{6}"
    //header(EXPIRES, result) must beSome TODO
    header(ETAG, result) mustBe a [Some[_]]
    header(LAST_MODIFIED, result) mustBe None
    //TODO: check body
    result.verifyNoCookie
  }

  def testOptions(url: String, allowedMethods: String)(implicit app: Application) = {
    for (origin <- List(None, Some("test"), Some("null"))) {
      val req = origin.foldLeft(FakeRequest("OPTIONS", url)) { case (r, header) =>
        r.withHeaders(ORIGIN -> header)
      }
      val Some(result) = route(app, req)
      result.verify204
      header(CACHE_CONTROL, result).getOrElse("") must include("public")
      header(CACHE_CONTROL, result).getOrElse("") must include regex "max-age=[1-9][0-9]{6}"
      //header(EXPIRES, result) must beSome TODO
      header(ACCESS_CONTROL_MAX_AGE, result).map(_.toInt).getOrElse(0) must be > 1000000
      contentAsString(result) mustBe empty
      result.verifyCORS(origin)
    }
  }

  /*
  def FcontentAsString(of: Result): String = new String(FcontentAsBytes(of), charset(of).getOrElse("utf-8"))

  def FcontentAsBytes(of: Result): Array[Byte] = of match {
    case r @ SimpleResult(_, bodyEnumerator) => {
      var readAsBytes = Enumeratee.map[r.BODY_CONTENT](r.writeable.transform(_)).transform(Iteratee.consume[Array[Byte]]())
      bodyEnumerator(readAsBytes).flatMap(_.run).value1.get
    }

    case c @ ChunkedResult(code, chunks) =>
      var buffer = Array.empty[Byte]
      val reader = Iteratee.fold[Array[Byte], Unit](buffer) { (_, bytes) => buffer ++= bytes }
      val promised = c.chunks(Enumeratee.map[c.BODY_CONTENT](c.writeable.transform(_)) &>> reader).asInstanceOf[Promise[Iteratee[Array[Byte], Unit]]]
      promised.future.flatMap(_.run).value1.get
      buffer

    case AsyncResult(p) => FcontentAsBytes(p.await.get)
  }
  */
}
