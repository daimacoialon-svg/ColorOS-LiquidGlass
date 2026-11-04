package top.liquidglass.vector;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.view.View;
import java.util.WeakHashMap;

/** V191 independent optical experiment. V190 stays intact for rollback. */
final class GlassLens191 {
    private GlassLens191() {}
    private static final WeakHashMap<View, RuntimeShader> CACHE = new WeakHashMap<>();
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
        "float roundedDepth(float2 p){\n" +
        " float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(halfSize.x,halfSize.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(halfSize-r);\n" +
        " return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "float2 displacement(float2 p){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/(size*0.5);\n" +
        " float minSide=min(size.x,size.y);\n" +
        " float d=max(roundedDepth(p),0.0);\n" +
        " float band=clamp(minSide*0.30,14.0,48.0);\n" +
        " band=min(band,max(5.0,radius*0.95));\n" +
        " float t=clamp(d/band,0.0,1.0);\n" +
        " // Edge surface slope is zero at both ends: no medial-axis SDF seams.\n" +
        " float2 grad=float2(0.5*(roundedDepth(p+float2(1.0,0.0))-roundedDepth(p-float2(1.0,0.0))),0.5*(roundedDepth(p+float2(0.0,1.0))-roundedDepth(p-float2(0.0,1.0))));\n" +
        " float rimSlope=6.0*t*(1.0-t);\n" +
        " float2 rim=grad*(band*0.115)*rimSlope;\n" +
        " // Smooth whole-surface deformation: no central crown and no fixed glint.\n" +
        " float2 inner=uv*(minSide*0.035)/(1.0+0.72*dot(uv,uv));\n" +
        " float inside=smoothstep(0.04,0.92,t);\n" +
        " float gain=style>1.5?1.0:0.73;\n" +
        " float2 raw=(rim+inner*inside)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(minSide*0.11,10.0,22.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "half4 main(float2 p){\n" +
        " float d=max(roundedDepth(p),0.0);\n" +
        " float2 delta=displacement(p);\n" +
        " // Do not clamp the distorted sample to the last bitmap column: that creates\n" +
        " // the stretched bands seen near the screen boundary. Fade displacement to\n" +
        " // zero BEFORE reaching the valid sample domain.\n" +
        " float2 lo=validBounds.xy+float2(2.0);\n" +
        " float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));\n" +
        " float2 q=p+delta;\n" +
        " float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));\n" +
        " float safety=smoothstep(0.0,12.0,margin);\n" +
        " float2 safeP=mix(p,q,safety);\n" +
        " // The caller uses the same bitmap transform for both child shaders.\n" +
        " half4 clear=wallpaper.eval(safeP);\n" +
        " half4 blurred=blurredWallpaper.eval(safeP);\n" +
        " half4 result=mix(clear,blurred,half(clamp(blurMix,0.0,1.0)));\n" +
        " if(diagnostic>10.5 && diagnostic<11.5){\n" +
        "   float2 uv=(safeP-validBounds.xy)/float2(16.0);\n" +
        "   float2 f=abs(fract(uv)-0.5);\n" +
        "   float line=1.0-smoothstep(0.45,0.49,max(f.x,f.y));\n" +
        "   return half4(half3(1.0-line),1.0);\n" +
        " }\n" +
        " if(diagnostic>15.5 && diagnostic<16.5){\n" +
        "   float v=clamp(length(delta)/9.0,0.0,1.0);\n" +
        "   return half4(half3(v,1.0-v,0.0),1.0);\n" +
        " }\n" +
        " if(diagnostic>16.5 && diagnostic<17.5){\n" +
        "   float v=clamp(length(delta)/9.0,0.0,1.0);\n" +
        "   return half4(half3(0.0,v,1.0-v),1.0);\n" +
        " }\n" +
        " // Lighting never affects the sample coordinates, regardless of mode.\n" +
        " float2 norm=delta/max(4.0,9.0*max(strength,0.1));\n" +
        " float curvature=clamp(length(norm)*2.3,0.0,1.0);\n" +
        " float3 backdrop=float3(clear.rgb);\n" +
        " float lum=dot(backdrop,float3(0.2126,0.7152,0.0722));\n" +
        " float2 stepX=float2(5.0,0.0);\n" +
        " float2 stepY=float2(0.0,5.0);\n" +
        " float2 grad=float2(dot(float3(wallpaper.eval(clamp(safeP+stepX,lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)),dot(float3(wallpaper.eval(clamp(safeP+stepY,lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)));\n" +
        " float gradLen=length(grad);\n" +
        " float2 gradDir=grad/(0.02+gradLen);\n" +
        " float2 normDir=norm/(0.02+length(norm));\n" +
        " float angle=clamp(0.5+0.5*dot(gradDir,normDir),0.0,1.0);\n" +
        " float t=clamp(d/max(8.0,min(bounds.z-bounds.x,bounds.w-bounds.y)*0.17),0.0,1.0);\n" +
        " float response=clamp(0.15+0.42*lum+2.5*gradLen,0.0,0.75);\n" +
        " float softness=clamp(reflectionSoftness,0.0,1.0);\n" +
        " float spec=pow(angle,mix(16.0,4.0,softness))*clamp(gradLen*8.0,0.0,1.0);\n" +
        " float enabled=reflectionMode>0.5?1.0:0.0;\n" +
        " float dynamic=reflectionMode>1.5?1.0:0.0;\n" +
        " float gain=clamp(reflectionStrength,0.0,2.0);\n" +
        " float reflect=enabled*gain*response*curvature*(0.045+0.065*(1.0-t));\n" +
        " float glint=dynamic*gain*response*curvature*spec*0.16;\n" +
        " // Separate narrow optical contour from the broad reflection envelope.\n" +
        " float rim=exp(-pow((d-2.8)/max(1.2,2.8+softness*3.0),2.0));\n" +
        " float contour=clamp(edgeStrength,0.0,1.0)*rim*(0.035+0.11*spec);\n" +
        " float shadow=smoothstep(0.0,1.7,d)*(1.0-smoothstep(2.2,7.0,d))*0.028;\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*clamp(reflect+glint+contour,0.0,0.23);\n" +
        " color*=1.0-shadow;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n" ;

    static void draw(View owner, Canvas canvas, RectF rect, float radius,
            BitmapShader wallpaper, BitmapShader blurredWallpaper, RectF valid,
            float strength, float blurMix, int diagnostic, boolean edgeEnabled,
            int edgeStrength, int surfaceMode, int surfaceStrength, int surfaceSoftness,
            int opticalStyle) {
        RuntimeShader shader;
        synchronized (CACHE) {
            shader = CACHE.get(owner);
            if (shader == null) {
                shader = new RuntimeShader(AGSL);
                CACHE.put(owner, shader);
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
        shader.setFloatUniform("style", (float) opticalStyle);
        shader.setFloatUniform("reflectionMode", (float) surfaceMode);
        shader.setFloatUniform("reflectionStrength", Math.max(0f, Math.min(2f, surfaceStrength / 100f)));
        shader.setFloatUniform("reflectionSoftness", Math.max(0f, Math.min(1f, (surfaceSoftness - 50f) / 150f)));
        shader.setFloatUniform("edgeStrength", edgeEnabled ? Math.max(0f, Math.min(1f, edgeStrength / 100f)) : 0f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
