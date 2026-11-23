package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.KVNR_ACHT_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_BUCHSTABE_C;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_ZIFFER_GEAENDERT;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_ZEHN_ZIFFERN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_GROSS;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_KLEIN;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.NEGATIVKONTEXT_SCHREIBWEISE;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Tests der KVNR ohne Kontextwort (Teil 1b, Feature kvnr-bsnr-erkennung, Task T-10: AK-11 bis AK-15, AK-20, E-3, D-4).
 * Alle Nummern stammen aus {@code SynthetischeKvNummern}; hier steht keine Nummer als Literal, Texte entstehen durch
 * Verkettung. Jeder Text laeuft durch den Waechter {@link KvnrSynthetischWaechter}. Meldungen nennen nur Namen und
 * Zahlen, nie Text oder Ziffern. Jede Gruppe von Negativfaellen hat eine Positiv-Gegenprobe im selben Test.
 *
 * <p>Der Fund der Regel 1b umfasst genau die zehn Zeichen, hat Konfidenz 0,60 (fraglich) und die Quelle
 * {@code kvnr-ohne-kontext}.
 */
class KvnrOhneKontextTest {

    private static final String G = KVNR_GUELTIG;
    private static final String QUELLE_1B = "kvnr-ohne-kontext";
    private static final double KONFIDENZ_1B = 0.60;
    private static final int LAENGE = 10;
    private static final char UE_KLEIN = (char) 0xFC;
    private static final char AKUT = (char) 0x0301;
    private static final char VOLLBREIT_FUENF = (char) 0xFF15;
    private static final char ARABISCH_FUENF = (char) 0x0665;
    private static final KvnrRecognizer R = new KvnrRecognizer();
    private static final TextScanner STANDARD = TextScanner.standard();

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> erkenne(String text) {
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        return R.detect(text);
    }

    private static int zahlKvnr(List<Finding> funde) {
        int n = 0;
        for (Finding f : funde) {
            n += f.type() == FindingType.KVNR ? 1 : 0;
        }
        return n;
    }

