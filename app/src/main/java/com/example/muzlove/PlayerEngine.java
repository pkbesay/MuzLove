package com.example.muzlove;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Движок воспроизведения: один MediaPlayer, очередь, аудиофокус, таймер сна.
 *  Синглтон процесса, только главный поток; переживает пересоздание MainActivity.
 *  UI и сервис уведомления наблюдают за ним через Listener и командуют публичными методами. */
public final class PlayerEngine {

    /** Все методы вызываются в главном потоке. */
    public interface Listener {
        /** Текущим стал другой трек, либо воспроизведение остановлено (current() == null). */
        default void onTrackChanged() {}
        /** Изменилось играет / пауза / буферизация (а также таймер сна). */
        default void onPlayStateChanged() {}
        default void onProgress(int positionMs, int durationMs) {}
        /** Позиция изменилась скачком (перемотка, повтор трека), а не ходом воспроизведения. */
        default void onSeeked() {}
        /** Изменились состав или порядок очереди, перемешивание или повтор. */
        default void onQueueChanged() {}
        /** Сообщение для пользователя (UI показывает Toast). */
        default void onPlaybackIssue(String message) {}
    }

    private static final int TICK_MS = 250;
    private static final String MSG_OFFLINE = "Не удалось загрузить трек. Проверьте подключение к интернету";
    private static final String MSG_FILE = "Файл трека недоступен";

    @SuppressLint("StaticFieldLeak")       // хранит только applicationContext
    private static PlayerEngine instance;

