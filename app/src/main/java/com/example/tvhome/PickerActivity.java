package com.example.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Choose which apps appear on Home and in what order.
 *  OK         = add / remove the highlighted app
 *  LEFT/RIGHT = move a selected app earlier / later in the Home list
 *  BACK       = done
 */
public class PickerActivity extends Activity {

    private Prefs prefs;
    private List<AppRepo.App> all;
    private final List<String> selected = new ArrayList<>();
    private LinearLayout list;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        all = AppRepo.loadAll(this);
        for (AppRepo.App a : AppRepo.favorites(this, prefs)) selected.add(a.pkg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF101216);
        col.setPadding(dp(80), dp(40), dp(80), dp(20));

        TextView title = text("Edit apps", 34, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        col.addView(title);
        TextView help = text("OK = add/remove     ◀ ▶ = move a selected app earlier/later     Back = done", 18, 0xFFAAAAAA);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = dp(8);
        hp.bottomMargin = dp(16);
        col.addView(help, hp);

        TextView reset = row("Reset to defaults (all the streaming services you have installed)", false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.resetFavorites();
                selected.clear();
                for (AppRepo.App a : AppRepo.favorites(PickerActivity.this, prefs)) selected.add(a.pkg);
                rebuild(0);
            }
        });
        col.addView(reset);

        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);
        rebuild(-1);
    }

    /** Selected apps first (in Home order), then everything else alphabetically. */
    private void rebuild(int focusIndex) {
        list.removeAllViews();
        List<AppRepo.App> ordered = new ArrayList<>();
        for (String p : selected) {
            for (AppRepo.App a : all) {
                if (a.pkg.equals(p)) ordered.add(a);
            }
        }
        for (AppRepo.App a : all) {
            if (!selected.contains(a.pkg)) ordered.add(a);
        }

        for (int i = 0; i < ordered.size(); i++) {
            final AppRepo.App a = ordered.get(i);
            final int idx = selected.indexOf(a.pkg);
            final boolean on = idx >= 0;
            final int position = i;
            TextView r = row((on ? "☑  " : "☐  ") + a.label + (on ? "     (#" + (idx + 1) + ")" : ""), on);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (on) selected.remove(a.pkg);
                    else selected.add(a.pkg);
                    save();
                    rebuild(position);
                }
            });
            r.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN || !on) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && idx > 0) {
                        selected.remove(idx);
                        selected.add(idx - 1, a.pkg);
                        save();
                        rebuild(idx - 1);
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && idx < selected.size() - 1) {
                        selected.remove(idx);
                        selected.add(idx + 1, a.pkg);
                        save();
                        rebuild(idx + 1);
                        return true;
                    }
                    return false;
                }
            });
            if (a.icon != null) {
                a.icon.setBounds(0, 0, dp(36), dp(36));
                r.setCompoundDrawables(a.icon, null, null, null);
                r.setCompoundDrawablePadding(dp(16));
            }
            list.addView(r);
            if (i == focusIndex) r.requestFocus();
        }
        if (focusIndex < 0 && list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private void save() {
        prefs.setFavorites(selected);
    }

    private TextView row(String s, boolean highlight) {
        final TextView t = text(s, 22, highlight ? Color.WHITE : 0xFFBBBBBB);
        t.setPadding(dp(20), dp(12), dp(20), dp(12));
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        setBg(t, 0x00000000);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                setBg(t, has ? 0x44FFFFFF : 0x00000000);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private void setBg(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
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
