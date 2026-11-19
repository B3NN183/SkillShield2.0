package de.skillshield.core.pseudonymize;

import java.util.Objects;

/**
 * Ergebnis der Pseudonymisierung.
 *
 * <p>Der Text kann Klartext enthalten (nicht erkannte oder abgelehnte Stellen); {@link #toString()} zeigt ihn
 * deshalb nicht.
 *
 * @param text    pseudonymisierter Text
 * @param mapping Zuordnung Platzhalter zu Originalwert, nur für tatsächlich ersetzte Werte
 */
public record PseudonymizationResult(String text, PlaceholderMapping mapping) {

    public PseudonymizationResult {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(mapping, "mapping");
    }

    @Override
    public String toString() {
        return "PseudonymizationResult[textLength=" + text.length() + ", placeholders=" + mapping.size() + "]";
    }
}
