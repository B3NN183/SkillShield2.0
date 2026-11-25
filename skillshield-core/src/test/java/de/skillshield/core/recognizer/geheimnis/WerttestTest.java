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
import static de.skillshield.core.SynthetischeGeheimnisse.mischwert;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.recognizer.geheimnis.Werttest.Verbinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Wert-Test W1 bis W6 (Anforderung D-8, AK-10 bis AK-12, AK-14, AK-16). Jede Bedingung, jede Fachbegriff-,
 * Versions-, Ticket-, Zahlwort- und Zeitstempelform und die Grenzlaengen. Meldungen nennen nur Fall-Nummern und
 * Zahlen, nie einen Wert (Plan Kapitel 6.1). Die Werte hier sind Fachformen ohne Geheimnischarakter oder stammen
 * aus {@code SynthetischeGeheimnisse}.
 */
class WerttestTest {

    private static boolean stark(String wert) {
        return Werttest.besteht(wert, 0, wert.length(), Verbinder.STARK, false);
    }

    private static boolean schwach(String wert) {
        return Werttest.besteht(wert, 0, wert.length(), Verbinder.SCHWACH, false);
    }

    private static boolean angefuehrt(String wert) {
        return Werttest.besteht(wert, 0, wert.length(), Verbinder.STARK, true);
    }

    /** Liefert die Nummern der Faelle, bei denen das Ergebnis nicht der Erwartung entspricht. */
    private static List<Integer> abweichungen(List<String> faelle, boolean erwartet, boolean schwacherVerbinder) {
        List<Integer> schlecht = new ArrayList<>();
        for (int i = 0; i < faelle.size(); i++) {
            boolean ist = schwacherVerbinder ? schwach(faelle.get(i)) : stark(faelle.get(i));
            if (ist != erwartet) {
                schlecht.add(i);
            }
        }
        return schlecht;
    }

    // ------------------------------------------------------------------ W1 Laenge

    @Test
    void w1_untergrenzeSechsZeichen_fuenfZeichenSindZuKurz() {
        assertEquals(5, P5.length());
        assertEquals(6, P6.length());
        assertFalse(stark(P5), "fuenf Zeichen");
        assertTrue(stark(P6), "sechs Zeichen");
        assertTrue(stark(P7), "sieben Zeichen");
    }

    @Test
    void w1_obergrenze4096_4097IstZuLang() {
        assertTrue(stark(mischwert(4096)), "4096 Zeichen");
        assertFalse(stark(mischwert(4097)), "4097 Zeichen");
        assertEquals(4096, Werttest.MAX_LAENGE);
    }

    @Test
    void w1_inAnfuehrungszeichen_obergrenze256() {
        assertTrue(angefuehrt(mischwert(256)), "256 Zeichen in Anfuehrung");
        assertFalse(angefuehrt(mischwert(257)), "257 Zeichen in Anfuehrung");
        assertFalse(angefuehrt(P5), "fuenf Zeichen in Anfuehrung");
        assertTrue(angefuehrt(P6), "sechs Zeichen in Anfuehrung");
        assertEquals(256, Werttest.MAX_LAENGE_ANFUEHRUNG);
    }

    @Test
    void w1_dieLaengeZaehltCodepoints_einSurrogatpaarIstEinZeichen() {
        String emoji = new String(Character.toChars(0x1F600));
        String fuenfPlusEmoji = "Ab3xy" + emoji;
        assertEquals(6, fuenfPlusEmoji.codePointCount(0, fuenfPlusEmoji.length()));
        assertTrue(stark(fuenfPlusEmoji), "sechs Codepoints, sieben UTF-16-Einheiten");
        String zuKurz = "Ab3x" + emoji;
        assertFalse(stark(zuKurz), "fuenf Codepoints, sechs UTF-16-Einheiten");
    }

    @Test
    void grenzenUndLeereBereiche_liefernFalse_ohneAusnahme() {
        assertFalse(Werttest.besteht(P1, 0, 0, Verbinder.STARK, false), "leerer Bereich");
        assertFalse(Werttest.besteht(P1, 3, 1, Verbinder.STARK, false), "umgekehrter Bereich");
        assertFalse(Werttest.besteht(P1, -1, 4, Verbinder.STARK, false), "negativer Beginn");
        assertFalse(Werttest.besteht(P1, 0, P1.length() + 1, Verbinder.STARK, false), "Ende hinter dem Text");
        assertTrue(Werttest.besteht("x " + P1 + " y", 2, 2 + P1.length(), Verbinder.STARK, false), "Teilbereich");
    }

