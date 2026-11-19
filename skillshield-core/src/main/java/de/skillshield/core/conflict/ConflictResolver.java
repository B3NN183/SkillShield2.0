package de.skillshield.core.conflict;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Macht eine Kandidatenliste überlappungsfrei, ohne eine von einem Kandidaten abgedeckte Position aus dem
 * Ergebnis zu nehmen (Schutzgarantie, D-8).
 *
 * <p><b>Begriffe.</b> Zwei Kandidaten überlappen, wenn sie mindestens ein Zeichen teilen; aneinandergrenzende
 * Kandidaten überlappen nicht. Ein Kandidat ist enthalten, wenn ein anderer seinen Bereich ganz umfasst; bei
 * gleichem Bereich gilt der schwächere (Ordnung {@code STAERKE}) als im stärkeren enthalten. Maximal ist ein
 * Kandidat, der nicht enthalten ist.
 *
 * <p><b>Stärke.</b> Konfidenz absteigend, dann Länge absteigend, dann Start aufsteigend, dann Typrang
 * ({@code URL} vor {@code PFAD} vor {@code EMAIL} vor {@code IP} vor übrigen), Typname, Quelle (die
 * letzten beiden nur für Totalität und Determinismus). Kandidaten, die auch in der Quelle übereinstimmen, haben
 * gleichen Bereich im selben Text und damit gleichen Originalwert; sie sind austauschbar und das Ergebnis
 * bleibt {@code equals}-gleich.
 *
 * <p><b>Regel.</b> Kandidaten, die sich (auch über eine Kette) überlappen, bilden eine Gruppe. Enthaltene Kandidaten
 * gehen im umschließenden auf und bestimmen weder Typ noch Konfidenz noch Quelle. Hat eine Gruppe genau einen
 * maximalen Kandidaten, wird dieser unverändert übernommen (Typ, Konfidenz, Stufe und Quelle des umschließenden,
 * unabhängig von der Konfidenz der enthaltenen). Hat sie mehrere maximale Kandidaten (Teilüberlappung, beliebig
 * lange Kette), entsteht ein Fund über die Vereinigung: Typ des stärksten maximalen Kandidaten, Konfidenz des
 * schwächsten maximalen Kandidaten, Quelle {@link Finding#SOURCE_MERGED}, Originalwert
 * {@code text.substring(start, end)}.
 *
 * <p>Das Ergebnis ist unveränderlich, aufsteigend nach {@code start} sortiert, überlappungsfrei und unabhängig von
 * der Reihenfolge der Eingabe. Laufzeit O(n log n), ohne Rekursion.
 */
public final class ConflictResolver {

    /** Totale Ordnung: stärkster Kandidat zuerst. */
    private static final Comparator<Finding> STAERKE = Comparator
            .comparingDouble(Finding::confidence).reversed()
            .thenComparing(Comparator.comparingInt(ConflictResolver::length).reversed())
            .thenComparingInt(Finding::start)
            .thenComparingInt(f -> typeRank(f.type()))
            .thenComparing(f -> f.type().name())
            .thenComparing(Finding::source);

    private static final Comparator<Finding> SORTIERUNG = Comparator
            .comparingInt(Finding::start)
            .thenComparing(Comparator.comparingInt(Finding::end).reversed())
            .thenComparing(STAERKE);

    private ConflictResolver() {
    }

    /**
     * Macht die Kandidaten überlappungsfrei (Regeln siehe Klassenbeschreibung).
     *
     * @param text       der gescannte Text, nicht {@code null}
     * @param candidates Kandidaten, nicht {@code null}, ohne {@code null}-Elemente; wird nicht verändert
     * @return unveränderliche Liste, aufsteigend nach {@code start}
     * @throws NullPointerException     bei {@code null} als Text ({@code text}), Liste ({@code candidates}) oder
     *                                  Element ({@code candidates enthält null})
     * @throws IllegalArgumentException wenn ein Kandidat nicht zum Text passt; die Meldung nennt nur den
     *                                  Listenindex, keinen Wert
     */
    public static List<Finding> resolve(String text, List<Finding> candidates) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(candidates, "candidates");
        List<Finding> sorted = new ArrayList<>(candidates.size());
        int index = 0;
        for (Finding f : candidates) {
            Objects.requireNonNull(f, "candidates enthält null");
            // regionMatches liefert false, wenn der Bereich über das Textende hinausreicht
            if (!text.regionMatches(f.start(), f.originalValue(), 0, f.originalValue().length())) {
                throw new IllegalArgumentException("Kandidat an Listenindex " + index + " passt nicht zum Text");
            }
            sorted.add(f);
            index++;
        }
        sorted.sort(SORTIERUNG);

        List<Finding> result = new ArrayList<>();
        List<Finding> members = new ArrayList<>();
        int groupStart = 0;
        int groupEnd = 0;
        for (Finding f : sorted) {
            if (members.isEmpty() || f.start() >= groupEnd) {
                closeGroup(text, groupStart, groupEnd, members, result);
                members.clear();
                members.add(f);
                groupStart = f.start();
                groupEnd = f.end();
            } else if (f.end() > groupEnd) {
                members.add(f);
                groupEnd = f.end();
            }
            // sonst: f liegt im letzten Glied und geht darin auf
        }
        closeGroup(text, groupStart, groupEnd, members, result);
        return List.copyOf(result);
    }

    private static void closeGroup(String text, int start, int end, List<Finding> members, List<Finding> result) {
        if (members.isEmpty()) {
            return;
        }
        if (members.size() == 1) {
            result.add(members.get(0));
            return;
        }
        Finding strongest = members.get(0);
        double weakest = strongest.confidence();
        for (Finding m : members) {
            if (STAERKE.compare(m, strongest) < 0) {
                strongest = m;
            }
            weakest = Math.min(weakest, m.confidence());
        }
        result.add(new Finding(strongest.type(), text.substring(start, end), start, end, weakest,
                Finding.SOURCE_MERGED));
    }

    private static int length(Finding f) {
        return f.end() - f.start();
    }

    private static int typeRank(FindingType type) {
        if (type.equals(FindingType.URL)) {
            return 0;
        }
        if (type.equals(FindingType.PFAD)) {
            return 1;
        }
        if (type.equals(FindingType.EMAIL)) {
            return 2;
        }
        if (type.equals(FindingType.IP)) {
            return 3;
        }
        return 4;
    }
}
