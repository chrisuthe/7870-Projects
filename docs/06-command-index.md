# 6. Sending commands

## The recipe

```java
IRemoteModule canbus = toolkit.getRemoteModule(7);
canbus.cmd(6, new int[]{ 1, N }, null, null);
```

- module **7** — canbus
- command code **6** — fixed, always
- payload `{ 1, N }` — `N` is a small **per-vehicle command index**

Verified live: `cmd(6, {1, 1})` toggles A/C, observed as `U_AIR_AC` moving
`1 → 0 → 1`.

## Why the obvious approach fails silently

The natural assumption is that you write to the same code you read from — send
`27` to set `U_AIR_TEMP_LEFT`. **That does not work, and it does not error.**

Eight variations were tried across three codes with two different array shapes.
Every one returned from Binder with no exception and changed nothing. That
behaviour looks exactly like a permission problem, which sent this project down
a long wrong path (see [9. Method and mistakes](09-method-and-mistakes.md)).

The actual explanation is that **there are two non-overlapping code spaces**:

| | Space | Range | Universal? |
|---|---|---|---|
| **Read** | `U_AIR_*` data ids | 10–96 | yes, all vehicles |
| **Write** | command indices | 1–25 | **no, per vehicle** |

Sending data-id 27 to `cmd()` is a well-formed request naming action #27 in a
space where actions number in the teens. Accepted, discarded, no error.

## Where the recipe came from

`Car_0374_PA_Jeep_All.sendCmd(int)`, disassembled:

```
const/4 v1, #int 7        ; module = 7 (canbus)
const/4 v2, #int 6        ; code   = 6 (fixed)
const/4 v3, #int 2
new-array v3, v3, [I      ; int[2]
aput v5, v3, v4           ; array[0] = 1
aput v7, v3, v5           ; array[1] = the sendCmd argument
invoke-virtual RemoteTools.sendInt(II[I)V
```

`RemoteTools` is the vendor's client wrapper around the AIDL; `sendInt(module,
code, values)` is `getRemoteModule(module).cmd(code, values, null, null)`.

---

## Command index table — Jeep Grand Cherokee WK2

**These indices are vehicle-specific.** They come from
`Car_0374_PA_Jeep_All`. A different vehicle profile will have a different table.
The method for building your own is at the bottom of this page.

| N | Function | Notes |
|---|---|---|
| 1 | A/C | toggle |
| 2 | AUTO on | **macro** — also sets A/C, clears body-blow, fan → 15 |
| 3 | Recirculate | toggle |
| 4 | Temp **left** up | 1 °F per press |
| 5 | Temp **left** down | |
| 6 | **Fan up** | exits AUTO as a side effect |
| 7 | Fan down | |
| 8 | Airflow → **face** | idempotent |
| 9 | Airflow → **face + feet** | idempotent |
| 10 | Airflow → **feet** | idempotent |
| 11 | Airflow → **feet + windshield** | idempotent |
| 12 | Front defrost | **macro** — forces recirc on, fan → 6 |
| 13 | SYNC | toggle (the button labelled DUAL) |
| 14 | Rear defrost | toggle |
| 15 | MAX A/C | **macro** — temps → -2 sentinel |
| 16 | **Climate power** | toggle — see warning below |
| 17 | Seat heat left | cycles down from 3 |
| 18 | Seat heat right | cycles down from 3 |
| 19 | *(no effect)* | unmapped or not fitted |
| 20 | Temp **right** up | |
| 21 | Temp **right** down | |
| 22 | Seat vent left | cycles |
| 23 | Seat vent right | cycles |
| 24 | Heated steering wheel | toggle |
| 25 | Airflow → feet | duplicate of 10 |
| 26–48 | *(no effect)* | command space ends at 25 |

### Warnings

**`N=16` turns the climate system off.** On a vehicle with no physical HVAC
controls that leaves no way to change anything until you send it again. The
system wakes on any control press, which makes this easy to misdiagnose.

**`N=12` and `N=15` are macros that toggle into each other**, each forcing
recirculation on and temperatures to the `-2` sentinel. Unwinding them through
`cmd()` takes several rounds. Tap injection on the airflow button
(`input tap 545 1793` on this panel) clears the state in one action.

For a replacement UI, prefer the **discrete flags (8/9/10/11)** over the macros.

### The airflow group is the nice part

`8`, `9`, `10`, `11` are **idempotent mode setters**, not toggles — press one
twice and the second press does nothing. All four airflow modes are directly
addressable.

A replacement bar therefore never needs to reproduce the OEM's four-step cycle:
four buttons, each lit when its mode is active, each a single idempotent
command. Less code and better UX than what ships.

---

## Building the table for your vehicle

1. Install [the probe](../probe) and subscribe to all climate codes.
2. Sweep: for `N` in 1..30, send `cmd(6, {1, N})` and record which `U_AIR_*`
   codes change.
3. **Do a second pass.** This is not optional — see below.
4. Restore state by diffing against a baseline captured before you started.

[`scripts/hu-sweep.sh`](../scripts/hu-sweep.sh) does steps 1–2.

### Why the second pass matters

A single-pass sweep samples each index in whatever state the *previous* index
left behind. On this vehicle that produced **three wrong labels** that survived
until they were re-tested from a known state:

| N | First pass said | Actually |
|---|---|---|
| 6 | "AUTO off" | **fan up** — it exits AUTO as a side effect |
| 20 | "climate power on" | **temp right up** — it woke a system N=16 had switched off |
| 8 | "no effect" | **airflow → face** — feet was already 0, so nothing visibly changed |

Every one of those is the same error: recording a side effect, or a no-op caused
by the starting state, as the function. Re-test each index from a known baseline
before you trust the table.
