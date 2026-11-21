package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gemeinsamer Zeichendurchlauf der KV-Nummern-Erkenner (LANR, spaeter IK). Der Text wird einmal von links nach
 * rechts gelesen und in Ereignisse zerlegt: Kontextwort, Fuellwort, Ziffernkette, Satzende und Zeilenumbruch. Eine
 * Ziffernkette wird nur dann ein Fund, wenn ein Kontextwort sie erschliesst. Die Pruefziffer bestimmt nur Stufe und
 * Quelle, nie, ob ein Fund entsteht.
 *
 * <p><b>Zeichenklassen.</b> Ziffer: nur ASCII {@code 0} bis {@code 9}. Buchstabe: Codepunkt mit
 * {@link Character#isLetter(int)} oder Kombinationszeichen. Wort: maximale Folge von Buchstaben. Kontextwort: ein
 * Wort, das nach Faltung von {@code A-Z} (kein {@code toLowerCase}, damit die tuerkischen i-Formen nicht zu
 * {@code i} werden) einem Eintrag von {@link KvArt#kontextwoerter()} gleicht, oder ein Wort aus
 * {@link KvArt#vorwoerter()}, dem nach hoechstens einem {@code -}, Leerzeichen oder U+00A0 das ganze Wort {@code nr}
 * oder {@code nummer} folgt. Zeilenumbruch: CR LF (zaehlt als einer), LF, CR, U+0085, U+2028, U+2029.
 * Gruppentrenner in einer Ziffernkette: genau eines von Leerzeichen, U+00A0, {@code -}, {@code .}, {@code /}.
 *
 * <p><b>Ziffernketten.</b> Eine Kette besteht aus Ziffernbloecken, die durch je genau einen Gruppentrenner
 * verbunden sind, dem wieder eine Ziffer folgt. Zwei Trenner hintereinander oder ein Trenner am Ende beenden die
 * Kette. Ab dem Kettenanfang werden Praefixe aus ganzen Bloecken betrachtet: Gibt es ein Praefix mit genau neun
 * Ziffern, ist es das Segment (Pruefziffer ueber die neun Ziffern ohne Trenner). Sonst ist das laengste Praefix mit
 * 6 bis 12 Ziffern das Segment mit Laengenabweichung. Sonst gibt es kein Segment, und der erste Block zaehlt als
 * Fuellwort. Danach geht der Durchlauf hinter dem Segment bzw. hinter dem ersten Block weiter; der Rest der Kette
 * wird als neue Kette gelesen. Die Praefixsuche bricht ab, sobald mehr als 12 Ziffern gezaehlt sind (auch mitten in
 * einem Block).
 *
 * <p><b>Zustand und Ereignisse (Stand dieses Tasks).</b> Zustand {@code aktiv} (ein Kontextwort erschliesst Zahlen)
 * und {@code direkt} (seit dem Kontextwort kam nichts ausser Trennzeichen und Nachsatzwoertern). Ein Kontextwort
 * setzt beide Merker. Ein Segment bei {@code aktiv} wird ausgegeben; Stufe "sicher" bei {@code direkt} und gueltiger
 * Pruefziffer. Jedes Fuellwort (auch ein Ziffernblock ohne Segment), jedes ausgegebene Segment, {@code !}, {@code ?}
 * und jeder Zeilenumbruch beenden den Kontext. Ein {@code .} beendet ihn nur, wenn {@code direkt} nicht mehr gilt
 * (direkt hinter dem Kontextwort ist er ein Abkuerzungspunkt wie in {@code LANR-Nr.}). Das Nachsatzwort {@code nr}
 * oder {@code nummer} zaehlt bei {@code direkt} nicht als Fuellwort. Die Erweiterungen (Fuellwoerter bis zu einer
 * Grenze, ein Zeilenumbruch, Aufzaehlung, nachgestellter Kontext) kommen in einem eigenen Task; sie aendern nur die
 * Ereignisbehandlung in {@code Lauf} (Methoden {@code fuellwort}, {@code zeilenumbruch}, {@code segment}), nicht
 * den Durchlauf.
 *
 * <p><b>Konfidenz, Stufe, Quelle.</b> Neun Ziffern mit gueltiger Pruefziffer: Konfidenz der Art, Quelle der Art
 * (bei {@code direkt}; sonst {@link #CONFIDENCE_KONTEXT_ENTFERNT}). Ungueltige Pruefziffer:
 * {@link #CONFIDENCE_PRUEFZIFFER_UNGUELTIG}. Sechs bis zwoelf Ziffern ausser neun:
 * {@link #CONFIDENCE_LAENGE_ABWEICHEND}. Der Fundbereich ist genau das Segment (Ziffern samt inneren Trennern), nie
 * das Kontextwort. Die Funde sind nach Position sortiert und ueberlappen nicht.
 *
 * <p><b>Linearitaet.</b> Ein Durchlauf, keine Rekursion, kein regulaerer Ausdruck, kein Zuruecksetzen der
 * Leseposition. Jedes Zeichen wird als Teil genau eines Ereignisses verarbeitet; die Kontextwortpruefung
 * vergleicht nur Woerter bis 23 Zeichen und schaut fuer das Nachsatzwort hoechstens 9 Zeichen voraus (und
 * verbraucht sie, wenn das Nachsatzwort passt). Die Praefixsuche einer Kette liest den ersten Block ganz und danach
 * hoechstens 13 Ziffern und 13 Trenner; da der Durchlauf mindestens hinter dem ersten Block weitergeht, wird jeder
 * Block hoechstens 13-mal beruehrt. Gesamtaufwand O(n), Zusatzspeicher O(1) plus Fundliste.
 *
 * <p>Der Durchlauf wirft nur {@link NullPointerException} mit den festen Meldungen {@code art} und {@code text}; er
 * schreibt nichts aus, und kein Fund und keine Meldung enthaelt Ziffern des Textes.
 */
final class KvNumberScanner {

    /** Konfidenz bei gueltiger Pruefziffer und weiter entferntem Kontextwort. */
    static final double CONFIDENCE_KONTEXT_ENTFERNT = 0.80;

    /** Konfidenz bei neun Ziffern mit ungueltiger Pruefziffer. */
    static final double CONFIDENCE_PRUEFZIFFER_UNGUELTIG = 0.50;

    /** Konfidenz bei einer Ziffernzahl von 6 bis 12 ausser neun. */
    static final double CONFIDENCE_LAENGE_ABWEICHEND = 0.40;

    private static final int NEUN = 9;
    private static final int MIN_LAENGE = 6;
    private static final int MAX_LAENGE = 12;
    private static final int MAX_KONTEXTWORT_LAENGE = 23;
    private static final int MAX_NACHSATZ_LAENGE = 6;
    private static final char NBSP = (char) 0xA0;
    private static final int NEL = 0x85;
    private static final int ZEILENTRENNER = 0x2028;
    private static final int ABSATZTRENNER = 0x2029;

    private KvNumberScanner() {
    }

    /**
     * Liefert die Funde der Art im Text, nach Position sortiert.
     *
     * @param art  Nummernart, nicht {@code null}
     * @param text zu durchsuchender Text, nicht {@code null}
     * @return die Funde, nie {@code null}
     * @throws NullPointerException mit der Meldung {@code art} oder {@code text} bei {@code null}
     */
    static List<Finding> detect(KvArt art, String text) {
        Objects.requireNonNull(art, "art");
        Objects.requireNonNull(text, "text");
        Lauf lauf = new Lauf(art, text);
        lauf.durchlaufen();
        return lauf.funde;
    }

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean istBuchstabe(int cp) {
        if (Character.isLetter(cp)) {
            return true;
        }
        int typ = Character.getType(cp);
        return typ == Character.NON_SPACING_MARK || typ == Character.ENCLOSING_MARK
                || typ == Character.COMBINING_SPACING_MARK;
    }

    private static boolean istZeilenumbruch(int cp) {
        return cp == '\n' || cp == '\r' || cp == NEL || cp == ZEILENTRENNER || cp == ABSATZTRENNER;
    }

    private static boolean istTrenner(char c) {
        return c == ' ' || c == NBSP || c == '-' || c == '.' || c == '/';
    }

    /** Zustand und Ereignisbehandlung eines Durchlaufs. */
    private static final class Lauf {

        private final KvArt art;
        private final String text;
        private final int n;
        private final List<Finding> funde = new ArrayList<>();
        private boolean aktiv;
        private boolean direkt;

        Lauf(KvArt art, String text) {
            this.art = art;
            this.text = text;
            this.n = text.length();
        }

        void durchlaufen() {
            int i = 0;
            while (i < n) {
                char c = text.charAt(i);
                if (istZiffer(c)) {
                    i = ziffernkette(i);
                } else if (c == '.') {
                    punkt();
                    i++;
                } else if (c == '!' || c == '?') {
                    satzende();
                    i++;
                } else {
                    int cp = text.codePointAt(i);
                    int laenge = Character.charCount(cp);
                    if (istZeilenumbruch(cp)) {
                        i += laenge;
                        if (cp == '\r' && i < n && text.charAt(i) == '\n') {
                            i++;
                        }
                        zeilenumbruch();
                    } else if (istBuchstabe(cp)) {
                        i = wort(i);
                    } else {
                        i += laenge; // Leerraum und Sonstiges sind neutral
                    }
                }
            }
        }

        // ------------------------------------------------------------------ Ereignisse

        private void kontextwort() {
            aktiv = true;
            direkt = true;
        }

        private void fuellwort() {
            aktiv = false;
            direkt = false;
        }

        private void punkt() {
            if (aktiv && !direkt) {
                aktiv = false;
            }
        }

        private void satzende() {
            aktiv = false;
            direkt = false;
        }

        private void zeilenumbruch() {
            aktiv = false;
            direkt = false;
        }

        /** Ein Segment [start, end) mit {@code anzahl} Ziffern wurde gelesen. */
        private void segment(int start, int end, int anzahl) {
            if (!aktiv) {
                return;
            }
            double konfidenz;
            String quelle;
            if (anzahl == NEUN) {
                if (art.isValid(ziffernwerte(start, end))) {
                    konfidenz = direkt ? art.confidenceDirekt() : CONFIDENCE_KONTEXT_ENTFERNT;
                    quelle = art.source();
                } else {
                    konfidenz = CONFIDENCE_PRUEFZIFFER_UNGUELTIG;
                    quelle = art.sourcePruefzifferUngueltig();
                }
            } else {
                konfidenz = CONFIDENCE_LAENGE_ABWEICHEND;
                quelle = art.sourceLaengeAbweichend();
            }
            funde.add(new Finding(art.type(), text.substring(start, end), start, end, konfidenz, quelle));
            direkt = false;
            aktiv = false;
        }

        // ------------------------------------------------------------------ Woerter

        /** Verarbeitet das Wort ab {@code start}; liefert die Position dahinter. */
        private int wort(int start) {
            int ende = wortEnde(start);
            int kontextEnde = kontextwortEnde(start, ende);
            if (kontextEnde >= 0) {
                kontextwort();
                return kontextEnde;
            }
            if (direkt && istNachsatzwort(start, ende)) {
                return ende; // bleibt direkt
            }
            fuellwort();
            return ende;
        }

        private int wortEnde(int start) {
            int p = start;
            while (p < n) {
                int cp = text.codePointAt(p);
                if (!istBuchstabe(cp)) {
                    break;
                }
                p += Character.charCount(cp);
            }
            return p;
        }

        /** Ende des Kontextworts, das das Wort [s, e) darstellt (samt Nachsatzwort), sonst -1. */
        private int kontextwortEnde(int s, int e) {
            if (e - s > MAX_KONTEXTWORT_LAENGE) {
                return -1;
            }
            String wort = gefaltet(s, e);
            if (art.kontextwoerter().contains(wort)) {
                return e;
            }
            if (art.vorwoerter().contains(wort)) {
                return nachsatzEnde(e);
            }
            return -1;
        }

        /** Ende des Nachsatzworts hinter einem Vorwort (hoechstens ein Trenner davor), sonst -1. */
        private int nachsatzEnde(int e) {
            int j = e;
            if (j < n) {
                char c = text.charAt(j);
                if (c == '-' || c == ' ' || c == NBSP) {
                    j++;
                }
            }
            int k = j;
            while (k < n && k - j <= MAX_NACHSATZ_LAENGE) {
                int cp = text.codePointAt(k);
                if (!istBuchstabe(cp)) {
                    break;
                }
                k += Character.charCount(cp);
            }
            return istNachsatzwort(j, k) ? k : -1;
        }

        private boolean istNachsatzwort(int s, int e) {
            int laenge = e - s;
            if (laenge != 2 && laenge != MAX_NACHSATZ_LAENGE) {
                return false;
            }
            String wort = gefaltet(s, e);
            return wort.equals("nr") || wort.equals("nummer");
        }

        /** Das Teilstueck [s, e) mit gefalteten Buchstaben {@code A-Z}; alles andere bleibt unveraendert. */
        private String gefaltet(int s, int e) {
            StringBuilder sb = new StringBuilder(e - s);
            for (int p = s; p < e; p++) {
                char c = text.charAt(p);
                sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
            }
            return sb.toString();
        }

        // ------------------------------------------------------------------ Ziffernketten

        /** Verarbeitet die Kette ab {@code start}; liefert die Position, ab der weitergelesen wird. */
        private int ziffernkette(int start) {
            int blockEnde = ziffernEnde(start);
            int anzahl = blockEnde - start;
            int neunEnde = -1;
            int besteEnde = -1;
            int besteAnzahl = 0;
            if (anzahl == NEUN) {
                neunEnde = blockEnde;
            } else if (anzahl >= MIN_LAENGE && anzahl <= MAX_LAENGE) {
                besteEnde = blockEnde;
                besteAnzahl = anzahl;
            }
            int p = blockEnde;
            while (neunEnde < 0 && anzahl <= MAX_LAENGE && p + 1 < n && istTrenner(text.charAt(p))
                    && istZiffer(text.charAt(p + 1))) {
                int q = p + 1;
                int c = 0;
                while (q + c < n && istZiffer(text.charAt(q + c)) && anzahl + c <= MAX_LAENGE) {
                    c++;
                }
                if (anzahl + c > MAX_LAENGE) {
                    break;
                }
                anzahl += c;
                p = q + c;
                if (anzahl == NEUN) {
                    neunEnde = p;
                } else if (anzahl >= MIN_LAENGE) {
                    besteEnde = p;
                    besteAnzahl = anzahl;
                }
            }
            if (neunEnde >= 0) {
                segment(start, neunEnde, NEUN);
                return neunEnde;
            }
            if (besteEnde >= 0) {
                segment(start, besteEnde, besteAnzahl);
                return besteEnde;
            }
            fuellwort();
            return blockEnde;
        }

        private int ziffernEnde(int start) {
            int p = start;
            while (p < n && istZiffer(text.charAt(p))) {
                p++;
            }
            return p;
        }

        /** Die neun Ziffernwerte des Segments [start, end), Trenner uebersprungen. */
        private int[] ziffernwerte(int start, int end) {
            int[] d = new int[NEUN];
            int k = 0;
            for (int p = start; p < end; p++) {
                char c = text.charAt(p);
                if (istZiffer(c)) {
                    d[k++] = c - '0';
                }
            }
            return d;
        }
    }
}
