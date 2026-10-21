package dev.lite.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.View;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;

/**
 * Small adapters that turn framework callbacks into Runnables. Top-level classes rather than
 * anonymous or inner ones: javac 21 emits metadata for those that d8 8.2 crashes on.
 */
final class Callbacks {
    private Callbacks() {}

    static class Receiver extends BroadcastReceiver {
        private final Runnable r;
        Receiver(Runnable r) { this.r = r; }
        @Override public void onReceive(Context c, Intent i) { r.run(); }
    }

    static class NetCallback extends ConnectivityManager.NetworkCallback {
        private final Runnable r;
        NetCallback(Runnable r) { this.r = r; }
        @Override public void onAvailable(Network n) { r.run(); }
        @Override public void onLost(Network n) { r.run(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { r.run(); }
    }

    static class RoundedOutline extends ViewOutlineProvider {
        private final float radius;
        RoundedOutline(float radius) { this.radius = radius; }
        @Override public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
        }
    }
}
