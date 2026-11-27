package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P_BEZEICHNER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_PLATZHALTERARTIG;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static de.skillshield.core.SynthetischeGeheimnisse.T_15;
import static de.skillshield.core.SynthetischeGeheimnisse.T_16;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bekannte Grenzen des Token-Erkenners (Plan geheimnisse-erkennung, T-6; Anforderung G-16 und die uebrigen Grenzen,
 * soweit sie Tokens betreffen; Javadoc von {@code TokenRecognizer}). Jeder Test {@code bekannteGrenze...} haelt fest,
 * was absichtlich nicht oder nur eingeschraenkt erkannt wird. Meldungen nennen nur Fall-Nummern, nie einen Wert.
 */
class TokenGrenzenTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String QUELLE = "token-kontext";

    private static List<Finding> tokens(String text) {
        List<Finding> liste = new ArrayList<>();
        for (Finding f : STANDARD.scan(text)) {
            if (f.type().equals(GeheimnisTypen.TOKEN)) {
                liste.add(f);
            }
        }
        return liste;
    }

    private static Finding token(String vor, String wert, double konfidenz) {
        return new Finding(GeheimnisTypen.TOKEN, wert, vor.length(), vor.length() + wert.length(), konfidenz,
                QUELLE);
    }

    private static boolean[] geschuetzt(String text) {
        boolean[] g = new boolean[text.length()];
        for (Finding f : STANDARD.scan(text)) {
            for (int i = f.start(); i < f.end(); i++) {
                g[i] = true;
            }
        }
        return g;
    }

    private static boolean alleGeschuetzt(String text, int von, int bis) {
        boolean[] g = geschuetzt(text);
        for (int i = von; i < bis; i++) {
            if (!g[i]) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ G-16

    /** G-16: Ein Token eines Herstellers ohne Kontextwort wird nicht erkannt; mit Kontextwort schon. */
    @Test
    void bekannteGrenze_g16_herstellerTokenOhneKontextwort_keinFund_mitKontextwortEinFund() {
        String herstellerForm = "tok_" + T;
        assertEquals(List.of(), tokens("Der Zugriff mit " + herstellerForm + " ist erfolgt"));
        assertEquals(List.of(), tokens(herstellerForm));
        String vor = "Token: ";
        assertEquals(List.of(token(vor, herstellerForm, 0.95)), tokens(vor + herstellerForm));
    }

    /** G-16: Schluessel mit Kontextwort in einer Zeile ohne Verbinder werden nicht erkannt. */
    @Test
    void bekannteGrenze_g16_kontextwortOhneVerbinderUndWertDirektDahinter_keinFund() {
        assertEquals(List.of(), tokens("Token " + T));
        assertEquals(List.of(), tokens("Secret " + T));
        assertEquals(List.of(), tokens("API Key " + T));
    }

    // ------------------------------------------------------------------ Bearer, Authorization (Schema)

    /** D-9 und Plan Kapitel 11 Punkt 5: {@code Bearer} ohne Doppelpunkt ist wahrscheinlich (0,80), nicht sicher. */
    @Test
    void bekannteGrenze_bearerOhneDoppelpunkt_nurWahrscheinlich() {
        assertEquals(List.of(token("Bearer ", T, 0.80)), tokens("Bearer " + T));
        assertEquals(List.of(token("Authorization: Bearer ", T, 0.95)), tokens("Authorization: Bearer " + T));
        assertEquals(List.of(token("Authorization Bearer ", T, 0.80)), tokens("Authorization Bearer " + T));
    }

    /** Hinter {@code Bearer} und {@code Authorization} gelten mindestens 16 Zeichen; ein kuerzerer Token bleibt. */
    @Test
    void bekannteGrenze_tokenUnterSechzehnZeichenHinterBearerUndAuthorization_bleibtSichtbar() {
        assertEquals(List.of(), tokens("Authorization: Bearer " + T_15));
        assertEquals(List.of(), tokens("Authorization: Basic " + T_15));
        assertEquals(List.of(), tokens("Bearer " + T_15));
        assertEquals(List.of(), tokens("Authorization: " + T_15));
        assertEquals(1, tokens("Authorization: Bearer " + T_16).size());
    }

    /** {@code Basic Wert} und {@code Token Wert} ohne {@code Authorization:} davor sind keine Schema-Formen. */
    @Test
    void bekannteGrenze_basicUndTokenOhneAuthorization_keineSchemaForm() {
        assertEquals(List.of(), tokens("Basic " + T));
        assertEquals(List.of(), tokens("Header: Basic " + T));
        assertEquals(List.of(), tokens("Das Token " + T + " ist gesetzt"));
        assertEquals(List.of(), tokens("Authorization failed, Basic " + T));
    }

    /**
     * Das Schema in Anfuehrungszeichen: Seit Durchlauf 2 (Entscheidung 3) bleibt das Schema-Wort Klartext und nur der
     * Wert ist der Fund, auch ueber 256 Zeichen (siehe {@code TokenSchemaStrengeTest}).
     */
    @Test
    void schemaInAnfuehrungszeichen_nurDerWertIstDerFund() {
        String vor = "\"Authorization\": \"Bearer ";
        assertEquals(List.of(token(vor, T, 0.95)), tokens(vor + T + "\""));
        assertTrue(alleGeschuetzt(vor + T + "\"", vor.length(), vor.length() + T.length()), "geschuetzt");
    }

    /** Der Wert hinter {@code Authorization:} steht in derselben Zeile; in der naechsten Zeile ist er kein Fund. */
    @Test
    void bekannteGrenze_wertInDerNaechstenZeileHinterAuthorization_keinFund() {
        assertEquals(List.of(), tokens("Authorization:\n" + T));
        assertEquals(List.of(), tokens("Authorization:\r\n   Basic " + T));
        assertEquals(List.of(), tokens("Authorization: Bearer\n" + T));
        // Gegenprobe: Bearer ohne Verbinder in der Folgezeile ist ein Kontext fuer sich (0,80)
        assertEquals(1, tokens("Authorization:\nBearer " + T).size());
    }

    /** {@code Bearer} und {@code Authorization} gelten nie mit K2 bis K4. */
    @Test
    void bekannteGrenze_bearerUndAuthorizationNieMitIstTabelleOderElement_keinFund() {
        assertEquals(List.of(), tokens("Authorization ist " + T));
        assertEquals(List.of(), tokens("| Authorization | " + T + " |"));
        assertEquals(List.of(), tokens("Authorization\t" + T));
        assertEquals(List.of(), tokens("<authorization>" + T + "</authorization>"));
        assertEquals(List.of(), tokens("Bearer ist " + T));
        assertEquals(List.of(), tokens("<bearer>" + T + "</bearer>"));
    }

    // ------------------------------------------------------------------ Grenzen wie beim Passwort

    /** G-1: Token ohne Kontextwort, Wert vor dem Kontextwort. */
    @Test
    void bekannteGrenze_g1_tokenOhneKontextwortOderVorDemKontextwort_keinFund() {
        assertEquals(List.of(), tokens("Anmeldung mit svc_x und " + T));
        assertEquals(List.of(), tokens(T + " (Token)"));
        assertEquals(List.of(), tokens("Zugangsdaten: svc_x / " + T));
    }

    /** G-2: Werte nur aus Buchstaben oder Ziffern, oder mit dem einzigen Sonderzeichen am Ende. */
    @Test
    void bekannteGrenze_g2_wertNurBuchstabenOderZiffern_keinFund() {
        assertEquals(List.of(), tokens("Token: abcdefghijklmnopqrstuvwxyz"));
        assertEquals(List.of(), tokens("Token: 12345678"));
        assertEquals(List.of(), tokens("Secret: Sonnenschein!"));
    }

    /** G-7: Endet der Wert auf ein Satzzeichen, bleibt es sichtbar. */
    @Test
    void bekannteGrenze_g7_satzzeichenAmEndeDesWerts_bleibenSichtbar() {
        String vor = "Token: ";
        assertEquals(List.of(token(vor, T, 0.95)), tokens(vor + T + "."));
        assertEquals(List.of(token(vor, T, 0.95)), tokens(vor + T + ")"));
        assertEquals(List.of(token(vor, T, 0.95)), tokens(vor + T + ";"));
    }

    /** G-9: Werte mit {@code $ % { < [} am Anfang bleiben (Variablen und Vorlagen gehen vor). */
    @Test
    void bekannteGrenze_g9_wertMitVariablenzeichenAmAnfang_keinFund() {
        for (String anfang : List.of("$", "%", "{", "<", "[")) {
            assertEquals(List.of(), tokens("Token: " + anfang + T), "Anfang " + anfang.length());
        }
    }

    /** G-13: Werte mit Leerzeichen werden nur in Anfuehrungszeichen erfasst, sonst nur das erste Wort. */
    @Test
    void bekannteGrenze_g13_wertMitLeerzeichen_nurDasErsteWort() {
        String vor = "Token: ";
        String text = vor + T + " Testwert7Zweiter";
        assertEquals(List.of(token(vor, T, 0.95)), tokens(text));
        assertFalse(alleGeschuetzt(text, vor.length(), text.length()), "der Rest bleibt sichtbar");
    }

    /** G-14: Ein Wort mit Gross-, Kleinbuchstabe und Ziffer hinter {@code ist} gilt als Token. */
    @Test
    void bekannteGrenze_g14_bezeichnerHinterIst_gilltAlsToken() {
        String vor = "Token ist ";
        assertEquals(List.of(token(vor, P_BEZEICHNER, 0.80)), tokens(vor + P_BEZEICHNER));
    }

    /** G-19: Ein Kontextwort innerhalb eines verworfenen Werts ohne Leerraum wird nicht mehr ausgewertet. */
    @Test
    void bekannteGrenze_g19_kontextwortInVerworfenemWert_keinFund() {
        assertEquals(List.of(), tokens("token:${TOKEN},secret:" + T));
        // Gegenprobe: mit Leerzeichen ist das zweite Kontextwort ein eigener Fund
        assertEquals(1, tokens("token:${TOKEN}, secret: " + T).size());
    }

    /** G-20: Platzhalterartige Werte mit Sonderzeichen oder Ziffern bleiben Funde (Schutz geht vor). */
    @Test
    void bekannteGrenze_g20_platzhalterartigeWerteMitZiffernOderSonderzeichen_sindFunde() {
        for (String wert : P_PLATZHALTERARTIG) {
            assertEquals(1, tokens("Secret: " + wert).size(), "Wert der Laenge " + wert.length());
        }
        assertEquals(1, tokens("api_key: REPLACE_ME_123").size());
        assertEquals(1, tokens("Secret: @@TOKEN9@@").size());
    }

    /** W3 (e): Ein Token in der Form eines ISO-Zeitstempels bleibt sichtbar. */
    @Test
    void bekannteGrenze_zeitstempelAlsWert_keinFund() {
        assertEquals(List.of(), tokens("Token: 2026-03-12T10:15:00Z"));
        assertEquals(List.of(), tokens("Secret: 2026-03-12T10:15:00+01:00"));
    }

    /** Eine einzelne {@code *} oder {@code _} um den Wert bleibt Teil des Funds (Auszeichnung, wie beim Passwort). */
    @Test
    void bekannteGrenze_einzelnerStern_gehoertZumFund() {
        String vor = "Token: ";
        String wert = "*" + T + "*";
        assertEquals(List.of(token(vor, wert, 0.95)), tokens(vor + wert));
        // Gegenprobe: zwei Sterne werden als Auszeichnung uebersprungen
        String doppelt = "**" + T + "**";
        assertEquals(List.of(token(vor + "**", T, 0.95)), tokens(vor + doppelt));
    }

    /** G-18: Ein bestehender Fund ganz im Wert bleibt unveraendert; der Token zerfaellt in Stuecke um ihn. */
    @Test
    void bekannteGrenze_g18_bestehenderFundImWert_dasGeheimnisZerfaelltInStuecke() {
        String email = "erika.beispiel" + "@" + "kv-beispiel.example";
        String wert = "Zq9" + (char) 0xA7 + email + (char) 0x3E + "y";
        String vor = "Token: ";
        String text = vor + wert;
        List<Finding> alle = STANDARD.scan(text);
        assertTrue(alle.stream().anyMatch(f -> f.type().equals(FindingType.EMAIL)), "der E-Mail-Fund bleibt");
        assertTrue(alle.stream().anyMatch(f -> f.type().equals(GeheimnisTypen.TOKEN)), "Stuecke als TOKEN");
        assertTrue(alleGeschuetzt(text, vor.length(), text.length()), "kein Zeichen des Werts bleibt frei");
        assertFalse(alle.stream().anyMatch(f -> f.type().equals(GeheimnisTypen.TOKEN)
                && f.originalValue().equals(wert)), "kein Fund ueber den ganzen Wert");
    }
}
