package controllers;

import play.mvc.*;

import play.sockjs.*;

public class ApplicationJ extends Controller {

    static class SockJSEcho extends SockJS {
        public void onReady(SockJS.In in, final SockJS.Out out) {
            in.onMessage(s -> out.write(s));
        };
    };

    public static SockJSRouter echo = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096)
        public SockJS sockjs() {
            return new SockJSEcho();
        };
    };

    public static SockJSRouter closed = new SockJSRouter() {
        public SockJS sockjs() {
            return new SockJS() {
                public void onReady(SockJS.In in, SockJS.Out out) {
                    out.close();
                };
            };
        };
    };

    public static SockJSRouter disabledWebSocketEcho = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096, websocket = false)
        public SockJS sockjs() {
            return new SockJSEcho();
        };
    };

    public static SockJSRouter cookieNeededEcho = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096, cookies = CookieCalculator.JSESSIONID.class)
        public SockJS sockjs() {
            return new SockJSEcho();
        };
    };

};