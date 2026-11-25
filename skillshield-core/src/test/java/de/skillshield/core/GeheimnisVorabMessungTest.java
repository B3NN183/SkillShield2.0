package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Vorabmessung des heutigen Stands (Plan geheimnisse-erkennung, T-1; Anforderung OF-2, AK-26, AK-28, AK-48 a): Die
 * Beispiele der Anforderung laufen durch die eingefrorene Kopie {@link TextScannerStandGeheimnis#ALT}; je Fall sind
 * die gemessenen Funde (Typ, Bereich, Konfidenz, Quelle) und die Lage der Wertposition festgehalten:
 * {@code FREI} (kein Fund auf der Wertposition), {@code TEIL} (ein Fund deckt einen Teil ab) oder {@code VOLL} (die
 * Wertposition ist ganz abgedeckt). Die Werte sind der gemessene Ist-Stand und ab Abnahme von T-1 eingefroren (Plan,
 * Kapitel 6.3); sie sind keine Sollwerte der spaeteren Erkenner. Meldungen nennen nur Fall-ID, Typen, Zahlen und
 * Quellen, nie Texte oder Werte.
 *
 * <p>Auswertung nach T-1 "Was bei Abweichung von der Vermutung geschieht": Regel 1 (Fund auf dem Passwort im Ticket
 * oder in der AK-1-Zeile) greift nicht; Regel 2 greift nicht (die sechs vermuteten Funde entsprechen der Messung);
 * Regel 3 greift fuer die Wertformen in Pfad-, URL- und E-Mail-Form und fuer das Passwort in einer URL mit
 * {@code password=} (siehe die Faelle mit Lage {@code VOLL} und {@code TEIL}); Regel 4: die Oracle-Form mit Punkt im
 * Ziel ist heute ein E-Mail-Fund ueber den ganzen Teil {@code Benutzer/Passwort@Ziel}.
 */
class GeheimnisVorabMessungTest {

    /** Das {@code @}-Zeichen, getrennt gehalten, damit keine E-Mail-aehnliche Zeichenfolge im Quelltext steht. */
    private static final String AT = "@";

    private static final String AE = String.valueOf((char) 0xE4);
    private static final String UE = String.valueOf((char) 0xFC);

    private static final String NICHTS = "keine";

    private record Fall(String id, String text, String wert, String erwartet, String lage) {
    }

    private static Fall fall(String id, String text, String wert, String erwartet, String lage) {
        return new Fall(id, text, wert, erwartet.isEmpty() ? NICHTS : erwartet, lage);
    }

    private static Fall frei(String id, String text, String wert) {
        return fall(id, text, wert, "", "FREI");
    }

    private static String beschreibe(List<Finding> funde) {
        if (funde.isEmpty()) {
            return NICHTS;
        }
        List<String> teile = new ArrayList<>();
        for (Finding f : funde) {
            teile.add(f.type().name() + "[" + f.start() + "," + f.end() + ")" + f.confidence() + ":" + f.source());
        }
        return String.join(" ", teile);
    }

    private static String lage(String text, String wert, List<Finding> funde) {
        if (wert == null) {
            return "-";
        }
        int von = text.indexOf(wert);
        if (von < 0) {
            return "WERT-FEHLT";
        }
        int abgedeckt = 0;
        for (int pos = von; pos < von + wert.length(); pos++) {
            for (Finding f : funde) {
                if (pos >= f.start() && pos < f.end()) {
                    abgedeckt++;
                    break;
                }
            }
        }
        if (abgedeckt == 0) {
            return "FREI";
        }
        return abgedeckt == wert.length() ? "VOLL" : "TEIL";
    }

    /** Misst alle Faelle gegen ALT und liefert die Abweichungen von der festgehaltenen Messung (Fall-ID, Ist, Soll). */
    private static List<String> abweichungen(List<Fall> faelle) {
        List<String> abweichungen = new ArrayList<>();
        for (Fall f : faelle) {
            List<Finding> funde = TextScannerStandGeheimnis.ALT.scan(f.text());
            String ist = beschreibe(funde);
            String istLage = lage(f.text(), f.wert(), funde);
            if (!ist.equals(f.erwartet()) || !istLage.equals(f.lage())) {
                abweichungen.add(f.id() + ": ist=" + ist + " lage=" + istLage + " soll=" + f.erwartet()
                        + " lage=" + f.lage());
            }
        }
        return abweichungen;
    }

    // ------------------------------------------------------------------ Referenz-Ticket und AK-1-Zeile

    @Test
    void messung_referenzTicket_heuteSechsBestandsfunde_dasPasswortIstFrei() {
        String ticket = SynthetischeGeheimnisse.korpusText("referenz-ticket.txt");
        String erwartet = "EMAIL[77,106)0.95:email PFAD[291,334)0.95:pfad-unc IP[419,429)0.85:ipv4 "
                + "PFAD[443,475)0.95:pfad-laufwerk PFAD[480,504)0.9:pfad-unix URL[691,744)0.95:url";
        assertEquals(List.of(), abweichungen(List.of(fall("ticket", ticket, P1, erwartet, "FREI"))));
    }

    @Test
    void messung_ak1Zeile_keinFundAufDemPasswort() {
        String text = "Tempor" + AE + "res Passwort f" + UE + "r svc_abr_export: " + P1;
        assertEquals(List.of(), abweichungen(List.of(frei("AK-1", text, P1))));
    }

    // ------------------------------------------------------------------ Wertformen: Pfad, URL, E-Mail (AK-13, AK-29)

    @Test
    void messung_passwortMitPfadUrlOderEmailAlsWert_heuteFundAufDerWertposition() {
        List<Fall> faelle = List.of(
                fall("AK-13a", "Passwort: /etc/shadow", "/etc/shadow", "PFAD[10,21)0.8:pfad-unix", "VOLL"),
                fall("AK-13b", "Passwort: C:\\Tresor\\pw.kdbx", "C:\\Tresor\\pw.kdbx",
                        "PFAD[10,27)0.95:pfad-laufwerk", "VOLL"),
                fall("AK-13c", "Passwortdatei: /etc/x", "/etc/x", "PFAD[15,21)0.8:pfad-unix", "VOLL"),
                fall("AK-13d", "Passwort: https://tresor.kvbsp.local/eintrag", "https://tresor.kvbsp.local/eintrag",
                        "URL[10,44)0.95:url", "VOLL"),
                fall("AK-29a", "Passwort: erika.beispiel" + AT + "kv-beispiel.example",
                        "erika.beispiel" + AT + "kv-beispiel.example", "EMAIL[10,44)0.95:email", "VOLL"),
                fall("AK-29b", "C:\\x\\pw=" + P1 + "\\y", P1, "PFAD[0,21)0.95:pfad-laufwerk", "VOLL"),
                fall("AK-29c", "pw=" + P1 + AT + "example.invalid", P1, "EMAIL[0,30)0.95:email", "VOLL"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ Oracle (AK-25, AK-26, G-6)

    @Test
    void messung_oracleFormenOhnePunktImZiel_heuteKeinFund_dieWertpositionIstFrei() {
        List<Fall> faelle = List.of(
                frei("AK-25a", "jdbc:oracle:thin:scott/" + P3 + AT + "dbsrv-ora01:1521/dienst", P3),
                frei("AK-25b", "sqlplus scott/" + P3 + AT + "ORCL", P3),
                frei("AK-25c", "sqlplus -S scott/" + P3 + AT + "ORCL", P3),
                frei("AK-25d", "CONNECT scott/" + P3 + AT + "ORCL", P3),
                frei("AK-25e", "scott/" + P3 + AT + "ORCL", P3),
                fall("AK-25f", "jdbc:oracle:thin:" + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", null, "", "-"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_oracleFormMitPunktImZiel_heuteEmailFundUeberBenutzerPasswortUndZiel() {
        List<Fall> faelle = List.of(
                fall("AK-26a", "jdbc:oracle:thin:scott/" + P3 + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", P3,
                        "EMAIL[17,60)0.95:email", "VOLL"),
                fall("G-6b", "sqlplus scott/" + P3 + AT + "orcl.kvbsp.local", P3, "EMAIL[8,44)0.95:email", "VOLL"),
                fall("G-6c", "sqlplus scott/" + P3 + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", P3,
                        "EMAIL[8,51)0.95:email", "VOLL"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ URLs mit Zugangsdaten (AK-28)

    @Test
    void messung_urlsMitZugangsdaten_heuteUrlFund_beiPasswordParameterEndetErVorDemAusrufezeichen() {
        List<Fall> faelle = List.of(
                fall("AK-28a", "https://svc_x:" + P1 + AT + "host.example/pfad", P1, "URL[0,43)0.95:url", "VOLL"),
                fall("AK-28b", "ftp://u:" + P1 + AT + "host.example", P1, "URL[0,32)0.95:url", "VOLL"),
                fall("AK-28c", "jdbc:postgresql://host.example/db?user=u&password=" + P1, P1, "URL[0,60)0.95:url",
                        "TEIL"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ ODBC und Kommandozeile (AK-24, AK-27)

    @Test
    void messung_odbcZeilen_heuteKeinFund() {
        List<Fall> faelle = List.of(
                frei("AK-24a", "Driver={X};Server=Beispiel;Database=Demo;Uid=u;Pwd=" + P1 + ";", P1),
                frei("AK-24b", "Server=Beispiel;Password=" + P3 + ";Database=Demo", P3),
                frei("AK-24c", "Server=Beispiel;Pwd=geheim;", "geheim"),
                frei("AK-24d", "Server=Beispiel;Pwd=Wolke Fuchs 7x;", "Wolke Fuchs 7x"),
                frei("AK-24e", "Server=Beispiel;Pwd='a;b1x'", "a;b1x"),
                frei("AK-24f", "Server=Beispiel;PWD={a;b1x}", "a;b1x"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_kommandozeile_heuteKeinFund_ausserDemPfadHinterPasswordFile() {
        List<Fall> faelle = List.of(
                frei("AK-27a", "--password=" + P1, P1),
                frei("AK-27b", "--password " + P1, P1),
                frei("AK-27c", "--passwd=" + P1, P1),
                frei("AK-27d", "--client-secret=" + T, T),
                frei("AK-27e", "--token " + T, T),
                frei("AK-27f", "--api-key=" + T, T),
                fall("AK-27g", "--password-file /etc/x", "/etc/x", "PFAD[16,22)0.8:pfad-unix", "VOLL"),
                frei("AK-27h", "-p " + P1, P1));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ KV-Nummer und Geheimnis (AK-30)

    @Test
    void messung_kvNummerNebenGeheimnis_heuteNurDerKvFund() {
        List<Fall> faelle = List.of(
                fall("AK-30a", "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + ", Passwort: " + P1, P1,
                        "BSNR[5,14)0.9:bsnr", "FREI"),
                fall("AK-30b", "LANR " + SynthetischeKvNummern.LANR_GUELTIG + ", Token: " + T, T,
                        "LANR[5,14)0.95:lanr", "FREI"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ Hex, Base64, UUID, JWT (AK-19 bis AK-21)

    @Test
    void messung_hexBase64FingerprintUuid_heuteKeinFund() {
        List<Fall> faelle = List.of(
                frei("AK-20a", SynthetischeGeheimnisse.hex32(), SynthetischeGeheimnisse.hex32()),
                frei("AK-20b", SynthetischeGeheimnisse.hex40(), SynthetischeGeheimnisse.hex40()),
                frei("AK-20c", SynthetischeGeheimnisse.hex64(), SynthetischeGeheimnisse.hex64()),
                frei("AK-20d", "sha256:" + SynthetischeGeheimnisse.hex64(), SynthetischeGeheimnisse.hex64()),
                frei("AK-20e", SynthetischeGeheimnisse.hex7(), SynthetischeGeheimnisse.hex7()),
                frei("AK-20f", SynthetischeGeheimnisse.base64Zeile60(), SynthetischeGeheimnisse.base64Zeile60()),
                frei("AK-20g", "Passwort-Hash: " + SynthetischeGeheimnisse.hex64(), SynthetischeGeheimnisse.hex64()),
                fall("AK-20h", SynthetischeGeheimnisse.fingerprint(), null, "", "-"),
                frei("AK-19a", "Request-ID: " + SynthetischeGeheimnisse.uuid(), SynthetischeGeheimnisse.uuid()),
                frei("AK-19b", "apikey: " + SynthetischeGeheimnisse.uuid(), SynthetischeGeheimnisse.uuid()));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_jwt_heuteKeinFund_inEinerUrlEinUrlFundUeberDieGanzeAdresse() {
        String jwt = SynthetischeGeheimnisse.jwt();
        List<Fall> faelle = List.of(
                frei("AK-21a", jwt, jwt),
                frei("AK-21b", "Authorization: Bearer " + jwt, jwt),
                frei("AK-21c", "Das Token " + jwt + " ist abgelaufen.", jwt),
                fall("AK-21d", "https://example.invalid/x?t=" + jwt, jwt, "URL[0,92)0.95:url", "VOLL"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ PEM-Koerper (AK-22, G-18)

    @Test
    void messung_pemKoerperOhneSchraegstrichZeile_heuteKeinFund() {
        List<Fall> faelle = new ArrayList<>();
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of("\n", "\r\n")) {
                String block = SynthetischeGeheimnisse.pemBlock(art, ze, true);
                String zeile = SynthetischeGeheimnisse.pemKoerperZeilen().get(0);
                faelle.add(frei("AK-22/" + art.length() + "/" + ze.length(), block, zeile));
            }
        }
        faelle.add(frei("AK-22/ohneEnde", SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\n", false),
                SynthetischeGeheimnisse.pemKoerperZeilen().get(0)));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_pemKoerperMitSchraegstrichZeile_heuteUnixPfadFundUeberDieZeile() {
        String zeile = SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich().get(0);
        String lf = SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", SynthetischeGeheimnisse
                .pemKoerperZeilenMitSchraegstrich(), "\n", true);
        String crlf = SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", SynthetischeGeheimnisse
                .pemKoerperZeilenMitSchraegstrich(), "\r\n", true);
        List<Fall> faelle = List.of(
                fall("G-18/LF", lf, zeile, "PFAD[28,91)0.8:pfad-unix", "VOLL"),
                fall("G-18/CRLF", crlf, zeile, "PFAD[29,92)0.8:pfad-unix", "VOLL"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    // ------------------------------------------------------------------ Positivbeispiele (AK-2 bis AK-23)

    @Test
    void messung_kontextwoerterMitDoppelpunktUndGleichheitszeichen_heuteKeinFundAufDemWert() {
        List<Fall> faelle = new ArrayList<>();
        for (String wort : List.of("Passwort", "passwort", "KENNWORT", "Kennwort", "password", "Password", "passwd",
                "Passphrase", "Initialpasswort", "Einmalkennwort", "Adminpasswort", "Root-Passwort", "db-passwort",
                "PW", "pw", "Pwd")) {
            faelle.add(frei("AK-2/" + wort, wort + ": " + P1, P1));
        }
        for (String wort : List.of("DB_PASSWORD", "dbPassword", "pwd")) {
            faelle.add(frei("AK-2e/" + wort, wort + "=" + P1, P1));
        }
        for (String wort : List.of("Token", "access_token", "accessToken", "refresh_token", "idToken", "API-Key",
                "apikey", "api_key", "X-API-Key", "API Key", "Secret", "client_secret", "clientSecret")) {
            faelle.add(frei("AK-17/" + wort, wort + ": " + T, T));
        }
        faelle.add(frei("AK-18a", "Authorization: Bearer " + T, T));
        faelle.add(frei("AK-18b", "Authorization: Basic " + T, T));
        faelle.add(frei("AK-18c", "Bearer " + T, T));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_verbinderZwischenteileUmbruecheAnfuehrungszeichen_heuteKeinFundAufDemWert() {
        List<Fall> faelle = new ArrayList<>();
        String[] texte = {"Passwort = " + P1, "Passwort : " + P1, "Passwort:" + P1, "Passwort ist " + P1,
            "Passwort lautet " + P1, "password is " + P1, "Passwort\t" + P1, "| Passwort | " + P1 + " |",
            "<password>" + P1 + "</password>", "\"password\": \"" + P1 + "\"", "password: '" + P1 + "'",
            "password = \"" + P1 + "\"", "Passwort f" + UE + "r svc_x: " + P1,
            "Das Passwort f" + UE + "r den Benutzer svc_x lautet " + P1, "Passwort des Kontos KVBSP\\svc_x: " + P1,
            "password for root: " + P1, "Passwort von SRV-APP07 ist " + P1, "Passwort:\n" + P1,
            "Passwort:\r\n    " + P1, "Passwort =\n" + P1, "Passwort: " + P1 + ".", "(Passwort: " + P1 + ")",
            "Benutzer: svc_x, Passwort: " + P1 + ", Token: " + T, "Passwort: " + P1 + " Passwort: " + P2};
        for (int i = 0; i < texte.length; i++) {
            faelle.add(frei("AK-4/" + i, texte[i], P1));
        }
        faelle.add(frei("AK-8a", "Passwort: \"Wolke Fuchs 7x!\"", "Wolke Fuchs 7x!"));
        faelle.add(frei("AK-11a", "Passwort: Pa$$word", "Pa$$word"));
        faelle.add(frei("AK-11b", "Passwort: " + P3, P3));
        faelle.add(frei("AK-10a", "Passwort: " + SynthetischeGeheimnisse.P6, SynthetischeGeheimnisse.P6));
        faelle.add(frei("AK-10b", "Passwort: " + SynthetischeGeheimnisse.P7, SynthetischeGeheimnisse.P7));
        faelle.add(frei("AK-16a", "Passwort ist Sommer2026", "Sommer2026"));
        faelle.add(frei("AK-16b", "Passwort: hunter2x", "hunter2x"));
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_praefixToken_heuteKeinFund() {
        List<Fall> faelle = new ArrayList<>();
        List<String> praefixe = SynthetischeGeheimnisse.praefixe();
        for (int i = 0; i < praefixe.size(); i++) {
            String token = SynthetischeGeheimnisse.praefixToken(praefixe.get(i));
            faelle.add(frei("AK-23/" + i, token, token));
        }
        assertEquals(List.of(), abweichungen(faelle));
    }

    @Test
    void messung_kontextwoerterOhneWert_heuteKeinFund() {
        List<Fall> faelle = List.of(
                fall("AK-33a", "Passwort zur" + UE + "ckgesetzt", null, "", "-"),
                fall("AK-33b", "Passwort ist abgelaufen", null, "", "-"),
                fall("AK-34a", "Token: ung" + UE + "ltig (HTTP 401)", null, "", "-"),
                fall("AK-34b", "password: required", null, "", "-"));
        assertEquals(List.of(), abweichungen(faelle));
    }
}
