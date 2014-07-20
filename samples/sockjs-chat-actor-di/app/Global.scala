import com.softwaremill.macwire._
import play.api.GlobalSettings

object Global extends GlobalSettings with Macwire {

  /**
   * This must be a lazy val or else the Application module will fail to start because it needs
   * a running play.api.Play.current to initialize the ChatRoom
   */
  lazy val instanceLookup = InstanceLookup(valsByClass(Application))

  override def getControllerInstance[A](controllerClass: Class[A]) = instanceLookup.lookupSingleOrThrow(controllerClass)

}
