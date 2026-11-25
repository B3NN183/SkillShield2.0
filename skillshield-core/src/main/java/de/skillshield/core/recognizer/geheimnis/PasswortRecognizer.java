package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt Passwoerter nach einem Kontextwort (Feature geheimnisse-erkennung, Anforderung D-4 bis D-9, D-13; Plan
 * Kapitel 1). Der Erkenner liest den Text von Hand (kein regulaerer Ausdruck, keine Netzwerk- oder Dateizugriffe) und
 * liefert nur den <b>Wert</b>: Kontextwort, Verbinder, Anfuehrungszeichen, Benutzername und der Rest der Zeile
 * bleiben unveraendert (D-3). Typ {@code PASSWORT}, Quelle {@value #QUELLE_KONTEXT}.
 *
 * <p>Stand T-5: Kontextwoerter, Zwischenteil, Verbinder K1 ({@code :} oder {@code =}) auch mit Zeilenumbruch,
 * Wertgrenzen und Wert-Test, dazu die schwaecheren Verbinder K2 ({@code ist}, {@code lautet}, {@code is}), K3
 * (senkrechter Strich, Tabulator) und K4 (XML-Element) mit Konfidenz 0,80. Die Verbindungszeichenfolgen (Quelle
 * {@code passwort-verbindung}) und die Kommandozeile folgen in spaeteren Tasks.
 *
 * <p><b>Zwei Durchgaenge.</b> Der erste Durchgang ({@code KontextRegel}: K1 und Zeilenumbruch) ist der Stand von T-4
 * und bleibt unveraendert; seine Kandidaten werden nie veraendert oder entfernt. Der zweite Durchgang
 * ({@code SchwacheVerbinder}) sucht nur K2 bis K4 und meldet einen Kandidaten nur, wenn er keinen Kandidaten des
 * ersten Durchgangs ueberlappt und kein Kontextwort mit K1-Verbinder ueberdeckt. Steht hinter dem Kontextwort (auch
 * im Zwischenteil nach {@code ist}) ein K1-Verbinder, gilt der erste Durchgang ({@code Passwort fuer svc_x ist jetzt:
 * Wert}). {@code pw} und {@code pwd} gelten nur mit K1 (D-4). Nach K2 bis K4 steht der Wert in derselben Zeile; ein
 * Zeilenumbruch danach ergibt keinen Fund.
 *
 * <p><b>Kontextwoerter (D-4).</b> Ein Wortkoerper (Folge aus Buchstaben, Ziffern, {@code _}, {@code -}; Gross/klein
 * gleichgueltig) zaehlt, wenn er auf {@code passwort}, {@code kennwort}, {@code password}, {@code passwd} oder
 * {@code passphrase} endet ({@code Initialpasswort}, {@code DB_PASSWORD}, {@code dbPassword}), nicht aber
 * {@code Passwortrichtlinie}, {@code Passwort-Reset}, {@code Passwoerter}. {@code pw} und {@code pwd} zaehlen nur als
 * ganzer Wortkoerper. Ausdruecklich keine Kontextwoerter: {@code pass}, {@code pin}, {@code code}, Schluessel,
 * Login, Kennung, Zugangsdaten, Credentials, Plural- und Genitivformen.
 *
 * <p><b>Verbinder, Zwischenteil, Zeilenumbruch (D-6).</b> Optional ein Zwischenteil ({@code fuer}, {@code for},
 * {@code von}, {@code vom}, {@code des}, {@code der}, {@code zu}, {@code zum}, {@code zur}, {@code of}, {@code to}
 * und ein bis drei Woerter), dann K1; danach darf ein Zeilenumbruch samt Einrueckung folgen, eine Leerzeile oder ein
 * Textende ergibt keinen Fund. Unicode-Leerraum (U+00A0, U+2009) gilt wie ein Leerzeichen.
 *
 * <p><b>Wert (D-7).</b> Der Wert beginnt hinter Verbinder und Leerraum und ist das erste Wort (hoechstens 4 096
 * Zeichen), oder bei oeffnendem Anfuehrungszeichen mit schliessendem Partner in derselben Zeile (hoechstens 256
 * Zeichen) der Inhalt; ein maskiertes ({@code \"}) oder verdoppeltes ({@code ""}) gerades Anfuehrungszeichen gehoert
 * zum Inhalt (JSON, YAML, SQL). Satzzeichen am Ende werden wiederholt abgetrennt; das {@code !} am Ende gehoert zum
 * Wert. Mehrere Werte in einer Zeile sind je ein Fund.
 *
 * <p><b>Auszeichnung (Entscheidung 6 vom 25.11.2026).</b> Zwischen Kontextwort, Verbinder und Wert werden die
 * Auszeichnungszeichen {@code *}, {@code _} und der Gravis (ein bis drei in Folge) uebersprungen
 * ({@code **Passwort:** Wert}, {@code **Kennwort**: Wert}, {@code __Passwort__: Wert}); beginnt der Wert mit einer
 * Folge von zwei oder drei gleichen Zeichen, die in derselben Zeile noch einmal steht ({@code **Wert**}), wird sie
 * uebersprungen, und der Fund endet vor der schliessenden Folge. Der Wert ist auch dann nur das erste Wort
 * ({@code **siehe Tresor**} ergibt keinen Fund).
 *
 * <p><b>Wert-Test (D-8).</b> W1 bis W6 stehen im Javadoc von {@code Werttest}; Pfad- und Adressformen (W4) gehoeren
 * den bestehenden Erkennern.
 *
 * <p><b>Konfidenz (D-9).</b> K1 in derselben Zeile mit mindestens 8 Zeichen 0,95 (sicher), mit 6 oder 7 Zeichen 0,80
 * (wahrscheinlich); K1 mit Zeilenumbruch 0,80. Es gibt keine Stufe fraglich: was den Wert-Test nicht besteht, ist
 * kein Fund.
 *
 * <p><b>Konflikte (D-13).</b> Der Erkenner liefert Kandidaten; der {@code TextScanner} ordnet sie nach der
 * Lueckenregel ein und aendert nie einen Fund der uebrigen Erkenner. Ein Wert in E-Mail-Form liegt im E-Mail-Fund,
 * ein Passwort in einer URL oder einem Pfad im URL- bzw. Pfad-Fund (kein zusaetzlicher PASSWORT-Fund). Reicht der
 * URL-Fund nicht ueber das ganze Passwort (der URL-Erkenner schneidet {@code . , ; : ! ? ' "} und unausgeglichene
 * {@code )} {@code ]} am Ende ab), steht der Rest als PASSWORT-Reststueck daneben (Nachtrag des Plans zu AK-28).
 *
 * <p><b>Kontextfreie Formen und Grenze zu Feature 3 (D-10 bis D-12).</b> Dieser Erkenner liefert nur Werte nach einem
 * Kontextwort. Hashes, Pruefsummen, UUIDs und lange Zufallsfolgen ohne Kontextwort sind kein Fund (D-10); JWT, PEM
 * und Praefix-Token (D-11) gehoeren dem spaeteren Token-Erkenner. Bei Verbindungszeichenfolgen und URLs (D-12) wird
 * nur das Geheimnis geschuetzt: Host, Port und Dienstname gehoeren zu Feature 3, der Benutzername zu Feature 4. Eine
 * URL mit Zugangsdaten ersetzt der bestehende URL-Fund als Ganzes (Fall 4, G-4); die ODBC-Regel, die Oracle-Form und
 * die Kommandozeile (Faelle 1 bis 3, Quelle {@code passwort-verbindung}) folgen in T-8, bis dahin wertet die
 * Kontextregel solche Schluessel mit ({@code Pwd=...} liest das erste Wort bis zum Leerraum).
 *
 * <p><b>Bekannte Grenzen</b> (je ein Test {@code bekannteGrenze...} in {@code PasswortGrenzenTest}):
 * <ul>
 * <li>G-1: Passwoerter ohne Kontextwort, Wert vor dem Kontextwort ({@code Sommer2026! (Passwort)}),
 * {@code Zugangsdaten: svc_x / Sommer2026!} und Kontext ueber mehrere Zeilen oder Tabellenkoepfe.</li>
 * <li>G-2: Passwoerter nur aus Buchstaben oder Ziffern und solche, deren einziges Sonderzeichen am Ende steht.</li>
 * <li>G-7: Ein Passwort, das auf {@code . , ; : ?} oder eine unausgeglichene Klammer endet, bleibt dort sichtbar.</li>
 * <li>G-8: Kontextwoerter ausserhalb der Liste (Plural, Genitiv, Tippfehler, unsichtbare Zeichen im Wort,
 * {@code Passwort aendern: ...}).</li>
 * <li>G-9: Passwoerter, die mit {@code $ % { < [} beginnen, bleiben (Variablen und Vorlagen gehen vor).</li>
 * <li>G-10: Hashes und Pruefsummen bleiben; Hashverfahren sind keine Geheimnisse.</li>
 * <li>G-13: Werte mit Leerzeichen werden nur in Anfuehrungszeichen erfasst, sonst nur das erste Wort.</li>
 * <li>G-18: Ein bestehender Fund, der ganz im Wert liegt, bleibt unveraendert; das Geheimnis zerfaellt in Stuecke
 * um ihn.</li>
 * <li>G-19: Ein Kontextwort innerhalb eines verworfenen Werts ohne Leerraum wird nicht mehr ausgewertet
 * ({@code Passwort:${PW},Kennwort:Sommer2026!} ergibt keinen Fund).</li>
 * <li>G-20: Platzhalterartige Werte mit Sonderzeichen oder Ziffern ({@code !vault |}, {@code @@DBPASS@@},
 * {@code REPLACE_ME_123}) bleiben Funde (Schutz geht vor; der Nutzer kann abwaehlen).</li>
 * <li>Auszeichnung: Eine einzelne {@code *} oder {@code _} um den Wert wird nicht uebersprungen und bleibt Teil
 * des Funds ({@code *Sommer2026!*}); der Wert ist so vollstaendig geschuetzt. Kursiver Text wie
 * {@code Passwort: *siehe Tresor*} ist deshalb ein Fund. Der Versuch, ein einzelnes Zeichen mit Gegenstueck in der
 * Zeile zu uebergehen (Entscheidung 2 vom 25.11.2026), brach den Positiv-Generator und wurde zurueckgenommen.</li>
 * <li>Zeitstempel: Ein Passwort in der Form eines ISO-Zeitstempels bleibt sichtbar (D-8 W3 (e)).</li>
 * <li>G-14: Ein Wort mit Grossbuchstabe, Kleinbuchstabe und Ziffer hinter {@code ist}, {@code lautet}, {@code is}
 * (K2), einem Tabulator, {@code |} oder in einem Element ({@code Passwort ist Q3Final2}) gilt als Passwort (W6 ist
 * die engste vertretbare Regel). Das gilt ausdruecklich auch hinter K2: {@code Passwort ist Exchange2019} und
 * {@code Passwort lautet Win11-24H2} sind Funde.</li>
 * <li>K2, Fachbegriffe: Ein Wort, das auf Bindestrich und ein reines Kleinbuchstabenwort endet
 * ({@code AES256-verschluesselt}), ist hinter {@code ist}, {@code lautet}, {@code is} kein Fund; ein Passwort dieser
 * Form ({@code Sommer2026-neu}) bleibt dort sichtbar. In Anfuehrungszeichen und hinter K1, K3, K4 gilt der
 * Ausschluss nicht.</li>
 * <li>K2 bis K4 erfassen nur die genannten Formen. Nicht erfasst und sichtbar wie in T-4 bleiben: ein Zusatz zwischen
 * Kontextwort und Verbinder ({@code Passwort (neu): ...}, {@code Passwort neu: ...}), mehr als drei Woerter nach
 * {@code fuer}, die Verbinder {@code =>}, {@code :=}, ein {@code :} in voller Breite, {@code ist:}, {@code lautet:},
 * {@code is:}, ein Verbinder ohne Kontextwort davor ({@code ist} direkt hinter einem Einzelwort), ein Strich oder
 * Pfeil ({@code ->}) und Attributpaare ({@code key="...Password" value="..."}), ein Kontextwort mit Auszeichnung
 * oder Anfuehrungszeichen vor K2 bis K4 ({@code **Passwort** ist ...}) und ein Wert nach K2 bis K4 mit Leerraum
 * ausserhalb von Anfuehrungszeichen (nur das erste Wort zaehlt, im Element gar nichts).</li>
 * <li>Ein Wert hinter K2 bis K4, der ein Kontextwort mit K1 enthaelt ({@code Passwort ist x-Passwort: Wert}),
 * bleibt vorn lesbar wie in T-4; der Wert hinter dem inneren K1 wird vom ersten Durchgang gefunden.</li>
 * <li>S1: Hinter {@code Passwort fuer svc_x ist Wert}, {@code | Passwort fuer svc_x | Wert |} und
 * {@code Passwort fuer svc_x<TAB>Wert} gilt ein {@code :} oder {@code =} im Wert als K1 des Zwischenteils (auch
 * {@code Passwort fuer svc_x<TAB>Wert<TAB>Benutzer: abc}); der erste Durchgang wertet dann nur den Rest dahinter. Der
 * Wert bleibt ganz (Rest zu kurz) oder vorn lesbar, genau wie in T-4; die Regel aendert daran nichts.</li>
 * <li>K3 mit {@code |}, Wert unmittelbar dahinter ({@code |Passwort|Wert|}): Das Wort reicht bis zum Leerraum. Der
 * Schlussstrich gehoert zum Fund ({@code Wert|}), ebenso alles bis zum Wortende
 * ({@code |Passwort|Wert|https://...|}); der Wert-Test prueft nur den Teil bis zum naechsten {@code |}, fuehrende
 * {@code |} uebersprungen. Kopfzeilen und Statusspalten ohne Leerraum ({@code |Passwort|Benutzer|Rolle|},
 * {@code |Passwort|nein|12|}, {@code ||Passwort||Benutzer||}, {@code |Passwort||gesperrt|}) sind deshalb kein Fund,
 * wenn der erste Teil den Wert-Test nicht besteht; ein Wort nur aus {@code |} ist kein Fund. Steht Leerraum hinter
 * dem {@code |} ({@code | Passwort | Wert |}), gilt das ganze Wort; der Wert bleibt dort auch mit {@code |} darin
 * ganz gedeckt ({@code | Passwort | Ab3|Wolke#Fuchs7x |}), und kompakte Zellen mit Leerraum
 * ({@code |K| S| S|}) sind Fehlalarme, die hingenommen werden.</li>
 * <li>Grenzen von K3 ohne Leerraum: Ein Wert, der selbst ein {@code |} enthaelt und dessen erster Teil den
 * Wert-Test nicht besteht ({@code |Passwort|Ab3|Wolke#Fuchs7x|}, auch {@code |Passwort||Ab3|Wolke#Fuchs7x|}),
 * bleibt lesbar; besteht der erste Teil, ist der Wert ganz gedeckt. Nicht erfasst: {@code |Passwort fuer svc_x|Wert|}
 * (der Strich steht im Wort des Zwischenteils, kein Verbinder) und {@code || Passwort || Wert ||} (das Wort hinter
 * dem Kontextwort besteht nur aus {@code |}). Restfehlalarme kompakter Zellen:
 * {@code |K|*S*|S|}, {@code |K|S<br>S|S|}, wenn der erste Teil den Wert-Test besteht.</li>
 * <li>Ein Element mit Attribut ({@code <password type="x">Wert</password>}) ist kein Fund: Das Kontextwort steht
 * unmittelbar zwischen {@code <} und {@code >}.</li>
 * </ul>
 * Die uebrigen Grenzen (G-3 bis G-6, G-11, G-12, G-14 bis G-17) betreffen Folgetasks oder die Oberflaeche und sind im
 * Plan beschrieben.
 *
 * <p>Der Erkenner ist zustandslos und laeuft linear (Javadoc von {@code KontextRegel}). Die Kandidaten sind
 * ueberlappungsfrei und aufsteigend. Eine {@code NullPointerException} nennt nur {@code text}; es gibt keine Meldung
 * mit Text, Wert oder Position.
 */
public final class PasswortRecognizer implements Recognizer {

    /** Quelle der Funde nach Kontextwort (Konfidenz 0,95 oder 0,80). */
    public static final String QUELLE_KONTEXT = "passwort-kontext";

    /**
     * Liefert die Kandidaten fuer Passwoerter nach einem Kontextwort.
     *
     * @param text der Text, nicht {@code null}
     * @return unveraenderliche, aufsteigend sortierte, ueberlappungsfreie Liste
     * @throws NullPointerException bei {@code null} (Meldung {@code text})
     */
    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> erster = new ArrayList<>();
        KontextRegel.finde(text, Art.PASSWORT, erster);
        List<Finding> zweiter = new ArrayList<>();
        SchwacheVerbinder.finde(text, Art.PASSWORT, erster, zweiter);
        if (zweiter.isEmpty()) {
            return List.copyOf(erster);
        }
        List<Finding> kandidaten = new ArrayList<>(erster.size() + zweiter.size());
        int a = 0;
        int b = 0;
        while (a < erster.size() || b < zweiter.size()) {
            boolean ersterZuerst = b >= zweiter.size()
                    || (a < erster.size() && erster.get(a).start() < zweiter.get(b).start());
            kandidaten.add(ersterZuerst ? erster.get(a++) : zweiter.get(b++));
        }
        return List.copyOf(kandidaten);
    }
}
