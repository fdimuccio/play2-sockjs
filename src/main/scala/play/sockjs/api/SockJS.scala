package play.sockjs.api

import scala.concurrent.Future
import scala.util.control.NonFatal

import akka.actor.{ActorRef, Props}
import akka.stream.scaladsl._

import play.api.http.websocket.CloseCodes
import play.api.libs.streams._
import play.api.libs.json._
import play.api.mvc._

import play.sockjs.api.Frame._

/**
  * A SockJS handler.
  */
trait SockJS extends Handler {

  /**
    * Execute the SockJS handler.
    *
    * The return value is either a result to reject the SockJS connection with,
    * or a flow that will handle the SockJS messages.
    */
  def apply(request: RequestHeader): Future[Either[Result, Flow[Frame, Frame, _]]]
}

object SockJS {

  def apply(f: RequestHeader => Future[Either[Result, Flow[Frame, Frame, _]]]): SockJS = {
    new SockJS {
      def apply(request: RequestHeader) = f(request)
    }
  }

  /**
    * Transforms SockJS message flows into flows of another type.
    *
    * The transformation may be more than just converting from one message to another, it may also produce messages, such
    * as close messages with an appropriate error code if the message can't be consumed.
    */
  trait MessageFlowTransformer[+In, -Out] { self =>

    /**
      * Transform the flow of In/Out messages into a flow of SockJS frames.
      */
    def transform(flow: Flow[In, Out, _]): Flow[Frame, Frame, _]

    /**
      * Contramap the out type of this transformer.
      */
    def contramap[NewOut](f: NewOut => Out): MessageFlowTransformer[In, NewOut] = {
      new MessageFlowTransformer[In, NewOut] {
        def transform(flow: Flow[In, NewOut, _]) = self.transform(flow map f)
      }
    }

    /**
      * Map the in type of this transformer.
      */
    def map[NewIn](f: In => NewIn): MessageFlowTransformer[NewIn, Out] = {
      new MessageFlowTransformer[NewIn, Out] {
        def transform(flow: Flow[NewIn, Out, _]) = self.transform(Flow[In] map f via flow)
      }
    }

    /**
      * Map the in type and contramap the out type of this transformer.
      */
    def map[NewIn, NewOut](f: In => NewIn, g: NewOut => Out): MessageFlowTransformer[NewIn, NewOut] = {
      new MessageFlowTransformer[NewIn, NewOut] {
        def transform(flow: Flow[NewIn, NewOut, _]) = self.transform(Flow[In] map f via flow map g)
      }
    }
  }

  /**
    * Defaults message transformers.
    */
  object MessageFlowTransformer {

    implicit val identityFrameFlowTransformer: MessageFlowTransformer[Frame, Frame] = {
      new MessageFlowTransformer[Frame, Frame] {
        def transform(flow: Flow[Frame, Frame, _]) = flow
      }
    }

    /**
      * Converts text message to/from Strings.
      */
    implicit val stringFrameFlowTransformer: MessageFlowTransformer[String, String] = {
      new MessageFlowTransformer[String, String] {
        def transform(flow: Flow[String, String, _]) = {
          AkkaStreams.bypassWith[Frame, Vector[String], Frame](Flow[Frame] collect {
            case Text(data) => Left(data)
          })(Flow[Vector[String]].mapConcat[String](identity) via flow map Text.apply)
        }
      }
    }

    /**
      * Converts messages to/from JsValue.
      */
    implicit val jsonMessageFlowTransformer: MessageFlowTransformer[JsValue, JsValue] = {
      def closeOnException[T](block: => T) = try {
        Left(block)
      } catch {
        case NonFatal(e) => Right(Frame.Close(CloseCodes.Unacceptable, "Unable to parse json message"))
      }

      new MessageFlowTransformer[JsValue, JsValue] {
        def transform(flow: Flow[JsValue, JsValue, _]) = {
          AkkaStreams.bypassWith[Frame, Vector[JsValue], Frame](Flow[Frame] collect {
            case Text(data) => closeOnException(data.map(Json.parse))
          })(Flow[Vector[JsValue]].mapConcat[JsValue](identity) via flow map (json => Text(Json.stringify(json))))
        }
      }
    }

    /**
      * Converts messages to/from a JSON high level object.
      *
      * If the input messages fail to be parsed, the SockJS will be closed with an 1003 close code and the parse error
      * serialised to JSON.
      */
    def jsonMessageFlowTransformer[In: Reads, Out: Writes]: MessageFlowTransformer[In, Out] = {
      jsonMessageFlowTransformer.map(json => Json.fromJson[In](json).fold(
        errors => throw SockJSCloseException(Close(CloseCodes.Unacceptable, Json.stringify(JsError.toJson(errors)))),
        identity
      ), out => Json.toJson(out))
    }
  }

  /**
    * Accepts a SockJS using the given flow.
    */
  def accept[In, Out](f: RequestHeader => Flow[In, Out, _])(implicit transformer: MessageFlowTransformer[In, Out]): SockJS = {
    acceptOrResult(f.andThen(flow => Future.successful(Right(flow))))
  }

  /**
    * Creates an action that will either accept the SockJS connection, using the given flow to handle the in and out stream, or
    * return a result to reject the request.
    */
  def acceptOrResult[In, Out](f: RequestHeader => Future[Either[Result, Flow[In, Out, _]]])(implicit transformer: MessageFlowTransformer[In, Out]): SockJS = {
    SockJS(request =>
      f(request).map(_.right.map(transformer.transform))(play.core.Execution.trampoline)
    )
  }

  /**
    * A function that, given an actor to send upstream messages to, returns actor props to create an actor to handle
    * the SockJS connection
    */
  type HandlerProps = ActorRef => Props
}