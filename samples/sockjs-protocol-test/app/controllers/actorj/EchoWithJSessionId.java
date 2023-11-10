package controllers.actorj;

import javax.inject.Inject;

import org.apache.pekko.actor.ActorSystem;

import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;
import play.sockjs.SockJSSettings;

public class EchoWithJSessionId extends SockJSRouter {

    final private ActorSystem actorSystem;

    @Inject
    public EchoWithJSessionId(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public SockJSSettings settings() {
        return Utils.Settings.withJSessionId;
    }

    public SockJS sockjs() {
        return Utils.Handlers.echo(actorSystem);
    }
}