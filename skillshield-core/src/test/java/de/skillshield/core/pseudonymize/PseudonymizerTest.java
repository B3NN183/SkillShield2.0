package de.skillshield.core.pseudonymize;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PseudonymizerTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";
    private static final FindingType PERSON = new FindingType("PERSON");

    private final Pseudonymizer p = new Pseudonymizer();

    /** Fund am n-ten Vorkommen von {@code wert} (ab 0) im Text. */
    private static Finding fund(FindingType type, String text, String wert, int vorkommen) {
        int idx = -1;
        for (int i = 0; i <= vorkommen; i++) {
            idx = text.indexOf(wert, idx + 1);
        }
        assertTrue(idx >= 0, "Testaufbau: Wert nicht im Text");
        return new Finding(type, wert, idx, idx + wert.length(), 1.0, Finding.SOURCE_MANUAL);
    }

    private static Finding fund(FindingType type, String text, String wert) {
        return fund(type, text, wert, 0);
    }

    /** Rückweg über die Produkt-API; jede Meldung der Rückübersetzung wäre hier ein Fehler. */
    static String zurueck(PseudonymizationResult r) {
        ReidentificationResult rr = new Reidentifier().restore(r.text(), r.mapping());
        assertTrue(rr.unresolved().isEmpty(), "Roundtrip ohne Fremdbearbeitung meldet nichts");
        return rr.text();
    }

    @Test
    void pfadWirdEinPlatzhalterAk25() {
        String pfad = bs("C:¦Tools¦BeispielTool¦tool.exe");
        String text = "Start " + pfad + " jetzt";
        PseudonymizationResult r = p.pseudonymize(text, List.of(fund(FindingType.PFAD, text, pfad)));
        assertEquals("Start [[PFAD_1]] jetzt", r.text());
        for (String teil : new String[] {"Tools", "BeispielTool", "tool.exe", "C:", "\\"}) {
            assertFalse(r.text().contains(teil), teil);
        }
        assertEquals(pfad, r.mapping().resolve("[[PFAD_1]]").orElseThrow());
        assertEquals(1, r.mapping().size());
    }

    @Test
    void beispielAk27UnsortiertUebergeben() {
        String text = bs("Mail erika.beispiel@example.invalid, Server 192.0.2.10, Tool C:¦Tools¦a.exe");
        Finding mail = fund(FindingType.EMAIL, text, "erika.beispiel@example.invalid");
        Finding ip = fund(FindingType.IP, text, "192.0.2.10");
        Finding pfad = fund(FindingType.PFAD, text, bs("C:¦Tools¦a.exe"));
        PseudonymizationResult r = p.pseudonymize(text, List.of(pfad, mail, ip));
        assertEquals("Mail [[EMAIL_1]], Server [[IP_2]], Tool [[PFAD_3]]", r.text());
        assertEquals(List.of("[[EMAIL_1]]", "[[IP_2]]", "[[PFAD_3]]"), new ArrayList<>(r.mapping().placeholders()));
        assertEquals(text, zurueck(r));
    }

    @Test
    void nummernFolgenDerPositionNichtDerListenreihenfolge() {
        String text = "a1 b2 c3";
        List<Finding> funde = new ArrayList<>(List.of(fund(FindingType.IP, text, "a1"),
                fund(FindingType.IP, text, "b2"), fund(FindingType.IP, text, "c3")));
        Collections.reverse(funde);
        assertEquals("[[IP_1]] [[IP_2]] [[IP_3]]", p.pseudonymize(text, funde).text());
    }

    @Test
    void zweiPfadeMitGemeinsamemAnfangUndPraefixPfad() {
        String a = bs("C:¦Tools¦a.exe");
        String b = bs("C:¦Tools¦b.exe");
        String text = a + " " + b;
        assertEquals("[[PFAD_1]] [[PFAD_2]]", p.pseudonymize(text,
                List.of(fund(FindingType.PFAD, text, a), fund(FindingType.PFAD, text, b))).text());

        String dir = bs("C:¦Tools");
        String text2 = dir + " " + a;
        PseudonymizationResult r = p.pseudonymize(text2,
                List.of(fund(FindingType.PFAD, text2, dir), fund(FindingType.PFAD, text2, a)));
        assertEquals("[[PFAD_1]] [[PFAD_2]]", r.text());
        assertEquals(2, r.mapping().size());
    }

    @Test
    void gleicherWertMehrfachGleicherPlatzhalter() {
        String a = bs("C:¦Tools¦a.exe");
        String text = a + " ... " + a + " ... " + a;
        List<Finding> funde = List.of(fund(FindingType.PFAD, text, a, 0), fund(FindingType.PFAD, text, a, 1),
                fund(FindingType.PFAD, text, a, 2));
        PseudonymizationResult r = p.pseudonymize(text, funde);
        assertEquals("[[PFAD_1]] ... [[PFAD_1]] ... [[PFAD_1]]", r.text());
        assertEquals(1, r.mapping().size());
        assertEquals(text, zurueck(r));
    }

    @Test
    void schreibvariantenSindVerschiedeneWerte() {
        String[] werte = {bs("C:¦Tools¦a.exe"), bs("c:¦tools¦a.exe"), "C:/Tools/a.exe", bs("C:¦Tools¦"), "C:/Tools/",
            bs("C:¦Tools")};
        StringBuilder sb = new StringBuilder();
        List<Finding> funde = new ArrayList<>();
        for (String w : werte) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            funde.add(new Finding(FindingType.PFAD, w, sb.length(), sb.length() + w.length(), 1.0, "manuell"));
            sb.append(w);
        }
        String text = sb.toString();
        PseudonymizationResult r = p.pseudonymize(text, funde);
        assertEquals("[[PFAD_1]] | [[PFAD_2]] | [[PFAD_3]] | [[PFAD_4]] | [[PFAD_5]] | [[PFAD_6]]", r.text());
        assertEquals(6, r.mapping().size());
        assertEquals(text, zurueck(r));
    }

    @Test
    void gleicherWertUnterZweiTypenZweiPlatzhalter() {
        String text = "wert und wert";
        PseudonymizationResult r = p.pseudonymize(text, List.of(fund(FindingType.PFAD, text, "wert", 0),
                fund(PERSON, text, "wert", 1)));
        assertEquals("[[PFAD_1]] und [[PERSON_2]]", r.text());
        assertEquals(2, r.mapping().size());
    }

    @Test
    void zweiDurchlaeufeBeginnenJeBeiEins() {
        String text = "x 192.0.2.1 y";
        List<Finding> funde = List.of(fund(FindingType.IP, text, "192.0.2.1"));
        assertEquals("x [[IP_1]] y", p.pseudonymize(text, funde).text());
        assertEquals("x [[IP_1]] y", p.pseudonymize(text, funde).text());
        assertEquals("x [[IP_1]] y", new Pseudonymizer().pseudonymize(text, funde).text());
    }

    @Test
    void weggelassenerFundBleibtKlartext() {
        String text = "A 192.0.2.1 B 192.0.2.2 C";
        PseudonymizationResult r = p.pseudonymize(text, List.of(fund(FindingType.IP, text, "192.0.2.2")));
        assertEquals("A 192.0.2.1 B [[IP_1]] C", r.text());
        assertEquals(1, r.mapping().size());
    }

    @Test
    void manuelleFundeBeliebigerQuelleAk31() {
        String text = "Anna Beispiel startet tool.exe heute";
        PseudonymizationResult r = p.pseudonymize(text, List.of(
                Finding.manual(PERSON, "Anna Beispiel", 0, 13),
                fund(FindingType.PFAD, text, "tool.exe")));
        assertEquals("[[PERSON_1]] startet [[PFAD_2]] heute", r.text());
        assertEquals("Anna Beispiel", r.mapping().resolve("[[PERSON_1]]").orElseThrow());
        Finding erkenner = new Finding(FindingType.IP, "192.0.2.1", 0, 9, 0.85, "ipv4");
        assertEquals("[[IP_1]]", p.pseudonymize("192.0.2.1", List.of(erkenner)).text());
        Finding zusammen = new Finding(FindingType.PFAD, "192.0.2.1", 0, 9, 0.4, Finding.SOURCE_MERGED);
        assertEquals("[[PFAD_1]]", p.pseudonymize("192.0.2.1", List.of(zusammen)).text());
    }

    @Test
    void leerraumUndZeilenendenBleibenUnveraendert() {
        String text = "a\tb  c   192.0.2.1\r\nzeile2\n192.0.2.2\rende\r\n\r\n";
        PseudonymizationResult r = p.pseudonymize(text,
                List.of(fund(FindingType.IP, text, "192.0.2.1"), fund(FindingType.IP, text, "192.0.2.2")));
        assertEquals("a\tb  c   [[IP_1]]\r\nzeile2\n[[IP_2]]\rende\r\n\r\n", r.text());
        assertEquals(zaehle(text, '\n'), zaehle(r.text(), '\n'));
        assertEquals(zaehle(text, '\r'), zaehle(r.text(), '\r'));
    }

    private static long zaehle(String s, char c) {
        return s.chars().filter(x -> x == c).count();
    }

    @Test
    void fundAmAnfangAmEndeUndGanzerText() {
        String text = "192.0.2.1 mitte 192.0.2.2";
        assertEquals("[[IP_1]] mitte [[IP_2]]", p.pseudonymize(text,
                List.of(fund(FindingType.IP, text, "192.0.2.1"), fund(FindingType.IP, text, "192.0.2.2"))).text());
        String pfad = bs("C:¦Tools¦a.exe");
        assertEquals("[[PFAD_1]]", p.pseudonymize(pfad, List.of(fund(FindingType.PFAD, pfad, pfad))).text());
    }

    @Test
    void aneinandergrenzendeFunde() {
        String text = "AABBCC";
        PseudonymizationResult r = p.pseudonymize(text, List.of(fund(PERSON, text, "BB"),
                fund(PERSON, text, "AA"), fund(PERSON, text, "CC")));
        assertEquals("[[PERSON_1]][[PERSON_2]][[PERSON_3]]", r.text());
        assertEquals(text, zurueck(r));
        // gleicher Wert direkt hintereinander
        String doppelt = "AAAA";
        PseudonymizationResult r2 = p.pseudonymize(doppelt, List.of(
                new Finding(PERSON, "AA", 0, 2, 1.0, "manuell"), new Finding(PERSON, "AA", 2, 4, 1.0, "manuell")));
        assertEquals("[[PERSON_1]][[PERSON_1]]", r2.text());
    }

    @Test
    void zuordnungNurFuerErsetztes() {
        String text = "x 192.0.2.1 y";
        assertEquals(0, p.pseudonymize(text, List.of()).mapping().size());
        assertEquals(1, p.pseudonymize(text, List.of(fund(FindingType.IP, text, "192.0.2.1"))).mapping().size());
    }

    @Test
    void leerUndLeerraumUndOhneFunde() {
        for (String t : new String[] {"", " ", " \t\r\n ", "kein Fund hier"}) {
            PseudonymizationResult r = p.pseudonymize(t, List.of());
            assertEquals(t, r.text());
            assertEquals(0, r.mapping().size());
        }
    }

    @Test
    void ersetzungstextWirdNichtAlsRegexOderErsatzmusterGedeutet() {
        String wert = bs("$1 ¦1 ${HOME} %APPDATA% ¦¦");
        String text = "[" + wert + "]";
        PseudonymizationResult r = p.pseudonymize(text, List.of(fund(PERSON, text, wert)));
        assertEquals("[[[PERSON_1]]]", r.text());
        assertEquals(wert, r.mapping().resolve("[[PERSON_1]]").orElseThrow());
    }

    @Test
    void surrogatpaareUndKombinationszeichen() {
        String emoji = "\uD83D\uDE00";
        String wert = "Erika" + emoji + "e\u0301";
        String text = emoji + " " + wert + emoji;
        int s = text.indexOf(wert);
        PseudonymizationResult r = p.pseudonymize(text,
                List.of(new Finding(PERSON, wert, s, s + wert.length(), 1.0, "manuell")));
        assertEquals(emoji + " [[PERSON_1]]" + emoji, r.text());
        assertEquals(text, zurueck(r));
    }

    @Test
    void fundMitAtAmAnfang() {
        String text = "x@example.invalid y";
        Finding rest = new Finding(FindingType.EMAIL, "@example.invalid", 1, 17, 0.95, "email");
        assertEquals("x[[EMAIL_1]] y", p.pseudonymize(text, List.of(rest)).text());
    }

    @Test
    void eingabelisteUndTextBleibenUnveraendert() {
        String text = "b a";
        List<Finding> funde = new ArrayList<>(List.of(fund(PERSON, text, "b"), fund(PERSON, text, "a")));
        Collections.reverse(funde);
        List<Finding> kopie = new ArrayList<>(funde);
        p.pseudonymize(text, funde);
        assertEquals(kopie, funde);
        assertEquals("b a", text);
    }

    @Test
    void nullEingaben() {
        assertEquals("text", assertThrows(NullPointerException.class, () -> p.pseudonymize(null, List.of()))
                .getMessage());
        assertEquals("findings", assertThrows(NullPointerException.class, () -> p.pseudonymize("a", null))
                .getMessage());
    }

    @Test
    void schutzpruefungMeldetOhneKlartext() {
        String text = "abc " + MARKER + " def";
        int s = text.indexOf(MARKER);
        Finding gut = new Finding(PERSON, MARKER, s, s + MARKER.length(), 1.0, "manuell");
        Finding falsch = new Finding(PERSON, MARKER, 0, MARKER.length(), 1.0, "manuell");
        Finding zuWeit = new Finding(PERSON, MARKER, text.length() - 3, text.length() - 3 + MARKER.length(), 1.0,
                "manuell");
        Finding ueberlapp = new Finding(PERSON, MARKER.substring(2, 7), s + 2, s + 7, 1.0, "manuell");
        List<List<Finding>> faelle = new ArrayList<>();
        faelle.add(List.of(falsch));
        faelle.add(List.of(zuWeit));
        faelle.add(List.of(gut, ueberlapp));
        faelle.add(java.util.Arrays.asList(gut, null));
        for (List<Finding> fall : faelle) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> p.pseudonymize(text, fall));
            assertFalse(e.getMessage().contains(MARKER));
            assertFalse(e.getMessage().contains("abc"));
        }
    }

    @Test
    void toStringOhneKlartext() {
        String text = "x " + MARKER + " y";
        int s = text.indexOf(MARKER);
        PseudonymizationResult r = p.pseudonymize(text,
                List.of(new Finding(PERSON, MARKER, s, s + MARKER.length(), 1.0, "manuell")));
        assertFalse(r.toString().contains(MARKER));
        assertFalse(r.toString().contains("PERSON"));
        assertEquals("PseudonymizationResult[textLength=" + r.text().length() + ", placeholders=1]", r.toString());
        assertThrows(NullPointerException.class, () -> new PseudonymizationResult(null, PlaceholderMapping.empty()));
        assertThrows(NullPointerException.class, () -> new PseudonymizationResult("", null));
    }

    @Test
    void sehrVieleFundeLinear() {
        int n = 100_000;
        StringBuilder sb = new StringBuilder();
        List<Finding> funde = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int s = sb.length();
            String wert = "192.0.2." + (i % 250);
            sb.append(wert);
            funde.add(new Finding(FindingType.IP, wert, s, sb.length(), 0.85, "ipv4"));
            sb.append(' ');
        }
        String text = sb.toString();
        PseudonymizationResult r = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> p.pseudonymize(text, funde));
        assertEquals(250, r.mapping().size());
        assertEquals(text, zurueck(r));
    }

    @Test
    void sehrVieleVerschiedeneWerte() {
        int n = 100_000;
        StringBuilder sb = new StringBuilder();
        List<Finding> funde = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int s = sb.length();
            String wert = "host" + i + ".example";
            sb.append(wert);
            funde.add(new Finding(PERSON, wert, s, sb.length(), 1.0, "manuell"));
            sb.append(',');
        }
        String text = sb.toString();
        PseudonymizationResult r = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> p.pseudonymize(text, funde));
        assertEquals(n, r.mapping().size());
        assertEquals(text, zurueck(r));
    }

    @Test
    void sehrGrosserFund() {
        String wert = "A".repeat(5_000_000);
        String text = "x" + wert + "y";
        Finding f = new Finding(PERSON, wert, 1, 1 + wert.length(), 1.0, "manuell");
        PseudonymizationResult r = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> p.pseudonymize(text, List.of(f)));
        assertEquals("x[[PERSON_1]]y", r.text());
        assertEquals(wert, r.mapping().resolve("[[PERSON_1]]").orElseThrow());
    }

    @Test
    void sehrGrosserTextOhneFunde() {
        String text = "abc ".repeat(2_500_000);
        PseudonymizationResult r = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> p.pseudonymize(text, List.of()));
        assertEquals(text, r.text());
    }

    @Test
    void verschiedeneWerteVerschiedenePlatzhalter() {
        String text = "a b";
        PseudonymizationResult r = p.pseudonymize(text,
                List.of(fund(PERSON, text, "a"), fund(PERSON, text, "b")));
        String[] teile = r.text().split(" ");
        assertNotEquals(teile[0], teile[1]);
    }
}
