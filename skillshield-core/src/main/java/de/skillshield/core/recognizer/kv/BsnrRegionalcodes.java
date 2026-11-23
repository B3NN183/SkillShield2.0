package de.skillshield.core.recognizer.kv;

/**
 * Regionalcodes der Betriebsstaettennummer (BSNR). Einzige Stelle der Liste im Produkt (Feature
 * kvnr-bsnr-erkennung, Anforderung A-5, D-8, AK-27).
 *
 * <p><b>Quellenvermerk (AK-24, sinngemaess).</b> Quelle: "Richtlinie der Kassenaerztlichen Bundesvereinigung nach
 * Paragraph 75 Absatz 7 SGB V zur Vergabe der Arzt-, Betriebsstaetten-, Praxisnetz- sowie der Netzverbundnummern",
 * Stand 1. Januar 2024 (kbv.de), Paragraph 6 und Anlage 1, abgerufen am 23.11.2026 (Abruf durch die Hauptsitzung).
 * Status: Primaerquelle laut Beleg der Hauptsitzung; der Entwickler hat die Richtlinie nicht selbst eingesehen und die
 * Liste nicht gegen sie verifiziert. Die Liste ist eine pflegbare Konstante ({@link #SCHLUESSEL}); bei einer neuen
 * Fassung der Richtlinie wird nur sie geaendert.
 *
 * <p><b>Aufbau der BSNR laut Quelle.</b> Neun Ziffern; die Ziffern 1 und 2 sind der Landes- oder
 * Bezirksstellenschluessel der Kassenaerztlichen Vereinigung (Anlage 1), die Ziffern 3 bis 9 vergibt die
 * Vereinigung. Eine Pruefziffer nennt die Richtlinie nicht; deshalb gibt es hier keine Pruefziffernpruefung.
 *
 * <p><b>Behandlung der Schluessel.</b>
 * <ul>
 *   <li>Bekannt sind die 78 Schluessel der Anlage 1: 01 bis 03, 06 bis 21, 24, 25, 27, 28, 31, 37 bis 73, 78 bis 81,
 *       83, 85 bis 91, 93 bis 96 und 98.</li>
 *   <li>Sonderkennungen 35 und 75 (besondere Versorgungsbereiche, Paragraph 6 Absatz 3): Die Ziffern 1 und 2 allein
 *       sind <em>nicht</em> bekannt ({@link #istBekannt(int, int)} ergibt {@code false}); bekannt ist eine solche BSNR
 *       nur, wenn an den Stellen 3 und 4 ein Schluessel der Anlage 1 steht (die Methode mit vier Ziffern). Steht
 *       dort wieder 35, 75 oder ein anderer Wert ausserhalb der Liste, ist sie unbekannt.</li>
 *   <li>Unbekannt sind alle anderen Werte, insbesondere 00 und 99 (Netzverbundnummer, Paragraph 16, beginnt mit 99;
 *       hinter einem BSNR-Kontextwort ein Fund niedriger Stufe, Schutz geht vor) sowie 74 und alle weiteren Werte
 *       ausserhalb der Liste. Feste Kennungen wie 00 (ASV-Teamnummer) oder 74 sind nicht in der Richtlinie belegt und
 *       gelten bis zu einem Beleg als unbekannt.</li>
 * </ul>
 *
 * <p>Der Regionalcode ist nur ein schwaches Plausibilitaetsmerkmal: Er bestimmt Stufe und Quelle eines Fundes, nie, ob
 * ein Fund entsteht (D-8). Fehlerhafte Eingaben (Ziffern ausserhalb 0 bis 9) fuehren zu einer
 * {@link IllegalArgumentException} mit fester Meldung, die weder Eingabewerte noch die Liste enthaelt. Kein Regex,
 * keine Ausgabe, kein Zustand ausser der unveraenderlichen Tabelle.
 */
final class BsnrRegionalcodes {

