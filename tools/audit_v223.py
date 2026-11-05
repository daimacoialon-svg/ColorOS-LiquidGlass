#!/usr/bin/env python3
"""Source consistency / release contract audit for V1.9.205.223."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/top/liquidglass/vector'
entry=(java/'HookEntry.java').read_text()
router=(java/'GlassLens191.java').read_text()
lens=(java/'GlassLens222.java').read_text()
ui=(java/'MainActivity.java').read_text()
release=(root/'app/build.gradle').read_text()
manual=(root/'系统说明书.txt').read_text()

def require(p,err):
    if not p:
        raise AssertionError(err)

require('19205223' in release and "versionName '1.9.205.223'" in release, 'version mismatch')
require('V1.9.205.223' in ui and 'V1.9.205.223' in manual, 'version label mismatch')
require('new RuntimeShader(GlassLens222.AGSL)' in router, 'production V222 not selected')
require('new RuntimeShader(fallbackProduction?PRODUCTION_AGSL_219:AGSL)' in router, 'V219 fallback missing')
require('PRODUCTION_222_FAILED' in router, 'V222 shader failure guard missing')
require('shader.setFloatUniform("diagnostic222"' in router, 'new uniform not sent to GPU')
require('liquidglass_debug_opt_in_219' in router and 'liquidglass_lens_diagnostic_222' in router, 'diagnostic opt-in missing')
require('uniform float diagnostic222;' in lens and 'uniform float radius;' in lens, 'lens diagnostics/geometry missing')
require('float2 warpMap222' in lens and 'float2 safeSample222' in lens, 'whole surface mapping missing')
require('min(1.50' not in lens, 'unexpected expression')
require('0.0,1.50' in lens, 'amplitude cap removed')
require('lowerCenter' not in lens and 'cornerBand' not in lens and 'rimRisk' not in lens, 'old localized AA gate survived')
require('setFilterMode(android.graphics.BitmapShader.FILTER_MODE_LINEAR)' in entry, 'linear texture filtering missing')
require('drawIconInnerOptics222(v,c,mapped' in entry, 'V222 icon path not activated')
require('computeIconContour2059(d)' not in entry, 'old polygon silhouette still active')
require('drawIconCornerRim222' in entry and 'PorterDuff.Mode.DST_IN' in entry, 'clipped vector optics missing')
require('setLensDiagnostic222(int requested)' in ui and 'setLensDiagnostic222(0)' in ui,
        'diagnostics not reversible from UI')
require('if(pipeline173==1){\n            drawCleanLens219(view,canvas,rect,radius);' in entry,
        'default one-pass lens route broken')
assert (root/'root-helper/99-liquidglass-wallpaper.sh').read_bytes()==(root/'app/src/main/assets/99-liquidglass-wallpaper.sh').read_bytes()
files=list(root.glob('**/*.txt'))
require(len(files)==1 and files[0].name=='系统说明书.txt','Only one system text file is allowed')
print('PASS: V223 routes, fallback, shader diagnostic, filter, vector icon, one-pass, release version, manual')
