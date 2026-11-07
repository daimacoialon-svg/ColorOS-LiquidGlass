#!/system/bin/sh
# V1.9.205.288.37: source events are hints, not 2.4s polling jobs;
# finished screenshot events publish the shared reference immediately.
# V1.9.205.288.35: watch directory child CREATE / MODIFY / MOVED_TO / CLOSE_WRITE;
# previous :wm missed ColorOS in-place screenshot changes on some builds.
# Keep all wallpaper event work outside Launcher render frames.
# V1.9.205.288.34: observe wallpaper source completion as an early wake-up;
# publish ONLY the verified screen-space screenshot. Do not replace it with an
# uncropped wallpaper source that would shift optical sampling UVs.
# Bound short-lived event retries; normal draw loop never scans the filesystem.
# Source is checked twice before atomic replacement; never expose partial copies.
# Android toybox inotifyd is optional; fallback works without it.
PKG=top.liquidglass.vector
SRC=/data/system/users/0/wallpaper_screenshot.png
DSTDIR=/data/user/0/$PKG/files
DST=$DSTDIR/wallpaper_reference.jpg
SIGFILE=$DSTDIR/.wallpaper_reference_source_sig
# Separate copy owned by Launcher: bypass third-party provider cold-start restrictions.
LAUNCHERDIR=/data/user/0/com.android.launcher/files
LAUNCHERDST=$LAUNCHERDIR/liquidglass_wallpaper_reference.jpg
LAUNCHERSIG=$LAUNCHERDIR/.liquidglass_wallpaper_reference_sig
# V148: device-protected mirror, available immediately after boot / unlock.
LAUNCHERDEDIR=/data/user_de/0/com.android.launcher/files
LAUNCHERDEDST=$LAUNCHERDEDIR/liquidglass_wallpaper_reference.jpg
LAUNCHERDESIG=$LAUNCHERDEDIR/.liquidglass_wallpaper_reference_sig
TRIGGER=/data/local/tmp/.liquidglass-wallpaper-trigger
LOG=/data/local/tmp/liquidglass-wallpaper-sync.log
WATCHER_PID=
CONFIG_WATCHER_PID=
CONFIG_TRIGGER=/data/local/tmp/.liquidglass-config-trigger
cleanup() { [ -n "$WATCHER_PID" ] && kill "$WATCHER_PID" 2>/dev/null; [ -n "$CONFIG_WATCHER_PID" ] && kill "$CONFIG_WATCHER_PID" 2>/dev/null; rm -f "$TRIGGER" "$CONFIG_TRIGGER"; }
# Event workers must NOT clean the resident service's trigger files.
# Only one service loop; avoid duplicates after manual testing/restarts.
LOCK=/data/local/tmp/.liquidglass-wallpaper-sync.lock
# Reject stale locks even when the old PID was reused by an unrelated service.
# Do not kill any process: only check the owner identity.
lock_owner_alive() {
  old=$(cat "$LOCK/pid" 2>/dev/null)
  case "$old" in ''|*[!0-9]*) return 1;; esac
  [ -r "/proc/$old/cmdline" ] || return 1
  cmd=$(tr '\000' ' ' < "/proc/$old/cmdline" 2>/dev/null)
  case "$cmd" in *"/data/adb/service.d/99-liquidglass-wallpaper.sh"*) ;; *) return 1;; esac
  old_start=$(cat "$LOCK/starttime" 2>/dev/null)
  [ -n "$old_start" ] || return 1
  now_start=$(sed 's/.*) //' "/proc/$old/stat" 2>/dev/null | awk '{print $20}')
  [ -n "$now_start" ] && [ "$old_start" = "$now_start" ]
}
if [ "$1" != "--event-wallpaper" ] && [ "$1" != "--event-config" ] && [ "$1" != "--event-early-source" ]; then
if ! mkdir "$LOCK" 2>/dev/null; then
  if lock_owner_alive; then exit 0; fi
  rm -rf "$LOCK"
  mkdir "$LOCK" 2>/dev/null || exit 0
