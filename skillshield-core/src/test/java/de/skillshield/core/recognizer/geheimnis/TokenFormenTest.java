package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.MARKE;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Die kontextfreien Formen JWT, PEM-Koerper und Praefix-Token (Plan geheimnisse-erkennung, T-7; Anforderung D-10,
 * D-11, AK-20 bis AK-23, AK-40 fuer PEM). Alle Werte kommen aus {@code SynthetischeGeheimnisse} und entstehen zur
 * Laufzeit aus Teilen (AK-50); Werte stehen nie in einer Testmeldung: Texte werden mit {@code assertTrue(a.equals(b),
 * Fall-ID)} verglichen, Fundlisten ({@code Finding.toString()} ist klartextfrei) mit {@code assertEquals}.
 */
class TokenFormenTest {

    private static final String LF = "\n";
    private static final String CRLF = "\r\n";

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TokenRecognizer ERKENNER = new TokenRecognizer();
    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    private static String jwt() {
        return SynthetischeGeheimnisse.jwt();
    }

    private static List<String> jwtTeile() {
        return List.of(jwt().split("\\."));
    }

    private static Finding form(int start, String wert, String quelle) {
        return new Finding(GeheimnisTypen.TOKEN, wert, start, start + wert.length(), 0.95, quelle);
    }

    private static Finding form(String vor, String wert, String quelle) {
        return form(vor.length(), wert, quelle);
    }

    private static List<Finding> tokens(String text) {
        List<Finding> liste = new ArrayList<>();
        for (Finding f : STANDARD.scan(text)) {
            if (f.type().equals(GeheimnisTypen.TOKEN)) {
                liste.add(f);
            }
        }
        return liste;
    }

    private static List<Finding> geheimnisse(String text) {
        return STANDARD.scan(text).stream().filter(f -> GeheimnisTypen.istGeheimnistyp(f.type())).toList();
    }

    private static String koerper(String ze) {
        return String.join(ze, SynthetischeGeheimnisse.pemKoerperZeilen());
    }

    // ------------------------------------------------------------------ AK-20: kein Fund ohne Kontext

