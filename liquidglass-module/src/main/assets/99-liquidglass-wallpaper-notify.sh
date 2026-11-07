#!/system/bin/sh
# V288.33: react to close-write/moved-in for the finished wallpaper screenshot.
SERVICE=/data/adb/service.d/99-liquidglass-wallpaper.sh
case "$3" in
  wallpaper_screenshot.png)
    case "$1" in
      *w*|*m*)
        read uptime_sec _ < /proc/uptime
        echo "KYANT28833_SOURCE_COMPLETE uptimeSec=$uptime_sec event=$1" >> /data/local/tmp/liquidglass-wallpaper-sync.log
        touch /data/local/tmp/.liquidglass-wallpaper-trigger
        "$SERVICE" --event-wallpaper >/dev/null 2>&1 & ;;
    esac ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger
    echo "CONFIG_CHAIN_126 stage=FS_EVENT wall=$(date +%s)" >> /data/local/tmp/liquidglass-wallpaper-sync.log
    ;;
esac