fi
printf '%s' "$$" > "$LOCK/pid"
# V1.9.203: identify the exact script revision used by this running process.
sha256sum /data/adb/service.d/99-liquidglass-wallpaper.sh 2>/dev/null | cut -d ' ' -f1 > "$LOCK/running_sha256"
sed 's/.*) //' "/proc/$$/stat" 2>/dev/null | awk '{print $20}' > "$LOCK/starttime"
cleanup_all() { cleanup; rm -rf "$LOCK"; }
trap cleanup_all EXIT INT TERM
fi
# inotifyd calls the helper as EVENTS WATCHED_PATH [CHILD_NAME].
# On watched directories: m = moved into dir, n = created in dir,
# c = modified child. The previous :wm lacked n/c and missed in-place writes.
# w also covers close-write where provided by this device's toybox.
start_wallpaper_watcher28835() {
  command -v inotifyd >/dev/null 2>&1 || return
  [ -d /data/system/users/0 ] || return
  if [ -n "$WATCHER_PID" ] && kill -0 "$WATCHER_PID" 2>/dev/null; then
    watcher_state=$(sed 's/.*) //' "/proc/$WATCHER_PID/stat" 2>/dev/null | cut -d ' ' -f1)
    [ "$watcher_state" != Z ] && [ "$watcher_state" != X ] && return
  fi
  inotifyd /data/adb/service.d/99-liquidglass-wallpaper-notify.sh /data/system/users/0:wmnc >/dev/null 2>&1 &
  WATCHER_PID=$!
  read watcher_up _ < /proc/uptime
  echo "KYANT28835_WATCHER_START uptimeSec=$watcher_up pid=$WATCHER_PID mask=wmnc" >> "$LOG"
}
[ -z "$1" ] && start_wallpaper_watcher28835
# V123: watch the app's private preferences directory. Android SharedPreferences
# commonly persists via rename from glass.xml.bak, so monitor the directory.
# This watcher only signals; the existing root loop publishes the snapshot.
start_config_watcher194() {
  [ -d "/data/user/0/$PKG/shared_prefs" ] || return
  command -v inotifyd >/dev/null 2>&1 || return
  if [ -n "$CONFIG_WATCHER_PID" ] && kill -0 "$CONFIG_WATCHER_PID" 2>/dev/null; then return; fi
  inotifyd /data/adb/service.d/99-liquidglass-wallpaper-notify.sh "/data/user/0/$PKG/shared_prefs:cmw" >/dev/null 2>&1 &
  CONFIG_WATCHER_PID=$!
}
[ -z "$1" ] && start_config_watcher194
# Persist a minimal non-secret configuration snapshot independent of the app provider.
PREF=/data/user/0/$PKG/shared_prefs/glass.xml
SNAPKEY=liquidglass_boot_snapshot_v1
LASTSNAP=""
# V125: parse preferences in a single awk process instead of 31 grep/head/sed
# pipelines. This reduces shell process creation on each UI update.
make_snapshot125() {
  awk '
  BEGIN {
    n=split("enabled:1 alpha:210 tint:0 tint_strength:30 highlight:0 diagnostics:0 highlight_strength:25 probe_mode:0 highlight_width:2 directional_light:1 native_blur_experiment:0 blur_radius_percent:100 native_alpha_probe:0 wallpaper_compare:0 real_refraction:0 custom_blur_dp:14 refraction_percent:35 glass_tint_opacity:18 custom_blur_mode:1 clear_refraction:0 dynamic_sheen_mode:0 surface_mode_195:3 surface_strength_195:100 surface_softness_195:100 icon_optics_204:1 dock_glass_204:1 nonlinear_107:65 thickness_107:55 interior_107:20 focus_107:45 reflection_107:30 optical_style_186:0 custom_blur_tenths_95:140", items, " ");
    for(i=1;i<=n;i++){split(items[i],pair,":"); keys[i]=pair[1]; values[pair[1]]=pair[2]}
  }
  /name="/ {
    line=$0
    if(match(line,/name="[^"]+"/)){
      key=substr(line,RSTART+6,RLENGTH-7)
      if(!(key in values))next
      if(match(line,/value="[^"]*"/)){
        val=substr(line,RSTART+7,RLENGTH-8)
        if(val=="true")val="1"; else if(val=="false")val="0"
        if(val ~ /^-?[0-9]+$/) values[key]=val
      }
    }
  }
  END { result="LG1";for(i=1;i<=n;i++)result=result "|" values[keys[i]];print result }
  ' "$PREF"
}
# V196: validate both PID and process identity; PID reuse must not block updates.
config_lock_owner_alive196() {
  owner=$(cat "$local_sync_lock/pid" 2>/dev/null)
  case "$owner" in ''|*[!0-9]*) return 1;; esac
  [ -r "/proc/$owner/cmdline" ] || return 1
  owner_cmd=$(tr '\000' ' ' < "/proc/$owner/cmdline" 2>/dev/null)
  case "$owner_cmd" in *"99-liquidglass-wallpaper.sh"*) ;; *) return 1;; esac
  owner_start=$(cat "$local_sync_lock/starttime" 2>/dev/null)
  [ -n "$owner_start" ] || return 1
  actual_start=$(sed 's/.*) //' "/proc/$owner/stat" 2>/dev/null | awk '{print $20}')
  [ -n "$actual_start" ] && [ "$owner_start" = "$actual_start" ]
}
unlock_config196() {
  # The lock directory must be empty before rmdir. Only its owner calls this.
  rm -f "$local_sync_lock/pid" "$local_sync_lock/starttime"
  rmdir "$local_sync_lock" 2>/dev/null || echo "CONFIG_196 stage=UNLOCK_FAILED" >> "$LOG"
}
sync_config() {
  local_sync_lock=/data/local/tmp/.liquidglass-config-publish.lock
  if ! mkdir "$local_sync_lock" 2>/dev/null; then
    if config_lock_owner_alive196; then
      echo "CONFIG_196 stage=LOCK_BUSY owner=$owner" >> "$LOG"
      touch "$CONFIG_TRIGGER"
      return 2
    fi
    # Legacy V195 locks have only a PID; a reused system PID is not an owner.
    rm -f "$local_sync_lock/pid" "$local_sync_lock/starttime"
    if ! rmdir "$local_sync_lock" 2>/dev/null || ! mkdir "$local_sync_lock" 2>/dev/null; then
      echo "CONFIG_196 stage=LOCK_RECOVERY_FAILED" >> "$LOG"
      touch "$CONFIG_TRIGGER"
      return 2
    fi
    echo "CONFIG_196 stage=RECOVER_STALE_LOCK" >> "$LOG"
  fi
  printf '%s' "$$" > "$local_sync_lock/pid"
  sed 's/.*) //' "/proc/$$/stat" 2>/dev/null | awk '{print $20}' > "$local_sync_lock/starttime"
  result=0
  if [ ! -s "$PREF" ]; then
    echo "CONFIG_196 stage=PREF_MISSING" >> "$LOG"
    result=3
  else
    SNAP=$(make_snapshot125)
    NF=$(printf '%s' "$SNAP" | awk -F '|' '{print NF}')
    if [ "$NF" != 34 ] || ! printf '%s' "$SNAP" | grep -Eq '^LG1(\|-?[0-9]+){33}$'; then
      echo "CONFIG_196 stage=REJECT_SCHEMA nf=$NF" >> "$LOG"
      result=4
    else
      CURRENT=$(settings get global "$SNAPKEY" 2>/dev/null)
      if [ "$SNAP" != "$CURRENT" ]; then
        if settings put global "$SNAPKEY" "$SNAP" 2>>"$LOG"; then
          echo "CONFIG_CHAIN_126 stage=ROOT_PUBLISHED wall=$(date +%s)" >> "$LOG"
        else
          echo "CONFIG_196 stage=WRITE_FAILED" >> "$LOG"
          result=5
        fi
      fi
      VERIFIED=$(settings get global "$SNAPKEY" 2>/dev/null)
      if [ "$VERIFIED" != "$SNAP" ]; then
        echo "CONFIG_196 stage=VERIFY_FAILED" >> "$LOG"
        result=6
      elif [ "$result" -eq 0 ]; then
        LASTSNAP="$SNAP"
        echo "CONFIG_196 stage=VERIFIED" >> "$LOG"
      fi
    fi
  fi
  unlock_config196
  return "$result"
}
sync_once() {
  [ -f "$SRC" ] && [ -d "$DSTDIR" ] || return
  UIDNUM=$(stat -c %u "/data/user/0/$PKG" 2>/dev/null)
  case "$UIDNUM" in ''|*[!0-9]*) return;; esac
  BEFORE=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
  [ -n "$BEFORE" ] || return
  PREV=$(cat "$SIGFILE" 2>/dev/null)
  [ "$BEFORE" != "$PREV" ] || return
  # Debounce writes and verify that the source has stabilized.
  sleep 0.06
  AFTER=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
  [ "$BEFORE" = "$AFTER" ] || return
  TMP="$DSTDIR/.wallpaper_reference.new.$$"
  if cp "$SRC" "$TMP" 2>/dev/null && [ "$(stat -c %s "$TMP" 2>/dev/null)" -gt 1024 ]; then
    FINAL=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
    if [ "$FINAL" = "$AFTER" ]; then
      chown "$UIDNUM:$UIDNUM" "$TMP" && chmod 600 "$TMP"
      restorecon "$TMP" 2>/dev/null
      if mv -f "$TMP" "$DST"; then
        printf '%s' "$FINAL" > "$SIGFILE"
        chown "$UIDNUM:$UIDNUM" "$SIGFILE"
        chmod 600 "$SIGFILE"
        restorecon "$DST" "$SIGFILE" 2>/dev/null
        echo "$(date) synced $FINAL" >> "$LOG"
      fi
    fi
  fi
  rm -f "$TMP"
}
sync_launcher_reference() {
  [ -f "$SRC" ] || { echo "$(date) launcher reference failed: source missing" >> "$LOG"; return; }
  LAUNCHERBASE=/data/user/0/com.android.launcher
  [ -d "$LAUNCHERBASE" ] || { echo "$(date) launcher reference waiting: launcher data missing" >> "$LOG"; return; }
  LUID=$(stat -c %u "$LAUNCHERBASE" 2>/dev/null)
  LGID=$(stat -c %g "$LAUNCHERBASE" 2>/dev/null)
  case "$LUID:$LGID" in *[!0-9:]*|:*) echo "$(date) launcher reference failed: invalid owner" >> "$LOG"; return;; esac
  if [ ! -d "$LAUNCHERDIR" ]; then
    mkdir -p "$LAUNCHERDIR" || { echo "$(date) launcher reference failed: mkdir" >> "$LOG"; return; }
    chown "$LUID:$LGID" "$LAUNCHERDIR" && chmod 700 "$LAUNCHERDIR"
    restorecon -R "$LAUNCHERDIR" 2>/dev/null
  fi
  BEFORE=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
  [ -n "$BEFORE" ] || return
  PREV=$(cat "$LAUNCHERSIG" 2>/dev/null)
  if [ "$BEFORE" = "$PREV" ] && [ -s "$LAUNCHERDST" ]; then return; fi
  sleep 0.06
  AFTER=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
  [ "$BEFORE" = "$AFTER" ] || return
  TMP="$LAUNCHERDIR/.liquidglass_wallpaper.new.$$"
  if cp "$SRC" "$TMP" 2>/dev/null && [ "$(stat -c %s "$TMP" 2>/dev/null)" -gt 1024 ]; then
    FINAL=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
    if [ "$FINAL" = "$AFTER" ]; then
      chown "$LUID:$LGID" "$TMP" && chmod 600 "$TMP"
      restorecon "$TMP" 2>/dev/null
      if mv -f "$TMP" "$LAUNCHERDST"; then
        printf '%s' "$FINAL" > "$LAUNCHERSIG"
        chown "$LUID:$LGID" "$LAUNCHERSIG"
        chmod 600 "$LAUNCHERSIG"
        restorecon "$LAUNCHERDST" "$LAUNCHERSIG" 2>/dev/null
        if [ -s "$LAUNCHERDST" ]; then
          echo "$(date) launcher reference synced $FINAL" >> "$LOG"
        else
          echo "$(date) launcher reference failed: missing after move" >> "$LOG"
        fi
      fi
    fi
  fi
  rm -f "$TMP"
}

