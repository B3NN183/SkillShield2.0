package de.skillshield.core.recognizer.general;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt Dateipfade rein per Zeichenprüfung (handgeschriebener, linearer Durchlauf, kein Regex, kein
 * Dateisystemzugriff, keine Prüfung auf Existenz; Architektur-Plan Kap. 2.4). Ein erkannter Pfad wird als genau ein
 * Fund vom Typ {@link FindingType#PFAD} geliefert; Programm-, Benutzer-, Server- und Ordnernamen darin sind damit
 * mitgeschützt.
 *
 * <p>Stand dieses Tasks (T-11): Form {@link PathForm#LAUFWERK} mit Anführungszeichenregel; die weiteren Formen
 * folgen mit den Folgetasks, ohne dass sich Läufe, Ende und Segmentzählung ändern.
 *
 * <p><b>Anführungszeichen (T-11).</b> Zusätzlich zum Durchlauf nach den folgenden Absätzen (Stand T-10, siehe
 * {@link #detectWithoutQuotes}) wird jedes Anführungszeichen ({@code "}, {@code '}, Backtick sowie die typografischen
 * Zeichen U+201E, U+201C, U+201D, U+00BB, U+00AB, U+2018, U+2019, U+201A, U+2039, U+203A) immer als öffnendes
 * probiert und mit dem nächsten schließenden Zeichen seiner Familie gepaart ({@link PathChars#closesQuote}). Die
 * Suche endet erfolglos an Steuerzeichen (Zeilenende, Tab), {@code <}, {@code >}, {@code |} und, außer bei der
 * doppelten Familie, am geraden {@code "}; Leerzeichen beenden sie nicht. Enthält der Inhalt eines Paars einen
 * Pfadkopf mit genügend Segmenten, ist der Fund der Inhalt ab dem ersten solchen Kopf bis zum schließenden Zeichen,
 * ohne Kürzung und auch mit Leerzeichen; die Anführungszeichen selbst gehören nicht zum Fund. Es wird kein Paar
 * verbraucht: Jedes Zeichen wird als öffnendes probiert, auch das schließende eines vorigen Paars. Ein unpaariges
 * Zeichen davor ({@code Monitor 24" an C:\Temp, Datei "D:\Mein Ordner\a.txt"}, {@code Peter's C:\Temp und
 * 'D:\Mein Ordner\a.txt'}) kann daher einen zusätzlichen, überflüssigen Fund über Text zwischen zwei Zeichen bilden
 * (zu groß, kein Leck), nimmt dem Pfad im richtigen Zitat aber nie sein öffnendes Zeichen. Alle Funde der Paare und
 * die des Durchlaufs ohne Anführungszeichen werden vereinigt (überlappende Funde werden zu einem): Der Fund ist nie
 * kleiner als ohne die Anführungszeichenregel (Obermengen-Garantie gegenüber T-10). Fehlt das schließende Zeichen
 * oder gibt es keinen Kopf im Inhalt, trägt das Paar nichts bei. Ein Apostroph kann so ein Paar bilden und einen
 * größeren Fund erzeugen als nötig (kein Leck). Die Funde grenzender Paare bleiben getrennt.
 *
 * <p><b>Läufe.</b> Der Text zerfällt in Läufe: maximale Folgen von Zeichen, die kein Endzeichen sind (Leerraum,
 * Steuerzeichen, {@code "}, Backtick, {@code <}, {@code >}, {@code |}; siehe {@link PathChars#isTerminator}). Ein Pfad
 * ohne Anführungszeichen liegt immer ganz in einem Lauf und endet damit am ersten Leerzeichen (Nutzerentscheidung
 * D-5): {@code C:\Mein Ordner\x} ergibt {@code C:\Mein}. Unsichtbare Formatzeichen (U+200B, U+FEFF, Bidi-Marken
 * u. a.) beenden den Pfad nicht.
 *
 * <p><b>Beginn.</b> An jeder Position eines Laufs wird der Kopf der Pfadform geprüft. Für Laufwerkspfade gilt keine
 * Startgrenze: {@code Pfad:C:\x}, {@code abcC:\x} und {@code .....C:\x} ergeben den Fund ab dem Laufwerksbuchstaben.
 * Der Fund reicht bis zum Laufende und wird dann am Ende gekürzt. Ein Lauf liefert höchstens einen Fund, auch wenn
 * er mehrere Pfade enthält ({@code C:\a;C:\b}: ein Fund).
 *
 * <p><b>Kürzung am Ende.</b> Von hinten werden wiederholt entfernt: {@code . , ; : ! ?} und {@code '}; ferner
 * schließende runde, eckige und geschweifte Klammern nur, wenn der Fund (ab seinem Beginn gezählt) mehr schließende
 * als öffnende Klammern dieser Art enthält ({@code C:\Tools\Beispiel(x86)} bleibt ganz, {@code C:\Tools\a.exe)}
 * verliert die Klammer). {@code *} und ein abschließender Trenner bleiben im Fund.
 *
 * <p><b>Segmente.</b> Hinter dem Kopf muss nach der Kürzung mindestens ein Segment (nichtleere Folge von
 * Nicht-Trennern) stehen: {@code C:}, {@code C:\} und {@code C:\.} sind kein Fund. Die Konfidenz hängt nur von Form
 * und der auf 3 gedeckelten Segmentzahl ab, nie von Leerzeichen.
 *
 * <p><b>Laufzeit.</b> Jeder Lauf wird einmal ausgewertet (Ende, Segmentanfänge von hinten), jede Position wird in
 * O(1) als Beginn geprüft, die Kürzung läuft einmal je Fund über dessen Lauf; danach springt der Durchlauf ans
 * Laufende. Die Suche nach dem schließenden Anführungszeichen endet spätestens am nächsten Zeichen derselben Art
 * (jedes Zeichen schließt seine eigene Art, auch wenn es zusätzlich Zeichen anderer Arten derselben Familie
 * schließt). Je Art sind die Bereiche der Paare daher paarweise disjunkt, und die Inhaltsprüfung (Segmentanfänge,
 * Kopfsuche, je Position O(1)) liest jede Position je Art höchstens einmal: insgesamt O(n) (höchstens 13 Arten mal
 * n), keine Rekursion. Die Funde der Paare entstehen nicht unbedingt in Startreihenfolge (Paare verschiedener Arten
 * verschachteln sich, und der erste gültige Kopf hängt vom Ende des Paars ab) und werden deshalb einmal nach Start
 * sortiert (O(k log k), bei bereits sortierter Eingabe linear). Jede Position liegt in höchstens 13 Paar-Funden
 * (einer je Art), die Teilstrings der Paar-Funde summieren sich daher auf höchstens 13 mal n Zeichen. Die Vereinigung
 * ist ein einziger Durchlauf über beide nach Start sortierten Listen, führt nur Zahlen mit und baut je Ergebnis einen
 * Fund.
 *
 * <p>Unicode: Alle Kopf-, Trenner-, Kürzungs- und Endzeichen sind BMP-Zeichen außerhalb der Surrogatbereiche; ein
 * Fund beginnt daher nie in einem Surrogatpaar und endet nie zwischen Grundzeichen und kombinierendem Zeichen.
 *
 * <p>Der Erkenner ist zustandslos und schreibt keinen Text in Meldungen.
 */
public final class PathRecognizer implements Recognizer {

    private static final PathForm[] FORMS = PathForm.values();
    private static final int CAP = 3;

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> base = detectWithoutQuotes(text);
        List<Finding> quoted = detectQuoted(text);
        if (quoted.isEmpty()) {
            return base;
        }
        return union(text, base, quoted);
    }

    /**
     * Anführungszeichenregel (Plan 2.4.3): Jedes Anführungszeichen wird als öffnendes probiert und mit dem nächsten
     * schließenden Zeichen seiner Familie gepaart. Hat der Inhalt einen gültigen Pfadkopf, ist der Fund der Inhalt ab
     * dem ersten solchen Kopf bis zum schließenden Zeichen (ohne Kürzung, auch mit Leerzeichen). Kein Paar wird
     * verbraucht, auch das schließende Zeichen wird als öffnendes probiert; so kann ein unpaariges Zeichen davor das
     * öffnende Zeichen eines späteren Zitats nicht wegnehmen. Das Ergebnis ist nach Start sortiert, kann sich aber
     * überlappen (Paare verschiedener Arten); {@link #union} führt überlappende Funde zusammen.
     */
    private static List<Finding> detectQuoted(String text) {
        List<Finding> result = new ArrayList<>();
        int n = text.length();
        int[] starts = new int[CAP];
        for (int pos = 0; pos < n; pos++) {
            int kind = PathChars.quoteKind(text.charAt(pos));
            if (kind < 0) {
                continue;
            }
            int close = findClosingQuote(text, pos, kind);
            Finding f = close < 0 ? null : quotedFinding(text, pos + 1, close, starts);
            if (f != null) {
                result.add(f);
            }
        }
        // Der erste gültige Kopf hängt vom Ende des Paars ab; Paare verschiedener Arten können einander verschachteln.
        result.sort(Comparator.comparingInt(Finding::start));
        return result;
    }

    /**
     * Position des schließenden Anführungszeichens zum öffnenden an {@code open} oder -1, wenn die Suche am Zeilenende
     * und anderen Abbruchzeichen endet. Die Suche endet spätestens am nächsten Zeichen derselben Art; die Bereiche
     * einer Art überlappen deshalb nicht (Gesamtaufwand höchstens {@link PathChars#QUOTE_KINDS} mal Textlänge).
     */
    private static int findClosingQuote(String text, int open, int kind) {
        int n = text.length();
        for (int j = open + 1; j < n; j++) {
            char c = text.charAt(j);
            if (PathChars.closesQuote(kind, c)) {
                return j;
            }
            if (PathChars.abortsQuoteSearch(kind, c)) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Fund im Inhalt {@code [from, to)} eines Anführungszeichenpaars: ab dem ersten Kopf, hinter dem genügend Segmente
     * stehen, bis {@code to}; sonst {@code null}. Direkt hinter dem öffnenden Zeichen ist die Startgrenze erfüllt.
     */
    private static Finding quotedFinding(String text, int from, int to, int[] starts) {
        if (from >= to) {
            return null;
        }
        int count = contentSegmentStarts(text, from, to, starts);
        for (int i = from; i < to; i++) {
            for (PathForm form : FORMS) {
                if (i != from && form.requiresStartBoundary() && !PathChars.isStartBoundary(text, i)) {
                    continue;
                }
                int headEnd = form.headEnd(text, i);
                if (headEnd < 0 || headEnd > to) {
                    continue;
                }
                int segments = countFrom(starts, count, headEnd);
                if (segments >= form.minSegments()) {
                    return new Finding(FindingType.PFAD, text.substring(i, to), i, to, form.confidence(segments),
                            form.source());
                }
            }
        }
        return null;
    }

    /** Anzahl der Segmentanfänge {@code starts[0..count)}, die bei {@code from} oder dahinter liegen (höchstens 3). */
    private static int countFrom(int[] starts, int count, int from) {
        int segments = 0;
        for (int k = 0; k < count; k++) {
            if (starts[k] >= from) {
                segments++;
            }
        }
        return segments;
    }

    /**
     * Anfänge der letzten (höchstens drei) Segmente in {@code [from, to)}, absteigend; ein Segment ist eine maximale
     * Folge von Nicht-Trennern mit mindestens einem Zeichen, das kein Leerraum ist.
     */
    private static int contentSegmentStarts(String text, int from, int to, int[] starts) {
        int count = 0;
        boolean content = false;
        for (int j = to - 1; j >= from && count < CAP; j--) {
            char c = text.charAt(j);
            if (PathChars.isSeparator(c)) {
                if (content) {
                    starts[count++] = j + 1;
                }
                content = false;
            } else if (!PathChars.isBlank(c)) {
                content = true;
            }
        }
        if (content && count < CAP) {
            starts[count++] = from;
        }
        return count;
    }

    /**
     * Vereinigung beider Fundlisten (je nach Start sortiert, innerhalb einer Liste dürfen sich Funde überlappen):
     * überlappende Funde werden zu einem Fund über den gesamten Bereich, Konfidenz = kleinere, Quelle des früher
     * beginnenden Fundes (bei gleichem Start der aus {@code a}). Jede Position, die in einer der Listen abgedeckt
     * ist, bleibt abgedeckt (Obermenge); aneinandergrenzende Funde bleiben getrennt. Der Durchlauf nimmt immer den
     * Fund mit dem kleineren Start aus beiden Listen und kennt daher nur einen offenen Bereich. Während der
     * Verlängerung werden nur Start, Ende, Konfidenz und Quelle geführt; Teilstring und Fund entstehen einmal je
     * Ergebnis beim Abschluss (Gesamtaufwand linear).
     */
    private static List<Finding> union(String text, List<Finding> a, List<Finding> b) {
        List<Finding> result = new ArrayList<>(a.size() + b.size());
        int i = 0;
        int j = 0;
        Finding first = null; // erster Fund des aktuellen Bereichs (Quelle)
        int start = 0;
        int end = 0;
        double confidence = 0;
        while (i < a.size() || j < b.size()) {
            Finding next;
            if (j >= b.size() || (i < a.size() && a.get(i).start() <= b.get(j).start())) {
                next = a.get(i++);
            } else {
                next = b.get(j++);
            }
            if (first == null) {
                first = next;
                start = next.start();
                end = next.end();
                confidence = next.confidence();
            } else if (next.start() >= end) {
                result.add(merged(text, first, start, end, confidence));
                first = next;
                start = next.start();
                end = next.end();
                confidence = next.confidence();
            } else if (next.end() > end) {
                end = next.end();
                confidence = Math.min(confidence, next.confidence());
            }
        }
        if (first != null) {
            result.add(merged(text, first, start, end, confidence));
        }
        return result;
    }

    /** Fund über {@code [start, end)}; ist es der unveränderte erste Fund, wird dieser selbst geliefert. */
    private static Finding merged(String text, Finding first, int start, int end, double confidence) {
        if (first.end() == end && first.confidence() == confidence) {
            return first;
        }
        return new Finding(FindingType.PFAD, text.substring(start, end), start, end, confidence, first.source());
    }

    /**
     * Durchlauf ohne Anführungszeichenregel (Stand T-10): Läufe, Beginn, Kürzung. Paketprivat, damit Tests die
     * Obermengen-Garantie der Anführungszeichenregel gegen diesen Durchlauf prüfen können; {@link #detect} deckt
     * jede hier abgedeckte Position ebenfalls ab.
     */
    List<Finding> detectWithoutQuotes(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> result = new ArrayList<>();
        int n = text.length();
        Run run = new Run();
        int pos = 0;
        while (pos < n) {
            if (PathChars.isTerminator(text.charAt(pos))) {
                pos++;
                continue;
            }
            if (pos >= run.end) {
                run.load(text, pos);
            }
            Finding f = tryForms(text, pos, run);
            if (f != null) {
                result.add(f);
                pos = run.end; // Rest des Laufs gehört zum Fund oder ist abgeschnittenes Satzzeichen
            } else {
                pos++;
            }
        }
        return result;
    }

    /** Liefert den Fund einer Pfadform, die an {@code pos} beginnt, oder {@code null}. */
    private static Finding tryForms(String text, int pos, Run run) {
        for (PathForm form : FORMS) {
            if (form.requiresStartBoundary() && !PathChars.isStartBoundary(text, pos)) {
                continue;
            }
            int headEnd = form.headEnd(text, pos);
            if (headEnd < 0) {
                continue;
            }
            int segments = run.segmentsFrom(headEnd);
            if (segments < form.minSegments()) {
                continue;
            }
            int end = trimEnd(text, pos, run.end, headEnd);
            return new Finding(FindingType.PFAD, text.substring(pos, end), pos, end,
                    form.confidence(segments), form.source());
        }
        return null;
    }

    /**
     * Kürzt von hinten: Satzzeichen immer, schließende Klammern nur, wenn sie im Fund ab {@code start} überzählig
     * sind. Hält am Kopfende an. Das Ergebnis liegt nie vor dem pessimistischen Ende des Laufs (dort werden alle
     * schließenden Klammern unabhängig von der Zählung entfernt), die geprüfte Segmentzahl bleibt also gültig.
     */
    private static int trimEnd(String text, int start, int runEnd, int headEnd) {
        int[] open = new int[3];
        int[] close = new int[3];
        for (int k = start; k < runEnd; k++) {
            char c = text.charAt(k);
            int o = PathChars.openingKind(c);
            if (o >= 0) {
                open[o]++;
            }
            int cl = PathChars.closingKind(c);
            if (cl >= 0) {
                close[cl]++;
            }
        }
        int end = runEnd;
        while (end > headEnd) {
            char c = text.charAt(end - 1);
            if (PathChars.isTrailingPunctuation(c)) {
                end--;
                continue;
            }
            int cl = PathChars.closingKind(c);
            if (cl >= 0 && close[cl] > open[cl]) {
                close[cl]--;
                end--;
                continue;
            }
            break;
        }
        return end;
    }

    /**
     * Daten eines Laufs, einmal je Lauf in O(Lauflänge) berechnet: {@code end} (erstes Endzeichen oder Textende)
     * und die Anfänge der letzten drei Segmente vor dem pessimistischen Ende (Ende nach Entfernen aller
     * abschließenden Kürzungszeichen und aller schließenden Klammern, unabhängig vom Beginn des Fundes).
     */
    private static final class Run {
        int end;
        private final int[] starts = new int[CAP]; // absteigend: letzter Segmentanfang zuerst
        private int count;

        void load(String text, int start) {
            int n = text.length();
            int e = start;
            while (e < n && !PathChars.isTerminator(text.charAt(e))) {
                e++;
            }
            end = e;
            int t = e;
            while (t > start && (PathChars.isTrailingPunctuation(text.charAt(t - 1))
                    || PathChars.isClosingBracket(text.charAt(t - 1)))) {
                t--;
            }
            count = 0;
            for (int j = t - 1; j >= start && count < CAP; j--) {
                if (!PathChars.isSeparator(text.charAt(j))
                        && (j == start || PathChars.isSeparator(text.charAt(j - 1)))) {
                    starts[count++] = j;
                }
            }
        }

        /** Anzahl der Segmente ab {@code from} vor dem pessimistischen Ende, gedeckelt bei 3. */
        int segmentsFrom(int from) {
            return countFrom(starts, count, from);
        }
    }
}
