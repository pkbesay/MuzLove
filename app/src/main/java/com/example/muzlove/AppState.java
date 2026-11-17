package com.example.muzlove;

import android.annotation.SuppressLint;
import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Единый источник пользовательских данных: загруженные треки, лайки, свои плейлисты.
 *  Синглтон процесса, только главный поток. Каждое изменение сразу сохраняется. */
public final class AppState {

    @SuppressLint("StaticFieldLeak")       // хранит только applicationContext
    private static AppState instance;

    public static AppState get(Context c) {
        if (instance == null) instance = new AppState(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final StorageManager storage;
    // «живые» коллекции: ссылки на них можно хранить (TrackAdapter держит liked)
    private final List<Track> uploaded = new ArrayList<>();
    private final Set<Integer> liked = new HashSet<>();
    private final List<Playlist> custom = new ArrayList<>();

    private AppState(Context app) {
        this.app = app;
        this.storage = new StorageManager(app);
        reload();
    }

    public StorageManager storage() { return storage; }

    /** Перечитывает всё из хранилища (коллекции остаются теми же объектами). */
    public void reload() {
        uploaded.clear();
        uploaded.addAll(storage.getTracks());
        custom.clear();
        custom.addAll(storage.getPlaylists());
        liked.clear();
        Set<Integer> saved = storage.getLikes();
        if (saved == null) { liked.add(1); liked.add(4); storage.saveLikes(liked); }   // первый запуск
        else liked.addAll(saved);
    }

    // ---------- треки ----------
    public List<Track> uploaded() { return uploaded; }

    /** Встроенные треки, затем загруженные (новый список). */
    public List<Track> allTracks() {
        List<Track> all = new ArrayList<>(Data.TRACKS.size() + uploaded.size());
        all.addAll(Data.TRACKS);
        all.addAll(uploaded);
        return all;
    }

    public Track find(int id) {
        for (Track t : Data.TRACKS) if (t.id == id) return t;
        for (Track t : uploaded) if (t.id == id) return t;
        return null;
    }

    public int nextUploadId() {
        int max = 999;
        for (Track t : uploaded) max = Math.max(max, t.id);
        return max + 1;
    }

    public void addUploaded(Track t) {
        if (t == null) return;
        uploaded.add(t);
        storage.saveTracks(uploaded);
    }

    /** Удаляет загруженный трек: файл (если он в filesDir), лайк и вхождения в своих плейлистах. */
    public boolean removeUploaded(int id) {
        Track found = null;
        for (Track t : uploaded) if (t.id == id) { found = t; break; }
        if (found == null) return false;
        uploaded.remove(found);
        deleteOwnFile(found.url);

        liked.remove(id);
        for (Playlist p : custom) {
            if (p.ids == null) continue;
            int n = 0;
            for (int x : p.ids) if (x != id) n++;
            if (n == p.ids.length) continue;
            int[] rest = new int[n];
            int i = 0;
            for (int x : p.ids) if (x != id) rest[i++] = x;
            p.ids = rest;
        }
        storage.saveTracks(uploaded);
        storage.saveLikes(liked);
        storage.savePlaylists(custom);
        return true;
    }

    private void deleteOwnFile(String path) {
        if (path == null || path.startsWith("http")) return;
        try {
            File f = new File(path).getCanonicalFile();
            String dir = app.getFilesDir().getCanonicalPath() + File.separator;
            if (f.getPath().startsWith(dir) && f.isFile()) f.delete();
        } catch (IOException | SecurityException ignored) {}
    }

    /** Сброс приложения: файлы загруженных треков и аватар удалены, настройки стёрты, данные прочитаны заново
     *  (как при первом запуске). Движок остановить заранее — PlayerEngine.stop(). */
    public void wipe() {
        for (Track t : new ArrayList<>(uploaded)) deleteOwnFile(t.url);
        Avatar.delete(app, storage.getAvatarUri());
        File[] files = app.getFilesDir().listFiles();            // копии без записи в настройках
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && (n.startsWith("track_") || n.startsWith("avatar_"))) f.delete();
            }
        }
        storage.clearAll();
        reload();
    }

    // ---------- лайки ----------
    public Set<Integer> liked() { return liked; }

    public boolean isLiked(int id) { return liked.contains(id); }

    /** Переключает лайк, возвращает новое состояние. */
    public boolean toggleLike(int id) {
        boolean now;
        if (liked.contains(id)) { liked.remove(id); now = false; }
        else { liked.add(id); now = true; }
        storage.saveLikes(liked);
        return now;
    }

    // ---------- плейлисты ----------
    public List<Playlist> customPlaylists() { return custom; }

    public void savePlaylists() { storage.savePlaylists(custom); }

    /** Встроенные плейлисты, затем свои (новый список). */
    public List<Playlist> allPlaylists() {
        List<Playlist> all = new ArrayList<>(Data.PLAYLISTS.size() + custom.size());
        all.addAll(Data.PLAYLISTS);
        all.addAll(custom);
        return all;
    }
}
