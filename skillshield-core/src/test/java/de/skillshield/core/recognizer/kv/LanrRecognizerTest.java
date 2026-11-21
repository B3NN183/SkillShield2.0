package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_13;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_4;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_5;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_PRUEFZIFFER_NULL;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_LEERZEICHEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_NBSP;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_SCHRAEGSTRICH;
import static de.skillshield.core.SynthetischeKvNummern.LANR_TRENNER_UNGLEICHE_GRUPPEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_ZIFFER_GEAENDERT;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static de.skillshield.core.SynthetischeKvNummern.bildeLanr;
import static de.skillshield.core.SynthetischeKvNummern.lanrGueltigReferenz;
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
 * Tests des {@link LanrRecognizer} (Feature kv-nummern-erkennung, Task T-3). Alle Nummern stammen aus
 * {@code SynthetischeKvNummern}; hier steht keine neunstellige Ziffernfolge als Literal, Texte entstehen per
 * Verkettung. Jeder Fund wird gegen {@code text.substring(start, end).equals(originalValue)}, Typ, Sortierung und
 * Ueberlappungsfreiheit geprueft; die Beispiel-Tests pruefen Bereich, Typ, Konfidenz, Stufe und Quelle.
 *
 * <p>Tests mit dem Praefix {@code t3Uebergang} halten das Verhalten dieses Tasks fest, das der Folgetask
 * (Fuellwoerter, Zeilenumbruch, Aufzaehlung, nachgestellter Kontext) bewusst aendert; er ersetzt sie durch die
 * Tests zu AK-12 bis AK-15.
 *
 * <p>Regel aus T-1: Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder IK-Verfahren
 * gueltig ist und nicht mit {@code 00} beginnt. Die Laufzeittests waehlen deshalb Ziffernmuster, bei denen kein
 * Neuner-Segment entsteht oder das Segment mit {@code 00} beginnt (Begruendung je Test).
 */
class LanrRecognizerTest {

