package play.sockjs;

import java.util.function.Function;

import play.mvc.Http;

public class CookieFunctions {

    /**
     * support jsessionid cookie
     */
    public static Function<Http.RequestHeader, Http.Cookie> jessionid = request -> {
        String value = request.cookie("JSESSIONID").map(c -> c.value()).orElse("dummy");
        return new Http.Cookie("JSESSIONID", value, null, "/", null, false, false, Http.Cookie.SameSite.STRICT);
    };

}
