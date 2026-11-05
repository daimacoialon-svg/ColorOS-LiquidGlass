package top.liquidglass.vector;

/**
 * V222: whole-surface, orientation-independent monotone lens map.
 *
 * p -> center + (p-center)*(1+a*(1-n)^2),  n = ||(p-center)/halfSize||_8.
 * For n in [0,1], tangential scale = 1+a*(1-n)^2 and radial scale
 * = 1+a*(1-n)*(1-3*n). At a<=1.5, both stay > 0.5 for the entire domain.
 * This replaces V221's corner / lower-center-specific sampling branches.
 *
 * No expensive multipoint sampling on the production path. Debug mode 4
 * separately tests full-domain 9-point sampling to isolate sampling artifacts.
 */
final class GlassLens222 {
    private GlassLens222() {}
    static final String AGSL = """
        uniform shader wallpaper;
        uniform shader blurredWallpaper;
        uniform float4 bounds;
        uniform float4 validBounds;
        uniform float radius;
        uniform float strength;
        uniform float thicknessMix;
        uniform float interiorMix;
        uniform float focusMix;
        uniform float reflectionMix;
        uniform float blurMix;
        uniform float style;
        uniform float reflectionMode;
        uniform float reflectionStrength;
        uniform float reflectionSoftness;
        uniform float edgeStrength;
        uniform float diagnostic222;

        float2 uv222(float2 p) {
          float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));
          return (p-(bounds.xy+bounds.zw)*0.5)/halfSize;
        }
        float norm222(float2 uv) {
          float2 u2=uv*uv;
          float2 u4=u2*u2;
          float n8=pow(u4.x*u4.x+u4.y*u4.y+0.0000000001,0.125);
          float n4=pow(u4.x+u4.y+0.0000000001,0.25);
          float2 hs=max((bounds.zw-bounds.xy)*0.5,float2(1.0));
          float cornerWeight=0.33*clamp(radius/max(1.0,min(hs.x,hs.y)),0.0,1.0);
          return mix(n8,n4,cornerWeight);
        }
        float amplitude222() {
          float a=clamp(strength,0.0,1.0)*
            (0.78+0.28*clamp(thicknessMix,0.0,1.0)+
             0.23*clamp(focusMix,0.0,1.0)+
             0.12*clamp(interiorMix,0.0,1.0));
          return clamp(a*(style>1.5?1.04:1.0),0.0,1.50);
        }
        float2 warpMap222(float2 p) {
          float2 center=(bounds.xy+bounds.zw)*0.5;
          float n=norm222(uv222(p));
          float t=clamp(1.0-n,0.0,1.0);
          float a=amplitude222();
          // The mapping matches the identity with zero first derivative at n=1.
          return center+(p-center)*(1.0+a*t*t);
        }
        float2 safeSample222(float2 p,float2 lo,float2 hi) {
          float2 q=warpMap222(p);
          float margin=min(min(q.x-lo.x,hi.x-q.x),min(q.y-lo.y,hi.y-q.y));
          return clamp(mix(p,q,smoothstep(0.0,10.0,margin)),lo,hi);
        }
        float2 slope222(float2 p) {
          float2 u=uv222(p);
          float2 u2=u*u;
          float2 u4=u2*u2;
          float n8=pow(u4.x*u4.x+u4.y*u4.y+0.0000000001,0.125);
          float n4=pow(u4.x+u4.y+0.0000000001,0.25);
          float2 halfSize=max((bounds.zw-bounds.xy)*0.5,float2(1.0));
          float cornerWeight=0.33*clamp(radius/max(1.0,min(halfSize.x,halfSize.y)),0.0,1.0);
          float n=mix(n8,n4,cornerWeight);
          float t=clamp(1.0-n,0.0,1.0);
          float2 dn8=(u4*u2*u)/max(pow(n8,7.0),0.0000001);
          float2 dn4=(u2*u)/max(pow(n4,3.0),0.0000001);
          float2 dn=mix(dn8,dn4,cornerWeight);
          return -2.0*t*dn/halfSize*(0.50+0.40*clamp(thicknessMix,0.0,1.0));
        }
        half4 main(float2 p) {
          float2 lo=validBounds.xy+float2(2.0);
          float2 hi=max(lo+float2(1.0),validBounds.zw-float2(2.0));
          float2 src=safeSample222(p,lo,hi);
          float n=norm222(uv222(p));
          float t=clamp(1.0-n,0.0,1.0);
          float a=amplitude222();
          float tangentScale=1.0+a*t*t;
          float radialScale=1.0+a*t*(1.0-3.0*n);
          float detJ=tangentScale*radialScale;

          // Debug modes affect no persistent settings; 0 is always production.
          if(diagnostic222>0.5 && diagnostic222<1.5) {
            float2 g=abs(fract(src/18.0)-0.5);
            float ink=1.0-smoothstep(0.465,0.49,max(g.x,g.y));
            return half4(half3(0.13+0.80*ink,0.20+0.68*ink,0.23+0.58*ink),1.0);
          }
          if(diagnostic222>1.5 && diagnostic222<2.5) {
            float compression=1.0-smoothstep(0.50,1.05,radialScale);
            float stretch=smoothstep(1.05,1.90,tangentScale);
            float fold=1.0-smoothstep(0.00,0.35,detJ);
            return half4(half3(clamp(compression+fold,0.0,1.0),
              clamp(1.0-compression-stretch-fold,0.0,1.0),stretch),1.0);
          }
          if(diagnostic222>2.5 && diagnostic222<3.5) {
            // Reference half / curved half reveals texture filtering artifacts.
            src=p.x<0.5*(bounds.x+bounds.z)?clamp(p,lo,hi):src;
          }

          half4 clear=wallpaper.eval(src);
          half4 blurred=blurredWallpaper.eval(src);
          if(diagnostic222>3.5 && diagnostic222<4.5) {
            half4 csum=half4(0.0);
            half4 bsum=half4(0.0);
            for(int ix=-1;ix<=1;ix++) {
              for(int iy=-1;iy<=1;iy++) {
                float2 at=p+float2(float(ix),float(iy))*0.36;
                float2 q=safeSample222(at,lo,hi);
                csum+=wallpaper.eval(q);
                bsum+=blurredWallpaper.eval(q);
              }
            }
            clear=csum/9.0;
            blurred=bsum/9.0;
          }
          half4 result=mix(clear,blurred,half(clamp(blurMix,0.0,1.0)));
          float2 slope=slope222(p);
          float2 unit=slope/(0.008+length(slope));
          float2 srcDx=float2(3.0,0.0),srcDy=float2(0.0,3.0);
          float3 reference=float3(clear.rgb);
          float2 grad=float2(
            dot(float3(wallpaper.eval(clamp(src+srcDx,lo,hi)).rgb)-reference,
                float3(0.2126,0.7152,0.0722)),
            dot(float3(wallpaper.eval(clamp(src+srcDy,lo,hi)).rgb)-reference,
                float3(0.2126,0.7152,0.0722)));
          float alignment=clamp(dot(grad/(0.012+length(grad)),unit),0.0,1.0);
          float spec=pow(alignment,mix(13.0,4.5,clamp(reflectionSoftness,0.0,1.0)))*
            smoothstep(0.01,0.22,length(grad));
          float3 normal=normalize(float3(-slope*5.0,1.0));
          float3 light=normalize(float3(-0.55,-0.75,0.77));
          float facing=clamp(dot(normal,light),0.0,1.0);
          float curvature=clamp(length(slope)*85.0,0.0,1.0);
          float shell=exp(-pow((t-0.07)/0.075,2.0));
          float meniscus=exp(-pow((t-0.18)/0.12,2.0));
          float enabled=reflectionMode>0.5?1.0:0.0;
          float dynamic=reflectionMode>1.5?1.0:0.0;
          float gain=clamp(reflectionStrength,0.0,2.0)*
            (0.65+0.70*clamp(reflectionMix,0.0,1.0));
          float luminosity=clamp(dot(reference,float3(0.2126,0.7152,0.0722)),0.0,1.0);
          float reflect=enabled*gain*curvature*(0.024+0.078*pow(facing,4.0))*(0.60+0.4*luminosity);
          float glint=dynamic*gain*curvature*spec*0.11;
          float outline=clamp(edgeStrength,0.0,1.0)*
            (0.075*shell+0.055*meniscus*curvature)*(0.25+0.75*pow(facing,3.0));
          float3 color=float3(result.rgb);
          color+=(1.0-color)*clamp(reflect+glint+outline,0.0,0.24);
          result.rgb=half3(clamp(color,0.0,1.0));
          return result;
        }
        """;
}
