package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Alle gültigen Testadressen liegen in den Dokumentationsbereichen 192.0.2.0/24, 198.51.100.0/24 oder 203.0.113.0/24
 * (auch in Laufzeittests). Nur ungültige Ketten (Block über 255 oder mit mehr als drei Ziffern) dürfen andere Zahlen
 * enthalten. Ausnahme sind Ketten mit mehr als vier Blöcken: Ihre inneren Vierergruppen ergeben zwangsläufig auch
 * andere Zahlen ({@code 19.11.2026.192.0.2.10.52344}; Datum und Port sind synthetisch). Lange Ketten für die
 * Laufzeit bestehen aus einem gültigen Rand und Blöcken {@code 9999}, damit keine vier benachbarten Blöcke außerhalb
 * der Bereiche eine Adresse ergeben. Weitere Ausnahme laut Plan 4a: die rein künstliche Laufzeit-Eingabe
 * {@code "1.".repeat(100_000)}.
 */
class Ipv4RecognizerTest {

    private static final Ipv4Recognizer R = new Ipv4Recognizer();
    private static final long LIMIT_NANOS = 2_000_000_000L;

    private static List<String> werte(String text) {
        List<Finding> funde = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue());
            assertEquals(FindingType.IP, f.type());
            assertEquals("ipv4", f.source());
            assertEquals(0.85, f.confidence());
            assertEquals(ConfidenceLevel.WAHRSCHEINLICH, f.level());
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

    // ---- AK-4

