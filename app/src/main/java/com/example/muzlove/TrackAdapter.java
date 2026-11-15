package com.example.muzlove;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {

    /** Нажатия в строке трека: сама строка, «Ещё» (и долгое нажатие), «Поделиться», «В плейлист». */
    public interface Callbacks {
        void onPlay(Track t);
        void onMore(Track t);
        void onShare(Track t);
        void onAddToPlaylist(Track t);
    }

    private final List<Track> items = new ArrayList<>();
    private final Set<Integer> liked;
    private int currentId = -1;
    private boolean light = false;
    private final Callbacks cb;

    public TrackAdapter(Set<Integer> liked, Callbacks cb) {
        this.liked = liked;
        this.cb = cb;
    }

    public void setData(List<Track> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    /** Копия показанного списка (для очереди воспроизведения). */
    public List<Track> getItems() { return new ArrayList<>(items); }

    /** id показанных треков по порядку. */
    public List<Integer> ids() {
        List<Integer> out = new ArrayList<>(items.size());
        for (Track t : items) out.add(t.id);
        return out;
    }

    public void setCurrent(int id) { currentId = id; notifyDataSetChanged(); }
    public void setLight(boolean l) { light = l; notifyDataSetChanged(); }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Track t = items.get(position);
        boolean active = t.id == currentId;
        boolean isLiked = liked.contains(t.id);

        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        cover.setCornerRadius(dp(h.cover, 9));
        h.cover.setBackground(cover);

        int textPrimary = light ? 0xFF15131F : 0xFFF3F1FA;
        int textMuted = light ? 0xFF6B6880 : 0xFFA09CB8;

        h.title.setText(t.title);
        h.title.setTextColor(active ? t.c1 : textPrimary);
        h.artist.setText(t.artist);
        h.artist.setTextColor(textMuted);

        h.liked.setVisibility(isLiked ? View.VISIBLE : View.GONE);
        h.liked.setColorFilter(t.c1);
        h.eq.setVisibility(active ? View.VISIBLE : View.GONE);
        h.eq.setColorFilter(t.c1);
        h.add.setColorFilter(textMuted);
        h.more.setColorFilter(textMuted);

        // рябь под тему (тема приложения переключается вручную, а не системной темой)
        if (h.rippleLight == null || h.rippleLight != light) {
            h.rippleLight = light;
            h.itemView.setForeground(ripple(true));
            h.share.setBackground(ripple(false));
            h.add.setBackground(ripple(false));
            h.more.setBackground(ripple(false));
        }

        int activeBg = light ? 0x14000000 : 0x10FFFFFF;
        h.itemView.setBackgroundColor(active ? activeBg : 0x00000000);
        h.itemView.setContentDescription(t.title + ", " + t.artist
                + (isLiked ? ", в любимых" : "") + (active ? ", сейчас играет" : ""));
        h.itemView.setOnClickListener(v -> cb.onPlay(t));
        h.itemView.setOnLongClickListener(v -> { cb.onMore(t); return true; });
        h.share.setOnClickListener(v -> cb.onShare(t));
        h.add.setOnClickListener(v -> cb.onAddToPlaylist(t));
        h.more.setOnClickListener(v -> cb.onMore(t));
    }

    @Override public int getItemCount() { return items.size(); }

    /** bounded — рябь строки, иначе круглая рябь кнопки. */
    private RippleDrawable ripple(boolean bounded) {
        ColorStateList c = ColorStateList.valueOf(light ? 0x1F000000 : 0x33FFFFFF);
        return new RippleDrawable(c, null, bounded ? new ColorDrawable(Color.WHITE) : null);
    }

    static class VH extends RecyclerView.ViewHolder {
        View cover; TextView title, artist; ImageView liked, eq, share, add, more;
        Boolean rippleLight;      // для какой темы уже поставлена рябь
        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.rowTitle);
            artist = v.findViewById(R.id.rowArtist);
            liked = v.findViewById(R.id.rowLiked);
            eq = v.findViewById(R.id.rowEq);
            share = v.findViewById(R.id.rowShare);
            add = v.findViewById(R.id.rowAdd);
            more = v.findViewById(R.id.rowMore);
        }
    }

    private static float dp(View v, float val) {
        return val * v.getResources().getDisplayMetrics().density;
    }
}