    private static final LanrRecognizer R = new LanrRecognizer();
    private static final double SICHER = 0.95;
    private static final double PZ_UNGUELTIG = 0.50;
    private static final double LAENGE = 0.40;
    private static final String Q = "lanr";
    private static final String Q_PZ = "lanr-pruefziffer-ungueltig";
    private static final String Q_LAENGE = "lanr-laenge-abweichend";
    private static final char NBSP = (char) 0xA0;

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> scan(String text) {
        List<Finding> funde = R.detect(text);
        assertNotNull(funde);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring-Invariante");
            assertEquals(FindingType.LANR, f.type(), "Typ");
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

    private static String repeat(String s, int mal) {
        return s.repeat(mal);
    }

    // ------------------------------------------------------------------ AK-1

    @Test
    void ak1_lanrMitDirektemKontextwort_einFundSicher_ohneKontextwortOhneKomma() {
        assertSicher("Arzt: LANR ", LANR_GUELTIG, ", Praxis");
    }

    // ------------------------------------------------------------------ AK-2 (Kontextwoerter)

    @Test
    void ak2_kontextwortKleingeschriebenLanr() {
        assertSicher("lanr ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLanrDoppelpunkt() {
        assertSicher("LANR: ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLanrGleichheitszeichen() {
        assertSicher("LANR=", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLanrSemikolon() {
        assertSicher("LANR;", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLanrTabulator() {
        assertSicher("LANR\t", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLanrNrPunkt() {
        assertSicher("LANR-Nr. ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortArztnummer() {
        assertSicher("Arztnummer ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortLebenslangeArztnummerDoppelpunkt() {
        assertSicher("Lebenslange Arztnummer: ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortArztNrPunkt() {
        assertSicher("Arzt-Nr. ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortPraxisLanr() {
        assertSicher("Praxis-LANR ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_grossUndKleinschreibungIstGleichgueltig() {
        assertSicher("LaNr ", LANR_GUELTIG, "");
        assertSicher("lAnR: ", LANR_GUELTIG, "");
        assertSicher("ARZTNUMMER ", LANR_GUELTIG, "");
        assertSicher("arzt-nr ", LANR_GUELTIG, "");
        assertSicher("ARZT NR. ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_weitereKontextwoerter_lanrnr_arztnr_lanrNummer_lanrNrOhnePunkt() {
        assertSicher("LANRNR ", LANR_GUELTIG, "");
        assertSicher("Arztnr. ", LANR_GUELTIG, "");
        assertSicher("LANR-Nummer: ", LANR_GUELTIG, "");
        assertSicher("LANR Nr ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortOhneAbstandVorDenZiffern_lanrDirektAnDerNummer() {
        assertSicher("LANR", LANR_GUELTIG, "");
    }

    @Test
    void ak2_arztNrMitLeerzeichenUndGeschuetztemLeerzeichen() {
        assertSicher("Arzt Nummer ", LANR_GUELTIG, "");
        assertSicher("Arzt" + NBSP + "Nr. ", LANR_GUELTIG, "");
    }

    @Test
    void ak2_kontextwortImSatz_praefixUndSuffixText() {
        assertSicher("Die Praxis meldet die LANR: ", LANR_GUELTIG, " und sonst nichts");
    }

    // ------------------------------------------------------------------ AK-6 (Trenner, LANR-Formen)

    @Test
    void ak6_lanrMitLeerzeichenGruppen333_sicher() {
        assertSicher("LANR ", LANR_TRENNER_LEERZEICHEN, "");
    }

    @Test
    void ak6_lanrMitLeerzeichenGruppen72_sicher() {
        assertSicher("LANR ", LANR_TRENNER_UNGLEICHE_GRUPPEN, "");
    }

    @Test
    void ak6_lanrMitGeschuetztemLeerzeichen_sicher() {
        assertSicher("LANR ", LANR_TRENNER_NBSP, "");
    }

    @Test
    void ak6_lanrMitSchraegstrich_sicher() {
        assertSicher("LANR ", LANR_TRENNER_SCHRAEGSTRICH, "");
    }

    @Test
    void ak6_lanrMitBindestrichUndPunkt_sicher() {
        assertSicher("LANR ", LANR_GUELTIG.substring(0, 3) + "-" + LANR_GUELTIG.substring(3, 6) + "-"
                + LANR_GUELTIG.substring(6), "");
        assertSicher("LANR ", LANR_GUELTIG.substring(0, 3) + "." + LANR_GUELTIG.substring(3, 6) + "."
                + LANR_GUELTIG.substring(6), "");
    }

    @Test
    void ak6_pruefzifferWirdUeberDieNeunZiffernOhneTrennerBerechnet_ungueltigMitTrennernBleibtFraglich() {
        String mitTrennern = LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN.substring(0, 3) + " "
                + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN.substring(3, 6) + " "
                + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN.substring(6);
        assertFraglichPruefziffer("LANR ", mitTrennern, "");
    }

    @Test
    void ak6_zweiTrennerHintereinanderGehoerenNichtZumFund() {
        assertSicher("LANR ", LANR_GUELTIG, "  2025");
        // Doppeltrenner mitten in der Kette beendet sie: nur die sechs Ziffern vor dem Doppeltrenner (Kap. 3.2, Fall 2)
        String doppelt = LANR_GUELTIG.substring(0, 3) + " " + LANR_GUELTIG.substring(3, 6);
        assertFraglichLaenge("LANR ", doppelt, "  " + LANR_GUELTIG.substring(6));
    }

    @Test
    void ak6_trennerAmEndeGehoertNichtZumFund() {
        assertSicher("LANR ", LANR_TRENNER_LEERZEICHEN, " ");
        assertSicher("LANR ", LANR_TRENNER_LEERZEICHEN, "-");
        assertSicher("LANR ", LANR_GUELTIG, "/");
    }

    // ------------------------------------------------------------------ AK-7 (Stufe nach Pruefziffer)

    @Test
    void ak7_gueltig_001235_pruefziffer1_sicher() {
        // Stellen 1-6: 0*4 + 0*9 + 1*4 + 2*9 + 3*4 + 5*9 = 79, Rest 9, Pruefziffer 1 = Stelle 7
        assertSicher("LANR ", LANR_GUELTIG, "");
    }

    @Test
    void ak7_gueltig_fachgruppeWirdNichtGeprueft_sicher() {
        // Summe 79, Pruefziffer 1 = Stelle 7; Stellen 8 und 9 (Fachgruppe) gehen nicht ein
        assertSicher("LANR ", LANR_GUELTIG_FACHGRUPPE, "");
    }

    @Test
    void ak7_gueltig_rechenergebnis10ergibtPruefziffer0_sicher() {
        // Stellen 1-6: 4 + 18 + 12 + 36 = 70, Rest 0, 10 - 0 = 10 ergibt Pruefziffer 0 = Stelle 7
        assertSicher("LANR ", LANR_GUELTIG_PRUEFZIFFER_NULL, "");
    }

    @Test
    void ak7_gueltig_001236_pruefziffer2_sicher() {
        // Stellen 1-6: 4 + 18 + 12 + 54 = 88, Rest 8, Pruefziffer 2 = Stelle 7
        assertSicher("LANR ", LANR_GUELTIG_ZWEI, "");
    }

    @Test
    void ak7_ungueltig_pruefzifferVerschoben_fraglich() {
        // Summe 79, erwartet 1, Stelle 7 ist 2
        assertFraglichPruefziffer("LANR ", LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "");
    }

    @Test
    void ak7_ungueltig_nachbarziffernVertauscht_fraglich() {
        // Stellen 1-6: 4 + 27 + 8 + 45 = 84, Rest 4, erwartet 6, Stelle 7 ist 1
        assertFraglichPruefziffer("LANR ", LANR_UNGUELTIG_VERTAUSCHT, "");
    }

    @Test
    void ak7_ungueltig_eineZiffernGeaendert_fraglich() {
        // Summe 79, erwartet 1, Stelle 7 ist 0
        assertFraglichPruefziffer("LANR ", LANR_UNGUELTIG_ZIFFER_GEAENDERT, "");
    }

    // ------------------------------------------------------------------ AK-9 (falsche Pruefziffer bleibt Fund)

    @Test
    void ak9_lanrMitFalscherPruefziffer_einFundFraglich_quelleOhneZiffern() {
        assertFraglichPruefziffer("LANR ", LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, "");
        Finding f = scan("LANR " + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN).get(0);
        assertEquals(LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG, f.source());
        assertTrue(keineZiffer(f.source()), "Quelle enthaelt eine Ziffer");
        assertFalse(f.source().contains(LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN));
    }

    // ------------------------------------------------------------------ AK-10 (ohne Kontextwort kein Fund)

    @Test
    void ak10_rueckrufUnterOhneKontextwort_keinFund() {
        assertKeinFund("R" + (char) 0xFC + "ckruf unter " + LANR_GUELTIG);
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
        assertKeinFund(LANR_GUELTIG);
    }

    // ------------------------------------------------------------------ AK-11 (ganzes Wort, Laenge)

    @Test
    void ak11_lanrxMitBuchstabeDahinter_keinKontext() {
        assertKeinFund("LANRX " + LANR_GUELTIG);
        assertKeinFund("LANRX: " + LANR_GUELTIG);
    }

    @Test
    void ak11_xlanrMitBuchstabeDavor_keinKontext() {
        assertKeinFund("xLANR " + LANR_GUELTIG);
        assertKeinFund("PraxisLANR " + LANR_GUELTIG);
    }

    @Test
    void ak11_laenge7_lanrMitSiebenZiffern_fraglichLaenge() {
        assertFraglichLaenge("LANR ", LAENGE_7, "");
    }

    @Test
    void ak11_laenge8_lanrMitAchtZiffern_fraglichLaenge() {
        assertFraglichLaenge("LANR ", LAENGE_8, "");
    }

    @Test
    void ak11_laenge10_lanrMitZehnZiffern_fraglichLaenge() {
        assertFraglichLaenge("LANR ", LAENGE_10, "");
    }

    @Test
    void ak11_laenge6und12_untereUndObereGrenzeSindFunde() {
        assertFraglichLaenge("LANR ", LAENGE_6, "");
        assertFraglichLaenge("LANR ", LAENGE_12, "");
    }

    @Test
    void ak11_weniger_als6Ziffern_keinFund() {
        assertKeinFund("LANR " + LAENGE_4);
        assertKeinFund("LANR " + LAENGE_5);
    }

    @Test
    void ak11_mehr_als12Ziffern_keinFund() {
        assertKeinFund("LANR " + LAENGE_13);
    }

    @Test
    void ak11_nachNeunZiffernTrennerUndZahl_fundNurDieNeunZiffern() {
        assertSicher("LANR ", LANR_GUELTIG, " 2025");
    }

    @Test
    void ak11_nachNeunZiffernBindestrichUndZahl_fundNurDieNeunZiffern() {
        assertSicher("LANR ", LANR_GUELTIG, "-2025");
    }

    // ------------------------------------------------------------------ AK-16 (Rand- und Fehlerfaelle)

    @Test
    void ak16a_satzzeichenDirektHinterDerZahl_gehoertNichtZumFund() {
        assertSicher("LANR ", LANR_GUELTIG, ".");
        assertSicher("LANR ", LANR_GUELTIG, ",");
        assertSicher("LANR ", LANR_GUELTIG, ")");
    }

    @Test
    void ak16c_kontextwortOhneZahl_keinFundKeineAusnahme() {
        assertKeinFund("LANR");
        assertKeinFund("LANR:");
        assertKeinFund("Arztnummer fehlt");
        assertKeinFund("Praxis LANR");
        assertKeinFund("Arzt-Nr.");
        assertKeinFund("Arzt-");
    }

    @Test
    void ak16c_kontextwortAmTextende_keinFundKeineAusnahme() {
        assertKeinFund("Hinweis: die Arztnummer");
        assertKeinFund("Arzt-N");
        assertKeinFund("Arzt Nummer");
    }

    @Test
    void ak16d_leererText_leereFundliste() {
        assertEquals(List.of(), scan(""));
    }

    @Test
    void ak16d_nurLeerraum_leereFundliste() {
        assertEquals(List.of(), scan("   \t  " + NBSP + " "));
        assertEquals(List.of(), scan(" \n \r\n "));
    }

    @Test
    void ak16e_nullAlsText_nullPointerExceptionMitFesterMeldung() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertEquals("text", e.getMessage());
    }

    // ------------------------------------------------------------------ Kontextwortgrenzen (Kap. 3.1)

    @Test
    void kontextwort_arztOhneNr_keinKontext() {
        assertKeinFund("Arzt " + LANR_GUELTIG);
        assertKeinFund("Arzt: " + LANR_GUELTIG);
        assertKeinFund("Arzt-" + LANR_GUELTIG);
    }

    @Test
    void kontextwort_arztNrMitZweiTrennernOderRestbuchstaben_keinKontext() {
        assertKeinFund("Arzt - Nr " + LANR_GUELTIG);
        assertKeinFund("Arzt--Nr " + LANR_GUELTIG);
        assertKeinFund("Arzt-Nrx " + LANR_GUELTIG);
        assertKeinFund("Arzt-Nummern " + LANR_GUELTIG);
        assertKeinFund("Arzt:Nr " + LANR_GUELTIG);
    }

    @Test
    void kontextwort_nrOhneVorwort_keinKontext() {
        assertKeinFund("Nr. " + LANR_GUELTIG);
        assertKeinFund("Nummer " + LANR_GUELTIG);
    }

    @Test
    void kontextwort_arztNrMitKurzemNachsatzUndLangemWort() {
        // "nummer" (sechs Zeichen) ist das laengste Nachsatzwort; ein siebter Buchstabe macht es zu einem anderen Wort
        assertSicher("Arzt-Nummer ", LANR_GUELTIG, "");
        assertKeinFund("Arzt-Nummere " + LANR_GUELTIG);
    }

    @Test
    void kontextwort_buchstabeFremderSchrift_keinKontext() {
        // Kyrillisches A (U+0410) statt lateinischem A: anderes Wort, kein Kontext
        assertKeinFund("L" + (char) 0x0410 + "NR " + LANR_GUELTIG);
        // Vollbreite Buchstaben (U+FF2C U+FF21 U+FF2E U+FF32)
        assertKeinFund("" + (char) 0xFF2C + (char) 0xFF21 + (char) 0xFF2E + (char) 0xFF32 + " " + LANR_GUELTIG);
    }

    @Test
    void kontextwort_kombinationszeichenGehoertZumWort_keinKontext() {
        // LANR + kombinierender Akut (U+0301): ein Wort aus fuenf Zeichen
        assertKeinFund("LANR" + (char) 0x0301 + " " + LANR_GUELTIG);
    }

    @Test
    void kontextwort_hinterZiffernOderSonderzeichen_gilt() {
        assertSicher("(LANR) ", LANR_GUELTIG, "");
        assertSicher("[LANR]=", LANR_GUELTIG, "");
        assertSicher("?lanr=", LANR_GUELTIG, "");
        assertSicher("x1LANR ", LANR_GUELTIG, "");
    }

    @Test
    void kontextwort_zweiKontextwoerterHintereinander_gueltig() {
        assertSicher("LANR LANR ", LANR_GUELTIG, "");
        assertSicher("Arztnummer, LANR: ", LANR_GUELTIG, "");
    }

    @Test
    void kontextwort_nachsatzwortBleibtDirekt_auchMehrfach() {
        // Auslegung (Kap. 3.1): "Nr" bei direkt ist kein Fuellwort und beendet "direkt" nicht
        assertSicher("LANR Nr Nr ", LANR_GUELTIG, "");
        assertSicher("LANR-Nr.: ", LANR_GUELTIG, "");
    }

    @Test
    void kontextwort_nachsatzwortOhneKontextwort_istNurEinWort() {
        assertKeinFund("Nr " + LANR_GUELTIG);
        assertKeinFund("Praxis Nr " + LANR_GUELTIG);
    }

    // ------------------------------------------------------------------ Satzende und Zeilenumbruch (Kap. 3.3)

    @Test
    void satzende_punktDirektHinterKontextwortIstAbkuerzungspunkt() {
        assertSicher("LANR. ", LANR_GUELTIG, "");
        assertSicher("LANR.", LANR_GUELTIG, "");
    }

    @Test
    void satzende_ausrufe_und_fragezeichenHinterKontextwortBeendenKontext() {
        assertKeinFund("LANR! " + LANR_GUELTIG);
        assertKeinFund("LANR? " + LANR_GUELTIG);
        assertKeinFund("Arztnummer?" + LANR_GUELTIG);
    }

    @Test
    void satzende_satzendeVorDemKontextwortStoertNicht() {
        assertSicher("Hallo! Wie geht es? Dies ist es. LANR ", LANR_GUELTIG, "");
    }

    @Test
    void t3Uebergang_zeilenumbruchHinterKontextwortBeendetKontext() {
        assertKeinFund("LANR:\n" + LANR_GUELTIG);
        assertKeinFund("LANR\r\n" + LANR_GUELTIG);
        assertKeinFund("LANR\r" + LANR_GUELTIG);
        assertKeinFund("LANR" + (char) 0x85 + LANR_GUELTIG);
        assertKeinFund("LANR" + (char) 0x2028 + LANR_GUELTIG);
        assertKeinFund("LANR" + (char) 0x2029 + LANR_GUELTIG);
        assertKeinFund("LANR\n\n" + LANR_GUELTIG);
    }

    @Test
    void zeilenumbruch_vorDemKontextwort_gueltig() {
        assertSicher("Zeile eins\nLANR ", LANR_GUELTIG, "");
        assertSicher("Zeile eins\r\nLANR: ", LANR_GUELTIG, "\r\nNaechste Zeile");
    }

    @Test
    void t3Uebergang_fuellwortZwischenKontextwortUndZahl_beendetKontext() {
        assertKeinFund("LANR des Behandlers " + LANR_GUELTIG);
        assertKeinFund("Die LANR fehlt. Vorgang " + LANR_GUELTIG);
    }

    @Test
    void t3Uebergang_ziffernblockOhneSegmentZwischenKontextwortUndZahl_beendetKontext() {
        assertKeinFund("LANR 12, " + LANR_GUELTIG);
    }

    @Test
    void t3Uebergang_ausgegebenesSegmentBeendetKontext_keineAufzaehlung() {
        assertSicher("LANR ", LANR_GUELTIG, ", " + LANR_GUELTIG_ZWEI);
    }

    @Test
    void t3Uebergang_nachgestelltesKontextwortGibtNochKeinenFund() {
        assertKeinFund("Nummer " + LANR_GUELTIG + " (LANR)");
        assertKeinFund(LANR_GUELTIG + " (LANR)");
    }

    // ------------------------------------------------------------------ mehrere Funde, Reihenfolge, Typ

    @Test
    void zweiKontextwoerter_zweiFundeInTextreihenfolge() {
        String text = "LANR " + LANR_GUELTIG + " und Arztnummer " + LANR_UNGUELTIG_VERTAUSCHT;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(LANR_GUELTIG, funde.get(0).originalValue());
        assertEquals(SICHER, funde.get(0).confidence());
        assertEquals(LANR_UNGUELTIG_VERTAUSCHT, funde.get(1).originalValue());
        assertEquals(PZ_UNGUELTIG, funde.get(1).confidence());
        assertTrue(funde.get(0).end() <= funde.get(1).start());
    }

    @Test
    void ikKontextwortWirdVomLanrErkennerNichtGelesen() {
        assertKeinFund("IK " + LANR_GUELTIG);
        assertKeinFund("Institutionskennzeichen " + LANR_GUELTIG);
    }

    // ------------------------------------------------------------------ AK-24 (kein Klartext), Quellen

    private static boolean keineZiffer(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= '0' && s.charAt(i) <= '9') {
                return false;
            }
        }
        return true;
    }

    @Test
    void ak24_quellenSindFesteKonstantenOhneZiffernImFindingFormat() {
        assertEquals("lanr", LanrRecognizer.SOURCE);
        assertEquals("lanr-pruefziffer-ungueltig", LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG);
        assertEquals("lanr-laenge-abweichend", LanrRecognizer.SOURCE_LAENGE_ABWEICHEND);
        assertEquals(KvArt.LANR.source(), LanrRecognizer.SOURCE);
        assertEquals(KvArt.LANR.sourcePruefzifferUngueltig(), LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG);
        assertEquals(KvArt.LANR.sourceLaengeAbweichend(), LanrRecognizer.SOURCE_LAENGE_ABWEICHEND);
        for (String q : List.of(LanrRecognizer.SOURCE, LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
                LanrRecognizer.SOURCE_LAENGE_ABWEICHEND)) {
            assertTrue(keineZiffer(q), "Quelle mit Ziffer");
            assertTrue(q.length() <= Finding.MAX_SOURCE_LENGTH);
            // Das Finding-Format [a-z][a-z0-9-]{0,31} wird beim Erzeugen eines Fundes erzwungen
            Finding f = new Finding(FindingType.LANR, "x", 0, 1, 0.5, q);
            assertEquals(q, f.source());
        }
    }

    @Test
    void ak24_toStringDerFundeEnthaeltWederNummerNochIhreErstenSechsZiffern() {
        for (String nummer : List.of(LANR_GUELTIG, LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LAENGE_7)) {
            for (Finding f : scan("LANR " + nummer)) {
                String s = f.toString();
                assertFalse(s.contains(nummer), "toString enthaelt die Nummer");
                assertFalse(s.contains(nummer.substring(0, 6)), "toString enthaelt Nummernteil");
            }
        }
    }

    @Test
    void ak24_keineAusnahmeMitKlartext_nullUndEngeFaelle() {
        // Einzige Ausnahme des Durchlaufs: NPE mit fester Meldung; Randfaelle werfen nichts
        assertEquals("text", assertThrows(NullPointerException.class, () -> R.detect(null)).getMessage());
        assertThrows(NullPointerException.class, () -> KvNumberScanner.detect(null, "x"));
        assertThrows(NullPointerException.class, () -> KvNumberScanner.detect(KvArt.LANR, null));
        assertEquals("art", assertThrows(NullPointerException.class,
                () -> KvNumberScanner.detect(null, "x")).getMessage());
        assertEquals("text", assertThrows(NullPointerException.class,
                () -> KvNumberScanner.detect(KvArt.LANR, null)).getMessage());
        // unvollstaendige Surrogate und Steuerzeichen
        assertEquals(List.of(), scan("LANR " + (char) 0xD800));
        assertEquals(List.of(), scan((char) 0xDC00 + "LANR"));
    }

    // ------------------------------------------------------------------ AK-26 (Determinismus)

    @Test
    void ak26_gleicherTextLiefertGleicheFundlisteInGleicherReihenfolge() {
        String text = "Arzt: LANR " + LANR_GUELTIG + ", Arztnummer " + LANR_UNGUELTIG_VERTAUSCHT + "; LANR "
                + LAENGE_7 + "\nLANR " + LANR_TRENNER_NBSP + " Ende";
        List<Finding> erste = scan(text);
        assertEquals(4, erste.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(erste, R.detect(text));
        }
        assertEquals(erste, new LanrRecognizer().detect(text));
    }

    @Test
    void ak26_eigenschaft_zufaelligeNummernMitPraefix00_stufeEntsprichtUnabhaengigerReferenz() {
        long seed = 20261122L;
        Random zufall = new Random(seed);
        for (int iteration = 0; iteration < 1_000; iteration++) {
            // Praefix 00 bleibt erhalten, weil nur die Stellen 3 bis 9 veraendert werden
            char[] z = bildeLanr(zufall).toCharArray();
            if (zufall.nextBoolean()) {
                int stelle = 2 + zufall.nextInt(7);
                z[stelle] = (char) ('0' + zufall.nextInt(10));
            }
            String nummer = new String(z);
            assertTrue(nummer.startsWith("00"), "Seed " + seed + " Iteration " + iteration);
            String vor = "Praxis, LANR: ";
            List<Finding> funde = scan(vor + nummer + ".");
            assertEquals(1, funde.size(), "Seed " + seed + " Iteration " + iteration);
            Finding f = funde.get(0);
            boolean gueltig = lanrGueltigReferenz(nummer);
            assertEquals(gueltig ? SICHER : PZ_UNGUELTIG, f.confidence(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(gueltig ? Q : Q_PZ, f.source(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(vor.length(), f.start(), "Seed " + seed + " Iteration " + iteration);
            assertEquals(nummer.length(), f.end() - f.start(), "Seed " + seed + " Iteration " + iteration);
        }
    }

    // ------------------------------------------------------------------ bekannte Grenzen (Kap. 3.5)

    @Test
    void bekannteGrenze2_kurzerZiffernblockVorDerNummer_einFundUeberBeides_fraglichLaenge() {
        // "12 " + neun Ziffern = 11 Ziffern: kein Neuner-Praefix, laengstes Praefix 6 bis 12 ist die ganze Kette
        assertFraglichLaenge("LANR ", "12 " + LANR_GUELTIG, "");
    }

    @Test
    void bekannteGrenze6_nichtAsciiZiffern_keinFund() {
        StringBuilder vollbreit = new StringBuilder();
        StringBuilder arabisch = new StringBuilder();
        for (int i = 0; i < LANR_GUELTIG.length(); i++) {
            int wert = LANR_GUELTIG.charAt(i) - '0';
            vollbreit.append((char) (0xFF10 + wert));
            arabisch.append((char) (0x0660 + wert));
        }
        assertKeinFund("LANR " + vollbreit);
        assertKeinFund("LANR " + arabisch);
    }

    @Test
    void bekannteGrenze7_nullbreitenzeichenInDerNummer_fundNurUeberDieErstenSechsZiffern() {
        // U+200B nach der sechsten Ziffer: Fund (fraglich, Laenge) nur ueber die ersten sechs, der Rest bleibt Klartext
        String nummer = LANR_GUELTIG.substring(0, 6) + (char) 0x200B + LANR_GUELTIG.substring(6);
        List<Finding> funde = scan("LANR " + nummer);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(LANR_GUELTIG.substring(0, 6), f.originalValue());
        assertEquals(5, f.start());
        assertEquals(11, f.end());
        assertEquals(LAENGE, f.confidence());
        assertEquals(Q_LAENGE, f.source());
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
        return praefix + repeat(baustein, wiederholungen);
    }

    @Test
    void ak21_laufzeit_lanrGefolgtVonNullUndLeerzeichenWiederholt_genauEinFund() {
        // Muster "0 " statt "1 ": neun Einsen waeren eine nach LANR gueltige Nummer ohne Praefix 00 (jede neunfach
        // wiederholte Ziffer x hat Pruefziffer x). Neun Nullen beginnen mit 00 (ausserhalb von Praefix 00 entsteht
        // kein Segment, weil alle Ketten nur aus Nullen bestehen).
        // Formel: Die erste Kette ("0 " neunmal, 17 Zeichen) ist ein Segment bei direktem Kontext = 1 Fund; das Segment
        // beendet den Kontext, kein weiteres Kontextwort folgt, die uebrigen Ketten ergeben keinen Fund. Summe 1.
        laufzeit("0 ", "LANR ", 1, "1 Fund");
    }

    @Test
    void ak21_laufzeit_lanrGefolgtVonNurLeerzeichen_keinFund() {
        // Formel: ein Kontextwort, keine Ziffer in 999 996 Leerzeichen: 0 Funde
        laufzeit(" ", "LANR", 0, "0 Funde");
    }

    @Test
    void ak21_laufzeit_lanrLeerzeichenWiederholtOhneZiffern_keinFund() {
        // Formel: 200 000 Kontextwoerter, keine Ziffer: 0 Funde
        laufzeit("LANR ", "", 0, "0 Funde");
    }

    @Test
    void ak21_laufzeit_lanrGefolgtVonEineMillionZiffern_keinFund() {
        // Formel: ein einziger Ziffernblock von mehr als 12 Ziffern ist kein Segment und beendet den Kontext: 0 Funde.
        // Ziffer 1 ist hier unkritisch: Ein Block von ueber 12 Ziffern erzeugt nie ein Neuner-Segment.
        String warm = "LANR " + repeat("1", 9_995);
        R.detect(warm);
        String text = "LANR " + repeat("1", 1_000_000);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> R.detect(text).size());
        assertEquals(0, ist, "0 Funde");
    }

    @Test
    void ak21_laufzeit_viererBloeckeOhneNeunerPraefix_genauEinFund() {
        // Muster "0123 ": Praefixe haben 4, 8, 12 Ziffern, nie neun (jede Kette beginnt an einer Blockgrenze).
        // Formel: erste Kette = Segment mit 12 Ziffern (laengstes Praefix 6 bis 12), 1 Fund bei direktem Kontext;
        // danach ist der Kontext beendet: Summe 1.
        laufzeit("0123 ", "LANR ", 1, "1 Fund");
    }

    @Test
    void ak21_laufzeit_gueltigeLanrMitKontextwortWiederholt_einFundJeWiederholung() {
        // Baustein "LANR " + Nummer + " " (15 Zeichen). Formel: Wiederholungen
        // = (1 000 000 + 15 - 1) / 15 = 66 667 (aufgerundet, Praefix leer); je Wiederholung ein Fund.
        String baustein = "LANR " + LANR_GUELTIG + " ";
        int erwartet = (1_000_000 + baustein.length() - 1) / baustein.length();
        assertEquals(66_667, erwartet);
        laufzeit(baustein, "", erwartet, "66 667 Funde");
    }

    @Test
    void ak21_laufzeit_lanrBindestricheOhneZiffern_keinFund() {
        // Formel: Kontextwort, dann nur Trennzeichen ohne Ziffer: 0 Funde
        laufzeit("-", "LANR", 0, "0 Funde");
    }
}
