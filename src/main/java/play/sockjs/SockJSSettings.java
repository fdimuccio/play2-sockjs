package play.sockjs;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import play.mvc.Http;

public class SockJSSettings {

    private final Function<Http.RequestHeader, String> scriptSRC;
    private final boolean websocket;
    private final Optional<Function<Http.RequestHeader, Http.Cookie>> cookies;
    private final FiniteDuration heartbeat;
    private final FiniteDuration sessionTimeout;
    private final long streamingQuota;
    private final int sendBufferSize;
    private final int sessionBufferSize;

    public SockJSSettings(
            Function<Http.RequestHeader, String> scriptSRC, boolean websocket,
            Optional<Function<Http.RequestHeader, Http.Cookie>> cookies, FiniteDuration heartbeat,
            FiniteDuration sessionTimeout, long streamingQuota, int sendBufferSize, int sessionBufferSize) {
        this.scriptSRC = scriptSRC;
        this.websocket = websocket;
        this.cookies = cookies;
        this.heartbeat = heartbeat;
        this.sessionTimeout = sessionTimeout;
        this.streamingQuota = streamingQuota;
        this.sendBufferSize = sendBufferSize;
        this.sessionBufferSize = sessionBufferSize;
    }

    public SockJSSettings() {
        this(
            request -> "//cdn.jsdelivr.net/sockjs/1.0.3/sockjs.min.js",
            true,
            Optional.empty(),
            Duration.create(25, TimeUnit.SECONDS),
            Duration.create(5, TimeUnit.SECONDS),
            128*1024,
            256,
            64*1024
        );
    }

    public SockJSSettings withScriptSRC(Function<Http.RequestHeader, String> scriptSRC) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withWebsocket(Boolean websocket) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withCookies(Optional<Function<Http.RequestHeader, Http.Cookie>> cookies) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withCookies(Function<Http.RequestHeader, Http.Cookie> cookies) {
        return new SockJSSettings(scriptSRC, websocket, Optional.of(cookies), heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withHeartbeat(FiniteDuration heartbeat) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withSessionTimeout(FiniteDuration sessionTimeout) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withStreamingQuota(long streamingQuota) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withSendBufferSize(int sendBufferSize) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public SockJSSettings withSessionBufferSize(int sessionBufferSize) {
        return new SockJSSettings(scriptSRC, websocket, cookies, heartbeat,
                sessionTimeout, streamingQuota, sendBufferSize, sessionBufferSize);
    }

    public Function<Http.RequestHeader, String> scriptSRC() {
        return scriptSRC;
    }

    public boolean websocket() {
        return websocket;
    }

    public Optional<Function<Http.RequestHeader, Http.Cookie>> cookies() {
        return cookies;
    }

    public FiniteDuration heartbeat() {
        return heartbeat;
    }

    public FiniteDuration sessionTimeout() {
        return sessionTimeout;
    }

    public long streamingQuota() {
        return streamingQuota;
    }

    public int sendBufferSize() {
        return sendBufferSize;
    }

    public int sessionBufferSize() {
        return sessionBufferSize;
    }
}
