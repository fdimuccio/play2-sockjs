package controllers;

import play.libs.F;
import play.sockjs.*;

public class ApplicationJ {

    static class SockJSEcho extends SockJS<String> {
        public void onReady(SockJS.In<String> in, final SockJS.Out<String> out) {
            in.onMessage(new F.Callback<String>() {
                public void invoke(String s) {
                    out.write(s);
                };
            });
        };
    };

    public static SockJSRouter echo = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096)
        public SockJS<String> sockjs() {
            return new SockJSEcho();
        };

    };

    public static SockJSRouter closed = new SockJSRouter() {
        public SockJS<String> sockjs() {
            return new SockJS<String>() {
                public void onReady(SockJS.In<String> in, SockJS.Out<String> out) {
                    out.close();
                };
            };
        };
    };

    public static SockJSRouter disabledWebSocketEcho = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096, websocket = false)
        public SockJS<String> sockjs() {
            return new SockJSEcho();
        };
    };

    public static SockJSRouter cookieNeededEcho = new SockJSRouter() {
        @SockJS.Settings(streamingQuota = 4096)
        public SockJS<String> sockjs() {
            return new SockJSEcho();
        };
    };

};