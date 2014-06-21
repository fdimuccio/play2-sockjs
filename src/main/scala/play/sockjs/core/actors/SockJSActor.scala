package play.sockjs.core
package actors

import scala.reflect.ClassTag

import akka.actor._

import play.api.libs.iteratee._

import play.sockjs.api.SockJS

/**
 * This object is a copy of play.core.actors.WebSocketActor but for SockJS
 */
private[sockjs] object SockJSActor {

  // -- Akka extension

  /**
   * The extension for managing sockjs SessionMasters and handlers
   */
  object SockJSExtension extends ExtensionId[SockJSExtension] {
    def createExtension(system: ExtendedActorSystem) = new SockJSExtension(system)
  }

  class SockJSExtension(system: ExtendedActorSystem) extends Extension {
    lazy val actor = system.actorOf(SockJSActor.props, "sockjs")
    def sessionMaster(name: Option[String]): ActorRef = {
      if (name.isDefined) system.actorOf(SessionMaster.props, name.get)
      else system.actorOf(SessionMaster.props)
    }
  }

  // -- Actors API

  object SockJSActor {

    val props = Props(new SockJSActor)

    /**
     * Connect an actor to the SockJS connection on the end of the given enumerator/iteratee.
     *
     * @param requestId The requestId. Used to name the actor.
     * @param enumerator The enumerator to send messages to.
     * @param iteratee The iteratee to consume messages from.
     * @param createHandler A function that creates a SockJS handler, given an actor to send messages
     *                      to.
     * @param messageType The type of message this SockJS connection deals with.
     */
    case class Connect[In, Out](requestId: Long, enumerator: Enumerator[In], iteratee: Iteratee[Out, Unit], createHandler: SockJS.HandlerProps)(implicit val messageType: ClassTag[Out])
  }

  /**
   * The actor responsible for creating all SockJS actors
   */
  private class SockJSActor extends Actor {
    import SockJSActor._

    def receive = {
      case c @ Connect(requestId, enumerator, iteratee, createHandler) =>
        implicit val mt = c.messageType
        context.actorOf(SockJSActorSupervisor.props(enumerator, iteratee, createHandler), requestId.toString)
    }
  }

  object SockJSActorSupervisor {
    def props[In, Out: ClassTag](enumerator: Enumerator[In], iteratee: Iteratee[Out, Unit], createHandler: ActorRef => Props) = {
      Props(new SockJSActorSupervisor(enumerator, iteratee, createHandler))
    }
  }

  /**
   * The supervisor that handles all messages to/from the SockJS actor.
   */
  private class SockJSActorSupervisor[In, Out](enumerator: Enumerator[In], iteratee: Iteratee[Out, Unit], createHandler: ActorRef => Props)(implicit messageType: ClassTag[Out]) extends Actor {

    import context.dispatcher

    @volatile var shutdown = false

    val sockjsActor = context.watch(context.actorOf(createHandler(self), "handler"))

    val channel = {
      val (enum, chan) = Concurrent.broadcast[Out]
      enum |>>> iteratee
      chan
    }

    val consumer = Iteratee.foreach[In] { msg =>
      sockjsActor ! msg
    }(play.api.libs.iteratee.Execution.trampoline)

    (enumerator |>> consumer).onComplete { _ =>
      if (!shutdown) sockjsActor ! PoisonPill
    }

    def receive: Receive = {
      case _: Terminated =>
        shutdown = true
        channel.end()
        context.stop(self)
      case messageType(a) => channel.push(a)
    }

    override def postStop() = {
      shutdown = true
      channel.end()
    }

    override def supervisorStrategy = OneForOneStrategy() {
      case _ => SupervisorStrategy.Stop
    }
  }
}
