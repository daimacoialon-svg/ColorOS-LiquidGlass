package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Opt-in, read-only per-instance geometry probe. Never captures pixels or changes rendering. */
public final class KyantLauncherProbe259 {
    private static final String KEY = "liquidglass_kyant_probe_259";
    private static final long CHECK_INTERVAL_MS = 250L;
    private static final long REPORT_INTERVAL_MS = 2500L;
    private static final WeakHashMap<View, State> observed = new WeakHashMap<>();
    private KyantLauncherProbe259() {}

    private static final class State {
        long lastCheck, lastReport;
        int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE;
        int width = -1, height = -1;
        boolean visible, initialized;
    }

    public static void install(ClassLoader loader) {
        try {
            Class<?> folder;
            try { folder = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader); }
            catch (ClassNotFoundException e) { XposedBridge.log("KYANT259_FOLDER_ICON_NOT_FOUND"); return; }
            int hooked = 0;
            for (Method m : folder.getDeclaredMethods()) {
                if (!"dispatchDraw".equals(m.getName())) continue;
                Class<?>[] types = m.getParameterTypes();
                if (types.length != 1 || !android.graphics.Canvas.class.isAssignableFrom(types[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View)) return;
                        View view = (View) param.thisObject;
                        if (!enabled(view)) return;
                        State state;
                        synchronized (observed) {
                            state = observed.get(view);
                            if (state == null) {
                                state = new State();
                                observed.put(view, state);
                                final State instanceState = state;
                                view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                            int oldL, int oldT, int oldR, int oldB) {
                                        sample(v, instanceState, "layout", true);
                                    }
                                });
                                XposedBridge.log("KYANT259_LISTENER_ATTACHED id="
                                        + System.identityHashCode(view) + " class=" + view.getClass().getName());
                                sample(view, state, "first", true);
                            }
                        }
                        sample(view, state, "draw", false);
                    }
                });
                hooked++;
            }
            if (hooked == 0) XposedBridge.log("KYANT259_DISPATCH_DRAW_NOT_DECLARED");
            else XposedBridge.log("KYANT259_PROBE_INSTALLED count=" + hooked + " opt_in_global=" + KEY);
        } catch (Throwable t) { XposedBridge.log("KYANT259_INSTALL_FAILED " + t); }
    }

    private static boolean enabled(View v) {
        try {
            Context c = v.getContext();
            return c != null && Settings.Global.getInt(c.getContentResolver(), KEY, 0) == 1;
        } catch (Throwable ignored) { return false; }
    }

    private static void sample(View v, State state, String reason, boolean force) {
        try {
            if (!enabled(v)) return;
            long now = SystemClock.uptimeMillis();
            if (!force && now - state.lastCheck < CHECK_INTERVAL_MS) return;
            state.lastCheck = now;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            int width = v.getWidth(), height = v.getHeight();
            Rect rect = new Rect();
            boolean visible = v.getGlobalVisibleRect(rect);
            boolean changed = !state.initialized || pos[0] != state.x || pos[1] != state.y
                    || width != state.width || height != state.height || visible != state.visible;
            state.x = pos[0]; state.y = pos[1]; state.width = width; state.height = height;
            state.visible = visible;
            boolean first = !state.initialized;
            state.initialized = true;
            if (!first && (!changed || now - state.lastReport < REPORT_INTERVAL_MS)) return;
            state.lastReport = now;
            View root = v.getRootView();
            ViewGroup parent = v.getParent() instanceof ViewGroup ? (ViewGroup) v.getParent() : null;
            XposedBridge.log("KYANT259_GEOMETRY reason=" + reason + " id=" + System.identityHashCode(v)
                    + " screen=" + pos[0] + "," + pos[1]
                    + " size=" + width + "x" + height
                    + " visible=" + visible + ":" + rect.toShortString()
                    + " root=" + (root == null ? "null" : root.getClass().getName())
                    + " parent=" + (parent == null ? "null" : parent.getClass().getName())
                    + " attached=" + v.isAttachedToWindow() + " hardware=" + v.isHardwareAccelerated());
            if (first) XposedBridge.log("KYANT259_SOURCE_POLICY no_pixel_capture; no_glass_replacement");
        } catch (Throwable t) { XposedBridge.log("KYANT259_PROBE_ERROR " + t.getClass().getSimpleName()); }
    }
}
