package de.skillshield.core.recognizer.kv;

/**
 * Prüfziffernverfahren der Krankenversichertennummer (KVNR). Einzige Stelle des Verfahrens im Produkt;
 * {@code KvCheckDigits} bleibt unberührt.
 *
 * <p><b>Quellenvermerk (AK-24).</b> Quelle: Wikipedia, Artikel "Krankenversichertennummer",
 * https://de.wikipedia.org/wiki/Krankenversichertennummer, abgerufen am 23.11.2026. Status: Sekundärquelle, nicht
 * gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: GKV-Datenaustausch, "Prüfziffernberechnung für
 * die Krankenversichertennummer nach § 290 SGB V" (Anlage 1, 2. Januar 2023), sowie "Organisatorische und technische
 * Richtlinien zur Nutzung der Versicherungsnummer nach § 147 SGB VI bei Einführung einer neuen
 * Krankenversichertennummer nach § 290 SGB V", Version 1.5, Anlage 1, nicht eingesehen.
 *
 * <p><b>Verfahren.</b> Eine KVNR besteht aus einem Buchstaben und neun Ziffern; die neunte Ziffer ist die Prüfziffer.
 * Der Buchstabe wird zu seiner zweistelligen Position im Alphabet (A = 01 bis Z = 26). Diese zwei Ziffern und die
 * ersten acht Ziffern der Nummer ergeben zehn Ziffern; sie werden von links abwechselnd mit 1, 2, 1, 2 ... gewichtet,
 * von jedem Produkt wird die Quersumme gebildet (bei Produkten über 9 gleichbedeutend: 9 abziehen), alle Quersummen
 * werden addiert. Die Summe modulo 10 ist die Prüfziffer.
 *
 * <p>Das Ergebnis bestimmt nur die Stufe eines Fundes (und in der Erkennung ohne Kontextwort die Voraussetzung), nie,
 * ob mit Kontextwort ein Fund entsteht. Fehlerhafte Eingaben (Buchstabe außerhalb A-Z und a-z, {@code null}, Anzahl
 * ungleich neun, Wert außerhalb 0 bis 9) führen zu einer {@link IllegalArgumentException} mit fester Meldung, die
 * keine Werte der Eingabe enthält. Weder Meldung noch Rückgabe enthalten die berechnete Prüfziffer. Kein Regex, keine
 * Zustände.
 *
 * <p>Grenzen: Das Verfahren ist nur durch eine Sekundaerquelle belegt (siehe Quellenvermerk). Ein Irrtum darin aendert
 * nur Stufe und Quelle der Funde (0,95 bzw. 0,80 gegen 0,50), nie, ob mit Kontextwort ein Fund entsteht. Nicht
 * Gegenstand ist die zwanzigstellige Nummer mit dem Institutionskennzeichen der Kasse und einer zweiten Pruefziffer.
 * Die Pruefziffer erkennt zufaellig passende Fremdnummern nicht (etwa jede zehnte Nummer ist gueltig); sie ist kein
 * Echtheitsnachweis.
 */
final class KvnrPruefziffer {

    /** Anzahl der Ziffern nach dem Buchstaben (acht Ziffern und die Prüfziffer). */
    private static final int ANZAHL = 9;

    /** Anzahl der Ziffern, die in die Summe eingehen. */
    private static final int ANZAHL_OHNE_PRUEFZIFFER = ANZAHL - 1;

    /** Meldung bei fehlerhafter Eingabe; enthält bewusst keine Werte der Eingabe. */
    private static final String FEHLER = "Buchstabe A-Z und genau neun Ziffern erwartet";

    private KvnrPruefziffer() {
    }

    /**
     * Prüft die Prüfziffer einer KVNR.
     *
     * @param buchstabe A-Z oder a-z (wird für die Rechnung auf A-Z gefaltet)
     * @param ziffern   genau neun Werte 0..9 (acht Ziffern und die Prüfziffer, Index 0 = erste Ziffer)
     * @return {@code true}, wenn die neunte Ziffer der berechneten Prüfziffer entspricht
     * @throws IllegalArgumentException bei Buchstabe außerhalb A-Z/a-z, {@code null}, Anzahl ungleich neun oder Wert
     *                                  außerhalb 0..9; ohne Werte der Eingabe in der Meldung
     */
    static boolean istGueltig(char buchstabe, int[] ziffern) {
        int position = alphabetPosition(buchstabe);
        if (ziffern == null || ziffern.length != ANZAHL) {
            throw new IllegalArgumentException(FEHLER);
        }
        for (int wert : ziffern) {
            if (wert < 0 || wert > 9) {
                throw new IllegalArgumentException(FEHLER);
            }
        }
        int summe = quersumme(position / 10) + quersumme(2 * (position % 10));
        for (int i = 0; i < ANZAHL_OHNE_PRUEFZIFFER; i++) {
            summe += quersumme(ziffern[i] * (i % 2 == 0 ? 1 : 2));
        }
        return summe % 10 == ziffern[ANZAHL_OHNE_PRUEFZIFFER];
    }

    /** Position im Alphabet 1 bis 26; Umlaute und alle anderen Zeichen sind ein Fehler. */
    private static int alphabetPosition(char buchstabe) {
        if (buchstabe >= 'A' && buchstabe <= 'Z') {
            return buchstabe - 'A' + 1;
        }
        if (buchstabe >= 'a' && buchstabe <= 'z') {
            return buchstabe - 'a' + 1;
        }
        throw new IllegalArgumentException(FEHLER);
    }

    /** Quersumme eines Produkts von höchstens 18. */
    private static int quersumme(int zahl) {
        return zahl / 10 + zahl % 10;
    }
}
