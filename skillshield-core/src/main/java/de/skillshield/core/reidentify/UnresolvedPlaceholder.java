package de.skillshield.core.reidentify;

import java.util.Objects;

/**
 * Ein Platzhalter im zurückkommenden Text, der nicht aufgelöst wurde. Enthält nur den Ausschnitt aus dem
 * zurückkommenden Text (Klammern, ASCII-Buchstaben und -Ziffern, {@code _}, Leerzeichen, höchstens 62 Zeichen),
 * nie einen Originalwert.
 *
 * @param token    der Ausschnitt, unverändert
 * @param position Beginn im zurückkommenden Text (Index in {@code char}-Einheiten)
 * @param reason   Grund
 */
public record UnresolvedPlaceholder(String token, int position, Reason reason) {

    public enum Reason {
        /** Wohlgeformt, aber nicht in der Zuordnung. */
        UNBEKANNT,
        /** Beschädigt (z. B. {@code [[PFAD_1]}), nicht tolerierte Form. */
        BESCHAEDIGT
    }

    public UnresolvedPlaceholder {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(reason, "reason");
        if (position < 0) {
            throw new IllegalArgumentException("position darf nicht negativ sein");
        }
    }
}
