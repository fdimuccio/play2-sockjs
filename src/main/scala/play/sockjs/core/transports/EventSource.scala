package play.sockjs.core
package transports

import play.api.libs.iteratee._
import play.api.libs.{EventSource => PlayEventSource}
import play.api.http.HeaderNames
import play.api.mvc.Results

/**
 * EventSource transport
 */
object EventSource extends HeaderNames with Results {

  def transport = Transport.Streaming { (req, session) =>
    session.bind { (enumerator, _) =>
      Ok.stream(Enumerator("\r\n") >>> (enumerator &> PlayEventSource[Frame]()))
        .notcached
        .as("text/event-stream; charset=UTF-8")
    }
  }

}
