#!/usr/bin/env python3
"""Offline consistency audit for V219. Uses the Java/root source, not a synthetic schema."""
import pathlib
import re
import sys

ROOT=pathlib.Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/top/liquidglass/vector'
CONFIG=(JAVA/'ConfigPublisher202.java').read_text()
HOOK=(JAVA/'HookEntry.java').read_text()
LENS=(JAVA/'GlassLens191.java').read_text()
SCRIPT=(ROOT/'root-helper/99-liquidglass-wallpaper.sh').read_text()
SCRIPT_ASSET=(ROOT/'app/src/main/assets/99-liquidglass-wallpaper.sh').read_text()
UI=(JAVA/'MainActivity.java').read_text()
PROVIDER=(JAVA/'GlassConfigProvider.java').read_text()


def require(value,what):
    if not value:raise AssertionError(what)

def array(name):
    m=re.search(r'private static final (?:String|int)\[\] '+name+r'=\{(.*?)\};',CONFIG,re.S)
    require(m,'Missing ConfigPublisher array: '+name)
    return m.group(1)

keys=re.findall(r'"([a-z][a-z0-9_]+)"',array('KEYS'))
defaults=[int(v) for v in re.findall(r'\b-?\d+\b',array('DEFAULTS'))]
require(len(keys)==len(defaults)==33,'Publisher 33-key / 33-default mismatch')
require(len(set(keys))==len(keys),'Publisher has duplicate names')
require(keys[-1]=='custom_blur_tenths_95','Precision must be added at the end, preserving prior ordinals')
make=re.search(r'n=split\("([^"]+)"\s*,\s*items\s*,\s*" "\)',SCRIPT)
require(make,'Missing root snapshot schema')
root_pairs=[pair.split(':',1) for pair in make.group(1).split()]
require([a for a,b in root_pairs]==keys,'Publisher/root key order mismatch')
require([int(b) for a,b in root_pairs]==defaults,'Publisher/root default mismatch')
require(SCRIPT==SCRIPT_ASSET,'App bundled root helper differs from standalone root helper')
require('"LG1(\\\\|-?[0-9]+){33}"' in CONFIG,'Publisher snapshot regex must be 33 numeric fields')
require('"LG1(\\\\|-?[0-9]+){33}"' in HOOK,'Launcher direct snapshot regex must be 33 numeric fields')
require('if(v.length>=34)folderMicroBlurDp95=clampInt(x[32],0,400)/10f;' in HOOK,'Launcher fractional blur index missing')
require('"custom_blur_tenths_95"' in PROVIDER,'Provider fractional blur field missing')
require('"custom_blur_tenths_95"' in UI,'UI fractional blur field missing')
require('PRODUCTION_AGSL_219' in LENS,'Production lens missing')
require('if(pipeline173==1){\n            drawCleanLens219(view,canvas,rect,radius);' in HOOK,'Clean lens is not the default route')
require('if(edgeOnly && opticalPipeline173(v)==1)return;' in HOOK,'Second edge pass not isolated')
require('"liquidglass_debug_opt_in_219",0)==1' in LENS,'Diagnostic shader is not opt-in')
for key, shaderuniform in {'nonlinear_107':'strength','thickness_107':'thicknessMix','interior_107':'interiorMix',
                           'focus_107':'focusMix','reflection_107':'reflectionMix', 'surface_mode_195':'reflectionMode',
                           'surface_strength_195':'reflectionStrength','surface_softness_195':'reflectionSoftness'}.items():
    require(key in keys and f'uniform float {shaderuniform};' in LENS,'Shader parameter linkage missing: '+key)
    require(f'setFloatUniform("{shaderuniform}"' in LENS,'Shader upload missing: '+key)
print('PASS: 33/33 schema matches publisher+root+asset; decimal blur, default renderer, diagnostics gating, 8 optical uniforms verified')
