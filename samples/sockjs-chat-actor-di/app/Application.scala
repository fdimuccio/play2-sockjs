import com.softwaremill.macwire.MacwireMacros._

import modules._

object Application extends ChatRoomModule {
  val applicationController = wire[controllers.Application]
}
