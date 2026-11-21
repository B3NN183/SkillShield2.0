package de.skillshield.core.recognizer.kv;

/**
 * Pr\u00FCfziffernverfahren der Lebenslangen Arztnummer (LANR) und des Institutionskennzeichens (IK). Beide Nummern
 * haben genau neun Stellen; die Pr\u00FCfung erwartet sie als Ziffernwerte {@code 0} bis {@code 9}.
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
 * <p><b>LANR.</b> Die Stellen 1 bis 6 werden von links abwechselnd mit 4 und 9 gewichtet, die Summe modulo 10
 * ergibt den Rest; die Pr\u00FCfziffer (Stelle 7) ist {@code (10 - Rest) modulo 10}, bei Rest 0 also 0. Die
 * Stellen 8 und 9 (Fachgruppe) werden nicht gepr\u00FCft.
 *
 * <p><b>IK (Lesart "Rest", AK-8).</b> Die Stellen 1 und 2 (Klassifikation) gehen nicht ein. Die Stellen 3 bis 8
 * werden von links mit 2, 1, 2, 1, 2, 1 gewichtet; von jedem Produkt wird die Quersumme gebildet (bei Produkten
 * \u00FCber 9 gleichbedeutend: 9 abziehen). Die Summe modulo 10 ist die Pr\u00FCfziffer (Stelle 9).
 *
 * <p>Das Ergebnis bestimmt in diesem Feature nur Stufe und Quelle eines Fundes, nie, ob ein Fund entsteht (A-2).
 * Fehlerhafte Eingaben (Anzahl ungleich neun, Werte au\u00DFerhalb 0 bis 9, {@code null}) f\u00FChren zu einer
 * {@link IllegalArgumentException}, deren Meldung keine Werte der Eingabe enth\u00E4lt. Kein Regex, keine
 * Zust\u00E4nde; nur Grundrechenarten.
 */
final class KvCheckDigits {

    /** Anzahl der Stellen von LANR und IK. */
    private static final int LENGTH = 9;

    /** Meldung bei fehlerhafter Eingabe; enth\u00E4lt bewusst keine Werte der Eingabe. */
    private static final String FEHLER = "genau neun Ziffern erwartet";

    private KvCheckDigits() {
    }

    /**
     * Pr\u00FCft die Pr\u00FCfziffer einer LANR.
     *
     * @param d genau neun Werte 0 bis 9 (Index 0 = Stelle 1)
     * @return {@code true}, wenn Stelle 7 der Pr\u00FCfziffer entspricht; Stellen 8 und 9 werden nicht gepr\u00FCft
     * @throws IllegalArgumentException bei {@code null}, Anzahl ungleich neun oder Wert au\u00DFerhalb 0 bis 9
     */
    static boolean isValidLanr(int[] d) {
        pruefeEingabe(d);
        int summe = 0;
        for (int i = 0; i < 6; i++) {
            summe += d[i] * (i % 2 == 0 ? 4 : 9);
        }
        int pruefziffer = (10 - summe % 10) % 10;
        return pruefziffer == d[6];
    }

    /**
     * Pr\u00FCft die Pr\u00FCfziffer eines IK (Lesart "Rest").
     *
     * @param d genau neun Werte 0 bis 9 (Index 0 = Stelle 1)
     * @return {@code true}, wenn Stelle 9 der Quersummensumme der Stellen 3 bis 8 modulo 10 entspricht
     * @throws IllegalArgumentException bei {@code null}, Anzahl ungleich neun oder Wert au\u00DFerhalb 0 bis 9
     */
    static boolean isValidIk(int[] d) {
        pruefeEingabe(d);
        int summe = 0;
        for (int i = 2; i < 8; i++) {
            int produkt = d[i] * (i % 2 == 0 ? 2 : 1);
            summe += produkt / 10 + produkt % 10;
        }
        return summe % 10 == d[8];
    }

    private static void pruefeEingabe(int[] d) {
        if (d == null || d.length != LENGTH) {
            throw new IllegalArgumentException(FEHLER);
        }
        for (int wert : d) {
            if (wert < 0 || wert > 9) {
                throw new IllegalArgumentException(FEHLER);
            }
        }
    }
}
