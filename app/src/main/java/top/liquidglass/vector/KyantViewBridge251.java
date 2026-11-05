package top.liquidglass.vector;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

/** Experimental Android View bridge to Kyant's ORIGINAL rounded-rect AGSL.
 * Input bitmap must already contain the backdrop in local view coordinates.
 * This class does NOT capture wallpaper or install a Launcher hook.
 */
public final class KyantViewBridge251 {
    private final RuntimeShader effect;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    public KyantViewBridge251() {
        if (Build.VERSION.SDK_INT < 33) throw new UnsupportedOperationException("AGSL needs API 33+");
        effect = new RuntimeShader(KyantOriginalShader251.SOURCE);
        paint.setShader(effect);
    }
    public void draw(Canvas canvas, Bitmap backdrop, float x, float y, float width, float height,
                     float radius, float heightPx, float amountPx, float depthEffect) {
        if (backdrop == null || backdrop.isRecycled() || width <= 0 || height <= 0) return;
        // Caller supplies a view-local backdrop, avoiding double translation.
        effect.setInputShader("content", new BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        effect.setFloatUniform("size", width, height);
        effect.setFloatUniform("offset", 0f, 0f);
        effect.setFloatUniform("cornerRadii", radius, radius, radius, radius);
        effect.setFloatUniform("refractionHeight", Math.max(0.01f, heightPx));
        effect.setFloatUniform("refractionAmount", amountPx);
        effect.setFloatUniform("depthEffect", depthEffect);
        canvas.save(); canvas.translate(x,y);
        canvas.drawRoundRect(0,0,width,height,radius,radius,paint);
        canvas.restore();
    }
}
