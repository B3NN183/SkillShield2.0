package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.geheimnis.KontextRegelStandT6.Klasse;
import java.util.ArrayList;
import java.util.List;

/**
 * Zweiter Durchgang der Kontextregel (Anforderung D-6 K2 bis K4, D-8 W6, D-9; Plan Kapitel 1.3, Nachtrag
 * "T-5 verkleinert"): sucht Passwoerter hinter den schwaecheren Verbindern, die der erste Durchgang
 * ({@link KontextRegelStandT6}, K1 und Zeilenumbruch) nicht kennt:
 * <ul>
 * <li><b>K2</b> ein ganzes Wort {@code ist}, {@code lautet} oder {@code is} mit Leerraum davor und dahinter, auch
 * hinter einem Zwischenteil ({@code Passwort fuer den Benutzer svc_x lautet Wert});</li>
 * <li><b>K3</b> ein senkrechter Strich ({@code | Passwort | Wert |}; beginnt der Wert unmittelbar hinter dem Strich,
 * ohne Leerraum, und steht im Wort ein weiterer Strich, prueft der Wert-Test nur den Teil bis dorthin, siehe
 * {@code KontextRegelStandT6.Klasse#K3_STRICH}; mit Leerraum dahinter gilt das ganze Wort) oder, wenn
 * kein anderer Verbinder steht, ein Tabulator im Leerraum;</li>
 * <li><b>K4</b> die Elementform {@code <Kennwort>Wert</Kennwort>}: das Kontextwort steht unmittelbar zwischen
 * {@code <} und {@code >}, der Wert steht bis zum naechsten {@code <} ohne Leerraum (hoechstens 256 Zeichen).</li>
 * </ul>
 * Jeder Fund hat die Strenge SCHWACH (W6: Laenge mindestens 8, drei von vier Klassen), Konfidenz 0,80 und die Quelle
 * {@code passwort-kontext}. Hinter K2 gilt zusaetzlich: ein Wort, das auf Bindestrich und ein reines
 * Kleinbuchstabenwort endet ({@code AES256-verschluesselt}), ist kein Fund. {@code pw} und {@code pwd} gelten nie
 * (D-4: nur mit K1). Nach dem Kontextwort steht kein Zeilenumbruch und keine Auszeichnung; der Wert ist das erste
 * Wort oder der Inhalt von Anfuehrungszeichen wie im ersten Durchgang.
 *
 * <p><b>Warum zwei Durchgaenge.</b> Der erste Durchgang bleibt unveraendert; seine Kandidaten werden nie veraendert
 * oder entfernt, sodass kein Schutz gegenueber Stand T-4 verloren gehen kann. Ein Kandidat dieses Durchgangs wird nur
 * gemeldet, wenn er (a) keinen Kandidaten des ersten Durchgangs ueberlappt und (b) kein Kontextwort mit K1-Verbinder
 * ueberdeckt (der Wert nach diesem Kontextwort gehoert dem ersten Durchgang; der vordere Teil bleibt dann lesbar wie
 * in T-4). Steht hinter dem Kontextwort ein K1-Verbinder, auch im Zwischenteil nach einem K2
 * ({@code Passwort fuer svc_x ist jetzt: Wert}), wertet dieser Durchgang das Kontextwort gar nicht aus.
 *
 * <p><b>Linearitaet (AK-46).</b> Der Lauf geht einmal vorwaerts und springt nie zurueck. Je Kontextwort liest er
 * hoechstens den Zwischenteil (ein Zwischenwort, drei Woerter zu je hoechstens 65 Zeichen, Leerraum), ein K2-Wort
 * und den Wert. Nach jedem ausgewerteten Wert (Fund oder nicht) geht es hinter dem Wert weiter; ein zu langes Wort
 * wird einmal gelesen und ganz uebersprungen, die Suche nach einem schliessenden Anfuehrungszeichen und der Wert
 * eines Elements sind auf 257 Zeichen begrenzt. Die Pruefung gegen den ersten Durchgang nutzt einen Zeiger ueber
 * dessen sortierte Liste; die Pruefung auf ein Kontextwort mit K1 liest nur den Fund selbst (die Funde sind
 * disjunkt) und je Kontextwort darin die feste Leseweite. Kein regulaerer Ausdruck, keine Rekursion. Folge (wie G-19):
 * Ein Kontextwort innerhalb eines ausgewerteten Werts wird in diesem Durchgang nicht mehr ausgewertet.
 *
 * <p>Keine Meldung und keine Ausnahme nennt Text oder Wert.
 */
final class SchwacheVerbinderStandT6 {