    // ------------------------------------------------------------------ W2 Zeichenmischung

    @Test
    void w2_keinFund_reineBuchstabenReineZahlenUndWortMitAusrufezeichenAmEnde() {
        List<String> faelle = List.of("abgelaufen", "Sonnenschein", "123456", "falsch!", "********", "!!!!!!!!",
                "E-Mail", "nicht-gesetzt", "Sommer!", "abcdef", "12345678", "ab_cd-ef", "ab.cd,ef");
        assertEquals(List.of(), abweichungen(faelle, false, false));
    }

    @Test
    void w2_fund_buchstabeMitZifferOderSonderzeichenNichtAmEnde() {
        List<String> faelle = List.of(P1_OHNE_AUSRUF, P_DOLLAR, P3, P1, P2, P6, P7, P_HUNTER, "a!bcde", "a?bcde",
                "ab%cde", "ab&cde", "ab=cde", "ab*cde", "ab+cde", "ab#cde", "ab~cde", "ab@cde", "ab^cde",
                "ab|cde", "ab>cde", "ab" + (char) 0xA7 + "cde", "ab" + (char) 0x20AC + "cde");
        assertEquals(List.of(), abweichungen(faelle, true, false));
    }

    @Test
    void w2_dasLetzteZeichenZaehltNichtAlsSonderzeichen_dieZeichenMinusPunktKommaZaehlenNie() {
        assertFalse(stark("abcde!"), "Sonderzeichen nur am Ende");
        assertFalse(stark("abcde*"), "Stern nur am Ende");
        assertTrue(stark("abcd1!"), "Ziffer vorhanden");
        assertFalse(stark("ab-cde"), "Bindestrich zaehlt nicht");
        assertFalse(stark("ab_cde"), "Unterstrich zaehlt nicht");
        assertFalse(stark("ab.cde"), "Punkt zaehlt nicht");
        assertFalse(stark("ab,cde"), "Komma zaehlt nicht");
        assertTrue(Werttest.istSonderzeichen('!'));
        assertFalse(Werttest.istSonderzeichen('-'));
        assertFalse(Werttest.istSonderzeichen(0x1F600), "Codepoint ausserhalb der Liste");
    }

    // ------------------------------------------------------------------ W3 Formen

    @Test
    void w3a_versionsformen_sindKeinWert() {
        List<String> faelle = List.of("v4.2.1", "V4.2.1", "4.2.1-rc1", "2.0-rc1", "v4.2", "v10.20.30", "v1.0-beta2",
                "10.20-rc1x");
        assertEquals(List.of(), abweichungen(faelle, false, false));
        assertTrue(Werttest.istVersion("4.2"));
        assertFalse(Werttest.istVersion("4"), "nur ein Block");
        assertFalse(Werttest.istVersion("4."), "Punkt ohne Block");
        assertFalse(Werttest.istVersion("4.2-"), "Bindestrich ohne Rest");
        assertFalse(Werttest.istVersion("4.2-a-b"), "zwei Bindestriche");
    }

    @Test
    void w3a_kontrolle_eineVersionMitAnhangOderEinWortMitPunktIstKeineVersion() {
        assertTrue(stark("v4.2.1x"), "Anhang ohne Bindestrich");
        assertTrue(stark("Sommer.2026a"), "Wort mit Punkt");
    }

    @Test
    void w3b_ticketUndAenderungsnummern_sindKeinWert() {
        List<String> faelle = List.of("CHG0012345", "INC0004711", "JIRA-1234", "AB123456", "ABCDEF-123456",
                "IT-12345");
        assertEquals(List.of(), abweichungen(faelle, false, false));
        assertTrue(Werttest.istTicketnummer("CHG0012345"));
        assertFalse(Werttest.istTicketnummer("A12345"), "ein Grossbuchstabe");
        assertFalse(Werttest.istTicketnummer("ABCDEFG123"), "sieben Grossbuchstaben");
        assertFalse(Werttest.istTicketnummer("AB12"), "zwei Ziffern");
        assertFalse(Werttest.istTicketnummer("AB1234x"), "Buchstabe am Ende");
    }

