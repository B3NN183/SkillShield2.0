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
 * Eigenschaftstests der Form UNC (T-12) mit festem Startwert. Fehlermeldungen nennen nur Startwert und Iteration,
 * keine Texte. Alle Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes.
 *
 * <p>(1) Differenztest (Obermengen-Garantie): Fuer Zufallstexte mit Laufwerks- und UNC-Koepfen, Anfuehrungszeichen
 * aller Art und doppelten Backslashes deckt {@code detect} mit allen Formen jede Position ab, die ein Durchlauf nur
 * mit {@code LAUFWERK} (Stand T-11) abdeckt; dasselbe fuer den Durchlauf ohne Anfuehrungszeichen. (2) Ueber
 * {@code TextScanner.standard()}, {@code Pseudonymizer} und {@code Reidentifier}: jeder erzeugte Pfad ohne
 * Leerzeichen bzw. jeder Pfad im Zitat liegt ganz in einem Fund, kein Name des Pfads steht im pseudonymisierten
 * Text, der Roundtrip ist zeichengleich und meldet nichts.
 */
class PathRecognizerUncEigenschaftTest {

    private static final long SEED = 20261121L;
    private static final int ITERATIONEN_DIFFERENZ = 100_000;
    private static final int ITERATIONEN_PIPELINE = 12_000;

    private static final PathRecognizer R = new PathRecognizer();
    private static final PathForm[] NUR_LAUFWERK = {PathForm.LAUFWERK};
    private static final PathForm[] NUR_LAUFWERK_UNC = {PathForm.LAUFWERK, PathForm.UNC};

