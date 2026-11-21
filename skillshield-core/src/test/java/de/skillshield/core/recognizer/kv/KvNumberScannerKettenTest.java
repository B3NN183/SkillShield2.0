package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Kettenregeln des {@link KvNumberScanner} (Kap. 3.2 des Architektur-Plans): genau neun Ziffern, Praefix aus ganzen
 * Bloecken, 6 bis 12 Ziffern, weniger als 6, mehr als 12, Doppeltrenner, Trenner am Ende, geschuetztes Leerzeichen.
 * Die Kettenregeln sind unabhaengig von der Nummernart; geprueft wird meist mit LANR, einige Tests nehmen die Art IK,
 * damit der Durchlauf mit beiden Konfigurationen belegt ist. Alle Nummern stammen aus {@code SynthetischeKvNummern}
 * (Texte per Verkettung, auch Trennerformen); als Literal stehen hier nur kurze Ziffernbloecke unter neun Ziffern.
 *
 * <p>Regel aus T-1: Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder IK-Verfahren gueltig
 * ist und nicht mit {@code 00} beginnt. Jede hier gebildete Kette mit genau neun Ziffern geht aus einer Konstante mit
 * Praefix {@code 00} hervor; Ketten ohne Praefix {@code 00} haben weniger oder mehr als neun Ziffern, oder sie
 * enden in der Praefixsuche vor einem neunten Ziffernwert (Begruendung je Test).
 */
class KvNumberScannerKettenTest {

    private static final double LAENGE = 0.40;
    private static final String Q_LAENGE = "lanr-laenge-abweichend";
    private static final char NBSP = (char) 0xA0;

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> scan(KvArt art, String text) {
        List<Finding> funde = KvNumberScanner.detect(art, text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring-Invariante");
            assertEquals(art.type(), f.type());
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static void assertFund(KvArt art, String vor, String wert, String nach, double konfidenz,
            ConfidenceLevel stufe, String quelle) {
        List<Finding> funde = scan(art, vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertEquals(wert, f.originalValue(), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertEquals(stufe, f.level(), "Stufe");
        assertEquals(quelle, f.source(), "Quelle");
    }

    private static void lanrSicher(String vor, String wert, String nach) {
        assertFund(KvArt.LANR, vor, wert, nach, 0.95, ConfidenceLevel.SICHER, "lanr");
    }

    private static void lanrLaenge(String vor, String wert, String nach) {
        assertFund(KvArt.LANR, vor, wert, nach, LAENGE, ConfidenceLevel.FRAGLICH, Q_LAENGE);
    }

    private static void lanrKeinFund(String text) {
        assertEquals(List.of(), scan(KvArt.LANR, text));
    }

    /** Neun Ziffern in 3-3-3 mit dem Trenner {@code t}. */
    private static String gruppiert(String neun, String t) {
        return neun.substring(0, 3) + t + neun.substring(3, 6) + t + neun.substring(6);
    }

    // ------------------------------------------------------------------ genau neun Ziffern

    @Test
    void kette_genauNeunZiffernOhneTrenner_einSegmentMitPruefziffer() {
        lanrSicher("LANR ", LANR_GUELTIG, "");
    }

    @Test
    void kette_neunZiffernMitLeerzeichen_ganzeFolgeInklusiveTrenner() {
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, " "), "");
    }

    @Test
    void kette_neunZiffernMitGeschuetztemLeerzeichenNbsp() {
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, String.valueOf(NBSP)), "");
    }

