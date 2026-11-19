package de.skillshield.core.recognizer.general;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt Dateipfade rein per Zeichenprüfung (handgeschriebener, linearer Durchlauf, kein Regex, kein
 * Dateisystemzugriff, keine Prüfung auf Existenz; Architektur-Plan Kap. 2.4). Ein erkannter Pfad wird als genau ein
 * Fund vom Typ {@link FindingType#PFAD} geliefert; Programm-, Benutzer-, Server- und Ordnernamen darin sind damit
 * mitgeschützt.
 *
 * <p>Stand dieses Tasks: nur die Form {@link PathForm#LAUFWERK} und nur Pfade ohne Anführungszeichen; die weiteren
 * Formen und die Anführungszeichenregel folgen mit den Folgetasks, ohne dass sich Läufe, Ende und Segmentzählung
 * ändern.
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
 * Laufende. Insgesamt O(n), keine Rekursion.
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
            int segments = 0;
            for (int k = 0; k < count; k++) {
                if (starts[k] >= from) {
                    segments++;
                }
            }
            return segments;
        }
    }
}
