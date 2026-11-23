package de.skillshield.core;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import java.util.ArrayList;
import java.util.List;

/**
 * Nachpruefung "fensterfern zulaessig" (Plan kvnr-bsnr-erkennung, Nachtrag 2 zu T-6, Punkt 6, und Nachtrag 5 zu T-6,
 * Punkt 1) fuer die Fuzz-Teile ab 1f. Die Pruefregel {@link KvnrBsnrHilfen#bestandsabweichungen} bleibt
 * eingefroren; sie meldet einen alten LANR-/IK-Fund, der in K-1, K-2 oder K-3 zu KVNR wird, als Verstoss, wenn das
 * KVNR-Kontextwort mehr als {@link KvnrBsnrHilfen#NAEHE} Zeichen entfernt steht (lange Aufzaehlungen: das Kontextwort
 * wirkt ueber Listenkontext weiter). Diese Klasse prueft jeden gemeldeten Verstoss nach und zaehlt ihn nur dann als
 * "fensterfern zulaessig", wenn alle uebrigen Bedingungen von K-1, K-2 oder K-3 gelten und ein KVNR-Kontextwort davor
 * im selben Absatz steht (keine Leerzeile dazwischen); jeder andere Verstoss bleibt ein echter Verstoss.
 *
 * <p>Bedingungen (alter Fund {@code f}, neuer Fund {@code g}, jeweils ohne die oertliche Bedingung der Pruefregel):
 * <ul>
 * <li>{@code f} hat den Typ LANR oder IK (ein Nicht-KV-Altfund ist nie fensterfern zulaessig);</li>
 * <li>K-3: {@code g} hat den Typ KVNR, denselben Bereich und eine hoehere Konfidenz;</li>
 * <li>K-2: wie K-3, aber gleiche Konfidenz und der Typname KVNR steht lexikographisch vor dem von {@code f};</li>
 * <li>K-1: {@code g} hat den Typ KVNR, dasselbe Ende, beginnt ein oder zwei Zeichen vor {@code f} (ein ASCII-Buchstabe,
 * bei zwei Zeichen danach ein Gruppentrenner) und hat mindestens die Konfidenz {@code min(f, 0,50)};</li>
 * <li>ein KVNR-Kontextwort (Referenz {@link KvnrBsnrHilfen#kvnrKontextReferenz} auf dem Absatz vor {@code g})
 * steht vor {@code g} im selben Absatz. Ein Absatz endet an einer Leerzeile (eine Zeile aus Leerraum und
 * unsichtbaren Zeichen) oder einem Absatztrenner (U+2029); {@code CR}, unsichtbare Zeichen und {@code LF} sind ein
 * einziger Zeilenumbruch.</li>
 * </ul>
 * Welche alten Funde Verstoesse sind, ergibt sich ohne Eingriff in die Pruefregel: Fuegt man {@code f} der neuen Liste
 * als gleichen Fund hinzu, sinkt die Zahl der Fund-Verstoesse genau dann, wenn {@code f} ein Verstoss war (die
 * verlorene Abdeckung zaehlt die Pruefregel je Text einmal und wird hier getrennt gerechnet; sie ist immer ein echter
 * Verstoss).
 *
 * <p>Die Kontextwort-Referenz arbeitet auf dem Buchstabenstrom und ueberschaetzt ("nahe"); die Zahl der
 * fensterfernen Faelle, deren Kontextwort nur ueber verstreute Buchstaben erkannt wurde (kein zusammenhaengendes
 * Wort im Absatz), steht deshalb getrennt im Ergebnis. Meldungen nennen nur Zahlen, nie Text oder Ziffern.
 */
public final class KvnrFensterfernPruefung {

