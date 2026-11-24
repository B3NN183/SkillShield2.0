package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NETZVERBUND;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_35;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_SONDER_75_SCHLUESSEL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_UNBEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.KOMMA;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE_LAENGE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.QUELLE_REGIONALCODE;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.REGIONALCODE_UNBEKANNT;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.SICHER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.WEITER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.scanBsnr;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_GROSS;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_KLEIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Bekannte Grenzen der BSNR (Feature kvnr-bsnr-erkennung, Task T-15, AK-36; Plan Kap. 11 und die Nachtraege zu T-13
 * und T-14). Jede Grenze hat mindestens einen Test {@code bekannteGrenze...} mit Positiv-Gegenprobe. Die Tests halten
 * das tatsaechliche Verhalten fest und sind ausdruecklich kein Soll fuer kuenftige Erweiterungen: Wird eine Grenze
 * geschlossen, ist der Test anzupassen. "Kein Fund" heisst: kein BSNR-Fund des {@code BsnrRecognizer}; die Tests auf
 * Scannerebene pruefen zusaetzlich, dass die Nummer im Klartext bleibt (kein Fund irgendeines Typs deckt sie). Jeder
 * Text besteht den Waechter {@link BsnrSynthetischWaechter}; Wertvergleiche laufen ueber {@code assertTrue(equals)},
 * damit keine Nummer in einer Fehlermeldung landet. Umlaute, Kombinationszeichen und Nullbreitenzeichen entstehen zur
 * Laufzeit.
 *
 * <p><b>Tabelle Grenze zu Test</b> (Kennungen wie in Plan Kap. 11 und im Javadoc von {@code KvNumberScanner}); ohne
 * Klassenangabe steht der Test in dieser Klasse:
 * <ul>
 * <li>G-B1: {@code bekannteGrenzeGB1_nummerOhneKontextwortInTabelleOderHinterLeerzeile_keinFund_gegenprobe...}
 * (eine BSNR hat keine Pruefziffer; ohne Kontextwort ist keine Zahl eine BSNR, E-2)</li>
 * <li>G-B2: {@code bekannteGrenzeGB2_zerlegterUmlaut_keinKontextwort_gegenprobeMitEinemZeichen}; ausserdem
 * {@code BsnrRecognizerTest.bekannteGrenze_zerlegterUmlaut_keinFund_gegenprobeMitEinemZeichen}</li>
 * <li>G-B3: {@code bekannteGrenzeGB3_hauptUndNebenbetriebsstaette_gleicherTypGleicheFelder}</li>
 * <li>G-D1: {@code bekannteGrenzeGD1_formularfeld_...} (der Typ kann bei Gleichstand "falsch" sein, die Position ist
 * geschuetzt) und {@code bekannteGrenzeGD1_konfliktregel_...} (die Reihenfolge bei Gleichstand)</li>
 * <li>Abkuerzungen und Schreibweisen ausserhalb D-8 (Nachtrag zu T-13, Punkt 3; Folgefeature-Vorschlag fuer
 * {@code Betriebsst.-Nr.}: Abkuerzungspunkt als Nachsatz-Trenner):
 * {@code bekannteGrenzeD8_abkuerzungenAusserhalbDerListe_keinFund_gegenprobeVolleWoerter}; die bereits vorhandene
 * Fassung steht in {@code BsnrRecognizerTest.bekannteGrenze_abkuerzungBetriebsst_keinFund_gegenprobeVollesWort}</li>
 * <li>{@code Betriebsstaette} ohne Nachsatzwort:
 * {@code bekannteGrenzeD8_betriebsstaetteOhneNachsatzwort_keinFund_gegenprobeMitNachsatzwort}</li>
 * <li>Leerzeile zwischen Kontextwort und Nummer:
 * {@code bekannteGrenzeKontextregel_leerzeileZwischenKontextwortUndNummer_keinFund_gegenprobeEinUmbruch}</li>
 * <li>{@code <Kontextwort> <IPv4>/<Nummer>} (Nachtrag zu T-14, Punkt 3 a):
 * {@code bekannteGrenzeBestand_kontextwortIpv4UndGruppierteNummer_restBleibtUngedeckt_gegenprobe...}</li>
 * <li>Rest von einer bis zwei Ziffern neben einem Bestandsfund, G-K14 fuer {@code bsnr-rest} (Nachtrag zu T-14,
 * Punkt 3 b): {@code bekannteGrenzeGK14_restVonEinsBisZweiZiffernNebenBestandsfund_...}</li>
 * <li>Unbekannter Regionalcode nur "fraglich", nie "sicher":
 * {@code bekannteGrenzeD8_unbekannterRegionalcode_nurFraglich_gegenprobeBekannterCode}</li>
 * <li>Grenzen der Vorgaenger sinngemaess (Nullbreitenzeichen in der Nummer, Kombinationszeichen am Kontextwort):
 * {@code bekannteGrenzeVorgaenger_...}</li>
 * </ul>
 * Hinweis fuer das UI-Feature (kein Mangel): Ein zusammengelegter Fund kann durch einen teilweise ueberlappenden
 * BSNR-Kandidaten von "sicher" auf "wahrscheinlich" fallen (K-4, die Abdeckung wird groesser); ein spaeterer Filter
 * nach Stufe muss das beruecksichtigen.
 */
class BsnrGrenzenTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();
    private static final String B = BSNR_BEKANNT;
    private static final String B2 = BSNR_BEKANNT_ZWEI;
    private static final String U = BSNR_UNBEKANNT;
    private static final String L = LANR_GUELTIG;
    private static final String BL = BSNR_LANR_GUELTIG;
    private static final String AE = String.valueOf(AE_KLEIN);
    private static final String AE_G = String.valueOf(AE_GROSS);
    private static final String ZERLEGT = "a" + (char) 0x308;
    private static final String DOMAIN = "@example.invalid";
    /** Adresse aus dem Dokumentationsbereich 192.0.2.0/24 (sechs Ziffern, keine Neunerfolge). */
    private static final String IP_DOKU = "192.0.2.1";

    // ------------------------------------------------------------------ Hilfen

    /** Scan mit {@code standard()}: Waechter, Teilstring-Invariante, Sortierung, Wiederholung gleich. */
    private static List<Finding> scanStandard(String text) {
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
        List<Finding> funde = TextScanner.standard().scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        assertTrue(funde.equals(TextScanner.standard().scan(text)), "Fundliste bei Wiederholung nicht gleich");
        return funde;
    }

    private static void assertKeinFund(String text) {
        assertTrue(scanBsnr(text).isEmpty(), "Es entstand ein BSNR-Fund");
    }

    /** Genau ein BSNR-Fund {@code wert} hinter {@code vor} mit Konfidenz und Quelle (Gegenprobe). */
    private static void assertFund(String vor, String wert, String nach, double konfidenz, String quelle) {
        List<Finding> funde = scanBsnr(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertTrue(quelle.equals(f.source()), "Quelle");
    }

    private static boolean[] abdeckung(String text, List<Finding> funde) {
        boolean[] gedeckt = new boolean[text.length()];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                gedeckt[i] = true;
            }
        }
        return gedeckt;
    }

    /** Zahl der ASCII-Ziffern in {@code [von, bis)}, die kein Fund des Scans abdeckt. */
    private static int ungedeckteZiffern(String text, List<Finding> funde, int von, int bis) {
        boolean[] gedeckt = abdeckung(text, funde);
        int zahl = 0;
        for (int i = von; i < bis; i++) {
            zahl += !gedeckt[i] && text.charAt(i) >= '0' && text.charAt(i) <= '9' ? 1 : 0;
        }
        return zahl;
    }

    /** Die Nummer steht im Scan ganz im Klartext: Kein Fund irgendeines Typs deckt eine ihrer Ziffern. */
    private static void assertNummerImKlartext(String text, String nummer) {
        List<Finding> funde = scanStandard(text);
        int start = text.indexOf(nummer);
        assertTrue(start >= 0, "Testdaten: Nummer nicht im Text");
        int ziffern = (int) nummer.chars().filter(c -> c >= '0' && c <= '9').count();
        assertEquals(ziffern, ungedeckteZiffern(text, funde, start, start + nummer.length()),
                "Ziffern der Nummer sind von einem Fund abgedeckt");
        assertTrue(PSEUDONYMIZER.pseudonymize(text, funde).text().contains(nummer), "Nummer im Ausgabetext");
    }

    private static void assertNummerGedeckt(String text, String nummer) {
        List<Finding> funde = scanStandard(text);
        int start = text.indexOf(nummer);
        assertEquals(0, ungedeckteZiffern(text, funde, start, start + nummer.length()),
                "Ziffern der Nummer bleiben im Klartext");
    }

    // ------------------------------------------------------------------ G-B1

    /**
     * G-B1 (E-2): Eine BSNR ohne Kontextwort ist kein Fund, in keiner Schreibweise und unabhaengig vom Regionalcode
     * (es gibt keine Pruefziffer, die eine Zahl als BSNR ausweisen koennte); ebenso eine Nummer in einer Tabelle unter
     * einer Spaltenueberschrift {@code BSNR}, wenn mehr als drei Woerter oder eine Leerzeile dazwischen liegen. Die
     * Nummer bleibt im Klartext. Gegenprobe: dieselbe Nummer mit Kontextwort davor, und die Ueberschrift mit
     * hoechstens drei Woertern und einem Umbruch ist ein Fund.
     */
    @Test
    void bekannteGrenzeGB1_nummerOhneKontextwortInTabelleOderHinterLeerzeile_keinFund_gegenprobeMitKontextwort() {
        for (String nummer : List.of(B, U, B2, BSNR_NETZVERBUND, B.substring(0, 3) + " "
                + B.substring(3, 6) + " " + B.substring(6), B.substring(0, 4) + "-" + B.substring(4))) {
            assertKeinFund(nummer);
            assertKeinFund("Der Wert " + nummer + " steht hier");
            assertNummerImKlartext("Der Wert " + nummer + " steht hier", nummer);
        }
        // Tabelle: Ueberschrift mit vier weiteren Spalten, die Nummer in der naechsten Zeile (vier Fuellwoerter)
        assertKeinFund("BSNR\tName\tOrt\tTelefon\tFax\n" + B + "\tMuster");
        assertNummerImKlartext("BSNR\tName\tOrt\tTelefon\tFax\n" + B + "\tMuster", B);
        // hinter einer Leerzeile
        assertKeinFund("BSNR\n\n" + B);
        assertNummerImKlartext("BSNR\n\n" + B, B);
        // Gegenproben: mit Kontextwort, und die Ueberschrift mit drei Woertern und einem Umbruch
        assertFund("BSNR ", B, "", SICHER, QUELLE);
        assertFund("BSNR\tName\tOrt\tTelefon\n", B, "\tMuster", WEITER, QUELLE);
        assertFund("BSNR\n", B, "", WEITER, QUELLE);
    }

    // ------------------------------------------------------------------ G-B2

    /**
     * G-B2: Zerlegte Umlaute ({@code a} plus U+0308) in {@code Betriebsstaette...} sind Kombinationszeichen und ergeben
     * kein Kontextwort; auch der Grossbuchstabe mit Kombinationszeichen nicht. Die Nummer bleibt im Klartext.
     * Gegenprobe: derselbe Text mit dem zusammengesetzten Umlaut (ein Zeichen) und mit {@code ae} ist ein Fund (0,90).
     */
    @Test
    void bekannteGrenzeGB2_zerlegterUmlaut_keinKontextwort_gegenprobeMitEinemZeichen() {
        String grossZerlegt = "A" + (char) 0x308;
        for (String text : List.of("Betriebsst" + ZERLEGT + "ttennummer " + B, "Betriebsst" + ZERLEGT + "tte Nr. " + B,
                "Nebenbetriebsst" + ZERLEGT + "ttennummer: " + B, "Nebenbetriebsst" + ZERLEGT + "tten-Nr. " + B,
                "BETRIEBSST" + grossZerlegt + "TTENNUMMER " + B)) {
            assertKeinFund(text);
            assertNummerImKlartext(text, B);
        }
        assertFund("Betriebsst" + AE + "ttennummer ", B, "", SICHER, QUELLE);
        assertFund("Betriebsst" + AE + "tte Nr. ", B, "", SICHER, QUELLE);
        assertFund("BETRIEBSST" + AE_G + "TTENNUMMER ", B, "", SICHER, QUELLE);
        assertFund("Betriebsstaettennummer ", B, "", SICHER, QUELLE);
    }

    // ------------------------------------------------------------------ G-B3

    /**
     * G-B3: Haupt- und Nebenbetriebsstaette sind nicht unterscheidbar: {@code BSNR}, {@code NBSNR} und
     * {@code Nebenbetriebsstaettennummer} ergeben denselben Typ BSNR mit denselben Feldern (Konfidenz, Quelle); der
     * Review sieht keinen Unterschied. Gegenprobe: das Kontextwort bleibt im Text stehen und ist kein Teil des Fundes.
     */
    @Test
    void bekannteGrenzeGB3_hauptUndNebenbetriebsstaette_gleicherTypGleicheFelder() {
        List<Finding> haupt = scanBsnr("BSNR " + B);
        List<Finding> neben = scanBsnr("NBSNR " + B);
        List<Finding> nebenWort = scanBsnr("Nebenbetriebsst" + AE + "ttennummer " + B);
        for (List<Finding> funde : List.of(haupt, neben, nebenWort)) {
            assertEquals(1, funde.size(), "Anzahl Funde");
            assertEquals(FindingType.BSNR, funde.get(0).type(), "Typ");
            assertEquals(SICHER, funde.get(0).confidence(), "Konfidenz");
            assertTrue(QUELLE.equals(funde.get(0).source()), "Quelle");
            assertTrue(B.equals(funde.get(0).originalValue()), "Originalwert ist nur die Nummer");
        }
        PseudonymizationResult ersetzt = PSEUDONYMIZER.pseudonymize("NBSNR " + B, neben);
        assertTrue("NBSNR [[BSNR_1]]".equals(ersetzt.text()), "Kontextwort bleibt, Platzhalter BSNR");
    }

    // ------------------------------------------------------------------ Abkuerzungen und Schreibweisen ausserhalb D-8

    /**
     * Nachtrag zu T-13, Punkt 3: Abkuerzungen und Schreibweisen ausserhalb der Wortliste von D-8 sind kein Kontextwort
     * (Verwechslungs- und Fehlalarmgefahr, Nutzervorgabe: bei vermehrten Fehlalarmen lassen): {@code Betriebsst.-Nr.},
     * {@code BS-Nr.}, {@code BSN}, {@code BSt-Nr.}, {@code HBSNR}, Pluralformen, Tippfehler und ein Trennstrich im
     * Wort. Die Nummer bleibt im Klartext. Gegenprobe: jedes Wort der Liste mit ganzen Woertern ist ein Fund. Folge-
     * feature-Vorschlag: der Abkuerzungspunkt als Nachsatz-Trenner fuer {@code Betriebsst.-Nr.}.
     */
    @Test
    void bekannteGrenzeD8_abkuerzungenAusserhalbDerListe_keinFund_gegenprobeVolleWoerter() {
        List<String> woerter = List.of("Betriebsst.-Nr.", "Betriebsst. Nr.", "Betriebsst Nr.", "BS-Nr.", "BS Nr.",
                "BSN", "BSt-Nr.", "HBSNR", "BSNRs", "BSNRn", "BSNRen", "Betriebsst" + AE + "ttennummern",
                "Betriebsst" + AE + "ttennrn", "Betriebsstaettennummern", "BSRN", "BNSR", "BSMR", "Betriebsstatte Nr.",
                "Betriebsst" + AE + "te Nr.", "Betriebsstaetten-Nnr.", "BS-NR", "B-SNR", "BSN-R",
                "Betriebs-st" + AE + "tten-nummer", "Betriebsstaetten-num-mer", "Betriebs staettennummer");
        for (String wort : woerter) {
            assertKeinFund(wort + " " + B);
            assertKeinFund(wort + ": " + B);
            assertNummerImKlartext(wort + " " + B, B);
        }
        for (String wort : List.of("BSNR", "BSNR-Nr.", "Betriebsstaettennummer", "Betriebsst" + AE + "ttennr",
                "Betriebsstaette Nr.", "Nebenbetriebsstaetten-Nummer")) {
            assertFund(wort + " ", B, "", SICHER, QUELLE);
        }
    }

    /**
     * D-8: {@code Betriebsstaette}, {@code Betriebsstaetten}, {@code Nebenbetriebsstaette} und
     * {@code Nebenbetriebsstaetten} sind nur mit Nachsatzwort {@code Nr} oder {@code Nummer} ein Kontextwort; ohne
     * Nachsatzwort (auch mit Doppelpunkt oder Gleichheitszeichen) entsteht kein Fund, die Nummer bleibt im Klartext.
     * Gegenprobe: dasselbe Wort mit {@code Nr.} und mit {@code Nummer} ist ein Fund (0,90).
     */
    @Test
    void bekannteGrenzeD8_betriebsstaetteOhneNachsatzwort_keinFund_gegenprobeMitNachsatzwort() {
        for (String wort : List.of("Betriebsst" + AE + "tte", "Betriebsstaette", "Betriebsstaetten",
                "Nebenbetriebsst" + AE + "tte", "Nebenbetriebsstaetten", "BETRIEBSSTAETTE")) {
            assertKeinFund(wort + " " + B);
            assertKeinFund(wort + ": " + B);
            assertKeinFund(wort + "= " + B);
            assertNummerImKlartext(wort + " " + B, B);
            assertFund(wort + " Nr. ", B, "", SICHER, QUELLE);
            assertFund(wort + " Nummer ", B, "", SICHER, QUELLE);
        }
    }

    // ------------------------------------------------------------------ Kontextregel: Leerzeile

    /**
     * Regel der Vorgaenger (Leerzeile beendet den Kontext, fuer alle Arten gleich): Zwischen Kontextwort und Nummer
     * ergibt eine Leerzeile (auch mit Leerzeichen oder Tab in der Zwischenzeile, auch {@code CR LF CR LF}) keinen Fund,
     * die Nummer bleibt im Klartext. Gegenprobe: ein einzelner Zeilenumbruch ({@code LF}, {@code CR LF}) ist ein Fund
     * (0,80).
     */
    @Test
    void bekannteGrenzeKontextregel_leerzeileZwischenKontextwortUndNummer_keinFund_gegenprobeEinUmbruch() {
        for (String umbruch : List.of("\n\n", ":\n\n", "\r\n\r\n", ":\r\n\r\n", "\n \n", "\n\t\n", "\r\r")) {
            String text = "BSNR" + umbruch + B;
            assertKeinFund(text);
            assertNummerImKlartext(text, B);
        }
        assertFund("BSNR\n", B, "", WEITER, QUELLE);
        assertFund("BSNR:\r\n", B, "", WEITER, QUELLE);
        assertFund("BSNR\r", B, "", WEITER, QUELLE);
    }

    // ------------------------------------------------------------------ Bestand: IPv4 und Schraegstrich

    /**
     * Nachtrag zu T-14, Punkt 3 a ({@code <Kontextwort> <IPv4>/<Nummer>}, bestand schon bei der LANR, vgl. G-K18): Die
     * Ziffernkette aus IP-Adresse, einem einzelnen Trenner (Schraegstrich oder Leerzeichen) und gruppierter Nummer ist
     * laenger als zwoelf Ziffern. Der Scan liefert den IP-Fund und, nach dem Zuschneiden am Bestand, einen Restfund
     * ueber die erste Zifferngruppe (Quelle {@code bsnr-rest}, 0,40); die Ziffern hinter dem Bindestrich bleiben im
     * Klartext (fuenf Ziffern). Bei der LANR bleiben dieselben fuenf Ziffern ebenfalls stehen. Die geschlossene Nummer
     * ist nicht betroffen (genaue Fassung der Grenze: Der Plan nennt {@code <Kontextwort> <IPv4>/<Nummer>} ohne die
     * Gruppierung; am Produkt gemessen bleibt eine geschlossene Nummer abgedeckt). Gegenprobe: Die geschlossene Nummer
     * hinter {@code IP/} ist ein eigener Fund (0,80), ebenso die Nummer vor {@code /IP} (0,90), die gruppierte Nummer
     * ohne die IP und die gruppierte Nummer hinter Komma und Leerzeichen.
     */
    @Test
    void bekannteGrenzeBestand_kontextwortIpv4UndGruppierteNummer_restBleibtUngedeckt_gegenprobeGeschlossen() {
        String gruppiert = B.substring(0, 4) + "-" + B.substring(4);
        String text = "BSNR " + IP_DOKU + "/" + gruppiert;
        List<Finding> funde = scanStandard(text);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.IP, funde.get(0).type(), "Typ des ersten Fundes");
        assertEquals(FindingType.BSNR, funde.get(1).type(), "Typ des zweiten Fundes");
        assertTrue(BsnrRecognizer.SOURCE_REST.equals(funde.get(1).source()), "Restfund aus dem Zuschneiden");
        assertEquals(4, funde.get(1).end() - funde.get(1).start(), "Restfund ueber die erste Gruppe");
        int nummerStart = text.indexOf(gruppiert);
        assertEquals(5, ungedeckteZiffern(text, funde, nummerStart, nummerStart + gruppiert.length()),
                "fuenf Ziffern hinter dem Bindestrich bleiben im Klartext");
        String lanrGruppiert = L.substring(0, 4) + "-" + L.substring(4);
        String lanrText = "LANR " + IP_DOKU + "/" + lanrGruppiert;
        int lanrStart = lanrText.indexOf(lanrGruppiert);
        assertEquals(5, ungedeckteZiffern(lanrText, scanStandard(lanrText), lanrStart, lanrStart
                + lanrGruppiert.length()), "bei der LANR bleiben dieselben fuenf Ziffern stehen");
        // dasselbe gilt mit einem Leerzeichen statt des Schraegstrichs: ein einzelner Trenner verbindet die Kette
        String mitLeerzeichen = "BSNR " + IP_DOKU + " " + gruppiert;
        int leerzeichenStart = mitLeerzeichen.indexOf(gruppiert);
        assertEquals(5, ungedeckteZiffern(mitLeerzeichen, scanStandard(mitLeerzeichen), leerzeichenStart,
                leerzeichenStart + gruppiert.length()), "fuenf Ziffern bleiben auch hinter einem Leerzeichen");
        // Gegenproben: geschlossen, umgekehrte Reihenfolge, ohne IP und mit Komma statt des einzelnen Trenners
        assertNummerGedeckt("BSNR " + IP_DOKU + "/" + B, B);
        assertNummerGedeckt("BSNR " + B + "/" + IP_DOKU, B);
        assertNummerGedeckt("BSNR " + gruppiert, gruppiert);
        assertNummerGedeckt("BSNR " + IP_DOKU + ", " + gruppiert, gruppiert);
    }

    // ------------------------------------------------------------------ G-K14 fuer bsnr-rest

    /**
     * G-K14 (Nachtrag zu T-14, Punkt 3 b): Bestand hat Vorrang. Ein Rest von einer oder zwei Ziffern vor dem lokalen
     * Teil einer E-Mail, der den hinteren Teil der gruppierten Nummer verschluckt, bleibt im Klartext (unter der
     * Schwelle von drei Ziffern kein Restfund); nur die E-Mail ist ein Fund. Gegenprobe: Bei drei Ziffern bleibt ein
     * Restfund (Quelle {@code bsnr-rest}, 0,80), und ohne E-Mail ist die Nummer ein Fund ueber alle Zeichen.
     */
    @Test
    void bekannteGrenzeGK14_restVonEinsBisZweiZiffernNebenBestandsfund_bleibtStehen_gegenprobeDreiZiffern() {
        for (int rest = 1; rest <= 2; rest++) {
            String vorne = B.substring(0, rest);
            String text = "BSNR " + vorne + " " + B.substring(rest) + DOMAIN;
            List<Finding> funde = scanStandard(text);
            assertEquals(1, funde.size(), "nur die E-Mail ist ein Fund, Rest " + rest);
            assertEquals(FindingType.EMAIL, funde.get(0).type(), "Typ des einzigen Fundes");
            assertEquals(rest, ungedeckteZiffern(text, funde, 0, text.length()), "Ziffern im Klartext, Rest " + rest);
            assertTrue(("BSNR " + vorne + " [[EMAIL_1]]").equals(PSEUDONYMIZER.pseudonymize(text, funde).text()),
                    "Ausgabetext, Rest " + rest);
        }
        String drei = "BSNR " + B.substring(0, 3) + " " + B.substring(3) + DOMAIN;
        List<Finding> mitRest = scanStandard(drei);
        assertEquals(2, mitRest.size(), "Restfund und E-Mail");
        assertEquals(FindingType.BSNR, mitRest.get(0).type(), "Typ des Restfundes");
        assertTrue(BsnrRecognizer.SOURCE_REST.equals(mitRest.get(0).source()), "Quelle des Restfundes");
        assertEquals(WEITER, mitRest.get(0).confidence(), "Konfidenz des Restfundes");
        assertEquals(FindingType.EMAIL, mitRest.get(1).type(), "Typ der E-Mail");
        assertEquals(0, ungedeckteZiffern(drei, mitRest, 0, drei.length()), "alle Ziffern abgedeckt");
        // ohne E-Mail ein Fund ueber alle Zeichen
        assertFund("BSNR ", B.substring(0, 2) + " " + B.substring(2), "", SICHER, QUELLE);
    }

    // ------------------------------------------------------------------ Regionalcode

    /**
     * D-8, AK-27: Der Regionalcode bestimmt nie, ob ein Fund entsteht (Schutz geht vor): Eine Nummer mit unbekanntem
     * Regionalcode, die Netzverbundnummer (Praefix 99) und eine Sonderkennung (35, 75) mit unbekanntem Schluessel
     * ergeben nur die Stufe "fraglich" (0,50, Quelle {@code bsnr-regionalcode-unbekannt}), in jeder Kontextstellung nie
     * "sicher" und nie "wahrscheinlich". Gegenprobe: bekannte Codes ergeben in denselben Stellungen die hoehere Stufe.
     */
    @Test
    void bekannteGrenzeD8_unbekannterRegionalcode_nurFraglich_gegenprobeBekannterCode() {
        for (String unbekannt : List.of(U, BSNR_NETZVERBUND, BSNR_SONDER_35)) {
            assertFund("BSNR ", unbekannt, "", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
            assertFund("NBSNR: ", unbekannt, "", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
            assertFund("BSNR der Praxis ", unbekannt, "", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
            assertFund("", unbekannt, " (BSNR)", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
            assertFund("B S N R ", unbekannt, "", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
            assertEquals(ConfidenceLevel.FRAGLICH, scanBsnr("BSNR " + unbekannt).get(0).level(), "Stufe");
        }
        // Sonderkennung 75 mit bekanntem Schluessel: bekannt, also sicher
        assertFund("BSNR ", BSNR_SONDER_75_SCHLUESSEL, "", SICHER, QUELLE);
        // Gegenprobe: bekannte Codes in denselben Stellungen
        assertFund("BSNR ", B, "", SICHER, QUELLE);
        assertFund("NBSNR: ", B2, "", SICHER, QUELLE);
        assertFund("BSNR der Praxis ", B, "", WEITER, QUELLE);
        assertFund("", B, " (BSNR)", WEITER, QUELLE);
        assertFund("", B, ", BSNR", KOMMA, QUELLE);
        // Laengenabweichung: ebenfalls nur fraglich (0,40)
        assertFund("BSNR ", SynthetischeKvNummern.LAENGE_8, "", 0.40, QUELLE_LAENGE);
    }

    // ------------------------------------------------------------------ G-D1

    /**
     * G-D1 (Formularfeld {@code BSNR/LANR <B>/<L>}, D-9): Beide Positionen sind in jedem Fall abgedeckt, aber der Typ
     * kann bei Gleichstand der Konfidenz "falsch" sein. Eine gueltige LANR, deren erste zwei Ziffern ein bekannter
     * Regionalcode sind (Testnummer {@code BSNR_LANR_GUELTIG}), hat als LANR-Kandidat und als BSNR-Kandidat dieselbe
     * Konfidenz 0,80; die Konfliktregel waehlt BSNR. In {@code BSNR/LANR BL/BL} ist die zweite Nummer, die LANR, ein
     * BSNR-Fund (0,80); in {@code LANR/BSNR BL/BL} sind beide Nummern BSNR-Funde. Die Position ist geschuetzt, der
     * Platzhalter fuehrt zum Originalwert zurueck (Roundtrip). Gegenprobe: mit einer LANR ohne bekannten Regionalcode
     * (Praefix 00) ist der Typ in beiden Positionen der erwartete.
     */
    @Test
    void bekannteGrenzeGD1_formularfeld_gleichstandBsnrLanr_typKannFalschSeinPositionenGeschuetzt() {
        assertTrue(SynthetischeKvNummern.lanrGueltigReferenz(BL), "Testdaten: BL ist eine gueltige LANR");
        assertFalse(SynthetischeKvNummern.ikGueltigReferenz(BL), "Testdaten: BL ist keine gueltige IK");
        String vorwaerts = "BSNR/LANR " + BL + "/" + BL;
        List<Finding> funde = scanStandard(vorwaerts);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.LANR, funde.get(0).type(), "erste Nummer: LANR direkt hinter dem Kontextwort (0,95)");
        assertEquals(FindingType.BSNR, funde.get(1).type(), "zweite Nummer, die LANR: Gleichstand 0,80, BSNR gewinnt");
        assertEquals(WEITER, funde.get(1).confidence(), "Konfidenz des zweiten Fundes");
        assertEquals(0, ungedeckteZiffern(vorwaerts, funde, 0, vorwaerts.length()), "beide Positionen geschuetzt");
        List<Finding> lanrAllein = new LanrRecognizer().detect(vorwaerts);
        assertEquals(2, lanrAllein.size(), "der LANR-Erkenner findet beide Nummern");
        assertEquals(WEITER, lanrAllein.get(1).confidence(), "LANR-Kandidat der zweiten Nummer: Gleichstand mit BSNR");
        String rueckwaerts = "LANR/BSNR " + BL + "/" + BL;
        List<Finding> umgekehrt = scanStandard(rueckwaerts);
        assertEquals(2, umgekehrt.size(), "Anzahl Funde, umgekehrte Reihenfolge");
        assertEquals(FindingType.BSNR, umgekehrt.get(0).type(), "erste Nummer, die LANR: BSNR");
        assertEquals(FindingType.BSNR, umgekehrt.get(1).type(), "zweite Nummer: BSNR");
        assertEquals(0, ungedeckteZiffern(rueckwaerts, umgekehrt, 0, rueckwaerts.length()), "geschuetzt");
        PseudonymizationResult ersetzt = PSEUDONYMIZER.pseudonymize(rueckwaerts, umgekehrt);
        assertTrue(rueckwaerts.equals(REIDENTIFIER.restore(ersetzt.text(), ersetzt.mapping()).text()),
                "Roundtrip zeichengleich");
        // Gegenprobe: eine LANR mit Praefix 00 und eine BSNR mit bekanntem Regionalcode behalten ihren Typ
        List<Finding> normal = scanStandard("BSNR/LANR " + B + "/" + L);
        assertEquals(2, normal.size(), "Anzahl Funde, Gegenprobe");
        assertEquals(FindingType.BSNR, normal.get(0).type(), "BSNR bleibt BSNR");
        assertEquals(FindingType.LANR, normal.get(1).type(), "LANR bleibt LANR");
        List<Finding> normalUmgekehrt = scanStandard("LANR/BSNR " + L + "/" + B);
        assertEquals(FindingType.LANR, normalUmgekehrt.get(0).type(), "LANR bleibt LANR, umgekehrt");
        assertEquals(FindingType.BSNR, normalUmgekehrt.get(1).type(), "BSNR bleibt BSNR, umgekehrt");
    }

    /**
     * G-D1 (Konfliktregel bei Gleichstand der Konfidenz und gleichem Bereich, Plan Kap. 11): {@code BSNR} gewinnt vor
     * {@code IK}, {@code KVNR} und {@code LANR}, {@code KVNR} vor {@code LANR}, {@code IP} vor allen KV-Arten, in jeder
     * Reihenfolge der Kandidaten. Es bleibt immer genau ein Fund ueber der Position (sie ist geschuetzt, der Typ kann
     * "falsch" sein). Gegenprobe: Bei hoeherer Konfidenz gewinnt der Fund mit der hoeheren Konfidenz.
     */
    @Test
    void bekannteGrenzeGD1_konfliktregel_gleichstandBsnrVorIkKvnrLanr_kvnrVorLanr_ipVorAllen() {
        String text = B;
        FindingType bsnr = FindingType.BSNR;
        FindingType ik = FindingType.IK;
        FindingType kvnr = FindingType.KVNR;
        FindingType lanr = FindingType.LANR;
        FindingType ip = FindingType.IP;
        assertEquals(bsnr, gleichstand(text, bsnr, ik).type(), "BSNR gegen IK");
        assertEquals(bsnr, gleichstand(text, ik, bsnr).type(), "IK gegen BSNR");
        assertEquals(bsnr, gleichstand(text, bsnr, kvnr).type(), "BSNR gegen KVNR");
        assertEquals(bsnr, gleichstand(text, bsnr, lanr).type(), "BSNR gegen LANR");
        assertEquals(kvnr, gleichstand(text, kvnr, lanr).type(), "KVNR gegen LANR");
        assertEquals(kvnr, gleichstand(text, lanr, kvnr).type(), "LANR gegen KVNR");
        for (FindingType kv : List.of(bsnr, ik, kvnr, lanr)) {
            assertEquals(ip, gleichstand(text, ip, kv).type(), "IP gegen " + kv.name());
            assertEquals(ip, gleichstand(text, kv, ip).type(), "IP gegen " + kv.name() + ", umgekehrt");
        }
        // Gegenprobe: die hoehere Konfidenz gewinnt unabhaengig vom Typ
        List<Finding> hoeher = ConflictResolver.resolve(text, List.of(new Finding(lanr, text, 0, 9, 0.95, "a"),
                new Finding(bsnr, text, 0, 9, 0.80, "b")));
        assertEquals(1, hoeher.size(), "ein Fund");
        assertEquals(lanr, hoeher.get(0).type(), "0,95 gewinnt gegen 0,80");
    }

    private static Finding gleichstand(String text, FindingType erster, FindingType zweiter) {
        List<Finding> aufgeloest = ConflictResolver.resolve(text, List.of(
                new Finding(erster, text, 0, text.length(), WEITER, "a"),
                new Finding(zweiter, text, 0, text.length(), WEITER, "b")));
        assertEquals(1, aufgeloest.size(), "genau ein Fund ueber der Position");
        return aufgeloest.get(0);
    }

    // ------------------------------------------------------------------ Grenzen der Vorgaenger

    /**
     * Grenzen der Vorgaenger, sinngemaess fuer die BSNR (Anforderung, Bekannte Grenzen; Grenzen 7 und 8): Ein
     * Nullbreitenzeichen in der Nummer beendet die Zifferngruppe, nur zusammenhaengende Teile mit mindestens sechs
     * Ziffern sind ein Fund (Laengenabweichung 0,40), der Rest bleibt im Klartext (hier drei Ziffern vorn oder hinten).
     * Das gilt nur bei der Teilung 6+3 oder 3+6; ohne Teil mit sechs Ziffern bleibt die ganze Nummer im Klartext
     * (G-B4, Tests {@code bekannteGrenzeGB4_...}). Ein Kombinationszeichen direkt am Kontextwort macht daraus ein
     * anderes Wort: kein Kontext, die Nummer bleibt im Klartext.
     * Gegenprobe: ohne diese Zeichen ist die Nummer ein Fund (0,90).
     */
    @Test
    void bekannteGrenzeVorgaenger_nullbreitenzeichenInDerNummerUndKombinationszeichenAmKontextwort_gegenprobe() {
        String hinten = "BSNR " + B.substring(0, 6) + ZWSP + B.substring(6);
        List<Finding> funde = scanBsnr(hinten);
        assertEquals(1, funde.size(), "Anzahl Funde, Nullbreitenzeichen nach sechs Ziffern");
        assertTrue(B.substring(0, 6).equals(funde.get(0).originalValue()), "Fund nur ueber die ersten sechs Ziffern");
        assertEquals(0.40, funde.get(0).confidence(), "Laengenabweichung");
        assertTrue(QUELLE_LAENGE.equals(funde.get(0).source()), "Quelle");
        assertEquals(3, ungedeckteZiffern(hinten, scanStandard(hinten), 0, hinten.length()), "drei Ziffern bleiben");
        String vorn = "BSNR " + B.substring(0, 3) + ZWSP + B.substring(3);
        List<Finding> vornFunde = scanBsnr(vorn);
        assertEquals(1, vornFunde.size(), "Anzahl Funde, Nullbreitenzeichen nach drei Ziffern");
        assertTrue(B.substring(3).equals(vornFunde.get(0).originalValue()), "Fund nur ueber die letzten sechs Ziffern");
        assertEquals(3, ungedeckteZiffern(vorn, scanStandard(vorn), 0, vorn.length()), "drei Ziffern bleiben");
        for (String text : List.of("BSNR" + (char) 0x301 + " " + B, "BSNR" + (char) 0x301 + ": " + B,
                (char) 0x301 + "BSNR " + B)) {
            assertKeinFund(text);
            assertNummerImKlartext(text, B);
        }
        assertFund("BSNR ", B, "", SICHER, QUELLE);
    }

    // ------------------------------------------------------------------ G-B4 Zeichen in der Nummer

    /** Die drei unsichtbaren Zeichen der Grenze G-B4: Nullbreitenzeichen, weicher Trennstrich, Wortverbinder. */
    private static List<String> unsichtbareZeichen() {
        return List.of(String.valueOf(ZWSP), String.valueOf(KvTestHilfen.SHY),
                String.valueOf(KvTestHilfen.WORD_JOINER));
    }

    /** Die neun Ziffern der Nummer {@code B}, an den Stellen {@code schnitte} (aufsteigend) durch {@code z} geteilt. */
    private static String geteilt(String nummer, String z, int... schnitte) {
        StringBuilder sb = new StringBuilder();
        int von = 0;
        for (int schnitt : schnitte) {
            sb.append(nummer, von, schnitt).append(z);
            von = schnitt;
        }
        return sb.append(nummer.substring(von)).toString();
    }

    /**
     * G-B4, Teilung ohne Teil mit sechs Ziffern (4+5, 5+4, 3+3+3): Gruppentrenner sind ausschliesslich Leerzeichen,
     * U+00A0, U+2009, U+202F, Bindestrich, Punkt und Schraegstrich; JEDES andere Zeichen in der Nummer unterbricht sie.
     * Dieser Test nimmt als Beispiele drei unsichtbare Zeichen (Nullbreitenzeichen, weicher Trennstrich U+00AD,
     * Wortverbinder U+2060); sie sind Beispiele, keine vollstaendige Liste. Hat kein Teil sechs Ziffern, entsteht kein
     * Fund, und die GANZE Nummer bleibt im Klartext (alle neun Ziffern stehen im Ausgabetext). Der Test haelt diese
     * Luecke ehrlich fest; sie ist kein Soll. Gegenprobe: ohne das Zeichen ist die Nummer ein Fund (0,90).
     */
    @Test
    void bekannteGrenzeGB4_unsichtbaresZeichenOhneSechserTeil_keinFundGanzeNummerImKlartext_gegenprobe() {
        for (String z : unsichtbareZeichen()) {
            for (int[] schnitte : new int[][] {{4}, {5}, {3, 6}}) {
                String nummer = geteilt(B, z, schnitte);
                String text = "BSNR " + nummer;
                assertKeinFund(text);
                assertNummerImKlartext(text, nummer);
                List<Finding> funde = scanStandard(text);
                String ausgabe = PSEUDONYMIZER.pseudonymize(text, funde).text();
                assertTrue(ausgabe.contains(nummer), "Alle neun Ziffern stehen im Ausgabetext");
                assertTrue(ausgabe.equals(text), "Der Text bleibt unveraendert");
            }
        }
        assertFund("BSNR ", B, "", SICHER, QUELLE);
    }

    /**
     * G-B4 (a), Teilung 6+3 oder 3+6: Der Teil mit sechs Ziffern ist ein Fund (Laengenabweichung 0,40), die drei
     * uebrigen Ziffern bleiben im Klartext; gilt fuer alle drei unsichtbaren Zeichen.
     */
    @Test
    void bekannteGrenzeGB4_unsichtbaresZeichenBeiTeilung6Plus3_fundUeberSechsZiffernDreiBleiben() {
        for (String z : unsichtbareZeichen()) {
            String hinten = "BSNR " + geteilt(B, z, 6);
            List<Finding> funde = scanBsnr(hinten);
            assertEquals(1, funde.size(), "Anzahl Funde, Teilung 6+3");
            assertTrue(B.substring(0, 6).equals(funde.get(0).originalValue()), "Fund ueber die ersten sechs");
            assertEquals(0.40, funde.get(0).confidence(), "Laengenabweichung");
            assertEquals(3, ungedeckteZiffern(hinten, scanStandard(hinten), 0, hinten.length()), "drei bleiben");
            String vorn = "BSNR " + geteilt(B, z, 3);
            List<Finding> vornFunde = scanBsnr(vorn);
            assertEquals(1, vornFunde.size(), "Anzahl Funde, Teilung 3+6");
            assertTrue(B.substring(3).equals(vornFunde.get(0).originalValue()), "Fund ueber die letzten sechs");
            assertEquals(3, ungedeckteZiffern(vorn, scanStandard(vorn), 0, vorn.length()), "drei bleiben");
        }
    }

    /**
     * G-B4: Gruppen, die mit einem Zeichen ausserhalb der Gruppentrenner (Leerzeichen, U+00A0, U+2009, U+202F,
     * Bindestrich, Punkt, Schraegstrich) getrennt sind, ergeben keinen Fund, die ganze Nummer bleibt im Klartext:
     * Halbgeviertstrich U+2013, geschuetzter Bindestrich U+2011 und Unterstrich sind Beispiele (keine vollstaendige
     * Liste), hier bei den Teilungen 4+5, 5+4 und 3+3+3. Dasselbe gilt fuer LANR und IK (Vorgaenger, hier bei der
     * Teilung 3+3+3). Gegenprobe: dieselben Gruppen mit Bindestrich oder Leerzeichen sind ein Fund ueber die ganze
     * Gruppe. Folgefeature-Vorschlag: weitere Gruppentrenner und unsichtbare Zeichen in der Nummer.
     */
    @Test
    void bekannteGrenzeGB4_gruppenMitTrennerAusserhalbDerGruppentrenner_keinFundGanzeNummerImKlartext_gegenprobe() {
        List<String> fremd = List.of(String.valueOf(KvTestHilfen.HALBGEVIERT),
                String.valueOf(KvTestHilfen.GESCH_BINDESTRICH), "_");
        for (String z : fremd) {
            for (int[] schnitte : new int[][] {{4}, {5}, {3, 6}}) {
                String nummer = geteilt(B, z, schnitte);
                String text = "BSNR " + nummer;
                assertKeinFund(text);
                assertNummerImKlartext(text, nummer);
            }
            String lanr = "LANR " + geteilt(L, z, 3, 6);
            assertTrue(scanStandard(lanr).isEmpty(), "LANR: kein Fund");
            String ik = "IK " + geteilt(IK_GUELTIG, z, 3, 6);
            assertTrue(scanStandard(ik).isEmpty(), "IK: kein Fund");
        }
        for (String z : List.of("-", " ")) {
            for (int[] schnitte : new int[][] {{4}, {5}, {3, 6}}) {
                String wert = geteilt(B, z, schnitte);
                assertFund("BSNR ", wert, "", SICHER, QUELLE);
                assertNummerGedeckt("BSNR " + wert, wert);
            }
        }
    }

    /**
     * G-B4 allgemein: Jedes Zeichen ausserhalb der Gruppentrenner unterbricht die Nummer, auch fuer LANR und IK
     * (Nullbreitenzeichen bei der Teilung 4+5: kein Fund, alle neun Ziffern bleiben im Klartext) und fuer BSNR mit
     * Beispielen aus anderen Klassen (Strich-Variante U+2212, anderer Leerraum U+2007, ASCII-Zeichen Semikolon),
     * jeweils bei der Teilung 4+5. Gegenprobe mit den Gruppentrennern U+2009 und U+202F: Fund ueber die ganze
     * Gruppe. Die Zeichen entstehen zur Laufzeit; die Beispiele sind keine vollstaendige Liste.
     */
    @Test
    void bekannteGrenzeGB4_jedesZeichenAusserhalbDerGruppentrenner_auchLanrIk_gegenprobe() {
        String nullbreite = String.valueOf(ZWSP);
        String lanr = "LANR " + geteilt(L, nullbreite, 4);
        assertTrue(scanStandard(lanr).isEmpty(), "LANR: kein Fund");
        assertTrue(PSEUDONYMIZER.pseudonymize(lanr, scanStandard(lanr)).text().equals(lanr), "LANR im Klartext");
        String ik = "IK " + geteilt(IK_GUELTIG, nullbreite, 4);
        assertTrue(scanStandard(ik).isEmpty(), "IK: kein Fund");
        assertTrue(PSEUDONYMIZER.pseudonymize(ik, scanStandard(ik)).text().equals(ik), "IK im Klartext");
        for (String z : List.of(String.valueOf((char) 0x2212), String.valueOf((char) 0x2007), ";")) {
            String nummer = geteilt(B, z, 4);
            String text = "BSNR " + nummer;
            assertKeinFund(text);
            assertNummerImKlartext(text, nummer);
        }
        for (String z : List.of(String.valueOf((char) 0x2009), String.valueOf((char) 0x202F))) {
            String wert = geteilt(B, z, 4);
            assertFund("BSNR ", wert, "", SICHER, QUELLE);
            assertNummerGedeckt("BSNR " + wert, wert);
        }
    }

    // ------------------------------------------------------------------ Nummernbildung (A-6)

    /**
     * Die Bildung der BSNR-Testnummern ({@code SynthetischeKvNummern.bildeBsnr} und {@code bildeBsnrUnbekannt}, im
     * Fuzz-Teil 1h benutzt) erfuellt A-6: bekannter bzw. unbekannter Regionalcode (unabhaengige Referenz in
     * {@link BsnrTestHilfen}), Stellen 3 bis 7 gleich {@code 00000} oder Praefix {@code 00}, nach LANR und IK
     * ungueltig, vom Waechter akzeptiert; der BSNR-Erkenner liefert dafuer die erwartete Quelle.
     */
    @Test
    void nummernbildung_bildeBsnrUndBildeBsnrUnbekannt_erfuelltA6() {
        Random zufall = new Random(20261130L);
        for (int i = 0; i < 300; i++) {
            String bekannt = SynthetischeKvNummern.bildeBsnr(zufall);
            String unbekannt = SynthetischeKvNummern.bildeBsnrUnbekannt(zufall);
            assertTrue(BsnrTestHilfen.bekanntReferenz(bekannt), "bekannter Regionalcode");
            assertFalse(BsnrTestHilfen.bekanntReferenz(unbekannt), "unbekannter Regionalcode");
            for (String nummer : List.of(bekannt, unbekannt)) {
                assertFalse(SynthetischeKvNummern.lanrGueltigReferenz(nummer), "nach LANR ungueltig");
                assertFalse(SynthetischeKvNummern.ikGueltigReferenz(nummer), "nach IK ungueltig");
                BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR " + nummer);
            }
            assertFund("BSNR ", bekannt, "", SICHER, QUELLE);
            assertFund("BSNR ", unbekannt, "", REGIONALCODE_UNBEKANNT, QUELLE_REGIONALCODE);
        }
    }
}
