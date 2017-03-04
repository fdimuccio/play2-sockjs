package controllers.j;

import akka.stream.javadsl.*;
import play.sockjs.*;

class Utils {
    static class Settings {
        public static SockJSSettings base = new SockJSSettings().withStreamingQuota(4096);
        public static SockJSSettings noWebSocket = base.withWebsocket(false);
        public static SockJSSettings withJSessionId = base.withCookies(CookieFunctions.jessionid);
    }
    static class Handlers {
        public static SockJS echo = SockJS.Text.accept(req -> Flow.create());
        public static SockJS closed = SockJS.Text.accept(req -> Flow.fromSinkAndSource(Sink.ignore(), Source.empty()));
    }
}