package com.example.muzlove;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Локальные копии встроенных онлайн-треков.
 * Копия создаётся в фоне после успешной подготовки трека и хранится в filesDir,
 * поэтому доступна без сети и не удаляется системой как обычный cacheDir.
 */
final class OfflineTrackCache {
    private static final long MAX_TRACK_BYTES = 40L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 250L * 1024L * 1024L;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private OfflineTrackCache() {}

    private static File directory(Context context) {
        return new File(context.getFilesDir(), "offline_tracks");
    }

    static File find(Context context, int trackId) {
        File file = new File(directory(context), "track_" + trackId + ".mp3");
        return file.isFile() && file.length() > 0 ? file : null;
    }

    static int countCached(Context context) {
        File dir = directory(context);
        File[] files = dir.listFiles((d, name) -> name.matches("track_[0-9]+\\.mp3"));
        return files == null ? 0 : files.length;
    }

    static void cacheAsync(Context context, Track track, Runnable onCached) {
        if (track == null || track.url == null || !track.url.startsWith("http")) return;
        if (find(context, track.id) != null) return;
        if (!DOWNLOADING.compareAndSet(false, true)) return;

        Context app = context.getApplicationContext();
        IO.execute(() -> {
            File temp = null;
            boolean saved = false;
            try {
                File dir = directory(app);
                if (!dir.exists() && !dir.mkdirs()) return;

                File destination = new File(dir, "track_" + track.id + ".mp3");
                if (destination.isFile() && destination.length() > 0) return;
                temp = new File(dir, "track_" + track.id + ".part");

                HttpURLConnection connection = (HttpURLConnection) new URL(track.url).openConnection();
                try {
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("User-Agent", "MuzLove-Android");
                    int status = connection.getResponseCode();
                    if (status != HttpURLConnection.HTTP_OK) return;

                    long expected = connection.getContentLengthLong();
                    if (expected > MAX_TRACK_BYTES) return;

                    long total = 0;
                    try (InputStream input = connection.getInputStream();
                         FileOutputStream output = new FileOutputStream(temp, false)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            total += read;
                            if (total > MAX_TRACK_BYTES) return;
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                    }
                    if (total < 1024) return;
                    if (destination.exists() && !destination.delete()) return;
                    if (!temp.renameTo(destination)) return;
                    saved = true;
                    trim(dir);
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) {
                // Кэш необязателен: потоковое воспроизведение уже работает независимо от него.
            } finally {
                if (!saved && temp != null && temp.exists()) temp.delete();
                DOWNLOADING.set(false);
            }

            if (saved && onCached != null) MAIN.post(onCached);
        });
    }

    private static void trim(File dir) {
        File[] files = dir.listFiles((d, name) -> name.matches("track_[0-9]+\\.mp3"));
        if (files == null || files.length == 0) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        long total = 0;
        for (File file : files) total += file.length();
        for (File file : files) {
            if (total <= MAX_TOTAL_BYTES) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }

    static void clear(Context context) {
        File dir = directory(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) file.delete();
            }
        }
        dir.delete();
    }
}
