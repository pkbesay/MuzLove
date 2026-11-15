package com.example.muzlove;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/** Шапка экрана плейлиста (экран 5): обложка, название, «N треков · M мин», «Слушать»,
 *  «Перемешать», меню своего плейлиста и пустое состояние. Сам список треков — общий RecyclerView. */
final class PlaylistScreen {

    private final MainActivity a;
    private final View view, cover, buttons, empty, play, shuffle, emptyAdd;
    private final ImageView back, more, shuffleIc;
    private final TextView title, desc, meta, shuffleTx, emptyText;

    PlaylistScreen(MainActivity a, ViewGroup parent) {
        this.a = a;
        view = LayoutInflater.from(a).inflate(R.layout.view_playlist_header, parent, false);
        back = view.findViewById(R.id.plBack);
        more = view.findViewById(R.id.plMore);
        cover = view.findViewById(R.id.plCover);
        title = view.findViewById(R.id.plTitle);
        desc = view.findViewById(R.id.plDesc);
        meta = view.findViewById(R.id.plMeta);
        buttons = view.findViewById(R.id.plButtons);
        play = view.findViewById(R.id.plPlay);
        shuffle = view.findViewById(R.id.plShuffle);
        shuffleIc = view.findViewById(R.id.plShuffleIc);
        shuffleTx = view.findViewById(R.id.plShuffleTx);
        empty = view.findViewById(R.id.plEmpty);
        emptyText = view.findViewById(R.id.plEmptyText);
        emptyAdd = view.findViewById(R.id.plEmptyAdd);
        back.setOnClickListener(v -> a.closePlaylist());
    }

    View view() { return view; }

    /** Перерисовка под плейлист p (tracks — его доступные треки по порядку). */
    void render(Playlist p, List<Track> tracks, boolean custom, boolean light, int cText, int cMuted) {
        float d = a.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{p.c1, p.c2});
        g.setCornerRadius(16 * d);
        cover.setBackground(g);

        String name = Playlists.name(p.name);
        title.setText(name);
        title.setTextColor(cText);
        // у встроенных — их описание, у своих — только посчитанное число треков
        boolean showDesc = !custom && !TextUtils.isEmpty(p.desc);
        desc.setVisibility(showDesc ? View.VISIBLE : View.GONE);
        desc.setText(showDesc ? p.desc : null);
        desc.setTextColor(cMuted);
        long sec = 0;
        for (Track t : tracks) sec += Math.max(0, t.dur);
        meta.setText(Texts.playlistMeta(tracks.size(), sec));
        meta.setTextColor(cMuted);

        back.setColorFilter(light ? 0xFF000000 : cText);
        back.setBackground(ListViews.ripple(light, false));
        more.setVisibility(custom ? View.VISIBLE : View.GONE);
        more.setColorFilter(light ? 0xFF000000 : cText);
        more.setBackground(ListViews.ripple(light, false));
        more.setOnClickListener(v -> a.lists().showPlaylistMenu(p));

        boolean isEmpty = tracks.isEmpty();
        buttons.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyText.setTextColor(cMuted);
        emptyAdd.setVisibility(custom ? View.VISIBLE : View.GONE);
        emptyAdd.setForeground(ListViews.rippleRounded(true, 16 * d));
        emptyAdd.setOnClickListener(v -> a.lists().editTracks(p));

        play.setForeground(ListViews.rippleRounded(true, 16 * d));
        play.setOnClickListener(v -> a.startPlaylist(p, false));
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(light ? 0xFFF2F1F6 : 0x1FFFFFFF);
        sb.setCornerRadius(16 * d);
        shuffle.setBackground(sb);
        shuffle.setForeground(ListViews.rippleRounded(!light, 16 * d));
        shuffleIc.setColorFilter(cText);
        shuffleTx.setTextColor(cText);
        shuffle.setOnClickListener(v -> a.startPlaylist(p, true));
    }
}
