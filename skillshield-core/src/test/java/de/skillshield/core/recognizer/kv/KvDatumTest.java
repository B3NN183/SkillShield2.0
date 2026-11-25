package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumReferenz;
import de.skillshield.core.SynthetischeDaten;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Einzeltests von {@link KvDatum} (Feature datum-und-kontextvorrang, T-2, Teil A): jede Form, jede Grenze aus D-3,
 * Schaltjahre, zweistelliges Jahr einschliesslich {@code 29.02.00}, Zeitraum und Kettenende, Buchstabe vorn, das
 * Neun-Ziffern-Segment wird nicht geprueft, der Vorgriff bricht am Textende ab.
 *
 * <p>Massstab ist die unabhaengige {@link DatumReferenz} (mit {@code java.time}, nur im Test): {@code KvDatum} rechnet
 * ohne {@code java.time} und muss auf jedem Ausschnitt exakt dasselbe Ergebnis liefern. Das wird ueber alle
 * Kalendertage von 1900 bis 2099 in allen Formen, ueber alle Werte rund um die Kalendergrenzen, ueber alle Zeitraeume
 * einer Stichprobe, ueber die Vektoren aus {@link SynthetischeDaten} und ueber zufaellige Texte mit festem Seed
 * belegt. Meldungen nennen nur Zahlen.
 */
class KvDatumTest {

    private static final char NBSP = (char) 0xA0;
    private static final char DUENNES = (char) 0x2009;
    private static final char SCHMAL = (char) 0x202F;

    private static boolean produkt(String text, int start, int end) {
        return KvDatum.istDatumssegment(text, start, end);
    }

    private static boolean referenz(String text, int start, int end) {
        return DatumReferenz.istDatumssegment(text, start, end);
    }

    /** Das ganze Segment {@code [0, laenge)}. */
    private static boolean ganz(String text) {
        return produkt(text, 0, text.length());
    }

    private static String zahl(int wert, boolean mitNull) {
        return mitNull && wert < 10 ? "0" + wert : String.valueOf(wert);
    }

    // ------------------------------------------------------------------ Formen und Vektoren

    @Test
    void konstanten_jahresgrenzenWieDieReferenz() {
        assertEquals(DatumReferenz.JAHR_MIN, KvDatum.JAHR_MIN, "kleinstes Jahr");
        assertEquals(DatumReferenz.JAHR_MAX, KvDatum.JAHR_MAX, "groesstes Jahr");
    }

    /** AK-2: jede gueltige Form ist als ganzes Segment ein Datum bzw. (Zeitraum) bis zum Ende des zweiten Datums. */
    @Test
    void gueltigeFormen_sindDatumssegmente() {
        for (String form : SynthetischeDaten.GUELTIGE_FORMEN) {
            assertTrue(ganz(form), "Form " + SynthetischeDaten.GUELTIGE_FORMEN.indexOf(form));
        }
        assertEquals(11, SynthetischeDaten.GUELTIGE_FORMEN.size(), "Zahl der Formen");
    }

    /** AK-3: Formen ausserhalb von D-2 sind kein Datum. */
    @Test
    void keineDatumsform_istKeinDatumssegment() {
        for (String form : SynthetischeDaten.KEINE_DATUMSFORM) {
            assertFalse(ganz(form), "Form " + SynthetischeDaten.KEINE_DATUMSFORM.indexOf(form));
        }
        assertEquals(7, SynthetischeDaten.KEINE_DATUMSFORM.size(), "Zahl der Formen");
    }

