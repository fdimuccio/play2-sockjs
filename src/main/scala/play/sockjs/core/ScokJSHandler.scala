package play.sockjs.core

import play.api.mvc._

import play.sockjs.api._

/**
 * A handler for SockJS request
 */
sealed trait SockJSHandler

/**
 * Handler that implements a plain SockJS HTTP endpoint
 */
case class SockJSAction[A](action: Action[A]) extends SockJSHandler

/**
 * Handler that implements a SockJS transport
 */
case class SockJSTransport(f: SockJS[_] => Handler) extends SockJSHandler