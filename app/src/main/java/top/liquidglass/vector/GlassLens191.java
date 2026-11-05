package top.liquidglass.vector;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.view.View;
import java.util.WeakHashMap;

/** V192 unified height-field lens experiment; previous renderer kept in GlassLens173. */
final class GlassLens191 {
    private GlassLens191() {}
    private static final WeakHashMap<View, RuntimeShader> CACHE = new WeakHashMap<>();
    // V217: never compile experimental grid code in the production lens.
    private static final WeakHashMap<View, RuntimeShader> GRID_CACHE_217 = new WeakHashMap<>();
    // V220 compact production shader: continuous rounded-SDF 2D displacement; no nested 3x3 sampling.
    static final String AGSL =
        "uniform shader wallpaper;\n" +
        "uniform shader blurredWallpaper;\n" +
        "uniform float4 bounds;\n" +
        "uniform float4 validBounds;\n" +
        "uniform float radius;\n" +
        "uniform float strength;\n" +
        "uniform float blurMix;\n" +
        "uniform float style;\n" +
        "uniform float reflectionMode;\n" +
        "uniform float reflectionStrength;\n" +
        "uniform float reflectionSoftness;\n" +
        "uniform float edgeStrength;\n" +
        "uniform float diagnostic;\n" +
        "uniform float aaProbe208;\n" +
        "float sdf(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,1.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "float2 displacement(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float small=min(h.x,h.y)*2.0;\n" +
        " float d=max(sdf(p),0.0);\n" +
        " float band=clamp(small*0.32,16.0,65.0);\n" +
        " float shoulder=1.0-smoothstep(0.0,band,d);\n" +
        " float e=1.6;\n" +
        " float2 n=float2(sdf(p+float2(e,0.0))-sdf(p-float2(e,0.0)),sdf(p+float2(0.0,e))-sdf(p-float2(0.0,e)))/(2.0*e);\n" +
        " float2 rim=n*(small*0.105)*shoulder;\n" +
        " float2 core=-uv*(small*0.050)*exp(-0.85*dot(uv,uv))*smoothstep(0.0,band*0.7,d);\n" +
        " float2 raw=(rim+core)*clamp(strength,0.0,1.0)*(style>1.5?1.10:1.0);\n" +
        " float cap=clamp(small*0.11,9.0,23.0);\n" +
        " return raw*(cap/sqrt(cap*cap+dot(raw,raw)));\n" +
        "}\n" +
        "half4 main(float2 p){\n" +
        " float d=max(sdf(p),0.0);\n" +
        " float2 delta=displacement(p);\n" +
        " float2 lo=validBounds.xy+float2(2.0);\n" +
        " float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));\n" +
        " float2 q=p+delta;\n" +
        " float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));\n" +
        " float2 sc=clamp(mix(p,q,smoothstep(0.0,14.0,margin)),lo,hi);\n" +
        " if(aaProbe208>17.5 && aaProbe208<18.5){\n" +
        "  float spacing=clamp(edgeStrength*16.0,8.0,16.0);\n" +
        "  float2 g=abs(fract(sc/spacing)-0.5)*spacing;\n" +
        "  float ink=1.0-smoothstep(0.4,1.1,min(g.x,g.y));\n" +
        "  return half4(half3(1.0-ink),1.0);\n" +
        " }\n" +
        " if(diagnostic>15.5 && diagnostic<17.5){\n" +
        "  float v=clamp(length(delta)/9.0,0.0,1.0);\n" +
        "  return half4(half3(v,1.0-v,0.0),1.0);\n" +
        " }\n" +
        " half4 clear=wallpaper.eval(sc);\n" +
        " half4 blurred=blurredWallpaper.eval(sc);\n" +
        " half4 result=mix(clear,blurred,half(clamp(blurMix,0.0,1.0)));\n" +
        " float edge=exp(-d*0.19)*smoothstep(0.0,1.4,d);\n" +
        " float light=clamp(edgeStrength,0.0,1.0)*edge*0.065;\n" +
        " float surface=reflectionMode>0.5?clamp(reflectionStrength,0.0,2.0)*edge*0.027:0.0;\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*(light+surface);\n" +
        " color*=1.0-edge*0.014;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n";
    private static final String WHITE_GRID_AGSL_217=AGSL;
    private static boolean GRID_FAILED_217=false;
    private static int ROUTE_LOGS_218=0;
    private static boolean PRODUCTION_FAILED_220=false;

    static void draw(View owner, Canvas canvas, RectF rect, float radius,
            BitmapShader wallpaper, BitmapShader blurredWallpaper, RectF valid,
            float strength, float blurMix, int diagnostic, boolean edgeEnabled,
            int edgeStrength, int surfaceMode, int surfaceStrength, int surfaceSoftness,
            int opticalStyle) {
        int aaProbe208=0;
        try { aaProbe208=android.provider.Settings.Global.getInt(owner.getContext().getContentResolver(),"liquidglass_aa_probe_208",0); } catch(Throwable ignored) {}
        boolean gridRequested=aaProbe208==18;
        if(ROUTE_LOGS_218<12){
            ROUTE_LOGS_218++;
            android.util.Log.i("LiquidGlass218","LENS_SHADER_ENTRY mode="+aaProbe208+
                " strength="+strength+" style="+opticalStyle+" bounds="+rect.toShortString());
        }
        RuntimeShader shader=null;
        if(gridRequested && !GRID_FAILED_217){
            synchronized(GRID_CACHE_217){
                shader=GRID_CACHE_217.get(owner);
                if(shader==null){
                    try{
                        if(!WHITE_GRID_AGSL_217.contains(GRID_NEW_217))throw new IllegalStateException("grid replacement missing");
                        shader=new RuntimeShader(WHITE_GRID_AGSL_217);
                        GRID_CACHE_217.put(owner,shader);
                    }catch(Throwable t){
                        GRID_FAILED_217=true;
                        android.util.Log.e("LiquidGlass217","GRID_217_SHADER_FAILED; production lens preserved",t);
                    }
                }
            }
        }
        boolean usingGrid=gridRequested && shader!=null;
        if(shader==null){
            if(PRODUCTION_FAILED_220)return;
            synchronized(CACHE){
                shader=CACHE.get(owner);
                if(shader==null){
                    try {
                        shader=new RuntimeShader(AGSL);
                        CACHE.put(owner,shader);
                        android.util.Log.i("LiquidGlass218","LENS_SHADER_CREATED production=true");
                    } catch(Throwable failure218) {
                        PRODUCTION_FAILED_220=true;
                        android.util.Log.e("LiquidGlass218","LENS_SHADER_FAILED production=true V220 disabled retry",failure218);
                        return;
                    }
                }
            }
        }
        shader.setInputShader("wallpaper", wallpaper);
        shader.setInputShader("blurredWallpaper", blurredWallpaper);
        shader.setFloatUniform("bounds", rect.left, rect.top, rect.right, rect.bottom);
        shader.setFloatUniform("validBounds", valid.left, valid.top, valid.right, valid.bottom);
        shader.setFloatUniform("radius", Math.max(0f, radius));
        shader.setFloatUniform("strength", Math.max(0f, Math.min(1f, strength)));
        shader.setFloatUniform("blurMix", Math.max(0f, Math.min(1f, blurMix)));
        shader.setFloatUniform("diagnostic", (float) diagnostic);
        // If the isolated grid cannot compile, show the known-good mode 16 lens.
        shader.setFloatUniform("aaProbe208", (float)(gridRequested && !usingGrid ? 16 : Math.max(0,Math.min(20,aaProbe208))));
        shader.setFloatUniform("style", (float) opticalStyle);
        shader.setFloatUniform("reflectionMode", (float) surfaceMode);
        shader.setFloatUniform("reflectionStrength", Math.max(0f, Math.min(2f, surfaceStrength / 100f)));
        shader.setFloatUniform("reflectionSoftness", Math.max(0f, Math.min(1f, (surfaceSoftness - 50f) / 150f)));
        int gridSpacing217=12;
        if(usingGrid){
            try { gridSpacing217=android.provider.Settings.Global.getInt(owner.getContext().getContentResolver(),"liquidglass_grid_spacing_216",12); }
            catch(Throwable ignored) {}
            if(gridSpacing217!=8 && gridSpacing217!=12 && gridSpacing217!=16)gridSpacing217=12;
        }
        // In diagnostic mode 18 only, reuse an existing uniform. No new shader
        // uniform or production configuration schema is introduced.
        shader.setFloatUniform("edgeStrength", usingGrid ? gridSpacing217/16f :
            (edgeEnabled ? Math.max(0f, Math.min(1f, edgeStrength / 100f)) : 0f));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
