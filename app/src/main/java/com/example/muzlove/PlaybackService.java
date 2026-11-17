package com.example.muzlove;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

/** Фоновое воспроизведение: foreground-сервис с медиа-уведомлением и MediaSession
 *  (экран блокировки, быстрые настройки, кнопки гарнитуры). Сам ничего не играет —
 *  только отражает состояние PlayerEngine и передаёт ему команды. */
public class PlaybackService extends Service {

    static final String ACTION_TOGGLE = "com.example.muzlove.action.TOGGLE";
    static final String ACTION_NEXT = "com.example.muzlove.action.NEXT";
    static final String ACTION_PREV = "com.example.muzlove.action.PREV";
    static final String ACTION_DISMISS = "com.example.muzlove.action.DISMISS";
    /** Extra для MainActivity: открыть экран плеера. */
    static final String EXTRA_OPEN_PLAYER = "open_player";

    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 2001;
    // уведомление прошлых версий («Music Island», id 1)
    private static final String LEGACY_CHANNEL_ID = "music_island";
    private static final int LEGACY_NOTIFICATION_ID = 1;
    private static final int ART_PX = 256;
    // чаще ~5 обновлений в секунду система молча отбрасывает (в шторке остался бы старый трек)
    private static final long NOTIFY_INTERVAL_MS = 250;
    private static final long ACTIONS = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;

    /** Экземпляр создан и не останавливается — тогда новый запуск не нужен. */
    private static boolean running;

    /** Запускает сервис (идемпотентно). Никогда не бросает исключений:
     *  на API 31+ запуск из фона может быть запрещён — тогда музыка играет без уведомления. */
    public static void start(Context c) {
        if (running) return;              // работающий сервис сам узнает о смене состояния
        try {
            ContextCompat.startForegroundService(c, new Intent(c, PlaybackService.class));
        } catch (RuntimeException ignored) {}
    }

    /** Убирает уведомления, которые некому обслуживать: уведомление и канал прошлых версий,
     *  а также наше, оставшееся после гибели процесса на паузе (движок пуст — сервис его уже не уберёт). */
    public static void removeStaleNotifications(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            nm.cancel(LEGACY_NOTIFICATION_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm.deleteNotificationChannel(LEGACY_CHANNEL_ID);
            if (!PlayerEngine.get(c).isStarted()) nm.cancel(NOTIFICATION_ID);
        } catch (RuntimeException ignored) {}
    }

    private PlayerEngine engine;
    private NotificationManager nm;
    private MediaSession session;
    private boolean foreground;           // сейчас в состоянии foreground
    private boolean stopping;             // stopSelf принят, ждём onDestroy
    private int lastStartId;

    // что уже отдано в MediaSession
    private int metaTrackId = -1, metaDuration = -1;

    // обложка уведомления: кэш по id трека
    private int artTrackId = -1;
    private Bitmap art;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean refreshPosted;
    private final Runnable refreshTask = () -> { refreshPosted = false; refresh(); };

    private final PlayerEngine.Listener listener = new PlayerEngine.Listener() {
        @Override public void onTrackChanged() { onEngineChanged(); }
        @Override public void onPlayStateChanged() { onEngineChanged(); }
        @Override public void onSeeked() { if (!stopping && engine.isStarted()) updatePlaybackState(); }
    };

