#!/usr/bin/env bash
# Start/stop an on-device recording, or pull the results.
#
#   ./hu-record.sh start     begin recording to the unit's own storage
#   ./hu-record.sh stop      end the recording
#   ./hu-record.sh pull      copy recordings here and list them
#
# Recording writes to the head unit's internal storage, so it needs no network.
# That matters for driving captures: Android ties wireless debugging to an
# active Wi-Fi connection, so it stops working the moment you leave the driveway.
# Start a recording on Wi-Fi, drive, come back, pull.
#
# GPS velocity from the unit's own receiver is recorded alongside the frames as
# speed ground truth, which makes decoding road speed a correlation rather than
# a guess. Needs the location permission granted once:
#   adb shell pm grant com.probe.hu android.permission.ACCESS_FINE_LOCATION
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1
DIR=/sdcard/Android/data/com.probe.hu/files

case "${1:-}" in
  start)
    hus "am broadcast -a com.probe.hu.RECORD --ei on 1" >/dev/null 2>&1
    sleep 2
    hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | tr -d '\r' \
      | grep -iE 'RECORDING|gps|FAILED' | tail -3 | sed 's/^/  /'
    ;;
  stop)
    hus "am broadcast -a com.probe.hu.RECORD --ei on 0" >/dev/null 2>&1
    sleep 2
    hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | tr -d '\r' \
      | grep -i 'RECORDING STOPPED' | tail -1 | sed 's/^/  /'
    hus "ls -la $DIR" 2>/dev/null | tr -d '\r' | sed 's/^/  /'
    ;;
  pull)
    mkdir -p captures
    for f in $(hus "ls $DIR" 2>/dev/null | tr -d '\r' | grep '\.tsv$'); do
      "$ADB" -s "$TARGET" pull "$DIR/$f" "captures/$f" 2>&1 | tail -1
    done
    ls -la captures/ 2>/dev/null | tail -n +2
    ;;
  *) echo "usage: $0 {start|stop|pull}"; exit 1 ;;
esac
