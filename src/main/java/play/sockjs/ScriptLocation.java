package play.sockjs;

import play.mvc.Http;

public interface ScriptLocation {

    String src(Http.RequestHeader request);

    public static class DefaultCdn implements ScriptLocation {
        public String src(Http.RequestHeader request) {
            return "//cdn.jsdelivr.net/sockjs/0.3.4/sockjs.min.js";
        }
    }

}
