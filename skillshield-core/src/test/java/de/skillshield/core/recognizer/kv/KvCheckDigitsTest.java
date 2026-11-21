package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests der Pr\u00FCfziffernverfahren LANR und IK (AK-7, AK-8). Alle Nummern stammen aus
 * {@link SynthetischeKvNummern}; hier steht keine neunstellige Ziffernfolge als Literal.
 *
 * <p><b>Quellenvermerk Pr\u00FCfverfahren LANR (AK-29).</b> Quelle: Wikipedia, "Lebenslange Arztnummer", Abschnitt
 * "Aufbau", https://de.wikipedia.org/wiki/Lebenslange_Arztnummer; gleichlautend: krankenkasseninfo.de,
 * Krankenkassenlexikon, Eintrag "Arztnummer",
 * https://www.krankenkasseninfo.de/zahlen-fakten/lexikon/arztnummer, abgerufen am 21.11.2026. Status:
 * Sekund\u00E4rquelle, nicht gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: Richtlinie
 * der KBV nach \u00A7 75 Absatz 7 SGB V (Stand 1. Januar 2024), eingesehen; sie best\u00E4tigt den Aufbau,
 * enth\u00E4lt aber das Rechenverfahren nicht (Verweis auf die Vereinbarung ANRV, nicht eingesehen).
 *
 * <p><b>Quellenvermerk Pr\u00FCfverfahren IK (AK-29).</b> Quelle: Wikipedia, "Institutionskennzeichen", Abschnitt
 * "Pr\u00FCfverfahren", https://de.wikipedia.org/wiki/Institutionskennzeichen, abgerufen am 21.11.2026.
 * Status: Sekund\u00E4rquelle, nicht gegen die offizielle Spezifikation verifiziert. Offizielle Quelle:
 * Gemeinsames Rundschreiben Institutionskennzeichen der ARGE IK, nicht eingesehen.
 *
 * <p>Regel aus T-1: Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder IK-Verfahren
 * gueltig ist und nicht mit {@code 00} beginnt. Gueltige Nummern kommen aus {@code bildeLanr} und
 * {@code bildeIk} (Praefix {@code 00}) oder aus Konstanten; zufaellige Nummern haben stets das Praefix
 * {@code 00}; Ziffernaenderungen betreffen nur Stellen 3 bis 9 und ergeben nach dem Beweis unten nie eine
 * gueltige Nummer. Die Belegung der Stellen 1 und 2 wird nur an einer nach IK ungueltigen Basis ausprobiert;
 * Kombinationen ohne Praefix {@code 00}, die nach dem LANR-Verfahren gueltig waeren, werden vorab rechnerisch
 * aus der Gewichtssumme bestimmt und nicht gebildet.
 */
class KvCheckDigitsTest {

    private static final long SEED = 20261121L;
    private static final int ZUFALLSNUMMERN = 10_000;

    /** Wandelt eine Nummer aus {@link SynthetischeKvNummern} in Ziffernwerte um. */
    private static int[] ziffern(String nummer) {
        int[] d = new int[nummer.length()];
        for (int i = 0; i < d.length; i++) {
            d[i] = nummer.charAt(i) - '0';
        }
        return d;
    }

    private static boolean lanr(String nummer) {
        return KvCheckDigits.isValidLanr(ziffern(nummer));
    }

    private static boolean ik(String nummer) {
        return KvCheckDigits.isValidIk(ziffern(nummer));
    }

    // ------------------------------------------------------------------ LANR (AK-7)

    @Test
    void ak7_lanrGueltig() {
        // Stellen 1-6 = 001235: 0*4 + 0*9 + 1*4 + 2*9 + 3*4 + 5*9 = 79, Rest 9, Pruefziffer 10 - 9 = 1 = Stelle 7.
        assertTrue(lanr(SynthetischeKvNummern.LANR_GUELTIG));
    }

    @Test
    void ak7_lanrGueltigFachgruppeWirdNichtGeprueft() {
        // Wie oben: Summe 79, Pruefziffer 1 = Stelle 7; Stellen 8 und 9 gehen nicht ein.
        assertTrue(lanr(SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE));
    }

    @Test
    void ak7_lanrGueltigPruefzifferNull() {
        // Stellen 1-6 = 001234: 0 + 0 + 4 + 18 + 12 + 36 = 70, Rest 0, 10 - 0 = 10 ergibt Pruefziffer 0 = Stelle 7.
        assertTrue(lanr(SynthetischeKvNummern.LANR_GUELTIG_PRUEFZIFFER_NULL));
    }

