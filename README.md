[![Build Status](https://travis-ci.org/fdimuccio/play2-sockjs.svg?branch=master)](https://travis-ci.org/fdimuccio/play2-sockjs) [![Maven](https://img.shields.io/maven-central/v/com.github.fdimuccio/play2-sockjs_2.12.svg)](http://mvnrepository.com/artifact/com.github.fdimuccio/play2-sockjs_2.12)

## play2-sockjs

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
        Play 2.4.x : 0.4.0
        Play 2.5.x : 0.5.3
        Play 2.6.x : 0.6.0

## What is SockJS?

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

## Usage

For installation see [Installing](https://github.com/fdimuccio/play2-sockjs/wiki#installing)

For usage see [API reference](https://github.com/fdimuccio/play2-sockjs/wiki#installing)

Want to learn more? [See the wiki](https://github.com/fdimuccio/play2-sockjs/wiki)

