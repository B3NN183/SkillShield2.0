package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BEGRIFF_SONDER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BEZEICHNER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BEZEICHNER_EXCHANGE;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BEZEICHNER_WIN;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BINDESTRICH_GEHEIM;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BINDESTRICH_NEU;
import static de.skillshield.core.SynthetischeGeheimnisse.P_FACHBEGRIFF_VERSCHLUESSELT;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_DOPPELPUNKT_KURZ;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_DOPPELPUNKT_VORN;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_STRICH;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_STRICH_KURZ;
import static de.skillshield.core.SynthetischeGeheimnisse.P_PLATZHALTERARTIG;
import static de.skillshield.core.SynthetischeGeheimnisse.P_PROGRAMM;
import static de.skillshield.core.SynthetischeGeheimnisse.P_ZQ9;
import static de.skillshield.core.SynthetischeGeheimnisse.hex64;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bekannte Grenzen der Passwort-Erkennung (Anforderung G-1 bis G-20, AK-52; Plan Kapitel 12): je Grenze ein Test
 * {@code bekannteGrenze...}, der festhaelt, was der Erkenner absichtlich nicht oder anders tut. Stand T-5: G-1, G-2,
 * G-7, G-8, G-9, G-10, G-12, G-13, G-14, G-18, G-19, G-20 sowie die Grenzen der Auszeichnung (Entscheidung 6), des
 * ISO-Zeitstempels (D-8 W3 (e)), der Fachbegriffe hinter K2, der entfallenen Verbinderformen und die zwei offenen
 * Punkte aus T-4 (Rueckstrich vor dem Begrenzer, leerer Wert in kompaktem JSON). Die uebrigen Grenzen gehoeren zu
 * spaeteren Tasks (Tokens, Verbindungszeichenfolgen) oder zur Oberflaeche.
 *
 * <p>Meldungen nennen nur Fall-Nummern und Zahlen, nie einen Text oder Wert (Plan Kapitel 6.1).
 */
class PasswortGrenzenTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String UE = String.valueOf((char) 0xFC);
    private static final String AE = String.valueOf((char) 0xE4);

    private static List<Finding> passwoerter(String text) {
        List<Finding> liste = new ArrayList<>();
        for (Finding f : STANDARD.scan(text)) {
            if (f.type().equals(GeheimnisTypen.PASSWORT)) {
                liste.add(f);
            }
        }
        return liste;
    }

    private static void keinFund(String id, String text) {
        assertEquals(List.of(), passwoerter(text), id);
    }

    /** Genau ein Fund ueber {@code wert} an der Stelle hinter {@code vor}. */
    private static void fundUeber(String id, String vor, String wert, String nach) {
        List<Finding> funde = passwoerter(vor + wert + nach);
        assertEquals(1, funde.size(), id + " Zahl");
        assertEquals(vor.length(), funde.get(0).start(), id + " Beginn");
        assertEquals(vor.length() + wert.length(), funde.get(0).end(), id + " Ende");
    }

    // ------------------------------------------------------------------ G-1

    @Test
    void bekannteGrenze_g1_passwoerterOhneKontextwortWertVorKontextUndMehrzeilenKontext() {
        keinFund("G-1 ohne Kontext", "Anmeldung mit svc_x und " + P1);
        keinFund("G-1 Wert vor dem Kontextwort", P1 + " (Passwort)");
        keinFund("G-1 Wert vor dem Kontextwort, Doppelpunkt", P1 + " ist das Passwort: ");
        keinFund("G-1 Zugangsdaten", "Zugangsdaten: svc_x / " + P1);
        keinFund("G-1 Kontext ueber eine Leerzeile", "Passwort\n\n" + P1);
        keinFund("G-1 Tabellenkopf", "Benutzer | Passwort\nsvc_x | " + P1);
        keinFund("G-1 Kontext in der Zeile davor", "Passwort\n" + P1);
    }

    // ------------------------------------------------------------------ G-2

    @Test
    void bekannteGrenze_g2_passwoerterNurAusBuchstabenOderZiffernUndMitSonderzeichenNurAmEnde() {
        keinFund("G-2 nur Buchstaben", "Passwort: Sonnenschein");
        keinFund("G-2 Passphrase aus Buchstaben", "Passwort: WolkeFuchsKaffee");
        keinFund("G-2 nur Ziffern", "Passwort: 123456");
        keinFund("G-2 Sonderzeichen nur am Ende", "Passwort: Sommer!");
        keinFund("G-2 Bindestriche zaehlen nicht", "Passwort: Wolke-Fuchs-Kaffee");
    }

    // ------------------------------------------------------------------ G-7

    @Test
    void bekannteGrenze_g7_einPasswortDasAufSatzzeichenEndetBleibtDortSichtbar() {
        for (String ende : List.of(".", ",", ";", ":", "?", ".)")) {
            fundUeber("G-7 " + ende.length() + ende.charAt(0), "Passwort: ", P2, ende);
        }
        String text = "Passwort: " + P2 + ".";
        assertTrue(P2.equals(passwoerter(text).get(0).originalValue()), "G-7 Wert ohne den Punkt");
        // eine unausgeglichene Klammer am Ende des Passworts bleibt ebenfalls sichtbar
        fundUeber("G-7 Klammer", "Passwort: ", P2, ")");
    }

    // ------------------------------------------------------------------ G-8

    @Test
    void bekannteGrenze_g8_kontextwoerterAusserhalbDerListe() {
        List<String> woerter = List.of("Passw" + (char) 0xF6 + "rter", "Kennwortes", "Passwoerter", "Pasword",
                "Passwot", "Zugangsdaten", "Login", "PIN", "Anmeldename", "Schl" + UE + "ssel");
        for (int i = 0; i < woerter.size(); i++) {
            keinFund("G-8 Wort " + i, woerter.get(i) + ": " + P1);
        }
        keinFund("G-8 Passwort aendern", "Passwort " + AE + "ndern: " + P1);
        keinFund("G-8 Passwort zuruecksetzen", "Passwort zur" + UE + "cksetzen: " + P1);
        keinFund("G-8 unsichtbares Zeichen im Wort", "Pass" + (char) 0x200B + "wort: " + P1);
        keinFund("G-8 unsichtbares Zeichen davor", "Passwort" + (char) 0x2060 + ": " + P1);
        keinFund("G-8 kombinierendes Zeichen dahinter", "Passwort" + (char) 0x0301 + ": " + P1);
    }

    // ------------------------------------------------------------------ G-9

    @Test
    void bekannteGrenze_g9_passwoerterMitDollarProzentKlammerOderKleinerBeginnBleiben() {
        for (String anfang : List.of("$", "%", "{", "<", "[")) {
            keinFund("G-9 " + anfang, "Passwort: " + anfang + P1);
        }
        keinFund("G-9 Platzhalter", "Passwort: [[PASSWORT_1]]");
    }

    // ------------------------------------------------------------------ G-10

    @Test
    void bekannteGrenze_g10_hashesUndPruefsummenBleiben() {
        keinFund("G-10 Passwort-Hash", "Passwort-Hash: " + hex64());
        keinFund("G-10 Hashverfahren", "Passwort: SHA-256");
        keinFund("G-10 sha256 ohne Kontext", "sha256: " + hex64());
        keinFund("G-10 bcrypt-Form", "Passwort: $2b$" + "12" + "$" + hex64().substring(0, 40));
        keinFund("G-10 Hashverfahren Passphrase", "Passphrase: PBKDF2");
    }

    // ------------------------------------------------------------------ G-13

    @Test
    void bekannteGrenze_g13_wertMitLeerzeichenOhneAnfuehrungNurDasErsteWort() {
        List<Finding> funde = passwoerter("Passwort: " + P1 + " Fuchs7");
        assertEquals(1, funde.size());
        assertTrue(P1.equals(funde.get(0).originalValue()), "G-13 nur das erste Wort");
        keinFund("G-13 erstes Wort ohne Ziffer", "Passwort: Wolke Fuchs 7x!");
        // in Anfuehrungszeichen wird der ganze Inhalt erfasst
        assertEquals(1, passwoerter("Passwort: \"Wolke Fuchs 7x!\"").size());
    }

    @Test
    void bekannteGrenze_g13_auchInAuszeichnungGiltNurDasErsteWort() {
        keinFund("G-13 fett mit Leerzeichen", "Passwort: **Wolke Fuchs 7x!**");
    }

    // ------------------------------------------------------------------ G-18

    @Test
    void bekannteGrenze_g18_einBestandsfundImWertBleibt_dasGeheimnisZerfaelltInStuecke() {
        String email = "erika.beispiel" + "@" + "kv-beispiel.example";
        String text = "Passwort: " + P_ZQ9 + (char) 0xA7 + email + (char) 0x3E + "y";
        List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        assertEquals(1, alt.size(), "G-18 ein Bestandsfund heute");
        assertEquals(FindingType.EMAIL, alt.get(0).type());
        assertTrue(neu.contains(alt.get(0)), "G-18 Bestandsfund unveraendert");
        List<Finding> teile = passwoerter(text);
        assertTrue(teile.size() >= 2, "G-18 mindestens zwei Stuecke: " + teile.size());
        // die Stuecke und der E-Mail-Fund decken den ganzen Wert, kein Zeichen davon ist frei
        boolean[] abgedeckt = new boolean[text.length()];
        for (Finding f : neu) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        for (int i = "Passwort: ".length(); i < text.length(); i++) {
            assertTrue(abgedeckt[i], "G-18 Position frei " + i);
        }
    }

    // ------------------------------------------------------------------ G-19

    @Test
    void bekannteGrenze_g19_keinKontextwortInnerhalbEinesVerworfenenWertsOhneLeerraum() {
        keinFund("G-19 Variable davor", "Passwort:${PW},Kennwort:" + P1);
        // mit Leerraum dazwischen wird das zweite Kontextwort ausgewertet
        assertEquals(1, passwoerter("Passwort:${PW}, Kennwort: " + P1).size(), "G-19 mit Leerraum");
    }

    // ------------------------------------------------------------------ G-20

    @Test
    void bekannteGrenze_g20_platzhalterartigeWerteMitSonderzeichenOderZiffernBleibenFunde() {
        List<String> werte = P_PLATZHALTERARTIG;
        for (int i = 0; i < werte.size(); i++) {
            List<Finding> funde = passwoerter("password: " + werte.get(i));
            assertEquals(1, funde.size(), "G-20 Fall " + i);
            assertTrue(werte.get(i).equals(funde.get(0).originalValue()), "G-20 ganzer Wert, Fall " + i);
        }
        assertEquals(1, passwoerter("password: !vault |").size(), "G-20 Datei-Tag mit senkrechtem Strich");
    }

    // ------------------------------------------------------------------ Auszeichnung, Zeitstempel

    @Test
    void bekannteGrenze_auszeichnung_einzelneSterneOderUnterstricheBleibenTeilDesFunds() {
        List<Finding> stern = passwoerter("Passwort: *" + P1 + "*");
        assertEquals(1, stern.size());
        assertTrue(("*" + P1 + "*").equals(stern.get(0).originalValue()), "Sterne gehoeren zum Fund");
        List<Finding> unter = passwoerter("Passwort: _" + P2 + "_");
        assertEquals(1, unter.size());
        assertTrue(("_" + P2 + "_").equals(unter.get(0).originalValue()), "Unterstriche gehoeren zum Fund");
        // ein einzelner Stern vor einem Wort ohne Ziffer ist (wegen des Sterns als Sonderzeichen) ein Fund
        assertEquals(1, passwoerter("Passwort: *siehe").size(), "Stern als Sonderzeichen");
        // Entscheidung 2 (25.11.2026): kursiver Text mit einzelnen Sternen bleibt ein Fund, weil der Versuch, ein
        // einzelnes Zeichen mit Gegenstueck zu uebergehen, den Positiv-Generator bricht (siehe KontextRegel)
        List<Finding> kursiv = passwoerter("Passwort: *siehe Tresor*");
        assertEquals(1, kursiv.size(), "einzelne Sterne um Fachtext");
        assertTrue("*siehe".equals(kursiv.get(0).originalValue()), "nur das erste Wort mit dem Stern");
    }

    @Test
    void bekannteGrenze_auszeichnung_folgeOhneGegenstueckBleibtTeilDesWerts() {
        List<Finding> funde = passwoerter("Passwort: **" + P1);
        assertEquals(1, funde.size());
        assertTrue(("**" + P1).equals(funde.get(0).originalValue()), "Folge ohne Gegenstueck gehoert zum Fund");
    }

    @Test
    void bekannteGrenze_isoZeitstempelAlsWert_einPasswortInDieserFormBleibtSichtbar() {
        keinFund("Zeitstempel", "Passwort: 2026-03-12T10:15:00Z");
        keinFund("Zeitstempel mit Versatz", "Passwort: 2026-03-12T10:15:00+01:00");
    }

    // ------------------------------------------------------------------ Anfuehrungszeichen im Wert (Entscheidung 1)

    @Test
    void bekannteGrenze_anfuehrung_rueckstricheImWertKoennenMitEinemPfadFundZerfallen() {
        // Ein Wert mit mehreren Rueckstrich-Maskierungen kann wie ein relativer Pfad aussehen; der bestehende
        // PFAD-Fund bleibt unveraendert (G-18), der Wert zerfaellt in Stuecke, kein Zeichen bleibt frei.
        String vor = "password: '";
        String inhalt = "ahFjA\\'\\'b3#\\'YV";
        String text = vor + inhalt + "'";
        List<Finding> funde = STANDARD.scan(text);
        assertTrue(funde.size() >= 2, "Zahl der Funde " + funde.size());
        boolean pfad = false;
        boolean[] abgedeckt = new boolean[text.length()];
        for (Finding f : funde) {
            pfad |= f.type().equals(FindingType.PFAD);
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        assertTrue(pfad, "ein PFAD-Fund daneben");
        for (int i = vor.length(); i < vor.length() + inhalt.length(); i++) {
            assertTrue(abgedeckt[i], "Position frei " + (i - vor.length()));
        }
    }

    // ------------------------------------------------------------------ T-5: K2 bis K4

    private static final PasswortRecognizer NEU = new PasswortRecognizer();
    private static final PasswortRecognizerStandT4 T4 = new PasswortRecognizerStandT4();
    private static final String FF1A = String.valueOf((char) 0xFF1A);
    private static final String FUER = "f" + UE + "r";

    /** Kein Fund, und der Stand von T-4 liefert dasselbe (die Form war schon dort ungeschuetzt). */
    private static void ungeschuetztWieT4(String id, String text) {
        assertEquals(List.of(), NEU.detect(text), id);
        assertEquals(T4.detect(text), NEU.detect(text), id + " gleich T-4");
    }

    @Test
    void bekannteGrenze_g14_bezeichnerMitGrossKleinUndZiffernHinterK2BisK4IstEinFund() {
        String bezeichner = P_BEZEICHNER;
        fundUeber("G-14 K2 ist", "Passwort ist ", bezeichner, "");
        fundUeber("G-14 K2 lautet", "Das Passwort lautet ", bezeichner, ".");
        fundUeber("G-14 K3 Tabulator", "Passwort\t", bezeichner, "");
        fundUeber("G-14 K3 Strich", "| Passwort | ", bezeichner, " |");
        fundUeber("G-14 K4", "<password>", bezeichner, "</password>");
    }

    @Test
    void bekannteGrenze_g14_auchHinterK2_produktUndBetriebssystemnamenSindFunde() {
        // Ausdruecklich hinter K2 (Passwort ist ...): Namen, wie sie in Betriebsdokumentation stehen, sind Funde.
        fundUeber("G-14 K2 Produktname", "Passwort ist ", P_BEZEICHNER_EXCHANGE, "");
        fundUeber("G-14 K2 Betriebssystem", "Passwort lautet ", P_BEZEICHNER_WIN, " (Build)");
        fundUeber("G-14 K2 is", "password is ", P_BEZEICHNER_WIN, "");
    }

    @Test
    void bekannteGrenze_k2_wertMitBindestrichUndKleinwortIstKeinFund_einPasswortDieserFormBleibtSichtbar() {
        keinFund("K2 Bindestrich und Kleinwort", "Passwort ist " + P_BINDESTRICH_NEU);
        keinFund("K2 lautet", "Kennwort lautet " + P_BINDESTRICH_GEHEIM);
        // Nur K2: hinter K1 gilt der Ausschluss nicht.
        fundUeber("K1 Bindestrich und Kleinwort", "Passwort: ", P_BINDESTRICH_NEU, "");
    }

    @Test
    void bekannteGrenze_k3k4_fachbegriffMitBindestrichUndBegriffeMitZiffer_sindFunde() {
        // Der Ausschluss "Bindestrich und Kleinwort" gilt nur hinter K2; in Tabellenzellen und Elementen sind
        // solche Begriffe Funde (Ueberschutz, wie G-14).
        String begriff = P_FACHBEGRIFF_VERSCHLUESSELT;
        fundUeber("K3 Strich", "| Passwort | ", begriff, " |");
        fundUeber("K3 Tabulator", "Passwort\t", begriff, "");
        fundUeber("K4", "<password>", begriff, "</password>");
        fundUeber("K3 Programmname mit Ziffer", "| Passwort | ", P_PROGRAMM, " |");
        fundUeber("K3 Begriff mit Sonderzeichen", "| Passwort | ", P_BEGRIFF_SONDER, " |");
    }

    @Test
    void bekannteGrenze_k2bisK4_wertMitInnerenKontextwortUndK1_vordererTeilBleibtLesbarWieInT4() {
        String vorn = "Ab3!xPasswort:";
        fundUeber("K2", "Passwort ist " + vorn, P1, "");
        fundUeber("K3 Strich", "| Passwort | " + vorn, P1, " |");
        // Beim Element reicht der Fund des ersten Durchgangs wie in T-4 bis zum Leerraum, also ueber das Endetag.
        String element = "<password>" + vorn + P1 + "</password>";
        assertEquals(T4.detect(element), NEU.detect(element), "K4 gleich T-4");
        assertEquals(element.indexOf(P1), NEU.detect(element).get(0).start(), "K4 Beginn");
    }

    @Test
    void bekannteGrenze_k2bisK4_dasKontextwortAlsWertVerbrauchtDenKontext() {
        // Wie G-19: Der Wert wird ausgewertet und uebersprungen; ein Kontextwort darin gilt nicht mehr als Kontext.
        keinFund("K2 Kontextwort als Wert", "Passwort ist Passwort ist " + P1);
        keinFund("K3 Kontextwort als Wert", "| Passwort | Passwort | " + P1 + " |");
    }

    @Test
    void bekannteGrenze_k2bisK4_entfalleneFormen_bleibenSichtbarWieInT4() {
        ungeschuetztWieT4("Klammerzusatz", "Passwort (neu): " + P1);
        ungeschuetztWieT4("Zusatzwort", "Passwort neu: " + P1);
        ungeschuetztWieT4("Pfeil =>", "Passwort => " + P1);
        ungeschuetztWieT4("Zuweisung :=", "Passwort := " + P1);
        ungeschuetztWieT4("Doppelpunkt in voller Breite", "Passwort" + FF1A + " " + P1);
        ungeschuetztWieT4("lautet mit Doppelpunkt", "Passwort lautet: " + P1);
        ungeschuetztWieT4("ist mit Doppelpunkt", "Passwort ist: " + P1);
        ungeschuetztWieT4("is mit Doppelpunkt", "password is: " + P1);
        ungeschuetztWieT4("Gedankenstrich", "Passwort - " + P1);
        ungeschuetztWieT4("Pfeil ->", "Passwort -> " + P1);
        ungeschuetztWieT4("Attributpaar", "<add key=\"DbPassword\" value=\"" + P1 + "\" />");
        ungeschuetztWieT4("mehr als drei Woerter nach fuer K2",
                "Passwort " + FUER + " den alten Benutzer svc_x ist " + P1);
        ungeschuetztWieT4("mehr als drei Woerter nach fuer K3",
                "Passwort " + FUER + " den alten Benutzer svc_x\t" + P1);
        ungeschuetztWieT4("Auszeichnung vor K2", "**Passwort** ist " + P1);
        ungeschuetztWieT4("Anfuehrungszeichen vor K2", "\"Passwort\" ist " + P1);
        ungeschuetztWieT4("Einzelwort vor ist", "svc_x ist " + P1);
    }

    @Test
    void bekannteGrenze_k2bisK4_wertMitLeerraumOhneAnfuehrung_nurDasErsteWort_imElementGarKein() {
        ungeschuetztWieT4("K2 mehrere Woerter", "Passwort ist Wolke Fuchs 7x!");
        ungeschuetztWieT4("K3 mehrere Woerter", "| Passwort | Wolke Fuchs 7x! |");
        ungeschuetztWieT4("K4 mit Leerraum", "<password>Wolke Fuchs 7x!</password>");
    }

    @Test
    void bekannteGrenze_k3_tabelleOhneLeerraum_derSchlussstrichGehoertZumFund() {
        // Der Wert ist das erste Wort bis zum Leerraum; das | am Ende steht in der Sonderzeichenliste und bleibt.
        fundUeber("K3 ohne Leerraum", "Passwort|", P1 + "|", "");
        // Der Schlussstrich gehoert auch dann zum Fund, wenn der Wert zwischen zwei Strichen steht (S2).
        fundUeber("K3 Schlussstrich", "|Passwort|", P1 + "|", "");
    }

    /** Kein Kandidat deckt den Wert ganz; die Kandidaten sind die des Stands T-4. */
    private static void wertNichtGanzGedeckt(String id, String text, String wert) {
        assertEquals(T4.detect(text), NEU.detect(text), id + " gleich T-4");
        int von = text.indexOf(wert);
        for (Finding f : NEU.detect(text)) {
            assertTrue(f.start() > von || f.end() < von + wert.length(), id + " Wert ganz gedeckt");
        }
    }

    @Test
    void bekannteGrenze_s1_einVerbinderZeichenImWertGiltAlsK1DesZwischenteils_derWertBleibtLesbarWieInT4() {
        String ohneWert = "Passwort " + FUER + " svc_x";
        // Der Doppelpunkt im Wert zaehlt als K1 des Zwischenteils: der Rest dahinter ist der Wert des ersten
        // Durchgangs; der vordere Teil bleibt lesbar. Ist der Rest zu kurz, bleibt der ganze Wert lesbar.
        for (String wert : List.of(P_MIT_DOPPELPUNKT_KURZ, P_MIT_DOPPELPUNKT_VORN + P1)) {
            wertNichtGanzGedeckt("S1 ist", ohneWert + " ist " + wert, wert);
            wertNichtGanzGedeckt("S1 lautet", ohneWert + " lautet " + wert, wert);
            wertNichtGanzGedeckt("S1 Tabelle", "| " + ohneWert + " | " + wert + " |", wert);
            wertNichtGanzGedeckt("S1 Tabulator", ohneWert + "\t" + wert, wert);
        }
        // ganz lesbar: kein Kandidat, wie in T-4
        assertEquals(List.of(), NEU.detect(ohneWert + " ist " + P_MIT_DOPPELPUNKT_KURZ), "S1 ganz lesbar");
        // teilweise lesbar: nur der Rest hinter dem Doppelpunkt ist ein Fund
        String teilweise = ohneWert + " ist " + P_MIT_DOPPELPUNKT_VORN + P1;
        assertEquals(1, NEU.detect(teilweise).size(), "S1 teilweise Zahl");
        assertEquals(teilweise.indexOf(P1), NEU.detect(teilweise).get(0).start(), "S1 teilweise Beginn");
        // Tabulator, Wert, Tabulator, K1 einer weiteren Spalte: der Wert bleibt lesbar wie in T-4
        wertNichtGanzGedeckt("S1 weitere Spalte", ohneWert + "\t" + P1 + "\tBenutzer: abc", P1);
        assertEquals(List.of(), NEU.detect(ohneWert + "\t" + P1 + "\tBenutzer: abc"), "S1 weitere Spalte kein Fund");
    }

    @Test
    void bekannteGrenze_k4_elementMitAttribut_istKeinFundUndGleichT4() {
        ungeschuetztWieT4("Element mit Attribut", "<password type=\"x\">" + P1 + "</password>");
    }

    @Test
    void bekannteGrenze_s2_wertMitStrichDarin_bestehtDerErsteTeilNicht_bleibtDerWertLesbar() {
        assertEquals(List.of(), NEU.detect("|Passwort|" + P_MIT_STRICH_KURZ + "|"), "S2 kurzer erster Teil");
        assertEquals(1, NEU.detect("|Passwort|" + P_MIT_STRICH + "|").size(), "S2 erster Teil besteht");
    }

    @Test
    void bekannteGrenze_offen_rueckstrichVorDemBegrenzer_teileDesWertsKoennenLesbarBleiben() {
        // Offener Punkt aus T-4 (security, niedrig): in Formaten mit Verdopplung gilt \' als maskiert; die Suche
        // endet vor dem Rest. Heutiger Stand: Der Fund endet nach dem maskierten Zeichen, der Rest bleibt sichtbar.
        fundUeber("Rueckstrich vor dem Begrenzer", "password: '", "Ab3#x\\'", "'Yz7'");
        // Ein Wert, der auf einen Rueckstrich endet, schliesst nicht; der Fund endet am Rueckstrich, das
        // schliessende Zeichen bleibt davor stehen.
        fundUeber("Wert endet auf Rueckstrich", "password: '", "Ab3#xYz7\\", "'");
    }

    @Test
    void bekannteGrenze_offen_leererWertInKompaktemJson_derFundLiestUeberDieNaechstenFelder() {
        // Offener Punkt aus T-4 (security, niedrig): der Rueckfall auf das erste Wort liest ueber Schluessel und
        // Wert des naechsten Felds (Fehlalarm und Strukturverlust, kein Abfluss).
        keinFund("leerer Wert allein", "{\"password\":\"\",\"user\":\"svc_x\"}");
        String text = "{\"password\":\"\",\"user\":\"svc_x\",\"token\":\"" + P1 + "\"}";
        List<Finding> funde = passwoerter(text);
        assertEquals(1, funde.size(), "Zahl");
        assertEquals(text.indexOf("\",\"user\""), funde.get(0).start(), "Beginn");
        assertEquals(text.length() - 1, funde.get(0).end(), "Ende");
        assertEquals(T4.detect(text), NEU.detect(text), "gleich T-4");
    }

    // ------------------------------------------------------------------ G-12

    @Test
    void bekannteGrenze_g12_antwortetDieKiMitDemPlatzhalter_stehtNachDerRueckuebersetzungDasPasswortImText() {
        // G-11 (Wert in der Fundtabelle im Klartext) betrifft die Oberflaeche; G-12 gilt fuer die Rueckuebersetzung
        // (D-14): ein Platzhalter in der Antwort der KI wird zurueckuebersetzt, das Passwort steht wieder im Text.
        String text = "Passwort: " + P3;
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, STANDARD.scan(text));
        assertTrue("Passwort: [[PASSWORT_1]]".equals(p.text()), "G-12 Platzhalter im gesendeten Text");
        String antwort = "Bitte setzen Sie [[PASSWORT_1]] neu.";
        String zurueck = new Reidentifier().restore(antwort, p.mapping()).text();
        assertTrue(("Bitte setzen Sie " + P3 + " neu.").equals(zurueck), "G-12 Passwort steht wieder im Text");
    }
}
