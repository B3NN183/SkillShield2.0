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
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests zu Teil 2 (Grenze 14c) des Features kv-trenner-und-umbruch (Task T-8; AK-24 (a), (c), (d), (e),
 * Eigenschaftsteil von AK-21, AK-20, AK-22 und AK-28 mit Determinismus, Sortierung, Ueberlappungsfreiheit und
 * Teilstring-Invariante). Reine Tests, kein Produktcode. Jeder Text geht durch {@link KvTestHilfen#scan} (Waechter,
 * Differenz gegen beide eingefrorenen Staende, Fehlalarm-Pruefstand, Invarianten). Meldungen nennen nur Seed,
 * Iteration, Art und feste Kennungen, nie Text oder Ziffern.
 *
 * <p><b>(a)</b> Texte aus Bloecken "Kontextwort ab vier Buchstaben, jede Luecke einzeln aus D-2 (a) bis (d) gewuerfelt
 * (mindestens eine erweiterte), zufaellige Schreibweise, optional Schlusspunkt, unsichtbare Zeichen an Buchstaben und
 * zwischen den Zeichen einer Luecke, Direktzeichen, Fuellwoerter oder Nachsatzwort, Nummer". Die Bloecke sind durch
 * Trenner verbunden, die jede Folge beenden. Das Orakel ist die Konstruktion: Jeder Block ergibt genau einen Fund ueber
 * die Nummer (0,80 bei gueltiger, 0,50 bei ungueltiger, 0,40 bei abweichender Laenge; Quelle aus der Nummer). Die
 * unabhaengige Referenz {@link KvTestHilfen#weitKontextwoerterReferenz} muss je Block genau ein Kontextwort finden.
 *
 * <p><b>(c)</b> Rauschtexte ohne Kontextwort. Texte, in denen die Weit- oder die strenge Referenz fuer eine Art einen
 * Treffer findet, werden neu gezogen (Zaehler), laufen vorher aber ebenfalls durch scan. Erwartung an die
 * behaltenen Texte: kein Fund. <b>(d)</b> Gegenproben aus AK-13 (zwei Buchstaben), AK-14 (maximale Folge,
 * Teilgruppen, Wiederholung) und AK-17 (nicht zulaessige Trenner) aus denselben Bausteinen, ebenso behandelt:
 * kein Fund.
 *
 * <p><b>(e)</b> Gleichheit mit der Vergleichsform (AK-21, D-5): Der Generator baut die erweiterte Form und die
 * Vergleichsform (jede erweiterte Luecke samt darin liegenden unsichtbaren Zeichen durch ein Leerzeichen ersetzt)
 * gleichzeitig samt Positionsabbildung. Gleiche Zahl, gleicher Typ, abgebildeter Bereich, gleicher Wert und gleiche
 * Quelle; die Konfidenz ist gleich der der Vergleichsform, fuer Nummern hinter einem Block mit gesperrtem Kontextwort
 * das Minimum aus 0,80 und der Konfidenz der Vergleichsform.
 *
 * <p>Selbsttests: Die Pruefregeln sind als Funktionen ohne Assertion geschrieben und erkennen eingebaute Fehler
 * (anderer Wert, verschobener Bereich, fehlender oder zusaetzlicher Fund, falsche Konfidenz, Teil 2 fehlt).
 * Mindestzahlen liegen mindestens 30 % unter den gemessenen Istwerten des festen Seeds.
 */
class KvTrennerEigenschaftTest {

    private static final long SEED_A = 20261401L;
    private static final long SEED_C = 20261402L;
    private static final long SEED_D = 20261403L;
    private static final long SEED_E = 20261404L;
    private static final long SEED_SELBST = 20261405L;
    private static final int TEXTE = 3_000;
    private static final double SPERR = 0.80;

    private static final String[] WOERTER_LANR = {"LANR", "LANRNR", "ARZTNUMMER", "ARZTNR"};
    private static final String[] WOERTER_IK = {"IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"};
    private static final String[] ALLE_WOERTER = {"LANR", "LANRNR", "ARZTNUMMER", "ARZTNR", "IKNR", "IKNUMMER",
        "INSTITUTIONSKENNZEICHEN", "IK"};
    private static final int[] ERWEITERT = {LUECKE_LEERRAUM, LUECKE_PUNKT, LUECKE_TAB};
    private static final String[] WS4 = {" ", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP)};
    private static final String[] STRENG = {" ", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), ".", "-", String.valueOf(GESCH_BINDESTRICH), String.valueOf(HALBGEVIERT)};
    /** Die Zeichen hinter dem letzten Buchstaben (ohne Schlusspunkt). */
    private static final String[] NACH_WORT = {" ", "  ", "   ", NBSP + " ", "\t", ": ", ":", ":\t", ":\n", ":\r\n"};
    private static final String[] FUELLWOERTER = {"des", "der", "die"};
    private static final String[] NACHSATZ = {" Nr. ", " Nr ", "-Nr. ", "-Nummer ", " Nummer "};
    private static final String[] NACHFELD = {"", "", ".", ")", " Ende", ";"};
    private static final String[] BLOCK_TRENNER = {", ", "; ", "\n", "\r\n", "\n\n", " und ", " Praxis ", ", Praxis "};
    private static final String[] VORFELD = {"", "", "Rechnung ", "Praxis ", "Bericht: "};
    private static final String[] GRUPPEN_TRENNER = {" ", "-", ".", "/"};
    private static final String[] RAUSCH = {"Nr", "Nummer", "Praxis", "der", "die", "und", "Rechnung", "Betrag",
        "Herr", "Termin"};
    private static final String[] INITIALEN = {"I. K.", "Dr. M. A. Beispiel", "J. R. R.", "Prof. I. K. Mustermann",
        "A. B.", "Dr. L. A.", "M. A.", "z. B."};
    private static final String[] SATZZEICHEN = {".", ",", ":", ";", "-", "/", "(", ")", "!", "_"};
    private static final String[] NICHT_LUECKEN = {"    ", "     ", "" + ' ' + NBSP + DUENN + SCHMAL_NBSP,
        "\t\t", "\t ", "\n", "\r\n", ", ", "; ", "/", "_", ": ", " .", ".. ", "..", " - ", " " + HALBGEVIERT + " ",
        ".\t"};
    private static final String[] UMBRUECHE = {"\n", "\r\n", "\r", String.valueOf(NEL), "\n\n", "\r\n\r\n"};

    private static final class Zaehler {
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

    private static <T> T wahl(Random r, T[] werte) {
        return werte[r.nextInt(werte.length)];
    }

    private static String ort(long seed, int iteration, Art a) {
        return "Seed " + seed + " Iteration " + iteration + " " + a.name;
    }

    // ------------------------------------------------------------------ Text in zwei Formen

    /**
     * Ein Text in erweiterter Form ({@code ext}) und Vergleichsform ({@code cmp}) mit der Abbildung der Positionen
     * der Vergleichsform auf die erweiterte Form. {@code via} enthaelt die Startpositionen (in der Vergleichsform)
     * der Nummern, die nur ein Block mit gesperrtem Kontextwort erschliesst.
     */
    private static final class Text {
        final StringBuilder ext = new StringBuilder();
        final StringBuilder cmp = new StringBuilder();
        /** Je Stueck {Anfang cmp, Anfang ext, Laenge cmp, Laenge ext}. */
        final List<int[]> stuecke = new ArrayList<>();
        final Set<Integer> via = new HashSet<>();

        /** Dieselben Zeichen in beiden Formen. */
        void gleich(String s) {
            if (s.isEmpty()) {
                return;
            }
            stuecke.add(new int[] {cmp.length(), ext.length(), s.length(), s.length()});
            ext.append(s);
            cmp.append(s);
        }

        /** Eine erweiterte Luecke samt unsichtbaren Zeichen; die Vergleichsform hat ein Leerzeichen. */
        void luecke(String erweitert) {
            stuecke.add(new int[] {cmp.length(), ext.length(), 1, erweitert.length()});
            ext.append(erweitert);
            cmp.append(' ');
        }

        /** Position in der erweiterten Form zu einer Position der Vergleichsform; -1 in einer ersetzten Luecke. */
        int extPos(int cmpPos) {
            for (int[] s : stuecke) {
                if (cmpPos >= s[0] && cmpPos < s[0] + s[2]) {
                    return s[2] == s[3] ? s[1] + cmpPos - s[0] : -1;
                }
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------ Bausteine

    private static String uzFolge(Random r) {
        StringBuilder sb = new StringBuilder();
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            sb.append(UZ_AK1[r.nextInt(UZ_AK1.length)]);
        }
        return sb.toString();
    }

    private static int zufallsArt(Random r) {
        int w = r.nextInt(100);
        return w < 30 ? LUECKE_STRENG : w < 55 ? LUECKE_LEERRAUM : w < 85 ? LUECKE_PUNKT : LUECKE_TAB;
    }

    private static String ws4(Random r) {
        return wahl(r, WS4);
    }

    private static String lueckeVon(Random r, int art) {
        StringBuilder sb = new StringBuilder();
        if (art == LUECKE_STRENG) {
            return wahl(r, STRENG);
        }
        if (art == LUECKE_TAB) {
            return "\t";
        }
        if (art == LUECKE_LEERRAUM) {
            int n = 2 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                sb.append(ws4(r));
            }
            return sb.toString();
        }
        sb.append('.');
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            sb.append(ws4(r));
        }
        return sb.toString();
    }

    private static String lueckenName(int art) {
        return art == LUECKE_STRENG ? "streng" : art == LUECKE_LEERRAUM ? "leerraum"
                : art == LUECKE_PUNKT ? "punkt" : "tab";
    }

    private static String schreibweise(Random r, String wort) {
        int modus = r.nextInt(4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            char c = wort.charAt(i);
            boolean gross = modus == 0 || modus == 2 && i == 0 || modus == 3 && r.nextBoolean();
            sb.append(gross ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** Ein Kontextwort ab vier Buchstaben der Art (gleich verteilt ueber die Woerter der Art). */
    private static String weitWortVon(Random r, Art a) {
        return wahl(r, a.lanr ? WOERTER_LANR : WOERTER_IK);
    }

    /**
     * Haengt ein Kontextwort in beiden Formen an. Jede Luecke wird einzeln gewuerfelt, mindestens eine ist erweitert.
     * {@code uzFrei}: unsichtbare Zeichen auch an Buchstaben neben einfachen Luecken (nur die erweiterte Form wird
     * dann bewertet); sonst nur vor dem ersten und hinter dem letzten Buchstaben sowie in erweiterten Luecken.
     * {@code teilEreignis}: Plan Kap. 3.2.5, die Luecke hinter dem strengen Teilwort ({@code lanr} in {@code lanrnr},
     * {@code ik} in {@code iknr}) ist erweitert, alle davor sind einfache Leerzeichen.
     */
    private static void weitWort(Random r, Text t, String wort, boolean uzFrei, boolean teilEreignis, Zaehler z) {
        int n = wort.length();
        int[] arten = new int[n - 1];
        boolean erweitert = false;
        for (int i = 0; i < n - 1; i++) {
            arten[i] = zufallsArt(r);
            erweitert |= arten[i] != LUECKE_STRENG;
        }
        if (teilEreignis) {
            int teil = wort.equals("LANRNR") ? 3 : 1;
            for (int i = 0; i < teil; i++) {
                arten[i] = LUECKE_STRENG;
            }
            arten[teil] = r.nextBoolean() ? LUECKE_PUNKT : LUECKE_LEERRAUM;
            erweitert = true;
            z.zaehle("teilEreignis");
        }
        if (!erweitert) {
            arten[r.nextInt(n - 1)] = ERWEITERT[r.nextInt(ERWEITERT.length)];
        }
        String s = schreibweise(r, wort);
        z.zaehle("laenge." + n);
        String vor = r.nextInt(8) == 0 ? uzFolge(r) : "";
        if (!vor.isEmpty()) {
            z.zaehle("uz.vor");
        }
        t.gleich(vor + s.charAt(0));
        for (int i = 0; i < n - 1; i++) {
            z.zaehle("luecke." + lueckenName(arten[i]));
            String g = lueckeVon(r, arten[i]);
            String uzHinterBuchstabe = "";
            String uzVorBuchstabe = "";
            if (g.length() >= 2 && r.nextInt(4) == 0) {
                int p = 1 + r.nextInt(g.length() - 1);
                g = g.substring(0, p) + uzFolge(r) + g.substring(p);
                z.zaehle("uz.luecke");
            }
            if (arten[i] != LUECKE_STRENG || uzFrei) {
                uzHinterBuchstabe = r.nextInt(8) == 0 ? uzFolge(r) : "";
                uzVorBuchstabe = r.nextInt(8) == 0 ? uzFolge(r) : "";
                z.zaehle(uzHinterBuchstabe.isEmpty() && uzVorBuchstabe.isEmpty() ? "uz.keinAnBuchstabe"
                        : "uz.anBuchstabe");
            }
            if (arten[i] == LUECKE_STRENG) {
                t.gleich(uzHinterBuchstabe + g + uzVorBuchstabe);
            } else {
                t.luecke(uzHinterBuchstabe + g + uzVorBuchstabe);
            }
            t.gleich(String.valueOf(s.charAt(i + 1)));
        }
        String ende = r.nextInt(8) == 0 ? uzFolge(r) : "";
        if (!ende.isEmpty()) {
            z.zaehle("uz.nach");
        }
        t.gleich(ende);
    }

    /** Schlusspunkt, Direktzeichen, Fuellwoerter oder Nachsatzwort hinter dem letzten Buchstaben (beide Formen). */
    private static void schluss(Random r, Text t, Zaehler z, int maxFuell) {
        StringBuilder sb = new StringBuilder();
        if (r.nextBoolean()) {
            sb.append('.');
            z.zaehle("schlusspunkt");
        }
        int w = r.nextInt(100);
        if (w < 55) {
            sb.append(wahl(r, NACH_WORT));
            z.zaehle("tail.direkt");
        } else if (w < 78) {
            sb.append(' ');
            int k = 1 + r.nextInt(maxFuell);
            for (int i = 0; i < k; i++) {
                sb.append(wahl(r, FUELLWOERTER)).append(' ');
            }
            z.zaehle("tail.fuell");
        } else {
            sb.append(wahl(r, NACHSATZ));
            z.zaehle("tail.nachsatz");
        }
        t.gleich(sb.toString());
    }

    private record Nummer(String text, double konf) {
    }

    private static Nummer nummer(Random r, Art a, Zaehler z) {
        int w = r.nextInt(100);
        if (w < 60) {
            String neun = a.bilde(r);
            if (r.nextInt(5) == 0) {
                z.zaehle("nummer.gruppiert");
                return new Nummer(gruppiert(neun, wahl(r, GRUPPEN_TRENNER)), 1.0);
            }
            z.zaehle("nummer.gueltig");
            return new Nummer(neun, 1.0);
        }
        if (w < 78) {
            String neun;
            do {
                StringBuilder sb = new StringBuilder("00");
                for (int i = 0; i < 7; i++) {
                    sb.append((char) ('0' + r.nextInt(10)));
                }
                neun = sb.toString();
            } while (a.gueltig(neun));
            z.zaehle("nummer.ungueltig");
            return new Nummer(neun, PZ_UNGUELTIG);
        }
        z.zaehle("nummer.laenge");
        String[] laengen = {LAENGE_6, LAENGE_7, LAENGE_8, LAENGE_10, LAENGE_12};
        return new Nummer(wahl(r, laengen), LAENGE);
    }

    /** Haengt die Nummer an (beide Formen) und liefert ihre Position in der erweiterten Form. */
    private static int nummerAnhaengen(Text t, Nummer n, boolean viaGesperrt) {
        int start = t.ext.length();
        if (viaGesperrt) {
            t.via.add(t.cmp.length());
        }
        t.gleich(n.text());
        return start;
    }

    // ------------------------------------------------------------------ AK-24 (a)

    private record Erwartet(int start, int ende, String wert, double konf, String quelle) {
    }

    private static Text baueA(Random r, Art a, Zaehler z, List<Erwartet> erwartet, int[] bloecke) {
        Text t = new Text();
        t.gleich(wahl(r, VORFELD));
        int n = 1 + r.nextInt(3);
        for (int b = 0; b < n; b++) {
            if (b > 0) {
                t.gleich(wahl(r, BLOCK_TRENNER));
            }
            weitWort(r, t, weitWortVon(r, a), true, false, z);
            schluss(r, t, z, 3);
            Nummer nr = nummer(r, a, z);
            int start = nummerAnhaengen(t, nr, true);
            double konf = Math.min(SPERR, nr.konf());
            erwartet.add(new Erwartet(start, start + nr.text().length(), nr.text(), konf,
                    a.quelleNachNummer(nr.text())));
            z.zaehle(konf == SPERR ? "stufe.080" : konf == PZ_UNGUELTIG ? "stufe.050" : "stufe.040");
            t.gleich(wahl(r, NACHFELD));
        }
        bloecke[0] = n;
        z.zaehle("bloecke." + n);
        return t;
    }

    /** Die Pruefregel von (a) ohne Assertion; "" wenn der Befund genau der Erwartung entspricht. */
    private static String abweichungA(Art a, List<Finding> ist, List<Erwartet> erwartet) {
        if (ist.size() != erwartet.size()) {
            return "Anzahl";
        }
        for (int i = 0; i < ist.size(); i++) {
            Finding f = ist.get(i);
            Erwartet e = erwartet.get(i);
            if (f.start() != e.start() || f.end() != e.ende()) {
                return "Bereich";
            }
            if (!f.originalValue().equals(e.wert())) {
                return "Wert";
            }
            if (f.type() != a.typ) {
                return "Typ";
            }
            if (f.confidence() != e.konf()) {
                return "Konfidenz";
            }
            if (f.level() != ConfidenceLevel.of(e.konf())) {
                return "Stufe";
            }
            if (!f.source().equals(e.quelle())) {
                return "Quelle";
            }
        }
        return "";
    }

    @Test
    void ak24a_kontextwortAbVierBuchstabenMitZufaelligenLuecken_genauDieseFunde() {
        Random r = new Random(SEED_A);
        Zaehler z = new Zaehler();
        for (int it = 0; it < TEXTE; it++) {
            Art a = it % 2 == 0 ? LANR_ART : IK_ART;
            List<Erwartet> erwartet = new ArrayList<>();
            int[] bloecke = new int[1];
            String text = baueA(r, a, z, erwartet, bloecke).ext.toString();
            String ort = ort(SEED_A, it, a);
            List<Finding> ist = scan(a, text);
            assertEquals("", abweichungA(a, ist, erwartet), ort + " Abweichung von der Konstruktion");
            assertTrue(ist.equals(a.erkenner.detect(text)), ort + " Determinismus");
            assertEquals(bloecke[0], weitKontextwoerterReferenz(text, a).size(), ort + " Referenz je Block");
            assertEquals(bloecke[0], erwartet.size(), ort + " Zahl der Bloecke");
            pruefstand(a, text, ist, z);
            z.zaehle("texte");
        }
        mindestzahlenA(z);
    }

    /**
     * Mindestzahlen von (a), je hoechstens 70 % des gemessenen Istwerts des festen Seeds (3 000 Texte, Zaehlung je
     * Block, Luecke oder Text). Istwerte: Texte mit 1 / 2 / 3 Bloecken 1021 / 1001 / 978; Kontextwoerter mit 4 / 6 / 8
     * / 10 / 23 Buchstaben 1741 / 1492 / 983 / 757 / 984; Luecken streng / Leerraum / Punkt / Tab 14443 / 11994 /
     * 14434 / 7154; Nummern gueltig / gruppiert / ungueltig / abweichende Laenge 2801 / 759 / 1076 / 1321; Stufen
     * 0,80 / 0,50 / 0,40 3560 / 1076 / 1321; Schlusspunkt 2975; Direktzeichen / Fuellwoerter / Nachsatzwort 3284 /
     * 1367 / 1306; unsichtbare Zeichen an Buchstaben / in einer Luecke / vor dem Wort / hinter dem Wort 11078 / 6602 /
     * 745 / 759. Der Zaehler der unerklaerten Fehlalarme des Pruefstands ist strikt 0.
     */
    private static void mindestzahlenA(Zaehler z) {
        z.mindestens("texte", TEXTE);
        z.mindestens("bloecke.1", 700);
        z.mindestens("bloecke.2", 700);
        z.mindestens("bloecke.3", 680);
        z.mindestens("laenge.4", 1200);
        z.mindestens("laenge.6", 1000);
        z.mindestens("laenge.8", 680);
        z.mindestens("laenge.10", 520);
        z.mindestens("laenge.23", 680);
        z.mindestens("luecke.streng", 10000);
        z.mindestens("luecke.leerraum", 8000);
        z.mindestens("luecke.punkt", 10000);
        z.mindestens("luecke.tab", 5000);
        z.mindestens("nummer.gueltig", 1900);
        z.mindestens("nummer.gruppiert", 500);
        z.mindestens("nummer.ungueltig", 740);
        z.mindestens("nummer.laenge", 900);
        z.mindestens("stufe.080", 2400);
        z.mindestens("stufe.050", 740);
        z.mindestens("stufe.040", 900);
        z.mindestens("schlusspunkt", 2000);
        z.mindestens("tail.direkt", 2200);
        z.mindestens("tail.fuell", 950);
        z.mindestens("tail.nachsatz", 900);
        z.mindestens("uz.anBuchstabe", 7700);
        z.mindestens("uz.luecke", 4600);
        z.mindestens("uz.vor", 510);
        z.mindestens("uz.nach", 520);
        assertEquals(0, z.get("fehlalarm.unerklaert"), "unerklaerte Fehlalarme des Pruefstands");
    }

    /** Der Fehlalarm-Pruefstand je Text als Zaehler: unerklaerte Zusatzfunde (strikt 0) und erklaerte. */
    private static void pruefstand(Art a, String text, List<Finding> neu, Zaehler z) {
        int[] zaehlung = fehlalarmZaehlung(a, text, neu, a.standLuecken.detect(text));
        for (int i = 0; i < zaehlung[0]; i++) {
            z.zaehle("fehlalarm.unerklaert");
        }
        for (int i = 0; i < zaehlung[1]; i++) {
            z.zaehle("fehlalarm.erklaert");
        }
    }

    // ------------------------------------------------------------------ Rauschtexte (c), Gegenproben (d)

    private static String einzelbuchstaben(Random r, Zaehler z) {
        int n = 1 + r.nextInt(7);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(r.nextInt(10) < 7 ? lueckeVon(r, zufallsArt(r)) : wahl(r, NICHT_LUECKEN));
            }
            char c = r.nextBoolean() ? "LANRIKZTUME".charAt(r.nextInt(11)) : (char) ('A' + r.nextInt(26));
            sb.append(r.nextBoolean() ? c : Character.toLowerCase(c));
            if (r.nextInt(12) == 0) {
                sb.append(uzFolge(r));
            }
        }
        z.zaehle("einzelbuchstaben");
        return sb.toString();
    }

    /** Unabhaengige Pruefung der Bausteine: Ist der Text (ohne Beachtung der Gross-/Kleinschreibung) ein Wort? */
    private static boolean istWort(String text) {
        for (String w : ALLE_WOERTER) {
            if (w.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    /** Die Mutationsarten von {@link #mutiert} in der Reihenfolge der Faelle. */
    private static final String[] MUTATIONEN = {"vorn", "hinten", "mitte", "loeschen", "tausch", "doppelt",
        "ersetzt"};

    /** Ein Beinahe-Kontextwort: ein Buchstabe zu viel, fehlt, vertauscht oder ersetzt, oder das Wort doppelt. */
    private static String mutiert(Random r, String wort, Zaehler z) {
        StringBuilder sb = new StringBuilder(wort);
        int pos = r.nextInt(wort.length());
        int mutation = r.nextInt(7);
        z.zaehle("mutation." + MUTATIONEN[mutation]);
        switch (mutation) {
            case 0 -> sb.insert(0, "AXZL".charAt(r.nextInt(4)));
            case 1 -> sb.append("XAZM".charAt(r.nextInt(4)));
            case 2 -> sb.insert(1 + r.nextInt(wort.length() - 1), "XAZ".charAt(r.nextInt(3)));
            case 3 -> sb.deleteCharAt(pos);
            case 4 -> {
                int p = r.nextInt(wort.length() - 1);
                char c = sb.charAt(p);
                sb.setCharAt(p, sb.charAt(p + 1));
                sb.setCharAt(p + 1, c);
            }
            case 5 -> sb.append(wort);
            default -> sb.setCharAt(pos, "XZQ".charAt(r.nextInt(3)));
        }
        String m = sb.toString();
        return istWort(m) ? wort + "X" : m;
    }

    /** Die Buchstaben des Textes als Einzelbuchstaben, durch Luecken verbunden ({@code modus}: 0 Luecken, 1 keine). */
    private static String verbunden(Random r, String buchstaben, int modus) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buchstaben.length(); i++) {
            if (i > 0) {
                int art = modus == 0 ? 0 : modus == 1 ? 1 : r.nextInt(2);
                sb.append(art == 0 ? lueckeVon(r, zufallsArt(r)) : wahl(r, NICHT_LUECKEN));
            }
            char c = buchstaben.charAt(i);
            sb.append(r.nextBoolean() ? c : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** Teilgruppen (Grenze 14a): das Wort in Gruppen von ein oder zwei Buchstaben, mindestens eine mit zwei. */
    private static String teilgruppen(Random r, String wort) {
        List<String> gruppen = new ArrayList<>();
        int i = 0;
        boolean zwei = false;
        while (i < wort.length()) {
            int len = i + 2 <= wort.length() && (r.nextBoolean() || !zwei && i + 2 >= wort.length()) ? 2 : 1;
            if (len == 2 && wort.substring(i, i + 2).equalsIgnoreCase("IK")) {
                len = 1;
            }
            zwei |= len == 2;
            gruppen.add(wort.substring(i, i + len));
            i += len;
        }
        if (!zwei) {
            return wort.charAt(0) + lueckeVon(r, zufallsArt(r)) + wort.substring(1, 3) + lueckeVon(r, zufallsArt(r))
                    + verbunden(r, wort.substring(3), 0);
        }
        StringBuilder sb = new StringBuilder();
        for (int g = 0; g < gruppen.size(); g++) {
            if (g > 0) {
                sb.append(lueckeVon(r, zufallsArt(r)));
            }
            sb.append(r.nextBoolean() ? gruppen.get(g) : gruppen.get(g).toLowerCase());
        }
        return sb.toString();
    }

    private static boolean trifftKontextwort(String text) {
        for (Art a : ARTEN) {
            if (!weitKontextwoerterReferenz(text, a).isEmpty() || !sperrKontextwoerterReferenz(text, a).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auch ein verworfener (neu gezogener) Text geht fuer beide Arten durch {@link KvTestHilfen#scan}, also durch
     * Waechter, Differenz, Fehlalarm-Pruefstand und Invarianten; eine Erwartung an die Funde gibt es nicht.
     */
    private static void verworfenPruefen(String text) {
        for (Art a : ARTEN) {
            scan(a, text);
        }
    }

    private static boolean folgeAbVierErweitert(String text) {
        for (WeitFolge f : weitFolgenReferenz(text)) {
            if (f.buchstaben() >= 4 && f.erweitert()) {
                return true;
            }
        }
        return false;
    }

    private static void uebernehmen(Zaehler ziel, Zaehler quelle) {
        for (Map.Entry<String, Integer> e : quelle.z.entrySet()) {
            ziel.z.merge(e.getKey(), e.getValue(), Integer::sum);
        }
    }

    /** Ein Rauschtext ohne Kontextwort (Versuch; der Aufrufer prueft die Referenzen). */
    private static String rauschVersuch(Random r, Zaehler z) {
        StringBuilder sb = new StringBuilder();
        String[] joiner = {"", "\t", "  ", "   "};
        int tokens = 4 + r.nextInt(11);
        for (int i = 0; i < tokens; i++) {
            if (i > 0) {
                sb.append(r.nextInt(100) < 80 ? " " : wahl(r, joiner));
            }
            int w = r.nextInt(100);
            if (w < 18) {
                sb.append(wahl(r, RAUSCH));
            } else if (w < 25) {
                sb.append(wahl(r, INITIALEN));
                z.zaehle("initialen");
            } else if (w < 45) {
                sb.append(einzelbuchstaben(r, z));
            } else if (w < 58) {
                sb.append(nummer(r, r.nextBoolean() ? LANR_ART : IK_ART, z).text()).append(',');
                z.zaehle("nummer");
            } else if (w < 66) {
                String wort = wahl(r, ALLE_WOERTER);
                sb.append(verbunden(r, mutiert(r, wort, z), 0));
                z.zaehle("beinahe");
            } else if (w < 72) {
                String zwei = (r.nextBoolean() ? "I" : "i") + lueckeVon(r, ERWEITERT[r.nextInt(3)])
                        + (r.nextBoolean() ? "K" : "k") + (r.nextBoolean() ? "." : "");
                sb.append(zwei).append(' ').append(nummer(r, IK_ART, z).text()).append(',');
                z.zaehle("ikZwei");
            } else if (w < 78) {
                sb.append(verbunden(r, wahl(r, ALLE_WOERTER), r.nextInt(3)));
                z.zaehle("kandidat");
            } else if (w < 84) {
                sb.append(wahl(r, UMBRUECHE));
            } else if (w < 89) {
                if (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\r') {
                    sb.append(uzFolge(r));
                    z.zaehle("uz");
                }
            } else if (w < 95) {
                sb.append(wahl(r, SATZZEICHEN));
            } else {
                sb.append(wahl(r, new String[] {"\t", "  ", "   ", "\t\t"}));
            }
        }
        return sb.toString();
    }

    private static String rauschText(Random r, Zaehler z) {
        while (true) {
            Zaehler lokal = new Zaehler();
            String text = rauschVersuch(r, lokal);
            if (trifftKontextwort(text)) {
                verworfenPruefen(text);
                z.zaehle("neuGezogen");
                continue;
            }
            uebernehmen(z, lokal);
            z.zaehle("texte");
            if (folgeAbVierErweitert(text)) {
                z.zaehle("folgeAb4Erweitert");
            }
            if (text.indexOf('\t') >= 0) {
                z.zaehle("mitTab");
            }
            if (text.contains("  ")) {
                z.zaehle("mitMehrfachLeerzeichen");
            }
            return text;
        }
    }

    private static final String[] IK_NACH = {" ", ": ", "-Nr. ", " Mustermann ", " Mustermann, Rechnung "};
    private static final String[] IK_VOR = {"", "", "Dr. ", "Prof. "};

    /** Die Art der Gegenprobe: 0 AK-13, 1 AK-14 (Mutation), 2 AK-14 (Teilgruppen), 3 AK-17. */
    private static final String[] GEGENPROBE_ART = {"ak13", "ak14.mutiert", "ak14.teilgruppen", "ak17"};

    private static String gegenprobeVersuch(Random r, Zaehler z, Art a, int[] artOut) {
        int w = r.nextInt(100);
        int kind = w < 25 ? 0 : w < 50 ? 1 : w < 62 ? 2 : 3;
        artOut[0] = kind;
        String folge;
        String tail;
        Art nummerArt = a;
        if (kind == 0) {
            nummerArt = IK_ART;
            String zwei = (r.nextBoolean() ? "I" : "i") + lueckeVon(r, ERWEITERT[r.nextInt(3)])
                    + (r.nextBoolean() ? "K" : "k");
            folge = wahl(r, IK_VOR) + zwei;
            tail = (r.nextBoolean() ? "." : "") + wahl(r, IK_NACH);
        } else {
            String wort = weitWortVon(r, a);
            if (kind == 1) {
                folge = verbunden(r, mutiert(r, wort, z), r.nextInt(3));
            } else if (kind == 2) {
                folge = teilgruppen(r, wort);
            } else {
                folge = verbunden(r, wort, r.nextBoolean() ? 1 : 2);
            }
            tail = (r.nextBoolean() ? "." : "") + wahl(r, NACH_WORT);
        }
        Nummer nr = nummer(r, nummerArt, z);
        StringBuilder sb = new StringBuilder(wahl(r, VORFELD));
        if (r.nextInt(5) == 0) {
            String sep = wahl(r, new String[] {" (", ", ", " "});
            sb.append(nr.text()).append(sep).append(folge).append(sep.endsWith("(") ? ")" : "");
            z.zaehle("nachgestellt");
        } else {
            sb.append(folge).append(tail).append(nr.text());
        }
        sb.append(wahl(r, NACHFELD));
        return sb.toString();
    }

    private static String gegenprobe(Random r, Zaehler z, Art a, int[] artOut) {
        while (true) {
            Zaehler lokal = new Zaehler();
            String text = gegenprobeVersuch(r, lokal, a, artOut);
            if (trifftKontextwort(text)) {
                verworfenPruefen(text);
                z.zaehle("neuGezogen");
                z.zaehle("neuGezogen." + GEGENPROBE_ART[artOut[0]]);
                continue;
            }
            uebernehmen(z, lokal);
            z.zaehle("texte");
            z.zaehle(GEGENPROBE_ART[artOut[0]]);
            return text;
        }
    }

    // ------------------------------------------------------------------ Selbsttests der Pruefregeln

    @Test
    void selbsttest_abbildungDerPositionen_ersetzteLueckeUndGleicheStuecke() {
        Text t = new Text();
        t.gleich("ab");
        t.luecke(".  " + ZWSP);
        t.gleich("cd");
        assertEquals("ab cd", t.cmp.toString());
        assertEquals("ab.  " + ZWSP + "cd", t.ext.toString());
        assertEquals(0, t.extPos(0));
        assertEquals(1, t.extPos(1));
        assertEquals(-1, t.extPos(2), "die ersetzte Luecke hat keine Einzelposition");
        assertEquals(6, t.extPos(3));
        assertEquals(7, t.extPos(4));
        assertEquals(-1, t.extPos(5));
    }

    @Test
    void selbsttest_a_pruefregelErkenntEingebauteFehler() {
        Art a = LANR_ART;
        String vor = "L. A. N. R. ";
        List<Finding> ist = scan(a, vor + a.gueltig);
        int s = vor.length();
        int e = s + a.gueltig.length();
        assertEquals("", abweichungA(a, ist, List.of(new Erwartet(s, e, a.gueltig, SPERR, a.quelle))));
        assertEquals("Anzahl", abweichungA(a, List.of(), List.of(new Erwartet(s, e, a.gueltig, SPERR, a.quelle))));
        assertEquals("Anzahl", abweichungA(a, ist, List.of()));
        assertEquals("Bereich", abweichungA(a, ist, List.of(new Erwartet(s + 1, e + 1, a.gueltig, SPERR, a.quelle))));
        assertEquals("Wert", abweichungA(a, ist, List.of(new Erwartet(s, e, a.ungueltig, SPERR, a.quelle))));
        assertEquals("Konfidenz", abweichungA(a, ist, List.of(new Erwartet(s, e, a.gueltig, 0.95, a.quelle))));
        assertEquals("Konfidenz", abweichungA(a, ist, List.of(new Erwartet(s, e, a.gueltig, PZ_UNGUELTIG, a.quelle))));
        assertEquals("Quelle",
                abweichungA(a, ist, List.of(new Erwartet(s, e, a.gueltig, SPERR, a.quellePruefziffer()))));
        assertEquals("Typ", abweichungA(IK_ART, ist, List.of(new Erwartet(s, e, a.gueltig, SPERR, a.quelle))));
    }

    @Test
    void selbsttest_a_fehlendesTeil2WirdErkannt_dieKopieAufDerVergleichsformStimmtMitDemOrakelUeberein() {
        Random r = new Random(SEED_SELBST);
        Zaehler z = new Zaehler();
        int ohneTeil2 = 0;
        int abweichungKuenstlichRichtig = 0;
        int abweichungKonfidenz = 0;
        int n = 600;
        for (int it = 0; it < n; it++) {
            Art a = it % 2 == 0 ? LANR_ART : IK_ART;
            List<Erwartet> erwartet = new ArrayList<>();
            String text = baueA(r, a, z, erwartet, new int[1]).ext.toString();
            if (!abweichungA(a, a.standLuecken.detect(text), erwartet).isEmpty()) {
                ohneTeil2++;
            }
            List<Finding> richtig = kuenstlicherRichtigerNeuErkenner(a.standLuecken, a).detect(text);
            String abw = abweichungA(a, richtig, erwartet);
            if (!abw.isEmpty()) {
                abweichungKuenstlichRichtig++;
            }
            if (abw.equals("Konfidenz")) {
                abweichungKonfidenz++;
            }
        }
        // Istwert: 599 von 600 Texten
        assertTrue(ohneTeil2 >= 400, "Die Kopie ohne Teil 2 muss auf den Texten der Eigenschaft (a) abweichen");
        assertEquals(0, abweichungKuenstlichRichtig, "Die Kopie auf der Vergleichsform muss das Orakel erfuellen");
        assertEquals(0, abweichungKonfidenz);
    }

    @Test
    void selbsttest_e_pruefregelErkenntEingebauteFehler() {
        Random r = new Random(SEED_SELBST);
        Zaehler z = new Zaehler();
        int n = 600;
        int ohneTeil2 = 0;
        int konfidenzHoeher = 0;
        int konfidenzNiedriger = 0;
        int ohneLetztenFund = 0;
        int verschoben = 0;
        int mitFund = 0;
        for (int it = 0; it < n; it++) {
            Art a = it % 2 == 0 ? LANR_ART : IK_ART;
            Text t = baueE(r, a, z, new int[1]);
            String ext = t.ext.toString();
            String cmp = t.cmp.toString();
            List<Finding> fe = a.erkenner.detect(ext);
            List<Finding> fc = a.erkenner.detect(cmp);
            assertEquals("", abweichungE(t, fe, fc), "Die Pruefregel muss den Befund des Produkts annehmen");
            if (!abweichungE(t, a.standLuecken.detect(ext), fc).isEmpty()) {
                ohneTeil2++;
            }
            Recognizer hoeher = kuenstlicherAbweichenderNeuErkenner(a.erkenner, 0.1, null);
            if (!abweichungE(t, hoeher.detect(ext), hoeher.detect(cmp)).isEmpty()) {
                konfidenzHoeher++;
            }
            Recognizer niedriger = kuenstlicherAbweichenderNeuErkenner(a.erkenner, -0.1, null);
            if (!abweichungE(t, niedriger.detect(ext), fc).isEmpty()) {
                konfidenzNiedriger++;
            }
            if (!fe.isEmpty()) {
                mitFund++;
                if (!abweichungE(t, fe.subList(0, fe.size() - 1), fc).isEmpty()) {
                    ohneLetztenFund++;
                }
                List<Finding> versetzt = new ArrayList<>(fe);
                Finding f = versetzt.get(0);
                versetzt.set(0, new Finding(f.type(), f.originalValue(), f.start() + 1, f.end() + 1, f.confidence(),
                        f.source()));
                if (!abweichungE(t, versetzt, fc).isEmpty()) {
                    verschoben++;
                }
            }
        }
        assertEquals(mitFund, ohneLetztenFund, "Ein fehlender Fund muss erkannt werden");
        assertEquals(mitFund, verschoben, "Ein verschobener Bereich muss erkannt werden");
        // Istwerte (600 Texte, davon 522 mit Fund): Kopie ohne Teil 2 weicht in 463 Texten ab; Konfidenz beider Formen
        // um 0,1 erhoeht: 313 Texte erkannt (nur ueber die Sperrgrenze 0,80 hinter gesperrtem Kontextwort);
        // Konfidenz nur der erweiterten Form um 0,1 gesenkt: 522 Texte erkannt (alle mit Fund)
        assertTrue(ohneTeil2 >= 320, "Die Kopie ohne Teil 2 muss von der Vergleichsform abweichen");
        assertTrue(konfidenzHoeher >= 215, "Eine in beiden Formen um 0,1 erhoehte Konfidenz muss erkannt werden");
        assertTrue(konfidenzNiedriger >= 360,
                "Eine nur in der erweiterten Form um 0,1 gesenkte Konfidenz muss erkannt werden");
    }

    @Test
    void selbsttest_cd_ein_kuenstlicherFehlalarmWirdInDenTextenGefunden() {
        Random rc = new Random(SEED_SELBST);
        Random rd = new Random(SEED_SELBST + 1);
        Zaehler z = new Zaehler();
        int[] art = new int[1];
        int initialeC = 0;
        int initialeD = 0;
        int ak13D = 0;
        for (int it = 0; it < 1_000; it++) {
            Art a = it % 2 == 0 ? LANR_ART : IK_ART;
            String c = rauschText(rc, z);
            Recognizer falsch = kuenstlicherNeuErkenner(a.erkenner, a.typ, a.quelle, FEHLALARM_INITIALE);
            if (!falsch.detect(c).isEmpty()) {
                initialeC++;
            }
            String d = gegenprobe(rd, z, a, art);
            if (!falsch.detect(d).isEmpty()) {
                initialeD++;
                ak13D += art[0] == 0 ? 1 : 0;
            }
        }
        // Istwerte (je 1 000 Texte): Rauschtexte 239, Gegenproben 45, davon AK-13 18
        assertTrue(initialeC >= 160, "Ein Fehlalarm hinter Initialen muss in den Rauschtexten auffallen");
        assertTrue(initialeD >= 30, "Ein Fehlalarm hinter Initialen muss in den Gegenproben auffallen");
        assertTrue(ak13D >= 12, "Ein Fehlalarm hinter Initialen muss in den Gegenproben aus AK-13 auffallen");
    }

    // ------------------------------------------------------------------ AK-24 (c)

    @Test
    void ak24c_rauschtexteOhneKontextwort_keinFund() {
        Random r = new Random(SEED_C);
        Zaehler z = new Zaehler();
        for (int it = 0; it < TEXTE; it++) {
            String text = rauschText(r, z);
            for (Art a : ARTEN) {
                List<Finding> funde = scan(a, text);
                assertTrue(funde.isEmpty(), ort(SEED_C, it, a) + " erwartet kein Fund, gefunden " + funde.size());
                assertTrue(funde.equals(a.erkenner.detect(text)), ort(SEED_C, it, a) + " Determinismus");
            }
        }
        mindestzahlenC(z);
    }

    /**
     * Mindestzahlen von (c), je hoechstens 70 % des gemessenen Istwerts des festen Seeds (3 000 Texte, Zaehlung je
     * Baustein oder Text). Istwerte: neu gezogene Versuche 382; Texte mit Folge ab vier Buchstaben und erweiterter
     * Luecke 2341, mit Tab 2590, mit mehreren Leerzeichen 2358, mit unsichtbarem Zeichen als Baustein 1287;
     * Bausteine Beinahe-Kontextwort 2086, Einzelbuchstabenfolge 5442, Initialen 1887, {@code I K} mit erweiterter
     * Luecke vor einer Nummer 1574, Kandidat (ganzes Kontextwort mit Luecken, wird neu gezogen) 1489, Nummern 3558
     * (gueltig 2398, gruppiert 670, ungueltig 908, abweichende Laenge 1156).
     */
    private static void mindestzahlenC(Zaehler z) {
        z.mindestens("texte", TEXTE);
        z.mindestens("neuGezogen", 260);
        z.mindestens("folgeAb4Erweitert", 1600);
        z.mindestens("mitTab", 1800);
        z.mindestens("mitMehrfachLeerzeichen", 1650);
        z.mindestens("uz", 900);
        z.mindestens("beinahe", 1450);
        z.mindestens("einzelbuchstaben", 3800);
        z.mindestens("initialen", 1300);
        z.mindestens("ikZwei", 1100);
        z.mindestens("kandidat", 1040);
        z.mindestens("nummer", 2480);
        z.mindestens("nummer.gueltig", 1670);
        z.mindestens("nummer.gruppiert", 460);
        z.mindestens("nummer.ungueltig", 630);
        z.mindestens("nummer.laenge", 800);
    }

    // ------------------------------------------------------------------ AK-24 (d)

    @Test
    void ak24d_gegenprobenAusAk13Ak14Ak17_keinFund() {
        Random r = new Random(SEED_D);
        Zaehler z = new Zaehler();
        int[] art = new int[1];
        for (int it = 0; it < TEXTE; it++) {
            Art nummerArt = it % 2 == 0 ? LANR_ART : IK_ART;
            String text = gegenprobe(r, z, nummerArt, art);
            for (Art a : ARTEN) {
                List<Finding> funde = scan(a, text);
                assertTrue(funde.isEmpty(), ort(SEED_D, it, a) + " " + GEGENPROBE_ART[art[0]]
                        + " erwartet kein Fund, gefunden " + funde.size());
                assertTrue(funde.equals(a.erkenner.detect(text)), ort(SEED_D, it, a) + " Determinismus");
            }
        }
        mindestzahlenD(z);
    }

    /**
     * Mindestzahlen von (d), je hoechstens 70 % des gemessenen Istwerts des festen Seeds (3 000 Texte). Istwerte:
     * neu gezogen 113; AK-13 791, AK-14 mit Mutation 735, AK-14 mit Teilgruppen 362, AK-17 1112; nachgestellte Form
     * 576; Nummern gueltig 1450, gruppiert 373, ungueltig 505, abweichende Laenge 672. Je Gegenprobenart wurden nach
     * der Neuziehung neu gezogen: AK-13 0, AK-14 mit Mutation 37, AK-14 mit Teilgruppen 25, AK-17 51. Mutationsarten
     * der behaltenen Texte (Buchstabe vorn / hinten / in der Mitte eingefuegt, geloescht, vertauscht, Wort doppelt,
     * Buchstabe ersetzt): 109 / 109 / 116 / 109 / 103 / 102 / 87.
     */
    private static void mindestzahlenD(Zaehler z) {
        z.mindestens("texte", TEXTE);
        z.mindestens("neuGezogen", 75);
        z.mindestens("neuGezogen.ak14.mutiert", 25);
        z.mindestens("neuGezogen.ak14.teilgruppen", 17);
        z.mindestens("neuGezogen.ak17", 35);
        z.mindestens("mutation.vorn", 75);
        z.mindestens("mutation.hinten", 75);
        z.mindestens("mutation.mitte", 80);
        z.mindestens("mutation.loeschen", 75);
        z.mindestens("mutation.tausch", 70);
        z.mindestens("mutation.doppelt", 70);
        z.mindestens("mutation.ersetzt", 60);
        z.mindestens("ak13", 550);
        z.mindestens("ak14.mutiert", 510);
        z.mindestens("ak14.teilgruppen", 250);
        z.mindestens("ak17", 770);
        z.mindestens("nachgestellt", 400);
        z.mindestens("nummer.gueltig", 1000);
        z.mindestens("nummer.gruppiert", 250);
        z.mindestens("nummer.ungueltig", 350);
        z.mindestens("nummer.laenge", 470);
    }

    // ------------------------------------------------------------------ AK-24 (e)

    private static String normalesKw(Random r, Art a, Text t, Zaehler z) {
        String kw = wahl(r, a.kws) + wahl(r, new String[] {": ", " ", ":"});
        t.gleich(kw);
        z.zaehle("normalesKw");
        return kw;
    }

    private static Text baueE(Random r, Art a, Zaehler z, int[] woerter) {
        Text t = new Text();
        t.gleich(wahl(r, VORFELD));
        int teile = 1 + r.nextInt(4);
        boolean via = false;
        for (int k = 0; k < teile; k++) {
            if (k > 0) {
                t.gleich(wahl(r, BLOCK_TRENNER));
            }
            int w = r.nextInt(100);
            if (w < 15) {
                normalesKw(r, a, t, z);
                via = false;
                if (r.nextBoolean()) {
                    nummerAnhaengen(t, nummer(r, a, z), false);
                    t.gleich(wahl(r, NACHFELD));
                }
            } else if (w < 62) {
                if (r.nextInt(7) == 0) {
                    normalesKw(r, a, t, z);
                    z.zaehle("laufenderKontext");
                }
                String wort = weitWortVon(r, a);
                boolean teil = (wort.equals("LANRNR") || wort.equals("IKNR")) && r.nextInt(100) < 35;
                weitWort(r, t, wort, false, teil, z);
                woerter[0]++;
                schluss(r, t, z, 5);
                int anzahl = r.nextInt(4) == 0 ? 2 + r.nextInt(2) : 1;
                for (int i = 0; i < anzahl; i++) {
                    if (i > 0) {
                        t.gleich(wahl(r, new String[] {", ", "; "}));
                        z.zaehle("aufzaehlung");
                    }
                    nummerAnhaengen(t, nummer(r, a, z), true);
                }
                t.gleich(wahl(r, NACHFELD));
                via = true;
            } else if (w < 80) {
                nummerAnhaengen(t, nummer(r, a, z), false);
                String sep = wahl(r, new String[] {" (", ", ", "; ", " ", " ["});
                t.gleich(sep);
                weitWort(r, t, weitWortVon(r, a), false, false, z);
                woerter[0]++;
                t.gleich(sep.endsWith("(") ? ")" : sep.endsWith("[") ? "]" : "");
                z.zaehle(sep.equals(", ") || sep.equals("; ") ? "nachgestellt.komma" : "nachgestellt");
                via = false;
            } else if (w < 88) {
                nummerAnhaengen(t, nummer(r, a, z), via);
                t.gleich(wahl(r, NACHFELD));
            } else if (w < 94) {
                if (r.nextInt(4) == 0) {
                    t.gleich("\r" + uzFolge(r) + "\n");
                    z.zaehle("crUzLf");
                } else {
                    t.gleich(wahl(r, UMBRUECHE));
                    z.zaehle("umbruch");
                }
            } else {
                t.gleich(wahl(r, new String[] {"des der die ", "Praxis ", "Rechnung "}));
            }
        }
        return t;
    }

    /**
     * Die Pruefregel von (e) ohne Assertion; "" wenn die erweiterte Form dieselben Funde hat wie die Vergleichsform:
     * gleiche Zahl, gleicher Typ, abgebildeter Bereich, gleicher Wert, gleiche Quelle, Konfidenz gleich der der
     * Vergleichsform, fuer Nummern hinter einem gesperrten Kontextwort das Minimum aus 0,80 und dieser Konfidenz.
     */
    private static String abweichungE(Text t, List<Finding> ext, List<Finding> cmp) {
        if (ext.size() != cmp.size()) {
            return "Anzahl";
        }
        for (int i = 0; i < cmp.size(); i++) {
            Finding fe = ext.get(i);
            Finding fc = cmp.get(i);
            if (fe.type() != fc.type()) {
                return "Typ";
            }
            int s = t.extPos(fc.start());
            int e = t.extPos(fc.end() - 1);
            if (s < 0 || e < 0) {
                return "Abbildung";
            }
            if (fe.start() != s || fe.end() != e + 1) {
                return "Bereich";
            }
            if (!fe.originalValue().equals(fc.originalValue())) {
                return "Wert";
            }
            double erwartet = t.via.contains(fc.start()) ? Math.min(SPERR, fc.confidence()) : fc.confidence();
            if (fe.confidence() != erwartet) {
                return "Konfidenz";
            }
            if (fe.level() != ConfidenceLevel.of(fe.confidence())) {
                return "Stufe";
            }
            if (!fe.source().equals(fc.source())) {
                return "Quelle";
            }
        }
        return "";
    }

    @Test
    void ak24e_erweiterteFormGleichDerVergleichsform_zahlTypBereichKonfidenz() {
        Random r = new Random(SEED_E);
        Zaehler z = new Zaehler();
        for (int it = 0; it < TEXTE; it++) {
            Art a = it % 2 == 0 ? LANR_ART : IK_ART;
            int[] woerter = new int[1];
            Text t = baueE(r, a, z, woerter);
            String ext = t.ext.toString();
            String cmp = t.cmp.toString();
            String ort = ort(SEED_E, it, a);
            List<Finding> fe = scan(a, ext);
            List<Finding> fc = scan(a, cmp);
            assertEquals("", abweichungE(t, fe, fc), ort + " Abweichung von der Vergleichsform");
            assertTrue(fe.equals(a.erkenner.detect(ext)), ort + " Determinismus");
            assertEquals(woerter[0], weitKontextwoerterReferenz(ext, a).size(), ort + " Referenz je Kontextwort");
            pruefstand(a, ext, fe, z);
            z.zaehle("texte");
            z.zaehle(fc.isEmpty() ? "ohneFund" : "mitFund");
            if (fe.size() > a.standLuecken.detect(ext).size()) {
                z.zaehle("mehrAlsKopie");
            }
            for (Finding f : fe) {
                z.zaehle(f.confidence() == SPERR ? "stufe.080" : f.confidence() == PZ_UNGUELTIG ? "stufe.050"
                        : f.confidence() == LAENGE ? "stufe.040" : "stufe.andere");
            }
        }
        mindestzahlenE(z);
    }

    /**
     * Mindestzahlen von (e), je hoechstens 70 % des gemessenen Istwerts des festen Seeds (3 000 Texte, Zaehlung je
     * Block, Luecke, Fund oder Text). Istwerte: Texte mit Fund 2673 (davon mit mehr Funden als die Kopie ohne Teil 2
     * 2423), ohne Fund 327; Kontextwoerter mit 4 / 6 / 8 / 10 / 23 Buchstaben 1440 / 1213 / 802 / 609 / 808; Luecken
     * streng / Leerraum / Punkt / Tab 12222 / 9786 / 11551 / 5697; Funde Stufe 0,80 / 0,50 / 0,40 / andere (normale
     * Kontextwoerter) 3399 / 1173 / 1434 / 577; Schlusspunkt 1777; Direktzeichen / Fuellwoerter / Nachsatzwort
     * 1911 / 867 / 755; unsichtbare Zeichen an Buchstaben / in einer Luecke / vor / hinter dem Wort 6353 / 5328 / 615
     * / 594; Formen aus Plan 3.2.5 (strenges Teilereignis) 335; laufender normaler Kontext direkt davor 507;
     * Aufzaehlung 1376; nachgestellt 796 (davon mit Komma 543); normales Kontextwort 1626; Umbruch 332, {@code CR UZ
     * LF} 112; Nummern gueltig / gruppiert / ungueltig / abweichende Laenge 3589 / 889 / 1308 / 1628; erklaerte
     * Zusatzfunde des Pruefstands 5425. Unerklaerte Fehlalarme: strikt 0.
     */
    private static void mindestzahlenE(Zaehler z) {
        z.mindestens("texte", TEXTE);
        z.mindestens("mitFund", 1870);
        z.mindestens("ohneFund", 225);
        z.mindestens("mehrAlsKopie", 1690);
        z.mindestens("laenge.4", 1000);
        z.mindestens("laenge.6", 840);
        z.mindestens("laenge.8", 560);
        z.mindestens("laenge.10", 420);
        z.mindestens("laenge.23", 560);
        z.mindestens("luecke.streng", 8500);
        z.mindestens("luecke.leerraum", 6800);
        z.mindestens("luecke.punkt", 8000);
        z.mindestens("luecke.tab", 3900);
        z.mindestens("stufe.080", 2370);
        z.mindestens("stufe.050", 820);
        z.mindestens("stufe.040", 1000);
        z.mindestens("stufe.andere", 400);
        z.mindestens("schlusspunkt", 1240);
        z.mindestens("tail.direkt", 1330);
        z.mindestens("tail.fuell", 600);
        z.mindestens("tail.nachsatz", 520);
        z.mindestens("uz.anBuchstabe", 4400);
        z.mindestens("uz.luecke", 3700);
        z.mindestens("uz.vor", 430);
        z.mindestens("uz.nach", 410);
        z.mindestens("teilEreignis", 230);
        z.mindestens("laufenderKontext", 350);
        z.mindestens("aufzaehlung", 960);
        z.mindestens("nachgestellt", 550);
        z.mindestens("nachgestellt.komma", 380);
        z.mindestens("normalesKw", 1130);
        z.mindestens("umbruch", 230);
        z.mindestens("crUzLf", 75);
        z.mindestens("nummer.gueltig", 2500);
        z.mindestens("nummer.gruppiert", 620);
        z.mindestens("nummer.ungueltig", 910);
        z.mindestens("nummer.laenge", 1130);
        z.mindestens("fehlalarm.erklaert", 3790);
        assertEquals(0, z.get("fehlalarm.unerklaert"), "unerklaerte Fehlalarme des Pruefstands");
    }
}
