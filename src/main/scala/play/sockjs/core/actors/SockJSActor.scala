package play.sockjs.core
package actors

import akka.actor._
import akka.util.Timeout
import akka.pattern._
import scala.concurrent.Future

private[sockjs] object SockJSActor {

  object SessionMasterSupervisor {

    val props = Props(new SessionMasterSupervisor)

    case class GetSessionMaster(name: String)
    case class AskToSessionMaster(name: String, message: Any)
  }

  /**
   * The actor that manages all SessionMaster actors under the same ActorSystem
   */
  class SessionMasterSupervisor extends Actor {
    import SessionMasterSupervisor._

    def receive = {
      case GetSessionMaster(name) => sender ! get(name)
      case AskToSessionMaster(name, message) => get(name) forward message
    }

    private def get(name: String) = context.child(name).getOrElse(context.actorOf(SessionMaster.props, name))
  }

  /**
   * Convenience wrapper useful to communicate directly with the SessionMaster
   */
  class SessionMasterRef(supervisor: ActorRef, name: String) {
    import SessionMasterSupervisor._
    def ?(message: Any)(implicit timeout: Timeout): Future[Any] = supervisor ? AskToSessionMaster(name, message)
  }

  /**
   * The extension for managing sockjs SessionMasters and handlers
   */
  object SockJSExtension extends ExtensionId[SockJSExtension] {
    def createExtension(system: ExtendedActorSystem) = {
      new SockJSExtension(system.actorOf(SessionMasterSupervisor.props, "sockjs"))
    }
  }

  class SockJSExtension(actor: ActorRef) extends Extension {
    def sessionMaster(name: String): SessionMasterRef = new SessionMasterRef(actor, name)
  }
}
