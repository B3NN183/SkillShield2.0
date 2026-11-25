package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Waechter fuer die Korpusdateien unter {@code src/test/resources/de/skillshield/core/geheimnis/} (Plan
 * geheimnisse-erkennung, Kapitel 6.2; Anforderung AK-49): das Referenz-Ticket und die spaeter folgenden Korpora.
 * Jede Datei dort muss
 * <ul>
 * <li>strikt als UTF-8 lesbar sein und darf nicht mit einem BOM beginnen ({@link SynthetischeGeheimnisse#dekodiere});
 * CR LF und CR werden dabei in LF gewandelt, die Pruefung haengt also nicht von der Checkout-Einstellung ab;</li>
 * <li>E-Mail-Adressen nur mit reservierter Endung enthalten (Regel (a) und (b) von {@code TestdatenSynthetischTest},
 * deren Hilfsmethoden hier benutzt werden); ein {@code @} ohne lokalen Teil (zum Beispiel in einer JDBC-Zeichenfolge)
 * braucht ebenfalls eine reservierte Endung oder die Endung {@code .local};</li>
 * <li>IPv4-Adressen nur aus den drei Dokumentationsnetzen enthalten;</li>
 * <li>keine Form nach AK-50 enthalten (JWT, privater PEM-Kopf, Praefix-Token; Regel von
 * {@code GeheimnisLiteralWaechterTest}).</li>
 * </ul>
 * Ziffernketten prueft der bestehende Waechter {@code TestdatenSynthetischTest}, der auch die Ressourcen liest; er
 * bleibt unveraendert und bekommt keine Ausnahme. Meldungen nennen Datei, Zeile und Art, nie den Wert.
 *
 * <p>Grenze: Teilwoerter von Echtdaten (Namen, Domaenen) prueft der Waechter nicht; sie stehen nur dort, wo die
 * Anforderung sie vorgibt ({@code kvbsp.local}, {@code kv-beispiel.example}, Referenz-Ticket), und werden vom reviewer
 * durchgesehen.
 */
class GeheimnisKorpusWaechterTest {

    private static final Path KORPUS = Path.of("src", "test", "resources", "de", "skillshield", "core", "geheimnis");

    /** Prueft einen (schon dekodierten, LF-normalisierten) Text; Meldungen ohne Zeileninhalt. */
    static List<String> pruefeText(String datei, String text) {
        List<String> meldungen = new ArrayList<>();
        String[] zeilen = text.split("\n", -1);
        for (int i = 0; i < zeilen.length; i++) {
            String ort = datei + ":" + (i + 1) + ": ";
            for (String kandidat : TestdatenSynthetischTest.emailKandidaten(zeilen[i])) {
                boolean zulaessig = TestdatenSynthetischTest.emailZulaessigOhneListe(kandidat)
                        || (kandidat.startsWith("@") && kandidat.toLowerCase(Locale.ROOT).endsWith(".local"));
                if (!zulaessig) {
                    meldungen.add(ort + "E-Mail-Adresse mit nicht reservierter Endung");
                }
            }
            for (String ip : TestdatenSynthetischTest.ipv4Adressen(zeilen[i])) {
                if (!TestdatenSynthetischTest.ipv4InDokumentationsbereich(ip)) {
                    meldungen.add(ort + "IPv4 ausserhalb der Dokumentationsnetze");
                }
            }
            for (GeheimnisLiteralWaechterTest.Art art : GeheimnisLiteralWaechterTest.arten(zeilen[i])) {
                meldungen.add(ort + art.beschreibung());
            }
        }
        return meldungen;
    }

    private static List<Path> korpusDateien() {
        assertTrue(Files.isDirectory(KORPUS), "Korpusverzeichnis fehlt");
        try (Stream<Path> s = Files.walk(KORPUS)) {
            return s.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ Lauf ueber die Korpusdateien

    @Test
    void korpusdateien_strengesUtf8OhneBom_keineVerbotenenFormen() throws IOException {
        List<Path> dateien = korpusDateien();
        assertTrue(dateien.stream().anyMatch(p -> p.getFileName().toString().equals("referenz-ticket.txt")),
                "das Referenz-Ticket fehlt im Korpusverzeichnis");
        List<String> meldungen = new ArrayList<>();
        for (Path datei : dateien) {
            String name = datei.getFileName().toString();
            String text = SynthetischeGeheimnisse.dekodiere(Files.readAllBytes(datei), name);
            meldungen.addAll(pruefeText(name, text));
        }
        assertEquals(List.of(), meldungen);
    }

    // ------------------------------------------------------------------ Selbsttests

    @Test
    void selbsttest_email_reservierteEndungErlaubt_andereGemeldet() {
        assertEquals(List.of(), pruefeText("d", "erika" + "@" + "kv-beispiel.example"));
        assertEquals(List.of(), pruefeText("d", "erika" + "@" + "host.invalid und x" + "@" + "y.localhost"));
        assertEquals(List.of("d:2: E-Mail-Adresse mit nicht reservierter Endung"),
                pruefeText("d", "ok\nerika" + "@" + "praxis-beispiel" + "." + "de"));
        assertEquals(List.of("d:1: E-Mail-Adresse mit nicht reservierter Endung"),
                pruefeText("d", "erika" + "@" + "praxis-beispiel.local"));
    }

    @Test
    void selbsttest_email_atOhneLokalenTeil_brauchtReservierteEndungOderLocal() {
        String jdbc = "Verbindung: jdbc:oracle:thin:" + "@" + "dbsrv-ora01.kvbsp.local:1521/ABRPROD";
        assertEquals(List.of(), pruefeText("d", jdbc));
        assertEquals(List.of(), pruefeText("d", "jdbc:x:" + "@" + "host.example:1521/ABC"));
        String fremd = "jdbc:x:" + "@" + "praxis-beispiel" + "." + "de" + ":1521/ABC";
        assertEquals(List.of("d:1: E-Mail-Adresse mit nicht reservierter Endung"), pruefeText("d", fremd));
    }

    @Test
    void selbsttest_ipv4_nurDokumentationsnetze() {
        assertEquals(List.of(), pruefeText("d", "192.0.2.47 und 198.51.100.7 und 203.0.113.9"));
        assertEquals(List.of("d:1: IPv4 ausserhalb der Dokumentationsnetze"),
                pruefeText("d", "Server 10." + "20.30.47 antwortet"));
        assertEquals(List.of(), pruefeText("d", "Version 4.2 und 4.2.1 und 0391 123-4567"));
    }

    @Test
    void selbsttest_formenNachAk50_werdenGemeldet_ohneWert() {
        String jwt = SynthetischeGeheimnisse.jwt();
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("RSA PRIVATE KEY");
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        List<String> meldungen = pruefeText("d", "a " + jwt + "\n" + kopf + "\nb " + token);
        assertEquals(List.of("d:1: " + GeheimnisLiteralWaechterTest.Art.JWT.beschreibung(),
                "d:2: " + GeheimnisLiteralWaechterTest.Art.PEM_KOPF.beschreibung(),
                "d:3: " + GeheimnisLiteralWaechterTest.Art.PRAEFIX_TOKEN.beschreibung()), meldungen);
        for (String m : meldungen) {
            assertFalse(m.contains(SynthetischeGeheimnisse.MARKE), "Meldung enthaelt Teile eines Werts");
        }
    }

    @Test
    void selbsttest_leser_lfBleibt_crlfUndCrWerdenLf() {
        byte[] bytes = "eins\r\nzwei\rdrei\nvier".getBytes(StandardCharsets.UTF_8);
        String gelesen = SynthetischeGeheimnisse.dekodiere(bytes, "probe");
        assertTrue("eins\nzwei\ndrei\nvier".equals(gelesen), "Normalisierung");
    }

    @Test
    void selbsttest_leser_umlauteBleibenErhalten() {
        String text = "Krueger " + (char) 0xFC + (char) 0xE4 + (char) 0xDF + " " + (char) 0x2013;
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        assertTrue(text.equals(SynthetischeGeheimnisse.dekodiere(bytes, "probe")), "Umlaute");
    }

    @Test
    void selbsttest_leser_bomWirdAbgelehnt_meldungOhneInhalt() {
        byte[] bytes = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', 'b'};
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SynthetischeGeheimnisse.dekodiere(bytes, "probe.txt"));
        assertEquals("Korpusdatei beginnt mit einem BOM: probe.txt", e.getMessage());
    }

    @Test
    void selbsttest_leser_ungueltigesUtf8WirdAbgelehnt_meldungOhneInhalt() {
        byte[] bytes = {'a', (byte) 0xC3, (byte) 0x28, 'b'};
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SynthetischeGeheimnisse.dekodiere(bytes, "probe.txt"));
        assertEquals("Korpusdatei ist kein gueltiges UTF-8: probe.txt", e.getMessage());
    }

    @Test
    void selbsttest_korpusZeile_kommentareLeerzeilenUndUmbrueche() {
        String text = "# Kategorie: ZUSTAND\n\nPasswort zur" + (char) 0xFC + "ckgesetzt\r\n# Kommentar\n"
                + "zeile\\neins\\tzwei\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        List<String> faelle = SynthetischeGeheimnisse.faelleAusText(SynthetischeGeheimnisse.dekodiere(bytes, "p"));
        assertEquals(2, faelle.size());
        assertTrue(("Passwort zur" + (char) 0xFC + "ckgesetzt").equals(faelle.get(0)), "Fall 1");
        assertTrue("zeile\neins\tzwei".equals(faelle.get(1)), "Ersetzung der Zeichenpaare");
    }
}
