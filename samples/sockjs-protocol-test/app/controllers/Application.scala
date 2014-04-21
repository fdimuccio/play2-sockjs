package controllers

import play.api.libs.iteratee._

import scala.concurrent.ExecutionContext.Implicits.global

import play.sockjs.api._
import play.sockjs.core.IterateeX

object Application {

  val quota = 4096

  /**
   * responds with identical data as received
   */
  val echo = SockJSRouter.using(req => IterateeX.joined[String]).streamingQuota(quota)

  /**
   * identical to echo, but with websockets disabled
   */
  val disabledWebSocketEcho = SockJSRouter.using(req => IterateeX.joined[String]).websocket(false)

  /**
   * identical to echo, but with JSESSIONID cookies sent
   */
  val cookieNeededEcho = SockJSRouter.using(req => IterateeX.joined[String]).jsessionid(true)

  /**
   * server immediately closes the session
   */
  val closed = SockJSRouter.using(req => (Iteratee.ignore[String], Enumerator.eof[String]))

}