package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_ACHT_ZIFFERN;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_BEKANNT_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_MARKER;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_NULL;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_UNBEKANNT;
import static de.skillshield.core.SynthetischeKvNummern.BSNR_ZEHN_ZIFFERN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests des Waechters {@link BsnrSynthetischWaechter} (T-12) und der Verbindung der BSNR-Testnummern mit der
 * Regionalcode-Liste. Nicht synthetische Nummern entstehen zur Laufzeit durch Ableitung aus den Konstanten, nie als
 * Literal.
 */
class BsnrSynthetischWaechterTest {

    private static final char NBSP = (char) 0xA0;

    private static int code(String bsnr, int stelle) {
        return bsnr.charAt(stelle) - '0';
    }

    /** Die Nummer mit geaendertem Zeichen an der Stelle (Index ab 0), abgeleitet aus einer Konstante. */
    private static String mitZiffer(String nummer, int index, char ziffer) {
        return nummer.substring(0, index) + ziffer + nummer.substring(index + 1);
    }

    private static boolean wirft(String text) {
        return assertThrows(AssertionError.class, () -> BsnrSynthetischWaechter.assertBsnrSynthetisch(text)) != null;
    }

    // ------------------------------------------------------------------ Verbindung der Konstanten mit der Liste

    @Test
    void bekannteBsnrKonstanten_habenEinenRegionalcodeDerListe() {
        for (String bsnr : SynthetischeKvNummern.alleBsnrBekannt()) {
            assertTrue(BsnrRegionalcodes.istBekannt(code(bsnr, 0), code(bsnr, 1)), "Regionalcode nicht in der Liste");
            assertTrue(BsnrRegionalcodes.istBekannt(code(bsnr, 0), code(bsnr, 1), code(bsnr, 2), code(bsnr, 3)));
        }
    }

    @Test
    void unbekannteBsnrKonstanten_habenEinenRegionalcodeAusserhalbDerListe() {
        for (String bsnr : List.of(BSNR_UNBEKANNT, BSNR_NULL)) {
            assertFalse(BsnrRegionalcodes.istBekannt(code(bsnr, 0), code(bsnr, 1)), "Regionalcode ist in der Liste");
        }
        // die Laengenvektoren tragen einen bekannten Code, nur die Laenge weicht ab
        for (String bsnr : List.of(BSNR_ACHT_ZIFFERN, BSNR_ZEHN_ZIFFERN)) {
            assertTrue(BsnrRegionalcodes.istBekannt(code(bsnr, 0), code(bsnr, 1)));
        }
    }

    // ------------------------------------------------------------------ Waechter: Neunerfolgen

    @Test
    void alleBsnrKonstanten_bestehenDenWaechter() {
        for (String bsnr : SynthetischeKvNummern.alleBsnrNeunstellig()) {
            BsnrSynthetischWaechter.Befund b = BsnrSynthetischWaechter.pruefe("BSNR " + bsnr + ", Rueckruf");
            assertTrue(b.neunerfolgen() == 1, "Neunerfolgen " + b.neunerfolgen());
            assertTrue(b.verstoesse() == 0, "Verstoesse " + b.verstoesse());
            BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR " + bsnr + ", Rueckruf");
        }
        for (String bsnr : SynthetischeKvNummern.alleBsnrUngueltig()) {
            BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR " + bsnr);
        }
    }

