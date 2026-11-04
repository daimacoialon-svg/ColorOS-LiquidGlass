package top.liquidglass.vector;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.view.View;
import java.util.WeakHashMap;

/**
 * V1.9.205.173: independent, deterministic, view-local optical renderer.
 * Reuses the verified bitmap coordinate transform supplied by HookEntry,
 * but does not reuse its historical displacement, motion sheen, or SDF normals.
 */
final class GlassLens173 {
    private GlassLens173() {}
    // Compile the shader once per container view; update child shaders and uniforms
    // every draw. Recompiling AGSL on every horizontal swipe was unnecessary work.
    private static final WeakHashMap<View, RuntimeShader> CACHE173 = new WeakHashMap<>();
    static final String AGSL =
        "uniform shader wallpaper;\n" +
        "uniform shader blurredWallpaper;\n" +
        "uniform float4 bounds;\n" +
        "uniform float4 validBounds;\n" +
        "uniform float radius;\n" +
        "uniform float glassStrength;\n" +
        "uniform float blurMix;\n" +
        "uniform float diagnostic;\n" +
        "uniform float opticalSampling;\n" +
        "uniform float edgeLightStrength;\n" +
        "uniform float edgeLightRawStrength;\n" +
        "uniform float edgeLightEnabled;\n" +
        "uniform float edgeLightWidth;\n" +
        "uniform float directionalEdgeLight;\n" +
        "uniform float reflectionModel178;\n" +
        "uniform float activeCurvature180;\n" +
        "uniform float surfaceMode185;\n" +
        "uniform float environmentStrength185;\n" +
        "uniform float environmentSoftness185;\n" +
        "\n" +
        "// Exact rounded-rectangle silhouette; positive depths point INTO glass.\n" +
        "float depth173(float2 p) {\n" +
        "  float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));\n" +
        "  float r=clamp(radius,0.0,min(halfSize.x,halfSize.y));\n" +
        "  float2 q=abs(p-(bounds.xy+bounds.zw)*0.5)-(halfSize-r);\n" +
        "  return r-length(max(q,float2(0.0)))-min(max(q.x,q.y),0.0);\n" +
        "}\n" +
        "\n" +
        "// Stable view-local optical map; no time, gesture, wallpaper offset, or\n" +
        "// nearest-edge index. The rounded SDF derivative is used ONLY in the rim;\n" +
        "// it vanishes before the medial axis, eliminating diagonal X creases.\n" +
        "float2 delta173(float2 p) {\n" +
        "  float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        "  float minSide=min(size.x,size.y);\n" +
        "  float2 mid=(bounds.xy+bounds.zw)*0.5;\n" +
        "  float2 uv=(p-mid)/max(size*0.5,float2(1.0));\n" +
        "  float depth=max(depth173(p),0.0);\n" +
        "  float band=min(clamp(minSide*0.175,12.0,29.0),max(4.0,radius*0.70));\n" +
        "  float tx=clamp(depth/band,0.0,1.0);\n" +
        "  float edgeSin=sin(3.14159265*tx);\n" +
        "  float edgeShape=edgeSin*edgeSin;\n" +
        "  // A symmetric central difference is safe because only near-surface SDF\n" +
        "  // gradients contribute: the rim envelope goes to exact zero inside.\n" +
        "  float2 gradDepth=float2(\n" +
        "      0.5*(depth173(p+float2(1.0,0.0))-depth173(p-float2(1.0,0.0))),\n" +
        "      0.5*(depth173(p+float2(0.0,1.0))-depth173(p-float2(0.0,1.0))));\n" +
        "  float2 rimDisp=gradDepth*(band*0.210)*edgeShape;\n" +
        "  float2 u2=uv*uv;\n" +
        "  float dome=exp(-0.9*(u2.x+u2.y));\n" +
        "  float interiorRamp=smoothstep(0.0,band*1.15,depth);\n" +
        "  float innerAmp=clamp(minSide*0.060,5.0,16.0);\n" +
        "  float2 innerDisp=-uv*innerAmp*dome*interiorRamp;\n" +
        "  float2 raw=(rimDisp+innerDisp)*clamp(glassStrength,0.0,1.0);\n" +
        "  // Smooth cap: bounded for 100%, retains first derivative at zero.\n" +
        "  float cap=clamp(minSide*0.12,10.0,23.0);\n" +
        "  return raw*(cap/sqrt(cap*cap+dot(raw,raw)));\n" +
        "}\n" +
        "\n" +
        "// V180: curvature applied to the ACTIVE independent renderer, not legacy HookEntry AGSL.\n" +
        "float2 delta180(float2 p) {\n" +
        "  float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        "  float2 mid=(bounds.xy+bounds.zw)*0.5;\n" +
        "  float2 uv=(p-mid)/(size*0.5);\n" +
        "  float d=max(depth173(p),0.0);\n" +
        "  float band=min(clamp(min(size.x,size.y)*0.26,13.0,42.0),max(5.0,radius*0.88));\n" +
        "  float t=clamp(d/band,0.0,1.0);\n" +
        "  float profile=sin(3.14159265*t);\n" +
        "  float2 g=float2(0.5*(depth173(p+float2(1.0,0.0))-depth173(p-float2(1.0,0.0))),0.5*(depth173(p+float2(0.0,1.0))-depth173(p-float2(0.0,1.0))));\n" +
        "  float2 rim=g*band*0.19*profile*profile;\n" +
        "  float inner=smoothstep(0.0,band*1.3,d);\n" +
        "  float2 core=-uv*min(min(size.x,size.y)*0.072,19.0)*exp(-0.68*dot(uv,uv))*inner;\n" +
        "  float gain=activeCurvature180>1.5?1.28:1.0;\n" +
        "  float2 raw=(rim+core)*gain*clamp(glassStrength,0.0,1.0);\n" +
        "  float cap=clamp(min(size.x,size.y)*0.13,11.0,25.0);\n" +
        "  return raw*(cap/sqrt(cap*cap+dot(raw,raw)));\n" +
        "}\n" +
        "// V182: shared meniscus surface: rounded-rect rim + smooth elliptical crown.\n" +
        "// Rim height and its gradient vanish before the rounded SDF medial axis.\n" +
        "float2 delta182(float2 p) {\n" +
        "  float2 size=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        "  float2 mid=(bounds.xy+bounds.zw)*0.5;\n" +
        "  float2 uv=(p-mid)/max(size*0.5,float2(1.0));\n" +
        "  float minSide=min(size.x,size.y);\n" +
        "  float band=min(clamp(minSide*0.235,14.0,37.0),max(5.0,radius*0.82));\n" +
        "  float d=max(depth173(p),0.0);\n" +
        "  float t=clamp(d/band,0.0,1.0);\n" +
        "  float2 gradD=float2(0.5*(depth173(p+float2(1.0,0.0))-depth173(p-float2(1.0,0.0))),0.5*(depth173(p+float2(0.0,1.0))-depth173(p-float2(0.0,1.0))));\n" +
        "  // A raised meniscus: h(t)=t^2(3-2t), dh/dt=6t(1-t).\n" +
        "  float rimSlope=6.0*t*(1.0-t);\n" +
        "  float2 rimNormal=gradD*rimSlope;\n" +
        "  // Shared interior crown is C-infinity, including the central axes.\n" +
        "  float r2=dot(uv,uv);\n" +
        "  float crown=exp(-1.15*r2);\n" +
        "  float2 crownGrad=-2.30*crown*uv/max(size*0.5,float2(1.0));\n" +
        "  float interior=smoothstep(0.0,band,d);\n" +
        "  float interiorSlope=6.0*t*(1.0-t)/band;\n" +
        "  float2 surfaceGrad=rimNormal*0.95 + crownGrad*(minSide*1.45)*interior + gradD*(crown*interiorSlope)*0.24*minSide;\n" +
        "  // One unified surface normal drives x/y refraction; no axis-wise stacking.\n" +
        "  float2 raw=surfaceGrad*min(band*0.23,9.0)*clamp(glassStrength,0.0,1.0);\n" +
        "  float cap=clamp(minSide*0.105,9.0,19.0);\n" +
        "  return raw*(cap/sqrt(cap*cap+dot(raw,raw)));\n" +
        "}\n" +
        "float2 opticalDelta180(float2 p) {\n" +
        "  return activeCurvature180>1.5?delta182(p):activeCurvature180>0.5?delta180(p):delta173(p);\n" +
        "}\n" +
        "float2 sample173(float2 p) {\n" +
        "  float2 lo=validBounds.xy+float2(1.0);\n" +
        "  float2 hi=max(lo+float2(2.0),validBounds.zw-float2(1.0));\n" +
        "  return clamp(p+opticalDelta180(p),lo,hi);\n" +
        "}\n" +
        "\n" +
        "half4 main(float2 p) {\n" +
        "  float2 s=sample173(p);\n" +
        "  float d=max(depth173(p),0.0);\n" +
        "  float2 disp=s-p;\n" +
        "  float strength=clamp(glassStrength,0.0,1.0);\n" +
        "  if(diagnostic>40.5 && diagnostic<41.5){\n" +
        "    float diff=length(delta182(p)-delta180(p));\n" +
        "    float t=clamp(diff/7.0,0.0,1.0);\n" +
        "    return half4(half3(t,0.1,1.0-t),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>41.5 && diagnostic<42.5){\n" +
        "    float2 v=s-p;\n" +
        "    return half4(half3(clamp(0.5+v.x/18.0,0.0,1.0),clamp(0.5+v.y/18.0,0.0,1.0),clamp(length(v)/12.0,0.0,1.0)),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>10.5 && diagnostic<11.5){\n" +
        "    float2 cell=abs(fract(s/23.0)-0.5);\n" +
        "    float line=1.0-smoothstep(0.457,0.492,max(cell.x,cell.y));\n" +
        "    return half4(half3(line),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>15.5 && diagnostic<17.5 || diagnostic>25.5 && diagnostic<26.5){\n" +
        "    float2 jx=0.5*(sample173(p+float2(1.0,0.0))-sample173(p-float2(1.0,0.0)));\n" +
        "    float2 jy=0.5*(sample173(p+float2(0.0,1.0))-sample173(p-float2(0.0,1.0)));\n" +
        "    float det=jx.x*jy.y-jx.y*jy.x;\n" +
        "    if(diagnostic<16.5){\n" +
        "      return half4(half3(clamp(1.0-det,0.0,1.0),clamp(det-1.0,0.0,1.0),det<=0.0?1.0:0.0),1.0);\n" +
        "    }\n" +
        "    if(diagnostic<17.5){\n" +
        "      float shear=length(jx-float2(1.0,0.0))+length(jy-float2(0.0,1.0));\n" +
        "      float value=clamp(shear*0.8,0.0,1.0);\n" +
        "      return half4(half3(value,0.15+0.65*(1.0-value),value*0.28),1.0);\n" +
        "    }\n" +
        "    float danger=1.0-smoothstep(0.20,0.62,det);\n" +
        "    return half4(half3(danger,1.0-danger,clamp((det-1.0)*0.75,0.0,1.0)),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>20.5 && diagnostic<21.5){\n" +
        "    float t=clamp(length(disp)/14.0,0.0,1.0);\n" +
        "    return half4(half3(t,0.1*t,1.0-t),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>21.5 && diagnostic<22.5){\n" +
        "    float removed=clamp(length((p+delta173(p))-s)/max(length(delta173(p)),0.001),0.0,1.0);\n" +
        "    return half4(half3(removed,1.0-removed,0.0),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>22.5 && diagnostic<23.5){\n" +
        "    float2 v=delta173(p);\n" +
        "    return half4(half3(clamp(0.5+v.x/20.0,0.0,1.0),clamp(0.5+v.y/20.0,0.0,1.0),clamp(length(v)/12.0,0.0,1.0)),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>24.5 && diagnostic<25.5){\n" +
        "    float band=min(clamp(min(bounds.z-bounds.x,bounds.w-bounds.y)*0.175,12.0,29.0),max(4.0,radius*0.70));\n" +
        "    float t=clamp(d/band,0.0,1.0);\n" +
        "    return half4(half3(1.0-t,t,0.28+0.3*t),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>23.5 && diagnostic<24.5){\n" +
        "    return wallpaper.eval(p.x<(bounds.x+bounds.z)*0.5?p:s);\n" +
        "  }\n" +
        "  half4 sharp=wallpaper.eval(s);\n" +
        "  if(opticalSampling>0.5){\n" +
        "    float2 lo=validBounds.xy+float2(1.0);\n" +
        "    float2 hi=validBounds.zw-float2(1.0);\n" +
        "    half4 four=wallpaper.eval(clamp(s+float2(-0.38,-0.38),lo,hi));\n" +
        "    four+=wallpaper.eval(clamp(s+float2(0.38,-0.38),lo,hi));\n" +
        "    four+=wallpaper.eval(clamp(s+float2(-0.38,0.38),lo,hi));\n" +
        "    four+=wallpaper.eval(clamp(s+float2(0.38,0.38),lo,hi));\n" +
        "    sharp=four*half(0.25);\n" +
        "    if(opticalSampling>1.5){\n" +
        "      half4 axes=wallpaper.eval(clamp(s+float2(-0.75,0.0),lo,hi));\n" +
        "      axes+=wallpaper.eval(clamp(s+float2(0.75,0.0),lo,hi));\n" +
        "      axes+=wallpaper.eval(clamp(s+float2(0.0,-0.75),lo,hi));\n" +
        "      axes+=wallpaper.eval(clamp(s+float2(0.0,0.75),lo,hi));\n" +
        "      sharp=(four+axes+wallpaper.eval(s))*half(1.0/9.0);\n" +
        "    }\n" +
        "  }\n" +
        "  half4 soft=blurredWallpaper.eval(s);\n" +
        "  if(opticalSampling>0.5){\n" +
        "    float2 lo=validBounds.xy+float2(1.0);\n" +
        "    float2 hi=validBounds.zw-float2(1.0);\n" +
        "    half4 softFour=blurredWallpaper.eval(clamp(s+float2(-0.38,-0.38),lo,hi));\n" +
        "    softFour+=blurredWallpaper.eval(clamp(s+float2(0.38,-0.38),lo,hi));\n" +
        "    softFour+=blurredWallpaper.eval(clamp(s+float2(-0.38,0.38),lo,hi));\n" +
        "    softFour+=blurredWallpaper.eval(clamp(s+float2(0.38,0.38),lo,hi));\n" +
        "    soft=softFour*half(0.25);\n" +
        "    if(opticalSampling>1.5){\n" +
        "      half4 softAxes=blurredWallpaper.eval(clamp(s+float2(-0.75,0.0),lo,hi));\n" +
        "      softAxes+=blurredWallpaper.eval(clamp(s+float2(0.75,0.0),lo,hi));\n" +
        "      softAxes+=blurredWallpaper.eval(clamp(s+float2(0.0,-0.75),lo,hi));\n" +
        "      softAxes+=blurredWallpaper.eval(clamp(s+float2(0.0,0.75),lo,hi));\n" +
        "      soft=(softFour+softAxes+blurredWallpaper.eval(s))*half(1.0/9.0);\n" +
        "    }\n" +
        "  }\n" +
        "  half4 result=mix(sharp,soft,half(clamp(blurMix,0.0,1.0)));\n" +
        "// V185: ambient reflection is computed from backdrop luminance and surface normals.\n" +
        "// The original Fresnel rim remains separately available.\n" +
        "  if(surfaceMode185>0.5 && activeCurvature180>1.5){\n" +
        "    float2 disp185=delta182(p);\n" +
        "    float2 slope185=disp185/max(2.0,9.0*max(glassStrength,0.05));\n" +
        "    float3 N185=normalize(float3(-slope185.x,-slope185.y,1.0));\n" +
        "    float3 L185=normalize(float3(-0.44,-0.54,0.71));\n" +
        "    float3 H185=normalize(L185+float3(0.0,0.0,1.0));\n" +
        "    float nDotV185=clamp(N185.z,0.0,1.0);\n" +
        "    float fresnel185=0.04+0.96*pow(1.0-nDotV185,5.0);\n" +
        "    float gloss185=pow(max(dot(N185,H185),0.0),mix(8.0,36.0,clamp(environmentSoftness185,0.0,1.0)));\n" +
        "    float2 lo185=validBounds.xy+float2(2.0);\n" +
        "    float2 hi185=validBounds.zw-float2(2.0);\n" +
        "    float2 q185=clamp(s,float2(lo185),float2(hi185));\n" +
        "    float3 bg185=float3(wallpaper.eval(q185).rgb);\n" +
        "    float3 bx185=float3(wallpaper.eval(clamp(q185+float2(5.0,0.0),lo185,hi185)).rgb);\n" +
        "    float3 by185=float3(wallpaper.eval(clamp(q185+float2(0.0,5.0),lo185,hi185)).rgb);\n" +
        "    float lum185=dot(bg185,float3(0.2126,0.7152,0.0722));\n" +
        "    float2 envGrad185=float2(dot(bx185-bg185,float3(0.2126,0.7152,0.0722)),dot(by185-bg185,float3(0.2126,0.7152,0.0722)));\n" +
        "    float envResponse185=clamp(0.48+0.36*lum185+dot(envGrad185,N185.xy)*1.8,0.1,1.0);\n" +
        "    float reflection185=environmentStrength185*(0.045+0.12*gloss185+0.19*fresnel185)*envResponse185;\n" +
        "    float2 size185=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        "    float shell185=clamp(min(size185.x,size185.y)*0.13,11.0,36.0);\n" +
        "    float edge185=1.0-smoothstep(0.0,shell185,max(depth173(p),0.0));\n" +
        "    float dynamic185=clamp(0.40+lum185*0.36+dot(envGrad185,normalize(N185.xy+float2(0.001)))*2.0,0.05,1.0);\n" +
        "    float highlight185=environmentStrength185*edge185*pow(max(dot(N185,H185),0.0),mix(4.0,18.0,environmentSoftness185))*dynamic185*0.22;\n" +
        "    float env185=surfaceMode185>1.5?reflection185:reflection185*0.7;\n" +
        "    float rim185=surfaceMode185>1.5?highlight185:0.0;\n" +
        "    float3 lit185=float3(result.rgb);\n" +
        "    lit185+=(1.0-lit185)*clamp(env185+rim185,0.0,0.42);\n" +
        "    result.rgb=half3(clamp(lit185,0.0,1.0));\n" +
        "  }\n" +
        "  // V174: material lighting is separate from lens displacement and blur.\n" +
        "  // Use the actual rounded-rectangle depth, not an elliptical outline.\n" +
        "  float2 size174=max(bounds.zw-bounds.xy,float2(2.0));\n" +
        "  float minSide174=min(size174.x,size174.y);\n" +
        "  float width174=clamp(edgeLightWidth,1.0,5.0);\n" +
        "  float shell174=clamp(minSide174*(0.052+0.011*width174),7.0,27.0);\n" +
        "  float t174=clamp(d/shell174,0.0,1.0);\n" +
        "  float envelope174=(1.0-t174)*(1.0-t174)*(1.0+2.0*t174);\n" +
        "  float2 normal174=float2(\n" +
        "    depth173(p+float2(0.75,0.0))-depth173(p-float2(0.75,0.0)),\n" +
        "    depth173(p+float2(0.0,0.75))-depth173(p-float2(0.0,0.75)));\n" +
        "  if(activeCurvature180>1.5){\n" +
        "    float2 surfaceNormal184=delta182(p);\n" +
        "    float surfaceLen184=length(surfaceNormal184);\n" +
        "    if(surfaceLen184>0.0001){normal174=surfaceNormal184/surfaceLen184;}\n" +
        "  }\n" +
        "  normal174=normal174/max(length(normal174),0.0001);\n" +
        "  float direction174=clamp(directionalEdgeLight,0.0,1.0);\n" +
        "  float directional174=dot(normal174,normalize(float2(-0.62,-0.78)));\n" +
        "  float diffuse174=mix(0.45,0.5+0.5*directional174,direction174);\n" +
        "  float dark174=mix(0.25,0.5-0.5*directional174,direction174);\n" +
        "  float energy174=clamp(edgeLightStrength,0.0,1.0)*envelope174;\n" +
        "  // Light and shade smoothly fade INTO the same glass surface.\n" +
        "  // V175: independent red/blue mask diagnostics before compositing.\n" +
        "  if(diagnostic>26.5 && diagnostic<27.5){\n" +
        "    return half4(half3(energy174*diffuse174,0.0,energy174*dark174),1.0);\n" +
        "  }\n" +
        "  if(diagnostic>27.5 && diagnostic<28.5){\n" +
        "    return half4(half3(envelope174,clamp(edgeLightRawStrength,0.0,1.0),clamp(edgeLightEnabled,0.0,1.0)),1.0);\n" +
        "  }\n" +
        // V176: full-field diagnostic channels; no edge masking or compositing.
        // 29: red=raw slider, green=effective strength, blue=enabled switch.
        "  if(diagnostic>28.5 && diagnostic<29.5){return half4(half3(clamp(edgeLightRawStrength,0.0,1.0),clamp(edgeLightStrength,0.0,1.0),clamp(edgeLightEnabled,0.0,1.0)),1.0); }\n" +
        // 30: red=width normalized 1..5, green=shell pixels normalized 0..27.
        "  if(diagnostic>29.5 && diagnostic<30.5){return half4(half3(clamp((edgeLightWidth-1.0)/4.0,0.0,1.0),clamp(shell174/27.0,0.0,1.0),0.0),1.0); }\n" +
        // V178: optical surface response is an anisotropic Fresnel-like rim, not a uniform outline.
        // Preserve the old V177 material path behind a reversible global setting.
        "  if(reflectionModel178>0.5){\n" +
        "    float2 lightDir178=normalize(float2(-0.72,-0.69));\n" +
        "    float incidence178=dot(normal174,lightDir178);\n" +
        "    float bright178=pow(max(incidence178,0.0),3.0);\n" +
        "    float dark178=pow(max(-incidence178,0.0),3.0);\n" +
        "    float shoulder178=pow(max(1.0-t174,0.0),1.3+1.2*(1.0-(width174-1.0)/4.0))*(0.5+0.5*smoothstep(0.0,0.18,t174));\n" +
        "    float reflect178=clamp(edgeLightStrength,0.0,1.0)*shoulder178;\n" +
        "    float directional178=clamp(directionalEdgeLight,0.0,1.0);\n" +
        "    float glint178=reflect178*(0.025+0.23*bright178)*(0.70+0.30*directional178);\n" +
        "    float shade178=reflect178*(0.008+0.085*dark178);\n" +

