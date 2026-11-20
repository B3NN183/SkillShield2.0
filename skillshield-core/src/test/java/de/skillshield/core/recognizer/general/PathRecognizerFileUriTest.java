package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests der Form FILE_URI (T-16, Plan 2.4.2 Zeile FILE_URI, OF-8, AK-24 (e)): {@code file:///C:/Tools/a.exe} ist ein
 * Fund ueber den ganzen Ausdruck. Benennung wie in den Tests der anderen Formen: {@code plan...} (so im Plan
 * festgelegt), {@code eigeneEntscheidung...} (Auslegung, wo der Plan nichts sagt oder abweicht),
 * {@code bekannteFolge...} und {@code bekannteGrenze...} (Luecke oder Fehltreffer, mit Konfidenz). Namen und Rechner
 * sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes, Backslashes ueber {@code bs(...)}.
 */
class PathRecognizerFileUriTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private static final String QUELLE = "pfad-file-uri";

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> r = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : r) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar");
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar");
            vorigesEnde = f.end();
        }
        return r;
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static Finding assertFund(String text, String wert, String quelle, double konfidenz) {
        List<Finding> f = funde(text);
        assertEquals(List.of(wert), f.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Text: " + text);
        assertEquals(quelle, f.get(0).source(), "Quelle, Text: " + text);
        assertEquals(konfidenz, f.get(0).confidence(), "Konfidenz, Text: " + text);
        return f.get(0);
    }

    /** Der ganze Text ist ein Fund der Form FILE_URI mit Konfidenz 0.95. */
    private static void assertGanzerFund(String text) {
        Finding f = assertFund(text, text, QUELLE, 0.95);
        assertEquals(0, f.start());
        assertEquals(text.length(), f.end());
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    // ================================================================== Plan: Zeile FILE_URI

    @Test
    void planPositivFileUriIstDerGanzeAusdruckEinschliesslichFileUndSchraegstrichen() {
        // Positivbeispiele der Plan-Zeile (OF-8): Fund = ganzer Wert, inklusive "file:///"
        assertGanzerFund("file:///C:/Tools/BeispielTool/tool.exe");
        assertGanzerFund("file://srv-beispiel/freigabe/x");
        assertGanzerFund("file:///opt/beispiel/x");
        Finding f = funde("file:///C:/Tools/BeispielTool/tool.exe").get(0);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("file:///C:/Tools/BeispielTool/tool.exe", f.originalValue());
    }

    @Test
    void planPositivImSatzBeginntDerFundAmFVonFile() {
        String text = "Starte file:///C:/Tools/BeispielTool/tool.exe jetzt";
        Finding f = assertFund(text, "file:///C:/Tools/BeispielTool/tool.exe", QUELLE, 0.95);
        assertEquals(7, f.start());
        assertEquals("Starte ", text.substring(0, f.start()));
        assertEquals(" jetzt", text.substring(f.end()));
    }

    @Test
    void planNegativFileUndFileMitSchraegstrichenOhneSegmentSindKeinFund() {
        assertKeinFund("file:");
        assertKeinFund("file:/");
        assertKeinFund("file://");
        assertKeinFund("file:///");
        assertKeinFund("file:////");
        assertKeinFund("FILE:///");
        assertKeinFund(bs("file:¦¦"));
        assertKeinFund(bs("file:///¦¦"));
        assertKeinFund("Datei file:/// offen");
        assertKeinFund("file: ///x"); // das Leerzeichen beendet den Lauf
        assertKeinFund("file:\n///x");
    }

    @Test
    void planSchreibungDesSchemasIstFrei() {
        assertGanzerFund("FILE:///C:/Tools/a.exe");
        assertGanzerFund("File://srv-beispiel/freigabe/x");
        assertGanzerFund("fIlE:///opt/beispiel/x");
        assertGanzerFund("FILE:/opt/beispiel/x");
    }

    @Test
    void planKonfidenzIstNullNeunFuenfQuelleIstPfadFileUri() {
        // Plan Zeile FILE_URI: 0.95 sicher (N-3); Quelle pfad-file-uri (TE-2)
        for (String text : List.of("file:///C:/a", "file:///C:/a/b", "file:///C:/a/b/c/d", "file://srv/x",
                "file://srv/x/y/z", "file:/x", "file:/x/y/z")) {
            Finding f = funde(text).get(0);
            assertEquals(0.95, f.confidence(), text);
            assertEquals(QUELLE, f.source(), text);
            assertTrue(f.source().matches("[a-z][a-z0-9-]{0,31}"), "Format der Quelle");
        }
        assertEquals(QUELLE, PathForm.FILE_URI.source());
    }

    @Test
    void planKonfidenzIstInDerSegmentzahlMonotonNichtFallend() {
        // Vertrag PathForm.confidence, Plan 7a (i)
        for (int kopf : new int[] {5, 6, 7, 8, 9, 100}) {
            double vorige = 0.0;
            for (int segmente = 1; segmente <= 3; segmente++) {
                double jetzt = PathForm.FILE_URI.confidence(kopf, segmente);
                assertTrue(jetzt >= vorige, "faellt bei " + segmente + " Segmenten");
                assertEquals(0.95, jetzt);
                vorige = jetzt;
            }
        }
    }

    @Test
    void planFileUriLiefertFuerDenGanzenAusdruckEinenFundKeinenTeilfund() {
        // AK-24 (e) auf Erkennerebene: weder "file:" plus Pfad noch /C: oder C: als Teilfunde
        List<Finding> f = funde("file:///C:/Tools/BeispielTool/tool.exe");
        assertEquals(1, f.size());
        assertEquals(0, f.get(0).start());
        assertEquals("file:///C:/Tools/BeispielTool/tool.exe".length(), f.get(0).end());
    }

    // ================================================================== Kopf: Trennerarten und Schreibweisen

    @Test
    void eigeneEntscheidungVieleSchreibweisenDesKopfsSindGanzerFund() {
        assertGanzerFund("file:///C:/Tools/a.exe");
        assertGanzerFund("file:///home/erika/a.txt");
        assertGanzerFund("file://srv-beispiel/freigabe/a.txt");
        assertGanzerFund("file:/C:/x");
        assertGanzerFund("file:/home/erika/a.txt");
        assertGanzerFund("file://localhost/C:/x");
        assertGanzerFund("file:///C:/Mein%20Ordner/Akte%20M%C3%BCller.pdf");
        assertGanzerFund(bs("file:¦¦srv¦freigabe¦x"));
        assertGanzerFund(bs("file:///¦¦srv¦x"));
        assertGanzerFund(bs("file:¦srv¦freigabe¦x"));
        assertGanzerFund("file:////srv/freigabe/x");
        assertGanzerFund(bs("file:/¦a/b¦c"));
    }

    @Test
    void eigeneEntscheidungKopfOhneTrennerGiltBeiPfadkopfDahinter() {
        // Kopf ohne Trenner, wenn direkt dahinter ein Kopf von LAUFWERK, RELATIV, HOME oder UMGEBUNG beginnt:
        // der ganze Ausdruck ab "file:" ist der Fund, "file:" bleibt nicht sichtbar.
        assertGanzerFund(bs("file:C:¦x"));
        assertGanzerFund("file:C:/x");
        assertGanzerFund("file:c:/Tools/a.exe");
        assertGanzerFund("file:./a");
        assertGanzerFund(bs("file:..¦x"));
        assertGanzerFund("file:../konf/app.yml");
        assertGanzerFund("file:~/x");
        assertGanzerFund("file:~erika/akte.pdf");
        assertGanzerFund("file:%USERPROFILE%/x");
        assertGanzerFund(bs("file:%APPDATA%¦BeispielTool¦konfig.ini"));
        assertGanzerFund("file:$HOME/x");
        assertGanzerFund("file:${HOME}/x");
        assertGanzerFund(bs("file:!USERPROFILE!¦x"));
        assertGanzerFund(bs("file:%~dp0..¦erika¦a"));
        assertGanzerFund("FILE:C:/x");
    }

    @Test
    void eigeneEntscheidungOhneTrennerUndOhnePfadkopfIstKeinFund() {
        assertKeinFund("file:foo");
        assertKeinFund("file:foo/bar");
        assertKeinFund(bs("file:foo¦bar"));
        assertKeinFund("file:abc");
        assertKeinFund("file:C:");
        assertKeinFund(bs("file:C:¦"));
        assertKeinFund("file:C:/");
        assertKeinFund("file:./");
        assertKeinFund("file:..");
        assertKeinFund("file:%APPDATA%");
        assertKeinFund(bs("file:%APPDATA%¦"));
        assertKeinFund("file:$HOME");
        assertKeinFund("file:~/");
    }

    @Test
    void eigeneEntscheidungEinSegmentGenuegtAbweichungVomPlanMitZweiSegmenten() {
        // Plan-Zeile: mindestens 2 Segmente; Negativbeispiele file:///C:/ und profile:///x. Hier genuegt ein Segment,
        // denn bis T-16 war file:/x ein Host-Fund (ein Segment, 0.80) und kein Fund darf verschwinden; file:///C:/ und
        // file:///etc waren Laufwerksfunde ab e:/ mit 0.95. Jetzt: der ganze Ausdruck, 0.95, kein "fil" sichtbar.
        assertGanzerFund("file:/x");
        assertGanzerFund("file:///C:/");
        assertGanzerFund("file:///etc");
        assertGanzerFund("file://srv-beispiel");
        assertGanzerFund("file:///x");
        assertGanzerFund(bs("file:¦¦srv"));
        // profile:///x: kein Plan-Negativbeispiel mehr, weil FILE_URI keine Startgrenze hat (siehe unten)
        assertFund("profile:///x", "file:///x", QUELLE, 0.95);
    }

    // ================================================================== Startgrenze

    @Test
    void eigeneEntscheidungKeineStartgrenzeFundBeginntAmFVonFile() {
        Finding f = assertFund("siehe:file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertEquals(6, f.start());
        assertFund("x=file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertFund("(file:///C:/x/y)", "file:///C:/x/y", QUELLE, 0.95);
        assertFund("Pfad:file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertFund("-file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertFund("1file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertFund("Datei,file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertFund(">file:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
    }

    @Test
    void eigeneEntscheidungWortAufFileDavorBleibtSichtbarDerFundBeginntAmF() {
        // Ist bis T-16: profile:///C:/x war ein Laufwerksfund ab e:/ ("profil" sichtbar). Jetzt beginnt der Fund am f
        // von file: ("pro" bleibt sichtbar). Im Standard-Scanner ist profile:// zusaetzlich ein URL-Schema (siehe
        // TextScannerAbgrenzungTest): der URL-Fund umfasst den ganzen Ausdruck.
        Finding f = assertFund("profile:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
        assertEquals(3, f.start());
        assertFund("Xfile:///C:/x/y", "file:///C:/x/y", QUELLE, 0.95);
    }

    // ================================================================== Verhaeltnis zu HOST_PFAD

    @Test
    void eigeneEntscheidungFileSchraegstrichPfadIstFileUriNichtMehrHost() {
        // Bis T-16 ein Host-Fund (pfad-host, 0.90 ab zwei Segmenten, 0.80 bei einem); jetzt FILE_URI mit 0.95. Die
        // Abdeckung ist dieselbe (ganzer Ausdruck ab f).
        assertFund("file:/home/erika/a.txt", "file:/home/erika/a.txt", QUELLE, 0.95);
        assertFund("file:/home/erika", "file:/home/erika", QUELLE, 0.95);
        assertFund("file:/etc", "file:/etc", QUELLE, 0.95);
        assertFund("FILE:/x/y", "FILE:/x/y", QUELLE, 0.95);
        assertEquals(-1, PathForm.HOST_PFAD.headEnd("file:/home/erika/a.txt", 0));
        assertEquals(-1, PathForm.HOST_PFAD.headEnd("File:/x", 0));
        assertEquals(-1, PathForm.HOST_PFAD.headEnd("FILE:/x", 0));
        assertEquals(6, PathForm.FILE_URI.headEnd("file:/home/erika/a.txt", 0));
    }

    @Test
    void eigeneEntscheidungLaeufeDieFileNurEnthaltenBleibenHostFunde() {
        // Der Lauf besteht nicht genau aus "file": Host-Fund ab dem Laufanfang wie bisher (Konfidenz 0.90/0.80). Der
        // FILE_URI-Fund ab f liegt darin; die Konfidenz bleibt die des frueheren Fundes (Plan 7a (iii)).
        assertFund("xfile:/a/b", "xfile:/a/b", "pfad-host", 0.90);
        assertFund("profile:/x/y", "profile:/x/y", "pfad-host", 0.90);
        assertFund(".file:/a/b", ".file:/a/b", "pfad-host", 0.90);
        assertFund("my-file:/a/b", "my-file:/a/b", "pfad-host", 0.90);
        assertFund("profile:/x", "profile:/x", "pfad-host", 0.80);
        assertEquals(7, PathForm.HOST_PFAD.headEnd("xfile:/a/b", 0));
        assertEquals(9, PathForm.HOST_PFAD.headEnd("profile:/x/y", 0));
    }

    @Test
    void eigeneEntscheidungHostMitFileImSegmentBleibtUnveraendert() {
        assertFund("host.example:/export/file:/x", "host.example:/export/file:/x", "pfad-host", 0.90);
        assertFund("Pfad:/etc/hosts", "Pfad:/etc/hosts", "pfad-host", 0.90);
    }

    @Test
    void eigeneEntscheidungHostLaufUeberVierundsechzigZeichenMitFileHatDenGleichenTeilfundWieBisT15() {
        // 100 Zeichen Host-Lauf vor ":/": der Host-Fund beginnt 64 Zeichen vor dem Doppelpunkt (Teilfund mitten im
        // Lauf, bekannte Grenze von HOST_PFAD, unveraendert); file: liegt darin und aendert den Anfang nicht.
        String text = "a".repeat(96) + "file:/x/y";
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(100 - 64, f.get(0).start());
        assertEquals(text.length(), f.get(0).end());
        assertEquals("pfad-host", f.get(0).source());
    }

    // ================================================================== Verhaeltnis zu LAUFWERK, UNC, UNIX

    @Test
    void eigeneEntscheidungLaufwerksKandidatImFileUriFundAendertDieKonfidenzNicht() {
        // Der Laufwerkskopf e:/ (ab dem e von file) und C:/ liegen im FILE_URI-Fund: 7a (iii), Konfidenz 0.95 bleibt.
        Finding f = assertFund("file:///C:/Tools/a.exe", "file:///C:/Tools/a.exe", QUELLE, 0.95);
        assertEquals(0, f.start());
        // Nur der Laufwerks-Durchlauf allein sieht weiterhin den Teilfund ab e (der Durchlauf je Form bleibt)
        List<Finding> nurLaufwerk = R.detectWithoutQuotes("file:///C:/Tools/a.exe",
                new PathForm[] {PathForm.LAUFWERK});
        assertEquals("e:///C:/Tools/a.exe", nurLaufwerk.get(0).originalValue());
        assertEquals(0.95, nurLaufwerk.get(0).confidence());
    }

    @Test
    void eigeneEntscheidungUncUndUnixKandidatenImFileUriFundAendernKonfidenzUndGrenzenNicht() {
        assertFund(bs("file:///¦¦srv¦freigabe¦x"), bs("file:///¦¦srv¦freigabe¦x"), QUELLE, 0.95);
        assertFund(bs("file:¦¦srv"), bs("file:¦¦srv"), QUELLE, 0.95); // UNC-Kandidat \\srv allein haette 0.60
        assertFund("file:///opt/a/b/c", "file:///opt/a/b/c", QUELLE, 0.95); // UNIX-Kandidat /opt/a/b/c: 0.90
        assertFund("file://srv-beispiel/freigabe", "file://srv-beispiel/freigabe", QUELLE, 0.95);
    }

    @Test
    void eigeneEntscheidungUmgebungImFileUriFundHebtDieKonfidenzAufNullNeunFuenf() {
        // UMGEBUNG allein hat 0.80, RELATIV 0.80: der Fund ab file: ist groesser (7a (i): der Kopf beginnt frueher)
        assertFund("file:%APPDATA%/x", "file:%APPDATA%/x", QUELLE, 0.95);
        assertFund("file:./a/b", "file:./a/b", QUELLE, 0.95);
        List<Finding> nurUmgebung = R.detectWithoutQuotes("file:%APPDATA%/x", new PathForm[] {PathForm.UMGEBUNG});
        assertEquals("%APPDATA%/x", nurUmgebung.get(0).originalValue());
        assertEquals(0.80, nurUmgebung.get(0).confidence());
    }

    // ================================================================== Ende, Kuerzung, Query, Fragment

    @Test
    void eigeneEntscheidungQueryFragmentUndKodierteZeichenGehoerenZumFund() {
        assertGanzerFund("file:///C:/a.html#abschnitt");
        assertGanzerFund("file:///C:/a.html?x=1");
        assertGanzerFund("file:///C:/a.html?x=1&y=2#t");
        assertGanzerFund("file:///C:/Mein%20Ordner/Akte%20M%C3%BCller.pdf");
        assertGanzerFund("file:///C:/Mein%20Ordner/");
        assertGanzerFund("file://nutzer:geheim@srv.example/x");
        assertGanzerFund("file://192.0.2.10/freigabe/a");
        assertGanzerFund("file://erika.beispiel@example.invalid/x");
    }

    @Test
    void eigeneEntscheidungEndeUndKuerzungWieBeiAllenFormen() {
        assertFund("Siehe file:///C:/a.txt.", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("Siehe file:///C:/a.txt, danach", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("Siehe file:///C:/a.txt?", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("(file:///C:/a.txt)", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("file:///C:/Beispiel(x86)/a.txt", "file:///C:/Beispiel(x86)/a.txt", QUELLE, 0.95);
        assertFund("file:///C:/a/", "file:///C:/a/", QUELLE, 0.95);
        assertFund("file:///C:/a.txt<x", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("file:///C:/a.txt|x", "file:///C:/a.txt", QUELLE, 0.95);
        assertFund("file:///C:/a.txt`x", "file:///C:/a.txt", QUELLE, 0.95);
    }

    @Test
    void eigeneEntscheidungLeerzeichenBeendetDenFundOhneAnfuehrungszeichen() {
        // D-5 wie bei allen Formen: kodierte Leerzeichen (%20) gehoeren zum Fund, echte beenden ihn
        assertFund("file:///C:/Mein Ordner/a.txt", "file:///C:/Mein", QUELLE, 0.95);
        assertFund("file:///C:/Mein%20Ordner/a.txt", "file:///C:/Mein%20Ordner/a.txt", QUELLE, 0.95);
    }

    // ================================================================== Zitat

    @Test
    void eigeneEntscheidungImZitatGehoertDerGanzeInhaltZumFundAuchMitLeerzeichen() {
        String inhalt = "file:///C:/Mein Ordner/a.txt";
        for (String[] paar : new String[][] {{"\"", "\""}, {"'", "'"}, {"`", "`"}, {"\u201E", "\u201C"},
                {"\u00BB", "\u00AB"}, {"\u2018", "\u2019"}}) {
            String text = "Pfad " + paar[0] + inhalt + paar[1] + " ende";
            Finding f = assertFund(text, inhalt, QUELLE, 0.95);
            assertEquals(6, f.start());
        }
        assertFund("\"file:///C:/Mein Ordner/a.txt.\"", "file:///C:/Mein Ordner/a.txt.", QUELLE, 0.95);
        assertFund(bs("\"file:///¦¦srv¦Mein Ordner¦a.txt\""), bs("file:///¦¦srv¦Mein Ordner¦a.txt"), QUELLE, 0.95);
        assertFund("\"file:C:/Mein Ordner/a.txt\"", "file:C:/Mein Ordner/a.txt", QUELLE, 0.95);
        assertFund("x=\"file:/home/Mein Ordner/a.txt\"", "file:/home/Mein Ordner/a.txt", QUELLE, 0.95);
    }

    @Test
    void eigeneEntscheidungZitatOhneGenuegendSegmenteOderUngeschlossenFaelltZurueck() {
        assertKeinFund("\"file:///\"");
        assertKeinFund("\"file:\"");
        // ungeschlossen: Regel ohne Anfuehrungszeichen, Ende am ersten Leerzeichen
        assertFund("\"file:///C:/Mein Ordner/a.txt", "file:///C:/Mein", QUELLE, 0.95);
    }

    @Test
    void eigeneEntscheidungZitatKonfidenzUndQuelleSindDieselbenWieOhneZitat() {
        Finding ohne = funde("file:///C:/Tools/a.exe").get(0);
        Finding mit = funde("\"file:///C:/Tools/a.exe\"").get(0);
        assertEquals(ohne.confidence(), mit.confidence());
        assertEquals(ohne.source(), mit.source());
        assertEquals(ohne.originalValue(), mit.originalValue());
    }

    // ================================================================== mehrere Funde, Reihenfolge

    @Test
    void mehrereFileUriFundeImTextSindGetrenntUndGeordnet() {
        String text = "Erst file:///C:/a/b dann file://srv-beispiel/freigabe/x und FILE:/opt/x/y fertig.";
        assertEquals(List.of("file:///C:/a/b", "file://srv-beispiel/freigabe/x", "FILE:/opt/x/y"), werte(text));
        assertEquals(werte(text), werte(text));
    }

    @Test
    void eigeneEntscheidungZweiFileUriOhneLeerraumSindEinFund() {
        // Wie bei allen Pfaden: ein Lauf liefert hoechstens einen Fund (Plan: durch Komma verbundene Pfade)
        assertFund("file:///C:/a,file:///C:/b", "file:///C:/a,file:///C:/b", QUELLE, 0.95);
    }

    // ================================================================== Kopf direkt (PathForm)

    @Test
    void headEndLiefertDasKopfendeHinterDenSchraegstrichen() {
        assertEquals(8, PathForm.FILE_URI.headEnd("file:C:/x", 0)); // Kopf ohne Trenner: hinter dem Laufwerkskopf
        assertEquals(7, PathForm.FILE_URI.headEnd("file:./a", 0));
        assertEquals(7, PathForm.FILE_URI.headEnd("file:~/a", 0));
        assertEquals(6, PathForm.FILE_URI.headEnd("file:/x", 0));
        assertEquals(7, PathForm.FILE_URI.headEnd("file://x", 0));
        assertEquals(8, PathForm.FILE_URI.headEnd("file:///x", 0));
        assertEquals(10, PathForm.FILE_URI.headEnd(bs("file:///¦¦x"), 0));
        assertEquals(8, PathForm.FILE_URI.headEnd("FILE:///", 0)); // Kopf ohne Segment: headEnd liefert das Ende
        assertEquals(9, PathForm.FILE_URI.headEnd("xx file:/x", 3));
        assertEquals(-1, PathForm.FILE_URI.headEnd("file:", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("file", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("fil:///", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("files:///x", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("file;///x", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("file:foo", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("fi\u00E4e:///x", 0)); // kein ASCII-Faltungstrick
        assertEquals(-1, PathForm.FILE_URI.headEnd("", 0));
        assertEquals(-1, PathForm.FILE_URI.headEnd("x", 0));
    }

    @Test
    void formEigenschaften() {
        assertEquals(1, PathForm.FILE_URI.minSegments());
        assertTrue(PathForm.FILE_URI.isStartBoundary("xfile:///a", 1));
        assertTrue(PathForm.FILE_URI.isStartBoundary("file:///a", 0));
        assertEquals(11, PathForm.values().length);
    }

    // ================================================================== Bekannte Grenzen

    @Test
    void bekannteGrenzeUnsichtbaresZeichenInFileVerhindertDenFileUriFund() {
        // U+200B in "file": kein Kopf. Es bleibt der Laufwerksfund ab dem e (bis T-16 genauso); "fi" + U+200B + "l"
        // bleibt sichtbar.
        String text = "fi\u200Ble:///C:/x/y";
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals("e:///C:/x/y", f.get(0).originalValue());
        assertEquals("pfad-laufwerk", f.get(0).source());
        // U+200B hinter den Schraegstrichen: der Kopf besteht, das Zeichen zaehlt als Segment (wie bei allen Formen)
        assertGanzerFund("file:/\u200B/x");
        assertGanzerFund("file:/\u200B");
        // U+200B direkt hinter dem Doppelpunkt: kein Kopf (weder Trenner noch Pfadkopf dahinter), kein Fund
        assertKeinFund("file:\u200B/x");
    }

    @Test
    void bekannteGrenzeVollbreiteDoppelpunktUndLeerzeichenAmKopfLassenNurDenLaufwerksteil() {
        // Vollbreiter Doppelpunkt U+FF1A, Leerzeichen vor ":" und URL-kodierter Doppelpunkt: kein FILE_URI-Kopf;
        // es bleibt der Laufwerkskandidat C:/x/y, "file" bleibt sichtbar.
        assertFund("file\uFF1A///C:/x/y", "C:/x/y", "pfad-laufwerk", 0.95);
        assertFund("file :///C:/x/y", "C:/x/y", "pfad-laufwerk", 0.95);
        assertFund("file%3A///C:/x/y", "C:/x/y", "pfad-laufwerk", 0.95);
        assertFund("file\u200B:///C:/x/y", "C:/x/y", "pfad-laufwerk", 0.95);
    }

    @Test
    void bekannteGrenzeSehrLangerHostWirdVollstaendigGeschuetzt() {
        // Im Unterschied zu HOST_PFAD (Host-Lauf hoechstens 64 Zeichen) hat der Host hinter file:// keine Obergrenze
        String host = "h".repeat(100_000);
        String text = "file://" + host + "/freigabe/x";
        List<Finding> f = assertTimeoutPreemptively(LIMIT, () -> funde(text));
        assertEquals(1, f.size());
        assertEquals(0, f.get(0).start());
        assertEquals(text.length(), f.get(0).end());
        assertEquals(1, SCANNER.scan(text).size());
    }

    @Test
    void bekannteFolgeFileMitPfadAehnlichemWortDahinterIstFundMitNullNeunFuenf() {
        // Fehltreffer (zu viel ersetzt, kein Leck): file:/ oder file:// im Fliesstext vor einem Wort
        assertFund("file:/text", "file:/text", QUELLE, 0.95);
        assertFund("Das file://Ordner ist leer", "file://Ordner", QUELLE, 0.95);
    }

    @Test
    void bekannteGrenzeFileOhneTrennerUndOhnePfadkopfDahinterIstKeinFileUriFund() {
        // Plan Nachtrag T-16 Punkt 6 (wie bis T-15): "file:" ohne Trenner und ohne Pfadkopf (Laufwerk, relativ, Home,
        // Umgebung) dahinter ist kein Kopf; der Pfadanteil dahinter bleibt im Klartext. Ist-Ergebnis des Erkenners
        // und des Standard-Scanners (alle Erkenner) fuer die drei Faelle, jeweils exakt.
        for (String text : List.of("file:erika/akte", "file:srv.example/freigabe/akte")) {
            assertEquals(List.of(), R.detect(text), "Erkenner, Text: " + text);
            assertEquals(List.of(), SCANNER.scan(text), "Standard-Scanner, Text: " + text);
        }
        String mitZugangsdaten = "file:nutzer:geheim@srv.example/x";
        assertEquals(List.of(), R.detect(mitZugangsdaten), "Erkenner");
        List<Finding> scan = SCANNER.scan(mitZugangsdaten);
        assertEquals(1, scan.size(), "Standard-Scanner: nur ein E-Mail-Fund");
        assertEquals(FindingType.EMAIL, scan.get(0).type());
        assertEquals("geheim@srv.example", scan.get(0).originalValue());
        assertEquals(12, scan.get(0).start());
        assertEquals(30, scan.get(0).end());
    }

    @Test
    void bekannteGrenzeFrueherFileUriKopfImLaufNimmtDemSpaeterenFileKandidatenDieSchliessendeKlammer() {
        // Plan Nachtrag T-16 Punkt 6 (gleiche Ursache wie T-15 Punkt 6, mehrere Koepfe derselben Form in einem Lauf):
        // Der Kopf FILE:\\ ab 0 beginnt weiter vorn; fuer das spaetere file:/b(c) entsteht kein eigener Kandidat. Die
        // Klammerzaehlung ab 0 (zwei schliessende, eine oeffnende Klammer) nimmt die letzte ")" aus dem Fund. Bis T-15
        // war sie abgedeckt (Host-Fund ab file:/ mit ausgeglichenen Klammern). Betroffen ist nie ein Pfadbestandteil.
        String text = bs("FILE:¦¦a)file:/b(c)");
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(0, f.get(0).start());
        assertEquals(18, f.get(0).end());
        assertEquals(bs("FILE:¦¦a)file:/b(c"), f.get(0).originalValue());
        assertEquals(QUELLE, f.get(0).source());
        assertEquals(0.95, f.get(0).confidence());
        assertEquals(')', text.charAt(18));
        List<Finding> scan = SCANNER.scan(text);
        assertEquals(1, scan.size());
        assertEquals(0, scan.get(0).start());
        assertEquals(18, scan.get(0).end());
        assertEquals(bs("FILE:¦¦a)file:/b(c"), scan.get(0).originalValue());
        assertEquals(FindingType.PFAD, scan.get(0).type());
        assertEquals(QUELLE, scan.get(0).source());
        // ohne den frueheren Kopf bleibt die Klammer im Fund (ausgeglichen)
        assertFund("file:/b(c)", "file:/b(c)", QUELLE, 0.95);
    }

    // ================================================================== Grenzlast (AK-49), je < 2 s

    private static void laufzeit(String text, int erwartet) {
        laufzeit(text, erwartet, erwartet);
    }

    /** Wie oben, mit eigener Fundzahl fuer den Standard-Scanner (der URL-Erkenner greift auf {@code Xfile:///}). */
    private static void laufzeit(String text, int erwartet, int erwartetScanner) {
        assertEquals(erwartet, (int) assertTimeoutPreemptively(LIMIT, () -> R.detect(text).size()), "Erkenner");
        assertEquals(erwartetScanner, (int) assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text).size()),
                "Scanner");
    }

    @Test
    void ak49EineMillionFMalHintereinander() {
        laufzeit("f".repeat(1_000_000), 0);
    }

    @Test
    void ak49FileDoppelpunktMalEineMillionZeichen() {
        laufzeit("file:".repeat(200_000), 0);
        laufzeit("file: ".repeat(166_667), 0);
    }

    @Test
    void ak49FileDoppelpunktSchraegstrichMalEineMillionZeichen() {
        // ein Lauf, ein Fund ueber alles (file:/file:/... : "file:" ist jeweils ein Segment)
        laufzeit("file:/".repeat(166_667), 1);
        laufzeit("file:/ ".repeat(142_858), 0); // kein Segment
        laufzeit("file:/x ".repeat(125_000), 125_000);
    }

    @Test
    void ak49FileZweiSchraegstricheMalEineMillionZeichenOhneSegment() {
        laufzeit("file://".repeat(142_858), 1);
        laufzeit("file:// ".repeat(125_000), 0);
        laufzeit("file:///".repeat(125_000), 1);
    }

    @Test
    void ak49FileGefolgtVonEinerMillionTrennernOhneSegment() {
        laufzeit("file:" + "/".repeat(1_000_000), 0);
        laufzeit("file:" + bs("¦").repeat(1_000_000), 0);
        laufzeit("file:" + "/".repeat(1_000_000) + "x", 1);
    }

    @Test
    void ak49ZitatPfadMitVielenFileUriZitaten() {
        laufzeit("\"file:///\" ".repeat(90_000), 0);
        laufzeit("\"file:///a\" ".repeat(80_000), 80_000);
        laufzeit("\"file:///a b ".repeat(80_000), 80_000);
        laufzeit("\"".repeat(1_000_000), 0);
        laufzeit("\"file:///C:/ a b c ".repeat(50_000), 50_000);
    }

    @Test
    void ak49KopfOhneTrennerMitDichtenPfadkoepfenDahinter() {
        laufzeit(bs("file:C:¦").repeat(125_000), 1);
        laufzeit("file:./".repeat(142_857), 1);
        laufzeit("file:~/".repeat(142_857), 1);
        laufzeit("file:%a%/".repeat(111_112), 1);
        laufzeit("file:%a%".repeat(125_000), 0);
        laufzeit("file:$".repeat(166_667), 0);
        // Batch-Koepfe mit Rest vor dem Trenner: nur die letzten Koepfe sind Koepfe, die Zaehlung bleibt konstant
        laufzeit("file:%~dp0".repeat(100_000) + bs("¦)"), 0);
        laufzeit(bs("file:%~dp0¦)").repeat(80_000), 1);
    }

    @Test
    void ak49KoepfeDichtImBatchRestMitTrennerLaufAlsInneremKopf() {
        // Der aeussere Kopf ist file: plus Batch-Kopf %~dp0file:/ (Ende 16), der innere beginnt im Batch-Rest bei 10,
        // ist file: plus der Trenner-Lauf /// und endet bei 18 (verschiedene Kopfenden, gleicher erster Trenner).
        String kopf = "file:%~dp0file:///.";
        assertEquals(16, PathForm.FILE_URI.headEnd(kopf, 0));
        assertEquals(18, PathForm.FILE_URI.headEnd(kopf, 10));
        // dicht gepackt, ein Lauf, ein Fund ueber alles (rund 1 Mio. Zeichen)
        laufzeit(kopf.repeat(52_632), 1);
        // dichte Koepfe im Batch-Rest am Ende des Laufs, hinter dem Trenner-Lauf kein Segment: kein Fund; die
        // Zaehlung nach der Kuerzung laeuft nur fuer die Koepfe am Laufende (je Kopf ueber den ganzen Lauf). Der
        // Standard-Scanner findet dort einen URL-Fund ab dem Wort vor ://, der Erkenner keinen Pfad.
        laufzeit("file:%~dp0file:".repeat(66_667) + "///.", 0, 1);
        laufzeit("file:%~dp0file:".repeat(66_667) + bs("¦¦¦)"), 0);
        laufzeit("file:%~dp0file:".repeat(66_667) + "///", 0, 1);
        // mit einem Segment dahinter: ein Fund
        laufzeit("file:%~dp0file:".repeat(66_667) + "///x", 1);
        // Pfadkoepfe ohne Trenner (Laufwerk, relativ, Home, Umgebung) im Batch-Rest; mit Segmenten ein Fund, als
        // reine Kette ohne Trenner (nur der Trenner-Lauf am Ende, ohne Segment) keiner
        laufzeit("file:%~dp0file:C:/file:./file:~/file:%a%/".repeat(24_000) + "///.", 1);
        laufzeit("file:%~dp0file:%a%%b%%c%%d%".repeat(34_000) + "///.", 0);
    }

    @Test
    void ak49FileUriGemischtMitAnderenPfadformenEineMillionZeichen() {
        String baustein = bs("Siehe file:///C:/Tools/BeispielTool/tool.exe, dann C:¦Ordner¦x.txt und file:/opt/a/b. ");
        int n = 1_000_000 / baustein.length() + 1;
        laufzeit(baustein.repeat(n), 3 * n);
    }

    // ================================================================== kein Klartext in Meldungen

    @Test
    void ungueltigeEingabeNenntKeinenTextInDerMeldung() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> R.detect(null));
        assertEquals("text", e.getMessage());
    }
}
