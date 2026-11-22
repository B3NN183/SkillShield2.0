package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.TestTexte.bs;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.neunerfenster;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
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
    private static final List<Recognizer> STANDARD_ERKENNER = TextScanner.standard().recognizers();

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

    // ------------------------------------------------------------------ Große Zuordnung (T-20, Nachtrag Punkt 5)

    @Test
    void ak49_hunderttausendVerschiedenePfade_grosseZuordnung_dreiSchritteJeUnterZweiSekunden() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WIEDERHOLUNGEN; i++) {
            sb.append(bs("C:¦d¦")).append(i).append(' ');
        }
        String text = sb.toString();
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        assertTrue(d.pseudo().mapping().size() >= WIEDERHOLUNGEN, "Zuordnung: " + d.pseudo().mapping().size());
        pruefeRoundtrip(text, d);
    }

    @Test
    void ak49_hunderttausendVerschiedeneEmails_grosseZuordnung_dreiSchritteJeUnterZweiSekunden() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WIEDERHOLUNGEN; i++) {
            sb.append("u").append(i).append("@e.invalid ");
        }
        String text = sb.toString();
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        assertTrue(d.pseudo().mapping().size() >= WIEDERHOLUNGEN, "Zuordnung: " + d.pseudo().mapping().size());
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

    // ------------------------------------------------------------------ AK-21: KV-Eingaben (T-8)

    /** Text aus Praefix und so oft wiederholtem Baustein, dass er mindestens {@code MIN_ZEICHEN} Zeichen hat. */
    private static String kvText(String praefix, String baustein, int mindestlaenge) {
        return praefix + baustein.repeat(kvWiederholungen(praefix, baustein, mindestlaenge));
    }

    private static int kvWiederholungen(String praefix, String baustein, int mindestlaenge) {
        return (mindestlaenge - praefix.length() + baustein.length() - 1) / baustein.length();
    }

    /**
     * Die zehn Eingaben aus AK-21, je mit Praefix, Baustein, erwarteter Fundzahl je Stufe (sicher, wahrscheinlich;
     * fraglich gibt es in keiner Eingabe) und Typ. Die Zahlen folgen aus den Regeln (Plan Kap. 3.2 und 3.3, Nachtrag
     * T-5), nicht aus einem Probelauf. Ersetzungen gegenueber dem Wortlaut von AK-21 (Nachtrag "Laufzeittests"):
     * Eingabe 2 mit {@code 0 } statt {@code 1 }, Eingabe 1 mit Nullen als Ziffern.
     */
    static Stream<Arguments> kvEingaben() {
        // Eingabe 8: Baustein "IK " + IK_GUELTIG + " " = 13 Zeichen; Wiederholungen = (1 000 000 + 12) / 13 = 76 924.
        String baustein8 = "IK " + IK_GUELTIG + " ";
        int wiederholungen8 = kvWiederholungen("", baustein8, MIN_ZEICHEN);
        assertEquals(76_924, wiederholungen8);

        // Eingabe 9: Baustein LANR_GUELTIG + " (LANR) " = 9 + 8 = 17 Zeichen; Wiederholungen
        // = (1 000 000 + 16) / 17 = 58 824. Die erste Nummer steht vor dem Kontextwort (nachgestellt, "offen"): Fund
        // mit 0.80 (wahrscheinlich). Danach ist der Kontext aktiv und bleibt "direkt" (")" und Leerzeichen sind
        // neutral): jede weitere Nummer wird als Segment bei "direkt" ausgegeben (0.95, sicher); das folgende
        // Kontextwort setzt den Kontext neu und hat kein offenes Segment. Je Wiederholung genau ein Fund.
        String baustein9 = LANR_GUELTIG + " (LANR) ";
        int wiederholungen9 = kvWiederholungen("", baustein9, MIN_ZEICHEN);
        assertEquals(58_824, wiederholungen9);

        // Eingabe 10: Baustein "IK " + IK_FORM_DOKU_IP + " " = 3 + 12 + 1 = 16 Zeichen; Wiederholungen
        // = (1 000 000 + 15) / 16 = 62 500 (genau 16 * 62 500 = 1 000 000). Je Wiederholung ein IK-Kandidat ueber
        // genau die Adresse (Gruppen 3-2-3-1 = neun Ziffern; Pruefziffer ungueltig, 0.50 fraglich) und ein
        // IPv4-Fund (0.85): bei gleichem Bereich gewinnt der staerkere, also je Wiederholung ein Fund Typ IP.
        String baustein10 = "IK " + IK_FORM_DOKU_IP + " ";
        int wiederholungen10 = kvWiederholungen("", baustein10, MIN_ZEICHEN);
        assertEquals(62_500, wiederholungen10);

        // Eingabe 2: Praefix "LANR " (5), Baustein "0 " (2): Bloecke b = (1 000 000 - 5 + 1) / 2 = 499 998. Jede Kette
        // liest ganze Bloecke; das Neuner-Praefix (neun Nullen, 17 Zeichen) ist das Segment, der Durchlauf geht
        // dahinter weiter. Segmente = b / 9 = 55 555 (Rest drei Nullen, kein Segment). Das erste Segment steht
        // direkt hinter dem Kontextwort (0.95, sicher), alle weiteren nicht (0.80, wahrscheinlich): 1 und 55 554.
        int bloecke2 = kvWiederholungen("LANR ", "0 ", MIN_ZEICHEN);
        assertEquals(499_998, bloecke2);
        int segmente2 = bloecke2 / 9;
        assertEquals(55_555, segmente2);

        return Stream.of(
            // 1: "IK " + nur Ziffern (Nullen): ein einziger Ziffernblock mit ueber 12 Ziffern, kein Segment: 0 Funde.
            Arguments.of("01 IK plus nur Ziffern", "IK ", "0", 0, 0, null),
            Arguments.of("02 LANR plus 0 und Leerzeichen wiederholt", "LANR ", "0 ", 1, segmente2 - 1,
                FindingType.LANR),
            // 3: Kontextwort, dann nur "-": keine Ziffer, keine Kette: 0 Funde.
            Arguments.of("03 IK plus nur Bindestriche", "IK", "-", 0, 0, null),
            // 4: Kontextwort, dann nur Leerzeichen: 0 Funde.
            Arguments.of("04 LANR plus nur Leerzeichen", "LANR", " ", 0, 0, null),
            // 5: 200 000 Kontextwoerter ohne Ziffer: 0 Funde.
            Arguments.of("05 LANR Leerzeichen wiederholt ohne Ziffern", "", "LANR ", 0, 0, null),
            // 6: Kontextwort, dann Woerter ohne Satzende und ohne Ziffer: 0 Funde.
            Arguments.of("06 LANR plus Woerter ohne Satzende", "LANR", " wort", 0, 0, null),
            // 7: Kontextwort, dann nur Zeilenumbrueche und keine Ziffer: 0 Funde.
            Arguments.of("07 LANR plus nur Zeilenumbrueche", "LANR", "\n", 0, 0, null),
            // 8: je Wiederholung ein Fund (Kontextwort direkt vor der Nummer, 0.92 sicher), Typ IK.
            Arguments.of("08 IK gueltige Nummer wiederholt", "", baustein8, wiederholungen8, 0, FindingType.IK),
            Arguments.of("09 gueltige LANR mit nachgestelltem Kontext wiederholt", "", baustein9,
                wiederholungen9 - 1, 1, FindingType.LANR),
            Arguments.of("10 IK mit Dokumentations-IP wiederholt", "", baustein10, 0, wiederholungen10,
                FindingType.IP));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvEingaben")
    void ak21_kvEingabeEineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name,
            String praefix, String baustein, int sicher, int wahrscheinlich, FindingType typ) {
        // Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart
        String warm = kvText(praefix, baustein, 10_000);
        assertEquals(0, neunerfenster(warm, "00")[1], "Waechter Aufwaermtext " + name);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        // Testdatenregel: kein gueltiges Neunerfenster ohne Praefix 00
        assertEquals(0, neunerfenster(text, "00")[1], "Waechter " + name);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);

        List<Finding> funde = d.funde();
        assertEquals(sicher + wahrscheinlich, funde.size(), "Fundzahl " + name);
        long anzahlSicher = funde.stream().filter(f -> f.level() == ConfidenceLevel.SICHER).count();
        long anzahlWahrscheinlich = funde.stream().filter(f -> f.level() == ConfidenceLevel.WAHRSCHEINLICH).count();
        assertEquals(sicher, anzahlSicher, "Stufe sicher " + name);
        assertEquals(wahrscheinlich, anzahlWahrscheinlich, "Stufe wahrscheinlich " + name);
        for (Finding f : funde) {
            assertEquals(typ, f.type(), "Typ " + name);
        }
        pruefeRoundtrip(text, d);
    }
}
