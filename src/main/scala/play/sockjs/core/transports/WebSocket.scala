package play.sockjs.core
package transports

import scala.util.control.Exception._
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._

import play.api.libs.concurrent.{Promise => PlayPromise}
import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.libs.json._
import play.api.http._

import play.sockjs.core.iteratee._
import play.sockjs.api._

private[sockjs] object WebSocket extends HeaderNames with Results {

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  /**
   * websocket sockjs framed transport
   */
  def sockjs(heartbeat: FiniteDuration) = handle { sockjs =>
    PlayWebSocket.tryAccept { req =>
      def bind[A, B](sockjs: SockJS[A, B]): Future[Either[Result, (Iteratee[String, Unit], Enumerator[String])]] = {
        sockjs.f(req).map(_.right.map { f =>
          val (itIN, enIN) = IterateeX.joined[String]
          val (itOUT, enOUT) = IterateeX.joined[String]
          val heartbeatK = Promise[Unit]() // A promise that when completed will kill the heartbeat
          @volatile var aborted = false
          f(enIN &> Enumeratee.mapInputFlatten[String] {
              case Input.El(payload) if payload.isEmpty => Enumerator.empty[A]
              case Input.El(payload) => (allCatch opt Json.parse(payload)).map(_.validate[Seq[String]].fold(
                invalid => Enumerator.empty[A], // abort connection?
                valid => Enumerator[A](valid.flatMap(allCatch opt sockjs.inFormatter.read(_)): _*))
              ).getOrElse { aborted = true; Enumerator.eof[A] }
              case Input.Empty => Enumerator.empty[A]
              case Input.EOF => aborted = true; Enumerator.eof[A]
            },
            Enumeratee.mapInputFlatten[B] {
              case Input.El(obj) => Enumerator[Frame](Frame.MessageFrame(sockjs.outFormatter.write(obj)))
              case Input.Empty => Enumerator.empty[Frame]
              case Input.EOF if aborted => Enumerator.eof[Frame]
              case Input.EOF => heartbeatK.trySuccess((): Unit); Enumerator[Frame](Frame.CloseFrame.GoAway) >>> Enumerator.eof
            } ><> Enumeratee.map(_.text) &>> itOUT)
          (itIN, Enumerator(Frame.OpenFrame.text) >>> Enumerator.fromCallback1(
            retriever = _ =>
              Future.firstCompletedOf(Seq(
                heartbeatK.future.map(_ => None),
                PlayPromise.timeout({
                  if (aborted || heartbeatK.isCompleted) None: Option[String]
                  else Some(Frame.HeartbeatFrame.text)
                }, heartbeat)))) >- enOUT)
        })
      }
      bind(sockjs)
    }
  }

  /**
   * raw websocket transport (no sockjs framing)
   */
  def raw = handle { sockjs =>
    def bind[A, B](sockjs: SockJS[A, B]): PlayWebSocket[A, B] = {
      PlayWebSocket(sockjs.f)(sockjs.inFormatter, sockjs.outFormatter)
    }
    bind(sockjs)
  }

  private def handle(f: SockJS[_, _] => Handler) = SockJSWebSocket { req =>
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

  private implicit def toFrameFormatter[A](fmt: SockJS.MessageFormatter[A]): PlayWebSocket.FrameFormatter[A] = {
    PlayWebSocket.FrameFormatter.stringFrame.transform(fmt.write, fmt.read)
  }
}
