package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P_PLATZHALTERARTIG;
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
 * {@code bekannteGrenze...}, der festhaelt, was der Erkenner absichtlich nicht oder anders tut. Stand T-4: G-1, G-2,
 * G-7, G-8, G-9, G-10, G-12, G-13, G-18, G-19, G-20 sowie die Grenzen der Auszeichnung (Entscheidung 6) und des
 * ISO-Zeitstempels (D-8 W3 (e)). Die uebrigen Grenzen gehoeren zu spaeteren Tasks (Tokens, Verbindungszeichenfolgen,
 * K2 bis K4) oder zur Oberflaeche.
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
