package play.sockjs;

import java.lang.annotation.*;
import java.util.*;

import play.libs.F.*;

public interface SockJS {

    public void onReady(In in, Out out);

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
}
