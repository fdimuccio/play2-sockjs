package play.sockjs;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Flow;

import com.fasterxml.jackson.databind.JsonNode;
import play.api.http.websocket.CloseCodes;
import play.libs.F;
import play.libs.Scala;
import play.libs.streams.AkkaStreams;
import play.mvc.*;
import scala.PartialFunction;

public abstract class SockJS {

    /**
     * Invoke the SockJS.
     *
     * @param request The request for the SockJS handler.
     * @return A future of either a result to reject the SockJS connection with, or a Flow to handle the SockJS.
     */
    public abstract CompletionStage<F.Either<Result, Flow<Frame, Frame, ?>>> apply(Http.RequestHeader request);

    /**
     * Acceptor for text SockJS.
     */
    public static final MappedSockJSAcceptor<String, String> Text = new MappedSockJSAcceptor<>(Scala.partialFunction(message -> {
        if (message instanceof Frame.Text) {
            return F.Either.Left(((Frame.Text) message).data());
        } else {
            throw Scala.noMatch();
        }
    }), Frame.Text::new);

    /**
     * Acceptor for JSON SockJS.
     */
    public static final MappedSockJSAcceptor<JsonNode, JsonNode> Json = new MappedSockJSAcceptor<>(Scala.partialFunction(message -> {
        try {
            if (message instanceof Frame.Text) {
                return F.Either.Left(play.libs.Json.parse(((Frame.Text) message).data()));
            }
        } catch (RuntimeException e) {
            return F.Either.Right(new Frame.Close(CloseCodes.Unacceptable(), "Unable to parse JSON message"));
        }
        throw Scala.noMatch();
    }), json -> new Frame.Text(play.libs.Json.stringify(json)));

