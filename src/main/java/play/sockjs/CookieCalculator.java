package play.sockjs;

import play.mvc.Http;

public interface CookieCalculator {

    Http.Cookie cookie(Http.RequestHeader request);

    public static class None implements CookieCalculator {
        public Http.Cookie cookie(Http.RequestHeader request) {
            return null;
        }
    }

    public static class JSESSIONID implements CookieCalculator {
        public Http.Cookie cookie(Http.RequestHeader request) {
            Http.Cookie jsessionid = request.cookie("JSESSIONID");
            String value = jsessionid != null ? jsessionid.value() : "dummy";
            return new Http.Cookie("JSESSIONID", value, null, "/", null, false, false);
        }
    }
}
