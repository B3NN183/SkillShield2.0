package de.skillshield.core.recognizer.kv;

import java.util.Objects;

/**
 * Datumsform und Kalender fuer die Ausgabe der KV-Nummern-Erkenner (Feature datum-und-kontextvorrang, Teil A,
 * Entscheidungen D-2, D-3 und D-5). Paketprivat, zustandslos, ohne regulaeren Ausdruck und ohne die JDK-Datumsklassen:
 * Der Kalender wird mit einer festen Tabelle der Monatslaengen und der Schaltjahrregel gerechnet. Die Klasse wird nur
 * von {@code KvNumberScanner.Lauf.ausgeben} gerufen; sie aendert keinen Zustand des Scanners.
 *
 * <p>Ein Segment {@code [start, end)} ist ein <b>Datumssegment</b>, wenn es nicht genau neun Ziffern hat (D-5, Regel
 * der Neun-Ziffern-Segmente) und
 * <ol>
 * <li>aus genau drei Ziffernbloecken mit zweimal demselben Trenner besteht und eine Form nach D-2 (a) bis (d) hat:
 * {@code .} mit Tag und Monat ein- oder zweistellig und Jahr vier- oder zweistellig; {@code /} oder {@code -} mit Tag
 * und Monat ein- oder zweistellig und vierstelligem Jahr; {@code -} mit vierstelligem Jahr zuerst, Monat und Tag je
 * zweistellig (ISO); und das Datum kalendarisch moeglich ist (Monat 1 bis 12, Tag 1 bis Monatslaenge, 29.02. nur im
 * Schaltjahr; vierstelliges Jahr {@value #JAHR_MIN} bis {@value #JAHR_MAX}; ein zweistelliges Jahr nur, wenn das
 * Datum in 19jj <b>und</b> in 20jj moeglich ist, also ist {@code 29.02.00} kein Datum), oder</li>
 * <li>ab {@code start} ein Zeitraum nach D-2 (e) steht: ein Datum der Punktform mit vierstelligem Jahr, genau ein
 * {@code -}, ein zweites Datum derselben Form, beide gueltig; {@code end} liegt hinter dem {@code -} und hoechstens am
 * Ende des zweiten Datums, und hinter dem zweiten Datum geht die Kette nicht weiter (kein Gruppentrenner mit
 * folgender Ziffer).</li>
 * </ol>
 * Ein Segment mit Buchstaben am Anfang (KVNR-Buchstabenform) beginnt nie mit einer Ziffer und ist nie ein Datum. Die
 * Pruefung ist strenger oder gleich streng wie die unabhaengige Testreferenz; im Zweifel bleibt ein Segment ein Fund.
 *
 * <p>Aufwand: Ein Segment hat hoechstens 12 Ziffern und 11 Trenner; die Pruefung liest nie mehr als 21 Zeichen des
 * Segments und hinter {@code end} hoechstens den Rest des zweiten Datums des Zeitraums und zwei Zeichen fuer das
 * Kettenende. Jeder Ziffernblock wird nach hoechstens fuenf Ziffern als zu lang abgelehnt, eine lange Ziffernfolge
 * wird also nie ganz gelesen. Konstante Arbeit je Segment; ohne Rekursion, ohne Speicher. Meldungen nennen weder Text
 * noch Ziffern.
 */
final class KvDatum {

    /** Kleinstes vierstelliges Jahr (D-3). */
    static final int JAHR_MIN = 1900;
    /** Groesstes vierstelliges Jahr (D-3). */
    static final int JAHR_MAX = 2099;
    /** Beginn des Jahrhunderts 20jj fuer das zweistellige Jahr (19jj beginnt bei {@link #JAHR_MIN}). */
    private static final int JAHR_ZWEITAUSEND = 2000;

    private static final int NEUN = 9;
    /** Laengstes Datum ({@code TT.MM.JJJJ}) und laengster Zeitraum ({@code TT.MM.JJJJ-TT.MM.JJJJ}) in Zeichen. */
    private static final int MAX_DATUM = 10;
    private static final int MAX_ZEITRAUM = 21;
    /** Hoechstzahl der Ziffern, die ein Block lesen darf, bevor er sicher zu lang ist (laengster Block: 4). */
    private static final int MAX_BLOCK_LESEN = 5;
    private static final int JAHR_STELLEN = 4;
    private static final int[] MONATSLAENGE = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final int FEBRUAR = 2;
    private static final int SCHALT_TAG = 29;
    private static final int DUENNES_LEERZEICHEN = 0x2009;
    private static final int SCHMALES_NBSP = 0x202F;
    private static final int NBSP = 0xA0;

    private KvDatum() {
    }

    /**
     * Ist das Segment {@code [start, end)} ein Datum nach D-2 (a) bis (d) und D-3, oder beginnt dort ein Zeitraum nach
     * D-2 (e), dessen zweites Datum die Kette beendet? Ein Segment mit genau neun Ziffern ist nie ein Datum (D-5);
     * ein ungueltiger Bereich auch nicht. Konstanter Aufwand; liest hinter {@code end} hoechstens sieben Zeichen.
     */
    static boolean istDatumssegment(String text, int start, int end) {
        Objects.requireNonNull(text, "text");
        if (start < 0 || start >= end || end > text.length() || end - start > MAX_ZEITRAUM) {
            return false;
        }
        if (ziffern(text, start, end) == NEUN) {
            return false;
        }
        return istDatum(text, start, end) || istZeitraumAb(text, start, end);
    }

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static int ziffern(String text, int start, int end) {
        int z = 0;
        for (int i = start; i < end; i++) {
            z += istZiffer(text.charAt(i)) ? 1 : 0;
        }
        return z;
    }

