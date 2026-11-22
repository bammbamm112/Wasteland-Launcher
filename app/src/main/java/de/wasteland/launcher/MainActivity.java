package de.wasteland.launcher;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private ImageView bgImageBack;
    private ImageView bgImageFront;
    private RetroTvView retroTvView;
    private FrameLayout itemsLayer;
    private TextView clockView;
    private TextView editBannerView;
    private TextView focusHudTitle;
    private TextView focusHudSub;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<String> backgroundFiles = new ArrayList<>();
    private int currentBgIndex = 0;
    private List<LauncherItem> items = new ArrayList<>();
    private final List<View> itemViews = new ArrayList<>();

    private boolean freeEditMode = false;
    private int freeEditSelectedIdx = -1;
    private List<LauncherItem> visibleItems = new ArrayList<>();
    private ObjectAnimator activeHoverBob = null;

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.GERMANY);
            clockView.setText(sdf.format(new Date()));
            handler.postDelayed(this, 15000);
        }
    };

    private final Runnable bgRotateRunnable = new Runnable() {
        @Override
        public void run() {
            if (LauncherPrefs.BG_AUTO.equals(LauncherPrefs.getSelectedBackground(MainActivity.this))
                    && backgroundFiles.size() > 1) {
                currentBgIndex = (currentBgIndex + 1) % backgroundFiles.size();
                applyBackground(backgroundFiles.get(currentBgIndex), true);
            }
            handler.postDelayed(this, 45000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.BLACK);
        rootLayout.setClipChildren(false);
        setContentView(rootLayout);

        bgImageBack = new ImageView(this);
        bgImageBack.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootLayout.addView(bgImageBack, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bgImageFront = new ImageView(this);
        bgImageFront.setScaleType(ImageView.ScaleType.CENTER_CROP);
        rootLayout.addView(bgImageFront, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View vignette = new View(this);
        GradientDrawable vig = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#35000000"), Color.parseColor("#00000000"), Color.parseColor("#99000000")}
        );
        vignette.setBackground(vig);
        rootLayout.addView(vignette, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        retroTvView = new RetroTvView(this);
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(dp(248), dp(216));
        tvLp.gravity = Gravity.TOP | Gravity.END;
        tvLp.topMargin = dp(20);
        tvLp.rightMargin = dp(26);
        rootLayout.addView(retroTvView, tvLp);

        LinearLayout topHud = new LinearLayout(this);
        topHud.setOrientation(LinearLayout.VERTICAL);
        topHud.setPadding(dp(14), dp(8), dp(16), dp(8));
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(Color.parseColor("#AA07160B"));
        hudBg.setStroke(dp(1), Color.parseColor("#AA33FF66"));
        hudBg.setCornerRadius(dp(6));
        topHud.setBackground(hudBg);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        clockView = new TextView(this);
        clockView.setTextColor(Color.parseColor("#FFE066"));
        clockView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        topRow.addView(clockView);

        TextView menuHint = new TextView(this);
        menuHint.setText("   •   [MENÜ] VAULT-TEC TERMINAL");
        menuHint.setTextColor(Color.parseColor("#CC33FF66"));
        menuHint.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        menuHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        topRow.addView(menuHint);
        topHud.addView(topRow);

        focusHudTitle = new TextView(this);
        focusHudTitle.setText("WASTELAND TV LAUNCHER v6.2");
        focusHudTitle.setTextColor(Color.parseColor("#FF33FF66"));
        focusHudTitle.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        focusHudTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        focusHudTitle.setPadding(0, dp(3), 0, 0);
        topHud.addView(focusHudTitle);

        focusHudSub = new TextView(this);
        focusHudSub.setText("Bereit");
        focusHudSub.setTextColor(Color.parseColor("#DDFFE066"));
        focusHudSub.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        focusHudSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        topHud.addView(focusHudSub);

        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hudLp.gravity = Gravity.TOP | Gravity.START;
        hudLp.leftMargin = dp(24);
        hudLp.topMargin = dp(20);
        rootLayout.addView(topHud, hudLp);

        itemsLayer = new FrameLayout(this);
        itemsLayer.setClipChildren(false);
        itemsLayer.setClipToPadding(false);
        rootLayout.addView(itemsLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        editBannerView = new TextView(this);
        editBannerView.setTextColor(Color.BLACK);
        editBannerView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        editBannerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        editBannerView.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(Color.parseColor("#EEFFE066"));
        editBg.setCornerRadius(dp(6));
        editBannerView.setBackground(editBg);
        editBannerView.setVisibility(View.GONE);
        FrameLayout.LayoutParams ebLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ebLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ebLp.topMargin = dp(18);
        rootLayout.addView(editBannerView, ebLp);

        startHomeGuardService();
        reloadAll();
    }

    private void startHomeGuardService() {
        try {
            Intent svc = new Intent(this, HomeGuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LauncherPrefs.setAmazonAllowedTemporarily(this, false);
        startHomeGuardService();
        retroTvView.startEffects();
        handler.removeCallbacks(clockRunnable);
        handler.post(clockRunnable);
        handler.removeCallbacks(bgRotateRunnable);
        handler.postDelayed(bgRotateRunnable, 45000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activeHoverBob != null) {
            activeHoverBob.cancel();
            activeHoverBob = null;
        }
        retroTvView.stopEffects();
        handler.removeCallbacks(clockRunnable);
        handler.removeCallbacks(bgRotateRunnable);
    }

    private void reloadAll() {
        backgroundFiles = ImageUtils.listImageNames(this, "Hintergruende");
        String selBg = LauncherPrefs.getSelectedBackground(this);
        if (!backgroundFiles.isEmpty()) {
            if (!LauncherPrefs.BG_AUTO.equals(selBg) && backgroundFiles.contains(selBg)) {
                applyBackground(selBg, false);
            } else {
                if (currentBgIndex >= backgroundFiles.size()) currentBgIndex = 0;
                applyBackground(backgroundFiles.get(currentBgIndex), false);
            }
        }
        retroTvView.reloadAssets();
        rebuildButtons();
    }

    private void applyBackground(String fileName, boolean animate) {
        Bitmap bmp = ImageUtils.loadBitmap(this, "Hintergruende", fileName, 1280, 720, false);
        if (bmp == null) return;
        if (animate && bgImageFront.getDrawable() != null) {
            bgImageBack.setImageDrawable(bgImageFront.getDrawable());
            bgImageFront.setAlpha(0f);
            bgImageFront.setImageBitmap(bmp);
            bgImageFront.animate().alpha(1f).setDuration(900).start();
        } else {
            bgImageFront.setImageBitmap(bmp);
            bgImageFront.setAlpha(1f);
        }
    }

    private void rebuildButtons() {
        if (activeHoverBob != null) {
            activeHoverBob.cancel();
            activeHoverBob = null;
        }
        itemsLayer.removeAllViews();
        itemViews.clear();
        items = LauncherPrefs.loadItems(this);
        visibleItems = new ArrayList<>();
        for (LauncherItem item : items) {
            if (item.visible) {
                visibleItems.add(item);
            }
        }
        if (visibleItems.isEmpty() && !items.isEmpty()) {
            items.get(0).visible = true;
            visibleItems.add(items.get(0));
        }

        int mode = LauncherPrefs.getLayoutMode(this);
        if (mode == LauncherPrefs.LAYOUT_ROW) {
            freeEditMode = false;
            updateEditBanner();
            buildRowLayout();
        } else {
            buildFreeLayout();
        }

        if (!itemViews.isEmpty()) {
            itemViews.get(0).requestFocus();
        }
    }

    private void buildRowLayout() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        hsv.setFillViewport(true);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(42), dp(52), dp(42), dp(22));

        for (int i = 0; i < visibleItems.size(); i++) {
            final int vIdx = i;
            final LauncherItem item = visibleItems.get(i);
            View btn = createFigureButtonView(item, vIdx, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            row.addView(btn, lp);
            itemViews.add(btn);
        }

        hsv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams hsvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(334));
        hsvLp.gravity = Gravity.BOTTOM;
        hsvLp.bottomMargin = dp(10);
        itemsLayer.addView(hsv, hsvLp);
    }

    private void buildFreeLayout() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        for (int i = 0; i < visibleItems.size(); i++) {
            final int vIdx = i;
            final LauncherItem item = visibleItems.get(i);
            View btn = createFigureButtonView(item, vIdx, true);

            int baseW = getBaseWidthDp(item, true);
            int baseH = getBaseHeightDp(item, true);
            int pxW = Math.round(dp(baseW) * item.scale);
            int pxH = Math.round(dp(baseH) * item.scale);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pxW, pxH);
            lp.leftMargin = Math.max(0, Math.min(screenW - pxW, Math.round(screenW * item.xPercent) - pxW / 2));
            lp.topMargin = Math.max(0, Math.min(screenH - pxH, Math.round(screenH * item.yPercent) - pxH / 2));
            itemsLayer.addView(btn, lp);
            itemViews.add(btn);
        }
        updateEditBanner();
    }

    private int getBaseWidthDp(LauncherItem item, boolean freeMode) {
        if ("SCHILD".equals(item.category)) return freeMode ? 156 : 138;
        if ("AUTOMAT".equals(item.category)) return freeMode ? 124 : 112;
        return freeMode ? 118 : 106;
    }

    private int getBaseHeightDp(LauncherItem item, boolean freeMode) {
        if ("SCHILD".equals(item.category)) return freeMode ? 126 : 122;
        if ("AUTOMAT".equals(item.category)) return freeMode ? 176 : 168;
        return freeMode ? 186 : 178;
    }

    private View createFigureButtonView(final LauncherItem item, final int vIdx, final boolean freeMode) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        container.setFocusable(true);
        container.setFocusableInTouchMode(true);
        container.setClickable(true);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        container.setPadding(dp(6), dp(8), dp(6), dp(6));

        int imgW = Math.round(dp(getBaseWidthDp(item, freeMode)) * (freeMode ? item.scale : Math.min(1.18f, item.scale)));
        int imgH = Math.round(dp(getBaseHeightDp(item, freeMode)) * (freeMode ? item.scale : Math.min(1.18f, item.scale)));

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bmp = ImageUtils.loadBitmap(this, "Figuren", item.assetFileName, 440, 480, true);
        if (bmp != null) {
            iv.setImageBitmap(bmp);
        }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                freeMode ? ViewGroup.LayoutParams.MATCH_PARENT : imgW,
                freeMode ? 0 : imgH,
                freeMode ? 1f : 0f
        );
        container.addView(iv, ivLp);

        TextView badge = new TextView(this);
        String appText = (item.appLabel != null && !item.appLabel.isEmpty()) ? item.appLabel : item.displayName;
        badge.setText(appText.toUpperCase());
        badge.setTextColor(Color.parseColor("#DDFFFFFF"));
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));

        GradientDrawable badgeNormal = new GradientDrawable();
        badgeNormal.setColor(Color.parseColor("#9908140B"));
        badgeNormal.setStroke(dp(1), Color.parseColor("#7733FF66"));
        badgeNormal.setCornerRadius(dp(4));

        GradientDrawable badgeFocused = new GradientDrawable();
        badgeFocused.setColor(Color.parseColor("#EE0E2E17"));
        badgeFocused.setStroke(dp(2), Color.parseColor("#FFFFE066"));
        badgeFocused.setCornerRadius(dp(5));

        badge.setBackground(badgeNormal);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.topMargin = dp(4);
        container.addView(badge, badgeLp);

        GradientDrawable focusHalo = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#0033FF66"), Color.parseColor("#3833FF66"), Color.parseColor("#55FFE066")}
        );
        focusHalo.setCornerRadius(dp(14));
        focusHalo.setStroke(dp(2), Color.parseColor("#BB33FF66"));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.bringToFront();
                v.setBackground(focusHalo);
                badge.setBackground(badgeFocused);
                badge.setTextColor(Color.parseColor("#FFFFE066"));

                v.animate()
                        .scaleX(1.16f)
                        .scaleY(1.16f)
                        .translationY(-dp(14))
                        .setDuration(190)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();

                if (activeHoverBob != null) activeHoverBob.cancel();
                activeHoverBob = ObjectAnimator.ofFloat(iv, "translationY", 0f, -dp(5), 0f);
                activeHoverBob.setDuration(1500);
                activeHoverBob.setRepeatCount(ValueAnimator.INFINITE);
                activeHoverBob.start();

                focusHudTitle.setText(item.displayName.toUpperCase());
                focusHudSub.setText("APP: " + appText.toUpperCase()
                        + (freeMode ? "   •   [PLAY] FREI VERSCHIEBEN" : ""));
                retroTvView.showChannelPreview(item);
            } else {
                v.setBackground(null);
                badge.setBackground(badgeNormal);
                badge.setTextColor(Color.parseColor("#DDFFFFFF"));
                iv.setTranslationY(0f);
                v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(160)
                        .start();
            }
        });

        container.setOnClickListener(v -> {
            if (freeEditMode) {
                if (freeEditSelectedIdx == vIdx) {
                    freeEditSelectedIdx = -1;
                    LauncherPrefs.saveItems(MainActivity.this, items);
                    updateEditBanner();
                    Toast.makeText(MainActivity.this, item.displayName + " gespeichert!", Toast.LENGTH_SHORT).show();
                } else {
                    freeEditSelectedIdx = vIdx;
                    updateEditBanner();
                }
                return;
            }
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
                v.animate().scaleX(1.16f).scaleY(1.16f).setDuration(100).start();
                launchItemAction(item);
            }).start();
        });

        container.setOnLongClickListener(v -> {
            if (LauncherPrefs.getLayoutMode(MainActivity.this) == LauncherPrefs.LAYOUT_FREE) {
                freeEditMode = true;
                freeEditSelectedIdx = vIdx;
                updateEditBanner();
                return true;
            }
            openTerminalSettings();
            return true;
        });

        return container;
    }

    private void launchItemAction(LauncherItem item) {
        if (LauncherItem.ACTION_SETTINGS.equals(item.packageName)) {
            openTerminalSettings();
            return;
        }
        if (LauncherItem.ACTION_APPSTORE.equals(item.packageName)) {
            Intent store = LauncherItem.getLaunchIntent(this, "com.amazon.venezia");
            if (store != null) {
                store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(store);
            } else {
                Toast.makeText(this, "Amazon Appstore nicht gefunden", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (item.packageName == null || item.packageName.isEmpty()) {
            Toast.makeText(this, "Noch keine App zugewiesen – drücke MENÜ für Einstellungen", Toast.LENGTH_SHORT).show();
            openTerminalSettings();
            return;
        }

        String resolvedPkg = LauncherItem.resolveInstalledPackage(this, item.packageName);
        Intent launch = LauncherItem.getLaunchIntent(this, resolvedPkg);
        if (launch != null) {
            retroTvView.triggerBandSwitchNoise(250);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Toast.makeText(this,
                    item.appLabel + " nicht installiert – bitte über [MENÜ] oder App-Store laden",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openTerminalSettings() {
        freeEditMode = false;
        freeEditSelectedIdx = -1;
        updateEditBanner();

        TerminalSettingsDialog dlg = new TerminalSettingsDialog(this, new TerminalSettingsDialog.Callback() {
            @Override
            public void onSettingsChanged() {
                reloadAll();
            }

            @Override
            public void onStartFreeEditMode() {
                reloadAll();
                freeEditMode = true;
                freeEditSelectedIdx = 0;
                if (!itemViews.isEmpty()) {
                    itemViews.get(0).requestFocus();
                }
                updateEditBanner();
            }

            @Override
            public void onOpenAmazonHomeExplicitly() {
                LauncherPrefs.setAmazonAllowedTemporarily(MainActivity.this, true);
                try {
                    Intent i = getPackageManager().getLaunchIntentForPackage("com.amazon.tv.launcher");
                    if (i == null) {
                        i = new Intent(Intent.ACTION_MAIN);
                        i.setPackage("com.amazon.tv.launcher");
                        i.addCategory(Intent.CATEGORY_HOME);
                    }
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Original Amazon Startseite freigegeben – Home-Taste drücken", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onReloadImages() {
                reloadAll();
            }
        });
        dlg.show();
    }

    private void updateEditBanner() {
        if (!freeEditMode) {
            editBannerView.setVisibility(View.GONE);
            return;
        }
        editBannerView.setVisibility(View.VISIBLE);
        if (freeEditSelectedIdx >= 0 && freeEditSelectedIdx < visibleItems.size()) {
            LauncherItem cur = visibleItems.get(freeEditSelectedIdx);
            editBannerView.setText("VERSCHIEBEN: " + cur.displayName.toUpperCase()
                    + "  |  [PFEILE] Bewegen  •  [FF/RW] Größe (" + Math.round(cur.scale * 100)
                    + "%)  •  [OK] Speichern  •  [ZURÜCK] Fertig");
        } else {
            editBannerView.setText("BEARBEITUNGSMODUS (VARIANTE B): Figur wählen & [OK] zum Verschieben  •  [ZURÜCK] Beenden");
        }
    }

    private void applyFreePositionMove(float dx, float dy, float dScale) {
        if (freeEditSelectedIdx < 0 || freeEditSelectedIdx >= visibleItems.size()) return;
        LauncherItem item = visibleItems.get(freeEditSelectedIdx);
        item.xPercent = Math.max(0.06f, Math.min(0.94f, item.xPercent + dx));
        item.yPercent = Math.max(0.16f, Math.min(0.88f, item.yPercent + dy));
        item.scale = Math.max(0.55f, Math.min(1.75f, item.scale + dScale));

        View v = itemViews.get(freeEditSelectedIdx);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int pxW = Math.round(dp(getBaseWidthDp(item, true)) * item.scale);
        int pxH = Math.round(dp(getBaseHeightDp(item, true)) * item.scale);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.width = pxW;
        lp.height = pxH;
        lp.leftMargin = Math.max(0, Math.min(screenW - pxW, Math.round(screenW * item.xPercent) - pxW / 2));
        lp.topMargin = Math.max(0, Math.min(screenH - pxH, Math.round(screenH * item.yPercent) - pxH / 2));
        v.setLayoutParams(lp);
        updateEditBanner();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();

            if (code == KeyEvent.KEYCODE_MENU) {
                openTerminalSettings();
                return true;
            }

            if (code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    && LauncherPrefs.getLayoutMode(this) == LauncherPrefs.LAYOUT_FREE) {
                freeEditMode = !freeEditMode;
                if (!freeEditMode) {
                    freeEditSelectedIdx = -1;
                    LauncherPrefs.saveItems(this, items);
                } else {
                    freeEditSelectedIdx = findFocusedItemIndex();
                }
                updateEditBanner();
                return true;
            }

            if (freeEditMode && freeEditSelectedIdx >= 0) {
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        applyFreePositionMove(-0.025f, 0f, 0f);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        applyFreePositionMove(0.025f, 0f, 0f);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        applyFreePositionMove(0f, -0.03f, 0f);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        applyFreePositionMove(0f, 0.03f, 0f);
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    case KeyEvent.KEYCODE_CHANNEL_UP:
                        applyFreePositionMove(0f, 0f, 0.08f);
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_REWIND:
                    case KeyEvent.KEYCODE_CHANNEL_DOWN:
                        applyFreePositionMove(0f, 0f, -0.08f);
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                        LauncherPrefs.saveItems(this, items);
                        freeEditSelectedIdx = -1;
                        freeEditMode = false;
                        updateEditBanner();
                        Toast.makeText(this, "Positionen gespeichert!", Toast.LENGTH_SHORT).show();
                        return true;
                }
            } else if (freeEditMode && code == KeyEvent.KEYCODE_BACK) {
                LauncherPrefs.saveItems(this, items);
                freeEditMode = false;
                updateEditBanner();
                return true;
            }

            if (code == KeyEvent.KEYCODE_BACK) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private int findFocusedItemIndex() {
        for (int i = 0; i < itemViews.size(); i++) {
            if (itemViews.get(i).hasFocus()) return i;
        }
        return itemViews.isEmpty() ? -1 : 0;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
