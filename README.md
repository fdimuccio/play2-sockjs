play2-sockjs
-----------

A SockJS server implementation for [Play Framework](http://http://www.playframework.com/).

play2-sockjs api aims to be as similar as possible to the WebSocket one provided by Play Framework:

```scala
// Play WebSocket api:
def websocket = WebSocket.using[String](handler)

// play2-sockjs api:
def sockjs = SockJS.using[String](handler)

// same request handler
val handler = { (request: RequestHeader) =>
  // Log events to the console
  val in = Iteratee.foreach[String](println).map { _ =>
    println("Disconnected")
  }
  // Send a single 'Hello!' message and close
  val out = Enumerator("Hello!") >>> Enumerator.eof
  (in, out)
}
```

It is currently in a early release, however all transports offered by SockJS have been
implemented according to the [0.3.3 protocol specifications](http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html).
Currently passes all transport tests from the specs except for test_haproxy, it should impact
only users that uses WebSocket Hixie-76 protocol behind HAProxy.

    Current versions:
        Play 2.1.x : 0.1
        Play 2.2.x : 0.2.1

What is SockJS?
---------------

[SockJS](http://sockjs.org) is a browser JavaScript library that provides a WebSocket-like
object. SockJS gives you a coherent, cross-browser, Javascript API
which creates a low latency, full duplex, cross-domain communication
channel between the browser and the web server.

Under the hood SockJS tries to use native WebSockets first. If that
fails it can use a variety of browser-specific transport protocols and
presents them through WebSocket-like abstractions.

SockJS is intended to work for all modern browsers and in environments
which don't support WebSocket protocol, for example behind restrictive
corporate proxies.

Installation
------------

Add play2-sockjs dependency to your build.sbt or project/Build.scala:

```scala
libraryDependencies <++= playVersion { v: String =>
    if (v.startsWith("2.2")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.2.1")
    else if (v.startsWith("2.1")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.1")
    else Seq()
}
```

You may also need to add the Sonatype Repository as a resolver:

```scala
resolvers += Resolver.sonatypeRepo("releases")
```

Usage
-----

Since SockJS uses a complex path system to support different transports it can not be
instantiated as a classic Play action handler, instead it must be used inside a SockJSRouter.
Each SockJSRouter can contain only one SockJS handler, however the application can contain
as many SockJSRouter as you wish.

A SockJS controller could be implemented like this:

```scala
package controllers

import play.api.mvc._
import play.sockjs.api._

// mixin SockJSRouter trait with your controller
object SockJSController extends Controller with SockJSRouter {

  // to handle a SockJS request override sockjs method
  def sockjs = SockJS.using[String] { request =>

    // Log events to the console
    val in = Iteratee.foreach[String](println).map { _ =>
      println("Disconnected")
    }

    // Send a single 'Hello!' message and close
    val out = Enumerator("Hello SockJS!") >>> Enumerator.eof

    (in, out)
  }

}
```

in route.conf define the path to the controller:

```scala

# Using Play sub routes include syntax `->`, map /foo url to SockJS controller
->      /foo                  controllers.SockJSController

```

and finally connect with the javascript client:

```javascript
<script src="http://cdn.sockjs.org/sockjs-0.3.min.js"></script>

<script>
   var sock = new SockJS('http://localhost:9000/foo');
   sock.onopen = function() {
       console.log('open');
   };
   sock.onmessage = function(e) {
       console.log('message', e.data);
   };
   sock.onclose = function() {
       console.log('close');
   };
</script>
```
### Configure underlying SockJS server

To change SockJS server side settings, such as connection heartbeat or session
timeout, you have to override `server` method in SockJSRouter, as shown here:

```scala
package controllers

import scala.concurrent.duration._

import play.api.mvc._
import play.sockjs.api._

// mixin SockJSRouter trait with your controller
object SockJSController extends Controller with SockJSRouter {

  // here goes the server with custom settings
  override val server = SockJSServer(SockJSSettings(websocket = false, heartbeat = 55 seconds)

  // here goes the request handler
  def sockjs = SockJS.using[String] { request =>

    // Log events to the console
    val in = Iteratee.foreach[String](println).map { _ =>
      println("Disconnected")
    }

    // Send a single 'Hello!' message and close
    val out = Enumerator("Hello SockJS!") >>> Enumerator.eof

    (in, out)
  }

}
```

Note: each SockJSRouter will have is own SockJSServer

### Load balancing and sticky sessions

If your Play application is deployed in a load balanced environment you must make sure that
all requests for a single session must reach the same server.

SockJS has two mechanisms that can be useful to achieve that:

    * Urls are prefixed with server and session id numbers, like:
      /resource/<server_number>/<session_id>/transport. This is useful for load
      balancers that support prefix-based affinity (HAProxy does).

    * JESSIONID cookie: it's possible to enable cookie writing for load balancers that
      support sticky sessions. In order to enable this feature please supply
      SockJSSettings.CookieCalculator.jessionid when configuring SockJSServer, it's disabled
      by default. It's also possible to implement custom CookieCalculator.

### Samples

In the samples/ folder there are two sample applications:

    * sockjs-chat: a port of the Play sample websocket-chat to SockJS
    * sockjs-protocol-test: server for SockJS 0.3.3 protocol specifications tests

### What's missing?

Currently, on 68 tests only 3 do not pass. As mentioned, the most important is test_haproxy.
The other two failing tests are edge cases of test_abort_xhr_streaming and test_abort_xhr_polling
due to different implementation details, however session closure is handled correctly.