    /**
     * Acceptor for JSON SockJS.
     *
     * @param in The class of the incoming messages, used to decode them from the JSON.
     * @param <In> The SockJS's input type (what it receives from clients)
     * @param <Out> The SockJS's output type (what it writes to clients)
     * @return The SockJS acceptor.
     */
    public static <In, Out> MappedSockJSAcceptor<In, Out> json(Class<In> in) {
        return new MappedSockJSAcceptor<>(Scala.partialFunction(message -> {
            try {
                if (message instanceof Frame.Text) {
                    return F.Either.Left(play.libs.Json.mapper().readValue(((Frame.Text) message).data(), in));
                }
            } catch (Exception e) {
                return F.Either.Right(new Frame.Close(CloseCodes.Unacceptable(), e.getMessage()));
            }
            throw Scala.noMatch();
        }), outMessage -> {
            try {
                return new Frame.Text(play.libs.Json.mapper().writeValueAsString(outMessage));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Utility class for creating SockJS.
     *
     * @param <In> the type the SockJS reads from clients (e.g. String, JsonNode)
     * @param <Out> the type the SockJS outputs back to remote clients (e.g. String, JsonNode)
     */
    public static class MappedSockJSAcceptor<In, Out> {
        private final PartialFunction<Frame, F.Either<In, Frame>> inMapper;
        private final Function<Out, Frame> outMapper;

        public MappedSockJSAcceptor(PartialFunction<Frame, F.Either<In, Frame>> inMapper, Function<Out, Frame> outMapper) {
            this.inMapper = inMapper;
            this.outMapper = outMapper;
        }

        /**
         * Accept a SockJS.
         *
         * @param f A function that takes the request header, and returns a future of either the result to reject the
         *          SockJS connection with, or a flow to handle the SockJS messages.
         * @return The SockJS handler.
         */
        public SockJS acceptOrResult(Function<Http.RequestHeader, CompletionStage<F.Either<Result, Flow<In, Out, ? >>>> f) {
            return SockJS.acceptOrResult(inMapper, f, outMapper);
        }

        /**
         * Accept a SockJS.
         *
         * @param f A function that takes the request header, and returns a flow to handle the SockJS messages.
         * @return The SockJS handler.
         */
        public SockJS accept(Function<Http.RequestHeader, Flow<In, Out, ? >> f) {
            return acceptOrResult(request -> CompletableFuture.completedFuture(F.Either.Right(f.apply(request))));
        }
    }

    /**
     * Helper to create handlers for SockJS.
     *
     * @param inMapper Function to map input messages. If it produces left, the message will be passed to the SockJS
     *                 flow, if it produces right, the message will be sent back out to the client - this can be used
     *                 to send errors directly to the client.
     * @param f The function to handle the SockJS.
     * @param outMapper Function to map output messages.
     * @return The SockJS handler.
     */
    private static <In, Out> SockJS acceptOrResult(
            PartialFunction<Frame, F.Either<In, Frame>> inMapper,
            Function<Http.RequestHeader, CompletionStage<F.Either<Result, Flow<In, Out, ?>>>> f,
            Function<Out, Frame> outMapper
    ) {
        return new SockJS() {
            @Override
            public CompletionStage<F.Either<Result, Flow<Frame, Frame, ?>>> apply(Http.RequestHeader request) {
                return f.apply(request).thenApply(resultOrFlow -> {
                    if (resultOrFlow.left.isPresent()) {
                        return F.Either.Left(resultOrFlow.left.get());
                    } else {
                        Flow<Frame, Frame, ?> flow = AkkaStreams.bypassWith(
                                Flow.<Frame>create().collect(inMapper),
                                play.api.libs.streams.AkkaStreams.onlyFirstCanFinishMerge(2),
                                resultOrFlow.right.get().map(outMapper::apply)
                        );
                        return F.Either.Right(flow);
                    }
                });
            }
        };
    }

    /**
     * A SockJS out.
     *
     * @deprecated Use Akka Streams instead.
     */
    @Deprecated
    public static interface Out {

        public void write(String message);

        public void close();

    }

    /**
     * A SockJS in.
     *
     * @deprecated Use Akka Streams instead.
     */
    @Deprecated
    public static class In {

        /**
         * Callbacks to invoke at each frame.
         */
        public final List<Consumer<String>> callbacks = new ArrayList<Consumer<String>>();

        /**
         * Callbacks to invoke on close.
         */
        public final List<Runnable> closeCallbacks = new ArrayList<Runnable>();

        /**
         * Registers a message callback.
         */
        public void onMessage(Consumer<String> callback) {
            callbacks.add(callback);
        }

        /**
         * Registers a close callback.
         */
        public void onClose(Runnable callback) {
            closeCallbacks.add(callback);
        }

    }

    /**
     * Creates a SockJS handler. The abstract {@code onReady} method is
     * implemented using the specified {@code Callback2<In<A>, Out<A>>}
     *
     * @param callback the callback used to implement onReady
     * @return a new legacy SockJS handler
     * @throws NullPointerException if the specified callback is null
     */
    public static LegacySockJS whenReady(final BiConsumer<In, Out> callback) {
        return new WhenReadySockJS(callback);
    }

    /**
     * Rejects a SockJS request.
     *
     * @param result The result that will be returned.
     * @return A rejected SockJS handler.
     */
    public static LegacySockJS reject(final Result result) {
        return new LegacySockJS() {
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
    public static LegacySockJS withActor(final Function<ActorRef, Props> props) {
        return new LegacySockJS() {
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

    static final class WhenReadySockJS extends LegacySockJS {

        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WhenReadySockJS.class);

        private final BiConsumer<In, Out> callback;

        WhenReadySockJS(BiConsumer<SockJS.In, SockJS.Out> callback) {
            if (callback == null) throw new NullPointerException("SockJS onReady callback cannot be null");
            this.callback = callback;
        }

        @Override
        public void onReady(In in, Out out) {
            try {
                callback.accept(in, out);
            } catch (Throwable e) {
                logger.error("Exception in SockJS.onReady", e);
            }
        }
    }
}
