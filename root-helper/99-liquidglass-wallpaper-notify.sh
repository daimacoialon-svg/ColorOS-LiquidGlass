#!/system/bin/sh
# V124: direct one-shot event handling. No second resident daemon.
SERVICE=/data/adb/service.d/99-liquidglass-wallpaper.sh
case "$3" in
  wallpaper_screenshot.png)
    touch /data/local/tmp/.liquidglass-wallpaper-trigger
    "$SERVICE" --event-wallpaper >/dev/null 2>&1 & ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger
    "$SERVICE" --event-config >/dev/null 2>&1 & ;;
esac
