package play.sockjs.core
package transports

import scala.collection.immutable.Seq
import scala.util.control.Exception._

import org.apache.pekko.stream.scaladsl._

import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.http._
import play.api.http.websocket._
import play.api.libs.json._
import play.api.libs.streams.PekkoStreams
import play.sockjs.core.streams._
import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[sockjs] class WebSocket(server: Server) extends HeaderNames with Results {
  import server.settings

  /**
   * websocket framed transport
   */
  def framed = server.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.map { flow =>
        PekkoStreams.bypassWith[Message, Frame, Frame](
          Flow[Message].collect {
            case TextMessage(data) if data.nonEmpty =>
              (allCatch opt Json.parse(data)).map(_.validate[Vector[String]].fold(
                invalid => Right(CloseAbruptly),
                valid => Left(Text(valid))
              )).getOrElse(Right(CloseAbruptly))
          })(Flow.fromGraph(new CancellationSuppresser(flow)))
          .via(ProtocolFlow(settings.heartbeat))
          .via(new FrameBufferStage(settings.sessionBufferSize))
          .map(f => TextMessage(f.encode.utf8String))
      })(play.core.Execution.trampoline)
    }
  }

  /**
   * websocket unframed transport
   */
  def unframed = server.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.map { flow =>
        Flow[Message]
          .collect { case TextMessage(data) => Text(data) }
          .via(new CancellationSuppresser(flow))
          .via(ProtocolFlow.Stage)
          .mapConcat[Message] {
            case Frame.Text(data) => data.map(TextMessage.apply)
            case Frame.Close(code, reason) => Seq(CloseMessage(Some(code), reason))
            case _ => Seq.empty[Message]
          }
      })(play.core.Execution.trampoline)
    }
  }
}
