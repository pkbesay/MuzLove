package com.example.muzlove;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Облачная синхронизация пользовательских лайков, своих плейлистов и профиля.
 * Сетевые запросы выполняются в фоне. Локальные данные остаются основным офлайн-хранилищем.
 */
final class SyncManager {
    private static final String PREFS = "muzlove_sync_metadata";
    private static final int BUILT_IN_TRACK_MAX = 10;
    private static final int MAX_PLAYLIST_RECORDS = 100;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();
    private static boolean running;
    private static boolean pending;
    private static final List<Runnable> callbacks = new ArrayList<>();

    private SyncManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String account(Context context) {
        String id = new AuthSessionStore(context).getAccountId();
        return id == null || id.trim().isEmpty() ? null : "user_" + id.trim();
    }

    private static String key(Context context, String suffix) {
        String account = account(context);
        return (account == null ? "no_account" : account) + "_" + suffix;
    }

    static void markLikeChanged(Context context, int trackId) {
        if (trackId < 1 || trackId > BUILT_IN_TRACK_MAX) return;
        SharedPreferences p = prefs(context);
        String k = key(context, "like_" + trackId);
        long previous = p.getLong(k, 0L);
        p.edit().putLong(k, nextTimestamp(previous)).apply();
        requestSync(context, null);
    }

    static void markProfileChanged(Context context) {
        SharedPreferences p = prefs(context);
        String k = key(context, "profile_updated_at");
        p.edit().putLong(k, nextTimestamp(p.getLong(k, 0L))).apply();
        requestSync(context, null);
    }

    /**
     * Вызывается при загрузке AppState. Старым плейлистам добавляются стабильные UUID.
     * Начальный список становится локальной базовой точкой, а не набором удалений.
     */
    static boolean ensurePlaylistMetadata(Context context, List<Playlist> playlists) {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Playlist playlist : playlists) {
            if (playlist == null) continue;
            if (playlist.syncId == null || playlist.syncId.trim().isEmpty()) {
                playlist.syncId = UUID.randomUUID().toString();
                changed = true;
            }
            if (playlist.updatedAt <= 0L) {
                playlist.updatedAt = now;
                changed = true;
            }
        }

