package controllers.actorj;

import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;
import play.sockjs.SockJSSettings;

public class EchoWithNoWebsocket extends SockJSRouter {

    @Override
    public SockJSSettings settings() {
        return Utils.Settings.noWebSocket;
    }

    public SockJS sockjs() {
        return Utils.Handlers.echo;
    }
}