package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Testdaten sind synthetisch (T-20; AK-53). Der Test liest alle {@code *.java} unter {@code src/test/java}
 * (Surefire-Arbeitsverzeichnis ist das Modulverzeichnis) und setzt die Regeln des Plan-Nachtrags 21.11.2026 um.
 * Pfade, Namen und Personen prüft er nicht; dafür gilt die Durchsicht durch den reviewer.
 *
 * <p><b>E-Mail.</b> Eine E-Mail-ähnliche Zeichenfolge ist ein {@code @}, direkt gefolgt von einem Domain-Lauf aus
 * Labels (Buchstaben, Ziffern, {@code -}, {@code _}) mit mindestens einem Punkt; abschließende Punkte und
 * Bindestriche des Laufs zählen nicht, Groß/klein ist egal. Als Zeichenfolge gilt der lokale Teil (Buchstaben,
 * Ziffern, {@code . _ % + -}) vor dem {@code @}, das {@code @} und der Domain-Lauf. Zulässig ist sie, wenn
 * (a) das letzte Label {@code example}, {@code invalid}, {@code test} oder {@code localhost} lautet oder die Domain
 * auf {@code example.com}, {@code example.org} oder {@code example.net} endet (als ganze Labelfolge), oder
 * (b) das letzte Label kürzer als zwei Zeichen ist (Kunstproben der Erkenner-Tests; keine echte Endung ist
 * einbuchstabig), oder (c) sie in der geschlossenen Ausnahmeliste steht.
 *
 * <p><b>IPv4.</b> Geprüft wird jede alleinstehende gültige IPv4: eine maximale Kette aus Ziffern und Punkten (Punkte
 * am Anfang und Ende zählen nicht zur Kette) mit genau vier Blöcken, jeder numerisch 0 bis 255 (führende Nullen
 * erlaubt). Sie muss numerisch in 192.0.2.0/24, 198.51.100.0/24 oder 203.0.113.0/24 liegen. Ketten mit anderer
 * Blockzahl oder einem Block über 255 sind keine alleinstehende gültige Adresse und werden nicht geprüft.
 *
 * <p><b>Geschlossene Ausnahmeliste.</b> Jeder Eintrag ist eine exakte Zeichenfolge, so wie die Prüfung sie aus dem
 * Quelltext liest, mit Begründung. Jede nicht zulässige Zeichenfolge außerhalb der Liste macht den Test rot; ein
 * Eintrag, der im Quelltext nicht mehr als nicht zulässige Zeichenfolge vorkommt, ebenfalls (keine verwaisten
 * Ausnahmen). Diese Datei prüft sich selbst mit; nur die Zeilen der Ausnahmeliste (zwischen den beiden
 * Markierungszeilen) sind ausgenommen, denn dort stehen die Einträge im Wortlaut. Beispiele für unzulässige
 * Zeichenfolgen stehen in den Selbsttests nur verkettet, nie als zusammenhängendes Literal.
 */
class TestdatenSynthetischTest {

    private static final Path QUELLE = Path.of("src", "test", "java");
    private static final String EIGENE_DATEI = "de/skillshield/core/TestdatenSynthetischTest.java";
    private static final String LISTE_ANFANG = "// AUSNAHMELISTE-BEGIN";
    private static final String LISTE_ENDE = "// AUSNAHMELISTE-ENDE";

    /** Untergrenzen (etwas unterhalb der gemessenen Werte), damit die Prüfung nicht ins Leere läuft. */
    private static final int MIN_DATEIEN = 45;
    private static final int MIN_EMAILS = 380;
    private static final int MIN_IPV4 = 300;

    private static final Set<String> RESERVIERTE_LABELS = Set.of("example", "invalid", "test", "localhost");
    private static final List<String> RESERVIERTE_ENDUNGEN = List.of("example.com", "example.org", "example.net");

    private static final Pattern AT_UND_DOMAIN = Pattern.compile("@([A-Za-z0-9_.-]+)");
    private static final Pattern IP_KETTE = Pattern.compile("[0-9.]+");

