package de.wasteland.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RetroTvView extends FrameLayout {

    private static final float SCREEN_LEFT = 0.052f;
    private static final float SCREEN_TOP = 0.105f;
    private static final float SCREEN_RIGHT = 0.760f;
    private static final float SCREEN_BOTTOM = 0.708f;

    private final FrameLayout screenContainer;
    private final ImageView standbyImageView;
    private final LinearLayout appPreviewOverlay;
    private final ImageView previewIconView;
    private final TextView previewTitleView;
    private final TextView previewSubView;
    private final StaticNoiseView noiseOverlay;
    private final ImageView tvBezelView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> standbyFiles = new ArrayList<>();
    private int currentStandbyIndex = 0;
    private boolean running = false;

    private final Runnable cycleRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            String mode = LauncherPrefs.getTvStandbyMode(getContext());
            if (LauncherPrefs.TV_CYCLE.equals(mode) && standbyFiles.size() > 1) {
                currentStandbyIndex = (currentStandbyIndex + 1) % standbyFiles.size();
                if (appPreviewOverlay.getVisibility() != VISIBLE) {
                    triggerBandSwitchNoise(320);
                    showCurrentStandbyImage();
                }
            }
            handler.postDelayed(this, 8500);
        }
    };

    private final Runnable hidePreviewRunnable = new Runnable() {
        @Override
        public void run() {
            if (appPreviewOverlay.getVisibility() == VISIBLE) {
                triggerBandSwitchNoise(240);
                appPreviewOverlay.setVisibility(GONE);
                showCurrentStandbyImage();
            }
        }
    };

    public RetroTvView(Context ctx) {
        super(ctx);
        setClipChildren(false);
        setClipToPadding(false);

        screenContainer = new FrameLayout(ctx);
        screenContainer.setBackgroundColor(Color.parseColor("#0A120C"));
        addView(screenContainer);

        standbyImageView = new ImageView(ctx);
        standbyImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screenContainer.addView(standbyImageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        appPreviewOverlay = new LinearLayout(ctx);
        appPreviewOverlay.setOrientation(LinearLayout.VERTICAL);
        appPreviewOverlay.setGravity(Gravity.CENTER);
        appPreviewOverlay.setBackgroundColor(Color.parseColor("#D8081A0D"));
        appPreviewOverlay.setPadding(16, 12, 16, 12);
        appPreviewOverlay.setVisibility(GONE);

        previewIconView = new ImageView(ctx);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconLp.bottomMargin = dp(6);
        appPreviewOverlay.addView(previewIconView, iconLp);

        previewTitleView = new TextView(ctx);
        previewTitleView.setTextColor(Color.parseColor("#33FF66"));
        previewTitleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        previewTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        previewTitleView.setGravity(Gravity.CENTER);
        appPreviewOverlay.addView(previewTitleView);

        previewSubView = new TextView(ctx);
        previewSubView.setTextColor(Color.parseColor("#FFE066"));
        previewSubView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        previewSubView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        previewSubView.setGravity(Gravity.CENTER);
        appPreviewOverlay.addView(previewSubView);

        screenContainer.addView(appPreviewOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        noiseOverlay = new StaticNoiseView(ctx);
        screenContainer.addView(noiseOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        tvBezelView = new ImageView(ctx);
        tvBezelView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(tvBezelView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        reloadAssets();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;

        int sl = Math.round(w * SCREEN_LEFT);
        int st = Math.round(h * SCREEN_TOP);
        int sr = Math.round(w * SCREEN_RIGHT);
        int sb = Math.round(h * SCREEN_BOTTOM);
        screenContainer.measure(
                MeasureSpec.makeMeasureSpec(sr - sl, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(sb - st, MeasureSpec.EXACTLY)
        );
        screenContainer.layout(sl, st, sr, sb);
    }

    public void reloadAssets() {
        Context ctx = getContext();
        Bitmap tvBmp = ImageUtils.loadBitmap(ctx, "TV", "retro_fernseher.png", 660, 580, true);
        if (tvBmp == null) {
            tvBmp = ImageUtils.loadBitmap(ctx, "TV", "TV_2.jpg", 660, 580, true);
        }
        if (tvBmp != null) {
            tvBezelView.setImageBitmap(tvBmp);
        }

        standbyFiles.clear();
        List<String> allTv = ImageUtils.listImageNames(ctx, "TV");
        for (String name : allTv) {
            String l = name.toLowerCase();
            if (!l.contains("retro_fernseher") && !l.equals("tv.jpg") && !l.equals("tv_2.jpg") && !l.equals("tv.png")) {
                standbyFiles.add(name);
            }
        }
        if (currentStandbyIndex >= standbyFiles.size()) {
            currentStandbyIndex = 0;
        }
        showCurrentStandbyImage();
        noiseOverlay.updateSettings();
    }

    private void showCurrentStandbyImage() {
        if (standbyFiles.isEmpty()) return;
        String mode = LauncherPrefs.getTvStandbyMode(getContext());
        String targetFile = standbyFiles.get(currentStandbyIndex);
        if (!LauncherPrefs.TV_CYCLE.equals(mode) && standbyFiles.contains(mode)) {
            targetFile = mode;
        }
        Bitmap bmp = ImageUtils.loadBitmap(getContext(), "TV", targetFile, 480, 320, false);
        if (bmp != null) {
            standbyImageView.setImageBitmap(bmp);
        }
    }

    public void showChannelPreview(LauncherItem item) {
        handler.removeCallbacks(hidePreviewRunnable);
        triggerBandSwitchNoise(260);

        previewTitleView.setText(item.appLabel != null && !item.appLabel.isEmpty()
                ? item.appLabel.toUpperCase() : item.displayName.toUpperCase());
        previewSubView.setText("KANAL: " + item.displayName.toUpperCase());

        Drawable appIcon = null;
        if (item.packageName != null && !item.packageName.isEmpty()
                && !LauncherItem.ACTION_SETTINGS.equals(item.packageName)) {
            String resolved = LauncherItem.resolveInstalledPackage(getContext(), item.packageName);
            if (LauncherItem.ACTION_APPSTORE.equals(resolved)) {
                resolved = "com.amazon.venezia";
            }
            try {
                PackageManager pm = getContext().getPackageManager();
                appIcon = pm.getApplicationIcon(resolved);
            } catch (Exception ignored) {
            }
        }
        if (appIcon != null) {
            previewIconView.setImageDrawable(appIcon);
            previewIconView.setVisibility(VISIBLE);
        } else {
            previewIconView.setVisibility(GONE);
        }

        appPreviewOverlay.setVisibility(VISIBLE);
        handler.postDelayed(hidePreviewRunnable, 4000);
    }

    public void triggerBandSwitchNoise(long durationMs) {
        if (LauncherPrefs.isTvSwitchNoiseEnabled(getContext())) {
            noiseOverlay.triggerBurst(durationMs);
        }
    }

    public void startEffects() {
        running = true;
        noiseOverlay.updateSettings();
        noiseOverlay.startAnim();
        handler.removeCallbacks(cycleRunnable);
        handler.postDelayed(cycleRunnable, 8500);
    }

    public void stopEffects() {
        running = false;
        noiseOverlay.stopAnim();
        handler.removeCallbacks(cycleRunnable);
        handler.removeCallbacks(hidePreviewRunnable);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static class StaticNoiseView extends View {
        private final Paint pixelPaint = new Paint();
        private final Paint scanlinePaint = new Paint();
        private final Random random = new Random();
        private final Handler animHandler = new Handler(Looper.getMainLooper());
        private boolean animating = false;
        private long burstEndTime = 0;
        private boolean grainEnabled = true;
        private int tickCount = 0;

        private final Runnable frameRunnable = new Runnable() {
            @Override
            public void run() {
                if (!animating) return;
                tickCount++;
                invalidate();
                long now = System.currentTimeMillis();
                long delay = (now < burstEndTime) ? 45 : 140;
                animHandler.postDelayed(this, delay);
            }
        };

        StaticNoiseView(Context ctx) {
            super(ctx);
            scanlinePaint.setColor(Color.argb(42, 0, 0, 0));
            scanlinePaint.setStrokeWidth(1.5f);
            updateSettings();
        }

        void updateSettings() {
            grainEnabled = LauncherPrefs.isTvGrainEnabled(getContext());
            invalidate();
        }

        void triggerBurst(long durationMs) {
            burstEndTime = System.currentTimeMillis() + durationMs;
            invalidate();
        }

        void startAnim() {
            if (animating) return;
            animating = true;
            animHandler.post(frameRunnable);
        }

        void stopAnim() {
            animating = false;
            animHandler.removeCallbacks(frameRunnable);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            long now = System.currentTimeMillis();
            boolean inBurst = now < burstEndTime;

            if (inBurst) {
                canvas.drawColor(Color.argb(195, 22, 24, 22));
                int block = Math.max(4, w / 55);
                for (int y = 0; y < h; y += block) {
                    for (int x = 0; x < w; x += block) {
                        int lum = random.nextInt(256);
                        pixelPaint.setColor(Color.argb(215, lum, lum, lum));
                        canvas.drawRect(x, y, x + block, y + block, pixelPaint);
                    }
                }
                int barY = random.nextInt(h);
                pixelPaint.setColor(Color.argb(180, 255, 255, 255));
                canvas.drawRect(0, barY, w, barY + Math.max(6, h / 18), pixelPaint);
            } else if (grainEnabled) {
                int speckles = (tickCount % 7 == 0) ? 140 : 48;
                int alpha = (tickCount % 7 == 0) ? 95 : 46;
                int sz = Math.max(2, w / 110);
                for (int i = 0; i < speckles; i++) {
                    int rx = random.nextInt(w);
                    int ry = random.nextInt(h);
                    int lum = random.nextBoolean() ? 245 : 30;
                    pixelPaint.setColor(Color.argb(alpha, lum, lum, lum));
                    canvas.drawRect(rx, ry, rx + sz, ry + sz, pixelPaint);
                }
                if (tickCount % 13 == 0) {
                    int gy = random.nextInt(h);
                    pixelPaint.setColor(Color.argb(55, 235, 255, 240));
                    canvas.drawRect(0, gy, w, gy + 2, pixelPaint);
                }
            }

            if (grainEnabled || inBurst) {
                for (int y = 0; y < h; y += 5) {
                    canvas.drawLine(0, y, w, y, scanlinePaint);
                }
            }
        }
    }
}
