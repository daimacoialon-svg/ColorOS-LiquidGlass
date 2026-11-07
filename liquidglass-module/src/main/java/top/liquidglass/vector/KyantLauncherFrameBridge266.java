package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** V266: opt-in, read-only in-process reference/geometry handoff validation.
 *  No bitmap copies, texture uploads, overlays, or changes to original glass rendering.
 */
public final class KyantLauncherFrameBridge266 {
    private static final String KEY = "liquidglass_kyant_launcher_bridge_266";
    private static volatile WeakReference<Bitmap> frame = new WeakReference<>(null);
    private static volatile long generation = 0;
    private static volatile long modified = -1, length = -1;
    private static final WeakHashMap<View, Long> last = new WeakHashMap<>();

    // V288.32: A shared wallpaper-reference publication point for both Kyant hosts.
    // Weak subscriptions do not retain detached FolderIcon/Dock hosts. Updates are
    // dispatched on the main thread; Bitmap decoding stays on HookEntry's worker.
    public interface ReferenceListener {
        void onReferenceFrameReady(Bitmap bitmap, long generation);
    }
    private static final Object listenerLock = new Object();
    private static final WeakHashMap<ReferenceListener, Boolean> referenceListeners = new WeakHashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean notificationPending = new AtomicBoolean(false);

    public static void addReferenceListener(ReferenceListener listener) {
        if (listener == null) return;
        synchronized (listenerLock) { referenceListeners.put(listener, Boolean.TRUE); }
    }
    public static void removeReferenceListener(ReferenceListener listener) {
        if (listener == null) return;
        synchronized (listenerLock) { referenceListeners.remove(listener); }
    }
    public static long peekGeneration270() { return generation; }

    private static void notifyReferenceListeners() {
        if (!notificationPending.compareAndSet(false, true)) return;
        mainHandler.post(() -> {
            // Clear the pending flag BEFORE calling listeners so a concurrent
            // publication is guaranteed to schedule a follow-up notification.
            notificationPending.set(false);
            final Bitmap latest = peekReferenceFrame270();
            if (latest == null) return;
            final long version = generation;
            final ArrayList<ReferenceListener> snapshot;
            synchronized (listenerLock) { snapshot = new ArrayList<>(referenceListeners.keySet()); }
            for (ReferenceListener listener : snapshot) {
                try { listener.onReferenceFrameReady(latest, version); }
                catch (Throwable error) {
                    XposedBridge.log("KYANT28832_REFERENCE_LISTENER_ERROR " + error.getClass().getSimpleName());
                }
            }
        });
    }
    private KyantLauncherFrameBridge266() {}

    public static void onReferenceReady(Bitmap bitmap, long mtime, long fileLength) {
        if (bitmap == null || bitmap.isRecycled()) return;
        boolean changed;
        synchronized (KyantLauncherFrameBridge266.class) {
            changed = mtime != modified || fileLength != length || frame.get() != bitmap;
            modified = mtime;
            length = fileLength;
            frame = new WeakReference<>(bitmap);
            if (changed) generation++;
        }
        if (changed) notifyReferenceListeners();
    }

    /** Read-only handoff. Caller must not recycle or mutate the returned bitmap. */
    public static Bitmap peekReferenceFrame270() {
        Bitmap value = frame.get();
        return value != null && !value.isRecycled() ? value : null;
    }

    public static void install(ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName("com.android.launcher3.folder.FolderIcon", false, loader);
            int hooks = 0;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!"dispatchDraw".equals(method.getName()) || method.getParameterTypes().length != 1
                        || !Canvas.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) sample((View)param.thisObject);
                    }
                });
                hooks++;
            }
            XposedBridge.log("KYANT266_INSTALLED hooks=" + hooks + " mode=in_process_reference_geometry_only gpuUpload=false");
        } catch (Throwable t) {
            XposedBridge.log("KYANT266_INSTALL_ERROR " + t.getClass().getSimpleName());
        }
    }

    private static void sample(View view) {
        try {
            Context context = view.getContext();
            if (context == null || Settings.Global.getInt(context.getContentResolver(), KEY, 0) != 1) return;
            long now = SystemClock.uptimeMillis();
            synchronized (last) {
                Long previous = last.get(view);
                if (previous != null && now - previous < 3000L) return;
                last.put(view, now);
            }
            Bitmap bitmap = frame.get();
            if (bitmap == null || bitmap.isRecycled()) {
                XposedBridge.log("KYANT266_FRAME_UNAVAILABLE id=" + System.identityHashCode(view));
                return;
            }
            int[] xy = new int[2]; view.getLocationOnScreen(xy);
            int w = view.getWidth(), h = view.getHeight();
            View root = view.getRootView();
            int[] rxy = new int[2]; root.getLocationOnScreen(rxy);
            int rw = root.getWidth(), rh = root.getHeight();
            if (w <= 0 || h <= 0 || rw <= 0 || rh <= 0) return;
            int left = Math.max(xy[0], rxy[0]);
            int top = Math.max(xy[1], rxy[1]);
            int right = Math.min(xy[0] + w, rxy[0] + rw);
            int bottom = Math.min(xy[1] + h, rxy[1] + rh);
            boolean empty = right <= left || bottom <= top;
            // UV is relative to the root/screen-aligned reference; it is NOT a verified
            // wallpaper engine crop/offset mapping. No pixels are sampled here.
            String uv = empty ? "EMPTY" : "[" + ((float)(left-rxy[0])/rw) + ","
                    + ((float)(top-rxy[1])/rh) + "]-["
                    + ((float)(right-rxy[0])/rw) + ","
                    + ((float)(bottom-rxy[1])/rh) + "]";
            XposedBridge.log("KYANT266_FRAME_GEOMETRY id=" + System.identityHashCode(view)
                    + " class=" + view.getClass().getSimpleName()
                    + " generation=" + generation + " frame=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " screen=" + xy[0] + "," + xy[1] + " size=" + w + "x" + h
                    + " root=" + rxy[0] + "," + rxy[1] + ":" + rw + "x" + rh
                    + " visibleRootUV=" + uv + " source=existing_bitmap_weak_reference"
                    + " noCopy=true noDecode=true gpuUpload=false");
        } catch (Throwable t) {
            XposedBridge.log("KYANT266_SAMPLE_ERROR " + t.getClass().getSimpleName());
        }
    }
}