    /**
     * Ergebnis fuer einen Text oder (summiert) eine Menge.
     *
     * @param k1                  zulaessige K-1-Faelle der Pruefregel (Kontextwort im Fenster)
     * @param k2                  zulaessige K-2-Faelle der Pruefregel
     * @param k3                  zulaessige K-3-Faelle der Pruefregel
     * @param k4                  zulaessige K-4-Faelle der Pruefregel
     * @param fensterfernK1       Verstoesse der Pruefregel, die fensterfern zulaessige K-1-Faelle sind
     * @param fensterfernK2       ebenso K-2
     * @param fensterfernK3       ebenso K-3
     * @param fensterfernVerstreut davon Faelle, bei denen das Kontextwort nur ueber verstreute Buchstaben erkannt wird
     * @param echteVerstoesse     alle uebrigen Verstoesse der Pruefregel (strikt 0 erwartet)
     */
    public record Ergebnis(int k1, int k2, int k3, int k4, int fensterfernK1, int fensterfernK2, int fensterfernK3,
            int fensterfernVerstreut, int echteVerstoesse) {

        /** Die Null der Summenbildung. */
        public static final Ergebnis NULL = new Ergebnis(0, 0, 0, 0, 0, 0, 0, 0, 0);

        /** Summe je Feld. */
        public Ergebnis plus(Ergebnis o) {
            return new Ergebnis(k1 + o.k1, k2 + o.k2, k3 + o.k3, k4 + o.k4, fensterfernK1 + o.fensterfernK1,
                    fensterfernK2 + o.fensterfernK2, fensterfernK3 + o.fensterfernK3,
                    fensterfernVerstreut + o.fensterfernVerstreut, echteVerstoesse + o.echteVerstoesse);
        }

        /** Zahl der fensterfern zulaessigen Faelle. */
        public int fensterfern() {
            return fensterfernK1 + fensterfernK2 + fensterfernK3;
        }

        /** Kurzform ohne Text und Ziffern, fuer Meldungen. */
        public String kurz() {
            return "K-1 " + k1 + ", K-2 " + k2 + ", K-3 " + k3 + ", K-4 " + k4 + ", fensterfern K-1 " + fensterfernK1
                    + ", K-2 " + fensterfernK2 + ", K-3 " + fensterfernK3 + " (nur verstreut " + fensterfernVerstreut
                    + "), echte Verstoesse " + echteVerstoesse;
        }
    }

    private enum Art {
        K1, K2, K3, KEINE
    }

    private static final String KVNR = "KVNR";
    private static final double MIN_KONFIDENZ_K1 = 0.50;
    private static final int ABSTAND_NUR_BUCHSTABE = 1;
    private static final int ABSTAND_BUCHSTABE_UND_TRENNER = 2;
    private static final char NBSP = (char) 0xA0;
    private static final char DUENN = (char) 0x2009;
    private static final char SCHMAL_NBSP = (char) 0x202F;
    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;
    private static final char NEXT_LINE = (char) 0x85;

    private KvnrFensterfernPruefung() {
    }

    /**
     * Prueft einen Text: die Pruefregel, dann die Nachpruefung jedes gemeldeten Verstosses.
     *
     * @param text der Text
     * @param alt  Fundliste von {@link KvnrBsnrHilfen#ALT_TRENNER}
     * @param neu  Fundliste des neuen Standes
     */
    public static Ergebnis pruefe(String text, List<Finding> alt, List<Finding> neu) {
        Abweichungen a = KvnrBsnrHilfen.bestandsabweichungen(text, alt, neu);
        if (a.verstoesse() == 0) {
            return new Ergebnis(a.k1(), a.k2(), a.k3(), a.k4(), 0, 0, 0, 0, 0);
        }
        int fundVerstoesse = fundVerstoesse(text, alt, neu);
        int k1 = 0;
        int k2 = 0;
        int k3 = 0;
        int verstreut = 0;
        for (Finding f : alt) {
            if (fundVerstoesse == 0) {
                break;
            }
            if (!KvnrBsnrHilfen.istKv(f) || !istVerstoss(text, alt, neu, f, fundVerstoesse)) {
                continue;
            }
            Finding g = fensterfernPartner(text, f, neu);
            if (g == null) {
                continue;
            }
            Art art = art(text, f, g);
            k1 += art == Art.K1 ? 1 : 0;
            k2 += art == Art.K2 ? 1 : 0;
            k3 += art == Art.K3 ? 1 : 0;
            String davor = text.substring(absatzAnfang(text, g.start()), g.start());
            verstreut += KvnrKollisionsNachrechnung.zusammenhaengendesKontextwort(davor) ? 0 : 1;
        }
        int echte = a.verstoesse() - k1 - k2 - k3;
        return new Ergebnis(a.k1(), a.k2(), a.k3(), a.k4(), k1, k2, k3, verstreut, echte);
    }

    /** Verstoesse der Pruefregel ohne die verlorene Abdeckung (die sie je Text einmal zaehlt). */
    private static int fundVerstoesse(String text, List<Finding> alt, List<Finding> neu) {
        int v = KvnrBsnrHilfen.bestandsabweichungen(text, alt, neu).verstoesse();
        return v - (abdeckungVerloren(text.length(), alt, neu) ? 1 : 0);
    }

