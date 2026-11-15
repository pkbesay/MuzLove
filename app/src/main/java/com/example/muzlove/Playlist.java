package com.example.muzlove;

import android.graphics.Color;

public class Playlist {
    // name / desc / ids изменяемые: свои плейлисты можно редактировать.
    // В старых сохранённых данных desc и ids могут быть null.
    public String name, desc;
    public final int c1, c2;
    public int[] ids;

    public Playlist(String name, String desc, String c1, String c2, int[] ids) {
        this.name = name;
        this.desc = desc;
        this.c1 = Color.parseColor(c1);
        this.c2 = Color.parseColor(c2);
        this.ids = ids;
    }
}
