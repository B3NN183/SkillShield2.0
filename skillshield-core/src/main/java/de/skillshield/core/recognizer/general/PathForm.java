package de.skillshield.core.recognizer.general;

/**
 * Pfadformen des {@link PathRecognizer} (Architektur-Plan Kap. 2.4.2). Jede Form ist eine Konstante mit eigenem
 * Kopf, Mindestzahl an Segmenten, Konfidenz und Quelle; Laufzerlegung, Ende, Klammerregel und Segmentzählung sind
 * allen Formen gemeinsam und liegen im Erkenner. Bisher vorhanden: {@link #LAUFWERK}; die weiteren Formen (UNC,
 * Unix, Home, relativ, Umgebungsvariable, {@code file:}) kommen mit den Folgetasks als zusätzliche Konstanten.
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
            return 0.95;
        }

        @Override
        String source() {
            return "pfad-laufwerk";
        }
    };

    /**
     * Ende des Kopfs (exklusiv) oder -1. Prüft nur Zeichen ab {@code i} und liest nie über das Textende hinaus; die
     * Startgrenze prüft der Aufrufer (falls {@link #requiresStartBoundary()}).
     */
    abstract int headEnd(String text, int i);

    /** Mindestzahl der Segmente hinter dem Kopf (nach dem Kürzen am Ende). */
    abstract int minSegments();

    /** Konfidenz je Segmentzahl hinter dem Kopf (gedeckelt bei 3). */
    abstract double confidence(int segmentsCappedAt3);

    /** Quellname der Funde, Format {@code [a-z][a-z0-9-]{0,31}}. */
    abstract String source();

    /** Ob der Kopf nur an einer Startgrenze ({@link PathChars#isStartBoundary}) beginnen darf. */
    boolean requiresStartBoundary() {
        return true;
    }
}
