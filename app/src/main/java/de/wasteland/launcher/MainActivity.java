package de.wasteland.launcher;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
 * Wasteland TV Launcher v5
 * - Retro 1950er Fallout Roehrenfernseher (zeigt Fallout "PLEASE STAND BY" Testbild & wechselt beim Ueberfahren
 *   einer Streaming-Figur auf das entsprechende TV-Startbild / Logo)
 * - 6 eingebaute Fallout-Spielwelten (inkl. Red Rocket Station & Vault 111 Eingang) + eigene Bilder
 * - 10 Fallout-Figuren (6 Haupt-Figuren + 4 ausbaubare Platzhalterfiguren: Mr. Handy, Protectron, Vault-Kiste, Holotape)
 * - Freier Platzierungs-Editor direkt mit dem Fire-TV-Steuerkreuz fuer jede Figur & den Retro-TV
 */
public class MainActivity extends Activity {

    private static final String PREFS_NAME = "WastelandPrefs";
    private static final String KEY_MODE = "ui_mode";
    private static final String KEY_INTERVAL_MIN = "bg_interval_min";
    private static final String KEY_EXTRA_APPS = "extra_apps";
    private static final String KEY_AMAZON_BYPASS_UNTIL = "amazon_bypass_until";
    private static final String KEY_ONLY_CUSTOM_BG = "only_custom_bg";

    private static final List<AppSlot> STREAMING_SLOTS = Arrays.asList(
            new AppSlot("netflix", "Netflix", "POWERRUESTUNG", R.drawable.fig_powerrustung, R.drawable.tv_screen_netflix,
                    Arrays.asList("com.netflix.ninja", "com.netflix.mediaclient")),
            new AppSlot("youtube", "YouTube", "VAULT-BOY", R.drawable.fig_vaultboy, R.drawable.tv_screen_youtube,
                    Arrays.asList("com.amazon.firetv.youtube", "com.google.android.youtube.tv",
                            "com.google.android.youtube", "com.liskovsoft.smarttubetv.beta",
                            "com.teamsmart.videomanager.tv")),
            new AppSlot("disney", "Disney+", "TODESKRALLE", R.drawable.fig_todeskralle, R.drawable.tv_screen_disney,
                    Arrays.asList("com.disney.disneyplus")),
            new AppSlot("waipu", "waipu.tv", "NUKA-COLA", R.drawable.fig_nukacola, R.drawable.tv_screen_waipu,
                    Arrays.asList("de.exaring.waipu.firetv", "de.exaring.waipu", "de.exaring.waipu.tv")),
            new AppSlot("prime", "Prime Video", "RED ROCKET", R.drawable.fig_redrocket, R.drawable.tv_screen_prime,
                    Arrays.asList("com.amazon.avod", "com.amazon.firebat", "com.amazon.amazonvideo.livingroom")),
            new AppSlot("twitch", "Twitch", "ROBCO-OS", R.drawable.fig_robco, R.drawable.tv_screen_twitch,
                    Arrays.asList("tv.twitch.android.app", "tv.twitch.android.viewer"))
    );

    // 4 ausbaubare Platzhalterfiguren fuer weitere Apps / Funktionen
    private static final List<FigurePreset> FIGURE_CATALOG = Arrays.asList(
            new FigurePreset("mrhandy", "Mr. Handy (Platzhalter)", "MR. HANDY", R.drawable.fig_mrhandy),
            new FigurePreset("protectron", "Protectron (Platzhalter)", "PROTECTRON", R.drawable.fig_protectron),
            new FigurePreset("vaultkiste", "Vault-Tec Kiste (Platzhalter)", "VAULT-KISTE", R.drawable.fig_vaultkiste),
            new FigurePreset("holotape", "Holotape-Modul (Platzhalter)", "HOLOTAPE", R.drawable.fig_holotape),
            new FigurePreset("powerrustung", "Powerrustung T-60", "POWERRUESTUNG", R.drawable.fig_powerrustung),
            new FigurePreset("vaultboy", "Vault-Boy Wackelkopf", "VAULT-BOY", R.drawable.fig_vaultboy),
            new FigurePreset("todeskralle", "Todeskralle", "TODESKRALLE", R.drawable.fig_todeskralle),
            new FigurePreset("nukacola", "Nuka-Cola Automat", "NUKA-COLA", R.drawable.fig_nukacola),
            new FigurePreset("redrocket", "Red Rocket Pylon", "RED ROCKET", R.drawable.fig_redrocket),
            new FigurePreset("robco", "RobCo Terminal", "ROBCO-OS", R.drawable.fig_robco)
    );

