# Shared helpers. Source this, don't run it.
# ADB is located from ANDROID_SDK / ANDROID_HOME, or PATH.
if [ -z "$ADB" ]; then
  for c in "$ANDROID_SDK" "$ANDROID_HOME" "$HOME/AppData/Local/Android/Sdk" \
           "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    [ -n "$c" ] && [ -x "$c/platform-tools/adb" ]     && ADB="$c/platform-tools/adb"     && break
    [ -n "$c" ] && [ -x "$c/platform-tools/adb.exe" ] && ADB="$c/platform-tools/adb.exe" && break
  done
  [ -z "$ADB" ] && ADB="$(command -v adb)"
fi
[ -n "$ADB" ] || { echo "adb not found; set ADB or ANDROID_SDK" >&2; exit 1; }

# Resolve the unit's current wireless-debugging address via mDNS.
# The port changes on every service restart; never hardcode it.
hu_target() {
  [ -n "$DEVICE" ] && { echo "$DEVICE"; return; }
  "$ADB" mdns services 2>/dev/null | grep '_adb-tls-connect' | awk '{print $3}' | head -1
}

hu_connect() {
  local t; t="$(hu_target)"
  [ -n "$t" ] || { echo "no unit advertising; is wireless debugging on?" >&2; return 1; }
  "$ADB" connect "$t" >/dev/null 2>&1
  echo "$t"
}

# adb shell against the unit
hus() { "$ADB" -s "$TARGET" shell "$@"; }

# Parse HUPROBE log lines into: code<TAB>name<TAB>value
hu_parse() {
  tr -d '\r' | awk 'match($0,/(U_[A-Z0-9_]+|code_[0-9]+) +code=([0-9]+) +ints=\[([^]]*)\]/){
      s=substr($0,RSTART,RLENGTH); split(s,a," "); n=a[1]; sub(/code=/,"",a[2]);
      v=s; sub(/.*ints=\[/,"",v); sub(/\].*/,"",v); print a[2]"\t"n"\t"v }'
}
