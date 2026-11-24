package de.skillshield.core.recognizer;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_UNBEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.ikGueltigReferenz;
import static de.skillshield.core.SynthetischeKvNummern.lanrGueltigReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.BsnrSynthetischWaechter;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Einbindung des BSNR-Erkenners in {@code TextScanner.standard()}: Kollisionen mit LANR, IK und KVNR, Formularfeld
 * {@code BSNR/LANR}, Platzhalter je Typ, Vorrang des Bestands, Mensch entscheidet (Feature kvnr-bsnr-erkennung, T-14;
 * AK-30 bis AK-33, AK-35; Plan Kapitel 2, Tabelle 2.2). Die Klasse prueft jede Zeile der Tabelle mit BSNR und die
 * Kollisionen mit der KVNR. Notation der Kommentare: {@code B} bekannte BSNR (als LANR und IK ungueltig), {@code L}
 * gueltige LANR (Praefix 00), {@code I} gueltige IK (Praefix 00), {@code K} gueltige KVNR.
 *
 * <p>Jeder Text besteht den Waechter {@link BsnrSynthetischWaechter#assertBsnrSynthetisch}; Wertvergleiche laufen ueber
 * {@code assertTrue(equals)}, damit keine Nummer in einer Fehlermeldung landet. Meldungen nennen nur Zahlen und Namen.
 */
class BsnrScannerKollisionenTest {

    private static final String B = BSNR_BEKANNT;
    private static final String L = LANR_GUELTIG;
    private static final String I = IK_GUELTIG;
    private static final String K = KVNR_GUELTIG;
    private static final String RUECK = "\\";
    /** Adresse aus dem Dokumentationsbereich 192.0.2.0/24 (sechs Ziffern, keine Neunerfolge). */
    private static final String IP_DOKU = "192.0.2.1";
    private static final double TOLERANZ = 1e-9;

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** Scannt mit {@code standard()}; prueft Waechter, Sortierung, Ueberlappungsfreiheit, Teilstring, Wiederholung. */
    private static List<Finding> scan(String text, int beabsichtigteKvnrFormen) {
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text, beabsichtigteKvnrFormen);
        List<Finding> funde = TextScanner.standard().scan(text);
        assertTrue(funde.equals(TextScanner.standard().scan(text)), "Fundliste bei Wiederholung nicht gleich");
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static List<Finding> scan(String text) {
        return scan(text, 0);
    }

    private static void assertFund(Finding f, FindingType typ, String wert, double konfidenz, String quelle) {
        assertEquals(typ, f.type(), "Typ");
        assertTrue(wert.equals(f.originalValue()), "Wert des Fundes");
        assertEquals(konfidenz, f.confidence(), TOLERANZ, "Konfidenz");
        assertTrue(quelle.equals(f.source()), "Quelle des Fundes");
    }

    private static void assertTypen(List<Finding> funde, FindingType... typen) {
        List<FindingType> ist = new ArrayList<>();
        for (Finding f : funde) {
            ist.add(f.type());
        }
        assertEquals(List.of(typen), ist, "Typen der Funde in Textreihenfolge");
    }

    private static int anzahl(List<Finding> funde, FindingType typ) {
        int n = 0;
        for (Finding f : funde) {
            n += f.type() == typ ? 1 : 0;
        }
        return n;
    }

    // ------------------------------------------------------------------ Voraussetzung der Testzahlen

    /** Die Testzahlen haben die im Plan vorausgesetzten Eigenschaften (mit Gegenprobe: L ist gueltige LANR). */
    @Test
    void testzahlen_bIstAlsLanrUndIkUngueltig_lUndIAbsichtlichGueltig() {
        assertFalse(lanrGueltigReferenz(B), "B darf keine gueltige LANR sein");
        assertFalse(ikGueltigReferenz(B), "B darf keine gueltige IK sein");
        assertTrue(lanrGueltigReferenz(L), "L ist eine gueltige LANR");
        assertTrue(ikGueltigReferenz(I), "I ist eine gueltige IK");
        assertFalse(new BsnrRecognizer().detect("BSNR " + B).isEmpty(), "B hat einen bekannten Regionalcode");
    }

    // ------------------------------------------------------------------ Tabelle 2.2, Zeilen mit BSNR (AK-30, D-9)

    /** Zeile 1: {@code BSNR B/LANR L}: BSNR 0,90 und LANR 0,95; beide Positionen abgedeckt. */
    @Test
    void ak30_zeile1_bsnrSchraegLanr_zweiFundeBsnrUndLanr() {
        String text = "BSNR " + B + "/LANR " + L;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B, 0.90, "bsnr");
        assertFund(funde.get(1), FindingType.LANR, L, 0.95, "lanr");
        assertEquals(text.indexOf(B), funde.get(0).start(), "Start der BSNR");
        assertEquals(text.indexOf(L), funde.get(1).start(), "Start der LANR");
    }

    /** Zeile 2: {@code LANR L/BSNR B}: LANR 0,95 und BSNR 0,90. */
    @Test
    void ak30_zeile2_lanrSchraegBsnr_zweiFundeLanrUndBsnr() {
        List<Finding> funde = scan("LANR " + L + "/BSNR " + B);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.LANR, L, 0.95, "lanr");
        assertFund(funde.get(1), FindingType.BSNR, B, 0.90, "bsnr");
    }

    /** Zeile 3, Formularfeld (D-9): {@code BSNR/LANR B/L}: beide Nummern geschuetzt, BSNR und LANR je 0,80. */
    @Test
    void ak30_zeile3_formularfeldBsnrLanr_zweiFundeBeidePositionenAbgedeckt() {
        String text = "BSNR/LANR " + B + "/" + L;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B, 0.80, "bsnr");
        assertFund(funde.get(1), FindingType.LANR, L, 0.80, "lanr");
        assertEquals(text.indexOf(B), funde.get(0).start(), "Start der ersten Nummer");
        assertEquals(text.indexOf(L), funde.get(1).start(), "Start der zweiten Nummer");
    }

    /** Zeile 4, Formularfeld umgekehrt: {@code LANR/BSNR L/B}: LANR und BSNR je 0,80. */
    @Test
    void ak30_zeile4_formularfeldLanrBsnr_zweiFundeBeidePositionenAbgedeckt() {
        String text = "LANR/BSNR " + L + "/" + B;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.LANR, L, 0.80, "lanr");
        assertFund(funde.get(1), FindingType.BSNR, B, 0.80, "bsnr");
    }

    /**
     * Zeile 5: {@code LANR L, BSNR L}: die zweite Zahl hat unter BSNR nur 0,50 (Regionalcode unbekannt), unter LANR
     * 0,80; beide Funde sind LANR (der Typ folgt der hoeheren Konfidenz, die Position ist geschuetzt).
     */
    @Test
    void ak30_zeile5_dieselbeGueltigeLanrUnterLanrUndBsnr_beideFundeLanr() {
        List<Finding> funde = scan("LANR " + L + ", BSNR " + L);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.LANR, L, 0.95, "lanr");
        assertFund(funde.get(1), FindingType.LANR, L, 0.80, "lanr");
        assertEquals(0, anzahl(funde, FindingType.BSNR), "kein BSNR-Fund");
    }

    /** Zeile 6: {@code LANR B, BSNR B}: die erste Zahl hat unter BSNR 0,60 (Komma), unter LANR 0,50: beide BSNR. */
    @Test
    void ak30_zeile6_dieselbeBsnrUnterLanrUndBsnr_beideFundeBsnr() {
        List<Finding> funde = scan("LANR " + B + ", BSNR " + B);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B, 0.60, "bsnr");
        assertFund(funde.get(1), FindingType.BSNR, B, 0.90, "bsnr");
        assertEquals(0, anzahl(funde, FindingType.LANR), "kein LANR-Fund");
    }

    /** Zeile 7: {@code IK I (BSNR)}: bleibt ein IK-Fund 0,92; das Kontextwort ohne Nummer erzeugt nichts. */
    @Test
    void ak30_zeile7_ikMitKlammerBsnr_bleibtEinIkFund() {
        List<Finding> funde = scan("IK " + I + " (BSNR)");
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.IK, I, 0.92, "ik");
    }

    /** Gegenprobe zu Zeile 7: dieselbe IK mit BSNR-Kontextwort danach und Nummer: IK und BSNR. */
    @Test
    void ak30_gegenprobeZeile7_ikUndBsnrMitNummer_zweiFunde() {
        List<Finding> funde = scan("IK " + I + ", BSNR " + B);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.IK, I, 0.92, "ik");
        assertFund(funde.get(1), FindingType.BSNR, B, 0.90, "bsnr");
    }

    /** Zeile mit BSNR-Kontextwort davor und einer gueltigen LANR dahinter: die LANR setzt sich mit 0,60 durch. */
    @Test
    void ak30_bsnrKontextwortZaehltNichtGegenEineGueltigeLanr_lanrFundMitKomma() {
        List<Finding> funde = scan("BSNR " + L + ", LANR");
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.LANR, L, 0.60, "lanr");
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(0).level(), "Stufe");
    }

    // ------------------------------------------------------------------ Kollisionen mit der KVNR (D-6)

    /** BSNR und KVNR nebeneinander: jede Art behaelt ihre Nummer (0,90 und 0,95), in beiden Reihenfolgen. */
    @Test
    void d6_bsnrUndKvnrNebeneinander_jedeArtBehaeltIhreNummer() {
        List<Finding> bsnrErst = scan("BSNR " + B + ", KVNR " + K, 1);
        assertEquals(2, bsnrErst.size(), "Zahl der Funde");
        assertFund(bsnrErst.get(0), FindingType.BSNR, B, 0.90, "bsnr");
        assertFund(bsnrErst.get(1), FindingType.KVNR, K, 0.95, "kvnr");
        List<Finding> kvnrErst = scan("KVNR " + K + ", BSNR " + B, 1);
        assertEquals(2, kvnrErst.size(), "Zahl der Funde");
        assertFund(kvnrErst.get(0), FindingType.KVNR, K, 0.95, "kvnr");
        assertFund(kvnrErst.get(1), FindingType.BSNR, B, 0.90, "bsnr");
    }

    /**
     * KVNR-Kontextwort, dahinter eine BSNR mit Komma und BSNR-Kontextwort: die BSNR (0,60) schlaegt den KVNR-Kandidaten
     * ohne Buchstaben (0,40); es bleibt ein Fund ueber der Position.
     */
    @Test
    void d6_kvnrKontextwortVorBsnrNummer_bsnrSetztSichGegenDenSchwaechenKvnrKandidatenDurch() {
        List<Finding> funde = scan("KVNR " + B + ", BSNR");
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B, 0.60, "bsnr");
    }

    /** Gegenprobe: ohne BSNR-Kontextwort bleibt es der KVNR-Kandidat mit 0,40 (Form abweichend). */
    @Test
    void d6_gegenprobeKvnrKontextwortOhneBsnrKontextwort_kvnrFundNullVierzig() {
        List<Finding> funde = scan("KVNR " + B);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.KVNR, B, 0.40, "kvnr-form-abweichend");
    }

    /**
     * Eine KVNR in Buchstabenform hinter einem BSNR-Kontextwort: die KVNR (mit Buchstabe, Regel 1b, 0,60) umschliesst
     * den BSNR-Kandidaten auf den Ziffern (0,50) und gewinnt (Kollisionsfall K-1 nach Kap. 5.2); es entsteht kein
     * BSNR-Fund, die Position ist als KVNR geschuetzt.
     */
    @Test
    void d6_kvnrInBuchstabenformHinterBsnrKontextwort_kvnrUmschliesstDenBsnrKandidaten() {
        List<Finding> funde = scan("BSNR " + K, 1);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.KVNR, K, 0.60, "kvnr-ohne-kontext");
        assertEquals(0, anzahl(funde, FindingType.BSNR), "kein BSNR-Fund");
    }

    /** Alle vier KV-Arten in einem Text: je ein Fund mit eigener Konfidenz und Quelle, in Textreihenfolge. */
    @Test
    void d6_allerVierKvArtenInEinemText_jeEinFundMitEigenerKonfidenzUndQuelle() {
        List<Finding> funde = scan("BSNR " + B + " KVNR " + K + " LANR " + L + " IK " + I, 1);
        assertTypen(funde, FindingType.BSNR, FindingType.KVNR, FindingType.LANR, FindingType.IK);
        assertFund(funde.get(0), FindingType.BSNR, B, 0.90, "bsnr");
        assertFund(funde.get(1), FindingType.KVNR, K, 0.95, "kvnr");
        assertFund(funde.get(2), FindingType.LANR, L, 0.95, "lanr");
        assertFund(funde.get(3), FindingType.IK, I, 0.92, "ik");
    }

    // ------------------------------------------------------------------ AK-30: Platzhalter je Typ, Rueckuebersetzung

    private static void assertRoundtrip(String text, PseudonymizationResult ergebnis) {
        ReidentificationResult zurueck = REIDENTIFIER.restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    /** Platzhalter {@code [[BSNR_n]]}, {@code [[LANR_n]]}, {@code [[IK_n]]}, global in Textreihenfolge nummeriert. */
    @Test
    void ak30_platzhalterTragenDenTypIhresFundes_nummerierungUeberAlleTypen() {
        String text = "BSNR " + B + ", LANR " + L + ", IK " + I;
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, scan(text));
        assertTrue("BSNR [[BSNR_1]], LANR [[LANR_2]], IK [[IK_3]]".equals(r.text()), "Text mit Platzhaltern");
        assertFalse(r.text().contains(B) || r.text().contains(L) || r.text().contains(I), "Nummer im Ergebnis");
        assertRoundtrip(text, r);
    }

    /** Dieselbe BSNR unter LANR- und BSNR-Kontextwort: beide Funde BSNR, ein gemeinsamer Platzhalter, Roundtrip. */
    @Test
    void ak30_dieselbeBsnrUnterLanrUndBsnr_einGemeinsamerPlatzhalterFuehrtZumOriginalwert() {
        String text = "LANR " + B + ", BSNR " + B;
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, scan(text));
        assertTrue("LANR [[BSNR_1]], BSNR [[BSNR_1]]".equals(r.text()), "Text mit Platzhaltern");
        assertRoundtrip(text, r);
    }

    /** Dieselbe gueltige LANR unter LANR- und BSNR-Kontextwort: beide Funde LANR, ein gemeinsamer Platzhalter. */
    @Test
    void ak30_dieselbeLanrUnterLanrUndBsnr_einGemeinsamerPlatzhalterFuehrtZumOriginalwert() {
        String text = "LANR " + L + ", BSNR " + L;
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, scan(text));
        assertTrue("LANR [[LANR_1]], BSNR [[LANR_1]]".equals(r.text()), "Text mit Platzhaltern");
        assertRoundtrip(text, r);
    }

    /**
     * Dieselbe Zahl als Fund vom Typ LANR und vom Typ BSNR (von Hand uebergeben; eine Zahl, die zugleich gueltige LANR
     * und BSNR mit bekanntem Regionalcode ist, ist mit synthetischen Daten nicht bildbar): jeder Platzhalter traegt den
     * Typ seines Fundes, beide fuehren zum Originalwert zurueck.
     */
    @Test
    void ak30_dieselbeZahlAlsLanrUndAlsBsnrFund_platzhalterTragenJeEigenenTypUndFuehrenZurueck() {
        String text = "LANR " + B + ", BSNR " + B;
        int zweiter = text.lastIndexOf(B);
        List<Finding> funde = List.of(
                new Finding(FindingType.LANR, B, 5, 5 + B.length(), 0.95, "lanr"),
                new Finding(FindingType.BSNR, B, zweiter, zweiter + B.length(), 0.90, "bsnr"));
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue("LANR [[LANR_1]], BSNR [[BSNR_2]]".equals(r.text()), "Text mit Platzhaltern");
        assertFalse(r.text().contains(B), "Nummer im Ergebnis");
        assertRoundtrip(text, r);
    }

    // ------------------------------------------------------------------ AK-31: Bestand hat Vorrang

    /** URL: {@code ...?bsnr=B} ergibt genau einen URL-Fund, keinen BSNR-Fund. */
    @Test
    void ak31_bsnrInEinerUrl_genauEinUrlFund() {
        String text = "https://example.invalid/praxis?bsnr=" + B;
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.URL, funde.get(0).type(), "Typ");
        assertEquals(0, funde.get(0).start(), "Start");
        assertEquals(text.length(), funde.get(0).end(), "Ende");
        assertEquals(0, anzahl(funde, FindingType.BSNR), "kein BSNR-Fund");
    }

    /** Pfad: {@code C:\Praxis\BSNR-B\x.txt} ergibt genau einen PFAD-Fund. */
    @Test
    void ak31_bsnrInEinemPfad_genauEinPfadFund() {
        String text = "C:" + RUECK + "Praxis" + RUECK + "BSNR-" + B + RUECK + "x.txt";
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.PFAD, funde.get(0).type(), "Typ");
        assertEquals(text.length(), funde.get(0).end() - funde.get(0).start(), "Fund umfasst den ganzen Pfad");
    }

    /** E-Mail: {@code BSNR B@example.invalid} ergibt genau einen EMAIL-Fund; das Kontextwort bleibt Klartext. */
    @Test
    void ak31_bsnrAlsLokalerTeilEinerEmail_genauEinEmailFundKontextwortBleibtKlartext() {
        String text = "BSNR " + B + "@example.invalid";
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.EMAIL, funde.get(0).type(), "Typ");
        assertEquals("BSNR ".length(), funde.get(0).start(), "Start: das Kontextwort liegt ausserhalb des Fundes");
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue("BSNR [[EMAIL_1]]".equals(r.text()), "Text mit Platzhalter");
        assertRoundtrip(text, r);
    }

    /** Gegenprobe zu AK-31: dieselbe Nummer frei hinter dem Kontextwort (ohne URL, Pfad, E-Mail) ist ein BSNR-Fund. */
    @Test
    void ak31_gegenprobeDieselbeNummerFreiHinterDemKontextwort_bsnrFund() {
        List<Finding> funde = scan("Siehe BSNR " + B + " im Formular");
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B, 0.90, "bsnr");
    }

    /**
     * Ragt der BSNR-Kandidat ueber eine E-Mail hinaus, bleibt der EMAIL-Fund unveraendert und nur der Teil
     * ausserhalb mit mindestens drei Ziffern wird als BSNR-Restfund (Quelle {@code bsnr-rest}) geschuetzt (G-K14).
     */
    @Test
    void ak31_bsnrKandidatRagtInEineEmail_emailFundUnveraendertRestfundMitBsnrRest() {
        String text = "BSNR " + B.substring(0, 3) + " " + B.substring(3, 6) + "." + B.substring(6) + "@example.invalid";
        List<Finding> alt = new TextScanner(List.of(new EmailRecognizer())).scan(text);
        List<Finding> funde = scan(text);
        assertEquals(1, alt.size(), "Zahl der E-Mail-Funde ohne BSNR");
        assertTrue(funde.contains(alt.get(0)), "E-Mail-Fund fehlt oder ist veraendert");
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, B.substring(0, 3), 0.80, "bsnr-rest");
        assertEquals(FindingType.EMAIL, funde.get(1).type(), "Typ des zweiten Fundes");
    }

    /** Die Standard-Liste enthaelt den BSNR-Erkenner am Ende, nachrangig, mit der Restquelle {@code bsnr-rest}. */
    @Test
    void ak33_standardEnthaeltDenBsnrErkennerAmEnde_nachrangigMitRestQuelle() {
        List<Recognizer> liste = TextScanner.standard().recognizers();
        Recognizer letzter = liste.get(liste.size() - 1);
        assertTrue(letzter instanceof BsnrRecognizer, "letzter Erkenner ist der BSNR-Erkenner");
        assertTrue(letzter.nachrangig(), "nachrangig");
        assertTrue("bsnr-rest".equals(letzter.restQuelle()), "Restquelle");
        assertEquals(1, liste.stream().filter(r -> r instanceof BsnrRecognizer).count(), "genau ein BSNR-Erkenner");
    }

    // ------------------------------------------------------------------ AK-32: Mensch entscheidet

    /**
     * Ein fraglicher BSNR-Fund (Regionalcode unbekannt, 0,50) wird ohne Uebergabe nicht ersetzt; bei Uebergabe
     * ersetzt, der Roundtrip ist zeichengleich. Der sichere Fund daneben wird unabhaengig davon ersetzt, wenn er
     * uebergeben wird.
     */
    @Test
    void ak32_fraglicherBsnrFund_wirdNurBeiUebergabeErsetzt() {
        String text = "BSNR " + B + " und BSNR " + I + " und BSNR " + BSNR_UNBEKANNT;
        List<Finding> funde = scan(text);
        assertEquals(3, funde.size(), "Zahl der Funde");
        assertEquals(ConfidenceLevel.SICHER, funde.get(0).level(), "Stufe des ersten Fundes");
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(1).level(), "Stufe des zweiten Fundes");
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(2).level(), "Stufe des dritten Fundes");
        PseudonymizationResult ohne = PSEUDONYMIZER.pseudonymize(text, List.of());
        assertTrue(text.equals(ohne.text()), "Text ohne Uebergabe veraendert");
        PseudonymizationResult nurSicher = PSEUDONYMIZER.pseudonymize(text, List.of(funde.get(0)));
        assertTrue(nurSicher.text().contains(I) && nurSicher.text().contains(BSNR_UNBEKANNT),
                "Fraglicher Fund ohne Uebergabe ersetzt");
        assertFalse(nurSicher.text().contains(B), "Sicherer Fund nicht ersetzt");
        assertRoundtrip(text, nurSicher);
        PseudonymizationResult alle = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue("BSNR [[BSNR_1]] und BSNR [[BSNR_2]] und BSNR [[BSNR_3]]".equals(alle.text()),
                "Text mit allen Platzhaltern");
        assertRoundtrip(text, alle);
    }

    /** Gegenprobe: die Nullnummer mit BSNR-Kontextwort ist ebenfalls fraglich (0,50), aber ein Fund. */
    @Test
    void ak32_gegenprobeNullnummerMitKontextwort_fraglicherFundEntstehtTrotzdem() {
        List<Finding> funde = scan("BSNR " + BSNR_NULL);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertFund(funde.get(0), FindingType.BSNR, BSNR_NULL, 0.50, "bsnr-regionalcode-unbekannt");
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(0).level(), "Stufe");
    }

    /** Ohne BSNR-Kontextwort (Gegenprobe zu AK-32): dieselbe Nummer in einem Satz ohne Kontext erzeugt keinen Fund. */
    @Test
    void ak32_nummerOhneKontextwort_keinBsnrFund() {
        List<Finding> funde = scan("Die Nummer " + B + " steht im Text");
        assertEquals(0, funde.size(), "Zahl der Funde");
        assertEquals(1, scan("Die BSNR " + B + " steht im Text").size(), "Gegenprobe mit Kontextwort");
    }

    // ------------------------------------------------------------------ Text mit allen Funden der Standard-Erkenner

    /**
     * Ein Text mit je einem Fund jedes Standard-Erkenners (E-Mail, IP, URL, Pfad, LANR, IK, KVNR, BSNR): acht Funde in
     * Textreihenfolge mit dem eigenen Typ, Pseudonymisierung aller Funde und zeichengleiche Rueckuebersetzung.
     */
    @Test
    void ak33_textMitAllenFundenDerStandardErkenner_achtFundeAchtTypenRoundtrip() {
        String pfad = "C:" + RUECK + "Praxis" + RUECK + "Muster" + RUECK + "x.txt";
        String text = String.join("\n", "Mail kontakt@example.invalid", "IP " + IP_DOKU,
                "URL https://example.invalid/praxis", "Pfad " + pfad, "LANR " + L, "IK " + I, "KVNR " + K,
                "BSNR " + B);
        List<Finding> funde = scan(text, 1);
        assertTypen(funde, FindingType.EMAIL, FindingType.IP, FindingType.URL, FindingType.PFAD, FindingType.LANR,
                FindingType.IK, FindingType.KVNR, FindingType.BSNR);
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, funde);
        for (String nummer : List.of(B, L, I, K)) {
            assertFalse(r.text().contains(nummer), "Nummer im pseudonymisierten Text");
        }
        assertRoundtrip(text, r);
    }

    /** Die Fundliste ist deterministisch, sortiert und von der Reihenfolge der Erkenner unabhaengig. */
    @Test
    void ak35_fundlisteIstUnabhaengigVonDerReihenfolgeDerErkenner() {
        String text = "BSNR " + B + "/LANR " + L + ", IK " + I + ", KVNR " + K;
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text, 1);
        List<Recognizer> umgekehrt = new ArrayList<>(TextScanner.standard().recognizers());
        Collections.reverse(umgekehrt);
        assertTrue(scan(text, 1).equals(new TextScanner(umgekehrt).scan(text)),
                "Fundliste haengt von der Reihenfolge der Erkenner ab");
    }
}
