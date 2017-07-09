package controllers.actorj;

import akka.actor.ActorSystem;
import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;

import javax.inject.Inject;

public class Closed extends SockJSRouter {

    final private ActorSystem actorSystem;

    @Inject
    public Closed(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public SockJS sockjs() {
        return Utils.Handlers.closed(actorSystem);
    }
}