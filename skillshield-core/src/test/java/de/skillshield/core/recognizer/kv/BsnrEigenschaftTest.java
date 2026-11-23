package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.KOMMA;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.SICHER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.WEITER;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.direkteKonfidenz;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.quelleNachNummerBsnr;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.scanBsnr;
import static de.skillshield.core.recognizer.kv.BsnrTestHilfen.schluesselReferenz;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.BOM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.DUENN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.GESCH_BINDESTRICH;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.HALBGEVIERT;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.LRM;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SCHMAL_NBSP;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.SHY;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.WORD_JOINER;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.TRENNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest zu Schreibweisen (AK-28, D-8) und Kontextregeln (AK-29) der BSNR (Feature kvnr-bsnr-erkennung,
 * Task T-13, AK-35 Determinismus), wie {@link KvnrEigenschaftTest} fuer die KVNR. Das Orakel ist die Konstruktion:
 * Jeder Text entsteht aus Bloecken (Kontextwort aus D-8 in zufaelliger Schreibung, mit {@code ae} oder Umlaut, Trenner,
 * Fuellwoerter oder Umbruch, Nummer), deren erwartete Funde (Start, Wert, Konfidenz, Quelle) bei der Erzeugung
 * feststehen; es gibt keinen Aufruf des Produkts im Orakel. Die Konfidenz ist {@code min(Deckel des Kontexts, Stufe der
 * Nummer)} mit den Deckeln 0,90 (direkt), 0,80 (Fuellwoerter, Umbruch, Aufzaehlung ab der zweiten Nummer,
 * nachgestellt, gesperrt) und 0,60 (nachgestellt mit Komma oder Semikolon) und den Stufen 0,90 (bekannter
 * Regionalcode), 0,50 (unbekannter Regionalcode) und 0,40 (Laenge 6 bis 12 ausser neun); die Quelle leitet
 * {@link BsnrTestHilfen#quelleNachNummerBsnr} aus der Nummer ab (Regel 9). Die Regionalcode-Referenz
 * ({@link BsnrTestHilfen#schluesselReferenz}) ist eine eigene, bereichsweise geschriebene Liste und ruft das Produkt
 * nicht auf. Jede Nummer hat Regionalcode, {@code 00000} und freie Ziffern (A-6; bei Laengenabweichung entsprechend
 * gekuerzt oder verlaengert), steht nie direkt hinter einem Buchstaben, und jeder Text geht durch
 * {@link BsnrTestHilfen#scanBsnr} (Waechter {@link BsnrSynthetischWaechter}, Typ, Teilstring-Invariante, Sortierung,
 * Ueberlappungsfreiheit) und wird auf Determinismus geprueft.
 *
 * <p>Die Bloecke sind immer durch einen Absatz mit Leerzeile getrennt (Grenze wie in {@link KvnrEigenschaftTest}); die
 * Wechselwirkung ueber Blockgrenzen decken die Einzeltests in {@link BsnrRecognizerTest} ab. Das nachgestellte
 * Kompositum ({@code Praxis-BSNR}) ist Teil des Orakels (Entscheidung T-13).
 *
 * <p>Zaehler belegen, dass jede Schreibweise und jede Regel wirklich erzeugt wird; die Mindestzahlen stehen bei 70
 * Prozent oder weniger des Messwerts (Messwert im Kommentar, fester Seed). Die Selbsttests belegen, dass der Vergleich
 * eingebaute Fehler erkennt. Meldungen nennen nur Seed, Iteration, Fall und Zaehler, nie Text oder Ziffern.
 */
class BsnrEigenschaftTest {

    private static final long SEED = 20261401L;
    private static final long SEED_SELBSTTEST = 20261402L;
    private static final int TEXTE = 3_000;
    private static final int TEXTE_SELBSTTEST = 600;

    private static final String ABSATZ = "\n\nDanach folgt ein neuer Absatz mit Text.\n\n";
    private static final String[] SPERR_TRENNER = {" ", ".", "-", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), String.valueOf(GESCH_BINDESTRICH), String.valueOf(HALBGEVIERT)};
    private static final String[] WEIT_TRENNER = {". ", ".  ", "  ", "   "};
    private static final int[][] GRUPPEN_ZIFFERN = {{3, 3, 3}, {2, 5, 2}, {4, 5}, {5, 4}};
    private static final String[] DIREKT_TRENNER = {" ", ": ", ":", " : ", "=", "= ", "\t", String.valueOf(NBSP),
        "  ", ":  "};
    private static final String[] PRAEFIX = {"", "", "Praxis, ", "Die Praxis hat die ", "Daten: "};
    private static final String[] SUFFIX = {"", "", "", ".", ",", ";", ")", " Rest", ", Praxis", "!", "?", ":", "-",
        "/", " -", "  Rest"};
    private static final String[] FUELLWOERTER = {"der", "Praxis", "Frau", "Muster", "bitte", "siehe", "unten",
        "heute", "wurde"};
    private static final String[] UMBRUECHE = {"\n", "\r\n", "\r"};
    private static final String[] AUFZAEHLUNG = {", ", "; ", " und ", " / ", "\n"};
    private static final String[] FREMDWOERTER = {"BSNRX", "Betriebsstaette", "Betriebsstaetten", "Betrieb", "KV-Nr.",
        "Tel.", "Rueckruf unter", "LANR", "IK", "Nebenbetriebsstaette"};
    private static final char[] UNSICHTBAR = {ZWSP, SHY, BOM, WORD_JOINER, LRM};
    private static final String[] SPERR_WOERTER = {"BSNR", "NBSNR"};
    private static final String PRAEFIX_WORT = "Praxis-";
    /** Das Wort vor dem Bindestrich eines nachgestellten Kompositums. */
    private static final String[] KOMPOSITUM_WOERTER = {"Praxis", "Pat.", "Haupt", "Herr"};

    /** Alle Kontextwoerter aus D-8 in den Schreibungen mit {@code ae} (Umlaut wird zufaellig eingesetzt). */
    private static final String[] KWS = {"BSNR", "BSNRNr", "NBSNR", "BSNR-Nr.", "Betriebsstaettennummer",
        "Betriebsstaettennr", "Betriebsstaettennr.", "Nebenbetriebsstaettennummer", "Nebenbetriebsstaettennr",
        "Betriebsstaette Nr.", "Betriebsstaette Nummer", "Betriebsstaetten-Nr.", "Betriebsstaetten-Nummer",
        "Betriebsstaetten Nr.", "Nebenbetriebsstaette Nr.", "Nebenbetriebsstaetten-Nr.",
        "Nebenbetriebsstaetten-Nummer", "Praxis-BSNR", "Praxis-Betriebsstaettennummer"};
    private static final String AE_KLEIN = String.valueOf(KvnrBsnrHilfen.AE_KLEIN);

    /** Nummernarten; die Zahl bestimmt die Gewichte der Auswahl. */
    private static final int BEKANNT = 0;
    private static final int UNBEKANNT = 1;
    private static final int LAENGE = 2;
    private static final String[] ART_NAME = {"bekannt", "unbekannt", "laenge"};
    private static final int[] LAENGEN = {6, 7, 8, 10, 11, 12};
    private static final int[] ALLE = {BEKANNT, UNBEKANNT, LAENGE};
    private static final int[] ALLE_GEWICHT = {45, 25, 30};
    private static final int[] NEGATIV_ARTEN = {BEKANNT, UNBEKANNT, LAENGE};
    private static final int[] NEGATIV_GEWICHT = {40, 30, 30};

    private record Nummer(String text, int art) {
    }

    private record Erw(int start, String wert, double konfidenz) {
    }

    /** Ein erzeugter Text mit den erwarteten Funden und den Namen der Bloecke (nur zur Meldung). */
    private static final class Gen {
        final StringBuilder text = new StringBuilder();
        final List<Erw> erw = new ArrayList<>();
        final List<String> bloecke = new ArrayList<>();
    }

    /** Zaehler, damit der Test belegt, dass jede Schreibweise und Regel wirklich erzeugt wird. */
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

    private static boolean w(Random r, int prozent) {
        return r.nextInt(100) < prozent;
    }

    private static String ziffern(Random r, int anzahl) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < anzahl; i++) {
            sb.append((char) ('0' + r.nextInt(10)));
        }
        return sb.toString();
    }

    private static String zweiStellig(int zahl) {
        return (zahl < 10 ? "0" : "") + zahl;
    }

    // ------------------------------------------------------------------ Nummern

    /** Wahl der Nummernart nach Gewichten; {@code erlaubt} nennt die zulaessigen Arten. */
    private static int wahlArt(Random r, int[] erlaubt, int[] gewicht) {
        int summe = 0;
        for (int g : gewicht) {
            summe += g;
        }
        int x = r.nextInt(summe);
        for (int i = 0; i < erlaubt.length; i++) {
            if (x < gewicht[i]) {
                return erlaubt[i];
            }
            x -= gewicht[i];
        }
        return erlaubt[erlaubt.length - 1];
    }

    /** Ein Regionalcode, der nach der Referenz bekannt ({@code bekannt}) oder unbekannt ist (auch 00, 35, 75, 99). */
    private static int code(Random r, boolean bekannt) {
        while (true) {
            int c = r.nextInt(100);
            if (schluesselReferenz(c) == bekannt) {
                return c;
            }
        }
    }

    /**
     * Eine zufaellige Zerlegung der neun Ziffern in mindestens zwei Gruppen (Bitmaske 1 bis 255 ueber die acht
     * Schnittstellen). Eine feste Zerlegung wird in der Haelfte der Faelle gezogen.
     */
    private static int[] zerlegung(Random r, Statistik s) {
        if (r.nextBoolean()) {
            int nr = r.nextInt(GRUPPEN_ZIFFERN.length);
            s.zaehle("form.gruppen." + nr);
            return GRUPPEN_ZIFFERN[nr];
        }
        int maske = 1 + r.nextInt((1 << 8) - 1);
        List<Integer> gruppen = new ArrayList<>();
        int laenge = 1;
        for (int i = 0; i < 8; i++) {
            if ((maske & (1 << i)) != 0) {
                gruppen.add(laenge);
                laenge = 0;
            }
            laenge++;
        }
        gruppen.add(laenge);
        s.zaehle("form.zerlegung.gruppen." + gruppen.size());
        return gruppen.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String gruppiert(String ziffern, int[] gruppen, Random r, boolean gleicherTrenner) {
        String trenner = TRENNER[r.nextInt(TRENNER.length)];
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < gruppen.length; i++) {
            if (i > 0) {
                sb.append(gleicherTrenner ? trenner : TRENNER[r.nextInt(TRENNER.length)]);
            }
            sb.append(ziffern, pos, pos + gruppen[i]);
            pos += gruppen[i];
        }
        return sb.toString();
    }

    /** Vier Punkt-Gruppen zu je ein bis drei Ziffern sind eine IPv4-Gestalt; der Generator vermeidet sie. */
    private static boolean ipv4Gestalt(String nummer) {
        String[] teile = nummer.split("\\.", -1);
        if (teile.length != 4) {
            return false;
        }
        for (String t : teile) {
            if (t.isEmpty() || t.length() > 3) {
                return false;
            }
        }
        return true;
    }

    /** Eine Nummer der Art {@code art}; die Schreibweise (Trenner, Gruppen) wird zufaellig gewaehlt. */
    private static Nummer nummer(Random r, Statistik s, int art) {
        s.zaehle("art." + ART_NAME[art]);
        if (art == LAENGE) {
            int n = LAENGEN[r.nextInt(LAENGEN.length)];
            s.zaehle("form.laenge." + n);
            return new Nummer(zweiStellig(code(r, r.nextBoolean())) + "0000" + ziffern(r, n - 6), art);
        }
        int c = code(r, art == BEKANNT);
        s.zaehle("code." + (c == 0 || c == 35 || c == 75 || c == 99 ? zweiStellig(c) : "sonstige"));
        String neun = zweiStellig(c) + "00000" + ziffern(r, 2);
        if (w(r, 45)) {
            int[] zerlegung = zerlegung(r, s);
            String text = gruppiert(neun, zerlegung, r, w(r, 50));
            if (!ipv4Gestalt(text)) {
                s.zaehle("form.gruppiert");
                return new Nummer(text, art);
            }
            s.zaehle("form.gruppiertIpGestalt");
        }
        s.zaehle("form.geschlossen");
        return new Nummer(neun, art);
    }

    // ------------------------------------------------------------------ Kontextwoerter

    private static String schreibung(Random r, Statistik s, String wort) {
        int v = r.nextInt(4);
        String[] name = {"original", "gross", "klein", "wechsel"};
        s.zaehle("schreibung." + name[v]);
        switch (v) {
            case 1:
                return wort.toUpperCase(Locale.ROOT);
            case 2:
                return wort.toLowerCase(Locale.ROOT);
            case 3:
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < wort.length(); i++) {
                    char c = wort.charAt(i);
                    sb.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
                }
                return sb.toString();
            default:
                return wort;
        }
    }

    /** Ein Kontextwort aus der Liste; mit {@code ae} oder, zufaellig, mit dem Umlaut. */
    private static String kw(Random r, Statistik s) {
        int i = r.nextInt(KWS.length);
        s.zaehle("kw." + i);
        String wort = KWS[i];
        if (wort.contains("ae") && r.nextBoolean()) {
            s.zaehle("kw.umlaut");
            return wort.replace("ae", AE_KLEIN);
        }
        if (wort.contains("ae")) {
            s.zaehle("kw.ae");
        }
        return wort;
    }

    private static String kwText(Random r, Statistik s, String kw) {
        return schreibung(r, s, kw);
    }

    private static String fuellwoerter(Random r, int anzahl) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < anzahl; i++) {
            sb.append(FUELLWOERTER[r.nextInt(FUELLWOERTER.length)]).append(' ');
        }
        return sb.toString();
    }

    private static String unsichtbar(Random r) {
        StringBuilder sb = new StringBuilder();
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            sb.append(UNSICHTBAR[r.nextInt(UNSICHTBAR.length)]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ Bloecke

    /** Haengt die Nummer an; ist {@code deckel > 0}, wird ein Fund mit {@code min(deckel, Stufe)} erwartet. */
    private static void nummerAn(Gen g, Statistik s, Nummer n, double deckel) {
        if (deckel > 0) {
            String quelle = quelleNachNummerBsnr(n.text());
            double konf = Math.min(deckel, direkteKonfidenz(quelle));
            g.erw.add(new Erw(g.text.length(), n.text(), konf));
            s.zaehle("konfidenz." + konf);
            s.zaehle("quelle." + quelle);
            s.zaehle("fund");
        }
        g.text.append(n.text());
    }

    private static void suffix(Gen g, Random r, Statistik s) {
        String x = SUFFIX[r.nextInt(SUFFIX.length)];
        if (!x.isEmpty()) {
            s.zaehle("suffix");
        }
        g.text.append(x);
    }

    private static String direktTrenner(Random r, Statistik s) {
        int i = r.nextInt(DIREKT_TRENNER.length);
        s.zaehle("direktTrenner." + i);
        return DIREKT_TRENNER[i];
    }

    private static String praefix(Random r) {
        return PRAEFIX[r.nextInt(PRAEFIX.length)];
    }

    private static Nummer wahl(Random r, Statistik s, boolean negativ) {
        return negativ ? nummer(r, s, wahlArt(r, NEGATIV_ARTEN, NEGATIV_GEWICHT))
                : nummer(r, s, wahlArt(r, ALLE, ALLE_GEWICHT));
    }

    private static void blockDirekt(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        Nummer n = wahl(r, s, false);
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        s.zaehle("block.direkt");
        g.bloecke.add("direkt");
        nummerAn(g, s, n, SICHER);
        suffix(g, r, s);
    }

    private static void blockFuellwoerter(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        Nummer n = wahl(r, s, false);
        int anzahl = 1 + r.nextInt(3);
        s.zaehle("fuellwoerter." + anzahl);
        s.zaehle("block.fuellwoerter");
        g.bloecke.add("fuellwoerter");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        g.text.append(fuellwoerter(r, anzahl));
        nummerAn(g, s, n, WEITER);
        suffix(g, r, s);
    }

    private static void blockVierFuellwoerter(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        Nummer n = wahl(r, s, true);
        s.zaehle("negativ.vierFuellwoerter");
        g.bloecke.add("vierFuellwoerter");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        g.text.append(fuellwoerter(r, 4 + r.nextInt(2)));
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockUmbruch(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        Nummer n = wahl(r, s, false);
        g.text.append(praefix(r)).append(kwText(r, s, k));
        if (w(r, 50)) {
            g.text.append(':');
        }
        if (w(r, 30)) {
            g.text.append(' ');
        }
        int v = r.nextInt(4);
        String[] name = {"lf", "crlf", "cr", "crUzLf"};
        s.zaehle("umbruch." + name[v]);
        if (v < 3) {
            g.text.append(UMBRUECHE[v]);
        } else {
            g.text.append('\r').append(unsichtbar(r)).append('\n');
        }
        if (w(r, 30)) {
            g.text.append(' ');
        }
        s.zaehle("block.umbruch");
        g.bloecke.add("umbruch");
        nummerAn(g, s, n, WEITER);
        suffix(g, r, s);
    }

    private static void blockLeerzeile(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        Nummer n = wahl(r, s, true);
        String[] leer = {"\n\n", "\r\n\r\n", "\n  \n", "\r\r", "\n\r\n"};
        int v = r.nextInt(leer.length);
        s.zaehle("negativ.leerzeile." + v);
        g.bloecke.add("leerzeile");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(w(r, 50) ? ":" : "").append(leer[v]);
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockAufzaehlung(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        int anzahl = 2 + r.nextInt(3);
        s.zaehle("aufzaehlungAnzahl." + anzahl);
        s.zaehle("block.aufzaehlung");
        g.bloecke.add("aufzaehlung");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        for (int i = 0; i < anzahl; i++) {
            if (i > 0) {
                int t = r.nextInt(AUFZAEHLUNG.length);
                s.zaehle("aufzaehlungTrenner." + t);
                g.text.append(AUFZAEHLUNG[t]);
            }
            nummerAn(g, s, wahl(r, s, false), i == 0 ? SICHER : WEITER);
        }
        suffix(g, r, s);
    }

    private static void blockNachgestellt(Gen g, Random r, Statistik s) {
        String k = kw(r, s);
        // Ein Wort, Bindestrich, Kontextwort (Praxis-BSNR) gilt hinter der Nummer als ein nachgestelltes Kontextwort
        // (Entscheidung T-13); die Stufen sind dieselben wie beim einfachen Kontextwort.
        boolean eigenesKompositum = k.startsWith(PRAEFIX_WORT);
        boolean kompositum = !eigenesKompositum && w(r, 35);
        s.zaehle(eigenesKompositum ? "nachgestelltKw.kwKompositum" : kompositum ? "nachgestelltKw.kompositum"
                : "nachgestelltKw.einfach");
        Nummer n = wahl(r, s, false);
        int v = r.nextInt(3);
        String[] name = {"klammer", "komma", "semikolon"};
        s.zaehle("nachgestellt." + name[v]);
        s.zaehle("block.nachgestellt");
        g.bloecke.add("nachgestellt");
        g.text.append(r.nextBoolean() ? "Wert " : "");
        nummerAn(g, s, n, v == 0 ? WEITER : KOMMA);
        String wort = kwText(r, s, k);
        if (kompositum) {
            wort = KOMPOSITUM_WOERTER[r.nextInt(KOMPOSITUM_WOERTER.length)] + "-" + wort;
        }
        g.text.append(v == 0 ? " (" + wort + ")" : (v == 1 ? ", " : "; ") + wort);
    }

    private static String sperrWort(Random r, Statistik s, String wort, String trenner, boolean punktEnde) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            if (i > 0) {
                sb.append(trenner);
            }
            sb.append(wort.charAt(i));
        }
        if (punktEnde) {
            sb.append('.');
        }
        return schreibung(r, s, sb.toString());
    }

    private static void blockSperr(Gen g, Random r, Statistik s) {
        String wort = SPERR_WOERTER[r.nextInt(SPERR_WOERTER.length)];
        s.zaehle("sperr.wort." + wort);
        String sperr;
        if (r.nextBoolean()) {
            int t = r.nextInt(SPERR_TRENNER.length);
            s.zaehle("sperr.einzeichen." + t);
            sperr = sperrWort(r, s, wort, SPERR_TRENNER[t], false);
        } else {
            int t = r.nextInt(WEIT_TRENNER.length);
            s.zaehle("sperr.weit." + t);
            sperr = sperrWort(r, s, wort, WEIT_TRENNER[t], t < 2);
        }
        Nummer n = wahl(r, s, false);
        g.bloecke.add("sperr");
        s.zaehle("block.sperr");
        g.text.append(praefix(r)).append(sperr).append(r.nextBoolean() ? " " : ": ");
        nummerAn(g, s, n, WEITER);
        suffix(g, r, s);
    }

    private static void blockFremdwort(Gen g, Random r, Statistik s) {
        String wort = FREMDWOERTER[r.nextInt(FREMDWOERTER.length)];
        Nummer n = wahl(r, s, true);
        s.zaehle("negativ.fremdwort");
        g.bloecke.add("fremdwort");
        g.text.append(praefix(r)).append(schreibung(r, s, wort)).append(w(r, 50) ? ": " : " ");
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockOhneKontext(Gen g, Random r, Statistik s) {
        Nummer n = wahl(r, s, true);
        s.zaehle("negativ.ohneKontext");
        g.bloecke.add("ohneKontext");
        g.text.append(r.nextBoolean() ? "Die Praxis " : "Der Wert ");
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static final String[] BLOCK_NAMEN = {"direkt", "fuellwoerter", "umbruch", "aufzaehlung", "nachgestellt",
        "sperr", "vierFuellwoerter", "leerzeile", "fremdwort", "ohneKontext"};
    private static final int[] BLOCK_GEWICHT = {20, 14, 12, 10, 12, 10, 6, 6, 5, 5};

    private static void block(Gen g, Random r, Statistik s) {
        int summe = 0;
        for (int x : BLOCK_GEWICHT) {
            summe += x;
        }
        int x = r.nextInt(summe);
        int b = 0;
        while (x >= BLOCK_GEWICHT[b]) {
            x -= BLOCK_GEWICHT[b];
            b++;
        }
        s.zaehle("blocktyp." + BLOCK_NAMEN[b]);
        switch (b) {
            case 0 -> blockDirekt(g, r, s);
            case 1 -> blockFuellwoerter(g, r, s);
            case 2 -> blockUmbruch(g, r, s);
            case 3 -> blockAufzaehlung(g, r, s);
            case 4 -> blockNachgestellt(g, r, s);
            case 5 -> blockSperr(g, r, s);
            case 6 -> blockVierFuellwoerter(g, r, s);
            case 7 -> blockLeerzeile(g, r, s);
            case 8 -> blockFremdwort(g, r, s);
            default -> blockOhneKontext(g, r, s);
        }
    }

    private static Gen erzeuge(Random r, Statistik s) {
        Gen g = new Gen();
        int bloecke = 1 + r.nextInt(3);
        for (int i = 0; i < bloecke; i++) {
            if (i > 0) {
                g.text.append(ABSATZ);
            }
            block(g, r, s);
        }
        s.zaehle("text.bloecke." + bloecke);
        return g;
    }

    // ------------------------------------------------------------------ Vergleich (Orakel gegen Produkt)

    /** Leer, wenn die Funde genau den erwarteten entsprechen; sonst eine Beschreibung ohne Text und Ziffern. */
    private static String abweichung(List<Finding> ist, List<Erw> soll) {
        if (ist.size() != soll.size()) {
            return "Fundzahl " + ist.size() + " statt " + soll.size();
        }
        for (int i = 0; i < soll.size(); i++) {
            Finding f = ist.get(i);
            Erw e = soll.get(i);
            String wo = "Fund " + i + ": ";
            if (f.start() != e.start() || f.end() != e.start() + e.wert().length()) {
                return wo + "Bereich";
            }
            if (!e.wert().equals(f.originalValue())) {
                return wo + "Wert";
            }
            if (f.confidence() != e.konfidenz()) {
                return wo + "Konfidenz " + f.confidence() + " statt " + e.konfidenz();
            }
            if (f.level() != ConfidenceLevel.of(e.konfidenz())) {
                return wo + "Stufe";
            }
            if (!quelleNachNummerBsnr(e.wert()).equals(f.source()) || f.type() != FindingType.BSNR) {
                return wo + "Quelle oder Typ";
            }
        }
        return "";
    }

    private static void einzeln(Statistik s, String schluessel, int min) {
        s.mindestens(schluessel, min);
    }

    /** Alle Zaehler mit dem Praefix: genau {@code anzahl} Stueck, jeder mindestens {@code min}. */
    private static void familie(Statistik s, String praefix, int anzahl, int min) {
        int gefunden = 0;
        for (Map.Entry<String, Integer> e : s.z.entrySet()) {
            if (e.getKey().startsWith(praefix)) {
                gefunden++;
                assertTrue(e.getValue() >= min, "Zaehler " + e.getKey() + " zu klein: " + e.getValue());
            }
        }
        assertEquals(anzahl, gefunden, "Zahl der Zaehler der Familie " + praefix);
    }

    /**
     * Mindestzahlen bei SEED und TEXTE = 3000. Der Istwert steht je Zeile im Kommentar (kleinster Wert bei
     * Familien); jede Mindestzahl liegt bei hoechstens 70 Prozent davon.
     */
    private static void pruefeStatistik(Statistik s) {
        for (int i = 0; i < KWS.length; i++) {
            einzeln(s, "kw." + i, 155); // Ist 227 bis 271 je Kontextwort der Liste
        }
        einzeln(s, "kw.ae", 1230); // Ist 1771 (Wort mit ae gewaehlt)
        einzeln(s, "kw.umlaut", 1190); // Ist 1712 (Wort mit Umlaut gewaehlt)
        familie(s, "schreibung.", 4, 960); // Ist 1385 bis 1463
        familie(s, "art.", 3, 1310); // Ist 1883 (unbekannt) bis 3181 (bekannt)
        einzeln(s, "art.bekannt", 2220); // Ist 3181
        einzeln(s, "art.laenge", 1480); // Ist 2121
        einzeln(s, "code.00", 55); // Ist 82
        einzeln(s, "code.35", 55); // Ist 81 (Sonderkennung, Stelle 3 und 4 gleich 00: unbekannt)
        einzeln(s, "code.75", 65); // Ist 94
        einzeln(s, "code.99", 55); // Ist 84 (Netzverbund)
        einzeln(s, "code.sonstige", 3300); // Ist 4723
        familie(s, "aufzaehlungAnzahl.", 3, 130); // Ist 188 bis 229
        familie(s, "aufzaehlungTrenner.", AUFZAEHLUNG.length, 160); // Ist 229 bis 258
        einzeln(s, "block.direkt", 830); // Ist 1197
        einzeln(s, "block.fuellwoerter", 590); // Ist 852
        einzeln(s, "block.umbruch", 470); // Ist 682
        einzeln(s, "block.aufzaehlung", 430); // Ist 620
        einzeln(s, "block.nachgestellt", 485); // Ist 697
        einzeln(s, "block.sperr", 415); // Ist 597
        einzeln(s, "blocktyp.vierFuellwoerter", 240); // Ist 348
        einzeln(s, "blocktyp.leerzeile", 245); // Ist 353
        einzeln(s, "blocktyp.fremdwort", 220); // Ist 320
        einzeln(s, "blocktyp.ohneKontext", 210); // Ist 305
        familie(s, "form.laenge.", LAENGEN.length, 225); // Ist 328 bis 375
        einzeln(s, "form.geschlossen", 1950); // Ist 2802
        einzeln(s, "form.gruppiert", 1580); // Ist 2262
        familie(s, "form.gruppen.", GRUPPEN_ZIFFERN.length, 170); // Ist 245 bis 319 (die festen Zerlegungen)
        pruefeZerlegungen(s);
        familie(s, "fuellwoerter.", 3, 190); // Ist 272 bis 300
        familie(s, "umbruch.", 4, 105); // Ist 154 (cr) bis 181
        einzeln(s, "umbruch.crUzLf", 125); // Ist 181
        familie(s, "nachgestellt.", 3, 140); // Ist 203 bis 253
        einzeln(s, "nachgestelltKw.einfach", 285); // Ist 410
        einzeln(s, "nachgestelltKw.kompositum", 140); // Ist 206 (Wort, Bindestrich, Kontextwort hinter der Nummer)
        einzeln(s, "nachgestelltKw.kwKompositum", 55); // Ist 81 (das Kontextwort der Liste ist selbst ein Kompositum)
        familie(s, "negativ.leerzeile.", 5, 39); // Ist 57 bis 78
        einzeln(s, "negativ.vierFuellwoerter", 240); // Ist 348
        einzeln(s, "negativ.fremdwort", 220); // Ist 320
        einzeln(s, "negativ.ohneKontext", 210); // Ist 305
        familie(s, "sperr.einzeichen.", SPERR_TRENNER.length, 19); // Ist 28 bis 43
        familie(s, "sperr.weit.", WEIT_TRENNER.length, 49); // Ist 70 bis 101
        familie(s, "sperr.wort.", SPERR_WOERTER.length, 200); // Ist 292 bis 305
        familie(s, "direktTrenner.", DIREKT_TRENNER.length, 190); // Ist 272 bis 318
        familie(s, "text.bloecke.", 3, 690); // Ist 987 bis 1016
        einzeln(s, "suffix", 2970); // Ist 4252
        einzeln(s, "fund", 4100); // Ist 5859
        einzeln(s, "konfidenz.0.9", 585); // Ist 839
        einzeln(s, "konfidenz.0.8", 1110); // Ist 1593
        einzeln(s, "konfidenz.0.6", 140); // Ist 203
        einzeln(s, "konfidenz.0.5", 1030); // Ist 1473
        einzeln(s, "konfidenz.0.4", 1220); // Ist 1751
        einzeln(s, "quelle.bsnr", 1840); // Ist 2635
        einzeln(s, "quelle.bsnr-regionalcode-unbekannt", 1030); // Ist 1473
        einzeln(s, "quelle.bsnr-laenge-abweichend", 1220); // Ist 1751
    }

    /** Zufaellige Zerlegungen der neun Ziffern: Je Gruppenzahl von 2 bis 8 ein Zaehler (neun Gruppen gibt es nur 1). */
    private static void pruefeZerlegungen(Statistik s) {
        int[] mindest = {25, 85, 155, 210, 160, 95, 30}; // Ist 37, 125, 227, 305, 234, 137, 47 fuer 2 bis 8 Gruppen
        for (int i = 0; i < mindest.length; i++) {
            einzeln(s, "form.zerlegung.gruppen." + (i + 2), mindest[i]);
        }
    }

    @Test
    void eigenschaft_texteAusBloecken_funde_entsprechenDerKonstruktion() {
        Random r = new Random(SEED);
        Statistik s = new Statistik();
        for (int i = 0; i < TEXTE; i++) {
            Gen g = erzeuge(r, s);
            String text = g.text.toString();
            List<Finding> ist = scanBsnr(text);
            String fehler = abweichung(ist, g.erw);
            assertTrue(fehler.isEmpty(),
                    "Seed " + SEED + " Iteration " + i + " Bloecke " + g.bloecke + ": " + fehler);
            assertTrue(ist.equals(scanBsnr(text)), "Nicht deterministisch, Seed " + SEED + " Iteration " + i);
            s.zaehle("text");
        }
        einzeln(s, "text", TEXTE);
        pruefeStatistik(s);
    }

    // ------------------------------------------------------------------ Selbsttests

    private static List<Finding> mit(List<Finding> funde, UnaryOperator<Finding> f) {
        List<Finding> aus = new ArrayList<>();
        for (Finding x : funde) {
            aus.add(f.apply(x));
        }
        return aus;
    }

    private static Finding neu(Finding f, int ende, double konfidenz, String quelle) {
        return new Finding(f.type(), f.originalValue().substring(0, ende - f.start()), f.start(), ende, konfidenz,
                quelle);
    }

    /** Wie viele von {@code TEXTE_SELBSTTEST} Texten weichen mit dem eingebauten Fehler vom Orakel ab? */
    private static int erkannteFehler(UnaryOperator<List<Finding>> fehler, int[] ohneFehler) {
        Random r = new Random(SEED_SELBSTTEST);
        Statistik s = new Statistik();
        int erkannt = 0;
        int sauber = 0;
        for (int i = 0; i < TEXTE_SELBSTTEST; i++) {
            Gen g = erzeuge(r, s);
            List<Finding> ist = scanBsnr(g.text.toString());
            sauber += abweichung(ist, g.erw).isEmpty() ? 0 : 1;
            erkannt += abweichung(fehler.apply(ist), g.erw).isEmpty() ? 0 : 1;
        }
        ohneFehler[0] = sauber;
        return erkannt;
    }

    private static void assertErkannt(String name, UnaryOperator<List<Finding>> fehler, int min) {
        int[] sauber = new int[1];
        int erkannt = erkannteFehler(fehler, sauber);
        assertEquals(0, sauber[0], "Das Produkt weicht ohne Fehler ab: " + name);
        assertTrue(erkannt >= min, "Eingebauter Fehler " + name + " zu selten erkannt: " + erkannt);
    }

    @Test
    void selbsttest_keineFunde_wirdErkannt() {
        assertErkannt("keineFunde", funde -> List.of(), 380); // Ist 548 von 600
    }

    @Test
    void selbsttest_nurDerErsteFund_wirdErkannt() {
        assertErkannt("nurErster", funde -> funde.isEmpty() ? funde : funde.subList(0, 1), 225); // Ist 326
    }

    @Test
    void selbsttest_konfidenzImmerDirekt_wirdErkannt() {
        assertErkannt("konfidenzDirekt", funde -> mit(funde, f -> neu(f, f.end(), SICHER, f.source())), 350); // Ist 505
    }

    @Test
    void selbsttest_endeEinsZuKurz_wirdErkannt() {
        // Ist 548
        assertErkannt("endeKurz", funde -> mit(funde, f -> neu(f, f.end() - 1, f.confidence(), f.source())), 380);
    }

    @Test
    void selbsttest_unbekannterRegionalcodeVerworfen_wirdErkannt() {
        // Ist 247
        assertErkannt("ohneRegionalcodeUnbekannt", funde -> funde.stream()
                .filter(f -> !BsnrRecognizer.SOURCE_REGIONALCODE_UNBEKANNT.equals(f.source())).toList(), 170);
    }

    @Test
    void selbsttest_quelleImmerBsnr_wirdErkannt() {
        assertErkannt("quelleBsnr", funde -> mit(funde, f -> neu(f, f.end(), f.confidence(), "bsnr")), 280); // Ist 406
    }

    @Test
    void selbsttest_angehaengterFundInJedemText_wirdErkannt() {
        assertErkannt("zusatzFund", funde -> {
            List<Finding> aus = new ArrayList<>(funde);
            aus.add(new Finding(FindingType.BSNR, "x", 0, 1, SICHER, "bsnr"));
            return aus;
        }, 420); // Ist 600
    }
}
