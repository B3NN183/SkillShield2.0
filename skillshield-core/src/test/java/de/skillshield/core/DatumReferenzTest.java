package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Selbsttests der unabhaengigen Datumsreferenz {@link DatumReferenz} (Feature datum-und-kontextvorrang, T-1):
 * die Vektoren aus AK-2 bis AK-5 und D-5, das Verhalten an Segmentgrenzen (Zeitraum, neun Ziffern), und ein
 * vollstaendiger Abgleich ueber alle Kalendertage 1900 bis 2099 mit {@code java.time}. Meldungen nennen nur Zahlen.
 */
class DatumReferenzTest {

    /**
     * Index des Zeitraums in {@code GUELTIGE_FORMEN}: Davor stehen zehn Einzelformen (Punkt, zweistelliges Jahr,
     * Schraegstrich, Bindestrich, ISO), der Zeitraum ist der letzte Eintrag.
     */
    private static final int INDEX_ZEITRAUM = 10;

    // ------------------------------------------------------------------ Vektoren (AK-2 bis AK-5)

    /** AK-2: alle Einzelformen sind ein Datum; der Zeitraum (letzter Eintrag) ist es als Ganzes nicht. */
    @Test
    void ak2_gueltigeFormen_sindDatum_derZeitraumIstKeinEinzelDatum() {
        List<String> formen = SynthetischeDaten.GUELTIGE_FORMEN;
        assertEquals(INDEX_ZEITRAUM + 1, formen.size(), "Zahl der Formen");
        for (int i = 0; i < INDEX_ZEITRAUM; i++) {
            assertTrue(DatumReferenz.istDatum(formen.get(i)), "Form " + i);
            assertTrue(DatumReferenz.istDatumssegment(formen.get(i), 0, formen.get(i).length()), "Segment " + i);
        }
        String zeitraum = formen.get(INDEX_ZEITRAUM);
        assertFalse(DatumReferenz.istDatum(zeitraum), "Zeitraum ist kein Einzeldatum");
        assertTrue(DatumReferenz.istDatumssegment(zeitraum, 0, zeitraum.length() - 5), "Zeitraum: Segment 12 Ziffern");
    }

    /** AK-3: Formen ohne Datumscharakter sind kein Datum. */
    @Test
    void ak3_keineDatumsform_istKeinDatum() {
        List<String> formen = SynthetischeDaten.KEINE_DATUMSFORM;
        for (int i = 0; i < formen.size(); i++) {
            String s = formen.get(i);
            assertFalse(DatumReferenz.istDatum(s), "Form mit Listenindex " + i);
            assertFalse(DatumReferenz.istDatumssegment(s, 0, s.length()), "Segment, Listenindex " + i);
        }
    }

    /** AK-4: kalendarisch unmoegliche Werte und Jahre ausserhalb 1900 bis 2099 sind kein Datum. */
    @Test
    void ak4_kalenderungueltig_istKeinDatum() {
        List<String> werte = SynthetischeDaten.KALENDERUNGUELTIG;
        for (int i = 0; i < werte.size(); i++) {
            assertFalse(DatumReferenz.istDatum(werte.get(i)), "Wert mit Listenindex " + i);
        }
        assertFalse(DatumReferenz.istDatum(SynthetischeDaten.SCHALTTAG_1900), "29.02. in 1900");
        for (String s : SynthetischeDaten.SCHALTTAGE_GUELTIG) {
            assertTrue(DatumReferenz.istDatum(s), "Schaltjahr");
        }
    }

    /** AK-5: Jahresgrenzen. */
    @Test
    void ak5_jahresgrenzen() {
        for (String s : SynthetischeDaten.JAHRESGRENZEN_GUELTIG) {
            assertTrue(DatumReferenz.istDatum(s), "Grenze gueltig");
        }
        for (String s : SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG) {
            assertFalse(DatumReferenz.istDatum(s), "Grenze ungueltig");
        }
    }

    /** Zweistelliges Jahr: 29.02. nur, wenn 19jj und 20jj Schaltjahre sind (Kap. 13 Punkt 7). */
    @Test
    void zweistelligesJahr_schalttagNurInBeidenJahrhunderten() {
        assertFalse(DatumReferenz.istDatum(SynthetischeDaten.SCHALTTAG_ZWEISTELLIG_NULL), "29.02.00");
        assertTrue(DatumReferenz.istDatum("29.02.04"), "29.02.04");
        assertTrue(DatumReferenz.istDatum("29.02.96"), "29.02.96");
        assertFalse(DatumReferenz.istDatum("29.02.03"), "29.02.03");
        assertTrue(DatumReferenz.istDatum("28.02.00"), "28.02.00");
    }

