package play.sockjs.core.json

import org.apache.pekko.util.{ByteString, ByteStringBuilder}

import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.core.{JsonEncoding, JsonFactory, JsonGenerator}

import play.sockjs.api.Frame._

private[sockjs] object JsonByteStringEncoder {

  private val jsonFactory = new JsonFactory(play.libs.Json.mapper())

  def asJsonString(bytes: ByteString): ByteString = using { gen =>
    val arr = bytes.toArray
    gen.writeUTF8String(arr, 0, arr.length)
  }

  def asJsonArray(frame: Text): ByteString = using { gen =>
    gen.enable(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature())

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

  def asJsonArray(frame: Close): ByteString = using { gen =>
    gen.writeStartArray(2)
    gen.writeNumber(frame.code)
    gen.writeString(frame.reason)
    gen.writeEndArray()
  }

  private def using(f: JsonGenerator => Unit): ByteString = {
    val out = new ByteStringBuilder
    val gen = jsonFactory.createGenerator(out.asOutputStream, JsonEncoding.UTF8)
    f(gen)
    gen.flush()
    out.result()
  }
}