    @Test
    void ak4BeispielLiefertEinenFund() {
        String text = "Server 192.0.2.10 antwortet nicht";
        List<Finding> funde = R.detect(text);
        assertEquals(1, funde.size());
        assertEquals("192.0.2.10", funde.get(0).originalValue());
        assertEquals(7, funde.get(0).start());
        assertEquals(17, funde.get(0).end());
        assertEinFund(text, "192.0.2.10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"300.1.1.1", "192.0.2", "1.2.3.4567", "999.1.1.1", "256.0.0.1", "192.0.2.256",
            "1.2.3.", "1.2..3.4", "192.0.2.0010", "300.300.1.300.300", "192.0.2.300.1.2.300"})
    void ungueltigeKettenLiefernKeinenFund(String text) {
        assertKeinFund(text);
        assertKeinFund("Wert " + text + " Ende");
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.1", "198.51.100.77", "203.0.113.255", "192.0.2.0", "192.0.2.255",
            "198.51.100.255", "203.0.113.0", "198.51.100.100", "203.0.113.249"})
    void gueltigeAdressenWerdenErkannt(String ip) {
        assertEinFund(ip, ip);
        assertEinFund("Server " + ip + " antwortet", ip);
    }

    @Test
    void dokumentationsbereicheAlleDrei() {
        assertEquals(List.of("192.0.2.1", "198.51.100.2", "203.0.113.3"),
                werte("a 192.0.2.1, b 198.51.100.2; c 203.0.113.3"));
    }

    // ---- Führende Nullen (Nutzerentscheidung): ein Block hat 1 bis 3 Ziffern und Wert höchstens 255

    @Test
    void fuehrendeNullenSindErlaubt() {
        assertEinFund("192.0.2.010", "192.0.2.010");
        assertEinFund("192.000.002.010", "192.000.002.010");
        assertEinFund("Host 198.51.100.007 aus", "198.51.100.007");
        assertEinFund("203.0.113.00", "203.0.113.00");
        assertEinFund("192.0.2.000.", "192.0.2.000");
    }

    @Test
    void vierZiffernImBlockSindUngueltigAuchMitNullen() {
        assertKeinFund("192.0.2.0010");
        assertKeinFund("0192.0.2.10");
        assertKeinFund("192.0000.2.10");
    }

    @Test
    void dreiZiffernMitWertUeberGrenzeSindUngueltig() {
        assertKeinFund("192.0.2.256");
        assertKeinFund("192.0.2.999");
        assertEinFund("192.0.2.255", "192.0.2.255");
    }

    // ---- Mehr als vier Blöcke (Nutzerentscheidung, Plan 3a): ganze Kette, wenn irgendwo vier gültige Blöcke in Folge stehen

    @Test
    void fuenfBloeckeMitGueltigemAnfangSindEinFund() {
        assertEinFund("192.0.2.10.5", "192.0.2.10.5");
        assertEinFund("Wert 192.0.2.10.5 Ende", "192.0.2.10.5");
        assertEinFund("Version 192.0.2.1.5", "192.0.2.1.5");
    }

    @Test
    void adresseMitAngehaengtemPortIstEinFund() {
        assertEinFund("192.0.2.10.52344", "192.0.2.10.52344");
        assertEinFund("Verbindung 192.0.2.10.52344 steht", "192.0.2.10.52344");
    }

    @Test
    void tcpdumpFormLiefertZweiFundeUeberJeDieGanzeKette() {
        assertEquals(List.of("192.0.2.10.52344", "198.51.100.7.443"),
                werte("IP 192.0.2.10.52344 > 198.51.100.7.443"));
        assertEquals(List.of("192.0.2.10.52344", "198.51.100.7.443"),
                werte("12:00:01.123456 IP 192.0.2.10.52344 > 198.51.100.7.443: Flags [S], seq 0"));
    }

    @Test
    void gueltigesEndeMitUngueltigemAnfangIstEinFund() {
        assertEinFund("300.192.0.2.10", "300.192.0.2.10");
        assertEinFund("300.300.192.0.2.10", "300.300.192.0.2.10");
        assertEinFund("Knoten 9999.198.51.100.7 an", "9999.198.51.100.7");
    }

    @Test
    void gueltigerAnfangMitUngueltigemEndeIstEinFund() {
        assertEinFund("192.0.2.10.300", "192.0.2.10.300");
        assertEinFund("192.0.2.10.300.300", "192.0.2.10.300.300");
        assertEinFund("198.51.100.7.9999", "198.51.100.7.9999");
    }

    @Test
    void sechsUndMehrBloeckeMitGueltigemRand() {
        assertEinFund("192.0.2.10.5.7", "192.0.2.10.5.7");
        assertEinFund("192.0.2.10.5.7.9999", "192.0.2.10.5.7.9999");
        assertEinFund("192.0.2.1.9999.9999.9999.9999.9999", "192.0.2.1.9999.9999.9999.9999.9999");
        assertEinFund("300.300.300.192.0.2.10", "300.300.300.192.0.2.10");
    }

    @Test
    void adresseInDerMitteEinerKetteIstEinFundUeberDieGanzeKette() {
        assertEinFund("999.192.0.2.10.999", "999.192.0.2.10.999");
        assertEinFund("300.192.0.2.10.300", "300.192.0.2.10.300");
        assertEinFund("2026.11.19.192.0.2.10.52344", "2026.11.19.192.0.2.10.52344");
        assertEinFund("19.11.2026.192.0.2.10.52344", "19.11.2026.192.0.2.10.52344");
        assertEinFund("v1.300.192.0.2.10.70000", "1.300.192.0.2.10.70000");
        assertEinFund("1234.192.0.2.10.5678", "1234.192.0.2.10.5678");
        assertEinFund("Az 2026.192.0.2.10.443 x", "2026.192.0.2.10.443");
        assertEinFund("999.192.0.2.10.999.", "999.192.0.2.10.999");
    }

    @Test
    void langeKetteOhneVierAufeinanderfolgendeGueltigeBloeckeIstKeinFund() {
        assertKeinFund("300.300.1.300.300");
        assertKeinFund("192.0.2.300.1.2.300");
        assertKeinFund("192.0.2.300.300.300.300.300.198.51.100.300");
        assertKeinFund("9999.9999.9999.9999.9999");
        assertKeinFund("1.2.3.9999.4.5.6.9999.7.8.9");
        assertKeinFund("999.192.0.2.999.198.51.100.999");
        assertKeinFund("9999.192.0.2.9999");
    }

    @Test
    void genauVierBloeckeMitUngueltigemBleibenKeinFund() {
        assertKeinFund("300.1.1.1");
        assertKeinFund("192.0.2.300");
        assertKeinFund("1.2.3.4567");
        assertKeinFund("192.0.2");
    }

    @Test
    void satzpunktNachLangerKetteGehoertNichtZumFund() {
        assertEinFund("Port 192.0.2.10.52344.", "192.0.2.10.52344");
        assertEinFund("Port 192.0.2.10.52344. Danach", "192.0.2.10.52344");
        assertEinFund("Quelle 192.0.2.10.5.7.", "192.0.2.10.5.7");
    }

    @Test
    void langeKettenWerdenNichtMitNachbarnVerschmolzen() {
        assertEquals(List.of("192.0.2.10.52344", "198.51.100.7"),
                werte("192.0.2.10.52344 198.51.100.7"));
        assertEquals(List.of("192.0.2.10.5", "198.51.100.7.443"),
                werte("192.0.2.10.5, 198.51.100.7.443"));
        assertEquals(List.of("192.0.2.10.5"), werte("300.1.1.1 192.0.2.10.5"));
    }

    // ---- Satzzeichen und Umgebung

    @Test
    void ipVorSatzpunktOhnePunkt() {
        assertEinFund("Erreichbar unter 192.0.2.10.", "192.0.2.10");
        assertEinFund("Erreichbar unter 192.0.2.10. Danach", "192.0.2.10");
        assertEinFund("192.0.2.10..", "192.0.2.10");
        assertEinFund("(192.0.2.10)", "192.0.2.10");
        assertEinFund("192.0.2.10, 5", "192.0.2.10");
        assertEinFund("192.0.2.10:8080", "192.0.2.10");
        assertEinFund("192.0.2.10/24", "192.0.2.10");
    }

    @Test
    void mehrereAdressenUndZeilenumbrueche() {
        assertEquals(List.of("192.0.2.1", "198.51.100.2", "203.0.113.3"),
                werte("192.0.2.1\n198.51.100.2\r\n\t203.0.113.3"));
        assertEquals(List.of("192.0.2.1", "192.0.2.2"), werte("192.0.2.1 192.0.2.2"));
        assertEquals(List.of("192.0.2.1", "192.0.2.2"), werte("192.0.2.1,192.0.2.2"));
    }

    // ---- Korrekturvermerk 2.3: Füllzeichen vor dem Wert verhindern den Fund nicht

    @Test
    void punktfuehrerVorDerAdresse() {
        assertEinFund("Server" + ".".repeat(60) + "192.0.2.10", "192.0.2.10");
        assertEinFund("IP:......198.51.100.1", "198.51.100.1");
        assertEinFund("....198.51.100.1", "198.51.100.1");
        assertEinFund(".198.51.100.1", "198.51.100.1");
        assertEinFund("Server" + ".".repeat(100_000) + "192.0.2.10", "192.0.2.10");
    }

    @Test
    void bindeUndUnterstrichVorDerAdresse() {
        assertEinFund("-198.51.100.1", "198.51.100.1");
        assertEinFund("_198.51.100.1", "198.51.100.1");
        assertEinFund("---198.51.100.1", "198.51.100.1");
        assertEinFund("___198.51.100.1", "198.51.100.1");
        assertEinFund("IP-" + "-".repeat(70) + "198.51.100.1", "198.51.100.1");
        assertEinFund("host_" + "_".repeat(70) + "198.51.100.1", "198.51.100.1");
        assertEinFund("=198.51.100.1", "198.51.100.1");
    }

    @Test
    void buchstabenAnDerAdresseVerhindernDenFundNicht() {
        assertEinFund("ip198.51.100.1", "198.51.100.1");
        assertEinFund("198.51.100.1abc", "198.51.100.1");
        assertEinFund("host-198.51.100.1-b", "198.51.100.1");
    }

    @Test
    void zifferAnschlussVerhindertDenFundBeiGenauVierBloecken() {
        assertKeinFund("110.0.0.1234");
        assertKeinFund("192.0.2.1234");
        assertKeinFund("1234.0.2.1");
    }

    @Test
    void adresseHinterAnderenZeichenketten() {
        assertEinFund("a@b.invalid 192.0.2.1", "192.0.2.1");
        assertEinFund("http://192.0.2.10/status", "192.0.2.10");
        assertEinFund("\\\\192.0.2.10\\freigabe", "192.0.2.10");
    }

    @Test
    void ungueltigeKetteNebenGueltigerAdresse() {
        assertEquals(List.of("198.51.100.1"), werte("300.1.1.1 198.51.100.1"));
        assertEquals(List.of("198.51.100.1"), werte("198.51.100.1 300.300.1.300.300"));
    }

    // ---- Randfälle

    @Test
    void leereUndKurzeEingaben() {
        assertKeinFund("");
        assertKeinFund(" ");
        assertKeinFund(".");
        assertKeinFund("1");
        assertKeinFund("1.");
        assertKeinFund("1.1");
        assertKeinFund("1.1.1");
        assertEinFund("192.0.2.1", "192.0.2.1");
        assertEinFund(" 192.0.2.1 ", "192.0.2.1");
    }

    @Test
    void ergebnisIstDeterministischUndUnabhaengigVomAufruf() {
        String text = "x 198.51.100.1 y 192.0.2.5";
        assertEquals(R.detect(text), R.detect(text));
        assertEquals(2, R.detect(text).size());
    }

    @Test
    void nullTextWirdMitParameternamenAbgelehnt() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> R.detect(null)).getMessage());
    }

    private static String cp(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    @Test
    void keineAbstuerzeBeiSurrogatenUndSonderzeichen() {
        String lone = String.valueOf((char) 0xD83D);
        String lone2 = String.valueOf((char) 0xDE00);
        assertDoesNotThrow(() -> R.detect("192.0.2.10 " + cp(0x1F600) + " " + lone + " 198.51.100.1 " + lone2));
    }

    @Test
    void nichtAsciiZiffernWerdenNichtAlsZiffernGelesen() {
        assertKeinFund(cp(0x661) + "." + cp(0x662) + "." + cp(0x663) + "." + cp(0x664));
        assertKeinFund("192.0.2." + cp(0x661));
        assertEinFund("192.0.2.10." + cp(0x661), "192.0.2.10");
    }

    @Test
    void surrogateUndSteuerzeichenBegrenzenDieAdresseNur() {
        assertEinFund(cp(0x1F600) + "198.51.100.1" + cp(0x1F600), "198.51.100.1");
        assertEinFund(cp(0xAD) + "198.51.100.1" + cp(0x200B), "198.51.100.1");
    }

    // ---- Laufzeit (AK-49, TE-7)

    @Test
    void pathologischeEingabenSindLinear() {
        // Lauter Blöcke 9999 (ungültig): eine einzige Kette ohne gültigen Rand, kein Fund.
        innerhalbLimit(() -> anzahl("9999.".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("1".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl(".".repeat(100_000)), 0);
        innerhalbLimit(() -> anzahl("192.0.2.1 ".repeat(100_000)), 100_000);
        // Gültiger Anfang, danach 100 000 ungültige Blöcke: ein Fund über die ganze Kette (Regel 3a: vier gültige Blöcke in Folge am Anfang).
        innerhalbLimit(() -> anzahl("192.0.2.1" + ".9999".repeat(100_000)), 1);
        // Gültiges Ende, davor 100 000 ungültige Blöcke: ein Fund über die ganze Kette (Regel 3a: vier gültige Blöcke in Folge am Ende).
        innerhalbLimit(() -> anzahl("9999.".repeat(100_000) + "192.0.2.1"), 1);
        innerhalbLimit(() -> anzahl("9999.".repeat(100_000) + " 192.0.2.1"), 1);
        innerhalbLimit(() -> anzahl("9".repeat(100_000) + ".192.0.2"), 0);
    }

    @Test
    void einsPunktWiederholtIstEinFundOhneSchlusspunkt() {
        // Ausnahme von der Testdaten-Regel laut Plan 4a: rein künstliche Laufzeit-Eingabe.
        String text = "1.".repeat(100_000);
        long t0 = System.nanoTime();
        List<Finding> funde = R.detect(text);
        long dauer = System.nanoTime() - t0;
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(0, f.start());
        assertEquals(text.length() - 1, f.end());
        assertEquals(text.substring(f.start(), f.end()), f.originalValue());
        assertTrue(dauer < LIMIT_NANOS, "Laufzeit " + dauer / 1_000_000 + " ms");
    }

    @Test
    void sehrLangeKetteMitGueltigemRandIstEinFundUndBrauchtKeineZwischenlisten() {
        String kette = "192.0.2.1" + ".9999".repeat(500_000);
        innerhalbLimit(() -> anzahl(kette), 1);
        assertEinFund("x " + kette + ". y", kette);
    }

    @Test
    void eineMillionZeichenInZweiSekunden() {
        innerhalbLimit(() -> anzahl("9999.".repeat(200_000)), 0);
        innerhalbLimit(() -> anzahl("192.0.2.1" + ".9999".repeat(200_000)), 1);
        innerhalbLimit(() -> anzahl("1".repeat(1_000_000)), 0);
        innerhalbLimit(() -> anzahl(".".repeat(1_000_000)), 0);
        innerhalbLimit(() -> anzahl("a 192.0.2.1 ".repeat(90_000)), 90_000);
    }
}