    private static boolean istGruppentrenner(char c) {
        return c == ' ' || c == NBSP || c == DUENNES_LEERZEICHEN || c == SCHMALES_NBSP || c == '-' || c == '.'
                || c == '/';
    }

    /** Index hinter dem Ziffernblock, der bei {@code von} beginnt, aber nicht ueber {@code bis} hinaus. */
    private static int blockEnde(String text, int von, int bis) {
        int j = von;
        while (j < bis && istZiffer(text.charAt(j))) {
            j++;
        }
        return j;
    }

    /** Wert des Ziffernblocks {@code [von, bis)}; nur fuer Bloecke mit hoechstens vier Ziffern gerufen. */
    private static int wert(String text, int von, int bis) {
        int w = 0;
        for (int i = von; i < bis; i++) {
            w = 10 * w + (text.charAt(i) - '0');
        }
        return w;
    }

    private static boolean istSchaltjahr(int jahr) {
        return jahr % 4 == 0 && (jahr % 100 != 0 || jahr % 400 == 0);
    }

    /** Kalender ohne Jahresgrenzen: Monat 1 bis 12, Tag 1 bis Monatslaenge, 29.02. nur im Schaltjahr. */
    private static boolean moeglich(int tag, int monat, int jahr) {
        if (monat < 1 || monat > MONATSLAENGE.length || tag < 1) {
            return false;
        }
        if (monat == FEBRUAR && tag == SCHALT_TAG) {
            return istSchaltjahr(jahr);
        }
        return tag <= MONATSLAENGE[monat - 1];
    }

    /** Vierstelliges Jahr: {@value #JAHR_MIN} bis {@value #JAHR_MAX}. */
    private static boolean gueltig(int tag, int monat, int jahr) {
        return jahr >= JAHR_MIN && jahr <= JAHR_MAX && moeglich(tag, monat, jahr);
    }

    /** Zweistelliges Jahr: nur, wenn das Datum in 19jj und in 20jj moeglich ist. */
    private static boolean gueltigZweistellig(int tag, int monat, int jj) {
        return moeglich(tag, monat, JAHR_MIN + jj) && moeglich(tag, monat, JAHR_ZWEITAUSEND + jj);
    }

    /** Ob {@code [start, end)} als Ganzes genau ein Datum nach D-2 (a) bis (d) ist, kalendarisch gueltig nach D-3. */
    private static boolean istDatum(String text, int start, int end) {
        if (end - start > MAX_DATUM) {
            return false;
        }
        int e1 = blockEnde(text, start, end);
        if (e1 == start || e1 >= end) {
            return false;
        }
        char trenner = text.charAt(e1);
        int b2 = e1 + 1;
        int e2 = blockEnde(text, b2, end);
        if (e2 == b2 || e2 >= end || text.charAt(e2) != trenner) {
            return false;
        }
        int b3 = e2 + 1;
        int e3 = blockEnde(text, b3, end);
        if (e3 == b3 || e3 != end) {
            return false;
        }
        int l1 = e1 - start;
        int l2 = e2 - b2;
        int l3 = e3 - b3;
        if (trenner == '-' && l1 == JAHR_STELLEN && l2 == 2 && l3 == 2) {
            return gueltig(wert(text, b3, e3), wert(text, b2, e2), wert(text, start, e1));
        }
        if (l1 > 2 || l2 > 2) {
            return false;
        }
        int tag = wert(text, start, e1);
        int monat = wert(text, b2, e2);
        if (trenner == '.' && l3 == 2) {
            return gueltigZweistellig(tag, monat, wert(text, b3, e3));
        }
        if ((trenner == '.' || trenner == '/' || trenner == '-') && l3 == JAHR_STELLEN) {
            return gueltig(tag, monat, wert(text, b3, e3));
        }
        return false;
    }

    /**
     * Liest ab {@code p} ein Datum der Punktform mit vierstelligem Jahr und liefert den Index hinter dem Jahr, sonst
     * -1. Der letzte Block ist genau vierstellig (keine weitere Ziffer dahinter), das Datum kalendarisch gueltig.
     */
    private static int punktDatumVierstellig(String text, int p) {
        int n = text.length();
        int tag = 0;
        int monat = 0;
        int jahr = 0;
        int i = p;
        for (int block = 0; block < 3; block++) {
            int j = i;
            while (j < n && j - i < MAX_BLOCK_LESEN && istZiffer(text.charAt(j))) {
                j++;
            }
            int laenge = j - i;
            boolean passt = block < 2 ? laenge == 1 || laenge == 2 : laenge == JAHR_STELLEN;
            if (!passt) {
                return -1;
            }
            int w = wert(text, i, j);
            if (block == 0) {
                tag = w;
            } else if (block == 1) {
                monat = w;
            } else {
                jahr = w;
            }
            i = j;
            if (block < 2) {
                if (i >= n || text.charAt(i) != '.') {
                    return -1;
                }
                i++;
            }
        }
        return gueltig(tag, monat, jahr) ? i : -1;
    }

    /**
     * Ob ab {@code start} ein Zeitraum nach D-2 (e) steht, dessen Ende {@code end} hinter dem {@code -} und hoechstens
     * am Ende des zweiten Datums liegt und dessen Kette hinter dem zweiten Datum nicht weitergeht.
     */
    private static boolean istZeitraumAb(String text, int start, int end) {
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
}