    // AUSNAHMELISTE-BEGIN
    private static final Map<String, String> AUSNAHMEN = Map.ofEntries(
        Map.entry("a@b.invalidC", "Grenzfallprobe: reservierte Endung mit angehaengtem Buchstaben (EmailRecognizer)"),
        Map.entry("erika.mustermann@example.invalidC",
            "Grenzfallprobe: reservierte Domain mit angehaengtem Buchstaben, erfundener Name"),
        Map.entry("d@example.invalid.erika",
            "Grenzfallprobe: reservierte Domain, dahinter ein weiteres Label (Mehrfachpunkt-Grenze)"),
        Map.entry("erika@praxis.example.max",
            "Grenzfallprobe: reserviertes Label example in der Mitte, dahinter ein erfundenes Label"),
        Map.entry("erika@example.invalid_x",
            "Grenzfallprobe: Unterstrich im letzten Label der reservierten Domain"),
        Map.entry("erika@example.invalid_",
            "Grenzfallprobe: Unterstrich am Ende des letzten Labels der reservierten Domain"),
        Map.entry("@gemeinschaftspraxis-dr-mustermann.allgemeinmedizin-musterstadt-nord",
            "Grenzfallprobe: Bruchstueck einer Probe mit ueberlangem Label, erfundene Praxis- und Ortsnamen"),
        Map.entry("nutzer@192.0.2.10",
            "Grenzfallprobe: Benutzerteil vor IP-Adresse im Dokumentationsbereich 192.0.2.0/24 (RFC 5737)"),
        Map.entry("geheim@192.0.2.10",
            "Grenzfallprobe: Userinfo vor IP-Adresse im Dokumentationsbereich 192.0.2.0/24 (RFC 5737)"));
    // AUSNAHMELISTE-ENDE

    /** Ergebnis der Prüfung eines oder mehrerer Texte. */
    record Ergebnis(List<String> verstoesse, Set<String> benutzteAusnahmen, int emails, int ipv4) {

        Ergebnis() {
            this(new ArrayList<>(), new LinkedHashSet<>(), 0, 0);
        }

        Ergebnis plus(Ergebnis o) {
            List<String> v = new ArrayList<>(verstoesse);
            v.addAll(o.verstoesse);
            Set<String> b = new LinkedHashSet<>(benutzteAusnahmen);
            b.addAll(o.benutzteAusnahmen);
            return new Ergebnis(v, b, emails + o.emails, ipv4 + o.ipv4);
        }
    }

    // ------------------------------------------------------------------ E-Mail

    /** Alle E-Mail-ähnlichen Zeichenfolgen einer Zeile (lokaler Teil, {@code @}, Domain-Lauf ohne Endpunkte). */
    static List<String> emailKandidaten(String zeile) {
        List<String> treffer = new ArrayList<>();
        Matcher m = AT_UND_DOMAIN.matcher(zeile);
        while (m.find()) {
            String domain = m.group(1);
            int ende = domain.length();
            while (ende > 0 && (domain.charAt(ende - 1) == '.' || domain.charAt(ende - 1) == '-')) {
                ende--;
            }
            domain = domain.substring(0, ende);
            if (domain.indexOf('.') < 0) {
                continue;
            }
            int anfang = m.start();
            while (anfang > 0 && istLokalesZeichen(zeile.charAt(anfang - 1))) {
                anfang--;
            }
            treffer.add(zeile.substring(anfang, m.start()) + "@" + domain);
        }
        return treffer;
    }

    private static boolean istLokalesZeichen(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_'
                || c == '%' || c == '+' || c == '-';
    }

