package com.example.muzlove;

import android.graphics.Color;

import java.util.UUID;

/** Пользовательский плейлист. syncId и updatedAt используются облачной синхронизацией. */
public class Playlist {
    // name / desc / ids изменяемые: свои плейлисты можно редактировать.
    // Старые сохранённые записи Gson могут не содержать syncId и updatedAt.
    public String name, desc;
    public final int c1, c2;
    public int[] ids;
    public String syncId;
    public long updatedAt;

    public Playlist(String name, String desc, String c1, String c2, int[] ids) {
        this(name, desc, Color.parseColor(c1), Color.parseColor(c2), ids,
                UUID.randomUUID().toString(), System.currentTimeMillis());
    }

    public Playlist(String name, String desc, int c1, int c2, int[] ids,
                    String syncId, long updatedAt) {
        this.name = name;
        this.desc = desc;
        this.c1 = c1;
        this.c2 = c2;
        this.ids = ids;
        this.syncId = syncId;
        this.updatedAt = updatedAt;
    }
}
