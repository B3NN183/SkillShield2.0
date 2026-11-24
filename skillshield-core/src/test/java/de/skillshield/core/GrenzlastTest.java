package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.TestTexte.bs;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
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
import de.skillshield.core.recognizer.kv.BsnrSynthetischWaechter;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
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

    // ------------------------------------------------------------------ AK-21: Nachtrag T-8 (R-1, R-2, R-3)

    /**
     * Anzahl der Wiederholungen des Bausteins, sodass Praefix, Wiederholungen und Schluss zusammen mindestens
     * {@code mindestlaenge} Zeichen haben: aufgerundet (mindestlaenge - praefix - schluss) / baustein.
     */
    private static int kvWiederholungen(String praefix, String baustein, String schluss, int mindestlaenge) {
        return (mindestlaenge - praefix.length() - schluss.length() + baustein.length() - 1) / baustein.length();
    }

    private static String kvText(String praefix, String baustein, String schluss, int mindestlaenge) {
        return praefix + baustein.repeat(kvWiederholungen(praefix, baustein, schluss, mindestlaenge)) + schluss;
    }

    private static void pruefeNachtragFall(String name, String praefix, String baustein, String schluss,
            int erwarteteWiederholungen, int erwarteteFunde, ConfidenceLevel stufe) {
        // Formel der Wiederholungen: (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein| (ganzzahlig)
        assertEquals(erwarteteWiederholungen, kvWiederholungen(praefix, baustein, schluss, MIN_ZEICHEN),
                "Wiederholungen " + name);
        // Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart
        String warm = kvText(praefix, baustein, schluss, 10_000);
        assertEquals(0, neunerfenster(warm, "00")[1], "Waechter Aufwaermtext " + name);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, schluss, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        // Testdatenregel: kein gueltiges Neunerfenster ohne Praefix 00, vor dem Scan
        assertEquals(0, neunerfenster(text, "00")[1], "Waechter " + name);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        List<Finding> funde = d.funde();
        assertEquals(erwarteteFunde, funde.size(), "Fundzahl " + name);
        for (Finding f : funde) {
            assertEquals(FindingType.LANR, f.type(), "Typ " + name);
            assertEquals(stufe, f.level(), "Stufe " + name);
        }
        pruefeRoundtrip(text, d);
        assertFalse(d.pseudo().text().contains(LANR_GUELTIG.substring(0, 6)), "Nummer im Ausgabetext " + name);
    }

    // ------------------------------------------------------------------ AK-24: kv-kontext-luecken (T-7b)

    /**
     * Die Faelle (a) bis (i) aus AK-24 ueber die ganze Kette (Standard-Scanner, Pseudonymisierung,
     * Rueckuebersetzung). Je Fall: Praefix, Baustein, Schluss, erwartete Wiederholungen r (Formel
     * {@code (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein|}, ganzzahlig), erwartete Fundzahl je
     * Stufe (sicher, wahrscheinlich, fraglich) und Typ. Die Zahlen folgen aus den Regeln (Plan Kap. 3.1 bis 3.3),
     * nicht aus einem Probelauf; die Bausteinlaenge wird je Fall geprueft.
     */
    static Stream<Arguments> kvKontextLueckenFaelle() {
        String n = IK_GUELTIG;
        String l = LANR_GUELTIG;
        return Stream.of(
            // (a) "L " = 2 Zeichen, r = (1 000 000 + 1) / 2 = 500 000; eine einzige Einzelbuchstabenfolge: 0 Funde
            Arguments.of("a L Leerzeichen", "", "L ", "", 2, 500_000, 0, 0, 0, null),
            // (b) "I K " = 4 Zeichen, r = (1 000 000 + 3) / 4 = 250 000; eine Folge ohne Nummer: 0 Funde
            Arguments.of("b I K", "", "I K ", "", 4, 250_000, 0, 0, 0, null),
            // (c) "I." = 2 Zeichen, r = 500 000; 0 Funde
            Arguments.of("c I Punkt", "", "I.", "", 2, 500_000, 0, 0, 0, null),
            // (d) "L-A-N-R " = 8 Zeichen, r = (1 000 000 + 7) / 8 = 125 000; eine einzige Folge, nie ein Wort: 0
            Arguments.of("d L-A-N-R", "", "L-A-N-R ", "", 8, 125_000, 0, 0, 0, null),
            // (e) "L A N R " + LANR + " " = 8 + 9 + 1 = 18 Zeichen, r = (1 000 000 + 17) / 18 = 55 556 (> 50 000);
            // je Wiederholung ein Fund, gueltig, gesperrtes Kontextwort ab drei Buchstaben: 0,80 wahrscheinlich
            Arguments.of("e L A N R Nummer", "", "L A N R " + l + " ", "", 18, 55_556, 0, 55_556, 0,
                FindingType.LANR),
            // (f) "I K " + IK + " " = 4 + 9 + 1 = 14 Zeichen, r = (1 000 000 + 13) / 14 = 71 429; je Wiederholung
            // ein Fund 0,50 (I K: Deckel 0,50): fraglich
            Arguments.of("f I K Nummer", "", "I K " + n + " ", "", 14, 71_429, 0, 0, 71_429, FindingType.IK),
            // (g1) IK + ", " = 11 Zeichen, r = (1 000 000 + 10) / 11 = 90 910; ohne Kontextwort: 0 Funde
            Arguments.of("g1 Nummer Komma ohne Kontextwort", "", n + ", ", "", 11, 90_910, 0, 0, 0, null),
            // (g2) wie g1 mit Schluss "IK" (2 Zeichen): r = (1 000 000 - 2 + 10) / 11 = 90 909; alle Nummern der
            // offenen Folge werden mit dem Deckel 0,60 ausgegeben (Komma vor dem Kontextwort): 90 909 fraglich
            Arguments.of("g2 Nummer Komma mit IK am Ende", "", n + ", ", "IK", 11, 90_909, 0, 0, 90_909,
                FindingType.IK),
            // (h) IK + ", IK " = 9 + 5 = 14 Zeichen, r = 71 429; die erste Nummer steht vor dem Kontextwort mit Komma
            // (0,60 fraglich), jede weitere steht direkt hinter dem Kontextwort der vorigen Wiederholung (0,92 sicher):
            // 1 fraglich und 71 428 sicher
            Arguments.of("h Nummer Komma IK wiederholt", "", n + ", IK ", "", 14, 71_429, 71_428, 0, 1,
                FindingType.IK),
            // (i1) "L" + 999 999 Leerzeichen: Einzelbuchstabe, danach nur Leerraum: 0 Funde
            Arguments.of("i1 L und Leerzeichen", "L", " ", "", 1, 999_999, 0, 0, 0, null),
            // (i2) "L A N R" (7) + 999 993 Leerzeichen: Kontextwort ohne Nummer: 0 Funde
            Arguments.of("i2 L A N R und Leerzeichen", "L A N R", " ", "", 1, 999_993, 0, 0, 0, null),
            // (i3) "L" + ".A" = 2 Zeichen, r = (1 000 000 - 1 + 1) / 2 = 500 000; Einzelbuchstabenkette: 0 Funde
            Arguments.of("i3 L Punkt A", "L", ".A", "", 2, 500_000, 0, 0, 0, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvKontextLueckenFaelle")
    void ak24_kvKontextLuecken_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name,
            String praefix, String baustein, String schluss, int bausteinLaenge, int r, int sicher,
            int wahrscheinlich, int fraglich, FindingType typ) {
        pruefeKvLastFall(name, praefix, baustein, schluss, bausteinLaenge, r, sicher, wahrscheinlich, fraglich, typ);
    }

    /**
     * Gemeinsame Pruefung der Grenzlastfaelle ueber die ganze Kette (Aufwaermlauf, Waechter, drei Schritte je unter
     * zwei Sekunden, exakte Fundzahl je Stufe, Typ, Roundtrip, kein Klartext bzw. unveraenderter Text ohne Fund).
     */
    private static void pruefeKvLastFall(String name, String praefix, String baustein, String schluss,
            int bausteinLaenge, int r, int sicher, int wahrscheinlich, int fraglich, FindingType typ) {
        assertEquals(bausteinLaenge, baustein.length(), "Bausteinlaenge " + name);
        assertEquals(r, kvWiederholungen(praefix, baustein, schluss, MIN_ZEICHEN), "Wiederholungen " + name);
        // Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart
        String warm = kvText(praefix, baustein, schluss, 10_000);
        assertEquals(0, neunerfenster(warm, "00")[1], "Waechter Aufwaermtext " + name);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, schluss, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        assertEquals(0, neunerfenster(text, "00")[1], "Waechter " + name);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        List<Finding> funde = d.funde();
        assertEquals(sicher + wahrscheinlich + fraglich, funde.size(), "Fundzahl " + name);
        assertEquals(sicher, funde.stream().filter(f -> f.level() == ConfidenceLevel.SICHER).count(),
                "Stufe sicher " + name);
        assertEquals(wahrscheinlich,
                funde.stream().filter(f -> f.level() == ConfidenceLevel.WAHRSCHEINLICH).count(),
                "Stufe wahrscheinlich " + name);
        assertEquals(fraglich, funde.stream().filter(f -> f.level() == ConfidenceLevel.FRAGLICH).count(),
                "Stufe fraglich " + name);
        for (Finding f : funde) {
            assertEquals(typ, f.type(), "Typ " + name);
        }
        pruefeRoundtrip(text, d);
        // kein Klartext der gefundenen Nummern im Ausgabetext (bei 0 Funden bleibt der Text unveraendert)
        if (typ != null) {
            String nummer = FindingType.LANR.equals(typ) ? LANR_GUELTIG : IK_GUELTIG;
            assertFalse(d.pseudo().text().contains(nummer.substring(0, 6)), "Nummer im Ausgabetext " + name);
        } else {
            assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert " + name);
        }
    }

    // ------------------------------------------------------------------ AK-26 (a) bis (k) und Zusatzfaelle (l), (m)

    /**
     * Die Faelle (a) bis (k) aus AK-26 des Features kv-trenner-und-umbruch ueber die ganze Kette: (g) und (h) zu
     * Teil 1 (CR UZ+ LF), (a) bis (f) und (i) bis (k) zu Teil 2 (Weit-Kontextwoerter), dazu die Zusatzfaelle (l)
     * (abgebrochene Beinahe-Kontextwoerter) und (m) (lange Laeufe unsichtbarer Zeichen). Aufbau wie bei
     * {@link #kvKontextLueckenFaelle}: Praefix, Baustein, Schluss, Bausteinlaenge, r (Formel
     * {@code (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein|}), Funde je Stufe, Typ. Die Zahlen
     * folgen aus den Regeln (Plan Kap. 3.1 und 3.2), nicht aus einem Probelauf. Die Nummern sind {@code 00}-Nummern.
     */
    static Stream<Arguments> kvTrennerUndUmbruchFaelle() {
        String umbruch = "\r" + ZWSP + "\n";
        String l = LANR_GUELTIG;
        String n = IK_GUELTIG;
        return Stream.of(
            // (a) "L. " = 3 Zeichen, r = (1 000 000 + 2) / 3 = 333 334; ein einzelner Buchstabe je Folge: 0 Funde
            Arguments.of("a L Punkt", "", "L. ", "", 3, 333_334, 0, 0, 0, null),
            // (b) "I.  K.  " = 8 Zeichen, r = (1 000 000 + 7) / 8 = 125 000; Zwei-Buchstaben-Wort (D-4), ohne Nummer: 0
            Arguments.of("b I K Punkt zwei Leerzeichen", "", "I.  K.  ", "", 8, 125_000, 0, 0, 0, null),
            // (c) "L" + 999 999 Leerzeichen, r = (1 000 000 - 1 + 1 - 1) / 1 = 999 999; keine Luecke ueber drei: 0
            Arguments.of("c L und Leerzeichen", "L", " ", "", 1, 999_999, 0, 0, 0, null),
            // (d) "L" + ".  A" (4 Zeichen), r = (1 000 000 - 1 + 3) / 4 = 250 000; nie ein Kontextwort: 0 Funde
            Arguments.of("d L Punkt zwei Leerzeichen A", "L", ".  A", "", 4, 250_000, 0, 0, 0, null),
            // (e) "L. A. N. R. " + LANR + " " = 12 + 9 + 1 = 22 Zeichen, r = (1 000 000 + 21) / 22 = 45 455 (> 40 000);
            // je Wiederholung ein Fund, gueltig, Weit-Kontextwort: 0,80 wahrscheinlich
            Arguments.of("e1 L. A. N. R. Nummer", "", "L. A. N. R. " + l + " ", "", 22, 45_455, 0, 45_455, 0,
                FindingType.LANR),
            // (e) IK entsprechend: "I. K. N. R. " + IK + " " = 22 Zeichen, r = 45 455, 0,80 wahrscheinlich
            Arguments.of("e2 I. K. N. R. Nummer", "", "I. K. N. R. " + n + " ", "", 22, 45_455, 0, 45_455, 0,
                FindingType.IK),
            // (f) "L" Tab "A" Tab "N" Tab "R" Tab + LANR + " " = 8 + 9 + 1 = 18 Zeichen, r = (1 000 000 + 17) / 18 =
            // 55 556; je Wiederholung ein Fund 0,80
            Arguments.of("f1 L Tab A Tab N Tab R Nummer", "", "L\tA\tN\tR\t" + l + " ", "", 18, 55_556, 0, 55_556,
                0, FindingType.LANR),
            // (f) IK entsprechend: 18 Zeichen, r = 55 556
            Arguments.of("f2 I Tab K Tab N Tab R Nummer", "", "I\tK\tN\tR\t" + n + " ", "", 18, 55_556, 0, 55_556,
                0, FindingType.IK),
            // (i) "L    A    N    R " (vier Leerzeichen je Luecke) = 17 Zeichen, r = (1 000 000 + 16) / 17 = 58 824;
            // keine Luecke nach D-2, keine Nummer: 0 Funde
            Arguments.of("i1 vier Leerzeichen ohne Nummer", "", "L    A    N    R ", "", 17, 58_824, 0, 0, 0,
                null),
            // (i) mit Nummer: 17 + 9 + 1 = 27 Zeichen, r = (1 000 000 + 26) / 27 = 37 038; kein Kontextwort: 0 Funde
            Arguments.of("i2 vier Leerzeichen LANR", "", "L    A    N    R " + l + " ", "", 27, 37_038, 0, 0, 0,
                null),
            Arguments.of("i3 vier Leerzeichen IK", "", "I    K    N    R " + n + " ", "", 27, 37_038, 0, 0, 0,
                null),
            // (j) "A. " = 3 Zeichen, r = 333 334; kein Kontextwort: 0 Funde
            Arguments.of("j A Punkt", "", "A. ", "", 3, 333_334, 0, 0, 0, null),
            // (k) "I. K. " + IK + " " = 6 + 9 + 1 = 16 Zeichen, r = (1 000 000 + 15) / 16 = 62 500;
            // Zwei-Buchstaben-Wort (D-4) ausgenommen, kein Kontextwort: 0 Funde
            Arguments.of("k I. K. Nummer", "", "I. K. " + n + " ", "", 16, 62_500, 0, 0, 0, null),
            // (g) "LANR:" + CR ZWSP LF + Nummer + " " = 5 + 3 + 9 + 1 = 18 Zeichen, r = (1 000 000 + 17) / 18 = 55 556;
            // je Wiederholung ein Fund: ein Umbruch zaehlt wie CR LF, der Deckel ist 0,80 (wahrscheinlich)
            Arguments.of("g1 LANR Umbruch Nummer", "", "LANR:" + umbruch + LANR_GUELTIG + " ", "", 18, 55_556, 0,
                55_556, 0, FindingType.LANR),
            // (g) IK entsprechend ("IKNR:" hat ebenfalls fuenf Zeichen): 18 Zeichen, r = 55 556
            Arguments.of("g2 IKNR Umbruch Nummer", "", "IKNR:" + umbruch + IK_GUELTIG + " ", "", 18, 55_556, 0,
                55_556, 0, FindingType.IK),
            // (h) "CR" + ZWSP wiederholt, insgesamt eine Million Zeichen: "\r" (1) + r ZWSP, r = (1 000 000 - 1 + 1 -
            // 1) / 1 = 999 999; ein einziger Lauf nach dem CR, kein Kontextwort: 0 Funde
            Arguments.of("h1 CR und unsichtbare Zeichen", "\r", String.valueOf(ZWSP), "", 1, 999_999, 0, 0, 0, null),
            // (h) "CR ZWSP LF" = 3 Zeichen wiederholt, r = (1 000 000 + 2) / 3 = 333 334; kein Kontextwort: 0 Funde
            Arguments.of("h2 CR UZ LF wiederholt", "", umbruch, "", 3, 333_334, 0, 0, 0, null),
            // (l1) "L. A. N. " = 9 Zeichen, r = (1 000 000 + 8) / 9 = 111 112; drei Buchstaben "LAN" sind kein
            // Kontextwort, die Folge reisst nie ab, ohne Nummer: 0 Funde
            Arguments.of("l1 L. A. N. wiederholt", "", "L. A. N. ", "", 9, 111_112, 0, 0, 0, null),
            // (l2) "L. A. N. " + LANR + " " = 9 + 9 + 1 = 19 Zeichen, r = (1 000 000 + 18) / 19 = 52 632; das
            // Beinahe-Kontextwort "LAN" steht je Wiederholung vor der Nummer, kein Kontextwort: 0 Funde
            Arguments.of("l2 L. A. N. Nummer", "", "L. A. N. " + l + " ", "", 19, 52_632, 0, 0, 0, null),
            // (m1) "L. " (3) + ein Lauf aus 999 997 unsichtbaren Zeichen, r = (1 000 000 - 3 + 1 - 1) / 1 = 999 997;
            // ein einziger Lauf, kein Kontextwort: 0 Funde
            Arguments.of("m1 L Punkt und ein Lauf unsichtbarer Zeichen", "L. ", String.valueOf(ZWSP), "", 1,
                999_997, 0, 0, 0, null),
            // (m2) "Arzt " (5, Vorwort) + ein Lauf aus 999 995 unsichtbaren Zeichen, r = 999 995; 0 Funde
            Arguments.of("m2 Arzt und ein Lauf unsichtbarer Zeichen", "Arzt ", String.valueOf(ZWSP), "", 1,
                999_995, 0, 0, 0, null),
            // (m3) "L. " + 100 unsichtbare Zeichen = 103 Zeichen, r = (1 000 000 + 102) / 103 = 9 709
            // (103 * 9 709 = 1 000 027 >= 1 000 000, 103 * 9 708 = 999 924); viele kuerzere Laeufe, 0 Funde
            Arguments.of("m3 L Punkt und viele kurze Laeufe", "", "L. " + String.valueOf(ZWSP).repeat(100), "",
                103, 9_709, 0, 0, 0, null),
            // (m4) "Arzt " + 100 unsichtbare Zeichen = 105 Zeichen, r = (1 000 000 + 104) / 105 = 9 524
            // (105 * 9 524 = 1 000 020 >= 1 000 000, 105 * 9 523 = 999 915); 0 Funde
            Arguments.of("m4 Arzt und viele kurze Laeufe", "", "Arzt " + String.valueOf(ZWSP).repeat(100), "",
                105, 9_524, 0, 0, 0, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvTrennerUndUmbruchFaelle")
    void ak26_kvTrennerUndUmbruch_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name,
            String praefix, String baustein, String schluss, int bausteinLaenge, int r, int sicher,
            int wahrscheinlich, int fraglich, FindingType typ) {
        pruefeKvLastFall(name, praefix, baustein, schluss, bausteinLaenge, r, sicher, wahrscheinlich, fraglich, typ);
    }

    /**
     * AK-26 (h) im Wortlaut: {@code CR} gefolgt von genau einer Million unsichtbarer Zeichen (die Eingabe hat dann
     * eine Million und ein Zeichen), ohne Kontextwort: 0 Funde, der Text bleibt unveraendert, Roundtrip gleich.
     */
    @Test
    void ak26h_crGefolgtVonEinerMillionUnsichtbarerZeichen_dreiSchritteJeUnterZweiSekunden_keinFund() {
        String uz = String.valueOf(ZWSP);
        drei("\r" + uz.repeat(10_000), GRENZE_GROSSER_TEXT);
        String text = "\r" + uz.repeat(1_000_000);
        assertEquals(1_000_001, text.length(), "Textlaenge");
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        assertEquals(0, d.funde().size(), "Fundzahl");
        assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert");
        pruefeRoundtrip(text, d);
    }

    @Test
    void ak21_nachtragA_sehrVieleOffeneSegmenteDieErstEinKontextwortAmEndeAusgibt() {
        // Text: r mal "<LANR>, " (11 Zeichen), dann "<LANR> (LANR)" (16 Zeichen). Ohne Kontextwort bleiben alle
        // Segmente offen (R-2: Leerraum und Komma erlauben das Anhaengen); das Kontextwort am Ende gibt sie alle aus.
        // r = (1 000 000 - 16 + 11 - 1) / 11 = 999 994 / 11 = 90 908 (11 * 90 908 + 16 = 1 000 004 >= 1 000 000,
        // 11 * 90 907 + 16 = 999 993 < 1 000 000). Funde = r + 1 = 90 909, je Segment ein Fund (nachgestellt,
        // gueltig: 0.80, wahrscheinlich), Typ LANR; kein IK-Kontext, keine IP, kein Pfad: nur der LANR-Erkenner
        // liefert.
        pruefeNachtragFall("a offene Segmente", "", LANR_GUELTIG + ", ", LANR_GUELTIG + " (LANR)", 90_908, 90_909,
                ConfidenceLevel.WAHRSCHEINLICH);
    }

    @Test
    void ak21_nachtragB_kontextwortMitUnsichtbaremZeichenVorJederNummer() {
        // Baustein "Praxis" + U+200B + "LANR " + <LANR> + " " = 6 + 1 + 5 + 9 + 1 = 22 Zeichen. Je Wiederholung bilden
        // die Teilstuecke "Praxis" und "LANR" einen Lauf (R-1); "LANR" ist Kontextwort, die Zahl steht direkt dahinter
        // (0.95, sicher); "Praxis" vor dem Kontextwort des Laufs leert nur offene Segmente.
        // r = (1 000 000 - 0 - 0 + 21) / 22 = 45 455 (22 * 45 455 = 1 000 010 >= 1 000 000,
        // 22 * 45 454 = 999 988 < 1 000 000). Funde = r = 45 455, alle Typ LANR, sicher.
        pruefeNachtragFall("b unsichtbares Zeichen", "", "Praxis\u200BLANR " + LANR_GUELTIG + " ", "", 45_455, 45_455,
                ConfidenceLevel.SICHER);
    }

    @Test
    void ak21_nachtragC_nummerierteListeUnterEinemKontextwort() {
        // Text: Praefix "LANR:\n" (6 Zeichen), dann r mal "1. <LANR>\n" (3 + 9 + 1 = 13 Zeichen). Der Listenpunkt "1."
        // am Zeilenanfang ist neutral (R-3, kein Fuellwort, kein Satzende), die einfachen Umbrueche beenden den Kontext
        // nicht. Je Zeile ein Fund, bei weiterem Kontext (Umbruch) 0.80, wahrscheinlich.
        // r = (1 000 000 - 6 - 0 + 13 - 1) / 13 = 1 000 006 / 13 = 76 923 (6 + 13 * 76 923 = 1 000 005 >= 1 000 000,
        // 6 + 13 * 76 922 = 999 992 < 1 000 000). Funde = r = 76 923.
        pruefeNachtragFall("c nummerierte Liste", "LANR:\n", "1. " + LANR_GUELTIG + "\n", "", 76_923, 76_923,
                ConfidenceLevel.WAHRSCHEINLICH);
    }

    // ------------------------------------------------------------------ AK-22: KVNR (kvnr-bsnr-erkennung T-8)

    private static final String BACKSLASH = String.valueOf((char) 0x5C);

    /**
     * Die KVNR-Lastfaelle aus AK-22 und dem Plan zu T-8 (Hinweis H-6), je mit {@code TextScanner.standard()},
     * Pseudonymisierung und Rueckuebersetzung. Aufbau: Praefix, Baustein, Schluss, Bausteinlaenge, Wiederholungen r
     * (Formel {@code (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein|}, ganzzahlig), erwartete
     * KVNR-Funde je Stufe (sicher, wahrscheinlich, fraglich), erwartete Funde anderer Typen und der Text, der im
     * pseudonymisierten Text nicht stehen darf ({@code null}: keiner). Die Zahlen folgen aus den Regeln (Plan Kap.
     * 4.2), nicht aus einem Probelauf. Die Nullnummer {@code KVNR_NULL} ist gueltig (zehn Zeichen); die Aufzaehlung
     * hinter einem einzigen Kontextwort (Fall 06) ist die einzige Moeglichkeit, ueber 70 000 Funde zu erreichen, weil
     * mit wiederholtem Kontextwort nur 62 500 Funde je Million Zeichen entstehen (Fall 07, Hinweis H-6).
     */
    static Stream<Arguments> kvnrLastFaelle() {
        String nullnummer = KVNR_NULL;
        String unguelt = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        String ip = IK_FORM_DOKU_IP;
        String uz = String.valueOf(ZWSP);
        return Stream.of(
            // 01 "S" + 999 999 Nullen: ein Block mit ueber 12 Ziffern, kein Segment: 0 Funde
            Arguments.of("01 Buchstabe und nur Ziffern", "S", "0", "", 1, 999_999, 0, 0, 0, 0, null),
            // 02 "KVNR S" (6) + 999 994 Nullen: ueber 12 Ziffern, kein Segment: 0 Funde
            Arguments.of("02 KVNR Buchstabe und nur Ziffern", "KVNR S", "0", "", 1, 999_994, 0, 0, 0, 0, null),
            // 03 "A1" = 2 Zeichen, r = (1 000 000 + 1) / 2 = 500 000; ohne Kontextwort: 0 Funde
            Arguments.of("03 A1 wiederholt", "", "A1", "", 2, 500_000, 0, 0, 0, 0, null),
            // 04 "KVNR " (5), "Z0 " = 3 Zeichen, r = (1 000 000 - 5 + 2) / 3 = 333 332; ein Buchstabe und eine Ziffer
            // sind ein Fuellwort, ab dem vierten ist der Kontext erloschen: 0 Funde
            Arguments.of("04 KVNR und Z0 wiederholt", "KVNR ", "Z0 ", "", 3, 333_332, 0, 0, 0, 0, null),
            // 05 "KVNR " (5), "Z" = 1 Zeichen, r = (1 000 000 - 5 + 1 - 1) / 1 = 999 995; ein Lauf aus Buchstaben: 0
            Arguments.of("05 KVNR und Z wiederholt", "KVNR ", "Z", "", 1, 999_995, 0, 0, 0, 0, null),
            // 06 Aufzaehlung: "KVNR " (5), Nullnummer und Leerzeichen = 11 Zeichen, r = (1 000 000 - 5 + 10) / 11 =
            // 90 909 (> 70 000): die erste Nummer steht direkt hinter dem Kontextwort (0,95 sicher), alle weiteren in
            // der Aufzaehlung (0,80 wahrscheinlich): 1 und 90 908
            Arguments.of("06 Aufzaehlung hinter einem Kontextwort", "KVNR ", nullnummer + " ", "", 11, 90_909, 1,
                90_908, 0, 0, nullnummer),
            // 07 Kontextwort und Nullnummer wiederholt: "KVNR " + Nullnummer + " " = 16 Zeichen, r = (1 000 000 + 15) /
            // 16 = 62 500: je Wiederholung direkt hinter dem Kontextwort, 0,95 sicher: 62 500
            Arguments.of("07 Kontextwort und Nullnummer wiederholt", "", "KVNR " + nullnummer + " ", "", 16, 62_500,
                62_500, 0, 0, 0, nullnummer),
            // 08 "K V N R " = 8 Zeichen, r = (1 000 000 + 7) / 8 = 125 000; eine einzige Sperrfolge ohne Nummer: 0
            Arguments.of("08 K V N R wiederholt", "", "K V N R ", "", 8, 125_000, 0, 0, 0, 0, null),
            // 09 "K V N R " + Nullnummer + " " = 19 Zeichen, r = (1 000 000 + 18) / 19 = 52 632; gesperrtes Kontextwort
            // hat den Deckel 0,80: je Wiederholung 1 wahrscheinlich
            Arguments.of("09 K V N R und Nullnummer wiederholt", "", "K V N R " + nullnummer + " ", "", 19, 52_632,
                0, 52_632, 0, 0, nullnummer),
            // 10 Weit-Schreibweise "K. V. N. R. " (12) + Nullnummer + " " = 23 Zeichen, r = (1 000 000 + 22) / 23 =
            // 43 479; Deckel 0,80: je Wiederholung 1 wahrscheinlich
            Arguments.of("10 K. V. N. R. und Nullnummer wiederholt", "", "K. V. N. R. " + nullnummer + " ", "", 23,
                43_479, 0, 43_479, 0, 0, nullnummer),
            // 11 "KVNR" (4) + 999 996 Leerzeichen: Kontextwort ohne Nummer: 0 Funde
            Arguments.of("11 KVNR und nur Leerzeichen", "KVNR", " ", "", 1, 999_996, 0, 0, 0, 0, null),
            // 12 "KV-Nr. " (7) + Nullnummer + " " = 18 Zeichen, r = (1 000 000 + 17) / 18 = 55 556: 0,95 sicher
            Arguments.of("12 KV-Nr. und Nullnummer wiederholt", "", "KV-Nr. " + nullnummer + " ", "", 18, 55_556,
                55_556, 0, 0, 0, nullnummer),
            // 13 KV-Nr. und reine Ziffernfolge (neun Ziffern, "KV-Nr." gilt nur fuer die Buchstabenform, G-K7):
            // "KV-Nr. " + LANR + " " = 17 Zeichen, r = (1 000 000 + 16) / 17 = 58 824: 0 Funde
            Arguments.of("13 KV-Nr. und reine Ziffernfolge wiederholt", "", "KV-Nr. " + LANR_GUELTIG + " ", "", 17,
                58_824, 0, 0, 0, 0, null),
            // 14 Versichertennummer und reine Ziffernfolge: "Versichertennummer " (19) + 9 + 1 = 29 Zeichen,
            // r = (1 000 000 + 28) / 29 = 34 483; je Wiederholung eine abweichende Form 0,40 fraglich
            Arguments.of("14 Versichertennummer und reine Ziffernfolge wiederholt", "",
                "Versichertennummer " + LANR_GUELTIG + " ", "", 29, 34_483, 0, 0, 34_483, 0, null),
            // 15 unsichtbares Zeichen vor dem Buchstaben, Aufzaehlung: "KVNR " (5), ZWSP + Nullnummer + " " = 12
            // Zeichen, r = (1 000 000 - 5 + 11) / 12 = 83 333: 1 sicher und 83 332 wahrscheinlich
            Arguments.of("15 unsichtbares Zeichen vor dem Buchstaben in der Aufzaehlung", "KVNR ",
                uz + nullnummer + " ", "", 12, 83_333, 1, 83_332, 0, 0, nullnummer),
            // 16 "KVNR " (5), ein Lauf aus 999 985 unsichtbaren Zeichen, Nullnummer (10) am Ende: genau 1 Fund 0,95
            Arguments.of("16 Lauf unsichtbarer Zeichen vor dem Buchstaben", "KVNR ", uz, nullnummer, 1, 999_985, 1,
                0, 0, 0, nullnummer),
            // 17 ZWSP + "Z" = 2 Zeichen, r = 500 000; ein einziger Lauf ohne Ziffern: 0 Funde
            Arguments.of("17 unsichtbares Zeichen und Buchstabe wiederholt", "", uz + "Z", "", 2, 500_000, 0, 0, 0,
                0, null),
            // 18 offene ungueltige Nummer (10 Zeichen) und Leerzeichen (11), dann "Wort-" = 5 Zeichen,
            // r = (1 000 000 - 11 + 4) / 5 = 199 998; ohne Kontextwort: 0 Funde
            Arguments.of("18 Kompositum ohne Kontextwort", unguelt + " ", "Wort-", "", 5, 199_998, 0, 0, 0, 0, null),
            // 19 offene Nummer (11), "a-" = 2 Zeichen, r = (1 000 000 - 11 + 1) / 2 = 499 995; 0 Funde
            Arguments.of("19 Einzelbuchstabe und Strich wiederholt", unguelt + " ", "a-", "", 2, 499_995, 0, 0, 0, 0,
                null),
            // 20 nachgestelltes Kompositum: Nullnummer + " (Pat.-KVNR)" (12) + zwei Zeilenumbrueche = 24 Zeichen,
            // r = (1 000 000 + 23) / 24 = 41 667; je Wiederholung nachgestellt 0,80 wahrscheinlich
            Arguments.of("20 Nummer mit nachgestelltem Kompositum wiederholt", "",
                nullnummer + " (Pat.-KVNR)\n\n", "", 24, 41_667, 0, 41_667, 0, 0, nullnummer),
            // 21 Kontextwort am Zeilenende vor der Nummer in der naechsten Zeile: "KVNR" + LF (5), Nullnummer + LF +
            // "KVNR" + LF = 16 Zeichen, r = (1 000 000 - 5 + 15) / 16 = 62 500; jede Nummer steht hinter "KVNR" und
            // einem Umbruch (0,80); das Kontextwort hinter der Nummer in der naechsten Zeile (G-K11) fuegt nichts hinzu
            Arguments.of("21 Kontextwort am Zeilenende vor der Nummer", "KVNR\n", nullnummer + "\nKVNR\n", "", 16,
                62_500, 0, 62_500, 0, 0, null),
            // 22 IPv4-Gestalt: "KVNR " (5), "Z" + IP + " " = 14 Zeichen, r = (1 000 000 - 5 + 13) / 14 = 71 429; der
            // Buchstabe gehoert nicht zur Kette, je Wiederholung gewinnt der IP-Fund (0,85): 71 429 Funde Typ IP
            Arguments.of("22 Buchstabe und IPv4-Gestalt wiederholt", "KVNR ", "Z" + ip + " ", "", 14, 71_429, 0, 0,
                0, 71_429, ip),
            // 23 Zuschneiden, abwechselnd IP und Nummer: "KVNR " (5), IP + " " + Nullnummer + " " = 24 Zeichen,
            // r = (1 000 000 - 5 + 23) / 24 = 41 667; je Wiederholung ein IP-Fund und ein KVNR-Fund (0,80)
            Arguments.of("23 abwechselnd IP und Nummer", "KVNR ", ip + " " + nullnummer + " ", "", 24, 41_667, 0,
                41_667, 0, 41_667, nullnummer),
            // 24 abwechselnd Unix-Pfad mit drei Segmenten und Nummer: "/a/b/c " (7) + Nullnummer + " " = 18 Zeichen,
            // r = (1 000 000 - 5 + 17) / 18 = 55 556; je Wiederholung ein Pfad-Fund und ein KVNR-Fund (0,80)
            Arguments.of("24 abwechselnd Unix-Pfad und Nummer", "KVNR ", "/a/b/c " + nullnummer + " ", "", 18,
                55_556, 0, 55_556, 0, 55_556, nullnummer),
            // 25 abwechselnd Laufwerkspfad und Nummer: "C:\x " (5) + Nullnummer + " " = 16 Zeichen,
            // r = (1 000 000 - 5 + 15) / 16 = 62 500; je Wiederholung ein Pfad-Fund und ein KVNR-Fund (0,80)
            Arguments.of("25 abwechselnd Laufwerkspfad und Nummer", "KVNR ", "C:" + BACKSLASH + "x " + nullnummer
                + " ", "", 16, 62_500, 0, 62_500, 0, 62_500, nullnummer),
            // 26 Restfunde: "KVNR " + Buchstabe und drei Ziffern + Leerzeichen + sechs Ziffern in der Adresse
            // "...@example.invalid " = 34 Zeichen, r = (1 000 000 + 33) / 34 = 29 412; je Wiederholung ein
            // E-Mail-Fund und ein Restfund (Quelle kvnr-rest, Pruefziffer falsch: 0,50 fraglich)
            Arguments.of("26 Restfund neben E-Mail wiederholt", "", "KVNR " + unguelt.substring(0, 4) + " "
                + unguelt.substring(4, 7) + "." + unguelt.substring(7) + "@example.invalid ", "", 34, 29_412, 0, 0,
                29_412, 29_412, unguelt.substring(0, 4)),
            // 27 nachgestelltes Kontextwort: Nullnummer + " (KVNR) " = 18 Zeichen, r = (1 000 000 + 17) / 18 =
            // 55 556; die erste Nummer steht vor dem Kontextwort (nachgestellt 0,80), jede weitere direkt dahinter
            // (0,95): 1 wahrscheinlich und 55 555 sicher
            Arguments.of("27 Nummer mit nachgestelltem Kontextwort wiederholt", "", nullnummer + " (KVNR) ", "", 18,
                55_556, 55_555, 1, 0, 0, nullnummer),
            // 28 bis 33 abgebrochene Beinahe-Kontextwoerter vor der Nummer mit falscher Pruefziffer (Regel 12: ohne
            // Kontextwort entsteht kein Fund, auch nicht in einer spaeteren Erkennung ohne Kontextwort): 0 Funde;
            // Bausteinlaenge je Wort + Nummer (10) + Leerzeichen, r = (1 000 000 + Bausteinlaenge - 1) / Bausteinlaenge
            Arguments.of("28 K. V. N. und Nummer", "", "K. V. N. " + unguelt + " ", "", 20, 50_000, 0, 0, 0, 0, null),
            Arguments.of("29 KVN und Nummer", "", "KVN " + unguelt + " ", "", 15, 66_667, 0, 0, 0, 0, null),
            Arguments.of("30 KVNRX und Nummer", "", "KVNRX " + unguelt + " ", "", 17, 58_824, 0, 0, 0, 0, null),
            Arguments.of("31 KV allein und Nummer", "", "KV " + unguelt + " ", "", 14, 71_429, 0, 0, 0, 0, null),
            Arguments.of("32 Versichertennumme und Nummer", "", "Versichertennumme " + unguelt + " ", "", 29, 34_483,
                0, 0, 0, 0, null),
            Arguments.of("33 KV-N und Nummer", "", "KV-N " + unguelt + " ", "", 16, 62_500, 0, 0, 0, 0, null),
            // 34 Kontextwort, Buchstabe und IP mit Praefixlaenge (CIDR): "KVNR Z" + IP + "/24 " = 6 + 12 + 3 + 1 = 22
            // Zeichen, r = (1 000 000 + 21) / 22 = 45 455; je Wiederholung gewinnt der IP-Fund (die Praefixlaenge
            // gehoert nicht dazu): 45 455 Funde Typ IP, kein KVNR-Fund
            Arguments.of("34 Buchstabe und IP mit Praefixlaenge wiederholt", "", "KVNR Z" + ip + "/24 ", "", 22,
                45_455, 0, 0, 0, 45_455, null),
            // 35 "KVNR Z" (6) + "0." = 2 Zeichen, r = (1 000 000 - 6 + 2 - 1) / 2 = 499 997; ein einziger Lauf aus
            // Punkt-Ziffern-Paaren: genau ein IP-Fund (0,85) ueber die ganze Kette, kein KVNR-Fund
            Arguments.of("35 Punkt-Ziffern-Kette hinter KVNR und Buchstabe", "KVNR Z", "0.", "", 2, 499_997, 0, 0, 0,
                1, null),
            // 36 ein URL-Fund umschliesst alle Kandidaten: "http://x/" (9), "KVNR/" + Nullnummer + "/" = 16 Zeichen,
            // Schluss "y" (1), r = (1 000 000 - 9 - 1 + 15) / 16 = 62 500; genau ein URL-Fund, alle KVNR-Kandidaten
            // darin entfallen (Bestand hat Vorrang), die Nullnummer steht nicht im Ausgabetext
            Arguments.of("36 ein URL-Fund umschliesst viele Kandidaten", "http://x/", "KVNR/" + nullnummer + "/", "y",
                16, 62_500, 0, 0, 0, 1, nullnummer),
            // 37 wie 36 mit einem Pfad: "/a/" (3), 16 Zeichen, Schluss "y" (1), r = (1 000 000 - 3 - 1 + 15) / 16 =
            // 62 500; genau ein Pfad-Fund, alle KVNR-Kandidaten darin entfallen
            Arguments.of("37 ein Pfad-Fund umschliesst viele Kandidaten", "/a/", "KVNR/" + nullnummer + "/", "y", 16,
                62_500, 0, 0, 0, 1, nullnummer));
    }

    /**
     * Kompositum mit zwei sehr langen Woertern am Strich hinter einer offenen Nummer, ueber {@code TextScanner.
     * standard()}: Die offene (ungueltige) Nummer, ein Leerzeichen, 500 000 Buchstaben, ein Strich und 500 000
     * Buchstaben (Laenge 10 + 1 + 500 000 + 1 + 500 000 = 1 000 012, ein einziger Lauf, kein Kontextwort): 0 Funde, der
     * Text bleibt unveraendert, Roundtrip gleich. Der Erkenner allein ist in {@code KvnrKontextTest} geprueft.
     */
    @Test
    void ak22_kvnr_kompositumMitZweiSehrLangenWoertern_dreiSchritteJeUnterZweiSekunden_keinFund() {
        String unguelt = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        drei(unguelt + " " + "a".repeat(5_000) + "-" + "b".repeat(5_000), GRENZE_GROSSER_TEXT);
        String text = unguelt + " " + "a".repeat(500_000) + "-" + "b".repeat(500_000);
        assertEquals(1_000_012, text.length(), "Textlaenge");
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        assertEquals(0, d.funde().size(), "Fundzahl");
        assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert");
        pruefeRoundtrip(text, d);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvnrLastFaelle")
    void ak22_kvnr_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name, String praefix,
            String baustein, String schluss, int bausteinLaenge, int r, int sicher, int wahrscheinlich, int fraglich,
            int andere, String verboten) {
        assertEquals(bausteinLaenge, baustein.length(), "Bausteinlaenge " + name);
        assertEquals(r, kvWiederholungen(praefix, baustein, schluss, MIN_ZEICHEN), "Wiederholungen " + name);
        // Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart
        String warm = kvText(praefix, baustein, schluss, 10_000);
        KvnrSynthetischWaechter.assertKvnrSynthetisch(warm);
        assertEquals(0, neunerfenster(warm, "00")[1], "Waechter Aufwaermtext " + name);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, schluss, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        // Testdatenregeln: keine gueltige KVNR-Form ohne 0000, kein gueltiges Neunerfenster ohne Praefix 00
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        assertEquals(0, neunerfenster(text, "00")[1], "Waechter " + name);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        List<Finding> funde = d.funde();
        List<Finding> kvnr = funde.stream().filter(f -> FindingType.KVNR.equals(f.type())).toList();
        assertEquals(sicher + wahrscheinlich + fraglich + andere, funde.size(), "Fundzahl " + name);
        assertEquals(sicher + wahrscheinlich + fraglich, kvnr.size(), "KVNR-Funde " + name);
        assertEquals(sicher, kvnr.stream().filter(f -> f.level() == ConfidenceLevel.SICHER).count(),
                "Stufe sicher " + name);
        assertEquals(wahrscheinlich,
                kvnr.stream().filter(f -> f.level() == ConfidenceLevel.WAHRSCHEINLICH).count(),
                "Stufe wahrscheinlich " + name);
        assertEquals(fraglich, kvnr.stream().filter(f -> f.level() == ConfidenceLevel.FRAGLICH).count(),
                "Stufe fraglich " + name);
        pruefeRoundtrip(text, d);
        if (verboten != null) {
            assertFalse(d.pseudo().text().contains(verboten), "Klartext im Ausgabetext " + name);
        } else if (funde.isEmpty()) {
            assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert " + name);
        }
    }

    /**
     * Fall 26 im Einzelnen: Die Restfunde haben die Quelle {@code kvnr-rest}, die E-Mails den Typ EMAIL; die Zahl der
     * Restfunde ist die Zahl der Wiederholungen (jede Wiederholung hat ihr eigenes Kontextwort).
     */
    @Test
    void ak22_kvnr_restfundeNebenEmails_quelleKvnrRestUndTypEmail() {
        String unguelt = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        String baustein = "KVNR " + unguelt.substring(0, 4) + " " + unguelt.substring(4, 7) + "."
                + unguelt.substring(7) + "@example.invalid ";
        String text = baustein.repeat(1_000);
        List<Finding> funde = TextScanner.standard().scan(text);
        assertEquals(2_000, funde.size(), "Fundzahl");
        for (int i = 0; i < funde.size(); i++) {
            Finding f = funde.get(i);
            if (i % 2 == 0) {
                assertEquals(FindingType.KVNR, f.type(), "Typ des Restfundes");
                assertTrue("kvnr-rest".equals(f.source()), "Quelle des Restfundes");
            } else {
                assertEquals(FindingType.EMAIL, f.type(), "Typ der E-Mail");
            }
        }
    }

    // ------------------------------------------------------------ AK-22: KVNR ohne Kontextwort, Zusatzfaelle (T-11)

    /**
     * Ein Lastfall mit der erwarteten Fundzahl je Typ: Praefix, Baustein, Schluss, Bausteinlaenge, Wiederholungen r
     * (Formel {@code (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein|}, ganzzahlig, aus den Regeln
     * und nicht aus einem Probelauf), erwartete KVNR-Funde (alle Stufe fraglich, Quelle {@code kvnr-ohne-kontext}),
     * erwartete IP-Funde und der Text, der im pseudonymisierten Text nicht stehen darf ({@code null}: keiner). Mit
     * Aufwaermlauf, je Schritt unter 2 Sekunden, Fundzahl exakt, Roundtrip zeichengleich. Die Hilfe dient auch den
     * beiden vorgemerkten Faellen aus security T-8 unten; diese sind nicht 1b-Inventar.
     */
    private static void zusatzLastFall(String name, String praefix, String baustein, String schluss,
            int bausteinLaenge, int r, int kvnrFraglich, int ip, String verboten) {
        assertEquals(bausteinLaenge, baustein.length(), "Bausteinlaenge " + name);
        assertEquals(r, kvWiederholungen(praefix, baustein, schluss, MIN_ZEICHEN), "Wiederholungen " + name);
        String warm = kvText(praefix, baustein, schluss, 10_000);
        KvnrSynthetischWaechter.assertKvnrSynthetisch(warm);
        assertEquals(0, neunerfenster(warm, "00")[1], "Waechter Aufwaermtext " + name);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, schluss, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        assertEquals(0, neunerfenster(text, "00")[1], "Waechter " + name);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        List<Finding> funde = d.funde();
        List<Finding> kvnr = funde.stream().filter(f -> FindingType.KVNR.equals(f.type())).toList();
        assertEquals(kvnrFraglich + ip, funde.size(), "Fundzahl " + name);
        assertEquals(kvnrFraglich, kvnr.size(), "KVNR-Funde " + name);
        assertEquals(kvnrFraglich, kvnr.stream().filter(f -> f.level() == ConfidenceLevel.FRAGLICH).count(),
                "Stufe fraglich " + name);
        assertEquals(ip, funde.stream().filter(f -> FindingType.IP.equals(f.type())).count(), "IP-Funde " + name);
        pruefeRoundtrip(text, d);
        if (verboten != null) {
            assertFalse(d.pseudo().text().contains(verboten), "Klartext im Ausgabetext " + name);
        } else if (funde.isEmpty()) {
            assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert " + name);
        }
    }

    /**
     * Die Lastfaelle der KVNR ohne Kontextwort (Teil 1b, AK-22): 1 000 000 Zeichen, Scan, Pseudonymisierung und
     * Rueckuebersetzung je unter 2 Sekunden. Fall 01 ist der Fall aus AK-22 ({@code <gueltige KVNR> } wiederholt:
     * aufgerundet 10 hoch 6 durch 11 = 90 910 Funde, ueber 80 000). Fall 02: LANR-Kontextwort direkt vor der Form
     * (K-1): je Wiederholung ein KVNR-Fund ueber Buchstabe und Ziffern. Fall 03: ein Wort der Negativliste direkt davor
     * (D-4): kein Fund. Fall 04: Punkt und Leerzeichen hinter der Nummer (Wortgrenze hinten): je Wiederholung ein Fund.
     */
    static Stream<Arguments> kvnrOhneKontextLastFaelle() {
        String n = KVNR_NULL;
        return Stream.of(
            // 01 Nullnummer (zehn Zeichen) und Leerzeichen = 11 Zeichen, r = (1 000 000 + 10) / 11 = 90 910
            Arguments.of("01 gueltige Nummer und Leerzeichen wiederholt", "", n + " ", "", 11, 90_910, 90_910, 0, n),
            // 02 "LANR " + Nullnummer + " " = 16 Zeichen, r = (1 000 000 + 15) / 16 = 62 500; der KVNR-Fund
            // umschliesst den LANR-Fund auf den Ziffern: 62 500 Funde Typ KVNR
            Arguments.of("02 LANR-Kontextwort und gueltige Nummer wiederholt", "", "LANR " + n + " ", "", 16, 62_500,
                62_500, 0, n),
            // 03 "Rechnungsnummer " (16) + Nullnummer + " " = 27 Zeichen, r = (1 000 000 + 26) / 27 = 37 038; kein Fund
            Arguments.of("03 Negativkontext und gueltige Nummer wiederholt", "", "Rechnungsnummer " + n + " ", "",
                27, 37_038, 0, 0, null),
            // 04 Nullnummer + ". " = 12 Zeichen, r = (1 000 000 + 11) / 12 = 83 334; je Wiederholung ein Fund
            Arguments.of("04 gueltige Nummer, Punkt und Leerzeichen wiederholt", "", n + ". ", "", 12, 83_334, 83_334,
                0, n));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("kvnrOhneKontextLastFaelle")
    void ak22_kvnrOhneKontext_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name,
            String praefix, String baustein, String schluss, int bausteinLaenge, int r, int kvnr, int ip,
            String verboten) {
        zusatzLastFall(name, praefix, baustein, schluss, bausteinLaenge, r, kvnr, ip, verboten);
    }

    /**
     * Vorgemerkt aus security T-8 (Nachtrag zu T-11, Punkt 3 b): gesperrt geschriebenes Kontextwort, Buchstabe und
     * IP-Adresse. Baustein {@code K V N R } (8 Zeichen), {@code Z}, Dokumentations-IP (12 Zeichen) und Leerzeichen = 22
     * Zeichen, r = (1 000 000 + 21) / 22 = 45 455. Je Wiederholung gewinnt der IP-Fund (der Buchstabe ist kein
     * Nummernanfang vor der IPv4-Gestalt, der Bestand hat Vorrang): 45 455 IP-Funde, kein KVNR-Fund. Nicht 1b-Inventar.
     */
    @Test
    void ak22_kvnr_gesperrtesKontextwortBuchstabeUndIp_dreiSchritteJeUnterZweiSekunden() {
        zusatzLastFall("gesperrtes Kontextwort, Buchstabe und IP", "", "K V N R Z" + IK_FORM_DOKU_IP + " ", "", 22,
                45_455, 0, 45_455, null);
    }

    /**
     * Vorgemerkt aus security T-8 (Nachtrag zu T-11, Punkt 3 b): ein Lauf unsichtbarer Zeichen zwischen Kontextwort und
     * einem Buchstaben mit IP-Adresse. Praefix {@code KVNR } (5), Lauf aus 999 982 Zeichen U+200B, Schluss {@code Z}
     * und Dokumentations-IP (13 Zeichen): genau ein IP-Fund, kein KVNR-Fund. Nicht 1b-Inventar.
     */
    @Test
    void ak22_kvnr_laufUnsichtbarerZeichenUndIp_dreiSchritteJeUnterZweiSekunden() {
        zusatzLastFall("Lauf unsichtbarer Zeichen und IP", "KVNR ", String.valueOf(ZWSP), "Z" + IK_FORM_DOKU_IP, 1,
                999_982, 0, 1, null);
    }

    // ------------------------------------------------------------------ AK-33: BSNR (T-15)

    /**
     * Ein Lastfall der BSNR mit {@code TextScanner.standard()}, Pseudonymisierung und Rueckuebersetzung (je Schritt
     * unter 2 Sekunden, Aufwaermlauf mit 10 000 Zeichen): Praefix, Baustein, Schluss, Bausteinlaenge, Wiederholungen r
     * (Formel {@code (1 000 000 - |Praefix| - |Schluss| + |Baustein| - 1) / |Baustein|}, ganzzahlig, aus den Regeln und
     * nicht aus einem Probelauf), erwartete BSNR-Funde je Stufe (sicher, wahrscheinlich, fraglich) und der Text, der im
     * pseudonymisierten Text nicht stehen darf ({@code null}: keiner). Es gibt keine Funde anderer Typen. Vor dem Scan
     * besteht der Text (auch der Aufwaermtext) den Waechter {@link BsnrSynthetischWaechter}.
     */
    private static void bsnrLastFall(String name, String praefix, String baustein, String schluss,
            int bausteinLaenge, int r, int sicher, int wahrscheinlich, int fraglich, String verboten) {
        assertEquals(bausteinLaenge, baustein.length(), "Bausteinlaenge " + name);
        assertEquals(r, kvWiederholungen(praefix, baustein, schluss, MIN_ZEICHEN), "Wiederholungen " + name);
        String warm = kvText(praefix, baustein, schluss, 10_000);
        BsnrSynthetischWaechter.assertBsnrSynthetisch(warm);
        drei(warm, GRENZE_GROSSER_TEXT);

        String text = kvText(praefix, baustein, schluss, MIN_ZEICHEN);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);

        Durchlauf d = drei(text, GRENZE_PATHOLOGISCH);
        List<Finding> funde = d.funde();
        assertEquals(sicher + wahrscheinlich + fraglich, funde.size(), "Fundzahl " + name);
        assertEquals(funde.size(), funde.stream().filter(f -> FindingType.BSNR.equals(f.type())).count(),
                "alle Funde vom Typ BSNR " + name);
        assertEquals(sicher, funde.stream().filter(f -> f.level() == ConfidenceLevel.SICHER).count(),
                "Stufe sicher " + name);
        assertEquals(wahrscheinlich, funde.stream().filter(f -> f.level() == ConfidenceLevel.WAHRSCHEINLICH).count(),
                "Stufe wahrscheinlich " + name);
        assertEquals(fraglich, funde.stream().filter(f -> f.level() == ConfidenceLevel.FRAGLICH).count(),
                "Stufe fraglich " + name);
        pruefeRoundtrip(text, d);
        if (verboten != null) {
            assertFalse(d.pseudo().text().contains(verboten), "Klartext im Ausgabetext " + name);
        } else if (funde.isEmpty()) {
            assertTrue(text.equals(d.pseudo().text()), "Text ohne Fund veraendert " + name);
        }
    }

    /**
     * Die BSNR-Lastfaelle aus AK-33 und dem Plan zu T-15, je 1 000 000 Zeichen. Fall 01: {@code BSNR } und nur Ziffern
     * (ueber 12 Ziffern, kein Fund). Fall 02: {@code BSNR } und {@code 0 } wiederholt: r = (1 000 000 - 5 + 1) / 2 =
     * 499 998 einzelne Ziffern mit
     * je einem Leerzeichen; der Scanner liest je neun Ziffern (mit den acht Trennern 17 Zeichen) als einen
     * Kandidaten, also 499 998 / 9 = 55 555 Funde mit Rest 3 Ziffern (unter sechs Ziffern kein Fund); Praefix
     * {@code 00}: Regionalcode unbekannt, alle 55 555 fraglich. Fall 03: {@code BSNR} und nur {@code -}: kein Fund.
     * Fall 04: {@code BSNR } und die Nullnummer mit Leerzeichen wiederholt (10 Zeichen): r = (1 000 000 - 5 + 9) / 10 =
     * 100 000 Funde (ueber 70 000), Regionalcode {@code 00}: alle fraglich. Fall 05: {@code B S N R } wiederholt ohne
     * Nummer: kein Fund. Fall 06: bekannte BSNR und {@code  (BSNR) } wiederholt (17 Zeichen): r = (1 000 000 + 16) /
     * 17 = 58 824 Funde, der erste nachgestellt (0,80 wahrscheinlich), jeder weitere direkt hinter dem Kontextwort
     * (0,90 sicher).
     *
     * <p>Ohne eigenen Lasttest: Gruppierte Ziffern mit bekanntem Regionalcode (zum Beispiel {@code BSNR } und
     * {@code 1 } wiederholt) bilden ebenso Kandidaten aus je neun Ziffern wie Fall 02, nur mit hoeherer Stufe; solche
     * Texte bestehen den Waechter nicht (kein Praefix {@code 00}, kein Nullblock), und fuer ihn gibt es keine
     * Ausnahme. Das Zeitverhalten der Kandidatenbildung belegt Fall 02.
     */
    static Stream<Arguments> bsnrLastFaelle() {
        return Stream.of(
            // 01 "BSNR " (5) und 999 995 Nullen: ein Block mit ueber 12 Ziffern: 0 Funde
            Arguments.of("01 BSNR und nur Ziffern", "BSNR ", "0", "", 1, 999_995, 0, 0, 0, null),
            // 02 "BSNR " (5), "0 " = 2 Zeichen, r = (1 000 000 - 5 + 1) / 2 = 499 998; 499 998 / 9 = 55 555 Funde
            // (Rest drei Ziffern: kein Fund), Praefix 00 ist kein Regionalcode: 55 555 fraglich
            Arguments.of("02 BSNR und 0 wiederholt", "BSNR ", "0 ", "", 2, 499_998, 0, 0, 55_555,
                "0 ".repeat(8) + "0"),
            // 03 "BSNR" (4) und 999 996 Bindestriche: Kontextwort ohne Ziffern: 0 Funde
            Arguments.of("03 BSNR und nur Bindestrich", "BSNR", "-", "", 1, 999_996, 0, 0, 0, null),
            // 04 "BSNR " (5), Nullnummer und Leerzeichen = 10 Zeichen, r = (1 000 000 - 5 + 9) / 10 = 100 000
            // (ueber 70 000): Regionalcode 00 unbekannt, alle fraglich
            Arguments.of("04 BSNR und Nullnummer wiederholt", "BSNR ", BSNR_NULL + " ", "", 10, 100_000, 0, 0,
                100_000, BSNR_NULL),
            // 05 "B S N R " = 8 Zeichen, r = (1 000 000 + 7) / 8 = 125 000; eine einzige Sperrfolge ohne Nummer: 0
            Arguments.of("05 B S N R wiederholt", "", "B S N R ", "", 8, 125_000, 0, 0, 0, null),
            // 06 bekannte BSNR (9) und " (BSNR) " (8) = 17 Zeichen, r = (1 000 000 + 16) / 17 = 58 824; die erste
            // Nummer steht vor dem Kontextwort (nachgestellt 0,80), jede weitere direkt dahinter (0,90)
            Arguments.of("06 Nummer mit nachgestelltem Kontextwort wiederholt", "", BSNR_BEKANNT + " (BSNR) ", "", 17,
                58_824, 58_823, 1, 0, BSNR_BEKANNT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bsnrLastFaelle")
    void ak33_bsnr_eineMillionZeichen_dreiSchritteJeUnterZweiSekundenMitExakterFundzahl(String name, String praefix,
            String baustein, String schluss, int bausteinLaenge, int r, int sicher, int wahrscheinlich, int fraglich,
            String verboten) {
        bsnrLastFall(name, praefix, baustein, schluss, bausteinLaenge, r, sicher, wahrscheinlich, fraglich, verboten);
    }
}
