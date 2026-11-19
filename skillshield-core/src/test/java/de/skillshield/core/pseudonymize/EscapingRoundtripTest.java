package de.skillshield.core.pseudonymize;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Literalschutz {@code [[TEXT_n]]} (T-9, Architektur-Plan Kap. 2.6 und 2.7; AK-30, AK-36, AK-44). */
class EscapingRoundtripTest {

    private static final FindingType PERSON = new FindingType("PERSON");
    private static final Random MISCHER = new Random(7L);
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private static final String MAIL = "erika.beispiel@example.invalid";

    private final Pseudonymizer p = new Pseudonymizer();

    /** Manueller Fund: erstes Vorkommen von {@code wert} ab {@code ab}. */
    private static Finding fund(FindingType typ, String text, String wert, int ab) {
        int s = text.indexOf(wert, ab);
        assertTrue(s >= 0, "Testfehler: Wert nicht im Text");
        return Finding.manual(typ, wert, s, s + wert.length());
    }

    private static Finding fund(FindingType typ, String text, String wert) {
        return fund(typ, text, wert, 0);
    }

    private PseudonymizationResult pruefe(String text, Finding... funde) {
        return LiteralschutzPruefung.pruefe(text, List.of(funde), MISCHER, text);
    }

    // ---------- Tests der T-9-Testliste, wörtlich ----------

