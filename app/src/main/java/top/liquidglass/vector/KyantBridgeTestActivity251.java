package top.liquidglass.vector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;

/** Test host only: synthetic high-contrast backdrop and original Kyant shader. */
public final class KyantBridgeTestActivity251 extends Activity {
    @Override public void onCreate(Bundle state) { super.onCreate(state); setTitle("Kyant 原版 Shader 桥接测试"); setContentView(new Demo()); }
    private final class Demo extends View {
        private final Paint p = new Paint(3);
        private KyantViewBridge251 bridge;
        Demo() { super(KyantBridgeTestActivity251.this); setLayerType(View.LAYER_TYPE_HARDWARE,null); }
        @Override protected void onDraw(Canvas c) {
            int w=getWidth(),h=getHeight(); if(w<2||h<2)return;
            Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas bc=new Canvas(b);bc.drawColor(Color.rgb(25,37,49));
            p.setStrokeWidth(3);for(int x=0;x<w;x+=26){p.setColor((x/26)%2==0?0xff78e0cb:0xffc3d6e8);bc.drawLine(x,0,x,h,p);}
            for(int y=0;y<h;y+=26){p.setColor(0xff697f93);bc.drawLine(0,y,w,y,p);}
            c.drawBitmap(b,0,0,null);
            try {
                if(bridge==null)bridge=new KyantViewBridge251();
                float left=w*0.10f,top=h*0.25f,ww=w*0.80f,hh=Math.min(w*0.80f,h*0.43f);
                // V252: feed full backdrop; keep real off-glass pixels for sampling.
                bridge.draw(c,b,left,top,ww,hh,Math.min(ww,hh)*0.20f,Math.min(ww,hh)*0.23f,Math.min(ww,hh)*0.18f,0.08f);
            }catch(Throwable ex){p.setColor(Color.RED);p.setTextSize(26);c.drawText(ex.getClass().getSimpleName()+": "+ex.getMessage(),20,h/2f,p);}
            b.recycle();
        }
    }
}
