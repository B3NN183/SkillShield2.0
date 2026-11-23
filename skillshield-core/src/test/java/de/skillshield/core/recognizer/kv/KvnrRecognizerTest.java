package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_ACHT_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_BUCHSTABE_C;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_LEHRBEISPIEL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_ZIFFER_GEAENDERT;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_ZEHN_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.BOM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LRM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SHY;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.WORD_JOINER;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.FORM;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.PZ_UNGUELTIG;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.QUELLE;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.QUELLE_FORM;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.QUELLE_PZ;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.SICHER;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.quelleNachNummerKvnr;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.scanKvnr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests des {@link KvnrRecognizer} mit Kontextwort (Feature kvnr-bsnr-erkennung, Task T-4: AK-1 bis AK-4, AK-7, AK-8,
 * AK-10). Alle Nummern stammen aus {@code SynthetischeKvNummern}; hier steht keine Ziffernkette ab neun Stellen und
 * keine Buchstabe-Ziffern-Folge als Literal, Texte entstehen per Verkettung. Jeder Text laeuft durch den Waechter
 * {@link KvnrSynthetischWaechter}; jeder Fund wird gegen Typ, Teilstring-Invariante, Sortierung,
 * Ueberlappungsfreiheit und die aus der Nummer abgeleitete Quelle geprueft ({@link KvnrTestHilfen#scanKvnr}).
 * Schreibweisen mit Trennern, Kleinbuchstaben und die Kontextregeln der Vorgaenger gehoeren zu T-5.
 *
 * <p>Negativbeispiele der Kontextwoerter verwenden eine ungueltige Nummer (Regel 12 des Plans), damit sie nicht von
 * der Erkennung ohne Kontextwort abhaengen.
 *
 * <p><b>Warnhinweis zum Laufzeit-Waechter:</b> Er wertet jeden einzelnen Buchstaben, einen optionalen Trenner und
 * neun Ziffern als KVNR-Form, also auch den Endbuchstaben eines Kontextworts vor einer reinen LANR-, IK- oder
 * Laengen-Ziffernfolge ({@code K V N R} und Ziffern). Ergibt das zufaellig eine gueltige Pruefziffer ohne
 * {@code 0000} an Stelle 2 bis 5, schlaegt der Waechter an (rund 10 Prozent der Konstanten). Das ist kein
 * Produktfehler; dann ein anderes Kontextwort oder eine andere Konstante waehlen.
 */
class KvnrRecognizerTest {

    private static final KvnrRecognizer R = new KvnrRecognizer();
    private static final String G = KVNR_GUELTIG;
    private static final String U = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
    /** Die neun Ziffern ohne den Buchstaben, zur Laufzeit gebildet (reine Ziffernfolge mit gueltiger KVNR-Ziffer). */
    private static final String ZIFFERN_G = G.substring(1);

    /** Kontextwoerter aus D-2 in den Schreibweisen von AK-2 (je mit {@code : } und mit Leerzeichen geprueft). */
    private static final List<String> KONTEXTWOERTER = List.of("kvnr", "Versichertennummer", "Versichertennr.",
            "Krankenversichertennummer", "Krankenversicherungsnummer", "eGK-Nummer", "eGK-Nr.", "Versicherten-Nr.",
            "Versicherten-Nummer", "Krankenversicherten-Nr.", "KV-Nr.", "KV-Nummer", "KV Nr.", "Patienten-KVNR");

    /** Weitere Woerter aus D-2: Schreibung, Kurzformen und das Vorwort mit Nachsatzwort in Grossschreibung. */
    private static final List<String> WEITERE_KONTEXTWOERTER = List.of("KVNR", "Kvnr", "KvNr", "Versichertennr",
            "Krankenversichertennr", "Krankenversicherungsnr", "eGKNr", "eGKNummer", "EGK-NR", "KV-NR", "KV-NUMMER",
            "kv nummer", "Krankenversicherten-Nummer", "KRANKENVERSICHERTENNUMMER");

    // ------------------------------------------------------------------ Hilfen

    /** Genau ein Fund {@code wert} hinter {@code vor}; Konfidenz, Stufe und Quelle wie angegeben. */
    private static void assertFund(String vor, String wert, String nach, double konfidenz, ConfidenceLevel stufe,
            String quelle) {
        List<Finding> funde = scanKvnr(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertEquals(stufe, f.level(), "Stufe");
        assertTrue(quelle.equals(f.source()), "Quelle");
        assertTrue(quelle.equals(quelleNachNummerKvnr(wert)), "Quelle passt nicht zur Nummer");
    }

    private static void assertSicher(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, SICHER, ConfidenceLevel.SICHER, QUELLE);
    }

    private static void assertFraglichPruefziffer(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, PZ_UNGUELTIG, ConfidenceLevel.FRAGLICH, QUELLE_PZ);
    }

    private static void assertFraglichForm(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, FORM, ConfidenceLevel.FRAGLICH, QUELLE_FORM);
    }

    private static void assertKeinFund(String text) {
        assertTrue(scanKvnr(text).isEmpty(), "Es entstand ein Fund");
    }

    // ------------------------------------------------------------------ AK-1

    @Test
    void ak1_grundfall_einFundTypKvnrUeberBuchstabeUndZiffern_ohneKontextwortDoppelpunktKomma() {
        assertSicher("Patient, KVNR: ", G, ", geb.");
    }

    @Test
    void ak1_grundfall_bereichIstGenauDieZehnZeichen() {
        String text = "Patient, KVNR: " + G + ", geb.";
        Finding f = scanKvnr(text).get(0);
        assertEquals(10, f.end() - f.start(), "Laenge");
        assertTrue(text.charAt(f.start() - 1) == ' ', "Vor dem Fund steht das Leerzeichen hinter dem Doppelpunkt");
        assertTrue(text.charAt(f.end()) == ',', "Das Komma steht nicht im Fund");
    }

    @Test
    void ak1_pseudonymisierung_kvnrPlatzhalterEins_undRoundtripZeichengleich() {
        String text = "KVNR: " + G;
        List<Finding> funde = new TextScanner(List.of(R)).scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("KVNR: [[KVNR_1]]".equals(ergebnis.text()), "Platzhalter im Text");
        ReidentificationResult zurueck = new Reidentifier().restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    @Test
    void ak1_weitereGueltigeVektoren_sindSicher() {
        assertSicher("KVNR ", KVNR_GUELTIG_ZWEI, "");
        assertSicher("KVNR ", KVNR_GUELTIG_BUCHSTABE_C, "");
        assertSicher("KVNR ", KVNR_LEHRBEISPIEL, "");
        assertSicher("KVNR ", KVNR_NULL, "");
    }

    // ------------------------------------------------------------------ AK-2

    @Test
    void ak2_jedesKontextwort_mitDoppelpunktUndLeerzeichen_genauEinFundSicher() {
        for (String wort : KONTEXTWOERTER) {
            assertSicher(wort + ": ", G, "");
            assertSicher(wort + " ", G, "");
        }
    }

    @Test
    void ak2_weitereSchreibungenUndKurzformen_genauEinFundSicher() {
        for (String wort : WEITERE_KONTEXTWOERTER) {
            assertSicher(wort + ": ", G, "");
            assertSicher(wort + " ", G, "");
        }
    }

    @Test
    void ak2_kontextwortImSatz_bereichBleibtBeiDerNummer() {
        assertSicher("Der Patient hat die Versichertennummer ", G, " angegeben");
        assertSicher("Daten: Patienten-KVNR ", KVNR_GUELTIG_ZWEI, ".");
    }

    // ------------------------------------------------------------------ AK-3

    @Test
    void ak3_kontextwortAbgrenzung_keinFund() {
        List<String> vorlaeufe = List.of("VKNR ", "KVNRX ", "Versicherungsnummer ", "Rentenversicherungsnummer ",
                "KV ", "Nr. ", "Die KV zahlt ", "vknr: ", "kvnrx: ", "Versicherungsnr. ", "KVID ");
        for (String vor : vorlaeufe) {
            assertKeinFund(vor + U);
        }
    }

    @Test
    void ak3_kontextwortOhneNummer_keinFundKeineAusnahme() {
        for (String text : List.of("KVNR:", "KVNR", "KV-Nr. fehlt", "KV-Nr.", "Versichertennummer", "KV Nr.",
                "Die KVNR: fehlt noch", "eGK-Nummer")) {
            assertKeinFund(text);
        }
    }

    @Test
    void ak3_nummerOhneKontextwort_keinFund() {
        assertKeinFund(U);
        assertKeinFund("Patient " + U + " kam");
    }

    // ------------------------------------------------------------------ AK-4

    @Test
    void ak4_kvNrVorKvnr_genauEinFundKvnr() {
        assertSicher("KV-Nr. ", G, "");
        assertSicher("KV-Nummer: ", G, "");
    }

    @Test
    void ak4_kvNrVorReinerZiffernfolge_keinKvnrFund_auchNichtVorLanrUndIk() {
        for (String ziffern : List.of(LAENGE_6, LAENGE_7, LAENGE_8, LANR_GUELTIG, IK_GUELTIG, LAENGE_10, LAENGE_12,
                ZIFFERN_G)) {
            assertKeinFund("KV-Nr. " + ziffern);
            assertKeinFund("KV-Nr.: " + ziffern + " Ende");
            assertKeinFund("KV Nr. " + ziffern);
        }
    }

    @Test
    void ak4_kvNrIstFuerLanrUndIkKeinKontextwort() {
        LanrRecognizer lanr = new LanrRecognizer();
        IkRecognizer ik = new IkRecognizer();
        for (String ziffern : List.of(LANR_GUELTIG, IK_GUELTIG, LAENGE_6, LAENGE_12)) {
            String text = "KV-Nr. " + ziffern;
            assertTrue(lanr.detect(text).isEmpty(), "KV-Nr. ist ein LANR-Kontext");
            assertTrue(ik.detect(text).isEmpty(), "KV-Nr. ist ein IK-Kontext");
        }
    }

    @Test
    void ak4_kvNrNachgestelltHinterZiffernfolge_keinFund() {
        assertKeinFund(LANR_GUELTIG + " (KV-Nr.)");
        assertKeinFund(IK_GUELTIG + ", KV-Nr.");
        assertKeinFund(LAENGE_6 + " KV-Nr.");
    }

    @Test
    void ak4_kvNrNachgestelltHinterKvnr_einFundMitKontextAbschlag() {
        assertFund("", G, " (KV-Nr.)", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
    }

    @Test
    void ak4_kvNrVorLanrUndZiffernfolgeDannKvnr_ziffernSindFuellwort_kvnrBleibtFund() {
        String text = "KV-Nr. " + LANR_GUELTIG + " " + G;
        List<Finding> funde = scanKvnr(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertTrue(G.equals(funde.get(0).originalValue()), "Originalwert");
        assertEquals(0.80, funde.get(0).confidence(), "Konfidenz (die Ziffernfolge war ein Fuellwort)");
    }

    @Test
    void ak4_lanrUndIkKontextVorKvnr_keinKvnrFundDesKvnrErkenners() {
        assertKeinFund("LANR " + U);
        assertKeinFund("IK " + U);
        assertKeinFund("LANR: " + U + ", IK " + U);
    }

    @Test
    void ak4_lanrKontextVorKvnr_lanrErkennerBleibtUnveraendertGegenEingefroreneKopie() {
        for (String kontext : List.of("LANR ", "IK ")) {
            String text = kontext + G;
            LanrRecognizer lanr = new LanrRecognizer();
            IkRecognizer ik = new IkRecognizer();
            assertTrue(lanr.detect(text).equals(KvStandTrenner.lanr().detect(text)), "LANR weicht von der Kopie ab");
            assertTrue(ik.detect(text).equals(KvStandTrenner.ik().detect(text)), "IK weicht von der Kopie ab");
        }
        List<Finding> lanr = new LanrRecognizer().detect("LANR " + G);
        assertEquals(1, lanr.size(), "Die Ziffern der KVNR bleiben fuer den LANR-Erkenner ein Fund");
        assertTrue(ZIFFERN_G.equals(lanr.get(0).originalValue()), "Fundbereich sind nur die Ziffern");
        assertEquals(FindingType.LANR, lanr.get(0).type(), "Typ");
    }

    @Test
    void ak4_versichertennummerVorReinerZiffernfolge_einFundFormAbweichend() {
        assertFraglichForm("Versichertennummer ", LANR_GUELTIG, "");
        assertFraglichForm("Versichertennummer: ", IK_GUELTIG, " bitte");
        assertFraglichForm("KVNR ", ZIFFERN_G, "");
    }

    // ------------------------------------------------------------------ AK-7

    @Test
    void ak7_falschePruefziffer_bleibtFund_fraglich_quellePruefzifferUngueltig() {
        assertFraglichPruefziffer("KVNR ", KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "");
        assertFraglichPruefziffer("KVNR ", KVNR_UNGUELTIG_VERTAUSCHT, ", Rest");
        assertFraglichPruefziffer("Versichertennummer: ", KVNR_UNGUELTIG_ZIFFER_GEAENDERT, ".");
    }

    @Test
    void ak7_fundUndHinweis_enthaltenKeinePruefziffer() {
        for (String ungueltig : List.of(KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, KVNR_UNGUELTIG_VERTAUSCHT,
                KVNR_UNGUELTIG_ZIFFER_GEAENDERT)) {
            Finding f = scanKvnr("KVNR " + ungueltig).get(0);
            assertTrue(f.source().chars().noneMatch(Character::isDigit), "Die Quelle enthaelt eine Ziffer");
            assertTrue(!f.toString().contains(ungueltig) && !f.toString().contains(ungueltig.substring(1)),
                    "toString enthaelt die Nummer");
        }
    }

    // ------------------------------------------------------------------ AK-8

    @Test
    void ak8_buchstabeMitAchtUndZehnZiffern_einFundFormAbweichend() {
        assertFraglichForm("KVNR ", KVNR_ACHT_ZIFFERN, "");
        assertFraglichForm("KVNR ", KVNR_ZEHN_ZIFFERN, "");
    }

    @Test
    void ak8_buchstabeMitSechsBisZwoelfZiffernAusserNeun_einFundFormAbweichend() {
        for (String ziffern : List.of(LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, "0".repeat(11), LAENGE_12)) {
            assertFraglichForm("KVNR ", "Z" + ziffern, "");
            assertFraglichForm("KVNR: ", "Z" + ziffern, ".");
        }
    }

    @Test
    void ak8_reineZiffernfolgeSechsBisZwoelf_einFundFormAbweichend_auchNeun() {
        for (String ziffern : List.of(LAENGE_6, LAENGE_7, LAENGE_8, LANR_GUELTIG, LAENGE_10, "0".repeat(11),
                LAENGE_12)) {
            assertFraglichForm("KVNR ", ziffern, "");
            assertFraglichForm("Versichertennummer: ", ziffern, ", Rest");
        }
    }

    @Test
    void ak8_wenigerAlsSechsOderMehrAlsZwoelfZiffern_keinFund() {
        assertKeinFund("KVNR " + LAENGE_5);
        assertKeinFund("KVNR " + LAENGE_4);
        assertKeinFund("KVNR " + LAENGE_13);
        assertKeinFund("KVNR Z" + LAENGE_5);
        assertKeinFund("KVNR Z" + LAENGE_4);
        assertKeinFund("KVNR Z" + LAENGE_13);
    }

    @Test
    void ak8_buchstabeUndNeunZiffernMitUngueltigerPruefziffer_istKeineFormAbweichung() {
        assertFraglichPruefziffer("KVNR ", U, "");
    }

    // ------------------------------------------------------------------ AK-10

    @Test
    void ak10_satzzeichenDirektHinterDerNummer_gehoertNichtZumFund() {
        for (String zeichen : List.of(".", ",", ";", ")", "!", "?", ":", "\"")) {
            assertSicher("KVNR ", G, zeichen);
            assertSicher("KVNR ", G, zeichen + " Rest");
        }
    }

    @Test
    void ak10_fundAmTextende_nachKontextwort() {
        assertSicher("Patient mit KVNR ", G, "");
        assertSicher("KVNR ", G, "");
    }

    @Test
    void ak10_fundAmTextanfang_beiNachgestelltemKontext() {
        List<Finding> funde = scanKvnr(G + " (KVNR)");
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(0, funde.get(0).start(), "Start am Textanfang");
        assertEquals(10, funde.get(0).end(), "Ende");
        assertEquals(0.80, funde.get(0).confidence(), "Konfidenz");
    }

    @Test
    void ak10_leererTextUndReinerLeerraum_leereFundliste() {
        assertTrue(R.detect("").isEmpty(), "leerer Text");
        assertTrue(R.detect(" ").isEmpty(), "ein Leerzeichen");
        assertTrue(R.detect("   \t  ").isEmpty(), "Leerraum");
        assertTrue(R.detect("\n\r\n \n").isEmpty(), "Zeilenumbrueche");
    }

    @Test
    void ak10_nullText_nullPointerExceptionMitMeldungText() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertTrue("text".equals(e.getMessage()), "Meldung");
    }

    @Test
    void ak10_nurEinBuchstabeOderNurKontextwortAmRand_keineAusnahmeKeinFund() {
        for (String text : List.of("Z", "KVNR Z", "KVNR Z ", "Z0", "KVNR Z0", "KVNR Z 0", "KVNR Z-", "KVNR Z-0",
                "KVNR Z.", "Z 1", "A")) {
            assertKeinFund(text);
        }
    }

    // ------------------------------------------------------------------ Buchstabenform und angeklebte Ziffern

    @Test
    void zweiBuchstabenVorDenZiffern_keinFund() {
        assertKeinFund("KVNR AB" + ZIFFERN_G);
        assertKeinFund("KVNR AB" + ZIFFERN_G + " Rest");
        assertKeinFund("KVNR Ab" + ZIFFERN_G);
    }

    @Test
    void zweiBuchstabenDannTrennerUndZiffern_einFundFormAbweichendUeberDieReinenZiffern_hinweisH5() {
        assertFraglichForm("KVNR AB ", ZIFFERN_G, "");
    }

    @Test
    void ziffernDirektAnDemKontextwort_keinFund() {
        assertKeinFund("KVNR" + ZIFFERN_G);
        assertKeinFund("Versichertennummer" + ZIFFERN_G);
        assertKeinFund("KV-Nr." + G.substring(1));
    }

    @Test
    void buchstabeMitTrennerUndZiffern_einFundUeberAlleZehnZeichen() {
        assertSicher("KVNR ", G.charAt(0) + " " + ZIFFERN_G, "");
    }

    // ------------------------------------------------------------------ Verhalten wie im Bestand fuer LANR und IK

    @Test
    void lanrUndIkErkenner_findenNachwievor_dieNummernMitIhremKontextwort() {
        String lanrText = "LANR " + LANR_GUELTIG;
        String ikText = "IK " + IK_GUELTIG;
        assertTrue(new LanrRecognizer().detect(lanrText).equals(KvStandTrenner.lanr().detect(lanrText)),
                "LANR weicht von der Kopie ab");
        assertTrue(new IkRecognizer().detect(ikText).equals(KvStandTrenner.ik().detect(ikText)),
                "IK weicht von der Kopie ab");
        assertEquals(1, new LanrRecognizer().detect(lanrText).size(), "LANR-Funde");
        assertEquals(1, new IkRecognizer().detect(ikText).size(), "IK-Funde");
    }

    @Test
    void erkennerIstDeterministischUndLiefertDieselbeListe() {
        String text = "KVNR: " + G + ", KVNR " + U + " und Versichertennummer " + LANR_GUELTIG;
        assertTrue(scanKvnr(text).equals(scanKvnr(text)), "Fundliste nicht deterministisch");
    }

    // ------------------------------------------------------------------ Unsichtbare Zeichen vor dem Buchstaben

    /** Die unsichtbaren Zeichen aus dem Nachtrag zu T-4: U+200B, U+00AD, U+FEFF, U+2060, U+200E. */
    private static final char[] UNSICHTBAR = {ZWSP, SHY, BOM, WORD_JOINER, LRM};

    /** Mehrere unsichtbare Zeichen hintereinander, auch gemischt. */
    private static final List<String> UNSICHTBAR_FOLGEN = List.of("" + ZWSP + ZWSP + ZWSP, "" + ZWSP + SHY + BOM,
            "" + WORD_JOINER + LRM + ZWSP + SHY + BOM);

    private static List<String> unsichtbareFolgen() {
        List<String> alle = new ArrayList<>();
        for (char uz : UNSICHTBAR) {
            alle.add(String.valueOf(uz));
        }
        alle.addAll(UNSICHTBAR_FOLGEN);
        return alle;
    }

    @Test
    void unsichtbarVorDemBuchstaben_hinterJedemKontextwort_sicherUndBereichAbDemBuchstaben() {
        for (String uz : unsichtbareFolgen()) {
            for (String wort : KONTEXTWOERTER) {
                assertSicher(wort + ": " + uz, G, "");
                assertSicher(wort + " " + uz, G, " Rest");
            }
            for (String wort : WEITERE_KONTEXTWOERTER) {
                assertSicher(wort + ": " + uz, G, ".");
            }
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_hinterKvNr_sicher() {
        for (String uz : unsichtbareFolgen()) {
            assertSicher("KV-Nr. " + uz, G, "");
            assertSicher("KV-Nr.: " + uz, G, "");
            assertSicher("KV Nr. " + uz, G, ", Rest");
            assertSicher("KV-Nummer " + uz, G, "");
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_direktHinterDemDoppelpunkt_sicher() {
        for (char uz : UNSICHTBAR) {
            assertSicher("KVNR:" + uz, KVNR_GUELTIG_ZWEI, "");
            assertSicher("Versichertennummer: " + uz + uz, KVNR_LEHRBEISPIEL, "");
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_trennerform_einFundUeberAlleZehnZeichen() {
        for (String uz : unsichtbareFolgen()) {
            for (char trenner : new char[] {' ', '-', '.', '/', NBSP}) {
                String wert = G.charAt(0) + String.valueOf(trenner) + ZIFFERN_G;
                assertSicher("KVNR: " + uz, wert, "");
                assertSicher("KV-Nr. " + uz, wert, ".");
                assertFund("K V N R " + uz, wert, "", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
            }
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_gesperrtesKontextwort_hoechstensNullAchtzig() {
        for (String uz : unsichtbareFolgen()) {
            for (String wort : List.of("K V N R ", "K V N R: ", "K. V. N. R. ", "K-V-N-R ", "K  V  N  R ")) {
                assertFund(wort + uz, G, "", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
                assertFund(wort + uz, G.charAt(0) + " " + ZIFFERN_G, ".", 0.80, ConfidenceLevel.WAHRSCHEINLICH,
                        QUELLE);
            }
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_falschePruefziffer_fraglichFuenfzig() {
        for (String uz : unsichtbareFolgen()) {
            assertFraglichPruefziffer("KVNR " + uz, KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "");
            assertFraglichPruefziffer("KV-Nr. " + uz, KVNR_UNGUELTIG_VERTAUSCHT, "");
            assertFraglichPruefziffer("K V N R " + uz, KVNR_UNGUELTIG_ZIFFER_GEAENDERT, "");
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_formAbweichung_fraglichVierzig() {
        for (String uz : unsichtbareFolgen()) {
            assertFraglichForm("KVNR " + uz, KVNR_ACHT_ZIFFERN, "");
            assertFraglichForm("Versichertennummer: " + uz, KVNR_ZEHN_ZIFFERN, "");
            assertFraglichForm("KV-Nr. " + uz, "Z" + LAENGE_12, ".");
            assertFraglichForm("K V N R " + uz, "Z" + LAENGE_6, "");
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_mehrereNummernHintereinander_jedeMitEigenemBereich() {
        String text = "KVNR: " + ZWSP + G + ", " + SHY + KVNR_GUELTIG_ZWEI;
        List<Finding> funde = scanKvnr(text);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(text.indexOf(G), funde.get(0).start(), "Start 1");
        assertEquals(text.indexOf(KVNR_GUELTIG_ZWEI), funde.get(1).start(), "Start 2");
        assertEquals(10, funde.get(1).end() - funde.get(1).start(), "Laenge 2");
    }

    @Test
    void unsichtbarVorDemBuchstaben_pseudonymisierungBehaeltDasUnsichtbareZeichen_roundtrip() {
        String text = "KVNR: " + ZWSP + G;
        List<Finding> funde = new TextScanner(List.of(R)).scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue(("KVNR: " + ZWSP + "[[KVNR_1]]").equals(ergebnis.text()), "Platzhalter im Text");
        ReidentificationResult zurueck = new Reidentifier().restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    @Test
    void unsichtbarVorDemBuchstaben_gegenprobe_zweiBuchstabenSindKeinAnfang() {
        for (String uz : unsichtbareFolgen()) {
            assertKeinFund("KVNR " + uz + "AB" + ZIFFERN_G);
            assertKeinFund("KVNR: " + uz + "Ab" + ZIFFERN_G);
            assertKeinFund("K V N R " + uz + "AB" + ZIFFERN_G);
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_gegenprobe_buchstabeOhneZiffern_keinFund() {
        for (String uz : unsichtbareFolgen()) {
            for (String nach : List.of("", " ", "-", " Rest", ".", ": ", "X", " " + ZWSP)) {
                assertKeinFund("KVNR " + uz + "Z" + nach);
                assertKeinFund("K V N R " + uz + "Z" + nach);
            }
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_gegenprobe_unsichtbaresZeichenZwischenBuchstabeUndZiffern_bleibtGrenze() {
        assertFraglichForm("KVNR " + ZWSP + "Z" + ZWSP, ZIFFERN_G, "");
        assertFraglichForm("KVNR Z" + ZWSP, ZIFFERN_G, "");
    }

    @Test
    void unsichtbarVorDemBuchstaben_ohneKontextwort_keinFund() {
        for (String uz : unsichtbareFolgen()) {
            assertKeinFund("Patient " + uz + U);
            assertKeinFund(uz + U);
        }
    }

    @Test
    void unsichtbarVorDemBuchstaben_lanrUndIk_bleibenWieDieKopie() {
        for (String uz : unsichtbareFolgen()) {
            for (String text : List.of("LANR " + uz + LANR_GUELTIG, "IK: " + uz + IK_GUELTIG, "LANR " + uz + G,
                    "L A N R " + uz + LANR_GUELTIG, "I K " + uz + IK_GUELTIG)) {
                assertTrue(new LanrRecognizer().detect(text).equals(KvStandTrenner.lanr().detect(text)),
                        "LANR weicht von der Kopie ab");
                assertTrue(new IkRecognizer().detect(text).equals(KvStandTrenner.ik().detect(text)),
                        "IK weicht von der Kopie ab");
            }
        }
    }

    @Test
    void laufzeit_einMillionUnsichtbareZeichenVorDemBuchstaben_einFund() {
        R.detect("KVNR " + String.valueOf(ZWSP).repeat(10_000) + G);
        String text = "KVNR " + String.valueOf(ZWSP).repeat(1_000_000) + G;
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(1, ist, "Fundzahl");
    }

    @Test
    void laufzeit_einMillionUnsichtbareZeichenOhneAnfang_keinFund() {
        String text = "KVNR " + String.valueOf(SHY).repeat(1_000_000) + "AB" + ZIFFERN_G;
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, ist, "Fundzahl");
    }

    @Test
    void laufzeit_wiederholtUnsichtbaresZeichenBuchstabeZiffer_keinFund() {
        String baustein = "" + ZWSP + "Z1";
        String text = "KVNR " + baustein.repeat(350_000);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, ist, "Fundzahl");
    }

    @Test
    void laufzeit_wiederholtKontextUnsichtbaresZeichenNummer_exakteFundzahl() {
        String baustein = "KVNR " + ZWSP + G + ". ";
        int anzahl = 1_000_000 / baustein.length() + 1;
        String text = baustein.repeat(anzahl);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten");
        assertEquals(anzahl, ist, "Fundzahl");
    }

    // ------------------------------------------------------------------ Umbruch (CR) mit unsichtbaren Zeichen

    /** Wie ohne {@code uz}, nur um dessen Laenge laenger: gleiche Fundzahl und Konfidenz, Start verschoben. */
    private static void assertWieOhneUnsichtbare(String vor, String uz, String wert, String nach) {
        List<Finding> mit = scanKvnr(vor + uz + wert + nach);
        List<Finding> ohne = scanKvnr(vor + wert + nach);
        assertEquals(ohne.size(), mit.size(), "Fundzahl weicht von der Variante ohne unsichtbare Zeichen ab");
        for (int i = 0; i < ohne.size(); i++) {
            assertEquals(ohne.get(i).confidence(), mit.get(i).confidence(), "Konfidenz");
            assertEquals(ohne.get(i).start() + uz.length(), mit.get(i).start(), "Start");
            assertTrue(ohne.get(i).originalValue().equals(mit.get(i).originalValue()), "Originalwert");
        }
    }

    @Test
    void umbruchCrDannUnsichtbaresZeichenDannDirektDerBuchstabe_ohneLf_einUmbruchEinFund() {
        for (String uz : unsichtbareFolgen()) {
            for (String vor : List.of("KVNR:\r", "KVNR\r", "Versichertennummer: \r", "KV-Nr.\r", "K V N R\r",
                    "eGK-Nummer:\r")) {
                assertFund(vor + uz, G, "", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
                assertWieOhneUnsichtbare(vor, uz, G, ", Rest");
            }
        }
    }

    @Test
    void umbruchCrUnsichtbaresZeichenLfDannKvnr_einUmbruchEinFund_wieOhneUnsichtbareZeichen() {
        for (String uz : unsichtbareFolgen()) {
            for (String vor : List.of("KVNR:\r", "KVNR\r", "Versichertennummer: \r", "KV-Nr.\r", "K V N R\r")) {
                assertFund(vor + uz + "\n", G, "", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
                assertFund(vor + uz + "\n" + uz, G, ".", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
                assertWieOhneUnsichtbare(vor, uz + "\n", G, " Rest");
            }
        }
    }

    @Test
    void umbruchMitUnsichtbarenZeichen_zweiUmbrueche_keinFund_wieOhneUnsichtbareZeichen() {
        for (String uz : unsichtbareFolgen()) {
            assertKeinFund("KVNR:\r" + uz + "\n\r" + uz + U);
            assertKeinFund("KVNR:\r" + uz + "\n\n" + U);
            assertKeinFund("KVNR:\r\n\r" + uz + U);
            assertKeinFund("KVNR:\n" + uz + "\n" + uz + U);
            assertKeinFund("KVNR:\r" + uz + "\r" + uz + U);
        }
        assertKeinFund("KVNR:\r\r" + U);
    }

    @Test
    void umbruchMitUnsichtbarenZeichen_ungueltigeNummer_fraglichFuenfzig() {
        for (String uz : unsichtbareFolgen()) {
            assertFraglichPruefziffer("KVNR:\r" + uz, U, "");
            assertFraglichPruefziffer("KVNR:\r" + uz + "\n", U, "");
        }
    }

    @Test
    void unsichtbaresZeichenDirektAmKontextwort_ohneSichtbarenTrenner_grenzeKontextwortDirektAmBuchstaben() {
        // Das Kontextwort, das unsichtbare Zeichen und der Buchstabe bilden einen Lauf: kein Kontextwort.
        for (char uz : UNSICHTBAR) {
            assertKeinFund("KVNR" + uz + U);
            assertKeinFund("Versichertennummer" + uz + U);
        }
        // Mit Trenner zwischen Kontextwort und unsichtbarem Zeichen ist es dagegen kein Hindernis.
        for (char uz : UNSICHTBAR) {
            assertSicher("KVNR " + uz, G, "");
        }
    }

    // ------------------------------------------------------------------ G-K5: gesperrtes Kontextwort, ein Trenner

    @Test
    void gesperrtesKontextwortMitEinemTrennerVorReinerZiffernfolge_einFundFormAbweichend() {
        for (String vor : List.of("K V N R ", "K-V-N-R ", "K.V.N.R ", "K V N R  ", "k v n r ",
                "K" + NBSP + "V" + NBSP + "N" + NBSP + "R ")) {
            assertFraglichForm(vor, LANR_GUELTIG, "");
        }
    }

    @Test
    void gesperrtesKontextwortMitEinemTrennerVorReinerZiffernfolge_andereLaengen() {
        assertFraglichForm("K V N R ", LAENGE_6, "");
        assertFraglichForm("K V N R ", LAENGE_12, ".");
        assertFraglichForm("K V N R: ", IK_GUELTIG, "");
        assertKeinFund("K V N R " + LAENGE_5);
    }

    @Test
    void gesperrtesKontextwortMitEinemTrennerVorReinerZiffernfolge_unsichtbaresZeichenVorDemLetztenBuchstaben() {
        for (String uz : unsichtbareFolgen()) {
            assertFraglichForm("K V N " + uz + "R ", LANR_GUELTIG, "");
        }
    }

    @Test
    void gesperrtesKontextwortVorEchterKvnrForm_bleibtBuchstabenform() {
        String buchstabe = G.substring(0, 1);
        for (String vor : List.of("K V N R ", "K-V-N-R ", "K. V. N. R. ", "K V N R: ", "K  V  N  R ")) {
            assertFund(vor, G, "", 0.80, ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
            for (char trenner : new char[] {' ', '-', '.', '/', NBSP}) {
                assertFund(vor, buchstabe + String.valueOf(trenner) + ZIFFERN_G, "", 0.80,
                        ConfidenceLevel.WAHRSCHEINLICH, QUELLE);
            }
        }
    }

    @Test
    void gesperrtesKontextwortOhneBuchstabeVorZiffern_bleibtWieBisher() {
        assertFraglichForm("K V N R: ", ZIFFERN_G, "");
        assertFraglichForm("K V N R ", LAENGE_8, "");
        assertKeinFund("K V Z " + U);
    }
}
