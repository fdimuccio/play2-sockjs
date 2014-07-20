package modules

import akka.actor.ActorSystem

import play.api.libs.concurrent.Akka

import models.ChatRoom

trait ChatRoomModule {
  val actorSystem: ActorSystem = Akka.system(play.api.Play.current)
  val chatRoom: ChatRoom = new ChatRoom(actorSystem)
}