    @Test
    void siehePfad1UndPfadMitManuellemPfadFundWirdTextEinsUndPfadZwei() {
        String pfad = bs("C:¦Tools¦a.exe");
        String text = "Siehe [[PFAD_1]] und " + pfad;
        PseudonymizationResult r = pruefe(text, fund(FindingType.PFAD, text, pfad));
        assertEquals("Siehe [[TEXT_1]] und [[PFAD_2]]", r.text());
        assertEquals("[[PFAD_1]]", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
        assertEquals(pfad, r.mapping().resolve("[[PFAD_2]]").orElseThrow());
    }

    @Test
    void roundtripOeffnendeDoppelklammer() {
        PseudonymizationResult r = pruefe("[[");
        assertEquals("[[", r.text());
        assertEquals(0, r.mapping().size());
    }

    @Test
    void roundtripSchliessendeDoppelklammer() {
        assertEquals("]]", pruefe("]]").text());
    }

    @Test
    void roundtripVerschachteltX1() {
        PseudonymizationResult r = pruefe("[[[[X_1]]]]");
        assertEquals("[[[[TEXT_1]]]]", r.text());
        assertEquals("[[X_1]]", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
    }

    @Test
    void roundtripAbgeschnittenerPlatzhalterPerson1OhneFundBleibtUnveraendert() {
        assertEquals("[[PERSON_1", pruefe("[[PERSON_1").text());
    }

    @Test
    void roundtripAbgeschnittenerPlatzhalterPerson1MitPersonFund() {
        String text = "[[PERSON_1 und Anna Beispiel";
        PseudonymizationResult r = pruefe(text, fund(PERSON, text, "Anna Beispiel"));
        assertEquals("[[TEXT_1]]und [[PERSON_2]]", r.text());
        assertEquals("[[PERSON_1 ", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
    }

    @Test
    void roundtripKleingeschriebenerPlatzhalterMitLeerzeichen() {
        PseudonymizationResult r = pruefe("[[ pfad_1 ]]");
        assertEquals("[[TEXT_1]]", r.text());
        assertEquals("[[ pfad_1 ]]", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
    }

    @Test
    void roundtripOriginalesText1() {
        PseudonymizationResult r = pruefe("a [[TEXT_1]] b");
        assertEquals("a [[TEXT_1]] b", r.text());
        assertEquals("[[TEXT_1]]", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
    }

    @Test
    void roundtripOriginalesText2VorText1VertauschtNummerierung() {
        PseudonymizationResult r = pruefe("[[TEXT_2]] [[TEXT_1]]");
        assertEquals("[[TEXT_1]] [[TEXT_2]]", r.text());
        assertEquals("[[TEXT_2]]", r.mapping().resolve("[[TEXT_1]]").orElseThrow());
    }

    @Test
    void roundtripBeschaedigtePfad1MitPfadFund() {
        String pfad = bs("C:¦Tools¦a.exe");
        String text = "Alt [PFAD_1] neu " + pfad;
        PseudonymizationResult r = pruefe(text, fund(FindingType.PFAD, text, pfad));
        assertEquals("Alt [[TEXT_1]] neu [[PFAD_2]]", r.text());
    }

    @Test
    void ohneFundeUndOhnePlatzhalterMusterIstDieAusgabeIdentisch() {
        for (String text : new String[] {"", "x", "Zeile\r\nzwei", "arr[idx_0]", "[[", "]]", "[ ]", "[[PERSON_1",
            "a [[ b", "[x_1", "$1 \\1"}) {
            PseudonymizationResult r = pruefe(text);
            assertEquals(text, r.text(), text);
            assertEquals(0, r.mapping().size());
        }
    }

    // ---------- Fälle aus dem Sicherheitsbefund zu T-8 ----------

    @Test
    void literalerPlatzhalterKollidiertNichtMitErzeugtem() {
        String text = "Mail [[EMAIL_1]] an " + MAIL;
        PseudonymizationResult r = pruefe(text, fund(FindingType.EMAIL, text, MAIL));
        assertEquals("Mail [[TEXT_1]] an [[EMAIL_2]]", r.text());
    }

    @Test
    void literalerPlatzhalterNachDemFundMitGleichemPlatzhalterText() {
        String text = MAIL + " und [[EMAIL_1]]";
        PseudonymizationResult r = pruefe(text, fund(FindingType.EMAIL, text, MAIL));
        assertEquals("[[EMAIL_1]] und [[TEXT_2]]", r.text());
        assertEquals("[[EMAIL_1]]", r.mapping().resolve("[[TEXT_2]]").orElseThrow());
    }

    @Test
    void literalePlatzhalterBeliebigenTypsUndSchreibweisen() {
        String[] literale = {"[[EMAIL_1]]", "[[email_1]]", "[[Email_1]]", "[[ EMAIL_1]]", "[[EMAIL_1 ]]",
            "[[ email_1 ]]", "[[FOO_7]]", "[[X1_123456789]]", "[[a_1]]", "[[TEXT_9]]", "[[IP_2]]"};
        for (String literal : literale) {
            String text = "A " + literal + " B 192.0.2.7 C " + literal;
            PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.7"),
                    fund(FindingType.EMAIL, text, "B"));
            assertEquals(literal, r.mapping().resolve("[[TEXT_1]]").orElseThrow(), literal);
            assertEquals("A [[TEXT_1]] [[EMAIL_2]] [[IP_3]] C [[TEXT_1]]", r.text(), literal);
        }
    }

    @Test
    void beschaedigteFormenBekannterTypenKeinFalschesBeschaedigt() {
        String[] formen = {"[EMAIL_1]", "[[EMAIL_", "[[EMAIL_1]", "[EMAIL_]", "[[EMAIL_]]", "[ email_1 ]",
            "[[EMAIL_12", "[EMAIL_"};
        for (String form : formen) {
            String text = "v " + form + " n " + MAIL;
            PseudonymizationResult r = pruefe(text, fund(FindingType.EMAIL, text, MAIL));
            assertTrue(r.text().startsWith("v [[TEXT_1]]"), form);
            assertEquals(form, r.mapping().resolve("[[TEXT_1]]").orElseThrow().stripTrailing(), form);
        }
    }

    @Test
    void beschaedigteFormEinesTypsDerErstDurchSpaeterenFundBekanntWird() {
        String text = "[[IP_ vorn, dann 192.0.2.1";
        assertEquals("[[TEXT_1]]vorn, dann [[IP_2]]", pruefe(text, fund(FindingType.IP, text, "192.0.2.1")).text());
        String text2 = "192.0.2.1 danach [[IP_";
        assertEquals("[[IP_1]] danach [[TEXT_2]]", pruefe(text2, fund(FindingType.IP, text2, "192.0.2.1")).text());
    }

    @Test
    void beschaedigteFormEinesUnbekanntenTypsBleibtTextUndMachtKeineMeldung() {
        String text = "[[PERSON_ und [PFAD_1] mit 192.0.2.1";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.1"));
        assertEquals("[[PERSON_ und [PFAD_1] mit [[IP_1]]", r.text());
        assertEquals(1, r.mapping().size());
    }

    @Test
    void beschaedigteFormVonTextOhneAnderesTokenBleibtUnveraendert() {
        // TEXT ist nur bekannt, wenn ein Fund TEXT heisst oder die Luecken ein Token enthalten (Plan 2.7)
        String text = "[[TEXT_ mit 192.0.2.1";
        assertEquals("[[TEXT_ mit [[IP_1]]", pruefe(text, fund(FindingType.IP, text, "192.0.2.1")).text());
    }

    @Test
    void beschaedigteFormVonTextMitAnderemTokenWirdGeschuetzt() {
        String text = "[[TEXT_ und [[EMAIL_1]] mit 192.0.2.1";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.1"));
        assertEquals("[[TEXT_1]]und [[TEXT_2]] mit [[IP_3]]", r.text());
    }

    @Test
    void beschaedigteFormVonTextMitManuellemTextFund() {
        String text = "[[TEXT_ und x";
        assertEquals("[[TEXT_1]]und [[TEXT_2]]", pruefe(text, fund(FindingType.TEXT, text, "x")).text());
    }

    // ---------- Einsetzen erzeugt neue platzhalterähnliche Folgen ----------

    @Test
    void angefangenerPlatzhalterDirektVorFundBleibtGetrennt() {
        String text = "[[EMAIL_" + MAIL;
        assertEquals("[[TEXT_1]][[EMAIL_2]]", pruefe(text, fund(FindingType.EMAIL, text, MAIL)).text());
        String text2 = "[[EMAIL_1x";
        assertEquals("[[TEXT_1]][[EMAIL_2]]", pruefe(text2, fund(FindingType.EMAIL, text2, "x")).text());
    }

    @Test
    void einzelneKlammerDirektVorFund() {
        String text = "[192.0.2.1";
        assertEquals("[[[IP_1]]", pruefe(text, fund(FindingType.IP, text, "192.0.2.1")).text());
    }

    @Test
    void schliessendeKlammernDirektHinterFund() {
        String text = "192.0.2.1]] und 192.0.2.2] und 192.0.2.3]]]";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.1"),
                fund(FindingType.IP, text, "192.0.2.2"), fund(FindingType.IP, text, "192.0.2.3"));
        assertEquals("[[IP_1]]]] und [[IP_2]]] und [[IP_3]]]]]", r.text());
    }

    @Test
    void zweiFundeMitUnterstrichOderZiffernDazwischen() {
        String text = "192.0.2.1_192.0.2.2 192.0.2.3_1192.0.2.4_";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.1"),
                fund(FindingType.IP, text, "192.0.2.2"), fund(FindingType.IP, text, "192.0.2.3"),
                fund(FindingType.IP, text, "192.0.2.4"));
        assertEquals("[[IP_1]]_[[IP_2]] [[IP_3]]_1[[IP_4]]_", r.text());
    }

    @Test
    void fundMitteninPlatzhalterFormBleibtErsetztUndRestBildetKeinToken() {
        String text = "[[EMAIL_1]]";
        assertEquals("[[[[PERSON_1]]]]", pruefe(text, fund(PERSON, text, "EMAIL_1")).text());
        // Fund deckt nur die Ziffer: der linke Rest [[EMAIL_ ist eine beschaedigte Form von EMAIL
        assertEquals("[[TEXT_1]][[EMAIL_2]]]]", pruefe(text, fund(FindingType.EMAIL, text, "1")).text());
    }

    @Test
    void fundUeberdecktPlatzhalterFormGanzWirdNichtVerdraengt() {
        String text = "x [[EMAIL_1]] y";
        PseudonymizationResult r = pruefe(text, fund(PERSON, text, "[[EMAIL_1]]"));
        assertEquals("x [[PERSON_1]] y", r.text());
        assertEquals(1, r.mapping().size());
    }

    @Test
    void fundBeginntInPlatzhalterFormUndReichtDarueberHinaus() {
        String text = "a [[EMAIL_1]] b";
        assertEquals("a [[[[PERSON_1]]", pruefe(text, fund(PERSON, text, "EMAIL_1]] b")).text());
    }

    @Test
    void fundBeginntVorPlatzhalterFormUndEndetDarin() {
        String text = "a [[EMAIL_1]] b";
        assertEquals("[[PERSON_1]]_1]] b", pruefe(text, fund(PERSON, text, "a [[EMAIL")).text());
    }

    // ---------- Manueller Fund mit Platzhalter als Originalwert (AK-44, Roundtrip) ----------

    @Test
    void manuellerFundMitOriginalwertIp1() {
        String text = "Wert [[IP_1]] steht hier";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "[[IP_1]]"));
        assertEquals("Wert [[IP_1]] steht hier", r.text());
        assertEquals("[[IP_1]]", r.mapping().resolve("[[IP_1]]").orElseThrow());
    }

