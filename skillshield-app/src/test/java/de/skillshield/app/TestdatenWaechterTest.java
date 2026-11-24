package de.skillshield.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Testdaten-Waechter (AK-56): Die Testquellen des Moduls enthalten keine Ziffernkette ab neun Ziffern (auch
 * gruppiert), keine E-Mail-Adresse ausserhalb reservierter Domains und keine IPv4-Adresse ausserhalb der
 * Dokumentationsnetze. KV-Nummern kommen ausschliesslich aus SynthetischeKvNummern des Kerns. Geprueft werden
 * src/test/java und src/test/resources. Grenzen: zusammengesetzte Zeichenketten, andere Trenner als Leerzeichen,
 * Punkt, Bindestrich, Schraegstrich und Unterstrich, IPv6-Adressen sowie Namen, Geburtsdaten und Adressen
 * erkennt der Test nicht; dafuer gilt die Review-Pflicht.
 */
class TestdatenWaechterTest {

    private static final Path QUELLE = Path.of("src", "test", "java");

    private static final Path RESSOURCEN = Path.of("src", "test", "resources");
    private static final Path POM = Path.of("pom.xml");

    private static final Pattern ZIFFERNKETTE = Pattern.compile("\\d(?:[ .\\-/_\\u00A0]?\\d){8,}");
    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+)");
    private static final Pattern IPV4 =
            Pattern.compile("(?<![\\d.])\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?![\\d.])");
    private static final List<String> ERLAUBTE_DOMAINS =
            List.of("example.org", "example.com", "example.net", "example.invalid");
    private static final List<String> ERLAUBTE_NETZE = List.of("192.0.2.", "198.51.100.", "203.0.113.");

    static List<String> verstoesse(String datei, String text) {
        List<String> liste = new ArrayList<>();
        if (ZIFFERNKETTE.matcher(text).find()) {
            liste.add(datei + ": Ziffernkette ab neun Ziffern");
        }
        Matcher mail = EMAIL.matcher(text);
        while (mail.find()) {
            String domain = mail.group(1).toLowerCase(Locale.ROOT);
            if (ERLAUBTE_DOMAINS.stream().noneMatch(d -> domain.equals(d) || domain.endsWith("." + d))) {
                liste.add(datei + ": E-Mail-Domain ausserhalb der reservierten Domains");
            }
        }
        Matcher ip = IPV4.matcher(text);
        while (ip.find()) {
            String adresse = ip.group();
            if (ERLAUBTE_NETZE.stream().noneMatch(adresse::startsWith)) {
                liste.add(datei + ": IPv4-Adresse ausserhalb der Dokumentationsnetze");
            }
        }
        return liste;
    }

    /** Alle Dateien unter der Wurzel (fehlendes Verzeichnis = leer); Quelltext nur .java, Ressourcen alle. */
    private static List<Path> dateien(Path wurzel, boolean nurJava) {
        assertTrue(Files.isRegularFile(POM), "pom.xml nicht im Arbeitsverzeichnis: Test laeuft am falschen Ort");
        if (!Files.isDirectory(wurzel)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(wurzel)) {
            return s.filter(p -> Files.isRegularFile(p) && (!nurJava || p.toString().endsWith(".java")))
                    .sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void testquellenEnthaltenNurSynthetischeDatenmuster() throws IOException {
        List<Path> pruefen = new ArrayList<>(dateien(QUELLE, true));
        pruefen.addAll(dateien(RESSOURCEN, false));
        List<String> alle = new ArrayList<>();
        for (Path datei : pruefen) {
            String text = new String(Files.readAllBytes(datei), StandardCharsets.UTF_8);
            alle.addAll(verstoesse(datei.getFileName().toString(), text));
        }
        assertEquals(List.of(), alle);
    }

    @Test
    void selbsttest_ziffernketteWirdAuchGruppiertErkannt() {
        assertEquals(1, verstoesse("A.java", String.join("", "123", "456", "789")).size());
        assertEquals(1, verstoesse("A.java", String.join(" ", "123", "456", "789")).size());
        assertEquals(1, verstoesse("A.java", String.join(".", "123", "456", "789")).size());
        assertEquals(1, verstoesse("A.java", String.join("-", "123", "456", "789")).size());
        assertEquals(1, verstoesse("A.java", String.join("/", "123", "456", "789")).size());
        assertTrue(verstoesse("A.java", String.join("", "1234", "5678")).isEmpty(), "acht Ziffern");
        assertEquals(1, verstoesse("A.java", String.join("_", "123", "456", "789")).size(), "Zahlliteral");
        assertTrue(verstoesse("A.java", "int n = 1_000_000;").isEmpty(), "sieben Ziffern");
    }

    @Test
    void selbsttest_emailUndIpWerdenNurInReserviertenBereichenZugelassen() {
        String at = "@";
        assertTrue(verstoesse("A.java", "mustermann" + at + "example.org").isEmpty());
        assertEquals(1, verstoesse("A.java", "max" + at + "firma-xyz.de").size());
        String doku = String.join(".", "192", "0", "2", "10");
        String fremd = String.join(".", "10", "20", "30", "40");
        assertTrue(verstoesse("A.java", doku).isEmpty());
        assertEquals(1, verstoesse("A.java", fremd).size());
    }
}
