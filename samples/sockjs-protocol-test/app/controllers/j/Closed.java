package controllers.j;

import play.sockjs.*;

public class Closed extends SockJSRouter {
    public SockJS sockjs() {
        return Utils.Handlers.closed;
    }
}