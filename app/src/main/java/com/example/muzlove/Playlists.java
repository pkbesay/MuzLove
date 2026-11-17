package com.example.muzlove;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Операции над списками id своих плейлистов и их оформление. Чистая Java, покрыта юнит-тестами. */
final class Playlists {

    /** Максимальная длина названия плейлиста. */
    static final int MAX_NAME = 40;

    /** Градиенты новых плейлистов (по кругу). */
    private static final String[][] GRADIENTS = {
            {"#8B5CF6", "#EC4899"},
            {"#06B6D4", "#6366F1"},
            {"#F59E0B", "#EF4444"},
            {"#10B981", "#06B6D4"},
            {"#F472B6", "#FB7185"},
            {"#6366F1", "#A855F7"},
            {"#14B8A6", "#84CC16"},
            {"#F97316", "#DB2777"},
    };

    private Playlists() {}

    /** Пара цветов для n-го своего плейлиста. */
    static String[] gradient(int n) {
        String[] g = GRADIENTS[Math.floorMod(n, GRADIENTS.length)];
        return new String[]{g[0], g[1]};
    }

    /** Название для показа: пустое или null — «Без названия». */
    static String name(String raw) {
        return raw == null || raw.trim().isEmpty() ? "Без названия" : raw;
    }

    static boolean contains(int[] ids, int id) {
        if (ids == null) return false;
        for (int x : ids) if (x == id) return true;
        return false;
    }

    /** Копия без id (null — пустой массив). */
    static int[] without(int[] ids, int id) {
        if (ids == null) return new int[0];
        int n = 0;
        for (int x : ids) if (x != id) n++;
        int[] out = new int[n];
        int i = 0;
        for (int x : ids) if (x != id) out[i++] = x;
        return out;
    }

    /** Новый состав после выбора: оставшиеся старые треки в прежнем порядке, затем новые в порядке выбора.
     *  Повторы убираются. */
    static int[] merge(int[] initial, List<Integer> selected) {
        Set<Integer> sel = new LinkedHashSet<>();
        if (selected != null) for (Integer id : selected) if (id != null) sel.add(id);
        Set<Integer> out = new LinkedHashSet<>();
        if (initial != null) for (int id : initial) if (sel.contains(id)) out.add(id);
        out.addAll(sel);
        return toArray(new ArrayList<>(out));
    }

    static int[] toArray(List<Integer> ids) {
        if (ids == null) return new int[0];
        int n = 0;
        for (Integer id : ids) if (id != null) n++;
        int[] out = new int[n];
        int i = 0;
        for (Integer id : ids) if (id != null) out[i++] = id;
        return out;
    }
}
