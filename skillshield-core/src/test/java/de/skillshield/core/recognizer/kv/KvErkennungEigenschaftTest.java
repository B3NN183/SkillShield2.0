package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_12;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest der Erkennung (Feature kv-nummern-erkennung, Task T-5, AK-12 bis AK-16, AK-26). Je Text werden
 * Bloecke aus Kontextwort-Variante, Direktzeichen und Nummer mit bekannter Position und erwarteter Konfidenz
 * konstruiert, getrennt durch Satzende und Rauschen. Das Orakel ist die Konstruktion: Die Fundliste des Erkenners
 * muss genau die konstruierten Bloecke enthalten (Position, Wert, Konfidenz, Stufe, Quelle, Typ). Zusaetzlich: Die
 * Konfidenz entsteht aus der unabhaengigen Referenz aus {@code SynthetischeKvNummern}, nicht aus dem Produkt.
 *
 * <p><b>Blockarten.</b> A: Kontextwort, Direktzeichen, Nummer (direkt). B: ein bis drei Fuellwoerter dazwischen
 * (0.80). C: ein Zeilenumbruch dazwischen, auch mit Fuellwoertern (0.80). D: nachgestellt mit {@code (}, {@code [}
 * oder ohne Klammer (0.80). E: Aufzaehlung aus zwei bis vier Nummern (erste direkt, weitere 0.80). X: Negativbloecke
 * ohne erwarteten Fund (vier Fuellwoerter, Leerzeile, Satzende, zwei Klammern, Wort vor dem nachgestellten Kontextwort,
 * Kontextwort-Doppelgaenger, Vorwort ohne Nachsatzwort). Nummern: gueltig (aus {@code bilde...}), ungueltig (eine
 * Ziffer geaendert, nach Referenz ungueltig), Laengenabweichung (Konstanten), auch mit Gruppentrennern. Kontextwoerter
 * erscheinen auch mit eingestreuten unsichtbaren Zeichen.
 *
 * <p><b>Trennung der Bloecke.</b> Hinter einem Block steht {@code ". "}, wenn er mit einer Nummer endet (nach einem
 * Fund ist der Kontext nicht mehr direkt, der Punkt ist Satzende). Endet ein Block mit einem Kontextwort (D und
 * einige X), steht {@code "! "}: Ein Punkt direkt hinter einem Kontextwort ist ein Abkuerzungspunkt (Kap. 3.3) und
 * beendet den Kontext nicht. Danach folgt Rauschen ohne Kontextwort.
 *
 * <p><b>Testdatenregel (T-1 Punkt 10).</b> Kein Text enthaelt ein Neunerfenster ohne Praefix {@code 00}, das nach
 * einem der Verfahren gueltig ist. Konstruktion: Nummern kommen aus {@code bilde...} (Praefix {@code 00}) und aus
 * den Laengenkonstanten (Praefix {@code 00} oder weniger bzw. mehr als neun Ziffern). Ziffernketten im Rauschen sind
 * entweder hoechstens acht Ziffern lang (in einem bis drei Bloecken: nie ein Neunerfenster) oder ein einziger Block
 * mit 9 bis 14 Ziffern, der mit {@code 00} beginnt. Jede Rauschkette steht vor {@code ", "} (das Komma beendet die
 * Kette), Fuellzahlen sind einzelne Bloecke mit hoechstens fuenf Ziffern. Die Methode
 * {@code assertKeinGueltigesNeunerfensterOhne00} sichert jeden erzeugten Text per Assertion ab, und
 * {@link #pruefeRauschkette(String)} prueft jede einzelne Rauschkette.
 *
 * <p>Meldungen nennen nur Seed und Iteration, nie den Text.
 */
class KvErkennungEigenschaftTest {

    private static final long SEED = 20261124L;
    private static final long SEED_OHNE_KONTEXT = 20261125L;
    private static final long SEED_UNSICHTBAR = 20261126L;
    private static final int TEXTE = 1_000;
    private static final double KOMMA = 0.60;

    private static final char[] UNSICHTBAR = {SHY, ZWSP, ZWNJ, ZWJ, WORD_JOINER, BOM, (char) 0xFE0F, (char) 0xFE00,
        (char) 0xFE0E};
    private static final String[] GRUPPENTRENNER = {" ", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), "-", ".", "/"};
    private static final String[] LAENGEN = {LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, LAENGE_12};
    private static final String[] ZEILENUMBRUECHE = {"\n", "\r\n", "\r", String.valueOf(NEL), String.valueOf(LS),
        String.valueOf(PS)};

    /** Fuellwoerter (enthalten kein Kontextwort; {@code Arzt} ist ein Vorwort ohne Nachsatzwort). */
    private static final String[] FUELLWOERTER = {"des", "der", "Behandlers", "Praxis", "bitte", "siehe", "unten",
        "und", "Arzt", "Frau", "Herr", "Beispiel", u('G', 'r', 0xFC, 'n'), u(0x41F, 0x440, 0x438, 0x432, 0x435, 0x442),
        u(0x3B1, 0x3BB, 0x3C6, 0x3B1), u(0x65E5, 0x672C, 0x8A9E), u(0x645, 0x631, 0x62D, 0x628, 0x627),
        u(0x1D400, 0x1D401), "Pra" + SHY + "xis"};

    /** Rauschwoerter: wie die Fuellwoerter, aber ohne {@code Arzt} (kein Vorwort neben einem {@code Nr}). */
    private static final String[] RAUSCHWOERTER = {"des", "der", "Praxis", "Muster", "Rechnung", "Dr", "Frau",
        "Ordination", "Nrx", "Nummern", "Praxisnummer", "Arztes", u('Z', 0xFC, 'r', 'i', 'c', 'h'),
        u(0x41F, 0x440, 0x438, 0x432, 0x435, 0x442), u(0x3B1, 0x3BB, 0x3C6, 0x3B1), u(0x65E5, 0x672C),
        u(0x1D400, 0x1D401), "Pra" + SHY + "xis"};

    /** Doppelgaenger: Woerter, die fuer keine der beiden Arten ein Kontextwort sind. */
    private static final String[] DOPPELGAENGER = {"LANRX", "xLANR", "IKEA", "Technik", "Fabrik", "IKs", "Mikrofon",
        u(0x130, 'K'), u(0x131, 'k'), "I" + (char) 0x212A, "IK" + AKUT, "LANR" + AKUT, "Lanrnr" + "x",
        "Praxis" + AKUT + "LANR"};

    /** Neutrale Folgen (kein Wort, kein Satzzeichen): Emoji, nur Formatzeichen, alleinstehendes Kombinationszeichen. */
    private static final String[] NEUTRAL = {u(0x1F600), String.valueOf(ZWSP), "" + SHY + ZWJ, String.valueOf(AKUT),
        u(0x1F4A1, 0x1F4A1)};

    /** Satzzeichen-Tokens im Rauschen (ohne Satzende-Wirkung auf spaetere Bloecke). */
    private static final String[] ZEICHEN = {"(", ")", "[", "]", "-", "/", "#", "=", "*", "&", ":", ";", "\"", "@"};

    /** Direktzeichen hinter dem Kontextwort (nur Nachsatzwoerter, Zeichen und Leerraum: bleibt direkt). */
    private static final String[] DIREKT = {" ", ": ", "=", ";", "\t", "", " : ", ":  ", String.valueOf(NBSP), ". ",
        ".", " - ", " Nr ", "-Nr. ", "-Nummer: ", "  ", ":"};

    /** Trenner hinter dem Kontextwort, wenn ein Fuellwort oder Umbruch folgt (nie direkt an einem Buchstaben). */
    private static final String[] DIREKT_NICHT_LEER = {" ", ": ", "=", "\t", " : ", ";"};

    private static final String[] SUFFIX = {"", "", "", ",", ")", " gesendet", ";", "\"", ":", " bitte", " (intern)"};

    /** Erwarteter Fund aus der Konstruktion. */
    private record Erw(int start, String wert, double konfidenz) {
    }

    /** Eine Nummer im Text samt ihren Ziffern. */
    private record Zahl(String text, String ziffern) {
    }

    /** Zaehler, damit der Test belegt, dass der Generator alle Arten und Stufen erzeugt. */
    private static final class Statistik {
        final int[] bloecke = new int[6];
        int sicher;
        int wahrscheinlich;
        int pruefziffer;
        int laenge;
        int invisibleKw;
        int trennerform;
        int sondertrenner;
        int funde;
    }

    // ------------------------------------------------------------------ Generator

    private static final class Bauer {
        final Random r;
        final Art art;
        final Statistik statistik;
        final StringBuilder sb = new StringBuilder();
        final List<Erw> erwartet = new ArrayList<>();

        Bauer(Random r, Art art, Statistik statistik) {
            this.r = r;
            this.art = art;
            this.statistik = statistik;
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

        // ---- Text als Ganzes

        void text() {
            rauschen();
            int anzahl = 1 + r.nextInt(5);
            for (int i = 0; i < anzahl; i++) {
                boolean endetMitKontextwort = block();
                sb.append(endetMitKontextwort ? "! " : ". ");
                rauschen();
            }
        }

        /** Liefert {@code true}, wenn der Block mit einem Kontextwort endet. */
        boolean block() {
            int w = r.nextInt(100);
            if (w < 28) {
                statistik.bloecke[0]++;
                blockA();
                return false;
            }
            if (w < 45) {
                statistik.bloecke[1]++;
                blockB();
                return false;
            }
            if (w < 60) {
                statistik.bloecke[2]++;
                blockC();
                return false;
            }
            if (w < 77) {
                statistik.bloecke[3]++;
                blockD();
                return true;
            }
            if (w < 90) {
                statistik.bloecke[4]++;
                blockE();
                return false;
            }
            statistik.bloecke[5]++;
            return blockX();
        }

        // ---- Bausteine

        String kontextwort() {
            return kontextwort(false);
        }

        /**
         * Ein Kontextwort der Art, mit Wahrscheinlichkeit 1/4 mit eingestreuten unsichtbaren Zeichen. Beim
         * nachgestellten Kontext ohne vorangestelltes Fuellwort ({@code Praxis-}, {@code Lebenslange}), weil ein Wort
         * zwischen Zahl und Kontextwort das offene Segment leert (Kap. 3.3).
         */
        String kontextwort(boolean nachgestellt) {
            String kw = wahl(art.kws);
            while (nachgestellt && (kw.startsWith("Praxis") || kw.startsWith("Lebenslange"))) {
                kw = wahl(art.kws);
            }
            if (r.nextInt(4) == 0) {
                StringBuilder s = new StringBuilder(kw);
                int einfuegungen = 1 + r.nextInt(2);
                for (int i = 0; i < einfuegungen; i++) {
                    s.insert(r.nextInt(s.length() + 1), wahl(boxed(UNSICHTBAR)));
                }
                statistik.invisibleKw++;
                return s.toString();
            }
            return kw;
        }

        private Character[] boxed(char[] zeichen) {
            Character[] c = new Character[zeichen.length];
            for (int i = 0; i < zeichen.length; i++) {
                c[i] = zeichen[i];
            }
            return c;
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
            boolean sonder = false;
            while (pos < neun.length()) {
                int groesse = Math.min(1 + r.nextInt(5), neun.length() - pos);
                s.append(neun, pos, pos + groesse);
                pos += groesse;
                if (pos < neun.length()) {
                    String t = wahl(GRUPPENTRENNER);
                    sonder |= t.charAt(0) == DUENN || t.charAt(0) == SCHMAL_NBSP;
                    s.append(t);
                }
            }
            statistik.trennerform++;
            if (sonder) {
                statistik.sondertrenner++;
            }
            return s.toString();
        }

        Zahl zahl() {
            int art = r.nextInt(10);
            String ziffern;
            if (art < 5) {
                ziffern = this.art.bilde(r);
            } else if (art < 8) {
                ziffern = ungueltig();
            } else {
                ziffern = LAENGEN[r.nextInt(LAENGEN.length)];
            }
            String text = ziffern;
            if (ziffern.length() == 9 && r.nextInt(100) < 35) {
                text = trennerform(ziffern);
            }
            return new Zahl(text, ziffern);
        }

        double konfidenz(Zahl z, boolean direkt) {
            if (z.ziffern().length() != 9) {
                return LAENGE;
            }
            if (!art.gueltig(z.ziffern())) {
                return PZ_UNGUELTIG;
            }
            return direkt ? art.sicher : WAHRSCHEINLICH;
        }

        void nummer(Zahl z, boolean direkt) {
            double konfidenz = konfidenz(z, direkt);
            erwartet.add(new Erw(sb.length(), z.text(), konfidenz));
            sb.append(z.text());
            if (konfidenz == art.sicher) {
                statistik.sicher++;
            } else if (konfidenz == WAHRSCHEINLICH) {
                statistik.wahrscheinlich++;
            } else if (konfidenz == PZ_UNGUELTIG) {
                statistik.pruefziffer++;
            } else {
                statistik.laenge++;
            }
        }

        void fuellwoerter(int anzahl) {
            for (int i = 0; i < anzahl; i++) {
                if (r.nextInt(6) == 0) {
                    sb.append(wahl(NEUTRAL)).append(' ');
                }
                if (r.nextInt(4) == 0) {
                    // ein kurzer Ziffernblock (hoechstens fuenf Ziffern) ist ebenfalls ein Fuellwort
                    sb.append(ziffern(1 + r.nextInt(5))).append(", ");
                } else {
                    String joiner = r.nextInt(3) == 0 ? ", " : " ";
                    sb.append(wahl(FUELLWOERTER)).append(joiner);
                }
            }
        }

        /** Beginnt das Kontextwort (ohne unsichtbare Zeichen) mit einem Wort davor? Dann leert es die Folge. */
        private boolean beginntMitWortDavor(String kw) {
            StringBuilder ohne = new StringBuilder();
            for (int i = 0; i < kw.length(); i++) {
                if (!istUnsichtbar(kw.charAt(i))) {
                    ohne.append(kw.charAt(i));
                }
            }
            return ohne.indexOf("Praxis") == 0 || ohne.indexOf("Lebenslange") == 0;
        }

        private boolean istUnsichtbar(char c) {
            for (char u : UNSICHTBAR) {
                if (u == c) {
                    return true;
                }
            }
            return false;
        }

        /** Konfidenz bei genau einem Komma vor dem nachgestellten Kontextwort (D-5). */
        double konfidenzKomma(Zahl z) {
            if (z.ziffern().length() != 9) {
                return LAENGE;
            }
            return art.gueltig(z.ziffern()) ? KOMMA : PZ_UNGUELTIG;
        }

        // ---- Bloecke

        void blockA() {
            sb.append(kontextwort()).append(wahl(DIREKT));
            nummer(zahl(), true);
            sb.append(wahl(SUFFIX));
        }

        void blockB() {
            sb.append(kontextwort()).append(wahl(DIREKT_NICHT_LEER));
            fuellwoerter(1 + r.nextInt(3));
            nummer(zahl(), false);
            sb.append(wahl(SUFFIX));
        }

        void blockC() {
            sb.append(kontextwort());
            if (r.nextInt(3) == 0) {
                sb.append(wahl(DIREKT_NICHT_LEER));
                fuellwoerter(1 + r.nextInt(2));
            } else {
                sb.append(wahl(new String[] {"", ":", " :", ": ", " "}));
            }
            sb.append(wahl(new String[] {"", " ", "  ", "\t"})).append(wahl(ZEILENUMBRUECHE));
            sb.append(wahl(new String[] {"", " ", "  ", "\t", "    "}));
            nummer(zahl(), false);
            sb.append(wahl(SUFFIX));
        }

        void blockD() {
            sb.append(wahl(new String[] {"", "", "Nummer ", "Kennung ", "Wert: "}));
            nummer(zahl(), false);
            sb.append(wahl(new String[] {" ", "  ", "\t", String.valueOf(NBSP), String.valueOf(DUENN), "", " \t"}));
            int klammer = r.nextInt(3);
            String auf = klammer == 0 ? "(" : klammer == 1 ? "[" : "";
            String zu = klammer == 0 ? ")" : klammer == 1 ? "]" : "";
            sb.append(auf);
            if (klammer < 2 && r.nextBoolean()) {
                sb.append(' ');
            }
            sb.append(kontextwort(true));
            if (r.nextInt(4) != 0) {
                sb.append(zu);
            }
        }

        void blockE() {
            sb.append(kontextwort());
            boolean erstesDirekt = r.nextInt(4) != 0;
            if (erstesDirekt) {
                sb.append(wahl(DIREKT));
            } else {
                sb.append(":").append(wahl(ZEILENUMBRUECHE));
            }
            int anzahl = 2 + r.nextInt(3);
            for (int i = 0; i < anzahl; i++) {
                if (i > 0) {
                    sb.append(wahl(new String[] {", ", "; ", " / ", " und ", " oder ", ", sowie ", ",\n", "\n",
                        "\r\n  ", " und auch ", ", und zwar "}));
                }
                nummer(zahl(), i == 0 && erstesDirekt);
            }
            sb.append(wahl(SUFFIX));
        }

        /** Negativblock ohne erwarteten Fund; liefert {@code true}, wenn er mit einem Kontextwort endet. */
        boolean blockX() {
            switch (r.nextInt(6)) {
                case 0 -> {
                    // vier bis sechs Woerter zwischen Kontextwort und Nummer
                    sb.append(kontextwort()).append(wahl(DIREKT_NICHT_LEER));
                    int woerter = 4 + r.nextInt(3);
                    for (int i = 0; i < woerter; i++) {
                        sb.append(wahl(FUELLWOERTER)).append(' ');
                    }
                    sb.append(zahl().text());
                    return false;
                }
                case 1 -> {
                    // Leerzeile: zwei Umbrueche; CR direkt vor LF waere ein Umbruch (CR LF), daher dann Leerraum
                    String erster = wahl(ZEILENUMBRUECHE);
                    sb.append(kontextwort()).append(wahl(new String[] {"", ":", " :", ": "})).append(erster);
                    String[] dazwischen = erster.equals("\r") ? new String[] {" ", "\t"} : new String[] {"", " ", "\t"};
                    sb.append(wahl(dazwischen));
                    sb.append(wahl(ZEILENUMBRUECHE)).append(wahl(new String[] {"", " "}));
                    sb.append(zahl().text());
                    return false;
                }
                case 2 -> {
                    // Satzende zwischen Kontextwort und Nummer
                    sb.append("Die ").append(kontextwort()).append(" fehlt");
                    sb.append(wahl(new String[] {". ", "! ", "? "})).append("Vorgang ").append(zahl().text());
                    return false;
                }
                case 3 -> {
                    Zahl zahl = zahl();
                    sb.append(zahl.text());
                    String[] varianten = {" ((", " ([", " [(", " (der ", ", ", ": ", "\n", ". ", " siehe "};
                    String v = wahl(varianten);
                    String kw = kontextwort();
                    if (v.equals(", ") && !beginntMitWortDavor(kw)) {
                        // Grenze 12 geschlossen (D-5): genau ein Komma vor dem nachgestellten Kontextwort ist ein Fund
                        erwartet.add(new Erw(sb.length() - zahl.text().length(), zahl.text(), konfidenzKomma(zahl)));
                    }
                    sb.append(v).append(kw);
                    if (v.startsWith(" (") || v.startsWith(" [")) {
                        sb.append(')');
                    }
                    return true;
                }
                case 4 -> {
                    // Doppelgaenger und Kontextwoerter der anderen Art
                    String d = r.nextBoolean() ? wahl(DOPPELGAENGER) : wahl(art.fremdeKws);
                    sb.append(d).append(wahl(DIREKT_NICHT_LEER)).append(zahl().text());
                    return false;
                }
                default -> {
                    // Vorwort ohne gueltiges Nachsatzwort
                    sb.append(wahl(new String[] {"Arzt ", "Arzt\nNr ", "Arzt - Nr ", "Arzt--Nr ", "Arzt-Nrx ", "Nr. ",
                        "Nummer ", "Arzt:Nr ", "Arzt\r\nNummer "}));
                    sb.append(zahl().text());
                    return false;
                }
            }
        }

        // ---- Rauschen

        void rauschen() {
            int tokens = r.nextInt(5);
            for (int i = 0; i < tokens; i++) {
                int w = r.nextInt(10);
                if (w < 4) {
                    sb.append(wahl(RAUSCHWOERTER)).append(' ');
                } else if (w < 6) {
                    sb.append(rauschkette()).append(", ");
                } else if (w < 7) {
                    sb.append(wahl(DOPPELGAENGER)).append(' ');
                } else if (w < 8) {
                    sb.append(wahl(ZEICHEN)).append(' ');
                } else if (w < 9) {
                    sb.append(wahl(NEUTRAL)).append(' ');
                } else {
                    sb.append(wahl(RAUSCHWOERTER)).append(", ");
                }
            }
            // Satzende mit einem Wort ueber vier Buchstaben: leert offene Segmente (R-2), beendet den Kontext (R-4)
            sb.append("fertig. ");
        }

        String rauschkette() {
            String kette;
            if (r.nextInt(3) == 0) {
                // ein einziger Block mit 9 bis 14 Ziffern, beginnend mit 00
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
            pruefeRauschkette(kette);
            return kette;
        }
    }

    /**
     * Konstruktionsregel der Rauschketten: hoechstens acht Ziffern (nie ein Neunerfenster) oder ein einziger Block
     * mit 9 bis 14 Ziffern, der mit {@code 00} beginnt. Die Meldung nennt keine Ziffern.
     */
    static void pruefeRauschkette(String kette) {
        int ziffern = 0;
        boolean nurZiffern = true;
        for (int i = 0; i < kette.length(); i++) {
            char c = kette.charAt(i);
            if (c >= '0' && c <= '9') {
                ziffern++;
            } else {
                nurZiffern = false;
            }
        }
        boolean ok = ziffern <= 8 || (nurZiffern && ziffern <= 14 && kette.startsWith("00"));
        assertTrue(ok, "Rauschkette verletzt die Konstruktionsregel (Laenge " + ziffern + ")");
    }

    // ------------------------------------------------------------------ Pruefungen

    private static void pruefeInvarianten(Art art, String text, List<Finding> funde, String ort) {
        assertNotNull(funde, ort);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), ort + " Teilstring-Invariante");
            assertEquals(art.typ, f.type(), ort + " Typ");
            assertTrue(f.start() >= vorigesEnde, ort + " Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ Eigenschaft: konstruierte Bloecke

    @Test
    void eigenschaft_konstruierteBloecke_fundlisteIstGenauDieBloecke() {
        Random r = new Random(SEED);
        Statistik statistik = new Statistik();
        for (int iteration = 0; iteration < TEXTE; iteration++) {
            String ort = "Seed " + SEED + " Iteration " + iteration;
            Art art = iteration % 2 == 0 ? LANR_ART : IK_ART;
            Art andere = art == LANR_ART ? IK_ART : LANR_ART;
            Bauer bauer = new Bauer(r, art, statistik);
            bauer.text();
            String text = bauer.sb.toString();
            assertKeinGueltigesNeunerfensterOhne00(text, ort);

            List<Finding> ist = art.erkenner.detect(text);
            pruefeInvarianten(art, text, ist, ort);
            List<Erw> erwartet = bauer.erwartet;
            assertEquals(erwartet.size(), ist.size(), ort + " Anzahl Funde");
            for (int k = 0; k < erwartet.size(); k++) {
                Erw e = erwartet.get(k);
                Finding f = ist.get(k);
                assertEquals(e.start(), f.start(), ort + " Start " + k);
                assertEquals(e.start() + e.wert().length(), f.end(), ort + " Ende " + k);
                assertTrue(e.wert().equals(f.originalValue()), ort + " Wert " + k);
                assertEquals(e.konfidenz(), f.confidence(), ort + " Konfidenz " + k);
                assertEquals(ConfidenceLevel.of(e.konfidenz()), f.level(), ort + " Stufe " + k);
                assertEquals(art.quelleFuer(e.konfidenz()), f.source(), ort + " Quelle " + k);
            }
            statistik.funde += ist.size();

            // Determinismus (AK-26) und Invarianten der anderen Art auf demselben Text
            assertEquals(ist, art.erkenner.detect(text), ort + " Determinismus");
            List<Finding> fremd = andere.erkenner.detect(text);
            pruefeInvarianten(andere, text, fremd, ort + " (andere Art)");
            assertEquals(fremd, andere.erkenner.detect(text), ort + " Determinismus (andere Art)");
        }
        // Der Generator hat alle Blockarten, Stufen und Sonderformen mehrfach erzeugt (sonst waere der Test zu schwach)
        for (int b = 0; b < statistik.bloecke.length; b++) {
            assertTrue(statistik.bloecke[b] >= 100, "Blockart " + b + " zu selten: " + statistik.bloecke[b]);
        }
        assertTrue(statistik.sicher >= 100, "sicher " + statistik.sicher);
        assertTrue(statistik.wahrscheinlich >= 100, "wahrscheinlich " + statistik.wahrscheinlich);
        assertTrue(statistik.pruefziffer >= 100, "pruefziffer " + statistik.pruefziffer);
        assertTrue(statistik.laenge >= 100, "laenge " + statistik.laenge);
        assertTrue(statistik.invisibleKw >= 100, "unsichtbare Zeichen " + statistik.invisibleKw);
        assertTrue(statistik.trennerform >= 100, "Trennerformen " + statistik.trennerform);
        assertTrue(statistik.sondertrenner >= 30, "schmale Trenner " + statistik.sondertrenner);
        assertTrue(statistik.funde >= 1_000, "Funde insgesamt " + statistik.funde);
    }

    // ------------------------------------------------------------------ Eigenschaft: ohne Kontextwort kein Fund

    private static String ohneKontextwort(Random r, Statistik statistik) {
        Bauer b = new Bauer(r, r.nextBoolean() ? LANR_ART : IK_ART, statistik);
        StringBuilder sb = b.sb;
        int teile = 3 + r.nextInt(8);
        for (int i = 0; i < teile; i++) {
            switch (r.nextInt(6)) {
                case 0 -> sb.append(b.wahl(DOPPELGAENGER)).append(b.wahl(DIREKT_NICHT_LEER)).append(b.zahl().text());
                case 1 -> sb.append(b.zahl().text()).append(" (").append(b.wahl(DOPPELGAENGER)).append(')');
                case 2 -> sb.append(b.wahl(new String[] {"Arzt ", "Arzt\nNr ", "Arzt - Nr ", "Arzt-Nrx ", "Nr. ",
                    "Nummer ", "Arzt:Nr "})).append(b.zahl().text());
                case 3 -> {
                    b.rauschen();
                    sb.append(b.zahl().text());
                }
                case 4 -> sb.append(b.wahl(RAUSCHWOERTER)).append(' ').append(b.wahl(RAUSCHWOERTER)).append(": ")
                        .append(b.zahl().text());
                default -> sb.append(b.zahl().text()).append(b.wahl(new String[] {"\n", " ", "\t"}))
                        .append(b.wahl(DOPPELGAENGER));
            }
            sb.append(b.wahl(new String[] {", ", ". ", " und ", "\n", "; ", " / "}));
        }
        return sb.toString();
    }

    @Test
    void eigenschaft_texteOhneKontextwort_keinFundBeiBeidenErkennern() {
        Random r = new Random(SEED_OHNE_KONTEXT);
        Statistik statistik = new Statistik();
        for (int iteration = 0; iteration < TEXTE; iteration++) {
            String ort = "Seed " + SEED_OHNE_KONTEXT + " Iteration " + iteration;
            String text = ohneKontextwort(r, statistik);
            assertKeinGueltigesNeunerfensterOhne00(text, ort);
            for (Art art : new Art[] {LANR_ART, IK_ART}) {
                List<Finding> funde = art.erkenner.detect(text);
                pruefeInvarianten(art, text, funde, ort + " " + art.name);
                assertEquals(0, funde.size(), ort + " " + art.name + " Anzahl Funde");
            }
        }
        assertTrue(statistik.trennerform >= 100, "Trennerformen " + statistik.trennerform);
    }

    // ------------------------------------------------------------------ Eigenschaft: unsichtbare Zeichen (R-1)

    private static final char[] UNSICHTBAR_R1 = {SHY, ZWSP, ZWNJ, ZWJ, WORD_JOINER, BOM, (char) 0x200E, (char) 0xFE0F,
        (char) 0xFE00, (char) 0xFE0E};
    private static final String[] DIREKT_R1 = {" ", ": ", "=", "\t", " : ", ":", "  "};
    private static final String[] SUFFIX_R1 = {"", ",", " gesendet", ")", "\n", " bitte"};

    private static char zufaelligUnsichtbar(Random r) {
        return UNSICHTBAR_R1[r.nextInt(UNSICHTBAR_R1.length)];
    }

    /**
     * R-1 (Nachtrag 22.11.2026): Zufallswoerter ohne Kontextwort, mit unsichtbaren Zeichen an das Kontextwort
     * geklebt (das Kontextwort selbst zufaellig durch unsichtbare Zeichen geteilt, wahlweise mit angeklebtem
     * Nachsatzwort), danach Direktzeichen und Nummer: Die Fundliste ist genau der eine erwartete Fund mit direkter
     * Konfidenz. Meldungen nennen nur Seed und Iteration.
     */
    @Test
    void eigenschaft_unsichtbareZeichenAnsKontextwortGeklebt_genauDerErwarteteFundMitDirekterKonfidenz() {
        Random r = new Random(SEED_UNSICHTBAR);
        int angeklebtVorn = 0;
        int mitNachsatz = 0;
        int geteilt = 0;
        for (int iteration = 0; iteration < TEXTE; iteration++) {
            String ort = "Seed " + SEED_UNSICHTBAR + " Iteration " + iteration;
            Art art = iteration % 2 == 0 ? LANR_ART : IK_ART;
            StringBuilder sb = new StringBuilder();
            int woerter = r.nextInt(4);
            for (int w = 0; w < woerter; w++) {
                sb.append(RAUSCHWOERTER[r.nextInt(RAUSCHWOERTER.length)]);
                if (w < woerter - 1) {
                    sb.append(' ');
                }
            }
            if (woerter > 0) {
                if (r.nextInt(3) > 0) {
                    sb.append(zufaelligUnsichtbar(r));
                    if (r.nextBoolean()) {
                        sb.append(zufaelligUnsichtbar(r));
                    }
                    angeklebtVorn++;
                } else {
                    sb.append(' ');
                }
            }
            StringBuilder kw = new StringBuilder(art.kws[r.nextInt(art.kws.length)]);
            int einfuegungen = 1 + r.nextInt(3);
            for (int i = 0; i < einfuegungen; i++) {
                kw.insert(r.nextInt(kw.length() + 1), zufaelligUnsichtbar(r));
            }
            geteilt++;
            sb.append(kw);
            if (r.nextInt(3) == 0) {
                String[] nachsatz = {"Nr", "Nummer", "Nr.", "NR", "N" + zufaelligUnsichtbar(r) + "r"};
                sb.append(zufaelligUnsichtbar(r)).append(nachsatz[r.nextInt(nachsatz.length)]);
                mitNachsatz++;
            }
            sb.append(DIREKT_R1[r.nextInt(DIREKT_R1.length)]);
            int start = sb.length();
            String nummer = art.bilde(r);
            sb.append(nummer).append(SUFFIX_R1[r.nextInt(SUFFIX_R1.length)]);
            String text = sb.toString();
            assertKeinGueltigesNeunerfensterOhne00(text, ort);

            List<Finding> funde = art.erkenner.detect(text);
            pruefeInvarianten(art, text, funde, ort);
            assertEquals(1, funde.size(), ort + " Anzahl Funde");
            Finding f = funde.get(0);
            assertEquals(start, f.start(), ort + " Start");
            assertEquals(start + nummer.length(), f.end(), ort + " Ende");
            assertTrue(nummer.equals(f.originalValue()), ort + " Wert");
            assertEquals(art.gueltig(nummer) ? art.sicher : PZ_UNGUELTIG, f.confidence(), ort + " Konfidenz");
            assertEquals(art.quelleFuer(f.confidence()), f.source(), ort + " Quelle");
            assertEquals(ConfidenceLevel.of(f.confidence()), f.level(), ort + " Stufe");
            assertEquals(f.confidence(), art.erkenner.detect(text).get(0).confidence(), ort + " Determinismus");
        }
        assertTrue(angeklebtVorn >= 400, "angeklebte Woerter " + angeklebtVorn);
        assertTrue(mitNachsatz >= 200, "angeklebte Nachsatzwoerter " + mitNachsatz);
        assertEquals(TEXTE, geteilt, "geteilte Kontextwoerter");
    }
}
