package controllers.j;

import play.sockjs.*;

class Utils {
    static class Settings {
        public static SockJSSettings base = new SockJSSettings().withStreamingQuota(4096);
        public static SockJSSettings noWebSocket = base.withWebsocket(false);
        public static SockJSSettings withJSessionId = base.withCookies(CookieFunctions.jessionid);
    }
    static class Handlers {
        public static SockJS echo = SockJS.whenReady((in, out) -> in.onMessage(out::write));
        public static SockJS closed = SockJS.whenReady((in, out) -> out.close());
    }
}