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
        // V234: one continuous optical volume, not a separately drawn rim.
        // The SDF provides a single differentiable shoulder over a substantial
        // part of the container, including its rounded corners.
        float surface230(float2 p){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float d=max(inside230(p),0.0);
            float shortSide=min(size.x,size.y);
            float reach=clamp(shortSide*(0.24+0.20*thickness230),18.0,140.0);
            float t=clamp(d/reach,0.0,1.0);
            // Raised cosine: slope is zero at both ends, with no hard facet.
            float shoulder=0.5-0.5*cos(3.14159265*t);
            float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/max(size*0.5,float2(1.0));
            float crown=clamp(1.0-0.12*dot(uv,uv),0.0,1.0);
            return shoulder*(0.88+0.12*crown);
        }
        float2 normalSlope230(float2 p){
            float e=1.5;
            return float2(surface230(p+float2(e,0.0))-surface230(p-float2(e,0.0)),
                          surface230(p+float2(0.0,e))-surface230(p-float2(0.0,e)))/(2.0*e);
        }
        float3 refract230(float3 incident,float3 normal,float eta){
            float cosI=clamp(-dot(normal,incident),-1.0,1.0);
            float k=1.0-eta*eta*(1.0-cosI*cosI);
            if(k<0.0)return reflect(incident,normal);
            return eta*incident+(eta*cosI-sqrt(max(0.0,k)))*normal;
        }
        float2 warp230(float2 p,float2 slope){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float shortSide=min(size.x,size.y);
            float2 center=(bounds.xy+bounds.zw)*0.5;
            float2 uv=(p-center)/max(size*0.5,float2(1.0));
            float3 frontN=normalize(float3(-slope*(13.0+8.0*thickness230),1.0));
            float3 rayInside=refract230(float3(0.0,0.0,-1.0),frontN,1.0/1.47);
            // Rear surface must face the incoming ray (+Z); the V233 -Z
            // normal was reversed and could produce an incorrect refraction.
            float2 rearSlope=-uv*(0.022+0.018*thickness230);
            float3 rearN=normalize(float3(-rearSlope,1.0));
            float3 rayOut=refract230(rayInside,rearN,1.47);
            float z=max(abs(rayOut.z),0.30);
            float2 displacement=rayOut.xy/z*shortSide*(0.08+0.10*thickness230)*strength230;
            // Continuous bounded response, including at strength 100%.
            float limit=clamp(shortSide*0.11,7.0,32.0);
            return displacement*limit/sqrt(limit*limit+dot(displacement,displacement));
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
            float edgeZone=clamp(length(slope)*35.0,0.0,1.0);
            half4 base=transmission230(q);
            // Optional AA, restricted to bent regions: the clear interior remains
            // true single-sample transmission, with no blanket glass blur.
            if(quality230>0.5 && edgeZone>0.07){
                float span=0.58;
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
            float3 n=normalize(float3(-slope*(13.0+8.0*thickness230),1.0));
            float3 light=normalize(float3(-0.48,-0.58,0.66));
            float grazing=pow(clamp(1.0-n.z,0.0,1.0),0.80);
            float fresnel=(0.045+0.65*pow(grazing,0.75))*environment230*strength230;
            float2 envDir=normalize(float2(-n.x-0.31,-n.y-0.20)+float2(0.0001));
            float2 envP=safe230(p+envDir*(10.0+35.0*environment230));
            float3 envRgb=float3(wallpaper.eval(envP).rgb);
            float3 baseRgb=float3(base.rgb);
            float envLum=dot(envRgb,float3(0.2126,0.7152,0.0722));
            float baseLum=dot(baseRgb,float3(0.2126,0.7152,0.0722));
            float contrast=clamp(abs(envLum-baseLum)*2.1,0.0,1.0);
            float highlight=pow(clamp(dot(n,light),0.0,1.0),6.0)*grazing;
            float materialReflect=clamp(fresnel*(0.85+0.65*contrast),0.0,0.52);
            float3 color=mix(baseRgb,envRgb,materialReflect);
            // Colored, curvature-dependent response instead of an independent
            // white line / luminous frame painted on top.
            color+=(1.0-color)*(highlight*(0.09+0.14*contrast)*environment230*strength230);
            color*=1.0-(1.0-clamp(dot(n,light),0.0,1.0))*grazing*0.055*environment230*strength230;
            return half4(half3(clamp(color,0.0,1.0)),1.0);
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
                    Log.i("LiquidGlass230","MATERIAL_SHADER_CREATED_234 continuousVolume=true");
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
            if(logs++<16)Log.i("LiquidGlass230","MATERIAL_RENDER_234 dimensions="+rect.width()+"x"+rect.height()+
                " strength="+s.strength+" thick="+s.thickness+" env="+s.environment+" quality="+s.quality);
            return true;
        }catch(Throwable fail){
            // Never disable the V229 lens.  The caller immediately uses it.
            failed=true;
            Log.e("LiquidGlass230","MATERIAL_SHADER_FAILED_234 fallback=V229",fail);
            return false;
        }
    }
}
