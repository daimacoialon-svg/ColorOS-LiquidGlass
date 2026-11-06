package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewParent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** V267: opt-in, read-only host feasibility probe. Does NOT create ComposeView or alter drawing. */
public final class KyantLauncherHostProbe267 {
    private static final String KEY = "liquidglass_kyant_host_probe_267";
    private static final WeakHashMap<View, Boolean> seen = new WeakHashMap<>();
    private static volatile long lastGlobalLog;
    private KyantLauncherHostProbe267() {}

    public static void install(ClassLoader loader) {
        try {
            Class<?> folder = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader);
            int hooks = 0;
            for (Method m : folder.getDeclaredMethods()) {
                if (!m.getName().equals("dispatchDraw") || m.getParameterTypes().length != 1 ||
                        !Canvas.class.isAssignableFrom(m.getParameterTypes()[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) inspect((View) param.thisObject);
                    }
                });
                hooks++;
            }
            XposedBridge.log("KYANT267_INSTALLED hooks=" + hooks + " readOnly=true composeViewCreated=false");
            XposedBridge.log("KYANT267_SOURCE_POLICY no_overlay; no_bitmap_copy; no_gpu_upload; no_render_replacement");
        } catch (Throwable t) { XposedBridge.log("KYANT267_INSTALL_ERROR " + t.getClass().getSimpleName()); }
    }

    private static boolean exists(String name, ClassLoader loader) {
        try { Class.forName(name, false, loader); return true; }
        catch (Throwable t) { return false; }
    }

    private static String owner(View view, String clazz) {
        try {
            Class<?> type = Class.forName(clazz, false, view.getClass().getClassLoader());
            Object value = type.getMethod("get", View.class).invoke(null, view);
            return value == null ? "null" : value.getClass().getName();
        } catch (ClassNotFoundException e) { return "class_unavailable"; }
        catch (Throwable e) { return "unavailable_" + e.getClass().getSimpleName(); }
    }

    private static void inspect(View view) {
        try {
            Context ctx = view.getContext();
            if (ctx == null || Settings.Global.getInt(ctx.getContentResolver(), KEY, 0) != 1) return;
            long now = SystemClock.uptimeMillis();
            synchronized (seen) {
                if (seen.containsKey(view)) return;
                // Avoid a large burst if Launcher binds many icons in one frame.
                if (now - lastGlobalLog < 350L) return;
                seen.put(view, Boolean.TRUE);
                lastGlobalLog = now;
            }
            ClassLoader cl = view.getClass().getClassLoader();
            View root = view.getRootView();
            int[] xy = new int[2]; view.getLocationOnScreen(xy);
            StringBuilder parents = new StringBuilder();
            ViewParent parent = view.getParent();
            int depth = 0;
            while (parent != null && depth++ < 10) {
                if (parents.length() != 0) parents.append('>');
                parents.append(parent.getClass().getSimpleName());
                parent = parent.getParent();
            }
            XposedBridge.log("KYANT267_HOST id=" + System.identityHashCode(view)
                    + " class=" + view.getClass().getName()
                    + " attached=" + view.isAttachedToWindow()
                    + " hardware=" + view.isHardwareAccelerated()
                    + " size=" + view.getWidth() + "x" + view.getHeight()
                    + " screen=" + xy[0] + "," + xy[1]
                    + " root=" + (root == null ? "null" : root.getClass().getName())
                    + " context=" + ctx.getClass().getName()
                    + " parents=" + parents);
            XposedBridge.log("KYANT267_COMPOSE_AVAIL id=" + System.identityHashCode(view)
                    + " composeView=" + exists("androidx.compose.ui.platform.ComposeView", cl)
                    + " lifecycleOwnerClass=" + exists("androidx.lifecycle.ViewTreeLifecycleOwner", cl)
                    + " savedStateOwnerClass=" + exists("androidx.savedstate.ViewTreeSavedStateRegistryOwner", cl)
                    + " lifecycleOwner=" + owner(view, "androidx.lifecycle.ViewTreeLifecycleOwner")
                    + " savedStateOwner=" + owner(view, "androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                    + " note=class_presence_does_not_prove_host_can_render");
        } catch (Throwable t) { XposedBridge.log("KYANT267_PROBE_ERROR " + t.getClass().getSimpleName()); }
    }
}
