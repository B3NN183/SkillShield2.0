package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_BUCHSTABE_C;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_LEHRBEISPIEL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.GESCH_BINDESTRICH;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.HALBGEVIERT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LRM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SHY;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.VS16;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.GRUPPEN;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.TRENNER;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.gruppiert;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.quelleNachNummerKvnr;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.scanKvnr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Schreibweisen (AK-5, D-1) und Kontextregeln der Vorgaenger (AK-9, D-3) der KVNR (Feature kvnr-bsnr-erkennung, Task
 * T-5). Je Beispiel ein benannter Test mit Gegenprobe. Alle Nummern stammen aus {@code SynthetischeKvNummern}; Texte
 * entstehen per Verkettung, hier steht weder eine Ziffernkette ab neun Stellen noch eine Buchstabe-Ziffern-Folge.
 * Jeder Text laeuft durch {@link KvnrTestHilfen#scanKvnr} (Waechter, Typ, Teilstring-Invariante, Sortierung,
 * Ueberlappungsfreiheit, aus der Nummer abgeleitete Quelle).
 *
 * <p>Regel 12 des Plans: Ein Negativbeispiel (kein Fund erwartet) enthaelt nie die gueltige, geschlossene,
 * grossgeschriebene Nummer, die die Erkennung ohne Kontextwort (Teil 1b) spaeter mit 0,60 finden wuerde. Negativtexte
 * verwenden die Nummer mit falscher Pruefziffer oder eine Form, die 1b nicht trifft. Positivtests duerfen jede Form
 * verwenden.
 *
 * <p>Nachgestelltes Kompositum (Nachtrag 2 zu T-5): {@code N (Patienten-KVNR)}, {@code N, Pat.-KVNR} und
 * {@code N (eGK-Versichertennummer)} gelten als nachgestelltes Kontextwort, nur fuer die KVNR.
 */
class KvnrKontextTest {

    private static final KvnrRecognizer R = new KvnrRecognizer();
    private static final String G = KVNR_GUELTIG;
    private static final String U = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
    private static final String ZIFFERN_G = G.substring(1);
    private static final String BUCHSTABE_G = G.substring(0, 1);
    private static final String ZIFFERN_U = U.substring(1);
    private static final String BUCHSTABE_U = U.substring(0, 1);
    private static final String UZ = String.valueOf(ZWSP) + SHY + LRM;
    private static final double SICHER = 0.95;
    private static final double WEITER = 0.80;
    private static final double KOMMA = 0.60;
    private static final double PZ = 0.50;
    private static final double FORM = 0.40;

    // ------------------------------------------------------------------ Hilfen

    private static ConfidenceLevel stufe(double konfidenz) {
        return ConfidenceLevel.of(konfidenz);
    }

