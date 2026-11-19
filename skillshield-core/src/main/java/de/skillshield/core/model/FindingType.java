package de.skillshield.core.model;

import java.util.Objects;

/**
 * Typname eines Fundes, z. B. {@code PFAD}. Format {@code [A-Z][A-Z0-9]{0,31}} (nur ASCII): kein Unterstrich,
 * damit ein Platzhalter {@code [[TYP_n]]} eindeutig zerlegbar ist.
 *
 * <p>Die Fehlermeldung bei ungültigem Namen wiederholt den Namen nicht.
 *
 * @param name der Typname
 */
public record FindingType(String name) {

    /** Höchstlänge des Typnamens. */
    public static final int MAX_LENGTH = 32;

    public static final FindingType PFAD = new FindingType("PFAD");
    public static final FindingType EMAIL = new FindingType("EMAIL");
    public static final FindingType IP = new FindingType("IP");
    public static final FindingType URL = new FindingType("URL");
    /** Literalschutz für platzhalterähnlichen Originaltext. */
    public static final FindingType TEXT = new FindingType("TEXT");

    public FindingType {
        Objects.requireNonNull(name, "name");
        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                    "Ungültiger Typname: erlaubt sind 1 bis " + MAX_LENGTH
                            + " Zeichen im Format [A-Z][A-Z0-9]*");
        }
    }

    /** Erstes Zeichen eines Typnamens: {@code A-Z} (ASCII). Einzige Definition, auch für den Platzhalter-Tokenizer. */
    public static boolean isNameStart(char c) {
        return c >= 'A' && c <= 'Z';
    }

    /** Folgezeichen eines Typnamens: {@code A-Z} oder {@code 0-9} (ASCII). Einzige Definition. */
    public static boolean isNameChar(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }

    /** Prüft {@code [A-Z][A-Z0-9]{0,31}}; {@code s} nicht {@code null}. */
    public static boolean isValidName(String s) {
        int len = s.length();
        if (len == 0 || len > MAX_LENGTH || !isNameStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < len; i++) {
            if (!isNameChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
