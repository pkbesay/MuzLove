package com.example.muzlove;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TextsTest {

    @Test public void plurals() {
        assertEquals("1 трек", Texts.tracks(1));
        assertEquals("2 трека", Texts.tracks(2));
        assertEquals("5 треков", Texts.tracks(5));
        assertEquals("11 треков", Texts.tracks(11));
        assertEquals("21 трек", Texts.tracks(21));
        assertEquals("0 треков", Texts.tracks(0));
        assertEquals("112 треков", Texts.tracks(112));
        assertEquals("5 минут", Texts.minutesAcc(5));
        assertEquals("1 минуту", Texts.minutesAcc(1));
        assertEquals("3 минуты", Texts.minutesAcc(3));
        assertEquals("21 минута", Texts.minutes(21));
    }

    @Test public void playlistMetaAndFailures() {
        assertEquals("4 трека · 14 мин", Texts.playlistMeta(4, 14 * 60 + 10));
        assertEquals("1 трек · 1 мин", Texts.playlistMeta(1, 20));
        assertEquals("2 трека", Texts.playlistMeta(2, 0));
        assertEquals("0 треков", Texts.playlistMeta(0, 0));
        assertEquals("Не удалось добавить файл", Texts.failedFiles(1));
        assertEquals("Не удалось добавить 3 файла", Texts.failedFiles(3));
        assertEquals("Не удалось добавить 5 файлов", Texts.failedFiles(5));
    }

    @Test public void sleepState() {
        assertEquals("Выкл", Texts.sleepState(-1, false));
        assertEquals("Выкл", Texts.sleepState(0, false));
        assertEquals("до конца трека", Texts.sleepState(-1, true));
        assertEquals("ещё 12 мин", Texts.sleepState(11 * 60_000 + 1, false));
        assertEquals("ещё 1 мин", Texts.sleepState(500, false));
    }

    @Test public void freqAndDb() {
        assertEquals("60 Гц", Texts.freq(60));
        assertEquals("910 Гц", Texts.freq(910));
        assertEquals("3,6 кГц", Texts.freq(3600));
        assertEquals("14 кГц", Texts.freq(14000));
        assertEquals("+3 дБ", Texts.db(300));
        assertEquals("0 дБ", Texts.db(20));
        assertEquals("−7 дБ", Texts.db(-700));
    }

    @Test public void sizeDurationFormat() {
        assertEquals("1 КБ", Texts.size(10));
        assertEquals("3,5 МБ", Texts.size(3_670_016));
        assertEquals("3:35", Texts.duration(215_000));
        assertEquals("1:02:07", Texts.duration(3_727_000));
        assertEquals("MP3", Texts.format("audio/mpeg"));
        assertEquals("XYZ", Texts.format("audio/x-xyz"));
        assertNull(Texts.format(null));
        assertEquals("Рок", Texts.preset("Rock"));
        assertEquals("Custom", Texts.preset("Custom"));
    }
}
