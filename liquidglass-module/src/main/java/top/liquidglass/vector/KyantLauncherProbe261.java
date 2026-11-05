package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/** Opt-in metadata-only Launcher source diagnostic. No screenshots, drawing or wallpaper reads. */
public final class KyantLauncherProbe261 {
    private static final String KEY = "liquidglass_kyant_probe_261";
    private static final long CHECK_MS = 250L;
    private static final long REPORT_MS = 2500L;
    private static final WeakHashMap<View, State> states = new WeakHashMap<>();
    private KyantLauncherProbe261() {}

    private static final class State {
        long check, report;
        int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE, w = -1, h = -1;
        boolean visible, first = true;
        int hierarchyHash;
        boolean sourceReported;
    }

    public static void install(ClassLoader loader) {
        try {
            Class<?> folder = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader);
            int count = 0;
            for (Method m : folder.getDeclaredMethods()) {
                if (!"dispatchDraw".equals(m.getName()) || m.getParameterTypes().length != 1
                        || !Canvas.class.isAssignableFrom(m.getParameterTypes()[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View)) return;
                        View view = (View) param.thisObject;
                        if (!enabled(view)) return;
                        State state;
                        synchronized (states) {
                            state = states.get(view);
                            if (state == null) {
                                state = new State();
                                states.put(view, state);
                                final State captured = state;
                                view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                    @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                                            int ol, int ot, int oright, int ob) {
                                        sample(v, captured, "layout", true);
                                    }
                                });
                                XposedBridge.log("KYANT261_ATTACHED id=" + System.identityHashCode(view)
                                        + " class=" + view.getClass().getName());
                            }
                        }
                        sample(view, state, "draw", false);
                    }
                });
                count++;
            }
            XposedBridge.log("KYANT261_PROBE_INSTALLED hooks=" + count + " key=" + KEY);
        } catch (Throwable t) { XposedBridge.log("KYANT261_INSTALL_FAILED " + t.getClass().getSimpleName()); }
    }

    private static boolean enabled(View view) {
        try { Context c = view.getContext();
            return c != null && Settings.Global.getInt(c.getContentResolver(), KEY, 0) == 1;
        } catch (Throwable ignored) { return false; }
    }

    private static String cls(View view) { return view == null ? "null" : view.getClass().getName(); }

    private static String hierarchy(View view) {
        StringBuilder out = new StringBuilder();
        View cur = view;
        for (int depth = 0; cur != null && depth < 12; depth++) {
            if (depth > 0) out.append(" > ");
            out.append(depth).append(':').append(cls(cur));
            if (cur instanceof ViewGroup) {
                out.append("[children=").append(((ViewGroup) cur).getChildCount()).append(']');
            }
            if (!(cur.getParent() instanceof View)) break;
            cur = (View) cur.getParent();
        }
        return out.toString();
    }

    /** Report structural source candidates once per folder instance, never render/capture pixels. */
    private static void inspectSources(View view, int id) {
        try {
            Context context = view.getContext();
            WallpaperManager wm = WallpaperManager.getInstance(context);
            WallpaperInfo live = null;
            try { live = wm.getWallpaperInfo(); } catch (Throwable ignored) { }
            String kind = live == null ? "static_or_unavailable" : "live";
            int desiredW = -1, desiredH = -1;
            try { desiredW = wm.getDesiredMinimumWidth(); desiredH = wm.getDesiredMinimumHeight(); }
            catch (Throwable ignored) { }
            XposedBridge.log("KYANT261_WALLPAPER id=" + id + " kind=" + kind
                    + " desiredMinimum=" + desiredW + "x" + desiredH
                    + " note=metadata_not_actual_surface_or_bitmap");
            View cur = view;
            for (int depth = 0; cur != null && depth < 12; depth++) {
                String name = cur.getClass().getName();
                if (depth == 0 || name.contains("DragLayer") || name.contains("Workspace")
                        || name.contains("CellLayout") || depth == 11) {
                    Drawable bg = cur.getBackground();
                    int[] xy = new int[2]; cur.getLocationOnScreen(xy);
                    XposedBridge.log("KYANT261_LAYER id=" + id + " depth=" + depth
                            + " class=" + name + " screen=" + xy[0] + "," + xy[1]
                            + " size=" + cur.getWidth() + "x" + cur.getHeight()
                            + " scroll=" + cur.getScrollX() + "," + cur.getScrollY()
                            + " translation=" + cur.getTranslationX() + "," + cur.getTranslationY()
                            + " background=" + (bg == null ? "none" : bg.getClass().getName()));
                }
                if (!(cur.getParent() instanceof View)) break;
                cur = (View) cur.getParent();
            }
            XposedBridge.log("KYANT261_SOURCE_LIMIT id=" + id
                    + " view_background_is_not_composited_wallpaper; scroll_metadata_only; no_capture");
        } catch (Throwable t) {
            XposedBridge.log("KYANT261_SOURCE_ERROR id=" + id + " error=" + t.getClass().getSimpleName());
        }
    }

    private static void sample(View view, State s, String reason, boolean force) {
        try {
            if (!enabled(view)) return;
            long now = SystemClock.uptimeMillis();
            if (!force && !s.first && now - s.check < CHECK_MS) return;
            s.check = now;
            int[] xy = new int[2]; view.getLocationOnScreen(xy);
            int w = view.getWidth(), h = view.getHeight();
            Rect rect = new Rect(); boolean visible = view.getGlobalVisibleRect(rect);
            String chain = hierarchy(view);
            int chainHash = chain.hashCode();
            boolean changed = s.first || xy[0] != s.x || xy[1] != s.y || w != s.w || h != s.h
                    || visible != s.visible || chainHash != s.hierarchyHash;
            s.x = xy[0]; s.y = xy[1]; s.w = w; s.h = h;
            s.visible = visible; s.hierarchyHash = chainHash;
            if (!changed || (!s.first && now - s.report < REPORT_MS)) return;
            boolean first = s.first; s.first = false; s.report = now;
            int id = System.identityHashCode(view);
            View root = view.getRootView();
            int windowFlags = 0; boolean flagsAvailable = false;
            try {
                if (root != null && root.getLayoutParams() instanceof WindowManager.LayoutParams) {
                    windowFlags = ((WindowManager.LayoutParams) root.getLayoutParams()).flags;
                    flagsAvailable = true;
                }
            } catch (Throwable ignored) { }
            XposedBridge.log("KYANT261_GEOMETRY reason=" + (first ? "first" : reason)
                    + " id=" + id + " screen=" + xy[0] + "," + xy[1]
                    + " size=" + w + "x" + h + " visible=" + visible + ":" + rect.toShortString()
                    + " attached=" + view.isAttachedToWindow() + " hardware=" + view.isHardwareAccelerated());
            if (first || chainHash != 0 && "layout".equals(reason)) {
                XposedBridge.log("KYANT261_SOURCE_TREE id=" + id + " chain=" + chain);
                XposedBridge.log("KYANT261_WINDOW id=" + id + " root=" + cls(root)
                        + " flags=" + (flagsAvailable ? "0x" + Integer.toHexString(windowFlags) : "unavailable")
                        + " showWallpaper=" + (flagsAvailable ? Boolean.toString((windowFlags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0) : "unknown"));
                if (!s.sourceReported) {
                    s.sourceReported = true;
                    inspectSources(view, id);
                }
                XposedBridge.log("KYANT261_SOURCE_POLICY metadata_only; no_wallpaper_bitmap_read; no_pixel_capture; no_render_replacement");
            }
        } catch (Throwable t) { XposedBridge.log("KYANT261_SAMPLE_ERROR " + t.getClass().getSimpleName()); }
    }
}
