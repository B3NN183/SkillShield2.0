package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests der Form UMGEBUNG (T-15, AK-15; Plan 2.4.2 Zeile UMGEBUNG). Pfade mit Backslash werden mit {@code bs(...)}
 * geschrieben (Marker U+00A6 steht fuer einen Backslash, Plan Kap. 2.10). Alle Namen sind synthetisch.
 * Nicht-ASCII-Zeichen stehen im Quelltext nur als Escapes, ausgenommen der Marker U+00A6 in den {@code bs(...)}-Texten.
 * Es wird nichts aufgeloest: Die Tests setzen keine Umgebungsvariable und lesen keine.
 *
 * <p>Gliederung: (1) Zeile UMGEBUNG laut Plan, positiv und negativ (AK-15); (2) Namenslaenge 64 ja, 65 nein;
 * (3) Kopfvarianten (Windows, Unix, Ketten, Batch, PowerShell, Makefile, verzoegerte Expansion {@code !NAME!});
 * (4) gewoehnlicher Text ohne Fund (Prozent-, Waehrungs-, Datumsformate, URL-Kodierung); (5) Startgrenze (keine);
 * (6) Kuerzung und Klammern; (7) Anfuehrungszeichen (13 Arten); (8) Verhaeltnis zu den bestehenden Formen und zum
 * Scanner; (9) bekannte Folgen und Grenzen; (10) Unicode; (11) Formvertrag; (12) Grenzlast (AK-49), je unter 2 s.
 *
 * <p>Jeder Test, der Funde ueber {@code funde(...)} (und damit {@code umgebung}, {@code fund}, {@code keinFund},
 * {@code werte}) pruefen laesst, prueft je Fund: Teilstring gleich Originalwert, Typ PFAD, sortiert,
 * ueberlappungsfrei, keine Grenze im Surrogatpaar; Quelle und Konfidenz pruefen {@code umgebung} und {@code fund}.
 * Tests, die {@code R.detect} oder die Formmethoden direkt aufrufen, pruefen das nur, wo sie es selbst tun. Die
 * Einteilung der Tests ist: laut Plan (Abschnitte 1 und 2),
 * eigene Entscheidung (Abschnitte 3 und 5, Test-Namen mit {@code entscheidung}) und bekannte Folge (Abschnitt 9,
 * Test-Namen mit {@code bekannteFolge} bzw. {@code bekannteGrenze}).
 */
class PathRecognizerUmgebungTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private static final int N = 1_000_000;
    private static final PathForm[] NUR_UMGEBUNG = {PathForm.UMGEBUNG};

    /** Die 13 Anfuehrungszeichen-Arten aus T-11 (Escapes), woertlich. */
    private static final String[] ANF = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB", "\u2018",
            "\u2019", "\u201A", "\u2039", "\u203A"};

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> funde = R.detect(text);
        pruefeStruktur(text, funde);
        return funde;
    }

    private static void pruefeStruktur(String text, List<Finding> funde) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar");
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar");
            vorigesEnde = f.end();
        }
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    /** Genau ein Fund {@code erwartet}, Quelle pfad-umgebung, Konfidenz 0.80 (WAHRSCHEINLICH, nie SICHER). */
    private static Finding umgebung(String text, String erwartet) {
        return fund(text, erwartet, "pfad-umgebung", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
    }

    private static Finding fund(String text, String erwartet, String quelle, double konfidenz, ConfidenceLevel stufe) {
        List<Finding> f = funde(text);
        assertEquals(List.of(erwartet), f.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Text: " + text);
        assertEquals(quelle, f.get(0).source(), "Quelle, Text: " + text);
        assertEquals(konfidenz, f.get(0).confidence(), "Konfidenz, Text: " + text);
        assertEquals(stufe, f.get(0).level(), "Stufe, Text: " + text);
        return f.get(0);
    }

    private static void keinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    /** Kein PFAD-Fund im Erkenner und im Standard-Scanner; der Text bleibt beim Pseudonymisieren unveraendert. */
    private static void keinFundAuchImScanner(String text) {
        keinFund(text);
        List<Finding> s = SCANNER.scan(text);
        assertTrue(s.stream().noneMatch(f -> f.type() == FindingType.PFAD), "Scanner, Text: " + text);
        assertEquals(text, new Pseudonymizer().pseudonymize(text, s).text(), "Text bleibt unveraendert");
    }

    /**
     * Genau ein Fund {@code erwartet} im Erkenner und im Standard-Scanner; er steht nicht mehr im pseudonymisierten
     * Text, der Roundtrip ist zeichengleich.
     */
    private static void umgebungUeberScanner(String text, String erwartet) {
        umgebung(text, erwartet);
        List<Finding> s = SCANNER.scan(text);
        assertEquals(List.of(erwartet), s.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Scanner, Text: " + text);
        assertEquals(FindingType.PFAD, s.get(0).type());
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, s);
        assertFalse(p.text().contains(erwartet), "Fund im pseudonymisierten Text");
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "Meldung");
    }

    /** Position der ersten Variable ({@code $} oder {@code %}) im Text, -1 wenn keine. */
    private static int ersteVariable(String text) {
        int d = text.indexOf('$');
        int p = text.indexOf('%');
        return d < 0 ? p : p < 0 ? d : Math.min(d, p);
    }

    // ================================================================== (1) Zeile UMGEBUNG laut Plan, AK-15

    @Test
    void ak15AppdataMitBackslashEinFundUeberDenGanzenAusdruck() {
        String pfad = bs("%APPDATA%¦BeispielTool¦konfig.ini");
        umgebungUeberScanner(pfad, pfad);
        umgebung("Siehe " + pfad + " jetzt", pfad);
        umgebung("Siehe " + pfad + ".", pfad);
    }

    @Test
    void ak15HomeMitSchraegstrichEinFundUeberDenGanzenAusdruck() {
        umgebungUeberScanner("$HOME/beispiel/konfig.yml", "$HOME/beispiel/konfig.yml");
        umgebung("Siehe $HOME/beispiel/konfig.yml jetzt", "$HOME/beispiel/konfig.yml");
    }

    @Test
    void ak15HomeInGeschweiftenKlammernEinFundUeberDenGanzenAusdruck() {
        umgebungUeberScanner("${HOME}/beispiel/konfig.yml", "${HOME}/beispiel/konfig.yml");
        umgebung("Siehe ${HOME}/beispiel/konfig.yml jetzt", "${HOME}/beispiel/konfig.yml");
    }

    @Test
    void zeileUmgebungPositivProgramFilesMitKlammernImNamen() {
        umgebung(bs("%ProgramFiles(x86)%¦x"), bs("%ProgramFiles(x86)%¦x"));
        umgebung(bs("Starte %ProgramFiles(x86)%¦BeispielTool¦tool.exe jetzt"),
                bs("%ProgramFiles(x86)%¦BeispielTool¦tool.exe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%APPDATA%", "$HOME", "${HOME}", "$1/x", "%%", "% x", "%PATH%", "$USER", "%USERNAME%",
            "echo $USER", "echo %USERNAME%", "%USERPROFILE%", "%USERPROFILE%¦", "$HOME/", "${HOME}/", "%APPDATA%¦.",
            "%windir%", "%windir%¦", "$(pwd)", "$(pwd)/"})
    void zeileUmgebungNegativVariableAlleinOhneSegmentIstKeinFund(String roh) {
        String text = bs(roh);
        keinFundAuchImScanner(text);
        keinFund("Siehe " + text + " im Text");
        if (!text.endsWith(".")) {
            // im Zitat wird der Inhalt ohne Kuerzung gezaehlt: ein Punkt am Ende waere dort ein Segment (Plan 2.4.3)
            keinFund("\"" + text + "\"");
        }
    }

    @Test
    void imZitatZaehltEinPunktAmEndeAlsSegmentDerInhaltWirdNichtGekuerzt() {
        umgebung("\"%APPDATA%\\.\"", "%APPDATA%\\.");
    }

    @Test
    void entscheidungPowerShellEnvIstSeitT15EinFundDerPlanNanntEsNichtAbgedeckt() {
        // Plan (Zeile UMGEBUNG, Negativbeispiel): "$env:APPDATA\x (PowerShell, nicht abgedeckt)"; seit der Empfehlung
        // aus der security-Pruefung T-14 und der Nutzerentscheidung vom 20.11.2026 ein Fund
        umgebung(bs("$env:APPDATA¦x"), bs("$env:APPDATA¦x"));
    }

    @Test
    void stufeNichtSicherSondernWahrscheinlich() {
        Finding f = umgebung("$HOME/a/b/c/d", "$HOME/a/b/c/d");
        assertEquals(0.80, f.confidence());
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, f.level());
        assertNotEquals(ConfidenceLevel.SICHER, f.level());
    }

    @Test
    void konfidenzHaengtNichtVonSegmentzahlOderLeerzeichenAb() {
        for (String t : new String[] {"$HOME/a", "$HOME/a/b", "$HOME/a/b/c", "$HOME/a/b/c/d/e/f"}) {
            umgebung(t, t);
        }
        umgebung("\"$HOME/Mein Ordner/a.txt\"", "$HOME/Mein Ordner/a.txt");
    }

    @Test
    void einSegmentHinterDemKopfGenuegt() {
        umgebung("$HOME/a", "$HOME/a");
        umgebung(bs("%APPDATA%¦a"), bs("%APPDATA%¦a"));
        umgebung("Datei ${HOME}/konfig.yml laden", "${HOME}/konfig.yml");
    }

    // ================================================================== (2) Namenslaenge 64 ja, 65 nein

    private static String name(int laenge) {
        return "a".repeat(laenge);
    }

    @Test
    void nameMitVierundsechzigZeichenIstEinFundMitFuenfundsechzigNicht() {
        String n64 = name(64);
        String n65 = name(65);
        umgebung("%" + n64 + bs("%¦x"), "%" + n64 + bs("%¦x"));
        keinFund("%" + n65 + bs("%¦x"));
        umgebung("$" + n64 + "/x", "$" + n64 + "/x");
        keinFund("$" + n65 + "/x");
        umgebung("${" + n64 + "}/x", "${" + n64 + "}/x");
        keinFund("${" + n65 + "}/x");
        umgebung("$(" + n64 + ")/x", "$(" + n64 + ")/x");
        keinFund("$(" + n65 + ")/x");
        umgebung("$env:" + n64 + "/x", "$env:" + n64 + "/x");
        keinFund("$env:" + n65 + "/x");
        umgebung("${env:" + n64 + "}/x", "${env:" + n64 + "}/x");
        keinFund("${env:" + n65 + "}/x");
    }

    @Test
    void nameMitKlammernImProzentNamenZaehlenMit() {
        String n64 = "A" + "(".repeat(31) + ")".repeat(32);
        assertEquals(64, n64.length());
        umgebung("%" + n64 + bs("%¦x"), "%" + n64 + bs("%¦x"));
        keinFund("%" + n64 + ")" + bs("%¦x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%1a%¦x", "%(a)%¦x", "%-a%¦x", "%a.b%¦x", "%a b%¦x", "%a-b%¦x", "$1a/x", "$-a/x", "$.a/x",
            "${1a}/x", "${a-b}/x", "${a(b)}/x", "${}/x", "$()/x", "$(a b)/x", "$(a(b))/x", "$$/x"})
    void ungueltigeNamenSindKeinKopf(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void unterstrichAmAnfangUndZiffernImNamenSindErlaubt() {
        umgebung(bs("%_a1%¦x"), bs("%_a1%¦x"));
        umgebung("$_a1/x", "$_a1/x");
        umgebung("${_1}/x", "${_1}/x");
        umgebung("$(_1)/x", "$(_1)/x");
        umgebung("$A1B2/x", "$A1B2/x");
    }

    // ================================================================== (3) Kopfvarianten

    @ParameterizedTest
    @ValueSource(strings = {"%USERPROFILE%¦Desktop¦akte.pdf", "%APPDATA%¦BeispielTool¦konfig.ini",
            "%HOMEDRIVE%%HOMEPATH%¦x", "%SystemRoot%¦System32¦drivers¦etc¦hosts", "%LOCALAPPDATA%/Temp/a",
            "%ProgramFiles%¦Mein Tool", "%TEMP%¦a¦b", "%a%%b%%c%%d%¦x", "%appdata%/x", "%A%¦x¦"})
    void entscheidungWindowsKoepfeMitProzentzeichen(String roh) {
        String text = bs(roh);
        List<Finding> f = funde(text);
        assertEquals(1, f.size(), text);
        assertEquals("pfad-umgebung", f.get(0).source());
        assertEquals(0.80, f.get(0).confidence());
        assertTrue(text.startsWith(f.get(0).originalValue()), "Fund beginnt am Anfang: " + text);
    }

    @Test
    void entscheidungWindowsBeispielwerteSindAmEndeVollstaendig() {
        umgebung(bs("%USERPROFILE%¦Desktop¦akte.pdf"), bs("%USERPROFILE%¦Desktop¦akte.pdf"));
        umgebung(bs("%HOMEDRIVE%%HOMEPATH%¦x"), bs("%HOMEDRIVE%%HOMEPATH%¦x"));
        umgebung(bs("%SystemRoot%¦System32¦drivers¦etc¦hosts"), bs("%SystemRoot%¦System32¦drivers¦etc¦hosts"));
        umgebung(bs("Aus %APPDATA%¦BeispielTool¦konfig.ini lesen"), bs("%APPDATA%¦BeispielTool¦konfig.ini"));
    }

    @Test
    void entscheidungKetteVonBisZuVierProzentGruppen() {
        umgebung(bs("%a%¦x"), bs("%a%¦x"));
        umgebung(bs("%a%%b%¦x"), bs("%a%%b%¦x"));
        umgebung(bs("%a%%b%%c%¦x"), bs("%a%%b%%c%¦x"));
        umgebung(bs("%a%%b%%c%%d%¦x"), bs("%a%%b%%c%%d%¦x"));
        // fuenf Gruppen: der Fund beginnt an der zweiten Gruppe (vier Gruppen und Trenner), die erste bleibt stehen
        umgebung(bs("%a%%b%%c%%d%%e%¦x"), bs("%b%%c%%d%%e%¦x"));
        keinFund(bs("%a%%b%¦"));
        keinFund(bs("%a%%b%x¦y"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%~dp0..¦erika¦a", "%~dpn0¦x", "%~f1¦a", "%~1¦a", "%~dp0¦x", "%~DP0¦x", "%~dp0sub¦x",
            "%~dpnxsatz0/x", "%~9/x", "%~dp0../erika/a", "%~dp0.¦.¦x", "%~Fdp0¦a"})
    void entscheidungBatchParameterMitTildeSindEinFundAbDemZweitenSegment(String roh) {
        umgebung(bs(roh), bs(roh));
        umgebung("call \"" + bs(roh) + "\" -x", bs(roh));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%~dp0", "%~dp0tool.exe", "%~dp0..¦", "%~dp0¦", "%~dp0..", "%~", "%~dp", "%~dp0x",
            "%~dpnxsatzf0/x", "%~q0¦x", "%~dp0 ¦x", "%1¦x", "%0¦x", "%*¦x", "%~$PATH:1¦x"})
    void entscheidungBatchParameterOhneSegmentOderMitFalschemKopfIstKeinFund(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void entscheidungBatchRestVorDemTrennerHatHoechstensVierundsechzigZeichen() {
        String rest64 = "a".repeat(64);
        String rest65 = "a".repeat(65);
        umgebung("%~dp0" + rest64 + "/x", "%~dp0" + rest64 + "/x");
        // 65 Zeichen: kein Kopf am %~dp0; der Rest ist ein einziges Segment, das Segment dahinter zaehlt aber nicht
        keinFund("%~dp0" + rest65 + "/x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$env:USERPROFILE¦Desktop¦akte.pdf", "$Env:APPDATA¦x", "$ENV:TEMP/x", "$eNv:A/b",
            "${env:APPDATA}¦x", "${ENV:APPDATA}/x", "${env:ProgramFiles(x86)}¦x", "${Env:A}¦x¦y", "$env:A_1¦x",
            "$PSScriptRoot¦x", "$HOME¦Desktop¦a"})
    void entscheidungPowerShellKoepfe(String roh) {
        String text = bs(roh);
        List<Finding> f = funde(text);
        assertEquals(1, f.size(), text);
        assertEquals(text, f.get(0).originalValue());
        assertEquals("pfad-umgebung", f.get(0).source());
        assertEquals(0.80, f.get(0).confidence());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$env:¦x", "$env:/x", "${env:}¦x", "${env:a b}¦x", "$env:A(x)¦y", "${env:1a}¦x", "$env¦",
            "${env:A¦x", "${env}", "$env:A"})
    void entscheidungPowerShellOhneGueltigenNamenOderSegmentIstKeinFundDerFormUmgebung(String roh) {
        assertEquals(List.of(), R.detect(bs(roh), NUR_UMGEBUNG), "Text: " + roh);
    }

    @Test
    void bekannteFolgePowerShellEnvOhneNamenGibtEinenLaufwerksFundAbDemLetztenBuchstaben() {
        // seit T-10 Folge der Laufwerksregel (kein Startzeichen): v:\x aus "$env:\x", 0.95; nicht Teil von UMGEBUNG
        fund(bs("$env:¦x"), bs("v:¦x"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void entscheidungPlainEnvAlsNameOhneDoppelpunktIstEinGewoehnlicherName() {
        umgebung("$env/x", "$env/x");
        umgebung("${env}/x", "${env}/x");
        umgebung("$ENV/a/b", "$ENV/a/b");
    }

    @Test
    void entscheidungMakefileUndShellKlammernSchreibweise() {
        umgebung("$(pwd)/erika/x", "$(pwd)/erika/x");
        umgebung("$(HOME)/x", "$(HOME)/x");
        umgebung("cd $(CURDIR)/build", "$(CURDIR)/build");
        keinFund("$(pwd)");
        keinFund("$(dirname $0)/x");
        keinFund("$(pwd /x");
    }

    @Test
    void entscheidungUnixBeispielwerte() {
        umgebung("$HOME/akte.pdf", "$HOME/akte.pdf");
        umgebung("${HOME}/akte.pdf", "${HOME}/akte.pdf");
        umgebung("$XDG_CONFIG_HOME/app/x", "$XDG_CONFIG_HOME/app/x");
        umgebung("$HOME/../erika/x", "$HOME/../erika/x");
        umgebung("$VAR/./erika/a", "$VAR/./erika/a");
        umgebung("$HOME//erika//x", "$HOME//erika//x");
        umgebung(bs("$HOME¦erika¦x"), bs("$HOME¦erika¦x"));
        umgebung(bs("$HOME/erika¦x"), bs("$HOME/erika¦x"));
    }

    @Test
    void entscheidungEinTrennerAmEndeGenuegtNichtAberEinPunktSegmentBleibtStehen() {
        keinFund("$HOME/");
        keinFund("$HOME//");
        keinFund("$HOME/.");
        keinFund("$HOME/..");
        umgebung("$HOME/./a", "$HOME/./a");
        umgebung("$HOME/a/", "$HOME/a/");
    }

    // ---- verzoegerte Batch-Expansion !NAME! (Plan Nachtrag T-15 Punkt 6)

    @ParameterizedTest
    @ValueSource(strings = {"!USERPROFILE!¦Desktop¦akte.pdf", "!APPDATA!/x", "!ProgramFiles!¦Mein Tool¦tool.exe",
            "!A!¦x¦", "!_a1!/x", "!ProgramFiles(x86)!¦x", "!appdata!/x/y", "!A!//x", "!A!¦¦x"})
    void entscheidungVerzoegerteExpansionMitAusrufezeichenIstEinKopf(String roh) {
        String text = bs(roh);
        List<Finding> f = funde(text);
        assertEquals(1, f.size(), text);
        assertEquals("pfad-umgebung", f.get(0).source());
        assertEquals(0.80, f.get(0).confidence());
        assertTrue(text.startsWith(f.get(0).originalValue()), "Fund beginnt am Anfang: " + text);
    }

    @Test
    void entscheidungVerzoegerteExpansionBeispielwerteSindAmEndeVollstaendig() {
        umgebungUeberScanner(bs("!USERPROFILE!¦Desktop¦akte.pdf"), bs("!USERPROFILE!¦Desktop¦akte.pdf"));
        umgebungUeberScanner("!APPDATA!/x", "!APPDATA!/x");
        umgebung(bs("Starte !APPDATA!¦BeispielTool¦tool.exe jetzt"), bs("!APPDATA!¦BeispielTool¦tool.exe"));
        umgebung("Siehe !APPDATA!/x.", "!APPDATA!/x");
        // im Zitat bleibt das Leerzeichen im Fund (Plan 2.4.3), die Anfuehrungszeichen gehoeren nicht dazu
        umgebung(bs("\"!ProgramFiles!¦Mein Tool¦tool.exe\""), bs("!ProgramFiles!¦Mein Tool¦tool.exe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"!VAR!", "!VAR!¦", "!VAR!/", "!!¦x", "!1!¦x", "Achtung!/x", "Hallo! Wichtig!/a", "a!=b/c",
            "!a b!/x", "!a-b!/x", "!a.b!/x", "!A!x/y", "!A", "!", "!!", "!a!", "!/x", "! !/x", "!A !/x", "!A! /x",
            "Wirklich?!/a", "!-a!/x", "!$a!/x"})
    void entscheidungVerzoegerteExpansionOhneGueltigenKopfIstKeinFund(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void entscheidungVerzoegerteExpansionAusrufezeichenImTextOhneKopfAuchImScanner() {
        keinFundAuchImScanner("Achtung!/x");
        keinFundAuchImScanner("Hallo! Wichtig!/a");
        keinFundAuchImScanner("Wichtig! / Dringend! / Eilt!");
        keinFundAuchImScanner("a!=b/c");
    }

    @Test
    void entscheidungVerzoegerteExpansionNameMitVierundsechzigZeichenJaFuenfundsechzigNein() {
        umgebung("!" + name(64) + "!/x", "!" + name(64) + "!/x");
        keinFund("!" + name(65) + "!/x");
        String n64 = "A" + "(".repeat(31) + ")".repeat(32);
        assertEquals(64, n64.length());
        umgebung("!" + n64 + bs("!¦x"), "!" + n64 + bs("!¦x"));
        keinFund("!" + n64 + ")" + bs("!¦x"));
    }

    @Test
    void entscheidungVerzoegerteExpansionKenntKeineKetteDerFundBeginntAnDerLetztenGruppe() {
        umgebung("!a!!b!/x", "!b!/x");
        umgebung(bs("!a!!b!!c!¦x"), bs("!c!¦x"));
        // gemischt mit Prozentgruppen: die Kette gilt nur fuer %NAME%
        umgebung(bs("%a%!b!¦x"), bs("!b!¦x"));
        umgebung(bs("!a!%b%¦x"), bs("%b%¦x"));
    }

    @Test
    void entscheidungVerzoegerteExpansionBeginntOhneStartgrenzeUndBleibtNachDemTrennerBeimSegment() {
        umgebung("x=!HOME!/a/b", "!HOME!/a/b");
        umgebung("-I!HOME!/inc", "!HOME!/inc");
        umgebung("abc!HOME!/x", "!HOME!/x");
        umgebung(bs("set P=!USERPROFILE!¦x"), bs("!USERPROFILE!¦x"));
        umgebung("(!HOME!/x)", "!HOME!/x");
        umgebung("!HOME!/a:!HOME!/b", "!HOME!/a:!HOME!/b");
    }

    @Test
    void entscheidungVerzoegerteExpansionImBatchRestEinesBatchParametersGiltDerGanzeAusdruck() {
        umgebung(bs("%~dp0!A!¦x"), bs("%~dp0!A!¦x"));
        umgebung(bs("%~dp0!A!!B!¦x"), bs("%~dp0!A!!B!¦x"));
    }

    @Test
    void bekannteFolgeVerzoegerteExpansionAusrufezeichenUmEinWortVorEinemTrennerIstEinFehltreffer() {
        // ein von zwei Ausrufezeichen umschlossenes Wort, direkt gefolgt von Trenner und Segment: Fund mit 0.80
        umgebung("!wichtig!/a", "!wichtig!/a");
        umgebung("Ja!Nein!/x", "!Nein!/x");
        umgebung("Hallo !Welt!/Mond", "!Welt!/Mond");
        umgebung("Sehr !gut!/schlecht", "!gut!/schlecht");
    }

    // ================================================================== (4) gewoehnlicher Text: kein Fund

    /** Prozent- und Waehrungsangaben, Datums- und Formatmuster: ohne Kopf, also auch ohne Startgrenze kein Fund. */
    @ParameterizedTest
    @ValueSource(strings = {"50%/Jahr", "3%/4%", "5$/St\u00FCck", "100 %/Monat", "100%/Jahr", "Preis: 5$/St\u00FCck",
            "$1/2", "$5/Monat", "%d/%m/%Y", "%s/%s", "%20/", "%2F", "%E4%B8%AD/x", "%1\\%2", "50%\\n", "100%\\",
            "%d.%m.%Y", "%H:%M:%S", "%Y%m%d/x", "%y%m/a", "a%/b", "a$/b", "% /x", "$ /x", "$/x", "%/x", "$$/x", "%%/x",
            "$1.50/Stunde", "$20/Monat", "20$/Monat", "%a", "%a b%", "von 10% bis 20%/Jahr", "7%/8%/9%",
            "Zinsen 3,5%/Jahr", "Rabatt 5% / Skonto 2%", "a % b / c % d", "$ 5/Tag", "\u20AC/$ 1.10",
            "%s%d/x", "%e4%/", "100%/%Anteil", "$-5/Tag"})
    void alltagstextMitProzentUndDollarIstKeinFund(String roh) {
        String text = roh.replace('\u00A6', '\\');
        keinFundAuchImScanner(text);
        keinFundAuchImScanner("Der Wert " + text + " gilt hier.");
    }

    @Test
    void alltagstextProzentMitLeerzeichenZwischenNameUndSchlusszeichenIstKeinKopf() {
        keinFund("%a %/x");
        keinFund("% a%/x");
        keinFund("Gewinn %Umsatz /Jahr");
    }

    @Test
    void urlKodierungErzeugtKeinenKopf() {
        // hinter dem schliessenden Prozentzeichen steht in gueltiger URL-Kodierung immer eine Hexziffer, nie ein
        // Trenner
        for (String url : new String[] {"https://example.invalid/a%20b/%2F/%E4%B8%AD/x",
                "https://example.invalid/%C3%A4%C3%B6%C3%BC/%e4%b8%ad%2f%2F",
                "https://example.invalid/?q=%41%42%43&r=%E2%82%AC",
                "https://example.invalid/a%2Fb%2Fc/%5C%5C/x", "ftp://example.invalid/%ab%cd%ef/%AB%CD%EF/y"}) {
            assertEquals(List.of(), R.detect(url, NUR_UMGEBUNG), "nur UMGEBUNG: " + url);
            List<Finding> s = SCANNER.scan(url);
            assertEquals(1, s.size(), url);
            assertEquals(FindingType.URL, s.get(0).type(), url);
            assertEquals(url, s.get(0).originalValue(), url);
        }
    }

    @Test
    void urlMitVariableInQueryBleibtEinUrlFund() {
        for (String url : new String[] {"https://example.invalid/?x=%USERPROFILE%/y",
                "https://example.invalid/?x=$HOME/y", "https://example.invalid/$id/x",
                "https://example.invalid/?p=${HOME}/a/b",
                "https://example.invalid/a%20b/?x=%25APPDATA%25/y", "http://example.invalid/?f=%~dp0../a"}) {
            List<Finding> s = SCANNER.scan(url);
            assertEquals(1, s.size(), url);
            assertEquals(FindingType.URL, s.get(0).type(), url);
            assertEquals(url, s.get(0).originalValue(), url);
        }
    }

    @Test
    void emailMitProzentUndDollarImLokalteilBleibtEinFund() {
        for (String mail : new String[] {"erika%x@example.invalid"}) {
            List<Finding> s = SCANNER.scan("Mail an " + mail + " bitte");
            assertEquals(1, s.size(), mail);
            assertEquals(FindingType.EMAIL, s.get(0).type(), mail);
            assertEquals(mail, s.get(0).originalValue(), mail);
        }
    }

    // ================================================================== (5) Startgrenze: keine (eigene Entscheidung)

    @Test
    void startgrenzeBeispieleAusDemAuftrag() {
        umgebung(bs("PATH=%PATH%;%APPDATA%¦x"), bs("%APPDATA%¦x"));
        umgebung(bs("cd /d %USERPROFILE%¦Desktop"), bs("%USERPROFILE%¦Desktop"));
        umgebung(bs("\"%ProgramFiles%¦Mein Tool¦tool.exe\""), bs("%ProgramFiles%¦Mein Tool¦tool.exe"));
        umgebung("x=$HOME/a", "$HOME/a");
        umgebung("-I$HOME/include", "$HOME/include");
        umgebung(bs("Pfad:%APPDATA%¦x"), bs("%APPDATA%¦x"));
        umgebung("--prefix=$HOME/erika/lib", "$HOME/erika/lib");
        umgebung("LD_LIBRARY_PATH=$HOME/lib:$HOME/erika/lib", "$HOME/lib:$HOME/erika/lib");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc$HOME/x", "a¦n$HOME/x", "(%APPDATA%¦x)", "<%APPDATA%¦x>", "[$HOME/x]", ",%APPDATA%¦x",
            ";$HOME/x", "|$HOME/x", "=%APPDATA%¦x", "\u00DF$HOME/x", "7$HOME/x", "-$HOME/x", ".$HOME/x", ":$HOME/x",
            "%%a%\u00a6x", "e\u0301$HOME/x", "\uD83D\uDE00$HOME/x", "\u200B$HOME/x", "\u00A0$HOME/x"})
    void entscheidungKeineStartgrenzeDavorStehenDarfAllesUndGehoertNichtZumFund(String roh) {
        String text = bs(roh);
        List<Finding> f = funde(text);
        assertEquals(1, f.size(), text);
        assertEquals("pfad-umgebung", f.get(0).source());
        assertEquals(0.80, f.get(0).confidence());
        // der Fund beginnt an der Variable (bei %%a% an der zweiten, die erste hat keinen Namen); Text davor bleibt
        // stehen
        int erwartet = text.startsWith("%%") ? 1 : ersteVariable(text);
        assertEquals(erwartet, f.get(0).start(), "Start, Text: " + text);
    }

    @Test
    void entscheidungKeineStartgrenzeUnixPfadMitVariableDahinterBleibtUnix() {
        // der Schraegstrich davor hat eine Startgrenze: UNIX deckt alles ab (Segmente $HOME und x, 0.80); der
        // enthaltene Fund der Form UMGEBUNG aendert die Konfidenz nicht (Plan 7a (iii))
        fund("/$HOME/x", "/$HOME/x", "pfad-unix", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
    }

    @Test
    void entscheidungKeineStartgrenzeFormvertrag() {
        assertTrue(PathForm.UMGEBUNG.isStartBoundary("abc$HOME/x", 3));
        assertTrue(PathForm.UMGEBUNG.isStartBoundary("a%X%/x", 1));
        assertTrue(PathForm.UMGEBUNG.isStartBoundary("$HOME/x", 0));
        assertTrue(PathForm.UMGEBUNG.isStartBoundary("x ", 1));
    }

    // ================================================================== (6) Kuerzung und Klammern

    @Test
    void satzzeichenAmEndeGehoerenNichtZumFund() {
        for (String ende : new String[] {".", ",", ";", ":", "!", "?", "'", "...", ".,;"}) {
            umgebung("Siehe $HOME/akte.pdf" + ende, "$HOME/akte.pdf");
            umgebung(bs("Siehe %APPDATA%¦x¦konfig.ini") + ende, bs("%APPDATA%¦x¦konfig.ini"));
        }
    }

    @Test
    void schliessendeKlammernNurBeiUeberzahlGekuerzt() {
        umgebung("($HOME/a)", "$HOME/a");
        umgebung("(siehe $HOME/a)", "$HOME/a");
        umgebung("$HOME/Beispiel(x86)", "$HOME/Beispiel(x86)");
        umgebung(bs("%ProgramFiles(x86)%¦a)"), bs("%ProgramFiles(x86)%¦a"));
        umgebung("$HOME/a]", "$HOME/a");
        umgebung("[$HOME/a/b]", "$HOME/a/b");
        umgebung("{$HOME/a}", "$HOME/a");
        umgebung("${HOME}/a}", "${HOME}/a");
    }

    @Test
    void klammerSegmentHinterDemKopfZaehltNachDerKuerzung() {
        // Segment aus ausgeglichenen Klammern direkt hinter dem Trenner: nur nach der Kuerzung als Segment erkennbar
        umgebung("$HOME/(a)", "$HOME/(a)");
        umgebung("$HOME/()", "$HOME/()");
        umgebung("($HOME/(a))", "$HOME/(a)");
    }

    @Test
    void punktSegmenteHinterDemKopfBleibenStehen() {
        umgebung("$HOME/.config/app", "$HOME/.config/app");
        umgebung("$HOME/a.", "$HOME/a");
        umgebung("$HOME/.a", "$HOME/.a");
    }

    @Test
    void zeilenumbruchUndTabBeendenDenFund() {
        assertEquals(List.of("$HOME/a", "$HOME/b"), werte("$HOME/a\n$HOME/b"));
        assertEquals(List.of("$HOME/a", "$HOME/b"), werte("$HOME/a\r\n$HOME/b\r\n"));
        assertEquals(List.of("$HOME/a", "$HOME/b"), werte("$HOME/a\t$HOME/b"));
        assertEquals(List.of("$HOME/a", "$HOME/b"), werte("$HOME/a $HOME/b"));
        assertEquals(List.of("$HOME/a", "$HOME/b"), werte("$HOME/a\u00A0$HOME/b"));
    }

    @Test
    void mehrereVariablenPfadeInEinemLaufSindEinFund() {
        // ein Lauf liefert hoechstens einen Fund (bekannt, wie bei C:\a;C:\b)
        umgebung(bs("%A%¦x;%B%¦y"), bs("%A%¦x;%B%¦y"));
        umgebung("$HOME/a:$HOME/b", "$HOME/a:$HOME/b");
    }

    // ================================================================== (7) Anfuehrungszeichen, 13 Arten

    @Test
    void anfuehrungszeichenJederArtMitVariablenKoepfenGanzerInhaltMitLeerzeichen() {
        String[] inhalte = {bs("%ProgramFiles%¦Mein Tool¦tool.exe"), "$HOME/Mein Ordner/a.txt",
                "${HOME}/Mein Ordner/a.txt", bs("$env:APPDATA¦Mein Ordner¦a"), bs("%~dp0..¦Mein Ordner¦a"),
                "$(pwd)/Mein Ordner/a", bs("%HOMEDRIVE%%HOMEPATH%¦Mein Ordner¦a"), bs("${env:APPDATA}¦Mein Ordner¦a"),
                bs("!USERPROFILE!¦Mein Ordner¦a")};
        for (String q : ANF) {
            for (String inhalt : inhalte) {
                String text = "Starte " + q + inhalt + q + " jetzt";
                umgebung(text, inhalt);
                umgebung(q + inhalt + q, inhalt);
            }
        }
    }

    @Test
    void anfuehrungszeichenGemischteTypografischePaare() {
        String inhalt = "$HOME/Mein Ordner/a.txt";
        umgebung("\u201E" + inhalt + "\u201C", inhalt);
        umgebung("\u00BB" + inhalt + "\u00AB", inhalt);
        umgebung("\u201A" + inhalt + "\u2018", inhalt);
        umgebung("\u2039" + inhalt + "\u203A", inhalt);
    }

    @Test
    void anfuehrungszeichenKopfMussNichtAmAnfangDesInhaltsStehen() {
        umgebung(bs("\"Siehe %APPDATA%¦Mein Ordner¦a.txt\""), bs("%APPDATA%¦Mein Ordner¦a.txt"));
        umgebung("'cd $HOME/Mein Ordner/x'", "$HOME/Mein Ordner/x");
    }

    @Test
    void anfuehrungszeichenNieGeschlossenFaelltAufDieRegelOhneZurueck() {
        umgebung(bs("\"%ProgramFiles%¦Mein Tool¦x"), bs("%ProgramFiles%¦Mein"));
        umgebung("\"$HOME/Mein Ordner/x", "$HOME/Mein");
    }

    @Test
    void anfuehrungszeichenBatchParameterAlleinImZitatIstKeinFund() {
        keinFund("\"%~dp0tool.exe\"");
        keinFund("\"%~dp0\"");
        umgebung(bs("\"%~dp0..¦erika¦a\""), bs("%~dp0..¦erika¦a"));
    }

    @Test
    void anfuehrungszeichenVariableAlleinImZitatIstKeinFund() {
        for (String q : ANF) {
            keinFund(q + "%APPDATA%" + q);
            keinFund(q + "$HOME" + q);
            keinFund(q + "${HOME}" + q);
            keinFund(q + "!APPDATA!" + q);
        }
    }

    // ================================================================== (8) Verhaeltnis zu bestehenden Formen

    @Test
    void variableInDerMitteEinesLaufwerksPfadsDerFundBleibtLaufwerkMitNullFuenfundneunzig() {
        fund(bs("C:¦Users¦%USERNAME%¦Desktop"), bs("C:¦Users¦%USERNAME%¦Desktop"), "pfad-laufwerk", 0.95,
                ConfidenceLevel.SICHER);
        fund(bs("Starte C:¦Users¦%USERNAME%¦Desktop¦a.exe jetzt"), bs("C:¦Users¦%USERNAME%¦Desktop¦a.exe"),
                "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void variableInDerMitteEinesUncPfadsDerFundBleibtUncMitNullFuenfundneunzig() {
        fund(bs("¦¦srv¦%USERNAME%¦x"), bs("¦¦srv¦%USERNAME%¦x"), "pfad-unc", 0.95, ConfidenceLevel.SICHER);
        fund(bs("¦¦%USERDOMAIN%¦freigabe¦x"), bs("¦¦%USERDOMAIN%¦freigabe¦x"), "pfad-unc", 0.95,
                ConfidenceLevel.SICHER);
    }

    @Test
    void variableInDerMitteEinesUnixPfadsDerFundBleibtUnixMitNullNeunzig() {
        fund("/home/$USER/x", "/home/$USER/x", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
        fund("/opt/${APP}/bin/tool", "/opt/${APP}/bin/tool", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
        fund("~/$X/a", "~/$X/a", "pfad-home", 0.90, ConfidenceLevel.SICHER);
    }

    @Test
    void variableInDerMitteEinesWurzelPfadsDerFundBleibtWurzelMitNullSechzig() {
        fund(bs("Users¦%USERNAME%¦x"), bs("Users¦%USERNAME%¦x"), "pfad-relativ", 0.60, ConfidenceLevel.FRAGLICH);
    }

    @Test
    void windowsPfadMitVariablenKopfHatKeinenKandidatenDerFormenEinzelBackslashUndUnix() {
        // %USERPROFILE%\Desktop\a enthaelt \Desktop\a: ohne Startgrenze davor (das Prozentzeichen) kein Kandidat
        String text = bs("%USERPROFILE%¦Desktop¦a");
        assertEquals(List.of(), R.detect(text, new PathForm[] {PathForm.EINZEL_BACKSLASH}));
        assertEquals(List.of(), R.detect(text, new PathForm[] {PathForm.UNIX}));
        assertEquals(List.of(), R.detect(text, new PathForm[] {PathForm.BENUTZER_WURZEL}));
        assertEquals(List.of(), R.detect(text, new PathForm[] {PathForm.RELATIV}));
        umgebung(text, text);
        // $HOME/akte.pdf enthaelt /akte.pdf: UNIX braucht Startgrenze und zwei Segmente
        assertEquals(List.of(), R.detect("$HOME/akte.pdf", new PathForm[] {PathForm.UNIX}));
        assertEquals(List.of(), R.detect("$HOME/a/b", new PathForm[] {PathForm.UNIX}));
        umgebung("$HOME/akte.pdf", "$HOME/akte.pdf");
    }

    @Test
    void regel7aKeineAndereFormHatAmSelbenStartEinenKopf() {
        // Voraussetzung von Plan 7a (i): am Start einer Variable (%, $ oder !) beginnt nie ein Kopf einer anderen Form
        String[] koepfe = {"%A%¦x", "$A/x", "${A}/x", "$(A)/x", "$env:A¦x", "${env:A}¦x", "%~dp0¦x", "%%a%¦x", "$$a/x",
                "%a%%b%¦x", "!A!¦x", "!A!/x", "!!a!¦x"};
        for (String roh : koepfe) {
            String text = bs(roh);
            for (PathForm form : PathForm.values()) {
                if (form != PathForm.UMGEBUNG) {
                    assertEquals(-1, form.headEnd(text, 0), form + " bei " + text);
                }
            }
        }
    }

    @Test
    void variablenFundVorLaufwerkFundImSelbenLaufTraegtDieNiedrigereKonfidenz() {
        // Plan 7a (ii): Der Fund der frueher beginnenden Form (UMGEBUNG) umfasst den Laufwerksfund; es gilt die
        // niedrigste Konfidenz (0.80). Ohne UMGEBUNG waere der Fund C:\a\b allein 0.95 (groessere Grenzen, niedrigere
        // Konfidenz).
        String text = bs("%A%¦x;C:¦a¦b");
        umgebung(text, text);
        fund(bs("C:¦a¦b"), bs("C:¦a¦b"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
        assertEquals(List.of(bs("C:¦a¦b")),
                R.detect(text, new PathForm[] {PathForm.LAUFWERK}).stream().map(Finding::originalValue)
                        .collect(Collectors.toList()));
    }

    @Test
    void scannerLiefertVariablenPfadeAlsPfadMitQuelleUndKonfidenz() {
        List<Finding> s = SCANNER.scan(bs("Nutze %APPDATA%¦a¦b und $HOME/x/y sowie ${HOME}/z"));
        assertEquals(3, s.size());
        for (Finding f : s) {
            assertEquals(FindingType.PFAD, f.type());
            assertEquals("pfad-umgebung", f.source());
            assertEquals(0.80, f.confidence());
        }
    }

    @Test
    void mehrereVariablenPfadeDurchLeerzeichenGetrenntSindEinzelneFunde() {
        assertEquals(List.of("$HOME/a", "$HOME/b", bs("%APPDATA%¦c")), werte(bs("$HOME/a $HOME/b %APPDATA%¦c")));
        assertEquals(List.of("$HOME/a/b", "$HOME/c"), werte("Kopiere $HOME/a/b nach $HOME/c."));
    }

    @Test
    void variablenPfadMitEmailAnteilBleibtEinPfadFundImScanner() {
        List<Finding> s = SCANNER.scan("$HOME/erika.mustermann@example.invalid/ablage/x");
        assertEquals(1, s.size());
        assertEquals(FindingType.PFAD, s.get(0).type());
    }

    // ================================================================== (9) bekannte Folgen und Grenzen

    @Test
    void bekannteFolgeVorlagenUndFormelnMitNameTrennerSegmentSindFundMitNullAchtzig() {
        umgebung("${x}/${y}", "${x}/${y}");
        umgebung("$a/$b", "$a/$b");
        umgebung("Es gilt $x/y$ hier", "$x/y$");
        umgebung("Kosten $USD/MWh", "$USD/MWh");
        umgebung("Wert a%b%/c", "%b%/c");
    }

    @Test
    void bekannteFolgeEscapeFolgeHinterVariableIstEinFund() {
        umgebung(bs("echo $x¦n"), bs("$x¦n"));
        umgebung(bs("$HOME¦n"), bs("$HOME¦n"));
    }

    @Test
    void bekannteGrenzeErsatzsyntaxUndParameterOhneTilde() {
        // Seit dem Nachtrag T-15 Punkt 6 ist !VAR!\x ein Fund (Test entscheidungVerzoegerteExpansion...); die uebrigen
        // Grenzen bleiben: der ganze Ausdruck samt Pfad bleibt im Klartext
        keinFund(bs("%PATH:~0,5%¦x"));
        keinFund("${HOME:-/tmp}/x");
        keinFund(bs("%1¦x"));
        keinFund(bs("%~dp0tool.exe"));
        keinFund(bs("%USERPROFILE%Desktop"));
        keinFund("$(dirname $0)/x");
        // verschachtelte Ersatzsyntax: Teilfund ab dem inneren Ausdruck bis zum Ende des Laufs
        umgebung("${XDG_CONFIG_HOME:-$HOME/.config}/app", "$HOME/.config}/app");
        umgebung("${X:-$HOME/.config}/app", "$HOME/.config}/app");
    }

    @Test
    void bekannteGrenzeVariableInEigenenAnfuehrungszeichenMitEinemSegmentDahinter() {
        keinFund("\"$HOME\"/erika");
        keinFund("\"${HOME}\"/erika");
        keinFund(bs("\"%USERPROFILE%\"¦erika"));
        keinFund(bs("\"!USERPROFILE!\"¦erika"));
    }

    @Test
    void bekannteGrenzeVariableInEigenenAnfuehrungszeichenAbZweiSegmentenFasstNurDieAndereFormOhneVariable() {
        // UNIX bzw. EINZEL_BACKSLASH finden den Pfad hinter dem Zitat, die Variable bleibt sichtbar
        fund("\"$HOME\"/erika/akte.pdf", "/erika/akte.pdf", "pfad-unix", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
        fund(bs("\"%USERPROFILE%\"¦Desktop¦erika.pdf"), bs("¦Desktop¦erika.pdf"), "pfad-relativ", 0.75,
                ConfidenceLevel.WAHRSCHEINLICH);
    }

    @Test
    void bekannteGrenzeBefehlsersetzungMitArgumentUndMitBacktickGibtKeinenFund() {
        keinFund("$(dirname $0)/erika");
        keinFund("$(dirname $0)/x");
        keinFund("`dirname $0`/x");
        keinFund("`dirname $0`/erika");
        // Befehlsersetzung mit Argument: auch mit mehreren Segmenten kein Fund (vor dem Schraegstrich steht keine
        // Startgrenze fuer UNIX)
        keinFund("$(dirname $0)/erika/akte.pdf");
        // Backticks sind ein Anfuehrungszeichen: ab zwei Segmenten fasst UNIX den Pfad ohne die Befehlsersetzung
        fund("`dirname $0`/erika/akte.pdf", "/erika/akte.pdf", "pfad-unix", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
    }

    @Test
    void bekannteGrenzeDateinameDirektHinterDerVariableOhneTrennerGibtKeinenFund() {
        keinFund("%TEMP%erika.txt");
        keinFund(bs("%USERPROFILE%Desktop"));
        keinFund("$HOMEerika.txt");
        keinFund("!TEMP!erika.txt");
    }

    @Test
    void bekannteGrenzeMehrAlsVierProzentGruppenDieErstenBleibenSichtbar() {
        umgebung(bs("%a%%b%%c%%d%%e%%f%¦x"), bs("%c%%d%%e%%f%¦x"));
        umgebung(bs("%a%%b%%c%%d%%e%¦x"), bs("%b%%c%%d%%e%¦x"));
    }

    @Test
    void bekannteFolgeSerienbriefPlatzhalterMitSchraegstrichSindEinFundMitNullAchtzig() {
        // ersetzt wird nur der Platzhalter; kein Leck
        umgebung("%DATUM%/%ORT%", "%DATUM%/%ORT%");
        umgebung("%VORNAME%/%NACHNAME%", "%VORNAME%/%NACHNAME%");
        umgebung("%VORNAME%/%NACHNAME%/%GEBDAT%", "%VORNAME%/%NACHNAME%/%GEBDAT%");
        umgebung("Sehr geehrte %VORNAME%/%NACHNAME%, anbei", "%VORNAME%/%NACHNAME%");
    }

    @Test
    void gegenprobeSerienbriefPlatzhalterOhneTrennerSindKeinFund() {
        keinFundAuchImScanner("%ANREDE% %NACHNAME%");
        keinFundAuchImScanner("%NAME%");
        keinFundAuchImScanner("Sehr geehrte %ANREDE% %NACHNAME%, anbei %DATUM%");
    }

    @Test
    void bekannteGrenzeVariablenNameMitPunktOderMinus() {
        keinFund(bs("%a.b%¦x"));
        keinFund("$a-b/x");
        keinFund("$a.b/x");
    }

    @Test
    void bekannteGrenzeVariableAlleinDerPfadDahinterFehlt() {
        keinFund("cd %USERPROFILE%");
        keinFund("cd $HOME");
    }

    // ================================================================== (10) Unicode

    @Test
    void umlauteSzKombinierendeZeichenUndEmojiImPfadDahinterBleibenImFund() {
        String pfad = "$HOME/Gr\u00F6\u00DFe/\u00DCbersicht/e\u0301cole/\uD83D\uDE00/akte.pdf";
        umgebung(pfad, pfad);
        umgebung("Siehe " + pfad + " ende", pfad);
        String pfad2 = bs("%APPDATA%¦Gr\u00F6\u00DFe¦e\u0301.txt");
        umgebung(pfad2, pfad2);
    }

    @Test
    void fundBeginntNieImSurrogatpaarOderInKombinationsfolge() {
        String text = "\uD83D\uDE00$HOME/a e\u0301$HOME/b \uD83D\uDE00%A%/c";
        List<Finding> f = funde(text);
        assertEquals(3, f.size());
        assertEquals("$HOME/a", f.get(0).originalValue());
        assertEquals("$HOME/b", f.get(1).originalValue());
        assertEquals("%A%/c", f.get(2).originalValue());
    }

    @Test
    void nichtAsciiBuchstabenImVariablenNamenSindKeinKopf() {
        keinFund("$H\u00D6ME/x");
        keinFund(bs("%\u00C4PPDATA%¦x"));
        keinFund("$\u0430bc/x");
        keinFund("${\u0430}/x");
        keinFund("$env\uFF1AA/x");
    }

    @Test
    void vollbreiteZeichenImKopfSindKeinKopfBekannteGrenze() {
        keinFund("\uFF04HOME/x");
        keinFund("$HOME\uFF0Fx");
        keinFund("\uFF05APPDATA\uFF05\\x");
    }

    // ================================================================== (11) Formvertrag

    @Test
    void formUmgebungVertrag() {
        PathForm f = PathForm.UMGEBUNG;
        assertEquals(1, f.minSegments());
        assertEquals("pfad-umgebung", f.source());
        // %NAME%
        assertEquals(4, f.headEnd(bs("%A%¦x"), 0));
        assertEquals(4, f.headEnd("%A%/x", 0));
        assertEquals(9, f.headEnd("%ab%%cd%/x", 0));
        assertEquals(-1, f.headEnd("%A%", 0));
        assertEquals(-1, f.headEnd("%A%x", 0));
        assertEquals(-1, f.headEnd("%", 0));
        assertEquals(-1, f.headEnd("%%", 0));
        assertEquals(-1, f.headEnd("%A", 0));
        assertEquals(-1, f.headEnd("", 0));
        assertEquals(-1, f.headEnd("%A%/x", 5));
        assertEquals(-1, f.headEnd("%A%/x", 99));
        assertEquals(5, f.headEnd("a%A%/x%B%/y", 1));
        assertEquals(10, f.headEnd("a%A%/x%B%/y", 6));
        // $NAME, ${NAME}, $(NAME)
        assertEquals(6, f.headEnd("$HOME/x", 0));
        assertEquals(8, f.headEnd("${HOME}/x", 0));
        assertEquals(8, f.headEnd("$(HOME)/x", 0));
        assertEquals(-1, f.headEnd("$HOME", 0));
        assertEquals(-1, f.headEnd("$", 0));
        assertEquals(-1, f.headEnd("${", 0));
        assertEquals(-1, f.headEnd("${HOME", 0));
        assertEquals(-1, f.headEnd("${HOME}", 0));
        assertEquals(-1, f.headEnd("$1/x", 0));
        // PowerShell
        assertEquals(10, f.headEnd("$env:HOME/x", 0));
        assertEquals(12, f.headEnd("${env:HOME}/x", 0));
        assertEquals(-1, f.headEnd("$env:", 0));
        assertEquals(-1, f.headEnd("$env:/x", 0));
        // Batch
        assertEquals(6, f.headEnd("%~dp0/x", 0));
        assertEquals(8, f.headEnd("%~dp0../x", 0));
        assertEquals(-1, f.headEnd("%~dp0", 0));
        assertEquals(-1, f.headEnd("%~dp0x", 0));
        assertEquals(-1, f.headEnd("%~", 0));
        assertEquals(-1, f.headEnd("%~dp0 x/y", 0));
        // verzoegerte Expansion
        assertEquals(4, f.headEnd("!A!/x", 0));
        assertEquals(4, f.headEnd(bs("!A!¦x"), 0));
        assertEquals(5, f.headEnd("!ab!/x!cd!/y", 0));
        assertEquals(11, f.headEnd("!ab!/x!cd!/y", 6));
        assertEquals(7, f.headEnd("!a!!b!/x", 3));
        assertEquals(-1, f.headEnd("!a!!b!/x", 0));
        assertEquals(-1, f.headEnd("!A!", 0));
        assertEquals(-1, f.headEnd("!A!x", 0));
        assertEquals(-1, f.headEnd("!A", 0));
        assertEquals(-1, f.headEnd("!", 0));
        assertEquals(-1, f.headEnd("!!/x", 0));
        assertEquals(-1, f.headEnd("!1!/x", 0));
        assertEquals(-1, f.headEnd("!A!/x", 5));
        assertEquals(-1, f.headEnd("a!A!/x", 0));
        // andere Zeichen
        assertEquals(-1, f.headEnd("a%A%/x", 0));
        assertEquals(-1, f.headEnd("~/x", 0));
        // keine Startgrenze: auch direkt hinter einem Buchstaben
        assertTrue(f.isStartBoundary("a$HOME/b", 1));
    }

    @Test
    void formUmgebungKonfidenzKonstantMonotonNichtFallend() {
        PathForm f = PathForm.UMGEBUNG;
        for (int kopf : new int[] {1, 2, 3, 7, 66, 270}) {
            double vorige = f.confidence(kopf, 0);
            for (int segmente = 1; segmente <= 6; segmente++) {
                double jetzt = f.confidence(kopf, segmente);
                assertTrue(jetzt >= vorige, "faellt bei " + segmente + " Segmenten, Kopf " + kopf);
                assertEquals(0.80, jetzt);
                vorige = jetzt;
            }
        }
    }

    @Test
    void alleFormenHabenQuelleMitPfadPraefix() {
        for (PathForm form : PathForm.values()) {
            assertTrue(form.source().matches("pfad-[a-z]+"), form.name());
        }
    }

    // ================================================================== (12) Grenzlast (AK-49), je < 2 s

    /** Erkenner und Standard-Scanner; die Fundzahl wird exakt geprueft, beide liefern dieselbe Zahl (keine URL). */
    private static void laufzeit(String text, int erwartet) {
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefeStruktur(text, r);
        assertEquals(erwartet, r.size(), "Fundzahl Erkenner");
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        assertEquals(erwartet, s.size(), "Fundzahl Scanner");
    }

    @Test
    void laufzeitProzentHunderttausendFachWieImPlan() {
        laufzeit("%".repeat(100_000), 0);
    }

    @Test
    void laufzeitProzentAHunderttausendFachWieImPlan() {
        laufzeit("%a".repeat(100_000), 0);
    }

    @Test
    void laufzeitDollarHunderttausendFachWieImPlan() {
        laufzeit("$".repeat(100_000), 0);
    }

    @Test
    void laufzeitProzentEineMillion() {
        laufzeit("%".repeat(N), 0);
    }

    @Test
    void laufzeitProzentAEineMillion() {
        laufzeit("%a".repeat(N / 2), 0);
        laufzeit("%a%".repeat(N / 3), 0);
        laufzeit("%a%%b%".repeat(N / 6), 0);
    }

    @Test
    void laufzeitProzentNameAnProzentOhneTrennerKetteLang() {
        laufzeit("%a%%b%%c%%d%%e%".repeat(N / 15), 0);
    }

    @Test
    void laufzeitDollarEineMillion() {
        laufzeit("$".repeat(N), 0);
        laufzeit("$a".repeat(N / 2), 0);
        laufzeit("${".repeat(N / 2), 0);
        laufzeit("$(".repeat(N / 2), 0);
        laufzeit("${a".repeat(N / 3), 0);
        laufzeit("$env:".repeat(N / 5), 0);
        laufzeit("${env:".repeat(N / 6), 0);
    }

    @Test
    void laufzeitAusrufezeichenEineMillion() {
        laufzeit("!".repeat(N), 0);
        laufzeit("!".repeat(100_000), 0);
        laufzeit("!x".repeat(N / 2), 0);
        laufzeit("!x".repeat(100_000), 0);
        laufzeit("!a!".repeat(N / 3), 0);
        laufzeit("!a!".repeat(100_000), 0);
        laufzeit("!a!!b!".repeat(N / 6), 0);
        laufzeit("!!".repeat(N / 2), 0);
    }

    @Test
    void laufzeitAusrufezeichenNamenMitVierundsechzigUndFuenfundsechzigZeichen() {
        laufzeit(("!" + name(64) + "!\\x ").repeat(N / 70), N / 70);
        laufzeit(("!" + name(65) + "!\\x ").repeat(N / 71), 0);
        laufzeit("!" + name(N), 0);
        // Namen mit 64 Zeichen ohne Trenner dicht hintereinander: jede Position liest hoechstens 67 Zeichen
        laufzeit(("!" + name(64)).repeat(N / 65), 0);
        laufzeit(("!" + name(64) + "!").repeat(N / 66), 0);
    }

    @Test
    void laufzeitAusrufezeichenMitTrennerUndSegment() {
        laufzeit("!a!/".repeat(N / 4), 1);
        laufzeit("!a!/b ".repeat(N / 6), N / 6);
        laufzeit("!a!/ ".repeat(N / 5), 0);
        laufzeit("!a!!b!!c!/x ".repeat(N / 12), N / 12);
    }

    @Test
    void laufzeitAusrufezeichenImBatchRestUndGemischteKoepfe() {
        laufzeit(bs("%~dp0!a!".repeat(N / 8) + "¦)"), 0);
        laufzeit(bs("%~dp0!a!".repeat(N / 8) + "¦x"), 1);
        laufzeit("%a!b$c".repeat(N / 6), 0);
        laufzeit("!a%b$".repeat(N / 5), 0);
    }

    @Test
    void laufzeitNamenMitVierundsechzigUndFuenfundsechzigZeichen() {
        laufzeit(("%" + name(64) + "%\\x ").repeat(N / 70), N / 70);
        laufzeit(("%" + name(65) + "%\\x ").repeat(N / 71), 0);
        laufzeit(("$" + name(64) + "/x ").repeat(N / 68), N / 68);
        laufzeit(("$" + name(65) + "/x ").repeat(N / 69), 0);
        laufzeit("%" + name(N), 0);
        laufzeit("$" + name(N), 0);
        laufzeit("${" + name(N), 0);
    }

    @Test
    void laufzeitBatchParameterDichtOhneTrenner() {
        laufzeit("%~dp0".repeat(N / 5), 0);
        laufzeit("%~".repeat(N / 2), 0);
    }

    @Test
    void laufzeitBatchParameterDichtMitNachzaehlungAmEnde() {
        // viele Koepfe, deren Segmentzahl erst nach der Kuerzung entschieden wird: hoechstens 13 Koepfe haben einen
        // Trenner innerhalb von 64 Zeichen, die Nachzaehlung bleibt konstant oft
        laufzeit(bs("%~dp0".repeat(N / 5) + "¦)"), 0);
        laufzeit(bs("%~dp0".repeat(N / 5) + "¦a)"), 1);
        laufzeit(bs("%a%¦".repeat(N / 4) + "("), 1);
    }

    @Test
    void laufzeitVariableMitTrennerUndSegment() {
        laufzeit("$a/".repeat(N / 3), 1);
        laufzeit(bs("%a%¦").repeat(N / 4), 1);
        laufzeit("$HOME/ ".repeat(N / 7), 0);
        laufzeit("$HOME/a ".repeat(N / 8), N / 8);
        laufzeit(bs("%APPDATA%¦a ").repeat(N / 12), N / 12);
        laufzeit(bs("%~dp0..¦a ").repeat(N / 10), N / 10);
        laufzeit("${HOME}/a ".repeat(N / 10), N / 10);
        laufzeit("$(pwd)/a ".repeat(N / 9), N / 9);
        laufzeit(bs("$env:A¦a ").repeat(N / 8), N / 8);
    }

    @Test
    void laufzeitAlltagsMusterProzentUndWaehrung() {
        laufzeit("%d/%m/%Y ".repeat(N / 9), 0);
        laufzeit("50%/Jahr ".repeat(N / 9), 0);
        laufzeit("3%/4% ".repeat(N / 6), 0);
        laufzeit("5$/St\u00FCck ".repeat(N / 9), 0);
        laufzeit("100 %/Monat ".repeat(N / 12), 0);
        laufzeit("$1/2 ".repeat(N / 5), 0);
        laufzeit("%20/%2F".repeat(N / 7), 0);
    }

    @Test
    void laufzeitPfadAnfangGefolgtVonHunderttausendWoertern() {
        laufzeit("$HOME/a" + " wort".repeat(100_000), 1);
        laufzeit(bs("%APPDATA%¦a") + " wort".repeat(100_000), 1);
    }

    @Test
    void laufzeitAnfuehrungszeichenAllerArtenMitVariablenKoepfen() {
        for (String q : ANF) {
            String einheit = q + "$a/b ";
            laufzeit(einheit.repeat(N / einheit.length()), N / einheit.length());
            String einheit2 = q + bs("%a%¦b ");
            laufzeit(einheit2.repeat(N / einheit2.length()), N / einheit2.length());
        }
    }

    @Test
    void laufzeitAnfuehrungszeichenNieGeschlossenMitVariablenKoepfen() {
        for (String q : ANF) {
            laufzeit(q + "$a/ ".repeat(N / 4), 0);
            laufzeit(q + bs("%~dp0 ").repeat(N / 6), 0);
            laufzeit(q + "!a!/ ".repeat(N / 5), 0);
        }
    }

    @Test
    void laufzeitAnfuehrungszeichenGefolgtVonLangerVariablenKette() {
        for (String q : new String[] {"\"", "'", "`", "\u201E"}) {
            laufzeit(q + "%a%".repeat(N / 3) + q, 0);
            laufzeit(q + "$".repeat(N) + q, 0);
            laufzeit(q + "%a".repeat(N / 2) + q, 0);
            laufzeit(q + "!a!".repeat(N / 3) + q, 0);
            laufzeit(q + "!".repeat(N) + q, 0);
        }
    }

    @Test
    void laufzeitScannerMitUrlsUndProzentKodierung() {
        String url = "https://example.invalid/a%20b/%2F/%E4%B8%AD/?x=%USERPROFILE%/y ";
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(url.repeat(N / url.length())));
        assertEquals(N / url.length(), s.size());
        for (Finding f : s) {
            assertEquals(FindingType.URL, f.type());
        }
    }
}
