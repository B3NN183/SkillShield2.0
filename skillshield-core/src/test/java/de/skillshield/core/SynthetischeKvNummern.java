package de.skillshield.core;

import java.util.List;
import java.util.Random;

/**
 * Zentrale Klasse aller synthetischen IK- und LANR-Testnummern (Feature kv-nummern-erkennung, AK-28, AK-29).
 * Nur hier stehen Nummern mit neun Ziffern (und alle weiteren IK/LANR-Testnummern) als Literal; andere Tests bilden
 * ihre Texte per Verkettung mit diesen Konstanten. {@code TestdatenSynthetischTest} (Regel "Ziffernketten")
 * erzwingt das und verlangt, dass jede neunstellige Ziffernfolge in dieser Datei mit {@code 00} beginnt.
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
 * <p><b>Verfahren (Sekund\u00E4rquellen-Stand, Lesart "Rest").</b> LANR: neun Stellen; Stellen 1 bis 6 von
 * links abwechselnd mit 4 und 9 gewichtet, Summe modulo 10, Pr\u00FCfziffer (Stelle 7) = (10 - Rest) modulo 10;
 * Stellen 8 und 9 (Fachgruppe) werden nicht gepr\u00FCft. IK: neun Stellen; Stellen 1 und 2 (Klassifikation)
 * gehen nicht ein; Stellen 3 bis 8 mit 2, 1, 2, 1, 2, 1 gewichtet, je Produkt die Quersumme, Summe modulo 10
 * ist die Pr\u00FCfziffer (Stelle 9).
 *
 * <p><b>Bildungsregel (A-6, OF-2).</b> Alle Nummern sind rechnerisch gebildet und beginnen mit {@code 00}
 * (LANR: Arztnummer beginnt mit 00; IK: Klassifikation 00). Das Beispiel-IK der Sekund\u00E4rquelle hat eine
 * echte Klassifikation und steht deshalb nirgends im Code. Es gibt keine Ersatz- oder Sammelnummern (Muster
 * aus lauter Neunen) und keine Nummern aus echten Quellen; die Nummern werden nie mit Namen, Adressen oder
 * Diagnosen kombiniert.
 *
 * <p><b>Restrisiko.</b> Es ist nicht belegt, dass Nummern mit f\u00FChrendem {@code 00} real nie vergeben
 * werden. Eine zuf\u00E4llige \u00DCbereinstimmung mit einer real vergebenen Nummer ist deshalb nicht
 * ausgeschlossen; sie w\u00E4re ohne Bezug zu einer Person oder Praxis und ohne Kontext im Test.
 */
public final class SynthetischeKvNummern {

    private SynthetischeKvNummern() {
    }

    // ------------------------------------------------------------------ LANR gueltig (AK-7)

    // Rechenweg LANR: Stellen 1-6 mit 4,9,4,9,4,9 gewichten, Summe S, Pruefziffer = (10 - S mod 10) mod 10 = Stelle 7.

    /** Stellen 1-6 = 001235: 0*4 + 0*9 + 1*4 + 2*9 + 3*4 + 5*9 = 79, Rest 9, Pruefziffer 1 = Stelle 7. */
    public static final String LANR_GUELTIG = "001235101";

    /** Wie oben, Fachgruppe (Stellen 8 und 9) = 99 wird nicht geprueft: Summe 79, Pruefziffer 1. */
    public static final String LANR_GUELTIG_FACHGRUPPE = "001235199";

    /** Stellen 1-6 = 001234: 4 + 18 + 12 + 36 = 70, Rest 0, 10 - 0 = 10 ergibt Pruefziffer 0 = Stelle 7. */
    public static final String LANR_GUELTIG_PRUEFZIFFER_NULL = "001234001";

    /** Stellen 1-6 = 001236: 4 + 18 + 12 + 54 = 88, Rest 8, Pruefziffer 2 = Stelle 7. */
    public static final String LANR_GUELTIG_ZWEI = "001236201";

    // ------------------------------------------------------------------ LANR ungueltig (AK-7)

    /** Summe 79, erwartet 1, Stelle 7 ist 2 (Pruefziffer verschoben). */
    public static final String LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN = "001235201";

    /** Stellen 1-6 = 001325: 4 + 27 + 8 + 45 = 84, Rest 4, erwartet 6, Stelle 7 ist 1 (Nachbarziffern vertauscht). */
    public static final String LANR_UNGUELTIG_VERTAUSCHT = "001325101";

    /** Summe 79, erwartet 1, Stelle 7 ist 0 (eine Ziffer geaendert). */
    public static final String LANR_UNGUELTIG_ZIFFER_GEAENDERT = "001235001";

    // ------------------------------------------------------------------ IK (AK-8)

    // Rechenweg IK: Stellen 3-8 mit 2,1,2,1,2,1 gewichten, je Produkt die Quersumme, Summe S, S mod 10 = Stelle 9.

