package play.sockjs.core

import play.api.mvc._

import play.sockjs.api._

/**
 * SockJS request handler
 */
private[sockjs] case class SockJSHandler(f: (RequestHeader, SockJS) => Handler) extends Handler