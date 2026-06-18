package com.example.muzlove;

import android.graphics.drawable.GradientDrawable;
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

    public interface OnClick { void onClick(Track t); }

    private final List<Track> items = new ArrayList<>();
    private final Set<Integer> liked;
    private int currentId = -1;
    private boolean light = false;
    private final OnClick onClick;

    public TrackAdapter(Set<Integer> liked, OnClick onClick) {
        this.liked = liked;
        this.onClick = onClick;
    }

    public void setData(List<Track> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    public void setCurrent(int id) { currentId = id; notifyDataSetChanged(); }
    public void setLight(boolean l) { light = l; notifyDataSetChanged(); }
    public void refreshLikes() { notifyDataSetChanged(); }

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

        GradientDrawable cover = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{t.c1, t.c2});
        cover.setCornerRadius(dp(h.cover, 9));
        h.cover.setBackground(cover);

        int textPrimary = light ? 0xFF15131F : 0xFFF3F1FA;
        int textMuted = light ? 0xFF6B6880 : 0xFFA09CB8;

        h.title.setText(t.title);
        h.title.setTextColor(active ? t.c1 : textPrimary);
        h.artist.setText(t.artist + " · " + t.album);
        h.artist.setTextColor(textMuted);

        boolean isLiked = liked.contains(t.id);
        h.heart.setVisibility(isLiked ? View.VISIBLE : View.GONE);
        h.heart.setColorFilter(t.c1);

        h.dur.setText(fmt(t.dur));
        h.dur.setTextColor(textMuted);
        h.dur.setVisibility(active ? View.GONE : View.VISIBLE);
        h.eq.setVisibility(active ? View.VISIBLE : View.GONE);
        h.eq.setColorFilter(t.c1);

        int activeBg = light ? 0x14000000 : 0x10FFFFFF;
        h.itemView.setBackgroundColor(active ? activeBg : 0x00000000);
        h.itemView.setOnClickListener(v -> onClick.onClick(t));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        View cover; TextView title, artist, dur; ImageView heart, eq;
        VH(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.rowTitle);
            artist = v.findViewById(R.id.rowArtist);
            dur = v.findViewById(R.id.rowDur);
            heart = v.findViewById(R.id.rowHeart);
            eq = v.findViewById(R.id.rowEq);
        }
    }

    private static String fmt(int s) {
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static float dp(View v, float val) {
        return val * v.getResources().getDisplayMetrics().density;
    }
}
