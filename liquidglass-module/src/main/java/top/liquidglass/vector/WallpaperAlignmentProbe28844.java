package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** V288.46: event-only full-resolution alignment diagnostic.
 * Never replaces the published Kyant reference or changes the optical renderer.
 * This probe is deliberately conservative about file generation matching.
 */
final class WallpaperAlignmentProbe28844 {
    private WallpaperAlignmentProbe28844() {}
    private static final String TAG = "ColorOSLiquidGlass";
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static volatile boolean pending;
    private static volatile String lastCompleted = "";
    // Content-based dedup: timestamps can change when the very same screenshot is republished.
    private static volatile String lastCompletedContent = "";
    // V288.52: a terminal verdict belongs to the exact content pair.
    // Do not conflate "already compared" with "crop approved".
    private static volatile String lastVerdict28852 = "PENDING";
    private static volatile String lastVerdictReason28852 = "NOT_ATTEMPTED";
    private static volatile String activePair28852 = "";
    private static volatile String lastAttemptPair28852 = "";
    private static volatile long attempts28852 = 0;
    private static String verdictDetail28852() {
        return " verdict="+lastVerdict28852+" detail="+lastVerdictReason28852+
            " attempts="+attempts28852;
    }
    private static final long MAX_PAIR_AGE_MS = 45000L;
    // Prevent per-draw metadata reuse from creating new calibration threads.
    // Source metadata is included: a new early original re-arms the check.
    private static volatile String lastReuseMetadata28851 = "";
    static void scheduleOnReuse28851(Context context, String reason) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        File dir = ceFiles(app);
        File early = new File(dir, "liquidglass_early_wallpaper_source");
        File ref = new File(dir, "liquidglass_wallpaper_reference.jpg");
        if (!early.isFile() || !ref.isFile()) return;
        String metadata = early.length()+":"+early.lastModified()+"|"+
            ref.length()+":"+ref.lastModified();
        synchronized(WallpaperAlignmentProbe28844.class) {
            if(metadata.equals(lastReuseMetadata28851)) return;
            lastReuseMetadata28851=metadata;
        }
        Log.i(TAG,"KYANT28851_CALIBRATION_REARM reason="+reason+" metadata="+metadata);
        schedule(app, reason);
    }
    // V288.50: a crop is approved only for the EXACT original image bytes.
    // A geometrically similar future wallpaper cannot inherit that approval.
    private static final String PREFS_28850 = "kyant_early_calibration_28850";
    static final class CropApproval28850 {
        final int left, sourceWidth, screenWidth, height;
        final String sourceKey;
        CropApproval28850(int left, int sourceWidth, int screenWidth, int height, String sourceKey) {
            this.left=left; this.sourceWidth=sourceWidth; this.screenWidth=screenWidth;
            this.height=height; this.sourceKey=sourceKey;
        }
    }
    static CropApproval28850 approvedCrop(Context app, File early, int expectedWidth, int expectedHeight) {
        if (app == null || early == null || !early.isFile()) return null;
        if (early.length() < 1024 || early.length() > 32000000L) return null;
        long size=early.length(), time=early.lastModified();
        try {
            String sourceKey=size+":"+contentCrc(early);
            if (early.length()!=size || early.lastModified()!=time) return null;
            android.content.SharedPreferences prefs=app.getSharedPreferences(PREFS_28850, Context.MODE_PRIVATE);
            String key=prefs.getString("sourceKey", "");
            int w=prefs.getInt("sourceWidth", 0), h=prefs.getInt("height", 0);
            int sw=prefs.getInt("screenWidth", 0), x=prefs.getInt("left", -1);
            if (!sourceKey.equals(key) || sw!=expectedWidth || h!=expectedHeight ||
                w<expectedWidth || w>expectedWidth+2 || x<0 || x>w-expectedWidth) return null;
            return new CropApproval28850(x,w,sw,h,sourceKey);
        } catch(Throwable ignored) { return null; }
    }
    // V288.53: compare actual pixel crops for width deltas 0, 1 and 2.
    // The approval is always tied to the exact source bytes and dimensions.
    private static double cropMae28853(Bitmap original, Bitmap canonical, int startX) {
        final int delta=original.getWidth()-canonical.getWidth();
        if (delta<0 || delta>2 || startX<0 || startX>delta ||
            original.getHeight()!=canonical.getHeight()) return Double.POSITIVE_INFINITY;
        final int nx=84, ny=132;
        double error=0.0;
        for(int iy=0; iy<ny; iy++) {
            int y=(int)((long)iy*(canonical.getHeight()-1)/(ny-1));
            for(int ix=0; ix<nx; ix++) {
                int x=(int)((long)ix*(canonical.getWidth()-1)/(nx-1));
                int a=original.getPixel(x+startX,y), b=canonical.getPixel(x,y);
                error+=(Math.abs(((a>>>16)&255)-((b>>>16)&255))+
                    Math.abs(((a>>>8)&255)-((b>>>8)&255))+
                    Math.abs((a&255)-(b&255)))/3.0;
            }
        }
        return error/(nx*ny);
    }
    private static String calibrateCrop28853(Context app, Bitmap a, Bitmap b,
                                              String sourceKey, String pair) {
        final int widthDelta=a.getWidth()-b.getWidth();
        if(widthDelta<0 || widthDelta>2 || a.getHeight()!=b.getHeight()) {
            Log.i(TAG,"KYANT28853_CALIBRATION_REJECT reason=UNSUPPORTED_GEOMETRY early="+
                a.getWidth()+"x"+a.getHeight()+" reference="+b.getWidth()+"x"+b.getHeight()+
                " widthDelta="+widthDelta);
            return "REJECTED:UNSUPPORTED_GEOMETRY";
        }
        int bestX=-1; double best=Double.POSITIVE_INFINITY, runner=Double.POSITIVE_INFINITY;
        for(int x=0; x<=widthDelta; x++) {
            double err=cropMae28853(a,b,x);
            if(err<best) {runner=best; best=err; bestX=x;}
            else if(err<runner) runner=err;
        }
        // An equal-width bitmap has only one crop mapping: there is no second
        // candidate, so uniqueness must not be evaluated against infinity.
        final boolean hasRunner=widthDelta>0;
        final double margin=hasRunner ? runner-best : 0.0;
        final boolean pass=Double.isFinite(best) && best<=4.0 &&
            (!hasRunner || (Double.isFinite(runner) && margin>=0.35));
        Log.i(TAG,"KYANT28853_CROP_CALIBRATION geometry="+a.getWidth()+"x"+a.getHeight()+
            "->"+b.getWidth()+"x"+b.getHeight()+" widthDelta="+widthDelta+
            " candidates="+(widthDelta+1)+" bestLeft="+bestX+
            " mae="+String.format(Locale.US,"%.3f",best)+
            " secondMae="+(hasRunner?String.format(Locale.US,"%.3f",runner):"NA")+
            " margin="+(hasRunner?String.format(Locale.US,"%.3f",margin):"NA")+
            " status="+(pass?"APPROVED_SOURCE_ONLY":"REJECTED")+" pair="+pair);
        if(!pass) {
            Log.i(TAG,"KYANT28853_CALIBRATION_REJECT reason=LOW_CONFIDENCE widthDelta="+
                widthDelta+" best="+String.format(Locale.US,"%.3f",best)+
                " margin="+(hasRunner?String.format(Locale.US,"%.3f",margin):"NA")+
                " source="+sourceKey);
            return "REJECTED:LOW_CONFIDENCE";
        }
        // Retain existing preference names so the early-frame consumer reads
        // approvals using one consistent schema for all three widths.
        app.getSharedPreferences(PREFS_28850,Context.MODE_PRIVATE).edit()
            .putString("sourceKey",sourceKey)
            .putInt("sourceWidth",a.getWidth()).putInt("screenWidth",b.getWidth())
            .putInt("height",b.getHeight()).putInt("left",bestX).apply();
        Log.i(TAG,"KYANT28853_CALIBRATION_APPROVED widthDelta="+widthDelta+
            " source="+sourceKey+" left="+bestX+
            " mae="+String.format(Locale.US,"%.3f",best));
        return "APPROVED:LEFT_"+bestX;
    }

    // Two full-resolution 1217x2640 ARGB_8888 images require about 25 MiB.
    // Refuse unusually large images to avoid temporary memory pressure.
    private static final long MAX_TOTAL_PIXELS = 13000000L;

    static void schedule(Context context, String reason) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        if (!busy.compareAndSet(false, true)) { pending = true; return; }
        new Thread(() -> {
            try { compare(app, reason); }
            catch (Throwable e) {
                lastVerdict28852="RETRYABLE_ERROR";
                lastVerdictReason28852="EXCEPTION_"+e.getClass().getSimpleName();
                Log.w(TAG, "KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason="+
                    lastVerdictReason28852+" pair="+activePair28852);
                Log.w(TAG, "KYANT28845_ALIGN_ERROR type=" + e.getClass().getSimpleName());
            }
            finally {
                busy.set(false);
                if (pending) { pending = false; schedule(app, "COALESCED"); }
            }
        }, "LiquidGlass-FullResAlign28845").start();
    }

    private static File ceFiles(Context app) {
        File f = app.getFilesDir();
        if (!app.isDeviceProtectedStorage()) return f;
        String path = f.getAbsolutePath();
        if (path.contains("/user_de/")) return new File(path.replace("/user_de/", "/user/"));
        return f;
    }
    private static int[] dimensions(File f) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
        return new int[]{opt.outWidth, opt.outHeight};
    }
    private static String contentCrc(File file) throws java.io.IOException {
        CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) crc.update(buf, 0, n);
        }
        return Long.toHexString(crc.getValue());
    }
    // Compare actual sampled coordinates, not model labels. LEFT_CROP dx=0 and
    // RIGHT_CROP dx=-1 are identical when the source is exactly 1 px wider.
    private static String mappingKey(int wa, int ha, int wb, int hb,
                                     int dx, int dy, int model) {
        StringBuilder key = new StringBuilder(96);
        for (int i = 0; i < 68; i++) {
            double u = 0.055 + 0.89 * i / 67.0;
            int bx = clamp((int)Math.round(u * (wb - 1)), 0, wb - 1);
            double ax0 = model == 0 ? bx : model == 1 ? bx + (wa-wb)/2.0
                       : model == 2 ? bx + wa-wb : u*(wa-1);
            key.append((int)Math.round(ax0 + dx)).append(',');
        }
        key.append('|');
        for (int i = 0; i < 112; i++) {
            double v = 0.055 + 0.89 * i / 111.0;
            int by = clamp((int)Math.round(v * (hb - 1)), 0, hb - 1);
            double ay0 = model == 3 ? v * (ha-1) : by + (ha-hb)/2.0;
            key.append((int)Math.round(ay0 + dy)).append(',');
        }
        return key.toString();
    }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double mae(Bitmap a, Bitmap b, int dx, int dy, int model) {
        int wa=a.getWidth(), ha=a.getHeight(), wb=b.getWidth(), hb=b.getHeight();
        double sum=0;
        int count=0;
        final int nx=68, ny=112;
        for (int j=0;j<ny;j++) {
            double v=0.055+0.89*j/(ny-1.0);
            int by=clamp((int)Math.round(v*(hb-1)),0,hb-1);
            double ay0=(model==3 ? v*(ha-1) : by + (ha-hb)/2.0) + dy;
            int ay=(int)Math.round(ay0);
            if (ay<0 || ay>=ha) continue;
            for (int i=0;i<nx;i++) {
                double u=0.055+0.89*i/(nx-1.0);
                int bx=clamp((int)Math.round(u*(wb-1)),0,wb-1);
                double ax0;
                if (model==0) ax0=bx; // left crop
                else if (model==1) ax0=bx+(wa-wb)/2.0; // center crop
                else if (model==2) ax0=bx+(wa-wb); // right crop
                else ax0=u*(wa-1); // stretch to reference
                int ax=(int)Math.round(ax0+dx);
                if (ax<0 || ax>=wa) continue;
                int pa=a.getPixel(ax,ay),pb=b.getPixel(bx,by);
                sum+=(Math.abs(((pa>>>16)&255)-((pb>>>16)&255))+
                      Math.abs(((pa>>>8)&255)-((pb>>>8)&255))+
                      Math.abs((pa&255)-(pb&255)))/3.0;
                count++;
            }
        }
        return count < nx*ny/2 ? Double.POSITIVE_INFINITY : sum/count;
    }
    private static void compare(Context app,String reason) {
        File dir=ceFiles(app);
        File early=new File(dir,"liquidglass_early_wallpaper_source");
        File reference=new File(dir,"liquidglass_wallpaper_reference.jpg");
        if(!early.isFile() || !reference.isFile() || early.length()<=1024 || reference.length()<=1024) {
            Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason=MISSING_SOURCE");
            Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=MISSING_SOURCE"); return;
        }
        long em=early.lastModified(),rm=reference.lastModified(),es=early.length(),rs=reference.length();
        long delay=rm-em;
        boolean closeInTime = delay>=0 && delay<=MAX_PAIR_AGE_MS;
        if (!closeInTime) {
            // V288.51: mtime mismatch is not proof of different image content.
            // Continue with one content-pair-deduplicated pixel comparison,
            // but never authorize a crop based on timestamps alone.
            Log.i(TAG,"KYANT28851_PAIR_TIME_UNVERIFIED deltaMs="+delay+
                " action=PIXEL_VERIFY_ONLY reason="+reason);
        }
        String pair=es+":"+em+"|"+rs+":"+rm;
        if(pair.equals(lastCompleted)) {
            Log.i(TAG,"KYANT28852_CALIBRATION_DEDUP kind=METADATA pair="+pair+
                verdictDetail28852()); return;
        }
        String contentPair;
        try {
            contentPair=es+":"+contentCrc(early)+"|"+rs+":"+contentCrc(reference);
        } catch (java.io.IOException e) {
            Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason=HASH_IO_ERROR");
            Log.i(TAG,"KYANT28846_ALIGN_SKIP reason=HASH_IO_ERROR"); return;
        }
        if (early.length()!=es || reference.length()!=rs ||
            early.lastModified()!=em || reference.lastModified()!=rm) {
            Log.i(TAG,"KYANT28846_ALIGN_SKIP reason=FILE_CHANGED_DURING_HASH"); return;
        }
        if (contentPair.equals(lastCompletedContent)) {
            lastCompleted=pair;
            Log.i(TAG,"KYANT28852_CALIBRATION_DEDUP kind=CONTENT contentPair="+contentPair+
                " timePair="+pair+verdictDetail28852());
            return;
        }
        // This is a new content pair. Every attempt must reach one observable
        // terminal outcome before it becomes eligible for content dedup.
        activePair28852=contentPair;
        lastAttemptPair28852=pair;
        lastVerdict28852="RUNNING";
        lastVerdictReason28852="PIXEL_COMPARE";
        attempts28852++;
        Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RUNNING contentPair="+
            contentPair+" timePair="+pair+" attempt="+attempts28852);
        int[] ad=dimensions(early),bd=dimensions(reference);
        if(ad[0]<1 || ad[1]<1 || bd[0]<1 || bd[1]<1 ||
           (long)ad[0]*ad[1]+(long)bd[0]*bd[1]>MAX_TOTAL_PIXELS) {
            lastVerdict28852="RETRYABLE_ERROR";
            lastVerdictReason28852="INVALID_OR_LARGE_DIMENSIONS";
            Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason="+
                lastVerdictReason28852+" pair="+contentPair);
            Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=INVALID_OR_LARGE_DIMENSIONS"); return;
        }
        Bitmap a=null,b=null;
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            options.inSampleSize=1;
            a=BitmapFactory.decodeFile(early.getAbsolutePath(),options);
            b=BitmapFactory.decodeFile(reference.getAbsolutePath(),options);
            if(a==null || b==null) {
                lastVerdict28852="RETRYABLE_ERROR";lastVerdictReason28852="DECODE_FAILED";
                Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason=DECODE_FAILED pair="+contentPair);
                Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=DECODE_FAILED");return;
            }
            if(early.length()!=es || reference.length()!=rs || early.lastModified()!=em || reference.lastModified()!=rm) {
                lastVerdict28852="RETRYABLE_ERROR";lastVerdictReason28852="FILE_CHANGED";
                Log.i(TAG,"KYANT28852_CALIBRATION_STATE state=RETRYABLE_ERROR reason=FILE_CHANGED pair="+contentPair);
                Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=FILE_CHANGED");return;
            }
            // V288.51: source-specific calibration is based on strict pixel
            // agreement; mtime is retained in diagnostics, not used as a
            // surrogate for image identity. Content key binds the approval.
            Log.i(TAG,"KYANT28851_CALIBRATION_ATTEMPT reason="+reason+
                " timeVerified="+closeInTime+" deltaMs="+delay+
                " source="+a.getWidth()+"x"+a.getHeight()+
                " reference="+b.getWidth()+"x"+b.getHeight()+" pair="+pair);
            String calibrationVerdict28852=calibrateCrop28853(
                app,a,b,contentPair.substring(0,contentPair.indexOf('|')),pair);
            double best=Double.POSITIVE_INFINITY, runner=Double.POSITIVE_INFINITY;
            int bestModel=0,bestDx=0,bestDy=0;
            String bestMapping="";
            Map<String, Double> uniqueMappings = new HashMap<>();
            for(int model=0;model<4;model++) for(int dy=-2;dy<=2;dy++) for(int dx=-3;dx<=3;dx++) {
                double e=mae(a,b,dx,dy,model);
                String mapping=mappingKey(a.getWidth(),a.getHeight(),b.getWidth(),b.getHeight(),dx,dy,model);
                Double old=uniqueMappings.get(mapping);
                if(old == null || e < old) uniqueMappings.put(mapping,e);
                if(e<best) {best=e;bestModel=model;bestDx=dx;bestDy=dy;bestMapping=mapping;}
            }
            for(Map.Entry<String,Double> entry:uniqueMappings.entrySet()) {
                if (!entry.getKey().equals(bestMapping) && entry.getValue()<runner)
                    runner=entry.getValue();
            }
            double margin = runner - best;
            boolean distinctTie = Double.isFinite(runner) && margin <= 0.02;
            // A file time window cannot prove both files belong to one wallpaper update.
            // Do not automatically publish a candidate just because the MAE looks good.
            String[] models={"LEFT_CROP","CENTER_CROP","RIGHT_CROP","STRETCH"};
            // Persist the terminal verdict in memory only after an actual
            // comparison; retryable I/O/decode failures never occupy dedup.
            String[] verdictParts28852=calibrationVerdict28852.split(":",2);
            lastVerdict28852=verdictParts28852[0];
            lastVerdictReason28852=verdictParts28852.length>1 ? verdictParts28852[1] : "COMPLETE";
            Log.i(TAG,"KYANT28852_CALIBRATION_STATE state="+lastVerdict28852+
                " reason="+lastVerdictReason28852+" pair="+contentPair+
                " attempt="+attempts28852);
            lastCompleted=pair;
            lastCompletedContent=contentPair;
            Log.i(TAG,"KYANT28846_FULL_RES_ALIGN reason="+reason+
                " early="+a.getWidth()+"x"+a.getHeight()+
                " reference="+b.getWidth()+"x"+b.getHeight()+
                " deltaMs="+delay+" model="+models[bestModel]+
                " dx="+bestDx+" dy="+bestDy+
                " mae="+String.format(Locale.US,"%.3f",best)+
                " secondDistinctMae="+String.format(Locale.US,"%.3f",runner)+
                " margin="+String.format(Locale.US,"%.3f",margin)+
                " distinctTie="+distinctTie+
                " uniqueMappings="+uniqueMappings.size()+
                " contentPair="+contentPair+
                " pair="+pair+" status=DIAGNOSTIC_ONLY_NO_EARLY_SWAP");
        } finally {
            if(a!=null) a.recycle();
            if(b!=null) b.recycle();
        }
    }
}
