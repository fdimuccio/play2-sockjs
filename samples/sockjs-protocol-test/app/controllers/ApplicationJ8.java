package controllers;

import controllers.ApplicationJ.SockJSEcho;
import play.libs.F;
import play.mvc.Controller;
import play.sockjs.CookieCalculator;
import play.sockjs.SockJS;
import play.sockjs.SockJSRouter;

public class ApplicationJ8 extends Controller {

	static SockJS echoer = (in, out) -> in.onMessage(s -> out.write(s));

	public static SockJSRouter echo = new SockJSRouter() {
		@SockJS.Settings(streamingQuota = 4096)
		public SockJS sockjs() {
			return echoer;
		};
	};

	public static SockJSRouter closed = SockJSRouter.whenReady((in, out) -> out.close());

	public static SockJSRouter disabledWebSocketEcho = new SockJSRouter() {
		@SockJS.Settings(streamingQuota = 4096, websocket = false)
		public SockJS sockjs() {
			return echoer;
		};
	};

	public static SockJSRouter cookieNeededEcho = new SockJSRouter() {
		@SockJS.Settings(streamingQuota = 4096, cookies = CookieCalculator.JSESSIONID.class)
		public SockJS sockjs() {
			return echoer;
		};
	};

}
