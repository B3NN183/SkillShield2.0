package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Eigenschaftstests der Formen UNIX, HOME (auch {@code ~name/}) und HOST_PFAD (T-13) mit festem Startwert;
 * Fehlermeldungen nennen nur Startwert und Iteration, keine Texte. Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes.
 *
 * <p>(1) Differenztest (Obermengen-Garantie): Mit allen fuenf Formen ist jede Position abgedeckt, die ein Durchlauf nur
 * mit {@code LAUFWERK} und {@code UNC} (Stand T-12) oder nur mit {@code LAUFWERK}, {@code UNC}, {@code UNIX} und
 * {@code HOME} (Stand Durchlauf 1) abdeckt, mit und ohne Anfuehrungszeichenregel; ausserdem je einzelne Form gegen den
 * Durchlauf mit allen Formen. Die zusaetzlichen Startgrenzen {@code , ; |} lassen sich nicht per Formfilter
 * abschalten; dort gilt der Orakeltest. (2) Orakel: Je einzelne Form (auch mit Mindestzahl 2 und Klammer-Segment) deckt
 * sich der Durchlauf mit einer unabhaengigen, naiven Referenzimplementierung (Kuerzen und Segmentzaehlung ohne die
 * Laufdaten des Erkenners; Kopf von HOME und HOST_PFAD per regulaerem Ausdruck), einschliesslich Quelle und Konfidenz
 * je Segmentzahl. Fuer den Durchlauf mit allen Formen
 * gilt Quelle und Konfidenz als Menge. (3) Ueber {@code TextScanner.standard()}, {@code Pseudonymizer} und
 * {@code Reidentifier}: jeder erzeugte Pfad (ohne Leerzeichen bzw. im Zitat) liegt ganz in einem Fund, kein Name eines
 * Pfads steht im pseudonymisierten Text, der Roundtrip ist zeichengleich und meldet nichts; jede URL ist genau ein
 * URL-Fund.
 */
class PathRecognizerUnixEigenschaftTest {

    private static final long SEED = 20261125L;
    private static final int ITERATIONEN_DIFFERENZ = 100_000;
    private static final int ITERATIONEN_ORAKEL = 150_000;
    private static final int ITERATIONEN_PIPELINE = 12_000;

