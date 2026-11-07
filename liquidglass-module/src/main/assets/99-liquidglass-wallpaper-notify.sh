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
        case "$3" in
          wallpaper_screenshot.png)
            # Finished screenshots are actionable; launch one bounded copy worker.
            echo "KYANT28837_SCREENSHOT_EVENT uptimeSec=$uptime_sec event=$1" >> /data/local/tmp/liquidglass-wallpaper-sync.log
            touch /data/local/tmp/.liquidglass-wallpaper-trigger
            "$SERVICE" --event-wallpaper wallpaper_screenshot.png >/dev/null 2>&1 &
            ;;
          *)
            # The source file can change >10s before ColorOS creates the
            # screen-space screenshot. Spawning a 2.4s polling worker for each
            # source write wastes CPU without producing an earlier reference.
            # Keep the source event timestamp for early-frame calibration.
            echo "KYANT28837_SOURCE_HINT uptimeSec=$uptime_sec file=$3 event=$1" >> /data/local/tmp/liquidglass-wallpaper-sync.log
            ;;
        esac ;;
    esac ;;
  glass.xml|glass.xml.bak)
    touch /data/local/tmp/.liquidglass-config-trigger
    echo "CONFIG_CHAIN_126 stage=FS_EVENT wall=$(date +%s)" >> /data/local/tmp/liquidglass-wallpaper-sync.log
    ;;
esac
