package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Zeichenklassen des Pfad-Erkenners (Plan Kap. 2.4.1). Nicht-ASCII-Zeichen nur als Escapes. */
class PathCharsTest {

    @Test
    void trennerSindBackslashUndSchraegstrich() {
        assertTrue(PathChars.isSeparator('\\'));
        assertTrue(PathChars.isSeparator('/'));
        for (char c : "a:.-_ \t|*?~".toCharArray()) {
            assertFalse(PathChars.isSeparator(c), "Zeichen " + (int) c);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n", "\r", "\f", "\u000B", "\u00A0", "\u2007", "\u202F", "\u2028", "\u2029",
            "\u3000", "\u2003", "\u0000", "\u001F", "\u007F", "\u0085", "\"", "`", "<", ">", "|"})
    void endzeichenBeendenEinenLauf(String zeichen) {
        assertTrue(PathChars.isTerminator(zeichen.charAt(0)), "Zeichen " + (int) zeichen.charAt(0));
    }

    @Test
    void endzeichenListeIstNichtZuGross() {
        // Plan: NICHT Endzeichen sind ' * ? ( ) [ ] { } = , ; : % $ ~ @ # & + !
        for (char c : "'*?()[]{}=,;:%$~@#&+!.-_/\\aZ09".toCharArray()) {
            assertFalse(PathChars.isTerminator(c), "Zeichen " + c);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00AD", "\u200B", "\u200C", "\u200D", "\u200E", "\u200F", "\u202A", "\u202E", "\u2060",
            "\uFEFF", "\u0301", "\u00E4", "\u00DF"})
    void unsichtbareFormatzeichenUndUmlauteBeendenKeinenLauf(String zeichen) {
        assertFalse(PathChars.isTerminator(zeichen.charAt(0)), "Zeichen " + (int) zeichen.charAt(0));
    }

    @Test
    void surrogateBeendenKeinenLauf() {
        String emoji = "\uD83D\uDE00";
        assertFalse(PathChars.isTerminator(emoji.charAt(0)));
        assertFalse(PathChars.isTerminator(emoji.charAt(1)));
    }

    @Test
    void startgrenzeAmTextanfang() {
        assertTrue(PathChars.isStartBoundary("C:", 0));
        assertTrue(PathChars.isStartBoundary("", 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n", "\r", "\u00A0", "\"", "'", "`", "(", "[", "{", "<", ">", "=", ",", ";", "|",
            "\u0000"})
    void startgrenzeNachLeerraumOderOeffnendemZeichen(String davor) {
        assertTrue(PathChars.isStartBoundary(davor + "x", 1), "Zeichen " + (int) davor.charAt(0));
    }

    /** Plan Nachtrag Punkt 3b: alle 13 Anfuehrungszeichen aus T-11 sind Startgrenze, auch die typografischen. */
    @ParameterizedTest
    @ValueSource(strings = {"\"", "\u201c", "\u201d", "\u201e", "\u00ab", "\u00bb", "'", "\u2018", "\u2019",
            "\u201a", "\u2039", "\u203a", "`"})
    void startgrenzeNachJedemDerDreizehnAnfuehrungszeichen(String davor) {
        assertTrue(PathChars.isStartBoundary(davor + "/x", 1), "Zeichen " + (int) davor.charAt(0));
        assertTrue(PathChars.quoteKind(davor.charAt(0)) >= 0);
    }

    @Test
    void startgrenzeHatGenauDieDreizehnAnfuehrungszeichenPlusOeffnendeKlammernUndTrenner() {
        String sonst = " \t\n\r\u00a0\u2028\u2029\u0000([{<>=,;|";
        int erfuellt = 0;
        for (char c = 0; c < 0x3000; c++) {
            boolean erwartet = ALLE_ANFUEHRUNGSZEICHEN.indexOf(c) >= 0 || sonst.indexOf(c) >= 0
                    || Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c);
            assertEquals(erwartet, PathChars.isStartBoundary(c + "x", 1), "Zeichen " + (int) c);
            if (erwartet) {
                erfuellt++;
            }
        }
        assertTrue(erfuellt > 13);
    }

