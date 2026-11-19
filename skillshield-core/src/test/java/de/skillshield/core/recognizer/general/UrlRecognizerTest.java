package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testdaten sind synthetisch: Hosts nur {@code example.invalid}, {@code intranet.invalid}, {@code *.example}
 * ({@code a.example}, {@code b.example}), IPs nur aus 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24, Zugangsdaten nur
 * der Platzhalter {@code nutzer:geheim}. Nicht-ASCII-Zeichen stehen nur als Code-Punkt-Aufruf im Quelltext.
 */
class UrlRecognizerTest {

    private static final UrlRecognizer R = new UrlRecognizer();
    private static final long LIMIT_NANOS = 2_000_000_000L;
    private static final int MAX_SCHEME = UrlRecognizer.MAX_SCHEME_LENGTH;
    private static final int MAX_PREFIXED = UrlRecognizer.MAX_PREFIXED_LENGTH;
    private static final int LANG = 100_000;

    private static String cp(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    /** Erkennt und prüft je Fund Teilstring, Typ, Quelle, Konfidenz, Sortierung und Überlappungsfreiheit. */
    private static List<String> werte(String text) {
        List<Finding> funde = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue());
            assertEquals(FindingType.URL, f.type());
            assertEquals("url", f.source());
            assertEquals(0.95, f.confidence());
            assertEquals(ConfidenceLevel.SICHER, f.level());
            assertTrue(f.start() >= vorigesEnde, "Funde überlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde.stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static void assertEinFund(String text, String erwartet) {
        assertEquals(List.of(erwartet), werte(text), "Text: " + text);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    private static int anzahl(String text) {
        return werte(text).size();
    }

    private static void innerhalbLimit(Supplier<Integer> lauf, int erwartet) {
        long t0 = System.nanoTime();
        int ist = lauf.get();
        long dauer = System.nanoTime() - t0;
        assertEquals(erwartet, ist);
        assertTrue(dauer < LIMIT_NANOS, "Laufzeit " + dauer / 1_000_000 + " ms");
    }

    // ---- AK-5, AK-1

    @Test
    void ak5BeispielLiefertEinenFundMitPosition() {
        String text = "Siehe https://example.invalid/pfad?x=1 für Details";
        List<Finding> funde = R.detect(text);
        assertEquals(1, funde.size());
        assertEquals("https://example.invalid/pfad?x=1", funde.get(0).originalValue());
        assertEquals(6, funde.get(0).start());
        assertEquals(38, funde.get(0).end());
        assertEinFund(text, "https://example.invalid/pfad?x=1");
    }

    @Test
    void httpUndHttpsSchemata() {
        assertEinFund("http://example.invalid/", "http://example.invalid/");
        assertEinFund("https://example.invalid", "https://example.invalid");
        assertKeinFund("mailto:erika.beispiel@example.invalid");
    }

    // ---- Beliebige Schemata (A-2, Nutzerentscheidung)

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://nutzer:geheim@host.example/p",
            "sftp://nutzer:geheim@host.example/p",
            "smb://srv.example/freigabe/akte.pdf",
            "ldap://dc.example/dc=a,dc=b",
            "mongodb+srv://nutzer:geheim@db.example/kv",
            "redis://192.0.2.10:6379/0",
            "wss://host.example/s?token=abc",
            "ws://example.invalid/x",
            "jdbc:postgresql://db.example:5432/kv?user=nutzer&password=geheim",
            "jdbc:mysql://db.example/kv",
            "a:b:c://host.example/x",
            "HTTPS://example.invalid/x",
            "x-y.z+1://host.example"})
    void beliebigeSchemataSindEinFund(String url) {
        assertEinFund(url, url);
        assertEinFund("Siehe " + url + ". Ende", url);
        assertEinFund("(" + url + ")", url);
    }

    @Test
    void schemaOhneBuchstabeIstKeinFund() {
        assertKeinFund("1://x");
        assertKeinFund("://x");
        assertKeinFund(".://x");
        assertKeinFund("-://x");
        assertKeinFund("+1.-://x");
        assertKeinFund(" ://x");
        assertKeinFund(cp(0xE4) + "://x");
    }

