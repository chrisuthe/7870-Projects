package com.probe.hu;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Instrumentation probe for the SYU/FYT vendor IPC bus on a UIS7870 head unit.
 *
 * Binds action "com.syu.ms.toolkit" in package com.syu.ms, obtains module 7
 * (canbus) via IRemoteToolkit.getRemoteModule, then registers one callback
 * against every known climate data code so that every state change becomes
 * visible. Read it with:  adb logcat -s HUPROBE:V
 *
 * Wire format and transaction ids were recovered from com.syu.air.apk:
 *   IRemoteToolkit  getRemoteModule = 1
 *   IRemoteModule   cmd = 1, get = 2, register = 3, unregister = 4
 *   IModuleCallback update = 1
 */
public class MainActivity extends Activity {

    static final String TAG          = "HUPROBE";
    static final String TOOLKIT_DESC = "com.syu.ipc.IRemoteToolkit";
    static final String MODULE_DESC  = "com.syu.ipc.IRemoteModule";
    static final String CB_DESC      = "com.syu.ipc.IModuleCallback";

    static final int MODULE_CANBUS   = 7;
    static final int TXN_GET_MODULE  = 1;
    static final int TXN_CMD         = 1;
    static final int TXN_REGISTER    = 3;
    static final int TXN_UNREGISTER  = 4;
    static final int REGISTER_FLAG   = 1;   // value com.syu.air passes

    private IBinder canbus;                       // module 7, kept for cmd()
    private final java.util.Map<Integer,IBinder> modules =
            new java.util.HashMap<Integer,IBinder>();
    private TextView out;
    private ScrollView scroll;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /** Binder that the vendor service calls back into. */
    private abstract static class Callback extends Binder implements IInterface {
        final Tables.Mod mod;
        Callback(Tables.Mod mod) { this.mod = mod; attachInterface(this, CB_DESC); }

