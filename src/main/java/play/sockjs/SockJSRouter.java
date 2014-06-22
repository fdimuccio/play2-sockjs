package play.sockjs;

import java.lang.annotation.Annotation;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import play.libs.F;
import play.mvc.Result; 
import play.mvc.Http.Request;

public abstract class SockJSRouter extends play.sockjs.core.j.JavaRouter {

    public SockJSRouter() {
        super(F.None());
    }

    private SockJSRouter(F.Option<SockJS.Settings> cfg) {
        super(cfg);
    }

    public static Builder withScript(Class<? extends ScriptLocation> script) {
        return new Builder().withScript(script);
    }

    public static Builder withCookies(Class<? extends CookieCalculator> cookies) {
        return new Builder().withCookies(cookies);
    }

    public static Builder withWebSocket(boolean enabled) {
        return new Builder().withWebSocket(enabled);
    }

    public static Builder withHeartbeat(long millis) {
        return new Builder().withHeartbeat(millis);
    }

    public static Builder withSessionTimeout(long millis) {
        return new Builder().withSessionTimeout(millis);
    }

    public static Builder withStreamingQuota(long bytes) {
        return new Builder().withStreamingQuota(bytes);
    }

    public static SockJSRouter whenReady(final SockJS sockJs) {
        return new Builder().whenReady(sockJs);
    }

    public static SockJSRouter withActor(final F.Function<ActorRef, Props> props) {
        return new Builder().withActor(props);
    }

    public static SockJSRouter withActor(final Class<? extends UntypedActor> actorClass) {
        return new Builder().withActor(actorClass);
    }
    
    public static SockJSRouter tryAcceptWithActor(F.Function<Request, F.Either<Result, F.Function<ActorRef, Props>>> resultOrProps) {
        return new Builder().tryAcceptWithActor(resultOrProps);
    }

    public static class Builder {

        final Class<? extends ScriptLocation> script;
        final Class<? extends CookieCalculator> cookies;
        final boolean websocket;
        final long heartbeatMillis;
        final long sessionTimeoutMillis;
        final long streamingQuotaBytes;

        private Builder() {
            this(ScriptLocation.DefaultCdn.class, CookieCalculator.None.class, true, 25000, 5000, 128*1024);
        }

        private Builder(Class<? extends ScriptLocation> script, Class<? extends CookieCalculator> cookies, boolean websocket, long heartbeatMillis, long sessionTimeoutMillis, long streamingQuotaBytes) {
            this.script = script;
            this.cookies = cookies;
            this.websocket = websocket;
            this.heartbeatMillis = heartbeatMillis;
            this.sessionTimeoutMillis = sessionTimeoutMillis;
            this.streamingQuotaBytes = streamingQuotaBytes;
        }

        public Builder withScript(Class<? extends ScriptLocation> script) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes);
        };

        public Builder withCookies(Class<? extends CookieCalculator> cookies) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes);
        }

        public Builder withWebSocket(boolean enabled) {
            return new Builder(script, cookies, enabled, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes);
        }

        public Builder withHeartbeat(long millis) {
            return new Builder(script, cookies, websocket, millis, sessionTimeoutMillis, streamingQuotaBytes);
        }

        public Builder withSessionTimeout(long millis) {
            return new Builder(script, cookies, websocket, heartbeatMillis, millis, streamingQuotaBytes);
        }

        public Builder withStreamingQuota(long bytes) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, bytes);
        }

        protected SockJS.Settings asSettings() {
        	return new SockJS.Settings() {
                @Override
                public Class<? extends ScriptLocation> script() {
                    return script;
                }
                @Override
                public Class<? extends CookieCalculator> cookies() {
                    return cookies;
                }
                @Override
                public boolean websocket() {
                    return websocket;
                }
                @Override
                public long heartbeat() {
                    return heartbeatMillis;
                }
                @Override
                public long sessionTimeout() {
                    return sessionTimeoutMillis;
                }
                @Override
                public long streamingQuota() {
                    return streamingQuotaBytes;
                }
                @Override
                public Class<? extends Annotation> annotationType() {
                    return SockJS.Settings.class;
                }
            };        	
        }
        
        public SockJSRouter whenReady(final SockJS sockJs) {
            F.Option<SockJS.Settings> cfg = new F.Some<SockJS.Settings>(asSettings());
            return new SockJSRouter(cfg) {
                public SockJS sockjs() {
                    return sockJs;
                }
            };
        }

        public SockJSRouter withActor(final Class<? extends UntypedActor> actorClass) {
        	return withActor(new F.Function<ActorRef, Props>() {
        		public Props apply(ActorRef out) {
        			return Props.create(actorClass, out);
        		}
        	});
        }
        
        public SockJSRouter withActor(final F.Function<ActorRef, Props> props) {
        	return tryAcceptWithActor(new F.Function<Request, F.Either<Result, F.Function<ActorRef, Props>>>() {
                public F.Either<Result, F.Function<ActorRef, Props>> apply(Request request) throws Throwable {
                    return F.Either.Right(props);
                }
            });
        }

        public SockJSRouter tryAcceptWithActor(final F.Function<Request, F.Either<Result, F.Function<ActorRef, Props>>> resultOrProps) {
            F.Option<SockJS.Settings> cfg = new F.Some<SockJS.Settings>(asSettings());
            return new SockJSRouter(cfg) {
                public SockJS sockjs() {
                    return null;
                }
                
                public boolean isActor() {
                	return true;
                }
                
                public F.Function<Request, F.Either<Result, F.Function<ActorRef, Props>>> resultOrProps() {
                	return resultOrProps;
                }
            };
        }
    }

}
