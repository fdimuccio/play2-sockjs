package play.sockjs.api

import scala.concurrent.duration._

import play.api.mvc.{Cookie, RequestHeader}

/**
  *
  * SockJS server settings
  *
  * @param scriptSRC A function to calculate SockJS script src. Defaults to "http://cdn.sockjs.org/sockjs-0.3.min.js".
  * @param websocket If true websocket are enabled, false otherwis. Defaults to true.
  * @param cookies Some hosting providers enable sticky sessions only to requests that have JSESSIONID cookie set.
  *                This setting controls if the server should set this cookie to a dummy value.
  *                By default setting JSESSIONID cookie is disabled. More sophisticated behaviour
  *                can be achieved by supplying a SockJSSettings.CookieCalculator.
  * @param heartbeat Interval at which heartbeat frame should be sent. Defaults to 25 seconds.
  * @param sessionTimeout The session will be closed if does not receive any connection
  *                       during this time. Defaults to 5 seconds.
  * @param streamingQuota Quota in bytes for a single streaming request: after the quota is
  *                       reached the request will be closed by the server to let the
  *                       client GC received messages. Defaults to 128Kb.
  * @param sendBufferSize Maximum number of messages kept in the 'send' buffer, used by
  *                       xhr_send and jsonp_send. If the buffer fills further messages
  *                       will be rejected. Defaults to 256.
  * @param sessionBufferSize Maximum size of the session buffer in bytes.
  *                          Transports that emulate websocket needs to store outgoing messages
  *                          in between client reconnects (after a long poll or a streaming
  *                          request completes). When the buffer fills the session
  *                          flow will backpressure. Defaults to 64Kb.
  */
case class SockJSSettings(
    scriptSRC: RequestHeader => String = _ => "//cdn.jsdelivr.net/sockjs/1.0.3/sockjs.min.js",
    websocket: Boolean = true,
    cookies: Option[RequestHeader => Cookie] = None,
    heartbeat: FiniteDuration = 25.seconds,
    sessionTimeout: FiniteDuration = 5.seconds,
    streamingQuota: Long = 128*1024,
    sendBufferSize: Int = 256,
    sessionBufferSize: Int = 64*1024) {
  def scriptSRC(f: RequestHeader => String): SockJSSettings = copy(scriptSRC = f)
  def websocket(enabled: Boolean): SockJSSettings = copy(websocket = enabled)
  def cookies(f: RequestHeader => Cookie): SockJSSettings = cookies(Some(f))
  def cookies(f: Option[RequestHeader => Cookie]): SockJSSettings = copy(cookies = f)
  def heartbeat(interval: FiniteDuration): SockJSSettings = copy(heartbeat = heartbeat)
  def streamingQuota(quota: Long): SockJSSettings = copy(streamingQuota = quota)
  def sessionTimeout(timeout: FiniteDuration): SockJSSettings = copy(sessionTimeout = timeout)
  def sendBufferSize(size: Int): SockJSSettings = copy(sendBufferSize = size)
  def sessionBufferSize(size: Int): SockJSSettings = copy(sessionBufferSize = size)
}

/**
  * Helper with common cookie functions
  */
object CookieFunctions {

  /**
    * support jsessionid cookie
    */
  val jsessionid = (req: RequestHeader) => {
    val value = req.cookies.get("JSESSIONID").map(_.value).getOrElse("dummy")
    Cookie("JSESSIONID", value, path = "/")
  }
}
