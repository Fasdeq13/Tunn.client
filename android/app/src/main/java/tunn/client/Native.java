package tunn.client;

public final class Native {
    static {
        String p = Layer.lib("tunn_service");
        if (p != null) {
            System.load(p);
        } else {
            System.loadLibrary("tunn_service");
        }
    }

    private Native() {
    }

    public static native String parseLink(String conf);

    public static native String prepareImport(String input);

    public static native String fetchSubscription(String url);

    public static native String ping(String conf, String endpoint, int mode, long timeoutMs);

    public static native String innerConfig(String outbound, int socks, int http, String dns);

    public static native String pluginOpen(String path);

    public static native String pluginCall(long handle, String op, String config, int fd, long arg);

    public static native String pluginClose(long handle);

    public static native void journalInit(String path);

    public static native void journalAdd(String level, String tag, String msg);

    public static native String journalRead(int lines);

    public static native void journalClear();

    public static native long retryDelay(int attempt);

    public static native long startTimeout(String kind, int attempt);

    public static native long settleMs(String prev);

    public static native String pickNext(String candidates, String failed, String current);

    public static native String subHeaders(String headers);

    public static native String updatePlan(String file, String manifest, String version, int code, String applied);

    public static native String uiOpen(String path, int w, int h, float density);

    public static native void uiClose();

    public static native void uiResize(int w, int h);

    public static native void uiState(String json);

    public static native void uiTouch(int kind, float x, float y);

    public static native void uiBack();

    public static native int uiRender(java.nio.ByteBuffer buf, int w, int h, int stride, int t);

    public static native String uiPoll();
}