# V288.38: early ORIGINAL source diagnostics, deliberately NOT an optical frame.
# This copy is created once per changed source, off Launcher/render threads.
# The original image may use a different crop/offset than ColorOS' screenshot.
EARLY_SRC=/data/system/users/0/wallpaper_orig
EARLY_DST=$LAUNCHERDIR/liquidglass_early_wallpaper_source
EARLY_SIG=$LAUNCHERDIR/.liquidglass_early_wallpaper_sig
publish_early_source_28838() {
  [ -s "$EARLY_SRC" ] && [ -d /data/user/0/com.android.launcher ] || return 0
  uid=$(stat -c %u /data/user/0/com.android.launcher 2>/dev/null) || return 0
  gid=$(stat -c %g /data/user/0/com.android.launcher 2>/dev/null) || return 0
  case "$uid:$gid" in ''|*[!0-9:]*) return 0;; esac
  before=$(stat -c '%s:%y' "$EARLY_SRC" 2>/dev/null) || return 0
  [ "${before%%:*}" -gt 1024 ] 2>/dev/null || return 0
  [ "$before" != "$(cat "$EARLY_SIG" 2>/dev/null)" ] || return 0
  # Complete-write event is normally sufficient; this short stability check
  # also protects against non-atomic vendor writes, with no long polling.
  sleep 0.08
  after=$(stat -c '%s:%y' "$EARLY_SRC" 2>/dev/null) || return 0
  [ "$before" = "$after" ] || return 0
  [ -d "$LAUNCHERDIR" ] || mkdir -p "$LAUNCHERDIR" || return 0
  tmp="$LAUNCHERDIR/.liquidglass_early_source.$$"
  if cp "$EARLY_SRC" "$tmp" 2>/dev/null && [ -s "$tmp" ] && [ "$(stat -c '%s:%y' "$EARLY_SRC" 2>/dev/null)" = "$after" ]; then
    chown "$uid:$gid" "$tmp" && chmod 600 "$tmp"
    restorecon "$tmp" 2>/dev/null
    if mv -f "$tmp" "$EARLY_DST"; then
      printf '%s' "$after" > "$EARLY_SIG"
      chown "$uid:$gid" "$EARLY_SIG"; chmod 600 "$EARLY_SIG"
      restorecon "$EARLY_DST" "$EARLY_SIG" 2>/dev/null
      read up _ < /proc/uptime
      echo "KYANT28838_EARLY_SOURCE_READY uptimeSec=$up size=$(stat -c %s "$EARLY_DST" 2>/dev/null)" >> "$LOG"
    fi
  fi
  rm -f "$tmp"
}
# V148: Keep a persistent DE copy synchronized with the verified CE reference.
# Copy only when source identity changes; never remove the previous valid frame
# on ordinary screen lock/unlock. The temporary file is atomically renamed.
sync_de_reference() {
  [ -s "$LAUNCHERDST" ] || return
  [ -d /data/user_de/0/com.android.launcher ] || return
  LUID=$(stat -c %u /data/user_de/0/com.android.launcher 2>/dev/null)
  LGID=$(stat -c %g /data/user_de/0/com.android.launcher 2>/dev/null)
  case "$LUID:$LGID" in *[!0-9:]*|:*) return;; esac
  [ -d "$LAUNCHERDEDIR" ] || { mkdir -p "$LAUNCHERDEDIR" || return; chown "$LUID:$LGID" "$LAUNCHERDEDIR"; chmod 700 "$LAUNCHERDEDIR"; restorecon "$LAUNCHERDEDIR" 2>/dev/null; }
  sig=$(cat "$LAUNCHERSIG" 2>/dev/null)
  [ -n "$sig" ] || return
  [ "$sig" = "$(cat "$LAUNCHERDESIG" 2>/dev/null)" ] && [ -s "$LAUNCHERDEDST" ] && return
  tmp="$LAUNCHERDEDIR/.liquidglass_ref.new.$$"
  if cp "$LAUNCHERDST" "$tmp" 2>/dev/null && [ "$(stat -c %s "$tmp" 2>/dev/null)" -gt 1024 ]; then
    chown "$LUID:$LGID" "$tmp" && chmod 600 "$tmp"
    restorecon "$tmp" 2>/dev/null
    if mv -f "$tmp" "$LAUNCHERDEDST"; then
      printf '%s' "$sig" > "$LAUNCHERDESIG"
      chown "$LUID:$LGID" "$LAUNCHERDESIG"; chmod 600 "$LAUNCHERDESIG"
      restorecon "$LAUNCHERDEDST" "$LAUNCHERDESIG" 2>/dev/null
      echo "$(date) DE reference synced $sig" >> "$LOG"
    fi
  fi
  rm -f "$tmp"
}
# V1.9.94: do not wait for the five-minute fallback when Android CE
# storage / Launcher private files appear after Magisk service startup.
# Run the first checks immediately; repeat quickly until both copies exist.
if [ "$1" = "--event-config" ]; then
  # Compatibility with legacy event callers: publish once, never touch daemon triggers.
  sync_config
  exit $?
