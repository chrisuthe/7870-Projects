#!/usr/bin/env bash
# Sweep command indices and record what each one changes.
#   ./hu-sweep.sh [from] [to]      default 1..25
#
# WARNING: this writes to a live vehicle. Park it. Take a baseline first:
#   ./hu-state.sh > base.tsv
# and restore afterwards:
#   ./hu-state.sh base.tsv
#
# Results from a single pass are HYPOTHESES. Each index is sampled in whatever
# state the previous index left behind, which produces confident wrong answers.
# Re-test every entry from a known baseline before believing it. See docs/06.
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1
from="${1:-1}"; to="${2:-25}"
echo "sweeping N=$from..$to   (cmd(6,{1,N}) on module 7)"
for N in $(seq "$from" "$to"); do
  hus "logcat -c" >/dev/null 2>&1
  hus "am broadcast -a com.probe.hu.CMD --ei code 6 --ei v0 1 --ei v1 $N" >/dev/null 2>&1
  sleep 2
  d=$(hus "logcat -d -v brief -s HUPROBE:V" 2>/dev/null | hu_parse \
      | awk -F'\t' '{sub(/^U_AIR_/,"",$2); print $2"="$3}' | sort -u | paste -sd' ')
  printf '  N=%-3s -> %s\n' "$N" "${d:-—}"
done
