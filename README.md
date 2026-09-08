# 7870-Projects

Reverse-engineering notes and tooling for **FYT / SYU aftermarket Android head units**
built on the Unisoc **UIS7870** (`ums9620`) platform.

The work here was done on a 13.6" vertical "Tesla style" unit fitted to a
**Jeep Grand Cherokee WK2**, sold under the Phoenix Automotive brand. Most of it
should apply to any FYT UIS7870 unit; the parts that are vehicle-specific are
marked as such.

Everything documented here was **verified on live hardware**. Where something is
inferred rather than proven, it says so.

---

## What was actually achieved

| | Status |
|---|---|
| Full read access to the vehicle climate bus from an ordinary app | **working** |
| Full write access — set temperature, fan, A/C, seat heaters, etc. | **working** |
| Root required? | **no** |
| Every control on the OEM bar mapped to its protocol codes | **done** |
| Replace the OEM bottom bar with your own UI | **feasible, not built** |
| Enlarge the space the bar occupies | **not possible without root** |

The headline result: **the vendor's climate bus is reachable from a normal
third-party app with no root, no special permissions, and no system signature.**

```java
IRemoteModule canbus = toolkit.getRemoteModule(7);   // module 7 = canbus
canbus.register(callback, 27, 1);                    // subscribe to temp
canbus.cmd(6, new int[]{ 1, 4 }, null, null);        // temp left up
```

---

## Start here

**[Documentation index →](docs/README.md)**

The docs are written to be read in order, but each stands alone:

| | |
|---|---|
| [1. The unit](docs/01-the-unit.md) | Identifying the hardware and firmware |
| [2. Getting access](docs/02-getting-access.md) | ADB on a unit with locked developer options |
| [3. What draws the bar](docs/03-what-draws-the-bar.md) | It isn't SystemUI, and that matters |
| [4. The IPC framework](docs/04-the-ipc-framework.md) | The SYU AIDL surface and module ids |
| [5. Climate data codes](docs/05-climate-data-codes.md) | The 87 `U_AIR_*` read codes |
| [6. Sending commands](docs/06-command-index.md) | The write recipe and the command table |
| [7. Screen space](docs/07-screen-space.md) | Why the bar is 227px and why you can't grow it |
| [8. Root](docs/08-root.md) | What it costs, what it buys |
| [9. Method and mistakes](docs/09-method-and-mistakes.md) | How this was found, including the wrong turns |

## Repo layout

```
docs/       the write-up
probe/      HU Probe - an Android app that reads and writes the climate bus
scripts/    shell helpers for driving a unit over ADB
data/       extracted constant tables
```

## Quick start

```bash
# find the unit (wireless debugging must be on)
adb mdns services
adb connect <ip>:<port>

# build and install the probe
cd probe && ./build.sh

# watch the climate bus
adb logcat -s HUPROBE:V
```

See [probe/README.md](probe/README.md) for the app, and
[scripts/README.md](scripts/README.md) for the helpers.

## Scope and safety

This touches the climate control of a moving vehicle. Everything here was done
on a **stationary, parked car**. The probe app defaults to read-only; writes
happen only when you explicitly send one.

Two commands are worth knowing before you experiment:

- `N=16` turns the climate system **off**. On a vehicle with no physical HVAC
  controls, that leaves no way to change anything until you turn it back on.
- `N=12` and `N=15` are macros that toggle into each other and force
  recirculation on and temperatures to a minimum sentinel. Messy to unwind.

## Licence

Apache 2.0. See [LICENSE](LICENSE).

Credit for the root procedure referenced in [docs/08-root.md](docs/08-root.md)
belongs to the XDA and 4PDA communities; it is linked, not reproduced.
