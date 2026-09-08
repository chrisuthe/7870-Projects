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

    private IBinder canbus;
    private TextView out;
    private ScrollView scroll;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /** Binder that the vendor service calls back into. */
    private abstract static class Callback extends Binder implements IInterface {
        Callback() { attachInterface(this, CB_DESC); }

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

    /** One shared callback for every code; the code arrives as an argument. */
    private final Callback callback = new Callback() {
        @Override void update(int code, int[] i, float[] f, String[] s) {
            line(String.format(Locale.US, "%-26s code=%-4d ints=%s flts=%s strs=%s",
                    Names.of(code), code, Arrays.toString(i), Arrays.toString(f),
                    Arrays.toString(s)));
        }
    };

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
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(cmdReceiver, filter, Context.RECEIVER_EXPORTED);
            registerReceiver(overlayReceiver, ofilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(cmdReceiver, filter);
            registerReceiver(overlayReceiver, ofilter);
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
                canbus = getRemoteModule(svc, MODULE_CANBUS);
                if (canbus == null) { line("module 7 returned NULL"); return; }
                line("module 7 acquired: " + canbus);
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
    private void cmd(int code, int[] values) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE_DESC);
            data.writeInt(code);
            data.writeIntArray(values);
            data.writeFloatArray(null);
            data.writeStringArray(null);
            canbus.transact(TXN_CMD, data, reply, 0);
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    private final BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent it) {
            int code = it.getIntExtra("code", -1);
            int v0   = it.getIntExtra("v0", 1);
            int v1   = it.getIntExtra("v1", 0);
            int n    = it.getIntExtra("n", 2);        // array length: 1 or 2
            if (code < 0)      { line("CMD rejected: no code extra"); return; }
            if (canbus == null){ line("CMD rejected: module 7 not bound"); return; }
            int[] vals = (n == 1) ? new int[]{ v0 } : new int[]{ v0, v1 };
            try {
                cmd(code, vals);
                line(">>> CMD " + Names.of(code) + " code=" + code
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

    private void register(int code) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(MODULE_DESC);
            data.writeStrongBinder(callback.asBinder());
            data.writeInt(code);
            data.writeInt(REGISTER_FLAG);
            canbus.transact(TXN_REGISTER, data, reply, 0);
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    private void registerAll() {
        int ok = 0, failed = 0;
        for (int code : Names.CODES) {
            try { register(code); ok++; }
            catch (Throwable t) { failed++; }
        }
        line("registered " + ok + " codes, " + failed + " failed (flag=" + REGISTER_FLAG + ")");
        line("================ tap the climate bar now ================");
    }

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
