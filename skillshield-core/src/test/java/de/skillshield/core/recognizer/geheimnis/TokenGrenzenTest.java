package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P_BEZEICHNER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_PLATZHALTERARTIG;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static de.skillshield.core.SynthetischeGeheimnisse.T_15;
import static de.skillshield.core.SynthetischeGeheimnisse.T_16;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeGeheimnisse;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
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

    // ------------------------------------------------------------------ T-7: kontextfreie Formen

    private static final String LF = "\n";
    private static final String CRLF = "\r\n";

    private static String koerper(String ze) {
        return String.join(ze, SynthetischeGeheimnisse.pemKoerperZeilen());
    }

    /** G-16: Ein Praefix ausserhalb der kurzen Liste (D-11) ergibt ohne Kontextwort keinen Fund, mit Kontextwort. */
    @Test
    void bekannteGrenze_g16_praefixAusserhalbDerListe_keinFund_mitKontextwortEinFund() {
        String fremd = "gh" + "x_" + SynthetischeGeheimnisse.PRAEFIX_REST;
        assertEquals(List.of(), tokens("Der Wert " + fremd + " wurde gesetzt"));
        String vor = "Token: ";
        assertEquals(List.of(token(vor, fremd, 0.95)), tokens(vor + fremd));
    }

    /** D-11: Ein Praefix-Token unter 20 Zeichen oder ohne Ziffer bleibt sichtbar (der Kontext macht es zum Fund). */
    @Test
    void bekannteGrenze_praefixTokenUnter20ZeichenOderOhneZiffer_keinFund_mitKontextwortEinFund() {
        String praefix = SynthetischeGeheimnisse.praefixe().get(0);
        String kurz = praefix + "7Testwert";
        String ohneZiffer = praefix + "AbcdTestwertEfghIjklMnop";
        assertEquals(List.of(), tokens("Der Wert " + kurz + " wurde gesetzt"));
        assertEquals(List.of(), tokens("Der Wert " + ohneZiffer + " wurde gesetzt"));
        assertEquals(List.of(token("Token: ", kurz, 0.95)), tokens("Token: " + kurz), "kurz, mit Kontextwort");
        // ohne Ziffer ist es auch mit Kontextwort kein Fund (W2: nur Buchstaben)
        assertEquals(List.of(), tokens("Token: " + ohneZiffer));
    }

    /**
     * Praezisierung zu D-11 (T-7, gemessen an der Stichprobe): Bei den klassischen GitHub-Praefixen ist ein
     * {@code _} oder {@code -} im Rest kein Token, sondern ein Bezeichner. Bei {@code glpat-}, {@code xoxb-} und
     * {@code xoxp-} sind solche Zeichen Teil der Form; ein Bezeichner mit diesem Anfang ist dort ein Fund
     * (Ueberschutz, Fehlalarm).
     */
    @Test
    void bekannteGrenze_praefixBezeichnerMitUnterstrich_klassischeGithubPraefixeKeinFund_andereSindFunde() {
        for (String praefix : SynthetischeGeheimnisse.praefixe().subList(0, 5)) {
            assertEquals(List.of(), tokens("Funktion " + praefix + "refresh_aufruf_2 ist veraltet"),
                    "Bezeichner mit " + praefix.length());
            assertEquals(List.of(), tokens("Funktion " + praefix + "refresh-aufruf-2 ist veraltet"), "mit Bindestrich");
        }
        for (String praefix : SynthetischeGeheimnisse.praefixe().subList(5, 9)) {
            assertEquals(1, tokens("Datei " + praefix + "export-status_2026-v2 erstellt").size(),
                    "Bezeichner mit " + praefix.length());
        }
    }

    /** G-17: Der PEM-Koerper ist ein einziger, mehrzeiliger Fund; die Rueckuebersetzung ist zeichengleich. */
    @Test
    void bekannteGrenze_g17_pemKoerperIstEinMehrzeiligerFund_rueckuebersetzungZeichengleich() {
        for (String ze : List.of(LF, CRLF)) {
            String text = SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", ze, true);
            List<Finding> funde = tokens(text);
            assertEquals(1, funde.size(), "ein Fund");
            assertTrue(funde.get(0).originalValue().equals(koerper(ze)), "ganzer Koerper");
            assertTrue(funde.get(0).originalValue().contains(ze), "mehrzeilig");
            PseudonymizationResult pseudo = new Pseudonymizer().pseudonymize(text, STANDARD.scan(text));
            assertTrue(new Reidentifier().restore(pseudo.text(), pseudo.mapping()).text().equals(text),
                    "Rueckuebersetzung Zeilenende " + ze.length());
        }
    }

    /**
     * G-18: Beginnt eine Zeile des PEM-Koerpers mit einem Schraegstrich, erkennt der bestehende Pfad-Erkenner sie als
     * Unix-Pfad. Dieser Fund bleibt unveraendert (Lueckenregel), der Koerper zerfaellt in Stuecke daneben; kein Zeichen
     * des Koerpers bleibt frei, und es gibt keinen TOKEN-Fund ueber den ganzen Koerper.
     */
    @Test
    void bekannteGrenze_g18_pemKoerperMitSchraegstrichZeile_zerfaelltInStuecke_keinZeichenFrei() {
        for (String ze : List.of(LF, CRLF)) {
            String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
            String wert = String.join(ze, SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich());
            String text = kopf + ze + wert + ze + SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
            int von = kopf.length() + ze.length();
            List<Finding> alle = STANDARD.scan(text);
            assertTrue(alle.stream().anyMatch(f -> f.type().equals(FindingType.PFAD)), "der Pfad-Fund bleibt");
            assertTrue(alle.stream().anyMatch(f -> f.type().equals(GeheimnisTypen.TOKEN)), "Stuecke als TOKEN");
            assertFalse(alle.stream().anyMatch(f -> f.type().equals(GeheimnisTypen.TOKEN)
                    && f.originalValue().equals(wert)), "kein Fund ueber den ganzen Koerper");
            assertTrue(alleGeschuetzt(text, von, von + wert.length()), "kein Zeichen des Koerpers bleibt frei");
        }
    }

    /**
     * Grenze (eingeengt nach T-7, Durchlauf 2): Ein PEM in einer Zeile mit maskierten Zeilenumbruechen (zwei Zeichen,
     * JSON) ist nur dann ein Fund, wenn die END-Markierung folgt; ohne sie bleibt der Koerper lesbar. Mit
     * END-Markierung ist es ein Fund (TokenFormenTest).
     */
    @Test
    void bekannteGrenze_pemInEinerZeileMitMaskiertenZeilenumbruechenOhneEnd_keinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String maskiert = "\\n";
        String zeilen = String.join(maskiert, SynthetischeGeheimnisse.pemKoerperZeilen());
        String ohneEnd = "{\"pem\": \"" + kopf + maskiert + zeilen + maskiert + "\"}";
        assertEquals(List.of(), new TokenRecognizer().detect(ohneEnd));
        assertEquals(List.of(), tokens(ohneEnd));
        String mitEnd = "{\"pem\": \"" + kopf + maskiert + zeilen + maskiert + fuss + maskiert + "\"}";
        assertEquals(1, tokens(mitEnd).size(), "Gegenprobe mit END-Markierung");
    }

    /**
     * Grenze (eingeengt nach T-7, Durchlauf 2): Steht Text vor der BEGIN-Zeichenfolge auf derselben Zeile und folgt
     * keine END-Markierung, ist es keine BEGIN-Zeile der Zeilenform und kein Fund.
     */
    @Test
    void bekannteGrenze_beginZeileMitTextDavorOhneEnd_keinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        assertEquals(List.of(), tokens("Schluessel " + kopf + LF + koerper(LF)), "ohne END");
        assertEquals(List.of(), tokens("Schluessel " + kopf + " " + koerper(" ")), "in einer Zeile ohne END");
        // Gegenproben: mit END-Markierung (Form zwischen Markierungen) und ohne Text davor ist es ein Fund
        assertEquals(1, tokens("Schluessel " + kopf + LF + koerper(LF) + LF + fuss).size(), "mit END");
        assertEquals(1, tokens(kopf + LF + koerper(LF) + LF + fuss).size(), "ohne Text davor");
    }

    /** Grenze: Liegen BEGIN- und END-Markierung mehr als 16 KiB auseinander, ist die Form zwischen ihnen kein Fund. */
    @Test
    void bekannteGrenze_pemBeginUndEndUeber16KibAuseinander_formZwischenMarkierungenKeinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String lang = SynthetischeGeheimnisse.base64Zeile(16 * 1024 + 1);
        assertEquals(List.of(), tokens("x " + kopf + " " + lang + " " + fuss), "mehr als 16 KiB");
        assertEquals(1, tokens("x " + kopf + lang.substring(1) + fuss).size(), "genau 16 KiB");
    }

    /**
     * Grenze (Fehlalarm, eng seit T-7, Durchlauf 3): Ein Wort von 16 Zeichen oder mehr zwischen den Markierungen ist
     * nur dann ein Fund, wenn es fast allein dort steht (mindestens 70 Prozent der Zeichen ausserhalb von Leerraum).
     * Ein Satz mit weiteren Woertern und ein Erklaertext ueber mehrere Absaetze (TokenFormenTest) sind kein Fund.
     */
    @Test
    void bekannteGrenze_langesWortZwischenDenMarkierungen_nurAlleinEinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        assertEquals(1, tokens("Doku: " + kopf + " Schluesselverwaltungsinhalt " + fuss).size(), "Wort allein");
        assertEquals(1, tokens("Doku: " + kopf + " Das ist Schluesselverwaltungsinhalt " + fuss).size(),
                "Wort mit zwei kurzen Woertern (82 Prozent)");
        assertEquals(List.of(), tokens("Doku: " + kopf + " Schluesselverwaltungsinhalt fuer alle Faelle " + fuss),
                "Wort mit drei Woertern (66 Prozent)");
        assertEquals(List.of(), tokens("Doku: " + kopf + " Schluessel einfuegen " + fuss), "kurze Woerter");
        // Pfad statt langem Wort: ein Satz mit einem Pfad unter /etc/ssl/private/ ist ebenso ein Fund (Ueberschutz),
        // solange der Pfad fast allein zwischen den Markierungen steht
        String pfad = "/etc/ssl/private/dienstschluessel";
        assertEquals(1, tokens("Doku: " + kopf + " Siehe " + pfad + " " + fuss).size(), "Pfad allein (Ueberschutz)");
        assertEquals(List.of(), tokens("Doku: " + kopf + " Siehe " + pfad + " fuer alle Faelle hier " + fuss),
                "Pfad mit weiteren Woertern");
    }

    /** Kopfzeilen eines verschluesselten Schluessels und ein kurzer Koerper, zur Laufzeit gebaut (kein PEM-Literal). */
    private static List<String> verschluesselterSchluessel() {
        List<String> zeilen = new ArrayList<>();
        zeilen.add("Proc-Type: 4,ENCRYPTED");
        zeilen.add("DEK-Info: AES-128-CBC," + SynthetischeGeheimnisse.hex32());
        zeilen.add("");
        zeilen.addAll(SynthetischeGeheimnisse.pemKoerperZeilen());
        return zeilen;
    }

    private static String schluesselMit(String trenner) {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("EC PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("EC PRIVATE KEY");
        return kopf + trenner + String.join(trenner, verschluesselterSchluessel()) + trenner + fuss;
    }

    /**
     * Grenze (security, mittel; Entscheidung des Nutzers 28.11.2026, nicht behoben): Zwischen den Markierungen zaehlt
     * die 70-Prozent-Schwelle. Standard-Kopfzeilen ({@code Proc-Type}, {@code DEK-Info}) und Markup-Trenner
     * ({@code &#13;&#10;}, {@code <br />}, {@code " + Environment.NewLine}) druecken den Anteil darunter: der
     * verschluesselte Schluessel bleibt dann ungeschuetzt. Am Zeilenanfang (Zeilenform) und mit {@code \n} als Trenner
     * ist derselbe Schluessel ein Fund (Gegenproben).
     */
    @Test
    void bekannteGrenze_pemZwischenMarkierungenMitKopfzeilenUndMarkupTrennern_unterSchwelle_keinFund() {
        String xml = "<key>" + schluesselMit("&#13;&#10;") + "</key>";
        assertEquals(List.of(), tokens(xml), "XML mit Zeichenverweisen");

        String html = "<blockquote>" + schluesselMit("<br />&gt; ") + "</blockquote>";
        assertEquals(List.of(), tokens(html), "HTML mit br und Zitat");

        String csharp = "string k = \"" + schluesselMit("\" + Environment.NewLine + \"") + "\";";
        assertEquals(List.of(), tokens(csharp), "C# mit Verkettung");

        // gemessen: ein blosses br ohne Zitatzeichen laesst diesen kurzen Schluessel noch ueber der Schwelle
        assertEquals(1, tokens("<p>" + schluesselMit("<br />") + "</p>").size(), "HTML nur mit br (ueber Schwelle)");

        // Gegenproben: Zeilenform am Zeilenanfang und JSON mit maskiertem Zeilenumbruch sind Funde
        String zeilenform = schluesselMit(LF);
        assertEquals(1, tokens(zeilenform).size(), "Zeilenform");
        String json = "{\"key\":\"" + schluesselMit("\\n") + "\"}";
        assertEquals(1, tokens(json).size(), "JSON mit maskiertem Zeilenumbruch");
    }

    /**
     * Grenze: Steht zwischen BEGIN und END privater Art die BEGIN-Markierung einer oeffentlichen Art, ist die Form
     * zwischen den Markierungen kein Fund (eine weitere BEGIN-Markierung dazwischen), auch wenn Schluesselinhalt
     * folgt.
     */
    @Test
    void bekannteGrenze_beginMarkierungOeffentlicherArtZwischenBeginUndEndPrivaterArt_keinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String oeffentlich = SynthetischeGeheimnisse.pemKopfzeile("PUBLIC KEY");
        String text = "x " + kopf + " " + koerper(" ") + " " + oeffentlich + " " + koerper(" ") + " " + fuss;
        assertEquals(List.of(), tokens(text), "mit Leerzeichen");
        assertEquals(List.of(), new TokenRecognizer().detect(text), "Kandidaten");
    }

    /**
     * Die END-Markierung unmittelbar an der letzten Koerperzeile (ohne Zeilenumbruch davor) ist keine END-Zeile der
     * Zeilenform; die Form zwischen den Markierungen schuetzt den ganzen Koerper bis zur END-Markierung. Das ist
     * keine Grenze (seit T-7, Durchlauf 3), sondern gedeckt.
     */
    @Test
    void pemEndMarkierungAnDieLetzteKoerperzeileGeklebt_koerperIstGeschuetzt() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String text = kopf + LF + koerper(LF) + fuss;
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "ein Fund");
        assertEquals(kopf.length() + 1, funde.get(0).start());
        assertEquals(text.length() - fuss.length(), funde.get(0).end(), "bis zur END-Markierung");
    }

    /** Grenze: Ein PuTTY-Schluessel hat keine BEGIN-Markierung und wird nicht erkannt. */
    @Test
    void bekannteGrenze_puttySchluessel_keinFund() {
        String text = String.join(LF, "PuTTY-User-Key-File-3: ssh-ed25519", "Encryption: none", "Comment: dienstkonto",
                "Public-Lines: 2", SynthetischeGeheimnisse.base64Zeile(64), SynthetischeGeheimnisse.base64Zeile(22),
                "Private-Lines: 1", SynthetischeGeheimnisse.base64Zeile(64),
                "Private-MAC: " + SynthetischeGeheimnisse.hex64());
        assertEquals(List.of(), tokens(text));
    }

    /** Grenze: Zwischen BEGIN und END ein Platzhalter oder Satz ohne 16 Base64-Zeichen in einer Zeile ist kein Fund. */
    @Test
    void bekannteGrenze_pemKoerperOhneSchluesselinhalt_keinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        assertEquals(List.of(), tokens(kopf + LF + "..." + LF + fuss));
        assertEquals(List.of(), tokens(kopf + LF + "(hier den Schluessel einfuegen)" + LF + fuss));
        assertEquals(List.of(), tokens(kopf + LF + "Abcdefgh123Abcd" + LF + fuss), "15 Zeichen");
    }

    /** D-11: Ohne END-Zeile gehoert jede folgende Base64-Zeile dazu, auch ein einzelnes Wort (Ueberschutz). */
    @Test
    void bekannteGrenze_pemOhneEnde_dieFolgendeWortzeileGehoertZumKoerper() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String text = kopf + LF + koerper(LF) + LF + "Ende";
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "ein Fund");
        assertTrue(funde.get(0).originalValue().equals(koerper(LF) + LF + "Ende"), "inklusive der Wortzeile");
    }

    /** Grenze: Ein JWT mit vier Teilen oder mit einem Auslassungszeichen in den Teilen ist kein Fund. */
    @Test
    void bekannteGrenze_jwtMitVierTeilenOderAuslassungszeichen_keinFund() {
        List<String> teile = List.of(SynthetischeGeheimnisse.jwt().split("\\."));
        String vier = String.join(".", teile) + "." + teile.get(2);
        assertEquals(List.of(), tokens("Muster " + vier));
        char auslassung = (char) 0x2026;
        String gekuerzt = teile.get(0).substring(0, 12) + auslassung + "." + teile.get(1).substring(0, 12) + auslassung
                + "." + teile.get(2).substring(0, 12) + auslassung;
        assertEquals(List.of(), tokens("Muster " + gekuerzt));
        String punkte = "ey" + "J" + "..." + "." + "ey" + "J" + "..." + ".abc";
        assertEquals(List.of(), tokens("Muster " + punkte));
    }

    /**
     * Grenzen der JWT-Form (T-7, Durchlauf 2): Ein JWE mit fuenf Teilen, ein durch Zeilenumbruch gebrochenes JWT und
     * ein Wort, das mit einem Punkt unmittelbar am JWT klebt ({@code .Wort}, ein vierter Teil), sind kein Fund.
     * Punkte am Ende ({@code ...}) gehoeren dagegen nicht zum JWT, der Fund bleibt.
     */
    @Test
    void bekannteGrenze_jweMitFuenfTeilenUmgebrochenesJwtUndAngeklebtesWort_keinFund() {
        List<String> teile = List.of(SynthetischeGeheimnisse.jwt().split("\\."));
        String jwe = String.join(".", teile.get(0), SynthetischeGeheimnisse.base64Zeile(40),
                SynthetischeGeheimnisse.base64Zeile(16), SynthetischeGeheimnisse.base64Zeile(40),
                SynthetischeGeheimnisse.base64Zeile(22));
        assertEquals(List.of(), tokens("Das Token war " + jwe), "JWE mit fuenf Teilen");
        String bruch1 = teile.get(0) + "." + teile.get(1) + ".";
        assertEquals(List.of(), tokens("Das Token war " + bruch1 + LF + teile.get(2)), "Umbruch vor dem dritten Teil");
        String bruch2 = teile.get(0) + ".";
        assertEquals(List.of(), tokens("Das Token war " + bruch2 + LF + teile.get(1) + "." + teile.get(2)),
                "Umbruch nach dem ersten Teil");
        assertEquals(List.of(), tokens("Das Token war " + SynthetischeGeheimnisse.jwt() + ".Wort"), "angeklebtes Wort");
        assertEquals(1, tokens("Das Token war " + SynthetischeGeheimnisse.jwt() + "...").size(), "Gegenprobe Punkte");
    }

    /**
     * Grenze (T-7, Durchlauf 2): Ein Praefix-Token ohne Ziffer im Rest ist kein Fund, bei jedem Praefix der Liste
     * (bei {@code glpat-} etwa 3 % der echten Token); mit Kontextwort ebenso (W2: nur Buchstaben).
     */
    @Test
    void bekannteGrenze_praefixTokenOhneZiffer_keinFund_beiJedemPraefix() {
        String rest = "AbcdTestwertEfghIjklMnopQrst";
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            assertEquals(List.of(), tokens("Der Wert " + praefix + rest + " wurde gesetzt"),
                    "Praefix der Laenge " + praefix.length());
            assertEquals(List.of(), tokens("Token: " + praefix + rest), "mit Kontextwort " + praefix.length());
        }
    }

    /**
     * Grenze (Fehlalarm, T-7, Durchlauf 2): Ein Bezeichner mit {@code glpat-}, {@code xoxb-}, {@code xoxp-} oder
     * {@code github_pat_} am Anfang und mindestens einer Ziffer ist ein Fund; ein engeres Format vertraegt sich
     * nicht mit dem eingefrorenen Pruefstand.
     */
    @Test
    void bekannteGrenze_bezeichnerMitHerstellerPraefixUndZiffer_istEinFund() {
        List<String> praefixe = SynthetischeGeheimnisse.praefixe();
        for (String praefix : List.of(praefixe.get(5), praefixe.get(6), praefixe.get(7), praefixe.get(8))) {
            assertEquals(1, tokens("Spalte " + praefix + "export_version_2 ist neu").size(),
                    "Bezeichner mit " + praefix.length());
        }
    }

    /**
     * Grenze von T-6 ({@code <token>} mit mehr als 256 Zeichen bleibt lesbar), Umstellung durch T-7: Ein langes JWT
     * im Element ist jetzt durch die Form geschuetzt; die Elementregel selbst liest weiter hoechstens 256 Zeichen.
     */
    @Test
    void bekannteGrenze_langesJwtImElement_nurDieFormSchuetzt_dieElementregelBleibtBeiDer256Grenze() {
        List<String> teile = List.of(SynthetischeGeheimnisse.jwt().split("\\."));
        String lang = teile.get(0) + "." + teile.get(1) + "." + teile.get(2) + "x".repeat(300);
        String vor = "<token>";
        String text = vor + lang + "</token>";
        assertEquals(List.of(new Finding(GeheimnisTypen.TOKEN, lang, vor.length(), vor.length() + lang.length(), 0.95,
                TokenRecognizer.QUELLE_JWT)), tokens(text));
        // ein langer Wert, der kein JWT ist, bleibt lesbar (T-6)
        String basis = SynthetischeGeheimnisse.base64Zeile(300);
        assertEquals(List.of(), tokens(vor + basis + "</token>"));
    }
}
