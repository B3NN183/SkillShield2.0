package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt Tokens, API-Keys und Client-Secrets nach einem Kontextwort (Feature geheimnisse-erkennung, Anforderung D-5
 * bis D-9, D-11, D-13; Plan Kapitel 1). Der Erkenner liest den Text von Hand (kein regulaerer Ausdruck, keine
 * Netzwerk- oder Dateizugriffe) und liefert nur den <b>Wert</b>: Kontextwort, Verbinder, Anfuehrungszeichen und der
 * Rest der Zeile bleiben unveraendert (D-3). Typ {@code TOKEN}, Quelle {@value #QUELLE_KONTEXT}.
 *
 * <p>Stand T-7: Kontextwoerter, Schema-Formen und die Verbinder K1 bis K4 (Quelle {@code token-kontext}), dazu die
 * kontextfreien Formen JWT, PEM-Koerper und Praefix-Token (D-10, D-11; Quellen {@code token-jwt},
 * {@code token-pem}, {@code token-praefix}; Regeln im Javadoc von {@code TokenFormen}); die Kommandozeile
 * ({@code --token}, {@code --api-key}) folgt in T-8. Der Erkenner nutzt dieselben zwei Durchgaenge wie der
 * {@link PasswortRecognizer}: erster Durchgang K1 und Zeilenumbruch ({@code KontextRegel}), zweiter Durchgang nur K2
 * bis K4 ({@code SchwacheVerbinder}); der zweite meldet nur, was keinen Kandidaten des ersten beruehrt.
 *
 * <p><b>Vereinigung mit den Formen.</b> Die Kandidaten der Kontextregel bleiben feldgleich; die Formen kommen
 * additiv hinzu. Deckt ein Kandidat einer Form genau denselben Bereich wie ein Kandidat der Kontextregel
 * ({@code Authorization: Bearer <JWT>}, {@code Token: <Praefix-Token>}), ersetzt der Formen-Kandidat ihn: gleicher
 * Typ, gleicher Bereich, gleiche Wirkung (die Position ist in beiden Faellen geschuetzt, die Konfidenz ist nie
 * kleiner), die Quelle ist die der Form. Begruendung: Die Form ist die staerkere, vom Kontext unabhaengige Aussage
 * (der Kontext kann ein anderes Wort sein, die Form bleibt), und die Quelle {@code token-jwt} hinter Bearer ist so
 * eindeutig und nicht vom letzten Gleichstandskriterium des {@code ConflictResolver} abhaengig. Liegt ein
 * Formen-Kandidat ganz in einem Kandidaten der Kontextregel, entfaellt er; ragt er darueber hinaus oder umfasst er
 * einen, bleibt der Kontext-Kandidat unveraendert und der Formen-Kandidat wird auf die Teile ausserhalb zugeschnitten.
 * Die Kandidaten sind weiter ueberlappungsfrei und aufsteigend.
 *
 * <p><b>Typwechsel gegenueber T-6 (T-7, Durchlauf 3).</b> Im Scanner ersetzt ein TOKEN-Fund 0,95 einen PASSWORT-Fund
 * 0,80 gleichen Bereichs ({@code Passwort ist <Praefix-Token>}, {@code Passwort ist <JWT>}); ein PASSWORT-Fund ganz in
 * einem PEM-Fund zwischen den Markierungen geht in diesem auf. Keine Position wird frei (Vergleichstest
 * {@code TokenFormenVergleichT6Test}).
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
 * <li>G-16: Hersteller-Tokens ausserhalb der kurzen Praefix-Liste (D-11, {@code TokenFormen}) werden ohne Kontextwort
 * nicht erkannt. Test {@code bekannteGrenze_g16_praefixAusserhalbDerListe_keinFund_mitKontextwortEinFund}.</li>
 * <li>G-17 (PEM): Der Koerper ist ein einziger mehrzeiliger Fund. Test
 * {@code bekannteGrenze_g17_pemKoerperIstEinMehrzeiligerFund_rueckuebersetzungZeichengleich}.</li>
 * <li>G-18 (PEM): Liegt ein Fund eines bestehenden Erkenners ganz im Koerper (eine Zeile, die mit einem
 * Schraegstrich beginnt, ist ein Unix-Pfad), zerfaellt der Fund in Stuecke um ihn; kein Zeichen bleibt frei. Test
 * {@code bekannteGrenze_g18_pemKoerperMitSchraegstrichZeile_zerfaelltInStuecke_keinZeichenFrei}.</li>
 * <li>Formen (T-7, Tests in {@code TokenGrenzenTest}; die vollstaendige Liste steht im Javadoc von
 * {@code TokenFormen}): PEM zwischen BEGIN- und END-Markierung ist auch mit Text davor, in einer Zeile und mit
 * maskierten Zeilenumbruechen (JSON) ein Fund; keiner ist es ohne END-Markierung, wenn Text vor der BEGIN-Markierung
 * steht, bei mehr als 16 KiB Abstand und ohne 16 Base64-Zeichen in Folge (Platzhalter); ohne END-Zeile gehoert eine
 * folgende Wortzeile aus Base64-Zeichen dazu; ein JWT mit vier Teilen, mit Auslassungszeichen, als JWE mit fuenf
 * Teilen oder umgebrochen ist kein Fund; ein Praefix-Token unter 20 Zeichen oder ohne Ziffer bleibt sichtbar; bei den
 * klassischen GitHub-Praefixen ist ein Bezeichner mit {@code _} oder {@code -} im Rest kein Token, bei
 * {@code glpat-}, {@code xoxb-}, {@code xoxp-} und {@code github_pat_} dagegen ein Fund (Fehlalarm). Ein
 * {@code Passwort ist <JWT>} ergibt statt PASSWORT 0,80 jetzt TOKEN 0,95 desselben Bereichs.</li>
 * <li>PEM unter der 70-Prozent-Schwelle (security, mittel; vom Nutzer bei der Abnahme von T-7 am 28.11.2026
 * angenommen, nicht behoben): Ein verschluesselter privater Schluessel mit Kopfzeilen ({@code Proc-Type},
 * {@code DEK-Info}) zwischen den Markierungen, dessen Zeilen durch Markup getrennt sind ({@code &#13;&#10;},
 * {@code <br />} mit Zitat, {@code " + Environment.NewLine}), ist kein Fund; am Zeilenanfang und mit {@code \n} ist
 * er einer. Test
 * {@code bekannteGrenze_pemZwischenMarkierungenMitKopfzeilenUndMarkupTrennern_unterSchwelle_keinFund}. Ein Satz mit
 * einem Pfad zwischen Markierungen ist ein Fund (Ueberschutz, niedrig), Test
 * {@code bekannteGrenze_langesWortZwischenDenMarkierungen_nurAlleinEinFund}.</li>
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
 * Zeichen in {@code <token>} bleibt lesbar (ein langes JWT dort schuetzt seit T-7 die Form); ein Wert mit 4 096
 * Zeichen, auf den ein Anfuehrungszeichen und zwei schliessende geschweifte Klammern folgen, ist kein Fund (das Wort
 * ist zu lang).
 *
 * <p>Der Erkenner ist zustandslos und laeuft linear (Javadoc von {@code KontextRegel} und {@code TokenKontext}). Die
 * Kandidaten sind ueberlappungsfrei und aufsteigend. Eine {@code NullPointerException} nennt nur {@code text}; es gibt
 * keine Meldung mit Text, Wert oder Position.
 */
public final class TokenRecognizer implements Recognizer {

    /** Quelle der Funde nach Kontextwort (Konfidenz 0,95 oder 0,80). */
    public static final String QUELLE_KONTEXT = "token-kontext";

    /** Quelle der Funde der Form JWT (Konfidenz 0,95). */
    public static final String QUELLE_JWT = "token-jwt";

    /** Quelle der Funde der Form PEM-Koerper eines privaten Schluessels (Konfidenz 0,95). */
    public static final String QUELLE_PEM = "token-pem";

    /** Quelle der Funde der Form Praefix-Token (Konfidenz 0,95). */
    public static final String QUELLE_PRAEFIX = "token-praefix";

    /**
     * Liefert die Kandidaten fuer Tokens nach einem Kontextwort und in den kontextfreien Formen.
     *
     * @param text der Text, nicht {@code null}
     * @return unveraenderliche, aufsteigend sortierte, ueberlappungsfreie Liste
     * @throws NullPointerException bei {@code null} (Meldung {@code text})
     */
    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> kontext = ZweiDurchgaenge.finde(text, Art.TOKEN);
        List<Finding> formen = TokenFormen.finde(text);
        if (formen.isEmpty()) {
            return kontext;
        }
        return vereinige(text, kontext, formen);
    }

    /**
     * Vereinigt zwei aufsteigende, je ueberlappungsfreie Listen: Kontext-Kandidaten bleiben feldgleich, ausser einer
     * mit genau dem Bereich eines Formen-Kandidaten (er wird durch diesen ersetzt); ein Formen-Kandidat wird auf die
     * Teile ausserhalb aller Kontext-Kandidaten zugeschnitten (ohne Kuerzung). Ein Durchlauf ueber beide Listen.
     */
    private static List<Finding> vereinige(String text, List<Finding> kontext, List<Finding> formen) {
        boolean[] ersetzt = new boolean[kontext.size()];
        List<Finding> neu = new ArrayList<>(formen.size());
        int k = 0;
        for (Finding form : formen) {
            while (k < kontext.size() && kontext.get(k).end() <= form.start()) {
                k++;
            }
            int ab = form.start();
            int j = k;
            boolean gleich = false;
            while (j < kontext.size() && kontext.get(j).start() < form.end()) {
                Finding kf = kontext.get(j);
                if (kf.start() == form.start() && kf.end() == form.end()) {
                    ersetzt[j] = true;
                    gleich = true;
                    break;
                }
                if (kf.start() > ab) {
                    neu.add(stueck(text, form, ab, kf.start()));
                }
                ab = Math.max(ab, kf.end());
                j++;
            }
            if (gleich) {
                neu.add(form);
            } else if (ab < form.end()) {
                neu.add(stueck(text, form, ab, form.end()));
            }
        }
        List<Finding> ergebnis = new ArrayList<>(kontext.size() + neu.size());
        for (int i = 0; i < kontext.size(); i++) {
            if (!ersetzt[i]) {
                ergebnis.add(kontext.get(i));
            }
        }
        ergebnis.addAll(neu);
        ergebnis.sort(Comparator.comparingInt(Finding::start));
        return List.copyOf(ergebnis);
    }

    private static Finding stueck(String text, Finding form, int von, int bis) {
        if (von == form.start() && bis == form.end()) {
            return form;
        }
        return new Finding(form.type(), text.substring(von, bis), von, bis, form.confidence(), form.source());
    }
}
