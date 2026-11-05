#!/usr/bin/env python3
"""Offline source invariant audit for V221; does not claim a device-side GPU test."""
import pathlib
import re
ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/top/liquidglass/vector'
HOOK=(JAVA/'HookEntry.java').read_text()
LENS=(JAVA/'GlassLens191.java').read_text()
UI=(JAVA/'MainActivity.java').read_text()
GRADLE=(ROOT/'app/build.gradle').read_text()
WORkFLOW=(ROOT/'.github/workflows/build-apk.yml').read_text()
DOC=(ROOT/'系统说明书.txt').read_text()

def ck(test,why):
    if not test:raise AssertionError(why)

ck('versionCode 19205221' in GRADLE and "versionName '1.9.205.221'" in GRADLE,'Build version mismatch')
ck('V1.9.205.221' in UI and 'V1.9.205.221-APK' in WORkFLOW,'UI/build workflow not updated')
ck('V1.9.205.221' in DOC,'System description not versioned')
ck('"icon_optics_204"' in UI and 'iconOptics204=' in HOOK,'Icon toggle chain not preserved')
ck('drawIconInnerOptics221(v,c,mapped' in HOOK,'Icon renderer not installed')
icon=HOOK.split('private static void drawIconInnerOptics221(',1)[1].split('private static void drawIconEdge205(',1)[0]
ck('BlurMaskFilter' not in icon and 'Paint.Style.STROKE' not in icon,'External icon halo/stroke present')
ck('canvas.drawBitmap(field.layer,left,top,p)' in icon,'Inner icon bitmap is offset / halo path still present')
ck('float diagonalLight=' in icon and 'Math.exp(-1.55f*t*t)' in icon,'Quadrant normal gradient or Gaussian inner fade missing')
ck('float alphaShade=' in icon and 'float alphaLight=' in icon,'Four-quadrant lighting not present')
ck('PRODUCTION_AGSL_221' in LENS and 'PRODUCTION_AGSL_219' in LENS,'221/219 production shader baseline missing')
prod=LENS.split('private static final String PRODUCTION_AGSL_221 =',1)[1].split('// Historical shader',1)[0]
ck('depthSmooth221' in prod and 'float sm=' in prod,'Continuous interior distance not used')
ck('cornerBand' in prod and 'raw*=1.0-0.17*corner*cornerBand' in prod,'Corner displacement taper not used')
ck('samplePosition221(p+float2(-e,-e)' in prod and 'samplePosition221(p+float2( e, e)' in prod,'5-point refracted samples not used')
ck('samplePosition221(p+float2(0.0,-0.42)' in prod and 'samplePosition221(p+float2(0.0, 0.42)' in prod,'9-point refracted samples not used')
ck('smoothstep(0.22,0.49,risk)' in prod and 'smoothstep(0.68,0.92,risk)' in prod,'AA transition not continuous')
ck('lowerCenter' in prod and 'contrast=clamp(length(luminanceGradient)' in prod,'AA not targeted to bottom center high contrast')
ck('new RuntimeShader(PRODUCTION_AGSL_221)' in LENS and 'new RuntimeShader(fallbackProduction?PRODUCTION_AGSL_219:AGSL)' in LENS,'219 fallback chain missing')
ck('setFloatUniform("strength"' in LENS,'Strength uniform no longer updated')
ck('if(pipeline173==1){\n            drawCleanLens219(view,canvas,rect,radius);' in HOOK,'Production single-pass routing lost')
ck('if(edgeOnly && opticalPipeline173(v)==1)return;' in HOOK,'Legacy second-pass isolation lost')
print('PASS V221: metadata; icon inner only; four-corner lighting; continuous field; true adaptive 5/9 taps; V219 runtime fallback; clean lens route')
