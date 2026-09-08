# 4. The IPC framework

SYU ships a clean three-interface AIDL framework in `com.syu.ipc`. Every
subsystem on the unit — climate, radio, Bluetooth, audio, steering-wheel
buttons, TPMS — is reached through it.

**It is not permission-gated.** A normal third-party app can bind to it and both
read and write. This was traced, not assumed — see
[the permission question](#the-permission-question) below.

## The interfaces

Recovered from `com.syu.air.apk` with `dexdump`:

```java
interface IRemoteToolkit {
    IRemoteModule getRemoteModule(int moduleId);
}

interface IRemoteModule {
    void         cmd(int code, int[] i, float[] f, String[] s);   // write
    ModuleObject get(int code, int[] i, float[] f, String[] s);   // read
    void         register(IModuleCallback cb, int code, int flag);
    void         unregister(IModuleCallback cb, int code);
}

interface IModuleCallback {
    void update(int code, int[] i, float[] f, String[] s);        // delivered on change
}
```

### Binder details

You need these exactly right — transaction ids are assigned by declaration
order, so a plausible-looking guess calls the wrong method.

| Interface | Descriptor | Transactions |
|---|---|---|
| `IRemoteToolkit` | `com.syu.ipc.IRemoteToolkit` | `getRemoteModule` = 1 |
| `IRemoteModule` | `com.syu.ipc.IRemoteModule` | `cmd` = 1, `get` = 2, `register` = 3, `unregister` = 4 |
| `IModuleCallback` | `com.syu.ipc.IModuleCallback` | `update` = 1 |

## Binding

```java
Intent i = new Intent("com.syu.ms.toolkit");
i.setPackage("com.syu.ms");
bindService(i, conn, Context.BIND_AUTO_CREATE);
// connects to com.syu.ms/app.ToolkitService
```

**On Android 11+ you must declare package visibility** or `bindService` fails
silently even though the service is exported:

```xml
<queries>
    <package android:name="com.syu.ms" />
</queries>
```

## Module ids

From `FinalMainServer`:

| Module | Id | | Module | Id |
|---|---|---|---|---|
| MAIN | 0 | | TV | 6 |
| RADIO | 1 | | **CANBUS** | **7** |
| BT | 2 | | TPMS | 8 |
| DVD | 3 | | DVR | 9 |
| SOUND | 4 | | STEER | 10 |
| IPOD | 5 | | SUBSERVER | 11 |
| | | | CUSTOMER | 12 |

Climate is **module 7**. Each module has its own code table in a matching
`Final*` class — `FinalCanbus`, `FinalSound`, `FinalRadio`, `FinalBt`,
`FinalSteer`, `FinalObd`, `FinalDvr`, `FinalIpod`, `FinalCustomer`.

Volume is module 4, not 7 — which is why volume presses produce nothing on the
canbus module.

## Subscribing

```java
IRemoteModule canbus = toolkit.getRemoteModule(7);
canbus.register(callback, code, 1);   // flag=1 is what com.syu.air passes
```

**On registration the current value is delivered immediately.** Register for all
87 climate codes and you get a complete state snapshot within a second — this is
the cheapest way to dump the entire climate state of the vehicle.

The callback carries the code, so one callback object can serve every
registration.

## The permission question

The obvious worry is that reads are open and writes are privileged —
`com.syu.air` runs as `android.uid.system`, an ordinary app does not. This was
traced through `com.syu.ms` and the answer is **no gate**:

- The stub (`Ly/c$a;`, ProGuard-obfuscated) has an `onTransact` that goes
  `enforceInterface` straight to dispatch. No `getCallingUid`, no
  `checkCallingPermission`, no branch on caller identity.
- The implementation (`Lbase/b;`) *does* call `Binder.getCallingPid()` — four
  times — but only to build a `"/proc/" + pid` string for logging. No
  comparison, no rejection.

A normal app is entitled to command this bus. If your writes appear to do
nothing, the cause is the **code namespace**, not permissions — see
[6. Sending commands](06-command-index.md).

## A working client

[`probe/`](../probe) is a complete minimal implementation: hand-written Binder
proxies, a callback stub, subscription to all 87 climate codes, and a broadcast
interface for sending commands. About 250 lines, no dependencies, builds with
the Android SDK command-line tools and no Gradle.
