package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P1_OHNE_AUSRUF;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P5;
import static de.skillshield.core.SynthetischeGeheimnisse.P6;
import static de.skillshield.core.SynthetischeGeheimnisse.P7;
import static de.skillshield.core.SynthetischeGeheimnisse.P_DOLLAR;
import static de.skillshield.core.SynthetischeGeheimnisse.P_HUNTER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_LEERZEICHEN;
import static de.skillshield.core.SynthetischeGeheimnisse.P_ZQ9;
import static de.skillshield.core.SynthetischeGeheimnisse.mischwert;
import static de.skillshield.core.SynthetischeGeheimnisse.wert4096;
import static de.skillshield.core.SynthetischeGeheimnisse.wert4097;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Passwort nach Kontextwort mit {@code :} oder {@code =} (Plan geheimnisse-erkennung, T-4): AK-1 bis AK-3, AK-4 und
 * AK-5 (K1), AK-6 (Zeilenumbruch), AK-7 bis AK-15, AK-16 (K1), AK-28, AK-29, AK-30 (Passwort-Teil), AK-45 (Teile),
 * die Wirkung der Entscheidung 6 (Auszeichnungszeichen) und das Passwort am Ende einer URL (Nachtrag zu AK-28).
 *
 * <p>Alle Tests laufen gegen {@code TextScanner.standard()}; Funde werden mit Typ, Bereich, Konfidenz und Quelle
 * verglichen. Meldungen nennen nur Fall-Nummern und Zahlen, nie einen Text oder Wert (Plan Kapitel 6.1);
 * {@code Finding.toString()} enthaelt keinen Wert.
 */
class PasswortKontextTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String QUELLE = "passwort-kontext";
    private static final String AE = String.valueOf((char) 0xE4);
    private static final String UE = String.valueOf((char) 0xFC);
    private static final String NBSP = String.valueOf((char) 0xA0);
    private static final String DUENN = String.valueOf((char) 0x2009);
    private static final String FUER = "f" + UE + "r";

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> passwoerter(String text) {
        List<Finding> liste = new ArrayList<>();
        for (Finding f : STANDARD.scan(text)) {
            if (f.type().equals(GeheimnisTypen.PASSWORT)) {
                liste.add(f);
            }
        }
        return liste;
    }

    private static Finding erwartet(String vor, String wert, double konfidenz) {
        return new Finding(GeheimnisTypen.PASSWORT, wert, vor.length(), vor.length() + wert.length(), konfidenz,
                QUELLE);
    }

    /** Genau ein PASSWORT-Fund ueber {@code wert} zwischen {@code vor} und {@code nach}. */
    private static void genau(String id, String vor, String wert, String nach, double konfidenz) {
        String text = vor + wert + nach;
        assertEquals(List.of(erwartet(vor, wert, konfidenz)), passwoerter(text), id);
        ConfidenceLevel stufe = konfidenz >= 0.90 ? ConfidenceLevel.SICHER : ConfidenceLevel.WAHRSCHEINLICH;
        assertEquals(stufe, ConfidenceLevel.of(konfidenz), id);
    }

    private static void genau(String id, String vor, String wert, double konfidenz) {
        genau(id, vor, wert, "", konfidenz);
    }

    private static void keinFund(String id, String text) {
        assertEquals(List.of(), passwoerter(text), id);
    }

    private static String pseudo(String text) {
        return PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text)).text();
    }

    private static void roundtrip(String id, String text) {
        PseudonymizationResult p = PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text));
        String zurueck = REIDENTIFIER.restore(p.text(), p.mapping()).text();
        assertTrue(text.equals(zurueck), "Roundtrip nicht zeichengleich: " + id);
    }

    // ------------------------------------------------------------------ AK-1

    @Test
    void ak1_referenzzeile_genauEinFundMitAusrufezeichen_sicher() {
        String vor = "Tempor" + AE + "res Passwort " + FUER + " svc_abr_export: ";
        genau("AK-1", vor, P1, 0.95);
        List<Finding> alle = STANDARD.scan(vor + P1);
        assertEquals(1, alle.size(), "kein weiterer Fund in der Referenzzeile");
        assertEquals(QUELLE, alle.get(0).source());
        assertEquals(GeheimnisTypen.PASSWORT.name(), alle.get(0).type().name());
        assertEquals(ConfidenceLevel.SICHER, ConfidenceLevel.of(alle.get(0).confidence()));
    }

    @Test
    void ak1_pseudonymisiert_nurDerWertWirdErsetzt_roundtripGleich() {
        String vor = "Tempor" + AE + "res Passwort " + FUER + " svc_abr_export: ";
        String text = vor + P1;
        assertTrue((vor + "[[PASSWORT_1]]").equals(pseudo(text)), "pseudonymisierte Referenzzeile");
        roundtrip("AK-1", text);
    }

    // ------------------------------------------------------------------ AK-2

    @Test
    void ak2_kontextwoerterMitDoppelpunkt_genauEinFund() {
        List<String> woerter = List.of("Passwort", "passwort", "KENNWORT", "Kennwort", "password", "Password",
                "passwd", "Passphrase", "Initialpasswort", "Einmalkennwort", "Adminpasswort", "Root-Passwort",
                "db-passwort", "PW", "pw", "Pwd");
        for (int i = 0; i < woerter.size(); i++) {
            genau("AK-2 Doppelpunkt " + i, woerter.get(i) + ": ", P1, 0.95);
        }
    }

    @Test
    void ak2_kontextwoerterMitGleichheitszeichenOhneLeerzeichen_genauEinFund() {
        for (String wort : List.of("DB_PASSWORD", "dbPassword", "pwd", "PW", "PASSWD", "PassPhrase")) {
            genau("AK-2 Gleichheitszeichen " + wort.length(), wort + "=", P1, 0.95);
        }
    }

    @Test
    void ak2_zusammensetzungenUndGrossKleinschreibung_amEndeDesWortkoerpers() {
        List<String> woerter = List.of("Systempasswort", "ADMIN_PASSWORT", "svc-kennwort", "mySQL_Password",
                "root_passphrase", "x1_passwd");
        for (int i = 0; i < woerter.size(); i++) {
            genau("AK-2 Zusammensetzung " + i, woerter.get(i) + ": ", P1, 0.95);
        }
    }

    // ------------------------------------------------------------------ AK-3

    @Test
    void ak3_abgrenzungDerKontextwoerter_keinFund() {
        List<String> woerter = List.of("Passwortrichtlinie", "Passwort-Reset", "Passw" + (char) 0xF6 + "rter",
                "Kennwortes", "Pass", "PIN", "Zugangscode", "Schl" + UE + "ssel", "Zugangsdaten", "xpw", "db-pw",
                "Passwortdatei", "Passwort2", "Kennwort-Hash", "Login", "Kennung", "Credentials", "Code");
        for (int i = 0; i < woerter.size(); i++) {
            keinFund("AK-3 Wort " + i, woerter.get(i) + ": " + P1);
        }
    }

    @Test
    void ak3_pwUndPwdMitIstOhneVerbinderK1_keinFund() {
        keinFund("AK-3 pw ist", "pw ist " + P1);
        keinFund("AK-3 pwd ist", "pwd ist " + P1);
        keinFund("AK-3 pwd Leerzeichen", "pwd " + P1);
    }

    @Test
    void ak3_kontextwortOhneWert_keinFundKeineAusnahme() {
        for (String text : List.of("Passwort:", "Passwort", "Passwort: ", "Passwort:\n", "Passwort =", "Das Passwort:",
                "Passwort:\r\n   ", "Passwort:\n   \n" + P1, "pw:", "Passwort: \t ", "")) {
            keinFund("AK-3 ohne Wert " + text.length(), text);
        }
    }

    // ------------------------------------------------------------------ AK-4 (K1) und AK-5 (K1)

    @Test
    void ak4_verbinderK1MitLeerraum_alle095() {
        genau("AK-4 gleich", "Passwort = ", P1, 0.95);
        genau("AK-4 beidseitig", "Passwort : ", P1, 0.95);
        genau("AK-4 ohne Leerraum", "Passwort:", P1, 0.95);
        genau("AK-4 gleich ohne", "Passwort=", P1, 0.95);
        genau("AK-4 zwei Leerzeichen", "Passwort:  ", P1, 0.95);
        genau("AK-4 Tabulator", "Passwort:\t", P1, 0.95);
    }

    @Test
    void ak4_anfuehrungszeichenUmKontextwortUndWert_fundOhneAnfuehrung() {
        genau("AK-4 JSON", "\"password\": \"", P1, "\"", 0.95);
        genau("AK-4 einfach", "password: '", P1, "'", 0.95);
        genau("AK-4 gleich", "password = \"", P1, "\"", 0.95);
        genau("AK-4 Kurzform", "'pw' = ", P1, 0.95);
        genau("AK-4 Anfuehrung nur um Kontext", "\"Passwort\": ", P1, 0.95);
    }

    @Test
    void ak5_zwischenteilBeiK1_genauEinFund() {
        genau("AK-5 fuer", "Passwort " + FUER + " svc_x: ", P1, 0.95);
        genau("AK-5 des Kontos", "Passwort des Kontos KVBSP\\svc_x: ", P1, 0.95);
        genau("AK-5 for", "password for root: ", P1, 0.95);
        genau("AK-5 drei Woerter", "Das Passwort " + FUER + " den Benutzer svc_x: ", P1, 0.95);
        genau("AK-5 vor dem Doppelpunkt Leerzeichen", "Passwort " + FUER + " svc_x : ", P1, 0.95);
        genau("AK-5 von", "Kennwort von SRV-APP07: ", P1, 0.95);
        genau("AK-5 zum", "Kennwort zum Dienstkonto svc_x: ", P2, 0.95);
        genau("AK-5 Grossbuchstaben", "PASSWORT F" + (char) 0xDC + "R SVC_X: ", P1, 0.95);
        genau("AK-5 mit Gleichheitszeichen", "password for root=", P1, 0.95);
    }

    @Test
    void ak5_keinFund_beiVierWoertern_zuLangemWortUndZeilenumbruchImZwischenteil() {
        keinFund("AK-5 vier Woerter", "Passwort " + FUER + " den alten Benutzer svc_x: " + P1);
        keinFund("AK-5 Zeilenumbruch", "Passwort " + FUER + " den\nBenutzer svc_x: " + P1);
        keinFund("AK-5 Wort mit 65 Zeichen", "Passwort " + FUER + " " + "x".repeat(65) + ": " + P1);
        keinFund("AK-5 ohne Zwischenwort", "Passwort svc_x: " + P1);
        keinFund("AK-5 Zwischenwort ohne Wort", "Passwort " + FUER + ": " + P1);
        keinFund("AK-5 Zwischenwort ohne Leerraum", "Passwort" + FUER + " svc_x: " + P1);
    }

    @Test
    void ak5_einWortMitVierundsechzigZeichenImZwischenteilIstErlaubt() {
        genau("AK-5 Wort mit 64 Zeichen", "Passwort " + FUER + " " + "x".repeat(64) + ": ", P1, 0.95);
    }

    // ------------------------------------------------------------------ AK-6 (Umbruch nach K1)

    @Test
    void ak6_zeilenumbruchNachK1_fund080() {
        genau("AK-6 LF", "Passwort:\n", P1, 0.80);
        genau("AK-6 CRLF und Einrueckung", "Passwort:\r\n    ", P1, 0.80);
        genau("AK-6 gleich LF", "Passwort =\n", P1, 0.80);
        genau("AK-6 CR", "Passwort:\r", P1, 0.80);
        genau("AK-6 Tabulator", "Passwort:\n\t\t", P1, 0.80);
        genau("AK-6 Leerzeichen vor dem Umbruch", "Passwort:  \n", P1, 0.80);
        genau("AK-6 Zeilenende danach", "Kennwort:\n", P2, "\nWeitere Angaben folgen.", 0.80);
    }

    @Test
    void ak6_leerzeileOderKeinWertAmAnfangDerNaechstenZeile_keinFund() {
        keinFund("AK-6 Leerzeile", "Passwort:\n\n" + P1);
        keinFund("AK-6 Leerzeile mit Leerzeichen", "Passwort:\n  \n" + P1);
        keinFund("AK-6 zwei Umbrueche CRLF", "Passwort:\r\n\r\n" + P1);
        keinFund("AK-6 Umbruch, Wert in der Zeile danach", "Passwort:\nBitte neues Passwort vergeben.");
        keinFund("AK-6 Passwort ist Umbruch", "Passwort ist\n" + P1);
    }

    @Test
    void ak6_dieNaechsteZeileMussDenSchwachenWertTestBestehen() {
        // Sechs Zeichen und Wert mit nur zwei Klassen genuegen mit K1 in derselben Zeile, nicht nach dem Umbruch.
        genau("AK-6 gleiche Zeile, sechs Zeichen", "Passwort: ", P6, 0.80);
        keinFund("AK-6 Umbruch, sechs Zeichen", "Passwort:\n" + P6);
        genau("AK-6 gleiche Zeile, zwei Klassen", "Passwort: ", P_HUNTER, 0.95);
        keinFund("AK-6 Umbruch, zwei Klassen", "Passwort:\n" + P_HUNTER);
    }

    // ------------------------------------------------------------------ AK-7 (Ende des Werts)

    @Test
    void ak7_satzzeichenAmEndeWerdenAbgetrennt_dasAusrufezeichenBleibt() {
        genau("AK-7 Punkt", "Passwort: ", P1, ".", 0.95);
        genau("AK-7 Komma", "Passwort: ", P1, ",", 0.95);
        genau("AK-7 Semikolon", "Passwort: ", P1, ";", 0.95);
        genau("AK-7 Doppelpunkt", "Passwort: ", P1, ":", 0.95);
        genau("AK-7 Fragezeichen", "Passwort: ", P1, "?", 0.95);
        genau("AK-7 Klammer", "(Passwort: ", P1, ")", 0.95);
        genau("AK-7 eckige Klammer", "[Passwort: ", P1, "]", 0.95);
        genau("AK-7 mehrere", "Passwort: ", P1, ".)", 0.95);
        genau("AK-7 Punkt Komma", "Passwort: ", P1, ".,;:?", 0.95);
        genau("AK-7 Punkt ohne Ausrufezeichen", "Passwort: ", P1_OHNE_AUSRUF, ".", 0.95);
        genau("AK-7 doppeltes Ausrufezeichen", "Passwort: ", P1 + "!", 0.95);
        genau("AK-7 Ausrufezeichen und Punkt", "Passwort: ", P1 + "!", ".", 0.95);
        genau("AK-7 Bindestrich bleibt Teil", "Passwort: ", P1 + "-", 0.95);
    }

    @Test
    void ak7_ausgeglichenKlammernImWertBleiben_unausgeglichenWerdenAbgetrennt() {
        String rund = P_HUNTER + "(" + P7 + ")";
        String geschweift = P_HUNTER + "{" + P7 + "}";
        String eckig = P_HUNTER + "[" + P7 + "]";
        genau("AK-7 ausgeglichen", "Passwort: ", rund, "", 0.95);
        genau("AK-7 ausgeglichen mit Punkt", "Passwort: ", rund, ".", 0.95);
        genau("AK-7 geschweift ausgeglichen", "Passwort: ", geschweift, "", 0.95);
        genau("AK-7 eckig ausgeglichen", "Passwort: ", eckig, "", 0.95);
        genau("AK-7 Ueberschuss", "Passwort: ", rund, ")", 0.95);
        genau("AK-7 Ueberschuss geschweift", "Passwort: ", geschweift, "}", 0.95);
        genau("AK-7 Anfuehrungszeichen ohne oeffnendes", "Passwort: ", P1, "\"", 0.95);
        genau("AK-7 Apostroph ohne oeffnendes", "Passwort: ", P1, "'", 0.95);
        genau("AK-7 Anfuehrung mit oeffnendem im Wert", "Passwort: ", P_HUNTER + "\"" + P7 + "\"", "", 0.95);
    }

    @Test
    void ak7_derWertEndetAmLeerraum() {
        genau("AK-7 Leerraum", "Passwort: ", P1, " bitte " + AE + "ndern", 0.95);
        genau("AK-7 Zeilenende", "Passwort: ", P1, "\nKennwort ist abgelaufen", 0.95);
        genau("AK-7 Tabulator", "Passwort: ", P1, "\tund mehr", 0.95);
    }

    @Test
    void ak9_mehrereWerteInEinerZeile_jeEinFund_verschiedenePlatzhalter() {
        String text = "Passwort: " + P1 + " Passwort: " + P2;
        List<Finding> funde = passwoerter(text);
        assertEquals(2, funde.size(), "AK-9 zwei Funde");
        assertEquals(erwartet("Passwort: ", P1, 0.95), funde.get(0));
        assertEquals(erwartet("Passwort: " + P1 + " Passwort: ", P2, 0.95), funde.get(1));
        assertTrue("Passwort: [[PASSWORT_1]] Passwort: [[PASSWORT_2]]".equals(pseudo(text)), "AK-9 Platzhalter");
        roundtrip("AK-9", text);
    }

    @Test
    void ak9_gleicherWertTrotzzweimalDerselbePlatzhalter() {
        String text = "Passwort: " + P1 + " und Passwort: " + P1;
        assertEquals(2, passwoerter(text).size(), "zwei Funde");
        assertTrue("Passwort: [[PASSWORT_1]] und Passwort: [[PASSWORT_1]]".equals(pseudo(text)), "ein Platzhalter");
    }

    @Test
    void ak9_benutzerKommaPasswortKomma_einFundOhneKomma() {
        String vor = "Benutzer: svc_x, Passwort: ";
        genau("AK-9 Komma", vor, P1, ", Hinweis: bitte " + AE + "ndern", 0.95);
        String text = vor + P1 + ",";
        assertTrue((vor + "[[PASSWORT_1]],").equals(pseudo(text)), "Komma bleibt");
    }

    @Test
    void ak9_zweiKontextwoerterMitKommaUndDirektHintereinander() {
        String text = "pw=" + P1 + "; pwd=" + P2;
        List<Finding> funde = passwoerter(text);
        assertEquals(2, funde.size());
        assertEquals(erwartet("pw=", P1, 0.95), funde.get(0));
        assertEquals(erwartet("pw=" + P1 + "; pwd=", P2, 0.95), funde.get(1));
        text = "Passwort: " + P1 + "\nKennwort: " + P2;
        assertEquals(2, passwoerter(text).size(), "zwei Zeilen");
    }

    // ------------------------------------------------------------------ AK-8 (Anfuehrungszeichen)

    @Test
    void ak8_wertMitLeerzeichenInAnfuehrungszeichen_gerade_typografisch_gravis() {
        genau("AK-8 gerade", "Passwort: \"", P_MIT_LEERZEICHEN, "\"", 0.95);
        genau("AK-8 typografisch", "Passwort: " + (char) 0x201E, P_MIT_LEERZEICHEN,
                String.valueOf((char) 0x201C), 0.95);
        genau("AK-8 Gravis", "Passwort: `", P_MIT_LEERZEICHEN, "`", 0.95);
        genau("AK-8 einfach", "Passwort: '", P_MIT_LEERZEICHEN, "'", 0.95);
        genau("AK-8 Guillemets", "Passwort: " + (char) 0xBB, P_MIT_LEERZEICHEN, String.valueOf((char) 0xAB), 0.95);
        genau("AK-8 einfache typografische", "Passwort: " + (char) 0x201A, P_MIT_LEERZEICHEN,
                String.valueOf((char) 0x2018), 0.95);
        genau("AK-8 Nachsatz", "Passwort: \"", P2, "\" gilt bis Freitag.", 0.95);
    }

    @Test
    void ak8_fehlendesSchliessendesZeichen_wertOhneDasOeffnende() {
        genau("AK-8 ohne Schluss", "Passwort: \"", P1, 0.95);
        genau("AK-8 ohne Schluss, Leerzeichen danach", "Passwort: \"", P1, " und mehr", 0.95);
        genau("AK-8 Gravis ohne Schluss", "Passwort: `", P1, 0.95);
        keinFund("AK-8 nur das oeffnende Zeichen", "Passwort: \"");
        keinFund("AK-8 oeffnendes Zeichen und Leerraum", "Passwort: \" " + P1);
    }

    @Test
    void ak8_dasSchliessendeZeichenLiegtZuWeitWegOderInDerNaechstenZeile_wieOhneAnfuehrung() {
        String lang = "x".repeat(300);
        genau("AK-8 zu weit", "Passwort: \"", P1, " " + lang + "\"", 0.95);
        genau("AK-8 naechste Zeile", "Passwort: \"", P1, " bitte\n\"", 0.95);
    }

    @Test
    void ak8_inhaltDerAnfuehrungMussDenWertTestBestehen() {
        keinFund("AK-8 Woerter", "Passwort: \"siehe Tresor\"");
        keinFund("AK-8 leer", "Passwort: \"\"");
        keinFund("AK-8 kurz", "Passwort: \"" + P5 + "\"");
        keinFund("AK-8 Pfad", "Passwort: \"/etc/shad0w\"");
        genau("AK-8 sechs Zeichen", "Passwort: \"", P6, "\"", 0.80);
        genau("AK-8 256 Zeichen", "Passwort: \"", mischwert(256), "\"", 0.95);
        // Mit 257 Zeichen findet sich kein schliessendes Zeichen in Reichweite: der Wert gilt wie ohne
        // Anfuehrungszeichen (D-7), das schliessende Zeichen ohne oeffnendes wird abgetrennt.
        genau("AK-8 257 Zeichen wie ohne Anfuehrung", "Passwort: \"", mischwert(257), "\"", 0.95);
    }

    // ------------------------------------------------------------------ AK-10 bis AK-12

    @Test
    void ak10_laenge_sechsUndSiebenZeichen080_fuenfZeichenKeinFund() {
        genau("AK-10 sechs", "Passwort: ", P6, 0.80);
        genau("AK-10 sieben", "Passwort: ", P7, 0.80);
        keinFund("AK-10 fuenf", "Passwort: " + P5);
        genau("AK-10 acht", "Passwort: ", P_HUNTER, 0.95);
    }

    @Test
    void ak10_viertausendNeunzigSechsZeichenEinFund_4097Keiner() {
        genau("AK-10 4096", "Passwort: ", wert4096(), 0.95);
        keinFund("AK-10 4097", "Passwort: " + wert4097());
        keinFund("AK-10 4097 mit Punkt", "Passwort: " + wert4097() + ".");
    }

    @Test
    void ak11_zeichenmischung_keinFund() {
        for (String wert : List.of("abgelaufen", "Sonnenschein", "123456", "falsch!", "********", "!!!!!!!!",
                "E-Mail", "nicht-gesetzt", "Sommer!", "abcdef")) {
            keinFund("AK-11 Laenge " + wert.length(), "Passwort: " + wert);
        }
    }

    @Test
    void ak11_zeichenmischung_einFund() {
        genau("AK-11 Sommer2026", "Passwort: ", P1_OHNE_AUSRUF, 0.95);
        genau("AK-11 Dollar", "Passwort: ", P_DOLLAR, 0.95);
        genau("AK-11 Bindestrich mit Ziffer", "Passwort: ", P3, 0.95);
        genau("AK-11 Raute", "Passwort: ", P2, 0.95);
    }

    @Test
    void ak12_versionsTicketZahlwortUndFachbegriffe_keinFund() {
        List<String> werte = List.of("v4.2.1", "4.2.1-rc1", "CHG0012345", "INC0004711", "8-stellig",
                "90-t" + AE + "gig", "SHA-256", "AES-256", "TLS1.2", "Windows11", "JIRA-1234",
                "2026-03-12T10:15:00Z");
        for (int i = 0; i < werte.size(); i++) {
            keinFund("AK-12 " + i, "Passwort: " + werte.get(i));
            keinFund("AK-12 Gleichheitszeichen " + i, "Passwort=" + werte.get(i));
        }
    }

    // ------------------------------------------------------------------ AK-13, AK-14, AK-15, AK-16

    @Test
    void ak13_pfadUndAdressformen_keinPasswortFundWohlAberDerBestehendeFund() {
        List<String> texte = List.of("Passwort: /etc/shadow", "Passwort: C:\\Tresor\\pw.kdbx",
                "Passwort: https://tresor.kvbsp.local/eintrag", "Passwortdatei: /etc/x");
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            keinFund("AK-13 Passwort " + i, text);
            assertEquals(TextScannerStandGeheimnis.ALT.scan(text), STANDARD.scan(text), "AK-13 wie heute " + i);
        }
        assertEquals(FindingType.PFAD, STANDARD.scan(texte.get(0)).get(0).type());
        assertEquals(FindingType.PFAD, STANDARD.scan(texte.get(1)).get(0).type());
        assertEquals(1, STANDARD.scan(texte.get(2)).size());
        assertEquals(FindingType.URL, STANDARD.scan(texte.get(2)).get(0).type());
    }

    @Test
    void ak14_masken_variablen_vorlagen_keinFund() {
        List<String> texte = List.of("Passwort: <geheim>", "Passwort: [entfernt]", "password=${DB_PASSWORD}",
                "password=%DB_PASSWORD%", "password: {{ vault_pw }}", "Passwort: xxxxxxxx", "Passwort: [[PASSWORT_1]]",
                "Passwort: ******", "password: ${DB_PW1}", "Passwort: {vault1}", "Passwort: [[TOKEN_2]]");
        for (int i = 0; i < texte.size(); i++) {
            keinFund("AK-14 " + i, texte.get(i));
        }
    }

    @Test
    void ak14_einSchonErsetzterText_erzeugtKeinenNeuenFund() {
        String text = "Passwort: " + P1;
        String ersetzt = pseudo(text);
        keinFund("AK-14 zweiter Durchlauf", ersetzt);
        assertEquals(List.of(), STANDARD.scan(ersetzt), "AK-14 gar kein Fund im ersetzten Text");
    }

    @Test
    void ak15_kurzformen_genauEinFund_pwdAlsBefehlKeiner() {
        genau("AK-15 pw", "pw: ", P1, 0.95);
        genau("AK-15 PW", "PW=", P1, 0.95);
        genau("AK-15 Pwd", "Pwd: ", P1, 0.95);
        keinFund("AK-15 Befehl", "Ausgabe von pwd: /opt/app");
        keinFund("AK-15 Substitution", "cd $(pwd) && ls");
        keinFund("AK-15 Befehl ohne Verbinder", "pwd /opt/app1");
    }

    @Test
    void ak16_k1_derErsteWertMitZweiKlassenIstEinFund() {
        genau("AK-16 K1 hunter", "Passwort: ", P_HUNTER, 0.95);
        genau("AK-16 K1 Sommer2026", "Passwort: ", P1_OHNE_AUSRUF, 0.95);
        keinFund("AK-16 K1 sechs Zeichen ohne Ziffer", "Passwort: Abcdef");
    }

    // ------------------------------------------------------------------ AK-45 (Teile)

    @Test
    void ak45_geschuetztesUndSchmalesLeerzeichenWirkenWieEinLeerzeichen() {
        genau("AK-45 NBSP", "Passwort:" + NBSP, P1, 0.95);
        genau("AK-45 U+2009", "Passwort:" + DUENN, P1, 0.95);
        genau("AK-45 NBSP vor dem Verbinder", "Passwort" + NBSP + ": ", P1, 0.95);
        genau("AK-45 NBSP im Umbruch", "Passwort:\n" + NBSP + DUENN, P1, 0.80);
    }

    @Test
    void ak45_wertMitEmoji_keinFundBeginntOderEndetMitteInEinemSurrogatpaar() {
        String emoji = new String(Character.toChars(0x1F600));
        String wert = P1 + emoji;
        genau("AK-45 Emoji am Ende", "Passwort: ", wert, 0.95);
        genau("AK-45 Emoji am Anfang", "Passwort: ", emoji + P1, 0.95);
        genau("AK-45 Emoji in Anfuehrung", "Passwort: \"", "Ab" + emoji + "3 x", "\"", 0.80);
        for (Finding f : STANDARD.scan("Passwort: " + emoji + P1 + emoji + " " + emoji)) {
            String text = "Passwort: " + emoji + P1 + emoji + " " + emoji;
            assertFalse(Character.isLowSurrogate(text.charAt(f.start())), "Beginn im Surrogatpaar");
            assertFalse(Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar");
        }
    }

    @Test
    void ak45_leererTextUndLeerraum_leereListe_nullIstEineNullPointerExceptionMitText() {
        assertEquals(List.of(), STANDARD.scan(""));
        assertEquals(List.of(), STANDARD.scan("   \n\t  "));
        NullPointerException e = assertThrows(NullPointerException.class, () -> new PasswortRecognizer().detect(null));
        assertEquals("text", e.getMessage());
    }

    @Test
    void ak45_gleicherTextGleicheFundlisteInGleicherReihenfolge() {
        String text = "Passwort: " + P1 + "\nKennwort: " + P2 + " pw=" + P3;
        assertEquals(STANDARD.scan(text), STANDARD.scan(text));
        List<Finding> funde = STANDARD.scan(text);
        for (int i = 1; i < funde.size(); i++) {
            assertTrue(funde.get(i - 1).end() <= funde.get(i).start(), "Reihenfolge und Ueberlappung " + i);
        }
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
        }
    }

    @Test
    void erkennerLiefertUnveraenderlicheListe() {
        List<Finding> funde = new PasswortRecognizer().detect("Passwort: " + P1);
        assertEquals(1, funde.size());
        assertThrows(UnsupportedOperationException.class, () -> funde.add(funde.get(0)));
        assertEquals(PasswortRecognizer.QUELLE_KONTEXT, QUELLE);
    }

    // ------------------------------------------------------------------ AK-28 und der Nachtrag (URL-Ende)

    @Test
    void ak28_urlMitZugangsdaten_einUrlFundKeinPasswortFund_wertNichtImKlartext() {
        List<String> texte = List.of("https://svc_x:" + P1 + "@host.example/pfad", "ftp://u:" + P1 + "@host.example",
                "jdbc:postgresql://host.example/db?user=u&password=" + P3);
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> funde = STANDARD.scan(text);
            assertEquals(1, funde.size(), "AK-28 Zahl der Funde " + i);
            assertEquals(FindingType.URL, funde.get(0).type(), "AK-28 Typ " + i);
            assertEquals("url", funde.get(0).source(), "AK-28 Quelle " + i);
            assertEquals(0.95, funde.get(0).confidence(), 0.0, "AK-28 Konfidenz " + i);
            assertEquals(TextScannerStandGeheimnis.ALT.scan(text), funde, "AK-28 wie heute " + i);
            keinFund("AK-28 kein PASSWORT-Fund " + i, text);
            assertFalse(pseudo(text).contains(P1), "AK-28 Klartext P1 " + i);
            assertFalse(pseudo(text).contains(P3), "AK-28 Klartext P3 " + i);
            roundtrip("AK-28 " + i, text);
        }
    }

    @Test
    void ak28_nachtrag_passwortAmEndeEinerUrl_urlFundWieHeute_plusReststueckUeberDieRestzeichen() {
        String prefix = "jdbc:postgresql://host.example/db?user=u&password=";
        // das Ausrufezeichen am Ende des Werts schneidet der URL-Erkenner ab: Reststueck
        String text = prefix + P1;
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(2, funde.size(), "URL und Reststueck");
        assertEquals(TextScannerStandGeheimnis.ALT.scan(text).get(0), funde.get(0), "URL-Fund wie heute");
        assertEquals(erwartet(prefix + P1_OHNE_AUSRUF, "!", 0.95), funde.get(1), "Reststueck");
        assertFalse(pseudo(text).contains(P1), "Wert nicht im Klartext");
        assertFalse(pseudo(text).contains("!"), "auch das Ausrufezeichen ist ersetzt");
        roundtrip("URL-Ende", text);
    }

    @Test
    void ak28_nachtrag_alleEndzeichenDesUrlErkenners_einzelnGeprueft() {
        String prefix = "jdbc:postgresql://host.example/db?user=u&password=";
        for (String ende : List.of(".", ",", ";", ":", "?", "'", "\"", ")", "]", "!")) {
            String text = prefix + P1 + ende;
            List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
            List<Finding> neu = STANDARD.scan(text);
            assertEquals(alt.get(0), neu.get(0), "URL-Fund wie heute, Ende " + ende.charAt(0));
            // der Wert (ohne Satzzeichen) steht hinter dem URL-Fund, soweit dieser ihn nicht abdeckt
            Finding url = neu.get(0);
            int wertEnde = prefix.length() + P1.length();
            for (int pos = prefix.length(); pos < wertEnde; pos++) {
                boolean geschuetzt = pos < url.end();
                for (int k = 1; k < neu.size(); k++) {
                    geschuetzt |= neu.get(k).start() <= pos && pos < neu.get(k).end();
                }
                assertTrue(geschuetzt, "Position des Werts ungeschuetzt, Ende " + ende.charAt(0));
            }
            String ersetzt = pseudo(text);
            assertFalse(ersetzt.contains(P1_OHNE_AUSRUF), "Klartext, Ende " + ende.charAt(0));
            roundtrip("URL-Ende " + ende.charAt(0), text);
        }
    }

    @Test
    void ak28_nachtrag_mehrfachesAusrufezeichenUndTextDahinter() {
        String prefix = "jdbc:postgresql://host.example/db?user=u&password=";
        String text = prefix + P1 + "! im Wiki";
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(2, funde.size());
        assertEquals(erwartet(prefix + P1_OHNE_AUSRUF, "!!", 0.95), funde.get(1));
        assertEquals(FindingType.URL, funde.get(0).type());
    }

    @Test
    void ak28_nachtrag_einWertOhneAbgeschnittenesEndzeichen_gehtGanzImUrlFundAuf() {
        String text = "jdbc:postgresql://host.example/db?user=u&password=" + P3;
        assertEquals(1, STANDARD.scan(text).size());
        keinFund("URL ohne Rest", text);
    }

    // ------------------------------------------------------------------ AK-29, AK-30

    @Test
    void ak29_wertInEmailForm_genauEinEmailFundUeberDenWert_keinPasswortFund() {
        String wert = "erika.beispiel" + "@" + "kv-beispiel.example";
        String text = "Passwort: " + wert;
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size());
        assertEquals(FindingType.EMAIL, funde.get(0).type());
        assertEquals(10, funde.get(0).start());
        assertEquals(text.length(), funde.get(0).end());
        assertEquals(TextScannerStandGeheimnis.ALT.scan(text), funde);
        assertFalse(pseudo(text).contains(wert), "Wert nicht im Klartext");
    }

    @Test
    void ak29_geheimnisInPfadUndInEmailAdresse_genauDerBestehendeFund() {
        List<String> texte = List.of("C:\\x\\pw=" + P1 + "\\y", "pw=" + P1 + "@" + "example.invalid");
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            assertEquals(TextScannerStandGeheimnis.ALT.scan(text), STANDARD.scan(text), "AK-29 wie heute " + i);
            keinFund("AK-29 kein PASSWORT-Fund " + i, text);
            assertFalse(pseudo(text).contains(P1), "AK-29 Klartext " + i);
        }
    }

    @Test
    void ak29_teilueberlappung_bestandsfundBleibt_restdesWertsIstPasswortFund_keinePositionFrei() {
        String text = "Passwort: " + P_ZQ9 + (char) 0xA7 + "erika.beispiel" + "@" + "kv-beispiel.example";
        List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        assertEquals(1, alt.size());
        assertTrue(neu.containsAll(alt), "Bestandsfund unveraendert");
        assertEquals(2, neu.size());
        assertEquals(GeheimnisTypen.PASSWORT, neu.get(0).type());
        assertEquals(10, neu.get(0).start());
        assertEquals(alt.get(0).start(), neu.get(0).end());
    }

    @Test
    void ak30_kvFundeBleibenUnveraendert_plusPasswortFund() {
        String bsnr = "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + ", Passwort: " + P1;
        String lanr = "LANR " + SynthetischeKvNummern.LANR_GUELTIG + ", Kennwort: " + P2;
        for (String text : List.of(bsnr, lanr)) {
            List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
            List<Finding> neu = STANDARD.scan(text);
            assertEquals(1, alt.size(), "ein KV-Fund heute");
            assertEquals(2, neu.size());
            assertTrue(neu.contains(alt.get(0)), "KV-Fund unveraendert");
            assertEquals(1, passwoerter(text).size());
            assertEquals(QUELLE, passwoerter(text).get(0).source());
        }
    }

    @Test
    void ak30_dasKontextwortAendertKeinenKvFund() {
        String ohne = "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT;
        String mit = ohne + ", Passwort: " + P1;
        assertEquals(STANDARD.scan(ohne), STANDARD.scan(mit).subList(0, 1));
    }

    // ------------------------------------------------------------------ Entscheidung 6: Auszeichnungszeichen

    @Test
    void entscheidung6_positiv_auszeichnungUmKontextwortUndVerbinder_fundOhneAuszeichnung() {
        genau("E6 fett mit Doppelpunkt innen", "**Passwort:** ", P1, 0.95);
        genau("E6 fett, Doppelpunkt aussen", "**Kennwort**: ", P1, 0.95);
        genau("E6 Unterstriche", "__Passwort__: ", P1, 0.95);
        genau("E6 kursiv", "*Passwort*: ", P1, 0.95);
        genau("E6 Gravis um das Kontextwort", "`Passwort`: ", P1, 0.95);
        genau("E6 fett mit Zwischenteil, Doppelpunkt innen", "**Passwort " + FUER + " svc_x:** ", P1, 0.95);
        genau("E6 fett mit Zwischenteil, Doppelpunkt aussen", "**Passwort " + FUER + " svc_x**: ", P1, 0.95);
        genau("E6 drei Sterne", "***Passwort:*** ", P1, 0.95);
        genau("E6 Gleichheitszeichen", "**password=** ", P1, 0.95);
        genau("E6 Verbinder mit Leerzeichen davor", "**Passwort :** ", P1, 0.95);
    }

    @Test
    void entscheidung6_positiv_auszeichnungUmDenWert_fundEndetVorDerFolge() {
        genau("E6 Wert fett", "Passwort: **", P1, "**", 0.95);
        genau("E6 Wert fett mit Punkt", "Passwort: **", P1, "**.", 0.95);
        genau("E6 Wert unterstrichen", "Passwort: __", P1, "__", 0.95);
        genau("E6 Wert drei Sterne", "Passwort: ***", P1, "***", 0.95);
        genau("E6 Wert mit Gravis doppelt", "Passwort: ``", P1, "``", 0.95);
        genau("E6 Wert ohne Leerzeichen nach dem Verbinder", "Passwort:**", P1, "**", 0.95);
        genau("E6 Verbinder fett, Wert fett", "**Passwort:** **", P1, "**", 0.95);
        genau("E6 Gegenstueck weiter hinten", "Passwort: **", P1, " und **fett** gesetzt", 0.95);
        genau("E6 Auszeichnung schliesst nicht ab", "Passwort: ", "**" + P1, 0.95);
        genau("E6 Auszeichnung hinter dem Verbinder, Leerraum danach", "Passwort:** ", P1, 0.95);
    }

    @Test
    void entscheidung6_positiv_umbruchMitAuszeichnung() {
        genau("E6 Umbruch nach fettem Verbinder", "**Passwort:**\n", P1, 0.80);
        genau("E6 Umbruch, Wert fett", "Passwort:\n**", P1, "**", 0.80);
    }

    @Test
    void entscheidung6_negativ_dieAuszeichnungMachtAusEinemSatzKeinPasswort() {
        List<String> texte = List.of("**Passwort:** siehe Tresor", "**Passwort:** **siehe Tresor**",
                "**Passwort** siehe Tresor", "**Passwort:** `siehe Tresor`", "__Passwort__ ist abgelaufen",
                "*Passwort*: siehe KeePass", "**Passwort:** ****", "Passwort: **a b**", "**Passwort:**",
                "**Passwort:** ", "**Passwort:**\n\n" + P1, "**Passwort:** **", "Passwort: ** **",
                "**Kennwort:** wird per Post zugestellt", "**Passwort " + FUER + " svc_x:** laut Tresor",
                "***Passwort***", "Passwort: ****** ******", "**Passwort:** _siehe Tresor_",
                "**Passwort:** **12 Zeichen**");
        for (int i = 0; i < texte.size(); i++) {
            keinFund("E6 negativ " + i, texte.get(i));
        }
    }

    @Test
    void entscheidung6_negativ_zuLangeFolgenSindKeineAuszeichnung() {
        keinFund("E6 vier Sterne vor dem Verbinder", "****Passwort****: siehe Tresor");
        keinFund("E6 vier Sterne nach dem Verbinder", "Passwort:**** " + "siehe");
        // Vier Sterne sind keine Auszeichnung: der Wert ist das erste Wort mit den Sternen (geschuetzt, Fund)
        assertEquals(1, passwoerter("Passwort: ****siehe Tresor****").size(), "vier Sterne vor dem Wort");
    }

    @Test
    void entscheidung6_wertMitLeerzeichenInAuszeichnung_nurDasErsteWort_wieOhneAuszeichnung() {
        keinFund("E6 Wert mit Leerzeichen fett", "Passwort: **" + P_MIT_LEERZEICHEN + "**");
        genau("E6 erstes Wort ist der Wert", "Passwort: **", P_HUNTER, " Fuchs**", 0.95);
    }

    @Test
    void entscheidung6_dieAuszeichnungZeichenSindGeschuetzt_pseudonymisiertBleibtDieAuszeichnungStehen() {
        String text = "**Passwort:** " + P1 + " und **Kennwort**: **" + P2 + "**.";
        assertTrue("**Passwort:** [[PASSWORT_1]] und **Kennwort**: **[[PASSWORT_2]]**.".equals(pseudo(text)),
                "Auszeichnung bleibt stehen");
        roundtrip("E6", text);
    }

    // ------------------------------------------------------------------ Entscheidung 3: Zwischenwort fuer

    @Test
    void entscheidung3_zwischenwortFuerInAsciiSchreibweise_wieMitUmlaut() {
        genau("E3 fuer", "Passwort fuer svc_x: ", P1, 0.95);
        genau("E3 FUER", "PASSWORT FUER SVC_X: ", P1, 0.95);
        genau("E3 Fuer, Gleichheitszeichen", "password Fuer root=", P1, 0.95);
        keinFund("E3 ohne Leerraum davor", "Passwortfuer svc_x: " + P1);
        keinFund("E3 vier Woerter", "Passwort fuer den alten Benutzer svc_x: " + P1);
    }

    // ------------------------------------------------------------------ Entscheidung 1: Anfuehrungszeichen im Wert

    /**
     * Genau ein Fund ueber {@code inhalt} (alles zwischen den aeusseren Anfuehrungszeichen): kein Zeichen davon
     * liegt ausserhalb eines Funds, der pseudonymisierte Text enthaelt nur den Platzhalter, der Roundtrip stimmt.
     */
    private static void deckt(String id, String vor, String inhalt, String nach) {
        String text = vor + inhalt + nach;
        List<Finding> funde = passwoerter(text);
        assertEquals(1, funde.size(), id + " Zahl");
        assertEquals(vor.length(), funde.get(0).start(), id + " Beginn");
        assertEquals(vor.length() + inhalt.length(), funde.get(0).end(), id + " Ende");
        boolean[] abgedeckt = new boolean[text.length()];
        for (Finding f : STANDARD.scan(text)) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        for (int i = vor.length(); i < vor.length() + inhalt.length(); i++) {
            assertTrue(abgedeckt[i], id + " Position frei " + (i - vor.length()));
        }
        assertTrue((vor + "[[PASSWORT_1]]" + nach).equals(pseudo(text)), id + " pseudonymisiert");
        roundtrip(id, text);
    }

    @Test
    void s1_json_maskiertesAnfuehrungszeichenGehoertZumWert() {
        String teil = "hun";
        deckt("S1 JSON doppelt", "{\"password\": \"", teil + "\\\"" + P7, "\"}");
        deckt("S1 JSON einfach", "password: '", teil + "\\'" + P7, "'");
        deckt("S1 JSON Anfang", "\"password\": \"", "\\\"" + P7, "\"");
        deckt("S1 JSON Ende", "\"password\": \"", P7 + "\\\"", "\"}");
        deckt("S1 JSON mehrere", "\"password\": \"", "Zx9\\\"kq\\\"w#1", "\"");
        deckt("S1 JSON nur maskierte Zeichen am Rand", "\"password\": \"", "\\\"" + P7 + "\\\"", "\"");
        deckt("S1 doppelter Rueckstrich schliesst", "\"password\": \"", P7 + "\\\\", "\"");
        deckt("S1 dreifacher Rueckstrich maskiert", "\"password\": \"", P7 + "\\\\\\\"x", "\"");
    }

    @Test
    void s1_yamlUndSql_verdoppeltesAnfuehrungszeichenGehoertZumWert() {
        deckt("S1 YAML einfach", "password: '", "hun''" + P7, "'");
        deckt("S1 YAML Anfang", "password: '", "''" + P7, "'");
        deckt("S1 YAML Ende", "password: '", P7 + "''", "'");
        deckt("S1 YAML mehrere", "password: '", "Zx9''kq''w#1", "'");
        deckt("S1 SQL doppelt", "pw = \"", "hun\"\"" + P7, "\"");
        deckt("S1 SQL Ende, Semikolon danach", "SET pw = \"", P7 + "\"\"", "\";");
        deckt("S1 SQL Anfang", "pw = \"", "\"\"" + P7, "\"");
    }

    @Test
    void s1_zweiFundeInEinerZeileMitMaskierung() {
        String vor = "{\"password\": \"";
        String text = vor + "hun\\\"" + P7 + "\", \"pw\": \"" + P2 + "\"}";
        List<Finding> funde = passwoerter(text);
        assertEquals(2, funde.size(), "S1 zwei Funde");
        assertEquals(vor.length(), funde.get(0).start(), "S1 erster Beginn");
        assertEquals(vor.length() + ("hun\\\"" + P7).length(), funde.get(0).end(), "S1 erstes Ende");
        assertEquals(erwartet(text.substring(0, text.indexOf(P2)), P2, 0.95), funde.get(1), "S1 zweiter Fund");
        roundtrip("S1 zwei Funde", text);
    }

    @Test
    void s1_leereOderZuKurzeWerteUndFalscheWerteBleibenKeinFund() {
        keinFund("S1 leer doppelt", "\"password\": \"\"");
        keinFund("S1 leer einfach", "password: ''");
        keinFund("S1 leer mit Komma", "{\"password\": \"\", \"user\": \"svc_x\"}");
        keinFund("S1 zu kurz mit Maskierung", "\"password\": \"a\\\"b\"");
        keinFund("S1 Woerter mit Maskierung", "\"password\": \"siehe \\\"Tresor\\\"\"");
        keinFund("S1 nur Buchstaben mit Maskierung", "\"password\": \"Sonnen\\\"schein\"");
        keinFund("S1 YAML zu kurz", "password: 'a''b'");
    }

    @Test
    void s1_ohneSchliessendesZeichen_derWertBleibtOhneDasOeffnendeGeschuetzt() {
        String vor = "\"password\": \"";
        String text = vor + "hun\\\"" + P7;
        List<Finding> funde = passwoerter(text);
        assertEquals(1, funde.size(), "S1 offen Zahl");
        assertEquals(vor.length(), funde.get(0).start(), "S1 offen Beginn");
        assertEquals(text.length(), funde.get(0).end(), "S1 offen Ende");
    }

    @Test
    void s1_zufall_festerSeed_gueltigeWerteMitInnerenAnfuehrungszeichenWerdenVollstaendigGedeckt() {
        String[][] formen = {
            {"{\"password\": \"", "\\\"", "\"}"},
            {"password: '", "\\'", "'"},
            {"password: '", "''", "'"},
            {"pw = \"", "\"\"", "\";"},
        };
        Random zufall = new Random(20261125L);
        String buchstaben = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int genauEinFund = 0;
        for (int lauf = 0; lauf < 3000; lauf++) {
            String[] form = formen[lauf % formen.length];
            StringBuilder basis = new StringBuilder();
            int vorn = 2 + zufall.nextInt(10);
            for (int i = 0; i < vorn; i++) {
                basis.append(buchstaben.charAt(zufall.nextInt(buchstaben.length())));
            }
            basis.append("Ab3#");
            int hinten = 2 + zufall.nextInt(10);
            for (int i = 0; i < hinten; i++) {
                basis.append(buchstaben.charAt(zufall.nextInt(buchstaben.length())));
            }
            // Die Zeichen werden an Stellen der Basis eingefuegt, nie in ein schon eingefuegtes Zeichen hinein.
            int zahl = 1 + zufall.nextInt(3);
            int[] stellen = new int[zahl];
            for (int k = 0; k < zahl; k++) {
                stellen[k] = zufall.nextInt(basis.length() + 1);
            }
            Arrays.sort(stellen);
            StringBuilder inhalt = new StringBuilder();
            int pos = 0;
            for (int k = 0; k < zahl; k++) {
                inhalt.append(basis, pos, stellen[k]).append(form[1]);
                pos = stellen[k];
            }
            inhalt.append(basis, pos, basis.length());
            if (geschuetztGanz("S1 Zufall Lauf " + lauf, form[0], inhalt.toString(), form[2])) {
                genauEinFund++;
            }
        }
        // Rueckstriche im Wert bilden mit dem Pfad-Erkenner Funde daneben (Luecken-Regel): selten, aber erlaubt
        assertTrue(genauEinFund >= 2900, "Zahl der Laeufe mit genau einem Fund: " + genauEinFund);
    }

    /**
     * Kein Zeichen von {@code inhalt} liegt ausserhalb eines Funds, der Roundtrip stimmt; liefert, ob es genau ein
     * PASSWORT-Fund ueber den ganzen Inhalt ist (sonst teilt sich der Wert mit einem Bestandsfund, G-18).
     */
    private static boolean geschuetztGanz(String id, String vor, String inhalt, String nach) {
        String text = vor + inhalt + nach;
        List<Finding> alle = STANDARD.scan(text);
        boolean[] abgedeckt = new boolean[text.length()];
        for (Finding f : alle) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        for (int i = vor.length(); i < vor.length() + inhalt.length(); i++) {
            assertTrue(abgedeckt[i], id + " Position frei " + (i - vor.length()));
        }
        roundtrip(id, text);
        return alle.size() == 1 && alle.get(0).type().equals(GeheimnisTypen.PASSWORT)
                && alle.get(0).start() == vor.length() && alle.get(0).end() == vor.length() + inhalt.length();
    }
}