    @Test
    void w3b_kontrolle_einWertMitKleinbuchstabenOderAnhangIstKeineTicketnummer() {
        assertTrue(stark("KVBSP1234x"), "Anhang");
        assertTrue(stark("Chg0012345"), "Kleinbuchstaben");
    }

    @Test
    void w3c_zahlwortVerbuende_sindKeinWert_auchMitUmlautUndEszett() {
        String taegig = "90-t" + (char) 0xE4 + "gig";
        String gross = "90-gr" + (char) 0xF6 + (char) 0xDF + "er";
        List<String> faelle = List.of("8-stellig", taegig, gross, "12-monatig", "3-fach");
        assertEquals(List.of(), abweichungen(faelle, false, false));
        assertTrue(Werttest.istZahlwort(taegig), "Umlaut als Kleinbuchstabe");
        assertTrue(Werttest.istZahlwort(gross), "Eszett als Kleinbuchstabe");
        assertFalse(Werttest.istZahlwort("8-Stellig"), "Grossbuchstabe");
        assertFalse(Werttest.istZahlwort("8-"), "nichts nach dem Bindestrich");
        assertFalse(Werttest.istZahlwort("-stellig"), "keine Ziffer davor");
    }

    @Test
    void w3c_kontrolle_einWertMitZifferImWortteilIstKeinZahlwort() {
        assertTrue(stark("90-tage7"), "Ziffer im Wortteil");
        assertTrue(stark("90-Tage"), "Grossbuchstabe im Wortteil");
    }

    @Test
    void w3d_fachbegriffe_allePassenInGrossUndKleinschreibung() {
        for (int i = 0; i < Werttest.FACHBEGRIFFE.size(); i++) {
            String klein = Werttest.FACHBEGRIFFE.get(i);
            assertEquals(klein, klein.toLowerCase(Locale.ROOT), "Eintrag nicht in Kleinbuchstaben, Index " + i);
            assertTrue(GeheimnisZeichen.inListe(klein, Werttest.FACHBEGRIFFE), "klein, Index " + i);
            assertTrue(GeheimnisZeichen.inListe(klein.toUpperCase(Locale.ROOT), Werttest.FACHBEGRIFFE),
                    "gross, Index " + i);
            if (klein.length() >= Werttest.MIN_LAENGE) {
                assertFalse(stark(klein), "als Wert klein, Index " + i);
                assertFalse(stark(klein.toUpperCase(Locale.ROOT)), "als Wert gross, Index " + i);
            }
        }
        assertEquals(32, Werttest.FACHBEGRIFFE.size());
    }

    @Test
    void w3d_dieGenanntenBeispiele_sindKeinWert() {
        List<String> faelle = List.of("SHA-256", "AES-256", "TLS1.2", "Windows11", "SHA512", "RSA-4096", "NTLMv2",
                "Argon2id", "Office365", "Server2022", "base64", "UTF-8");
        // "UTF-8" hat nur fuenf Zeichen und ist schon durch W1 kein Wert; der letzte Eintrag prueft das mit.
        assertEquals(List.of(), abweichungen(faelle, false, false));
    }

    @Test
    void w3d_kontrolle_einFachbegriffMitAnhangIstEinWert() {
        assertTrue(stark("SHA-256x"), "Anhang");
        assertTrue(stark("Windows11!x"), "Anhang mit Sonderzeichen");
    }

    @Test
    void w3e_isoZeitstempel_sindKeinWert() {
        List<String> faelle = List.of("2026-03-12T10:15:00Z", "2026-03-12T10:15:00+01:00", "2026-03-12T10:15",
                "2026-03-12T10:15:00.123Z", "2026-03-12T10:15:00,5-05", "2026-03-12T10:15:00", "2026-03-12T10:15Z");
        assertEquals(List.of(), abweichungen(faelle, false, false));
        assertTrue(Werttest.istZeitstempel("2026-03-12T10:15:00Z"));
        assertFalse(Werttest.istZeitstempel("2026-03-12"), "nur das Datum");
        assertFalse(Werttest.istZeitstempel("2026-03-12T10"), "nur die Stunde");
        assertFalse(Werttest.istZeitstempel("2026-03-12T10:15:00Zx"), "Anhang");
        assertFalse(Werttest.istZeitstempel("2026-03-12T10:15:00.Z"), "Bruchteil ohne Ziffer");
        assertFalse(Werttest.istZeitstempel("2026-03-12T10:15:00+01:0"), "Zonenversatz zu kurz");
        assertFalse(Werttest.istZeitstempel("2026-3-12T10:15:00Z"), "Monat einstellig");
    }

