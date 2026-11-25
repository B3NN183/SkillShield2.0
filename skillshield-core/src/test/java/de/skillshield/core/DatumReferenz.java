package de.skillshield.core;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Unabhaengige Referenz fuer die Datumsregeln von Teil A des Features datum-und-kontextvorrang (Anforderung D-2,
 * D-3, D-5; Architektur Kap. 1.1). Sie benutzt nichts aus dem Produkt: Die Form wird durch Zerlegen in Ziffernbloecke
 * und Trenner geprueft, die kalendarische Gueltigkeit durch {@link LocalDate} (Gregorianischer Kalender).
 *
 * <p>Ein Segment (ein Ausschnitt {@code [start, end)} des Textes, so wie der Scanner ihn als Fund ausgibt) ist ein
 * <b>Datumssegment</b>, wenn es nicht genau neun Ziffern hat und
 * <ol>
 * <li>genau aus drei Ziffernbloecken mit zweimal demselben Trenner besteht und eine Form nach D-2 (a) bis (d) hat:
 * Punkt mit Tag und Monat ein- oder zweistellig und Jahr vier- oder zweistellig; Schraegstrich oder Bindestrich mit
 * Tag und Monat ein- oder zweistellig und vierstelligem Jahr; Bindestrich mit vierstelligem Jahr zuerst, Monat und
 * Tag zweistellig (ISO); und das Datum kalendarisch moeglich ist (Jahr vierstellig 1900 bis 2099; zweistelliges Jahr
 * nur, wenn das Datum in 19jj <b>und</b> in 20jj moeglich ist, also ist {@code 29.02.00} kein Datum), oder</li>
 * <li>am Segmentanfang ein Zeitraum nach D-2 (e) steht: zwei Daten der Form Punkt mit vierstelligem Jahr, durch genau
 * ein {@code -} ohne Leerraum verbunden, beide gueltig; das Segment endet hinter dem {@code -} und hoechstens am Ende
 * des zweiten Datums, und hinter dem zweiten Datum geht die Kette nicht weiter (kein Gruppentrenner mit folgender
 * Ziffer; Architektur Kap. 13 Punkt 3).</li>
 * </ol>
 * Ein Segment mit Buchstaben beginnt nie mit einer Ziffer und ist deshalb nie ein Datum. Alle Methoden sind
 * zustandslos; sie nennen in keiner Meldung Text oder Ziffern.
 */
public final class DatumReferenz {

    /** Kleinstes vierstelliges Jahr (D-3). */
    public static final int JAHR_MIN = 1900;
    /** Groesstes vierstelliges Jahr (D-3). */
    public static final int JAHR_MAX = 2099;

    private static final int NEUN = 9;
    private static final char NBSP = (char) 0xA0;
    private static final char DUENNES_LEERZEICHEN = (char) 0x2009;
    private static final char SCHMALES_NBSP = (char) 0x202F;

    private DatumReferenz() {
    }

    /** Ob das Zeichen eine ASCII-Ziffer ist. */
    public static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    /** Ob das Zeichen ein Gruppentrenner der Nummernerkennung ist (Leerraum, U+00A0, U+2009, U+202F, - . /). */
    public static boolean istGruppentrenner(char c) {
        return c == ' ' || c == NBSP || c == DUENNES_LEERZEICHEN || c == SCHMALES_NBSP || c == '-' || c == '.'
                || c == '/';
    }

    /**
     * Kalenderpruefung mit {@link LocalDate}: Jahr im vierstelligen Bereich {@value #JAHR_MIN} bis {@value #JAHR_MAX},
     * Monat 1 bis 12, Tag 1 bis Monatslaenge (29.02. nur im Schaltjahr).
     */
    public static boolean kalenderGueltig(int tag, int monat, int jahr) {
        if (jahr < JAHR_MIN || jahr > JAHR_MAX) {
            return false;
        }
        return moeglich(tag, monat, jahr);
    }

    /** Zweistelliges Jahr: gueltig nur, wenn das Datum in 19jj und in 20jj kalendarisch moeglich ist. */
    public static boolean kalenderGueltigZweistellig(int tag, int monat, int jj) {
        if (jj < 0 || jj > 99) {
            return false;
        }
        return moeglich(tag, monat, 1900 + jj) && moeglich(tag, monat, 2000 + jj);
    }

