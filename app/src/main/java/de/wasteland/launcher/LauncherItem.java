package de.wasteland.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LauncherItem {
    public static final String ACTION_SETTINGS = "__WASTELAND_SETTINGS__";
    public static final String ACTION_APPSTORE = "__AMAZON_APPSTORE__";

    public String id;
    public String displayName;
    public String assetFileName;
    public String category; // FIGUR, AUTOMAT, SCHILD
    public String packageName;
    public String appLabel;
    public boolean visible;
    public float xPercent;
    public float yPercent;
    public float scale;

    public static class InstalledApp {
        public final String label;
        public final String packageName;

        public InstalledApp(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
    }

    public LauncherItem(String id, String displayName, String assetFileName, String category,
                        String packageName, String appLabel, boolean visible,
                        float xPercent, float yPercent, float scale) {
        this.id = id;
        this.displayName = displayName;
        this.assetFileName = assetFileName;
        this.category = category;
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.visible = visible;
        this.xPercent = xPercent;
        this.yPercent = yPercent;
        this.scale = scale;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("displayName", displayName);
        o.put("assetFileName", assetFileName);
        o.put("category", category);
        o.put("packageName", packageName);
        o.put("appLabel", appLabel);
        o.put("visible", visible);
        o.put("xPercent", xPercent);
        o.put("yPercent", yPercent);
        o.put("scale", scale);
        return o;
    }

    public static LauncherItem fromJson(JSONObject o) throws JSONException {
        return new LauncherItem(
                o.getString("id"),
                o.getString("displayName"),
                o.getString("assetFileName"),
                o.optString("category", "FIGUR"),
                o.optString("packageName", ""),
                o.optString("appLabel", ""),
                o.optBoolean("visible", true),
                (float) o.optDouble("xPercent", 0.5),
                (float) o.optDouble("yPercent", 0.72),
                (float) o.optDouble("scale", 1.0)
        );
    }

    /**
     * Collects ALL launchable apps on Fire TV: both LEANBACK_LAUNCHER (TV apps!)
     * and standard LAUNCHER apps, deduplicated by packageName and sorted alphabetically.
     */
    public static List<InstalledApp> queryAllInstalledApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, String> found = new LinkedHashMap<>();
        String selfPkg = ctx.getPackageName();

        String[] categories = new String[]{
                Intent.CATEGORY_LEANBACK_LAUNCHER,
                Intent.CATEGORY_LAUNCHER
        };
        for (String cat : categories) {
            try {
                Intent q = new Intent(Intent.ACTION_MAIN, null);
                q.addCategory(cat);
                List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
                if (list != null) {
                    for (ResolveInfo ri : list) {
                        if (ri.activityInfo == null) continue;
                        String pkg = ri.activityInfo.packageName;
                        if (pkg == null || pkg.equals(selfPkg)) continue;
                        if ("com.amazon.tv.launcher".equals(pkg) || "com.amazon.firehomestarter".equals(pkg)) continue;
                        if (!found.containsKey(pkg)) {
                            CharSequence cs = ri.loadLabel(pm);
                            String lbl = (cs != null && cs.length() > 0) ? cs.toString().trim() : pkg;
                            found.put(pkg, lbl);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<InstalledApp> result = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            result.add(new InstalledApp(e.getValue(), e.getKey()));
        }
        Collections.sort(result, Comparator.comparing(a -> a.label.toLowerCase()));
        return result;
    }

    public static String resolveInstalledPackage(Context ctx, String preferredOrAlias) {
        if (preferredOrAlias == null || preferredOrAlias.isEmpty()) return "";
        if (ACTION_SETTINGS.equals(preferredOrAlias) || ACTION_APPSTORE.equals(preferredOrAlias)) {
            return preferredOrAlias;
        }
        PackageManager pm = ctx.getPackageManager();
        if (isLaunchable(pm, preferredOrAlias)) {
            return preferredOrAlias;
        }
        String lower = preferredOrAlias.toLowerCase();
        String[] candidates = null;
        if (lower.contains("waipu")) {
            candidates = new String[]{"de.exaring.waipu.firetv.live", "de.exaring.waipu", "de.exaring.waipu.tv"};
        } else if (lower.contains("youtube")) {
            candidates = new String[]{"com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv"};
        } else if (lower.contains("netflix")) {
            candidates = new String[]{"com.netflix.ninja", "com.netflix.mediaclient"};
        } else if (lower.contains("disney")) {
            candidates = new String[]{"com.disney.disneyplus"};
        } else if (lower.contains("prime") || lower.contains("avod")) {
            candidates = new String[]{"com.amazon.avod", "com.amazon.firebat", "com.amazon.amazonvideo.livingroom"};
        } else if (lower.contains("twitch")) {
            candidates = new String[]{"tv.twitch.android.app", "tv.twitch.android.viewer"};
        } else if (lower.contains("music")) {
            candidates = new String[]{"com.amazon.mp3", "com.amazon.bueller.music"};
        } else if (lower.contains("appstore") || lower.contains("venezia")) {
            candidates = new String[]{"com.amazon.venezia", "com.aurora.store"};
        }
        if (candidates != null) {
            for (String pkg : candidates) {
                if (isLaunchable(pm, pkg)) return pkg;
            }
        }
        for (InstalledApp app : queryAllInstalledApps(ctx)) {
            if (app.packageName.toLowerCase().contains(lower) || app.label.toLowerCase().contains(lower)) {
                return app.packageName;
            }
        }
        return preferredOrAlias;
    }

    public static boolean isLaunchable(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i != null) return true;
        return pm.getLaunchIntentForPackage(pkg) != null;
    }

    public static Intent getLaunchIntent(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        return i;
    }
}
