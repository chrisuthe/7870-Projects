# Documentation

Reverse-engineering a FYT / SYU UIS7870 head unit, from "I can't get into
developer options" to "I can set the cabin temperature from my own code".

## Read in order

1. **[The unit](01-the-unit.md)**
   Identifying the SoC, firmware and vendor stack. What `uis7870sc_2h10_nosec`
   actually tells you, and what it doesn't.

2. **[Getting access](02-getting-access.md)**
   Developer options behind an unknown 4-digit code. Wireless debugging, the
   port that changes every reboot, and why `adb mdns services` is the answer.

3. **[What draws the bar](03-what-draws-the-bar.md)**
   The bottom bar is not SystemUI and not the launcher. Reading the window
   stack to find the real owner, and why that determines everything after.

4. **[The IPC framework](04-the-ipc-framework.md)**
   `IRemoteToolkit` / `IRemoteModule` / `IModuleCallback`, the module id table,
   and how to bind to it from an ordinary app.

5. **[Climate data codes](05-climate-data-codes.md)**
   The 87 `U_AIR_*` constants, what they mean, and the sentinel values that
   will bite you if you treat them as plain numbers.

6. **[Sending commands](06-command-index.md)**
   The write recipe, why the obvious approach fails silently, and the full
   per-vehicle command index table.

7. **[Screen space](07-screen-space.md)**
   Where the 227px comes from, what a third-party app is allowed to draw, and
   the three shapes a replacement bar can take.

8. **[Root](08-root.md)**
   What the community procedure requires, what the risks are, and the honest
   cost/benefit — which is smaller than you'd expect.

9. **[Method and mistakes](09-method-and-mistakes.md)**
   How the findings were actually arrived at, including four wrong conclusions
   that survived for a while. Probably the most useful page if you're doing
   this on a different unit.

## Conventions

- **Verified** means observed on live hardware, usually twice by different means.
- **Inferred** means read from bytecode or reasoning, not confirmed on device.
- Anything vehicle-specific is marked **WK2** — the command indices in
  particular are per-vehicle and will differ on your car.
- Pixel coordinates assume the 1080x1920 portrait panel at 160dpi (density 1.0,
  so dp and px are interchangeable).
