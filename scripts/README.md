# Scripts

Shell helpers for driving a UIS7870 unit over ADB. All of them locate `adb` from
`ANDROID_SDK` / `ANDROID_HOME` / `PATH`, and resolve the unit's address via
`adb mdns services` — **never hardcode the wireless-debugging port**, it changes
on every service restart.

Override the target with `DEVICE=<ip>:<port>` if you're on USB or mDNS isn't
working.

Most of these expect [HU Probe](../probe) installed and its activity started —
it's the thing that makes the bus visible.

| Script | What it does |
|---|---|
| `hu-connect.sh` | Connect, print unit identity, **report GPS velocity** |
| `hu-state.sh` | Dump full climate state, or diff against a saved baseline |
| `hu-cmd.sh <N>` | Send one command index, report what changed |
| `hu-sweep.sh [from] [to]` | Sweep a range of command indices |
| `hu-tap.sh <x> <y>` | Inject a tap, report what changed |

## The workflow that keeps a car safe

```bash
./hu-connect.sh                 # confirm identity, confirm vel=0.0
./hu-state.sh > base.tsv        # baseline BEFORE touching anything
./hu-cmd.sh 1                   # experiment
./hu-state.sh base.tsv          # diff -- shows exactly what drifted
# ... restore ...
./hu-state.sh base.tsv          # verify CLEAN before you walk away
```

`hu-state.sh` with a baseline prints either `CLEAN - all codes match baseline`
or a list of deviations with old and new values. Don't finish a session on
anything other than CLEAN.

## Warnings

- **`hu-sweep.sh` writes to a live vehicle.** Park it. `N=16` turns the climate
  system off; on a car without physical HVAC controls that leaves no way to
  change anything until you send it again.
- **Single-pass sweep results are hypotheses, not findings.** Each index is
  sampled in whatever state the previous one left behind. That produced three
  wrong labels on this vehicle. Re-test from a known baseline —
  see [wiki: Sending Commands](https://github.com/chrisuthe/7870-Projects/wiki/6-Sending-Commands).
- **Command indices are per-vehicle.** The table in the docs is for a Jeep
  Grand Cherokee WK2.
- `hu-tap.sh` is often *better* than `hu-cmd.sh` for unwinding the
  defrost / MAX-A-C macros, which toggle into each other and are awkward to
  reverse through the command interface.
