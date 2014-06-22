package controllers;

import play.mvc.Controller;
import play.sockjs.CookieCalculator;
import play.sockjs.SockJSRouter;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.UntypedActor;
import controllers.ApplicationActorJ.Echo;

/**
 * Test application with Java Actors
 */
public class ApplicationActorJ extends Controller {

	public static class Echo extends UntypedActor {
		
	    private final ActorRef out;

	    public Echo(ActorRef out) {
	        this.out = out;
	    }

	    public void onReceive(Object message) throws Exception {
	        out.tell(message, self());
	    }				
	}
	
	public static class Closed extends UntypedActor {
		
	    public Closed(ActorRef out) {}
	    
	    @Override
	    public void preStart() throws Exception {
	    	self().tell(PoisonPill.getInstance(), self());	
	    }

		@Override
		public void onReceive(Object arg0) throws Exception {}
	    
	}


	public static SockJSRouter echo = SockJSRouter.withStreamingQuota(4096).withActor(Echo.class);

	public static SockJSRouter closed = SockJSRouter.withActor(Closed.class);

	public static SockJSRouter disabledWebSocketEcho = SockJSRouter.withStreamingQuota(4096).withWebSocket(false).withActor(Echo.class);

	public static SockJSRouter cookieNeededEcho = SockJSRouter.withStreamingQuota(4096).withCookies(CookieCalculator.JSESSIONID.class).withActor(Echo.class);

}