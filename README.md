play2-sockjs
-----------

A SockJS server implementation for [Play Framework](http://www.playframework.com/).

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

It provides api for Scala and Java. All transports offered by SockJS have been
implemented according to the [0.3.3 protocol specifications](http://sockjs.github.io/sockjs-protocol/sockjs-protocol-0.3.3.html).
Currently passes all transport tests from the specs except for test_haproxy, it should impact
only users that uses WebSocket Hixie-76 protocol behind HAProxy.

    Current versions:
        Play 2.1.x : 0.1.6
        Play 2.2.x : 0.2.6
        Play 2.3.x : 0.3.1
        Play 2.4.x : 0.4.0-SNAPSHOT

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
    if (v.startsWith("2.4")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.4.0-SNAPSHOT")
    else if (v.startsWith("2.3")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.3.1")
    else if (v.startsWith("2.2")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.2.6")
    else if (v.startsWith("2.1")) Seq("com.github.fdimuccio" %% "play2-sockjs" % "0.1.6")
    else Seq()
}
```

You may also need to add the Sonatype Repository as a resolver:

```scala
resolvers += Resolver.sonatypeRepo("releases")
```

or if using snapshot version:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")
```

Usage
-----

Since SockJS uses a complex path system to support different transports it can not be
instantiated as a classic Play action handler, instead it must be used inside a SockJSRouter.
Each SockJSRouter can contain only one SockJS handler, however the application can contain
as many SockJSRouter as you wish.

#### Scala API

A SockJS endpoint could be implemented in two way.

First, using SockJSRouter builder facility:

```scala
package controllers

import play.api.mvc._
import play.api.libs.iteratee._
import play.sockjs.api._

object Application extends Controller {

    def index = Action {
        Ok("It Works!")
    }

    // it must be a `val` or `lazy val` because you are instantiating a play Router and not a
    // classic request handler
    lazy val sockjs = SockJSRouter.using[String] { request =>

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

and in route.conf define the route:

```scala

# Using Play sub routes include syntax `->`, map /foo url to SockJS router
->      /foo                  controllers.Application.sockjs

```

Second (more verbose), extending SockJSRouter trait:

```scala
package controllers

import play.api.mvc._
import play.api.libs.iteratee._
import play.sockjs.api._

// extends or mixin SockJSRouter trait
object SockJSController extends SockJSRouter {

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

and in route.conf define the path to the controller:

```scala

# Using Play sub routes include syntax `->`, map /foo url to SockJS controller
->      /foo                  controllers.SockJSController

```

and finally connect with the javascript client:

```javascript
<script src="//cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js"></script>

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
###### Configure underlying SockJS server

It's possible to change SockJS server side settings such as connection heartbeat or
session timeout.

When using SockJSRouter builder:

```scala
package controllers

import scala.concurrent.duration._

import play.api.mvc._
import play.api.libs.iteratee._
import play.sockjs.api._

object Application extends Controller {

    def index = Action {
        Ok("It Works!")
    }

    // it's possible to change default settings
    lazy val sockjs = SockJSRouter(_.websocket(false).heartbeat(55 seconds)).using[String] { request =>
        ...
    }

    // or to pass a new SockJSSettings instance
    lazy val sockjs = SockJSRouter(SockJSSettings(websocket = false, heartbeat = 55 seconds)).using[String] { request =>
        ...
    }

    // or to pass a new SockJSServer instance
    lazy val sockjs = SockJSRouter(SockJSServer(...)).using[String] { request =>
        ...
    }
}
```

When extending SockJSRouter:

```scala
package controllers

import scala.concurrent.duration._

import play.api.mvc._
import play.sockjs.api._

// mixin SockJSRouter trait with your controller
object SockJSController extends Controller with SockJSRouter {

  // override this method to specify a different SockJSServer instance with custom settings
  override val server = SockJSServer(SockJSSettings(websocket = false, heartbeat = 55 seconds)

  // here goes the request handler
  def sockjs = SockJS.using[String] { request =>
    ...
  }

}
```

Note: each SockJSRouter will have is own SockJSServer

#### Java API

Here is a short example of how to implement a SockJS endpoint in Java (for Java8 see below):

```java
package controllers;

import play.libs.F;
import play.mvc.*;

import play.sockjs.*;

public class Application extends Controller {

    public static SockJSRouter hello = new SockJSRouter() {

        // override sockjs method
        public SockJS sockjs() {
            return new SockJS() {

                // Called when the SockJS Handshake is done.
                public void onReady(SockJS.In in, SockJS.Out out) {

                    // For each event received on the socket,
                    in.onMessage(new Callback<String>() {
                        public void invoke(String event) {

                            // Log events to the console
                            System.out.println(event);

                        }
                    });

                    // When SockJS connection is closed.
                    in.onClose(new Callback0() {
                        public void invoke() {

                            System.out.println("Disconnected");

                        }
                    });

                    // Send a single 'Hello!' message
                    out.write("Hello!");

                }

            };
        }
    }

}
```

in route.conf define the route:

```scala

# Using Play sub routes include syntax `->`, map /foo url to SockJS router
->      /hello                  controllers.Application.hello

```

To configure it you can use @SockJS.Settings annotation:

```java
package controllers;

import play.libs.F;
import play.mvc.*;

import play.sockjs.*;

public class Application extends Controller {

    public static SockJSRouter hello = new SockJSRouter() {

        @SockJS.Settings(
            cookies = CookieCalculator.JSESSIONID.class,
            websocket = false,
            heartbeat = 55000 // duration in milliseconds
        )
        public SockJS sockjs() {
            return new SockJS() {...}
        }

    }

}
```

### Java 8 API

If you are using Java 8 you can take advantage of Lambda Expressions (api contribution by [Ariel Scarpinelli](https://github.com/arielscarpinelli)): 

```java
package controllers;

import play.mvc.*;

import play.sockjs.*;

public class Application extends Controller {

    // SockJS endpoint handler with default configuration:
    public static SockJSRouter hello = SockJSRouter.whenReady((in, out) -> {

    	// Log each event received on the socket to the console
        in.onMessage(System.out::println);

        // When SockJS connection is closed.
        in.onClose(() -> System.out.println("Disconnected"));

        // Send a single 'Hello!' message
        out.write("Hello!");

    });
    
    // and if default configuration isn't enough:
    public static SockJSRouter helloNoWebSocket = SockJSRouter.withWebSocket(false).whenReady((in, out) -> {    
        ...
    });

}
```

Load balancing and sticky sessions
----------------------------------

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

Samples
-------

In the samples/ folder there are two sample applications:

    * sockjs-protocol-test: server for SockJS 0.3.3 protocol specifications tests
    * sockjs-chat: a port of the Play sample websocket-chat to SockJS
    * sockjs-chat-actor-di: same demo app of sockjs-chat but implemented with the 
                            actor api available from 0.3.0 and dependency injection 
                            using [macwire](https://github.com/adamw/macwire)

### What's missing?

Currently, on 68 tests only 3 do not pass. As mentioned, the most important is test_haproxy.
The other two failing tests are edge cases of test_abort_xhr_streaming and test_abort_xhr_polling
due to different implementation details, however session closure is handled correctly.