        "    half3 lit178=result.rgb+half3(glint178)*(half3(1.0)-result.rgb);\n" +
        "    result.rgb=clamp(lit178*(half(1.0)-half(shade178)),half3(0.0),half3(1.0));\n" +
        "    return result;\n" +
        "  }\n" +
        "  float lift175=energy174*(0.21*diffuse174+0.022);\n" +
        "  float shade175=energy174*(0.18*dark174+0.018);\n" +
        "  if(diagnostic>30.5 && diagnostic<31.5){return half4(half3(clamp(lift175*4.0,0.0,1.0),clamp(edgeLightStrength,0.0,1.0),clamp(shade175*4.0,0.0,1.0)),1.0); }\n" +
        "  // Blend independent signed light and shadow without mutual cancellation.\n" +
        "  half3 lit175=result.rgb+half3(lift175)*(half3(1.0)-result.rgb);\n" +
        "  result.rgb=clamp(lit175*(half(1.0)-half(shade175)),half3(0.0),half3(1.0));\n" +
        "  return result;\n" +
        "}\n";

    static void draw(View owner, Canvas canvas, RectF rect, float radius,
                     BitmapShader wallpaper, BitmapShader blurredWallpaper,
                     RectF validBounds, float strength, float blurMix, int diagnostic, int samplingMode,
                     boolean edgeEnabled, int edgeStrength, int edgeWidth, boolean directional, int reflectionModel178, int activeCurvature180, int surfaceMode185, int surfaceStrength185, int surfaceSoftness185) {
        RuntimeShader shader;
        synchronized (CACHE173) {
            shader = CACHE173.get(owner);
            if (shader == null) {
                shader = new RuntimeShader(AGSL);
                CACHE173.put(owner, shader);
            }
        }
        shader.setInputShader("wallpaper", wallpaper);
        shader.setInputShader("blurredWallpaper", blurredWallpaper);
        shader.setFloatUniform("bounds", rect.left, rect.top, rect.right, rect.bottom);
        shader.setFloatUniform("validBounds", validBounds.left, validBounds.top,
                validBounds.right, validBounds.bottom);
        shader.setFloatUniform("radius", Math.max(0f, radius));
        shader.setFloatUniform("glassStrength", Math.max(0f, Math.min(1f, strength)));
        shader.setFloatUniform("blurMix", Math.max(0f, Math.min(1f, blurMix)));
        shader.setFloatUniform("diagnostic", (float) diagnostic);
        shader.setFloatUniform("opticalSampling", (float) Math.max(0, Math.min(2, samplingMode)));
        shader.setFloatUniform("edgeLightStrength", edgeEnabled ? Math.max(0f, Math.min(1f, edgeStrength / 100f)) : 0f);
        shader.setFloatUniform("edgeLightRawStrength", Math.max(0f, Math.min(1f, edgeStrength / 100f)));
        shader.setFloatUniform("edgeLightEnabled", edgeEnabled ? 1f : 0f);
        shader.setFloatUniform("edgeLightWidth", (float) Math.max(1, Math.min(5, edgeWidth)));
        shader.setFloatUniform("directionalEdgeLight", directional ? 1f : 0f);
        shader.setFloatUniform("reflectionModel178", reflectionModel178 > 0 ? 1f : 0f);
        shader.setFloatUniform("activeCurvature180", (float)Math.max(0,Math.min(2,activeCurvature180)));
        shader.setFloatUniform("surfaceMode185", (float)Math.max(0,Math.min(3,surfaceMode185)));
        shader.setFloatUniform("environmentStrength185", Math.max(0f,Math.min(2f,surfaceStrength185/100f)));
        shader.setFloatUniform("environmentSoftness185", Math.max(0f,Math.min(1f,(surfaceSoftness185-50f)/150f)));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
