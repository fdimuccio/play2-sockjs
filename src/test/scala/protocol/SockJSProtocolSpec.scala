package protocol

import java.util.UUID

import scala.concurrent.duration._
import scala.util.{Random, Try}

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.testkit.scaladsl.{TestSink, TestSource}

import org.apache.commons.text.StringEscapeUtils

import play.api.libs.json._

import protocol.routers._

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.sockjs.api.DefaultSockJSRouterComponents

/**
  * SockJS protocol tests.
  *
  * https://github.com/sockjs/sockjs-protocol/blob/master/sockjs-protocol.py
  */
abstract class SockJSProtocolSpec(builder: ActorSystem => TestRouters)
  extends utils.TestHelpers with utils.TestClient with utils.TestServer {
  override def TestRoutersBuilder(as: ActorSystem): TestRouters = builder(as)

  "play2-sockjs" must {

    "provide a base url greeting" which {

      "greets client" in {
        for (url <- List(baseURL, baseURL + "/")) {
          val r = http(HttpRequest(GET, url))
          r.verify200()
          r.verifyTextPlain()
          r.body must include("Welcome to SockJS!\n")
          r.verifyNoCookie()
        }
      }

      "returns 404 notFound to simple requests" in {
        for (suffix <- List("/a", "/a.html", "//", "///", "/a/a", "/a/a/", "/a", "/a/")) {
          val r = http(HttpRequest(GET, baseURL + suffix))
          r.verify404()
          r.discardBody()
        }
      }

    }

    "provide an iframe page" which {

      "route is '/iframe.html'" in {
        val r = http(HttpRequest(GET, baseURL + "/iframe.html"))
        r.verifyIFrame()
      }

      "supports versioned url" in {
        val urls = List("/iframe-a.html", "/iframe-.html", "/iframe-0.1.2.html", "/iframe-0.1.2abc-dirty.2144.html")
        for (url <- urls) {
          val r = http(HttpRequest(GET, baseURL + url))
          r.verifyIFrame()
        }
      }

      "supports query string in url" in {
        val urls = List("/iframe-a.html?t=1234", "/iframe-0.1.2.html?t=123414", "/iframe-0.1.2abc-dirty.2144.html?t=qweqweq123")
        for (url <- urls) {
          val r = http(HttpRequest(GET, baseURL + url))
          r.verifyIFrame()
        }
      }

      "returns 404 notFound to malformed request" in {
        val urls = List("/iframe.htm", "/iframe", "/IFRAME.HTML", "/IFRAME", "/iframe.HTML", "/iframe.xml", "/iframe-/.html")
        for (url <- urls) {
          val r = http(HttpRequest(GET, baseURL + url))
          r.verify404()
          r.discardBody()
        }
      }

      "is cacheable" in {
        val r1 = http(HttpRequest(GET, baseURL + "/iframe.html"))
        val r2 = http(HttpRequest(GET, baseURL + "/iframe.html"))
        r1.discardBody()
        r1.header[ETag] mustBe a[Some[_]]
        r1.header[ETag] mustEqual r2.header[ETag]

        val header = `If-None-Match`(r1.header[ETag].get.etag)
        val r = http(HttpRequest(GET, baseURL + "/iframe.html", List(header)))
        r.verify304()
        r.verifyNoContentType()
        r.body mustBe empty
      }

    }

    "provide an info endpoint" which {

      "route is '/info'" in {
        val result = http(HttpRequest(GET, baseURL + "/info", List(RawHeader("Origin","test"))))
        result.verifyNoCookie()
        result.verifyNotCached()
        result.verifyCORS(Some("test"))
        val json = result.json
        (json \ "websocket").as[Boolean] mustBe true
        (json \ "cookie_needed").as[Boolean] must (be(true) or be(false))
        (json \ "origins").as[List[String]] mustBe List("*:*")
        (json \ "entropy").validate[Long] mustBe a[JsSuccess[_]]
      }

      "returns a good entropy" in {
        val result1 = http(HttpRequest(GET, baseURL + "/info"))
        val entropy1 = result1.json \ "entropy"
        val result2 = http(HttpRequest(GET, baseURL + "/info"))
        val entropy2 = result2.json \ "entropy"
        entropy1.validate[Long] mustBe a[JsSuccess[_]]
        entropy2.validate[Long] mustBe a[JsSuccess[_]]
        entropy1 must not equal entropy2
      }

      "implements OPTIONS method correctly" in {
        verifyOptions(baseURL + "/info", "OPTIONS, GET")
      }

      "implements OPTIONS method correctly when origin is null" in {
        val headers = List(RawHeader("Origin", "null"), `Access-Control-Request-Method`(POST))
        val result = http(HttpRequest(OPTIONS, baseURL + "/info", headers))
        result.status must (be(OK) or be(NoContent))
        result.body mustBe empty
        result.header[`Access-Control-Allow-Origin`].map(_.value) mustBe Some("null")
      }

      "returns disabled websocket" in {
        val result = http(HttpRequest(GET, wsOffBaseURL + "/info"))
        result.verify200()
        (result.json \ "websocket").as[Boolean] mustBe false
      }

    }

    "provide session URLs" which {

      "accepts any value in `server` and `session` fields" in {
        val rnd = Random.nextInt(1024)
        val r = http(HttpRequest(POST, baseURL + "/a/a" + rnd + "/xhr"))
        r.verifyOpenFrame()
        for (part <- List("/_/_", "/1/", "/abcdefgh_i-j%20/abcdefg_i-j%20")) {
          val r = http(HttpRequest(POST, baseURL + part + rnd + "/xhr"))
          r.verifyOpenFrame()
        }
      }

      "doesn't accept empty string, anything containing dots or paths with less or more parts" in {
        for (suffix <- List("//", "/a./a", "/a/a.", "/./.", "/", "///")) {
          val r1 = http(HttpRequest(GET, baseURL + suffix + "/xhr"))
          r1.verify404()
          r1.discardBody()
          val r2 = http(HttpRequest(POST, baseURL + suffix + "/xhr"))
          r2.verify404()
          r2.discardBody()
        }
      }

      "ignores 'server_id'" in {
        val sid = UUID.randomUUID().toString
        val r1 = http(HttpRequest(POST, baseURL + "/000/" + sid + "/xhr"))
        r1.verifyOpenFrame()

        val payload = "[\"a\"]"
        val r2 = http(HttpRequest(POST, baseURL + "/000/" + sid + "/xhr_send", entity = payload))
        r2.verify204()
        r2.body mustBe empty

        val r3 = http(HttpRequest(POST, baseURL + "/999/" + sid + "/xhr"))
        r3.verify200()
        r3.body mustBe "a[\"a\"]\n"
      }

    }

    "implement protocol version 0.3" which {

      "passes simple session test" in {
        val transURL = baseURL + session()

        val r1 = http(HttpRequest(POST, transURL + "/xhr"))
        r1.verifyOpenFrame()

        val payload = "[\"a\"]"
        val r2 = http(HttpRequest(POST, transURL + "/xhr_send", entity = payload))
        r2.verify204()
        r2.body mustBe empty

        val r3 = http(HttpRequest(POST, transURL + "/xhr"))
        r3.verify200()
        r3.body mustBe "a[\"a\"]\n"

        val r4 = http(HttpRequest(POST, baseURL + "/000/bad_session/xhr_send", entity = payload))
        r4.verify404()

        // waiting for session timeout
        sleep(6.seconds)

        val r5 = http(HttpRequest(POST, transURL + "/xhr"))
        r5.verifyOpenFrame()

        val r6 = http(HttpRequest(POST, transURL + "/xhr"))
        val r7 = http(HttpRequest(POST, transURL + "/xhr"))
        r7.verify200()
        r7.body mustBe "c[2010,\"Another connection still open\"]\n"

        http(HttpRequest(POST, transURL + "/xhr_send", entity = payload))
        r6.body mustBe "a[\"a\"]\n"
      }

      "returns the close frame when the session has been closed before it expires" in {
        val transURL = closeBaseURL + session()

        val r1 = http(HttpRequest(POST, transURL + "/xhr"))
        r1.verifyOpenFrame()

        val r2 = http(HttpRequest(POST, transURL + "/xhr"))
        r2.verify200()
        r2.body mustBe "c[3000,\"Go away!\"]\n"

        val r3 = http(HttpRequest(POST, transURL + "/xhr"))
        r3.verify200()
        r3.body mustBe "c[3000,\"Go away!\"]\n"
      }

    }

    //TODO websocket hixie

    "support WebSocket transport" which {

      "implementation is compliant with specs" in {
        val (_, (in, out)) = http.ws(baseURL + session() + "/websocket")
        in.requestNext(TextMessage("o"))

        // server must ignore empty messages
        out.sendNext(TextMessage(""))
        out.sendNext(TextMessage("[\"a\"]"))
        in.requestNext(TextMessage("a[\"a\"]"))
        out.sendComplete()
      }

      "sends a close frame when the session is over" in {
        val (_, (in, out)) = http.ws(closeBaseURL + session() + "/websocket")
        in.requestNext(TextMessage("o"))
        in.requestNext(TextMessage("c[3000,\"Go away!\"]"))
        in.expectComplete()
      }

   /*   "is compatible with both Hybi-07 and Hybi-10" in {
        for (version <- List("7", "8", "13")) {
          val headers = List(
            "Upgrade"    -> "websocket",
            "Connection" -> "Upgrade",
            "Sec-WebSocket-Version" -> version,
            "Sec-WebSocket-Origin"  -> "http://asd",
            "Sec-WebSocket-Key"     -> "x3JJHMbDL1EzLkh9GBhXDw=="
          ).map { case (k, v) => RawHeader(k, v) }

          val r = http(HttpRequest(GET, baseURL + session() + "/websocket", headers))
          r.status mustBe 101
          val map = r.headers.map(h => h.name() -> h.value()).toMap
          println(r.headers)
          map.get("Sec-WebSocket-Accept") mustBe Some("HSmrc0sMlYUkAGmm5OPpG2HaGWk=")
          map.get("Connection") must (be(Some("Upgrade")) or be(Some("upgrade")))
          map.get("Content-Length") mustBe None
          r.discardBody()
        }
      }*/

      "closes the connection abruptly if the client sends broken json" in {
        val (_, (in, out)) = http.ws(baseURL + session() + "/websocket")
        in.requestNext(TextMessage("o"))
        out.sendNext(TextMessage("[\"a"))
        in.expectComplete()
      }

      /*"works with Firefox 6.0.2 connection header" in {
        val ws: WSClient = ???//app.injector.instanceOf[WSClient]

        val headers = List(
          "Upgrade"    -> "websocket",
          "Connection" -> "Keep-Alive, Upgrade",
          "Sec-WebSocket-Version" -> "7",
          "Sec-WebSocket-Origin"  -> "http://asd",
          "Sec-WebSocket-Key"     -> "x3JJHMbDL1EzLkh9GBhXDw=="
        )
        val req = ws.url("http://localhost:" + port + baseURL + session() + "/websocket")
          .withHeaders(headers:_*)
          .get()
        Await.result(req, Duration.Inf).status mustBe 101
      }*/
    }

    "implement XHR polling" which {

      "supports CORS requests and answer correctly to OPTIONS requests" in {
        for (suffix <- List("/xhr", "/xhr_send")) {
          verifyOptions(baseURL + "/abc/abc" + suffix, "OPTIONS, POST")
        }
      }

      "transport is compliant with specs" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr", List(RawHeader("Origin","test"))))
        r1.verifyOpenFrame()
        r1.verifyApplicationJavascript()
        r1.verifyCORS(Some("test"))
        // iOS 6 caches POSTs. Make sure we send no-cache header.
        r1.verifyNotCached()

        // Xhr transports receive json-encoded array of messages.
        val r2 = http(HttpRequest(POST, url + "/xhr_send", List(RawHeader("Origin","test")), "[\"x\"]"))
        r2.verify204()
        r2.body mustBe empty
        // The content type of `xhr_send` must be set to `text/plain`,
        // even though the response code is `204`. This is due to
        // Firefox/Firebug behaviour - it assumes that the content type
        // is xml and shouts about it.
        r2.verifyTextPlain()
        r2.verifyCORS(Some("test"))
        // iOS 6 caches POSTs. Make sure we send no-cache header.
        r2.verifyNotCached()

        val r3 = http(HttpRequest(POST, url + "/xhr"))
        r3.verify200()
        r3.body mustBe "a[\"x\"]\n"
      }

      "returns 404 when publishing messages to a non-existing session" in {
        val url = baseURL + session()
        val r = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"x\"]"))
        r.verify404()
      }

      "behave when invalid json data is sent or when no json data is sent at all" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.verifyOpenFrame()

        val r2 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"x"))
        r2.verify500()
        r2.body mustBe "Broken JSON encoding."

        val r3 = http(HttpRequest(POST, url + "/xhr_send"))
        r3.verify500()
        r3.body mustBe "Payload expected."

        val r4 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"a\"]"))
        r4.verify204()
        r4.body mustBe empty

        val r5 = http(HttpRequest(POST, url + "/xhr"))
        r5.verify200()
        r5.body mustBe "a[\"a\"]\n"
      }

      "accepts messages sent with different content types" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.body mustBe "o\n"

        val ctypes = List(
          "text/plain", "T", "application/json", "application/xml", "",
          "application/json; charset=utf-8", "text/xml; charset=utf-8",
          "text/xml")
        for (ct <- ctypes) {
          val header = RawHeader("Content-Type", ct)
          val r = http(HttpRequest(POST, url + "/xhr_send", List(header), "[\"a\"]"))
          r.verify204()
          r.body mustBe empty
        }

        val r2 = http(HttpRequest(POST, url + "/xhr"))
        r2.verify200()
        r2.body mustBe ("a[" + Array.fill(ctypes.length)("\"a\"").mkString(",") + "]\n")
      }

      "has to be compliant with CORS requests" in {
        val tests = List(
          List(
            RawHeader("Origin","test"),
            `Access-Control-Request-Method`(POST),
            `Access-Control-Request-Headers`("a, b, c")),
          List(
            RawHeader("Origin","test"),
            `Access-Control-Request-Method`(POST),
            `Access-Control-Request-Headers`("")),
          List(
            RawHeader("Origin","test"),
            `Access-Control-Request-Method`(POST))
        )

        for (headers <- tests) {
          val url = baseURL + session()
          val r = http(HttpRequest(OPTIONS, url + "/xhr", headers))
          r.status must (be(OK) or be(NoContent))
          r.verifyCORS(Some("test"))
          if (headers.size == 3)
            r.header[`Access-Control-Allow-Headers`].map(_.value) mustBe Some(headers.last.value)
          else
            r.header[`Access-Control-Allow-Headers`] mustBe None
        }
      }

      "accepts empty frames from clients" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.verifyOpenFrame()

        val r2 = http(HttpRequest(POST, url + "/xhr_send", entity = "[]"))
        r2.verify204()

        val r3 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"a\"]"))
        r3.verify204()

        val r4 = http(HttpRequest(POST, url + "/xhr"))
        r4.verify200()
        r4.body mustBe "a[\"a\"]\n"
      }

    }

    "implement XHR streaming" which {

      "supports CORS requests and answer correctly to OPTIONS requests" in {
        verifyOptions(baseURL + "/abc/abc/xhr_streaming", "OPTIONS, POST")
      }

      "transport is compliant with specs" in {
        val url = baseURL + session()

        val r = http(HttpRequest(POST, url + "/xhr_streaming", List(RawHeader("Origin","test"))))
        r.verify200()
        r.verifyApplicationJavascript()
        r.verifyCORS(Some("test"))
        // iOS 6 caches POSTs. Make sure we send no-cache header.
        r.verifyNotCached()

        val reader = r.stream("\n")

        // The transport must first send 2KiB of `h` bytes as prelude.
        reader.requestNext("h" * 2048 + "\n")

        reader.requestNext("o\n")

        val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"x\"]"))
        r1.verify204()
        r1.body mustBe empty

        reader.requestNext("a[\"x\"]\n")
        reader.cancel()
      }

      "closes a single streaming request after enough data has been delivered" in {
        val url = baseURL + session()
        val r = http(HttpRequest(POST, url + "/xhr_streaming"))
        r.verify200()

        val reader = r.stream("\n")

        // prelude
        reader.requestNext()

        reader.requestNext("o\n")

        // Test server should gc streaming session after 4096 bytes
        // were sent (including framing)
        val msg = "\"" + "x" * 128 + "\""
        for (i <- 0 until 31) {
          val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[" + msg + "]"))
          r1.verify204()
          reader.requestNext("a[" + msg + "]\n")
        }

        reader.request(1)
        reader.expectComplete()
      }
    }

    "implement EventSource" which {

      "transport is compliant with specs" in {
        val url = baseURL + session()

        val r = http(HttpRequest(GET, url + "/eventsource"))
        r.verify200()
        r.entity.contentType.mediaType mustBe MediaType.text("event-stream")
        // As EventSource is requested using GET we must be very
        // careful not to allow it being cached.
        r.verifyNotCached()

        val reader = r.stream("\r\n\r\n")

        // The transport must first send a new line prelude, due to a
        // bug in Opera.
        reader.requestNext("\r\ndata: o\r\n\r\n")

        val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"x\"]"))
        r1.body mustBe empty
        r1.verify204()

        reader.requestNext("data: a[\"x\"]\r\n\r\n")

        // This protocol doesn't allow binary data and we need to
        // specially treat leading space, new lines and things like
        // \x00. But, now the protocol json-encodes everything, so
        // there is no way to trigger this case.
        val r2 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"  \\u0000\\n\\r \"]"))
        r2.body mustBe empty
        r2.verify204()

        reader.requestNext("data: a[\"  \\u0000\\n\\r \"]\r\n\r\n")

        reader.cancel()
      }

      "closes a single streaming request after enough data has been deleivered" in {
        val url = baseURL + session()
        val r = http(HttpRequest(GET, url + "/eventsource"))
        r.verify200()

        val reader = r.stream("\r\n\r\n")

        // prelude + open frame
        reader.requestNext("\r\ndata: o\r\n\r\n")

        // Test server should gc streaming session after 4096 bytes
        // were sent (including framing)
        val msg = "\"" + "x" * 4096 + "\""
        val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[" + msg + "]"))
        r1.verify204()
        reader.requestNext("data: a[" + msg + "]\r\n\r\n")

        reader.request(1)
        reader.expectComplete()
      }
    }

    "implement HtmlFile" which {

      val html =
        """|<!doctype html>
           |<html><head>
           |  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
           |  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
           |</head><body><h2>Don't panic!</h2>
           |  <script>
           |    document.domain = document.domain;
           |    var c = parent.%s;
           |    c.start();
           |    function p(d) {c.message(d);};
           |    window.onload = function() {c.stop();};
           |  </script>
           |""".stripMargin.trim

      "transport is compliant with specs" in {
        val url = baseURL + session()

        val r = http(HttpRequest(GET, url + "/htmlfile?c=%63allback"))
        r.verify200()
        r.verifyTextHtml()
        // As HtmlFile is requested using GET we must be very careful
        // not to allow it being cached.
        r.verifyNotCached()

        val reader = r.stream("\r\n")

        val head = reader.requestNext()
        head.size must be > 1024
        head.trim mustEqual (html format "callback")
        reader.requestNext("\r\n")
        reader.requestNext("<script>\np(\"o\");\n</script>\r\n")

        val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"x\"]"))
        r1.body mustBe empty
        r1.verify204()

        reader.requestNext("<script>\np(\"a[\\\"x\\\"]\");\n</script>\r\n")
        reader.cancel()
      }

      "returns 500 if the callback parameter is missing" in {
        val r = http(HttpRequest(GET, baseURL + "/a/a/htmlfile"))
        r.verify500()
        r.body mustEqual "\"callback\" parameter required"
      }

      "returns 500 if the callback parameter contains invalid characters" in {
        for (c <- List("%20", "*", "abc(", "abc%28")) {
          val r = http(HttpRequest(GET, baseURL + "/a/a/htmlfile?c=" + c))
          r.verify500()
          r.body mustEqual "invalid \"callback\" parameter"
        }
      }

      "closes a single streaming request after enough data has been deleivered" in {
        val url = baseURL + session()

        val r = http(HttpRequest(GET, url + "/htmlfile?c=callback"))
        r.verify200()

        val reader = r.stream("\r\n")

        // html + padding
        reader.requestNext()
        reader.requestNext()

        reader.requestNext("<script>\np(\"o\");\n</script>\r\n")

        // Test server should gc streaming session after 4096 bytes
        // were sent (including framing)
        val msg = "x" * 4096
        val r1 = http(HttpRequest(POST, url + "/xhr_send", entity = "[\"" + msg + "\"]"))
        r1.verify204()

        reader.requestNext("<script>\np(\"a[\\\"" + msg + "\\\"]\");\n</script>\r\n")

        // The connection should be closed after enough data was delivered.
        reader.request(1)
        reader.expectComplete()
      }
    }

    "implement json polling" which {

      "transport is compliant with specs" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(GET, url + "/jsonp?c=%63allback"))
        r1.verify200()
        r1.verifyApplicationJavascript()
        // As JsonPolling is requested using GET we must be very
        // careful not to allow it being cached.
        r1.verifyNotCached()

        r1.body mustEqual "/**/callback(\"o\");\r\n"

        val data = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22x%22%5D")
        val r2 = http(HttpRequest(POST, url + "/jsonp_send", entity = data))
        // Konqueror does weird things on 204. As a workaround we need
        // to respond with something - let it be the string `ok`.
        r2.body mustEqual "ok"
        r2.verify200()
        r2.verifyTextPlain()
        // iOS 6 caches POSTs. Make sure we send no-cache header.
        r2.verifyNotCached()

        val r3 = http(HttpRequest(GET, url + "/jsonp?c=%63allback"))
        r3.verify200()
        r3.body mustEqual "/**/callback(\"a[\\\"x\\\"]\");\r\n"
      }

      "returns 500 when the callback parameter is missing" in {
        val r = http(HttpRequest(GET, baseURL + "/a/a/jsonp"))
        r.verify500()
        r.body mustEqual "\"callback\" parameter required"
      }

      "returns 500 when the callback parameter contains invalid characters" in {
        for (c <- List("%20", "*", "abc(", "abc%28")) {
          val r = http(HttpRequest(GET, baseURL + "/a/a/jsonp?c=" + c))
          r.verify500()
          r.body mustEqual "invalid \"callback\" parameter"
        }
      }

      "returns 500 when the json data sent is invalid or missing" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r1.body mustEqual "/**/x(\"o\");\r\n"

        val data2 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22x")
        val r2 = http(HttpRequest(POST, url + "/jsonp_send", entity = data2))
        r2.verify500()
        r2.body mustEqual "Broken JSON encoding."

        for (payload <- List("", "d=", "p=p")) {
          val data = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, payload)
          val r = http(HttpRequest(POST, url + "/jsonp_send", entity = data))
          r.verify500()
          r.body mustEqual "Payload expected."
        }

        val data3 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22b%22%5D")
        val r3 = http(HttpRequest(POST, url + "/jsonp_send", entity = data3))
        r3.body mustEqual "ok"

        val r4 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r4.verify200()
        r4.body mustEqual "/**/x(\"a[\\\"b\\\"]\");\r\n"
      }

      "accepts messages sent with different content types" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r1.body mustEqual "/**/x(\"o\");\r\n"

        val data2 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22abc%22%5D")
        val r2 = http(HttpRequest(POST, url + "/jsonp_send", entity = data2))
        r2.body mustEqual "ok"

        val r3 = http(HttpRequest(POST, url + "/jsonp_send", entity = "[\"%61bc\"]"))
        r3.body mustEqual "ok"

        val r4 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r4.verify200()
        r4.body mustEqual "/**/x(\"a[\\\"abc\\\",\\\"%61bc\\\"]\");\r\n"
      }

      "emits close frame until the session expires" in {
        val url = closeBaseURL + session()

        val r1 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r1.body mustEqual "/**/x(\"o\");\r\n"

        val r2 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r2.body mustEqual "/**/x(\"c[3000,\\\"Go away!\\\"]\");\r\n"

        val r3 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r3.body mustEqual "/**/x(\"c[3000,\\\"Go away!\\\"]\");\r\n"
      }

      "accepts empty frames from clients" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r1.body mustEqual "/**/x(\"o\");\r\n"

        val data2 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%5D")
        val r2 = http(HttpRequest(POST, url + "/jsonp_send", entity = data2))
        r2.body mustEqual "ok"

        val data3 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22x%22%5D")
        val r3 = http(HttpRequest(POST, url + "/jsonp_send", entity = data3))
        r3.body mustEqual "ok"

        val r4 = http(HttpRequest(GET, url + "/jsonp?c=x"))
        r4.verify200()
        r4.body mustEqual "/**/x(\"a[\\\"x\\\"]\");\r\n"
      }
    }

    "implement JsessionID cookies support" which {

      "is enabled with cookie_needed parameter in info" in {
        val r = http(HttpRequest(GET, cookieBaseURL + "/info"))
        r.verify200()
        r.verifyNoCookie()
        (r.json \ "cookie_needed").as[Boolean] mustBe true
      }

      "works properly in XHR polling" in {
        // polling url must set cookies
        val url1 = cookieBaseURL + session()
        val r1 = http(HttpRequest(POST, url1 + "/xhr"))
        r1.verify200()
        r1.body mustBe "o\n"
        r1.verifyCookie("dummy")

        // Cookie must be echoed back if it's already set.
        val url2 = cookieBaseURL + session()
        val headers = List(RawHeader("Cookie", "JSESSIONID=abcdef"))
        val r2 = http(HttpRequest(POST, url2 + "/xhr", headers))
        r2.verify200()
        r2.body mustEqual "o\n"
        r2.verifyCookie("abcdef")
      }

      "works properly in XHR streaming" in {
        val url = cookieBaseURL + session()
        val r = http(HttpRequest(POST, url + "/xhr_streaming"))
        r.verify200()
        r.verifyCookie("dummy")
        r.cancel()
      }

      "works properly in EventSource" in {
        val url = cookieBaseURL + session()
        val r = http(HttpRequest(GET, url + "/eventsource"))
        r.verify200()
        r.verifyCookie("dummy")
        r.cancel()
      }

      "works properly in HtmlFile" in {
        val url = cookieBaseURL + session()
        val r = http(HttpRequest(GET, url + "/htmlfile?c=%63allback"))
        r.verify200()
        r.verifyCookie("dummy")
        r.cancel()
      }

      "works properly in Jsonp" in {
        val url = cookieBaseURL + session()
        val r1 = http(HttpRequest(GET, url + "/jsonp?c=%63allback"))
        r1.verify200()
        r1.verifyCookie("dummy")

        r1.body mustEqual "/**/callback(\"o\");\r\n"

        val data2 = HttpEntity(ContentTypes.`application/x-www-form-urlencoded`, "d=%5B%22x%22%5D")
        val r2 = http(HttpRequest(POST, url + "/jsonp_send", entity = data2))
        r2.body mustEqual "ok"
        r2.verify200()
        r2.verifyCookie("dummy")
      }
    }

    "support raw WebSocket" which {

      "handles raw transport" in {
        val (_, (in, out)) = http.ws(baseURL + "/websocket")
        out.sendNext(TextMessage("Hello world!\uffff"))
        in.requestNext(TextMessage("Hello world!\uffff"))
        out.sendComplete()
        in.expectComplete()
      }

      "handles closed connection properly" in {
        val (_, (in, out)) = http.ws(closeBaseURL + "/websocket")
        val error = in.expectSubscriptionAndError()
        error mustBe a[PeerClosedConnectionException]
        error.asInstanceOf[PeerClosedConnectionException].closeReason mustEqual "Go away!"
      }
    }

    "handle correctly JSON surrogates" when {

      /**
        * SockJS takes the responsibility of encoding Unicode strings for the
        * user.  The idea is that SockJS should properly deliver any valid
        * string from the browser to the server and back. This is actually
        * quite hard, as browsers do some magical character
        * translations. Additionally there are some valid characters from
        * JavaScript point of view that are not valid Unicode, called
        * surrogates (JavaScript uses UCS-2, which is not really Unicode).
        *
        * Dealing with unicode surrogates (0xD800-0xDFFF) is quite special. If
        * possible we should make sure that server does escape decode
        * them. This makes sense for SockJS servers that support UCS-2
        * (SockJS-node), but can't really work for servers supporting unicode
        * properly (Python).
        *
        * The browser must escape quite a list of chars, this is due to
        * browser mangling outgoing chars on transports like XHR.
        */
      val escapableByClient = "[\\\"\\x00-\\x1f\\x7f-\\x9f\\u00ad\\u0600-\\u0604\\u070f\\u17b4\\u17b5\\u2000-\\u20ff\\ufeff\\ufff0-\\uffff\\x00-\\x1f\\ufffe\\uffff\\u0300-\\u0333\\u033d-\\u0346\\u034a-\\u034c\\u0350-\\u0352\\u0357-\\u0358\\u035c-\\u0362\\u0374\\u037e\\u0387\\u0591-\\u05af\\u05c4\\u0610-\\u0617\\u0653-\\u0654\\u0657-\\u065b\\u065d-\\u065e\\u06df-\\u06e2\\u06eb-\\u06ec\\u0730\\u0732-\\u0733\\u0735-\\u0736\\u073a\\u073d\\u073f-\\u0741\\u0743\\u0745\\u0747\\u07eb-\\u07f1\\u0951\\u0958-\\u095f\\u09dc-\\u09dd\\u09df\\u0a33\\u0a36\\u0a59-\\u0a5b\\u0a5e\\u0b5c-\\u0b5d\\u0e38-\\u0e39\\u0f43\\u0f4d\\u0f52\\u0f57\\u0f5c\\u0f69\\u0f72-\\u0f76\\u0f78\\u0f80-\\u0f83\\u0f93\\u0f9d\\u0fa2\\u0fa7\\u0fac\\u0fb9\\u1939-\\u193a\\u1a17\\u1b6b\\u1cda-\\u1cdb\\u1dc0-\\u1dcf\\u1dfc\\u1dfe\\u1f71\\u1f73\\u1f75\\u1f77\\u1f79\\u1f7b\\u1f7d\\u1fbb\\u1fbe\\u1fc9\\u1fcb\\u1fd3\\u1fdb\\u1fe3\\u1feb\\u1fee-\\u1fef\\u1ff9\\u1ffb\\u1ffd\\u2000-\\u2001\\u20d0-\\u20d1\\u20d4-\\u20d7\\u20e7-\\u20e9\\u2126\\u212a-\\u212b\\u2329-\\u232a\\u2adc\\u302b-\\u302c\\uaab2-\\uaab3\\uf900-\\ufa0d\\ufa10\\ufa12\\ufa15-\\ufa1e\\ufa20\\ufa22\\ufa25-\\ufa26\\ufa2a-\\ufa2d\\ufa30-\\ufa6d\\ufa70-\\ufad9\\ufb1d\\ufb1f\\ufb2a-\\ufb36\\ufb38-\\ufb3c\\ufb3e\\ufb40-\\ufb41\\ufb43-\\ufb44\\ufb46-\\ufb4e]".r

      /**
        * The server is able to send much more chars verbatim. But, it can't
        * send Unicode surrogates over Websockets, also various \\u2xxxx chars
        * get mangled. Additionally, if the server is capable of handling
        * UCS-2 (ie: 16 bit character size), it should be able to deal with
        * Unicode surrogates 0xD800-0xDFFF:
        * http://en.wikipedia.org/wiki/Mapping_of_Unicode_characters#Surrogates
        */
      val escapableByServer = "[\\x00-\\x1f\\u200c-\\u200f\\u2028-\\u202f\\u2060-\\u206f\\ufff0-\\uffff]".r

      "encoding server side" in {
        val serverKillerString =
          (255 until 65536)
            .map(_.toChar)
            .filter(ch => escapableByServer.findFirstIn(Array(ch)).isDefined)
            .mkString

        val serverKillerStringEsc =
          serverKillerString
            .map(ch => f"\\u$ch%04X")
            .mkString

        // Make sure that server encodes at least all the characters
        // it's supposed to encode.
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.body mustEqual "o\n"
        r1.verify200()

        val payload = "[\"" + serverKillerString + "\"]"
        val r2 = http(HttpRequest(POST, url + "/xhr_send", entity = payload))
        r2.verify204()

        val r3 = http(HttpRequest(POST, url + "/xhr"))
        r3.verify200()
        val recv = r3.body.replace("a[\"", "").replace("\"]\n", "")
        recv mustEqual serverKillerStringEsc
      }

      "decoding server side" in {
        val clientKillerString =
          (0 until 65536)
            .map(_.toChar)
            .filter(ch => escapableByClient.findFirstIn(Array(ch)).isDefined)
            .mkString

        val clientKillerStringEsc =
          clientKillerString
            .map(ch => f"\\u$ch%04X")
            .mkString

        // Make sure that server encodes at least all the characters
        // it's supposed to encode.
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.body mustEqual "o\n"
        r1.verify200()

        val payload = "[\"" + clientKillerStringEsc + "\"]"
        val r2 = http(HttpRequest(POST, url + "/xhr_send", entity = payload))
        r2.verify204()

        val r3 = http(HttpRequest(POST, url + "/xhr"))
        r3.verify200()
        val recv = r3.body.replace("a[\"", "").replace("\"]\n", "")
        StringEscapeUtils.unescapeJava(recv) mustBe clientKillerString
      }

    }

    "handle session closure correctly" when {

      "server is unlinking current request" in {
        val url = closeBaseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader1 = r1.stream("\n")

        // prelude
        reader1.requestNext()
        reader1.requestNext("o\n")
        reader1.requestNext("c[3000,\"Go away!\"]\n")

        val r2 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader2 = r2.stream("\n")

        // prelude
        reader2.requestNext()
        reader2.requestNext("c[3000,\"Go away!\"]\n")

        // HTTP streaming requests should be automatically closed after close.
        reader1.request(1); reader1.expectComplete()
        reader2.request(1); reader2.expectComplete()
      }

      "another connection is still open" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader1 = r1.stream("\n")

        // prelude
        reader1.requestNext()
        reader1.requestNext("o\n")

        val r2 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader2 = r2.stream("\n")

        // prelude
        reader2.requestNext()
        reader2.requestNext("c[2010,\"Another connection still open\"]\n")

        // HTTP streaming requests should be automatically closed after
        // getting the close frame.
        reader2.request(1)
        reader2.expectComplete()

        reader1.cancel()
      }

      "a streaming request has been aborted" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader1 = r1.stream("\n")

        // prelude
        reader1.requestNext()
        reader1.requestNext("o\n")

        // Can't do second polling request now.
        val r2 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader2 = r2.stream("\n")

        // prelude
        reader2.requestNext()
        reader2.requestNext("c[2010,\"Another connection still open\"]\n")
        reader2.request(1)
        reader2.expectComplete()

        reader1.cancel()

        // Polling request now, after we aborted previous one, should
        // trigger a connection closure. Implementations may close
        // the session and forget the state related. Alternatively
        // they may return a 1002 close message.
        val r3 = http(HttpRequest(POST, url + "/xhr_streaming"))
        val reader3 = r3.stream("\n")

        // prelude
        reader3.requestNext()
        reader3.requestNext() must (be("o\n") or be("c[1002,\"Connection interrupted\"]\n"))

        reader3.cancel()
      }

      "a polling request has been aborted" in {
        val url = baseURL + session()

        val r1 = http(HttpRequest(POST, url + "/xhr"))
        r1.body mustEqual "o\n"

        val r2 = http(HttpRequest(POST, url + "/xhr"))
        // FIXME
        sleep(500.millis)

        // Can't do second polling request now.
        val r3 = http(HttpRequest(POST, url + "/xhr"))
        r3.body mustEqual "c[2010,\"Another connection still open\"]\n"

        r2.stream("\n").cancel()
        // FIXME: ugly but needed since canceling a stream is asynchronous
        sleep(500.millis)

        // Polling request now, after we aborted previous one, should
        // trigger a connection closure. Implementations may close
        // the session and forget the state related. Alternatively
        // they may return a 1002 close message.
        val r4 = http(HttpRequest(POST, url + "/xhr"))
        r4.body must (be("o\n") or be("c[1002,\"Connection interrupted\"]\n"))
      }
    }

    "implement correctly Http 1.0" when {

      "calling simple urls" in {
        val connection = Http().newHostConnectionPool[Int]("localhost", port,
          ConnectionPoolSettings(as).withMaxConnections(1))

        val (client, responses) =
          TestSource.probe[(HttpRequest, Int)]
            .via(connection)
            .toMat(TestSink.probe[(Try[HttpResponse], Int)])(Keep.both)
            .run()

        val req = HttpRequest(GET, baseURL,
          headers = List(Connection("Keep-Alive")),
          protocol = HttpProtocols.`HTTP/1.0`)

        client.sendNext(req -> 1)
        val (scala.util.Success(r), 1) = responses.requestNext()
        r.verify200()
        // In practice the exact http version on the response doesn't
        // really matter. Many serves always respond 1.1.
        r.protocol must (be(HttpProtocols.`HTTP/1.0`) or be(HttpProtocols.`HTTP/1.1`))
        // Transfer-encoding is not allowed in http/1.0.
        r.header[`Transfer-Encoding`] mustBe None

        // There are two ways to give valid response. Use
        // Content-Length (and maybe connection:Keep-Alive) or
        // Connection: close.
        r.entity.contentLengthOption match {

          case Some(clength) =>
            clength mustBe 19
            r.body mustBe "Welcome to SockJS!\n"
            r.header[Connection] match {
              case None =>
              case Some(conn) if conn.hasClose =>
              case Some(conn) =>
                conn.hasKeepAlive mustBe true
                // We should be able to issue another request on the same connection
                client.sendNext(req -> 2)
                val (scala.util.Success(r), 2) = responses.requestNext()
                r.verify200()
            }

          case None =>
            r.header[Connection].exists(_.hasClose) mustBe true
            r.body mustEqual "Welcome to SockJS!\n"
        }
      }

      "using streaming protocols" in {
        val url = closeBaseURL + session()

        val req = HttpRequest(POST, url + "/xhr_streaming",
          headers = List(Connection("Keep-Alive")),
          protocol = HttpProtocols.`HTTP/1.0`)

        val r = http(req)
        r.verify200()
        // Transfer-encoding is not allowed in http/1.0.
        r.header[`Transfer-Encoding`] mustBe None
        // Content-length is not allowed - we don't know it yet.
        r.entity.contentLengthOption mustBe None

        // `Connection` should be not set or be `close`. On the other
        // hand, if it is set to `Keep-Alive`, it won't really hurt, as
        // we are confident that neither `Content-Length` nor
        // `Transfer-Encoding` are set.

        // This is a the same logic as HandlingClose.test_close_frame
        val reader = r.stream("\n")
        reader.requestNext("h" * 2048 + "\n")
        reader.requestNext("o\n")
        reader.requestNext("c[3000,\"Go away!\"]\n")
        reader.cancel()
      }
    }

    "implement correctly Http 1.1" when {

      "calling simple urls" in {
        val connection = Http().newHostConnectionPool[Int]("localhost", port,
          ConnectionPoolSettings(as).withMaxConnections(1))

        val (client, responses) =
          TestSource.probe[(HttpRequest, Int)]
            .via(connection)
            .toMat(TestSink.probe[(Try[HttpResponse], Int)])(Keep.both)
            .run()

        val req = HttpRequest(GET, baseURL,
          headers = List(Connection("Keep-Alive")),
          protocol = HttpProtocols.`HTTP/1.1`)

        client.sendNext(req -> 1)
        val (scala.util.Success(r), 1) = responses.requestNext()
        r.protocol mustBe HttpProtocols.`HTTP/1.1`
        val connH = r.header[Connection].map(_.tokens.map(_.toLowerCase))
        connH.getOrElse(Seq("")) must (contain("keep-alive") or contain(""))

        // Server should use 'Content-Length' or 'Transfer-Encoding'
        r.entity.contentLengthOption match {

          case Some(clength) =>
            clength mustBe 19
            r.body mustBe "Welcome to SockJS!\n"
            r.header[`Transfer-Encoding`] mustBe None

          case None =>
            r.entity.isChunked() mustBe true
            r.body mustEqual "Welcome to SockJS!\n"
        }

        client.sendNext(req -> 2)
        val (scala.util.Success(r2), 2) = responses.requestNext()
        r2.verify200()
      }

      "using streaming protocols" in {
        val url = closeBaseURL + session()

        val req = HttpRequest(POST, url + "/xhr_streaming",
          headers = List(Connection("Keep-Alive")),
          protocol = HttpProtocols.`HTTP/1.1`)

        val r = http(req)
        r.verify200()
        // Transfer-encoding is required in http/1.1.
        r.entity.isChunked() mustBe true
        // Content-length is not allowed.
        r.entity.contentLengthOption mustBe None
        // Connection header can be anything, so don't bother verifying it.

        // This is a the same logic as HandlingClose.test_close_frame
        val reader = r.stream("\n")
        reader.requestNext("h" * 2048 + "\n")
        reader.requestNext("o\n")
        reader.requestNext("c[3000,\"Go away!\"]\n")
        reader.cancel()
      }
    }
  }
}

class ScalaFlowSockJSProtocolTest
  extends SockJSProtocolSpec(_ => {
    val app: Application = new GuiceApplicationBuilder().build
    val components = app.injector.instanceOf(classOf[DefaultSockJSRouterComponents])
      new ScalaFlowTestRouters(components)
  })

class ScalaActorSockJSProtocolTest
  extends SockJSProtocolSpec(implicit as => {
    val app: Application = new GuiceApplicationBuilder().build
    val components = app.injector.instanceOf(classOf[DefaultSockJSRouterComponents])
      new ScalaActorTestRouters(components)
  })

class JavaFlowSockJSProtocolTest
  extends SockJSProtocolSpec(_ => {
    val app: Application = new GuiceApplicationBuilder().build
    val components = app.injector.instanceOf(classOf[DefaultSockJSRouterComponents])
    new JavaFlowTestRouters(components)
  })

class JavaActorSockJSProtocolTest
  extends SockJSProtocolSpec(implicit as => {
    val app: Application = new GuiceApplicationBuilder().build
    val components = app.injector.instanceOf(classOf[DefaultSockJSRouterComponents])
    new JavaActorTestRouters(components)
  })