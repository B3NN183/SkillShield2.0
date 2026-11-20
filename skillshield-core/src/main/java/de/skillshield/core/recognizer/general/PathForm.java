package de.skillshield.core.recognizer.general;

/**
 * Pfadformen des {@link PathRecognizer} (Architektur-Plan Kap. 2.4.2). Jede Form ist eine Konstante mit eigenem
 * Kopf, Mindestzahl an Segmenten, Konfidenz und Quelle; Laufzerlegung, Ende, Klammerregel und Segmentzählung sind
 * allen Formen gemeinsam und liegen im Erkenner. Bisher vorhanden: {@link #LAUFWERK}, {@link #UNC}, {@link #UNIX},
 * {@link #HOME}, {@link #HOST_PFAD}, {@link #RELATIV}, {@link #EINZEL_BACKSLASH}, {@link #BENUTZER_WURZEL},
 * {@link #WURZEL_ZWEI_SEGMENTE} und {@link #UMGEBUNG}; die weitere Form ({@code file:}) kommt mit dem Folgetask als
 * zusätzliche Konstante. Welche Position als Beginn eines Kopfes zulässig ist, entscheidet je Form
 * {@link #isStartBoundary(String, int)}.
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
        boolean isStartBoundary(String text, int i) {
            return true;
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
        boolean isStartBoundary(String text, int i) {
            return true;
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
        boolean isStartBoundary(String text, int i) {
            return true;
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
    },

    /**
     * Relativer Pfad mit eindeutigem Kopf ({@code ./skript.sh}, {@code .\tools\tool.exe}, {@code ../konf/app.yml},
     * {@code ..\..\daten\akte.pdf}; Plan 2.4.2 Zeile RELATIV, AK-14): ein Punkt oder zwei Punkte, direkt dahinter ein
     * Trenner. Mindestens ein Segment hinter dem Kopf ({@code ./a}; {@code .\}, {@code ../} und {@code ..} allein sind
     * kein Fund). {@code ..\..\x} ist ein Fund: das erste {@code ..} steht dort nicht am Ende. Dagegen ist bei
     * {@code ..\..}, {@code ../..} und {@code cd ..\..} kein Fund: die Kürzung am Ende entfernt die abschließenden
     * Punkte bis zum Kopfende, {@code ..} am Ende besteht nur aus Kürzungszeichen und ist kein Segment (bekannte
     * Grenze, Plan Nachtrag T-14 Punkt 4). Konfidenz 0.80 (wahrscheinlich, nie sicher),
     * Quelle {@code pfad-relativ}. Ohne Kopf gibt es keinen Fund ({@code ordner\datei.txt}, {@code ordner/datei.txt},
     * {@code Files\Mein}, {@code Tool\tool.exe}; Nutzerentscheidung D-6).
     *
     * <p>Startgrenze erforderlich (Plan 2.4.2: "Die Startgrenze gilt für alle Formen";
     * {@link PathChars#isStartBoundary}): Der Punkt ist in gewöhnlichem Text häufig, und {@code ./} oder {@code ../}
     * entsteht dort durch Satzende oder Aufzählung ({@code Ende../x}, {@code siehe oben./unten}, {@code usw./etc.},
     * {@code 1./2.}, {@code 1.0/2.0}). Wegen der Startgrenze ist das kein Fund; ebenso {@code ...\x} und
     * {@code .../x} (drei Punkte sind kein Kopf, und der zweite Punkt hat den ersten davor).
     *
     * <p>Zusätzliche Startgrenze nur für diese Form (Plan Nachtrag T-14 Punkt 5 (a)): Auch ein {@code :} direkt vor
     * dem Kopf genügt ({@code Pfad:.\erika\akte.pdf}, {@code host.example:./a/b}, {@code C:./a} ab {@code ./a},
     * {@code PATH=.:./bin:../erika/bin} ab dem zweiten {@code .}). Bekannte Fehltreffer: {@code usw.:./x} und
     * {@code 12:./30}. Der Doppelpunkt ist auch für {@link #BENUTZER_WURZEL} und {@link #WURZEL_ZWEI_SEGMENTE}
     * Startgrenze, für {@link #UNIX}, {@link #HOME} und {@link #EINZEL_BACKSLASH} nicht.
     *
     * <p>Laufzeit des Kopfs: Die Prüfung liest höchstens drei Zeichen ab der Position (O(1) je Position);
     * {@code headEnd} liest nichts vor der Position, {@code isStartBoundary} liest das Zeichen davor. Es gibt keine
     * Auflösung gegen ein Arbeitsverzeichnis.
     */
    RELATIV {
        @Override
        int headEnd(String text, int i) {
            int n = text.length();
            if (i >= n || text.charAt(i) != '.') {
                return -1;
            }
            int j = i + 1;
            if (j < n && text.charAt(j) == '.') {
                j++;
            }
            return j < n && PathChars.isSeparator(text.charAt(j)) ? j + 1 : -1;
        }

        @Override
        boolean isStartBoundary(String text, int i) {
            return startBoundaryOrColon(text, i);
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return CONFIDENCE_PROBABLE;
        }

        @Override
        String source() {
            return "pfad-relativ";
        }
    },

    /**
     * Einzelner führender Backslash ({@code \srv\freigabe\a.txt}, {@code \Users\erika.mustermann\Desktop\x};
     * Nutzerentscheidung 20.11.2026, Plan Nachtrag T-12 Punkt 8: entsteht, wenn Chat oder Wiki aus {@code \\} ein
     * {@code \} machen). Kopf ist genau ein Backslash: davor steht kein Backslash und dahinter kein Trenner (kein
     * Kopf am zweiten Backslash eines Doppel-Backslashs; {@code \srv} gehört der Form {@link #UNC}). Mindestens zwei
     * Segmente dahinter, Konfidenz 0.75 (wahrscheinlich), Quelle {@code pfad-relativ} (der Plan nennt keine eigene
     * Quelle; die Form gehört zu T-14, Eigene Entscheidung). Die Konfidenz ist konstant und damit in der Segmentzahl
     * nicht fallend.
     *
     * <p>Eigene Entscheidung (Plan-Regel "Die Startgrenze gilt für alle Formen"): Startgrenze erforderlich. "Führend"
     * heißt am Wortanfang; sonst würde in {@code Ordner\Datei\x.txt} der Teil ab dem ersten Backslash zum Fund, ein
     * Teilfund, der dem Nutzerentscheid "Pfade ohne Kopf sind kein Fund" widerspricht (AK-14, Registry-Schlüssel). Mit
     * Startgrenze ergeben {@code x\Users\erika\a} und {@code HKEY_LOCAL_MACHINE\SOFTWARE\x} keinen Fund;
     * {@code Pfad:\Users\erika} ist ein Laufwerksfund ab {@code d:}.
     *
     * <p>Bekannte Folge (Nutzerentscheidung): Jede Zeichenfolge mit führendem Backslash und zwei Segmenten ist ein
     * Fund, auch wenn sie kein Pfad ist: {@code \d+\s}, {@code \textbf{a}\cite{b}},
     * {@code \begin{document}\section{x}}, {@code "\r\n"} (im Zitat), {@code \n\t}. Ein einzelnes Escape
     * ({@code \n}) hat nur ein Segment und ist kein Fund.
     *
     * <p>Laufzeit des Kopfs: höchstens zwei Zeichen ab der Position und das eine davor (O(1) je Position); es werden
     * nie Segmente gelesen, deren Zahl liefern die Laufdaten.
     */
    EINZEL_BACKSLASH {
        @Override
        int headEnd(String text, int i) {
            if (i + 1 >= text.length() || text.charAt(i) != '\\' || (i > 0 && text.charAt(i - 1) == '\\')) {
                return -1;
            }
            return PathChars.isSeparator(text.charAt(i + 1)) ? -1 : i + 1;
        }

        @Override
        int minSegments() {
            return 2;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return CONFIDENCE_SINGLE_BACKSLASH;
        }

        @Override
        String source() {
            return "pfad-relativ";
        }
    },

    /**
     * Abgeschnittener Benutzerpfad mit einem Segment ohne Laufwerk und ohne führenden Trenner
     * ({@code Users\erika.mustermann\Desktop}, {@code Benutzer\erika}, {@code users/erika}; Plan Nachtrag T-14
     * Punkt 5 (b)): Kopf ist {@code Users} mit {@code \} oder {@code /} oder {@code Benutzer} mit {@code \} (nur ASCII,
     * Groß- und Kleinschreibung egal), dahinter mindestens ein Segment. Der Fund beginnt am ersten Buchstaben des
     * Wurzelnamens. Startgrenze erforderlich ({@link PathForm#isStartBoundary(String, int)}: die gemeinsame Grenze
     * oder {@code :} davor), Konfidenz 0.60 (fraglich), Quelle {@code pfad-relativ}. {@code Benutzer} mit
     * Schrägstrich ist kein Kopf dieser Form, sondern der Form {@link #WURZEL_ZWEI_SEGMENTE} (mindestens zwei
     * Segmente).
     *
     * <p>Kein Fund: {@code Users}, {@code Users\} (kein Segment), {@code Users:\a} (kein Wurzel-Kopf; es bleibt der
     * Laufwerksfund ab {@code s:\a}, 0.95), {@code xUsers\a} und {@code usersa\b} (kein Kopf), {@code C:\Users\erika\x}
     * (die Startgrenze fehlt an {@code Users}; es bleibt der Laufwerksfund mit 0.95) und
     * {@code \Users\erika\x} (ebenso; es bleibt {@link #EINZEL_BACKSLASH}, 0.75).
     *
     * <p>Entscheidung (Plan Nachtrag T-14 Punkt 5 (b)): Nach {@code Users} und {@code Benutzer\} folgt in Pfaden fast
     * immer ein Benutzername; deshalb gilt schon ein Segment dahinter als Fund, obwohl das die Nutzerentscheidung
     * "Pfade ohne Kopf sind kein Fund" um diese Ordnernamen ergänzt. Bekannte Fehltreffer (zu viel ersetzt, kein
     * Leck): {@code Users/Groups}, {@code Users/Admins}, {@code Benutzer\Gruppen} und jedes andere Wort {@code Users}
     * oder {@code Benutzer\} direkt vor einem Trenner. {@code Users and groups} ohne Trenner ist kein Fund.
     *
     * <p>Laufzeit des Kopfs: Die Prüfung liest höchstens neun Zeichen ab der Position ({@code benutzer\}), nichts
     * davor und nie Segmente beliebiger Länge (O(1) je Position). Die Formzahl ändert die Laufzeit des Erkenners nur um
     * einen konstanten Faktor.
     */
    BENUTZER_WURZEL {
        @Override
        int headEnd(String text, int i) {
            boolean users = PathChars.matchesAsciiIgnoreCase(text, i, "users");
            boolean benutzer = !users && PathChars.matchesAsciiIgnoreCase(text, i, "benutzer");
            if (!users && !benutzer) {
                return -1;
            }
            int sep = i + (users ? "users".length() : "benutzer".length());
            if (sep >= text.length() || !PathChars.isSeparator(text.charAt(sep))) {
                return -1;
            }
            if (benutzer && text.charAt(sep) == '/') {
                return -1; // Schrägstrich-Variante: Form WURZEL_ZWEI_SEGMENTE
            }
            return sep + 1;
        }

        @Override
        boolean isStartBoundary(String text, int i) {
            return startBoundaryOrColon(text, i);
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return CONFIDENCE_QUESTIONABLE;
        }

        @Override
        String source() {
            return "pfad-relativ";
        }
    },

    /**
     * Abgeschnittener Pfad, dessen Kopf allein zu mehrdeutig ist und der deshalb mindestens zwei Segmente braucht
     * ({@code home/erika/akte.pdf}, {@code Benutzer/erika/Desktop}; Plan Nachtrag T-14 Punkt 5 (b) und Korrektur nach
     * der security-Prüfung Durchlauf 2). Zwei Kopfvarianten: {@code home/} (nur kleingeschrieben, nur mit
     * {@code /}) und {@code Benutzer/} (nur ASCII, Groß- und Kleinschreibung egal, nur mit {@code /}). Startgrenze
     * erforderlich ({@link PathForm#isStartBoundary(String, int)}: die gemeinsame Grenze oder {@code :} davor),
     * Konfidenz 0.60 (fraglich), Quelle {@code pfad-relativ}. Kein Fund: {@code home/office} und
     * {@code Benutzer/Passwort} (ein Segment), {@code home/} und {@code Benutzer/}, {@code Home/erika/x}
     * (großgeschrieben), {@code homepage/a/b} (kein Kopf), {@code xhome/a/b} (keine Startgrenze) und
     * {@code /home/erika/x} (davor steht {@code /}; der Fund gehört {@link #UNIX}).
     *
     * <p>Ausnahme Gendern (nur Kopf {@code Benutzer/}): Ist das erste Segment dahinter genau {@code in} oder
     * {@code innen} (nur ASCII, Groß- und Kleinschreibung egal) und folgt direkt ein Trenner, ist es kein Kopf:
     * {@code Benutzer/in/x}, {@code Benutzer/Innen/y} und {@code Benutzer/innen/erika/a} ergeben keinen Fund,
     * {@code Benutzer/in-Konto/x} und {@code Benutzer/ina/x} dagegen schon. Steht hinter {@code in} oder {@code innen}
     * das Textende oder das Zitatende ({@code Benutzer/in}, {@code Benutzer/innen}), hat der Kopf höchstens ein
     * Segment und ist ohnehin kein Fund. Folgt ein anderes Zeichen als ein Trenner, gilt das erste Segment als
     * längeres Wort; dann entscheidet allein die Segmentzahl ({@code Benutzer/ina} und {@code Benutzer/in.pdf} sind
     * kein Fund, {@code Benutzer/ina/x} und {@code Benutzer/in.pdf/x} sind einer). Die Prüfung liest nur das eine
     * Zeichen hinter {@code in} oder {@code innen}
     * und nie über das Textende hinaus. Im Zitat-Pfad liest sie höchstens das Zeichen an der Stelle des Inhaltsendes,
     * also das schließende Anführungszeichen; das ist weder Buchstabe noch Trenner, sodass das Ergebnis dasselbe ist,
     * als stünde dort das Textende ({@code "Benutzer/in"/x} und {@code "Benutzer/innen"/a/b} haben im Zitat nur ein
     * Segment). Der Text hinter dem Zitat ändert das Ergebnis daher nie.
     *
     * <p>Laufzeit des Kopfs: Die Prüfung liest höchstens 15 Zeichen ab der Position ({@code Benutzer/} 9, {@code innen}
     * 5, ein Trenner), nichts davor und nie Segmente beliebiger Länge (O(1) je Position).
     *
     * <p>Modellierung: Die Mindestzahl der Segmente hängt von der Kopfvariante ab ({@code Users\}, {@code Users/},
     * {@code Benutzer\}: 1; {@code home/}, {@code Benutzer/}: 2). Statt die Schnittstelle um eine
     * Kopflängen-Abhängigkeit der Mindestzahl zu erweitern, gibt es zwei Formen: Die Kopfvarianten mit zwei Segmenten
     * liegen hier, die mit einem Segment in {@link #BENUTZER_WURZEL}. Jede Form behält damit eine konstante
     * Mindestzahl, der Kopf einer Form hat eine feste Konfidenz (konstant, also in der Segmentzahl monoton nicht
     * fallend), und die Obermengen-Garantie je Form (ein Durchlauf je Form) trennt beide Varianten von selbst.
     */
    WURZEL_ZWEI_SEGMENTE {
        @Override
        int headEnd(String text, int i) {
            if (text.startsWith("home/", i)) {
                return i + "home/".length();
            }
            int sep = i + "benutzer".length();
            if (PathChars.matchesAsciiIgnoreCase(text, i, "benutzer") && sep < text.length()
                    && text.charAt(sep) == '/') {
                return isGenderFirstSegment(text, sep + 1) ? -1 : sep + 1;
            }
            return -1;
        }

        @Override
        boolean isStartBoundary(String text, int i) {
            return startBoundaryOrColon(text, i);
        }

        @Override
        int minSegments() {
            return 2;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return CONFIDENCE_QUESTIONABLE;
        }

        @Override
        String source() {
            return "pfad-relativ";
        }
    },

    /**
     * Pfad hinter einer Umgebungsvariable (Plan 2.4.2 Zeile UMGEBUNG, AK-15, OF-6): Die Variable samt Pfad dahinter ist
     * EIN Fund ({@code %APPDATA%\BeispielTool\konfig.ini}); Ordner-, Benutzer- und Programmnamen hinter der Variable
     * sind damit mitgeschützt. Die Variable allein ({@code %APPDATA%}, {@code $HOME}, {@code ${HOME}}, {@code %PATH%})
     * ist kein Fund, ebenso nicht ein Dateiname direkt hinter der Variable ohne Trenner ({@code %~dp0tool.exe},
     * {@code %USERPROFILE%Desktop}): Wie bei jedem Pfad zählen nur Segmente hinter dem Kopf (AK-21). Es wird nichts
     * aufgelöst (kein {@code System.getenv}, kein Dateisystem); die Variable ist nur Text.
     *
     * <p>Kopfvarianten, jede bis einschließlich eines Trenners ({@code \} oder {@code /}) vor dem ersten Segment:
     * <ul>
     *   <li>{@code %NAME%} (Windows), NAME = {@code [A-Za-z_][A-Za-z0-9_()]{0,63}}; bis zu vier solche Gruppen direkt
     *       hintereinander ({@code %HOMEDRIVE%%HOMEPATH%\x}), danach der Trenner;</li>
     *   <li>{@code $NAME} (Unix, PowerShell: {@code $HOME/x}, {@code $PSScriptRoot\x}), NAME =
     *       {@code [A-Za-z_][A-Za-z0-9_]{0,63}}; {@code ${NAME}} und {@code $(NAME)} (Makefile und Shell,
     *       {@code $(pwd)/x}) mit demselben NAME;</li>
     *   <li>PowerShell {@code $env:NAME} (NAME wie bei {@code $NAME}) und {@code ${env:NAME}} (NAME wie bei
     *       {@code %NAME%}, also auch mit Klammern: {@code ${env:ProgramFiles(x86)}\x}); {@code env} in beliebiger
     *       Groß- und Kleinschreibung;</li>
     *   <li>verzögerte Batch-Expansion {@code !NAME!} ({@code !USERPROFILE!\Desktop\x}, {@code !APPDATA!/x}), NAME wie
     *       bei {@code %NAME%}; genau eine Gruppe (keine Kette), danach der Trenner;</li>
     *   <li>Batch-Parameter {@code %~dp0}, {@code %~dpn0}, {@code %~f1}, {@code %~1}: {@code %~}, höchstens acht
     *       Modifikatorbuchstaben aus {@code fdpnxsatz} (Groß- und Kleinschreibung egal), eine Ziffer; dahinter
     *       höchstens 64 Zeichen, die weder Trenner noch Endzeichen sind (der Rest des Batch-Ausdrucks, etwa
     *       {@code ..} in {@code %~dp0..\erika\a}), und ein Trenner. Der Rest vor dem Trenner gehört zum Kopf und
     *       wird nicht als Segment gezählt: In {@code %~dp0..\erika\a} endet der Kopf hinter {@code ..\}, gezählt
     *       werden {@code erika} und {@code a}; {@code %~dp0tool.exe} hat keinen Trenner und ist kein Fund.</li>
     * </ul>
     * Bekannte Grenzen: In diesen Fällen entsteht kein Fund, der ganze Ausdruck samt Pfad bleibt im Klartext (nicht nur
     * die Variable): Variablen mit Ersatzsyntax ({@code ${HOME:-/tmp}/x}, {@code %PATH:~0,5%\x}), Batch-Parameter ohne
     * Tilde ({@code %1\x}), Variablennamen mit mehr als 64 Zeichen oder mit Punkt oder Minus, Befehlsersetzung mit
     * Argument ({@code $(dirname $0)/x}), Befehlsersetzung mit Backticks und Variable in eigenen Anführungszeichen,
     * jeweils mit nur einem Segment dahinter ({@code `dirname $0`/x}, {@code "$HOME"/erika}), sowie ein Dateiname
     * direkt hinter der Variable ({@code %TEMP%erika.txt}). Nur ein Teil des Ausdrucks wird gefunden: bei einer Kette
     * aus mehr als vier {@code %NAME%}-Gruppen beginnt der Fund an der viertletzten Gruppe, die davor stehenden
     * Gruppen bleiben sichtbar; bei verschachtelter Ersatzsyntax ({@code ${X:-$HOME/.config}/app}) beginnt der Fund am
     * inneren {@code $HOME} und reicht bis zum Ende des Laufs, der Anfang mit der äußeren Variable bleibt sichtbar; bei
     * einer Variable in eigenen Anführungszeichen oder Backticks mit mindestens zwei Segmenten dahinter
     * ({@code "$HOME"/erika/akte.pdf}) fassen UNIX bzw. EINZEL_BACKSLASH den Pfad ohne die Variable, die Variable
     * bleibt sichtbar. Steht eine Variable in der Mitte eines Pfads ({@code C:\Users\%USERNAME%\x},
     * {@code \\srv\%USERNAME%\x}), deckt der Fund der anderen Form alles ab; der Fund bleibt dann wie bisher
     * (Konfidenz 0.95), weil ein vollständig enthaltener Fund die Konfidenz nicht ändert (Plan 7a (iii)).
     *
     * <p>Konfidenz konstant 0.80 (wahrscheinlich), also in der Segmentzahl nicht fallend (Vertrag, Plan 7a (i)); Quelle
     * {@code pfad-umgebung}; mindestens ein Segment hinter dem Kopf (konstant für alle Varianten).
     *
     * <p>Eigene Entscheidung (Plan-Abweichung, Grundsatz "möglichst viel schützen", Nutzerentscheidung 20.11.2026):
     * Für diese Form gilt keine Startgrenze ({@link #isStartBoundary(String, int)} ist immer wahr), anders als der
     * Plan für "alle Formen" sagt. Grund: Der Kopf ist unverwechselbar (Variable plus Trenner plus Segment); eine
     * Startgrenze würde nur Fälle verlieren, in denen Pfade tatsächlich vorkommen ({@code -I$HOME/inc},
     * {@code Pfad:%APPDATA%\x}, {@code a\n$HOME/x} in maskiertem Text, {@code --prefix$HOME/x}). Die Alltagsformen in
     * Geschäftstexten bleiben auch ohne Startgrenze kein Fund, weil ihnen der Kopf fehlt: {@code 50%/Jahr},
     * {@code 3%/4%}, {@code 5$/Stück}, {@code 100 %/Monat} (hinter {@code %} bzw. {@code $} steht kein Name),
     * {@code $1/2}, {@code $5/Monat} (Name beginnt mit einer Ziffer), {@code %d/%m/%Y}, {@code %s/%s} (kein
     * schließendes {@code %} direkt hinter dem Namen), {@code %20/}, {@code %2F}, {@code %E4%B8%AD} (URL-Kodierung:
     * hinter dem schließenden {@code %} steht immer eine Hexziffer, nie ein Trenner), {@code %1\%2}, {@code a!=b/c},
     * {@code Achtung!/x} (hinter {@code !} steht kein Name). Bekannte Fehltreffer (zu viel ersetzt, kein Leck,
     * Konfidenz 0.80): Vorlagen und Formeln mit Name, Trenner und Segment ({@code ${x}/${y}}, {@code $a/$b},
     * {@code $x/y$}, {@code $USD/MWh}), Escape-Folgen hinter einer Variable ({@code echo $x\n}), Serienbrief-
     * Platzhalter mit Schrägstrich ({@code %DATUM%/%ORT%}, {@code %VORNAME%/%NACHNAME%}; ersetzt wird nur der
     * Platzhalter) und ein von zwei Ausrufezeichen umschlossenes Wort vor einem Trenner ({@code !wichtig!/a},
     * {@code Ja!Nein!/x}).
     *
     * <p><b>Laufzeit des Kopfs (linear).</b> Jede Variante liest nur eine begrenzte Zahl von Zeichen ab der Position;
     * die Namenslänge ist auf 64 begrenzt (ein 65. Namenszeichen beendet die Prüfung), die Gruppenzahl bei
     * {@code %NAME%} auf vier, die Modifikatoren auf acht, der Rest hinter einem Batch-Parameter auf 64. Je Position
     * werden höchstens gelesen: an einer Position, die weder {@code %} noch {@code $} noch {@code !} ist, ein Zeichen;
     * bei {@code %NAME%} höchstens 4 mal 66 Zeichen (je Gruppe {@code %}, höchstens 64 Namenszeichen und ein weiteres
     * Zeichen) plus ein Zeichen für den Trenner, also höchstens 265; bei {@code $} höchstens 2 plus 4 plus 65 plus 2,
     * also höchstens 73; bei {@code %~} höchstens 2 plus 8 plus 1 plus 65, also höchstens 76; bei {@code !NAME!}
     * höchstens 1 plus 65 plus 1, also höchstens 67 (das {@code !}, die Namensprüfung samt dem Zeichen dahinter, der
     * Trenner). Der Erkenner ruft den Kopf je Position und Durchlauf höchstens einmal auf, im Zitat-Pfad je Position
     * und Art (13 Arten) einmal; auch wenn sich die Leseabschnitte benachbarter Köpfe überlappen ({@code %a%} mal n,
     * {@code %a%%b%} mal n, {@code %a} mal n, {@code $} mal n, {@code $} und geschweifte Klammer auf mal n,
     * {@code %~dp0} mal n, {@code !a!} mal n, {@code !} mal n), ist der Gesamtaufwand je Durchlauf höchstens 265 mal
     * die Zahl der Stellen mit {@code %}, {@code $} oder {@code !} plus die Textlänge, im Zitat-Pfad höchstens das
     * 13-fache davon, also linear in der Textlänge. Es gibt keine Schleife, die über eine dieser Schranken hinaus
     * liest, und keine Rekursion.
     *
     * <p>Alle Köpfe dieser Form enden mit einem Trenner und beginnen mit einem Nicht-Trenner ({@code %}, {@code $}
     * oder {@code !}). Köpfe können sich überlappen ({@code %a%%b%\x}, {@code %~dp0%~dp0\x}); überlappende Köpfe
     * haben dasselbe Kopfende, und höchstens 28 Köpfe teilen ein Kopfende (Schranke: 22 Batch-Köpfe plus 4 Köpfe der
     * Kette plus 1 Kopf mit {@code $} plus 1 Kopf mit {@code !}, die Summe der Einzelschranken, ohne dass sie zugleich
     * erreicht werden müssen; die Herleitung steht im Klassen-Javadoc von {@code PathRecognizer}). Zwischen dem Ende
     * eines Kopfes und dem Beginn eines Kopfes mit anderem Ende liegt ein Segmentanfang. Das ist die Voraussetzung
     * der Nachzählung im Erkenner (Beweis im Klassen-Javadoc von {@code PathRecognizer}). Beispiel dafür, warum ein
     * Batch-Kopf nicht schon hinter der Ziffer endet: {@code %~dp0} mal n gefolgt von {@code \)} hätte sonst n Köpfe
     * ohne Segmentanfang dazwischen und n Nachzählungen, die je den ganzen Lauf lesen (quadratisch); mit dem Trenner
     * im Kopf sind es in diesem Beispiel nur 13 (die Köpfe, deren Rest vor dem Trenner höchstens 64 Zeichen lang
     * ist).
     */
    UMGEBUNG {
        @Override
        int headEnd(String text, int i) {
            if (i >= text.length()) {
                return -1;
            }
            char c = text.charAt(i);
            if (c == '%') {
                return i + 1 < text.length() && text.charAt(i + 1) == '~' ? batchHeadEnd(text, i)
                        : percentHeadEnd(text, i);
            }
            if (c == '!') {
                return delayedHeadEnd(text, i);
            }
            return c == '$' ? dollarHeadEnd(text, i) : -1;
        }

        @Override
        boolean isStartBoundary(String text, int i) {
            return true;
        }

        @Override
        int minSegments() {
            return 1;
        }

        @Override
        double confidence(int headLength, int segmentsCappedAt3) {
            return CONFIDENCE_PROBABLE;
        }

        @Override
        String source() {
            return "pfad-umgebung";
        }
    };

    /**
     * Höchstlänge eines Benutzernamens ({@code ~name/}), eines Host-Laufs vor {@code :/}, eines Variablennamens und
     * des Rests hinter einem Batch-Parameter ({@code %~dp0}).
     */
    private static final int MAX_NAME_LENGTH = 64;

    /** Höchstzahl der {@code %NAME%}-Gruppen direkt hintereinander ({@code %HOMEDRIVE%%HOMEPATH%}). */
    private static final int MAX_PERCENT_GROUPS = 4;

    /** Höchstzahl der Modifikatorbuchstaben eines Batch-Parameters ({@code %~dpnxsatz0}). */
    private static final int MAX_BATCH_MODIFIERS = 8;

    /** Präfix der PowerShell-Umgebungsvariable hinter {@code $} bzw. {@code ${} ({@code env} und Doppelpunkt). */
    private static final String ENV_PREFIX = "env:";

    /**
     * Kopf {@code !NAME!} (verzögerte Batch-Expansion, genau eine Gruppe, keine Kette) und Trenner ab {@code i}
     * ({@code text[i]} ist {@code !}): Ende hinter dem Trenner oder -1. NAME wie bei {@code %NAME%}. Liest höchstens
     * 67 Zeichen (das {@code !}, höchstens 65 Zeichen des Namens samt dem Zeichen dahinter, der Trenner).
     */
    private static int delayedHeadEnd(String text, int i) {
        int nameEnd = nameEnd(text, i + 1, true);
        if (nameEnd < 0 || nameEnd >= text.length() || text.charAt(nameEnd) != '!') {
            return -1;
        }
        return separatorEnd(text, nameEnd + 1);
    }

    /**
     * Kopf {@code %NAME%} (eine bis {@value #MAX_PERCENT_GROUPS} Gruppen) und Trenner ab {@code i} ({@code text[i]}
     * ist {@code %}, dahinter keine Tilde): Ende hinter dem Trenner oder -1. Liest höchstens 265 Zeichen.
     */
    private static int percentHeadEnd(String text, int i) {
        int n = text.length();
        int pos = i;
        int groups = 0;
        while (groups < MAX_PERCENT_GROUPS && pos < n && text.charAt(pos) == '%') {
            int nameEnd = nameEnd(text, pos + 1, true);
            if (nameEnd < 0 || nameEnd >= n || text.charAt(nameEnd) != '%') {
                break;
            }
            pos = nameEnd + 1;
            groups++;
        }
        return groups > 0 ? separatorEnd(text, pos) : -1;
    }

    /**
     * Kopf mit {@code $} ab {@code i}: {@code $NAME}, {@code ${NAME}}, {@code $(NAME)}, {@code $env:NAME},
     * {@code ${env:NAME}}, jeweils mit Trenner; Ende hinter dem Trenner oder -1. Liest höchstens 73 Zeichen.
     */
    private static int dollarHeadEnd(String text, int i) {
        int from = i + 1;
        if (from >= text.length()) {
            return -1;
        }
        char c = text.charAt(from);
        int end;
        if (c == '{') {
            boolean env = isEnvPrefix(text, from + 1);
            end = closedNameEnd(text, env ? from + 1 + ENV_PREFIX.length() : from + 1, env, '}');
        } else if (c == '(') {
            end = closedNameEnd(text, from + 1, false, ')');
        } else {
            end = nameEnd(text, isEnvPrefix(text, from) ? from + ENV_PREFIX.length() : from, false);
        }
        return end < 0 ? -1 : separatorEnd(text, end);
    }

    /**
     * Batch-Parameter {@code %~}, Modifikatoren, Ziffer, höchstens {@value #MAX_NAME_LENGTH} Zeichen und ein Trenner
     * ab {@code i} ({@code text[i]} ist {@code %}, {@code text[i+1]} die Tilde): Ende hinter dem Trenner oder -1. Die
     * Zeichen vor dem Trenner dürfen kein Endzeichen sein (der Kopf bleibt im Lauf). {@link #MAX_NAME_LENGTH}
     * begrenzt hier die Länge des Rests hinter der Ziffer, nicht die eines Namens. Liest höchstens 76 Zeichen.
     */
    private static int batchHeadEnd(String text, int i) {
        int n = text.length();
        int j = i + 2;
        while (j < n && j - (i + 2) < MAX_BATCH_MODIFIERS && isBatchModifier(text.charAt(j))) {
            j++;
        }
        if (j >= n || text.charAt(j) < '0' || text.charAt(j) > '9') {
            return -1;
        }
        j++;
        for (int k = j; k < n && k - j <= MAX_NAME_LENGTH; k++) {
            char c = text.charAt(k);
            if (PathChars.isSeparator(c)) {
                return k + 1;
            }
            if (PathChars.isTerminator(c)) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Ende eines Variablennamens ab {@code from} ({@code [A-Za-z_]} gefolgt von höchstens 63 Zeichen aus
     * {@code [A-Za-z0-9_]}, mit {@code parens} auch {@code (} und {@code )}) oder -1 (kein Name oder mehr als 64
     * Zeichen). Liest höchstens 65 Zeichen.
     */
    private static int nameEnd(String text, int from, boolean parens) {
        int n = text.length();
        if (from >= n) {
            return -1;
        }
        char first = text.charAt(from);
        if (!PathChars.isAsciiLetter(first) && first != '_') {
            return -1;
        }
        int j = from + 1;
        while (j < n && isNameChar(text.charAt(j), parens)) {
            j++;
            if (j - from > MAX_NAME_LENGTH) {
                return -1;
            }
        }
        return j;
    }

    private static boolean isNameChar(char c, boolean parens) {
        return PathChars.isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_' || (parens && (c == '(' || c == ')'));
    }

    /** Ende hinter dem Namen und dem schließenden Zeichen {@code close} (ab {@code nameStart}) oder -1. */
    private static int closedNameEnd(String text, int nameStart, boolean parens, char close) {
        int end = nameEnd(text, nameStart, parens);
        return end >= 0 && end < text.length() && text.charAt(end) == close ? end + 1 : -1;
    }

    /** Ob ab {@code i} {@link #ENV_PREFIX} steht (ASCII, Groß- und Kleinschreibung bei {@code env} egal). */
    private static boolean isEnvPrefix(String text, int i) {
        int colon = i + ENV_PREFIX.length() - 1;
        return colon < text.length() && PathChars.matchesAsciiIgnoreCase(text, i, "env") && text.charAt(colon) == ':';
    }

    /** Modifikatorbuchstabe eines Batch-Parameters: {@code f d p n x s a t z}, Groß- und Kleinschreibung egal. */
    private static boolean isBatchModifier(char c) {
        return PathChars.isAsciiLetter(c) && "fdpnxsatz".indexOf(c | 0x20) >= 0;
    }

    /** Ende hinter dem Trenner an {@code pos} oder -1, wenn dort kein Trenner steht. */
    private static int separatorEnd(String text, int pos) {
        return pos < text.length() && PathChars.isSeparator(text.charAt(pos)) ? pos + 1 : -1;
    }

    /** Konfidenz "sicher" (Stufe SICHER). */
    private static final double CONFIDENCE_SURE = 0.95;

    /** Konfidenz "sicher" für Unix ab drei Segmenten, Home ({@code ~/}) und Host ab zwei Segmenten (Plan N-3). */
    private static final double CONFIDENCE_UNIX_SURE = 0.90;

    /** Konfidenz "wahrscheinlich": relativ, Unix mit zwei Segmenten, {@code ~name/} und Host mit einem Segment. */
    private static final double CONFIDENCE_PROBABLE = 0.80;

    /** Konfidenz 0.75 (wahrscheinlich): einzelner führender Backslash (Plan Nachtrag T-12 Punkt 8). */
    private static final double CONFIDENCE_SINGLE_BACKSLASH = 0.75;

    /** Konfidenz "fraglich" (Stufe FRAGLICH), z. B. UNC-Kopf mit nur einem Segment. */
    private static final double CONFIDENCE_QUESTIONABLE = 0.60;

    /**
     * Ende des Kopfs (exklusiv) oder -1. Prüft nur Zeichen ab {@code i} und liest nie über das Textende hinaus; UNC,
     * EINZEL_BACKSLASH und HOST_PFAD lesen zusätzlich das Zeichen davor (UNC und EINZEL_BACKSLASH, um nur am ersten
     * Backslash eines Laufs zu prüfen; HOST_PFAD, um Wortanfang und Wortmitte zu unterscheiden), HOST_PFAD sogar bis zu
     * 64 Zeichen davor. Die Startgrenze prüft der Aufrufer ({@link #isStartBoundary(String, int)}).
     */
    abstract int headEnd(String text, int i);

    /**
     * Ob an Position {@code i} ein Kopf dieser Form beginnen darf. Standard ist die gemeinsame Startgrenze
     * ({@link PathChars#isStartBoundary}); LAUFWERK, UNC, HOST_PFAD und UMGEBUNG haben keine (immer wahr), RELATIV,
     * BENUTZER_WURZEL und WURZEL_ZWEI_SEGMENTE lassen zusätzlich {@code :} davor zu. Im Zitat-Pfad wird die Methode
     * nicht für die erste Inhaltsposition gefragt (dort ist die Grenze durch das öffnende Zeichen erfüllt).
     */
    boolean isStartBoundary(String text, int i) {
        return PathChars.isStartBoundary(text, i);
    }

    /**
     * Ob an {@code from} (direkt hinter {@code Benutzer/}) das erste Segment genau {@code in} oder {@code innen} ist
     * (ASCII, Groß- und Kleinschreibung egal) und direkt dahinter ein Trenner steht (Gendern mit Schrägstrich). Liest
     * höchstens sechs Zeichen ab {@code from} und nie über das Textende hinaus.
     */
    private static boolean isGenderFirstSegment(String text, int from) {
        int end;
        if (PathChars.matchesAsciiIgnoreCase(text, from, "innen")) {
            end = from + "innen".length();
        } else if (PathChars.matchesAsciiIgnoreCase(text, from, "in")) {
            end = from + "in".length();
        } else {
            return false;
        }
        return end < text.length() && PathChars.isSeparator(text.charAt(end));
    }

    /** Gemeinsame Startgrenze oder {@code :} direkt davor (RELATIV, BENUTZER_WURZEL, WURZEL_ZWEI_SEGMENTE). */
    private static boolean startBoundaryOrColon(String text, int i) {
        return (i > 0 && text.charAt(i - 1) == ':') || PathChars.isStartBoundary(text, i);
    }

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
}
