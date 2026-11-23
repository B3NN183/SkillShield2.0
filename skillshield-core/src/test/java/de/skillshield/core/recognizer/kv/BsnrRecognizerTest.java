package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_ACHT_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_MARKER;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NETZVERBUND;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_35;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_35_SCHLUESSEL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_75;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_75_SCHLUESSEL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_UNBEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_ZEHN_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.ikGueltigReferenz;
import static de.skillshield.core.SynthetischeKvNummern.lanrGueltigReferenz;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.KOMMA;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.LAENGE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE_LAENGE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE_REGIONALCODE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.REGIONALCODE_UNBEKANNT;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.SICHER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.WEITER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.quelleNachNummerBsnr;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.scanBsnr;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_GROSS;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_KLEIN;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.GRUPPEN;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.TRENNER;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.gruppiert;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Tests des {@link BsnrRecognizer} (Feature kvnr-bsnr-erkennung, Task T-13: AK-25 bis AK-29). Alle Nummern stammen aus
 * {@code SynthetischeKvNummern} oder entstehen zur Laufzeit aus Regionalcode, {@code 00000} und zwei freien Ziffern;
 * hier steht keine Ziffernkette ab sechs Stellen und keine Buchstabe-Ziffern-Folge als Literal. Umlaute entstehen zur
 * Laufzeit. Jeder Text geht durch den Waechter {@link BsnrSynthetischWaechter} (ueber
 * {@link BsnrTestHilfen#scanBsnr}), jeder Fund wird gegen Typ, Teilstring-Invariante, Sortierung,
 * Ueberlappungsfreiheit und die aus der Nummer abgeleitete Quelle geprueft. Die Negativgruppen tragen je eine
 * Positiv-Gegenprobe. Die Entscheidung zum nachgestellten Kompositum steht im Javadoc von {@link BsnrRecognizer}.
 */
class BsnrRecognizerTest {

    private static final BsnrRecognizer R = new BsnrRecognizer();
    private static final String B = BSNR_BEKANNT;
    private static final String B2 = BSNR_BEKANNT_ZWEI;
    private static final String U = BSNR_UNBEKANNT;
    private static final String AE = String.valueOf(AE_KLEIN);
    private static final String AE_G = String.valueOf(AE_GROSS);
    private static final String UE = String.valueOf((char) 0xFC);
    private static final String RUECKRUF = "R" + UE + "ckruf";
    private static final String MUSTER = "Muster";

    /** Ersetzt {@code ae} durch den Umlaut: aus {@code Betriebsstaette} wird die Schreibung mit Umlaut. */
    private static String mitUmlaut(String wort) {
        return wort.replace("ae", AE);
    }

    // ------------------------------------------------------------------ Hilfen

    /** Genau ein Fund {@code wert} hinter {@code vor}; Konfidenz, Stufe und Quelle wie angegeben. */
    private static void assertFund(String vor, String wert, String nach, double konfidenz, ConfidenceLevel stufe,
            String quelle) {
        List<Finding> funde = scanBsnr(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertEquals(stufe, f.level(), "Stufe");
        assertTrue(quelle.equals(f.source()), "Quelle");
        assertTrue(quelle.equals(quelleNachNummerBsnr(wert)), "Quelle passt nicht zur Nummer");
    }

    private static void assertSicher(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, SICHER, ConfidenceLevel.SICHER, QUELLE);
    }

    private static void assertWahrscheinlich(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, WEITER, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
    }

    private static void assertFraglichKomma(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, KOMMA, ConfidenceLevel.FRAGLICH, QUELLE);
    }

    private static void assertFraglichRegionalcode(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, REGIONALCODE_UNBEKANNT, ConfidenceLevel.FRAGLICH, QUELLE_REGIONALCODE);
    }

    private static void assertFraglichLaenge(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, LAENGE, ConfidenceLevel.FRAGLICH, QUELLE_LAENGE);
    }

    private static void assertKeinFund(String text) {
        assertTrue(scanBsnr(text).isEmpty(), "Es entstand ein Fund");
    }

    /** Das Kontextwort mit Doppelpunkt und mit Leerzeichen: genau ein Fund, sicher. */
    private static void assertKontextwort(String wort) {
        assertSicher(wort + ": ", B, "");
        assertSicher(wort + " ", B, "");
    }

    // ------------------------------------------------------------------ AK-25: Grundfall

    @Test
    void ak25_grundfall_einFundTypBsnrUeberNeunZiffern_ohneKontextwortUndKomma() {
        assertSicher("Praxis, BSNR ", B, ", " + RUECKRUF);
    }

    @Test
    void ak25_grundfall_bereichIstGenauDieNeunZiffern() {
        String text = "Praxis, BSNR " + B + ", " + RUECKRUF;
        Finding f = scanBsnr(text).get(0);
        assertEquals(9, f.end() - f.start(), "Laenge");
        assertTrue(text.charAt(f.start() - 1) == ' ', "Vor dem Fund steht das Leerzeichen hinter dem Kontextwort");
        assertTrue(text.charAt(f.end()) == ',', "Das Komma steht nicht im Fund");
        assertEquals(FindingType.BSNR, f.type(), "Typ");
    }

    @Test
    void ak25_pseudonymisierung_bsnrPlatzhalterEins_undRoundtripZeichengleich() {
        String text = "BSNR " + B;
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
        List<Finding> funde = new TextScanner(List.of(R)).scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("BSNR [[BSNR_1]]".equals(ergebnis.text()), "Platzhalter im Text");
        ReidentificationResult zurueck = new Reidentifier().restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    @Test
    void ak25_gleicheNummerGleicherPlatzhalter_andereNummerNaechsterPlatzhalter() {
        String text = "BSNR " + B + ", BSNR " + B2 + ", BSNR " + B;
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
        List<Finding> funde = new TextScanner(List.of(R)).scan(text);
        assertEquals(3, funde.size(), "Anzahl Funde");
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("BSNR [[BSNR_1]], BSNR [[BSNR_2]], BSNR [[BSNR_1]]".equals(ergebnis.text()), "Platzhalter");
        ReidentificationResult zurueck = new Reidentifier().restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    // ------------------------------------------------------------------ AK-25: Kontextwoerter

    @Test
    void ak25_kontextwort_bsnrKlein() {
        assertKontextwort("bsnr");
    }

    @Test
    void ak25_kontextwort_bsnrNr() {
        assertKontextwort("BSNR-Nr.");
    }

    @Test
    void ak25_kontextwort_praxisBsnr() {
        assertKontextwort("Praxis-BSNR");
    }

    @Test
    void ak25_kontextwort_nbsnr() {
        assertKontextwort("NBSNR");
    }

    @Test
    void ak25_kontextwort_betriebsstaettennummerMitUmlaut() {
        assertKontextwort(mitUmlaut("Betriebsstaettennummer"));
    }

    @Test
    void ak25_kontextwort_betriebsstaettennummerMitAe() {
        assertKontextwort("Betriebsstaettennummer");
    }

    @Test
    void ak25_kontextwort_betriebsstaettenBindestrichNr() {
        assertKontextwort(mitUmlaut("Betriebsstaetten-Nr."));
        assertKontextwort("Betriebsstaetten-Nr.");
    }

    @Test
    void ak25_kontextwort_betriebsstaetteNr() {
        assertKontextwort(mitUmlaut("Betriebsstaette Nr."));
        assertKontextwort("Betriebsstaette Nr.");
    }

    @Test
    void ak25_kontextwort_nebenbetriebsstaettennummerMitUmlaut() {
        assertKontextwort(mitUmlaut("Nebenbetriebsstaettennummer"));
    }

    @Test
    void ak25_kontextwort_nebenbetriebsstaettennummerMitAe_siebenundzwanzigBuchstaben() {
        String wort = "Nebenbetriebsstaettennummer";
        assertEquals(27, wort.length(), "Das laengste Kontextwort hat 27 Buchstaben");
        assertEquals(KvNumberScanner.MAX_KONTEXTWORT, wort.length(), "Fenster fuer das laengste Kontextwort");
        assertKontextwort(wort);
        assertKontextwort(wort.toUpperCase(Locale.ROOT));
        assertKontextwort(wort.toLowerCase(Locale.ROOT));
    }

    @Test
    void ak25_kontextwort_betriebsstaetteNrGrossgeschriebenMitUmlaut() {
        // BETRIEBSSTAETTE mit dem grossen Umlaut (U+00C4): die Faltung U+00C4 zu U+00E4 muss greifen
        String wort = "BETRIEBSST" + AE_G + "TTE Nr.";
        assertKontextwort(wort);
        assertKontextwort(wort.replace(" Nr.", "-NR."));
    }

    @Test
    void ak25_kontextwort_alleWoerterAusD8_inJederSchreibung() {
        String[] ganz = {"BSNR", "BSNRNr", "NBSNR", "Betriebsstaettennummer", "Betriebsstaettennr",
            "Nebenbetriebsstaettennummer", "Nebenbetriebsstaettennr"};
        String[] vor = {"Betriebsstaette", "Betriebsstaetten", "Nebenbetriebsstaette", "Nebenbetriebsstaetten"};
        for (String wort : ganz) {
            for (String form : new String[] {wort, mitUmlaut(wort)}) {
                assertKontextwort(form);
                assertKontextwort(form.toUpperCase(Locale.ROOT));
                assertKontextwort(form.toLowerCase(Locale.ROOT));
            }
        }
        for (String wort : vor) {
            for (String form : new String[] {wort, mitUmlaut(wort)}) {
                for (String nachsatz : new String[] {" Nr.", "-Nr.", " Nummer", "-Nummer", " NR", "-nummer"}) {
                    assertKontextwort(form + nachsatz);
                    assertKontextwort(form.toUpperCase(Locale.ROOT) + nachsatz);
                }
            }
        }
    }

    @Test
    void ak25_kontextwort_gemischteGrossUndKleinschreibung() {
        for (String wort : List.of("Bsnr", "bSnR", "NbSnr", "BsNr-nr.", "bETRIEBSSTAETTENNUMMER")) {
            assertKontextwort(wort);
        }
    }

    @Test
    void ak25_kontextwortImSatz_bereichBleibtBeiDerNummer() {
        assertSicher("Die Praxis hat die BSNR ", B, " angegeben");
        assertSicher("Daten: Praxis-BSNR ", B2, ".");
        assertSicher("Daten: Betriebsst" + AE + "tte Nr. ", B, "");
    }

    // ------------------------------------------------------------------ AK-26: Abgrenzung der Woerter

    @Test
    void ak26_bsnrx_keinFund_gegenprobeBsnrSicher() {
        assertKeinFund("BSNRX " + B);
        assertKeinFund("bsnrx: " + B);
        assertSicher("BSNR ", B, "");
    }

    @Test
    void ak26_betriebsstaetteOhneNachsatzwort_keinFund_gegenprobeMitNachsatzwort() {
        assertKeinFund("Betriebsst" + AE + "tte " + B);
        assertKeinFund("Betriebsstaette " + B);
        assertKeinFund("Betriebsst" + AE + "tten: " + B);
        assertKeinFund("Nebenbetriebsstaette " + B);
        assertSicher("Betriebsst" + AE + "tte Nr. ", B, "");
        assertSicher("Betriebsstaette Nummer ", B, "");
    }

    @Test
    void ak26_kvNr_keinKontextwortDerBsnr_keinFund_gegenprobeBsnrNr() {
        assertKeinFund("KV-Nr. " + B);
        assertKeinFund("KV-Nummer: " + B);
        assertKeinFund("KV Nr. " + B);
        assertSicher("BSNR-Nr. ", B, "");
    }

    @Test
    void ak26_betrieb_keinFund_gegenprobeBetriebsstaettenNr() {
        assertKeinFund("Betrieb " + B);
        assertKeinFund("Betrieb: " + B);
        assertSicher("Betriebsst" + AE + "tten-Nr. ", B, "");
    }

    @Test
    void ak26_rueckrufUnter_keinFund_gegenprobeMitBsnr() {
        assertKeinFund(RUECKRUF + " unter " + B);
        assertSicher(RUECKRUF + " unter BSNR ", B, "");
    }

    @Test
    void ak26_tel_keinFund_gegenprobeMitBsnr() {
        assertKeinFund("Tel. " + B);
        assertKeinFund("Tel.: " + B);
        assertSicher("Tel. BSNR ", B, "");
    }

    @Test
    void ak26_nummerAllein_keinFund_gegenprobeMitKontextwort() {
        assertKeinFund(B);
        assertKeinFund("Praxis " + B + " Ende");
        assertKeinFund(U);
        assertKeinFund(BSNR_NULL);
        assertSicher("BSNR ", B, "");
    }

    @Test
    void ak26_kontextwortOhneNummerOderAmTextende_keinFundKeineAusnahme() {
        for (String text : List.of("BSNR:", "BSNR", "BSNR-Nr.", "Betriebsst" + AE + "tte Nr.", "NBSNR", "B S N R",
                "Betriebsst" + AE + "tte", "BSNR fehlt")) {
            assertKeinFund(text);
        }
        assertSicher("BSNR: ", B, "");
    }

    @Test
    void ak26_lanrUndIkKontextwoerter_sindFuerDieBsnrKeinKontext_gegenprobeBsnr() {
        assertKeinFund("LANR " + B);
        assertKeinFund("IK " + B);
        assertKeinFund("Arztnummer " + B);
        assertSicher("BSNR ", B, "");
    }

    // ------------------------------------------------------------------ AK-27: Regionalcode

    @Test
    void ak27_unbekannterRegionalcode_einFundFraglich_quelleRegionalcodeUnbekannt() {
        assertFraglichRegionalcode("BSNR ", U, "");
        assertFraglichRegionalcode("BSNR: ", U, ", Rest");
        assertFraglichRegionalcode("Betriebsst" + AE + "ttennummer ", U, ".");
    }

    @Test
    void ak27_bekannteRegionalcodes_sicher_gegenprobeZumUnbekannten() {
        assertSicher("BSNR ", B, "");
        assertSicher("BSNR ", B2, "");
        assertSicher("BSNR ", BSNR_MARKER, "");
        assertFraglichRegionalcode("BSNR ", U, "");
    }

    @Test
    void ak27_sonderkennungen35Und75_mitSchluesselDoppelNull_sindUnbekannt() {
        assertTrue(BSNR_SONDER_35.startsWith("35") && BSNR_SONDER_75.startsWith("75"), "Vorbedingung");
        assertFraglichRegionalcode("BSNR ", BSNR_SONDER_35, "");
        assertFraglichRegionalcode("BSNR ", BSNR_SONDER_75, "");
    }

    @Test
    void ak27_netzverbundnummer99_istUnbekannt_einFundFraglich() {
        assertFraglichRegionalcode("BSNR ", BSNR_NETZVERBUND, "");
        assertFraglichRegionalcode("Betriebsst" + AE + "tte Nr. ", BSNR_NETZVERBUND, "");
    }

    @Test
    void ak27_sonderkennungMitBekanntemSchluesselAnStelle3Und4_istBekannt_sicher() {
        for (String nummer : List.of(BSNR_SONDER_35_SCHLUESSEL, BSNR_SONDER_75_SCHLUESSEL)) {
            // Vorbedingungen: Referenz kennt den Schluessel, die Nummer ist weder LANR noch IK
            assertTrue(BsnrTestHilfen.bekanntReferenz(nummer), "Referenz: Sonderkennung mit Schluessel ist bekannt");
            assertFalse(lanrGueltigReferenz(nummer), "LANR-Referenz: Nummer ist ungueltig");
            assertFalse(ikGueltigReferenz(nummer), "IK-Referenz: Nummer ist ungueltig");
            assertSicher("BSNR ", nummer, "");
            assertSicher("Betriebsst" + AE + "ttennummer: ", nummer, ", " + RUECKRUF);
        }
    }

    @Test
    void ak27_alleHundertRegionalcodes_quelleEntsprichtDerUnabhaengigenReferenz() {
        int bekannt = 0;
        for (int code = 0; code < 100; code++) {
            String nummer = (code < 10 ? "0" : "") + code + "0".repeat(5) + "31";
            boolean erwartet = BsnrTestHilfen.bekanntReferenz(nummer);
            bekannt += erwartet ? 1 : 0;
            if (erwartet) {
                assertSicher("BSNR ", nummer, "");
            } else {
                assertFraglichRegionalcode("BSNR ", nummer, "");
            }
        }
        // Anlage 1 hat 78 Schluessel; 35 und 75 sind mit 00 an Stelle 3 und 4 unbekannt
        assertEquals(78, bekannt, "Zahl der bekannten Regionalcodes");
        assertEquals(78, BsnrTestHilfen.schluesselAnzahl(), "Referenz hat 78 Schluessel");
    }

    @Test
    void ak27_regionalcodeListeIstNieInFundOderQuelle() {
        for (String nummer : List.of(B, U, BSNR_NETZVERBUND, LAENGE_8)) {
            Finding f = scanBsnr("BSNR " + nummer).get(0);
            assertTrue(f.source().chars().noneMatch(Character::isDigit), "Die Quelle enthaelt eine Ziffer");
            assertTrue(!f.toString().contains(nummer), "toString enthaelt die Nummer");
            assertTrue(f.source().length() <= 32, "Quelle zu lang");
        }
        assertTrue(KvArt.BSNR.sourcePruefzifferUngueltig().equals(BsnrRecognizer.SOURCE_REGIONALCODE_UNBEKANNT),
                "Quelle der Art");
        assertTrue(KvArt.BSNR.sourceLaengeAbweichend().equals(BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND), "Quelle");
        assertTrue(KvArt.BSNR.source().equals(BsnrRecognizer.SOURCE), "Quelle der Art");
    }

    // ------------------------------------------------------------------ AK-28: Schreibweisen und Laenge

    @Test
    void ak28_gruppen2plus5plus2_jederTrenner_einFundUeberDieGanzeFolge() {
        int[] gruppen = {2, 5, 2};
        for (String trenner : TRENNER) {
            assertSicher("BSNR ", gruppiert(B, gruppen, trenner), "");
            assertFraglichRegionalcode("BSNR ", gruppiert(U, gruppen, trenner), " Rest");
        }
    }

    @Test
    void ak28_gruppen3plus3plus3_jederTrenner_einFundUeberDieGanzeFolge() {
        int[] gruppen = {3, 3, 3};
        for (String trenner : TRENNER) {
            assertSicher("BSNR ", gruppiert(B, gruppen, trenner), "");
            assertSicher("BSNR: ", gruppiert(B2, gruppen, trenner), ".");
        }
    }

    @Test
    void ak28_weitereGruppen_einFundUeberDieGanzeFolge() {
        for (int[] gruppen : GRUPPEN) {
            for (String trenner : TRENNER) {
                assertSicher("BSNR ", gruppiert(B, gruppen, trenner), "");
            }
        }
        assertSicher("BSNR ", gruppiert(B, new int[] {4, 3, 2}, " "), "");
        assertSicher("BSNR ", gruppiert(B, new int[] {2, 2, 2, 3}, "-"), "");
    }

    @Test
    void ak28_zweiTrennerHintereinander_gehoerenNichtZumFund() {
        for (String trenner : TRENNER) {
            String zwei = trenner + trenner;
            String text = "BSNR " + B.substring(0, 2) + zwei + B.substring(2);
            for (Finding f : scanBsnr(text)) {
                assertFalse(f.originalValue().contains(zwei), "Der Fund enthaelt zwei Trenner hintereinander");
                assertTrue(f.confidence() <= LAENGE, "Eine unterbrochene Nummer ist hoechstens eine Laengenabweichung");
            }
            assertTrue(scanBsnr(text).stream().noneMatch(f -> QUELLE.equals(f.source())),
                    "Aus zwei Trennern darf keine neunstellige Nummer werden");
            assertSicher("BSNR ", B.substring(0, 2) + trenner + B.substring(2), "");
        }
    }

    @Test
    void ak28_trennerAmEnde_gehoertNichtZumFund() {
        for (String trenner : TRENNER) {
            assertSicher("BSNR ", B, trenner);
            assertSicher("BSNR ", B, trenner + "Rest");
            assertSicher("BSNR ", gruppiert(B, new int[] {3, 3, 3}, trenner), trenner);
        }
    }

    @Test
    void ak28_nachNeunZiffernTrennerUndWeitererText_umfasstDerFundNurDieNeunZiffern() {
        assertSicher("BSNR ", B, " und Rest");
        assertSicher("BSNR ", B, "-" + MUSTER);
        assertSicher("BSNR ", B, ". Danach");
        assertSicher("BSNR ", B, " (Praxis)");
        assertSicher("BSNR ", B, "/" + MUSTER);
    }

    @Test
    void ak28_laengeAcht_einFundLaengeAbweichend() {
        assertFraglichLaenge("BSNR ", BSNR_ACHT_ZIFFERN, "");
        assertFraglichLaenge("BSNR: ", BSNR_ACHT_ZIFFERN, ".");
    }

    @Test
    void ak28_laengeZehn_einFundLaengeAbweichend() {
        assertFraglichLaenge("BSNR ", BSNR_ZEHN_ZIFFERN, "");
        assertFraglichLaenge("BSNR: ", BSNR_ZEHN_ZIFFERN, ", Rest");
    }

    @Test
    void ak28_sechsBisZwoelfZiffernAusserNeun_einFundLaengeAbweichend() {
        for (String ziffern : List.of(LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, "0".repeat(11), LAENGE_12)) {
            assertFraglichLaenge("BSNR ", ziffern, "");
            assertFraglichLaenge("Betriebsst" + AE + "ttennummer: ", ziffern, ".");
        }
    }

    @Test
    void ak28_wenigerAlsSechsOderMehrAlsZwoelfZiffern_keinFund_gegenprobeNeunZiffern() {
        assertKeinFund("BSNR " + LAENGE_5);
        assertKeinFund("BSNR " + LAENGE_4);
        assertKeinFund("BSNR " + LAENGE_13);
        assertKeinFund("BSNR " + "0".repeat(15));
        assertSicher("BSNR ", B, "");
    }

    @Test
    void ak28_neunZiffernMitUnbekanntemRegionalcode_istKeineLaengenabweichung() {
        assertFraglichRegionalcode("BSNR ", U, "");
        assertFraglichRegionalcode("BSNR ", BSNR_NULL, "");
    }

    // ------------------------------------------------------------------ AK-29: Kontextregeln der Vorgaenger

    @Test
    void ak29_dreiFuellwoerter_wahrscheinlich_gegenprobeDirekt() {
        assertWahrscheinlich("BSNR der Praxis " + MUSTER + " ", B, "");
        assertWahrscheinlich("BSNR der Praxis ", B, "");
        assertWahrscheinlich("BSNR der ", B, "");
        assertSicher("BSNR ", B, "");
    }

    @Test
    void ak29_vierFuellwoerter_keinFund_gegenprobeDreiFuellwoerter() {
        assertKeinFund("BSNR der Praxis von " + MUSTER + " " + B);
        assertKeinFund("BSNR der Praxis von Frau " + MUSTER + " " + B);
        assertWahrscheinlich("BSNR der Praxis von ", B, "");
    }

    @Test
    void ak29_zeilenumbruchLf_wahrscheinlich() {
        assertWahrscheinlich("BSNR:\n", B, "");
        assertWahrscheinlich("BSNR\n", B, "");
        assertWahrscheinlich("BSNR: \n  ", B, "");
    }

    @Test
    void ak29_zeilenumbruchCrLf_wahrscheinlich() {
        assertWahrscheinlich("BSNR:\r\n", B, "");
        assertWahrscheinlich("BSNR\r\n", B, "");
    }

    @Test
    void ak29_zeilenumbruchCr_wahrscheinlich() {
        assertWahrscheinlich("BSNR:\r", B, "");
    }

    @Test
    void ak29_zeilenumbruchCrUzLf_wahrscheinlich() {
        assertWahrscheinlich("BSNR:\r" + ZWSP + "\n", B, "");
        assertWahrscheinlich("BSNR\r" + ZWSP + ZWSP + "\n", B, "");
    }

    @Test
    void ak29_leerzeile_keinFund_gegenprobeEinZeilenumbruch() {
        assertKeinFund("BSNR:\n\n" + B);
        assertKeinFund("BSNR:\r\n\r\n" + B);
        assertKeinFund("BSNR:\n  \n" + B);
        assertWahrscheinlich("BSNR:\n", B, "");
    }

    @Test
    void ak29_aufzaehlung_ersteSicher_dieUebrigenWahrscheinlich() {
        String text = "BSNR " + B + ", " + B2 + " und " + BSNR_MARKER;
        List<Finding> funde = scanBsnr(text);
        assertEquals(3, funde.size(), "Anzahl Funde");
        assertEquals(SICHER, funde.get(0).confidence(), "erste Nummer");
        assertEquals(WEITER, funde.get(1).confidence(), "zweite Nummer");
        assertEquals(WEITER, funde.get(2).confidence(), "dritte Nummer");
        assertTrue(B.equals(funde.get(0).originalValue()), "Wert der ersten Nummer");
        assertTrue(B2.equals(funde.get(1).originalValue()), "Wert der zweiten Nummer");
        assertTrue(BSNR_MARKER.equals(funde.get(2).originalValue()), "Wert der dritten Nummer");
    }

    @Test
    void ak29_nachgestellt_inKlammer_wahrscheinlich() {
        assertWahrscheinlich("", B, " (BSNR)");
        assertWahrscheinlich("Wert ", B, " (BSNR)");
        assertWahrscheinlich("", B, " (Betriebsst" + AE + "ttennummer)");
        assertWahrscheinlich("", B, " (Betriebsst" + AE + "tte Nr.)");
        assertWahrscheinlich("", B, " BSNR");
    }

    @Test
    void ak29_nachgestellt_amTextanfang_bereichUndStart() {
        List<Finding> funde = scanBsnr(B + " (BSNR)");
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(0, funde.get(0).start(), "Start am Textanfang");
        assertEquals(9, funde.get(0).end(), "Ende");
        assertEquals(WEITER, funde.get(0).confidence(), "Konfidenz");
    }

    @Test
    void ak29_nachgestellt_hinterKomma_fraglich0komma6() {
        assertFraglichKomma("", B, ", BSNR");
        assertFraglichKomma("", B, "; BSNR");
        assertFraglichKomma("Wert ", B, ", Betriebsst" + AE + "ttennummer");
    }

    @Test
    void ak29_nachgestelltKommaRegel_zweiKommataUndWortDazwischen_keinFund_gegenprobeEinKomma() {
        assertKeinFund(B + ",, BSNR");
        assertKeinFund(B + ", und BSNR");
        assertKeinFund(B + ", Praxis BSNR");
        assertKeinFund(B + " Praxis der Frau Muster Dr BSNR");
        assertFraglichKomma("", B, ", BSNR");
    }

    @Test
    void ak29_sperrschrift_bSnR_wahrscheinlich() {
        assertWahrscheinlich("B S N R ", B, "");
        assertWahrscheinlich("B S N R: ", B, "");
        assertWahrscheinlich("b s n r ", B, "");
        assertFraglichRegionalcode("B S N R ", U, "");
    }

    @Test
    void ak29_sperrschrift_bPunktSPunktNPunktRPunkt_wahrscheinlich() {
        assertWahrscheinlich("B. S. N. R. ", B, "");
        assertWahrscheinlich("B.S.N.R. ", B, "");
        assertWahrscheinlich("B-S-N-R ", B, "");
        assertWahrscheinlich("B" + NBSP + "S" + NBSP + "N" + NBSP + "R ", B, "");
    }

    @Test
    void ak29_sperrschrift_nBSNR_wahrscheinlich() {
        assertWahrscheinlich("N B S N R ", B, "");
        assertWahrscheinlich("N. B. S. N. R. ", B, "");
        assertWahrscheinlich("N B S N R: ", B2, ".");
    }

    @Test
    void ak29_sperrschrift_weitForm_wahrscheinlich() {
        assertWahrscheinlich("B  S  N  R ", B, "");
        assertWahrscheinlich("B   S   N   R ", B, "");
        assertWahrscheinlich("B.  S.  N.  R. ", B, "");
        assertWahrscheinlich("B\tS\tN\tR ", B, "");
        assertWahrscheinlich("N  B  S  N  R ", B, "");
    }

    @Test
    void ak29_sperrschrift_dasKontextwortBleibtImText() {
        String text = "B S N R " + B;
        Finding f = scanBsnr(text).get(0);
        assertEquals(8, f.start(), "Der Fund beginnt hinter dem gesperrten Kontextwort");
        assertTrue(text.startsWith("B S N R "), "Kontextwort im Text");
    }

    @Test
    void ak29_sperrschrift_ohneKontextwortDerArt_keinFund() {
        assertKeinFund("B S N " + B);
        assertKeinFund("B S X R " + B);
        assertKeinFund("L A N R " + B);
        assertWahrscheinlich("B S N R ", B, "");
    }

    // ------------------------------------------------------------------ Nachgestelltes Kompositum (Entscheidung T-13)

    @Test
    void kompositum_nachgestellt_hinterKlammerOhneKomma_wahrscheinlich() {
        assertWahrscheinlich("", B, " (Praxis-BSNR)");
        assertWahrscheinlich("", B, " (Pat.-BSNR)");
        assertWahrscheinlich("", B, " (Praxis-Betriebsst" + AE + "ttennummer)");
        assertWahrscheinlich("", B, " (Praxis-Betriebsstaettennummer)");
        assertWahrscheinlich("", B, " (Praxis-Betriebsst" + AE + "tte Nr.)");
        assertWahrscheinlich("", B, " Praxis-BSNR");
    }

    @Test
    void kompositum_nachgestellt_hinterKomma_fraglich() {
        assertFraglichKomma("", B, ", Praxis-BSNR");
        assertFraglichKomma("", B, "; Pat.-BSNR");
        assertFraglichKomma("", B, ", Praxis-Betriebsst" + AE + "ttennummer");
    }

    @Test
    void kompositum_grossgeschriebenMitGrossemUmlaut_wahrscheinlich_gegenprobeFalscheSchreibung() {
        // Grossumlaut (U+00C4) zur Laufzeit; Zweig der Faltung im Kompositum-Vorgriff
        assertWahrscheinlich("", B, " (Praxis-BETRIEBSST" + AE_G + "TTENNUMMER)");
        assertWahrscheinlich("", B, " (Pat.-BETRIEBSST" + AE_G + "TTE NR.)");
        assertFraglichKomma("", B, ", Praxis-NEBENBETRIEBSST" + AE_G + "TTENNR");
        // Gegenprobe: gemischte Schreibung (Grossumlaut und ae) ist kein Kontextwort, auch im Kompositum
        assertKeinFund(B + " (Praxis-BETRIEBSST" + AE_G + "ETTENNUMMER)");
        assertKeinFund(B + " (Praxis-BETRIEBSSTATTENNUMMER)");
    }

    @Test
    void bekannteGrenze_abkuerzungBetriebsst_keinFund_gegenprobeVollesWort() {
        // Bewusst nicht aufgenommen (Nachtrag zu T-13, Punkt 3): "Betriebsst." braucht Scanner-Logik fuer den Punkt
        assertKeinFund("Betriebsst.-Nr. " + B);
        assertKeinFund("Betriebsst. Nr. " + B);
        assertKeinFund("Betriebsst Nr. " + B);
        assertKeinFund("Betriebsst-Nr. " + B);
        assertSicher("Betriebsstaette Nr. ", B, "");
    }

    @Test
    void kompositum_zweiWoerterDavor_keinFund_gegenprobeEinWort() {
        assertKeinFund(B + " (der Praxis-BSNR)");
        assertKeinFund(B + " (Praxis-BSNX)");
        assertKeinFund(B + " (Praxis - BSNR)");
        assertWahrscheinlich("", B, " (Praxis-BSNR)");
    }

    @Test
    void kompositum_betrifftNurKvnrUndBsnr_lanrUndIkBleibenWieDieEingefroreneKopie() {
        for (String text : List.of(LANR_GUELTIG + " (Praxis-LANR)", LANR_GUELTIG + ", Pat.-LANR",
                IK_GUELTIG + " (Praxis-IK)", IK_GUELTIG + ", Pat.-IK", LANR_GUELTIG + " (Praxis-Arztnummer)",
                IK_GUELTIG + " (Praxis-IK-Nr.)")) {
            BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
            assertTrue(new LanrRecognizer().detect(text).equals(KvStandTrenner.lanr().detect(text)), "LANR");
            assertTrue(new IkRecognizer().detect(text).equals(KvStandTrenner.ik().detect(text)), "IK");
            assertTrue(new LanrRecognizer().detect(text).isEmpty(), "LANR: Wort dazwischen, kein Fund");
            assertTrue(new IkRecognizer().detect(text).isEmpty(), "IK: Wort dazwischen, kein Fund");
        }
        assertFalse(KvArt.LANR.nachgestelltesKompositum() || KvArt.IK.nachgestelltesKompositum(), "LANR, IK");
        assertTrue(KvArt.KVNR.nachgestelltesKompositum() && KvArt.BSNR.nachgestelltesKompositum(), "KVNR, BSNR");
    }

    // ------------------------------------------------------------------ Umlaut: Schreibungen, Grenzen

    @Test
    void umlaut_grossUndKleinUndAe_gleichwertig() {
        String ae = "Betriebsst" + AE + "tte Nr. ";
        String grossAe = "BETRIEBSST" + AE_G + "TTE NR. ";
        String ascii = "BETRIEBSSTAETTE NR. ";
        assertSicher(ae, B, "");
        assertSicher(grossAe, B, "");
        assertSicher(ascii, B, "");
        assertSicher("Nebenbetriebsst" + AE_G + "ttennummer ", B, "");
    }

    @Test
    void umlaut_gemischteSchreibungMitAeUndUmlaut_istKeinKontextwort() {
        // nur a, nur e oder beides hintereinander mit Umlaut ist weder ae noch Umlaut: kein Fund (Grenze)
        assertKeinFund("Betriebsstatte Nr. " + B);
        assertKeinFund("Betriebsstette Nr. " + B);
        assertKeinFund("Betriebsst" + AE + "ette Nr. " + B);
        assertSicher("Betriebsst" + AE + "tte Nr. ", B, "");
        assertSicher("Betriebsstaette Nr. ", B, "");
    }

    @Test
    void bekannteGrenze_zerlegterUmlaut_keinFund_gegenprobeMitEinemZeichen() {
        String zerlegt = "Betriebsst" + "a" + (char) 0x308 + "tte Nr. ";
        assertKeinFund(zerlegt + B);
        assertKeinFund("Betriebsst" + "a" + (char) 0x308 + "ttennummer " + B);
        assertSicher("Betriebsst" + AE + "tte Nr. ", B, "");
    }

    @Test
    void unsichtbareZeichenImKontextwort_trennenTeilstuecke_gegenprobe() {
        assertSicher("Praxis" + ZWSP + "BSNR ", B, "");
        assertSicher("BS" + (char) 0xAD + "NR ", B, "");
        assertKeinFund("PraxisBSNR " + B);
        assertSicher("Praxis-BSNR ", B, "");
    }

    // ------------------------------------------------------------------ Rand- und Fehlerfaelle

    @Test
    void satzzeichenDirektHinterDerNummer_gehoertNichtZumFund() {
        for (String zeichen : List.of(".", ",", ";", ")", "!", "?", ":", "\"")) {
            assertSicher("BSNR ", B, zeichen);
            assertSicher("BSNR ", B, zeichen + " Rest");
        }
    }

    @Test
    void fundAmTextende_nachKontextwort() {
        assertSicher("Patient mit BSNR ", B, "");
        assertSicher("BSNR ", B, "");
    }

    @Test
    void leererTextUndReinerLeerraum_leereFundliste() {
        assertTrue(R.detect("").isEmpty(), "leerer Text");
        assertTrue(R.detect(" ").isEmpty(), "ein Leerzeichen");
        assertTrue(R.detect("   \t  ").isEmpty(), "Leerraum");
        assertTrue(R.detect("\n\r\n \n").isEmpty(), "Zeilenumbrueche");
    }

    @Test
    void nullText_nullPointerExceptionMitMeldungText() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertTrue("text".equals(e.getMessage()), "Meldung");
    }

    @Test
    void erkennerIstDeterministischUndLiefertDieselbeListe() {
        String text = "BSNR: " + B + ", BSNR " + U + " und Betriebsst" + AE + "ttennummer " + B2;
        assertTrue(scanBsnr(text).equals(scanBsnr(text)), "Fundliste nicht deterministisch");
    }

    @Test
    void erkennerIstNachrangigMitRestQuelleBsnrRest() {
        assertTrue(R.nachrangig(), "nachrangig");
        assertTrue("bsnr-rest".equals(R.restQuelle()), "restQuelle");
        assertTrue(BsnrRecognizer.SOURCE_REST.equals(R.restQuelle()), "Konstante");
    }

    // ------------------------------------------------------------------ Verhalten wie im Bestand fuer LANR und IK

    @Test
    void lanrUndIkErkenner_bleibenBeiTextenMitBsnrKontextUndUmlautGleichWieDieEingefroreneKopie() {
        List<String> texte = List.of("BSNR " + B, "Betriebsst" + AE + "tte Nr. " + B,
                "BETRIEBSST" + AE_G + "TTE Nr. " + B, "LANR " + LANR_GUELTIG + " BSNR " + B,
                "BSNR " + B + ", LANR " + LANR_GUELTIG, "IK " + IK_GUELTIG + " (BSNR)",
                "Betriebsst" + AE_G + "tte " + LANR_GUELTIG, AE_G + "rztnummer " + LANR_GUELTIG,
                "Arzt" + AE + "nr " + LANR_GUELTIG, "I" + AE_G + " " + IK_GUELTIG);
        for (String text : texte) {
            BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
            assertTrue(new LanrRecognizer().detect(text).equals(KvStandTrenner.lanr().detect(text)), "LANR");
            assertTrue(new IkRecognizer().detect(text).equals(KvStandTrenner.ik().detect(text)), "IK");
        }
    }

    @Test
    void lanrUndIkErkenner_findenNachwievor_dieNummernMitIhremKontextwort() {
        String lanrText = "LANR " + LANR_GUELTIG;
        String ikText = "IK " + IK_GUELTIG;
        BsnrSynthetischWaechter.assertBsnrSynthetisch(lanrText);
        BsnrSynthetischWaechter.assertBsnrSynthetisch(ikText);
        assertEquals(1, new LanrRecognizer().detect(lanrText).size(), "LANR-Funde");
        assertEquals(1, new IkRecognizer().detect(ikText).size(), "IK-Funde");
        assertEquals(0, R.detect(lanrText).size(), "BSNR-Funde hinter LANR");
        assertEquals(0, R.detect(ikText).size(), "BSNR-Funde hinter IK");
    }

    @Test
    void kvArtBsnr_hatDieErwartetenWerte() {
        assertEquals(FindingType.BSNR, KvArt.BSNR.type(), "Typ");
        assertEquals(0.90, KvArt.BSNR.confidenceDirekt(), "Konfidenz direkt");
        assertFalse(KvArt.BSNR.buchstabenform(), "keine Buchstabenform");
        assertTrue(KvArt.BSNR.nurBuchstabenVorwoerter().isEmpty(), "kein nurBuchstabenform");
        // drei Woerter ohne ae (bsnr, bsnrnr, nbsnr) einmal, vier Woerter mit ae in zwei Schreibungen
        assertEquals(11, KvArt.BSNR.kontextwoerter().size(), "Zahl der Kontextwoerter");
        assertEquals(8, KvArt.BSNR.vorwoerter().size(), "vier Vorwoerter in zwei Schreibungen");
        assertTrue(KvArt.BSNR.kontextwoerter().contains("nebenbetriebsstaettennummer"), "ae-Form");
        assertTrue(KvArt.BSNR.kontextwoerter().contains(mitUmlaut("nebenbetriebsstaettennummer")), "Umlautform");
        assertTrue(KvArt.BSNR.kontextwoerter().contains("bsnr") && !KvArt.BSNR.kontextwoerter().contains("bsnrx"),
                "bsnr");
    }
}
