package top.liquidglass.vector;

import android.graphics.Canvas;
import android.provider.Settings;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/** V270: one-shot, 200dp bounded Kyant wallpaper lens experiment in Launcher. */
public final class KyantLauncherLensProbe270 {
    private static final String KEY = "liquidglass_kyant_persistent_probe_281";
    private static final java.util.Set<View> attempted =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    private static final java.util.Set<View> activeAnchors =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    public static boolean isActiveFor273(View v) { synchronized (activeAnchors) { return activeAnchors.contains(v); } }
    public static void setActiveFor273(View v, boolean active) {
        synchronized (activeAnchors) { if(active) activeAnchors.add(v); else activeAnchors.remove(v); }
    }
    public static void clearAttempt(View v) {
        synchronized (KyantLauncherLensProbe270.class) { attempted.remove(v); }
    }
    // A short TTL avoids synchronous Settings provider reads on every FolderIcon draw.
    // The same cache is used by the Compose pre-draw listener for switch-off.
    private static volatile long lastSwitchCheckMs = 0L;
    private static volatile boolean cachedSwitch = false;
    public static boolean isExperimentEnabled(android.content.Context context) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastSwitchCheckMs < 400L) return cachedSwitch;
        synchronized (KyantLauncherLensProbe270.class) {
            now = android.os.SystemClock.uptimeMillis();
            if (now - lastSwitchCheckMs >= 400L) {
                try { cachedSwitch = Settings.Global.getInt(context.getContentResolver(), KEY, 0) == 1; }
                catch (Throwable ignored) { cachedSwitch = false; }
                lastSwitchCheckMs = now;
            }
            return cachedSwitch;
        }
    }
    private KyantLauncherLensProbe270() {}
    public static void install(ClassLoader loader) {
        try {
            Class<?> folder = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader);
            int hooks = 0;
            for (Method m : folder.getDeclaredMethods()) {
                if (!m.getName().equals("dispatchDraw") || m.getParameterTypes().length != 1 ||
                    !Canvas.class.isAssignableFrom(m.getParameterTypes()[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View)) return;
                        View view = (View) param.thisObject;
                        if (!view.isAttachedToWindow()) return;
                        try {
                            if (!isExperimentEnabled(view.getContext())) return;
                            if (KyantLauncherFrameBridge266.peekReferenceFrame270() == null) return;
                            synchronized (KyantLauncherLensProbe270.class) {
                                if (!attempted.add(view)) return;
                            }
                            view.post(() -> {
                                try { KyantLauncherLens270.run(view); }
                                catch (Throwable t) { XposedBridge.log("KYANT281_RUN_ERROR " + t.getClass().getSimpleName()); }
                            });
                        } catch (Throwable t) { XposedBridge.log("KYANT281_CHECK_ERROR " + t.getClass().getSimpleName()); }
                    }
                });
                hooks++;
            }
            XposedBridge.log("KYANT281_INSTALLED hooks=" + hooks + " defaultOff=true layer=folder_child_index_0 legacyGlassMutualExclusion=true");
        } catch (Throwable t) { XposedBridge.log("KYANT281_INSTALL_ERROR " + t.getClass().getSimpleName()); }
    }
}
