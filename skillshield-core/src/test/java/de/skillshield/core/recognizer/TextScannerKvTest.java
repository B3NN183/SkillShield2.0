package de.skillshield.core.recognizer;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Einbindung der LANR- und IK-Erkenner in {@code TextScanner.standard()} (Feature kv-nummern-erkennung, T-6;
 * AK-5, AK-17, AK-18, AK-19, AK-27). Alle Nummern stammen aus {@code SynthetischeKvNummern}; Wertvergleiche laufen
 * ueber {@code assertTrue(equals)}, damit keine Nummer in einer Fehlermeldung landet.
 */
class TextScannerKvTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    private static List<Finding> scan(String text) {
        List<Finding> funde = TextScanner.standard().scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static void assertRoundtrip(String text, PseudonymizationResult ergebnis) {
        ReidentificationResult zurueck = REIDENTIFIER.restore(ergebnis.text(), ergebnis.mapping());
        assertTrue(zurueck.unresolved().isEmpty(), "unaufgeloeste Platzhalter");
        assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich");
    }

    @Test
    void ak5_pseudonymisierungIkUndLanrMitGlobalerNummerierung() {
        String text = "IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG;
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(FindingType.IK, funde.get(0).type());
        assertEquals(FindingType.LANR, funde.get(1).type());
        assertTrue(IK_GUELTIG.equals(funde.get(0).originalValue()), "IK-Wert");
        assertTrue(LANR_GUELTIG.equals(funde.get(1).originalValue()), "LANR-Wert");

        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals("IK [[IK_1]], LANR [[LANR_2]]", ergebnis.text());
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak17_ikInUrlLiefertGenauEinenUrlFund() {
        String url = "https://example.invalid/praxis?ik=" + IK_GUELTIG;
        List<Finding> funde = scan(url);
        assertEquals(1, funde.size());
        assertEquals(FindingType.URL, funde.get(0).type());
        assertEquals(0, funde.get(0).start());
        assertEquals(url.length(), funde.get(0).end());
        assertTrue(url.equals(funde.get(0).originalValue()), "URL-Wert");
    }

    @Test
    void ak17_ikInPfadLiefertGenauEinenPfadFund() {
        String pfad = bs("C:\u00A6Praxis\u00A6IK-" + IK_GUELTIG + "\u00A6x.txt");
        List<Finding> funde = scan(pfad);
        assertEquals(1, funde.size());
        assertEquals(FindingType.PFAD, funde.get(0).type());
        assertEquals(0, funde.get(0).start());
        assertEquals(pfad.length(), funde.get(0).end());
        assertTrue(pfad.equals(funde.get(0).originalValue()), "Pfad-Wert");
    }

    @Test
    void ak17_lanrAlsLokalteilEinerEmailLiefertGenauEinenEmailFundOhneKontextwort() {
        String kontext = "LANR ";
        String email = LANR_GUELTIG + "@example.invalid";
        List<Finding> funde = scan(kontext + email);
        assertEquals(1, funde.size());
        assertEquals(FindingType.EMAIL, funde.get(0).type());
        assertEquals(kontext.length(), funde.get(0).start());
        assertEquals(kontext.length() + email.length(), funde.get(0).end());
        assertTrue(email.equals(funde.get(0).originalValue()), "E-Mail-Wert");
        // Das Kontextwort bleibt Klartext
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(kontext + email, funde);
        assertEquals("LANR [[EMAIL_1]]", ergebnis.text());
    }

    @Test
    void ak18_ikMitDokumentationsIpLiefertGenauEinenIpFundUndKeinenZiffernrest() {
        // Rechenweg: "198.51.100.1" hat neun Ziffern (Gruppen 3-2-3-1), davor steht das Kontextwort IK.
        // Der IK-Erkenner liefert damit einen Kandidaten ueber genau denselben Bereich wie der IPv4-Erkenner. Die
        // Pruefziffer stimmt nicht: Stellen 3 bis 8 sind 8 5 1 1 0 0, Gewichte 2,1,2,1,2,1: 16 (Quersumme 7), 5, 2,
        // 1, 0, 0 = 15, Rest 5, Stelle 9 ist aber 1. Also Konfidenz 0.50 (fraglich) gegen 0.85 der IPv4: Bei
        // gleichem Bereich gewinnt der staerkere Kandidat, es bleibt genau ein Fund, Typ IP (Plan Kap. 3.3, OF-4).
        String praefix = "IK ";
        String text = praefix + IK_FORM_DOKU_IP;
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(FindingType.IP, f.type());
        assertEquals(praefix.length(), f.start());
        assertEquals(text.length(), f.end());
        assertTrue(IK_FORM_DOKU_IP.equals(f.originalValue()), "IP-Wert");

        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals("IK [[IP_1]]", ergebnis.text());
        assertFalse(ergebnis.text().contains("198"), "Ziffernrest");
        assertFalse(ergebnis.text().contains("100"), "Ziffernrest");
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void ak19_standardScannerLiefertAlleSechsFunde() {
        String text = "Mail erika.beispiel@example.invalid\n"
                + "Server 192.0.2.10\n"
                + "Seite https://example.invalid/x\n"
                + "Datei " + bs("C:\u00A6Tools\u00A6a.txt") + "\n"
                + "IK " + IK_GUELTIG + "\n"
                + "LANR " + LANR_GUELTIG + "\n";
        List<Finding> funde = scan(text);
        assertEquals(List.of(FindingType.EMAIL, FindingType.IP, FindingType.URL, FindingType.PFAD, FindingType.IK,
                FindingType.LANR), funde.stream().map(Finding::type).toList());

        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertRoundtrip(text, ergebnis);
        assertEquals(6, ergebnis.mapping().size());
    }

    @Test
    void ak27_fraglicherFundBleibtImScanUndOhneUebergabeBleibtDerTextUnveraendert() {
        String text = "LANR " + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        assertEquals(FindingType.LANR, funde.get(0).type());
        assertEquals(ConfidenceLevel.FRAGLICH, funde.get(0).level());
        assertTrue(LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN.equals(funde.get(0).originalValue()), "LANR-Wert");

        // Mensch entscheidet: ohne uebergebenen Fund wird nichts ersetzt
        PseudonymizationResult ohne = PSEUDONYMIZER.pseudonymize(text, List.of());
        assertTrue(text.equals(ohne.text()), "Text ohne Fund veraendert");
        // mit dem Fund wird ersetzt
        PseudonymizationResult mit = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals("LANR [[LANR_1]]", mit.text());
        assertRoundtrip(text, mit);
    }
}