        @Override public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CB_DESC);
                return true;
            }
            if (code == 1) {                       // update(...)
                data.enforceInterface(CB_DESC);
                int      c = data.readInt();
                int[]    i = data.createIntArray();
                float[]  f = data.createFloatArray();
                String[] s = data.createStringArray();
                update(c, i, f, s);
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        abstract void update(int code, int[] i, float[] f, String[] s);
    }

    /**
     * One callback per module. Codes are only unique within a module -- code 2
     * is U_STANDBY on MAIN and U_VOL on SOUND -- so the callback has to know
     * which module it was registered against.
     */
    private Callback callbackFor(final Tables.Mod m) {
        return new Callback(m) {
            @Override void update(int code, int[] i, float[] f, String[] s) {
                // len is recorded explicitly: it settles whether the
                // progressive-prefix frames are real vendor behaviour or a
                // logging artifact.
                recWrite(System.currentTimeMillis() + "\t" + mod.label + "\t" + code
                         + "\t" + mod.of(code) + "\t" + (i == null ? 0 : i.length)
                         + "\t" + fmt(i));
                if (rec != null) return;          // recording: skip the log spam
                line(String.format(Locale.US, "%-7s %-28s c=%-5d %s",
                        mod.label, mod.of(code), code, fmt(i))
                        + (f != null ? " f=" + Arrays.toString(f) : "")
                        + (s != null ? " s=" + Arrays.toString(s) : ""));
            }
        };
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F1418"));

        out = new TextView(this);
        out.setTextColor(Color.parseColor("#7CFF9E"));
        out.setTextSize(12f);
        out.setPadding(20, 20, 20, 20);
        out.setTypeface(Typeface.MONOSPACE);

        scroll = new ScrollView(this);
        scroll.addView(out, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        IntentFilter filter = new IntentFilter("com.probe.hu.CMD");
        IntentFilter ofilter = new IntentFilter("com.probe.hu.OVERLAY");
        // Exported so `adb shell am broadcast` can reach them, but gated on
        // android.permission.DUMP: the shell uid holds it, ordinary installed
        // apps do not. Without this any app on the unit could drive the
        // climate system through this receiver.
        final String GATE = android.Manifest.permission.DUMP;
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(cmdReceiver, filter, GATE, null, Context.RECEIVER_EXPORTED);
            registerReceiver(overlayReceiver, ofilter, GATE, null, Context.RECEIVER_EXPORTED);
            registerReceiver(recordReceiver, new IntentFilter("com.probe.hu.RECORD"),
                             GATE, null, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(cmdReceiver, filter, GATE, null);
            registerReceiver(overlayReceiver, ofilter, GATE, null);
            registerReceiver(recordReceiver, new IntentFilter("com.probe.hu.RECORD"),
                             GATE, null);
        }

        for (String n : new String[]{"navigation_bar_height",
                "navigation_bar_height_landscape", "status_bar_height"}) {
            int id = getResources().getIdentifier(n, "dimen", "android");
            line("framework " + n + " id=" + id + " -> "
                 + (id > 0 ? getResources().getDimensionPixelSize(id) + "px" : "not found"));
        }

        line("binding com.syu.ms.toolkit / com.syu.ms");
        Intent intent = new Intent("com.syu.ms.toolkit");
        intent.setPackage("com.syu.ms");
        boolean requested = bindService(intent, conn, Context.BIND_AUTO_CREATE);
        line("bindService() returned " + requested);
        if (!requested) line("BIND REFUSED - service not visible or not exported");
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder svc) {
            line("connected to " + n.flattenToShortString());
            try {
                for (Tables.Mod m : Tables.ALL) {
                    IBinder b = getRemoteModule(svc, m.id);
                    if (b == null) { line("module " + m.id + " (" + m.label + ") NULL"); continue; }
                    modules.put(m.id, b);
                }
                canbus = modules.get(MODULE_CANBUS);
                registerAll();
            } catch (Throwable t) {
                line("ERROR " + t);
            }
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            line("service disconnected");
            canbus = null;
        }
    };

    private IBinder getRemoteModule(IBinder toolkit, int moduleId) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TOOLKIT_DESC);
            data.writeInt(moduleId);
            toolkit.transact(TXN_GET_MODULE, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally { data.recycle(); reply.recycle(); }
    }

    /**
     * Write path. AIDL wire order for cmd(int, int[], float[], String[]).
     * Drive it from a shell:
     *   am broadcast -a com.probe.hu.CMD --ei code 53 --ei value 1
     * FinalMainServer defines OFF=0, ON=1, SWITCH=2 (toggle).
     */
    private void cmd(IBinder module, int code, int[] values) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE_DESC);
            data.writeInt(code);
            data.writeIntArray(values);
            data.writeFloatArray(null);
            data.writeStringArray(null);
            module.transact(TXN_CMD, data, reply, 0);
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    private final BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent it) {
            int mod  = it.getIntExtra("mod", MODULE_CANBUS);
            int code = it.getIntExtra("code", -1);
            int v0   = it.getIntExtra("v0", 1);
            int v1   = it.getIntExtra("v1", 0);
            int n    = it.getIntExtra("n", 2);        // array length: 1 or 2
            if (code < 0)      { line("CMD rejected: no code extra"); return; }
            IBinder target = modules.get(mod);
            if (target == null) { line("CMD rejected: module " + mod + " not bound"); return; }
            int[] vals = (n == 1) ? new int[]{ v0 } : new int[]{ v0, v1 };
            try {
                cmd(target, code, vals);
                line(">>> CMD mod=" + mod + " code=" + code
                        + " vals=" + Arrays.toString(vals));
            } catch (Throwable t) {
                line(">>> CMD FAILED code=" + code + " : " + t);
            }
        }
    };

    /**
     * Overlay experiment. Can a normal app claim a bottom inset the way
     * com.syu.air's TYPE_NAVIGATION_BAR window does?
     *   am broadcast -a com.probe.hu.OVERLAY --ei h 400 --ei type 2038
     * type 2038 = TYPE_APPLICATION_OVERLAY, 2032 = TYPE_ACCESSIBILITY_OVERLAY,
     * 2019 = TYPE_NAVIGATION_BAR (expected to be refused for a normal app).
     */
    private android.view.View overlayView;

    private final BroadcastReceiver overlayReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent it) {
            int h    = it.getIntExtra("h", 400);
            int type = it.getIntExtra("type", 2038);
            android.view.WindowManager wm =
                    (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (overlayView != null) {
                try { wm.removeView(overlayView); } catch (Throwable ignored) {}
                overlayView = null;
                line("overlay removed");
                if (h <= 0) return;
            }
            android.widget.TextView v = new android.widget.TextView(MainActivity.this);
            v.setBackgroundColor(0xCCE5A143);
            v.setText("  PROBE OVERLAY  h=" + h + "  type=" + type);
            v.setTextColor(0xFF0F1418);
            android.view.WindowManager.LayoutParams lp =
                    new android.view.WindowManager.LayoutParams(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT, h, type,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                      | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                      | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = android.view.Gravity.BOTTOM;
            try {
                wm.addView(v, lp);
                overlayView = v;
                line("overlay ADDED h=" + h + " type=" + type);
            } catch (Throwable t) {
                line("overlay REFUSED type=" + type + " : " + t);
            }
        }
    };

    private void register(IBinder module, Callback cb, int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE_DESC);
            data.writeStrongBinder(cb.asBinder());
            data.writeInt(code);
            data.writeInt(REGISTER_FLAG);
            module.transact(TXN_REGISTER, data, reply, 0);
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    /** Keeps callbacks alive; a GC'd callback stops receiving updates. */
    private final java.util.List<Callback> held = new java.util.ArrayList<Callback>();

    private void registerAll() {
        for (Tables.Mod m : Tables.ALL) {
            IBinder b = modules.get(m.id);
            if (b == null) continue;
            Callback cb = callbackFor(m);
            held.add(cb);
            int ok = 0, failed = 0;
            for (int code : m.codes) {
                try { register(b, cb, code); ok++; }
                catch (Throwable t) { failed++; }
            }
            line("module " + m.id + " " + m.label + ": registered " + ok
                 + (failed > 0 ? ", " + failed + " failed" : ""));
        }
        line("======================================================");
    }

    /**
     * Long arrays are rendered as hex. Arrays.toString costs ~5 chars a byte,
     * which overruns logcat's line budget on a 20-byte MCU frame and silently
     * truncates the tail - where the interesting bytes tend to be.
     */
    private static String fmt(int[] a) {
        if (a == null) return "null";
        if (a.length <= 4) return Arrays.toString(a);
        StringBuilder b = new StringBuilder(a.length * 2 + 4);
        b.append('<');
        for (int v : a) b.append(String.format(Locale.US, "%02X", v & 0xFF));
        b.append('>').append(a.length);
        return b.toString();
    }

    // ---- recording -------------------------------------------------------
    // Writes to the app's own external dir, which `adb pull` can reach with no
    // storage permission and no root. Used for driving captures, where wireless
    // debugging is unavailable (Android ties it to an active Wi-Fi connection).
    private java.io.BufferedWriter rec;
    private String recPath;
    private android.location.LocationListener gpsListener;

    private synchronized void recWrite(String s) {
        if (rec == null) return;
        try { rec.write(s); rec.write('\n'); } catch (Throwable ignored) {}
    }

    private synchronized void recStart() {
        if (rec != null) { line("already recording -> " + recPath); return; }
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null),
                    "huprobe-" + System.currentTimeMillis() + ".tsv");
            rec = new java.io.BufferedWriter(new java.io.FileWriter(f), 1 << 16);
            recPath = f.getAbsolutePath();
            recWrite("# ms\tsource\tcode\tname\tlen\tvalue");
            line("RECORDING -> " + recPath);
            startGps();
        } catch (Throwable t) { line("record start FAILED: " + t); }
    }

    private synchronized void recStop() {
        if (rec == null) { line("not recording"); return; }
        try { rec.flush(); rec.close(); } catch (Throwable ignored) {}
        rec = null;
        stopGps();
        line("RECORDING STOPPED -> " + recPath);
    }

    /** The unit's own GPS, recorded alongside the frames as speed ground truth. */
    private void startGps() {
        try {
            android.location.LocationManager lm = (android.location.LocationManager)
                    getSystemService(Context.LOCATION_SERVICE);
            gpsListener = new android.location.LocationListener() {
                @Override public void onLocationChanged(android.location.Location l) {
                    recWrite(System.currentTimeMillis() + "\tGPS\t-1\tspeed_mps\t1\t"
                             + l.getSpeed());
                }
            };
            lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                    500, 0, gpsListener);
            line("gps ground-truth started");
        } catch (Throwable t) { line("gps unavailable: " + t); }
    }

    private void stopGps() {
        if (gpsListener == null) return;
        try {
            ((android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE))
                    .removeUpdates(gpsListener);
        } catch (Throwable ignored) {}
        gpsListener = null;
    }

    private final BroadcastReceiver recordReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent it) {
            if (it.getIntExtra("on", 1) != 0) recStart(); else recStop();
        }
    };

    private void line(final String s) {
        Log.i(TAG, s);
        final String stamped = clock.format(new Date()) + "  " + s;
        ui.post(new Runnable() {
            @Override public void run() {
                out.append(stamped + "\n");
                scroll.post(new Runnable() {
                    @Override public void run() { scroll.fullScroll(ScrollView.FOCUS_DOWN); }
                });
            }
        });
    }
}
