package play.sockjs;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.HeaderNames.SET_COOKIE;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.header;
import static play.test.Helpers.route;
import static play.test.Helpers.running;
import static play.test.Helpers.status;

import org.junit.Test;

import play.core.Router.HandlerDef;
import play.core.Router.HandlerInvoker;
import play.core.Router.HandlerInvokerFactory;
import play.libs.Json;
import play.mvc.Result;
import play.test.FakeApplication;
import scala.Function0;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

public class SockJSRouterTest {

	private static SockJS echoer = (in, out) -> in.onMessage(s -> out.write(s));
	private static String BASE_URL = "/java_echo";

	public static class Echo$ {
		public static SockJSRouter MODULE$ = SockJSRouter.whenReady(echoer);
	}

	protected FakeApplication app() {
		String routerClass = Echo$.class.getName();
		routerClass = routerClass.substring(0, routerClass.length()-1); // remove last "$"
		Echo$.MODULE$.setPrefix(BASE_URL); // Force router load
		return fakeApplication(ImmutableMap.of("application.router", routerClass,
				"application.context", BASE_URL));
	}

	@Test
	public void respondWithCorrectJsonToInfoRequest() {
		running(app(), () -> {
			Result result = route(fakeRequest(GET, BASE_URL + "/info"));
			assertThat(status(result)).isEqualTo(OK);
			assertThat(header(SET_COOKIE, result)).isNull();
			JsonNode json = Json.parse(contentAsString(result));
			assertTrue(json.get("websocket").booleanValue());
			assertThat(json.get("cookie_needed").booleanValue()).isNotNull();
			assertThat(json.get("origins").iterator()).containsOnly(Json.toJson("*:*"));
			assertThat(json.get("entropy").longValue()).isNotNull();
		});
	}

}
