package play.sockjs.core
package actors

import scala.util.control.Exception._
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

import akka.util._
import akka.actor._
import akka.pattern.pipe
import akka.pattern.ask

import play.api.libs.iteratee._
import play.api.libs.iteratee.Concurrent.Channel
import play.api.mvc._

import play.sockjs.api._
import play.sockjs.core.Frame._
import play.sockjs.core.iteratee.EnumeratorX

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
  final class SessionOpened(actor: ActorRef) extends SessionResponse(actor) {

    private[this] var closed = false

    def bind[A, B](req: RequestHeader, sockjs: SockJS[A, B])(implicit ec: ExecutionContext): Future[Either[Result, SessionResponse]] = {
      // input enumerator: client messages will be read here and forwarded to the handler
      def en = Concurrent.unicast[String](
        onStart = channel => actor ! Session.SessionBound(channel),
        // EOF could be sent by the session actor if timeout occurs so it's useless
        // to tell the actor that is closed
        onComplete = {
          // sure that is needed?
          if (!closed) {
            actor ! Session.CloseSession
            closed = true
          }
        }
        //onError = (error, in) => self ! SessionUnbound(Some(error)), //bisogna gestire l'errore, ad esempio chiudendo la sessione
      ) &> Enumeratee.mapInputFlatten {
        // This is hackish but this is the only way i found to make it works.
        // The right way should be mapInput but it looks like that EOF is not
        // propagated (something is wrong with mapInput and Concurrent.unicast)
        // To reproduce the bug:
        // Concurrent.unicast[Unit](_.eofAndEnd()) &> Enumeratee.mapInput {
        //   case in => println(in); in
        // } |>> Iteratee.ignore.map(_ => println("DONE!"))
        case Input.EOF => closed = true; Enumerator.eof[A]
        case Input.El(message) => (allCatch opt Enumerator.enumInput[A](Input.El(sockjs.inFormatter.read(message)))).getOrElse(Enumerator.enumInput[A](Input.Empty))
        case Input.Empty => Enumerator.enumInput[A](Input.Empty)
      }
      // output iteratee: the handler will push messages here that will be written to the client
      def it = Enumeratee.breakE[B](_ => closed) &>> Iteratee.foreach[B] { message =>
        actor ! Session.Write(MessageFrame(sockjs.outFormatter.write(message)))
      }.map { _ =>
        if (!closed) {
          actor ! Session.CloseSession
          closed = true
        }
      }
      // bind the session to the handler
      sockjs.f(req).map(_.right.map { f => f(en, it); this })
    }

  }

  object SessionOpened {

    def apply(actor: ActorRef) = new SessionOpened(actor)

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
  case class Connected(enumerator: Enumerator[Frame])

  case class SessionBound(channel: Concurrent.Channel[String])

  case class Push(json: Seq[String])
  case class Write(frame: Frame)

  case object ConsumeQueue

  case object SessionTimeout
  case object CloseSession

}

private[sockjs] class Session extends Actor {

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

  def opening(session: Option[Channel[String]], connection: Option[ActorRef]): Receive = {

    case Connect(heartbeat, timeout, quota)  =>
      val client = sender
      if (context.child("c").isEmpty) {
        sessionTimeout = timeout
        val conn = context.actorOf(Props(new Connection(client, heartbeat, quota)), "c")
        if (session.isDefined) conn ! WriteFrame(OpenFrame)
        context.become(opening(session, Some(conn)))
      } else client ! Connected(Enumerator(CloseFrame.AnotherConnectionStillOpen))

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

    case ConnectionAborted =>
      session.foreach(_.eofAndEnd())
      scheduleTimeout()
      context.become(closed)

    case Write(frame) =>
      buffer.enqueue(frame)

    case CloseSession =>
      self ! Write(CloseFrame.GoAway)

    case Push(msg) =>
      session.fold(sender ! Error) { channel =>
        msg.foreach(channel.push)
        sender ! Ack
      }

  }

  def connected(session: Channel[String], connection: ActorRef): Receive = {

    case Connect(_, _, _) =>
      sender ! Connected(Enumerator(CloseFrame.AnotherConnectionStillOpen))

    case Push(jsons) =>
      jsons.foreach(session.push)
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

    case ConnectionAborted =>
      session.eofAndEnd()
      scheduleTimeout()
      context.become(closed)

    case CloseSession =>
      self ! Write(CloseFrame.GoAway)

  }

  def disconnected(session: Channel[String]): Receive = {

    case Connect(heartbeat, timeout, quota) =>
      val client = sender
      if (context.child("c").isEmpty) {
        val conn = context.actorOf(Props(new Connection(client, heartbeat, quota)), "c")
        sessionTimeout = timeout
        clearTimeout()
        self ! ConsumeQueue
        context.become(connected(session, conn))
      } else client ! Connected(Enumerator(CloseFrame.AnotherConnectionStillOpen))

    case Push(jsons) =>
      jsons.foreach(session.push)
      sender ! Ack

    case Write(frame) =>
      buffer.enqueue(frame)

    case SessionTimeout =>
      session.eofAndEnd()
      context.stop(self)

    case CloseSession =>
      session.eofAndEnd()
      context.become(closed)

  }

  def closed: Receive = {
    case Connect(_, _, _) => sender ! Connected(Enumerator(CloseFrame.GoAway))
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
  case object ConnectionAborted

  case class ChannelUnbound(error: Option[String])

}

private[sockjs] class Connection(client: ActorRef, heartbeat: FiniteDuration, limit: Long) extends Actor {

  import Session._
  import Connection._
  import context.dispatcher

  private[this] var done = false
  private[this] var quota = limit

  private[this] val (enumerator, channel) = EnumeratorX.concurrent[Frame](
    onComplete = self ! ChannelUnbound(None),
    onError = (error, in) => self ! ChannelUnbound(Some(error)))

  client ! Connected(enumerator)

  context.setReceiveTimeout(heartbeat)

  def receive: Receive = {

    case WriteFrame(frame) =>
      channel.push(frame)
      quota -= frame.size
      frame match {
        case frame: CloseFrame =>
          done = true
          context.parent ! ConnectionAborted
        case _ if quota > 0 =>
          context.parent ! ConnectionCont
        case _ =>
          done = true
          context.parent ! ConnectionDone
      }
      if (done) channel.eofAndEnd()

    case ChannelUnbound(error) =>
      if (!done) context.parent ! ConnectionAborted
      context.stop(self)

    case ReceiveTimeout =>
      self ! WriteFrame(Frame.HeartbeatFrame)
  }

}
