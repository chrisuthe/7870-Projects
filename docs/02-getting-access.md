# 2. Getting access

## The problem

On many of these units, **Developer options is behind a 4-digit PIN** that the
reseller sets. The usual factory codes circulated for FYT units — `3368`,
`8888`, `1617`, `126` — are for the *factory settings* menu, which is a
different gate.

Worse: the UIS7870 generation ships **per-firmware passwords**. There is no
universal list, and community reports confirm people failing every published
code on this platform.

### Codes that must not be tried

Guessing is not harmless. From the XDA factory-code thread:

| Code | Effect |
|---|---|
| `7890` | documented to trigger a **factory reset** |
| `9191` | leaves the unit in a *"device not activated"* state requiring a `license.dat` file you do not have |

A senior member of that thread warns explicitly that random codes on these units
can cause data deletion or removal of licence files. Do not brute force.

### Routes that don't need the code

1. **Activity Launcher.** OEM PIN gates are usually a wrapper around the stock
   activity, which often remains exported. Install
   [Activity Launcher](https://f-droid.org/packages/de.szalkowski.activitylauncher/)
   and launch
   `com.android.settings/.Settings$DevelopmentSettingsDashboardActivity`
   directly. Costs nothing, risks nothing, works often but not always — AOSP
   also gates the screen on `Settings.Global.DEVELOPMENT_SETTINGS_ENABLED`, so
   you may reach an inert screen.

2. **USB.** ADB-over-USB only needs the `adb_enabled` flag, which OEMs sometimes
   ship on for service. For UIS7870 the reported working port is the **4-pin
   connector D**, not connector E and not the USB-C, with a **USB-A to USB-A**
   cable.

3. **Factory settings menu.** Sometimes exposes an ADB toggle the normal menu
   doesn't.

## Wireless debugging

Android 11+ replaced plain `adb connect <ip>:5555` with Wireless debugging.
Three things trip people up:

- The port is **random**, not 5555. Port-scanning for 5555 finds nothing.
- It needs a **pairing handshake** with a 6-digit code before any connection.
- There are **two different ports**: a *pairing* port shown only inside the
  pairing dialog, and a *connect* port shown on the main Wireless debugging
  screen. They are not the same, and using the wrong one is the most common
  failure.

### First time

On the unit: `Settings → System → Developer options → Wireless debugging`,
toggle on, then `Pair device with pairing code`. Leave the dialog open — the
pairing socket only lives while it's displayed.

```bash
adb pair <ip>:<pair-port> <6-digit-code>
adb connect <ip>:<connect-port>
```

### Every time after

**The connect port is regenerated whenever the service restarts** — screen
sleep, ACC off, Wi-Fi drop. Pairing survives; the address does not. Do not
scan for it:

```bash
adb mdns services
# adb-<serial>  _adb-tls-connect._tcp  192.168.x.x:44755
adb connect 192.168.x.x:44755
```

`adb mdns services` returns the current port instantly whenever the unit is
advertising. If it returns nothing, wireless debugging is off on the unit and no
amount of scanning will help — the handshake has to start there.

### Make the pairing stick

In Developer options, enable **"Disable adb authorization timeout"**. Without
it, authorisations are revoked after 7 days of no connection.

## Confirming you're talking to the right device

Head units use MAC randomisation like any Android device, so the OUI tells you
nothing. Check the model string instead:

```bash
adb -s <ip>:<port> shell getprop ro.product.model
# uis7870sc_2h10_nosec
```

If you have several Android devices on the desk, `adb devices -l` shows the
product name for each — worth checking before you start issuing commands to what
you think is the head unit.

## Useful device state

```bash
# is the vehicle moving?  do not experiment if it is
adb shell "timeout 5 logcat -v brief -s Gps:V" | grep -oE 'vel=[0-9.]+'

# which vendor processes are alive
adb shell "ps -A -o NAME | grep -E 'syu|fyt'"

# what is on screen
adb exec-out screencap -p > screen.png
```

Tap injection also works and is useful for driving the OEM UI:

```bash
adb shell input tap 545 1793
```

See [scripts/](../scripts/) for wrappers around all of this.