    @ParameterizedTest
    @ValueSource(strings = {":", "/", "\\", "a", "Z", "5", ".", "-", "_", ")", "]", "}", "~", "\u0301",
            "\u00E4", "\u200B", "\uFEFF", "%", "$", "@", "!", "?", "*", "+"})
    void keineStartgrenzeNachBuchstabeZifferPunktUndAnderen(String davor) {
        assertFalse(PathChars.isStartBoundary(davor + "x", 1), "Zeichen " + (int) davor.charAt(0));
    }

    @Test
    void keineStartgrenzeInHttpsUndDatum() {
        String url = "https://e.invalid/a/b";
        assertFalse(PathChars.isStartBoundary(url, url.indexOf("/a/b")));
        assertFalse(PathChars.isStartBoundary("12/2025", 2));
        assertFalse(PathChars.isStartBoundary("und/oder", 3));
        assertFalse(PathChars.isStartBoundary("file:///C:", 8));
    }

    @Test
    void hostZeichenSindNurAsciiBuchstabenZiffernPunktUnterstrichUndMinus() {
        for (char c = 0; c < 0x3000; c++) {
            boolean erwartet = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.'
                    || c == '_' || c == '-';
            assertEquals(erwartet, PathChars.isHostChar(c), "Zeichen " + (int) c);
            assertEquals((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'), PathChars.isAsciiLetter(c),
                    "Zeichen " + (int) c);
            assertFalse(PathChars.isHostChar(c) && PathChars.isTerminator(c), "Endzeichen " + (int) c);
        }
    }

    @Test
    void startgrenzeNachSurrogatpaarIstNichtErfuellt() {
        assertFalse(PathChars.isStartBoundary("\uD83D\uDE00x", 2));
    }

    @Test
    void kuerzungszeichenAmEnde() {
        for (char c : ".,;:!?'".toCharArray()) {
            assertTrue(PathChars.isTrailingPunctuation(c), "Zeichen " + c);
        }
        for (char c : "*)]}\\/a-_(\"%$~".toCharArray()) {
            assertFalse(PathChars.isTrailingPunctuation(c), "Zeichen " + c);
        }
    }

    @Test
    void klammerarten() {
        assertEquals(0, PathChars.closingKind(')'));
        assertEquals(1, PathChars.closingKind(']'));
        assertEquals(2, PathChars.closingKind('}'));
        assertEquals(-1, PathChars.closingKind('('));
        assertEquals(0, PathChars.openingKind('('));
        assertEquals(1, PathChars.openingKind('['));
        assertEquals(2, PathChars.openingKind('{'));
        assertEquals(-1, PathChars.openingKind(')'));
        assertTrue(PathChars.isClosingBracket(')'));
        assertTrue(PathChars.isClosingBracket(']'));
        assertTrue(PathChars.isClosingBracket('}'));
        assertFalse(PathChars.isClosingBracket('('));
        assertFalse(PathChars.isClosingBracket('a'));
    }

    @Test
    void laufwerkKopf() {
        PathForm f = PathForm.LAUFWERK;
        assertEquals(3, f.headEnd("C:\\x", 0));
        assertEquals(3, f.headEnd("c:/x", 0));
        assertEquals(5, f.headEnd("abC:\\x", 2));
        assertEquals(-1, f.headEnd("C:", 0));
        assertEquals(-1, f.headEnd("C:x", 0));
        assertEquals(-1, f.headEnd("1:\\x", 0));
        assertEquals(-1, f.headEnd("\u00E4:\\x", 0));
        assertEquals(-1, f.headEnd("C;\\x", 0));
        assertEquals(-1, f.headEnd("", 0));
        assertEquals(-1, f.headEnd("C:\\", 1));
        assertEquals(3, f.headEnd("C:\\", 0));
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(1, f.minSegments());
        assertEquals(0.95, f.confidence(3, 1));
        assertEquals(0.95, f.confidence(3, 3));
        assertFalse(f.requiresStartBoundary());
    }

    // ------------------------------------------------------------------ Anfuehrungszeichen (T-11)

    private static final String ALLE_ANFUEHRUNGSZEICHEN = "\"\u201C\u201D\u201E\u00AB\u00BB'\u2018\u2019\u201A\u2039\u203A`";

    @Test
    void jedesAnfuehrungszeichenHatEineArtUndWirdVonSichSelbstGeschlossen() {
        assertEquals(PathChars.QUOTE_KINDS, ALLE_ANFUEHRUNGSZEICHEN.length());
        for (int i = 0; i < ALLE_ANFUEHRUNGSZEICHEN.length(); i++) {
            char c = ALLE_ANFUEHRUNGSZEICHEN.charAt(i);
            assertEquals(i, PathChars.quoteKind(c), "Zeichen " + (int) c);
            assertTrue(PathChars.closesQuote(i, c), "schliesst sich selbst: " + (int) c);
        }
        for (char c : "a:\\/ \t<>|*(".toCharArray()) {
            assertEquals(-1, PathChars.quoteKind(c), "Zeichen " + (int) c);
        }
    }

    @Test
    void geradesDoppeltesZeichenSchliesstMitGeradenUndTypografischenZeichen() {
        assertTrue(PathChars.closesQuote(0, '"'));
        assertTrue(PathChars.closesQuote(0, '\u201C'));
        assertTrue(PathChars.closesQuote(0, '\u201D'));
        assertFalse(PathChars.closesQuote(0, '\u201E'));
        assertFalse(PathChars.closesQuote(0, '\u00BB'));
        assertFalse(PathChars.closesQuote(0, '\''));
        assertFalse(PathChars.closesQuote(0, '`'));
    }

    @Test
    void typografischeDoppelteZeichenSchliessenMitJedemDerDoppeltenFamilie() {
        for (int kind = 1; kind <= 5; kind++) {
            for (int j = 0; j <= 5; j++) {
                assertTrue(PathChars.closesQuote(kind, ALLE_ANFUEHRUNGSZEICHEN.charAt(j)), kind + "/" + j);
            }
            for (int j = 6; j < ALLE_ANFUEHRUNGSZEICHEN.length(); j++) {
                assertFalse(PathChars.closesQuote(kind, ALLE_ANFUEHRUNGSZEICHEN.charAt(j)), kind + "/" + j);
            }
        }
    }

    @Test
    void einfacheZeichenSchliessenNurInIhrerFamilieUndBacktickNurMitBacktick() {
        assertTrue(PathChars.closesQuote(6, '\''));
        assertTrue(PathChars.closesQuote(6, '\u2018'));
        assertTrue(PathChars.closesQuote(6, '\u2019'));
        assertFalse(PathChars.closesQuote(6, '\u201A'));
        assertFalse(PathChars.closesQuote(6, '"'));
        for (int kind = 7; kind <= 11; kind++) {
            for (int j = 6; j <= 11; j++) {
                assertTrue(PathChars.closesQuote(kind, ALLE_ANFUEHRUNGSZEICHEN.charAt(j)), kind + "/" + j);
            }
            assertFalse(PathChars.closesQuote(kind, '"'));
            assertFalse(PathChars.closesQuote(kind, '`'));
        }
        assertTrue(PathChars.closesQuote(12, '`'));
        assertFalse(PathChars.closesQuote(12, '\''));
        assertFalse(PathChars.closesQuote(12, '"'));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\t", "\u0000", "\u007F", "\u0085", "\u2028", "\u2029", "<", ">", "|"})
    void abbruchzeichenBeendenDieSucheFuerJedeArt(String zeichen) {
        for (int kind = 0; kind < PathChars.QUOTE_KINDS; kind++) {
            assertTrue(PathChars.abortsQuoteSearch(kind, zeichen.charAt(0)), kind + "/" + (int) zeichen.charAt(0));
        }
    }

    @Test
    void leerzeichenBeendenDieSucheNichtUndGeradesDoppeltesZeichenNurAusserhalbDerDoppeltenFamilie() {
        for (int kind = 0; kind < PathChars.QUOTE_KINDS; kind++) {
            assertFalse(PathChars.abortsQuoteSearch(kind, ' '));
            assertFalse(PathChars.abortsQuoteSearch(kind, '\u00A0'));
            assertFalse(PathChars.abortsQuoteSearch(kind, 'a'));
            assertEquals(kind > 5, PathChars.abortsQuoteSearch(kind, '"'), "Art " + kind);
        }
    }
}
