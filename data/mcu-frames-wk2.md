# MCU frame reference — Jeep Grand Cherokee WK2

Frames observed on `U_CANBUS_FRAME_TO_UI` (module 7, code 1019). This is the
raw MCU serial stream surfaced at the app layer — no root, no access to
`/dev/ttyS*` required.

**Vehicle-specific.** Message ids and payload layouts will differ on another car.

## Frame format

```
2E <id> <len> <payload…> <cksum>
│   │     │                └── checksum: byte sum is constant modulo 256
│   │     └── payload length in bytes
│   └── message id
└── 0x2E start marker
```

The checksum is **mod 256**, not a fixed total. Message `2E29` produced sums of
468, 212 and 724 across a capture — all ≡ 212 (mod 256). An early read of
"constant sum" was a small-sample artifact.

Not every delivered array starts with `0x2E`; see [open questions](#open-questions).

## Decoded

### `2E 29` — steering angle · **confirmed**

```
2E 29 02 <lo> <hi> <cksum>
         └────┴── little-endian signed int16, 0.1° per LSB
```

Verified against three sustained positions on a stationary vehicle:

| Held position | Raw | Decoded |
|---|---|---|
| full left | `4B EB` | **−530.1°** |
| centre | `10 00` | **+1.6°** |
| full right | `E4 14` | **+534.8°** |

Symmetric to ±~535°, ≈1.5 turns each way — correct for a WK2.

**The named code `U_STEER_ANGLE` (MAIN, code 41) does not carry this.** It fired
93 times during the same lock-to-lock sweep and reported `[80, 160, 80]` every
time. It is a static descriptor, not an angle.

## Reverse-related · **candidates**

These appear or grow **only** while `U_BACKCAR = 1`. Layouts not yet confirmed.

### `2E 28` — likely a distance

```
2E 28 04 00 00 <v> 02 <cksum>       byte sum ≡ 211
```

`<v>` climbed `0x69 → 0x7B → 0x82 → 0x8C` (105 → 123 → 130 → 140) across a
ten-second reverse hold. Monotonic and smooth — consistent with a distance or
position, and notably the named `U_RADAR_*` codes reported a flat `10` for the
whole window.

### `2E 5A` — gains a 4-byte block in reverse

Outside reverse the frame ends after `…B3 40`. In reverse it carries four more
bytes whose sum is constant at 360:

```
… B3 40  08 80 01 DF        08+80+01+DF = 360
… B3 40  09 80 01 DE
… B3 40  0A 80 01 DD
… B3 40  0B 80 01 DC
```

### `2E 40`, `2E 52`

`2E40` appears only in reverse. `2E52` lengthens substantially. Not analysed.

## Activity summary

Distinct payloads per message id, idle baseline vs. driver actions. A message
that is frozen at rest and varied during an action is a candidate for that
signal.

| id | idle | steering sweep | vehicle actions |
|---|---|---|---|
| `2E29` | 1 | **67** | 4 |
| `2E28` | 1 | 18 | **16** (reverse only) |
| `2E52` | 23 | 138 | 60 |
| `2E5A` | 6 | 14 | **45** (reverse only) |
| `2E4D` | 6 | 5 | 12 |
| `2E30` | 2 | 2 | 4 |
| `2E14` `2E17` `2E21`–`2E27` `2E4E` `2E4F` `2E51` `2E53`–`2E59` | 1 | 1 | 1–4 |

At idle with no driver input the bus is **almost entirely frozen** — most ids
emit a single unchanging payload. That makes correlation a set difference
rather than a statistical exercise.

## Method

1. **Capture an idle baseline.** Engine running, nobody touching anything.
2. **Capture with sustained states.** Hold each position ~10s with clean pauses
   between. Holds beat sweeps — three stable plateaus make the correlating byte
   obvious where continuous motion smears it.
3. **Diff distinct payloads per message id.** Frozen at rest, varied during the
   action = candidate.
4. **Search byte order.** Try big- and little-endian, signed and unsigned. Plot
   the value over time and look for the shape you drove.

## Open questions

- **Progressive-prefix frames.** Some arrays arrive as prefixes of one another
  (`2E5A090140`, `2E5A09014000`, `2E5A0901400000`, …). Logcat truncation was
  eliminated by switching to hex, so this is either genuine vendor behaviour —
  partial arrays as the frame assembles — or a second truncation not yet found.
  Logging the raw `int[]` length alongside each frame should settle it.
- **Arrays not starting with `0x2E`** (`0037800064`, `0280280037800062`) look
  like left-truncations of the same data. Same root cause, presumably.
- **Only 2 of 8 radar sensors** reported (`U_RADAR_FMR`, `U_RADAR_FR`, both
  `10`). Either only sensors detecting something report, or the rest are carried
  in the raw frames only.
- **Road speed** — `U_CUR_SPEED` never fired stationary. Needs a driving
  capture; correlate frame bytes against the head unit's own GPS velocity as
  ground truth.