    /** D-2: Formen, die ausdruecklich kein Datum sind, auch wenn Tag, Monat und Jahr moeglich waeren. */
    @Test
    void d2_trennerUndZeichen_ausserhalbDerFormen() {
        assertFalse(DatumReferenz.istDatum("14,03,1958"), "Komma");
        assertFalse(DatumReferenz.istDatum("14.03.1958."), "Punkt am Ende");
        assertFalse(DatumReferenz.istDatum(".14.03.1958"), "Punkt am Anfang");
        assertFalse(DatumReferenz.istDatum("14..03.1958"), "zwei Trenner");
        assertFalse(DatumReferenz.istDatum("14.03"), "nur zwei Bloecke");
        assertFalse(DatumReferenz.istDatum(SynthetischeDaten.DATUM_PUNKT + ".1"), "vier Bloecke");
        assertFalse(DatumReferenz.istDatum("0" + SynthetischeDaten.DATUM_PUNKT), "dreistelliger Tag");
        assertFalse(DatumReferenz.istDatum(SynthetischeDaten.DATUM_PUNKT + "0"), "fuenfstelliges Jahr");
        assertFalse(DatumReferenz.istDatum("14.03.195"), "dreistelliges Jahr");
        assertFalse(DatumReferenz.istDatum("1958-3-14"), "ISO mit einstelligem Monat");
        assertFalse(DatumReferenz.istDatum("1958-03-4"), "ISO mit einstelligem Tag");
        assertFalse(DatumReferenz.istDatum(""), "leer");
        assertFalse(DatumReferenz.istDatum("B 14.03.1958"), "Buchstabe davor");
    }

    /** D-2: Leerzeichenartige Zeichen sind als Trenner kein Datum (U+00A0, U+2009, U+202F wie das Leerzeichen). */
    @Test
    void d2_leerzeichenartigeTrenner_sindKeinDatum() {
        int[] codepunkte = {0x20, 0xA0, 0x2009, 0x202F};
        for (int codepunkt : codepunkte) {
            char trenner = (char) codepunkt;
            String s = SynthetischeDaten.DATUM_PUNKT.replace('.', trenner);
            assertFalse(DatumReferenz.istDatum(s), "Trenner mit Codepunkt " + Integer.toHexString(codepunkt));
            assertFalse(DatumReferenz.istDatumssegment(s, 0, s.length()),
                    "Segment, Trenner mit Codepunkt " + Integer.toHexString(codepunkt));
        }
    }

    /** D-5: Mit weiterem Block ueber einen Gruppentrenner ist die Kette als Ganzes kein Datum. */
    @Test
    void d5_datumMitWeiteremBlock_istAlsGanzesKeinDatum() {
        for (String s : SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK) {
            assertFalse(DatumReferenz.istDatumssegment(s, 0, s.length()), "Kette");
        }
        String s = SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT;
        int datumEnde = SynthetischeDaten.DATUM_PUNKT.length();
        assertTrue(DatumReferenz.istDatumssegment(s, 0, datumEnde), "Datum vor Doppelpunkt");
        String b = SynthetischeDaten.BUCHSTABE_UND_DATUM;
        assertFalse(DatumReferenz.istDatumssegment(b, 0, b.length()), "Buchstabe plus Datum");
        assertTrue(DatumReferenz.istDatumssegment(b, 2, b.length()), "das Datum allein");
    }

    // ------------------------------------------------------------------ Segmente und Zeitraum

