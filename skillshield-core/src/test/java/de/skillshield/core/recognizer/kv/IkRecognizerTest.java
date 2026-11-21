package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_BINDESTRICH;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_GRUPPE_6_3;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_NBSP;
import static de.skillshield.core.SynthetischeKvNummern.IK_TRENNER_PUNKT;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_NULL;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static de.skillshield.core.SynthetischeKvNummern.bildeIk;
import static de.skillshield.core.SynthetischeKvNummern.ikGueltigReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests des {@link IkRecognizer} (Feature kv-nummern-erkennung, Task T-4). Alle Nummern stammen aus
 * {@code SynthetischeKvNummern}; hier steht keine neunstellige Ziffernfolge als Literal, Texte entstehen per
 * Verkettung. Jeder Fund wird gegen {@code text.substring(start, end).equals(originalValue)}, Typ, Sortierung und
 * Ueberlappungsfreiheit geprueft; die Beispiel-Tests pruefen Bereich, Typ, Konfidenz, Stufe und Quelle.
 *
 * <p>Nicht Teil dieses Tasks (gehoert zu T-5): Fuellwoerter (AK-12), nachgestellter Kontext (AK-13),
 * Zeilenumbruch-Toleranz (AK-14), Aufzaehlung (AK-15) und Fund am Textanfang bei nachgestelltem Kontext (AK-16 b).
 *
 * <p>Regel aus T-1: Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder IK-Verfahren
 * gueltig ist und nicht mit {@code 00} beginnt. Die Laufzeittests nennen ihre Begruendung je Test.
 */
class IkRecognizerTest {

    private static final IkRecognizer R = new IkRecognizer();
    private static final double SICHER = 0.92;
    private static final double PZ_UNGUELTIG = 0.50;
    private static final double LAENGE = 0.40;
    private static final String Q = "ik";
    private static final String Q_PZ = "ik-pruefziffer-ungueltig";
    private static final String Q_LAENGE = "ik-laenge-abweichend";
    private static final char NBSP = (char) 0xA0;

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> scan(String text) {
        List<Finding> funde = R.detect(text);
        assertNotNull(funde);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring-Invariante");
            assertEquals(FindingType.IK, f.type(), "Typ");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde;
    }

    /** Genau ein Fund {@code wert} direkt hinter {@code vor}; Konfidenz, Stufe und Quelle wie angegeben. */
    private static void assertFund(String vor, String wert, String nach, double konfidenz, ConfidenceLevel stufe,
            String quelle) {
        List<Finding> funde = scan(vor + wert + nach);
        assertEquals(1, funde.size(), "Anzahl Funde");
        Finding f = funde.get(0);
        assertEquals(wert, f.originalValue(), "Originalwert");
        assertEquals(vor.length(), f.start(), "Start");
        assertEquals(vor.length() + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertEquals(stufe, f.level(), "Stufe");
        assertEquals(quelle, f.source(), "Quelle");
    }

    private static void assertSicher(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, SICHER, ConfidenceLevel.SICHER, Q);
    }

