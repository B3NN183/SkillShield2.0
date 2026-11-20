package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests der Form UMGEBUNG (T-15) mit festem Startwert; Fehlermeldungen nennen nur Startwert und Iteration,
 * keine Texte. Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes. Die Ausgabe der Messwerte
 * ({@code System.out}) enthaelt nur Zahlen und Startwerte, nie Text. Das Orakel (Einzelform und Gesamtebene, beide
 * Richtungen, mit eigenen woertlichen Regeln) steht in {@link PathRecognizerRelativEigenschaftTest} (dort ist
 * UMGEBUNG aufgenommen).
 *
 * <p>(1) Differenztest (Obermengen-Garantie): Mit allen zehn Formen ist jede Position abgedeckt, die ein Durchlauf nur
 * mit den neun Formen des Stands T-14 abdeckt, mit und ohne Anfuehrungszeichenregel, mit und ohne Anfuehrungszeichen im
 * Text. Gemessen und ausgegeben wird, wie viele bestehende Funde eine niedrigere Konfidenz bekommen: bei gleichen
 * Grenzen (ohne Anfuehrungszeichen im Text muss es 0 sein; mit Anfuehrungszeichen hat jeder Fall die Ursache, dass ein
 * Fund der Form UMGEBUNG im Zitat-Durchlauf die niedrigere Konfidenz traegt) und bei groesseren Grenzen (Plan Nachtrag
 * T-12 Punkt 7a (ii), T-14 Nachtrag 5 (c)).
 * (2) Obermenge je Form auf dichten kurzen Texten.
 * (3) Ueber {@code TextScanner.standard()}, {@code Pseudonymizer} und {@code Reidentifier}: jeder erzeugte Pfad liegt
 * ganz in einem Fund, kein Pfadbestandteil steht im pseudonymisierten Text, der Roundtrip ist zeichengleich; jede
 * URL (auch mit {@code %20}, {@code %2F} und {@code ?x=%USERPROFILE%/y}) ist genau ein URL-Fund.
 * (4) Jede allein stehende URL bleibt genau ein URL-Fund; die URL-Kodierung {@code %XX} erzeugt keinen Kopf.
 */
class PathRecognizerUmgebungEigenschaftTest {

    private static final long SEED = 20261201L;
    private static final int ITERATIONEN_DIFFERENZ = 100_000;
    private static final int ITERATIONEN_PIPELINE = 15_000;