    /** Ungueltige Bereiche und Segmente mit genau neun Ziffern sind nie ein Datumssegment (A-2). */
    @Test
    void ungueltigeBereiche_undNeunZiffern() {
        String s = SynthetischeDaten.DATUM_PUNKT;
        assertFalse(DatumReferenz.istDatumssegment(s, -1, 4), "Anfang negativ");
        assertFalse(DatumReferenz.istDatumssegment(s, 3, 3), "leer");
        assertFalse(DatumReferenz.istDatumssegment(s, 4, 3), "umgekehrt");
        assertFalse(DatumReferenz.istDatumssegment(s, 0, s.length() + 1), "ueber das Ende");
        assertEquals(8, DatumReferenz.ziffern(s, 0, s.length()), "ein Datum hat acht Ziffern");
        String z = SynthetischeDaten.ZEITRAUM;
        int minus = z.indexOf('-');
        for (int ende = minus + 1; ende <= z.length(); ende++) {
            int ziffern = DatumReferenz.ziffern(z, 0, ende);
            assertEquals(ziffern != 9, DatumReferenz.istDatumssegment(z, 0, ende), "Zeitraum, Ende " + ende);
        }
    }

    /** Der Zeitraum: Praefixe hinter dem Strich sind Datumssegmente, Praefixe davor nicht. */
    @Test
    void zeitraum_praefixeHinterDemStrich() {
        String z = SynthetischeDaten.ZEITRAUM;
        int minus = z.indexOf('-');
        assertTrue(DatumReferenz.istDatumssegment(z, 0, minus), "bis vor den Strich: das erste Datum allein");
        assertTrue(DatumReferenz.istDatum(z.substring(0, minus)), "erstes Datum");
        assertFalse(DatumReferenz.istZeitraumAb(z, 0, minus), "Ende vor dem Strich: noch kein Zeitraum");
        assertTrue(DatumReferenz.istZeitraumAb(z, 0, minus + 1), "Ende hinter dem Strich");
        assertTrue(DatumReferenz.istZeitraumAb(z, 0, minus + 2), "ein Zeichen hinter dem Strich");
        assertTrue(DatumReferenz.istZeitraumAb(z, 0, z.length()), "ganzer Zeitraum");
    }

    /** Zeitraum: nur mit zwei Daten der Punktform mit vierstelligem Jahr, ohne Leerraum, Kette danach beendet. */
    @Test
    void zeitraum_formUndKettenende() {
        String z = SynthetischeDaten.ZEITRAUM;
        int ende = z.length();
        assertTrue(DatumReferenz.istZeitraumAb(z + " x", 0, 14), "Wort dahinter");
        assertTrue(DatumReferenz.istZeitraumAb(z + "-", 0, 14), "Strich am Ende ohne Ziffer");
        assertTrue(DatumReferenz.istZeitraumAb(z + ", 5", 0, 14), "Komma ist kein Gruppentrenner");
        assertFalse(DatumReferenz.istZeitraumAb(z + " 5", 0, 14), "Leerzeichen und Ziffer: die Kette geht weiter");
        assertFalse(DatumReferenz.istZeitraumAb(z + ".5", 0, 14), "Punkt und Ziffer");
        assertFalse(DatumReferenz.istZeitraumAb(z + "/5", 0, 14), "Schraegstrich und Ziffer");
        assertFalse(DatumReferenz.istZeitraumAb(z + "-5", 0, 14), "Strich und Ziffer");
        assertFalse(DatumReferenz.istZeitraumAb(z + "5", 0, 14), "weitere Ziffer macht das Jahr fuenfstellig");
        assertFalse(DatumReferenz.istZeitraumAb("01.03.2026 - 31.03.2026", 0, 14), "Leerraum um den Strich");
        assertFalse(DatumReferenz.istZeitraumAb("01.03.26-31.03.2026", 0, 14), "erstes Jahr zweistellig");
        assertFalse(DatumReferenz.istZeitraumAb("01.03.2026-31.03.26", 0, 14), "zweites Jahr zweistellig");
        assertFalse(DatumReferenz.istZeitraumAb("01.03.2026-31.04.2026", 0, 14), "zweites Datum ungueltig");
        assertFalse(DatumReferenz.istZeitraumAb("31.04.2026-01.05.2026", 0, 14), "erstes Datum ungueltig");
        assertFalse(DatumReferenz.istZeitraumAb("01/03/2026-31/03/2026", 0, 14), "Schraegstrich");
        assertFalse(DatumReferenz.istZeitraumAb(z, 0, ende + 1), "Ende hinter dem zweiten Datum");
    }

