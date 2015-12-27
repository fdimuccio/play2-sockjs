package play.sockjs.core
package actors

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.immutable.Seq

import akka.stream.scaladsl._
import akka.util._
import akka.actor._
import akka.pattern.pipe
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.stage.{TerminationDirective, SyncDirective, Context, PushStage}

import play.api.mvc._

import play.sockjs.api._
import play.sockjs.api.Frame._
import play.sockjs.core.streams._

//TODO: session master should become a registry of streams where each session is a stream.
//      Each stream is composed by two graph, one that is runnable (source -> sink)
//      and another one that is just a source, created each time the client connects for that
//      particular session. This source should read data from the sink of the
//      first graph.
private[sockjs] object SessionMaster {

  def props = Props(new SessionMaster)

  case class Get(sessionID: String)
  case class Send(sessionID: String, payload: Seq[String])

  case object Ack
  case object Error

  /**
   * Response to Get(sessionID) request
   */
  sealed abstract class SessionResponse(actor: ActorRef) {
    final def connect(
      heartbeat: FiniteDuration,
      sessionTimeout: FiniteDuration,
      quota: Long)(
      implicit timeout: Timeout) = actor ? Session.Connect(heartbeat, sessionTimeout, quota)
  }

  /**
   * A session that has just been created and opened
   */
  final class SessionOpened(actor: ActorRef)(implicit mat: Materializer) extends SessionResponse(actor) {

    def bind(req: RequestHeader, sockjs: SockJS): Future[Either[Result, SessionResponse]] = {
      sockjs(req).map(_.right.map { flow =>

        val handler = Flow.wrap(
          sink =
            Flow[Frame]
              .map(frame => actor ! Session.Write(frame))
              .to(Sink.onComplete(_ => actor ! Session.CloseSession)),
          source =
            Source.actorRef[String](256, OverflowStrategy.dropNew)
        )(Keep.right)

        val channel = flow.joinMat(handler)(Keep.right).run()

        actor ! Session.SessionBound(channel)

        this
      })(play.api.libs.iteratee.Execution.trampoline)
    }

  }

  object SessionOpened {

    def apply(actor: ActorRef)(implicit mat: Materializer) = new SessionOpened(actor)

    def unapply(any: Any): Option[SessionOpened] = any match {
      case session: SessionOpened => Some(session)
      case _ => None
    }

  }

  /**
   * A session that has been resumed
   */
  final class SessionResumed(actor: ActorRef) extends SessionResponse(actor)

  object SessionResumed {

    def apply(actor: ActorRef) = new SessionResumed(actor)

    def unapply(any: Any): Option[SessionResumed] = any match {
      case session: SessionResumed => Some(session)
      case _ => None
    }

  }

}

/**
 * This actor supervise sockjs sessions
 */
private[sockjs] class SessionMaster extends Actor {

  import SessionMaster._
  import Session._

  import context.dispatcher

  implicit val materializer = ActorMaterializer()

  override def supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  implicit val defaultTimeout = Timeout(5 seconds)

  def receive = {
    case Get(sessionID) => context.child(sessionID) match {
      case Some(actor) => sender ! SessionResumed(actor)
      case _ => sender ! SessionOpened(context.actorOf(Props(new Session), sessionID))
    }

    case Send(sessionID, payload) =>
      context.child(sessionID).map(_ ask Push(payload) pipeTo sender).getOrElse(sender ! Error)
  }

}

private[sockjs] object Session {

  case class Connect(heartbeat: FiniteDuration, sessionTimeout: FiniteDuration, quota: Long)
  case class Connected(source: Source[Frame, _])

  case class SessionBound(channel: ActorRef)

  case class Push(json: Seq[String])
  case class Write(frame: Frame)

  case object ConsumeQueue

  case object SessionTimeout
  case object CloseSession

}

