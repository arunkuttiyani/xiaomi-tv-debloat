package dev.lite.home;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

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
 * Lite Home: a clock, a network indicator and a grid of app banners. No network access, no
 * tracking, no background work. Long-press OK (or press MENU) on a tile to reorder, hide or manage it.
 *
 * Motion is kept on the RenderThread (ViewPropertyAnimator + hardware layers) so it holds 60 fps
 * on the Mi Box's Mali-450; the only full-screen redraw is the short background tint crossfade.
 */
public class HomeActivity extends Activity {

    private static final int COLS = 5;
    private static final int BASE = 0xFF0B0E14;
    private static final String SETTINGS_KEY = "__settings__";
    // Launchers and the TV settings app are reached through the Settings tile instead.
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList(
            "dev.lite.home", "com.google.android.tvlauncher", "com.android.tv.settings"));
    private static final Interpolator EASE = new DecelerateInterpolator(1.8f);

    static class App {
        String key, label;
        Drawable banner, icon;
        Intent launch;
        boolean system;
        int tint = 0xFF1B2433;
        Drawable wash; // fills tiles of apps that ship only a square icon
    }

    private SharedPreferences prefs;
    private LinearLayout grid;
    private ScrollView scroller;
    private NetworkIcon netIcon;
    private View netChip;
    private TextView netLabel;
    private GradientDrawable backdrop;
    private ValueAnimator tintAnim;
    private int tint = 0xFF1B2433;
    private List<App> apps = new ArrayList<>();
    private String focusKey;
    private boolean resumed, dirty = true, shownOnce;
    private float dp;

    private final Callbacks.Receiver packagesChanged = new Callbacks.Receiver(() -> {
        dirty = true;
        if (resumed) load();
    });
    private final Callbacks.Receiver wifiChanged = new Callbacks.Receiver(this::updateNetwork);
    private final Callbacks.NetCallback netCallback =
            new Callbacks.NetCallback(() -> runOnUiThread(this::updateNetwork));

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("home", MODE_PRIVATE);

        backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{tint, BASE, BASE});
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(48), px(28), px(48), 0);
        root.setClipChildren(false);
        root.setBackground(backdrop);

        root.addView(header());

        scroller = new ScrollView(this);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setSmoothScrollingEnabled(true);
        scroller.setPadding(0, px(14), 0, px(32));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroller.addView(grid);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        registerReceiver(wifiChanged, new IntentFilter(WifiManager.RSSI_CHANGED_ACTION));
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(netCallback);
        updateNetwork();
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setTextColor(Color.WHITE);
        time.setTextSize(40);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextColor(0x99FFFFFF);
        date.setTextSize(15);
        clock.addView(time);
        clock.addView(date);
        bar.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));

        // Network chip: icon always, label slides in when focused. OK opens network settings.
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(px(12), px(8), px(12), px(8));
        chip.setFocusable(false); // enabled once the tiles hold focus, see buildGrid()
        chip.setClickable(true);
        netChip = chip;
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(px(22));
        chipBg.setColor(0x26FFFFFF);
        chipBg.setAlpha(0);
        chip.setBackground(chipBg);

        netLabel = new TextView(this);
        netLabel.setTextColor(Color.WHITE);
        netLabel.setTextSize(14);
        netLabel.setAlpha(0f);
        netLabel.setVisibility(View.GONE);
        netLabel.setPadding(0, 0, px(10), 0);
        netIcon = new NetworkIcon(this);
        chip.addView(netLabel);
        chip.addView(netIcon, new LinearLayout.LayoutParams(px(30), px(30)));

        chip.setOnFocusChangeListener((v, has) -> {
            ObjectAnimator.ofInt(chipBg, "alpha", has ? 255 : 0).setDuration(150).start();
            netLabel.setVisibility(has ? View.VISIBLE : View.GONE);
            netLabel.animate().alpha(has ? 1f : 0f).setDuration(180).setInterpolator(EASE).start();
        });
        chip.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        bar.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        return bar;
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        unregisterReceiver(wifiChanged);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        updateNetwork();
        if (dirty) load();
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override public void onBackPressed() { /* already home */ }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // HOME pressed while already home: glide back to the first tile.
        scroller.smoothScrollTo(0, 0);
        View first = apps.isEmpty() ? null : grid.findViewWithTag(apps.get(0).key);
        if (first != null) first.requestFocus();
    }

    // ---- network ---------------------------------------------------------------------

    private void updateNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null) {
            netIcon.set(NetworkIcon.OFFLINE, 0, true);
            netLabel.setText("Offline");
            return;
        }
        boolean ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            netIcon.set(NetworkIcon.ETHERNET, 0, ok);
            netLabel.setText(ok ? "Ethernet" : "Ethernet · no internet");
        } else {
            int rssi = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo().getRssi();
            int level = WifiManager.calculateSignalLevel(rssi, 4); // 0..3
            netIcon.set(NetworkIcon.WIFI, Math.max(1, level), ok);
            netLabel.setText(ok ? "Wi-Fi" : "Wi-Fi · no internet");
        }
    }

    // ---- app list --------------------------------------------------------------------

    private void load() {
        dirty = false;
        new Thread(() -> {
            List<App> list = queryApps();
            runOnUiThread(() -> { apps = list; buildGrid(); });
        }).start();
    }

    private List<App> queryApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        // Prefer each app's TV (leanback) entry; fall back to its phone launcher entry.
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                String pkg = r.activityInfo.packageName;
                if (!SKIP.contains(pkg) && !found.containsKey(pkg)) found.put(pkg, r);
            }
        }

        Set<String> hidden = prefs.getStringSet("hidden", Collections.emptySet());
        List<App> list = new ArrayList<>();
        for (ResolveInfo r : found.values()) {
            ActivityInfo ai = r.activityInfo;
            if (hidden.contains(ai.packageName)) continue;
            App a = new App();
            a.key = ai.packageName;
            a.label = String.valueOf(r.loadLabel(pm));
            a.banner = ai.loadBanner(pm);
            if (a.banner == null) a.icon = r.loadIcon(pm);
            a.tint = accentOf(a.banner != null ? a.banner : a.icon);
            if (a.banner == null) a.wash = washOf(a.icon);
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

        App s = new App();
        s.key = SETTINGS_KEY;
        s.label = "Settings";
        s.icon = getDrawable(R.drawable.gear);
        s.tint = 0xFF26324A;
        s.wash = washOf(s.icon); // white glyph -> slate fallback
        s.system = true;
        s.launch = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        list.add(s);
        return list;
    }

    /** A dark, saturated version of a drawable's dominant colour, used to tint the backdrop. */
    /**
     * Background for apps that ship only a square icon: a diagonal gradient in the icon's own
     * dominant colour plus a soft glow behind the icon. Greyscale icons fall back to slate.
     */
    private Drawable washOf(Drawable icon) {
        float[] hsv = new float[3];
        Color.colorToHSV(accentOf(icon), hsv);
        if (hsv[1] < 0.2f) { hsv[0] = 222; hsv[1] = 0.35f; }
        hsv[1] = Math.min(0.85f, hsv[1]);
        hsv[2] = 0.62f;
        int light = Color.HSVToColor(hsv);
        hsv[2] = 0.24f;
        int dark = Color.HSVToColor(hsv);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{light, dark});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(px(70));
        glow.setColors(new int[]{0x40FFFFFF, 0x00FFFFFF});
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{base, glow});
    }

    private static int accentOf(Drawable d) {
        if (d == null) return 0xFF1B2433;
        Bitmap b = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 16, 9);
        d.draw(new Canvas(b));
        float[] hsv = new float[3];
        double r = 0, g = 0, bl = 0, wsum = 0;
        for (int y = 0; y < 9; y++) for (int x = 0; x < 16; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            double w = hsv[1] * hsv[2] + 0.02; // favour vivid pixels over white/black
            r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; wsum += w;
        }
        b.recycle();
        if (wsum == 0) return 0xFF1B2433;
        Color.colorToHSV(Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum)), hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = 0.30f;
        return Color.HSVToColor(hsv);
    }

    private void setTint(int to) {
        if (tintAnim != null) tintAnim.cancel();
        tintAnim = ValueAnimator.ofObject(new ArgbEvaluator(), tint, to);
        tintAnim.setDuration(450);
        tintAnim.setInterpolator(EASE);
        tintAnim.addUpdateListener(va -> {
            tint = (int) va.getAnimatedValue();
            backdrop.setColors(new int[]{tint, BASE, BASE});
        });
        tintAnim.start();
    }

    private List<String> savedOrder() {
        String o = prefs.getString("order", "");
        return o.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(o.split(",")));
    }

    private void saveOrder() {
        List<String> keys = new ArrayList<>();
        for (App a : apps) if (!a.key.equals(SETTINGS_KEY)) keys.add(a.key);
        prefs.edit().putString("order", TextUtils.join(",", keys)).apply();
    }

    // ---- grid ------------------------------------------------------------------------

    private void buildGrid() {
        View cur = getCurrentFocus();
        if (cur != null && cur.getTag() != null) focusKey = (String) cur.getTag();
        // Removing the focused tile would hand focus to the header chip; park it until we refocus.
        netChip.setFocusable(false);
        grid.removeAllViews();

        boolean entrance = !shownOnce;
        shownOnce = true;
        LinearLayout row = null;
        View toFocus = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                grid.addView(row);
            }
            View cell = tile(apps.get(i));
            row.addView(cell);
            View card = cell.findViewWithTag(apps.get(i).key);
            if (apps.get(i).key.equals(focusKey) || toFocus == null) toFocus = card;
            if (entrance) {
                // staggered rise-in on first show only
                cell.setAlpha(0f);
                cell.setTranslationY(px(28));
                cell.animate().alpha(1f).translationY(0).setStartDelay(30L * i)
                        .setDuration(380).setInterpolator(EASE).withLayer().start();
            }
        }
        // Focus after layout, then let the chip be reachable again (UP from the top row).
        View target = toFocus;
        grid.post(() -> {
            if (target != null) target.requestFocus();
            netChip.setFocusable(true);
        });
    }

    private View tile(App a) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(px(152), -2);
        clp.setMargins(px(7), px(10), px(7), px(4));
        cell.setLayoutParams(clp);

        FrameLayout card = new FrameLayout(this);
        card.setTag(a.key);
        card.setFocusable(true);
        card.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1F2633);
        bg.setCornerRadius(px(10));
        card.setBackground(bg);
        card.setOutlineProvider(new Callbacks.RoundedOutline(px(10)));
        card.setClipToOutline(true);

        ImageView img = new ImageView(this);
        if (a.banner != null) {
            img.setImageDrawable(a.banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // square icon: blurred colour field behind, icon large and centred on top
            img.setImageDrawable(a.wash);
            img.setScaleType(ImageView.ScaleType.FIT_XY);
        }
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        if (a.banner == null) {
            ImageView glyph = new ImageView(this);
            glyph.setImageDrawable(a.icon);
            glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int size = a.key.equals(SETTINGS_KEY) ? px(44) : px(60);
            card.addView(glyph, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        }

        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(px(10));
        ring.setStroke(px(3), Color.WHITE);
        ring.setAlpha(0);
        card.setForeground(ring);

        TextView label = new TextView(this);
        label.setText(a.label);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(12);
        label.setTextColor(Color.WHITE);
        label.setAlpha(0.5f);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, px(8), 0, 0);

        card.setOnFocusChangeListener((v, has) -> {
            float s = has ? 1.1f : 1f;
            v.animate().scaleX(s).scaleY(s).translationZ(has ? px(12) : 0)
                    .setDuration(has ? 200 : 160).setInterpolator(EASE).withLayer().start();
            ObjectAnimator.ofInt(ring, "alpha", has ? 255 : 0).setDuration(160).start();
            label.animate().alpha(has ? 1f : 0.5f).translationY(has ? px(5) : 0)
                    .setDuration(180).setInterpolator(EASE).start();
            if (has) setTint(a.tint);
        });
        card.setOnClickListener(v -> launch(a));
        card.setOnLongClickListener(v -> { options(a); return true; });
        card.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a); return true; }
            return false;
        });

        cell.addView(card, new LinearLayout.LayoutParams(-1, px(86)));
        cell.addView(label);
        return cell;
    }

    private void launch(App a) {
        try {
            startActivity(a.launch);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- long-press menu -------------------------------------------------------------

    private void options(App a) {
        if (a.key.equals(SETTINGS_KEY)) {
            String[] items = {"Show hidden apps", "Reset app order"};
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Lite Home")
                    .setItems(items, (d, w) -> {
                        prefs.edit().remove(w == 0 ? "hidden" : "order").apply();
                        load();
                    }).show();
            return;
        }
        List<String> items = new ArrayList<>(Arrays.asList("Move left", "Move right", "Move to start", "Hide", "App info"));
        if (!a.system) items.add("Uninstall");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    int i = apps.indexOf(a);
                    int last = apps.size() - 2; // Settings always stays last
                    switch (items.get(w)) {
                        case "Move left":     move(i, Math.max(0, i - 1)); break;
                        case "Move right":    move(i, Math.min(last, i + 1)); break;
                        case "Move to start": move(i, 0); break;
                        case "Hide":
                            Set<String> h = new HashSet<>(prefs.getStringSet("hidden", Collections.emptySet()));
                            h.add(a.key);
                            prefs.edit().putStringSet("hidden", h).apply();
                            Toast.makeText(this, a.label + " hidden. Long-press Settings to show it again.", Toast.LENGTH_LONG).show();
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

    private void move(int from, int to) {
        if (from == to) return;
        focusKey = apps.get(from).key;
        apps.add(to, apps.remove(from));
        saveOrder();
        buildGrid();
    }

    private int px(float v) { return Math.round(v * dp); }
}
