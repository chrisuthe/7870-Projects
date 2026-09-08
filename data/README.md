# Extracted data

| File | Source | Scope |
|---|---|---|
| `U_AIR_table.txt` | `FinalCanbus` (climate), via `dexdump` | universal |
| `U_CANBUS_table.txt` | `FinalCanbus` (non-climate) | universal |
| `U_MAIN_table.txt` | `FinalMain` — module 0 | universal |
| `U_SOUND_table.txt` | `FinalSound` — module 4 | universal |
| `U_STEER_table.txt` | `FinalSteer` — module 10 | universal |
| `command-index-wk2.tsv` | live sweep, second pass | **WK2 only** |
| `mcu-frames-wk2.md` | decoded from the raw frame stream | **WK2 only** |

"Universal" means the constant is defined identically across all 228 vehicle
profiles. It does **not** mean your car populates it — see
[wiki: Beyond Climate](https://github.com/chrisuthe/7870-Projects/wiki/10-Beyond-Climate).

`U_AIR_table.txt` is `NAME=code`, one per line. `probe/build.sh` generates
`Names.java` from it at build time, so it is the single source of truth for the
read codes.

The command index table is per-vehicle. To build one for your car, see the
bottom of [wiki: Sending Commands](https://github.com/chrisuthe/7870-Projects/wiki/6-Sending-Commands).

## Re-extracting the read codes yourself

```bash
adb pull /odm/app/190000000_com.syu.air/190000000_com.syu.air.apk    # use PowerShell on Windows
unzip -o com.syu.air.apk classes.dex
$ANDROID_SDK/build-tools/*/dexdump -d classes.dex > air.txt

awk '/\(in Lcom\/syu\/ipc\/data\/FinalCanbus;\)/{f=1;name="";next}
     f&&/name +: /{gsub(/.*: .|.$/,"");name=$0}
     f&&/value +: /{gsub(/.*: /,"");if(name ~ /^U_AIR_/) printf "%s=%s\n",name,$0; f=0}' air.txt \
  | sort -t= -k2 -n
```

`dexdump` ships with the Android SDK build-tools — no jadx or apktool required.
