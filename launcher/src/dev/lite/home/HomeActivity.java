package dev.lite.home;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lite Home: a clock, a grid of app banners and a Control Center panel on the right for network,
 * Bluetooth, display and storage, and optional weather beside the clock. No tracking, no background
 * work; the only network use is the weather (see Weather.java), which the Weather setting turns off.
 *
 *   Hold OK (or MENU) on a tile      move, hide or manage the app
 *   OK on the network pill           Control Center (also: RIGHT at the end of a row)
 *   Hold OK on the network pill      launcher settings (SettingsActivity)
 *
 * Performance notes, measured on a Mi Box 4 (Mali-450): soft shadows and per-animation GPU layers
 * caused most dropped frames, so focus is shown by size and brightness only (unselected tiles are
 * veiled). An outline was tried too, but its stroke aliased at the corners when scaled.
 */
public class HomeActivity extends Activity {

    static final int BASE = 0xFF0B0E14;
    static final String SETTINGS_KEY = "__settings__";
    // Launchers and the TV settings app are reached through the Settings tile instead.
    static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "dev.lite.home", "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.android.tv.settings"));

    static final Interpolator EASE = new DecelerateInterpolator(2f);
    static final Interpolator SETTLE = new OvershootInterpolator(1.1f);

    // Layout, in dp
    static final int SIDE = 48, HEADER = 112, GAP = 22;

    float dp;
    SharedPreferences prefs;
    Prefs cfg;
    String cfgSignature;
    int cols, cardW, cardH, rowH, rowsVisible;

    FrameLayout root;
    GradientDrawable backdrop;
    ValueAnimator tintAnim;
    int tint = 0xFF1B2433;
    TextClock time, date;
    LinearLayout weatherBox;
    WeatherIcon wxIcon;
    TextView wxTemp, wxDetails;
    ValueAnimator wxExpand;
    boolean wxExpanded;
    static final long IDLE_MS = 60_000;
    final Runnable wxIdle = this::expandWeather;
    final android.os.Handler wxHandler = new android.os.Handler();
    final Runnable wxTick = this::weatherTick; // method reference: d8 8.2 crashes on anonymous classes
    Callbacks.SteadyScroll scroller;
    LinearLayout grid;
    LinearLayout pill;
    TextView pillLabel;
    NetworkIcon pillNet;
    ControlPanel panel;
    ObjectAnimator scrollAnim;

    List<AppInfo> apps = new ArrayList<>();
    final Map<String, Integer> rowOf = new HashMap<>();
    AppInfo moving;
    ObjectAnimator wiggle;
    String focusKey;
    boolean resumed, dirty = true, shownOnce;
    final NetState net = new NetState();

    final Callbacks.Receiver packagesChanged = new Callbacks.Receiver(() -> {
        dirty = true;
        if (resumed) load();
    });
    final Callbacks.Receiver radioChanged = new Callbacks.Receiver(() -> updateStatus());
    Callbacks.NetCallback netCallback;

    // ---- setup ---------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE); // same file as v1/v2: keeps your order
        applyPrefs();

        root = new FrameLayout(this);
        backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{tint, BASE, BASE});
        root.setBackground(backdrop);

        buildGridArea();
        buildHeader();
        panel = new ControlPanel(this);
        root.addView(panel.scrim, new FrameLayout.LayoutParams(-1, -1));
        // Drops in at the top right, under the network pill, like tvOS's Control Center.
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(px(ControlPanel.WIDTH), -2, Gravity.END | Gravity.TOP);
        plp.setMargins(0, px(24), px(24), 0);
        root.addView(panel.view, plp);
        setContentView(root);
        // The backdrop covers the whole screen; drawing the window background under it too would
        // fill every pixel twice per frame.
        getWindow().setBackgroundDrawable(null);
        applyClockFormat();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        IntentFilter r = new IntentFilter(WifiManager.RSSI_CHANGED_ACTION);
        r.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        r.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        r.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        r.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        r.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(radioChanged, r);
        if (Build.VERSION.SDK_INT >= 24) {
            netCallback = new Callbacks.NetCallback(() -> runOnUiThread(this::updateStatus));
            connectivity().registerDefaultNetworkCallback(netCallback);
        }
        updateStatus();
    }

    /** Reads settings and works out a grid that fits this screen. */
    private void applyPrefs() {
        cfg = Prefs.load(this);
        cfgSignature = cfg.signature();
        cols = Math.max(3, Math.min(7, cfg.cols));
        float screenW = getResources().getDisplayMetrics().widthPixels / dp;
        float screenH = getResources().getDisplayMetrics().heightPixels / dp;
        float fit = (screenW - 2 * SIDE) / cols - GAP;
        cardW = (int) Math.min(cfg.tileWidthDp(), fit);
        cardH = Math.round(cardW * 0.586f); // TV banner shape
        rowH = cardH + (cfg.labels == Prefs.LABELS_NEVER ? 30 : 48);
        rowsVisible = Math.max(1, (int) ((screenH - HEADER) / rowH));
    }

    private void applyClockFormat() {
        String f = cfg.clock == Prefs.CLOCK_12 ? "h:mm" : cfg.clock == Prefs.CLOCK_24 ? "HH:mm" : null;
        time.setFormat12Hour(f != null ? f : "h:mm");
        time.setFormat24Hour(f != null ? f : "HH:mm");
        date.setVisibility(cfg.date ? View.VISIBLE : View.GONE);
    }

    private void buildHeader() {
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        time = new TextClock(this);
        time.setTextColor(Color.WHITE);
        time.setTextSize(40);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0x99FFFFFF);
        date.setTextSize(15);
        // time, then the weather (condition icon + temperature) on the same line
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.addView(time);
        weatherBox = new LinearLayout(this);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherBox.setPadding(px(20), px(2), 0, 0);
        weatherBox.setVisibility(View.GONE);
        wxIcon = new WeatherIcon(this);
        wxTemp = new TextView(this);
        wxTemp.setTextColor(Color.WHITE);
        wxTemp.setTextSize(34);
        wxTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        wxTemp.setPadding(px(6), 0, 0, 0);
        // Details that unfold to the right after a minute without input, e.g.
        // "Partly cloudy · Rain expected · 17° / 11°". Revealed by a clip, so nothing re-lays out.
        wxDetails = new TextView(this);
        wxDetails.setTextColor(0xCCFFFFFF);
        wxDetails.setTextSize(17);
        wxDetails.setSingleLine(true);
        wxDetails.setEllipsize(TextUtils.TruncateAt.END);
        wxDetails.setMaxWidth(px(440)); // never reaches the network pill on the right
        wxDetails.setPadding(px(18), px(4), 0, 0);
        wxDetails.setAlpha(0f);
        weatherBox.addView(wxIcon, new LinearLayout.LayoutParams(px(54), px(54)));
        weatherBox.addView(wxTemp);
        weatherBox.addView(wxDetails);
        timeRow.addView(weatherBox);
        clock.addView(timeRow);
        clock.addView(date);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP);
        clp.setMargins(px(SIDE), px(26), 0, 0);
        root.addView(clock, clp);

        // Network pill, top right. OK: Control Center. Hold OK or MENU: launcher settings.
        pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(px(14), px(8), px(16), px(8));
        pill.setFocusable(false); // enabled once the grid holds focus, see buildGrid()
        pill.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(22));
        bg.setColor(0xFF1C222D);
        pill.setBackground(bg);
        pillNet = new NetworkIcon(this);
        pillLabel = new TextView(this);
        pillLabel.setTextColor(Color.WHITE);
        pillLabel.setTextSize(15);
        pillLabel.setPadding(px(10), 0, 0, 0);
        pill.addView(pillNet, new LinearLayout.LayoutParams(px(24), px(24)));
        pill.addView(pillLabel);
        pill.setOnFocusChangeListener((v, has) -> {
            ValueAnimator a = ValueAnimator.ofObject(new ArgbEvaluator(), has ? 0xFF1C222D : Color.WHITE, has ? Color.WHITE : 0xFF1C222D);
            a.setDuration(cfg.ms(160));
            a.addUpdateListener(va -> bg.setColor((int) va.getAnimatedValue()));
            a.start();
            int fg = has ? 0xFF111111 : Color.WHITE;
            pillLabel.setTextColor(fg);
            pillNet.setColor(fg);
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(cfg.ms(200)).setInterpolator(EASE).start();
        });
        pill.setOnClickListener(v -> panel.open(v));
        pill.setOnLongClickListener(v -> { openSettings(); return true; });
        pill.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { openSettings(); return true; }
            return false;
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        lp.setMargins(0, px(36), px(SIDE), 0);
        root.addView(pill, lp);
    }

    private void buildGridArea() {
        scroller = new Callbacks.SteadyScroll(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(px(SIDE - GAP / 2f), px(6), 0, px(200));
        scroller.addView(grid);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.topMargin = px(HEADER);
        root.addView(scroller, lp);
    }

    void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        unregisterReceiver(radioChanged);
        if (netCallback != null) connectivity().unregisterNetworkCallback(netCallback);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!Prefs.load(this).signature().equals(cfgSignature)) {
            // settings changed: re-fit the grid and redraw everything
            applyPrefs();
            applyClockFormat();
            shownOnce = false;
            dirty = true;
            refreshWeather(true);
        }
        scheduleIdle();
        updateStatus();
        if (dirty) load();
        maybeHintDefaultHome();
        showWeather(cfg.weather ? Weather.cached(prefs) : null);
        wxHandler.removeCallbacks(wxTick);
        wxHandler.post(wxTick); // refresh now if stale, then check every 10 minutes while visible
    }

    @Override protected void onPause() {
        resumed = false;
        wxHandler.removeCallbacks(wxTick); // no weather requests while an app is in front
        wxHandler.removeCallbacks(wxIdle);
        collapseWeather(false);
        if (moving != null) endMove();
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (panel.isOpen()) panel.close();
        else if (moving != null) endMove();
        // otherwise: already home
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME pressed while already home: close everything and glide back to the first tile.
        if (panel.isOpen()) panel.close();
        if (moving != null) endMove();
        animateScroll(0);
        View first = apps.isEmpty() ? null : grid.findViewWithTag(apps.get(0).key);
        if (first != null) first.requestFocus();
    }

    /** Once: if opened as an app rather than as the home screen, say how to make it the default. */
    private void maybeHintDefaultHome() {
        if (prefs.getBoolean("hinted_home", false) || DefaultHome.isDefault(this)) return;
        prefs.edit().putBoolean("hinted_home", true).apply();
        Toast.makeText(this, "Lite Home isn't your home screen yet. Hold OK on the Wi-Fi pill, then "
                + "\"Set as default home screen\".", Toast.LENGTH_LONG).show();
    }

    // ---- weather ------------------------------------------------------------------------------

    private void weatherTick() {
        refreshWeather(false);
        wxHandler.postDelayed(wxTick, 10 * 60 * 1000L);
    }

    /**
     * Fetches new weather in the background when the cached copy is older than 30 minutes. The
     * location comes from the system time zone (looked up once per time zone).
     */
    void refreshWeather(boolean force) {
        if (!cfg.weather) { showWeather(null); return; }
        Weather cached = Weather.cached(prefs);
        if (!force && cached != null && !cached.stale()) return;
        new Thread(() -> {
            double[] at = Weather.location(prefs);
            Weather w = at == null ? null : Weather.fetch(prefs, at[0], at[1]);
            if (w != null) runOnUiThread(() -> showWeather(w));
        }).start();
    }

    private void showWeather(Weather w) {
        if (w == null || !cfg.weather) { weatherBox.setVisibility(View.GONE); return; }
        boolean f = Weather.fahrenheit(prefs);
        wxIcon.set(w.kind(), w.day);
        wxTemp.setText(Weather.deg(w.temp, f));
        String outlook = w.outlook();
        wxDetails.setText(w.condition() + (outlook != null ? "  ·  " + outlook : "")
                + "  ·  " + Weather.deg(w.max, f) + " / " + Weather.deg(w.min, f)); // today's high / low
        if (weatherBox.getVisibility() != View.VISIBLE) {
            weatherBox.setAlpha(0f);
            weatherBox.setVisibility(View.VISIBLE);
            weatherBox.animate().alpha(1f).setDuration(cfg.ms(300)).start();
        }
    }

    // ---- idle: unfold the weather details ---------------------------------------------------

    /** Any key press counts as activity: fold the details away and restart the idle timer. */
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (wxExpanded) collapseWeather(true);
        scheduleIdle();
    }

    private void scheduleIdle() {
        wxHandler.removeCallbacks(wxIdle);
        if (resumed && cfg.weather) wxHandler.postDelayed(wxIdle, IDLE_MS);
    }

    private void expandWeather() {
        if (wxExpanded || weatherBox.getVisibility() != View.VISIBLE || panel.isOpen()) return;
        wxExpanded = true;
        animateDetails(1f, cfg.ms(700));
    }

    private void collapseWeather(boolean animate) {
        if (!wxExpanded) return;
        wxExpanded = false;
        animateDetails(0f, animate ? cfg.ms(250) : 0);
    }

    /** Reveals the details left to right by growing a clip, with a fade; no re-layout per frame. */
    private void animateDetails(float to, long duration) {
        if (wxExpand != null) wxExpand.cancel();
        float from = wxDetails.getAlpha();
        if (duration == 0) { revealDetails(to); return; }
        wxExpand = ValueAnimator.ofFloat(from, to);
        wxExpand.setDuration(duration);
        wxExpand.setInterpolator(EASE);
        wxExpand.addUpdateListener(va -> revealDetails((float) va.getAnimatedValue()));
        wxExpand.start();
    }

    private void revealDetails(float f) {
        int w = wxDetails.getWidth(), h = wxDetails.getHeight();
        wxDetails.setClipBounds(new android.graphics.Rect(0, 0, Math.round(w * f), h));
        wxDetails.setAlpha(f);
        wxDetails.setTranslationX((1f - f) * -px(10));
    }

    // ---- status (pill + Control Center) ------------------------------------------------------

    ConnectivityManager connectivity() { return (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE); }

    void updateStatus() {
        ConnectivityManager cm = connectivity();
        net.ip = null;
        net.mbps = 0;
        net.name = null;
        net.type = NetworkIcon.OFFLINE;
        if (Build.VERSION.SDK_INT >= 23) {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps != null) {
                net.validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                net.type = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? NetworkIcon.ETHERNET : NetworkIcon.WIFI;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof Inet4Address) net.ip = la.getAddress().getHostAddress();
                }
            }
        } else {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                net.validated = true; // Android 5 can't tell
                net.type = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? NetworkIcon.ETHERNET : NetworkIcon.WIFI;
            }
        }
        if (net.type == NetworkIcon.WIFI) {
            WifiInfo wi = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
            if (wi != null) {
                net.level = Math.max(1, WifiManager.calculateSignalLevel(wi.getRssi(), 4));
                net.mbps = wi.getLinkSpeed();
                String ssid = wi.getSSID();
                // Android 9+ hides the name from apps without location access
                if (ssid != null && !ssid.contains("unknown ssid")) net.name = ssid.replace("\"", "");
            }
        }
        pillNet.set(net.type, net.level, net.validated);
        pillLabel.setText(net.type == NetworkIcon.OFFLINE ? "Offline"
                : net.type == NetworkIcon.ETHERNET ? "Ethernet" : (net.validated ? "Wi-Fi" : "No internet"));
        if (panel != null) panel.refresh();
    }

    // ---- apps ----------------------------------------------------------------------------

    void load() {
        dirty = false;
        new Thread(() -> {
            List<AppInfo> list = queryApps();
            runOnUiThread(() -> { apps = list; buildGrid(); });
        }).start();
    }

    private List<AppInfo> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        // Prefer each app's TV (leanback) entry; fall back to its phone launcher entry.
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = r.activityInfo.packageName;
                if (!SKIP.contains(pkg) && !found.containsKey(pkg)) found.put(pkg, r);
            }
        }
        Set<String> hidden = prefs.getStringSet("hidden", Collections.emptySet());
        List<AppInfo> list = new ArrayList<>();
        for (ResolveInfo r : found.values()) {
            ActivityInfo ai = r.activityInfo;
            if (hidden.contains(ai.packageName)) continue;
            AppInfo a = new AppInfo();
            a.key = ai.packageName;
            a.label = String.valueOf(r.loadLabel(pm));
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) {
                a.icon = r.loadIcon(pm);
                a.washColors = Art.washColors(a.icon);
            }
            a.tint = Art.accentOf(a.banner != null ? a.banner : Art.iconTile(a.icon, a.washColors, 160, 90, 52, 0));
            a.system = (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            a.launch = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            list.add(a);
        }

        // Saved order first, everything else alphabetically after it.
        List<String> order = savedOrder();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        Collections.sort(list, (x, y) -> {
            Integer rx = rank.get(x.key), ry = rank.get(y.key);
            if (rx != null && ry != null) return rx - ry;
            if (rx != null) return -1;
            if (ry != null) return 1;
            return x.label.compareToIgnoreCase(y.label);
        });

        AppInfo s = new AppInfo();
        s.key = SETTINGS_KEY;
        s.label = "Settings";
        s.icon = getDrawable(R.drawable.gear);
        s.washColors = new int[]{0xFF4A5D85, 0xFF26324A};
        s.tint = 0xFF26324A;
        s.system = true;
        s.launch = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        list.add(s);
        return list;
    }

    /** Fresh artwork for one view: the banner, or the square icon on its colour wash. */
    Drawable artFor(AppInfo a) {
        if (a.banner != null) return Art.copy(a.banner);
        int size = Math.round(cardH * (a.key.equals(SETTINGS_KEY) ? 0.48f : 0.64f));
        return Art.iconTile(a.icon, a.washColors, px(cardW), px(cardH), px(size), 0);
    }

    private List<String> savedOrder() {
        String o = prefs.getString("order", "");
        return o.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(o.split(",")));
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (AppInfo a : apps) if (!a.key.equals(SETTINGS_KEY)) keys.add(a.key);
        prefs.edit().putString("order", TextUtils.join(",", keys)).apply();
    }

    // ---- grid ----------------------------------------------------------------------------

    void buildGrid() {
        View cur = getCurrentFocus();
        if (cur != null && cur.getTag() instanceof String) focusKey = (String) cur.getTag();
        // Removing the focused tile would hand focus to the pill; park it until we refocus.
        pill.setFocusable(false);
        grid.removeAllViews();
        rowOf.clear();

        boolean entrance = !shownOnce && cfg.motion != Prefs.MOTION_OFF;
        shownOnce = true;
        LinearLayout row = null;
        View toFocus = null;
        for (int i = 0; i < apps.size(); i++) {
            AppInfo a = apps.get(i);
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                grid.addView(row, new LinearLayout.LayoutParams(-2, px(rowH)));
            }
            rowOf.put(a.key, i / cols);
            View cell = tile(a, i == apps.size() - 1 || i % cols == cols - 1);
            row.addView(cell);
            View card = cell.findViewWithTag(a.key);
            if (a.key.equals(focusKey) || toFocus == null) toFocus = card;
            if (entrance) {
                cell.setAlpha(0f);
                cell.setTranslationY(px(24));
                cell.animate().alpha(1f).translationY(0).setStartDelay(cfg.ms(25) * i)
                        .setDuration(cfg.ms(380)).setInterpolator(EASE).start();
            }
        }
        View target = toFocus;
        grid.post(() -> {
            if (target != null) target.requestFocus();
            pill.setFocusable(true);
            if (moving != null) startWiggle();
        });
    }

    private View tile(AppInfo a, boolean lastInRow) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.setClipToPadding(false); // the focused tile grows past the cell's padding
        cell.setLayoutParams(new LinearLayout.LayoutParams(px(cardW + GAP), -1));
        cell.setPadding(px(GAP / 2f), px(12), px(GAP / 2f), 0);

        FrameLayout card = new FrameLayout(this);
        card.setTag(a.key);
        card.setFocusable(true);
        card.setClickable(true);
        card.setOutlineProvider(new Callbacks.RoundedOutline(px(12)));
        card.setClipToOutline(true);
        // Banners fill the tile, so only square-icon tiles (drawn on their own wash) need no backing.
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setImageDrawable(artFor(a));
        // Unselected tiles are dimmed by a colour filter on the artwork itself: it's applied in the
        // same pass that draws the image, so it costs no extra drawing (an overlay layer doubled
        // the pixels drawn and an outline aliased at the corners when scaled).
        art.setColorFilter(DIM, PorterDuff.Mode.MULTIPLY);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        TextView label = new TextView(this);
        label.setText(a.label);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(13);
        label.setTextColor(Color.WHITE);
        label.setAlpha(labelAlpha(false));
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, px(14), 0, 0);
        if (cfg.labels == Prefs.LABELS_NEVER) label.setVisibility(View.GONE);

        card.setOnFocusChangeListener((v, has) -> onCardFocus(v, art, label, a, has));
        card.setOnClickListener(v -> onCardClick(v, a));
        card.setOnLongClickListener(v -> { if (moving == null) options(a); return true; });
        card.setOnKeyListener((v, code, e) -> onCardKey(v, a, code, e, lastInRow));

        cell.addView(card, new LinearLayout.LayoutParams(px(cardW), px(cardH)));
        cell.addView(label, new LinearLayout.LayoutParams(px(cardW), -2));
        return cell;
    }

    private float labelAlpha(boolean focused) {
        if (focused) return 1f;
        return cfg.labels == Prefs.LABELS_FOCUSED ? 0f : 0.55f;
    }

    static final int DIM = 0xFFA6A6A6; // multiply: about 65% brightness for unselected tiles

    /** Fades one tile's artwork between dimmed and full brightness. Only two tiles change per move. */
    private void brighten(ImageView art, boolean on) {
        Object running = art.getTag();
        if (running instanceof ValueAnimator) ((ValueAnimator) running).cancel();
        if (cfg.motion == Prefs.MOTION_OFF) {
            art.setColorFilter(on ? Color.WHITE : DIM, PorterDuff.Mode.MULTIPLY);
            return;
        }
        int from = on ? DIM : Color.WHITE, to = on ? Color.WHITE : DIM;
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        va.setDuration(cfg.ms(on ? 160 : 220));
        va.addUpdateListener(x -> art.setColorFilter((int) x.getAnimatedValue(), PorterDuff.Mode.MULTIPLY));
        art.setTag(va);
        va.start();
    }

    private float focusScale() { return cfg.motion == Prefs.MOTION_FULL ? 1.12f : 1.08f; }

    private void onCardFocus(View card, ImageView art, TextView label, AppInfo a, boolean has) {
        float s = has ? focusScale() : 1f;
        card.animate().scaleX(s).scaleY(s)
                .setDuration(cfg.ms(has ? 240 : 180))
                .setInterpolator(has && cfg.motion == Prefs.MOTION_FULL ? SETTLE : EASE).start();
        label.animate().alpha(labelAlpha(has)).translationY(has ? px(6) : 0).setDuration(cfg.ms(200)).setInterpolator(EASE).start();
        brighten(art, has);
        if (!has) return;
        focusKey = a.key;
        if (cfg.tint) setTint(a.tint);
        Integer r = rowOf.get(a.key);
        scrollToRow(r == null ? 0 : r);
    }

    private boolean onCardKey(View card, AppInfo a, int code, KeyEvent e, boolean lastInRow) {
        if (code == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP && moving == null) options(a);
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (moving != null) return moveKey(code);
        // RIGHT at the end of a row opens the Control Center, but only on a fresh press, so holding
        // RIGHT to race along a row stops at the last tile instead of flinging the panel open.
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT && lastInRow && cfg.edgeCc) {
            if (e.getRepeatCount() == 0) panel.open(card);
            return true;
        }
        return false;
    }

    private void onCardClick(View card, AppInfo a) {
        if (moving != null) { endMove(); return; }
        if (cfg.motion == Prefs.MOTION_FULL) {
            // A quick press-in before the app opens.
            card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(70).setInterpolator(EASE)
                    .withEndAction(() -> card.animate().scaleX(focusScale()).scaleY(focusScale()).setDuration(180).setInterpolator(SETTLE).start())
                    .start();
        }
        try {
            startActivity(a.launch);
        } catch (Exception ex) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void setTint(int to) {
        if (tintAnim != null) tintAnim.cancel();
        if (cfg.motion == Prefs.MOTION_OFF) {
            tint = to;
            backdrop.setColors(new int[]{tint, BASE, BASE});
            return;
        }
        tintAnim = ValueAnimator.ofObject(new ArgbEvaluator(), tint, to);
        tintAnim.setDuration(cfg.ms(420));
        tintAnim.setInterpolator(EASE);
        tintAnim.addUpdateListener(va -> {
            tint = (int) va.getAnimatedValue();
            backdrop.setColors(new int[]{tint, BASE, BASE});
        });
        tintAnim.start();
    }

    // The rows that fit under the header never scroll; past that, glide so the focused row sits last.
    private void scrollToRow(int row) {
        int target = Math.max(0, (row - (rowsVisible - 1)) * px(rowH));
        if (scroller.getScrollY() == target && (scrollAnim == null || !scrollAnim.isRunning())) return;
        animateScroll(target);
    }

    private void animateScroll(int target) {
        if (scrollAnim != null) scrollAnim.cancel();
        if (cfg.motion == Prefs.MOTION_OFF) { scroller.scrollTo(0, target); return; }
        scrollAnim = ObjectAnimator.ofInt(scroller, "scrollY", scroller.getScrollY(), target);
        scrollAnim.setDuration(cfg.ms(360));
        scrollAnim.setInterpolator(EASE);
        scrollAnim.start();
    }

    // ---- Control Center hooks --------------------------------------------------------------

    /** While the Control Center is open, nothing behind it can take focus. */
    void setBackgroundFocusable(boolean on) {
        scroller.setDescendantFocusability(on ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        pill.setFocusable(on);
    }

    void startSettings(String action) {
        Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(getPackageManager()) == null) i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    // ---- options and move mode ---------------------------------------------------------------

    private void options(AppInfo a) {
        if (a.key.equals(SETTINGS_KEY)) {
            openSettings();
            return;
        }
        List<String> items = new ArrayList<>(Arrays.asList("Move", "Hide", "App info"));
        if (!a.system) items.add("Uninstall");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    switch (items.get(w)) {
                        case "Move": startMove(a); break;
                        case "Hide":
                            Set<String> h = new HashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
                            h.add(a.key);
                            prefs.edit().putStringSet("hidden", h).apply();
                            Toast.makeText(this, a.label + " hidden. Launcher settings can show it again.", Toast.LENGTH_LONG).show();
                            load();
                            break;
                        case "App info":
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", a.key, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            break;
                        case "Uninstall":
                            startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.key, null)));
                            break;
                    }
                }).show();
    }

    private void startMove(AppInfo a) {
        moving = a;
        focusKey = a.key;
        startWiggle();
        Toast.makeText(this, "Use the arrows to move " + a.label + ". Press OK when done.", Toast.LENGTH_SHORT).show();
    }

    private void startWiggle() {
        if (wiggle != null) wiggle.cancel();
        View card = grid.findViewWithTag(moving.key);
        if (card == null || cfg.motion == Prefs.MOTION_OFF) return;
        wiggle = ObjectAnimator.ofFloat(card, "rotation", -1.6f, 1.6f);
        wiggle.setDuration(120);
        wiggle.setRepeatMode(ValueAnimator.REVERSE);
        wiggle.setRepeatCount(ValueAnimator.INFINITE);
        wiggle.start();
    }

    private boolean moveKey(int code) {
        int i = apps.indexOf(moving), last = apps.size() - 2; // Settings always stays last
        int j = i;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: j = i - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: j = i + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: j = i - cols; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: j = i + cols; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                endMove();
                return true;
            default:
                return true;
        }
        j = Math.max(0, Math.min(last, j));
        if (j != i) {
            apps.add(j, apps.remove(i));
            focusKey = moving.key;
            buildGrid();
        }
        return true;
    }

    private void endMove() {
        if (wiggle != null) wiggle.cancel();
        wiggle = null;
        View card = grid.findViewWithTag(moving.key);
        if (card != null) card.animate().rotation(0).setDuration(120).start();
        moving = null;
        saveOrder();
    }

    int px(float v) { return Math.round(v * dp); }
}
