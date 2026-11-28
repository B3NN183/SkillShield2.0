package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;
import java.util.Objects;

/**
 * Eingefrorene Kopie des {@code TokenRecognizer} im Stand {@code ge-t6-token} (Plan geheimnisse-erkennung, T-7): ruft
 * die woertlichen Kopien {@link ZweiDurchgaengeStandT6}, {@link KontextRegelStandT6}, {@link SchwacheVerbinderStandT6}
 * und {@link TokenKontextStandT6} auf (nur die Klassennamen sind geaendert). Sie ist oeffentlich, damit Tests
 * ausserhalb des Pakets den Stand T-6 als Massstab nutzen koennen. Nicht aendern. Der folgende Text ist der
 * unveraenderte Javadoc des Stands T-6.
 *
 * <p>Erkennt Tokens, API-Keys und Client-Secrets nach einem Kontextwort (Feature geheimnisse-erkennung, Anforderung D-5
 * bis D-9, D-11, D-13; Plan Kapitel 1). Der Erkenner liest den Text von Hand (kein regulaerer Ausdruck, keine
 * Netzwerk- oder Dateizugriffe) und liefert nur den <b>Wert</b>: Kontextwort, Verbinder, Anfuehrungszeichen und der
 * Rest der Zeile bleiben unveraendert (D-3). Typ {@code TOKEN}, Quelle {@value #QUELLE_KONTEXT}.
 *
 * <p>Stand T-6: Kontextwoerter, Schema-Formen und die Verbinder K1 bis K4. JWT, PEM-Koerper und Praefix-Token ohne
 * Kontext (D-11, Quellen {@code token-jwt}, {@code token-pem}, {@code token-praefix}) folgen in T-7, die Kommandozeile
 * ({@code --token}, {@code --api-key}) in T-8. Der Erkenner nutzt dieselben zwei Durchgaenge wie der
 * {@link PasswortRecognizer}: erster Durchgang K1 und Zeilenumbruch ({@code KontextRegel}), zweiter Durchgang nur K2
 * bis K4 ({@code SchwacheVerbinder}); der zweite meldet nur, was keinen Kandidaten des ersten beruehrt.
 *
 * <p><b>Kontextwoerter (D-5).</b> Ein Wortkoerper (Folge aus Buchstaben, Ziffern, {@code _}, {@code -}; Gross/klein
 * gleichgueltig) zaehlt, wenn er, ohne {@code _} und {@code -} verglichen (hoechstens drei uebersprungen), auf
 * {@code token}, {@code apikey}, {@code secret}, {@code secretkey}, {@code accesskey} oder {@code privatekey} endet
 * ({@code access_token}, {@code accessToken}, {@code refresh_token}, {@code idToken}, {@code API-Key},
 * {@code api_key}, {@code X-API-Key}, {@code client_secret}, {@code clientSecret}); dazu die Schreibweise
 * {@code API Key} mit Leerzeichen und die ganzen Woerter {@code Bearer} und {@code Authorization}. Das blosse Wort
 * {@code Secret} ist ebenfalls TOKEN. Kein Kontextwort sind {@code Tokenlaenge}, {@code Token-Ablauf},
 * {@code Secretary}, Plural- und Genitivformen.
 *
 * <p><b>Verbinder, Zwischenteil, Zeilenumbruch, Wert (D-6, D-7).</b> Wie beim Passwort (Javadoc von
 * {@link PasswortRecognizer}): optional ein Zwischenteil ({@code fuer}, {@code von}, {@code of} ...), dann K1
 * ({@code :} oder {@code =}) mit optionalem Zeilenumbruch, oder K2 ({@code ist}, {@code lautet}, {@code is}), K3
 * (Tabulator, {@code |}), K4 ({@code <token>Wert</token>}); der Wert ist das erste Wort oder der Inhalt von
 * Anfuehrungszeichen, Satzzeichen am Ende werden abgetrennt. {@code Bearer} und {@code Authorization} gelten nur mit
 * K1 (und {@code Bearer} ohne Verbinder, siehe unten), nie mit K2 bis K4.
 *
 * <p><b>Schema-Formen (D-11).</b> {@code Authorization: Bearer Wert}, {@code Authorization: Basic Wert} und
 * {@code Authorization: Token Wert}: der Fund ist nur der Wert, das Schema-Wort bleibt. {@code Bearer Wert} ohne
 * Doppelpunkt hat die Konfidenz 0,80 (Plan Kapitel 11 Punkt 5). Fuer {@code Bearer} und {@code Authorization} prueft
 * der Wert-Test W1 bis W5 und eine Laenge von mindestens 16 Zeichen, ohne die Klassenregel von W6. Beide Kontextwoerter
 * ergeben denselben Bereich und damit genau einen Fund.
 *
 * <p><b>Wert-Test und Konfidenz (D-8, D-9).</b> Alle anderen Kontextwoerter pruefen wie beim Passwort W1 bis W5 (K1)
 * bzw. zusaetzlich W6 (K2 bis K4, Zeilenumbruch). Eine UUID ist ohne Kontextwort kein Fund (D-10), hinter
 * {@code apikey:} ist sie einer. K1 gleiche Zeile mit mindestens 8 Zeichen 0,95, mit 6 oder 7 Zeichen 0,80, alle
 * schwaecheren Verbinder und der Zeilenumbruch 0,80; {@code Authorization} und {@code Bearer} mit K1 0,95. Es gibt
 * keine Stufe fraglich.
 *
 * <p><b>Konflikte und Zusammenspiel (D-13).</b> Der Erkenner liefert Kandidaten; der {@code TextScanner} ordnet sie
 * nach der Lueckenregel ein und aendert nie einen Fund der uebrigen Erkenner. Ein Wert in E-Mail-, URL- oder Pfadform
 * liegt im Fund des bestehenden Erkenners. Steht ein Kontextwort fuer Passwort und eines fuer Token vor demselben
 * Wert ({@code Passwort fuer token: Wert}), liefern beide Erkenner denselben Bereich; der {@code ConflictResolver}
 * waehlt dann einen Typ, die Position bleibt geschuetzt.
 *
 * <p><b>Bekannte Grenzen</b> (Tests {@code bekannteGrenze...} in {@code TokenGrenzenTest} und
 * {@code TokenSchemaStrengeTest}):
 * <ul>
 * <li>Grenze 1 (security, mittel; vom Nutzer bei der Abnahme von T-6 am 27.11.2026 angenommen): Ein Schema-Wert
 * ({@code Bearer}, {@code Basic}) mit genau einem {@code =} am Ende ist kein Fund, wenn direkt nach dem
 * schliessenden Anfuehrungszeichen ein Zeichen folgt, das die Kuerzung am Ende nicht entfernt ({@code ! * | <},
 * {@code **}); {@code istParameterAnfuehrung} wertet {@code ="} dort als Parameterform. Gemessen 2 bis 4,5 Prozent
 * gedeckt. Test {@code bekannteGrenze_schemaWertMitEinemPaddingZeichenUndFolgezeichenNachAnfuehrung_keinFund}
 * ({@code TokenSchemaStrengeTest}). Behebung (nicht umgesetzt): Parameterform nur, wenn nach {@code ="} ein
 * Namenszeichen folgt.</li>
 * <li>Grenze 2a (niedrig): Bei kompaktem CSV-JSON (Padding, zwei Anfuehrungszeichen, schliessende geschweifte
 * Klammer, Anfuehrungszeichen) reicht der Fund ueber das Wertende hinaus (Ueberschutz, kein Leck). Test
 * {@code bekannteGrenze_kompaktesCsvJsonMitPadding_fundReichtUeberDasWertendeHinaus}.</li>
 * <li>Grenze 2b (niedrig): Eine maskierte Parameterform ({@code Bearer error=\"invalid_token\"}) ist ein Fund. Test
 * {@code bekannteGrenze_bearerMitMaskiertemParameter_istFund}.</li>
 * <li>Grenze 2c (niedrig): Ein unmittelbar an einen Schema-Wert geklebtes zweites Kontextwort mit Verbinder wird
 * nicht ausgewertet (ein Fund bis zum Textende). Test
 * {@code bekannteGrenze_angeklebtesZweitesKontextwortHinterSchemaWert_nichtAusgewertet}.</li>
 * <li>G-16: Hersteller-Tokens ohne Kontextwort werden hier nicht erkannt; nur mit Kontextwort ({@code Praefix}
 * folgt in T-7 fuer eine kurze Liste).</li>
 * <li>G-1, G-2, G-7 bis G-10, G-13, G-18 bis G-20 gelten wie beim Passwort ({@link PasswortRecognizer}): Werte ohne
 * Kontextwort, nur aus Buchstaben oder Ziffern, mit Satzzeichen am Ende, mit {@code $ % { < [} am Anfang, mit
 * Leerzeichen ausserhalb von Anfuehrungszeichen und Hashes bleiben, wie dort beschrieben.</li>
 * <li>G-14 gilt fuer K2 bis K4 ({@code Token ist Q3Final2} ist ein Fund).</li>
 * <li>Hex-Tokens und UUIDs hinter K2 bis K4 und nach einem Zeilenumbruch bleiben lesbar (Entscheidung des Nutzers
 * nach T-6, Durchlauf 2; Test {@code bekannteGrenze_hexUndUuidHinterK2BisK4UndUmbruch_keinFund}).</li>
 * <li>{@code Bearer} ohne Verbinder (0,80): Ein Wort mit weniger als 16 Zeichen oder nur aus Buchstaben ist kein
 * Wert; ein Token mit weniger als 16 Zeichen hinter {@code Bearer} oder {@code Authorization} bleibt sichtbar.</li>
 * <li>{@code Basic Wert} und {@code Token Wert} ohne {@code Authorization:} davor sind keine Schema-Formen.</li>
 * <li>Der Wert hinter {@code Authorization} oder {@code Bearer} steht in derselben Zeile; ein Zeilenumbruch dazwischen
 * ergibt keinen Fund. {@code Bearer} und {@code Authorization} gelten nie mit K2 bis K4.</li>
 * <li>Steht das Schema in Anfuehrungszeichen ({@code "Authorization": "Bearer Wert"}), bleibt das Schema-Wort
 * Klartext und der Wert ist der Fund, in jeder Laenge bis 4 096 Zeichen (Entscheidung 3 nach Pruefung T-6).</li>
 * <li>Ein Wert mit weniger als 16 Zeichen nach {@code Authorization: Basic} oder {@code Authorization: Token} bleibt
 * sichtbar, ebenso ein Schluessel in der Kommandozeile ({@code --token}; T-8).</li>
 * </ul>
 *
 * <p><b>W6 hinter schwaecheren Verbindern (Entscheidung des Nutzers nach T-6, Durchlauf 2).</b> Hinter K2 bis K4 und
 * nach einem Zeilenumbruch gilt fuer TOKEN wie fuer PASSWORT W6 (Konfidenz 0,80). Hex-Tokens und UUIDs haben nur zwei
 * Zeichenklassen und bleiben dort lesbar, ebenso ein Bezeichner aus Kleinbuchstaben und Ziffern; das ist eine
 * Grenze mit Test ({@code TokenSchemaStrengeTest}), gewollt zugunsten weniger Fehlalarme. Hinter K1 und den
 * Schema-Formen sind sie Funde. Base64 mit drei Klassen ist ueberall ein Fund.
 *
 * <p><b>Weitere Grenzen mit Test</b> ({@code TokenSchemaStrengeTest}): Hinter {@code Bearer} und
 * {@code Authorization: <Schema>} ist ein Wert der Form {@code name="..."} oder mit runden Klammern kein Token
 * (Entscheidung 2); Base64-Padding vor einem schliessenden, verdoppelten oder maskierten Anfuehrungszeichen
 * ({@code ==""}, {@code =\"}) gehoert zum Wert; ein Base64-Token mit {@code /} am Anfang bleibt sichtbar (W4);
 * {@code Authorization: Negotiate}, {@code NTLM} und {@code Digest} sind keine Schema-Woerter; umfasst ein TOKEN-Fund
 * einen PASSWORT-Fund ({@code Passwort fuer token<TAB>X=Y}), waehlt der Scanner den Typ TOKEN, die Positionen bleiben
 * geschuetzt; Code mit Funktionsaufruf hinter {@code token =}, Referenzwerte (Tresor-Pfad, Cloud-Ressourcenkennung)
 * und IT-Begriffe mit Ziffer hinter {@code Token:} sind Funde (Fehlalarme, Schutz geht vor); ein Wert ueber 256
 * Zeichen in {@code <token>} (lange JWT) bleibt lesbar; ein Wert mit 4 096 Zeichen, auf den ein Anfuehrungszeichen und
 * zwei schliessende geschweifte Klammern folgen, ist kein Fund (das Wort ist zu lang).
 *
 * <p>Der Erkenner ist zustandslos und laeuft linear (Javadoc von {@code KontextRegel} und {@code TokenKontext}). Die
 * Kandidaten sind ueberlappungsfrei und aufsteigend. Eine {@code NullPointerException} nennt nur {@code text}; es gibt
 * keine Meldung mit Text, Wert oder Position.
 */
public final class TokenRecognizerStandT6 implements Recognizer {

    /** Quelle der Funde nach Kontextwort (Konfidenz 0,95 oder 0,80). */
    public static final String QUELLE_KONTEXT = "token-kontext";

    /**
     * Liefert die Kandidaten fuer Tokens nach einem Kontextwort.
     *
     * @param text der Text, nicht {@code null}
     * @return unveraenderliche, aufsteigend sortierte, ueberlappungsfreie Liste
     * @throws NullPointerException bei {@code null} (Meldung {@code text})
     */
    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        return ZweiDurchgaengeStandT6.finde(text, Art.TOKEN);
    }
}
