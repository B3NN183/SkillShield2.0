package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Referenz-Ticket, Teil T-1 (Plan geheimnisse-erkennung; Anforderung AK-48 a, AK-49): Das Ticket liegt als Ressource
 * {@code de/skillshield/core/geheimnis/referenz-ticket.txt} im Kern-Testbaum (Wortlaut des Nutzers mit den zwei
 * Ersetzungen aus D-17: E-Mail-Domaene {@code kv-beispiel.example}, IP {@code 192.0.2.47}). Dieser Test haelt die
 * Ablage und den gemessenen Stand "heute" fest, gemessen gegen die eingefrorene Kopie
 * {@link TextScannerStandGeheimnis#ALT} (nicht gegen {@code standard()}): sechs Bestandsfunde, und das Passwort
 * {@code Sommer2026!} ist heute durch keinen Fund geschuetzt. Die Abnahme des neuen PASSWORT-Funds (AK-48 b) folgt in
 * T-4 als eigener Test; diese Messung bleibt unveraendert gueltig (eingefroren ab Abnahme T-1, Plan Kapitel 6.3).
 *
 * <p>Meldungen nennen Fall-IDs, Zahlen und Typen, nie Textinhalte.
 */
class ReferenzTicketTest {

    private static final String DATEI = "referenz-ticket.txt";
    private static final String AT = "@";

    private static String ticket() {
        return SynthetischeGeheimnisse.korpusText(DATEI);
    }

    private static Finding fund(String text, FindingType typ, int start, int ende, double konfidenz, String quelle) {
        return new Finding(typ, text.substring(start, ende), start, ende, konfidenz, quelle);
    }

    /** Die gemessene Liste "heute" (AK-48 a): sechs Bestandsfunde. */
    private static List<Finding> heute(String text) {
        return List.of(
                fund(text, FindingType.EMAIL, 77, 106, 0.95, "email"),
                fund(text, FindingType.PFAD, 291, 334, 0.95, "pfad-unc"),
                fund(text, FindingType.IP, 419, 429, 0.85, "ipv4"),
                fund(text, FindingType.PFAD, 443, 475, 0.95, "pfad-laufwerk"),
                fund(text, FindingType.PFAD, 480, 504, 0.90, "pfad-unix"),
                fund(text, FindingType.URL, 691, 744, 0.95, "url"));
    }

    private static boolean frei(int start, int ende, List<Finding> funde) {
        for (Finding f : funde) {
            if (f.start() < ende && start < f.end()) {
                return false;
            }
        }
        return true;
    }

    private static boolean wortFrei(String text, String wort, List<Finding> funde) {
        int von = text.indexOf(wort);
        return von >= 0 && frei(von, von + wort.length(), funde);
    }

    // ------------------------------------------------------------------ Ablage (AK-49)

    @Test
    void ablage_ticketIstLesbar_lfNormalisiert_endetMitZeilenumbruch() {
        String text = ticket();
        assertFalse(text.isEmpty(), "Ticket leer");
        assertFalse(text.contains("\r"), "CR im gelesenen Ticket");
        assertTrue(text.endsWith("\n"), "Ticket endet nicht mit LF");
        assertEquals(11, text.split("\n", -1).length - 1, "Zeilenzahl");
    }

    @Test
    void ablage_dieZweiErsetzungenSindDrin_dieOriginalwerteNicht() {
        String text = ticket();
        assertEquals(1, zaehle(text, "t.krueger" + AT + "kv-beispiel.example"), "E-Mail");
        assertEquals(1, zaehle(text, "SRV-APP07 (192.0.2.47)"), "IP");
        assertFalse(text.contains("kv-beispiel." + "de"), "alte Domaene");
        assertFalse(text.contains("10.20." + "30.47"), "alte IP");
    }

    @Test
    void ablage_waechterAk49_keineEmailMitNichtReserviertemEnde_keineIpAusserhalbDerDokuNetze() {
        assertEquals(List.of(), GeheimnisKorpusWaechterTest.pruefeText(DATEI, ticket()));
    }

    @Test
    void ablage_ziffernkettenWaechter_dieRufnummerHatKeineNeunerFolge() {
        String text = ticket();
        TestdatenSynthetischTest.Ergebnis erg = TestdatenSynthetischTest.pruefe("resources/" + DATEI, text,
                Set.of(), Set.of(), true);
        assertEquals(List.of(), erg.verstoesse());
        Pattern nummer = Pattern.compile("Tel\\. [0-9]{4} [0-9]{3}-[0-9]{4}");
        assertTrue(nummer.matcher(text).find(), "Rufnummer in der Form 4-3-4 fehlt");
    }

    @Test
    void ablage_keineFormNachAk50_kopfzeileEinesSchluesselsOderTokenSteht() {
        assertEquals(List.of(), GeheimnisLiteralWaechterTest.pruefeText(DATEI, ticket()));
    }

    private static int zaehle(String text, String teil) {
        int n = 0;
        for (int p = text.indexOf(teil); p >= 0; p = text.indexOf(teil, p + teil.length())) {
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ Bausteine laut Uebersicht, Abschnitt 6

    @Test
    void bausteine_alleStehenImTicket() {
        String text = ticket();
        List<String> fehlt = new ArrayList<>();
        for (String baustein : bausteine()) {
            if (!text.contains(baustein)) {
                fehlt.add("Baustein " + baustein.length() + " Zeichen");
            }
        }
        assertEquals(List.of(), fehlt);
        assertEquals(2, zaehle(text, "svc_abr_export"), "svc_abr_export steht zweimal");
        assertEquals(1, zaehle(text, P1), "Passwort steht einmal");
    }

    private static List<String> bausteine() {
        return List.of("Ticket #48213", "Berechtigungsfehler", "Thomas Kr" + (char) 0xFC + "ger",
                "t.krueger" + AT + "kv-beispiel.example", "KVBSP\\tkrueger", "ABR_Sachbearbeitung_RW",
                "AD-Gruppe GG-ABR-Export-Lesen", "Version 4.2 von ArvisPro", "Export",
                "\\\\srv-fs02.kvbsp.local\\Abrechnung\\Export\\Q3", "svc_abr_export auf SRV-APP07",
                "D:\\Apps\\ArvisPro\\logs\\export.log", "/var/log/arvis/batch.log", "dbsrv-ora01",
                "jdbc:oracle:thin:" + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", "Schema ABR_STAGE",
                "Tabelle T_EXPORT_QUEUE", "Tempor" + (char) 0xE4 + "res Passwort f" + (char) 0xFC
                        + "r svc_abr_export: " + P1,
                "https://wiki.kvbsp.local/display/IT/Abrechnungsexport", "Gruppe GG-ABR-Export-Schreiben",
                "Dienst neu gestartet", "Change CHG0012345");
    }

    // ------------------------------------------------------------------ Stand heute (AK-48 a)

    @Test
    void heute_sechsBestandsfunde_mitTypBereichKonfidenzQuelle() {
        String text = ticket();
        assertEquals(heute(text), TextScannerStandGeheimnis.ALT.scan(text));
    }

    @Test
    void heute_dieSechsFundeDeckenDieErwartetenTextstellen() {
        String text = ticket();
        List<Finding> funde = TextScannerStandGeheimnis.ALT.scan(text);
        assertTrue(("t.krueger" + AT + "kv-beispiel.example").equals(funde.get(0).originalValue()), "E-Mail");
        assertTrue("\\\\srv-fs02.kvbsp.local\\Abrechnung\\Export\\Q3".equals(funde.get(1).originalValue()), "UNC");
        assertTrue("192.0.2.47".equals(funde.get(2).originalValue()), "IP");
        assertTrue("D:\\Apps\\ArvisPro\\logs\\export.log".equals(funde.get(3).originalValue()), "Laufwerk");
        assertTrue("/var/log/arvis/batch.log".equals(funde.get(4).originalValue()), "Unix");
        String url = "https://wiki.kvbsp.local/display/IT/Abrechnungsexport";
        assertTrue(url.equals(funde.get(5).originalValue()), "URL");
    }

    @Test
    void heute_dasPasswortIstDurchKeinenFundGeschuetzt_keinePositionIstAbgedeckt() {
        String text = ticket();
        List<Finding> funde = TextScannerStandGeheimnis.ALT.scan(text);
        int von = text.indexOf(P1);
        assertTrue(von > 0, "Passwort nicht gefunden");
        assertTrue(frei(von, von + P1.length(), funde), "ein Fund deckt eine Position des Passworts ab");
    }

    @Test
    void heute_festgehalteneNichtFunde_stehenFreiImSatz() {
        String text = ticket();
        List<Finding> funde = TextScannerStandGeheimnis.ALT.scan(text);
        List<String> abgedeckt = new ArrayList<>();
        List<String> woerter = List.of("Ticket #48213", "Thomas Kr" + (char) 0xFC + "ger", "KVBSP\\tkrueger",
                "ABR_Sachbearbeitung_RW", "GG-ABR-Export-Lesen", "GG-ABR-Export-Schreiben", "Version 4.2",
                "ArvisPro kann", "der Export nach", "svc_abr_export auf", "SRV-APP07", "auf dbsrv-ora01.",
                "jdbc:oracle:thin:" + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", "Schema ABR_STAGE",
                "Tabelle T_EXPORT_QUEUE", "Dienst neu gestartet", "Change CHG0012345");
        for (String wort : woerter) {
            if (!wortFrei(text, wort, funde)) {
                abgedeckt.add("Baustein " + wort.length() + " Zeichen");
            }
        }
        assertEquals(List.of(), abgedeckt);
        Matcher m = Pattern.compile("Tel\\. [0-9]{4} [0-9]{3}-[0-9]{4}").matcher(text);
        assertTrue(m.find(), "Rufnummer fehlt");
        assertTrue(frei(m.start(), m.end(), funde), "die Rufnummer ist heute ein Fund");
    }

    // ------------------------------------------------------------------ Pseudonymisierung heute

    @Test
    void heute_pseudonymisiertMitDenSechsFunden_passwortBleibtLesbar_dieStichwoerterStehen_roundtripGleich() {
        String text = ticket();
        List<Finding> funde = TextScannerStandGeheimnis.ALT.scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        String ersetzt = p.text();
        // die Nummern zaehlen ueber alle Typen in der Reihenfolge der Funde
        for (String platzhalter : List.of("[[EMAIL_1]]", "[[PFAD_2]]", "[[IP_3]]", "[[PFAD_4]]", "[[PFAD_5]]",
                "[[URL_6]]")) {
            assertTrue(ersetzt.contains(platzhalter), "Platzhalter " + platzhalter);
        }
        assertTrue(ersetzt.contains(P1), "heute ist das Passwort noch im Klartext");
        for (String bleibt : List.of("Version 4.2", "#48213", "CHG0012345", "Dienst neu gestartet",
                "Tempor" + (char) 0xE4 + "res Passwort f" + (char) 0xFC + "r svc_abr_export: ")) {
            assertTrue(ersetzt.contains(bleibt), "Klartext fehlt, Baustein " + bleibt.length() + " Zeichen");
        }
        for (Finding f : funde) {
            assertFalse(ersetzt.contains(f.originalValue()), "ersetzter Wert steht noch im Text: " + f);
        }
        String zurueck = new Reidentifier().restore(ersetzt, p.mapping()).text();
        assertTrue(text.equals(zurueck), "Rueckuebersetzung nicht zeichengleich");
    }

    // ------------------------------------------------------------------ T-4: Abnahme AK-48 b (neuer Stand)

    /** Der neue Fund (AK-48 b): das Passwort, 0,95, Quelle passwort-kontext. */
    private static Finding passwortFund(String text) {
        int von = text.indexOf(P1);
        return new Finding(GeheimnisTypen.PASSWORT, P1, von, von + P1.length(), 0.95, "passwort-kontext");
    }

    /** Die erwartete neue Fundliste: die sechs Funde "heute" plus der Passwort-Fund, nach Beginn sortiert. */
    private static List<Finding> neuErwartet(String text) {
        List<Finding> liste = new ArrayList<>(heute(text));
        liste.add(passwortFund(text));
        liste.sort(Comparator.comparingInt(Finding::start));
        return liste;
    }

    @Test
    void neu_genauEinNeuerFundUeberDasPasswort_dieSechsFundeHeuteSindFeldgleich() {
        String text = ticket();
        List<Finding> neu = TextScanner.standard().scan(text);
        assertEquals(7, neu.size(), "sechs Funde heute und ein neuer");
        assertEquals(neuErwartet(text), neu);
        for (Finding f : TextScannerStandGeheimnis.ALT.scan(text)) {
            assertTrue(neu.contains(f), "Fund von heute fehlt oder ist veraendert: " + f);
        }
        long passwoerter = neu.stream().filter(f -> f.type().equals(GeheimnisTypen.PASSWORT)).count();
        assertEquals(1, passwoerter, "genau ein PASSWORT-Fund");
        assertEquals(0, neu.stream().filter(f -> f.type().equals(GeheimnisTypen.TOKEN)).count(), "kein TOKEN-Fund");
    }

    @Test
    void neu_derPasswortFundHatTypBereichKonfidenzStufeUndQuelle() {
        String text = ticket();
        Finding pw = TextScanner.standard().scan(text).stream()
                .filter(f -> f.type().equals(GeheimnisTypen.PASSWORT)).findFirst().orElseThrow();
        assertEquals(GeheimnisTypen.PASSWORT, pw.type());
        assertTrue(P1.equals(text.substring(pw.start(), pw.end())), "Bereich deckt genau das Passwort");
        assertTrue(P1.equals(pw.originalValue()), "Originalwert");
        assertEquals(0.95, pw.confidence(), 0.0);
        assertEquals(ConfidenceLevel.SICHER, ConfidenceLevel.of(pw.confidence()));
        assertEquals("passwort-kontext", pw.source());
    }

    @Test
    void neu_pseudonymisiertOhnePasswort_stichwoerterStehen_roundtripZeichengleich() {
        String text = ticket();
        List<Finding> funde = TextScanner.standard().scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        String ersetzt = p.text();
        assertFalse(ersetzt.contains(P1), "Passwort steht im Klartext");
        assertFalse(ersetzt.contains("Sommer2026"), "Teil des Passworts steht im Klartext");
        // die Nummer im Platzhalter zaehlt ueber alle Typen: das Passwort ist der sechste Fund (vor der URL)
        assertTrue(ersetzt.contains("[[PASSWORT_6]]"), "Platzhalter des Passworts");
        assertTrue(ersetzt.contains("[[URL_7]]"), "die URL ist jetzt der siebte Fund");
        assertEquals(1, zaehle(ersetzt, "[[PASSWORT_"), "genau ein PASSWORT-Platzhalter");
        for (String bleibt : List.of("Version 4.2", "#48213", "CHG0012345", "Export", "Dienst neu gestartet",
                "Tempor" + (char) 0xE4 + "res Passwort f" + (char) 0xFC + "r svc_abr_export: [[PASSWORT_6]]")) {
            assertTrue(ersetzt.contains(bleibt), "Klartext fehlt, Baustein " + bleibt.length() + " Zeichen");
        }
        for (Finding f : funde) {
            assertFalse(ersetzt.contains(f.originalValue()), "ersetzter Wert steht noch im Text: " + f);
        }
        String zurueck = new Reidentifier().restore(ersetzt, p.mapping()).text();
        assertTrue(text.equals(zurueck), "Rueckuebersetzung nicht zeichengleich");
    }

    @Test
    void neu_alleFundeDesAltStandsBleibenUnveraendert_dieNichtFundeStehenWeiterFrei() {
        String text = ticket();
        List<Finding> funde = TextScanner.standard().scan(text);
        List<String> abgedeckt = new ArrayList<>();
        List<String> woerter = List.of("Ticket #48213", "Thomas Kr" + (char) 0xFC + "ger", "KVBSP\\tkrueger",
                "ABR_Sachbearbeitung_RW", "GG-ABR-Export-Lesen", "GG-ABR-Export-Schreiben", "Version 4.2",
                "ArvisPro kann", "der Export nach", "svc_abr_export auf", "SRV-APP07", "auf dbsrv-ora01.",
                "jdbc:oracle:thin:" + AT + "dbsrv-ora01.kvbsp.local:1521/ABRPROD", "Schema ABR_STAGE",
                "Tabelle T_EXPORT_QUEUE", "Dienst neu gestartet", "Change CHG0012345",
                "Tempor" + (char) 0xE4 + "res Passwort f" + (char) 0xFC + "r svc_abr_export: ");
        for (String wort : woerter) {
            if (!wortFrei(text, wort, funde)) {
                abgedeckt.add("Baustein " + wort.length() + " Zeichen");
            }
        }
        assertEquals(List.of(), abgedeckt);
        Matcher m = Pattern.compile("Tel\\. [0-9]{4} [0-9]{3}-[0-9]{4}").matcher(text);
        assertTrue(m.find(), "Rufnummer fehlt");
        assertTrue(frei(m.start(), m.end(), funde), "die Rufnummer ist ein Fund");
    }
}
