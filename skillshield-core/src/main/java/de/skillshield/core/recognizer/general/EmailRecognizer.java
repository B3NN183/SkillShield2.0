package de.skillshield.core.recognizer.general;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt E-Mail-Adressen rein per Zeichenprüfung (keine DNS-/MX-Prüfung, kein Netzwerk).
 *
 * <p>Suchverfahren: Der Erkenner sucht jedes {@code @} und geht von dort höchstens 64 Zeichen nach links über
 * Zeichen des lokalen Teils (nicht über das Ende des letzten Fundes hinaus), rückt zum ersten Buchstaben oder
 * Ziffer vor und wendet das Muster genau dort an. Füllzeichen wie {@code . _ - = !} vor der Adresse verhindern
 * den Fund daher nicht, werden aber mitgenommen, soweit sie zwischen dem ersten Buchstaben und dem {@code @} liegen
 * ({@code mail=erika@...}, {@code Danke!erika@...}); Zeichen vor dem ersten Buchstaben bleiben draußen. Ist der
 * lokale Teil länger als 64 Zeichen, entsteht ein Teilfund aus den letzten höchstens 64 Zeichen vor dem {@code @}
 * samt Domain; ebenso ein Teilfund bis zur Längengrenze, wenn ein Folgelabel der Domain länger als 63 Zeichen ist.
 * Bis zu vier Paare "lokaler Teil und {@code @}" ({@code a@b@example.invalid}) gehören zu einem Fund; ein
 * {@code @} direkt hinter der Domain gehört nicht zum Fund. Folgt auf einen Fund direkt ein {@code @}, wird geprüft,
 * ob das letzte Domain-Label der lokale Teil einer weiteren Adresse ist: Ergibt sowohl der Fund bis vor dem letzten
 * Punkt als auch die Adresse ab diesem Label einen Fund, entstehen zwei Funde (dies wiederholt sich); sonst bleibt der
 * ursprüngliche Fund unverändert. Dabei werden Formatzeichen am Trenner den Teilfunden zugeschlagen. Hängt hinter
 * einem Fund noch ein {@code @} samt Domain (mehr als vier Paare), wird dieser Rest als eigener Fund gemeldet (er
 * beginnt dann mit dem {@code @}), sofern kein folgender Fund im Weg steht.
 *
 * <p>Obermengen-Garantie: {@code detect} berechnet immer auch das ungekürzte Ergebnis (nur das Muster, ohne Aufteilen
 * und ohne Rest-Funde) und gleicht beide ab. Jedes Zeichen, das das ungekürzte Ergebnis abdeckt, ist auch im Ergebnis
 * von {@code detect} abgedeckt, ausgenommen einzelne Trenner-Punkte direkt zwischen zwei Funden. Wo das Aufteilen
 * etwas freilassen würde, tritt ein einziger Fund über die Vereinigung an die Stelle der Teilfunde.
 *
 * <p>Regeln: Lokaler Teil 1 bis 64 Zeichen; Anfang Buchstabe, Marke oder Ziffer, danach Buchstaben, Marken, Ziffern,
 * {@code . _ % + -}, die RFC-Sonderzeichen {@code ' ’ & ! # $ * / = ? ^ ` { | } ~} und unsichtbare Formatzeichen
 * (weiches Trennzeichen, Zero-Width-Zeichen, Links-/Rechtsmarke, Bidi-Steuerzeichen U+202A bis U+202E, Word
 * Joiner, BOM). Domain: mindestens zwei Labels mit je 1 bis 63 Zeichen aus Buchstaben, Marken, Ziffern,
 * {@code -}, {@code _} und Formatzeichen; ein Label beginnt und endet mit Buchstabe oder Ziffer. Formatzeichen
 * direkt hinter dem {@code @}, hinter einem {@code .} und direkt vor einem {@code .} gehören zum Fund, am Ende der
 * Adresse nicht; ebenso wenig ein Schlusspunkt, -komma, -bindestrich oder -unterstrich.
 *
 * <p>Bekannte Grenzen: Anführungszeichen im lokalen Teil, Domain-Literale ({@code [192.0.2.1]}) und verschleierte
 * Schreibweisen ({@code (at)}) werden nicht erkannt; ist schon das erste Domain-Label länger als 63 Zeichen, gibt
 * es keinen Fund.
 *
 * <p>Laufzeit: das Muster startet höchstens einmal je {@code @}; alle Wiederholungen sind begrenzt oder possessiv,
 * nur die äußere Paar-Wiederholung (höchstens vier) und der Rest eines Labels (höchstens 61 Zeichen) dürfen
 * zurücksetzen. Es gibt keine Rekursion über die Labelzahl. Es laufen zwei Erkennungsläufe und ein linearer Abgleich
 * (plus Sortierung der Kandidaten). Konfidenz 0.95, Quelle {@code email}. Der Erkenner ist zustandslos.
 */
public final class EmailRecognizer implements Recognizer {

    /** Quellname der Funde. */
    public static final String SOURCE = "email";
    /** Konfidenz der Funde (Stufe SICHER). */
    public static final double CONFIDENCE = 0.95;

    /** Höchstlänge des lokalen Teils laut RFC 5321. */
    private static final int MAX_LOCAL_LENGTH = 64;
    /** Höchstlänge eines Domain-Labels laut RFC 1035. */
    private static final int MAX_LABEL_LENGTH = 63;
    /** So viele Paare "lokaler Teil und @" gehören höchstens zu einem Fund. */
    private static final int MAX_LOCAL_PARTS = 4;

    /** Buchstaben, Marken und Ziffern (Inhalt einer Zeichenklasse). */
    private static final String ALNUM = "\\p{L}\\p{M}\\p{N}";
    /**
     * Unsichtbare Formatzeichen (Inhalt einer Zeichenklasse): U+00AD, U+200B bis U+200F, U+202A bis U+202E, U+2060,
     * U+FEFF (keine Zeilenumbrüche).
     */
    private static final String INVISIBLE = "\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF";
    /** RFC-zulässige Sonderzeichen im lokalen Teil (Inhalt einer Zeichenklasse). */
    private static final String LOCAL_SPECIAL = "'\\u2019\\&!#$*/=?^`{|}~";
    /** Zeichen im lokalen Teil nach dem ersten Zeichen. */
    private static final String LOCAL_CHARS = ALNUM + "._%+\\-" + LOCAL_SPECIAL + INVISIBLE;
    /** Dieselben Sonderzeichen als Text für die Rückwärtssuche (ohne Alphanumerische und Formatzeichen). */
    private static final String LOCAL_PUNCTUATION = "._%+-'’&!#$*/=?^`{|}~";

    /** Lokaler Teil samt {@code @}: erstes Zeichen Buchstabe/Ziffer, danach höchstens 63 Zeichen, possessiv. */
    private static final String LOCAL_PART =
            "[" + ALNUM + "][" + LOCAL_CHARS + "]{0," + (MAX_LOCAL_LENGTH - 1) + "}+@";
    /**
     * Ein Domain-Label: davor beliebig viele Formatzeichen, Buchstabe/Ziffer am Anfang und Ende, dazwischen höchstens
     * 61 Zeichen (63 minus Anfangs- und Endzeichen).
     */
    private static final String DOMAIN_LABEL = "[" + INVISIBLE + "]*+[" + ALNUM + "](?:[" + ALNUM + "_\\-"
            + INVISIBLE + "]{0," + (MAX_LABEL_LENGTH - 2) + "}[" + ALNUM + "])?";

    /**
     * Wird nur am Anfang eines Laufs mit {@code lookingAt()} angewendet (kein Lookbehind, keine Suche): ein bis vier
     * Paare (nicht possessiv, damit ein {@code @} hinter der Domain das letzte Paar wieder freigibt), dann das erste
     * Label und mindestens ein weiteres Label (possessiv, kein Stack-Verbrauch über die Labelzahl).
     */
    private static final Pattern EMAIL = Pattern.compile(
            "(?:" + LOCAL_PART + "){1," + MAX_LOCAL_PARTS + "}"
                    + DOMAIN_LABEL
                    + "(?:[" + INVISIBLE + "]*+\\." + DOMAIN_LABEL + ")++");

    /** Domain allein (für den Rest hinter einem Fund, wenn mehr als vier Paare aneinanderhängen). */
    private static final Pattern DOMAIN_ONLY = Pattern.compile(
            DOMAIN_LABEL + "(?:[" + INVISIBLE + "]*+\\." + DOMAIN_LABEL + ")++");

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        return reconcile(text, scan(text, false), scan(text, true));
    }

    /** Ergebnis ohne Aufteilen am letzten Punkt und ohne Rest-Funde (Vergleichsbasis, siehe Klassen-Javadoc). */
    List<Finding> detectUnshortened(String text) {
        Objects.requireNonNull(text, "text");
        return scan(text, false);
    }

    /**
     * Ein Erkennungslauf. Mit {@code split} wird am letzten Punkt aufgeteilt, wenn danach eine weitere Adresse
     * beginnt, und ein Rest hinter einem Fund wird als eigener Fund gemeldet; ohne {@code split} entstehen nur die
     * Funde des Musters. Das Ergebnis ist sortiert und überlappungsfrei.
     */
    private static List<Finding> scan(String text, boolean split) {
        List<Finding> result = new ArrayList<>();
        Matcher m = EMAIL.matcher(text);
        int from = 0;
        int lastEnd = -1; // Ende des letzten Fundes
        int at;
        while ((at = text.indexOf('@', from)) >= 0) {
            int start = localStart(text, from, at);
            if (start < at) {
                m.region(start, text.length());
                if (m.lookingAt()) {
                    int s = m.start();
                    addTail(result, text, lastEnd, s, split);
                    lastEnd = emitWithSplits(text, m, result, split);
                    from = lastEnd;
                    continue;
                }
            }
            from = at + 1;
        }
        addTail(result, text, lastEnd, text.length(), split);
        return result;
    }

    /**
     * Meldet den Fund, den {@code m} (nach {@code lookingAt()}) gerade gefunden hat, und liefert sein Ende. Folgt
     * direkt ein {@code @}, kann das letzte Domain-Label der lokale Teil einer weiteren Adresse sein: Dann endet der
     * Fund vor dem letzten Punkt und die nächste Adresse beginnt dahinter (wiederholt). Gelingt eines von beiden
     * nicht, bleibt der Fund unverändert. Formatzeichen am Trenner gehören zu den Teilfunden; nur der Punkt bleibt
     * frei. Ob dabei etwas verloren geht, entscheidet nicht diese Methode, sondern {@link #reconcile}.
     */
    private static int emitWithSplits(String text, Matcher m, List<Finding> result, boolean split) {
        int s = m.start(); // Anfang für die Mustersuche
        int out = s; // gemeldeter Anfang des aktuellen Fundes
        int e = m.end();
        while (split && e < text.length() && text.charAt(e) == '@') {
            int dot = text.lastIndexOf('.', e - 1);
            if (dot <= s) {
                break;
            }
            int s2 = localStart(text, dot + 1, e);
            if (s2 >= e || !onlyInvisible(text, dot + 1, s2)) {
                break;
            }
            m.region(s, dot);
            if (!m.lookingAt() || !onlyInvisible(text, m.end(), dot)) {
                break;
            }
            m.region(s2, text.length());
            if (!m.lookingAt()) {
                break;
            }
            result.add(finding(text, out, dot));
            s = m.start();
            out = dot + 1;
            e = m.end();
        }
        result.add(finding(text, out, e));
        return e;
    }

    private static Finding finding(String text, int start, int end) {
        return new Finding(FindingType.EMAIL, text.substring(start, end), start, end, CONFIDENCE, SOURCE);
    }

    /**
     * Gleicht das aufgeteilte Ergebnis {@code shortened} mit dem ungekürzten {@code unshortened} ab und garantiert so,
     * dass das Ergebnis jedes Zeichen des ungekürzten Ergebnisses abdeckt, ausgenommen einzelne Trenner-Punkte
     * zwischen zwei Funden. Für jeden Fund u des ungekürzten Ergebnisses gilt: Ist jedes Zeichen von u im
     * aufgeteilten Ergebnis abgedeckt oder ein einzelner Punkt zwischen zwei Funden, bleiben die Teilfunde
     * unverändert; sonst werden alle Teilfunde, die u überlappen, durch einen Fund über die Vereinigung ersetzt.
     * Funde des aufgeteilten Ergebnisses außerhalb aller u bleiben erhalten. Zum Schluss werden sich überlappende
     * Kandidaten verschmolzen (das vergrößert die Abdeckung nur). Beide Eingaben sind sortiert und überlappungsfrei.
     */
    private static List<Finding> reconcile(String text, List<Finding> unshortened, List<Finding> shortened) {
        int n = shortened.size();
        boolean[] replaced = new boolean[n];
        List<int[]> candidates = new ArrayList<>();
        int k = 0; // erster aufgeteilter Fund, der nicht vor dem aktuellen u endet (wächst monoton)
        for (Finding u : unshortened) {
            while (k < n && shortened.get(k).end() <= u.start()) {
                k++;
            }
            if (coveredExceptSeparators(text, shortened, k, u)) {
                continue;
            }
            int start = u.start();
            int end = u.end();
            for (int i = k; i < n && shortened.get(i).start() < u.end(); i++) {
                replaced[i] = true;
                start = Math.min(start, shortened.get(i).start());
                end = Math.max(end, shortened.get(i).end());
            }
            candidates.add(new int[] {start, end});
        }
        for (int i = 0; i < n; i++) {
            if (!replaced[i]) {
                candidates.add(new int[] {shortened.get(i).start(), shortened.get(i).end()});
            }
        }
        return mergeOverlapping(text, candidates);
    }

    /** Sortiert die Kandidaten (Anfang, Ende), verschmilzt überlappende zu einem Fund; Berührende bleiben getrennt. */
    private static List<Finding> mergeOverlapping(String text, List<int[]> candidates) {
        candidates.sort((x, y) -> Integer.compare(x[0], y[0]));
        List<Finding> result = new ArrayList<>();
        int curStart = -1;
        int curEnd = -1;
        for (int[] c : candidates) {
            if (curEnd >= 0 && c[0] < curEnd) {
                curEnd = Math.max(curEnd, c[1]);
                continue;
            }
            if (curEnd >= 0) {
                result.add(finding(text, curStart, curEnd));
            }
            curStart = c[0];
            curEnd = c[1];
        }
        if (curEnd >= 0) {
            result.add(finding(text, curStart, curEnd));
        }
        return result;
    }

    /**
     * Wahr, wenn jedes Zeichen von {@code u} in {@code shortened} (ab Index {@code k}) abgedeckt ist oder ein einzelner
     * Punkt, der direkt zwischen zwei dieser Funde liegt (Aufteilstelle).
     */
    private static boolean coveredExceptSeparators(String text, List<Finding> shortened, int k, Finding u) {
        int n = shortened.size();
        int p = u.start();
        int i = k;
        while (p < u.end()) {
            if (i < n && shortened.get(i).start() <= p) {
                p = Math.max(p, shortened.get(i).end());
                i++;
                continue;
            }
            boolean separator = text.charAt(p) == '.' && i > 0 && i < n && shortened.get(i - 1).end() == p
                    && shortened.get(i).start() == p + 1;
            if (!separator) {
                return false;
            }
            p++;
        }
        return true;
    }

    /**
     * Hängen mehr als vier Paare aneinander, bliebe hinter einem Fund (Ende {@code e}) ein "{@code @}" samt Domain als
     * Klartext. Dieser Rest wird als eigener Fund gemeldet (beginnend mit dem "{@code @}"), soweit er vor {@code limit}
     * endet, also keinem folgenden Fund im Weg steht.
     */
    private static void addTail(List<Finding> result, String text, int e, int limit, boolean enabled) {
        if (!enabled || e < 0 || e >= limit || text.charAt(e) != '@') {
            return;
        }
        Matcher d = DOMAIN_ONLY.matcher(text);
        while (e < limit && text.charAt(e) == '@') {
            d.region(e + 1, limit);
            if (!d.lookingAt()) {
                return;
            }
            result.add(finding(text, e, d.end()));
            e = d.end();
        }
    }

    /**
     * Anfang des lokalen Teils für das {@code @} an Position {@code at}: höchstens 64 Zeichen nach links über
     * Zeichen des lokalen Teils, nicht vor {@code from}, dann vorwärts zum ersten Buchstaben oder Ziffer. Es wird
     * nach Codepoints gesprungen (kein Start im Surrogatpaar) und eine Marke, die auf ein Zeichen der Klasse folgt,
     * wird übersprungen (kein Start zwischen Grundzeichen und Marke). Liefert {@code at}, wenn es keinen Anfang gibt.
     */
    private static int localStart(String text, int from, int at) {
        int s = at;
        while (s > from) {
            int cp = text.codePointBefore(s);
            int n = Character.charCount(cp);
            if (at - s + n > MAX_LOCAL_LENGTH || !isLocalChar(cp)) {
                break;
            }
            s -= n;
        }
        while (s < at) {
            int cp = text.codePointAt(s);
            boolean splitsMark = isMark(cp) && s > 0 && isAlnum(text.codePointBefore(s));
            if (isAlnum(cp) && !splitsMark) {
                break;
            }
            s += Character.charCount(cp);
        }
        return s;
    }

    /** Wahr, wenn {@code text[from, to)} leer ist oder nur unsichtbare Formatzeichen enthält. */
    private static boolean onlyInvisible(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!isInvisible(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalChar(int cp) {
        return isAlnum(cp) || isInvisible(cp) || LOCAL_PUNCTUATION.indexOf(cp) >= 0;
    }

    private static boolean isInvisible(int cp) {
        return cp == 0x00AD || (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x202A && cp <= 0x202E) || cp == 0x2060
                || cp == 0xFEFF;
    }

    private static boolean isMark(int cp) {
        int t = Character.getType(cp);
        return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK
                || t == Character.COMBINING_SPACING_MARK;
    }

    /** Entspricht {@code [\p{L}\p{M}\p{N}]}. */
    private static boolean isAlnum(int cp) {
        if (isMark(cp)) {
            return true;
        }
        switch (Character.getType(cp)) {
            case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                    Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
                    Character.LETTER_NUMBER, Character.OTHER_NUMBER:
                return true;
            default:
                return false;
        }
    }
}
