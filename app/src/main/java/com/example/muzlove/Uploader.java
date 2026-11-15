package com.example.muzlove;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Загрузка своих треков: копирование в filesDir и чтение метаданных в фоновом потоке,
 *  добавление в AppState — в главном. Переживает пересоздание и закрытие активности:
 *  треки добавятся в любом случае, а экран обновит та MainActivity, что жива к концу. */
final class Uploader {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static WeakReference<MainActivity> host = new WeakReference<>(null);

    /** Цвета обложек загруженных треков (по id). */
    private static final String[][] COLORS = {
            {"#10B981", "#06B6D4"}, {"#8B5CF6", "#EC4899"}, {"#F59E0B", "#EF4444"},
            {"#3B82F6", "#06B6D4"}, {"#F472B6", "#A855F7"}, {"#14B8A6", "#84CC16"},
            {"#F97316", "#DB2777"}, {"#6366F1", "#22D3EE"},
    };

    /** Скопированный файл с прочитанными метаданными (готов стать треком). */
    private static final class Item {
        File file;
        String title, artist, album;
        int durSec;
    }

    private Uploader() {}

    /** Активность, которой сообщить о конце загрузки (последняя созданная). */
    static void attach(MainActivity a) { host = new WeakReference<>(a); }

    static void start(Context c, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        Context app = c.getApplicationContext();
        List<Uri> todo = new ArrayList<>();
        for (Uri u : uris) if (u != null) todo.add(u);
        if (todo.isEmpty()) return;
        Toast.makeText(app, "Загружаем…", Toast.LENGTH_SHORT).show();
        Handler main = new Handler(Looper.getMainLooper());
        IO.execute(() -> {
            List<Item> ok = new ArrayList<>();
            int failed = 0;
            for (Uri u : todo) {
                Item it = null;
                try { it = prepare(app, u); } catch (RuntimeException ignored) {}
                if (it != null) ok.add(it); else failed++;
            }
            int f = failed;
            main.post(() -> finish(app, ok, f));
        });
    }

    // ---------- фоновый поток ----------
    private static Item prepare(Context app, Uri uri) {
        ContentResolver cr = app.getContentResolver();
        String name = displayName(cr, uri);
        String mimeExt = null;
        try {
            String type = cr.getType(uri);
            if (type != null) mimeExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
        } catch (RuntimeException ignored) {}

        File dir = app.getFilesDir();
        String ext = extension(name, mimeExt);
        File dest;
        do { dest = new File(dir, "track_" + UUID.randomUUID().toString().substring(0, 12) + ext); }
        while (dest.exists());

        try (InputStream in = cr.openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new java.io.IOException("no stream");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {          // IOException, SecurityException и т.п.
            dest.delete();
            return null;
        }
        if (dest.length() == 0) { dest.delete(); return null; }

        String title = null, artist = null, album = null, mime = null;
        long durMs = -1;
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(dest.getAbsolutePath());
            title = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            artist = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            if (artist == null) artist = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            album = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) durMs = Long.parseLong(d.trim());
        } catch (RuntimeException ignored) {
            // не читается: решит проверка ниже
        } finally {
            try { r.release(); } catch (Exception ignored) {}   // на API 29+ release() объявляет IOException
        }
        if (durMs <= 0 && !isAudioMime(mime)) { dest.delete(); return null; }   // не аудио

        Item it = new Item();
        it.file = dest;
        String fromName = fileTitle(name);
        it.title = title != null ? title : (fromName != null ? fromName : "Загруженный трек");
        it.artist = artist != null ? artist : "Неизвестный исполнитель";
        it.album = album != null ? album : "Загрузки";
        it.durSec = durMs > 0 ? (int) Math.max(1, Math.round(durMs / 1000.0)) : 0;
        return it;
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i != -1) return clean(c.getString(i));
            }
        } catch (RuntimeException ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? clean(last) : null;
    }

    // ---------- главный поток ----------
    private static void finish(Context app, List<Item> items, int failed) {
        AppState st = AppState.get(app);
        int added = 0, dup = 0;
        for (Item it : items) {
            if (isDuplicate(st, it)) { it.file.delete(); dup++; continue; }
            int id = st.nextUploadId();
            String[] c = COLORS[id % COLORS.length];
            st.addUploaded(new Track(id, it.title, it.artist, it.album, it.durSec, c[0], c[1],
                    it.file.getAbsolutePath()));
            added++;
        }
        List<String> msg = new ArrayList<>();
        if (added == 1) msg.add("Добавлен трек");
        else if (added > 1) msg.add("Добавлено треков: " + added);
        if (dup == 1) msg.add("Этот трек уже загружен");
        else if (dup > 1) msg.add("Уже были загружены: " + dup);
        if (failed > 0) msg.add(Texts.failedFiles(failed));
        if (!msg.isEmpty()) Toast.makeText(app, android.text.TextUtils.join("\n", msg), Toast.LENGTH_LONG).show();

        MainActivity m = host.get();
        if (m != null && !m.isFinishing() && !m.isDestroyed()) m.onUploadsFinished(added);
    }

    /** Тот же файл уже загружен: совпадают название, исполнитель, длительность и размер. */
    private static boolean isDuplicate(AppState st, Item it) {
        long size = it.file.length();
        for (Track t : st.uploaded()) {
            if (t.dur != it.durSec || !it.title.equals(t.title) || !it.artist.equals(t.artist)) continue;
            File f = AudioMeta.localFile(t);
            if (f != null && f.length() == size) return true;
        }
        return false;
    }

    // ---------- чистые помощники (юнит-тесты) ----------
    /** Расширение копии: из имени файла (если похоже на расширение), иначе по MIME, иначе «.audio». */
    static String extension(String displayName, String mimeExt) {
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot > 0 && dot < displayName.length() - 1) {
                String e = displayName.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (e.length() <= 5 && e.matches("[a-z0-9]+")) return "." + e;
            }
        }
        if (mimeExt != null) {
            String e = mimeExt.toLowerCase(Locale.ROOT);
            if (!e.isEmpty() && e.length() <= 5 && e.matches("[a-z0-9]+")) return "." + e;
        }
        return ".audio";
    }

    /** Название из имени файла: без расширения и пробелов по краям; null — если ничего не осталось. */
    static String fileTitle(String displayName) {
        if (displayName == null) return null;
        String s = displayName.trim();
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot).trim();
        return s.isEmpty() ? null : s;
    }

    static boolean isAudioMime(String mime) {
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.ROOT);
        return m.startsWith("audio/") || m.equals("application/ogg");
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
}
