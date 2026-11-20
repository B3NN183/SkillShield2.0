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
     * Startgrenze vor Position {@code i}: Textanfang, Leerraum oder eines der Zeichen direkt davor: jedes der 13
     * Anführungszeichen aus T-11 ({@link #quoteKind}: {@code "}, {@code '}, Backtick und die typografischen Zeichen
     * U+201C, U+201D, U+201E, U+00AB, U+00BB, U+2018, U+2019, U+201A, U+2039, U+203A; Nachtrag 20.11.2026 Punkt 3b),
     * öffnende runde, eckige oder geschweifte Klammer, {@code <}, {@code >}, {@code =} sowie {@code ,}, {@code ;}
     * und {@code |} (Nachtrag Punkt 1: {@code Datei,/etc/hosts}, {@code a;/opt/x/y}, {@code cmd|/opt/x/y}; {@code |}
     * beendet zugleich einen Lauf, die Grenze gilt für den Lauf dahinter). Ein typografisches Zeichen zählt auch
     * unpaarig oder über das Zeilenende hinaus; sonst bliebe der Pfad dahinter im Klartext. Buchstaben, Ziffern,
     * Punkt, Minus, Unterstrich, Doppelpunkt, Trenner und kombinierende Zeichen davor erfüllen die Grenze nicht.
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
        if (quoteKind(p) >= 0) {
            return true;
        }
        switch (p) {
            case '(':
            case '[':
            case '{':
            case '<':
            case '>':
            case '=':
            case ',':
            case ';':
            case '|':
                return true;
            default:
                return false;
        }
    }

    /** ASCII-Buchstabe {@code [A-Za-z]}. */
    static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Ob ab {@code i} das Wort {@code lowerCaseWord} steht, ASCII-Buchstaben ohne Beachtung der Groß- und
     * Kleinschreibung. Bewusst kein {@code regionMatches(true, ...)}: Das faltet auch Nicht-ASCII-Zeichen (etwa U+017F
     * zu {@code s}). {@code lowerCaseWord} besteht nur aus ASCII-Kleinbuchstaben; die Prüfung liest nie über das
     * Textende hinaus.
     */
    static boolean matchesAsciiIgnoreCase(String text, int i, String lowerCaseWord) {
        int len = lowerCaseWord.length();
        if (i < 0 || len > text.length() - i) {
            return false;
        }
        for (int k = 0; k < len; k++) {
            if ((text.charAt(i + k) | 0x20) != lowerCaseWord.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Zeichen eines Rechner- oder Benutzernamens {@code [A-Za-z0-9._-]} (Formen {@code HOST_PFAD} und
     * {@code HOME}); bewusst nur ASCII und kein Endzeichen, damit der Lauf nie über Leerraum reicht und kein
     * Surrogat oder kombinierendes Zeichen enthält.
     */
    static boolean isHostChar(char c) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
    }

    /** Anzahl der Arten öffnender Anführungszeichen, siehe {@link #quoteKind}. */
    static final int QUOTE_KINDS = 13;

    /**
     * Art eines öffnenden Anführungszeichens (0 bis {@link #QUOTE_KINDS} - 1) oder -1. Doppelte Familie (0 bis 5):
     * {@code "}, U+201C, U+201D, U+201E, U+00AB, U+00BB. Einfache Familie (6 bis 11): {@code '}, U+2018, U+2019,
     * U+201A, U+2039, U+203A. Backtick: 12. Jedes Zeichen kann öffnen und schließen; ob ein Paar zustande kommt,
     * regelt {@link #closesQuote}. Dieselbe Menge ist die Startgrenze für UNIX und HOME ({@link #isStartBoundary}).
     */
    static int quoteKind(char c) {
        switch (c) {
            case '"':
                return 0;
            case 0x201C:
                return 1;
            case 0x201D:
                return 2;
            case 0x201E:
                return 3;
            case 0x00AB:
                return 4;
            case 0x00BB:
                return 5;
            case '\'':
                return 6;
            case 0x2018:
                return 7;
            case 0x2019:
                return 8;
            case 0x201A:
                return 9;
            case 0x2039:
                return 10;
            case 0x203A:
                return 11;
            case '`':
                return 12;
            default:
                return -1;
        }
    }

    /**
     * Ob {@code c} ein Anführungszeichen der Art {@code kind} schließt. Das gerade {@code "} schließt mit {@code "},
     * U+201C oder U+201D; ein typografisches doppeltes Zeichen mit jedem Zeichen der doppelten Familie (gemischte
     * Paare wie U+201E ... U+201C oder U+00BB ... U+00AB); das gerade {@code '} mit {@code '}, U+2018 oder U+2019; ein
     * typografisches einfaches Zeichen mit jedem der einfachen Familie; Backtick nur mit Backtick. Jede Art wird
     * auch von ihrem eigenen Zeichen geschlossen; daraus folgt die lineare Laufzeit der Suche (Bereiche gleicher Art
     * überlappen nicht).
     */
    static boolean closesQuote(int kind, char c) {
        if (kind == 0) {
            return c == '"' || c == 0x201C || c == 0x201D;
        }
        if (kind <= 5) {
            return quoteKind(c) >= 0 && quoteKind(c) <= 5;
        }
        if (kind == 6) {
            return c == '\'' || c == 0x2018 || c == 0x2019;
        }
        if (kind <= 11) {
            return quoteKind(c) >= 6 && quoteKind(c) <= 11;
        }
        return c == '`';
    }

    /**
     * Ob die Suche nach dem schließenden Anführungszeichen der Art {@code kind} am Zeichen {@code c} erfolglos
     * endet: Steuerzeichen (Zeilenende, Tab), U+2028, U+2029, {@code <}, {@code >}, {@code |}; für alle Arten außer
     * der doppelten Familie außerdem das gerade {@code "}. Leerzeichen beenden die Suche nicht.
     */
    static boolean abortsQuoteSearch(int kind, char c) {
        return Character.isISOControl(c) || c == 0x2028 || c == 0x2029 || c == '<' || c == '>' || c == '|'
                || (kind > 5 && c == '"');
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

    /** Schließende runde, eckige oder geschweifte Klammer; am Ende nur gekürzt, wenn im Fund nicht geöffnet. */
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
