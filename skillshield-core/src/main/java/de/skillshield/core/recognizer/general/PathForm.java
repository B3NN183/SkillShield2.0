package de.skillshield.core.recognizer.general;

/**
 * Pfadformen des {@link PathRecognizer} (Architektur-Plan Kap. 2.4.2). Jede Form ist eine Konstante mit eigenem
 * Kopf, Mindestzahl an Segmenten, Konfidenz und Quelle; Laufzerlegung, Ende, Klammerregel und Segmentzählung sind
 * allen Formen gemeinsam und liegen im Erkenner. Bisher vorhanden: {@link #LAUFWERK} und {@link #UNC}; die weiteren
 * Formen (Unix, Home, relativ, Umgebungsvariable, {@code file:}) kommen mit den Folgetasks als zusätzliche
 * Konstanten.
 */
enum PathForm {

    /**
     * Laufwerkspfad: ASCII-Buchstabe, {@code :}, Trenner ({@code C:\}, {@code c:/}). Mindestens ein Segment hinter
     * dem Kopf, Konfidenz 0.95 (sicher), Quelle {@code pfad-laufwerk}.
     *
     * <p>Eigene Entscheidung (Plan-Abweichung, vom Auftraggeber verlangt): Für Laufwerkspfade gilt keine
     * Startgrenze. Der Laufwerksbuchstabe ist immer der Buchstabe direkt vor dem {@code :}; was davor steht
     * ({@code Pfad:C:\x}, {@code abcC:\x}, {@code .....C:\x}), verhindert den Fund nicht und wird nicht in ihn
     * aufgenommen. Das Risiko (ein Wort, das auf einen Buchstaben endet, gefolgt von {@code :\} oder {@code :/})
     * ist ein zu großer Fund, kein Leck.
     */
    LAUFWERK {
        @Override
        int headEnd(String text, int i) {
            if (i + 2 >= text.length()) {
                return -1;
            }
            char letter = text.charAt(i);
            boolean ascii = (letter >= 'A' && letter <= 'Z') || (letter >= 'a' && letter <= 'z');
            if (ascii && text.charAt(i + 1) == ':' && PathChars.isSeparator(text.charAt(i + 2))) {
                return i + 3;
            }
            return -1;
        }

        @Override
        boolean requiresStartBoundary() {
            return false;
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int segmentsCappedAt3) {
            return CONFIDENCE_SURE;
        }

        @Override
        String source() {
            return "pfad-laufwerk";
        }
    },