    @Test
    void nichtSynthetischeNeunerfolge_schlaegtAn_alsReineZahlUndMitTrennern() {
        // Stellen 3 bis 7 nicht null, Praefix nicht 00: aus einer Konstante abgeleitet
        String fremd = mitZiffer(BSNR_BEKANNT, 4, '7');
        assertTrue(BsnrSynthetischWaechter.pruefe(fremd).verstoesse() == 1);
        assertTrue(wirft("BSNR " + fremd));
        // an jeder der fuenf Nullstellen einzeln
        for (int stelle = 2; stelle < 7; stelle++) {
            assertTrue(wirft("BSNR " + mitZiffer(BSNR_BEKANNT_ZWEI, stelle, '4')), "Stelle " + stelle);
        }
        // mit Trennern, Gruppen 2+5+2 und 3+3+3
        for (char trenner : new char[] {' ', NBSP, '-', '.', '/'}) {
            String gruppiert25 = fremd.substring(0, 2) + trenner + fremd.substring(2, 7) + trenner + fremd.substring(7);
            String gruppiert33 = fremd.substring(0, 3) + trenner + fremd.substring(3, 6) + trenner + fremd.substring(6);
            assertTrue(wirft("BSNR " + gruppiert25), "Gruppen 2+5+2");
            assertTrue(wirft("BSNR " + gruppiert33), "Gruppen 3+3+3");
        }
        // vollbreite Ziffern zaehlen als Ziffern
        StringBuilder breit = new StringBuilder();
        for (char c : fremd.toCharArray()) {
            breit.append((char) (0xFF10 + (c - '0')));
        }
        assertTrue(wirft("BSNR " + breit));
    }

    @Test
    void gegenprobe_synthetischeNeunerfolge_auchMitTrennern_schlaegtNichtAn() {
        for (String bsnr : SynthetischeKvNummern.alleBsnrNeunstellig()) {
            for (char trenner : new char[] {' ', NBSP, '-', '.', '/'}) {
                String gruppiert = bsnr.substring(0, 2) + trenner + bsnr.substring(2, 7) + trenner + bsnr.substring(7);
                BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR " + gruppiert);
                assertTrue(BsnrSynthetischWaechter.pruefe("BSNR " + gruppiert).neunerfolgen() == 1);
            }
        }
        // das Praefix 00 allein genuegt (wie bei LANR und IK), auch ohne Nullblock
        assertTrue(BSNR_UNBEKANNT.startsWith("00") && !BSNR_UNBEKANNT.startsWith("0".repeat(5), 2));
        BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR " + BSNR_UNBEKANNT);
    }

    @Test
    void andereLaengen_sindKeineNeunerfolge_undSchlagenNichtAn() {
        String acht = mitZiffer(BSNR_ACHT_ZIFFERN, 4, '7');
        String zehn = mitZiffer(BSNR_ZEHN_ZIFFERN, 4, '7');
        for (String text : List.of("BSNR " + acht, "BSNR " + zehn, "BSNR " + BSNR_ACHT_ZIFFERN,
                "BSNR " + BSNR_ZEHN_ZIFFERN)) {
            BsnrSynthetischWaechter.Befund b = BsnrSynthetischWaechter.pruefe(text);
            assertTrue(b.neunerfolgen() == 0 && b.verstoesse() == 0, "Laengenprobe wurde als Neunerfolge gezaehlt");
        }
        // leerer Text, nur Text ohne Ziffern
        BsnrSynthetischWaechter.assertBsnrSynthetisch("");
        BsnrSynthetischWaechter.assertBsnrSynthetisch("BSNR fehlt");
    }

    @Test
    void mehrereNummernImText_werdenEinzelnGezaehlt() {
        String fremd = mitZiffer(BSNR_BEKANNT, 3, '5');
        String text = "BSNR " + BSNR_BEKANNT + ", " + BSNR_MARKER + " und " + fremd;
        BsnrSynthetischWaechter.Befund b = BsnrSynthetischWaechter.pruefe(text);
        assertTrue(b.neunerfolgen() == 3, "Neunerfolgen " + b.neunerfolgen());
        assertTrue(b.verstoesse() == 1, "Verstoesse " + b.verstoesse());
    }

    @Test
    void meldungNenntKeineZiffernUndKeinenText() {
        String fremd = mitZiffer(BSNR_BEKANNT, 4, '7');
        AssertionError e = assertThrows(AssertionError.class,
                () -> BsnrSynthetischWaechter.assertBsnrSynthetisch("Praxisname " + fremd));
        String meldung = String.valueOf(e.getMessage());
        assertFalse(meldung.contains(fremd), "Die Meldung nennt die Nummer");
        assertFalse(meldung.contains(fremd.substring(0, 6)), "Die Meldung nennt Ziffern");
        assertFalse(meldung.contains("Praxisname"), "Die Meldung nennt Text");
    }

