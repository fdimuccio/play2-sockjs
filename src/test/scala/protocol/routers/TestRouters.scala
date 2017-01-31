package protocol.routers

import play.api.routing.Router

trait TestRouters {

  /**
    * responds with identical data as received
    */
  def Echo(prefix: String): Router

  /**
    * same as echo, but with websockets disabled
    */
  def EchoWithNoWebsocket(prefix: String): Router

  /**
    * same as echo, but with JSESSIONID cookies sent
    */
  def EchoWithJSessionId(prefix: String): Router

  /**
    * server immediately closes the session
    */
  def Closed(prefix: String): Router
}
