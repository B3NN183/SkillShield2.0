package de.skillshield.core.pseudonymize;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ersetzt genau die übergebenen Funde durch Platzhalter {@code [[TYP_n]]} (Architektur-Plan Kap. 2.6, 2.7).
 *
 * <p>Zustandslos: der Zähler gilt je Aufruf. Die Nummer {@code n} zählt über alle Typen fortlaufend ab 1 in
 * Textreihenfolge; gleiche Originalwerte desselben Typs (zeichengleich) erhalten denselben Platzhalter.
 *
 * <p>Sicherheit per Konstruktion: Die Ausgabe entsteht in einem einzigen Durchlauf über die nach {@code start}
 * sortierten, überlappungsfreien Funde. Zwischen den Funden wird der Text unverändert aus {@code text}
 * kopiert, jeder Fundbereich wird nur durch den Platzhalter ersetzt. Der Fundbereich selbst wird nie kopiert.
 *
 * <p>Die Eingabe wird vollständig geprüft, bevor etwas erzeugt wird (T-7); ungültige Funde führen zu einer
 * {@link InvalidFindingException}.
 *
 * <p><b>Literalschutz (T-9):</b> Die Lücken zwischen den Funden werden mit {@link PlaceholderSyntax#tokenize}
 * durchsucht. Die Typnamen der übergebenen Funde gelten als bekannt; findet sich in den Lücken mindestens ein Token
 * (vollständiger Platzhalter oder beschädigte Form eines bekannten Typs), kommt {@code TEXT} hinzu und es wird mit
 * dieser Menge erneut tokenisiert. Jedes Token wird wie ein Fund vom Typ {@code TEXT} behandelt: Es erhält
 * {@code [[TEXT_n]]} (gleiche Nummerierung und Konsistenzregel wie die übrigen Funde) und kommt bei der
 * Rückübersetzung unverändert zurück. Funde werden dadurch nie verdrängt oder verkleinert: Nur die Lücken werden
 * geschützt, ein Fund, der eine Platzhalterform ganz oder teilweise überdeckt, bleibt ersetzt, die in den Lücken
 * verbleibenden Reste werden nach denselben Regeln geschützt oder bleiben Text. Weil kein Token einer Lücke über
 * deren Ende hinausreichen kann (ein erzeugter Platzhalter beginnt mit {@code [}, das kein Token fortsetzt), liefert
 * {@code tokenize} auf dem Ergebnis mit den Typnamen der Zuordnung genau die erzeugten Platzhalter.
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
        Set<String> known = literalTypes(text, sorted);

        StringBuilder out = new StringBuilder(text.length());
        Map<String, Map<String, Integer>> numbers = new HashMap<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        int[] counter = {0};
        int pos = 0;
        for (Entry entry : sorted) {
            Finding f = entry.finding();
            pos = appendGap(text, pos, f.start(), known, out, numbers, mapping, counter);
            appendPlaceholder(f.type(), f.originalValue(), out, numbers, mapping, counter);
            pos = f.end();
        }
        appendGap(text, pos, text.length(), known, out, numbers, mapping, counter);
        return new PseudonymizationResult(out.toString(), PlaceholderMapping.of(mapping));
    }

    /**
     * Typnamen, mit denen die Lücken tokenisiert werden: die der Funde, dazu {@code TEXT}, sobald die Lücken ein
     * Token enthalten. {@code null} bedeutet: kein Literalschutz nötig.
     */
    private static Set<String> literalTypes(String text, List<Entry> sorted) {
        Set<String> known = new HashSet<>();
        for (Entry e : sorted) {
            known.add(e.finding().type().name());
        }
        int pos = 0;
        boolean found = false;
        for (Entry e : sorted) {
            if (!PlaceholderSyntax.tokenize(text, pos, e.finding().start(), known).isEmpty()) {
                found = true;
                break;
            }
            pos = e.finding().end();
        }
        if (!found && PlaceholderSyntax.tokenize(text, pos, text.length(), known).isEmpty()) {
            return null;
        }
        known.add(FindingType.TEXT.name());
        return known;
    }

    /** Hängt die Lücke {@code [from, to)} an, jedes Token als {@code TEXT}-Platzhalter. Liefert {@code to}. */
    private static int appendGap(String text, int from, int to, Set<String> known, StringBuilder out,
            Map<String, Map<String, Integer>> numbers, Map<String, String> mapping, int[] counter) {
        if (known == null) {
            out.append(text, from, to);
            return to;
        }
        int pos = from;
        for (PlaceholderSyntax.Token token : PlaceholderSyntax.tokenize(text, from, to, known)) {
            out.append(text, pos, token.start());
            appendPlaceholder(FindingType.TEXT, text.substring(token.start(), token.end()), out, numbers, mapping,
                    counter);
            pos = token.end();
        }
        out.append(text, pos, to);
        return to;
    }

    private static void appendPlaceholder(FindingType type, String value, StringBuilder out,
            Map<String, Map<String, Integer>> numbers, Map<String, String> mapping, int[] counter) {
        Map<String, Integer> perType = numbers.computeIfAbsent(type.name(), k -> new HashMap<>());
        Integer n = perType.get(value);
        if (n == null) {
            counter[0]++;
            n = counter[0];
            perType.put(value, n);
            mapping.put(PlaceholderSyntax.format(type, n), value);
        }
        out.append(PlaceholderSyntax.format(type, n));
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
