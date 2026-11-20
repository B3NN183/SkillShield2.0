package de.skillshield.core.recognizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextScannerTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";

    /** Stub-Erkenner: findet jedes Vorkommen eines festen Suchworts. */
    private static Recognizer stub(FindingType type, String wort, double konfidenz, String quelle) {
        return text -> {
            List<Finding> r = new ArrayList<>();
            int i = text.indexOf(wort);
            while (i >= 0) {
                r.add(new Finding(type, wort, i, i + wort.length(), konfidenz, quelle));
                i = text.indexOf(wort, i + 1);
            }
            return r;
        };
    }

    @Test
    void ohneErkennerKeineFunde() {
        assertTrue(new TextScanner(List.of()).scan("irgendein Text").isEmpty());
        assertTrue(TextScanner.standard().scan("Das Wetter ist heute schön.").isEmpty());
    }

    @Test
    void standardScannerFindetIpv4() {
        String text = "Server 192.0.2.10 antwortet nicht";
        List<Finding> r = TextScanner.standard().scan(text);
        assertEquals(1, r.size());
        assertEquals(FindingType.IP, r.get(0).type());
        assertEquals("192.0.2.10", r.get(0).originalValue());
        assertEquals(text.substring(r.get(0).start(), r.get(0).end()), r.get(0).originalValue());
        assertTrue(TextScanner.standard().scan("Version 300.1.1.1 und 192.0.2").isEmpty());
    }

    private static void assertFunde(String text, List<Finding> funde, FindingType... typen) {
        assertEquals(typen.length, funde.size(), "Text: " + text);
        int vorigesEnde = 0;
        for (int i = 0; i < typen.length; i++) {
            Finding f = funde.get(i);
            assertEquals(typen[i], f.type());
            assertEquals(text.substring(f.start(), f.end()), f.originalValue());
            assertTrue(f.start() >= vorigesEnde);
            vorigesEnde = f.end();
        }
    }

    @Test
    void standardScannerFindetUrlAk5() {
        String text = "Siehe https://example.invalid/pfad?x=1 für Details";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.URL);
        assertEquals("https://example.invalid/pfad?x=1", r.get(0).originalValue());
        assertEquals("url", r.get(0).source());
        assertEquals(0.95, r.get(0).confidence());
    }

    @Test
    void urlMitIpHostLiefertNurDieUrl() {
        String text = "Status: http://192.0.2.10/status ok";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.URL);
        assertEquals("http://192.0.2.10/status", r.get(0).originalValue());
        String mitPort = "Aufruf http://198.51.100.7:8080/x.";
        List<Finding> r2 = TextScanner.standard().scan(mitPort);
        assertFunde(mitPort, r2, FindingType.URL);
        assertEquals("http://198.51.100.7:8080/x", r2.get(0).originalValue());
    }

    @Test
    void urlMitEmailImQueryLiefertNurDieUrl() {
        String text = "Link https://example.invalid/?m=erika.beispiel@example.invalid. Ende";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.URL);
        assertEquals("https://example.invalid/?m=erika.beispiel@example.invalid", r.get(0).originalValue());
        String userinfo = "https://erika.beispiel@example.invalid/x";
        assertFunde(userinfo, TextScanner.standard().scan(userinfo), FindingType.URL);
    }

    @Test
    void urlMitZugangsdatenIstVollstaendigImFund() {
        String text = "Aufruf https://nutzer:geheim@192.0.2.10:8443/a?b=c#d jetzt";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.URL);
        assertEquals("https://nutzer:geheim@192.0.2.10:8443/a?b=c#d", r.get(0).originalValue());
    }

    @Test
    void fileUriLiefertKeinenUrlFundSondernGenauEinenPfadFundSeitT16() {
        // Geaendert in T-16: bis dahin nur "kein URL-Fund" (der Rest blieb ein Laufwerks- bzw. Teilfund); jetzt genau
        // ein PFAD-Fund der Form FILE_URI ueber den ganzen Ausdruck.
        for (String text : List.of("file:///C:/Akte/x.pdf", "Datei file:///srv/beispiel/x.pdf offen",
                "FILE://srv.example/x", "x:file://srv.example/x")) {
            List<Finding> r = TextScanner.standard().scan(text);
            assertTrue(r.stream().noneMatch(f -> f.type() == FindingType.URL), text);
            assertEquals(1, r.size(), text);
            assertEquals(FindingType.PFAD, r.get(0).type(), text);
            assertEquals("pfad-file-uri", r.get(0).source(), text);
            assertTrue(text.toLowerCase().contains(r.get(0).originalValue().toLowerCase()), text);
            assertTrue(r.get(0).originalValue().toLowerCase().startsWith("file:"), text);
        }
    }

    @Test
    void ftpUrlMitZugangsdatenIstGenauEinUrlFundOhneEmail() {
        String text = "ftp://nutzer:geheim@host.example/p";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.URL);
        assertEquals(text, r.get(0).originalValue());
        String mitJdbc = "Verbindung jdbc:postgresql://db.example:5432/kv?user=nutzer&password=geheim bereit";
        List<Finding> r2 = TextScanner.standard().scan(mitJdbc);
        assertFunde(mitJdbc, r2, FindingType.URL);
        assertEquals("jdbc:postgresql://db.example:5432/kv?user=nutzer&password=geheim", r2.get(0).originalValue());
    }

    @Test
    void emailIpUndUrlNebeneinander() {
        String text = "Mail erika.beispiel@example.invalid, Server 192.0.2.10, Seite https://example.invalid/x.";
        List<Finding> r = TextScanner.standard().scan(text);
        assertFunde(text, r, FindingType.EMAIL, FindingType.IP, FindingType.URL);
        assertEquals("https://example.invalid/x", r.get(2).originalValue());
    }

    @Test
    void urlOhneLeerzeichenLaufzeit() {
        long t0 = System.nanoTime();
        assertTrue(TextScanner.standard().scan("a".repeat(100_000)).isEmpty());
        assertEquals(1, TextScanner.standard().scan("https://" + "a".repeat(100_000)).size());
        assertTrue(System.nanoTime() - t0 < 4_000_000_000L);
    }

    @Test
    void stubFundHatPositionGleichTeilstring() {
        String text = "ab " + MARKER + " cd " + MARKER;
        List<Finding> r = new TextScanner(List.of(stub(FindingType.TEXT, MARKER, 0.9, "stub"))).scan(text);
        assertEquals(2, r.size());
        for (Finding f : r) {
            assertEquals(f.originalValue(), text.substring(f.start(), f.end()));
        }
        assertTrue(r.get(0).start() < r.get(1).start());
    }

    @Test
    void leererTextUndLeerraumLiefernLeereListe() {
        TextScanner scanner = new TextScanner(List.of(stub(FindingType.TEXT, "xyz", 0.9, "stub")));
        assertTrue(scanner.scan("").isEmpty());
        assertTrue(scanner.scan("  \t\r\n  ").isEmpty());
        assertTrue(TextScanner.standard().scan("").isEmpty());
    }

    @Test
    void nullTextWirdMitParameternamenAbgelehnt() {
        assertEquals("text", assertThrows(NullPointerException.class,
                () -> new TextScanner(List.of()).scan(null)).getMessage());
        assertEquals("text", assertThrows(NullPointerException.class,
                () -> TextScanner.standard().scan(null)).getMessage());
    }

    @Test
    void nullErkennerlisteUndNullElementWerdenAbgelehnt() {
        assertEquals("recognizers", assertThrows(NullPointerException.class,
                () -> new TextScanner(null)).getMessage());
        List<Recognizer> mitNull = new ArrayList<>();
        mitNull.add(null);
        assertThrows(NullPointerException.class, () -> new TextScanner(mitNull));
    }

    @Test
    void erkennerListeWirdKopiert() {
        List<Recognizer> liste = new ArrayList<>();
        TextScanner scanner = new TextScanner(liste);
        liste.add(stub(FindingType.TEXT, "xyz", 0.9, "stub"));
        assertTrue(scanner.scan("xyz").isEmpty());
    }

    @Test
    void umschliessenderFundGewinntGegenEnthaltenenMitHoehererKonfidenz() {
        String text = "Aufruf https://example.invalid/?datei=C:/Tools/a.exe jetzt";
        String url = "https://example.invalid/?datei=C:/Tools/a.exe";
        String pfad = "C:/Tools/a.exe";
        Recognizer urlStub = stub(FindingType.URL, url, 0.80, "url");
        Recognizer pfadStub = stub(FindingType.PFAD, pfad, 0.99, "pfad-laufwerk");
        List<Finding> r = new TextScanner(List.of(pfadStub, urlStub)).scan(text);
        assertEquals(1, r.size());
        assertEquals(FindingType.URL, r.get(0).type());
    }

    @Test
    void teilueberlappungWirdZusammengelegt() {
        String text = "xx AAAA BBBB yy";
        Recognizer a = t -> List.of(new Finding(FindingType.TEXT, "AAAA BB", 3, 10, 0.7, "stub-a"));
        Recognizer b = t -> List.of(new Finding(FindingType.IP, "BBBB y", 8, 14, 0.9, "stub-b"));
        List<Finding> r = new TextScanner(List.of(a, b)).scan(text);
        assertEquals(1, r.size());
        assertEquals(3, r.get(0).start());
        assertEquals(14, r.get(0).end());
        assertEquals(text.substring(3, 14), r.get(0).originalValue());
        assertEquals(Finding.SOURCE_MERGED, r.get(0).source());
        assertEquals(0.7, r.get(0).confidence());
    }

    @Test
    void beispielAk7gMitStubErkennernErgibtEinenZusammengelegtenFund() {
        String text = "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende";
        Recognizer url = stub(FindingType.URL, "https://intranet.invalid/?f='C:/Akte", 0.95, "url");
        Recognizer pfad = stub(FindingType.PFAD, "C:/Akte 2025/erika.beispiel@example.invalid/x.pdf", 0.95,
                "pfad-laufwerk");
        Recognizer mail = stub(FindingType.EMAIL, "erika.beispiel@example.invalid", 0.95, "email");
        List<List<Recognizer>> reihenfolgen = List.of(List.of(url, pfad, mail), List.of(mail, pfad, url),
                List.of(pfad, url, mail));
        for (List<Recognizer> erkenner : reihenfolgen) {
            List<Finding> r = new TextScanner(erkenner).scan(text);
            assertEquals(1, r.size());
            Finding fund = r.get(0);
            assertEquals(FindingType.PFAD, fund.type());
            assertEquals(5, fund.start());
            assertEquals(83, fund.end());
            assertEquals(text.substring(5, 83), fund.originalValue());
            assertEquals(0.95, fund.confidence());
            assertEquals(Finding.SOURCE_MERGED, fund.source());
            assertEquals('\'', text.charAt(fund.end()));
        }
    }

    @Test
    void ausnahmeEinesErkennersWirdOhneMeldungUndUrsacheGekapselt() {
        String text = "Text " + MARKER;
        RuntimeException original = new RuntimeException("Eingabe war " + text,
                new IllegalArgumentException(MARKER));
        original.addSuppressed(new IllegalStateException(MARKER));
        Recognizer ok = stub(FindingType.TEXT, "Text", 0.9, "stub");
        Recognizer wirft = t -> {
            throw original;
        };
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new TextScanner(List.of(ok, wirft)).scan(text));
        assertEquals("Erkenner an Index 1 fehlgeschlagen: java.lang.RuntimeException", e.getMessage());
        assertFalse(e.getMessage().contains(MARKER));
        assertNull(e.getCause());
        assertEquals(0, e.getSuppressed().length);
    }

    @Test
    void errorEinesErkennersWirdNichtGekapselt() {
        Recognizer wirft = t -> {
            throw new StackOverflowError();
        };
        assertThrows(StackOverflowError.class, () -> new TextScanner(List.of(wirft)).scan("x"));
    }

    @Test
    void ergebnisIstUnabhaengigVonDerReihenfolgeDerErkenner() {
        String text = "aa bb cc aa bb cc";
        Recognizer r1 = stub(FindingType.TEXT, "aa", 0.9, "stub-a");
        Recognizer r2 = stub(FindingType.IP, "bb", 0.6, "stub-b");
        Recognizer r3 = stub(FindingType.URL, "cc", 0.2, "stub-c");
        List<Finding> x = new TextScanner(List.of(r1, r2, r3)).scan(text);
        List<Finding> y = new TextScanner(List.of(r3, r1, r2)).scan(text);
        List<Finding> z = new TextScanner(List.of(r2, r3, r1)).scan(text);
        assertEquals(x, y);
        assertEquals(x, z);
        assertEquals(6, x.size());
    }

    @Test
    void ergebnisIstNachStartSortiertUndUeberlappungsfrei() {
        String text = "cc aa bb";
        List<Finding> r = new TextScanner(List.of(
                stub(FindingType.TEXT, "bb", 0.9, "stub"),
                stub(FindingType.TEXT, "cc", 0.9, "stub"),
                stub(FindingType.TEXT, "aa", 0.9, "stub"))).scan(text);
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).end() <= r.get(i).start());
        }
        assertEquals(List.of("cc", "aa", "bb"), r.stream().map(Finding::originalValue).toList());
    }

    @Test
    void fraglicherFundBleibtImScanErhalten() {
        List<Finding> r = new TextScanner(List.of(stub(FindingType.TEXT, "zz", 0.3, "stub"))).scan("a zz b");
        assertEquals(1, r.size());
        assertEquals(ConfidenceLevel.FRAGLICH, r.get(0).level());
    }

    @Test
    void zweiAufrufeLiefernGleicheListe() {
        TextScanner scanner = new TextScanner(List.of(
                stub(FindingType.TEXT, "aa", 0.9, "stub-a"), stub(FindingType.IP, "a", 0.9, "stub-b")));
        assertEquals(scanner.scan("aa a aa"), scanner.scan("aa a aa"));
    }

    @Test
    void ergebnislisteIstUnveraenderlich() {
        List<Finding> r = new TextScanner(List.of(stub(FindingType.TEXT, "aa", 0.9, "stub"))).scan("aa");
        assertThrows(UnsupportedOperationException.class, () -> r.add(r.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> r.remove(0));
    }

    @Test
    void fundDerNichtZumTextPasstWirdOhneWertInDerMeldungAbgelehnt() {
        Recognizer falsch = t -> List.of(new Finding(FindingType.TEXT, MARKER, 0, MARKER.length(), 0.9, "stub"));
        TextScanner scanner = new TextScanner(List.of(falsch));
        IllegalStateException e1 = assertThrows(IllegalStateException.class,
                () -> scanner.scan("ein ganz anderer Text mit genug Laenge"));
        IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> scanner.scan("kurz"));
        assertFalse(e1.getMessage().contains(MARKER));
        assertFalse(e2.getMessage().contains(MARKER));
        assertFalse(e1.getMessage().contains("mustermann"));
    }

    @Test
    void erkennerDerNullLiefertWirdAbgelehnt() {
        Recognizer nullListe = t -> null;
        assertThrows(NullPointerException.class, () -> new TextScanner(List.of(nullListe)).scan("x"));
        Recognizer nullElement = t -> {
            List<Finding> l = new ArrayList<>();
            l.add(null);
            return l;
        };
        assertThrows(NullPointerException.class, () -> new TextScanner(List.of(nullElement)).scan("x"));
    }

    @Test
    void ausnahmenmeldungenEnthaltenKeinenTextMarker() {
        String text = "Text " + MARKER;
        NullPointerException npe = assertThrows(NullPointerException.class,
                () -> new TextScanner(List.of((Recognizer) t -> null)).scan(text));
        assertFalse(String.valueOf(npe.getMessage()).contains(MARKER));
    }

    @Test
    void ergebnisToStringEnthaeltKeinenMarker() {
        String text = "x " + MARKER + " y";
        List<Finding> r = new TextScanner(List.of(stub(FindingType.TEXT, MARKER, 0.9, "stub"))).scan(text);
        assertFalse(r.toString().contains(MARKER));
    }
}
