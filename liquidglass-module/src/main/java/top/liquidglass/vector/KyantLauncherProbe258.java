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

/** Read-only, opt-in FolderIcon geometry probe. Does not capture pixels or alter rendering. */
public final class KyantLauncherProbe258 {
    private static final String KEY = "liquidglass_kyant_probe_258";
    private static final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();
    private static long lastReport;
    private KyantLauncherProbe258() {}

    public static void install(ClassLoader loader) {
        try {
            Class<?> folder;
            try { folder = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader); }
            catch (ClassNotFoundException e) { XposedBridge.log("KYANT258_FOLDER_ICON_NOT_FOUND"); return; }
            int hooked = 0;
            // FolderIcon may inherit onLayout. Hook a declared per-instance draw entry instead;
            // never hook View/ViewGroup globally. Use a listener for layout and a throttled
            // draw callback to notice position changes caused by scrolling/transforms.
            for (Method m : folder.getDeclaredMethods()) {
                if (!"dispatchDraw".equals(m.getName())) continue;
                Class<?>[] types = m.getParameterTypes();
                if (types.length != 1 || !android.graphics.Canvas.class.isAssignableFrom(types[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View)) return;
                        final View v = (View) param.thisObject;
                        if (!enabled(v)) return;
                        synchronized (observed) {
                            if (!observed.containsKey(v)) {
                                observed.put(v, Boolean.TRUE);
                                v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override public void onLayoutChange(View view, int l, int t, int r, int b,
                                            int oldL, int oldT, int oldR, int oldB) {
                                        report(view, "layout");
                                    }
                                });
                                XposedBridge.log("KYANT258_LISTENER_ATTACHED class="+v.getClass().getName());
                            }
                        }
                        report(v, "draw");
                    }
                });
                hooked++;
            }
            if (hooked == 0) XposedBridge.log("KYANT258_DISPATCH_DRAW_NOT_DECLARED");
            else XposedBridge.log("KYANT258_PROBE_INSTALLED count="+hooked+" opt_in_global="+KEY);
        } catch (Throwable t) { XposedBridge.log("KYANT258_INSTALL_FAILED "+t); }
    }

    private static boolean enabled(View v) {
        try {
            Context c = v.getContext();
            return c != null && Settings.Global.getInt(c.getContentResolver(), KEY, 0) == 1;
        } catch (Throwable ignored) { return false; }
    }

    private static void report(View v, String reason) {
        try {
            if (!enabled(v)) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastReport < 2500) return;
            lastReport = now;
            int[] loc = new int[2]; v.getLocationOnScreen(loc);
            Rect visible = new Rect(); boolean ok = v.getGlobalVisibleRect(visible);
            View root = v.getRootView();
            ViewGroup parent = v.getParent() instanceof ViewGroup ? (ViewGroup) v.getParent() : null;
            XposedBridge.log("KYANT258_GEOMETRY reason="+reason+" screen="+loc[0]+","+loc[1]
                +" size="+v.getWidth()+"x"+v.getHeight()
                +" visible="+ok+":"+visible.toShortString()
                +" root="+(root==null?"null":root.getClass().getName())
                +" parent="+(parent==null?"null":parent.getClass().getName())
                +" attached="+v.isAttachedToWindow()+" hardware="+v.isHardwareAccelerated());
            XposedBridge.log("KYANT258_SOURCE_POLICY no_pixel_capture; no_glass_replacement");
        } catch (Throwable t) { XposedBridge.log("KYANT258_PROBE_ERROR "+t.getClass().getSimpleName()); }
    }
}
