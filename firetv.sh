#!/usr/bin/env bash
# Fire TV debloat helper for Insignia NS-55F301NA22 (Fire OS 7.7.1.7)
# Usage:
#   ./firetv.sh connect            connect to the TV over ADB
#   ./firetv.sh backup             save installed + disabled package lists to ./backup/
#   ./firetv.sh find <word>        search packages, e.g. ./firetv.sh find amazon
#   ./firetv.sh disable <pkg>...   disable packages (reversible), logs each one
#   ./firetv.sh disable-file       disable everything listed in packages.txt
#   ./firetv.sh restore            re-enable everything this script disabled
set -u
TV_IP="192.168.68.54"
LOG="backup/disabled.log"
mkdir -p backup

case "${1:-}" in
  connect)
    adb connect "$TV_IP:5555"
    echo "Accept the 'Allow USB debugging' prompt on the TV, then run: adb devices"
    ;;
  backup)
    adb shell pm list packages -f   > backup/all_packages.txt
    adb shell pm list packages -d   > backup/already_disabled.txt
    adb shell dumpsys package packages > backup/dumpsys_packages.txt 2>/dev/null
    echo "Saved to ./backup/"
    ;;
  find)
    adb shell pm list packages | grep -i "${2:?give a search word}"
    ;;
  disable)
    shift
    for p in "$@"; do
      if adb shell pm disable-user --user 0 "$p" | grep -q "new state: disabled"; then
        echo "$p" >> "$LOG"; echo "disabled  $p"
      else
        echo "FAILED    $p"
      fi
    done
    ;;
  disable-file)
    grep -vE '^\s*(#|$)' packages.txt | while read -r p; do
      if adb shell pm disable-user --user 0 "$p" | grep -q "new state: disabled"; then
        echo "$p" >> "$LOG"; echo "disabled  $p"
      else
        echo "FAILED    $p"
      fi
    done
    ;;
  restore)
    [ -f "$LOG" ] || { echo "nothing to restore"; exit 0; }
    sort -u "$LOG" | while read -r p; do
      adb shell pm enable "$p" && echo "enabled   $p"
    done
    : > "$LOG"
    ;;
  *)
    sed -n '2,12p' "$0"
    ;;
esac
