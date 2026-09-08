#!/usr/bin/env bash
# Send one climate command and report which codes changed.
#   ./hu-cmd.sh <N>     where N is the per-vehicle command index (see docs/06)
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1
[ -n "$1" ] || { echo "usage: $0 <command-index>"; exit 1; }
hus "logcat -c" >/dev/null 2>&1
hus "am broadcast -a com.probe.hu.CMD --ei code 6 --ei v0 1 --ei v1 $1" >/dev/null 2>&1
sleep 2
hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | hu_parse \
  | awk -F'\t' '{printf "  %s = %s\n",$2,$3}' | sort -u
