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
        "float depth(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "// A single continuous height field drives transmission and lighting.\n" +
        "// A broad low-curvature interior is joined smoothly to a meniscus near the rim.\n" +
        "float heightAt(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float radial=dot(uv,uv);\n" +
        " float d=max(depth(p),0.0);\n" +
        " float width=clamp(min(h.x,h.y)*0.76,18.0,75.0);\n" +
        " float x=clamp(d/width,0.0,1.0);\n" +
        " float shoulder=x*x*(3.0-2.0*x);\n" +
        " float core=(1.0-0.23*radial);\n" +
        " return (0.55*shoulder+0.45*shoulder*core);\n" +
        "}\n" +
        "float2 normalSlope(float2 p){\n" +
        " float e=1.25;\n" +
        " return float2(heightAt(p+float2(e,0.0))-heightAt(p-float2(e,0.0)),heightAt(p+float2(0.0,e))-heightAt(p-float2(0.0,e)))/(2.0*e);\n" +
        "}\n" +
        "float2 offsetAt(float2 p){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 h=size*0.5;\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float small=min(size.x,size.y);\n" +
        " float2 slope=normalSlope(p);\n" +
        " float2 core=-uv*(small*0.105)*exp(-0.72*dot(uv,uv))*smoothstep(0.0,clamp(small*0.16,12.0,32.0),max(depth(p),0.0));\n" +
        " float gain=style>1.5?1.12:1.0;\n" +
        " float2 raw=(slope*small*0.56+core)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(small*0.115,10.0,24.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "half4 main(float2 p){\n" +
        " float d=max(depth(p),0.0);\n" +
        " float2 delta=offsetAt(p);\n" +
        " float2 lo=validBounds.xy+float2(2.0);\n" +
        " float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));\n" +
        " float2 q=p+delta;\n" +
        " float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));\n" +
        " float safety=smoothstep(0.0,14.0,margin);\n" +
        " float2 sampleAt=mix(p,q,safety);\n" +
        " half4 clear=wallpaper.eval(sampleAt);\n" +
        " half4 blurred=blurredWallpaper.eval(sampleAt);\n" +
        " half4 result=mix(clear,blurred,half(clamp(blurMix,0.0,1.0)));\n" +
        " if(diagnostic>10.5 && diagnostic<11.5){\n" +
        "  float2 f=abs(fract((sampleAt-validBounds.xy)/16.0)-0.5);\n" +
        "  float line=1.0-smoothstep(0.45,0.49,max(f.x,f.y));\n" +
        "  return half4(half3(1.0-line),1.0);\n" +
        " }\n" +
        " if(diagnostic>15.5 && diagnostic<16.5){\n" +
        "  float v=clamp(length(delta)/9.0,0.0,1.0);\n" +
        "  return half4(half3(v,1.0-v,0.0),1.0);\n" +
        " }\n" +
        " if(diagnostic>16.5 && diagnostic<17.5){\n" +
        "  float v=clamp(length(delta)/9.0,0.0,1.0);\n" +
        "  return half4(half3(0.0,v,1.0-v),1.0);\n" +
        " }\n" +
        " // Reflection is driven by the same normal slope as the lens. No center crown/glow.\n" +
        " float2 slope=normalSlope(p);\n" +
        " float curvature=clamp(length(slope)*22.0,0.0,1.0);\n" +
        " float3 backdrop=float3(clear.rgb);\n" +
        " float2 grad=float2(\n" +
        "  dot(float3(wallpaper.eval(sampleAt+float2(5.0,0.0)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)),\n" +
        "  dot(float3(wallpaper.eval(sampleAt+float2(0.0,5.0)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)));\n" +
        " float gradLen=length(grad);\n" +
        " float alignment=clamp(dot(grad/(0.02+gradLen),slope/(0.02+length(slope))),0.0,1.0);\n" +
        " float soft=clamp(reflectionSoftness,0.0,1.0);\n" +
        " float spec=pow(alignment,mix(15.0,4.0,soft))*clamp(gradLen*9.0,0.0,1.0);\n" +
        " float enabled=reflectionMode>0.5?1.0:0.0;\n" +
        " float dynamic=reflectionMode>1.5?1.0:0.0;\n" +
        " float gain=clamp(reflectionStrength,0.0,2.0);\n" +
        " float reflect=enabled*gain*curvature*(0.028+0.075*clamp(gradLen*5.0,0.0,1.0));\n" +
        " float glint=dynamic*gain*curvature*spec*0.18;\n" +
        " // Optical boundary is a subpixel-to-1px line; thickness is in the lens slope,\n" +
        " // not in a wide constant-width white outline.\n" +
        " float rim=exp(-pow((d-1.35)/1.5,2.0));\n" +
        " float fresnel=pow(1.0-clamp(1.0/sqrt(1.0+dot(slope*3.5,slope*3.5)),0.0,1.0),1.5);\n" +
        " float contour=clamp(edgeStrength,0.0,1.0)*(0.09*rim+0.075*fresnel*smoothstep(0.0,7.0,d));\n" +
        " float shadow=(1.0-smoothstep(1.0,6.0,d))*smoothstep(0.0,1.1,d)*0.055;\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*clamp(reflect+glint+contour,0.0,0.28);\n" +
        " color*=1.0-shadow;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n";
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
