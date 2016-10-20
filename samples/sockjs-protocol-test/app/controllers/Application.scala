package controllers

import play.sockjs.api._

import streams._

object Application {

  /**
    * responds with identical data as received
    */
  class Echo extends TestRouter {
    def sockjs: SockJS = SockJS.accept(_ => FlowX.echo)
  }

  /**
    * same as echo, but with websockets disabled
    */
  class EchoWithNoWebsocket extends TestRouter(Settings.noWebSocket) {
    def sockjs: SockJS = SockJS.accept(_ => FlowX.echo)
  }

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  class EchoWithJSessionId extends TestRouter(Settings.withJSessionId) {
    def sockjs: SockJS = SockJS.accept(_ => FlowX.echo)
  }

  /**
    * server immediately closes the session
    */
  class Closed extends SockJSRouter {
    def sockjs: SockJS = SockJS.accept(_ => FlowX.closed)
  }
}