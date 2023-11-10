package controllers.actorj;

import org.apache.pekko.actor.*;

import org.apache.pekko.stream.OverflowStrategy;

import play.sockjs.CookieFunctions;
import play.sockjs.SockJS;
import play.sockjs.SockJSSettings;
import play.sockjs.api.libs.streams.ActorFlow;

class Utils {

    static class Settings {
        public static SockJSSettings base = new SockJSSettings().withStreamingQuota(4096);
        public static SockJSSettings noWebSocket = base.withWebsocket(false);
        public static SockJSSettings withJSessionId = base.withCookies(CookieFunctions.jessionid);
    }

    static class Handlers {

        public static class Echo extends AbstractActor {

            private final ActorRef out;

            public Echo(ActorRef out) {
                this.out = out;

            }

            @Override
            public Receive createReceive() {
                return receiveBuilder()
                    .match(String.class, msg -> out.tell(msg, self()))
                    .build();
            }
        }

        public static class Closed extends AbstractActor {

            final ActorRef out;

            public Closed(ActorRef out) {
                this.out = out;
            }

            @Override
            public void preStart() throws Exception {
                self().tell(PoisonPill.getInstance(), self());
            }

            @Override
            public Receive createReceive() {
                return receiveBuilder().build();
            }
        }

        public static SockJS echo(ActorSystem as) {
            return SockJS.Text.accept(req -> ActorFlow.<String, String>actorRef(
                    out -> Props.create(Echo.class, out),
                    16,
                    OverflowStrategy.dropNew(),
                    as).asJava());
        }

        public static SockJS closed(ActorSystem as) {
            return SockJS.Text.accept(req -> ActorFlow.<String, String>actorRef(
                    out -> Props.create(Closed.class, out),
                    16,
                    OverflowStrategy.dropNew(),
                    as).asJava());
        }
    }
}