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
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
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
    void kvKontextLuecken_ak21_ikNummerKommaLanr_imGesamtergebnisGenauEinFundTypIkSicher() {
        // Der LANR-Erkenner liefert fuer "N, LANR" einen Kandidaten ueber dieselbe Nummer (die IK-Nummer hat als LANR
        // keine gueltige Pruefziffer: 0,50, Quelle Pruefziffer; mit gueltiger Pruefziffer waere es 0,60). Der
        // IK-Kandidat (direkt hinter "IK", 0,92) gewinnt die Konfliktregel: genau ein Fund, Typ IK, Bereich genau
        // die Nummer.
        String vor = "IK ";
        String text = vor + IK_GUELTIG + ", LANR";
        List<Finding> kandidatenLanr = new LanrRecognizer().detect(text);
        assertEquals(1, kandidatenLanr.size(), "LANR-Kandidat auf Erkennerebene");
        assertEquals(0.50, kandidatenLanr.get(0).confidence());
        assertEquals(vor.length(), kandidatenLanr.get(0).start());
        assertEquals(vor.length() + IK_GUELTIG.length(), kandidatenLanr.get(0).end());

        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(FindingType.IK, f.type());
        assertEquals(0.92, f.confidence());
        assertEquals(ConfidenceLevel.SICHER, f.level());
        assertEquals(vor.length(), f.start());
        assertEquals(vor.length() + IK_GUELTIG.length(), f.end());
        assertTrue(IK_GUELTIG.equals(f.originalValue()), "IK-Wert");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals("IK [[IK_1]], LANR", ergebnis.text());
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void kvKontextLuecken_ak21_ikNummerKommaLanrMitLanrKandidat060_typwechselZuLanrPositionAbgedeckt() {
        // Die Nummer hat als LANR eine gueltige Pruefziffer (LANR-Kandidat 0,60), als IK nicht (IK-Kandidat direkt
        // hinter "IK" mit ungueltiger Pruefziffer: 0,50). Beide Kandidaten haben denselben Bereich; nach der
        // Staerke des ConflictResolver (Konfidenz absteigend) gilt der IK-Kandidat als im LANR-Kandidaten enthalten.
        // Ergebnis: genau ein Fund, Typ LANR (Typwechsel gegenueber dem Fall mit gueltiger IK-Nummer), 0,60
        // "fraglich"; die Position ist in jedem Fall abgedeckt. Ein Typwechsel ist im Plan (Kap. 13, Hinweis 10)
        // nicht ausdruecklich beschrieben; die Schutzgarantie (Position abgedeckt) bleibt erhalten.
        String vor = "IK ";
        String text = vor + LANR_GUELTIG + ", LANR";
        List<Finding> lanr = new LanrRecognizer().detect(text);
        assertEquals(1, lanr.size(), "LANR-Kandidat auf Erkennerebene");
        assertEquals(0.60, lanr.get(0).confidence());
        List<Finding> ik = new IkRecognizer().detect(text);
        assertEquals(1, ik.size(), "IK-Kandidat auf Erkennerebene");
        assertEquals(0.50, ik.get(0).confidence());

        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(FindingType.LANR, f.type());
        assertEquals(0.60, f.confidence());
        assertEquals(ConfidenceLevel.FRAGLICH, f.level());
        assertEquals(vor.length(), f.start());
        assertEquals(vor.length() + LANR_GUELTIG.length(), f.end());
        assertTrue(LANR_GUELTIG.equals(f.originalValue()), "Wert");
        PseudonymizationResult ergebnis = PSEUDONYMIZER.pseudonymize(text, funde);
        assertEquals("IK [[LANR_1]], LANR", ergebnis.text());
        assertRoundtrip(text, ergebnis);
    }

    @Test
    void bekannteGrenze_pfadFund080UndIk14gFund050_werdenZuEinemFund050ZusammengelegtPositionAbgedeckt() {
        // Restform 6 (Plan Kap. 11): gesperrtes "i k" mit Trennern, dann ";.Nr:/" und eine gruppierte IK-Nummer.
        // PathRecognizer: ".Nr:/" plus erste Gruppe, 0,80. IkRecognizer: die ganze Nummer, 0,50 (14g, von "i k"
        // erschlossen). Beide ueberlappen teilweise, der ConflictResolver bildet die Vereinigung mit der Konfidenz
        // des schwaechsten maximalen Kandidaten (0,50), Typ des staerkeren (PFAD). Stufe "fraglich", Position
        // abgedeckt.
        String gruppiert = IK_GUELTIG.substring(0, 3) + " " + IK_GUELTIG.substring(3, 6) + " "
            + IK_GUELTIG.substring(6);
        String vor = "i k ;.Nr:/";
        String text = vor + gruppiert + " ;";
        List<Finding> pfad = new PathRecognizer().detect(text);
        assertEquals(1, pfad.size(), "Pfad-Kandidat");
        assertEquals(0.80, pfad.get(0).confidence());
        List<Finding> ik = new IkRecognizer().detect(text);
        assertEquals(1, ik.size(), "IK-Kandidat");
        assertEquals(0.50, ik.get(0).confidence());

        List<Finding> funde = scan(text);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(0.50, f.confidence());
        assertEquals(ConfidenceLevel.FRAGLICH, f.level());
        assertTrue(f.start() <= pfad.get(0).start(), "Pfad-Anfang abgedeckt");
        assertEquals(vor.length() + gruppiert.length(), f.end(), "Ende der Nummer abgedeckt");
    }

    @Test
    void kvKontextLuecken_ak27_ikGesperrtUndNummerKommaIk_fundVorhandenUndOhneUebergabeBleibtDerTextUnveraendert() {
        String[] texte = {"I K " + IK_GUELTIG, IK_GUELTIG + ", IK", "L A N R " + LANR_GUELTIG,
            LANR_GUELTIG + ", LANR"};
        for (int i = 0; i < texte.length; i++) {
            String text = texte[i];
            List<Finding> funde = scan(text);
            assertEquals(1, funde.size(), "Fund in Text " + i);
            Finding f = funde.get(0);
            boolean ik = i < 2;
            assertEquals(ik ? FindingType.IK : FindingType.LANR, f.type(), "Typ in Text " + i);
            // I K: 0,50 und Komma vor dem Kontextwort: 0,60 sind fraglich; gesperrtes LANR: 0,80 wahrscheinlich
            ConfidenceLevel erwartet = i == 2 ? ConfidenceLevel.WAHRSCHEINLICH : ConfidenceLevel.FRAGLICH;
            assertEquals(erwartet, f.level(), "Stufe in Text " + i);

            // Mensch entscheidet: ohne uebergebenen Fund wird nichts ersetzt
            PseudonymizationResult ohne = PSEUDONYMIZER.pseudonymize(text, List.of());
            assertTrue(text.equals(ohne.text()), "Text ohne Fund veraendert " + i);
            // mit dem Fund wird ersetzt, und die Rueckuebersetzung stellt den Text her
            PseudonymizationResult mit = PSEUDONYMIZER.pseudonymize(text, funde);
            assertFalse(text.equals(mit.text()), "Text mit Fund nicht ersetzt " + i);
            assertFalse(mit.text().contains(f.originalValue()), "Nummer im Ausgabetext " + i);
            assertRoundtrip(text, mit);
        }
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