    public static PlayerEngine get(Context c) {
        if (instance == null) instance = new PlayerEngine(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final AppState data;
    private final StorageManager storage;
    private final PlayQueue queue = new PlayQueue();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final List<Listener> listeners = new ArrayList<>();
    private final EqualizerController equalizer;
    private final AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

    // плеер
    private MediaPlayer mp;
    private int generation = 0;              // защита от колбэков предыдущей загрузки
    private Track current;                   // null — ничего не загружено
    private boolean playing;                 // пользователь хочет, чтобы играло (в т.ч. во время подготовки)
    private boolean preparing, prepared, seeking;
    private boolean fallback;                // имитация воспроизведения онлайн-трека без сети
    private int positionMs, durationMs;
    private int pendingSeek = -1;            // перемотка, запрошенная во время подготовки
    private long lastTickAt, seekStartedAt;
    private boolean tickerPosted;
    private boolean offlineNotified;         // сообщение «нет сети» уже показано
    private int failStreak;                  // подряд недоступных локальных файлов
    private boolean announcedPlaying;        // слушателям уже сообщили «играет»

    // аудиофокус
    private Object focusRequest;             // AudioFocusRequest на API 26+
    private boolean focusRequested, hasFocus, pausedByFocus, ducked;
    private boolean noisyRegistered;

    // таймер сна
    private long sleepDeadline = -1;
    private boolean sleepAtEnd;

    private PlayerEngine(Context app) {
        this.app = app;
        this.data = AppState.get(app);
        this.storage = data.storage();
        this.audio = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        this.equalizer = new EqualizerController(storage);
        queue.setShuffle(storage.getShuffle());
        queue.setRepeat(storage.getRepeat());
    }

    // ---------- слушатели ----------
    public void addListener(Listener l) { if (l != null && !listeners.contains(l)) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyTrack() { for (Listener l : new ArrayList<>(listeners)) l.onTrackChanged(); }
    private void notifyState() {
        // переход в «играет» запускает сервис фонового воспроизведения (повторный вызов безвреден)
        boolean now = isPlaying();
        if (now && !announcedPlaying) PlaybackService.start(app);
        announcedPlaying = now;
        for (Listener l : new ArrayList<>(listeners)) l.onPlayStateChanged();
    }
    private void notifySeek() { for (Listener l : new ArrayList<>(listeners)) l.onSeeked(); }
    private void notifyQueue() { for (Listener l : new ArrayList<>(listeners)) l.onQueueChanged(); }
    private void notifyProgress() {
        int p = positionMs(), d = durationMs();
        for (Listener l : new ArrayList<>(listeners)) l.onProgress(p, d);
    }
    private void issue(String msg) { for (Listener l : new ArrayList<>(listeners)) l.onPlaybackIssue(msg); }

    // ---------- состояние ----------
    public boolean isStarted() { return current != null; }
    public boolean isPlaying() { return current != null && playing; }
    public boolean isBuffering() { return current != null && playing && preparing; }
    /** Пауза из-за временной потери аудиофокуса (звонок и т.п.): музыка продолжится сама. */
    public boolean isPausedTransiently() { return current != null && !playing && pausedByFocus; }
    public Track current() { return current; }

    public int positionMs() {
        if (current == null) return 0;
        // пока идёт перемотка, плеер ещё отдаёт старую позицию; страховка, если onSeekComplete не придёт
        if (seeking && SystemClock.elapsedRealtime() - seekStartedAt > 1500) seeking = false;
        if (prepared && !fallback && !seeking && mp != null) {
            try { positionMs = mp.getCurrentPosition(); } catch (IllegalStateException ignored) {}
        }
        return Math.max(0, Math.min(positionMs, durationMs()));
    }

    public int durationMs() {
        if (current == null) return 0;
        return Math.max(1, durationMs);
    }

    public List<Integer> queueIds() { return queue.order(); }
    public int queueIndex() { return queue.index(); }
    public boolean isShuffle() { return queue.isShuffle(); }
    public int repeatMode() { return queue.getRepeat(); }
    public int audioSessionId() {
        if (mp == null) return 0;
        try { return mp.getAudioSessionId(); } catch (IllegalStateException e) { return 0; }
    }
    /** Эквалайзер текущей аудиосессии (работает и когда ничего не играет: isAvailable() == false). */
    public EqualizerController equalizer() { return equalizer; }

    // ---------- команды ----------
    /** Очередь = список, из которого нажали трек. Повторное нажатие на текущий трек — пауза / продолжить. */
    public void playFrom(List<Integer> contextIds, int trackId) {
        if (current != null && current.id == trackId) { toggle(); return; }
        List<Integer> ids = contextIds != null ? new ArrayList<>(contextIds) : new ArrayList<>();
        if (!ids.contains(trackId)) ids.add(0, trackId);
        queue.set(ids, trackId);
        failStreak = 0;
        load(true);
        notifyQueue();
    }

    /** «Слушать» плейлист: как playFrom, но без переключения паузы. Если trackId уже текущий, очередь
     *  становится contextIds без перезагрузки трека: он продолжает играть (или начинает, если стоял на паузе). */
    public void playContext(List<Integer> contextIds, int trackId) {
        if (current == null || current.id != trackId) { playFrom(contextIds, trackId); return; }
        List<Integer> ids = contextIds != null ? new ArrayList<>(contextIds) : new ArrayList<>();
        if (!ids.contains(trackId)) ids.add(0, trackId);
        queue.set(ids, trackId);
        notifyQueue();
        if (!playing) play();
    }

    public void toggle() { if (isPlaying()) pause(); else play(); }

    public void play() {
        if (current == null || playing) return;
        if (sleepDeadline > 0 && SystemClock.elapsedRealtime() >= sleepDeadline) sleepDeadline = -1; // истёк на паузе
        boolean wasTransient = pausedByFocus;
        pausedByFocus = false;
        if (!requestFocus()) { if (wasTransient) notifyState(); return; }
        playing = true;
        lastTickAt = SystemClock.elapsedRealtime();
        if (prepared && !fallback && mp != null) {
            try { mp.start(); } catch (IllegalStateException e) { load(true); return; }
        } else if (!preparing && !fallback) {
            load(true);                        // плеер потерял трек — загрузить заново
            return;
        }
        registerNoisy();
        notifyState();
        updateTicker();
    }

    public void pause() {
        boolean wasTransient = pausedByFocus && !playing;
        pausedByFocus = false;
        pauseOutput();
        abandonFocus();
        if (wasTransient && current != null) notifyState();   // временная пауза стала обычной
    }

    /** Пауза без отказа от аудиофокуса (используется и при временной потере фокуса). */
    private void pauseOutput() {
        if (current == null) return;
        boolean was = playing;
        if (prepared && !fallback && mp != null) {
            try {
                if (mp.isPlaying()) mp.pause();
                if (!seeking) positionMs = mp.getCurrentPosition();   // во время перемотки плеер отдаёт старую позицию
            } catch (IllegalStateException ignored) {}
        }
        playing = false;
        unregisterNoisy();
        updateTicker();
        if (was) notifyState();
    }

    public void next() {
        if (current == null) return;
        queue.advance(false);
        load(playing);
    }

    public void prev() {
        if (current == null) return;
        if (positionMs() > 3000) { seekTo(0); return; }
        queue.back();
        load(playing);
    }

    public void seekTo(int ms) {
        if (current == null) return;
        ms = Math.max(0, Math.min(ms, durationMs()));
        positionMs = ms;
        if (prepared && !fallback && mp != null) {
            try { mp.seekTo(ms); markSeeking(); } catch (IllegalStateException ignored) {}
        } else if (preparing) {
            pendingSeek = ms;                  // применится в onPrepared
        }
        notifyProgress();
        notifySeek();
    }

    public void setShuffle(boolean on) {
        if (on == queue.isShuffle()) return;
        queue.setShuffle(on);
        storage.setShuffle(on);
        notifyQueue();
    }

    public void toggleShuffle() { setShuffle(!queue.isShuffle()); }

    public void cycleRepeat() {
        storage.setRepeat(queue.cycleRepeat());
        notifyQueue();
    }

    /** Переход к позиции очереди: трек начинает играть. */
    public void jumpTo(int queueIndex) {
        if (queueIndex < 0 || queueIndex >= queue.size()) return;
        if (queueIndex == queue.index() && current != null) { if (!playing) play(); return; }
        queue.jumpTo(queueIndex);
        failStreak = 0;
        load(true);
        notifyQueue();
    }

    public void removeAt(int queueIndex) {
        if (queueIndex < 0 || queueIndex >= queue.size()) return;
        boolean changed = queue.removeAt(queueIndex);
        if (queue.isEmpty()) { stop(); return; }
        if (changed) load(playing);
        notifyQueue();
    }

    public void removeTrack(int trackId) {
        int i = queue.indexOf(trackId);
        if (i >= 0) removeAt(i);
    }

    public void playNext(int trackId) {
        if (current == null) { playFrom(Collections.singletonList(trackId), trackId); return; }
        queue.playNext(trackId);
        notifyQueue();
    }

    /** Возвращает false, если трек уже был в очереди. */
    public boolean addToQueue(int trackId) {
        if (current == null) { playFrom(Collections.singletonList(trackId), trackId); return true; }
        if (!queue.append(trackId)) return false;
        notifyQueue();
        return true;
    }

    public void stop() {
        generation++;
        handler.removeCallbacks(ticker);
        tickerPosted = false;
        releasePlayer();
        current = null;
        playing = preparing = prepared = seeking = fallback = false;
        positionMs = durationMs = 0;
        pendingSeek = -1;
        failStreak = 0;
        offlineNotified = false;               // новое воспроизведение без сети снова объяснит тишину
        queue.set(null, -1);
        sleepDeadline = -1;
        sleepAtEnd = false;
        pausedByFocus = false;
        abandonFocus();
        unregisterNoisy();
        notifyTrack();
        notifyState();
        notifyQueue();
    }

    // ---------- таймер сна ----------
    /** 0 — отменить. */
    public void setSleepTimer(int minutes) {
        sleepDeadline = minutes > 0 ? SystemClock.elapsedRealtime() + minutes * 60_000L : -1;
        notifyState();
    }

    public void setSleepAtTrackEnd(boolean on) {
        sleepAtEnd = on;
        notifyState();
    }

    public long sleepRemainingMs() {
        if (sleepDeadline <= 0) return -1;
        return Math.max(0, sleepDeadline - SystemClock.elapsedRealtime());
    }

    public boolean isSleepAtTrackEnd() { return sleepAtEnd; }

    // ---------- загрузка ----------
    /** Загружает текущий трек очереди. autoplay=false — трек встаёт на паузу. */
    private void load(boolean autoplay) {
        Track t = null;
        boolean dropped = false;
        while (!queue.isEmpty()) {             // id удалённых треков просто выкидываем из очереди
            t = data.find(queue.currentId());
            if (t != null) break;
            queue.removeAt(queue.index());
            dropped = true;
        }
        if (t == null) { stop(); return; }
        if (dropped) notifyQueue();

        if (autoplay && !requestFocus()) autoplay = false;
        generation++;
        final int gen = generation;
        current = t;
        playing = autoplay;
        preparing = prepared = seeking = fallback = false;
        positionMs = 0;
        durationMs = Math.max(1000, t.dur * 1000);
        pendingSeek = -1;
        lastTickAt = SystemClock.elapsedRealtime();

        boolean online = isOnline(t);
        if (!online && !isReadableLocal(t)) { localFailed(); return; }

        ensurePlayer();
        try {
            mp.reset();
            mp.setAudioAttributes(attrs);
            mp.setOnPreparedListener(m -> onPrepared(gen));
            mp.setOnCompletionListener(m -> { if (gen == generation) onCompleted(); });
            mp.setOnErrorListener((m, what, extra) -> {
                // медиасервер умер: этот плеер больше не работает, следующая загрузка создаст новый
                if (gen == generation && what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) releasePlayer();
                onLoadFailed(gen);
                return true;
            });
            mp.setOnSeekCompleteListener(m -> { if (gen == generation) seeking = false; });
            if (t.url.startsWith("content:") || t.url.startsWith("file:")) mp.setDataSource(app, Uri.parse(t.url));
            else mp.setDataSource(t.url);
            mp.prepareAsync();
            preparing = true;
        } catch (Exception e) {
            if (online) enterFallback(); else { localFailed(); return; }
        }

        if (playing) registerNoisy();
        else { unregisterNoisy(); if (!pausedByFocus) abandonFocus(); }
        notifyTrack();
        notifyState();
        notifyProgress();
        updateTicker();
    }

    private void markSeeking() {
        seeking = true;
        seekStartedAt = SystemClock.elapsedRealtime();
    }

    private void ensurePlayer() {
        if (mp != null) return;
        mp = new MediaPlayer();
        mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        equalizer.onSession(audioSessionId());   // новый плеер — новая сессия: эквалайзер заново
    }

    private void releasePlayer() {
        if (mp == null) return;
        equalizer.release();
        try { mp.reset(); } catch (RuntimeException ignored) {}
        mp.release();
        mp = null;
    }

    private void onPrepared(int gen) {
        if (gen != generation || mp == null) return;
        preparing = false;
        prepared = true;
        fallback = false;
        failStreak = 0;
        offlineNotified = false;
        try {
            int d = mp.getDuration();
            if (d > 0) durationMs = d;
            applyVolume();
            if (pendingSeek >= 0) {
                int ms = Math.min(pendingSeek, durationMs);
                mp.seekTo(ms);
                positionMs = ms;
                markSeeking();
            }
            pendingSeek = -1;
            if (playing) {
                if (requestFocus()) { mp.start(); registerNoisy(); }
                else { playing = false; unregisterNoisy(); }
            }
        } catch (IllegalStateException e) {
            onLoadFailed(gen);
            return;
        }
        lastTickAt = SystemClock.elapsedRealtime();
        notifyState();
        notifyProgress();
        updateTicker();
    }

    private void onLoadFailed(int gen) {
        if (gen != generation || current == null) return;
        // плеер уже в состоянии Error — позицию у него не спрашиваем, positionMs обновлял тикер
        preparing = prepared = seeking = false;
        if (isOnline(current)) {
            if (pendingSeek >= 0) positionMs = pendingSeek;
            enterFallback();
            notifyState();
            updateTicker();
        } else {
            localFailed();
        }
    }

    /** Онлайн-трек без сети: продолжаем «играть» беззвучно по Track.dur. */
    private void enterFallback() {
        fallback = true;
        preparing = prepared = seeking = false;
        pendingSeek = -1;
        failStreak = 0;
        lastTickAt = SystemClock.elapsedRealtime();
        if (!offlineNotified) { offlineNotified = true; issue(MSG_OFFLINE); }
    }

    /** Локальный файл недоступен: сообщить и перейти к следующему; если не играет вся очередь — стоп.
     *  Пропуск за конец очереди без повтора — это её конец: первый трек встаёт на паузу
     *  (иначе [A, сломанный B] крутил бы A по кругу при выключенном повторе). */
    private void localFailed() {
        failStreak++;
        if (failStreak == 1) issue(MSG_FILE);
        if (failStreak >= queue.size()) { stop(); return; }
        boolean wrapped = queue.advance(false) == PlayQueue.Advance.WRAPPED;
        load(playing && !(wrapped && queue.getRepeat() == PlayQueue.REPEAT_OFF));
    }

    private void onCompleted() {
        if (current == null) return;
        if (sleepAtEnd) {                      // «до конца трека»: следующий трек на паузе
            sleepAtEnd = false;
            pausedByFocus = false;
            queue.advance(false);
            load(false);
            return;
        }
        switch (queue.advance(true)) {
            case SAME:
                positionMs = 0;
                if (prepared && !fallback && mp != null) {
                    try { mp.seekTo(0); markSeeking(); if (playing) mp.start(); }
                    catch (IllegalStateException e) { load(playing); return; }
                }
                lastTickAt = SystemClock.elapsedRealtime();
                notifyProgress();
                notifySeek();
                updateTicker();
                break;
            case ENDED:
                pausedByFocus = false;
                load(false);
                break;
            default:
                load(playing);                 // доигрывает только играющий трек, так что это «дальше играть»
        }
    }

    private static boolean isOnline(Track t) { return t.url != null && t.url.startsWith("http"); }

    private static boolean isReadableLocal(Track t) {
        if (t.url == null || t.url.isEmpty()) return false;
        if (t.url.startsWith("content:")) return true;
        String path = t.url.startsWith("file:") ? Uri.parse(t.url).getPath() : t.url;
        if (path == null) return false;
        File f = new File(path);
        return f.isFile() && f.canRead();
    }

    // ---------- тикер ----------
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tickerPosted = false;
            tick();
        }
    };

    private void updateTicker() {
        if (current != null && playing) {
            if (!tickerPosted) { tickerPosted = true; handler.postDelayed(ticker, TICK_MS); }
        } else {
            handler.removeCallbacks(ticker);
            tickerPosted = false;
        }
    }

    private void tick() {
        if (current == null || !playing) return;
        long now = SystemClock.elapsedRealtime();
        if (fallback) positionMs += (int) (now - lastTickAt);
        lastTickAt = now;
        if (sleepDeadline > 0 && now >= sleepDeadline) {
            sleepDeadline = -1;
            pause();
            return;
        }
        if (fallback && positionMs >= durationMs) {
            positionMs = durationMs;
            onCompleted();
            return;
        }
        notifyProgress();
        updateTicker();
    }

    // ---------- аудиофокус ----------
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                if (ducked) { ducked = false; applyVolume(); }
                if (pausedByFocus) { pausedByFocus = false; play(); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                hasFocus = false;
                if (playing) { pausedByFocus = true; pauseOutput(); }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                ducked = true;
                applyVolume();
                break;
            default:
                break;
        }
    };