    /** Genau ein Fund der Regel 1b ueber {@code nummer} ab {@code start} (Erkenner und {@code standard()}). */
    private static void assertFund1b(String text, int start, String nummer, String name) {
        for (List<Finding> funde : List.of(erkenne(text), STANDARD.scan(text))) {
            assertEquals(1, funde.size(), name + ": Anzahl Funde");
            Finding f = funde.get(0);
            assertEquals(FindingType.KVNR, f.type(), name + ": Typ");
            assertEquals(start, f.start(), name + ": Start");
            assertEquals(start + LAENGE, f.end(), name + ": Ende");
            assertTrue(nummer.equals(f.originalValue()), name + ": Originalwert");
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), name + ": Teilstring");
            assertEquals(KONFIDENZ_1B, f.confidence(), 1e-9, name + ": Konfidenz");
            assertEquals(ConfidenceLevel.FRAGLICH, f.level(), name + ": Stufe");
            assertTrue(QUELLE_1B.equals(f.source()), name + ": Quelle");
        }
    }

    /** Kein KVNR-Fund, weder vom Erkenner noch von {@code standard()}. */
    private static void assertKeinKvnrFund(String text, String name) {
        assertTrue(erkenne(text).isEmpty(), name + ": Erkenner liefert einen Fund");
        assertEquals(0, zahlKvnr(STANDARD.scan(text)), name + ": standard() liefert einen KVNR-Fund");
    }

    // ------------------------------------------------------------------ AK-11

    @Test
    void ak11_nummerAllein_einFundZehnZeichenFraglich() {
        assertFund1b(G, 0, G, "allein");
    }

    @Test
    void ak11_nummerImSatz_einFundZehnZeichenFraglich() {
        String davor = "Der Patient ";
        assertFund1b(davor + G + " kam gestern", davor.length(), G, "im Satz");
    }

    @Test
    void ak11_weitereGueltigeNummern_einFund() {
        assertFund1b("Notiz " + KVNR_GUELTIG_ZWEI + " Ende", 6, KVNR_GUELTIG_ZWEI, "zweite Nummer");
        assertFund1b("Notiz " + KVNR_GUELTIG_BUCHSTABE_C + " Ende", 6, KVNR_GUELTIG_BUCHSTABE_C, "Buchstabe C");
    }

    @Test
    void ak11_kontextwortWeiterWegAlsDreiWoerter_bleibtFund060() {
        String davor = "KVNR der Frau Beispiel von hier aus ";
        assertFund1b(davor + G, davor.length(), G, "Kontextwort weit weg");
    }

    @Test
    void ak11_mitKontextwort_nurDerEineFund095() {
        for (String text : List.of("KVNR " + G, "KVNR: " + G, "Versichertennummer " + G)) {
            List<Finding> funde = erkenne(text);
            assertEquals(1, funde.size(), "Anzahl Funde mit Kontextwort");
            assertEquals(0.95, funde.get(0).confidence(), 1e-9, "Konfidenz mit Kontextwort");
            assertTrue("kvnr".equals(funde.get(0).source()), "Quelle mit Kontextwort");
        }
    }

    @Test
    void ak11_nachgestelltesKontextwort_keinZweiterFund() {
        List<Finding> klammer = erkenne(G + " (KVNR)");
        assertEquals(1, klammer.size(), "Anzahl Funde nachgestellt");
        assertEquals(0.80, klammer.get(0).confidence(), 1e-9, "Konfidenz nachgestellt");
        List<Finding> komma = erkenne(G + ", KVNR");
        assertEquals(1, komma.size(), "Anzahl Funde nachgestellt mit Komma");
        assertTrue("kvnr".equals(komma.get(0).source()), "Quelle nachgestellt mit Komma");
    }

    // ------------------------------------------------------------------ AK-12

    @Test
    void ak12_ungueltigePruefziffer_keinFund_gegenprobeGueltigerFund() {
        for (String ungueltig : List.of(KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, KVNR_UNGUELTIG_VERTAUSCHT,
                KVNR_UNGUELTIG_ZIFFER_GEAENDERT)) {
            assertKeinKvnrFund(ungueltig, "ungueltig allein");
            assertKeinKvnrFund("Der Patient " + ungueltig + " kam", "ungueltig im Satz");
        }
        assertFund1b("Der Patient " + G + " kam", 12, G, "Gegenprobe gueltig");
    }

    @Test
    void ak12_kleinbuchstabe_keinFund_gegenprobeGrossbuchstabe() {
        String klein = G.toLowerCase(Locale.ROOT);
        assertKeinKvnrFund(klein, "klein allein");
        assertKeinKvnrFund("Der Patient " + klein + " kam", "klein im Satz");
        assertFund1b("Der Patient " + G + " kam", 12, G, "Gegenprobe gross");
    }

    @Test
    void ak12_trennerInDerNummer_keinFund_gegenprobeGeschlossen() {
        String ziffern = G.substring(1);
        for (String trenner : KvnrTestHilfen.TRENNER) {
            for (int[] gruppen : KvnrTestHilfen.GRUPPEN) {
                String gruppiert = KvnrTestHilfen.gruppiert(ziffern, gruppen, trenner);
                assertKeinKvnrFund(G.charAt(0) + gruppiert, "Trenner in den Ziffern");
                assertKeinKvnrFund(G.charAt(0) + trenner + gruppiert, "Trenner nach dem Buchstaben");
            }
            assertKeinKvnrFund(G.charAt(0) + trenner + ziffern, "Trenner nur nach dem Buchstaben");
        }
        assertFund1b(G, 0, G, "Gegenprobe geschlossen");
    }

    @Test
    void ak12_zweiBuchstabenDavor_keinFund_gegenprobeEinBuchstabe() {
        assertKeinKvnrFund("X" + G, "zwei Buchstaben");
        assertKeinKvnrFund("Der Patient X" + G + " kam", "zwei Buchstaben im Satz");
        assertKeinKvnrFund("xy" + G, "zwei Buchstaben klein");
        assertFund1b("X " + G, 2, G, "Gegenprobe Wort davor getrennt");
    }

    @Test
    void ak12_achtOderElfZeichenStattZehn_keinFund_gegenprobeZehn() {
        assertKeinKvnrFund(KVNR_ACHT_ZIFFERN, "neun Zeichen");
        assertKeinKvnrFund("Der Patient " + KVNR_ACHT_ZIFFERN + " kam", "neun Zeichen im Satz");
        assertKeinKvnrFund(KVNR_ZEHN_ZIFFERN, "elf Zeichen");
        assertKeinKvnrFund("Der Patient " + KVNR_ZEHN_ZIFFERN + " kam", "elf Zeichen im Satz");
        assertKeinKvnrFund(G.substring(1), "nur Ziffern");
        assertFund1b("Der Patient " + G + " kam", 12, G, "Gegenprobe zehn Zeichen");
    }

    // ------------------------------------------------------------------ AK-13

    @Test
    void ak13_zeichenDavor_keinFund_gegenprobeErlaubteZeichen() {
        char[] verboten = {'x', 'X', AE_KLEIN, AE_GROSS, '5', '_', '-', '/', '\\', '@'};
        for (int i = 0; i < verboten.length; i++) {
            assertKeinKvnrFund(verboten[i] + G, "verbotenes Zeichen davor Nr. " + i);
            assertKeinKvnrFund("Der Patient " + verboten[i] + G + " kam", "verbotenes Zeichen davor im Satz Nr. " + i);
        }
        char[] erlaubt = {' ', '\t', '\n', '(', ')', '"', ':'};
        for (int i = 0; i < erlaubt.length; i++) {
            assertFund1b(erlaubt[i] + G, 1, G, "erlaubtes Zeichen davor Nr. " + i);
            assertFund1b("Der Patient" + erlaubt[i] + G, 12, G, "erlaubtes Zeichen davor im Satz Nr. " + i);
        }
        assertFund1b(G, 0, G, "Textanfang");
    }

    @Test
    void ak13_zeichenDahinter_keinFund_gegenprobeErlaubteZeichen() {
        char[] verboten = {'x', 'X', AE_KLEIN, AE_GROSS, '5', '_', '-', '/', '\\', '@'};
        for (int i = 0; i < verboten.length; i++) {
            assertKeinKvnrFund(G + verboten[i], "verbotenes Zeichen dahinter Nr. " + i);
            assertKeinKvnrFund("Der Patient " + G + verboten[i] + " kam", "verbotenes Zeichen dahinter im Satz " + i);
        }
        char[] erlaubt = {'.', ',', ';', ')', ' ', '\t', '\n', '"', ':', '!', '?', '('};
        for (int i = 0; i < erlaubt.length; i++) {
            assertFund1b(G + erlaubt[i], 0, G, "erlaubtes Zeichen dahinter Nr. " + i);
            assertFund1b("Der Patient " + G + erlaubt[i] + " kam", 12, G, "erlaubtes Zeichen dahinter im Satz " + i);
        }
        assertFund1b(G, 0, G, "Textende");
    }

    @Test
    void ak13_punktOderKommaMitZifferDahinter_keinFund_gegenprobeOhneZiffer() {
        for (char satz : new char[] {'.', ','}) {
            assertKeinKvnrFund(G + satz + '5', "Satzzeichen und Ziffer dahinter");
            assertKeinKvnrFund("Der Patient " + G + satz + "5 kam", "Satzzeichen und Ziffer dahinter im Satz");
            assertFund1b(G + satz + 'x', 0, G, "Satzzeichen und Buchstabe dahinter");
            assertFund1b(G + satz + ' ' + '5', 0, G, "Satzzeichen, Leerzeichen und Ziffer dahinter");
            assertFund1b(G + satz, 0, G, "Satzzeichen am Textende");
        }
    }

    @Test
    void ak13_kombinationszeichenUndNichtAsciiAnfang_keinFund() {
        assertKeinKvnrFund(AKUT + G, "Kombinationszeichen davor");
        assertKeinKvnrFund(G + AKUT, "Kombinationszeichen dahinter");
        assertKeinKvnrFund(AE_GROSS + G.substring(1), "Nicht-ASCII-Grossbuchstabe als Anfang");
        String mathematischesA = new String(Character.toChars(0x1D400));
        assertKeinKvnrFund(mathematischesA + G, "Buchstabe ausserhalb der BMP davor");
        assertKeinKvnrFund(G + mathematischesA, "Buchstabe ausserhalb der BMP dahinter");
        assertFund1b(G, 0, G, "Gegenprobe");
    }

    @Test
    void nichtAsciiZiffernAlsNachbar_sperren_gewaehlteLesart() {
        assertKeinKvnrFund(VOLLBREIT_FUENF + G, "Vollbreite Ziffer davor");
        assertKeinKvnrFund(G + VOLLBREIT_FUENF, "Vollbreite Ziffer dahinter");
        assertKeinKvnrFund(ARABISCH_FUENF + G, "arabisch-indische Ziffer davor");
        assertKeinKvnrFund(G + ARABISCH_FUENF, "arabisch-indische Ziffer dahinter");
        String imInnern = G.substring(0, 5) + VOLLBREIT_FUENF + G.substring(6);
        assertKeinKvnrFund(imInnern, "Nicht-ASCII-Ziffer in der Nummer");
        assertFund1b(G, 0, G, "Gegenprobe");
    }

    // ------------------------------------------------------------------ AK-14

    @Test
    void ak14_jedesWortAusD4_mitLeerzeichenDoppelpunktGleichheitszeichen_keinFund() {
        String[] trenner = {" ", ":", "=", ": ", " = ", " : "};
        int geprueft = 0;
        for (String wort : NEGATIVKONTEXT_SCHREIBWEISE) {
            for (String form : List.of(wort, wort.toLowerCase(Locale.ROOT), wort.toUpperCase(Locale.ROOT))) {
                for (String t : trenner) {
                    assertKeinKvnrFund(form + t + G, "Negativkontext " + wort.length() + " Trenner " + t.length());
                    assertKeinKvnrFund("Das ist die " + form + t + G, "Negativkontext im Satz");
                    geprueft++;
                }
            }
        }
        assertEquals(NEGATIVKONTEXT_SCHREIBWEISE.size() * 3 * trenner.length, geprueft, "Zahl der Faelle");
        assertEquals(14, NEGATIVKONTEXT_SCHREIBWEISE.size(), "Zahl der Woerter aus D-4");
    }

    @Test
    void ak14_negativkontextMitTabZeilenumbruchUndGeschuetztemLeerzeichen_keinFund() {
        for (String wort : NEGATIVKONTEXT_SCHREIBWEISE) {
            assertKeinKvnrFund(wort + "\t" + G, "Tab nach " + wort.length());
            assertKeinKvnrFund(wort + "\n" + G, "Zeilenumbruch nach " + wort.length());
            assertKeinKvnrFund(wort + NBSP + G, "geschuetztes Leerzeichen nach " + wort.length());
        }
        assertFund1b("Notiz\t" + G, 6, G, "Gegenprobe Tab");
    }

    @Test
    void ak14_negativkontextNurImDirektenVorlauf_gegenprobenMitFund() {
        String davor = "Rechnung f" + UE_KLEIN + "r Patient ";
        assertFund1b(davor + G, davor.length(), G, "Rechnung fuer Patient");
        for (String wort : NEGATIVKONTEXT_SCHREIBWEISE) {
            String text = wort + " Beispiel " + G;
            assertFund1b(text, text.length() - LAENGE, G, "Fuellwort zwischen Negativwort und Nummer");
        }
    }

    @Test
    void ak14_negativwortMitBuchstabeOderZifferDavor_istKeinNegativkontext() {
        String text = "Xartikelnummer: " + G;
        assertFund1b(text, text.length() - LAENGE, G, "Buchstabe vor dem Negativwort");
        String mitZiffer = "5Aktenzeichen " + G;
        assertFund1b(mitZiffer, mitZiffer.length() - LAENGE, G, "Ziffer vor dem Negativwort");
    }

    @Test
    void ak14_kontextwortNachNegativwort_nurDerEineFund095() {
        String text = "Rechnung KVNR " + G;
        List<Finding> funde = erkenne(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(0.95, funde.get(0).confidence(), 1e-9, "Konfidenz");
        assertTrue("kvnr".equals(funde.get(0).source()), "Quelle");
        assertEquals(1, STANDARD.scan(text).size(), "Anzahl Funde in standard()");
    }

    // ------------------------------------------------------------------ AK-15

    @Test
    void ak15_nummerInEinerUrl_genauEinFundUrl() {
        String text = "https://example.invalid/praxis?x=" + G;
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.URL, funde.get(0).type(), "Typ");
        assertEquals(0, zahlKvnr(funde), "KVNR-Funde");
    }

    @Test
    void ak15_nummerInEinemPfad_genauEinFundPfad() {
        String text = "C:\\Praxis\\" + G + "\\x.txt";
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.PFAD, funde.get(0).type(), "Typ");
        assertEquals(0, zahlKvnr(funde), "KVNR-Funde");
    }

    @Test
    void ak15_nummerAlsEmailTeil_genauEinFundEmail() {
        String text = G + "@example.invalid";
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.EMAIL, funde.get(0).type(), "Typ");
        assertEquals(0, zahlKvnr(funde), "KVNR-Funde");
    }

    @Test
    void ak15_dateinameOhnePfad_einKvnrFund_gegenprobe() {
        String text = G + ".pdf";
        assertFund1b(text, 0, G, "Dateiname ohne Pfad");
    }

    @Test
    void ak15_gegenprobe_nummerNebenUrlBleibtFund() {
        String text = "Siehe https://example.invalid/praxis und " + G;
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.URL, funde.get(0).type(), "Typ erster Fund");
        assertEquals(FindingType.KVNR, funde.get(1).type(), "Typ zweiter Fund");
    }

    // ------------------------------------------------------------------ Konflikt mit LANR und IK (K-1, R-2)

    @Test
    void k1_lanrOderIkKontextDavor_kvnrUmschliesstUndTraegtDieZehnZeichen() {
        for (String wort : List.of("LANR ", "IK ", "Arztnummer ")) {
            String text = wort + G;
            List<Finding> funde = STANDARD.scan(text);
            assertEquals(1, funde.size(), "Anzahl Funde nach " + wort.length());
            assertEquals(FindingType.KVNR, funde.get(0).type(), "Typ nach " + wort.length());
            assertEquals(wort.length(), funde.get(0).start(), "Start nach " + wort.length());
            assertEquals(wort.length() + LAENGE, funde.get(0).end(), "Ende nach " + wort.length());
            assertEquals(KONFIDENZ_1B, funde.get(0).confidence(), 1e-9, "Konfidenz nach " + wort.length());
        }
    }

    // ------------------------------------------------------------------ Mehrere Funde und Reihenfolge

    @Test
    void mehrereNummern_jedeEinFund_sortiertUndOhneUeberlappung() {
        String text = G + " und " + KVNR_GUELTIG_ZWEI + ", dann (" + KVNR_GUELTIG_BUCHSTABE_C + ") und " + G;
        List<Finding> funde = erkenne(text);
        assertEquals(4, funde.size(), "Anzahl Funde");
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            assertEquals(LAENGE, f.end() - f.start(), "Laenge");
            assertTrue(QUELLE_1B.equals(f.source()), "Quelle");
            vorigesEnde = f.end();
        }
    }

    @Test
    void mitKontextwortUndOhne_jeEinFund_ohneUeberschneidung() {
        String text = "KVNR " + G + " dann kam noch ein langer Text " + KVNR_GUELTIG_ZWEI;
        List<Finding> funde = erkenne(text);
        assertEquals(2, funde.size(), "Anzahl Funde");
        assertTrue("kvnr".equals(funde.get(0).source()), "Quelle erster Fund");
        assertTrue(QUELLE_1B.equals(funde.get(1).source()), "Quelle zweiter Fund");
        assertEquals(text.length() - LAENGE, funde.get(1).start(), "Start zweiter Fund");
    }

    // ------------------------------------------------------------------ AK-20 (Mensch entscheidet)

    @Test
    void ak20_nummerAllein_leereUebergabe_textUnveraendert() {
        for (String text : List.of(G, "Der Patient " + G + " kam gestern", "KVNR " + KVNR_UNGUELTIG_VERTAUSCHT)) {
            assertTrue(!erkenne(text).isEmpty(), "Der Scan liefert den Fund");
            PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, new ArrayList<>());
            assertTrue(text.equals(ergebnis.text()), "Text bei leerer Uebergabe unveraendert");
        }
    }

    @Test
    void ak20_nurWasUebergebenWird_wirdErsetztUndKommtZurueck() {
        String text = "Der Patient " + G + " kam gestern";
        List<Finding> funde = new TextScanner(List.of(R)).scan(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue("Der Patient [[KVNR_1]] kam gestern".equals(ergebnis.text()), "Platzhalter im Text");
        ReidentificationResult zurueck = new Reidentifier().restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    // ------------------------------------------------------------------ Laufzeit

    @Test
    void laufzeit_nummerMitLeerzeichenWiederholt_linearUndVollstaendig() {
        int anzahl = Math.ceilDiv(1_000_000, LAENGE + 1);
        String text = (G + " ").repeat(anzahl);
        R.detect((G + " ").repeat(1000)); // Aufwaermlauf, nicht gemessen
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeit der Erkennerebene");
        assertEquals(anzahl, ist, "Zahl der Funde");
    }

    @Test
    void laufzeit_langerLeerraumVorKandidaten_linear() {
        String text = (" ".repeat(1000) + G).repeat(1000);
        R.detect((" ".repeat(1000) + G).repeat(10)); // Aufwaermlauf, nicht gemessen
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeit mit langem Leerraum");
        assertEquals(1000, ist, "Zahl der Funde");
    }

    @Test
    void laufzeit_langeZiffernfolgenOhneKandidat_linearUndOhneFund() {
        String text = ("A" + "0".repeat(20)).repeat(40_000);
        R.detect(("A" + "0".repeat(20)).repeat(400)); // Aufwaermlauf, nicht gemessen
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeit mit langen Ziffernfolgen");
        assertEquals(0, ist, "Zahl der Funde");
    }
}