    /** AK-4, AK-5: Kalendergrenzen, Schaltjahre, Jahresgrenzen. */
    @Test
    void kalender_grenzen_schaltjahre_jahresgrenzen() {
        for (String s : SynthetischeDaten.KALENDERUNGUELTIG) {
            assertFalse(ganz(s), "ungueltig " + SynthetischeDaten.KALENDERUNGUELTIG.indexOf(s));
        }
        for (String s : SynthetischeDaten.SCHALTTAGE_GUELTIG) {
            assertTrue(ganz(s), "Schaltjahr " + SynthetischeDaten.SCHALTTAGE_GUELTIG.indexOf(s));
        }
        assertFalse(ganz(SynthetischeDaten.SCHALTTAG_1900), "1900 ist kein Schaltjahr");
        for (String s : SynthetischeDaten.JAHRESGRENZEN_GUELTIG) {
            assertTrue(ganz(s), "Jahresgrenze gueltig " + SynthetischeDaten.JAHRESGRENZEN_GUELTIG.indexOf(s));
        }
        for (String s : SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG) {
            assertFalse(ganz(s), "Jahresgrenze ungueltig " + SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG.indexOf(s));
        }
    }

    /** Monatslaengen: der letzte Tag ist ein Datum, der Tag danach nicht; alle zwoelf Monate, Schalt-/Normaljahr. */
    @Test
    void monatslaengen_letzterTagGueltig_naechsterTagNicht() {
        int[] normal = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int geprueft = 0;
        for (int jahr : new int[] {1900, 1999, 2000, 2024, 2025, 2099}) {
            boolean schalt = jahr % 4 == 0 && (jahr % 100 != 0 || jahr % 400 == 0);
            for (int monat = 1; monat <= 12; monat++) {
                int laenge = normal[monat - 1] + (monat == 2 && schalt ? 1 : 0);
                String letzter = zahl(laenge, true) + "." + zahl(monat, true) + "." + jahr;
                String danach = zahl(laenge + 1, true) + "." + zahl(monat, true) + "." + jahr;
                assertTrue(ganz(letzter), "letzter Tag Monat " + monat + " Jahr " + jahr);
                assertFalse(ganz(danach), "Tag danach Monat " + monat + " Jahr " + jahr);
                geprueft += 2;
            }
        }
        assertEquals(144, geprueft, "Zahl der Pruefungen");
    }

    /** Zweistelliges Jahr (nur mit Punkt): 29.02.JJ nur, wenn in beiden Jahrhunderten ein Schaltjahr. */
    @Test
    void zweistelligesJahr_schalttagNurInBeidenJahrhunderten() {
        int gueltig = 0;
        for (int jj = 0; jj < 100; jj++) {
            boolean erwartet = jj % 4 == 0 && jj != 0;
            String text = "29.02." + zahl(jj, true);
            assertEquals(erwartet, ganz(text), "29.02. Jahr " + jj);
            gueltig += erwartet ? 1 : 0;
        }
        assertEquals(24, gueltig, "Jahre 04 bis 96 in Vierer-Schritten");
        assertFalse(ganz("29.02." + "00"), "29.02.00 ist kein Datum");
        assertTrue(ganz("28.02." + "00"), "28.02.00 ist ein Datum");
        assertFalse(ganz("29/02/" + "04"), "zweistelliges Jahr nur mit Punkt");
        assertFalse(ganz("29-02-" + "04"), "zweistelliges Jahr nur mit Punkt");
    }

