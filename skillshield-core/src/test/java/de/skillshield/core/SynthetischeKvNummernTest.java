package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Prueft die zentrale Klasse {@link SynthetischeKvNummern} (AK-7, AK-8, AK-28): Jeder Vektor ist nach der
 * unabhaengigen Referenz so gueltig oder ungueltig, wie er benannt ist; die Bildungsfunktionen liefern nur gueltige
 * Nummern mit Praefix 00. Die Verfahren sind Sekundaerquellen-Stand (Vermerk im Javadoc der Klasse).
 */
class SynthetischeKvNummernTest {

    private static final List<String> LANR_GUELTIG_ALLE = List.of(LANR_GUELTIG, LANR_GUELTIG_FACHGRUPPE,
            LANR_GUELTIG_PRUEFZIFFER_NULL, LANR_GUELTIG_ZWEI, LANR_MARKER);
    private static final List<String> LANR_UNGUELTIG_ALLE = List.of(LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN,
            LANR_UNGUELTIG_VERTAUSCHT, LANR_UNGUELTIG_ZIFFER_GEAENDERT);
    private static final List<String> IK_UNGUELTIG_ALLE = List.of(IK_UNGUELTIG_KOMPLEMENT, IK_UNGUELTIG_NULL,
            IK_UNGUELTIG_VERTAUSCHT);

    // Zur Laufzeit zusammengesetzte neunstellige Nummern OHNE Praefix 00 (Regel A-6/AK-28): jede ist nach dem LANR-
    // und nach dem IK-Referenzverfahren ungueltig; der Waechtertest unten prueft das. Gueltige Varianten werden nie
    // gebildet, sondern nur rechnerisch erwaehnt.
    /** "12" + Stellen 3 bis 9 von LANR_GUELTIG: LANR-Summe 101, Stelle 7 ist 1 (101 + 1 nicht durch 10 teilbar). */
    private static final String ZUSAMMENGESETZT_12 = "12" + LANR_GUELTIG.substring(2);
    /** "1" + Stellen 2 bis 9 von LANR_GUELTIG: LANR-Summe 83. */
    private static final String ZUSAMMENGESETZT_1 = "1" + LANR_GUELTIG.substring(1);
    /** "01" + Stellen 3 bis 9 von LANR_GUELTIG: LANR-Summe 88, beginnt mit 01, nicht mit 00. */
    private static final String ZUSAMMENGESETZT_01 = "0" + "1" + LANR_GUELTIG.substring(2);
    /** Klassifikation 99, Stellen 3 bis 8 von IK_GUELTIG, falsche Pruefziffer 2 (richtig waere 1). */
    private static final String ZUSAMMENGESETZT_99 = "99" + IK_GUELTIG.substring(2, 8) + "2";
    /** Neun Ziffern der Doku-IP ohne Punkte (siehe ak6_dokuIpHatNeunZiffern...). */
    private static final String ZUSAMMENGESETZT_DOKU_IP = IK_FORM_DOKU_IP.replace(".", "");
    private static final List<String> ZUSAMMENGESETZT_ALLE = List.of(ZUSAMMENGESETZT_12, ZUSAMMENGESETZT_1,
            ZUSAMMENGESETZT_01, ZUSAMMENGESETZT_99, ZUSAMMENGESETZT_DOKU_IP);

    // ------------------------------------------------------------------ LANR

    @Test
    void ak7_lanrGueltigeVektorenSindNachReferenzGueltig() {
        for (String n : LANR_GUELTIG_ALLE) {
            assertTrue(lanrGueltigReferenz(n), "LANR-Vektor sollte gueltig sein: Laenge " + n.length());
        }
    }

    @Test
    void ak7_lanrUngueltigeVektorenSindNachReferenzUngueltig() {
        for (String n : LANR_UNGUELTIG_ALLE) {
            assertFalse(lanrGueltigReferenz(n), "LANR-Vektor sollte ungueltig sein: Laenge " + n.length());
        }
    }

