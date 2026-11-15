package com.example.muzlove;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlaylistsTest {

    @Test public void mergeKeepsOldOrderAndAppendsNew() {
        int[] before = {3, 1, 7};
        // 7 сняли, добавили 9 и 2 (в порядке нажатий), 1 и 3 остались
        assertArrayEquals(new int[]{3, 1, 9, 2}, Playlists.merge(before, Arrays.asList(3, 1, 9, 2)));
        // порядок в selected не меняет порядок уже бывших треков
        assertArrayEquals(new int[]{3, 1, 5}, Playlists.merge(before, Arrays.asList(5, 1, 3)));
        assertArrayEquals(new int[0], Playlists.merge(before, Collections.emptyList()));
        assertArrayEquals(new int[]{4, 2}, Playlists.merge(null, Arrays.asList(4, null, 2, 4)));
        assertArrayEquals(new int[0], Playlists.merge(null, null));
    }

    @Test public void withoutAndContains() {
        assertArrayEquals(new int[]{1, 3}, Playlists.without(new int[]{1, 2, 3, 2}, 2));
        assertArrayEquals(new int[]{1}, Playlists.without(new int[]{1}, 5));
        assertArrayEquals(new int[0], Playlists.without(null, 1));
        assertTrue(Playlists.contains(new int[]{4, 5}, 5));
        assertFalse(Playlists.contains(new int[]{4, 5}, 6));
        assertFalse(Playlists.contains(null, 1));
    }

    @Test public void toArraySkipsNulls() {
        assertArrayEquals(new int[]{1, 2}, Playlists.toArray(Arrays.asList(1, null, 2)));
        assertArrayEquals(new int[0], Playlists.toArray(null));
    }

    @Test public void namesAndGradients() {
        assertEquals("Без названия", Playlists.name(null));
        assertEquals("Без названия", Playlists.name("   "));
        assertEquals("Дорога", Playlists.name("Дорога"));
        assertEquals(2, Playlists.gradient(0).length);
        assertNotEquals(Playlists.gradient(0)[0] + Playlists.gradient(0)[1],
                Playlists.gradient(1)[0] + Playlists.gradient(1)[1]);
        assertArrayEquals(Playlists.gradient(0), Playlists.gradient(8));   // по кругу
        assertEquals(2, Playlists.gradient(-3).length);
    }
}