    /** D-2 b bis d: der Trenner ist zweimal derselbe; gemischte, fremde und fehlende Trenner sind kein Datum. */
    @Test
    void trenner_gleichUndErlaubt() {
        assertTrue(ganz("14/03/1958"), "Schraegstrich");
        assertTrue(ganz("14-03-1958"), "Bindestrich");
        assertTrue(ganz("1958-03-14"), "ISO");
        assertFalse(ganz("14.03/1958"), "gemischt");
        assertFalse(ganz("14.03-1958"), "gemischt");
        assertFalse(ganz("14 03 1958"), "Leerzeichen");
        assertFalse(ganz("14:03:1958"), "Doppelpunkt");
        assertFalse(ganz("14,03,1958"), "Komma");
        assertFalse(ganz("1958.03.14"), "Jahr zuerst mit Punkt");
        assertFalse(ganz("1958/03/14"), "Jahr zuerst mit Schraegstrich");
        assertFalse(ganz("1958-3-14"), "ISO nur mit zwei Stellen");
        assertFalse(ganz("1958-03-4"), "ISO nur mit zwei Stellen");
        assertFalse(ganz("14031958"), "ohne Trenner");
        assertFalse(ganz("14.03."), "unvollstaendig");
        assertFalse(ganz("14.03"), "nur zwei Bloecke");
        assertFalse(ganz("14..1958"), "leerer Block");
        assertFalse(ganz(".03.1958"), "Trenner am Anfang");
        assertFalse(ganz("14.03.1958."), "Trenner am Ende");
        // Texte mit neun Ziffern entstehen zur Laufzeit (Wachter fuer Testdaten, Architektur Kap. 6 Punkt 3).
        String datum = SynthetischeDaten.DATUM_PUNKT;
        assertFalse(ganz(datum + ".1"), "vier Bloecke");
        assertFalse(ganz("14.03.195"), "dreistelliges Jahr");
        assertFalse(ganz(datum + "0"), "fuenfstelliges Jahr");
        assertFalse(ganz("1" + datum), "dreistelliger Tag");
        assertFalse(ganz("14" + ".003." + "1958"), "dreistelliger Monat");
    }

    /** Buchstabe vorn (KVNR-Buchstabenform): nie ein Datum. */
    @Test
    void buchstabeVorn_istNieEinDatum() {
        String text = SynthetischeDaten.BUCHSTABE_UND_DATUM;
        assertFalse(ganz(text), "Buchstabe, Leerzeichen und Datum");
        assertTrue(produkt(text, 2, text.length()), "das Datum allein");
        assertFalse(produkt("B14.03.1958", 0, 11), "Buchstabe direkt am Datum");
        assertFalse(produkt("x" + SynthetischeDaten.ZEITRAUM, 0, 1 + SynthetischeDaten.ZEITRAUM.length()),
                "Buchstabe vor dem Zeitraum");
    }

    // ------------------------------------------------------------------ Zeitraum und Kettenende

    /** AK-10, D-2 e: Zeitraum; das Segment endet hinter dem Bindestrich und hoechstens am Ende des zweiten Datums. */
    @Test
    void zeitraum_endeHinterBindestrichBisEndeDesZweitenDatums() {
        String z = SynthetischeDaten.ZEITRAUM;
        int minus = z.indexOf('-');
        int ende = z.length();
        for (int end = 1; end <= ende; end++) {
            // Ein Segment mit genau neun Ziffern ist nie ein Datum (A-2): Ende 12 ("...-3").
            // Ende 8: "01.03.20" ist ein Datum mit zweistelligem Jahr (die Segmentwahl des Scanners endet dort nie);
            // Ende 10 (vor dem Bindestrich) ist das erste Datum allein.
            boolean erwartet = end == 8 || end == minus || end > minus && DatumReferenz.ziffern(z, 0, end) != 9;
            assertEquals(erwartet, produkt(z, 0, end), "Ende " + end);
        }
        assertEquals(10, minus, "Bindestrich hinter dem ersten Datum");
        assertFalse(produkt(z + "x", 0, ende + 1), "Segment ueber das zweite Datum hinaus");
        assertTrue(produkt(z + "x", 0, ende), "Buchstabe hinter dem zweiten Datum beendet die Kette");
    }

    /** Das praktische Segment des Scanners: die ersten zwoelf Ziffern des Zeitraums (16 Ziffern) sind ein Datum. */
    @Test
    void zeitraum_praefixMitZwoelfZiffern_istDatumssegment() {
        String z = SynthetischeDaten.ZEITRAUM;
        int zwoelf = z.indexOf("31.03") + 5;
        assertEquals(12, DatumReferenz.ziffern(z, 0, zwoelf), "zwoelf Ziffern");
        assertTrue(produkt(z, 0, zwoelf), "Praefix mit zwoelf Ziffern");
    }

