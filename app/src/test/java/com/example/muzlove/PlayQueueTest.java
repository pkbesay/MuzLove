package com.example.muzlove;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public class PlayQueueTest {

    private PlayQueue q;

    @Before
    public void setUp() { q = new PlayQueue(new Random(42)); }

    private static List<Integer> ids(Integer... v) { return Arrays.asList(v); }

    private static HashSet<Integer> setOf(List<Integer> l) { return new HashSet<>(l); }

    // ---------- set ----------
    @Test
    public void set_startsAtRequestedTrack() {
        q.set(ids(1, 2, 3, 4), 3);
        assertEquals(3, q.currentId());
        assertEquals(2, q.index());
        assertEquals(ids(1, 2, 3, 4), q.order());
        assertEquals(4, q.size());
        assertTrue(q.contains(4));
        assertFalse(q.contains(9));
    }

    @Test
    public void set_unknownStartGivesIndexZero() {
        q.set(ids(5, 6, 7), 99);
        assertEquals(0, q.index());
        assertEquals(5, q.currentId());
    }

    @Test
    public void set_removesDuplicatesFirstWins() {
        q.set(ids(1, 2, 1, 3, 2), 3);
        assertEquals(ids(1, 2, 3), q.order());
        assertEquals(2, q.index());
    }

    @Test
    public void set_emptyListGivesEmptyQueue() {
        q.set(new ArrayList<>(), 1);
        assertTrue(q.isEmpty());
        assertEquals(-1, q.currentId());
        assertEquals(0, q.size());
        assertEquals(PlayQueue.Advance.ENDED, q.advance(true));
    }

    @Test
    public void set_withShufflePutsStartFirst() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3, 4, 5, 6), 4);
        assertEquals(0, q.index());
        assertEquals(4, q.currentId());
        assertEquals(setOf(ids(1, 2, 3, 4, 5, 6)), setOf(q.order()));
        assertEquals(6, q.size());
    }

    // ---------- advance ----------
    @Test
    public void advance_movesForward() {
        q.set(ids(1, 2, 3), 1);
        assertEquals(PlayQueue.Advance.MOVED, q.advance(true));
        assertEquals(2, q.currentId());
        assertEquals(PlayQueue.Advance.MOVED, q.advance(false));
        assertEquals(3, q.currentId());
    }

    @Test
    public void advance_repeatOffAutoAtEndEnds() {
        q.set(ids(1, 2, 3), 3);
        assertEquals(PlayQueue.Advance.ENDED, q.advance(true));
        assertEquals(0, q.index());
        assertEquals(1, q.currentId());
    }

    @Test
    public void advance_repeatOffManualAtEndWraps() {
        q.set(ids(1, 2, 3), 3);
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(false));
        assertEquals(1, q.currentId());
    }

    @Test
    public void advance_repeatAllWrapsBothWays() {
        q.setRepeat(PlayQueue.REPEAT_ALL);
        q.set(ids(1, 2, 3), 3);
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(true));
        assertEquals(1, q.currentId());
        q.jumpTo(2);
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(false));
        assertEquals(1, q.currentId());
    }

    @Test
    public void advance_repeatOneStaysOnAutoButMovesOnNext() {
        q.setRepeat(PlayQueue.REPEAT_ONE);
        q.set(ids(1, 2, 3), 2);
        assertEquals(PlayQueue.Advance.SAME, q.advance(true));
        assertEquals(2, q.currentId());
        assertEquals(PlayQueue.Advance.MOVED, q.advance(false));
        assertEquals(3, q.currentId());
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(false));
        assertEquals(1, q.currentId());
    }

    @Test
    public void advance_singleTrackRepeatOff() {
        q.set(ids(7), 7);
        assertEquals(PlayQueue.Advance.ENDED, q.advance(true));
        assertEquals(7, q.currentId());
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(false));
        assertEquals(7, q.currentId());
    }

    @Test
    public void back_wrapsToLast() {
        q.set(ids(1, 2, 3), 2);
        q.back();
        assertEquals(1, q.currentId());
        q.back();
        assertEquals(3, q.currentId());
    }

    @Test
    public void jumpTo_ignoresInvalidIndex() {
        q.set(ids(1, 2, 3), 1);
        q.jumpTo(2);
        assertEquals(3, q.currentId());
        q.jumpTo(5);
        q.jumpTo(-1);
        assertEquals(3, q.currentId());
    }

    // ---------- removal ----------
    @Test
    public void removeAt_beforeCurrentKeepsTrack() {
        q.set(ids(1, 2, 3, 4), 3);
        assertFalse(q.removeAt(0));
        assertEquals(3, q.currentId());
        assertEquals(1, q.index());
        assertEquals(ids(2, 3, 4), q.order());
    }

    @Test
    public void removeAt_afterCurrentKeepsTrack() {
        q.set(ids(1, 2, 3, 4), 2);
        assertFalse(q.removeAt(3));
        assertEquals(2, q.currentId());
        assertEquals(1, q.index());
        assertEquals(ids(1, 2, 3), q.order());
    }

    @Test
    public void removeAt_currentNextTakesPlace() {
        q.set(ids(1, 2, 3, 4), 2);
        assertTrue(q.removeAt(1));
        assertEquals(1, q.index());
        assertEquals(3, q.currentId());
    }

    @Test
    public void removeAt_currentLastGoesToFirst() {
        q.set(ids(1, 2, 3), 3);
        assertTrue(q.removeAt(2));
        assertEquals(0, q.index());
        assertEquals(1, q.currentId());
    }

    @Test
    public void removeAt_lastRemainingEmptiesQueue() {
        q.set(ids(5), 5);
        assertTrue(q.removeAt(0));
        assertTrue(q.isEmpty());
        assertEquals(-1, q.currentId());
        assertFalse(q.removeAt(0));
    }

    @Test
    public void removeAt_alsoRemovesFromBaseOrder() {
        q.set(ids(1, 2, 3, 4), 1);
        q.removeAt(2);
        q.setShuffle(true);
        q.setShuffle(false);
        assertEquals(ids(1, 2, 4), q.order());
    }

    @Test
    public void removeId_presentAndAbsent() {
        q.set(ids(1, 2, 3), 2);
        assertFalse(q.removeId(42));
        assertFalse(q.removeId(1));
        assertEquals(2, q.currentId());
        assertTrue(q.removeId(2));
        assertEquals(3, q.currentId());
        assertEquals(ids(3), q.order());
    }

    // ---------- playNext / append ----------
    @Test
    public void playNext_absentIdInsertedAfterCurrent() {
        q.set(ids(1, 2, 3), 2);
        q.playNext(9);
        assertEquals(ids(1, 2, 9, 3), q.order());
        assertEquals(2, q.currentId());
        assertEquals(PlayQueue.Advance.MOVED, q.advance(false));
        assertEquals(9, q.currentId());
    }

    @Test
    public void playNext_presentIdIsMovedWithoutDuplicate() {
        q.set(ids(1, 2, 3, 4, 5), 3);
        q.playNext(1);          // стоял до текущего
        assertEquals(ids(2, 3, 1, 4, 5), q.order());
        assertEquals(3, q.currentId());
        q.playNext(5);          // стоял после текущего
        assertEquals(ids(2, 3, 5, 1, 4), q.order());
        assertEquals(3, q.currentId());
        assertEquals(5, q.size());
    }

    @Test
    public void playNext_currentIsNoOp() {
        q.set(ids(1, 2, 3), 2);
        q.playNext(2);
        assertEquals(ids(1, 2, 3), q.order());
        assertEquals(1, q.index());
    }

    @Test
    public void playNext_onEmptyQueueBecomesCurrent() {
        q.playNext(8);
        assertEquals(ids(8), q.order());
        assertEquals(8, q.currentId());
    }

    @Test
    public void playNext_insertedIntoBaseAfterCurrent() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3, 4), 2);
        q.playNext(9);
        assertEquals(9, q.order().get(1).intValue());
        q.setShuffle(false);
        assertEquals(ids(1, 2, 9, 3, 4), q.order());
        assertEquals(2, q.currentId());
    }

    @Test
    public void append_addsOnceToTheEnd() {
        q.set(ids(1, 2), 1);
        assertTrue(q.append(3));
        assertFalse(q.append(2));
        assertEquals(ids(1, 2, 3), q.order());
        PlayQueue empty = new PlayQueue(new Random(1));
        assertTrue(empty.append(4));
        assertEquals(4, empty.currentId());
    }

    // ---------- shuffle / repeat ----------
    @Test
    public void shuffleOnOff_keepsCurrentAndSameIds() {
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= 20; i++) all.add(i);
        q.set(all, 7);
        q.setShuffle(true);
        assertTrue(q.isShuffle());
        assertEquals(7, q.currentId());
        assertEquals(0, q.index());
        assertEquals(setOf(all), setOf(q.order()));
        assertEquals(20, q.size());
        q.advance(false);
        int cur = q.currentId();
        q.setShuffle(false);
        assertFalse(q.isShuffle());
        assertEquals(all, q.order());
        assertEquals(cur, q.currentId());
        assertEquals(cur - 1, q.index());
    }

    @Test
    public void shuffle_isDeterministicWithSeed() {
        PlayQueue a = new PlayQueue(new Random(7)), b = new PlayQueue(new Random(7));
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= 12; i++) all.add(i);
        a.set(all, 1); b.set(all, 1);
        a.setShuffle(true); b.setShuffle(true);
        assertEquals(a.order(), b.order());
    }

    @Test
    public void shuffle_onEmptyQueueOnlyStoresFlag() {
        q.setShuffle(true);
        assertTrue(q.isShuffle());
        assertTrue(q.isEmpty());
        q.set(ids(3, 1, 2), 1);
        assertEquals(1, q.currentId());
        assertEquals(0, q.index());
    }

    @Test
    public void repeat_cyclesAndClamps() {
        assertEquals(PlayQueue.REPEAT_OFF, q.getRepeat());
        assertEquals(1, q.cycleRepeat());
        assertEquals(2, q.cycleRepeat());
        assertEquals(0, q.cycleRepeat());
        q.setRepeat(2);
        assertEquals(2, q.getRepeat());
        q.setRepeat(7);
        assertEquals(0, q.getRepeat());
    }

    // ---------- дополнительные случаи ----------
    @Test
    public void set_replacesPreviousQueue() {
        q.set(ids(1, 2, 3), 3);
        q.set(ids(7, 8), 8);
        assertEquals(ids(7, 8), q.order());
        assertEquals(1, q.index());
        assertFalse(q.contains(1));
        q.set(null, 7);
        assertTrue(q.isEmpty());
        assertEquals(0, q.index());
    }

    @Test
    public void emptyQueue_operationsAreSafe() {
        q.back();
        q.jumpTo(0);
        assertFalse(q.removeAt(0));
        assertFalse(q.removeId(1));
        assertEquals(PlayQueue.Advance.ENDED, q.advance(false));
        assertEquals(-1, q.currentId());
        assertEquals(0, q.index());
        assertTrue(q.order().isEmpty());
    }

    @Test
    public void removeAt_invalidIndexChangesNothing() {
        q.set(ids(1, 2, 3), 2);
        assertFalse(q.removeAt(-1));
        assertFalse(q.removeAt(3));
        assertEquals(ids(1, 2, 3), q.order());
        assertEquals(2, q.currentId());
    }

    @Test
    public void removeId_currentLastItemWrapsToFirst() {
        q.set(ids(4, 5, 6), 6);
        assertTrue(q.removeId(6));
        assertEquals(0, q.index());
        assertEquals(4, q.currentId());
    }

    @Test
    public void removeInShuffle_alsoLeavesBaseOrder() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3, 4, 5, 6), 3);
        assertFalse(q.removeId(5));
        q.setShuffle(false);
        assertEquals(ids(1, 2, 3, 4, 6), q.order());
        assertEquals(3, q.currentId());
    }

    @Test
    public void appendInShuffle_goesToTheEndOfBothOrders() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3), 1);
        assertTrue(q.append(9));
        assertEquals(9, q.order().get(3).intValue());
        q.setShuffle(false);
        assertEquals(ids(1, 2, 3, 9), q.order());
        assertEquals(1, q.currentId());
    }

    @Test
    public void playNext_trackBeforeCurrentOnLastPosition() {
        q.set(ids(1, 2, 3), 3);
        q.playNext(1);
        assertEquals(ids(2, 3, 1), q.order());
        assertEquals(3, q.currentId());
        assertEquals(PlayQueue.Advance.MOVED, q.advance(true));
        assertEquals(1, q.currentId());
    }

    @Test
    public void advance_repeatAllSingleTrackWraps() {
        q.setRepeat(PlayQueue.REPEAT_ALL);
        q.set(ids(5), 5);
        assertEquals(PlayQueue.Advance.WRAPPED, q.advance(true));
        assertEquals(5, q.currentId());
    }

    /** Случайные последовательности операций: без дублей, индекс валиден,
     *  base и order содержат одни и те же id (проверяется переключением перемешивания). */
    @Test
    public void randomOperations_keepInvariants() {
        Random r = new Random(2024);
        for (int step = 0; step < 500; step++) {
            int id = 1 + r.nextInt(12);
            switch (r.nextInt(10)) {
                case 0: {
                    List<Integer> l = new ArrayList<>();
                    int n = r.nextInt(9);
                    for (int i = 0; i < n; i++) l.add(1 + r.nextInt(12));
                    q.set(l, id);
                    break;
                }
                case 1: q.advance(r.nextBoolean()); break;
                case 2: q.back(); break;
                case 3: q.jumpTo(r.nextInt(12) - 1); break;
                case 4: q.removeAt(r.nextInt(12) - 1); break;
                case 5: q.removeId(id); break;
                case 6: q.playNext(id); break;
                case 7: q.append(id); break;
                case 8: q.setRepeat(r.nextInt(3)); break;
                default: {
                    List<Integer> before = q.order();
                    int cur = q.currentId();
                    q.setShuffle(!q.isShuffle());
                    assertEquals(setOf(before), setOf(q.order()));
                    assertEquals(cur, q.currentId());
                }
            }
            List<Integer> o = q.order();
            assertEquals("дубли в очереди на шаге " + step, o.size(), setOf(o).size());
            assertEquals(o.size(), q.size());
            if (o.isEmpty()) {
                assertEquals(-1, q.currentId());
                assertEquals(0, q.index());
            } else {
                assertTrue(q.index() >= 0 && q.index() < o.size());
                assertEquals(o.get(q.index()).intValue(), q.currentId());
            }
        }
    }

    @Test
    public void set_shuffleWithAbsentStartKeepsAllIds() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3, 4), 9);
        assertEquals(0, q.index());
        assertEquals(4, q.size());
        assertFalse(q.contains(9));
        assertEquals(setOf(ids(1, 2, 3, 4)), setOf(q.order()));
        q.setShuffle(false);
        assertEquals(ids(1, 2, 3, 4), q.order());
    }

    @Test
    public void set_skipsNullIds() {
        q.set(Arrays.asList(1, null, 2, null), 2);
        assertEquals(ids(1, 2), q.order());
        assertEquals(2, q.currentId());
        assertEquals(1, q.index());
    }

    @Test
    public void removeAt_currentInShuffleNextTakesPlaceAndBaseFollows() {
        q.setShuffle(true);
        q.set(ids(1, 2, 3, 4, 5), 3);
        List<Integer> before = q.order();
        assertTrue(q.removeAt(q.index()));
        assertEquals(0, q.index());
        assertEquals(before.get(1).intValue(), q.currentId());
        int cur = q.currentId();
        q.setShuffle(false);
        assertEquals(ids(1, 2, 4, 5), q.order());
        assertEquals(cur, q.currentId());
    }

    @Test
    public void back_singleItemStaysOnIt() {
        q.set(ids(4), 4);
        q.back();
        assertEquals(0, q.index());
        assertEquals(4, q.currentId());
    }

    @Test
    public void playNext_whenCurrentIsLastGoesToTheEnd() {
        q.set(ids(1, 2, 3), 3);
        q.playNext(7);
        assertEquals(ids(1, 2, 3, 7), q.order());
        assertEquals(3, q.currentId());
        assertEquals(PlayQueue.Advance.MOVED, q.advance(true));
        assertEquals(7, q.currentId());
        assertEquals(PlayQueue.Advance.ENDED, q.advance(true));
        assertEquals(1, q.currentId());
    }

    @Test
    public void order_returnsCopy() {
        q.set(ids(1, 2, 3), 1);
        List<Integer> o = q.order();
        o.clear();
        assertEquals(3, q.size());
        Collections.reverse(q.order());
        assertEquals(ids(1, 2, 3), q.order());
    }
}
