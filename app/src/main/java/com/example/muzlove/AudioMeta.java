package com.example.muzlove;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;

/** Сведения о локальном аудиофайле из MediaMetadataRetriever (для «О треке» и «Поделиться»).
 *  Для онлайн-треков и нечитаемых файлов все поля пустые. */
final class AudioMeta {

    String mime;              // null — неизвестно
    long durationMs = -1;
    int bitrate = -1;         // бит/с

    static boolean isOnline(Track t) { return t != null && t.url != null && t.url.startsWith("http"); }

    /** Файл загруженного трека или null (онлайн, content:, файла нет). */
    static File localFile(Track t) {
        if (t == null || t.url == null || t.url.isEmpty() || isOnline(t) || t.url.startsWith("content:")) return null;
        String path = t.url.startsWith("file:") ? Uri.parse(t.url).getPath() : t.url;
        if (path == null) return null;
        File f = new File(path);
        return f.isFile() ? f : null;
    }

    static AudioMeta read(Context c, Track t) {
        AudioMeta m = new AudioMeta();
        if (t == null || t.url == null || isOnline(t)) return m;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            if (t.url.startsWith("content:")) {
                r.setDataSource(c, Uri.parse(t.url));
            } else {
                File f = localFile(t);
                if (f == null) return m;
                r.setDataSource(f.getAbsolutePath());
            }
            m.mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            m.durationMs = parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            m.bitrate = (int) parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        } catch (RuntimeException ignored) {
            // повреждённый или неподдерживаемый файл
        } finally {
            try { r.release(); } catch (Exception ignored) {}   // на API 29+ release() объявляет IOException
        }
        return m;
    }

    private static long parse(String s) {
        if (s == null) return -1;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; }
    }
}
