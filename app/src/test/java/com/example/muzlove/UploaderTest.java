package com.example.muzlove;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UploaderTest {

    @Test public void extensionFromNameThenMime() {
        assertEquals(".mp3", Uploader.extension("Song.MP3", null));
        assertEquals(".flac", Uploader.extension("a.b.flac", "mpeg"));
        assertEquals(".ogg", Uploader.extension("без расширения", "ogg"));
        assertEquals(".m4a", Uploader.extension(null, "m4a"));
        assertEquals(".audio", Uploader.extension("name.", null));
        assertEquals(".audio", Uploader.extension(".hidden", null));
        assertEquals(".audio", Uploader.extension("track.очень", null));
        assertEquals(".audio", Uploader.extension(null, null));
    }

    @Test public void titleFromFileName() {
        assertEquals("Моя песня", Uploader.fileTitle("  Моя песня.mp3 "));
        assertEquals("a.b", Uploader.fileTitle("a.b.wav"));
        assertEquals("trackname", Uploader.fileTitle("trackname"));
        assertEquals(".mp3", Uploader.fileTitle(".mp3"));
        assertNull(Uploader.fileTitle("   "));
        assertNull(Uploader.fileTitle(null));
    }

    @Test public void audioMime() {
        assertTrue(Uploader.isAudioMime("audio/mpeg"));
        assertTrue(Uploader.isAudioMime("AUDIO/FLAC"));
        assertTrue(Uploader.isAudioMime("application/ogg"));
        assertFalse(Uploader.isAudioMime("video/mp4"));
        assertFalse(Uploader.isAudioMime("image/png"));
        assertFalse(Uploader.isAudioMime(null));
    }
}
