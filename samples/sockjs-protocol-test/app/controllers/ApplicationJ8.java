package controllers;

import controllers.ApplicationJ.SockJSEcho;
import play.libs.F;
import play.mvc.Controller;
import play.sockjs.CookieCalculator;
import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;

/**
 * Test application with Java8 lambda expressions
public class ApplicationJ8 extends Controller {

	static SockJS echoer = (in, out) -> in.onMessage(s -> out.write(s));

	public static SockJSRouter echo = SockJSRouter.withStreamingQuota(4096).whenReady(echoer);

	public static SockJSRouter closed = SockJSRouter.whenReady((in, out) -> out.close());

	public static SockJSRouter disabledWebSocketEcho = SockJSRouter.withStreamingQuota(4096).withWebSocket(false).whenReady(echoer);

	public static SockJSRouter cookieNeededEcho = SockJSRouter.withStreamingQuota(4096).withCookies(CookieCalculator.JSESSIONID.class).whenReady(echoer);

}
*/