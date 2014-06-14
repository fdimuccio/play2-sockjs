import java.util.UUID

import play.api.libs.ws.WS
import scala.concurrent.ExecutionContext.Implicits.global

import org.specs2.mutable._
import org.specs2.matcher._

import play.api.GlobalSettings
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import play.api.test._
import play.api.test.Helpers._
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.util.parsing.json.JSONArray

//Here goes the tests for sockjs protocol
class SockJSProtocolSpec extends Specification with JsonMatchers  {

  val baseURL = "/echo"
  val closeBaseURL = "/close"
  val wsOffBaseURL = "/disabled_websocket_echo"
  val cookieBaseURL = "/cookie_needed_echo"

  trait FakeRouter extends GlobalSettings {
    val echoController = new controllers.Echo(baseURL)
    val closedController = new controllers.Closed(closeBaseURL)
    val wsoffController = new controllers.DisabledWebSocketEcho(wsOffBaseURL)
    val cookieNeededController = new controllers.CookieNeededEchoController(cookieBaseURL)
    override def onRouteRequest(req: RequestHeader): Option[Handler] = req.path match {
      case url if url.startsWith(baseURL)       => echoController.routes.lift(req)
      case url if url.startsWith(closeBaseURL)  => closedController.routes.lift(req)
      case url if url.startsWith(wsOffBaseURL)  => wsoffController.routes.lift(req)
      case url if url.startsWith(cookieBaseURL) => cookieNeededController.routes.lift(req)
      case _ => super.onRouteRequest(req)
    }
  }

  def FakeApp = FakeApplication(withGlobal = Some(new GlobalSettings with FakeRouter))

  implicit class Verifier(val result: Future[SimpleResult]) {
    def verify200 = status(result) must equalTo(OK)
    def verify204 = status(result) must equalTo(NO_CONTENT)
    def verify404 = status(result) must equalTo(NOT_FOUND)
    def verify405 = {
      status(result) must equalTo(METHOD_NOT_ALLOWED)
      header(CONTENT_TYPE, result) must beNone
      header(ALLOW, result) must beSome
      contentAsBytes(result) must beEmpty
    }
    def verifyNoCookie = header(SET_COOKIE, result) must beNone
    def verifyCORS(origin: Option[String]) = {
      header(ACCESS_CONTROL_ALLOW_ORIGIN, result) must beSome(origin.filter(_ != "null").getOrElse("*"))
      header(ACCESS_CONTROL_ALLOW_CREDENTIALS, result) must beSome("true")
    }
    def verifyNotCached = {
      header(CACHE_CONTROL, result) must beSome("no-store, no-cache, must-revalidate, max-age=0")
      header(EXPIRES, result) must beNone
      header(LAST_MODIFIED, result) must beNone
    }
  }

  def testIFrame(url: String) = {
    val Some(result) = route(FakeRequest(GET, url))
    result.verify200
    header(CONTENT_TYPE, result) must beSome("text/html; charset=UTF-8")
    header(CACHE_CONTROL, result).getOrElse("") must contain("public")
    header(CACHE_CONTROL, result).getOrElse("") must find("""max-age=[1-9][0-9]{6}""".r)
    //header(EXPIRES, result) must beSome TODO
    header(ETAG, result) must beSome
    header(LAST_MODIFIED, result) must beNone
    //TODO: check body
    result.verifyNoCookie
  }

