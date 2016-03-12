package controllers.j;

import play.sockjs.*;

public class Echo extends SockJSRouter {

    @Override
    public SockJSSettings settings() {
        return Utils.Settings.base;
    }

    public SockJS sockjs() {
        return Utils.Handlers.echo;
    }
}