    private static final PathRecognizer R = new PathRecognizer();
    private static final PathForm[] STAND_T14 = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME,
            PathForm.HOST_PFAD, PathForm.RELATIV, PathForm.EINZEL_BACKSLASH, PathForm.BENUTZER_WURZEL,
            PathForm.WURZEL_ZWEI_SEGMENTE};
    private static final PathForm[] NUR_UMGEBUNG = {PathForm.UMGEBUNG};
    private static final Set<String> QUELLEN = Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home",
            "pfad-host", "pfad-relativ", "pfad-umgebung");
    private static final Set<Double> KONFIDENZEN = Set.of(0.95, 0.90, 0.80, 0.75, 0.60);

    /** Die 13 Anfuehrungszeichen-Arten (Escapes). */
    private static final String[] ANFUEHRUNG = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB",
            "\u2018", "\u2019", "\u201A", "\u2039", "\u203A"};
    private static final String ANFUEHRUNG_ALLE = "\"'`\u201E\u201C\u201D\u00BB\u00AB\u2018\u2019\u201A\u2039\u203A";

    /** Mischung aus Tokens der Stufen T-10 bis T-14 und Variablen-Tokens (Koepfe, Nicht-Koepfe, Ketten, Batch). */
    private static final String[] TOKEN = {"/", "//", "/a/b", "~/", "~/x", "\\", "\\a", "\\a\\b", "\\\\", "\\\\srv",
            "C:\\", "d:/", "host:/", ".", "..", "./", "../", ".\\", "Users\\", "home/", "Benutzer/", "a", "b", "x",
            "Mein", " ", " ", " ", " ", "(", ")", "[", "]", "{", "}", "=", ",", ";", ":", "!", "?", "*", "-", "_", "@",
            "1", "9",
            "\t", "\n", "<", ">", "|", "\uD83D\uDE00", "e\u0301", "\u00A0", "\u200B", "\u2028",
            "$", "%", "$HOME/", "$a", "${H}/", "${", "$(p)/", "%A%\\", "%a%%b%\\", "%a%", "%~dp0", "%~dp0..\\", "%~f1/",
            "%~", "$env:A\\", "${env:A}/", "${env:P(x)}\\", "%P(x)%/", "$ENV/", "$1/", "%1\\", "%d/", "%%/", "${}/",
            "%a%%b%%c%%d%%e%\\", "%~dpnxsatz0/", "$env:", "${env:", "%20", "%2F", "%E4%B8%AD", "$USD/", "}", "%s/",
            "!A!\\", "!a!/", "!a!!b!\\", "!!/", "!1!/", "!P(x)!\\", "%~dp0!a!/"};

    /** Dichte kurze Bausteine fuer Texte mit vielen Koepfen auf engem Raum. */
    private static final String[] DICHT = {"$", "%", "a", "b", "/", "\\", "{", "}", "(", ")", "~", "d", "p", "0", ":",
            "env", " ", "\"", "'", "%a%", "$a", "${a}", "$(a)", "%~dp0", ".", "x", "!", "!a!"};

    // ------------------------------------------------------------------ (1) Differenztest

    @Test
    void differenzTestAlleFormenDeckenAbWasStandT14AbdecktMitAnfuehrungszeichenImText() {
        differenz(SEED, 4);
    }

    @Test
    void differenzTestAlleFormenDeckenAbWasStandT14AbdecktOhneAnfuehrungszeichenImText() {
        differenz(SEED + 1, 0);
    }

    private void differenz(long seed, int anfuehrungsAnteil) {
        Random rnd = new Random(seed);
        int echtGroesser = 0;
        int mitUmgebung = 0;
        long altFunde = 0;
        long gleicheGrenzenNiedriger = 0;
        long groesserNiedriger = 0;
        long gleicheGrenzenGleich = 0;
        long groesserGleichOderHoeher = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd, 30, anfuehrungsAnteil, TOKEN);
            String wo = ", Startwert " + seed + ", Iteration " + it;
            List<Finding> neu = R.detect(text);
            List<Finding> alt = R.detect(text, STAND_T14);
            pruefeFunde(text, neu, wo);
            pruefeFunde(text, alt, wo);
            boolean[] a = abdeckung(text.length(), neu);
            boolean[] b = abdeckung(text.length(), alt);
            boolean groesser = false;
            for (int i = 0; i < text.length(); i++) {
                if (b[i] && !a[i]) {
                    fail("Position nicht mehr abgedeckt" + wo);
                }
                groesser |= a[i] && !b[i];
            }
            boolean[] aOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] bOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, STAND_T14));
            for (int i = 0; i < text.length(); i++) {
                if (bOhne[i] && !aOhne[i]) {
                    fail("Position ohne Zitatregel nicht mehr abgedeckt" + wo);
                }
            }
            if (groesser) {
                echtGroesser++;
            }
            for (Finding n : neu) {
                if (n.source().equals("pfad-umgebung")) {
                    mitUmgebung++;
                    break;
                }
            }
            for (Finding f : alt) {
                altFunde++;
                Finding g = enthaltender(neu, f);
                assertNotNull(g, "kein enthaltender Fund" + wo);
                boolean gleich = g.start() == f.start() && g.end() == f.end();
                if (g.confidence() < f.confidence()) {
                    if (gleich) {
                        gleicheGrenzenNiedriger++;
                        pruefeUrsacheDerAbsenkung(text, f, g, wo);
                    } else {
                        groesserNiedriger++;
                    }
                } else if (gleich) {
                    gleicheGrenzenGleich++;
                } else {
                    groesserGleichOderHoeher++;
                }
            }
        }
        System.out.println("T-15 Differenztest (Startwert " + seed + ", Anfuehrungsanteil " + anfuehrungsAnteil + "): "
                + "Texte " + ITERATIONEN_DIFFERENZ + ", bestehende Funde " + altFunde
                + ", gleiche Grenzen/Konfidenz gleich " + gleicheGrenzenGleich
                + ", gleiche Grenzen/niedriger " + gleicheGrenzenNiedriger
                + ", groessere Grenzen/Konfidenz gleich oder hoeher " + groesserGleichOderHoeher
                + ", groessere Grenzen/niedriger " + groesserNiedriger
                + ", Texte mit echter Vergroesserung " + echtGroesser
                + ", Texte mit Fund pfad-umgebung " + mitUmgebung);
        if (anfuehrungsAnteil == 0) {
            assertEquals(0L, gleicheGrenzenNiedriger, "Konfidenz bei gleichen Grenzen gesunken");
        }
        assertTrue(echtGroesser > ITERATIONEN_DIFFERENZ / 50, "zu wenige echte Vergroesserungen: " + echtGroesser);
        assertTrue(mitUmgebung > ITERATIONEN_DIFFERENZ / 5, "zu wenige Texte mit Umgebungs-Fund: " + mitUmgebung);
    }

    /**
     * Ursache einer Absenkung bei gleichen Grenzen (Plan 7a (ii)): Der Text enthaelt ein Anfuehrungszeichen (nur das
     * Zitat-Paar kann einen frueheren ersten Kopf mit demselben Ende liefern), und ein Durchlauf nur mit der Form
     * UMGEBUNG liefert einen den Fund beruehrenden Fund mit niedrigerer Konfidenz als der bestehende Fund.
     */
    private static void pruefeUrsacheDerAbsenkung(String text, Finding alt, Finding neu, String wo) {
        boolean anfuehrung = false;
        for (int i = 0; i < text.length(); i++) {
            anfuehrung |= ANFUEHRUNG_ALLE.indexOf(text.charAt(i)) >= 0;
        }
        assertTrue(anfuehrung, "Absenkung bei gleichen Grenzen ohne Anfuehrungszeichen im Text" + wo);
        boolean ursache = false;
        for (Finding x : R.detect(text, NUR_UMGEBUNG)) {
            if (x.start() < neu.end() && neu.start() < x.end() && x.confidence() < alt.confidence()) {
                ursache = true;
            }
        }
        assertTrue(ursache, "Absenkung bei gleichen Grenzen ohne Fund der Form UMGEBUNG als Ursache" + wo);
    }

    /** Der Fund aus {@code neu}, der {@code f} ganz enthaelt (Obermenge). */
    private static Finding enthaltender(List<Finding> neu, Finding f) {
        for (Finding g : neu) {
            if (g.start() <= f.start() && g.end() >= f.end()) {
                return g;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ (2) dichte kurze Texte, Obermenge je Form

    @Test
    void obermengeAufDichtenKurzenTextenFuerJedeFormUndStandT14() {
        Random rnd = new Random(SEED + 2);
        for (int it = 0; it < 150_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(12);
            for (int k = 0; k < len; k++) {
                sb.append(DICHT[rnd.nextInt(DICHT.length)]);
            }
            String text = sb.toString();
            String wo = ", Startwert " + (SEED + 2) + ", Iteration " + it;
            boolean[] alle = abdeckung(text.length(), R.detect(text));
            boolean[] alleOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] t14 = abdeckung(text.length(), R.detect(text, STAND_T14));
            for (int i = 0; i < text.length(); i++) {
                if (t14[i] && !alle[i]) {
                    fail("Stand T-14: Position nicht mehr abgedeckt" + wo);
                }
            }
            for (PathForm form : PathForm.values()) {
                boolean[] einzeln = abdeckung(text.length(), R.detect(text, new PathForm[] {form}));
                boolean[] einzelnOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, new PathForm[] {form}));
                for (int i = 0; i < text.length(); i++) {
                    if (einzeln[i] && !alle[i]) {
                        fail(form + ": Position nicht mehr abgedeckt" + wo);
                    }
                    if (einzelnOhne[i] && !alleOhne[i]) {
                        fail(form + ": Position ohne Zitatregel nicht abgedeckt" + wo);
                    }
                }
            }
        }
    }

    /**
     * Belege des Javadocs (PathForm.UMGEBUNG und PathRecognizer): Ueberlappende Koepfe haben dasselbe Kopfende, und
     * hoechstens 28 Koepfe teilen ein Kopfende (Schranke 22 Batch plus 4 Kette plus 1 {@code $} plus 1 {@code !}; die
     * Messung unten erreicht je Variante hoechstens deren Schranke); jeder Kopf beginnt mit {@code %}, {@code $} oder
     * {@code !}.
     */
    @Test
    void ueberlappendeKoepfeHabenDasselbeKopfendeUndHoechstensAchtundzwanzigTeilenEines() {
        Random rnd = new Random(SEED + 4);
        int maximum = 0;
        for (int it = 0; it < 200_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(30);
            for (int k = 0; k < len; k++) {
                sb.append(DICHT[rnd.nextInt(DICHT.length)]);
            }
            maximum = Math.max(maximum, pruefeKoepfe(sb.toString(), ", Startwert " + (SEED + 4) + ", Iteration " + it));
        }
        // gezielt: dichteste Folgen von Batch-Koepfen und Ketten
        int batch = pruefeKoepfe("%~0".repeat(60) + "/x", "Batch-Folge");
        assertEquals(22, batch, "Batch-Koepfe mit hoechstens 64 Zeichen Rest vor dem Trenner");
        assertEquals(4, pruefeKoepfe("%a%".repeat(10) + "\\x", "Kette"), "Koepfe einer langen Kette: hoechstens vier");
        int gemischt = pruefeKoepfe("%~0".repeat(30) + "%a%%b%%c%%d%%e%$f" + "/x", "gemischt");
        assertTrue(gemischt <= 28, "gemischt: " + gemischt);
        // jede Variante fuer sich und alle zusammen: je Kopfende hoechstens die Schranke der Variante (je 1 fuer $, !)
        assertEquals(1, pruefeKoepfe("!a!/x", "!"), "ein !-Kopf je Kopfende");
        assertEquals(1, pruefeKoepfe("!a!!b!/x", "!-Folge"), "ein !-Kopf je Kopfende (keine Kette)");
        assertEquals(1, pruefeKoepfe("$a/x", "$"), "ein $-Kopf je Kopfende");
        int alle = pruefeKoepfe("%~0".repeat(30) + "%a%%b%%c%%d%$f!g!" + "/x", "alle Varianten");
        assertTrue(alle <= 28, "alle Varianten: " + alle);
        assertTrue(maximum <= 28, "Koepfe je Kopfende: " + maximum);
    }

    /** Prueft die Behauptungen fuer einen Text und liefert die groesste Zahl von Koepfen mit gleichem Kopfende. */
    private static int pruefeKoepfe(String text, String wo) {
        List<int[]> koepfe = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            int e = PathForm.UMGEBUNG.headEnd(text, i);
            if (e >= 0) {
                char c = text.charAt(i);
                assertTrue(c == '%' || c == '$' || c == '!', "Kopf beginnt mit %, $ oder !" + wo);
                assertTrue(c != '/' && c != '\\' && (text.charAt(e - 1) == '/' || text.charAt(e - 1) == '\\'),
                        "Kopf endet mit einem Trenner" + wo);
                koepfe.add(new int[] {i, e});
            }
        }
        int groesste = 0;
        for (int[] k1 : koepfe) {
            int gleich = 0;
            for (int[] k2 : koepfe) {
                if (k2[0] > k1[0] && k2[0] < k1[1]) {
                    assertEquals(k1[1], k2[1], "ueberlappende Koepfe haben dasselbe Kopfende" + wo);
                }
                gleich += k2[1] == k1[1] ? 1 : 0;
            }
            groesste = Math.max(groesste, gleich);
        }
        return groesste;
    }

    /** Fund der Form UMGEBUNG im Zitat liest nie hinter das Zitat: Zitat-Funde bleiben bei Text dahinter gleich. */
    @Test
    void zitatFundeAendernSichDurchTextHinterDemSchliesszeichenNie() {
        Random rnd = new Random(SEED + 3);
        PathForm[] formen = PathForm.values();
        int mitFund = 0;
        for (int it = 0; it < 60_000; it++) {
            String q = ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)];
            String inhalt = zufallstext(rnd, 1 + rnd.nextInt(6), 0, TOKEN);
            String tail = zufallstext(rnd, 1 + rnd.nextInt(4), 0, TOKEN);
            String kurz = q + inhalt + q;
            List<Finding> erwartet = PathRecognizer.collectQuoted(kurz, formen);
            List<Finding> ist = PathRecognizer.collectQuoted(kurz + tail, formen);
            assertEquals(erwartet, ist, "Zitat-Funde aendern sich durch den Text dahinter, Startwert " + (SEED + 3)
                    + ", Iteration " + it);
            mitFund += erwartet.isEmpty() ? 0 : 1;
        }
        assertTrue(mitFund > 2_000, "zu wenige Zitat-Funde: " + mitFund);
    }

    // ------------------------------------------------------------------ Hilfen

    private static String zufallstext(Random rnd, int maxTokens, int anfuehrungsAnteil, String[] tokens) {
        StringBuilder sb = new StringBuilder();
        int n = 1 + rnd.nextInt(maxTokens);
        for (int k = 0; k < n; k++) {
            if (anfuehrungsAnteil > 0 && rnd.nextInt(anfuehrungsAnteil + 1) == 0) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
            } else {
                sb.append(tokens[rnd.nextInt(tokens.length)]);
            }
        }
        return sb.toString();
    }

    private static boolean[] abdeckung(int laenge, List<Finding> funde) {
        boolean[] a = new boolean[laenge];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    /** Teilstring, Typ, Quelle und Konfidenz als Menge, sortiert, ueberlappungsfrei, keine Grenze im Surrogatpaar. */
    private static void pruefeFunde(String text, List<Finding> funde, String wo) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
            assertEquals(FindingType.PFAD, f.type(), "Typ" + wo);
            assertTrue(QUELLEN.contains(f.source()), "Quelle " + f.source() + wo);
            assertTrue(KONFIDENZEN.contains(f.confidence()), "Konfidenz " + f.confidence() + wo);
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei" + wo);
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar" + wo);
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar" + wo);
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ (3) Pipeline

    private record Paar(String auf, String zu) {
    }

    private static final Paar[] PAARE = {new Paar("\"", "\""), new Paar("'", "'"), new Paar("`", "`"),
            new Paar("\u201E", "\u201C"), new Paar("\u201C", "\u201D"), new Paar("\u00BB", "\u00AB"),
            new Paar("\u2018", "\u2019"), new Paar("\u2039", "\u203A")};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "oben", "folgt", "nichts", "weiter", "und/oder", "km/h", "19/11/2026", "TCP/IP", "n/a",
            "50%/Jahr", "3%/4%", "5$/Stueck", "100 %/Monat", "%d/%m/%Y", "$1/2", "Preis: 5$/Monat", "%s/%s"};
    /** Praefixe mit Startgrenze (Plan 2.4.1); fuer alle Pfadformen gueltig. */
    private static final String[] VOR = {"", "", " ", "x=", "(", "[", "{", "--config=", ">", "\n", "ab ", "Datei: ",
            "\t", "<", "Pfad ", "-> ", ",", ";", "|", "a,", "cmd|"};
    /** Praefixe ohne Startgrenze: nur die Form UMGEBUNG (sowie LAUFWERK und UNC) braucht keine. */
    private static final String[] VOR_OHNE_GRENZE = {"-I", "Pfad:", "abc", "a\\n", "7", "$", "x%"};
    private static final String[] NACH = {"", "", ".", ",", ";", "!", "?", " Ende", "\n", "\r\n"};
    private static final String[] BASIS = {"Tools", "Daten", "Akten", "Projekt", "Gr\u00F6\u00DFe", "\u00DCbersicht",
            "BeispielTool", "e\u0301cole", "home", "Dokumente", "erika.beispiel"};
    private static final String[] DATEI = {"akte.pdf", "tool.exe", "notiz.txt", "konfig.yml", "a.tar.gz", "x"};
    private static final String[] VARIABLEN = {"USERPROFILE", "APPDATA", "HOME", "XDG_CONFIG_HOME", "ProgramFiles",
            "TEMP", "SystemRoot", "_x1", "a"};
    private static final String[] KOEPFE_UEBERGANG = {"..\\", "\\", "../"};

    private record Gen(String text, List<int[]> pfade, List<String> namen, List<int[]> urls) {
    }

    private static String name(Random rnd, boolean mitLeerzeichen) {
        String n = BASIS[rnd.nextInt(BASIS.length)] + rnd.nextInt(10_000);
        if (mitLeerzeichen && rnd.nextInt(3) == 0) {
            n = "Mein Ordner" + rnd.nextInt(10_000);
        }
        return n;
    }

    private static String segmente(Random rnd, boolean zitat, List<String> namen, int anzahl, String trenner) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < anzahl; s++) {
            String n = name(rnd, zitat);
            if (s == anzahl - 1 && rnd.nextBoolean()) {
                n = n.replace(' ', '_') + "." + DATEI[rnd.nextInt(DATEI.length)] + rnd.nextInt(100);
            }
            namen.add(n);
            sb.append(s > 0 ? (rnd.nextInt(7) == 0 ? "/" : trenner) : "").append(n);
        }
        return sb.toString();
    }

    /** Ein Variablen-Pfad aller Kopfvarianten; der Trenner dahinter ist {@code \} oder {@code /}. */
    private static String umgebungsPfad(Random rnd, boolean zitat, List<String> namen) {
        String v = VARIABLEN[rnd.nextInt(VARIABLEN.length)];
        String v2 = VARIABLEN[rnd.nextInt(VARIABLEN.length)];
        boolean bs = rnd.nextBoolean();
        String t = bs ? "\\" : "/";
        String kopf;
        switch (rnd.nextInt(11)) {
            case 0:
                kopf = "%" + v + "%" + t;
                break;
            case 1:
                kopf = "%" + v + "%%" + v2 + "%" + t;
                break;
            case 2:
                kopf = "$" + v + t;
                break;
            case 3:
                kopf = "${" + v + "}" + t;
                break;
            case 4:
                kopf = "$(" + v + ")" + t;
                break;
            case 5:
                kopf = "$env:" + v + t;
                break;
            case 6:
                kopf = "${env:" + v + "}" + t;
                break;
            case 7:
                kopf = "%~dp0" + KOEPFE_UEBERGANG[rnd.nextInt(KOEPFE_UEBERGANG.length)];
                break;
            case 8:
                kopf = "%~f1" + t;
                break;
            case 9:
                kopf = "!" + v + "!" + t;
                break;
            default:
                kopf = "%ProgramFiles(x86)%" + t;
                break;
        }
        return kopf + segmente(rnd, zitat, namen, 1 + rnd.nextInt(4), t);
    }

    private static String andererPfad(Random rnd, boolean zitat, List<String> namen) {
        switch (rnd.nextInt(5)) {
            case 0:
                return "C:\\" + segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), "\\");
            case 1:
                String server = "srv-beispiel" + rnd.nextInt(1000);
                namen.add(server);
                return "\\\\" + server + "\\" + segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), "\\");
            case 2:
                return "/" + segmente(rnd, zitat, namen, 2 + rnd.nextInt(3), "/");
            case 3:
                return "~/" + segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), "/");
            default:
                return "./" + segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), "/");
        }
    }

    /** URLs, auch mit URL-Kodierung und Variablen in der Query. */
    private static String url(Random rnd) {
        String[] teile = {"a%20b", "%2F", "%E4%B8%AD", "%C3%A4", "seite", "a/b/c", "%41%42", "x%25y", "%e4%b8%ad"};
        StringBuilder sb = new StringBuilder("https://example.invalid");
        int n = 1 + rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            sb.append('/').append(teile[rnd.nextInt(teile.length)]);
        }
        String[] queries = {"", "", "?x=%USERPROFILE%/y", "?q=%41%42&r=$HOME/a", "?p=${HOME}/a/b", "?f=%~dp0../a",
                "?a=%20&b=%2F", "?v=%25APPDATA%25/x"};
        return sb.append(queries[rnd.nextInt(queries.length)]).toString();
    }

    private static Gen erzeuge(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        List<int[]> urls = new ArrayList<>();
        int anzahl = 1 + rnd.nextInt(4);
        for (int p = 0; p < anzahl; p++) {
            int fuell = rnd.nextInt(3);
            for (int k = 0; k < fuell; k++) {
                sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
            }
            int art = rnd.nextInt(12);
            if (art == 0) {
                int start = sb.length();
                sb.append(url(rnd));
                urls.add(new int[] {start, sb.length()});
                sb.append(rnd.nextBoolean() ? " " : "\n");
                continue;
            }
            if (art == 1) {
                sb.append("erika.mustermann@example.invalid").append(" ");
                continue;
            }
            boolean zitat = rnd.nextInt(3) == 0;
            Paar paar = PAARE[rnd.nextInt(PAARE.length)];
            List<String> pfadNamen = new ArrayList<>();
            boolean umgebung = art < 9;
            String pfad = umgebung ? umgebungsPfad(rnd, zitat, pfadNamen) : andererPfad(rnd, zitat, pfadNamen);
            if (zitat) {
                sb.append(paar.auf());
            } else if (umgebung && rnd.nextInt(4) == 0) {
                sb.append(VOR_OHNE_GRENZE[rnd.nextInt(VOR_OHNE_GRENZE.length)]);
            } else {
                sb.append(VOR[rnd.nextInt(VOR.length)]);
            }
            int start = sb.length();
            sb.append(pfad);
            pfade.add(new int[] {start, sb.length()});
            sb.append(zitat ? paar.zu() : NACH[rnd.nextInt(NACH.length)]);
            namen.addAll(pfadNamen);
            sb.append(rnd.nextBoolean() ? " " : "\n");
        }
        return new Gen(sb.toString(), pfade, namen, urls);
    }

    private static boolean deckt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
    }

    @Test
    void zufaelligeVariablenPfadeSindGanzAbgedecktOhneKlartextUndMitRoundtrip() {
        Random rnd = new Random(SEED + 7);
        TextScanner scanner = TextScanner.standard();
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        int umgebungsFunde = 0;
        int urlsStreng = 0;
        for (int it = 0; it < ITERATIONEN_PIPELINE; it++) {
            Gen g = erzeuge(rnd);
            String text = g.text();
            String wo = ", Startwert " + (SEED + 7) + ", Iteration " + it;
            List<Finding> funde = scanner.scan(text);

            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
                assertTrue(f.start() >= vorigesEnde, "Ueberlappung" + wo);
                vorigesEnde = f.end();
                if (f.source().equals("pfad-umgebung")) {
                    umgebungsFunde++;
                }
            }
            for (int[] pfad : g.pfade()) {
                assertTrue(deckt(funde, pfad[0], pfad[1]), "Pfad nicht ganz in einem Fund" + wo);
            }
            boolean ohneAnfuehrung = true;
            for (int i = 0; i < text.length(); i++) {
                ohneAnfuehrung &= ANFUEHRUNG_ALLE.indexOf(text.charAt(i)) < 0;
            }
            for (int[] url : g.urls()) {
                int anzahl = 0;
                for (Finding f : funde) {
                    if (f.start() < url[1] && url[0] < f.end()) {
                        anzahl++;
                        if (ohneAnfuehrung) {
                            urlsStreng++;
                            assertEquals(FindingType.URL, f.type(), "URL-Typ" + wo);
                            assertEquals(url[0], f.start(), "URL-Start" + wo);
                            assertEquals(url[1], f.end(), "URL-Ende" + wo);
                        } else {
                            assertTrue(f.start() <= url[0] && f.end() >= url[1], "URL ganz in einem Fund" + wo);
                        }
                    }
                }
                assertEquals(1, anzahl, "ein Fund je URL, keine Zersplitterung" + wo);
            }

            PseudonymizationResult p = pseudo.pseudonymize(text, funde);
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip" + wo);
            assertTrue(back.unresolved().isEmpty(), "Meldung" + wo);
            for (String n : g.namen()) {
                assertFalse(p.text().contains(n), "Name im Klartext" + wo);
            }
            for (int[] pfad : g.pfade()) {
                assertFalse(p.text().contains(text.substring(pfad[0], pfad[1])), "Pfad im Klartext" + wo);
            }
        }
        assertTrue(umgebungsFunde > ITERATIONEN_PIPELINE, "zu wenige Umgebungs-Funde im Test: " + umgebungsFunde);
        assertTrue(urlsStreng > 500, "zu wenige URLs ohne Anfuehrungszeichen im Text: " + urlsStreng);
    }

    /**
     * Messung auf realistisch erzeugten Texten (Pfade durch Leerraum getrennt, mit Fuelltext, Variablen-Pfade):
     * bestehende
     * Funde des Stands T-14 behalten Grenzen und Konfidenz. Die Zahl der Absenkungen bei groesseren Grenzen wird
     * ausgegeben.
     */
    @Test
    void konfidenzBestehenderFundeAufRealistischenTexten() {
        Random rnd = new Random(SEED + 10);
        long altFunde = 0;
        long gleicheGrenzenNiedriger = 0;
        long groesserNiedriger = 0;
        long groesser = 0;
        for (int it = 0; it < 30_000; it++) {
            String text = erzeuge(rnd).text();
            List<Finding> neu = R.detect(text);
            for (Finding f : R.detect(text, STAND_T14)) {
                altFunde++;
                Finding g = enthaltender(neu, f);
                assertNotNull(g, "kein enthaltender Fund, Startwert " + (SEED + 10) + ", Iteration " + it);
                boolean gleich = g.start() == f.start() && g.end() == f.end();
                groesser += gleich ? 0 : 1;
                if (g.confidence() < f.confidence()) {
                    if (gleich) {
                        gleicheGrenzenNiedriger++;
                        pruefeUrsacheDerAbsenkung(text, f, g, ", Startwert " + (SEED + 10) + ", Iteration " + it);
                    } else {
                        groesserNiedriger++;
                    }
                }
            }
        }
        System.out.println("T-15 realistische Texte (Startwert " + (SEED + 10) + "): bestehende Funde " + altFunde
                + ", groessere Grenzen " + groesser + ", gleiche Grenzen/niedriger " + gleicheGrenzenNiedriger
                + ", groessere Grenzen/niedriger " + groesserNiedriger);
        assertTrue(groesserNiedriger * 100 < altFunde, "zu viele Absenkungen bei groesseren Grenzen");
    }

    // ------------------------------------------------------------------ (4) allein stehende URLs

    @Test
    void jedeAlleinStehendeUrlBleibtGenauEinUrlFund() {
        Random rnd = new Random(SEED + 8);
        TextScanner scanner = TextScanner.standard();
        int mitVariable = 0;
        for (int it = 0; it < 40_000; it++) {
            String url = url(rnd);
            String wo = ", Startwert " + (SEED + 8) + ", Iteration " + it;
            List<Finding> s = scanner.scan(url);
            assertEquals(1, s.size(), "genau ein Fund" + wo);
            assertEquals(FindingType.URL, s.get(0).type(), "Typ" + wo);
            assertEquals(0, s.get(0).start(), "Start" + wo);
            assertEquals(url.length(), s.get(0).end(), "Ende" + wo);
            mitVariable += url.contains("=%") || url.contains("=$") ? 1 : 0;
            // reine URL-Kodierung erzeugt keinen Kopf der Form UMGEBUNG
            if (!url.contains("?")) {
                assertEquals(List.of(), R.detect(url, NUR_UMGEBUNG), "Kopf aus URL-Kodierung" + wo);
            }
        }
        assertTrue(mitVariable > 5_000, "zu wenige URLs mit Variable in der Query: " + mitVariable);
    }

    @Test
    void zufaelligeGueltigeUrlKodierungErzeugtNieEinenKopfDerFormUmgebung() {
        Random rnd = new Random(SEED + 9);
        String hex = "0123456789ABCDEFabcdef";
        String lit = "abcXYZ019-_.~/";
        for (int it = 0; it < 60_000; it++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + rnd.nextInt(40);
            for (int i = 0; i < n; i++) {
                if (rnd.nextBoolean()) {
                    sb.append('%').append(hex.charAt(rnd.nextInt(hex.length())))
                            .append(hex.charAt(rnd.nextInt(hex.length())));
                } else {
                    sb.append(lit.charAt(rnd.nextInt(lit.length())));
                }
            }
            String text = sb.toString();
            assertEquals(List.of(), R.detect(text, NUR_UMGEBUNG), "Kopf aus gueltiger Kodierung, Startwert "
                    + (SEED + 9) + ", Iteration " + it);
        }
    }
}
