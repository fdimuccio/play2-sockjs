package play.sockjs.core

import scala.util.control.Exception._
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.actor.ActorRef
import akka.pattern.ask

import play.core.parsers.FormUrlEncodedParser
import play.api.mvc._
import play.api.mvc.BodyParsers._
import play.api.mvc.Results._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.http.Writeable
import play.core.Execution.Implicits.internalContext

import play.sockjs.api._
import play.sockjs.core.actors._
import play.sockjs.core.actors.SockJSActor.SessionMasterRef

/**
 * SockJS transport helper
 */
private[sockjs] case class Transport(f: SessionMasterRef => (String, SockJSSettings) => SockJSHandler) {

  def apply(sessionID: String)(implicit sessionMaster: SessionMasterRef, settings: SockJSSettings) = {
    f(sessionMaster)(sessionID, settings)
  }

}

private[sockjs] object Transport {

  implicit val defaultTimeout = akka.util.Timeout(5 seconds) //TODO: make it configurable?

  val P = """/([^/.]+)/([^/.]+)/([^/.]+)""".r
  def unapply(path: String): Option[(String, String)] = path match {
    case P(serverID, sessionID, transport) => Some(sessionID -> transport)
    case _ => None
  }

  case class Res[T](body: Enumerator[T], cors: Boolean = false)(implicit val writeable: Writeable[T])

  /**
   * The session the client is bound to
   */
  trait Session {

    /**
     * Bind this session to the SessionMaster. The enumerator provided must be used
     * to write messages to the client
     */
    def bind[A](f: Enumerator[Frame] => Res[A]): Future[SimpleResult]

  }

  def Send(ok: RequestHeader => SimpleResult, ko: => SimpleResult)= Transport { sessionMaster => (sessionID, settings) =>
    import settings._
    SockJSAction(Action.async(parse.raw(Int.MaxValue)) { implicit req =>
      def parsePlainText(txt: String) = {
        (allCatch either Json.parse(txt)).left.map(_ => "Broken JSON encoding.")
      }
      def parseFormUrlEncoded(data: String) = {
        val query = FormUrlEncodedParser.parse(data, req.charset.getOrElse("utf-8"))
        for {
          d <- query.get("d").flatMap(_.headOption.filter(!_.isEmpty)).toRight("Payload expected.").right
          json <- parsePlainText(d).right
        } yield json
      }
      // Request body should be parsed as raw and forced to be a UTF-8 string, otherwise
      // if no charset header is specified Play will default to ISO-8859-1, messing up unicode encoding
      ((req.contentType.getOrElse(""), new String(req.body.asBytes().getOrElse(Array()), "UTF-8")) match {
        case ("application/x-www-form-urlencoded", data) => parseFormUrlEncoded(data)
        case (_, txt) if !txt.isEmpty => parsePlainText(txt)
        case _ => Left("Payload expected.")
      }).fold(
        error => Future.successful(InternalServerError(error)),
        json => json.validate[Seq[String]].fold(
          invalid => Future.successful(InternalServerError("Payload expected.")),
          payload => (sessionMaster ? SessionMaster.Send(sessionID, payload)).map {
            case SessionMaster.Ack => ok(req).withCookies(cookies.map(f => List(f(req))).getOrElse(Nil):_*)
            case SessionMaster.Error => ko
          }))
    })
  }

  /**
   * HTTP polling transport
   */
  val Polling = Http(Some(1L)) _

  /**
   * HTTP streaming transport
   */
  val Streaming = Http(None) _

  /**
   * HTTP transport that emulate websockets. Provides method to bind this
   * transport session to the SessionMaster.
   */
  def Http(quota: Option[Long])(f: (RequestHeader, Session) => Future[SimpleResult]) = Transport { sessionMaster => (sessionID, settings) =>
    import settings._
    SockJSTransport { sockjs =>
      Action.async { req =>
        f(req, new Session {
          def bind[A](f: Enumerator[Frame] => Res[A]): Future[SimpleResult] = {
            (sessionMaster ? SessionMaster.Get(sessionID)).map {
              case SessionMaster.SessionOpened(session) => session.bind(req, sockjs)
              case SessionMaster.SessionResumed(session) => session
            }.flatMap(_.connect(heartbeat, sessionTimeout, quota.getOrElse(streamingQuota)).map {
              case actors.Session.Connected(enumerator) =>
                val res = f(enumerator)
                val status =
                  if (req.version.contains("1.0")) Ok.feed(res.body)(res.writeable)
                  else Ok.chunked(res.body)(res.writeable)
                (if (res.cors) status.enableCORS(req) else status)
                  .notcached
                  .withCookies(cookies.map(f => List(f(req))).getOrElse(Nil):_*)
            })
          }
        })
      }
    }
  }

}
