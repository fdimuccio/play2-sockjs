package controllers.j;

import play.sockjs.*;

public class EchoWithNoWebsocket extends SockJSRouter {

    @Override
    public SockJSSettings settings() {
        return Utils.Settings.noWebSocket;
    }

    public SockJS sockjs() {
        return Utils.Handlers.echo;
    }
}