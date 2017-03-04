package play.sockjs;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

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
}
