package top.liquidglass.vector;

import android.graphics.Canvas;
import android.provider.Settings;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/** V270: one-shot, 200dp bounded Kyant wallpaper lens experiment in Launcher. */
public final class KyantLauncherLensProbe270 {
    private static final String KEY = "liquidglass_kyant_folder_probe_271";
    private static boolean attempted;
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
                            if (Settings.Global.getInt(view.getContext().getContentResolver(), KEY, 0) != 1) return;
                            if (KyantLauncherFrameBridge266.peekReferenceFrame270() == null) return;
                            synchronized (KyantLauncherLensProbe270.class) {
                                if (attempted) return;
                                attempted = true;
                            }
                            view.post(() -> {
                                try { KyantLauncherLens270.run(view); }
                                catch (Throwable t) { XposedBridge.log("KYANT271_RUN_ERROR " + t.getClass().getSimpleName()); }
                            });
                        } catch (Throwable t) { XposedBridge.log("KYANT271_CHECK_ERROR " + t.getClass().getSimpleName()); }
                    }
                });
                hooks++;
            }
            XposedBridge.log("KYANT271_INSTALLED hooks=" + hooks + " defaultOff=true overlay=temporary_follow_folder noGlassReplacement=true");
        } catch (Throwable t) { XposedBridge.log("KYANT271_INSTALL_ERROR " + t.getClass().getSimpleName()); }
    }
}