    /** Die 78 Schluessel der Anlage 1 als Zahl 0 bis 99 (Ziffer 1 mal zehn plus Ziffer 2), aufsteigend. */
    private static final int[] SCHLUESSEL = {
        1, 2, 3,
        6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
        24, 25, 27, 28, 31,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63,
        64, 65, 66, 67, 68, 69, 70, 71, 72, 73,
        78, 79, 80, 81, 83,
        85, 86, 87, 88, 89, 90, 91,
        93, 94, 95, 96, 98
    };

    /** Die Sonderkennungen 35 und 75 (Paragraph 6 Absatz 3), je mit Schluessel an den Stellen 3 und 4. */
    private static final int SONDER_1 = 35;
    private static final int SONDER_2 = 75;

    /** Nachschlagetabelle, Index = Zahl aus den Ziffern 1 und 2. */
    private static final boolean[] BEKANNT = new boolean[100];

    /** Meldung bei fehlerhafter Eingabe; enthaelt bewusst weder Werte der Eingabe noch die Liste. */
    private static final String FEHLER = "Ziffern von null bis neun erwartet";

    static {
        for (int schluessel : SCHLUESSEL) {
            BEKANNT[schluessel] = true;
        }
    }

    private BsnrRegionalcodes() {
    }

    /** Zahl der bekannten Schluessel der Anlage 1 (78). */
    static int anzahl() {
        return SCHLUESSEL.length;
    }

    /**
     * Ob die Ziffern 1 und 2 ein Schluessel der Anlage 1 sind. Die Sonderkennungen 35 und 75 sind hier
     * {@code false}; fuer eine BSNR ist {@link #istBekannt(int, int, int, int)} massgeblich.
     *
     * @param ersteZiffer  Wert 0 bis 9
     * @param zweiteZiffer Wert 0 bis 9
     * @throws IllegalArgumentException bei einem Wert ausserhalb 0 bis 9; ohne Werte in der Meldung
     */
    static boolean istBekannt(int ersteZiffer, int zweiteZiffer) {
        pruefeZiffer(ersteZiffer);
        pruefeZiffer(zweiteZiffer);
        return BEKANNT[ersteZiffer * 10 + zweiteZiffer];
    }

    /**
     * Ob die ersten vier Ziffern einer BSNR einen bekannten Regionalcode ergeben: ein Schluessel der Anlage 1 an den
     * Stellen 1 und 2, oder bei den Sonderkennungen 35 und 75 ein Schluessel der Anlage 1 an den Stellen 3 und 4.
     *
     * @param ersteZiffer   Wert 0 bis 9
     * @param zweiteZiffer  Wert 0 bis 9
     * @param dritteZiffer  Wert 0 bis 9
     * @param vierteZiffer  Wert 0 bis 9
     * @throws IllegalArgumentException bei einem Wert ausserhalb 0 bis 9; ohne Werte in der Meldung
     */
    static boolean istBekannt(int ersteZiffer, int zweiteZiffer, int dritteZiffer, int vierteZiffer) {
        pruefeZiffer(ersteZiffer);
        pruefeZiffer(zweiteZiffer);
        pruefeZiffer(dritteZiffer);
        pruefeZiffer(vierteZiffer);
        int erste = ersteZiffer * 10 + zweiteZiffer;
        if (erste == SONDER_1 || erste == SONDER_2) {
            return BEKANNT[dritteZiffer * 10 + vierteZiffer];
        }
        return BEKANNT[erste];
    }

    /** Ob die Ziffern 1 und 2 eine Sonderkennung (35 oder 75) sind; Werte ausserhalb 0 bis 9 sind ein Fehler. */
    static boolean istSonderkennung(int ersteZiffer, int zweiteZiffer) {
        pruefeZiffer(ersteZiffer);
        pruefeZiffer(zweiteZiffer);
        int zahl = ersteZiffer * 10 + zweiteZiffer;
        return zahl == SONDER_1 || zahl == SONDER_2;
    }

    private static void pruefeZiffer(int wert) {
        if (wert < 0 || wert > 9) {
            throw new IllegalArgumentException(FEHLER);
        }
    }
}
