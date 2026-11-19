package de.skillshield.core.reidentify;

import java.util.List;
import java.util.Objects;

/**
 * Ergebnis der Rückübersetzung.
 *
 * <p>{@code text} enthält die eingesetzten Originalwerte und damit Klartext; {@link #toString()} zeigt ihn nicht.
 *
 * @param text       rückübersetzter Text
 * @param unresolved nicht aufgelöste Platzhalter in Textreihenfolge, unveränderlich
 */
public record ReidentificationResult(String text, List<UnresolvedPlaceholder> unresolved) {

    public ReidentificationResult {
        Objects.requireNonNull(text, "text");
        unresolved = List.copyOf(Objects.requireNonNull(unresolved, "unresolved"));
    }

    @Override
    public String toString() {
        return "ReidentificationResult[textLength=" + text.length() + ", unresolved=" + unresolved.size() + "]";
    }
}
