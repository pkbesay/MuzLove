package com.example.muzlove;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRouter2;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Шторки и диалоги плеера: очередь, «Поделиться», «Ещё», плейлисты, вывод звука, таймер сна,
 *  «О треке», эквалайзер. Создаётся в MainActivity.onCreate (регистрирует ActivityResultLauncher).
 *  Каждая шторка про трек получает Track и флаг dark и работает, даже когда ничего не играет.
 *  Только главный поток. */
final class PlayerSheets {

    private static final String NO_APP = "Нет подходящего приложения";
    private static final int[] SLEEP_MINUTES = {5, 15, 30, 45, 60};

    // типы устройств вывода выше minSdk (значения констант AudioDeviceInfo)
    private static final int TYPE_USB_HEADSET = 22, TYPE_HEARING_AID = 23, TYPE_BLE_HEADSET = 26,
            TYPE_BLE_SPEAKER = 27, TYPE_HDMI_EARC = 29, TYPE_BLE_BROADCAST = 30, TYPE_DOCK_ANALOG = 31;

    /** Последний выбранный вариант таймера сна (живёт вместе с процессом, как и PlayerEngine). */
    private static int sleepChoice = 0;

    private final MainActivity a;
    private final PlayerEngine engine;
    private final AppState state;
    private final List<Sheet> openSheets = new ArrayList<>();
    private final ActivityResultLauncher<Intent> effectPanel;