    /** K2-Woerter (geschlossene, pflegbare Liste, D-6); Kleinbuchstaben. */
    private static final List<String> K2_WOERTER = List.of("ist", "lautet", "is");
    /** Laenge des laengsten K2-Worts ({@code lautet}). */
    private static final int MAX_K2_WORT = 6;

    private final String text;
    private final int n;
    private final Art art;
    private final List<Finding> erster;
    private final List<Finding> ziel;
    /** Hilfsliste fuer den einen Kandidaten je Auswertung. */
    private final List<Finding> einzel = new ArrayList<>(1);
    /** Zeiger in {@link #erster}: der erste Kandidat, der nicht vor dem aktuellen Fund endet. */
    private int zeiger;
    /**
     * Klasse des Verbinders, den {@link #probe} zuletzt mit Erfolg gefunden hat (gesetzt von {@link #wertAb}); erst
     * nach einem Treffer von {@code probe} oder {@code zwischenteil} gelesen, davor {@code null}.
     */
    private Klasse klasseDesVerbinders;

    private SchwacheVerbinderStandT6(String text, Art art, List<Finding> erster, List<Finding> ziel) {
        this.text = text;
        this.n = text.length();
        this.art = art;
        this.erster = erster;
        this.ziel = ziel;
    }

    /**
     * Durchsucht den Text und haengt die Kandidaten an {@code ziel} an (aufsteigend, ueberlappungsfrei, ohne
     * Ueberlappung mit {@code erster}).
     *
     * @param text   der Text
     * @param art    die Art (Typ und Quelle der Funde)
     * @param erster die Kandidaten des ersten Durchgangs, aufsteigend und ueberlappungsfrei; nicht veraendert
     * @param ziel   die Liste, an die die Kandidaten angehaengt werden
     */
    static void finde(String text, Art art, List<Finding> erster, List<Finding> ziel) {
        new SchwacheVerbinderStandT6(text, art, erster, ziel).lauf();
    }