    // ------------------------------------------------------------------ Waechter: ungewollte KVNR-Form (Punkt 5)

    /** Die Grossbuchstaben, die vor den neun Ziffern eine nach der Referenz gueltige KVNR-Form ergeben. */
    private static List<Character> gueltigeBuchstaben(String neun) {
        List<Character> treffer = new ArrayList<>();
        for (char c = 'A'; c <= 'Z'; c++) {
            if (KvnrBsnrHilfen.kvnrPruefziffer(c, neun.substring(0, 8)) == neun.charAt(8) - '0') {
                treffer.add(c);
            }
        }
        return treffer;
    }

    @Test
    void bsnrDirektHinterEinzelnemGrossbuchstaben_mitPassenderPruefziffer_schlaegtAn() {
        int gefunden = 0;
        for (String bsnr : SynthetischeKvNummern.alleBsnrNeunstellig()) {
            for (char buchstabe : gueltigeBuchstaben(bsnr)) {
                gefunden++;
                String text = "Eintrag " + buchstabe + bsnr + " Ende";
                assertTrue(BsnrSynthetischWaechter.pruefe(text).kvnrFormen() == 1);
                assertTrue(wirft(text), "Waechter schlaegt bei gueltiger KVNR-Form nicht an");
                // beabsichtigt: dieselbe Form mit genannter Zahl erlaubter Formen ist kein Verstoss
                BsnrSynthetischWaechter.assertBsnrSynthetisch(text, 1);
                // Gegenprobe: mit Leerzeichen dazwischen, als Kleinbuchstabe oder in einem Wort: keine Form
                BsnrSynthetischWaechter.assertBsnrSynthetisch("Eintrag " + buchstabe + " " + bsnr + " Ende");
                BsnrSynthetischWaechter.assertBsnrSynthetisch("Eintrag " + Character.toLowerCase(buchstabe) + bsnr);
                BsnrSynthetischWaechter.assertBsnrSynthetisch("Eintrag X" + buchstabe + bsnr + " Ende");
            }
        }
        // Istwert siehe Lauf; ohne einen Treffer liefe die Pruefung ins Leere
        assertTrue(gefunden >= 3, "Passende Buchstaben insgesamt: " + gefunden);
    }

    @Test
    void bsnrDirektHinterEinzelnemGrossbuchstaben_ohnePassendePruefziffer_schlaegtNichtAn() {
        int geprueft = 0;
        for (String bsnr : SynthetischeKvNummern.alleBsnrNeunstellig()) {
            List<Character> passend = gueltigeBuchstaben(bsnr);
            for (char buchstabe = 'A'; buchstabe <= 'Z'; buchstabe++) {
                if (!passend.contains(buchstabe)) {
                    geprueft++;
                    BsnrSynthetischWaechter.assertBsnrSynthetisch("Eintrag " + buchstabe + bsnr + " Ende");
                }
            }
        }
        // Istwert 5 Nummern mal etwa 24 Buchstaben; Mindestzahl unter 70 Prozent davon
        assertTrue(geprueft >= 80, "Geprueft: " + geprueft);
    }

    @Test
    void echteKvnr_alsBeabsichtigterFund_istMitErlaubterZahlKeinVerstoss_ohneZahlSchon() {
        String kvnr = SynthetischeKvNummern.KVNR_GUELTIG;
        String text = "KVNR " + kvnr + ", BSNR " + BSNR_BEKANNT;
        BsnrSynthetischWaechter.Befund b = BsnrSynthetischWaechter.pruefe(text);
        assertTrue(b.kvnrFormen() == 1 && b.verstoesse() == 0 && b.neunerfolgen() == 2);
        assertTrue(wirft(text));
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text, 1);
        // die falsche Zahl erlaubter Formen ist ebenfalls ein Fehler
        assertThrows(AssertionError.class, () -> BsnrSynthetischWaechter.assertBsnrSynthetisch(text, 2));
    }
}
