package com.example.muzlove;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Шторки и диалоги списков: меню трека, меню своего плейлиста, выбор треков, создание,
 *  переименование и удаление плейлиста, удаление загруженного трека. Цвета — по теме приложения.
 *  Создаётся в MainActivity.onCreate, dismissAll() — в onDestroy. Только главный поток. */
final class ListSheets {

    private final MainActivity a;
    private final PlayerEngine engine;
    private final AppState state;
    private final List<Sheet> openSheets = new ArrayList<>();
    private final AppDialogs dialogs;

    ListSheets(MainActivity a) {
        this.a = a;
        this.engine = a.engine();
        this.state = a.appState();
        this.dialogs = a.dialogs();
    }

    void dismissAll() {
        for (Sheet s : new ArrayList<>(openSheets)) s.dismiss();
        openSheets.clear();
    }

    private boolean dark() { return !a.isLightTheme(); }

    private boolean gone() { return a.isFinishing() || a.isDestroyed(); }

    private Sheet sheet(boolean dark, CharSequence title) {
        Sheet s = Sheet.create(a, dark, title);
        openSheets.add(s);
        s.onDismiss(() -> openSheets.remove(s));
        return s;
    }

    // ======================= меню трека =======================
    /** Меню трека из списка. inPlaylist — свой плейлист, открытый на экране (даёт «Убрать из плейлиста»). */
    void showTrackMenu(Track t, Playlist inPlaylist) {
        if (t == null || gone()) return;
        boolean dark = dark();
        Sheet s = sheet(dark, null);
        s.addTrackHeader(t);
        boolean liked = state.isLiked(t.id);
        s.addRow(R.drawable.ic_heart, liked ? "Убрать из любимых" : "В любимые", null, false, () -> {
            s.dismiss();
            a.toggleLike(t.id);
            toast(liked ? "Убрано из любимых" : "Добавлено в любимые");
        });
        Track cur = engine.current();
        boolean isCurrent = cur != null && cur.id == t.id;
        if (!isCurrent) {
            s.addRow(R.drawable.ic_next, "Слушать следующим", null, false, () -> {
                s.dismiss();
                boolean started = engine.isStarted();     // ничего не играло — трек просто включится
                engine.playNext(t.id);
                if (started) toast("Сыграет следующим");
            });
        }
        s.addRow(R.drawable.ic_queue_add, "Добавить в очередь", null, false, () -> {
            s.dismiss();
            boolean started = engine.isStarted();
            boolean added = engine.addToQueue(t.id);
            if (started) toast(added ? "Добавлено в очередь" : "Уже в очереди");
        });
        s.addRow(R.drawable.ic_plus, "Добавить в плейлист", null, false, () -> {
            s.dismiss();
            a.playerSheets().showAddToPlaylist(t, dark);
        });
        s.addRow(R.drawable.ic_upload, "Поделиться", null, false, () -> {
            s.dismiss();
            a.playerSheets().showShare(t, dark);
        });
        s.addRow(R.drawable.ic_info, "О треке", null, false, () -> {
            s.dismiss();
            a.playerSheets().showAbout(t, dark);
        });
        if (inPlaylist != null && state.customPlaylists().contains(inPlaylist)) {
            s.addRow(R.drawable.ic_close, "Убрать из плейлиста", null, false, () -> {
                s.dismiss();
                removeFromPlaylist(inPlaylist, t);
            });
        }
        if (t.id >= 1000 && state.find(t.id) != null) {
            s.addRow(R.drawable.ic_trash, "Удалить трек", null, false, () -> {
                s.dismiss();
                dialogs.confirm(dark, "Удалить трек?",
                        "«" + t.title + "» будет удалён с устройства, из любимых и из всех плейлистов.",
                        "Удалить", () -> a.deleteUploadedTrack(t.id));
            });
        }
        s.show();
    }

    private void removeFromPlaylist(Playlist p, Track t) {
        p.ids = Playlists.without(p.ids, t.id);
        p.desc = Texts.tracks(p.ids.length);
        state.savePlaylists();
        a.onPlaylistsChanged();
        toast("Убрано из плейлиста");
    }

    // ======================= свой плейлист =======================
    /** «Ещё» своего плейлиста: добавить треки, переименовать, удалить. */
    void showPlaylistMenu(Playlist p) {
        if (p == null || gone() || !state.customPlaylists().contains(p)) return;
        Sheet s = sheet(dark(), Playlists.name(p.name));
        s.setSubtitle(Texts.tracks(a.playlistTracks(p).size()));
        s.addRow(R.drawable.ic_plus, "Добавить треки", null, false, () -> { s.dismiss(); editTracks(p); });
        s.addRow(R.drawable.ic_edit, "Переименовать", null, false, () -> { s.dismiss(); rename(p); });
        s.addRow(R.drawable.ic_trash, "Удалить плейлист", null, false, () -> { s.dismiss(); confirmDelete(p); });
        s.show();
    }

    /** «Добавить треки»: все треки с отметками, уже добавленные отмечены. */
    void editTracks(Playlist p) {
        if (p == null || !state.customPlaylists().contains(p)) return;
        List<Integer> initial = new ArrayList<>();
        for (Track t : a.playlistTracks(p)) initial.add(t.id);
        int[] before = Playlists.toArray(initial);
        showTrackPicker(dark(), "Добавить треки", initial, selected -> {
            if (!state.customPlaylists().contains(p)) return;   // удалили, пока шторка была открыта
            p.ids = Playlists.merge(before, selected);
            p.desc = Texts.tracks(p.ids.length);
            state.savePlaylists();
            a.onPlaylistsChanged();
        });
    }

