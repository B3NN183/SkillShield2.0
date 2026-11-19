package de.skillshield.core.pseudonymize;

import de.skillshield.core.model.FindingType;
import java.util.Objects;

/**
 * Format von Platzhaltern {@code [[TYP_n]]}. Ab T-8 kommt hier die Erkennung (Tokenizer) für Hin- und Rückweg
 * hinzu; dieser Stand kennt nur das Erzeugen und die Prüfung der kanonischen Form.
 */
public final class PlaceholderSyntax {

    private static final String OPEN = "[[";
    private static final String CLOSE = "]]";
    private static final int MAX_DIGITS = 9;

    private PlaceholderSyntax() {
    }

    /**
     * Kanonischer Platzhalter {@code [[TYP_n]]}.
     *
     * @param type Typ, nicht {@code null}
     * @param n    laufende Nummer, mindestens 1
     * @throws NullPointerException     wenn {@code type} {@code null} ist
     * @throws IllegalArgumentException wenn {@code n < 1}
     */
    public static String format(FindingType type, int n) {
        Objects.requireNonNull(type, "type");
        if (n < 1) {
            throw new IllegalArgumentException("n muss mindestens 1 sein");
        }
        return OPEN + type.name() + "_" + n + CLOSE;
    }

    /**
     * Zerlegt einen kanonischen Platzhalter in Typname und Nummer.
     *
     * @return {@code {typname, nummer}} oder {@code null}, wenn {@code s} nicht exakt kanonisch ist
     */
    static String[] parseCanonical(String s) {
        int len = s.length();
        if (len < OPEN.length() + CLOSE.length() + 3 || !s.startsWith(OPEN) || !s.endsWith(CLOSE)) {
            return null;
        }
        int innerStart = OPEN.length();
        int innerEnd = len - CLOSE.length();
        int underscore = -1;
        for (int i = innerStart; i < innerEnd; i++) {
            if (s.charAt(i) == '_') {
                if (underscore >= 0) {
                    return null;
                }
                underscore = i;
            }
        }
        if (underscore < 0) {
            return null;
        }
        String typ = s.substring(innerStart, underscore);
        String nummer = s.substring(underscore + 1, innerEnd);
        if (!isTypeName(typ) || !isNumber(nummer)) {
            return null;
        }
        return new String[] {typ, nummer};
    }

    private static boolean isTypeName(String s) {
        int len = s.length();
        if (len == 0 || len > FindingType.MAX_LENGTH) {
            return false;
        }
        char first = s.charAt(0);
        if (first < 'A' || first > 'Z') {
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(String s) {
        int len = s.length();
        if (len == 0 || len > MAX_DIGITS || s.charAt(0) < '1' || s.charAt(0) > '9') {
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
