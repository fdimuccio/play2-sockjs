import play.api.ApplicationLoader.Context
import router.Routes
import play.api.{ApplicationLoader, BuiltInComponentsFromContext}
import play.api.routing.Router
import com.softwaremill.macwire.wire

class ChatRoomApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    new ChatRoomComponents(context).application
  }
}

class ChatRoomComponents(context: Context) extends BuiltInComponentsFromContext(context) {
  lazy val chatRoom = wire[models.ChatRoom]
  lazy val applicationController = wire[controllers.Application]
  lazy val assets = wire[controllers.Assets]
  lazy val prefix = "/"
  lazy val router: Router = wire[Routes]
}