    private static final String[] ANFUEHRUNG = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB",
            "\u2018", "\u2019", "\u201A", "\u2039", "\u203A"};
    private static final String[] TOKEN = {"C:\\", "D:/", "c:\\", "\\\\", "\\\\", "\\\\\\\\", "\\\\\\", "\\", "/", ":",
            " ", " ", " ", "\n", "\t", "a", "srv", "Mein", "b c", ".", ",", "|", "<", ">", "(", ")", "[", "]",
            "\uD83D\uDE00", "e\u0301", "\u00A0", "\u200B", "=", "x", ";", "?", "$", "\\\\srv\\a", "\\\\?\\C:\\x",
            "\\\\.\\pipe\\n", "C:\\\\Tools\\\\a", "Beispiel(x86)"};

    // ------------------------------------------------------------------ (1) Differenztest gegen den Stand T-11

    @Test
    void differenzTestAbdeckungMitUncEnthaeltAbdeckungNurMitLaufwerk() {
        Random rnd = new Random(SEED);
        int groesser = 0;
        int mitUnc = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd);
            List<Finding> alle = R.detect(text);
            List<Finding> laufwerk = R.detect(text, NUR_LAUFWERK);
            pruefeFunde(text, alle, it);
            pruefeFunde(text, laufwerk, it);
            for (Finding lw : laufwerk) {
                assertEquals(0.95, lw.confidence(), "Laufwerksdurchlauf immer 0.95, Startwert " + SEED + ", Iteration " + it);
                assertEquals("pfad-laufwerk", lw.source());
            }
            boolean[] abgedeckt = abdeckung(text.length(), alle);
            boolean[] abgedecktLw = abdeckung(text.length(), laufwerk);
            boolean echtGroesser = false;
            for (int i = 0; i < text.length(); i++) {
                if (abgedecktLw[i] && !abgedeckt[i]) {
                    fail("Position nicht mehr abgedeckt, Startwert " + SEED + ", Iteration " + it);
                }
                if (abgedeckt[i] && !abgedecktLw[i]) {
                    echtGroesser = true;
                }
            }
            // dasselbe fuer den Durchlauf ohne Anfuehrungszeichenregel
            boolean[] ohneAlle = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] ohneLw = abdeckung(text.length(), R.detectWithoutQuotes(text, NUR_LAUFWERK));
            for (int i = 0; i < text.length(); i++) {
                if (ohneLw[i] && !ohneAlle[i]) {
                    fail("Position ohne Zitatregel nicht mehr abgedeckt, Startwert " + SEED + ", Iteration " + it);
                }
            }
            if (echtGroesser) {
                groesser++;
            }
            if (text.contains("\\\\")) {
                mitUnc++;
            }
        }
        // nicht leer: UNC liefert in vielen Texten zusaetzliche Abdeckung
        assertTrue(groesser > ITERATIONEN_DIFFERENZ / 20, "zu wenige echte Vergroesserungen: " + groesser);
        assertTrue(mitUnc > ITERATIONEN_DIFFERENZ / 4, "zu wenige Texte mit UNC-Kopf: " + mitUnc);
    }

    @Test
    void differenzTestMitDichtenKurzenTexten() {
        Random rnd = new Random(SEED + 1);
        for (int it = 0; it < 60_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(10);
            for (int k = 0; k < len; k++) {
                int r = rnd.nextInt(10);
                if (r < 3) {
                    sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
                } else if (r < 5) {
                    sb.append(rnd.nextBoolean() ? "C:\\" : "\\\\");
                } else {
                    sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
                }
            }
            String text = sb.toString();
            boolean[] abgedeckt = abdeckung(text.length(), R.detect(text));
            boolean[] abgedecktLw = abdeckung(text.length(), R.detect(text, NUR_LAUFWERK));
            for (int i = 0; i < text.length(); i++) {
                if (abgedecktLw[i] && !abgedeckt[i]) {
                    fail("Position nicht mehr abgedeckt (dicht), Startwert " + (SEED + 1) + ", Iteration " + it);
                }
            }
        }
    }

    /** Die Funde der Zitatregel entstehen mit Laufwerk und UNC immer in Startreihenfolge (kein Nachsortieren noetig). */
    @Test
    void zitatFundeEntstehenMitLaufwerkUndUncNieUnsortiert() {
        // Reviewer-Auflage aus T-11: gesucht war ein Text, bei dem ein spaeter oeffnendes Zeichen einen frueher
        // beginnenden Fund liefert. Mit LAUFWERK und UNC (je mindestens 1 Segment), beide ohne Startgrenze, ist das
        // nicht konstruierbar: Ein Kopf, den das spaeter oeffnende Paar B findet, liegt im Inhalt des frueher
        // oeffnenden Paars A (A oeffnet vor B, A schliesst vor B oder gleichzeitig). A zaehlt dort mindestens so
        // viele Segmente hinter dem Kopf wie noetig, wenn irgendein Kopf hinter diesem in A gueltig ist, denn jeder
        // Laufwerkskopf enthaelt selbst einen Trenner und ein Segment dahinter. Die Sortierung in detectQuoted bleibt
        // trotzdem: Mit UNIX (Startgrenze, zwei Segmente) entstehen unsortierte Zitatfunde (Gegenbeispiel als Test in
        // PathRecognizerUnixTest). Dieser Test gilt daher nur fuer LAUFWERK und UNC und sucht per Zufall nach einem
        // Gegenbeispiel; er prueft ausserdem, dass das Endergebnis immer sortiert ist.
        Random rnd = new Random(SEED + 2);
        for (int it = 0; it < 200_000; it++) {
            String text = zufallstext(rnd);
            List<Finding> roh = PathRecognizer.collectQuoted(text, NUR_LAUFWERK_UNC);
            for (int i = 1; i < roh.size(); i++) {
                assertTrue(roh.get(i).start() >= roh.get(i - 1).start(),
                        "Zitatfunde unsortiert entstanden, Startwert " + (SEED + 2) + ", Iteration " + it);
            }
            int vorigesEnde = 0;
            for (Finding f : R.detect(text, NUR_LAUFWERK_UNC)) {
                assertTrue(f.start() >= vorigesEnde, "Ergebnis sortiert, Startwert " + (SEED + 2) + ", Iteration " + it);
                vorigesEnde = f.end();
            }
        }
    }

    // ------------------------------------------------------------------ (1b) unabhaengiges Orakel fuer die Konfidenz

    /**
     * Texte ohne Anfuehrungszeichen: Der Fund mit Laufwerkskopf hat 0.95, ein UNC-Fund mit mindestens zwei nicht leeren
     * Segmenten (Trenner {@code \} und {@code /}, ohne den fuehrenden Backslash-Lauf) im Fund hat 0.95, mit genau einem
     * 0.60. Die Zaehlung im Test arbeitet auf dem fertigen Fund und teilt keinen Code mit dem Erkenner.
     */
    @Test
    void orakelKonfidenzOhneAnfuehrungszeichenFolgtDerSegmentzahlImFund() {
        Random rnd = new Random(SEED + 3);
        int unc1 = 0;
        int unc2 = 0;
        for (int it = 0; it < 300_000; it++) {
            StringBuilder sb = new StringBuilder();
            int tokens = 1 + rnd.nextInt(8);
            for (int k = 0; k < tokens; k++) {
                sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
            }
            String text = sb.toString();
            for (Finding f : R.detect(text, NUR_LAUFWERK_UNC)) {
                String wert = f.originalValue();
                String wo = ", Startwert " + (SEED + 3) + ", Iteration " + it;
                if (wert.startsWith("\\\\")) {
                    int segmente = 0;
                    for (String teil : wert.replaceFirst("^\\\\+", "").split("[\\\\/]")) {
                        if (!teil.isEmpty()) {
                            segmente++;
                        }
                    }
                    assertEquals(segmente >= 2 ? 0.95 : 0.60, f.confidence(),
                            "UNC-Konfidenz, Text " + text.replace("\\", "#") + wo);
                    if (segmente >= 2) {
                        unc2++;
                    } else {
                        unc1++;
                    }
                } else {
                    // Laufwerksfund: 0.95; nur wenn ein spaeter beginnender UNC-Fund (Doppel-Backslash im Fund) den
                    // Bereich verlaengert hat, gilt D-8 (niedrigste Konfidenz), also hoechstens 0.60
                    if (wert.contains("\\\\")) {
                        assertTrue(f.confidence() == 0.95 || f.confidence() == 0.60, "Konfidenz" + wo);
                    } else {
                        assertEquals(0.95, f.confidence(), "Laufwerkskonfidenz, Text " + text.replace("\\", "#") + wo);
                    }
                }
            }
        }
        assertTrue(unc1 > 1_000 && unc2 > 1_000, "Orakel zu selten angewandt: " + unc1 + "/" + unc2);
    }

    private static String zufallstext(Random rnd) {
        StringBuilder sb = new StringBuilder();
        int tokens = 1 + rnd.nextInt(30);
        for (int k = 0; k < tokens; k++) {
            if (rnd.nextInt(4) == 0) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
            } else {
                sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
            }
        }
        return sb.toString();
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

    /** Teilstring, Typ, Quelle, Konfidenz, sortiert, ueberlappungsfrei, keine Grenze im Surrogatpaar. */
    private static void pruefeFunde(String text, List<Finding> funde, int it) {
        int vorigesEnde = 0;
        String wo = ", Startwert " + SEED + ", Iteration " + it;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
            assertEquals(FindingType.PFAD, f.type(), "Typ" + wo);
            // seit T-13 fuenf Formen: Quelle und Konfidenz als Menge (die genaue Pruefung je Form macht das Orakel in
            // PathRecognizerUnixEigenschaftTest); ein zusammengelegter Fund traegt die Quelle des frueher beginnenden
            assertTrue(Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home", "pfad-host",
                    "pfad-relativ", "pfad-umgebung").contains(f.source()),
                    "Quelle " + f.source() + wo);
            assertTrue(Set.of(0.95, 0.90, 0.80, 0.75, 0.60).contains(f.confidence()), "Konfidenz " + f.confidence() + wo);
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei" + wo);
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar" + wo);
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar" + wo);
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ (2) Pipeline Scanner -> Pseudonymizer -> Reidentifier

    private record Paar(String auf, String zu, boolean einfach) {
    }

    private static final Paar[] PAARE = {
            new Paar("\"", "\"", false), new Paar("'", "'", true), new Paar("`", "`", true),
            new Paar("\u201E", "\u201C", false), new Paar("\u201C", "\u201D", false),
            new Paar("\u00BB", "\u00AB", false), new Paar("\u2018", "\u2019", true),
            new Paar("\u2039", "\u203A", true)};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "oben", "folgt", "nichts", "weiter"};
    private static final String[] VOR = {"", "", " ", "Pfad:", "Datei:", "x=", "(", "[", "siehe", "-> ", ">>", "\n",
            "ab ", "12"};
    private static final String[] NACH = {"", "", ".", ",", ")", ";", "!", "?", " Ende", "\n", "\r\n", "]"};
    private static final String[] SERVER = {"srv-beispiel", "srv01.praxis.example", "srv-b\u00E4r", "srv.beispiel.invalid",
            "srv-\uD83D\uDE00"};
    private static final String[] IPS = {"192.0.2.", "198.51.100.", "203.0.113."};
    private static final String[] BASIS = {"Tools", "Daten", "Akten", "Projekt", "Gr\u00F6\u00DFe", "\u00DCbersicht",
            "BeispielTool", "e\u0301cole"};
    private static final String[] DATEI = {"akte.pdf", "tool.exe", "notiz.txt", "konfig.yml", "a.tar.gz", "x"};

    private record Gen(String text, List<int[]> pfade, List<String> namen) {
    }

    private static String name(Random rnd, boolean mitLeerzeichen) {
        String n = BASIS[rnd.nextInt(BASIS.length)] + rnd.nextInt(10_000);
        if (mitLeerzeichen && rnd.nextInt(3) == 0) {
            n = "Mein Ordner" + rnd.nextInt(10_000);
        }
        return n;
    }

    private static Gen erzeuge(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        int anzahl = 1 + rnd.nextInt(3);
        for (int p = 0; p < anzahl; p++) {
            int fuell = rnd.nextInt(3);
            for (int k = 0; k < fuell; k++) {
                sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
            }
            boolean zitat = rnd.nextInt(3) == 0;
            Paar paar = PAARE[rnd.nextInt(PAARE.length)];
            if (zitat) {
                sb.append(paar.auf());
            } else {
                sb.append(VOR[rnd.nextInt(VOR.length)]);
            }
            List<String> pfadNamen = new ArrayList<>();
            String pfad = rnd.nextInt(4) == 0 ? laufwerkspfad(rnd, zitat, pfadNamen) : uncPfad(rnd, zitat, pfadNamen);
            if (zitat) {
                // Zeichen, die das Zitat vorzeitig schliessen koennten, kommen in den Namen nicht vor (reine ASCII-/Wort-
                // zeichen und Leerzeichen); der Pfad liegt zwischen auf und zu.
                int start = sb.length();
                sb.append(pfad);
                pfade.add(new int[] {start, sb.length()});
                sb.append(paar.zu());
            } else {
                int start = sb.length();
                sb.append(pfad);
                pfade.add(new int[] {start, sb.length()});
                sb.append(NACH[rnd.nextInt(NACH.length)]);
            }
            namen.addAll(pfadNamen);
            sb.append(rnd.nextBoolean() ? " " : "\n");
        }
        return new Gen(sb.toString(), pfade, namen);
    }

    private static String trenner(Random rnd) {
        return rnd.nextInt(5) == 0 ? "/" : "\\";
    }

    private static String uncPfad(Random rnd, boolean zitat, List<String> namen) {
        boolean doppelt = rnd.nextInt(5) == 0;
        StringBuilder sb = new StringBuilder();
        sb.append(doppelt ? "\\\\\\\\" : "\\\\");
        int art = rnd.nextInt(8);
        String t = doppelt ? "\\\\" : trenner(rnd);
        if (art == 0) {
            sb.append("?").append(t).append("UNC").append(t);
        } else if (art == 1) {
            sb.append("?").append(t).append("C:").append(t);
        } else if (art == 2) {
            sb.append(".").append(t).append("pipe").append(t);
        }
        String server;
        if (rnd.nextInt(5) == 0) {
            server = IPS[rnd.nextInt(IPS.length)] + (1 + rnd.nextInt(250));
        } else {
            server = SERVER[rnd.nextInt(SERVER.length)] + (art == 3 ? "" : rnd.nextInt(10_000));
        }
        sb.append(server);
        namen.add(server);
        int segmente = 1 + rnd.nextInt(4);
        for (int s = 0; s < segmente; s++) {
            String n = s == 0 && rnd.nextInt(5) == 0 ? "c$" : name(rnd, zitat);
            if (s == segmente - 1 && rnd.nextBoolean()) {
                n = n.replace(' ', '_') + "." + DATEI[rnd.nextInt(DATEI.length)];
            }
            if (!n.equals("c$")) {
                namen.add(n);
            }
            sb.append(t).append(n);
        }
        if (rnd.nextInt(6) == 0) {
            namen.add("erika.mustermann");
            sb.append(t).append("erika.mustermann");
        }
        return sb.toString();
    }

    private static String laufwerkspfad(Random rnd, boolean zitat, List<String> namen) {
        StringBuilder sb = new StringBuilder();
        char l = (char) (rnd.nextBoolean() ? 'A' + rnd.nextInt(26) : 'a' + rnd.nextInt(26));
        boolean doppelt = rnd.nextInt(5) == 0;
        String t = doppelt ? "\\\\" : trenner(rnd);
        sb.append(l).append(':').append(t);
        int segmente = 1 + rnd.nextInt(4);
        for (int s = 0; s < segmente; s++) {
            String n = name(rnd, zitat);
            if (s == segmente - 1 && rnd.nextBoolean()) {
                n = n.replace(' ', '_') + "." + DATEI[rnd.nextInt(DATEI.length)];
            }
            namen.add(n);
            if (s > 0) {
                sb.append(t);
            }
            sb.append(n);
        }
        return sb.toString();
    }

    @Test
    void zufaelligeUncUndLaufwerkspfadeSindGanzAbgedecktOhneKlartextUndMitRoundtrip() {
        Random rnd = new Random(SEED + 3);
        TextScanner scanner = TextScanner.standard();
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        for (int it = 0; it < ITERATIONEN_PIPELINE; it++) {
            Gen g = erzeuge(rnd);
            String text = g.text();
            String wo = ", Startwert " + (SEED + 3) + ", Iteration " + it;
            List<Finding> funde = scanner.scan(text);

            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
                assertTrue(f.start() >= vorigesEnde, "Ueberlappung" + wo);
                vorigesEnde = f.end();
            }
            for (int[] pfad : g.pfade()) {
                boolean gedeckt = false;
                for (Finding f : funde) {
                    if (f.start() <= pfad[0] && f.end() >= pfad[1]) {
                        gedeckt = true;
                    }
                }
                assertTrue(gedeckt, "Pfad nicht ganz in einem Fund" + wo);
            }

            PseudonymizationResult p = pseudo.pseudonymize(text, funde);
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip" + wo);
            assertTrue(back.unresolved().isEmpty(), "Meldung" + wo);
            for (String n : g.namen()) {
                assertFalse(p.text().contains(n), "Name im Klartext" + wo);
            }
        }
    }
}