fi
if [ "$1" = "--event-early-source" ]; then
  # Worker bypasses the RESIDENT service's PID lock (fixed V288.39).
  read early_up _ < /proc/uptime
  echo "KYANT28839_EARLY_WORKER_START uptimeSec=$early_up pid=$$" >> "$LOG"
  # One worker per event burst. Only w/m events start a copy; c/n are hints.
  lock=/data/local/tmp/.liquidglass-early-source.lock
  if ! mkdir "$lock" 2>/dev/null; then
    echo "KYANT28839_EARLY_WORKER_COALESCED pid=$$" >> "$LOG"
    exit 0
  fi
  trap 'rmdir "$lock" 2>/dev/null' EXIT INT TERM
  publish_early_source_28838
  read early_done _ < /proc/uptime
  echo "KYANT28839_EARLY_WORKER_FINISH uptimeSec=$early_done pid=$$" >> "$LOG"
  exit 0
fi
if [ "$1" = "--event-wallpaper" ]; then
  # Event workers are temporary. A burst of system file events is coalesced
  # into one bounded retry, and a locked-out worker leaves a resident trigger.
  event_lock=/data/local/tmp/.liquidglass-wallpaper-event.lock
  if ! mkdir "$event_lock" 2>/dev/null; then
    touch "$TRIGGER"
    exit 0
  fi
  trap 'rmdir "$event_lock" 2>/dev/null' EXIT INT TERM
  read event_up _ < /proc/uptime
  event_name=$2
  source_before=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
  published_before=$(cat "$LAUNCHERSIG" 2>/dev/null)
  echo "KYANT28837_EVENT_BEGIN uptimeSec=$event_up file=$event_name screenshotSig=$source_before publishedSig=$published_before" >> "$LOG"
  # Source hints are logged by notify.sh only. Publishing must wait for
  # ColorOS' screen-space screenshot; never substitute an uncalibrated source.
  # Retain short retries for close-write races and atomic replacements.
  case "$event_name" in
    wallpaper_screenshot.png) attempts=4;;
    *) echo "KYANT28837_SOURCE_ONLY_NO_POLL event=$event_name" >> "$LOG"; exit 0;;
  esac
  n=0
  published_ok=0
  while [ "$n" -lt "$attempts" ]; do
    n=$((n + 1))
    current=$(stat -c '%s:%y' "$SRC" 2>/dev/null)
    if [ -n "$current" ] && [ "$current" != "$published_before" ]; then
      sync_launcher_reference
      sync_de_reference
      sync_once
      published=$(cat "$LAUNCHERSIG" 2>/dev/null)
      if [ "$current" = "$published" ] && [ -s "$LAUNCHERDST" ]; then
        read publish_up _ < /proc/uptime
        echo "KYANT28837_SCREENSHOT_PUBLISHED uptimeSec=$publish_up event=$event_name retry=$n sig=$current" >> "$LOG"
        published_ok=1
        break
      fi
    fi
    sleep 0.1
  done
  if [ "$published_ok" -eq 0 ]; then
    read end_up _ < /proc/uptime
    echo "KYANT28837_SCREENSHOT_WAIT_LIMIT uptimeSec=$end_up event=$event_name source=$(stat -c '%s:%y' "$SRC" 2>/dev/null)" >> "$LOG"
  fi
  # The normal service is a fallback if ColorOS produces the screenshot later.
  touch "$TRIGGER"
  exit 0
