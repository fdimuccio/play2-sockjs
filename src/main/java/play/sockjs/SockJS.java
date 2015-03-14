package play.sockjs;

import java.lang.annotation.*;
import java.util.*;

import akka.actor.ActorRef;
import akka.actor.Props;

import play.mvc.*;
import play.libs.F.*;

public abstract class SockJS {

    abstract public void onReady(In in, Out out);

    public Result rejectWith() {
        return null;
    }

    public boolean isActor() {
        return false;
    }

    public Props actorProps(ActorRef out) {
        return null;
    }

    public static interface Out {

        public void write(String message);

        public void close();

    }

    public static class In {

        /**
         * Callbacks to invoke at each frame.
         */
        public final List<Callback<String>> callbacks = new ArrayList<Callback<String>>();

        /**
         * Callbacks to invoke on close.
         */
        public final List<Callback0> closeCallbacks = new ArrayList<Callback0>();

        /**
         * Registers a message callback.
         */
        public void onMessage(Callback<String> callback) {
            callbacks.add(callback);
        }

        /**
         * Registers a close callback.
         */
        public void onClose(Callback0 callback) {
            closeCallbacks.add(callback);
        }

    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Settings {
        Class<? extends ScriptLocation> script() default ScriptLocation.DefaultCdn.class;
        Class<? extends CookieCalculator> cookies() default CookieCalculator.None.class;
        boolean websocket() default true;
        long heartbeat() default 25000;
        long sessionTimeout() default 5000;
        long streamingQuota() default 128*1024;
    }

    /**
     * Creates a SockJS handler. The abstract {@code onReady} method is
     * implemented using the specified {@code Callback2<In<A>, Out<A>>}
     *
     * @param callback the callback used to implement onReady
     * @return a new WebSocket
     * @throws NullPointerException if the specified callback is null
     */
    public static SockJS whenReady(final Callback2<SockJS.In, SockJS.Out> callback) {
        return new WhenReadySockJS(callback);
    }

    /**
     * Rejects a SockJS request.
     *
     * @param result The result that will be returned.
     * @return A rejected SockJS handler.
     */
    public static SockJS reject(final Result result) {
        return new SockJS() {
            public void onReady(In in, Out out) {
            }
            @Override
            public Result rejectWith() {
                return result;
            }
        };
    }

    /**
     * Handles a SockJS with an actor.
     *
     * @param props The function used to create the props for the actor.  The passed in argument is the upstream actor.
     * @return An actor SockJS.
     */
    public static SockJS withActor(final Function<ActorRef, Props> props) {
        return new SockJS() {
            public void onReady(In in, Out out) {
            }
            @Override
            public boolean isActor() {
                return true;
            }
            @Override
            public Props actorProps(ActorRef out) {
                try {
                    return props.apply(out);
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

    static final class WhenReadySockJS extends SockJS {

        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WhenReadySockJS.class);

        private final Callback2<SockJS.In, SockJS.Out> callback;

        WhenReadySockJS(Callback2<SockJS.In, SockJS.Out> callback) {
            if (callback == null) throw new NullPointerException("SockJS onReady callback cannot be null");
            this.callback = callback;
        }

        @Override
        public void onReady(In in, Out out) {
            try {
                callback.invoke(in, out);
            } catch (Throwable e) {
                logger.error("Exception in SockJS.onReady", e);
            }
        }
    }
}
