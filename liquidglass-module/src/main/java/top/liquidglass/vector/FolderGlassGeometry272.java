package top.liquidglass.vector;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import de.robv.android.xposed.XposedBridge;

/** Conservative OEM geometry probe: never mistake icon child bounds for glass. */
public final class FolderGlassGeometry272 {
    private static boolean logged;
    private FolderGlassGeometry272() {}
    public static Rect resolve(View folder) {
        int w = Math.max(1, folder.getWidth()), h = Math.max(1, folder.getHeight());
        Rect full = new Rect(0, 0, w, h);
        android.graphics.RectF precise=HookEntry.kyantFolderGlassRect273(folder);
        if(precise!=null && !precise.isEmpty()) {
            Rect b=new Rect(Math.round(precise.left),Math.round(precise.top),
                Math.round(precise.right),Math.round(precise.bottom));
            logOnce("legacyGlassGeometry",fallback,b,"mBackground.previewDrawable");
            return b;
        }
        Drawable bg = folder.getBackground();
        if (bg != null) {
            Rect b = bg.getBounds();
            if (b != null && b.width() > 0 && b.height() > 0 &&
                b.left >= 0 && b.top >= 0 && b.right <= w && b.bottom <= h) {
                logOnce("backgroundDrawable", full, b, bg.getClass().getName(), true);
                return new Rect(b);
            }
        }
        logOnce("viewFallback_unresolved", full, full,
                bg == null ? "background=null" : bg.getClass().getName(), false);
        return full;
    }
    private static synchronized void logOnce(String source, Rect view, Rect glass,
                                              String drawable, boolean measured) {
        if (logged) return;
        logged = true;
        XposedBridge.log("KYANT273_GEOMETRY source=" + source +
            " viewBounds=" + view.toShortString() +
            " backgroundBounds=" + glass.toShortString() +
            " lensBounds=" + glass.toShortString() +
            " drawable=" + drawable + " measured=" + measured);
    }
}
