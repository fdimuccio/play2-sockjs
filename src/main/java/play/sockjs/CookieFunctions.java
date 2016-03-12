package play.sockjs;

import java.util.function.Function;

import play.mvc.Http;

public class CookieFunctions {

    /**
     * support jsessionid cookie
     */
    public static Function<Http.RequestHeader, Http.Cookie> jessionid = request -> {
        Http.Cookie jsessionid = request.cookie("JSESSIONID");
        String value = jsessionid != null ? jsessionid.value() : "dummy";
        return new Http.Cookie("JSESSIONID", value, null, "/", null, false, false);
    };

}
