package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderSyntax.Token;
import de.skillshield.core.pseudonymize.PlaceholderSyntax.Token.Kind;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tokenizer: T (Platzhalter), D (beschädigt), Bruchstücke, Grenzen, Laufzeit (Plan Kap. 2.7, T-8). */
class PlaceholderSyntaxTest {

    private static final Set<String> BEKANNT = Set.of("EMAIL", "PFAD", "IP", "TEXT");
    private static final Duration GRENZE = Duration.ofSeconds(2);

    private static List<Token> tok(String text) {
        return PlaceholderSyntax.tokenize(text, 0, text.length(), BEKANNT);
    }

    private static void erwartePlatzhalter(String text, String kanonisch) {
        List<Token> t = tok(text);
        assertEquals(1, t.size(), text);
        assertEquals(Kind.PLATZHALTER, t.get(0).kind(), text);
        assertEquals(kanonisch, t.get(0).canonical(), text);
        assertEquals(0, t.get(0).start(), text);
        assertEquals(text.length(), t.get(0).end(), text);
    }

    private static void erwarteBeschaedigt(String text, int ende) {
        List<Token> t = tok(text);
        assertEquals(1, t.size(), text);
        assertEquals(Kind.BESCHAEDIGT, t.get(0).kind(), text);
        assertNull(t.get(0).canonical(), text);
        assertEquals(0, t.get(0).start(), text);
        assertEquals(ende, t.get(0).end(), text);
    }

    @Test
    void kanonischerPlatzhalter() {
        erwartePlatzhalter("[[PFAD_1]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[EMAIL_123456789]]", "[[EMAIL_123456789]]");
    }

    @Test
    void kleinschreibungWirdKanonisch() {
        erwartePlatzhalter("[[pfad_1]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[Pfad_1]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[email_1]]", "[[EMAIL_1]]");
    }

    @Test
    void leerzeichenInnenWerdenToleriert() {
        erwartePlatzhalter("[[ PFAD_1]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[PFAD_1 ]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[ PFAD_1 ]]", "[[PFAD_1]]");
        erwartePlatzhalter("[[" + " ".repeat(8) + "PFAD_1" + " ".repeat(8) + "]]", "[[PFAD_1]]");
    }

    @Test
    void neunLeerzeichenSindKeinPlatzhalterMehr() {
        String text = "[[" + " ".repeat(9) + "PFAD_1]]";
        assertTrue(tok(text).isEmpty());
        String hinten = "[[PFAD_1" + " ".repeat(9) + "]]";
        List<Token> t = tok(hinten);
        assertEquals(1, t.size());
        assertEquals(Kind.BESCHAEDIGT, t.get(0).kind());
    }

