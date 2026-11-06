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

/** Opt-in metadata-only Launcher coordinate-domain diagnostic. No screenshots, drawing or wallpaper reads. */
public final class KyantLauncherProbe262 {
    private static final String KEY = "liquidglass_kyant_probe_262";
    private static final long CHECK_MS = 250L;
    private static final long REPORT_MS = 2500L;
    private static final WeakHashMap<View, State> states = new WeakHashMap<>();
    private KyantLauncherProbe262() {}

    private static final class State {
        long check, report;
        int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE, w = -1, h = -1;
        boolean visible, first = true;
        int hierarchyHash;
        boolean sourceReported;
        long mappingReport;
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
                                XposedBridge.log("KYANT262_ATTACHED id=" + System.identityHashCode(view)
                                        + " class=" + view.getClass().getName());
                            }
                        }
                        sample(view, state, "draw", false);
                    }
                });
                count++;
            }
            XposedBridge.log("KYANT262_PROBE_INSTALLED hooks=" + count + " key=" + KEY);
        } catch (Throwable t) { XposedBridge.log("KYANT262_INSTALL_FAILED " + t.getClass().getSimpleName()); }
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
            XposedBridge.log("KYANT262_WALLPAPER id=" + id + " kind=" + kind
                    + " desiredMinimum=" + desiredW + "x" + desiredH
                    + " note=metadata_not_actual_surface_or_bitmap");
            View cur = view;
            for (int depth = 0; cur != null && depth < 12; depth++) {
                String name = cur.getClass().getName();
                if (depth == 0 || name.contains("DragLayer") || name.contains("Workspace")
                        || name.contains("CellLayout") || depth == 11) {
                    Drawable bg = cur.getBackground();
                    int[] xy = new int[2]; cur.getLocationOnScreen(xy);
                    XposedBridge.log("KYANT262_LAYER id=" + id + " depth=" + depth
                            + " class=" + name + " screen=" + xy[0] + "," + xy[1]
                            + " size=" + cur.getWidth() + "x" + cur.getHeight()
                            + " scroll=" + cur.getScrollX() + "," + cur.getScrollY()
                            + " translation=" + cur.getTranslationX() + "," + cur.getTranslationY()
                            + " background=" + (bg == null ? "none" : bg.getClass().getName()));
                }
                if (!(cur.getParent() instanceof View)) break;
                cur = (View) cur.getParent();
            }
            try {
                int wallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM);
                XposedBridge.log("KYANT262_WALLPAPER_ACCESS id=" + id
                        + " systemWallpaperId=" + wallpaperId
                        + " note=id_is_not_bitmap;live_and_lock_wallpapers_may_differ");
            } catch (Throwable t) {
                XposedBridge.log("KYANT262_WALLPAPER_ACCESS id=" + id
                        + " unavailable=" + t.getClass().getSimpleName());
            }
            XposedBridge.log("KYANT262_SOURCE_LIMIT id=" + id
                    + " view_background_is_not_composited_wallpaper; scroll_metadata_only; no_capture");
        } catch (Throwable t) {
            XposedBridge.log("KYANT262_SOURCE_ERROR id=" + id + " error=" + t.getClass().getSimpleName());
        }
    }


    /** Read-only screen-space coordinate diagnostics; never assume wallpaper texture coordinates. */
    private static void reportMapping(View view, int[] xy, int w, int h, Rect visibleRect, boolean visible) {
        try {
            View root = view.getRootView();
            int[] rootXY = new int[2];
            if (root != null) root.getLocationOnScreen(rootXY);
            int rootW = root == null ? 0 : root.getWidth();
            int rootH = root == null ? 0 : root.getHeight();
            int left = Math.max(xy[0], rootXY[0]);
            int top = Math.max(xy[1], rootXY[1]);
            int right = Math.min(xy[0] + w, rootXY[0] + rootW);
            int bottom = Math.min(xy[1] + h, rootXY[1] + rootH);
            int intersectionW = Math.max(0, right - left);
            int intersectionH = Math.max(0, bottom - top);
            boolean valid = rootW > 0 && rootH > 0 && w > 0 && h > 0;
            String uv = valid ? "[" + ((left - rootXY[0]) / (float) rootW)
                    + "," + ((top - rootXY[1]) / (float) rootH) + "]-["
                    + ((right - rootXY[0]) / (float) rootW) + ","
                    + ((bottom - rootXY[1]) / (float) rootH) + "]" : "unavailable";
            int workspaceScrollX = Integer.MIN_VALUE, workspaceScrollY = Integer.MIN_VALUE;
            for (View cur = view; cur != null;) {
                if (cur.getClass().getName().contains("Workspace")) {
                    workspaceScrollX = cur.getScrollX(); workspaceScrollY = cur.getScrollY(); break;
                }
                if (!(cur.getParent() instanceof View)) break;
                cur = (View) cur.getParent();
            }
            XposedBridge.log("KYANT262_MAPPING id=" + System.identityHashCode(view)
                    + " screen=" + xy[0] + "," + xy[1] + " size=" + w + "x" + h
                    + " root=" + rootXY[0] + "," + rootXY[1] + ":" + rootW + "x" + rootH
                    + " clipped=" + intersectionW + "x" + intersectionH
                    + " visible=" + visible + ":" + visibleRect.toShortString()
                    + " rootUV=" + uv + " workspaceScroll="
                    + (workspaceScrollX == Integer.MIN_VALUE ? "unknown" : workspaceScrollX + "," + workspaceScrollY)
                    + " wallpaperUV=UNKNOWN wallpaperOffset=UNKNOWN wallpaperScale=UNKNOWN");
        } catch (Throwable t) {
            XposedBridge.log("KYANT262_MAPPING_ERROR " + t.getClass().getSimpleName());
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
            // Report coordinate-domain checks independently from the slower geometry logs.
            // Screen-space UV is NOT wallpaper texture UV: wallpaper offsets and scaling are unknown.
            if (s.first || now - s.mappingReport >= REPORT_MS) {
                s.mappingReport = now;
                reportMapping(view, xy, w, h, rect, visible);
            }
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
            XposedBridge.log("KYANT262_GEOMETRY reason=" + (first ? "first" : reason)
                    + " id=" + id + " screen=" + xy[0] + "," + xy[1]
                    + " size=" + w + "x" + h + " visible=" + visible + ":" + rect.toShortString()
                    + " attached=" + view.isAttachedToWindow() + " hardware=" + view.isHardwareAccelerated());
            if (first || chainHash != 0 && "layout".equals(reason)) {
                XposedBridge.log("KYANT262_SOURCE_TREE id=" + id + " chain=" + chain);
                XposedBridge.log("KYANT262_WINDOW id=" + id + " root=" + cls(root)
                        + " flags=" + (flagsAvailable ? "0x" + Integer.toHexString(windowFlags) : "unavailable")
                        + " showWallpaper=" + (flagsAvailable ? Boolean.toString((windowFlags & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER) != 0) : "unknown"));
                if (!s.sourceReported) {
                    s.sourceReported = true;
                    inspectSources(view, id);
                }
                XposedBridge.log("KYANT262_SOURCE_POLICY metadata_only; rootUV_not_wallpaperUV; no_wallpaper_bitmap_read; no_pixel_capture; no_render_replacement");
            }
        } catch (Throwable t) { XposedBridge.log("KYANT262_SAMPLE_ERROR " + t.getClass().getSimpleName()); }
    }
}
