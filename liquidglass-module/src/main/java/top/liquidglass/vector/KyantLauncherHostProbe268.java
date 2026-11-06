package top.liquidglass.vector;

import android.graphics.Canvas;
import android.provider.Settings;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/** V268 opt-in single-instance, short-lived Compose host experiment. */
public final class KyantLauncherHostProbe268 {
    private static final String KEY = "liquidglass_kyant_host_probe_268";
    private static boolean attempted;
    private KyantLauncherHostProbe268() {}
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
                            synchronized (KyantLauncherHostProbe268.class) {
                                if (attempted) return;
                                attempted = true;
                            }
                            view.post(() -> {
                                try { KyantIsolatedHost268.run(view); }
                                catch (Throwable t) { XposedBridge.log("KYANT268_HOST_ERROR " + t.getClass().getSimpleName()); }
                            });
                        } catch (Throwable t) { XposedBridge.log("KYANT268_CHECK_ERROR " + t.getClass().getSimpleName()); }
                    }
                });
                hooks++;
            }
            XposedBridge.log("KYANT268_INSTALLED hooks=" + hooks + " enabledByDefault=false noGlassReplacement=true");
        } catch (Throwable t) { XposedBridge.log("KYANT268_INSTALL_ERROR " + t.getClass().getSimpleName()); }
    }
}
