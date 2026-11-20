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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests der Anführungszeichenregel (T-11) mit festem Startwert. Fehlermeldungen nennen nur Startwert und
 * Iteration, keine Texte. Alle Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes.
 *
 * <p>(1) Differenztest gegen {@link PathRecognizer#detectWithoutQuotes} (Stand T-10): Für jeden Zufallstext mit
 * Anführungszeichen aller Art (unbalanciert, verschachtelt, über Zeilenumbrüche) deckt {@code detect} jede Position
 * ab, die der Durchlauf ohne Anführungszeichenregel abdeckt (Obermengen-Garantie); ohne Anführungszeichen im Text
 * sind beide Listen gleich. (2) Pfade mit Leerzeichen in zufällig gewählten Anführungszeichenpaaren liegen über den
 * Standard-Scanner ganz in einem Fund, kein Pfadteil bleibt im pseudonymisierten Text, der Roundtrip ist gleich;
 * vor einem Teil der Zitate steht ein einzelnes unpaariges Zeichen derselben Art (verschiebt die Paarbildung nicht).
 */
class PathRecognizerAnfuehrungEigenschaftTest {

    private static final long SEED = 20261120L;
    private static final int ITERATIONEN_DIFFERENZ = 100_000;
    private static final int ITERATIONEN_PAARE = 6_000;

    private static final PathRecognizer R = new PathRecognizer();

    private static final String[] ANFUEHRUNG = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB",
            "\u2018", "\u2019", "\u201A", "\u2039", "\u203A"};
    private static final String[] TOKEN = {"C:\\", "D:/", "c:\\", "\\", "/", ":", " ", " ", " ", " ", "\n", "\r\n",
            "\t", "a", "Mein", "b c", ".", ",", "|", "<", ">", "(", ")", "\uD83D\uDE00", "e\u0301", "\u00A0",
            "\u200B", "=", "x", ";", "C", ":\\", "\u2028"};

    // ------------------------------------------------------------------ (1) Differenztest gegen T-10

    @Test
    void differenzTestAbdeckungMitAnfuehrungszeichenEnthaeltAbdeckungOhne() {
        Random rnd = new Random(SEED);
        int groesser = 0;
        int mitFunden = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd);
            List<Finding> neu = R.detect(text);
            List<Finding> alt = R.detectWithoutQuotes(text);
            pruefeFunde(text, neu, it);
            boolean[] abgedecktNeu = abdeckung(text.length(), neu);
            boolean[] abgedecktAlt = abdeckung(text.length(), alt);
            boolean echtGroesser = false;
            for (int i = 0; i < text.length(); i++) {
                if (abgedecktAlt[i] && !abgedecktNeu[i]) {
                    fail("Position nicht mehr abgedeckt, Startwert " + SEED + ", Iteration " + it);
                }
                if (abgedecktNeu[i] && !abgedecktAlt[i]) {
                    echtGroesser = true;
                }
            }
            if (echtGroesser) {
                groesser++;
            }
            if (!neu.isEmpty()) {
                mitFunden++;
            }
            if (!enthaeltAnfuehrungszeichen(text)) {
                assertEquals(alt, neu, "ohne Anführungszeichen gleich, Startwert " + SEED + ", Iteration " + it);
            }
        }
        // der Test ist nicht leer: viele Texte mit Funden, viele davon durch die Regel größer
        assertTrue(mitFunden > ITERATIONEN_DIFFERENZ / 10, "zu wenige Texte mit Funden: " + mitFunden);
        assertTrue(groesser > ITERATIONEN_DIFFERENZ / 100, "zu wenige echte Vergrößerungen: " + groesser);
    }

    @Test
    void differenzTestMitDichtenAnfuehrungszeichenUndKurzenTexten() {
        Random rnd = new Random(SEED + 1);
        for (int it = 0; it < 40_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(14);
            for (int k = 0; k < len; k++) {
                int r = rnd.nextInt(10);
                if (r < 5) {
                    sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
                } else if (r < 7) {
                    sb.append(rnd.nextBoolean() ? "C:\\" : "d:/");
                } else {
                    sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
                }
            }
            String text = sb.toString();
            List<Finding> neu = R.detect(text);
            pruefeFunde(text, neu, it);
            boolean[] abgedecktNeu = abdeckung(text.length(), neu);
            boolean[] abgedecktAlt = abdeckung(text.length(), R.detectWithoutQuotes(text));
            for (int i = 0; i < text.length(); i++) {
                if (abgedecktAlt[i] && !abgedecktNeu[i]) {
                    fail("Position nicht mehr abgedeckt (dicht), Startwert " + (SEED + 1) + ", Iteration " + it);
                }
            }
        }
    }

    private static String zufallstext(Random rnd) {
        StringBuilder sb = new StringBuilder();
        int tokens = 1 + rnd.nextInt(30);
        for (int k = 0; k < tokens; k++) {
            if (rnd.nextInt(3) == 0) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
            } else {
                sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
            }
        }
        return sb.toString();
    }

    private static boolean enthaeltAnfuehrungszeichen(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (PathChars.quoteKind(text.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] abdeckung(int laenge, List<Finding> funde) {
        boolean[] a = new boolean[laenge];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    /** Teilstring, Typ, Quelle, Konfidenz, sortiert, überlappungsfrei, keine Grenze im Surrogatpaar. */
    private static void pruefeFunde(String text, List<Finding> funde, int it) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            String wo = ", Startwert " + SEED + ", Iteration " + it;
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
            assertEquals(FindingType.PFAD, f.type(), "Typ" + wo);
            // seit T-13 fuenf Formen: Quelle und Konfidenz als Menge (Orakel je Form: PathRecognizerUnixEigenschaftTest);
            // ein zusammengelegter Fund traegt die Quelle des frueher beginnenden Fundes
            assertTrue(Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home", "pfad-host").contains(f.source()),
                    "Quelle " + f.source() + wo);
            assertTrue(Set.of(0.95, 0.90, 0.80, 0.60).contains(f.confidence()), "Konfidenz " + f.confidence() + wo);
            assertTrue(f.start() >= vorigesEnde, "sortiert und überlappungsfrei" + wo);
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar" + wo);
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar" + wo);
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ (2) Pfade in Paaren über den Standard-Scanner

    /** Öffnendes und schließendes Zeichen; {@code einfach}: das Paar gehört zur einfachen Familie oder ist Backtick. */
    private record Paar(String auf, String zu, boolean einfach) {
    }

    private static final Paar[] PAARE = {
            new Paar("\"", "\"", false), new Paar("'", "'", true), new Paar("`", "`", true),
            new Paar("\u201E", "\u201C", false), new Paar("\u201C", "\u201D", false),
            new Paar("\u00BB", "\u00AB", false), new Paar("\u00AB", "\u00BB", false),
            new Paar("\u2018", "\u2019", true), new Paar("\u201A", "\u2018", true),
            new Paar("\u201E", "\"", false), new Paar("\"", "\u201D", false), new Paar("'", "\u2019", true),
            new Paar("\u2039", "\u203A", true)};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "siehe", "oben", "folgt", "nichts", "weiter"};
    private static final String[] NAMEN = {"Mein Ordner", "Meine Akten", "Program Files", "BeispielTool",
            "erika.mustermann", "Projekt A", "\u00DCbersicht", "Gr\u00F6\u00DFe \u2713", "\uD83D\uDE00 Emoji",
            "e\u0301cole nova", "a.b.c", "Neue Daten"};
    private static final String[] ENDUNG = {"", ".exe", ".pdf", ".txt", ".yml", " x.tar.gz"};
    private static final String[] NACH = {"", " und danach", ".", ")", ",", "\n", " Ende.\r\n", ";"};

    private record Erzeugt(String text, int pfadStart, int pfadEnde, List<String> segmente) {
    }

    @Test
    void zufaelligeZitierstellenSchuetzenDenGanzenPfadMitLeerzeichen() {
        Random rnd = new Random(SEED + 2);
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        TextScanner scanner = TextScanner.standard();
        for (int it = 0; it < ITERATIONEN_PAARE; it++) {
            Erzeugt e = erzeuge(rnd);
            String text = e.text();
            List<Finding> funde = scanner.scan(text);
            String wo = ", Startwert " + (SEED + 2) + ", Iteration " + it;

            boolean gedeckt = false;
            for (Finding f : funde) {
                if (f.start() <= e.pfadStart() && f.end() >= e.pfadEnde()) {
                    gedeckt = true;
                }
            }
            assertTrue(gedeckt, "Pfad nicht ganz in einem Fund" + wo);

            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
                assertTrue(f.start() >= vorigesEnde, "Überlappung" + wo);
                vorigesEnde = f.end();
            }

            PseudonymizationResult p = pseudo.pseudonymize(text, funde);
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip" + wo);
            assertTrue(back.unresolved().isEmpty(), "Meldung" + wo);
            for (String s : e.segmente()) {
                assertFalse(p.text().contains(s), "Pfadteil im Klartext" + wo);
            }
        }
    }

    // ------------------------------------------------------------------ (3) unpaarige Zeichen zwischen korrekten Zitaten

    private static final int ITERATIONEN_UNPAARIG = 200_000;
    private static final String[] ZWISCHEN_WORT = {"Peter", "gibt", "Monitor 24", "und", "dann", "siehe", "oben", "Datei",
            "kann", "nicht", "Er sagte", "s", "x", "ok"};
    private static final String[] ZWISCHEN_PFAD = {"C:\\Temp", "D:/Daten", "e:\\a\\b", "C:\\x y", "F:/Ablage/z"};

    /** Text ohne Abbruchzeichen: unpaarige Zeichen aller 13 Arten, Wörter, Leerzeichen, Pfade ohne Anführungszeichen. */
    private static void zwischentext(Random rnd, StringBuilder sb) {
        int teile = rnd.nextInt(6);
        for (int k = 0; k < teile; k++) {
            int r = rnd.nextInt(10);
            if (r < 4) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
            } else if (r < 6) {
                sb.append(ZWISCHEN_PFAD[rnd.nextInt(ZWISCHEN_PFAD.length)]);
            } else {
                sb.append(ZWISCHEN_WORT[rnd.nextInt(ZWISCHEN_WORT.length)]);
            }
            sb.append(' ');
        }
    }

    /** Ersetzt jedes Zeichen, das das Zitat der Art von {@code auf} vorzeitig schließen oder abbrechen würde. */
    private static String ohneVorzeitigesEnde(String inhalt, String auf) {
        int art = PathChars.quoteKind(auf.charAt(0));
        StringBuilder sb = new StringBuilder(inhalt.length());
        for (int i = 0; i < inhalt.length(); i++) {
            char c = inhalt.charAt(i);
            boolean ende = PathChars.closesQuote(art, c) || PathChars.abortsQuoteSearch(art, c);
            sb.append(ende ? 'x' : c);
        }
        return sb.toString();
    }

    private record Zitate(String text, List<int[]> pfade, List<String> namen) {
    }

    private static Zitate erzeugeMitUnpaarigen(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        int anzahl = 1 + rnd.nextInt(4);
        for (int z = 0; z < anzahl; z++) {
            zwischentext(rnd, sb);
            Paar paar = PAARE[rnd.nextInt(PAARE.length)];
            sb.append(paar.auf());
            StringBuilder pfad = new StringBuilder();
            pfad.append((char) ('A' + rnd.nextInt(26))).append(':').append(rnd.nextBoolean() ? "\\" : "/");
            int n = 1 + rnd.nextInt(3);
            String name = "";
            for (int s = 0; s < n; s++) {
                name = NAMEN[rnd.nextInt(NAMEN.length)] + rnd.nextInt(1000);
                if (rnd.nextInt(4) == 0) {
                    name = name + ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)] + "s";
                }
                pfad.append(name);
                if (s < n - 1) {
                    pfad.append(pfad.charAt(2));
                }
            }
            if (rnd.nextInt(4) == 0) {
                pfad.append(' ');
            }
            String inhalt = ohneVorzeitigesEnde(pfad.toString(), paar.auf());
            int start = sb.length();
            sb.append(inhalt);
            pfade.add(new int[] {start, sb.length()});
            namen.add(ohneVorzeitigesEnde(name, paar.auf()));
            sb.append(paar.zu());
        }
        zwischentext(rnd, sb);
        return new Zitate(sb.toString(), pfade, namen);
    }

    @Test
    void unpaarigeZeichenZwischenKorrektenZitatenNehmenKeinemZitatDasOeffnendeZeichen() {
        Random rnd = new Random(SEED + 3);
        TextScanner scanner = TextScanner.standard();
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        for (int it = 0; it < ITERATIONEN_UNPAARIG; it++) {
            Zitate z = erzeugeMitUnpaarigen(rnd);
            String text = z.text();
            List<Finding> funde = R.detect(text);
            String wo = ", Startwert " + (SEED + 3) + ", Iteration " + it;
            pruefeFunde(text, funde, it);
            for (int[] pfad : z.pfade()) {
                assertTrue(deckt(funde, pfad[0], pfad[1]), "Pfad im Zitat nicht ganz in einem Fund" + wo);
            }
            if (it % 20 == 0) {
                List<Finding> scan = scanner.scan(text);
                for (int[] pfad : z.pfade()) {
                    assertTrue(deckt(scan, pfad[0], pfad[1]), "Scanner: Pfad nicht ganz in einem Fund" + wo);
                }
                PseudonymizationResult p = pseudo.pseudonymize(text, scan);
                assertEquals(text, rueck.restore(p.text(), p.mapping()).text(), "Roundtrip" + wo);
                for (String name : z.namen()) {
                    assertFalse(p.text().contains(name), "Pfadteil im Klartext" + wo);
                }
            }
        }
    }

    private static boolean deckt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
    }

    private static Erzeugt erzeuge(Random rnd) {
        StringBuilder sb = new StringBuilder();
        int fuell = rnd.nextInt(4);
        for (int k = 0; k < fuell; k++) {
            sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
        }
        Paar paar = PAARE[rnd.nextInt(PAARE.length)];
        if (rnd.nextInt(3) == 0) {
            // einzelnes fremdes Zeichen derselben Art vor dem Zitat (Zoll, Apostroph, unpaariges Zeichen)
            sb.append("Text").append(paar.auf()).append(rnd.nextBoolean() ? "s " : " und ");
        }
        sb.append(paar.auf());
        if (rnd.nextInt(4) == 0) {
            sb.append("Siehe "); // Text vor dem Pfad im Zitat
        }
        int start = sb.length();
        List<String> segmente = new ArrayList<>();
        char laufwerk = (char) (rnd.nextBoolean() ? 'A' + rnd.nextInt(26) : 'a' + rnd.nextInt(26));
        String trenner = rnd.nextBoolean() ? "\\" : "/";
        sb.append(laufwerk).append(':').append(trenner);
        int n = 1 + rnd.nextInt(4);
        for (int s = 0; s < n; s++) {
            String name = NAMEN[rnd.nextInt(NAMEN.length)] + rnd.nextInt(1000);
            if (s == n - 1) {
                name = name + ENDUNG[rnd.nextInt(ENDUNG.length)];
            }
            if (!paar.einfach() && rnd.nextInt(5) == 0) {
                name = name.replace(" ", "'s "); // Apostroph im Pfad nur in der doppelten Familie
            }
            sb.append(name);
            segmente.add(name);
            if (s < n - 1) {
                sb.append(trenner);
            }
        }
        int ende = sb.length();
        sb.append(paar.zu());
        sb.append(NACH[rnd.nextInt(NACH.length)]);
        return new Erzeugt(sb.toString(), start + 0, ende, segmente);
    }
}
