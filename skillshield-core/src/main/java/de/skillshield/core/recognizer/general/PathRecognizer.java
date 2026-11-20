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
 * <p>Stand dieses Tasks (T-14): neun Formen: {@link PathForm#LAUFWERK}, {@link PathForm#UNC},
 * {@link PathForm#UNIX}, {@link PathForm#HOME} (auch {@code ~name/}), {@link PathForm#HOST_PFAD}
 * ({@code host.example:/export/home}), {@link PathForm#RELATIV} ({@code ./a}, {@code ../a}),
 * {@link PathForm#EINZEL_BACKSLASH} (ein führender Backslash, mindestens zwei Segmente),
 * {@link PathForm#BENUTZER_WURZEL} ({@code Users\erika\x}, {@code Benutzer\erika}) und
 * {@link PathForm#WURZEL_ZWEI_SEGMENTE} ({@code home/erika/x}, {@code Benutzer/erika/x}) mit Anführungszeichenregel;
 * die weiteren Formen folgen mit den Folgetasks, ohne dass sich Läufe, Ende und Segmentzählung ändern. Für
 * Laufwerks-, UNC- und Host-Pfade gilt keine Startgrenze (Text direkt davor verhindert den Fund nicht). Alle anderen
 * Formen beginnen nur an einer Startgrenze ({@link PathForm#isStartBoundary(String, int)}, in der Regel
 * {@link PathChars#isStartBoundary}; bei RELATIV und den beiden Wurzel-Formen zusätzlich nach {@code :}), weil
 * {@code /}, {@code .} und der Backslash in gewöhnlichem Text häufig sind ({@code und/oder}, {@code km/h},
 * {@code 19/11/2026}, {@code Ende../x}, {@code 1./2.}).
 *
 * <p><b>Obermengen-Garantie je Form.</b> Der Durchlauf ohne Anführungszeichen liefert die Vereinigung aus dem
 * Durchlauf mit allen Formen und je einem Durchlauf mit nur einer Form
 * ({@link #detectWithoutQuotes(String, PathForm[])}); eine später hinzukommende Form macht den Fund einer bisherigen
 * Form daher nie kleiner (ein Lauf liefert sonst höchstens einen Fund, und der Fund einer früher beginnenden Form
 * kann am Ende kürzer sein).
 *
 * <p><b>Anführungszeichen (T-11).</b> Zusätzlich zum Durchlauf nach den folgenden Absätzen wird jedes
 * Anführungszeichen (die 13 Arten aus {@link PathChars#quoteKind}) immer als öffnendes probiert und mit dem nächsten
 * schließenden Zeichen seiner Familie gepaart ({@link PathChars#closesQuote}). Die Suche endet erfolglos an
 * Steuerzeichen (Zeilenende, Tab), {@code <}, {@code >}, {@code |} und, außer bei der doppelten Familie, am geraden
 * {@code "}; Leerzeichen beenden sie nicht. Enthält der Inhalt eines Paars einen Pfadkopf mit genügend Segmenten, ist
 * der Fund der Inhalt ab dem ersten solchen Kopf bis zum schließenden Zeichen, ohne Kürzung und auch mit
 * Leerzeichen; die Anführungszeichen selbst gehören nicht zum Fund. Es wird kein Paar verbraucht: Jedes Zeichen wird
 * als öffnendes probiert, auch das schließende eines vorigen Paars. Ein unpaariges Zeichen davor kann daher einen
 * zusätzlichen, zu großen Fund bilden (kein Leck), nimmt dem Pfad im richtigen Zitat aber nie sein öffnendes
 * Zeichen. Alle Funde der Paare und die des Durchlaufs ohne Anführungszeichen werden vereinigt (überlappende Funde
 * werden zu einem): Der Fund ist nie kleiner als ohne die Anführungszeichenregel. Fehlt das schließende Zeichen
 * oder gibt es keinen Kopf im Inhalt, trägt das Paar nichts bei. Die Funde grenzender Paare bleiben getrennt.
 *
 * <p><b>Läufe.</b> Der Text zerfällt in Läufe: maximale Folgen von Zeichen, die kein Endzeichen sind (Leerraum,
 * Steuerzeichen, {@code "}, Backtick, {@code <}, {@code >}, {@code |}; siehe {@link PathChars#isTerminator}). Ein Pfad
 * ohne Anführungszeichen liegt immer ganz in einem Lauf und endet damit am ersten Leerzeichen (Nutzerentscheidung
 * D-5): {@code C:\Mein Ordner\x} ergibt {@code C:\Mein}. Unsichtbare Formatzeichen (U+200B, U+FEFF, Bidi-Marken
 * u. a.) beenden den Pfad nicht.
 *
 * <p><b>Beginn.</b> An jeder Position eines Laufs wird der Kopf der Pfadform geprüft. Der Fund reicht bis zum
 * Laufende und wird dann am Ende gekürzt. Ein Lauf liefert höchstens einen Fund, auch wenn er mehrere Pfade enthält
 * ({@code C:\a;C:\b}: ein Fund). Bei Formen ohne Startgrenze beginnt der Fund am Kopf selbst: {@code Pfad:C:\x},
 * {@code abcC:\x} und {@code .....C:\x} ergeben den Fund ab dem Laufwerksbuchstaben, {@code Pfad:/etc/hosts} ab dem
 * Host-Lauf {@code Pfad} ({@link PathForm#HOST_PFAD}).
 *
 * <p><b>Kürzung am Ende.</b> Von hinten werden wiederholt entfernt: {@code . , ; : ! ?} und {@code '}; ferner
 * schließende runde, eckige und geschweifte Klammern nur, wenn der Fund (ab seinem Beginn gezählt) mehr schließende
 * als öffnende Klammern dieser Art enthält ({@code C:\Tools\Beispiel(x86)} bleibt ganz, {@code C:\Tools\a.exe)}
 * verliert die Klammer). {@code *} und ein abschließender Trenner bleiben im Fund.
 *
 * <p><b>Segmente und Konfidenz.</b> Hinter dem Kopf müssen nach der Kürzung mindestens so viele Segmente (nichtleere
 * Folgen von Nicht-Trennern) stehen, wie die Form verlangt (Laufwerk, UNC, Home, Host, relativ und Benutzer-Wurzel
 * {@code Users\}, {@code Users/}, {@code Benutzer\}: eines; Unix, Einzel-Backslash, {@code home/} und
 * {@code Benutzer/}: zwei). Die Prüfung geschieht mit der Zahl NACH der Kürzung: Die vorab berechnete
 * (pessimistische) Zahl ist höchstens um eines kleiner, denn hinter dem pessimistischen Ende stehen nur Satzzeichen und
 * schließende Klammern, und davon bildet höchstens ein Block direkt hinter einem Trenner ein weiteres Segment
 * ({@code /opt(/)} hat zwei Segmente, {@code opt(} und {@code )}). Fehlt der pessimistischen Zahl genau eines und
 * kann eines hinzukommen (O(1)-Vorprüfung), wird die Kürzung gerechnet und danach entschieden; so geht kein Fund
 * verloren. {@code C:}, {@code C:\} und {@code C:\.} sind kein Fund. Die Konfidenz bestimmt die Form
 * aus der Kopflänge und der auf 3 gedeckelten Segmentzahl ({@link PathForm#confidence(int, int)}), nie aus
 * Leerzeichen; die Werte je Form stehen im Javadoc der Form.
 *
 * <p><b>Laufzeit.</b> Jeder Lauf wird einmal ausgewertet (Ende, Segmentanfänge von hinten), jede Position wird in
 * (amortisiert) O(1) als Beginn geprüft, die Kürzung läuft einmal je Fund über dessen Lauf; danach springt der
 * Durchlauf ans Laufende. Der Kopf einer Form liest nur Zeichen unmittelbar ab der Position, nie Servernamen,
 * Freigabe oder Segmente (deren Anzahl liefern die Laufdaten in O(1)). Die Belege je Form (UNC, HOME, HOST_PFAD)
 * stehen im Javadoc der Form: UNIX, LAUFWERK, RELATIV, EINZEL_BACKSLASH, BENUTZER_WURZEL (höchstens 9 Zeichen) und
 * WURZEL_ZWEI_SEGMENTE (höchstens 15 Zeichen) prüfen nur wenige Zeichen, UNC liest den Backslash-Lauf nur an dessen
 * erstem Backslash, HOME liest höchstens 66 Zeichen je Tilde in nicht überlappenden Abschnitten, HOST_PFAD liest
 * höchstens 64 Zeichen je {@code :/}-Stelle (O(64 n)). Die Zählung nach der Kürzung (siehe Segmente) kostet
 * O(Lauflänge) und läuft nur für Köpfe, deren pessimistische Zahl genau eines unter der Mindestzahl liegt und hinter
 * denen ein Klammer-Segment möglich ist. Das sind je Lauf höchstens eine Handvoll Köpfe:
 * Köpfe der Formen LAUFWERK, UNIX, HOME, HOST_PFAD, RELATIV, BENUTZER_WURZEL und WURZEL_ZWEI_SEGMENTE enden mit einem
 * Trenner. Zwischen dem Kopfende und dem Beginn des nächsten Kopfes derselben Form liegt ein Segmentanfang: Der
 * nächste Kopf beginnt mit einem Nicht-Trenner, bei UNIX steht wegen der Startgrenze vor seinem Trenner ein
 * Nicht-Trenner. Bei EINZEL_BACKSLASH ist das Kopfende selbst ein Segmentanfang (das Zeichen hinter dem Backslash
 * ist kein Trenner). Ein solcher Segmentanfang kann im abschließenden Satzzeichen-Bereich hinter {@code tail} liegen
 * (bei EINZEL_BACKSLASH das Kopfende des letzten Kopfes, etwa bei {@code \a\.}) und ist dann in den Laufdaten nicht
 * enthalten; ein Kopf selbst liegt aber immer vor {@code tail}. Deshalb haben nur die letzten zwei Köpfe je Lauf
 * (Mindestzahl höchstens zwei) einen Segmentanfang weniger in den Laufdaten und werden nachgezählt; jeder Kopf mit
 * mindestens zwei späteren Köpfen derselben Form hat die Mindestzahl an Segmenten und wird ohne Zählung angenommen.
 * Köpfe verschiedener Formen können dasselbe Kopfende haben (HOST_PFAD {@code ab:/} und
 * LAUFWERK {@code b:/}); das vervielfacht die Zahl der Köpfe nur um einen konstanten Faktor (höchstens die
 * Formzahl). Der Gesamtaufwand bleibt O(n). Eine neue Form mit dichten Köpfen muss das erneut belegen. Die Suche nach
 * dem schließenden Anführungszeichen endet spätestens am nächsten Zeichen derselben Art (jedes Zeichen schließt seine
 * eigene Art). Je Art sind die Bereiche der Paare daher paarweise disjunkt, und die Inhaltsprüfung liest jede
 * Position je Art höchstens einmal. Die Kopfprüfungen an diesen Positionen kosten je Art so viel, wie das
 * Javadoc der jeweiligen Form belegt (HOST_PFAD bis zu 64 Zeichen je {@code :/}-Stelle in überlappenden Abschnitten,
 * HOME bis zu 66 Zeichen je Tilde), insgesamt O(13 mal 64 mal n); keine Rekursion. Die Funde der Paare entstehen
 * nicht unbedingt in Startreihenfolge (Paare verschiedener Arten verschachteln sich, und der erste gültige Kopf
 * hängt vom Ende des Paars und von der Mindestsegmentzahl der Form ab) und werden deshalb einmal nach Start
 * sortiert. Jede Position liegt in höchstens 13 Paar-Funden (einer je Art), die Teilstrings der Paar-Funde summieren
 * sich daher auf höchstens 13 mal n Zeichen. Die Vereinigung ist ein einziger Durchlauf über beide nach Start
 * sortierten Listen, führt nur Zahlen mit und baut je Ergebnis einen Fund.
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
        return detect(text, FORMS);
    }

    /**
     * Wie {@link #detect(String)}, aber nur mit den übergebenen Formen. Paketprivat: Tests prüfen damit, dass eine
     * hinzukommende Form die Funde der bisherigen Formen nicht verkleinert (Obermengen-Garantie).
     */
    List<Finding> detect(String text, PathForm[] forms) {
        Objects.requireNonNull(text, "text");
        List<Finding> base = detectWithoutQuotes(text, forms);
        List<Finding> quoted = detectQuoted(text, forms);
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
    private static List<Finding> detectQuoted(String text, PathForm[] forms) {
        List<Finding> result = collectQuoted(text, forms);
        // Der erste gültige Kopf hängt vom Ende des Paars ab; Paare verschiedener Arten können einander verschachteln.
        result.sort(Comparator.comparingInt(Finding::start));
        return result;
    }

    /**
     * Die Funde der Paare in der Reihenfolge der öffnenden Zeichen (nicht unbedingt nach Start sortiert). Nur für Tests
     * paketprivat; der Produktivpfad ist {@link #detectQuoted}.
     */
    static List<Finding> collectQuoted(String text, PathForm[] forms) {
        List<Finding> result = new ArrayList<>();
        int n = text.length();
        int[] starts = new int[CAP];
        for (int pos = 0; pos < n; pos++) {
            int kind = PathChars.quoteKind(text.charAt(pos));
            if (kind < 0) {
                continue;
            }
            int close = findClosingQuote(text, pos, kind);
            Finding f = close < 0 ? null : quotedFinding(text, pos + 1, close, starts, forms);
            if (f != null) {
                result.add(f);
            }
        }
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
    private static Finding quotedFinding(String text, int from, int to, int[] starts, PathForm[] forms) {
        if (from >= to) {
            return null;
        }
        int count = contentSegmentStarts(text, from, to, starts);
        for (int i = from; i < to; i++) {
            for (PathForm form : forms) {
                if (i != from && !form.isStartBoundary(text, i)) {
                    continue;
                }
                int headEnd = form.headEnd(text, i);
                if (headEnd < 0 || headEnd > to) {
                    continue;
                }
                int segments = countFrom(starts, count, headEnd);
                if (segments >= form.minSegments()) {
                    return new Finding(FindingType.PFAD, text.substring(i, to), i, to,
                            form.confidence(headEnd - i, segments), form.source());
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
     * Überlappende Funde werden zu einem Fund über den gesamten Bereich mit der Quelle des früher beginnenden Fundes
     * (bei gleichem Start der aus {@code a}); aneinandergrenzende Funde bleiben getrennt, jede abgedeckte Position
     * bleibt abgedeckt (Obermenge). Die Konfidenz folgt Plan 7a: (i) Beginnt ein längerer Fund am selben Start wie der
     * bisherige Bereich, gilt seine Konfidenz (der Bereich war nur die kürzere Deutung desselben Kopfes; das setzt
     * voraus, dass {@link PathForm#confidence} in der Segmentzahl nicht fällt); (ii) verlängert ein Fund mit späterem
     * Start den Bereich, gilt die niedrigste Konfidenz der Beteiligten (D-8); (iii) ein Fund, der ganz im Bereich
     * liegt, ändert die Konfidenz nicht. Der Durchlauf nimmt immer den Fund mit dem kleineren Start aus beiden Listen
     * und führt nur Start, Ende, Konfidenz und Quelle; Teilstring und Fund entstehen einmal je Ergebnis (linear).
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
                // Beginnt der längere Fund an derselben Stelle, ist der bisherige nur eine kürzere Deutung desselben
                // Kopfes (z. B. UNC-Server allein, 0.60, gegenüber dem Zitat mit Freigabe, 0.95): es gilt die
                // Konfidenz des längeren, der mit seiner eigenen Segmentzahl gebildet wurde. Beginnt er später,
                // gilt die niedrigste Konfidenz der Beteiligten (D-8).
                confidence = next.start() == start ? next.confidence() : Math.min(confidence, next.confidence());
                end = next.end();
            } else if (next.start() == start && next.end() == end) {
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
        return detectWithoutQuotes(text, FORMS);
    }

    /**
     * Durchlauf ohne Anführungszeichenregel mit den übergebenen Formen. Bei mehreren Formen wird zusätzlich je Form ein
     * Durchlauf mit nur dieser Form gemacht und alles vereinigt: Ein Lauf liefert höchstens einen Fund, und der Fund
     * einer früher beginnenden Form (UNC vor einem Laufwerkskopf im selben Lauf) kann am Ende kürzer ausfallen, weil
     * die Klammerzählung früher beginnt. Ohne die Vereinigung könnte so der Fund einer einzelnen Form durch eine
     * andere Form kleiner werden. Der Aufwand sind (Formzahl + 1) lineare Durchläufe: einer mit allen Formen und je
     * einer je Form (derzeit neun Formen, also zehn Durchläufe).
     */
    List<Finding> detectWithoutQuotes(String text, PathForm[] forms) {
        Objects.requireNonNull(text, "text");
        List<Finding> result = scan(text, forms);
        if (forms.length > 1) {
            for (PathForm form : forms) {
                List<Finding> single = scan(text, new PathForm[] {form});
                if (!single.isEmpty()) {
                    result = union(text, result, single);
                }
            }
        }
        return result;
    }

    /** Ein Durchlauf (Läufe, Beginn, Kürzung) mit den übergebenen Formen; je Beginn gewinnt die erste passende Form. */
    private static List<Finding> scan(String text, PathForm[] forms) {
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
            Finding f = tryForms(text, pos, run, forms);
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
    private static Finding tryForms(String text, int pos, Run run, PathForm[] forms) {
        for (PathForm form : forms) {
            if (!form.isStartBoundary(text, pos)) {
                continue;
            }
            int headEnd = form.headEnd(text, pos);
            if (headEnd < 0) {
                continue;
            }
            // Die pessimistische Zählung (vor allen abschließenden Kürzungszeichen) ist höchstens um EINS kleiner als
            // die Zahl nach der tatsächlichen Kürzung: Ein zusätzliches Segment entsteht nur, wenn hinter einem
            // Trenner ausgeglichene Klammern stehen bleiben (/opt(/) hat tatsächlich zwei Segmente, pessimistisch
            // eines). Reicht die pessimistische Zahl, ist es ein Fund; fehlt genau eines, entscheidet die Zählung nach
            // der Kürzung (nur dann, wenn überhaupt ein Segment hinzukommen kann, O(1) Vorprüfung); sonst kein Fund.
            int minSegments = form.minSegments();
            int pessimistic = run.segmentsFrom(headEnd);
            if (pessimistic < minSegments
                    && (pessimistic + 1 < minSegments || !run.mayGainSegment(text, headEnd))) {
                continue;
            }
            int end = trimEnd(text, pos, run.end, headEnd);
            int segments = run.segmentsTo(text, headEnd, end);
            if (segments < minSegments) {
                continue;
            }
            return new Finding(FindingType.PFAD, text.substring(pos, end), pos, end,
                    form.confidence(headEnd - pos, segments), form.source());
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
        private int runStart;
        private int tail; // pessimistisches Ende: hinter dem letzten Zeichen, das weder Satzzeichen noch Klammer zu ist
        private final int[] starts = new int[CAP]; // absteigend: letzter Segmentanfang zuerst
        private int count;

        void load(String text, int start) {
            int n = text.length();
            int e = start;
            while (e < n && !PathChars.isTerminator(text.charAt(e))) {
                e++;
            }
            end = e;
            runStart = start;
            int t = e;
            while (t > start && (PathChars.isTrailingPunctuation(text.charAt(t - 1))
                    || PathChars.isClosingBracket(text.charAt(t - 1)))) {
                t--;
            }
            tail = t;
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

        /**
         * Anzahl der Segmente ab {@code from} bis zum tatsächlichen Ende {@code actualEnd} (nach der Kürzung),
         * gedeckelt bei 3. Das tatsächliche Ende liegt nie vor {@code tail}; zwischen {@code tail} und dem Ende stehen
         * nur Satzzeichen und schließende Klammern, also keine Trenner. Ein Segment kommt daher nur hinzu, wenn dort
         * (ausgeglichene) Klammern stehen, die ein eigenes Segment bilden: direkt hinter einem Trenner. O(1).
         */
        int segmentsTo(String text, int from, int actualEnd) {
            int segments = segmentsFrom(from);
            if (actualEnd > tail && mayGainSegment(text, from)) {
                segments = Math.min(segments + 1, CAP);
            }
            return segments;
        }

        /**
         * Ob hinter dem pessimistischen Ende ein weiteres Segment stehen kann (O(1)): Zwischen {@code tail} und dem
         * Ende des Laufs stehen nur Satzzeichen und schließende Klammern; sie bilden ein eigenes Segment nur direkt
         * hinter einem Trenner, und nur, wenn {@code tail} hinter dem Kopfende liegt.
         */
        boolean mayGainSegment(String text, int from) {
            return tail < end && tail >= from && tail > runStart && PathChars.isSeparator(text.charAt(tail - 1));
        }
    }
}
