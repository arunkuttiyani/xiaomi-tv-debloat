package dev.lite.shield;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;

/** Turns the shield back on after a reboot or an update, if it was on before. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        boolean wasOn = c.getSharedPreferences(ShieldService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(ShieldService.ENABLED, false);
        if (wasOn && VpnService.prepare(c) == null) { ShieldService.start(c); Watchdog.schedule(c); }
    }
}
