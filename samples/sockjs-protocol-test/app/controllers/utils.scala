package controllers

import play.sockjs.api.{CookieFunctions, SockJSRouter, SockJSSettings}

object Settings {
  val default = SockJSSettings(streamingQuota = 4096)
  val noWebSocket = default.websocket(false)
  val withJSessionId = default.cookies(CookieFunctions.jsessionid)
}

abstract class TestRouter(cfg: SockJSSettings = Settings.default) extends SockJSRouter {
  /**
    * Override this method to specify different settings
    */
  override protected def settings: SockJSSettings = cfg
}


