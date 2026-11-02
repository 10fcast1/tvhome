package com.example.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Landing screen with three platforms, as designed in the uploaded "TV OS" page:
 *  - Google TV : in-app page with tabs, hero spotlight and app row
 *  - Apple TV  : in-app page with tabs, hero spotlight and wide tiles
 *  - Fire TV   : opens the real stock Fire TV home screen
 * All layouts are measured on a 960-unit-wide grid, so they scale to any TV resolution.
 */
public class MainActivity extends Activity {

    private interface FocusCb {
        void on(boolean has);
    }

    private static final int PAGE_SELECTOR = -1;
    private static final int GOOGLE = 0;
    private static final int APPLE = 1;
    private static final int FIRE = 2;

    private static final String[] NAMES = Prefs.MODE_NAMES;
    private static final String[] CAPTIONS = {"Google TV OS", "Apple TV OS", "Stock Fire TV home"};

    // {background A, background B, glow 1, glow 2}
    private static final int[][] THEMES = {
            {0xFF070C18, 0xFF03060D, 0x482563EB, 0x30F59E0B},
            {0xFF0D0E12, 0xFF050608, 0x3A94A3B8, 0x30757585},
            {0xFF180A04, 0xFF0A0301, 0x48EA580C, 0x30DC2626},
    };
    private static final int[] ACCENT = {0xFF4285F4, 0xFFE5E7EB, 0xFFFF9900};
    private static final int[][] EMBLEM = {
            {0xFF4285F4, 0xFF1A56DB}, {0xFF48484A, 0xFF1C1C1E}, {0xFFFF9900, 0xFFE8590C}};

    private static final String[] GOOGLE_TABS = {"For You", "Live", "Movies", "Shows", "Apps", "Library"};
    private static final String[] GOOGLE_CODES = {"", "L", "M", "S", "*apps", "B"};
    private static final String[] APPLE_TABS = {"Home", "Apple TV+", "Store", "Library"};
    private static final String[] APPLE_CODES = {"", "*atv", "*store", "B"};

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    private Prefs prefs;
    private float unit;
    private FrameLayout root;
    private FrameLayout content;
    private AmbientView ambient;

    // boot overlay
    private FrameLayout overlay;
    private TextView overlayTitle;
    private EmblemView overlayEmblem;
    private Runnable pendingBoot;

    // selector
    private final View[] cards = new View[3];
    private final TextView[] pills = new TextView[3];
    private TextView footerSel;

    // clock (rebuilt with each page)
    private TextView clockTime;
    private TextView clockDate;

