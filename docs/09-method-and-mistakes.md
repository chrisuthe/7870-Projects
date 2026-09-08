# 9. Method and mistakes

If you're doing this on a different unit, this is probably the most useful page.
The findings elsewhere are specific to one board and one car; the errors below
are the ones you'll repeat.

## The method that worked

1. **Read the window stack before touching anything.** `dumpsys window windows`
   answers "what am I actually fighting" in one command, and the answer
   determines whether the job is an afternoon or a firmware project.
2. **Correlate live behaviour with static analysis.** Every important finding
   here was confirmed twice — once by watching the device react, once by reading
   bytecode. Neither alone was reliable.
3. **Build the instrument before doing the survey.** Most of the wasted effort
   happened while inferring state from screenshots and one app's log output.
   Once a purpose-built probe subscribed to all 87 codes, mapping the entire
   panel took twenty minutes with complete visibility.
4. **Baseline, change, diff, restore.** Capture full state, make one change,
   diff, restore, verify clean. On someone's car this isn't optional.

## The mistakes

### Watching the wrong process

`logcat` showed a `D/canbus` tag ticking over with `onRefresh updateCode = 1049`
and nothing else, through the entire vehicle-state range. Reasonable conclusion:
the bus is idle.

Wrong. That tag belongs to `com.fyt.screenbutton`, which had subscribed to
exactly one code. It was one app's heartbeat, not the bus. The real traffic was
invisible because no process we were watching had registered for it.

**Lesson:** logcat shows you what some app chose to log. Absence of evidence in
another process's log is not evidence of absence.

### "No code" is not "no effect"

While probing buttons through the same keyhole, a tap on A/C produced no log
output — and silently turned the A/C on. It was only caught by diffing
screenshots.

**Lesson:** if your observation channel is incomplete, you cannot distinguish
"nothing happened" from "something happened that I can't see". Stop probing and
fix the instrument.

### Reading state from pixels

A/C was read as "off" from a dim on-screen label. The probe later reported
`U_AIR_AC = 1`. Icon brightness on that bar does not mean what it appears to.

**Lesson:** ground truth comes from the protocol, not the rendering.

### Trusting a property over the window stack

`qemu.hw.mainkeys=0` was read as "SystemUI owns the navigation bar", which would
have made this a SystemUI-replacement project. The property only means a
navigation-bar *slot* exists. A vendor app had taken it.

**Lesson:** check the owner, not the capability flag.

### A silent failure that looked like a permission problem

Writes to `cmd()` returned cleanly from Binder and changed nothing. That
signature — accepted, no exception, no effect — reads exactly like a service
silently dropping unprivileged callers, and that hypothesis held for a long
time. It was wrong.

The real cause was a **namespace error**: read codes and write codes are
different, non-overlapping spaces, and a data id passed to `cmd()` names an
action that doesn't exist. Settling it required disassembling the service's
`onTransact` to prove there was no caller check at all.

**Lesson:** "accepted but ineffective" points at *addressing*, not
*authorisation*. Check what you're naming before you conclude you're not allowed.

### A single-pass sweep produces confident wrong answers

Sweeping command indices 1–48 once, in sequence, gave a clean-looking table with
**three wrong entries** — every one caused by sampling an index in whatever
state the previous index had left:

| N | Recorded | Actually |
|---|---|---|
| 6 | "AUTO off" | fan up — which exits AUTO as a side effect |
| 20 | "climate power on" | temp right up — it woke a system the previous index had switched off |
| 8 | "no effect" | airflow → face — the flag it clears was already 0 |

All three survived into a published table and were only caught when the table's
owner questioned an unrelated row.

**Lesson:** sweep results are hypotheses. Re-test each entry from a known
baseline before you believe the table, and be especially suspicious of entries
recorded immediately after a disruptive index.

### Ambiguous presentation is a bug

The corrected table was first published in two columns with a blank separator.
`N=4` (temp left up) and `N=16` (climate power off) landed on the same visual
row, and it read as though they were related. That prompted a challenge which,
usefully, uncovered the three real errors above.

**Lesson:** if a reader has to guess how to parse your table, the table is
wrong, even when the data is right.

## Environment notes

Small things that cost time on Windows:

- **Git Bash mangles absolute device paths.** `adb pull /odm/app/...` becomes
  `C:/Program Files/Git/odm/app/...`. `MSYS_NO_PATHCONV=1` fixes the source path
  and then breaks the destination. Use PowerShell for `adb pull` with absolute
  device paths.
- **`dexdump` is already in the Android SDK build-tools.** No jadx or apktool
  needed for constant tables, method signatures, and readable disassembly.
- **`aapt2 dump resources`** gives resource values without unpacking anything.
- **Wireless debugging ports change constantly.** `adb mdns services` finds the
  current one; port scanning is a waste of time.
- **logcat rolls fast.** A once-per-second heartbeat will bury a state snapshot
  within a minute. Filter noisy codes out of your subscription rather than
  grepping after the fact.