    @Test
    void ak7_lanrRechenwegSummen() {
        assertEquals(79, lanrGewichtsSumme(LANR_GUELTIG));
        assertEquals(79, lanrGewichtsSumme(LANR_GUELTIG_FACHGRUPPE));
        assertEquals(70, lanrGewichtsSumme(LANR_GUELTIG_PRUEFZIFFER_NULL));
        assertEquals(88, lanrGewichtsSumme(LANR_GUELTIG_ZWEI));
        assertEquals(84, lanrGewichtsSumme(LANR_UNGUELTIG_VERTAUSCHT));
        assertEquals(105, lanrGewichtsSumme(LANR_MARKER));
    }

    @Test
    void ak7_lanrFachgruppeWirdNichtGeprueft() {
        assertTrue(lanrGueltigReferenz(LANR_GUELTIG));
        assertTrue(lanrGueltigReferenz(LANR_GUELTIG_FACHGRUPPE));
        assertTrue(lanrGueltigReferenz(LANR_GUELTIG.substring(0, 7) + "47"));
        assertEquals(LANR_GUELTIG.substring(0, 7), LANR_GUELTIG_FACHGRUPPE.substring(0, 7));
        assertNotEquals(LANR_GUELTIG, LANR_GUELTIG_FACHGRUPPE);
    }

    @Test
    void ak7_lanrRestZehnErgibtPruefzifferNull() {
        assertEquals(0, lanrGewichtsSumme(LANR_GUELTIG_PRUEFZIFFER_NULL) % 10);
        assertEquals('0', LANR_GUELTIG_PRUEFZIFFER_NULL.charAt(6));
    }

    @Test
    void ak7_lanrJedeEinzelneAenderungDerPruefzifferMachtUngueltig() {
        for (int z = 0; z <= 9; z++) {
            String n = LANR_GUELTIG.substring(0, 6) + z + LANR_GUELTIG.substring(7);
            assertEquals(z == 1, lanrGueltigReferenz(n), "Pruefziffer " + z);
        }
    }

    @Test
    void ak7_lanrGewichteDerStellenEinsUndZweiGehenEin() {
        // Stellen 1 und 2 sind 1 und 2: 1*4 + 2*9 = 22 kommen zur Summe 79 des Vektors hinzu
        assertEquals(79 + 4 + 18, lanrGewichtsSumme(ZUSAMMENGESETZT_12));
        assertEquals(101, lanrGewichtsSumme(ZUSAMMENGESETZT_12));
        // Rest 1, erwartete Pruefziffer (10 - 1) = 9; Stelle 7 ist aber 1: ungueltig
        assertEquals(9, (10 - lanrGewichtsSumme(ZUSAMMENGESETZT_12) % 10) % 10);
        assertEquals('1', ZUSAMMENGESETZT_12.charAt(6));
        assertFalse(lanrGueltigReferenz(ZUSAMMENGESETZT_12));
        // rein rechnerisch (ohne die Nummer zu bilden): mit Stelle 7 = 9 waere die Summe 101 + 9 = 110
        assertEquals(0, (101 + 9) % 10);
        // jede andere Pruefziffer 0 bis 8 ergibt keine durch 10 teilbare Summe; es wird keine Nummer gebildet
        for (int z = 0; z <= 8; z++) {
            assertNotEquals(0, (lanrGewichtsSumme(ZUSAMMENGESETZT_12) + z) % 10, "Pruefziffer " + z);
        }
        // einzeln: Stelle 1 hat Gewicht 4, Stelle 2 hat Gewicht 9 (beide Nummern sind nach beiden Verfahren ungueltig)
        assertEquals(79 + 4, lanrGewichtsSumme(ZUSAMMENGESETZT_1));
        assertEquals(79 + 9, lanrGewichtsSumme(ZUSAMMENGESETZT_01));
    }

    // ------------------------------------------------------------------ IK

    @Test
    void ak8_ikGueltigerVektorIstNachReferenzGueltig() {
        assertTrue(ikGueltigReferenz(IK_GUELTIG));
        assertTrue(ikGueltigReferenz(IK_MARKER));
    }

    @Test
    void ak8_ikUngueltigeVektorenSindNachReferenzUngueltig() {
        for (String n : IK_UNGUELTIG_ALLE) {
            assertFalse(ikGueltigReferenz(n), "IK-Vektor sollte ungueltig sein: Laenge " + n.length());
        }
    }

