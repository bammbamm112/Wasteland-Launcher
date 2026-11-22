package de.wasteland.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class LauncherPrefs {
    private static final String PREF_NAME = "wasteland_launcher_v6_prefs";

    public static final int LAYOUT_ROW = 0;
    public static final int LAYOUT_FREE = 1;

    public static final String BG_AUTO = "__AUTO__";
    public static final String TV_CYCLE = "__CYCLE__";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static int getLayoutMode(Context ctx) {
        return prefs(ctx).getInt("layout_mode", LAYOUT_ROW);
    }

    public static void setLayoutMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt("layout_mode", mode).apply();
    }

    public static String getSelectedBackground(Context ctx) {
        return prefs(ctx).getString("selected_bg", BG_AUTO);
    }

    public static void setSelectedBackground(Context ctx, String bg) {
        prefs(ctx).edit().putString("selected_bg", bg).apply();
    }

    public static String getTvStandbyMode(Context ctx) {
        return prefs(ctx).getString("tv_standby_mode", TV_CYCLE);
    }

    public static void setTvStandbyMode(Context ctx, String mode) {
        prefs(ctx).edit().putString("tv_standby_mode", mode).apply();
    }

    public static boolean isTvSwitchNoiseEnabled(Context ctx) {
        return prefs(ctx).getBoolean("tv_switch_noise", true);
    }

    public static void setTvSwitchNoiseEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("tv_switch_noise", enabled).apply();
    }

    public static boolean isTvGrainEnabled(Context ctx) {
        return prefs(ctx).getBoolean("tv_grain", true);
    }

    public static void setTvGrainEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("tv_grain", enabled).apply();
    }

    public static boolean isHomeGuardEnabled(Context ctx) {
        return prefs(ctx).getBoolean("home_guard_enabled", true);
    }

    public static void setHomeGuardEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean("home_guard_enabled", enabled).apply();
    }

    public static boolean isAmazonAllowedTemporarily(Context ctx) {
        return prefs(ctx).getBoolean("allow_amazon_temp", false);
    }

    public static void setAmazonAllowedTemporarily(Context ctx, boolean allowed) {
        prefs(ctx).edit().putBoolean("allow_amazon_temp", allowed).apply();
    }

    public static List<LauncherItem> loadItems(Context ctx) {
        String raw = prefs(ctx).getString("items_json_v61", null);
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                List<LauncherItem> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(LauncherItem.fromJson(arr.getJSONObject(i)));
                }
                if (!list.isEmpty()) return list;
            } catch (Exception ignored) {
            }
        }
        List<LauncherItem> defaults = createDefaultItems();
        saveItems(ctx, defaults);
        return defaults;
    }

    public static void saveItems(Context ctx, List<LauncherItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (LauncherItem item : items) {
                arr.put(item.toJson());
            }
            prefs(ctx).edit().putString("items_json_v61", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void resetItemsToDefaults(Context ctx) {
        saveItems(ctx, createDefaultItems());
    }

    public static List<LauncherItem> createDefaultItems() {
        List<LauncherItem> list = new ArrayList<>();

        // Aktive Start-Mischung (8 sichtbare Buttons: Figuren, Automaten, Schilder, Appstore, Terminal)
        list.add(new LauncherItem(
                "powerrustung", "Powerrüstung", "powerrustung.png", "FIGUR",
                "waipu", "waipu.tv", true,
                0.10f, 0.72f, 1.05f
        ));
        list.add(new LauncherItem(
                "nuka_cola_automat", "Nuka-Cola-Automat", "nuka_cola_automat.png", "AUTOMAT",
                "netflix", "Netflix", true,
                0.23f, 0.74f, 1.00f
        ));
        list.add(new LauncherItem(
                "todeskralle", "Todeskralle", "todeskralle.png", "FIGUR",
                "youtube", "YouTube", true,
                0.36f, 0.72f, 1.08f
        ));
        list.add(new LauncherItem(
                "verkaufsautomat_sale", "Verkaufsautomat SALE", "verkaufsautomat_sale.png", "AUTOMAT",
                "disney", "Disney+", true,
                0.49f, 0.74f, 0.98f
        ));
        list.add(new LauncherItem(
                "nuka_cola_werbetafel", "Nuka-Cola-Werbetafel", "nuka_cola_werbetafel.png", "SCHILD",
                "prime", "Prime Video", true,
                0.62f, 0.70f, 1.12f
        ));
        list.add(new LauncherItem(
                "pfandhaus_automat_pawn", "Pfandhaus-Automat PAWN", "pfandhaus_automat_pawn.png", "AUTOMAT",
                LauncherItem.ACTION_APPSTORE, "App-Store", true,
                0.75f, 0.74f, 1.02f
        ));
        list.add(new LauncherItem(
                "yes_man_roboter", "Yes-Man-Roboter", "yes_man_roboter.png", "FIGUR",
                "twitch", "Twitch", true,
                0.88f, 0.75f, 0.96f
        ));
        // Mittig oben platziert -> kollidiert in Variante B nicht mit HUD (links) oder Retro-TV (rechts)!
        list.add(new LauncherItem(
                "fallout_4_logo", "Fallout-4-Logo", "fallout_4_logo.png", "SCHILD",
                LauncherItem.ACTION_SETTINGS, "Vault-Tec Menü", true,
                0.50f, 0.22f, 1.12f
        ));

        // 4 weitere frei zuschaltbare Figuren & Schilder
        list.add(new LauncherItem(
                "vault_boy_helm_figur", "Vault-Boy auf Helm", "vault_boy_helm_figur.png", "FIGUR",
                "music", "Amazon Music", false,
                0.28f, 0.46f, 0.96f
        ));
        list.add(new LauncherItem(
                "vault_boy_statue", "Vault-Boy-Statue", "vault_boy_statue.png", "FIGUR",
                "", "Frei belegbar", false,
                0.42f, 0.46f, 0.92f
        ));
        list.add(new LauncherItem(
                "oedland_charakter", "Ödland-Charakter", "oedland_charakter.png", "FIGUR",
                "", "Frei belegbar", false,
                0.56f, 0.46f, 0.95f
        ));
        list.add(new LauncherItem(
                "sunset_sarsaparilla_schild", "Sunset-Sarsaparilla-Schild", "sunset_sarsaparilla_schild.png", "SCHILD",
                "", "Frei belegbar", false,
                0.70f, 0.46f, 1.10f
        ));

        return list;
    }
}
