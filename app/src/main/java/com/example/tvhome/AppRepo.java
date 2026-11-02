package com.example.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finds installed, launchable apps and resolves the Home list. */
class AppRepo {

    static class App {
        String pkg;
        String label;
        Intent intent;
        Drawable banner; // may be null
        Drawable icon;
    }

    /**
     * Default Home apps. Each row: display name, then every package name that app is known by
     * (the Android TV build and the Fire TV build often differ). First installed match wins.
     * To add a future app permanently, add a row here, or just use "Edit apps" on the TV.
     */
    static final String[][] DEFAULTS = {
            {"YouTube", "com.google.android.youtube.tv", "com.amazon.firetv.youtube"},
            {"Netflix", "com.netflix.ninja"},
            {"YouTube TV", "com.google.android.youtube.tvunplugged", "com.amazon.firetv.youtube.tv"},
            {"Hulu", "com.hulu.plus", "com.hulu.livingroomplus"},
            {"Disney+", "com.disney.disneyplus"},
            {"Peacock", "com.peacocktv.peacockandroid"},
    };

    static List<App> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> map = new LinkedHashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (p.equals(c.getPackageName()) || map.containsKey(p)) continue;
                App a = new App();
                a.pkg = p;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setClassName(p, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                try {
                    a.banner = ri.activityInfo.loadBanner(pm);
                } catch (Exception ignored) {
                }
                a.icon = ri.loadIcon(pm);
                map.put(p, a);
            }
        }
        List<App> list = new ArrayList<>(map.values());
        Collections.sort(list, new Comparator<App>() {
            @Override
            public int compare(App x, App y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        return list;
    }

    /** The apps to show on Home, in order. */
    static List<App> favorites(Context c, Prefs prefs) {
        List<App> all = loadAll(c);
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (App a : all) byPkg.put(a.pkg, a);

        List<String> saved = prefs.favorites();
        List<App> out = new ArrayList<>();
        if (saved == null) {
            for (String[] row : DEFAULTS) {
                for (int i = 1; i < row.length; i++) {
                    App a = byPkg.get(row[i]);
                    if (a != null) {
                        out.add(a);
                        break;
                    }
                }
            }
        } else {
            for (String p : saved) {
                App a = byPkg.get(p);
                if (a != null) out.add(a);
            }
        }
        return out;
    }
}
