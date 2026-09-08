#!/usr/bin/env bash
# Connect to the unit and report identity + whether it is safe to experiment.
set -e
. "$(dirname "$0")/_common.sh"
TARGET="$(hu_connect)" || exit 1
echo "connected: $TARGET"
for p in ro.product.model ro.board.platform ro.build.version.release \
         ro.build.version.incremental; do
  printf '  %-32s %s\n' "$p" "$(hus getprop $p | tr -d '\r')"
done
echo
echo "vehicle motion (do not experiment if non-zero):"
hus "logcat -c; timeout 5 logcat -v brief -s Gps:V" 2>/dev/null \
  | tr -d '\r' | grep -oE 'vel=[0-9.]+' | tail -2 | sed 's/^/  /' || echo "  no GPS fix"