    @Test
    void tabulatorUndZeilenumbruchInnenSindKeinPlatzhalter() {
        assertTrue(tok("[[\tPFAD_1]]").isEmpty());
        assertTrue(tok("[[PFAD_1\n]]").stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER));
        assertTrue(tok("[[PFAD\n_1]]").isEmpty());
    }

    @Test
    void beschaedigteFormenMitBekanntemTyp() {
        erwarteBeschaedigt("[[PFAD_1]", 9);
        erwarteBeschaedigt("[PFAD_1]", 8);
        erwarteBeschaedigt("[[PFAD_]]", 9);
        erwarteBeschaedigt("[[PFAD_", 7);
        erwarteBeschaedigt("[PFAD_1", 7);
        erwarteBeschaedigt("[[pfad_1]", 9);
    }

    @Test
    void beschaedigtMitUnbekanntemTypWirdVerbrauchtAberNichtGemeldet() {
        assertTrue(tok("arr[idx_0]").isEmpty());
        assertTrue(tok("[[FOO_1]").isEmpty());
        // verbraucht: das Ende von arr[idx_0] startet kein neues Token
        assertTrue(tok("a[idx_0]]").isEmpty());
    }

    @Test
    void unbekannterTypImPlatzhalterBleibtToken() {
        erwartePlatzhalter("[[FOO_1]]", "[[FOO_1]]");
    }

    @Test
    void bruchstueckNurOeffnendeKlammern() {
        assertTrue(tok("[[").isEmpty());
        assertTrue(tok("[").isEmpty());
        assertTrue(tok("[[[[").isEmpty());
    }

    @Test
    void bruchstueckOhneNummerUndKlammer() {
        erwarteBeschaedigt("[[EMAIL_", 8);
    }

    @Test
    void bruchstueckEineSchliessendeKlammer() {
        erwarteBeschaedigt("[[EMAIL_1]", 10);
    }

    @Test
    void bruchstueckZuvieleSchliessendeKlammern() {
        List<Token> t = tok("[[EMAIL_1]]]]");
        assertEquals(1, t.size());
        assertEquals(Kind.PLATZHALTER, t.get(0).kind());
        assertEquals(10 + 1, t.get(0).end());
        assertEquals("[[EMAIL_1]]", t.get(0).canonical());
    }

    @Test
    void bruchstueckZuvieleOeffnendeKlammernErsteBleibtText() {
        List<Token> t = tok("[[[EMAIL_1]]");
        assertEquals(1, t.size());
        assertEquals(1, t.get(0).start());
        assertEquals(12, t.get(0).end());
        assertEquals("[[EMAIL_1]]", t.get(0).canonical());
    }

    @Test
    void bruchstueckFuehrendeNull() {
        erwarteBeschaedigt("[[EMAIL_01]]", 12);
        erwarteBeschaedigt("[[EMAIL_0]]", 11);
    }

    @Test
    void bruchstueckKleinschreibung() {
        erwartePlatzhalter("[[email_1]]", "[[EMAIL_1]]");
    }

    @Test
    void zahlUeberlaufWirdNieAlsPlatzhalterGelesen() {
        String text = "[[EMAIL_99999999999999999999]]";
        List<Token> t = tok(text);
        assertEquals(1, t.size());
        assertEquals(Kind.BESCHAEDIGT, t.get(0).kind());
        assertEquals("[[EMAIL_".length() + 9, t.get(0).end());
        List<Token> zehn = tok("[[EMAIL_1234567890]]");
        assertEquals(1, zehn.size());
        assertEquals(Kind.BESCHAEDIGT, zehn.get(0).kind());
    }

    @Test
    void neunStelligeNummerIstDieGrenze() {
        erwartePlatzhalter("[[EMAIL_999999999]]", "[[EMAIL_999999999]]");
    }

    @Test
    void typnameGrenzen32Und33Zeichen() {
        String name32 = "A".repeat(32);
        String name33 = "A".repeat(33);
        erwartePlatzhalter("[[" + name32 + "_1]]", "[[" + name32 + "_1]]");
        assertTrue(tok("[[" + name33 + "_1]]").isEmpty());
    }

    @Test
    void typnameBeginntMitBuchstabe() {
        assertTrue(tok("[[1A_1]]").isEmpty());
        assertTrue(tok("[[_1]]").isEmpty());
        erwartePlatzhalter("[[A1_1]]", "[[A1_1]]");
    }

    @Test
    void leerzeichenZwischenTypUndUnterstrichIstKeinPlatzhalter() {
        assertTrue(tok("[[PFAD _1]]").isEmpty());
        assertTrue(tok("[[PFAD_ 1]]").stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER));
    }

    @Test
    void vollbreiteKlammernUndZiffernSindKeinPlatzhalter() {
        assertTrue(tok("\uFF3B\uFF3BEMAIL_1\uFF3D\uFF3D").isEmpty());
        assertTrue(tok("[[EMAIL_1\uFF3D\uFF3D").stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER));
        assertTrue(tok("[[EMAIL_\uFF11]]").stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER));
        // arabisch-indische Ziffer 1
        assertTrue(tok("[[EMAIL_\u0661]]").stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER));
    }

    @Test
    void unsichtbareZeichenImPlatzhalterBrechenIhn() {
        int[] unsichtbar = {0x00AD, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202E, 0x2060, 0xFEFF};
        for (int cp : unsichtbar) {
            String z = new String(Character.toChars(cp));
            for (String text : new String[] {"[[EMAIL" + z + "_1]]", "[[EMAIL_" + z + "1]]",
                "[[EMAIL_1" + z + "]]", "[[" + z + "EMAIL_1]]", "[" + z + "[EMAIL_1]]", "[[EMAIL_1]" + z + "]"}) {
                assertTrue(tok(text).stream().noneMatch(x -> x.kind() == Kind.PLATZHALTER),
                        "U+" + Integer.toHexString(cp));
            }
        }
    }

    @Test
    void mehrereTokenInTextreihenfolgeUndEingebettet() {
        String text = "a [[PFAD_2]] b [[ip_1]] c [[PFAD_1]";
        List<Token> t = tok(text);
        assertEquals(3, t.size());
        assertEquals("[[PFAD_2]]", t.get(0).canonical());
        assertEquals("[[IP_1]]", t.get(1).canonical());
        assertEquals(Kind.BESCHAEDIGT, t.get(2).kind());
        assertEquals(2, t.get(0).start());
        assertEquals(text.length(), t.get(2).end());
    }

    @Test
    void zusammenhaengendePlatzhalterOhneTrenner() {
        List<Token> t = tok("[[PFAD_1]][[PFAD_2]]");
        assertEquals(2, t.size());
        assertEquals(10, t.get(1).start());
    }

    @Test
    void bereichBegrenztDieSuche() {
        String text = "[[PFAD_1]] [[PFAD_2]] [[PFAD_3]]";
        List<Token> t = PlaceholderSyntax.tokenize(text, 11, 21, BEKANNT);
        assertEquals(1, t.size());
        assertEquals("[[PFAD_2]]", t.get(0).canonical());
        // Platzhalter, der ueber to hinausragt, zaehlt nicht als vollstaendig
        List<Token> abgeschnitten = PlaceholderSyntax.tokenize(text, 0, 9, BEKANNT);
        assertEquals(1, abgeschnitten.size());
        assertEquals(Kind.BESCHAEDIGT, abgeschnitten.get(0).kind());
    }

    @Test
    void ungueltigeArgumente() {
        NullPointerException a = assertThrows(NullPointerException.class,
                () -> PlaceholderSyntax.tokenize(null, 0, 0, BEKANNT));
        assertEquals("text", a.getMessage());
        NullPointerException b = assertThrows(NullPointerException.class,
                () -> PlaceholderSyntax.tokenize("x", 0, 1, null));
        assertEquals("knownTypes", b.getMessage());
        assertThrows(IndexOutOfBoundsException.class, () -> PlaceholderSyntax.tokenize("abc", 0, 4, BEKANNT));
        assertThrows(IndexOutOfBoundsException.class, () -> PlaceholderSyntax.tokenize("abc", -1, 2, BEKANNT));
        assertThrows(IndexOutOfBoundsException.class, () -> PlaceholderSyntax.tokenize("abc", 2, 1, BEKANNT));
        assertTrue(PlaceholderSyntax.tokenize("", 0, 0, BEKANNT).isEmpty());
    }

    @Test
    void ergebnislisteIstUnveraenderlich() {
        List<Token> t = tok("[[PFAD_1]]");
        assertThrows(UnsupportedOperationException.class, () -> t.add(t.get(0)));
    }

    @Test
    void tokenPruefungUndKeinKlartextInMeldung() {
        assertThrows(IllegalArgumentException.class, () -> new Token(-1, 2, Kind.BESCHAEDIGT, null));
        assertThrows(IllegalArgumentException.class, () -> new Token(2, 2, Kind.BESCHAEDIGT, null));
        assertThrows(IllegalArgumentException.class, () -> new Token(0, 5, Kind.PLATZHALTER, null));
        assertThrows(IllegalArgumentException.class, () -> new Token(0, 5, Kind.BESCHAEDIGT, "[[A_1]]"));
        assertThrows(NullPointerException.class, () -> new Token(0, 5, null, null));
    }

    @Test
    void formatUndTokenizerSindKonsistent() {
        for (FindingType type : new FindingType[] {FindingType.PFAD, FindingType.EMAIL, FindingType.TEXT,
            new FindingType("A"), new FindingType("X".repeat(32))}) {
            for (int n : new int[] {1, 9, 10, 999_999_999}) {
                String ph = PlaceholderSyntax.format(type, n);
                erwartePlatzhalter(ph, ph);
                String[] teile = PlaceholderSyntax.parseCanonical(ph);
                assertNotNull(teile);
                assertEquals(type.name(), teile[0]);
                assertEquals(Integer.toString(n), teile[1]);
            }
        }
    }

    @Test
    void typnamenRegelIstEineGemeinsameDefinition() {
        // parseCanonical und FindingType teilen FindingType.isValidName; Tokenizer nutzt isNameStart/isNameChar
        assertNull(PlaceholderSyntax.parseCanonical("[[pfad_1]]"));
        assertNull(PlaceholderSyntax.parseCanonical("[[" + "A".repeat(33) + "_1]]"));
        assertNull(PlaceholderSyntax.parseCanonical("[[1A_1]]"));
        assertThrows(IllegalArgumentException.class, () -> new FindingType("pfad"));
        assertTrue(FindingType.isValidName("PFAD"));
        // jeder zulaessige Name wird vom Tokenizer als Platzhalter erkannt, jeder unzulaessige nicht
        for (String name : new String[] {"A", "Z9", "A".repeat(32), "A".repeat(33), "9A", "A_B", "A-B", "ÄA"}) {
            boolean gueltig = FindingType.isValidName(name);
            boolean erkannt = tok("[[" + name + "_1]]").stream().anyMatch(x -> x.kind() == Kind.PLATZHALTER
                    && x.start() == 0 && x.end() == name.length() + 6);
            assertEquals(gueltig, erkannt, name);
        }
    }

    // ----- pathologische Eingaben: linear, erwartetes Ergebnis -----

    private static List<Token> zeitlich(String text, Set<String> bekannt) {
        return assertTimeoutPreemptively(GRENZE,
                () -> PlaceholderSyntax.tokenize(text, 0, text.length(), bekannt));
    }

    @Test
    void pathologischEckigeKlammernEinzeln() {
        assertTrue(zeitlich("[".repeat(1_000_000), BEKANNT).isEmpty());
    }

    @Test
    void pathologischDoppelteOeffnendeKlammern() {
        assertTrue(zeitlich("[[".repeat(500_000), BEKANNT).isEmpty());
    }

    @Test
    void pathologischSchliessendeKlammern() {
        assertTrue(zeitlich("]".repeat(1_000_000), BEKANNT).isEmpty());
    }

    @Test
    void pathologischTypanfangWiederholt() {
        String text = "[[A_".repeat(250_000);
        assertTrue(zeitlich(text, Set.of("B")).isEmpty());
        assertEquals(250_000, zeitlich(text, Set.of("A")).size());
    }

    @Test
    void pathologischUnvollstaendigeNummerWiederholt() {
        String text = "[[EMAIL_1".repeat(110_000);
        List<Token> t = zeitlich(text, Set.of("EMAIL"));
        assertEquals(110_000, t.size());
        assertTrue(t.stream().allMatch(x -> x.kind() == Kind.BESCHAEDIGT));
    }

    @Test
    void pathologischLangeNameOhneUnterstrich() {
        assertTrue(zeitlich("[[" + "A".repeat(1_000_000), BEKANNT).isEmpty());
        assertTrue(zeitlich("[".repeat(500_000) + "A".repeat(500_000), BEKANNT).isEmpty());
    }

    @Test
    void pathologischLeerzeichenNachKlammer() {
        assertTrue(zeitlich("[[" + " ".repeat(1_000_000), BEKANNT).isEmpty());
        assertTrue(zeitlich("[[ ".repeat(300_000), BEKANNT).isEmpty());
    }

    @Test
    void hunderttausendGueltigePlatzhalter() {
        String text = "[[PFAD_1]] ".repeat(100_000);
        List<Token> t = zeitlich(text, BEKANNT);
        assertEquals(100_000, t.size());
        assertTrue(t.stream().allMatch(x -> x.kind() == Kind.PLATZHALTER));
    }

    @Test
    void eineMillionZeichenGemischt() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 1_000_000) {
            sb.append("text [[PFAD_1]] [ [[x [[IP_ 2]] [[EMAIL_1] ]] ");
        }
        List<Token> t = zeitlich(sb.toString(), BEKANNT);
        assertTrue(t.size() > 10_000);
    }
}
