# 7. Screen space

If you're replacing the bar, the first question is how much room you get. The
answer is **227 px, and you cannot grow it without root** — but the reason is
not what it looks like.

## Where 227 comes from

Not from `com.syu.air`. Its window creation, disassembled:

```
0000: const/4 v1, #int -1        ; width  = MATCH_PARENT
0009: move v2, v1                ; height = MATCH_PARENT  (same -1)
000a: LayoutParams.<init>(v1, v2, 2019, flags, -3)
000d: const-string v1, "NavigationBar"
```

The app asks for `MATCH_PARENT × MATCH_PARENT` with type
`TYPE_NAVIGATION_BAR` (2019) and never mentions a height. WindowManager clamps
any window of that type to the **framework's** navigation-bar height.

Read from the device:

```
android:dimen/navigation_bar_height            = 227px
android:dimen/navigation_bar_height_landscape  = 227px
android:dimen/status_bar_height                = 100px
```

**227 is a framework resource**, raised by the OEM from AOSP's stock 48dp when
they built the ROM. The panel is 160dpi so dp and px are interchangeable.

Total system chrome: 100 + 227 = **327 px of 1920, about 17% of the screen**,
permanently unavailable to apps.

## What a third-party app can and can't do

Tested on device, not inferred:

| Window type | Result |
|---|---|
| `TYPE_NAVIGATION_BAR` (2019) | **refused** — `BadTokenException: permission denied for window type 2019` |
| `TYPE_APPLICATION_OVERLAY` (2038) | works, needs `SYSTEM_ALERT_WINDOW` |
| `TYPE_ACCESSIBILITY_OVERLAY` (2032) | works, needs an `AccessibilityService` context |

And the decisive measurement — with a 400px overlay added:

```
app area before:  app=1080x1693
app area after:   app=1080x1693     (unchanged)
```

**An overlay creates no inset.** Only a `TYPE_NAVIGATION_BAR` window does, and
that type is signature-gated.

So the split is:

- **Drawing more than 227px: yes**, any height you like.
- **Reserving more than 227px: no.** Apps will not resize around you.

Everything beyond the 227px the system already reserves **covers app content**.
In testing, a 227px overlay landed on top of the launcher's media transport
controls.

### Positioning note

A `TYPE_APPLICATION_OVERLAY` with `gravity=BOTTOM` resolves against the
inset-reduced parent frame (`[0,0][1080,1693]`), so it sits *above* the OEM bar
rather than over it. `FLAG_LAYOUT_NO_LIMITS` alone did not change that in
testing. Covering the bar exactly needs a negative `y` offset — believed
straightforward, **not yet demonstrated**.

## Three shapes for a replacement

| | Height | App content lost | Notes |
|---|---|---|---|
| **A. In place** | 227px | none | keep `com.syu.air` running so the inset persists; draw over it |
| **B. Taller** | any | permanent | fine for a launcher, bad for maps / video / CarPlay |
| **C. Rest + expand** | 227 → full | only while open | rest in the free region, expand on demand |

**C is the recommended shape**, and the strongest argument for it is that the
OEM already does exactly this — the full-page climate view behind the `REAR`
button is pattern C, working on this hardware today.

### A useful consequence of A and C

Neither requires disabling `com.syu.air`. Leaving it running:

- preserves the 227px inset, so no app content is lost
- keeps the vendor stack warm
- means a bug in your bar never leaves the vehicle without climate control

Your overlay simply covers it.

## If you did have root

`navigation_bar_height` is a single framework dimension, and everything derives
from it — the reserved inset, the OEM bar, and any replacement. Changing it
resizes the whole system coherently. That would need an RRO targeting the
`android` package or a patched `framework-res.apk`, both of which need a system
partition or the platform key.

That is the *only* thing root buys for this project. See [8. Root](08-root.md).