    // app page
    private int page = PAGE_SELECTOR;
    private List<AppRepo.App> apps = new ArrayList<>();
    private String filter = "";
    private LinearLayout row;
    private TextView[] tabViews = new TextView[0];
    private String[] tabCodes = new String[0];
    private AppRepo.App heroApp;
    private FrameLayout heroBox;
    private FrameLayout heroArtBox;
    private ImageView heroArt;
    private TextView heroMono;
    private TextView heroBadge;
    private TextView heroTitle;
    private TextView heroDesc;
    private TextView heroPrimary;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        unit = Math.max(dm.widthPixels, dm.heightPixels) / 960f;

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070C);

        ambient = new AmbientView(this);
        ambient.setThemeNow(THEMES[prefs.mode()]);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        overlay = buildOverlay();
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        showSelector();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (page == GOOGLE || page == APPLE) {
            reloadApps(); // picks up apps you just installed or edited
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home was pressed: always come back to the platform chooser.
        cancelBoot();
        showSelector();
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            cancelBoot();
            restoreSelectorFocus();
        } else if (page != PAGE_SELECTOR) {
            showSelector();
        }
        // On the chooser there is nowhere to go "back" to.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && page != PAGE_SELECTOR) {
            showSelector();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ selector screen

    private void showSelector() {
        page = PAGE_SELECTOR;
        content.removeAllViews();
        content.addView(buildSelector(), new FrameLayout.LayoutParams(-1, -1));
        int idx = prefs.mode();
        ambient.setTheme(THEMES[idx]);
        restoreSelectorFocus();
    }

    private void restoreSelectorFocus() {
        if (page != PAGE_SELECTOR) return;
        final View card = cards[prefs.mode()];
        if (card != null) {
            card.post(new Runnable() {
                @Override
                public void run() {
                    card.requestFocus();
                }
            });
        }
    }

    private View buildSelector() {
        LinearLayout col = vbox();
        col.setPadding(u(40), u(20), u(40), u(16));

        // ----- header bar
        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(u(18), u(10), u(18), u(10));
        setBg(bar, 18, 0x0AFFFFFF, 0x18FFFFFF);

        TextView logo = tv("TV", 14, Color.WHITE);
        logo.setTypeface(BLACK);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6366F1, 0xFFA855F7});
        lg.setCornerRadius(u(10));
        logo.setBackground(lg);
        bar.addView(logo, new LinearLayout.LayoutParams(u(38), u(38)));

        LinearLayout names = vbox();
        names.setPadding(u(12), 0, 0, 0);
        SpannableString brand = new SpannableString("AURA OS");
        brand.setSpan(new ForegroundColorSpan(0xFF818CF8), 4, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView brandTv = tv("", 17, 0xE6FFFFFF);
        brandTv.setText(brand);
        brandTv.setTypeface(MEDIUM);
        TextView sub = tv("SYSTEM BOOT  •  SELECT PLATFORM", 9.5f, 0x66FFFFFF);
        sub.setTypeface(Typeface.MONOSPACE);
        sub.setLetterSpacing(0.1f);
        names.addView(brandTv);
        names.addView(sub);
        bar.addView(names);

        bar.addView(spacer(), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout clock = vbox();
        clock.setGravity(Gravity.END);
        clockTime = tv("", 20, Color.WHITE);
        clockTime.setTypeface(MEDIUM);
        clockTime.setGravity(Gravity.END);
        clockDate = tv("", 11, 0x99FFFFFF);
        clockDate.setGravity(Gravity.END);
        clock.addView(clockTime);
        clock.addView(clockDate);
        bar.addView(clock);
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        // ----- centre stage
        LinearLayout center = vbox();
        center.setGravity(Gravity.CENTER);
        center.setClipChildren(false);
        col.addView(center, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout chip = hbox();
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(u(14), u(6), u(14), u(6));
        setBg(chip, 20, 0x0DFFFFFF, 0x1AFFFFFF);
        View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF818CF8);
        dot.setBackground(dg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(u(8), u(8));
        dotLp.rightMargin = u(8);
        chip.addView(dot, dotLp);
        chip.addView(tv("Interactive TV OS Environment", 12, 0xFFA5B4FC));
        center.addView(chip, new LinearLayout.LayoutParams(-2, -2));

        TextView title = tv("Choose Your Experience", 40, Color.WHITE);
        title.setTypeface(BLACK);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = u(12);
        center.addView(title, tl);

        TextView subtitle = tv("Pick a look. Same apps everywhere.", 14, 0x80FFFFFF);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = u(6);
        sl.bottomMargin = u(20);
        center.addView(subtitle, sl);

        LinearLayout cardRow = hbox();
        cardRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            cardRow.addView(buildCard(i));
        }
        center.addView(cardRow, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout hint = hbox();
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(u(18), u(8), u(18), u(8));
        setBg(hint, 18, 0x0DFFFFFF, 0x26FFFFFF);
        hint.addView(tv("Press Left / Right to choose, then OK to open", 12.5f, 0xCCFFFFFF));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = u(22);
        center.addView(hint, hl);

        // ----- footer
        LinearLayout foot = hbox();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(u(8), u(8), u(8), 0);
        View green = new View(this);
        GradientDrawable gg = new GradientDrawable();
        gg.setShape(GradientDrawable.OVAL);
        gg.setColor(0xFF34D399);
        green.setBackground(gg);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(u(8), u(8));
        gl.rightMargin = u(8);
        foot.addView(green, gl);
        foot.addView(tv("Fire OS runtime  •  " + Build.MODEL, 11.5f, 0x66FFFFFF));
        foot.addView(spacer(), new LinearLayout.LayoutParams(0, 1, 1f));
        footerSel = tv("Selected: " + NAMES[prefs.mode()], 12, 0x99FFFFFF);
        footerSel.setTypeface(Typeface.MONOSPACE);
        foot.addView(footerSel);
        col.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        updateClock();
        return col;
    }

    private View buildCard(final int idx) {
        LinearLayout card = vbox();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(u(16), u(12), u(16), u(14));

        LinearLayout top = hbox();
        top.setGravity(Gravity.END);
        final TextView pill = tv("ACTIVE", 9, ACCENT[idx]);
        pill.setTypeface(MEDIUM);
        pill.setLetterSpacing(0.08f);
        pill.setPadding(u(10), u(3), u(10), u(3));
        setBg(pill, 12, (ACCENT[idx] & 0x00FFFFFF) | 0x33000000, (ACCENT[idx] & 0x00FFFFFF) | 0x66000000);
        pill.setVisibility(View.INVISIBLE);
        top.addView(pill);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        pills[idx] = pill;

        card.addView(spacer(), new LinearLayout.LayoutParams(1, 0, 1f));

        EmblemView emblem = new EmblemView(this, EMBLEM[idx][0], EMBLEM[idx][1]);
        card.addView(emblem, new LinearLayout.LayoutParams(u(64), u(64)));

        TextView name = tv(NAMES[idx], 26, Color.WHITE);
        name.setTypeface(BLACK);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.topMargin = u(12);
        card.addView(name, nl);

        if (idx == FIRE) {
            TextView ed = tv("INSIGNIA EDITION", 9, 0xFFFDBA74);
            ed.setTypeface(MEDIUM);
            ed.setLetterSpacing(0.12f);
            card.addView(ed);
        }

        card.addView(spacer(), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView cap = tv(CAPTIONS[idx], 12, 0x80FFFFFF);
        cap.setTypeface(MEDIUM);
        card.addView(cap);

        styleFocus(card, 26, 0x0AFFFFFF, 0x26FFFFFF, 0x1AFFFFFF, 0xAAFFFFFF,
                1.08f, 8, ACCENT[idx], new FocusCb() {
                    @Override
                    public void on(boolean has) {
                        pill.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                        if (has) {
                            ambient.setTheme(THEMES[idx]);
                            if (footerSel != null) footerSel.setText("Selected: " + NAMES[idx]);
                        }
                    }
                });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchSelected(idx);
            }
        });
        cards[idx] = card;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(u(236), u(190));
        lp.leftMargin = u(14);
        lp.rightMargin = u(14);
        card.setLayoutParams(lp);
        return card;
    }

    private void launchSelected(final int idx) {
        prefs.setMode(idx);
        if (idx == FIRE) {
            boot("Opening Fire TV…", idx, new Runnable() {
                @Override
                public void run() {
                    startFireHome();
                }
            });
        } else {
            boot("Booting " + NAMES[idx] + "…", idx, new Runnable() {
                @Override
                public void run() {
                    showPage(idx);
                }
            });
        }
    }

    /** Opens Amazon's own home screen: the first Home activity that is not this app. */
    private void startFireHome() {
        try {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            PackageManager pm = getPackageManager();
            ResolveInfo best = null;
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                if (best == null || p.startsWith("com.amazon")) best = ri;
            }
            if (best == null) {
                Toast.makeText(this, "Couldn't find the Fire TV home screen.", Toast.LENGTH_LONG).show();
                restoreSelectorFocus();
                return;
            }
            Intent go = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new ComponentName(best.activityInfo.packageName, best.activityInfo.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(go);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open the Fire TV home screen.", Toast.LENGTH_LONG).show();
            restoreSelectorFocus();
        }
    }

    // ------------------------------------------------------------------ boot overlay

    private FrameLayout buildOverlay() {
        FrameLayout o = new FrameLayout(this);
        o.setBackgroundColor(0xF2000000);
        o.setClickable(true);
        o.setFocusable(true);

        LinearLayout c = vbox();
        c.setGravity(Gravity.CENTER);
        overlayEmblem = new EmblemView(this, 0xFF6366F1, 0xFFA855F7);
        c.addView(overlayEmblem, new LinearLayout.LayoutParams(u(88), u(88)));

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(u(44), u(44));
        pl.topMargin = u(26);
        c.addView(pb, pl);

        overlayTitle = tv("Booting…", 24, Color.WHITE);
        overlayTitle.setTypeface(MEDIUM);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = u(18);
        c.addView(overlayTitle, tl);

        TextView sub = tv("Press Back to cancel", 12, 0x80FFFFFF);
        sub.setTypeface(Typeface.MONOSPACE);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = u(6);
        c.addView(sub, sl);

        o.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return o;
    }

    private void boot(String title, int idx, final Runnable done) {
        cancelBoot();
        overlayTitle.setText(title);
        overlayEmblem.setColors(EMBLEM[idx][0], EMBLEM[idx][1]);
        overlay.setVisibility(View.VISIBLE);
        overlay.requestFocus();
        pendingBoot = new Runnable() {
            @Override
            public void run() {
                pendingBoot = null;
                overlay.setVisibility(View.GONE);
                done.run();
            }
        };
        handler.postDelayed(pendingBoot, 900);
    }

    private void cancelBoot() {
        if (pendingBoot != null) {
            handler.removeCallbacks(pendingBoot);
            pendingBoot = null;
        }
        overlay.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ Google TV / Apple TV pages

    private void showPage(int pf) {
        page = pf;
        filter = "";
        apps = AppRepo.favorites(this, prefs);
        content.removeAllViews();
        content.addView(buildPage(pf), new FrameLayout.LayoutParams(-1, -1));
        ambient.setTheme(THEMES[pf]);
        buildTiles();
        if (!apps.isEmpty()) updateHero(apps.get(0));
        focusTile(null);
        updateClock();
    }

    private View buildPage(final int pf) {
        final boolean apple = pf == APPLE;
        final int accent = ACCENT[pf];
        LinearLayout col = vbox();
        col.setPadding(u(36), u(18), u(36), u(10));

        // ----- top navigation
        LinearLayout nav = hbox();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(chipButton("‹  Switch OS", accent, new Runnable() {
            @Override
            public void run() {
                showSelector();
            }
        }));

        TextView brand = tv(NAMES[pf], 20, Color.WHITE);
        brand.setTypeface(BLACK);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.leftMargin = u(16);
        bl.rightMargin = u(16);
        nav.addView(brand, bl);

        tabCodes = apple ? APPLE_CODES : GOOGLE_CODES;
        String[] labels = apple ? APPLE_TABS : GOOGLE_TABS;
        LinearLayout tabs = hbox();
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(u(4), u(4), u(4), u(4));
        setBg(tabs, 24, 0x14FFFFFF, 0x22FFFFFF);
        tabViews = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int ti = i;
            TextView t = tv(labels[i], 13, Color.WHITE);
            t.setTypeface(MEDIUM);
            t.setPadding(u(16), u(7), u(16), u(7));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onTab(ti);
                }
            });
            tabViews[i] = t;
            tabs.addView(t);
        }
        refreshTabs();
        nav.addView(tabs);

        nav.addView(spacer(), new LinearLayout.LayoutParams(0, 1, 1f));

        nav.addView(chipButton("Settings", accent, new Runnable() {
            @Override
            public void run() {
                openSettings();
            }
        }));

        clockTime = tv("", 17, Color.WHITE);
        clockTime.setTypeface(MEDIUM);
        clockDate = null;
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = u(16);
        nav.addView(clockTime, cl);
        col.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        // ----- hero spotlight
        heroBox = new FrameLayout(this);
        heroBox.setClipToOutline(true);
        heroBox.setClipChildren(false);

        heroArtBox = new FrameLayout(this);
        heroArt = new ImageView(this);
        heroArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroArt.setPadding(u(24), u(24), u(24), u(24));
        heroArtBox.addView(heroArt, new FrameLayout.LayoutParams(-1, -1));
        heroMono = tv("", 64, Color.WHITE);
        heroMono.setTypeface(BLACK);
        heroMono.setGravity(Gravity.CENTER);
        heroArtBox.addView(heroMono, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(u(140), u(140),
                Gravity.END | Gravity.CENTER_VERTICAL);
        alp.rightMargin = u(46);
        heroBox.addView(heroArtBox, alp);

        LinearLayout text = vbox();
        text.setGravity(Gravity.BOTTOM);
        text.setPadding(u(28), u(16), u(240), u(20));
        heroBadge = tv("", 10, Color.WHITE);
        heroBadge.setTypeface(MEDIUM);
        heroBadge.setLetterSpacing(0.1f);
        heroBadge.setPadding(u(10), u(3), u(10), u(3));
        text.addView(heroBadge, new LinearLayout.LayoutParams(-2, -2));
        heroTitle = tv("", 36, Color.WHITE);
        heroTitle.setTypeface(BLACK);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(-2, -2);
        ttl.topMargin = u(6);
        text.addView(heroTitle, ttl);
        heroDesc = tv("", 13.5f, 0xCCFFFFFF);
        heroDesc.setMaxLines(2);
        heroDesc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = u(4);
        text.addView(heroDesc, dl);

        LinearLayout btns = hbox();
        heroPrimary = tv("Open", 13.5f, 0xFF111111);
        heroPrimary.setTypeface(MEDIUM);
        heroPrimary.setPadding(u(24), u(10), u(24), u(10));
        styleFocus(heroPrimary, 16, 0xFFFFFFFF, 0xFFE2E8F0, 0x00000000, accent, 1.06f, 2, accent, null);
        heroPrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (heroApp != null) launchApp(heroApp);
            }
        });
        btns.addView(heroPrimary);
        TextView allApps = tv("All apps", 13.5f, Color.WHITE);
        allApps.setTypeface(MEDIUM);
        allApps.setPadding(u(22), u(10), u(22), u(10));
        styleFocus(allApps, 16, 0x24FFFFFF, 0x4DFFFFFF, 0x33FFFFFF, 0xE6FFFFFF, 1.06f, 2, accent, null);
        allApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AllAppsActivity.class));
            }
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
        al.leftMargin = u(12);
        btns.addView(allApps, al);
        LinearLayout.LayoutParams bll = new LinearLayout.LayoutParams(-2, -2);
        bll.topMargin = u(12);
        text.addView(btns, bll);
        heroBox.addView(text, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, u(190));
        hlp.topMargin = u(14);
        col.addView(heroBox, hlp);

        // ----- apps row header
        LinearLayout sec = hbox();
        sec.setGravity(Gravity.CENTER_VERTICAL);
        TextView st = tv(apple ? "Apps & Channels" : "Your Apps & Channels", 14, 0xCCFFFFFF);
        st.setTypeface(MEDIUM);
        sec.addView(st);
        sec.addView(spacer(), new LinearLayout.LayoutParams(0, 1, 1f));
        sec.addView(chipButton("Edit apps", accent, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, PickerActivity.class));
            }
        }));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = u(12);
        col.addView(sec, sl);

        // ----- apps row
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(u(8), u(14), u(8), u(14));
        row = hbox();
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));

        return col;
    }

    private void onTab(int i) {
        String code = tabCodes[i];
        if (code.startsWith("*")) {
            if (code.equals("*apps")) {
                startActivity(new Intent(this, AllAppsActivity.class));
            } else if (code.equals("*store")) {
                openAppstoreHome();
            } else if (code.equals("*atv")) {
                AppRepo.App a = findByLabel("Apple TV");
                if (a != null) launchApp(a);
                else openStore("com.apple.atve.amazon.appletv");
            }
            return;
        }
        filter = code;
        refreshTabs();
        buildTiles();
        List<AppRepo.App> list = filtered();
        if (!list.isEmpty()) updateHero(list.get(0));
    }

    private void refreshTabs() {
        boolean apple = page == APPLE;
        int accent = ACCENT[apple ? APPLE : GOOGLE];
        for (int i = 0; i < tabViews.length; i++) {
            boolean sel = !tabCodes[i].startsWith("*") && tabCodes[i].equals(filter);
            int fill = sel ? ((accent & 0x00FFFFFF) | 0x44000000) : 0x00000000;
            int stroke = sel ? ((accent & 0x00FFFFFF) | 0x88000000) : 0x00000000;
            styleFocus(tabViews[i], 18, fill, 0x40FFFFFF, stroke, 0xE6FFFFFF, 1.06f, 2, accent, null);
        }
    }

    private AppRepo.App findByLabel(String label) {
        for (AppRepo.App a : apps) {
            if (a.label.equalsIgnoreCase(label)) return a;
        }
        return null;
    }

    private List<AppRepo.App> filtered() {
        if (filter.isEmpty()) return apps;
        List<AppRepo.App> out = new ArrayList<>();
        for (AppRepo.App a : apps) {
            if (a.tags.contains(filter)) out.add(a);
        }
        return out;
    }

    /** Re-reads installed apps (after returning from the Appstore or Edit apps). */
    private void reloadApps() {
        if (row == null) return;
        String keep = heroApp != null ? heroApp.pkg : null;
        apps = AppRepo.favorites(this, prefs);
        buildTiles();
        List<AppRepo.App> list = filtered();
        if (!list.isEmpty()) updateHero(list.get(0));
        focusTile(keep);
    }

    private void buildTiles() {
        row.removeAllViews();
        boolean apple = page == APPLE;
        List<AppRepo.App> list = filtered();
        if (list.isEmpty()) {
            row.addView(tv("Nothing here yet. Use Edit apps to add some.", 14, 0x99FFFFFF));
            return;
        }
        for (AppRepo.App a : list) {
            row.addView(apple ? appleTile(a) : googleTile(a));
        }
    }

    private void focusTile(final String pkg) {
        if (row == null) return;
        row.post(new Runnable() {
            @Override
            public void run() {
                View v = null;
                if (pkg != null) v = (View) row.findViewWithTag(pkg);
                if (v == null) {
                    // first focusable tile
                    for (int i = 0; i < row.getChildCount(); i++) {
                        View cell = row.getChildAt(i);
                        if (cell instanceof ViewGroupLike) {
                            v = ((ViewGroupLike) cell).tile;
                            break;
                        }
                    }
                }
                if (v != null) v.requestFocus();
            }
        });
    }

    /** A tile and its label, so we can find the focusable part. */
    private static class ViewGroupLike extends LinearLayout {
        View tile;

        ViewGroupLike(android.content.Context c) {
            super(c);
        }
    }

    private View googleTile(final AppRepo.App a) {
        ViewGroupLike cell = new ViewGroupLike(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);

        final TextView label = tv(a.label, 12.5f, 0xFFB8BDC9);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);

        FrameLayout tile = makeTile(a, 80, 80, 26, false, 1.12f, new FocusCb() {
            @Override
            public void on(boolean has) {
                label.setTextColor(has ? Color.WHITE : 0xFFB8BDC9);
                if (has) updateHero(a);
            }
        });
        cell.tile = tile;
        cell.addView(tile, new LinearLayout.LayoutParams(u(80), u(80)));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(u(100), -2);
        ll.topMargin = u(10);
        cell.addView(label, ll);

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(u(100), -2);
        cl.rightMargin = u(14);
        cell.setLayoutParams(cl);
        return cell;
    }

    private View appleTile(final AppRepo.App a) {
        ViewGroupLike cell = new ViewGroupLike(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setClipChildren(false);

        final TextView label = tv(a.label, 12.5f, 0xCCFFFFFF);
        label.setTypeface(MEDIUM);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);

        FrameLayout tile = makeTile(a, 140, 86, 18, true, 1.12f, new FocusCb() {
            @Override
            public void on(boolean has) {
                label.setTextColor(has ? Color.WHITE : 0xCCFFFFFF);
                if (has) updateHero(a);
            }
        });
        cell.tile = tile;
        cell.addView(tile, new LinearLayout.LayoutParams(u(140), u(86)));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(u(140), -2);
        ll.topMargin = u(10);
        cell.addView(label, ll);

        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(u(140), -2);
        cl.rightMargin = u(16);
        cell.setLayoutParams(cl);
        return cell;
    }

    /** A focusable tile: brand gradient, banner or icon (or a letter), and a GET badge if missing. */
    private FrameLayout makeTile(final AppRepo.App a, int wU, int hU, final float radiusU,
                                 boolean preferBanner, final float focusScale, final FocusCb extra) {
        final FrameLayout f = new FrameLayout(this);
        f.setTag(a.pkg);
        paintTile(f, a, radiusU, false);
        f.setClipToOutline(true);

        boolean banner = preferBanner && a.banner != null && a.installed;
        if (a.installed && (banner || a.icon != null)) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner ? a.banner : copy(a.icon));
            iv.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            int pad = banner ? 0 : u(Math.min(wU, hU) * 0.2f);
            iv.setPadding(pad, pad, pad, pad);
            f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView mono = tv(initial(a.label), Math.min(wU, hU) * 0.46f, Color.WHITE);
            mono.setTypeface(BLACK);
            mono.setGravity(Gravity.CENTER);
            f.addView(mono, new FrameLayout.LayoutParams(-1, -1));
        }
        if (!a.installed) {
            f.setAlpha(0.62f);
            TextView get = tv("GET", 8.5f, Color.WHITE);
            get.setTypeface(MEDIUM);
            get.setPadding(u(6), u(2), u(6), u(2));
            setBg(get, 8, 0xCC000000, 0);
            FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            gp.topMargin = u(6);
            gp.rightMargin = u(6);
            f.addView(get, gp);
        }

        f.setFocusable(true);
        f.setClickable(true);
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                paintTile(f, a, radiusU, has);
                v.animate().scaleX(has ? focusScale : 1f).scaleY(has ? focusScale : 1f)
                        .translationY(has ? -u(4) : 0).setDuration(180).start();
                v.setElevation(has ? u(16) : 0);
                if (has) setGlow(v, a.color);
                if (extra != null) extra.on(has);
            }
        });
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchApp(a);
            }
        });
        return f;
    }

    private void paintTile(View v, AppRepo.App a, float radiusU, boolean focused) {
        int top = AppRepo.mix(a.color, Color.WHITE, focused ? 0.28f : 0.08f);
        int bottom = AppRepo.mix(a.color, Color.BLACK, 0.45f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(u(radiusU));
        g.setStroke(focused ? u(2.5f) : Math.max(1, u(1)), focused ? 0xF2FFFFFF : 0x26FFFFFF);
        v.setBackground(g);
    }

    private void updateHero(AppRepo.App a) {
        if (heroBox == null || a == null) return;
        heroApp = a;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                AppRepo.mix(a.color, Color.BLACK, 0.88f),
                AppRepo.mix(a.color, Color.BLACK, 0.62f),
                AppRepo.mix(a.color, Color.BLACK, 0.20f)});
        g.setCornerRadius(u(22));
        g.setStroke(Math.max(1, u(1)), 0x26FFFFFF);
        heroBox.setBackground(g);

        heroTitle.setText(a.label);
        heroDesc.setText(a.tagline.isEmpty() ? "Open " + a.label + " on your TV." : a.tagline);
        if (a.installed) {
            heroBadge.setText("INSTALLED");
            setBg(heroBadge, 12, 0x33FFFFFF, 0x55FFFFFF);
            heroPrimary.setText("Open");
        } else {
            heroBadge.setText("NOT INSTALLED");
            setBg(heroBadge, 12, 0x55FBBF24, 0x88FBBF24);
            heroPrimary.setText("Get it on Appstore");
        }

        GradientDrawable ab = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                AppRepo.mix(a.color, Color.WHITE, 0.18f), AppRepo.mix(a.color, Color.BLACK, 0.35f)});
        ab.setCornerRadius(u(34));
        ab.setStroke(Math.max(1, u(1)), 0x33FFFFFF);
        heroArtBox.setBackground(ab);
        if (a.installed && a.icon != null) {
            heroArt.setImageDrawable(copy(a.icon));
            heroMono.setText("");
        } else {
            heroArt.setImageDrawable(null);
            heroMono.setText(initial(a.label));
        }
    }

    // ------------------------------------------------------------------ launching

    private void launchApp(AppRepo.App a) {
        if (!a.installed || a.intent == null) {
            openStore(a.storePkg);
            return;
        }
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?p=" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Open the Amazon Appstore and search for it.", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppstoreHome() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.amazon.venezia");
            if (i == null) throw new IllegalStateException("no store");
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open the Appstore. Try All apps.", Toast.LENGTH_LONG).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open Settings. Try All apps.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ clock

    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(this);
        Date now = new Date();
        if (clockTime != null) clockTime.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm a", now));
        if (clockDate != null) clockDate.setText(DateFormat.format("EEEE, MMM d", now));
    }

    // ------------------------------------------------------------------ building blocks

    private TextView chipButton(String label, int glow, final Runnable run) {
        TextView t = tv(label, 13, Color.WHITE);
        t.setTypeface(MEDIUM);
        t.setPadding(u(16), u(7), u(16), u(7));
        styleFocus(t, 20, 0x14FFFFFF, 0x4DFFFFFF, 0x26FFFFFF, 0xE6FFFFFF, 1.06f, 2, glow, null);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        return t;
    }

    /** Gives a view a rounded background that brightens, lifts and grows when focused. */
    private void styleFocus(View v, final float radiusU, final int fill, final int fillF,
                            final int stroke, final int strokeF, final float scale,
                            final float liftU, final int glow, final FocusCb cb) {
        setBg(v, radiusU, fill, stroke);
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean has) {
                setBg(x, radiusU, has ? fillF : fill, has ? strokeF : stroke);
                x.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                        .translationY(has ? -u(liftU) : 0).setDuration(200).start();
                x.setElevation(has ? u(14) : 0);
                if (has) setGlow(x, glow);
                if (cb != null) cb.on(has);
            }
        });
    }

    private void setGlow(View v, int color) {
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineSpotShadowColor(color);
            v.setOutlineAmbientShadowColor(color);
        }
    }

    private void setBg(View v, float radiusU, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(u(radiusU));
        if ((stroke >>> 24) != 0) g.setStroke(Math.max(1, u(1.5f)), stroke);
        v.setBackground(g);
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    private View spacer() {
        return new View(this);
    }

    private TextView tv(String s, float sizeU, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeU * unit);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private String initial(String label) {
        return label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
    }

    private Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : d;
    }

    /** Design units to pixels (the layout is designed on a 960-unit-wide screen). */
    private int u(float v) {
        return Math.round(v * unit);
    }
}
