package de.skillshield.core.recognizer;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UNC-Pfade und ihre Abgrenzung zu IP, E-Mail und URL ueber {@link TextScanner#standard()} (T-12). Testdaten sind
 * synthetisch ({@code srv-beispiel}, {@code *.invalid}, {@code erika.mustermann}, 192.0.2.0/24); Backslashes ueber
 * {@code bs(...)}.
 */
class TextScannerUncTest {

    private static final TextScanner SCANNER = TextScanner.standard();

    private static List<Finding> scan(String text) {
        List<Finding> funde = SCANNER.scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertTrue(f.start() >= vorigesEnde, "sortiert, ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static Finding einziger(String text) {
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "genau ein Fund");
        return funde.get(0);
    }

    private static String pseudonymisiert(String text) {
        List<Finding> funde = scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    // ---- IP als Server (Plan 2.4.5 (c))

    @Test
    void ipAlsServerEinPfadFundDieIpLiegtDarin() {
        String text = bs("¦¦192.0.2.10¦freigabe¦a.txt");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-unc", f.source(), "unveraenderter Kandidat des umschliessenden Erkenners");
        assertEquals(text, f.originalValue());
        assertEquals(0.95, f.confidence());
        String aus = pseudonymisiert("Ziel " + text + " fertig");
        assertEquals("Ziel [[PFAD_1]] fertig", aus);
        assertFalse(aus.contains("192.0.2.10"));
    }

    @Test
    void ipAlsServerAlleDreiDokumentationsnetze() {
        for (String ip : List.of("192.0.2.10", "198.51.100.7", "203.0.113.250")) {
            String text = bs("Share ¦¦" + ip + "¦freigabe¦ordner ok");
            Finding f = einziger(text);
            assertEquals(bs("¦¦" + ip + "¦freigabe¦ordner"), f.originalValue());
            assertEquals(FindingType.PFAD, f.type());
            assertFalse(pseudonymisiert(text).contains(ip));
        }
    }

    /**
     * Umgestellt (vorher: nur IP-Fund): Plan Nachtrag T-12 Punkt 7, ein Segment ist ein UNC-Fund mit 0.60. Die IP liegt
     * ganz darin; nach der Konfliktregel gewinnt der umschliessende Pfad-Kandidat unveraendert (Quelle pfad-unc, seine
     * Konfidenz 0.60, nicht zusammengelegt).
     */
    @Test
    void ipNurAlsServerOhneFreigabeIstEinPfadFundDieIpLiegtDarin() {
        String text = bs("¦¦192.0.2.10");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-unc", f.source());
        assertEquals(text, f.originalValue());
        assertEquals(0.60, f.confidence());
        String aus = pseudonymisiert("Server " + text + " ok");
        assertEquals("Server [[PFAD_1]] ok", aus);
        assertFalse(aus.contains("192.0.2.10"));
    }

    // ---- Server ohne Freigabe (Plan Nachtrag T-12 Punkt 7)

    @Test
    void serverOhneFreigabeImSatzOhneSatzpunktImFund() {
        String text = bs("liegt auf ¦¦fileserver01.");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-unc", f.source());
        assertEquals(bs("¦¦fileserver01"), f.originalValue());
        assertEquals(0.60, f.confidence());
        assertEquals("liegt auf [[PFAD_1]].", pseudonymisiert(text));
    }

    @Test
    void serverOhneFreigabeMitAbschliessendemTrenner() {
        Finding f = einziger(bs("auf ¦¦fileserver01¦ ok"));
        assertEquals(bs("¦¦fileserver01¦"), f.originalValue());
        assertEquals(0.60, f.confidence());
    }

    @Test
    void zweiServerOhneFreigabeZweiPlatzhalter() {
        String text = bs("Server ¦¦abrechnung-srv und ¦¦fs01");
        assertEquals(2, scan(text).size());
        assertEquals("Server [[PFAD_1]] und [[PFAD_2]]", pseudonymisiert(text));
    }

    @Test
    void serverMitLeerzeichenImZitatIstEinFund() {
        String text = bs("\"¦¦srv mit leerzeichen\"");
        Finding f = einziger(text);
        assertEquals(bs("¦¦srv mit leerzeichen"), f.originalValue());
        assertEquals(0.60, f.confidence());
        assertEquals("\"[[PFAD_1]]\"", pseudonymisiert(text));
    }

    @Test
    void serverMitFreigabeBleibtBei095() {
        assertEquals(0.95, einziger(bs("¦¦srv¦freigabe¦a")).confidence());
        assertEquals(0.95, einziger(bs("Pfad \"¦¦srv mit¦freigabe\" ok")).confidence());
    }

    // ---- bekannte Folge und bekannte Luecke

    @Test
    void bekannteFolgeMaskierteZeichenfolgenWerdenPseudonymisiertUndBleibenUmkehrbar() {
        assertEquals("Zeilenumbruch [[PFAD_1]] im Text", pseudonymisiert(bs("Zeilenumbruch ¦¦n im Text")));
        assertEquals("a[[PFAD_1]]", pseudonymisiert(bs("a¦¦nb¦¦nc")));
    }

    /**
     * Bekannte Luecke (Plan Nachtrag T-12 Punkt 9, Folge-Feature "Zugangsdaten in Kommandozeilen"): nur der UNC-Pfad
     * wird ersetzt. Passwort-Argument und {@code /user:DOMAENE und Benutzername} bleiben im Klartext. Dieser Test haelt den
     * Ist-Zustand fest und ist anzupassen, wenn das Folge-Feature kommt.
     */
    @Test
    void bekannteLueckeNetUseZugangsdatenBleibenImKlartext() {
        String text = bs("net use Z: ¦¦srv-beispiel¦freigabe geheim /user:praxis¦erika.mustermann");
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        assertEquals(bs("¦¦srv-beispiel¦freigabe"), funde.get(0).originalValue());
        String aus = pseudonymisiert(text);
        assertEquals(bs("net use Z: [[PFAD_1]] geheim /user:praxis¦erika.mustermann"), aus);
    }

    // ---- E-Mail und Benutzername im UNC-Pfad

    @Test
    void emailImUncPfadLiegtDarinDerPfadFundBleibtUnveraendert() {
        String text = bs("¦¦srv-beispiel¦c$¦Users¦erika.beispiel@example.invalid¦Ablage");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-unc", f.source());
        assertEquals(text, f.originalValue());
    }

    @Test
    void benutzernameUndServerNichtImPseudonymisiertenText() {
        String text = bs("Profil ¦¦srv-beispiel¦c$¦Users¦erika.mustermann¦Desktop liegt dort.");
        String aus = pseudonymisiert(text);
        assertEquals("Profil [[PFAD_1]] liegt dort.", aus);
        assertFalse(aus.contains("erika.mustermann"));
        assertFalse(aus.contains("srv-beispiel"));
    }

    @Test
    void fqdnServerNichtImPseudonymisiertenText() {
        String text = bs("Ablage: ¦¦srv01.praxis.example¦daten¦akte.pdf.");
        String aus = pseudonymisiert(text);
        assertFalse(aus.contains("srv01"));
        assertFalse(aus.contains("praxis"));
        assertFalse(aus.contains("akte.pdf"));
        assertTrue(aus.endsWith("."), "Schlusspunkt bleibt");
    }

    // ---- URL: genau ein URL-Fund

    @Test
    void urlOhneBackslashBleibtGenauEinUrlFund() {
        String text = "https://example.invalid/a";
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals("url", f.source());
        assertEquals(text, f.originalValue());
    }

    @Test
    void urlMitPfadUndSchraegstrichenBleibtGenauEinUrlFund() {
        for (String text : List.of("Link https://example.invalid/a/b/c ende", "http://example.invalid//a//b",
                "https://srv-beispiel.invalid/freigabe/ordner/x.pdf")) {
            List<Finding> f = scan(text);
            assertEquals(1, f.size(), text);
            assertEquals(FindingType.URL, f.get(0).type(), text);
            assertEquals("url", f.get(0).source(), text);
        }
    }

    @Test
    void urlMitUncImParameterBleibtGanzeUrlOderWirdZusammengelegtOhneLeck() {
        // der UNC-Pfad hinter "=" liegt ganz in der URL: unveraendert ein URL-Fund
        String text = bs("https://example.invalid/?datei=¦¦srv-beispiel¦freigabe¦a.txt");
        List<Finding> f = scan(text);
        assertEquals(1, f.size());
        assertEquals(text, f.get(0).originalValue());
        assertFalse(pseudonymisiert(text).contains("srv-beispiel"));
    }

    // ---- maskierte Laufwerkspfade (Plan: genau ein Fund, doppelte Backslashes unveraendert)

    @Test
    void maskierterLaufwerkspfadBleibtEinFundUndUnveraendert() {
        String pfad = bs("C:¦¦Tools¦¦BeispielTool¦¦tool.exe");
        Finding f = einziger("Start " + pfad + " jetzt");
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(pfad, f.originalValue());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(6, f.originalValue().chars().filter(c -> c == '\\').count());
        assertEquals("Start [[PFAD_1]] jetzt", pseudonymisiert("Start " + pfad + " jetzt"));
    }

    @Test
    void maskierterUncPfadVierfachBackslashGanzUndOhneKlartext() {
        String pfad = bs("¦¦¦¦srv-beispiel¦¦freigabe¦¦a.txt");
        assertEquals(8, pfad.chars().filter(c -> c == '\\').count());
        Finding f = einziger("{\"pfad\":\"" + pfad + "\"}");
        assertEquals(pfad, f.originalValue());
        String aus = pseudonymisiert("{\"pfad\":\"" + pfad + "\"}");
        assertFalse(aus.contains("srv-beispiel"));
        assertFalse(aus.contains("freigabe"));
        assertFalse(aus.contains("a.txt"));
    }

    // ---- Anfuehrungszeichen mit Leerzeichen

    @Test
    void uncImZitatMitLeerzeichenGanzUndOhneKlartext() {
        String pfad = bs("¦¦srv-beispiel¦Gemeinsame Daten¦Mein Ordner¦a.txt");
        String aus = pseudonymisiert("Starte \"" + pfad + "\" jetzt");
        assertEquals("Starte \"[[PFAD_1]]\" jetzt", aus);
    }

    @Test
    void zweiUncPfadeUnterschiedlicheWerteZweiPlatzhalter() {
        String aus = pseudonymisiert(bs("Von ¦¦srv-beispiel¦a¦x nach ¦¦srv-beispiel¦b¦y fertig"));
        assertEquals("Von [[PFAD_1]] nach [[PFAD_2]] fertig", aus);
    }

    @Test
    void gleicherUncPfadZweimalGleicherPlatzhalter() {
        String pfad = bs("¦¦srv-beispiel¦a¦x");
        assertEquals("[[PFAD_1]] und [[PFAD_1]]", pseudonymisiert(pfad + " und " + pfad));
    }
}