    private final MediaSession.Callback sessionCallback = new MediaSession.Callback() {
        @Override public void onPlay() { playFromOutside(); }
        @Override public void onPause() { engine.pause(); }
        @Override public void onSkipToNext() { engine.next(); }
        @Override public void onSkipToPrevious() { engine.prev(); }
        @Override public void onSeekTo(long pos) { engine.seekTo((int) Math.max(0, Math.min(pos, Integer.MAX_VALUE))); }
        @Override public void onStop() { engine.stop(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        engine = PlayerEngine.get(this);
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        removeStaleNotifications(this);
        createSession();
        engine.addListener(listener);
    }

    @SuppressWarnings("deprecation")      // флаги обязательны только на API 24–25, дальше игнорируются
    private void createSession() {
        session = new MediaSession(this, "MuzLove");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(sessionCallback, handler);
        session.setSessionActivity(openPlayerIntent());
        session.setActive(true);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Воспроизведение", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lastStartId = startId;
        String action = intent != null ? intent.getAction() : null;

        // смахнули уведомление (обычный startService): остановить только сервис, трек остаётся на паузе
        if (ACTION_DISMISS.equals(action)) {
            if (!keepForeground()) shutdown();
            return START_NOT_STICKY;
        }

        // запуск через startForegroundService: startForeground обязателен на любом пути и сразу
        Track t = engine.current();
        startForegroundSafe(t != null ? buildNotification(t) : buildPlaceholder());

        if (ACTION_TOGGLE.equals(action)) engine.toggle();
        else if (ACTION_NEXT.equals(action)) engine.next();
        else if (ACTION_PREV.equals(action)) engine.prev();

        refresh();
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // приложение смахнули из недавних: на паузе — всё закрываем, во время музыки — играем дальше
        if (!engine.isPlaying()) {
            if (engine.isStarted()) engine.stop();     // слушатель вызовет shutdown()
            else shutdown();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(refreshTask);
        engine.removeListener(listener);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        cancelNotification();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /** «Играть» с экрана блокировки, гарнитуры, Bluetooth. Сначала foreground: с targetSdk 35+
     *  аудиофокус дают только видимому приложению или foreground-сервису, иначе play() не сработает. */
    private void playFromOutside() {
        Track t = engine.current();
        if (t == null || engine.isPlaying()) return;
        if (stopping) { engine.play(); return; }
        if (!foreground) startForegroundSafe(buildNotification(t));
        engine.play();
        refresh();                        // фокус не дали — refresh вернёт сервис из foreground
    }

    // ---------- состояние ----------
    /** Играет, либо пауза из-за временной потери аудиофокуса (звонок) — тогда скоро продолжим. */
    private boolean keepForeground() { return engine.isPlaying() || engine.isPausedTransiently(); }

    /** Событие движка: MediaSession — сразу, уведомление — не чаще раза в NOTIFY_INTERVAL_MS
     *  (с последним состоянием на момент перерисовки). Остановку обрабатываем сразу. */
    private void onEngineChanged() {
        if (stopping) return;
        Track t = engine.current();
        if (t == null) { refresh(); return; }
        updateMetadata(t);
        updatePlaybackState();
        if (!refreshPosted) {
            refreshPosted = true;
            handler.postDelayed(refreshTask, NOTIFY_INTERVAL_MS);
        }
    }

    /** Перерисовывает уведомление и MediaSession по движку. */
    private void refresh() {
        handler.removeCallbacks(refreshTask);
        refreshPosted = false;
        if (stopping) return;
        Track t = engine.current();
        if (t == null) { shutdown(); return; }
        updateMetadata(t);
        updatePlaybackState();
        Notification n = buildNotification(t);
        if (keepForeground()) {
            if (foreground) notifySafe(n); else startForegroundSafe(n);
        } else {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_DETACH);   // уведомление остаётся, его можно смахнуть
                foreground = false;
            }
            notifySafe(n);
        }
    }

    /** Движок пуст: убрать уведомление и остановиться. Если уже пришёл новый запуск — дождаться его. */
    private void shutdown() {
        if (stopping) return;
        handler.removeCallbacks(refreshTask);
        refreshPosted = false;
        if (session != null) {
            session.setPlaybackState(new PlaybackState.Builder().setActions(ACTIONS)
                    .setState(PlaybackState.STATE_STOPPED, 0, 0f, SystemClock.elapsedRealtime()).build());
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        foreground = false;
        cancelNotification();
        // stopSelfResult остановит, только если после lastStartId не было новых запусков:
        // иначе ждущий onStartCommand ещё должен вызвать startForeground (иначе система уронит процесс)
        if (stopSelfResult(lastStartId)) { stopping = true; running = false; }
    }

    /** Единственное место вызова startForeground; при запрете — обычное уведомление. */
    private void startForegroundSafe(Notification n) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
            foreground = true;
        } catch (RuntimeException e) {     // ForegroundServiceStartNotAllowedException и т.п.
            foreground = false;
            notifySafe(n);
        }
    }

    private void notifySafe(Notification n) {
        if (nm == null) return;
        try { nm.notify(NOTIFICATION_ID, n); } catch (RuntimeException ignored) {}
    }

    private void cancelNotification() {
        if (nm == null) return;
        try { nm.cancel(NOTIFICATION_ID); } catch (RuntimeException ignored) {}
    }

    // ---------- MediaSession ----------
    private void updateMetadata(Track t) {
        int dur = engine.durationMs();
        if (session == null || (t.id == metaTrackId && dur == metaDuration)) return;
        metaTrackId = t.id;
        metaDuration = dur;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title(t))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, nz(t.artist))
                .putString(MediaMetadata.METADATA_KEY_ALBUM, nz(t.album))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artFor(t))
                .build());
    }

    /** Позицию между обновлениями система экстраполирует сама — на каждый тик не вызываем. */
    private void updatePlaybackState() {
        if (session == null) return;
        int state = engine.isBuffering() ? PlaybackState.STATE_BUFFERING
                : engine.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(state, engine.positionMs(), state == PlaybackState.STATE_PLAYING ? 1f : 0f,
                        SystemClock.elapsedRealtime())
                .build());
    }

    // ---------- уведомление ----------
    private Notification.Builder newBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new Notification.Builder(this, CHANNEL_ID);
        return legacyBuilder();
    }

    @SuppressWarnings("deprecation")      // API 24–25: каналов ещё нет
    private Notification.Builder legacyBuilder() {
        return new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
    }

    private Notification buildNotification(Track t) {
        boolean playing = engine.isPlaying();
        Notification.Builder b = newBuilder()
                .setSmallIcon(R.drawable.ic_notes)
                .setLargeIcon(artFor(t))
                .setContentTitle(title(t))
                .setContentText(nz(t.artist))
                .setContentIntent(openPlayerIntent())
                .setDeleteIntent(dismissIntent())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setOngoing(keepForeground())
                .setColor(0xFF8B5CF6)
                // подписи — как у тех же кнопок в приложении
                .addAction(action(R.drawable.ic_prev, "Предыдущий трек", ACTION_PREV, 1))
                .addAction(playing ? action(R.drawable.ic_pause, "Пауза", ACTION_TOGGLE, 2)
                                   : action(R.drawable.ic_play, "Воспроизвести", ACTION_TOGGLE, 2))
                .addAction(action(R.drawable.ic_next, "Следующий трек", ACTION_NEXT, 3));
        if (t.album != null && !t.album.isEmpty()) b.setSubText(t.album);
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (session != null) style.setMediaSession(session.getSessionToken());
        b.setStyle(style);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    /** Для startForeground, когда трека уже нет (устаревшая кнопка после гибели процесса). */
    private Notification buildPlaceholder() {
        Notification.Builder b = newBuilder()
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle("MUZLOVE")
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private Notification.Action action(int icon, String label, String act, int requestCode) {
        Intent i = new Intent(this, PlaybackService.class).setAction(act);
        int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(this, requestCode, i, fl)
                : PendingIntent.getService(this, requestCode, i, fl);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    /** Смахивание: обычный запуск (не foreground), чтобы не показывать уведомление заново. */
    private PendingIntent dismissIntent() {
        Intent i = new Intent(this, PlaybackService.class).setAction(ACTION_DISMISS);
        return PendingIntent.getService(this, 4, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent openPlayerIntent() {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_OPEN_PLAYER, true);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Обложка — градиент трека c1 → c2 из левого верхнего угла в правый нижний, как в приложении. */
    private Bitmap artFor(Track t) {
        if (art != null && artTrackId == t.id) return art;
        Bitmap bmp = Bitmap.createBitmap(ART_PX, ART_PX, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, ART_PX, ART_PX, t.c1, t.c2, Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, ART_PX, ART_PX, p);
        art = bmp;
        artTrackId = t.id;
        return bmp;
    }

    private static String title(Track t) {
        return t.title != null && !t.title.isEmpty() ? t.title : "Без названия";
    }

    private static String nz(String s) { return s != null ? s : ""; }
}