private[sockjs] class Session(implicit mat: Materializer) extends Actor {

  import SessionMaster._
  import Session._
  import Connection._

  import context.dispatcher
  implicit val defaultTimeout = Timeout(5 seconds)

  private val buffer = new FrameBuffer
  private var sessionTimeout: FiniteDuration = 5 seconds
  private var toTimer: Option[Cancellable] = None
  private[this] var awaitingCont: Boolean = false

  def receive: Receive = opening(None, None)

  def opening(session: Option[ActorRef], connection: Option[ActorRef]): Receive = {

    case Connect(heartbeat, timeout, quota)  =>
      val client = sender
      if (context.child("c").isEmpty) {
        sessionTimeout = timeout
        val conn = context.actorOf(Props(new Connection(client, heartbeat, quota)), "c")
        if (session.isDefined) conn ! WriteFrame(OpenFrame)
        context.become(opening(session, Some(conn)))
      } else client ! Connected(Source.single(CloseFrame.AnotherConnectionStillOpen))

    case SessionBound(channel) =>
      connection.foreach(_ ! WriteFrame(OpenFrame))
      context.become(opening(Some(channel), connection))

    case ConnectionCont =>
      self ! ConsumeQueue
      // this should be safe since ConnectionCont will be sent by connection actor when
      // session and connection have been bounded
      context.become(connected(session.get, connection.get))

    case ConnectionDone =>
      scheduleTimeout()
      // this should be safe since ConnectionDone will be sent by connection actor when
      // the session has been bounded
      context.become(disconnected(session.get))

    case ConnectionClosed(aborted) =>
      session.foreach(_ ! Status.Success(()))
      scheduleTimeout()
      context.become(closed(aborted))

    case Write(frame) =>
      buffer.enqueue(frame)

    case CloseSession =>
      self ! Write(CloseFrame.GoAway)

    case Push(msg) =>
      session.fold(sender ! Error) { channel =>
        msg.foreach(channel ! _)
        sender ! Ack
      }

  }

  def connected(session: ActorRef, connection: ActorRef): Receive = {

    case Connect(_, _, _) =>
      sender ! Connected(Source.single(CloseFrame.AnotherConnectionStillOpen))

    case Push(jsons) =>
      jsons.foreach(session ! _)
      sender ! Ack

    case ConsumeQueue if !buffer.isEmpty =>
      connection ! WriteFrame(buffer.dequeue())
      awaitingCont = true

    case Write(frame) =>
      if (buffer.isEmpty && !awaitingCont) {
        connection ! WriteFrame(frame)
        awaitingCont = true
      } else buffer.enqueue(frame)

    case ConnectionCont =>
      awaitingCont = false
      self ! ConsumeQueue

    case ConnectionDone =>
      awaitingCont = false
      scheduleTimeout()
      context.become(disconnected(session))

    case ConnectionClosed(aborted) =>
      session ! Status.Success(())
      scheduleTimeout()
      context.become(closed(aborted))

    case CloseSession =>
      self ! Write(CloseFrame.GoAway)

  }

  def disconnected(session: ActorRef): Receive = {

    case Connect(heartbeat, timeout, quota) =>
      val client = sender
      if (context.child("c").isEmpty) {
        val conn = context.actorOf(Props(new Connection(client, heartbeat, quota)), "c")
        sessionTimeout = timeout
        clearTimeout()
        self ! ConsumeQueue
        context.become(connected(session, conn))
      } else client ! Connected(Source.single(CloseFrame.AnotherConnectionStillOpen))

    case Push(jsons) =>
      jsons.foreach(session ! _)
      sender ! Ack

    case Write(frame) =>
      buffer.enqueue(frame)

    case SessionTimeout =>
      session ! Status.Success(())
      context.stop(self)

    case CloseSession =>
      session ! Status.Success(())
      context.become(closed(false))

  }

  def closed(aborted: Boolean): Receive = {
    case Connect(_, _, _) =>
      val frame = if (aborted) CloseFrame.ConnectionInterrupted else CloseFrame.GoAway
      sender ! Connected(Source.single(frame))
    case Push => sender ! Error
    case SessionTimeout => context.stop(self)
  }

  private def clearTimeout() {
    toTimer.filter(!_.isCancelled).foreach(_.cancel())
    toTimer = None
  }

  private def scheduleTimeout() {
    toTimer.filter(!_.isCancelled).foreach(_.cancel())
    toTimer = Some(context.system.scheduler.scheduleOnce(sessionTimeout, self, SessionTimeout))
  }

}

private[sockjs] object Connection {

  case class WriteFrame(frame: Frame)

  case object ConnectionCont
  case object ConnectionDone
  case class ConnectionClosed(aborted: Boolean)

  case class ChannelUnbound(error: Option[String])

}

private[sockjs] class Connection(client: ActorRef, heartbeat: FiniteDuration, limit: Long)(implicit mat: Materializer) extends Actor {

  import Session._
  import Connection._

  private[this] var done = false
  private[this] var quota = limit

  val (channel, publisher) =
    Source.actorRef[Frame](256, OverflowStrategy.dropNew)
      .toMat(Sink.publisher)(Keep.both)
      .run()

  val source =
    Source(publisher)
      .via(FlowX.onPostStop(() => self ! ChannelUnbound(None)))

  client ! Connected(source)

  context.setReceiveTimeout(heartbeat)

  def receive: Receive = {

    case WriteFrame(frame) =>
      channel ! frame
      quota -= frame.size
      frame match {
        case frame: CloseFrame =>
          done = true
          context.parent ! ConnectionClosed(false)
        case _ if quota > 0 =>
          context.parent ! ConnectionCont
        case _ =>
          done = true
          context.parent ! ConnectionDone
      }
      if (done) channel ! Status.Success(())

    case ChannelUnbound(error) =>
      if (!done) context.parent ! ConnectionClosed(true)
      context.stop(self)

    case ReceiveTimeout =>
      self ! WriteFrame(Frame.HeartbeatFrame)
  }

}
