package play.sockjs.api

import scala.concurrent.duration._

import akka.actor.ActorSystem

import play.api.libs.concurrent.Akka
import play.api.mvc._

import play.sockjs.core._
import play.sockjs.api.SockJSSettings.CookieCalculator
import play.sockjs.core.actors.SockJSActor.SockJSExtension
import play.api.Logger

/**
 * ScokJS server.
 *
 * @param actorSystem The [[akka.actor.ActorSystem]] that will be used to create
 *                    the [[play.sockjs.core.actors.SessionMaster]] to handle SockJS sessions.
 * @param settings The [[play.sockjs.api.SockJSSettings]] used to setup this server.
 * @param name The name of the newly created [[play.sockjs.core.actors.SessionMaster]] actor for this server, if needed.
 */
case class SockJSServer(actorSystem: ActorSystem, settings: SockJSSettings, name: Option[String]) {

  def reconfigure(f: SockJSSettings => SockJSSettings): SockJSServer = copy(settings = f(settings))

  private[sockjs] def dispatcher(prefix: String) = {
    new Dispatcher(SockJSExtension(actorSystem).sessionMaster(name), settings)
  }

}

object SockJSServer {

  /**
   * Returns a SockJS server with default settings
   */
  def default = SockJSServer(SockJSSettings.default)

  /**
   * Returns a SockJS server with specified settings
   *
   * @param settings The [[play.sockjs.api.SockJSSettings]] used to setup this server.
   */
  def apply(settings: SockJSSettings): SockJSServer = {
    SockJSServer(
      play.api.Play.maybeApplication.map(Akka.system(_)).getOrElse(sys.error("Play application not started!")),
      settings)
  }

  /**
   * Returns a SockJS server with specified actorSystem and settings
   *
   * @param actorSystem The [[akka.actor.ActorSystem]] that will be used to create
   *                    the [[play.sockjs.core.actors.SessionMaster]] to handle SockJS sessions.
   * @param settings The [[play.sockjs.api.SockJSSettings]] used to setup this server.
   */
  def apply(actorSystem: ActorSystem, settings: SockJSSettings): SockJSServer = SockJSServer(actorSystem, settings, None)

}

/**
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
 */
case class SockJSSettings(
    scriptSRC: RequestHeader => String = _ => "http://cdn.sockjs.org/sockjs-0.3.min.js",
    websocket: Boolean = true,
    cookies: Option[CookieCalculator] = None,
    heartbeat: FiniteDuration = 25 seconds,
    sessionTimeout: FiniteDuration = 5 seconds,
    streamingQuota: Long = 128*1024) {
  def scriptSRC(src: RequestHeader => String): SockJSSettings = copy(scriptSRC = src)
  def websocket(enabled: Boolean): SockJSSettings = copy(websocket = enabled)
  def cookies(calculator: CookieCalculator): SockJSSettings = cookies(Some(calculator))
  def cookies(calculator: Option[CookieCalculator]): SockJSSettings = copy(cookies = calculator)
  def heartbeat(interval: FiniteDuration): SockJSSettings = copy(heartbeat = heartbeat)
  def streamingQuota(quota: Long): SockJSSettings = copy(streamingQuota = quota)
  def sessionTimeout(timeout: FiniteDuration): SockJSSettings = copy(sessionTimeout = timeout)
}

object SockJSSettings {

  /**
   * SockJS default settings
   */
  def default = SockJSSettings()

  /**
   * Cookie calculator used by transports to set cookie
   */
  case class CookieCalculator(f: RequestHeader => Cookie) {
    def apply(req: RequestHeader): Cookie = f(req)
  }

  object CookieCalculator {

    /**
     * default jsessionid cookie calculator
     */
    val jsessionid = CookieCalculator { req =>
      val value = req.cookies.get("JSESSIONID").map(_.value).getOrElse("dummy")
      Cookie("JSESSIONID", value, path = "/")
    }

  }

}