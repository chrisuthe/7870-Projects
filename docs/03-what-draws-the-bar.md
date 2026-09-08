# 3. What draws the bar

These units replace Android's back / home / recents with a permanent bottom bar
carrying climate and volume controls. Before you can change it you have to know
what's drawing it, and there are three very different possibilities with very
different costs:

| Case | What it is | Cost to change |
|---|---|---|
| 1 | AOSP SystemUI's NavigationBar, restyled | high — decompile and replace SystemUI, needs root |
| 2 | A separate always-on-top app | low — disable or cover it |
| 3 | A view inside the launcher | trivial — install a different launcher |

## Finding out

One command distinguishes all three:

```bash
adb shell dumpsys window windows
```

On this unit:

```
Window #3 Window{2430e53 u0 NavigationBar}:
  mOwnerUid=1000  showForAllUsers=true  package=com.syu.air
  mAttrs={(0,0)(fillx227) gr=BOTTOM ty=NAVIGATION_BAR fmt=TRANSLUCENT
  Frames: frame=[0,1693][1080,1920]
```

**The window is titled `NavigationBar` but it is owned by `com.syu.air`, not
SystemUI.** That's case 2 — the good case.

### A property that lies

```bash
adb shell getprop qemu.hw.mainkeys
# 0
```

`mainkeys=0` means "this device has no hardware nav keys, so render the software
navigation bar". It is tempting to read that as "SystemUI owns the bar". It does
not. It means a navigation-bar *slot* exists — and a vendor app took it.

**Check the window owner, not the property.**

## The full window stack

Bottom half of the panel, from `dumpsys window windows`:

| Window | Owner | Frame | Type | What it is |
|---|---|---|---|---|
| `NavigationBar` | `com.syu.air` | `[0,1693][1080,1920]` | `NAVIGATION_BAR` | **the climate bar**, 227px |
| — | `com.syu.fytgesture` | `[0,1898][1080,1920]` | 2032 | 22px edge strip, gesture catcher |
| — | `com.syu.fytgesture` | fullscreen, invisible | 2032 | gesture canvas |
| — | `com.android.systemui` | `[1016,1514][1096,1594]` | — | 80x80 floating button, clipped at the screen edge |
| `StatusBar` | `com.android.systemui` | `[0,0][1080,100]` | — | top status bar |

`com.syu.fytgesture` uses window type **2032 = `TYPE_ACCESSIBILITY_OVERLAY`**,
backed by:

```
$ adb shell settings get secure enabled_accessibility_services
com.syu.fytgesture/com.syu.fytgesture.AccessibilityServiceGesture
```

That matters: an accessibility service is exactly how a third-party app performs
global Back / Home / **Recents** actions without root. The OEM is already using
the mechanism a replacement bar would need.

## Removing it

```bash
adb shell pm disable-user --user 0 com.syu.air
```

Verified: the `NavigationBar` window leaves the stack, the `com.syu.air` process
exits, and SystemUI and the status bar are untouched. No crash, no reboot.

**Putting it back needs two steps**, because the app only self-starts on
`BOOT_COMPLETED`:

```bash
adb shell pm enable com.syu.air
adb shell am start-service -n com.syu.air/.AirService
```

`pm enable` alone leaves you with an enabled package and no bar.

### Does disabling it break climate control?

**No.** This was the biggest open question of the project and it was tested
directly. With `com.syu.air` disabled:

- `com.syu.ms`, `com.syu.ss` and `com.syu.canbus` all keep running
- the IPC framework delivered **13 callbacks in 12 seconds** to a still-registered
  client

`com.syu.air` is one *client* of the climate bus, not the gateway to it. You can
remove the OEM bar permanently and still read and write climate. See
[4. The IPC framework](04-the-ipc-framework.md).

### But be careful anyway

On a vehicle with no physical HVAC controls — which includes this WK2 — a
disabled `com.syu.air` means **no way to change temperature, fan or defrost**.
The climate module simply holds its last state. Don't leave it disabled in a car
someone is about to drive.

## The "REAR" button

The top-row `REAR` button emits nothing on the climate bus. It is not a dead
control: it opens a **full-page climate view** with `Front Air` / `Rear Air`
tabs. On a vehicle without rear climate the `Rear Air` tab is greyed out.

That page is worth looking at if you're designing a replacement, because it
shows the OEM's own richer layout — notably **four discrete airflow-mode buttons
instead of the bar's cycling button**, proving the protocol supports direct
selection.