    /** Regeln (a) und (b): zulässig ohne Ausnahmeliste. */
    static boolean emailZulaessigOhneListe(String zeichenfolge) {
        String domain = zeichenfolge.substring(zeichenfolge.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        String letztes = domain.substring(domain.lastIndexOf('.') + 1);
        if (RESERVIERTE_LABELS.contains(letztes)) {
            return true;
        }
        for (String endung : RESERVIERTE_ENDUNGEN) {
            if (domain.equals(endung) || domain.endsWith("." + endung)) {
                return true;
            }
        }
        return letztes.length() < 2;
    }

    // ------------------------------------------------------------------ IPv4

    /** Alle alleinstehenden gültigen IPv4-Adressen einer Zeile (siehe Klassenbeschreibung). */
    static List<String> ipv4Adressen(String zeile) {
        List<String> treffer = new ArrayList<>();
        Matcher m = IP_KETTE.matcher(zeile);
        while (m.find()) {
            String kette = m.group();
            int von = 0;
            int bis = kette.length();
            while (von < bis && kette.charAt(von) == '.') {
                von++;
            }
            while (bis > von && kette.charAt(bis - 1) == '.') {
                bis--;
            }
            kette = kette.substring(von, bis);
            if (istGueltigeIpv4(kette)) {
                treffer.add(kette);
            }
        }
        return treffer;
    }

    private static boolean istGueltigeIpv4(String kette) {
        String[] bloecke = kette.split("\\.", -1);
        if (bloecke.length != 4) {
            return false;
        }
        for (String b : bloecke) {
            if (b.isEmpty() || blockWert(b) > 255) {
                return false;
            }
        }
        return true;
    }

    /** Numerischer Wert eines Blocks aus Ziffern; führende Nullen zählen nicht, Werte über 255 liefern 256. */
    private static int blockWert(String block) {
        int i = 0;
        while (i < block.length() - 1 && block.charAt(i) == '0') {
            i++;
        }
        String ohneNullen = block.substring(i);
        return ohneNullen.length() > 3 ? 256 : Integer.parseInt(ohneNullen);
    }

    /** Liegt die (gültige) Adresse numerisch in einem der drei Dokumentationsbereiche (RFC 5737)? */
    static boolean ipv4InDokumentationsbereich(String ip) {
        String[] b = ip.split("\\.", -1);
        int a = blockWert(b[0]);
        int bb = blockWert(b[1]);
        int c = blockWert(b[2]);
        return (a == 192 && bb == 0 && c == 2) || (a == 198 && bb == 51 && c == 100)
                || (a == 203 && bb == 0 && c == 113);
    }

    // ------------------------------------------------------------------ Prüfung eines Textes

    /**
     * Prüft einen Quelltext zeilenweise. In der eigenen Datei sind die Zeilen zwischen den Markierungszeilen der
     * Ausnahmeliste ausgenommen.
     */
    static Ergebnis pruefe(String datei, String text, Set<String> ausnahmen) {
        String[] zeilen = text.split("\\R", -1);
        boolean ausgenommen = false;
        Ergebnis erg = new Ergebnis();
        for (int i = 0; i < zeilen.length; i++) {
            String zeile = zeilen[i];
            if (datei.equals(EIGENE_DATEI)) {
                if (zeile.trim().equals(LISTE_ANFANG)) {
                    ausgenommen = true;
                    continue;
                }
                if (zeile.trim().equals(LISTE_ENDE)) {
                    ausgenommen = false;
                    continue;
                }
                if (ausgenommen) {
                    continue;
                }
            }
            String ort = datei + ":" + (i + 1) + ": ";
            int emails = 0;
            for (String kandidat : emailKandidaten(zeile)) {
                emails++;
                if (emailZulaessigOhneListe(kandidat)) {
                    continue;
                }
                if (ausnahmen.contains(kandidat)) {
                    erg.benutzteAusnahmen().add(kandidat);
                } else {
                    erg.verstoesse().add(ort + "E-Mail-aehnlich, nicht zulaessig: " + kandidat);
                }
            }
            int ips = 0;
            for (String ip : ipv4Adressen(zeile)) {
                ips++;
                if (!ipv4InDokumentationsbereich(ip)) {
                    erg.verstoesse().add(ort + "IPv4 ausserhalb der Dokumentationsbereiche: " + ip);
                }
            }
            erg = erg.plus(new Ergebnis(new ArrayList<>(), new LinkedHashSet<>(), emails, ips));
        }
        return erg;
    }

    /** Ausnahmen, die im Quelltext nicht (mehr) als nicht zulässige Zeichenfolge vorkommen. */
    static Set<String> verwaiste(Set<String> ausnahmen, Set<String> benutzt) {
        Set<String> v = new LinkedHashSet<>(ausnahmen);
        v.removeAll(benutzt);
        return v;
    }

    // ------------------------------------------------------------------ Lesen der Quellen

    private static List<Path> testdateien() {
        assertTrue(Files.isDirectory(QUELLE), "Testquellverzeichnis fehlt: " + QUELLE.toAbsolutePath());
        try (Stream<Path> s = Files.walk(QUELLE)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")).sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relativ(Path datei) {
        return QUELLE.relativize(datei).toString().replace('\\', '/');
    }

    private static String lies(Path datei) {
        try {
            return Files.readString(datei, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Ergebnis pruefeAlle(List<Path> dateien) {
        Ergebnis gesamt = new Ergebnis();
        for (Path datei : dateien) {
            gesamt = gesamt.plus(pruefe(relativ(datei), lies(datei), AUSNAHMEN.keySet()));
        }
        return gesamt;
    }

    // ------------------------------------------------------------------ Prüfung des Testbestands

    @Test
    void ak53_alleTestdatenSindSynthetisch_emailsUndIpv4() {
        Ergebnis erg = pruefeAlle(testdateien());
        assertTrue(erg.verstoesse().isEmpty(), "Nicht synthetische Testdaten: " + erg.verstoesse());
    }

    @Test
    void ak53_ausnahmelisteHatKeineVerwaistenEintraege_undJederEintragHatBegruendung() {
        Ergebnis erg = pruefeAlle(testdateien());
        assertEquals(Set.of(), verwaiste(AUSNAHMEN.keySet(), erg.benutzteAusnahmen()),
                "Verwaiste Ausnahmen (im Quelltext nicht mehr vorhanden)");
        AUSNAHMEN.forEach((eintrag, begruendung) -> {
            assertFalse(begruendung.isBlank(), "Begruendung fehlt: " + eintrag);
            assertTrue(eintrag.contains("@"), "Kein E-Mail-Eintrag: " + eintrag);
            assertEquals(List.of(eintrag), emailKandidaten(eintrag), "Eintrag ist keine E-Mail-aehnliche Zeichenfolge");
            assertFalse(emailZulaessigOhneListe(eintrag), "Eintrag ist ohnehin zulaessig: " + eintrag);
        });
    }

    @Test
    void ak53_pruefungLaeuftNichtInsLeere() {
        List<Path> dateien = testdateien();
        assertTrue(dateien.size() >= MIN_DATEIEN, "Gelesene Dateien: " + dateien.size());
        Ergebnis erg = pruefeAlle(dateien);
        assertTrue(erg.emails() >= MIN_EMAILS, "Geprueft E-Mail-aehnliche Zeichenfolgen: " + erg.emails());
        assertTrue(erg.ipv4() >= MIN_IPV4, "Geprueft IPv4-Adressen: " + erg.ipv4());
        // Die eigene Datei ist dabei und enthaelt genau eine Ausnahmeliste
        assertTrue(dateien.stream().map(TestdatenSynthetischTest::relativ).toList().contains(EIGENE_DATEI));
        String eigener = lies(QUELLE.resolve(EIGENE_DATEI));
        assertEquals(1, zaehleZeilen(eigener, LISTE_ANFANG));
        assertEquals(1, zaehleZeilen(eigener, LISTE_ENDE));
    }

    /** Anzahl der Zeilen, die (ohne Randleerraum) genau {@code inhalt} sind. */
    private static int zaehleZeilen(String text, String inhalt) {
        int n = 0;
        for (String zeile : text.split("\\R", -1)) {
            if (zeile.trim().equals(inhalt)) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ Selbsttests: E-Mail

    @Test
    void selbsttest_email_zulaessigNachRegelA() {
        for (String z : List.of("erika@example.invalid", "a@srv.example", "a@x.test", "a@x.localhost",
                "A@Example.ORG", "a@sub.example.com", "a@example.net.", "x@y.invalid-", "a.b+c_d%e@x.INVALID",
                "a@example.net", "a@b.example.org")) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertTrue(emailZulaessigOhneListe(k.get(0)), "sollte zulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_zulaessigNachRegelB() {
        for (String z : List.of("a@b.c", "a@b.c.", "x@y.z-", "x@a.b.c")) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertTrue(emailZulaessigOhneListe(k.get(0)), "sollte zulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_unzulaessigOhneListe() {
        List<String> beispiele = List.of(
                "name@" + "firma.de", "n@" + "gmail.com", "n@" + "myexample.com", "n@" + "example.com.de",
                "n@" + "x.invalidC", "n@" + "x.example.max", "n@" + "a.b.c.de", "n@" + "sub.web.org",
                "n@" + "x.invalid.erika", "n@" + "x.testx", "n@" + "x.localhost1");
        for (String z : beispiele) {
            List<String> k = emailKandidaten(z);
            assertEquals(1, k.size(), z);
            assertEquals(z, k.get(0));
            assertFalse(emailZulaessigOhneListe(k.get(0)), "sollte unzulaessig sein: " + z);
        }
    }

    @Test
    void selbsttest_email_keineKandidaten() {
        assertEquals(List.of(), emailKandidaten("@" + " param x.y"));
        assertEquals(List.of(), emailKandidaten("x@" + "ohnepunkt"));
        assertEquals(List.of(), emailKandidaten("x@" + "a."));
        assertEquals(List.of(), emailKandidaten("x@" + "a.-"));
        assertEquals(List.of(), emailKandidaten("n@\"" + "x.de"));
        assertEquals(List.of(), emailKandidaten("@"));
    }

    @Test
    void selbsttest_email_zeichenfolgeEnthaeltLokalenTeilUndEndet_ohneSatzzeichen() {
        assertEquals(List.of("a.b@x.invalid"), emailKandidaten("Mail: \"a.b@x.invalid\", Ende."));
        assertEquals(List.of("a@b.c", "d@e.invalid"), emailKandidaten("a@b.c d@e.invalid."));
    }

    @Test
    void selbsttest_pruefung_meldetDateiZeileUndZeichenfolge() {
        String text = "ok\nn@" + "gmail.com\nok\n";
        Ergebnis erg = pruefe("a/B.java", text, Set.of());
        assertEquals(List.of("a/B.java:2: E-Mail-aehnlich, nicht zulaessig: n@" + "gmail.com"), erg.verstoesse());
        assertEquals(1, erg.emails());
    }

    @Test
    void selbsttest_ausnahmeliste_istGeschlossen_undErkenntVerwaiste() {
        String zeichenfolge = "n@" + "x.invalidC";
        String text = "a\n" + zeichenfolge + "\n";
        // nicht in der Liste: Verstoss
        assertEquals(1, pruefe("a/B.java", text, Set.of()).verstoesse().size());
        // in der Liste: kein Verstoss, Eintrag benutzt
        Ergebnis mit = pruefe("a/B.java", text, Set.of(zeichenfolge));
        assertEquals(List.of(), mit.verstoesse());
        assertEquals(Set.of(zeichenfolge), mit.benutzteAusnahmen());
        assertEquals(Set.of(), verwaiste(Set.of(zeichenfolge), mit.benutzteAusnahmen()));
        // ein anderer Eintrag deckt die Zeichenfolge nicht ab (exakte Zeichenfolge) und ist verwaist
        Ergebnis andere = pruefe("a/B.java", text, Set.of("n@" + "x.invalidD"));
        assertEquals(1, andere.verstoesse().size());
        assertEquals(Set.of("n@" + "x.invalidD"), verwaiste(Set.of("n@" + "x.invalidD"), andere.benutzteAusnahmen()));
        // Eintrag, der gar nicht im Text steht, ist verwaist
        assertEquals(Set.of(zeichenfolge), verwaiste(Set.of(zeichenfolge), Set.of()));
        // zulaessige Zeichenfolgen verbrauchen keine Ausnahme
        assertEquals(Set.of(), pruefe("a/B.java", "a@x.invalid\n", Set.of("a@x.invalid")).benutzteAusnahmen());
    }

    @Test
    void selbsttest_eigeneDatei_nurDieAusnahmelistenzeilenSindAusgenommen() {
        String unzulaessig = "n@" + "gmail.com";
        String text = String.join("\n", unzulaessig, LISTE_ANFANG, unzulaessig, LISTE_ENDE, unzulaessig);
        Ergebnis erg = pruefe(EIGENE_DATEI, text, Set.of());
        assertEquals(2, erg.verstoesse().size());
        assertTrue(erg.verstoesse().get(0).contains(":1: "));
        assertTrue(erg.verstoesse().get(1).contains(":5: "));
        // in jeder anderen Datei gibt es keine Ausnahmezeilen
        assertEquals(3, pruefe("a/B.java", text, Set.of()).verstoesse().size());
    }

    // ------------------------------------------------------------------ Selbsttests: IPv4

    @Test
    void selbsttest_ipv4_innerhalbDerDokumentationsbereiche() {
        for (String ip : List.of("192.0.2.0", "192.0.2.255", "198.51.100.7", "203.0.113.99", "192.000.002.004",
                "203.0.113.099", "x 192.0.2.1:80", "192.0.2.1.", "(198.51.100.1)")) {
            List<String> a = ipv4Adressen(ip);
            assertEquals(1, a.size(), ip);
            assertTrue(ipv4InDokumentationsbereich(a.get(0)), "sollte innerhalb liegen: " + ip);
        }
    }

    @Test
    void selbsttest_ipv4_ausserhalb() {
        for (String ip : List.of("8.8." + "8.8", "1.2." + "3.4", "10.0." + "0.1", "192.0." + "3.1", "192.1." + "2.1",
                "198.51." + "101.1", "203.0." + "114.1", "127.0." + "0.1", "255.255." + "255.255", "0.0." + "0.0",
                "193.0." + "2.1")) {
            List<String> a = ipv4Adressen(ip);
            assertEquals(1, a.size(), ip);
            assertFalse(ipv4InDokumentationsbereich(a.get(0)), "sollte ausserhalb liegen: " + ip);
        }
    }

    @Test
    void selbsttest_ipv4_keineAlleinstehendeGueltigeAdresse_wirdNichtGeprueft() {
        // Kette mit fuenf Bloecken, vier Bloecken plus Rest, drei Bloecke
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.4.5"));
        assertEquals(List.of(), ipv4Adressen("8.8." + "8.8.8"));
        assertEquals(List.of(), ipv4Adressen("8.8." + "8"));
        assertEquals(List.of(), ipv4Adressen("8." + "8"));
        // Block ueber 255
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.256"));
        assertEquals(List.of(), ipv4Adressen("300.1." + "1.1"));
        assertEquals(List.of(), ipv4Adressen("1.2." + "3.1000"));
        // leere Bloecke
        assertEquals(List.of(), ipv4Adressen("1.." + "2.3"));
        // Ziffernreihe ohne Punkt
        assertEquals(List.of(), ipv4Adressen("12345678"));
    }

    @Test
    void selbsttest_ipv4_fuehrendeNullenSindErlaubt_undZaehlenNumerisch() {
        assertEquals(List.of("192.00.002.004"), ipv4Adressen("a 192.00.002.004 b"));
        assertTrue(ipv4InDokumentationsbereich("192.00.002.004"));
        String vierte = "0001.0002." + "0003.0004";
        assertEquals(List.of(vierte), ipv4Adressen(vierte));
        assertFalse(ipv4InDokumentationsbereich(vierte));
        // ein Block mit vielen Nullen und Wert ueber 255 bleibt ungueltig
        assertEquals(List.of(), ipv4Adressen("0001.0002.0003.0256"));
        String grenze = "0001.0002." + "0003.0255";
        assertEquals(List.of(grenze), ipv4Adressen(grenze));
    }

    @Test
    void selbsttest_ipv4_pruefungMeldetDateiZeileUndAdresse() {
        Ergebnis erg = pruefe("a/B.java", "a\nb 8.8." + "8.8 c\n", Set.of());
        assertEquals(List.of("a/B.java:2: IPv4 ausserhalb der Dokumentationsbereiche: 8.8." + "8.8"),
                erg.verstoesse());
        assertEquals(1, erg.ipv4());
        assertEquals(List.of(), pruefe("a/B.java", "x 192.0.2.4:/x\n", Set.of()).verstoesse());
    }
}
