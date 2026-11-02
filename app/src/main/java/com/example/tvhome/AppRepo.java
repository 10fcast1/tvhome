package com.example.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds installed, launchable apps and resolves the Home list. */
class AppRepo {

    static class App {
        String pkg;
        String label;
        Intent intent;
        Drawable banner; // may be null
        Drawable icon;
        int color;       // accent colour used for tiles and the Google TV background
    }

    /** A known streaming service: display name, accent colour, package names, and label aliases. */
    static class Svc {
        final String name;
        final int color;
        final String[] pkgs;
        final String[] labels;

        Svc(String name, int color, String[] pkgs, String... labels) {
            this.name = name;
            this.color = color;
            this.pkgs = pkgs;
            this.labels = labels;
        }
    }

    /**
     * Services shown on Home by default (only the ones actually installed appear).
     * Matching is by package name first, then by app label, so a wrong package name
     * here usually still works. To add a service for good, add a row.
     */
    static final Svc[] SERVICES = {
            new Svc("YouTube", 0xFFE62117, new String[]{"com.google.android.youtube.tv", "com.amazon.firetv.youtube"}, "YouTube"),
            new Svc("Netflix", 0xFFE50914, new String[]{"com.netflix.ninja"}, "Netflix"),
            new Svc("YouTube TV", 0xFFCC1A14, new String[]{"com.google.android.youtube.tvunplugged", "com.amazon.firetv.youtube.tv"}, "YouTube TV"),
            new Svc("Hulu", 0xFF1CE783, new String[]{"com.hulu.plus", "com.hulu.livingroomplus"}, "Hulu"),
            new Svc("Disney+", 0xFF113CCF, new String[]{"com.disney.disneyplus"}, "Disney+", "Disney Plus"),
            new Svc("Peacock", 0xFF3A3A3A, new String[]{"com.peacocktv.peacockandroid"}, "Peacock", "Peacock TV"),
            new Svc("Prime Video", 0xFF00A8E1, new String[]{"com.amazon.amazonvideo.livingroom"}, "Prime Video"),
            new Svc("Max", 0xFF002BE7, new String[]{"com.wbd.stream", "com.hbo.hbonow"}, "Max", "HBO Max"),
            new Svc("Apple TV", 0xFF2C2C2E, new String[]{"com.apple.atve.amazon.appletv"}, "Apple TV", "Apple TV+"),
            new Svc("Paramount+", 0xFF0064FF, new String[]{"com.cbs.ott", "com.cbs.app"}, "Paramount+", "Paramount Plus"),
            new Svc("Tubi", 0xFFFA382F, new String[]{"com.tubitv"}, "Tubi", "Tubi TV"),
            new Svc("Pluto TV", 0xFF3B3B8F, new String[]{"tv.pluto.android"}, "Pluto TV"),
            new Svc("Plex", 0xFFE5A00D, new String[]{"com.plexapp.android"}, "Plex"),
            new Svc("Crunchyroll", 0xFFF47521, new String[]{"com.crunchyroll.crunchyroid"}, "Crunchyroll"),
            new Svc("Spotify", 0xFF1DB954, new String[]{"com.spotify.tv.android"}, "Spotify"),
            new Svc("Twitch", 0xFF9146FF, new String[]{"tv.twitch.android.app"}, "Twitch"),
            new Svc("Sling TV", 0xFF1B6EF3, new String[]{"com.sling"}, "Sling TV", "Sling"),
            new Svc("fuboTV", 0xFFFA4616, new String[]{"com.fubo.firetv.screen"}, "fuboTV", "Fubo"),
            new Svc("discovery+", 0xFF0A47FF, new String[]{"com.discovery.discoveryplus.firetv"}, "discovery+"),
            new Svc("STARZ", 0xFF1A1A1A, new String[]{"com.bydeluxe.d3.android.program.starz"}, "STARZ"),
    };

    // ------------------------------------------------------------ colour helpers

    /** Blend colour c toward colour "to" by t (0..1). Result is fully opaque. */
    static int mix(int c, int to, float t) {
        int r = (int) (Color.red(c) + (Color.red(to) - Color.red(c)) * t);
        int g = (int) (Color.green(c) + (Color.green(to) - Color.green(c)) * t);
        int b = (int) (Color.blue(c) + (Color.blue(to) - Color.blue(c)) * t);
        return Color.rgb(r, g, b);
    }

    private static Svc match(String pkg, String label) {
        for (Svc s : SERVICES) {
            for (String p : s.pkgs) {
                if (p.equals(pkg)) return s;
            }
            for (String l : s.labels) {
                if (l.equalsIgnoreCase(label)) return s;
            }
        }
        return null;
    }

    /** Brand colour for known services, a stable pleasant colour for everything else. */
    static int colorFor(String pkg, String label) {
        Svc s = match(pkg, label);
        if (s != null) return s.color;
        float hue = Math.abs(pkg.hashCode()) % 360;
        return Color.HSVToColor(new float[]{hue, 0.50f, 0.62f});
    }

    // ------------------------------------------------------------ discovery

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
                a.color = colorFor(p, a.label);
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
            Set<String> used = new HashSet<>();
            for (Svc s : SERVICES) {
                App found = null;
                for (String p : s.pkgs) {
                    if (byPkg.containsKey(p)) {
                        found = byPkg.get(p);
                        break;
                    }
                }
                if (found == null) {
                    for (App a : all) {
                        for (String l : s.labels) {
                            if (l.equalsIgnoreCase(a.label)) found = a;
                        }
                        if (found != null) break;
                    }
                }
                if (found != null && used.add(found.pkg)) out.add(found);
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
