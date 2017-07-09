package play.sockjs

import play.api.{Configuration, Environment}
import play.api.inject.{Module => PlayModule}

import play.sockjs.api.{DefaultSockJSRouterComponents, SockJSRouterComponents}

class Module extends PlayModule {
  def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[SockJSRouterComponents].to[DefaultSockJSRouterComponents]
  )
}
