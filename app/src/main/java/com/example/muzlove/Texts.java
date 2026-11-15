package com.example.muzlove;

import java.util.Locale;

/** Русские подписи: склонения, частоты, децибелы, размеры. Чистая Java, покрыта юнит-тестами. */
final class Texts {

    static final Locale RU = Locale.forLanguageTag("ru-RU");

    private Texts() {}

    /** plural(5, "трек", "трека", "треков") -> "треков". */
    static String plural(int n, String one, String few, String many) {
        int a = Math.abs(n) % 10, b = Math.abs(n) % 100;
        if (a == 1 && b != 11) return one;
        if (a >= 2 && a <= 4 && (b < 10 || b >= 20)) return few;
        return many;
    }

    static String tracks(int n) { return n + " " + plural(n, "трек", "трека", "треков"); }

    /** Строка под названием плейлиста: «4 трека · 14 мин»; без длительности — только число треков. */
    static String playlistMeta(int n, long totalSec) {
        if (n <= 0 || totalSec <= 0) return tracks(Math.max(0, n));
        return tracks(n) + " · " + Math.max(1, Math.round(totalSec / 60.0)) + " мин";
    }

    /** «Не удалось добавить файл» / «Не удалось добавить 3 файла». */
    static String failedFiles(int n) {
        if (n == 1) return "Не удалось добавить файл";
        return "Не удалось добавить " + n + " " + plural(n, "файл", "файла", "файлов");
    }

    /** «через N минут»: винительный падеж (1 минуту, 2 минуты, 5 минут). */
    static String minutesAcc(int n) { return n + " " + plural(n, "минуту", "минуты", "минут"); }

    /** Название варианта таймера: «5 минут», «1 минута». */
    static String minutes(int n) { return n + " " + plural(n, "минута", "минуты", "минут"); }

    /** Состояние таймера сна: «Выкл», «ещё 12 мин», «до конца трека». */
    static String sleepState(long remainingMs, boolean atTrackEnd) {
        if (remainingMs > 0) return "ещё " + Math.max(1, (remainingMs + 59_999) / 60_000) + " мин";
        if (atTrackEnd) return "до конца трека";
        return "Выкл";
    }

    /** Центральная частота полосы: «60 Гц», «3,6 кГц», «14 кГц». */
    static String freq(int hz) {
        if (hz < 1000) return hz + " Гц";
        float k = hz / 1000f;
        if (Math.abs(k - Math.round(k)) < 0.05f) return Math.round(k) + " кГц";
        return String.format(RU, "%.1f кГц", k);
    }

    /** Уровень полосы в миллибелах -> «+3 дБ», «0 дБ», «−3 дБ». */
    static String db(int millibel) {
        int d = Math.round(millibel / 100f);
        if (d > 0) return "+" + d + " дБ";
        if (d < 0) return "−" + (-d) + " дБ";
        return "0 дБ";
    }

    /** Размер файла: «840 КБ», «3,4 МБ». */
    static String size(long bytes) {
        if (bytes < 0) bytes = 0;
        if (bytes < 1024L * 1024L) return Math.max(1, Math.round(bytes / 1024f)) + " КБ";
        return String.format(RU, "%.1f МБ", bytes / (1024f * 1024f));
    }

    /** Длительность: «3:35», «1:02:07». */
    static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    /** MIME-тип -> короткое название формата. */
    static String format(String mime) {
        if (mime == null || mime.isEmpty()) return null;
        String m = mime.toLowerCase(Locale.ROOT);
        switch (m) {
            case "audio/mpeg": case "audio/mp3": case "audio/mpeg3": return "MP3";
            case "audio/mp4": case "audio/m4a": case "audio/x-m4a": return "M4A (AAC)";
            case "audio/aac": case "audio/aac-adts": return "AAC";
            case "audio/flac": case "audio/x-flac": return "FLAC";
            case "audio/ogg": case "application/ogg": case "audio/vorbis": return "OGG";
            case "audio/opus": return "Opus";
            case "audio/wav": case "audio/x-wav": case "audio/wave": return "WAV";
            case "audio/amr": case "audio/3gpp": return "AMR";
            case "audio/midi": case "audio/sp-midi": return "MIDI";
            default:
                int slash = m.indexOf('/');
                String sub = slash >= 0 ? m.substring(slash + 1) : m;
                if (sub.startsWith("x-")) sub = sub.substring(2);
                return sub.isEmpty() ? null : sub.toUpperCase(Locale.ROOT);
        }
    }

    /** Названия стандартных пресетов эквалайзера по-русски (остальные — как отдаёт система). */
    static String preset(String name) {
        if (name == null || name.trim().isEmpty()) return "Пресет";
        switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "normal": return "Обычный";
            case "classical": return "Классика";
            case "dance": return "Танцевальный";
            case "flat": return "Ровный";
            case "folk": return "Фолк";
            case "heavy metal": return "Хеви-метал";
            case "hip hop": return "Хип-хоп";
            case "jazz": return "Джаз";
            case "pop": return "Поп";
            case "rock": return "Рок";
            case "bass boost": return "Усиление басов";
            case "vocal": return "Вокал";
            default: return name.trim();
        }
    }
}
