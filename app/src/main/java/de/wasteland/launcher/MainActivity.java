package de.wasteland.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wasteland TV Launcher
 * Optimiert fuer Amazon Fire TV Stick 4K (1. Gen, Fire OS 6.7.1.1 / Android 7.1.2 / armeabi-v7a)
 * - Variante A: In die Landschaft integrierte Schilder (prozentuale TV-Positionierung)
 * - Variante B: Freie Landschaft mit dezentem Pip-Boy App-Dock
 * - Weicher Hintergrundwechsel (Standard: 5 Min., frei einstellbar)
 * - Automatische Erkennung der 6 Streaming-Apps (keine Spiele, leicht erweiterbar)
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "WastelandPrefs";
    private static final String KEY_MODE = "ui_mode"; // "A" oder "B"
    private static final String KEY_INTERVAL_MIN = "bg_interval_min";
    private static final String KEY_EXTRA_APPS = "extra_apps";
    private static final String KEY_AMAZON_BYPASS_UNTIL = "amazon_bypass_until";

    private static final List<AppSlot> STREAMING_SLOTS = Arrays.asList(
            new AppSlot("Netflix", "REKLAMETAFEL", Arrays.asList(
                    "com.netflix.ninja", "com.netflix.mediaclient")),
            new AppSlot("YouTube", "VAULT-BOY", Arrays.asList(
                    "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
                    "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta",
                    "com.teamsmart.videomanager.tv")),
            new AppSlot("Disney+", "RED ROCKET", Arrays.asList(
                    "com.disney.disneyplus")),
            new AppSlot("waipu.tv", "NUKA-AUTOMAT", Arrays.asList(
                    "de.exaring.waipu.firetv", "de.exaring.waipu", "de.exaring.waipu.tv")),
            new AppSlot("Prime Video", "OLDTIMER", Arrays.asList(
                    "com.amazon.avod", "com.amazon.firebat", "com.amazon.amazonvideo.livingroom")),
            new AppSlot("Twitch", "AUSSENPOSTEN", Arrays.asList(
                    "tv.twitch.android.app", "tv.twitch.android.viewer"))
    );

    // Prozentuale Koordinaten (xFrac, yFrac) passend zu den Objekten in allen 4 Fallout-Landschaften
    private static final float[][] WORLD_HOTSPOTS_FRAC = new float[][]{
            {0.09f, 0.36f}, // Reklametafel links
            {0.39f, 0.56f}, // Vault-Boy Mitte
            {0.69f, 0.44f}, // Red Rocket / Haus rechts
            {0.53f, 0.60f}, // Nuka-Cola-Automat
            {0.17f, 0.67f}, // Oldtimer links unten
            {0.76f, 0.69f}, // Aussenposten rechts unten
            {0.34f, 0.78f}, // Zusatz-Slot 1
            {0.58f, 0.78f}  // Zusatz-Slot 2
    };

    private final int[] builtInBackgrounds = new int[]{
            R.drawable.bg_01_mojave_red_rocket,
            R.drawable.bg_02_ruinen_vorstadt,
            R.drawable.bg_03_ueberwucherte_autobahn,
            R.drawable.bg_04_nacht_oedland
    };

    private final List<Object> wallpaperSources = new ArrayList<>();
    private int currentBgIndex = 0;
    private boolean showingA = true;
    private int containerWidth = 0;
    private int containerHeight = 0;

    private ImageView bgImageA;
    private ImageView bgImageB;
    private TextView clockView;
    private TextView dateView;
    private TextView btnSettings;
    private LinearLayout dockContainer;
    private HorizontalScrollView dockScroll;
    private FrameLayout worldHotspotsContainer;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 10000L);
        }
    };

    private final Runnable bgSwitchRunnable = new Runnable() {
        @Override
        public void run() {
            switchBackgroundSmooth();
            scheduleNextBackgroundSwitch();
        }
    };

    public static boolean isAmazonBypassActive(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return System.currentTimeMillis() < p.getLong(KEY_AMAZON_BYPASS_UNTIL, 0L);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bgImageA = (ImageView) findViewById(R.id.bg_image_a);
        bgImageB = (ImageView) findViewById(R.id.bg_image_b);
        clockView = (TextView) findViewById(R.id.pip_clock);
        dateView = (TextView) findViewById(R.id.pip_date);
        btnSettings = (TextView) findViewById(R.id.btn_settings);
        dockContainer = (LinearLayout) findViewById(R.id.dock_container);
        dockScroll = (HorizontalScrollView) findViewById(R.id.dock_scroll);
        worldHotspotsContainer = (FrameLayout) findViewById(R.id.world_hotspots_container);

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettingsDialog();
            }
        });

        loadWallpaperSources();
        applyDrawableToView(bgImageA, wallpaperSources.get(0));
        bgImageA.setAlpha(1.0f);
        bgImageB.setAlpha(0.0f);

        // Sobald die echte TV-Flaeche vermessen ist, Variante A/B sauber positionieren
        worldHotspotsContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int w = worldHotspotsContainer.getWidth();
                        int h = worldHotspotsContainer.getHeight();
                        if (w > 0 && h > 0 && (w != containerWidth || h != containerHeight)) {
                            containerWidth = w;
                            containerHeight = h;
                            applyModeAndPopulate();
                        }
                    }
                });

        updateClock();
        handler.post(clockRunnable);
        scheduleNextBackgroundSwitch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sobald der Nutzer zurueck im Wasteland-Launcher ist, Amazon-Bypass zuruecksetzen
        prefs.edit().putLong(KEY_AMAZON_BYPASS_UNTIL, 0L).apply();
        if (containerWidth > 0 && containerHeight > 0) {
            applyModeAndPopulate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(bgSwitchRunnable);
    }

    private void loadWallpaperSources() {
        wallpaperSources.clear();
        for (int resId : builtInBackgrounds) {
            wallpaperSources.add(resId);
        }
        // Optional eigene Zusatzbilder aus /sdcard/FalloutLauncher/Hintergruende/ einlesen
        try {
            File extDir = new File(Environment.getExternalStorageDirectory(), "FalloutLauncher/Hintergruende");
            if (extDir.exists() && extDir.isDirectory()) {
                File[] files = extDir.listFiles();
                if (files != null) {
                    Arrays.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File a, File b) {
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });
                    for (File f : files) {
                        String low = f.getName().toLowerCase(Locale.ROOT);
                        if (f.isFile() && (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png"))) {
                            // Standard-Kopien nicht doppelt laden
                            if (!low.startsWith("bg_01_") && !low.startsWith("bg_02_")
                                    && !low.startsWith("bg_03_") && !low.startsWith("bg_04_")) {
                                wallpaperSources.add(f);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleNextBackgroundSwitch() {
        handler.removeCallbacks(bgSwitchRunnable);
        int mins = prefs.getInt(KEY_INTERVAL_MIN, 5);
        if (mins > 0 && wallpaperSources.size() > 1) {
            handler.postDelayed(bgSwitchRunnable, mins * 60L * 1000L);
        }
    }

    private void switchBackgroundSmooth() {
        if (wallpaperSources.size() <= 1) {
            return;
        }
        currentBgIndex = (currentBgIndex + 1) % wallpaperSources.size();
        Object nextSrc = wallpaperSources.get(currentBgIndex);

        final ImageView fadeInView = showingA ? bgImageB : bgImageA;
        final ImageView fadeOutView = showingA ? bgImageA : bgImageB;

        applyDrawableToView(fadeInView, nextSrc);
        fadeInView.setAlpha(0.0f);
        fadeInView.animate().alpha(1.0f).setDuration(1100L).start();
        fadeOutView.animate().alpha(0.0f).setDuration(1100L).start();
        showingA = !showingA;
    }

    private void applyDrawableToView(ImageView iv, Object source) {
        if (source instanceof Integer) {
            iv.setImageResource((Integer) source);
        } else if (source instanceof File) {
            Bitmap bmp = decodeScaledBitmap(((File) source).getAbsolutePath(), 1920, 1080);
            if (bmp != null) {
                iv.setImageDrawable(new BitmapDrawable(getResources(), bmp));
            }
        }
    }

    private Bitmap decodeScaledBitmap(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int inSample = 1;
            if (opts.outHeight > reqH || opts.outWidth > reqW) {
                int halfH = opts.outHeight / 2;
                int halfW = opts.outWidth / 2;
                while ((halfH / inSample) >= reqH && (halfW / inSample) >= reqW) {
                    inSample *= 2;
                }
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private void updateClock() {
        Date now = new Date();
        clockView.setText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(now));
        dateView.setText(new SimpleDateFormat("EE, dd.MM.yyyy", Locale.GERMANY).format(now).toUpperCase(Locale.GERMANY));
    }

    private void applyModeAndPopulate() {
        String mode = prefs.getString(KEY_MODE, "A");
        boolean isVariantA = "A".equals(mode);

        worldHotspotsContainer.setVisibility(isVariantA ? View.VISIBLE : View.GONE);
        dockScroll.setVisibility(isVariantA ? View.GONE : View.VISIBLE);

        dockContainer.removeAllViews();
        worldHotspotsContainer.removeAllViews();

        PackageManager pm = getPackageManager();
        List<ResolvedApp> detected = detectStreamingApps(pm);

        if (detected.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Keine Streaming-Apps erkannt — druecke [PIP-OS] oder Menue-Taste zum Hinzufuegen.");
            empty.setTextColor(Color.parseColor("#88FF96"));
            empty.setBackgroundResource(R.drawable.pip_clock_bg);
            empty.setPadding(dp(16), dp(10), dp(16), dp(10));
            dockContainer.addView(empty);
            dockScroll.setVisibility(View.VISIBLE);
            return;
        }

        View firstFocus = null;
        if (isVariantA) {
            for (int i = 0; i < detected.size(); i++) {
                ResolvedApp app = detected.get(i);
                float[] frac = WORLD_HOTSPOTS_FRAC[i % WORLD_HOTSPOTS_FRAC.length];
                View sign = createWorldSignView(pm, app, frac[0], frac[1]);
                worldHotspotsContainer.addView(sign);
                if (firstFocus == null) {
                    firstFocus = sign;
                }
            }
        } else {
            for (ResolvedApp app : detected) {
                View card = createDockItemView(pm, app);
                dockContainer.addView(card);
                if (firstFocus == null) {
                    firstFocus = card;
                }
            }
        }

        if (firstFocus != null) {
            firstFocus.requestFocus();
        }
    }

    private List<ResolvedApp> detectStreamingApps(PackageManager pm) {
        List<ResolvedApp> result = new ArrayList<>();
        Set<String> seenPkgs = new LinkedHashSet<>();

        for (AppSlot slot : STREAMING_SLOTS) {
            String pkg = findInstalledPackage(pm, slot.packageCandidates);
            if (pkg != null && !seenPkgs.contains(pkg)) {
                seenPkgs.add(pkg);
                result.add(new ResolvedApp(slot.displayName, slot.landmarkLabel, pkg));
            }
        }

        Set<String> extras = prefs.getStringSet(KEY_EXTRA_APPS, new LinkedHashSet<String>());
        for (String extraPkg : extras) {
            if (!seenPkgs.contains(extraPkg) && hasLaunchIntent(pm, extraPkg)) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(extraPkg, 0);
                    CharSequence lbl = pm.getApplicationLabel(ai);
                    seenPkgs.add(extraPkg);
                    result.add(new ResolvedApp(lbl.toString(), "SEKTOR", extraPkg));
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String findInstalledPackage(PackageManager pm, List<String> candidates) {
        for (String pkg : candidates) {
            if (hasLaunchIntent(pm, pkg)) {
                return pkg;
            }
        }
        return null;
    }

    private boolean hasLaunchIntent(PackageManager pm, String pkg) {
        Intent launch = null;
        if (Build.VERSION.SDK_INT >= 21) {
            launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        }
        if (launch == null) {
            launch = pm.getLaunchIntentForPackage(pkg);
        }
        return launch != null;
    }

    private Drawable loadAppIcon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    private View createDockItemView(PackageManager pm, final ResolvedApp app) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(14), dp(8));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setBackgroundResource(R.drawable.pip_card_selector);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(58));
        lp.setMargins(dp(7), dp(4), dp(7), dp(4));
        card.setLayoutParams(lp);

        Drawable icon = loadAppIcon(pm, app.packageName);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
            ilp.rightMargin = dp(10);
            iv.setLayoutParams(ilp);
            iv.setImageDrawable(icon);
            card.addView(iv);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(app.title.toUpperCase(Locale.GERMANY));
        title.setTextColor(Color.parseColor("#88FF96"));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView sub = new TextView(this);
        sub.setText("STREAM BEREIT");
        sub.setTextColor(Color.parseColor("#9CC99E"));
        sub.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);

        textCol.addView(title);
        textCol.addView(sub);
        card.addView(textCol);

        attachFocusZoom(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app.packageName);
            }
        });
        return card;
    }

    private View createWorldSignView(PackageManager pm, final ResolvedApp app, float xFrac, float yFrac) {
        final LinearLayout plaque = new LinearLayout(this);
        plaque.setOrientation(LinearLayout.HORIZONTAL);
        plaque.setGravity(Gravity.CENTER_VERTICAL);
        plaque.setPadding(dp(10), dp(6), dp(12), dp(6));
        plaque.setFocusable(true);
        plaque.setFocusableInTouchMode(true);
        plaque.setBackgroundResource(R.drawable.world_sign_selector);

        int maxW = Math.max(dp(120), containerWidth - dp(190));
        int maxH = Math.max(dp(80), containerHeight - dp(80));
        int left = Math.min(maxW, Math.max(dp(16), (int) (containerWidth * xFrac)));
        int top = Math.min(maxH, Math.max(dp(64), (int) (containerHeight * yFrac)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        lp.topMargin = top;
        plaque.setLayoutParams(lp);

        Drawable icon = loadAppIcon(pm, app.packageName);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
            ilp.rightMargin = dp(8);
            iv.setLayoutParams(ilp);
            iv.setImageDrawable(icon);
            plaque.addView(iv);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView landmark = new TextView(this);
        landmark.setText(app.landmark);
        landmark.setTextColor(Color.parseColor("#E2C274"));
        landmark.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        landmark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);

        TextView label = new TextView(this);
        label.setText(app.title.toUpperCase(Locale.GERMANY));
        label.setTextColor(Color.parseColor("#88FF96"));
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);

        textCol.addView(landmark);
        textCol.addView(label);
        plaque.addView(textCol);

        attachFocusZoom(plaque);
        plaque.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(app.packageName);
            }
        });
        return plaque;
    }

    private void attachFocusZoom(final View view) {
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float scale = hasFocus ? 1.12f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140L).start();
            }
        });
    }

    private void launchApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = null;
        if (Build.VERSION.SDK_INT >= 21) {
            intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        }
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        if (intent != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, "App konnte nicht gestartet werden: " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettingsDialog() {
        final String currentMode = prefs.getString(KEY_MODE, "A");
        int currentInterval = prefs.getInt(KEY_INTERVAL_MIN, 5);
        String modeDesc = "A".equals(currentMode) ? "Variante A (Welt-Schilder)" : "Variante B (App-Dock)";
        String intervalDesc = currentInterval > 0 ? (currentInterval + " Min.") : "Aus";

        String[] options = new String[]{
                "Ansicht wechseln (Aktuell: " + modeDesc + ")",
                "Hintergrundwechsel-Zeit (Aktuell: " + intervalDesc + ")",
                "Naechste Fallout-Landschaft jetzt anzeigen",
                "Weitere Streaming-App hinzufuegen",
                "Zusaetzliche App entfernen",
                "Amazon Fire TV Startseite oeffnen (60 Sek. Pause)",
                "Fire TV Einstellungen oeffnen"
        };

        new AlertDialog.Builder(this)
                .setTitle("PIP-OS KONFIGURATION")
                .setItems(options, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            String next = "A".equals(currentMode) ? "B" : "A";
                            prefs.edit().putString(KEY_MODE, next).apply();
                            applyModeAndPopulate();
                        } else if (which == 1) {
                            showIntervalPicker();
                        } else if (which == 2) {
                            switchBackgroundSmooth();
                        } else if (which == 3) {
                            showAddAppPicker();
                        } else if (which == 4) {
                            showRemoveExtraAppPicker();
                        } else if (which == 5) {
                            openAmazonHomeSafely();
                        } else if (which == 6) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Einstellungen nicht erreichbar", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .show();
    }

    private void openAmazonHomeSafely() {
        // Pausiert den Home-Tasten-Dienst fuer 60 Sekunden, damit Amazon Home direkt nutzbar ist
        prefs.edit().putLong(KEY_AMAZON_BYPASS_UNTIL, System.currentTimeMillis() + 60000L).apply();
        Intent amazonHome = new Intent(Intent.ACTION_MAIN);
        amazonHome.addCategory(Intent.CATEGORY_HOME);
        amazonHome.setPackage("com.amazon.tv.launcher");
        try {
            startActivity(amazonHome);
        } catch (Exception e) {
            Toast.makeText(this, "Amazon Startseite nicht direkt erreichbar", Toast.LENGTH_SHORT).show();
        }
    }

    private void showIntervalPicker() {
        final int[] values = new int[]{1, 2, 3, 5, 10, 15, 30, 60, 0};
        final String[] labels = new String[]{
                "1 Minute", "2 Minuten", "3 Minuten", "5 Minuten (Standard)",
                "10 Minuten", "15 Minuten", "30 Minuten", "60 Minuten", "Aus (Feste Landschaft)"
        };
        new AlertDialog.Builder(this)
                .setTitle("HINTERGRUNDWECHSEL-INTERVALL")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        prefs.edit().putInt(KEY_INTERVAL_MIN, values[i]).apply();
                        scheduleNextBackgroundSwitch();
                    }
                })
                .show();
    }

    private void showAddAppPicker() {
        PackageManager pm = getPackageManager();
        Set<String> seen = new LinkedHashSet<>();
        final List<String> names = new ArrayList<>();
        final List<String> pkgs = new ArrayList<>();

        String[] cats = new String[]{
                Intent.CATEGORY_LEANBACK_LAUNCHER,
                Intent.CATEGORY_LAUNCHER
        };
        List<ResolveInfo> all = new ArrayList<>();
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN, null);
            q.addCategory(cat);
            all.addAll(pm.queryIntentActivities(q, 0));
        }

        Collections.sort(all, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });

        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (!seen.contains(p) && !getPackageName().equals(p)) {
                seen.add(p);
                names.add(ri.loadLabel(pm).toString());
                pkgs.add(p);
            }
        }

        if (names.isEmpty()) {
            Toast.makeText(this, "Keine weiteren Apps gefunden", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("APP HINZUFUEGEN")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        Set<String> extras = new LinkedHashSet<>(
                                prefs.getStringSet(KEY_EXTRA_APPS, new LinkedHashSet<String>()));
                        extras.add(pkgs.get(i));
                        prefs.edit().putStringSet(KEY_EXTRA_APPS, extras).apply();
                        applyModeAndPopulate();
                    }
                })
                .show();
    }

    private void showRemoveExtraAppPicker() {
        final List<String> extrasList = new ArrayList<>(
                prefs.getStringSet(KEY_EXTRA_APPS, new LinkedHashSet<String>()));
        if (extrasList.isEmpty()) {
            Toast.makeText(this, "Keine zusaetzlichen Apps gespeichert", Toast.LENGTH_SHORT).show();
            return;
        }
        PackageManager pm = getPackageManager();
        String[] labels = new String[extrasList.size()];
        for (int i = 0; i < extrasList.size(); i++) {
            try {
                labels[i] = pm.getApplicationLabel(pm.getApplicationInfo(extrasList.get(i), 0)).toString();
            } catch (Exception e) {
                labels[i] = extrasList.get(i);
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("ZUSATZ-APP ENTFERNEN")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        Set<String> updated = new LinkedHashSet<>(extrasList);
                        updated.remove(extrasList.get(i));
                        prefs.edit().putStringSet(KEY_EXTRA_APPS, updated).apply();
                        applyModeAndPopulate();
                    }
                })
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettingsDialog();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true; // Verhindert versehentliches Schliessen des Launchers
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static class AppSlot {
        final String displayName;
        final String landmarkLabel;
        final List<String> packageCandidates;

        AppSlot(String d, String l, List<String> p) {
            this.displayName = d;
            this.landmarkLabel = l;
            this.packageCandidates = p;
        }
    }

    private static class ResolvedApp {
        final String title;
        final String landmark;
        final String packageName;

        ResolvedApp(String t, String l, String p) {
            this.title = t;
            this.landmark = l;
            this.packageName = p;
        }
    }
}
