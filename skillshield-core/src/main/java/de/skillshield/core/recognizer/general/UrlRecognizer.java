package de.skillshield.core.recognizer.general;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt Adressen mit beliebigem Schema vor {@code ://} rein per Zeichenprüfung (ohne Netzwerkbibliotheken, keine
 * Namensauflösung, kein Regex).
 *
 * <p>Suchverfahren (ein Durchlauf von links nach rechts), verankert am Trenner {@code ://}:
 * <ol>
 *   <li>Schema ist der Lauf aus ASCII-Zeichen {@code [A-Za-z0-9+.-]} direkt vor dem Trenner, so weit wie möglich nach
 *       links gelesen, höchstens {@value #MAX_SCHEME_LENGTH} Zeichen (ist der Lauf länger, zählen die letzten
 *       {@value #MAX_SCHEME_LENGTH}). Das Schema beginnt am ersten Buchstaben des Laufs; enthält der Lauf keinen
 *       Buchstaben (leer, {@code 1://}, {@code .://}), gibt es keinen Fund. Davorstehende Buchstaben und Ziffern gehören
 *       damit zum Schema ({@code xhttps://…} beginnt bei {@code x}, {@code siehe-https://…} bei {@code siehe}).</li>
 *   <li>Vorangestellte Präfixe der Form {@code [A-Za-z][A-Za-z0-9+.-]*:} gehören zum Fund ({@code jdbc:postgresql://…},
 *       {@code URL:https://…}), auch mehrfach; zusammen mit dem Schema höchstens {@value #MAX_PREFIXED_LENGTH} Zeichen vor
 *       dem Trenner. Es wird nie vor das Ende des vorigen Funds zurückgelesen.</li>
 *   <li>Ausnahme: Ist das Schema genau {@code file} (Groß- und Kleinschreibung egal), gibt es keinen Fund; das regelt
 *       der Pfad-Erkenner.</li>
 *   <li>Der Rumpf reicht bis zum ersten Leerraum ({@code \s}, Trenn-Zeichen der Kategorie Z), Steuerzeichen, einzelnes
 *       Surrogat oder eines der Zeichen {@code < > "}. Unsichtbare Formatzeichen (U+00AD, U+200B bis U+200F, U+2060,
 *       U+FEFF u. a.), nicht zugewiesene und Private-Use-Zeichen beenden den Rumpf nicht; Zugangsdaten
 *       ({@code nutzer:geheim@host}), Port, Pfad, Query, Fragment, IP-Hosts und Umlaute gehören dazu. Zwei Adressen ohne
 *       Leerraum dazwischen ergeben einen Fund.</li>
 *   <li>Am Ende werden wiederholt entfernt: {@code . , ; : ! ? '} und {@code "}, sowie {@code )} oder {@code ]},
 *       solange im Fund mehr schließende als öffnende Klammern dieser Art stehen. Bleibt vom Rumpf nichts übrig, gibt
 *       es keinen Fund.</li>
 * </ol>
 *
 * <p>Bekannte Grenzen: ein unsichtbares Zeichen im Schema oder im Trenner verhindert den Fund; {@code "}, {@code <},
 * {@code >} in Zugangsdaten oder Query beenden den Fund; eine über einen Zeilenumbruch umbrochene Adresse wird nur bis
 * zum Umbruch erkannt; Adressen ohne {@code ://} ({@code www.…}, {@code mailto:}) sind keine Funde.
 *
 * <p>Der Fund ist stets genau ein zusammenhängender Bereich vom Schema (bzw. Präfix) bis zum bereinigten Ende; es wird
 * nichts geteilt oder verkürzt außer dem Entfernen der Endzeichen, daher entstehen keine überlappenden Funde. Die Suche
 * nach dem nächsten Fund beginnt hinter dem Ende des vorigen.
 *
 * <p>Laufzeit: linear. Jede Position wird beim Suchen von {@code ://}, beim Lesen des Rumpfs und beim Entfernen der
 * Endzeichen höchstens konstant oft besucht; je Treffer von {@code ://} werden höchstens eine konstante Zahl von
 * Zeichen davor geprüft. Keine Rekursion. Konfidenz 0.95, Quelle {@code url}. Der Erkenner ist zustandslos.
 */
public final class UrlRecognizer implements Recognizer {

    /** Quellname der Funde. */
    public static final String SOURCE = "url";
    /** Konfidenz der Funde (Stufe SICHER). */
    public static final double CONFIDENCE = 0.95;

    private static final String SEPARATOR = "://";
    private static final String FILE_SCHEME = "file";
    /** Höchstlänge eines Schema-Laufs (und eines Präfix-Laufs) vor {@code ://} bzw. {@code :}. */
    static final int MAX_SCHEME_LENGTH = 32;
    /** Höchstlänge von Präfixen und Schema zusammen vor {@code ://}. */
    static final int MAX_PREFIXED_LENGTH = 64;
    private static final String TRAILING_PUNCTUATION = ".,;:!?'\"";

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> result = new ArrayList<>();
        int searchFrom = 0;
        int sep;
        while ((sep = text.indexOf(SEPARATOR, searchFrom)) >= 0) {
            int bodyStart = sep + SEPARATOR.length();
            searchFrom = bodyStart;
            int start = findStart(text, sep, result.isEmpty() ? 0 : result.get(result.size() - 1).end());
            if (start < 0) {
                continue;
            }
            int end = trimEnd(text, bodyStart, bodyEnd(text, bodyStart));
            if (end <= bodyStart) {
                continue;
            }
            result.add(new Finding(FindingType.URL, text.substring(start, end), start, end, CONFIDENCE, SOURCE));
            searchFrom = end;
        }
        return result;
    }

    /**
     * Anfang des Funds (Schema samt Präfixen) vor dem Trenner bei {@code sep} oder -1, wenn kein Fund entsteht
     * (kein Schema oder Schema {@code file}). Es wird nicht vor {@code lowerBound} zurückgelesen.
     */
    private static int findStart(String text, int sep, int lowerBound) {
        int schemeFrom = runStart(text, sep, lowerBound);
        if (schemeFrom < 0) {
            return -1;
        }
        if (sep - schemeFrom == FILE_SCHEME.length() && text.regionMatches(true, schemeFrom, FILE_SCHEME, 0, FILE_SCHEME.length())) {
            return -1;
        }
        int start = schemeFrom;
        while (start - 1 >= lowerBound && text.charAt(start - 1) == ':') {
            int prefixFrom = runStart(text, start - 1, lowerBound);
            if (prefixFrom < 0 || sep - prefixFrom > MAX_PREFIXED_LENGTH) {
                break;
            }
            start = prefixFrom;
        }
        return start;
    }

    /**
     * Beginn des Schema-Laufs, der direkt vor {@code end} endet: höchstens {@link #MAX_SCHEME_LENGTH} Schemazeichen,
     * nicht vor {@code lowerBound}, ab dem ersten Buchstaben darin. -1, wenn der Lauf keinen Buchstaben enthält.
     */
    private static int runStart(String text, int end, int lowerBound) {
        int limit = Math.max(lowerBound, end - MAX_SCHEME_LENGTH);
        int p = end;
        while (p > limit && isSchemeChar(text.charAt(p - 1))) {
            p--;
        }
        while (p < end && !isAsciiLetter(text.charAt(p))) {
            p++;
        }
        return p < end ? p : -1;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isSchemeChar(char c) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '+' || c == '.' || c == '-';
    }

    /** Ende des Rumpfs: Position des ersten Zeichens ab {@code from}, das den Rumpf beendet (oder Textende). */
    private static int bodyEnd(String text, int from) {
        int p = from;
        int n = text.length();
        while (p < n) {
            int cp = text.codePointAt(p);
            if (endsBody(cp)) {
                break;
            }
            p += Character.charCount(cp);
        }
        return p;
    }

    private static boolean endsBody(int cp) {
        if (cp == '<' || cp == '>' || cp == '"') {
            return true;
        }
        switch (Character.getType(cp)) {
            case Character.SPACE_SEPARATOR:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.CONTROL:
            case Character.SURROGATE: // nur ein einzelnes Surrogat; gültige Paare liefert codePointAt als ein Zeichen
                return true;
            default:
                return false;
        }
    }

    /** Entfernt Endzeichen vom Bereich {@code [bodyStart, end)} und liefert das neue Ende. */
    private static int trimEnd(String text, int bodyStart, int end) {
        int openParen = 0;
        int closeParen = 0;
        int openBracket = 0;
        int closeBracket = 0;
        for (int k = bodyStart; k < end; k++) {
            switch (text.charAt(k)) {
                case '(' -> openParen++;
                case ')' -> closeParen++;
                case '[' -> openBracket++;
                case ']' -> closeBracket++;
                default -> { }
            }
        }
        int e = end;
        while (e > bodyStart) {
            char c = text.charAt(e - 1);
            if (TRAILING_PUNCTUATION.indexOf(c) >= 0) {
                e--;
            } else if (c == ')' && closeParen > openParen) {
                closeParen--;
                e--;
            } else if (c == ']' && closeBracket > openBracket) {
                closeBracket--;
                e--;
            } else {
                break;
            }
        }
        return e;
    }
}