    /** Die Grenze aus A-2: Praefixe mit genau neun Ziffern sind kein Datumssegment. */
    @Test
    void bekannteGrenze_zeitraumMitNeunZiffernPraefix() {
        for (String z : List.of(SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN, SynthetischeDaten.ZEITRAUM_EINSTELLIG)) {
            int minus = z.indexOf('-');
            int neun = -1;
            for (int ende = minus + 1; ende <= z.length(); ende++) {
                if (DatumReferenz.ziffern(z, 0, ende) == 9) {
                    neun = ende;
                }
            }
            assertTrue(neun > 0, "ein Praefix mit neun Ziffern existiert");
            assertFalse(DatumReferenz.istDatumssegment(z, 0, neun), "Neun-Ziffern-Praefix");
        }
        String nachSpaces = SynthetischeDaten.DATUM_PUNKT + " " + "1";
        assertFalse(DatumReferenz.istDatumssegment(nachSpaces, 0, nachSpaces.length()), "Datum plus eine Ziffer");
    }

    // ------------------------------------------------------------------ Abgleich mit java.time

    /** Alle Kalendertage 1900 bis 2099 in jeder Form sind ein Datum; der Tag davor und danach nicht. */
    @Test
    void alleKalendertage_inAllenFormen() {
        int tage = 0;
        for (LocalDate d = LocalDate.of(1900, 1, 1); d.getYear() <= 2099; d = d.plusDays(1)) {
            int t = d.getDayOfMonth();
            int m = d.getMonthValue();
            int j = d.getYear();
            String tt = pad(t);
            String mm = pad(m);
            assertTrue(DatumReferenz.istDatum(tt + "." + mm + "." + j), "TT.MM.JJJJ");
            assertTrue(DatumReferenz.istDatum(t + "." + m + "." + j), "T.M.JJJJ");
            assertTrue(DatumReferenz.istDatum(t + "/" + m + "/" + j), "T/M/JJJJ");
            assertTrue(DatumReferenz.istDatum(tt + "-" + mm + "-" + j), "TT-MM-JJJJ");
            assertTrue(DatumReferenz.istDatum(j + "-" + mm + "-" + tt), "ISO");
            assertEquals(zweistelligErwartet(t, m, j % 100),
                    DatumReferenz.istDatum(tt + "." + mm + "." + pad(j % 100)), "TT.MM.JJ");
            assertFalse(DatumReferenz.istDatum(tt + "." + mm + "-" + j), "gemischte Trenner");
            assertFalse(DatumReferenz.istDatum(tt + " " + mm + " " + j), "Leerzeichen");
            assertFalse(DatumReferenz.istDatum(j + "." + mm + "." + tt), "Jahr zuerst mit Punkt");
            assertFalse(DatumReferenz.istDatum(tt + "/" + mm + "/" + pad(j % 100)), "zweistellig mit Schraegstrich");
            tage++;
        }
        assertEquals(SynthetischeDaten.KALENDERTAGE_1900_BIS_2099, tage, "Zahl der Kalendertage 1900 bis 2099");
    }

    private static String pad(int zahl) {
        return zahl < 10 ? "0" + zahl : String.valueOf(zahl);
    }

    /** Unabhaengig formuliert: das Datum muss in 19jj und in 20jj existieren. */
    private static boolean zweistelligErwartet(int tag, int monat, int jj) {
        return tag <= YearMonth.of(1900 + jj, monat).lengthOfMonth()
                && tag <= YearMonth.of(2000 + jj, monat).lengthOfMonth();
    }

    /** Tag 0 bis 40 und Monat 0 bis 14 in einem Schaltjahr, einem normalen Jahr und ausserhalb des Bereichs. */
    @Test
    void ungueltigeTageUndMonate_gegenYearMonth() {
        int geprueft = 0;
        for (int jahr : new int[] {1899, 1900, 2000, 2023, 2024, 2099, 2100}) {
            for (int monat = 0; monat <= 14; monat++) {
                for (int tag = 0; tag <= 40; tag++) {
                    boolean erwartet = jahr >= 1900 && jahr <= 2099 && monat >= 1 && monat <= 12 && tag >= 1
                            && tag <= YearMonth.of(jahr, monat).lengthOfMonth();
                    String s = pad(tag) + "." + pad(monat) + "." + jahr;
                    assertEquals(erwartet, DatumReferenz.istDatum(s), "Tag " + tag + " Monat " + monat + " Jahr "
                            + jahr);
                    geprueft++;
                }
            }
        }
        assertEquals(4305, geprueft, "7 Jahre, 15 Monatswerte, 41 Tageswerte");
    }
}
