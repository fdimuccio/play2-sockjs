package controllers.actorj;

import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;

public class Closed extends SockJSRouter {
    public SockJS sockjs() {
        return Utils.Handlers.closed();
    }
}