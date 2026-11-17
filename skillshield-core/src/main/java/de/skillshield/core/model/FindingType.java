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
        if (!isValid(name)) {
            throw new IllegalArgumentException(
                    "Ungültiger Typname: erlaubt sind 1 bis " + MAX_LENGTH
                            + " Zeichen im Format [A-Z][A-Z0-9]*");
        }
    }

    private static boolean isValid(String s) {
        int len = s.length();
        if (len == 0 || len > MAX_LENGTH) {
            return false;
        }
        char first = s.charAt(0);
        if (first < 'A' || first > 'Z') {
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
