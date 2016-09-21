package play.sockjs

import java.util.Locale

import org.joda.time.{DateTimeZone, DateTime}

import play.api.mvc._
import play.api.mvc.Results._
import play.api.http.HeaderNames._

package object core {

  // -- Results helpers

  def OptionsResult(methods: String*)(implicit req: RequestHeader) = {
    val ttl = 31536000
    NoContent
      .enableCORS(req)
      .cached(ttl)
      .withHeaders(
        ACCESS_CONTROL_ALLOW_METHODS -> methods.mkString(", "),
        ACCESS_CONTROL_MAX_AGE -> s"$ttl")
  }

  implicit class ResultEnricher(val result: Result) extends AnyVal {

    def enableCORS(req: RequestHeader) = {
      val headers = req.headers.get(ORIGIN) match {
        case Some(origin) =>
          List(ACCESS_CONTROL_ALLOW_ORIGIN -> origin, ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true")
        case _ =>
          List(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      }
      req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS).foldLeft(result) { (res, h) =>
        res.withHeaders(ACCESS_CONTROL_ALLOW_HEADERS -> h)
      }.withHeaders(headers:_*)
    }

    def notcached = result.withHeaders(CACHE_CONTROL -> "no-store, no-cache, no-transform, must-revalidate, max-age=0")

    def cached(ttl: Int) = {
      val expires = DateTime.now.plusSeconds(ttl).withZone(DateTimeZone.UTC)
      result.withHeaders(
        CACHE_CONTROL -> s"public, max-age=$ttl",
        EXPIRES -> expires.toString("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH))
    }

  }

}
