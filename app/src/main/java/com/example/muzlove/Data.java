package com.example.muzlove;

import java.util.ArrayList;
import java.util.List;

public class Data {
    public static final List<Track> TRACKS = new ArrayList<>();
    public static final List<Playlist> PLAYLISTS = new ArrayList<>();

    static {
        TRACKS.add(new Track(1,  "Полуночный неон",   "Aria Voss",   "Городские огни", 215, "#FF6B6B", "#FFA94D", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"));
        TRACKS.add(new Track(2,  "Стеклянный океан",  "Lумен",       "Глубина",        188, "#06B6D4", "#6366F1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"));
        TRACKS.add(new Track(3,  "Бархатный закат",   "Nova Kane",   "Тёплый шум",     242, "#F59E0B", "#EF4444", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"));
        TRACKS.add(new Track(4,  "Северное сияние",   "Halcyon",     "Полярность",     201, "#14B8A6", "#84CC16", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"));
        TRACKS.add(new Track(5,  "Пыльный розовый",   "Mira Sole",   "Пастель",        176, "#EC4899", "#A855F7", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"));
        TRACKS.add(new Track(6,  "Электрический сон", "Cassiel",     "Грёзы",          223, "#8B5CF6", "#3B82F6", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3"));
        TRACKS.add(new Track(7,  "Золотая пыль",      "Solene",      "Эхо",            198, "#FB7185", "#FBBF24", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3"));
        TRACKS.add(new Track(8,  "Тихий прибой",      "Atlas Bloom", "Берег",          256, "#10B981", "#06B6D4", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"));
        TRACKS.add(new Track(9,  "Лунный камень",     "Veyra",       "Минералы",       167, "#A78BFA", "#F472B6", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3"));
        TRACKS.add(new Track(10, "Бесконечный поезд", "Kiro Vale",   "Движение",       234, "#F97316", "#DB2777", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3"));

        PLAYLISTS.add(new Playlist("Ночная смена", "Для долгих вечеров",   "#8B5CF6", "#3B82F6", new int[]{1, 6, 9, 5}));
        PLAYLISTS.add(new Playlist("Тёплая волна", "Лёгкость и солнце",    "#F59E0B", "#EF4444", new int[]{3, 7, 8, 4}));
        PLAYLISTS.add(new Playlist("Фокус",        "Чтобы не отвлекаться", "#06B6D4", "#6366F1", new int[]{2, 4, 8, 6}));
        PLAYLISTS.add(new Playlist("Свежее",       "Новое за неделю",      "#EC4899", "#A855F7", new int[]{10, 5, 1, 7}));
    }

    public static Track byId(int id) {
        for (Track t : TRACKS) if (t.id == id) return t;
        return TRACKS.get(0);
    }
}
