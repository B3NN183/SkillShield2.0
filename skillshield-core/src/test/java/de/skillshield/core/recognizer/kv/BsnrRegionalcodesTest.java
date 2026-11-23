package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests der Regionalcode-Liste der BSNR (Feature kvnr-bsnr-erkennung, T-12, AK-27, AK-34).
 *
 * <p>Quelle der Erwartung (sinngemaess AK-24): KBV-Richtlinie nach Paragraph 75 Absatz 7 SGB V zur Vergabe der Arzt-,
 * Betriebsstaetten-, Praxisnetz- sowie der Netzverbundnummern, Stand 1. Januar 2024, Paragraph 6 und Anlage 1,
 * abgerufen am 23.11.2026 durch die Hauptsitzung. Status: vom Entwickler nicht selbst gegen die Richtlinie
 * verifiziert. Das Orakel unten ist unabhaengig vom Produkt als Bereichsliste geschrieben (das Produkt fuehrt die
 * einzelnen Werte), die Gesamtzahl 78 steht als eigener Wert fest.
 */
class BsnrRegionalcodesTest {

    /** Bereiche der Anlage 1 (von, bis, beide inklusive), unabhaengig vom Produkt geschrieben. */
    private static final int[][] ANLAGE_1 = {
        {1, 3}, {6, 21}, {24, 25}, {27, 28}, {31, 31}, {37, 73}, {78, 81}, {83, 83}, {85, 91}, {93, 96}, {98, 98}
    };

    /** Zahl der Schluessel laut Anlage 1. */
    private static final int ANZAHL_SCHLUESSEL = 78;

    private static boolean orakel(int zahl) {
        for (int[] bereich : ANLAGE_1) {
            if (zahl >= bereich[0] && zahl <= bereich[1]) {
                return true;
            }
        }
        return false;
    }

    private static List<Integer> alleListenCodes() {
        List<Integer> codes = new ArrayList<>();
        for (int zahl = 0; zahl < 100; zahl++) {
            if (orakel(zahl)) {
                codes.add(zahl);
            }
        }
        return codes;
    }

