package streams

import akka.stream.scaladsl._

import play.sockjs.api.Frame

object FlowX {
  def echo: Flow[String, Frame, _] = Flow[String].map(Frame.MessageFrame.apply)
  def closed: Flow[String, Frame, _] = Flow.fromSinkAndSource(Sink.ignore, Source.empty[Frame])
}
