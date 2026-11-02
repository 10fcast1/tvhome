package com.example.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Stores the chosen layout and the ordered list of apps shown on Home. */
class Prefs {
    static final int MODE_ANDROID_TV = 0;
    static final int MODE_GOOGLE_TV = 1;
    static final int MODE_APPLE_TV = 2;
    static final String[] MODE_NAMES = {"Android TV", "Google TV", "Apple TV"};

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
    }

    int mode() {
        return sp.getInt("mode", MODE_GOOGLE_TV);
    }

    void setMode(int m) {
        sp.edit().putInt("mode", m).apply();
    }

    /** Returns null when the user has never customised the list (use defaults). */
    List<String> favorites() {
        String s = sp.getString("favs", null);
        if (s == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) {
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    void setFavorites(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        sp.edit().putString("favs", sb.toString()).apply();
    }

    void resetFavorites() {
        sp.edit().remove("favs").apply();
    }
}
