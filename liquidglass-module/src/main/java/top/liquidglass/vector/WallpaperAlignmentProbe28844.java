package top.liquidglass.vector;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** V288.45: event-only full-resolution alignment diagnostic.
 * Never replaces the published Kyant reference or changes the optical renderer.
 * This probe is deliberately conservative about file generation matching.
 */
final class WallpaperAlignmentProbe28844 {
    private WallpaperAlignmentProbe28844() {}
    private static final String TAG = "ColorOSLiquidGlass";
    private static final AtomicBoolean busy = new AtomicBoolean(false);
    private static volatile boolean pending;
    private static volatile String lastCompleted = "";
    private static final long MAX_PAIR_AGE_MS = 45000L;
    // Two full-resolution 1217x2640 ARGB_8888 images require about 25 MiB.
    // Refuse unusually large images to avoid temporary memory pressure.
    private static final long MAX_TOTAL_PIXELS = 13000000L;

    static void schedule(Context context, String reason) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        if (!busy.compareAndSet(false, true)) { pending = true; return; }
        new Thread(() -> {
            try { compare(app, reason); }
            catch (Throwable e) { Log.w(TAG, "KYANT28845_ALIGN_ERROR type=" + e.getClass().getSimpleName()); }
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
            Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=MISSING_SOURCE"); return;
        }
        long em=early.lastModified(),rm=reference.lastModified(),es=early.length(),rs=reference.length();
        long delay=rm-em;
        if(delay<0 || delay>MAX_PAIR_AGE_MS) {
            Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=UNPAIRED_TIME deltaMs="+delay); return;
        }
        String pair=es+":"+em+"|"+rs+":"+rm;
        if(pair.equals(lastCompleted)) {
            Log.i(TAG,"KYANT28845_ALIGN_DEDUP pair="+pair); return;
        }
        int[] ad=dimensions(early),bd=dimensions(reference);
        if(ad[0]<1 || ad[1]<1 || bd[0]<1 || bd[1]<1 ||
           (long)ad[0]*ad[1]+(long)bd[0]*bd[1]>MAX_TOTAL_PIXELS) {
            Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=INVALID_OR_LARGE_DIMENSIONS"); return;
        }
        Bitmap a=null,b=null;
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            options.inSampleSize=1;
            a=BitmapFactory.decodeFile(early.getAbsolutePath(),options);
            b=BitmapFactory.decodeFile(reference.getAbsolutePath(),options);
            if(a==null || b==null) { Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=DECODE_FAILED");return; }
            if(early.length()!=es || reference.length()!=rs || early.lastModified()!=em || reference.lastModified()!=rm) {
                Log.i(TAG,"KYANT28845_ALIGN_SKIP reason=FILE_CHANGED");return;
            }
            double best=Double.POSITIVE_INFINITY, runner=Double.POSITIVE_INFINITY;
            int bestModel=0,bestDx=0,bestDy=0;
            for(int model=0;model<4;model++) for(int dy=-2;dy<=2;dy++) for(int dx=-3;dx<=3;dx++) {
                double e=mae(a,b,dx,dy,model);
                if(e<best) {runner=best;best=e;bestModel=model;bestDx=dx;bestDy=dy;}
                else if(e<runner) runner=e;
            }
            // A file time window cannot prove both files belong to one wallpaper update.
            // Do not automatically publish a candidate just because the MAE looks good.
            String[] models={"LEFT_CROP","CENTER_CROP","RIGHT_CROP","STRETCH"};
            lastCompleted=pair;
            Log.i(TAG,"KYANT28845_FULL_RES_ALIGN reason="+reason+
                " early="+a.getWidth()+"x"+a.getHeight()+
                " reference="+b.getWidth()+"x"+b.getHeight()+
                " deltaMs="+delay+" model="+models[bestModel]+
                " dx="+bestDx+" dy="+bestDy+
                " mae="+String.format(Locale.US,"%.3f",best)+
                " secondMae="+String.format(Locale.US,"%.3f",runner)+
                " pair="+pair+" status=DIAGNOSTIC_ONLY_NO_EARLY_SWAP");
        } finally {
            if(a!=null) a.recycle();
            if(b!=null) b.recycle();
        }
    }
}
