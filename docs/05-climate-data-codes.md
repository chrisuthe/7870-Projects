# 5. Climate data codes

`FinalCanbus` defines **87** `U_AIR_*` constants. These are the **read** codes —
what you subscribe to with `register()` and receive through `update()`.

They are **car-agnostic**. `U_AIR_TEMP_LEFT` is 27 on a Jeep and 27 on a Sonata.
The 228 `Car_NNNN_*` classes handle translation down to actual CAN frames; your
code never sees that layer.

**These are not the codes you write to.** Sending one of these to `cmd()` is
accepted and silently discarded. See [6. Sending commands](06-command-index.md).

## Verification

Two codes were confirmed empirically before the table was trusted: tapping
fan-up on the OEM bar produced code **21**, and temp-up produced code **27**, at
timestamps matching the presses. `dexdump` then independently gave
`U_AIR_WIND_LEVEL_LEFT = 21` and `U_AIR_TEMP_LEFT = 27`. Two methods, same
answer.

## Values and sentinels

Values arrive in the callback's `int[]`. Most are 0/1 booleans or small
integers, but there are traps:

| Code | Normal range | Sentinel |
|---|---|---|
| `U_AIR_WIND_LEVEL_LEFT` (21) | 1–7 | **15 = AUTO** |
| `U_AIR_TEMP_LEFT` / `_RIGHT` (27/28) | °F, 1° steps | **-2 = minimum / LO** |
| `U_AIR_SEAT_HOT_*` (29/30) | 0–3 | — |
| `U_AIR_SEAT_BLOW_*` (31/32) | 0–3 | — |
| `U_AIR_TEMP_UNIT` (37) | 1 = Fahrenheit | — |

**Fan level 15 is not a fan speed.** When AUTO engages, `WIND_LEVEL_LEFT`
reports 15 — outside the 1–7 range the manual control uses. Render it as "AUTO"
or you will show your users a fan speed that doesn't exist.

**Temperature -2 is not below zero.** It's the minimum/LO sentinel, set by the
MAX A/C macro among others.

## Airflow is three flags, not an enum

The three airflow booleans are independent:

| | `BLOW_UP_LEFT` (18) | `BLOW_BODY_LEFT` (19) | `BLOW_FOOT_LEFT` (20) |
|---|:---:|:---:|:---:|
| face | 0 | **1** | 0 |
| face + feet | 0 | **1** | **1** |
| feet | 0 | 0 | **1** |
| feet + windshield | **1** | 0 | **1** |

The OEM bar's cycle button walks these four states in that order. Only four of
the eight representable combinations are reachable through it — a replacement UI
is not bound by that.

## Mutual exclusion

Seat heat and seat ventilation are mutually exclusive **in the protocol**, not
just in the UI. Setting `SEAT_HOT_LEFT` actively zeroes `SEAT_BLOW_LEFT` and
vice versa. Model that or your UI state will disagree with the vehicle.

Seat heat cycles **downward** from 3.

## Confirmed fitted on the WK2

Present in the table *and* responding on this vehicle, including several the OEM
bar does not expose:

`U_AIR_ACMAX` (MAX A/C) · `U_AIR_HOT_STEER` (heated steering wheel) ·
`U_AIR_SYNC` · both seat heaters · both seat ventilators · dual-zone temp ·
front and rear defrost · recirculate · AUTO · all three airflow flags

Not fitted: massage and lumbar seats (`U_AIR_MASSAGESEAT_*`,
`U_AIR_LUMBARSEAT_*`), rear climate zone.

## A label that lies

The OEM bar's **`DUAL` button drives `U_AIR_SYNC` (62)**, not `U_AIR_DUAL` (14).
Confirmed three ways: tapping it moves 62 while 14 never changes; the probe reads
`SYNC=1` while `DUAL=0`; and the OEM's own full-page view lights its `DUAL`
label when `SYNC` is 1.

Don't assume the on-screen label names the constant.

## Full table

Extracted from `FinalCanbus` in `com.syu.air.apk`. Also in
[`data/U_AIR_table.txt`](../data/U_AIR_table.txt).

