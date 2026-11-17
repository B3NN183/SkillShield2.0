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
 * @param source        Quelle (Erkennername oder {@link #SOURCE_MANUAL})
 */
public record Finding(FindingType type, String originalValue, int start, int end,
                      double confidence, String source) {

    /** Quelle für vom Nutzer markierte Funde. */
    public static final String SOURCE_MANUAL = "manuell";

    public Finding {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(originalValue, "originalValue");
        Objects.requireNonNull(source, "source");
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
