package streams

import akka.stream.scaladsl._

import play.sockjs.api.Frame

object FlowX {

  def echo: Flow[String, Frame, _] = Flow[String].map(Frame.MessageFrame.apply)

  def closed: Flow[String, Frame, _] = Flow[String, Frame]() { implicit b =>
    import FlowGraph.Implicits._
    val sink = b.add(Sink.ignore)
    val source = b.add(Source.empty[Frame])
    (sink.inlet, source.outlet)
  }
}
