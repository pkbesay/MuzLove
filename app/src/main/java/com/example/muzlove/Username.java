package com.example.muzlove;

import java.util.Locale;
import java.util.Random;

/** Имя и @username профиля: нормализация, проверка, транслитерация. Чистая Java, покрыта юнит-тестами. */
final class Username {

    static final int MIN = 3, MAX = 20, NAME_MAX = 30;
    static final String ERROR = "Имя пользователя: 3–20 латинских букв, цифр, _ или .";

    /** Русские буквы «а»..«я» по порядку (ъ и ь пропадают). */
    private static final String[] RU = {"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n",
            "o", "p", "r", "s", "t", "u", "f", "kh", "ts", "ch", "sh", "shch", "", "y", "", "e", "yu", "ya"};

    private Username() {}

    /** Пробелы и ведущие «@» долой, строчные буквы. Результат может быть пустым и недопустимым. */
    static String normalize(CharSequence raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) b.append(c);
        }
        int start = 0;
        while (start < b.length() && b.charAt(start) == '@') start++;
        return b.substring(start).toLowerCase(Locale.ROOT);
    }

    /** 3..20 символов из a-z, 0-9, «_» и «.». */
    static boolean isValid(String s) {
        if (s == null || s.length() < MIN || s.length() > MAX) return false;
        for (int i = 0; i < s.length(); i++) if (!allowed(s.charAt(i))) return false;
        return true;
    }

    /** Нормализованное допустимое имя пользователя или null (пусто и недопустимо — тоже null). */
    static String parse(CharSequence raw) {
        String n = normalize(raw);
        return isValid(n) ? n : null;
    }

    /** Имя пользователя по имени: кириллица транслитом, лишние символы убраны. Короче трёх — «user» и 4 цифры. */
    static String fromName(String name, Random random) {
        StringBuilder b = new StringBuilder();
        if (name != null) {
            String s = name.toLowerCase(Locale.ROOT);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= 'а' && c <= 'я') b.append(RU[c - 'а']);
                else if (c == 'ё' || c == 'є') b.append('e');          // ё, є
                else if (c == 'і' || c == 'ї') b.append('i');          // і, ї
                else if (c == 'ґ') b.append('g');                           // ґ
                else if (allowed(c)) b.append(c);
            }
        }
        String s = trimDots(b.toString());
        if (s.length() > MAX) s = trimDots(s.substring(0, MAX));
        if (s.length() >= MIN) return s;
        return "user" + String.format(Locale.US, "%04d", random.nextInt(10000));
    }

    /** Имя профиля: без пробелов по краям, не длиннее 30 символов; пустое — «». */
    static String cleanName(CharSequence raw) {
        if (raw == null) return "";
        int from = 0, to = raw.length();
        while (from < to && isBlank(raw.charAt(from))) from++;
        while (to > from && isBlank(raw.charAt(to - 1))) to--;
        if (to - from > NAME_MAX) {
            to = from + NAME_MAX;
            if (Character.isHighSurrogate(raw.charAt(to - 1))) to--;        // не разрезаем пару
            while (to > from && isBlank(raw.charAt(to - 1))) to--;
        }
        return raw.subSequence(from, to).toString();
    }

    /** Буква для аватара: первый символ имени заглавным, для пустого имени «M». */
    static String initial(String name) {
        if (name == null || name.trim().isEmpty()) return "M";
        String s = name.trim();
        return new String(Character.toChars(s.codePointAt(0))).toUpperCase(Locale.ROOT);
    }

    private static boolean allowed(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
    }

    private static boolean isBlank(char c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    private static String trimDots(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '.') a++;
        while (b > a && s.charAt(b - 1) == '.') b--;
        return s.substring(a, b);
    }
}
