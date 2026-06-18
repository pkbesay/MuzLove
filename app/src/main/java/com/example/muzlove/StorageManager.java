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
        List<Playlist> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    // ---- загруженные треки ----
    public void saveTracks(List<Track> tracks) {
        prefs.edit().putString(KEY_TRACKS, gson.toJson(tracks)).apply();
    }

    public List<Track> getTracks() {
        String json = prefs.getString(KEY_TRACKS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<ArrayList<Track>>() {}.getType();
        List<Track> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
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

    // ---- звуковая волна / ползунок ----
    public boolean isWaveform() { return prefs.getBoolean(KEY_WAVE, true); }
    public void setWaveform(boolean v) { prefs.edit().putBoolean(KEY_WAVE, v).apply(); }

    // ---- лайки ----
    public void saveLikes(Set<Integer> likes) {
        prefs.edit().putString(KEY_LIKES, gson.toJson(new ArrayList<>(likes))).apply();
    }

    public Set<Integer> getLikes() {
        String json = prefs.getString(KEY_LIKES, null);
        if (json == null) return null; // null => первый запуск, поставим значения по умолчанию
        Type type = new TypeToken<ArrayList<Integer>>() {}.getType();
        List<Integer> list = gson.fromJson(json, type);
        return list != null ? new HashSet<>(list) : new HashSet<>();
    }

    // ---- регистрация ----
    public boolean isRegistered() { return prefs.getBoolean(KEY_REGISTERED, false); }
    public void setRegistered(boolean v) { prefs.edit().putBoolean(KEY_REGISTERED, v).apply(); }

    // ---- аватар ----
    public String getAvatarUri() { return prefs.getString(KEY_AVATAR, null); }
    public void setAvatarUri(String uri) { prefs.edit().putString(KEY_AVATAR, uri).apply(); }
}
