package de.skillshield.core.model;

import java.util.Objects;

/**
 * Ein Fund in einem Text.
 *
 * <p>Konvention der Positionen: UTF-16-Indizes wie bei {@link String#substring(int, int)}, {@code start}
 * inklusive, {@code end} exklusive. Invariante gegenüber dem gescannten Text:
 * {@code text.substring(start, end).equals(originalValue)}.
 *
 * <p>{@link #toString()} und alle Fehlermeldungen enthalten den Originalwert nicht.
 *
 * @param type          Typ des Fundes
 * @param originalValue Originalwert (Klartext, nicht in Ausgaben verwenden)
 * @param start         Startposition, inklusive, mindestens 0
 * @param end           Endposition, exklusive, größer als {@code start}
 * @param confidence    Konfidenz von 0.0 bis 1.0
 * @param source        Quelle (Erkennername, {@link #SOURCE_MANUAL} oder {@link #SOURCE_MERGED}),
 *                      Format {@code [a-z][a-z0-9-]{0,31}}
 */
public record Finding(FindingType type, String originalValue, int start, int end,
                      double confidence, String source) {

    /** Quelle für vom Nutzer markierte Funde. */
    public static final String SOURCE_MANUAL = "manuell";

    /**
     * Quelle eines vom ConflictResolver aus teilweise überlappenden Kandidaten zusammengelegten Fundes; die
     * Konfidenz ist die des schwächsten beteiligten Glieds. Erkenner verwenden diese Quelle nicht.
     */
    public static final String SOURCE_MERGED = "zusammengelegt";

    /** Höchstlänge der Quelle. */
    public static final int MAX_SOURCE_LENGTH = 32;

    /**
     * Quelle muss dem Format {@code [a-z][a-z0-9-]{0,31}} entsprechen (nur ASCII, z. B. {@code email},
     * {@code pfad-laufwerk}, {@code manuell}); geprüft per Zeichenschleife, damit {@link #toString()} und
     * Auswertungen keinen frei wählbaren Text enthalten.
     */
    private static boolean isValidSource(String s) {
        int len = s.length();
        if (len == 0 || len > MAX_SOURCE_LENGTH) {
            return false;
        }
        char first = s.charAt(0);
        if (first < 'a' || first > 'z') {
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
                return false;
            }
        }
        return true;
    }

    public Finding {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(originalValue, "originalValue");
        Objects.requireNonNull(source, "source");
        if (!isValidSource(source)) {
            throw new IllegalArgumentException("Ungültige Quelle: erlaubt sind 1 bis " + MAX_SOURCE_LENGTH
                    + " Zeichen im Format [a-z][a-z0-9-]*");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start darf nicht negativ sein");
        }
        if (end <= start) {
            throw new IllegalArgumentException("end muss größer als start sein");
        }
        if (originalValue.length() != end - start) {
            throw new IllegalArgumentException("Länge des Originalwerts passt nicht zu start und end");
        }
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("confidence muss im Bereich 0.0 bis 1.0 liegen");
        }
    }

    /** Manueller Fund: Konfidenz 1.0, Quelle {@link #SOURCE_MANUAL}. */
    public static Finding manual(FindingType type, String text, int start, int end) {
        return new Finding(type, text, start, end, 1.0, SOURCE_MANUAL);
    }

    /** Stufe der Konfidenz dieses Fundes. */
    public ConfidenceLevel level() {
        return ConfidenceLevel.of(confidence);
    }

    @Override
    public String toString() {
        return "Finding[type=" + type.name() + ", start=" + start + ", end=" + end
                + ", confidence=" + confidence + ", level=" + level() + ", source=" + source + "]";
    }
}
