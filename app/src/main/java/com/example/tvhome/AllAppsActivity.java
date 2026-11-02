package com.example.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Every installed app, so you can always reach Settings, Downloader, etc. */
public class AllAppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(30), dp(56), dp(10));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF181B22, 0xFF0D0F14}));

        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(title);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setPadding(0, dp(18), 0, dp(30));

        List<AppRepo.App> apps = AppRepo.loadAll(this);
        View first = null;
        for (final AppRepo.App a : apps) {
            final LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(dp(8), dp(14), dp(8), dp(12));
            tile.setFocusable(true);
            tile.setClickable(true);
            style(tile, false);

            ImageView iv = new ImageView(this);
            iv.setImageDrawable(a.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(72)));

            TextView name = new TextView(this);
            name.setText(a.label);
            name.setTextSize(14);
            name.setTextColor(0xFFD0D5E0);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(dp(120), -2);
            nl.topMargin = dp(8);
            tile.addView(name, nl);

            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    style(tile, has);
                }
            });
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(a.intent);
                    } catch (Exception e) {
                        Toast.makeText(AllAppsActivity.this, "Couldn't open " + a.label, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(136);
            lp.height = -2;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(tile, lp);
            if (first == null) first = tile;
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(grid);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);
        if (first != null) first.requestFocus();
    }

    private void style(View v, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(focused ? 0x40FFFFFF : 0x00000000);
        if (focused) g.setStroke(dp(2), Color.WHITE);
        v.setBackground(g);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
