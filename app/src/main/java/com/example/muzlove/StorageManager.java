package com.example.muzlove;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StorageManager {
    private static final String PREF_NAME = "muzlove_prefs";
    private static final String KEY_PLAYLISTS = "custom_playlists";
    private static final String KEY_TRACKS = "uploaded_tracks";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_LIGHT = "theme_light";
    private static final String KEY_THEME_INITIALIZED = "theme_initialized";
    private static final String KEY_WAVE = "waveform_on";
    private static final String KEY_LIKES = "liked_ids";
    private static final String KEY_REGISTERED = "is_registered";
    private static final String KEY_AVATAR = "profile_avatar";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final String KEY_SHUFFLE = "player_shuffle";
    private static final String KEY_REPEAT = "player_repeat";
    private static final String KEY_NOTIF_ASKED = "notifications_asked";
    private static final String KEY_EQ_ENABLED = "eq_enabled";
    private static final String KEY_EQ_BANDS = "eq_bands";
    private static final String KEY_EQ_PRESET = "eq_preset";

    private final SharedPreferences prefs;
    private final Gson gson;

    public StorageManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    // ---- плейлисты ----
    public void savePlaylists(List<Playlist> playlists) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    public List<Playlist> getPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Playlist>>() {}.getType();
        List<Playlist> list = null;
        try { list = gson.fromJson(json, type); } catch (RuntimeException ignored) {}
        List<Playlist> out = new ArrayList<>();
        if (list != null) for (Playlist p : list) if (p != null) out.add(p);
        return out;
    }

    // ---- загруженные треки ----
    public void saveTracks(List<Track> tracks) {
        prefs.edit().putString(KEY_TRACKS, gson.toJson(tracks)).apply();
    }

    public List<Track> getTracks() {
        String json = prefs.getString(KEY_TRACKS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Track>>() {}.getType();
        List<Track> list = null;
        try { list = gson.fromJson(json, type); } catch (RuntimeException ignored) {}
        List<Track> out = new ArrayList<>();
        if (list != null) for (Track t : list) if (t != null) out.add(t);
        return out;
    }

    // ---- имя профиля ----
    public String getName() { return prefs.getString(KEY_NAME, "Меломан"); }
    public void setName(String name) { prefs.edit().putString(KEY_NAME, name).apply(); }

    // ---- тема ----
    public boolean isLight() {
        if (!prefs.contains(KEY_THEME_INITIALIZED)) {
            // Первый вход: ставим светлую тему по умолчанию и помечаем как инициализированную
            prefs.edit().putBoolean(KEY_LIGHT, true).putBoolean(KEY_THEME_INITIALIZED, true).apply();
            return true;
        }
        return prefs.getBoolean(KEY_LIGHT, true);
    }
    public void setLight(boolean v) {
        prefs.edit().putBoolean(KEY_LIGHT, v).putBoolean(KEY_THEME_INITIALIZED, true).apply();
    }

    // ---- звуковая волна / ползунок (по умолчанию — ползунок) ----
    public boolean isWaveform() { return prefs.getBoolean(KEY_WAVE, false); }
    public void setWaveform(boolean v) { prefs.edit().putBoolean(KEY_WAVE, v).apply(); }

    // ---- режимы плеера: перемешивание и повтор (0 выкл, 1 все, 2 один) ----
    public boolean getShuffle() { return prefs.getBoolean(KEY_SHUFFLE, false); }
    public void setShuffle(boolean v) { prefs.edit().putBoolean(KEY_SHUFFLE, v).apply(); }
    public int getRepeat() { return prefs.getInt(KEY_REPEAT, 0); }
    public void setRepeat(int v) { prefs.edit().putInt(KEY_REPEAT, v).apply(); }

    // ---- разрешение на уведомления уже запрашивали (API 33+, спрашиваем один раз) ----
    public boolean wasNotificationsAsked() { return prefs.getBoolean(KEY_NOTIF_ASKED, false); }
    public void setNotificationsAsked() { prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply(); }

    // ---- эквалайзер: включён, уровни полос (миллибелы), пресет (-1 — свой) ----
    public boolean isEqEnabled() { return prefs.getBoolean(KEY_EQ_ENABLED, false); }
    public void setEqEnabled(boolean v) { prefs.edit().putBoolean(KEY_EQ_ENABLED, v).apply(); }
    public int getEqPreset() { return prefs.getInt(KEY_EQ_PRESET, -1); }
    public void setEqPreset(int p) { prefs.edit().putInt(KEY_EQ_PRESET, p).apply(); }

    public int[] getEqBands() {
        String json = prefs.getString(KEY_EQ_BANDS, null);
        if (json == null) return new int[0];
        int[] out = null;
        try { out = gson.fromJson(json, int[].class); } catch (RuntimeException ignored) {}
        return out != null ? out : new int[0];
    }

    public void setEqBands(int[] levels) {
        prefs.edit().putString(KEY_EQ_BANDS, gson.toJson(levels != null ? levels : new int[0])).apply();
    }

    // ---- лайки ----
    public void saveLikes(Set<Integer> likes) {
        prefs.edit().putString(KEY_LIKES, gson.toJson(new ArrayList<>(likes))).apply();
    }

    public Set<Integer> getLikes() {
        String json = prefs.getString(KEY_LIKES, null);
        if (json == null) return null; // null => первый запуск, поставим значения по умолчанию
        Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
        List<Integer> list = null;
        try { list = gson.fromJson(json, type); } catch (RuntimeException ignored) {}
        Set<Integer> out = new HashSet<>();
        if (list != null) for (Integer id : list) if (id != null) out.add(id);
        return out;
    }

    // ---- регистрация ----
    public boolean isRegistered() { return prefs.getBoolean(KEY_REGISTERED, false); }
    public void setRegistered(boolean v) { prefs.edit().putBoolean(KEY_REGISTERED, v).apply(); }

    // ---- аватар ----
    public String getAvatarUri() { return prefs.getString(KEY_AVATAR, null); }
    public void setAvatarUri(String uri) { prefs.edit().putString(KEY_AVATAR, uri).apply(); }

    // ---- история поиска ----
    public List<String> getSearchHistory() {
        String json = prefs.getString(KEY_SEARCH_HISTORY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<String>>() {}.getType();
        List<String> list = null;
        try { list = gson.fromJson(json, type); } catch (RuntimeException ignored) {}
        List<String> out = new ArrayList<>();
        if (list != null) for (String s : list) if (s != null) out.add(s);
        return out;
    }

    public void addSearchHistory(String q) {
        if (q == null) return;
        q = q.trim();
        if (q.isEmpty()) return;
        List<String> list = getSearchHistory();
        // убрать дубликат (без учёта регистра) и поставить запрос первым
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(q)) list.remove(i);
        }
        list.add(0, q);
        while (list.size() > 12) list.remove(list.size() - 1);
        prefs.edit().putString(KEY_SEARCH_HISTORY, gson.toJson(list)).apply();
    }

    /** Убрать один запрос (без учёта регистра). */
    public void removeSearchHistory(String q) {
        if (q == null) return;
        List<String> list = getSearchHistory();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).equalsIgnoreCase(q.trim())) { list.remove(i); changed = true; }
        }
        if (changed) prefs.edit().putString(KEY_SEARCH_HISTORY, gson.toJson(list)).apply();
    }

    public void clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply();
    }
}