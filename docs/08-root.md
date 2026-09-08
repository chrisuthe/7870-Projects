# 8. Root

## Short version

**You almost certainly don't need it.** Everything this project set out to do —
read the climate bus, write to it, draw a replacement bar — works on a stock,
unrooted unit. Root buys exactly one thing: the ability to change
`navigation_bar_height` and reclaim screen space. See
[7. Screen space](07-screen-space.md).

Weigh that against a firmware downgrade on a platform with a thin recovery path.

## What the lock state actually means

```
ro.boot.flash.locked      = 1
ro.boot.verifiedbootstate = green
```

That reads like "locked bootloader, verified boot enforcing, no root without
unlocking". **It is misleading on this platform.**

The community root procedure flashes an unsigned boot image with:

```bash
adb reboot bootloader
fastboot flash boot_a boot_<version>_Rooted.img
fastboot --set-active=a
```

There is **no `fastboot flashing unlock` step**. Fastboot accepts an arbitrary
boot image regardless of the reported lock state.

Fastboot can also be reached by pressing reset 4–5 times as the boot logo
appears.

## The procedure

Credit to **Sserjlrk** on 4PDA; write-up by the XDA thread
[[Guide] FYT uis7870sc Android 13 Root](https://xdaforums.com/t/guide-fyt-uis7870sc-android-13-root.4766708/).
Not reproduced here — follow the source, which is maintained.

Shape of it:

1. Get the unit onto FYT firmware **20250814** — the version the prebuilt rooted
   boot image targets.
   Firmware is applied from a FAT32 USB stick containing `6318_1.zip` and
   `lsec6318update` in the root, plugged into the USB pigtail. Takes ~10 minutes.
   **May wipe data.**
2. Install the Magisk APK.
3. `fastboot flash boot_a` the rooted boot image, set slot A active.
4. Open Magisk, accept the extended installation, reboot.

`6318` is the package identifier for UIS7870 devices.

## The obstacle, if your firmware is newer

The rooted image is built for a specific firmware. This unit shipped on:

```
ro.build.version.incremental = eng.ls.20260508.144045
ro.product.build.date        = Fri May 8 2026
```

Nine months newer than the guide's target. Going that route means a
**downgrade**, using an `lsec-downgrade` file alongside the firmware package.

## The risk that matters

Community reports state there is **no `.pac` file** available for uis7870(s)
devices. On Unisoc platforms the `.pac` is the full firmware image used with
ResearchDownload / SPD Upgrade Tool to recover a bricked device — the standard
safety net. Without it, a failed flash has fewer outs.

There is an
[unbrick guide](https://xdaforums.com/t/guide-fyt-7870-generic-head-unit-unbrick-procedures.4780947/)
for these units, so recovery isn't impossible, but plan on the usual net being
absent.

## A better path if you want root anyway

Rather than downgrading to fit someone else's prebuilt image:

1. Obtain the `6318` firmware package **for your own build**.
2. Extract `boot.img` from it.
3. Patch it with Magisk on a phone.
4. `fastboot flash boot_a` your patched image.

Same procedure, no downgrade, no data loss. It depends entirely on whether a
firmware package for your build is obtainable.

## DUDU OS

"DUDU OS" is an alternative firmware / interface for FYT 7870 units, discussed
in [this XDA thread](https://xdaforums.com/t/fyt-7870-dudu-os-interface-and-firmware.4705649/).

**Flashing it is not rooting.** It replaces the UI and firmware; superuser is a
separate Magisk step afterwards. Worth being clear about, because the two get
conflated.

## Verified boot after rooting

Not tested here. If you root, expect `verifiedbootstate` to change and any
attestation-dependent apps (banking, DRM) to notice. On a head unit that mostly
matters for Widevine L1 → L3 on streaming apps.
