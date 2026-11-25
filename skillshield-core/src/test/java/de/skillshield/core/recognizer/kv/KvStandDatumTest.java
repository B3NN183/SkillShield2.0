package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMengen;
import de.skillshield.core.SynthetischeDaten;
import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prueft die vierte eingefrorene Kopie ({@link KvNumberScannerStandDatum}, Huellen in {@link KvStandDatum}; Feature
 * datum-und-kontextvorrang, T-1): Auf allen Textmengen liefern die Huellen je Art dieselbe Fundliste wie das Produkt
 * (alle Felder, gleiche Reihenfolge) und {@code ALT_DATUM} dieselbe wie {@code TextScanner.standard()}. Solange das
 * Produkt unveraendert ist, gilt das fuer jeden Text; sobald Teil A im Produkt steht (T-2), darf es auf den Funden
 * ueber Datumssegmente abweichen und diese Tests werden nach dem Plan angepasst. Die Siegel halten die Ausgabe der
 * Kopie je Textmenge fest (hexadezimal); aendert sich die Kopie oder ein Generator, schlagen sie an. Meldungen nennen
 * nur Zahlen und Namen der Textmengen.
 */
class KvStandDatumTest {

    /** Siegel der Funde der vier Huellen je Textmenge in der Reihenfolge von {@link DatumMengen#NAMEN}. */
    private static final int[] SIEGEL_KOPIE = {0x6E238C11, 0xED8C05E0, 0x6E26869F, 0x4F003658, 0xF77C3DCD,
        0x7BEFA653, 0x79C2F818, 0x88CF49C6, 0x109670AA, 0xED41AF2E, 0x041095AC, 0xE55DBB6E, 0x2693D516,
        0x260D5468};

    /** Siegel der vollstaendigen Ausgabe von {@code ALT_DATUM} (mit Originalwert) je Textmenge. */
    private static final int[] SIEGEL_ALT = {0x47C9C8DC, 0x3D3D266B, 0x566C8D92, 0x77604F38, 0x9C0A5808,
        0x565BCBFC, 0x3C073A48, 0x6E0696F5, 0xF7974E7E, 0xEE15DEDF, 0x8F878047, 0xAE18158C, 0x3C7B17DE,
        0x22D023FE};

    private static final int SIEGEL_START = 17;

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;

    private static final List<Recognizer> KOPIEN = KvStandDatum.alle();
    private static final List<Recognizer> PRODUKT = List.of(new LanrRecognizer(), new IkRecognizer(),
            new KvnrRecognizer(), new BsnrRecognizer());
    private static final List<String> ARTEN = List.of("LANR", "IK", "KVNR", "BSNR");

    private static int hash(int h, List<Finding> funde, boolean mitOriginalwert) {
        int ergebnis = 31 * h + funde.size();
        for (Finding f : funde) {
            long bits = Double.doubleToLongBits(f.confidence());
            ergebnis = 31 * ergebnis + f.start();
            ergebnis = 31 * ergebnis + f.end();
            ergebnis = 31 * ergebnis + f.type().name().hashCode();
            ergebnis = 31 * ergebnis + (int) (bits ^ (bits >>> 32));
            ergebnis = 31 * ergebnis + f.source().hashCode();
            if (mitOriginalwert) {
                ergebnis = 31 * ergebnis + f.originalValue().hashCode();
            }
        }
        return ergebnis;
    }

    /** Messwerte einer Textmenge. */
    private static final class Messung {
        int siegelKopie = SIEGEL_START;
        int siegelAlt = SIEGEL_START;
        final int[] ungleichKopieProdukt = new int[4];
        int ungleichAltNeu;
        int kopieFunde;
        int altFunde;
    }

    private static List<Messung> messungen;

    private static synchronized List<Messung> messungen() {
        if (messungen == null) {
            List<Messung> alle = new ArrayList<>();
            for (List<String> texte : DatumMengen.mengen()) {
                Messung m = new Messung();
                for (String text : texte) {
                    for (int a = 0; a < 4; a++) {
                        List<Finding> kopie = KOPIEN.get(a).detect(text);
                        m.siegelKopie = hash(m.siegelKopie, kopie, false);
                        m.kopieFunde += kopie.size();
                        m.ungleichKopieProdukt[a] += kopie.equals(PRODUKT.get(a).detect(text)) ? 0 : 1;
                    }
                    List<Finding> alt = ALT.scan(text);
                    m.siegelAlt = hash(m.siegelAlt, alt, true);
                    m.altFunde += alt.size();
                    m.ungleichAltNeu += alt.equals(NEU.scan(text)) ? 0 : 1;
                }
                alle.add(m);
            }
            messungen = List.copyOf(alle);
        }
        return messungen;
    }

    /** Die Huellen der Kopie liefern auf allen Textmengen dieselben Funde wie die Produkterkenner (alle Felder). */
    @Test
    void kopie_gleichProdukt_aufAllenMengenJeArt() {
        List<Messung> m = messungen();
        for (int i = 0; i < m.size(); i++) {
            for (int a = 0; a < 4; a++) {
                assertEquals(0, m.get(i).ungleichKopieProdukt[a],
                        DatumMengen.NAMEN.get(i) + " " + ARTEN.get(a) + ": Texte mit abweichender Fundliste");
            }
        }
    }

