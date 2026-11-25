package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Der Anlasstext als Ganzes fuer Teil A des Features datum-und-kontextvorrang (T-3, AK-7 in der verkleinerten Form des
 * Nachtrags vom 24.11.2026): Alle Datumsangaben bleiben im Klartext, alle bisherigen Nummernfunde bleiben unveraendert
 * (13 Funde von {@code ALT_DATUM}, danach 9), der Roundtrip ist zeichengleich, und der Weg ueber {@link Pseudonymizer}
 * und {@link Reidentifier} geht Ende zu Ende. Der Text ist {@link SynthetischeDaten#anlassText()} mit erfundenen
 * Nummern gleicher Gestalt.
 *
 * <p>Die Aussage "beide LANR-Stellen tragen denselben Platzhalter" gehoert zu Teil B (Kontextvorrang, zurueckgestellt)
 * und entfaellt; {@link #bekannteGrenze_dieselbeNummerHinterLanrUndInDerStammdatenzeile_zweiPlatzhalter} haelt den
 * heutigen Stand fest. Meldungen nennen nur Zahlen.
 */
class DatumAnlassTest {

    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner NEU = TextScanner.standard();

    private static final List<String> DATEN = List.of(SynthetischeDaten.DATUM_PUNKT,
            SynthetischeDaten.DATUM_LEISTUNG, SynthetischeDaten.DATUM_LEISTUNG_SPAETER,
            SynthetischeDaten.DATUM_GENEHMIGUNG);

    private static boolean istDatumsfund(String text, Finding f) {
        return DatumMessung.istKv(f) && DatumReferenz.istDatumssegment(text, f.start(), f.end());
    }

    /** Keine der Datumsangaben ist (ganz oder teilweise) ein Fund; sie stehen im pseudonymisierten Text im Klartext. */
    @Test
    void anlass_datumsangabenBleibenImKlartext() {
        String text = SynthetischeDaten.anlassText();
        List<Finding> neu = NEU.scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, neu);
        for (String datum : DATEN) {
            int index = text.indexOf(datum);
            while (index >= 0) {
                for (Finding f : neu) {
                    assertFalse(f.start() < index + datum.length() && index < f.end(),
                            "Fund ueber einem Datum an Position " + index);
                }
                index = text.indexOf(datum, index + 1);
            }
            assertTrue(p.text().contains(datum), "Datum im pseudonymisierten Text");
        }
        for (Finding f : neu) {
            assertFalse(istDatumsfund(text, f), "Fund ist ein Datumssegment");
        }
    }

    /** Die Datumsfunde von ALT_DATUM entfallen (vier); alle anderen neun Funde bleiben gleich in allen Feldern. */
    @Test
    void anlass_dreizehnFundeWerdenNeun_alleNummernfundeUnveraendert() {
        String text = SynthetischeDaten.anlassText();
        List<Finding> alt = ALT.scan(text);
        List<Finding> neu = NEU.scan(text);
        assertEquals(13, alt.size(), "Funde von ALT_DATUM");
        assertEquals(9, neu.size(), "Funde des Produkts");
        List<Finding> erwartet = new ArrayList<>();
        int datumsfunde = 0;
        for (Finding f : alt) {
            if (istDatumsfund(text, f)) {
                datumsfunde++;
            } else {
                erwartet.add(f);
            }
        }
        assertEquals(4, datumsfunde, "entfallene Datumsfunde");
        assertTrue(erwartet.equals(neu), "alle anderen Funde gleich in Typ, Bereich, Konfidenz, Quelle");
        DatumFolgeMessung.Ergebnis e = DatumFolgeMessung.messe(text, alt, neu);
        assertEquals(DatumFolgeMessung.Z.KEIN, e.z(), "keine Folgeaenderung: " + e.kurz());
        assertTrue(e.hartErfuellt(), e.kurz());
    }

    /** Ende zu Ende: Pseudonymisieren und Zurueckuebersetzen ergibt zeichengleich den Anlasstext. */
    @Test
    void anlass_roundtripZeichengleich_endeZuEnde() {
        String text = SynthetischeDaten.anlassText();
        List<Finding> neu = NEU.scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, neu);
        ReidentificationResult zurueck = new Reidentifier().restore(p.text(), p.mapping());
        assertTrue(text.equals(zurueck.text()), "Roundtrip zeichengleich");
        assertTrue(zurueck.unresolved().isEmpty(), "keine offenen Platzhalter");
        assertFalse(text.equals(p.text()), "der Text wurde pseudonymisiert");
        for (Finding f : neu) {
            assertFalse(p.text().contains(f.originalValue()), "Fundwert im pseudonymisierten Text");
        }
        for (String nummer : List.of(SynthetischeKvNummern.BSNR_BEKANNT, SynthetischeKvNummern.BSNR_BEKANNT_ZWEI,
                SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN)) {
            assertFalse(p.text().contains(nummer), "Nummer im pseudonymisierten Text");
        }
    }

    /** Ohne Fundliste bleibt der Text unveraendert (nichts wird ohne Uebergabe ersetzt). */
    @Test
    void anlass_ohneFunde_textBleibtUnveraendert() {
        String text = SynthetischeDaten.anlassText();
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, List.of());
        assertTrue(text.equals(p.text()), "unveraendert");
    }

    /**
     * Bekannte Grenze, Verweis auf den zurueckgestellten Teil B (Kontextvorrang): Die Nummer hinter {@code LANR} in
     * Zeile 2 wird als BSNR erkannt, dieselbe Nummer in der Stammdatenzeile ({@code Stammdaten LANR ...}) als LANR;
     * es entstehen zwei Platzhalter fuer denselben Wert. Teil A aendert daran nichts; das Soll "ein gemeinsamer
     * Platzhalter" gehoert zu Teil B.
     */
    @Test
    void bekannteGrenze_dieselbeNummerHinterLanrUndInDerStammdatenzeile_zweiPlatzhalter() {
        String lanrForm = SynthetischeKvNummern.BSNR_MARKER;
        String text = SynthetischeDaten.anlassText();
        int zeile2 = text.indexOf("\nLANR " + lanrForm) + "\nLANR ".length();
        int stammdaten = text.indexOf("Stammdaten LANR " + lanrForm) + "Stammdaten LANR ".length();
        assertTrue(zeile2 >= "\nLANR ".length() && stammdaten >= "Stammdaten LANR ".length(), "Stellen gefunden");

        List<Finding> neu = NEU.scan(text);
        Finding inZeile2 = findeAn(neu, zeile2);
        Finding inStammdaten = findeAn(neu, stammdaten);
        assertEquals(FindingType.BSNR, inZeile2.type(), "Typ in Zeile 2");
        assertEquals(FindingType.LANR, inStammdaten.type(), "Typ in der Stammdatenzeile");
        assertTrue(inZeile2.originalValue().equals(inStammdaten.originalValue()), "derselbe Wert");

        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, neu);
        assertTrue(p.text().contains("\nLANR [[BSNR_"), "Zeile 2 traegt einen BSNR-Platzhalter");
        assertTrue(p.text().contains("Stammdaten LANR [[LANR_"), "Stammdatenzeile traegt einen LANR-Platzhalter");
        assertFalse(p.text().contains(lanrForm), "der Wert steht nirgends im Klartext");
    }

    private static Finding findeAn(List<Finding> funde, int start) {
        for (Finding f : funde) {
            if (f.start() == start) {
                return f;
            }
        }
        throw new AssertionError("kein Fund an der Stelle " + start);
    }
}
