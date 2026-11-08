package tunn.client;

import android.content.Context;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Layer {
    public static final int SHELL = 1;
    private static final List<File> dirs = new ArrayList<>();
    private static String version = "";
    private static String serial = "";

    private Layer() {
    }

    public static File root(Context c) {
        return new File(c.getFilesDir(), "layer");
    }

    public static synchronized String version() {
        return version;
    }

    public static synchronized String serial() {
        return serial;
    }

    public static synchronized String lib(String name) {
        for (String abi : Build.SUPPORTED_ABIS) {
            for (File d : dirs) {
                File f = new File(d, "lib/" + abi + "/lib" + name + ".so");
                if (f.isFile()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    public static void apply(Context c) {
        try {
            File root = root(c);
            File state = new File(root, "state.json");
            if (!state.isFile()) return;
            File boots = new File(root, "boots");
            int n = 0;
            if (boots.isFile()) {
                try {
                    n = Integer.parseInt(new String(Files.readAllBytes(boots.toPath()), StandardCharsets.UTF_8).trim());
                } catch (Exception e) {
                    n = 0;
                }
            }
            if (n >= 3) {
                state.renameTo(new File(root, "state.bad"));
                write(boots, "0");
                return;
            }
            write(boots, Integer.toString(n + 1));
            JSONObject st = new JSONObject(new String(Files.readAllBytes(state.toPath()), StandardCharsets.UTF_8));
            List<File> order = new ArrayList<>();
            JSONArray fixes = st.optJSONArray("fixes");
            if (fixes != null) {
                for (int i = fixes.length() - 1; i >= 0; i--) order.add(new File(root, fixes.getString(i)));
            }
            String up = st.optString("update", "");
            if (!up.isEmpty()) order.add(new File(root, up));
            List<File> dex = new ArrayList<>();
            List<File> live = new ArrayList<>();
            for (File d : order) {
                File[] list = new File(d, "dex").listFiles();
                live.add(d);
                if (list == null) continue;
                Arrays.sort(list);
                for (File f : list) {
                    if (f.getName().endsWith(".dex")) {
                        f.setReadOnly();
                        dex.add(f);
                    }
                }
            }
            if (!dex.isEmpty()) inject(c, dex);
            synchronized (Layer.class) {
                dirs.addAll(live);
                version = st.optString("version", "");
                serial = st.optString("serial", "");
            }
        } catch (Throwable e) {
        }
    }

    public static void healthy(Context c) {
        try {
            File b = new File(root(c), "boots");
            if (b.isFile()) write(b, "0");
        } catch (Throwable e) {
        }
    }

    private static void write(File f, String text) throws Exception {
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Field field(Class<?> k, String name) throws Exception {
        Class<?> cur = k;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                cur = cur.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void inject(Context c, List<File> dex) throws Exception {
        ClassLoader base = c.getClassLoader();
        StringBuilder path = new StringBuilder();
        for (File f : dex) {
            if (path.length() > 0) path.append(File.pathSeparatorChar);
            path.append(f.getAbsolutePath());
        }
        File opt = new File(c.getCodeCacheDir(), "layer-opt");
        opt.mkdirs();
        dalvik.system.DexClassLoader extra = new dalvik.system.DexClassLoader(path.toString(), opt.getAbsolutePath(), null, base.getParent());
        Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader");
        Object basePath = field(bdcl, "pathList").get(base);
        Object extraPath = field(bdcl, "pathList").get(extra);
        Field elements = field(basePath.getClass(), "dexElements");
        Object[] oldE = (Object[]) elements.get(basePath);
        Object[] newE = (Object[]) field(extraPath.getClass(), "dexElements").get(extraPath);
        Object[] merged = (Object[]) Array.newInstance(oldE.getClass().getComponentType(), newE.length + oldE.length);
        System.arraycopy(newE, 0, merged, 0, newE.length);
        System.arraycopy(oldE, 0, merged, newE.length, oldE.length);
        elements.set(basePath, merged);
    }
}
