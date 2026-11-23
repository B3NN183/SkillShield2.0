package de.skillshield.core.recognizer.kv;

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
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.GRUPPEN;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.TRENNER;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.direkteKonfidenz;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.quelleNachNummerKvnr;
import static de.skillshield.core.recognizer.kv.KvnrTestHilfen.scanKvnr;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest zu Schreibweisen (AK-5, D-1) und Kontextregeln (AK-9, D-3) der KVNR (Feature kvnr-bsnr-erkennung,
 * Task T-5, AK-35 Determinismus). Das Orakel ist die Konstruktion: Jeder Text entsteht aus Bloecken (Kontextwort aus
 * D-2 in zufaelliger Schreibung, Trenner, Fuellwoerter oder Umbruch, Nummer), deren erwartete Funde (Start, Wert,
 * Konfidenz, Quelle) bei der Erzeugung feststehen; es gibt keinen Aufruf des Produkts im Orakel. Die Konfidenz ist
 * {@code min(Deckel des Kontexts, Stufe der Nummer)} mit den Deckeln 0,95 (direkt), 0,80 (Fuellwoerter, Umbruch,
 * Aufzaehlung ab der zweiten Nummer, nachgestellt, gesperrt) und 0,60 (nachgestellt mit Komma oder Semikolon) und den
 * Stufen 0,95 (gueltig), 0,50 (Pruefziffer falsch) und 0,40 (andere Form); die Quelle leitet
 * {@link KvnrTestHilfen#quelleNachNummerKvnr} aus der Nummer ab (Regel 9). Die Pruefziffer der erzeugten Nummern
 * rechnet die unabhaengige Referenz {@link KvnrBsnrHilfen#kvnrPruefziffer}; jede Nummer hat an Stelle 2 bis 5 die
 * Ziffern 0000 (A-6), also auch jede reine Ziffernfolge (Warnhinweis zu {@code scanKvnr}). Jeder Text geht durch
 * {@link KvnrTestHilfen#scanKvnr} (Waechter, Typ, Teilstring-Invariante, Sortierung, Ueberlappungsfreiheit) und wird
 * auf Determinismus geprueft.
 *
 * <p>Schreibweisen der Nummer: Gruppen der neun Ziffern stammen zur Haelfte aus sechs festen Zerlegungen, zur
 * Haelfte zufaellig aus allen 255 Zerlegungen mit mindestens zwei Gruppen (mit der ungruppierten Form alle 256).
 * Die Aufzaehlung trennt mit Komma, Semikolon, {@code und}, {@code /} mit Leerzeichen oder einem Zeilenumbruch (LF);
 * ab der zweiten Nummer gilt 0,80 (direkter Kontext nur fuer die erste).
 * Bildet eine Buchstabenform-Nummer hinter dem Buchstaben (und einem Trenner) vier Punkt-Gruppen zu je ein bis drei
 * Ziffern (IPv4-Gestalt, Nachtrag zu T-6), ist der Buchstabe kein Nummernanfang; der Generator ersetzt dann den
 * ersten Punkt zwischen den Gruppen durch einen Bindestrich (Zaehler {@code form.vierPunktGruppenUmgestellt}, der
 * Zufallsstrom bleibt gleich). Das Verhalten der Gestalt selbst pruefen die Einzeltests in {@link KvnrKontextTest}.
 *
 * <p>Negativbloecke (kein Fund erwartet): vier oder fuenf Fuellwoerter, Leerzeile, {@code KV-Nr.} (auch nachgestellt)
 * vor einer reinen Ziffernfolge, Woerter ausserhalb von D-2, Nummer ohne Kontext. Sie verwenden nur ungueltige
 * Nummern oder andere Formen (Regel 12).
 *
 * <p><b>Bewusste Grenze des Orakels:</b> Die Bloecke sind immer durch einen Absatz mit Leerzeile getrennt, damit kein
 * Kontextwort auf die Nummer eines anderen Blocks wirkt. Wechselwirkungen ueber Blockgrenzen (ein nachgestelltes
 * Kontextwort hinter der Nummer des vorigen Blocks, ein Kontext, der ueber den Block hinaus lebt) sind deshalb nicht
 * Gegenstand dieses Tests; sie decken die Einzeltests in {@link KvnrKontextTest} und {@link KvnrRecognizerTest} ab.
 * Innerhalb eines Blocks sind alle Nummern der Aufzaehlung, das nachgestellte Kontextwort und das nachgestellte
 * Kompositum ({@code Patienten-KVNR}, Nachtrag 2 zu T-5) Teil des Orakels.
 *
 * <p>Zaehler belegen, dass jede Schreibweise und jede Regel wirklich erzeugt wird; die Mindestzahlen stehen bei 70
 * Prozent oder weniger des Messwerts (Messwert im Kommentar, fester Seed). Die Selbsttests belegen, dass der
 * Vergleich eingebaute Fehler erkennt. Meldungen nennen nur Seed, Iteration, Fall und Zaehler, nie Text oder Ziffern.
 */
class KvnrEigenschaftTest {

    private static final long SEED = 20261301L;
    private static final long SEED_SELBSTTEST = 20261302L;
    private static final int TEXTE = 3_000;
    private static final int TEXTE_SELBSTTEST = 600;

    private static final double DIREKT = 0.95;
    private static final double WEITER = 0.80;
    private static final double KOMMA = 0.60;

    private static final String ABSATZ = "\n\nDanach folgt ein neuer Absatz mit Text.\n\n";
    private static final String[] SPERR_TRENNER = {" ", ".", "-", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), String.valueOf(GESCH_BINDESTRICH), String.valueOf(HALBGEVIERT)};
    private static final String[] WEIT_TRENNER = {". ", ".  ", "  ", "   "};
    private static final int[][] GRUPPEN_ZIFFERN = {{3, 3, 3}, {4, 5}, {5, 4}};
    private static final String[] DIREKT_TRENNER = {" ", ": ", ":", " : ", "=", "= ", "\t", String.valueOf(NBSP),
        "  ", ":  "};
    private static final String[] PRAEFIX = {"", "", "Patient, ", "Der Patient hat die ", "Daten: "};
    private static final String[] SUFFIX = {"", "", "", ".", ",", ";", ")", " Rest", ", geb.", "!", "?", ":", "-",
        "/", " -", "  Rest"};
    private static final String[] FUELLWOERTER = {"des", "Patienten", "Herrn", "Muster", "bitte", "siehe", "unten",
        "heute", "wurde"};
    private static final String[] UMBRUECHE = {"\n", "\r\n", "\r"};
    private static final String[] AUFZAEHLUNG = {", ", "; ", " und ", " / ", "\n"};
    private static final String[] FREMDWOERTER = {"VKNR", "KVNRX", "Versicherungsnummer", "Rentenversicherungsnummer",
        "KV", "Nr.", "KVID", "LANR", "IK"};
    private static final char[] UNSICHTBAR = {ZWSP, SHY, BOM, WORD_JOINER, LRM};
    private static final String[] SPERR_WOERTER = {"KVNR", "EGKNR"};
    private static final String PRAEFIX_WORT = "Patienten-";
    /** Das Wort vor dem Bindestrich eines nachgestellten Kompositums. */
    private static final String[] KOMPOSITUM_WOERTER = {"Patienten", "Pat.", "eGK", "Versicherter", "Herr"};

    /** Ein Kontextwort aus D-2; {@code kv} heisst: Vorwort KV, nur fuer die Buchstabenform. */
    private record Kw(String text, boolean kv) {
    }

    private static final Kw[] KWS = {new Kw("KVNR", false), new Kw("Versichertennummer", false),
        new Kw("Versichertennr", false), new Kw("Versichertennr.", false), new Kw("Krankenversichertennummer", false),
        new Kw("Krankenversichertennr", false), new Kw("Krankenversicherungsnummer", false),
        new Kw("Krankenversicherungsnr", false), new Kw("eGK-Nummer", false), new Kw("eGKNummer", false),
        new Kw("eGK-Nr.", false), new Kw("eGKNr", false), new Kw("Versicherten-Nr.", false),
        new Kw("Versicherten-Nummer", false), new Kw("Krankenversicherten-Nr.", false),
        new Kw("Krankenversicherten-Nummer", false), new Kw("eGK Nr.", false), new Kw("Versicherten Nr.", false),
        new Kw("Patienten-KVNR", false), new Kw("KV-Nr.", true), new Kw("KV-Nummer", true), new Kw("KV Nr.", true),
        new Kw("KV Nummer", true)};

    /** Nummernarten; die Zahl bestimmt die Gewichte der Auswahl. */
    private static final int GUELTIG = 0;
    private static final int UNGUELTIG = 1;
    private static final int FORM_BUCHSTABE = 2;
    private static final int FORM_ZIFFERN = 3;
    private static final String[] ART_NAME = {"gueltig", "ungueltig", "formBuchstabe", "formZiffern"};

    private record Nummer(String text, boolean buchstabenform, int art) {
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
        /** Die verschiedenen zufaellig gezogenen Zerlegungen der neun Ziffern (Bitmaske der Schnittstellen). */
        final Set<Integer> zerlegungen = new TreeSet<>();

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

    private static final int[] ALLE = {GUELTIG, UNGUELTIG, FORM_BUCHSTABE, FORM_ZIFFERN};
    private static final int[] ALLE_GEWICHT = {45, 20, 15, 20};
    private static final int[] BUCHSTABE = {GUELTIG, UNGUELTIG, FORM_BUCHSTABE};
    private static final int[] BUCHSTABE_GEWICHT = {50, 25, 25};
    private static final int[] NEGATIV = {UNGUELTIG, FORM_BUCHSTABE, FORM_ZIFFERN};
    private static final int[] NEGATIV_GEWICHT = {40, 30, 30};
    private static final int[] NEGATIV_BUCHSTABE = {UNGUELTIG, FORM_BUCHSTABE};
    private static final int[] NEGATIV_BUCHSTABE_GEWICHT = {50, 50};

    /**
     * Eine zufaellige Zerlegung der neun Ziffern in mindestens zwei Gruppen (Bitmaske 1 bis 255 ueber die acht
     * Schnittstellen; mit der ungruppierten Form sind das alle 256 Zerlegungen). Eine feste Zerlegung aus
     * {@code GRUPPEN} wird in der Haelfte der Faelle gezogen, die uebrigen Faelle stammen aus allen Zerlegungen.
     */
    private static int[] zerlegung(Random r, Statistik s, int ziffernAnzahl) {
        if (r.nextBoolean()) {
            int nr = r.nextInt(GRUPPEN.length);
            s.zaehle("form.gruppen." + nr);
            return GRUPPEN[nr];
        }
        int maske = 1 + r.nextInt((1 << (ziffernAnzahl - 1)) - 1);
        s.zerlegungen.add(maske);
        List<Integer> gruppen = new ArrayList<>();
        int laenge = 1;
        for (int i = 0; i < ziffernAnzahl - 1; i++) {
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

    /** Eine Nummer der Art {@code art}; die Schreibweise (Klein, Trenner, Gruppen) wird zufaellig gewaehlt. */
    private static Nummer nummer(Random r, Statistik s, int art) {
        s.zaehle("art." + ART_NAME[art]);
        if (art == FORM_ZIFFERN) {
            int n = 6 + r.nextInt(7);
            String z = "0000" + ziffern(r, n - 4);
            if (n == 9 && w(r, 40)) {
                s.zaehle("form.ziffernGruppiert");
                int[] zerlegung = w(r, 50) ? GRUPPEN_ZIFFERN[r.nextInt(GRUPPEN_ZIFFERN.length)] : zerlegung(r, s, n);
                return new Nummer(gruppiert(z, zerlegung, r, true), false, art);
            }
            s.zaehle("form.ziffernAnzahl." + n);
            return new Nummer(z, false, art);
        }
        char buchstabe = (char) ('A' + r.nextInt(26));
        String ziffern;
        if (art == FORM_BUCHSTABE) {
            int[] laengen = {6, 7, 8, 10, 11, 12};
            int n = laengen[r.nextInt(laengen.length)];
            s.zaehle("form.buchstabeAnzahl." + n);
            ziffern = "0000" + ziffern(r, n - 4);
        } else {
            String acht = "0000" + ziffern(r, 4);
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, acht);
            if (art == UNGUELTIG) {
                pruef = (pruef + 1 + r.nextInt(9)) % 10;
            }
            ziffern = acht + pruef;
        }
        boolean klein = w(r, 25);
        if (klein) {
            s.zaehle("form.klein");
            buchstabe = Character.toLowerCase(buchstabe);
        }
        StringBuilder sb = new StringBuilder().append(buchstabe);
        if (w(r, 40)) {
            String t = TRENNER[r.nextInt(TRENNER.length)];
            s.zaehle("form.buchstabeTrenner." + trennerName(t));
            sb.append(t);
        } else {
            s.zaehle("form.buchstabeGeschlossen");
        }
        if (ziffern.length() == 9 && w(r, 50)) {
            sb.append(gruppiert(ziffern, zerlegung(r, s, ziffern.length()), r, w(r, 50)));
        } else {
            sb.append(ziffern);
        }
        return new Nummer(ohneIpv4Gestalt(sb.toString(), s), true, art);
    }

    /** Vier Punkt-Gruppen zu je ein bis drei Ziffern, danach keine weitere Ziffer (IPv4-Gestalt, Nachtrag zu T-6). */
    private static final Pattern IPV4_GESTALT = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?!\\d).*",
            Pattern.DOTALL);

    /**
     * Die Buchstabenform soll hier die Regel "Buchstabe ist Nummernanfang" pruefen, nicht die IPv4-Gestalt hinter
     * dem Buchstaben (Nachtrag zu T-6: dort ist der Buchstabe kein Anfang, bekannte Grenze, Einzeltests in
     * {@link KvnrKontextTest}). Bilden die Gruppen hinter Buchstabe und optionalem Trenner diese Gestalt, wird der
     * erste Punkt zwischen den Gruppen durch einen Bindestrich ersetzt; der Zufallsstrom bleibt unveraendert.
     */
    private static String ohneIpv4Gestalt(String nummer, Statistik s) {
        int p = Character.isDigit(nummer.charAt(1)) ? 1 : 2;
        String rest = nummer.substring(p);
        if (!IPV4_GESTALT.matcher(rest).matches()) {
            return nummer;
        }
        s.zaehle("form.vierPunktGruppenUmgestellt");
        int punkt = p + rest.indexOf('.');
        return nummer.substring(0, punkt) + "-" + nummer.substring(punkt + 1);
    }

    private static String trennerName(String t) {
        return switch (t) {
            case " " -> "leerzeichen";
            case "-" -> "bindestrich";
            case "." -> "punkt";
            case "/" -> "schraegstrich";
            default -> "nbsp";
        };
    }

    // ------------------------------------------------------------------ Kontextwoerter

    private static String schreibung(Random r, Statistik s, String wort) {
        int v = r.nextInt(4);
        String[] name = {"original", "gross", "klein", "wechsel"};
        s.zaehle("schreibung." + name[v]);
        switch (v) {
            case 1:
                return wort.toUpperCase();
            case 2:
                return wort.toLowerCase();
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

    private static Kw kw(Random r, Statistik s) {
        int i = r.nextInt(KWS.length);
        s.zaehle("kw." + i);
        return KWS[i];
    }

    private static String kwText(Random r, Statistik s, Kw kw) {
        return schreibung(r, s, kw.text());
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
            String quelle = quelleNachNummerKvnr(n.text());
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

    /** Wahl der Nummer fuer ein Kontextwort: hinter {@code KV-Nr.} ist nur die Buchstabenform ein Fund. */
    private static int[][] mix(boolean buchstabeNoetig, boolean negativ) {
        if (negativ) {
            return buchstabeNoetig ? new int[][] {NEGATIV_BUCHSTABE, NEGATIV_BUCHSTABE_GEWICHT}
                    : new int[][] {NEGATIV, NEGATIV_GEWICHT};
        }
        return buchstabeNoetig ? new int[][] {BUCHSTABE, BUCHSTABE_GEWICHT} : new int[][] {ALLE, ALLE_GEWICHT};
    }

    private static Nummer wahl(Random r, Statistik s, int[][] m) {
        return nummer(r, s, wahlArt(r, m[0], m[1]));
    }

    private static void blockDirekt(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        Nummer n = wahl(r, s, mix(false, false));
        boolean erwartet = !k.kv() || n.buchstabenform();
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        if (n.buchstabenform() && w(r, 20)) {
            s.zaehle("unsichtbarVorBuchstabe");
            g.text.append(unsichtbar(r));
        }
        s.zaehle(erwartet ? "block.direkt" : "negativ.kvNrVorZiffern");
        g.bloecke.add(erwartet ? "direkt" : "negativKvZiffern");
        nummerAn(g, s, n, erwartet ? DIREKT : 0);
        suffix(g, r, s);
    }

    private static void blockFuellwoerter(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        Nummer n = wahl(r, s, mix(false, false));
        boolean erwartet = !k.kv() || n.buchstabenform();
        int anzahl = 1 + r.nextInt(3);
        s.zaehle("fuellwoerter." + anzahl);
        s.zaehle(erwartet ? "block.fuellwoerter" : "negativ.kvNrVorZiffern");
        g.bloecke.add(erwartet ? "fuellwoerter" : "negativKvZiffern");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        g.text.append(fuellwoerter(r, anzahl));
        nummerAn(g, s, n, erwartet ? WEITER : 0);
        suffix(g, r, s);
    }

    private static void blockVierFuellwoerter(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        Nummer n = wahl(r, s, mix(false, true));
        s.zaehle("negativ.vierFuellwoerter");
        g.bloecke.add("vierFuellwoerter");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(direktTrenner(r, s));
        g.text.append(fuellwoerter(r, 4 + r.nextInt(2)));
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockUmbruch(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        Nummer n = wahl(r, s, mix(false, false));
        boolean erwartet = !k.kv() || n.buchstabenform();
        g.text.append(praefix(r)).append(kwText(r, s, k));
        if (w(r, 50)) {
            g.text.append(':');
        }
        if (w(r, 30)) {
            g.text.append(' ');
        }
        int v = r.nextInt(n.buchstabenform() ? 5 : 4);
        String[] name = {"lf", "crlf", "cr", "crUzLf", "crUzDirekt"};
        s.zaehle("umbruch." + name[v]);
        if (v < 3) {
            g.text.append(UMBRUECHE[v]);
        } else if (v == 3) {
            g.text.append('\r').append(unsichtbar(r)).append('\n');
        } else {
            g.text.append('\r').append(unsichtbar(r));
        }
        if (v != 4 && w(r, 30)) {
            g.text.append(' ');
        }
        s.zaehle(erwartet ? "block.umbruch" : "negativ.kvNrVorZiffern");
        g.bloecke.add(erwartet ? "umbruch" : "negativKvZiffern");
        nummerAn(g, s, n, erwartet ? WEITER : 0);
        suffix(g, r, s);
    }

    private static void blockLeerzeile(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        Nummer n = wahl(r, s, mix(false, true));
        String[] leer = {"\n\n", "\r\n\r\n", "\n  \n", "\r\r", "\n\r\n"};
        int v = r.nextInt(leer.length);
        s.zaehle("negativ.leerzeile." + v);
        g.bloecke.add("leerzeile");
        g.text.append(praefix(r)).append(kwText(r, s, k)).append(w(r, 50) ? ":" : "").append(leer[v]);
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockAufzaehlung(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
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
            nummerAn(g, s, wahl(r, s, mix(true, false)), i == 0 ? DIREKT : WEITER);
        }
        suffix(g, r, s);
    }

    private static void blockNachgestellt(Gen g, Random r, Statistik s) {
        Kw k = kw(r, s);
        // Ein Wort, Bindestrich, Kontextwort (Patienten-KVNR) gilt hinter der Nummer als ein nachgestelltes
        // Kontextwort (Nachtrag 2 zu T-5); die Stufen sind dieselben wie beim einfachen Kontextwort.
        boolean eigenesKompositum = k.text().startsWith(PRAEFIX_WORT);
        boolean kompositum = !eigenesKompositum && w(r, 35);
        s.zaehle(eigenesKompositum ? "nachgestelltKw.kwKompositum" : kompositum ? "nachgestelltKw.kompositum"
                : "nachgestelltKw.einfach");
        Nummer n = wahl(r, s, mix(false, false));
        boolean erwartet = !k.kv() || n.buchstabenform();
        int v = r.nextInt(3);
        String[] name = {"klammer", "komma", "semikolon"};
        s.zaehle("nachgestellt." + name[v]);
        s.zaehle(erwartet ? "block.nachgestellt" : "negativ.kvNrVorZiffern");
        g.bloecke.add(erwartet ? "nachgestellt" : "negativKvZiffern");
        g.text.append(r.nextBoolean() ? "Wert " : "");
        nummerAn(g, s, n, erwartet ? (v == 0 ? WEITER : KOMMA) : 0);
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
        int form = r.nextInt(3);
        String sperr;
        Nummer n;
        String dsep = r.nextBoolean() ? " " : ": ";
        if (form == 0) {
            int t = r.nextInt(SPERR_TRENNER.length);
            s.zaehle("sperr.einzeichen." + t);
            sperr = sperrWort(r, s, wort, SPERR_TRENNER[t], false);
            n = wahl(r, s, mix(false, false));
            dsep = " ";
        } else {
            int t = r.nextInt(WEIT_TRENNER.length);
            s.zaehle("sperr.weit." + t);
            sperr = sperrWort(r, s, wort, WEIT_TRENNER[t], t < 2);
            n = wahl(r, s, mix(true, false));
        }
        g.bloecke.add("sperr");
        s.zaehle("block.sperr");
        s.zaehle(n.buchstabenform() ? "sperr.buchstabeform" : "sperr.zifferfolge");
        g.text.append(praefix(r)).append(sperr).append(dsep);
        nummerAn(g, s, n, WEITER);
        suffix(g, r, s);
    }

    private static void blockFremdwort(Gen g, Random r, Statistik s) {
        String wort = FREMDWOERTER[r.nextInt(FREMDWOERTER.length)];
        Nummer n = wahl(r, s, mix(false, true));
        s.zaehle("negativ.fremdwort");
        g.bloecke.add("fremdwort");
        g.text.append(praefix(r)).append(schreibung(r, s, wort)).append(w(r, 50) ? ": " : " ");
        nummerAn(g, s, n, 0);
        suffix(g, r, s);
    }

    private static void blockOhneKontext(Gen g, Random r, Statistik s) {
        Nummer n = wahl(r, s, mix(false, true));
        s.zaehle("negativ.ohneKontext");
        g.bloecke.add("ohneKontext");
        g.text.append(r.nextBoolean() ? "Patient " : "Der Wert ");
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
            if (!quelleNachNummerKvnr(e.wert()).equals(f.source()) || f.type() != FindingType.KVNR) {
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
        familie(s, "kw.", KWS.length, 120); // Ist 177 bis 245
        familie(s, "schreibung.", 4, 950); // Ist 1375 bis 1457
        familie(s, "art.", 4, 770); // Ist 1111 (formZiffern) bis 2780 (gueltig)
        einzeln(s, "art.gueltig", 1900); // Ist 2780
        einzeln(s, "art.ungueltig", 1270); // Ist 1822
        einzeln(s, "art.formBuchstabe", 1050); // Ist 1546
        familie(s, "aufzaehlungAnzahl.", 3, 130); // Ist 200 bis 211
        familie(s, "aufzaehlungTrenner.", AUFZAEHLUNG.length, 150); // Ist 215 bis 264 (je Trenner, auch / und LF)
        einzeln(s, "block.direkt", 800); // Ist 1171
        einzeln(s, "block.fuellwoerter", 540); // Ist 776
        einzeln(s, "block.umbruch", 520); // Ist 756
        einzeln(s, "block.aufzaehlung", 420); // Ist 612
        einzeln(s, "block.nachgestellt", 490); // Ist 709
        einzeln(s, "block.sperr", 380); // Ist 548
        einzeln(s, "blocktyp.vierFuellwoerter", 235); // Ist 354
        einzeln(s, "blocktyp.leerzeile", 245); // Ist 365
        einzeln(s, "blocktyp.fremdwort", 190); // Ist 281
        einzeln(s, "blocktyp.ohneKontext", 210); // Ist 331
        familie(s, "form.buchstabeAnzahl.", 6, 155); // Ist 231 bis 284
        familie(s, "form.ziffernAnzahl.", 7, 55); // Ist 94 (neun Ziffern) bis 178
        einzeln(s, "form.ziffernGruppiert", 38); // Ist 56
        einzeln(s, "form.buchstabeGeschlossen", 2500); // Ist 3662
        familie(s, "form.buchstabeTrenner.", 5, 325); // Ist 475 bis 529
        familie(s, "form.gruppen.", GRUPPEN.length, 110); // Ist 162 bis 228 (die sechs festen Zerlegungen)
        pruefeZerlegungen(s);
        einzeln(s, "form.klein", 1080); // Ist 1595
        familie(s, "fuellwoerter.", 3, 170); // Ist 250 bis 287
        familie(s, "umbruch.", 5, 79); // Ist 122 (crUzDirekt) bis 183
        einzeln(s, "umbruch.crUzLf", 105); // Ist 177
        einzeln(s, "unsichtbarVorBuchstabe", 114); // Ist 212
        familie(s, "nachgestellt.", 3, 155); // Ist 227 bis 256
        einzeln(s, "nachgestelltKw.einfach", 320); // Ist 462
        einzeln(s, "nachgestelltKw.kompositum", 160); // Ist 236 (Wort, Bindestrich, Kontextwort hinter der Nummer)
        einzeln(s, "nachgestelltKw.kwKompositum", 20); // Ist 30 (das Kontextwort der Liste ist selbst Patienten-KVNR)
        familie(s, "negativ.leerzeile.", 5, 44); // Ist 66 bis 83
        einzeln(s, "negativ.kvNrVorZiffern", 74); // Ist 122
        einzeln(s, "negativ.vierFuellwoerter", 235); // Ist 354
        einzeln(s, "negativ.fremdwort", 190); // Ist 281
        einzeln(s, "negativ.ohneKontext", 210); // Ist 331
        familie(s, "sperr.einzeichen.", SPERR_TRENNER.length, 10); // Ist 15 bis 25
        familie(s, "sperr.weit.", WEIT_TRENNER.length, 54); // Ist 87 bis 101
        familie(s, "sperr.wort.", SPERR_WOERTER.length, 185); // Ist 266 bis 282
        einzeln(s, "sperr.buchstabeform", 350); // Ist 504
        einzeln(s, "sperr.zifferfolge", 30); // Ist 44
        familie(s, "direktTrenner.", DIREKT_TRENNER.length, 200); // Ist 286 bis 320
        familie(s, "text.bloecke.", 3, 670); // Ist 962 bis 1051
        einzeln(s, "suffix", 2900); // Ist 4319
        einzeln(s, "fund", 4000); // Ist 5806
        einzeln(s, "konfidenz.0.95", 600); // Ist 880
        einzeln(s, "konfidenz.0.8", 1180); // Ist 1686
        einzeln(s, "konfidenz.0.6", 130); // Ist 214
        einzeln(s, "konfidenz.0.5", 900); // Ist 1295
        einzeln(s, "konfidenz.0.4", 1200); // Ist 1731
        einzeln(s, "quelle.kvnr", 1900); // Ist 2780
        einzeln(s, "quelle.kvnr-pruefziffer-ungueltig", 900); // Ist 1295
        einzeln(s, "quelle.kvnr-form-abweichend", 1200); // Ist 1731
        einzeln(s, "waechter.gueltigeFormen", 1900); // Ist 2790
        einzeln(s, "form.vierPunktGruppenUmgestellt", 45); // Ist 65 (IPv4-Gestalt hinter dem Buchstaben, ersetzt)
    }

    /**
     * Zufaellige Zerlegungen der neun Ziffern: Je Gruppenzahl von 2 bis 8 ein Zaehler (die Zerlegung in neun
     * Einzelziffern ist eine von 255 und kommt nur einmal vor), dazu die Zahl der verschiedenen Zerlegungen.
     */
    private static void pruefeZerlegungen(Statistik s) {
        int[] mindest = {23, 85, 170, 230, 180, 83, 21}; // Ist 33, 122, 247, 332, 260, 119, 30 fuer 2 bis 8 Gruppen
        for (int i = 0; i < mindest.length; i++) {
            einzeln(s, "form.zerlegung.gruppen." + (i + 2), mindest[i]);
        }
        einzeln(s, "form.zerlegungVerschieden", 176); // Ist 252 von 255 moeglichen
    }

    @Test
    void eigenschaft_texteAusBloecken_funde_entsprechenDerKonstruktion() {
        Random r = new Random(SEED);
        Statistik s = new Statistik();
        int gueltigeFormen = 0;
        int verstoesse = 0;
        for (int i = 0; i < TEXTE; i++) {
            Gen g = erzeuge(r, s);
            String text = g.text.toString();
            List<Finding> ist = scanKvnr(text);
            String fehler = abweichung(ist, g.erw);
            assertTrue(fehler.isEmpty(),
                    "Seed " + SEED + " Iteration " + i + " Bloecke " + g.bloecke + ": " + fehler);
            assertTrue(ist.equals(scanKvnr(text)), "Nicht deterministisch, Seed " + SEED + " Iteration " + i);
            KvnrSynthetischWaechter.Befund b = KvnrSynthetischWaechter.pruefe(text);
            verstoesse += b.verstoesse();
            gueltigeFormen += b.gueltigeFormen();
            s.zaehle("text");
        }
        assertEquals(0, verstoesse, "Waechter-Verstoesse (strikt 0)");
        s.z.put("waechter.gueltigeFormen", gueltigeFormen);
        s.z.put("form.zerlegungVerschieden", s.zerlegungen.size());
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
            List<Finding> ist = scanKvnr(g.text.toString());
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
        assertErkannt("keineFunde", funde -> List.of(), 370);
    }

    @Test
    void selbsttest_nurDerErsteFund_wirdErkannt() {
        assertErkannt("nurErster", funde -> funde.isEmpty() ? funde : funde.subList(0, 1), 225);
    }

    @Test
    void selbsttest_konfidenzImmerDirekt_wirdErkannt() {
        assertErkannt("konfidenzDirekt", funde -> mit(funde, f -> neu(f, f.end(), DIREKT, f.source())), 340);
    }

    @Test
    void selbsttest_endeEinsZuKurz_wirdErkannt() {
        assertErkannt("endeKurz", funde -> mit(funde, f -> neu(f, f.end() - 1, f.confidence(), f.source())), 370);
    }

    @Test
    void selbsttest_kleinbuchstabenVerworfen_wirdErkannt() {
        assertErkannt("ohneKlein", funde -> funde.stream()
                .filter(f -> !Character.isLowerCase(f.originalValue().charAt(0))).toList(), 135);
    }

    @Test
    void selbsttest_quelleImmerKvnr_wirdErkannt() {
        assertErkannt("quelleKvnr", funde -> mit(funde, f -> neu(f, f.end(), f.confidence(), "kvnr")), 255);
    }

    @Test
    void selbsttest_angehaengterFundInJedemText_wirdErkannt() {
        assertErkannt("zusatzFund", funde -> {
            List<Finding> aus = new ArrayList<>(funde);
            aus.add(new Finding(FindingType.KVNR, "x", 0, 1, DIREKT, "kvnr"));
            return aus;
        }, 420);
    }
}
