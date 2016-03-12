package streams

import akka.stream.scaladsl._

import play.sockjs.api.Frame

object FlowX {
  def echo: Flow[Frame, Frame, _] = Flow[Frame]
  def closed: Flow[Frame, Frame, _] = Flow.fromSinkAndSource(Sink.ignore, Source.empty[Frame])
}
