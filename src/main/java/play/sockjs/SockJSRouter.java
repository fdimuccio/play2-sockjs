package play.sockjs;

import play.sockjs.SockJS.Settings;

public abstract class SockJSRouter extends play.sockjs.core.j.JavaRouter {

	public static SockJSRouter whenReady(final SockJS sockJs) {
		return new SockJSRouter() {
			public SockJS sockjs() {
				return sockJs;
			}
		};
	}

}