    private void lauf() {
        int i = 0;
        while (i < n) {
            int cp = text.codePointAt(i);
            if (!GeheimnisZeichen.istWortkoerperZeichen(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int ende = i;
            while (ende < n) {
                int c = text.codePointAt(ende);
                if (!GeheimnisZeichen.istWortkoerperZeichen(c)) {
                    break;
                }
                ende += Character.charCount(c);
            }
            int weiter = ende;
            if (KontextRegelStandT6.istKontextwort(text, i, ende, false, art)) {
                weiter = Math.max(ende, auswerten(i, ende));
            }
            i = weiter;
        }
    }

    /** Wertet hinter dem Kontextwort {@code text[von, kw)} aus; liefert die Position, ab der weitergesucht wird. */
    private int auswerten(int von, int kw) {
        if (von > 0 && text.charAt(von - 1) == '<' && kw < n && text.charAt(kw) == '>') {
            return element(kw + 1);
        }
        if (KontextRegelStandT6.verbinderK1(text, kw) >= 0) {
            return kw;
        }
        int start = probe(kw);
        if (start < 0) {
            start = zwischenteil(kw);
        }
        if (start < 0) {
            return kw;
        }
        einzel.clear();
        int weiter = KontextRegelStandT6.wert(text, start, art, einzel, klasseDesVerbinders);
        if (!einzel.isEmpty()) {
            melde(einzel.get(0));
        }
        return weiter;
    }

    // ------------------------------------------------------------------ Verbinder

    /**
     * K2 oder K3 an der Stelle {@code q} (hinter dem Kontextwort oder einem Wort des Zwischenteils). Liefert die
     * Position des Wertanfangs in derselben Zeile und setzt {@link #klasseDesVerbinders}, sonst -1.
     */
    private int probe(int q) {
        int r = KontextRegelStandT6.ueberspringeLeerraum(text, q);
        if (r >= n) {
            return -1;
        }
        if (text.charAt(r) == '|') {
            int start = KontextRegelStandT6.ueberspringeLeerraum(text, r + 1);
            return wertAb(start, start == r + 1 ? Klasse.K3_STRICH : Klasse.K3_K4);
        }
        if (r == q || GeheimnisZeichen.istZeilenumbruch(text.charAt(r))) {
            return -1;
        }
        int w = KontextRegelStandT6.wortEnde(text, r, MAX_K2_WORT + 1);
        if (w < n && GeheimnisZeichen.istLeerraum(text.charAt(w)) && istK2Wort(r, w)) {
            return wertAb(KontextRegelStandT6.ueberspringeLeerraum(text, w), Klasse.K2);
        }
        return hatTabulator(q, r) ? wertAb(r, Klasse.K3_K4) : -1;
    }

    /** Merkt die Klasse und liefert {@code start}, wenn dort in derselben Zeile ein Wert steht, sonst -1. */
    private int wertAb(int start, Klasse k) {
        if (start >= n || GeheimnisZeichen.istZeilenumbruch(text.charAt(start))) {
            return -1;
        }
        klasseDesVerbinders = k;
        return start;
    }

    private boolean istK2Wort(int von, int bis) {
        for (String wort : K2_WOERTER) {
            if (GeheimnisZeichen.gleichKlein(text, von, bis, wort)) {
                return true;
            }
        }
        return false;
    }

    private boolean hatTabulator(int von, int bis) {
        for (int i = von; i < bis; i++) {
            if (text.charAt(i) == '\t') {
                return true;
            }
        }
        return false;
    }

    /**
     * Zwischenteil nach D-6: ein Zwischenwort, dann ein bis drei Woerter; nach jedem Wort wird auf K2 oder K3
     * geprueft, der erste Treffer beendet die Suche. Liefert den Wertanfang oder -1.
     */
    private int zwischenteil(int kw) {
        int z = KontextRegelStandT6.ueberspringeLeerraum(text, kw);
        if (z == kw) {
            return -1;
        }
        int w = KontextRegelStandT6.wortEnde(text, z, KontextRegelStandT6.MAX_ZWISCHENWORT + 1);
        if (w == z || !KontextRegelStandT6.istZwischenwort(text, z, w)) {
            return -1;
        }
        int pos = w;
        for (int k = 0; k < KontextRegelStandT6.MAX_ZWISCHENTEIL_WOERTER; k++) {
            int q = KontextRegelStandT6.ueberspringeLeerraum(text, pos);
            if (q == pos) {
                return -1;
            }
            w = KontextRegelStandT6.wortEnde(text, q, KontextRegelStandT6.MAX_ZWISCHENWORT + 1);
            if (w == q || w - q > KontextRegelStandT6.MAX_ZWISCHENWORT) {
                return -1;
            }
            int start = probe(w);
            if (start >= 0) {
                return start;
            }
            pos = w;
        }
        return -1;
    }

    // ------------------------------------------------------------------ K4

    /**
     * K4: der Wert steht ab {@code vonWert} bis zum naechsten {@code <} derselben Zeile, ohne Leerraum, hoechstens
     * 256 Zeichen. Liefert die Position, ab der weitergesucht wird.
     */
    private int element(int vonWert) {
        int grenze = Math.min(n, vonWert + Werttest.MAX_LAENGE_ANFUEHRUNG + 1);
        int j = vonWert;
        while (j < grenze) {
            char c = text.charAt(j);
            if (c == '<' || GeheimnisZeichen.istLeerraum(c) || GeheimnisZeichen.istZeilenumbruch(c)) {
                break;
            }
            j++;
        }
        if (j > vonWert && j < grenze && text.charAt(j) == '<') {
            einzel.clear();
            KontextRegelStandT6.pruefeUndMelde(text, vonWert, j, true, true, art, einzel);
            if (!einzel.isEmpty()) {
                melde(einzel.get(0));
            }
        }
        return j;
    }

    // ------------------------------------------------------------------ Pruefung gegen den ersten Durchgang

    /** Meldet den Kandidaten, wenn er den ersten Durchgang nicht beruehrt (siehe Klassenkommentar). */
    private void melde(Finding f) {
        while (zeiger < erster.size() && erster.get(zeiger).end() <= f.start()) {
            zeiger++;
        }
        if (zeiger < erster.size() && erster.get(zeiger).start() < f.end()) {
            return;
        }
        if (enthaeltKontextwortMitK1(f.start(), f.end())) {
            return;
        }
        ziel.add(f);
    }

    /** Ob in {@code text[von, bis)} ein Kontextwort steht, hinter dem der Verbinder K1 folgt. */
    private boolean enthaeltKontextwortMitK1(int von, int bis) {
        int i = von;
        while (i < bis) {
            int cp = text.codePointAt(i);
            if (!GeheimnisZeichen.istWortkoerperZeichen(cp)) {
                i += Character.charCount(cp);
                continue;
            }
            int ende = i;
            while (ende < bis) {
                int c = text.codePointAt(ende);
                if (!GeheimnisZeichen.istWortkoerperZeichen(c)) {
                    break;
                }
                ende += Character.charCount(c);
            }
            if (KontextRegelStandT6.istKontextwort(text, i, ende, true, art)
                    && KontextRegelStandT6.verbinderK1(text, ende) >= 0) {
                return true;
            }
            i = ende;
        }
        return false;
    }
}
