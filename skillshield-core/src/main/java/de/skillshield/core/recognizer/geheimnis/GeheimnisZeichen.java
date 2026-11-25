package de.skillshield.core.recognizer.geheimnis;

import java.util.List;

/**
 * Zeichenklassen und Vergleiche der Geheimnis-Erkenner (Plan geheimnisse-erkennung, Kapitel 1.7, OF-10). Alles ist
 * paketintern; die Klasse hat keinen Zustand und liest nur.
 *
 * <p>Gelesen wird nach Codepoints. Buchstabe ist {@link Character#isLetter(int)}, Ziffer
 * {@link Character#isDigit(int)}, Grossbuchstabe {@link Character#isUpperCase(int)}, Kleinbuchstabe
 * {@link Character#isLowerCase(int)} (Umlaute zaehlen, {@code ss}-Eszett ist klein). Kombinierende Zeichen (Mn, Mc, Me)
 * gehoeren zum Wortkoerper. Zeilenumbruch sind LF, CR (CR LF als einer), U+0085, U+2028 und U+2029. Leerraum ist
 * jedes Zeichen mit {@link Character#isWhitespace(int)} oder {@link Character#isSpaceChar(int)}, das kein
 * Zeilenumbruch ist; damit gelten auch U+00A0, U+2009 und U+202F wie ein Leerzeichen (AK-45).
 *
 * <p>Vergleiche mit Staemmen, Listen und Ankern erfolgen zeichenweise nur in ASCII: {@code A-Z} wird zu {@code a-z},
 * jedes Nicht-ASCII-Zeichen passt nie (kein {@code toLowerCase}, keine Locale, kein Sonderfall fuer
 * {@code I mit Punkt}). Einzige Ausnahme ist das Zwischenwort {@code fuer} der Liste in {@code KontextRegel}: dort
 * gilt der Umlaut in beiden Schreibweisen (U+00FC und U+00DC), damit eine Ueberschrift in Grossbuchstaben nicht an
 * einem Umlaut scheitert. Unsichtbare Formatzeichen (U+200B u. a.) sind kein Wortkoerperzeichen.
 */
final class GeheimnisZeichen {

    /** Kleinbuchstabe u mit Umlaut. */
    static final char UE_KLEIN = (char) 0xFC;
    /** Grossbuchstabe U mit Umlaut. */
    static final char UE_GROSS = (char) 0xDC;

    private GeheimnisZeichen() {
    }

    /** Buchstabe nach {@link Character#isLetter(int)}. */
    static boolean istBuchstabe(int cp) {
        return Character.isLetter(cp);
    }

    /** Ziffer nach {@link Character#isDigit(int)}. */
    static boolean istZiffer(int cp) {
        return Character.isDigit(cp);
    }

    /** ASCII-Ziffer {@code 0-9}. */
    static boolean istAsciiZiffer(int c) {
        return c >= '0' && c <= '9';
    }

    /** ASCII-Grossbuchstabe {@code A-Z}. */
    static boolean istAsciiGross(int c) {
        return c >= 'A' && c <= 'Z';
    }

    /** ASCII-Buchstabe oder -Ziffer. */
    static boolean istAsciiBuchstabeOderZiffer(int c) {
        return istAsciiZiffer(c) || istAsciiGross(c) || (c >= 'a' && c <= 'z');
    }

    /** Kombinierendes Zeichen (Mn, Mc, Me). */
    static boolean istKombinierend(int cp) {
        int typ = Character.getType(cp);
        return typ == Character.NON_SPACING_MARK || typ == Character.COMBINING_SPACING_MARK
                || typ == Character.ENCLOSING_MARK;
    }

    /** Zeichen eines Wortkoerpers: Buchstabe, Ziffer, kombinierendes Zeichen, {@code _} und {@code -}. */
    static boolean istWortkoerperZeichen(int cp) {
        return cp == '_' || cp == '-' || istBuchstabe(cp) || istZiffer(cp) || istKombinierend(cp);
    }

    /** Ein Zeichen eines Zeilenumbruchs: LF, CR, U+0085, U+2028, U+2029. */
    static boolean istZeilenumbruch(int cp) {
        return cp == '\n' || cp == '\r' || cp == 0x85 || cp == 0x2028 || cp == 0x2029;
    }

    /** Leerraum ohne Zeilenumbruch. */
    static boolean istLeerraum(int cp) {
        return !istZeilenumbruch(cp) && (Character.isWhitespace(cp) || Character.isSpaceChar(cp));
    }

