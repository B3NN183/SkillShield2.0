package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Eigenschaftstests mit festem Startwert (Architektur-Plan, Lehren aus T-3 bis T-5). */
class PseudonymizerEigenschaftTest {

    private static final long SEED = 20261119L;
    private static final Pattern PLATZHALTER = Pattern.compile("\\[\\[[A-Z0-9]+_[1-9][0-9]*]]");
    private static final String[] WERTE = {"erika.beispiel@example.invalid", "192.0.2.10", "198.51.100.7",
        "https://intranet.example/a/b", "C:\\Tools\\a.exe", "@example.org", "Erika Mustermann", "x"};
    private static final String[] TEXTE = {"", " ", "Start", "mit \uD83D\uDE00 Emoji", "e\u0301", "\t", "\r\n",
        "$1", "\\1", "a.b", ".", "Zeile\nzwei"};
    private static final FindingType[] TYPEN = {FindingType.EMAIL, FindingType.IP, FindingType.URL,
        FindingType.PFAD, new FindingType("PERSON")};

    private record Fall(String text, List<Finding> funde, List<String> luecken) {
    }

    private static Fall baue(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<Finding> funde = new ArrayList<>();
        List<String> luecken = new ArrayList<>();
        int anzahl = rnd.nextInt(12);
        for (int i = 0; i <= anzahl; i++) {
            StringBuilder luecke = new StringBuilder();
            int teile = rnd.nextInt(3);
            for (int k = 0; k < teile; k++) {
                luecke.append(TEXTE[rnd.nextInt(TEXTE.length)]);
            }
            sb.append(luecke);
            luecken.add(luecke.toString());
            if (i == anzahl) {
                break;
            }
            String wert = WERTE[rnd.nextInt(WERTE.length)];
            int s = sb.length();
            sb.append(wert);
            funde.add(new Finding(TYPEN[rnd.nextInt(TYPEN.length)], wert, s, sb.length(), 0.9, "manuell"));
        }
        return new Fall(sb.toString(), funde, luecken);
    }

    @Test
    void zufallstextEigenschaften() {
        Random rnd = new Random(SEED);
        Pseudonymizer p = new Pseudonymizer();
        for (int lauf = 0; lauf < 3000; lauf++) {
            Fall fall = baue(rnd);
            List<Finding> gemischt = new ArrayList<>(fall.funde());
            Collections.shuffle(gemischt, rnd);
            PseudonymizationResult r = p.pseudonymize(fall.text(), gemischt);

            // (a) Ausserhalb der Platzhalter steht nur Text, der vorher ausserhalb der Funde stand
            String ohnePlatzhalter = PLATZHALTER.matcher(r.text()).replaceAll("\u0000");
            String[] stuecke = ohnePlatzhalter.split("\u0000", -1);
            assertEquals(fall.luecken().size(), stuecke.length, "Lauf " + lauf);
            for (int i = 0; i < stuecke.length; i++) {
                assertEquals(fall.luecken().get(i), stuecke[i], "Lauf " + lauf);
            }

            // (b) gleiche (Typ, Wert) -> gleicher Platzhalter, verschiedene -> verschiedene; n global ab 1
            Matcher m = PLATZHALTER.matcher(r.text());
            Map<String, String> schluesselZuPlatzhalter = new HashMap<>();
            Map<String, String> platzhalterZuSchluessel = new HashMap<>();
            int naechste = 1;
            for (Finding f : fall.funde()) {
                assertTrue(m.find());
                String ph = m.group();
                String key = f.type().name() + "\u0000" + f.originalValue();
                String bisher = schluesselZuPlatzhalter.putIfAbsent(key, ph);
                if (bisher == null) {
                    assertEquals(PlaceholderSyntax.format(f.type(), naechste++), ph, "Lauf " + lauf);
                    assertEquals(null, platzhalterZuSchluessel.put(ph, key));
                } else {
                    assertEquals(bisher, ph);
                }
            }
            assertEquals(schluesselZuPlatzhalter.size(), r.mapping().size());

            // (c) deterministisch
            assertEquals(r, p.pseudonymize(fall.text(), fall.funde()));

            // (d) Rueckweg ueber die Produkt-API ergibt den Originaltext, ohne Meldung
            ReidentificationResult rr = new Reidentifier().restore(r.text(), r.mapping());
            assertEquals(fall.text(), rr.text(), "Lauf " + lauf);
            assertTrue(rr.unresolved().isEmpty(), "Lauf " + lauf);
        }
    }
}
