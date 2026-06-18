package com.example.muzlove;

import android.graphics.Color;

public class Playlist {
    public final String name, desc;
    public final int c1, c2;
    public final int[] ids;

    public Playlist(String name, String desc, String c1, String c2, int[] ids) {
        this.name = name;
        this.desc = desc;
        this.c1 = Color.parseColor(c1);
        this.c2 = Color.parseColor(c2);
        this.ids = ids;
    }
}
