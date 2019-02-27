# Change Log

## [0.7.0](https://github.com/fdimuccio/play2-sockjs/tree/0.7.0)

[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.6.0...0.7.0)

**Closed issues:**

- Play 2.7 released [\#27](https://github.com/fdimuccio/play2-sockjs/issues/27)

## [0.6.0](https://github.com/fdimuccio/play2-sockjs/tree/0.6.0)

[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.5.3...0.6.0)

- Update to Play 2.6.19
- Add API to inject SockJSRouter

## [0.5.3](https://github.com/fdimuccio/play2-sockjs/tree/0.5.3)

[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.5.2...0.5.3)

- Update to Play 2.5.12

**Closed issues:**

- Heartbeat frames shouldn't be taken in consideration when calculating framebuffer size [\#22](https://github.com/fdimuccio/play2-sockjs/issues/22) 

## [0.5.2](https://github.com/fdimuccio/play2-sockjs/tree/0.5.2)

[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.5.1...0.5.2)

- Update to Play 2.5.10
- Session flows are now pre-fused
- When using Actor Java API the ActorFlow is built with a buffer of 256, the same
  size as the JAVA callback based API

## [0.5.1](https://github.com/fdimuccio/play2-sockjs/tree/0.5.1)

[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.5.0...0.5.1)

- Update to Play 2.5.9
- Better support for WebSocket transports
- Alternative ActorFlow implementation based on GraphStage
- More complete test suite

**Closed issues:**

- Rejecting & accepting websockets [\#20](https://github.com/fdimuccio/play2-sockjs/issues/20) 

## [0.5.0](https://github.com/fdimuccio/play2-sockjs/tree/0.5.0) (2016-03-13)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.4.0...0.5.0)

- Play 2.5 support
- Add flow based Java API
- Remove SockJSRouter builder methods
- Refactored all internal session handling code to akka-streams

## [0.4.0](https://github.com/fdimuccio/play2-sockjs/tree/0.4.0) (2015-06-11)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.3.1...0.4.0)

**Closed issues:**

- play 2.4 stable release [\#15](https://github.com/fdimuccio/play2-sockjs/issues/15)

## [0.3.1](https://github.com/fdimuccio/play2-sockjs/tree/0.3.1) (2014-09-13)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.6...0.3.1)

## [0.1.6](https://github.com/fdimuccio/play2-sockjs/tree/0.1.6) (2014-09-13)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.6...0.1.6)

## [0.2.6](https://github.com/fdimuccio/play2-sockjs/tree/0.2.6) (2014-09-13)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.3.0...0.2.6)

## [0.3.0](https://github.com/fdimuccio/play2-sockjs/tree/0.3.0) (2014-06-30)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.5...0.3.0)

**Closed issues:**

- Play 2.3.0 support [\#2](https://github.com/fdimuccio/play2-sockjs/issues/2)

## [0.2.5](https://github.com/fdimuccio/play2-sockjs/tree/0.2.5) (2014-06-22)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.5...0.2.5)

## [0.1.5](https://github.com/fdimuccio/play2-sockjs/tree/0.1.5) (2014-06-22)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.4...0.1.5)

**Merged pull requests:**

- Java Actors API [\#7](https://github.com/fdimuccio/play2-sockjs/pull/7) ([arielscarpinelli](https://github.com/arielscarpinelli))
- Enables tests back. Adds Tests for Java API [\#6](https://github.com/fdimuccio/play2-sockjs/pull/6) ([arielscarpinelli](https://github.com/arielscarpinelli))

## [0.2.4](https://github.com/fdimuccio/play2-sockjs/tree/0.2.4) (2014-06-19)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.4...0.2.4)

## [0.1.4](https://github.com/fdimuccio/play2-sockjs/tree/0.1.4) (2014-06-19)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.3...0.1.4)

**Merged pull requests:**

- Fixes Java API to work with the new tryAccept API [\#5](https://github.com/fdimuccio/play2-sockjs/pull/5) ([arielscarpinelli](https://github.com/arielscarpinelli))

## [0.2.3](https://github.com/fdimuccio/play2-sockjs/tree/0.2.3) (2014-06-16)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.3...0.2.3)

## [0.1.3](https://github.com/fdimuccio/play2-sockjs/tree/0.1.3) (2014-06-16)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.2...0.1.3)

**Implemented enhancements:**

- provide java api [\#1](https://github.com/fdimuccio/play2-sockjs/issues/1)

**Merged pull requests:**

- Support Java 8 Lambda Expression syntax [\#4](https://github.com/fdimuccio/play2-sockjs/pull/4) ([arielscarpinelli](https://github.com/arielscarpinelli))
- Fixes tests [\#3](https://github.com/fdimuccio/play2-sockjs/pull/3) ([arielscarpinelli](https://github.com/arielscarpinelli))

## [0.1.2](https://github.com/fdimuccio/play2-sockjs/tree/0.1.2) (2014-04-27)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.2...0.1.2)

## [0.2.2](https://github.com/fdimuccio/play2-sockjs/tree/0.2.2) (2014-04-27)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1.1...0.2.2)

## [0.1.1](https://github.com/fdimuccio/play2-sockjs/tree/0.1.1) (2014-04-19)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2.1...0.1.1)

## [0.2.1](https://github.com/fdimuccio/play2-sockjs/tree/0.2.1) (2014-04-13)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.2...0.2.1)

## [0.2](https://github.com/fdimuccio/play2-sockjs/tree/0.2) (2014-04-02)
[Full Changelog](https://github.com/fdimuccio/play2-sockjs/compare/0.1...0.2)

Play 2.2.x support

## [0.1](https://github.com/fdimuccio/play2-sockjs/tree/0.1) (2014-04-01)

Initial release, Play 2.1.x support