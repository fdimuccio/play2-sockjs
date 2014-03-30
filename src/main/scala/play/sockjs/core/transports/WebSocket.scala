package play.sockjs.core
package transports

import scala.util.control.Exception._
import scala.concurrent.stm.Ref

import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.libs.json._
import play.api.http._
import play.core.Execution.Implicits.internalContext

import play.sockjs.api._

object WebSocket extends HeaderNames with Results {

  /**
   * websocket sockjs framed transport
   */
  def sockjs = SockJSTransport { sockjs =>
    PlayWebSocket.using { req =>
      def bind[A](sockjs: SockJS[A]): (Iteratee[String, Unit], Enumerator[String]) = {
        val (itIN, enIN) = IterateeX.joined[String]
        val (itOUT, enOUT) = IterateeX.joined[String]
        val aborted = Ref(false)
        sockjs.f(req)(
          enIN &> Enumeratee.mapInputFlatten[String] {
            case Input.El(payload) if payload.isEmpty => Enumerator.enumInput[A](Input.Empty)
            case Input.El(payload) => (allCatch opt Json.parse(payload)).map(_.validate[Seq[String]].fold(
              invalid => Enumerator.enumInput[A](Input.Empty), // abort connection?
              valid   => Enumerator[A](valid.flatMap(allCatch opt sockjs.formatter.read(_)):_*))
            ).getOrElse { aborted.single.set(true); Enumerator.eof[A] }
            case Input.Empty => Enumerator.enumInput[A](Input.Empty)
            case Input.EOF => Enumerator.eof[A]
          },
          Enumeratee.mapInputFlatten[A] {
            case Input.El(obj) => Enumerator[Frame](Frame.MessageFrame(sockjs.formatter.write(obj)))
            case Input.Empty => Enumerator.enumInput[Frame](Input.Empty)
            case Input.EOF if aborted.single() => Enumerator.eof[Frame]
            case Input.EOF => Enumerator[Frame](Frame.CloseFrame.GoAway) >>> Enumerator.eof
          } ><> Enumeratee.heading(Enumerator(Frame.OpenFrame)) ><> Frame.toText &>> itOUT)
        (itIN, enOUT)
      }
      bind(sockjs)
    }
  }

  /**
   * raw websocket transport (no sockjs framing)
   */
  def raw = SockJSTransport { sockjs =>
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

}
