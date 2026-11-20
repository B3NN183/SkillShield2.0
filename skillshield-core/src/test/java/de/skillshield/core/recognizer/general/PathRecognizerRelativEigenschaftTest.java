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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests der Formen RELATIV, EINZEL_BACKSLASH, BENUTZER_WURZEL und WURZEL_ZWEI_SEGMENTE (T-14) und, als
 * Orakel (2), UMGEBUNG (T-15; die uebrigen Tests zu UMGEBUNG stehen in PathRecognizerUmgebungEigenschaftTest) mit
 * festem Startwert; Fehlermeldungen nennen nur Startwert und Iteration, keine Texte. Namen sind synthetisch,
 * Nicht-ASCII-Zeichen stehen nur als Escapes. Die Ausgabe der Messwerte ({@code System.out}) enthaelt nur Zahlen und
 * Startwerte, nie Text.
 *
 * <p>(1) Differenztest (Obermengen-Garantie): Mit allen Formen (T-15: zehn) ist jede Position abgedeckt, die ein
 * Durchlauf nur mit den fuenf Formen des Stands T-13 bzw. den sieben Formen von Durchlauf 1 (ohne die beiden
 * Wurzel-Formen) abdeckt, mit und ohne Anfuehrungszeichenregel, mit und ohne Anfuehrungszeichen im Text. Zusaetzlich
 * wird gemessen (und ausgegeben), wie viele bestehende Funde eine niedrigere Konfidenz bekommen: bei gleichen Grenzen
 * (ohne Anfuehrungszeichen im Text muss es 0 sein; mit Anfuehrungszeichen hat jeder Fall die Ursache, dass ein Fund einer
 * neuen Form im Zitat-Durchlauf die niedrigere Konfidenz traegt) und bei groesseren Grenzen (Plan Nachtrag T-12 Punkt
 * 7a (ii), T-14 Nachtrag 5 (c)).
 * (2) Orakel: Je einzelne Form (alle zehn) deckt sich der Durchlauf mit einer unabhaengigen, naiven Referenz mit
 * eigenen, woertlich ausgeschriebenen Zeichenlisten (nicht {@code PathChars}, kein {@code PathForm.headEnd}),
 * einschliesslich Quelle und Konfidenz. Auf Gesamtebene (Durchlauf mit allen Formen) wird in beide Richtungen
 * geprueft: Jeder Fund wird von mindestens einem Orakelfund irgendeiner Form beruehrt, und jede Orakelposition ist
 * abgedeckt; Funde der Quellen {@code pfad-relativ} und {@code pfad-umgebung} sind nicht ausgenommen. Der Kopf von
 * UMGEBUNG im Orakel enthaelt die verzoegerte Expansion {@code !NAME!}; ein zusaetzlicher Test vergleicht gegen den
 * Kopf ohne sie (Durchlauf 1 von T-15, Obermenge).
 * (3) Ueber {@code TextScanner.standard()}, {@code Pseudonymizer} und {@code Reidentifier}: jeder erzeugte Pfad liegt
 * ganz in einem Fund, kein Name eines Pfads steht im pseudonymisierten Text, der Roundtrip ist zeichengleich; jede URL
 * ist genau ein URL-Fund.
 */
class PathRecognizerRelativEigenschaftTest {

    private static final long SEED = 20261126L;
    private static final int ITERATIONEN_DIFFERENZ = 100_000;
    private static final int ITERATIONEN_ORAKEL = 120_000;
    private static final int ITERATIONEN_PIPELINE = 15_000;

