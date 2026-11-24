package de.skillshield.ui.logik;

/** Gemeinsame Prüfung auf leeren oder nur aus Leerraum bestehenden Text (Scan-Eingabe und Markierung). */
final class Leerraum {

    private Leerraum() {
    }

    /** {@code true}, wenn der Text leer ist oder nur aus Leerraum (auch Unicode-Leerzeichen) besteht. */
    static boolean istLeer(String text) {
        return text.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }
}
