package play.sockjs.core
package transports

import scala.util.control.Exception._
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.collection.immutable.Seq

import akka.stream.scaladsl._

import play.api.libs.concurrent.{Promise => PlayPromise}
import play.api.libs.iteratee._
import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.libs.json._
import play.api.http._
import play.api.http.websocket.{Message => PlayMessage, CloseCodes}

import play.sockjs.api._

import play.sockjs.api.Frame._

private[sockjs] object WebSocket extends HeaderNames with Results {

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  /**
   * websocket sockjs framed transport
   */
  def sockjs(heartbeat: FiniteDuration) = handle { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        Flow[String, String]() { implicit builder =>
          import FlowGraph.Implicits._

          val decoder = builder.add(
            Flow[String].mapConcat { data =>
              if (data.nonEmpty) {
                (allCatch opt Json.parse(data)).map(_.validate[Seq[String]].fold(
                  invalid => throw SockJSCloseException(CloseFrame(CloseCodes.Unacceptable, "Unable to parse json message")),
                  valid => valid
                )).getOrElse(throw SockJSCloseException(CloseFrame(CloseCodes.Unacceptable, "Unable to parse json message")))
              } else Seq.empty[String]
            })
          val handler = builder.add(flow)
          val concat = builder.add(Concat[Frame]())
          val encoder = builder.add(Flow[Frame].map(_.encode))

          Source.single(Frame.OpenFrame) ~> concat.in(0)
          decoder ~> handler /*TODO: ~> Flow.keepAlive(heartbeat)*/ ~> concat.in(1)
          concat ~> encoder

          (decoder.inlet, encoder.outlet)
        }
      })
    }
  }

  /**
   * raw websocket transport (no sockjs framing)
   */
  def raw = handle { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        flow.mapConcat[String] {
          case Frame.MessageFrame(data) => data
          case _ => Seq.empty
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
}