    /** Stellen 3-8 = 123456: 2 + 2 + 6 + 4 + (10 -> 1) + 6 = 21, Rest 1 = Stelle 9. */
    public static final String IK_GUELTIG = "001234561";

    /** Summe 21, Rest 1; Stelle 9 ist 9 = 10 - 1 (Lesart "Komplement"), hier ausdruecklich ungueltig. */
    public static final String IK_UNGUELTIG_KOMPLEMENT = "001234569";

    /** Summe 21, Rest 1; Stelle 9 ist 0. */
    public static final String IK_UNGUELTIG_NULL = "001234560";

    /** Stellen 3-8 = 124356: 2 + 2 + 8 + 3 + (10 -> 1) + 6 = 22, Rest 2; Stelle 9 ist 1 (Ziffern vertauscht). */
    public static final String IK_UNGUELTIG_VERTAUSCHT = "001243561";

    /** Stellen 3-8 = 123457: 2 + 2 + 6 + 4 + (10 -> 1) + 7 = 22, Rest 2 = Stelle 9 (fuer Aufzaehlungen, T-5). */
    public static final String IK_GUELTIG_ZWEI = "001234572";

    /** Stellen 3-8 = 123458: 2 + 2 + 6 + 4 + (10 -> 1) + 8 = 23, Rest 3 = Stelle 9 (fuer Aufzaehlungen, T-5). */
    public static final String IK_GUELTIG_DREI = "001234583";

    // ------------------------------------------------------------------ Marker-Nummern (T-9)

    /**
     * Marker-LANR, gueltig. Stellen 1-6 = 007382: 0 + 0 + 28 + 27 + 32 + 18 = 105, Rest 5, Pruefziffer 5 = Stelle 7;
     * Fachgruppe 07. Von allen anderen Nummern dieser Klasse verschieden, auch in den ersten sechs Ziffern.
     */
    public static final String LANR_MARKER = "007382507";

    /**
     * Marker-IK, gueltig. Stellen 3-8 = 591648: 10 -> 1, 9, 2, 6, 8, 8 ergibt 1 + 9 + 2 + 6 + 8 + 8 = 34, Rest 4 =
     * Stelle 9. Von allen anderen Nummern dieser Klasse verschieden, auch in den ersten sechs Ziffern.
     */
    public static final String IK_MARKER = "005916484";

    // ------------------------------------------------------------------ Trennerformen (AK-6)

    /** LANR mit Leerzeichen, Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_LEERZEICHEN = "001 235 101";

    /** LANR mit Leerzeichen, ungleiche Gruppen 7-2; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_UNGLEICHE_GRUPPEN = "0012351 01";

    /** LANR mit geschuetztem Leerzeichen (NBSP), Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_NBSP = "001\u00A0235\u00A0101";

    /** LANR mit Schraegstrich, Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_SCHRAEGSTRICH = "001/235/101";

    /** IK mit Bindestrich, Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_BINDESTRICH = "001-234-561";

    /** IK mit Punkt, Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_PUNKT = "001.234.561";

    /** IK mit Leerzeichen, Gruppen 6-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_GRUPPE_6_3 = "001234 561";

    /** IK mit geschuetztem Leerzeichen (NBSP), Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_NBSP = "001\u00A0234\u00A0561";

    /** Neun Ziffern mit Punkten, zugleich gueltige IPv4 im Dokumentationsbereich 198.51.100.0/24 (AK-18). */
    public static final String IK_FORM_DOKU_IP = "198.51.100.1";

    // ------------------------------------------------------------------ Laengenproben (AK-11)

    /** Fuenf Ziffern: zu kurz (unter 6). */
    public static final String LAENGE_5 = "12345";

    /** Sechs Ziffern: untere Grenze der Laengenregel. */
    public static final String LAENGE_6 = "001235";

    /** Sieben Ziffern (AK-11, LANR). */
    public static final String LAENGE_7 = "0012351";

    /** Acht Ziffern (AK-11, IK). */
    public static final String LAENGE_8 = "00123456";

    /** Zehn Ziffern (AK-11, LANR). */
    public static final String LAENGE_10 = "0012351012";

    /** Zwoelf Ziffern: obere Grenze der Laengenregel. */
    public static final String LAENGE_12 = "001235101234";

    /** Dreizehn Ziffern: zu lang (ueber 12). */
    public static final String LAENGE_13 = "1234567890123";

    /** Vier Ziffern (AK-11: {@code IK 1234}). */
    public static final String LAENGE_4 = "1234";

    /** Telefonnummer-Probe mit Trennern, zehn Ziffern (AK-10); keine KV-Nummer. */
    public static final String TELEFON_PROBE = "0123 456 789";

    // ------------------------------------------------------------------ Gesamtliste