    @Test
    void orakel_hatGenau78Schluessel_unddasProduktFuehrtEbensoViele() {
        assertEquals(ANZAHL_SCHLUESSEL, alleListenCodes().size(), "Orakel");
        assertEquals(ANZAHL_SCHLUESSEL, BsnrRegionalcodes.anzahl(), "Produkt");
        int bekannte = 0;
        for (int zahl = 0; zahl < 100; zahl++) {
            bekannte += BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10) ? 1 : 0;
        }
        assertEquals(ANZAHL_SCHLUESSEL, bekannte, "Zahl der bekannten Codes nach Abfrage");
    }

    @Test
    void jederListenCode_istBekannt_zweiZiffern() {
        for (int zahl : alleListenCodes()) {
            assertTrue(BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10), "Listencode nicht bekannt, Nr. " + zahl);
        }
    }

    @Test
    void jederListenCode_istBekannt_auchMitVierZiffern_beliebigeStellenDreUndVier() {
        for (int zahl : alleListenCodes()) {
            for (int rest = 0; rest < 100; rest += 7) {
                assertTrue(BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10, rest / 10, rest % 10),
                        "Listencode mit vier Ziffern nicht bekannt, Nr. " + zahl);
            }
        }
    }

    @Test
    void jederWertAusserhalbDerListe_istUnbekannt_einschliesslichNullNullUndNeunUndNeunzig() {
        int unbekannt = 0;
        for (int zahl = 0; zahl < 100; zahl++) {
            if (orakel(zahl)) {
                continue;
            }
            unbekannt++;
            assertFalse(BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10), "unbekannter Wert bekannt, Nr. " + zahl);
        }
        assertEquals(100 - ANZAHL_SCHLUESSEL, unbekannt);
        // 00, 99 und 74 ausdruecklich: nicht in der Richtlinie belegt bzw. Netzverbundnummer
        assertFalse(BsnrRegionalcodes.istBekannt(0, 0));
        assertFalse(BsnrRegionalcodes.istBekannt(9, 9));
        assertFalse(BsnrRegionalcodes.istBekannt(7, 4));
        // mindestens drei weitere unbekannte Codes aus den Luecken der Liste
        assertFalse(BsnrRegionalcodes.istBekannt(0, 4));
        assertFalse(BsnrRegionalcodes.istBekannt(2, 2));
        assertFalse(BsnrRegionalcodes.istBekannt(9, 2));
        assertFalse(BsnrRegionalcodes.istBekannt(8, 2));
    }

    @Test
    void unbekannterCode_istAuchMitVierZiffernUnbekannt_stellenDreUndVierAendernNichts() {
        for (int zahl = 0; zahl < 100; zahl++) {
            if (orakel(zahl) || zahl == 35 || zahl == 75) {
                continue;
            }
            assertFalse(BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10, 1, 7),
                    "unbekannter Wert mit Schluessel an Stelle 3 und 4 ist bekannt, Nr. " + zahl);
        }
    }

    @Test
    void grenzwerte_derBereiche_innenBekanntUndAussenUnbekannt() {
        for (int[] bereich : ANLAGE_1) {
            assertTrue(BsnrRegionalcodes.istBekannt(bereich[0] / 10, bereich[0] % 10), "untere Grenze");
            assertTrue(BsnrRegionalcodes.istBekannt(bereich[1] / 10, bereich[1] % 10), "obere Grenze");
        }
        // jeweils der Nachbar ausserhalb: 04, 05, 22, 23, 26, 29, 30, 32, 36, 74, 77, 82, 84, 92, 97
        int[] draussen = {4, 5, 22, 23, 26, 29, 30, 32, 36, 74, 77, 82, 84, 92, 97};
        for (int zahl : draussen) {
            assertFalse(BsnrRegionalcodes.istBekannt(zahl / 10, zahl % 10), "Nachbar ausserhalb bekannt, Nr. " + zahl);
        }
        // kleinster und groesster Wert des Wertebereichs
        assertFalse(BsnrRegionalcodes.istBekannt(0, 0));
        assertTrue(BsnrRegionalcodes.istBekannt(0, 1));
        assertTrue(BsnrRegionalcodes.istBekannt(9, 8));
        assertFalse(BsnrRegionalcodes.istBekannt(9, 9));
    }

    @Test
    void sonderkennungen35Und75_sindMitZweiZiffernUnbekannt_mitVierZiffernNurMitSchluesselAnStelleDreUndVier() {
        for (int[] sonder : new int[][] {{3, 5}, {7, 5}}) {
            assertTrue(BsnrRegionalcodes.istSonderkennung(sonder[0], sonder[1]));
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1]), "Sonderkennung allein");
            // Schluessel der Anlage 1 an Stelle 3 und 4: bekannt
            assertTrue(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 1, 7));
            assertTrue(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 0, 1));
            assertTrue(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 9, 8));
            // unbekannter Wert, 35, 75 und 99 an Stelle 3 und 4: unbekannt
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 0, 0));
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 9, 9));
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 3, 5));
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 7, 5));
            assertFalse(BsnrRegionalcodes.istBekannt(sonder[0], sonder[1], 7, 4));
        }
        // Gegenprobe: ein gewoehnlicher Schluessel ist keine Sonderkennung und braucht die Stellen 3 und 4 nicht
        assertFalse(BsnrRegionalcodes.istSonderkennung(1, 7));
        assertTrue(BsnrRegionalcodes.istBekannt(1, 7, 0, 0));
        assertFalse(BsnrRegionalcodes.istSonderkennung(0, 0));
        assertFalse(BsnrRegionalcodes.istSonderkennung(9, 9));
    }

    @Test
    void fehlerfaelle_ziffernAusserhalbNullBisNeun_werfenIllegalArgumentException_ohneWerteInDerMeldung() {
        int[][] schlecht = {{-1, 0}, {0, -1}, {10, 0}, {0, 10}, {Integer.MIN_VALUE, 5}, {5, Integer.MAX_VALUE}};
        for (int[] paar : schlecht) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> BsnrRegionalcodes.istBekannt(paar[0], paar[1]));
            pruefeMeldung(e, paar);
            e = assertThrows(IllegalArgumentException.class,
                    () -> BsnrRegionalcodes.istBekannt(paar[0], paar[1], 1, 7));
            pruefeMeldung(e, paar);
            e = assertThrows(IllegalArgumentException.class,
                    () -> BsnrRegionalcodes.istBekannt(1, 7, paar[0], paar[1]));
            pruefeMeldung(e, paar);
            e = assertThrows(IllegalArgumentException.class,
                    () -> BsnrRegionalcodes.istSonderkennung(paar[0], paar[1]));
            pruefeMeldung(e, paar);
        }
    }

    private static void pruefeMeldung(IllegalArgumentException e, int[] paar) {
        String meldung = e.getMessage();
        assertTrue(meldung != null && !meldung.isEmpty(), "Meldung fehlt");
        for (int wert : paar) {
            assertFalse(meldung.contains(String.valueOf(wert)), "Die Meldung nennt einen Eingabewert");
        }
        // keine Listenwerte (zwei Ziffern in Folge) in der Meldung
        for (int zahl : alleListenCodes()) {
            String text = zahl < 10 ? "0" + zahl : String.valueOf(zahl);
            assertFalse(meldung.contains(text), "Die Meldung nennt einen Listenwert");
        }
    }

    @Test
    void fehlerfall_gueltigerWertNebenUngueltigem_wirftAuchBeiBekanntemCode() {
        assertThrows(IllegalArgumentException.class, () -> BsnrRegionalcodes.istBekannt(1, 7, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> BsnrRegionalcodes.istBekannt(3, 5, -1, 7));
        assertThrows(IllegalArgumentException.class, () -> BsnrRegionalcodes.istBekannt(1, 10, 1, 7));
    }

    @Test
    void anzahl_istUnveraenderlichUndAbfragenSindWiederholbar() {
        assertEquals(BsnrRegionalcodes.anzahl(), BsnrRegionalcodes.anzahl());
        for (int lauf = 0; lauf < 3; lauf++) {
            assertTrue(BsnrRegionalcodes.istBekannt(4, 3));
            assertFalse(BsnrRegionalcodes.istBekannt(0, 0));
        }
    }
}