    /** Genau ein Fund {@code wert} hinter {@code vor}, Quelle aus der Nummer, Konfidenz und Stufe wie angegeben. */
    private static void assertFund(String vor, String wert, String nach, double konfidenz) {
        List<Finding> funde = scanKvnr(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertEquals(stufe(konfidenz), f.level(), "Stufe");
        assertTrue(quelleNachNummerKvnr(wert).equals(f.source()), "Quelle");
    }

    private static void assertKeinFund(String text) {
        assertTrue(scanKvnr(text).isEmpty(), "Es entstand ein Fund");
    }

    // ------------------------------------------------------------------ AK-5: Schreibweisen

    @Test
    void ak5_kleinbuchstabe_einFundUeberAlleZehnZeichen_gueltigUndUngueltig() {
        for (String nummer : List.of(KVNR_GUELTIG, KVNR_GUELTIG_ZWEI, KVNR_GUELTIG_BUCHSTABE_C, KVNR_LEHRBEISPIEL)) {
            assertFund("KVNR ", nummer.toLowerCase(), "", SICHER);
            assertFund("Versichertennummer: ", nummer.toLowerCase(), ", Rest", SICHER);
        }
        assertFund("KVNR ", U.toLowerCase(), "", PZ);
        assertFund("KVNR ", KVNR_UNGUELTIG_VERTAUSCHT.toLowerCase(), ".", PZ);
    }

    @Test
    void ak5_buchstabeTrennerZiffern_jederTrenner_einFundUeberAlleElfZeichen() {
        for (String trenner : TRENNER) {
            assertFund("KVNR ", BUCHSTABE_G + trenner + ZIFFERN_G, "", SICHER);
            assertFund("KVNR: ", BUCHSTABE_G.toLowerCase() + trenner + ZIFFERN_G, " Rest", SICHER);
            assertFund("KVNR ", BUCHSTABE_U + trenner + ZIFFERN_U, "", PZ);
        }
    }

    @Test
    void ak5_ziffernInGruppen_jedeGruppierungMitJedemTrenner_einFundUeberDieGanzeFolge() {
        for (int[] gruppen : GRUPPEN) {
            for (String trenner : TRENNER) {
                assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, gruppen, trenner), "", SICHER);
                assertFund("KVNR ", BUCHSTABE_G + trenner + gruppiert(ZIFFERN_G, gruppen, trenner), ", Rest", SICHER);
                assertFund("KVNR ", BUCHSTABE_G.toLowerCase() + gruppiert(ZIFFERN_G, gruppen, trenner), ".", SICHER);
                assertFund("KVNR ", BUCHSTABE_U + trenner + gruppiert(ZIFFERN_U, gruppen, trenner), "", PZ);
            }
        }
    }

    @Test
    void ak5_pruefzifferWirdOhneTrennerGebildet_gruppierteNummerIstGueltig() {
        String nummer = BUCHSTABE_G + " " + gruppiert(ZIFFERN_G, new int[] {4, 5}, "-");
        assertTrue(quelleNachNummerKvnr(nummer).equals("kvnr"), "Referenz sieht eine gueltige Nummer");
        assertFund("KVNR ", nummer, "", SICHER);
    }

    @Test
    void ak5_trennerAmEnde_gehoertNichtZumFund() {
        for (String trenner : TRENNER) {
            assertFund("KVNR ", G, trenner, SICHER);
            assertFund("KVNR ", G, trenner + "Rest", SICHER);
            assertFund("KVNR ", BUCHSTABE_G + " " + ZIFFERN_G, trenner + " Rest", SICHER);
            assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {3, 3, 3}, "-"), trenner + trenner,
                    SICHER);
        }
    }

    @Test
    void ak5_zweiTrennerHintereinanderAmEnde_gehoertenNichtZumFund() {
        for (String trenner : TRENNER) {
            assertFund("KVNR ", G, trenner + trenner + "Rest", SICHER);
            assertFund("KVNR ", G, trenner + " " + "Rest", SICHER);
        }
    }

    @Test
    void ak5_zweiTrennerHintereinanderInDerNummer_beendenDieKette_derFundEndetDavor() {
        for (String trenner : TRENNER) {
            String doppelt = trenner + trenner;
            // Buchstabe und sieben Ziffern, dann zwei Trenner, dann zwei Ziffern: Fund (abweichende Form) bis vor den
            // beiden Trennern; die Ziffern dahinter gehoeren nicht dazu.
            String vorn = BUCHSTABE_G + ZIFFERN_G.substring(0, 7);
            List<Finding> funde = scanKvnr("KVNR " + vorn + doppelt + ZIFFERN_G.substring(7));
            assertEquals(1, funde.size(), "Anzahl Funde");
            assertTrue(vorn.equals(funde.get(0).originalValue()), "Fund endet vor den zwei Trennern");
            assertEquals(FORM, funde.get(0).confidence(), "Konfidenz");
        }
    }

    @Test
    void ak5_zweiBuchstabenVorDenZiffern_keinFund_auchGruppiertUndMitKleinbuchstaben() {
        for (String zwei : List.of("AB", "Ab", "ab", "ZZ", BUCHSTABE_G + BUCHSTABE_G.toLowerCase())) {
            assertKeinFund("KVNR " + zwei + ZIFFERN_G);
            assertKeinFund("Versichertennummer " + zwei + ZIFFERN_G + " Rest");
        }
    }

    /**
     * Bekannte Grenze G-K4 (Plan Kap. 4.2, Nachtrag 2 zu T-5, Punkt 3): Zwei Buchstaben direkt an gruppierten Ziffern
     * liegen ausserhalb von D-1. Der erste Block samt Buchstaben bleibt ohne Fund; die Gruppen danach ergeben einen
     * Teilfund 0,40 (abweichende Form, sechs Ziffern). Der Test haelt das tatsaechliche Verhalten fest und ist
     * ausdruecklich kein Soll fuer kuenftige Erweiterungen: Wird die Grenze geschlossen, ist er anzupassen.
     */
    @Test
    void bekannteGrenzeGK4_zweiBuchstabenVorGruppiertenZiffern_fundNurUeberDieRestlichenBloecke() {
        String vor = "KVNR: ";
        String ersterBlock = ZIFFERN_G.substring(0, 3);
        String rest = gruppiert(ZIFFERN_G.substring(3), new int[] {3, 3}, "-");
        for (String zwei : List.of("AB", "ab", "ZZ")) {
            assertFund(vor + zwei + ersterBlock + "-", rest, ".", FORM);
        }
    }

    @Test
    void ak5_zweiBuchstabenMitTrennerDannZiffern_einFundUeberDieReinenZiffern_hinweisH5() {
        for (String trenner : TRENNER) {
            assertFund("KVNR AB" + trenner, ZIFFERN_G, "", FORM);
        }
    }

    @Test
    void ak5_einBuchstabeVorDenZiffernOhneKontext_keinFund() {
        assertKeinFund(U);
        assertKeinFund("Patient " + U.toLowerCase() + " kam");
        assertKeinFund(BUCHSTABE_U + " " + ZIFFERN_U);
    }

    // ------------------------------------------------------------------ AK-9: Kontextregeln der Vorgaenger

    @Test
    void ak9_dreiFuellwoerter_nullAchtzig() {
        assertFund("KVNR des Patienten Muster ", G, "", WEITER);
        assertFund("KVNR des Patienten ", G, "", WEITER);
        assertFund("KVNR des ", G, ", Rest", WEITER);
        assertFund("Versichertennummer: des Patienten Muster ", G, ".", WEITER);
    }

    @Test
    void ak9_vierFuellwoerter_keinFund_gegenprobeMitUngueltigerNummer() {
        assertKeinFund("KVNR des Patienten Herrn Muster " + U);
        assertKeinFund("KVNR des Patienten Herrn Muster " + KVNR_UNGUELTIG_VERTAUSCHT);
        assertKeinFund("Versichertennummer: eins zwei drei vier " + U);
        assertFund("KVNR eins zwei drei ", U, "", PZ);
    }

    @Test
    void ak9_zeilenumbruch_lfUndCrLfUndCr_nullAchtzig() {
        for (String umbruch : List.of("\n", "\r\n", "\r")) {
            assertFund("KVNR:" + umbruch, G, "", WEITER);
            assertFund("KVNR: " + umbruch, G, "", WEITER);
            assertFund("Versichertennummer" + umbruch, G, ", Rest", WEITER);
        }
    }

    @Test
    void ak9_zeilenumbruch_crUnsichtbarLf_nullAchtzig_wieOhneUnsichtbareZeichen() {
        for (String uz : List.of(String.valueOf(ZWSP), UZ, String.valueOf(SHY))) {
            assertFund("KVNR:\r" + uz + "\n", G, "", WEITER);
            assertFund("KVNR\r" + uz + uz + "\n", G, ".", WEITER);
            assertFund("Versichertennummer: \r" + uz + "\n", G, "", WEITER);
        }
    }

    @Test
    void ak9_leerzeile_keinFund_gegenprobeMitUngueltigerNummer() {
        for (String leer : List.of("\n\n", "\r\n\r\n", "\r\r", "\n \n", "\r\n\t\r\n", "\n\r\n")) {
            assertKeinFund("KVNR:" + leer + U);
            assertKeinFund("KVNR " + leer + KVNR_UNGUELTIG_VERTAUSCHT);
        }
        assertKeinFund("KVNR:\r" + ZWSP + "\n\n" + U);
        assertFund("KVNR:\n", U, "", PZ);
    }

    @Test
    void ak9_aufzaehlung_nullFuenfundneunzigDannNullAchtzigNullAchtzig() {
        String zwei = KVNR_GUELTIG_ZWEI;
        String drei = KVNR_GUELTIG_BUCHSTABE_C;
        String text = "KVNR " + G + ", " + zwei + " und " + drei;
        List<Finding> funde = scanKvnr(text);
        assertEquals(3, funde.size(), "Anzahl Funde");
        assertEquals(SICHER, funde.get(0).confidence(), "Konfidenz 1");
        assertEquals(WEITER, funde.get(1).confidence(), "Konfidenz 2");
        assertEquals(WEITER, funde.get(2).confidence(), "Konfidenz 3");
        assertTrue(G.equals(funde.get(0).originalValue()), "Wert 1");
        assertTrue(zwei.equals(funde.get(1).originalValue()), "Wert 2");
        assertTrue(drei.equals(funde.get(2).originalValue()), "Wert 3");
    }

    @Test
    void ak9_aufzaehlung_andereTrenner_undGegenprobeLuecke() {
        List<Finding> semikolon = scanKvnr("KVNR " + G + "; " + KVNR_GUELTIG_ZWEI);
        assertEquals(2, semikolon.size(), "Semikolon");
        List<Finding> luecke = scanKvnr("KVNR " + G + "\n\n" + U);
        assertEquals(1, luecke.size(), "Leerzeile beendet die Aufzaehlung");
        assertTrue(G.equals(luecke.get(0).originalValue()), "Die erste Nummer bleibt Fund");
        // Zwei Fuellwoerter beenden den Kontext nicht (hoechstens drei): die zweite Nummer bleibt ein Fund.
        List<Finding> wort = scanKvnr("KVNR " + G + " dann Rest " + U);
        assertEquals(2, wort.size(), "Anzahl Funde");
        assertTrue(G.equals(wort.get(0).originalValue()), "Die erste Nummer bleibt Fund");
        assertEquals(PZ, wort.get(1).confidence(), "Konfidenz der zweiten Nummer");
    }

    @Test
    void ak9_nachgestellt_klammerKontextwort_nullAchtzig() {
        assertFund("", G, " (KVNR)", WEITER);
        assertFund("Patient ", G, " (Versichertennummer)", WEITER);
        assertFund("", G, " (eGK-Nr.)", WEITER);
    }

    @Test
    void ak9_nachgestellt_gegenprobeOhneKontextwort_keinFund() {
        assertKeinFund(U + " (Patient)");
        assertKeinFund(U + " (VKNR)");
        assertKeinFund(U + " (KVNRX)");
        assertKeinFund(U + " (Versicherungsnummer)");
    }

    // ------------------------------------------------------------------ Nachtrag 2 zu T-5: nachgestelltes Kompositum

    private static final List<String> KOMPOSITA = List.of("Patienten-KVNR", "Pat.-KVNR", "eGK-Versichertennummer",
            "patienten-kvnr", "PAT.-KVNR", "Patienten-Versicherten-Nr.", "Pat.-KV-Nr.", "Pat.-eGK-Nummer");

    /** Die Schreibweisen der gueltigen Nummer: geschlossen, mit Trenner, gruppiert, klein; je mit der Stufe. */
    private static List<String> gueltigeFormen() {
        return List.of(G, G.toLowerCase(), BUCHSTABE_G + " " + ZIFFERN_G,
                BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {3, 3, 3}, "-"),
                BUCHSTABE_G + " " + gruppiert(ZIFFERN_G, new int[] {4, 5}, " "),
                BUCHSTABE_G.toLowerCase() + gruppiert(ZIFFERN_G, new int[] {2, 2, 5}, "."));
    }

    /** Jede Form der Nummer (gueltig 0,95 bzw. falsche Pruefziffer 0,50) vor {@code nachsatz} mit Deckel. */
    private static void assertKompositum(String vor, String nachsatz, double deckel) {
        for (String komposit : KOMPOSITA) {
            for (String form : gueltigeFormen()) {
                assertFund(vor, form, nachsatz.replace("%", komposit), Math.min(deckel, SICHER));
            }
            assertFund(vor, U, nachsatz.replace("%", komposit), Math.min(deckel, PZ));
            assertFund(vor, U.toLowerCase(), nachsatz.replace("%", komposit), Math.min(deckel, PZ));
        }
    }

    @Test
    void ak9_nachgestellt_kompositum_klammerUndOhneKlammer_nullAchtzig() {
        assertKompositum("", " (%)", WEITER);
        assertKompositum("", " [%]", WEITER);
        assertKompositum("Wert ", " %", WEITER);
        assertKompositum("Wert ", " (%) Rest", WEITER);
    }

    @Test
    void ak9_nachgestellt_kompositum_kommaUndSemikolon_nullSechzig() {
        assertKompositum("", ", %", KOMMA);
        assertKompositum("", "; %", KOMMA);
        assertKompositum("Wert ", ", (%)", KOMMA);
    }

    @Test
    void ak9_nachgestellt_kompositum_aufzaehlungVorDemKompositum_alleNummernNullAchtzig() {
        for (String komposit : KOMPOSITA) {
            for (String trenner : List.of(", ", "; ", " und ", " ")) {
                List<Finding> funde = scanKvnr(G + trenner + KVNR_GUELTIG_ZWEI + " (" + komposit + ")");
                assertEquals(2, funde.size(), "Anzahl Funde");
                assertTrue(G.equals(funde.get(0).originalValue()), "Wert 1");
                assertTrue(KVNR_GUELTIG_ZWEI.equals(funde.get(1).originalValue()), "Wert 2");
                assertEquals(WEITER, funde.get(0).confidence(), "Konfidenz 1");
                assertEquals(WEITER, funde.get(1).confidence(), "Konfidenz 2");
            }
        }
    }

    @Test
    void ak9_nachgestellt_kompositum_hinterKommaNachDerAufzaehlung_nullSechzig() {
        List<Finding> funde = scanKvnr(G + ", " + KVNR_GUELTIG_ZWEI + ", Patienten-KVNR");
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(KOMMA, funde.get(0).confidence(), "Konfidenz 1");
        assertEquals(KOMMA, funde.get(1).confidence(), "Konfidenz 2");
    }

    @Test
    void ak9_nachgestellt_kompositum_kontextwortBeginntKontext_nummerDahinterSicher() {
        // Das Kompositum ist zugleich Kontextwort fuer die folgende Nummer (wie vorangestellt: Pat.-KVNR: N).
        List<Finding> funde = scanKvnr(G + " (Pat.-KVNR): " + KVNR_GUELTIG_ZWEI);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(WEITER, funde.get(0).confidence(), "Konfidenz 1");
        assertEquals(SICHER, funde.get(1).confidence(), "Konfidenz 2");
    }

    /**
     * Gemeinsame Positivprobe fuer die Kein-Fund-Tests unten: Dieselbe ungueltige Nummer mit normalem Kompositum
     * ist ein Fund mit 0,50 (Deckel 0,80 bzw. 0,60 liegt darueber). Damit ist "kein Fund" dort aussagekraeftig.
     */
    @Test
    void ak9_nachgestellt_kompositum_ungueltigeNummer_fundFuenfzig() {
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertFund("", U, " (Pat.-KVNR)", PZ);
        assertFund("", U, " Patienten-KVNR", PZ);
        assertFund("", U, ", Pat.-KVNR", PZ);
        assertFund("", U, "; Pat.-KVNR", PZ);
        assertFund("", U.toLowerCase(), " (Patienten-KVNR)", PZ);
    }

    /** Fund- und Kein-Fund-Zeilen: Positivprobe mit normalem Kompositum, dann Negativfaelle mit zwei Woertern. */
    @Test
    void ak9_nachgestellt_kompositum_gegenprobeZweiWoerter_gegenprobeNormalFund_sonstKeinFund() {
        // Positivprobe, siehe ak9_nachgestellt_kompositum_ungueltigeNummer_fundFuenfzig.
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertKeinFund(U + " (des Patienten-KVNR)");
        assertKeinFund(U + " des Patienten-KVNR");
        assertKeinFund(U + ", des Patienten-KVNR");
        assertKeinFund(U + "; der Herr Pat.-KVNR");
        assertKeinFund(U.toLowerCase() + " (Herrn Pat.-KVNR)");
    }

    /** Fund- und Kein-Fund-Zeilen: Positivprobe mit echtem Kontextwort, dann Woerter ohne Kontextwort. */
    @Test
    void ak9_nachgestellt_kompositum_gegenprobeKeinKontextwortDahinter_gegenprobeNormalFund_sonstKeinFund() {
        // Positivproben, siehe ak9_nachgestellt_kompositum_ungueltigeNummer_fundFuenfzig.
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertFund("", U, ", Patienten-KVNR", PZ);
        assertFund("", U.toLowerCase(), " Patienten-KVNR", PZ);
        for (String wort : List.of("Patienten-VKNR", "Patienten-Versicherungsnummer", "Patienten-KVNRX",
                "Patienten-KV", "Patienten-Nr.", "Pat.-Rentenversicherungsnummer", "Patienten-KVID")) {
            assertKeinFund(U + " (" + wort + ")");
            assertKeinFund(U + ", " + wort);
            assertKeinFund(U.toLowerCase() + " " + wort);
        }
    }

    /** Fund- und Kein-Fund-Zeilen: Positivprobe mit einem Strich und einem Wort, dann die Abweichungen. */
    @Test
    void ak9_nachgestellt_kompositum_gegenprobeMehrAlsEinStrichOderEinWort_gegenprobeNormalFund_sonstKeinFund() {
        // Positivproben, siehe ak9_nachgestellt_kompositum_ungueltigeNummer_fundFuenfzig.
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertFund("", U, " (Pat.-KVNR)", PZ);
        assertKeinFund(U + " (Patienten--KVNR)");
        assertKeinFund(U + " (Patienten-Herr-KVNR)");
        assertKeinFund(U + " (Pat..-KVNR)");
        assertKeinFund(U + " (1-KVNR)");
    }

    /**
     * Ein Strich mit Leerraum ist kein Kompositum (Nachtrag 2 zu T-5: "unmittelbar"): Das Wort davor bleibt ein
     * Fuellwort und leert die offenen Nummern, hinter der Nummer entsteht kein Fund. Das ist plangemaess und wird
     * hier als tatsaechliches Verhalten festgehalten. Gegenprobe: Dieselbe Nummer mit Strich ohne Leerraum ist ein
     * Fund 0,50 (erste Zeilen).
     */
    @Test
    void ak9_nachgestellt_kompositumMitLeerraumAmStrich_gegenprobeNormalFund_sonstKeinFund_plangemaess() {
        // Positivproben, siehe ak9_nachgestellt_kompositum_ungueltigeNummer_fundFuenfzig.
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertFund("", U, ", Pat.-KVNR", PZ);
        assertFund("", U, " Patienten-KVNR", PZ);
        assertKeinFund(U + " (Patienten - KVNR)");
        assertKeinFund(U + " (Patienten- KVNR)");
        assertKeinFund(U + " (Patienten -KVNR)");
        assertKeinFund(U + ", Pat. - KVNR");
        assertKeinFund(U + " (Pat. -KVNR)");
        assertKeinFund(U + "\nPatienten-KVNR");
    }

    /**
     * Das Wort vor dem Strich ist beliebig kurz oder enthaelt Nicht-ASCII-Buchstaben: Ein Einzelbuchstabe und ein
     * Wort mit Umlaut loesen das Kompositum aus (tatsaechliches Verhalten, Fund 0,80).
     */
    @Test
    void ak9_nachgestellt_kompositum_einzelbuchstabeUndUmlautAlsWort_nullAchtzig() {
        String umlautWort = "M" + (char) 0xFC + "ller";
        for (String wort : List.of("a", "A", umlautWort)) {
            assertFund("", G, " " + wort + "-KVNR", WEITER);
            assertFund("", G, " (" + wort + "-KVNR)", WEITER);
            assertFund("", G, ", " + wort + "-KVNR", KOMMA);
        }
    }

    /**
     * Bekannte Grenzen des nachgestellten Kompositums (Nachtrag 2 zu T-5, Hinweise der Sicherheitspruefung): Diese
     * Varianten loesen kein Kompositum aus, es entsteht kein Fund. Der Test haelt das tatsaechliche Verhalten fest und
     * ist ausdruecklich kein Soll fuer kuenftige Erweiterungen. Leerraum am Strich: siehe
     * {@code ak9_nachgestellt_kompositumMitLeerraumAmStrich_gegenprobeNormalFund_sonstKeinFund_plangemaess}. Die
     * Nummern sind ungueltig (Regel 12). Gegenprobe zuerst: dieselbe Zeile mit normalem Bindestrich ist ein Fund
     * 0,50, danach die Varianten ohne Fund.
     */
    @Test
    void bekannteGrenze_kompositum_restgrenzen_keinFund_gegenprobeMitNormalemStrichFund() {
        // Positivproben mit normalem Bindestrich.
        assertFund("", U, " (Pat.-KVNR)", PZ);
        assertFund("", U, ", Pat.-KVNR", PZ);
        assertFund("", U, ", Patienten-KVNR", PZ);
        // Unsichtbares Zeichen hinter dem Strich.
        assertKeinFund(U + " (Pat.-" + ZWSP + "KVNR)");
        assertKeinFund(U + ", Pat.-" + ZWSP + "KVNR");
        // Andere Striche: Bindestrich U+2010, geschuetzter Bindestrich U+2011, Halbgeviertstrich U+2013.
        for (char strich : new char[] {(char) 0x2010, GESCH_BINDESTRICH, HALBGEVIERT}) {
            assertKeinFund(U + " (Pat." + strich + "KVNR)");
            assertKeinFund(U + ", Patienten" + strich + "KVNR");
        }
        // Doppelte Zeichen: zwei Punkte, zwei Striche.
        assertKeinFund(U + " (Pat..-KVNR)");
        assertKeinFund(U + " (Pat.--KVNR)");
        assertKeinFund(U + ", Pat.--KVNR");
        // Variantenwaehler direkt hinter dem Kontextwort des Kompositums.
        assertKeinFund(U + " (Pat.-KVNR" + VS16 + ")");
        assertKeinFund(U + ", Pat.-KVNR" + VS16);
    }

    @Test
    void ak9_vorangestellt_kompositum_bleibtUnveraendert() {
        for (String komposit : KOMPOSITA) {
            assertFund(komposit + ": ", G, "", SICHER);
            assertFund(komposit + " ", G, ", Rest", SICHER);
            assertFund(komposit + ": ", U, "", PZ);
        }
        // Mit Nummer davor: das Kompositum ist nachgestelltes Kontextwort der ersten Nummer und beginnt den Kontext
        // der zweiten; beide sind Funde.
        List<Finding> funde = scanKvnr(G + " (Patienten-KVNR): " + KVNR_GUELTIG_ZWEI);
        assertEquals(2, funde.size(), "Anzahl Funde");
    }

    @Test
    void ak9_nachgestellt_kompositum_lanrUndIk_bleibenGleichDerKopie() {
        for (String text : List.of(LANR_GUELTIG + " (Arzt-LANR)", LANR_GUELTIG + ", Arzt-LANR",
                LANR_GUELTIG + " (Pat.-LANR)", LANR_GUELTIG + " (Praxis-LANR)", IK_GUELTIG + " (Praxis-IK)",
                IK_GUELTIG + ", Pat.-IK", IK_GUELTIG + " (Arzt-Nr.)", LANR_GUELTIG + " (Arzt-Arztnummer)",
                IK_GUELTIG + " (Praxis-Institutionskennzeichen)", LANR_GUELTIG + " (Patienten-KVNR)")) {
            KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
            assertTrue(new LanrRecognizer().detect(text).equals(KvStandTrenner.lanr().detect(text)),
                    "LANR weicht von der Kopie ab");
            assertTrue(new IkRecognizer().detect(text).equals(KvStandTrenner.ik().detect(text)),
                    "IK weicht von der Kopie ab");
        }
    }

    @Test
    void ak9_nachgestellt_kompositum_lanrUndIkBleibenOhneFundBeiWortDazwischen() {
        // Die gemeinsame Regel "Wort dazwischen: kein Fund" gilt fuer LANR und IK unveraendert.
        assertTrue(new LanrRecognizer().detect(LANR_GUELTIG + " (Praxis-LANR)").isEmpty(), "LANR");
        assertTrue(new IkRecognizer().detect(IK_GUELTIG + " (Praxis-IK)").isEmpty(), "IK");
    }

    @Test
    void laufzeit_kompositum_einMillionZeichenWortStrichHinterOffenerNummer_exakteFundzahl() {
        String text = U + " " + "Wort-".repeat(200_000);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, ist, "Fundzahl");
    }

    @Test
    void laufzeit_kompositum_wiederholtNummerMitKompositum_exakteFundzahl() {
        String baustein = G + " (Pat.-KVNR)\n\n";
        int anzahl = 1_000_000 / baustein.length() + 1;
        String text = baustein.repeat(anzahl);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(anzahl, ist, "Fundzahl");
    }

    @Test
    void laufzeit_kompositum_langesWortStrichLangesWort_keinFund() {
        String text = U + " " + "a".repeat(500_000) + "-" + "b".repeat(500_000);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, ist, "Fundzahl");
    }

    @Test
    void ak9_nachgestellt_wortVorDemKontextwort_nurVorangestelltOhneNummerDavor() {
        // Ohne Nummer davor gibt es nichts nachzustellen; vorangestellt gilt AK-2.
        assertFund("Patienten-KVNR ", G, "", SICHER);
        assertKeinFund("Patienten-KVNR");
        assertKeinFund(" (Pat.-KVNR)");
        assertFund("", U, " (KVNR)", PZ);
    }

    @Test
    void ak9_kommaRegel_nullSechzig_fraglich() {
        assertFund("", G, ", KVNR", KOMMA);
        assertFund("", G, "; KVNR", KOMMA);
        assertFund("Patient ", G, ", Versichertennummer", KOMMA);
    }

    @Test
    void ak9_kommaRegel_gegenprobenKeinFund() {
        assertKeinFund(U + ",, KVNR");
        assertKeinFund(U + ", und KVNR");
        assertKeinFund(U + ", Wort KVNR");
        assertKeinFund(U + ", ,KVNR");
        assertFund("", U, ", KVNR", PZ);
    }

    @Test
    void ak9_sperrschrift_gesperrteKontextwoerter_nullAchtzig_kontextwortBleibtImText() {
        for (String wort : List.of("K V N R ", "K. V. N. R. ", "K-V-N-R ", "k v n r ", "K V N R: ")) {
            assertFund(wort, G, "", WEITER);
            assertFund(wort, BUCHSTABE_G + " " + ZIFFERN_G, ", Rest", WEITER);
        }
        String text = "K V N R " + G;
        List<Finding> funde = new TextScanner(List.of(new KvnrRecognizer())).scan(text);
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("K V N R [[KVNR_1]]".equals(ergebnis.text()), "Das gesperrte Kontextwort bleibt im Text");
        assertTrue(text.equals(new Reidentifier().restore(ergebnis.text(), ergebnis.mapping()).text()), "Roundtrip");
    }

    @Test
    void ak9_sperrschriftMitPunkten_kontextwortBleibtImText_roundtrip() {
        String text = "K. V. N. R. " + G;
        List<Finding> funde = new TextScanner(List.of(new KvnrRecognizer())).scan(text);
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("K. V. N. R. [[KVNR_1]]".equals(ergebnis.text()), "Das gesperrte Kontextwort bleibt im Text");
        assertTrue(text.equals(new Reidentifier().restore(ergebnis.text(), ergebnis.mapping()).text()), "Roundtrip");
    }

    @Test
    void ak9_weitForm_punktMitMehrerenLeerzeichen_nullAchtzig() {
        for (String wort : List.of("K.  V.  N.  R. ", "K.  V.  N.  R.  ", "K  V  N  R ", "K   V   N   R ")) {
            assertFund(wort, G, "", WEITER);
        }
    }

    @Test
    void ak9_sperrschrift_gegenproben_keinKontextwort_keinFund() {
        for (String wort : List.of("K V N ", "K V N X ", "K V R ", "V N R ", "K V N R X ", "K. V. N. ")) {
            assertKeinFund(wort + U);
        }
        assertKeinFund("K,V,N,R " + U);
        assertKeinFund("K_V_N_R " + U);
    }

    @Test
    void ak9_sperrschrift_mitUngueltigerNummer_fraglichFuenfzig() {
        assertFund("K V N R ", U, "", PZ);
        assertFund("K. V. N. R. ", U, "", PZ);
    }

    @Test
    void ak9_reineZiffernfolgeVorDerNummer_istSelbstEinFund_dannKommtDieNummerAlsAufzaehlung() {
        for (String ziffern : List.of(IK_GUELTIG, LANR_GUELTIG)) {
            List<Finding> funde = scanKvnr("KVNR " + ziffern + " " + G);
            assertEquals(2, funde.size(), "Anzahl Funde");
            assertEquals(FORM, funde.get(0).confidence(), "Konfidenz der Ziffernfolge");
            assertEquals(WEITER, funde.get(1).confidence(), "Konfidenz der Nummer");
            assertTrue(G.equals(funde.get(1).originalValue()), "Wert der Nummer");
        }
    }

    // ------------------------------------------------------------------ IPv4-Gestalt hinter dem Buchstaben (T-6)

    /** Eine IPv4-Adresse aus dem Dokumentationsbereich, neun Ziffern in vier Punkt-Gruppen. */
    private static final String IP = IK_FORM_DOKU_IP;

    /** Kein Fund beginnt mit einem Buchstaben oder liegt ueber 0,40; Rueckgabe: die Fundliste. */
    private static List<Finding> scanOhneBuchstabenform(String text) {
        List<Finding> funde = scanKvnr(text);
        for (Finding f : funde) {
            assertTrue(Character.isDigit(f.originalValue().charAt(0)), "Fund in Buchstabenform");
            assertTrue(f.confidence() <= FORM, "Konfidenz ueber 0,40");
        }
        return funde;
    }

    @Test
    void ipv4Gestalt_hinterKontextwort_buchstabeIstKeinNummernanfang_fundNurUeberDieZiffern() {
        for (String vor : List.of("KVNR Z", "KVNR z", "KVNR Z ", "KVNR Z-", "KVNR z/", "KVNR: Z.", "KVNR Z" + NBSP,
                "Versichertennummer Z ", "eGK-Nummer: Z", "Patient KVNR Z ")) {
            List<Finding> funde = scanOhneBuchstabenform(vor + IP);
            assertEquals(1, funde.size(), "Anzahl Funde");
            assertTrue(IP.equals(funde.get(0).originalValue()), "Fund genau ueber die Ziffern");
            assertEquals(vor.length(), funde.get(0).start(), "Start");
            assertEquals(FORM, funde.get(0).confidence(), "Konfidenz");
        }
    }

    @Test
    void ipv4Gestalt_hinterKvNr_keinFund_gegenprobeNormaleNummerHatFund() {
        for (String vor : List.of("KV-Nr. Z", "KV-Nr. Z ", "KV-Nummer: z-", "KV Nr. Z")) {
            assertKeinFund(vor + IP);
        }
        for (String vor : List.of("KV-Nr. ", "KV-Nummer: ", "KV Nr. ")) {
            assertFund(vor, G, "", SICHER);
        }
    }

    @Test
    void ipv4Gestalt_unsichtbaresZeichenVorDemBuchstaben_dieselbeRegel() {
        for (String vor : List.of("KVNR " + UZ + "Z", "KVNR " + ZWSP + "Z-", "KVNR" + ZWSP + " Z",
                "KVNR: " + SHY + "z ")) {
            List<Finding> funde = scanOhneBuchstabenform(vor + IP);
            assertEquals(1, funde.size(), "Anzahl Funde");
            assertTrue(IP.equals(funde.get(0).originalValue()), "Fund genau ueber die Ziffern");
        }
        // Gegenprobe: dieselbe Stellung mit einer Nummer ohne IPv4-Gestalt bleibt die Buchstabenform
        assertFund("KVNR " + ZWSP, G, "", SICHER);
        assertFund("KVNR " + UZ, BUCHSTABE_G + " " + ZIFFERN_G, "", SICHER);
    }

    @Test
    void ipv4Gestalt_gesperrtesKontextwortDavor_dieselbeRegel_gegenprobeNormaleNummer() {
        for (String vor : List.of("K V N R Z", "K V N R Z ", "K. V. N. R. Z", "K. V. N. R. z ", "K V N R  Z ")) {
            List<Finding> funde = scanOhneBuchstabenform(vor + IP);
            assertTrue(funde.size() <= 1, "Anzahl Funde");
            assertTrue(funde.isEmpty() || IP.equals(funde.get(0).originalValue()), "Fund nur ueber die Ziffern");
        }
        assertFund("K V N R ", U, "", PZ);
        assertFund("K. V. N. R. ", BUCHSTABE_U + " " + ZIFFERN_U, "", PZ);
    }

    @Test
    void ipv4Gestalt_normaleFormenBleibenUnveraendert_einFundUeberAlleZeichen() {
        assertFund("KVNR ", G, "", SICHER);
        assertFund("KVNR ", BUCHSTABE_G + " " + ZIFFERN_G, "", SICHER);
        for (String trenner : TRENNER) {
            assertFund("KVNR ", BUCHSTABE_G + trenner + ZIFFERN_G, "", SICHER);
        }
        // Punkt-Gruppen in zwei oder drei Gruppen: keine IPv4-Gestalt
        for (int[] gruppen : List.of(new int[] {4, 5}, new int[] {3, 3, 3}, new int[] {2, 7}, new int[] {1, 2, 6})) {
            assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, gruppen, "."), "", SICHER);
            assertFund("KVNR ", BUCHSTABE_G + "." + gruppiert(ZIFFERN_G, gruppen, "."), ", Rest", SICHER);
        }
        // vier Gruppen mit anderen Trennern: nur der Punkt zaehlt als IPv4-Gestalt
        for (String trenner : List.of(" ", String.valueOf(NBSP), "-", "/")) {
            assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {3, 2, 2, 2}, trenner), "", SICHER);
            assertFund("KVNR ", BUCHSTABE_G + " " + gruppiert(ZIFFERN_G, new int[] {2, 2, 2, 3}, trenner), "",
                    SICHER);
        }
    }

    @Test
    void ipv4Gestalt_grenzfaelle_vierZiffernInEinerGruppe_bleibenBuchstabenform() {
        // vier Gruppen, aber eine mit vier Ziffern: keine IPv4-Gestalt
        assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {4, 2, 2, 1}, "."), "", SICHER);
        assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {1, 4, 2, 2}, "."), "", SICHER);
        assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {2, 2, 1, 4}, "."), "", SICHER);
        // vierte Gruppe mit vier Ziffern (zwoelf Ziffern, Buchstabe): abweichende Form mit Buchstaben
        assertFund("KVNR ", "Z" + IP + "234", "", FORM);
    }

    @Test
    void ipv4Gestalt_grenzfaelle_fuenfPunktGruppen_dieErstenVierGruppenBildenDieGestalt() {
        String fuenf = gruppiert(ZIFFERN_G, new int[] {2, 2, 2, 2, 1}, ".");
        List<Finding> funde = scanOhneBuchstabenform("KVNR " + BUCHSTABE_G + fuenf);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertTrue(fuenf.equals(funde.get(0).originalValue()), "Fund genau ueber die Ziffern");
    }

    @Test
    void ipv4Gestalt_weitereZiffernDahinter_dieRegelGreiftTrotzdem() {
        for (String rest : List.of(" " + LAENGE_6, " 12", ".5", ".5.6", "/12")) {
            List<Finding> funde = scanOhneBuchstabenform("KVNR Z" + IP + rest);
            assertTrue(funde.size() <= 2, "Anzahl Funde");
            for (Finding f : funde) {
                assertTrue(f.start() >= "KVNR Z".length(), "Fund beginnt hinter dem Buchstaben");
            }
        }
    }

    /**
     * Bekannte Grenze (Nachtrag zu T-6, Punkt 3): Eine KVNR, deren neun Ziffern mit Punkten in genau vier Gruppen zu je
     * ein bis drei Ziffern stehen, verliert den Buchstaben im Fund; es bleibt ein Fund 0,40 ueber die Ziffern. Die
     * Schreibweise ist fuer eine KVNR nicht ueblich. Der Test haelt das Verhalten fest und ist kein Soll fuer
     * kuenftige Erweiterungen. Gegenprobe: dieselbe Nummer in drei Punkt-Gruppen bleibt ein Fund ueber alle Zeichen.
     */
    @Test
    void bekannteGrenze_kvnrInVierPunktGruppen_fundNurUeberDieZiffern_gegenprobeDreiGruppenVollerFund() {
        for (int[] gruppen : List.of(new int[] {3, 3, 2, 1}, new int[] {2, 2, 2, 3}, new int[] {1, 3, 3, 2})) {
            String vier = gruppiert(ZIFFERN_G, gruppen, ".");
            List<Finding> funde = scanOhneBuchstabenform("KVNR " + BUCHSTABE_G + vier);
            assertEquals(1, funde.size(), "Anzahl Funde");
            assertTrue(vier.equals(funde.get(0).originalValue()), "Fund nur ueber die Ziffern");
            assertEquals(FORM, funde.get(0).confidence(), "Konfidenz");
        }
        assertFund("KVNR ", BUCHSTABE_G + gruppiert(ZIFFERN_G, new int[] {3, 3, 3}, "."), "", SICHER);
    }
}
