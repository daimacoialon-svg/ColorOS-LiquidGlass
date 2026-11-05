#!/system/bin/sh
# V194: config events only set one trigger; the resident script does the publication.
SERVICE=/data/adb/service.d/99-liquidglass-wallpaper.sh
case "$3" in
  wallpaper_screenshot.png)
    touch /data/local/tmp/.liquidglass-wallpaper-trigger
    "$SERVICE" --event-wallpaper >/dev/null 2>&1 & ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger
    echo "CONFIG_CHAIN_126 stage=FS_EVENT wall=$(date +%s)" >> /data/local/tmp/liquidglass-wallpaper-sync.log
    ;;
esac
