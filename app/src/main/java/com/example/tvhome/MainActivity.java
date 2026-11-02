package com.example.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Home screen with three switchable looks:
 *  - Android TV: classic row of wide cards
 *  - Google TV : hero area that takes the colour of the focused app, round icons below
 *  - Apple TV  : big colourful tiles that lift when focused
 * Press MENU on the remote to cycle looks.
 */
public class MainActivity extends Activity {

    private interface Focus {
        void on(boolean has);
    }

    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final int DARK = 0xFF0A0C12;

    private FrameLayout root;
    private TextView clockTime;
    private TextView clockDate;
    private Prefs prefs;
    private GradientDrawable dynamicBg;
    private int accent = 0xFF3A4A8A;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        root = new FrameLayout(this);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        render(); // Home pressed while already on Home: jump back to the first app
    }

    @Override
    public void onBackPressed() {
        // A launcher has nowhere to go "back" to.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            prefs.setMode((prefs.mode() + 1) % 3);
            render();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ render

    private void render() {
        int mode = prefs.mode();
        root.removeAllViews();
        dynamicBg = null;

        View bg = new View(this);
        bg.setBackground(background(mode));
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = vbox();
        col.setPadding(dp(56), dp(24), dp(56), dp(30));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        col.addView(topBar(mode), new LinearLayout.LayoutParams(-1, -2));

        List<AppRepo.App> apps = AppRepo.favorites(this, prefs);
        View first = null;
        if (apps.isEmpty()) {
            TextView empty = text("No apps yet. Choose \"Edit apps\" above to add some.", 22, Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            col.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else if (mode == Prefs.MODE_ANDROID_TV) {
            first = buildAndroidTv(col, apps);
        } else if (mode == Prefs.MODE_GOOGLE_TV) {
            first = buildGoogleTv(col, apps);
        } else {
            first = buildAppleTv(col, apps);
        }
        if (first != null) first.requestFocus();
        updateClock();
    }

    private Drawable background(int mode) {
        if (mode == Prefs.MODE_ANDROID_TV) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xFF181B22, 0xFF0D0F14});
            return g;
        }
        if (mode == Prefs.MODE_GOOGLE_TV) {
            dynamicBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{AppRepo.mix(accent, Color.BLACK, 0.55f), DARK});
            return dynamicBg;
        }
        // Apple TV look: deep gradient with two soft glows
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3A2A78, 0xFF14214F, 0xFF0A1330});
        GradientDrawable glow1 = new GradientDrawable();
        glow1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow1.setGradientRadius(dp(560));
        glow1.setGradientCenter(0.15f, 0.05f);
        glow1.setColors(new int[]{0x66FF6AD5, 0x00FF6AD5});
        GradientDrawable glow2 = new GradientDrawable();
        glow2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow2.setGradientRadius(dp(620));
        glow2.setGradientCenter(0.9f, 1.0f);
        glow2.setColors(new int[]{0x5538B6FF, 0x0038B6FF});
        return new LayerDrawable(new Drawable[]{base, glow1, glow2});
    }

    /** Smoothly shifts the Google TV background toward the focused app's colour. */
    private void animateBg(int toAccent) {
        if (dynamicBg == null) return;
        final GradientDrawable target = dynamicBg;
        ValueAnimator va = ValueAnimator.ofObject(new ArgbEvaluator(), accent, toAccent);
        va.setDuration(380);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                int c = (Integer) a.getAnimatedValue();
                target.setColors(new int[]{AppRepo.mix(c, Color.BLACK, 0.55f), DARK});
            }
        });
        va.start();
        accent = toAccent;
    }

    // ------------------------------------------------------------------ top bar

    private View topBar(int currentMode) {
        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // segmented look switcher
        LinearLayout seg = hbox();
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable segBg = new GradientDrawable();
        segBg.setColor(0x26FFFFFF);
        segBg.setCornerRadius(dp(40));
        seg.setBackground(segBg);
        for (int i = 0; i < Prefs.MODE_NAMES.length; i++) {
            final int m = i;
            seg.addView(segment(Prefs.MODE_NAMES[i], i == currentMode, new Runnable() {
                @Override
                public void run() {
                    prefs.setMode(m);
                    render();
                }
            }));
        }
        bar.addView(seg);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        bar.addView(pill("All apps", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, AllAppsActivity.class));
            }
        }), pillParams());
        bar.addView(pill("Edit", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, PickerActivity.class));
            }
        }), pillParams());
        bar.addView(pill("Settings", new Runnable() {
            @Override
            public void run() {
                openSettings();
            }
        }), pillParams());

        LinearLayout clock = vbox();
        clock.setGravity(Gravity.END);
        clock.setPadding(dp(14), 0, 0, 0);
        clockTime = text("", 28, Color.WHITE);
        clockTime.setTypeface(LIGHT);
        clockTime.setGravity(Gravity.END);
        clockDate = text("", 13, 0xFFB0B7C8);
        clockDate.setGravity(Gravity.END);
        clock.addView(clockTime);
        clock.addView(clockDate);
        bar.addView(clock);
        return bar;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(10);
        return lp;
    }

    private void updateClock() {
        if (clockTime == null) return;
        boolean h24 = DateFormat.is24HourFormat(this);
        Date now = new Date();
        clockTime.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm a", now));
        clockDate.setText(DateFormat.format("EEEE, MMM d", now));
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 12) return "Good morning";
        if (h < 18) return "Good afternoon";
        return "Good evening";
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open Settings. Try All apps.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ Android TV look

    private View buildAndroidTv(LinearLayout col, List<AppRepo.App> apps) {
        TextView title = text("Apps", 28, Color.WHITE);
        title.setTypeface(MEDIUM);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(46);
        col.addView(title, tl);

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 0.35f));

        HorizontalScrollView sv = scroller();
        LinearLayout row = hbox();
        View first = null;
        for (final AppRepo.App a : apps) {
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            final TextView name = text(a.label, 17, 0xFFB8BDC9);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);

            FrameLayout card = makeCard(a, dp(280), dp(158), dp(14), true, 1.10f, true, new Focus() {
                @Override
                public void on(boolean has) {
                    name.setTextColor(has ? Color.WHITE : 0xFFB8BDC9);
                }
            });
            cell.addView(card, new LinearLayout.LayoutParams(dp(280), dp(158)));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(280), -2);
            nl.topMargin = dp(14);
            cell.addView(name, nl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = dp(22);
            row.addView(cell, cl);
            if (first == null) first = card;
        }
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        View bottom = new View(this);
        col.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1f));
        return first;
    }

    // ------------------------------------------------------------------ Google TV look

    private class Hero {
        FrameLayout icon;
        ImageView img;
        TextView title;
        TextView sub;

        void show(AppRepo.App a) {
            img.setImageDrawable(copy(a.icon));
            paintCard(icon, a.color, dp(32), false, false);
            title.setText(a.label);
            sub.setText("Press OK to open");
            animateBg(a.color);
        }
    }

    private View buildGoogleTv(LinearLayout col, List<AppRepo.App> apps) {
        final Hero hero = new Hero();
        LinearLayout heroRow = hbox();
        heroRow.setGravity(Gravity.BOTTOM);

        hero.icon = new FrameLayout(this);
        hero.icon.setClipToOutline(true);
        hero.img = new ImageView(this);
        hero.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        hero.img.setPadding(dp(30), dp(30), dp(30), dp(30));
        hero.icon.addView(hero.img, new FrameLayout.LayoutParams(-1, -1));
        heroRow.addView(hero.icon, new LinearLayout.LayoutParams(dp(150), dp(150)));

        LinearLayout texts = vbox();
        texts.setGravity(Gravity.BOTTOM);
        texts.setPadding(dp(30), 0, 0, dp(4));
        TextView greet = text(greeting(), 18, 0xFFC4CADB);
        hero.title = text("", 54, Color.WHITE);
        hero.title.setTypeface(LIGHT);
        hero.sub = text("", 18, 0xFFC4CADB);
        texts.addView(greet);
        texts.addView(hero.title);
        texts.addView(hero.sub);
        heroRow.addView(texts, new LinearLayout.LayoutParams(-2, -2));
        col.addView(heroRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView label = text("Your apps", 20, Color.WHITE);
        label.setTypeface(MEDIUM);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = dp(18);
        col.addView(label, ll);

        HorizontalScrollView sv = scroller();
        LinearLayout row = hbox();
        View first = null;
        hero.show(apps.get(0));
        for (final AppRepo.App a : apps) {
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            final TextView name = text(a.label, 14, 0xFFA9B0C2);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);

            FrameLayout circle = makeCard(a, dp(96), dp(96), dp(48), false, 1.14f, true, new Focus() {
                @Override
                public void on(boolean has) {
                    name.setTextColor(has ? Color.WHITE : 0xFFA9B0C2);
                    if (has) hero.show(a);
                }
            });
            cell.addView(circle, new LinearLayout.LayoutParams(dp(96), dp(96)));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(110), -2);
            nl.topMargin = dp(10);
            cell.addView(name, nl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = dp(22);
            row.addView(cell, cl);
            if (first == null) first = circle;
        }
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        return first;
    }

    // ------------------------------------------------------------------ Apple TV look

    private View buildAppleTv(LinearLayout col, List<AppRepo.App> apps) {
        HorizontalScrollView sv = scroller();
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER);
        View first = null;
        for (final AppRepo.App a : apps) {
            LinearLayout cell = vbox();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            final TextView name = text(a.label, 22, Color.WHITE);
            name.setTypeface(MEDIUM);
            name.setAlpha(0f);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);

            FrameLayout tile = makeCard(a, dp(190), dp(190), dp(44), false, 1.20f, false, new Focus() {
                @Override
                public void on(boolean has) {
                    name.animate().alpha(has ? 1f : 0f).setDuration(160).start();
                }
            });
            cell.addView(tile, new LinearLayout.LayoutParams(dp(190), dp(190)));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(190), -2);
            nl.topMargin = dp(34);
            cell.addView(name, nl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = dp(34);
            cl.leftMargin = dp(4);
            row.addView(cell, cl);
            if (first == null) first = tile;
        }
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return first;
    }

    // ------------------------------------------------------------------ building blocks

    /** A focusable tile showing the app's banner or icon on a coloured gradient. */
    private FrameLayout makeCard(final AppRepo.App a, int w, int h, final int radius,
                                 boolean preferBanner, final float focusScale, final boolean ring,
                                 final Focus extra) {
        final FrameLayout f = new FrameLayout(this);
        paintCard(f, a.color, radius, false, ring);
        f.setClipToOutline(true);

        boolean banner = preferBanner && a.banner != null;
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(banner ? a.banner : a.icon);
        iv.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int pad = banner ? 0 : (int) (Math.min(w, h) * 0.22f);
        iv.setPadding(pad, pad, pad, pad);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));

        f.setFocusable(true);
        f.setClickable(true);
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                paintCard(f, a.color, radius, has, ring);
                v.animate().scaleX(has ? focusScale : 1f).scaleY(has ? focusScale : 1f).setDuration(150).start();
                v.setElevation(has ? dp(22) : 0);
                if (extra != null) extra.on(has);
            }
        });
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launch(a);
            }
        });
        return f;
    }

    private void paintCard(View v, int color, int radius, boolean focused, boolean ring) {
        int top = AppRepo.mix(color, Color.WHITE, focused ? 0.28f : 0.10f);
        int bottom = AppRepo.mix(color, Color.BLACK, 0.42f);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        g.setCornerRadius(radius);
        if (focused && ring) g.setStroke(dp(3), Color.WHITE);
        v.setBackground(g);
    }

    private HorizontalScrollView scroller() {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setFillViewport(true);
        sv.setPadding(dp(34), dp(34), dp(34), dp(34));
        return sv;
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

    private TextView segment(String label, final boolean selected, final Runnable onClick) {
        final TextView t = text(label, 16, Color.WHITE);
        t.setTypeface(MEDIUM);
        t.setPadding(dp(18), dp(9), dp(18), dp(9));
        t.setFocusable(true);
        t.setClickable(true);
        styleSegment(t, selected, false);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                styleSegment(t, selected, has);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        return t;
    }

    private void styleSegment(TextView t, boolean selected, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(36));
        if (selected) {
            g.setColor(Color.WHITE);
            t.setTextColor(0xFF111111);
            if (focused) g.setStroke(dp(3), 0xFF6EA8FF);
        } else {
            g.setColor(focused ? 0x66FFFFFF : 0x00000000);
            t.setTextColor(Color.WHITE);
        }
        t.setBackground(g);
    }

    private TextView pill(String label, final Runnable onClick) {
        final TextView t = text(label, 16, Color.WHITE);
        t.setTypeface(MEDIUM);
        t.setPadding(dp(18), dp(9), dp(18), dp(9));
        t.setFocusable(true);
        t.setClickable(true);
        stylePill(t, false);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                stylePill(t, has);
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClick.run();
            }
        });
        return t;
    }

    private void stylePill(TextView t, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(36));
        g.setColor(focused ? Color.WHITE : 0x26FFFFFF);
        t.setBackground(g);
        t.setTextColor(focused ? 0xFF111111 : Color.WHITE);
    }

    private void launch(AppRepo.App a) {
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable().mutate() : d;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