    /** Eine Kette, die hinter dem zweiten Datum weitergeht (Gruppentrenner und Ziffer), ist kein Zeitraum. */
    @Test
    void zeitraum_kettegehtWeiter_istKeinDatumssegment() {
        String z = SynthetischeDaten.ZEITRAUM;
        int zwoelf = z.indexOf("31.03") + 5;
        for (String trenner : List.of(" ", "-", ".", "/", String.valueOf(NBSP), String.valueOf(DUENNES),
                String.valueOf(SCHMAL))) {
            assertFalse(produkt(z + trenner + "1", 0, zwoelf), "Trenner " + (int) trenner.charAt(0) + " und Ziffer");
        }
        List<String> enden = List.of(" ", ",", ";", ":", "-", ".", "/", "x", "\n", " x", ". x", " -");
        for (String ende : enden) {
            assertTrue(produkt(z + ende, 0, zwoelf), "Kette endet: " + ende.length() + " Zeichen");
        }
        assertEquals(12, enden.size(), "Zahl der Kettenenden");
    }

    /** Zeitraum mit ungueltigem Datum, mit Leerraum am Bindestrich oder aus mehr als zwei Daten ist keiner. */
    @Test
    void zeitraum_ungueltigeFormen() {
        assertFalse(ganz("01.03.2026-31.04.2026"), "zweites Datum ungueltig");
        assertFalse(ganz("31.04.2026-01.05.2026"), "erstes Datum ungueltig");
        assertFalse(ganz("01.03.2026 - 31.03.2026"), "Leerraum am Bindestrich");
        assertFalse(ganz("01.03.2026-31.03.26"), "zweites Datum mit zweistelligem Jahr");
        assertFalse(ganz("01.03.26-31.03.2026"), "erstes Datum mit zweistelligem Jahr");
        assertFalse(ganz("01/03/2026-31/03/2026"), "Schraegstrich-Daten als Zeitraum");
        assertFalse(ganz("01.03.2026--31.03.2026"), "zwei Bindestriche");
        assertFalse(ganz("01.03.2026-31.03.20266"), "fuenfstelliges Jahr hinten");
        assertTrue(ganz("1.3.2026-3.4.2026"), "ein- und zweistellige Werte");
    }

    /** Neun-Ziffern-Regel (A-2): Ein Segment mit genau neun Ziffern wird nie als Datum gewertet. */
    @Test
    void neunZiffern_werdenNieGeprueft() {
        String neun = SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN;
        int ziffern = DatumReferenz.ziffern(neun, 0, neun.indexOf('-') + 2);
        assertEquals(9, ziffern, "Zeitraum und eine Ziffer: neun Ziffern");
        assertFalse(produkt(neun, 0, neun.indexOf('-') + 2), "Praefix mit neun Ziffern");
        assertTrue(produkt(neun, 0, neun.indexOf('-') + 1), "Praefix mit acht Ziffern bis hinter den Bindestrich");
        String einstellig = SynthetischeDaten.ZEITRAUM_EINSTELLIG;
        int neunEnde = einstellig.indexOf("15.3") + 4;
        assertEquals(9, DatumReferenz.ziffern(einstellig, 0, neunEnde), "6 + 2 + 1 = neun Ziffern");
        assertFalse(produkt(einstellig, 0, neunEnde), "Praefix mit neun Ziffern");
    }

    /** Der Vorgriff bricht am Textende ab: keine Ausnahme, kein Ergebnis fuer abgeschnittene Texte. */
    @Test
    void vorgriff_brichtAmTextendeAb() {
        String z = SynthetischeDaten.ZEITRAUM;
        for (int laenge = 1; laenge <= z.length(); laenge++) {
            String abgeschnitten = z.substring(0, laenge);
            for (int end = 1; end <= laenge; end++) {
                assertEquals(referenz(abgeschnitten, 0, end), produkt(abgeschnitten, 0, end),
                        "Textlaenge " + laenge + " Ende " + end);
            }
        }
        assertFalse(produkt(z.substring(0, 11), 0, 11), "Bindestrich am Textende: zweites Datum fehlt");
        assertFalse(produkt(z.substring(0, 15), 0, 15), "zweites Datum abgeschnitten");
    }

