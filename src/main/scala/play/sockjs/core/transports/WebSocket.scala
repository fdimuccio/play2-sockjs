package play.sockjs.core
package transports

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.Exception._

import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.libs.json._
import play.api.http._
import play.api.libs.concurrent.{Promise => PlayPromise}

import play.sockjs.api._

private[sockjs] object WebSocket extends HeaderNames with Results {

  implicit val ec = play.core.Execution.internalContext

  /**
   * websocket sockjs framed transport
   */
  def sockjs(heartbeat: FiniteDuration) = handle { sockjs =>
    PlayWebSocket.using { req =>
      def bind[A](sockjs: SockJS[A]): (Iteratee[String, Unit], Enumerator[String]) = {
        val (itIN, enIN) = IterateeX.joined[String]
        val (itOUT, enOUT) = IterateeX.joined[String]
        val heartbeatK = Promise[Unit]() // A promise that when completed will kill the heartbeat
        @volatile var aborted = false
        sockjs.f(req)(
          enIN &> Enumeratee.mapInputFlatten[String] {
            case Input.El(payload) if payload.isEmpty => Enumerator.enumInput[A](Input.Empty)
            case Input.El(payload) => (allCatch opt Json.parse(payload)).map(_.validate[Seq[String]].fold(
              invalid => Enumerator.enumInput[A](Input.Empty), // abort connection?
              valid   => Enumerator[A](valid.flatMap(allCatch opt sockjs.formatter.read(_)):_*))
            ).getOrElse { aborted = true; Enumerator.eof[A] }
            case Input.Empty => Enumerator.enumInput[A](Input.Empty)
            case Input.EOF => aborted = true; Enumerator.eof[A]
          },
          Enumeratee.mapInputFlatten[A] {
            case Input.El(obj) => Enumerator[Frame](Frame.MessageFrame(sockjs.formatter.write(obj)))
            case Input.Empty => Enumerator.enumInput[Frame](Input.Empty)
            case Input.EOF if aborted => Enumerator.eof[Frame]
            case Input.EOF => heartbeatK.trySuccess(); Enumerator[Frame](Frame.CloseFrame.GoAway) >>> Enumerator.eof
          } ><> Enumeratee.map(_.text) &>> itOUT)
        (itIN, Enumerator(Frame.OpenFrame.text) >>> Enumerator.fromCallback1(
          retriever = _ =>
            Future.firstCompletedOf(Seq(
              heartbeatK.future.map(_ => None),
              PlayPromise.timeout({
                if (aborted || heartbeatK.isCompleted) None: Option[String]
                else Some(Frame.HeartbeatFrame.text)
              }, heartbeat)))) >- enOUT)
      }
      bind(sockjs)
    }
  }

  /**
   * raw websocket transport (no sockjs framing)
   */
  def raw = handle { sockjs =>
    PlayWebSocket.using { req =>
      def bind[A](sockjs: SockJS[A]): (Iteratee[String, Unit], Enumerator[String]) = {
        val (itIN, enIN) = IterateeX.joined[String]
        val (itOUT, enOUT) = IterateeX.joined[String]
        sockjs.f(req)(
          enIN &> Enumeratee.mapInputFlatten[String] {
            case Input.El(payload) if payload.isEmpty => Enumerator.enumInput[A](Input.Empty)
            case Input.El(payload) => (allCatch opt sockjs.formatter.read(payload))
              .map(Enumerator[A](_)).getOrElse(Enumerator.enumInput[A](Input.Empty))
            case Input.Empty => Enumerator.enumInput[A](Input.Empty)
            case Input.EOF => Enumerator.eof[A]
          },
          Enumeratee.mapInputFlatten[A] {
            case Input.El(obj) => Enumerator(sockjs.formatter.write(obj))
            case Input.Empty => Enumerator.enumInput[String](Input.Empty)
            case Input.EOF => Enumerator.eof[String]
          } &>> itOUT)
        (itIN, enOUT)
      }
      bind(sockjs)
    }
  }

  private def handle(f: SockJS[_] => Handler) = SockJSWebSocket { req =>
    (if (req.method == "GET") {
      if (req.headers.get(UPGRADE).exists(_.equalsIgnoreCase("websocket"))) {
        if (req.headers.get(CONNECTION).exists(_.equalsIgnoreCase("upgrade"))) {
          Right(SockJSTransport(f))
        } else Left(BadRequest("\"Connection\" must be \"Upgrade\"."))
      } else Left(BadRequest("'Can \"Upgrade\" only to \"WebSocket\".'"))
    } else Left(MethodNotAllowed.withHeaders(ALLOW -> "GET")))
    .fold(result => SockJSTransport(_ => Action(result)), identity)
  }

}
