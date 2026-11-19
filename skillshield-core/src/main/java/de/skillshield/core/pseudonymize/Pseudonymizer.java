package de.skillshield.core.pseudonymize;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ersetzt genau die übergebenen Funde durch Platzhalter {@code [[TYP_n]]} (Architektur-Plan Kap. 2.6).
 *
 * <p>Zustandslos: der Zähler gilt je Aufruf. Die Nummer {@code n} zählt über alle Typen fortlaufend ab 1 in
 * Textreihenfolge; gleiche Originalwerte desselben Typs (zeichengleich) erhalten denselben Platzhalter.
 *
 * <p>Sicherheit per Konstruktion: Die Ausgabe entsteht in einem einzigen Durchlauf über die nach {@code start}
 * sortierten, überlappungsfreien Funde. Zwischen den Funden wird der Text unverändert aus {@code text}
 * kopiert, jeder Fundbereich wird nur durch den Platzhalter ersetzt. Der Fundbereich selbst wird nie kopiert.
 *
 * <p>Stand T-6: Der Literalschutz ({@code [[TEXT_n]]}, T-9) und die vollständige Eingabeprüfung mit
 * {@code InvalidFindingException} (T-7) fehlen noch; die hier vorhandene Prüfung ist eine Schutzvorkehrung
 * mit {@link IllegalArgumentException} ohne Klartext in der Meldung.
 */
public final class Pseudonymizer {

    private static final Comparator<Finding> BY_POSITION =
            Comparator.comparingInt(Finding::start).thenComparingInt(Finding::end);

    /**
     * @param text     Text, nicht {@code null}
     * @param findings Funde, beliebige Reihenfolge, nicht {@code null}
     * @throws NullPointerException     bei {@code null} als Text oder Liste (Meldung: Parametername)
     * @throws IllegalArgumentException bei {@code null}-Element, Fund außerhalb des Textes, abweichendem
     *                                  Originalwert oder Überlappung (Meldung nur mit Listenindex)
     */
    public PseudonymizationResult pseudonymize(String text, List<Finding> findings) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(findings, "findings");

        List<Finding> sorted = validatedAndSorted(text, findings);

        StringBuilder out = new StringBuilder(text.length());
        Map<String, Map<String, Integer>> numbers = new HashMap<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        int counter = 0;
        int pos = 0;
        for (Finding f : sorted) {
            out.append(text, pos, f.start());
            Map<String, Integer> perType = numbers.computeIfAbsent(f.type().name(), k -> new HashMap<>());
            Integer n = perType.get(f.originalValue());
            if (n == null) {
                counter++;
                n = counter;
                perType.put(f.originalValue(), n);
                mapping.put(PlaceholderSyntax.format(f.type(), n), f.originalValue());
            }
            out.append(PlaceholderSyntax.format(f.type(), n));
            pos = f.end();
        }
        out.append(text, pos, text.length());
        return new PseudonymizationResult(out.toString(), PlaceholderMapping.of(mapping));
    }

    private static List<Finding> validatedAndSorted(String text, List<Finding> findings) {
        List<Finding> copy = new ArrayList<>(findings.size());
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            if (f == null) {
                throw invalid(i, "NULL_ELEMENT");
            }
            if (f.end() > text.length()) {
                throw invalid(i, "OUT_OF_RANGE");
            }
            if (!text.regionMatches(f.start(), f.originalValue(), 0, f.originalValue().length())) {
                throw invalid(i, "VALUE_MISMATCH");
            }
            copy.add(f);
        }
        copy.sort(BY_POSITION);
        for (int i = 1; i < copy.size(); i++) {
            if (copy.get(i).start() < copy.get(i - 1).end()) {
                throw new IllegalArgumentException("Ungültige Fundliste: OVERLAP");
            }
        }
        return copy;
    }

    private static IllegalArgumentException invalid(int index, String reason) {
        return new IllegalArgumentException("Ungültiger Fund an Listenindex " + index + ": " + reason);
    }
}