    /** Ungueltige Bereiche sind kein Datum; {@code null} wirft nur die feste Meldung ohne Text. */
    @Test
    void bereiche_ungueltig_undNull() {
        String text = SynthetischeDaten.DATUM_PUNKT;
        assertFalse(produkt(text, -1, 5), "Anfang negativ");
        assertFalse(produkt(text, 3, 3), "leer");
        assertFalse(produkt(text, 5, 3), "umgekehrt");
        assertFalse(produkt(text, 0, text.length() + 1), "Ende hinter dem Text");
        assertFalse(produkt("", 0, 0), "leerer Text");
        assertTrue(produkt(text, 0, text.length()), "Kontrolle");
        NullPointerException npe = assertThrows(NullPointerException.class, () -> produkt(null, 0, 1));
        assertTrue("text".equals(npe.getMessage()), "feste Meldung ohne Text");
    }

    /** Konstanter Aufwand: Ein Datum mit einer Million angehaengter Ziffern wird nie ganz gelesen. */
    @Test
    void lange_ziffernfolgen_werdenNichtGanzGelesen() {
        String datum = SynthetischeDaten.DATUM_PUNKT;
        String lang = datum + " " + "1".repeat(1_000_000);
        assertTrue(produkt(lang, 0, datum.length()), "das Datum am Anfang");
        assertFalse(produkt(lang, 0, 20), "Segment ueber das Datum hinaus");
        assertFalse(produkt(lang, 0, lang.length()), "Segment ueber den ganzen Text");
        String zeitraum = SynthetischeDaten.ZEITRAUM + "1".repeat(1_000_000);
        // Das Segment endet hinter dem Zeitraum-Strich; hinter dem zweiten Datum geht die Kette mit Ziffern weiter,
        // also ist es kein Zeitraum nach D-2 (e): beide, Produkt und Referenz, lehnen ab.
        assertFalse(produkt(zeitraum, 0, 17), "Zeitraum mit Ziffernfolge dahinter");
        assertFalse(referenz(zeitraum, 0, 17), "Zeitraum mit Ziffernfolge dahinter, Referenz");
        String nurZiffern = "1".repeat(1_000_000);
        assertFalse(produkt(nurZiffern, 0, 12), "nur Ziffern");
        assertFalse(produkt(nurZiffern, 0, nurZiffern.length()), "ein Segment von einer Million Zeichen");
        String schluss = "01.03.2026-" + "1".repeat(1_000_000);
        assertFalse(produkt(schluss, 0, 11), "zweites Datum fehlt, Ziffernblock zu lang");
    }

    // ------------------------------------------------------------------ Vergleich mit der Referenz

    // Die Zaehler sind absichtlich nicht synchronisiert: Das Projekt schaltet die parallele Ausfuehrung von JUnit
    // nicht ein (weder in junit-platform.properties noch im pom), die Tests dieser Klasse laufen nacheinander, und
    // jeder Test setzt beide Zaehler zu Beginn zurueck. Wer Parallelitaet einschaltet, muss sie ersetzen.
    private static long verglichen;
    private static long davonDatum;

    private static void vergleiche(String text, int start, int end) {
        boolean r = referenz(text, start, end);
        assertEquals(r, produkt(text, start, end), "Abweichung bei Textlaenge " + text.length() + " Bereich " + start
                + " bis " + end);
        verglichen++;
        davonDatum += r ? 1 : 0;
    }

    /** Alle Segmente eines Textes, die mit dem Textanfang beginnen. */
    private static void vergleicheAlleEnden(String text) {
        for (int end = 1; end <= text.length(); end++) {
            vergleiche(text, 0, end);
        }
    }

