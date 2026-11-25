package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Waechter fuer Geheimnis-Literale (Plan geheimnisse-erkennung, Kapitel 6.2; Anforderung AK-50). Er durchsucht
 * {@code src/test/java} und {@code src/test/resources} des Kerns sowie {@code src/test} der Module ui und app und
 * meldet jede Zeile, die eine dieser Formen vollstaendig enthaelt:
 * <ol>
 * <li>JWT: der Anfang {@code ey} plus {@code J}, dahinter JWT-Zeichen ({@code A-Z a-z 0-9 _ -}), zwei Punkte und
 * zwischen den Punkten und dahinter je mindestens ein JWT-Zeichen;</li>
 * <li>PEM-Kopf: die Zeichenfolge aus fuenf Bindestrichen und {@code BEGIN} und danach (gleiche Zeile) die Art
 * {@code PRIVATE KEY};</li>
 * <li>Praefix-Token: ein Praefix aus {@link SynthetischeGeheimnisse#praefixe()} mit so vielen Folgezeichen
 * ({@code A-Z a-z 0-9 _ -}), dass die Laenge 20 erreicht wird.</li>
 * </ol>
 *
 * <p>Geprueft wird die Zeile des Quelltexts (in Ressourcen: die Zeile der Datei) nach dem Aufloesen von Unicode- und
 * Oktal-Escapes ({@code TestdatenSynthetischTest#loeseEscapesAuf}). Dateien mit Nullbyte werden als UTF-16 gelesen,
 * wenn ein BOM oder das Nullbyte-Muster dafuer spricht; jede andere Datei mit Nullbyte ist ein Verstoss, ausser ihre
 * Endung steht in einer festen Liste von Binaerendungen (png, jpg, jpeg, gif, jar, zip, class). Das erfasst jedes
 * zusammenhaengende Java-Literal einer Zeile, ausserdem Kommentare und Textbloecke; ein Literal, das aus
 * verketteten Teilen besteht (Teile durch Anfuehrungszeichen getrennt), ist keine Form. Es gibt keine Ausnahme,
 * auch nicht fuer {@code SynthetischeGeheimnisse} und diesen Waechter selbst; dort stehen die Formen nur zerlegt, die
 * Selbsttests verketten zur Laufzeit.
 *
 * <p><b>Grenzen.</b> Nicht erkannt werden Formen, die ueber mehrere Zeilen verteilt sind, und Dateien in anderen
 * Modulen als Kern, ui und app. Meldungen nennen Datei, Zeile und Art, nie den Wert.
 */
class GeheimnisLiteralWaechterTest {

    /** Art einer Form; die Beschreibung nennt nie den Wert. */
    enum Art {
        JWT("Form eines JWT"),
        PEM_KOPF("Kopfzeile eines privaten PEM-Schluessels"),
        PRAEFIX_TOKEN("Praefix-Token");

        private final String beschreibung;

        Art(String beschreibung) {
            this.beschreibung = beschreibung;
        }

        String beschreibung() {
            return beschreibung;
        }
    }

    private static final Pattern JWT_FORM =
            Pattern.compile("ey" + "J[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final String PEM_ANFANG = "-----" + "BEGIN";
    private static final String PEM_ART = "PRIVATE" + " KEY";
    private static final int PRAEFIX_MINDESTLAENGE = 20;
    private static final List<Pattern> PRAEFIX_FORMEN = praefixFormen();

    /** Endungen, deren Dateien als Binaerdateien (mit Nullbyte) nicht geprueft werden. */
    private static final Set<String> BINAERENDUNGEN = Set.of("png", "jpg", "jpeg", "gif", "jar", "zip", "class");

    private static final long MAX_DATEIGROESSE = 5L * 1024 * 1024;
    private static final Path KERN_TEST = Path.of("src", "test");
    private static final List<String> NACHBARMODULE = List.of("skillshield-ui", "skillshield-app");

    private static List<Pattern> praefixFormen() {
        List<Pattern> formen = new ArrayList<>();
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            int rest = PRAEFIX_MINDESTLAENGE - praefix.length();
            formen.add(Pattern.compile(Pattern.quote(praefix) + "[A-Za-z0-9_-]{" + rest + ",}"));
        }
        return List.copyOf(formen);
    }

    // ------------------------------------------------------------------ Pruefung

    /** Die Arten von Formen, die die Zeile vollstaendig enthaelt (jede Art hoechstens einmal). */
    static List<Art> arten(String quellzeile) {
        String zeile = TestdatenSynthetischTest.loeseEscapesAuf(quellzeile);
        List<Art> arten = new ArrayList<>();
        if (JWT_FORM.matcher(zeile).find()) {
            arten.add(Art.JWT);
        }
        int pem = zeile.indexOf(PEM_ANFANG);
        if (pem >= 0 && zeile.indexOf(PEM_ART, pem + PEM_ANFANG.length()) >= 0) {
            arten.add(Art.PEM_KOPF);
        }
        for (Pattern form : PRAEFIX_FORMEN) {
            if (form.matcher(zeile).find()) {
                arten.add(Art.PRAEFIX_TOKEN);
                break;
            }
        }
        return arten;
    }

    /** Meldungen {@code Datei:Zeile: Art} fuer alle Zeilen des Textes; die Meldung enthaelt nie den Zeileninhalt. */
    static List<String> pruefeText(String datei, String text) {
        List<String> meldungen = new ArrayList<>();
        String[] zeilen = text.split("\\R", -1);
        for (int i = 0; i < zeilen.length; i++) {
            for (Art art : arten(zeilen[i])) {
                meldungen.add(datei + ":" + (i + 1) + ": " + art.beschreibung());
            }
        }
        return meldungen;
    }

    // ------------------------------------------------------------------ Quellen

    /** Eine zu pruefende Datei: Pfad und Name (Modul und relativer Pfad) fuer Meldungen. */
    record Quelle(Path pfad, String name) {
    }

    private static boolean istNormaleDatei(Path p) {
        return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static void sammle(Path basis, String praefix, List<Quelle> ziel) {
        assertTrue(Files.isDirectory(basis), "Verzeichnis fehlt: " + praefix);
        try (Stream<Path> s = Files.walk(basis)) {
            s.filter(GeheimnisLiteralWaechterTest::istNormaleDatei).sorted().forEach(p -> ziel.add(
                    new Quelle(p, praefix + basis.relativize(p).toString().replace('\\', '/'))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path projektWurzel() {
        for (Path p = KERN_TEST.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("skillshield-core").resolve("src"))) {
                return p;
            }
        }
        throw new IllegalStateException("Projektwurzel (Verzeichnis mit skillshield-core/src) nicht gefunden");
    }

    private static List<Quelle> kernJava() {
        List<Quelle> quellen = new ArrayList<>();
        sammle(KERN_TEST.resolve("java"), "core/java/", quellen);
        return quellen;
    }

    private static List<Quelle> kernRessourcen() {
        List<Quelle> quellen = new ArrayList<>();
        sammle(KERN_TEST.resolve("resources"), "core/resources/", quellen);
        return quellen;
    }

    private static List<Quelle> nachbarn() {
        List<Quelle> quellen = new ArrayList<>();
        Path wurzel = projektWurzel();
        for (String modul : NACHBARMODULE) {
            sammle(wurzel.resolve(modul).resolve("src").resolve("test"), modul + "/", quellen);
        }
        return quellen;
    }

    private static String endung(String name) {
        int punkt = name.lastIndexOf('.');
        return punkt < 0 ? "" : name.substring(punkt + 1).toLowerCase(Locale.ROOT);
    }

    private static String dekodiereUtf8OderLatin1(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Erkennt UTF-16 mit BOM oder am Nullbyte-Muster (alle Nullbytes auf Positionen gleicher Paritaet, mindestens ein
     * Viertel der Zeichenpositionen); {@code null}, wenn die Bytes keine erkennbare UTF-16-Kodierung haben.
     */
    private static String dekodiereUtf16(byte[] bytes) {
        int n = bytes.length;
        String text = null;
        if (n >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            text = new String(bytes, 2, n - 2, StandardCharsets.UTF_16LE);
        } else if (n >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            text = new String(bytes, 2, n - 2, StandardCharsets.UTF_16BE);
        } else if (n % 2 == 0) {
            int gerade = 0;
            int ungerade = 0;
            for (int i = 0; i < n; i++) {
                if (bytes[i] == 0) {
                    if (i % 2 == 0) {
                        gerade++;
                    } else {
                        ungerade++;
                    }
                }
            }
            boolean einseitig = (gerade == 0) != (ungerade == 0);
            if (einseitig && Math.max(gerade, ungerade) >= n / 8) {
                text = new String(bytes, ungerade > 0 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE);
            }
        }
        return text != null && text.indexOf('\0') < 0 ? text : null;
    }

    /**
     * Prueft den Inhalt einer Datei: Text (UTF-8, sonst ISO-8859-1, auch UTF-16 mit BOM oder Nullbyte-Muster) wird auf
     * Formen geprueft; eine Datei mit Nullbyte und ohne erkennbare UTF-16-Kodierung ist ein Verstoss, ausser ihre
     * Endung steht in {@link #BINAERENDUNGEN}. Meldungen nennen Datei, Zeile und Art, nie den Inhalt.
     */
    static List<String> pruefeBytes(String name, byte[] bytes) {
        boolean nullbyte = false;
        for (byte b : bytes) {
            if (b == 0) {
                nullbyte = true;
                break;
            }
        }
        if (!nullbyte) {
            return pruefeText(name, dekodiereUtf8OderLatin1(bytes));
        }
        String utf16 = dekodiereUtf16(bytes);
        if (utf16 != null) {
            return pruefeText(name, utf16);
        }
        if (BINAERENDUNGEN.contains(endung(name))) {
            return List.of();
        }
        return List.of(name + ": Binaerdatei ohne erkennbare Textkodierung");
    }

    private static List<String> pruefeDateien(List<Quelle> quellen) {
        List<String> meldungen = new ArrayList<>();
        for (Quelle q : quellen) {
            try {
                assertTrue(Files.size(q.pfad()) <= MAX_DATEIGROESSE, "Datei zu gross: " + q.name());
                meldungen.addAll(pruefeBytes(q.name(), Files.readAllBytes(q.pfad())));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return meldungen;
    }

    // ------------------------------------------------------------------ Lauf ueber die Quellen

    @Test
    void kern_java_keinGeheimnisLiteral() {
        List<Quelle> quellen = kernJava();
        assertTrue(quellen.size() >= 100, "zu wenige Java-Dateien im Kern geprueft: " + quellen.size());
        assertTrue(quellen.stream().anyMatch(q -> q.name().endsWith("/SynthetischeGeheimnisse.java")),
                "die zentrale Klasse wird nicht geprueft");
        assertTrue(quellen.stream().anyMatch(q -> q.name().endsWith("/GeheimnisLiteralWaechterTest.java")),
                "der Waechter prueft sich nicht selbst");
        assertEquals(List.of(), pruefeDateien(quellen));
    }

    @Test
    void kern_ressourcen_keinGeheimnisLiteral() {
        List<Quelle> quellen = kernRessourcen();
        assertTrue(quellen.stream().anyMatch(q -> q.name().endsWith("geheimnis/referenz-ticket.txt")),
                "das Referenz-Ticket wird nicht geprueft");
        assertEquals(List.of(), pruefeDateien(quellen));
    }

    @Test
    void uiUndApp_keinGeheimnisLiteral() {
        List<Quelle> quellen = nachbarn();
        for (String modul : NACHBARMODULE) {
            assertTrue(quellen.stream().anyMatch(q -> q.name().startsWith(modul + "/")),
                    "keine Datei geprueft in " + modul);
        }
        assertEquals(List.of(), pruefeDateien(quellen));
    }

    // ------------------------------------------------------------------ Selbsttests

    private static String jwtForm() {
        return "ey" + "J" + "abcdefgh" + "." + "ey" + "J" + "abcdefgh" + "." + "abcdefgh";
    }

    @Test
    void selbsttest_jwt_wirdErkannt_auchInJavaLiteralUndInRessourcenzeile() {
        assertEquals(List.of(Art.JWT), arten(jwtForm()));
        assertEquals(List.of(Art.JWT), arten("    String s = \"" + jwtForm() + "\";"));
        assertEquals(List.of(Art.JWT), arten("Authorization: Bearer " + jwtForm()));
    }

    @Test
    void selbsttest_jwt_zuKurzeOderZerlegteFormenSindKeinVerstoss() {
        String zweiTeile = "ey" + "J" + "abcdefgh" + "." + "abcdefgh";
        assertEquals(List.of(), arten(zweiTeile));
        assertEquals(List.of(), arten("ey" + "J" + " nur der Anfang"));
        // zerlegt: Teile durch Anfuehrungszeichen getrennt, wie in SynthetischeGeheimnisse
        String zerlegt = "String s = \"ey\" + \"J\" + MARKE + \".\" + \"ey\" + \"J\" + MARKE + \".\" + \"abc\";";
        assertEquals(List.of(), arten(zerlegt));
    }

    @Test
    void selbsttest_pemKopf_wirdErkannt_nurMitPrivateKey() {
        assertEquals(List.of(Art.PEM_KOPF), arten("-----" + "BEGIN " + "PRIVATE" + " KEY-----"));
        assertEquals(List.of(Art.PEM_KOPF), arten("x = \"-----" + "BEGIN RSA " + "PRIVATE" + " KEY-----\";"));
        assertEquals(List.of(Art.PEM_KOPF), arten("-----" + "BEGIN PGP " + "PRIVATE" + " KEY BLOCK-----"));
        assertEquals(List.of(), arten("-----" + "BEGIN " + "CERTIFICATE" + "-----"));
        assertEquals(List.of(), arten("-----" + "BEGIN " + "PUBLIC" + " KEY-----"));
        assertEquals(List.of(), arten("Ein " + "PRIVATE" + " KEY ohne Kopfzeile"));
        assertEquals(List.of(), arten("-----\" + \"BEGIN \" + art + \"-----\""));
    }

    @Test
    void selbsttest_praefixToken_jederPraefixAbLaenge20() {
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            String rest = "a".repeat(PRAEFIX_MINDESTLAENGE - praefix.length());
            assertEquals(List.of(Art.PRAEFIX_TOKEN), arten("Token " + praefix + rest), praefix.length() + " Zeichen");
            assertEquals(List.of(), arten("Token " + praefix + rest.substring(1)),
                    "eins zu kurz, Praefixlaenge " + praefix.length());
            assertEquals(List.of(Art.PRAEFIX_TOKEN), arten(SynthetischeGeheimnisse.praefixToken(praefix)),
                    "praefixToken, Praefixlaenge " + praefix.length());
        }
    }

    @Test
    void selbsttest_praefixToken_zerlegtOderAlleinOderMitAnfuehrungszeichenIstKeinVerstoss() {
        assertEquals(9, SynthetischeGeheimnisse.praefixe().size());
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            assertEquals(List.of(), arten("List.of(\"" + praefix + "\", \"rest\")"), praefix.length() + " Zeichen");
            assertEquals(List.of(), arten(praefix));
        }
    }

    @Test
    void selbsttest_mehrereArtenInEinerZeile_beideGemeldet() {
        String praefix = SynthetischeGeheimnisse.praefixe().get(0);
        String zeile = jwtForm() + " " + SynthetischeGeheimnisse.praefixToken(praefix);
        assertEquals(List.of(Art.JWT, Art.PRAEFIX_TOKEN), arten(zeile));
    }

    @Test
    void selbsttest_meldung_nenntDateiZeileUndArt_nieDenWert() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String text = "harmlos\n\nzeile 3: " + jwtForm() + "\r\nund " + kopf;
        List<String> meldungen = pruefeText("datei.txt", text);
        assertEquals(List.of("datei.txt:3: " + Art.JWT.beschreibung(),
                "datei.txt:4: " + Art.PEM_KOPF.beschreibung()), meldungen);
        for (String m : meldungen) {
            assertFalse(m.contains("abcdefgh"), "Meldung enthaelt Teile der Form");
            assertFalse(m.contains("-----"), "Meldung enthaelt Teile der Form");
        }
    }

    private static String escape(char zeichen) {
        return "\\" + "u" + String.format("%04x", (int) zeichen);
    }

    @Test
    void selbsttest_unicodeEscapeAmAnfang_wirdAufgeloestUndGemeldet() {
        String jwt = jwtForm();
        assertEquals(List.of(Art.JWT), arten(escape(jwt.charAt(0)) + jwt.substring(1)));
        assertEquals(List.of(Art.JWT), arten(jwt.substring(0, 2) + escape('J') + jwt.substring(3)));
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        assertEquals(List.of(Art.PRAEFIX_TOKEN), arten("x = \"" + escape(token.charAt(0)) + token.substring(1) + "\""));
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        assertEquals(List.of(Art.PEM_KOPF), arten(kopf.replace("BEGIN", escape('B') + "EGIN")));
        assertEquals(List.of(Art.PEM_KOPF), arten(kopf.replace("PRIVATE", escape('P') + "RIVATE")));
        assertEquals(List.of(), arten(escape('a') + "bc"));
    }

    private static byte[] utf16(String text, Charset zeichensatz, boolean bom) {
        byte[] rumpf = text.getBytes(zeichensatz);
        if (!bom) {
            return rumpf;
        }
        byte[] kopf = zeichensatz == StandardCharsets.UTF_16LE
                ? new byte[] {(byte) 0xFF, (byte) 0xFE} : new byte[] {(byte) 0xFE, (byte) 0xFF};
        byte[] ganz = new byte[kopf.length + rumpf.length];
        System.arraycopy(kopf, 0, ganz, 0, kopf.length);
        System.arraycopy(rumpf, 0, ganz, kopf.length, rumpf.length);
        return ganz;
    }

    @Test
    void selbsttest_utf16MitForm_wirdGemeldet_mitUndOhneBom() {
        String text = "erste Zeile\n" + jwtForm() + "\n";
        List<String> erwartet = List.of("d.txt:2: " + Art.JWT.beschreibung());
        assertEquals(erwartet, pruefeBytes("d.txt", utf16(text, StandardCharsets.UTF_16LE, true)));
        assertEquals(erwartet, pruefeBytes("d.txt", utf16(text, StandardCharsets.UTF_16BE, true)));
        assertEquals(erwartet, pruefeBytes("d.txt", utf16(text, StandardCharsets.UTF_16LE, false)));
        assertEquals(erwartet, pruefeBytes("d.txt", utf16(text, StandardCharsets.UTF_16BE, false)));
        assertEquals(List.of(), pruefeBytes("d.txt", utf16("harmlos\n", StandardCharsets.UTF_16LE, true)));
    }

    @Test
    void selbsttest_unbekannteBinaerdatei_wirdGemeldet_erlaubteEndungUebersprungen() {
        byte[] bytes = {1, 0, 2, 0, 0, 3, 'a', 'b', 'c', 0, 4, 5, 6};
        assertEquals(List.of("blob.dat: Binaerdatei ohne erkennbare Textkodierung"), pruefeBytes("blob.dat", bytes));
        assertEquals(List.of("ohne-endung: Binaerdatei ohne erkennbare Textkodierung"),
                pruefeBytes("ohne-endung", bytes));
        for (String endung : BINAERENDUNGEN) {
            assertEquals(List.of(), pruefeBytes("bild." + endung.toUpperCase(Locale.ROOT), bytes), endung);
        }
        assertEquals(List.of(), pruefeBytes("klartext.dat", "nur Text".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void selbsttest_zentraleKlasse_bauteileSindKeineFormen() {
        assertEquals(List.of(), arten(SynthetischeGeheimnisse.MARKE));
        assertEquals(List.of(Art.JWT), arten(SynthetischeGeheimnisse.jwt()));
        assertEquals(List.of(Art.PEM_KOPF), arten(SynthetischeGeheimnisse.pemKopfzeile("EC PRIVATE KEY")));
        assertEquals(List.of(), arten(SynthetischeGeheimnisse.pemFusszeile("EC PRIVATE KEY")));
        assertEquals(List.of(), arten(SynthetischeGeheimnisse.T));
        assertEquals(List.of(), arten(SynthetischeGeheimnisse.hex64()));
        assertEquals(List.of(), arten(SynthetischeGeheimnisse.uuid()));
    }
}
