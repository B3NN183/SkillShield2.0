package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest mit festem Startwert über {@link TextScanner#standard()}, {@link Pseudonymizer} und
 * {@link Reidentifier}: Zufallstexte mit synthetischen Laufwerkspfaden. Geprüft wird (1) jeder erzeugte Pfad liegt
 * ganz in einem Fund (Obermenge), (2) der Roundtrip ist zeichengleich ohne Meldung, (3) kein Pfadbestandteil eines
 * erkannten Pfads steht im pseudonymisierten Text. Die Füllwörter enthalten keinen Pfadbestandteil. Fehlermeldungen
 * nennen nur Startwert und Iteration, keine Texte.
 */
class PathRecognizerLaufwerkEigenschaftTest {

    private static final long SEED = 20261119L;
    private static final int ITERATIONEN = 3_000;

    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "siehe", "oben", "folgt", "nichts", "weiter"};
    private static final String[] BASIS = {"Ordner", "BeispielTool", "erika.mustermann", "Akten", "Konfig",
            "\u00DCbersicht", "Gr\u00F6\u00DFe", "Beispiel(x86)", "e\u0301cole", "\uD83D\uDE00Emoji", "Projekt-A",
            "Mein_Ordner", ".versteckt", "a.b.c"};
    private static final String[] ENDUNG = {"", ".exe", ".pdf", ".txt", ".yml", ".tar.gz"};
    private static final String[] VORHER = {"", "", "", "", "Pfad:", "x=", "(", "\"", "'", "[", "siehe.....", "abc",
            "<", ">", "`", "{", "\u200B", "1"};
    private static final String[] NACHHER = {"", "", "", "", ".", ",", ";", "!", "?", ")", "'", "\"", ".)", "...",
            ",;", "]", "}", ">", "`"};
    private static final String[] TRENNER_ZWISCHEN = {" ", "\n", "\t", "\r\n", " und ", ", ", ". ", " | ", "\n\n"};

    private record Erzeugt(String text, List<int[]> pfade, List<String> bestandteile) {
    }

    @Test
    void zufallstextMitLaufwerkspfadenRoundtripUndKeineBestandteileImKlartext() {
        Random rnd = new Random(SEED);
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        TextScanner scanner = TextScanner.standard();
        int gedeckteGesamt = 0;
        for (int it = 0; it < ITERATIONEN; it++) {
            Erzeugt e = erzeuge(rnd);
            String text = e.text();
            List<Finding> funde = scanner.scan(text);

            // (1) Obermenge: jeder erzeugte Pfad liegt ganz in einem Fund
            for (int[] p : e.pfade()) {
                boolean gedeckt = false;
                for (Finding f : funde) {
                    if (f.start() <= p[0] && f.end() >= p[1]) {
                        gedeckt = true;
                        break;
                    }
                }
                if (gedeckt) {
                    gedeckteGesamt++;
                } else {
                    fail("Pfad nicht gedeckt, Startwert " + SEED + ", Iteration " + it);
                }
            }

            // Fundliste: Teilstring, sortiert, überlappungsfrei
            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(),
                        "Teilstring, Startwert " + SEED + ", Iteration " + it);
                assertTrue(f.start() >= vorigesEnde, "Überlappung, Startwert " + SEED + ", Iteration " + it);
                vorigesEnde = f.end();
            }

            // (2) Roundtrip
            PseudonymizationResult p = pseudo.pseudonymize(text, funde);
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip, Startwert " + SEED + ", Iteration " + it);
            assertTrue(back.unresolved().isEmpty(), "Meldung, Startwert " + SEED + ", Iteration " + it);

            // (3) kein Bestandteil eines erkannten Pfads im pseudonymisierten Text
            for (Finding f : funde) {
                if (f.type() != FindingType.PFAD) {
                    continue;
                }
                for (String teil : f.originalValue().split("[\\\\/]+")) {
                    if (teil.length() >= 4 && !teil.contains(":")) {
                        assertFalse(p.text().contains(teil),
                                "Bestandteil im Klartext, Startwert " + SEED + ", Iteration " + it);
                    }
                }
            }
            for (String b : e.bestandteile()) {
                assertFalse(p.text().contains(b), "Pfadname im Klartext, Startwert " + SEED + ", Iteration " + it);
            }
        }
        // der Test ist nicht leer: sehr viele Pfade wurden erzeugt, gedeckt und geprüft
        assertTrue(gedeckteGesamt >= 3 * ITERATIONEN / 2, "zu wenige geprüfte Pfade: " + gedeckteGesamt);
    }

    private static Erzeugt erzeuge(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        List<String> bestandteile = new ArrayList<>();
        int teile = 1 + rnd.nextInt(5);
        for (int t = 0; t < teile; t++) {
            if (t > 0) {
                sb.append(TRENNER_ZWISCHEN[rnd.nextInt(TRENNER_ZWISCHEN.length)]);
            }
            int fuell = rnd.nextInt(4);
            for (int k = 0; k < fuell; k++) {
                sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
            }
            sb.append(VORHER[rnd.nextInt(VORHER.length)]);
            int start = sb.length();
            appendPfad(rnd, sb, bestandteile);
            pfade.add(new int[] {start, sb.length()});
            sb.append(NACHHER[rnd.nextInt(NACHHER.length)]);
        }
        return new Erzeugt(sb.toString(), pfade, bestandteile);
    }

    private static void appendPfad(Random rnd, StringBuilder sb, List<String> bestandteile) {
        char laufwerk = (char) (rnd.nextBoolean() ? 'A' + rnd.nextInt(26) : 'a' + rnd.nextInt(26));
        String[] trenner = {"\\", "/"};
        boolean gemischt = rnd.nextInt(4) == 0;
        String fest = trenner[rnd.nextInt(2)];
        sb.append(laufwerk).append(':').append(gemischt ? trenner[rnd.nextInt(2)] : fest);
        if (rnd.nextInt(8) == 0) {
            sb.append(gemischt ? trenner[rnd.nextInt(2)] : fest); // doppelter Trenner
        }
        int segmente = 1 + rnd.nextInt(5);
        for (int s = 0; s < segmente; s++) {
            String name = BASIS[rnd.nextInt(BASIS.length)] + rnd.nextInt(1000);
            if (s == segmente - 1) {
                name = name + ENDUNG[rnd.nextInt(ENDUNG.length)];
            }
            sb.append(name);
            bestandteile.add(name);
            boolean ende = s == segmente - 1;
            if (!ende || rnd.nextInt(6) == 0) {
                sb.append(gemischt ? trenner[rnd.nextInt(2)] : fest);
            }
        }
    }
}
