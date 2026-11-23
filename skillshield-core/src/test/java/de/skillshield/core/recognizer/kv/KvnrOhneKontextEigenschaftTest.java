package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_BUCHSTABE_C;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.fremdKvnr;
import static de.skillshield.core.SynthetischeKvNummern.zufallsKvnr;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_KLEIN;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.NEGATIVKONTEXT_SCHREIBWEISE;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kvnrOhneKontextReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.Optionen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest der KVNR ohne Kontextwort (Teil 1b, Feature kvnr-bsnr-erkennung, Task T-10; AK-11 bis AK-14, AK-16).
 * Er erzeugt {@value #ANZAHL_TEXTE} Texte aus {@code new Random(SEED)} (fester Seed) und prueft je Text gegen die
 * unabhaengige Referenz {@link KvnrOhneKontextHilfen#kvnrOhneKontextReferenz} (eigenstaendige Umsetzung von Kap. 4.3):
 * Die Funde mit der Quelle {@code kvnr-ohne-kontext} sind genau die Treffer der Referenz ohne jene, die einen Fund mit
 * Kontextwort (Teil 1a) ueberlappen; die uebrigen Funde sind die des Scanners mit Kontextwort, unveraendert.
 *
 * <p>Die Texte mischen Nummern (gueltig, mit falscher Pruefziffer, mit gleichverteilter letzter Ziffer), Kontextwoerter
 * der KVNR und von LANR und IK, Woerter aus D-4, Grenzzeichen aller Arten (auch Nicht-ASCII und Ziffern), Trenner,
 * Kleinbuchstaben, Pfad- und URL-Anfaenge. Alle Nummern entstehen zur Laufzeit aus {@code SynthetischeKvNummern}.
 * Die Mindestzahlen zeigen, dass die Eigenschaft nicht ins Leere laeuft (Positiv-Gegenprobe); jede liegt bei hoechstens
 * 70 Prozent des gemessenen Istwerts.
 */
class KvnrOhneKontextEigenschaftTest {

    private static final long SEED = 20261124L;
    static final int ANZAHL_TEXTE = 10_000;
    private static final String QUELLE_1B = "kvnr-ohne-kontext";
    private static final int MAX_TOKEN = 6;
    private static final int ZUFALLSZIFFERN_MAX = 8;
    private static final KvnrRecognizer R = new KvnrRecognizer();

    /** Istwert 4797 Texte mit mindestens einem Fund der Regel 1b; Mindestzahl unter 70 Prozent. */
    private static final int MIN_TEXTE_MIT_FUND = 3350;
    /** Istwert 1555 Texte, in denen die Referenz einen Treffer hat, den ein Fund mit Kontextwort verdraengt. */
    private static final int MIN_TEXTE_VERDRAENGT = 1080;
    /** Istwert 2940 Texte, in denen die Referenz ohne Pruefziffer einen Treffer mehr hat. */
    private static final int MIN_TEXTE_OHNE_PRUEFZIFFER_ANDERS = 2050;
    /** Istwert 3586 Texte, in denen die Referenz ohne Wortgrenzen einen Treffer mehr hat. */
    private static final int MIN_TEXTE_OHNE_GRENZEN_ANDERS = 2500;
    /** Istwert 1027 Texte, in denen die Referenz ohne Negativkontext einen Treffer mehr hat. */
    private static final int MIN_TEXTE_OHNE_NEGATIV_ANDERS = 710;

    private static final String[] FUELLWOERTER = {"Der", "Patient", "kam", "gestern", "mit", "Frau", "Beispiel",
        "und", "Befund", "Datum", "Text", "Notiz", "von", "hier", "aus"};
    private static final String[] KONTEXT = {"KVNR ", "KVNR: ", "Versichertennummer ", "eGK-Nr. ", "KV-Nr. ",
        "K V N R ", "Patienten-KVNR ", "LANR ", "IK ", "Arztnummer ", "Krankenversichertennummer: "};
    private static final String[] NACHGESTELLT = {" (KVNR)", ", KVNR", " (Versichertennummer)", "; eGK-Nummer",
        " (LANR)", ", IK"};
    private static final String[] PRAEFIXE = {"https://example.invalid/praxis?x=", "C:\\Praxis\\", "mailto:",
        "www.example.invalid/", "/home/praxis/"};
    private static final String[] SUFFIXE = {"@example.invalid", ".pdf", "\\x.txt", "/index", ".example.invalid"};
    private static final String[] TRENNER = {" ", "-", ".", "/", "_", "\t", "\n"};
    private static final String[] GRENZEN = {"(", ")", "\"", ":", ";", ".", ",", "!", "?", "=", "#", "'"};

    // ------------------------------------------------------------------ Generator

    private static String grenzzeichen(Random z) {
        char[] sonder = {AE_KLEIN, (char) 0xC4, (char) 0x0301, (char) 0xFF15, (char) 0x0665, (char) 0x200B,
            (char) 0xA0, (char) 0xAD, '5', 'x', 'X', '_', '-', '/', '\\', '@'};
        return String.valueOf(sonder[z.nextInt(sonder.length)]);
    }

    private static String nummer(Random z) {
        return switch (z.nextInt(8)) {
            case 0, 1 -> fremdKvnr(z, true);
            case 2 -> fremdKvnr(z, false);
            case 3, 4 -> zufallsKvnr(z);
            case 5 -> KVNR_GUELTIG;
            case 6 -> KVNR_GUELTIG_ZWEI;
            default -> KVNR_GUELTIG_BUCHSTABE_C;
        };
    }

    /** Eine Nummer, gegebenenfalls verfremdet: klein, mit Trenner, abgeschnitten, verlaengert. */
    private static String nummerVerfremdet(Random z) {
        String n = nummer(z);
        return switch (z.nextInt(8)) {
            case 0 -> n.toLowerCase(Locale.ROOT);
            case 1 -> n.charAt(0) + TRENNER[z.nextInt(TRENNER.length)] + n.substring(1);
            case 2 -> n.substring(0, n.length() - 1);
            case 3 -> n + z.nextInt(10);
            case 4 -> (char) ('A' + z.nextInt(26)) + n;
            default -> n;
        };
    }

    private static String ziffernlauf(Random z) {
        StringBuilder sb = new StringBuilder("0000");
        int n = 1 + z.nextInt(ZUFALLSZIFFERN_MAX);
        for (int i = 0; i < n; i++) {
            sb.append(z.nextInt(10));
        }
        return sb.toString();
    }

    private static String token(Random z) {
        return switch (z.nextInt(14)) {
            case 0, 1, 2 -> nummer(z);
            case 3 -> nummerVerfremdet(z);
            case 4 -> NEGATIVKONTEXT_SCHREIBWEISE.get(z.nextInt(NEGATIVKONTEXT_SCHREIBWEISE.size()))
                    + new String[] {" ", ": ", "=", " = ", "\t", "\n"}[z.nextInt(6)] + nummer(z);
            case 5 -> KONTEXT[z.nextInt(KONTEXT.length)];
            case 6 -> nummer(z) + NACHGESTELLT[z.nextInt(NACHGESTELLT.length)];
            case 7 -> grenzzeichen(z) + nummer(z) + grenzzeichen(z);
            case 8 -> GRENZEN[z.nextInt(GRENZEN.length)] + nummer(z) + GRENZEN[z.nextInt(GRENZEN.length)];
            case 9 -> PRAEFIXE[z.nextInt(PRAEFIXE.length)] + nummer(z);
            case 10 -> nummer(z) + SUFFIXE[z.nextInt(SUFFIXE.length)];
            case 11 -> ziffernlauf(z);
            default -> FUELLWOERTER[z.nextInt(FUELLWOERTER.length)];
        };
    }

    private static String trennerZwischen(Random z) {
        return switch (z.nextInt(6)) {
            case 0 -> "";
            case 1 -> "\n";
            case 2 -> ", ";
            default -> " ";
        };
    }

    static String text(Random z) {
        StringBuilder sb = new StringBuilder();
        int n = 1 + z.nextInt(MAX_TOKEN);
        for (int i = 0; i < n; i++) {
            sb.append(token(z));
            sb.append(trennerZwischen(z));
        }
        return sb.toString();
    }

    static List<String> texte() {
        Random z = new Random(SEED);
        List<String> texte = new ArrayList<>(ANZAHL_TEXTE);
        for (int i = 0; i < ANZAHL_TEXTE; i++) {
            texte.add(text(z));
        }
        return texte;
    }

    // ------------------------------------------------------------------ Orakel

    private static boolean ueberlappt(Bereich b, List<Finding> funde) {
        for (Finding f : funde) {
            if (f.start() < b.ende() && f.end() > b.start()) {
                return true;
            }
        }
        return false;
    }

    private static List<Bereich> erwartet(String text, List<Finding> mitKontext, Optionen optionen) {
        List<Bereich> ergebnis = new ArrayList<>();
        for (Bereich b : kvnrOhneKontextReferenz(text, optionen)) {
            if (!ueberlappt(b, mitKontext)) {
                ergebnis.add(b);
            }
        }
        return ergebnis;
    }

    private static List<Bereich> bereiche1b(List<Finding> funde) {
        List<Bereich> ergebnis = new ArrayList<>();
        for (Finding f : funde) {
            if (QUELLE_1B.equals(f.source())) {
                ergebnis.add(new Bereich(f.start(), f.end()));
            }
        }
        return ergebnis;
    }

    private static List<Finding> ohne1b(List<Finding> funde) {
        List<Finding> ergebnis = new ArrayList<>();
        for (Finding f : funde) {
            if (!QUELLE_1B.equals(f.source())) {
                ergebnis.add(f);
            }
        }
        return ergebnis;
    }

    private static void pruefeFund1b(String text, Finding f) {
        assertEquals(FindingType.KVNR, f.type(), "Typ des Fundes ohne Kontextwort");
        assertEquals(10, f.end() - f.start(), "Laenge des Fundes ohne Kontextwort");
        assertEquals(0.60, f.confidence(), 1e-9, "Konfidenz des Fundes ohne Kontextwort");
        assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
    }

    // ------------------------------------------------------------------ Eigenschaften

    @Test
    void zehntausendTexte_fundeOhneKontextSindDieReferenzMinusUeberlappungMitKontextfunden() {
        int mitFund = 0;
        int verdraengt = 0;
        int ohnePruefziffer = 0;
        int ohneGrenzen = 0;
        int ohneNegativ = 0;
        int index = 0;
        for (String text : texte()) {
            List<Finding> funde = R.detect(text);
            List<Finding> mitKontext = ohne1b(funde);
            assertTrue(mitKontext.equals(KvNumberScanner.detect(KvArt.KVNR, text)),
                    "Funde mit Kontextwort veraendert, Text Nr. " + index);
            List<Bereich> soll = erwartet(text, mitKontext, Optionen.REFERENZ);
            assertTrue(soll.equals(bereiche1b(funde)), "Funde ohne Kontextwort weichen ab, Text Nr. " + index);
            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei, Text Nr. " + index);
                vorigesEnde = f.end();
                if (QUELLE_1B.equals(f.source())) {
                    pruefeFund1b(text, f);
                }
            }
            mitFund += soll.isEmpty() ? 0 : 1;
            verdraengt += kvnrOhneKontextReferenz(text).size() > soll.size() ? 1 : 0;
            ohnePruefziffer += erwartet(text, mitKontext, Optionen.REFERENZ.ohnePruefziffer()).size() > soll.size()
                    ? 1 : 0;
            ohneGrenzen += erwartet(text, mitKontext, Optionen.REFERENZ.ohneWortgrenzen()).size() > soll.size()
                    ? 1 : 0;
            ohneNegativ += erwartet(text, mitKontext, Optionen.REFERENZ.ohneNegativkontext()).size() > soll.size()
                    ? 1 : 0;
            index++;
        }
        assertTrue(mitFund >= MIN_TEXTE_MIT_FUND, "Texte mit Fund ohne Kontextwort: " + mitFund);
        assertTrue(verdraengt >= MIN_TEXTE_VERDRAENGT, "Texte mit verdraengtem Treffer: " + verdraengt);
        assertTrue(ohnePruefziffer >= MIN_TEXTE_OHNE_PRUEFZIFFER_ANDERS,
                "Texte, in denen die Pruefziffer wirkt: " + ohnePruefziffer);
        assertTrue(ohneGrenzen >= MIN_TEXTE_OHNE_GRENZEN_ANDERS, "Texte, in denen die Wortgrenzen wirken: "
                + ohneGrenzen);
        assertTrue(ohneNegativ >= MIN_TEXTE_OHNE_NEGATIV_ANDERS, "Texte, in denen der Negativkontext wirkt: "
                + ohneNegativ);
    }

    @Test
    void erzeugungIstDeterministisch() {
        assertTrue(texte().equals(texte()), "Der Generator liefert bei festem Seed nicht dieselben Texte");
        assertEquals(ANZAHL_TEXTE, texte().size(), "Zahl der Texte");
    }
}
