package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.provider.Settings;
import de.robv.android.xposed.XposedBridge;

/**
 * V264: zero-copy Java-side reference handoff and lifecycle diagnostic.
 * This does NOT upload a GPU texture or provide a Compose Backdrop layer yet.
 * Never decodes, copies, captures or recycles the existing wallpaper Bitmap.
 */
public final class KyantReferenceBridge264 {
    private static final String KEY = "liquidglass_kyant_bridge_264";
    private static volatile long sourceModified = -1L;
    private static volatile long sourceLength = -1L;
    private static volatile long generation;
    private static volatile long lastReport;
    private KyantReferenceBridge264() { }

    public static void onLauncherStart() {
        XposedBridge.log("KYANT264_BRIDGE_INSTALLED mode=reference_metadata_only gpuUpload=false kyantBackdropConnected=false key=" + KEY);
    }

    /** Called only after the existing background decode has succeeded. */
    public static void onReferenceReady(Context context, Bitmap bitmap, long modified, long length) {
        if (bitmap == null || bitmap.isRecycled()) return;
        // The source is owned by HookEntry. We retain only a weak reference to
        // avoid extending the lifetime of obsolete wallpaper frames.
        long nextGeneration;
        synchronized (KyantReferenceBridge264.class) {
            if (modified != sourceModified || length != sourceLength) generation++;
            sourceModified = modified;
            sourceLength = length;
            nextGeneration = generation;
        }
        try {
            if (context == null || Settings.Global.getInt(context.getContentResolver(), KEY, 0) != 1) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastReport < 1000L) return;
            lastReport = now;
            XposedBridge.log("KYANT264_REFERENCE_READY generation=" + nextGeneration
                    + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " config=" + bitmap.getConfig()
                    + " bytes=" + bitmap.getAllocationByteCount()
                    + " sourceModified=" + modified + " sourceLength=" + length
                    + " decodeReused=true gpuUpload=false noCopy=true");
            XposedBridge.log("KYANT264_SOURCE_POLICY wallpaper_only; no_desktop_capture; no_compose_texture_yet");
        } catch (Throwable t) {
            XposedBridge.log("KYANT264_REFERENCE_ERROR " + t.getClass().getSimpleName());
        }
    }
}
