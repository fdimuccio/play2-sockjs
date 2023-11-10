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
class Utils(server: Server) extends HeaderNames with Results {
  import server.settings

  /**
   * greeting
   */
  def greet = server.action {
    Ok("Welcome to SockJS!\n").as("text/plain; charset=UTF-8")
  }

  /**
   * iframe page (needed by iframe transports)
   */
  def iframe = server.action { (req: Request[AnyContent]) =>
    val content = """|<!DOCTYPE html>
                     |<html>
                     |<head>
                     |  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
                     |  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                     |  <script src="""".stripMargin+settings.scriptSRC(req)+""""></script>
                     |  <script>
                     |    document.domain = document.domain;
                     |    SockJS.bootstrap_iframe();
                     |  </script>
                     |</head>
                     |<body>
                     |  <h2>Don't panic!</h2>
                     |  <p>This is a SockJS hidden iframe. It's used for cross domain magic.</p>
                     |</body>
                     |</html>""".stripMargin
    val etag = MessageDigest.getInstance("MD5")
      .digest(content.getBytes("UTF-8")).map(b => f"$b%02x").mkString("\"","","\"")
    if (req.headers.get(IF_NONE_MATCH).contains(etag)) NotModified
    else Ok(content)
      .cached(31536000) // 31536000 seconds, that is one year, as sockjs 0.3.3 specs
      .withHeaders(ETAG -> etag)
      .as("text/html; charset=UTF-8")
  }


  /**
   * info functionality
   */
  def info = server.action { implicit req: Request[AnyContent] =>
    if (req.method == "OPTIONS") OptionsResult("OPTIONS", "GET")
    else Ok(Json.obj(
      "websocket" -> settings.websocket,
      "cookie_needed" -> settings.cookies.isDefined,
      "origins" -> Json.arr("*:*"),
      "entropy" -> Random.nextInt(Int.MaxValue)
    )).enableCORS(req)
      .notcached
      .as("application/json; charset=UTF-8")
  }
}
