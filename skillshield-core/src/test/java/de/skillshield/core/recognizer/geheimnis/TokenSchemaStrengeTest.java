package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P7;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static de.skillshield.core.SynthetischeGeheimnisse.base64Zeile;
import static de.skillshield.core.SynthetischeGeheimnisse.hex;
import static de.skillshield.core.SynthetischeGeheimnisse.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Entscheidungen nach der Pruefung von T-6 (Plan geheimnisse-erkennung): W6 fuer TOKEN hinter K2 bis K4 und nach
 * einem Zeilenumbruch, Hex und UUID dort als Grenze (S1; die Schema-Strenge dort ist zurueckgenommen), engere
 * Schema-Formen und Base64-Padding vor verdoppelten oder maskierten Anfuehrungszeichen (S2), Schema-Wert in
 * Anfuehrungszeichen ueber 256 Zeichen (S3) und die Grenzen mit Test (S4, S5). Meldungen nennen nur Fall-Nummern und
 * Zahlen, nie einen Text oder Wert.
 */
class TokenSchemaStrengeTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final String QUELLE = "token-kontext";
    private static final String FUER = "f" + (char) 0xFC + "r";

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

    private static void genau(String id, String vor, String wert, String nach, double konfidenz) {
        assertEquals(List.of(erwartet(vor, wert, konfidenz)), tokens(vor + wert + nach), id);
    }

    private static void keinFund(String id, String text) {
        assertEquals(List.of(), tokens(text), id);
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

    /** Die schwaecheren Kontexte mit Kontextwort Token: K2, K3 (Strich, Tabulator), K4, Zeilenumbruch nach K1. */
    private static List<String[]> schwacheKontexte(String wort) {
        return List.of(new String[] {wort + " ist ", ""}, new String[] {"Das " + wort + " lautet ", ""},
                new String[] {wort + " is ", ""}, new String[] {"| " + wort + " | ", " |"},
                new String[] {wort + "\t", ""}, new String[] {"<" + wort + ">", "</" + wort + ">"},
                new String[] {wort + ":\n", ""}, new String[] {wort + ":\r\n    ", ""},
                new String[] {wort + " " + FUER + " svc_x ist ", ""});
    }

    // ------------------------------------------------------------------ S1 W6 hinter K2 bis K4 (Grenze)

    /**
     * Entscheidung des Nutzers nach Durchlauf 2: Hinter K2 bis K4 und nach dem Zeilenumbruch gilt fuer TOKEN wie fuer
     * PASSWORT W6. Hex-Tokens und UUIDs haben zwei Zeichenklassen und bleiben dort lesbar (Grenze, Fehlalarme sind
     * schaedlicher als diese Luecke); hinter K1 und den Schema-Formen sind sie Funde.
     */
    @Test
    void bekannteGrenze_hexUndUuidHinterK2BisK4UndUmbruch_keinFund() {
        List<String> werte = List.of(hex(32), hex(32).toUpperCase(Locale.ROOT), hex(16), uuid(),
                hex(40).toUpperCase(Locale.ROOT), T.toLowerCase(Locale.ROOT));
        for (String wort : List.of("Token", "Secret", "apiKey", "access_token", "client_secret")) {
            List<String[]> kontexte = schwacheKontexte(wort);
            for (int w = 0; w < werte.size(); w++) {
                for (int k = 0; k < kontexte.size(); k++) {
                    String[] kontext = kontexte.get(k);
                    keinFund("Wort " + wort.length() + " Wert " + w + " Kontext " + k,
                            kontext[0] + werte.get(w) + kontext[1]);
                }
            }
        }
        // Gegenproben: hinter K1 und den Schema-Formen sind dieselben Werte Funde
        genau("K1 Hex", "Token: ", hex(32), "", 0.95);
        genau("K1 UUID", "api_key = ", uuid(), "", 0.95);
        genau("Schema Hex", "Authorization: Bearer ", hex(32), "", 0.95);
        genau("Schema UUID", "Authorization: Bearer ", uuid(), "", 0.95);
    }

    /** Base64 mit drei Klassen besteht W6 und ist hinter K2 bis K4 weiter ein Fund (0,80). */
    @Test
    void s1_base64MitDreiKlassen_inAllenSchwachenKontexten_fundMitKonfidenzAchtzig() {
        List<String> werte = List.of(base64Zeile(40), base64Zeile(24), T);
        List<String[]> kontexte = schwacheKontexte("Token");
        for (int w = 0; w < werte.size(); w++) {
            for (int k = 0; k < kontexte.size(); k++) {
                String[] kontext = kontexte.get(k);
                genau("Wert " + w + " Kontext " + k, kontext[0], werte.get(w), kontext[1], 0.80);
            }
        }
    }

    @Test
    void s1_woerterOhneZiffernOderSonderzeichen_keinFund() {
        List<String> woerter = List.of("Zugriffsberechtigungsverwaltung", "abcdefghijklmnopqrstuvwxyz",
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "Kontoverwaltung-Zugriffsdienst");
        List<String[]> kontexte = schwacheKontexte("Token");
        for (int w = 0; w < woerter.size(); w++) {
            for (int k = 0; k < kontexte.size(); k++) {
                String[] kontext = kontexte.get(k);
                keinFund("Wort " + w + " Kontext " + k, kontext[0] + woerter.get(w) + kontext[1]);
            }
        }
    }

    @Test
    void s1_wertTestW1BisW5GiltWeiter_keinFundBeiPfadMaskeUndZeichenwiederholung() {
        List<String> werte = List.of("/var/lib/token/dienst1.json", "${ACCESS_TOKEN_VALUE_1}", "%ACCESS_TOKEN_VALUE%",
                "<insert-token-here-12>", "{{vault_token_12345}}", "https://example.invalid/token1",
                "1".repeat(20), "x".repeat(18));
        List<String[]> kontexte = schwacheKontexte("Token");
        for (int w = 0; w < werte.size(); w++) {
            for (int k = 0; k < kontexte.size(); k++) {
                String[] kontext = kontexte.get(k);
                keinFund("Wert " + w + " Kontext " + k, kontext[0] + werte.get(w) + kontext[1]);
            }
        }
    }

    /** Nach K1 in derselben Zeile gilt weiter W1 bis W5 (0,95); nach dem Zeilenumbruch gilt W6 (Konfidenz 0,80). */
    @Test
    void s1_k1InDerselbenZeileUnveraendert() {
        genau("K1 Hex", "Token: ", hex(32), "", 0.95);
        genau("K1 UUID", "api_key = ", uuid(), "", 0.95);
        genau("K1 Umbruch", "Token:\n", base64Zeile(32), "", 0.80);
        keinFund("K1 Umbruch Hex", "Token:\n" + hex(32));
    }

    /** PASSWORT bleibt unveraendert: hinter K2 bis K4 und dem Zeilenumbruch gilt W6, Hex und UUID bleiben sichtbar. */
    @Test
    void s1_passwortBleibtUnveraendert_hexUndUuidHinterSchwachenVerbindernKeinFund() {
        PasswortRecognizer erkenner = new PasswortRecognizer();
        for (String wert : List.of(hex(32), hex(32).toUpperCase(Locale.ROOT), uuid(), T.toLowerCase(Locale.ROOT))) {
            for (String[] kontext : schwacheKontexte("Passwort")) {
                assertEquals(List.of(), erkenner.detect(kontext[0] + wert + kontext[1]), "Passwort ohne W6-Wert");
            }
        }
        // Gegenprobe: Mit W6 (drei Klassen, ab 8 Zeichen) bleibt der Fund
        assertEquals(1, erkenner.detect("Passwort ist " + P2).size(), "Passwort mit W6");
    }

    @Test
    void s1_tokenHinterIstWirdErsetzt_roundtripUndKlarTextFrei() {
        Pseudonymizer pseudonymizer = new Pseudonymizer();
        String wert = base64Zeile(32);
        String text = "Das Token ist " + wert + " und gilt bis morgen.";
        PseudonymizationResult p = pseudonymizer.pseudonymize(text, STANDARD.scan(text));
        assertFalse(p.text().contains(wert), "Wert im pseudonymisierten Text");
        assertTrue(p.text().startsWith("Das Token ist [["), "Kontext bleibt");
    }

    /**
     * Stichprobe (Fehlalarme): Zeilen ohne Geheimnis rund um {@code Token ist ...}, Tabellen mit
     * Token-Spalte, XML mit {@code <token>} und {@code <apiKey>} und Zeilenumbruch nach {@code Token:}. Keine Zeile
     * darf einen TOKEN-Fund ergeben.
     */
    @Test
    void s1_stichprobeOhneGeheimnis_keinTokenFund() {
        List<String> zeilen = stichprobe();
        assertTrue(zeilen.size() >= 60, "Zahl der Zeilen " + zeilen.size());
        List<Integer> mitFund = new ArrayList<>();
        for (int i = 0; i < zeilen.size(); i++) {
            if (!tokens(zeilen.get(i)).isEmpty()) {
                mitFund.add(i);
            }
        }
        assertEquals(List.of(), mitFund, "Zeilen der Stichprobe mit Fund");
    }

    private static List<String> stichprobe() {
        return List.of(
                // Token ist ... (K2)
                "Das Token ist abgelaufen und muss erneuert werden.",
                "Das Token ist nicht mehr g" + (char) 0xFC + "ltig, siehe Ticket CHG0012345.",
                "Der Token ist 3600 Sekunden g" + (char) 0xFC + "ltig.",
                "Das Secret ist im Tresor hinterlegt.",
                "Das Secret ist rotiert worden.",
                "Der API Key ist widerrufen.",
                "Der API Key ist im Passwortmanager abgelegt.",
                "Token ist kein Passwort.",
                "Das Token ist in der Datei token.json gespeichert.",
                "Der Client Secret ist ausgelaufen seit dem 2026-03-12.",
                "Das Token lautet anders als im Test.",
                "Der Refresh Token ist 24 Stunden g" + (char) 0xFC + "ltig, der Access Token 15 Minuten.",
                "Das Bearer Token ist im Header Authorization " + (char) 0xFC + "bergeben worden.",
                "Das Secret is stored in the key vault.",
                "The token is valid for 3600 seconds.",
                "The API key is rotated every quarter.",
                "Das Token ist ein JWT mit dem Algorithmus RS256.",
                "Das Secret ist ein Zufallswert aus 32 Bytes.",
                // Tabellen mit Token-Spalte (K3, Strich und Tabulator)
                "| Token | Beschreibung | Ablauf |",
                "| Token | G" + (char) 0xFC + "ltigkeit | 3600 Sekunden |",
                "| Secret | Rotation | quartalsweise |",
                "| API Key | Dienst | Verwendung |",
                "|Token|Typ|Ablauf|",
                "| Token | JWT | 15 Minuten |",
                "| Access Token | Bearer | 1 Stunde |",
                "| Secret | in Tresor | nein |",
                "| Token | wird nicht gespeichert |",
                "| Token | Keycloak | 2 Stunden |",
                "| Token | Dauer | 24h |",
                "| API Key | Anzahl | 12 |",
                "| Secret | siehe Tresor | Abteilung IT |",
                "Token\tBeschreibung\tAblauf",
                "Token\tG" + (char) 0xFC + "ltigkeit\t3600",
                "Secret\tRotation\tquartalsweise",
                "API Key\tDienst\tVerwendung",
                "Token\tJWT\t15 Minuten",
                "Token\tabgelaufen",
                "Secret\tnicht vorhanden",
                "Token\tAblauf\tTyp\tBemerkung",
                "Token\tBearer\tHeader",
                "Secret\tTresor\tKeePass",
                // XML mit token und apiKey (K4)
                "<token>abgelaufen</token>",
                "<token></token>",
                "<token/>",
                "<apiKey>wird-per-Tresor-geliefert</apiKey>",
                "<secret>siehe_Tresor_Eintrag</secret>",
                "<token>${TOKEN}</token>",
                "<token>%TOKEN%</token>",
                "<apiKey>{{ vault_api_key }}</apiKey>",
                "<token>bitte-eintragen</token>",
                "<token type=\"jwt\">siehe Tresor</token>",
                "<clientSecret>aus-dem-Tresor-lesen</clientSecret>",
                "<token>[entfernt]</token>",
                "<token>0</token>",
                "<apiKey>keiner</apiKey>",
                "<secret>geheim-und-nicht-hier</secret>",
                "<token>nicht-gesetzt</token>",
                "<apiKey>${env.API_KEY}</apiKey>",
                // Zeilenumbruch nach Token:
                "Token:\nwird per Post geliefert",
                "Token:\nsiehe Tresor",
                "Token:\n  abgelaufen",
                "Secret:\nnicht vorhanden",
                "API Key:\nliegt im Tresor",
                "Token:\n\nBeschreibung folgt",
                "Token:\n- Ablauf: 3600 Sekunden",
                "Secret:\n* rotiert quartalsweise",
                "Token:\n3600",
                "access_token:\n  type: string",
                "client_secret:\n  required: true",
                "token:\n  description: Zugriffstoken f" + (char) 0xFC + "r den Dienst",
                "api_key:\n  - name: dienst",
                "Token:\n" + (char) 0xDC + "bersicht der Tokens im Projekt");
    }

    // ------------------------------------------------------------------ S2 engere Schema-Formen

    @Test
    void s2_httpParameterUndAufrufe_keinToken() {
        keinFund("Bearer error", "WWW-Authenticate: Bearer error=\"invalid_token\"");
        keinFund("Bearer realm", "WWW-Authenticate: Bearer realm=\"api\"");
        keinFund("Bearer realm lang", "WWW-Authenticate: Bearer realm=\"" + hex(24) + "\"");
        keinFund("Bearer Fehler und Beschreibung",
                "WWW-Authenticate: Bearer error=\"invalid_token\", error_description=\"The access token expired\"");
        keinFund("Basic Aufruf", "Authorization: Basic base64(user:password)");
        keinFund("Bearer Aufruf", "Authorization: Bearer getAccessToken(" + hex(20) + ")");
        keinFund("Bearer ohne Doppelpunkt Aufruf", "Bearer fetchToken(" + hex(20) + ")");
        keinFund("Token Aufruf", "Authorization: Token loadFromVault(" + hex(20) + ")");
        keinFund("Authorization ohne Schema", "Authorization: build_header(" + hex(20) + ")");
    }

    @Test
    void s2_echteWerte_weiterFund() {
        String padding = base64Zeile(40) + "==";
        genau("Bearer Padding", "Authorization: Bearer ", padding, "", 0.95);
        genau("Basic Padding", "Authorization: Basic ", base64Zeile(24) + "=", "", 0.95);
        String url = base64Zeile(20) + "-_" + base64Zeile(20);
        genau("Bearer base64url", "Authorization: Bearer ", url, "", 0.95);
        genau("Bearer Hex", "Authorization: Bearer ", hex(40), "", 0.95);
        genau("ohne Doppelpunkt Padding", "Bearer ", padding, "", 0.80);
        genau("ohne Doppelpunkt Hex", "Bearer ", hex(40), "", 0.80);
        genau("im Anfuehrungszeichen", "Authorization: Bearer ", padding, "\"", 0.95);
    }

    /** Die Rahmen: CSV-verdoppeltes JSON, maskiertes JSON und curl-Zeilen (CSV und maskiert), je mit Schema-Wort. */
    private static List<String[]> padRahmen(String schema) {
        return List.of(
                new String[] {"\"{\"\"Authorization\"\": \"\"" + schema + " ", "\"\"}\""},
                new String[] {"{\\\"Authorization\\\": \\\"" + schema + " ", "\\\"}"},
                new String[] {"\"curl -H \"\"Authorization: " + schema + " ", "\"\" https://example.invalid\""},
                new String[] {"curl -H \\\"Authorization: " + schema + " ", "\\\" https://example.invalid"},
                new String[] {"{\"Authorization\": \"" + schema + " ", "\"}"},
                new String[] {"curl -H \"Authorization: " + schema + " ", "\" https://example.invalid"});
    }

    /** Genau ein TOKEN-Fund, der mit dem Wert beginnt und ihn ganz deckt (ein Rest der Anfuehrung darf mit). */
    private static void deckt(String id, String vor, String wert, String nach) {
        List<Finding> funde = tokens(vor + wert + nach);
        assertEquals(1, funde.size(), id);
        assertEquals(vor.length(), funde.get(0).start(), id);
        assertTrue(funde.get(0).end() >= vor.length() + wert.length(), id);
    }

    /** Bearer und Basic mit Padding 0, 1, 2 in CSV-verdoppeltem JSON, maskiertem JSON und curl-Zeilen. */
    @Test
    void s2_paddingMitVerdoppeltemOderMaskiertemAnfuehrungszeichen_fundUeberDenWert() {
        for (String schema : List.of("Bearer", "Basic")) {
            List<String[]> rahmen = padRahmen(schema);
            for (int pad = 0; pad <= 2; pad++) {
                String wert = base64Zeile(40) + "=".repeat(pad);
                for (int r = 0; r < rahmen.size(); r++) {
                    deckt(schema + " Padding " + pad + " Rahmen " + r, rahmen.get(r)[0], wert, rahmen.get(r)[1]);
                }
            }
        }
    }

    /** Die Parameter-Form {@code name="..."} bleibt auch in CSV-verdoppelter Schreibweise kein Token. */
    @Test
    void s2_parameterFormenBleibenKeinFund_auchVerdoppelt() {
        keinFund("error", "WWW-Authenticate: Bearer error=\"invalid_token\"");
        keinFund("realm", "WWW-Authenticate: Bearer realm=\"api\"");
        keinFund("Basic Aufruf", "Authorization: Basic base64(user:password)");
        keinFund("error verdoppelt", "\"WWW-Authenticate: Bearer error=\"\"invalid_token\"\"\"");
        keinFund("realm verdoppelt", "\"WWW-Authenticate: Bearer realm=\"\"api_gateway_prod\"\"\"");
    }

    /**
     * Zufallstest mit festem Seed: Werte mit zufaelliger Laenge (20 bis 119 Zeichen) und Padding 0 bis 2 in allen
     * Rahmen und beiden Schemata (4 800 Faelle). Gemessen bei diesem Seed: 4 790 Faelle sind voll gedeckt (99,8
     * Prozent), 10 nicht. Die 10 sind ein einziger Wert (33 Zeichen, nur Buchstaben, kein Padding) in je 5 der 6
     * Rahmen (alle ausser dem CSV-verdoppelten JSON) und beiden Schemata (Bearer, Basic): ein Wort nur aus
     * Buchstaben ist kein Token (G-2, Javadoc von {@code PasswortRecognizer}). Der Test haelt die Zahl fest und
     * prueft, dass jeder ungedeckte Fall ein Wert nur aus Buchstaben (mit Padding) ist.
     */
    @Test
    void s2_zufallMitPadding_viertausendSiebenhundertNeunzigVonViertausendAchthundertVollGedeckt() {
        Random zufall = new Random(20261127L);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz01234" + "56789+/";
        int faelle = 0;
        int gedeckt = 0;
        int ungedecktMitZiffernOderSonderzeichen = 0;
        for (int i = 0; i < 400; i++) {
            StringBuilder sb = new StringBuilder();
            int laenge = 20 + zufall.nextInt(100);
            for (int k = 0; k < laenge; k++) {
                sb.append(alphabet.charAt(zufall.nextInt(alphabet.length())));
            }
            sb.setCharAt(0, 'Q');
            String wert = sb + "=".repeat(zufall.nextInt(3));
            for (String schema : List.of("Bearer", "Basic")) {
                for (String[] rahmen : padRahmen(schema)) {
                    String text = rahmen[0] + wert + rahmen[1];
                    faelle++;
                    if (alleGeschuetzt(text, rahmen[0].length(), rahmen[0].length() + wert.length())) {
                        gedeckt++;
                    } else if (!nurBuchstabenUndPadding(wert)) {
                        ungedecktMitZiffernOderSonderzeichen++;
                    }
                }
            }
        }
        assertEquals(400 * 2 * 6, faelle, "Zahl der Faelle");
        assertEquals(4790, gedeckt, "Faelle voll gedeckt");
        assertEquals(0, ungedecktMitZiffernOderSonderzeichen, "ungedeckte Faelle mit Ziffer oder + /");
    }

    private static boolean nurBuchstabenUndPadding(String wert) {
        return wert.chars().allMatch(c -> Character.isLetter(c) || c == '=');
    }

    // ------------------------------------------------------------------ S3 Schema-Wert in Anfuehrungszeichen

    @Test
    void s3_schemaInAnfuehrungszeichen_dieLaengeDesWertsIstEgal() {
        for (int laenge : List.of(30, 249, 250, 256, 257, 1000, 4096)) {
            String wert = base64Zeile(laenge);
            for (String schema : List.of("Bearer", "Basic", "Token")) {
                genau("JSON " + schema + " " + laenge, "\"Authorization\": \"" + schema + " ", wert, "\"", 0.95);
                genau("JSON Komma " + schema + " " + laenge, "\"Authorization\": \"" + schema + " ", wert, "\",",
                        0.95);
                genau("YAML " + schema + " " + laenge, "authorization: \"" + schema + " ", wert, "\"", 0.95);
                genau("Gleichheitszeichen " + schema + " " + laenge, "Authorization=\"" + schema + " ", wert, "\"",
                        0.95);
                genau("einfach " + schema + " " + laenge, "'Authorization': '" + schema + " ", wert, "'", 0.95);
            }
        }
    }

    @Test
    void s3_ueberDerObergrenze_keinFund_wieBeimPasswort() {
        String wert = base64Zeile(4097);
        keinFund("4097 in Anfuehrungszeichen", "\"Authorization\": \"Bearer " + wert + "\"");
        keinFund("4097 ohne Anfuehrungszeichen", "Authorization: Bearer " + wert);
    }

    @Test
    void s3_schemaWortBleibtKlartext_langerWertWirdErsetzt() {
        String wert = base64Zeile(400);
        String text = "{\"Authorization\": \"Bearer " + wert + "\"}";
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, STANDARD.scan(text));
        assertEquals("{\"Authorization\": \"Bearer [[TOKEN_1]]\"}", p.text(), "pseudonymisiert");
    }

    // ------------------------------------------------------------------ S4 Grenzen

    /** Ein Base64-Token, das mit {@code /} beginnt, gilt als Pfad (W4) und bleibt sichtbar. */
    @Test
    void bekannteGrenze_base64MitSchraegstrichAmAnfang_keinFund() {
        String wert = "/" + base64Zeile(30);
        keinFund("Authorization", "Authorization: Bearer " + wert);
        keinFund("Bearer", "Bearer " + wert);
        keinFund("Token", "Token: " + wert);
        // Gegenproben: ein Schraegstrich im Innern oder ohne den ersten Platz ist ein Fund
        genau("Innern", "Authorization: Bearer ", base64Zeile(10) + "/" + base64Zeile(20), "", 0.95);
        genau("ohne", "Authorization: Bearer ", base64Zeile(30), "", 0.95);
    }

    /** Negotiate, NTLM und Digest sind keine Schema-Woerter; das Schema-Wort ist der Wert und zu kurz. */
    @Test
    void bekannteGrenze_negotiateNtlmDigest_keinFund() {
        for (String schema : List.of("Negotiate", "NTLM", "Digest")) {
            keinFund(schema, "Authorization: " + schema + " " + base64Zeile(40));
            keinFund(schema + " klein", "authorization: " + schema.toLowerCase(Locale.ROOT) + " " + hex(40));
        }
    }

    /**
     * Typwechsel PASSWORT nach TOKEN: Umfasst ein TOKEN-Fund (K3, Tabulator) einen PASSWORT-Fund (K1 im Zwischenteil),
     * liefern beide Erkenner Kandidaten; der Scanner waehlt einen Typ, kein Zeichen bleibt frei.
     */
    @Test
    void bekannteGrenze_typwechselPasswortNachToken_keinPositionVerloren() {
        String vor = "Passwort fuer token\t";
        String wert = P7 + "=" + P2;
        String text = vor + wert;
        List<Finding> passwort = new PasswortRecognizer().detect(text);
        List<Finding> token = new TokenRecognizer().detect(text);
        assertEquals(1, passwort.size(), "PASSWORT-Kandidaten");
        assertEquals(1, token.size(), "TOKEN-Kandidaten");
        assertTrue(token.get(0).start() < passwort.get(0).start() && token.get(0).end() >= passwort.get(0).end(),
                "TOKEN umfasst PASSWORT");
        assertTrue(alleGeschuetzt(text, vor.length(), text.length()), "kein Zeichen des Werts bleibt frei");
        List<Finding> alle = STANDARD.scan(text);
        assertEquals(1, alle.size(), "ein Fund im Scanner");
        assertEquals(GeheimnisTypen.TOKEN, alle.get(0).type(), "Typ nach dem Wechsel");
    }

    /**
     * Grenze 1 (security, mittel; Abnahme von T-6 durch den Nutzer): Ein Schema-Wert ({@code Bearer}, {@code Basic})
     * mit genau einem {@code =} am Ende ist kein Fund, wenn direkt nach dem schliessenden Anfuehrungszeichen ein
     * Zeichen folgt, das die Kuerzung am Ende nicht entfernt ({@code !}, {@code *}, {@code |}, {@code <},
     * {@code **}): {@code istParameterAnfuehrung} wertet {@code ="} dort als Parameterform. Behebung (nicht
     * umgesetzt): Parameterform nur, wenn nach {@code ="} ein Namenszeichen folgt. Gegenproben: Leerzeichen, Punkt,
     * Komma und schliessende geschweifte Klammer nach dem Anfuehrungszeichen sowie zwei {@code =} oder kein
     * {@code =} sind Funde (gemessen, wie erwartet).
     */
    @Test
    void bekannteGrenze_schemaWertMitEinemPaddingZeichenUndFolgezeichenNachAnfuehrung_keinFund() {
        List<String> folgen = List.of("!", "*", "|", "</code>", "**");
        for (String schema : List.of("Bearer", "Basic")) {
            String vor = "{\"Authorization\": \"" + schema + " ";
            String wert = base64Zeile(40) + "=";
            for (int f = 0; f < folgen.size(); f++) {
                keinFund(schema.length() + " Folge " + f + " Padding 1", vor + wert + "\"" + folgen.get(f));
            }
            // Gegenproben: andere Folgezeichen nach dem Anfuehrungszeichen, Padding 2 und kein Padding
            List<String> andere = List.of(" x", ".", ",", "}");
            for (int f = 0; f < andere.size(); f++) {
                deckt(schema.length() + " Gegenprobe " + f, vor, wert, "\"" + andere.get(f));
            }
            String wert2 = base64Zeile(40) + "==";
            String wert0 = base64Zeile(40);
            for (int f = 0; f < folgen.size(); f++) {
                deckt(schema.length() + " Padding 2 Folge " + f, vor, wert2, "\"" + folgen.get(f));
                deckt(schema.length() + " Padding 0 Folge " + f, vor, wert0, "\"" + folgen.get(f));
            }
        }
    }

    /**
     * Grenze 2a (niedrig): Bei kompaktem CSV-JSON (nach dem Wert zwei Anfuehrungszeichen, eine schliessende
     * geschweifte Klammer und ein Anfuehrungszeichen) beginnt der Fund am Wert und reicht um diese vier Zeichen
     * ueber das Wertende hinaus (Ueberschutz, kein Leck).
     */
    @Test
    void bekannteGrenze_kompaktesCsvJsonMitPadding_fundReichtUeberDasWertendeHinaus() {
        String vor = "\"{\"\"Authorization\"\": \"\"Bearer ";
        String wert = base64Zeile(40) + "==";
        String nach = "\"\"}\"";
        List<Finding> funde = tokens(vor + wert + nach);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(vor.length(), funde.get(0).start(), "Beginn");
        assertEquals(vor.length() + wert.length() + nach.length(), funde.get(0).end(), "Ende");
    }

    /** Grenze 2b (niedrig): ein maskierter Parameter {@code error=\"...\"} hinter {@code Bearer} ist ein Fund. */
    @Test
    void bekannteGrenze_bearerMitMaskiertemParameter_istFund() {
        String text = "WWW-Authenticate: Bearer error=\\\"invalid_token\\\"";
        assertEquals(1, tokens(text).size(), "Zahl der Funde");
    }

    /**
     * Grenze 2c (niedrig): Ein unmittelbar an den Schema-Wert geklebtes zweites Kontextwort mit Verbinder wird nicht
     * ausgewertet. Gemessen: ein einziger Fund ab dem Wert bis zum Ende des Textes; Wert, angeklebtes Kontextwort
     * und dessen Wert sind zusammen ein Wort (Ueberschutz, kein Leck, kein zweiter Fund).
     */
    @Test
    void bekannteGrenze_angeklebtesZweitesKontextwortHinterSchemaWert_nichtAusgewertet() {
        String vor = "Authorization: Bearer ";
        String text = vor + base64Zeile(30) + "token:" + hex(20);
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(vor.length(), funde.get(0).start(), "Beginn");
        assertEquals(text.length(), funde.get(0).end(), "Ende");
    }

    // ------------------------------------------------------------------ S5 Fehlalarm-Klassen (nur dokumentiert)

    /**
     * Ein langer technischer Bezeichner aus Kleinbuchstaben, Ziffern und {@code _} direkt hinter dem Kontextwort ist
     * hinter K2 bis K4 und nach dem Zeilenumbruch kein Fund (W6, zwei Klassen); die Schema-Strenge dort ist
     * zurueckgenommen.
     */
    @Test
    void bekannteGrenze_langerBezeichnerMitZifferDirektHinterDemKontextwort_keinFund() {
        String bezeichner = "oauth2_refresh_token_ttl";
        keinFund("Tabelle", "| Token | " + bezeichner + " | Sekunden |");
        keinFund("Tabulator", "Secret\t" + bezeichner);
        keinFund("Element", "<token>" + bezeichner + "</token>");
        keinFund("ist", "Das Secret ist " + bezeichner + " genannt.");
        keinFund("Zeilenumbruch", "Token:\n" + bezeichner);
    }

    /**
     * Eine Code-Zuweisung mit Funktionsaufruf als Wert hinter einem Token-Kontextwort mit {@code =} ist ein Fund
     * (Schutz geht vor); der Ausschluss von Aufrufen gilt nur hinter {@code Bearer} und {@code Authorization}.
     */
    @Test
    void bekannteGrenze_codeZuweisungMitFunktionsaufrufHinterKontextwort_istFund() {
        assertEquals(1, tokens("String token = createToken2(user);").size(), "Zuweisung");
        assertEquals(1, tokens("token = vault.fetch2(dienst)").size(), "Punkt");
        assertEquals(1, tokens("access_token=Hole3Token(" + hex(12) + ")").size(), "ohne Leerzeichen");
        // Gegenprobe: hinter Authorization und Bearer ist ein Aufruf kein Token
        keinFund("Authorization", "Authorization: build_header(" + hex(20) + ")");
    }

    /**
     * Ein Wert in {@code <token>} ueber 256 Zeichen (lange JWT, Base64) bleibt lesbar: Die Anfuehrungs- und
     * Elementregel liest hoechstens 256 Zeichen weit; hinter K1 und den Schema-Formen gilt 4 096.
     */
    @Test
    void bekannteGrenze_elementWertUeber256Zeichen_keinFund() {
        keinFund("Element 257", "<token>" + base64Zeile(257) + "</token>");
        keinFund("Element 600", "<token>" + base64Zeile(600) + "</token>");
        // Gegenproben: bis 256 Zeichen im Element und 600 Zeichen hinter K1 sind Funde
        assertEquals(1, tokens("<token>" + base64Zeile(256) + "</token>").size(), "Element 256");
        assertEquals(1, tokens("Token: " + base64Zeile(600)).size(), "K1 600");
    }

    /**
     * Ein Wert mit genau 4 096 Zeichen, auf den ein Anfuehrungszeichen und zwei schliessende geschweifte Klammern
     * folgen, ist kein Fund: Das Wort ist mit diesen Zeichen laenger als 4 096 und wird ganz uebersprungen.
     */
    @Test
    void bekannteGrenze_wert4096ZeichenMitAnfuehrungUndKlammernAmEnde_keinFund() {
        String wert = base64Zeile(4096);
        keinFund("Klammern", "{\"o\": {\"Authorization\": \"Bearer " + wert + "\"}}");
        keinFund("Klammern ohne Schema", "{\"o\": {\"token\": \"" + wert + "\"}}");
        // Gegenprobe: mit nur einer schliessenden Klammer ist es hinter dem Schema-Wort ein Fund
        genau("4096 Anfuehrung", "{\"Authorization\": \"Bearer ", wert, "\"}", 0.95);
    }

    /** Referenzwerte wie Tresor-Pfad und Cloud-Ressourcenkennung hinter dem Kontextwort sind Funde. */
    @Test
    void bekannteGrenze_referenzwerteTresorPfadUndRessourcenkennung_sindFunde() {
        assertEquals(1, tokens("Secret: secret/data/app2/prod").size(), "Tresor-Pfad");
        assertEquals(1, tokens("Token: arn:aws:secretsmanager:eu-central-1:konto:secret:app2").size(), "Kennung");
    }

    /** IT-Begriffe mit Ziffer hinter {@code Token:} sind Funde. */
    @Test
    void bekannteGrenze_itBegriffeMitZifferHinterTokenDoppelpunkt_sindFunde() {
        assertEquals(1, tokens("Token: OAuth2").size(), "OAuth2");
        assertEquals(1, tokens("Token: Kerberos5").size(), "Kerberos5");
        assertEquals(1, tokens("Secret: Base64-kodiert").size(), "Base64-kodiert");
    }
}
