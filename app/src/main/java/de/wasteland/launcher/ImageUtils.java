package de.wasteland.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Environment;
import android.util.LruCache;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ImageUtils {
    private static final String BASE_SD_DIR = "FalloutLauncher";
    private static final String BASE_ASSET_DIR = "FalloutLauncher";

    private static final LruCache<String, Bitmap> cache;

    static {
        int maxMemKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSizeKb = Math.max(8192, maxMemKb / 5);
        cache = new LruCache<String, Bitmap>(cacheSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    public static void clearCache() {
        cache.evictAll();
    }

    public static File getSdSubDir(String subFolder) {
        return new File(new File(Environment.getExternalStorageDirectory(), BASE_SD_DIR), subFolder);
    }

    public static List<String> listImageNames(Context ctx, String subFolder) {
        Set<String> names = new LinkedHashSet<>();

        File dir = getSdSubDir(subFolder);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && isImageFile(f.getName())) {
                        names.add(f.getName());
                    }
                }
            }
        }

        try {
            String[] assetFiles = ctx.getAssets().list(BASE_ASSET_DIR + "/" + subFolder);
            if (assetFiles != null) {
                for (String af : assetFiles) {
                    if (isImageFile(af)) {
                        names.add(af);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    public static boolean isImageFile(String name) {
        String l = name.toLowerCase();
        return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".webp");
    }

    public static Bitmap loadBitmap(Context ctx, String subFolder, String fileName,
                                    int reqW, int reqH, boolean removeBlackBorder) {
        String key = subFolder + "/" + fileName + "_" + reqW + "x" + reqH + "_" + removeBlackBorder;
        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap bmp = null;
        File sdFile = new File(getSdSubDir(subFolder), fileName);
        if (sdFile.exists() && sdFile.isFile()) {
            bmp = decodeSampledFile(sdFile.getAbsolutePath(), reqW, reqH);
        }

        if (bmp == null) {
            String assetPath = BASE_ASSET_DIR + "/" + subFolder + "/" + fileName;
            bmp = decodeSampledAsset(ctx, assetPath, reqW, reqH);
        }

        if (bmp != null && removeBlackBorder) {
            bmp = removeBlackBorderIfOpaque(bmp);
        }

        if (bmp != null) {
            cache.put(key, bmp);
        }
        return bmp;
    }

    private static Bitmap decodeSampledFile(String path, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeSampledAsset(Context ctx, String assetPath, int reqW, int reqH) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is1 = ctx.getAssets().open(assetPath)) {
                BitmapFactory.decodeStream(is1, null, opts);
            }
            opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream is2 = ctx.getAssets().open(assetPath)) {
                return BitmapFactory.decodeStream(is2, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
        int h = opts.outHeight;
        int w = opts.outWidth;
        int inSampleSize = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2;
            int halfW = w / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private static Bitmap removeBlackBorderIfOpaque(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w < 4 || h < 4) return src;

        int[] corners = new int[]{
                src.getPixel(0, 0),
                src.getPixel(w - 1, 0),
                src.getPixel(0, h - 1),
                src.getPixel(w - 1, h - 1)
        };
        int darkOpaqueCorners = 0;
        for (int c : corners) {
            if (Color.alpha(c) < 200) {
                return src;
            }
            int maxC = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c)));
            if (maxC <= 26) darkOpaqueCorners++;
        }
        if (darkOpaqueCorners < 3) {
            return src;
        }

        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        int[] pixels = new int[w * h];
        out.getPixels(pixels, 0, w, 0, 0, w, h);

        boolean[] visited = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int thresh = 26;

        for (int x = 0; x < w; x++) {
            tryEnqueue(x, 0, w, pixels, visited, queue, thresh);
            tryEnqueue(x, h - 1, w, pixels, visited, queue, thresh);
        }
        for (int y = 0; y < h; y++) {
            tryEnqueue(0, y, w, pixels, visited, queue, thresh);
            tryEnqueue(w - 1, y, w, pixels, visited, queue, thresh);
        }

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            pixels[idx] = 0x00000000;
            int x = idx % w;
            int y = idx / w;
            if (x > 0) tryEnqueue(x - 1, y, w, pixels, visited, queue, thresh);
            if (x < w - 1) tryEnqueue(x + 1, y, w, pixels, visited, queue, thresh);
            if (y > 0) tryEnqueue(x, y - 1, w, pixels, visited, queue, thresh);
            if (y < h - 1) tryEnqueue(x, y + 1, w, pixels, visited, queue, thresh);
        }

        // Soften 1px dark edge halo around newly cut out black pixels
        int[] smoothed = pixels.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (pixels[idx] == 0) continue;
                boolean touchesTrans = (pixels[idx - 1] == 0 || pixels[idx + 1] == 0
                        || pixels[idx - w] == 0 || pixels[idx + w] == 0);
                if (touchesTrans) {
                    int c = pixels[idx];
                    int r = Color.red(c);
                    int g = Color.green(c);
                    int b = Color.blue(c);
                    int maxC = Math.max(r, Math.max(g, b));
                    if (maxC < 56) {
                        int newAlpha = Math.max(0, Math.min(255, (maxC - 14) * 6));
                        smoothed[idx] = Color.argb(newAlpha,
                                Math.min(255, r + 18),
                                Math.min(255, g + 18),
                                Math.min(255, b + 18));
                    }
                }
            }
        }
        out.setPixels(smoothed, 0, w, 0, 0, w, h);
        return out;
    }

    private static void tryEnqueue(int x, int y, int w, int[] pixels, boolean[] visited,
                                   ArrayDeque<Integer> queue, int thresh) {
        int idx = y * w + x;
        if (visited[idx]) return;
        int c = pixels[idx];
        int maxC = Math.max(Color.red(c), Math.max(Color.green(c), Color.blue(c)));
        if (maxC <= thresh) {
            visited[idx] = true;
            queue.add(idx);
        }
    }

    public static String prettifyBackgroundName(String fileName) {
        String s = fileName;
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.startsWith("bg_")) s = s.substring(3);
        s = s.replace('_', ' ');
        s = s.replace("oedland", "Ödland").replace("Oedland", "Ödland");
        StringBuilder sb = new StringBuilder();
        for (String part : s.split(" ")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
