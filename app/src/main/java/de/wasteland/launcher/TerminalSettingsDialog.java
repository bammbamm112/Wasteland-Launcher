package de.wasteland.launcher;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class TerminalSettingsDialog extends Dialog {

    public interface Callback {
        void onSettingsChanged();
        void onStartFreeEditMode();
        void onOpenAmazonHomeExplicitly();
        void onReloadImages();
    }

    private static final int COL_BG = Color.parseColor("#EE061409");
    private static final int COL_PANEL = Color.parseColor("#FF0B2210");
    private static final int COL_GREEN = Color.parseColor("#FF33FF66");
    private static final int COL_GREEN_DIM = Color.parseColor("#FF1E8F3E");
    private static final int COL_AMBER = Color.parseColor("#FFFFE066");
    private static final int COL_FOCUS_BG = Color.parseColor("#FF184A24");

    private final Callback callback;
    private final LinearLayout contentContainer;
    private final ScrollView scrollView;
    private final TextView breadcrumbView;
    private int currentSubPage = 0;
    private LauncherItem editingItem = null;

    public TerminalSettingsDialog(Context ctx, Callback callback) {
        super(ctx);
        this.callback = callback;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(20), dp(28), dp(18));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(COL_BG);
        rootBg.setStroke(dp(2), COL_GREEN);
        rootBg.setCornerRadius(dp(8));
        root.setBackground(rootBg);

        TextView header = new TextView(ctx);
        header.setText("ROBCO INDUSTRIES UNIFIED OPERATING SYSTEM  //  VAULT-TEC TERMINAL v6.2");
        header.setTextColor(COL_GREEN);
        header.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(header);

        breadcrumbView = new TextView(ctx);
        breadcrumbView.setTextColor(COL_AMBER);
        breadcrumbView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        breadcrumbView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        breadcrumbView.setPadding(0, dp(4), 0, dp(10));
        root.addView(breadcrumbView);

        View divider = new View(ctx);
        divider.setBackgroundColor(COL_GREEN);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        divLp.bottomMargin = dp(10);
        root.addView(divider, divLp);

        scrollView = new ScrollView(ctx);
        scrollView.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scrollView, scrollLp);

        contentContainer = new LinearLayout(ctx);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider2 = new View(ctx);
        divider2.setBackgroundColor(COL_GREEN_DIM);
        LinearLayout.LayoutParams div2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        div2Lp.topMargin = dp(10);
        div2Lp.bottomMargin = dp(6);
        root.addView(divider2, div2Lp);

        TextView footerHintView = new TextView(ctx);
        footerHintView.setText("[HOCH/RUNTER] Wählen   •   [OK] Bestätigen   •   [ZURÜCK] Ebene zurück / Schließen");
        footerHintView.setTextColor(COL_GREEN_DIM);
        footerHintView.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        footerHintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        root.addView(footerHintView);

        setContentView(root);
        Window win = getWindow();
        if (win != null) {
            win.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.82f);
            lp.height = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.85f);
            win.setAttributes(lp);
        }

        renderMainMenu();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (editingItem != null) {
                editingItem = null;
                renderButtonsMenu();
                return true;
            }
            if (currentSubPage != 0) {
                renderMainMenu();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void renderMainMenu() {
        currentSubPage = 0;
        editingItem = null;
        breadcrumbView.setText("> HAUPTMENÜ  //  WASTELAND TV LAUNCHER");
        contentContainer.removeAllViews();

        int mode = LauncherPrefs.getLayoutMode(getContext());
        String modeStr = (mode == LauncherPrefs.LAYOUT_ROW)
                ? "VARIANTE A (Klare Reihe)" : "VARIANTE B (Frei platzierbar)";

        addTerminalButton("[01] LAYOUT-MODUS & POSITIONIERUNG",
                "Aktiv: " + modeStr, this::renderLayoutMenu);

        addTerminalButton("[02] STREAMING-BUTTONS, FIGUREN & APPS",
                "Alle 12 Figuren/Automaten/Schilder ein-/ausblenden & Apps zuweisen", this::renderButtonsMenu);

        String curBg = LauncherPrefs.getSelectedBackground(getContext());
        String bgLabel = LauncherPrefs.BG_AUTO.equals(curBg)
                ? "Automatischer Wechsel (alle 8 Hintergründe)" : ImageUtils.prettifyBackgroundName(curBg);
        addTerminalButton("[03] HINTERGRUNDBILDER",
                "Aktiv: " + bgLabel, this::renderBackgroundsMenu);

        addTerminalButton("[04] RETRO-TV STANDBY & RAUSCHEN",
                "Testbilder-Wechsel, Bandwechsel-Rauschen & Grieseln", this::renderRetroTvMenu);

        boolean guardOn = LauncherPrefs.isHomeGuardEnabled(getContext());
        boolean usageOk = HomeGuardService.hasUsageStatsPermission(getContext());
        addTerminalButton("[05] HOME-TASTEN-SCHUTZ (ANTI-AMAZON)",
                "Schutz: " + (guardOn ? "EIN" : "AUS") + "  |  Berechtigung: " + (usageOk ? "AKTIV" : "FEHLT (Skript 1 ausführen)"),
                this::renderHomeGuardMenu);

        addTerminalButton("[06] AMAZON APPSTORE, ORIGINAL-STARTSEITE & SYSTEM",
                "Neue Apps laden, gezielt Amazon Startseite oder Fire TV System öffnen", this::renderSystemMenu);

        addTerminalButton("[07] ALLE INSTALLIERTEN APPS (SCHNELLSTART)",
                "Startet jede beliebige App auf deinem Fire TV Stick direkt", this::renderAllAppsDrawer);

        addTerminalButton("[ X ] TERMINAL SCHLIESSEN",
                "Zurück zum Wasteland Startbildschirm", this::dismiss);

        focusFirst();
    }

    private void renderLayoutMenu() {
        currentSubPage = 1;
        breadcrumbView.setText("> HAUPTMENÜ > [01] LAYOUT-MODUS");
        contentContainer.removeAllViews();

        int mode = LauncherPrefs.getLayoutMode(getContext());

        addTerminalButton(
                (mode == LauncherPrefs.LAYOUT_ROW ? "[AKTIV] " : "[     ] ") + "VARIANTE A: REIHEN-ANSICHT",
                "Klare, aufgeräumte Reihe der Figuren, Automaten & Schilder",
                () -> {
                    LauncherPrefs.setLayoutMode(getContext(), LauncherPrefs.LAYOUT_ROW);
                    callback.onSettingsChanged();
                    renderLayoutMenu();
                });

        addTerminalButton(
                (mode == LauncherPrefs.LAYOUT_FREE ? "[AKTIV] " : "[     ] ") + "VARIANTE B: FREI PLATZIERBARE FIGUREN",
                "Jede Figur & jedes Schild frei auf dem Bildschirm verschieben & skalieren",
                () -> {
                    LauncherPrefs.setLayoutMode(getContext(), LauncherPrefs.LAYOUT_FREE);
                    callback.onSettingsChanged();
                    renderLayoutMenu();
                });

        addTerminalButton(">> FIGUREN JETZT FREI VERSCHIEBEN & GRÖSSE ÄNDERN <<",
                "Schaltet auf Variante B und öffnet den Bearbeitungsmodus am TV",
                () -> {
                    LauncherPrefs.setLayoutMode(getContext(), LauncherPrefs.LAYOUT_FREE);
                    dismiss();
                    callback.onStartFreeEditMode();
                });

        addTerminalButton("STANDARD-MISCHUNG & POSITIONEN ZURÜCKSETZEN",
                "Stellt die 8 gemischten Start-Buttons & Standard-Positionen wieder her",
                () -> {
                    LauncherPrefs.resetItemsToDefaults(getContext());
                    callback.onSettingsChanged();
                    Toast.makeText(getContext(), "Standard-Layout wiederhergestellt", Toast.LENGTH_SHORT).show();
                    renderLayoutMenu();
                });

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderButtonsMenu() {
        currentSubPage = 2;
        editingItem = null;
        breadcrumbView.setText("> HAUPTMENÜ > [02] STREAMING-BUTTONS & FIGUREN (12 MOTIVE)");
        contentContainer.removeAllViews();

        List<LauncherItem> items = LauncherPrefs.loadItems(getContext());
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            final LauncherItem item = items.get(i);
            String state = item.visible ? "[SICHTBAR]" : "[VERSTECKT]";
            String target = (item.appLabel != null && !item.appLabel.isEmpty()) ? item.appLabel : "Nicht belegt";
            addTerminalButton(
                    state + "  " + item.displayName.toUpperCase() + "  (" + item.category + ")",
                    "Zugewiesen: " + target + "   •   [OK] Bearbeiten / App wählen",
                    () -> renderSingleButtonEditor(idx));
        }

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderSingleButtonEditor(final int itemIndex) {
        List<LauncherItem> items = LauncherPrefs.loadItems(getContext());
        if (itemIndex < 0 || itemIndex >= items.size()) {
            renderButtonsMenu();
            return;
        }
        final LauncherItem item = items.get(itemIndex);
        editingItem = item;
        breadcrumbView.setText("> BUTTONS > " + item.displayName.toUpperCase());
        contentContainer.removeAllViews();

        addTerminalButton(
                "ANZEIGE AUF STARTBILDSCHIRM: " + (item.visible ? "EIN (SICHTBAR)" : "AUS (VERSTECKT)"),
                "Drücke OK zum Ein- oder Ausblenden dieser Figur",
                () -> {
                    List<LauncherItem> list = LauncherPrefs.loadItems(getContext());
                    list.get(itemIndex).visible = !list.get(itemIndex).visible;
                    LauncherPrefs.saveItems(getContext(), list);
                    callback.onSettingsChanged();
                    renderSingleButtonEditor(itemIndex);
                });

        addTerminalButton(
                "GRÖSSE (SKALIERUNG): " + Math.round(item.scale * 100) + "%",
                "Drücke OK zum Durchschalten (80% -> 95% -> 110% -> 125% -> 145%)",
                () -> {
                    List<LauncherItem> list = LauncherPrefs.loadItems(getContext());
                    float s = list.get(itemIndex).scale;
                    if (s < 0.90f) s = 0.95f;
                    else if (s < 1.05f) s = 1.10f;
                    else if (s < 1.20f) s = 1.25f;
                    else if (s < 1.38f) s = 1.45f;
                    else s = 0.80f;
                    list.get(itemIndex).scale = s;
                    LauncherPrefs.saveItems(getContext(), list);
                    callback.onSettingsChanged();
                    renderSingleButtonEditor(itemIndex);
                });

        addSectionHeader("SONDER-FUNKTIONEN ZUWEISEN:");

        addTerminalButton("-> AMAZON APPSTORE (NEUE APPS LADEN)",
                "Öffnet den Amazon Appstore direkt über diesen Button",
                () -> assignTarget(itemIndex, LauncherItem.ACTION_APPSTORE, "App-Store"));

        addTerminalButton("-> VAULT-TEC EINSTELLUNGEN (TERMINAL)",
                "Öffnet dieses Wasteland-Einstellungsmenü",
                () -> assignTarget(itemIndex, LauncherItem.ACTION_SETTINGS, "Vault-Tec Menü"));

        List<LauncherItem.InstalledApp> entries = LauncherItem.queryAllInstalledApps(getContext());
        addSectionHeader("INSTALLIERTE APPS AUF DEINEM FIRE TV STICK (" + entries.size() + " GEFUNDEN):");

        for (LauncherItem.InstalledApp app : entries) {
            boolean selected = app.packageName.equals(item.packageName);
            addTerminalButton(
                    (selected ? "[AKTIV] " : "        ") + app.label,
                    app.packageName,
                    () -> assignTarget(itemIndex, app.packageName, app.label));
        }

        addBackRow(this::renderButtonsMenu);
        focusFirst();
    }

    private void assignTarget(int itemIndex, String pkg, String label) {
        List<LauncherItem> list = LauncherPrefs.loadItems(getContext());
        list.get(itemIndex).packageName = pkg;
        list.get(itemIndex).appLabel = label;
        list.get(itemIndex).visible = true;
        LauncherPrefs.saveItems(getContext(), list);
        callback.onSettingsChanged();
        Toast.makeText(getContext(), list.get(itemIndex).displayName + " -> " + label, Toast.LENGTH_SHORT).show();
        renderButtonsMenu();
    }

    private void renderBackgroundsMenu() {
        currentSubPage = 3;
        breadcrumbView.setText("> HAUPTMENÜ > [03] HINTERGRUNDBILDER");
        contentContainer.removeAllViews();

        String cur = LauncherPrefs.getSelectedBackground(getContext());
        addTerminalButton(
                (LauncherPrefs.BG_AUTO.equals(cur) ? "[AKTIV] " : "[     ] ") + "AUTOMATISCHER WECHSEL (ALLE BILDER)",
                "Wechselt sanft alle 45 Sekunden durch deine Ödland-Hintergründe",
                () -> {
                    LauncherPrefs.setSelectedBackground(getContext(), LauncherPrefs.BG_AUTO);
                    callback.onSettingsChanged();
                    renderBackgroundsMenu();
                });

        List<String> bgs = ImageUtils.listImageNames(getContext(), "Hintergruende");
        for (String bg : bgs) {
            boolean sel = bg.equals(cur);
            addTerminalButton(
                    (sel ? "[AKTIV] " : "[     ] ") + ImageUtils.prettifyBackgroundName(bg).toUpperCase(),
                    "Datei: " + bg,
                    () -> {
                        LauncherPrefs.setSelectedBackground(getContext(), bg);
                        callback.onSettingsChanged();
                        renderBackgroundsMenu();
                    });
        }

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderRetroTvMenu() {
        currentSubPage = 4;
        breadcrumbView.setText("> HAUPTMENÜ > [04] RETRO-TV STANDBY & RAUSCHEN");
        contentContainer.removeAllViews();

        boolean switchNoise = LauncherPrefs.isTvSwitchNoiseEnabled(getContext());
        addTerminalButton(
                "BANDWECHSEL-RAUSCHEN (WEISSER SCHNEE): " + (switchNoise ? "EIN" : "AUS"),
                "Kurzes TV-Rauschen beim Bildwechsel & Überfahren von Buttons",
                () -> {
                    LauncherPrefs.setTvSwitchNoiseEnabled(getContext(), !switchNoise);
                    callback.onSettingsChanged();
                    renderRetroTvMenu();
                });

        boolean grain = LauncherPrefs.isTvGrainEnabled(getContext());
        addTerminalButton(
                "LEICHTES GRIESELN & RÖHREN-SCANLINES: " + (grain ? "EIN" : "AUS"),
                "Feines CRT-Flackern dauerhaft über dem Fernsehbild",
                () -> {
                    LauncherPrefs.setTvGrainEnabled(getContext(), !grain);
                    callback.onSettingsChanged();
                    renderRetroTvMenu();
                });

        addSectionHeader("STANDBY-TESTBILD AUF DEM RETRO-FERNSEHER:");

        String curTv = LauncherPrefs.getTvStandbyMode(getContext());
        addTerminalButton(
                (LauncherPrefs.TV_CYCLE.equals(curTv) ? "[AKTIV] " : "[     ] ") + "ALLE 3 TESTBILDER IM WECHSEL",
                "Wechselt automatisch zwischen Please Stand By Farbe, 4 & Vault-Tec Terminal",
                () -> {
                    LauncherPrefs.setTvStandbyMode(getContext(), LauncherPrefs.TV_CYCLE);
                    callback.onSettingsChanged();
                    renderRetroTvMenu();
                });

        List<String> tvImgs = ImageUtils.listImageNames(getContext(), "TV");
        for (String name : tvImgs) {
            String l = name.toLowerCase();
            if (l.contains("retro_fernseher") || l.equals("tv.jpg") || l.equals("tv_2.jpg") || l.equals("tv.png")) continue;
            boolean sel = name.equals(curTv);
            addTerminalButton(
                    (sel ? "[AKTIV] " : "[     ] ") + ImageUtils.prettifyBackgroundName(name).toUpperCase(),
                    "Festes Testbild: " + name,
                    () -> {
                        LauncherPrefs.setTvStandbyMode(getContext(), name);
                        callback.onSettingsChanged();
                        renderRetroTvMenu();
                    });
        }

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderHomeGuardMenu() {
        currentSubPage = 5;
        breadcrumbView.setText("> HAUPTMENÜ > [05] HOME-TASTEN-SCHUTZ (ANTI-AMAZON)");
        contentContainer.removeAllViews();

        boolean guardOn = LauncherPrefs.isHomeGuardEnabled(getContext());
        boolean usageOk = HomeGuardService.hasUsageStatsPermission(getContext());
        boolean overlayOk = Settings.canDrawOverlays(getContext());
        boolean secureOk = getContext().checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;

        addTerminalButton(
                "DAUERHAFTER HOME-TASTEN-SCHUTZ: " + (guardOn ? "AKTIV (EIN)" : "DEAKTIVIERT (AUS)"),
                "Fängt Amazon-Startseite ab und holt Wasteland Launcher sofort nach vorne",
                () -> {
                    LauncherPrefs.setHomeGuardEnabled(getContext(), !guardOn);
                    LauncherPrefs.setAmazonAllowedTemporarily(getContext(), false);
                    callback.onSettingsChanged();
                    renderHomeGuardMenu();
                });

        addSectionHeader("SYSTEM-BERECHTIGUNGEN (WERDEN PER SKRIPT 1 GESETZT):");
        addInfoLine("• GET_USAGE_STATS (Home-Guard)  : " + (usageOk ? "[ OK - FREIGESCHALTET ]" : "[ FEHLT - Skript 1 ausführen ]"), usageOk);
        addInfoLine("• WRITE_SECURE_SETTINGS         : " + (secureOk ? "[ OK - FREIGESCHALTET ]" : "[ FEHLT - Skript 1 ausführen ]"), secureOk);
        addInfoLine("• SYSTEM_ALERT_WINDOW (Overlay) : " + (overlayOk ? "[ OK - FREIGESCHALTET ]" : "[ FEHLT - Skript 1 ausführen ]"), overlayOk);

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderSystemMenu() {
        currentSubPage = 6;
        breadcrumbView.setText("> HAUPTMENÜ > [06] AMAZON FIRE TV, APPSTORE & SYSTEM");
        contentContainer.removeAllViews();

        addTerminalButton("AMAZON APPSTORE ÖFFNEN (NEUE APPS HINZUFÜGEN)",
                "Startet den Amazon Appstore direkt",
                () -> {
                    dismiss();
                    Intent i = LauncherItem.getLaunchIntent(getContext(), "com.amazon.venezia");
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(i);
                    } else {
                        Toast.makeText(getContext(), "Amazon Appstore nicht gefunden", Toast.LENGTH_SHORT).show();
                    }
                });

        addTerminalButton("ORIGINAL AMAZON FIRE TV STARTSEITE ÖFFNEN (GEZIELT)",
                "Pausiert den Wasteland-Schutz bewusst, bis du wieder in den Launcher gehst",
                () -> {
                    dismiss();
                    callback.onOpenAmazonHomeExplicitly();
                });

        addTerminalButton("FIRE TV SYSTEM-EINSTELLUNGEN ÖFFNEN",
                "WLAN, Bluetooth, Töne & Bildschirm, Mein Fire TV",
                () -> {
                    dismiss();
                    try {
                        Intent i = new Intent(Settings.ACTION_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "System-Einstellungen konnten nicht geöffnet werden", Toast.LENGTH_SHORT).show();
                    }
                });

        addTerminalButton("BILDER VON SD-KARTE NEU EINLESEN",
                "Lädt neu übertragene Hintergründe, Figuren & TV-Bilder sofort neu",
                () -> {
                    ImageUtils.clearCache();
                    callback.onReloadImages();
                    Toast.makeText(getContext(), "Alle Bilder neu eingelesen!", Toast.LENGTH_SHORT).show();
                });

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void renderAllAppsDrawer() {
        currentSubPage = 7;
        breadcrumbView.setText("> HAUPTMENÜ > [07] ALLE INSTALLIERTEN APPS (SCHNELLSTART)");
        contentContainer.removeAllViews();

        List<LauncherItem.InstalledApp> apps = LauncherItem.queryAllInstalledApps(getContext());
        addSectionHeader("ALLE APPS AUF DEINEM FIRE TV STICK (" + apps.size() + "):");
        for (LauncherItem.InstalledApp app : apps) {
            addTerminalButton(app.label, app.packageName, () -> {
                Intent i = LauncherItem.getLaunchIntent(getContext(), app.packageName);
                if (i != null) {
                    dismiss();
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                } else {
                    Toast.makeText(getContext(), app.label + " konnte nicht gestartet werden", Toast.LENGTH_SHORT).show();
                }
            });
        }

        addBackRow(this::renderMainMenu);
        focusFirst();
    }

    private void addSectionHeader(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(COL_AMBER);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        contentContainer.addView(tv);
    }

    private void addInfoLine(String text, boolean ok) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(ok ? COL_GREEN : Color.parseColor("#FFFF6655"));
        tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        contentContainer.addView(tv);
    }

    private void addBackRow(Runnable onBack) {
        addTerminalButton("<< ZURÜCK", "Eine Ebene zurück im Vault-Tec Terminal", onBack);
    }

    private void addTerminalButton(String title, String subtitle, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setPadding(dp(14), dp(9), dp(14), dp(9));

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(COL_PANEL);
        normalBg.setStroke(dp(1), COL_GREEN_DIM);
        normalBg.setCornerRadius(dp(4));

        GradientDrawable focusBg = new GradientDrawable();
        focusBg.setColor(COL_FOCUS_BG);
        focusBg.setStroke(dp(2), COL_AMBER);
        focusBg.setCornerRadius(dp(4));

        row.setBackground(normalBg);

        TextView t1 = new TextView(getContext());
        t1.setText(title);
        t1.setTextColor(COL_GREEN);
        t1.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(t1);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView t2 = new TextView(getContext());
            t2.setText(subtitle);
            t2.setTextColor(COL_GREEN_DIM);
            t2.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            row.addView(t2);
        }

        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(hasFocus ? focusBg : normalBg);
            t1.setTextColor(hasFocus ? COL_AMBER : COL_GREEN);
            if (row.getChildCount() > 1) {
                ((TextView) row.getChildAt(1)).setTextColor(hasFocus ? COL_GREEN : COL_GREEN_DIM);
            }
        });

        row.setOnClickListener(v -> action.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        contentContainer.addView(row, lp);
    }

    private void focusFirst() {
        scrollView.post(() -> scrollView.scrollTo(0, 0));
        for (int i = 0; i < contentContainer.getChildCount(); i++) {
            View v = contentContainer.getChildAt(i);
            if (v.isFocusable()) {
                v.requestFocus();
                break;
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