    private void rename(Playlist p) {
        dialogs.input(dark(), "Переименовать плейлист", p.name, "Название плейлиста", "Сохранить", false, Playlists.MAX_NAME, name -> {
            if (!state.customPlaylists().contains(p)) return;
            p.name = name;
            state.savePlaylists();
            a.onPlaylistsChanged();
        });
    }

    private void confirmDelete(Playlist p) {
        dialogs.confirm(dark(), "Удалить плейлист?",
                "«" + Playlists.name(p.name) + "» исчезнет из медиатеки. Сами треки останутся в приложении.",
                "Удалить", () -> {
                    if (!state.customPlaylists().remove(p)) return;
                    state.savePlaylists();
                    a.onPlaylistsChanged();         // открытый экран плейлиста закроется сам
                    toast("Плейлист удалён");
                });
    }

    // ======================= создание =======================
    /** Новый плейлист: название, затем выбор треков (отмечены любимые). forTrack != null — плейлист
     *  сразу создаётся с этим треком (из выбора плейлиста для трека), без выбора треков. */
    void createPlaylist(Track forTrack, boolean dark) {
        dialogs.input(dark, "Новый плейлист", null, "Мой плейлист", "Создать", true, Playlists.MAX_NAME, raw -> {
            String name = raw.isEmpty() ? "Мой плейлист" : raw;
            if (forTrack != null) { finishCreate(name, new int[]{forTrack.id}); return; }
            List<Integer> liked = new ArrayList<>();
            for (Track t : state.allTracks()) if (state.isLiked(t.id)) liked.add(t.id);
            showTrackPicker(dark, "Добавить треки", liked,
                    selected -> finishCreate(name, Playlists.toArray(selected)));
        });
    }

    private void finishCreate(String name, int[] ids) {
        String[] g = Playlists.gradient(state.customPlaylists().size());
        Playlist p = new Playlist(name, Texts.tracks(ids.length), g[0], g[1], ids);
        state.customPlaylists().add(p);
        state.savePlaylists();
        a.onPlaylistsChanged();
        a.openPlaylist(p);
        toast("Плейлист создан");
    }

    // ======================= выбор треков =======================
    /** Все треки с отметками и закреплённой кнопкой «Готово». Закрытие без «Готово» ничего не меняет.
     *  selected в onDone: сначала изначально отмеченные (в их порядке), затем новые в порядке нажатий. */
    private void showTrackPicker(boolean dark, String title, List<Integer> preChecked, Consumer<List<Integer>> onDone) {
        if (gone()) return;
        Sheet s = sheet(dark, title);
        Set<Integer> sel = new LinkedHashSet<>(preChecked);
        Runnable subtitle = () -> s.setSubtitle(sel.isEmpty() ? "Ничего не выбрано" : "Выбрано: " + Texts.tracks(sel.size()));
        subtitle.run();
        List<Track> all = state.allTracks();
        for (Track t : all) {
            View[] mark = new View[1];
            View row = pickRow(s, t, mark);
            setPicked(s, row, mark[0], t, sel.contains(t.id));
            row.setOnClickListener(v -> {
                boolean on = !sel.contains(t.id);
                if (on) sel.add(t.id); else sel.remove(t.id);
                setPicked(s, row, mark[0], t, on);
                subtitle.run();
            });
            s.content.addView(row, Sheet.matchWrap());
        }
        if (all.isEmpty()) s.addCaption("Треков пока нет — загрузите свою музыку в медиатеке.");

        TextView done = new TextView(s.ctx);
        done.setText("Готово");
        done.setTextColor(Color.WHITE);
        done.setTextSize(16);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundResource(R.drawable.btn_accent);
        done.setForeground(ListViews.rippleRounded(true, s.dp(16)));
        done.setClickable(true);
        done.setFocusable(true);
        done.setOnClickListener(v -> {
            List<Integer> out = new ArrayList<>(sel);
            s.dismiss();
            onDone.accept(out);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.dp(50));
        lp.setMargins(s.dp(20), s.dp(8), s.dp(20), s.dp(16));
        s.addFooter(done, lp);
        s.show();
    }

    private View pickRow(Sheet s, Track t, View[] markOut) {
        LinearLayout row = new LinearLayout(s.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(s.dp(60));
        row.setPadding(s.dp(20), s.dp(6), s.dp(20), s.dp(6));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(s.pressable(false));

        View cover = new View(s.ctx);
        cover.setBackground(Sheet.cover(t, s.dp(9)));
        row.addView(cover, new LinearLayout.LayoutParams(s.dp(44), s.dp(44)));

        LinearLayout texts = new LinearLayout(s.ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(s.dp(14), 0, s.dp(12), 0);
        TextView title = new TextView(s.ctx);
        title.setText(t.title);
        title.setTextColor(s.text);
        title.setTextSize(15);
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

        FrameLayout mark = new FrameLayout(s.ctx);
        ImageView ck = new ImageView(s.ctx);
        ck.setImageResource(R.drawable.ic_check);
        ck.setColorFilter(s.onAccent);
        ck.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mark.addView(ck, new FrameLayout.LayoutParams(s.dp(16), s.dp(16), Gravity.CENTER));
        row.addView(mark, new LinearLayout.LayoutParams(s.dp(24), s.dp(24)));
        markOut[0] = mark;
        return row;
    }

    /** Отметка: залитый кружок с галочкой или пустой кружок. */
    private static void setPicked(Sheet s, View row, View mark, Track t, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (on) g.setColor(s.accent);
        else g.setStroke(s.dp(2), Sheet.withAlpha(s.muted, 0x99));
        mark.setBackground(g);
        ((ViewGroup) mark).getChildAt(0).setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        row.setContentDescription(t.title + ", " + t.artist + (on ? ", выбран" : ", не выбран"));
    }

    private void toast(String s) { Toast.makeText(a, s, Toast.LENGTH_SHORT).show(); }
}
