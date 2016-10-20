package controllers

import javax.inject.Inject

import akka.actor._

import play.sockjs.api._
import play.sockjs.api.libs.streams._

object ApplicationActor {

  class EchoHandler @Inject()(implicit as: ActorSystem) {
    def apply() = SockJS.accept { _ =>
      ActorFlow.actorRef[Frame, Frame](out => Props(new Actor {
        def receive = {
          case message => out ! message
        }
      }))
    }
  }

  class ClosedHandler @Inject()(implicit as: ActorSystem) {
    def apply() = SockJS.accept { _ =>
      ActorFlow.actorRef[Frame, Frame](out => Props(new Actor {
        self ! PoisonPill
        def receive = {
          case _ =>
        }
      }))
    }
  }

  /**
   * responds with identical data as received
   */
  class Echo @Inject() (handler: EchoHandler) extends TestRouter {
    def sockjs = handler()
  }

  /**
   * identical to echo, but with websockets disabled
   */
  class EchoWithNoWebsocket @Inject() (handler: EchoHandler) extends TestRouter(Settings.noWebSocket) {
    def sockjs = handler()
  }

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  class EchoWithJSessionId @Inject() (handler: EchoHandler)  extends TestRouter(Settings.withJSessionId) {
    def sockjs = handler()
  }

  /**
   * server immediately closes the session
   */
  class Closed @Inject() (handler: ClosedHandler)  extends SockJSRouter {
    def sockjs = handler()
  }
}