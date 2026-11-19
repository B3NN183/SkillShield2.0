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
    @ValueSource(strings = {" ", "\t", "\n", "\r", "\u00A0", "\"", "'", "`", "(", "[", "{", "<", ">", "=", "\u0000"})
    void startgrenzeNachLeerraumOderOeffnendemZeichen(String davor) {
        assertTrue(PathChars.isStartBoundary(davor + "x", 1), "Zeichen " + (int) davor.charAt(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {":", "/", "\\", "a", "Z", "5", ".", "-", "_", ")", "]", "}", ",", ";", "~", "\u0301",
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
    void startgrenzeNachSurrogatpaarIstNichtErfuellt() {
        assertFalse(PathChars.isStartBoundary("\uD83D\uDE00x", 2));
    }

    @Test
    void anfuehrungszeichen() {
        assertTrue(PathChars.isQuote('"'));
        assertTrue(PathChars.isQuote('\''));
        assertTrue(PathChars.isQuote('`'));
        assertFalse(PathChars.isQuote('<'));
        assertFalse(PathChars.isQuote('a'));
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
        assertEquals(0.95, f.confidence(1));
        assertEquals(0.95, f.confidence(3));
        assertFalse(f.requiresStartBoundary());
    }
}
