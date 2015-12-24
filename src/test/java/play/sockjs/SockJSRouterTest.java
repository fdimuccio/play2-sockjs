package play.sockjs;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static play.mvc.Http.HeaderNames.SET_COOKIE;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import org.junit.Test;

import play.Application;
import play.GlobalSettings;
import play.api.mvc.Handler;
import play.api.routing.Router;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

public class SockJSRouterTest {

    final String ECHO_PREFIX = "/java_echo";

	protected Application app() {
		final Router echo = SockJSRouter.whenReady((in, out) -> in.onMessage(out::write)).withPrefix(ECHO_PREFIX);
		return fakeApplication(new GlobalSettings() {
            @Override
            public Handler onRouteRequest(Http.RequestHeader request) {
                return request.path().startsWith(ECHO_PREFIX) ?
                        echo.handlerFor(request._underlyingHeader()).getOrElse(null) :
                        super.onRouteRequest(request);
            }
        });
	}

	@Test
	public void respondWithCorrectJsonToInfoRequest() {
		running(app(), () -> {
			Result result = route(fakeRequest(GET, ECHO_PREFIX + "/info"));
			assertEquals(OK, result.status());
			assertFalse(result.header(SET_COOKIE).isPresent());
			JsonNode json = Json.parse(contentAsString(result));
			assertTrue(json.get("websocket").booleanValue());
			assertNotNull(json.get("cookie_needed").booleanValue());
			//assertThat(json.get("origins").iterator(), hasItem(Json.toJson("*:*")));
			assertNotNull(json.get("entropy").longValue());
		});
	}

}
