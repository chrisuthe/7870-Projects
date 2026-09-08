# HU Probe

A minimal Android app that reads and writes the SYU/FYT vendor climate bus.

It exists to make the bus **observable**. Without it you are inferring vehicle
state from screenshots and another app's log output, which is how most of the
wrong conclusions in [wiki: Method and Mistakes](https://github.com/chrisuthe/7870-Projects/wiki/9-Method-and-Mistakes) happened.

- Binds `com.syu.ms.toolkit`, obtains module 7 (canbus)
- Subscribes to all 87 `U_AIR_*` codes — every state change is logged
- Exposes a write path over a broadcast, so you can drive it from a shell
- Can create test overlays, for the screen-space experiments in [wiki: Screen Space](https://github.com/chrisuthe/7870-Projects/wiki/7-Screen-Space)

**Read-only until you tell it otherwise.** It never sends a command on its own.

No Gradle, no dependencies, ~250 lines. Hand-written Binder proxies against the
transaction ids in [wiki: The IPC Framework](https://github.com/chrisuthe/7870-Projects/wiki/4-The-IPC-Framework).

## Build and install

```bash
./build.sh                # build, sign, install to the connected unit
./build.sh --no-install   # build only
```

It finds the SDK, picks the newest build-tools and platform, generates
`Names.java` from [`data/U_AIR_table.txt`](../data/U_AIR_table.txt), and
auto-connects via `adb mdns services`. Override with `ANDROID_SDK`,
`BUILD_TOOLS`, `PLATFORM`, `JAVA_HOME`, `KEYSTORE`, `DEVICE`.

## Use

```bash
adb shell am start -n com.probe.hu/.MainActivity
adb logcat -s HUPROBE:V
```

On start it dumps complete current climate state, because registering for a code
delivers its present value immediately:

```
I/HUPROBE: connected to com.syu.ms/app.ToolkitService
I/HUPROBE: module 7 acquired: android.os.BinderProxy@106eebc
I/HUPROBE: registered 87 codes, 0 failed (flag=1)
I/HUPROBE: U_AIR_POWER            code=10   ints=[1]
I/HUPROBE: U_AIR_AC               code=11   ints=[1]
I/HUPROBE: U_AIR_TEMP_LEFT        code=27   ints=[68]
I/HUPROBE: U_AIR_WIND_LEVEL_LEFT  code=21   ints=[5]
...
```

The activity also shows the log on screen, which is handy when you're sitting in
the car rather than at the laptop.

### Sending a command

```bash
# cmd(6, {v0, v1}) on module 7 -- see docs/06
adb shell am broadcast -a com.probe.hu.CMD --ei code 6 --ei v0 1 --ei v1 1
```

`code` is the command code (always 6 for climate), `v0`/`v1` are the payload
array, `n` sets array length (default 2).

> `v1` is the per-vehicle command index. The table in
> [wiki: Sending Commands](https://github.com/chrisuthe/7870-Projects/wiki/6-Sending-Commands) is for a **Jeep Grand Cherokee WK2**.
> On another vehicle, sweep and build your own.

### Overlay experiments

```bash
adb shell appops set com.probe.hu SYSTEM_ALERT_WINDOW allow
adb shell am broadcast -a com.probe.hu.OVERLAY --ei h 400 --ei type 2038
adb shell am broadcast -a com.probe.hu.OVERLAY --ei h 0   --ei type 2038  # remove
```

`type` 2038 = `TYPE_APPLICATION_OVERLAY`, 2032 = `TYPE_ACCESSIBILITY_OVERLAY`,
2019 = `TYPE_NAVIGATION_BAR` (refused for a normal app — that refusal is itself
the finding).

### Recording (for driving captures)

Wireless debugging is unusable on the move — Android ties it to an active Wi-Fi
connection, so it stops the moment you leave the driveway. Tailscale doesn't
help; the toggle itself goes away. So the probe records to the unit's own
storage instead, and you pull the file afterwards.

```bash
adb shell pm grant com.probe.hu android.permission.ACCESS_FINE_LOCATION  # once
adb shell am broadcast -a com.probe.hu.RECORD --ei on 1                  # start
#   ... drive ...
adb shell am broadcast -a com.probe.hu.RECORD --ei on 0                  # stop
adb pull /sdcard/Android/data/com.probe.hu/files/
```

Or use [`scripts/hu-record.sh`](../scripts/hu-record.sh) `start` / `stop` / `pull`.

Output is TSV in the app's own external directory — reachable by `adb pull`
with no storage permission and no root:

```
# ms      source  code  name              len  value
1757...   CANBUS  1019  U_CANBUS_FRAME..  13   <2E2902E414CC>13
1757...   GPS     -1    speed_mps         1    12.4
```

Two deliberate details:

- **GPS velocity is recorded alongside the frames.** The unit has its own
  receiver, so a driving capture carries its own speed ground truth — decoding
  road speed becomes a correlation against a known reference rather than
  guesswork.
- **The raw array length is recorded per row.** That should settle whether the
  progressive-prefix frames are genuine vendor behaviour or a logging artifact.

While recording, per-update logcat output is suppressed — at ~26 frames/sec it
would otherwise bury the log and cost throughput. Roughly 45 MB/hour.

> **Not yet tested on hardware.** It compiles and installs; the vehicle was
> unavailable when it was written. Verify with a short stationary recording
> before relying on it for a drive.

## Manifest notes

Two things are load-bearing:

```xml
<queries><package android:name="com.syu.ms" /></queries>
```

Without this, `bindService` fails silently on Android 11+ even though the
service is exported.

```xml
<uses-sdk android:targetSdkVersion="33" />
```

The receivers are registered with `RECEIVER_EXPORTED` on SDK 33+, which is
required for `am broadcast` to reach them.

## Safety

- Read-only unless you send a `CMD` broadcast.
- Take a baseline before experimenting and diff against it afterwards —
  [`scripts/hu-state.sh`](../scripts/hu-state.sh) does this.
- Don't experiment on a moving vehicle. `scripts/hu-connect.sh` reports GPS
  velocity for exactly this reason.
- `v1=16` turns the climate system off.
