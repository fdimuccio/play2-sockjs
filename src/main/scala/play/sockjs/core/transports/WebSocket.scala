package play.sockjs.core
package transports

import scala.concurrent.duration._
import scala.collection.immutable.Seq
import scala.util.control.Exception._

import akka.stream.scaladsl._
import akka.stream.stage._

import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.http._
import play.api.http.websocket._
import play.api.libs.json._
import play.api.libs.streams.AkkaStreams

import play.sockjs.api._
import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[sockjs] object WebSocket extends HeaderNames with Results {

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  /**
   * websocket framed transport
   */
  def sockjs(heartbeat: FiniteDuration) = handle { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        AkkaStreams.bypassWith[String, Seq[String], Frame](decoder)(Flow[Seq[String]].mapConcat[String](identity) via flow)
          //TODO: .via(Flow.keepAlive(heartbeat))
          .transform(encoder(_.encode))
      })
    }
  }

  /**
   * websocket unframed transport
   */
  def raw = handle { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        Flow[Message]
          .collect { case TextMessage(data) => data }
          .via(flow)
          .transform(encoder(identity))
          .mapConcat[Message] {
            case Frame.MessageFrame(data) => data.map(TextMessage.apply)
            case Frame.CloseFrame(code, reason) => Seq(CloseMessage(Some(code), reason))
            case _ => Seq.empty[Message]
          }
      })
    }
  }

  private def handle(f: SockJS => Handler) = SockJSWebSocket { req =>
    (if (req.method == "GET") {
      if (req.headers.get(UPGRADE).exists(_.equalsIgnoreCase("websocket"))) {
        if (req.headers.get(CONNECTION).exists(_.toLowerCase.contains("upgrade"))) {
          Right(SockJSTransport(f))
        } else Left(BadRequest("\"Connection\" must be \"Upgrade\"."))
      } else Left(BadRequest("'Can \"Upgrade\" only to \"WebSocket\".'"))
    } else Left(MethodNotAllowed.withHeaders(ALLOW -> "GET"))).fold(
      result => SockJSTransport(sockjs => Action(result)),
      transport => transport)
  }

  private val decoder = Flow[String].map { data =>
    if (data.nonEmpty) {
      (allCatch opt Json.parse(data)).map(_.validate[Seq[String]].fold(
        invalid => Right(CloseAbruptly),
        valid => Left(valid)
      )).getOrElse(Right(CloseAbruptly))
    } else Left(Seq.empty[String])
  }

  private def encoder[T](enc: Frame => T): () => Stage[Frame, T] = () => new DetachedStage[Frame, T] {
    var buffer = Vector[Frame](Frame.OpenFrame)
    var closed = false

    def onPush(frame: Frame, ctx: DetachedContext[T]) = {
      if (buffer.nonEmpty || !ctx.isHoldingDownstream) {
        buffer :+= frame
        ctx.holdUpstream()
      } else frame match {
        case CloseAbruptly =>
          ctx.finish()
        case close: CloseFrame =>
          closed = true
          ctx.holdUpstreamAndPush(enc(close))
        case other =>
          ctx.pushAndPull(enc(other))
      }
    }

    def onPull(ctx: DetachedContext[T]) = {
      if (closed) ctx.finish()
      else if (buffer.nonEmpty) {
        val (frame, rest) = buffer.splitAt(1)
        buffer = rest
        frame(0) match {
          case CloseAbruptly =>
            ctx.finish()
          case close: CloseFrame =>
            ctx.pushAndFinish(enc(close))
          case other =>
            if (ctx.isHoldingUpstream) ctx.pushAndPull(enc(other))
            else ctx.push(enc(other))
        }
      } else ctx.holdDownstream()
    }

    override def onUpstreamFinish(ctx: DetachedContext[T]) = {
      buffer :+= Frame.CloseFrame.GoAway
      ctx.absorbTermination()
    }
  }
}