    private boolean requestFocus() {
        if (hasFocus) return true;
        if (audio == null) { hasFocus = true; return true; }
        int r;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusListener, handler)
                        .build();
            }
            r = audio.requestAudioFocus((AudioFocusRequest) focusRequest);
        } else {
            r = requestFocusLegacy();
        }
        focusRequested = true;
        hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    @SuppressWarnings("deprecation")
    private int requestFocusLegacy() {
        return audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (focusRequested && audio != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) audio.abandonAudioFocusRequest((AudioFocusRequest) focusRequest);
            } else {
                audio.abandonAudioFocus(focusListener);
            }
        }
        focusRequested = false;
        hasFocus = false;
        if (ducked) { ducked = false; applyVolume(); }
    }

    private void applyVolume() {
        if (mp == null || fallback) return;   // в имитации плеер мог остаться в состоянии Error
        float v = ducked ? 0.25f : 1f;
        try { mp.setVolume(v, v); } catch (IllegalStateException ignored) {}
    }

    // ---------- наушники отключены ----------
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
        }
    };

    private void registerNoisy() {
        if (noisyRegistered) return;
        ContextCompat.registerReceiver(app, noisyReceiver,
                new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED);
        noisyRegistered = true;
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        noisyRegistered = false;
        try { app.unregisterReceiver(noisyReceiver); } catch (IllegalArgumentException ignored) {}
    }
}