    /** Alle Nummern mit neun Ziffern (ohne Trennerformen und Doku-IP), gueltige und ungueltige, samt Markern. */
    public static List<String> alleNeunstelligenNummern() {
        return List.of(LANR_GUELTIG, LANR_GUELTIG_FACHGRUPPE, LANR_GUELTIG_PRUEFZIFFER_NULL, LANR_GUELTIG_ZWEI,
                LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LANR_UNGUELTIG_VERTAUSCHT, LANR_UNGUELTIG_ZIFFER_GEAENDERT,
                IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, IK_UNGUELTIG_KOMPLEMENT, IK_UNGUELTIG_NULL,
                IK_UNGUELTIG_VERTAUSCHT, LANR_MARKER, IK_MARKER);
    }

    // ------------------------------------------------------------------ unabhaengige Referenzpruefungen

    /** Gewicht je Stelle (Index 0 = Stelle 1) fuer LANR; Stellen 7 bis 9 gehen nicht in die Summe ein. */
    private static final int[] LANR_GEWICHTE = {4, 9, 4, 9, 4, 9, 0, 0, 0};

    /** Gewicht je Stelle (Index 0 = Stelle 1) fuer IK; Klassifikation und Pruefziffer gehen nicht ein. */
    private static final int[] IK_GEWICHTE = {0, 0, 2, 1, 2, 1, 2, 1, 0};

    /** Genau neun ASCII-Ziffern? */
    private static boolean istNeunZiffern(String s) {
        if (s == null || s.length() != 9) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static void verlangeNeunZiffern(String s) {
        if (!istNeunZiffern(s)) {
            throw new IllegalArgumentException("genau neun Ziffern erwartet");
        }
    }

    /** Gewichtete Summe der Stellen 1 bis 6 (LANR): Beispiel {@link #LANR_GUELTIG} ergibt 79. */
    public static int lanrGewichtsSumme(String nummer) {
        verlangeNeunZiffern(nummer);
        int summe = 0;
        for (int i = 0; i < 9; i++) {
            summe += LANR_GEWICHTE[i] * (nummer.charAt(i) - '0');
        }
        return summe;
    }

    /** Summe der Quersummen der gewichteten Stellen 3 bis 8 (IK): Beispiel {@link #IK_GUELTIG} ergibt 21. */
    public static int ikQuersummenSumme(String nummer) {
        verlangeNeunZiffern(nummer);
        int summe = 0;
        for (int i = 0; i < 9; i++) {
            int produkt = IK_GEWICHTE[i] * (nummer.charAt(i) - '0');
            summe += produkt / 10 + produkt % 10;
        }
        return summe;
    }

    /**
     * Unabhaengige Referenz LANR (Gewichtstabelle statt Zeilenformel des Produkts): gueltig, wenn die Nummer aus genau
     * neun ASCII-Ziffern besteht und Summe + Stelle 7 durch 10 teilbar ist. Alles andere (auch {@code null}) ist
     * ungueltig, ohne Ausnahme.
     */
    public static boolean lanrGueltigReferenz(String nummer) {
        if (!istNeunZiffern(nummer)) {
            return false;
        }
        return (lanrGewichtsSumme(nummer) + (nummer.charAt(6) - '0')) % 10 == 0;
    }

    /**
     * Unabhaengige Referenz IK (Lesart "Rest"): gueltig, wenn die Nummer aus genau neun ASCII-Ziffern besteht und die
     * Quersummensumme modulo 10 gleich Stelle 9 ist. Alles andere (auch {@code null}) ist ungueltig, ohne Ausnahme.
     */
    public static boolean ikGueltigReferenz(String nummer) {
        if (!istNeunZiffern(nummer)) {
            return false;
        }
        return ikQuersummenSumme(nummer) % 10 == nummer.charAt(8) - '0';
    }

    // ------------------------------------------------------------------ rechnerische Bildung

    /** Bildet rechnerisch eine nach {@link #lanrGueltigReferenz} gueltige LANR mit Praefix {@code 00}. */
    public static String bildeLanr(Random zufall) {
        StringBuilder sb = new StringBuilder("00");
        for (int i = 0; i < 4; i++) {
            sb.append(zufall.nextInt(10));
        }
        sb.append('0').append(zufall.nextInt(10)).append(zufall.nextInt(10));
        int summe = lanrGewichtsSumme(sb.toString());
        int pruefziffer = 0;
        while ((summe + pruefziffer) % 10 != 0) {
            pruefziffer++;
        }
        sb.setCharAt(6, (char) ('0' + pruefziffer));
        return sb.toString();
    }

    /** Bildet rechnerisch ein nach {@link #ikGueltigReferenz} gueltiges IK mit Praefix {@code 00}. */
    public static String bildeIk(Random zufall) {
        StringBuilder sb = new StringBuilder("00");
        for (int i = 0; i < 6; i++) {
            sb.append(zufall.nextInt(10));
        }
        sb.append('0');
        int rest = ikQuersummenSumme(sb.toString()) % 10;
        sb.setCharAt(8, (char) ('0' + rest));
        return sb.toString();
    }
}
