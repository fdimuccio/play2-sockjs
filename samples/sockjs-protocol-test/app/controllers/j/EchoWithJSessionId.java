package controllers.j;

import play.sockjs.*;

public class EchoWithJSessionId extends SockJSRouter {

    @Override
    public SockJSSettings settings() {
        return Utils.Settings.withJSessionId;
    }

    public SockJS sockjs() {
        return Utils.Handlers.echo;
    }
}