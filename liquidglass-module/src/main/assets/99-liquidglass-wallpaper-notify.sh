#!/system/bin/sh
# V288.35: include directory-child modified/created events (c/n) in addition
# to the older close-write/move events (w/m).
# V288.34: wallpaper source change is the EARLY hint; the final screenshot
# remains the only optical reference. All work is off the Launcher UI thread.
SERVICE=/data/adb/service.d/99-liquidglass-wallpaper.sh
case "$3" in
  wallpaper_screenshot.png|wallpaper|wallpaper_orig|wallpaper_info.xml)
    case "$1" in
      *w*|*m*|*n*|*c*)
        read uptime_sec _ < /proc/uptime
        echo "KYANT28835_SOURCE_EVENT uptimeSec=$uptime_sec file=$3 event=$1" >> /data/local/tmp/liquidglass-wallpaper-sync.log
        touch /data/local/tmp/.liquidglass-wallpaper-trigger
        "$SERVICE" --event-wallpaper "$3" >/dev/null 2>&1 & ;;
    esac ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger
    echo "CONFIG_CHAIN_126 stage=FS_EVENT wall=$(date +%s)" >> /data/local/tmp/liquidglass-wallpaper-sync.log
    ;;
esac
