#!/usr/bin/env bash
# Dump complete climate state. With a file argument, diff against it instead.
#   ./hu-state.sh                 print current state
#   ./hu-state.sh > base.tsv      save a baseline
#   ./hu-state.sh base.tsv        diff current against the baseline
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1

hus "am force-stop com.probe.hu; logcat -c" >/dev/null 2>&1
hus "am start -n com.probe.hu/.MainActivity" >/dev/null 2>&1
sleep 6
now="$(mktemp)"
hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | hu_parse | sort -n -u > "$now"

if [ -n "$1" ] && [ -f "$1" ]; then
  d=$(join -t"$(printf '\t')" -j1 <(sort -k1,1 "$1") <(sort -k1,1 "$now") \
      | awk -F'\t' '$3!=$5 {printf "  %-5s %-26s %s -> %s\n",$1,$2,$3,$5}')
  [ -z "$d" ] && echo "CLEAN - all codes match baseline" || { echo "DEVIATIONS:"; echo "$d"; }
else
  cat "$now"
fi
rm -f "$now"
