package com.example.muzlove;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class UsernameTest {

    @Test public void normalizeStripsAtAndSpacesAndLowercases() {
        assertEquals("ivan_99", Username.normalize("@Ivan_99"));
        assertEquals("ivan", Username.normalize("  @ Ivan  "));
        assertEquals("a.b", Username.normalize("@@A.B"));
        assertEquals("", Username.normalize("@"));
        assertEquals("", Username.normalize(null));
    }

    @Test public void validity() {
        assertTrue(Username.isValid("abc"));
        assertTrue(Username.isValid("a_b.c9"));
        assertTrue(Username.isValid("abcdefghij0123456789"));
        assertFalse(Username.isValid("ab"));
        assertFalse(Username.isValid("abcdefghij01234567890"));
        assertFalse(Username.isValid("abc-d"));
        assertFalse(Username.isValid("иван"));
        assertFalse(Username.isValid("ab c"));
        assertFalse(Username.isValid("abc@"));
        assertFalse(Username.isValid(""));
        assertFalse(Username.isValid(null));
    }

    @Test public void parse() {
        assertEquals("vasya_p", Username.parse("  @Vasya_P "));
        assertNull(Username.parse("@ab"));
        assertNull(Username.parse("Вася"));
        assertNull(Username.parse(""));
        assertNull(Username.parse("a!b"));
        assertNull(Username.parse("@this_name_is_far_too_long"));
    }

    @Test public void fromCyrillicName() {
        Random r = new Random(1);
        assertEquals("ivanpetrov", Username.fromName("Иван Петров", r));
        assertEquals("shchuka", Username.fromName("Щука", r));
        assertEquals("elka", Username.fromName("Ёлка", r));
        assertEquals("yuliya", Username.fromName("Юлия", r));
        assertEquals("mariya", Username.fromName("Мария", r));
        assertEquals("pod_ezd", Username.fromName("Под_ъезд", r));
    }

    @Test public void fromLatinAndMixedName() {
        Random r = new Random(1);
        assertEquals("john.smith", Username.fromName("John.Smith", r));
        assertEquals("annaanna2", Username.fromName("Anna Анна 2", r));
        assertEquals("dzhon", Username.fromName("Джон!?", r));
        assertEquals("abc", Username.fromName("...abc...", r));
    }

    @Test public void fromNameIsCutToTwenty() {
        String u = Username.fromName("Александрийский Александр Александрович", new Random(1));
        assertEquals(20, u.length());
        assertTrue(Username.isValid(u));
        assertEquals("aleksandriyskiyaleks", u);
    }

    @Test public void shortOrEmptyResultsGetRandomUserName() {
        for (String name : new String[]{"", null, "🙂", "!!!", "Ю", "Ъь", "  "}) {
            String u = Username.fromName(name, new Random(7));
            assertTrue(name + " -> " + u, u.matches("user\\d{4}"));
            assertTrue(Username.isValid(u));
        }
        assertEquals(Username.fromName("", new Random(5)), Username.fromName("", new Random(5)));
    }

    @Test public void generatedNamesAreAlwaysValid() {
        String[] names = {"Меломан", "Кристина-Мария", "Zoë", "名前", "Ольга Ивановна Смирнова-Петрова", "x", "..", "a b"};
        for (String n : names) assertTrue(n, Username.isValid(Username.fromName(n, new Random(3))));
    }

    @Test public void cleanNameTrimsAndLimits() {
        assertEquals("Вася", Username.cleanName("  Вася \n"));
        assertEquals("", Username.cleanName("   "));
        assertEquals("", Username.cleanName(null));
        String long40 = "абвгдежзиклмнопрстуфхцчшщьыэюя1234567890";
        assertEquals(30, Username.cleanName(long40).length());
        assertEquals(long40.substring(0, 30), Username.cleanName(long40));
        // эмодзи на границе не разрезается
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 29; i++) b.append('a');
        b.append("🙂zzz");
        String cut = Username.cleanName(b.toString());
        assertEquals(29, cut.length());
        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
    }

    @Test public void initialLetter() {
        assertEquals("В", Username.initial("вася"));
        assertEquals("M", Username.initial("  "));
        assertEquals("M", Username.initial(null));
        assertEquals("🙂", Username.initial("🙂 smile"));
        assertEquals("A", Username.initial(" anna"));
    }
}
