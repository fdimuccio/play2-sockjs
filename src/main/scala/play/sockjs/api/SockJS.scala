package play.sockjs.api

import play.api.Application
import play.api.libs.concurrent.Akka

import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.control.NonFatal

import akka.actor.{ActorRef, Props}
import akka.stream.scaladsl._
import akka.stream.Materializer

import play.api.http.websocket.CloseCodes
import play.api.libs.iteratee._
import play.api.libs.streams._
import play.api.libs.json._
import play.api.mvc._

import play.core.Execution.Implicits.internalContext

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

  @deprecated("Use MessageFormatter instead", "0.5.0")
  type MessageFormatter[A] = MessageFlowTransformer[A, A]

  /**
    * Defaults (old) message formatters.
    */
  object MessageFormatter {

    @deprecated("Use MessageFlowTransformer.stringMessageFlowTransformer instead", "0.5.0")
    implicit val textMessage: MessageFormatter[String] = MessageFlowTransformer.stringFrameFlowTransformer

    @deprecated("Use MessageFlowTransformer.jsonMessageFlowTransformer instead", "0.5.0")
    implicit val jsonMessage: MessageFormatter[JsValue] = MessageFlowTransformer.jsonMessageFlowTransformer

  }

  /**
    * A function that, given an actor to send upstream messages to, returns actor props to create an actor to handle
    * the SockJS connection
    */
  type HandlerProps = ActorRef => Props

  /**
    * Accepts a SockJS connection using the given inbound/outbound channels.
    */
  @deprecated("Use accept with an Akka streams flow instead", "0.5.0")
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit transformer: MessageFlowTransformer[A, A]): SockJS = {
    tryAccept[A](f.andThen(handler => Future.successful(Right(handler))))
  }

  /**
    * Creates a SockJS handler that will adapt the incoming stream and send it back out.
    */
  @deprecated("Use accept with an Akka streams flow instead", "0.5.0")
  def adapter[A](f: RequestHeader => Enumeratee[A, A])(implicit transformer: MessageFlowTransformer[A, A]): SockJS = {
    using(f.andThen { enumeratee =>
      val (iteratee, enumerator) = Concurrent.joined[A]
      (enumeratee &> iteratee, enumerator)
    })
  }

  /**
    * Creates a SockJS handler that will either reject the connection with the given result, or will be handled by the given
    * inbound and outbound channels, asynchronously
    */
  @deprecated("Use acceptOrResult with an Akka streams flow instead", "0.5.0")
  def tryAccept[A](f: RequestHeader => Future[Either[Result, (Iteratee[A, _], Enumerator[A])]])(implicit transformer: MessageFlowTransformer[A, A]): SockJS = {
    acceptOrResult[A, A](f.andThen(_.map(_.right.map {
      case (iteratee, enumerator) =>
        // Play 2.4 and earlier only closed the WebSocket if the enumerator specifically fed EOF. So, you could
        // return an empty enumerator, and it would never close the socket. Converting an empty enumerator to a
        // publisher however will close the socket, so, we need to ensure the enumerator only completes if EOF
        // is sent.
        val enumeratorCompletion = Promise[Enumerator[A]]()
        val nonCompletingEnumerator = onEOF(enumerator, () => {
          enumeratorCompletion.success(Enumerator.empty)
        }) >>> Enumerator.flatten(enumeratorCompletion.future)
        val publisher = Streams.enumeratorToPublisher(nonCompletingEnumerator)
        val (subscriber, _) = Streams.iterateeToSubscriber(iteratee)
        Flow.fromSinkAndSource(Sink.fromSubscriber(subscriber), Source.fromPublisher(publisher))
    })))
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
      f(request).map(_.right.map(transformer.transform))
    )
  }

  /**
    * Create a SockJS handler that will pass messages to/from the actor created by the given props.
    *
    * Given a request and an actor ref to send messages to, the function passed should return the props for an actor
    * to create to handle this SockJS connection.
    *
    * For example:
    *
    * {{{
    *   def sockjs = SockJS.acceptWithActor[JsValue, JsValue] { req => out =>
    *     MySockJSActor.props(out)
    *   }
    * }}}
    */
  @deprecated("Use accept with a flow that wraps a Sink.actorRef and Source.actorRef, or play.api.libs.Streams.actorFlow", "0.5.0")
  def acceptWithActor[In, Out](f: RequestHeader => HandlerProps)(implicit transformer: MessageFlowTransformer[In, Out],
                                                                 app: Application, mat: Materializer): SockJS = {
    tryAcceptWithActor(req => Future.successful(Right(f(req))))
  }

  /**
    * Create a SockJS handler that will pass messages to/from the actor created by the given props asynchronously.
    *
    * Given a request, this method should return a future of either:
    *
    * - A result to reject the WebSocket with, or
    * - A function that will take the sending actor, and create the props that describe the actor to handle this SockJS connection
    *
    * For example:
    *
    * {{{
    *   def subscribe = SockJS.acceptWithActor[JsValue, JsValue] { req =>
    *     val isAuthenticated: Future[Boolean] = authenticate(req)
    *     val isAuthenticated.map {
    *       case false => Left(Forbidden)
    *       case true => Right(MySockJSActor.props)
    *     }
    *   }
    * }}}
    */
  @deprecated("Use accept with a flow that wraps a Sink.actorRef and Source.actorRef, or play.api.libs.Streams.actorFlow", "0.5.0")
  def tryAcceptWithActor[In, Out](f: RequestHeader => Future[Either[Result, HandlerProps]])(implicit transformer: MessageFlowTransformer[In, Out],
                                                                                            app: Application, mat: Materializer): SockJS = {

    implicit val system = Akka.system

    acceptOrResult(f.andThen(_.map(_.right.map { props =>
      ActorFlow.actorRef(props)
    })))
  }

  /**
    * Like Enumeratee.onEOF, however enumeratee.onEOF always gets fed an EOF (by the enumerator if nothing else).
    */
  private def onEOF[E](enumerator: Enumerator[E], action: () => Unit): Enumerator[E] = new Enumerator[E] {
    def apply[A](i: Iteratee[E, A]) = enumerator(wrap(i))

    def wrap[A](i: Iteratee[E, A]): Iteratee[E, A] = new Iteratee[E, A] {
      def fold[B](folder: (Step[E, A]) => Future[B])(implicit ec: ExecutionContext) = i.fold {
        case Step.Cont(k) => folder(Step.Cont {
          case eof @ Input.EOF =>
            action()
            wrap(k(eof))
          case other => wrap(k(other))
        })
        case other => folder(other)
      }(ec)
    }
  }
}