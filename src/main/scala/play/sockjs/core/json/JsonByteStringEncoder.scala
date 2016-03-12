package play.sockjs.core.json

import akka.util.{ByteStringBuilder, ByteString}

import com.fasterxml.jackson.core.{JsonGenerator, JsonEncoding, JsonFactory}

import play.sockjs.api.Frame._
import play.sockjs.api.Frame.Text._

private[sockjs] object JsonByteStringEncoder {

  private val jsonFactory = new JsonFactory(play.libs.Json.mapper())

  def asJsonString(bytes: ByteString): ByteString = using { (out, gen) =>
    val arr = bytes.toArray
    gen.writeUTF8String(arr, 0, arr.length)
  }

  def asJsonArray(frame: Text): ByteString = using { (out, gen) =>
    gen.enable(JsonGenerator.Feature.ESCAPE_NON_ASCII)

    val data = frame.data
    val size = data.size
    gen.writeStartArray(size)
    var i = 0
    while(i < size) {
      gen.writeString(data(i))
      i += 1
    }
    gen.writeEndArray()
  }

  def asJsonArray(frame: Close): ByteString = using { (out, gen) =>
    gen.writeStartArray(2)
    gen.writeNumber(frame.code)
    gen.writeString(frame.reason)
    gen.writeEndArray()
  }

  private def using(f: (ByteStringBuilder, JsonGenerator) => Unit): ByteString = {
    val out = new ByteStringBuilder
    val gen = jsonFactory.createGenerator(out.asOutputStream, JsonEncoding.UTF8)
    f(out, gen)
    gen.flush()
    out.result()
  }
}
