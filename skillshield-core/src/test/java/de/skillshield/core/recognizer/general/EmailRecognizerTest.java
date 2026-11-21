package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Alle Testadressen verwenden reservierte Domains (example.invalid, example.org, example.net). */
class EmailRecognizerTest {

    private static final EmailRecognizer R = new EmailRecognizer();
    /** Grenze für AK-49 (je Eingabe und Schritt, strenger als die 10 s aus AK-48/OF-13, damit Quadratisches auffällt). */
    private static final long LIMIT_NANOS = 2_000_000_000L;
    /** Grenze aus AK-48 / OF-13 (TE-4): 10 s je Schritt bei 1 Million Zeichen. */
    private static final long LIMIT_AK48_NANOS = 10_000_000_000L;
    private static final String ADRESSE = "erika.mustermann@example.invalid";
    private static final String KURZ = "erika@example.invalid";
    private static final String ZEHN_HEX = "0123456789";

    // Unsichtbare Formatzeichen, bewusst als Escapes (nie als Rohzeichen im Quelltext)
    private static final String SOFT_HYPHEN = "\u00AD";
    private static final String ZWSP = "\u200B";
    private static final String ZWNJ = "\u200C";
    private static final String ZWJ = "\u200D";
    private static final String LRM = "\u200E";
    private static final String RLM = "\u200F";
    private static final String WORD_JOINER = "\u2060";
    private static final String BOM = "\uFEFF";

