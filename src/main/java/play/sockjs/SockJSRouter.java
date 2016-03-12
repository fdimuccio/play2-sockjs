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
}
