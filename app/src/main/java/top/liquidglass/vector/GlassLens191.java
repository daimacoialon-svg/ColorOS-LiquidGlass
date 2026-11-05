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
    private static final WeakHashMap<View, RuntimeShader> LEGACY_DEBUG_CACHE_219 = new WeakHashMap<>();
    // V217: never compile experimental grid code in the production lens.
    private static final WeakHashMap<View, RuntimeShader> GRID_CACHE_217 = new WeakHashMap<>();
    // V219 production GPU shader: single material, no historical diagnostic branches.
    private static final String PRODUCTION_AGSL_219 =
        "uniform shader wallpaper;\n" +
        "uniform shader blurredWallpaper;\n" +
        "uniform float4 bounds;\n" +
        "uniform float4 validBounds;\n" +
        "uniform float radius;\n" +
        "uniform float strength;\n" +
        "uniform float thicknessMix;\n" +
        "uniform float interiorMix;\n" +
        "uniform float focusMix;\n" +
        "uniform float reflectionMix;\n" +
        "uniform float blurMix;\n" +
        "uniform float style;\n" +
        "uniform float reflectionMode;\n" +
        "uniform float reflectionStrength;\n" +
        "uniform float reflectionSoftness;\n" +
        "uniform float edgeStrength;\n" +
        "float depth219(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "float radial219(float2 u){\n" +
        " float2 a=abs(u);\n" +
        " float roundTerm=dot(u,u);\n" +
        " float box=max(a.x,a.y);\n" +
        " float blend=smoothstep(0.44,0.96,max(a.x,a.y)+0.22*min(a.x,a.y));\n" +
        " return mix(roundTerm,box*box,blend*0.42);\n" +
        "}\n" +
        "float height219(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 u=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float d=max(depth219(p),0.0);\n" +
        " float width=clamp(min(h.x,h.y)*(0.68+0.44*clamp(thicknessMix,0.0,1.0)),22.0,108.0);\n" +
        " float x=clamp(d/width,0.0,1.0);\n" +
        " float shoulder=x*x*(3.0-2.0*x);\n" +
        " float t=clamp(thicknessMix,0.0,1.0);\n" +
        " float core=1.0-(0.05+0.34*clamp(interiorMix,0.0,1.0))*radial219(u);\n" +
        " return ((0.46+0.20*t)+(0.54-0.20*t)*core)*shoulder;\n" +
        "}\n" +
        "float2 slope219(float2 p){\n" +
        " float e=1.0;\n" +
        " float hx=(height219(p+float2(e,0.0))+height219(p+float2(e,e))+height219(p+float2(e,-e))\n" +
        "  -height219(p-float2(e,0.0))-height219(p-float2(e,e))-height219(p-float2(e,-e)))/(6.0*e);\n" +
        " float hy=(height219(p+float2(0.0,e))+height219(p+float2(e,e))+height219(p-float2(e,e))\n" +
        "  -height219(p-float2(0.0,e))-height219(p+float2(e,-e))-height219(p-float2(e,-e)))/(6.0*e);\n" +
        " return float2(hx,hy);\n" +
        "}\n" +
        "float2 warp219(float2 p){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 u=(p-(bounds.xy+bounds.zw)*0.5)/(size*0.5);\n" +
        " float small=min(size.x,size.y);\n" +
        " float2 slope=slope219(p);\n" +
        " float2 focus=-u*(small*0.086)*(0.42+clamp(focusMix,0.0,1.0))\n" +
        "  *exp(-0.66*radial219(u))*smoothstep(0.0,clamp(small*0.18,14.0,36.0),max(depth219(p),0.0));\n" +
        " float gain=style>1.5?1.08:0.98;\n" +
        " float2 raw=(slope*small*0.47+focus)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(small*0.096,9.0,21.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "half4 main(float2 p){\n" +
        " float2 lo=validBounds.xy+float2(2.0);\n" +
        " float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));\n" +
        " float2 q=p+warp219(p);\n" +
        " float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));\n" +
        " float safety=smoothstep(0.0,14.0,margin);\n" +
        " float2 src=clamp(mix(p,q,safety),lo,hi);\n" +
        " half4 clear=wallpaper.eval(src);\n" +
        " half4 softened=blurredWallpaper.eval(src);\n" +
        " half4 result=mix(clear,softened,half(clamp(blurMix,0.0,1.0)));\n" +
        " float2 slope=slope219(p);\n" +
        " float curvature=clamp(length(slope)*22.0,0.0,1.0);\n" +
        " float2 sampledx=float2(4.0,0.0),sampledy=float2(0.0,4.0);\n" +
        " float3 backdrop=float3(clear.rgb);\n" +
        " float2 luminanceGradient=float2(\n" +
        "  dot(float3(wallpaper.eval(clamp(src+sampledx,lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)),\n" +
        "  dot(float3(wallpaper.eval(clamp(src+sampledy,lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)));\n" +
        " float3 normal=normalize(float3(-slope*4.0,1.0));\n" +
        " float3 light=normalize(float3(-0.55,-0.75,0.75));\n" +
        " float facing=clamp(dot(normal,light),0.0,1.0);\n" +
        " float direction=clamp(dot(luminanceGradient/(0.02+length(luminanceGradient)),\n" +
        "  slope/(0.02+length(slope))),0.0,1.0);\n" +
        " float spec=pow(direction,mix(15.0,4.0,clamp(reflectionSoftness,0.0,1.0)))\n" +
        "  *clamp(length(luminanceGradient)*9.0,0.0,1.0);\n" +
        " float enabled=reflectionMode>0.5?1.0:0.0;\n" +
        " float dynamic=reflectionMode>1.5?1.0:0.0;\n" +
        " float gain=clamp(reflectionStrength,0.0,2.0)*(0.65+0.70*clamp(reflectionMix,0.0,1.0));\n" +
        " float ambient=clamp(dot(backdrop,float3(0.2126,0.7152,0.0722)),0.0,1.0);\n" +
        " float reflect=enabled*gain*curvature*(0.014+0.078*pow(facing,5.0))*(0.55+0.45*ambient);\n" +
        " float glint=dynamic*gain*curvature*spec*0.14;\n" +
        " float d=max(depth219(p),0.0);\n" +
        " float rim=exp(-pow((d-0.80)/0.92,2.0));\n" +
        " float meniscus=exp(-pow((d-4.6)/4.8,2.0));\n" +
        " float fresnel=pow(1.0-clamp(1.0/sqrt(1.0+dot(slope*4.2,slope*4.2)),0.0,1.0),1.25);\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 cornerUV=abs((p-(bounds.xy+bounds.zw)*0.5)/h);\n" +
        " float cornerFade=smoothstep(0.58,0.98,max(cornerUV.x,cornerUV.y))*smoothstep(0.10,0.82,min(cornerUV.x,cornerUV.y));\n" +
        " float outline=clamp(edgeStrength,0.0,1.0)*(0.10*rim+0.085*meniscus*fresnel)\n" +
        "  *(0.25+0.75*pow(facing,3.0))*(1.0-0.46*cornerFade);\n" +
        " float shadow=(1.0-smoothstep(0.8,5.6,d))*smoothstep(0.0,0.9,d)*0.030*(1.0-0.55*cornerFade);\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*clamp(reflect+glint+outline,0.0,0.28);\n" +
        " color*=1.0-shadow;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n";
    // V221 production optical field. V219 remains the tested fallback.
    private static final String PRODUCTION_AGSL_221 =
        "uniform shader wallpaper;\n" +
        "uniform shader blurredWallpaper;\n" +
        "uniform float4 bounds;\n" +
        "uniform float4 validBounds;\n" +
        "uniform float radius;\n" +
        "uniform float strength;\n" +
        "uniform float thicknessMix;\n" +
        "uniform float interiorMix;\n" +
        "uniform float focusMix;\n" +
        "uniform float reflectionMix;\n" +
        "uniform float blurMix;\n" +
        "uniform float style;\n" +
        "uniform float reflectionMode;\n" +
        "uniform float reflectionStrength;\n" +
        "uniform float reflectionSoftness;\n" +
        "uniform float edgeStrength;\n" +
        "float depth219(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "float radial219(float2 u){\n" +
        " float2 a=abs(u);\n" +
        " float roundTerm=dot(u,u);\n" +
        " float box=max(a.x,a.y);\n" +
        " float blend=smoothstep(0.44,0.96,max(a.x,a.y)+0.22*min(a.x,a.y));\n" +
        " return mix(roundTerm,box*box,blend*0.42);\n" +
        "}\n" +
        "float depthSmooth221(float2 p){\n" +
        " float exact=depth219(p);\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " float k=clamp(min(h.x,h.y)*0.08,3.5,11.0);\n" +
        " float sm=0.5*(q.x+q.y+sqrt((q.x-q.y)*(q.x-q.y)+k*k));\n" +
        " float softened=r-length(max(q,float2(0.0)))-min(sm,0.0);\n" +
        " float blend=smoothstep(2.0,max(8.0,k*1.3),max(exact,0.0));\n" +
        " return mix(exact,softened,blend);\n" +
        "}\n" +
        "float height219(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 u=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float d=max(depthSmooth221(p),0.0);\n" +
        " float width=clamp(min(h.x,h.y)*(0.68+0.44*clamp(thicknessMix,0.0,1.0)),22.0,108.0);\n" +
        " float x=clamp(d/width,0.0,1.0);\n" +
        " float shoulder=x*x*(3.0-2.0*x);\n" +
        " float t=clamp(thicknessMix,0.0,1.0);\n" +
        " float core=1.0-(0.05+0.34*clamp(interiorMix,0.0,1.0))*radial219(u);\n" +
        " return ((0.46+0.20*t)+(0.54-0.20*t)*core)*shoulder;\n" +
        "}\n" +
        "float2 slope219(float2 p){\n" +
        " float e=1.0;\n" +
        " float hx=(height219(p+float2(e,0.0))+height219(p+float2(e,e))+height219(p+float2(e,-e))\n" +
        "  -height219(p-float2(e,0.0))-height219(p-float2(e,e))-height219(p-float2(e,-e)))/(6.0*e);\n" +
        " float hy=(height219(p+float2(0.0,e))+height219(p+float2(e,e))+height219(p-float2(e,e))\n" +
        "  -height219(p-float2(0.0,e))-height219(p+float2(e,-e))-height219(p-float2(e,-e)))/(6.0*e);\n" +
        " return float2(hx,hy);\n" +
        "}\n" +
        "float2 warp219(float2 p){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 u=(p-(bounds.xy+bounds.zw)*0.5)/(size*0.5);\n" +
        " float small=min(size.x,size.y);\n" +
        " float2 slope=slope219(p);\n" +
        " float2 focus=-u*(small*0.086)*(0.42+clamp(focusMix,0.0,1.0))\n" +
        "  *exp(-0.66*radial219(u))*smoothstep(0.0,clamp(small*0.18,14.0,36.0),max(depth219(p),0.0));\n" +
        " float gain=style>1.5?1.08:0.98;\n" +
        " float2 raw=(slope*small*0.47+focus)*gain*clamp(strength,0.0,1.0);\n" +
        " float2 cornerUV=abs(u);\n" +
        " float corner=smoothstep(0.58,0.92,cornerUV.x)*smoothstep(0.58,0.92,cornerUV.y);\n" +
        " float cornerBand=1.0-smoothstep(4.0,27.0,max(depth219(p),0.0));\n" +
        " raw*=1.0-0.17*corner*cornerBand;\n" +
        " float cap=clamp(small*0.096,9.0,21.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "float2 samplePosition221(float2 p,float2 lo,float2 hi){\n" +
        " float2 q=p+warp219(p);\n" +
        " float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));\n" +
        " float safety=smoothstep(0.0,14.0,margin);\n" +
        " return clamp(mix(p,q,safety),lo,hi);\n" +
        "}\n" +
        "half4 main(float2 p){\n" +
        " float2 lo=validBounds.xy+float2(2.0);\n" +
        " float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));\n" +
        " float2 src=samplePosition221(p,lo,hi);\n" +
        " half4 clear=wallpaper.eval(src);\n" +
        " half4 softened=blurredWallpaper.eval(src);\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 signedUV=(p-(bounds.xy+bounds.zw)*0.5)/(size*0.5);\n" +
        " float2 uv=abs(signedUV);\n" +
        " float dRisk=max(depth219(p),0.0);\n" +
        " float small=min(size.x,size.y);\n" +
        " float band=smoothstep(1.2,8.0,dRisk)*(1.0-smoothstep(small*0.16,small*0.33,dRisk));\n" +
        " float corner=smoothstep(0.59,0.90,uv.x)*smoothstep(0.59,0.90,uv.y);\n" +
        " float rimRisk=band*corner;\n" +
        " float lowerCenter=smoothstep(0.36,0.57,signedUV.y)*(1.0-smoothstep(0.40,0.66,uv.x));\n" +
        " float3 backdrop=float3(clear.rgb);\n" +
        " float2 luminanceGradient=float2(\n" +
        "  dot(float3(wallpaper.eval(clamp(src+float2(4.0,0.0),lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)),\n" +
        "  dot(float3(wallpaper.eval(clamp(src+float2(0.0,4.0),lo,hi)).rgb)-backdrop,float3(0.2126,0.7152,0.0722)));\n" +
        " float contrast=clamp(length(luminanceGradient)*8.0,0.0,1.0);\n" +
        " float risk=max(rimRisk,lowerCenter*(0.40+0.60*contrast))\n" +
        "  *smoothstep(0.30,0.75,clamp(strength,0.0,1.0));\n" +
        " if(risk>0.22){\n" +
        "  float e=0.34;\n" +
        "  float2 s1=samplePosition221(p+float2(-e,-e),lo,hi);\n" +
        "  float2 s2=samplePosition221(p+float2( e,-e),lo,hi);\n" +
        "  float2 s3=samplePosition221(p+float2(-e, e),lo,hi);\n" +
        "  float2 s4=samplePosition221(p+float2( e, e),lo,hi);\n" +
        "  half4 csum=clear+wallpaper.eval(s1)+wallpaper.eval(s2)+wallpaper.eval(s3)+wallpaper.eval(s4);\n" +
        "  half4 bsum=softened+blurredWallpaper.eval(s1)+blurredWallpaper.eval(s2)+blurredWallpaper.eval(s3)+blurredWallpaper.eval(s4);\n" +
        "  float w5=smoothstep(0.22,0.49,risk);\n" +
        "  half4 c5=csum*half(0.20);half4 b5=bsum*half(0.20);\n" +
        "  clear=mix(clear,c5,half(w5));softened=mix(softened,b5,half(w5));\n" +
        "  if(risk>0.68){\n" +
        "   float2 s5=samplePosition221(p+float2(-0.42,0.0),lo,hi);\n" +
        "   float2 s6=samplePosition221(p+float2( 0.42,0.0),lo,hi);\n" +
        "   float2 s7=samplePosition221(p+float2(0.0,-0.42),lo,hi);\n" +
        "   float2 s8=samplePosition221(p+float2(0.0, 0.42),lo,hi);\n" +
        "   half4 c9=(csum+wallpaper.eval(s5)+wallpaper.eval(s6)+wallpaper.eval(s7)+wallpaper.eval(s8))*half(1.0/9.0);\n" +
        "   half4 b9=(bsum+blurredWallpaper.eval(s5)+blurredWallpaper.eval(s6)+blurredWallpaper.eval(s7)+blurredWallpaper.eval(s8))*half(1.0/9.0);\n" +
        "   float w9=smoothstep(0.68,0.92,risk);\n" +
        "   clear=mix(clear,c9,half(w9));softened=mix(softened,b9,half(w9));\n" +
        "  }\n" +
        " }\n" +
        " half4 result=mix(clear,softened,half(clamp(blurMix,0.0,1.0)));\n" +
        " float2 slope=slope219(p);\n" +
        " float curvature=clamp(length(slope)*22.0,0.0,1.0);\n" +
        " float3 normal=normalize(float3(-slope*4.0,1.0));\n" +
        " float3 light=normalize(float3(-0.55,-0.75,0.75));\n" +
        " float facing=clamp(dot(normal,light),0.0,1.0);\n" +
        " float direction=clamp(dot(luminanceGradient/(0.02+length(luminanceGradient)),\n" +
        "  slope/(0.02+length(slope))),0.0,1.0);\n" +
        " float spec=pow(direction,mix(15.0,4.0,clamp(reflectionSoftness,0.0,1.0)))\n" +
        "  *clamp(length(luminanceGradient)*9.0,0.0,1.0);\n" +
        " float enabled=reflectionMode>0.5?1.0:0.0;\n" +
        " float dynamic=reflectionMode>1.5?1.0:0.0;\n" +
        " float gain=clamp(reflectionStrength,0.0,2.0)*(0.65+0.70*clamp(reflectionMix,0.0,1.0));\n" +
        " float ambient=clamp(dot(backdrop,float3(0.2126,0.7152,0.0722)),0.0,1.0);\n" +
        " float reflect=enabled*gain*curvature*(0.014+0.078*pow(facing,5.0))*(0.55+0.45*ambient);\n" +
        " float glint=dynamic*gain*curvature*spec*0.14;\n" +
        " float d=max(depth219(p),0.0);\n" +
        " float rim=exp(-pow((d-0.80)/0.92,2.0));\n" +
        " float meniscus=exp(-pow((d-4.6)/4.8,2.0));\n" +
        " float fresnel=pow(1.0-clamp(1.0/sqrt(1.0+dot(slope*4.2,slope*4.2)),0.0,1.0),1.25);\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 cornerUV=abs((p-(bounds.xy+bounds.zw)*0.5)/h);\n" +
        " float cornerFade=smoothstep(0.58,0.98,max(cornerUV.x,cornerUV.y))*smoothstep(0.10,0.82,min(cornerUV.x,cornerUV.y));\n" +
        " float outline=clamp(edgeStrength,0.0,1.0)*(0.10*rim+0.085*meniscus*fresnel)\n" +
        "  *(0.25+0.75*pow(facing,3.0))*(1.0-0.46*cornerFade);\n" +
        " float shadow=(1.0-smoothstep(0.8,5.6,d))*smoothstep(0.0,0.9,d)*0.030*(1.0-0.55*cornerFade);\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*clamp(reflect+glint+outline,0.0,0.28);\n" +
        " color*=1.0-shadow;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n";

    // Historical shader is deliberately reachable only through explicit debug opt-in.
    static final String AGSL =
        "uniform shader wallpaper;\n" +
        "uniform shader blurredWallpaper;\n" +
        "uniform float4 bounds;\n" +
        "uniform float4 validBounds;\n" +
        "uniform float radius;\n" +
        "uniform float strength;\n" +
        "uniform float thicknessMix;\n" +
        "uniform float interiorMix;\n" +
        "uniform float focusMix;\n" +
        "uniform float reflectionMix;\n" +
        "uniform float blurMix;\n" +
        "uniform float style;\n" +
        "uniform float reflectionMode;\n" +
        "uniform float reflectionStrength;\n" +
        "uniform float reflectionSoftness;\n" +
        "uniform float edgeStrength;\n" +
        "uniform float diagnostic;\n" +
        "uniform float aaProbe208;\n" +
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
        " float width=clamp(min(h.x,h.y)*(0.56+0.37*clamp(thicknessMix,0.0,1.0)),18.0,85.0);\n" +
        " float x=clamp(d/width,0.0,1.0);\n" +
        " float shoulder=x*x*(3.0-2.0*x);\n" +
        " float core=(1.0-(0.06+0.38*clamp(interiorMix,0.0,1.0))*radial);\n" +
        " float t=clamp(thicknessMix,0.0,1.0);\n" +
        " return (0.48+0.22*t)*shoulder+(0.52-0.22*t)*shoulder*core;\n" +
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
        " float2 core=-uv*(small*0.105)*(0.45+clamp(focusMix,0.0,1.0))*exp(-0.72*dot(uv,uv))*smoothstep(0.0,clamp(small*0.16,12.0,32.0),max(depth(p),0.0));\n" +
        " float gain=style>1.5?1.12:1.0;\n" +
        " float2 raw=(slope*small*0.56+core)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(small*0.115,10.0,24.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "// V213 experiment: smooth the rounded-box distance's axis-to-corner transition.\n" +
        "// This is an alternative height field, NOT a claim that the original SDF was discontinuous.\n" +
        "float depth213(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,0.0,min(h.x,h.y));\n" +
        " float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(h-r);\n" +
        " float k=clamp(r*0.16,1.0,5.0);\n" +
        " float2 z=0.5*(q+sqrt(q*q+float2(k*k)));\n" +
        " float m=0.5*(q.x+q.y+sqrt((q.x-q.y)*(q.x-q.y)+k*k));\n" +
        " float inside=0.5*(m-sqrt(m*m+k*k));\n" +
        " return r-length(z)-inside;\n" +
        "}\n" +
        "float height213(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float d=max(depth213(p),0.0);\n" +
        " float width=clamp(min(h.x,h.y)*0.76,18.0,75.0);\n" +
        " float x=clamp(d/width,0.0,1.0);\n" +
        " float shoulder=x*x*(3.0-2.0*x);\n" +
        " return shoulder*(1.0-0.1035*dot(uv,uv));\n" +
        "}\n" +
        "float2 normal213(float2 p){\n" +
        " float e=1.25;\n" +
        " return float2(height213(p+float2(e,0.0))-height213(p-float2(e,0.0)),height213(p+float2(0.0,e))-height213(p-float2(0.0,e)))/(2.0*e);\n" +
        "}\n" +
        "float2 offset213(float2 p){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 h=size*0.5;\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float small=min(size.x,size.y);\n" +
        " float2 core=-uv*(small*0.105)*exp(-0.72*dot(uv,uv))*smoothstep(0.0,clamp(small*0.16,12.0,32.0),max(depth(p),0.0));\n" +
        " float gain=style>1.5?1.12:1.0;\n" +
        " float2 raw=(normal213(p)*small*0.56+core)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(small*0.115,10.0,24.0);\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "// V214: continuous corner direction field; experimental, never used by default.\n" +
        "float2 normal214(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,1.0,min(h.x,h.y));\n" +
        " float2 a=abs(p-(bounds.xy+bounds.zw)*0.5);\n" +
        " float2 start=max(h-float2(r*1.9),float2(0.0));\n" +
        " float2 gate=smoothstep(start,start+float2(max(3.0,r*0.95)),a);\n" +
        " float corner=gate.x*gate.y;\n" +
        " float2 fine=normal213(p);\n" +
        " float e=max(1.8,min(3.2,r*0.12));\n" +
        " float2 broad=float2(height213(p+float2(e,0.0))-height213(p-float2(e,0.0)),height213(p+float2(0.0,e))-height213(p-float2(0.0,e)))/(2.0*e);\n" +
        " float2 direction=mix(fine,broad,corner*0.80);\n" +
        " float mag=length(fine);\n" +
        " return direction*(mag/max(length(direction),0.00001));\n" +
        "}\n" +
        "float2 offset214(float2 p,float mode){\n" +
        " float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        " float2 h=size*0.5;\n" +
        " float2 uv=(p-(bounds.xy+bounds.zw)*0.5)/h;\n" +
        " float small=min(size.x,size.y);\n" +
        " float2 core=-uv*(small*0.105)*exp(-0.72*dot(uv,uv))*smoothstep(0.0,clamp(small*0.16,12.0,32.0),max(depth(p),0.0));\n" +
        " float gain=style>1.5?1.12:1.0;\n" +
        " float2 raw=(normal214(p)*small*0.56+core)*gain*clamp(strength,0.0,1.0);\n" +
        " float cap=clamp(small*0.115,10.0,24.0);\n" +
        " if(mode>0.5){\n" +
        "  float2 prior=(normal213(p)*small*0.56+core)*gain*clamp(strength,0.0,1.0);\n" +
        "  float oldMag=length(prior)*cap/sqrt(cap*cap+dot(prior,prior));\n" +
        "  float2 blended=raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "  return blended*(oldMag/max(length(blended),0.00001));\n" +
        " }\n" +
        " return raw*cap/sqrt(cap*cap+dot(raw,raw));\n" +
        "}\n" +
        "// V215 experimental mapping: corner-local direction continuity with original magnitude.\n" +
        "float2 offset215(float2 p){\n" +
        " float2 base=offset214(p,1.0);\n" +
        " float w=cornerMask211(p);\n" +
        " if(w<0.002)return base;\n" +
        " float e=2.0;\n" +
        " float2 avg=(offset214(p+float2(e,0.0),1.0)+offset214(p-float2(e,0.0),1.0)+offset214(p+float2(0.0,e),1.0)+offset214(p-float2(0.0,e),1.0))*0.25;\n" +
        " float2 direction=mix(base,avg,w*0.65);\n" +
        " return direction*(length(base)/max(length(direction),0.00001));\n" +
        "}\n" +
        "// Corner-only diagnostic: smooth the sampling displacement field, not the glass brightness.\n" +
        "float cornerMask211(float2 p){\n" +
        " float2 h=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        " float r=clamp(radius,1.0,min(h.x,h.y));\n" +
        " float2 a=abs(p-(bounds.xy+bounds.zw)*0.5);\n" +
        " float2 cornerStart=max(h-float2(r*1.35),float2(0.0));\n" +
        " float2 w=smoothstep(cornerStart,cornerStart+float2(max(3.0,r*0.45)),a);\n" +
        " float d=max(depth(p),0.0);\n" +
        " return w.x*w.y*(1.0-smoothstep(max(3.0,r*0.65),max(6.0,r*1.5),d));\n" +
        "}\n" +
        "float2 cornerOffset211(float2 p){\n" +
        " float2 base=offsetAt(p);\n" +
        " float mask=cornerMask211(p);\n" +
        " if(mask<0.002)return base;\n" +
        " float e=1.15;\n" +
        " float2 smoothOffset=(offsetAt(p+float2(e,0.0))+offsetAt(p-float2(e,0.0))+offsetAt(p+float2(0.0,e))+offsetAt(p-float2(0.0,e)))*0.25;\n" +
        " return mix(base,smoothOffset,mask*0.85);\n" +
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
        " // Adaptive finite-difference footprint; no unsupported AGSL derivatives.\n" +
        " // Pre-gate by meniscus shoulder to avoid expensive Jacobian tests in the center.\n" +
        " float shoulderWidth=clamp(min(bounds.z-bounds.x,bounds.w-bounds.y)*0.38,18.0,75.0);\n" +
        " float shoulderAA=smoothstep(2.0,8.0,d)*(1.0-smoothstep(shoulderWidth*0.65,shoulderWidth*1.05,d));\n" +
        " float risk=0.0;\n" +
        " float footprint=1.0;\n" +
        " if((shoulderAA>0.005 && strength>0.01) || aaProbe208>0.5){\n" +
        "  float2 px=p+float2(1.0,0.0),py=p+float2(0.0,1.0);\n" +
        "  float2 qx=px+offsetAt(px),qy=py+offsetAt(py);\n" +
        "  float2 jx=mix(float2(1.0,0.0),qx-q,safety);\n" +
        "  float2 jy=mix(float2(0.0,1.0),qy-q,safety);\n" +
        "  footprint=max(length(jx),length(jy));\n" +
        "  float anisotropy=abs(dot(jx,jy))/(0.01+length(jx)*length(jy));\n" +
        "  risk=shoulderAA*max(smoothstep(1.10,2.25,footprint),smoothstep(0.20,0.85,anisotropy)*0.6);\n" +
        "  if(risk>0.12){\n" +
        "   float2 ax=jx*0.29,ay=jy*0.29;\n" +
        "   half4 c4=(wallpaper.eval(clamp(sampleAt+ax+ay,lo,hi))+wallpaper.eval(clamp(sampleAt+ax-ay,lo,hi))+wallpaper.eval(clamp(sampleAt-ax+ay,lo,hi))+wallpaper.eval(clamp(sampleAt-ax-ay,lo,hi)))*0.25;\n" +
        "   half4 b4=(blurredWallpaper.eval(clamp(sampleAt+ax+ay,lo,hi))+blurredWallpaper.eval(clamp(sampleAt+ax-ay,lo,hi))+blurredWallpaper.eval(clamp(sampleAt-ax+ay,lo,hi))+blurredWallpaper.eval(clamp(sampleAt-ax-ay,lo,hi)))*0.25;\n" +
        "   clear=mix(clear,c4,half(smoothstep(0.12,0.42,risk)));\n" +
        "   blurred=mix(blurred,b4,half(smoothstep(0.12,0.42,risk)));\n" +
        "   if(risk>0.68){\n" +
        "    half4 c9=wallpaper.eval(clamp(sampleAt+ax,lo,hi))+wallpaper.eval(clamp(sampleAt-ax,lo,hi))+wallpaper.eval(clamp(sampleAt+ay,lo,hi))+wallpaper.eval(clamp(sampleAt-ay,lo,hi));\n" +
        "    half4 b9=blurredWallpaper.eval(clamp(sampleAt+ax,lo,hi))+blurredWallpaper.eval(clamp(sampleAt-ax,lo,hi))+blurredWallpaper.eval(clamp(sampleAt+ay,lo,hi))+blurredWallpaper.eval(clamp(sampleAt-ay,lo,hi));\n" +
        "    half w9=half(smoothstep(0.68,0.92,risk));\n" +
        "    clear=mix(clear,(c4*4.0+c9+wallpaper.eval(sampleAt))/9.0,w9);\n" +
        "    blurred=mix(blurred,(b4*4.0+b9+blurredWallpaper.eval(sampleAt))/9.0,w9);\n" +
        "   }\n" +
        "  }\n" +
        " }\n" +
                " // V208 diagnostic 1: show actual V207 sampling gates (blue=1, yellow=4, red=9).\n" +
        " if(aaProbe208>0.5 && aaProbe208<1.5){\n" +
        "  if(risk>0.68) return half4(1.0,0.12,0.12,1.0);\n" +
        "  if(risk>0.12) return half4(1.0,0.83,0.08,1.0);\n" +
        "  return half4(0.05,0.25,1.0,1.0);\n" +
        " }\n" +
        " // V208 diagnostic 2: real nine-point 3x3 subpixel supersampling.\n" +
        " // Re-evaluate offsetAt for each subpixel, not a linearized Jacobian.\n" +
        " if(aaProbe208>1.5 && aaProbe208<2.5){\n" +
        "  half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "  for(int ix=-1;ix<=1;ix++){\n" +
        "   for(int iy=-1;iy<=1;iy++){\n" +
        "    float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "    float2 sq=sp+offsetAt(sp);\n" +
        "    float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "    float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "    sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "   }\n" +
        "  }\n" +
        "  clear=sumC/9.0;blurred=sumB/9.0;\n" +
        " }\n" +
        " // V208 diagnostic 3: map local sampling footprint and rapid distortion.\n" +
        " if(aaProbe208>2.5 && aaProbe208<3.5){\n" +
        "  float v=smoothstep(1.0,2.5,footprint);\n" +
        "  return half4(half3(v,1.0-v,0.12),1.0);\n" +
        " }\n" +
        " // V210 probe 4: full 3x3 real subpixel sampling in the entire meniscus shoulder.\n" +
        " // Outside the shoulder retain original single-sample result; no persistent quality change.\n" +
        " if(aaProbe208>3.5 && aaProbe208<4.5){\n" +
        "  float band=smoothstep(0.5,3.0,d)*(1.0-smoothstep(shoulderWidth*0.9,shoulderWidth*1.3,d));\n" +
        "  if(band>0.001){\n" +
        "   half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "   for(int ix=-1;ix<=1;ix++){\n" +
        "    for(int iy=-1;iy<=1;iy++){\n" +
        "     float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "     float2 sq=sp+offsetAt(sp);\n" +
        "     float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "     float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "     sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "    }\n" +
        "   }\n" +
        "   clear=mix(clear,sumC/9.0,half(band));\n" +
        "   blurred=mix(blurred,sumB/9.0,half(band));\n" +
        "  }\n" +
        " }\n" +
        " // V213 12: alternative unified smooth-distance normal, single-sample control.\n" +
        " if(aaProbe208>11.5 && aaProbe208<12.5){\n" +
        "  float2 sq=p+offset213(p);\n" +
        "  float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "  float2 sc=clamp(mix(p,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "  clear=wallpaper.eval(sc);blurred=blurredWallpaper.eval(sc);\n" +
        " }\n" +
        " // V213 13: same alternative normal with real 3x3 subpixel evaluation.\n" +
        " if(aaProbe208>12.5 && aaProbe208<13.5){\n" +
        "  half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "  for(int ix=-1;ix<=1;ix++){\n" +
        "   for(int iy=-1;iy<=1;iy++){\n" +
        "    float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "    float2 sq=sp+offset213(sp);\n" +
        "    float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "    float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "    sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "   }\n" +
        "  }\n" +
        "  clear=sumC/9.0;blurred=sumB/9.0;\n" +
        " }\n" +
        " // V214 modes 15/16: direction-continuous normal, true 3x3 sampling.\n" +
        " if(aaProbe208>14.5 && aaProbe208<16.5){\n" +
        "  half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "  float mode=aaProbe208>15.5?1.0:0.0;\n" +
        "  for(int ix=-1;ix<=1;ix++){\n" +
        "   for(int iy=-1;iy<=1;iy++){\n" +
        "    float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "    float2 sq=sp+offset214(sp,mode);\n" +
        "    float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "    float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "    sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "   }\n" +
        "  }\n" +
        "  clear=sumC/9.0;blurred=sumB/9.0;\n" +
        " }\n" +
        " // V215 mode 18: warped coordinate grid, sampled through mode 16 mapping.\n" +
        " if(aaProbe208>17.5 && aaProbe208<18.5){\n" +
        "  float2 sq=p+offset214(p,1.0);\n" +
        "  float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "  float2 sc=clamp(mix(p,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "  float2 grid=abs(fract(sc/16.0)-0.5);\n" +
        "  float line=1.0-smoothstep(0.018,0.065,min(grid.x,grid.y));\n" +
        "  return half4(half3(0.08+line*0.82,0.18+line*0.78,0.24+line*0.68),1.0);\n" +
        " }\n" +
        " // V215 mode 19: mapping Jacobian compression (red) / stretch (green).\n" +
        " if(aaProbe208>18.5 && aaProbe208<19.5){\n" +
        "  float e=1.0;\n" +
        "  float2 x=(offset214(p+float2(e,0.0),1.0)-offset214(p-float2(e,0.0),1.0))/(2.0*e)+float2(1.0,0.0);\n" +
        "  float2 y=(offset214(p+float2(0.0,e),1.0)-offset214(p-float2(0.0,e),1.0))/(2.0*e)+float2(0.0,1.0);\n" +
        "  float det=x.x*y.y-x.y*y.x;\n" +
        "  float compression=1.0-smoothstep(0.2,1.0,det);\n" +
        "  float stretch=smoothstep(1.0,2.4,max(length(x),length(y)));\n" +
        "  return half4(half3(compression,stretch,0.18),1.0);\n" +
        " }\n" +
        " // V215 mode 20: local mapping continuity, preserving mode 16 displacement length and 3x3 sampling.\n" +
        " if(aaProbe208>19.5 && aaProbe208<20.5){\n" +
        "  half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "  for(int ix=-1;ix<=1;ix++){\n" +
        "   for(int iy=-1;iy<=1;iy++){\n" +
        "    float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "    float2 sq=sp+offset215(sp);\n" +
        "    float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "    float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "    sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "   }\n" +
        "  }\n" +
        "  clear=sumC/9.0;blurred=sumB/9.0;\n" +
        " }\n" +
        " // V214 mode 17: final offset difference, blue=small, red=large.\n" +
        " if(aaProbe208>16.5 && aaProbe208<17.5){\n" +
        "  float diff=length(offset214(p,1.0)-offset213(p));\n" +
        "  float t=smoothstep(0.02,1.5,diff);\n" +
        "  return half4(half3(t,0.25*(1.0-t),1.0-t),1.0);\n" +
        " }\n" +
        " // V213 14: difference between original and alternative normals.\n" +
        " if(aaProbe208>13.5 && aaProbe208<14.5){\n" +
        "  float diff=length(normal213(p)-normalSlope(p));\n" +
        "  float t=smoothstep(0.0005,0.035,diff);\n" +
        "  return half4(half3(t,0.22*(1.0-t),1.0-t),1.0);\n" +
        " }\n" +
        " // V212 probe 8: signed-distance gradient orientation (R=x, G=y, B=gradient magnitude).\n" +
        " // Finite differences are used for diagnostic only; no dFdx/dFdy.\n" +
        " if(aaProbe208>7.5 && aaProbe208<8.5){\n" +
        "  float e=1.0;\n" +
        "  float2 g=float2(depth(p+float2(e,0.0))-depth(p-float2(e,0.0)),depth(p+float2(0.0,e))-depth(p-float2(0.0,e)))/(2.0*e);\n" +
        "  return half4(half3(0.5+0.5*g.x,0.5+0.5*g.y,clamp(length(g),0.0,1.0)),1.0);\n" +
        " }\n" +
        " // V212 probe 9: height-field normal slope orientation and magnitude.\n" +
        " if(aaProbe208>8.5 && aaProbe208<9.5){\n" +
        "  float2 g=normalSlope(p);\n" +
        "  float m=length(g);\n" +
        "  float2 unitG=g/max(m,0.0001);\n" +
        "  return half4(half3(0.5+0.5*unitG.x,0.5+0.5*unitG.y,smoothstep(0.0,0.06,m)),1.0);\n" +
        " }\n" +
        " // V212 probe 10: local variation of normalSlope, highlighting kinks in the height field.\n" +
        " if(aaProbe208>9.5 && aaProbe208<10.5){\n" +
        "  float2 nx=normalSlope(p+float2(1.0,0.0))-normalSlope(p-float2(1.0,0.0));\n" +
        "  float2 ny=normalSlope(p+float2(0.0,1.0))-normalSlope(p-float2(0.0,1.0));\n" +
        "  float k=max(length(nx),length(ny));\n" +
        "  float t=smoothstep(0.004,0.08,k);\n" +
        "  return half4(half3(t,0.25*(1.0-t),1.0-t),1.0);\n" +
        " }\n" +
        " // V212 probe 11: displacement gradient with symmetric differences.\n" +
        " if(aaProbe208>10.5 && aaProbe208<11.5){\n" +
        "  float2 gx=offsetAt(p+float2(1.0,0.0))-offsetAt(p-float2(1.0,0.0));\n" +
        "  float2 gy=offsetAt(p+float2(0.0,1.0))-offsetAt(p-float2(0.0,1.0));\n" +
        "  float t=smoothstep(0.1,2.0,max(length(gx),length(gy)));\n" +
        "  return half4(half3(t,1.0-abs(2.0*t-1.0),1.0-t),1.0);\n" +
        " }\n" +
        " // V211 probe 6: corner-mask heatmap, white=corner correction domain.\n" +
        " if(aaProbe208>5.5 && aaProbe208<6.5){\n" +
        "  float m=cornerMask211(p);\n" +
        "  return half4(half3(m,0.12+0.55*m,1.0-m),1.0);\n" +
        " }\n" +
        " // V211 probe 7: real 3x3 supersampling with corner-local displacement smoothing.\n" +
        " // Deliberately experimental: original offsetAt() remains untouched in normal modes.\n" +
        " if(aaProbe208>6.5 && aaProbe208<7.5){\n" +
        "  half4 sumC=half4(0.0),sumB=half4(0.0);\n" +
        "  for(int ix=-1;ix<=1;ix++){\n" +
        "   for(int iy=-1;iy<=1;iy++){\n" +
        "    float2 sp=p+float2(float(ix),float(iy))*(1.0/3.0);\n" +
        "    float2 sq=sp+cornerOffset211(sp);\n" +
        "    float sm=min(min(sq.x-lo.x,hi.x-sq.x),min(sq.y-lo.y,hi.y-sq.y));\n" +
        "    float2 sc=clamp(mix(sp,sq,smoothstep(0.0,14.0,sm)),lo,hi);\n" +
        "    sumC+=wallpaper.eval(sc);sumB+=blurredWallpaper.eval(sc);\n" +
        "   }\n" +
        "  }\n" +
        "  clear=sumC/9.0;blurred=sumB/9.0;\n" +
        " }\n" +
        " // V210 probe 5: visualize actual displacement-gradient severity.\n" +
        " if(aaProbe208>4.5 && aaProbe208<5.5){\n" +
        "  float2 sx=p+float2(1.0,0.0),sy=p+float2(0.0,1.0);\n" +
        "  float2 dx=offsetAt(sx)-delta,dy=offsetAt(sy)-delta;\n" +
        "  float risk210=max(length(dx),length(dy));\n" +
        "  float t=smoothstep(0.15,1.5,risk210);\n" +
        "  return half4(half3(t,1.0-abs(t-0.5)*2.0,1.0-t),1.0);\n" +
        " }\n" +
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
        " float gain=clamp(reflectionStrength,0.0,2.0)*(0.65+0.70*clamp(reflectionMix,0.0,1.0));\n" +
        " // Directional reflection follows the height-field slope and local wallpaper luminance.\n" +
        " float3 lightDir=normalize(float3(-0.55,-0.75,0.75));\n" +
        " float3 n=normalize(float3(-slope*4.0,1.0));\n" +
        " float facing=clamp(dot(n,lightDir),0.0,1.0);\n" +
        " float ambient=clamp(dot(backdrop,float3(0.2126,0.7152,0.0722)),0.0,1.0);\n" +
        " float reflect=enabled*gain*curvature*(0.014+0.078*pow(facing,5.0))*(0.55+0.45*ambient);\n" +
        " float glint=dynamic*gain*curvature*spec*0.14;\n" +
        " // Thin optical silhouette plus a softer, curvature-dependent internal meniscus.\n" +
        " float rim=exp(-pow((d-0.85)/0.85,2.0));\n" +
        " float meniscus=exp(-pow((d-4.2)/3.8,2.0));\n" +
        " float fresnel=pow(1.0-clamp(1.0/sqrt(1.0+dot(slope*4.8,slope*4.8)),0.0,1.0),1.35);\n" +
        " float direction=0.25+0.75*pow(facing,3.0);\n" +
        " float contour=clamp(edgeStrength,0.0,1.0)*(0.12*rim*direction+0.11*meniscus*fresnel*direction);\n" +
        " float shadow=(1.0-smoothstep(0.7,5.0,d))*smoothstep(0.0,0.8,d)*0.042;\n" +
        " float3 color=float3(result.rgb);\n" +
        " color+=(1.0-color)*clamp(reflect+glint+contour,0.0,0.28);\n" +
        " color*=1.0-shadow;\n" +
        " result.rgb=half3(clamp(color,0.0,1.0));\n" +
        " return result;\n" +
        "}\n";
    // The original V215 shader above is the verified normal-mode baseline.
    // Only mode 18 compiles this isolated white-grid variant; a grid failure cannot
    // disable normal 2D refraction, mode 16 or mode 20.
    private static final String GRID_OLD_217 =
        "  float2 grid=abs(fract(sc/16.0)-0.5);\n" +
        "  float line=1.0-smoothstep(0.018,0.065,min(grid.x,grid.y));\n" +
        "  return half4(half3(0.08+line*0.82,0.18+line*0.78,0.24+line*0.68),1.0);\n";
    private static final String GRID_NEW_217 =
        "  float spacing=clamp(edgeStrength*16.0,8.0,16.0);\n" +
        "  float2 grid=abs(fract(sc/spacing)-0.5)*spacing;\n" +
        "  float dist=min(grid.x,grid.y);\n" +
        "  float minorInk=1.0-smoothstep(0.4,1.1,dist);\n" +
        "  float2 majorGrid=abs(fract(sc/(spacing*5.0))-0.5)*(spacing*5.0);\n" +
        "  float majorInk=1.0-smoothstep(0.8,1.8,min(majorGrid.x,majorGrid.y));\n" +
        "  float ink=max(minorInk*0.78,majorInk);\n" +
        "  return half4(half3(1.0-ink),1.0);\n";
    private static final String WHITE_GRID_AGSL_217=AGSL.replace(GRID_OLD_217,GRID_NEW_217);
    private static boolean GRID_FAILED_217=false;
    private static boolean PRODUCTION_221_FAILED=false;
    private static boolean PRODUCTION_221_FALLBACK_REPORTED=false;
    private static int ROUTE_LOGS_218=0;

    static void draw(View owner, Canvas canvas, RectF rect, float radius,
            BitmapShader wallpaper, BitmapShader blurredWallpaper, RectF valid,
            float strength, float blurMix, int diagnostic, boolean edgeEnabled,
            int edgeStrength, int surfaceMode, int surfaceStrength, int surfaceSoftness,
            int opticalStyle,int thickness,int interior,int focus,int reflection) {
        int aaProbe208=0;
        try {
            if(android.provider.Settings.Global.getInt(owner.getContext().getContentResolver(),
                "liquidglass_debug_opt_in_219",0)==1)
                aaProbe208=android.provider.Settings.Global.getInt(owner.getContext().getContentResolver(),
                    "liquidglass_aa_probe_208",0);
        } catch(Throwable ignored) {}
        boolean gridRequested=aaProbe208==18;
        if(ROUTE_LOGS_218<12){
            ROUTE_LOGS_218++;
            android.util.Log.i("LiquidGlass219","LENS_SHADER_ENTRY mode="+aaProbe208+
                " strength="+strength+" style="+opticalStyle+" bounds="+rect.toShortString());
        }
        RuntimeShader shader=null;
        boolean usingGrid=false;
        // Retired diagnostics cannot enter the normal production shader.
        final boolean production219=aaProbe208==0;
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
                        android.util.Log.e("LiquidGlass219","GRID_SHADER_FAILED; falling back to compact lens",t);
                    }
                }
            }
        }
        usingGrid=gridRequested && shader!=null;
        if(shader==null){
            // If diagnostic grid compilation fails, stay with working production optics.
            final boolean fallbackProduction=production219 || gridRequested;
            WeakHashMap<View,RuntimeShader> cache=fallbackProduction?CACHE:LEGACY_DEBUG_CACHE_219;
            synchronized(cache){
                shader=cache.get(owner);
                if(shader==null){
                    try{
                        if(fallbackProduction && !PRODUCTION_221_FAILED){
                            try{
                                shader=new RuntimeShader(PRODUCTION_AGSL_221);
                                android.util.Log.i("LiquidGlass221","LENS_SHADER_CREATED production=221 adaptiveSSAA=true");
                            }catch(Throwable fail221){
                                PRODUCTION_221_FAILED=true;
                                android.util.Log.e("LiquidGlass221","LENS_SHADER_221_FAILED fallback=219",fail221);
                            }
                        }
                        if(shader==null){
                            shader=new RuntimeShader(fallbackProduction?PRODUCTION_AGSL_219:AGSL);
                            if(fallbackProduction && !PRODUCTION_221_FALLBACK_REPORTED){
                                PRODUCTION_221_FALLBACK_REPORTED=true;
                                android.util.Log.w("LiquidGlass221","LENS_SHADER_CREATED production=219 fallback=true");
                            }
                        }
                        cache.put(owner,shader);
                    }catch(Throwable fail){
                        android.util.Log.e("LiquidGlass221","LENS_SHADER_FAILED production="+fallbackProduction,fail);
                        throw fail;
                    }
                }
            }
        }
        final boolean debugShader=!production219 && !gridRequested || usingGrid;
        shader.setInputShader("wallpaper", wallpaper);
        shader.setInputShader("blurredWallpaper", blurredWallpaper);
        shader.setFloatUniform("bounds", rect.left, rect.top, rect.right, rect.bottom);
        shader.setFloatUniform("validBounds", valid.left, valid.top, valid.right, valid.bottom);
        shader.setFloatUniform("radius", Math.max(0f, radius));
        shader.setFloatUniform("strength", Math.max(0f, Math.min(1f, strength)));
        shader.setFloatUniform("thicknessMix",Math.max(0f,Math.min(1f,thickness/100f)));
        shader.setFloatUniform("interiorMix",Math.max(0f,Math.min(1f,interior/100f)));
        shader.setFloatUniform("focusMix",Math.max(0f,Math.min(1f,focus/100f)));
        shader.setFloatUniform("reflectionMix",Math.max(0f,Math.min(1f,reflection/100f)));
        shader.setFloatUniform("blurMix", Math.max(0f, Math.min(1f, blurMix)));
        if(debugShader){
            shader.setFloatUniform("diagnostic", (float) diagnostic);
            shader.setFloatUniform("aaProbe208", (float)Math.max(0,Math.min(20,aaProbe208)));
        }
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
