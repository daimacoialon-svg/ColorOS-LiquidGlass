package top.liquidglass.vector;

import android.graphics.BitmapShader;
import android.provider.Settings;
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
    private static volatile int diagnostic235=0;
    private static int probeCounter235=0;
    private static int probeLogs235=0;
    private static int routeLogs237=0;
    // Diagnostic is read-only and intentionally NOT part of LG230 persistence.
    private static final String DIAG_KEY_235="liquidglass_optical_diagnostic_235";

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
        uniform float diagnostic235;

        // Signed distance: positive within this particular rounded container.
        float inside230(float2 p){
            float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));
            float r=clamp(radius,0.0,min(halfSize.x,halfSize.y));
            float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(halfSize-r);
            return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);
        }
        // V236 experimental C1-continuous superellipse optical surface.
        // A rounded-rectangle SDF is exact for clipping, but its nearest-edge
        // gradient has a medial-axis seam (the X visible in V235 diagnostics).
        // Use a smooth superellipse only for optical height/normal calculation;
        // the actual container is still clipped by drawRoundRect.
        // V238: one differentiable rounded-superellipse height field. The
        // quartic radius has no nearest-edge branch, avoiding diagonal X seams.
        float surface230(float2 p){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/max(size*0.5,float2(1.0));
            float radial=pow(pow(abs(uv.x),4.0)+pow(abs(uv.y),4.0)+0.00000001,0.25);
            float depth=clamp(1.0-radial,0.0,1.0);
            float shoulder=mix(0.19,0.48,clamp(thickness230,0.0,1.0));
            // Smooth shoulder reaches farther into the glass than a thin rim.
            float t=clamp(depth/max(shoulder,0.01),0.0,1.0);
            float smooth=t*t*(3.0-2.0*t);
            float dome=clamp(1.0-radial*radial,0.0,1.0);
            return 0.55*smooth+0.45*dome;
        }
        float2 normalSlope230(float2 p){
            float e=1.0;
            return float2(surface230(p+float2(e,0.0))-surface230(p-float2(e,0.0)),
                          surface230(p+float2(0.0,e))-surface230(p-float2(0.0,e)))/(2.0*e);
        }
        // V239: Convert derivatives from screen pixels to normalized optical slopes.
        // The V238 derivatives were tiny on large folders and therefore generated
        // almost no visible wallpaper displacement even at maximum UI strength.
        float2 opticalSlope239(float2 slope){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float2 normalized=slope*size*0.5;
            return normalized/(1.0+length(normalized)*0.42);
        }
        float2 warp230(float2 p,float2 slope){
            float2 size=max(bounds.zw-bounds.xy,float2(2.0));
            float shortSide=min(size.x,size.y);
            float2 ns=opticalSlope239(slope);
            float strength=clamp(strength230,0.0,1.0);
            // One continuous displacement field, not four independent edge warps.
            // Smoothly saturate at the configured maximum without a hard cut.
            float amplitude=min(shortSide*0.115,32.0)*(0.20+0.80*thickness230)*strength;
            float2 raw=-ns*amplitude;
            float limit=min(shortSide*0.14,36.0);
            return raw*limit/sqrt(limit*limit+dot(raw,raw)+0.0001);
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
            // V235: isolate actual shader stages without replacing the production path.
            // 1 displacement, 2 normals, 3 continuous surface, 4 raw source,
            // 5 warped source only, 6 final material (same as mode 0).
            if(diagnostic235>0.5 && diagnostic235<1.5){
                float2 size=max(bounds.zw-bounds.xy,float2(1.0));
                float2 disp=(q-p)/max(size,float2(1.0));
                return half4(half3(clamp(0.5+disp.x*5.0,0.0,1.0),
                    clamp(0.5+disp.y*5.0,0.0,1.0),0.5),1.0);
            }
            if(diagnostic235>1.5 && diagnostic235<2.5){
                float3 nn=normalize(float3(-slope*(13.0+8.0*thickness230),1.0));
                return half4(half3(nn*0.5+0.5),1.0);
            }
            if(diagnostic235>2.5 && diagnostic235<3.5){
                float h=surface230(p);
                return half4(half3(h,h,h),1.0);
            }
            if(diagnostic235>3.5 && diagnostic235<4.5){
                return transmission230(p);
            }
            if(diagnostic235>4.5 && diagnostic235<5.5){
                return transmission230(q);
            }
            float3 n=normalize(float3(-opticalSlope239(slope)*(0.75+0.85*thickness230),1.0));
            float grazing=clamp(1.0-n.z,0.0,1.0);
            float edgeZone=smoothstep(0.005,0.22,grazing);
            half4 base=transmission230(q);
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
                    base=mix(base,aa9,half(edgeZone*0.65));
                } else {
                    base=mix(base,aa5,half(edgeZone*0.65));
                }
            }
            // Schlick Fresnel, shared surface normal. The reflected environment
            // is approximated from actual wallpaper pixels, not a fixed tint.
            float cosTheta=clamp(n.z,0.0,1.0);
            float f0=0.04;
            float fresnel=f0+(1.0-f0)*pow(1.0-cosTheta,5.0);
            float2 envDir=normalize(float2(-n.x-0.23,-n.y-0.17)+float2(0.0001));
            float envReach=(8.0+20.0*environment230)*strength230;
            float3 envA=float3(wallpaper.eval(safe230(p+envDir*envReach)).rgb);
            float3 envB=float3(wallpaper.eval(safe230(p-envDir*envReach*0.55)).rgb);
            float3 envRgb=mix(envA,envB,0.25);
            float3 baseRgb=float3(base.rgb);
            float reflectWeight=clamp(fresnel*environment230*(0.25+0.75*strength230),0.0,0.24);
            float3 color=mix(baseRgb,envRgb,reflectWeight);
            float3 light=normalize(float3(-0.48,-0.58,0.66));
            float spec=pow(clamp(dot(n,light),0.0,1.0),24.0)*edgeZone;
            color+=(1.0-color)*spec*0.075*environment230*strength230;
            return half4(half3(clamp(color,0.0,1.0)),1.0);
        }
        """;

    static boolean draw(View view,Canvas canvas,RectF rect,float radius,
            BitmapShader wallpaper,BitmapShader blurredWallpaper,RectF valid,float blurMix){
        MaterialConfig230.State s=MaterialConfig230.get();
        // Settings.Global is a transient, opt-in test switch; normal mode is 0.
        // Sampling every 60 draws avoids a binder read for every frame.
        if((++probeCounter235 % 60)==1){
            try{
                int next=Settings.Global.getInt(view.getContext().getContentResolver(),DIAG_KEY_235,0);
                diagnostic235=Math.max(0,Math.min(6,next));
            }catch(Throwable ignored){diagnostic235=0;}
        }
        if(!s.enabled || failed)return false;
        try{
            RuntimeShader shader;
            synchronized(CACHE){
                shader=CACHE.get(view);
                if(shader==null){
                    shader=new RuntimeShader(AGSL);
                    CACHE.put(view,shader);
                    Log.i("LiquidGlass230","MATERIAL_SHADER_CREATED_239 continuousSurface=true");
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
            shader.setFloatUniform("diagnostic235",(float)diagnostic235);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            paint.setShader(shader);
            canvas.drawRoundRect(rect,radius,radius,paint);
            if(routeLogs237++<20)Log.i("LiquidGlass239",
                "OPTICAL_PIPELINE_239 wallpaper=bound blurred=bound bounds="+rect.toShortString()+
                " valid="+valid.toShortString()+" blurMix="+blurMix+
                " mode="+diagnostic235+" material=singlePass");
            if(diagnostic235!=0 && probeLogs235++<24)Log.i("LiquidGlass235",
                "OPTICAL_STAGE mode="+diagnostic235+" bounds="+rect.toShortString()+
                " valid="+valid.toShortString()+" blurMix="+blurMix);
            if(logs++<16)Log.i("LiquidGlass230","MATERIAL_RENDER_239 dimensions="+rect.width()+"x"+rect.height()+
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