  def testOptions(url: String, allowedMethods: String) = {
    for (origin <- List(None, Some("test"), Some("null")))
    yield {
      val req = origin.foldLeft(FakeRequest("OPTIONS", url)) { case (r, header) =>
        r.withHeaders(ORIGIN -> header)
      }
      val Some(result) = route(req)
      result.verify204
      header(CACHE_CONTROL, result).getOrElse("") must contain("public")
      header(CACHE_CONTROL, result).getOrElse("") must find("""max-age=[1-9][0-9]{6}""".r)
      //header(EXPIRES, result) must beSome TODO
      header(ACCESS_CONTROL_MAX_AGE, result).map(_.toInt).getOrElse(0) must be_>(1000000)
      contentAsString(result) must beEmpty
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

  "SockJS" should {

    "greet client" in new WithApplication(FakeApp) {
      for (url <- List(baseURL, baseURL + "/")) yield {
        val Some(result) = route(FakeRequest(GET, url))
        result.verify200
        header(CONTENT_TYPE, result) must beSome("text/plain; charset=UTF-8")
        contentAsString(result) must contain("Welcome to SockJS!\n")
        result.verifyNoCookie
      }
    }

    "respond 404 notFound" in new WithApplication(FakeApp) {
      for (suffix <- List("/a", "/a.html", "//", "///", "/a/a", "/a/a/", "/a", "/a/")) yield {
        val Some(result) = route(FakeRequest(GET, baseURL + suffix))
        result.verify404
      }
    }

    "respond with iframe to simple iframe url" in new WithApplication(FakeApp) {
      testIFrame(baseURL + "/iframe.html")
    }

    "respond with iframe to iframe versioned url" in new WithApplication(FakeApp) {
      val urls = List("/iframe-a.html", "/iframe-.html", "/iframe-0.1.2.html", "/iframe-0.1.2abc-dirty.2144.html")
      for (url <- urls) yield testIFrame(baseURL + url)
    }

    "respond with iframe to iframe queried url" in new WithApplication(FakeApp) {
      val urls = List("/iframe-a.html?t=1234", "/iframe-0.1.2.html?t=123414", "/iframe-0.1.2abc-dirty.2144.html?t=qweqweq123")
      for (url <- urls) yield testIFrame(baseURL + url)
    }

    "respond with 404 notFound to malformed iframe request" in new WithApplication(FakeApp) {
      val urls = List("/iframe.htm", "/iframe", "/IFRAME.HTML", "/IFRAME", "/iframe.HTML", "/iframe.xml", "/iframe-/.html")
      for (url <- urls) yield {
        val Some(result) = route(FakeRequest(GET, baseURL + url))
        result.verify404
      }
    }

    //TODO: iframe test cacheability

    "respond with correct json to info request" in new WithApplication(FakeApp) {
      val Some(result) = route(FakeRequest(GET, baseURL + "/info"))
      result.verifyNoCookie
      //result.verifyNotCached
      //result.verifyCORS
      val json = contentAsString(result)
      json must /("websocket" -> true)
      json must /("cookie_needed" -> true) or /("cookie_needed" -> false)
      json must /("origins" -> JSONArray(List("*:*")))
      val entropy = Json.parse(json) \ "entropy"
      (entropy.asOpt[Int] orElse entropy.asOpt[Long]) must beSome
    }

    "respond with good entropy to info request" in new WithApplication(FakeApp) {
      val Some(result1) = route(FakeRequest(GET, baseURL + "/info"))
      val entropy1 = Json.parse(contentAsString(result1)) \ "entropy"
      val Some(result2) = route(FakeRequest(GET, baseURL + "/info"))
      val entropy2 = Json.parse(contentAsString(result2)) \ "entropy"
      (entropy1.asOpt[Int] orElse entropy1.asOpt[Long]) must beSome
      (entropy2.asOpt[Int] orElse entropy2.asOpt[Long]) must beSome
      entropy1 mustNotEqual entropy2
    }

    "respond correctly to info request with OPTIONS method" in new WithApplication(FakeApp) {
      testOptions(baseURL + "/info", "OPTIONS, GET")
    }

    "respond with * for cors when issuing request for null Origin" in new WithApplication(FakeApp) {
      val Some(result) = route(FakeRequest("OPTIONS", baseURL + "/info").withHeaders(ORIGIN -> "null"))
      result.verify204
      contentAsString(result) must beEmpty
      header(ACCESS_CONTROL_ALLOW_ORIGIN, result) must beSome("*")
    }

    "respond with disabled websocket" in new WithApplication(FakeApp) {
      val Some(result) = route(FakeRequest(GET, wsOffBaseURL + "/info"))
      result.verify200
      val json = contentAsString(result)
      json must /("websocket" -> false)
    }

    "pass simple session test" in new WithServer(FakeApp, 3333) {
      val transURL = "http://localhost:3333" + baseURL + "/000/" + UUID.randomUUID().toString

      val r1 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r1.status must equalTo(OK)
      r1.body must equalTo("o\n")

      val payload = "[\"a\"]"
      val r2 = Helpers.await(WS.url(transURL + "/xhr_send").post(payload))
      r2.status must equalTo(NO_CONTENT)
      r2.body must beEmpty

      val r3 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r3.status must equalTo(OK)
      r3.body must equalTo("a[\"a\"]\n")

      val r4 = Helpers.await(WS.url("http://localhost:3333" + baseURL + "/000/bad_session/xhr_send").post(payload))
      r4.status must equalTo(NOT_FOUND)

      // waiting for session timeout
      Thread.sleep(5100)

      val r5 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r5.status must equalTo(OK)
      r5.body must equalTo("o\n")

      val r6 = WS.url(transURL + "/xhr").post("")
      val r7 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r7.status must equalTo(OK)
      r7.body must equalTo("c[2010,\"Another connection still open\"]\n")

      WS.url(transURL + "/xhr_send").post(payload)
      Helpers.await(r6).body must equalTo("a[\"a\"]\n")

    }

    "respond to a closed session with close frame till session timeout" in new WithServer(FakeApp, 3333) {
      val transURL = "http://localhost:3333" + closeBaseURL + "/000/" + UUID.randomUUID().toString

      val r1 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r1.status must equalTo(OK)
      r1.body must equalTo("o\n")

      val r2 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r2.status must equalTo(OK)
      r2.body must equalTo("c[3000,\"Go away!\"]\n")

      val r3 = Helpers.await(WS.url(transURL + "/xhr").post(""))
      r3.status must equalTo(OK)
      r3.body must equalTo("c[3000,\"Go away!\"]\n")
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

}
