package play.sockjs;

import java.lang.annotation.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.Duration;

import play.libs.F.*;

public abstract class SockJS<A> {

    public abstract void onReady(In<A> in, Out<A> out);

    public static interface Out<A> {

        public void write(A frame);

        public void close();

    }

    public static class In<A> {

        /**
         * Callbacks to invoke at each frame.
         */
        public final List<Callback<A>> callbacks = new ArrayList<Callback<A>>();

        /**
         * Callbacks to invoke on close.
         */
        public final List<Callback0> closeCallbacks = new ArrayList<Callback0>();

        /**
         * Registers a message callback.
         */
        public void onMessage(Callback<A> callback) {
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
        //TODO: scriptSRC
        //TODO: cookies
        boolean websocket() default true;
        long heartbeat() default 25000;
        long sessionTimeout() default 5000;
        long streamingQuota() default 128*1024;
    }
}
