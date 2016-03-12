package play.sockjs;

import java.util.concurrent.CompletionStage;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Flow;

import play.libs.F;
import play.mvc.Http;
import play.mvc.Result;
import scala.NotImplementedError;

/**
 * A legacy SockJS handler.
 *
 * @deprecated Use play.sockjs.SockJS instead.
 */
@Deprecated
public abstract class LegacySockJS extends SockJS {

    /**
     * Called when the SockJS connection is ready
     */
    public abstract void onReady(SockJS.In in, SockJS.Out out);

    /**
     * If this method returns a result, the SockJS connection will be rejected with that result.
     *
     * This method will be invoked before onReady.
     *
     * @return The result to reject the SockJS connection with, or null if the SockJS shouldn't be rejected.
     */
    public Result rejectWith() {
        return null;
    }

    /**
     * If this method returns true, then the SockJS connection should be handled by an actor.  The actor will be obtained by
     * passing an ActorRef representing to the actor method, which should return the props for creating the actor.
     *
     * @return true if the SockJS connection should be handled by an actor.
     */
    public boolean isActor() {
        return false;
    }

    /**
     * The props to create the actor to handle this SockJS connection.
     *
     * @param out The actor to send upstream messages to.
     * @return The props of the actor to handle the SockJS connection. If isActor returns true, must not return null.
     */
    public Props actorProps(ActorRef out) {
        return null;
    }

    final public CompletionStage<F.Either<Result, Flow<Frame, Frame, ?>>> apply(Http.RequestHeader request) {
        throw new NotImplementedError("This method is not implemented in legacy SockJS");
    }
}
