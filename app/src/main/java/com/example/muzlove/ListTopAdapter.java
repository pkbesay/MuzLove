package com.example.muzlove;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Один элемент над списком треков (через ConcatAdapter): контейнер, в который экран кладёт
 *  свою шапку, строки-действия или пустое состояние. Пустой контейнер имеет нулевую высоту.
 *  Содержимое меняется прямо в контейнере, без notify — RecyclerView перекладывает его сам. */
final class ListTopAdapter extends RecyclerView.Adapter<ListTopAdapter.VH> {

    private final LinearLayout box;

    ListTopAdapter(LinearLayout box) { this.box = box; }

    LinearLayout box() { return box; }

    @Override public int getItemCount() { return 1; }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout frame = new FrameLayout(parent.getContext());
        frame.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new VH(frame);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (box.getParent() == h.frame) return;
        // контейнер мог остаться в другой (уже отсоединённой) рамке — переносим
        if (box.getParent() instanceof ViewGroup) ((ViewGroup) box.getParent()).removeView(box);
        h.frame.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    static final class VH extends RecyclerView.ViewHolder {
        final FrameLayout frame;
        VH(FrameLayout f) { super(f); frame = f; }
    }
}
