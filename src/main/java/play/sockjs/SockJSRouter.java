package play.sockjs;

import java.lang.annotation.Annotation;

import play.libs.F;

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

    public static class Builder {

        final Class<? extends ScriptLocation> script;
        final Class<? extends CookieCalculator> cookies;
        final boolean websocket;
        final long heartbeatMillis;
        final long sessionTimeoutMillis;
        final long streamingQuotaBytes;

        private Builder() {
            this(ScriptLocation.DefaultCdn.class, CookieCalculator.None.class, true, 25000, 5000, 128*1204);
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

        public SockJSRouter whenReady(final SockJS sockJs) {
            F.Option<SockJS.Settings> cfg = new F.Some<SockJS.Settings>(new SockJS.Settings() {
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
            });
            return new SockJSRouter(cfg) {
                public SockJS sockjs() {
                    return sockJs;
                }
            };
        }

    }

}
