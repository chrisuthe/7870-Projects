# 1. The unit

## Identification

The single most useful command:

```bash
adb shell getprop ro.build.description
```

On the unit this work was done on:

```
uis7870sc_2h10_nosec-user 13 TP1A.220624.014 eng.ls.20260508.144045 release-keys
```

Broken down:

| Property | Value | Meaning |
|---|---|---|
| `ro.board.platform` | `ums9620` | Unisoc UMS9620 — the UIS7870 silicon |
| `ro.product.model` | `uis7870sc_2h10_nosec` | FYT board designation |
| `ro.product.brand` | `UNISOC` | not the reseller's brand |
| `ro.build.version.release` | `13` | Android 13, SDK 33 |
| `ro.build.version.incremental` | `eng.ls.20260508.144045` | **firmware date — matters for root** |
| panel | 1080x1920, 160dpi | portrait, density 1.0 |

Retail branding (Phoenix Automotive, Navifly, NaviRider, Joying, Dasaita, …) is
applied by resellers on top of the same FYT hardware. Ignore it and go by
`ro.board.platform` and `ro.product.model`.

### Reseller spec names

Sellers list the chipset under invented names. Observed mappings:

| Listing name | Actual part |
|---|---|
| `SC7870` | Unisoc UIS7870 (`ums9620`) — this platform |
| `S7865` | different lineage, different root tooling |
| `T9` | entry-level, least documented |

## About `nosec`

The model string contains `nosec`, which looks like it should mean
"non-secure boot". **It does not straightforwardly mean that.** The device
reports:

```
ro.boot.flash.locked      = 1
ro.boot.verifiedbootstate = green
```

Bootloader locked, verified boot in the green state.

However — and this took a while to establish — the community root procedure
flashes an unsigned boot image via `fastboot flash boot_a` with **no unlock
step at all**, and it works. So the lock state is advisory on this platform:
fastboot will accept an arbitrary boot image regardless.

Treat `flash.locked=1` here as **not** meaning "you cannot flash". See
[8. Root](08-root.md).

## The vendor software stack

Everything vendor-specific is under two package prefixes, `com.syu.*` (SYU) and
`com.fyt.*` (FYT). The ones that matter:

| Package | UID | Location | Role |
|---|---|---|---|
| `com.syu.ms` | 1000 | `/data/app` | **the main service** — hosts the IPC toolkit |
| `com.syu.ss` | 1000 | `/odm/app` | secondary service, keep-alive |
| `com.syu.canbus` | u0a71 | `/data/app` | ~90 MB vehicle protocol database |
| `com.syu.air` | 1000 | `/odm/app` | **draws the climate bar** |
| `com.syu.fytgesture` | 10080 | `/odm/app` | edge-swipe gesture accessibility service |
| `com.fyt.screenbutton` | u0a71 | `/odm/app` | physical button handling |
| `com.autolauncher.motorcar` | — | `/data/app` | the launcher (third-party, replaceable) |

`com.syu.canbus` and `com.fyt.screenbutton` share UID `u0a71`, so they share a
signing key.

`com.syu.air` declares `android:sharedUserId="android.uid.system"` — it is
platform-signed. You cannot modify and reinstall it without the platform key.

### The MCU link

The head unit does not speak CAN directly. A separate microcontroller bridges
the vehicle bus and talks to Android over a serial line:

```
$ adb shell ls -la /dev/ttyS*
crw-rw---- 1 system system 511, 0 /dev/ttyS0
crw-rw-rw- 1 system system 511, 1 /dev/ttyS1
crw-rw---- 1 system system 511, 2 /dev/ttyS2
crw-rw---- 1 system system 511, 3 /dev/ttyS3
```

`ttyS0` and `ttyS2` are the active links and are `system:system`. `adb shell`
runs as `shell`, which is not in that group — **you cannot tap the serial line
directly without root.** The vendor IPC framework is the only way in, which is
why the rest of this documentation is about that framework rather than about
CAN frames.

## Vehicle profile

`com.syu.air` ships **228** per-vehicle classes named
`com.syu.air.canbus.Car_NNNN_XXX_Model`. The profile ids live in `FinalCanbus`
as `CAR_*` constants. For this vehicle:

```
CAR_PA_GrandCherokee_14_22 = 2687350
```

Related Jeep entries: `CAR_PA_Cherokee_14_22`, `Car_0374_PA_Jeep_Wrangler`,
`Car_0372_WC_Jeep_Zhinanzhe`.

**You do not need to know your profile id to read the bus** — see
[5. Climate data codes](05-climate-data-codes.md) — but you do need to know your
*vehicle* to interpret the command indices in
[6. Sending commands](06-command-index.md).
