package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.AKUT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.HALBGEVIERT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.UZ_AK1;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.TRENNER;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.gruppiert;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.quelleNachNummerKvnr;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.scanKvnr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Bekannte Grenzen der KVNR mit Kontextwort (Feature kvnr-bsnr-erkennung, Task T-8, AK-36; Plan Kap. 11 und die
 * Nachtraege zu T-4 bis T-7). Jede Grenze hat mindestens einen Test {@code bekannteGrenze...}. Die Tests halten das
 * tatsaechliche Verhalten fest und sind ausdruecklich kein Soll fuer kuenftige Erweiterungen: Wird eine Grenze
 * geschlossen, ist der Test anzupassen. "Kein Fund" heisst hier: kein KVNR-Fund des {@code KvnrRecognizer}; die Nummer
 * bleibt dann im Klartext (die Tests auf Scannerebene pruefen das am pseudonymisierten Text).
 *
 * <p>Regel 12 des Plans: Eine Zeile mit der Gesamterwartung "kein Fund" enthaelt nie die gueltige, geschlossene,
 * grossgeschriebene Nummer (die Erkennung ohne Kontextwort, Teil 1b seit T-10, findet sie mit 0,60). Dort stehen die
 * Nummer mit falscher Pruefziffer, die gruppierte oder die kleingeschriebene Form. In T-11 sind alle "kein Fund"-Zeilen
 * der Tests G-K1 bis G-K17 und G-D2 darauf geprueft: keine davon enthaelt die gueltige, geschlossene, grossgeschriebene
 * Form mit Wortgrenzen (Ausnahmen sind nur die Tests {@code bekannteGrenzeOhneKontext...} unten, die genau diese Form
 * mit Wortgrenze, Negativkontext oder Nicht-ASCII-Ziffer pruefen). Zu jeder Grenze gehoert eine Positiv-Gegenprobe mit
 * derselben Nummer in der naechstliegenden erkannten Schreibweise, damit "kein Fund" nicht an der Nummer liegt.
 *
 * <p><b>Tabelle Grenze zu Test</b> (Kennungen wie in Plan Kap. 11 und im Javadoc von {@code KvNumberScanner}); ohne
 * Klassenangabe steht der Test in dieser Klasse:
 * <ul>
 * <li>G-K1: {@code bekannteGrenzeGK1_kvnrOhneKontextwort_keinFund_gegenprobeMitKontextwort} (Schreibweisen ausser der
 * geschlossenen, gueltigen, grossgeschriebenen Form) und, mit Teil 1b,
 * {@code bekannteGrenzeOhneKontext_kleinTrenner...} (dieselben Schreibweisen auch im Satz und allein, Gegenprobe: die
 * geschlossene Form ist ein Fund 0,60)</li>
 * <li>G-K2 (nur mit Teil 1b): {@code bekannteGrenzeOhneKontext_g_k2_fremdnummerMitPassenderPruefziffer_...} (der
 * bewusst eingegangene Fehlalarm), die Rate misst der Fehlalarm-Pruefstand von Teil 1b</li>
 * <li>G-K3: {@code bekannteGrenzeGK3_zweiBuchstabenNichtAsciiUndWoerterAusserhalbD2_keinFund}</li>
 * <li>G-K4: {@code bekannteGrenzeGK4_ziffernDirektAmWort_keinFund_gegenprobeMitLeerzeichen}; zwei Buchstaben an
 * gruppierten Ziffern: {@code KvnrKontextTest.bekannteGrenzeGK4_zweiBuchstabenVorGruppiertenZiffern_...}</li>
 * <li>G-K5: {@code bekannteGrenzeGK5_sperrschriftBuchstabeMitZiffernOhneTrenner_keinFund}</li>
 * <li>G-K6: {@code bekannteGrenzeGK6_kvnrZweiBuchstabenTrennerZiffern_fundUeberDieZiffern}</li>
 * <li>G-K7: {@code bekannteGrenzeGK7_kvNrVorReinerZiffernfolge_keinFund_gegenprobeVersichertennummer} und die Folge
 * {@code bekannteGrenzeGK7_folge_kvNrZiffernNachgestelltesKontextwort_keinFund}</li>
 * <li>G-K8: {@code bekannteGrenzeGK8_unsichtbaresZeichenZwischenBuchstabeUndZiffernOderInDerNummer}</li>
 * <li>G-K9: {@code bekannteGrenzeGK9_kontextwortDirektAmBuchstaben_keinFund_gegenprobeMitTrenner} und mit Teil 1b
 * {@code bekannteGrenzeOhneKontext_kontextwortDirektAmBuchstaben_keinFundAuchMitTeil1b_gegenprobeMitTrenner}</li>
 * <li>G-K10: {@code bekannteGrenzeGK10_trennerAusserhalbD1_hoechstensFundUeberDieZiffern}</li>
 * <li>G-K11: {@code bekannteGrenzeGK11_nachgestelltesKontextwortInDerNaechstenZeile_keinFund}</li>
 * <li>G-K12: {@code KvnrKontextTest.bekannteGrenze_kompositum_restgrenzen_keinFund_gegenprobeMitNormalemStrichFund}
 * </li>
 * <li>G-K13: {@code KvnrKontextTest.bekannteGrenze_kvnrInVierPunktGruppen_...}</li>
 * <li>G-K14: {@code TextScannerKvnrTest.bekannteGrenze_nummerUeberschneidetEinenAnderenFund_...} und
 * {@code bekannteGrenzeGK14_hintererTeilGruppierterNummerVomLokalenTeilEinerEmailGeschluckt}</li>
 * <li>G-K15: {@code bekannteGrenzeGK15_pfadOderUrlZwischenKontextwortUndNummer_keinFund} und
 * {@code bekannteGrenzeGK15_ursachen_segmentzaehlungUndSatzendeAmDateiendungspunkt}; mit Teil 1b
 * {@code bekannteGrenzeOhneKontext_g_k15_pfadOderUrlZwischenKontextwortUndNummer_...} (nur die geschlossene, gueltige,
 * grossgeschriebene Form wird gefunden)</li>
 * <li>G-K16: {@code bekannteGrenzeGK16_derselbeWertAnZweiterNichtErkannterStelle_bleibtStehen}</li>
 * <li>G-K17: {@code bekannteGrenzeGK17_exotischeUnsichtbareZeichenVorDemBuchstaben_keinFund} und die allgemeine
 * Fassung {@code bekannteGrenzeGK17_allgemein_kombinationszeichenUndFuellzeichenVorOderHinterDemBuchstaben_keinFund}
 * </li>
 * <li>G-K18 (Nachtrag 2 zu T-11): {@code bekannteGrenzeGK18_dateinameOhnePfad_...},
 * {@code bekannteGrenzeGK18_urlOhneSchemaMitNummerAlsPfadsegment_...} und
 * {@code bekannteGrenzeGK18_nummerPerSchraegstrichAnEinerIp_...} (gueltige, geschlossene Form ohne Kontextwort mit
 * Sperrzeichen an der Nummer und ohne umschliessenden Fund: kein KVNR-Fund, kein Fund deckt die Nummer; Gegenproben
 * mit Leerzeichen, mit Kontextwort, mit Schema und im Pfad)</li>
 * <li>G-D2: {@code bekannteGrenzeGD2_lanrOderIkKontextwortVorKvnrForm_nurDieZiffernGeschuetzt} (falsche Pruefziffer)
 * und mit Teil 1b {@code bekannteGrenzeOhneKontext_k1_lanrOderIkKontextwortVorGueltigerForm_...} (gueltige Form:
 * KVNR-Fund ueber Buchstabe und Ziffern)</li>
 * <li>Grenzen von Teil 1b (AK-12 bis AK-14, Lesart zur Nicht-ASCII-Ziffer):
 * {@code bekannteGrenzeOhneKontext_wortgrenzen_...}, {@code ..._negativkontext_...},
 * {@code ..._nichtAsciiZifferAlsNachbar_...}</li>
 * <li>Grenzen der Vorgaenger: {@code bekannteGrenzeVorgaenger_kombinationszeichenTeilgruppenUndUndVorKontextwort}
 * </li>
 * <li>G-B1 bis G-B3 und G-D1: Teil 2 (BSNR), nicht Teil dieses Tasks</li>
 * </ul>
 */
class KvnrGrenzenTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();
    private static final String BS = String.valueOf((char) 0x5C);
    private static final String G = KVNR_GUELTIG;
    private static final String U = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
    private static final String BUCHSTABE_G = G.substring(0, 1);
    private static final String ZIFFERN_G = G.substring(1);
    private static final String BUCHSTABE_U = U.substring(0, 1);
    private static final String ZIFFERN_U = U.substring(1);
    private static final double SICHER = 0.95;
    private static final double WEITER = 0.80;
    private static final double PZ = 0.50;
    private static final double FORM = 0.40;
    private static final int ARABISCH_NULL = 0x660;
    private static final int VOLLBREIT_NULL = 0xFF10;

    // ------------------------------------------------------------------ Hilfen

    /** Genau ein Fund {@code wert} hinter {@code vor}; Quelle aus der Nummer, Konfidenz wie angegeben. */
    private static void assertFund(String vor, String wert, String nach, double konfidenz) {
        List<Finding> funde = scanKvnr(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertTrue(quelleNachNummerKvnr(wert).equals(f.source()), "Quelle");
    }

    private static void assertKeinFund(String text) {
        assertTrue(scanKvnr(text).isEmpty(), "Es entstand ein KVNR-Fund");
    }

    /** Scan mit {@code TextScanner.standard()}: Waechter, Teilstring-Invariante, Sortierung, Wiederholung gleich. */
    private static List<Finding> scanStandard(String text) {
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
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

    /** Ersetzt alle Funde und prueft, dass die Rueckuebersetzung den Text zeichengleich wiederherstellt. */
    private static PseudonymizationResult ersetzeAlleMitRoundtrip(String text, List<Finding> funde) {
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        var zurueck = REIDENTIFIER.restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
        return ergebnis;
    }

    /** Die Ziffern als Zeichen eines anderen Ziffernblocks von Unicode (nur Nicht-ASCII-Ziffern). */
    private static String ziffernAls(String ziffern, int nullPunkt) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ziffern.length(); i++) {
            sb.append((char) (nullPunkt + (ziffern.charAt(i) - '0')));
        }
        return sb.toString();
    }

    /** Die gruppierte Form (3+3+3, Leerzeichen) der Nummer {@code BUCHSTABE_G + ZIFFERN_G}; 1b findet sie nicht. */
    private static String gruppierteForm() {
        return BUCHSTABE_G + " " + gruppiert(ZIFFERN_G, new int[] {3, 3, 3}, " ");
    }

    // ------------------------------------------------------------------ G-K1: ohne Kontextwort

    /**
     * G-K1: Ohne Kontextwort ist keine Nummer ein Fund: nicht mit falscher Pruefziffer, nicht klein geschrieben, nicht
     * gruppiert, nicht mit unsichtbarem Zeichen in der Nummer, auch nicht mit einem Wort wie {@code Rechnungsnummer}
     * davor. Die gueltige, geschlossene, grossgeschriebene Form steht hier nicht: Sie ist seit Teil 1b (T-10) auch ohne
     * Kontextwort ein Fund (0,60, Quelle {@code kvnr-ohne-kontext}); G-K1 gilt damit nur noch fuer die uebrigen
     * Schreibweisen (Tests {@code bekannteGrenzeOhneKontext...}). Gegenprobe: dieselbe Nummer mit {@code KVNR} davor.
     */
    @Test
    void bekannteGrenzeGK1_kvnrOhneKontextwort_keinFund_gegenprobeMitKontextwort() {
        for (String wort : List.of("Rechnungsnummer", "Artikelnummer", "Aktenzeichen", "Kennzeichen", "Ticket",
                "Bestellnummer", "Patient", "Beleg", "Kunde")) {
            assertKeinFund(wort + " " + U + " kam");
            assertKeinFund(wort + ": " + U);
        }
        assertKeinFund(U);
        assertKeinFund("Der Patient " + G.toLowerCase() + " kam gestern");
        assertKeinFund("Der Patient " + gruppierteForm() + " kam gestern");
        assertKeinFund("Der Patient " + BUCHSTABE_G + ZIFFERN_G.substring(0, 4) + ZWSP + ZIFFERN_G.substring(4));
        assertKeinFund("Der Patient " + U + ", " + U + " und " + U);
        // Gegenprobe: mit Kontextwort ein Fund in denselben Schreibweisen
        assertFund("KVNR ", U, "", PZ);
        assertFund("KVNR ", G.toLowerCase(), "", SICHER);
        assertFund("KVNR ", gruppierteForm(), "", SICHER);
        assertFund("Versichertennummer: ", G, " kam gestern", SICHER);
        // Seit Teil 1b: die geschlossene, gueltige, grossgeschriebene Form gehoert nicht mehr zur Grenze
        assertEquals(1, erkenne("Der Patient " + G + " kam gestern").size(),
                "1b: geschlossene gueltige Form ist ein Fund");
    }

    // ------------------------------------------------------------------ G-K3

    /**
     * G-K3: Zwei Buchstaben, Nicht-ASCII-Buchstaben, Nicht-ASCII-Ziffern und Woerter ausserhalb der Kontextwortliste
     * ergeben keinen Fund. Gegenprobe: dieselbe Nummer hinter {@code KVNR} bzw. {@code Versichertennummer}.
     */
    @Test
    void bekannteGrenzeGK3_zweiBuchstabenNichtAsciiUndWoerterAusserhalbD2_keinFund() {
        for (String zwei : List.of("AB", "ab", "Ab", BUCHSTABE_U + BUCHSTABE_U)) {
            assertKeinFund("KVNR " + zwei + ZIFFERN_U);
        }
        for (char buchstabe : new char[] {(char) 0xC4, (char) 0xFC, (char) 0x3A9, (char) 0x41F}) {
            assertKeinFund("KVNR " + buchstabe + ZIFFERN_U);
        }
        assertKeinFund("KVNR " + BUCHSTABE_U + ziffernAls(ZIFFERN_U, VOLLBREIT_NULL));
        assertKeinFund("KVNR " + BUCHSTABE_U + ziffernAls(ZIFFERN_U, ARABISCH_NULL));
        assertKeinFund("KVNR " + BUCHSTABE_U + ZIFFERN_U.substring(0, 4) + ziffernAls(ZIFFERN_U.substring(4, 5),
                VOLLBREIT_NULL) + ZIFFERN_U.substring(5));
        for (String wort : List.of("KVID", "VKNR", "KVNRX", "Versicherungsnummer", "Rentenversicherungsnummer",
                "Krankenversicherungs-Nr.", "Versicherungs-Nr.")) {
            assertKeinFund(wort + " " + U);
        }
        // Gegenprobe: dieselbe Nummer mit einem Wort aus D-2
        assertFund("KVNR ", U, "", PZ);
        assertFund("Versichertennummer ", U, "", PZ);
        assertFund("Krankenversicherten-Nr. ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K4

    /**
     * G-K4 (Ziffern direkt am Wort): Ziffern ohne Trenner direkt hinter dem Kontextwort oder hinter zwei Buchstaben
     * sind keine Nummer. Die Ziffernfolge ohne Buchstaben ist reine Ziffernfolge und haette hinter einem Leerzeichen
     * 0,40 (Gegenprobe). Zwei Buchstaben direkt an gruppierten Ziffern: siehe den Test in {@code KvnrKontextTest}.
     */
    @Test
    void bekannteGrenzeGK4_ziffernDirektAmWort_keinFund_gegenprobeMitLeerzeichen() {
        for (String wort : List.of("KVNR", "Versichertennummer", "eGK-Nummer", "Patienten-KVNR", "KVNR:AB", "KVNRAB",
                "KVNR AB")) {
            assertKeinFund(wort + ZIFFERN_U);
        }
        // Gegenprobe: mit Leerzeichen eine reine Ziffernfolge 0,40, mit Buchstabe die Nummer mit Stufe nach Pruefziffer
        assertFund("KVNR ", ZIFFERN_U, "", FORM);
        assertFund("Versichertennummer ", ZIFFERN_U, "", FORM);
        assertFund("KVNR ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K5

    /**
     * G-K5 (Rest): Folgt dem letzten Einzelbuchstaben der Sperrschrift unmittelbar eine Ziffer ({@code K V N R} und
     * Ziffern ohne Trenner) oder bei einer Sperrung mit Punkt und Leerzeichen oder mit mehreren Leerzeichen genau ein
     * Leerzeichen und eine reine Ziffernfolge, gilt der Buchstabe als Buchstabe einer Nummer: kein Kontextwort, kein
     * Fund. Gegenproben: {@code K V N R} mit genau einem Trenner (G-K5 geschlossen, 0,40) und die Sperrung mit
     * Punkt am Ende bzw. zwei Leerzeichen hinter dem letzten Buchstaben (0,40); mit Buchstabe der Nummer ein Fund.
     */
    @Test
    void bekannteGrenzeGK5_sperrschriftBuchstabeMitZiffernOhneTrenner_keinFund() {
        for (String sperr : List.of("K V N R", "K-V-N-R", "k v n r", "K.V.N.R")) {
            assertKeinFund(sperr + ZIFFERN_U);
        }
        for (String weit : List.of("K. V. N. R ", "K.  V.  N.  R ", "K  V  N  R ", "K   V   N   R ")) {
            assertKeinFund(weit + ZIFFERN_U);
        }
        // Gegenproben: dieselben Ziffern mit genau einem Trenner hinter der Sperrung, mit Punkt oder zwei Leerzeichen
        assertFund("K V N R ", ZIFFERN_U, "", FORM);
        assertFund("K-V-N-R ", ZIFFERN_U, "", FORM);
        assertFund("K. V. N. R. ", ZIFFERN_U, "", FORM);
        assertFund("K  V  N  R  ", ZIFFERN_U, "", FORM);
        // Gegenprobe mit Buchstabe: die Sperrung ist ein Kontextwort, die Nummer ein Fund
        assertFund("K V N R ", U, "", PZ);
        assertFund("K. V. N. R. ", U, "", PZ);
        assertFund("K.  V.  N.  R ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K6

    /** G-K6 (Folge, kein Fehler): {@code KVNR AB} mit Trenner und Ziffern ergibt 0,40 ueber die reinen Ziffern. */
    @Test
    void bekannteGrenzeGK6_kvnrZweiBuchstabenTrennerZiffern_fundUeberDieZiffern() {
        for (String trenner : TRENNER) {
            assertFund("KVNR AB" + trenner, ZIFFERN_U, "", FORM);
            assertFund("KVNR ab" + trenner, ZIFFERN_U, " Rest", FORM);
        }
        // Gegenprobe: ein Buchstabe ergibt die Nummer ueber alle Zeichen
        assertFund("KVNR ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K7

    /**
     * G-K7: Hinter {@code KV-Nr.}, {@code KV-Nummer} und {@code KV Nr.} ist eine reine Ziffernfolge kein Fund
     * (gewollt: Nummern der KV selbst). Gegenprobe: dieselbe Ziffernfolge hinter {@code Versichertennummer} ist ein
     * Fund 0,40; mit Buchstabe ist {@code KV-Nr.} ein Kontextwort.
     */
    @Test
    void bekannteGrenzeGK7_kvNrVorReinerZiffernfolge_keinFund_gegenprobeVersichertennummer() {
        for (String kw : List.of("KV-Nr.", "KV-Nummer", "KV Nr.", "kv-nr", "KV-Nr.:")) {
            assertKeinFund(kw + " " + ZIFFERN_U);
        }
        assertFund("Versichertennummer ", ZIFFERN_U, "", FORM);
        assertFund("KV-Nr. ", U, "", PZ);
        assertFund("KV-Nummer ", U, "", PZ);
        assertFund("KV Nr. ", U, "", PZ);
    }

    /**
     * G-K7, Folge (Nachtrag zu T-7): Ist der Kontext eines KV-Vorworts noch aktiv und steht hinter einer reinen
     * Ziffernfolge ein nachgestelltes vollwertiges Kontextwort ({@code KV-Nr. Ziffern (KVNR)}), bleibt die Ziffernfolge
     * ohne Fund, weil sie schon als Fuellwort verbraucht ist. Gegenprobe: ohne {@code KV-Nr.} davor ein Fund 0,40.
     */
    @Test
    void bekannteGrenzeGK7_folge_kvNrZiffernNachgestelltesKontextwort_keinFund() {
        assertKeinFund("KV-Nr. " + ZIFFERN_U + " (KVNR)");
        assertKeinFund("KV-Nr. " + ZIFFERN_U + ", KVNR");
        assertKeinFund("KV-Nr. Beleg " + ZIFFERN_U + " (KVNR)");
        assertKeinFund("KV-Nummer " + ZIFFERN_U + " (Versichertennummer)");
        // Gegenprobe: dieselbe Ziffernfolge ohne KV-Vorwort davor ist als nachgestellte Nummer ein Fund 0,40
        assertFund("Beleg ", ZIFFERN_U, " (KVNR)", FORM);
        assertFund("Beleg ", ZIFFERN_U, ", KVNR", FORM);
        assertFund("", ZIFFERN_U, " (KVNR)", FORM);
    }

    // ------------------------------------------------------------------ G-K8

    /**
     * G-K8: Ein unsichtbares Zeichen zwischen Buchstabe und Ziffern beendet die Kette (Fund hoechstens 0,40 ueber die
     * Ziffern, der Buchstabe bleibt stehen); ein Variantenwaehler am Buchstaben klebt die Ziffern an ihn, dann gibt es
     * keinen Fund. Ein unsichtbares Zeichen in der Nummer beendet die Kette ebenfalls (die Teile vor und hinter dem
     * Zeichen sind zu kurz oder ergeben eine abweichende Form). Gegenprobe: ohne das Zeichen ein Fund ueber alle
     * Zeichen.
     */
    @Test
    void bekannteGrenzeGK8_unsichtbaresZeichenZwischenBuchstabeUndZiffernOderInDerNummer() {
        for (char uz : UZ_AK1) {
            if (Character.getType(uz) == Character.FORMAT) {
                assertFund("KVNR " + BUCHSTABE_U + uz, ZIFFERN_U, "", FORM);
            } else {
                // Variantenwaehler sind Kombinationszeichen: Die Ziffern kleben dann am Buchstaben, kein Fund
                assertKeinFund("KVNR " + BUCHSTABE_U + uz + ZIFFERN_U);
            }
            // vier Ziffern, Zeichen, fuenf Ziffern: beide Teile sind kurz, kein Fund
            assertKeinFund("KVNR " + BUCHSTABE_U + ZIFFERN_U.substring(0, 4) + uz + ZIFFERN_U.substring(4));
            // sieben Ziffern, Zeichen, zwei Ziffern: Fund (abweichende Form) bis vor dem Zeichen, Rest bleibt stehen
            assertFund("KVNR ", BUCHSTABE_U + ZIFFERN_U.substring(0, 7), uz + ZIFFERN_U.substring(7), FORM);
        }
        assertFund("KVNR ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K9

    /**
     * G-K9 (Nachtrag zu T-4 Punkt 3, Nachtrag 3 zu T-7 (b)): Steht das Kontextwort direkt am Buchstaben der Nummer
     * ({@code KVNR} und Buchstabe ohne Trenner), ist es mit dem Buchstaben ein anderes Wort: kein Fund fuer die
     * geschlossene Form (hier mit falscher Pruefziffer, Regel 12) und die kleingeschriebene Form, mit einem
     * unsichtbaren Zeichen dazwischen ebenso. Die gruppierte Form liefert dort hoechstens 0,40 ueber die Ziffern, der
     * Buchstabe bleibt stehen. Auch eine Erkennung ohne Kontextwort (Teil 1b) schuetzt hier nicht, weil sie vor dem
     * Buchstaben eine Wortgrenze verlangt. Gegenprobe: mit Leerzeichen ein Fund ueber alle Zeichen.
     */
    @Test
    void bekannteGrenzeGK9_kontextwortDirektAmBuchstaben_keinFund_gegenprobeMitTrenner() {
        for (String kw : List.of("KVNR", "kvnr", "Versichertennummer", "eGK-Nummer", "Krankenversichertennummer",
                "Patienten-KVNR")) {
            assertKeinFund(kw + U);
            assertKeinFund(kw + G.toLowerCase());
            assertKeinFund(kw + BUCHSTABE_G + ZIFFERN_G.substring(0, 4) + " " + ZIFFERN_G.substring(4));
            for (char uz : UZ_AK1) {
                assertKeinFund(kw + uz + U);
                assertKeinFund(kw + uz + G.toLowerCase());
            }
        }
        // gruppierte Form mit unsichtbarem Zeichen hinter dem Kontextwort: nur die Ziffern, der Buchstabe bleibt
        assertFund("KVNR" + ZWSP + BUCHSTABE_G + " ", ZIFFERN_G, "", FORM);
        // Gegenprobe: mit Trenner dahinter dieselbe Nummer als Fund
        assertFund("KVNR ", U, "", PZ);
        assertFund("KVNR ", G.toLowerCase(), "", SICHER);
        assertFund("KVNR ", gruppierteForm(), "", SICHER);
        assertFund("KVNR " + ZWSP, U, "", PZ);
        assertFund("Versichertennummer: ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K10

    /**
     * G-K10 (Nachtrag zu T-4 Punkt 3): Ein Trenner ausserhalb der Gruppentrenner zwischen Buchstabe und Ziffern (Tab,
     * zwei Leerzeichen, Doppelpunkt mit Leerzeichen, Unterstrich, Komma, Halbgeviertstrich, Zeilenumbruch) laesst
     * hoechstens einen Fund 0,40 ueber die Ziffern; der Buchstabe bleibt stehen. Hinter {@code KV-Nr.} gibt es keinen
     * Fund. Ein Satzzeichen mit Satzende-Wirkung ({@code !}, {@code ?}) zwischen Buchstabe und Ziffern oder zwischen
     * Kontextwort samt Leerzeichen und Buchstabe ergibt gar keinen Fund (auch nicht 0,40 ueber die Ziffern).
     * Gegenprobe: jeder Gruppentrenner aus D-1 ergibt einen Fund ueber alle Zeichen, auch hinter {@code KV-Nr.}.
     */
    @Test
    void bekannteGrenzeGK10_trennerAusserhalbD1_hoechstensFundUeberDieZiffern() {
        for (String trenner : List.of("\t", "  ", ": ", "_", ",", String.valueOf(HALBGEVIERT), "\n", "\r\n", "\r")) {
            assertFund("KVNR " + BUCHSTABE_U + trenner, ZIFFERN_U, "", FORM);
            assertKeinFund("KV-Nr. " + BUCHSTABE_U + trenner + ZIFFERN_U);
        }
        // Satzende (! und ?): gar kein Fund, in keiner Stellung und auch hinter KV-Nr.
        for (String satzende : List.of("!", "?")) {
            for (String kontext : List.of("KVNR ", "KV-Nr. ")) {
                assertKeinFund(kontext + BUCHSTABE_U + satzende + ZIFFERN_U);
                assertKeinFund(kontext + BUCHSTABE_U + satzende + " " + ZIFFERN_U);
                assertKeinFund(kontext + satzende + " " + U);
            }
        }
        // Gegenprobe: ohne Satzende hinter Kontextwort samt Leerzeichen ein Fund ueber alle Zeichen
        assertFund("KVNR ", U, "", PZ);
        for (String trenner : TRENNER) {
            assertFund("KVNR ", BUCHSTABE_U + trenner + ZIFFERN_U, "", PZ);
            assertFund("KV-Nr. ", BUCHSTABE_U + trenner + ZIFFERN_U, "", PZ);
        }
    }

    // ------------------------------------------------------------------ G-K11

    /**
     * G-K11 (Nachtrag 3 zu T-5 (a)): Ein nachgestelltes Kontextwort, einfach oder als Kompositum, in der naechsten
     * Zeile hinter der Nummer ergibt keinen Fund ueber das nachgestellte Kontextwort: Ein Zeilenumbruch leert die
     * offenen Nummern (Regel der Vorgaenger, fuer alle Arten gleich). Gegenprobe: dasselbe Kontextwort in derselben
     * Zeile.
     */
    @Test
    void bekannteGrenzeGK11_nachgestelltesKontextwortInDerNaechstenZeile_keinFund() {
        for (String umbruch : List.of("\n", "\r\n", "\r", " \n")) {
            assertKeinFund(U + umbruch + "KVNR");
            assertKeinFund(U + umbruch + "Patienten-KVNR");
            assertKeinFund(U + umbruch + "(KVNR)");
            assertKeinFund(U + "," + umbruch + "KVNR");
        }
        assertKeinFund(G.toLowerCase() + "\nKVNR");
        assertKeinFund(gruppierteForm() + "\nPatienten-KVNR");
        // Gegenprobe: in derselben Zeile ein Fund
        assertFund("", U, " (KVNR)", PZ);
        assertFund("", U, " KVNR", PZ);
        assertFund("", U, ", KVNR", PZ);
        assertFund("", U, " (Patienten-KVNR)", PZ);
        assertFund("", G.toLowerCase(), " (KVNR)", WEITER);
        assertFund("", gruppierteForm(), " (Patienten-KVNR)", WEITER);
    }

    // ------------------------------------------------------------------ G-K14 (hinterer Teil, E-Mail)

    /**
     * G-K14, Nachtrag 5 zu T-6 Punkt 2: Der hintere Teil einer gruppierten Nummer wird vom lokalen Teil einer E-Mail
     * geschluckt. Stehen vor der E-Mail der Buchstabe und weniger als drei Ziffern, bleiben sie im Klartext (nur die
     * E-Mail ist ein Fund); mit drei Ziffern davor bleibt ein Restfund (Quelle {@code kvnr-rest}). Gegenprobe: ohne
     * die E-Mail ist die Nummer ein Fund ueber alle Zeichen.
     */
    @Test
    void bekannteGrenzeGK14_hintererTeilGruppierterNummerVomLokalenTeilEinerEmailGeschluckt() {
        String domain = "@example.invalid";
        String zwei = BUCHSTABE_U + ZIFFERN_U.substring(0, 2) + " " + ZIFFERN_U.substring(2, 4) + "-"
                + ZIFFERN_U.substring(4);
        String vor = "KVNR ";
        String text = vor + zwei + domain;
        List<Finding> funde = scanStandard(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.EMAIL, funde.get(0).type(), "Typ des einzigen Fundes");
        int emailStart = vor.length() + (BUCHSTABE_U + ZIFFERN_U.substring(0, 2) + " ").length();
        assertEquals(emailStart, funde.get(0).start(), "die E-Mail beginnt mit dem hinteren Teil der Nummer");
        PseudonymizationResult ergebnis = ersetzeAlleMitRoundtrip(text, funde);
        String klartextRest = BUCHSTABE_U + ZIFFERN_U.substring(0, 2);
        assertTrue(ergebnis.text().startsWith(vor + klartextRest + " [[EMAIL_"), "Buchstabe und zwei Ziffern bleiben");
        // mit drei Ziffern vor der E-Mail bleibt ein Restfund
        String drei = BUCHSTABE_U + ZIFFERN_U.substring(0, 3) + " " + ZIFFERN_U.substring(3, 6) + "."
                + ZIFFERN_U.substring(6);
        List<Finding> mitRest = scanStandard(vor + drei + domain);
        assertEquals(2, mitRest.size(), "Anzahl Funde mit drei Ziffern davor");
        assertEquals(FindingType.KVNR, mitRest.get(0).type(), "Typ des Restfundes");
        assertTrue("kvnr-rest".equals(mitRest.get(0).source()), "Quelle des Restfundes");
        assertEquals(FindingType.EMAIL, mitRest.get(1).type(), "Typ der E-Mail");
        // Gegenprobe: ohne E-Mail ein Fund ueber alle Zeichen
        assertFund(vor, zwei, "", PZ);
        assertEquals(1, scanStandard(vor + zwei).size(), "Anzahl Funde ohne E-Mail im Scan");
    }

    // ------------------------------------------------------------------ G-K15

    /** Pfade und URLs zwischen Kontextwort und Nummer, bei denen der Kontext erloschen ist (Nachtrag 2 zu T-7). */
    private static List<String> erloschenePfadePraefixe() {
        return List.of(
                "KVNR /a/b/c/d ",
                "KVNR /praxis/befund.pdf ",
                "KVNR https://example.invalid/x ",
                "KVNR https://example.invalid ",
                "KVNR C:" + BS + "Praxis" + BS + "Akten" + BS + "x" + BS + "a ",
                "Versichertennummer: /opt/praxis/akten/neu.txt ");
    }

    /**
     * G-K15 (Nachtrag 2 zu T-7 Punkt 1, Nachtrag 3 zu T-7 (a)): Steht zwischen Kontextwort und Nummer ein Pfad oder
     * eine URL, entsteht kein KVNR-Fund, in jeder Schreibweise der Nummer (geschlossen mit falscher Pruefziffer wegen
     * Regel 12, gruppiert, klein); auch kurze Pfade (vier Segmente), ein Pfad mit zwei Segmenten und Dateiendung und
     * eine URL mit Schema und mehr als zwei weiteren Segmenten gehoeren dazu (Fassung der Richtigstellung: Abgedeckt
     * bleibt eine URL aus Schema und hoechstens zwei weiteren Segmenten, Host-Labels und Pfadsegmente zaehlen einzeln,
     * ohne Punkt hinter einem Wort mit mehr als vier Buchstaben und ohne Query; jede URL mit einem laengeren Host-Label
     * vor einem Punkt, mit insgesamt mehr als drei Segmenten oder mit Query bleibt ohne Fund, siehe den zweiten Test).
     * Keine andere Erkennung faengt die Nummer in dieser Stellung auf: Im Scan bleibt sie im Klartext. Ohne Teil 1b
     * bleibt jede Form ohne Fund; mit Teil 1b bleiben die gruppierte und die kleingeschriebene Form sowie Nummern mit
     * falscher Pruefziffer ohne Fund, die geschlossene, gueltige, grossgeschriebene Form ist dagegen ein Fund 0,60
     * (Test {@code bekannteGrenzeOhneKontext_g_k15_...}). Gegenprobe: ein Pfad mit hoechstens drei Segmenten ohne
     * Endungspunkt laesst den Kontext bestehen (Fund mit 0,50 bzw. 0,80, weil das Kontextwort weiter entfernt ist).
     */
    @Test
    void bekannteGrenzeGK15_pfadOderUrlZwischenKontextwortUndNummer_keinFund() {
        List<String> nummern = List.of(U, G.toLowerCase(), gruppierteForm());
        for (String praefix : erloschenePfadePraefixe()) {
            for (String nummer : nummern) {
                assertKeinFund(praefix + nummer);
                String text = praefix + nummer + " Ende";
                List<Finding> funde = scanStandard(text);
                for (Finding f : funde) {
                    assertFalse(f.type() == FindingType.KVNR, "KVNR-Fund im Scan");
                }
                PseudonymizationResult ergebnis = ersetzeAlleMitRoundtrip(text, funde);
                assertTrue(ergebnis.text().contains(nummer), "Nummer bleibt im pseudonymisierten Text stehen");
            }
        }
        // Gegenprobe: Pfad mit drei Segmenten ohne Endungspunkt, dieselben Nummern als Fund
        assertFund("KVNR /a/b/c ", U, "", PZ);
        assertFund("KVNR /a/b/c ", G.toLowerCase(), "", WEITER);
        assertFund("KVNR /a/b/c ", gruppierteForm(), "", WEITER);
        assertFund("KVNR C:" + BS + "Praxis" + BS + "a ", U, "", PZ);
    }

    /**
     * G-K15, Ursachen (Nachtrag 2 zu T-7): Die Grenze folgt nicht aus der Laenge, sondern aus (a) der Zaehlung der
     * Pfad- und URL-Segmente als Fuellwoerter (ab dem vierten Segment ist der Kontext erloschen) und (b) dem
     * Satzende am Dateiendungspunkt hinter einem Wort mit mehr als vier Buchstaben. Jede Ursache wird einzeln belegt:
     * vier Segmente ohne Punkt: kein Fund; drei Segmente: Fund; Endung hinter einem kurzen Wort (hoechstens vier
     * Buchstaben): Fund; Endung hinter einem langen Wort: kein Fund; eine URL mit Schema ohne Punkt im Host und mit
     * hoechstens drei Segmenten laesst den Kontext bestehen (Abweichung der Praxis von der Formulierung "jede URL mit
     * Schema" im Plan: der Kontext erlischt erst durch die Segmentzahl oder den Punkt hinter einem langen Wort).
     * Bei URLs zaehlen das Schema und jedes Host-Label als Segment: Abgedeckt bleibt nur eine sehr kurze URL (Schema
     * und hoechstens zwei weitere Segmente, kein Punkt hinter einem langen Wort, keine Query); eine URL mit Schema,
     * Host und zwei Pfadsegmenten, eine URL mit Punkt im Host hinter einem kurzen Label und einem Pfadsegment und eine
     * URL mit Query bleiben ohne Fund. Abweichung vom Plan ("jede URL mit Domain bleibt ohne Fund"): Eine URL mit
     * nur zwei kurzen Host-Labels (drei Segmente) laesst den Kontext bestehen.
     */
    @Test
    void bekannteGrenzeGK15_ursachen_segmentzaehlungUndSatzendeAmDateiendungspunkt() {
        // (a) Zaehlung: vier Segmente erloschen, drei bestehen
        assertKeinFund("KVNR /a/b/c/d " + U);
        assertKeinFund("KVNR /ab/cd/ef/gh " + U);
        assertFund("KVNR /a/b/c ", U, "", PZ);
        assertFund("KVNR /ab/cd/ef ", U, "", PZ);
        // (b) Satzende: Endung hinter langem Wort beendet den Kontext, hinter kurzem Wort nicht
        assertKeinFund("KVNR /praxis/befund.pdf " + U);
        assertKeinFund("KVNR befund.pdf " + U);
        assertFund("KVNR /a/a.txt ", U, "", PZ);
        assertFund("KVNR /praxis/befund ", U, "", PZ);
        // URL: mit Punkt hinter langem Hostwort erloschen; ohne Punkt und mit drei Segmenten besteht der Kontext
        assertKeinFund("KVNR https://example.invalid/x " + U);
        assertFund("KVNR http://a/b ", U, "", PZ);
        // URL: Schema und jedes Host-Label zaehlen als Segment
        for (String nummer : List.of(U, G.toLowerCase(), gruppierteForm())) {
            // Punkt im Host hinter kurzem Label und ein Pfadsegment: vier Segmente, kein Fund
            assertKeinFund("KVNR http://a.b/c " + nummer);
            // Schema, Host und zwei Pfadsegmente: vier Segmente, kein Fund
            assertKeinFund("KVNR http://a/b/c " + nummer);
            // Query: das Fragezeichen gilt als Satzende, kein Fund
            assertKeinFund("KVNR http://a?b=c " + nummer);
            assertKeinFund("KVNR http://a/b?x=y " + nummer);
        }
        // Gegenprobe: Schema und Host ohne Punkt, Schema und zwei kurze Host-Labels (drei Segmente): Kontext besteht
        assertFund("KVNR http://a ", U, "", PZ);
        assertFund("KVNR http://a.b ", U, "", PZ);
        assertFund("KVNR http://ab.cd ", U, "", PZ);
    }

    // ------------------------------------------------------------------ G-K16

    /**
     * G-K16 (Nachtrag 2 zu T-6 Punkt 7): Der Pseudonymizer ersetzt nur gefundene Stellen. Steht derselbe Wert an einer
     * zweiten, nicht erkannten Stelle (hier das Kontextwort ohne Trenner am Buchstaben), bleibt er dort stehen; ein
     * Leak-Check ist nicht Teil des Features. Gegenprobe: mit Trenner werden beide Stellen erkannt und ersetzt.
     */
    @Test
    void bekannteGrenzeGK16_derselbeWertAnZweiterNichtErkannterStelle_bleibtStehen() {
        String text = "KVNR " + U + "; Kopie KVNR" + U;
        List<Finding> funde = scanStandard(text);
        assertEquals(1, funde.size(), "nur die erste Stelle ist ein Fund");
        PseudonymizationResult ergebnis = ersetzeAlleMitRoundtrip(text, funde);
        assertTrue(ergebnis.text().equals("KVNR [[KVNR_1]]; Kopie KVNR" + U), "zweite Stelle bleibt im Klartext");
        assertEquals(1, ergebnis.mapping().size(), "ein Platzhalter");
        // Gegenprobe: beide Stellen erkannt, derselbe Wert, derselbe Platzhalter, kein Klartext
        String beide = "KVNR " + U + "; Kopie KVNR " + U;
        List<Finding> zwei = scanStandard(beide);
        assertEquals(2, zwei.size(), "beide Stellen sind Funde");
        PseudonymizationResult ersetzt = ersetzeAlleMitRoundtrip(beide, zwei);
        assertTrue(ersetzt.text().equals("KVNR [[KVNR_1]]; Kopie KVNR [[KVNR_1]]"), "beide Stellen ersetzt");
        assertFalse(ersetzt.text().contains(ZIFFERN_U), "Ziffern im pseudonymisierten Text");
    }

    // ------------------------------------------------------------------ G-K17

    /**
     * G-K17 (security zu T-4, Hinweis 3): Zeichen, die man nicht sieht, aber nicht zur Menge der unsichtbaren Zeichen
     * des Scanners gehoeren (U+034F, U+180B, U+E0100, U+115F, U+3164, U+FFA0), vor dem Buchstaben ergeben keinen Fund.
     * Gegenprobe: ein Trenner dahinter ergibt einen Fund; ein unsichtbares Zeichen der Menge (U+200B) ist dagegen kein
     * Hindernis.
     */
    @Test
    void bekannteGrenzeGK17_exotischeUnsichtbareZeichenVorDemBuchstaben_keinFund() {
        for (int codepunkt : new int[] {0x34F, 0x180B, 0xE0100, 0x115F, 0x3164, 0xFFA0}) {
            String zeichen = new String(Character.toChars(codepunkt));
            assertKeinFund("KVNR " + zeichen + U);
            assertKeinFund("KVNR: " + zeichen + G.toLowerCase());
            assertKeinFund("KV-Nr. " + zeichen + U);
            // Gegenprobe: mit Trenner hinter dem Zeichen ein Fund ueber alle Zeichen der Nummer
            assertFund("KVNR " + zeichen + " ", U, "", PZ);
        }
        assertFund("KVNR " + ZWSP, U, "", PZ);
    }

    /**
     * G-K17 allgemeiner (security zu T-8): Jedes Kombinationszeichen (Kategorien Mn, Mc, Me) und die Fuellzeichen
     * U+115F, U+1160, U+3164 und U+FFA0 verhindern den Fund in Buchstabenform nicht nur vor dem Buchstaben, sondern
     * auch zwischen Buchstabe und Ziffern: Dann bleiben Buchstabe und alle neun Ziffern ohne Fund (gar kein Fund,
     * auch nicht 0,40 ueber die Ziffern). Gegenprobe: mit einem Leerzeichen hinter dem Zeichen entsteht dagegen ein
     * Fund 0,40 ueber die Ziffern (der Buchstabe bleibt stehen); ohne das Zeichen ein Fund ueber alle Zeichen.
     */
    @Test
    void bekannteGrenzeGK17_allgemein_kombinationszeichenUndFuellzeichenVorOderHinterDemBuchstaben_keinFund() {
        // Akut (Mn), Devanagari-Zeichen (Mc), umschliessender Kreis (Me), Hangul-Fuellzeichen und U+1160
        for (int codepunkt : new int[] {0x301, 0x903, 0x20DD, 0x1160, 0x115F, 0x3164, 0xFFA0}) {
            String zeichen = new String(Character.toChars(codepunkt));
            // vor dem Buchstaben
            assertKeinFund("KVNR " + zeichen + U);
            assertKeinFund("KVNR " + zeichen + G.toLowerCase());
            // zwischen Buchstabe und Ziffern: gar kein Fund
            assertKeinFund("KVNR " + BUCHSTABE_U + zeichen + ZIFFERN_U);
            assertKeinFund("KV-Nr. " + BUCHSTABE_U + zeichen + ZIFFERN_U);
            assertKeinFund("KVNR " + BUCHSTABE_G.toLowerCase() + zeichen + ZIFFERN_G);
            // Gegenprobe: mit Leerzeichen hinter dem Zeichen ein Fund 0,40 ueber die Ziffern
            assertFund("KVNR " + BUCHSTABE_U + zeichen + " ", ZIFFERN_U, "", FORM);
        }
        // Gegenprobe: ohne Zeichen ein Fund ueber alle Zeichen; ein Zeichen der Menge (U+200B) ist kein Hindernis vor
        // dem Buchstaben
        assertFund("KVNR ", U, "", PZ);
        assertFund("KVNR " + ZWSP, U, "", PZ);
    }

    // ------------------------------------------------------------------ G-D2

    /**
     * G-D2: Ein LANR- oder IK-Kontextwort vor einer KVNR-Form schuetzt nur die Ziffern (Fund LANR bzw. IK, der
     * Buchstabe bleibt im Klartext). Mit einem KVNR-Kontextwort davor umfasst der Fund Buchstabe und Ziffern und hat
     * den Typ KVNR (Kollisionsfall K-1). Die Nummer hat hier eine falsche Pruefziffer (Regel 12).
     */
    @Test
    void bekannteGrenzeGD2_lanrOderIkKontextwortVorKvnrForm_nurDieZiffernGeschuetzt() {
        String text = "LANR " + U;
        List<Finding> funde = scanStandard(text);
        assertEquals(1, funde.size(), "Anzahl Funde");
        assertEquals(FindingType.LANR, funde.get(0).type(), "Typ");
        assertEquals("LANR ".length() + 1, funde.get(0).start(), "der Fund beginnt hinter dem Buchstaben");
        PseudonymizationResult ergebnis = ersetzeAlleMitRoundtrip(text, funde);
        assertTrue(ergebnis.text().startsWith("LANR " + BUCHSTABE_U + "[["), "der Buchstabe bleibt im Klartext");
        List<Finding> ik = scanStandard("IK " + U);
        assertEquals(1, ik.size(), "Anzahl Funde IK");
        assertEquals(FindingType.IK, ik.get(0).type(), "Typ IK");
        assertEquals("IK ".length() + 1, ik.get(0).start(), "der IK-Fund beginnt hinter dem Buchstaben");
        // Gegenprobe: mit KVNR-Kontextwort davor ein KVNR-Fund ueber Buchstabe und Ziffern
        for (String vor : List.of("KVNR LANR ", "KVNR IK ", "KVNR: LANR: ")) {
            List<Finding> kvnr = scanStandard(vor + U);
            assertEquals(1, kvnr.size(), "Anzahl Funde mit KVNR-Kontextwort");
            assertEquals(FindingType.KVNR, kvnr.get(0).type(), "Typ KVNR");
            assertEquals(vor.length(), kvnr.get(0).start(), "der Fund beginnt beim Buchstaben");
            assertTrue(U.equals(kvnr.get(0).originalValue()), "Fund ueber Buchstabe und Ziffern");
        }
    }

    // ------------------------------------------------------------------ Grenzen von Teil 1b (KVNR ohne Kontextwort)

    private static final String QUELLE_1B = KvnrOhneKontextHilfen.QUELLE;
    private static final double KONFIDENZ_1B = 0.60;
    private static final int LAENGE_1B = 10;

    /** Auf Erkennerebene: Waechter, dann alle Funde des KVNR-Erkenners (Teil 1a und Teil 1b) mit Invarianten. */
    private static List<Finding> erkenne(String text) {
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        List<Finding> funde = new KvnrRecognizer().detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertEquals(FindingType.KVNR, f.type(), "Typ");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde;
    }

    /** Kein KVNR-Fund, weder vom Erkenner noch von {@code TextScanner.standard()} (andere Typen sind erlaubt). */
    private static void assertKeinKvnrFund(String text, String name) {
        assertTrue(erkenne(text).isEmpty(), name + ": Erkenner liefert einen Fund");
        for (Finding f : TextScanner.standard().scan(text)) {
            assertFalse(f.type() == FindingType.KVNR, name + ": standard() liefert einen KVNR-Fund");
        }
    }

    /** Genau ein Fund der Regel 1b ueber {@code nummer} ab {@code start}: zehn Zeichen, 0,60, Quelle 1b. */
    private static void assertFund1b(String text, int start, String nummer, String name) {
        List<Finding> funde = erkenne(text);
        assertEquals(1, funde.size(), name + ": Anzahl Funde");
        Finding f = funde.get(0);
        assertEquals(start, f.start(), name + ": Start");
        assertEquals(start + LAENGE_1B, f.end(), name + ": Ende");
        assertTrue(nummer.equals(f.originalValue()), name + ": Originalwert");
        assertEquals(KONFIDENZ_1B, f.confidence(), 1e-9, name + ": Konfidenz");
        assertTrue(QUELLE_1B.equals(f.source()), name + ": Quelle");
    }

    /**
     * Teil 1b, E-3 (AK-12): Eine KVNR ohne Kontextwort ist nur als geschlossene, grossgeschriebene Form mit gueltiger
     * Pruefziffer ein Fund. Kein Fund bleiben Kleinschreibung, jeder Trenner hinter dem Buchstaben, Gruppen, zwei
     * Buchstaben und eine falsche Pruefziffer; diese Formen bleiben im Klartext stehen. Gegenprobe: dieselbe Nummer
     * geschlossen, gross und gueltig ist ein Fund (Quelle {@code kvnr-ohne-kontext}, 0,60).
     */
    @Test
    void bekannteGrenzeOhneKontext_kleinTrennerGruppiertZweiBuchstabenUngueltig_keinFund_gegenprobeGeschlosseneForm() {
        List<String> nummern = new ArrayList<>(List.of(G.toLowerCase(Locale.ROOT), gruppierteForm(), U, "A" + G));
        for (String trenner : TRENNER) {
            nummern.add(BUCHSTABE_G + trenner + ZIFFERN_G);
        }
        for (String nummer : nummern) {
            assertKeinKvnrFund("Der Patient " + nummer + " kam", "Satz");
            assertKeinKvnrFund(nummer, "allein");
            List<Finding> scan = scanStandard("Der Patient " + nummer + " kam");
            assertTrue(ersetzeAlleMitRoundtrip("Der Patient " + nummer + " kam", scan).text().contains(nummer),
                    "die Nummer bleibt im pseudonymisierten Text stehen");
        }
        assertFund1b("Der Patient " + G + " kam", 12, G, "geschlossen, gross, gueltig");
        assertFund1b(G, 0, G, "allein");
    }

    /**
     * Teil 1b, AK-13: Direkt vor oder hinter den zehn Zeichen sperren ein Buchstabe, eine Ziffer, {@code _},
     * {@code -}, {@code /}, {@code \}, {@code @}, jeder Nicht-ASCII-Buchstabe, jedes Kombinationszeichen und ein Punkt
     * oder Komma mit Ziffer dahinter: kein Fund. Gegenprobe: Leerzeichen, Satzzeichen und Klammern lassen den Fund zu.
     */
    @Test
    void bekannteGrenzeOhneKontext_wortgrenzen_sperrzeichenVerhindernDenFund_gegenprobeMitSatzzeichen() {
        List<String> sperr = List.of("x", "7", "_", "-", "/", BS, "@", String.valueOf((char) 0xE4),
                String.valueOf((char) 0xC4), String.valueOf((char) 0x3A9), String.valueOf(AKUT));
        for (String s : sperr) {
            assertKeinKvnrFund("Wert " + s + G + " Ende", "davor");
            assertKeinKvnrFund("Wert " + G + s + " Ende", "dahinter");
        }
        assertKeinKvnrFund("Wert " + G + ".5 Ende", "Dezimalpunkt");
        assertKeinKvnrFund("Wert " + G + ",5 Ende", "Dezimalkomma");
        // Gegenprobe: Satzzeichen ohne Ziffer dahinter und Klammern, Anfuehrungszeichen, Doppelpunkt davor
        for (String nach : List.of(".", ",", ";", ")", ". Neu", ", und")) {
            assertFund1b("Wert (" + G + nach + " Ende", 6, G, "Satzzeichen dahinter");
        }
        for (String davor : List.of("(", "\"", ":", "\t", "\n", " ")) {
            assertFund1b("Wert" + davor + G + " Ende", 4 + davor.length(), G, "Grenzzeichen davor");
        }
        assertFund1b(G, 0, G, "Textanfang und Textende");
    }

    /**
     * Teil 1b, AK-14 (D-4): Steht unmittelbar vor der Nummer ein Wort der Negativliste (mit Leerzeichen, Tab,
     * Zeilenumbruch, geschuetztem Leerzeichen, {@code :} oder {@code =} dazwischen, gleich in welcher Schreibweise),
     * gibt es keinen Fund. Gegenprobe: ein Fuellwort dazwischen oder ein anderes Wort davor ergibt den Fund 0,60; mit
     * dem Kontextwort {@code KVNR} direkt davor den Fund 0,95 aus Teil 1a.
     */
    @Test
    void bekannteGrenzeOhneKontext_negativkontext_wortDerListeDirektDavor_keinFund_gegenprobeFuellwort() {
        List<String> trenner = List.of(" ", ": ", ":", "=", " = ", "\t", "\n", String.valueOf((char) 0xA0), "  ");
        for (String wort : KvnrOhneKontextHilfen.NEGATIVKONTEXT_SCHREIBWEISE) {
            for (String t : trenner) {
                assertKeinKvnrFund(wort + t + G, "Wort " + wort.length());
            }
            assertKeinKvnrFund(wort.toLowerCase(Locale.ROOT) + " " + G, "klein");
            assertKeinKvnrFund(wort.toUpperCase(Locale.ROOT) + ": " + G, "gross");
            assertKeinKvnrFund("Notiz " + wort + " " + G, "im Satz");
        }
        assertFund1b("Rechnungsnummer Muster " + G, "Rechnungsnummer Muster ".length(), G, "Fuellwort dazwischen");
        assertFund1b("Rechnung fuer Patient " + G, "Rechnung fuer Patient ".length(), G, "anderes Wort davor");
        List<Finding> mitKontext = erkenne("Rechnung KVNR " + G);
        assertEquals(1, mitKontext.size(), "Kontextwort direkt davor: ein Fund");
        assertEquals(SICHER, mitKontext.get(0).confidence(), 1e-9, "Fund aus Teil 1a: 0,95");
        assertTrue(KvnrRecognizer.SOURCE.equals(mitKontext.get(0).source()), "Quelle des Fundes aus Teil 1a");
    }

    /**
     * Teil 1b, Lesart zur Nicht-ASCII-Ziffer (Nachtrag zu T-10 Punkt 4, Javadoc von {@code KvnrOhneKontext}): Eine
     * Ziffer ausserhalb von ASCII (arabisch-indisch, Vollbreite, Devanagari) direkt vor oder hinter der Form sperrt wie
     * ein Buchstabe; die Gegenrichtung, eine echte KVNR direkt an einer solchen Ziffer, bleibt ohne Fund und im
     * Klartext. Gegenprobe: dieselbe Nummer mit Leerzeichen statt der Ziffer ist ein Fund.
     */
    @Test
    void bekannteGrenzeOhneKontext_nichtAsciiZifferAlsNachbar_sperrt_keinFund_gegenprobeMitLeerzeichen() {
        for (int codepunkt : new int[] {ARABISCH_NULL + 5, VOLLBREIT_NULL + 5, 0x966 + 2}) {
            String ziffer = new String(Character.toChars(codepunkt));
            assertKeinKvnrFund("Wert " + ziffer + G + " Ende", "davor");
            assertKeinKvnrFund("Wert " + G + ziffer + " Ende", "dahinter");
            String text = "Wert " + ziffer + G + " Ende";
            assertTrue(ersetzeAlleMitRoundtrip(text, scanStandard(text)).text().contains(G),
                    "die Nummer bleibt im pseudonymisierten Text stehen");
        }
        assertFund1b("Wert " + G + " Ende", 5, G, "Gegenprobe mit Leerzeichen");
    }

    /**
     * Teil 1b, G-K9: Auch eine Erkennung ohne Kontextwort schuetzt nicht, wenn das Kontextwort ohne Trenner direkt am
     * Buchstaben der gueltigen, geschlossenen Form haengt: Der Buchstabe hat keine Wortgrenze davor, es gibt keinen
     * Fund (weder aus Teil 1a noch aus Teil 1b), die Nummer bleibt im Klartext. Restrisiko, vom Nutzer zur Kenntnis
     * genommen (Nachtrag zu T-7). Gegenprobe: mit Trenner dahinter der Fund 0,95 aus Teil 1a.
     */
    @Test
    void bekannteGrenzeOhneKontext_kontextwortDirektAmBuchstaben_keinFundAuchMitTeil1b_gegenprobeMitTrenner() {
        for (String kw : List.of("KVNR", "kvnr", "Versichertennummer", "eGK-Nummer", "Krankenversichertennummer",
                "Patienten-KVNR")) {
            assertKeinKvnrFund(kw + G, "Kontextwort " + kw.length());
            String text = kw + G;
            assertTrue(ersetzeAlleMitRoundtrip(text, scanStandard(text)).text().contains(G),
                    "die Nummer bleibt im pseudonymisierten Text stehen");
        }
        List<Finding> mitTrenner = erkenne("KVNR " + G);
        assertEquals(1, mitTrenner.size(), "Gegenprobe: ein Fund");
        assertEquals(SICHER, mitTrenner.get(0).confidence(), 1e-9, "Gegenprobe: 0,95");
    }

    /**
     * Teil 1b schliesst G-K15 teilweise: Steht zwischen Kontextwort und Nummer ein Pfad oder eine URL, die den Kontext
     * beenden, ist die geschlossene, grossgeschriebene, gueltige Form ein Fund 0,60 aus Teil 1b (in der Stellung hinter
     * dem Pfad). Die gruppierte und die kleingeschriebene Form sowie Nummern mit falscher Pruefziffer bleiben dort
     * weiter ohne Fund (die Tests von G-K15). Gegenprobe: dieselben Formen ohne Pfad davor mit Kontextwort.
     */
    @Test
    void bekannteGrenzeOhneKontext_g_k15_pfadOderUrlZwischenKontextwortUndNummer_nurGeschlosseneGueltigeFormGefunden() {
        for (String praefix : erloschenePfadePraefixe()) {
            assertFund1b(praefix + G, praefix.length(), G, "geschlossene gueltige Form");
            String text = praefix + G + " Ende";
            List<Finding> scan = scanStandard(text);
            assertTrue(QUELLE_1B.equals(scan.stream().filter(f -> f.type() == FindingType.KVNR).findFirst()
                    .orElseThrow().source()), "Quelle des Fundes im Scan");
            assertFalse(ersetzeAlleMitRoundtrip(text, scan).text().contains(G), "Nummer ersetzt");
            assertKeinKvnrFund(praefix + gruppierteForm(), "gruppiert");
            assertKeinKvnrFund(praefix + G.toLowerCase(Locale.ROOT), "klein");
            assertKeinKvnrFund(praefix + U, "ungueltig");
        }
        assertEquals(WEITER, erkenne("KVNR /a/b/c " + G).get(0).confidence(), 1e-9,
                "Gegenprobe: Pfad mit drei Segmenten");
    }

    /**
     * Teil 1b, Kollisionsfall K-1 (G-D2, Plan Kap. 2, Risiko R-2): Ein LANR- oder IK-Kontextwort direkt vor einer
     * gueltigen, geschlossenen, grossgeschriebenen KVNR-Form ergibt jetzt einen KVNR-Fund (0,60) ueber Buchstabe und
     * Ziffern statt des LANR- oder IK-Fundes nur ueber die Ziffern: Die Position bleibt geschuetzt und wird um den
     * Buchstaben erweitert, die Stufe kann sinken (hier von sicher auf fraglich). Gegenprobe: mit falscher Pruefziffer
     * (G-D2) bleibt es beim Fund der Kontextwort-Art ueber die Ziffern.
     */
    @Test
    void bekannteGrenzeOhneKontext_k1_lanrOderIkKontextwortVorGueltigerForm_kvnrFundUmschliesstDenAltenFund() {
        String nummer = KVNR_NULL;
        for (String kw : List.of("LANR ", "IK ")) {
            String text = kw + nummer;
            List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
            assertEquals(1, alt.size(), "alter Stand: ein Fund");
            assertTrue(KvnrBsnrHilfen.istKv(alt.get(0)), "alter Stand: LANR oder IK");
            assertEquals(kw.length() + 1, alt.get(0).start(), "alter Stand: nur die Ziffern");
            List<Finding> neu = scanStandard(text);
            assertEquals(1, neu.size(), "neuer Stand: ein Fund");
            assertEquals(FindingType.KVNR, neu.get(0).type(), "neuer Stand: Typ KVNR");
            assertEquals(kw.length(), neu.get(0).start(), "neuer Stand: der Fund beginnt beim Buchstaben");
            assertEquals(text.length(), neu.get(0).end(), "neuer Stand: Ende wie der alte Fund");
            assertEquals(KONFIDENZ_1B, neu.get(0).confidence(), 1e-9, "neuer Stand: 0,60");
            assertTrue(neu.get(0).confidence() <= alt.get(0).confidence(), "Stufe sinkt oder bleibt gleich");
            assertFalse(ersetzeAlleMitRoundtrip(text, neu).text().contains(BUCHSTABE_NULL), "Buchstabe ersetzt");
        }
        List<Finding> ungueltig = scanStandard("LANR " + U);
        assertEquals(1, ungueltig.size(), "Gegenprobe: ein Fund");
        assertEquals(FindingType.LANR, ungueltig.get(0).type(), "Gegenprobe: Typ LANR, nur die Ziffern");
    }

    private static final String BUCHSTABE_NULL = KVNR_NULL.substring(0, 1);

    /**
     * Teil 1b, G-K2 (bewusst eingegangener Fehlalarm, E-3): Eine Fremdnummer aus Buchstabe und neun Ziffern mit
     * zufaellig passender Pruefziffer ist ein Fund der Stufe fraglich, wenn kein Wort der Negativliste direkt davor
     * steht (etwa jede zehnte solche Nummer); fraglich heisst: ersetzt wird nur bei Uebergabe durch den Menschen
     * (AK-20). Der Fehlalarm-Pruefstand misst die Rate. Gegenprobe: dieselbe Nummer hinter einem Wort der Negativliste.
     */
    @Test
    void bekannteGrenzeOhneKontext_g_k2_fremdnummerMitPassenderPruefziffer_fundFraglich_gegenprobeNegativwort() {
        for (String wort : List.of("Beleg", "Lieferschein", "Vorgang", "Referenz", "Rechnung")) {
            List<Finding> funde = erkenne(wort + " " + G);
            assertEquals(1, funde.size(), "ein Fund hinter einem Wort ausserhalb der Liste");
            assertEquals(ConfidenceLevel.FRAGLICH, funde.get(0).level(), "Stufe fraglich");
            assertTrue(QUELLE_1B.equals(funde.get(0).source()), "Quelle");
        }
        PseudonymizationResult ohneUebergabe = PSEUDONYMIZER.pseudonymize("Beleg " + G, List.of());
        assertTrue(("Beleg " + G).equals(ohneUebergabe.text()), "ohne Uebergabe bleibt der Text unveraendert");
        assertKeinKvnrFund("Rechnungsnummer " + G, "Gegenprobe Negativwort");
    }

    // ------------------------------------------------------------------ Vorgaenger

    /**
     * Die Grenzen der Vorgaenger gelten fuer die KVNR sinngemaess: ein Kombinationszeichen am Kontextwort macht es zu
     * einem anderen Wort (14-Reihe), Teilgruppen wie {@code K VNR} und {@code KVN R} sind keine Sperrschrift (14a),
     * und {@code und} vor dem nachgestellten Kontextwort laesst die Nummer ohne Fund (12R). Gegenproben: das
     * Kontextwort ohne Kombinationszeichen, {@code KV NR} (Vorwort mit Nachsatzwort) und das Komma.
     */
    @Test
    void bekannteGrenzeVorgaenger_kombinationszeichenTeilgruppenUndUndVorKontextwort() {
        assertKeinFund("KVNR" + AKUT + " " + U);
        assertKeinFund("K VNR " + U);
        assertKeinFund("KVN R " + U);
        assertKeinFund(U + ", und KVNR");
        assertFund("KVNR ", U, "", PZ);
        assertFund("KV NR ", U, "", PZ);
        assertFund("", U, ", KVNR", PZ);
    }

    // ------------------------------------------------------------------ G-K18

    /** Ob ein Fund irgendeiner Art die zehn Zeichen ab {@code start} ganz oder teilweise ueberdeckt. */
    private static boolean nummerAbgedeckt(List<Finding> funde, int start) {
        for (Finding f : funde) {
            if (f.start() < start + LAENGE_1B && f.end() > start) {
                return true;
            }
        }
        return false;
    }

    /**
     * G-K18: Kein KVNR-Fund (Erkenner und {@code standard()}), kein Fund irgendeiner Art deckt die Nummer, und die
     * Nummer steht nach dem Ersetzen aller Funde (Roundtrip zeichengleich) im Klartext im Text.
     */
    private static void assertNummerOhneJedeAbdeckung(String text, String name) {
        assertKeinKvnrFund(text, name);
        int start = text.indexOf(G);
        assertTrue(start >= 0, name + ": Nummer fehlt im Text");
        List<Finding> scan = scanStandard(text);
        assertFalse(nummerAbgedeckt(scan, start), name + ": ein Fund deckt die Nummer (Grenze geschlossen?)");
        assertTrue(ersetzeAlleMitRoundtrip(text, scan).text().contains(G), name + ": Nummer nicht im Klartext");
    }

    /** Gegenprobe zu G-K18: ein Fund im Scan deckt die Nummer ({@code type}: erwarteter Typ des deckenden Fundes). */
    private static void assertNummerGedecktVonFund(String text, FindingType type, String name) {
        List<Finding> scan = scanStandard(text);
        assertTrue(nummerAbgedeckt(scan, text.indexOf(G)), name + ": keine Abdeckung");
        assertTrue(scan.stream().anyMatch(f -> f.type() == type), name + ": erwarteter Fundtyp fehlt");
    }

    /**
     * G-K18 (Nachtrag 2 zu T-11, security T-11): Ein Dateiname ohne Pfad mit {@code _} oder {@code -} direkt vor oder
     * hinter der gueltigen, geschlossenen Nummer ohne Kontextwort ist kein Fund: AK-13 verlangt die Wortgrenze, und
     * ein Pfad oder eine URL mit Schema, die die Nummer umschliesst, gibt es nicht. Die Nummer bleibt im Klartext, und
     * kein anderer Erkenner deckt sie. Gegenproben: dieselbe Nummer mit Leerzeichen statt des Sperrzeichens ist ein
     * Fund 1b (0,60); mit {@code KVNR} davor ist es ein Fund aus Teil 1a auf der Nummer (0,80 bei Sperrzeichen vorn,
     * 0,95 bei Sperrzeichen hinten), also keine Luecke mehr.
     */
    @Test
    void bekannteGrenzeGK18_dateinameOhnePfad_keinFund_gegenprobeLeerzeichenUndKontextwort() {
        for (String sperr : List.of("_", "-")) {
            assertNummerOhneJedeAbdeckung("Datei bericht" + sperr + G + ".pdf Ende", "vorn " + sperr);
            assertNummerOhneJedeAbdeckung("Datei " + G + sperr + "bericht.pdf Ende", "hinten " + sperr);
            assertNummerOhneJedeAbdeckung("Datei " + sperr + G + " Ende", "nur Sperrzeichen vorn " + sperr);
            assertNummerOhneJedeAbdeckung("Datei " + G + sperr + " Ende", "nur Sperrzeichen hinten " + sperr);
            assertFund1b("Datei bericht " + G + ".pdf Ende", "Datei bericht ".length(), G, "Leerzeichen " + sperr);
            assertFund1b("Datei " + G + " bericht.pdf Ende", "Datei ".length(), G, "Leerzeichen hinten " + sperr);
            List<Finding> vorn = erkenne("KVNR bericht" + sperr + G + ".pdf Ende");
            assertEquals(1, vorn.size(), "Kontextwort davor, Nummer vorn: ein Fund");
            assertEquals(WEITER, vorn.get(0).confidence(), 1e-9, "Kontextwort davor, vorn: 0,80");
            assertTrue(G.equals(vorn.get(0).originalValue()), "Kontextwort davor, vorn: Fund ist die Nummer");
            List<Finding> hinten = erkenne("KVNR " + G + sperr + "bericht.pdf Ende");
            assertEquals(1, hinten.size(), "Kontextwort davor, Nummer hinten: ein Fund");
            assertEquals(SICHER, hinten.get(0).confidence(), 1e-9, "Kontextwort davor, hinten: 0,95");
            assertTrue(G.equals(hinten.get(0).originalValue()), "Kontextwort davor, hinten: Fund ist die Nummer");
        }
    }

    /**
     * G-K18: Eine URL ohne Schema mit der Nummer als Pfadsegment ist kein Fund, und kein Fund deckt die Nummer (der
     * URL-Erkenner verlangt ein Schema); auch ein Kontextwort davor hilft nicht (G-K15: die URL beendet den Kontext,
     * und die Nummer hat vorn und hinten {@code /}). Gegenproben: dieselbe URL mit Schema ist ein URL-Fund, der die
     * Nummer deckt, ebenso ein Pfad, der sie enthaelt (Unix-Pfad, Laufwerkspfad).
     */
    @Test
    void bekannteGrenzeGK18_urlOhneSchemaMitNummerAlsPfadsegment_keinFund_gegenprobeMitSchemaUndPfad() {
        String host = "portal.example.org/akte/";
        assertNummerOhneJedeAbdeckung("Siehe " + host + G + " Ende", "Segment am Ende");
        assertNummerOhneJedeAbdeckung("Siehe " + host + G + "/x Ende", "Segment in der Mitte");
        assertNummerOhneJedeAbdeckung("KVNR " + host + G + " Ende", "mit Kontextwort davor");
        assertNummerGedecktVonFund("Siehe https://" + host + G + " Ende", FindingType.URL, "URL mit Schema");
        assertNummerGedecktVonFund("Siehe /home/x/" + G + "_a.pdf Ende", FindingType.PFAD, "Unix-Pfad");
        assertNummerGedecktVonFund("Siehe C:" + BS + "x" + BS + G + " Ende", FindingType.PFAD, "Laufwerkspfad");
    }

    /**
     * G-K18: Eine Nummer, die per {@code /} direkt an einer IP-Adresse haengt (beide Reihenfolgen), ist kein KVNR-Fund,
     * und kein Fund deckt sie: Der IP-Fund umfasst nur die Adresse, die Nummer hat {@code /} als Sperrzeichen.
     * Gegenproben: mit Leerzeichen statt {@code /} ein Fund 1b (0,60) neben dem IP-Fund; mit {@code KVNR} davor ist
     * die Nummer ein Fund aus Teil 1a (0,80 hinter der IP, 0,95 vor ihr); Teil 1a legt dabei zusaetzlich einen Fund
     * der Stufe 0,40 (Form abweichend) ueber die Adresse, den {@code standard()} durch den IP-Fund ersetzt.
     */
    @Test
    void bekannteGrenzeGK18_nummerPerSchraegstrichAnEinerIp_keinFund_gegenprobeLeerzeichenUndKontextwort() {
        for (String text : List.of("Siehe " + IK_FORM_DOKU_IP + "/" + G + " Ende",
                "Siehe " + G + "/" + IK_FORM_DOKU_IP + " Ende")) {
            assertNummerOhneJedeAbdeckung(text, "Schraegstrich an der IP");
            assertTrue(scanStandard(text).stream().anyMatch(f -> f.type() == FindingType.IP), "IP-Fund fehlt");
        }
        assertFund1b("Siehe " + IK_FORM_DOKU_IP + " " + G + " Ende", ("Siehe " + IK_FORM_DOKU_IP + " ").length(), G,
                "Leerzeichen hinter der IP");
        assertFund1b("Siehe " + G + " " + IK_FORM_DOKU_IP + " Ende", "Siehe ".length(), G, "Leerzeichen vor der IP");
        List<Finding> hinterIp = erkenne("KVNR " + IK_FORM_DOKU_IP + "/" + G + " Ende");
        assertTrue(hinterIp.stream().anyMatch(f -> G.equals(f.originalValue()) && f.confidence() == WEITER),
                "Kontextwort davor, Nummer hinter der IP: Fund 0,80 auf der Nummer");
        List<Finding> vorIp = erkenne("KVNR " + G + "/" + IK_FORM_DOKU_IP + " Ende");
        assertTrue(vorIp.stream().anyMatch(f -> G.equals(f.originalValue()) && f.confidence() == SICHER),
                "Kontextwort davor, Nummer vor der IP: Fund 0,95 auf der Nummer");
    }
}
