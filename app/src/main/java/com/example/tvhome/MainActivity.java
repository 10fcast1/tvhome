package com.example.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.List;

/**
 * Home screen with three switchable looks:
 *  - Android TV: classic row of banner cards
 *  - Google TV : hero area on top, round app icons below
 *  - Apple TV  : large floating tiles that grow when focused
 * Press MENU on the remote to cycle looks.
 */
public class MainActivity extends Activity {

    private FrameLayout root;
    private TextView clock;
    private Prefs prefs;
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
        // Home pressed while already on Home: go back to the first app tile.
        render();
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

    // ------------------------------------------------------------------ layout

    private void render() {
        int mode = prefs.mode();
        root.removeAllViews();
        root.setBackground(background(mode));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(28), dp(56), dp(36));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        col.addView(topBar(mode), new LinearLayout.LayoutParams(-1, -2));

        List<AppRepo.App> apps = AppRepo.favorites(this, prefs);
        View first;
        if (apps.isEmpty()) {
            TextView empty = text("No apps yet. Choose \"Edit apps\" above to add some.", 22, Color.WHITE);
            empty.setGravity(Gravity.CENTER);
            col.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
            first = null;
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
        GradientDrawable g = new GradientDrawable();
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        if (mode == Prefs.MODE_ANDROID_TV) {
            g.setColors(new int[]{0xFF101216, 0xFF101216});
        } else if (mode == Prefs.MODE_GOOGLE_TV) {
            g.setColors(new int[]{0xFF202638, 0xFF0A0C12});
        } else {
            g.setColors(new int[]{0xFF4A2F86, 0xFF152552, 0xFF0A1330});
        }
        return g;
    }

    // ------------------------------------------------------------------ top bar

    private View topBar(int currentMode) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);

        for (int i = 0; i < Prefs.MODE_NAMES.length; i++) {
            final int m = i;
            TextView p = pill(Prefs.MODE_NAMES[i], i == currentMode, new Runnable() {
                @Override
                public void run() {
                    prefs.setMode(m);
                    render();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(10);
            bar.addView(p, lp);
        }

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView edit = pill("Edit apps", false, new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(MainActivity.this, PickerActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(24);
        bar.addView(edit, lp);

        clock = text("", 26, Color.WHITE);
        clock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(clock);
        return bar;
    }

    private void updateClock() {
        if (clock == null) return;
        boolean h24 = DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm a", new Date()));
    }

    // ------------------------------------------------------------------ Android TV look

    private View buildAndroidTv(LinearLayout col, final List<AppRepo.App> apps) {
        TextView title = text("Apps", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = dp(60);
        col.addView(title, tl);

        HorizontalScrollView sv = scroller();
        LinearLayout row = row();
        View first = null;
        for (final AppRepo.App a : apps) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout card = card(dp(300), dp(169), dp(10), 0xFF2A2E36);
            fillCard(card, a, true);
            makeFocusable(card, 1.10f, a);
            cell.addView(card, new LinearLayout.LayoutParams(dp(300), dp(169)));

            TextView name = text(a.label, 18, 0xFFCCCCCC);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(300), -2);
            nl.topMargin = dp(12);
            cell.addView(name, nl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = dp(22);
            row.addView(cell, cl);
            if (first == null) first = card;
        }
        sv.addView(row);
        spacerAbove(col, sv);
        return first;
    }

    // ------------------------------------------------------------------ Google TV look

    private View buildGoogleTv(LinearLayout col, final List<AppRepo.App> apps) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);

        final TextView heroTitle = text(apps.get(0).label, 54, Color.WHITE);
        heroTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        final TextView heroSub = text("Press OK to open", 22, 0xFFB8C0D4);
        hero.addView(heroTitle);
        hero.addView(heroSub);
        col.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView label = text("Your apps", 22, Color.WHITE);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = dp(18);
        ll.bottomMargin = dp(10);
        col.addView(label, ll);

        HorizontalScrollView sv = scroller();
        LinearLayout row = row();
        View first = null;
        for (final AppRepo.App a : apps) {
            FrameLayout circle = card(dp(104), dp(104), dp(52), 0xFF2C3346);
            fillCard(circle, a, false);
            makeFocusable(circle, 1.15f, a);
            circle.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    animateScale(v, has ? 1.15f : 1f);
                    if (has) {
                        heroTitle.setText(a.label);
                    }
                }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(104), dp(104));
            cl.rightMargin = dp(26);
            row.addView(circle, cl);
            if (first == null) first = circle;
        }
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        return first;
    }

    // ------------------------------------------------------------------ Apple TV look

    private View buildAppleTv(LinearLayout col, final List<AppRepo.App> apps) {
        HorizontalScrollView sv = scroller();
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        View first = null;
        for (final AppRepo.App a : apps) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);

            FrameLayout tile = card(dp(210), dp(210), dp(34), 0x33FFFFFF);
            fillCard(tile, a, false);
            final TextView name = text(a.label, 20, Color.WHITE);
            name.setAlpha(0f);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);

            makeFocusable(tile, 1.18f, a);
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    animateScale(v, has ? 1.18f : 1f);
                    v.setElevation(has ? dp(24) : 0);
                    name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
                }
            });
            cell.addView(tile, new LinearLayout.LayoutParams(dp(210), dp(210)));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(210), -2);
            nl.topMargin = dp(26);
            cell.addView(name, nl);

            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.rightMargin = dp(40);
            row.addView(cell, cl);
            if (first == null) first = tile;
        }
        sv.addView(row);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        return first;
    }

    // ------------------------------------------------------------------ building blocks

    private HorizontalScrollView scroller() {
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(dp(30), dp(30), dp(30), dp(30));
        sv.setFillViewport(true);
        return sv;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private void spacerAbove(LinearLayout col, View below) {
        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(below, new LinearLayout.LayoutParams(-1, -2));
    }

    private FrameLayout card(int w, int h, int radius, int color) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        f.setBackground(g);
        f.setClipToOutline(true);
        return f;
    }

    /** Puts the app's banner (wide cards) or icon (square/round tiles) inside a card. */
    private void fillCard(FrameLayout card, AppRepo.App a, boolean wide) {
        ImageView iv = new ImageView(this);
        Drawable d = (wide && a.banner != null) ? a.banner : a.icon;
        iv.setImageDrawable(d);
        boolean useBanner = wide && a.banner != null;
        iv.setScaleType(useBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        int pad = useBanner ? 0 : dp(wide ? 28 : 18);
        iv.setPadding(pad, pad, pad, pad);
        card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
    }

    private void makeFocusable(View v, final float scale, final AppRepo.App a) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                animateScale(view, has ? scale : 1f);
                view.setElevation(has ? dp(16) : 0);
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(a);
            }
        });
    }

    private void animateScale(View v, float s) {
        v.animate().scaleX(s).scaleY(s).setDuration(140).start();
    }

    private void launch(AppRepo.App a) {
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private TextView pill(String label, boolean selected, final Runnable onClick) {
        final TextView t = text(label, 20, Color.WHITE);
        t.setPadding(dp(22), dp(10), dp(22), dp(10));
        t.setFocusable(true);
        t.setClickable(true);
        final int normal = selected ? 0x55FFFFFF : 0x22FFFFFF;
        setPillBg(t, normal);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                setPillBg(t, has ? 0xFFFFFFFF : normal);
                t.setTextColor(has ? Color.BLACK : Color.WHITE);
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

    private void setPillBg(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(30));
        v.setBackground(g);
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
