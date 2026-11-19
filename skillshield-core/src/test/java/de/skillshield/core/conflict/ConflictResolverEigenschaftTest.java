package de.skillshield.core.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Zufallstest der Konfliktauflösung gegen eine naive Referenz (O(n²)), mit festem Startwert. */
class ConflictResolverEigenschaftTest {

    private static final long STARTWERT = 20261119L;
    private static final int TEXTLAENGE = 60;
    private static final String TEXT = "abcdefghij".repeat(6);
    private static final String[] TYPEN = {"URL", "PFAD", "EMAIL", "IP"};
    private static final double[] KONFIDENZEN = {0.30, 0.80, 0.85, 0.95, 1.0};
    private static final String[] QUELLEN = {"qa", "qb"};

    /** Unabhängige Nachbildung der Stärke-Ordnung (stärkster zuerst), nur für die Referenz. */
    private static final Comparator<Finding> STAERKE = (a, b) -> {
        int c = Double.compare(b.confidence(), a.confidence());
        if (c == 0) {
            c = Integer.compare(b.end() - b.start(), a.end() - a.start());
        }
        if (c == 0) {
            c = Integer.compare(a.start(), b.start());
        }
        if (c == 0) {
            c = Integer.compare(rang(a.type().name()), rang(b.type().name()));
        }
        if (c == 0) {
            c = a.type().name().compareTo(b.type().name());
        }
        if (c == 0) {
            c = a.source().compareTo(b.source());
        }
        return c;
    };

    private static int rang(String typ) {
        return switch (typ) {
            case "URL" -> 0;
            case "PFAD" -> 1;
            case "EMAIL" -> 2;
            case "IP" -> 3;
            default -> 4;
        };
    }

    private static boolean ueberlappen(Finding a, Finding b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static boolean enthaltenIn(Finding f, Finding g) {
        boolean umfasst = g.start() <= f.start() && g.end() >= f.end();
        boolean gleicherBereich = g.start() == f.start() && g.end() == f.end();
        return umfasst && (!gleicherBereich || STAERKE.compare(g, f) < 0);
    }

    /** Referenz: Gruppen per Überlappung, je Gruppe maximale Kandidaten, Regel aus Kap. 2.5. */
    private static List<Finding> referenz(List<Finding> kandidaten) {
        int n = kandidaten.size();
        int[] gruppe = new int[n];
        for (int i = 0; i < n; i++) {
            gruppe[i] = i;
        }
        boolean geaendert = true;
        while (geaendert) {
            geaendert = false;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (ueberlappen(kandidaten.get(i), kandidaten.get(j)) && gruppe[i] != gruppe[j]) {
                        int kleiner = Math.min(gruppe[i], gruppe[j]);
                        gruppe[i] = kleiner;
                        gruppe[j] = kleiner;
                        geaendert = true;
                    }
                }
            }
        }
        List<Finding> ergebnis = new ArrayList<>();
        for (int g = 0; g < n; g++) {
            List<Finding> maximale = new ArrayList<>();
            int start = Integer.MAX_VALUE;
            int ende = -1;
            for (int i = 0; i < n; i++) {
                if (gruppe[i] != g) {
                    continue;
                }
                Finding f = kandidaten.get(i);
                start = Math.min(start, f.start());
                ende = Math.max(ende, f.end());
                boolean enthalten = false;
                for (Finding andere : kandidaten) {
                    if (enthaltenIn(f, andere)) {
                        enthalten = true;
                        break;
                    }
                }
                if (!enthalten && !maximale.contains(f)) {
                    maximale.add(f);
                }
            }
            if (maximale.isEmpty()) {
                continue;
            }
            if (maximale.size() == 1) {
                ergebnis.add(maximale.get(0));
            } else {
                Finding staerkster = Collections.min(maximale, STAERKE);
                double schwaechste = maximale.stream().mapToDouble(Finding::confidence).min().orElseThrow();
                ergebnis.add(new Finding(staerkster.type(), TEXT.substring(start, ende), start, ende,
                        schwaechste, Finding.SOURCE_MERGED));
            }
        }
        ergebnis.sort(Comparator.comparingInt(Finding::start));
        return ergebnis;
    }

    private static List<Finding> zufallsKandidaten(Random zufall) {
        int anzahl = zufall.nextInt(31);
        List<Finding> liste = new ArrayList<>();
        for (int i = 0; i < anzahl; i++) {
            int start = zufall.nextInt(TEXTLAENGE);
            int ende = start + 1 + zufall.nextInt(Math.min(25, TEXTLAENGE - start));
            liste.add(new Finding(new FindingType(TYPEN[zufall.nextInt(TYPEN.length)]),
                    TEXT.substring(start, ende), start, ende,
                    KONFIDENZEN[zufall.nextInt(KONFIDENZEN.length)], QUELLEN[zufall.nextInt(QUELLEN.length)]));
        }
        return liste;
    }

    @Test
    void zufaelligeKandidatenmengenErfuellenSchutzgarantieUndRegel() {
        Random zufall = new Random(STARTWERT);
        Random mischer = new Random(STARTWERT + 1);
        for (int runde = 0; runde < 2_000; runde++) {
            List<Finding> kandidaten = zufallsKandidaten(zufall);
            String ort = "Startwert " + STARTWERT + ", Iteration " + runde;
            List<Finding> r = ConflictResolver.resolve(TEXT, kandidaten);

            // (1) überlappungsfrei, nach start sortiert
            for (int i = 1; i < r.size(); i++) {
                assertTrue(r.get(i - 1).end() <= r.get(i).start(), "Ordnung/Überlappung: " + ort);
            }

            // (2) abgedeckte Positionen: Vereinigung der Kandidaten (⊇ AK-7 a, ⊆ AK-7 e)
            boolean[] kand = new boolean[TEXTLAENGE];
            boolean[] erg = new boolean[TEXTLAENGE];
            for (Finding f : kandidaten) {
                for (int p = f.start(); p < f.end(); p++) {
                    kand[p] = true;
                }
            }
            for (Finding f : r) {
                for (int p = f.start(); p < f.end(); p++) {
                    erg[p] = true;
                }
            }
            for (int p = 0; p < TEXTLAENGE; p++) {
                assertEquals(kand[p], erg[p], "Abdeckung an Position " + p + ": " + ort);
            }

            // (3) jeder Fund ist ein Kandidat oder zusammengelegt, Originalwert = Teilstring
            for (Finding f : r) {
                assertTrue(kandidaten.contains(f) || Finding.SOURCE_MERGED.equals(f.source()),
                        "Herkunft: " + ort);
                assertEquals(TEXT.substring(f.start(), f.end()), f.originalValue(), "Originalwert: " + ort);
            }

            // (4) Typ und Konfidenz nach Regel: Vergleich mit der Referenz
            assertEquals(referenz(kandidaten), r, "Referenz: " + ort);

            // (5) Eingabereihenfolge ohne Einfluss
            List<Finding> gemischt = new ArrayList<>(kandidaten);
            Collections.shuffle(gemischt, mischer);
            assertEquals(r, ConflictResolver.resolve(TEXT, gemischt), "Reihenfolge: " + ort);
        }
    }
}
