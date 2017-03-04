package controllers.actorj;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.sockjs.CookieFunctions;
import play.sockjs.SockJS;
import play.sockjs.SockJSSettings;

class Utils {
    static class Settings {
        public static SockJSSettings base = new SockJSSettings().withStreamingQuota(4096);
        public static SockJSSettings noWebSocket = base.withWebsocket(false);
        public static SockJSSettings withJSessionId = base.withCookies(CookieFunctions.jessionid);
    }
    static class Handlers {
        public static class Echo extends UntypedActor {
            private final ActorRef out;
            public Echo(ActorRef out) {
                this.out = out;
            }
            public void onReceive(Object message) throws Exception {
                out.tell(message, self());
            }
        }
        public static class Closed extends UntypedActor {
            final ActorRef out;
            public Closed(ActorRef out) {
                this.out = out;
            }
            @Override
            public void preStart() throws Exception {
                self().tell(PoisonPill.getInstance(), self());
            }
            @Override
            public void onReceive(Object arg0) throws Exception {}
        }
        public static SockJS echo() { throw new RuntimeException("NYI"); }//SockJS.withActor(out -> Props.create(Echo.class, out));
        public static SockJS closed() { throw new RuntimeException("NYI"); }//SockJS.withActor(out -> Props.create(Closed.class, out));
    }
}