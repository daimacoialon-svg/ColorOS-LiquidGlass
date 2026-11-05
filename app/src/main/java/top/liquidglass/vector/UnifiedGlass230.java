package top.liquidglass.vector;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.util.Log;
import android.view.View;
import java.util.WeakHashMap;

/**
 * Opt-in V230 glass material; a SINGLE surface height field drives the normal,
 * transmission displacement, Fresnel response and wallpaper-derived reflection.
 * Never paints an extra rim on top of the production glass; the V229 shader is
 * left untouched and is the direct fallback when disabled or compilation fails.
 */
final class UnifiedGlass230 {
    private UnifiedGlass230(){}
    private static final WeakHashMap<View,RuntimeShader> CACHE=new WeakHashMap<>();
    private static boolean failed=false;
    private static int logs=0;

    private static final String AGSL="""
        uniform shader wallpaper;
        uniform shader blurredWallpaper;
        uniform float4 bounds;
        uniform float4 validBounds;
        uniform float radius;
        uniform float strength230;
        uniform float thickness230;
        uniform float environment230;
        uniform float quality230;
        uniform float blurMix;

        // Signed distance: positive within this particular rounded container.
        float inside230(float2 p){
            float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));
            float r=clamp(radius,0.0,min(halfSize.x,halfSize.y));
            float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(halfSize-r);
            return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);
        }
        // A single curved lens surface with a broad clear interior.  Both
        // refraction and reflection use the derivative of THIS function.
        float surface230(float2 p){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float shortSide=min(size.x,size.y);
            float2 u=(p-(bounds.xy+bounds.zw)*0.5)/(size*0.5);
            float d=max(inside230(p),0.0);
            float reach=clamp(shortSide*(0.095+0.092*thickness230),14.0,82.0);
            float t=smoothstep(0.0,reach,d);
            float dome=1.0-(0.045+0.095*thickness230)*dot(u,u);
            return t*max(dome,0.15);
        }
        float2 normalSlope230(float2 p){
            float stepPx=1.25;
            float hx=(surface230(p+float2(stepPx,0.0))-surface230(p-float2(stepPx,0.0)))/(2.0*stepPx);
            float hy=(surface230(p+float2(0.0,stepPx))-surface230(p-float2(0.0,stepPx)))/(2.0*stepPx);
            return float2(hx,hy);
        }
        float2 warp230(float2 p,float2 slope){
            float small=min(bounds.z-bounds.x,bounds.w-bounds.y);
            float2 center=(bounds.xy+bounds.zw)*0.5;
            float2 u=(p-center)/max(bounds.zw-bounds.xy,float2(1.0));
            float d=max(inside230(p),0.0);
            float central=smoothstep(0.0,clamp(small*0.12,12.0,40.0),d);
            float2 raw=(slope*small*0.55-u*small*0.023*central)*strength230;
            float limit=clamp(small*0.071,7.0,20.0);
            return raw*limit/sqrt(limit*limit+dot(raw,raw));
        }
        float2 safe230(float2 p){
            float2 lo=validBounds.xy+float2(2.0);
            float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));
            return clamp(p,lo,hi);
        }
        half4 transmission230(float2 samplePos){
            float2 src=safe230(samplePos);
            half4 clear=wallpaper.eval(src);
            half4 softened=blurredWallpaper.eval(src);
            return mix(clear,softened,half(clamp(blurMix,0.0,1.0)));
        }
        half4 main(float2 p){
            float2 slope=normalSlope230(p);
            float2 delta=warp230(p,slope);
            float2 q=safe230(p+delta);
            float edgeZone=clamp(length(slope)*19.0,0.0,1.0);
            half4 base=transmission230(q);
            // Optional AA, restricted to bent regions: the clear interior remains
            // true single-sample transmission, with no blanket glass blur.
            if(quality230>0.5 && edgeZone>0.07){
                float span=0.48;
                half4 aa5=(base + transmission230(q+float2(span,0.0))
                    +transmission230(q+float2(-span,0.0))
                    +transmission230(q+float2(0.0,span))
                    +transmission230(q+float2(0.0,-span)))/5.0;
                if(quality230>1.5){
                    half4 aa9=(aa5*5.0
                        +transmission230(q+float2(span,span))
                        +transmission230(q+float2(-span,span))
                        +transmission230(q+float2(span,-span))
                        +transmission230(q+float2(-span,-span)))/9.0;
                    base=mix(base,aa9,half(smoothstep(0.08,0.45,edgeZone)));
                } else {
                    base=mix(base,aa5,half(smoothstep(0.08,0.45,edgeZone)));
                }
            }

            // Refraction, reflection and glancing highlights share the SAME
            // normal and curvature: they cannot drift out of alignment.
            float3 n=normalize(float3(-slope*8.5,1.0));
            float3 light=normalize(float3(-0.48,-0.58,0.66));
            float grazing=pow(clamp(1.0-n.z,0.0,1.0),0.88);
            float fresnel=(0.024+0.27*grazing)*environment230*strength230;
            float2 envDir=normalize(float2(-n.x-0.31,-n.y-0.20));
            float2 envP=safe230(q+envDir*(4.0+12.0*environment230));
            float3 envRgb=float3(wallpaper.eval(envP).rgb);
            float3 baseRgb=float3(base.rgb);
            float envLum=dot(envRgb,float3(0.2126,0.7152,0.0722));
            float baseLum=dot(baseRgb,float3(0.2126,0.7152,0.0722));
            float contrast=clamp(abs(envLum-baseLum)*2.1,0.0,1.0);
            float highlight=pow(clamp(dot(n,light),0.0,1.0),6.0)*grazing;
            float materialReflect=clamp(fresnel*(0.70+0.60*contrast),0.0,0.26);
            float3 color=mix(baseRgb,envRgb,materialReflect);
            // Colored, curvature-dependent response instead of an independent
            // white line / luminous frame painted on top.
            color+=(1.0-color)*(highlight*(0.055+0.10*contrast)*environment230*strength230);
            color*=1.0-(1.0-clamp(dot(n,light),0.0,1.0))*grazing*0.055*environment230*strength230;
            return half4(half3(clamp(color,0.0,1.0)),base.a);
        }
        """;

