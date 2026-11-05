#!/system/bin/sh
# Usage (Termux): su -c 'sh /path/to/optical_diagnostic_235.sh 1'
# 0=normal, 1=displacement, 2=normals, 3=height, 4=raw wallpaper,
# 5=warped wallpaper without reflection, 6=final composite.
mode="${1:-0}"
case "$mode" in 0|1|2|3|4|5|6) ;; *) echo 'Mode must be 0..6'; exit 2;; esac
settings put global liquidglass_optical_diagnostic_235 "$mode"
echo "Optical diagnostic mode=$mode; return to 0 after testing"
