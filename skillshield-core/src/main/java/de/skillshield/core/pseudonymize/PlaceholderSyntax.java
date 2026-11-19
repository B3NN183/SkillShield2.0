package de.skillshield.core.pseudonymize;

import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Format von Platzhaltern {@code [[TYP_n]]} und die einzige Stelle, die Platzhalter in Text erkennt.
 *
 * <p>Der Tokenizer ist ein handgeschriebener Scanner ohne regulären Ausdruck: Jeder Versuch an einer Position
 * liest höchstens 62 Zeichen, danach geht es entweder hinter das Token oder um ein Zeichen weiter. Die Laufzeit ist
 * damit linear in der Textlänge, auch bei {@code [} oder {@code [[A_} in beliebiger Wiederholung.
 *
 * <p>Erkannt werden (Architektur-Plan Kap. 2.7, links nach rechts, T vor D):
 * <ul>
 *   <li>T (Platzhalter): {@code [[}, bis zu 8 Leerzeichen, Typname ({@code [A-Za-z][A-Za-z0-9]{0,31}}),
 *       {@code _}, Nummer ({@code [1-9][0-9]{0,8}}), bis zu 8 Leerzeichen, {@code ]]}. Kanonisch ist
 *       {@code [[TYP_n]]} in Großschreibung ohne Leerzeichen.</li>
 *   <li>D (beschädigt): ein oder zwei {@code [}, bis zu 8 Leerzeichen, Typname, {@code _}, bis zu 9 Ziffern, bis zu
 *       8 Leerzeichen, bis zu zwei {@code ]}. Wird nur gemeldet, wenn der (große) Typname bekannt ist; sonst wird
 *       der Ausschnitt verbraucht, aber nicht gemeldet.</li>
 * </ul>
 * Nur ASCII-Zeichen zählen. Vollbreite-Klammern, Ziffern anderer Schriften und unsichtbare Zeichen erzeugen kein
 * Token (ein unsichtbares Zeichen innerhalb des Platzhalters bricht den Platzhalter).
 */
public final class PlaceholderSyntax {

    private static final String OPEN = "[[";
    private static final String CLOSE = "]]";
    private static final int MAX_DIGITS = 9;
    private static final int MAX_SPACES = 8;

    private PlaceholderSyntax() {
    }

    /**
     * Ein erkannter Ausschnitt. {@code start} inklusive, {@code end} exklusiv.
     *
     * @param start     Beginn im Text
     * @param end       Ende (exklusiv) im Text
     * @param kind      Art des Tokens
     * @param canonical bei {@link Kind#PLATZHALTER} die kanonische Form {@code [[TYP_n]]}, bei
     *                  {@link Kind#BESCHAEDIGT} {@code null}
     */
    public record Token(int start, int end, Kind kind, String canonical) {

        public enum Kind { PLATZHALTER, BESCHAEDIGT }

        public Token {
            Objects.requireNonNull(kind, "kind");
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("Ungültiger Bereich");
            }
            if ((kind == Kind.PLATZHALTER) != (canonical != null)) {
                throw new IllegalArgumentException("canonical passt nicht zur Art");
            }
        }
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
     * Findet Platzhalter und beschädigte Platzhalter im Bereich {@code [from, to)}.
     *
     * @param text       Text, nicht {@code null}
     * @param from       Beginn (inklusive)
     * @param to         Ende (exklusiv)
     * @param knownTypes bekannte Typnamen in Großschreibung; nur für beschädigte Platzhalter maßgeblich
     * @return unveränderliche Liste in Textreihenfolge, überlappungsfrei
     * @throws NullPointerException      wenn {@code text} oder {@code knownTypes} {@code null} ist
     * @throws IndexOutOfBoundsException wenn der Bereich nicht in den Text passt (Meldung nur mit Zahlen)
     */
    public static List<Token> tokenize(CharSequence text, int from, int to, Set<String> knownTypes) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(knownTypes, "knownTypes");
        Objects.checkFromToIndex(from, to, text.length());
        List<Token> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            if (text.charAt(i) != '[') {
                i++;
                continue;
            }
            int end = scan(text, i, to, knownTypes, out);
            i = end > i ? end : i + 1;
        }
        return Collections.unmodifiableList(out);
    }

    /** Versucht T, dann D ab {@code i} (dort steht {@code [}). Liefert das Ende des verbrauchten Teils oder -1. */
    private static int scan(CharSequence text, int i, int to, Set<String> knownTypes, List<Token> out) {
        int p = i;
        while (p < to && p - i < 2 && text.charAt(p) == '[') {
            p++;
        }
        int opens = p - i;
        int spaces = 0;
        while (p < to && spaces < MAX_SPACES && text.charAt(p) == ' ') {
            p++;
            spaces++;
        }
        int nameStart = p;
        if (p >= to || !FindingType.isNameStart(fold(text.charAt(p)))) {
            return -1;
        }
        p++;
        while (p < to && p - nameStart < FindingType.MAX_LENGTH && FindingType.isNameChar(fold(text.charAt(p)))) {
            p++;
        }
        if (p >= to || text.charAt(p) != '_') {
            return -1;
        }
        int nameEnd = p;
        p++;
        int digitsStart = p;
        while (p < to && p - digitsStart < MAX_DIGITS && isDigit(text.charAt(p))) {
            p++;
        }
        int digitsEnd = p;

        // T: genau zwei [, Nummer ohne führende 0 (höchstens 9 Ziffern), bis 8 Leerzeichen, genau ]]
        if (opens == 2 && digitsEnd > digitsStart && text.charAt(digitsStart) != '0') {
            int q = digitsEnd;
            int closeSpaces = 0;
            while (q < to && closeSpaces < MAX_SPACES && text.charAt(q) == ' ') {
                q++;
                closeSpaces++;
            }
            if (q + 1 < to && text.charAt(q) == ']' && text.charAt(q + 1) == ']') {
                String canonical = OPEN + upper(text, nameStart, nameEnd) + "_"
                        + text.subSequence(digitsStart, digitsEnd) + CLOSE;
                out.add(new Token(i, q + 2, Token.Kind.PLATZHALTER, canonical));
                return q + 2;
            }
        }

        // D: schließende Teile optional, daher gelingt D, sobald Klammer, Typname und _ passen
        int q = digitsEnd;
        int closeSpaces = 0;
        while (q < to && closeSpaces < MAX_SPACES && text.charAt(q) == ' ') {
            q++;
            closeSpaces++;
        }
        int closes = 0;
        while (q < to && closes < 2 && text.charAt(q) == ']') {
            q++;
            closes++;
        }
        if (knownTypes.contains(upper(text, nameStart, nameEnd))) {
            out.add(new Token(i, q, Token.Kind.BESCHAEDIGT, null));
        }
        return q;
    }

    private static char fold(char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String upper(CharSequence text, int from, int to) {
        char[] chars = new char[to - from];
        for (int k = 0; k < chars.length; k++) {
            chars[k] = fold(text.charAt(from + k));
        }
        return new String(chars);
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
        if (!FindingType.isValidName(typ) || !isNumber(nummer)) {
            return null;
        }
        return new String[] {typ, nummer};
    }

    private static boolean isNumber(String s) {
        int len = s.length();
        if (len == 0 || len > MAX_DIGITS || s.charAt(0) < '1' || s.charAt(0) > '9') {
            return false;
        }
        for (int i = 1; i < len; i++) {
            if (!isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