    static boolean draw(View view,Canvas canvas,RectF rect,float radius,
            BitmapShader wallpaper,BitmapShader blurredWallpaper,RectF valid,float blurMix){
        MaterialConfig230.State s=MaterialConfig230.get();
        if(!s.enabled || failed)return false;
        try{
            RuntimeShader shader;
            synchronized(CACHE){
                shader=CACHE.get(view);
                if(shader==null){
                    shader=new RuntimeShader(AGSL);
                    CACHE.put(view,shader);
                    Log.i("LiquidGlass230","MATERIAL_SHADER_CREATED_230 singlePass=true");
                }
            }
            shader.setInputShader("wallpaper",wallpaper);
            shader.setInputShader("blurredWallpaper",blurredWallpaper);
            shader.setFloatUniform("bounds",rect.left,rect.top,rect.right,rect.bottom);
            shader.setFloatUniform("validBounds",valid.left,valid.top,valid.right,valid.bottom);
            shader.setFloatUniform("radius",Math.max(0f,radius));
            shader.setFloatUniform("strength230",s.strength/100f);
            shader.setFloatUniform("thickness230",s.thickness/100f);
            shader.setFloatUniform("environment230",s.environment/100f);
            shader.setFloatUniform("quality230",(float)s.quality);
            shader.setFloatUniform("blurMix",Math.max(0f,Math.min(1f,blurMix)));
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            canvas.drawRoundRect(rect,radius,radius,paint);
            if(logs++<16)Log.i("LiquidGlass230","MATERIAL_RENDER_230 dimensions="+rect.width()+"x"+rect.height()+
                " strength="+s.strength+" thick="+s.thickness+" env="+s.environment+" quality="+s.quality);
            return true;
        }catch(Throwable fail){
            // Never disable the V229 lens.  The caller immediately uses it.
            failed=true;
            Log.e("LiquidGlass230","MATERIAL_SHADER_FAILED_230 fallback=V229",fail);
            return false;
        }
    }
}