    @Test
    void kette_neunZiffernMitBindestrich() {
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, "-"), "");
    }

    @Test
    void kette_neunZiffernMitPunkt() {
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, "."), "");
    }

    @Test
    void kette_neunZiffernMitSchraegstrich() {
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, "/"), "");
    }

    @Test
    void kette_neunZiffernMitGemischtenTrennern() {
        String gemischt = LANR_GUELTIG.substring(0, 3) + " " + LANR_GUELTIG.substring(3, 6) + NBSP
                + LANR_GUELTIG.substring(6);
        lanrSicher("LANR ", gemischt, "");
    }

    @Test
    void kette_neunZiffernMitEinzelnemTrennerInJederLuecke() {
        // Trenner an jeder der acht Stellen zwischen den Ziffern; jeder Block hat dann eine Ziffer
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LANR_GUELTIG.length(); i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(LANR_GUELTIG.charAt(i));
        }
        lanrSicher("LANR ", sb.toString(), "");
    }

    @Test
    void kette_pruefzifferWirdUeberNeunZiffernOhneTrennerBerechnet() {
        // Summe 79, erwartet 1, Stelle 7 ist 2: ungueltig, auch mit Trennern
        assertFund(KvArt.LANR, "LANR ", gruppiert(LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "."), "", 0.50,
                ConfidenceLevel.FRAGLICH, "lanr-pruefziffer-ungueltig");
    }

    // ------------------------------------------------------------------ Praefix aus ganzen Bloecken

    @Test
    void kette_neunerPraefixMitFolgendemBlock_segmentNurDieNeunZiffern() {
        lanrSicher("LANR ", LANR_GUELTIG, " 2025");
        lanrSicher("LANR ", LANR_GUELTIG, "-2025");
        lanrSicher("LANR ", LANR_GUELTIG, "/2025");
        lanrSicher("LANR ", LANR_GUELTIG, ".2025");
    }

    @Test
    void kette_neunerPraefixAusMehrerenBloecken_restWirdAlsNeueKetteGelesen() {
        // Kette aus 3 + 6 Ziffern, dahinter ein weiterer Block: Segment = die ersten neun Ziffern samt Trenner
        lanrSicher("LANR ", LANR_GUELTIG.substring(0, 3) + " " + LANR_GUELTIG.substring(3), " 12");
    }

    @Test
    void kette_zweiNeunerSegmenteHintereinander_nurDasErsteIstFund_bisZurAufzaehlung() {
        // Kette aus 11 + 1 + 11 Zeichen: die ersten neun Ziffern sind das Segment; der Rest wird als neue Kette gelesen
        // und ist ein Segment, aber nicht mehr im Kontext. Bis zur Aufzaehlung im Folgetask gibt es nur den ersten
        // Fund.
        String erste = gruppiert(LANR_GUELTIG, " ");
        String zweite = gruppiert(LANR_GUELTIG_ZWEI, " ");
        lanrSicher("LANR ", erste, " " + zweite);
        // Jede Kette fuer sich ist ein Segment: mit einem Kontextwort davor entstehen zwei Funde
        List<Finding> funde = scan(KvArt.LANR, "LANR " + erste + " LANR " + zweite);
        assertEquals(2, funde.size());
        assertEquals(erste, funde.get(0).originalValue());
        assertEquals(zweite, funde.get(1).originalValue());
    }

    @Test
    void kette_neunerPraefixHatVorrangVorLaengerenPraefixen() {
        // 6 + 3 = 9 Ziffern, danach noch 3: das Neuner-Praefix gewinnt gegen das 12er-Praefix
        lanrSicher("LANR ", LANR_GUELTIG.substring(0, 6) + " " + LANR_GUELTIG.substring(6), " 123");
    }

    // ------------------------------------------------------------------ 6 bis 12 Ziffern

    @Test
    void kette_sechsZiffern_fraglichLaenge() {
        lanrLaenge("LANR ", LAENGE_6, "");
    }

    @Test
    void kette_siebenZiffern_fraglichLaenge() {
        lanrLaenge("LANR ", LAENGE_7, "");
    }

    @Test
    void kette_achtZiffern_fraglichLaenge() {
        lanrLaenge("LANR ", LAENGE_8, "");
    }

    @Test
    void kette_zehnZiffern_fraglichLaenge() {
        lanrLaenge("LANR ", LAENGE_10, "");
    }

    @Test
    void kette_zwoelfZiffern_fraglichLaenge() {
        lanrLaenge("LANR ", LAENGE_12, "");
    }

    @Test
    void kette_laengenabweichungMitTrennern_ganzeKetteIstDasSegment() {
        // Telefonprobe mit 10 Ziffern (4 + 3 + 3): weitergehend als AK-11 auch mit Trennern geschuetzt
        lanrLaenge("LANR ", TELEFON_PROBE, "");
        // 6 + 2 + 2 = 10 Ziffern, Praefixsummen 6, 8, 10: nie neun
        lanrLaenge("LANR ", LAENGE_6 + " 78 90", "");
    }

    @Test
    void kette_laengstesPraefixMit6Bis12_beiZuLangerKetteNurDerFittendePraefix() {
        // 6 + 7 = 13 Ziffern: das laengste Praefix mit 6 bis 12 Ziffern ist der erste Block (6); der Rest ist kein Fund
        lanrLaenge("LANR ", LAENGE_6, " " + LAENGE_7);
    }

    @Test
    void kette_laengstesPraefixMit6Bis12_mehrereBloecke() {
        // 6 + 2 + 2 + 2 = 12 Ziffern, danach noch ein Block (14): Praefixsummen 6, 8, 10, 12 (nie neun), laengstes = 12
        lanrLaenge("LANR ", LAENGE_6 + " 78 90 12", " 34");
    }

    @Test
    void kette_kleineEinzelBloeckeBisAchtZiffern_fraglichLaenge() {
        // 1 + 1 + ... = 8 Ziffern in acht Bloecken (nie neun): Segment mit Laengenabweichung
        lanrLaenge("LANR ", "1 2 3 4 5 6 7 8", "");
    }

    // ------------------------------------------------------------------ weniger als 6 und mehr als 12 Ziffern

    @Test
    void kette_wenigerAls6Ziffern_keinSegment() {
        lanrKeinFund("LANR " + LAENGE_4);
        lanrKeinFund("LANR " + LAENGE_5);
        lanrKeinFund("LANR 12 34");
    }

    @Test
    void kette_mehrAls12Ziffern_keinSegment() {
        lanrKeinFund("LANR " + LAENGE_13);
    }

    @Test
    void kette_mehrAls12ZiffernAusBloeckenOhneFittendesPraefix_keinSegment() {
        // 5 + 8 = 13 Ziffern: der erste Block hat nur 5, der zweite fuehrt ueber 12 hinaus
        lanrKeinFund("LANR " + LAENGE_5 + " " + LAENGE_8);
    }

    @Test
    void kette_erstesBlockUeber12Ziffern_keinSegment_undDieFolgekettenBleibenOhneKontext() {
        lanrKeinFund("LANR " + LAENGE_13 + " " + LANR_GUELTIG);
    }

    // ------------------------------------------------------------------ Doppeltrenner und Trenner am Ende

    @Test
    void kette_doppeltrennerNachNeunZiffern_segmentOhneTrenner() {
        lanrSicher("LANR ", LANR_GUELTIG, "  2025");
        lanrSicher("LANR ", LANR_GUELTIG, "--2025");
        lanrSicher("LANR ", LANR_GUELTIG, " -2025");
    }

    @Test
    void kette_doppeltrennerInDerKette_beendetDieKette() {
        // 3 + 3 Ziffern, Doppeltrenner, 3 Ziffern: die Kette endet nach sechs Ziffern (Laengenabweichung)
        String sechs = LANR_GUELTIG.substring(0, 3) + " " + LANR_GUELTIG.substring(3, 6);
        lanrLaenge("LANR ", sechs, "  " + LANR_GUELTIG.substring(6));
        lanrLaenge("LANR ", sechs, "--" + LANR_GUELTIG.substring(6));
    }

    @Test
    void kette_doppeltrennerNachDemErstenBlock_keinSegment() {
        // nur drei Ziffern vor dem Doppeltrenner: weniger als 6, kein Segment
        lanrKeinFund("LANR " + LANR_GUELTIG.substring(0, 3) + "  " + LANR_GUELTIG.substring(3));
    }

    @Test
    void kette_trennerAmEnde_gehoertNichtZurKette() {
        lanrSicher("LANR ", LANR_GUELTIG, " ");
        lanrSicher("LANR ", LANR_GUELTIG, "-");
        lanrSicher("LANR ", LANR_GUELTIG, ".");
        lanrSicher("LANR ", LANR_GUELTIG, "/");
        lanrSicher("LANR ", LANR_GUELTIG, String.valueOf(NBSP));
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, " "), " -");
    }

    @Test
    void kette_trennerAmEndeEinerLaengenabweichung_gehoertNichtZurKette() {
        lanrLaenge("LANR ", LAENGE_7, "-");
        lanrLaenge("LANR ", LAENGE_10, " ");
    }

    // ------------------------------------------------------------------ Zeichen, die keine Gruppentrenner sind

    @Test
    void kette_tabulatorKommaUnterstrichDoppelpunktSindKeineTrenner() {
        for (String t : List.of("\t", ",", "_", ":", ";", "(", ")", "'", "+", "=")) {
            // drei Bloecke mit je drei Ziffern, getrennt durch ein Nicht-Trenner-Zeichen: jeder Block allein zu kurz
            lanrKeinFund("LANR " + gruppiert(LANR_GUELTIG, t));
        }
    }

    @Test
    void kette_andereLeerraumzeichenSindKeineTrenner() {
        // U+2009 (schmales Leerzeichen), U+202F (schmales geschuetztes Leerzeichen), U+3000 (ideographisch)
        for (int cp : new int[] {0x2009, 0x202F, 0x3000}) {
            lanrKeinFund("LANR " + gruppiert(LANR_GUELTIG, String.valueOf((char) cp)));
        }
    }

    @Test
    void kette_buchstabeAmKettenanfang_dieKetteIstTrotzdemEineKette() {
        // Ziffern direkt hinter einem Buchstaben: der Buchstabe ist ein Fuellwort und beendet den Kontext
        lanrKeinFund("LANR x" + LANR_GUELTIG);
    }

    @Test
    void kette_buchstabeDirektHinterDerKette_stoertNicht() {
        lanrSicher("LANR ", LANR_GUELTIG, "abc");
        lanrSicher("LANR ", gruppiert(LANR_GUELTIG, " "), "x");
    }

    @Test
    void kette_vorgesetztesZeichenVorDerKette_gehoertNichtZumFund() {
        lanrSicher("LANR -", LANR_GUELTIG, "");
        lanrSicher("LANR (", LANR_GUELTIG, ")");
        lanrSicher("LANR #", LANR_GUELTIG, "");
    }

    @Test
    void kette_unmittelbarHinterKontextwort_ohneTrennzeichen() {
        lanrSicher("LANR", LANR_GUELTIG, "");
        lanrSicher("LANR", gruppiert(LANR_GUELTIG, " "), "");
    }

    // ------------------------------------------------------------------ Kontextwortgrenzen (Art IK)

    @Test
    void ik_technikFabrikIkea_keinKontextwort() {
        for (String wort : List.of("Technik:", "Fabrik", "IKEA", "IKs", "Mikrofon", "Tick")) {
            assertEquals(List.of(), scan(KvArt.IK, wort + " " + IK_GUELTIG), wort);
        }
    }

    @Test
    void ik_tuerkischeIFormenFaltenNichtZuIk() {
        // U+0130 (I mit Punkt), U+0131 (i ohne Punkt), Kelvin-Zeichen U+212A: kein ASCII-Kontextwort "ik"
        String[] varianten = {"" + (char) 0x0130 + "K", "I" + (char) 0x212A, "" + (char) 0x0131 + "k",
            "" + (char) 0x0131 + (char) 0x212A, "i" + (char) 0x212A, "IK" + (char) 0x0301};
        for (String wort : varianten) {
            assertEquals(List.of(), scan(KvArt.IK, wort + " " + IK_GUELTIG));
        }
    }

    @Test
    void ik_konfigurationIk_gueltigSicher092_ungueltigFraglich_kontextwoerter() {
        assertFund(KvArt.IK, "IK ", IK_GUELTIG, "", 0.92, ConfidenceLevel.SICHER, "ik");
        assertFund(KvArt.IK, "IK-Nr. ", IK_GUELTIG, "", 0.92, ConfidenceLevel.SICHER, "ik");
        assertFund(KvArt.IK, "Praxis-IK=", IK_GUELTIG, "", 0.92, ConfidenceLevel.SICHER, "ik");
        assertFund(KvArt.IK, "Institutionskennzeichen: ", IK_GUELTIG, "", 0.92, ConfidenceLevel.SICHER, "ik");
        assertFund(KvArt.IK, "IK ", IK_UNGUELTIG_KOMPLEMENT, "", 0.50, ConfidenceLevel.FRAGLICH,
                "ik-pruefziffer-ungueltig");
        assertFund(KvArt.IK, "IK ", LAENGE_7, "", LAENGE, ConfidenceLevel.FRAGLICH, "ik-laenge-abweichend");
    }

    @Test
    void ik_arztNrIstKeinKontextwortDerArtIk() {
        assertEquals(List.of(), scan(KvArt.IK, "Arzt-Nr. " + IK_GUELTIG));
    }

    // ------------------------------------------------------------------ Ereignisreihenfolge

    @Test
    void ereignisse_mehrereKettenUndKontexte_inTextreihenfolge() {
        String text = "LANR " + LANR_GUELTIG + "; LANR " + LAENGE_10 + "! LANR " + LANR_GUELTIG_ZWEI + "\nLANR "
                + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        List<Finding> funde = scan(KvArt.LANR, text);
        assertEquals(4, funde.size());
        assertEquals(LANR_GUELTIG, funde.get(0).originalValue());
        assertEquals(LAENGE_10, funde.get(1).originalValue());
        assertEquals(LANR_GUELTIG_ZWEI, funde.get(2).originalValue());
        assertEquals(LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, funde.get(3).originalValue());
    }

    @Test
    void ereignisse_zeilenumbruchCrLfZaehltAlsEiner_endeDesKontexts() {
        // Der Umbruch beendet den Kontext (Folgetask: ein Umbruch ist erlaubt); CRLF verhaelt sich wie LF
        lanrKeinFund("LANR\r\n" + LANR_GUELTIG);
        lanrKeinFund("LANR\n" + LANR_GUELTIG);
        // Nach dem Umbruch beginnt ein neues Kontextwort normal
        lanrSicher("LANR\r\nLANR ", LANR_GUELTIG, "");
    }

    @Test
    void ereignisse_zeichenAusserhalbDesBmp_sindNeutral() {
        // U+1F600 als Surrogatpaar zwischen Kontextwort und Zahl ist Sonstiges und beendet den Kontext nicht
        String emoji = new String(Character.toChars(0x1F600));
        lanrSicher("LANR " + emoji + " ", LANR_GUELTIG, "");
        lanrSicher("LANR:" + emoji, LANR_GUELTIG, emoji);
    }

    @Test
    void ereignisse_buchstabenAusserhalbDesBmp_sindEinWort() {
        // U+1D400 (mathematisches fettes A) ist ein Buchstabe und damit ein Fuellwort, das den Kontext beendet
        String a = new String(Character.toChars(0x1D400));
        lanrKeinFund("LANR " + a + " " + LANR_GUELTIG);
    }
}
