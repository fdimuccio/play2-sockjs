package play.sockjs.core

import java.util.concurrent.ConcurrentHashMap

import scala.util.control.Exception._
import scala.concurrent.Future
import scala.collection.immutable.Seq

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString

import play.core.parsers.FormUrlEncodedParser
import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.libs.json._
import play.api.http.{HttpChunk, HeaderNames, HttpEntity}

import play.sockjs.api._

/**
 * SockJS transports builder
 */
private[sockjs] class Transport(materializer: Materializer) extends HeaderNames with Results {
  import Transport._

  private[this] val playInternalEC = play.core.Execution.internalContext
  private[this] val trampolineEC = play.api.libs.iteratee.Execution.trampoline

  private[this] val sessions = new ConcurrentHashMap[String, Any]()

  /**
    * Endpoint that connects to the `send` method of SockJS client (xhr_send and jsonp_send)
    */
  def send(f: RequestHeader => Result): String => SockJSHandler =
    sessionID => SockJSHandler { (_, sockjs) =>
      Action.async(parse.raw(Int.MaxValue)) { implicit req =>
        def tryParse(f: => JsValue) = {
          (allCatch either f).left.map(_ => "Broken JSON encoding.")
        }
        def parseFormUrlEncoded(data: ByteString) = {
          val query = FormUrlEncodedParser.parse(data.utf8String, "UTF-8")
          for {
            d <- query.get("d").flatMap(_.headOption.filter(!_.isEmpty)).toRight("Payload expected.").right
            json <- tryParse(Json.parse(d)).right
          } yield json
        }
        // Request body should be taken as raw and forced to be a UTF-8 string, otherwise
        // if no charset header is specified Play will default to ISO-8859-1, messing up unicode encoding
        ((req.contentType.getOrElse(""), req.body.asBytes().getOrElse(ByteString.empty)) match {
          case ("application/x-www-form-urlencoded", data) => parseFormUrlEncoded(data)
          case (_, data) if data.nonEmpty => tryParse(Json.parse(data.toArray))
          case _ => Left("Payload expected.")
        }).fold(
          error => Future.successful(InternalServerError(error)),
          json => json.validate[Seq[String]].fold(
            invalid => Future.successful(InternalServerError("Payload expected.")),
            payload => sessions.get(sessionID) match {
              case session: streams.Session => session.push(payload).map { accepted =>
                if (accepted) f(req).withCookies(sockjs.settings.cookies.map(f => List(f(req))).getOrElse(Nil): _*)
                else NotFound //FIXME: proper error message
              }(playInternalEC)
              case _ => Future.successful(NotFound)
            }))
      }
    }

  /**
    * HTTP polling transport builder.
    */
  val polling = http(1) _

  /**
    * HTTP streaming transport builder.
    */
  val streaming = http(-1) _

  /**
    * HTTP generic transport builder.
    */
  private def http(quota: Long)(f: SockJSRequest => Future[Result]): String => SockJSHandler =
    sessionID => SockJSHandler { (_, sockjs) =>
      val cfg = sockjs.settings
      import cfg._
      Action.async { req =>
        f(new SockJSRequest(req) {
          def bind(ctype: String)(f: Source[Frame, _] => Source[ByteString, _]) = {
            (sessions.putIfAbsent(sessionID, InProgress) match {

              // There is already a session being build with the provided sessionID
              case InProgress =>
                Future.successful(Right(Source.single(Frame.CloseFrame.AnotherConnectionStillOpen)))

              // Session resumed
              case session: streams.Session =>
                Future.successful(Right(session.source))

              // New session
              case _ =>
                sockjs(req).map(_.right.map { handler =>
                  val (session, binding) =
                    handler.joinMat(
                      streams.Session.flow(
                        heartbeat,
                        sessionTimeout,
                        if (quota > 0) quota else streamingQuota,
                        sendBufferSize,
                        sessionBufferSize
                      )
                    )(Keep.right).run()(materializer)
                  sessions.put(sessionID, session)
                  binding.onComplete { case _ => sessions.remove(sessionID) }(trampolineEC)
                  session.source
                })(playInternalEC)

            }).map {

              case Right(source) =>
                Ok.sendEntity(
                  if (req.version.contains("1.0")) HttpEntity.Streamed(f(source), None, Some(ctype))
                  else HttpEntity.Chunked(f(source).map(HttpChunk.Chunk), Some(ctype))
                ).notcached.withCookies(cfg.cookies.map(f => List(f(req))).getOrElse(Nil):_*)

              case Left(result) => result

            }(playInternalEC)
          }
        })
      }
    }

  /**
    * WebSocket transport builder.
    */
  def websocket(f: SockJS => Handler): SockJSHandler = SockJSHandler { (req, sockjs) =>
    (if (sockjs.settings.websocket) {
      if (req.method == "GET") {
        if (req.headers.get(UPGRADE).exists(_.equalsIgnoreCase("websocket"))) {
          if (req.headers.get(CONNECTION).exists(_.toLowerCase.contains("upgrade"))) {
            Right(f(sockjs))
          } else Left(BadRequest("\"Connection\" must be \"Upgrade\"."))
        } else Left(BadRequest("'Can \"Upgrade\" only to \"WebSocket\".'"))
      } else Left(MethodNotAllowed.withHeaders(ALLOW -> "GET"))
    } else Left(NotFound))
      .fold(Action(_), identity)
  }
}

private[sockjs] object Transport {

  val P = """/([^/.]+)/([^/.]+)/([^/.]+)""".r
  def unapply(path: String): Option[(String, String)] = path match {
    case P(serverID, sessionID, transport) => Some(sessionID -> transport)
    case _ => None
  }

  /**
    * Needed to signal that a session is being build
    */
  private case object InProgress
}