    private static boolean moeglich(int tag, int monat, int jahr) {
        try {
            LocalDate.of(jahr, monat, tag);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** Zerlegung in Ziffernbloecke und die einzelnen Trenner dazwischen; {@code null}, wenn die Form nicht passt. */
    private record Teile(List<String> bloecke, List<Character> trenner) {
    }

    private static Teile zerlege(String s) {
        List<String> bloecke = new ArrayList<>();
        List<Character> trenner = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            int j = i;
            while (j < n && istZiffer(s.charAt(j))) {
                j++;
            }
            if (j == i) {
                return null;
            }
            bloecke.add(s.substring(i, j));
            if (j == n) {
                break;
            }
            trenner.add(s.charAt(j));
            i = j + 1;
            if (i >= n) {
                return null;
            }
        }
        return new Teile(bloecke, trenner);
    }

    private static boolean einOderZweistellig(String block) {
        return block.length() == 1 || block.length() == 2;
    }

    /** Ob die ganze Zeichenfolge genau ein Datum nach D-2 (a) bis (d) ist, kalendarisch gueltig nach D-3. */
    public static boolean istDatum(String s) {
        Teile t = zerlege(s);
        if (t == null || t.bloecke().size() != 3 || !t.trenner().get(0).equals(t.trenner().get(1))) {
            return false;
        }
        char trenner = t.trenner().get(0);
        String b1 = t.bloecke().get(0);
        String b2 = t.bloecke().get(1);
        String b3 = t.bloecke().get(2);
        if (trenner == '-' && b1.length() == 4 && b2.length() == 2 && b3.length() == 2) {
            return kalenderGueltig(Integer.parseInt(b3), Integer.parseInt(b2), Integer.parseInt(b1));
        }
        if (!einOderZweistellig(b1) || !einOderZweistellig(b2)) {
            return false;
        }
        int tag = Integer.parseInt(b1);
        int monat = Integer.parseInt(b2);
        if (trenner == '.' && b3.length() == 2) {
            return kalenderGueltigZweistellig(tag, monat, Integer.parseInt(b3));
        }
        if ((trenner == '.' || trenner == '/' || trenner == '-') && b3.length() == 4) {
            return kalenderGueltig(tag, monat, Integer.parseInt(b3));
        }
        return false;
    }

    /**
     * Liest ab {@code p} ein Datum Punkt-Form mit vierstelligem Jahr und liefert den Index hinter dem Jahr, sonst -1.
     * Der letzte Block ist genau vierstellig (keine weitere Ziffer dahinter), das Datum kalendarisch gueltig.
     */
    private static int punktDatumVierstellig(String text, int p) {
        int n = text.length();
        int[] wert = new int[3];
        int i = p;
        for (int block = 0; block < 3; block++) {
            int j = i;
            while (j < n && istZiffer(text.charAt(j))) {
                j++;
            }
            int laenge = j - i;
            boolean passt = block < 2 ? laenge == 1 || laenge == 2 : laenge == 4;
            if (!passt) {
                return -1;
            }
            wert[block] = Integer.parseInt(text.substring(i, j));
            i = j;
            if (block < 2) {
                if (i >= n || text.charAt(i) != '.') {
                    return -1;
                }
                i++;
            }
        }
        return kalenderGueltig(wert[0], wert[1], wert[2]) ? i : -1;
    }

    /**
     * Ob ab {@code start} ein Zeitraum nach D-2 (e) steht, dessen Ende {@code end} hinter dem {@code -} und hoechstens
     * am Ende des zweiten Datums liegt und dessen Kette hinter dem zweiten Datum nicht weitergeht.
     */
    public static boolean istZeitraumAb(String text, int start, int end) {
        int n = text.length();
        int ende1 = punktDatumVierstellig(text, start);
        if (ende1 < 0 || ende1 >= n || text.charAt(ende1) != '-') {
            return false;
        }
        int ende2 = punktDatumVierstellig(text, ende1 + 1);
        if (ende2 < 0 || end <= ende1 || end > ende2) {
            return false;
        }
        boolean kettePunktWeiter = ende2 + 1 < n && istGruppentrenner(text.charAt(ende2))
                && istZiffer(text.charAt(ende2 + 1));
        return !kettePunktWeiter;
    }

    /** Anzahl der ASCII-Ziffern im Ausschnitt. */
    public static int ziffern(String text, int start, int end) {
        int z = 0;
        for (int i = start; i < end; i++) {
            z += istZiffer(text.charAt(i)) ? 1 : 0;
        }
        return z;
    }

    /**
     * Ob der Ausschnitt {@code [start, end)} des Textes ein Datumssegment ist (siehe Klassenbeschreibung). Ein
     * ungueltiger Bereich ist kein Datumssegment.
     */
    public static boolean istDatumssegment(String text, int start, int end) {
        if (start < 0 || start >= end || end > text.length()) {
            return false;
        }
        if (ziffern(text, start, end) == NEUN) {
            return false;
        }
        return istDatum(text.substring(start, end)) || istZeitraumAb(text, start, end);
    }
}
