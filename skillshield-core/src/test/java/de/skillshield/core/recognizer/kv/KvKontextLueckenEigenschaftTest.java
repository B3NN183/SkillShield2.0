package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests zu Feature kv-kontext-luecken (Task T-5, AK-28 (a) bis (d), AK-22, AK-26): Sperrschrift (D-1 bis
 * D-4) und Komma/Semikolon vor dem nachgestellten Kontextwort (D-5). Das Orakel ist die Konstruktion: Texte werden aus
 * Bausteinen erzeugt, deren erwartete Funde (Position, Wert, Konfidenz, Stufe, Quelle) bei der Erzeugung feststehen;
 * es gibt keinen Aufruf des Scanners oder des Vorgaengers im Orakel. Fuer die Rauschtexte (c) entscheidet die
 * unabhaengige Referenz {@link KvTestHilfen#sperrKontextwoerterReferenz}, ob eine gezogene Einzelbuchstabenfolge ein
 * Kontextwort ergaebe (dann wird sie neu gezogen, ein Zaehler belegt, dass das vorkommt). Jeder Text geht ausserdem
 * durch {@link KvTestHilfen#scan} (Waechter der Testdatenregel, Invarianten, Differenzpruefung gegen den
 * eingefrorenen Vorgaenger) und wird auf Determinismus geprueft. Meldungen nennen nur Seed, Iteration und Zaehler.
 *
 * <p><b>(a)</b> Bloecke aus gesperrtem Kontextwort (zufaellige Trenner nach D-1, zufaellige Gross-/Kleinschreibung,
 * optional Punkt am Ende, optional Praefix), Direktzeichen (auch Nachsatzwort), Fuellwoertern, Umbruch oder
 * Aufzaehlung und Nummer. Erwartung: ab drei Buchstaben 0,80 (ungueltig 0,50, Laenge 0,40); {@code I K} nur die
 * unmittelbar folgende Nummer mit 0,50. <b>(b)</b> Nummernfolgen, danach genau ein Komma oder Semikolon mit
 * Leerraum, optional Klammer und Kontextwort (normal oder gesperrt): 0,60/0,50/0,40; als Kontrolle ohne Komma:
 * 0,80. <b>(c)</b> Rauschtexte mit Einzelbuchstabenfolgen, Initialen, Nummern: kein Fund. <b>(d)</b> Gegenproben aus
 * AK-6, AK-7, AK-18 zufaellig zusammengesetzt: kein Fund. <b>(e)</b> Unsichtbare Zeichen, an zufaelligen Stellen
 * ausserhalb der Nummern eingestreut, aendern die Funde nicht (Plan Kap. 3.2.1).
 *
 * <p><b>Ausgespart beim Einstreuen (bekannte, dokumentierte Ausnahmen):</b> Positionen zwischen zwei Buchstaben
 * (ein Formatzeichen mitten im Wort bildet dort einen Lauf mit mehreren Buchstaben: bei einer Sperrfolge die Teilgruppe
 * der Grenze 14a, bei einem normalen Wort ein R-1-Teilstueck, das als Kontextwort gelten oder eines zerstoeren
 * kann), Positionen im Inneren einer Nummer (Grenze 7: Ziffernkette) und die Position zwischen CR und LF (Plan
 * Kap. 11 Nr. 15: dort zaehlt der Scanner zwei Zeilenumbrueche; Vorfeature-Verhalten, offen). Positionen unmittelbar
 * vor und hinter einer Nummer sind zulaessig; Positionen innerhalb eines Surrogatpaars werden nie gewaehlt.
 *
 * <p>Testdatenregel: Nummern kommen aus {@code bilde...} (Praefix {@code 00}), den Laengenkonstanten oder sind
 * Rauschketten (hoechstens acht Ziffern oder ein Block mit {@code 00}-Praefix); der Waechter sichert jeden Text.
 */
public class KvKontextLueckenEigenschaftTest {

    private static final long SEED_A = 20261201L;
    private static final long SEED_B = 20261202L;
    private static final long SEED_C = 20261203L;
    private static final long SEED_D = 20261204L;
    private static final long SEED_E_A = 20261205L;
    private static final long SEED_E_B = 20261206L;
    private static final long SEED_E_D = 20261207L;
    private static final int TEXTE = 1_000;
    private static final double KOMMA = 0.60;
    private static final double ZWEI = 0.50;

    /** Alle acht Sperr-Trenner nach D-1 samt Namen fuer die Zaehler. */
    private static final String[] SPERR_TRENNER = {" ", ".", "-", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), String.valueOf(GESCH_BINDESTRICH), String.valueOf(HALBGEVIERT)};
    private static final String[] TRENNER_NAME = {"leerzeichen", "punkt", "bindestrich", "nbsp", "thin", "nnbsp",
        "geschuetzterBindestrich", "halbgeviert"};

    private static final String[] SPERR_LANR = {"LANR", "LANRNR", "ARZTNUMMER", "ARZTNR"};
    private static final String[] SPERR_IK = {"IK", "IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"};
    private static final String[] SPERR_IK_DREI = {"IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"};

    private static final String[] LAENGEN = {LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, LAENGE_12};
    private static final String[] GRUPPENTRENNER = {" ", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), "-", ".", "/"};
    private static final String[] UMBRUECHE = {"\n", "\r\n", "\r", String.valueOf(NEL), String.valueOf(LS),
        String.valueOf(PS)};

    /** Praefixe vor der gesperrten Schreibweise (Wortgrenze, Bindestrich-Praefix, Satzumfeld, AK-11). */
    private static final String[] PRAEFIX = {"", "", "", "Praxis ", "Praxis-", "Rechnung an ", "der "};

    /** Direktzeichen hinter dem Kontextwort: nur Leerraum, Zeichen und Nachsatzwoerter (bleibt direkt). */
    private static final String[] DIR = {" ", ": ", "=", ":", "\t", " : ", ":  ", "", String.valueOf(NBSP), "  ",
        " Nr ", "-Nr. ", "-Nummer: ", " - ", ": Nr. ", "= "};
    private static final String[] DIR_NICHT_LEER = {" ", ": ", "=", "\t", " : ", ";"};
    private static final String[] SUFFIX = {"", "", "", ")", " gesendet", ";"};
    private static final String[] AUFZAEHLUNG = {", ", "; ", " / ", " und ", ",\n"};

    /** Leerraum ohne Zeilenumbruch (auch Tab) vor und nach dem Komma. */
    private static final String[] WS = {"", " ", "  ", "\t", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), " \t"};
    /** Luecken zwischen zwei Nummern der Folge (R-2): Komma, Semikolon, Schraegstrich, einmal {@code und}. */
    private static final String[] INNEN = {", ", "; ", " / ", " und ", ",", ";", " , ", ", und ", " ; ", " / , "};
    private static final String[] VOR_NUMMER = {"", "", "Nummer ", "Kennung ", "Wert: "};

    private static final String[] RAUSCHWOERTER = {"des", "der", "Praxis", "Muster", "Rechnung", "Dr", "Frau",
        "Ordination", "Nrx", "Nummern", "Praxisnummer", "Arztes", u('Z', 0xFC, 'r', 'i', 'c', 'h'),
        u(0x41F, 0x440, 0x438, 0x432, 0x435, 0x442), u(0x3B1, 0x3BB, 0x3C6, 0x3B1), u(0x65E5, 0x672C),
        u(0x1D400, 0x1D401), "Pra" + SHY + "xis"};
    private static final String[] FUELL = {"des", "der", "Behandlers", "Praxis", "bitte", "siehe", "unten", "Frau",
        "Herr", "Beispiel", u('G', 'r', 0xFC, 'n'), u(0x41F, 0x440, 0x438, 0x432, 0x435, 0x442),
        u(0x3B1, 0x3BB, 0x3C6, 0x3B1), u(0x65E5, 0x672C, 0x8A9E)};

    private static final char[] UNSICHTBAR = {SHY, ZWSP, ZWNJ, ZWJ, WORD_JOINER, BOM, (char) 0x200E, (char) 0xFE0F,
        (char) 0xFE00, (char) 0xFE0E};

    /** Trenner, die eine Sperrfolge sicher brechen (Tab, Komma, Unterstrich, Schraegstrich, doppelt, Umbruch ...). */
    private static final String[] BRUCH = {"\t", ",", "_", "/", "  ", "\n", " , ", ";", ".  "};
    /** Buchstaben fuer zufaellige Einzelbuchstabenfolgen (viele aus den Kontextwoertern: Beinahe-Treffer). */
    private static final String POOL = "IKLANRZTUMESXY";
    /** Einzelbuchstaben aus anderen Schriften: ergeben nie ein Kontextwort (Faltung nur A-Z). */
    private static final String[] FREMDBUCHSTABEN = {String.valueOf((char) 0x212A), String.valueOf((char) 0xE4),
        String.valueOf((char) 0x41A), String.valueOf((char) 0x130), String.valueOf((char) 0x131)};
    private static final String[] INITIALEN = {"I. K.", "A. B.", "I. K. Mustermann", "Prof. I. K. Mustermann",
        "L. A. N. R.", "I.K.", "I.K", "L.A.N.R.", "L.A.N.R", "I-K", "I K", "L A N R", "A. I. K."};

    private record Erw(int start, String wert, double konfidenz) {
    }

    private record Zahl(String text, String ziffern) {
    }

    /** Zaehler, damit der Test belegt, dass der Generator jede Regel, Trennerart und Stufe wirklich erzeugt. */
    private static final class Statistik {
        final Map<String, Integer> z = new TreeMap<>();

        void zaehle(String schluessel) {
            z.merge(schluessel, 1, Integer::sum);
        }

        int get(String schluessel) {
            return z.getOrDefault(schluessel, 0);
        }

        void mindestens(String schluessel, int min) {
            assertTrue(get(schluessel) >= min, "Zaehler " + schluessel + " zu klein: " + get(schluessel));
        }
    }

    // ------------------------------------------------------------------ Generator

    private static final class Bauer {
        final Random r;
        final Art art;
        final Statistik st;
        final String pre;
        final StringBuilder sb = new StringBuilder();
        final List<Erw> erwartet = new ArrayList<>();
        /** Alle Nummern des Textes (Start, Ende), auch die ohne erwarteten Fund: dort wird nichts eingestreut. */
        final List<int[]> nummern = new ArrayList<>();
        boolean endeKw;

        Bauer(Random r, Art art, Statistik st, String pre) {
            this.r = r;
            this.art = art;
            this.st = st;
            this.pre = pre;
        }

        <T> T wahl(T[] werte) {
            return werte[r.nextInt(werte.length)];
        }

        String ziffern(int anzahl) {
            StringBuilder z = new StringBuilder();
            for (int i = 0; i < anzahl; i++) {
                z.append((char) ('0' + r.nextInt(10)));
            }
            return z.toString();
        }

        String ungueltig() {
            while (true) {
                char[] z = art.bilde(r).toCharArray();
                int stelle = 2 + r.nextInt(7);
                char neu = (char) ('0' + r.nextInt(10));
                if (neu == z[stelle]) {
                    continue;
                }
                z[stelle] = neu;
                String kandidat = new String(z);
                if (!art.gueltig(kandidat)) {
                    return kandidat;
                }
            }
        }

        String trennerform(String neun) {
            StringBuilder s = new StringBuilder();
            int pos = 0;
            while (pos < neun.length()) {
                int groesse = Math.min(1 + r.nextInt(5), neun.length() - pos);
                s.append(neun, pos, pos + groesse);
                pos += groesse;
                if (pos < neun.length()) {
                    s.append(wahl(GRUPPENTRENNER));
                }
            }
            return s.toString();
        }

        Zahl zahl() {
            int w = r.nextInt(10);
            String z;
            if (w < 5) {
                z = art.bilde(r);
                st.zaehle(pre + ".zahl.gueltig");
            } else if (w < 8) {
                z = ungueltig();
                st.zaehle(pre + ".zahl.ungueltig");
            } else {
                z = LAENGEN[r.nextInt(LAENGEN.length)];
                st.zaehle(pre + ".zahl.laenge");
            }
            String text = z;
            if (z.length() == 9 && r.nextInt(100) < 35) {
                text = trennerform(z);
                st.zaehle(pre + ".zahl.trennerform");
            }
            return new Zahl(text, z);
        }

        /** Konfidenz nach Plan Kap. 3.3: Laenge 0,40, ungueltige Pruefziffer 0,50, sonst der Deckel der Regel. */
        double konf(Zahl z, double deckelGueltig) {
            if (z.ziffern().length() != 9) {
                return LAENGE;
            }
            return art.gueltig(z.ziffern()) ? deckelGueltig : PZ_UNGUELTIG;
        }

        int platziere(Zahl z) {
            int start = sb.length();
            sb.append(z.text());
            nummern.add(new int[] {start, start + z.text().length()});
            return start;
        }

        void erwarte(String regel, int start, Zahl z, double konfidenz) {
            erwartet.add(new Erw(start, z.text(), konfidenz));
            st.zaehle(pre + ".fund." + regel);
            st.zaehle(pre + ".fund." + regel + "." + konfidenz + "." + art.quelleNachNummer(z.text()));
        }

        void fund(String regel, Zahl z, double konfidenz) {
            erwarte(regel, platziere(z), z, konfidenz);
        }

        void ohneFund(Zahl z) {
            platziere(z);
        }

        String zufallsBuchstabe(char c) {
            return String.valueOf(r.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }

        /** Das Wort gesperrt: je Buchstabe zufaellige Schreibweise, zwischen den Buchstaben genau ein Sperr-Trenner. */
        String folge(String wort, boolean punktEnde) {
            boolean gemischt = r.nextBoolean();
            int fest = r.nextInt(SPERR_TRENNER.length);
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < wort.length(); i++) {
                if (i > 0) {
                    int t = gemischt ? r.nextInt(SPERR_TRENNER.length) : fest;
                    f.append(SPERR_TRENNER[t]);
                    st.zaehle(pre + ".trenner." + TRENNER_NAME[t]);
                }
                f.append(zufallsBuchstabe(wort.charAt(i)));
            }
            if (punktEnde) {
                f.append('.');
            }
            return f.toString();
        }

        String[] sperrWorte() {
            return art.lanr ? SPERR_LANR : SPERR_IK;
        }

        String[] sperrWorteDrei() {
            return art.lanr ? SPERR_LANR : SPERR_IK_DREI;
        }

        void fuellwoerter(int anzahl) {
            for (int i = 0; i < anzahl; i++) {
                sb.append(wahl(FUELL)).append(' ');
            }
        }

        void rauschen() {
            int tokens = r.nextInt(4);
            for (int i = 0; i < tokens; i++) {
                sb.append(wahl(RAUSCHWOERTER)).append(r.nextInt(5) == 0 ? ", " : " ");
            }
            // Satzende hinter einem Wort ueber vier Buchstaben: leert offene Segmente und beendet den Kontext
            sb.append("fertig. ");
        }

        // ---------------------------------------------------------------- (a) gesperrtes Kontextwort + Nummer

        void textA() {
            rauschen();
            int anzahl = 1 + r.nextInt(4);
            for (int i = 0; i < anzahl; i++) {
                blockA();
                sb.append(". ");
                rauschen();
            }
        }

        void blockA() {
            sb.append(wahl(PRAEFIX));
            String wort = wahl(sperrWorte());
            boolean zwei = wort.length() == 2;
            st.zaehle("a.wort." + art.name + "." + wort);
            boolean punkt = r.nextInt(4) == 0;
            if (punkt) {
                st.zaehle("a.punktEnde");
            }
            sb.append(folge(wort, punkt));
            double deckel = zwei ? ZWEI : WAHRSCHEINLICH;
            String regel = zwei ? "sperrZwei" : "sperrDrei";
            int w = r.nextInt(100);
            if (zwei) {
                sb.append(wahl(DIR));
                if (w < 80) {
                    st.zaehle("a.block.direkt");
                    Zahl z = zahl();
                    fund(regel, z, konf(z, deckel));
                } else {
                    // Segment beendet den nur-direkt-Kontext: die zweite Nummer bleibt ohne Fund (AK-5)
                    st.zaehle("a.block.zweiteNummerOhneFund");
                    Zahl z = zahl();
                    fund(regel, z, konf(z, deckel));
                    sb.append(wahl(AUFZAEHLUNG));
                    ohneFund(zahl());
                }
            } else if (w < 50) {
                st.zaehle("a.block.direkt");
                sb.append(wahl(DIR));
                Zahl z = zahl();
                fund(regel, z, konf(z, deckel));
            } else if (w < 65) {
                st.zaehle("a.block.fuellwort");
                sb.append(wahl(DIR_NICHT_LEER));
                fuellwoerter(1 + r.nextInt(3));
                Zahl z = zahl();
                fund(regel, z, konf(z, deckel));
            } else if (w < 80) {
                st.zaehle("a.block.umbruch");
                sb.append(wahl(new String[] {"", ":", " :", ": ", " "}));
                sb.append(wahl(new String[] {"", " ", "  ", "\t"})).append(wahl(UMBRUECHE));
                sb.append(wahl(new String[] {"", " ", "  ", "\t", "    "}));
                Zahl z = zahl();
                fund(regel, z, konf(z, deckel));
            } else {
                st.zaehle("a.block.aufzaehlung");
                sb.append(wahl(DIR));
                Zahl z = zahl();
                fund(regel, z, konf(z, deckel));
                sb.append(wahl(AUFZAEHLUNG));
                Zahl z2 = zahl();
                fund("aufzaehlung", z2, konf(z2, WAHRSCHEINLICH));
            }
            sb.append(wahl(SUFFIX));
        }

        // ---------------------------------------------------------------- (b) Nummer, Komma, Kontextwort

        void textB() {
            rauschen();
            int anzahl = 1 + r.nextInt(4);
            for (int i = 0; i < anzahl; i++) {
                blockB();
                sb.append("! ");
                rauschen();
            }
        }

        /** Normales Kontextwort der Art ohne Wort davor ({@code Praxis-}, {@code Lebenslange}) oder gesperrt. */
        String nachgestelltesKontextwort(String schluessel) {
            if (r.nextInt(100) < 30) {
                st.zaehle(schluessel + ".kw.gesperrt");
                return folge(wahl(sperrWorteDrei()), false);
            }
            String kw = wahl(art.kws);
            while (kw.startsWith("Praxis") || kw.startsWith("Lebenslange")) {
                kw = wahl(art.kws);
            }
            st.zaehle(schluessel + ".kw.normal");
            return kw;
        }

        void blockB() {
            sb.append(wahl(VOR_NUMMER));
            int anzahl = 1 + r.nextInt(3);
            st.zaehle("b.folgenLaenge." + anzahl);
            List<int[]> starts = new ArrayList<>();
            List<Zahl> zahlen = new ArrayList<>();
            for (int i = 0; i < anzahl; i++) {
                if (i > 0) {
                    String innen = wahl(INNEN);
                    st.zaehle("b.innen." + innen.trim().replace(' ', '_'));
                    sb.append(innen);
                }
                Zahl z = zahl();
                starts.add(new int[] {platziere(z)});
                zahlen.add(z);
            }
            boolean komma = r.nextInt(100) < 70;
            if (komma) {
                char zeichen = r.nextBoolean() ? ',' : ';';
                st.zaehle("b.zeichen." + zeichen);
                sb.append(wahl(WS)).append(zeichen).append(wahl(WS));
            } else {
                sb.append(wahl(WS));
            }
            int klammer = r.nextInt(3);
            String zu = klammer == 0 ? ")" : klammer == 1 ? "]" : "";
            if (klammer < 2) {
                sb.append(klammer == 0 ? '(' : '[');
                st.zaehle("b.klammer." + (klammer == 0 ? "rund" : "eckig"));
                if (r.nextBoolean()) {
                    sb.append(' ');
                }
            }
            String schluessel = komma ? "b.komma" : "b.kontrolle";
            sb.append(nachgestelltesKontextwort(schluessel));
            if (r.nextInt(4) != 0) {
                sb.append(zu);
            }
            double deckel = komma ? KOMMA : WAHRSCHEINLICH;
            for (int i = 0; i < anzahl; i++) {
                erwarte(komma ? "komma" : "nachgestellt", starts.get(i)[0], zahlen.get(i), konf(zahlen.get(i), deckel));
            }
        }

        // ---------------------------------------------------------------- (c) Rauschen ohne Kontextwort

        /** Ergibt dieses Stueck, fuer sich zwischen Umbruechen gelesen, laut Referenz ein Kontextwort (eine Art)? */
        boolean ergibtKontextwort(String stueck) {
            String text = "\n" + stueck + "\n";
            return !sperrKontextwoerterReferenz(text, LANR_ART).isEmpty()
                    || !sperrKontextwoerterReferenz(text, IK_ART).isEmpty();
        }

        String zufallsFolge(int laenge) {
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < laenge; i++) {
                if (i > 0) {
                    f.append(wahl(SPERR_TRENNER));
                }
                f.append(zufallsBuchstabe(POOL.charAt(r.nextInt(POOL.length()))));
            }
            return f.toString();
        }

        /**
         * Zieht einen Kandidaten fuer eine Einzelbuchstabenfolge. Die Zaehler {@code c.gezogen.*} zaehlen Ziehungen,
         * nicht angenommene Tokens: Ein Kandidat, den die Referenz als Kontextwort erkennt, wird verworfen
         * ({@code c.neuGezogen}); angenommene Tokens zaehlt {@code c.sperrToken}.
         */
        String sperrKandidat() {
            String wort = wahl(r.nextBoolean() ? SPERR_LANR : SPERR_IK);
            int muster = r.nextInt(8);
            st.zaehle("c.gezogen.muster." + muster);
            switch (muster) {
                case 0, 1 -> {
                    return zufallsFolge(2 + r.nextInt(7));
                }
                case 2 -> {
                    // ein Buchstabe durch einen Buchstaben einer anderen Schrift ersetzt
                    int i = r.nextInt(wort.length());
                    StringBuilder f = new StringBuilder();
                    for (int k = 0; k < wort.length(); k++) {
                        if (k > 0) {
                            f.append(wahl(SPERR_TRENNER));
                        }
                        f.append(k == i ? wahl(FREMDBUCHSTABEN) : zufallsBuchstabe(wort.charAt(k)));
                    }
                    return f.toString();
                }
                case 3 -> {
                    // Buchstaben vor und hinter dem Wort
                    String vorn = r.nextBoolean() ? zufallsBuchstabe('A') + wahl(SPERR_TRENNER) : "";
                    String hinten = r.nextBoolean() ? wahl(SPERR_TRENNER) + zufallsBuchstabe('X') : "";
                    return vorn + folge(wort, false) + hinten;
                }
                case 4 -> {
                    // ein Trenner durch ein Zeichen ersetzt, das die Folge bricht
                    String f = folge(wort, false);
                    List<Integer> stellen = new ArrayList<>();
                    for (int k = 0; k < f.length(); k++) {
                        if (istTrennerZeichen(f.charAt(k))) {
                            stellen.add(k);
                        }
                    }
                    int k = stellen.get(r.nextInt(stellen.size()));
                    return f.substring(0, k) + wahl(BRUCH) + f.substring(k + 1);
                }
                case 5 -> {
                    return folge(wort, false) + wahl(SPERR_TRENNER) + folge(wahl(r.nextBoolean() ? SPERR_LANR
                            : SPERR_IK), false);
                }
                case 6 -> {
                    st.zaehle("c.gezogen.initialen");
                    return wahl(INITIALEN);
                }
                default -> {
                    return folge(wort, r.nextBoolean());
                }
            }
        }

        boolean istTrennerZeichen(char c) {
            for (String t : SPERR_TRENNER) {
                if (t.charAt(0) == c) {
                    return true;
                }
            }
            return false;
        }

        String sperrToken() {
            for (int versuch = 0; versuch < 1_000; versuch++) {
                String kandidat = sperrKandidat();
                if (!ergibtKontextwort(kandidat)) {
                    st.zaehle("c.sperrToken");
                    return kandidat;
                }
                st.zaehle("c.neuGezogen");
            }
            throw new AssertionError("Generator (c): keine Einzelbuchstabenfolge ohne Kontextwort gefunden");
        }

        String rauschkette() {
            String kette;
            if (r.nextInt(3) == 0) {
                kette = "00" + ziffern(7 + r.nextInt(6));
            } else {
                int gesamt = 1 + r.nextInt(8);
                StringBuilder s = new StringBuilder();
                int rest = gesamt;
                while (rest > 0) {
                    int groesse = Math.min(1 + r.nextInt(4), rest);
                    s.append(ziffern(groesse));
                    rest -= groesse;
                    if (rest > 0) {
                        s.append(wahl(GRUPPENTRENNER));
                    }
                }
                kette = s.toString();
            }
            return kette;
        }

        void textC() {
            int tokens = 4 + r.nextInt(9);
            int vorher = 0;
            String[] sicher = {", ", "; ", "\n", ": ", "! ", "? "};
            String[] frei = {" ", ", ", "; ", "\n", ": ", " - ", "! ", ". ", "  "};
            for (int i = 0; i < tokens; i++) {
                int w = r.nextInt(100);
                int sorte = w < 25 ? 1 : w < 70 ? 2 : w < 90 ? 3 : 4;
                if (i > 0) {
                    // zwei Ziffernstuecke nie nur durch einen Gruppentrenner verbinden (Waechter, Neunerfenster);
                    // zwei Einzelbuchstabenfolgen nie durch einen Sperr-Trenner (sonst waere es eine Folge)
                    boolean ziffern = vorher >= 3 || sorte >= 3;
                    sb.append(ziffern || (vorher == 2 && sorte == 2) ? wahl(sicher) : wahl(frei));
                }
                switch (sorte) {
                    case 1 -> sb.append(wahl(RAUSCHWOERTER));
                    case 2 -> sb.append(sperrToken());
                    case 3 -> {
                        st.zaehle("c.nummer");
                        ohneFund(zahl());
                    }
                    default -> {
                        st.zaehle("c.rauschkette");
                        // jede Ziffernkette gilt als Nummer: dort wird nichts eingestreut (Invarianz, Grenze 7)
                        String kette = r.nextBoolean() ? rauschkette() : ziffern(1 + r.nextInt(5));
                        platziere(new Zahl(kette, kette));
                    }
                }
                vorher = sorte;
            }
        }

        // ---------------------------------------------------------------- (d) Gegenproben

        int musterAnzahl() {
            return art.lanr ? 9 + 3 + 18 : 9 + 3 + 18 + 5;
        }

        /** Verifizierter Buchstabenanteil: wird neu gezogen, solange die Referenz ein Kontextwort der Art findet. */
        String ohneKontextwort(Supplier<String> teil) {
            for (int versuch = 0; versuch < 1_000; versuch++) {
                String t = teil.get();
                String text = "\n" + t + "\n";
                if (sperrKontextwoerterReferenz(text, art).isEmpty()) {
                    return t;
                }
                st.zaehle("d.neuGezogen");
            }
            throw new AssertionError("Generator (d): kein Buchstabenanteil ohne Kontextwort gefunden");
        }

        void textD() {
            rauschen();
            int anzahl = 1 + r.nextInt(4);
            for (int i = 0; i < anzahl; i++) {
                endeKw = false;
                muster(r.nextInt(musterAnzahl()));
                sb.append(endeKw ? "! " : ". ");
                rauschen();
            }
        }

        String ersetzeTrenner(String wort) {
            // Buchstaben des Wortes, ein Trenner (zufaellig gewaehlt) durch ein brechendes Zeichen ersetzt
            int bruch = r.nextInt(wort.length() - 1);
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < wort.length(); i++) {
                if (i > 0) {
                    f.append(i - 1 == bruch ? wahl(BRUCH) : wahl(SPERR_TRENNER));
                }
                f.append(zufallsBuchstabe(wort.charAt(i)));
            }
            return f.toString();
        }

        String teilgruppe(String wort) {
            int paar;
            do {
                paar = r.nextInt(wort.length() - 1);
                // das zusammengeschriebene normale Wort IK waere selbst ein Kontextwort
            } while (wort.substring(paar, paar + 2).equalsIgnoreCase("IK"));
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < wort.length(); i++) {
                if (i > 0 && i - 1 != paar) {
                    f.append(wahl(SPERR_TRENNER));
                }
                f.append(zufallsBuchstabe(wort.charAt(i)));
            }
            return f.toString();
        }

        String initialen(String wort) {
            StringBuilder f = new StringBuilder();
            for (int i = 0; i < wort.length(); i++) {
                f.append(zufallsBuchstabe(wort.charAt(i))).append(". ");
            }
            return f.toString();
        }

        /** Hinter dem Buchstabenanteil eine Nummer (ohne erwarteten Fund). */
        void nummerDahinter(String trenner) {
            sb.append(trenner);
            ohneFund(zahl());
        }

        void muster(int id) {
            st.zaehle("d.muster." + id);
            String wort = wahl(sperrWorte());
            String drei = wahl(sperrWorteDrei());
            switch (id) {
                case 0 -> {
                    // ein Einzelbuchstabe vor der Folge: A L A N R N, A I K N
                    sb.append(ohneKontextwort(() -> zufallsBuchstabe((char) ('A' + r.nextInt(26)))
                            + wahl(SPERR_TRENNER) + folge(wort, false)));
                    nummerDahinter(wahl(DIR));
                }
                case 1 -> {
                    // Buchstaben hinter der Folge: L A N R X N, I K E A N, I K N N
                    String extra = wahl(new String[] {"X", "Y", "EA", "N", "XY", "E"});
                    sb.append(ohneKontextwort(() -> folge(wort + extra, false)));
                    nummerDahinter(wahl(DIR));
                }
                case 2 -> {
                    // ein Buchstabe weniger: L A N N, I K N N (nur ab vier Buchstaben, sonst nur ein Buchstabe)
                    sb.append(ohneKontextwort(() -> folge(drei.substring(0, drei.length() - 1), false)));
                    nummerDahinter(wahl(DIR));
                }
                case 3 -> {
                    // ein fremder Buchstabe mitten in der Folge: L A N X R N
                    sb.append(ohneKontextwort(() -> {
                        int i = 1 + r.nextInt(wort.length() - 1);
                        String fremd = wahl(new String[] {"X", "Y", "Q"});
                        return folge(wort.substring(0, i) + fremd + wort.substring(i), false);
                    }));
                    nummerDahinter(wahl(DIR));
                }
                case 4 -> {
                    // Teilgruppe: LA NR N, L ANR N (bekannte Grenze); das normale Wort IK ist ausgenommen
                    sb.append(ohneKontextwort(() -> teilgruppe(drei)));
                    nummerDahinter(wahl(DIR));
                }
                case 5 -> {
                    // unmittelbar wiederholte Sperrschrift: eine einzige Folge (Grenze 14d)
                    sb.append(ohneKontextwort(() -> folge(wort + wort, false)));
                    nummerDahinter(wahl(DIR));
                }
                case 6 -> {
                    // gebrochene Folge: Tab, Komma, Unterstrich, Schraegstrich, zwei Trenner, Umbruch
                    sb.append(ohneKontextwort(() -> ersetzeTrenner(wort)));
                    nummerDahinter(wahl(DIR));
                }
                case 7 -> {
                    // Initialenform mit Punkt und Leerzeichen: L. A. N. R. N, I. K. N
                    sb.append(initialen(wort));
                    sb.append(wahl(new String[] {"", "Mustermann, Rechnung "}));
                    ohneFund(zahl());
                }
                case 8 -> {
                    // gesperrtes Wort ohne Nummer; danach folgt Satzende und weiteres Rauschen, kein Textende
                    sb.append(folge(wort, r.nextBoolean()));
                    endeKw = true;
                }
                case 9 -> {
                    // mehr als drei Fuellwoerter hinter der Sperrschrift ab drei Buchstaben
                    sb.append(folge(drei, false)).append(wahl(DIR_NICHT_LEER));
                    fuellwoerter(4 + r.nextInt(3));
                    ohneFund(zahl());
                }
                case 10 -> {
                    // Leerzeile zwischen Sperrschrift und Nummer
                    sb.append(folge(drei, false)).append(wahl(new String[] {"", ":", " :", ": "}));
                    String erster = wahl(UMBRUECHE);
                    sb.append(erster).append(wahl(erster.equals("\r") ? new String[] {" ", "\t"}
                            : new String[] {"", " ", "\t"}));
                    sb.append(wahl(UMBRUECHE)).append(wahl(new String[] {"", " "}));
                    ohneFund(zahl());
                }
                case 11 -> {
                    // Sperrschrift der anderen Art
                    String[] fremd = art.lanr ? SPERR_IK : SPERR_LANR;
                    sb.append(folge(wahl(fremd), false));
                    nummerDahinter(wahl(DIR));
                }
                default -> {
                    if (id < 30) {
                        kommaMuster(id - 12);
                    } else {
                        ikMuster(id - 30);
                    }
                }
            }
        }

        void kommaMuster(int k) {
            Zahl z = zahl();
            ohneFund(z);
            String kw = nachgestelltesKontextwort("d");
            endeKw = true;
            switch (k) {
                case 0 -> sb.append(",, ").append(kw);
                case 1 -> sb.append(",; ").append(kw);
                case 2 -> sb.append(";, ").append(kw);
                case 3 -> sb.append(", und ").append(kw);
                case 4 -> sb.append(", / ").append(kw);
                case 5 -> sb.append(wahl(new String[] {": ", " : ", ":"})).append(kw);
                case 6 -> sb.append(". ").append(kw);
                case 7 -> sb.append(", ").append(wahl(FUELL)).append(' ').append(kw);
                case 8 -> sb.append(',').append(wahl(UMBRUECHE)).append(wahl(new String[] {"", " ", "\t"})).append(kw);
                case 9 -> sb.append(", ((").append(kw).append(')');
                case 10 -> sb.append("(, ").append(kw).append(')');
                case 11 -> sb.append(" und ").append(kw);
                case 12 -> sb.append(" / ").append(kw);
                case 13 -> sb.append(" und, ").append(kw);
                case 14 -> sb.append(" /, ").append(kw);
                case 15 -> sb.append(", , ").append(kw);
                case 16 -> sb.append(" ((").append(kw).append(')');
                default -> sb.append(", [(").append(kw).append(")]");
            }
        }

        void ikMuster(int k) {
            switch (k) {
                case 0 -> {
                    // Fuellwoerter hinter I K (D-3): I K der Praxis N
                    sb.append(folge("IK", r.nextInt(4) == 0)).append(wahl(DIR_NICHT_LEER));
                    fuellwoerter(1 + r.nextInt(3));
                    ohneFund(zahl());
                }
                case 1 -> {
                    // Zeilenumbruch hinter I K
                    sb.append(folge("IK", false)).append(wahl(new String[] {"", ":", " :", ": "}));
                    sb.append(wahl(UMBRUECHE)).append(wahl(new String[] {"", " ", "\t"}));
                    ohneFund(zahl());
                }
                case 2 -> {
                    // nachgestelltes I K (D-3): N (I K), N I K, N, I K
                    ohneFund(zahl());
                    sb.append(wahl(new String[] {" (", " ", ", ", " , ", ",\t", "; ", ";", " ["}));
                    sb.append(folge("IK", false));
                    endeKw = true;
                }
                case 3 -> {
                    // I K, danach vier oder mehr Woerter, dann die Nummer
                    sb.append(folge("IK", false)).append(wahl(DIR_NICHT_LEER));
                    fuellwoerter(4 + r.nextInt(3));
                    ohneFund(zahl());
                }
                default -> {
                    // Initialen im Satz
                    sb.append(wahl(new String[] {"I. K. Mustermann, Rechnung ", "Prof. I. K. Mustermann, Rechnung ",
                        "Dr. I. K. Muster, Praxis "}));
                    ohneFund(zahl());
                }
            }
        }
    }

    // Gemeinsame Bausteine der Testschleifen und von differenzTexte(): eine Aenderung hier wirkt auf beide,
    // so dass die Differenzpruefung immer dieselben Texte sieht wie die Eigenschaftstests (siehe auch
    // differenzTexte_liefertDieTexteDerTestschleifen).

    private static Art artVon(int iteration) {
        return iteration % 2 == 0 ? LANR_ART : IK_ART;
    }

    private static Bauer bauA(Random r, int iteration, Statistik st) {
        Bauer b = new Bauer(r, artVon(iteration), st, "a");
        b.textA();
        return b;
    }

    private static Bauer bauB(Random r, int iteration, Statistik st) {
        Bauer b = new Bauer(r, artVon(iteration), st, "b");
        b.textB();
        return b;
    }

    /** Text von (c), mit Wahrscheinlichkeit 1/4 mit eingestreuten unsichtbaren Zeichen. */
    private static String bauC(Random r, int iteration, Statistik st) {
        Bauer b = new Bauer(r, artVon(iteration), st, "c");
        b.textC();
        String text = b.sb.toString();
        if (r.nextInt(4) == 0) {
            text = streue(r, text, b.nummern).text;
            st.zaehle("c.mitUnsichtbaren");
        }
        return text;
    }

    private static Bauer bauD(Random r, int iteration, Statistik st) {
        Bauer b = new Bauer(r, artVon(iteration), st, "d");
        b.textD();
        return b;
    }

    /**
     * Die Texte der Generatoren (a) bis (d) mit denselben Seeds wie die Tests, ohne Pruefung: Eingabe fuer die
     * Differenzpruefung auf Scannerebene ({@code KvVorgaengerDifferenzTest}, T-6). Jeder Text geht dort vor dem
     * Vergleich durch den Waechter. Nutzt dieselben Helfer ({@code bauA} bis {@code bauD}) wie die Testschleifen.
     */
    public static List<String> differenzTexte() {
        List<String> texte = new ArrayList<>();
        Random ra = new Random(SEED_A);
        Random rb = new Random(SEED_B);
        Random rc = new Random(SEED_C);
        Random rd = new Random(SEED_D);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            texte.add(bauA(ra, it, st).sb.toString());
            texte.add(bauB(rb, it, st).sb.toString());
            texte.add(bauC(rc, it, st));
            texte.add(bauD(rd, it, st).sb.toString());
        }
        return texte;
    }

    @Test
    void differenzTexte_liefertDieTexteDerTestschleifen() {
        List<String> texte = differenzTexte();
        assertEquals(4 * TEXTE, texte.size());
        Random ra = new Random(SEED_A);
        Random rb = new Random(SEED_B);
        Random rc = new Random(SEED_C);
        Random rd = new Random(SEED_D);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            assertEquals(bauA(ra, it, st).sb.toString(), texte.get(4 * it), "a, Iteration " + it);
            assertEquals(bauB(rb, it, st).sb.toString(), texte.get(4 * it + 1), "b, Iteration " + it);
            assertEquals(bauC(rc, it, st), texte.get(4 * it + 2), "c, Iteration " + it);
            assertEquals(bauD(rd, it, st).sb.toString(), texte.get(4 * it + 3), "d, Iteration " + it);
        }
    }

    // ------------------------------------------------------------------ Pruefungen

    private static void pruefe(Art art, String text, List<Erw> erwartet, String ort) {
        List<Finding> ist = scan(art, text);
        assertEquals(erwartet.size(), ist.size(), ort + " Anzahl Funde");
        for (int k = 0; k < erwartet.size(); k++) {
            Erw e = erwartet.get(k);
            Finding f = ist.get(k);
            assertEquals(e.start(), f.start(), ort + " Start " + k);
            assertEquals(e.start() + e.wert().length(), f.end(), ort + " Ende " + k);
            assertTrue(e.wert().equals(f.originalValue()), ort + " Wert " + k);
            assertEquals(e.konfidenz(), f.confidence(), ort + " Konfidenz " + k);
            assertEquals(ConfidenceLevel.of(e.konfidenz()), f.level(), ort + " Stufe " + k);
            assertEquals(art.quelleNachNummer(e.wert()), f.source(), ort + " Quelle " + k);
        }
        assertEquals(ist, art.erkenner.detect(text), ort + " Determinismus");
    }

    private static String ort(long seed, int iteration) {
        return "Seed " + seed + " Iteration " + iteration;
    }

    // ------------------------------------------------------------------ (a)

    @Test
    void ak28a_gesperrtesKontextwortPlusNummer_genauDieKonstruiertenFunde() {
        Random r = new Random(SEED_A);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            String ort = ort(SEED_A, it);
            Art art = artVon(it);
            Art andere = art == LANR_ART ? IK_ART : LANR_ART;
            Bauer b = bauA(r, it, st);
            String text = b.sb.toString();
            pruefe(art, text, b.erwartet, ort);
            assertEquals(List.of(), scan(andere, text), ort + " andere Art darf nichts finden");
        }
        for (String name : TRENNER_NAME) {
            st.mindestens("a.trenner." + name, 400);
        }
        for (Art art : ARTEN) {
            for (String w : art.lanr ? SPERR_LANR : SPERR_IK) {
                st.mindestens("a.wort." + art.name + "." + w, 60);
            }
        }
        st.mindestens("a.punktEnde", 100);
        // Messwerte (fester Seed): direkt 1 340, Fuellwort 320, Umbruch 330, Aufzaehlung 425, zweite Nummer 62
        for (String k : new String[] {"direkt", "fuellwort", "umbruch", "aufzaehlung"}) {
            st.mindestens("a.block." + k, 60);
        }
        // Reine Vorkommens-Untergrenze (keine Erkennungspruefung): 40 liegt mehr als 30 % unter dem Messwert 62
        // bei festem Seed (vorher 60, zu nah am Messwert).
        st.mindestens("a.block.zweiteNummerOhneFund", 40);
        st.mindestens("a.fund.sperrDrei", 500);
        st.mindestens("a.fund.sperrDrei.0.8.lanr", 150);
        st.mindestens("a.fund.sperrDrei.0.8.ik", 100);
        st.mindestens("a.fund.sperrDrei.0.5.lanr-pruefziffer-ungueltig", 30);
        st.mindestens("a.fund.sperrDrei.0.5.ik-pruefziffer-ungueltig", 30);
        st.mindestens("a.fund.sperrDrei.0.4.lanr-laenge-abweichend", 30);
        st.mindestens("a.fund.sperrDrei.0.4.ik-laenge-abweichend", 30);
        st.mindestens("a.fund.sperrZwei", 100);
        st.mindestens("a.fund.sperrZwei.0.5.ik", 40);
        st.mindestens("a.fund.sperrZwei.0.5.ik-pruefziffer-ungueltig", 20);
        st.mindestens("a.fund.sperrZwei.0.4.ik-laenge-abweichend", 20);
        st.mindestens("a.fund.aufzaehlung", 50);
        st.mindestens("a.zahl.trennerform", 100);
    }

    // ------------------------------------------------------------------ (b)

    @Test
    void ak28b_nummerKommaKontextwort_genauDieKonstruiertenFunde() {
        Random r = new Random(SEED_B);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            String ort = ort(SEED_B, it);
            Art art = artVon(it);
            Bauer b = bauB(r, it, st);
            pruefe(art, b.sb.toString(), b.erwartet, ort);
        }
        st.mindestens("b.fund.komma", 1_000);
        st.mindestens("b.fund.komma.0.6.lanr", 150);
        st.mindestens("b.fund.komma.0.6.ik", 150);
        st.mindestens("b.fund.komma.0.5.lanr-pruefziffer-ungueltig", 60);
        st.mindestens("b.fund.komma.0.5.ik-pruefziffer-ungueltig", 60);
        st.mindestens("b.fund.komma.0.4.lanr-laenge-abweichend", 60);
        st.mindestens("b.fund.komma.0.4.ik-laenge-abweichend", 60);
        st.mindestens("b.fund.nachgestellt", 300);
        st.mindestens("b.fund.nachgestellt.0.8.lanr", 40);
        st.mindestens("b.fund.nachgestellt.0.8.ik", 40);
        st.mindestens("b.zeichen.,", 300);
        st.mindestens("b.zeichen.;", 300);
        for (int n = 1; n <= 3; n++) {
            st.mindestens("b.folgenLaenge." + n, 200);
        }
        for (String innen : new String[] {",", ";", "/", "und", ",_und", "/_,"}) {
            st.mindestens("b.innen." + innen, 20);
        }
        st.mindestens("b.klammer.rund", 150);
        st.mindestens("b.klammer.eckig", 150);
        st.mindestens("b.komma.kw.gesperrt", 100);
        st.mindestens("b.komma.kw.normal", 300);
        st.mindestens("b.kontrolle.kw.gesperrt", 30);
        st.mindestens("b.kontrolle.kw.normal", 100);
        for (String name : TRENNER_NAME) {
            st.mindestens("b.trenner." + name, 40);
        }
    }

    // ------------------------------------------------------------------ (c)

    @Test
    void ak28c_rauschtexteMitEinzelbuchstabenInitialenNummern_keinFund() {
        Random r = new Random(SEED_C);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            String ort = ort(SEED_C, it);
            String text = bauC(r, it, st);
            // Selbstpruefung des Generators: die Referenz findet im ganzen Text kein Kontextwort
            for (Art art : ARTEN) {
                assertEquals(0, sperrKontextwoerterReferenz(text, art).size(), ort + " Generator " + art.name);
            }
            for (Art art : ARTEN) {
                pruefe(art, text, List.of(), ort + " " + art.name);
            }
        }
        st.mindestens("c.neuGezogen", 200);
        st.mindestens("c.sperrToken", 3_000);
        st.mindestens("c.gezogen.initialen", 200);
        st.mindestens("c.nummer", 500);
        st.mindestens("c.rauschkette", 200);
        st.mindestens("c.mitUnsichtbaren", 150);
        for (int m = 0; m < 8; m++) {
            st.mindestens("c.gezogen.muster." + m, 200);
        }
    }

    // ------------------------------------------------------------------ (d)

    @Test
    void ak28d_gegenprobenAusAk6Ak7Ak18_keinFund() {
        Random r = new Random(SEED_D);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            String ort = ort(SEED_D, it);
            Art art = artVon(it);
            Bauer b = bauD(r, it, st);
            pruefe(art, b.sb.toString(), b.erwartet, ort);
        }
        for (int id = 0; id < 9 + 3 + 18; id++) {
            st.mindestens("d.muster." + id, 40);
        }
        // Messwerte (Seed fest): Muster 0 bis 29 mindestens 59, Muster 30 bis 34 mindestens 25.
        // Reine Vorkommens-Untergrenze (keine Erkennungspruefung): 15 liegt mehr als 30 % unter dem kleinsten
        // Messwert 25 (vorher 20, zu nah am Messwert).
        for (int id = 30; id < 35; id++) {
            st.mindestens("d.muster." + id, 15);
        }
        st.mindestens("d.kw.gesperrt", 100);
        st.mindestens("d.kw.normal", 300);
    }

    // ------------------------------------------------------------------ (e) Invarianz gegen unsichtbare Zeichen

    private record Gestreut(String text, int[] positionen) {
    }

    /**
     * Streut 1 bis 6 unsichtbare Zeichen ein. Ausgespart (Codepunkt-basiert): Positionen im Inneren einer Nummer,
     * zwischen zwei Buchstaben, innerhalb eines Surrogatpaars und zwischen CR und LF (siehe Klassenbeschreibung).
     */
    private static Gestreut streue(Random r, String text, List<int[]> nummern) {
        List<Integer> erlaubt = new ArrayList<>();
        for (int p = 0; p <= text.length(); p++) {
            boolean imInneren = false;
            for (int[] n : nummern) {
                imInneren |= p > n[0] && p < n[1];
            }
            boolean imSurrogatpaar = p > 0 && p < text.length() && Character.isHighSurrogate(text.charAt(p - 1))
                    && Character.isLowSurrogate(text.charAt(p));
            boolean zwischenBuchstaben = !imSurrogatpaar && p > 0 && p < text.length()
                    && Character.isLetter(text.codePointBefore(p)) && Character.isLetter(text.codePointAt(p));
            boolean zwischenCrLf = p > 0 && p < text.length() && text.charAt(p - 1) == '\r'
                    && text.charAt(p) == '\n';
            if (!imInneren && !imSurrogatpaar && !zwischenBuchstaben && !zwischenCrLf) {
                erlaubt.add(p);
            }
        }
        int anzahl = 1 + r.nextInt(6);
        int[] positionen = new int[anzahl];
        for (int i = 0; i < anzahl; i++) {
            positionen[i] = erlaubt.get(r.nextInt(erlaubt.size()));
        }
        Arrays.sort(positionen);
        StringBuilder neu = new StringBuilder();
        int index = 0;
        for (int p = 0; p <= text.length(); p++) {
            while (index < anzahl && positionen[index] == p) {
                neu.append(UNSICHTBAR[r.nextInt(UNSICHTBAR.length)]);
                index++;
            }
            if (p < text.length()) {
                neu.append(text.charAt(p));
            }
        }
        return new Gestreut(neu.toString(), positionen);
    }

    private static List<Erw> verschiebe(List<Erw> erwartet, int[] positionen) {
        List<Erw> neu = new ArrayList<>();
        for (Erw e : erwartet) {
            int davor = 0;
            for (int p : positionen) {
                if (p <= e.start()) {
                    davor++;
                }
            }
            neu.add(new Erw(e.start() + davor, e.wert(), e.konfidenz()));
        }
        return neu;
    }

    private void invarianz(long seed, char modus) {
        Random r = new Random(seed);
        Statistik st = new Statistik();
        for (int it = 0; it < TEXTE; it++) {
            String ort = ort(seed, it);
            Art art = it % 2 == 0 ? LANR_ART : IK_ART;
            Bauer b = new Bauer(r, art, st, String.valueOf(modus));
            if (modus == 'a') {
                b.textA();
            } else if (modus == 'b') {
                b.textB();
            } else {
                b.textD();
            }
            String text = b.sb.toString();
            Gestreut g = streue(r, text, b.nummern);
            st.zaehle("e.eingestreut");
            st.z.merge("e.zeichen", g.positionen().length, Integer::sum);
            // gleiche Funde wie im ungestoerten Text (Position verschoben); dessen Fundliste ist die Konstruktion
            pruefe(art, g.text(), verschiebe(b.erwartet, g.positionen()), ort);
            st.z.merge("e.funde", b.erwartet.size(), Integer::sum);
        }
        st.mindestens("e.eingestreut", TEXTE);
        st.mindestens("e.zeichen", 2_000);
        if (modus != 'd') {
            st.mindestens("e.funde", 1_000);
        }
    }

    @Test
    void ak28e_unsichtbareZeichenInTextenAusA_aendernDieFundeNicht() {
        invarianz(SEED_E_A, 'a');
    }

    @Test
    void ak28e_unsichtbareZeichenInTextenAusB_aendernDieFundeNicht() {
        invarianz(SEED_E_B, 'b');
    }

    @Test
    void ak28e_unsichtbareZeichenInGegenprobenAusD_oeffnenKeineLuecke() {
        invarianz(SEED_E_D, 'd');
    }

    // ------------------------------------------------------------------ Referenz selbst

    @Test
    void referenz_findetDieBekanntenFolgenUndSonstNichts() {
        String ik = "I.K.";
        assertEquals(1, sperrKontextwoerterReferenz("Praxis " + ik + " x", IK_ART).size());
        assertEquals(0, sperrKontextwoerterReferenz("Praxis " + ik + " x", LANR_ART).size());
        assertEquals(1, sperrKontextwoerterReferenz("L A N R", LANR_ART).size());
        assertEquals(0, sperrKontextwoerterReferenz("L  A N R", LANR_ART).size());
        assertEquals(0, sperrKontextwoerterReferenz("I K N", IK_ART).size());
        assertEquals(0, sperrKontextwoerterReferenz("I. K. N", IK_ART).size());
        // Faltung nur A-Z: das Kelvin-Zeichen (U+212A) ist kein K; als echtes K waere es eine Folge
        assertEquals(0, sperrKontextwoerterReferenz("I " + (char) 0x212A + ":", IK_ART).size());
        assertEquals(1, sperrKontextwoerterReferenz("I K:", IK_ART).size());
        // unsichtbare Zeichen: L ZWSP A ist ein Lauf mit zwei Buchstaben; L ZWSP Leerzeichen A ist eine Folge
        assertEquals(0, sperrKontextwoerterReferenz("L" + ZWSP + "A N R", LANR_ART).size());
        assertEquals(1, sperrKontextwoerterReferenz("L" + ZWSP + " A N R", LANR_ART).size());
        assertEquals(0, sperrKontextwoerterReferenz("L " + ZWSP + " A N R", LANR_ART).size());
        List<int[]> treffer = sperrKontextwoerterReferenz("ab I" + ZWSP + ".K. x", IK_ART);
        assertEquals(1, treffer.size());
        assertEquals(3, treffer.get(0)[0]);
        // Ende hinter dem letzten Buchstaben (K), der Punkt danach gehoert nicht dazu
        assertEquals(7, treffer.get(0)[1]);
    }
}
