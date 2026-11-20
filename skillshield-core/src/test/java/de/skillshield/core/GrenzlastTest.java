package de.skillshield.core;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Grenzlast-Test (T-19; AK-48, AK-49) mit den echten Standard-Erkennern, dem {@link Pseudonymizer} und dem
 * {@link Reidentifier} im Standard-Heap des Surefire-Laufs.
 *
 * <p>Jeder Text entsteht zur Laufzeit deterministisch; Scan, Pseudonymisierung und Rückübersetzung werden je Schritt
 * getrennt mit {@code assertTimeoutPreemptively} begrenzt (AK-48: 10 s je Schritt, AK-49 und lange Kette: 2 s je
 * Schritt; OF-13). Das Ergebnis jedes Schritts geht in den Roundtrip-Vergleich ein. Die Zeit der
 * Texterzeugung ist nicht Teil der Messung. Testdaten sind synthetisch;
 * Backslashes stehen über {@link TestTexte#bs(String)} im Quelltext.
 */
class GrenzlastTest {

    private static final Duration GRENZE_GROSSER_TEXT = Duration.ofSeconds(10);
    private static final Duration GRENZE_PATHOLOGISCH = Duration.ofSeconds(2);

    private static final int MIN_ZEICHEN = 1_000_000;
    private static final int MIN_FUNDE = 10_000;
    private static final int WIEDERHOLUNGEN = 100_000;
    private static final int KETTE = 40_000;

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** Genau die Erkenner von {@code TextScanner.standard()} (wie in {@code RoundtripFuzzTest}). */
    private static final List<Recognizer> STANDARD_ERKENNER = List.of(new EmailRecognizer(), new Ipv4Recognizer(),
            new UrlRecognizer(), new PathRecognizer());

    /** Die elf Pfadformen: Name, Quellname der Erkennung und ein Muster mit {@code #} als Platz für den Baustein. */
    private static final String[][] FORMEN = {
        {"LAUFWERK", "pfad-laufwerk", "C:¦Tools¦#¦a.txt"},
        {"UNC", "pfad-unc", "¦¦srv-beispiel¦freigabe¦#¦a.txt"},
        {"UNIX", "pfad-unix", "/opt/beispiel/#/a.txt"},
        {"HOME", "pfad-home", "~erika/#/a.txt"},
        {"HOST", "pfad-host", "host.example:/export/#/a.txt"},
        {"RELATIV", "pfad-relativ", "../#/a.txt"},
        {"EINZEL_BACKSLASH", "pfad-relativ", "¦srv¦freigabe¦#¦a.txt"},
        {"BENUTZER_WURZEL", "pfad-relativ", "Users¦erika¦#¦a.txt"},
        {"WURZEL_ZWEI_SEGMENTE", "pfad-relativ", "home/erika/#/a.txt"},
        {"UMGEBUNG", "pfad-umgebung", "${HOME}/#/a.txt"},
        {"FILE_URI", "pfad-file-uri", "file:///C:/Tools/#/a.txt"},
    };

    /** Ergebnis der drei Schritte eines Texts. */
    private record Durchlauf(List<Finding> funde, PseudonymizationResult pseudo, ReidentificationResult zurueck) {
    }

    // ------------------------------------------------------------------ Hilfen

    private static <T> T zeitbegrenzt(Duration grenze, Supplier<T> schritt) {
        return assertTimeoutPreemptively(grenze, schritt::get);
    }

    /** Scan, Pseudonymisierung aller Funde und Rückübersetzung, je Schritt mit eigener Zeitgrenze. */
    private static Durchlauf drei(String text, Duration grenze) {
        TextScanner scanner = TextScanner.standard();
        List<Finding> funde = zeitbegrenzt(grenze, () -> scanner.scan(text));
        PseudonymizationResult pseudo = zeitbegrenzt(grenze, () -> PSEUDONYMIZER.pseudonymize(text, funde));
        ReidentificationResult zurueck = zeitbegrenzt(grenze,
                () -> REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()));
        return new Durchlauf(funde, pseudo, zurueck);
    }

    private static void pruefeRoundtrip(String text, Durchlauf d) {
        assertTrue(text.equals(d.zurueck().text()), "Roundtrip nicht zeichengleich (Textlaenge " + text.length() + ")");
        assertTrue(d.zurueck().unresolved().isEmpty(),
                "Rueckuebersetzung meldet " + d.zurueck().unresolved().size() + " Platzhalter");
    }

    private static String wiederhole(String teil, int anzahl) {
        return teil.repeat(anzahl);
    }

    private static boolean[] abdeckung(int laenge, List<int[]> bereiche) {
        boolean[] a = new boolean[laenge];
        for (int[] b : bereiche) {
            for (int i = b[0]; i < b[1]; i++) {
                a[i] = true;
            }
        }
        return a;
    }

    // ------------------------------------------------------------------ AK-48: großer Text

    private static String formPfad(int form, int block) {
        return bs(FORMEN[form][2]).replace("#", "Ordner" + block);
    }

    /** Ein Block: elf Pfade aller Formen, E-Mail, IPv4, URL und Füllsatz; die Pfadbausteine heißen OrdnerN. */
    private static void baueBlock(StringBuilder sb, int block, int[] pfadStarts) {
        for (int form = 0; form < FORMEN.length; form++) {
            pfadStarts[form] = sb.length();
            sb.append(formPfad(form, block)).append(", ");
        }
        sb.append("erika.beispiel@example.invalid, ");
        sb.append("192.0.2.").append(1 + block % 250).append(", ");
        sb.append("https://intranet.invalid/pfad?x=").append(block).append(" Dies ist ein Fuelltext ohne Fund.\n");
    }

    @Test
    void ak48_grosserTextMitPfadenAllerFormen_dreiSchritteJeUnterZehnSekunden() {
        StringBuilder sb = new StringBuilder(MIN_ZEICHEN + 2000);
        int[][] starts = new int[4000][FORMEN.length];
        int bloecke = 0;
        while (sb.length() < MIN_ZEICHEN + 1000) {
            baueBlock(sb, bloecke, starts[bloecke]);
            bloecke++;
        }
        String text = sb.toString();
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());

        Durchlauf d = drei(text, GRENZE_GROSSER_TEXT);

        // Umfang: Fundzahl und Pfadanteil
        List<Finding> funde = d.funde();
        assertTrue(funde.size() >= MIN_FUNDE, "Fundzahl " + funde.size());
        long pfade = funde.stream().filter(f -> FindingType.PFAD.equals(f.type())).count();
        assertTrue(pfade * 2 >= funde.size(),"Pfadanteil " + pfade + " von " + funde.size());

        // Jede Pfadform ist als Quelle eines Pfadfunds belegt: der Fund an der Pfadposition hat den erwarteten
        // Quellnamen, den Typ PFAD und enthält den Baustein; das gilt für jeden Block und jede Form.
        int[] belegt = new int[FORMEN.length];
        int fundIndex = 0;
        for (int b = 0; b < bloecke; b++) {
            for (int form = 0; form < FORMEN.length; form++) {
                int start = starts[b][form];
                while (fundIndex < funde.size() && funde.get(fundIndex).end() <= start) {
                    fundIndex++;
                }
                Finding f = funde.get(fundIndex);
                if (f.start() <= start && FindingType.PFAD.equals(f.type()) && f.source().equals(FORMEN[form][1])
                        && f.originalValue().contains("Ordner" + b + (form == 0 || form == 6 || form == 1
                                || form == 7 ? "\\" : "/"))) {
                    belegt[form]++;
                }
            }
        }
        for (int form = 0; form < FORMEN.length; form++) {
            assertEquals(bloecke, belegt[form], "Pfadform " + FORMEN[form][0] + " (Quelle " + FORMEN[form][1] + ")");
        }

        // Kein Bestandteil eines erzeugten Pfads (und keine E-Mail, IP, URL) steht im pseudonymisierten Text.
        String pseudoText = d.pseudo().text();
        for (String verboten : new String[] {"Ordner", "Tools", "freigabe", "srv-beispiel", "opt/beispiel", "export",
            "erika", "example.invalid", "intranet.invalid", "192.0.2.", "a.txt"}) {
            assertFalse(pseudoText.contains(verboten), "Bestandteil im pseudonymisierten Text: " + verboten);
        }

        pruefeRoundtrip(text, d);
    }

    // ------------------------------------------------------------------ AK-49: pathologische Eingaben

    /** Jede in AK-49 aufgezählte Eingabe als eigener, benannter Fall. */
    static Stream<Arguments> pathologischeEingaben() {
        return Stream.of(
            fall("01 100000 x a", () -> wiederhole("a", WIEDERHOLUNGEN)),
            fall("02 100000 x a@", () -> wiederhole("a@", WIEDERHOLUNGEN)),
            fall("03 100000 x 1.", () -> wiederhole("1.", WIEDERHOLUNGEN)),
            fall("04 100000 x C:\\", () -> wiederhole(bs("C:¦"), WIEDERHOLUNGEN)),
            fall("05 100000 x C:\\a (mit Leerzeichen)", () -> wiederhole(bs("C:¦a "), WIEDERHOLUNGEN)),
            fall("06 100000 x \\\\", () -> wiederhole(bs("¦¦"), WIEDERHOLUNGEN)),
            fall("07 100000 x /a", () -> wiederhole("/a", WIEDERHOLUNGEN)),
            fall("08 100000 x ~/", () -> wiederhole("~/", WIEDERHOLUNGEN)),
            fall("09 100000 x %", () -> wiederhole("%", WIEDERHOLUNGEN)),
            fall("10 100000 x \" (nie geschlossen: ungerade Anzahl, ein Zeichen)",
                () -> wiederhole("\"", WIEDERHOLUNGEN + 1)),
            fall("10b 100000 x \" (gerade Anzahl)", () -> wiederhole("\"", WIEDERHOLUNGEN)),
            fall("11a\"C:\\ a b c  nie geschlossen, 100000 Leerzeichen",
                () -> bs("\"C:¦ a b c ") + wiederhole(" ", WIEDERHOLUNGEN)),
            fall("11b \"C:\\ nie geschlossen, 100000 Woerter",
                () -> bs("\"C:¦") + wiederhole(" a b c", WIEDERHOLUNGEN)),
            fall("12a Pfadanfang C:\\a, 100000 durch Leerzeichen getrennte Woerter",
                () -> bs("C:¦a") + wiederhole(" wort", WIEDERHOLUNGEN)),
            fall("12b Pfadanfang C:\\a, 100000 Woerter mit Pfadzeichen",
                () -> bs("C:¦a") + wiederhole(bs(" w¦x"), WIEDERHOLUNGEN)),
            fall("13a lange Ziffernreihe, 100000 Ziffern", () -> wiederhole("7", WIEDERHOLUNGEN)),
            fall("13b lange Ziffernreihe, 1000000 Ziffern in Zehnerbloecken",
                () -> wiederhole("0123456789", WIEDERHOLUNGEN)),
            fall("14 sehr lange Zeichenfolge ohne Leerzeichen, 1000000 Zeichen",
                () -> wiederhole("abcdefghij", WIEDERHOLUNGEN)),
            fall("15 100000 x [[", () -> wiederhole("[[", WIEDERHOLUNGEN)),
            fall("16 100000 x [[A_", () -> wiederhole("[[A_", WIEDERHOLUNGEN)));
    }

    private static Arguments fall(String name, Supplier<String> text) {
        return Arguments.of(name, text);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pathologischeEingaben")
    void ak49_pathologischeEingabeDurchAlleDreiSchritteJeUnterZweiSekunden(String name, Supplier<String> quelle) {
        String text = quelle.get();
        assertTrue(text.length() >= WIEDERHOLUNGEN, "Textlaenge " + text.length());
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        pruefeRoundtrip(text, d);
    }

    // ------------------------------------------------------------------ Lange Kette mit echten Erkennern

    @Test
    void ak49_langeKetteUeberlappenderKandidaten_dreiSchritteSchutzgarantieUndRoundtrip() {
        String text = wiederhole(bs("https://e.invalid/''C:¦a "), KETTE);
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        pruefeRoundtrip(text, d);

        // Es entsteht wirklich eine lange Kette: der längste Fund deckt den größten Teil des Texts ab.
        int laengster = 0;
        for (Finding f : d.funde()) {
            laengster = Math.max(laengster, f.end() - f.start());
        }
        assertTrue(laengster * 2 >= text.length(), "laengster Fund " + laengster + " von " + text.length());
        assertTrue(d.funde().size() * 10 < KETTE, "Fundzahl " + d.funde().size());

        // Schutzgarantie (wie T-18): abgedeckte Positionen des Scans = Vereinigung der Kandidatenpositionen
        List<int[]> kandidaten = new java.util.ArrayList<>();
        for (Recognizer erkenner : STANDARD_ERKENNER) {
            for (Finding f : erkenner.detect(text)) {
                kandidaten.add(new int[] {f.start(), f.end()});
            }
        }
        List<int[]> scan = new java.util.ArrayList<>();
        for (Finding f : d.funde()) {
            scan.add(new int[] {f.start(), f.end()});
        }
        boolean[] kand = abdeckung(text.length(), kandidaten);
        boolean[] gedeckt = abdeckung(text.length(), scan);
        int kandidatNichtImScan = 0;
        int fremdImScan = 0;
        for (int i = 0; i < text.length(); i++) {
            if (kand[i] && !gedeckt[i]) {
                kandidatNichtImScan++;
            }
            if (gedeckt[i] && !kand[i]) {
                fremdImScan++;
            }
        }
        assertEquals(0, kandidatNichtImScan, "Kandidatenpositionen ohne Abdeckung im Scan");
        assertEquals(0, fremdImScan, "Scanpositionen ohne Kandidat");
    }
}