    // ------------------------------------------------------------------ W4 Pfad- und Adressformen

    @Test
    void w4_pfadUndAdressformen_sindKeinWert() {
        List<String> faelle = List.of("/etc/shad0w", "\\\\srv\\share1", "~/dateiname1", "./skript1.sh",
                "../ordner1/x", "C:\\Tresor\\pw1.kdbx", "d:\\Ordner1", "https://tresor.example/e1",
                "ab://cd1ef", "\\Ordner1x");
        assertEquals(List.of(), abweichungen(faelle, false, false));
    }

    @Test
    void w4_kontrolle_einSchraegstrichMitteninnenOderEinLaufwerkOhneBackslashIstErlaubt() {
        assertTrue(stark("ab/cd1ef"), "Schraegstrich innen");
        assertTrue(stark("C:Tresor1x"), "Doppelpunkt ohne Backslash");
        assertTrue(stark("x.y/z1abc"), "Punkt vor Schraegstrich innen");
    }

    // ------------------------------------------------------------------ W5 Masken, Variablen, Vorlagen

    @Test
    void w5_variablenMaskenUndVorlagen_sindKeinWert() {
        List<String> faelle = List.of("${DB_PASSWORD}", "$Sommer2026x", "%DB_PASSWORD%", "{{vault_pw1}}",
                "<geheim1x>", "[entfernt1]", "[[PASSWORT_1]]", "xxxxxx", "******", "111111", "aaaaaa", "!!!!!!!!");
        assertEquals(List.of(), abweichungen(faelle, false, false));
    }

    @Test
    void w5_kontrolle_einZeichenInnenOderEinWiederholtesMitAnderemEndeIstErlaubt() {
        assertTrue(stark("ab$cde1"), "Dollar innen");
        assertTrue(stark("xxxxx1"), "fast nur ein Zeichen");
        assertTrue(stark("ab[cde1"), "Klammer innen");
    }

    // ------------------------------------------------------------------ W6 schwaechere Verbinder

    @Test
    void w6_schwach_drittKlassenUndMindestensAchtZeichen() {
        assertTrue(schwach(P1), "elf Zeichen, drei Klassen und Sonderzeichen am Ende");
        assertTrue(schwach(P1_OHNE_AUSRUF), "drei Klassen ohne Sonderzeichen");
        assertTrue(schwach(P2), "vier Klassen");
        assertTrue(schwach("Ab3!xyzq"), "genau acht Zeichen mit vier Klassen");
        assertFalse(schwach(P_HUNTER), "nur Klein und Ziffer");
        assertFalse(schwach(P6), "sechs Zeichen");
        assertFalse(schwach(P7), "sieben Zeichen");
        assertFalse(schwach("abcdefg1h"), "zwei Klassen");
        assertFalse(schwach("ABCDEFG1H"), "zwei Klassen gross");
    }

    @Test
    void w6_gilt_nurFuerSchwachDerStarkeVerbinderPrueftW1BisW5() {
        assertTrue(stark(P_HUNTER), "stark: zwei Klassen genuegen");
        assertTrue(stark(P6), "stark: sechs Zeichen genuegen");
        assertFalse(schwach(P_HUNTER), "schwach: zwei Klassen");
        assertFalse(schwach(P6), "schwach: sechs Zeichen");
    }

    @Test
    void w6_dasLetzteSonderzeichenZaehltAuchHierNichtAlsKlasse() {
        assertFalse(schwach("abcdefg1!"), "Klein, Ziffer, Sonderzeichen nur am Ende");
        assertTrue(schwach("Abcdefg1!"), "Gross, Klein, Ziffer");
    }

    @Test
    void konstanten_stimmenMitDerAnforderungUeberein() {
        assertEquals(6, Werttest.MIN_LAENGE);
        assertEquals(8, Werttest.MIN_LAENGE_SCHWACH);
        assertEquals(8, Werttest.MIN_LAENGE_SICHER);
    }
}
