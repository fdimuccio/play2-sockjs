package play.sockjs.core
package transports

import scala.collection.immutable.Seq
import scala.util.control.Exception._

import akka.stream.scaladsl._

import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.http._
import play.api.http.websocket._
import play.api.libs.json._
import play.api.libs.streams.AkkaStreams

import play.sockjs.core.streams._
import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[sockjs] class WebSocket(transport: Transport) extends HeaderNames with Results {

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  /**
   * websocket framed transport
   */
  def sockjs = transport.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        AkkaStreams.bypassWith[String, Seq[String], Frame](
          Flow[String].map { data =>
            if (data.nonEmpty) {
              (allCatch opt Json.parse(data)).map(_.validate[Seq[String]].fold(
                invalid => Right(CloseAbruptly),
                valid => Left(valid)
              )).getOrElse(Right(CloseAbruptly))
            } else Left(Seq.empty[String])
          }
        )(Flow[Seq[String]].mapConcat[String](identity) via flow)
          .via(Protocol(transport.cfg.heartbeat, _.encode))
      })
    }
  }

  /**
   * websocket unframed transport
   */
  def raw = transport.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        Flow[Message]
          .collect { case TextMessage(data) => data }
          .via(flow)
          .via(Protocol(transport.cfg.heartbeat, identity))
          .mapConcat[Message] {
            case Frame.MessageFrame(data) => data.map(TextMessage.apply)
            case Frame.CloseFrame(code, reason) => Seq(CloseMessage(Some(code), reason))
            case _ => Seq.empty[Message]
          }
      })
    }
  }
}