        String activeKey = key(context, "playlist_active_ids");
        if (!prefs(context).contains(activeKey)) {
            savePlaylistTracking(context, playlists, readLongMap(context, "playlist_tombstones"));
        }
        return changed;
    }

    /**
     * Сравнивает плейлисты с предыдущим локальным снимком. Изменения получают новый timestamp,
     * а удалённые элементы превращаются в tombstone, чтобы удаление дошло до других устройств.
     */
    static void onLocalPlaylistsChanged(Context context, List<Playlist> playlists) {
        ensurePlaylistMetadata(context, playlists);
        SharedPreferences p = prefs(context);
        String activeKey = key(context, "playlist_active_ids");
        if (!p.contains(activeKey)) {
            savePlaylistTracking(context, playlists, readLongMap(context, "playlist_tombstones"));
            return;
        }

        Set<String> oldIds = readStringSet(p.getString(activeKey, "[]"));
        Map<String, String> oldFingerprints = readStringMap(
                p.getString(key(context, "playlist_fingerprints"), "{}"));
        Map<String, Long> tombstones = readLongMap(context, "playlist_tombstones");
        Set<String> currentIds = new HashSet<>();
        Map<String, String> currentFingerprints = new HashMap<>();

        for (Playlist playlist : playlists) {
            if (playlist == null || playlist.syncId == null) continue;
            currentIds.add(playlist.syncId);
            String fingerprint = fingerprint(playlist);
            String old = oldFingerprints.get(playlist.syncId);
            if (old != null && !old.equals(fingerprint)) {
                playlist.updatedAt = nextTimestamp(playlist.updatedAt);
            }
            currentFingerprints.put(playlist.syncId, fingerprint);
        }

        long deletedAt = System.currentTimeMillis();
        for (String oldId : oldIds) {
            if (!currentIds.contains(oldId)) {
                tombstones.put(oldId, nextTimestamp(tombstones.containsKey(oldId)
                        ? tombstones.get(oldId) : deletedAt - 1L));
            }
        }
        savePlaylistTracking(context, playlists, tombstones, currentFingerprints);
        requestSync(context, null);
    }

    private static String fingerprint(Playlist p) {
        StringBuilder b = new StringBuilder();
        b.append(p.name == null ? "" : p.name).append('|')
                .append(p.c1).append('|').append(p.c2).append('|');
        if (p.ids != null) for (int id : p.ids) b.append(id).append(',');
        return b.toString();
    }

    private static void savePlaylistTracking(
            Context context, List<Playlist> playlists, Map<String, Long> tombstones
    ) {
        Map<String, String> fingerprints = new HashMap<>();
        for (Playlist p : playlists) {
            if (p != null && p.syncId != null) fingerprints.put(p.syncId, fingerprint(p));
        }
        savePlaylistTracking(context, playlists, tombstones, fingerprints);
    }

    private static void savePlaylistTracking(
            Context context, List<Playlist> playlists, Map<String, Long> tombstones,
            Map<String, String> fingerprints
    ) {
        JSONArray ids = new JSONArray();
        for (Playlist p : playlists) {
            if (p != null && p.syncId != null) ids.put(p.syncId);
        }
        prefs(context).edit()
                .putString(key(context, "playlist_active_ids"), ids.toString())
                .putString(key(context, "playlist_fingerprints"), new JSONObject(fingerprints).toString())
                .putString(key(context, "playlist_tombstones"), new JSONObject(tombstones).toString())
                .apply();
    }

    private static Set<String> readStringSet(String json) {
        Set<String> out = new HashSet<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, null);
                if (value != null && !value.isEmpty()) out.add(value);
            }
        } catch (JSONException ignored) {}
        return out;
    }

    private static Map<String, String> readStringMap(String json) {
        Map<String, String> out = new HashMap<>();
        try {
            JSONObject object = new JSONObject(json);
            JSONArray names = object.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i);
                String value = object.optString(name, null);
                if (value != null) out.put(name, value);
            }
        } catch (JSONException ignored) {}
        return out;
    }

    private static Map<String, Long> readLongMap(Context context, String suffix) {
        Map<String, Long> out = new HashMap<>();
        try {
            JSONObject object = new JSONObject(prefs(context).getString(key(context, suffix), "{}"));
            JSONArray names = object.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String name = names.optString(i);
                out.put(name, object.optLong(name, 0L));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    private static long nextTimestamp(long previous) {
        return Math.max(System.currentTimeMillis(), previous + 1L);
    }

    static void resetLocalTracking(Context context) {
        // Metadata is device-local only. Clear it on an app reset, but keep the cloud account untouched.
        prefs(context).edit().clear().apply();
    }

    static void requestSync(Context context, Runnable onSuccess) {
        Context app = context.getApplicationContext();
        AuthSessionStore sessions = new AuthSessionStore(app);
        if (!sessions.hasRefreshToken() || !NetworkStatus.hasInternet(app)) return;

        synchronized (LOCK) {
            if (onSuccess != null) callbacks.add(onSuccess);
            if (running) {
                pending = true;
                return;
            }
            running = true;
        }
        startWorker(app);
    }

    private static void startWorker(Context app) {
        IO.execute(() -> {
            JSONObject response = null;
            try {
                response = performSync(app);
            } catch (Exception ignored) {
                // Сеть может быть недоступна. Локальные данные остаются нетронутыми.
            }
            final JSONObject result = response;
            MAIN.post(() -> {
                if (result != null) applyResponse(app, result);

                boolean runAgain;
                List<Runnable> done = Collections.emptyList();
                synchronized (LOCK) {
                    runAgain = pending;
                    pending = false;
                    if (!runAgain) {
                        running = false;
                        done = new ArrayList<>(callbacks);
                        callbacks.clear();
                    }
                }
                if (runAgain) {
                    startWorker(app);
                } else if (result != null) {
                    for (Runnable callback : done) {
                        try { callback.run(); } catch (RuntimeException ignored) {}
                    }
                } else {
                    synchronized (LOCK) { callbacks.clear(); }
                }
            });
        });
    }

    private interface AuthorizedCall {
        JSONObject run(String accessToken) throws IOException;
    }

    private static JSONObject authorized(
            Context context, AuthSessionStore sessions, AuthorizedCall call
    ) throws IOException {
        String access = sessions.getAccessToken();
        if (access == null || access.isEmpty()) access = refresh(context, sessions);
        try {
            return call.run(access);
        } catch (AuthApi.ApiException e) {
            if (e.statusCode != 401) throw e;
            return call.run(refresh(context, sessions));
        }
    }

    private static String refresh(Context context, AuthSessionStore sessions) throws IOException {
        String refresh = sessions.getRefreshToken();
        if (refresh == null || refresh.isEmpty()) throw new IOException("Сессия не найдена.");
        try {
            JSONObject updated = AuthApi.refresh(context, refresh);
            sessions.saveSession(updated);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Не удалось обновить сессию.", e);
        }
        String access = sessions.getAccessToken();
        if (access == null || access.isEmpty()) throw new IOException("Сервер не вернул access token.");
        return access;
    }

    private static JSONObject performSync(Context context) throws IOException {
        AuthSessionStore sessions = new AuthSessionStore(context);
        // Older installations may have a valid refresh token but no locally cached account ID yet.
        if (sessions.getAccountId() == null) refresh(context, sessions);
        JSONObject remote = authorized(context, sessions,
                token -> AuthApi.getSync(context, token));
        JSONObject local = createLocalPayload(context);
        JSONObject merged = mergePayloads(local, remote);
        return authorized(context, sessions,
                token -> AuthApi.putSync(context, merged, token));
    }

    private static JSONObject createLocalPayload(Context context) throws IOException {
        StorageManager storage = new StorageManager(context);
        JSONObject payload = new JSONObject();
        try {
            payload.put("name", storage.getName());
            payload.put("username", storage.getUsername());
            payload.put("profileUpdatedAt",
                    prefs(context).getLong(key(context, "profile_updated_at"), 0L));

            Set<Integer> liked = storage.getLikes();
            if (liked == null) {
                liked = new HashSet<>();
                liked.add(1);
                liked.add(4);
            }
            JSONArray likes = new JSONArray();
            for (int id = 1; id <= BUILT_IN_TRACK_MAX; id++) {
                JSONObject item = new JSONObject();
                item.put("trackId", id);
                item.put("liked", liked.contains(id));
                item.put("updatedAt", prefs(context).getLong(key(context, "like_" + id), 0L));
                likes.put(item);
            }
            payload.put("likes", likes);

            JSONArray playlists = new JSONArray();
            for (Playlist playlist : storage.getPlaylists()) {
                if (playlist == null || playlist.syncId == null) continue;
                playlists.put(playlistToJson(playlist));
            }
            Map<String, Long> tombstones = readLongMap(context, "playlist_tombstones");
            for (Map.Entry<String, Long> entry : tombstones.entrySet()) {
                JSONObject deleted = new JSONObject();
                deleted.put("id", entry.getKey());
                deleted.put("name", "Удалённый плейлист");
                deleted.put("c1", 0);
                deleted.put("c2", 0);
                deleted.put("trackIds", new JSONArray());
                deleted.put("deleted", true);
                deleted.put("updatedAt", entry.getValue());
                playlists.put(deleted);
            }
            payload.put("playlists", limitPlaylistRecords(playlists));
        } catch (JSONException e) {
            throw new IOException("Не удалось подготовить данные синхронизации.", e);
        }
        return payload;
    }

    private static JSONObject playlistToJson(Playlist playlist) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", playlist.syncId);
        item.put("name", playlist.name == null || playlist.name.trim().isEmpty()
                ? "Мой плейлист" : playlist.name);
        item.put("c1", playlist.c1);
        item.put("c2", playlist.c2);
        JSONArray ids = new JSONArray();
        if (playlist.ids != null) {
            for (int id : playlist.ids) if (id >= 1 && id <= BUILT_IN_TRACK_MAX) ids.put(id);
        }
        item.put("trackIds", ids);
        item.put("deleted", false);
        item.put("updatedAt", Math.max(0L, playlist.updatedAt));
        return item;
    }

    private static JSONArray limitPlaylistRecords(JSONArray input) {
        if (input.length() <= MAX_PLAYLIST_RECORDS) return input;
        List<JSONObject> active = new ArrayList<>();
        List<JSONObject> deleted = new ArrayList<>();
        for (int i = 0; i < input.length(); i++) {
            JSONObject item = input.optJSONObject(i);
            if (item == null) continue;
            if (item.optBoolean("deleted", false)) deleted.add(item);
            else active.add(item);
        }
        deleted.sort((a, b) -> Long.compare(b.optLong("updatedAt"), a.optLong("updatedAt")));
        JSONArray out = new JSONArray();
        for (JSONObject item : active) {
            if (out.length() >= MAX_PLAYLIST_RECORDS) break;
            out.put(item);
        }
        for (JSONObject item : deleted) {
            if (out.length() >= MAX_PLAYLIST_RECORDS) break;
            out.put(item);
        }
        return out;
    }

    private static JSONObject mergePayloads(JSONObject local, JSONObject remote) throws IOException {
        JSONObject merged = new JSONObject();
        try {
            long localProfileTime = local.optLong("profileUpdatedAt", 0L);
            long remoteProfileTime = remote.optLong("profileUpdatedAt", 0L);
            boolean useLocalProfile = localProfileTime > remoteProfileTime;
            merged.put("name", useLocalProfile
                    ? local.optString("name", remote.optString("name", "Меломан"))
                    : remote.optString("name", local.optString("name", "Меломан")));
            merged.put("username", useLocalProfile
                    ? local.optString("username", remote.optString("username", ""))
                    : remote.optString("username", local.optString("username", "")));
            merged.put("profileUpdatedAt", useLocalProfile ? localProfileTime : remoteProfileTime);

            Map<Integer, JSONObject> remoteLikes = new HashMap<>();
            JSONArray rLikes = remote.optJSONArray("likes");
            if (rLikes != null) for (int i = 0; i < rLikes.length(); i++) {
                JSONObject item = rLikes.optJSONObject(i);
                if (item != null) remoteLikes.put(item.optInt("trackId"), item);
            }
            JSONArray outLikes = new JSONArray();
            JSONArray lLikes = local.optJSONArray("likes");
            for (int id = 1; id <= BUILT_IN_TRACK_MAX; id++) {
                JSONObject l = findLike(lLikes, id);
                JSONObject r = remoteLikes.get(id);
                JSONObject selected = r == null || (l != null
                        && l.optLong("updatedAt") > r.optLong("updatedAt")) ? l : r;
                if (selected != null) outLikes.put(selected);
            }
            merged.put("likes", outLikes);

            Map<String, JSONObject> records = new LinkedHashMap<>();
            JSONArray rPlaylists = remote.optJSONArray("playlists");
            if (rPlaylists != null) for (int i = 0; i < rPlaylists.length(); i++) {
                JSONObject item = rPlaylists.optJSONObject(i);
                if (item != null && !item.optString("id", "").isEmpty()) {
                    records.put(item.optString("id"), item);
                }
            }
            JSONArray lPlaylists = local.optJSONArray("playlists");
            if (lPlaylists != null) for (int i = 0; i < lPlaylists.length(); i++) {
                JSONObject item = lPlaylists.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) continue;
                JSONObject old = records.get(id);
                if (old == null || item.optLong("updatedAt") > old.optLong("updatedAt")) {
                    records.put(id, item);
                }
            }
            List<JSONObject> playlistList = new ArrayList<>(records.values());
            playlistList.sort((a, b) -> Long.compare(b.optLong("updatedAt"), a.optLong("updatedAt")));
            JSONArray outPlaylists = new JSONArray();
            for (JSONObject item : playlistList) {
                if (outPlaylists.length() >= MAX_PLAYLIST_RECORDS) break;
                outPlaylists.put(item);
            }
            merged.put("playlists", outPlaylists);
        } catch (JSONException e) {
            throw new IOException("Не удалось объединить локальные и облачные данные.", e);
        }
        return merged;
    }

    private static JSONObject findLike(JSONArray array, int id) {
        if (array == null) return null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && item.optInt("trackId") == id) return item;
        }
        return null;
    }

    /** Применяет ответ на главном потоке; более новые локальные изменения не затираются. */
    private static void applyResponse(Context context, JSONObject response) {
        StorageManager storage = new StorageManager(context);
        SharedPreferences p = prefs(context);

        long localProfileTime = p.getLong(key(context, "profile_updated_at"), 0L);
        long remoteProfileTime = response.optLong("profileUpdatedAt", 0L);
        if (remoteProfileTime >= localProfileTime) {
            String name = response.optString("name", "");
            String username = response.optString("username", "");
            if (!name.trim().isEmpty()) storage.setName(name);
            if (Username.isValid(username)) storage.setUsername(username);
            p.edit().putLong(key(context, "profile_updated_at"), remoteProfileTime).apply();
        }

        Set<Integer> likes = storage.getLikes();
        if (likes == null) {
            likes = new HashSet<>();
            likes.add(1);
            likes.add(4);
        }
        JSONArray remoteLikes = response.optJSONArray("likes");
        if (remoteLikes != null) {
            for (int i = 0; i < remoteLikes.length(); i++) {
                JSONObject item = remoteLikes.optJSONObject(i);
                if (item == null) continue;
                int id = item.optInt("trackId", -1);
                if (id < 1 || id > BUILT_IN_TRACK_MAX) continue;
                long remoteTime = item.optLong("updatedAt", 0L);
                long localTime = p.getLong(key(context, "like_" + id), 0L);
                if (remoteTime >= localTime) {
                    if (item.optBoolean("liked", false)) likes.add(id);
                    else likes.remove(id);
                    p.edit().putLong(key(context, "like_" + id), remoteTime).apply();
                }
            }
        }
        storage.saveLikes(likes);

        List<Playlist> current = storage.getPlaylists();
        Map<String, Playlist> localById = new HashMap<>();
        for (Playlist item : current) {
            if (item != null && item.syncId != null) localById.put(item.syncId, item);
        }

        Map<String, JSONObject> selected = new LinkedHashMap<>();
        Map<String, Long> oldTombstones = readLongMap(context, "playlist_tombstones");
        for (Playlist item : current) {
            if (item == null || item.syncId == null) continue;
            try {
                selected.put(item.syncId, playlistToJson(item));
            } catch (JSONException ignored) {}
        }
        for (Map.Entry<String, Long> entry : oldTombstones.entrySet()) {
            try {
                JSONObject deleted = new JSONObject();
                deleted.put("id", entry.getKey());
                deleted.put("name", "Удалённый плейлист");
                deleted.put("deleted", true);
                deleted.put("updatedAt", entry.getValue());
                selected.put(entry.getKey(), deleted);
            } catch (JSONException ignored) {}
        }

        JSONArray remotePlaylists = response.optJSONArray("playlists");
        if (remotePlaylists != null) {
            for (int i = 0; i < remotePlaylists.length(); i++) {
                JSONObject item = remotePlaylists.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "");
                if (id.isEmpty()) continue;
                JSONObject old = selected.get(id);
                if (old == null || item.optLong("updatedAt") >= old.optLong("updatedAt")) {
                    selected.put(id, item);
                }
            }
        }

        List<Playlist> result = new ArrayList<>();
        Map<String, Long> tombstones = new HashMap<>();
        for (JSONObject item : selected.values()) {
            String id = item.optString("id", "");
            if (id.isEmpty()) continue;
            long updatedAt = item.optLong("updatedAt", 0L);
            if (item.optBoolean("deleted", false)) {
                tombstones.put(id, updatedAt);
                continue;
            }

            Playlist local = localById.get(id);
            if (local != null && local.updatedAt > updatedAt) {
                result.add(local);
                continue;
            }

            String name = item.optString("name", "Мой плейлист");
            JSONArray trackIds = item.optJSONArray("trackIds");
            List<Integer> ids = new ArrayList<>();
            if (trackIds != null) for (int j = 0; j < trackIds.length(); j++) {
                int trackId = trackIds.optInt(j, -1);
                if (trackId >= 1 && trackId <= BUILT_IN_TRACK_MAX && !ids.contains(trackId)) ids.add(trackId);
            }
            // Загруженные аудиофайлы не покидают устройство. Сохраняем их ссылки
            // в одноимённом локальном плейлисте, даже когда облако меняет встроенные треки.
            if (local != null && local.ids != null) {
                for (int localId : local.ids) {
                    if (localId > BUILT_IN_TRACK_MAX && !ids.contains(localId)) ids.add(localId);
                }
            }
            int[] array = new int[ids.size()];
            for (int j = 0; j < ids.size(); j++) array[j] = ids.get(j);
            result.add(new Playlist(name, Texts.tracks(array.length),
                    item.optInt("c1"), item.optInt("c2"), array, id, updatedAt));
        }

        storage.savePlaylists(result);
        savePlaylistTracking(context, result, tombstones);
        AppState.get(context).reload();
    }
}