    private static List<String> werte(String text) {
        List<Finding> funde = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue());
            assertEquals(FindingType.EMAIL, f.type());
            assertEquals("email", f.source());
            assertEquals(0.95, f.confidence());
            assertEquals(ConfidenceLevel.SICHER, f.level());
            assertTrue(f.start() >= vorigesEnde, "Funde überlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde.stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static void assertEinFund(String text, String erwartet) {
        assertEquals(List.of(erwartet), werte(text), "Text: " + text);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    /** Genau ein Fund, der am Textende endet und die Adresse vollständig enthält (Übererfassung davor erlaubt). */
    private static void assertFundDeckt(String text, String adresse) {
        List<String> w = werte(text);
        assertEquals(1, w.size(), "Text: " + text);
        assertTrue(w.get(0).endsWith(adresse), "Fund deckt die Adresse nicht ab");
        assertEquals(text.length(), R.detect(text).get(0).end());
    }

    /**
     * Genau ein Teilfund: er endet am Textende, enthält die Adresse und hat einen lokalen Teil von 60 bis 64 Zeichen
     * (die letzten höchstens 64 Zeichen vor dem {@code @}); davor bleibt etwas stehen.
     */
    private static void assertTeilfundLokal(String text, String adresse) {
        List<String> w = werte(text);
        assertEquals(1, w.size(), "Text: " + text);
        String fund = w.get(0);
        assertTrue(text.endsWith(fund), "Fund endet nicht am Textende");
        assertTrue(fund.endsWith(adresse), "Fund enthält die Adresse nicht");
        int lokal = fund.indexOf('@');
        assertTrue(lokal >= 60 && lokal <= 64, "lokaler Teil hat " + lokal + " Zeichen");
        assertTrue(fund.length() < text.length(), "kein Teilfund");
    }

    // ---- AK-2 / Position

    @Test
    void ak2Beispiel() {
        String text = "Kontakt: erika.beispiel@example.invalid";
        List<Finding> r = R.detect(text);
        assertEquals(1, r.size());
        assertEquals("erika.beispiel@example.invalid", r.get(0).originalValue());
        assertEquals(9, r.get(0).start());
        assertEquals(text.length(), r.get(0).end());
        assertEquals(FindingType.EMAIL, r.get(0).type());
    }

    @Test
    void nurAdresseUndFundAmTextanfang() {
        assertEinFund(KURZ, KURZ);
    }

    // ---- AK-3 Satzzeichen am Ende, einzeln je Zeichen

    @ParameterizedTest
    @ValueSource(strings = {".", ",", ";", ":", "!", "?", ")", "]", "\"", "'", "-", "_", "...", "’"})
    void abschliessendesZeichenGehoertNichtZumFund(String schluss) {
        assertEinFund("Schreiben Sie an erika.beispiel@example.invalid" + schluss,
                "erika.beispiel@example.invalid");
        assertEinFund("an erika.beispiel@example.invalid" + schluss + " Danke", "erika.beispiel@example.invalid");
    }

    @Test
    void schlusspunktPositionExakt() {
        String text = "Schreiben Sie an erika.beispiel@example.invalid.";
        Finding f = R.detect(text).get(0);
        assertEquals(text.length() - 1, f.end());
    }

    @Test
    void umgebendeKlammernUndAnfuehrungszeichen() {
        assertEinFund("(erika@example.invalid)", KURZ);
        assertEinFund("[erika@example.invalid]", KURZ);
        assertEinFund("\"erika@example.invalid\"", KURZ);
        assertEinFund("'erika@example.invalid'", KURZ);
        assertEinFund("’erika@example.invalid’", KURZ);
        assertEinFund("<erika@example.invalid>", KURZ);
        assertEinFund("Mail: <erika@example.invalid>, bitte", KURZ);
    }

    @Test
    void mehrereAdressen() {
        assertEquals(List.of("a.b@example.invalid", "c@example.org", "d@example.net"),
                werte("a.b@example.invalid, c@example.org; d@example.net."));
        assertEquals(List.of("a@example.invalid", "b@example.invalid"),
                werte("a@example.invalid\nb@example.invalid"));
    }

    @Test
    void direktNebenAnderemText() {
        assertEinFund("Mail:erika@example.invalid", KURZ);
        assertEinFund("erika@example.invalid/pfad", KURZ);
        assertEinFund("erika@example.invalid\terika-ende", KURZ);
        assertEinFund("erika@example.invalid Danke", KURZ);
    }

    // ---- lokaler Teil

    @ParameterizedTest
    @ValueSource(strings = {"a@example.invalid", "erika.beispiel@example.invalid", "erika_b@example.invalid",
            "erika+tag@example.invalid", "erika%x@example.invalid", "erika-b@example.invalid",
            "e123@example.invalid", "1@example.invalid"})
    void erlaubteLokaleTeile(String adresse) {
        assertEinFund(adresse, adresse);
    }

    @Test
    void umlauteImLokalenTeilNieAlsTeiltreffer() {
        assertEinFund("jürgen.müller@example.invalid", "jürgen.müller@example.invalid");
        assertEinFund("Kontakt: ürgen@example.invalid", "ürgen@example.invalid");
        assertEinFund("Kontakt: erika.schön@example.invalid.", "erika.schön@example.invalid");
        // kombinierende Marke (e + U+0301) bleibt im Fund
        assertEinFund("ere\u0301ika@example.invalid", "ere\u0301ika@example.invalid");
    }

    @Test
    void surrogatpaarBuchstabeWirdNichtGetrennt() {
        // U+1D5BA MATHEMATICAL SANS-SERIF SMALL A (Buchstabe, Surrogatpaar)
        String sa = new String(Character.toChars(0x1D5BA));
        assertEinFund(sa + "x@example.invalid", sa + "x@example.invalid");
        assertEinFund("x" + sa + "@example.invalid", "x" + sa + "@example.invalid");
        // Emoji (kein Buchstabe) davor: Fund beginnt dahinter, nie im Surrogatpaar
        String emoji = new String(Character.toChars(0x1F600));
        List<Finding> r = R.detect(emoji + "erika@example.invalid");
        assertEquals(1, r.size());
        assertEquals(emoji.length(), r.get(0).start());
    }

    @Test
    void rueckwaertssucheTrenntKeinSurrogatpaar() {
        String sa = new String(Character.toChars(0x1D5BA));
        // 80 Zeichen (40 Paare) vor dem @: Teilfund aus den letzten 32 Paaren (64 Zeichen)
        assertEinFund(sa.repeat(40) + "@example.invalid", sa.repeat(32) + "@example.invalid");
        // ungerade Grenze: 'a' plus 31 Paare = 63 Zeichen; ein weiteres Paar würde 65 ergeben und bleibt draußen
        assertEinFund(sa.repeat(35) + "a@example.invalid", sa.repeat(31) + "a@example.invalid");
        for (Finding f : R.detect(sa.repeat(35) + "a@example.invalid")) {
            assertFalse(Character.isLowSurrogate(f.originalValue().charAt(0)));
        }
    }

    @Test
    void rueckwaertssucheTrenntKeineKombinationsfolge() {
        // 'e', Marke, 63 'a': die 64-Zeichen-Grenze fiele auf die Marke; der Fund beginnt dahinter (AK-23)
        String text = "e\u0301" + "a".repeat(63) + "@example.invalid";
        assertEinFund(text, "a".repeat(63) + "@example.invalid");
        // Marke innerhalb der 64 Zeichen bleibt dabei
        assertEinFund("e\u0301" + "a".repeat(10) + "@example.invalid", "e\u0301" + "a".repeat(10) + "@example.invalid");
    }

    @Test
    void atAmTextanfangMehrereAtUndAtImFund() {
        assertKeinFund("@");
        assertEinFund("@" + KURZ, KURZ);
        assertEinFund("@@@" + KURZ, KURZ);
        assertEinFund("@a@" + KURZ, "a@" + KURZ);
        // das @ der Adresse selbst löst keine zweite Suche aus: genau ein Fund
        assertEquals(1, R.detect("a@b@erika@example.invalid").size());
    }

    @Test
    void lokalerTeilLaengeGrenze64() {
        String l64 = "a".repeat(64);
        assertEinFund(l64 + "@example.invalid", l64 + "@example.invalid");
        // S-2: 65 Buchstaben ohne Trenner vor dem @ ergeben einen Teilfund aus den letzten 64 (Start bei Index 1)
        String text65 = "a".repeat(65) + "@example.invalid";
        assertEinFund(text65, l64 + "@example.invalid");
        assertEquals(1, R.detect(text65).get(0).start());
        assertEinFund("ü".repeat(65) + "@example.invalid", "ü".repeat(64) + "@example.invalid");
        // Mit einem Trenner im Lauf: die letzten 64 Zeichen vor dem @ (33 a, Punkt, 30 b) bilden den Fund
        assertEinFund("a".repeat(40) + "." + "b".repeat(30) + "@example.invalid",
                "a".repeat(33) + "." + "b".repeat(30) + "@example.invalid");
    }

    @Test
    void langerLokalerTeilTeilfundMitName() {
        String base64 = "QWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg5Ky9BQkNERUZHSElKS0xNTk9QUQ==";
        assertTrue(base64.length() >= 72);
        assertTeilfundLokal(base64.substring(0, 72) + KURZ, KURZ);
        assertTeilfundLokal("abc/" + base64 + KURZ, KURZ);
        // 70 Ziffern: die letzten 64 Zeichen, darunter der Name vollständig
        String ziffern = "7".repeat(70) + KURZ;
        assertEinFund(ziffern, ziffern.substring(ziffern.length() - 80));
        // Aktenzeichen plus 60 X plus Name: 79 Zeichen vor dem @, Fund aus den letzten 64
        String akte = "Aktenzeichen" + "X".repeat(60) + "mueller@example.invalid";
        assertEinFund(akte, akte.substring(akte.length() - (64 + 1 + 15)));
        // zusammengeschriebener Fließtext ohne Leerzeichen, endet in der Adresse
        String fliess = "bittewendensiesichandenzustaendigenmitarbeiterfuerdiegenehmigungsverfahren"
                + "erikamustermann@example.invalid";
        assertEinFund(fliess, fliess.substring(fliess.length() - (64 + 1 + 15)));
    }

    @Test
    void lokalerTeilFehltOderUngueltigeZeichen() {
        assertKeinFund("@example.invalid");
        assertKeinFund(" @example.invalid");
        assertKeinFund("erika @example.invalid");
        // Zeichen außerhalb der Klasse trennen: der Rest ab dort ist der Fund
        assertEinFund("erika,x@example.invalid", "x@example.invalid");
        assertEinFund("erika;x@example.invalid", "x@example.invalid");
        assertEinFund("erika:x@example.invalid", "x@example.invalid");
        assertEinFund("erika\nx@example.invalid", "x@example.invalid");
    }

    // ---- Füllzeichen vor der Adresse

    @Test
    void fuehrendePunkteVorDerAdresse() {
        assertEinFund("E-Mail" + ".".repeat(60) + ADRESSE, ADRESSE);
    }

    @Test
    void fuehrendeUnterstricheVorDerAdresse() {
        assertEinFund("E-Mail:" + "_".repeat(60) + ADRESSE, ADRESSE);
    }

    @Test
    void fuehrendeBindestricheVorDerAdresse() {
        assertEinFund("-".repeat(70) + ADRESSE, ADRESSE);
    }

    @Test
    void gemischteFuellzeichenVorDerAdresse() {
        assertEinFund(".-_".repeat(30) + ADRESSE, ADRESSE);
    }

    @Test
    void wortketteMitPunktenVorDerAdresse() {
        String praefix = "alpha.beta.gamma.delta.epsilon.zeta.eta.theta.iota.kappa.lambda.my.ny.xi.omikron.";
        assertTrue(praefix.length() > 64);
        // Übererfassung: der Fund beginnt höchstens 64 Zeichen vor dem @ und enthält die Adresse vollständig
        assertFundDeckt(praefix + ADRESSE, ADRESSE);
    }

    @Test
    void satzOhneLeerzeichenVorDerAdresse() {
        // Folgt nach einem Satzende kein Leerzeichen, wird das Wort davor mit erfasst (unschädliche Übererfassung)
        assertEinFund("Satz.erika@example.invalid", "Satz.erika@example.invalid");
        assertEinFund("Ende.\nerika@example.invalid", KURZ);
        // gleiche dokumentierte Übererfassung bei Ausrufe- und Fragezeichen ohne Leerzeichen: der Fund ist größer
        // als die Adresse, aber nicht kleiner (Plan-Grundsatz: Fund statt kein Fund)
        assertEinFund("Danke!erika@example.invalid", "Danke!erika@example.invalid");
        assertEinFund("Wirklich?erika@example.invalid", "Wirklich?erika@example.invalid");
        assertEinFund("Danke! erika@example.invalid", KURZ);
    }

    // ---- mehrere @

    @Test
    void zweiAtZeichen() {
        assertKeinFund("a@b@c");
        assertEinFund("a@b@example.invalid", "a@b@example.invalid");
        assertEinFund("erika.mustermann@praxis@example.invalid", "erika.mustermann@praxis@example.invalid");
        assertEinFund("an erika@praxis@example.invalid.", "erika@praxis@example.invalid");
        assertKeinFund("erika@@example.invalid");
    }

    @Test
    void mehrAlsVierAtZeichenTeilfund() {
        // Bis zu vier Paare gehören zum Fund; bei fünf bleibt das erste Paar bewusst außerhalb (Teilfund)
        assertEinFund("a@b@c@d@example.invalid", "a@b@c@d@example.invalid");
        assertEinFund("a@b@c@d@e@example.invalid", "b@c@d@e@example.invalid");
    }

    // ---- S-1: ein @ direkt hinter der Adresse verhindert den Fund nicht

    @Test
    void atDirektHinterDerAdresse() {
        assertEinFund("erika@example.invalid@", KURZ);
        assertEinFund("Bitte an erika@example.invalid@home weiterleiten", KURZ);
        assertEinFund("erika@example.invalid@ Montag", KURZ);
        assertEinFund("erika.mustermann@example.invalid@@", ADRESSE);
        assertEinFund("erika@example.invalid@2x", KURZ);
        assertEinFund("erika@praxis.example.invalid@", "erika@praxis.example.invalid");
        // mit vorangestellten Paaren: der Fund enthält die Adresse vollständig (Paare davor gehören dazu)
        assertEinFund("a@erika@example.invalid@", "a@erika@example.invalid");
        assertEinFund("a@b@erika@example.invalid@", "a@b@erika@example.invalid");
        assertEinFund("a@b@c@erika@example.invalid@x", "a@b@c@erika@example.invalid");
    }

    // ---- Grenze der Rückwärtssuche und zweite Adresse direkt hinter einem Fund

    /**
     * Jede Adresse (erstes Vorkommen) ist über alle Funde zusammen abgedeckt. Ein einzelner Punkt zwischen zwei Funden,
     * der beim Aufteilen als Trenner übrig bleibt, gilt als abgedeckt.
     */
    private static void assertAdressenAbgedeckt(String text, String... adressen) {
        werte(text); // Struktur: sortiert, überlappungsfrei, substring == originalValue
        boolean[] gedeckt = new boolean[text.length()];
        for (Finding f : R.detect(text)) {
            for (int i = f.start(); i < f.end(); i++) {
                gedeckt[i] = true;
            }
        }
        for (String a : adressen) {
            int idx = text.indexOf(a);
            assertTrue(idx >= 0, "Adresse nicht im Text: " + a);
            for (int i = idx; i < idx + a.length(); i++) {
                boolean trenner = text.charAt(i) == '.' && i > 0 && i + 1 < gedeckt.length && gedeckt[i - 1]
                        && gedeckt[i + 1];
                assertTrue(gedeckt[i] || trenner,
                        "Adresse nicht vollständig abgedeckt (Index " + (i - idx) + " von " + a.length() + ")");
            }
        }
    }

    @Test
    void rueckwaertssucheGehtNichtVorDasEndeDesLetztenFundes() {
        // Teilfund wegen zu langem Folgelabel, direkt dahinter ein @: der zweite Fund beginnt am Rest des Labels und
        // darf nicht in den ersten hineinreichen (ohne die Grenze "nicht vor from" überlappten die Funde)
        String l63 = "b".repeat(63);
        assertEquals(List.of("a@example." + l63, "b@c.example.invalid"),
                werte("a@example." + "b".repeat(64) + "@c.example.invalid"));
    }

    @Test
    void zweiteAdresseDirektHinterTeilfundBleibtNichtKlartext() {
        String lang = "d".repeat(63) + ".example.invalid";
        String zweite = "max.mustermann@privat-mustermann.example.invalid";
        assertAdressenAbgedeckt("erika.mustermann@" + lang + "." + zweite, zweite);
        assertAdressenAbgedeckt("erika.mustermann@" + lang + "." + zweite, "erika.mustermann@" + lang);
        assertAdressenAbgedeckt("a@b@c@d@example.invalid.erika@mustermann-privat.example.invalid",
                "a@b@c@d@example.invalid", "erika@mustermann-privat.example.invalid");
        assertAdressenAbgedeckt("erika@" + ZWSP + "example.invalid.max@privat-mustermann.example.invalid",
                "erika@" + ZWSP + "example.invalid", "max@privat-mustermann.example.invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {".", "-", "_", "+", "%"})
    void kettenVonFuenfAdressenSindVollstaendigAbgedeckt(String verbinder) {
        String[] a = {"anna@praxis-eins.example.invalid", "bert@praxis-zwei.example.invalid",
                "carla@praxis-drei.example.invalid", "dirk@praxis-vier.example.invalid",
                "emma@praxis-fuenf.example.invalid"};
        assertAdressenAbgedeckt(String.join(verbinder, a), a);
    }

    // ---- Aufteilen am letzten Punkt verliert nichts (Durchlauf 5)

    /** Abdeckung der Funde je Zeichen des Textes. */
    private static boolean[] abdeckung(String text, List<Finding> funde) {
        boolean[] gedeckt = new boolean[text.length()];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                gedeckt[i] = true;
            }
        }
        return gedeckt;
    }

    /** Lesbare Beschreibung eines Textes für Fehlermeldungen (unsichtbare Zeichen und Nicht-BMP-Zeichen ersetzt). */
    private static String beschreibe(String text) {
        return text.codePoints()
                .mapToObj(c -> c == ZWSP.charAt(0) ? "<Z>" : c > 0xFFFF ? "<S>" : Character.toString(c))
                .collect(Collectors.joining());
    }

    /**
     * Obermengen-Eigenschaft von {@code detect}: Jede Position, die {@code detectUnshortened} abdeckt, ist auch in
     * {@code detect} abgedeckt, es sei denn, sie ist ein einzelner Punkt direkt zwischen zwei Funden (Aufteilstelle).
     * Dazu die Invarianten (sortiert, überlappungsfrei, Wert gleich Teilstring, keine Grenze im Surrogatpaar).
     * Liefert die Anzahl der Trenner-Punkte.
     */
    private static int assertKeinVerlust(String text) {
        werte(text);
        List<Finding> kurz = R.detect(text);
        boolean[] lang = abdeckung(text, R.detectUnshortened(text));
        boolean[] gedeckt = abdeckung(text, kurz);
        boolean[] endetHier = new boolean[text.length() + 2];
        boolean[] beginntHier = new boolean[text.length() + 2];
        for (Finding f : kurz) {
            endetHier[f.end()] = true;
            beginntHier[f.start()] = true;
            assertFalse(Character.isLowSurrogate(text.charAt(f.start())), "Anfang im Surrogatpaar");
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end())),
                    "Ende im Surrogatpaar");
        }
        int trenner = 0;
        for (int i = 0; i < text.length(); i++) {
            if (lang[i] && !gedeckt[i]) {
                boolean aufteilstelle = text.charAt(i) == '.' && endetHier[i] && beginntHier[i + 1];
                assertTrue(aufteilstelle, "Klartext an Index " + i + " in: " + beschreibe(text));
                trenner++;
            }
        }
        return trenner;
    }

    private static String mal(String s, int n) {
        return s.repeat(n);
    }

    @Test
    void vierPaareMitZweiLabelDomainWerdenNichtVerkuerztUndVerlierenNichts() {
        String text = "x@y@z@praxis.example@mustermann.invalid@home.example.invalid";
        assertEquals(0, assertKeinVerlust(text));
        assertAdressenAbgedeckt(text, text.substring(0, text.indexOf("@home")));
        assertEquals(List.of("x@y@z@praxis.example@mustermann.invalid", "@home.example.invalid"), werte(text));
    }

    @Test
    void langerLetzterLokalerTeilVerliertNichts() {
        String praxis = "p".repeat(48);
        String text = "erika@praxis.invalid@" + praxis + ".mustermann-privat@home.example.invalid";
        assertEquals(0, assertKeinVerlust(text));
        assertAdressenAbgedeckt(text, "erika@praxis.invalid@" + praxis + ".mustermann");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "_"})
    void kettenVonFuenfAdressenMitZweiLabelDomainsSindAbgedeckt(String verbinder) {
        String[] a = {"anna@praxis-eins.invalid", "bert@praxis-zwei.invalid", "carla@praxis-drei.invalid",
                "dirk@praxis-vier.invalid", "emma@praxis-fuenf.invalid"};
        String text = String.join(verbinder, a);
        assertKeinVerlust(text);
        assertAdressenAbgedeckt(text, a);
    }

    @Test
    void wiederholtesABCAtVerliertNichts() {
        for (int n = 1; n <= 12; n++) {
            String text = mal("a@b.c@", n);
            assertKeinVerlust(text);
            assertKeinVerlust(text + "x");
            assertKeinVerlust(mal("a@b.c@", n) + "a@b.c");
        }
        assertAdressenAbgedeckt("a@b.c@a@b.c@a@b.c@", "@a@b.");
    }

    @Test
    void labelAusserhalbDerBmpVerliertNichts() {
        String lang = "praxis-mustermann-" + "x".repeat(20);
        String b20000 = new String(Character.toChars(0x20000)).repeat(40);
        String b10400 = new String(Character.toChars(0x10400)).repeat(63);
        for (String label : new String[] {b20000, b10400}) {
            String text = "erika@" + lang + ".example." + label + "@home.example.invalid";
            assertKeinVerlust(text);
            assertAdressenAbgedeckt(text, "erika@" + lang + ".example." + label);
        }
    }

    @Test
    void formatzeichenAmTrennerWerdenDenTeilfundenZugeschlagen() {
        String zweite = "erika@mustermann.example.invalid";
        String erste = "a@b@c@d@example.invalid";
        // vor dem Punkt: gehört zum ersten Teilfund
        assertEquals(List.of(erste + ZWSP, zweite), werte(erste + ZWSP + "." + zweite));
        // hinter dem Punkt: gehört zum zweiten Teilfund
        assertEquals(List.of(erste, ZWSP + SOFT_HYPHEN + zweite), werte(erste + "." + ZWSP + SOFT_HYPHEN + zweite));
        // beidseitig: zwischen den Teilfunden bleibt nur der Punkt
        List<Finding> f = R.detect(erste + ZWJ + "." + BOM + zweite);
        assertEquals(2, f.size());
        assertEquals(f.get(0).end() + 1, f.get(1).start());
        assertEquals('.', (erste + ZWJ + "." + BOM + zweite).charAt(f.get(0).end()));
        assertEquals(1, assertKeinVerlust(erste + ZWSP + "." + zweite));
    }

    /** Zufälliger Baustein für den Differenztest (kurze Zeichen, Teiladressen, Labels um die Grenzen). */
    private static String zufallsBaustein(java.util.Random rnd) {
        String ausserhalbBmp = new String(Character.toChars(0x20000));
        String[] fest = {"a", "b", ".", "@", "-", "_", ZWSP, ausserhalbBmp, "c.d", "a@b", "a@b.c", "x.y@"};
        int k = rnd.nextInt(fest.length + 4);
        switch (k - fest.length) {
            case 0: // Label oder lokaler Teil mit 50 bis 74 Zeichen (Grenzen 63 und 64)
                return "a".repeat(50 + rnd.nextInt(25));
            case 1: // Label mit Bindestrichen in der Mitte
                return "b" + "-".repeat(55 + rnd.nextInt(8)) + "b";
            case 2: // kürzeres Label
                return "d".repeat(1 + rnd.nextInt(63));
            case 3: // lokaler Teil, der durch Punkte unterbrochen ist
                return "e".repeat(20 + rnd.nextInt(30)) + "." + "f".repeat(10 + rnd.nextInt(30));
            default:
                return fest[k];
        }
    }

    @Test
    void zufallsDifferenztestDetectGegenDetectUnshortened() {
        java.util.Random rnd = new java.util.Random(20261119L);
        int trenner = 0;
        for (int n = 0; n < 200_000; n++) {
            StringBuilder sb = new StringBuilder();
            int bausteine = 1 + rnd.nextInt(14);
            for (int i = 0; i < bausteine; i++) {
                sb.append(zufallsBaustein(rnd));
            }
            trenner += assertKeinVerlust(sb.toString());
        }
        assertTrue(trenner > 0, "Der Zufallstest erreicht die Aufteilung nie");
    }

    private static final String LANG_LABEL = "gemeinschaftspraxis-dr-mustermann-und-kollegen."
            + "allgemeinmedizin-musterstadt";

    /** Alle Zeichen von {@code text[von, bis)} sind in {@code detect} abgedeckt. */
    private static void assertBereichAbgedeckt(String text, int von, int bis) {
        boolean[] gedeckt = abdeckung(text, R.detect(text));
        for (int i = von; i < bis; i++) {
            assertTrue(gedeckt[i], "Klartext an Index " + i + " in: " + beschreibe(text));
        }
    }

    @Test
    void securityBeleg5aLangeKetteMitVielenAt() {
        String text = "erika@praxis.example.max@a@b@c@praxis.example@" + LANG_LABEL + "@home.example.invalid";
        assertKeinVerlust(text);
        assertBereichAbgedeckt(text, 45, 57);
    }

    @Test
    void securityBeleg5bKetteMitPunktVorLetztemAt() {
        String text = "erika@praxis.example.max@a@b@c@praxis.example@" + LANG_LABEL + "-nord.@home.example.invalid";
        assertKeinVerlust(text);
        assertBereichAbgedeckt(text, 45, 63);
    }

    @Test
    void securityBeleg5cLabelMit63ZeichenUndBindestrichVorAt() {
        String l63 = "x".repeat(63);
        String zweite = "@gemeinschaftspraxis-dr-mustermann.allgemeinmedizin-musterstadt-nord-"
                + "@privat-mustermann.example";
        String text = "erika@praxis.example." + l63 + zweite;
        assertKeinVerlust(text);
        assertBereichAbgedeckt(text, text.indexOf("-@privat"), text.length());
        String text2 = "erika@praxis.example." + l63
                + "@gemeinschaftspraxis-dr-mustermann.allgemeinmedizin-musterstadt-nord.@home.example.invalid";
        assertKeinVerlust(text2);
        assertBereichAbgedeckt(text2, text2.indexOf(".@home"), text2.length());
    }

    @Test
    void rueckwaertssucheLandetImLabelUndNichtImLokalenTeil() {
        // letztes Label (mit Formatzeichen und Bindestrichen) ist länger als 64 Zeichen
        String text = "x@y.example." + ZWSP.repeat(10) + "b" + "-".repeat(60) + "b" + "@mustermann.example.invalid";
        assertKeinVerlust(text);
        assertAdressenAbgedeckt(text, text);
    }

    @Test
    void kuerzenNurWennDieZweiteAdresseErkanntWird() {
        // zweite Adresse nicht vollständig: Fund bleibt ungekürzt
        assertEinFund("erika@praxis.example.invalid@home.", "erika@praxis.example.invalid");
        // nach dem Kürzen bliebe nur ein Label: nicht gekürzt
        assertEinFund("a@b.c@d", "a@b.c");
        // gekürzt: zwei Funde, der zweite beginnt am letzten Label
        assertEquals(List.of("a@b@c@d@example.invalid", "erika@mustermann.example.invalid"),
                werte("a@b@c@d@example.invalid.erika@mustermann.example.invalid"));
    }

    // ---- RFC-zulässige Sonderzeichen im lokalen Teil

    @ParameterizedTest
    @ValueSource(strings = {"o'brien@example.invalid", "d’angelo@example.invalid", "erika&max@example.invalid",
            "a!b@example.invalid", "a#b@example.invalid", "a$b@example.invalid", "a*b@example.invalid",
            "a/b@example.invalid", "a=b@example.invalid", "a?b@example.invalid", "a^b@example.invalid",
            "a`b@example.invalid", "a{b@example.invalid", "a|b@example.invalid", "a}b@example.invalid",
            "a~b@example.invalid"})
    void sonderzeichenImLokalenTeil(String adresse) {
        assertEinFund(adresse, adresse);
        assertEinFund("Mail: " + adresse + ".", adresse);
    }

    @Test
    void sonderzeichenAmAnfangUndKomplettesPraefix() {
        // Sonderzeichen beginnen keinen Fund; das Anführungszeichen davor bleibt draußen (AK-3)
        assertEinFund("'erika@example.invalid'", KURZ);
        assertEinFund("&erika@example.invalid", KURZ);
        // Preis der Regel: ein vorangestelltes Wort mit Sonderzeichen wird mit erfasst (unschädliche Übererfassung)
        assertEinFund("mail=erika@example.invalid", "mail=erika@example.invalid");
        // lange Läufe aus Sonderzeichen vor der Adresse verhindern den Fund nicht
        assertEinFund("x" + "!".repeat(80) + ADRESSE, ADRESSE);
        assertEinFund("=".repeat(80) + ADRESSE, ADRESSE);
    }

    // ---- unsichtbare Formatzeichen

    @ParameterizedTest
    @ValueSource(strings = {"\u00AD", "\u200B", "\u200C", "\u200D", "\u200E", "\u200F", "\u2060", "\uFEFF",
            "\u202A", "\u202B", "\u202C", "\u202D", "\u202E"})
    void unsichtbareFormatzeichenBleibenImFund(String z) {
        String lokal = "eri" + z + "ka@example.invalid";
        assertEinFund(lokal, lokal);
        String domain = "erika@exam" + z + "ple.invalid";
        assertEinFund(domain, domain);
        // direkt vor dem @ (S-5: auch Links-/Rechtsmarke und Bidi-Steuerzeichen)
        String vorAt = "erika" + z + "@example.invalid";
        assertEinFund(vorAt, vorAt);
        // am Rand gehören sie nicht zum Fund
        assertEinFund(KURZ + z, KURZ);
        assertEinFund(z + KURZ, KURZ);
    }

    @Test
    void formatzeichenAmLabelAnfangUndVorPunkt() {
        // S-4: direkt nach dem @ und direkt nach einem Punkt
        assertEinFund("erika@" + ZWSP + "example.invalid", "erika@" + ZWSP + "example.invalid");
        assertEinFund("erika@example." + ZWSP + "invalid", "erika@example." + ZWSP + "invalid");
        assertEinFund("erika@example." + SOFT_HYPHEN + "invalid", "erika@example." + SOFT_HYPHEN + "invalid");
        assertEinFund("erika" + ZWSP + "@" + ZWSP + "example." + ZWSP + "invalid",
                "erika" + ZWSP + "@" + ZWSP + "example." + ZWSP + "invalid");
        // direkt vor einem Punkt: gehört zum Fund (Fund statt kein Fund)
        assertEinFund("erika@example" + ZWSP + ".invalid", "erika@example" + ZWSP + ".invalid");
        assertEinFund("erika@example" + ZWNJ + ZWJ + ".invalid", "erika@example" + ZWNJ + ZWJ + ".invalid");
        // am Ende der Adresse nicht, auch nicht vor einem Schlusspunkt
        assertEinFund("erika@example.invalid" + ZWSP, KURZ);
        assertEinFund("erika@example.invalid" + ZWSP + ".", KURZ);
        assertEinFund("erika@example.invalid" + WORD_JOINER + BOM, KURZ);
    }

    @Test
    void bidiSteuerzeichenSindFormatzeichen() {
        assertEinFund("erika" + LRM + "@example.invalid", "erika" + LRM + "@example.invalid");
        assertEinFund("erika" + RLM + "@example.invalid", "erika" + RLM + "@example.invalid");
        // U+202A bis U+202E sind ebenfalls aufgenommen (sie verbergen sonst eine Adresse im Text)
        assertEinFund("eri\u202Eka@example.invalid", "eri\u202Eka@example.invalid");
    }

    @Test
    void zeilenumbruchBleibtTrenner() {
        assertEinFund("eri\nka@example.invalid", "ka@example.invalid");
        assertKeinFund("erika@exam\nple.invalid");
    }

    // ---- Domain

    @ParameterizedTest
    @ValueSource(strings = {"a@b.example.invalid", "a@example.invalid", "a@mail.example.invalid",
            "a@a-b.example.invalid", "a@xn--bcher-kva.example.invalid", "a@1.example.invalid",
            "a@1.2.example.invalid", "a@müller.example.invalid", "a@example.org", "a@example.net"})
    void erlaubteDomains(String adresse) {
        assertEinFund(adresse, adresse);
    }

    @Test
    void domainOhnePunktIstKeinFund() {
        assertKeinFund("erika@example");
        assertKeinFund("erika@localhost und weiter");
        assertKeinFund("a@");
        assertKeinFund("a@.");
        assertKeinFund("Ort@ 10 Uhr");
    }

    @Test
    void domainMitUngueltigenLabeln() {
        assertKeinFund("erika@.example.invalid");
        assertKeinFund("erika@-example.invalid");
        assertKeinFund("erika@_example.invalid");
        assertKeinFund("erika@example..invalid");
        // Ein Label darf nicht auf Bindestrich enden: das erste Label ("example") ist dann nicht von einem Punkt
        // gefolgt, und der Rest hinter dem Bindestrich hat kein @, daher kein Fund
        assertKeinFund("erika@example-.invalid");
    }

    @Test
    void abschliessenderBindestrichGehoertNichtZurDomain() {
        // Bekannte Grenze: das letzte Label endet nie auf Bindestrich; der Fund endet davor
        assertEinFund("erika@example.invalid-", KURZ);
        assertEinFund("erika@example.invalid--", KURZ);
    }

    @Test
    void unterstrichInDerDomain() {
        // Unterstrich innerhalb eines Labels ist erlaubt, am Labelrand nicht
        assertEinFund("erika@praxis_mueller.example.invalid", "erika@praxis_mueller.example.invalid");
        assertEinFund("erika@mail.praxis_mueller.example.invalid", "erika@mail.praxis_mueller.example.invalid");
        assertEinFund("erika@example.invalid_x", "erika@example.invalid_x");
        assertEinFund("erika@example.invalid_", KURZ);
        assertEinFund("erika@example.invalid_.", KURZ);
    }

    @Test
    void labelLaengeGrenze63() {
        String l63 = "b".repeat(63);
        assertEinFund("a@" + l63 + ".invalid", "a@" + l63 + ".invalid");
        assertEinFund("a@example." + l63, "a@example." + l63);
        // Erstes Label mit 64 Zeichen: nichts Gültiges davor, kein Fund
        assertKeinFund("a@" + "b".repeat(64) + ".invalid");
        // Folgelabel mit 64 Zeichen: Teilfund bis zur Längengrenze, ein Zeichen bleibt stehen. Ein Teilfund ist
        // besser als kein Fund (Plan-Grundsatz, Kap. 2.3).
        assertEinFund("a@example." + "b".repeat(64), "a@example." + l63);
        assertEinFund("a@example.invalid." + "b".repeat(64), "a@example.invalid." + l63);
    }

    @Test
    void adresseMitAnschliessendemLangenToken() {
        // Punkt und ein Token mit mindestens 64 Klassenzeichen direkt hinter der Adresse
        String hex = ZEHN_HEX.repeat(6) + "0123"; // 64 Zeichen
        String text = "erika@example.invalid." + hex;
        List<Finding> r = R.detect(text);
        assertEquals(1, r.size());
        assertEquals(0, r.get(0).start());
        assertEquals(85, r.get(0).end());
        assertEquals(text.substring(0, 85), r.get(0).originalValue());
        assertEquals(text.length() - 1, r.get(0).end());
    }

    // ---- bekannte Grenzen (seltene Formen, heutiges Verhalten festgehalten)

    @Test
    void seltenFormenSindBekannteGrenzen() {
        // in Anführungszeichen gesetzter lokaler Teil mit Leerzeichen
        assertKeinFund("\"erika mustermann\"@example.invalid");
        // Klammern im lokalen Teil: nur der Rest ab '{' beziehungsweise bis '}' ist ein Teilfund
        assertEinFund("{erika}@example.invalid", "erika}@example.invalid");
        // Domain-Literal
        assertKeinFund("erika@[192.0.2.1]");
        // Verschleierte Schreibweisen
        assertKeinFund("erika(at)example.invalid");
        assertKeinFund("erika [at] example.invalid");
        assertKeinFund("erika at example punkt invalid");
    }

    // ---- AK-8 / AK-49 / Robustheit

    @Test
    void ak8TextOhneAdressen() {
        assertKeinFund("Das Wetter ist heute schön.");
        assertKeinFund("");
        assertKeinFund("   \t\r\n");
        assertTrue(TextScanner.standard().scan("Das Wetter ist heute schön.").isEmpty());
    }

    @Test
    void ak49KurzeEingaben() {
        assertKeinFund("a");
        assertKeinFund("a@");
        assertKeinFund("@");
        assertKeinFund("@@");
        assertKeinFund(".");
        assertKeinFund("a.");
        assertKeinFund("a@a");
    }

    /** Eine pathologische Eingabe: Text (wird erst beim Aufruf gebaut), erwartete Fundzahl und deren Begründung. */
    private record Last(Supplier<String> text, int funde, String grund) {}

    private static Last last(Supplier<String> text, int funde, String grund) {
        return new Last(text, funde, grund);
    }

    private static String wiederholt(String einheit, int mindestLaenge) {
        return einheit.repeat((mindestLaenge + einheit.length() - 1) / einheit.length());
    }

    /** Pathologische Eingaben mit je rund 200 000 Zeichen (AK-49: je Eingabe unter 2 s). */
    private static Map<String, Last> pathologisch() {
        int len = 200_000;
        Map<String, Last> m = new LinkedHashMap<>();
        m.put("a. wiederholt, kein @", last(() -> "a.".repeat(len / 2), 0, "ohne @ gibt es keinen Fund"));
        m.put(".a wiederholt, ein @ am Ende",
                last(() -> ".a".repeat(len / 2) + "@", 0, "hinter dem @ steht keine Domain"));
        m.put("nur a", last(() -> "a".repeat(len), 0, "ohne @ gibt es keinen Fund"));
        m.put("nur Bindestriche", last(() -> "-".repeat(len), 0, "ohne @ gibt es keinen Fund"));
        m.put("nur Punkte", last(() -> ".".repeat(len), 0, "ohne @ gibt es keinen Fund"));
        m.put("a@ wiederholt", last(() -> "a@".repeat(len / 2), 0, "nirgends folgt auf ein @ eine Domain mit Punkt"));
        m.put("a@ wiederholt, dann Punkt",
                last(() -> "a@".repeat(len / 4) + ".", 0, "nach jedem @ steht höchstens ein Buchstabe, dann @"));
        m.put("a@ und b. wiederholt",
                last(() -> "a@" + "b.".repeat(len / 2), 1, "eine Adresse mit 100 000 Labels, der Schlusspunkt bleibt draußen"));
        m.put("a@ und ein langes b",
                last(() -> "a@" + "b".repeat(len), 0, "das erste Domain-Label hat mehr als 63 Zeichen"));
        m.put("a@ und b- wiederholt",
                last(() -> "a@" + "b-".repeat(len / 2), 0, "die Domain enthält nie einen Punkt"));
        m.put("63 a und @ wiederholt",
                last(() -> ("a".repeat(63) + "@").repeat(len / 64), 0, "jede Domain wäre 63 Zeichen ohne Punkt"));
        m.put("62 a und @ wiederholt, dann x",
                last(() -> ("a".repeat(62) + "@").repeat(len / 63) + "x", 0, "keine Domain enthält einen Punkt"));
        m.put("a! wiederholt", last(() -> "a!".repeat(len / 2), 0, "ohne @ gibt es keinen Fund"));
        m.put("a und ZWSP wiederholt, dann @",
                last(() -> ("a" + ZWSP).repeat(len / 2) + "@", 0, "hinter dem @ steht keine Domain"));
        m.put("a@b_ wiederholt", last(() -> "a@b_".repeat(len / 4), 0, "keine Domain enthält einen Punkt"));
        m.put("a@b.c ohne Trenner wiederholt",
                last(() -> "a@b.c".repeat(len / 5), 8_001, "8000 Funde aus je fünf Einheiten (vier Paare plus Domain, ohne Kürzen) und ein Rest-Fund am Ende"));
        m.put("63-Zeichen-Labels mit Punkt",
                last(() -> "a@" + ("b".repeat(63) + ".").repeat(len / 64), 1, "eine Adresse mit vielen 63er-Labels"));
        m.put("64-Zeichen-Labels mit Punkt",
                last(() -> "a@" + ("b".repeat(64) + ".").repeat(len / 65), 0, "das erste Label ist zu lang"));
        // S-3: Angriffsformen, die lokale Teile und Labels mit vielen Rückschritten kombinieren; nirgends steht ein
        // Punkt hinter einem @, daher gibt es keinen Fund
        m.put("31 a-ZWSP, a@ wiederholt",
                last(() -> wiederholt(("a" + ZWSP).repeat(31) + "a@", len), 0, "keine Domain mit Punkt"));
        m.put("a-a@ und b_, dann ! wiederholt",
                last(() -> wiederholt("a-a@" + "b_".repeat(30) + "!", len), 0, "keine Domain mit Punkt"));
        m.put("31 a-Bindestrich, a@ wiederholt",
                last(() -> wiederholt("a-".repeat(31) + "a@", len), 0, "keine Domain mit Punkt"));
        m.put("31 a-Ausrufezeichen, a@ wiederholt",
                last(() -> wiederholt("a!".repeat(31) + "a@", len), 0, "keine Domain mit Punkt"));
        m.put("drei Paare, b-Bindestriche, b! wiederholt",
                last(() -> wiederholt(("a-".repeat(31) + "a@").repeat(3) + "b" + "-".repeat(60) + "b!", len), 0,
                        "keine Domain mit Punkt"));
        m.put("drei Paare, 31 b-Bindestrich, b! wiederholt",
                last(() -> wiederholt(("a-".repeat(31) + "a@").repeat(3) + "b-".repeat(31) + "b!", len), 0,
                        "keine Domain mit Punkt"));
        // Formen, die das Kürzen hinter einem Fund (@ direkt dahinter) maximal oft auslösen
        m.put("a@b.c@ wiederholt", last(() -> wiederholt("a@b.c@", len), 16667, "jeder Fund umfasst zwei Einheiten, dazwischen wird gekürzt"));
        m.put("a@b.c.d@e.f.g@ wiederholt", last(() -> wiederholt("a@b.c.d@e.f.g@", len), 14285, "Kürzen hinter jedem Fund"));
        m.put("x@y. wiederholt", last(() -> wiederholt("x@y.", len), 10000, "je vier Paare plus Domain; Aufteilen nur ohne Verlust"));
        m.put("vier Paare, Domain, Label und @ wiederholt",
                last(() -> wiederholt("a@b@c@d@example.invalid.erika@", len), 6667, "Kürzen hinter jedem Fund"));
        m.put("Kette aus Adressen mit Punkt verbunden",
                last(() -> wiederholt("a@" + "b".repeat(30) + ".example.invalid.", len), 1021, "Kürzen hinter vielen Funden"));
        m.put("Teilfunde mit 64er-Label und @ wiederholt",
                last(() -> wiederholt("a@example." + "b".repeat(64) + "@", len), 2667, "Teilfunde wegen zu langem Label"));
        return m;
    }

    @Test
    void pathologischeEingabenInZeitMitErwartetenFunden() {
        for (Map.Entry<String, Last> e : pathologisch().entrySet()) {
            String s = e.getValue().text().get(); // vor der Messung gebaut
            long t0 = System.nanoTime();
            List<Finding> funde = R.detect(s);
            long dauer = System.nanoTime() - t0;
            assertTrue(dauer < LIMIT_NANOS, "Laufzeit zu hoch bei " + e.getKey() + ": " + dauer / 1_000_000 + " ms");
            assertEquals(e.getValue().funde(), funde.size(),
                    "Fundzahl bei " + e.getKey() + " (" + e.getValue().grund() + ")");
        }
    }

    @Test
    void ak48MillionZeichenMitVielenFundenInZehnSekunden() {
        // AK-48: 1 Million Zeichen mit mindestens 10 000 Funden, Grenze 10 s (OF-13); hier 50 000 Funde
        String s = "a@b.example.invalid ".repeat(50_000);
        assertEquals(1_000_000, s.length());
        long t0 = System.nanoTime();
        List<Finding> funde = R.detect(s);
        long dauer = System.nanoTime() - t0;
        assertTrue(dauer < LIMIT_AK48_NANOS, "AK-48: Laufzeit " + dauer / 1_000_000 + " ms");
        assertEquals(50_000, funde.size());
        assertEquals("a@b.example.invalid", funde.get(49_999).originalValue());
        assertEquals(s.length() - 1, funde.get(49_999).end());
    }

    @Test
    void langeKetteVonLabelnIstEinFundKeinAbsturz() {
        String kette = "a@" + "a.".repeat(1_000) + "de";
        assertEinFund(kette, kette);
        // sehr viele Label: kein StackOverflowError (kein gieriges + über eine Gruppe)
        String lang = "a@" + "b.".repeat(500_000) + "de";
        assertEquals(1, R.detect(lang).size());
    }

    @Test
    void nullTextWirdAbgelehnt() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> R.detect(null)).getMessage());
    }

    @Test
    void zustandslosUndDeterministisch() {
        String text = "a@example.invalid und b@example.org.";
        assertEquals(R.detect(text), new EmailRecognizer().detect(text));
        assertEquals(R.detect(text), R.detect(text));
    }

    @Test
    void keinKlartextInToString() {
        Finding f = R.detect("erika.beispiel@example.invalid").get(0);
        assertFalse(f.toString().contains("erika"));
        assertDoesNotThrow(f::toString);
    }

    // ---- Scanner

    @Test
    void standardScannerFindetAdressenUndKeinenSchlusspunkt() {
        String text = "Schreiben Sie an erika.beispiel@example.invalid.";
        List<Finding> r = TextScanner.standard().scan(text);
        assertEquals(1, r.size());
        assertEquals("erika.beispiel@example.invalid", r.get(0).originalValue());
        assertEquals(FindingType.EMAIL, r.get(0).type());
        assertEquals(text.indexOf("erika"), r.get(0).start());
        assertEquals(text.length() - 1, r.get(0).end());
    }

    @Test
    void standardScannerKontaktBeispielAk2() {
        List<Finding> r = TextScanner.standard().scan("Kontakt: erika.beispiel@example.invalid");
        assertEquals(1, r.size());
        assertEquals("erika.beispiel@example.invalid", r.get(0).originalValue());
        assertEquals(FindingType.EMAIL, r.get(0).type());
    }
}