    private static void assertFraglichPruefziffer(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, PZ_UNGUELTIG, ConfidenceLevel.FRAGLICH, Q_PZ);
    }

    private static void assertFraglichLaenge(String vor, String wert, String nach) {
        assertFund(vor, wert, nach, LAENGE, ConfidenceLevel.FRAGLICH, Q_LAENGE);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), scan(text));
    }

    private static boolean keineZiffer(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ AK-3

    @Test
    void ak3_ikMitDirektemKontextwort_einFundSicher_rechnungAnIkGesendet() {
        assertSicher("Rechnung an IK ", IK_GUELTIG, " gesendet");
    }

    // ------------------------------------------------------------------ AK-4 (Kontextwoerter)

    @Test
    void ak4_kontextwortKleingeschriebenIkDoppelpunkt() {
        assertSicher("ik: ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortIkGleichheitszeichen() {
        assertSicher("IK=", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortIkNrPunkt() {
        assertSicher("IK-Nr. ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortIkNummerDoppelpunkt() {
        assertSicher("IK-Nummer: ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortIknr() {
        assertSicher("IKNR ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortInstitutionskennzeichen() {
        assertSicher("Institutionskennzeichen ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortPraxisIk() {
        assertSicher("Praxis-IK ", IK_GUELTIG, "");
    }

    @Test
    void ak4_weitereKontextwoerter_iknummer_ikNrMitLeerzeichen_grossUndKleinschreibung() {
        assertSicher("IKNUMMER ", IK_GUELTIG, "");
        assertSicher("IK Nr ", IK_GUELTIG, "");
        assertSicher("IK Nummer: ", IK_GUELTIG, "");
        assertSicher("iK ", IK_GUELTIG, "");
        assertSicher("INSTITUTIONSKENNZEICHEN: ", IK_GUELTIG, "");
        assertSicher("institutionskennzeichen ", IK_GUELTIG, "");
        assertSicher("Ik-nr: ", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortOhneAbstandVorDenZiffern_ikDirektAnDerNummer() {
        assertSicher("IK", IK_GUELTIG, "");
    }

    @Test
    void ak4_kontextwortImSatz_praefixUndSuffixText() {
        assertSicher("Die Praxis meldet das IK: ", IK_GUELTIG, " und sonst nichts");
    }

    @Test
    void ak4_kontextwortVonLanrWirdVomIkErkennerNichtGelesen() {
        assertKeinFund("LANR " + IK_GUELTIG);
        assertKeinFund("Arztnummer " + IK_GUELTIG);
        assertKeinFund("Arzt-Nr. " + IK_GUELTIG);
    }

    // ------------------------------------------------------------------ AK-6 (Trenner, IK-Formen)

    @Test
    void ak6_ikMitBindestrichGruppen333_sicher() {
        assertSicher("IK ", IK_TRENNER_BINDESTRICH, "");
    }

    @Test
    void ak6_ikMitPunktGruppen333_sicher() {
        assertSicher("IK ", IK_TRENNER_PUNKT, "");
    }

    @Test
    void ak6_ikMitLeerzeichenGruppen63_sicher() {
        assertSicher("IK ", IK_TRENNER_GRUPPE_6_3, "");
    }

    @Test
    void ak6_ikMitGeschuetztemLeerzeichen_sicher() {
        assertSicher("IK ", IK_TRENNER_NBSP, "");
    }

    @Test
    void ak6_ikMitLeerzeichenUndSchraegstrich_sicher() {
        String leer = IK_GUELTIG.substring(0, 3) + " " + IK_GUELTIG.substring(3, 6) + " " + IK_GUELTIG.substring(6);
        String schraeg = IK_GUELTIG.substring(0, 3) + "/" + IK_GUELTIG.substring(3, 6) + "/" + IK_GUELTIG.substring(6);
        assertSicher("IK ", leer, "");
        assertSicher("IK ", schraeg, "");
    }

    @Test
    void ak6_pruefzifferWirdUeberDieNeunZiffernOhneTrennerBerechnet_ungueltigMitTrennernBleibtFraglich() {
        String mitTrennern = IK_UNGUELTIG_KOMPLEMENT.substring(0, 3) + "-" + IK_UNGUELTIG_KOMPLEMENT.substring(3, 6)
                + "-" + IK_UNGUELTIG_KOMPLEMENT.substring(6);
        assertFraglichPruefziffer("IK ", mitTrennern, "");
    }

    @Test
    void ak6_zweiTrennerHintereinanderGehoerenNichtZumFund() {
        assertSicher("IK ", IK_GUELTIG, "  2025");
        // Doppeltrenner mitten in der Kette beendet sie: nur die sechs Ziffern davor (Kap. 3.2, Fall 2)
        String vorDoppelt = IK_GUELTIG.substring(0, 3) + " " + IK_GUELTIG.substring(3, 6);
        assertFraglichLaenge("IK ", vorDoppelt, "  " + IK_GUELTIG.substring(6));
    }

    @Test
    void ak6_trennerAmEndeGehoertNichtZumFund() {
        assertSicher("IK ", IK_TRENNER_BINDESTRICH, " ");
        assertSicher("IK ", IK_TRENNER_PUNKT, "-");
        assertSicher("IK ", IK_GUELTIG, "/");
        assertSicher("IK ", IK_TRENNER_NBSP, "" + NBSP);
    }

    // ------------------------------------------------------------------ AK-8 (Stufe nach IK-Pruefziffer)

    @Test
    void ak8_gueltig_123456_summe21_rest1_sicher() {
        // Stellen 3-8 = 123456: 2 + 2 + 6 + 4 + (10 -> 1) + 6 = 21, Rest 1 = Stelle 9
        assertSicher("IK ", IK_GUELTIG, "");
    }

    @Test
    void ak8_ungueltig_komplementLesart_9_fraglich() {
        // Summe 21, Rest 1; Stelle 9 ist 9 = 10 - 1 (Lesart "Komplement"): hier ausdruecklich ungueltig
        assertFraglichPruefziffer("IK ", IK_UNGUELTIG_KOMPLEMENT, "");
    }

    @Test
    void ak8_ungueltig_pruefzifferNull_fraglich() {
        // Summe 21, Rest 1; Stelle 9 ist 0
        assertFraglichPruefziffer("IK ", IK_UNGUELTIG_NULL, "");
    }

    @Test
    void ak8_ungueltig_ziffernVertauscht_fraglich() {
        // Stellen 3-8 = 124356: 2 + 2 + 8 + 3 + (10 -> 1) + 6 = 22, Rest 2; Stelle 9 ist 1
        assertFraglichPruefziffer("IK ", IK_UNGUELTIG_VERTAUSCHT, "");
    }

    // ------------------------------------------------------------------ AK-9 (falsche Pruefziffer bleibt Fund)

    @Test
    void ak9_ikMitFalscherPruefziffer_einFundFraglich_quelleOhneZiffern() {
        assertFraglichPruefziffer("IK ", IK_UNGUELTIG_KOMPLEMENT, "");
        Finding f = scan("IK " + IK_UNGUELTIG_KOMPLEMENT).get(0);
        assertEquals(IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG, f.source());
        assertTrue(keineZiffer(f.source()), "Quelle enthaelt eine Ziffer");
        assertFalse(f.source().contains(IK_UNGUELTIG_KOMPLEMENT));
    }

    // ------------------------------------------------------------------ AK-10 (ohne Kontextwort kein Fund)

    @Test
    void ak10_rueckrufUnterOhneKontextwort_keinFund() {
        assertKeinFund("R" + (char) 0xFC + "ckruf unter " + IK_GUELTIG);
    }

    @Test
    void ak10_vorgangLiegtVorOhneKontextwort_keinFund() {
        assertKeinFund("Vorgang " + IK_GUELTIG + " liegt vor");
    }

    @Test
    void ak10_telefonnummerOhneKontextwort_keinFund() {
        assertKeinFund("Tel. " + TELEFON_PROBE);
    }

    @Test
    void ak10_nummerAllein_keinFund() {
        assertKeinFund(IK_GUELTIG);
    }

    // ------------------------------------------------------------------ AK-11 (ganzes Wort, Laenge)

    @Test
    void ak11_technikMitDoppelpunkt_keinKontext() {
        assertKeinFund("Technik: " + IK_GUELTIG);
    }

    @Test
    void ak11_fabrik_keinKontext() {
        assertKeinFund("Fabrik " + IK_GUELTIG);
    }

    @Test
    void ak11_ikea_keinKontext() {
        assertKeinFund("IKEA " + IK_GUELTIG);
    }

    @Test
    void ak11_weitereWortteile_ikAlsWortanfangOderWortende_keinKontext() {
        assertKeinFund("IKX " + IK_GUELTIG);
        assertKeinFund("xIK " + IK_GUELTIG);
        assertKeinFund("PraxisIK " + IK_GUELTIG);
        assertKeinFund("IKs: " + IK_GUELTIG);
    }

    @Test
    void ak11_laenge8_ikMitAchtZiffern_fraglichLaenge() {
        assertFraglichLaenge("IK ", LAENGE_8, "");
    }

    @Test
    void ak11_laenge7und10_ikMitSiebenUndZehnZiffern_fraglichLaenge() {
        assertFraglichLaenge("IK ", LAENGE_7, "");
        assertFraglichLaenge("IK ", LAENGE_10, "");
    }

    @Test
    void ak11_laenge6und12_untereUndObereGrenzeSindFunde() {
        assertFraglichLaenge("IK ", LAENGE_6, "");
        assertFraglichLaenge("IK ", LAENGE_12, "");
    }

    @Test
    void ak11_weniger_als6Ziffern_keinFund() {
        assertKeinFund("IK " + LAENGE_4);
        assertKeinFund("IK " + LAENGE_5);
    }

    @Test
    void ak11_mehr_als12Ziffern_keinFund() {
        assertKeinFund("IK " + LAENGE_13);
    }

    @Test
    void ak11_nachNeunZiffernLeerzeichenUndZahl_fundNurDieNeunZiffern() {
        assertSicher("IK ", IK_GUELTIG, " 2025");
    }

    @Test
    void ak11_nachNeunZiffernBindestrichUndZahl_fundNurDieNeunZiffern() {
        assertSicher("IK ", IK_GUELTIG, "-2025");
    }

    // ------------------------------------------------------------------ AK-16 (Rand- und Fehlerfaelle, soweit T-4)

    @Test
    void ak16a_satzzeichenDirektHinterDerZahl_gehoertNichtZumFund() {
        assertSicher("IK ", IK_GUELTIG, ".");
        assertSicher("IK ", IK_GUELTIG, ",");
        assertSicher("IK ", IK_GUELTIG, ")");
    }

    @Test
    void ak16c_kontextwortOhneZahl_keinFundKeineAusnahme() {
        assertKeinFund("IK");
        assertKeinFund("IK:");
        assertKeinFund("Institutionskennzeichen fehlt");
        assertKeinFund("Praxis-IK");
        assertKeinFund("IK-Nr.");
    }

    @Test
    void ak16d_leererTextUndNurLeerraum_leereFundliste() {
        assertEquals(List.of(), scan(""));
        assertEquals(List.of(), scan("   \t  " + NBSP + " "));
        assertEquals(List.of(), scan(" \n \r\n "));
    }

    @Test
    void ak16e_nullAlsText_nullPointerExceptionMitFesterMeldung() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertEquals("text", e.getMessage());
    }

    // ------------------------------------------------------------------ AK-24 (kein Klartext), Quellen

    @Test
    void ak24_quellenSindFesteKonstantenOhneZiffernImFindingFormat() {
        assertEquals("ik", IkRecognizer.SOURCE);
        assertEquals("ik-pruefziffer-ungueltig", IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG);
        assertEquals("ik-laenge-abweichend", IkRecognizer.SOURCE_LAENGE_ABWEICHEND);
        assertEquals(KvArt.IK.source(), IkRecognizer.SOURCE);
        assertEquals(KvArt.IK.sourcePruefzifferUngueltig(), IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG);
        assertEquals(KvArt.IK.sourceLaengeAbweichend(), IkRecognizer.SOURCE_LAENGE_ABWEICHEND);
        for (String q : List.of(IkRecognizer.SOURCE, IkRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
                IkRecognizer.SOURCE_LAENGE_ABWEICHEND)) {
            assertTrue(keineZiffer(q), "Quelle mit Ziffer");
            assertTrue(q.length() <= Finding.MAX_SOURCE_LENGTH);
            // Das Finding-Format [a-z][a-z0-9-]{0,31} wird beim Erzeugen eines Fundes erzwungen
            Finding f = new Finding(FindingType.IK, "x", 0, 1, 0.5, q);
            assertEquals(q, f.source());
        }
    }

    @Test
    void ak24_toStringDerFundeEnthaeltWederNummerNochIhreErstenSechsZiffern() {
        for (String nummer : List.of(IK_GUELTIG, IK_UNGUELTIG_KOMPLEMENT, LAENGE_7)) {
            for (Finding f : scan("IK " + nummer)) {
                String s = f.toString();
                assertFalse(s.contains(nummer), "toString enthaelt die Nummer");
                assertFalse(s.contains(nummer.substring(0, 6)), "toString enthaelt Nummernteil");
            }
        }
    }

    @Test
    void ak24_keineAusnahmeMitKlartext_nullUndEngeFaelle() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> R.detect(null)).getMessage());
        assertEquals("text", assertThrows(NullPointerException.class,
                () -> KvNumberScanner.detect(KvArt.IK, null)).getMessage());
        // unvollstaendige Surrogate und Steuerzeichen
        assertEquals(List.of(), scan("IK " + (char) 0xD800));
        assertEquals(List.of(), scan((char) 0xDC00 + "IK"));
    }

    // ------------------------------------------------------------------ AK-26 (Determinismus)

    @Test
    void ak26_gleicherTextLiefertGleicheFundlisteInGleicherReihenfolge() {
        String text = "Rechnung an IK " + IK_GUELTIG + ", Institutionskennzeichen " + IK_UNGUELTIG_VERTAUSCHT
                + "; IK " + LAENGE_7 + "\nIK " + IK_TRENNER_NBSP + " Ende";
        List<Finding> erste = scan(text);
        assertEquals(4, erste.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(erste, R.detect(text));
        }
        assertEquals(erste, new IkRecognizer().detect(text));
    }

    @Test
    void ak26_eigenschaft_zufaelligeNummernMitPraefix00_stufeEntsprichtUnabhaengigerReferenz() {
        long seed = 20261123L;
        Random zufall = new Random(seed);
        for (int iteration = 0; iteration < 1_000; iteration++) {
            // Praefix 00 bleibt erhalten, weil nur die Stellen 3 bis 9 veraendert werden
            char[] z = bildeIk(zufall).toCharArray();
            if (zufall.nextBoolean()) {
                int stelle = 2 + zufall.nextInt(7);
                z[stelle] = (char) ('0' + zufall.nextInt(10));
            }
            String nummer = new String(z);
            assertTrue(nummer.startsWith("00"), "Seed " + seed + " Iteration " + iteration);
            String vor = "Praxis, IK: ";
            List<Finding> funde = scan(vor + nummer + ".");
            assertEquals(1, funde.size(), "Seed " + seed + " Iteration " + iteration);
            Finding f = funde.get(0);
            boolean gueltig = ikGueltigReferenz(nummer);
            assertEquals(gueltig ? SICHER : PZ_UNGUELTIG, f.confidence(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(gueltig ? Q : Q_PZ, f.source(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(vor.length(), f.start(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(nummer.length(), f.end() - f.start(), "Seed " + seed + " Iteration " + iteration);
        }
    }

    @Test
    void ak26_konfidenzSicherIst092_undStufeSicher() {
        Finding f = scan("IK " + IK_GUELTIG).get(0);
        assertEquals(0.92, f.confidence());
        assertEquals(ConfidenceLevel.SICHER, f.level());
    }

    // ------------------------------------------------------------------ Laufzeit (AK-21, Erkennerebene, OF-8)

    /**
     * Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart, dann Messlauf mit mindestens einer Million
     * Zeichen unter hartem Limit von 2 Sekunden und exakter Fundzahl.
     */
    private static void laufzeit(String baustein, String praefix, int erwartet, String formel) {
        String warm = bauen(praefix, baustein, 10_000);
        R.detect(warm);
        String text = bauen(praefix, baustein, 1_000_000);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size(),
                "Laufzeitgrenze 2 s ueberschritten: " + formel);
        assertEquals(erwartet, ist, formel);
    }

    private static String bauen(String praefix, String baustein, int mindestlaenge) {
        int wiederholungen = (mindestlaenge - praefix.length() + baustein.length() - 1) / baustein.length();
        return praefix + baustein.repeat(wiederholungen);
    }

    @Test
    void ak21_laufzeit_ikGefolgtVonEineMillionNullen_keinFund() {
        // Nullen statt anderer Ziffern: kein Neuner-Segment ohne Praefix 00 moeglich (hier ohnehin keines).
        // Formel: Die ganze Zeichenfolge ist ein einziger Ziffernblock mit ueber 12 Ziffern. Kein Praefix hat 9 oder
        // 6 bis 12 Ziffern, also kein Segment; der Block ist ein Fuellwort und beendet den Kontext: 0 Funde.
        laufzeit("0", "IK ", 0, "0 Funde");
    }

    @Test
    void ak21_laufzeit_ikGefolgtVonNurBindestrichen_keinFund() {
        // Formel: Kontextwort, dann nur Trennzeichen ohne Ziffer: 0 Funde (keine Ziffer, also keine Kette)
        laufzeit("-", "IK", 0, "0 Funde");
    }

    @Test
    void ak21_laufzeit_gueltigeIkMitKontextwortWiederholt_einFundJeWiederholung() {
        // Baustein "IK " + Nummer + " " = 3 + 9 + 1 = 13 Zeichen, Praefix leer. Wiederholungen
        // = (1 000 000 + 13 - 1) / 13 = 1 000 012 / 13 = 76 924 (aufgerundet, 13 * 76 924 = 1 000 012); je
        // Wiederholung ein Fund (Kontextwort direkt vor der Nummer). Alle Neuner-Segmente sind IK_GUELTIG (Praefix
        // 00); die Nummern stehen durch "IK " getrennt, nach der Nummer gibt es keine Fortsetzung der Kette.
        String baustein = "IK " + IK_GUELTIG + " ";
        assertEquals(13, baustein.length());
        int erwartet = (1_000_000 + baustein.length() - 1) / baustein.length();
        assertEquals(76_924, erwartet);
        assertTrue(erwartet > 70_000);
        laufzeit(baustein, "", erwartet, "76 924 Funde");
    }

    @Test
    void ak21_laufzeit_ikLeerzeichenWiederholtOhneZiffern_keinFund() {
        // Formel: 333 334 Kontextwoerter ("IK " = 3 Zeichen), keine Ziffer: 0 Funde
        laufzeit("IK ", "", 0, "0 Funde");
    }
}