    @Test
    void ak7_lanrGueltigPruefzifferZwei() {
        // Stellen 1-6 = 001236: 0 + 0 + 4 + 18 + 12 + 54 = 88, Rest 8, Pruefziffer 2 = Stelle 7.
        assertTrue(lanr(SynthetischeKvNummern.LANR_GUELTIG_ZWEI));
    }

    @Test
    void ak7_lanrUngueltigPruefzifferVerschoben() {
        // Summe 79, Rest 9, erwartet 1; Stelle 7 ist 2.
        assertFalse(lanr(SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN));
    }

    @Test
    void ak7_lanrUngueltigNachbarziffernVertauscht() {
        // Stellen 1-6 = 001325: 0 + 0 + 4 + 27 + 8 + 45 = 84, Rest 4, erwartet 6; Stelle 7 ist 1.
        assertFalse(lanr(SynthetischeKvNummern.LANR_UNGUELTIG_VERTAUSCHT));
    }

    @Test
    void ak7_lanrUngueltigZifferGeaendert() {
        // Summe 79, Rest 9, erwartet 1; Stelle 7 ist 0.
        assertFalse(lanr(SynthetischeKvNummern.LANR_UNGUELTIG_ZIFFER_GEAENDERT));
    }

    @Test
    void ak7_lanrFachgruppeStellenAchtUndNeunGehenNichtEin() {
        // Stellen 8 und 9 werden nicht geprueft: jede Belegung aendert das Ergebnis nicht.
        for (int stelle8 = 0; stelle8 < 10; stelle8++) {
            for (int stelle9 = 0; stelle9 < 10; stelle9++) {
                int[] d = ziffern(SynthetischeKvNummern.LANR_GUELTIG);
                d[7] = stelle8;
                d[8] = stelle9;
                assertTrue(KvCheckDigits.isValidLanr(d));
            }
        }
    }

    // ------------------------------------------------------------------ IK (AK-8)

    @Test
    void ak8_ikGueltig() {
        // Stellen 3-8 = 123456 mit 2,1,2,1,2,1: 2 + 2 + 6 + 4 + (10 -> 1) + 6 = 21, Rest 1 = Stelle 9.
        assertTrue(ik(SynthetischeKvNummern.IK_GUELTIG));
    }

    @Test
    void ak8_ikUngueltigKomplement() {
        // Summe 21, Rest 1; Stelle 9 ist 9 = 10 - 1 (Lesart "Komplement"): ausdruecklich ungueltig.
        // Dieser Vektor unterscheidet "Rest" (gueltig bei 1) und "Komplement" (gueltig bei 9).
        assertFalse(ik(SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT));
    }

    @Test
    void ak8_ikUngueltigNull() {
        // Summe 21, Rest 1; Stelle 9 ist 0.
        assertFalse(ik(SynthetischeKvNummern.IK_UNGUELTIG_NULL));
    }

    @Test
    void ak8_ikUngueltigZiffernVertauscht() {
        // Stellen 3-8 = 124356: 2 + 2 + 8 + 3 + (10 -> 1) + 6 = 22, Rest 2; Stelle 9 ist 1.
        assertFalse(ik(SynthetischeKvNummern.IK_UNGUELTIG_VERTAUSCHT));
    }

    /**
     * Stellen 1 und 2 gehen nicht in die IK-Summe ein. Gezeigt an einer nach IK ungueltigen Basis: gingen die
     * Stellen ein, wuerde mindestens eine der 100 Belegungen gueltig. Es wird nie eine Nummer ohne Praefix 00
     * gebildet, die nach einem der beiden Verfahren gueltig ist: Die Basis ist nach IK ungueltig (Referenz), und
     * Belegungen, die nach LANR gueltig waeren, werden vorab aus der Gewichtssumme bestimmt und uebersprungen.
     * Erwartet: je Basis 10 uebersprungen, denn Stellen 1-6 der Basen sind 0,0,1,2,3,4 und Stelle 7 ist 5; die
     * LANR-Summe ist 4*s1 + 9*s2 + 70, gueltig bei 4*s1 + 9*s2 + 70 + 5 durch 10 teilbar, also 4*s1 + 9*s2 gleich
     * 5 modulo 10. Das geht nur mit ungeradem s2 (5 Werte), und dazu gibt es je 2 Werte s1: 10 Belegungen, die
     * uebrigen 90 werden gebildet.
     */
    @Test
    void ak8_ikKlassifikationStellenEinsUndZweiGehenNichtEin() {
        for (String basis : new String[] {SynthetischeKvNummern.IK_UNGUELTIG_NULL,
                SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT}) {
            assertFalse(SynthetischeKvNummern.ikGueltigReferenz(basis));
            assertFalse(SynthetischeKvNummern.lanrGueltigReferenz(basis));
            int basisSumme = SynthetischeKvNummern.lanrGewichtsSumme(basis);
            int basisStelle7 = basis.charAt(6) - '0';
            int basisStelle1 = basis.charAt(0) - '0';
            int basisStelle2 = basis.charAt(1) - '0';
            int gebildet = 0;
            int uebersprungen = 0;
            for (int stelle1 = 0; stelle1 < 10; stelle1++) {
                for (int stelle2 = 0; stelle2 < 10; stelle2++) {
                    int summe = basisSumme - 4 * basisStelle1 - 9 * basisStelle2 + 4 * stelle1 + 9 * stelle2;
                    if ((summe + basisStelle7) % 10 == 0) {
                        uebersprungen++;
                        continue;
                    }
                    String nummer = "" + stelle1 + stelle2 + basis.substring(2);
                    assertFalse(SynthetischeKvNummern.ikGueltigReferenz(nummer), nummer.length() + " Stellen");
                    assertFalse(SynthetischeKvNummern.lanrGueltigReferenz(nummer), nummer.length() + " Stellen");
                    assertFalse(ik(nummer), "Stellen 1 und 2 duerfen nicht eingehen: " + stelle1 + stelle2);
                    gebildet++;
                }
            }
            assertEquals(10, uebersprungen, "uebersprungene Belegungen");
            assertEquals(90, gebildet, "gebildete Belegungen");
        }
    }

