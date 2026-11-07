package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WallpaperReference V2: diagnostic only. Never changes rendering, frame cache,
 * settings, or a published bitmap. All decoding/comparison happens off the UI thread.
 */
final class WallpaperAlignmentProbe28844 {
    private WallpaperAlignmentProbe28844() {}
    private static final String TAG = "ColorOSLiquidGlass";
    private static final String EARLY = "liquidglass_early_wallpaper_source";
    private static final String FINAL = "liquidglass_wallpaper_reference.jpg";
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static volatile String lastCompleted = "";
    private static volatile boolean pending;
    private static final long MAX_PAIR_AGE_MS = 45000L;

    static void schedule(Context context, String reason) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (!busy.compareAndSet(false, true)) { pending = true; return; }
        new Thread(() -> {
            try { compare(app, reason); }
            catch (Throwable t) { Log.w(TAG, "KYANT28844_ALIGN_ERROR type=" + t.getClass().getSimpleName() + " message=" + t.getMessage()); }
            finally {
                busy.set(false);
                if (pending) { pending = false; schedule(app, "COALESCED"); }
            }
        }, "LiquidGlass-Alignment28844").start();
    }

    private static File ceFiles(Context app) {
        File file = app.getFilesDir();
        if (!app.isDeviceProtectedStorage()) return file;
        String path = app.createDeviceProtectedStorageContext().getFilesDir().getAbsolutePath();
        if (path.contains("/user_de/")) return new File(path.replace("/user_de/", "/user/"));
        return file;
    }

    private static void compare(Context app, String reason) {
        File dir = ceFiles(app);
        File early = new File(dir, EARLY);
        File reference = new File(dir, FINAL);
        if (!early.isFile() || !reference.isFile() || early.length() <= 1024 || reference.length() <= 1024) {
            Log.i(TAG, "KYANT28844_ALIGN_SKIP reason=MISSING_FILE early=" + early.isFile() + " reference=" + reference.isFile());
            return;
        }
        long em = early.lastModified(), rm = reference.lastModified();
        long es = early.length(), rs = reference.length();
        long delay = rm - em;
        // Do not infer geometric correspondence from unrelated wallpaper generations.
        if (delay < 0 || delay > MAX_PAIR_AGE_MS) {
            Log.i(TAG, "KYANT28844_ALIGN_SKIP reason=UNPAIRED_AGE deltaMs=" + delay + " earlyBytes=" + es + " referenceBytes=" + rs);
            return;
        }
        String key = es + ":" + em + "|" + rs + ":" + rm;
        if (key.equals(lastCompleted)) return;
        Bitmap a = null, b = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            a = BitmapFactory.decodeFile(early.getAbsolutePath(), opts);
            b = BitmapFactory.decodeFile(reference.getAbsolutePath(), opts);
            if (a == null || b == null) {
                Log.i(TAG, "KYANT28844_ALIGN_SKIP reason=DECODE_FAILED"); return;
            }
            if (early.length() != es || reference.length() != rs || early.lastModified() != em || reference.lastModified() != rm) {
                Log.i(TAG, "KYANT28844_ALIGN_SKIP reason=FILE_CHANGED_DURING_COMPARISON"); return;
            }
            // Grid sampling (~8k positions/candidate). Integer offsets are original-image
            // pixel units; decoded images are sampled at half resolution. Diagnostic
            // quantization means this is a coarse fit, NOT a subpixel calibration.
            double best = Double.POSITIVE_INFINITY, second = Double.POSITIVE_INFINITY;
            int bestDx = 0, bestDy = 0;
            final int nx = 64, ny = 128;
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dx = -4; dx <= 4; dx++) {
                    double error = 0; int count = 0;
                    for (int j = 0; j < ny; j++) {
                        double v = 0.05 + 0.90 * j / (ny - 1.0);
                        int by = clamp((int) Math.round(v * (b.getHeight() - 1)), 0, b.getHeight()-1);
                        int ay = clamp((int) Math.round(v * (a.getHeight() - 1) + dy / 2.0), 0, a.getHeight()-1);
                        for (int i = 0; i < nx; i++) {
                            double u = 0.05 + 0.90 * i / (nx - 1.0);
                            int bx = clamp((int) Math.round(u * (b.getWidth() - 1)), 0, b.getWidth()-1);
                            int ax = clamp((int) Math.round(u * (a.getWidth() - 1) + dx / 2.0), 0, a.getWidth()-1);
                            int ca = a.getPixel(ax, ay), cb = b.getPixel(bx, by);
                            error += (Math.abs(((ca >>> 16)&255)-((cb >>> 16)&255)) +
                                      Math.abs(((ca >>> 8)&255)-((cb >>> 8)&255)) +
                                      Math.abs((ca&255)-(cb&255))) / 3.0;
                            count++;
                        }
                    }
                    double mae = error / count;
                    if (mae < best) { second = best; best = mae; bestDx = dx; bestDy = dy; }
                    else if (mae < second) second = mae;
                }
            }
            // Only record this comparison after both files were stable for the entire read.
            lastCompleted = key;
            Log.i(TAG, "KYANT28844_ALIGN_RESULT reason=" + reason +
                " early=" + a.getWidth() + "x" + a.getHeight() +
                " reference=" + b.getWidth() + "x" + b.getHeight() +
                " deltaMs=" + delay + " bestDxOriginalPx=" + bestDx +
                " bestDyOriginalPx=" + bestDy +
                " mae=" + String.format(java.util.Locale.US,"%.2f",best) +
                " secondMae=" + String.format(java.util.Locale.US,"%.2f",second) +
                " status=DIAGNOSTIC_ONLY_NOT_CALIBRATED");
        } finally {
            if (a != null) a.recycle();
            if (b != null) b.recycle();
        }
    }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
