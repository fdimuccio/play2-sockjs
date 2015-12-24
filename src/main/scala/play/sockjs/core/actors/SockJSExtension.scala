package play.sockjs.core
package actors

import scala.reflect.ClassTag

import akka.actor._

import play.api.libs.iteratee._

import play.sockjs.api.SockJS

/**
 * The extension for managing sockjs SessionMasters and handlers
 */
private[sockjs] object SockJSExtension extends ExtensionId[SockJSExtension] {
  def createExtension(system: ExtendedActorSystem) = new SockJSExtension(system)
}

private[sockjs] class SockJSExtension(system: ExtendedActorSystem) extends Extension {
  def sessionMaster(name: Option[String]): ActorRef = {
    if (name.isDefined) system.actorOf(SessionMaster.props, name.get)
    else system.actorOf(SessionMaster.props)
  }
}
