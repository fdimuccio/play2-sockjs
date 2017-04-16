package protocol.utils

import java.util.UUID
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Future, Await}
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.testkit._
import akka.stream.testkit.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.headers.CacheDirectives._
import akka.http.scaladsl.model.HttpMethods._
import akka.util.{Timeout, ByteString}
import play.api.libs.json._

trait Helpers { self: TestServer =>

  object ContentTypesX {
    val `application/x-www-form-urlencoded` =
      ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`)
  }

  def sleep(duration: FiniteDuration) = {
    val scheduler  = app.actorSystem.scheduler
    val dispatcher = app.actorSystem.dispatcher
    val f = akka.pattern.after(duration, scheduler)(Future.successful(()))(dispatcher)
    Await.ready(f, Duration.Inf)
  }

  def session(serverId: String = "000") = "/" + serverId + "/" + UUID.randomUUID().toString

  implicit class Verifiers(val result: HttpResponse) {

    def verify200() = result.status mustEqual OK

    def verify204() = result.status mustEqual NoContent

    def verify304() = result.status mustEqual NotModified

    def verify404() = result.status mustEqual NotFound

    def verify405() = {
      result.status mustEqual MethodNotAllowed
      result.header[`Content-Type`] mustBe None
      result.header[Allow] mustBe a[Some[_]]
      body mustBe empty
    }

    def verify500() = result.status mustEqual InternalServerError

    def verifyTextPlain() = verifyMediaType(MediaTypes.`text/plain`)

    def verifyTextHtml() = verifyMediaType(MediaTypes.`text/html`)

    def verifyApplicationJavascript() = verifyMediaType(MediaTypes.`application/javascript`)

    def verifyMediaType(mtype: MediaType) = {
      result.entity.contentType.mediaType mustBe mtype
      result.entity.contentType.charsetOption mustBe Some(HttpCharsets.`UTF-8`)
    }

    def verifyNoContentType() = {
      result.entity.contentType mustBe ContentTypes.NoContentType
    }

    def verifyNoCookie() = result.header[`Set-Cookie`] mustBe None

    def verifyCookie(value: String) = {
      val Some(cookie) = result.header[`Set-Cookie`].map(_.cookie)
      cookie.name mustEqual "JSESSIONID"
      cookie.value mustEqual value
      cookie.path mustEqual Some("/")
    }

    def verifyCORS(origin: Option[String]) = origin match {
      case Some(value) =>
        // I have to look for the header manually because origin could be an invalid
        // value and akka-http will fail to parse it
        result.headers.find(_.is("access-control-allow-origin")).map(_.value) mustBe Some(value)
        result.header[`Access-Control-Allow-Credentials`] mustBe Some(`Access-Control-Allow-Credentials`(true))
      case _ =>
        result.header[`Access-Control-Allow-Origin`].map(_.range) mustBe Some(HttpOriginRange.*)
        result.header[`Access-Control-Allow-Credentials`] mustBe None
    }

    def verifyNotCached() = {
      result.header[`Cache-Control`] mustBe Some(`Cache-Control`(`no-store`, `no-cache`, `no-transform`, `must-revalidate`, `max-age`(0)))
      result.header[Expires] mustBe None
      result.header[`Last-Modified`] mustBe None
    }

    def verifyIFrame() = {
      verify200()
      verifyTextHtml()
      result.header[`Cache-Control`].map(_.value).orNull must include("public")
      result.header[`Cache-Control`].map(_.value).orNull must include regex "max-age=[1-9][0-9]{6}"
      result.header[Expires] mustBe a[Some[_]]
      result.header[ETag] mustBe a[Some[_]]
      result.header[`Last-Modified`] mustBe None
      //TODO: check body
      verifyNoCookie()
      discardBody()
    }

    def verifyOpenFrame() = {
      verify200()
      body mustBe "o\n"
    }

    def json: JsValue = Json.parse(body)

    def body: String = {
      val bytes = result.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
      Await.result(bytes, 3.seconds).utf8String
    }

    def discardBody(): Unit = Await.result(result.discardEntityBytes().future(), 3.seconds)

    def stream(delimiter: String)(implicit mat: Materializer): TestSubscriber.Probe[String] = {
      result.entity.dataBytes
        .via(Framing.delimiter(ByteString(delimiter), Int.MaxValue))
        .map(_.utf8String + delimiter)
        .runWith(TestSink.probe[String])
    }

    def cancel(): Unit = stream("\n").cancel()
  }

  def verifyOptions(url: String, allowedMethods: String)(implicit timeout: Timeout) = {
    for (origin <- List("test", "null")) {
      val r = http(HttpRequest(OPTIONS, uri = url, headers = List(RawHeader("Origin", origin))))
      r.status must (be(OK) or be(NoContent))
      r.header[`Cache-Control`].map(_.value).orNull must include("public")
      r.header[`Cache-Control`].map(_.value).orNull must include regex "max-age=[1-9][0-9]{6}"
      r.header[Expires] mustBe a[Some[_]]
      r.header[`Access-Control-Max-Age`].map(_.value.toInt).getOrElse(0) must be > 1000000
      r.body mustBe empty
      //TODO: test allowed methods
      r.verifyCORS(Some(origin))
    }
  }
}
