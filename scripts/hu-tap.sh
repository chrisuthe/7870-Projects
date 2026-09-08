#!/usr/bin/env bash
# Inject a tap and report which codes changed. Useful for mapping the OEM bar,
# and more predictable than cmd() for unwinding the defrost/MAX-AC macros.
#   ./hu-tap.sh <x> <y>
# Bar coordinates for a 1080x1920 panel are in docs/03.
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1
[ -n "$2" ] || { echo "usage: $0 <x> <y>"; exit 1; }
hus "logcat -c" >/dev/null 2>&1
hus "input tap $1 $2" >/dev/null 2>&1
sleep 2
hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | hu_parse \
  | awk -F'\t' '{printf "  %s = %s\n",$2,$3}' | sort -u