    @Test
    void ak8_ikVektor123456UnterscheidetRestVonKomplement() {
        assertEquals(21, ikQuersummenSumme(IK_GUELTIG));
        assertEquals(21, ikQuersummenSumme(IK_UNGUELTIG_KOMPLEMENT));
        int rest = 21 % 10;
        int komplement = 10 - rest;
        assertEquals(1, rest);
        assertEquals(9, komplement);
        assertEquals(rest, IK_GUELTIG.charAt(8) - '0');
        assertEquals(komplement, IK_UNGUELTIG_KOMPLEMENT.charAt(8) - '0');
        assertTrue(ikGueltigReferenz(IK_GUELTIG));
        assertFalse(ikGueltigReferenz(IK_UNGUELTIG_KOMPLEMENT));
    }

    @Test
    void ak8_ikRechenwegSummen() {
        assertEquals(22, ikQuersummenSumme(IK_UNGUELTIG_VERTAUSCHT));
        assertEquals(34, ikQuersummenSumme(IK_MARKER));
    }

    @Test
    void ak8_ikKlassifikationGehtNichtEin() {
        // Klassifikation 99 statt 00, falsche Pruefziffer 2: die Nummer ist ungueltig und wird nur zum Rechnen benutzt
        assertEquals(ikQuersummenSumme(IK_GUELTIG), ikQuersummenSumme(ZUSAMMENGESETZT_99));
        assertEquals(21, ikQuersummenSumme(ZUSAMMENGESETZT_99));
        assertEquals('2', ZUSAMMENGESETZT_99.charAt(8));
        assertFalse(ikGueltigReferenz(ZUSAMMENGESETZT_99));
        // rein rechnerisch: mit der Pruefziffer 1 (Rest von 21) waere sie gueltig; diese Nummer wird nicht gebildet
        assertEquals(1, ikQuersummenSumme(ZUSAMMENGESETZT_99) % 10);
    }

    // ------------------------------------------------------------------ Referenz: Randfaelle

    @Test
    void referenzen_ungueltigeEingabenSindUngueltigOhneAusnahme() {
        for (String s : new String[] {null, "", "1", LAENGE_8, LAENGE_10, LANR_GUELTIG + "0",
            LANR_GUELTIG.substring(0, 8) + "x", "\uFF10" + LANR_GUELTIG.substring(1)}) {
            assertFalse(lanrGueltigReferenz(s));
            assertFalse(ikGueltigReferenz(s));
        }
    }

