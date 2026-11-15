package com.example.muzlove;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Очередь воспроизведения по id треков. Чистая Java (без android.*), покрыта юнит-тестами.
 *  base — исходный порядок списка, order — порядок воспроизведения (совпадает с base без перемешивания). */
public final class PlayQueue {

    /** Результат advance(). */
    public enum Advance { SAME, MOVED, WRAPPED, ENDED }

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    private final Random random;
    private final List<Integer> base = new ArrayList<>();
    private final List<Integer> order = new ArrayList<>();
    private int index = 0;
    private boolean shuffle = false;
    private int repeat = REPEAT_OFF;

    public PlayQueue() { this(new Random()); }

    public PlayQueue(Random random) { this.random = random != null ? random : new Random(); }

    // ---------- состояние ----------
    public int currentId() { return order.isEmpty() ? -1 : order.get(index); }
    public int index() { return index; }
    public int size() { return order.size(); }
    public boolean isEmpty() { return order.isEmpty(); }
    public List<Integer> order() { return new ArrayList<>(order); }
    public boolean contains(int id) { return order.contains(id); }
    public int indexOf(int id) { return order.indexOf(id); }
    public boolean isShuffle() { return shuffle; }
    public int getRepeat() { return repeat; }

    // ---------- наполнение ----------
    public void set(List<Integer> ids, int startId) {
        base.clear();
        order.clear();
        index = 0;
        if (ids != null) {
            LinkedHashSet<Integer> uniq = new LinkedHashSet<>();
            for (Integer id : ids) if (id != null) uniq.add(id);
            base.addAll(uniq);
        }
        if (base.isEmpty()) return;
        if (shuffle) {
            List<Integer> rest = new ArrayList<>(base);
            boolean has = rest.remove((Integer) startId);
            Collections.shuffle(rest, random);
            if (has) order.add(startId);
            order.addAll(rest);
        } else {
            order.addAll(base);
            index = Math.max(0, order.indexOf(startId));
        }
    }

    // ---------- перемещение ----------
    /** auto = трек доиграл сам; false = кнопка «Далее» (всегда переходит, даже при повторе одного). */
    public Advance advance(boolean auto) {
        if (order.isEmpty()) return Advance.ENDED;
        if (auto && repeat == REPEAT_ONE) return Advance.SAME;
        if (index + 1 < order.size()) { index++; return Advance.MOVED; }
        index = 0;
        if (repeat == REPEAT_OFF && auto) return Advance.ENDED;
        return Advance.WRAPPED;
    }

    public void back() {
        if (order.isEmpty()) return;
        index = index - 1 < 0 ? order.size() - 1 : index - 1;
    }

    public void jumpTo(int i) {
        if (i >= 0 && i < order.size()) index = i;
    }

    // ---------- изменение ----------
    /** Возвращает true, если сменился текущий трек или очередь опустела. */
    public boolean removeAt(int i) {
        if (i < 0 || i >= order.size()) return false;
        Integer id = order.remove(i);
        base.remove(id);
        if (order.isEmpty()) { index = 0; return true; }
        if (i < index) { index--; return false; }
        if (i == index) {
            if (index >= order.size()) index = 0;
            return true;
        }
        return false;
    }

    public boolean removeId(int id) {
        int i = order.indexOf(id);
        return i >= 0 && removeAt(i);
    }

    /** Ставит трек сразу после текущего (в обоих порядках). */
    public void playNext(int id) {
        if (order.isEmpty()) {
            base.clear();
            base.add(id);
            order.add(id);
            index = 0;
            return;
        }
        int cur = currentId();
        if (id == cur) return;
        int existing = order.indexOf(id);
        if (existing >= 0) removeAt(existing);      // не текущий, поэтому текущий трек не меняется
        order.add(index + 1, id);
        int b = base.indexOf(cur);
        if (b >= 0) base.add(b + 1, id); else base.add(id);
    }

    public boolean append(int id) {
        if (order.contains(id)) return false;
        order.add(id);
        base.add(id);
        return true;
    }

    // ---------- режимы ----------
    public void setShuffle(boolean on) {
        shuffle = on;
        if (order.isEmpty()) return;
        int cur = currentId();
        order.clear();
        if (on) {
            List<Integer> rest = new ArrayList<>(base);
            rest.remove((Integer) cur);
            Collections.shuffle(rest, random);
            order.add(cur);
            order.addAll(rest);
            index = 0;
        } else {
            order.addAll(base);
            index = Math.max(0, order.indexOf(cur));
        }
    }

    public void setRepeat(int r) { repeat = (r >= REPEAT_OFF && r <= REPEAT_ONE) ? r : REPEAT_OFF; }

    public int cycleRepeat() {
        repeat = (repeat + 1) % 3;
        return repeat;
    }
}
