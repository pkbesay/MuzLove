package com.example.muzlove;

import android.graphics.Color;

public class Track {
    public final int id;
    public final String title, artist, album, url;
    public final int dur;       // длительность в секундах (на случай отсутствия сети)
    public final int c1, c2;    // цвета обложки

    public Track(int id, String title, String artist, String album, int dur,
                 String c1, String c2, String url) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.dur = dur;
        this.c1 = Color.parseColor(c1);
        this.c2 = Color.parseColor(c2);
        this.url = url;
    }
}