| Code | Constant | Code | Constant |
|---|---|---|---|
| 10 | `U_AIR_POWER` | 54 | `U_AIR_AQS` |
| 11 | `U_AIR_AC` | 55 | `U_AIR_MONO` |
| 12 | `U_AIR_CYCLE` | 56 | `U_AIR_TRMP_SET_V` |
| 13 | `U_AIR_AUTO` | 57 | `U_AIR_ZONE` |
| 14 | `U_AIR_DUAL` | 58 | `U_AIR_ION` |
| 15 | `U_AIR_MAX_FRONT` | 59 | `U_AIR_REARVIEW_HOT` |
| 16 | `U_AIR_REAR_DEFROST` | 60 | `U_AIR_FULL_LEFT` |
| 17 | `U_AIR_FRONT_HOT` | 61 | `U_AIR_FULL_RIGHT` |
| 18 | `U_AIR_BLOW_UP_LEFT` | 62 | `U_AIR_SYNC` |
| 19 | `U_AIR_BLOW_BODY_LEFT` | 63 | `U_AIR_HEAT` |
| 20 | `U_AIR_BLOW_FOOT_LEFT` | 65 | `U_AIR_FRONT_DEFROST` |
| 21 | `U_AIR_WIND_LEVEL_LEFT` | 66 | `U_AIR_HOT_STEER` |
| 22 | `U_AIR_BLOW_UP_RIGHT` | 67 | `U_AIR_REAR_LOCK` |
| 23 | `U_AIR_BLOW_BODY_RIGHT` | 68 | `U_AIR_PTC` |
| 24 | `U_AIR_BLOW_FOOT_RIGHT` | 70 | `U_AIR_FAST` |
| 25 | `U_AIR_WIND_LEVEL_RIGHT` | 71 | `U_AIR_SOFT` |
| 26 | `U_AIR_AUTO_RIGHT` | 72 | `U_AIR_BLOW_HEAD` |
| 27 | `U_AIR_TEMP_LEFT` | 73 | `U_AIR_AUTO_WIN_LEV` |
| 28 | `U_AIR_TEMP_RIGHT` | 74 | `U_AIR_AUTO_WIN_BLOW` |
| 29 | `U_AIR_SEAT_HOT_LEFT` | 75 | `U_AIR_TEMP_TYPE` |
| 30 | `U_AIR_SEAT_HOT_RIGHT` | 76 | `U_AIR_CLEAN` |
| 31 | `U_AIR_SEAT_BLOW_LEFT` | 77 | `U_AIR_BLOW_MODE_LEFT` |
| 32 | `U_AIR_SEAT_BLOW_RIGHT` | 78 | `U_AIR_REAR_AC` |
| 33 | `U_AIR_FLOW_AUTO` | 79 | `U_AIR_FULL` |
| 34 | `U_AIR_NANOE` | 80 | `U_AIR_PARK` |
| 35 | `U_AIR_SWING` | 81 | `U_AIR_REAR_AUTO_RIGHT` |
| 36 | `U_AIR_CYCLE_AUTO` | 82 | `U_AIR_REAR_BLOW_BODY_RIGHT` |
| 37 | `U_AIR_TEMP_UNIT` | 83 | `U_AIR_REAR_BLOW_FOOT_RIGHT` |
| 38 | `U_AIR_REAR` | 84 | `U_AIR_REAR_BLOW_UP_RIGHT` |
| 39 | `U_AIR_REAR_DUAL` | 85 | `U_AIR_REAR_COOL` |
| 40 | `U_AIR_REAR_TEMP_LEFT` | 86 | `U_AIR_REAR_MANUAL` |
| 41 | `U_AIR_REAR_TEMP_RIGHT` | 87 | `U_AIR_BLOW_MODE_RIGHT` |
| 42 | `U_AIR_REAR_POWER` | 88 | `U_AIR_REAR_SEAT_HOT_LEFT` |
| 43 | `U_AIR_REAR_AUTO` | 89 | `U_AIR_REAR_SEAT_HOT_RIGHT` |
| 44 | `U_AIR_REAR_WIN_LEV` | 90 | `U_AIR_REAR_SEAT_BLOW_LEFT` |
| 45 | `U_AIR_REAR_VIEW_HOT` | 91 | `U_AIR_REAR_SEAT_BLOW_RIGHT` |
| 46 | `U_AIR_REAR_BLOW_BODY` | 92 | `U_AIR_FRONT_ONLY` |
| 47 | `U_AIR_REAR_BLOW_FOOT` | 93 | `U_AIR_MASSAGESEAT_LEFT` |
| 48 | `U_AIR_REAR_BLOW_UP` | 94 | `U_AIR_MASSAGESEAT_RIGHT` |
| 49 | `U_AIR_BLOW_AUTO_LEFT` | 95 | `U_AIR_LUMBARSEAT_LEFT` |
| 50 | `U_AIR_BLOW_AUTO_RIGHT` | 96 | `U_AIR_LUMBARSEAT_RIGHT` |
| 51 | `U_AIR_WIND_STRENGTH` | 1001 | `U_AIR_WINDOW_ENABLE` |
| 52 | `U_AIR_ECO` | 1014 | `U_AIR_CONTROL_PAGE` |
| 53 | `U_AIR_ACMAX` |  |  |
