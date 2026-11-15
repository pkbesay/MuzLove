package com.example.muzlove;

import android.media.audiofx.Equalizer;

/** Эквалайзер на аудиосессии плеера. Принадлежит PlayerEngine, только главный поток.
 *  Эффект создаётся лениво: когда он включён или его открыли, и только для живой сессии.
 *  Новый MediaPlayer = новая сессия: PlayerEngine вызывает onSession(), сохранённые настройки
 *  применяются заново. Любой вызов эффекта может бросить исключение на конкретном устройстве. */
public final class EqualizerController {

    private final StorageManager storage;
    private Equalizer eq;
    private int session;                 // 0 — плеера нет
    private boolean tried;               // для этой сессии уже пробовали создать эффект

    // описание полос (известно, пока эффект есть)
    private int bands, minLevel, maxLevel;
    private int[] freqsHz = new int[0];
    private String[] presets = new String[0];

    // настройки пользователя: в памяти, сохраняются в StorageManager
    private boolean enabled;
    private int preset;
    private int[] levels;

    EqualizerController(StorageManager storage) {
        this.storage = storage;
        enabled = storage.isEqEnabled();
        preset = storage.getEqPreset();
        levels = storage.getEqBands();
    }

    // ---------- сессия (вызывает PlayerEngine) ----------
    /** Создан новый MediaPlayer (или 0 — плеер освобождён). */
    void onSession(int sessionId) {
        if (sessionId == session) return;
        release();
        session = sessionId;
        if (enabled) ensure();
    }

    /** Освобождает эффект вместе с плеером. */
    void release() {
        if (eq != null) {
            try { eq.release(); } catch (RuntimeException ignored) {}
        }
        eq = null;
        session = 0;
        tried = false;
    }

    /** Создаёт эффект для текущей сессии, если ещё не пробовали. */
    public void ensure() {
        if (eq != null || tried || session == 0) return;
        tried = true;
        Equalizer e = null;
        try {
            e = new Equalizer(0, session);
            int n = e.getNumberOfBands();
            short[] range = e.getBandLevelRange();
            if (n <= 0 || range == null || range.length < 2 || range[0] >= range[1])
                throw new IllegalStateException("bands");
            int[] f = new int[n];
            for (short b = 0; b < n; b++) f[b] = e.getCenterFreq(b) / 1000;   // миллигерцы -> Гц
            int pc = Math.max(0, (int) e.getNumberOfPresets());
            String[] p = new String[pc];
            for (short i = 0; i < pc; i++) p[i] = Texts.preset(e.getPresetName(i));
            eq = e;
            bands = n; minLevel = range[0]; maxLevel = range[1];
            freqsHz = f; presets = p;
            apply();
        } catch (RuntimeException ex) {
            if (e != null) try { e.release(); } catch (RuntimeException ignored) {}
            eq = null;
        }
    }

    // ---------- состояние ----------
    /** Есть живая сессия плеера (что-то загружено или играло после последнего stop()). */
    public boolean hasSession() { return session != 0; }
    /** Эффект создан и им можно управлять (сначала вызвать ensure()). */
    public boolean isAvailable() { return eq != null; }

    public int bandCount() { return eq != null ? bands : 0; }
    public int minLevel() { return minLevel; }
    public int maxLevel() { return maxLevel; }
    public int centerFreqHz(int band) { return band >= 0 && band < freqsHz.length ? freqsHz[band] : 0; }
    public int presetCount() { return eq != null ? presets.length : 0; }
    public String presetName(int i) { return i >= 0 && i < presets.length ? presets[i] : ""; }

    public boolean isEnabled() { return enabled; }
    /** -1 — «Свой». */
    public int preset() { return preset >= 0 && preset < presetCount() ? preset : -1; }

    /** Уровень полосы в миллибелах (в пределах диапазона устройства). */
    public int bandLevel(int band) {
        int v = band >= 0 && band < levels.length ? levels[band] : 0;
        return eq != null ? clamp(v) : v;
    }

    // ---------- команды ----------
    public void setEnabled(boolean on) {
        enabled = on;
        storage.setEqEnabled(on);
        if (on) ensure();
        if (eq != null) {
            try { eq.setEnabled(on); } catch (RuntimeException ignored) {}
        }
    }

    /** Пресет устройства; -1 — «Свой» (текущие уровни остаются). */
    public void usePreset(int p) {
        if (eq == null) return;
        if (p < 0 || p >= presets.length) {
            preset = -1;
        } else {
            try {
                eq.usePreset((short) p);
                preset = p;
                readLevels();
            } catch (RuntimeException ignored) {}
        }
        save();
    }

    /** Двигает полосу (пресет становится «Свой»). Сохранение — save(), когда палец отпущен. */
    public void setBandLevel(int band, int millibel) {
        if (eq == null || band < 0 || band >= bands) return;
        ensureLevels();
        levels[band] = clamp(millibel);
        preset = -1;
        try { eq.setBandLevel((short) band, (short) levels[band]); } catch (RuntimeException ignored) {}
    }

    /** Все полосы в 0, пресет «Свой». */
    public void reset() {
        levels = new int[Math.max(bands, levels.length)];
        preset = -1;
        apply();
        save();
    }

    public void save() {
        storage.setEqPreset(preset);
        storage.setEqBands(levels);
    }

    // ---------- внутреннее ----------
    /** Применяет сохранённые настройки к свежему эффекту. */
    private void apply() {
        if (eq == null) return;
        try {
            if (preset >= 0 && preset < presets.length) {
                eq.usePreset((short) preset);
                readLevels();
            } else {
                preset = -1;
                ensureLevels();
                for (short b = 0; b < bands; b++) eq.setBandLevel(b, (short) clamp(levels[b]));
            }
            eq.setEnabled(enabled);
        } catch (RuntimeException ignored) {}
    }

    private void readLevels() {
        ensureLevels();
        for (short b = 0; b < bands; b++) {
            try { levels[b] = eq.getBandLevel(b); } catch (RuntimeException ignored) {}
        }
    }

    private void ensureLevels() {
        if (levels == null) levels = new int[0];
        if (levels.length >= bands) return;
        int[] l = new int[bands];
        System.arraycopy(levels, 0, l, 0, levels.length);
        levels = l;
    }

    private int clamp(int v) { return Math.max(minLevel, Math.min(maxLevel, v)); }
}