    /** Dritte, anders formulierte Rechnung: Quersumme von 2*Ziffer ueber eine feste Tabelle statt p/10 + p%10. */
    private static boolean ikDritteRechnung(int[] d) {
        final int[] quersummeMalZwei = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};
        int summe = quersummeMalZwei[d[2]] + d[3] + quersummeMalZwei[d[4]] + d[5] + quersummeMalZwei[d[6]] + d[7];
        return summe % 10 == d[8];
    }

    @Test
    void ak8_ikDritteRechnungStimmtBeiAllenVektorenMitProduktUeberein() {
        for (String nummer : SynthetischeKvNummern.alleNeunstelligenNummern()) {
            assertEquals(ikDritteRechnung(ziffern(nummer)), ik(nummer), "Vektor " + nummer);
        }
    }

    @Test
    void markerNummernSindGueltig() {
        assertTrue(lanr(SynthetischeKvNummern.LANR_MARKER));
        assertTrue(ik(SynthetischeKvNummern.IK_MARKER));
    }

    // ------------------------------------------------------------------ Eigenschaften

    /** Neun Ziffern mit Praefix 00 und sieben Zufallsziffern (nie eine Nummer ohne 00). */
    private static String zufallsNummerMitPraefix00(Random zufall) {
        StringBuilder sb = new StringBuilder("00");
        for (int i = 0; i < 7; i++) {
            sb.append(zufall.nextInt(10));
        }
        return sb.toString();
    }

    @Test
    void eigenschaft_produktGleichReferenzAufZufallsnummern() {
        Random zufall = new Random(SEED);
        int lanrGueltig = 0;
        int ikGueltig = 0;
        for (int i = 0; i < ZUFALLSNUMMERN; i++) {
            String nummer = zufallsNummerMitPraefix00(zufall);
            boolean lanrErwartet = SynthetischeKvNummern.lanrGueltigReferenz(nummer);
            boolean ikErwartet = SynthetischeKvNummern.ikGueltigReferenz(nummer);
            assertEquals(lanrErwartet, lanr(nummer), "LANR weicht ab, Seed " + SEED + ", Iteration " + i);
            assertEquals(ikErwartet, ik(nummer), "IK weicht ab, Seed " + SEED + ", Iteration " + i);
            assertEquals(ikErwartet, ikDritteRechnung(ziffern(nummer)), "IK dritte Rechnung, Iteration " + i);
            lanrGueltig += lanrErwartet ? 1 : 0;
            ikGueltig += ikErwartet ? 1 : 0;
        }
        // Beide Ausgaenge kommen vor (je Verfahren etwa jede zehnte Nummer ist zufaellig gueltig).
        assertTrue(lanrGueltig > 0 && lanrGueltig < ZUFALLSNUMMERN, "LANR ohne beide Ausgaenge");
        assertTrue(ikGueltig > 0 && ikGueltig < ZUFALLSNUMMERN, "IK ohne beide Ausgaenge");
    }

    @Test
    void eigenschaft_gebildeteNummernSindGueltig() {
        Random zufall = new Random(SEED);
        for (int i = 0; i < ZUFALLSNUMMERN; i++) {
            assertTrue(lanr(SynthetischeKvNummern.bildeLanr(zufall)), "LANR, Seed " + SEED + ", Iteration " + i);
            assertTrue(ik(SynthetischeKvNummern.bildeIk(zufall)), "IK, Seed " + SEED + ", Iteration " + i);
        }
    }

    /**
     * Beweis: Eine Ziffernaenderung an Stelle 9 aendert den verglichenen Wert, also ist die Nummer ungueltig. An
     * den Stellen 4, 6 und 8 (Gewicht 1) aendert sich die Summe um die Differenz der Ziffern, die zwischen 1 und 9
     * liegt und modulo 10 nie 0 ist. An den Stellen 3, 5 und 7 (Gewicht 2) ist die Quersumme von 2 mal Ziffer
     * (0,2,4,6,8,1,3,5,7,9 fuer die Ziffern 0 bis 9) eine Permutation von 0 bis 9; zwei verschiedene Ziffern
     * ergeben verschiedene Werte, die Differenz liegt zwischen 1 und 9, modulo 10 nie 0. Es gibt keine Ausnahme;
     * auch der Wechsel 0 und 9 aendert die Summe (um 9). Die Aenderung betrifft nie die Stellen 1 und 2.
     */
    @Test
    void eigenschaft_ikEinzelneZifferAendernMachtUngueltigAnStelle3Bis9() {
        Random zufall = new Random(SEED);
        for (int i = 0; i < 1_000; i++) {
            String gueltig = SynthetischeKvNummern.bildeIk(zufall);
            for (int stelle = 2; stelle < 9; stelle++) {
                for (int neu = 0; neu < 10; neu++) {
                    int[] d = ziffern(gueltig);
                    if (d[stelle] == neu) {
                        continue;
                    }
                    d[stelle] = neu;
                    assertFalse(KvCheckDigits.isValidIk(d),
                            "Seed " + SEED + ", Iteration " + i + ", Stelle " + (stelle + 1));
                }
            }
        }
    }

    @Test
    void eigenschaft_ikWechselNullNeunAnGewichtZweiAendertDieSumme() {
        // Bekannter Sonderfall der Quersumme mod 9: 0 und 9 an einer mit 2 gewichteten Stelle (3, 5, 7).
        // Mit mod 10 und Quersumme gilt: 2*0 = 0 ergibt 0, 2*9 = 18 ergibt 1 + 8 = 9, die Summe aendert sich um 9.
        Random zufall = new Random(SEED);
        int geprueft = 0;
        for (int i = 0; i < 2_000 && geprueft < 50; i++) {
            String gueltig = SynthetischeKvNummern.bildeIk(zufall);
            for (int stelle : new int[] {2, 4, 6}) {
                if (gueltig.charAt(stelle) == '0') {
                    int[] d = ziffern(gueltig);
                    d[stelle] = 9;
                    assertFalse(KvCheckDigits.isValidIk(d), "Seed " + SEED + ", Iteration " + i);
                    geprueft++;
                }
            }
        }
        assertTrue(geprueft >= 50, "zu wenige Faelle mit Ziffer 0 an gewichteter Stelle");
    }

    // ------------------------------------------------------------------ Fehlerfaelle

    private static void pruefeMeldungOhneZiffern(IllegalArgumentException e) {
        String meldung = e.getMessage();
        assertEquals("genau neun Ziffern erwartet", meldung);
        for (int i = 0; i < meldung.length(); i++) {
            assertFalse(Character.isDigit(meldung.charAt(i)), "Meldung enthaelt eine Ziffer");
        }
    }

    @Test
    void fehler_zuKurz() {
        int[] kurz = ziffern(SynthetischeKvNummern.LAENGE_8);
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidLanr(kurz)));
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidIk(kurz)));
    }

    @Test
    void fehler_zuLang() {
        int[] lang = ziffern(SynthetischeKvNummern.LAENGE_10);
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidLanr(lang)));
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidIk(lang)));
    }

    @Test
    void fehler_leer() {
        int[] leer = new int[0];
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidLanr(leer)));
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidIk(leer)));
    }

    @Test
    void fehler_nullFeld() {
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidLanr(null)));
        pruefeMeldungOhneZiffern(assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidIk(null)));
    }

    @Test
    void fehler_wertAusserhalbNullBisNeun() {
        for (int stelle = 0; stelle < 9; stelle++) {
            for (int wert : new int[] {-1, 10, 57, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                int[] d = ziffern(SynthetischeKvNummern.LANR_GUELTIG);
                d[stelle] = wert;
                pruefeMeldungOhneZiffern(
                        assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidLanr(d)));
                pruefeMeldungOhneZiffern(
                        assertThrows(IllegalArgumentException.class, () -> KvCheckDigits.isValidIk(d)));
            }
        }
    }
}
