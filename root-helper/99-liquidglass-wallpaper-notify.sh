#!/system/bin/sh
# V123: lightweight inotifyd callback; no background loop in this script.
case "$3" in
  wallpaper_screenshot.png)
    touch /data/local/tmp/.liquidglass-wallpaper-trigger ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger ;;
esac
