#!/usr/bin/env python3
"""Independent V230 source-wiring regression test (no Android runtime required)."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[1]
J=R/'app/src/main/java/top/liquidglass/vector'
def src(name): return (J/name).read_text(encoding='utf-8')
M=src('MaterialConfig230.java'); S=src('UnifiedGlass230.java')
C=src('ConfigPublisher202.java'); H=src('HookEntry.java')
L=src('GlassLens191.java'); U=src('MainActivity.java')
P=src('GlassConfigProvider.java')
old_keys=re.search(r'private static final String\[\] KEYS=\{(.*?)\};',C,re.S).group(1)
old_defaults=re.search(r'private static final int\[\] DEFAULTS=\{(.*?)\};',C,re.S).group(1)
assert len(re.findall(r'"[a-z_0-9]+"',old_keys))==33
assert len(re.findall(r'\b\d+\b',old_defaults))==33
assert 'LG1(\\\\|-?[0-9]+){33}' in C
assert 'LG1(\\\\|-?[0-9]+){33}' in H
assert 'liquidglass_material_snapshot_230' in M
assert 'MaterialConfig230.restoreGlobal(context)' in H
assert 'i.getStringExtra(MaterialConfig230.EXTRA)' in H
assert 'MaterialConfig230.apply(directMaterial230,true)' in H
assert 'i.putExtra(MaterialConfig230.EXTRA' in U
assert 'publishMaterialVerified(' in U and 'publishMaterialVerified(' in C
assert 'MaterialConfig230.snapshot(context)' in C
assert 'if(aaProbe208==0 && UnifiedGlass230.draw(' in L
assert 'if(!s.enabled){' in S and 'retryAfter240' in S and 'reset240' in S
assert 'OPTICAL_FAILURE_240' in S and 'OPTICAL_RECOVERED_240' in S
assert 'canvas.drawRoundRect(rect,radius,radius,paint);' in S
assert 'uniform float strength230;' in S
assert 'uniform float thickness230;' in S
assert 'uniform float environment230;' in S
assert 'uniform float quality230;' in S
for v in ['surface230','normalSlope230','warp230','transmission230']:
    assert v in S
for v in ['PREF_ON','PREF_STRENGTH','PREF_THICKNESS','PREF_ENV','PREF_QUALITY']:
    assert v in M and 'MaterialConfig230.'+v in U
assert 'String[] qualityNames230' in U
assert U.count('section("同曲面环境反射"')==0
assert U.count('section("二维曲面透镜"')==0
assert '展开稳定引擎兼容参数（V229）' in U
assert 'LG230' in M and 'root("settings put global "+MaterialConfig230.GLOBAL_KEY' in C
assert 'new State(false,70,55,60,0)' in M
for old in ['nonlinear_107','thickness_107','interior_107','focus_107','reflection_107','surface_mode_195','surface_strength_195','surface_softness_195']:
    assert old in U and old in C and old in P
assert (R/'root-helper/99-liquidglass-wallpaper.sh').read_bytes()==(R/'app/src/main/assets/99-liquidglass-wallpaper.sh').read_bytes()
assert "versionName '1.9.205.240'" in (R/'app/build.gradle').read_text()
assert 'ColorOS-LiquidGlass-V1.9.205.240-APK' in (R/'.github/workflows/build-apk.yml').read_text()
print('PASS V230: LG1 33 unchanged, LG230 persisted/direct/root/boot, five controls, single-pass shader, V229 fallback, legacy keys and version metadata')