    /**
     * UNC-/Netzpfad: ein Lauf aus mindestens zwei Backslashes ({@code \\srv\freigabe}, auch
     * {@code \\\\srv\\freigabe} aus JSON oder Quelltext), danach ein Nicht-Trenner; Server und Freigabe sind die
     * ersten beiden Segmente. Schon ein Segment hinter dem Kopf (Server ohne Freigabe, {@code \\fileserver01}) ist
     * ein Fund, aber nur mit Konfidenz 0.60 (fraglich; Nutzerentscheidung 20.11.2026, Plan Nachtrag T-12 Punkt 7:
     * ein Servername allein ist schutzwürdig, kann aber auch eine maskierte Zeichenfolge wie {@code \\n} sein); ab
     * zwei Segmenten 0.95 (sicher), siehe {@link #confidence(int)}. Quelle {@code pfad-unc}. Das Gerätepräfix
     * ({@code \?\C:\x}, {@code \.\pipe\x}) ist kein Sonderfall: {@code ?} bzw. {@code .} ist dann der erste
     * "Servername" (Entscheidung N-2: erkennen).
     *
     * <p>Der Kopf besteht nur aus dem Backslash-Lauf und dem einen Zeichen dahinter; Servername und Freigabe liest
     * {@code headEnd} nicht, deren Segmente zählt der Erkenner aus den Laufdaten. Ein Lauf wird nur an seinem ersten
     * Backslash geprüft (davor steht kein Backslash); die Prüfung liest den Lauf einmal, jede weitere Position darin
     * endet nach einem Zeichen. Die Kopfprüfung kostet daher insgesamt höchstens O(Textlänge).
     *
     * <p>Eigene Entscheidung (Plan-Abweichung, Grundsatz "zu großer oder Teilfund besser als kein Fund"):
     * <ul>
     *   <li>Es gilt keine Startgrenze. {@code Pfad:\\srv\freigabe\x}, {@code x=\\srv\freigabe\x},
     *       {@code (\\srv\freigabe\x)} und {@code siehe\\srv\freigabe\x} liefern einen Fund ab den Backslashes (oder
     *       ab einem davorstehenden Laufwerkskopf, siehe {@link #LAUFWERK}); der Text davor gehört nicht dazu.</li>
     *   <li>Die Länge des Backslash-Laufs ist nicht auf 2 oder 4 beschränkt (Plan: "genau 2 oder 4"); auch 3 oder 5
     *       und mehr Backslashes ergeben einen Fund. Sonst bliebe ein Pfad, bei dem beim Maskieren ein Backslash
     *       verloren ging oder dazukam, vollständig im Klartext.</li>
     * </ul>
     * Schrägstrich-UNC ({@code //srv/freigabe}) gehört nach dem Plan (Kap. 2.4.2, Entscheidung N-2) zur Form UNIX
     * und kommt mit T-13; gemischte Trenner hinter den Backslashes ({@code \\srv/freigabe\a}) gelten hier.
     */
    UNC {
        @Override
        int headEnd(String text, int i) {
            int n = text.length();
            if (i >= n || text.charAt(i) != '\\' || (i > 0 && text.charAt(i - 1) == '\\')) {
                return -1;
            }
            int j = i + 1;
            while (j < n && text.charAt(j) == '\\') {
                j++;
            }
            if (j - i < 2 || j >= n || PathChars.isSeparator(text.charAt(j))) {
                return -1;
            }
            return j;
        }

        @Override
        boolean requiresStartBoundary() {
            return false;
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int segmentsCappedAt3) {
            return segmentsCappedAt3 >= 2 ? CONFIDENCE_SURE : CONFIDENCE_QUESTIONABLE;
        }

        @Override
        String source() {
            return "pfad-unc";
        }
    };

    /** Konfidenz "sicher" (Stufe SICHER). */
    private static final double CONFIDENCE_SURE = 0.95;

    /** Konfidenz "fraglich" (Stufe FRAGLICH), z. B. UNC-Kopf mit nur einem Segment. */
    private static final double CONFIDENCE_QUESTIONABLE = 0.60;

    /**
     * Ende des Kopfs (exklusiv) oder -1. Prüft nur Zeichen ab {@code i} (UNC liest zusätzlich das eine Zeichen davor,
     * um nur am ersten Backslash eines Laufs zu prüfen) und liest nie über das Textende hinaus; die Startgrenze prüft
     * der Aufrufer (falls {@link #requiresStartBoundary()}).
     */
    abstract int headEnd(String text, int i);

    /** Mindestzahl der Segmente hinter dem Kopf (nach dem Kürzen am Ende). */
    abstract int minSegments();

    /**
     * Konfidenz je Segmentzahl hinter dem Kopf (gedeckelt bei 3). Die Segmentzahl ist die des einzelnen Fundes (je
     * Kopf gezählt), nicht die eines anderen Kandidaten an derselben Stelle. Vertrag: in der Segmentzahl monoton nicht
     * fallend (Voraussetzung der Konfidenzregel beim Zusammenlegen, Plan Nachtrag T-12 Punkt 7a (i)).
     */
    abstract double confidence(int segmentsCappedAt3);

    /** Quellname der Funde, Format {@code [a-z][a-z0-9-]{0,31}}. */
    abstract String source();

    /** Ob der Kopf nur an einer Startgrenze ({@link PathChars#isStartBoundary}) beginnen darf. */
    boolean requiresStartBoundary() {
        return true;
    }
}