    @Test
    void summenfunktionenLehnenFalscheLaengeOhneWerteAb() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> lanrGewichtsSumme(LAENGE_8));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> ikQuersummenSumme(LAENGE_10));
        assertEquals("genau neun Ziffern erwartet", e1.getMessage());
        assertEquals("genau neun Ziffern erwartet", e2.getMessage());
        assertThrows(IllegalArgumentException.class, () -> lanrGewichtsSumme(null));
    }

    // ------------------------------------------------------------------ Bildung

    /** Eigene kleine Rechnung ohne den gemeinsamen Hilfscode: LANR (Stellen 1 bis 6 mit 4 und 9, Stelle 7). */
    private static boolean lanrNachgerechnet(String n) {
        int summe = 0;
        for (int i = 0; i < 6; i++) {
            summe += (n.charAt(i) - '0') * (i % 2 == 0 ? 4 : 9);
        }
        return (10 - summe % 10) % 10 == n.charAt(6) - '0';
    }

    /** Eigene kleine Rechnung ohne den gemeinsamen Hilfscode: IK (Stellen 3 bis 8 mit 2 und 1, Stelle 9). */
    private static boolean ikNachgerechnet(String n) {
        int summe = 0;
        for (int i = 2; i < 8; i++) {
            int produkt = (n.charAt(i) - '0') * (i % 2 == 0 ? 2 : 1);
            summe += produkt > 9 ? produkt - 9 : produkt;
        }
        return summe % 10 == n.charAt(8) - '0';
    }

    @Test
    void nachrechnung_stimmtMitDenVektorenUeberein() {
        for (String n : LANR_GUELTIG_ALLE) {
            assertTrue(lanrNachgerechnet(n), "LANR-Vektor Laenge " + n.length());
        }
        for (String n : LANR_UNGUELTIG_ALLE) {
            assertFalse(lanrNachgerechnet(n), "LANR-Vektor Laenge " + n.length());
        }
        assertTrue(ikNachgerechnet(IK_GUELTIG));
        assertTrue(ikNachgerechnet(IK_MARKER));
        for (String n : IK_UNGUELTIG_ALLE) {
            assertFalse(ikNachgerechnet(n), "IK-Vektor Laenge " + n.length());
        }
    }

    @Test
    void bildeLanr_liefert1000GueltigeNummernMitPraefix00() {
        Random zufall = new Random(20261121L);
        Set<String> verschieden = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String n = bildeLanr(zufall);
            assertEquals(9, n.length(), "Iteration " + i);
            assertTrue(n.startsWith("00"), "Iteration " + i);
            assertTrue(lanrGueltigReferenz(n), "Iteration " + i);
            assertTrue(lanrNachgerechnet(n), "Iteration " + i + " (eigene Rechnung)");
            verschieden.add(n);
        }
        assertTrue(verschieden.size() > 500, "Verschiedene Nummern: " + verschieden.size());
    }

    @Test
    void bildeIk_liefert1000GueltigeNummernMitPraefix00() {
        Random zufall = new Random(20261122L);
        Set<String> verschieden = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String n = bildeIk(zufall);
            assertEquals(9, n.length(), "Iteration " + i);
            assertTrue(n.startsWith("00"), "Iteration " + i);
            assertTrue(ikGueltigReferenz(n), "Iteration " + i);
            assertTrue(ikNachgerechnet(n), "Iteration " + i + " (eigene Rechnung)");
            verschieden.add(n);
        }
        assertTrue(verschieden.size() > 900, "Verschiedene Nummern: " + verschieden.size());
    }

    @Test
    void bilde_istDeterministischBeiGleichemSeed() {
        assertEquals(bildeLanr(new Random(7L)), bildeLanr(new Random(7L)));
        assertEquals(bildeIk(new Random(7L)), bildeIk(new Random(7L)));
    }

    // ------------------------------------------------------------------ Konstanten

    @Test
    void waechter_zusammengesetzteNeunstelligeNummernOhnePraefix00SindNachBeidenVerfahrenUngueltig() {
        assertEquals(5, ZUSAMMENGESETZT_ALLE.size());
        for (String n : ZUSAMMENGESETZT_ALLE) {
            assertEquals(9, n.length());
            assertTrue(istNurAsciiZiffern(n));
            assertFalse(n.startsWith("00"), "Nummer beginnt mit 00 und gehoert nicht in diese Liste");
            assertFalse(lanrGueltigReferenz(n), "LANR-gueltig, Nummer Laenge " + n.length());
            assertFalse(ikGueltigReferenz(n), "IK-gueltig, Nummer Laenge " + n.length());
            assertFalse(lanrNachgerechnet(n), "LANR-gueltig (eigene Rechnung)");
            assertFalse(ikNachgerechnet(n), "IK-gueltig (eigene Rechnung)");
        }
    }

    @Test
    void alleNeunstelligenNummernHabenPraefix00UndSindVerschieden() {
        List<String> alle = alleNeunstelligenNummern();
        assertEquals(15, alle.size());
        assertEquals(alle.size(), new HashSet<>(alle).size());
        for (String n : alle) {
            assertTrue(istNurAsciiZiffern(n) && n.length() == 9 && n.startsWith("00"), "Laenge " + n.length());
        }
    }

    @Test
    void markerNummernSindGueltigVerschiedenUndInDenErstenSechsZiffernEinzigartig() {
        assertTrue(lanrGueltigReferenz(LANR_MARKER));
        assertTrue(ikGueltigReferenz(IK_MARKER));
        Set<String> sechser = new HashSet<>();
        for (String n : alleNeunstelligenNummern()) {
            if (!n.equals(LANR_MARKER) && !n.equals(IK_MARKER)) {
                sechser.add(n.substring(0, 6));
            }
        }
        assertFalse(sechser.contains(LANR_MARKER.substring(0, 6)));
        assertFalse(sechser.contains(IK_MARKER.substring(0, 6)));
        assertNotEquals(LANR_MARKER.substring(0, 6), IK_MARKER.substring(0, 6));
        assertNotEquals(LANR_MARKER, IK_MARKER);
    }

    @Test
    void ak6_trennerformenEntsprechenDenGueltigenNummernUndHabenGenauEinenTrennerZwischenGruppen() {
        pruefeTrennerform(LANR_TRENNER_LEERZEICHEN, LANR_GUELTIG, 2);
        pruefeTrennerform(LANR_TRENNER_UNGLEICHE_GRUPPEN, LANR_GUELTIG, 1);
        pruefeTrennerform(LANR_TRENNER_NBSP, LANR_GUELTIG, 2);
        pruefeTrennerform(LANR_TRENNER_SCHRAEGSTRICH, LANR_GUELTIG, 2);
        pruefeTrennerform(IK_TRENNER_BINDESTRICH, IK_GUELTIG, 2);
        pruefeTrennerform(IK_TRENNER_PUNKT, IK_GUELTIG, 2);
        pruefeTrennerform(IK_TRENNER_GRUPPE_6_3, IK_GUELTIG, 1);
        pruefeTrennerform(IK_TRENNER_NBSP, IK_GUELTIG, 2);
        assertEquals(' ', LANR_TRENNER_LEERZEICHEN.charAt(3));
        assertEquals('\u00A0', LANR_TRENNER_NBSP.charAt(3));
        assertEquals('\u00A0', IK_TRENNER_NBSP.charAt(3));
    }

    private static void pruefeTrennerform(String form, String ohneTrenner, int anzahlTrenner) {
        StringBuilder ziffern = new StringBuilder();
        int trenner = 0;
        for (int i = 0; i < form.length(); i++) {
            char c = form.charAt(i);
            if (c >= '0' && c <= '9') {
                ziffern.append(c);
            } else {
                trenner++;
                assertTrue(i > 0 && i < form.length() - 1, "Trenner am Rand");
                char vor = form.charAt(i - 1);
                char nach = form.charAt(i + 1);
                assertTrue(vor >= '0' && vor <= '9' && nach >= '0' && nach <= '9', "Zwei Trenner hintereinander");
            }
        }
        assertEquals(ohneTrenner, ziffern.toString());
        assertEquals(anzahlTrenner, trenner);
    }

    @Test
    void ak6_dokuIpHatNeunZiffernUndLiegtImDokumentationsbereich() {
        assertEquals(9, ZUSAMMENGESETZT_DOKU_IP.length());
        assertFalse(ikGueltigReferenz(ZUSAMMENGESETZT_DOKU_IP));
        // 198.51.100.0/24: die ersten drei Bloecke sind 198, 51, 100; der letzte liegt zwischen 0 und 255
        String[] bloecke = IK_FORM_DOKU_IP.split("[.]", -1);
        assertEquals(4, bloecke.length);
        assertEquals(198, Integer.parseInt(bloecke[0]));
        assertEquals(51, Integer.parseInt(bloecke[1]));
        assertEquals(100, Integer.parseInt(bloecke[2]));
        int letzter = Integer.parseInt(bloecke[3]);
        assertTrue(letzter >= 0 && letzter <= 255, "letzter Block " + letzter);
    }

    @Test
    void ak11_laengenprobenHabenDieErwartetenLaengenUndKeineDavonIstNeun() {
        assertEquals(5, LAENGE_5.length());
        assertEquals(6, LAENGE_6.length());
        assertEquals(7, LAENGE_7.length());
        assertEquals(8, LAENGE_8.length());
        assertEquals(10, LAENGE_10.length());
        assertEquals(12, LAENGE_12.length());
        assertEquals(13, LAENGE_13.length());
        assertEquals(4, LAENGE_4.length());
        for (String s : List.of(LAENGE_4, LAENGE_5, LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, LAENGE_12,
                LAENGE_13)) {
            assertTrue(istNurAsciiZiffern(s));
        }
        assertEquals(10, TELEFON_PROBE.replace(" ", "").length());
    }

    private static boolean istNurAsciiZiffern(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return !s.isEmpty();
    }

    // ------------------------------------------------------------------ KVNR (kvnr-bsnr-erkennung, AK-6, AK-23)

    private static boolean istKvnrForm(String s, int ziffern) {
        if (s.length() != 1 + ziffern || s.charAt(0) < 'A' || s.charAt(0) > 'Z') {
            return false;
        }
        return istNurAsciiZiffern(s.substring(1));
    }

    private static boolean kvnrGueltigReferenz(String s) {
        int soll = KvnrBsnrHilfen.kvnrPruefziffer(s.charAt(0), s.substring(1, 9));
        return soll == s.charAt(9) - '0';
    }

    @Test
    void ak23_kvnrVektoren_habenBuchstabeUndNeunZiffern_mindestensEinGueltigerUndDreiUngueltige() {
        assertTrue(alleKvnrGueltig().size() >= 1);
        assertTrue(alleKvnrUngueltig().size() >= 3);
        for (String n : alleKvnrGueltig()) {
            assertTrue(istKvnrForm(n, 9), "gueltiger Vektor hat nicht die Form Buchstabe und neun Ziffern");
            assertTrue(kvnrGueltigReferenz(n), "gueltiger Vektor ist nach der Referenz ungueltig");
        }
        for (String n : alleKvnrUngueltig()) {
            assertTrue(istKvnrForm(n, 9), "ungueltiger Vektor hat nicht die Form Buchstabe und neun Ziffern");
            assertFalse(kvnrGueltigReferenz(n), "ungueltiger Vektor ist nach der Referenz gueltig");
        }
    }

    @Test
    void ak23_kvnrVektoren_sindVerschieden_undNurDasLehrbeispielOhneDieZiffern0000() {
        List<String> alle = new ArrayList<>(alleKvnrGueltig());
        alle.addAll(alleKvnrUngueltig());
        assertEquals(alle.size(), new HashSet<>(alle).size(), "doppelte KVNR-Vektoren");
        for (String n : alle) {
            if (n.equals(KVNR_LEHRBEISPIEL)) {
                assertEquals('A', n.charAt(0));
                assertFalse(n.startsWith("0000", 1));
            } else {
                assertTrue(n.startsWith("0000", 1), "KVNR-Vektor ohne die Ziffern 0000 an Stelle 2 bis 5");
            }
        }
    }

    @Test
    void ak23_kvnrVektoren_markerUndNullfolgeUndFormabweichungenHabenDieBenanntenEigenschaften() {
        assertTrue(kvnrGueltigReferenz(KVNR_MARKER));
        assertTrue(alleKvnrGueltig().contains(KVNR_MARKER));
        assertEquals(1, alleKvnrGueltig().stream().filter(n -> n.regionMatches(1, KVNR_MARKER, 1, 9)).count());
        assertTrue(kvnrGueltigReferenz(KVNR_NULL));
        assertEquals(1, KVNR_NULL.substring(1).chars().distinct().count(), "Nullfolge hat nicht nur gleiche Ziffern");
        assertEquals('0', KVNR_NULL.charAt(1));
        assertTrue(istKvnrForm(KVNR_ACHT_ZIFFERN, 8));
        assertTrue(istKvnrForm(KVNR_ZEHN_ZIFFERN, 10));
        assertTrue(KVNR_ACHT_ZIFFERN.startsWith("0000", 1) && KVNR_ZEHN_ZIFFERN.startsWith("0000", 1));
    }

    @Test
    void ak6_kvnrVektoren_enthaltenBuchstabeZ_undEinenVektorMitBuchstabeAusAbisI() {
        assertEquals('Z', KVNR_GUELTIG.charAt(0));
        assertEquals('Z', KVNR_GUELTIG_ZWEI.charAt(0));
        assertTrue(KVNR_GUELTIG_BUCHSTABE_C.charAt(0) >= 'A' && KVNR_GUELTIG_BUCHSTABE_C.charAt(0) <= 'I');
        assertEquals('A', KVNR_LEHRBEISPIEL.charAt(0));
        assertEquals(10, KVNR_LEHRBEISPIEL.length());
        assertTrue(istKvnrForm(KVNR_LEHRBEISPIEL, 9));
        assertTrue(kvnrGueltigReferenz(KVNR_LEHRBEISPIEL));
        for (int stelle = 1; stelle <= 8; stelle++) {
            assertEquals(stelle, KVNR_LEHRBEISPIEL.charAt(stelle) - '0', "Lehrbeispiel nicht eins bis acht");
        }
    }

    @Test
    void zentraleKlasse_alleOeffentlichenStringFelderUndListenwerte_bestehenDenLaufzeitWaechter()
            throws ReflectiveOperationException {
        int felder = 0;
        int kvnrFelder = 0;
        int listenwerte = 0;
        for (Field feld : SynthetischeKvNummern.class.getFields()) {
            int modus = feld.getModifiers();
            if (Modifier.isStatic(modus) && Modifier.isFinal(modus) && feld.getType() == String.class) {
                felder++;
                kvnrFelder += feld.getName().startsWith("KVNR_") ? 1 : 0;
                assertEquals(0, KvnrSynthetischWaechter.pruefe((String) feld.get(null)).verstoesse(),
                        "Feld " + feld.getName() + " enthaelt eine gueltige KVNR-Form ohne die Ziffern 0000");
            }
        }
        for (Method methode : SynthetischeKvNummern.class.getMethods()) {
            if (Modifier.isStatic(methode.getModifiers()) && methode.getParameterCount() == 0
                    && methode.getReturnType() == List.class) {
                for (Object wert : (List<?>) methode.invoke(null)) {
                    listenwerte++;
                    assertEquals(0, KvnrSynthetischWaechter.pruefe((String) wert).verstoesse(),
                            "Listenwert aus " + methode.getName() + " ohne die Ziffern 0000");
                }
            }
        }
        // Istwert 44 Felder, davon 11 KVNR-Felder, 24 Listenwerte; Mindestzahlen unter 70 Prozent (30, 7, 16)
        assertTrue(felder >= 30 && kvnrFelder >= 7 && listenwerte >= 16,
                "Felder " + felder + ", KVNR-Felder " + kvnrFelder + ", Listenwerte " + listenwerte);
        // Das Lehrbeispiel ist die einzige gueltige Form ohne 0000, und der Waechter erkennt es als gueltig
        KvnrSynthetischWaechter.Befund lehr = KvnrSynthetischWaechter.pruefe(KVNR_LEHRBEISPIEL);
        assertEquals(1, lehr.gueltigeFormen());
        assertEquals(0, lehr.verstoesse());
    }

    @Test
    void selbsttest_feldWaechter_meldetEineRegelwidrigeNummer_rechnerischGebildetOhneLiteral() {
        String acht = "1234" + "5671";
        String regelwidrig = "Q" + KvnrBsnrHilfen.kvnrNeunZiffern('Q', acht);
        KvnrSynthetischWaechter.Befund befund = KvnrSynthetischWaechter.pruefe(regelwidrig);
        assertEquals(1, befund.gueltigeFormen());
        assertEquals(1, befund.verstoesse());
        assertTrue(kvnrGueltigReferenz(regelwidrig));
        assertFalse(regelwidrig.startsWith("0000", 1));
    }

    @Test
    void kvnrVektoren_ihreNeunZiffernSindKeineEintraegeDerLanrIkListe() {
        List<String> andere = alleNeunstelligenNummern();
        for (String n : alleKvnrGueltig()) {
            assertFalse(andere.contains(n.substring(1)), "KVNR-Ziffern stehen schon in der LANR/IK-Liste");
        }
        for (String n : alleKvnrUngueltig()) {
            assertFalse(andere.contains(n.substring(1)), "KVNR-Ziffern stehen schon in der LANR/IK-Liste");
        }
        assertEquals(15, andere.size(), "die Liste der LANR/IK-Nummern bleibt unveraendert");
    }
}
