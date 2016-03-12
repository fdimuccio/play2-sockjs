package play.sockjs.core

import java.util.concurrent.ConcurrentHashMap

import scala.util.control.NonFatal
import scala.util.control.Exception._
import scala.concurrent.Future

import akka.stream.{Fusing, Materializer, QueueOfferResult}
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString

import play.core.parsers.FormUrlEncodedParser
import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.libs.json._
import play.api.http.{HeaderNames, HttpChunk, HttpEntity}
import play.sockjs.core.streams.SessionFlow
import play.sockjs.api._
import play.sockjs.api.Frame._

/**
 * SockJS server
 */
private[sockjs] final class Server(val settings: SockJSSettings, materializer: Materializer)
  extends HeaderNames with Results {
  import Server._

  private[this] val playInternalEC = play.core.Execution.internalContext
  private[this] val trampolineEC = play.api.libs.iteratee.Execution.trampoline

  // Fusing disabled since Queue.source has a bug when reused
  private[this] def fusedHttpPolling = /*Fusing.aggressive(*/SessionFlow(
    settings.heartbeat,
    settings.sessionTimeout,
    1,
    settings.sendBufferSize,
    settings.sessionBufferSize
  )

  // Fusing disabled since Queue.source has a bug when reused
  private[this] def fusedHttpStreaming = /*Fusing.aggressive(*/SessionFlow(
    settings.heartbeat,
    settings.sessionTimeout,
    settings.streamingQuota,
    settings.sendBufferSize,
    settings.sessionBufferSize
  )

  private[this] val sessions = new ConcurrentHashMap[String, Any]()

  /**
    * Used by the `send` method of SockJS client (xhr_send and jsonp_send)
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
          case (_, data) if data.nonEmpty => tryParse(Json.parse(data.iterator.asInputStream))
          case _ => Left("Payload expected.")
        }).fold(
          error => Future.successful(InternalServerError(error)),
          json => json.validate[Vector[String]].fold(
            invalid => Future.successful(InternalServerError("Payload expected.")),
            payload => sessions.get(sessionID) match {
              case session: streams.Session =>
                val result =
                  if (payload.nonEmpty) session.push(Text(payload))
                  else Future.successful(QueueOfferResult.Enqueued)
                result.map {
                  case QueueOfferResult.Enqueued =>
                    f(req).withCookies(settings.cookies.map(f => List(f(req))).getOrElse(Nil): _*)
                  case _ =>
                    NotFound //FIXME: proper error message
                }(trampolineEC)

              case _ => Future.successful(NotFound)
            }))
      }
    }

  /**
    * HTTP polling transport builder.
    */
  val polling = http(streaming = false) _

  /**
    * HTTP streaming transport builder.
    */
  val streaming = http(streaming = true) _

  /**
    * HTTP generic transport builder.
    */
  private def http(streaming: Boolean)(f: SockJSRequest => Future[Result]): String => SockJSHandler =
    sessionID => SockJSHandler { (_, sockjs) =>
      Action.async { req =>
        f(new SockJSRequest(req) {
          def bind(ctype: String)(f: Source[ByteString, _] => Source[ByteString, _]) = {
            (sessions.putIfAbsent(sessionID, Initializing) match {

              // Session resumed
              case session: streams.Session =>
                Future.successful(Right(session.source))

              // New session
              case null =>
                val handler =
                  try sockjs(req)
                  catch {
                    case NonFatal(e) => Future.failed(e)
                  }

                handler.map(_.right.map { flow =>
                  val (session, binding) =
                    flow.joinMat(
                      if (streaming) fusedHttpStreaming else fusedHttpPolling
                    )(Keep.right).run()(materializer)
                  sessions.put(sessionID, session)
                  //TODO: log failure
                  binding.onComplete { _ =>
                    sessions.remove(sessionID)
                  }(trampolineEC)
                  session.source
                })(playInternalEC).recover {

                  case NonFatal(e) =>
                    //TODO: log failure
                    sessions.remove(sessionID)
                    Right(Source.single(Close.ConnectionInterrupted.encode))
                }(trampolineEC)

              // There is already a session being initialized with the provided sessionID
              case Initializing =>
                Future.successful(Right(Source.single(Close.AnotherConnectionStillOpen.encode)))

            }).map {

              case Right(source) =>
                Ok.sendEntity(
                  if (req.version.contains("1.0")) HttpEntity.Streamed(f(source), None, Some(ctype))
                  else HttpEntity.Chunked(f(source).map(HttpChunk.Chunk), Some(ctype))
                ).notcached.withCookies(settings.cookies.map(f => List(f(req))).getOrElse(Nil):_*)

              case Left(result) => result

            }(trampolineEC)
          }
        })
      }
    }

  /**
    * WebSocket transport builder.
    */
  def websocket(f: SockJS => Handler): SockJSHandler = SockJSHandler { (req, sockjs) =>
    if (!settings.websocket)
      Action(NotFound)
    else if (req.method != "GET")
      Action(MethodNotAllowed.withHeaders(ALLOW -> "GET"))
    else if (!req.headers.get(UPGRADE).exists(_.equalsIgnoreCase("websocket")))
      Action(BadRequest("'Can \"Upgrade\" only to \"WebSocket\".'"))
    else if (!req.headers.get(CONNECTION).exists(_.toLowerCase.contains("upgrade")))
      Action(BadRequest("\"Connection\" must be \"Upgrade\"."))
    else
      f(sockjs)
  }
}

private[sockjs] object Server {

  /**
    * Signals that a session is being initialized
    */
  private case object Initializing
}
