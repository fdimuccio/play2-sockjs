package play.sockjs.core

import play.api.mvc._

import play.sockjs.api._

/**
 * Helper for SockJS request handler
 */
sealed trait SockJSHandler

/**
 * Helper for a plain SockJS HTTP endpoint
 */
case class SockJSAction[A](action: Action[A]) extends SockJSHandler

/**
 * Helper for a SockJS transport
 */
case class SockJSTransport(f: SockJS[_, _] => Handler) extends SockJSHandler

/**
 * Helper for a SockJS websocket transport
 */
case class SockJSWebSocket[A](f: RequestHeader => SockJSTransport) extends SockJSHandler