    /** {@code f} ist ein Verstoss, wenn der gleiche Fund in der neuen Liste die Zahl der Fund-Verstoesse senkt. */
    private static boolean istVerstoss(String text, List<Finding> alt, List<Finding> neu, Finding f, int vorher) {
        List<Finding> mitKopie = new ArrayList<>(neu);
        mitKopie.add(f);
        return fundVerstoesse(text, alt, mitKopie) < vorher;
    }

    private static boolean abdeckungVerloren(int laenge, List<Finding> alt, List<Finding> neu) {
        boolean[] imNeuen = new boolean[laenge];
        for (Finding g : neu) {
            for (int i = g.start(); i < g.end(); i++) {
                imNeuen[i] = true;
            }
        }
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                if (!imNeuen[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Der neue KVNR-Fund, der {@code f} nach K-1, K-2 oder K-3 ersetzt, mit Kontextwort davor im Absatz. */
    private static Finding fensterfernPartner(String text, Finding f, List<Finding> neu) {
        for (Finding g : neu) {
            if (g.type().name().equals(KVNR) && art(text, f, g) != Art.KEINE
                    && KvnrBsnrHilfen.kvnrKontextReferenz(text.substring(absatzAnfang(text, g.start()), g.start()))) {
                return g;
            }
        }
        return null;
    }

    private static Art art(String text, Finding f, Finding g) {
        if (g.start() == f.start() && g.end() == f.end()) {
            if (g.confidence() > f.confidence()) {
                return Art.K3;
            }
            return g.confidence() == f.confidence() && KVNR.compareTo(f.type().name()) < 0 ? Art.K2 : Art.KEINE;
        }
        int vor = f.start() - g.start();
        boolean k1 = g.end() == f.end() && (vor == ABSTAND_NUR_BUCHSTABE || vor == ABSTAND_BUCHSTABE_UND_TRENNER)
                && asciiBuchstabe(text.charAt(g.start()))
                && (vor == ABSTAND_NUR_BUCHSTABE || gruppentrenner(text.charAt(g.start() + 1)))
                && g.confidence() >= Math.min(f.confidence(), MIN_KONFIDENZ_K1);
        return k1 ? Art.K1 : Art.KEINE;
    }

    static boolean asciiBuchstabe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean gruppentrenner(char c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP || c == '-' || c == '.' || c == '/';
    }

    // ------------------------------------------------------------------ Absatz

    /**
     * Anfang des Absatzes, in dem {@code pos} liegt: der Index hinter der letzten Leerzeile, die vor {@code pos}
     * endet, sonst 0. Eine Leerzeile ist eine Zeile aus Leerraum und unsichtbaren Zeichen; ein
     * Absatztrenner (U+2029) beendet den Absatz allein. {@code CR}, unsichtbare Zeichen und {@code LF} bilden einen
     * einzigen Zeilenumbruch, ebenso {@code CR LF}.
     */
    static int absatzAnfang(String text, int pos) {
        int absatz = 0;
        int zeile = 0;
        while (zeile < pos) {
            int ende = zeile;
            while (ende < text.length() && umbruchLaenge(text, ende) == 0) {
                ende++;
            }
            if (ende >= pos) {
                break;
            }
            int naechste = ende + umbruchLaenge(text, ende);
            if (leer(text, zeile, ende) || text.charAt(ende) == PARAGRAPH_SEPARATOR) {
                absatz = naechste;
            }
            zeile = naechste;
        }
        return absatz;
    }

    /** Laenge des Zeilenumbruchs an {@code i} (0, wenn dort keiner beginnt). */
    private static int umbruchLaenge(String text, int i) {
        char c = text.charAt(i);
        if (c == '\r') {
            int j = i + 1;
            while (j < text.length() && unsichtbar(text.charAt(j))) {
                j++;
            }
            return j < text.length() && text.charAt(j) == '\n' ? j + 1 - i : 1;
        }
        return c == '\n' || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR || c == NEXT_LINE ? 1 : 0;
    }

    private static boolean unsichtbar(char c) {
        return Character.getType(c) == Character.FORMAT;
    }

    private static boolean leer(String text, int von, int bis) {
        for (int i = von; i < bis; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c) && !unsichtbar(c)) {
                return false;
            }
        }
        return true;
    }
}