    /**
     * Laenge des Zeilenumbruchs an der Stelle {@code pos}: 2 fuer CR LF, 1 fuer ein anderes Umbruchzeichen, sonst 0.
     */
    static int zeilenumbruchLaenge(String text, int pos) {
        if (pos >= text.length()) {
            return 0;
        }
        char c = text.charAt(pos);
        if (c == '\r') {
            return pos + 1 < text.length() && text.charAt(pos + 1) == '\n' ? 2 : 1;
        }
        return istZeilenumbruch(c) ? 1 : 0;
    }

    /** Gleicht {@code A-Z} an {@code a-z} an; alle anderen Zeichen bleiben unveraendert. */
    static char asciiKlein(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    /**
     * Ob {@code text[von, bis)} dem Kleinbuchstaben-Wort {@code klein} entspricht: {@code A-Z} gilt wie {@code a-z},
     * der Umlaut U+00DC wie U+00FC, sonst muss jedes Zeichen gleich sein (Nicht-ASCII passt nie, ausser diesem
     * Umlaut).
     */
    static boolean gleichKlein(String text, int von, int bis, String klein) {
        if (bis - von != klein.length()) {
            return false;
        }
        for (int i = 0; i < klein.length(); i++) {
            char c = text.charAt(von + i);
            if (c == UE_GROSS) {
                c = UE_KLEIN;
            } else {
                c = asciiKlein(c);
            }
            if (c != klein.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Ob {@code text[..ende)} auf das Kleinbuchstaben-Wort {@code klein} endet (nur ASCII, Gross/klein egal). */
    static boolean endetMit(String text, int ende, String klein) {
        int von = ende - klein.length();
        if (von < 0) {
            return false;
        }
        for (int i = 0; i < klein.length(); i++) {
            if (asciiKlein(text.charAt(von + i)) != klein.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Ob der Wert einem Eintrag der Kleinbuchstaben-Liste entspricht (nur ASCII, Gross/klein egal). */
    static boolean inListe(String wert, List<String> liste) {
        for (String eintrag : liste) {
            if (gleichKlein(wert, 0, wert.length(), eintrag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alle Anfuehrungszeichen im Sinne von D-7 (gerade, Gravis und typografisch): {@code "}, {@code '}, Gravis,
     * U+201E, U+201C, U+201D, U+201A, U+2018, U+2019, U+00BB, U+00AB, U+203A und U+2039.
     */
    static final String ANFUEHRUNG = "\"'`" + (char) 0x201E + (char) 0x201C + (char) 0x201D + (char) 0x201A
            + (char) 0x2018 + (char) 0x2019 + (char) 0xBB + (char) 0xAB + (char) 0x203A + (char) 0x2039;

    /** Ob das Zeichen ein oeffnendes Anfuehrungszeichen im Sinne von D-7 ist (gerade und typografisch). */
    static boolean istAnfuehrungOeffnend(int c) {
        return ANFUEHRUNG.indexOf(c) >= 0;
    }

    /** Ob {@code zu} ein zu {@code offen} passendes schliessendes Anfuehrungszeichen ist (D-7). */
    static boolean schliesst(int offen, int zu) {
        switch (offen) {
            case '"':
            case '\'':
            case '`':
                return zu == offen;
            case 0x201E:
                return zu == 0x201C || zu == 0x201D;
            case 0x201C:
                return zu == 0x201D;
            case 0x201D:
                return zu == 0x201D;
            case 0x201A:
                return zu == 0x2018 || zu == 0x2019;
            case 0x2018:
                return zu == 0x2019;
            case 0x2019:
                return zu == 0x2019;
            case 0xBB:
                return zu == 0xAB;
            case 0xAB:
                return zu == 0xBB;
            case 0x203A:
                return zu == 0x2039;
            case 0x2039:
                return zu == 0x203A;
            default:
                return false;
        }
    }

    /** Ob das Zeichen ein Auszeichnungszeichen ist ({@code *}, {@code _}, Gravis; Entscheidung 6 vom 25.11.2026). */
    static boolean istAuszeichnung(int c) {
        return c == '*' || c == '_' || c == '`';
    }

    /**
     * Laenge der Folge gleicher Zeichen an {@code pos}, gelesen hoechstens bis {@code grenze} (ist die Folge
     * laenger, wird {@code grenze} geliefert). Liefert 0 am Textende.
     */
    static int folgenLaenge(String text, int pos, int grenze) {
        if (pos >= text.length()) {
            return 0;
        }
        char c = text.charAt(pos);
        int i = pos + 1;
        while (i < text.length() && i - pos < grenze && text.charAt(i) == c) {
            i++;
        }
        return i - pos;
    }
}
