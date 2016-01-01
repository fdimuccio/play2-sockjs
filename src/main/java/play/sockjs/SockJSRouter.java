package play.sockjs;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import play.mvc.Http;

public abstract class SockJSRouter extends play.sockjs.core.j.JavaRouter {

    public SockJSRouter() {
        super(Optional.empty());
    }

    private SockJSRouter(Optional<SockJS.Settings> cfg) {
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

    public static SockJSRouter whenReady(final BiConsumer<SockJS.In, SockJS.Out> callback) {
        return new Builder().whenReady(callback);
    }

    public static SockJSRouter withActor(final Function<ActorRef, Props> props) {
        return new Builder().withActor(props);
    }

    public static SockJSRouter withActor(final Class<? extends UntypedActor> actorClass) {
        return new Builder().withActor(actorClass);
    }

    public static SockJSRouter tryAccept(final Function<Http.Context, SockJS> sockjs)  {
        return new Builder().tryAccept(sockjs);
    }

    public static class Builder {

        final Class<? extends ScriptLocation> script;
        final Class<? extends CookieCalculator> cookies;
        final boolean websocket;
        final long heartbeatMillis;
        final long sessionTimeoutMillis;
        final long streamingQuotaBytes;
        final int sendBufferSize;
        final int sessionBufferSize;

        private Builder() {
            this(ScriptLocation.DefaultCdn.class, CookieCalculator.None.class, true, 25000, 5000, 128*1024, 256, 64*1024);
        }

        private Builder(Class<? extends ScriptLocation> script, Class<? extends CookieCalculator> cookies, boolean websocket, long heartbeatMillis, long sessionTimeoutMillis, long streamingQuotaBytes, int sendBufferSize, int sessionBufferSize) {
            this.script = script;
            this.cookies = cookies;
            this.websocket = websocket;
            this.heartbeatMillis = heartbeatMillis;
            this.sessionTimeoutMillis = sessionTimeoutMillis;
            this.streamingQuotaBytes = streamingQuotaBytes;
            this.sendBufferSize = sendBufferSize;
            this.sessionBufferSize = sessionBufferSize;
        }

        public Builder withScript(Class<? extends ScriptLocation> script) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes, sendBufferSize, sessionBufferSize);
        };

        public Builder withCookies(Class<? extends CookieCalculator> cookies) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes, sendBufferSize, sessionBufferSize);
        }

        public Builder withWebSocket(boolean enabled) {
            return new Builder(script, cookies, enabled, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes, sendBufferSize, sessionBufferSize);
        }

        public Builder withHeartbeat(long millis) {
            return new Builder(script, cookies, websocket, millis, sessionTimeoutMillis, streamingQuotaBytes, sendBufferSize, sessionBufferSize);
        }

        public Builder withSessionTimeout(long millis) {
            return new Builder(script, cookies, websocket, heartbeatMillis, millis, streamingQuotaBytes, sendBufferSize, sessionBufferSize);
        }

        public Builder withStreamingQuota(long bytes) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, bytes, sendBufferSize, sessionBufferSize);
        }

        public Builder withSendBufferSize(int size) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes, size, sessionBufferSize);
        }

        public Builder withSessionBufferSize(int size) {
            return new Builder(script, cookies, websocket, heartbeatMillis, sessionTimeoutMillis, streamingQuotaBytes, sendBufferSize, size);
        }

        protected Optional<SockJS.Settings> asSettings() {
        	return Optional.<SockJS.Settings>of(new SockJS.Settings() {
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
                public int sendBufferSize() {
                    return sendBufferSize;
                }
                @Override
                public int sessionBufferSize() {
                    return sessionBufferSize;
                }
                @Override
                public Class<? extends Annotation> annotationType() {
                    return SockJS.Settings.class;
                }
            });
        }
        
        public SockJSRouter whenReady(final BiConsumer<SockJS.In, SockJS.Out> callback) {
            if (callback == null) throw new NullPointerException("SockJS onReady callback cannot be null");
            return new SockJSRouter(asSettings()) {
                public SockJS sockjs() {
                    return SockJS.whenReady(callback);
                }
            };
        }

        public SockJSRouter withActor(final Class<? extends UntypedActor> actorClass) {
        	return withActor(out -> Props.create(actorClass, out));
        }
        
        public SockJSRouter withActor(final Function<ActorRef, Props> props) {
        	return new SockJSRouter(asSettings()) {
                public SockJS sockjs() {
                    return SockJS.withActor(props);
                }
            };
        }

        public SockJSRouter tryAccept(final Function<Http.Context, SockJS> sockjs) {
            return new SockJSRouter(asSettings()) {
                public SockJS sockjs() {
                    try {
                        return sockjs.apply(Http.Context.current());
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Error e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
            };
        }

    }

}
