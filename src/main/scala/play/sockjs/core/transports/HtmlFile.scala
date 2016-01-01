package play.sockjs.core
package transports

import scala.concurrent.Future

import akka.stream.scaladsl._
import akka.util.{ByteStringBuilder, ByteString}

import play.api.mvc._
import play.api.http._

import play.sockjs.core.json.JsonByteStringEncoder

/**
 * HTMLfile transport
 */
private[sockjs] class HtmlFile(transport: Transport) extends HeaderNames with Results {
  import HtmlFile._

  def streaming = transport.streaming { req =>
    req.getQueryString("c").orElse(req.getQueryString("callback")).map { callback =>
      if (callback.matches("[a-zA-Z0-9-_.]*")) req.bind("text/html; charset=UTF-8") { source =>
        val tpl =
          """|<!doctype html>
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
          """.stripMargin
        val prelude = tpl + Array.fill(1024 - tpl.length + 14)(' ').mkString + "\r\n\r\n"
        Source.single(ByteString(prelude)).concat(source.map { frame =>
          scriptS ++ JsonByteStringEncoder.encodeFrame(frame) ++ scriptE
        })
      } else Future.successful(InternalServerError("invalid \"callback\" parameter"))
    }.getOrElse(Future.successful(InternalServerError("\"callback\" parameter required")))
  }
}

private[sockjs] object HtmlFile {
  private val scriptS = ByteString("<script>\np(")
  private val scriptE = ByteString(");\n</script>\r\n")
}