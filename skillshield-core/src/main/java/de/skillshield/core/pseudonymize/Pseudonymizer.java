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
 * <p>Die Eingabe wird vollständig geprüft, bevor etwas erzeugt wird (T-7); ungültige Funde führen zu einer
 * {@link InvalidFindingException}. Stand T-7: Der Literalschutz ({@code [[TEXT_n]]}, T-9) fehlt noch.
 */
public final class Pseudonymizer {

    private static final Comparator<Entry> BY_POSITION =
            Comparator.<Entry>comparingInt(e -> e.finding().start())
                    .thenComparingInt(e -> e.finding().end())
                    .thenComparingInt(Entry::index);

    /**
     * @param text     Text, nicht {@code null}
     * @param findings Funde, beliebige Reihenfolge, nicht {@code null}
     * @throws NullPointerException     bei {@code null} als Text oder Liste (Meldung: Parametername)
     * @throws InvalidFindingException  bei {@code null}-Element, Fund außerhalb des Textes, abweichendem
     *                                  Originalwert, getrenntem Surrogatpaar oder Überlappung (Meldung nur mit
     *                                  Listenindex und Grund)
     */
    public PseudonymizationResult pseudonymize(String text, List<Finding> findings) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(findings, "findings");

        List<Entry> sorted = validatedAndSorted(text, findings);

        StringBuilder out = new StringBuilder(text.length());
        Map<String, Map<String, Integer>> numbers = new HashMap<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        int counter = 0;
        int pos = 0;
        for (Entry entry : sorted) {
            Finding f = entry.finding();
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

    /** Fund mit seinem Index in der übergebenen Liste. */
    private record Entry(Finding finding, int index) { }

    /**
     * Prüft die gesamte Liste, bevor etwas erzeugt wird. Reihenfolge (Plan Kap. 2.6): Elemente in Listenreihenfolge,
     * je Element NULL_ELEMENT, OUT_OF_RANGE, VALUE_MISMATCH, SPLITS_SURROGATE_PAIR; das erste fehlerhafte Element
     * gewinnt. Danach die Überlappungsprüfung nach Sortierung (Aufwand O(n log n)); gemeldet wird der in
     * Textreihenfolge spätere der beiden Funde mit seinem Index in der übergebenen Liste.
     */
    private static List<Entry> validatedAndSorted(String text, List<Finding> findings) {
        int len = text.length();
        List<Entry> entries = new ArrayList<>(findings.size());
        for (int i = 0; i < findings.size(); i++) {
            Finding f = findings.get(i);
            if (f == null) {
                throw new InvalidFindingException(InvalidFindingException.Reason.NULL_ELEMENT, i);
            }
            if (f.end() > len) {
                throw new InvalidFindingException(InvalidFindingException.Reason.OUT_OF_RANGE, i);
            }
            if (!text.regionMatches(f.start(), f.originalValue(), 0, f.originalValue().length())) {
                throw new InvalidFindingException(InvalidFindingException.Reason.VALUE_MISMATCH, i);
            }
            if (splitsSurrogatePair(text, f.start()) || splitsSurrogatePair(text, f.end())) {
                throw new InvalidFindingException(InvalidFindingException.Reason.SPLITS_SURROGATE_PAIR, i);
            }
            entries.add(new Entry(f, i));
        }
        // Stabile Sortierung: bei gleicher Position entscheidet der Listenindex, die Meldung ist deterministisch.
        entries.sort(BY_POSITION);
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).finding().start() < entries.get(i - 1).finding().end()) {
                throw new InvalidFindingException(InvalidFindingException.Reason.OVERLAP, entries.get(i).index());
            }
        }
        return entries;
    }

    /** {@code true}, wenn die Grenze vor Position {@code pos} zwischen High- und Low-Surrogat liegt. */
    private static boolean splitsSurrogatePair(String text, int pos) {
        return pos > 0 && pos < text.length()
                && Character.isHighSurrogate(text.charAt(pos - 1))
                && Character.isLowSurrogate(text.charAt(pos));
    }
}
