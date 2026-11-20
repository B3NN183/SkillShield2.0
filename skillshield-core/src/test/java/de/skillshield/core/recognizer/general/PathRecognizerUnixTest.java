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
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests der Formen UNIX und HOME (T-13, Plan Kap. 2.4.2 Zeilen {@code UNIX} und {@code HOME}). Die Testmethoden sind
 * nach Herkunft der Erwartung benannt: {@code plan...} (so im Plan festgelegt), {@code eigeneEntscheidung...}
 * (sicherste plan-nahe Auslegung, wo der Plan nichts oder Widerspruechliches sagt) und {@code bekannteFolge...}
 * (Fehltreffer oder Luecke, die der Plan in Kauf nimmt; im Bericht tabelliert). Pfade mit Backslash ueber
 * {@code bs(...)}; alle Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes.
 */
class PathRecognizerUnixTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private static final Set<String> QUELLEN = Set.of("pfad-laufwerk", "pfad-unc", "pfad-unix", "pfad-home", "pfad-host");

    // ------------------------------------------------------------------ Hilfen

    /** Funde des Erkenners; je Fund Teilstring, Typ, Quelle, Konfidenz/Stufe, Sortierung, Grenzen. */
    private static List<Finding> funde(String text) {
        return pruefe(text, R.detect(text));
    }

    private static List<Finding> pruefe(String text, List<Finding> funde) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(QUELLEN.contains(f.source()), "Quelle " + f.source());
            assertTrue(Set.of(0.95, 0.90, 0.80, 0.60).contains(f.confidence()), "Konfidenz " + f.confidence());
            assertEquals(ConfidenceLevel.of(f.confidence()), f.level());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
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

    /** Genau ein Fund mit Wert, Quelle und Konfidenz. */
    private static Finding assertFund(String text, String wert, String quelle, double konfidenz) {
        List<Finding> f = funde(text);
        assertEquals(List.of(wert), f.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Text: " + text);
        assertEquals(quelle, f.get(0).source(), "Quelle, Text: " + text);
        assertEquals(konfidenz, f.get(0).confidence(), "Konfidenz, Text: " + text);
        return f.get(0);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    private static List<Finding> scan(String text) {
        List<Finding> funde = SCANNER.scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Scanner: Teilstring");
            assertTrue(f.start() >= vorigesEnde, "Scanner: sortiert, ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static String pseudonymisiert(String text) {
        List<Finding> funde = scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    // ================================================================== LAUT PLAN: Zeile UNIX, positiv

    @Test
    void planAk12AbsoluterPfadImSatz() {
        Finding f = assertFund("Datei /opt/beispiel-tool/bin/tool laden", "/opt/beispiel-tool/bin/tool", "pfad-unix", 0.90);
        assertEquals(6, f.start());
        assertEquals(33, f.end());
        assertEquals(ConfidenceLevel.SICHER, f.level());
    }

    @Test
    void planAk18ZweiPfadeMitKommaWerdenZweiFunde() {
        assertEquals(List.of("/opt/beispiel/a", "/opt/beispiel/b"), werte("/opt/beispiel/a, /opt/beispiel/b"));
        for (Finding f : funde("/opt/beispiel/a, /opt/beispiel/b")) {
            assertEquals(0.90, f.confidence());
        }
    }

    @Test
    void planAk18ZweiPfadeNurMitLeerzeichenGetrennt() {
        assertEquals(List.of("/opt/beispiel/a", "/opt/beispiel/b"), werte("/opt/beispiel/a /opt/beispiel/b"));
    }

    @Test
    void planSchraegstrichUncOhneDateiHatZweiSegmente() {
        assertFund("//srv-beispiel/freigabe", "//srv-beispiel/freigabe", "pfad-unix", 0.80);
        assertFund("Ablage //srv-beispiel/freigabe ok", "//srv-beispiel/freigabe", "pfad-unix", 0.80);
    }

    @Test
    void planSchraegstrichUncMitDateiHatDreiSegmente() {
        assertFund("//srv-beispiel/freigabe/ordner/datei.txt", "//srv-beispiel/freigabe/ordner/datei.txt", "pfad-unix",
                0.90);
    }

    @Test
    void planSchraegstrichUncAllein() {
        // Plan N-2: "// Kommentar" und "//" haben kein Segment, "//srv-beispiel" nur eines
        assertKeinFund("//");
        assertKeinFund("// Kommentar");
        assertKeinFund("//srv-beispiel");
        assertKeinFund("// Kommentar zur Datei");
    }

    @Test
    void planBeispieleDerZeileUnixPositivEinzeln() {
        assertFund("/opt/beispiel-tool/bin/tool", "/opt/beispiel-tool/bin/tool", "pfad-unix", 0.90);
        assertFund("/opt/beispiel/a", "/opt/beispiel/a", "pfad-unix", 0.90);
    }

    // ================================================================== LAUT PLAN: Zeile UNIX, negativ (AK-13)

    @ParameterizedTest(name = "kein Fund: {0}")
    @ValueSource(strings = {"xcopy /s /e", "tool.exe /quiet", "/?", "ja / nein", "12/2025", "12/05/2025", "1/2",
            "24/7", "und/oder", "km/h", "Ein/Aus", "/etc", "/s", "xcopy /s", "/"})
    void planAk13NegativbeispieleOhneFund(String text) {
        assertKeinFund(text);
    }

    @Test
    void planAk13SchalterOhneFundImSatz() {
        assertKeinFund("Aufruf: xcopy /s /e und tool.exe /quiet, dann ja / nein, Hilfe mit /?.");
    }

    @Test
    void planAk13BeiDatumUndBruchImSatzKeinFund() {
        assertKeinFund("Termin 12/05/2025, Quote 1/2, Betrieb 24/7, und/oder km/h, Ein/Aus.");
    }

    @Test
    void planUnixPfadInEinerUrlHatKeineStartgrenze() {
        // /a/b in https://example.invalid/a/b hat keine Startgrenze (davor "d"): der Unix-Kandidat entsteht nicht.
        // (Der Erkenner allein liefert den bekannten Laufwerkskandidaten "p://example.invalid/a/b", T-10 Nachtrag 2;
        // im Standard-Scanner liegt er ganz in der URL.)
        for (Finding f : funde("https://example.invalid/a/b")) {
            assertNotEquals("pfad-unix", f.source());
            assertNotEquals("pfad-home", f.source());
        }
        List<Finding> scan = scan("https://example.invalid/a/b");
        assertEquals(1, scan.size());
        assertEquals(FindingType.URL, scan.get(0).type());
        assertEquals("https://example.invalid/a/b", scan.get(0).originalValue());
    }

    // ================================================================== LAUT PLAN: Zeile HOME

    @Test
    void planHomePositiv() {
        assertFund("~/beispiel/konfig.yml", "~/beispiel/konfig.yml", "pfad-home", 0.90);
        assertFund("~/x", "~/x", "pfad-home", 0.90);
        assertFund("Datei ~/Dokumente/akte.pdf laden", "~/Dokumente/akte.pdf", "pfad-home", 0.90);
    }

    @Test
    void planHomeNegativ() {
        assertKeinFund("~/");
        assertKeinFund(bs("~¦x"));
        assertKeinFund(bs("~erika¦x"));
        assertKeinFund("~");
        assertKeinFund("Heimat ~ Ende");
    }

    @Test
    void planHomeKonfidenzIstUnabhaengigVonDerSegmentzahl() {
        assertFund("~/a", "~/a", "pfad-home", 0.90);
        assertFund("~/a/b", "~/a/b", "pfad-home", 0.90);
        assertFund("~/a/b/c/d", "~/a/b/c/d", "pfad-home", 0.90);
        assertEquals(ConfidenceLevel.SICHER, funde("~/a").get(0).level());
    }

    // ================================================================== LAUT PLAN: Konfidenz je Segmentzahl

    @Test
    void planKonfidenzUnixZweiSegmenteWahrscheinlichDreiSicher() {
        Finding zwei = assertFund("/etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, zwei.level());
        Finding drei = assertFund("/var/log/app.log", "/var/log/app.log", "pfad-unix", 0.90);
        assertEquals(ConfidenceLevel.SICHER, drei.level());
        assertFund("/a/b/c/d/e/f", "/a/b/c/d/e/f", "pfad-unix", 0.90);
    }

    @Test
    void planKonfidenzJederFormIstInDerSegmentzahlMonotonNichtFallend() {
        for (PathForm form : PathForm.values()) {
            for (int kopf : new int[] {1, 2, 3, 7, 66}) {
                double vorige = form.confidence(kopf, 0);
                for (int segmente = 1; segmente <= 6; segmente++) {
                    double jetzt = form.confidence(kopf, segmente);
                    assertTrue(jetzt >= vorige,
                            form + " faellt bei " + segmente + " Segmenten, Kopflaenge " + kopf);
                    vorige = jetzt;
                }
            }
        }
        assertEquals(0.80, PathForm.UNIX.confidence(1, 2));
        assertEquals(0.90, PathForm.UNIX.confidence(1, 3));
        assertEquals(0.90, PathForm.HOME.confidence(2, 1));
        assertEquals(0.80, PathForm.HOME.confidence(7, 1));
        assertEquals(0.80, PathForm.HOST_PFAD.confidence(6, 1));
        assertEquals(0.90, PathForm.HOST_PFAD.confidence(6, 2));
        assertEquals("pfad-host", PathForm.HOST_PFAD.source());
        assertTrue(PathForm.HOST_PFAD.isStartBoundary("ab:/x", 1));
        assertEquals(1, PathForm.HOST_PFAD.minSegments());
        assertEquals("pfad-unix", PathForm.UNIX.source());
        assertEquals("pfad-home", PathForm.HOME.source());
        assertFalse(PathForm.UNIX.isStartBoundary("a/b", 1));
        assertTrue(PathForm.UNIX.isStartBoundary(" /b", 1));
        assertFalse(PathForm.UNIX.isStartBoundary(":/b", 1));
        assertFalse(PathForm.HOME.isStartBoundary("a~/b", 1));
        assertFalse(PathForm.HOME.isStartBoundary(":~/b", 1));
        assertTrue(PathForm.HOME.isStartBoundary(" ~/b", 1));
        assertEquals(2, PathForm.UNIX.minSegments());
        assertEquals(1, PathForm.HOME.minSegments());
    }

    // ================================================================== LAUT PLAN: Startgrenze (AK-12)

    @Test
    void planStartgrenzeKeinFundMitBuchstabeOderZiffernDavor() {
        assertKeinFund("a/opt/x");
        assertKeinFund("siehe/opt/x");
        assertKeinFund("12/opt/x");
        assertKeinFund("und/opt/x/y");
    }

    @ParameterizedTest(name = "Startgrenze erfuellt: {0}")
    @ValueSource(strings = {"=/opt/x", "(/opt/x)", "[/opt/x]", "{/opt/x}", "\"/opt/x\"", "'/opt/x'", "`/opt/x`",
            "</opt/x>", ">/opt/x", " /opt/x", "\t/opt/x", "\n/opt/x", "/opt/x", "a=/opt/x", "--config=/opt/x",
            ",/opt/x", ";/opt/x", "|/opt/x", "a,/opt/x", "a;/opt/x", "a|/opt/x"})
    void planStartgrenzeErfuellt(String text) {
        List<Finding> f = funde(text);
        assertEquals(1, f.size(), text);
        assertEquals("/opt/x", f.get(0).originalValue(), text);
        assertEquals("pfad-unix", f.get(0).source());
    }

    @ParameterizedTest(name = "keine Startgrenze: {0}")
    @ValueSource(strings = {"a/opt/x", "1/opt/x", ".../opt/x", "-/opt/x", "_/opt/x", "@/opt/x",
            "x//opt/x", "+/opt/x", "$/opt/x", "%/opt/x", "&/opt/x", "#/opt/x", "!/opt/x", "?/opt/x", "*/opt/x",
            "~/opt/x"})
    void planStartgrenzeNichtErfuellt(String text) {
        for (Finding f : funde(text)) {
            assertNotEquals("pfad-unix", f.source(), "kein Unix-Fund: " + text);
        }
    }

    @Test
    void planStartgrenzeBeiHome() {
        assertFund("x=~/beispiel/a", "~/beispiel/a", "pfad-home", 0.90);
        assertFund("(~/beispiel/a)", "~/beispiel/a", "pfad-home", 0.90);
        assertFund("\"~/beispiel/a\"", "~/beispiel/a", "pfad-home", 0.90);
        assertKeinFund("a~/beispiel/a");
        assertKeinFund("host:~/beispiel/a");
        assertKeinFund("1~/x");
    }

    @Test
    void planNachZeichenAusDerStartgrenzeKeinPfadMitnahmeDesPraefix() {
        // Das Praefix gehoert nicht zum Fund (Plan 2.4.1: der Fund beginnt am Kopf)
        Finding f = assertFund("--config=/etc/app/config.yml", "/etc/app/config.yml", "pfad-unix", 0.90);
        assertEquals(9, f.start());
    }

    // ================================================================== LAUT PLAN: Ende, Klammern, Leerzeichen

    @Test
    void planSatzzeichenAmEndeGehoerenNichtZumFund() {
        assertFund("Siehe /etc/hosts.", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts, danach", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts; oder", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts: genau", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts!", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts?", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe /etc/hosts...", "/etc/hosts", "pfad-unix", 0.80);
    }

    @Test
    void planKlammerRegel() {
        assertFund("(siehe /etc/hosts)", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Siehe (/etc/hosts)", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("/opt/Beispiel(x86)/tool", "/opt/Beispiel(x86)/tool", "pfad-unix", 0.90);
        assertFund("/opt/tool(1)", "/opt/tool(1)", "pfad-unix", 0.80);
        assertFund("[/etc/hosts]", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("{/etc/hosts}", "/etc/hosts", "pfad-unix", 0.80);
    }

    @Test
    void planAbschliessenderTrennerUndSternBleibenImFund() {
        assertFund("Ordner /var/log/ ok", "/var/log/", "pfad-unix", 0.80);
        assertFund("Alle /var/log/* ok", "/var/log/*", "pfad-unix", 0.90);
    }

    @Test
    void planEndetAmErstenLeerzeichenD5() {
        // Nutzerentscheidung D-5: ohne Anfuehrungszeichen endet der Pfad am ersten Leerzeichen
        assertFund("Datei /home/erika mustermann/Meine Akte.pdf ende", "/home/erika", "pfad-unix", 0.80);
    }

    @Test
    void planEndetAnTabZeilenendeUndSteuerzeichen() {
        assertEquals(List.of("/opt/a/b", "/opt/c/d"), werte("/opt/a/b\n/opt/c/d"));
        assertEquals(List.of("/opt/a/b", "/opt/c/d"), werte("/opt/a/b\r\n/opt/c/d"));
        assertEquals(List.of("/opt/a/b", "/opt/c/d"), werte("/opt/a/b\t/opt/c/d"));
        // "|" beendet den Lauf und ist seit dem Nachtrag 20.11.2026 zu T-13 eine Startgrenze: beide Pfade sind Funde
        assertEquals(List.of("/opt/a/b", "/opt/c/d"), werte("/opt/a/b|/opt/c/d"));
        assertEquals(List.of("/opt/c/d"), werte("ls | /opt/c/d"));
        assertEquals(List.of("/opt/a/b", "/opt/c/d"), werte("/opt/a/b<br>/opt/c/d"));
    }

    @Test
    void planGemischteUndDoppelteTrenner() {
        // Trenner sind fuer alle Formen / und \ ; ein Trenner-Lauf zaehlt wie ein Trenner (AK-22)
        assertFund(bs("/opt¦a¦b"), bs("/opt¦a¦b"), "pfad-unix", 0.90);
        assertFund("/opt//a", "/opt//a", "pfad-unix", 0.80);
        assertFund("/opt///a//b", "/opt///a//b", "pfad-unix", 0.90);
        assertFund(bs("/opt¦¦a"), bs("/opt¦¦a"), "pfad-unix", 0.80);
    }

    // ================================================================== LAUT PLAN: Anfuehrungszeichen (AK-16, D-5)

    @Test
    void planAnfuehrungszeichenMitLeerzeichenGanzerInhalt() {
        assertFund("\"/home/erika mustermann/Meine Akte.pdf\"", "/home/erika mustermann/Meine Akte.pdf", "pfad-unix",
                0.90);
        assertFund("Starte '/opt/Mein Tool/bin/tool' jetzt", "/opt/Mein Tool/bin/tool", "pfad-unix", 0.90);
        assertFund("Starte `/opt/Mein Tool/bin/x` jetzt", "/opt/Mein Tool/bin/x", "pfad-unix", 0.90);
        assertFund("Starte \"~/Meine Akten/a.pdf\" jetzt", "~/Meine Akten/a.pdf", "pfad-home", 0.90);
    }

    @Test
    void planAnfuehrungszeichenAnkerBleibtImText() {
        String text = "Starte \"/home/erika mustermann/Meine Akte.pdf\" jetzt";
        assertEquals("Starte \"[[PFAD_1]]\" jetzt", pseudonymisiert(text));
    }

    @Test
    void planNieGeschlossenFaelltAufDieRegelOhneAnfuehrungszeichenZurueck() {
        assertFund("Starte \"/home/erika mustermann/Meine Akte.pdf", "/home/erika", "pfad-unix", 0.80);
    }

    // ================================================================== LAUT PLAN: Mehrere Segmente / Positiv-Beispiele aus dem Auftrag

    @ParameterizedTest(name = "Pfad wird gefunden: {0}")
    @ValueSource(strings = {"/home/erika.mustermann/Dokumente/akte.pdf", "/etc/hosts", "/var/log/app.log",
            "/opt/BeispielTool/bin/tool", "/mnt/daten/akte.pdf", "/Users/erika.mustermann/Desktop/x",
            "/c/Users/erika.mustermann/x", "/tmp/a", "~/Dokumente/akte.pdf", "//srv-beispiel/freigabe/a.txt"})
    void planEchtePfadeWerdenGanzGefunden(String pfad) {
        List<Finding> f = funde("Datei " + pfad + " laden");
        assertEquals(1, f.size(), pfad);
        assertEquals(pfad, f.get(0).originalValue());
        assertEquals(pfad.startsWith("~") ? "pfad-home" : "pfad-unix", f.get(0).source());
        // dieselbe Eingabe am Textanfang, am Textende, in Anfuehrungszeichen und nach "="
        assertEquals(List.of(pfad), werte(pfad));
        assertEquals(List.of(pfad), werte("x=" + pfad));
        assertEquals(List.of(pfad), werte("\"" + pfad + "\""));
        assertEquals(List.of(pfad), werte("Datei " + pfad + "."));
    }

    @Test
    void planPfadNachOptionMitGleichheitszeichen() {
        assertFund("--config=/etc/app/config.yml", "/etc/app/config.yml", "pfad-unix", 0.90);
        assertFund("tool --log-file=/var/log/app.log --verbose", "/var/log/app.log", "pfad-unix", 0.90);
    }

    @Test
    void planPathVariableMitDoppelpunktIstEinFund() {
        // ":" trennt nicht und beendet nicht: ein Fund ueber die ganze Liste (alle Verzeichnisse geschuetzt)
        Finding f = assertFund("PATH=/usr/local/bin:/usr/bin", "/usr/local/bin:/usr/bin", "pfad-unix", 0.90);
        assertEquals(5, f.start());
        assertFund("export PATH=/usr/local/bin:/usr/bin:/bin ", "/usr/local/bin:/usr/bin:/bin", "pfad-unix", 0.90);
    }

    @Test
    void planPfadNachDoppelpunktMitLeerzeichen() {
        assertFund("Pfad: /etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Datei: ~/Dokumente/akte.pdf", "~/Dokumente/akte.pdf", "pfad-home", 0.90);
    }

    @Test
    void planPfadMitBenutzernamenIstGanzGeschuetzt() {
        String text = "Ablage /home/erika.mustermann/Dokumente/akte.pdf fertig";
        assertEquals("Ablage [[PFAD_1]] fertig", pseudonymisiert(text));
    }

    @Test
    void planDerPseudonymisierteTextEnthaeltKeinenNamenAusDemPfad() {
        String aus = pseudonymisiert("Kopiere /home/erika.mustermann/Dokumente/akte.pdf nach ~/Ablage/BeispielTool/x.");
        assertEquals("Kopiere [[PFAD_1]] nach [[PFAD_2]].", aus);
    }

    // ================================================================== LAUT PLAN: Schalter mit Pfadwert

    @Test
    void planLogSchalterMitLaufwerkspfadIstEinFundUeberDenGanzenAusdruck() {
        // Plan 2.4.2 "Folgen der Regeln": /log=C:\Logs\a.log ist EIN Fund (Unix-Form, Backslash ist Trenner);
        // der Pfad ist vollstaendig geschuetzt, der Schaltername ist mitersetzt.
        String text = bs("tool.exe /log=C:¦Logs¦a.log");
        Finding f = assertFund(text, bs("/log=C:¦Logs¦a.log"), "pfad-unix", 0.90);
        assertEquals(9, f.start());
        assertEquals("tool.exe [[PFAD_1]]", pseudonymisiert(text));
        assertEquals(List.of(bs("/log=C:¦Logs¦a.log")), werte(bs("/log=C:¦Logs¦a.log")));
        // ohne Backslash ist es ein normaler Unix-Fund
        assertFund("tool --log=/var/log/a.log", "/var/log/a.log", "pfad-unix", 0.90);
    }

    @Test
    void bekannteFolgeUndOderBeidesUndRdSQ() {
        // Plan 2.4.2 (OF-4, bekannte Grenze): ein Leerzeichen vor dem Schraegstrich ergibt einen Pfad
        assertFund("und /oder/beides", "/oder/beides", "pfad-unix", 0.80);
        assertFund("rd /s/q", "/s/q", "pfad-unix", 0.80);
        assertFund("dir /w/p ordner", "/w/p", "pfad-unix", 0.80);
    }

    // ================================================================== EIGENE ENTSCHEIDUNG: Startgrenze nicht abgeschafft

    @Test
    void eigeneEntscheidungPfadNachBuchstabeOhneLeerzeichenIstKeinUnixFund() {
        // Die Startgrenze bleibt (Plan 2.4.1, AK-12, "nicht mitten in einem Wort"). Folge: kein Fund (offene Planfrage)
        assertKeinFund("siehe/etc/hosts");
        assertKeinFund("-I/usr/include/foo");
        assertKeinFund("-o/tmp/out.txt");
        assertKeinFund("user@host/etc/hosts");
    }

    @Test
    void eigeneEntscheidungPfadNachDoppelpunktOhneLeerzeichenKommtUeberDieLaufwerksregel() {
        // Seit dem Nachtrag 20.11.2026 zu T-13 (Form HOST_PFAD) beginnt der Fund am Anfang des Wortes vor dem
        // Doppelpunkt; das Wort wird mitersetzt (vorher: Laufwerksregel ab "d:", "Pfa" blieb sichtbar, 0.95). Die
        // Konfidenz ist die der Host-Form (0.90 bei zwei Segmenten, Plan 7a (ii)).
        assertFund("Pfad:/etc/hosts", "Pfad:/etc/hosts", "pfad-host", 0.90);
        assertFund("Datei:/var/log/app.log", "Datei:/var/log/app.log", "pfad-host", 0.90);
        assertFund("Port8080:/etc/hosts", "Port8080:/etc/hosts", "pfad-host", 0.90);
    }

    @Test
    void eigeneEntscheidungHostMitUnixPfadImScpStil() {
        // Standard-Scanner: Host und Pfad als ein Fund der Form HOST_PFAD (vorher ueber die Laufwerksregel ab "e:",
        // der Hostanfang blieb sichtbar)
        List<Finding> a = scan("host.example:/export/home");
        assertEquals(1, a.size());
        assertEquals("host.example:/export/home", a.get(0).originalValue());
        assertEquals("pfad-host", a.get(0).source());
        assertEquals("[[PFAD_1]]", pseudonymisiert("host.example:/export/home"));
        // mit Benutzer: die E-Mail-Form ueberlappt den Laufwerkskandidaten; zusammengelegt ueber die ganze Angabe
        List<Finding> b = scan("scp nutzer@host.example:/pfad/datei");
        assertEquals(1, b.size());
        assertEquals("nutzer@host.example:/pfad/datei", b.get(0).originalValue());
        assertEquals("zusammengelegt", b.get(0).source());
        assertEquals("scp [[EMAIL_1]]", pseudonymisiert("scp nutzer@host.example:/pfad/datei"));
        // ein Pfad ohne Buchstaben vor dem Doppelpunkt (Port) bleibt offen
        assertEquals(List.of(), scan("host.example:8080:/pfad/datei"));
    }

    @Test
    void eigeneEntscheidungTildeBenutzerBleibtOhneFundOf4() {
        // Plan OF-4 (~benutzer/x kein Fund) ist durch den Nachtrag 20.11.2026 zu T-13 Punkt 2 ersetzt (Fund mit 0.80,
        // siehe planHomeBenutzer...); ohne Segment hinter dem Schraegstrich bleibt es kein Fund
        assertKeinFund("~erika");
        assertKeinFund("cd ~erika/");
        assertKeinFund("~");
        assertKeinFund("cd ~");
        assertKeinFund("cd ~ && ls");
    }

    @Test
    void eigeneEntscheidungWindowsSchalterMitUnixLaufwerkspfadOhneZusatz() {
        assertFund(bs("tool.exe /log=C:¦Logs¦a.log /v"), bs("/log=C:¦Logs¦a.log"), "pfad-unix", 0.90);
        assertFund(bs("tool.exe /out:D:¦Temp¦x.txt"), bs("/out:D:¦Temp¦x.txt"), "pfad-unix", 0.90);
    }

    @Test
    void eigeneEntscheidungZweiSegmentePfadMitKleinemUmfangIstWahrscheinlich() {
        // /tmp/a ist ein echter Pfad, aber nur "wahrscheinlich" (zwei Segmente, 0.80), wie /etc/hosts
        assertFund("/tmp/a", "/tmp/a", "pfad-unix", 0.80);
        assertFund("Datei /dev/null leeren", "/dev/null", "pfad-unix", 0.80);
    }

    // ================================================================== BEKANNTE FOLGEN (im Bericht tabelliert)

    @Test
    void bekannteFolgeSchlussTagAlsKeinFund() {
        // Schliessende HTML-Tags: "/b" hat nur ein Segment
        assertKeinFund("</b>");
        assertKeinFund("<b>fett</b>");
        assertKeinFund("</div></td></tr>");
        assertKeinFund("<p>Text</p>");
    }

    @Test
    void bekannteFolgeHtmlPfadInAttributIstEinFund() {
        assertFund("<a href=\"/index/home\">", "/index/home", "pfad-unix", 0.80);
        assertFund("<a href=/index/home>", "/index/home", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeUrlPfadAmWortanfangNachLeerzeichen() {
        // Pfad hinter Leerzeichen zaehlt als Pfad, auch wenn er zu einer Beschreibung gehoert
        assertFund("Seite /impressum/kontakt ansehen", "/impressum/kontakt", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeKommentarMitSchraegstrichen() {
        assertKeinFund("// Kommentar");
        assertKeinFund("/* Kommentar */");
        assertKeinFund("/**/");
        assertKeinFund("/*/");
        assertFund("//kommentar/zeile", "//kommentar/zeile", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeBefehlSchalterMitKlammerPfad() {
        assertKeinFund("(/)");
        assertKeinFund("(/a)");
    }

    // ================================================================== Gewoehnlicher Text: Ist-Verhalten des Erkenners

    @ParameterizedTest(name = "gewoehnlicher Text ohne Fund: {0}")
    @ValueSource(strings = {"und/oder", "\u00C4rzte/\u00C4rztinnen", "19/11/2026", "01/2026", "1/2", "km/h", "TCP/IP",
            "n/a", "Mo/Di/Mi", "</b>", "example.invalid/a/b", "a/b/c", "/s", "/?", " / ", "//", "// Kommentar",
            "/* Kommentar */", "s/alt/neu/g", "10.0/8", "192.0.2.0/24", "19.11.2026", "11/19/2026", "w/o", "+/-",
            "(n/a)", "Tel./Fax", "(m/w/d)", "24/7", "Ein/Aus", "1/2 Liter und 3/4 Jahr", ":/", "Gesundheit/Soziales",
            "(Kinder/Jugendliche)", "\"und/oder\"", "'a/b/c'", "\u201E\u00C4rzte/\u00C4rztinnen\u201C",
            "Datum 19.11./20.11.2026", "12.05.2025/2026", "Version 1.2/1.3", "[/INST]", "</s>"})
    void gewoehnlicherTextHatKeinenFund(String text) {
        assertKeinFund(text);
    }

    @Test
    void gewoehnlicherTextImSatzOhneFund() {
        String text = "Die \u00C4rzte/\u00C4rztinnen und/oder Pflegekr\u00E4fte (m/w/d) sind 24/7 erreichbar (Mo/Di/Mi) "
                + "bei 10 km/h, Stand 19/11/2026 bzw. 01/2026, Quote 1/2, TCP/IP, n/a.";
        assertKeinFund(text);
        assertEquals(List.of(), scan(text));
    }

    @Test
    void gewoehnlicherTextMitVielenSchraegstrichenInWoertern() {
        assertKeinFund("und/oder ".repeat(5_000));
        assertKeinFund("a/b/c/d/e ".repeat(5_000));
    }

    // ================================================================== Standard-Scanner: Abgrenzung

    @Test
    void scannerUrlMitPfadIstGenauEinUrlFund() {
        List<Finding> f = scan("http://example.invalid/a/b");
        assertEquals(1, f.size());
        assertEquals(FindingType.URL, f.get(0).type());
        assertEquals("url", f.get(0).source());
        assertEquals("http://example.invalid/a/b", f.get(0).originalValue());
        assertEquals("[[URL_1]]", pseudonymisiert("http://example.invalid/a/b"));
    }

    @Test
    void scannerUrlMitUnixPfadImQueryBleibtEinUrlFund() {
        String url = "https://example.invalid/?f=/home/erika.mustermann/x";
        List<Finding> f = scan("Link " + url + " ende");
        assertEquals(1, f.size());
        assertEquals(FindingType.URL, f.get(0).type());
        assertEquals(url, f.get(0).originalValue());
        assertEquals("url", f.get(0).source());
    }

    @Test
    void scannerUrlInKlammernAnfuehrungszeichenUndSpitzenKlammern() {
        for (String text : List.of("(https://example.invalid/a/b)", "\"https://example.invalid/a/b\"",
                "<https://example.invalid/a/b>", "[https://example.invalid/a/b]", "siehe https://example.invalid/a/b.")) {
            List<Finding> f = scan(text);
            assertEquals(1, f.size(), text);
            assertEquals(FindingType.URL, f.get(0).type(), text);
            assertEquals("https://example.invalid/a/b", f.get(0).originalValue(), text);
        }
    }

    @Test
    void scannerUrlOhneSchemaIstKeinFund() {
        assertEquals(List.of(), scan("example.invalid/a/b"));
        assertEquals(List.of(), scan("www.example.invalid/a/b/c"));
    }

    @Test
    void scannerIpMitPraefixlaengeBleibtIpFundOhnePfad() {
        List<Finding> f = scan("Netz 192.0.2.0/24 frei");
        assertEquals(1, f.size());
        assertEquals(FindingType.IP, f.get(0).type());
        assertEquals("192.0.2.0", f.get(0).originalValue());
        assertEquals("Netz [[IP_1]]/24 frei", pseudonymisiert("Netz 192.0.2.0/24 frei"));
    }

    @Test
    void scannerGewoehnlicherTextOhneFund() {
        for (String text : List.of("und/oder", "19/11/2026", "01/2026", "1/2", "km/h", "TCP/IP", "n/a", "Mo/Di/Mi", "</b>",
                "a/b/c", "/s", "/?", " / ", "//", "/* Kommentar */", "s/alt/neu/g", "10.0/8", "\u00C4rzte/\u00C4rztinnen",
                "19.11.2026", "11/19/2026")) {
            assertEquals(List.of(), scan(text), text);
        }
    }

    @Test
    void scannerWindowsSchalterMitPfadwert() {
        List<Finding> f = scan(bs("tool.exe /log=C:¦Logs¦a.log"));
        assertEquals(1, f.size());
        assertEquals(bs("/log=C:¦Logs¦a.log"), f.get(0).originalValue());
        assertEquals("pfad-unix", f.get(0).source());
    }

    @Test
    void scannerKennzeichnungVonHomeUndUnixImMischtext() {
        String text = "Mail erika.mustermann@example.invalid, Server 192.0.2.10, Datei /etc/app/x.yml und ~/Ablage/y.";
        List<Finding> f = scan(text);
        assertEquals(List.of(FindingType.EMAIL, FindingType.IP, FindingType.PFAD, FindingType.PFAD),
                f.stream().map(Finding::type).collect(Collectors.toList()));
        assertEquals("/etc/app/x.yml", f.get(2).originalValue());
        assertEquals("~/Ablage/y", f.get(3).originalValue());
        assertEquals("pfad-home", f.get(3).source());
    }

    // ================================================================== Auflage 1: Mindestsegmente nach der Kuerzung

    @Test
    void auflage1AusgeglichenerKlammerSegmentZaehltZuMindestsegmenten() {
        // pessimistisch (vor dem Kuerzen) ein Segment, tatsaechlich zwei ("opt(" und ")"): kein Fund darf verloren gehen
        assertFund("/opt(/)", "/opt(/)", "pfad-unix", 0.80);
        assertFund("Datei /opt(/) ok", "/opt(/)", "pfad-unix", 0.80);
        assertFund("/a[/]", "/a[/]", "pfad-unix", 0.80);
        assertFund("/a{/}", "/a{/}", "pfad-unix", 0.80);
        assertFund("//srv(/)", "//srv(/)", "pfad-unix", 0.80);
        assertFund("(/opt(/))", "/opt(/)", "pfad-unix", 0.80);
    }

    @Test
    void auflage1MitSatzzeichenHinterDenKlammern() {
        assertFund("Siehe /opt(/).", "/opt(/)", "pfad-unix", 0.80);
        assertFund("Siehe /opt(/),", "/opt(/)", "pfad-unix", 0.80);
    }

    @Test
    void auflage1UeberzaehligeKlammerBildetKeinSegment() {
        assertKeinFund("/opt/)");
        assertKeinFund("/opt/))");
        assertKeinFund("/opt(/");
        assertKeinFund("/opt/.");
        assertKeinFund("/opt/...");
        assertFund("/opt/a)", "/opt/a", "pfad-unix", 0.80);
    }

    @Test
    void auflage1DreiSegmenteMitKlammerSegmentErgebenSicher() {
        assertFund("/a/b(/)", "/a/b(/)", "pfad-unix", 0.90);
    }

    @Test
    void auflage1FuerJedeFormKeinFundVerlustBeiKlammerSegmenten() {
        // Formen mit Mindestzahl 1 (LAUFWERK, UNC, HOME) und die Form mit Mindestzahl 2 (UNIX): jeweils mit einem
        // Segment, das nur aus schliessender Klammer besteht. Die Orakel-Eigenschaftstests (PathRecognizerUnixEigen-
        // schaftTest) pruefen das fuer alle Formen mit Zufallstexten.
        assertFund(bs("C:¦a(¦)"), bs("C:¦a(¦)"), "pfad-laufwerk", 0.95);
        assertFund(bs("C:¦(¦)"), bs("C:¦(¦)"), "pfad-laufwerk", 0.95);
        assertFund(bs("¦¦srv(¦)"), bs("¦¦srv(¦)"), "pfad-unc", 0.95);
        assertFund("~/a(/)", "~/a(/)", "pfad-home", 0.90);
        assertFund("~/(/)", "~/(/)", "pfad-home", 0.90);
        assertFund("/opt(/)", "/opt(/)", "pfad-unix", 0.80);
    }

    @Test
    void auflage1DieUnicodeNachbarschaftAendertDasErgebnisNicht() {
        assertFund("/opt\u00E4(/)", "/opt\u00E4(/)", "pfad-unix", 0.80);
        assertFund("/opt\uD83D\uDE00(/)", "/opt\uD83D\uDE00(/)", "pfad-unix", 0.80);
    }

    // ================================================================== Auflage 2: unsortierte Zitat-Funde

    /**
     * Gegenbeispiel (Auflage 2, nach Nachtrag 3b neu gebaut): Seit alle 13 Anfuehrungszeichen Startgrenze sind, hat ein
     * Kopf direkt hinter einem Zeichen in jedem umschliessenden Paar dieselbe Gueltigkeit bis auf die Segmentzahl.
     * Das oeffnende "'" (einfache Familie, schliesst an U+2019 an Position 8) und U+201E (doppelte Familie, oeffnet an
     * Position 1, schliesst an U+201C) bilden ueberlappende Paare. Im Paar mit U+201E ist der Kopf UNIX "/" an Position
     * 2 gueltig (hinter "/" stehen zwei Segmente: zwei Backslashes mit "srv" und U+2019, dann "x"; das Paar reicht
     * ueber U+2019 hinaus). Im Paar mit "'" endet der Inhalt vor U+2019: hinter "/" steht nur das Segment aus zwei
     * Backslashes und "srv", ein Segment, UNIX scheitert, der erste gueltige Kopf
     * ist UNC ab Position 3 (Mindestzahl 1). Das frueher beginnende Paar liefert damit den spaeteren Start.
     */
    private static final String UNSORTIERT = bs("'\u201E/\u00A6\u00A6srv\u2019/x \u201C");

    @Test
    void auflage2ZitatFundeKoennenUnsortiertEntstehen() {
        List<Finding> roh = PathRecognizer.collectQuoted(UNSORTIERT, PathForm.values());
        assertEquals(2, roh.size());
        assertEquals(bs("\u00A6\u00A6srv"), roh.get(0).originalValue());
        assertEquals(bs("/\u00A6\u00A6srv\u2019/x "), roh.get(1).originalValue());
        assertTrue(roh.get(0).start() > roh.get(1).start(), "roh unsortiert: Sortierung in detectQuoted ist noetig");
    }

    @Test
    void auflage2DasErgebnisIstTrotzdemSortiertUndUeberlappungsfrei() {
        List<Finding> f = funde(UNSORTIERT);
        assertEquals(List.of(bs("/\u00A6\u00A6srv\u2019/x ")),
                f.stream().map(Finding::originalValue).collect(Collectors.toList()));
        assertEquals(2, f.get(0).start());
        // nur mit LAUFWERK und UNC beginnen beide Paare am UNC-Kopf (Position 3): gleicher Start, nicht unsortiert
        PathForm[] nurAltFormen = {PathForm.LAUFWERK, PathForm.UNC};
        List<Finding> nurAlt = PathRecognizer.collectQuoted(UNSORTIERT, nurAltFormen);
        assertEquals(2, nurAlt.size());
        assertEquals(3, nurAlt.get(0).start());
        assertEquals(3, nurAlt.get(1).start());
    }

    // ================================================================== Unicode (AK-23)

    @Test
    void ak23UmlauteEmojiUndKombinierendesZeichenImPfad() {
        String pfad = "/home/erika.mustermann/\u00DCbersicht/Gr\u00F6\u00DFe.txt";
        assertFund("Datei " + pfad + " ok", pfad, "pfad-unix", 0.90);
        String emoji = "/home/erika/\uD83D\uDE00/x";
        assertFund("Datei " + emoji + " ok", emoji, "pfad-unix", 0.90);
        String kombi = "/home/erika/e\u0301cole/x";
        assertFund("Datei " + kombi + " ok", kombi, "pfad-unix", 0.90);
        String kombiEnde = "/home/e\u0301";
        assertFund("Datei " + kombiEnde + " ok", kombiEnde, "pfad-unix", 0.80);
    }

    @Test
    void ak23KombinierendesZeichenVorDemKopfVerhindertDieStartgrenze() {
        assertKeinFund("e\u0301/opt/x");
        assertKeinFund("\uD83D\uDE00/opt/x");
    }

    @Test
    void ak23UnsichtbaresZeichenImPfadBeendetNichtUndDavorHatKeineStartgrenze() {
        assertFund("Datei /opt/a\u200Bb/c ok", "/opt/a\u200Bb/c", "pfad-unix", 0.90);
        // ein unsichtbares Zeichen direkt vor dem Kopf ist keine Startgrenze (wie bei den anderen Formen)
        assertKeinFund("x\u200B/opt/a/b");
    }

    @Test
    void ak23FundGrenzenTrennenKeinSurrogatpaar() {
        String text = "\uD83D\uDE00 /opt/\uD83D\uDE00/\uD83D\uDE00. \uD83D\uDE00";
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals("/opt/\uD83D\uDE00/\uD83D\uDE00", f.get(0).originalValue());
    }

    // ================================================================== Grenzlast (AK-49), je < 2 s

    private static void laufzeit(String text, int erwartet) {
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefe(text, r);
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        int vorigesEnde = 0;
        for (Finding f : s) {
            assertTrue(f.start() >= vorigesEnde, "Scanner: sortiert, ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        if (erwartet >= 0) {
            assertEquals(erwartet, r.size(), "Fundzahl Erkenner");
            assertEquals(erwartet, s.size(), "Fundzahl Scanner");
        }
    }

    @Test
    void ak49SchraegstrichVielfach() {
        laufzeit("/".repeat(1_000_000), 0);
    }

    @Test
    void ak49DoppelterSchraegstrichVielfach() {
        laufzeit("//".repeat(500_000), 0);
    }

    @Test
    void ak49SchraegstrichUndBuchstabeVielfachWoertlich() {
        // "/a" x n: ein Lauf "/a/a/a...", ein Fund
        String text = "/a".repeat(500_000);
        laufzeit(text, 1);
        assertEquals(text, R.detect(text).get(0).originalValue());
        assertEquals(0.90, R.detect(text).get(0).confidence());
    }

    @Test
    void ak49SchraegstrichABMitLeerzeichenVielfach() {
        String text = "/a/b ".repeat(200_000);
        laufzeit(text, 200_000);
        assertEquals("/a/b", R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49SchraegstrichAMitLeerzeichenVielfachHatKeinenFund() {
        laufzeit("/a ".repeat(300_000), 0);
    }

    @Test
    void ak49BuchstabeUndSchraegstrichVielfach() {
        laufzeit("a/".repeat(500_000), 0);
    }

    @Test
    void ak49TildeVielfach() {
        laufzeit("~".repeat(1_000_000), 0);
    }

    @Test
    void ak49TildeSchraegstrichVielfachWoertlich() {
        // "~/" x n: Kopf an Position 0, dahinter Segmente "~" ...: ein Fund ueber den ganzen Lauf
        String text = "~/".repeat(500_000);
        laufzeit(text, 1);
        assertEquals(text, R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49TildeSchraegstrichMitLeerzeichenVielfach() {
        // "~/ " x n: Kopf ohne Segment in jedem Lauf
        laufzeit("~/ ".repeat(300_000), 0);
    }

    @Test
    void ak49TildeSchraegstrichAMitLeerzeichenVielfach() {
        String text = "~/a ".repeat(250_000);
        laufzeit(text, 250_000);
        assertEquals("~/a", R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49ZitatMitUnixKopfNieGeschlossen() {
        // "\"/a/b c" x n: Anfuehrungszeichen in jedem Wort; je Paar ein Zitatfund, dazu der Fund hinter dem letzten
        String text = "\"/a/b c".repeat(100_000);
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefe(text, r);
        assertEquals(100_000, r.size());
        assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
    }

    @Test
    void ak49ZitatMitUnixKopfGeschlossenUndLeerzeichenVielfach() {
        String text = "\"/a/b c\" ".repeat(100_000);
        laufzeit(text, 100_000);
        assertEquals("/a/b c", R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49EinZitatOeffnetNieGeschlossenDanachVieleWoerter() {
        String text = "\"/a/b c" + " x".repeat(400_000);
        laufzeit(text, 1);
        assertEquals("/a/b", R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49DreizehnAnfuehrungszeichenArtenMitUnixKoepfen() {
        String[] zeichen = {"\"", "\u201C", "\u201D", "\u201E", "\u00AB", "\u00BB", "'", "\u2018", "\u2019", "\u201A",
                "\u2039", "\u203A", "`"};
        assertEquals(13, zeichen.length);
        for (String q : zeichen) {
            laufzeit((q + "/a/b c").repeat(40_000), -1);
            laufzeit((q + "/a/b c" + q + " ").repeat(40_000), -1);
            laufzeit((q + "~/a b").repeat(40_000), -1);
        }
        // alle Arten gemischt in einem Text
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40_000; i++) {
            for (String q : zeichen) {
                sb.append(q).append("/a/b c ");
            }
        }
        laufzeit(sb.toString(), -1);
    }

    @Test
    void ak49SehrVieleKoepfeOhneGenugSegmenteInEinemLauf() {
        // jeder "/" hat Startgrenze ("=" davor) und nur ein kurzes Segment dahinter
        laufzeit("=/".repeat(500_000), -1);
        laufzeit("(/".repeat(500_000), -1);
        laufzeit("=/(/)".repeat(200_000), -1);
        // Koepfe, bei denen nur das Klammer-Segment fehlt oder hinzukommt (Pruefung nach der Kuerzung)
        laufzeit("x=/a(/)".repeat(150_000), -1);
        laufzeit("/a(/)".repeat(200_000), -1);
        laufzeit("/a/)".repeat(250_000), -1);
        laufzeit("/a(/".repeat(250_000), -1);
        // alle Koepfe im Lauf sind zu kurz: " /a" -> je Lauf ein Kopf
        laufzeit(" /a".repeat(300_000), 0);
    }

    @Test
    void ak49UndOderVielfachImText() {
        String text = "und/oder ".repeat(100_000);
        laufzeit(text, 0);
        laufzeit("Mo/Di/Mi ".repeat(100_000), 0);
        laufzeit("19/11/2026 ".repeat(90_000), 0);
        laufzeit("km/h ".repeat(200_000), 0);
    }

    @Test
    void ak49SehrLangerPfadOhneLeerzeichen() {
        String text = "/" + "abcdefgh/".repeat(150_000) + "x";
        laufzeit(text, 1);
        assertEquals(text, R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49EinPfadAmEndeVieleKlammern() {
        // Kuerzung am Ende laeuft einmal je Fund; viele schliessende Klammern hinter dem Pfad
        String text = "/a/b" + ")".repeat(500_000);
        laufzeit(text, 1);
        assertEquals("/a/b", R.detect(text).get(0).originalValue());
    }

    // ================================================================== NACHTRAG 20.11.2026 Punkt 1: Startgrenzen , ; |

    @Test
    void nachtragStartgrenzeKommaSemikolonUndSenkrechterStrichUnix() {
        assertFund("Datei,/etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("Datei;/etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
        assertFund("a;/opt/x/y", "/opt/x/y", "pfad-unix", 0.90);
        assertFund("cmd|/opt/x/y", "/opt/x/y", "pfad-unix", 0.90);
        assertFund("cmd | /opt/x/y", "/opt/x/y", "pfad-unix", 0.90);
        Finding f = assertFund("Datei,/etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
        assertEquals(6, f.start());
        assertEquals("Datei,[[PFAD_1]]", pseudonymisiert("Datei,/etc/hosts"));
    }

    @Test
    void nachtragStartgrenzeKommaSemikolonUndSenkrechterStrichHome() {
        assertFund("x,~/a/b", "~/a/b", "pfad-home", 0.90);
        assertFund("x;~/a/b", "~/a/b", "pfad-home", 0.90);
        assertFund("x|~/a/b", "~/a/b", "pfad-home", 0.90);
        assertFund("x,~erika/a", "~erika/a", "pfad-home", 0.80);
    }

    @Test
    void nachtragPathMitSemikolonIstEinFundIstVerhalten() {
        // Ist-Verhalten: Der Lauf reicht ueber ";" hinweg; der zweite Pfad liegt im ersten Fund (ein Fund, 0.90)
        assertFund("PATH=/usr/bin;/opt/x/y", "/usr/bin;/opt/x/y", "pfad-unix", 0.90);
        assertFund("/usr/bin;/opt/x/y", "/usr/bin;/opt/x/y", "pfad-unix", 0.90);
    }

    @Test
    void nachtragGewoehnlicherTextMitKommaSemikolonStrichBleibtOhneFund() {
        assertKeinFund("und/oder");
        assertKeinFund("1,/2"); // ein Segment hinter dem Schraegstrich
        assertKeinFund("a,b/c");
        assertKeinFund("Mo,/Di");
        assertKeinFund("\u00C4rzte,/innen");
        assertKeinFund("a;/b");
        assertKeinFund("a|/b");
        assertKeinFund("01/2026, 02/2026; 03/2026");
        assertKeinFund("km/h, m/s; und/oder");
        assertKeinFund("Ein,/Aus, Ja;/Nein");
    }

    @Test
    void bekannteFolgeAufzaehlungMitSchraegstrichNachKommaIstEinFundMitNullAcht() {
        // "\u00C4rzte,/innen/x": zwei Segmente hinter dem Schraegstrich nach dem Komma; Fehltreffer, Konfidenz 0.80
        assertFund("\u00C4rzte,/innen/x", "/innen/x", "pfad-unix", 0.80);
        assertFund("Hausaerzte,/innen/und weitere", "/innen/und", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeCsvMitSemikolonUndSchraegstrichIstEinFundMitNullAcht() {
        // CSV "a;/b;/c": Segmente "b;" und "c" -> ein Fund ab dem ersten "/" (zu gross, kein Leck), Konfidenz 0.80
        assertFund("a;/b;/c", "/b;/c", "pfad-unix", 0.80);
        assertFund("a;/b;/c;/d", "/b;/c;/d", "pfad-unix", 0.90);
    }

    @Test
    void bekannteFolgeMarkdownTabelleMitPfadzelleIstEinFundMitNullAcht() {
        assertFund("|/a/b|", "/a/b", "pfad-unix", 0.80);
        assertFund("| /a/b | x |", "/a/b", "pfad-unix", 0.80);
        assertKeinFund("|/a|");
        assertKeinFund("| a/b | c/d |");
    }

    // ================================================================== NACHTRAG 20.11.2026 Punkt 2: ~name/...

    @Test
    void nachtragHomeBenutzerPositivMitNullAcht() {
        assertFund("~erika/akte.pdf", "~erika/akte.pdf", "pfad-home", 0.80);
        assertFund("~erika.mustermann/Dokumente/a.txt", "~erika.mustermann/Dokumente/a.txt", "pfad-home", 0.80);
        assertFund("~_svc/x", "~_svc/x", "pfad-home", 0.80);
        assertFund("(~erika/x)", "~erika/x", "pfad-home", 0.80);
        assertFund("\"~erika/Meine Akte.pdf\"", "~erika/Meine Akte.pdf", "pfad-home", 0.80);
        assertFund("Datei ~erika/a laden", "~erika/a", "pfad-home", 0.80);
        assertFund("x=~erika/a/b/c/d", "~erika/a/b/c/d", "pfad-home", 0.80);
        assertFund("~a-b_c.d9/x", "~a-b_c.d9/x", "pfad-home", 0.80);
    }

    @Test
    void nachtragHomeBenutzerKonfidenzBleibtBeiMehrSegmentenNullAchtUndEigenerHomeNullNeun() {
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, funde("~erika/a/b/c").get(0).level());
        assertFund("~/a/b/c", "~/a/b/c", "pfad-home", 0.90);
        assertFund("~/a", "~/a", "pfad-home", 0.90);
        assertFund("~erika/a", "~erika/a", "pfad-home", 0.80);
    }

    @Test
    void nachtragHomeBenutzerNegativ() {
        assertKeinFund("~");
        assertKeinFund("~/");
        assertKeinFund("~erika");
        assertKeinFund("~erika/");
        assertKeinFund("~erika//");
        assertKeinFund("~erika/.");
        assertKeinFund("~30/40");
        assertKeinFund("~.x/y");
        assertKeinFund("~-x/y");
        assertKeinFund("a~erika/x");
        assertKeinFund("host:~erika/x");
        assertKeinFund("1~erika/x");
        assertKeinFund(bs("~erika¦x"));
        assertKeinFund("~\u00E4rzte/x");
        assertKeinFund("~er ika/x");
    }

    @Test
    void nachtragHomeBenutzerNamenslaengeHoechstens64() {
        String name64 = "a".repeat(64);
        String name65 = "a".repeat(65);
        assertFund("~" + name64 + "/x", "~" + name64 + "/x", "pfad-home", 0.80);
        assertKeinFund("~" + name65 + "/x");
        assertKeinFund("~" + "a".repeat(1000) + "/x");
    }

    @Test
    void nachtragHomeBenutzerIstImStandardScannerEinFundOhneKlartext() {
        assertEquals("Datei [[PFAD_1]] laden", pseudonymisiert("Datei ~erika/akte.pdf laden"));
        List<Finding> s = scan("~erika.mustermann/Dokumente/a.txt");
        assertEquals(1, s.size());
        assertEquals("pfad-home", s.get(0).source());
        assertEquals(0.80, s.get(0).confidence());
    }

    @Test
    void nachtragHomeFormMonotonUndKopfvarianten() {
        String eigener = "~/x";
        String fremder = "~erika/x";
        assertEquals(0.90, PathForm.HOME.confidence(PathForm.HOME.headEnd(eigener, 0), 1));
        assertEquals(0.80, PathForm.HOME.confidence(PathForm.HOME.headEnd(fremder, 0), 1));
        for (int segmente = 0; segmente <= 6; segmente++) {
            assertEquals(0.90, PathForm.HOME.confidence(2, segmente));
            assertEquals(0.80, PathForm.HOME.confidence(7, segmente));
        }
        assertEquals(2, PathForm.HOME.headEnd(eigener, 0));
        assertEquals(7, PathForm.HOME.headEnd(fremder, 0));
        assertEquals(-1, PathForm.HOME.headEnd("~erika", 0));
        assertEquals(-1, PathForm.HOME.headEnd("~", 0));
        assertEquals(-1, PathForm.HOME.headEnd("", 0));
    }

    @Test
    void nachtragHomeBenutzerLaufzeit() {
        // Der Name wird je Kopf hoechstens 64 Zeichen gelesen; Abschnitte verschiedener Koepfe ueberlappen nicht
        laufzeit(("~" + "a".repeat(70) + " ").repeat(14_000), 0);
        laufzeit("~" + "a".repeat(1_000_000) + "/x", 0);
        laufzeit("~a ".repeat(300_000), 0);
        laufzeit("~a/ ".repeat(250_000), 0);
        laufzeit("~erika/a ".repeat(110_000), 110_000);
        laufzeit("~a".repeat(500_000), 0);
        laufzeit("(~a".repeat(330_000), 0);
    }
}
