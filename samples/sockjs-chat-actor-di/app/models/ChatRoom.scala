package models

import scala.concurrent.duration._

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._

import play.api._
import play.api.libs.json._
import play.api.libs.streams._

/**
 * ChatRoom service
 */
class ChatRoom(akka: ActorSystem, materializer: Materializer) {

  private val room = {
    val roomActor = akka.actorOf(Props[ChatRoomActor])
    akka.actorOf(Props(new Robot(roomActor)))
    roomActor
  }

  def join(username: String): Flow[JsValue, JsValue, _] =
    ActorFlow.actorRef(out => Props(new ChatRoomMember(username, room, out)), 256)(akka, materializer)

}

/**
 * Robot actor, will send "I'm still alive" every 30 seconds
 */
class Robot(room: ActorRef) extends Actor {

  import context.dispatcher

  room ! Join("Robot", self)

  def receive = {
    case json => Logger("robot").info(json.toString)
  }

  context.system.scheduler.schedule(
    30.seconds,
    30.seconds,
    room,
    Talk("Robot", "I'm still alive")
  )

}

/**
 * A ChatRoom user, will receive messages from the socket and forward them to the room
 */
class ChatRoomMember(username: String, room: ActorRef, out: ActorRef) extends Actor {

  context.watch(out)

  room ! Join(username, out)

  private var connected = false

  def receive = {

    case Connected(welcome) =>
      out ! welcome
      connected = true

    case CannotConnect(error) =>
      out ! Json.obj("error" -> error)
      context.stop(self)

    case json: JsValue if connected =>
      room ! Talk(username, (json \ "text").as[String])

    case akka.actor.Terminated(`out`) =>
      context.stop(self)
  }

  override def postStop() {
    super.postStop()

    if (connected)
      room ! Quit(username)
  }

}

/**
 * The ChatRoom
 */
class ChatRoomActor extends Actor {
  
  var members = Map.empty[String, ActorRef]

  def receive = {
    
    case Join(username, out) => {
      if(members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members = members + (username -> out)
        sender ! Connected(Json.obj("members" -> members.keys))
        self ! NotifyJoin(username)
      }
    }

    case NotifyJoin(username) => {
      notifyAll("join", username, "has entered the room")
    }
    
    case Talk(username, text) => {
      notifyAll("talk", username, text)
    }
    
    case Quit(username) => {
      members = members - username
      notifyAll("quit", username, "has left the room")
    }
    
  }
  
  def notifyAll(kind: String, user: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user),
        "message" -> JsString(text),
        "members" -> JsArray(
          members.keys.toList.map(JsString)
        )
      )
    )
    members.values.foreach(_ ! msg)
  }
  
}

case class Join(username: String, out: ActorRef)
case class Quit(username: String)
case class Talk(username: String, text: String)
case class NotifyJoin(username: String)

case class Connected(welcome: JsValue)
case class CannotConnect(msg: String)