    private static final PathRecognizer R = new PathRecognizer();
    private static final PathForm[] ALT = {PathForm.LAUFWERK, PathForm.UNC};
    private static final PathForm[] DURCHLAUF1 = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME};
    /** Formen mit Orakel in dieser Datei (Stand T-13); RELATIV und EINZEL_BACKSLASH: PathRecognizerRelativEigenschaftTest. */
    private static final PathForm[] ORAKEL_FORMEN = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME,
            PathForm.HOST_PFAD};
    private static final Set<String> QUELLEN = Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home", "pfad-relativ",
            "pfad-host", "pfad-umgebung");

    private static final String[] ANFUEHRUNG = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB",
            "\u2018", "\u2019", "\u201A", "\u2039", "\u203A"};
    private static final String[] TOKEN = {"/", "/", "//", "/a", "/a/b", "~/", "~", "~/x", "(", ")", "[", "]", "{", "}",
            "=", " ", " ", " ", "\\", "C:\\", "d:/", "\\\\", "\\\\srv", ".", ",", ";", ":", "!", "?", "'", "*", "a", "b c",
            "x", "\"", "\t", "\n", "<", ">", "|", "\uD83D\uDE00", "e\u0301", "\u00A0", "\u200B", "-", "_", "@", "s",
            "Mein", "(/)", "()", "/opt", "$", "%", "\u2028", "~erika/", "~_x/a", "host:/", "ab:/", "srv.example:/", ":/",
            "h:/", "http:/", "::/", "x.y", "9", "~a"};

    // ------------------------------------------------------------------ (1) Differenztest

    @Test
    void differenzTestAlleFormenDeckenAbWasNurLaufwerkUndUncAbdecken() {
        Random rnd = new Random(SEED);
        int groesser = 0;
        int mitUnix = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd, 30, 4);
            List<Finding> alle = R.detect(text);
            List<Finding> alt = R.detect(text, ALT);
            pruefeFunde(text, alle, it);
            pruefeFunde(text, alt, it);
            boolean[] a = abdeckung(text.length(), alle);
            boolean[] b = abdeckung(text.length(), alt);
            boolean echtGroesser = false;
            for (int i = 0; i < text.length(); i++) {
                if (b[i] && !a[i]) {
                    fail("Position nicht mehr abgedeckt, Startwert " + SEED + ", Iteration " + it);
                }
                if (a[i] && !b[i]) {
                    echtGroesser = true;
                }
            }
            boolean[] ohneAlle = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] ohneAlt = abdeckung(text.length(), R.detectWithoutQuotes(text, ALT));
            for (int i = 0; i < text.length(); i++) {
                if (ohneAlt[i] && !ohneAlle[i]) {
                    fail("Position ohne Zitatregel nicht mehr abgedeckt, Startwert " + SEED + ", Iteration " + it);
                }
            }
            for (Finding f : alle) {
                if (f.source().equals("pfad-unix") || f.source().equals("pfad-home")) {
                    mitUnix++;
                    break;
                }
            }
            if (echtGroesser) {
                groesser++;
            }
        }
        assertTrue(groesser > ITERATIONEN_DIFFERENZ / 20, "zu wenige echte Vergroesserungen: " + groesser);
        assertTrue(mitUnix > ITERATIONEN_DIFFERENZ / 10, "zu wenige Texte mit Unix- oder Home-Fund: " + mitUnix);
    }

    @Test
    void differenzTestAlleFormenDeckenAbWasDurchlauf1MitVierFormenAbdeckt() {
        Random rnd = new Random(SEED + 7);
        int groesser = 0;
        for (int it = 0; it < ITERATIONEN_DIFFERENZ; it++) {
            String text = zufallstext(rnd, 30, 4);
            boolean[] alle = abdeckung(text.length(), R.detect(text));
            boolean[] alt = abdeckung(text.length(), R.detect(text, DURCHLAUF1));
            boolean[] alleOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
            boolean[] altOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, DURCHLAUF1));
            boolean echtGroesser = false;
            for (int i = 0; i < text.length(); i++) {
                if (alt[i] && !alle[i]) {
                    fail("Position nicht mehr abgedeckt, Startwert " + (SEED + 7) + ", Iteration " + it);
                }
                if (altOhne[i] && !alleOhne[i]) {
                    fail("Position ohne Zitatregel nicht mehr abgedeckt, Startwert " + (SEED + 7) + ", Iteration "
                            + it);
                }
                echtGroesser |= alle[i] && !alt[i];
            }
            if (echtGroesser) {
                groesser++;
            }
        }
        assertTrue(groesser > ITERATIONEN_DIFFERENZ / 50, "zu wenige echte Vergroesserungen: " + groesser);
    }

    @Test
    void differenzTestJedeEinzelneFormIstInAllenFormenEnthalten() {
        Random rnd = new Random(SEED + 1);
        for (int it = 0; it < 60_000; it++) {
            String text = zufallstext(rnd, 12, 3);
            boolean[] alle = abdeckung(text.length(), R.detect(text));
            boolean[] alleOhne = abdeckung(text.length(), R.detectWithoutQuotes(text));
            for (PathForm form : PathForm.values()) {
                boolean[] einzeln = abdeckung(text.length(), R.detect(text, new PathForm[] {form}));
                boolean[] einzelnOhne = abdeckung(text.length(), R.detectWithoutQuotes(text, new PathForm[] {form}));
                for (int i = 0; i < text.length(); i++) {
                    if (einzeln[i] && !alle[i]) {
                        fail(form + ": Position nicht mehr abgedeckt, Startwert " + (SEED + 1) + ", Iteration " + it);
                    }
                    if (einzelnOhne[i] && !alleOhne[i]) {
                        fail(form + ": Position ohne Zitatregel nicht abgedeckt, Startwert " + (SEED + 1)
                                + ", Iteration " + it);
                    }
                }
            }
        }
    }

    @Test
    void differenzTestMitDichtenKurzenTexten() {
        Random rnd = new Random(SEED + 2);
        for (int it = 0; it < 80_000; it++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(10);
            for (int k = 0; k < len; k++) {
                int r = rnd.nextInt(10);
                if (r < 2) {
                    sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
                } else if (r < 5) {
                    sb.append(rnd.nextBoolean() ? "/" : "~/");
                } else {
                    sb.append(TOKEN[rnd.nextInt(TOKEN.length)]);
                }
            }
            String text = sb.toString();
            boolean[] a = abdeckung(text.length(), R.detect(text));
            boolean[] b = abdeckung(text.length(), R.detect(text, ALT));
            for (int i = 0; i < text.length(); i++) {
                if (b[i] && !a[i]) {
                    fail("Position nicht mehr abgedeckt (dicht), Startwert " + (SEED + 2) + ", Iteration " + it);
                }
            }
        }
    }

    // ------------------------------------------------------------------ (2) Orakel je einzelne Form

    private record Fund(int start, int ende, double konfidenz, String quelle) {
    }

    /**
     * Naive Referenz fuer den Durchlauf ohne Anfuehrungszeichen mit nur einer Form: Kopf aus
     * {@code PathForm}, aber Startgrenze (eigene, woertlich ausgeschriebene Zeichenliste), Lauf, Kuerzung und
     * Segmentzaehlung neu und ohne Laufdaten (quadratisch, nur fuer kurze Texte). Konfidenz und Quelle als Literale
     * je Form, nicht aus {@code PathForm}.
     */
    private static List<Fund> orakel(String text, PathForm form) {
        List<Fund> r = new ArrayList<>();
        int n = text.length();
        int pos = 0;
        while (pos < n) {
            if (PathChars.isTerminator(text.charAt(pos))) {
                pos++;
                continue;
            }
            int laufEnde = pos;
            while (laufEnde < n && !PathChars.isTerminator(text.charAt(laufEnde))) {
                laufEnde++;
            }
            boolean ohneGrenze = form == PathForm.LAUFWERK || form == PathForm.UNC || form == PathForm.HOST_PFAD;
            int kopf = (!ohneGrenze && !startgrenze(text, pos)) ? -1
                    : kopf(text, pos, form);
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

    private static final java.util.regex.Pattern HOME_KOPF = java.util.regex.Pattern
            .compile("~(?:/|[A-Za-z_][A-Za-z0-9._-]{0,63}/)");
    private static final java.util.regex.Pattern HOST_KOPF = java.util.regex.Pattern
            .compile("([A-Za-z0-9._-]{2,64}):/(?!/)");

    /** Oeffnende und sonstige Startgrenzzeichen ausser den Anfuehrungszeichen (Plan Nachtrag Punkte 1 und 3b). */
    private static final String START_ZEICHEN = "([{<>=,;|";

    /**
     * Startgrenze laut Referenz, unabhaengig von {@code PathChars}: Textanfang, Leerraum, Steuerzeichen, eines der 13
     * Anfuehrungszeichen aus {@link #ANFUEHRUNG} (woertliche Liste dieser Datei) oder eines aus {@link #START_ZEICHEN}.
     */
    private static boolean startgrenze(String text, int pos) {
        if (pos == 0) {
            return true;
        }
        char p = text.charAt(pos - 1);
        if (Character.isWhitespace(p) || Character.isSpaceChar(p) || Character.isISOControl(p)) {
            return true;
        }
        for (String a : ANFUEHRUNG) {
            if (a.charAt(0) == p) {
                return true;
            }
        }
        return START_ZEICHEN.indexOf(p) >= 0;
    }

    /**
     * Kopfende laut Referenz: HOME und HOST_PFAD per regulaerem Ausdruck (unabhaengig vom Kopf der Form), die uebrigen
     * Formen ueber {@code PathForm.headEnd}. HOST_PFAD: Lauf aus 2 bis 64 Host-Zeichen mit Buchstabe direkt vor
     * {@code :/} (nicht {@code ://}), beginnend am Laufanfang oder, bei genau 64 Zeichen, mitten in einem laengeren Lauf.
     */
    private static int kopf(String text, int pos, PathForm form) {
        if (form == PathForm.HOME) {
            java.util.regex.Matcher m = HOME_KOPF.matcher(text).region(pos, text.length());
            return m.lookingAt() ? m.end() : -1;
        }
        if (form == PathForm.HOST_PFAD) {
            java.util.regex.Matcher m = HOST_KOPF.matcher(text).region(pos, text.length());
            if (!m.lookingAt()) {
                return -1;
            }
            String lauf = m.group(1);
            boolean wortanfang = pos == 0 || !PathChars.isHostChar(text.charAt(pos - 1));
            boolean buchstabe = lauf.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
            return buchstabe && (wortanfang || lauf.length() == 64) ? m.end() : -1;
        }
        return form.headEnd(text, pos);
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

    /** Anzahl der nichtleeren Segmente in {@code [von, bis)} (Trenner {@code /} und {@code \}). */
    private static int segmente(String text, int von, int bis) {
        int z = 0;
        boolean im = false;
        for (int i = von; i < bis; i++) {
            char c = text.charAt(i);
            if (c == '/' || c == '\\') {
                im = false;
            } else if (!im) {
                im = true;
                z++;
            }
        }
        return z;
    }

    private static int mindest(PathForm form) {
        switch (form) {
            case UNIX:
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
            default:
                throw new IllegalStateException("Form ohne Orakel: " + form);
        }
    }

    @Test
    void orakelJedeEinzelneFormStimmtMitDerReferenzUeberein() {
        Random rnd = new Random(SEED + 3);
        int[] mitFund = new int[PathForm.values().length];
        for (int it = 0; it < ITERATIONEN_ORAKEL; it++) {
            String text = zufallstext(rnd, 1 + rnd.nextInt(10), 0);
            for (PathForm form : ORAKEL_FORMEN) {
                List<Fund> soll = orakel(text, form);
                List<Finding> ist = R.detectWithoutQuotes(text, new PathForm[] {form});
                String wo = ", Form " + form + ", Startwert " + (SEED + 3) + ", Iteration " + it;
                assertEquals(soll.size(), ist.size(), "Fundzahl" + wo);
                for (int i = 0; i < soll.size(); i++) {
                    assertEquals(soll.get(i).start(), ist.get(i).start(), "Start" + wo);
                    assertEquals(soll.get(i).ende(), ist.get(i).end(), "Ende" + wo);
                    assertEquals(soll.get(i).konfidenz(), ist.get(i).confidence(), "Konfidenz" + wo);
                    assertEquals(soll.get(i).quelle(), ist.get(i).source(), "Quelle" + wo);
                }
                if (!soll.isEmpty()) {
                    mitFund[form.ordinal()]++;
                }
            }
        }
        for (PathForm form : ORAKEL_FORMEN) {
            assertTrue(mitFund[form.ordinal()] > 5_000, form + ": Orakel zu selten angewandt: " + mitFund[form.ordinal()]);
        }
    }

    /** Gezielt Texte, in denen das Klammer-Segment (Auflage 1) den Ausschlag gibt. */
    @Test
    void orakelMitKlammerSegmentenFuerJedeForm() {
        String[] koepfe = {"/", "~/", "C:\\", "\\\\", "//", "=/", "(/", "~erika/", "host:/", "ab.c:/", ",/", "|/"};
        String[] teile = {"a", "(", ")", "/", "\\", "[", "]", "{", "}", ".", ",", "a(", ")", "(/)", "x"};
        Random rnd = new Random(SEED + 4);
        int mitGewinn = 0;
        for (int it = 0; it < 150_000; it++) {
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
            for (PathForm form : ORAKEL_FORMEN) {
                List<Fund> soll = orakel(text, form);
                List<Finding> ist = R.detectWithoutQuotes(text, new PathForm[] {form});
                String wo = ", Form " + form + ", Startwert " + (SEED + 4) + ", Iteration " + it;
                assertEquals(soll.size(), ist.size(), "Fundzahl" + wo);
                for (int i = 0; i < soll.size(); i++) {
                    assertEquals(soll.get(i).start(), ist.get(i).start(), "Start" + wo);
                    assertEquals(soll.get(i).ende(), ist.get(i).end(), "Ende" + wo);
                    assertEquals(soll.get(i).konfidenz(), ist.get(i).confidence(), "Konfidenz" + wo);
                }
                // pessimistisch zu wenig, tatsaechlich genug: UNIX-Fund, der auf ausgeglichenen Klammern am Ende beruht
                if (form == PathForm.UNIX && !soll.isEmpty() && text.endsWith(")") && text.contains("(/")) {
                    mitGewinn++;
                }
            }
        }
        assertTrue(mitGewinn > 500, "Klammer-Segment-Faelle zu selten: " + mitGewinn);
    }

    /**
     * Durchlauf mit allen Formen: Quelle und Konfidenz als Menge (ein Lauf liefert je Durchlauf hoechstens einen Fund,
     * zusammengelegte Funde tragen die Quelle des frueher beginnenden und die niedrigste Konfidenz, Plan 7a). Jeder Fund
     * ist ausserdem von mindestens einem einzelnen Orakelfund einer Form beruehrt, und seine Konfidenz ist nie hoeher
     * als die hoechste Konfidenz seiner Quelle.
     */
    @Test
    void orakelAlleFormenQuelleUndKonfidenzAlsMenge() {
        Random rnd = new Random(SEED + 5);
        for (int it = 0; it < 100_000; it++) {
            String text = zufallstext(rnd, 1 + rnd.nextInt(10), 0);
            String wo = ", Startwert " + (SEED + 5) + ", Iteration " + it;
            List<Finding> alle = R.detectWithoutQuotes(text);
            for (Finding f : alle) {
                assertTrue(QUELLEN.contains(f.source()), "Quelle" + wo);
                assertTrue(Set.of(0.95, 0.90, 0.80, 0.75, 0.60).contains(f.confidence()), "Konfidenz" + wo);
                double max = f.source().equals("pfad-unc") || f.source().equals("pfad-laufwerk") ? 0.95 : 0.90;
                assertTrue(f.confidence() <= max, "Konfidenz hoechstens Maximum der Quelle" + wo);
                boolean beruehrt = false;
                for (PathForm form : ORAKEL_FORMEN) {
                    for (Fund o : orakel(text, form)) {
                        if (o.start() < f.end() && f.start() < o.ende()) {
                            beruehrt = true;
                        }
                    }
                }
                // Funde der Quellen pfad-relativ und pfad-umgebung stammen aus Formen ohne Orakel in dieser Datei; ihre
                // Pruefung ohne Ausnahme steht im Gesamtorakel von PathRecognizerRelativEigenschaftTest
                assertTrue(beruehrt || f.source().equals("pfad-relativ") || f.source().equals("pfad-umgebung"),
                        "Fund ohne beruehrenden Orakelfund" + wo);
            }
            // jede Orakelposition ist abgedeckt
            boolean[] a = abdeckung(text.length(), alle);
            for (PathForm form : ORAKEL_FORMEN) {
                for (Fund o : orakel(text, form)) {
                    for (int i = o.start(); i < o.ende(); i++) {
                        assertTrue(a[i], form + ": Orakelposition nicht abgedeckt" + wo);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static String zufallstext(Random rnd, int maxTokens, int anfuehrungsAnteil) {
        StringBuilder sb = new StringBuilder();
        int tokens = 1 + rnd.nextInt(maxTokens);
        for (int k = 0; k < tokens; k++) {
            if (anfuehrungsAnteil > 0 && rnd.nextInt(anfuehrungsAnteil + 1) == 0) {
                sb.append(ANFUEHRUNG[rnd.nextInt(ANFUEHRUNG.length)]);
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
    private static void pruefeFunde(String text, List<Finding> funde, int it) {
        int vorigesEnde = 0;
        String wo = ", Startwert " + SEED + ", Iteration " + it;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
            assertEquals(FindingType.PFAD, f.type(), "Typ" + wo);
            assertTrue(QUELLEN.contains(f.source()), "Quelle " + f.source() + wo);
            assertTrue(Set.of(0.95, 0.90, 0.80, 0.75, 0.60).contains(f.confidence()), "Konfidenz " + f.confidence() + wo);
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei" + wo);
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar" + wo);
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar" + wo);
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ (3) Pipeline Scanner -> Pseudonymizer -> Reidentifier

    private record Paar(String auf, String zu) {
    }

    private static final Paar[] PAARE = {new Paar("\"", "\""), new Paar("'", "'"), new Paar("`", "`"),
            new Paar("\u201E", "\u201C"), new Paar("\u201C", "\u201D"), new Paar("\u00BB", "\u00AB"),
            new Paar("\u2018", "\u2019"), new Paar("\u2039", "\u203A")};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach", "gilt",
            "Hinweis", "oben", "folgt", "nichts", "weiter", "und/oder", "km/h", "19/11/2026", "TCP/IP", "n/a"};
    /** Praefixe, die eine Startgrenze fuer Unix- und Home-Pfade ergeben (Plan 2.4.1). */
    private static final String[] VOR_UNIX = {"", "", " ", "x=", "(", "[", "{", "--config=", ">", "\n", "ab ", "Datei: ",
            "\t", "<", "Pfad ", "-> ", ",", ";", "|", "a,", "cmd|"};
    private static final String[] NACH = {"", "", ".", ",", ")", ";", "!", "?", " Ende", "\n", "\r\n", "]"};
    private static final String[] BASIS = {"Tools", "Daten", "Akten", "Projekt", "Gr\u00F6\u00DFe", "\u00DCbersicht",
            "BeispielTool", "e\u0301cole", "home", "Dokumente"};
    private static final String[] DATEI = {"akte.pdf", "tool.exe", "notiz.txt", "konfig.yml", "a.tar.gz", "x"};
    private static final String[] BENUTZER = {"erika.mustermann", "svc_beispiel", "_dienst", "max-muster", "E1"};
    private static final String[] SERVER ={"srv-beispiel", "srv01.praxis.example", "srv.beispiel.invalid"};

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

    private static String unixPfad(Random rnd, boolean zitat, List<String> namen) {
        StringBuilder sb = new StringBuilder();
        int art = rnd.nextInt(8);
        int segmente = 2 + rnd.nextInt(3);
        if (art == 0) {
            sb.append("/");
            String server = SERVER[rnd.nextInt(SERVER.length)] + rnd.nextInt(1000);
            namen.add(server);
            sb.append("/").append(server);
            segmente = 1 + rnd.nextInt(3);
        } else if (art == 1) {
            sb.append("/home/erika.mustermann");
            namen.add("erika.mustermann");
            segmente = 1 + rnd.nextInt(3);
        }
        for (int s = 0; s < segmente; s++) {
            String n = name(rnd, zitat);
            if (s == segmente - 1 && rnd.nextBoolean()) {
                n = endung(rnd, n);
            }
            namen.add(n);
            sb.append(s > 0 && rnd.nextInt(7) == 0 ? "\\" : "/").append(n);
        }
        return sb.toString();
    }

    private static String homePfad(Random rnd, boolean zitat, List<String> namen) {
        StringBuilder sb = new StringBuilder("~");
        if (rnd.nextInt(3) == 0) {
            String benutzer = BENUTZER[rnd.nextInt(BENUTZER.length)] + rnd.nextInt(100);
            namen.add(benutzer);
            sb.append(benutzer);
        }
        int segmente = 1 + rnd.nextInt(3);
        for (int s = 0; s < segmente; s++) {
            String n = name(rnd, zitat);
            if (s == segmente - 1 && rnd.nextBoolean()) {
                n = endung(rnd, n);
            }
            namen.add(n);
            sb.append("/").append(n);
        }
        return sb.toString();
    }

    /** Rechner vor dem Pfad ({@code srv-beispiel12:/export/Akten3/x}). */
    private static String hostPfad(Random rnd, boolean zitat, List<String> namen) {
        String host = SERVER[rnd.nextInt(SERVER.length)] + rnd.nextInt(1000);
        namen.add(host);
        StringBuilder sb = new StringBuilder(host).append(":");
        int segmente = 1 + rnd.nextInt(3);
        for (int s = 0; s < segmente; s++) {
            String n = name(rnd, zitat);
            if (s == segmente - 1 && rnd.nextBoolean()) {
                n = endung(rnd, n);
            }
            namen.add(n);
            sb.append("/").append(n);
        }
        return sb.toString();
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
        int segmente = 1 + rnd.nextInt(3);
        for (int s = 0; s < segmente; s++) {
            String n = name(rnd, zitat);
            namen.add(n);
            if (s > 0) {
                sb.append("\\");
            }
            sb.append(n);
        }
        return sb.toString();
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
            int art = rnd.nextInt(10);
            if (art == 0) {
                int start = sb.length();
                sb.append("https://example.invalid/").append(rnd.nextBoolean() ? "a/b/c" : "seite/neu")
                        .append(rnd.nextBoolean() ? "?f=/home/erika.mustermann/x" : "");
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
            String pfad;
            String vor;
            int wahl = rnd.nextInt(12);
            if (wahl < 5) {
                pfad = unixPfad(rnd, zitat, pfadNamen);
                vor = VOR_UNIX[rnd.nextInt(VOR_UNIX.length)];
            } else if (wahl < 7) {
                pfad = homePfad(rnd, zitat, pfadNamen);
                vor = VOR_UNIX[rnd.nextInt(VOR_UNIX.length)];
            } else if (wahl < 9) {
                pfad = hostPfad(rnd, zitat, pfadNamen);
                vor = rnd.nextBoolean() ? "" : VOR_UNIX[rnd.nextInt(VOR_UNIX.length)];
            } else {
                pfad = windowsPfad(rnd, zitat, pfadNamen);
                vor = rnd.nextBoolean() ? "" : "x=";
            }
            if (zitat) {
                sb.append(paar.auf());
            } else {
                sb.append(vor);
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
    void zufaelligeUnixHomeUncUndLaufwerkspfadeSindGanzAbgedecktOhneKlartextUndMitRoundtrip() {
        Random rnd = new Random(SEED + 6);
        TextScanner scanner = TextScanner.standard();
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        int unixFunde = 0;
        int urlsStreng = 0;
        for (int it = 0; it < ITERATIONEN_PIPELINE; it++) {
            Gen g = erzeuge(rnd);
            String text = g.text();
            String wo = ", Startwert " + (SEED + 6) + ", Iteration " + it;
            List<Finding> funde = scanner.scan(text);

            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
                assertTrue(f.start() >= vorigesEnde, "Ueberlappung" + wo);
                vorigesEnde = f.end();
                if (f.source().equals("pfad-unix") || f.source().equals("pfad-home")) {
                    unixFunde++;
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
                            // ohne Anfuehrungszeichen im Text: genau der URL-Fund, unveraendert (Enthaltensein)
                            assertEquals(FindingType.URL, f.type(), "URL-Typ" + wo);
                            assertEquals(url[0], f.start(), "URL-Start" + wo);
                            assertEquals(url[1], f.end(), "URL-Ende" + wo);
                        } else {
                            // ein falsch gepaartes Anfuehrungszeichen (Zitat ueber die URL hinweg, T-11 Nachtrag 5) darf die
                            // URL zusammenlegen, aber nie zersplittern: ein Fund deckt die ganze URL
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
        assertTrue(unixFunde > ITERATIONEN_PIPELINE, "zu wenige Unix-/Home-Funde im Test: " + unixFunde);
        assertTrue(urlsStreng > 500, "zu wenige URLs ohne Anfuehrungszeichen im Text: " + urlsStreng);
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
