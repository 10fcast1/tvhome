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

/** Finds installed apps and builds the Home lineup (same lineup on every look). */
class AppRepo {

    static class App {
        String pkg;
        String label;
        Intent intent;       // null when the app is not installed
        Drawable banner;     // may be null
        Drawable icon;       // may be null
        int color;           // accent colour for tiles and the hero banner
        boolean installed = true;
        String tagline = "";
        String tags = "";    // L live, M movies, S shows, B library
        String storePkg;     // package to look up in the Appstore when not installed
    }

    /** A known streaming service. */
    static class Svc {
        final String name;
        final int color;
        final boolean core;     // shown by default, installed or not
        final String tags;
        final String tagline;
        final String[] pkgs;
        final String[] labels;

        Svc(String name, int color, boolean core, String tags, String tagline,
            String[] pkgs, String... labels) {
            this.name = name;
            this.color = color;
            this.core = core;
            this.tags = tags;
            this.tagline = tagline;
            this.pkgs = pkgs;
            this.labels = labels;
        }
    }

    /**
     * Most-used US streaming apps, in rough order of popularity. "core" ones are always on Home;
     * installed ones come first, the rest show a GET badge and open the Amazon Appstore.
     * Matching is by package name first, then by app label, so a wrong package name here
     * usually still works for apps you have installed.
     */
    static final Svc[] SERVICES = {
            new Svc("YouTube", 0xFFE62117, true, "LB", "Videos, music and live streams from around the world.",
                    new String[]{"com.amazon.firetv.youtube", "com.google.android.youtube.tv"}, "YouTube"),
            new Svc("Netflix", 0xFFE50914, true, "MS", "Series, films and originals, ready when you are.",
                    new String[]{"com.netflix.ninja"}, "Netflix"),
            new Svc("Prime Video", 0xFF00A8E1, true, "MS", "Included with Prime, plus rentals, purchases and channels.",
                    new String[]{"com.amazon.amazonvideo.livingroom"}, "Prime Video"),
            new Svc("Disney+", 0xFF113CCF, true, "MS", "Disney, Pixar, Marvel, Star Wars and National Geographic.",
                    new String[]{"com.disney.disneyplus"}, "Disney+", "Disney Plus"),
            new Svc("Hulu", 0xFF1CE783, true, "LS", "Current TV episodes, originals and live TV options.",
                    new String[]{"com.hulu.plus", "com.hulu.livingroomplus"}, "Hulu"),
            new Svc("HBO Max", 0xFF5822B4, true, "MS", "Prestige series, blockbuster films and Warner Bros. titles.",
                    new String[]{"com.wbd.stream", "com.hbo.hbonow"}, "HBO Max", "Max"),
            new Svc("Peacock", 0xFF2B2B33, true, "LMS", "NBCUniversal shows, movies, news and live sports.",
                    new String[]{"com.peacocktv.peacockandroid"}, "Peacock", "Peacock TV"),
            new Svc("Paramount+", 0xFF0064FF, true, "LMS", "CBS, Paramount films, live sports and originals.",
                    new String[]{"com.cbs.ott", "com.cbs.app"}, "Paramount+", "Paramount Plus"),
            new Svc("YouTube TV", 0xFFCC1A14, true, "L", "Live TV from major networks with a cloud DVR.",
                    new String[]{"com.amazon.firetv.youtube.tv", "com.google.android.youtube.tvunplugged"}, "YouTube TV"),
            new Svc("Apple TV", 0xFF3A3A3C, true, "MS", "Apple Originals plus movies and shows to rent or buy.",
                    new String[]{"com.apple.atve.amazon.appletv"}, "Apple TV", "Apple TV+"),
            new Svc("Tubi", 0xFFFA382F, true, "LM", "Free movies and TV, supported by ads.",
                    new String[]{"com.tubitv"}, "Tubi", "Tubi TV"),
            new Svc("Pluto TV", 0xFF3B3B8F, true, "LS", "Free live channels and on-demand TV.",
                    new String[]{"tv.pluto.android"}, "Pluto TV"),
            new Svc("Plex", 0xFFE5A00D, true, "B", "Your own media library plus free movies and live TV.",
                    new String[]{"com.plexapp.android"}, "Plex"),
            new Svc("Spotify", 0xFF1DB954, true, "B", "Music and podcasts on the big screen.",
                    new String[]{"com.spotify.tv.android"}, "Spotify"),
            new Svc("Twitch", 0xFF9146FF, true, "L", "Live streams from gamers and creators.",
                    new String[]{"tv.twitch.android.app"}, "Twitch"),
            new Svc("STARZ", 0xFF1A1A1A, true, "MS", "Premium series and movies.",
                    new String[]{"com.bydeluxe.d3.android.program.starz"}, "STARZ"),
            // Not on Home by default, but recognised if you add them with "Edit apps".
            new Svc("Crunchyroll", 0xFFF47521, false, "S", "Anime, simulcasts and classics.",
                    new String[]{"com.crunchyroll.crunchyroid"}, "Crunchyroll"),
            new Svc("Sling TV", 0xFF1B6EF3, false, "L", "Live TV packages you build yourself.",
                    new String[]{"com.sling"}, "Sling TV", "Sling"),
            new Svc("fuboTV", 0xFFFA4616, false, "L", "Live sports and TV.",
                    new String[]{"com.fubo.firetv.screen"}, "fuboTV", "Fubo"),
            new Svc("discovery+", 0xFF0A47FF, false, "S", "Reality, home, food and true crime.",
                    new String[]{"com.discovery.discoveryplus.firetv"}, "discovery+"),
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
        }
        for (Svc s : SERVICES) {
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
                Svc s = match(p, a.label);
                if (s != null) {
                    a.tagline = s.tagline;
                    a.tags = s.tags;
                }
                a.storePkg = p;
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

    private static App missing(Svc s) {
        App a = new App();
        a.pkg = s.pkgs[0];
        a.label = s.name;
        a.color = s.color;
        a.installed = false;
        a.tagline = s.tagline;
        a.tags = s.tags;
        a.storePkg = s.pkgs[0];
        return a;
    }

    /**
     * The apps to show on Home, in order. By default: every core service, with the ones you have
     * installed first. After "Edit apps", exactly the apps you chose.
     */
    static List<App> favorites(Context c, Prefs prefs) {
        List<App> all = loadAll(c);
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (App a : all) byPkg.put(a.pkg, a);

        List<String> saved = prefs.favorites();
        List<App> out = new ArrayList<>();
        if (saved == null) {
            Set<String> used = new HashSet<>();
            List<App> notInstalled = new ArrayList<>();
            for (Svc s : SERVICES) {
                if (!s.core) continue;
                App found = null;
                for (String p : s.pkgs) {
                    App a = byPkg.get(p);
                    if (a != null) {
                        found = a;
                        break;
                    }
                }
                if (found == null) {
                    for (App a : all) {
                        for (String l : s.labels) {
                            if (l.equalsIgnoreCase(a.label)) {
                                found = a;
                                break;
                            }
                        }
                        if (found != null) break;
                    }
                }
                if (found != null) {
                    if (used.add(found.pkg)) out.add(found);
                } else {
                    notInstalled.add(missing(s));
                }
            }
            out.addAll(notInstalled);
        } else {
            for (String p : saved) {
                App a = byPkg.get(p);
                if (a != null) out.add(a);
            }
        }
        return out;
    }
}
