package de.skillshield.core;

/** Testhilfe für Texte mit Backslashes (Architektur-Plan Kap. 2.10). */
public final class TestTexte {

    /** Platzhalterzeichen für einen Backslash in Testtexten: U+00A6. */
    public static final char BS_MARKER = '¦';

    private TestTexte() {
    }

    /** Ersetzt jedes {@code ¦} (U+00A6) durch genau einen Backslash. */
    public static String bs(String s) {
        return s.replace(BS_MARKER, '\\');
    }
}