    /**
     * Alle Kalendertage von 1900 bis 2099 in allen Formen (Punkt mit vier- und zweistelligem Jahr, Schraegstrich,
     * Bindestrich, ISO; Tag und Monat mit und ohne fuehrende Null): {@code KvDatum} entspricht der Referenz.
     */
    @Test
    void alleKalendertage_inAllenFormen_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        int tage = 0;
        java.time.LocalDate tag = java.time.LocalDate.of(1900, 1, 1);
        java.time.LocalDate ende = java.time.LocalDate.of(2099, 12, 31);
        while (!tag.isAfter(ende)) {
            int t = tag.getDayOfMonth();
            int m = tag.getMonthValue();
            int j = tag.getYear();
            for (int nullTag = 0; nullTag < 2; nullTag++) {
                for (int nullMonat = 0; nullMonat < 2; nullMonat++) {
                    String tt = zahl(t, nullTag == 1);
                    String mm = zahl(m, nullMonat == 1);
                    for (char trenner : new char[] {'.', '/', '-'}) {
                        String s = tt + trenner + mm + trenner + j;
                        vergleiche(s, 0, s.length());
                    }
                    String zweistellig = tt + "." + mm + "." + zahl(j % 100, true);
                    vergleiche(zweistellig, 0, zweistellig.length());
                }
            }
            String iso = j + "-" + zahl(m, true) + "-" + zahl(t, true);
            vergleiche(iso, 0, iso.length());
            tage++;
            tag = tag.plusDays(1);
        }
        assertEquals(SynthetischeDaten.KALENDERTAGE_1900_BIS_2099, tage, "Zahl der Kalendertage");
        // Je Tag 4 Schreibweisen von Tag und Monat, je 3 + 1 Formen, dazu ISO: 73049 * (16 + 1).
        assertEquals(73_049L * 17L, verglichen, "Zahl der Vergleiche");
        // Gueltig sind: Punkt, Schraegstrich, Bindestrich und ISO (je alle Tage) und das zweistellige Jahr in 100
        // Jahren. Istwert 1 241 829 Datumssegmente (die uebrigen sind 29.02. mit zweistelligem Jahr); verlangt:
        // mindestens 70 %.
        assertTrue(davonDatum >= 860_000L, "Zahl der Datumssegmente " + davonDatum);
    }

    /** Alle Werte rund um die Kalendergrenzen: Tag 0 bis 32, Monat 0 bis 13, Jahre 1895 bis 2105, jeder Trenner. */
    @Test
    void werteRundUmDieKalendergrenzen_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        for (int jahr = 1895; jahr <= 2105; jahr++) {
            for (int monat = 0; monat <= 13; monat++) {
                for (int tag = 0; tag <= 32; tag++) {
                    for (char trenner : new char[] {'.', '/', '-'}) {
                        String s = zahl(tag, tag % 2 == 0) + trenner + zahl(monat, monat % 2 == 0) + trenner + jahr;
                        vergleiche(s, 0, s.length());
                    }
                }
            }
        }
        for (int jj = 0; jj < 100; jj++) {
            for (int monat = 0; monat <= 13; monat++) {
                for (int tag = 0; tag <= 32; tag++) {
                    String s = zahl(tag, true) + "." + zahl(monat, true) + "." + zahl(jj, true);
                    vergleiche(s, 0, s.length());
                    String iso = (1900 + jj) + "-" + zahl(monat, true) + "-" + zahl(tag, true);
                    vergleiche(iso, 0, iso.length());
                }
            }
        }
        assertEquals(211L * 14L * 33L * 3L + 100L * 14L * 33L * 2L, verglichen, "Zahl der Vergleiche");
        // Istwert 292 195 Datumssegmente; verlangt: mindestens 70 %.
        assertTrue(davonDatum >= 200_000L, "Zahl der Datumssegmente " + davonDatum);
    }

    /** Zeitraeume: jedes Anfangsdatum mit drei zweiten Daten, an allen Endpositionen und mit Kettenenden dahinter. */
    @Test
    void zeitraeume_alleEndpositionenUndKettenenden_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        List<String> enden = List.of("", " ", ",", " 1", "-1", ".5", "/1", "x", ". 1", " -");
        java.time.LocalDate tag = java.time.LocalDate.of(1900, 1, 1);
        java.time.LocalDate letzter = java.time.LocalDate.of(2099, 12, 31);
        int index = 0;
        while (!tag.isAfter(letzter)) {
            for (int abstand : new int[] {0, 31, 400}) {
                java.time.LocalDate zweites = tag.plusDays(abstand);
                String z = punkt(tag) + "-" + punkt(zweites);
                String ende = enden.get(index++ % enden.size());
                String text = z + ende;
                for (int end = 1; end <= text.length(); end++) {
                    vergleiche(text, 0, end);
                }
            }
            tag = tag.plusDays(1);
        }
        assertEquals(10, enden.size(), "Zahl der Kettenenden");
        // Istwerte: 4 952 721 Vergleiche, davon 1 750 512 Datumssegmente; verlangt: mindestens 70 %.
        assertTrue(verglichen >= 3_400_000L, "Zahl der Vergleiche " + verglichen);
        assertTrue(davonDatum >= 1_200_000L, "Zahl der Datumssegmente " + davonDatum);
        for (String ende : enden) {
            String text = SynthetischeDaten.ZEITRAUM + ende;
            vergleicheAlleEnden(text);
        }
    }

    private static String punkt(java.time.LocalDate d) {
        return zahl(d.getDayOfMonth(), true) + "." + zahl(d.getMonthValue(), true) + "." + d.getYear();
    }

    /** Alle Vektoren aus {@link SynthetischeDaten}, jeweils mit allen Endpositionen und verschiedenen Nachbarn. */
    @Test
    void vektorenAusSynthetischeDaten_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        List<String> vektoren = new ArrayList<>();
        vektoren.addAll(SynthetischeDaten.GUELTIGE_FORMEN);
        vektoren.addAll(SynthetischeDaten.KEINE_DATUMSFORM);
        vektoren.addAll(SynthetischeDaten.KALENDERUNGUELTIG);
        vektoren.addAll(SynthetischeDaten.SCHALTTAGE_GUELTIG);
        vektoren.add(SynthetischeDaten.SCHALTTAG_1900);
        vektoren.addAll(SynthetischeDaten.JAHRESGRENZEN_GUELTIG);
        vektoren.addAll(SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG);
        vektoren.add(SynthetischeDaten.SCHALTTAG_ZWEISTELLIG_NULL);
        vektoren.addAll(SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK);
        vektoren.add(SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT);
        vektoren.add(SynthetischeDaten.BUCHSTABE_UND_DATUM);
        vektoren.add(SynthetischeDaten.ZEITRAUM);
        vektoren.add(SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN);
        vektoren.add(SynthetischeDaten.ZEITRAUM_EINSTELLIG);
        vektoren.add(SynthetischeDaten.DATUM_PUNKT + " 1");
        vektoren.add(SynthetischeDaten.DATUM_LEISTUNG);
        vektoren.add(SynthetischeDaten.DATUM_LEISTUNG_SPAETER);
        vektoren.add(SynthetischeDaten.DATUM_GENEHMIGUNG);
        List<String> vorn = List.of("", "BSNR ", "x", "B ", "1 ");
        List<String> hinten = List.of("", " ", ", ", " 1", ":", "x", "-1", " 08:30", ".", "/", String.valueOf(NBSP));
        for (String v : vektoren) {
            for (String davor : vorn) {
                for (String dahinter : hinten) {
                    String text = davor + v + dahinter;
                    for (int start = 0; start <= davor.length(); start++) {
                        for (int end = start + 1; end <= text.length(); end++) {
                            vergleiche(text, start, end);
                        }
                    }
                }
            }
        }
        // Istwerte: 110 046 Vergleiche, davon 4 260 Datumssegmente; verlangt: mindestens 70 %.
        assertTrue(verglichen >= 70_000L, "Zahl der Vergleiche " + verglichen);
        assertTrue(davonDatum >= 2_900L, "Zahl der Datumssegmente " + davonDatum);
    }

    /** Zufaellige Texte aus Ziffernbloecken und Trennern, fester Seed, beliebige Bereiche. */
    @Test
    void zufaelligeTexte_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        Random r = new Random(20261125L);
        char[] trenner = {'.', '.', '.', '-', '-', '/', ' ', ':', 'B', ',', NBSP, DUENNES, SCHMAL};
        for (int n = 0; n < 200_000; n++) {
            StringBuilder sb = new StringBuilder();
            int teile = 1 + r.nextInt(8);
            for (int t = 0; t < teile; t++) {
                int laenge = r.nextInt(6);
                for (int i = 0; i < laenge; i++) {
                    sb.append((char) ('0' + (i == 0 && r.nextInt(3) == 0 ? r.nextInt(4) : r.nextInt(10))));
                }
                if (t < teile - 1 || r.nextBoolean()) {
                    sb.append(trenner[r.nextInt(trenner.length)]);
                }
            }
            String text = sb.toString();
            if (text.isEmpty()) {
                continue;
            }
            int start = r.nextInt(3) == 0 ? r.nextInt(text.length()) : 0;
            for (int end = start + 1; end <= text.length(); end++) {
                vergleiche(text, start, end);
            }
        }
        // Istwerte: 2 569 767 Vergleiche, davon 195 Datumssegmente; verlangt: mindestens 70 %.
        assertTrue(verglichen >= 1_700_000L, "Zahl der Vergleiche " + verglichen);
        assertTrue(davonDatum >= 130L, "Zahl der Datumssegmente " + davonDatum);
    }

    /** Abwandlungen gueltiger Daten (ein Zeichen ersetzt, eingefuegt, entfernt) mit allen Endpositionen. */
    @Test
    void abwandlungenGueltigerDaten_wieDieReferenz() {
        verglichen = 0;
        davonDatum = 0;
        Random r = new Random(20261126L);
        String zeichen = "0123456789.-/ :B" + NBSP + DUENNES + SCHMAL;
        List<String> basis = new ArrayList<>(SynthetischeDaten.GUELTIGE_FORMEN);
        basis.add(SynthetischeDaten.DATUM_LEISTUNG);
        basis.add(SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN);
        basis.add(SynthetischeDaten.ZEITRAUM_EINSTELLIG);
        basis.addAll(SynthetischeDaten.SCHALTTAGE_GUELTIG);
        for (int n = 0; n < 100_000; n++) {
            StringBuilder sb = new StringBuilder(basis.get(r.nextInt(basis.size())));
            int aenderungen = 1 + r.nextInt(2);
            for (int a = 0; a < aenderungen; a++) {
                int pos = r.nextInt(sb.length() + 1);
                char c = zeichen.charAt(r.nextInt(zeichen.length()));
                switch (r.nextInt(3)) {
                    case 0 -> sb.insert(pos, c);
                    case 1 -> {
                        if (pos < sb.length()) {
                            sb.setCharAt(pos, c);
                        }
                    }
                    default -> {
                        if (pos < sb.length()) {
                            sb.deleteCharAt(pos);
                        }
                    }
                }
            }
            String text = sb.toString();
            if (!text.isEmpty()) {
                vergleicheAlleEnden(text);
            }
        }
        // Istwerte: 1 117 059 Vergleiche, davon 70 415 Datumssegmente; verlangt: mindestens 70 %.
        assertTrue(verglichen >= 780_000L, "Zahl der Vergleiche " + verglichen);
        assertTrue(davonDatum >= 49_000L, "Zahl der Datumssegmente " + davonDatum);
    }
}
