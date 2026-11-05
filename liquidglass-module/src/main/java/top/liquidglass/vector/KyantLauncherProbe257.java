package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** V257 read-only Launcher source and geometry probe. Never captures pixels or changes drawing. */
public final class KyantLauncherProbe257 {
    private static long lastReport;
    private KyantLauncherProbe257() {}
    public static void install(ClassLoader loader) {
        try {
            Class<?> folder = XposedHelpers.findClassIfExists("com.android.launcher3.folder.FolderIcon", loader);
            if (folder == null) { XposedBridge.log("KYANT257_FOLDER_ICON_NOT_FOUND"); return; }
            XposedBridge.hookAllMethods(folder, "onLayout", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View v = (View) param.thisObject;
                        Context c = v.getContext();
                        if (Settings.Global.getInt(c.getContentResolver(), "liquidglass_kyant_probe_257", 0) != 1) return;
                        long now = SystemClock.uptimeMillis();
                        if (now - lastReport < 2500) return;
                        lastReport = now;
                        int[] loc = new int[2]; v.getLocationOnScreen(loc);
                        Rect visible = new Rect(); boolean visibleOk = v.getGlobalVisibleRect(visible);
                        View root = v.getRootView();
                        ViewGroup parent = v.getParent() instanceof ViewGroup ? (ViewGroup)v.getParent() : null;
                        XposedBridge.log("KYANT257_GEOMETRY screen="+loc[0]+","+loc[1]
                          +" size="+v.getWidth()+"x"+v.getHeight()
                          +" visible="+visibleOk+":"+visible.toShortString()
                          +" root="+(root==null?"null":root.getClass().getName())
                          +" parent="+(parent==null?"null":parent.getClass().getName())
                          +" attached="+v.isAttachedToWindow()
                          +" hardware="+v.isHardwareAccelerated());
                        XposedBridge.log("KYANT257_SOURCE_POLICY no_pixel_capture; wallpaper_may_be_separate_surface; no_glass_replacement");
                    } catch(Throwable t) { XposedBridge.log("KYANT257_PROBE_ERROR "+t.getClass().getSimpleName()); }
                }
            });
            XposedBridge.log("KYANT257_PROBE_INSTALLED opt_in_global=liquidglass_kyant_probe_257");
        } catch(Throwable t) { XposedBridge.log("KYANT257_INSTALL_FAILED "+t); }
    }
}