    @Test
    void fileSchemaIstKeinUrlFund() {
        assertKeinFund("file:///C:/Akte/x.pdf");
        assertKeinFund("FILE://srv.example/freigabe/x.pdf");
        assertKeinFund("File:///srv/x");
        assertKeinFund("Datei file:///srv/x offen");
        assertKeinFund("x:file://srv.example/x");
        assertKeinFund("jdbc:file://srv.example/x");
        assertKeinFund("1file://srv.example/x");
        // nicht genau "file": normaler Fund
        assertEinFund("xfile://srv.example/x", "xfile://srv.example/x");
        assertEinFund("file2://srv.example/x", "file2://srv.example/x");
        assertEinFund("file:x://srv.example/x", "file:x://srv.example/x");
    }

    @Test
    void praefixeGehoerenZumFund() {
        assertEinFund("URL:https://example.invalid/x", "URL:https://example.invalid/x");
        assertEinFund("a:b:c:https://example.invalid/x", "a:b:c:https://example.invalid/x");
        assertEinFund("Hinweis:ftp://host.example/x", "Hinweis:ftp://host.example/x");
        // Präfix ohne Buchstaben gehört nicht dazu
        assertEinFund("12:https://example.invalid/x", "https://example.invalid/x");
        assertEinFund(":https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("URL: https://example.invalid/x", "https://example.invalid/x");
    }

    @Test
    void schemaLaufHatHoechstens32Zeichen() {
        String lang = "a".repeat(MAX_SCHEME + 10);
        String erwartet = "a".repeat(MAX_SCHEME - 3) + "ftp";
        assertEinFund(lang + "ftp://host.example/x", erwartet + "://host.example/x");
        // Beginn am ersten Buchstaben im 32er-Fenster, Ziffern/Punkte davor zählen nicht
        String fenster = "1".repeat(MAX_SCHEME - 3) + "ftp";
        assertEinFund(fenster + "://host.example/x", "ftp://host.example/x");
        String punkte = ".".repeat(MAX_SCHEME + 10) + "ftp";
        assertEinFund(punkte + "://host.example/x", "ftp://host.example/x");
        // genau 32 Zeichen bleiben vollständig
        String genau = "b".repeat(MAX_SCHEME - 3) + "ftp";
        assertEquals(MAX_SCHEME, genau.length());
        assertEinFund(genau + "://x", genau + "://x");
    }

    @Test
    void praefixeSindInsgesamtAufMaximalLaenge64Begrenzt() {
        String schema = "s".repeat(MAX_SCHEME);
        // 32 + ':' + 32 = 65 Zeichen vor dem Trenner: Präfix wird nicht aufgenommen
        String voll = "p".repeat(MAX_SCHEME) + ":" + schema + "://x";
        assertEquals(MAX_PREFIXED + 1, voll.indexOf("://"));
        assertEinFund(voll, schema + "://x");
        // 31 + ':' + 32 = 64 Zeichen vor dem Trenner: noch erlaubt
        String grenze = "p".repeat(MAX_SCHEME - 1) + ":" + schema + "://x";
        assertEquals(MAX_PREFIXED, grenze.indexOf("://"));
        assertEinFund(grenze, grenze);
        // Viele kurze Präfixe: Fund beginnt höchstens 64 Zeichen vor dem Trenner
        String viele = "a:".repeat(50) + "c://x";
        List<Finding> funde = R.detect(viele);
        assertEquals(1, funde.size());
        assertTrue(viele.indexOf("://") - funde.get(0).start() <= MAX_PREFIXED);
        assertEquals(viele.length(), funde.get(0).end());
    }

    @Test
    void nichtVorDasEndeDesVorigenFundsZurueckgelesen() {
        assertEquals(List.of("ftp://a.example/x", "ftp://b.example/y"),
                werte("ftp://a.example/x ftp://b.example/y"));
        assertEquals(List.of("ftp://a.example/x", "ftp://b.example/y"),
                werte("<ftp://a.example/x>ftp://b.example/y"));
        // Ende des ersten Funds bei '"': Das zweite Schema beginnt dahinter.
        assertEquals(List.of("a://x", "b://y"), werte("a://x\"b://y"));
    }

    // ---- AK-3: Endzeichen

    @ParameterizedTest
    @ValueSource(strings = {".", ",", ";", ":", "!", "?", "'", "..", ".,", "?!", "!?.", ").", "),", ")?", ".)"})
    void endzeichenGehoerenNichtZumFund(String ende) {
        String url = "https://example.invalid/pfad";
        assertEinFund(url + ende, url);
        assertEinFund("Siehe " + url + ende + " Danach", url);
    }

    @Test
    void schlusspunktBeiHostOhnePfad() {
        assertEinFund("Aufruf unter https://example.invalid.", "https://example.invalid");
        assertEinFund("Aufruf unter https://example.invalid:", "https://example.invalid");
        assertEinFund("https://example.invalid:8443.", "https://example.invalid:8443");
    }

    @Test
    void nurSchemaOhneRumpfIstKeinFund() {
        assertKeinFund("https://");
        assertKeinFund("http://");
        assertKeinFund("http:// x");
        assertKeinFund("https://.");
        assertKeinFund("https://.,;:!?'");
        assertKeinFund("Siehe https://. ");
        assertKeinFund("https://)");
        assertKeinFund("https://\n");
        assertKeinFund("(https://)");
        assertKeinFund("https:/example.invalid");
        assertKeinFund("https//example.invalid");
        assertKeinFund("://example.invalid");
    }

    @Test
    void klammernAmEnde() {
        assertEinFund("(https://example.invalid/x)", "https://example.invalid/x");
        assertEinFund("(siehe https://example.invalid/x).", "https://example.invalid/x");
        assertEinFund("https://example.invalid/a_(b)", "https://example.invalid/a_(b)");
        assertEinFund("(https://example.invalid/a_(b))", "https://example.invalid/a_(b)");
        assertEinFund("https://example.invalid/a_(b).", "https://example.invalid/a_(b)");
        assertEinFund("https://example.invalid/a_(b))", "https://example.invalid/a_(b)");
        assertEinFund("https://example.invalid/a_(b)))", "https://example.invalid/a_(b)");
        assertEinFund("[https://example.invalid/x]", "https://example.invalid/x");
        assertEinFund("https://example.invalid/a[1]", "https://example.invalid/a[1]");
        assertEinFund("[https://example.invalid/a[1]]", "https://example.invalid/a[1]");
        assertEinFund("https://example.invalid/(a", "https://example.invalid/(a");
        assertEinFund("https://example.invalid/a)(", "https://example.invalid/a)(");
        assertEinFund("(https://example.invalid/x).)", "https://example.invalid/x");
    }

    @Test
    void geschweifteKlammerGehoertZumFund() {
        // Plan: nur ) und ] werden entfernt; } bleibt (lieber zu groß).
        assertEinFund("{https://example.invalid/x}", "https://example.invalid/x}");
    }

    @Test
    void umschliessendeZeichen() {
        assertEinFund("<https://example.invalid/x>", "https://example.invalid/x");
        assertEinFund("\"https://example.invalid/x\"", "https://example.invalid/x");
        assertEinFund("'https://example.invalid/x'", "https://example.invalid/x");
        assertEinFund("`https://example.invalid/x`", "https://example.invalid/x`");
        assertEinFund("(\"https://example.invalid/x\")", "https://example.invalid/x");
        assertEinFund("<a href=\"https://example.invalid/x?a=1&b=2\">", "https://example.invalid/x?a=1&b=2");
    }

    @Test
    void markdownLinkIstEinGrosserFundOhneLeerraumDazwischen() {
        // Keine Leerraum-Trennung: ein Fund über beide Adressen (nichts bleibt Klartext).
        assertEinFund("[https://a.example/x](https://b.example/y)", "https://a.example/x](https://b.example/y)");
    }

    @Test
    void typografischeAnfuehrungszeichenAmEndeBleibenImFund() {
        // Nicht Teil der Endzeichen-Liste im Plan: größer statt kleiner.
        String text = cp(0x201E) + "https://example.invalid/x" + cp(0x201C);
        assertEinFund(text, "https://example.invalid/x" + cp(0x201C));
    }

    // ---- Schema-Schreibung

    @ParameterizedTest
    @ValueSource(strings = {"HTTPS://example.invalid/x", "Https://example.invalid/x", "hTtPs://example.invalid/x",
            "HTTP://example.invalid/x", "Http://example.invalid/x", "hTTp://example.invalid/x"})
    void schemaGrossUndKleinschreibung(String url) {
        assertEinFund(url, url);
        assertEinFund("Siehe " + url + " Ende", url);
    }

    @Test
    void nichtAsciiBuchstabenSindKeinSchema() {
        // U+017F (langes s) und U+212A (Kelvin-Zeichen) sind kein ASCII-s/-k; kein Fund über Unicode-Faltung.
        assertKeinFund("http" + cp(0x17F) + "://example.invalid/x");
        // Davor stehende ASCII-Zeichen gehören zum Schema, das nicht-ASCII-Zeichen nicht.
        assertEinFund(cp(0x210D) + "ttp://example.invalid/x", "ttp://example.invalid/x");
    }

    // ---- Zeichen direkt vor dem Schema: Schema-Lauf wird nach links gelesen (Nachtrag 19.11.2026, Punkt 3)

    @Test
    void punktfuehrerVorDerUrl() {
        // '.' ist Schemazeichen: Der Lauf "Link.....https" ist als Ganzes schemaförmig.
        assertEinFund("Link.....https://example.invalid/x", "Link.....https://example.invalid/x");
        // Längerer Lauf: nur die letzten 32 Zeichen zählen, Beginn am ersten Buchstaben darin.
        assertEinFund("Link" + ".".repeat(100) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("....http://example.invalid/x", "http://example.invalid/x");
        assertEinFund(".https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("Link" + ".".repeat(LANG) + "https://example.invalid/x", "https://example.invalid/x");
    }

    @Test
    void bindeUndUnterstrichVorDerUrl() {
        // "siehe-https" ist als Ganzes schemaförmig: zu großer Fund ab "siehe" (in Ordnung).
        assertEinFund("siehe-https://example.invalid/x", "siehe-https://example.invalid/x");
        assertEinFund("-https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("_https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("---https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("___https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("host-" + "-".repeat(70) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("host_" + "_".repeat(70) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("+https://example.invalid/x", "https://example.invalid/x");
    }

    @Test
    void buchstabenUndZiffernVorDemSchema() {
        assertEinFund("xhttps://example.invalid/x", "xhttps://example.invalid/x");
        assertEinFund("Xhttp://example.invalid/x", "Xhttp://example.invalid/x");
        assertEinFund("abchttps://example.invalid/x", "abchttps://example.invalid/x");
        assertEinFund("1https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("URL:https://example.invalid/x", "URL:https://example.invalid/x");
        assertEinFund("URL=https://example.invalid/x", "https://example.invalid/x");
        assertEinFund("shttp://example.invalid/x", "shttp://example.invalid/x");
        assertEinFund(cp(0xE4) + "https://example.invalid/x", "https://example.invalid/x");
        // Lauf länger als 32 Zeichen: nur die letzten 32 zählen
        assertEinFund("a".repeat(LANG) + "https://example.invalid/x",
                "a".repeat(MAX_SCHEME - 5) + "https://example.invalid/x");
    }

    @Test
    void unsichtbareUndSonstigeZeichenVorDemSchema() {
        assertEinFund(cp(0xAD) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund(cp(0x200B) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund(cp(0xFEFF) + "https://example.invalid/x", "https://example.invalid/x");
        assertEinFund(cp(0x1F600) + "https://example.invalid/x", "https://example.invalid/x");
    }

    // ---- Bekannte Grenzen (Nachtrag Punkt 5): Ist-Zustand, vom Nutzer nicht zur Behebung gewählt

    @Test
    void bekannteGrenzeUnsichtbaresZeichenImSchema() {
        // U+200B mitten im Schema: Das Schema ist nur der Rest hinter dem Zeichen ("tps"), der Fund beginnt dort.
        assertEinFund("ht" + cp(0x200B) + "tps://example.invalid/x", "tps://example.invalid/x");
        // Im Trenner selbst: kein Fund.
        assertKeinFund("https:" + cp(0x200B) + "//example.invalid/x");
        assertKeinFund("https:/" + cp(0x200B) + "/example.invalid/x");
        assertKeinFund("https" + cp(0x200B) + "://");
    }

    @Test
    void bekannteGrenzeAnfuehrungszeichenInZugangsdatenBeendetDenFund() {
        assertEinFund("https://nutzer:ge\"heim@example.invalid/x", "https://nutzer:ge");
    }

    @Test
    void bekannteGrenzeUmbrocheneUrlEndetAmZeilenumbruch() {
        assertEinFund("https://example.invalid/lang\nerPfad/x", "https://example.invalid/lang");
        assertEinFund("https://example.invalid/lang\r\nerPfad/x", "https://example.invalid/lang");
    }

    // ---- Zugangsdaten, Port, Query, Fragment, Hosts

    @Test
    void zugangsdatenSindVollstaendigAbgedeckt() {
        assertEinFund("https://nutzer:geheim@example.invalid/x", "https://nutzer:geheim@example.invalid/x");
        assertEinFund("Aufruf https://nutzer:geheim@example.invalid.", "https://nutzer:geheim@example.invalid");
        assertEinFund("http://nutzer:geheim@192.0.2.10:8443/a?b=c#d", "http://nutzer:geheim@192.0.2.10:8443/a?b=c#d");
        assertEinFund("https://nutzer@example.invalid/x", "https://nutzer@example.invalid/x");
        assertEinFund("https://:geheim@example.invalid/x", "https://:geheim@example.invalid/x");
        assertEinFund("https://nutzer:ge%40heim@example.invalid/", "https://nutzer:ge%40heim@example.invalid/");
        assertEinFund("(https://nutzer:ge(heim)@example.invalid/x)", "https://nutzer:ge(heim)@example.invalid/x");
        assertEinFund("https://nutzer:ge@he:im@example.invalid/x", "https://nutzer:ge@he:im@example.invalid/x");
        assertEinFund("HTTPS://NUTZER:GEHEIM@EXAMPLE.INVALID/", "HTTPS://NUTZER:GEHEIM@EXAMPLE.INVALID/");
    }

    @Test
    void portQueryFragment() {
        assertEinFund("http://example.invalid:8080/pfad?a=1&b=2#abschnitt-3", "http://example.invalid:8080/pfad?a=1&b=2#abschnitt-3");
        assertEinFund("https://example.invalid/?q=a+b%20c;d=e", "https://example.invalid/?q=a+b%20c;d=e");
        assertEinFund("https://example.invalid/#/route/1", "https://example.invalid/#/route/1");
        assertEinFund("https://example.invalid?x=1", "https://example.invalid?x=1");
        assertEinFund("https://example.invalid/a//b///c", "https://example.invalid/a//b///c");
    }

    @Test
    void ipHostUndIpv6Form() {
        assertEinFund("http://192.0.2.10/status", "http://192.0.2.10/status");
        assertEinFund("http://198.51.100.7:8080/", "http://198.51.100.7:8080/");
        assertEinFund("http://203.0.113.9.", "http://203.0.113.9");
        assertEinFund("http://[2001:db8::1]:8080/x", "http://[2001:db8::1]:8080/x");
    }

    @Test
    void umlauteUndNichtAsciiInHostUndPfad() {
        String host = "b" + cp(0xFC) + "cher.example";
        String pfad = "/stra" + cp(0xDF) + "e/" + cp(0xE4) + "?x=" + cp(0xFC);
        assertEinFund("Siehe https://" + host + pfad + " ende", "https://" + host + pfad);
        assertEinFund("https://" + host + pfad + ".", "https://" + host + pfad);
        String emoji = "https://example.invalid/" + cp(0x1F600) + "x";
        assertEinFund("a " + emoji + " b", emoji);
        String cjk = "https://example.invalid/" + cp(0x65E5, 0x672C);
        assertEinFund(cjk, cjk);
        String kombi = "https://example.invalid/e" + cp(0x301) + "x";
        assertEinFund(kombi, kombi);
    }

    // ---- Mehrere URLs, Umgebung

    @Test
    void mehrereUrlsMitTrennzeichen() {
        assertEquals(List.of("https://a.example/x", "http://b.example/y"),
                werte("https://a.example/x http://b.example/y"));
        assertEquals(List.of("https://a.example/x", "http://b.example/y"),
                werte("https://a.example/x\nhttp://b.example/y"));
        assertEquals(List.of("https://a.example/x", "http://b.example/y"),
                werte("https://a.example/x\r\n\thttp://b.example/y."));
        assertEquals(List.of("https://a.example/x", "https://b.example/y"),
                werte("<https://a.example/x><https://b.example/y>"));
        assertEquals(List.of("https://a.example/x", "https://b.example/y"),
                werte("\"https://a.example/x\"\"https://b.example/y\""));
        assertEquals(List.of("https://a.example/x", "https://b.example/y"),
                werte("(https://a.example/x) (https://b.example/y)"));
    }

    @Test
    void zweiUrlsOhneLeerraumDazwischenSindEinGrosserFund() {
        // Lieber ein zu großer Fund als keiner: nichts bleibt Klartext.
        assertEinFund("https://a.example/xhttps://b.example/y", "https://a.example/xhttps://b.example/y");
        assertEinFund("https://a.example/x,https://b.example/y", "https://a.example/x,https://b.example/y");
        assertEinFund("https://a.example/x;http://b.example/y.", "https://a.example/x;http://b.example/y");
        assertEinFund("https://https://a.example/x", "https://https://a.example/x");
    }

    @Test
    void urlInSaetzenUndZeilenumbruechen() {
        assertEinFund("Zeile1\nhttps://example.invalid/x\nZeile3", "https://example.invalid/x");
        assertEinFund("Zeile1\r\nhttps://example.invalid/x\r\nZeile3", "https://example.invalid/x");
        assertEinFund("Link: https://example.invalid/x, danach mehr.", "https://example.invalid/x");
        assertEinFund("Link\thttps://example.invalid/x\tEnde", "https://example.invalid/x");
    }

    // ---- Unsichtbare Zeichen, Leerraum und Steuerzeichen

    @ParameterizedTest
    @ValueSource(ints = {0xAD, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202E, 0x2060, 0xFEFF})
    void unsichtbareZeichenImRumpfBeendenDenFundNicht(int unsichtbar) {
        String url = "https://example.invalid/a" + cp(unsichtbar) + "b";
        assertEinFund("Siehe " + url + " Ende", url);
        assertEinFund("Siehe https://exam" + cp(unsichtbar) + "ple.invalid/x Ende",
                "https://exam" + cp(unsichtbar) + "ple.invalid/x");
    }

    @ParameterizedTest
    @ValueSource(ints = {0x378, 0xE000, 0xF8FF})
    void nichtZugewieseneUndPrivateZeichenImRumpfBeendenDenFundNicht(int zeichen) {
        String url = "https://example.invalid/a" + cp(zeichen) + "b";
        assertEinFund("Siehe " + url + " Ende", url);
    }

    @Test
    void zeichenKategorienDerTestwerteStimmen() {
        assertEquals(Character.UNASSIGNED, Character.getType(0x378));
        assertEquals(Character.PRIVATE_USE, Character.getType(0xE000));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x20, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x00, 0x1F, 0x7F, 0x85, 0xA0, 0x2003, 0x2028, 0x2029,
            0x3000, 0x202F})
    void leerraumUndSteuerzeichenBeendenDenFund(int trenner) {
        assertEinFund("https://example.invalid/a" + cp(trenner) + "b", "https://example.invalid/a");
    }

    @Test
    void einzelnesSurrogatBeendetDenFund() {
        String lone = String.valueOf((char) 0xD83D);
        assertEinFund("https://example.invalid/a" + lone + "b", "https://example.invalid/a");
        assertEinFund("https://example.invalid/a" + String.valueOf((char) 0xDE00) + "b",
                "https://example.invalid/a");
        assertDoesNotThrow(() -> R.detect(lone + "https://example.invalid/x" + lone));
        assertEinFund("https://example.invalid/x" + cp(0x1F600) + cp(0x1F600), "https://example.invalid/x"
                + cp(0x1F600) + cp(0x1F600));
    }

    // ---- Lange Eingaben, Ränder

    @Test
    void leereUndKurzeEingaben() {
        assertKeinFund("");
        assertKeinFund(" ");
        assertKeinFund("h");
        assertKeinFund("http");
        assertKeinFund("http:");
        assertKeinFund("http:/");
        assertKeinFund("://");
        assertEinFund("http://a", "http://a");
        assertEinFund("https://a", "https://a");
        assertEinFund(" https://a ", "https://a");
    }

    @Test
    void sehrLangeUrl() {
        String lang = "https://example.invalid/" + "a".repeat(500_000);
        assertEinFund(lang, lang);
        assertEinFund("x " + lang + ". y", lang);
    }

    @Test
    void nullTextWirdMitParameternamenAbgelehnt() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> R.detect(null)).getMessage());
    }

    @Test
    void ergebnisIstDeterministisch() {
        String text = "x https://a.example/x y http://b.example";
        assertEquals(R.detect(text), R.detect(text));
        assertEquals(2, R.detect(text).size());
    }

    // ---- Laufzeit (AK-49, TE-7)

    @Test
    void planLaufzeitprobenHunderttausendZeichenOhneLeerzeichenMitUndOhneSchema() {
        // Wörtlich laut Plan T-5: 100 000 Zeichen ohne Leerzeichen mit/ohne https://, je < 2 s.
        innerhalbLimit(() -> anzahl("a".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("https://" + "a".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("a".repeat(99_992) + "https://"), 0);
        innerhalbLimit(() -> anzahl("https://example.invalid/".concat("a".repeat(100_000))), 1);
    }

    @Test
    void pathologischeEingabenSindLinear() {
        innerhalbLimit(() -> anzahl("http://".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("https://".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("h".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("://".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("a.".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("a@".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("1.".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("ttp://".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("http:/".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("http:// ".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("http://. ".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("http://.".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("http://a ".repeat(100_000)), 100_000);
        innerhalbLimit(() -> anzahl("https://a.example/x ".repeat(100_000)), 100_000);
        innerhalbLimit(() -> anzahl("http://:".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("http://)".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("https://x" + ")".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("https://x" + "(".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("https://x" + ".".repeat(100_000)), 1);
        innerhalbLimit(() -> anzahl("https://" + ".".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("https://" + ")".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("https://" + ")".repeat(100_000) + " " + "://".repeat(1000)), 0);
        // Schema-/Präfix-Proben (Nachtrag Punkt 3/4)
        innerhalbLimit(() -> anzahl("a".repeat(LANG) + "://x"), 1);
        innerhalbLimit(() -> anzahl("a:".repeat(LANG) + "//x"), 1);
        innerhalbLimit(() -> anzahl("a://".repeat(LANG)), 1);
        innerhalbLimit(() -> anzahl("ab:".repeat(LANG) + "c://x"), 1);
        innerhalbLimit(() -> anzahl("://".repeat(LANG)), 0);
        innerhalbLimit(() -> anzahl("a.".repeat(LANG) + "://x"), 1);
        innerhalbLimit(() -> anzahl("a://x ".repeat(LANG)), LANG);
        innerhalbLimit(() -> anzahl("file://x ".repeat(LANG)), 0);
    }

    @Test
    void langeSchemaPraefixe() {
        String praefix = "https".repeat(200_000);
        long t0 = System.nanoTime();
        List<Finding> funde = R.detect(praefix + "://example.invalid/x");
        long dauer = System.nanoTime() - t0;
        assertEquals(1, funde.size());
        // Letzte 32 Zeichen des Laufs, Beginn am ersten Buchstaben (hier das erste Zeichen des Fensters).
        assertEquals(praefix.length() - MAX_SCHEME, funde.get(0).start());
        assertEquals(praefix.substring(praefix.length() - MAX_SCHEME) + "://example.invalid/x",
                funde.get(0).originalValue());
        assertTrue(dauer < LIMIT_NANOS);
        innerhalbLimit(() -> anzahl("http".repeat(250_000) + "://"), 0);
        innerhalbLimit(() -> anzahl("http".repeat(250_000) + "://x"), 1);
        innerhalbLimit(() -> anzahl("https".repeat(200_000)), 0);
        innerhalbLimit(() -> anzahl("http:/" + "h".repeat(1_000_000)), 0);
    }

    @Test
    void eineMillionZeichenInZweiSekunden() {
        innerhalbLimit(() -> anzahl("a".repeat(1_000_000)), 0);
        innerhalbLimit(() -> anzahl("https://" + "a".repeat(1_000_000)), 1);
        innerhalbLimit(() -> anzahl("http://a ".repeat(110_000)), 110_000);
        innerhalbLimit(() -> anzahl("x https://a.example/p?q=1. ".repeat(40_000)), 40_000);
        innerhalbLimit(() -> anzahl("://".repeat(333_333)), 0);
        innerhalbLimit(() -> anzahl("http://".repeat(142_857)), 1);
        innerhalbLimit(() -> anzahl("a".repeat(1_000_000) + "://x"), 1);
        innerhalbLimit(() -> anzahl("a:".repeat(500_000) + "//x"), 1);
        innerhalbLimit(() -> anzahl("a.".repeat(500_000) + "://x"), 1);
        innerhalbLimit(() -> anzahl("ab:".repeat(333_333) + "c://x"), 1);
        innerhalbLimit(() -> anzahl("ftp://a ".repeat(120_000)), 120_000);
    }

    // ---- Eigenschaftstest: Invarianten bei zufälligen Eingaben (fester Startwert)

    @Test
    void zufallseingabenHaltenInvarianten() {
        String[] teile = {"http", "https", "HTTPS", "://", ":", "/", ".", ",", ")", "(", "]", "[", " ", "\n", "a", "b",
                "@", "?", "=", "<", ">", "\"", "'", cp(0x200B), cp(0xFC), cp(0x1F600), String.valueOf((char) 0xD83D),
                "example.invalid", "nutzer:geheim"};
        Random rnd = new Random(20261119L);
        for (int lauf = 0; lauf < 3000; lauf++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(30);
            for (int i = 0; i < len; i++) {
                sb.append(teile[rnd.nextInt(teile.length)]);
            }
            String text = sb.toString();
            List<Finding> funde = R.detect(text);
            int vorigesEnde = 0;
            for (Finding f : funde) {
                String wert = f.originalValue();
                assertEquals(text.substring(f.start(), f.end()), wert, "Lauf " + lauf);
                assertTrue(f.start() >= vorigesEnde, "Lauf " + lauf);
                vorigesEnde = f.end();
                int trenner = wert.indexOf("://");
                assertTrue(trenner >= 1 && trenner <= MAX_PREFIXED, "Lauf " + lauf);
                char erstes = wert.charAt(0);
                assertTrue((erstes >= 'a' && erstes <= 'z') || (erstes >= 'A' && erstes <= 'Z'), "Lauf " + lauf);
                assertTrue(wert.length() > trenner + 3, "Lauf " + lauf);
                assertTrue(wert.chars().noneMatch(c -> c == ' ' || c == '\n' || c == '<' || c == '>' || c == '"'),
                        "Lauf " + lauf);
                char letztes = wert.charAt(wert.length() - 1);
                assertTrue(".,;:!?'".indexOf(letztes) < 0, "Lauf " + lauf);
            }
            // Obermenge: Jedes Schema (Buchstabe direkt vor dem Trenner) mit nicht leerem, unbereinigtem Rumpf ist
            // von einem Fund abgedeckt (keiner der Zufallsbausteine ergibt das Schema "file").
            for (int i = text.indexOf("://"); i >= 0; i = text.indexOf("://", i + 1)) {
                char davor = i >= 1 ? text.charAt(i - 1) : ' ';
                boolean schema = (davor >= 'a' && davor <= 'z') || (davor >= 'A' && davor <= 'Z');
                int rumpf = i + 3;
                if (schema && rumpf < text.length() && "abc=@/-xyz".indexOf(text.charAt(rumpf)) >= 0) {
                    final int pos = rumpf;
                    assertTrue(funde.stream().anyMatch(f -> f.start() < pos && pos < f.end()), "Lauf " + lauf);
                }
            }
        }
    }
}
