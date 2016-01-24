package models

import scala.concurrent.duration._

import akka.actor._
import akka.stream.{Materializer, Attributes, OverflowStrategy}

import play.api._
import play.api.libs.json._
import play.api.libs.concurrent._

import akka.stream.scaladsl._
import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

object Robot {
  
  def apply(chatRoom: ActorRef) {
    
    // Create an Iteratee that logs all messages to the console.
    val loggerSink = Sink.foreach[JsValue](event => Logger("robot").info(event.toString))
    
    implicit val timeout = Timeout(1 second)
    // Make the robot join the room
    chatRoom ? (Join("Robot")) map {
      case Connected(robotChannel) => 
        // Apply this Enumerator on the logger.
        robotChannel.to(loggerSink).run()(play.api.Play.current.materializer)
    }
    
    // Make the robot talk every 30 seconds
    Akka.system.scheduler.schedule(
      30 seconds,
      30 seconds,
      chatRoom,
      Talk("Robot", "I'm still alive")
    )
  }
  
}

object ChatRoom {
  
  implicit val timeout = Timeout(1 second)
  
  lazy val default = {
    val roomActor = Akka.system.actorOf(Props(new ChatRoom(play.api.Play.materializer)))
    
    // Create a bot user (just for fun)
    Robot(roomActor)
    
    roomActor
  }

  def join(username:String):scala.concurrent.Future[Flow[JsValue, JsValue, _]] = {

    (default ? Join(username)).map {
      
      case Connected(source) =>
      
        // Create a Sink to consume the feed
        val sink = Flow[JsValue].map { event =>
          default ! Talk(username, (event \ "text").as[String])
        }.to(Sink.onComplete { _ =>
          default ! Quit(username)
        })

        Flow.fromSinkAndSource(sink, source)
        
      case CannotConnect(error) => 
      
        // Connection error

        // A Sink that ignores incoming data
        val sink = Sink.ignore

        // Send an error and close the socket
        val source = Source.single(JsObject(Seq("error" -> JsString(error))))
        
        Flow.fromSinkAndSource(sink, source)
         
    }

  }
  
}

class ChatRoom(materializer: Materializer) extends Actor {
  
  var members = Set.empty[String]

  val (channel, publisher) =
    Source.actorRef[JsValue](1024, OverflowStrategy.dropNew)
      .toMat(Sink.asPublisher(true).withAttributes(Attributes.inputBuffer(256, 512)))(Keep.both)
      .run()(materializer)

  def receive = {
    
    case Join(username) => {
      if(members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members = members + username
        val motd = Json.obj(
          "members" -> members,
          "message" -> s"$username welcome to sockjs-chat!")
        sender ! Connected(Source.single(motd) concat Source.fromPublisher(publisher))
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
    val msg = Json.obj(
      "kind" -> kind,
      "user" -> user,
      "message" -> text,
      "members" -> members.toList.map(JsString))
    channel ! msg
  }
  
}

case class Join(username: String)
case class Quit(username: String)
case class Talk(username: String, text: String)
case class NotifyJoin(username: String)

case class Connected(source: Source[JsValue, _])
case class CannotConnect(msg: String)
