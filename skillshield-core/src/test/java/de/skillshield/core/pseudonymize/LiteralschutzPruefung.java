package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Gemeinsame Prüfung für Literalschutz-Tests: Roundtrip, Platzhalter-Invariante (Positionen), Determinismus.
 * Das Orakel baut die erwartete Folge aus Textstücken und Platzhaltern aus den Lücken der Funde und vergleicht sie
 * mit der Tokenisierung des Ergebnisses.
 */
final class LiteralschutzPruefung {

    private LiteralschutzPruefung() {
    }

    /** Prüft alle Eigenschaften für einen Fall und liefert das Ergebnis zurück. */
    static PseudonymizationResult pruefe(String text, List<Finding> funde, Random mischer, String kontext) {
        Pseudonymizer p = new Pseudonymizer();
        PseudonymizationResult r = p.pseudonymize(text, funde);

        // (d) Determinismus, auch bei anderer Reihenfolge der Eingabe
        assertEquals(r, p.pseudonymize(text, funde), kontext);
        List<Finding> gemischt = new ArrayList<>(funde);
        Collections.shuffle(gemischt, mischer);
        assertEquals(r.text(), p.pseudonymize(text, gemischt).text(), kontext);

        // (a) Roundtrip zeichengleich, ohne Meldung
        ReidentificationResult rr = new Reidentifier().restore(r.text(), r.mapping());
        assertEquals(text, rr.text(), kontext);
        assertTrue(rr.unresolved().isEmpty(), kontext);

        // (b) und (c) Invariante mit Positionen und Platzhalter-Zuordnung je Platz
        List<Finding> sortiert = new ArrayList<>(funde);
        sortiert.sort(Comparator.comparingInt(Finding::start));
        Set<String> known = new HashSet<>();
        for (Finding f : sortiert) {
            known.add(f.type().name());
        }
        boolean schutz = false;
        int pos = 0;
        for (Finding f : sortiert) {
            schutz |= !PlaceholderSyntax.tokenize(text, pos, f.start(), known).isEmpty();
            pos = f.end();
        }
        schutz |= !PlaceholderSyntax.tokenize(text, pos, text.length(), known).isEmpty();
        Set<String> mitText = new HashSet<>(known);
        if (schutz) {
            mitText.add(FindingType.TEXT.name());
        }
        List<String> literale = new ArrayList<>();
        List<String> originale = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        pos = 0;
        for (Finding f : sortiert) {
            luecke(text, pos, f.start(), mitText, cur, literale, originale);
            literale.add(cur.toString());
            cur.setLength(0);
            originale.add(f.originalValue());
            pos = f.end();
        }
        luecke(text, pos, text.length(), mitText, cur, literale, originale);
        literale.add(cur.toString());

        List<PlaceholderSyntax.Token> tokens =
                PlaceholderSyntax.tokenize(r.text(), 0, r.text().length(), r.mapping().typeNames());
        assertEquals(originale.size(), tokens.size(), kontext);
        int ende = 0;
        for (int i = 0; i < tokens.size(); i++) {
            PlaceholderSyntax.Token t = tokens.get(i);
            assertEquals(PlaceholderSyntax.Token.Kind.PLATZHALTER, t.kind(), kontext);
            assertEquals(literale.get(i), r.text().substring(ende, t.start()), kontext);
            assertNotNull(t.canonical());
            assertEquals(r.text().substring(t.start(), t.end()), t.canonical(), kontext);
            assertEquals(originale.get(i), r.mapping().resolve(t.canonical()).orElseThrow(), kontext);
            ende = t.end();
        }
        assertEquals(literale.get(tokens.size()), r.text().substring(ende), kontext);
        return r;
    }

    private static void luecke(String text, int from, int to, Set<String> known, StringBuilder cur,
            List<String> literale, List<String> originale) {
        int pos = from;
        for (PlaceholderSyntax.Token t : PlaceholderSyntax.tokenize(text, from, to, known)) {
            cur.append(text, pos, t.start());
            literale.add(cur.toString());
            cur.setLength(0);
            originale.add(text.substring(t.start(), t.end()));
            pos = t.end();
        }
        cur.append(text, pos, to);
    }
}