    // Standard-Positionen in der Landschaft (inkl. Platz fuer den Retro-TV auf 0.42, 0.36)
    private static final float[][] WORLD_HOTSPOTS_FRAC = new float[][]{
            {0.05f, 0.44f}, // 0: Powerrustung (Netflix)
            {0.23f, 0.56f}, // 1: Vault-Boy (YouTube)
            {0.62f, 0.44f}, // 2: Todeskralle (Disney+)
            {0.79f, 0.56f}, // 3: Nuka-Cola (waipu.tv)
            {0.08f, 0.72f}, // 4: Red Rocket (Prime Video)
            {0.28f, 0.73f}, // 5: RobCo (Twitch)
            {0.58f, 0.72f}, // 6: Platzhalter-Slot 1 (Mr. Handy)
            {0.77f, 0.72f}, // 7: Platzhalter-Slot 2 (Protectron)
            {0.43f, 0.73f}, // 8: Platzhalter-Slot 3 (Vault-Kiste)
            {0.15f, 0.32f}  // 9: Platzhalter-Slot 4 (Holotape)
    };

    private static final float DEFAULT_TV_X = 0.41f;
    private static final float DEFAULT_TV_Y = 0.36f;

    private final int[] builtInBackgrounds = new int[]{
            R.drawable.bg_01_traktor_oedland,
            R.drawable.bg_02_appalachia_kraftwerk,
            R.drawable.bg_03_red_rocket_station,
            R.drawable.bg_04_vault_eingang_oedland,
            R.drawable.bg_05_traktor_abendrot,
            R.drawable.bg_06_appalachia_nacht
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

    // Retro CRT TV Views
    private FrameLayout retroTvContainer;
    private ImageView retroTvScreen;
    private LinearLayout retroTvCustomOverlay;
    private ImageView retroTvCustomIcon;
    private TextView retroTvCustomTitle;

    // Freier Platzierungs-Modus
    private TextView placementHud;
    private View activePlacementView = null;
    private String activePlacementKey = null;
    private long placementExitLockUntil = 0L;
    private boolean suppressInitialTvPreview = false;

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

    private final Runnable tvIdleStandbyRunnable = new Runnable() {
        @Override
        public void run() {
            showTvStandbyTestCard();
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
        placementHud = (TextView) findViewById(R.id.placement_hud);

        retroTvContainer = (FrameLayout) findViewById(R.id.retro_tv_container);
        retroTvScreen = (ImageView) findViewById(R.id.retro_tv_screen);
        retroTvCustomOverlay = (LinearLayout) findViewById(R.id.retro_tv_custom_overlay);
        retroTvCustomIcon = (ImageView) findViewById(R.id.retro_tv_custom_icon);
        retroTvCustomTitle = (TextView) findViewById(R.id.retro_tv_custom_title);

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activePlacementView != null) {
                    exitPlacementMode();
                } else {
                    openSettingsDialog();
                }
            }
        });
        btnSettings.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    showTvStandbyTestCard();
                }
            }
        });

        retroTvContainer.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float sc = hasFocus ? 1.07f : 1.0f;
                v.animate().scaleX(sc).scaleY(sc).setDuration(150L).start();
                if (hasFocus) {
                    showTvStandbyTestCard();
                }
            }
        });
        retroTvContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activePlacementView != null || System.currentTimeMillis() < placementExitLockUntil) {
                    if (activePlacementView != null) exitPlacementMode();
                    return;
                }
                switchBackgroundSmooth();
                Toast.makeText(MainActivity.this, "Radiation King TV: Naechster Sektor", Toast.LENGTH_SHORT).show();
            }
        });

        loadWallpaperSources();
        if (!wallpaperSources.isEmpty()) {
            applyDrawableToView(bgImageA, wallpaperSources.get(0));
        }
        bgImageA.setAlpha(1.0f);
        bgImageB.setAlpha(0.0f);

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
        prefs.edit().putLong(KEY_AMAZON_BYPASS_UNTIL, 0L).apply();
        if (containerWidth > 0 && containerHeight > 0 && activePlacementView == null) {
            applyModeAndPopulate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(bgSwitchRunnable);
        handler.removeCallbacks(tvIdleStandbyRunnable);
    }

    private void showTvStandbyTestCard() {
        retroTvCustomOverlay.setVisibility(View.GONE);
        retroTvScreen.setVisibility(View.VISIBLE);
        retroTvScreen.setImageResource(R.drawable.tv_screen_standby);
    }

    private void showTvAppPreview(PackageManager pm, ResolvedApp app) {
        if (app.tvScreenResId != 0) {
            retroTvCustomOverlay.setVisibility(View.GONE);
            retroTvScreen.setVisibility(View.VISIBLE);
            retroTvScreen.setImageResource(app.tvScreenResId);
        } else {
            retroTvScreen.setVisibility(View.GONE);
            retroTvCustomOverlay.setVisibility(View.VISIBLE);
            Drawable icon = loadAppIcon(pm, app.packageName);
            if (icon != null) {
                retroTvCustomIcon.setImageDrawable(icon);
            }
            retroTvCustomTitle.setText(app.title.toUpperCase(Locale.GERMANY));
        }
        // Kurzes Roehren-Aufblitzen beim Kanalwechsel
        retroTvContainer.setAlpha(0.84f);
        retroTvContainer.animate().alpha(1.0f).setDuration(140L).start();
        // Nach 18 Sekunden ohne Bedienung kehrt der Retro-TV automatisch zum Fallout-Testbild zurueck
        handler.removeCallbacks(tvIdleStandbyRunnable);
        handler.postDelayed(tvIdleStandbyRunnable, 18000L);
    }

    private void positionRetroTv() {
        float xFrac = prefs.getFloat("pos_x_retro_tv", DEFAULT_TV_X);
        float yFrac = prefs.getFloat("pos_y_retro_tv", DEFAULT_TV_Y);
        int tvW = dp(210);
        int tvH = dp(164);
        int maxW = Math.max(dp(40), containerWidth - tvW - dp(12));
        int maxH = Math.max(dp(60), containerHeight - tvH - dp(12));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) retroTvContainer.getLayoutParams();
        lp.leftMargin = Math.min(maxW, Math.max(dp(12), (int) (containerWidth * xFrac)));
        lp.topMargin = Math.min(maxH, Math.max(dp(58), (int) (containerHeight * yFrac)));
        retroTvContainer.setLayoutParams(lp);
    }

    private void loadWallpaperSources() {
        wallpaperSources.clear();
        List<File> customFiles = new ArrayList<>();
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
                            if (!low.startsWith("bg_01_mojave") && !low.startsWith("bg_02_ruinen")
                                    && !low.startsWith("bg_03_ueberwucherte") && !low.startsWith("bg_04_nacht")) {
                                customFiles.add(f);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        boolean onlyCustom = prefs.getBoolean(KEY_ONLY_CUSTOM_BG, false);
        if (onlyCustom && !customFiles.isEmpty()) {
            wallpaperSources.addAll(customFiles);
        } else {
            wallpaperSources.addAll(customFiles);
            for (int resId : builtInBackgrounds) {
                wallpaperSources.add(resId);
            }
        }
        currentBgIndex = 0;
    }

    private void scheduleNextBackgroundSwitch() {
        handler.removeCallbacks(bgSwitchRunnable);
        handler.removeCallbacks(tvIdleStandbyRunnable);
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

    private Bitmap loadCustomFigureBitmap(String slug, String pkgName) {
        try {
            File figDir = new File(Environment.getExternalStorageDirectory(), "FalloutLauncher/Figuren");
            if (!figDir.exists()) {
                return null;
            }
            String[] keys = new String[]{slug, pkgName};
            String[] exts = new String[]{".png", ".jpg", ".jpeg"};
            for (String k : keys) {
                if (k == null) continue;
                for (String ext : exts) {
                    File f = new File(figDir, k + ext);
                    if (f.exists() && f.isFile()) {
                        return decodeScaledBitmap(f.getAbsolutePath(), 300, 300);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
            if (reqW >= 1280) {
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
            }
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
        retroTvContainer.setVisibility(isVariantA ? View.VISIBLE : View.GONE);
        dockScroll.setVisibility(isVariantA ? View.GONE : View.VISIBLE);

        if (isVariantA) {
            positionRetroTv();
            showTvStandbyTestCard();
        }

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
                float[] defaultFrac = WORLD_HOTSPOTS_FRAC[i % WORLD_HOTSPOTS_FRAC.length];
                float xFrac = prefs.getFloat("pos_x_" + app.packageName, defaultFrac[0]);
                float yFrac = prefs.getFloat("pos_y_" + app.packageName, defaultFrac[1]);
                View figureTile = createWorldFigureView(pm, app, xFrac, yFrac);
                worldHotspotsContainer.addView(figureTile);
                if (firstFocus == null) {
                    firstFocus = figureTile;
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
            suppressInitialTvPreview = true;
            firstFocus.requestFocus();
            suppressInitialTvPreview = false;
        }
    }

    private FigurePreset resolveFigureOverride(String pkg, String defaultLandmark, int defaultResId) {
        String presetId = prefs.getString("fig_override_" + pkg, null);
        if (presetId != null) {
            for (FigurePreset fp : FIGURE_CATALOG) {
                if (fp.id.equals(presetId)) {
                    return fp;
                }
            }
        }
        return new FigurePreset("default", defaultLandmark, defaultLandmark, defaultResId);
    }

    private List<ResolvedApp> detectStreamingApps(PackageManager pm) {
        List<ResolvedApp> result = new ArrayList<>();
        Set<String> seenPkgs = new LinkedHashSet<>();

        for (AppSlot slot : STREAMING_SLOTS) {
            String pkg = findInstalledPackage(pm, slot.packageCandidates);
            if (pkg != null && !seenPkgs.contains(pkg)) {
                seenPkgs.add(pkg);
                FigurePreset fp = resolveFigureOverride(pkg, slot.landmarkLabel, slot.figureResId);
                result.add(new ResolvedApp(slot.slug, slot.displayName, fp.badge, fp.resId, slot.tvScreenResId, pkg));
            }
        }

        Set<String> extras = prefs.getStringSet(KEY_EXTRA_APPS, new LinkedHashSet<String>());
        int placeholderIdx = 0;
        for (String extraPkg : extras) {
            if (!seenPkgs.contains(extraPkg) && hasLaunchIntent(pm, extraPkg)) {
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(extraPkg, 0);
                    CharSequence lbl = pm.getApplicationLabel(ai);
                    seenPkgs.add(extraPkg);
                    FigurePreset defaultPh = FIGURE_CATALOG.get(placeholderIdx % 4);
                    placeholderIdx++;
                    FigurePreset fp = resolveFigureOverride(extraPkg, defaultPh.badge, defaultPh.resId);
                    result.add(new ResolvedApp("extra_" + placeholderIdx, lbl.toString(), fp.badge, fp.resId, 0, extraPkg));
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

    private View createWorldFigureView(final PackageManager pm, final ResolvedApp app, float xFrac, float yFrac) {
        final LinearLayout wrapper = new LinearLayout(this);
        wrapper.setTag(app);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_HORIZONTAL);
        wrapper.setPadding(dp(6), dp(4), dp(6), dp(6));
        wrapper.setFocusable(true);
        wrapper.setFocusableInTouchMode(true);
        wrapper.setBackgroundResource(R.drawable.world_figure_selector);
        wrapper.setClipChildren(false);
        wrapper.setClipToPadding(false);

        int maxW = Math.max(dp(120), containerWidth - dp(155));
        int maxH = Math.max(dp(100), containerHeight - dp(145));
        int left = Math.min(maxW, Math.max(dp(12), (int) (containerWidth * xFrac)));
        int top = Math.min(maxH, Math.max(dp(58), (int) (containerHeight * yFrac)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        lp.topMargin = top;
        wrapper.setLayoutParams(lp);

        final ImageView figureImg = new ImageView(this);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(82), dp(82));
        figureImg.setLayoutParams(flp);
        figureImg.setScaleType(ImageView.ScaleType.FIT_CENTER);

        Bitmap customBmp = loadCustomFigureBitmap(app.slug, app.packageName);
        if (customBmp != null) {
            figureImg.setImageBitmap(customBmp);
        } else {
            figureImg.setImageResource(app.figureResId);
        }
        wrapper.addView(figureImg);

        final LinearLayout plaque = new LinearLayout(this);
        plaque.setOrientation(LinearLayout.HORIZONTAL);
        plaque.setGravity(Gravity.CENTER_VERTICAL);
        plaque.setPadding(dp(8), dp(4), dp(10), dp(4));
        plaque.setBackgroundResource(R.drawable.world_plaque_normal);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(-6);
        plaque.setLayoutParams(plp);

        Drawable icon = loadAppIcon(pm, app.packageName);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(19), dp(19));
            ilp.rightMargin = dp(6);
            iv.setLayoutParams(ilp);
            iv.setImageDrawable(icon);
            plaque.addView(iv);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(this);
        label.setText(app.title.toUpperCase(Locale.GERMANY));
        label.setTextColor(Color.parseColor("#88FF96"));
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

        TextView sub = new TextView(this);
        sub.setText(app.landmark);
        sub.setTextColor(Color.parseColor("#E2C274"));
        sub.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);

        textCol.addView(label);
        textCol.addView(sub);
        plaque.addView(textCol);
        wrapper.addView(plaque);

        wrapper.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                plaque.setBackgroundResource(hasFocus
                        ? R.drawable.world_plaque_focused
                        : R.drawable.world_plaque_normal);
                float targetScale = hasFocus ? 1.12f : 1.0f;
                v.animate().scaleX(targetScale).scaleY(targetScale).setDuration(150L).start();
                if (hasFocus) {
                    if (!suppressInitialTvPreview) {
                        playBobbleWobble(figureImg);
                        showTvAppPreview(pm, app);
                    }
                }
            }
        });

        wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activePlacementView != null || System.currentTimeMillis() < placementExitLockUntil) {
                    if (activePlacementView != null) exitPlacementMode();
                    return;
                }
                playClickBounceAndLaunch(v, app.packageName);
            }
        });
        return wrapper;
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

        final ImageView figThumb = new ImageView(this);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
        ilp.rightMargin = dp(10);
        figThumb.setLayoutParams(ilp);
        figThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);

        Bitmap customBmp = loadCustomFigureBitmap(app.slug, app.packageName);
        if (customBmp != null) {
            figThumb.setImageBitmap(customBmp);
        } else {
            figThumb.setImageResource(app.figureResId);
        }
        card.addView(figThumb);

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
        sub.setText(app.landmark);
        sub.setTextColor(Color.parseColor("#D8BA72"));
        sub.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);

        textCol.addView(title);
        textCol.addView(sub);
        card.addView(textCol);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float scale = hasFocus ? 1.10f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(140L).start();
                if (hasFocus) {
                    playBobbleWobble(figThumb);
                }
            }
        });

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playClickBounceAndLaunch(v, app.packageName);
            }
        });
        return card;
    }

    private void playBobbleWobble(View target) {
        if (target.getWidth() > 0 && target.getHeight() > 0) {
            target.setPivotX(target.getWidth() * 0.5f);
            target.setPivotY(target.getHeight() * 0.92f);
        }
        ObjectAnimator wobble = ObjectAnimator.ofFloat(
                target, "rotation", 0f, -8.5f, 7f, -4.5f, 2.5f, -1f, 0f);
        wobble.setDuration(520L);
        wobble.start();
    }

    private void playClickBounceAndLaunch(final View target, final String pkg) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(target, "scaleX", 1.12f, 0.94f, 1.08f, 1.0f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(target, "scaleY", 1.12f, 0.94f, 1.08f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(150L);
        set.start();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                launchApp(pkg);
            }
        }, 120L);
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
        final boolean onlyCustom = prefs.getBoolean(KEY_ONLY_CUSTOM_BG, false);

        String modeDesc = "A".equals(currentMode) ? "Variante A (Figuren + Retro-TV)" : "Variante B (App-Dock)";
        String intervalDesc = currentInterval > 0 ? (currentInterval + " Min.") : "Aus";
        String bgModeDesc = onlyCustom ? "Nur eigene Bilder" : "Eigene + Eingebaute (" + wallpaperSources.size() + ")";

        String[] options = new String[]{
                "Figur oder Retro-TV frei auf Bildschirm platzieren",
                "Figur fuer eine App wechseln (inkl. Platzhalter)",
                "Weitere App / Funktion hinzufuegen (bekommt Platzhalterfigur)",
                "Zusaetzliche App entfernen",
                "Naechste Fallout-Landschaft jetzt anzeigen",
                "Hintergrundwechsel-Zeit (Aktuell: " + intervalDesc + ")",
                "Hintergrund-Quelle (" + bgModeDesc + ")",
                "Ansicht wechseln (Aktuell: " + modeDesc + ")",
                "Eigene Bilder & Figuren neu einlesen",
                "Alle Positionen auf Standard zuruecksetzen",
                "Amazon Fire TV Startseite oeffnen (60 Sek. Pause)",
                "Fire TV Einstellungen oeffnen"
        };

        new AlertDialog.Builder(this)
                .setTitle("PIP-OS KONFIGURATION v5")
                .setItems(options, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            showPlacementTargetPicker();
                        } else if (which == 1) {
                            showChangeFigureAppPicker();
                        } else if (which == 2) {
                            showAddAppPicker();
                        } else if (which == 3) {
                            showRemoveExtraAppPicker();
                        } else if (which == 4) {
                            switchBackgroundSmooth();
                        } else if (which == 5) {
                            showIntervalPicker();
                        } else if (which == 6) {
                            prefs.edit().putBoolean(KEY_ONLY_CUSTOM_BG, !onlyCustom).apply();
                            loadWallpaperSources();
                            if (!wallpaperSources.isEmpty()) {
                                applyDrawableToView(showingA ? bgImageA : bgImageB, wallpaperSources.get(0));
                            }
                            Toast.makeText(MainActivity.this,
                                    "Aktive Hintergruende: " + wallpaperSources.size(), Toast.LENGTH_SHORT).show();
                        } else if (which == 7) {
                            String next = "A".equals(currentMode) ? "B" : "A";
                            prefs.edit().putString(KEY_MODE, next).apply();
                            applyModeAndPopulate();
                        } else if (which == 8) {
                            loadWallpaperSources();
                            if (!wallpaperSources.isEmpty()) {
                                applyDrawableToView(showingA ? bgImageA : bgImageB, wallpaperSources.get(0));
                            }
                            applyModeAndPopulate();
                            Toast.makeText(MainActivity.this,
                                    "Neu geladen (" + wallpaperSources.size() + " Hintergruende)", Toast.LENGTH_SHORT).show();
                        } else if (which == 9) {
                            resetAllPositions();
                        } else if (which == 10) {
                            openAmazonHomeSafely();
                        } else if (which == 11) {
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

    private void showPlacementTargetPicker() {
        if (!"A".equals(prefs.getString(KEY_MODE, "A"))) {
            prefs.edit().putString(KEY_MODE, "A").apply();
            applyModeAndPopulate();
        }
        final List<String> names = new ArrayList<>();
        final List<View> views = new ArrayList<>();
        final List<String> keys = new ArrayList<>();

        names.add("Retro-Roehrenfernseher (Testbild / Vorschau-TV)");
        views.add(retroTvContainer);
        keys.add("retro_tv");

        for (int i = 0; i < worldHotspotsContainer.getChildCount(); i++) {
            View child = worldHotspotsContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof ResolvedApp) {
                ResolvedApp app = (ResolvedApp) tag;
                names.add(app.title + " (" + app.landmark + ")");
                views.add(child);
                keys.add(app.packageName);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("WELCHES ELEMENT VERSCHIEBEN?")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        enterPlacementMode(views.get(i), keys.get(i), names.get(i));
                    }
                })
                .show();
    }

    private void enterPlacementMode(View target, String key, String label) {
        activePlacementView = target;
        activePlacementKey = key;
        placementHud.setText("VERSCHIEBEN (" + label + "): Steuerkreuz | OK = Fertig");
        placementHud.setVisibility(View.VISIBLE);
        target.requestFocus();
        target.animate().scaleX(1.18f).scaleY(1.18f).setDuration(150L).start();
    }

    private void exitPlacementMode() {
        if (activePlacementView != null) {
            activePlacementView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150L).start();
        }
        activePlacementView = null;
        activePlacementKey = null;
        placementExitLockUntil = System.currentTimeMillis() + 450L;
        placementHud.setVisibility(View.GONE);
        Toast.makeText(this, "Position gespeichert!", Toast.LENGTH_SHORT).show();
    }

    private void nudgeActivePlacement(int dxPx, int dyPx) {
        if (activePlacementView == null || activePlacementKey == null || containerWidth <= 0 || containerHeight <= 0) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) activePlacementView.getLayoutParams();
        int maxW = Math.max(dp(40), containerWidth - Math.max(dp(120), activePlacementView.getWidth()));
        int maxH = Math.max(dp(60), containerHeight - Math.max(dp(110), activePlacementView.getHeight()));
        int newLeft = Math.min(maxW, Math.max(dp(10), lp.leftMargin + dxPx));
        int newTop = Math.min(maxH, Math.max(dp(54), lp.topMargin + dyPx));
        lp.leftMargin = newLeft;
        lp.topMargin = newTop;
        activePlacementView.setLayoutParams(lp);

        prefs.edit()
                .putFloat("pos_x_" + activePlacementKey, (float) newLeft / (float) containerWidth)
                .putFloat("pos_y_" + activePlacementKey, (float) newTop / (float) containerHeight)
                .apply();
    }

    private void resetAllPositions() {
        SharedPreferences.Editor ed = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (k.startsWith("pos_x_") || k.startsWith("pos_y_")) {
                ed.remove(k);
            }
        }
        ed.apply();
        applyModeAndPopulate();
        Toast.makeText(this, "Alle Positionen auf Standard zurueckgesetzt", Toast.LENGTH_SHORT).show();
    }

    private void showChangeFigureAppPicker() {
        final List<ResolvedApp> apps = detectStreamingApps(getPackageManager());
        if (apps.isEmpty()) {
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).title + " (Aktuell: " + apps.get(i).landmark + ")";
        }
        new AlertDialog.Builder(this)
                .setTitle("APP FUER FIGUR-WECHSEL WAEHLEN")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        showFigureCatalogPicker(apps.get(i));
                    }
                })
                .show();
    }

    private void showFigureCatalogPicker(final ResolvedApp app) {
        String[] names = new String[FIGURE_CATALOG.size()];
        for (int i = 0; i < FIGURE_CATALOG.size(); i++) {
            names[i] = FIGURE_CATALOG.get(i).title;
        }
        new AlertDialog.Builder(this)
                .setTitle("FIGUR FUER " + app.title.toUpperCase(Locale.GERMANY))
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        FigurePreset chosen = FIGURE_CATALOG.get(i);
                        prefs.edit().putString("fig_override_" + app.packageName, chosen.id).apply();
                        applyModeAndPopulate();
                    }
                })
                .show();
    }

    private void openAmazonHomeSafely() {
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
        final PackageManager pm = getPackageManager();
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
                .setTitle("APP / FUNKTION HINZUFUEGEN")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int i) {
                        Set<String> extras = new LinkedHashSet<>(
                                prefs.getStringSet(KEY_EXTRA_APPS, new LinkedHashSet<String>()));
                        extras.add(pkgs.get(i));
                        prefs.edit().putStringSet(KEY_EXTRA_APPS, extras).apply();
                        applyModeAndPopulate();
                        Toast.makeText(MainActivity.this,
                                "Hinzugefuegt! Ueber [PIP-OS] frei platzierbar.", Toast.LENGTH_SHORT).show();
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
    public void onBackPressed() {
        if (activePlacementView != null) {
            exitPlacementMode();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            int code = event.getKeyCode();
            if (activePlacementView != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int step = dp(18);
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                        nudgeActivePlacement(-step, 0);
                        return true;
                    } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        nudgeActivePlacement(step, 0);
                        return true;
                    } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                        nudgeActivePlacement(0, -step);
                        return true;
                    } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                        nudgeActivePlacement(0, step);
                        return true;
                    } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
                        exitPlacementMode();
                        return true;
                    }
                }
                return true;
            }
            if (System.currentTimeMillis() < placementExitLockUntil && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)) {
                return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (activePlacementView != null) {
                exitPlacementMode();
            } else {
                openSettingsDialog();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static class FigurePreset {
        final String id;
        final String title;
        final String badge;
        final int resId;

        FigurePreset(String id, String title, String badge, int resId) {
            this.id = id;
            this.title = title;
            this.badge = badge;
            this.resId = resId;
        }
    }

    private static class AppSlot {
        final String slug;
        final String displayName;
        final String landmarkLabel;
        final int figureResId;
        final int tvScreenResId;
        final List<String> packageCandidates;

        AppSlot(String s, String d, String l, int fig, int tvScr, List<String> p) {
            this.slug = s;
            this.displayName = d;
            this.landmarkLabel = l;
            this.figureResId = fig;
            this.tvScreenResId = tvScr;
            this.packageCandidates = p;
        }
    }

    private static class ResolvedApp {
        final String slug;
        final String title;
        final String landmark;
        final int figureResId;
        final int tvScreenResId;
        final String packageName;

        ResolvedApp(String s, String t, String l, int fig, int tvScr, String p) {
            this.slug = s;
            this.title = t;
            this.landmark = l;
            this.figureResId = fig;
            this.tvScreenResId = tvScr;
            this.packageName = p;
        }
    }
}