fi
sync_config
publish_early_source_28838
sync_launcher_reference
sync_de_reference
sync_once
TICKS=0
CONFIG_TICKS=0
BOOT_TICKS=0
while true; do
  if [ "$BOOT_TICKS" -lt 180 ]; then sleep 1; else sleep 2; fi
  # V194: restart a failed/missing preference watcher; APP may mount CE late.
  if [ $((BOOT_TICKS % 10)) -eq 0 ]; then
    start_config_watcher194
    start_wallpaper_watcher28835
  fi
  # V123: event-first config publication. Fallback is deliberately infrequent
  # (roughly 5 minutes after boot) and never blocks wallpaper synchronization.
  if [ -e "$CONFIG_TRIGGER" ]; then
    rm -f "$CONFIG_TRIGGER"
    sync_config
  elif [ "$BOOT_TICKS" -lt 180 ]; then
    # Initial startup: retry while CE storage and preferences become available.
    if [ $((BOOT_TICKS % 10)) -eq 0 ]; then sync_config; fi
  elif [ $((CONFIG_TICKS % 150)) -eq 0 ]; then
    sync_config
  fi
  CONFIG_TICKS=$((CONFIG_TICKS + 1))
  TICKS=$((TICKS + 1))
  if [ -e "$TRIGGER" ]; then
    rm -f "$TRIGGER"
    sync_launcher_reference
    sync_de_reference
    sync_once
  elif [ "$BOOT_TICKS" -lt 180 ]; then
    # During the first three minutes, missing paths must not defer
    # synchronization to the normal five-minute maintenance cycle.
    [ -s "$LAUNCHERDST" ] || sync_launcher_reference
    [ -s "$LAUNCHERDEDST" ] || sync_de_reference
    [ -s "$DST" ] || sync_once
    # If the source changed across reboot, signatures will be refreshed.
    if [ $((BOOT_TICKS % 2)) -eq 0 ]; then
      sync_launcher_reference
      sync_de_reference
      sync_once
    fi
  elif [ "$TICKS" -ge 2 ]; then
    sync_launcher_reference
    sync_de_reference
    sync_once
  fi
  [ "$TICKS" -ge 2 ] && TICKS=0
  [ "$BOOT_TICKS" -lt 180 ] && BOOT_TICKS=$((BOOT_TICKS + 1))
done
