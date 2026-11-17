package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TestTexteTest {

    private static long zaehle(String s, char c) {
        return s.chars().filter(x -> x == c).count();
    }

    @Test
    void einfacherPfadHatEinenBackslashProMarker() {
        String s = bs("C:¦Tools¦BeispielTool¦tool.exe");
        assertEquals("C:\\Tools\\BeispielTool\\tool.exe", s);
        assertEquals(3, zaehle(s, '\\'));
    }

    @Test
    void doppelteMarkerSindDoppelteBackslashes() {
        String s = bs("C:¦¦Tools¦¦x");
        assertEquals(4, zaehle(s, '\\'));
        assertEquals("C:\\\\Tools\\\\x", s);
    }

    @Test
    void uncZweiUndVierBackslashes() {
        assertEquals(3, zaehle(bs("¦¦srv-beispiel¦freigabe"), '\\'));
        String vier = bs("¦¦¦¦srv-beispiel¦¦freigabe");
        assertEquals(6, zaehle(vier, '\\'));
        assertEquals("\\\\\\\\srv-beispiel\\\\freigabe", vier);
    }

    @Test
    void ergebnisEnthaeltKeinMarkerzeichenMehr() {
        assertFalse(bs("¦a¦¦b¦").indexOf('\u00A6') >= 0);
    }

    @Test
    void textOhneMarkerBleibtUnveraendert() {
        assertEquals("C:/Tools/a.exe", bs("C:/Tools/a.exe"));
        assertEquals("", bs(""));
    }

    @Test
    void vorhandeneBackslashesBleibenErhalten() {
        assertEquals("a\\b\\c", bs("a\\b¦c"));
    }
}
