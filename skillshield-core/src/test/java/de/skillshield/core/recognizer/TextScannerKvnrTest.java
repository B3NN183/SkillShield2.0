package de.skillshield.core.recognizer;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_MARKER;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Einbindung des KVNR-Erkenners in {@code TextScanner.standard()} (Feature kvnr-bsnr-erkennung, T-6; AK-4 auf
 * Scannerebene, AK-18, AK-20, AK-21, AK-35). Alle Nummern stammen aus {@code SynthetischeKvNummern}; jeder Text
 * laeuft vor dem Scan durch den Laufzeit-Waechter fuer synthetische KVNR. Wertvergleiche mit Text oder Nummern
 * laufen ueber {@code assertTrue(equals)} mit fester Meldung. Negativbeispiele verwenden die ungueltige KVNR und
 * haben eine Positiv-Gegenprobe mit derselben Nummer (Regel 12 des Plans).
 *
 * <p>Die im Plan zu T-6 genannte Ergaenzung der Klartextfreiheit (AK-35) steht hier ({@code ak35_...}) und nicht in
 * den seit T-1 eingefrorenen Dateien. Der Test {@code ipFundHinterKvnrKontextwort...} belegt die IPv4-Regel des
 * Nachtrags zu T-6 auf Scannerebene; die Tests {@code zuschneiden_...}, {@code angrenzend_...} und
 * {@code nachrangig_...} belegen Nachtrag 3 zu T-6 (Zuschneiden statt Verwerfen).
 */
class TextScannerKvnrTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();
    private static final String BS = String.valueOf((char) 0x5C);
    private static final String KVNR_UNGUELTIG = KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
    private static final String QUELLE_PZ = "kvnr-pruefziffer-ungueltig";
    private static final String QUELLE_FORM = "kvnr-form-abweichend";
    private static final String IP_ZWEI = "192.0.2.1";

    private static List<Finding> scan(String text) {
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        List<Finding> funde = TextScanner.standard().scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        assertTrue(funde.equals(TextScanner.standard().scan(text)), "Fundliste bei Wiederholung nicht gleich");
        return funde;
    }

    private static void assertRoundtrip(String text, PseudonymizationResult ergebnis) {
        ReidentificationResult zurueck = REIDENTIFIER.restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    private static void assertEinFund(List<Finding> funde, FindingType typ, int start, String wert, double konfidenz,
            String quelle) {
        assertEquals(1, funde.size(), "Zahl der Funde");
        Finding f = funde.get(0);
        assertEquals(typ, f.type(), "Typ");
        assertEquals(start, f.start(), "Start");
        assertEquals(start + wert.length(), f.end(), "Ende");
        assertTrue(wert.equals(f.originalValue()), "Wert");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        assertTrue(quelle.equals(f.source()), "Quelle");
    }

    // ------------------------------------------------------------------ AK-21: Einbindung

    /**
     * Der KVNR-Erkenner steht an Index 6. Seit T-14 folgt als achter Erkenner der BSNR-Erkenner an Index 7; der Name
     * des Tests bleibt unveraendert.
     */
    @Test
    void ak21_standardEnthaeltDenKvnrErkennerAnLetzterStelleIndexSechs() {
        List<Class<?>> klassen = TextScanner.standard().recognizers().stream()
                .<Class<?>>map(Object::getClass).toList();
        // Nachtrag zu T-14: Zahl der Standard-Erkenner 8 (alter Wert: 7)
        // Umgestellt durch geheimnisse-erkennung T-4: Zahl der Standard-Erkenner 9 (alter Wert: 8)
        assertEquals(9, klassen.size(), "Zahl der Standard-Erkenner");
        // Nachtrag zu T-14: Liste um BsnrRecognizer ergaenzt (alt: endete mit KvnrRecognizer.class)
        // Umgestellt durch geheimnisse-erkennung T-4: Liste um PasswortRecognizer ergaenzt (alt: endete mit
        // BsnrRecognizer.class); die ersten acht bleiben in unveraenderter Reihenfolge
        assertTrue(List.of(EmailRecognizer.class, Ipv4Recognizer.class, UrlRecognizer.class, PathRecognizer.class,
                LanrRecognizer.class, IkRecognizer.class, KvnrRecognizer.class, BsnrRecognizer.class,
                PasswortRecognizer.class).equals(klassen), "Reihenfolge der Standard-Erkenner");
        assertEquals(KvnrRecognizer.class, klassen.get(6), "Index 6");
        // Nachtrag zu T-14: neuer Eintrag, BsnrRecognizer an Index 7
        assertEquals(BsnrRecognizer.class, klassen.get(7), "Index 7");
    }

    @Test
    void ak21_textMitJeEinerEmailIpUrlPfadIkLanrKvnrLiefertAlleSiebenFunde() {
        String text = "Mail erika.beispiel@example.invalid\n"
                + "Server " + IK_FORM_DOKU_IP + "\n"
                + "Seite https://example.invalid/x\n"
                + "Datei C:" + BS + "Tools" + BS + "a.txt\n"
                + "IK " + IK_GUELTIG + "\n"
                + "LANR " + LANR_GUELTIG + "\n"
                + "KVNR " + KVNR_GUELTIG + "\n";
        List<Finding> funde = scan(text);
        assertTrue(List.of(FindingType.EMAIL, FindingType.IP, FindingType.URL, FindingType.PFAD, FindingType.IK,
                FindingType.LANR, FindingType.KVNR).equals(funde.stream().map(Finding::type).toList()),
                "Typen und Reihenfolge der sieben Funde");
        Finding kvnr = funde.get(6);
        assertTrue(KVNR_GUELTIG.equals(kvnr.originalValue()), "KVNR-Wert");
        assertEquals(0.95, kvnr.confidence(), "Konfidenz der KVNR");
        assertEquals(ConfidenceLevel.SICHER, kvnr.level(), "Stufe der KVNR");

        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue(ergebnis.text().contains("KVNR [[KVNR_7]]"), "Platzhalter der KVNR");
        assertFalse(ergebnis.text().contains(KVNR_GUELTIG), "KVNR im Klartext");
        assertEquals(7, ergebnis.mapping().size(), "Zahl der Platzhalter");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak21_mehrereTypenImSelbenText_gleicheKvnrErhaeltGleichenPlatzhalterUndRoundtripIstZeichengleich() {
        String text = "IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG + ", KVNR " + KVNR_GUELTIG
                + ", nochmals KVNR " + KVNR_GUELTIG + ".";
        List<Finding> funde = scan(text);
        assertTrue(List.of(FindingType.IK, FindingType.LANR, FindingType.KVNR, FindingType.KVNR)
                .equals(funde.stream().map(Finding::type).toList()), "Typen der vier Funde");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue(ergebnis.text().equals("IK [[IK_1]], LANR [[LANR_2]], KVNR [[KVNR_3]], nochmals KVNR [[KVNR_3]]."),
                "pseudonymisierter Text");
        assertEquals(3, ergebnis.mapping().size(), "gleiche Nummern teilen einen Platzhalter");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak21_kvnrErsetztNurDenUebergebenenFundUndEntfernenDenKlartextDerNummer() {
        String text = "KVNR " + KVNR_GUELTIG + ", IK " + IK_GUELTIG;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        PseudonymizationResult nurKvnr = PSEUDONYMIZER.pseudonymize(text, List.of(funde.get(0)));
        assertTrue(nurKvnr.text().equals("KVNR [[KVNR_1]], IK " + IK_GUELTIG), "nur die KVNR ersetzt");
        assertRoundtrip(text, nurKvnr);
    }

    // ------------------------------------------------------------------ AK-18: Konflikt zwischen Arten

    @Test
    void ak18_ikKommaKvnr_genauEinIkFundUeberDieZahlMitKonfidenz092() {
        // IK-Kandidat direkt hinter "IK" 0,92; der KVNR-Erkenner sieht "KVNR" nachgestellt hinter dem Komma und
        // liefert fuer dieselbe Ziffernfolge (ohne Buchstaben) hoechstens 0,40 (Form abweichend). Gleicher Bereich:
        // die hoehere Konfidenz entscheidet, Typ IK.
        String text = "IK " + IK_GUELTIG + ", KVNR";
        assertEinFund(scan(text), FindingType.IK, 3, IK_GUELTIG, 0.92, "ik");
        assertTrue(new KvnrRecognizer().detect(text).size() == 1, "KVNR-Kandidat auf Erkennerebene");
        assertEquals(0.40, new KvnrRecognizer().detect(text).get(0).confidence(), "Kandidat der KVNR");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, scan(text));
        assertTrue(ergebnis.text().equals("IK [[IK_1]], KVNR"), "pseudonymisierter Text");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak18_versichertennummerIkKommaIk_genauEinIkFundUeberDieZahlMitKonfidenz060() {
        // KVNR-Kandidat hinter "Versichertennummer" 0,40 (Form abweichend, reine Ziffern); IK-Kandidat nachgestellt
        // hinter dem Komma 0,60. Gleicher Bereich: Typ IK, Konfidenz 0,60.
        String vor = "Versichertennummer ";
        String text = vor + IK_GUELTIG + ", IK";
        assertEinFund(scan(text), FindingType.IK, vor.length(), IK_GUELTIG, 0.60, "ik");
        assertEquals(0.40, new KvnrRecognizer().detect(text).get(0).confidence(), "Kandidat der KVNR");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, scan(text));
        assertTrue(ergebnis.text().equals("Versichertennummer [[IK_1]], IK"), "pseudonymisierter Text");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak18_kvnrOhneIkKontext_dieselbeZahlMitKvnrKontextwortIstEinKvnrFundMitNiedrigerStufe() {
        // Gegenprobe: ohne IK-Kontextwort bleibt der KVNR-Kandidat uebrig (reine Ziffernfolge, 0,40).
        String text = "Versichertennummer " + IK_GUELTIG;
        assertEinFund(scan(text), FindingType.KVNR, "Versichertennummer ".length(), IK_GUELTIG, 0.40, QUELLE_FORM);
    }

    // ------------------------------------------------------------------ AK-4 auf Scannerebene, K-1

    @Test
    void ak4_scanner_kvNrVorKvnrFormIstEinKvnrFundAberVorLanrIkOderZiffernKeiner() {
        assertEinFund(scan("KV-Nr. " + KVNR_GUELTIG), FindingType.KVNR, 7, KVNR_GUELTIG, 0.95, "kvnr");
        assertTrue(scan("KV-Nr. " + LANR_GUELTIG).isEmpty(), "KV-Nr. vor gueltiger LANR");
        assertTrue(scan("KV-Nr. " + IK_GUELTIG).isEmpty(), "KV-Nr. vor gueltiger IK");
        // Gegenprobe: dieselbe LANR hinter "Versichertennummer" ist ein KVNR-Fund 0,40 (Form abweichend)
        assertEinFund(scan("Versichertennummer " + LANR_GUELTIG), FindingType.KVNR, 19, LANR_GUELTIG, 0.40,
                QUELLE_FORM);
    }

    @Test
    void ak4_scanner_lanrOderIkVorUngueltigerKvnrBleibenAltFunde_mitKvnrKontextwortIstEsEinKvnrFund() {
        for (String wort : List.of("LANR ", "IK ")) {
            String ohne = wort + KVNR_UNGUELTIG;
            List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(ohne);
            assertTrue(alt.equals(scan(ohne)), "Fundliste ohne KVNR-Kontextwort weicht vom Altstand ab");
            assertEquals(1, alt.size(), "Zahl der alten Funde");
            assertFalse(alt.get(0).type().equals(FindingType.KVNR), "kein KVNR-Fund ohne Kontextwort");
        }
        // Positiv-Gegenprobe mit derselben Nummer: mit KVNR-Kontextwort ist sie ein KVNR-Fund
        assertEinFund(scan("KVNR " + KVNR_UNGUELTIG), FindingType.KVNR, 5, KVNR_UNGUELTIG, 0.50, QUELLE_PZ);
    }

    @Test
    void k1_kvnrKontextwortUndLanrWort_dieKvnrFormErsetztDenLanrFundUeberDenZiffern() {
        String text = "KVNR LANR " + KVNR_GUELTIG;
        List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
        assertEquals(1, alt.size(), "alter Fund");
        assertEquals(FindingType.LANR, alt.get(0).type(), "alter Typ");
        assertEquals(11, alt.get(0).start(), "alter Start (nur die Ziffern)");
        // Der KVNR-Fund umschliesst die Ziffern mit dem Buchstaben; der umschliessende Fund gewinnt (Kap. 2.1).
        List<Finding> neu = scan(text);
        assertEinFund(neu, FindingType.KVNR, 10, KVNR_GUELTIG, 0.80, "kvnr");
        Abweichungen a = KvnrBsnrHilfen.bestandsabweichungen(text, alt, neu);
        assertEquals(1, a.k1(), "K-1");
        assertEquals(0, a.k2() + a.k3() + a.k4() + a.verstoesse(), "andere Faelle und Verstoesse");
    }

    // ------------------------------------------------------------------ Nachtrag zu T-6: IPv4-Gestalt

    @Test
    void ipFundHinterKvnrKontextwort_buchstabeDavorAendertNichts_typKonfidenzUndPlatzhalterBleiben() {
        String ip = IK_FORM_DOKU_IP;
        Finding ohneBuchstabe = scan("Server " + ip).get(0);
        assertEquals(FindingType.IP, ohneBuchstabe.type(), "Typ ohne Kontextwort");
        for (String vor : List.of("KVNR: Z", "KVNR: Z ", "KVNR Z-", "Versichertennummer z", "KV-Nr. Z", "KV-Nr. Z ")) {
            String text = vor + ip;
            List<Finding> funde = scan(text);
            assertEinFund(funde, FindingType.IP, vor.length(), ip, ohneBuchstabe.confidence(), ohneBuchstabe.source());
            PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
            assertTrue(ergebnis.text().equals(vor + "[[IP_1]]"), "pseudonymisierter Text");
            assertFalse(ergebnis.text().contains(ip), "IP im Klartext");
            assertRoundtrip(text, ergebnis);
        }
        // Gegenprobe: dieselbe Stellung mit einer normalen KVNR bleibt ein KVNR-Fund ueber alle Zeichen
        assertEinFund(scan("KVNR: " + KVNR_GUELTIG), FindingType.KVNR, 6, KVNR_GUELTIG, 0.95, "kvnr");
    }

    // ------------------------------------------------------------------ Nachtrag 2 zu T-6: Vorrang des Bestands

    private static List<Finding> nurBestand(List<Finding> funde) {
        return funde.stream().filter(f -> !f.type().equals(FindingType.KVNR) && !f.type().equals(FindingType.LANR)
                && !f.type().equals(FindingType.IK)).toList();
    }

    /** Alter Fund unveraendert, Platzhaltertyp wie ohne KVNR-Kontextwort, Roundtrip zeichengleich. */
    private static void assertBestandUnveraendert(String text, FindingType erwartet) {
        List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
        List<Finding> neu = scan(text);
        assertTrue(alt.stream().anyMatch(f -> f.type().equals(erwartet)), "kein alter Fund des erwarteten Typs");
        for (Finding f : alt) {
            assertTrue(neu.contains(f), "alter Fund fehlt oder ist veraendert");
        }
        assertTrue(nurBestand(neu).equals(nurBestand(alt)), "Teilliste der Bestandsfunde ungleich");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, neu);
        assertTrue(ergebnis.text().contains("[[" + erwartet.name() + "_"), "Platzhaltertyp");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void bestandVorrang_ipMitPraefixlaenge_hinterKontextwortBleibtEinIpFund() {
        String ip = IK_FORM_DOKU_IP;
        for (String text : List.of("KVNR " + ip + "/24", "KVNR Z" + ip + "/24", "KVNR Z " + ip + "/24",
                "KV-Nr. Z" + ip + "/24", "K V N R Z" + ip + "/8", ip + "/24 (Patienten-KVNR)",
                "Versichertennummer: Z" + ip + "/24Praxis")) {
            assertBestandUnveraendert(text, FindingType.IP);
        }
        // Gegenprobe: ohne Kontextwort aendert sich nichts, mit der KVNR allein bleibt sie ein KVNR-Fund
        assertEinFund(scan("Server " + ip + "/24"), FindingType.IP, 7, ip, 0.85, "ipv4");
        assertEinFund(scan("KVNR " + KVNR_GUELTIG), FindingType.KVNR, 5, KVNR_GUELTIG, 0.95, "kvnr");
    }

    @Test
    void bestandVorrang_ipLeerzeichenZiffern_hinterKontextwortBleibtEinIpFund() {
        String ip = IK_FORM_DOKU_IP;
        for (String text : List.of("KVNR " + ip + " " + LAENGE_6, "KVNR Z" + ip + " " + LAENGE_6,
                "KVNR " + ip + " 12", "KVNR " + ip + " " + KVNR_GUELTIG.substring(1),
                "KV-Nr. Z " + ip + " " + LAENGE_6, "K V N R Z" + ip + " 12",
                ip + " " + LAENGE_6 + " (Patienten-KVNR)")) {
            assertBestandUnveraendert(text, FindingType.IP);
        }
    }

    @Test
    void bestandVorrang_ipKetteMitLangemErstemBlock_hinterKontextwortBleibtEinIpFund() {
        String ip = IK_FORM_DOKU_IP;
        for (String text : List.of("KVNR 1234." + ip, "KVNR 12345." + ip, "KVNR Z1234." + ip,
                "KV-Nr. " + LAENGE_6 + "." + ip, "K V N R 1234." + ip, "1234." + ip + " (Patienten-KVNR)")) {
            assertBestandUnveraendert(text, FindingType.IP);
        }
    }

    /** Randfall: Buchstabe, Kette mit vierstelliger vierter Gruppe und danach eine weitere gueltige IP-Kette. */
    @Test
    void bestandVorrang_buchstabeVorKetteMitVierstelligerVierterGruppeUndWeitererIpKette() {
        String ip = IK_FORM_DOKU_IP;
        String ipAnfang = ip.substring(0, ip.lastIndexOf('.') + 1);
        for (String objekt : List.of("Z" + ipAnfang + "1234." + ip, "Z" + ipAnfang + "1234.1.2.3.4",
                "z " + ipAnfang + "1234." + ip)) {
            for (String vor : List.of("KVNR ", "KV-Nr. ", "K V N R ", "Versichertennummer: ")) {
                assertBestandUnveraendert(vor + objekt, FindingType.IP);
            }
        }
    }

    @Test
    void bestandVorrang_emailPfadUndUrlMitNummerDavorDarinUndDahinter_alterFundBleibt() {
        String z = KVNR_GUELTIG.substring(1);
        String email = "@example.invalid";
        List<String> emails = List.of(KVNR_GUELTIG + email, "Z " + z + email, "a." + KVNR_GUELTIG + email,
                "a" + email + " " + KVNR_GUELTIG, "a" + email + KVNR_GUELTIG);
        for (String objekt : emails) {
            assertBestandUnveraendert("KVNR " + objekt, FindingType.EMAIL);
            assertBestandUnveraendert("KV-Nr. " + objekt, FindingType.EMAIL);
            assertBestandUnveraendert(objekt + " (Patienten-KVNR)", FindingType.EMAIL);
        }
        for (String objekt : List.of("C:" + BS + "Praxis" + BS + KVNR_GUELTIG + ".txt", "/home/praxis/Z" + z + "/x.txt",
                "/x/" + KVNR_GUELTIG + ".pdf", "/x/befund-" + KVNR_GUELTIG + ".pdf")) {
            assertBestandUnveraendert("KVNR " + objekt, FindingType.PFAD);
            assertBestandUnveraendert("K V N R " + objekt, FindingType.PFAD);
        }
        for (String objekt : List.of("https://example.invalid/" + KVNR_GUELTIG, "https://example.invalid/?id=" + z,
                KVNR_GUELTIG + "https://example.invalid/x")) {
            assertBestandUnveraendert("KVNR " + objekt, FindingType.URL);
            assertBestandUnveraendert("Versichertennummer: " + objekt, FindingType.URL);
        }
    }

    // ------------------------------------------------------------------ Nachtrag 3 zu T-6: Zuschneiden

    private static final char NBSP = (char) 0xA0;
    private static final String QUELLE_REST = KvnrRecognizer.SOURCE_REST;

    private static boolean istAsciiZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    /** Der Text ohne Platzhalter der Form {@code [[TYP_n]]}; uebrig bleiben nur Zeichen, die nicht ersetzt wurden. */
    private static String ohnePlatzhalter(String text) {
        return text.replaceAll("\\[\\[[A-Z]+_[0-9]+\\]\\]", "");
    }

    private static boolean hatAsciiZiffer(String text) {
        return text.chars().anyMatch(c -> istAsciiZiffer((char) c));
    }

    private static List<Finding> restfunde(List<Finding> funde) {
        return funde.stream().filter(f -> f.source().equals(QUELLE_REST)).toList();
    }

    /**
     * Prueft einen Text, in dem ein alter Fund des Typs {@code altTyp} die Nummer am Buchstaben abschneidet: alle
     * alten Nicht-KV-Funde stehen in allen Feldern unveraendert in der neuen Liste; jede Ziffer ausserhalb der alten
     * Funde ist von einem KVNR-Restfund (Quelle {@code kvnr-rest}, Konfidenz hoechstens 0,80) abgedeckt; nach der
     * Pseudonymisierung bleibt keine Ziffer ausserhalb der Platzhalter, und der Roundtrip ist zeichengleich.
     */
    private static void assertRestAbgedeckt(String text, FindingType altTyp) {
        List<Finding> alt = KvnrBsnrHilfen.ALT_TRENNER.scan(text);
        List<Finding> neu = scan(text);
        assertTrue(alt.stream().anyMatch(f -> f.type().equals(altTyp)), "kein alter Fund des erwarteten Typs");
        for (Finding f : alt) {
            assertTrue(neu.contains(f), "alter Fund fehlt oder ist veraendert");
        }
        assertTrue(nurBestand(neu).equals(nurBestand(alt)), "Teilliste der Bestandsfunde ungleich");
        List<Finding> rest = restfunde(neu);
        assertFalse(rest.isEmpty(), "kein Restfund");
        for (Finding r : rest) {
            assertEquals(FindingType.KVNR, r.type(), "Typ des Restfundes");
            assertTrue(r.confidence() <= 0.80, "Konfidenz des Restfundes ueber 0,80");
        }
        for (int i = 0; i < text.length(); i++) {
            int pos = i;
            boolean imAltenFund = alt.stream().anyMatch(f -> f.start() <= pos && pos < f.end());
            if (istAsciiZiffer(text.charAt(i)) && !imAltenFund) {
                assertTrue(rest.stream().anyMatch(f -> f.start() <= pos && pos < f.end()),
                        "Ziffer ausserhalb des alten Fundes nicht von einem Restfund abgedeckt");
            }
        }
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, neu);
        assertTrue(ergebnis.text().contains("[[" + altTyp.name() + "_"), "alter Fund nicht ersetzt");
        assertTrue(ergebnis.text().contains("[[KVNR_"), "Restfund nicht ersetzt");
        assertFalse(hatAsciiZiffer(ohnePlatzhalter(ergebnis.text())), "Ziffer im pseudonymisierten Text");
        assertRoundtrip(text, ergebnis);
    }

    /** Die Pfad- und URL-Formen, in denen der alte Fund am Buchstaben der Nummer endet. */
    private record Objekt(String vorBuchstabe, FindingType typ) {
    }

    private static List<Objekt> objekteMitBuchstabeAmEnde() {
        return List.of(new Objekt("C:" + BS + "Praxis" + BS, FindingType.PFAD),
                new Objekt("C:/Praxis/", FindingType.PFAD),
                new Objekt(BS + BS + "srv" + BS + "share" + BS, FindingType.PFAD),
                new Objekt("/home/praxis/", FindingType.PFAD),
                new Objekt("file:///C:/Praxis/", FindingType.PFAD),
                new Objekt("https://example.invalid/?kvnr=", FindingType.URL));
    }

    @Test
    void zuschneiden_pfadUndUrlOhneAnfuehrungszeichen_ziffernHinterDemBuchstabenSindDurchRestfundGeschuetzt() {
        String buchstabe = KVNR_GUELTIG.substring(0, 1);
        String z = KVNR_GUELTIG.substring(1);
        List<String> formen = List.of(z, z.substring(0, 3) + " " + z.substring(3, 6) + " " + z.substring(6));
        int faelle = 0;
        for (String kontext : List.of("KVNR: ", "Versichertennummer ", "KV-Nr. ")) {
            for (Objekt objekt : objekteMitBuchstabeAmEnde()) {
                for (String trenner : List.of(" ", String.valueOf(NBSP))) {
                    for (String form : formen) {
                        assertRestAbgedeckt(kontext + objekt.vorBuchstabe() + buchstabe + trenner + form,
                                objekt.typ());
                        faelle++;
                    }
                }
            }
        }
        assertEquals(72, faelle, "Zahl der geprueften Texte");
        // Positiv-Gegenprobe (Regel 12): dieselbe Nummer ohne Pfad ist ein einziger KVNR-Fund mit der Quelle kvnr
        assertEinFund(scan("KVNR: " + buchstabe + " " + z), FindingType.KVNR, 6, buchstabe + " " + z, 0.95, "kvnr");
    }

    /** Zuschneiden mit dem Pfad am Buchstaben: der Restfund hat die Felder des Plans (Typ, Quelle, Konfidenz). */
    @Test
    void zuschneiden_restfundHatTypQuelleKonfidenzUndTextausschnittDesPlans() {
        String z = KVNR_GUELTIG.substring(1);
        String text = "KVNR: C:" + BS + "Praxis" + BS + "Z " + z;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.PFAD, funde.get(0).type(), "Typ des alten Fundes");
        Finding rest = funde.get(1);
        assertEquals(FindingType.KVNR, rest.type(), "Typ");
        assertEquals(text.length() - z.length(), rest.start(), "Start");
        assertEquals(text.length(), rest.end(), "Ende");
        assertTrue(z.equals(rest.originalValue()), "Textausschnitt");
        assertEquals(0.80, rest.confidence(), "Konfidenz (Kandidat 0,95, hoechstens 0,80)");
        assertTrue("kvnr-rest".equals(rest.source()), "Quelle");
        // die Konfidenz eines schwaecheren Kandidaten bleibt: reine Ziffernfolge 0,40 neben einer IP
        List<Finding> ip = scan("KVNR 12 " + IP_ZWEI + " 123");
        assertEquals(2, ip.size(), "Zahl der Funde mit IP");
        assertEquals(FindingType.IP, ip.get(0).type(), "Typ des Bestandsfundes");
        assertEquals(0.40, ip.get(1).confidence(), "Konfidenz des Restfundes hinter der IP");
        assertTrue(QUELLE_REST.equals(ip.get(1).source()), "Quelle des Restfundes hinter der IP");
    }

    @Test
    void zuschneiden_kontextwortNummerKommaDateiMitDerselbenNummer_ersteNennungNormalZweiteAlsRestfund() {
        String buchstabe = KVNR_GUELTIG.substring(0, 1);
        String z = KVNR_GUELTIG.substring(1);
        for (String kontext : List.of("Versichertennummer ", "KVNR: ", "KV-Nr. ")) {
            String text = kontext + KVNR_GUELTIG + ", Datei C:" + BS + "Praxis" + BS + buchstabe + " " + z;
            List<Finding> funde = scan(text);
            assertEquals(3, funde.size(), "Zahl der Funde");
            assertEquals(FindingType.KVNR, funde.get(0).type(), "erste Nennung");
            assertTrue("kvnr".equals(funde.get(0).source()), "Quelle der ersten Nennung");
            assertEquals(FindingType.PFAD, funde.get(1).type(), "Pfad");
            assertTrue(QUELLE_REST.equals(funde.get(2).source()), "Quelle der zweiten Nennung");
            assertTrue(z.equals(funde.get(2).originalValue()), "Ziffern der zweiten Nennung");
            assertTrue(funde.get(1).equals(KvnrBsnrHilfen.ALT_TRENNER.scan(text).get(0)), "alter Pfad-Fund");
            PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
            // verschiedene Originalwerte (mit und ohne Buchstabe) ergeben verschiedene Platzhalter
            assertTrue(ergebnis.text().equals(kontext + "[[KVNR_1]], Datei [[PFAD_2]] [[KVNR_3]]"),
                    "pseudonymisierter Text");
            assertFalse(hatAsciiZiffer(ohnePlatzhalter(ergebnis.text())), "Ziffer im pseudonymisierten Text");
            assertRoundtrip(text, ergebnis);
        }
    }

    // ------------------------------------------------------------------ Nachtrag 3 zu T-6: Schwelle von drei Ziffern

    @Test
    void zuschneiden_schwelleDreiZiffern_zweiZiffernEntfallenDreiBleibenEinBuchstabeBleibtUngeschuetzt() {
        // genau drei Ziffern vor der IP bleiben
        List<Finding> dreiVorn = scan("KVNR 123 " + IP_ZWEI);
        assertEquals(1, restfunde(dreiVorn).size(), "Restfunde bei 3 Ziffern vor der IP");
        assertEquals(5, restfunde(dreiVorn).get(0).start(), "Restfund liegt vor der IP");
        // zwei Ziffern vor der IP entfallen
        assertEquals(0, restfunde(scan("KVNR 12 " + IP_ZWEI)).size(), "Restfunde bei 2 Ziffern vor der IP");
        // genau drei Ziffern hinter der IP bleiben, zwei davor entfallen
        List<Finding> dreiHinten = scan("KVNR 12 " + IP_ZWEI + " 123");
        assertEquals(1, restfunde(dreiHinten).size(), "Restfunde bei 2 vor und 3 hinter der IP");
        assertTrue("123".equals(restfunde(dreiHinten).get(0).originalValue()), "Restfund hinter der IP");
        // der Kandidat umschliesst die IP: Reststuecke davor und dahinter je nach Schwelle
        assertEquals(0, restfunde(scan("KVNR 12 " + IP_ZWEI + " 45")).size(), "Restfunde bei 2 vor und 2 hinter");
        // der Erkenner liefert keinen Kandidaten mit drei Ziffern auf beiden Seiten der IP: Direkttest der Stufe
        String umschlossen = "123 " + IP_ZWEI + " 456";
        Finding kandidat = new Finding(FindingType.KVNR, umschlossen, 0, umschlossen.length(), 0.40,
                "kvnr-form-abweichend");
        Finding ipFund = new Finding(FindingType.IP, IP_ZWEI, 4, 4 + IP_ZWEI.length(), 0.85, "ipv4");
        List<Finding> reste = TextScanner.zuschneiden(List.of(kandidat), QUELLE_REST, List.of(ipFund));
        assertEquals(2, reste.size(), "Reststuecke mit je drei Ziffern davor und dahinter");
        assertTrue("123".equals(reste.get(0).originalValue()), "Reststueck davor");
        assertTrue("456".equals(reste.get(1).originalValue()), "Reststueck dahinter");
        String zweiUndDrei = "12 " + IP_ZWEI + " 456";
        Finding kandidat23 = new Finding(FindingType.KVNR, zweiUndDrei, 0, zweiUndDrei.length(), 0.40, "kvnr");
        Finding ipFund23 = new Finding(FindingType.IP, IP_ZWEI, 3, 3 + IP_ZWEI.length(), 0.85, "ipv4");
        List<Finding> nurHinten = TextScanner.zuschneiden(List.of(kandidat23), QUELLE_REST, List.of(ipFund23));
        assertEquals(1, nurHinten.size(), "Reststuecke mit zwei Ziffern davor und drei dahinter");
        assertTrue("456".equals(nurHinten.get(0).originalValue()), "Reststueck dahinter");
        // nur zwei Ziffern ausserhalb (Praefixlaenge hinter der IP): kein Restfund, der IP-Fund bleibt allein
        String praefix = "KVNR " + IK_FORM_DOKU_IP + "/24";
        assertTrue(scan(praefix).equals(KvnrBsnrHilfen.ALT_TRENNER.scan(praefix)), "Fundliste mit Praefixlaenge");
        assertEquals(0, restfunde(scan(praefix)).size(), "Restfunde bei Praefixlaenge");
        // nur der Buchstabe ausserhalb: kein Restfund
        String buchstabe = KVNR_UNGUELTIG.substring(0, 1);
        String ziffern = KVNR_UNGUELTIG.substring(1);
        assertEquals(0, restfunde(scan("KVNR " + buchstabe + " " + ziffern + "@example.invalid")).size(),
                "Restfunde bei nur dem Buchstaben ausserhalb");
        // Positiv-Gegenprobe (Regel 12) mit derselben Nummer, ohne Ueberschneidung: ein KVNR-Fund ueber alle Zeichen
        assertEinFund(scan("KVNR " + buchstabe + " " + ziffern), FindingType.KVNR, 5, buchstabe + " " + ziffern,
                0.50, QUELLE_PZ);
    }

    // ------------------------------------------------------------------ Nachtrag 3 zu T-6: angrenzende Kandidaten

    /** Der Kandidat grenzt an einen Bestandsfund an (ohne gemeinsame Position): er bleibt vollstaendig erhalten. */
    @Test
    void angrenzend_kvnrKandidatDirektHinterIpUndDirektVorPfad_bleibtVollstaendigUnveraendert() {
        String hinterIp = "KVNR " + IK_FORM_DOKU_IP + KVNR_GUELTIG;
        List<Finding> funde = scan(hinterIp);
        assertEquals(2, funde.size(), "Zahl der Funde hinter der IP");
        assertEquals(FindingType.IP, funde.get(0).type(), "Typ des Bestandsfundes");
        assertEquals(funde.get(0).end(), funde.get(1).start(), "Kandidat grenzt unmittelbar an");
        assertEquals(FindingType.KVNR, funde.get(1).type(), "Typ");
        assertTrue(KVNR_GUELTIG.equals(funde.get(1).originalValue()), "Wert unveraendert");
        assertFalse(QUELLE_REST.equals(funde.get(1).source()), "kein Restfund");
        assertTrue(new KvnrRecognizer().detect(hinterIp).contains(funde.get(1)), "Fund gleich einem Kandidaten");

        String vorPfad = "KVNR " + KVNR_GUELTIG + "C:" + BS + "x" + BS + "a.txt";
        List<Finding> vor = scan(vorPfad);
        assertEquals(2, vor.size(), "Zahl der Funde vor dem Pfad");
        assertEquals(FindingType.KVNR, vor.get(0).type(), "Typ");
        assertEquals(vor.get(0).end(), vor.get(1).start(), "Pfad grenzt unmittelbar an");
        assertEquals(FindingType.PFAD, vor.get(1).type(), "Typ des Bestandsfundes");
        assertTrue(KVNR_GUELTIG.equals(vor.get(0).originalValue()), "Wert unveraendert");
        assertFalse(QUELLE_REST.equals(vor.get(0).source()), "kein Restfund");
        assertTrue(new KvnrRecognizer().detect(vorPfad).contains(vor.get(0)), "Fund gleich einem Kandidaten");
    }

    /** Direkttest der Zuschneidestufe: Angrenzen ist keine Ueberschneidung, eine gemeinsame Position ist eine. */
    @Test
    void angrenzend_zuschneidenDirekt_grenzeIstKeineUeberschneidungEinePositionMehrSchneidet() {
        String hinten = "KVNR " + IK_FORM_DOKU_IP + KVNR_GUELTIG;
        int ende = 5 + IK_FORM_DOKU_IP.length();
        Finding ip = new Finding(FindingType.IP, IK_FORM_DOKU_IP, 5, ende, 0.85, "ipv4");
        Finding dahinter = kandidat(hinten, ende, hinten.length());
        assertTrue(List.of(dahinter).equals(TextScanner.zuschneiden(List.of(dahinter), QUELLE_REST, List.of(ip))),
                "angrenzend hinter dem Bestand");
        // eine Position weiter vorn: gemeinsame Position, der Kandidat wird auf den Teil hinter dem Bestand gekuerzt
        List<Finding> eine = TextScanner.zuschneiden(List.of(kandidat(hinten, ende - 1, hinten.length())),
                QUELLE_REST, List.of(ip));
        assertEquals(1, eine.size(), "Zahl der Funde bei einer gemeinsamen Position");
        assertEquals(ende, eine.get(0).start(), "Start des Restfundes");
        assertEquals(hinten.length(), eine.get(0).end(), "Ende des Restfundes");
        assertTrue(QUELLE_REST.equals(eine.get(0).source()), "Quelle des Restfundes");

        String vorn = "KVNR " + KVNR_GUELTIG + "C:" + BS + "x" + BS + "a.txt";
        int pfadStart = 5 + KVNR_GUELTIG.length();
        Finding pfad = new Finding(FindingType.PFAD, vorn.substring(pfadStart), pfadStart, vorn.length(), 0.95,
                "pfad-laufwerk");
        Finding davor = kandidat(vorn, 5, pfadStart);
        assertTrue(List.of(davor).equals(TextScanner.zuschneiden(List.of(davor), QUELLE_REST, List.of(pfad))),
                "angrenzend vor dem Bestand");
        List<Finding> eineVorn = TextScanner.zuschneiden(List.of(kandidat(vorn, 5, pfadStart + 1)), QUELLE_REST,
                List.of(pfad));
        assertEquals(1, eineVorn.size(), "Zahl der Funde bei einer gemeinsamen Position vorn");
        assertEquals(5, eineVorn.get(0).start(), "Start des Restfundes vorn");
        assertEquals(pfadStart, eineVorn.get(0).end(), "Ende des Restfundes vorn");
        // liegt der Kandidat ganz im Bestand, entfaellt er
        assertTrue(TextScanner.zuschneiden(List.of(kandidat(hinten, 5, ende)), QUELLE_REST, List.of(ip)).isEmpty(),
                "Kandidat ganz im Bestand");
        // ein Fund eines anderen Typs (LANR) ist kein Bestand
        Finding lanr = new Finding(FindingType.LANR, IK_FORM_DOKU_IP, 5, ende, 0.95, "lanr");
        Finding ueber = kandidat(hinten, 5, hinten.length());
        assertTrue(List.of(ueber).equals(TextScanner.zuschneiden(List.of(ueber), QUELLE_REST, List.of(lanr))),
                "LANR ist kein Bestand");
    }

    private static Finding kandidat(String text, int start, int ende) {
        return new Finding(FindingType.KVNR, text.substring(start, ende), start, ende, 0.95, "kvnr");
    }

    // ------------------------------------------------------------------ Nachtrag 3 zu T-6: nachrangige Erkenner

    @Test
    void nachrangig_inStandardIstGenauDerKvnrErkennerNachrangigEinAnderer_nicht() {
        int nachrangig = 0;
        for (Recognizer erkenner : TextScanner.standard().recognizers()) {
            // Nachtrag zu T-14: auch BsnrRecognizer ist nachrangig (alt: nur instanceof KvnrRecognizer)
            boolean istKvnr = erkenner instanceof KvnrRecognizer || erkenner instanceof BsnrRecognizer;
            assertEquals(istKvnr, erkenner.nachrangig(), "Flag nachrangig() von "
                    + erkenner.getClass().getSimpleName());
            nachrangig += erkenner.nachrangig() ? 1 : 0;
        }
        // Nachtrag zu T-14: genau zwei nachrangige Erkenner (alter Wert: 1)
        assertEquals(2, nachrangig, "Zahl der nachrangigen Erkenner");
        Recognizer lambda = t -> List.of();
        assertFalse(lambda.nachrangig(), "Lambda-Erkenner ist nicht nachrangig");
        assertTrue(new KvnrRecognizer().restQuelle().equals(QUELLE_REST), "Rest-Quelle des KVNR-Erkenners");
        assertTrue(lambda.restQuelle().equals("rest"), "Standard-Rest-Quelle");
    }

    /** Ein weiterer nachrangiger Erkenner (so wird T-14 den BSNR-Erkenner eintragen) liefert seine eigene Quelle. */
    @Test
    void nachrangig_weitererErkenner_liefertRestfundeMitEigenerQuelleOhneUmbauDesScanners() {
        String text = "Datei /home/praxis/a " + LAENGE_6;
        int von = "Datei ".length();
        Recognizer nachrangigerErkenner = new Recognizer() {
            @Override
            public List<Finding> detect(String t) {
                return List.of(new Finding(FindingType.LANR, t.substring(von), von, t.length(), 0.70, "test"));
            }

            @Override
            public boolean nachrangig() {
                return true;
            }

            @Override
            public String restQuelle() {
                return "test-rest";
            }
        };
        List<Finding> funde = new TextScanner(List.of(new PathRecognizer(), nachrangigerErkenner)).scan(text);
        assertEquals(2, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.PFAD, funde.get(0).type(), "Typ des Bestandsfundes");
        assertEquals(FindingType.LANR, funde.get(1).type(), "Typ des Restfundes");
        assertTrue("test-rest".equals(funde.get(1).source()), "Quelle des Restfundes");
        assertTrue(LAENGE_6.equals(funde.get(1).originalValue()), "Wert des Restfundes");
        assertEquals(0.70, funde.get(1).confidence(), "Konfidenz des Restfundes");
    }

    // ------------------------------------------------------------------ bekannte Grenze (Nachtrag 3 zu T-6, Punkt 4)

    /**
     * Bekannte Grenze (Nachtrag 3 zu T-6, Punkt 4; Test {@code bekannteGrenze...} uebernimmt T-8 nach Kap. 11): Der
     * Teil einer Nummer, der im Bestandsfund liegt, traegt dessen Typ; ein einzelner Buchstabe oder ein Rest mit
     * weniger als drei Ziffern ausserhalb bleibt stehen.
     */
    @Test
    void bekannteGrenze_nummerUeberschneidetEinenAnderenFund_teilImBestandstypUndKleinerRestBleibtStehen() {
        String buchstabe = KVNR_UNGUELTIG.substring(0, 1);
        String ziffern = KVNR_UNGUELTIG.substring(1);
        // Buchstabe ausserhalb: bleibt stehen, die Ziffern tragen den Typ EMAIL
        String teilweise = "KVNR " + buchstabe + " " + ziffern + "@example.invalid";
        List<Finding> funde = scan(teilweise);
        assertEquals(1, funde.size(), "Zahl der Funde");
        assertEquals(FindingType.EMAIL, funde.get(0).type(), "Typ des einzigen Fundes");
        assertTrue(funde.get(0).start() > 5 + buchstabe.length(), "Buchstabe bleibt ausserhalb des Fundes");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(teilweise, funde);
        assertTrue(ergebnis.text().startsWith("KVNR " + buchstabe + " [[EMAIL_"), "Buchstabe bleibt stehen");
        assertRoundtrip(teilweise, ergebnis);
        // zwei Ziffern ausserhalb (Praefixlaenge hinter einer IP) bleiben stehen
        String praefix = "KVNR " + IK_FORM_DOKU_IP + "/24";
        PseudonymizationResult mitPraefix = PSEUDONYMIZER.pseudonymize(praefix, scan(praefix));
        assertTrue(mitPraefix.text().equals("KVNR [[IP_1]]/24"), "Praefixlaenge bleibt stehen");
        assertRoundtrip(praefix, mitPraefix);
        // Positiv-Gegenprobe mit derselben Nummer, ohne die Ueberschneidung: ein KVNR-Fund ueber alle Zeichen
        assertEinFund(scan("KVNR " + buchstabe + " " + ziffern), FindingType.KVNR, 5, buchstabe + " " + ziffern,
                0.50, QUELLE_PZ);
        // Positiv-Gegenprobe der Schwelle: mit drei Ziffern ausserhalb gibt es einen Restfund
        assertEquals(1, restfunde(scan("KVNR " + IP_ZWEI + " 123")).size(), "Gegenprobe mit drei Ziffern");
        // ganz im Dateinamen: der Pfad-Fund schuetzt die Nummer, mit dem Typ PFAD
        String ganz = "KVNR /x/befund-" + KVNR_UNGUELTIG + ".pdf";
        List<Finding> imPfad = scan(ganz);
        assertEquals(1, imPfad.size(), "Zahl der Funde im Dateinamen");
        assertEquals(FindingType.PFAD, imPfad.get(0).type(), "Typ im Dateinamen");
        assertTrue(imPfad.get(0).originalValue().contains(KVNR_UNGUELTIG), "Nummer im Pfad-Fund");
    }

    // ------------------------------------------------------------------ AK-20: Mensch entscheidet

    @Test
    void ak20_fraglicherKvnrFundWirdGeliefertUndOhneUebergabeNichtErsetzt() {
        String text = "KVNR " + KVNR_UNGUELTIG;
        List<Finding> funde = scan(text);
        assertEinFund(funde, FindingType.KVNR, 5, KVNR_UNGUELTIG, 0.50, QUELLE_PZ);
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(0).level(), "Stufe");

        PseudonymizationResult ohneUebergabe = PSEUDONYMIZER.pseudonymize(text, List.of());
        assertTrue(text.equals(ohneUebergabe.text()), "Text ohne Uebergabe veraendert");
        assertEquals(0, ohneUebergabe.mapping().size(), "Zuordnung ohne Uebergabe");

        PseudonymizationResult mitUebergabe = PSEUDONYMIZER.pseudonymize(text, funde);
        assertTrue(mitUebergabe.text().equals("KVNR [[KVNR_1]]"), "Text mit Uebergabe");
        assertRoundtrip(text, mitUebergabe);
    }

    @Test
    void ak20_formAbweichendeKvnrWirdGeliefertAberNurBeiUebergabeErsetzt() {
        String text = "Versichertennummer " + LANR_GUELTIG;
        List<Finding> funde = scan(text);
        assertEinFund(funde, FindingType.KVNR, 19, LANR_GUELTIG, 0.40, QUELLE_FORM);
        assertTrue(text.equals(PSEUDONYMIZER.pseudonymize(text, List.of()).text()), "Text ohne Uebergabe");
        assertTrue(PSEUDONYMIZER.pseudonymize(text, funde).text().equals("Versichertennummer [[KVNR_1]]"),
                "Text mit Uebergabe");
    }

    // ------------------------------------------------------------------ AK-35: kein Klartext

    @Test
    void ak35_kvnrMarkerFehltInToStringVonFundErgebnisUndZuordnungUndInAusnahmenDesScanners() {
        String text = "KVNR " + KVNR_MARKER + " Ende";
        List<String> teile = List.of(KVNR_MARKER, KVNR_MARKER.substring(1), KVNR_MARKER.substring(1, 7));
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "Zahl der Funde");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        List<String> ausgaben = List.of(funde.get(0).toString(), funde.toString(), ergebnis.toString(),
                ergebnis.mapping().toString(), ergebnis.text());
        for (String ausgabe : ausgaben) {
            for (String teil : teile) {
                assertFalse(ausgabe.contains(teil), "Marker in toString() oder im pseudonymisierten Text");
            }
        }
        Recognizer wirft = t -> {
            throw new IllegalArgumentException(KVNR_MARKER);
        };
        TextScanner scanner = new TextScanner(List.of(new KvnrRecognizer(), wirft));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> scanner.scan(text));
        assertFalse(e.getMessage().contains(KVNR_MARKER), "Marker in der Meldung");
        assertNull(e.getCause(), "Ursache");
    }
}