    @Test
    void manuellerFundMitOriginalwertIp1UndLiteralIp1ImText() {
        String text = "[[IP_1]] und 192.0.2.5 und [[IP_1]]";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "[[IP_1]]"),
                fund(FindingType.IP, text, "192.0.2.5"));
        assertEquals("[[IP_1]] und [[IP_2]] und [[TEXT_3]]", r.text());
    }

    @Test
    void manuellerFundMitPlatzhalterWertGleicherWertMehrfach() {
        String text = "[[IP_2]] 192.0.2.5 [[IP_2]]";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "[[IP_2]]"),
                fund(FindingType.IP, text, "192.0.2.5"), fund(FindingType.IP, text, "[[IP_2]]", 5));
        assertEquals("[[IP_1]] [[IP_2]] [[IP_1]]", r.text());
    }

    // ---------- Nummerierung und Zuordnung ----------

    @Test
    void textPlatzhalterZaehlenGemeinsamMitDenAnderen() {
        String text = "[[A_1]] 192.0.2.1 [[B_2]] 192.0.2.2 [[A_1]]";
        PseudonymizationResult r = pruefe(text, fund(FindingType.IP, text, "192.0.2.1"),
                fund(FindingType.IP, text, "192.0.2.2"));
        assertEquals("[[TEXT_1]] [[IP_2]] [[TEXT_3]] [[IP_4]] [[TEXT_1]]", r.text());
        assertEquals(List.of("[[TEXT_1]]", "[[IP_2]]", "[[TEXT_3]]", "[[IP_4]]"),
                new ArrayList<>(r.mapping().placeholders()));
    }

    @Test
    void gleichesLiteralErhaeltGleichenPlatzhalterAuchMitManuellemTextFund() {
        String text = "[[A_1]] x [[A_1]] y";
        PseudonymizationResult r = pruefe(text, fund(FindingType.TEXT, text, "[[A_1]]"));
        assertEquals("[[TEXT_1]] x [[TEXT_1]] y", r.text());
        assertEquals(1, r.mapping().size());
    }

    @Test
    void zuordnungUndErgebnisToStringZeigenKeineWerte() {
        String marker = "GEHEIM-mustermann-4711";
        String text = "[[A_1]] " + marker + " [[B_2]]";
        PseudonymizationResult r = pruefe(text, fund(PERSON, text, marker));
        assertFalse(r.mapping().toString().contains(marker));
        assertFalse(r.mapping().toString().contains("[["));
        assertFalse(r.toString().contains(marker));
        assertFalse(r.toString().contains("[["));
    }

    @Test
    void validierungBleibtVorDemLiteralschutzKeinTeilergebnis() {
        String text = "[[EMAIL_1]] abc";
        Finding zuWeit = new Finding(FindingType.IP, "abcd", 12, 16, 1.0, "manuell");
        InvalidFindingException e = assertThrows(InvalidFindingException.class,
                () -> p.pseudonymize(text, List.of(zuWeit)));
        assertEquals(InvalidFindingException.Reason.OUT_OF_RANGE, e.reason());
        assertFalse(e.getMessage().contains("EMAIL"));
    }

    @Test
    void ueberlappendeFundeWerdenAuchMitPlatzhalterTextAbgelehnt() {
        String text = "[[EMAIL_1]] abc";
        Finding a = Finding.manual(FindingType.IP, text.substring(12, 15), 12, 15);
        Finding b = Finding.manual(FindingType.IP, text.substring(13, 15), 13, 15);
        assertEquals(InvalidFindingException.Reason.OVERLAP,
                assertThrows(InvalidFindingException.class, () -> p.pseudonymize(text, List.of(a, b))).reason());
    }

    @Test
    void vollbreiteKlammernUndUnsichtbareZeichenSindKeinPlatzhalter() {
        String[] texte = {"\uFF3B\uFF3BEMAIL_1\uFF3D\uFF3D", "[\u200B[EMAIL_1]]", "[[EMAIL\u200B_1]]",
            "[[EMAIL_1\u00AD]]", "[[         EMAIL_1]]"};
        for (String text : texte) {
            PseudonymizationResult r = pruefe(text, fund(FindingType.EMAIL, text, "1"));
            assertTrue(r.text().contains("[[EMAIL_"), text);
        }
        assertEquals("\uFF3B\uFF3BEMAIL_1\uFF3D\uFF3D", pruefe("\uFF3B\uFF3BEMAIL_1\uFF3D\uFF3D").text());
    }

    // ---------- Laufzeit (linear) ----------

    @Test
    void laufzeitMillionZeichenVollstaendigePlatzhalter() {
        int n = 1_000_000 / 11;
        String text = "[[EMAIL_1]]".repeat(n);
        PseudonymizationResult r = assertTimeoutPreemptively(LIMIT, () -> p.pseudonymize(text, List.of()));
        assertEquals("[[TEXT_1]]".repeat(n), r.text());
        assertEquals(text, assertTimeoutPreemptively(LIMIT,
                () -> new Reidentifier().restore(r.text(), r.mapping()).text()));
    }

    @Test
    void laufzeitMillionZeichenEinzelneKlammer() {
        String text = "[".repeat(1_000_000);
        PseudonymizationResult r = assertTimeoutPreemptively(LIMIT, () -> p.pseudonymize(text, List.of()));
        assertEquals(text, r.text());
    }

    @Test
    void laufzeitMillionZeichenAngefangenerPlatzhalterMitBekanntemTyp() {
        String text = "[[A_".repeat(250_000) + "x";
        Finding f = Finding.manual(new FindingType("A"), "x", text.length() - 1, text.length());
        PseudonymizationResult r = assertTimeoutPreemptively(LIMIT, () -> p.pseudonymize(text, List.of(f)));
        assertEquals("[[TEXT_1]]".repeat(250_000) + "[[A_2]]", r.text());
    }

    @Test
    void laufzeitMillionZeichenAngefangenerPlatzhalterMitUnbekanntemTyp() {
        String text = "[[A_".repeat(250_000);
        PseudonymizationResult r = assertTimeoutPreemptively(LIMIT, () -> p.pseudonymize(text, List.of()));
        assertEquals(text, r.text());
    }

    @Test
    void laufzeitGemischtMitHunderttausendFunden() {
        String[] luecken = {"[[EMAIL_1]]", "[", "[[A_", "]]", "_", "[[TEXT_"};
        StringBuilder sb = new StringBuilder();
        List<Finding> funde = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            sb.append(luecken[i % luecken.length]);
            int s = sb.length();
            sb.append("ab");
            funde.add(Finding.manual(i % 2 == 0 ? FindingType.EMAIL : new FindingType("A"), "ab", s, s + 2));
        }
        String text = sb.toString();
        PseudonymizationResult r = assertTimeoutPreemptively(LIMIT, () -> p.pseudonymize(text, funde));
        assertEquals(text, assertTimeoutPreemptively(LIMIT,
                () -> new Reidentifier().restore(r.text(), r.mapping()).text()));
    }
}
