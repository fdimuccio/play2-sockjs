package play.sockjs.core
package actors

import akka.actor._

import play.api.libs.iteratee._

import play.sockjs.api.SockJS
import scala.reflect.ClassTag

private[sockjs] object SockJSActor {

  // -- Akka extension

  /**
   * The extension for managing sockjs SessionMasters and handlers
   */
  object SockJSExtension extends ExtensionId[SockJSExtension] {
    def createExtension(system: ExtendedActorSystem) = new SockJSExtension(system)
  }

  class SockJSExtension(system: ExtendedActorSystem) extends Extension {
    def sessionMaster(name: String): ActorRef = system.actorOf(SessionMaster.props, name)
    def sessionMaster(): ActorRef = system.actorOf(SessionMaster.props)
  }

  // -- SockJS actor supervisor

  class SockJSActorSupervisor extends Actor {

    def receive: Receive = ???

  }

  // -- Actors API

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