    /** {@code ALT_DATUM} liefert auf allen Textmengen dieselben Funde wie {@code TextScanner.standard()}. */
    @Test
    void altDatum_gleichStandard_aufAllenMengen() {
        List<Messung> m = messungen();
        for (int i = 0; i < m.size(); i++) {
            assertEquals(0, m.get(i).ungleichAltNeu, DatumMengen.NAMEN.get(i) + ": Texte mit abweichender Fundliste");
        }
    }

    /** Siegel: Die Ausgabe der Kopie (Erkennerebene, ohne Originalwert) je Textmenge ist festgehalten. */
    @Test
    void siegel_kopie_jeMenge() {
        List<Messung> m = messungen();
        StringBuilder ist = new StringBuilder();
        for (int i = 0; i < m.size(); i++) {
            ist.append(i).append(": 0x").append(String.format("%08X", m.get(i).siegelKopie)).append("; ");
        }
        for (int i = 0; i < m.size(); i++) {
            assertEquals(SIEGEL_KOPIE[i], m.get(i).siegelKopie, DatumMengen.NAMEN.get(i) + ": Siegel der Kopie; Ist: "
                    + ist);
        }
    }

    /** Siegel: Die vollstaendige Ausgabe von {@code ALT_DATUM} je Textmenge ist festgehalten. */
    @Test
    void siegel_altDatum_jeMenge() {
        List<Messung> m = messungen();
        StringBuilder ist = new StringBuilder();
        for (int i = 0; i < m.size(); i++) {
            ist.append(i).append(": 0x").append(String.format("%08X", m.get(i).siegelAlt)).append("; ");
        }
        for (int i = 0; i < m.size(); i++) {
            assertEquals(SIEGEL_ALT[i], m.get(i).siegelAlt, DatumMengen.NAMEN.get(i) + ": Siegel von ALT_DATUM; Ist: "
                    + ist);
        }
    }

    /**
     * Die Siegel laufen nicht ins Leere: auf jeder Menge hat {@code ALT_DATUM} Funde; die Kopie hat sie auf jeder
     * Menge ausser Teil 1 (der Generator erzeugt dort keine KV-Texte).
     */
    @Test
    void siegel_mengenHabenFunde() {
        List<Messung> m = messungen();
        for (int i = 0; i < m.size(); i++) {
            assertTrue(m.get(i).altFunde > 0, DatumMengen.NAMEN.get(i) + ": keine Funde von ALT_DATUM");
            assertEquals(i == 0, m.get(i).kopieFunde == 0, DatumMengen.NAMEN.get(i) + ": Funde der Kopie");
        }
    }

    /** Die Huellen haben dieselben Werte fuer {@code nachrangig()} und {@code restQuelle()} wie das Produkt. */
    @Test
    void huellen_nachrangigUndRestQuelle_wieDasProdukt() {
        for (int a = 0; a < 4; a++) {
            assertEquals(PRODUKT.get(a).nachrangig(), KOPIEN.get(a).nachrangig(), ARTEN.get(a) + ": nachrangig");
            assertEquals(PRODUKT.get(a).restQuelle(), KOPIEN.get(a).restQuelle(), ARTEN.get(a) + ": restQuelle");
        }
        assertEquals(4, KvStandDatum.alle().size(), "vier Huellen");
    }

    /** {@code ALT_DATUM} hat die Erkenner von {@code standard()} in derselben Reihenfolge, mit Huellen. */
    @Test
    void altDatum_aufbauWieStandard() {
        List<Recognizer> neu = NEU.recognizers();
        List<Recognizer> alt = ALT.recognizers();
        assertEquals(neu.size(), alt.size(), "Zahl der Erkenner");
        for (int i = 0; i < 4; i++) {
            assertEquals(neu.get(i).getClass(), alt.get(i).getClass(), "allgemeiner Erkenner " + i);
        }
        for (int i = 4; i < neu.size(); i++) {
            assertFalse(alt.get(i).getClass().equals(neu.get(i).getClass()), "Huelle statt Produkt " + i);
            assertEquals(neu.get(i).nachrangig(), alt.get(i).nachrangig(), "nachrangig " + i);
        }
    }

    /** Die Simulation ohne jede Auslassung ist {@code ALT_DATUM}; ohne Praedikat ist sie nicht moeglich. */
    @Test
    void simuliert_ohneAuslassung_gleichAltDatum_nullIstFehler() {
        TextScanner leer = KvStandDatum.simuliert((text, f) -> false);
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT + ", LANR " + SynthetischeKvNummern.LANR_GUELTIG;
        assertTrue(ALT.scan(text).equals(leer.scan(text)), "keine Auslassung");
        assertEquals("entfaellt",
                assertThrows(NullPointerException.class, () -> KvStandDatum.simuliert(null)).getMessage(),
                "Meldung ohne Text");
    }

    /** Die Kopie ist deterministisch und liefert fuer {@code null} dieselbe Ausnahme wie der Produktscanner. */
    @Test
    void kopie_deterministisch_nullText() {
        String text = "KVNR: " + SynthetischeDaten.DATUM_PUNKT + " LANR " + SynthetischeKvNummern.LANR_GUELTIG;
        for (Recognizer r : KOPIEN) {
            assertTrue(r.detect(text).equals(r.detect(text)), "zweimal gleich");
        }
        assertThrows(NullPointerException.class, () -> KvNumberScannerStandDatum.detect(KvArt.LANR, null));
    }
}
