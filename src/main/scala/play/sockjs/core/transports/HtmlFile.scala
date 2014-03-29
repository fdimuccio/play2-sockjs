package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.mvc._
import play.api.http._
import play.api.templates.Html

/**
 * HTMLfile transport
 */
object HtmlFile extends HeaderNames with Results {

  def transport = Transport.Streaming { (req, session) =>
    req.getQueryString("c").orElse(req.getQueryString("callback")).map { callback =>
      if (!callback.matches("[^a-zA-Z0-9-_.]")) {
        val tpl = Html(
          """
            |<!doctype html>
            |<html><head>
            |  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
            |  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            |</head><body><h2>Don't panic!</h2>
            |  <script>
            |    document.domain = document.domain;
            |    var c = parent.""".stripMargin+callback+""";
            |    c.start();
            |    function p(d) {c.message(d);};
            |    window.onload = function() {c.stop();};
            |  </script>
          """.stripMargin)
        val prelude = tpl + Array.fill(1024 - tpl.body.length + 14)(' ').mkString + "\r\n\r\n"
        session.bind { (enumerator, _) =>
          Ok.stream(Enumerator(Html(prelude)) >>> (enumerator &> Frame.toHTMLfile))
            .notcached
            .as("text/html; charset=UTF-8")
        }
      } else InternalServerError("invalid \"callback\" parameter")
    }.getOrElse(InternalServerError("\"callback\" parameter required"))
  }

}
