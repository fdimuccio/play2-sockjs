package play.sockjs.core
package transports

import java.security.MessageDigest

import scala.util.Random

import play.api.mvc._
import play.api.http._
import play.api.libs.json._

/**
 * SockJS utils endpoint
 */
object Utils extends HeaderNames with Results {

  /**
   * greeting
   */
  def greet = SockJSAction(Action {
    Ok("Welcome to SockJS!\n").as("text/plain; charset=UTF-8")
  })

  /**
   * iframe page (needed by iframe transports)
   */
  def iframe(script: RequestHeader => String) = SockJSAction(Action { req =>
    val content = """
                    |<!DOCTYPE html>
                    |<html>
                    |<head>
                    |  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
                    |  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                    |  <script>
                    |    document.domain = document.domain;
                    |    _sockjs_onload = function(){SockJS.bootstrap_iframe();};
                    |  </script>
                    |  <script src="""".stripMargin+script(req)+""""></script>
                    |</head>
                    |<body>
                    |  <h2>Don't panic!</h2>
                    |  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>
                    |</body>
                    |</html>
                  """.stripMargin
    val digest = MessageDigest.getInstance("MD5").digest(content.getBytes("UTF-8")).map(b => f"$b%02x").mkString
    if (req.headers.get(IF_NONE_MATCH).exists(_ == digest)) NotModified
    else Ok(content)
      .cached(31536000) // one year, as sockjs 0.3.3 specs
      .withHeaders(
        ETAG -> digest,
        CONTENT_TYPE -> "text/html; charset=UTF-8")
  })

  /**
   * info functionality
   */
  def info(websocket: Boolean, cookies: Boolean) = SockJSAction(Action { implicit req =>
    if (req.method == "OPTIONS") OptionsResult("OPTIONS", "GET")
    else Ok(Json.obj(
      "websocket" -> websocket,
      "cookie_needed" -> cookies,
      "origins" -> Json.arr("*:*"),
      "entropy" -> Random.nextInt(Int.MaxValue)
    )).enableCORS(req)
      .notcached
      .as("application/json; charset=UTF-8")
  })

}
