package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/**
 * Tests der Form HOST_PFAD (Nachtrag 20.11.2026 zu T-13, Punkt 3): {@code host.example:/export/home}. Benennung wie in
 * {@code PathRecognizerUnixTest}: {@code plan...} (so im Nachtrag festgelegt), {@code eigeneEntscheidung...}
 * (Auslegung, wo der Nachtrag nichts sagt) und {@code bekannteFolge...} (Fehltreffer oder Luecke, mit Konfidenz).
 * Namen und Rechner sind synthetisch ({@code host.example}, {@code srv-beispiel}), Nicht-ASCII-Zeichen stehen nur als
 * Escapes.
 */
class PathRecognizerHostTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> r = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : r) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
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
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, scan(text));
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    // ================================================================== Positiv

    @Test
    void planHostMitPfadIstEinFundAbDemHostAnfang() {
        Finding f = assertFund("host.example:/export/home", "host.example:/export/home", "pfad-host", 0.90);
        assertEquals(0, f.start());
        assertFund("Datei host.example:/export/home laden", "host.example:/export/home", "pfad-host", 0.90);
        assertFund("srv-beispiel:/export/akten/x", "srv-beispiel:/export/akten/x", "pfad-host", 0.90);
        assertFund("srv01.praxis.example:/a/b/c/d", "srv01.praxis.example:/a/b/c/d", "pfad-host", 0.90);
    }

    @Test
    void planKonfidenzEinSegmentNullAchtAbZweiNullNeun() {
        assertFund("host:/a", "host:/a", "pfad-host", 0.80);
        assertFund("host.example:/export", "host.example:/export", "pfad-host", 0.80);
        assertFund("host:/a/b", "host:/a/b", "pfad-host", 0.90);
        assertFund("host:/a/b/c/d", "host:/a/b/c/d", "pfad-host", 0.90);
        assertFund("host:/a/", "host:/a/", "pfad-host", 0.80);
    }

    @Test
    void planPfadDoppelpunktPfadIstEinFundAbP() {
        Finding f = assertFund("Pfad:/etc/hosts", "Pfad:/etc/hosts", "pfad-host", 0.90);
        assertEquals(0, f.start());
        assertEquals("[[PFAD_1]]", pseudonymisiert("Pfad:/etc/hosts"));
    }

    @Test
    void planGleichheitszeichenGehoertNichtZumLauf() {
        Finding f = assertFund("x=host:/a/b", "host:/a/b", "pfad-host", 0.90);
        assertEquals(2, f.start());
        assertFund("--ziel=srv-beispiel:/export/x", "srv-beispiel:/export/x", "pfad-host", 0.90);
    }

    @Test
    void planKeineStartgrenzeKlammernAnfuehrungszeichenUndAtZeichen() {
        assertFund("(host:/a/b)", "host:/a/b", "pfad-host", 0.90);
        assertFund("[host:/a/b]", "host:/a/b", "pfad-host", 0.90);
        assertFund("\"host:/Meine Akte/x\"", "host:/Meine Akte/x", "pfad-host", 0.90);
        assertFund("'host:/a/b'", "host:/a/b", "pfad-host", 0.90);
        assertFund("nutzer@host.example:/pfad/datei", "host.example:/pfad/datei", "pfad-host", 0.90);
        assertFund("a,host:/x/y", "host:/x/y", "pfad-host", 0.90);
    }

    @Test
    void planLaufBeginntAmAnfangDesHostLaufsAuchMitZiffernPunktUndStrich() {
        assertFund("1a:/x", "1a:/x", "pfad-host", 0.80);
        assertFund("-srv:/x", "-srv:/x", "pfad-host", 0.80);
        assertFund(".host:/x", ".host:/x", "pfad-host", 0.80);
        assertFund("a_b-c.d9:/x", "a_b-c.d9:/x", "pfad-host", 0.80);
        assertFund("abc123:/x/y", "abc123:/x/y", "pfad-host", 0.90);
    }

    @Test
    void planAuchBackslashHinterDemSchraegstrichIstTrenner() {
        assertFund(bs("host:/a¦b"), bs("host:/a¦b"), "pfad-host", 0.90);
    }

    // ================================================================== Negativ

    @Test
    void planNegativOhneBuchstabeOhneSegmentOhneSchraegstrich() {
        assertKeinFund("Uhrzeit 12:/30");
        assertKeinFund("12:/30");
        assertKeinFund("1.2.3.4:/x");
        // IP-Adresse: kein Buchstabe im Lauf (die Adresse erkennt der IP-Erkenner)
        assertKeinFund("192.0.2.10:/export/home");
        assertKeinFund("-:/x");
        assertKeinFund("..:/x");
        assertKeinFund("C:/");
        assertKeinFund("host:/");
        assertKeinFund("host:/.");
        assertKeinFund("host:");
        assertKeinFund("host:x/y");
        assertEquals(List.of("/x/y"), werte("host: /x/y")); // Leerzeichen: nur der Unix-Pfad hinter dem Leerzeichen
        assertKeinFund(":/x/y");
        assertKeinFund("std::/x");
        // Backslash statt Schraegstrich: kein Host-Kopf, nur der Laufwerkskopf ab dem letzten Buchstaben (wie bisher)
        assertFund(bs("host:¦x"), bs("t:¦x"), "pfad-laufwerk", 0.95);
        assertKeinFund("");
        assertKeinFund(":");
        assertKeinFund("a");
    }

    @Test
    void eigeneEntscheidungDoppelterSchraegstrichHinterDemDoppelpunktIstKeinHostKopf() {
        // "://" gehoert dem URL-Erkenner (bzw. T-16 fuer file:); sonst waeren die URL-Grenzen im Standard-Scanner
        // veraendert. Der Laufwerkskopf s:/ deckt das Schema wie bisher ab dem letzten Buchstaben ab.
        assertFund("https://example.invalid/a/b", "s://example.invalid/a/b", "pfad-laufwerk", 0.95);
        assertFund("file:///C:/x", "e:///C:/x", "pfad-laufwerk", 0.95);
        assertFund("ftp://host.example/x", "p://host.example/x", "pfad-laufwerk", 0.95);
        assertKeinFund("host.example://");
        assertEquals(-1, PathForm.HOST_PFAD.headEnd("https://example.invalid/a/b", 0));
        assertEquals(-1, PathForm.HOST_PFAD.headEnd("host:///x", 0));
        assertEquals(6, PathForm.HOST_PFAD.headEnd("host:/ /x", 0));
    }

    // ================================================================== Verhaeltnis zu LAUFWERK

    @Test
    void planLaufwerkMitEinemBuchstabenBleibtLaufwerksfundMitNullNeunFuenf() {
        Finding f = assertFund("C:/Tools/a.exe", "C:/Tools/a.exe", "pfad-laufwerk", 0.95);
        assertEquals(0, f.start());
        assertFund("c:/x", "c:/x", "pfad-laufwerk", 0.95);
        assertFund("a:/b", "a:/b", "pfad-laufwerk", 0.95);
        assertFund("Datei C:/Tools/a.exe laden", "C:/Tools/a.exe", "pfad-laufwerk", 0.95);
        assertFund("x=C:/a", "C:/a", "pfad-laufwerk", 0.95);
        assertFund("(C:/Tools/a.exe)", "C:/Tools/a.exe", "pfad-laufwerk", 0.95);
        assertKeinFund("C:/");
    }

    @Test
    void planLaufwerkMitMehrBuchstabenWirdHostFundMitEigenerKonfidenz() {
        // Der Host-Fund beginnt frueher als der Laufwerkskopf ("e:/" in "host.example:/..."): ein Fund ab dem
        // Host-Anfang; die Konfidenz ist die niedrigere der Beteiligten (Plan 7a (ii)): min(0.90, 0.95) = 0.90.
        assertFund("host.example:/export/home", "host.example:/export/home", "pfad-host", 0.90);
        // Bei einem Segment: min(0.80, 0.95) = 0.80 (vorher 0.95 fuer "e:/export", nur ab dem letzten Buchstaben)
        assertFund("host.example:/export", "host.example:/export", "pfad-host", 0.80);
        assertFund("ab:/c", "ab:/c", "pfad-host", 0.80);
    }

    // ================================================================== Standard-Scanner: E-Mail und URLs

    @Test
    void standardScannerNutzerMitHostUndPfadIstEinZusammengelegterFundOhneKlartext() {
        String text = "scp nutzer@host.example:/pfad/datei";
        List<Finding> s = scan(text);
        assertEquals(1, s.size());
        assertEquals("nutzer@host.example:/pfad/datei", s.get(0).originalValue());
        assertEquals("zusammengelegt", s.get(0).source());
        assertEquals("scp [[EMAIL_1]]", pseudonymisiert(text));
        // der Erkenner liefert den Host-Fund ab "host"
        assertEquals(List.of("host.example:/pfad/datei"), werte("nutzer@host.example:/pfad/datei"));
    }

    @Test
    void standardScannerHostMitPfadIstEinPfadFundOhneKlartext() {
        List<Finding> s = scan("Ziel host.example:/export/home ok");
        assertEquals(1, s.size());
        assertEquals("pfad-host", s.get(0).source());
        assertEquals("host.example:/export/home", s.get(0).originalValue());
        assertEquals("Ziel [[PFAD_1]] ok", pseudonymisiert("Ziel host.example:/export/home ok"));
    }

    private static void assertGenauEinUrlFund(String url) {
        for (String text : List.of(url, "Siehe " + url + " dort", "Siehe " + url + ".", "(" + url + ")",
                "x " + url + ", y")) {
            List<Finding> s = scan(text);
            assertEquals(1, s.size(), "Text: " + text);
            Finding f = s.get(0);
            assertEquals(FindingType.URL, f.type(), "Typ, Text: " + text);
            assertEquals("url", f.source(), "Quelle, Text: " + text);
            assertEquals(0.95, f.confidence(), "Konfidenz, Text: " + text);
            assertEquals(url, f.originalValue(), "Grenzen, Text: " + text);
        }
    }

    @Test
    void standardScannerJedeUrlBleibtGenauEinUnveraenderterUrlFund() {
        assertGenauEinUrlFund("https://example.invalid/a/b");
        assertGenauEinUrlFund("https://example.invalid:8443/a/b");
        assertGenauEinUrlFund("https://example.invalid");
        assertGenauEinUrlFund("http://example.invalid/a");
        assertGenauEinUrlFund("ftp://nutzer:geheim@host/p");
        assertGenauEinUrlFund("ftp://nutzer:geheim@host.example:21/p/q");
        assertGenauEinUrlFund("jdbc:postgresql://db.example:5432/kv?user=nutzer&password=geheim");
        assertGenauEinUrlFund("svn+ssh://host.example/x/y");
        assertGenauEinUrlFund("https://192.0.2.10/a/b");
        assertGenauEinUrlFund("https://example.invalid/a/b?f=1&g=2#t");
        assertGenauEinUrlFund("URL:https://example.invalid/a/b");
        assertGenauEinUrlFund("a.b.c-d://example.invalid/a/b");
    }

    @Test
    void standardScannerFileUriBleibtBisT16UnveraendertEinLaufwerksFundAbE() {
        // Ist-Zustand bis T-16 (unveraendert gegenueber Durchlauf 1): "://" ist kein Host-Kopf, der Laufwerkskopf
        // "e:/" von "file:" beginnt den Fund, "fil" bleibt sichtbar.
        List<Finding> s = scan("file:///C:/x");
        assertEquals(1, s.size());
        assertEquals(FindingType.PFAD, s.get(0).type());
        assertEquals("pfad-laufwerk", s.get(0).source());
        assertEquals("e:///C:/x", s.get(0).originalValue());
        assertEquals(0.95, s.get(0).confidence());
    }

    // ================================================================== Bekannte Folgen (Fehltreffer), mit Konfidenz

    @Test
    void bekannteFolgeHinweisMitDoppelpunktUndSchraegstrichIstFundMitNullAcht() {
        assertFund("Hinweis:/n", "Hinweis:/n", "pfad-host", 0.80);
        assertFund("Verhaeltnis:/x", "Verhaeltnis:/x", "pfad-host", 0.80);
        assertFund("Montag:/Dienstag", "Montag:/Dienstag", "pfad-host", 0.80);
        assertFund("Ja:/Nein", "Ja:/Nein", "pfad-host", 0.80);
        assertFund("http:/x", "http:/x", "pfad-host", 0.80); // Tippfehler-URL: ein Segment
        assertFund("http:/example.invalid/a", "http:/example.invalid/a", "pfad-host", 0.90);
    }

    @Test
    void bekannteFolgeUmlautImHostNameBeendetDenLaufTeilfund() {
        // Der Lauf besteht nur aus ASCII-Zeichen; ein Umlaut beginnt einen neuen Lauf: "Verh\u00E4ltnis" wird
        // ab "ltnis" geschuetzt, "Verh\u00E4" bleibt sichtbar (Teilfund, kein Totalverlust), Konfidenz 0.80
        assertFund("Verh\u00E4ltnis:/x", "ltnis:/x", "pfad-host", 0.80);
        assertFund("Gr\u00F6\u00DFe:/a/b", "e:/a/b", "pfad-laufwerk", 0.95);
    }

    @Test
    void bekannteFolgeZeitangabenUndVerhaeltnisseOhneBuchstabenSindKeinFund() {
        assertKeinFund("Termin 12:/30");
        assertKeinFund("Stand 10:30:/x");
        assertKeinFund("Faktor 1:/2");
        assertKeinFund("std::/x");
        assertKeinFund("A::/x");
    }

    // ================================================================== Laenge des Host-Laufs: 64 Zeichen

    @Test
    void laufMitGenau64ZeichenIstVollerFund() {
        String host = "h" + "a".repeat(63);
        assertEquals(64, host.length());
        Finding f = assertFund(host + ":/x", host + ":/x", "pfad-host", 0.80);
        assertEquals(0, f.start());
        assertFund("Datei " + host + ":/x/y ok", host + ":/x/y", "pfad-host", 0.90);
    }

    @Test
    void laengererLaufBeginntGenau64ZeichenVorDemDoppelpunktTeilfund() {
        String lang = "b" + "a".repeat(64);
        Finding f = assertFund(lang + ":/x", "a".repeat(64) + ":/x", "pfad-host", 0.80);
        assertEquals(1, f.start());
        String sehrLang = "x".repeat(100) + ":/x/y";
        Finding g = assertFund(sehrLang, "x".repeat(64) + ":/x/y", "pfad-host", 0.90);
        assertEquals(36, g.start());
    }

    @Test
    void laengererLaufOhneBuchstabeInDenLetzten64ZeichenIstKeinFund() {
        // der Buchstabe liegt vor dem 64-Zeichen-Fenster: kein Fund (hoechstens die letzten 64 Zeichen zaehlen)
        assertKeinFund("a" + "1".repeat(100) + ":/x/y");
        assertKeinFund("1".repeat(100) + ":/x/y");
        // genau 64 Ziffern mit einem Buchstaben davor (Lauf laenger als 64): Fenster ohne Buchstabe
        assertKeinFund("a" + "1".repeat(64) + ":/x/y");
        // Buchstabe im Fenster: Fund
        assertFund("1".repeat(100) + "a:/x/y", "1".repeat(63) + "a:/x/y", "pfad-host", 0.90);
    }

    @Test
    void langerLaufStehtNieVorDemLaufAnfangUndKeinSurrogatpaarWirdGetrennt() {
        String text = "\uD83D\uDE00" + "a".repeat(70) + ":/x/y";
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(text.length() - (64 + 5), f.get(0).start());
        assertFalse(Character.isLowSurrogate(text.charAt(f.get(0).start())));
        assertEquals("a".repeat(64) + ":/x/y", f.get(0).originalValue());
    }

    // ================================================================== Laufzeit (linear, je < 2 s)

    private static void laufzeit(String text, int erwartet) {
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        assertEquals(erwartet, r.size(), "Fundzahl Erkenner");
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        assertEquals(erwartet, s.size(), "Fundzahl Scanner");
    }

    @Test
    void laufzeitEinMillionABuchstabenDannDoppelpunktSchraegstrichX() {
        String text = "a".repeat(1_000_000) + ":/x";
        laufzeit(text, 1);
        Finding f = R.detect(text).get(0);
        assertEquals(1_000_000 - 64, f.start());
        assertEquals(text.length(), f.end());
    }

    @Test
    void laufzeitLaufwerksMusterVielfach() {
        laufzeit("a:/".repeat(333_333), 1);
        laufzeit("a:/ ".repeat(250_000), 0);
    }

    @Test
    void laufzeitKurzeHostPfadeVielfach() {
        laufzeit("ab:/c ".repeat(166_666), 166_666);
        laufzeit("ab:/ ".repeat(200_000), 0);
    }

    @Test
    void laufzeitPunktBuchstabeFolgeDannDoppelpunktSchraegstrichX() {
        String text = "a.".repeat(500_000) + ":/x";
        laufzeit(text, 1);
        assertEquals(text.length() - (64 + 3), R.detect(text).get(0).start());
    }

    @Test
    void laufzeitDoppelpunktSchraegstrichVielfach() {
        laufzeit(":/".repeat(500_000), 0);
        laufzeit(":/ ".repeat(333_333), 0);
    }

    @Test
    void laufzeitBuchstabeDoppelpunktVielfachDannSchraegstrichX() {
        String text = "a:".repeat(500_000) + "/x";
        laufzeit(text, 1);
        assertEquals("a:/x", R.detect(text).get(0).originalValue());
    }

    @Test
    void laufzeitLangeLaeufeMitZiffernUndPunktenOhneBuchstabenVielfach() {
        laufzeit(("1".repeat(70) + ":/x ").repeat(13_000), 0);
        laufzeit(("a" + "1".repeat(70) + ":/x ").repeat(13_000), 0);
        laufzeit(("a".repeat(70) + ":/x ").repeat(13_000), 13_000);
        laufzeit(("a".repeat(64) + ":/x ").repeat(15_000), 15_000);
        laufzeit(("a".repeat(63) + ":/x ").repeat(15_000), 15_000);
        laufzeit(("a".repeat(65) + ":/x ").repeat(15_000), 15_000);
    }

    @Test
    void laufzeitHostPfadeInEinemLangenLauf() {
        // viele Koepfe in einem Lauf: "ab:/ab:/ab:/..." ohne Leerraum ist ein Lauf, ein Fund
        laufzeit("ab:/".repeat(250_000), 1);
        laufzeit("ab.cd:/e".repeat(110_000), 1);
    }

    // ------------------------------------------------------------------ Laufzeit: Mangel 1 (reviewer, Durchlauf 3)
    // Linearitaetsbeleg: hoechstens 64 gelesene Zeichen je ":/"-Stelle (nicht "jedes Zeichen hoechstens zweimal").
    // Die Muster treffen das Gegenbeispiel: mehrere ":/" innerhalb von 64 Zeichen, fruehere Koepfe scheitern.

    private static String wiederholt(String einheit, int zeichen) {
        return einheit.repeat(zeichen / einheit.length());
    }

    @Test
    void laufzeitZiffernLaufMitDoppelpunktSchraegstrichAlle42Zeichen() {
        // 40 Ziffern + ":/": kein Buchstabe im Lauf, jede ":/"-Stelle liest ein 64-Zeichen-Fenster ohne Treffer
        laufzeit(wiederholt("1".repeat(40) + ":/", 1_000_000), 0);
    }

    @Test
    void laufzeitBuchstabenLaufMitDoppelpunktUndZweiSchraegstrichen() {
        // "://": nie ein Host-Kopf; der Laufwerkskopf "a:/" hat dahinter kein Segment
        laufzeit(wiederholt("a".repeat(30) + ":// ", 1_000_000), 0);
    }

    @Test
    void laufzeitUeberlappendeFensterMehrereDoppelpunkteInnerhalb64Zeichen() {
        // das Muster aus dem reviewer-Befund: dasselbe Stueck wird von mehreren ":/"-Stellen gelesen, alle Koepfe
        // scheitern (kein Buchstabe im Lauf)
        laufzeit(wiederholt("1".repeat(70) + ":/" + "1".repeat(30) + ":/x ", 1_000_000), 0);
    }

    @Test
    void laufzeitUeberlappendeFensterMitBuchstabeErgebenJeEinenFund() {
        String einheit = "1".repeat(70) + ":/" + "1".repeat(30) + "a:/x ";
        String text = wiederholt(einheit, 1_000_000);
        laufzeit(text, text.length() / einheit.length());
        Finding erster = R.detect(text).get(0);
        assertEquals("1".repeat(30) + "a:/x", erster.originalValue());
        assertEquals(72, erster.start());
    }

    @Test
    void laufzeitDichteFensterMitLetztemBuchstabenAmEnde() {
        // ":/" alle 9 Zeichen ohne Buchstabe, nur der letzte Lauf hat einen: ein Fund, kein quadratisches Lesen
        String text = "1234567:/".repeat(100_000) + "a:/x";
        laufzeit(text, 1);
    }

    // ------------------------------------------------------------------ HOST_PFAD-Koepfe in Anfuehrungszeichen

    private static final String[] ANFUEHRUNG = {"\"", "\u201C", "\u201D", "\u201E", "\u00AB", "\u00BB", "'", "\u2018",
            "\u2019", "\u201A", "\u2039", "\u203A", "`"};

    @Test
    void anfuehrungLangerLaufImZitatBeginnt64ZeichenVorDemDoppelpunkt() {
        String text = "\"" + "a".repeat(100) + ":/x\"";
        Finding f = assertFund(text, "a".repeat(64) + ":/x", "pfad-host", 0.80);
        assertEquals(1 + 36, f.start());
        assertEquals("\"" + "a".repeat(36) + "[[PFAD_1]]\"", pseudonymisiert(text));
    }

    @Test
    void anfuehrungHostMitLeerzeichenImPfadJederDreizehnArtenIstEinFundOhneAnfuehrungszeichen() {
        for (String q : ANFUEHRUNG) {
            assertFund(q + "host:/Meine Akte/x" + q, "host:/Meine Akte/x", "pfad-host", 0.90);
            // Ein schliessendes Zeichen direkt am Pfad, das weder Endzeichen noch gekuerzt ist (alle ausser
            // Anfuehrungszeichen, Backtick, Apostroph), bleibt im Durchlauf ohne Zitatregel im Fund: zu gross, kein Leck.
            boolean imFund = !q.equals("\"") && !q.equals("`") && !q.equals("'");
            assertFund("Datei " + q + "srv-beispiel:/a/b" + q + " ok", "srv-beispiel:/a/b" + (imFund ? q : ""),
                    "pfad-host", 0.90);
            assertFund(q + "ab:/c d" + q, "ab:/c d", "pfad-host", 0.80);
            // im Standard-Scanner und in der Pseudonymisierung: Anfuehrungszeichen bleiben, der Pfad ist ersetzt
            assertEquals(q + "[[PFAD_1]]" + q, pseudonymisiert(q + "host:/Meine Akte/x" + q),
                    "Zeichen " + (int) q.charAt(0));
        }
    }

    @Test
    void anfuehrungHostKopfOhneSchliessendesZeichenBleibtGeschuetzt() {
        for (String q : ANFUEHRUNG) {
            // ohne Paar zaehlt nur der Durchlauf ohne Anfuehrungszeichen: Ende am ersten Leerzeichen
            assertFund(q + "host:/a/b c", "host:/a/b", "pfad-host", 0.90);
        }
    }

    @Test
    void laufzeitDichteHostKoepfeInAnfuehrungszeichenVielfach() {
        String text = wiederholt("\"ab:/c d\" ", 1_000_000);
        laufzeit(text, text.length() / 10);
        assertEquals("ab:/c d", R.detect(text).get(0).originalValue());
    }

    @Test
    void laufzeitDreizehnAnfuehrungszeichenArtenMitHostKoepfen() {
        for (String q : ANFUEHRUNG) {
            String text = wiederholt(q + "ab:/c d" + q + " ", 1_000_000);
            laufzeit(text, text.length() / 10);
        }
    }

    @Test
    void laufzeitGemischteAnfuehrungszeichenMitHostKoepfenDecktJedenKopfAb() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sb.length() < 1_000_000; i++) {
            String q = ANFUEHRUNG[i % ANFUEHRUNG.length];
            sb.append(q).append("ab:/c d").append(q).append(' ');
        }
        String text = sb.toString();
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        // Paare verschiedener Arten koennen einander ueberspannen: die Fundzahl ist nicht fest, aber jeder Kopf ist
        // abgedeckt, und Erkenner und Scanner stimmen ueberein
        assertEquals(r.size(), s.size(), "Scanner liefert dieselbe Fundzahl");
        boolean[] gedeckt = new boolean[text.length()];
        int vorigesEnde = 0;
        for (Finding f : s) {
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            vorigesEnde = f.end();
            for (int i = f.start(); i < f.end(); i++) {
                gedeckt[i] = true;
            }
        }
        int koepfe = 0;
        for (int i = text.indexOf("ab:/c"); i >= 0; i = text.indexOf("ab:/c", i + 1)) {
            assertTrue(gedeckt[i] && gedeckt[i + 4], "Host-Kopf bei " + i + " nicht abgedeckt");
            koepfe++;
        }
        assertTrue(koepfe > 90_000, "Koepfe: " + koepfe);
    }

    // ------------------------------------------------------------------ Bekannte Folgen und Grenzen (security)

    @Test
    void bekannteFolgeKonfidenzSinktOhneVerlaengerungBeiHostFundImZitat() {
        // Vor T-13: Laufwerksfund ab "C:" bis vor das letzte Zeichen mit 0.95 (das Zitat "Pfad:/x y" war erst ab
        // "d:/" ein Laufwerksfund). Jetzt ist der spaetere Fund im Zitat ein Host-Fund ab "P" (0.80); beim
        // Zusammenlegen gilt die niedrigste Konfidenz der Beteiligten (Plan 7a (ii)): dieselben Grenzen, aber 0.80
        // statt 0.95. Ersetzt wird in jedem Fall.
        String text = bs("C:¦Akten'Pfad:/x y'");
        List<Finding> r = funde(text);
        assertEquals(1, r.size());
        assertEquals(0, r.get(0).start());
        assertEquals(text.length() - 1, r.get(0).end());
        assertEquals(0.80, r.get(0).confidence());
        assertEquals("pfad-laufwerk", r.get(0).source());
        // dieselben Grenzen wie ohne die Form HOST_PFAD
        PathForm[] ohneHost = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME};
        List<Finding> alt = R.detect(text, ohneHost);
        assertEquals(1, alt.size());
        assertEquals(r.get(0).start(), alt.get(0).start());
        assertEquals(r.get(0).end(), alt.get(0).end());
        assertEquals(0.95, alt.get(0).confidence());
        assertEquals("[[PFAD_1]]'", pseudonymisiert(text));
    }

    @Test
    void bekannteGrenzeIpAlsRechnerDerPfadBleibtKlartext() {
        // kein Buchstabe im Lauf: kein Host-Kopf; "/export/..." hinter ":" hat keine Startgrenze (nach 3a (e))
        String text = "192.0.2.10:/export/home/erika";
        assertKeinFund(text);
        List<Finding> s = scan(text);
        assertEquals(1, s.size());
        assertEquals(FindingType.IP, s.get(0).type());
        assertEquals("192.0.2.10", s.get(0).originalValue());
        assertTrue(pseudonymisiert(text).endsWith(":/export/home/erika"), "der Pfad bleibt Klartext");
    }

    @Test
    void bekannteGrenzeNutzerAtIpAlsRechnerDerPfadBleibtKlartext() {
        String text = "nutzer@192.0.2.10:/export/erika";
        assertKeinFund(text);
        List<Finding> s = scan(text);
        assertTrue(s.stream().noneMatch(f -> f.type() == FindingType.PFAD), "kein Pfad-Fund");
        assertTrue(pseudonymisiert(text).endsWith(":/export/erika"), "der Pfad bleibt Klartext");
    }

    @Test
    void bekannteGrenzeHostMitHomePfadHinterDemDoppelpunkt() {
        assertKeinFund("host:~/x");
        assertKeinFund("host.example:~erika/akte.pdf");
        assertTrue(scan("host:~/x").isEmpty());
    }

    @Test
    void bekannteGrenzeEinSegmentOhneHostKopfIstKeinFund() {
        assertKeinFund("/Akte_Mustermann");
        assertKeinFund("Datei /Akte_Mustermann ok");
        assertTrue(scan("Datei /Akte_Mustermann ok").isEmpty());
    }

    @Test
    void bekannteGrenzeScpMitRelativemPfadBleibtAuchNachT14Klartext() {
        // "host:erika/repo.git" hat hinter dem Doppelpunkt keinen Schraegstrich und keinen Kopf "./" oder "../": Die Form
        // RELATIV (T-14, AK-14) verlangt den Kopf, der Plan sieht fuer scp-Schreibweise mit relativem Pfad keine Form
        // vor. Ist-Verhalten bleibt: kein Fund (offene Planfrage, siehe Bericht zu T-14).
        String text = "git@host.example:erika/repo.git";
        assertKeinFund(text);
        List<Finding> s = scan(text);
        assertTrue(s.stream().noneMatch(f -> f.type() == FindingType.PFAD), "kein Pfad-Fund");
        assertTrue(pseudonymisiert(text).contains("erika/repo.git"), "relativer Pfadteil bleibt sichtbar (bekannte Grenze)");
    }
}
