package play.sockjs;

/**
 * A SockJS Frame.
 */
public abstract class Frame {

    private Frame() {
    }

    public static class Text extends Frame {
        private final String data;

        public Text(String data) {
            this.data = data;
        }

        public String data() {
            return data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Text text = (Text) o;

            return data.equals(text.data);
        }

        @Override
        public int hashCode() {
            return data.hashCode();
        }

        @Override
        public String toString() {
            return "TextSockJSFrame('" + data + "')";
        }
    }

    public static class Close extends Frame {
        private final int code;
        private final String reason;

        public Close(int code, String reason) {
            this.code = code;
            this.reason = reason;
        }

        public int code() {
            return code;
        }

        public String reason() {
            return reason;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Close close = (Close) o;

            return code == close.code && reason.equals(close.reason);
        }

        @Override
        public int hashCode() {
            return 31 * code + reason.hashCode();
        }

        @Override
        public String toString() {
            return "CloseSockJSFrame(" + code + ", '" + reason + "')";
        }
    }
}
