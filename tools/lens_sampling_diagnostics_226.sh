#!/system/bin/sh
# V226 non-rendering diagnostic snapshot; read-only except output files.
# Run from Termux: su -c 'sh /sdcard/lens_sampling_diagnostics_226.sh'
OUT=/sdcard/Download/liquidglass_lens_226_$(date +%Y%m%d_%H%M%S)
mkdir -p "$OUT" || exit 1
{
 echo 'V226 read-only sampling investigation'; date
 echo '=== display ==='; wm size; wm density
 echo '=== surface ==='; dumpsys SurfaceFlinger 2>/dev/null | grep -i -m 16 -E 'color mode|composition|refresh|display'
 echo '=== graphics ==='; getprop ro.hardware.egl; getprop ro.opengles.version
 echo '=== launcher ==='; dumpsys package com.android.launcher 2>/dev/null | grep -m 8 -E 'versionName|versionCode'
 echo '=== active globals ==='
 settings list global | grep -Ei 'liquidglass_(refraction|lens|aa|edge|sample|valid|interior|diagnostic)' | sed -E 's/(password|token|secret)=[^ ]+/\1=[REDACTED]/Ig'
} > "$OUT/device.txt" 2>&1
logcat -d -t 3500 -v time 2>/dev/null | grep -Ei 'LIQUIDGLASS|REFRACTION_SAMPLE|SHADER.*(FAILED|FALLBACK)' > "$OUT/render_logs.txt"
echo "$OUT"
