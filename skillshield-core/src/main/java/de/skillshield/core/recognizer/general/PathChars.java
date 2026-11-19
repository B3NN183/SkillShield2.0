package de.skillshield.core.recognizer.general;

/**
 * Zeichenklassen des Pfad-Erkenners (Architektur-Plan Kap. 2.4.1). Nur statische, zustandslose Prüfungen auf
 * einzelnen {@code char}-Werten; alle hier genannten Sonderzeichen sind BMP-Zeichen außerhalb der Surrogatbereiche
 * und keine kombinierenden Zeichen, damit ein Fund nie ein Surrogatpaar oder eine Kombinationsfolge trennt (AK-23).
 *
 * <p>Keine plattformabhängige Logik: das Ergebnis ist auf jedem Betriebssystem gleich (AK-51).
 */
final class PathChars {

    private PathChars() {
    }

    /** Trenner sind {@code \} und {@code /} (für alle Pfadformen beide, auch gemischt). */
    static boolean isSeparator(char c) {
        return c == '\\' || c == '/';
    }

    /**
     * Endzeichen beenden einen Lauf: Leerraum (Leerzeichen, Tab, Zeilenende, geschütztes Leerzeichen und alle
     * anderen Leerzeichen-Kategorien), ISO-Steuerzeichen sowie {@code "}, Backtick, {@code <}, {@code >} und
     * {@code |}. Unsichtbare Formatzeichen (U+00AD, U+200B bis U+200F, U+2060, U+FEFF u. a.) sind weder Leerraum noch
     * Steuerzeichen und beenden den Pfad daher nicht.
     */
    static boolean isTerminator(char c) {
        return isBlank(c) || c == '"' || c == '`' || c == '<' || c == '>' || c == '|';
    }

    /** Leerraum im Sinne der Startgrenze und der Endzeichen: Leerzeichen-Kategorien und Steuerzeichen. */
    static boolean isBlank(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c);
    }

    /**
     * Startgrenze vor Position {@code i}: Textanfang, Leerraum oder eines der Zeichen Anführungszeichen, Apostroph,
     * Backtick, öffnende runde, eckige oder geschweifte Klammer, {@code <}, {@code >} und {@code =} direkt davor.
     * Buchstaben, Ziffern, Punkt, Minus, Unterstrich, Doppelpunkt, Trenner und kombinierende Zeichen davor erfüllen
     * die Grenze nicht.
     *
     * @param text der Text
     * @param i    Position, {@code 0 <= i <= text.length()}
     */
    static boolean isStartBoundary(String text, int i) {
        if (i == 0) {
            return true;
        }
        char p = text.charAt(i - 1);
        if (isBlank(p)) {
            return true;
        }
        switch (p) {
            case '"':
            case '\'':
            case '`':
            case '(':
            case '[':
            case '{':
            case '<':
            case '>':
            case '=':
                return true;
            default:
                return false;
        }
    }

    /** Anführungszeichen, die einen Pfad umschließen können: {@code "}, {@code '} und Backtick. */
    static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '`';
    }

    /** Satzzeichen, die am Ende eines Fundes immer gekürzt werden: {@code . , ; : ! ?} und {@code '}. */
    static boolean isTrailingPunctuation(char c) {
        switch (c) {
            case '.':
            case ',':
            case ';':
            case ':':
            case '!':
            case '?':
            case '\'':
                return true;
            default:
                return false;
        }
    }

    /** Schließende runde, eckige oder geschweifte Klammer; am Ende nur gekürzt, wenn sie im Fund nicht geöffnet wurde. */
    static boolean isClosingBracket(char c) {
        return c == ')' || c == ']' || c == '}';
    }

    /** Art einer schließenden Klammer: 0 für rund, 1 für eckig, 2 für geschweift, sonst -1. */
    static int closingKind(char c) {
        switch (c) {
            case ')':
                return 0;
            case ']':
                return 1;
            case '}':
                return 2;
            default:
                return -1;
        }
    }

    /** Art einer öffnenden Klammer, Zählung wie bei {@link #closingKind}, sonst -1. */
    static int openingKind(char c) {
        switch (c) {
            case '(':
                return 0;
            case '[':
                return 1;
            case '{':
                return 2;
            default:
                return -1;
        }
    }
}
