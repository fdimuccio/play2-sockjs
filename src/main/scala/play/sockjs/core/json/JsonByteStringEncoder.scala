package play.sockjs.core.json

import akka.util.{ByteStringBuilder, ByteString}

import com.fasterxml.jackson.core.{JsonGenerator, JsonEncoding, JsonFactory}
import com.fasterxml.jackson.databind.ObjectMapper

import play.sockjs.api.Frame
import play.sockjs.api.Frame._

private[sockjs] object JsonByteStringEncoder {

  private val mapper = new ObjectMapper
  private val jsonFactory = new JsonFactory(mapper)

  def encodeFrame(frame: Frame): ByteString = {
    val (out, gen) = init()

    val bytes = frame.encode.toArray
    gen.writeUTF8String(bytes, 0, bytes.length)
    gen.flush()

    out.result()
  }

  def encodeMessageFrame(frame: MessageFrame): ByteString = {
    val (out, gen) = init()
    gen.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)

    gen.writeStartArray(frame.data.size)
    for(data <- frame.data) {
      gen.writeString(data)
    }
    gen.writeEndArray()
    gen.flush()

    out.result()
  }

  def encodeCloseFrame(frame: CloseFrame): ByteString = {
    val (out, gen) = init()

    gen.writeStartArray(2)
    gen.writeNumber(frame.code)
    gen.writeString(frame.reason)
    gen.writeEndArray()
    gen.flush()

    out.result()
  }

  private def init(): (ByteStringBuilder, JsonGenerator) = {
    val out = new ByteStringBuilder
    val gen = jsonFactory.createGenerator(out.asOutputStream, JsonEncoding.UTF8)
    (out, gen)
  }
}