    @Test
    void ak20_hashesKennungenUndBase64OhneKontext_keinGeheimnisFundUndKeinKandidat() {
        List<String> texte = List.of(SynthetischeGeheimnisse.hex32(), SynthetischeGeheimnisse.hex40(),
                SynthetischeGeheimnisse.hex64(), "sha256:" + SynthetischeGeheimnisse.hex64(),
                "Commit " + SynthetischeGeheimnisse.hex7(), "Commit " + SynthetischeGeheimnisse.hex40(),
                SynthetischeGeheimnisse.fingerprint(), SynthetischeGeheimnisse.base64Zeile60(),
                "Passwort-Hash: " + SynthetischeGeheimnisse.hex64(), SynthetischeGeheimnisse.uuid(),
                "Request-ID: " + SynthetischeGeheimnisse.uuid(), "Objekt " + SynthetischeGeheimnisse.uuid(),
                "SHA256:" + SynthetischeGeheimnisse.base64Zeile(43));
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), geheimnisse(texte.get(i)), "Scan Text " + i);
            assertEquals(List.of(), ERKENNER.detect(texte.get(i)), "Kandidaten Text " + i);
        }
    }

    // ------------------------------------------------------------------ AK-21: JWT

    @Test
    void ak21_jwtAllein_einFundTokenUeberDasGanzeJwt() {
        String text = jwt();
        assertEquals(List.of(form("", text, TokenRecognizer.QUELLE_JWT)), tokens(text));
        assertEquals(List.of(form("", text, TokenRecognizer.QUELLE_JWT)), ERKENNER.detect(text));
    }

    @Test
    void ak21_jwtImSatz_einFund() {
        String vor = "Das Token ";
        String text = vor + jwt() + " ist abgelaufen.";
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(text));
    }

    @Test
    void ak21_jwtHinterAuthorizationBearer_genauEinFundMitQuelleJwt() {
        String vor = "Authorization: Bearer ";
        String text = vor + jwt();
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), funde);
        int bearer = text.indexOf("Bearer");
        assertTrue(bearer >= 0 && funde.get(0).start() >= bearer + "Bearer".length(), "Bearer bleibt Klartext");
        assertEquals(1, ERKENNER.detect(text).size(), "ein Kandidat");
    }

    /**
     * Begruendung der Quelle hinter {@code Authorization: Bearer} (Entscheidung nach T-2, Punkt 5): Die Kontextregel
     * von T-6 liefert fuer denselben Bereich einen Kandidaten mit Quelle {@code token-kontext} und derselben Konfidenz;
     * der Formen-Kandidat ersetzt ihn. Beide schuetzen dieselben Positionen, die Form ist die vom Kontext
     * unabhaengige und damit staerkere Aussage, und die Quelle ist nicht dem letzten Gleichstandskriterium des
     * {@code ConflictResolver} ueberlassen.
     */
    @Test
    void ak21_jwtHinterBearer_kontextKandidatVonT6HatDenSelbenBereich_dieFormErsetztIhn() {
        String vor = "Authorization: Bearer ";
        String jwtMitZiffer = jwt() + "7";
        String text = vor + jwtMitZiffer;
        List<Finding> t6 = new TokenRecognizerStandT6().detect(text);
        assertEquals(List.of(new Finding(GeheimnisTypen.TOKEN, jwtMitZiffer, vor.length(), text.length(), 0.95,
                TokenRecognizer.QUELLE_KONTEXT)), t6);
        assertEquals(List.of(form(vor, jwtMitZiffer, TokenRecognizer.QUELLE_JWT)), ERKENNER.detect(text));
    }

    /**
     * Das JWT der zentralen Klasse hat keine Ziffer: Die Kontextregel (W2: Buchstabe und Ziffer oder Sonderzeichen,
     * {@code .} und {@code -} zaehlen nicht) liefert hinter {@code Authorization: Bearer} fuer dieses JWT keinen
     * Kandidaten. Die Quelle {@code token-jwt} ist dann die einzige; mit einer Ziffer ersetzt die Form den
     * Kontext-Kandidaten (vorheriger Test). In beiden Faellen ist der Fund ein TOKEN ueber das JWT mit 0,95.
     */
    @Test
    void ak21_jwtOhneZiffer_dieKontextregelLiefertKeinenKandidaten_nurDieFormSchuetzt() {
        String vor = "Authorization: Bearer ";
        assertEquals(List.of(), new TokenRecognizerStandT6().detect(vor + jwt()));
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), ERKENNER.detect(vor + jwt()));
    }

    @Test
    void ak21_jwtHinterKontextwortMitSchwachemVerbinder_dieFormHebtDieKonfidenzAuf095() {
        String vor = "Token ist ";
        String jwtMitZiffer = jwt() + "7";
        String text = vor + jwtMitZiffer;
        List<Finding> t6 = new TokenRecognizerStandT6().detect(text);
        assertEquals(1, t6.size(), "T-6");
        assertEquals(0.80, t6.get(0).confidence(), 1e-9, "Konfidenz T-6");
        assertEquals(List.of(form(vor, jwtMitZiffer, TokenRecognizer.QUELLE_JWT)), ERKENNER.detect(text));
        String k1 = "Token: " + jwtMitZiffer;
        assertEquals(List.of(form("Token: ", jwtMitZiffer, TokenRecognizer.QUELLE_JWT)), tokens(k1));
    }

    @Test
    void ak21_keinFund_zweiTeile_teilUnterAcht_ersteTeileOhneAnfang() {
        List<String> teile = jwtTeile();
        String k = teile.get(0);
        String n = teile.get(1);
        String s = teile.get(2);
        List<String> texte = List.of(k + "." + n, k.substring(0, 7) + "." + n + "." + s,
                k + "." + n.substring(0, 7) + "." + s, k + "." + n + "." + s.substring(0, 7),
                "ab" + k.substring(2) + "." + n + "." + s, k + "." + "ab" + n.substring(2) + "." + s,
                k + "." + n + "." + s + ".Abcdefgh", k + "." + n + "." + ".");
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), geheimnisse(texte.get(i)), "Text " + i);
            assertEquals(List.of(), ERKENNER.detect(texte.get(i)), "Kandidat Text " + i);
        }
    }

    @Test
    void ak21_keinFund_wennDavorEinBuchstabeEinePunktEinBindestrichOderEinUnterstrichSteht() {
        for (String davor : List.of("x", "7", ".", "-", "_", "A")) {
            String text = "Wert " + davor + jwt();
            assertEquals(List.of(), geheimnisse(text), "davor " + (int) davor.charAt(0));
        }
        // Gegenprobe: Zeichen ausserhalb der JWT-Zeichen davor sind eine Grenze
        for (String davor : List.of("(", "\"", "=", ":", ">", "'")) {
            String text = "Wert " + davor + jwt();
            assertEquals(1, geheimnisse(text).size(), "davor " + (int) davor.charAt(0));
        }
    }

    @Test
    void ak21_keinFund_wennDahinterEinBuchstabeEinePunktFolgeOderEinUmlautSteht() {
        assertEquals(List.of(), geheimnisse("Wert " + jwt() + (char) 0xE4), "Umlaut dahinter");
        assertEquals(List.of(), geheimnisse("Wert " + jwt() + ".Abcdefgh"), "vierter Teil");
        assertEquals(List.of(), geheimnisse((char) 0xE4 + jwt()), "Umlaut davor");
    }

    /** Praezisierung zu D-11: Ein einzelner Punkt hinter dem dritten Teil ist das Satzende, nicht Teil des JWT. */
    @Test
    void ak21_einzelnerPunktAmSatzende_gehoertNichtZumJwt_derFundBleibt() {
        String vor = "Das Token war ";
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(vor + jwt() + "."));
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(vor + jwt() + ". Danach"));
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(vor + jwt() + ","));
        assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(vor + jwt() + ")"));
    }

    /** Entscheidung nach T-7, Durchlauf 1: Alle Punkte am Ende gehoeren nicht zum JWT (Auslassung, Satzende). */
    @Test
    void ak21_mehrerePunkteAmEnde_gehoerenNichtZumJwt_derFundBleibt() {
        String vor = "Das Token war ";
        for (String punkte : List.of("..", "...", "....", "... und weiter", ".. ", "...)", "..\n")) {
            assertEquals(List.of(form(vor, jwt(), TokenRecognizer.QUELLE_JWT)), tokens(vor + jwt() + punkte),
                    "Punkte " + punkte.length());
        }
        assertEquals(List.of(form("", jwt(), TokenRecognizer.QUELLE_JWT)), tokens(jwt() + "..."), "Textende");
        // Gegenprobe: Punkte vor dem dritten Teil trennen und bleiben im Fund
        List<String> teile = jwtTeile();
        String mitte = teile.get(0) + "." + teile.get(1) + "." + teile.get(2);
        assertEquals(List.of(form(vor, mitte, TokenRecognizer.QUELLE_JWT)), tokens(vor + mitte + "..."));
        // Gegenprobe: Der Lauf aus Punkten allein und ein Anfang mit Punkten sind kein JWT
        assertEquals(List.of(), geheimnisse(vor + "..." + jwt().substring(0, 12) + "..."), "gekuerzter Anfang");
    }

    @Test
    void ak21_jwtInEinerUrl_genauEinUrlFund_keinTokenFund() {
        String text = "https://example.invalid/x?t=" + jwt();
        List<Finding> funde = STANDARD.scan(text);
        assertEquals(1, funde.size(), "ein Fund");
        assertEquals(FindingType.URL, funde.get(0).type());
        assertEquals(List.of(), tokens(text));
        assertEquals(1, ERKENNER.detect(text).size(), "der Kandidat der Form liegt in der URL");
    }

    @Test
    void ak21_jwtInAnfuehrungszeichenUndKlammern_einFundUeberDasJwt() {
        for (String huelle : List.of("\"%s\"", "'%s'", "(%s)", "<%s>", "[%s]", "`%s`", "{%s}")) {
            String text = "x " + huelle.replace("%s", jwt()) + " y";
            List<Finding> funde = geheimnisse(text);
            assertEquals(1, funde.size(), "Huelle " + huelle);
            assertTrue(funde.get(0).originalValue().equals(jwt()), "Wert " + huelle);
        }
    }

    // ------------------------------------------------------------------ AK-22: PEM

    @Test
    void ak22_jedePrivateArt_lfUndCrlf_einFundTokenUeberDenKoerper() {
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of(LF, CRLF)) {
                String kopf = SynthetischeGeheimnisse.pemKopfzeile(art);
                String fuss = SynthetischeGeheimnisse.pemFusszeile(art);
                String text = kopf + ze + koerper(ze) + ze + fuss;
                List<Finding> erwartet = List.of(form(kopf + ze, koerper(ze), TokenRecognizer.QUELLE_PEM));
                assertEquals(erwartet, tokens(text), "Art " + art.length() + " Zeilenende " + ze.length());
                assertEquals(erwartet, ERKENNER.detect(text), "Kandidat " + art.length() + " " + ze.length());
                Finding f = erwartet.get(0);
                assertTrue(text.substring(0, f.start()).equals(kopf + ze), "BEGIN-Zeile bleibt");
                assertTrue(text.substring(f.end()).equals(ze + fuss), "END-Zeile bleibt");
            }
        }
    }

    @Test
    void ak22_ohneEndZeile_derFundReichtBisZurLetztenBase64Zeile() {
        for (String ze : List.of(LF, CRLF)) {
            String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
            String ende = kopf + ze + koerper(ze);
            assertEquals(List.of(form(kopf + ze, koerper(ze), TokenRecognizer.QUELLE_PEM)), tokens(ende),
                    "bis zum Textende");
            String mitText = ende + ze + "Danach neu starten.";
            assertEquals(List.of(form(kopf + ze, koerper(ze), TokenRecognizer.QUELLE_PEM)), tokens(mitText),
                    "bis zur letzten Base64-Zeile");
            String mitLeerzeile = ende + ze + ze + "Weiter im Text.";
            assertEquals(List.of(form(kopf + ze, koerper(ze), TokenRecognizer.QUELLE_PEM)), tokens(mitLeerzeile),
                    "Leerzeile beendet den Koerper");
        }
    }

    @Test
    void ak22_ohneEndZeile_naechsteBeginZeileBeendetDenKoerper() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("RSA PRIVATE KEY");
        String text = kopf + LF + koerper(LF) + LF + SynthetischeGeheimnisse.pemKopfzeile("PUBLIC KEY") + LF
                + SynthetischeGeheimnisse.base64Zeile(64);
        assertEquals(List.of(form(kopf + LF, koerper(LF), TokenRecognizer.QUELLE_PEM)), tokens(text));
    }

    @Test
    void ak22_kopfzeilenProcTypeUndDekInfo_gehoerenZumKoerper_mitUndOhneEnde() {
        String art = "RSA PRIVATE KEY";
        String kopf = SynthetischeGeheimnisse.pemKopfzeile(art);
        List<String> zeilen = new ArrayList<>(List.of("Proc-Type: 4,ENCRYPTED",
                "DEK-Info: AES-128-CBC," + SynthetischeGeheimnisse.hex32(), ""));
        zeilen.addAll(SynthetischeGeheimnisse.pemKoerperZeilen());
        String wert = String.join(LF, zeilen);
        String mitEnde = kopf + LF + wert + LF + SynthetischeGeheimnisse.pemFusszeile(art);
        assertEquals(List.of(form(kopf + LF, wert, TokenRecognizer.QUELLE_PEM)), tokens(mitEnde), "mit END");
        String ohneEnde = kopf + LF + wert;
        assertEquals(List.of(form(kopf + LF, wert, TokenRecognizer.QUELLE_PEM)), tokens(ohneEnde), "ohne END");
    }

    @Test
    void ak22_oeffentlicheArten_keinFund() {
        for (String art : SynthetischeGeheimnisse.pemOeffentlicheArten()) {
            for (String ze : List.of(LF, CRLF)) {
                String text = SynthetischeGeheimnisse.pemBlock(art, ze, true);
                assertEquals(List.of(), geheimnisse(text), "Art " + art.length() + " " + ze.length());
                assertEquals(List.of(), ERKENNER.detect(text), "Kandidat Art " + art.length());
                assertEquals(List.of(), geheimnisse(SynthetischeGeheimnisse.pemBlock(art, ze, false)),
                        "ohne END Art " + art.length());
            }
        }
        String pgpOeffentlich = SynthetischeGeheimnisse.pemBlock("PGP PUBLIC KEY BLOCK", LF, true);
        assertEquals(List.of(), geheimnisse(pgpOeffentlich), "oeffentlicher PGP-Block");
    }

    @Test
    void ak22_leererKoerper_keinFund() {
        String art = "PRIVATE KEY";
        String kopf = SynthetischeGeheimnisse.pemKopfzeile(art);
        String fuss = SynthetischeGeheimnisse.pemFusszeile(art);
        List<String> texte = List.of(kopf + LF + fuss, kopf + LF + LF + fuss, kopf + CRLF + CRLF + fuss,
                kopf + LF + "   " + LF + fuss, kopf, kopf + LF, kopf + LF + LF, kopf + LF + "Danach neu starten.",
                kopf + "  " + LF + fuss);
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), geheimnisse(texte.get(i)), "Text " + i);
        }
    }

    /** Praezisierung zu D-11: Zwischen BEGIN und END nur ein Platzhalter oder Satz ist kein Schluesselinhalt. */
    @Test
    void ak22_koerperOhneSchluesselinhalt_keinFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        for (String inhalt : List.of("...", "(Inhalt der Datei)", "<hier einfuegen>", "[gekuerzt]",
                "Schluessel aus dem Tresor", "MIIE" + (char) 0x2026, "Abcdefgh123Abcd")) {
            assertEquals(List.of(), geheimnisse(kopf + LF + inhalt + LF + fuss),
                    "Inhalt der Laenge " + inhalt.length());
        }
        // Gegenprobe: eine Folge von genau 16 Base64-Zeichen reicht
        String sechzehn = "Abcdefgh1234Abcd";
        assertEquals(1, geheimnisse(kopf + LF + sechzehn + LF + fuss).size(), "16 Zeichen");
    }

    @Test
    void ak22_eingerueckterKoerper_derFundBeginntAmErstenZeichenDerErstenZeile() {
        String kopf = "  " + SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = "  " + SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String wert = String.join(LF + "  ", SynthetischeGeheimnisse.pemKoerperZeilen());
        String vor = "key: |" + LF + kopf + LF + "  ";
        String mitEnde = vor + wert + LF + fuss;
        assertEquals(List.of(form(vor, wert, TokenRecognizer.QUELLE_PEM)), tokens(mitEnde), "mit END");
        assertEquals(List.of(form(vor, wert, TokenRecognizer.QUELLE_PEM)), tokens(vor + wert), "ohne END");
    }

    @Test
    void ak22_zeilenendeNurCr_einFund() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("EC PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("EC PRIVATE KEY");
        String text = kopf + "\r" + koerper("\r") + "\r" + fuss;
        assertEquals(List.of(form(kopf + "\r", koerper("\r"), TokenRecognizer.QUELLE_PEM)), tokens(text));
    }

    @Test
    void ak22_zweiBloeckImSelbenText_zweiFunde() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        String block = kopf + LF + koerper(LF) + LF + fuss;
        String text = block + LF + "Zwischentext" + LF + block;
        List<Finding> funde = tokens(text);
        assertEquals(2, funde.size(), "zwei Funde");
        assertTrue(funde.get(0).originalValue().equals(koerper(LF)), "erster Koerper");
        assertTrue(funde.get(1).originalValue().equals(koerper(LF)), "zweiter Koerper");
    }

    @Test
    void ak22_pseudonymisiert_beginZeileTokenPlatzhalterEndZeile_rueckuebersetzungZeichengleich() {
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of(LF, CRLF)) {
                String kopf = SynthetischeGeheimnisse.pemKopfzeile(art);
                String fuss = SynthetischeGeheimnisse.pemFusszeile(art);
                String text = "Vor dem Schluessel" + ze + kopf + ze + koerper(ze) + ze + fuss + ze + "Danach";
                List<Finding> funde = STANDARD.scan(text);
                PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, funde);
                String erwartet = "Vor dem Schluessel" + ze + kopf + ze + "[[TOKEN_1]]" + ze + fuss + ze + "Danach";
                String id = "Art " + art.length() + " Zeilenende " + ze.length();
                assertTrue(pseudo.text().equals(erwartet), "pseudonymisierter Text " + id);
                assertTrue(REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()).text().equals(text),
                        "Rueckuebersetzung " + id);
            }
        }
    }

    // ------------------------------------------------------------------ AK-22: PEM zwischen Markierungen

    private static final String ART = "PRIVATE KEY";
    private static final String MASK_LF = "\\n";
    private static final String MASK_CRLF = "\\r\\n";

    private static String kopfP() {
        return SynthetischeGeheimnisse.pemKopfzeile(ART);
    }

    private static String fussP() {
        return SynthetischeGeheimnisse.pemFusszeile(ART);
    }

    private static List<String> zeilen() {
        return SynthetischeGeheimnisse.pemKoerperZeilen();
    }

    /**
     * Prueft einen Text mit einem privaten PEM-Schluessel zwischen den Markierungen: genau ein TOKEN-Fund der Quelle
     * {@code token-pem}, der jede Koerperzeile ganz deckt, hinter der BEGIN- und vor der END-Markierung liegt; nach
     * der Pseudonymisierung sind BEGIN- und END-Markierung lesbar, vom Koerper keine zwoelf Zeichen einer Zeile, und
     * die Rueckuebersetzung ist zeichengleich.
     */
    private static Finding pruefeZwischen(String text, String id) {
        return pruefeZwischen(text, id, false);
    }

    /**
     * Wie oben; mit {@code nurGeheimnisse} werden nur die Geheimnis-Funde pseudonymisiert. Das braucht der Fall mit
     * maskierten Zeilenumbruechen: Der unveraenderte Pfad-Erkenner liest {@code \n-----END ...-----\n} als relativen
     * Pfad und deckt die END-Markierung mit ab (Ueberschutz; die Markierung ist dann nicht mehr lesbar).
     */
    private static Finding pruefeZwischen(String text, String id, boolean nurGeheimnisse) {
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "ein Fund " + id);
        Finding f = funde.get(0);
        assertEquals(TokenRecognizer.QUELLE_PEM, f.source(), "Quelle " + id);
        for (String zeile : zeilen()) {
            int p = text.indexOf(zeile);
            assertTrue(p >= 0 && f.start() <= p && p + zeile.length() <= f.end(), "Koerperzeile gedeckt " + id);
        }
        assertTrue(f.start() >= text.indexOf(kopfP()) + kopfP().length(), "BEGIN bleibt Klartext " + id);
        assertTrue(f.end() <= text.indexOf(fussP()), "END bleibt Klartext " + id);
        List<Finding> alle = nurGeheimnisse ? geheimnisse(text) : STANDARD.scan(text);
        PseudonymizationResult pseudo = PSEUDONYMIZER.pseudonymize(text, alle);
        String erwartet = text.substring(0, f.start()) + "[[TOKEN_1]]" + text.substring(f.end());
        assertTrue(pseudo.text().equals(erwartet), "pseudonymisierter Text " + id);
        assertTrue(pseudo.text().contains(kopfP()) && pseudo.text().contains(fussP()), "Markierungen lesbar " + id);
        for (String zeile : zeilen()) {
            assertTrue(!pseudo.text().contains(zeile.substring(0, 12)), "Koerper nicht lesbar " + id);
        }
        assertTrue(REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()).text().equals(text),
                "Rueckuebersetzung " + id);
        return f;
    }

    @Test
    void ak22z_mailZitatMitGroesserZeichen_einFundUeberDenKoerper() {
        List<String> z = zeilen();
        String text = "> " + kopfP() + "\n> " + String.join("\n> ", z) + "\n> " + fussP() + "\n> Danke";
        pruefeZwischen(text, "Zitat");
    }

    @Test
    void ak22z_kommentarMitRaute_einFund() {
        String text = "# " + kopfP() + "\n# " + String.join("\n# ", zeilen()) + "\n# " + fussP() + "\n";
        pruefeZwischen(text, "Kommentar");
    }

    @Test
    void ak22z_envDateiMitSchluesselUndKoerperInFolgezeilen_einFund() {
        String text = "TLS_PRIVAT=\"" + kopfP() + "\n" + String.join("\n", zeilen()) + "\n" + fussP()
                + "\"\nPORT=8443\n";
        pruefeZwischen(text, "env");
        String crlf = "TLS_PRIVAT=\"" + kopfP() + CRLF + String.join(CRLF, zeilen()) + CRLF + fussP() + "\"" + CRLF;
        pruefeZwischen(crlf, "env CR LF");
    }

    @Test
    void ak22z_tabellenzelle_einFund() {
        String text = "| Name | Wert |\n| tls_privat | " + kopfP() + "\n" + String.join("\n", zeilen()) + "\n" + fussP()
                + " |\n";
        pruefeZwischen(text, "Tabelle");
    }

    @Test
    void ak22z_javaStringVerkettung_einFund() {
        String text = "String s = \"" + kopfP() + "\\n\" +\n    \"" + String.join("\\n\" +\n    \"", zeilen())
                + "\\n\" +\n    \"" + fussP() + "\\n\";";
        pruefeZwischen(text, "Java");
    }

    @Test
    void ak22z_alleInEinerZeileMitLeerzeichen_einFundMitGenauemBereich() {
        String wert = String.join(" ", zeilen());
        String vor = "Schluessel: " + kopfP() + " ";
        String text = vor + wert + " " + fussP() + " Ende";
        Finding f = pruefeZwischen(text, "Leerzeichen");
        assertEquals(form(vor, wert, TokenRecognizer.QUELLE_PEM), f);
    }

    @Test
    void ak22z_serviceAccountJsonMitMaskiertemZeilenumbruch_einFundMitGenauemBereich() {
        for (String maske : List.of(MASK_LF, MASK_CRLF)) {
            String wert = String.join(maske, zeilen());
            String vor = "{\"type\": \"service_account\", \"pem_inhalt\": \"" + kopfP() + maske;
            String text = vor + wert + maske + fussP() + maske + "\", \"client_id\": \"4711\"}";
            Finding f = pruefeZwischen(text, "JSON " + maske.length(), true);
            assertEquals(form(vor, wert, TokenRecognizer.QUELLE_PEM), f, "Bereich " + maske.length());
            PseudonymizationResult voll = PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text));
            assertTrue(REIDENTIFIER.restore(voll.text(), voll.mapping()).text().equals(text),
                    "Rueckuebersetzung mit allen Funden " + maske.length());
        }
    }

    /**
     * Mit dem echten Schluesselnamen {@code private_key} greift zusaetzlich die Kontextregel und liefert einen
     * groesseren Fund (Wert bis zum Anfuehrungszeichen, samt Markierungen); kein Zeichen des Koerpers bleibt frei.
     */
    @Test
    void ak22z_serviceAccountJsonMitSchluesselnamenPrivateKey_keinZeichenDesKoerpersBleibtFrei() {
        for (String maske : List.of(MASK_LF, MASK_CRLF)) {
            String wert = String.join(maske, zeilen());
            String text = "{\"private_key\": \"" + kopfP() + maske + wert + maske + fussP() + maske + "\"}";
            boolean[] geschuetzt = new boolean[text.length()];
            for (Finding f : geheimnisse(text)) {
                for (int i = f.start(); i < f.end(); i++) {
                    geschuetzt[i] = true;
                }
            }
            int von = text.indexOf(wert);
            for (int i = von; i < von + wert.length(); i++) {
                assertTrue(geschuetzt[i], "Position geschuetzt " + maske.length());
            }
        }
    }

    @Test
    void ak22z_ersteKoerperzeileAnDieBeginMarkierungGeklebt_einFund() {
        String wert = String.join("\n", zeilen());
        String text = kopfP() + wert + "\n" + fussP();
        Finding f = pruefeZwischen(text, "geklebt");
        assertEquals(form(kopfP(), wert, TokenRecognizer.QUELLE_PEM), f);
        String mitText = "Schluessel: " + text;
        assertEquals(form("Schluessel: " + kopfP(), wert, TokenRecognizer.QUELLE_PEM), pruefeZwischen(mitText, "Text"));
    }

    @Test
    void ak22z_jedePrivateArtMitten_imSatz_einFund() {
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            String wert = String.join(" ", zeilen());
            String vor = "Der Schluessel lautet " + SynthetischeGeheimnisse.pemKopfzeile(art) + " ";
            String text = vor + wert + " " + SynthetischeGeheimnisse.pemFusszeile(art) + " und ist geheim.";
            assertEquals(List.of(form(vor, wert, TokenRecognizer.QUELLE_PEM)), tokens(text), "Art " + art.length());
        }
    }

    @Test
    void ak22z_zeilenformUndZwischenformZaehlenNichtDoppelt_einFundUeberDenKoerper() {
        String text = SynthetischeGeheimnisse.pemBlock(ART, LF, true);
        assertEquals(List.of(form(kopfP() + LF, koerper(LF), TokenRecognizer.QUELLE_PEM)), tokens(text));
        // Zeilenform ohne END und eine zweite Form mit END in der naechsten Zeile: ein Fund je Schluessel
        String zwei = text + LF + "x " + kopfP() + " " + koerper(" ") + " " + fussP();
        List<Finding> funde = tokens(zwei);
        assertEquals(2, funde.size(), "zwei Schluessel");
        assertTrue(funde.get(0).end() <= funde.get(1).start(), "ueberlappungsfrei");
    }

    @Test
    void ak22z_zweiSchluesselHintereinanderUndEinMarkierungsPaarOhneInhalt() {
        String text = "x " + kopfP() + " " + koerper(" ") + " " + fussP() + " y " + kopfP() + " ... " + fussP() + " z "
                + kopfP() + " " + koerper(" ") + " " + fussP();
        assertEquals(2, tokens(text).size(), "zwei Funde, das Paar mit Platzhalter keiner");
    }

    // Negativ

    @Test
    void ak22z_dokuMitPlatzhalterOderGekuerztemInhalt_keinFund() {
        String k = kopfP();
        String f = fussP();
        List<String> texte = List.of("Beginn mit " + k + " ... " + f + " Ende", "x " + k + " (gekuerzt) " + f,
                "x " + k + "Abcdefgh123Abcd" + f, "x " + k + MASK_LF + "..." + MASK_LF + f,
                "# " + k + "\n# <Schluessel hier einfuegen>\n# " + f, "KEY=\"" + k + " REDACTED " + f + "\"",
                "x " + k + " " + "Abcdefgh" + " " + "Ijklmnop" + " " + f);
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), geheimnisse(texte.get(i)), "Text " + i);
            assertEquals(List.of(), ERKENNER.detect(texte.get(i)), "Kandidat " + i);
        }
    }

    @Test
    void ak22z_oeffentlicheArtenZwischenMarkierungen_keinFund() {
        for (String art : SynthetischeGeheimnisse.pemOeffentlicheArten()) {
            String k = SynthetischeGeheimnisse.pemKopfzeile(art);
            String f = SynthetischeGeheimnisse.pemFusszeile(art);
            assertEquals(List.of(), geheimnisse("x " + k + " " + koerper(" ") + " " + f),
                    "Leerzeichen " + art.length());
            assertEquals(List.of(), geheimnisse("x " + k + MASK_LF + koerper(MASK_LF) + MASK_LF + f),
                    "maskiert " + art.length());
        }
    }

    @Test
    void ak22z_endMarkierungAndererArt_keinFund() {
        String text = "x " + kopfP() + " " + koerper(" ") + " " + SynthetischeGeheimnisse.pemFusszeile("PUBLIC KEY");
        assertEquals(List.of(), tokens(text));
        String rsa = "x " + kopfP() + " " + koerper(" ") + " "
                + SynthetischeGeheimnisse.pemFusszeile("RSA PRIVATE KEY");
        assertEquals(List.of(), tokens(rsa), "andere private Art");
    }

    @Test
    void ak22z_beginOhneEndImSatz_keinFund() {
        List<String> texte = List.of("Der Schluessel beginnt mit " + kopfP() + " und ist abgelegt.",
                "x " + kopfP() + " " + koerper(" "), "x " + kopfP() + MASK_LF + koerper(MASK_LF) + MASK_LF,
                "Die Datei endet mit " + fussP() + " und beginnt mit " + kopfP() + ".");
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), tokens(texte.get(i)), "Text " + i);
        }
    }

    @Test
    void ak22z_weitereBeginMarkierungDazwischen_nurDasInnerePaarIstFund() {
        String text = "x " + kopfP() + " " + koerper(" ") + " " + kopfP() + " " + koerper(" ") + " " + fussP();
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "ein Fund");
        assertTrue(funde.get(0).start() > text.indexOf(kopfP(), text.indexOf(kopfP()) + 1), "hinter der zweiten BEGIN");
    }

    @Test
    void ak22z_abstandSechzehnKibIstDieGrenze() {
        int fenster = 16 * 1024;
        String vor = "x " + kopfP();
        String genau = SynthetischeGeheimnisse.base64Zeile(fenster);
        assertEquals(List.of(form(vor, genau, TokenRecognizer.QUELLE_PEM)), tokens(vor + genau + fussP()),
                "genau 16 KiB");
        String zuviel = SynthetischeGeheimnisse.base64Zeile(fenster + 1);
        assertEquals(List.of(), tokens(vor + zuviel + fussP()), "ein Zeichen mehr");
    }

    @Test
    void ak22z_beginMarkierungMitText_zwischenformBraucht16Base64ZeichenInFolge() {
        String f = fussP();
        String k = kopfP();
        assertEquals(1, tokens("x " + k + " " + "Abcdefgh1234Abcd" + " " + f).size(), "16 Zeichen");
        assertEquals(List.of(), tokens("x " + k + " " + "Abcdefgh123Abcd" + " " + f), "15 Zeichen");
        assertEquals(List.of(), tokens("x " + k + " Abcdefgh 123Abcd " + f), "durch Leerzeichen getrennt");
    }

    /**
     * T-7, Durchlauf 3: Ein Erklaertext, der die BEGIN-Markierung in einem Absatz und die END-Markierung einen, drei
     * oder zehn Absaetze spaeter nennt, ist kein Fund, auch mit einem langen Wort, Pfad oder einer URL dazwischen
     * (hoechstens etwa 21 Prozent Schluesselinhalt, die Schwelle liegt bei 70).
     */
    @Test
    void ak22z_erklaertextMitBeginUndEndUeberEinenDreiUndZehnAbsaetze_keinFund() {
        int geprueft = 0;
        for (int absaetze : List.of(1, 3, 10)) {
            for (int variante = 0; variante < 8; variante++) {
                String text = TokenFormenFehlalarmTest.erklaertext(absaetze, variante);
                String id = "Absaetze " + absaetze + " Variante " + variante;
                assertEquals(List.of(), tokens(text), id);
                assertEquals(List.of(), ERKENNER.detect(text), "Kandidat " + id);
                geprueft++;
            }
        }
        assertEquals(24, geprueft, "Zahl der Texte");
    }

    /** Erklaertext ausserhalb der Markierungen hat keinen Einfluss: der Schluessel dazwischen bleibt ein Fund. */
    @Test
    void ak22z_schluesselMitErklaertextDavorUndDahinter_einFundMitGenauemBereich() {
        String wert = String.join("\n", zeilen());
        String vor = TokenFormenFehlalarmTest.erklaertext(3, 2) + "\n\nSchluessel: " + kopfP() + "\n";
        String text = vor + wert + "\n" + fussP() + "\n\n" + TokenFormenFehlalarmTest.erklaertext(1, 5);
        List<Finding> funde = tokens(text);
        assertEquals(1, funde.size(), "ein Fund");
        assertEquals(form(vor, wert, TokenRecognizer.QUELLE_PEM), funde.get(0));
    }

    /**
     * Die Schwelle: Ein Lauf von 70 Zeichen plus 30 einzelne Zeichen (je ein Wort) liegt bei genau 70 Prozent, mit
     * einem Wort mehr bei knapp darunter.
     */
    @Test
    void ak22z_anteilSchluesselinhalt_siebzigProzentIstDieGrenze() {
        String lauf = SynthetischeGeheimnisse.base64Zeile(70);
        String genau = "x " + kopfP() + " " + lauf + " " + "a ".repeat(29) + "a " + fussP();
        String wert = lauf + " " + "a ".repeat(29) + "a";
        assertEquals(List.of(form("x " + kopfP() + " ", wert, TokenRecognizer.QUELLE_PEM)), tokens(genau),
                "70 Prozent");
        String knapp = "x " + kopfP() + " " + lauf + " " + "a ".repeat(30) + "a " + fussP();
        assertEquals(List.of(), tokens(knapp), "knapp unter 70 Prozent");
    }

    /** Maskierte Schraegstriche (JSON) zaehlen als Base64-Zeichen: ein Schluessel mit vielen bleibt ein Fund. */
    @Test
    void ak22z_jsonMitMaskiertenSchraegstrichen_einFundUeberDenKoerper() {
        String maske = "\\n";
        List<String> zeilen = new ArrayList<>();
        for (String zeile : zeilen()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < zeile.length(); i++) {
                sb.append(i % 8 == 7 ? "\\/" : String.valueOf(zeile.charAt(i)));
            }
            zeilen.add(sb.toString());
        }
        String wert = String.join(maske, zeilen);
        String vor = "{\"k\": \"" + kopfP() + maske;
        String text = vor + wert + maske + fussP() + maske + "\"}";
        assertEquals(List.of(form(vor, wert, TokenRecognizer.QUELLE_PEM)), tokens(text));
    }

    // ------------------------------------------------------------------ AK-23: Praefix-Token

    @Test
    void ak23_jedesPraefixDerListe_einFundTokenUeberDasGanzeToken() {
        List<String> praefixe = SynthetischeGeheimnisse.praefixe();
        assertEquals(9, praefixe.size(), "Zahl der Praefixe");
        for (String praefix : praefixe) {
            String token = SynthetischeGeheimnisse.praefixToken(praefix);
            assertEquals(List.of(form("", token, TokenRecognizer.QUELLE_PRAEFIX)), tokens(token),
                    "Praefix der Laenge " + praefix.length());
            String vor = "Der Wert ";
            assertEquals(List.of(form(vor, token, TokenRecognizer.QUELLE_PRAEFIX)),
                    tokens(vor + token + " wurde widerrufen."), "im Satz " + praefix.length());
        }
    }

    private static String tokenMitLaenge(String praefix, int laenge) {
        String rest = ("7" + MARKE + "xxxxxxxxxxxxxxxxxxxxxxxx").substring(0, laenge - praefix.length());
        return praefix + rest;
    }

    @Test
    void ak23_laengeAb20Zeichen_dieGrenze() {
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            String zu = tokenMitLaenge(praefix, 19);
            String genau = tokenMitLaenge(praefix, 20);
            assertEquals(19, zu.length(), "Laenge zu kurz");
            assertEquals(List.of(), geheimnisse("Wert " + zu), "19 Zeichen, Praefix " + praefix.length());
            assertEquals(List.of(form("Wert ", genau, TokenRecognizer.QUELLE_PRAEFIX)), tokens("Wert " + genau),
                    "20 Zeichen, Praefix " + praefix.length());
        }
    }

    @Test
    void ak23_keinFund_ohneZiffer_praefixNichtInDerListe_andereSchreibweise() {
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            String ohneZiffer = praefix + "AbcdTestwertEfghIjklMnopQrst";
            assertEquals(List.of(), geheimnisse("Wert " + ohneZiffer), "ohne Ziffer " + praefix.length());
        }
        String rest = SynthetischeGeheimnisse.PRAEFIX_REST;
        for (String fremd : List.of("gh" + "x_", "gh" + "p-", "gh" + "p", "xo" + "xa-", "gl" + "pat_",
                "git" + "hub_pat-", "GH" + "P_", "Gh" + "p_")) {
            assertEquals(List.of(), geheimnisse("Wert " + fremd + rest),
                    "Praefix nicht in der Liste " + fremd.length());
        }
    }

    @Test
    void ak23_keinFund_wennDavorEinBuchstabeEineZifferUnterstrichOderBindestrichSteht() {
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        for (String davor : List.of("x", "7", "_", "-", "A")) {
            assertEquals(List.of(), geheimnisse("Wert " + davor + token), "davor " + (int) davor.charAt(0));
        }
        assertEquals(List.of(), geheimnisse("Wert " + (char) 0xE4 + token), "Umlaut davor");
        assertEquals(List.of(), geheimnisse("Wert " + token + (char) 0xE4), "Umlaut dahinter");
        // Gegenprobe: Zeichen, die keinen Wortkoerper bilden
        for (String davor : List.of("(", "\"", "=", ":", ".", ">", "'")) {
            assertEquals(1, geheimnisse("Wert " + davor + token).size(), "davor " + (int) davor.charAt(0));
        }
    }

    @Test
    void ak23_praefixInNachbarschaftVonSatzzeichen_derFundEndetAmLaufende() {
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(1));
        String vor = "Token war ";
        for (String dahinter : List.of(".", ",", ";", ")", "!", "\"", ":")) {
            assertEquals(List.of(form(vor, token, TokenRecognizer.QUELLE_PRAEFIX)), tokens(vor + token + dahinter),
                    "dahinter " + dahinter);
        }
    }

    // ------------------------------------------------------------------ Zusammenspiel mit der Kontextregel

    @Test
    void vereinigung_praefixTokenHinterKontextwort_dieFormErsetztDenKontextKandidaten() {
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(2));
        String vor = "Token: ";
        assertEquals(List.of(form(vor, token, TokenRecognizer.QUELLE_PRAEFIX)), ERKENNER.detect(vor + token));
        assertEquals(List.of(new Finding(GeheimnisTypen.TOKEN, token, vor.length(), vor.length() + token.length(),
                0.95, TokenRecognizer.QUELLE_KONTEXT)), new TokenRecognizerStandT6().detect(vor + token));
    }

    @Test
    void vereinigung_formInEinemGroesserenKontextKandidaten_entfaellt_derKontextKandidatBleibtUnveraendert() {
        String vor = "Token: ";
        String text = vor + jwt() + ",Rest" + "7";
        List<Finding> t6 = new TokenRecognizerStandT6().detect(text);
        assertEquals(1, t6.size(), "T-6");
        assertEquals(t6, ERKENNER.detect(text), "unveraendert");
        assertEquals(TokenRecognizer.QUELLE_KONTEXT, ERKENNER.detect(text).get(0).source());
    }

    @Test
    void vereinigung_praefixTokenInEinemJwtTeil_entfaelltDerInnereKandidat() {
        List<String> teile = jwtTeile();
        String innen = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        String jwt = teile.get(0) + "." + teile.get(1) + "." + innen;
        assertEquals(List.of(form("", jwt, TokenRecognizer.QUELLE_JWT)), ERKENNER.detect(jwt));
    }

    @Test
    void vereinigung_jwtUndPraefixTokenInEinemPemKoerper_nurDerKoerperIstKandidat() {
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        List<String> zeilen = List.of(jwt(), SynthetischeGeheimnisse.base64Zeile(64), token,
                SynthetischeGeheimnisse.base64Zeile(40));
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String text = SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", zeilen, LF, true);
        assertEquals(List.of(form(kopf + LF, String.join(LF, zeilen), TokenRecognizer.QUELLE_PEM)),
                ERKENNER.detect(text));
    }

    /** Ein Kandidat der Kontextregel im PEM-Koerper bleibt unveraendert; der Koerper wird um ihn zugeschnitten. */
    @Test
    void vereinigung_kontextKandidatImPemKoerper_derKoerperWirdZugeschnitten_keinePositionFrei() {
        List<String> zeilen = List.of(SynthetischeGeheimnisse.base64Zeile(64), "Token: " + T,
                SynthetischeGeheimnisse.base64Zeile(40));
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String text = SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", zeilen, LF, true);
        int koerperStart = kopf.length() + 1;
        int koerperEnde = koerperStart + String.join(LF, zeilen).length();
        List<Finding> kandidaten = ERKENNER.detect(text);
        assertEquals(3, kandidaten.size(), "Stueck, Kontext, Stueck");
        assertEquals(TokenRecognizer.QUELLE_PEM, kandidaten.get(0).source());
        assertEquals(TokenRecognizer.QUELLE_KONTEXT, kandidaten.get(1).source());
        assertEquals(TokenRecognizer.QUELLE_PEM, kandidaten.get(2).source());
        assertEquals(koerperStart, kandidaten.get(0).start());
        assertEquals(koerperEnde, kandidaten.get(2).end());
        assertEquals(kandidaten.get(0).end(), kandidaten.get(1).start(), "lueckenlos");
        assertEquals(kandidaten.get(1).end(), kandidaten.get(2).start(), "lueckenlos");
        assertTrue(T.equals(kandidaten.get(1).originalValue()), "Wert des Kontext-Kandidaten");
    }

    @Test
    void erkenner_nullText_nullPointerExceptionMitMeldungText() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> ERKENNER.detect(null));
        assertEquals("text", e.getMessage());
    }

    @Test
    void erkenner_leererTextUndLeerraum_keineKandidaten() {
        assertEquals(List.of(), ERKENNER.detect(""));
        assertEquals(List.of(), ERKENNER.detect("   \n\r\n\t "));
    }
}