    PlayerSheets(MainActivity a) {
        this.a = a;
        this.engine = a.engine();
        this.state = a.appState();
        // системная панель эквалайзера; результат не нужен
        effectPanel = a.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {});
    }

    /** Закрыть всё открытое (MainActivity.onDestroy): слушатели движка снимаются в onDismiss. */
    void dismissAll() {
        for (Sheet s : new ArrayList<>(openSheets)) s.dismiss();
        openSheets.clear();
    }

    private Sheet sheet(boolean dark, CharSequence title) {
        Sheet s = Sheet.create(a, dark, title);
        openSheets.add(s);
        s.onDismiss(() -> openSheets.remove(s));
        return s;
    }

    // ======================= очередь =======================
    /** «Очередь»: треки в порядке воспроизведения, обновляется сама, пока открыта. */
    void showQueue(boolean dark) {
        Sheet s = sheet(dark, "Очередь");
        LinearLayout list = new LinearLayout(s.ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        s.content.addView(list, Sheet.matchWrap());

        final View[] current = {fillQueue(s, list)};
        PlayerEngine.Listener l = new PlayerEngine.Listener() {
            @Override public void onTrackChanged() { current[0] = fillQueue(s, list); }
            @Override public void onQueueChanged() { current[0] = fillQueue(s, list); }
        };
        s.onShow(() -> { engine.addListener(l); current[0] = fillQueue(s, list); });
        s.onDismiss(() -> engine.removeListener(l));

        // при открытии — прокрутить к текущему треку (один раз, после первой раскладки)
        s.scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l1, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (b - t <= 0) return;
                s.scroll.removeOnLayoutChangeListener(this);
                View row = current[0];
                if (row != null && row.getParent() == list) {
                    int y = list.getTop() + row.getTop() - s.dp(56);
                    s.scroll.post(() -> s.scroll.scrollTo(0, Math.max(0, y)));
                }
            }
        });
        s.show();
    }

    /** Перестраивает список очереди; возвращает строку текущего трека (или null). */
    private View fillQueue(Sheet s, LinearLayout list) {
        list.removeAllViews();
        List<Integer> ids = engine.queueIds();
        int cur = engine.isStarted() ? engine.queueIndex() : -1;
        int n = 0;
        View curRow = null;
        for (int i = 0; i < ids.size(); i++) {
            Track t = state.find(ids.get(i));
            if (t == null) continue;                 // удалённая загрузка — движок выкинет её сам
            n++;
            View row = queueRow(s, t, i, i == cur);
            if (i == cur) curRow = row;
            list.addView(row, Sheet.matchWrap());
        }
        s.setSubtitle(n > 0 ? Texts.tracks(n) : null);
        if (n == 0) {
            TextView empty = new TextView(s.ctx);
            empty.setText("Очередь пуста. Включите трек или плейлист — он появится здесь.");
            empty.setTextColor(s.muted);
            empty.setTextSize(14);
            empty.setPadding(s.dp(20), s.dp(8), s.dp(20), s.dp(8));
            list.addView(empty, Sheet.matchWrap());
        }
        return curRow;
    }

    private View queueRow(Sheet s, Track t, int index, boolean isCurrent) {
        LinearLayout row = new LinearLayout(s.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(s.dp(60));
        row.setPadding(s.dp(20), s.dp(6), s.dp(8), s.dp(6));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(s.pressable(false));
        row.setOnClickListener(v -> engine.jumpTo(index));

        View cover = new View(s.ctx);
        cover.setBackground(Sheet.cover(t, s.dp(9)));
        row.addView(cover, new LinearLayout.LayoutParams(s.dp(44), s.dp(44)));

        LinearLayout texts = new LinearLayout(s.ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(s.dp(14), 0, s.dp(8), 0);
        TextView title = new TextView(s.ctx);
        title.setText(t.title);
        title.setTextColor(isCurrent ? s.accent : s.text);
        title.setTextSize(15);
        if (isCurrent) title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView artist = new TextView(s.ctx);
        artist.setText(t.artist);
        artist.setTextColor(s.muted);
        artist.setTextSize(13);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(artist);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView side = new ImageView(s.ctx);
        int pad = s.dp(10);
        side.setPadding(pad, pad, pad, pad);
        if (isCurrent) {
            side.setImageResource(R.drawable.ic_eq);
            side.setColorFilter(s.accent);
            side.setContentDescription("Сейчас играет");
        } else {
            side.setImageResource(R.drawable.ic_close);
            side.setColorFilter(s.muted);
            side.setContentDescription("Убрать из очереди");
            side.setBackground(s.pressable(true));
            side.setOnClickListener(v -> engine.removeAt(index));
        }
        row.addView(side, new LinearLayout.LayoutParams(s.dp(40), s.dp(40)));
        row.setContentDescription(t.title + ", " + t.artist + (isCurrent ? ", сейчас играет" : ""));
        return row;
    }

    // ======================= поделиться =======================
    /** «Поделиться треком»: Facebook, «Ссылка» (буфер обмена), «Ещё» (системный выбор приложения). */
    void showShare(Track t, boolean dark) {
        if (t == null) return;
        Sheet s = sheet(dark, "Поделиться треком");
        s.title.setGravity(Gravity.CENTER);
        LinearLayout row = new LinearLayout(s.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, s.dp(12), 0, s.dp(4));
        shareOption(s, row, R.drawable.ic_social, "Facebook", () -> shareFacebook(t));
        shareOption(s, row, R.drawable.ic_link, "Ссылка", () -> copyLink(t));
        shareOption(s, row, R.drawable.ic_more, "Ещё", () -> shareSystem(t));
        s.content.addView(row, Sheet.matchWrap());
        s.show();
    }

    private void shareOption(Sheet s, LinearLayout row, int icon, String label, Runnable action) {
        LinearLayout col = new LinearLayout(s.ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(s.dp(6), s.dp(8), s.dp(6), s.dp(8));
        col.setClickable(true);
        col.setFocusable(true);
        col.setBackground(s.pressable(true));
        col.setContentDescription(label);
        ImageView iv = new ImageView(s.ctx);
        iv.setImageResource(icon);
        iv.setColorFilter(s.text);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(s.dark ? 0x33FFFFFF : 0x14000000);
        iv.setBackground(circle);
        int p = s.dp(13);
        iv.setPadding(p, p, p, p);
        col.addView(iv, new LinearLayout.LayoutParams(s.dp(52), s.dp(52)));
        TextView tv = new TextView(s.ctx);
        tv.setText(label);
        tv.setTextColor(s.text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, s.dp(8), 0, 0);
        col.addView(tv);
        col.setOnClickListener(v -> { s.dismiss(); action.run(); });
        row.addView(col, new LinearLayout.LayoutParams(s.dp(88), ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** «Название» — Исполнитель, ссылка (для онлайн-трека) и «Слушаю в MUZLOVE». */
    static String shareText(Track t) {
        StringBuilder b = new StringBuilder("«").append(t.title).append("» — ").append(t.artist);
        if (AudioMeta.isOnline(t)) b.append('\n').append(t.url);
        return b.append("\nСлушаю в MUZLOVE").toString();
    }

    private void copyLink(Track t) {
        boolean online = AudioMeta.isOnline(t);
        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            if (cm == null) throw new IllegalStateException("clipboard");
            cm.setPrimaryClip(ClipData.newPlainText(online ? "Ссылка на трек" : "Трек",
                    online ? t.url : shareText(t)));
        } catch (RuntimeException e) {
            toast("Не удалось скопировать");
            return;
        }
        // Android 13+ сам показывает, что скопировано
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            toast(online ? "Ссылка скопирована" : "Название скопировано");
    }

    private void shareSystem(Track t) {
        Intent send = new Intent(Intent.ACTION_SEND);
        Uri uri = null;
        File f = AudioMeta.localFile(t);
        if (f != null) {
            try { uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f); }
            catch (IllegalArgumentException ignored) {}   // файл вне filesDir — отправим только текст
        }
        if (uri != null) {
            String mime = AudioMeta.read(a, t).mime;
            send.setType(mime != null && mime.startsWith("audio/") ? mime : "audio/*");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(t.title, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            send.setType("text/plain");
        }
        send.putExtra(Intent.EXTRA_TEXT, shareText(t));
        startSafe(Intent.createChooser(send, "Поделиться треком"));
    }

    private void shareFacebook(Track t) {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, shareText(t))
                .setPackage("com.facebook.katana");
        try {
            a.startActivity(i);
            return;
        } catch (ActivityNotFoundException | SecurityException ignored) {}
        if (AudioMeta.isOnline(t)) {
            startSafe(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.facebook.com/sharer/sharer.php?u=" + Uri.encode(t.url))));
        } else {
            toast("Приложение Facebook не установлено");
        }
    }

    // ======================= «Ещё» =======================
    /** Меню трека: поделиться, в плейлист, вывод звука, удалить из очереди, таймер сна, о треке. */
    void showMore(Track t, boolean dark) {
        if (t == null) return;
        Sheet s = sheet(dark, null);
        s.addTrackHeader(t);
        s.addRow(R.drawable.ic_upload, "Поделиться", null, false, () -> { s.dismiss(); showShare(t, dark); });
        s.addRow(R.drawable.ic_plus, "Добавить в плейлист", null, false, () -> { s.dismiss(); showAddToPlaylist(t, dark); });
        s.addRow(R.drawable.ic_speaker, "Слушать на устройстве", null, false, () -> { s.dismiss(); showOutput(dark); });
        if (engine.queueIds().contains(t.id)) {
            s.addRow(R.drawable.ic_trash, "Удалить из очереди", null, false, () -> {
                s.dismiss();                         // раньше, чем движок остановится и плеер закроется
                int i = engine.queueIds().indexOf(t.id);
                if (i < 0) return;
                engine.removeAt(i);
                toast("Удалено из очереди");
            });
        }
        s.addRow(R.drawable.ic_timer, "Таймер сна", sleepState(), false, () -> { s.dismiss(); showSleepTimer(dark); });
        s.addRow(R.drawable.ic_info, "О треке", null, false, () -> { s.dismiss(); showAbout(t, dark); });
        s.show();
    }

    // ======================= плейлисты =======================
    /** Выбор своего плейлиста для трека (встроенные только для чтения и не показываются). */
    void showAddToPlaylist(Track t, boolean dark) {
        if (t == null) return;
        Sheet s = sheet(dark, "Добавить в плейлист");
        s.setSubtitle("«" + t.title + "»");
        s.addRow(R.drawable.ic_add, "Новый плейлист", null, false, () -> { s.dismiss(); a.createPlaylist(t, dark); });
        List<Playlist> custom = state.customPlaylists();
        for (Playlist p : custom) {
            int n = a.playlistTracks(p).size();        // только треки, которые есть в приложении
            s.addRow(R.drawable.ic_playlist, playlistName(p), Texts.tracks(n), contains(p.ids, t.id),
                    () -> { s.dismiss(); addToPlaylist(p, t); });
        }
        if (custom.isEmpty()) s.addCaption("Своих плейлистов пока нет — создайте первый.");
        s.show();
    }

    private void addToPlaylist(Playlist p, Track t) {
        String name = playlistName(p);
        if (contains(p.ids, t.id)) { toast("Уже в плейлисте «" + name + "»"); return; }
        int[] old = p.ids != null ? p.ids : new int[0];
        int[] ids = Arrays.copyOf(old, old.length + 1);
        ids[old.length] = t.id;
        p.ids = ids;
        p.desc = Texts.tracks(ids.length);
        state.savePlaylists();
        a.onPlaylistsChanged();
        toast("Добавлено в «" + name + "»");
    }

    private static String playlistName(Playlist p) { return Playlists.name(p.name); }

    private static boolean contains(int[] ids, int id) { return Playlists.contains(ids, id); }

    // ======================= вывод звука =======================
    /** «Слушать на устройстве»: системный переключатель вывода (API 34+), иначе своя шторка. */
    void showOutput(boolean dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                if (MediaRouter2.getInstance(a).showSystemOutputSwitcher()) return;
            } catch (RuntimeException ignored) {}
        }
        showOutputSheet(dark);
    }

    private void showOutputSheet(boolean dark) {
        Sheet s = sheet(dark, "Устройства вывода");
        AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = new AudioDeviceInfo[0];
        if (am != null) {
            try { devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS); } catch (RuntimeException ignored) {}
        }
        List<Integer> active = activeOutputIds(am);
        List<String> shown = new ArrayList<>();
        for (AudioDeviceInfo d : devices) {
            String name = outputName(d);
            if (name == null || shown.contains(name)) continue;
            shown.add(name);
            s.addRow(outputIcon(d.getType()), name, null, active.contains(d.getId()), null);
        }
        if (shown.isEmpty()) s.addRow(R.drawable.ic_phone, "Динамик телефона", null, false, null);
        s.addCaption("Подключите наушники или колонку — звук перейдёт на них автоматически.");
        s.addRow(R.drawable.ic_settings, "Настройки Bluetooth", null, false, () -> {
            s.dismiss();
            startSafe(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        });
        s.addRow(R.drawable.ic_volume, "Громкость", null, false, () -> { s.dismiss(); showVolume(am); });
        s.show();
    }

    /** id устройств, на которые сейчас идёт музыка (API 33+; ниже — неизвестно). */
    private static List<Integer> activeOutputIds(AudioManager am) {
        List<Integer> out = new ArrayList<>();
        if (am == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return out;
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            for (AudioDeviceInfo d : am.getAudioDevicesForAttributes(attrs)) out.add(d.getId());
        } catch (RuntimeException ignored) {}
        return out;
    }

    /** Русское название выхода или null для внутренних (динамик звонков, телефония и т.п.). */
    private static String outputName(AudioDeviceInfo d) {
        switch (d.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Динамик телефона";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Проводные наушники";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case TYPE_BLE_HEADSET:
            case TYPE_BLE_SPEAKER:
            case TYPE_BLE_BROADCAST: {
                CharSequence pn = d.getProductName();
                String n = pn != null ? pn.toString().trim() : "";
                return n.isEmpty() ? "Bluetooth-устройство" : "Bluetooth «" + n + "»";
            }
            case TYPE_HEARING_AID: return "Слуховой аппарат";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY: return "USB-устройство";
            case TYPE_USB_HEADSET: return "USB-гарнитура";
            case AudioDeviceInfo.TYPE_HDMI:
            case AudioDeviceInfo.TYPE_HDMI_ARC:
            case TYPE_HDMI_EARC: return "HDMI";
            case AudioDeviceInfo.TYPE_DOCK:
            case TYPE_DOCK_ANALOG: return "Док-станция";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
            case AudioDeviceInfo.TYPE_AUX_LINE: return "Линейный выход";
            default: return null;   // динамик звонков, телефония, шина, FM, IP и прочие внутренние
        }
    }

    private static int outputIcon(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return R.drawable.ic_phone;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case TYPE_USB_HEADSET:
            case TYPE_BLE_HEADSET:
            case TYPE_HEARING_AID: return R.drawable.ic_headphones;
            default: return R.drawable.ic_speaker;
        }
    }

    private void showVolume(AudioManager am) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                a.startActivity(new Intent(Settings.Panel.ACTION_VOLUME));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {}
        }
        // нет панели — системный регулятор громкости музыки
        if (am == null) { toast(NO_APP); return; }
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
        } catch (RuntimeException e) {
            toast(NO_APP);
        }
    }

    // ======================= таймер сна =======================
    /** «Выкл», «ещё 12 мин» или «до конца трека». */
    String sleepState() { return Texts.sleepState(engine.sleepRemainingMs(), engine.isSleepAtTrackEnd()); }

    /** «Таймер сна»: выключен / 5–60 минут / до конца трека. Работает и без текущего трека. */
    void showSleepTimer(boolean dark) {
        Sheet s = sheet(dark, "Таймер сна");
        boolean timed = engine.sleepRemainingMs() > 0;
        boolean atEnd = !timed && engine.isSleepAtTrackEnd();
        String st = sleepState();
        s.setSubtitle(timed || atEnd ? "Сейчас: " + st : "Музыка остановится сама в выбранное время");
        s.addRow(0, "Выключен", null, !timed && !atEnd, () -> {
            s.dismiss();
            engine.setSleepTimer(0);
            engine.setSleepAtTrackEnd(false);
            toast("Таймер сна выключен");
        });
        for (int m : SLEEP_MINUTES) {
            boolean on = timed && sleepChoice == m;
            s.addRow(0, Texts.minutes(m), on ? st : null, on, () -> {
                s.dismiss();
                engine.setSleepAtTrackEnd(false);
                engine.setSleepTimer(m);
                sleepChoice = m;
                toast("Музыка выключится через " + Texts.minutesAcc(m));
            });
        }
        s.addRow(0, "До конца трека", null, atEnd, () -> {
            s.dismiss();
            engine.setSleepTimer(0);
            engine.setSleepAtTrackEnd(true);
            sleepChoice = 0;
            toast("Музыка выключится после этого трека");
        });
        s.show();
    }

    // ======================= о треке =======================
    void showAbout(Track t, boolean dark) {
        if (t == null) return;
        Sheet s = sheet(dark, "О треке");
        s.addTrackHeader(t);
        boolean online = AudioMeta.isOnline(t);
        AudioMeta meta = online ? new AudioMeta() : AudioMeta.read(a, t);
        Track cur = engine.current();
        boolean isCurrent = cur != null && cur.id == t.id && !engine.isBuffering();

        long durMs = -1;
        if (meta.durationMs > 0) durMs = meta.durationMs;                 // файл: из метаданных
        else if (isCurrent) durMs = engine.durationMs();                   // загружен: настоящая
        else if (online && t.dur > 0) durMs = t.dur * 1000L;               // каталог

        s.addInfo("Название", t.title);
        s.addInfo("Исполнитель", t.artist);
        s.addInfo("Альбом", t.album);
        s.addInfo("Длительность", durMs > 0 ? Texts.duration(durMs) : "Неизвестно");
        s.addInfo("Источник", online ? "Онлайн" : "Файл на устройстве");
        if (!online) {
            File f = AudioMeta.localFile(t);
            if (f != null) s.addInfo("Размер", Texts.size(f.length()));
            else s.addInfo("Файл", "Недоступен");
            String fmt = Texts.format(meta.mime);
            if (fmt != null) s.addInfo("Формат", fmt);
            if (meta.bitrate > 0) s.addInfo("Битрейт", Math.round(meta.bitrate / 1000f) + " кбит/с");
        }
        s.addInfo("В любимых", state.isLiked(t.id) ? "Да" : "Нет");
        List<String> in = new ArrayList<>();
        for (Playlist p : state.customPlaylists()) if (contains(p.ids, t.id)) in.add("«" + playlistName(p) + "»");
        s.addInfo("В плейлистах", in.isEmpty() ? "Нет" : TextUtils.join(", ", in));
        s.show();
    }

    // ======================= эквалайзер =======================
    /** Эквалайзер текущей сессии; без неё — пояснение и системная панель, если она есть. */
    void showEqualizer(boolean dark) {
        EqualizerController eq = engine.equalizer();
        eq.ensure();
        if (!eq.isAvailable()) { showEqualizerUnavailable(eq, dark); return; }

        Sheet s = sheet(dark, null);

        LinearLayout head = new LinearLayout(s.ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(s.dp(20), 0, s.dp(16), s.dp(8));
        TextView ht = new TextView(s.ctx);
        ht.setText("Эквалайзер");
        ht.setTextColor(s.text);
        ht.setTextSize(18);
        ht.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(ht, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        SwitchCompat sw = new SwitchCompat(s.ctx);
        sw.setChecked(eq.isEnabled());
        sw.setContentDescription("Эквалайзер");
        head.addView(sw);
        s.content.addView(head, Sheet.matchWrap());

        LinearLayout controls = new LinearLayout(s.ctx);
        controls.setOrientation(LinearLayout.VERTICAL);
        s.content.addView(controls, Sheet.matchWrap());

        // пресеты: устройство + «Свой»
        HorizontalScrollView hs = new HorizontalScrollView(s.ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(s.dp(20), s.dp(4), s.dp(20), s.dp(12));
        LinearLayout chips = new LinearLayout(s.ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips);
        controls.addView(hs, Sheet.matchWrap());

        int bands = eq.bandCount();
        int min = eq.minLevel(), max = eq.maxLevel();
        SeekBar[] bars = new SeekBar[bands];
        TextView[] values = new TextView[bands];
        List<TextView> chipViews = new ArrayList<>();
        List<Integer> chipPresets = new ArrayList<>();

        Runnable refresh = () -> {
            int p = eq.preset();
            for (int i = 0; i < chipViews.size(); i++) styleChip(s, chipViews.get(i), chipPresets.get(i) == p);
            for (int b = 0; b < bands; b++) {
                int lv = eq.bandLevel(b);
                bars[b].setProgress(lv - min);
                values[b].setText(Texts.db(lv));
            }
        };

        for (int i = 0; i <= eq.presetCount(); i++) {       // последний — «Свой»
            int preset = i < eq.presetCount() ? i : -1;
            TextView c = new TextView(s.ctx);
            c.setText(preset >= 0 ? eq.presetName(preset) : "Свой");
            c.setTextSize(14);
            c.setGravity(Gravity.CENTER);
            c.setMinHeight(s.dp(36));
            c.setPadding(s.dp(14), s.dp(6), s.dp(14), s.dp(6));
            c.setClickable(true);
            c.setFocusable(true);
            c.setOnClickListener(v -> { eq.usePreset(preset); refresh.run(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = s.dp(8);
            chips.addView(c, lp);
            chipViews.add(c);
            chipPresets.add(preset);
        }

        // полосы
        for (int b = 0; b < bands; b++) {
            final int band = b;
            LinearLayout row = new LinearLayout(s.ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(s.dp(48));
            row.setPadding(s.dp(20), 0, s.dp(20), 0);
            TextView label = new TextView(s.ctx);
            label.setText(Texts.freq(eq.centerFreqHz(b)));
            label.setTextColor(s.muted);
            label.setTextSize(13);
            row.addView(label, new LinearLayout.LayoutParams(s.dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            AppCompatSeekBar bar = new AppCompatSeekBar(s.ctx);
            bar.setMax(max - min);
            bar.setContentDescription("Полоса " + Texts.freq(eq.centerFreqHz(b)));
            row.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = new TextView(s.ctx);
            value.setTextColor(s.text);
            value.setTextSize(13);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(s.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
            bars[b] = bar;
            values[b] = value;
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                boolean tracking;
                @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    int lv = min + progress;
                    eq.setBandLevel(band, lv);       // слышно сразу, пресет -> «Свой»
                    value.setText(Texts.db(eq.bandLevel(band)));
                    for (int i = 0; i < chipViews.size(); i++) styleChip(s, chipViews.get(i), chipPresets.get(i) == -1);
                    if (!tracking) eq.save();        // клавиатура / спецвозможности
                }
                @Override public void onStartTrackingTouch(SeekBar sb) { tracking = true; }
                @Override public void onStopTrackingTouch(SeekBar sb) { tracking = false; eq.save(); }
            });
            controls.addView(row, Sheet.matchWrap());
        }

        View reset = s.row(R.drawable.ic_reset, "Сбросить", null, false, () -> { eq.reset(); refresh.run(); });
        controls.addView(reset, Sheet.matchWrap());

        Runnable applyEnabled = () -> setControlsEnabled(controls, eq.isEnabled());
        sw.setOnCheckedChangeListener((v, on) -> { eq.setEnabled(on); applyEnabled.run(); });
        head.setOnClickListener(v -> sw.toggle());
        refresh.run();
        applyEnabled.run();
        s.show();
    }

    private void showEqualizerUnavailable(EqualizerController eq, boolean dark) {
        Sheet s = sheet(dark, "Эквалайзер");
        s.addCaption(eq.hasSession()
                ? "Эквалайзер недоступен на этом устройстве"
                : "Эквалайзер станет доступен, когда начнёт играть трек");
        Intent panel = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, engine.audioSessionId())
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, a.getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        boolean hasPanel;
        try { hasPanel = panel.resolveActivity(a.getPackageManager()) != null; }
        catch (RuntimeException e) { hasPanel = false; }
        if (hasPanel) {
            s.addRow(R.drawable.ic_eq, "Системный эквалайзер", null, false, () -> {
                s.dismiss();
                try { effectPanel.launch(panel); }
                catch (ActivityNotFoundException | SecurityException e) { toast(NO_APP); }
            });
        }
        s.show();
    }

    private static void styleChip(Sheet s, TextView c, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(s.dp(18));
        g.setColor(selected ? s.accent : s.chip);
        c.setBackground(g);
        c.setTextColor(selected ? s.onAccent : s.text);
        c.setSelected(selected);
    }

    /** Выключенный эквалайзер: все элементы блока недоступны и приглушены. */
    private static void setControlsEnabled(View block, boolean on) {
        setEnabledDeep(block, on);
        block.setAlpha(on ? 1f : 0.4f);
    }

    private static void setEnabledDeep(View v, boolean on) {
        v.setEnabled(on);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) setEnabledDeep(g.getChildAt(i), on);
        }
    }

    // ======================= общее =======================
    /** startActivity для внешнего интента: без подходящего приложения — тост. */
    private void startSafe(Intent i) {
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast(NO_APP);
        }
    }

    private void toast(String s) { Toast.makeText(a, s, Toast.LENGTH_SHORT).show(); }
}