    private static final PathRecognizer R = new PathRecognizer();
    private static final PathForm[] STAND_T13 = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME,
            PathForm.HOST_PFAD};
    /** Stand von Durchlauf 1 dieses Tasks: die Formen des Stands T-13 plus RELATIV und EINZEL_BACKSLASH. */
    private static final PathForm[] DURCHLAUF1 = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME,
            PathForm.HOST_PFAD, PathForm.RELATIV, PathForm.EINZEL_BACKSLASH};
    private static final PathForm[] NEUE_FORMEN = {PathForm.RELATIV, PathForm.EINZEL_BACKSLASH,
            PathForm.BENUTZER_WURZEL, PathForm.WURZEL_ZWEI_SEGMENTE, PathForm.UMGEBUNG};
    private static final Set<String> QUELLEN = Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home",
            "pfad-host", "pfad-relativ", "pfad-umgebung");
    private static final Set<Double> KONFIDENZEN = Set.of(0.95, 0.90, 0.80, 0.75, 0.60);

    /** Die 13 Anfuehrungszeichen-Arten (woertliche Liste dieser Datei, Escapes). */
    private static final String[] ANFUEHRUNG = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB",
            "\u2018", "\u2019", "\u201A", "\u2039", "\u203A"};
    private static final String ANFUEHRUNG_ALLE = "\"'`\u201E\u201C\u201D\u00BB\u00AB\u2018\u2019\u201A\u2039\u203A";
    private static final String[] TOKEN = {".", "..", "...", "./", "../", ".\\", "..\\", ".\\", "\\", "\\", "\\a",
            "\\a\\b", "/", "/a/b", "//", "~/", "~/x", "a", "b", "x", "Mein", "Ordner", " ", " ", " ", " ", "(", ")",
            "[", "]", "{", "}", "=", ",", ";", ":", "!", "?", "*", "-", "_", "@", "1", "9", "\\\\", "\\\\srv", "C:\\",
            "d:/", "host:/", "ab.c:/", ":/", "\t", "\n", "<", ">", "|", "\uD83D\uDE00", "e\u0301", "\u00A0", "\u200B",
            "\u2028", "~erika/", "(./)", "()", "/opt", "$", "%", "'", "\"",
            "Users\\", "users/", "USERS\\", "Benutzer/", "Benutzer\\", "benutzer/", "home/", "Home/", "Users", "home",
            "in", "innen", "Innen", "Benutzer/in", "Benutzer/innen.", ".:./", ".:", "x:./", "e", "home/x/y", "home/a/",
            "home/q/r/s", "/b", "Benutzer/in/", "Benutzer/innen/", "BENUTZER/IN\\", ":Users\\", ":home/", ":Benutzer/",
            "Pfad:Benutzer/", "Benutzer/Passwort", "Benutzer/in-", "Benutzer/\u0131n/",
            // T-15 (Umgebungsvariablen): Koepfe, Nicht-Koepfe, Ketten, Batch, PowerShell
            "$HOME/", "$a", "${H}/", "${", "$(p)/", "%A%\\", "%a%%b%\\", "%a%", "%~dp0", "%~dp0..\\", "%~f1/", "%~",
            "$env:A\\", "${env:A}/", "${env:P(x)}\\", "%P(x)%/", "$ENV/", "$1/", "%1\\", "%d/", "%%/", "${}/",
            "%a%%b%%c%%d%%e%\\", "%~dpnxsatzf0/", "$env:", "${env:",
            // T-15 Nachtrag Punkt 6: verzoegerte Expansion
            "!A!\\", "!a!/", "!a!!b!\\", "!!/", "!1!/", "!P(x)!\\", "%~dp0!a!/", "!a b!/"};

    // ------------------------------------------------------------------ (1) Differenztest

    @Test
    void differenzTestAlleFormenDeckenAbWasStandT13AbdecktMitAnfuehrungszeichenImText() {
        differenz(SEED, 4);
    }

    @Test
    void differenzTestAlleFormenDeckenAbWasStandT13AbdecktOhneAnfuehrungszeichenImText() {
        differenz(SEED + 1, 0);
    }

    private void differenz(long seed, int anfuehrungsAnteil) {
        Random rnd = new Random(seed);
        int echtGroesser = 0;
        long altFunde = 0;
        long gleicheGrenzenNiedriger = 0;
        long groesserNiedriger = 0;
        long gleicheGrenzenGleich = 0;
        long groesserGleichOderHoeher = 0;
        int mitNeuemFund = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd, 30, anfuehrungsAnteil);
            String wo = ", Startwert " + seed + ", Iteration " + it;
            List<Finding> neu = R.detect(text);
            List<Finding> alt = R.detect(text, STAND_T13);
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
            boolean[] bOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, STAND_T13));
            for (int i = 0; i < text.length(); i++) {
                if (bOhne[i] && !aOhne[i]) {
                    fail("Position ohne Zitatregel nicht mehr abgedeckt" + wo);
                }
            }
            if (groesser) {
                echtGroesser++;
            }
            for (Finding n : neu) {
                if (n.source().equals("pfad-relativ")) {
                    mitNeuemFund++;
                    break;
                }
            }
            // Konfidenz bestehender Funde: der neue Fund, der den alten enthaelt
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
        System.out.println("T-14 Differenztest (Startwert " + seed + ", Anfuehrungsanteil " + anfuehrungsAnteil + "): "
                + "Texte " + ITERATIONEN_DIFFERENZ + ", bestehende Funde " + altFunde
                + ", gleiche Grenzen/Konfidenz gleich " + gleicheGrenzenGleich
                + ", gleiche Grenzen/niedriger " + gleicheGrenzenNiedriger
                + ", groessere Grenzen/Konfidenz gleich oder hoeher " + groesserGleichOderHoeher
                + ", groessere Grenzen/niedriger " + groesserNiedriger
                + ", Texte mit echter Vergroesserung " + echtGroesser);
        if (anfuehrungsAnteil == 0) {
            assertEquals(0L, gleicheGrenzenNiedriger, "Konfidenz bei gleichen Grenzen gesunken");
        }
        assertTrue(echtGroesser > ITERATIONEN_DIFFERENZ / 50, "zu wenige echte Vergroesserungen: " + echtGroesser);
        assertTrue(mitNeuemFund > ITERATIONEN_DIFFERENZ / 5, "zu wenige Texte mit relativem Fund: " + mitNeuemFund);
    }

    /**
     * Ursache einer Absenkung bei gleichen Grenzen (Plan 7a (ii), T-14 Nachtrag 5 (c)): Der Text enthaelt ein
     * Anfuehrungszeichen (nur das Zitat-Paar kann einen frueheren ersten Kopf mit demselben Ende liefern), und ein
     * Durchlauf mit nur einer der neuen Formen liefert einen den Fund beruehrenden Fund mit niedrigerer Konfidenz als
     * der bestehende Fund.
     */
    private static void pruefeUrsacheDerAbsenkung(String text, Finding alt, Finding neu, String wo) {
        boolean anfuehrung = false;
        for (int i = 0; i < text.length(); i++) {
            anfuehrung |= ANFUEHRUNG_ALLE.indexOf(text.charAt(i)) >= 0;
        }
        assertTrue(anfuehrung, "Absenkung bei gleichen Grenzen ohne Anfuehrungszeichen im Text" + wo);
        boolean ursache = false;
        for (PathForm form : NEUE_FORMEN) {
            for (Finding x : R.detect(text, new PathForm[] {form})) {
                if (x.start() < neu.end() && neu.start() < x.end() && x.confidence() < alt.confidence()) {
                    ursache = true;
                }
            }
        }
        assertTrue(ursache, "Absenkung bei gleichen Grenzen ohne Fund einer neuen Form als Ursache" + wo);
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

    @Test
    void differenzTestAlleFormenDeckenAbWasDurchlauf1MitSiebenFormenAbdeckt() {
        for (int anteil : new int[] {0, 4}) {
            long seed = SEED + 20 + anteil;
            Random rnd = new Random(seed);
            int echtGroesser = 0;
            for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
                String text = zufallstext(rnd, 30, anteil);
                String wo = ", Startwert " + seed + ", Iteration " + it;
                boolean[] neu = abdeckung(text.length(), R.detect(text));
                boolean[] alt = abdeckung(text.length(), R.detect(text, DURCHLAUF1));
                boolean[] neuOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
                boolean[] altOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, DURCHLAUF1));
                boolean groesser = false;
                for (int i = 0; i < text.length(); i++) {
                    if (alt[i] && !neu[i]) {
                        fail("Position nicht mehr abgedeckt" + wo);
                    }
                    if (altOhne[i] && !neuOhne[i]) {
                        fail("Position ohne Zitatregel nicht mehr abgedeckt" + wo);
                    }
                    groesser |= neu[i] && !alt[i];
                }
                if (groesser) {
                    echtGroesser++;
                }
            }
            assertTrue(echtGroesser > ITERATIONEN_DIFFERENZ / 100, "zu wenige echte Vergroesserungen: " + echtGroesser);
        }
    }

    @Test
    void differenzTestJedeFormIstInAllenFormenEnthalten() {
        Random rnd = new Random(SEED + 2);
        for (int it = 0; it < 80_000; it++) {
            String text = zufallstext(rnd, 12, 3);
            boolean[] alle = abdeckung(text.length(), R.detect(text));
            boolean[] alleOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
            for (PathForm form : PathForm.values()) {
                boolean[] einzeln = abdeckung(text.length(), R.detect(text, new PathForm[] {form}));
                boolean[] einzelnOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, new PathForm[] {form}));
                for (int i = 0; i < text.length(); i++) {
                    if (einzeln[i] && !alle[i]) {
                        fail(form + ": Position nicht mehr abgedeckt, Startwert " + (SEED + 2) + ", Iteration " + it);
                    }
                    if (einzelnOhne[i] && !alleOhne[i]) {
                        fail(form + ": Position ohne Zitatregel nicht abgedeckt, Startwert " + (SEED + 2)
                                + ", Iteration " + it);
                    }
                }
            }
        }
    }

    @Test
    void differenzTestMitDichtenKurzenTexten() {
        Random rnd = new Random(SEED + 3);
        String[] dicht = {".", "./", "../", "\\", ".\\", "..\\", "a", "b", " ", "(", ")", "=", ",", "\"", "'", "/", "x",
                ":", "Users\\", "home/", "Benutzer/", "in"};
        for (int it = 0; it < 120_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(10);
            for (int k = 0; k < len; k++) {
                sb.append(dicht[rnd.nextInt(dicht.length)]);
            }
            String text = sb.toString();
            boolean[] neu = abdeckung(text.length(), R.detect(text));
            boolean[] alt = abdeckung(text.length(), R.detect(text, STAND_T13));
            boolean[] alt1 = abdeckung(text.length(), R.detect(text, DURCHLAUF1));
            for (int i = 0; i < text.length(); i++) {
                if ((alt[i] || alt1[i]) && !neu[i]) {
                    fail("Position nicht mehr abgedeckt (dicht), Startwert " + (SEED + 3) + ", Iteration " + it);
                }
            }
        }
    }

    /**
     * Der Zitat-Pfad liest nie hinter das Zitat: Die Zitat-Funde eines Texts {@code Q inhalt Q tail} (tail ohne
     * Anfuehrungszeichen) sind dieselben wie die des Texts {@code Q inhalt Q} ohne tail.
     */
    @Test
    void zitatPfadAendertSichDurchTextHinterDemSchliesszeichenNie() {
        Random rnd = new Random(SEED + 7);
        PathForm[] formen = PathForm.values();
        int mitFund = 0;
        for (int it = 0; it < 60_000; it++) {
            String q = ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)];
            String inhalt = zufallstext(rnd, 1 + rnd.nextInt(6), 0);
            String tail = zufallstext(rnd, 1 + rnd.nextInt(4), 0);
            String kurz = q + inhalt + q;
            List<Finding> erwartet = PathRecognizer.collectQuoted(kurz, formen);
            List<Finding> ist = PathRecognizer.collectQuoted(kurz + tail, formen);
            assertEquals(erwartet, ist, "Zitat-Funde aendern sich durch den Text dahinter, Startwert " + (SEED + 7)
                    + ", Iteration " + it);
            mitFund += erwartet.isEmpty() ? 0 : 1;
        }
        assertTrue(mitFund > 2_000, "zu wenige Zitat-Funde: " + mitFund);
    }

    // ------------------------------------------------------------------ (2) Orakel je einzelne Form und gesamt

    private record Fund(int start, int ende, double konfidenz, String quelle) {
    }

    private static final Pattern KOPF_LAUFWERK = Pattern.compile("[A-Za-z]:[/\\\\]");
    private static final Pattern KOPF_HOME = Pattern.compile("~(?:/|[A-Za-z_][A-Za-z0-9._-]{0,63}/)");
    private static final Pattern KOPF_HOST = Pattern.compile("([A-Za-z0-9._-]{2,64}):/(?!/)");
    private static final Pattern KOPF_RELATIV = Pattern.compile("\\.{1,2}[/\\\\]");
    /** Kopf der Form BENUTZER_WURZEL: Users mit beiden Trennern, Benutzer nur mit Backslash. */
    private static final Pattern KOPF_WURZEL_EIN = Pattern.compile(
            "(?:[Uu][Ss][Ee][Rr][Ss][/\\\\]|[Bb][Ee][Nn][Uu][Tt][Zz][Ee][Rr][\\\\])");
    /** Kopf der Form WURZEL_ZWEI_SEGMENTE: home/ (klein) oder Benutzer/ (Gross-/Kleinschreibung egal). */
    private static final Pattern KOPF_WURZEL_ZWEI = Pattern.compile("(?:home|[Bb][Ee][Nn][Uu][Tt][Zz][Ee][Rr])/");
    /** Gendern: erstes Segment hinter Benutzer/ ist genau in oder innen, direkt dahinter ein Trenner. */
    private static final Pattern GENDER_SEGMENT = Pattern.compile("(?:[Ii][Nn][Nn][Ee][Nn]|[Ii][Nn])[/\\\\]");
    /** Variablenname bei {@code %NAME%} und {@code ${env:NAME}}: 1 bis 64 Zeichen, auch Klammern. */
    private static final String NAME_PROZENT = "[A-Za-z_][A-Za-z0-9_()]{0,63}";
    /** Variablenname bei {@code $NAME}, {@code ${NAME}}, {@code $(NAME)} und {@code $env:NAME}: 1 bis 64 Zeichen. */
    private static final String NAME_DOLLAR = "[A-Za-z_][A-Za-z0-9_]{0,63}";
    /** Kopfvarianten (a) bis (g) der Form UMGEBUNG, woertlich (siehe {@link #KOPF_UMGEBUNG}). */
    private static final String KOPF_UMGEBUNG_A_BIS_G =
            "(?:(?:%" + NAME_PROZENT + "%){1,4}"
            + "|\\$" + NAME_DOLLAR
            + "|\\$\\{" + NAME_DOLLAR + "\\}"
            + "|\\$\\(" + NAME_DOLLAR + "\\)"
            + "|\\$[Ee][Nn][Vv]:" + NAME_DOLLAR
            + "|\\$\\{[Ee][Nn][Vv]:" + NAME_PROZENT + "\\})[/\\\\]"
            + "|%~[fdpnxsatzFDPNXSATZ]{0,8}[0-9][^/\\\\\\p{Z}\\p{Cc}\"`<>|]{0,64}[/\\\\]";
    /** Variante (h): verzoegerte Expansion {@code !NAME!}, eine Gruppe, mit Trenner. */
    private static final String VERZOEGERT = "|!" + NAME_PROZENT + "![/\\\\]";
    /**
     * Kopf der Form UMGEBUNG (woertlich): (a) eine bis vier {@code %NAME%}-Gruppen, (b) {@code $NAME}, (c)
     * {@code ${NAME}}, (d) {@code $(NAME)}, (e) {@code $env:NAME}, (f) {@code ${env:NAME}}, jeweils mit Trenner; (g)
     * Batch {@code %~}, 0 bis 8 Modifikatoren aus {@code fdpnxsatz}, eine Ziffer, 0 bis 64 Zeichen, die weder Trenner
     * noch Endzeichen sind, und ein Trenner; (h) verzoegerte Expansion {@code !NAME!} (NAME wie bei {@code %NAME%},
     * genau eine Gruppe) und ein Trenner. Endzeichen sind Leerraum-Kategorien (Z), Steuerzeichen (Cc) und {@code "},
     * Backtick, {@code <}, {@code >}, {@code |}.
     */
    private static final Pattern KOPF_UMGEBUNG = Pattern.compile(KOPF_UMGEBUNG_A_BIS_G + VERZOEGERT);
    /** Die Kopfvarianten (a) bis (g), also der Kopf der Form UMGEBUNG in Durchlauf 1 (ohne {@code !NAME!}). */
    private static final Pattern KOPF_UMGEBUNG_DURCHLAUF1 = Pattern.compile(KOPF_UMGEBUNG_A_BIS_G);
    private static final String ANFUEHRUNG_START = ANFUEHRUNG_ALLE;
    private static final String START_ZEICHEN = "([{<>=,;|";
    private static final String HOST_ZEICHEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-";

    private static boolean endzeichen(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)
                || "\"`<>|".indexOf(c) >= 0;
    }

    private static boolean trenner(char c) {
        return c == '/' || c == '\\';
    }

    private static boolean ohneStartgrenze(PathForm form) {
        return form == PathForm.LAUFWERK || form == PathForm.UNC || form == PathForm.HOST_PFAD
                || form == PathForm.UMGEBUNG;
    }

    private static boolean startgrenze(String text, int pos, PathForm form) {
        if (ohneStartgrenze(form) || pos == 0) {
            return true;
        }
        char p = text.charAt(pos - 1);
        return Character.isWhitespace(p) || Character.isSpaceChar(p) || Character.isISOControl(p)
                || ANFUEHRUNG_START.indexOf(p) >= 0 || START_ZEICHEN.indexOf(p) >= 0
                || ((form == PathForm.RELATIV || form == PathForm.BENUTZER_WURZEL
                        || form == PathForm.WURZEL_ZWEI_SEGMENTE) && p == ':');
    }

    /**
     * Kopfende laut Referenz (Regex bzw. Zeichenvergleich, ohne {@code PathForm.headEnd}), -1 wenn kein Kopf. Fuer
     * UMGEBUNG gilt das Muster {@code umgebung} (aktuell {@link #KOPF_UMGEBUNG} oder, fuer den Vergleich mit
     * Durchlauf 1, {@link #KOPF_UMGEBUNG_DURCHLAUF1}).
     */
    private static int kopf(String text, int pos, PathForm form, Pattern umgebung) {
        int n = text.length();
        switch (form) {
            case LAUFWERK:
                return regionKopf(KOPF_LAUFWERK, text, pos);
            case UNC:
                return kopfUnc(text, pos);
            case UNIX:
                return text.charAt(pos) == '/' ? pos + 1 : -1;
            case HOME:
                return regionKopf(KOPF_HOME, text, pos);
            case HOST_PFAD:
                return kopfHost(text, pos);
            case RELATIV:
                return regionKopf(KOPF_RELATIV, text, pos);
            case EINZEL_BACKSLASH:
                if (text.charAt(pos) != '\\' || (pos > 0 && text.charAt(pos - 1) == '\\') || pos + 1 >= n) {
                    return -1;
                }
                return trenner(text.charAt(pos + 1)) ? -1 : pos + 1;
            case BENUTZER_WURZEL:
                return regionKopf(KOPF_WURZEL_EIN, text, pos);
            case WURZEL_ZWEI_SEGMENTE:
                return kopfWurzelZwei(text, pos);
            case UMGEBUNG:
                return regionKopf(umgebung, text, pos);
            default:
                throw new IllegalStateException("Form ohne Orakel: " + form);
        }
    }

    private static int regionKopf(Pattern p, String text, int pos) {
        Matcher m = p.matcher(text).region(pos, text.length());
        return m.lookingAt() ? m.end() : -1;
    }

    private static int kopfUnc(String text, int pos) {
        int n = text.length();
        if (text.charAt(pos) != '\\' || (pos > 0 && text.charAt(pos - 1) == '\\')) {
            return -1;
        }
        int j = pos;
        while (j < n && text.charAt(j) == '\\') {
            j++;
        }
        return j - pos >= 2 && j < n && !trenner(text.charAt(j)) ? j : -1;
    }

    /**
     * HOST_PFAD: Lauf aus 2 bis 64 Host-Zeichen mit mindestens einem Buchstaben direkt vor {@code :/} (nicht
     * {@code ://}), beginnend am Wortanfang oder, bei genau 64 Zeichen, mitten in einem laengeren Lauf.
     */
    private static int kopfHost(String text, int pos) {
        Matcher m = KOPF_HOST.matcher(text).region(pos, text.length());
        if (!m.lookingAt()) {
            return -1;
        }
        String lauf = m.group(1);
        boolean wortanfang = pos == 0 || HOST_ZEICHEN.indexOf(text.charAt(pos - 1)) < 0;
        boolean buchstabe = lauf.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
        return buchstabe && (wortanfang || lauf.length() == 64) ? m.end() : -1;
    }

    private static int kopfWurzelZwei(String text, int pos) {
        int ende = regionKopf(KOPF_WURZEL_ZWEI, text, pos);
        if (ende < 0) {
            return -1;
        }
        boolean benutzer = text.charAt(pos) != 'h';
        if (benutzer && regionKopf(GENDER_SEGMENT, text, ende) >= 0) {
            return -1;
        }
        return ende;
    }

    private static int mindest(PathForm form) {
        switch (form) {
            case UNIX:
            case EINZEL_BACKSLASH:
            case WURZEL_ZWEI_SEGMENTE:
                return 2;
            default:
                return 1;
        }
    }

    private static double konfidenz(PathForm form, String text, int pos, int segmente) {
        switch (form) {
            case LAUFWERK:
                return 0.95;
            case UNC:
                return segmente >= 2 ? 0.95 : 0.60;
            case UNIX:
                return segmente >= 3 ? 0.90 : 0.80;
            case HOME:
                return text.charAt(pos + 1) == '/' ? 0.90 : 0.80;
            case HOST_PFAD:
                return segmente >= 2 ? 0.90 : 0.80;
            case RELATIV:
            case UMGEBUNG:
                return 0.80;
            case EINZEL_BACKSLASH:
                return 0.75;
            case BENUTZER_WURZEL:
            case WURZEL_ZWEI_SEGMENTE:
                return 0.60;
            default:
                throw new IllegalStateException("Form ohne Orakel: " + form);
        }
    }

    private static String quelle(PathForm form) {
        switch (form) {
            case LAUFWERK:
                return "pfad-laufwerk";
            case UNC:
                return "pfad-unc";
            case UNIX:
                return "pfad-unix";
            case HOME:
                return "pfad-home";
            case HOST_PFAD:
                return "pfad-host";
            case UMGEBUNG:
                return "pfad-umgebung";
            default:
                return "pfad-relativ";
        }
    }

    /** Referenz fuer den Durchlauf ohne Anfuehrungszeichen mit nur einer Form (quadratisch, nur fuer kurze Texte). */
    private static List<Fund> orakel(String text, PathForm form) {
        return orakel(text, form, KOPF_UMGEBUNG);
    }

    /** Wie oben, mit wahlweise anderem Kopf-Muster fuer UMGEBUNG (siehe {@code kopf}). */
    private static List<Fund> orakel(String text, PathForm form, Pattern umgebung) {
        List<Fund> r = new ArrayList<>();
        int n = text.length();
        int pos = 0;
        while (pos < n) {
            if (endzeichen(text.charAt(pos))) {
                pos++;
                continue;
            }
            int laufEnde = pos;
            while (laufEnde < n && !endzeichen(text.charAt(laufEnde))) {
                laufEnde++;
            }
            int kopf = startgrenze(text, pos, form) ? kopf(text, pos, form, umgebung) : -1;
            if (kopf >= 0) {
                int ende = kuerze(text, pos, laufEnde, kopf);
                int segmente = Math.min(3, segmente(text, kopf, ende));
                if (segmente >= mindest(form)) {
                    r.add(new Fund(pos, ende, konfidenz(form, text, pos, segmente), quelle(form)));
                    pos = laufEnde;
                    continue;
                }
            }
            pos++;
        }
        return r;
    }

    private static int kuerze(String text, int start, int laufEnde, int kopf) {
        int ende = laufEnde;
        boolean geaendert = true;
        while (geaendert && ende > kopf) {
            geaendert = false;
            char c = text.charAt(ende - 1);
            if (".,;:!?'".indexOf(c) >= 0) {
                ende--;
                geaendert = true;
            } else {
                int k = ")]}".indexOf(c);
                if (k >= 0) {
                    char auf = "([{".charAt(k);
                    int zu = 0;
                    int offen = 0;
                    for (int i = start; i < ende; i++) {
                        if (text.charAt(i) == c) {
                            zu++;
                        } else if (text.charAt(i) == auf) {
                            offen++;
                        }
                    }
                    if (zu > offen) {
                        ende--;
                        geaendert = true;
                    }
                }
            }
        }
        return ende;
    }

    private static int segmente(String text, int von, int bis) {
        int z = 0;
        boolean im = false;
        for (int i = von; i < bis; i++) {
            if (trenner(text.charAt(i))) {
                im = false;
            } else if (!im) {
                im = true;
                z++;
            }
        }
        return z;
    }

    private static void vergleiche(List<Fund> soll, List<Finding> ist, String wo, boolean mitQuelle) {
        assertEquals(soll.size(), ist.size(), "Fundzahl" + wo);
        for (int i = 0; i < soll.size(); i++) {
            assertEquals(soll.get(i).start(), ist.get(i).start(), "Start" + wo);
            assertEquals(soll.get(i).ende(), ist.get(i).end(), "Ende" + wo);
            assertEquals(soll.get(i).konfidenz(), ist.get(i).confidence(), "Konfidenz" + wo);
            if (mitQuelle) {
                assertEquals(soll.get(i).quelle(), ist.get(i).source(), "Quelle" + wo);
            }
        }
    }

    @Test
    void orakelJedeFormStimmtMitDerReferenzUeberein() {
        Random rnd = new Random(SEED + 4);
        PathForm[] formen = PathForm.values();
        int[] mitFund = new int[formen.length];
        for (int it = 0; it < ITERATIONEN_ORAKEL; it++) {
            String text = zufallstext(rnd, 1 + rnd.nextInt(10), 0);
            for (PathForm form : formen) {
                List<Fund> soll = orakel(text, form);
                List<Finding> ist = R.detectWithoutQuotes(text, new PathForm[] {form});
                vergleiche(soll, ist, ", Form " + form + ", Startwert " + (SEED + 4) + ", Iteration " + it, true);
                if (!soll.isEmpty()) {
                    mitFund[form.ordinal()]++;
                }
            }
        }
        for (PathForm form : formen) {
            assertTrue(mitFund[form.ordinal()] > 1_500, form + ": Orakel zu selten angewandt: "
                    + mitFund[form.ordinal()]);
        }
    }

    /** Gezielt Texte, in denen das Klammer-Segment den Ausschlag gibt (Mindestzahl 1 und 2). */
    @Test
    void orakelMitKlammerSegmentenFuerJedeForm() {
        String[] koepfe = {"./", "../", ".\\", "..\\", "\\", "x=\\", "(\\", "(./", ",\\", "|./", "Users\\", "users/",
                "home/", "(Benutzer\\", ":./", "/", "~/", "C:\\", "\\\\", "host:/"};
        String[] teile = {"a", "(", ")", "/", "\\", "[", "]", "{", "}", ".", ",", "a(", ")", "(/)", "x", "()", "\\)"};
        Random rnd = new Random(SEED + 5);
        int mitGewinn = 0;
        for (int it = 0; it < 100_000; it++) {
            StringBuilder sb = new StringBuilder();
            if (rnd.nextBoolean()) {
                sb.append(' ');
            }
            sb.append(koepfe[rnd.nextInt(koepfe.length)]);
            int n = 1 + rnd.nextInt(6);
            for (int k = 0; k < n; k++) {
                sb.append(teile[rnd.nextInt(teile.length)]);
            }
            String text = sb.toString();
            for (PathForm form : PathForm.values()) {
                List<Fund> soll = orakel(text, form);
                List<Finding> ist = R.detectWithoutQuotes(text, new PathForm[] {form});
                vergleiche(soll, ist, ", Form " + form + ", Startwert " + (SEED + 5) + ", Iteration " + it, false);
                if (!soll.isEmpty() && text.endsWith(")") && text.contains("(")) {
                    mitGewinn++;
                }
            }
        }
        assertTrue(mitGewinn > 500, "Klammer-Segment-Faelle zu selten: " + mitGewinn);
    }

    /**
     * Gesamtebene (Durchlauf mit allen zehn Formen), in beide Richtungen und ohne Ausnahme fuer eine Quelle: (a) Jeder
     * Fund wird von mindestens einem Orakelfund irgendeiner Form beruehrt. (b) Jede Position, die die Referenz einer
     * Form als Fund meldet, ist abgedeckt. (c) Die Konfidenz eines Fundes liegt zwischen der niedrigsten und der
     * hoechsten Konfidenz der ihn beruehrenden Orakelfunde (zusammengelegte Funde tragen die niedrigste Konfidenz der
     * Beteiligten, Plan 7a), und die Quelle ist die eines beruehrenden Orakelfundes.
     */
    @Test
    void orakelAlleFormenGesamtebeneBeideRichtungen() {
        Random rnd = new Random(SEED + 6);
        long funde = 0;
        long relativ = 0;
        long umgebung = 0;
        for (int it = 0; it < 100_000; it++) {
            String text = zufallstext(rnd, 1 + rnd.nextInt(10), 0);
            String wo = ", Startwert " + (SEED + 6) + ", Iteration " + it;
            List<Finding> alle = R.detectWithoutQuotes(text);
            pruefeFunde(text, alle, wo);
            List<Fund> referenz = new ArrayList<>();
            for (PathForm form : PathForm.values()) {
                referenz.addAll(orakel(text, form));
            }
            for (Finding f : alle) {
                funde++;
                relativ += f.source().equals("pfad-relativ") ? 1 : 0;
                umgebung += f.source().equals("pfad-umgebung") ? 1 : 0;
                double min = 2.0;
                double max = 0.0;
                boolean quelleGefunden = false;
                for (Fund o : referenz) {
                    if (o.start() < f.end() && f.start() < o.ende()) {
                        min = Math.min(min, o.konfidenz());
                        max = Math.max(max, o.konfidenz());
                        quelleGefunden |= o.quelle().equals(f.source());
                    }
                }
                assertTrue(max > 0.0, "Fund ohne beruehrenden Orakelfund" + wo);
                assertTrue(f.confidence() >= min && f.confidence() <= max, "Konfidenz ausserhalb des Orakels" + wo);
                assertTrue(quelleGefunden, "Quelle ohne beruehrenden Orakelfund dieser Quelle" + wo);
            }
            boolean[] a = abdeckung(text.length(), alle);
            for (Fund o : referenz) {
                for (int i = o.start(); i < o.ende(); i++) {
                    assertTrue(a[i], "Orakelposition nicht abgedeckt" + wo);
                }
            }
        }
        assertTrue(funde > 20_000, "zu wenige Funde im Gesamttest: " + funde);
        assertTrue(relativ > 5_000, "zu wenige Funde der Quelle pfad-relativ im Gesamttest: " + relativ);
        assertTrue(umgebung > 3_000, "zu wenige Funde der Quelle pfad-umgebung im Gesamttest: " + umgebung);
    }

    /**
     * Obermengen-Garantie gegenueber Durchlauf 1 des Tasks (UMGEBUNG ohne {@code !NAME!}): Jede Position, die die
     * Referenz einer Form mit dem Kopf von Durchlauf 1 meldet, ist im Durchlauf mit allen Formen und im Durchlauf mit
     * der Einzelform abgedeckt. Enthaelt der Text kein {@code !}, sind beide Referenzen gleich, und der Durchlauf der
     * Einzelform UMGEBUNG stimmt mit der Referenz von Durchlauf 1 in Grenzen, Konfidenz und Quelle ueberein.
     */
    @Test
    void obermengeGegenueberDurchlauf1OhneVerzoegerteExpansion() {
        Random rnd = new Random(SEED + 11);
        PathForm[] formen = PathForm.values();
        int mitAusrufezeichen = 0;
        int mitFundDurchlauf1 = 0;
        int echtGroesser = 0;
        for (int it = 0; it < 100_000; it++) {
            String text = zufallstext(rnd, 1 + rnd.nextInt(10), 0);
            String wo = ", Startwert " + (SEED + 11) + ", Iteration " + it;
            boolean[] alle = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] einzeln = abdeckung(text.length(),
                    R.detectWithoutQuotes(text, new PathForm[] {PathForm.UMGEBUNG}));
            boolean[] referenz1 = new boolean[text.length()];
            for (PathForm form : formen) {
                for (Fund o : orakel(text, form, KOPF_UMGEBUNG_DURCHLAUF1)) {
                    for (int i = o.start(); i < o.ende(); i++) {
                        referenz1[i] = true;
                        assertTrue(alle[i], "Position von Durchlauf 1 nicht mehr abgedeckt, Form " + form + wo);
                    }
                }
            }
            List<Fund> nurUmgebung1 = orakel(text, PathForm.UMGEBUNG, KOPF_UMGEBUNG_DURCHLAUF1);
            for (Fund o : nurUmgebung1) {
                for (int i = o.start(); i < o.ende(); i++) {
                    assertTrue(einzeln[i], "Position von Durchlauf 1 nicht mehr abgedeckt, UMGEBUNG allein" + wo);
                }
            }
            if (text.indexOf('!') < 0) {
                vergleiche(nurUmgebung1, R.detectWithoutQuotes(text, new PathForm[] {PathForm.UMGEBUNG}), wo, true);
            } else {
                mitAusrufezeichen++;
                for (int i = 0; i < text.length(); i++) {
                    if (alle[i] && !referenz1[i]) {
                        echtGroesser++;
                        break;
                    }
                }
            }
            mitFundDurchlauf1 += nurUmgebung1.isEmpty() ? 0 : 1;
        }
        assertTrue(mitAusrufezeichen > 10_000, "zu wenige Texte mit Ausrufezeichen: " + mitAusrufezeichen);
        assertTrue(mitFundDurchlauf1 > 3_000, "zu wenige Funde von Durchlauf 1: " + mitFundDurchlauf1);
        assertTrue(echtGroesser > 500, "zu wenige echte Vergroesserungen durch !NAME!: " + echtGroesser);
    }

    // ------------------------------------------------------------------ Hilfen

    private static String zufallstext(Random rnd, int maxTokens, int anfuehrungsAnteil) {
        StringBuilder sb = new StringBuilder();
        int tokens = 1 + rnd.nextInt(maxTokens);
        for (int k = 0; k < tokens; k++) {
            if (anfuehrungsAnteil > 0 && rnd.nextInt(anfuehrungsAnteil + 1) == 0) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
            } else if (anfuehrungsAnteil == 0) {
                String t = TOKEN[rnd.nextInt(TOKEN.length)];
                // ohne Anfuehrungszeichen im Text: die beiden Tokens mit " und ' werden ersetzt
                sb.append(t.equals("'") || t.equals("\"") ? "a" : t);
            } else {
                sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
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

    // ------------------------------------------------------------------ (3) Pipeline Scanner -> Pseudonymizer
    // -> Reidentifier

    private record Paar(String auf, String zu) {
    }

    private static final Paar[] PAARE = {new Paar("\"", "\""), new Paar("'", "'"), new Paar("`", "`"),
            new Paar("\u201E", "\u201C"), new Paar("\u201C", "\u201D"), new Paar("\u00BB", "\u00AB"),
            new Paar("\u2018", "\u2019"), new Paar("\u2039", "\u203A")};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "oben", "folgt", "nichts", "weiter", "und/oder", "km/h", "19/11/2026", "TCP/IP", "n/a",
            "1./2.", "usw./etc.", "Ende../x", "1.0/2.0", "Zeile\\nNeu", "praxis\\konto", "Liebe Benutzer/innen,",
            "Benutzer/in", "Users", "home"};
    /** Praefixe, die eine Startgrenze ergeben (Plan 2.4.1). */
    private static final String[] VOR = {"", "", " ", "x=", "(", "[", "{", "--config=", ">", "\n", "ab ", "Datei: ",
            "\t", "<", "Pfad ", "-> ", ",", ";", "|", "a,", "cmd|"};
    private static final String[] NACH = {"", "", ".", ",", ")", ";", "!", "?", " Ende", "\n", "\r\n", "]"};
    private static final String[] BASIS = {"Tools", "Daten", "Akten", "Projekt", "Gr\u00F6\u00DFe", "\u00DCbersicht",
            "BeispielTool", "e\u0301cole", "home", "Dokumente"};
    private static final String[] DATEI = {"akte.pdf", "tool.exe", "notiz.txt", "konfig.yml", "a.tar.gz", "x"};
    private static final String[] SERVER = {"srv-beispiel", "srv01.praxis.example", "srv.beispiel.invalid"};
    private static final String[] RELATIV_KOEPFE = {"./", "../", ".\\", "..\\", "../../", "..\\..\\", ".\\.\\",
            "./../"};
    private static final String[] WURZEL_KOEPFE = {"Users\\", "users/", "USERS\\", "Benutzer\\", "benutzer/",
            "Benutzer/", "home/"};

    private record Gen(String text, List<int[]> pfade, List<String> namen, List<int[]> urls) {
    }

    private static String name(Random rnd, boolean mitLeerzeichen) {
        String n = BASIS[rnd.nextInt(BASIS.length)] + rnd.nextInt(10_000);
        if (mitLeerzeichen && rnd.nextInt(3) == 0) {
            n = "Mein Ordner" + rnd.nextInt(10_000);
        }
        return n;
    }

    private static String endung(Random rnd, String n) {
        return n.replace(' ', '_') + "." + DATEI[rnd.nextInt(DATEI.length)] + rnd.nextInt(100);
    }

    private static String segmente(Random rnd, boolean zitat, List<String> namen, int anzahl, String trenner) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < anzahl; s++) {
            String n = name(rnd, zitat);
            if (s == anzahl - 1 && rnd.nextBoolean()) {
                n = endung(rnd, n);
            }
            namen.add(n);
            sb.append(s > 0 ? (trenner.isEmpty() ? (rnd.nextInt(7) == 0 ? "/" : "\\") : trenner) : "").append(n);
        }
        return sb.toString();
    }

    private static String relativPfad(Random rnd, boolean zitat, List<String> namen) {
        String kopf = RELATIV_KOEPFE[rnd.nextInt(RELATIV_KOEPFE.length)];
        String trenner = kopf.contains("\\") ? "\\" : "/";
        return kopf + segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), trenner);
    }

    private static String wurzelPfad(Random rnd, boolean zitat, List<String> namen) {
        String kopf = WURZEL_KOEPFE[rnd.nextInt(WURZEL_KOEPFE.length)];
        String trenner = kopf.endsWith("\\") ? "\\" : "/";
        int mindest = kopf.equals("home/") || kopf.equalsIgnoreCase("benutzer/") ? 2 : 1;
        return kopf + segmente(rnd, zitat, namen, mindest + rnd.nextInt(3), trenner);
    }

    private static String backslashPfad(Random rnd, boolean zitat, List<String> namen) {
        return "\\" + segmente(rnd, zitat, namen, 2 + rnd.nextInt(3), "");
    }

    private static String windowsPfad(Random rnd, boolean zitat, List<String> namen) {
        StringBuilder sb = new StringBuilder();
        if (rnd.nextBoolean()) {
            sb.append((char) ('A' + rnd.nextInt(26))).append(rnd.nextBoolean() ? ":\\" : ":/");
        } else {
            String server = SERVER[rnd.nextInt(SERVER.length)] + rnd.nextInt(1000);
            namen.add(server);
            sb.append("\\\\").append(server).append("\\");
        }
        return sb.append(segmente(rnd, zitat, namen, 1 + rnd.nextInt(3), "\\")).toString();
    }

    private static String unixPfad(Random rnd, boolean zitat, List<String> namen) {
        return (rnd.nextBoolean() ? "/" : "~/") + segmente(rnd, zitat, namen, 2 + rnd.nextInt(3), "/");
    }

    private static String pfadVonArt(Random rnd, boolean zitat, List<String> namen) {
        int wahl = rnd.nextInt(12);
        if (wahl < 4) {
            return relativPfad(rnd, zitat, namen);
        } else if (wahl < 6) {
            return backslashPfad(rnd, zitat, namen);
        } else if (wahl < 8) {
            return wurzelPfad(rnd, zitat, namen);
        } else if (wahl < 10) {
            return windowsPfad(rnd, zitat, namen);
        }
        return unixPfad(rnd, zitat, namen);
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
                sb.append("https://example.invalid/").append(rnd.nextBoolean() ? "a/b/c" : "seite/neu")
                        .append(rnd.nextBoolean() ? "?f=../a/b" : "");
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
            String pfad = pfadVonArt(rnd, zitat, pfadNamen);
            if (zitat) {
                sb.append(paar.auf());
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

    @Test
    void zufaelligeRelativeBackslashUndWurzelPfadeSindGanzAbgedecktOhneKlartextUndMitRoundtrip() {
        Random rnd = new Random(SEED + 7);
        TextScanner scanner = TextScanner.standard();
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        int relativFunde = 0;
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
                if (f.source().equals("pfad-relativ")) {
                    relativFunde++;
                }
            }
            for (int[] pfad : g.pfade()) {
                assertTrue(deckt(funde, pfad[0], pfad[1]), "Pfad nicht ganz in einem Fund" + wo);
            }
            boolean ohneAnfuehrung = true;
            for (int i = 0; i < text.length(); i++) {
                ohneAnfuehrung &= PathChars.quoteKind(text.charAt(i)) < 0;
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
        }
        assertTrue(relativFunde > ITERATIONEN_PIPELINE, "zu wenige relative Funde im Test: " + relativFunde);
        assertTrue(urlsStreng > 500, "zu wenige URLs ohne Anfuehrungszeichen im Text: " + urlsStreng);
    }

    /**
     * Messung auf realistisch erzeugten Texten (Pfade durch Leerraum getrennt, mit Fuelltext): bestehende Funde des
     * Stands T-13 behalten Grenzen und Konfidenz. Die Zahl der Absenkungen bei groesseren Grenzen wird ausgegeben.
     */
    @Test
    void konfidenzBestehenderFundeAufRealistischenTexten() {
        Random rnd = new Random(SEED + 8);
        long altFunde = 0;
        long gleicheGrenzenNiedriger = 0;
        long groesserNiedriger = 0;
        long groesser = 0;
        for (int it = 0; it < 30_000; it++) {
            String text = erzeuge(rnd).text();
            List<Finding> neu = R.detect(text);
            for (Finding f : R.detect(text, STAND_T13)) {
                altFunde++;
                Finding g = enthaltender(neu, f);
                assertNotNull(g, "kein enthaltender Fund, Startwert " + (SEED + 8) + ", Iteration " + it);
                boolean gleich = g.start() == f.start() && g.end() == f.end();
                groesser += gleich ? 0 : 1;
                if (g.confidence() < f.confidence()) {
                    if (gleich) {
                        gleicheGrenzenNiedriger++;
                        pruefeUrsacheDerAbsenkung(text, f, g, ", Startwert " + (SEED + 8) + ", Iteration " + it);
                    } else {
                        groesserNiedriger++;
                    }
                }
            }
        }
        System.out.println("T-14 realistische Texte (Startwert " + (SEED + 8) + "): bestehende Funde " + altFunde
                + ", groessere Grenzen " + groesser + ", gleiche Grenzen/niedriger " + gleicheGrenzenNiedriger
                + ", groessere Grenzen/niedriger " + groesserNiedriger);
        assertTrue(groesserNiedriger * 100 < altFunde, "zu viele Absenkungen bei groesseren Grenzen");
    }

    private static boolean deckt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
    }
}
