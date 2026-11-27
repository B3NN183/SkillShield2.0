package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P6;
import static de.skillshield.core.SynthetischeGeheimnisse.P7;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static de.skillshield.core.SynthetischeGeheimnisse.T_15;
import static de.skillshield.core.SynthetischeGeheimnisse.T_16;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeGeheimnisse;
import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Token nach Kontextwort und Schema-Formen (Plan geheimnisse-erkennung, T-6): AK-9 (Passwort und Token in einer
 * Zeile), AK-17 (Kontextwoerter), AK-18 (Schema-Formen), AK-19 (UUID), AK-30 (Token-Teil), AK-43 (Einbindung, zehn
 * Funde, Typnamen, Reihenfolge der zehn Erkenner) und das Zusammenspiel mit dem {@code PasswortRecognizer}.
 *
 * <p>Alle Tests laufen gegen {@code TextScanner.standard()}; Funde werden mit Typ, Bereich, Konfidenz und Quelle
 * verglichen. Meldungen nennen nur Fall-Nummern und Zahlen, nie einen Text oder Wert (Plan Kapitel 6.1).
 */
class TokenKontextTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String QUELLE = "token-kontext";
    private static final String UE = String.valueOf((char) 0xFC);
    private static final String FUER = "f" + UE + "r";
    private static final String NBSP = String.valueOf((char) 0xA0);

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> tokens(String text) {
        List<Finding> liste = new ArrayList<>();
        for (Finding f : STANDARD.scan(text)) {
            if (f.type().equals(GeheimnisTypen.TOKEN)) {
                liste.add(f);
            }
        }
        return liste;
    }

    private static Finding erwartet(String vor, String wert, double konfidenz) {
        return new Finding(GeheimnisTypen.TOKEN, wert, vor.length(), vor.length() + wert.length(), konfidenz,
                QUELLE);
    }

    /** Genau ein TOKEN-Fund ueber {@code wert} zwischen {@code vor} und {@code nach}. */
    private static void genau(String id, String vor, String wert, String nach, double konfidenz) {
        String text = vor + wert + nach;
        assertEquals(List.of(erwartet(vor, wert, konfidenz)), tokens(text), id);
        ConfidenceLevel stufe = konfidenz >= 0.90 ? ConfidenceLevel.SICHER : ConfidenceLevel.WAHRSCHEINLICH;
        assertEquals(stufe, ConfidenceLevel.of(konfidenz), id);
    }

    private static void genau(String id, String vor, String wert, double konfidenz) {
        genau(id, vor, wert, "", konfidenz);
    }

    private static void keinFund(String id, String text) {
        assertEquals(List.of(), tokens(text), id);
    }

    private static String pseudo(String text) {
        return PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text)).text();
    }

    private static void roundtrip(String id, String text) {
        PseudonymizationResult p = PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text));
        String zurueck = REIDENTIFIER.restore(p.text(), p.mapping()).text();
        assertTrue(text.equals(zurueck), "Roundtrip nicht zeichengleich: " + id);
    }

    // ------------------------------------------------------------------ AK-17 Kontextwoerter

    @Test
    void ak17_kontextwoerterMitDoppelpunkt_genauEinFundSicher() {
        List<String> woerter = List.of("Token", "access_token", "accessToken", "refresh_token", "idToken", "API-Key",
                "apikey", "api_key", "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret");
        for (int i = 0; i < woerter.size(); i++) {
            genau("AK-17 Doppelpunkt " + i, woerter.get(i) + ": ", T, 0.95);
        }
    }

    @Test
    void ak17_weitereStaemmeUndSchreibweisen_genauEinFund() {
        List<String> woerter = List.of("secretkey", "secret_key", "SecretKey", "accesskey", "access-key",
                "privatekey", "private_key", "TOKEN", "token", "Bearer-Token", "ID_TOKEN", "ApiKey", "API_KEY",
                "x-api-key", "api key", "Api Key", "SECRET", "Client-Secret", "client_secret_key", "OAuth-Token",
                "JWT_TOKEN", "__Token__", "**Token**");
        for (int i = 0; i < woerter.size(); i++) {
            genau("AK-17 weitere " + i, woerter.get(i) + ": ", T, 0.95);
        }
    }

    @Test
    void ak17_gleichheitszeichenUndLeerraumUmDenVerbinder_genauEinFund() {
        genau("AK-17 =", "client_secret=", T, 0.95);
        genau("AK-17 = ", "token = ", T, 0.95);
        genau("AK-17 :", "Secret:", T, 0.95);
        genau("AK-17 :  ", "API_KEY :  ", T, 0.95);
        genau("AK-17 NBSP", "Token:" + NBSP, T, 0.95);
        genau("AK-17 Ende", "Token: ", T, ".", 0.95);
        genau("AK-17 Komma", "Token: ", T, ", Rest", 0.95);
    }

    @Test
    void ak17_anfuehrungszeichen_derInhaltIstDerWert() {
        genau("AK-17 JSON", "\"token\": \"", T, "\"", 0.95);
        genau("AK-17 JSON kompakt", "{\"access_token\":\"", T, "\"}", 0.95);
        genau("AK-17 einfach", "secret = '", T, "'", 0.95);
        genau("AK-17 Gravis", "api_key: `", T, "`", 0.95);
        genau("AK-17 Leerzeichen im Wert", "Token: \"", "Testwert 7 Abcd Efgh", "\"", 0.95);
    }

    @Test
    void ak17_abgrenzungDerKontextwoerter_keinFund() {
        List<String> falsch = List.of("Tokenl" + (char) 0xE4 + "nge: ", "Token-Ablauf: ", "Secretary: ", "Tokens: ",
                "Secrets: ", "Tokenizer: ", "Token_Typ: ", "Keyword: ", "Key: ", "Api: ", "Passkey: ",
                "Secretkeys: ", "Tokenlaenge: ", "Secret-Rotation: ", "t-o-k-e-n: ", "t_o_k_e_n: ");
        for (int i = 0; i < falsch.size(); i++) {
            keinFund("AK-17 abgegrenzt " + i, falsch.get(i) + T);
        }
        keinFund("AK-17 Strich am Ende", "Token-: " + T);
    }

    @Test
    void ak17_trennerRegel_hoechstensDreiUebersprungeneTrenner() {
        genau("drei Trenner", "t-o-k-en: ", T, 0.95);
        genau("zwei Trenner", "sec_ret_key: ", T, 0.95);
        keinFund("vier Trenner", "t-o-k-e-n: " + T);
        keinFund("Trenner am Ende", "access_token_: " + T);
    }

    @Test
    void ak17_apiKeyMitLeerzeichen_nurMitGenauEinemLeerraumUndKey() {
        genau("API Key", "Der API Key: ", T, 0.95);
        genau("Tabulator", "API\tKey: ", T, 0.95);
        genau("NBSP", "API" + NBSP + "Key: ", T, 0.95);
        genau("kleingeschrieben", "api key: ", T, 0.95);
        keinFund("zwei Leerzeichen", "API  Key: " + T);
        keinFund("Key allein", "Key: " + T);
        keinFund("Zeilenumbruch", "API\nKey: " + T);
        keinFund("anderes Wort", "Der Key: " + T);
        keinFund("Key mit Zusatz", "API Keys: " + T);
    }

    @Test
    void ak17_zwischenteil_kontextwortFuerDenDienst() {
        genau("Zwischenteil", "Token " + FUER + " svc_x: ", T, 0.95);
        genau("Zwischenteil englisch", "Secret for the app: ", T, 0.95);
        keinFund("vier Woerter", "Token " + FUER + " den alten Benutzer svc_x: " + T);
    }

    @Test
    void ak17_zeilenumbruchHinterDemVerbinder_wahrscheinlich() {
        genau("LF", "Token:\n", T, 0.80);
        genau("CR LF", "access_token =\r\n    ", T, 0.80);
        keinFund("Leerzeile", "Token:\n\n" + T);
    }

    @Test
    void ak17_schwaecherVerbinder_k2k3k4_wahrscheinlich() {
        genau("K2 ist", "Token ist ", T, 0.80);
        genau("K2 lautet", "Das Secret lautet ", T, 0.80);
        genau("K2 is", "api_key is ", T, 0.80);
        genau("K3 Tabelle", "| Secret | ", T, " |", 0.80);
        genau("K3 Tabulator", "Token\t", T, 0.80);
        genau("K4 Element", "<token>", T, "</token>", 0.80);
        genau("K4 apiKey", "<apiKey>", T, "</apiKey>", 0.80);
        genau("K2 mit Zwischenteil", "Das Token " + FUER + " den Benutzer svc_x ist ", T, 0.80);
        // Hinter K2 gilt W6 wie beim Passwort (Entscheidung des Nutzers nach Durchlauf 2): zwei Klassen sind kein Fund
        keinFund("K2 ohne Klassenmischung ab 16", "Token ist " + T.toLowerCase(Locale.ROOT));
        keinFund("K2 ohne Klassenmischung unter 16", "Token ist " + T_15.toLowerCase(Locale.ROOT));
        keinFund("K2 siehe", "Token ist abgelaufen");
    }

    @Test
    void ak17_kurzeWerte_sechsOderSiebenZeichen_wahrscheinlich() {
        genau("sechs", "Token: ", P6, 0.80);
        genau("sieben", "api_key=", P7, 0.80);
    }

    @Test
    void ak17_wertTest_keinFundBeiWortenZahlenVersionenMaskenUndPfaden() {
        List<String> werte = List.of("abgelaufen", "ung" + UE + "ltig", "3600", "v4.2.1", "CHG0012345", "<geheim>",
                "${TOKEN}", "%TOKEN%", "{{ vault_token }}", "[entfernt]", "/etc/token", "C:\\Tresor\\token.txt",
                "xxxxxxxx", "********", "Ab3!x", "2026-03-12T10:15:00Z", "SHA-256", "8-stellig", "[[TOKEN_1]]",
                "https://example.invalid/token");
        for (int i = 0; i < werte.size(); i++) {
            keinFund("Wert-Test " + i, "Token: " + werte.get(i));
        }
    }

    @Test
    void ak17_saetzeUeberTokens_keinFund() {
        List<String> saetze = List.of("Token abgelaufen", "Token: ung" + UE + "ltig (HTTP 401)",
                "Der Token l" + (char) 0xE4 + "uft nach 3600 Sekunden ab.", "Secret: nicht vorhanden",
                "Das Token ist nicht mehr g" + UE + "ltig, siehe CHG0012345.", "access_token: wird per Post geliefert",
                "API Key: siehe Tresor", "Secret: siehe KeePass", "Bearer-Token abgelaufen",
                "Token: " + "[[TOKEN_1]]", "Das Token ist abgelaufen", "Client Secret rotiert am 2026-03-12");
        for (int i = 0; i < saetze.size(); i++) {
            keinFund("Satz " + i, saetze.get(i));
        }
    }

    @Test
    void ak17_hashesOhneKontextwort_keinFund_mitKontextwortEinFund() {
        String hex = SynthetischeGeheimnisse.hex64();
        keinFund("Token-Hash", "Token-Hash: " + hex);
        keinFund("Secret-Fingerprint", "Secret-Fingerprint: " + hex);
        keinFund("Hash allein", hex);
        genau("Hex mit Kontextwort", "Token: ", hex, 0.95);
    }

    // ------------------------------------------------------------------ AK-18 Schema-Formen

    @Test
    void ak18_schemaFormen_genauEinFundUeberDenWert() {
        genau("Bearer", "Authorization: Bearer ", T, 0.95);
        genau("Basic", "Authorization: Basic ", T, 0.95);
        genau("Token", "Authorization: Token ", T, 0.95);
        genau("klein", "authorization: bearer ", T, 0.95);
        genau("gross", "AUTHORIZATION: BEARER ", T, 0.95);
        genau("Gleichheitszeichen", "authorization=Bearer ", T, 0.95);
        genau("Zeilenende", "Authorization: Bearer ", T, "\nNaechste Zeile", 0.95);
        genau("NBSP", "Authorization: Bearer" + NBSP, T, 0.95);
        genau("mehrere Leerzeichen", "Authorization:   Bearer   ", T, 0.95);
        genau("im Befehl", "curl -H \"Authorization: Bearer ", T, "\" https://example.invalid/x", 0.95);
        genau("ohne Schema-Wort", "Authorization: ", T, 0.95);
    }

    @Test
    void ak18_bearerOhneVerbinder_wahrscheinlich_mitDoppelpunktSicher() {
        genau("Bearer ohne Doppelpunkt", "Bearer ", T, 0.80);
        genau("im Satz", "Der Header lautet Bearer ", T, " und ist gueltig", 0.80);
        genau("Bearer mit Doppelpunkt", "Bearer: ", T, 0.95);
        genau("Bearer mit Gleichheitszeichen", "bearer=", T, 0.95);
    }

    @Test
    void ak18_beideKontextwoerterErgebenGenauEinenFund() {
        String text = "Authorization: Bearer " + T;
        assertEquals(1, new TokenRecognizer().detect(text).size(), "Kandidaten des Erkenners");
        assertEquals(1, tokens(text).size(), "Funde im Scanner");
        assertEquals(1, STANDARD.scan(text).size(), "alle Funde im Scanner");
    }

    @Test
    void ak18_schemaWortBleibtKlartext_pseudonymisiertUndRoundtrip() {
        for (String schema : List.of("Authorization: Bearer ", "Authorization: Basic ", "Authorization: Token ",
                "Bearer ")) {
            String text = "Kopfzeile " + schema + T + " Ende";
            assertTrue(("Kopfzeile " + schema + "[[TOKEN_1]] Ende").equals(pseudo(text)),
                    "pseudonymisiert, Schema " + schema.length());
            roundtrip("Schema " + schema.length(), text);
        }
    }

    @Test
    void ak18_keinFund() {
        List<String> saetze = List.of("Bearer Authentifizierung", "Bearer-Token abgelaufen", "Authorization: failed",
                "Authorization: Fehler 401", "Authorization: Bearer", "Authorization:", "Bearer", "Bearer ",
                "Authorization: Basic", "Authorization: Bearer abgelaufen", "Authorization failed: " + T,
                "Authorization required", "Bearer Token: ung" + UE + "ltig", "Der Bearer wurde gesendet");
        for (int i = 0; i < saetze.size(); i++) {
            keinFund("AK-18 " + i, saetze.get(i));
        }
    }

    @Test
    void ak18_wertUnterSechzehnZeichen_keinFund_abSechzehnEinFund() {
        assertEquals(15, T_15.length());
        assertEquals(16, T_16.length());
        keinFund("Bearer 15", "Authorization: Bearer " + T_15);
        keinFund("Basic 15", "Authorization: Basic " + T_15);
        keinFund("ohne Verbinder 15", "Bearer " + T_15);
        keinFund("Authorization 15", "Authorization: " + T_15);
        genau("Bearer 16", "Authorization: Bearer ", T_16, 0.95);
        genau("Basic 16", "Authorization: Basic ", T_16, 0.95);
        genau("ohne Verbinder 16", "Bearer ", T_16, 0.80);
        // Gegenprobe: ein anderes Kontextwort verlangt keine 16 Zeichen
        genau("Token 15", "Token: ", T_15, 0.95);
    }

    @Test
    void ak18_ohneKlassenregelVonW6_wertAusKleinbuchstabenUndZiffer_einFund() {
        String klein = T.toLowerCase(Locale.ROOT);
        genau("Schema klein", "Authorization: Bearer ", klein, 0.95);
        genau("Bearer klein", "Bearer ", klein, 0.80);
        // Hinter K2 gilt W6 (Entscheidung des Nutzers nach Durchlauf 2): ohne Klassenmischung kein Fund
        keinFund("K2 klein", "Token ist " + klein);
    }

    @Test
    void ak18_wertTestGiltAuchImSchema_keinFundBeiMaskeVariablePfad() {
        List<String> werte = List.of("${ACCESS_TOKEN_VALUE_X}", "%ACCESS_TOKEN_VALUE%", "<insert-token-here-12>",
                "/var/run/secrets/token1", "xxxxxxxxxxxxxxxxxx", "aaaaaaaaaaaaaaaaaaaaaaaa", "Abcdefghijklmnopqrstuv");
        for (int i = 0; i < werte.size(); i++) {
            keinFund("Schema Wert-Test " + i, "Authorization: Bearer " + werte.get(i));
            keinFund("Bearer Wert-Test " + i, "Bearer " + werte.get(i));
        }
    }

    // ------------------------------------------------------------------ AK-19 UUID

    @Test
    void ak19_uuid_ohneKontextwortKeinFund_mitKontextwortEinFund() {
        String uuid = SynthetischeGeheimnisse.uuid();
        keinFund("Request-ID", "Request-ID: " + uuid);
        keinFund("Objekt", "Objekt " + uuid);
        keinFund("allein", uuid);
        genau("apikey", "apikey: ", uuid, 0.95);
        genau("Secret", "client_secret=", uuid, 0.95);
    }

    // ------------------------------------------------------------------ AK-9 mehrere Werte

    @Test
    void ak9_passwortUndTokenInEinerZeile_zweiFunde_pseudonymisiert() {
        String vor1 = "Benutzer: svc_x, Passwort: ";
        String text = vor1 + P1 + ", Token: " + T;
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertEquals(new Finding(GeheimnisTypen.PASSWORT, P1, vor1.length(), vor1.length() + P1.length(), 0.95,
                "passwort-kontext"), funde.get(0));
        int tokenStart = (vor1 + P1 + ", Token: ").length();
        assertEquals(new Finding(GeheimnisTypen.TOKEN, T, tokenStart, tokenStart + T.length(), 0.95, QUELLE),
                funde.get(1));
        // Der Pseudonymizer zaehlt ueber alle Typen: der Token ist der zweite Platzhalter (Anforderung AK-9 nennt
        // TOKEN_1, das ist bei einem gemeinsamen Zaehler nicht moeglich).
        assertTrue("Benutzer: svc_x, Passwort: [[PASSWORT_1]], Token: [[TOKEN_2]]".equals(pseudo(text)),
                "pseudonymisierte Zeile");
        roundtrip("AK-9", text);
    }

    @Test
    void ak9_mehrereTokenInEinerZeile_jeEinFund_gleicherWertGleicherPlatzhalter() {
        String text = "Token: " + T + " Secret: " + T_16 + " api_key=" + T;
        List<Finding> funde = tokens(text);
        assertEquals(3, funde.size(), "Zahl der Funde");
        assertTrue(("Token: [[TOKEN_1]] Secret: [[TOKEN_2]] api_key=[[TOKEN_1]]").equals(pseudo(text)),
                "Platzhalter");
        roundtrip("mehrere Token", text);
    }

    @Test
    void ak9_zweiPasswoerterUndEinToken_dreiFundeMitVerschiedenenPlatzhaltern() {
        String text = "Passwort: " + P1 + " Passwort: " + P2 + " Token: " + T;
        assertEquals(3, STANDARD.scan(text).size(), "Zahl der Funde");
        assertEquals(1, tokens(text).size(), "Zahl der TOKEN-Funde");
        roundtrip("zwei Passwoerter, ein Token", text);
    }

    // ------------------------------------------------------------------ AK-30 KV-Funde unveraendert

    @Test
    void ak30_kvFundBleibtUnveraendert_zusaetzlichDerTokenFund() {
        String lanr = SynthetischeKvNummern.LANR_GUELTIG;
        String vor = "LANR " + lanr + ", Token: ";
        String text = vor + T;
        List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        assertEquals(1, alt.size(), "Funde von ALT");
        assertEquals(FindingType.LANR, alt.get(0).type());
        assertEquals(2, neu.size(), "Funde neu");
        assertEquals(alt.get(0), neu.get(0), "LANR-Fund unveraendert");
        assertEquals(erwartet(vor, T, 0.95), neu.get(1), "Token-Fund");
        roundtrip("AK-30 LANR", text);
    }

    @Test
    void ak30_bsnrUndIkFundeBleibenUnveraendert_neben_tokenUndPasswort() {
        String text = "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + ", IK " + SynthetischeKvNummern.IK_GUELTIG
                + ", Secret: " + T + ", Passwort: " + P1;
        List<Finding> alt = TextScannerStandGeheimnis.ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        assertTrue(neu.containsAll(alt), "alle Funde von ALT stehen unveraendert in der neuen Liste");
        assertEquals(alt.size() + 2, neu.size(), "genau zwei zusaetzliche Funde");
        assertEquals(1, tokens(text).size(), "ein TOKEN-Fund");
    }

    // ------------------------------------------------------------------ AK-43 Einbindung

    @Test
    void ak43_standardEnthaeltPasswortUndTokenHinterDenAchtBisherigen_aufIndexAchtUndNeun() {
        List<Recognizer> liste = STANDARD.recognizers();
        assertEquals(10, liste.size(), "Zahl der Erkenner");
        assertEquals(PasswortRecognizer.class, liste.get(8).getClass(), "Index 8");
        assertEquals(TokenRecognizer.class, liste.get(9).getClass(), "Index 9");
        List<Class<?>> bisherige = TextScannerStandGeheimnis.ALT.recognizers().stream()
                .<Class<?>>map(Object::getClass).toList();
        assertEquals(8, bisherige.size(), "Zahl der Erkenner von ALT");
        for (int i = 0; i < 8; i++) {
            assertEquals(bisherige.get(i), liste.get(i).getClass(), "Index " + i + " unveraendert");
        }
        assertFalse(liste.get(9).nachrangig(), "TokenRecognizer ist nicht nachrangig");
    }

    @Test
    void ak43_jeEinFundAllerBestandstypenPlusPasswortUndToken_zehnFunde() {
        String pfad = "C:\\Praxis\\Muster\\x.txt";
        String text = String.join("\n", "Mail kontakt@example.invalid", "IP 192.0.2.17",
                "URL https://example.invalid/praxis", "Pfad " + pfad, "LANR " + SynthetischeKvNummern.LANR_GUELTIG,
                "IK " + SynthetischeKvNummern.IK_GUELTIG, "KVNR " + SynthetischeKvNummern.KVNR_GUELTIG,
                "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT, "Passwort: " + P1, "Token: " + T);
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(List.of(FindingType.EMAIL, FindingType.IP, FindingType.URL, FindingType.PFAD, FindingType.LANR,
                FindingType.IK, FindingType.KVNR, FindingType.BSNR, GeheimnisTypen.PASSWORT, GeheimnisTypen.TOKEN),
                funde.stream().map(Finding::type).toList(), "Typen und Reihenfolge der zehn Funde");
        PseudonymizationResult r = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals(10, r.mapping().size(), "Zahl der Platzhalter");
        assertFalse(r.text().contains(P1), "Passwort im Klartext");
        assertFalse(r.text().contains(T), "Token im Klartext");
        assertTrue(text.equals(REIDENTIFIER.restore(r.text(), r.mapping()).text()), "Roundtrip");
    }

    @Test
    void ak43_typnamenEntsprechenDemNamensmuster() {
        assertTrue(FindingType.isValidName(GeheimnisTypen.PASSWORT.name()), "PASSWORT");
        assertTrue(FindingType.isValidName(GeheimnisTypen.TOKEN.name()), "TOKEN");
        assertEquals("TOKEN", GeheimnisTypen.TOKEN.name());
        for (Finding f : STANDARD.scan("Token: " + T)) {
            assertTrue(FindingType.isValidName(f.type().name()), "Typname des Funds");
        }
    }

    // ------------------------------------------------------------------ Zusammenspiel mit PASSWORT

    @Test
    void zusammenspiel_kontextwortFuerPasswortUndTokenVorDemselbenWert_einFundDerWertIstGeschuetzt() {
        String vor = "Passwort " + FUER + " token: ";
        String text = vor + P1;
        assertEquals(1, new PasswortRecognizer().detect(text).size(), "Passwort-Kandidat");
        assertEquals(1, new TokenRecognizer().detect(text).size(), "Token-Kandidat");
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(vor.length(), funde.get(0).start(), "Beginn");
        assertEquals(text.length(), funde.get(0).end(), "Ende");
        assertFalse(pseudo(text).contains(P1), "Wert im Klartext");
        roundtrip("Passwort fuer token", text);
    }

    @Test
    void zusammenspiel_zusammengesetzteKontextwoerter_dieEndungEntscheidetDenTyp() {
        String vor = "passwort_token: ";
        List<Finding> a = STANDARD.scan(vor + T);
        assertEquals(1, a.size(), "Zahl der Funde passwort_token");
        assertEquals(GeheimnisTypen.TOKEN, a.get(0).type(), "Typ bei passwort_token");
        List<Finding> b = STANDARD.scan("token_passwort: " + P1);
        assertEquals(1, b.size(), "Zahl der Funde token_passwort");
        assertEquals(GeheimnisTypen.PASSWORT, b.get(0).type(), "Typ bei token_passwort");
    }

    @Test
    void zusammenspiel_passwortKandidatenDesScannersBleibenBeiTokenKontextGeschuetzt() {
        String text = "Passwort: " + P1 + " und Bearer " + T + ", Secret: " + P2;
        List<Finding> funde = STANDARD.scan(text);
        boolean[] geschuetzt = new boolean[text.length()];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                geschuetzt[i] = true;
            }
        }
        for (String wert : List.of(P1, T, P2)) {
            int von = text.indexOf(wert);
            for (int i = von; i < von + wert.length(); i++) {
                assertTrue(geschuetzt[i], "Position " + i + " ungeschuetzt");
            }
        }
    }

    // ------------------------------------------------------------------ Eigenschaften des Erkenners

    @Test
    void erkenner_nurTokenKandidatenMitDerQuelleTokenKontext_sortiertUeberlappungsfrei() {
        List<String> texte = List.of("Token: " + T + " Secret: " + T_16 + " Bearer " + T,
                "Authorization: Bearer " + T + "\nAPI Key: " + T, "<token>" + T + "</token> | Secret | " + T + " |",
                "Passwort: " + P1 + " Token: " + T, "Token ist " + T + " Token\t" + T);
        for (int i = 0; i < texte.size(); i++) {
            List<Finding> k = new TokenRecognizer().detect(texte.get(i));
            assertFalse(k.isEmpty(), "Text " + i);
            for (int j = 0; j < k.size(); j++) {
                assertEquals(GeheimnisTypen.TOKEN, k.get(j).type(), "Typ " + i);
                assertEquals(QUELLE, k.get(j).source(), "Quelle " + i);
                assertEquals(TokenRecognizer.QUELLE_KONTEXT, k.get(j).source(), "Konstante " + i);
                assertTrue(k.get(j).confidence() >= 0.80, "Konfidenz " + i);
                if (j > 0) {
                    assertTrue(k.get(j - 1).end() <= k.get(j).start(), "sortiert und ueberlappungsfrei " + i);
                }
            }
        }
    }

    @Test
    void erkenner_nullWirdMitMeldungTextAbgelehnt_gleicherTextGleicheFunde_leerLiefertLeereListe() {
        NullPointerException npe = assertThrows(NullPointerException.class, () -> new TokenRecognizer().detect(null));
        assertEquals("text", npe.getMessage());
        assertEquals(List.of(), new TokenRecognizer().detect(""));
        assertEquals(List.of(), new TokenRecognizer().detect("   \n\t "));
        String text = "Token: " + T + " Bearer " + T;
        assertEquals(new TokenRecognizer().detect(text), new TokenRecognizer().detect(text));
        assertThrows(UnsupportedOperationException.class, () -> new TokenRecognizer().detect(text).clear());
    }
}
