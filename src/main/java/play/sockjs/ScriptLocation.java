package play.sockjs;

import play.mvc.Http;

public interface ScriptLocation {

    String src(Http.RequestHeader request);

    public static class DefaultCdn implements ScriptLocation {
        public String src(Http.RequestHeader request) {
            return "http://cdn.sockjs.org/sockjs-0.3.min.js";
        }
    }

}
