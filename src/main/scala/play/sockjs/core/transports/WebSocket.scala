package play.sockjs.core
package transports

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

import scala.collection.immutable.Seq
import scala.util.control.Exception._
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import play.api.mvc._
import play.api.mvc.{WebSocket => PlayWebSocket}
import play.api.http._
import play.api.http.websocket._
import play.api.libs.json._
import play.api.libs.streams.AkkaStreams
import play.sockjs.core.streams._
import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[sockjs] class WebSocket(server: Server) extends HeaderNames with Results {
  import server.settings

  import play.api.libs.iteratee.Execution.Implicits.trampoline

  /**
   * websocket framed transport
   */
  def framed = server.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        AkkaStreams.bypassWith[Message, Frame, Frame](
          Flow[Message].collect {
            case TextMessage(data) if data.nonEmpty =>
              (allCatch opt Json.parse(data)).map(_.validate[Vector[String]].fold(
                invalid => Right(CloseAbruptly),
                valid => Left(Text(valid))
              )).getOrElse(Right(CloseAbruptly))
          })(Flow[Frame].via(new CancellationSuppresser(flow)))
          .via(ProtocolFlow(settings.heartbeat))
          .via(new FrameBufferStage(settings.sessionBufferSize))
          .map(f => TextMessage(f.encode.utf8String))
      })
    }
  }

  /**
   * websocket unframed transport
   */
  def unframed = server.websocket { sockjs =>
    PlayWebSocket.acceptOrResult { req =>
      sockjs(req).map(_.right.map { flow =>
        Flow[Message]
          .collect { case TextMessage(data) => Text(data) }
          .via(Flow[Frame].via(new CancellationSuppresser(flow)))
          .via(ProtocolFlow.Stage)
          .mapConcat[Message] {
            case Frame.Text(data) => data.map(TextMessage.apply)
            case Frame.Close(code, reason) => Seq(CloseMessage(Some(code), reason))
            case _ => Seq.empty[Message]
          }
      })
    }
  }
}
