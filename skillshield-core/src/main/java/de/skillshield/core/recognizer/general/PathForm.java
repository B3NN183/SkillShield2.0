package de.skillshield.core.recognizer.general;

/**
 * Pfadformen des {@link PathRecognizer} (Architektur-Plan Kap. 2.4.2). Jede Form ist eine Konstante mit eigenem
 * Kopf, Mindestzahl an Segmenten, Konfidenz und Quelle; Laufzerlegung, Ende, Klammerregel und Segmentzählung sind
 * allen Formen gemeinsam und liegen im Erkenner. Bisher vorhanden: {@link #LAUFWERK}, {@link #UNC}, {@link #UNIX},
 * {@link #HOME} und {@link #HOST_PFAD}; die weiteren Formen (relativ, Umgebungsvariable, {@code file:}) kommen mit
 * den Folgetasks als zusätzliche Konstanten.
 *
 * <p>Laufzeit: Der Erkenner prüft jede Position eines Laufs einmal als Beginn; im Zitat-Pfad ruft die Inhaltsprüfung
 * die Kopfprüfung an jeder Inhaltsposition je Art auf (siehe das Klassen-Javadoc von {@code PathRecognizer}). Den
 * Beleg, dass die einzelne Kopfprüfung dabei linear bleibt, führt jede Form in ihrem eigenen Javadoc.
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
     *
     * <p>Laufzeit des Kopfs: Die Prüfung liest höchstens drei Zeichen ab der Position (O(1) je Position).
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
        double confidence(int headLength, int segmentsCappedAt3) {
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
     * zwei Segmenten 0.95 (sicher), siehe {@link #confidence(int, int)}. Quelle {@code pfad-unc}. Das Gerätepräfix
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
     * (seit T-13); gemischte Trenner hinter den Backslashes ({@code \\srv/freigabe\a}) gelten hier.
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
        double confidence(int headLength, int segmentsCappedAt3) {
            return segmentsCappedAt3 >= 2 ? CONFIDENCE_SURE : CONFIDENCE_QUESTIONABLE;
        }

        @Override
        String source() {
            return "pfad-unc";
        }
    },

    /**
     * Absoluter Unix-Pfad ({@code /opt/beispiel-tool/bin/tool}). Kopf ist ein einzelner {@code /}; er beginnt nur an
     * einer Startgrenze ({@link PathChars#isStartBoundary}: Textanfang, Leerraum, eines der 13 Anführungszeichen
     * aus T-11 (gerade und typografisch), {@code ( [ { < > =} und {@code , ; |}; Plan 2.4.1 mit Nachtrag
     * 20.11.2026, Punkte 1 und 3b) und braucht mindestens zwei Segmente dahinter ({@code /etc} und {@code /s} sind
     * kein Fund). Konfidenz bei zwei Segmenten 0.80 (wahrscheinlich), ab drei 0.90 (sicher); Quelle
     * {@code pfad-unix}. Schrägstrich-UNC
     * ({@code //srv-beispiel/freigabe}, Entscheidung N-2) gehört hierher: {@code srv-beispiel} ist das erste Segment,
     * {@code //} zählt wie ein Trenner; {@code //} allein und {@code // Kommentar} haben kein Segment.
     *
     * <p>Plan-Folgen (im Test dokumentiert): {@code und /oder/beides} und {@code rd /s/q} ergeben einen Fund; ein
     * Schalter mit Pfadwert ({@code /log=C:\Logs\a.log}) ist ein Fund über den ganzen Ausdruck, weil der Backslash
     * ebenfalls Trenner ist. Wegen der Startgrenze ergibt {@code siehe/etc/hosts} (Buchstabe davor) keinen Fund; das
     * ist eine vom Nutzer bestätigte bekannte Grenze (Nachtrag 20.11.2026 Punkt 4). Mit den Startgrenzen {@code ,},
     * {@code ;} und {@code |} sind {@code Datei,/etc/hosts}, {@code a;/opt/x/y} und {@code cmd|/opt/x/y} Funde ab dem
     * {@code /}.
     *
     * <p>Der Kopf prüft nur das eine Zeichen an der Position (O(1)).
     */
    UNIX {
        @Override
        int headEnd(String text, int i) {
            return i < text.length() && text.charAt(i) == '/' ? i + 1 : -1;
        }

        @Override
        int minSegments() {
            return 2;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return segmentsCappedAt3 >= 3 ? CONFIDENCE_UNIX_SURE : CONFIDENCE_PROBABLE;
        }

        @Override
        String source() {
            return "pfad-unix";
        }
    },

    /**
     * Home-Pfad: {@code ~/beispiel/konfig.yml} (eigener Benutzer, Konfidenz 0.90, sicher) und
     * {@code ~erika/akte.pdf} (Home-Verzeichnis eines anderen Benutzers, Konfidenz 0.80, wahrscheinlich; Nachtrag
     * 20.11.2026 Punkt 2). Mindestens ein Segment hinter dem {@code /}, Startgrenze erforderlich, Quelle
     * {@code pfad-home}. Der Benutzername ist {@code [A-Za-z_][A-Za-z0-9._-]{0,63}} direkt hinter der Tilde und endet
     * am {@code /}; kein Fund: {@code ~}, {@code ~/}, {@code ~erika}, {@code ~erika/} (kein Segment), {@code ~30/40},
     * {@code ~.x/y}, ein Name mit mehr als 64 Zeichen und {@code ~} mit Backslash. Es wird nichts aufgelöst (kein
     * Zugriff auf Systemeigenschaften oder Dateisystem). Die Startgrenze sind wie bei {@link #UNIX} alle 13
     * Anführungszeichen-Arten (Plan Nachtrag Punkt 3b).
     *
     * <p>Modellierung: eine Form mit zwei Kopfvarianten. Die Konfidenz hängt von der Kopflänge ab (Kopf {@code ~/}:
     * Länge 2, 0.90; Kopf {@code ~name/}: länger, 0.80), nicht von der Segmentzahl; sie ist damit in der Segmentzahl
     * konstant, also monoton nicht fallend ({@link #confidence(int, int)}). Der Kopf bestimmt die Variante und liegt
     * am Start des Fundes; Funde desselben Starts haben daher dieselbe Variante, die Konfidenzregel beim
     * Zusammenlegen (Plan 7a (i)) bleibt gültig.
     *
     * <p>Laufzeit des Kopfs: Der Name wird höchstens 64 Zeichen lang gelesen (je Kopf höchstens 66 Zeichen). Ein Name
     * enthält nie eine Tilde, und das Lesen endet am ersten Nicht-Namenszeichen; die Leseabschnitte zweier Köpfe
     * überlappen sich daher nicht (der Abschnitt des früheren endet spätestens vor der Tilde des späteren). Die Summe
     * aller Kopfprüfungen bleibt höchstens die Textlänge; die Begrenzung auf 64 Zeichen hält den Aufwand je Kopf
     * konstant (amortisiert O(1)).
     */
    HOME {
        @Override
        int headEnd(String text, int i) {
            int n = text.length();
            if (i + 1 >= n || text.charAt(i) != '~') {
                return -1;
            }
            char first = text.charAt(i + 1);
            if (first == '/') {
                return i + 2;
            }
            if (!PathChars.isAsciiLetter(first) && first != '_') {
                return -1;
            }
            int j = i + 2;
            while (j < n && j - (i + 1) < MAX_NAME_LENGTH && PathChars.isHostChar(text.charAt(j))) {
                j++;
            }
            return j < n && text.charAt(j) == '/' ? j + 1 : -1;
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return headLength == 2 ? CONFIDENCE_UNIX_SURE : CONFIDENCE_PROBABLE;
        }

        @Override
        String source() {
            return "pfad-home";
        }
    },

    /**
     * Rechner vor dem Pfad ({@code host.example:/export/home}, scp-/NFS-Schreibweise; Nachtrag 20.11.2026 Punkt 3):
     * Direkt vor {@code :/} steht ein Lauf aus {@code [A-Za-z0-9._-]} mit mindestens einem ASCII-Buchstaben (höchstens
     * die letzten 64 Zeichen des Laufs, nie über den Laufanfang hinaus), dahinter {@code :}, {@code /} und mindestens
     * ein Segment. Der Fund beginnt am Anfang des Host-Laufs, das Wort vor dem Doppelpunkt ({@code Pfad:/etc/hosts})
     * wird mitersetzt. Keine Startgrenze. Quelle {@code pfad-host}, Konfidenz 0.80 bei einem Segment, 0.90 ab zwei.
     * Es wird nichts aufgelöst und nicht geprüft, ob der Name ein Rechner ist (kein DNS, kein Netzwerkzugriff).
     *
     * <p>Eigene Entscheidung (Plan-Abweichung, im Bericht genannt): Folgt auf {@code :/} sofort ein weiteres {@code /}
     * ({@code https://...}, {@code file:///...}, {@code ftp://...}), ist es kein Kopf dieser Form. Adressen mit
     * {@code ://} gehören dem URL-Erkenner (und für {@code file:} der Form FILE_URI, T-16); ein Host-Fund über das
     * Schema würde die URL-Grenzen verändern (Fund ab {@code https} bis zum Ende des Pfad-Laufs statt der URL-Grenzen,
     * bei Teilüberlappung mit einem Zitat {@code pfad-host} statt {@code zusammengelegt}). Der Schutz bleibt: der
     * Laufwerkskopf {@code s:/} deckt das Schema ab dem letzten Buchstaben ab wie bisher.
     *
     * <p>Ein Lauf aus genau einem Zeichen ({@code C:/x}) ist die Form {@link #LAUFWERK}: gleicher Start, gleiche
     * Abdeckung, Konfidenz 0.95; diese Form lässt ihn aus, damit der Fund unverändert {@code pfad-laufwerk} mit 0.95
     * bleibt. Beginnt der Host-Lauf früher als der Laufwerkskopf ({@code host.example:/export/home} mit dem
     * Laufwerkskopf {@code e:/}), gilt der längere Fund des früheren Starts; seine Konfidenz ist die niedrigere der
     * Beteiligten (Plan 7a (ii)).
     *
     * <p><b>Laufzeit (linear).</b> Der Kopf liegt vor der Stelle {@code :/}, an der man ihn erkennt. Der Erkenner prüft
     * aber jede Position einmal von links nach rechts; der Kopf wird daher nur an zwei Arten von Positionen
     * gelesen (an allen anderen Positionen endet die Prüfung nach einem oder zwei Zeichen):
     * <ul>
     *   <li><b>Wortanfang</b> (Position ist ein Host-Zeichen, das Zeichen davor nicht): Es werden höchstens 65 Zeichen
     *       des Host-Laufs gelesen, bis zum ersten Nicht-Host-Zeichen. Die Leseabschnitte zweier Wortanfänge liegen in
     *       verschiedenen Läufen (ein Abschnitt endet spätestens am Laufende), überlappen sich also nie: Diese Lesungen
     *       kosten insgesamt höchstens die Textlänge.</li>
     *   <li><b>Mitten im Lauf</b> (Zeichen davor ist Host-Zeichen): Nur wenn genau 64 Zeichen weiter {@code :/} steht
     *       (zwei Zeichen, O(1)), werden die 64 Zeichen davor gelesen. Das ist der Teilfund eines Laufs mit mehr als 64
     *       Zeichen ({@code a} mal eine Million, dann {@code :/x}: der Fund beginnt 64 Zeichen vor dem {@code :}). Zu
     *       jeder {@code :/}-Stelle gibt es höchstens eine solche Position. Diese Abschnitte dürfen sich überlappen:
     *       Liegen mehrere {@code :/} innerhalb von 64 Zeichen und scheitern die früheren Köpfe (kein Buchstabe im
     *       Lauf), wird dasselbe Stück mehrfach gelesen ({@code 1} mal 70, {@code :/}, {@code 1} mal 30, {@code :/x}).
     *       Die Schranke ist deshalb nicht "jedes Zeichen höchstens einmal", sondern höchstens 64 gelesene Zeichen je
     *       {@code :/}-Stelle.</li>
     * </ul>
     * Der Gesamtaufwand der Kopfprüfungen ist damit höchstens n (Wortanfänge) plus 64 mal die Zahl der
     * {@code :/}-Stellen (höchstens n/2), also O(64 n), linear in der Textlänge. Ein Fund beginnt immer an einem
     * ASCII-Zeichen (kein Surrogat, keine Kombinationsfolge).
     */
    HOST_PFAD {
        @Override
        int headEnd(String text, int i) {
            int n = text.length();
            if (i >= n || !PathChars.isHostChar(text.charAt(i))) {
                return -1;
            }
            int colon;
            boolean letter = false;
            if (i == 0 || !PathChars.isHostChar(text.charAt(i - 1))) {
                int j = i;
                while (j < n && j - i <= MAX_NAME_LENGTH && PathChars.isHostChar(text.charAt(j))) {
                    letter |= PathChars.isAsciiLetter(text.charAt(j));
                    j++;
                }
                if (j - i > MAX_NAME_LENGTH || j - i < 2) {
                    return -1; // Lauf länger als 64 Zeichen (Teilfund mitten im Lauf) oder ein Zeichen (LAUFWERK)
                }
                colon = j;
            } else {
                colon = i + MAX_NAME_LENGTH;
                if (colon + 1 >= n || text.charAt(colon) != ':' || text.charAt(colon + 1) != '/') {
                    return -1;
                }
                for (int j = i; j < colon; j++) {
                    char c = text.charAt(j);
                    if (!PathChars.isHostChar(c)) {
                        return -1;
                    }
                    letter |= PathChars.isAsciiLetter(c);
                }
            }
            if (!letter || colon + 1 >= n || text.charAt(colon) != ':' || text.charAt(colon + 1) != '/') {
                return -1;
            }
            if (colon + 2 < n && text.charAt(colon + 2) == '/') {
                return -1; // "://": Adresse (URL, file:), nicht scp-Schreibweise
            }
            return colon + 2;
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
        double confidence(int headLength, int segmentsCappedAt3) {
            return segmentsCappedAt3 >= 2 ? CONFIDENCE_UNIX_SURE : CONFIDENCE_PROBABLE;
        }

        @Override
        String source() {
            return "pfad-host";
        }
    };

    /** Höchstlänge eines Benutzernamens ({@code ~name/}) und eines Host-Laufs vor {@code :/}. */
    private static final int MAX_NAME_LENGTH = 64;

    /** Konfidenz "sicher" (Stufe SICHER). */
    private static final double CONFIDENCE_SURE = 0.95;

    /** Konfidenz "sicher" für Unix ab drei Segmenten, Home ({@code ~/}) und Host ab zwei Segmenten (Plan N-3). */
    private static final double CONFIDENCE_UNIX_SURE = 0.90;

    /** Konfidenz "wahrscheinlich": Unix mit zwei Segmenten, {@code ~name/} und Host mit einem Segment. */
    private static final double CONFIDENCE_PROBABLE = 0.80;

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
     * Konfidenz eines Fundes dieser Form: {@code headLength} ist die Länge des Kopfes ({@code headEnd - start}),
     * {@code segmentsCappedAt3} die Zahl der Segmente hinter dem Kopf, gedeckelt bei 3. Die Segmentzahl ist die des
     * einzelnen Fundes (je Kopf gezählt), nicht die eines anderen Kandidaten an derselben Stelle. Nur HOME wertet die
     * Kopflänge aus ({@code ~/} gegenüber {@code ~name/}), alle anderen Formen ignorieren sie. Vertrag: in der
     * Segmentzahl monoton nicht fallend, für jede Kopflänge (Voraussetzung der Konfidenzregel beim Zusammenlegen,
     * Plan Nachtrag T-12 Punkt 7a (i)).
     */
    abstract double confidence(int headLength, int segmentsCappedAt3);

    /** Quellname der Funde, Format {@code [a-z][a-z0-9-]{0,31}}. */
    abstract String source();

    /** Ob der Kopf nur an einer Startgrenze ({@link PathChars#isStartBoundary}) beginnen darf. */
    boolean requiresStartBoundary() {
        return true;
    }
}
