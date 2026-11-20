package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Tests der Form LAUFWERK (T-10). Pfade in Testtexten werden mit {@code bs(...)} geschrieben (Plan Kap. 2.10); alle
 * Namen sind synthetisch. Nicht-ASCII-Zeichen und unsichtbare Zeichen stehen nur als Escapes im Quelltext.
 *
 * <p>Jeder Test prüft über {@link #werte} je Fund: Teilstring = Originalwert, Typ PFAD, Quelle pfad-laufwerk,
 * Konfidenz 0.95 (SICHER), sortiert, überlappungsfrei, Grenzen nicht in Surrogatpaar oder Kombinationsfolge.
 */
class PathRecognizerLaufwerkTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final Duration LIMIT = Duration.ofSeconds(2);

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> funde = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertEquals("pfad-laufwerk", f.source());
            assertEquals(0.95, f.confidence());
            assertEquals(ConfidenceLevel.SICHER, f.level());
            assertTrue(f.start() >= vorigesEnde, "sortiert und überlappungsfrei");
            assertGrenzenSauber(text, f);
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static void assertGrenzenSauber(String text, Finding f) {
        int s = f.start();
        int e = f.end();
        assertFalse(s > 0 && Character.isLowSurrogate(text.charAt(s)) && Character.isHighSurrogate(text.charAt(s - 1)),
                "Start im Surrogatpaar");
        assertFalse(e < text.length() && Character.isLowSurrogate(text.charAt(e))
                && Character.isHighSurrogate(text.charAt(e - 1)), "Ende im Surrogatpaar");
        assertFalse(isKombinierend(text.charAt(s)), "Start auf kombinierendem Zeichen");
        assertFalse(e < text.length() && isKombinierend(text.charAt(e)), "Ende vor kombinierendem Zeichen");
    }

    private static boolean isKombinierend(char c) {
        int t = Character.getType(c);
        return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK
                || t == Character.COMBINING_SPACING_MARK;
    }

    private static void assertEinFund(String text, String erwartet) {
        assertEquals(List.of(erwartet), werte(text), "Text: " + text);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    private static int zaehle(String text) {
        return werte(text).size();
    }

    // ------------------------------------------------------------------ Positiv-Zeile LAUFWERK (Plan 2.4.2)

    @Test
    void positivAk9LaufwerkspfadImSatz() {
        String text = bs("Start C:¦Tools¦BeispielTool¦tool.exe jetzt");
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(bs("C:¦Tools¦BeispielTool¦tool.exe"), f.get(0).originalValue());
        assertEquals(6, f.get(0).start());
        assertEquals(6 + f.get(0).originalValue().length(), f.get(0).end());
        assertEquals(36, f.get(0).end());
    }

    @Test
    void positivSchraegstriche() {
        assertEinFund("Start C:/Tools/BeispielTool/tool.exe jetzt", "C:/Tools/BeispielTool/tool.exe");
    }

    @Test
    void positivGemischteTrenner() {
        assertEinFund(bs("Start C:¦Tools/BeispielTool¦tool.exe jetzt"), bs("C:¦Tools/BeispielTool¦tool.exe"));
        assertEinFund(bs("Start C:/Tools¦BeispielTool/tool.exe jetzt"), bs("C:/Tools¦BeispielTool/tool.exe"));
    }

    @Test
    void positivKleinbuchstabenLaufwerk() {
        assertEinFund(bs("c:¦tools¦beispieltool¦tool.exe"), bs("c:¦tools¦beispieltool¦tool.exe"));
        assertEinFund("Start c:/tools/x.exe jetzt", "c:/tools/x.exe");
    }

    @Test
    void positivAbschliessenderTrennerGehoertZumFund() {
        assertEinFund(bs("Ordner C:¦Tools¦BeispielTool¦ ok"), bs("C:¦Tools¦BeispielTool¦"));
        assertEinFund("Ordner C:/Tools/BeispielTool/ ok", "C:/Tools/BeispielTool/");
        assertEinFund(bs("C:¦Tools¦BeispielTool¦"), bs("C:¦Tools¦BeispielTool¦"));
    }

    @Test
    void positivAk22DoppelteBackslashesUnveraendert() {
        String pfad = bs("C:¦¦Tools¦¦BeispielTool¦¦tool.exe");
        assertEquals(6, pfad.chars().filter(c -> c == '\\').count());
        List<Finding> f = funde("Start " + pfad + " jetzt");
        assertEquals(1, f.size());
        assertEquals(pfad, f.get(0).originalValue());
        assertEquals(6, f.get(0).originalValue().chars().filter(c -> c == '\\').count());
    }

    @Test
    void positivDoppelteSchraegstricheUndGemischteTrennerFolgen() {
        assertEinFund("Start C://Tools//x.exe jetzt", "C://Tools//x.exe");
        assertEinFund(bs("Start C:¦/Tools/¦x.exe jetzt"), bs("C:¦/Tools/¦x.exe"));
    }

    @Test
    void positivKlammerImNamenBleibtGanz() {
        assertEinFund(bs("Pfad C:¦Tools¦Beispiel(x86) ok"), bs("C:¦Tools¦Beispiel(x86)"));
        assertEinFund(bs("C:¦Tools¦Beispiel(x86)"), bs("C:¦Tools¦Beispiel(x86)"));
        assertEinFund(bs("C:¦Tools¦Beispiel(x86)."), bs("C:¦Tools¦Beispiel(x86)"));
        assertEinFund(bs("C:¦Tools¦Beispiel[1]"), bs("C:¦Tools¦Beispiel[1]"));
        assertEinFund(bs("C:¦Tools¦Beispiel{1}"), bs("C:¦Tools¦Beispiel{1}"));
    }

    @Test
    void positivAk17aProgramFilesEndetAmErstenLeerzeichen() {
        String text = bs("Starte C:¦Program Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log.");
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(bs("C:¦Program"), f.get(0).originalValue());
        assertEquals(7, f.get(0).start());
        assertEquals(17, f.get(0).end());
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, f);
        assertEquals(bs("Starte [[PFAD_1]] Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log."), p.text());
        // Rest bleibt Klartext und liefert keinen weiteren Fund (auch nicht im pseudonymisierten Text)
        assertKeinFund(bs("Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log."));
        assertKeinFund(p.text());
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text());
        assertTrue(back.unresolved().isEmpty());
    }

    @Test
    void positivBenutzernameLiegtImFund() {
        String pfad = bs("C:¦Users¦erika.mustermann¦Desktop¦akte.pdf");
        assertEinFund("Datei " + pfad + " liegt dort", pfad);
        assertEinFund("Datei " + pfad + ".", pfad);
        assertTrue(werte("Datei " + pfad).get(0).contains("erika.mustermann"));
    }

    // ------------------------------------------------------------------ Negativ-Zeile LAUFWERK (Plan 2.4.2)

    @Test
    void negativLaufwerkAllein() {
        assertKeinFund("C:");
        assertKeinFund("Das Laufwerk C: ist voll");
        assertKeinFund("c:");
    }

    @Test
    void negativAk10TeilAUndUhrzeit() {
        assertKeinFund("Teil A: Beschreibung");
        assertKeinFund("Uhrzeit 10:30");
        assertKeinFund("Termin um 12:30 Uhr, Treffen 09:15.");
    }

    @Test
    void negativOf7WurzelOhnePfadteil() {
        assertKeinFund(bs("C:¦"));
        assertKeinFund("C:/");
        assertKeinFund(bs("Wurzel C:¦ ist leer"));
        assertKeinFund(bs("C:¦¦"));
        assertKeinFund(bs("C:¦/¦"));
    }

    @Test
    void negativNachKuerzungKeinSegment() {
        assertKeinFund(bs("C:¦."));
        assertKeinFund(bs("Ordner C:¦. ende"));
        assertKeinFund(bs("C:¦.."));
        assertKeinFund(bs("C:¦;"));
        assertKeinFund(bs("C:¦:"));
        assertKeinFund(bs("C:¦)"));
        assertKeinFund(bs("C:¦.,;:!?'"));
    }

    @Test
    void xCDoppelpunktBackslashHatKeineStartgrenzeNurLaufwerksbuchstabeZaehlt() {
        // Plan nennt xC:\a als Negativbeispiel (keine Startgrenze). EIGENE ENTSCHEIDUNG auf Weisung des
        // Auftraggebers: Text direkt vor dem Laufwerk verhindert den Fund nicht; Fund ab dem Laufwerksbuchstaben.
        List<Finding> f = funde(bs("xC:¦a"));
        assertEquals(1, f.size());
        assertEquals(bs("C:¦a"), f.get(0).originalValue());
        assertEquals(1, f.get(0).start());
    }

    // ------------------------------------------------------------------ Text direkt vor dem Pfad

    @Test
    void textDirektVorDemPfadVerhindertDenFundNicht() {
        assertEinFund(bs("Pfad:C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("x=C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("(C:¦Tools¦a.exe)"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("siehe.....C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("abcC:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("1C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("-C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("_C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("Datei=C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("[C:¦Tools¦a.exe]"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("{C:¦Tools¦a.exe}"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("\u00DCbersichtC:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("\u200BC:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("\uFEFFC:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("e\u0301C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("\uD83D\uDE00C:¦Tools¦a.exe"), bs("C:¦Tools¦a.exe"));
    }

    @Test
    void fundDecktAllesVomLaufwerksbuchstabenAn() {
        String text = bs("Pfad:C:¦Users¦erika.mustermann¦Desktop¦akte.pdf");
        Finding f = funde(text).get(0);
        assertEquals(text.indexOf("C:"), f.start());
        assertEquals(text.length(), f.end());
    }

    @Test
    void laufwerksbuchstabeNachWortendeIstDerBuchstabeVorDemDoppelpunkt() {
        // Wort endet auf einen Buchstaben, dann :\ : dieser Buchstabe ist das Laufwerk, das Wort davor bleibt Text.
        List<Finding> f = funde(bs("Verzeichnis:¦Tools¦a.exe"));
        assertEquals(1, f.size());
        assertEquals(bs("s:¦Tools¦a.exe"), f.get(0).originalValue());
    }

    @Test
    void verwechslungenMitVerhaeltnisUhrzeitUndTextOhneTrenner() {
        assertKeinFund("Verh\u00E4ltnis A:B");
        assertKeinFund("12:30");
        assertKeinFund("Hinweis:\nDatei folgt");
        assertKeinFund("Hinweis:\r\nDatei folgt");
        assertKeinFund("Hinweis: \\ nichts");
        assertKeinFund("a:b:c");
        assertKeinFund("1:2:3");
        assertKeinFund("C :" + bs("¦a"));
        assertKeinFund("\u00E4:" + bs("¦x"));
        assertKeinFund("5:" + bs("¦x"));
    }

    @Test
    void literalesBackslashNNachDoppelpunktIstEinGroesserFundKeinLeck() {
        // Eingabe mit buchstaeblichem Backslash-n (z. B. aus einem Log): Wort-Endbuchstabe + :\ + n... wird als
        // Laufwerk gelesen. Bewusst in Kauf genommen (zu groß statt zu klein).
        assertEinFund(bs("Hinweis:¦nText"), bs("s:¦nText"));
    }

    // ------------------------------------------------------------------ Pfadende (AK-19)

    @ParameterizedTest
    @ValueSource(strings = {".", ",", ";", ":", "!", "?", "'", "..", "...", ".,", "?!", "!?", ".'", ";.", ",;:!"})
    void ak19SatzzeichenAmEndeGehoerenNichtZumFund(String satzzeichen) {
        String pfad = bs("C:¦Tools¦BeispielTool¦tool.exe");
        assertEinFund("Siehe " + pfad + satzzeichen, pfad);
        assertEinFund("Siehe " + pfad + satzzeichen + " Danach geht es weiter", pfad);
        assertEinFund("Siehe " + pfad + satzzeichen + "\nZeile", pfad);
    }

    @Test
    void ak19DateiendungGehoertZumFundSatzpunktNicht() {
        assertEinFund(bs("Siehe C:¦Tools¦BeispielTool¦tool.exe."), bs("C:¦Tools¦BeispielTool¦tool.exe"));
        assertEinFund(bs("Siehe C:¦Tools¦BeispielTool¦tool.exe"), bs("C:¦Tools¦BeispielTool¦tool.exe"));
        assertEinFund(bs("C:¦Tools¦BeispielTool¦tool.exe.bak."), bs("C:¦Tools¦BeispielTool¦tool.exe.bak"));
    }

    @Test
    void ak19PfadAmZeilenUndTextende() {
        String pfad = bs("C:¦Tools¦a.exe");
        assertEinFund("Start " + pfad, pfad);
        assertEinFund(pfad, pfad);
        assertEinFund(pfad + "\nweiter", pfad);
        assertEinFund("Start " + pfad + "\n", pfad);
    }

    @Test
    void ak19PfadEndetAnZeilenumbruchUndTab() {
        String pfad = bs("C:¦Tools¦a.exe");
        assertEquals(List.of(pfad, bs("D:¦x¦b.exe")), werte(pfad + "\n" + bs("D:¦x¦b.exe")));
        assertEquals(List.of(pfad, bs("D:¦x¦b.exe")), werte(pfad + "\r" + bs("D:¦x¦b.exe")));
        assertEquals(List.of(pfad, bs("D:¦x¦b.exe")), werte(pfad + "\r\n" + bs("D:¦x¦b.exe")));
        assertEquals(List.of(pfad, bs("D:¦x¦b.exe")), werte(pfad + "\t" + bs("D:¦x¦b.exe")));
        assertEinFund(pfad + "\tText danach", pfad);
        assertEinFund("Text\t" + pfad, pfad);
    }

    @Test
    void ak19TabAlsTrennerZwischenSpalten() {
        String text = "Name\t" + bs("C:¦Tools¦a.exe") + "\tGr\u00F6\u00DFe\t12";
        assertEinFund(text, bs("C:¦Tools¦a.exe"));
    }

    @Test
    void ak19SchliessendeKlammerNurWennGeoeffnet() {
        assertEinFund(bs("(siehe C:¦Tools¦a.exe)"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("(siehe C:¦Tools¦a.exe)."), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("(C:¦Tools¦a.exe)"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("((C:¦Tools¦a.exe))"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("[C:¦Tools¦a.exe]"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("{C:¦Tools¦a.exe}"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("C:¦Tools¦a(b))"), bs("C:¦Tools¦a(b)"));
        assertEinFund(bs("C:¦Tools¦a(b)),"), bs("C:¦Tools¦a(b)"));
        assertEinFund(bs("C:¦Tools¦a[b]]"), bs("C:¦Tools¦a[b]"));
        assertEinFund(bs("C:¦Tools¦a)"), bs("C:¦Tools¦a"));
        assertEinFund(bs("C:¦Tools¦a]"), bs("C:¦Tools¦a"));
        assertEinFund(bs("C:¦Tools¦a}"), bs("C:¦Tools¦a"));
        // Klammerarten werden getrennt gezählt: ( schützt kein ]
        assertEinFund(bs("C:¦Tools¦a(b]"), bs("C:¦Tools¦a(b"));
        // Zählung ohne Reihenfolge: zwei ) bei einer ( kürzen das letzte )
        assertEinFund(bs("C:¦Tools¦a)(b)"), bs("C:¦Tools¦a)(b"));
    }

    @Test
    void ak19SternUndAbschliessenderTrennerBleiben() {
        assertEinFund(bs("C:¦Tools¦*"), bs("C:¦Tools¦*"));
        assertEinFund(bs("C:¦Tools¦*.exe"), bs("C:¦Tools¦*.exe"));
        assertEinFund(bs("C:¦Tools¦*."), bs("C:¦Tools¦*"));
        assertEinFund(bs("C:¦Tools¦."), bs("C:¦Tools¦"));
        assertEinFund(bs("C:¦Tools¦, danach"), bs("C:¦Tools¦"));
    }

    @Test
    void ak20SpitzeKlammernUndAnfuehrungszeichenUmschliessen() {
        assertEinFund(bs("<C:¦Tools¦a.exe>"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("Start <C:¦Tools¦a.exe> jetzt"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("\"C:¦Tools¦a.exe\""), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("`C:¦Tools¦a.exe`"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("'C:¦Tools¦a.exe'"), bs("C:¦Tools¦a.exe"));
        assertEinFund(bs("(C:¦Tools¦a.exe)"), bs("C:¦Tools¦a.exe"));
    }

    @Test
    void ak20AnfuehrungszeichenBleibenImPseudonymisiertenText() {
        String text = bs("Start \"C:¦Tools¦BeispielTool¦tool.exe\" jetzt");
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, R.detect(text));
        assertEquals("Start \"[[PFAD_1]]\" jetzt", p.text());
    }

    @Test
    void umleitungsZeichenAlsStartgrenze() {
        assertEinFund(bs(">C:¦Logs¦a.log"), bs("C:¦Logs¦a.log"));
        assertEinFund(bs("tool.exe >C:¦Logs¦a.log"), bs("C:¦Logs¦a.log"));
        assertEinFund(bs("tool.exe > C:¦Logs¦a.log"), bs("C:¦Logs¦a.log"));
        assertEinFund(bs("tool.exe 2>C:¦Logs¦err.log"), bs("C:¦Logs¦err.log"));
        assertEinFund(bs("tool.exe |C:¦Logs¦a.log"), bs("C:¦Logs¦a.log"));
    }

    @Test
    void pipeUndSpitzeKlammerBeendenDenPfad() {
        assertEquals(List.of(bs("C:¦a¦b"), bs("C:¦x")), werte(bs("C:¦a¦b|C:¦x")));
        assertEquals(List.of(bs("C:¦a¦b"), bs("D:¦x")), werte(bs("C:¦a¦b<D:¦x")));
    }

    // ------------------------------------------------------------------ AK-21: Dateiname ohne Pfad

    @ParameterizedTest
    @ValueSource(strings = {"Starte tool.exe", "tool.exe", "BeispielTool.exe l\u00E4uft", "Datei konfig.yml",
            "Programm BeispielTool ist installiert", "ordner\\datei.txt", "ordner/datei.txt", "Files\\Mein",
            "Tool\\tool.exe", "%APPDATA%\\x", "$HOME/x"})
    void ak21DateinameOhnePfadUndAndereFormenSindKeinFund(String text) {
        assertKeinFund(text);
    }

    // ------------------------------------------------------------------ mehrere Pfade (AK-18, PATH-Listen)

    @Test
    void ak18ZweiPfadeImSatz() {
        assertEquals(List.of(bs("C:¦Tools¦a.exe"), bs("D:¦Ablage¦b.exe")),
                werte(bs("Kopiere C:¦Tools¦a.exe nach D:¦Ablage¦b.exe")));
    }

    @Test
    void ak18ZweiPfadeNurDurchLeerzeichenGetrennt() {
        assertEquals(List.of(bs("C:¦Tools¦a.exe"), bs("C:¦Tools¦b.exe")),
                werte(bs("C:¦Tools¦a.exe C:¦Tools¦b.exe")));
    }

    @Test
    void ak18ZweiPfadeMitKommaUndLeerzeichen() {
        assertEquals(List.of(bs("C:¦a¦x"), bs("C:¦b¦y")), werte(bs("C:¦a¦x, C:¦b¦y")));
    }

    @Test
    void pathListeMitSemikolonOhneLeerzeichenIstEinFund() {
        // bekannte Grenze (Plan 2.4.2): durch ; ohne Leerzeichen verbundene Pfade sind ein Fund
        assertEinFund(bs("C:¦a;C:¦b"), bs("C:¦a;C:¦b"));
        assertEinFund(bs("PATH=C:¦a;D:¦b;"), bs("C:¦a;D:¦b"));
        assertEinFund(bs("C:¦a,C:¦b"), bs("C:¦a,C:¦b"));
    }

    @Test
    void mehrereZeilenMitPfaden() {
        String text = bs("C:¦a¦x\nD:¦b¦y\r\nE:¦c¦z\n");
        assertEquals(List.of(bs("C:¦a¦x"), bs("D:¦b¦y"), bs("E:¦c¦z")), werte(text));
    }

    // ------------------------------------------------------------------ Schalter mit Pfadwert (seit T-13 UNIX)

    @Test
    void logSchalterErgibtSeitT13DenGanzenAusdruckAlsUnixFund() {
        // Seit T-13 (Plan 2.4.2, bewusst umgestellt, vorher nur der Laufwerksfund ab "C:"): Der Schalter mit
        // Pfadwert ist EIN Fund ueber den ganzen Ausdruck, weil der Backslash auch Trenner ist (drei Segmente:
        // "log=C:", "Logs", "a.log"). Der Pfad ist vollstaendig geschuetzt, der Schaltername ist mitersetzt.
        String text = bs("tool.exe /log=C:¦Logs¦a.log");
        List<Finding> f = R.detect(text);
        assertEquals(1, f.size());
        assertEquals(bs("/log=C:¦Logs¦a.log"), f.get(0).originalValue());
        assertEquals(9, f.get(0).start());
        assertEquals("pfad-unix", f.get(0).source());
        assertEquals(0.90, f.get(0).confidence());
        assertEquals(FindingType.PFAD, f.get(0).type());
    }

    // ------------------------------------------------------------------ Unicode (AK-23)

    @Test
    void ak23UmlauteUndEszettImPfad() {
        String pfad = bs("C:¦Users¦mustermann¦\u00DCbersicht¦Gr\u00F6\u00DFe.txt");
        assertEinFund("Datei " + pfad + " ok", pfad);
        assertEinFund(pfad, pfad);
    }

    @Test
    void ak23KombinierendesZeichenImPfad() {
        String pfad = bs("C:¦Users¦mustermann¦e\u0301cole¦a.txt");
        assertEinFund("Datei " + pfad + " ok", pfad);
    }

    @Test
    void ak23KombinierendesZeichenAmEndeBleibtImFund() {
        String pfad = bs("C:¦Users¦e\u0301");
        assertEinFund("Datei " + pfad + " ok", pfad);
        // Kürzungszeichen gefolgt von kombinierendem Zeichen: nicht kürzen, kombinierendes Zeichen bleibt dabei
        String mitPunkt = bs("C:¦Users¦a.\u0301");
        assertEinFund("Datei " + mitPunkt + " ok", mitPunkt);
        String punktDanachKombi = bs("C:¦Users¦a\u0301.");
        assertEinFund("Datei " + punktDanachKombi + " ok", bs("C:¦Users¦a\u0301"));
    }

    @Test
    void ak23EmojiAlsSurrogatpaarImPfad() {
        String emoji = "\uD83D\uDE00";
        String pfad = bs("C:¦Users¦mustermann¦" + emoji + "Ordner¦a" + emoji + ".txt");
        assertEinFund("Datei " + pfad + " ok", pfad);
        assertEinFund(pfad + emoji, pfad + emoji);
        assertEinFund(pfad + "." + emoji, pfad + "." + emoji);
    }

    @Test
    void ak23EmojiDirektNachDemPfadendeBleibtImFundOhneSurrogateZuTrennen() {
        String emoji = "\uD83D\uDE00";
        String text = bs("C:¦a¦b") + emoji + " ende";
        Finding f = funde(text).get(0);
        assertEquals(bs("C:¦a¦b") + emoji, f.originalValue());
    }

    @Test
    void ak23NichtBmpBuchstabeImPfadnamen() {
        String pfad = bs("C:¦Ordner¦\uD835\uDC00\uD835\uDC01¦x.txt");
        assertEinFund(pfad, pfad);
    }

    // ------------------------------------------------------------------ unsichtbare Zeichen (Entscheidung)

    @ParameterizedTest
    @ValueSource(strings = {"\u00AD", "\u200B", "\u200C", "\u200D", "\u200E", "\u200F", "\u202A", "\u202E",
            "\u2060", "\uFEFF"})
    void unsichtbareFormatzeichenBeendenDenPfadNicht(String unsichtbar) {
        String pfad = bs("C:¦Tools¦BeispielTool" + unsichtbar + "¦a" + unsichtbar + "b.exe");
        assertEinFund("Start " + pfad + " ok", pfad);
        assertEinFund("Start " + pfad + unsichtbar + " ok", pfad + unsichtbar);
        assertEinFund(unsichtbar + pfad, pfad);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u2007", "\u202F", "\u2028", "\u2029", "\u3000", "\u2003", "\u0085",
            "\u000B", "\f"})
    void leerzeichenAllerArtBeendenDenPfad(String trenner) {
        String text = bs("Start C:¦Tools¦a" + trenner + "b.exe ok");
        assertEinFund(text, bs("C:¦Tools¦a"));
    }

    @Test
    void steuerzeichenBeendenDenPfad() {
        assertEinFund(bs("C:¦Tools¦a\u0000b.exe"), bs("C:¦Tools¦a"));
        assertEinFund(bs("C:¦Tools¦a\u001Fb.exe"), bs("C:¦Tools¦a"));
        assertEinFund(bs("C:¦Tools¦a\u007Fb.exe"), bs("C:¦Tools¦a"));
    }

    // ------------------------------------------------------------------ AK-1, AK-6, Aufbau

    @Test
    void ak6KonfidenzQuelleUndStufeEinesLaufwerksfundes() {
        Finding f = R.detect(bs("Start C:¦Tools¦a.exe")).get(0);
        assertEquals(0.95, f.confidence());
        assertEquals(ConfidenceLevel.SICHER, f.level());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(FindingType.PFAD, f.type());
    }

    @Test
    void ak1LeererTextUndNurLeerraumLiefernNichts() {
        assertKeinFund("");
        assertKeinFund("   ");
        assertKeinFund("\n\t\r\n");
    }

    @Test
    void nullAlsTextLiefertNullPointerExceptionMitParameternamen() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertEquals("text", e.getMessage());
    }

    @Test
    void zweimalScannenLiefertGleicheListe() {
        String text = bs("Kopiere C:¦Tools¦a.exe nach D:¦Ablage¦b.exe, siehe (E:¦x¦y).");
        assertEquals(R.detect(text), R.detect(text));
        assertEquals(R.detect(text), new PathRecognizer().detect(text));
    }

    @Test
    void ergebnisIstVonDerBetriebssystemKonventionUnabhaengigUndVeraendertDenTextNicht() {
        String text = bs("C:¦Tools¦a.exe");
        String kopie = new String(text);
        R.detect(text);
        assertEquals(kopie, text);
    }

    // ------------------------------------------------------------------ Standard-Scanner

    @Test
    void standardScannerEnthaeltDenPfadErkenner() {
        String text = bs("Tool C:¦Tools¦BeispielTool¦tool.exe auf Server 192.0.2.10 gestartet.");
        List<Finding> f = TextScanner.standard().scan(text);
        assertEquals(2, f.size());
        assertEquals(FindingType.PFAD, f.get(0).type());
        assertEquals(bs("C:¦Tools¦BeispielTool¦tool.exe"), f.get(0).originalValue());
        assertEquals("pfad-laufwerk", f.get(0).source());
        assertEquals(0.95, f.get(0).confidence());
        assertEquals(FindingType.IP, f.get(1).type());
        assertEquals("192.0.2.10", f.get(1).originalValue());
    }

    @Test
    void standardScannerRoundtripMitPfad() {
        String text = bs("Starte C:¦Program Files¦Mein Tool¦tool.exe und \"C:¦Tools¦BeispielTool¦b.exe\".");
        List<Finding> f = TextScanner.standard().scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, f);
        assertFalse(p.text().contains("BeispielTool"));
        assertFalse(p.text().contains("Tools"));
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text());
        assertTrue(back.unresolved().isEmpty());
    }

    @Test
    void standardScannerUrlBleibtEinUrlFundAuchWennDerPfadErkennerEinenKandidatenLiefert() {
        // Der Pfad-Erkenner liefert für https://… einen Kandidaten ab dem "s" (kein Startgrenzen-Zwang bei
        // Laufwerken); er liegt ganz in der URL, der umschließende URL-Fund gewinnt unverändert (AK-24).
        for (String url : List.of("https://example.invalid/a/b", "https://example.invalid/pfad?x=1",
                "http://example.invalid:8080/a", "https://example.invalid/?datei=" + bs("C:¦Tools¦a.exe"))) {
            String text = "Siehe " + url + " fuer Details";
            List<Finding> f = TextScanner.standard().scan(text);
            assertEquals(1, f.size(), text);
            assertEquals(FindingType.URL, f.get(0).type(), text);
            assertEquals(url, f.get(0).originalValue(), text);
            assertEquals("url", f.get(0).source(), text);
        }
    }

    @Test
    void standardScannerEmailImPfadBleibtEinPfadFund() {
        String text = bs("C:¦Benutzer¦erika.beispiel@example.invalid¦ablage");
        List<Finding> f = TextScanner.standard().scan(text);
        assertEquals(1, f.size());
        assertEquals(FindingType.PFAD, f.get(0).type());
        assertEquals(text, f.get(0).originalValue());
        assertEquals("pfad-laufwerk", f.get(0).source());
    }

    @Test
    void standardScannerPfadMitIpImPfad() {
        String text = bs("Ablage C:¦Daten¦192.0.2.10¦log.txt fertig");
        List<Finding> f = TextScanner.standard().scan(text);
        assertEquals(1, f.size());
        assertEquals(bs("C:¦Daten¦192.0.2.10¦log.txt"), f.get(0).originalValue());
        assertEquals(FindingType.PFAD, f.get(0).type());
    }

    // ------------------------------------------------------------------ AK-49 Grenzlast (je < 2 s)

    @Test
    void ak49LaufwerkMitWurzelVielfach() {
        String text = bs("C:¦").repeat(100_000);
        int n = assertTimeoutPreemptively(LIMIT, () -> {
            List<Finding> f = funde(text);
            assertEquals(text, f.get(0).originalValue());
            return f.size();
        });
        assertEquals(1, n);
    }

    @Test
    void ak49PfadMitLeerzeichenVielfach() {
        String text = bs("C:¦a ").repeat(100_000);
        int n = assertTimeoutPreemptively(LIMIT, () -> zaehle(text));
        assertEquals(100_000, n);
    }

    @Test
    void ak49PfadanfangGefolgtVon100000Woertern() {
        String text = bs("C:¦a") + " a".repeat(100_000);
        List<String> w = assertTimeoutPreemptively(LIMIT, () -> werte(text));
        assertEquals(List.of(bs("C:¦a")), w);
    }

    @Test
    void ak49SehrLangeZeichenfolgeOhneLeerzeichen() {
        String lang = bs("C:¦") + "a".repeat(1_000_000);
        List<String> w = assertTimeoutPreemptively(LIMIT, () -> werte(lang));
        assertEquals(List.of(lang), w);
        String ohnePfad = "a".repeat(1_000_000);
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(ohnePfad)));
        String vieleSegmente = bs("C:¦") + bs("a¦").repeat(500_000);
        assertEquals(List.of(vieleSegmente), assertTimeoutPreemptively(LIMIT, () -> werte(vieleSegmente)));
    }

    @Test
    void ak49KlammernVorUndHinterDemPfad() {
        String text = "(".repeat(100_000) + bs("C:¦a") + ")".repeat(100_000);
        List<Finding> f = assertTimeoutPreemptively(LIMIT, () -> funde(text));
        assertEquals(1, f.size());
        assertEquals(bs("C:¦a"), f.get(0).originalValue());
        assertEquals(100_000, f.get(0).start());
    }

    @Test
    void ak49NurBackslashes() {
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle("\\".repeat(100_000))));
    }

    @Test
    void ak49NurDoppelpunkte() {
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(":".repeat(100_000))));
    }

    @Test
    void ak49BuchstabeDoppelpunktVielfach() {
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle("a:".repeat(100_000))));
    }

    @Test
    void ak49LaufwerkMitVielenSegmenten() {
        String text = "C:" + bs("¦a").repeat(100_000);
        assertEquals(List.of(text), assertTimeoutPreemptively(LIMIT, () -> werte(text)));
    }

    @Test
    void ak49PunktOrdnerVielfachMitUndOhneLeerzeichen() {
        String ohne = bs("C:¦.").repeat(100_000);
        List<Finding> f = assertTimeoutPreemptively(LIMIT, () -> funde(ohne));
        assertEquals(1, f.size());
        assertEquals(ohne.length() - 1, f.get(0).end()); // nur der letzte Punkt wird gekürzt
        String mit = bs("C:¦. ").repeat(100_000);
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(mit)));
    }

    @Test
    void ak49WurzelGefolgtVonVielenPunkten() {
        String text = bs("C:¦") + ".".repeat(100_000);
        assertEquals(0, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)));
    }

    @Test
    void ak49VieleSchliessendeKlammernOhneOeffnende() {
        String text = bs("C:¦a") + ")".repeat(100_000);
        List<Finding> f = assertTimeoutPreemptively(LIMIT, () -> funde(text));
        assertEquals(List.of(bs("C:¦a")), f.stream().map(Finding::originalValue).collect(Collectors.toList()));
        String zwischen = bs("C:¦a)").repeat(50_000);
        List<Finding> g = assertTimeoutPreemptively(LIMIT, () -> funde(zwischen));
        assertEquals(1, g.size());
        assertEquals(zwischen.length() - 1, g.get(0).end());
    }

    @Test
    void ak49OeffnendeKlammernImPfad() {
        String text = bs("C:¦a") + "(".repeat(100_000);
        assertEquals(List.of(text), assertTimeoutPreemptively(LIMIT, () -> werte(text)));
    }

    @Test
    void ak49PfadeAbwechselndMitSemikolon() {
        String text = bs("C:¦a;").repeat(100_000);
        List<Finding> f = assertTimeoutPreemptively(LIMIT, () -> funde(text));
        assertEquals(1, f.size());
    }

    @Test
    void ak49VieleKurzeLaeufeMitLaufwerken() {
        String text = bs("x C:¦a\n").repeat(100_000);
        assertEquals(100_000, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)));
    }

    @Test
    void ak49EineMillionZeichenGemischterText() {
        String baustein = bs("Siehe C:¦Ordner¦BeispielTool¦tool.exe, danach D:/Ablage/b.txt. ");
        String text = baustein.repeat(1_000_000 / baustein.length() + 1);
        int erwartet = 2 * (1_000_000 / baustein.length() + 1);
        assertTrue(text.length() >= 1_000_000);
        assertEquals(erwartet, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)));
    }

    // ------------------------------------------------------------------ dokumentierte Ist-Zustände (Review T-10)

    @Test
    void fileUriVorT16BeginntDerFundAmLaufwerksbuchstabenAehnlichenKopfUndLaesstFilSichtbar() {
        // Ist-Zustand vor T-16 (wird in T-16 angepasst, dort liefert die Form FILE_URI den ganzen Ausdruck): Der
        // Laufwerkskopf braucht keine Startgrenze, daher beginnt der Fund am 'e' von "file" (e, ':', '/') und
        // reicht bis zum Ende; "fil" bleibt im Klartext sichtbar. Das ist ein Teilfund, kein Totalverlust.
        String text = "file:///C:/Tools/a.exe";
        List<Finding> funde = funde(text);
        assertEquals(1, funde.size());
        assertEquals(3, funde.get(0).start());
        assertEquals(text.length(), funde.get(0).end());
        assertEquals("e:///C:/Tools/a.exe", funde.get(0).originalValue());
        assertEquals("fil", text.substring(0, funde.get(0).start()));
    }

    @Test
    void laufwerkWurzelMitNurEinemUnsichtbarenFormatzeichenIstIstZustandEinFund() {
        // Ist-Zustand (festgeschrieben, bekannte Grenze OF-7): Ein unsichtbares Formatzeichen (U+200B) beendet den
        // Pfad nicht und zählt als Segment hinter dem Kopf; "C:\" + U+200B ist daher ein Fund, obwohl "C:\" allein
        // keiner ist. Zu groß ist kein Leck.
        String text = bs("C:¦") + "\u200B";
        assertEinFund(text, text);
    }

    @Test
    void ak49StandardScannerMitVielenLaufwerkspfaden() {
        String text = bs("C:¦a ").repeat(100_000);
        int n = assertTimeoutPreemptively(LIMIT, () -> TextScanner.standard().scan(text).size());
        assertEquals(100_000, n);
    }
}
