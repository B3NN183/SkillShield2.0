package de.skillshield.core;

import static de.skillshield.core.DatumKontextvorrangDifferenzTest.TEIL_A;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMessung.Messung;
import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Prognose fuer Teil A des Features datum-und-kontextvorrang (T-1, Architektur Kap. 7 und 8.3, verkleinert nach dem
 * Nachtrag vom 24.11.2026): Mit der eingefrorenen Kopie und der unabhaengigen {@link DatumReferenz} wird auf jeder
 * Textmenge gezaehlt, wie viele Funde Teil A kuenftig entfallen laesst, und die Zahlen werden als exakte Werte
 * festgehalten. Daraus folgt vor jeder Produktaenderung, welche Mengen-Tests anschlagen werden.
 *
 * <p><b>Ergebnis (gemessen in T-1):</b> Auf keiner der 14 Textmengen aus {@link DatumMengen} steht ein Fund, dessen
 * Bereich ein Datumssegment ist (alle Zahlen 0). Die Mengen-Tests gegen aeltere Kopien ({@code KvStandTrenner...},
 * {@code KvStandLuecken...}, {@code KvVorgaenger...}, {@code KvDifferenzTest}, {@code Kvnr...Bestand...},
 * {@code Bsnr...Bestand...}, {@code KvArtBestandTest}, {@code RoundtripKorpusTest}, {@code RoundtripFuzzTest} in den
 * Teilen, die diese Mengen benutzen) schlagen durch Teil A deshalb auf diesen Mengen nicht an. Betroffen koennen nur
 * Tests sein, die ihre Texte im Test selbst zufaellig bilden (Eigenschafts- und Fehlalarmtests); sie sind mit dieser
 * Methode nicht erreichbar und werden in T-2 nach der Umstellregel behandelt (Bericht von T-1).
 *
 * <p>Ausserdem halten die Tests die heutigen Werte fuer Datumsvektoren hinter den Kontextwoertern aller Arten fest
 * (Ausgangswerte fuer AK-1 bis AK-5 und AK-10) und messen den Anlasstext. Meldungen nennen nur Zahlen.
 */
class DatumKontextvorrangPrognoseTest {

    /** Zahl der Texte je Textmenge in der Reihenfolge von {@link DatumMengen#NAMEN} (Istwerte). */
    private static final int[] TEXTE = {1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 327, 101, 4000, 143, 139,
        98};

    /** Funde der Kopie je Menge (alle Arten zusammen, Istwerte); die Pruefung verlangt mindestens 70 Prozent. */
    private static final int[] KOPIE_FUNDE = {0, 1065, 7222, 8559, 5864, 11204, 6616, 10237, 271, 57, 7986, 131, 56,
        109};

    /** Entfallende Funde auf Scannerebene je Menge (Klasse A): in T-1 auf allen Mengen 0. */
    static final int[] ENTFALLEN_SCANNER = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /** Entfallende Funde auf Erkennerebene je Menge und Art (LANR, IK, KVNR, BSNR): in T-1 auf allen Mengen 0. */
    private static final int[][] ENTFALLEN_ERKENNER = {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0},
        {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0},
        {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};

    private static final List<String> KONTEXTWOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer", "Arztnummer");
    private static final List<String> TRENNER = List.of(" ", ": ");

    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner SIMULIERT = KvStandDatum.simuliert(
            (text, f) -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));

    // ------------------------------------------------------------------ Textmengen

    /** Die Messwerte stimmen: Zahl der Texte exakt, Funde der Kopie mindestens 70 Prozent des Istwerts. */
    @Test
    void mengen_zahlenUndFunde() {
        List<Messung> m = DatumMessung.messungen();
        assertEquals(TEXTE.length, m.size(), "Zahl der Mengen");
        for (int i = 0; i < m.size(); i++) {
            int funde = 0;
            for (int a = 0; a < 4; a++) {
                funde += m.get(i).kopieFunde[a];
            }
            assertEquals(TEXTE[i], m.get(i).texte, DatumMengen.NAMEN.get(i) + ": Zahl der Texte");
            // Istwert in der Konstante KOPIE_FUNDE; die Pruefung verlangt nur 70 Prozent davon (Menge 1 hat keine).
            assertTrue(funde >= (int) (KOPIE_FUNDE[i] * 0.7), DatumMengen.NAMEN.get(i) + ": Funde der Kopie " + funde);
        }
    }

    /** Erkennerebene: Zahl der Funde, deren Bereich ein Datumssegment ist, je Menge und Art (in T-1 alle 0). */
    @Test
    void prognose_erkennerebene_entfallendeFundeJeMengeUndArt() {
        List<Messung> m = DatumMessung.messungen();
        for (int i = 0; i < m.size(); i++) {
            for (int a = 0; a < 4; a++) {
                assertEquals(ENTFALLEN_ERKENNER[i][a], m.get(i).datumFunde[a],
                        DatumMengen.NAMEN.get(i) + " " + DatumMessung.ARTEN.get(a) + ": kuenftig entfallende Funde");
                assertEquals(0, m.get(i).zeitraumFunde[a],
                        DatumMengen.NAMEN.get(i) + " " + DatumMessung.ARTEN.get(a) + ": davon Zeitraeume");
            }
        }
    }

    /** Scannerebene: Simulation von Teil A gegen {@code ALT_DATUM}: entfallende Funde je Menge, sonst nichts. */
    @Test
    void prognose_scannerebene_entfallendeFundeJeMenge_keineFolgeaenderung() {
        List<Messung> m = DatumMessung.messungen();
        for (int i = 0; i < m.size(); i++) {
            Vergleich v = m.get(i).simulation;
            assertEquals(ENTFALLEN_SCANNER[i], v.datumEntfallen(), DatumMengen.NAMEN.get(i) + ": " + v.kurz());
            assertEquals(0, v.sonstigeVerluste() + v.neueFunde() + v.abdeckung(),
                    DatumMengen.NAMEN.get(i) + ": Folgeaenderungen " + v.kurz());
            assertEquals(ENTFALLEN_SCANNER[i] > 0, m.get(i).texteMitAenderung > 0,
                    DatumMengen.NAMEN.get(i) + ": Texte mit Aenderung " + m.get(i).texteMitAenderung);
        }
    }

    /** Summe: Auf den Textmengen aendert Teil A keinen einzigen Fund; die Mengen-Tests aus Kap. 8.3 bleiben gruen. */
    @Test
    void prognose_summe_keineMengeBetroffen() {
        int summe = 0;
        for (int e : ENTFALLEN_SCANNER) {
            summe += e;
        }
        assertEquals(0, summe, "Summe der entfallenden Funde auf allen Mengen");
    }

    // ------------------------------------------------------------------ Datumsvektoren

    /** Heutige und kuenftige Funde fuer eine Liste von Vektoren hinter allen Kontextwoertern und Trennern. */
    private record Vektoren(int texte, int alteFunde, int entfallen, int verbleibend) {
    }

    private static Vektoren messeVektoren(List<String> daten) {
        int texte = 0;
        int alt = 0;
        int entfallen = 0;
        int verbleibend = 0;
        for (String kw : KONTEXTWOERTER) {
            for (String t : TRENNER) {
                for (String d : daten) {
                    String text = kw + t + d;
                    List<Finding> a = ALT.scan(text);
                    List<Finding> s = SIMULIERT.scan(text);
                    Vergleich v = DatumMessung.vergleiche(text, a, s);
                    assertEquals(0, v.sonstigeVerluste() + v.neueFunde() + v.abdeckung(), v.kurz());
                    texte++;
                    alt += a.size();
                    entfallen += v.datumEntfallen();
                    verbleibend += s.size();
                }
            }
        }
        return new Vektoren(texte, alt, entfallen, verbleibend);
    }

    private static void pruefeVektoren(String name, List<String> daten, int alteFunde, int entfallen) {
        Vektoren v = messeVektoren(daten);
        assertEquals(daten.size() * KONTEXTWOERTER.size() * TRENNER.size(), v.texte(), name + ": Zahl der Texte");
        assertEquals(alteFunde, v.alteFunde(), name + ": heutige Funde");
        assertEquals(entfallen, v.entfallen(), name + ": kuenftig entfallende Funde");
        assertEquals(alteFunde - entfallen, v.verbleibend(), name + ": kuenftig verbleibende Funde");
    }

    /** AK-2: Jede Datumsform hat heute genau einen Fund je Kontext (0,40); Teil A laesst alle entfallen. */
    @Test
    void ausgangswerte_ak2_gueltigeFormen_einFundJeKontext_allesEntfaellt() {
        int n = SynthetischeDaten.GUELTIGE_FORMEN.size() * KONTEXTWOERTER.size() * TRENNER.size();
        pruefeVektoren("AK-2", SynthetischeDaten.GUELTIGE_FORMEN, n, n);
        for (String kw : KONTEXTWOERTER) {
            for (String d : SynthetischeDaten.GUELTIGE_FORMEN) {
                List<Finding> alt = ALT.scan(kw + " " + d);
                assertEquals(1, alt.size(), "ein Fund");
                assertEquals(0.40, alt.get(0).confidence(), 1e-9, "Konfidenz 0,40");
            }
        }
    }

    /** AK-3, AK-4, AK-5: Alles, was kein Datum ist, hat heute einen Fund und behaelt ihn. */
    @Test
    void ausgangswerte_ak3bis5_keinDatum_fundBleibt() {
        int je = KONTEXTWOERTER.size() * TRENNER.size();
        List<String> keine = new ArrayList<>(SynthetischeDaten.KEINE_DATUMSFORM);
        pruefeVektoren("AK-3", keine, keine.size() * je, 0);
        List<String> ungueltig = new ArrayList<>(SynthetischeDaten.KALENDERUNGUELTIG);
        ungueltig.add(SynthetischeDaten.SCHALTTAG_1900);
        ungueltig.addAll(SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG);
        ungueltig.add(SynthetischeDaten.SCHALTTAG_ZWEISTELLIG_NULL);
        pruefeVektoren("AK-4/5 ungueltig", ungueltig, ungueltig.size() * je, 0);
        List<String> gueltig = new ArrayList<>(SynthetischeDaten.SCHALTTAGE_GUELTIG);
        gueltig.addAll(SynthetischeDaten.JAHRESGRENZEN_GUELTIG);
        pruefeVektoren("AK-4/5 gueltig", gueltig, gueltig.size() * je, gueltig.size() * je);
    }

    /**
     * AK-10 und D-5 (bekannte Grenzen): Ein Datum mit weiterem Block ueber einen Gruppentrenner bleibt Fund; das gilt
     * auch fuer {@code 14.03.1958 08:30}. Der Doppelpunkt beendet die Kette erst hinter dem Block {@code 08}, das
     * Segment ist {@code 14.03.1958 08} (zehn Ziffern) und kein Datum. AK-10 verlangt hier "kein Fund"; mit D-5 und
     * der Regel "Datum zuerst: nein" (Architektur Kap. 1.2) ist das nicht erfuellbar. Offener Punkt fuer T-2.
     */
    @Test
    void ausgangswerte_ak10_datumMitWeiteremBlock_auchMitDoppelpunkt_fundBleibt() {
        int je = KONTEXTWOERTER.size() * TRENNER.size();
        List<String> mit = new ArrayList<>(SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK);
        mit.add(SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT);
        pruefeVektoren("D-5", mit, mit.size() * je, 0);
        String text = "BSNR " + SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT;
        List<Finding> alt = ALT.scan(text);
        assertEquals(1, alt.size(), "ein Fund");
        assertEquals(13, alt.get(0).end() - alt.get(0).start(), "das Segment ist Datum, Leerzeichen und zwei Ziffern");
    }

    /**
     * Grenzen aus Kap. 11: Zeitraeume und Ketten mit genau neun Ziffern am Anfang bleiben Fund (A-2). Ausgenommen
     * ist der Zeitraum mit zwoelf Ziffern im Praefix, den Teil A entfallen laesst.
     */
    @Test
    void ausgangswerte_neunZiffernGrenze_fundBleibt() {
        int je = KONTEXTWOERTER.size() * TRENNER.size();
        List<String> neun = List.of(SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN, SynthetischeDaten.ZEITRAUM_EINSTELLIG,
                SynthetischeDaten.DATUM_PUNKT + " " + "1");
        pruefeVektoren("Neun-Ziffern-Praefix", neun, neun.size() * je, 0);
    }

    /**
     * D-2, Buchstabe vor dem Datum: Bei LANR, IK und BSNR ist der Buchstabe ein Fuellwort, das Segment ist das Datum
     * allein, und Teil A laesst den Fund entfallen. Bei den KVNR-Kontextwoertern gehoert der Buchstabe zum Fund
     * (Buchstabenform), er bleibt.
     */
    @Test
    void ausgangswerte_buchstabeUndDatum_nurBeiKvnrKontextBleibtDerFund() {
        int kvnrKontexte = 2 * TRENNER.size();
        int andere = (KONTEXTWOERTER.size() - 2) * TRENNER.size();
        Vektoren v = messeVektoren(List.of(SynthetischeDaten.BUCHSTABE_UND_DATUM));
        assertEquals(andere + kvnrKontexte, v.alteFunde(), "heutige Funde: einer je Kontext");
        assertEquals(andere, v.entfallen(), "entfallen bei LANR, IK, BSNR, NBSNR, Arztnummer");
        assertEquals(kvnrKontexte, v.verbleibend(), "bleibt bei KVNR und Versichertennummer");
    }

    // ------------------------------------------------------------------ Anlasstext

    private static final TextScanner NEU_SCANNER = TextScanner.standard();

    /** Heutige Funde im Anlasstext (Istwert, gemessen). */
    private static final int ANLASS_FUNDE = 13;

    /** Davon Funde, deren Bereich ein Datumssegment ist und die durch Teil A entfallen (Istwert, gemessen). */
    private static final int ANLASS_DATUMSFUNDE = 4;

    /**
     * Messung des Anlasstexts (synthetische Fassung, {@link SynthetischeDaten#anlassText()}) mit {@code ALT_DATUM}:
     * dreizehn Funde. Die vier Datumsfunde (14.03.1958 als KVNR 0,40, das fruehere Leistungsdatum zweimal und das
     * spaetere einmal als BSNR 0,40) entfallen durch Teil A; 01.03.2026 hat heute keinen Fund. Die BSNR-Form
     * hinter {@code LANR} in Zeile 2 ist heute BSNR (0,80), in den Stammdaten LANR (0,50)
     * (Teil B, zurueckgestellt, aendert das nicht).
     */
    @Test
    void anlasstext_heutigeFunde_undWasTeilAEntfallenLaesst() {
        assertFalse(SynthetischeKvNummern.lanrGueltigReferenz(SynthetischeKvNummern.BSNR_MARKER), "LANR ungueltig");
        assertFalse(SynthetischeKvNummern.ikGueltigReferenz(SynthetischeKvNummern.BSNR_MARKER), "IK ungueltig");
        String text = SynthetischeDaten.anlassText();
        List<Finding> alt = ALT.scan(text);
        List<Finding> neu = NEU_SCANNER.scan(text);
        List<Finding> ohneDatum = new ArrayList<>(alt);
        ohneDatum.removeIf(f -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));
        // Ohne Teil A ist das Produkt gleich der Kopie, mit Teil A gleich der Kopie ohne Datumssegmente.
        List<Finding> erwartetProdukt = TEIL_A ? ohneDatum : alt;
        assertTrue(erwartetProdukt.equals(neu), TEIL_A ? "Produkt gleich Kopie ohne Datumssegmente"
                : "Produkt gleich Kopie");
        Vergleich produkt = DatumMessung.vergleiche(text, alt, neu);
        assertTrue(produkt.zulaessig(TEIL_A), "Abweichung des Produkts zulaessig " + produkt.kurz());
        assertEquals(TEIL_A ? ANLASS_DATUMSFUNDE : 0, produkt.datumEntfallen(), "entfallene Datumsfunde des Produkts");
        assertEquals(ANLASS_FUNDE, alt.size(), "heutige Funde");
        List<FindingType> typen = new ArrayList<>();
        for (Finding f : alt) {
            typen.add(f.type());
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Originalwert gleich Ausschnitt");
        }
        assertEquals(List.of(FindingType.BSNR, FindingType.BSNR, FindingType.BSNR, FindingType.KVNR,
                FindingType.KVNR, FindingType.BSNR, FindingType.BSNR, FindingType.BSNR, FindingType.BSNR,
                FindingType.BSNR, FindingType.BSNR, FindingType.LANR, FindingType.BSNR), typen,
                "Typen in Textreihenfolge");
        double[] konfidenz = {0.90, 0.90, 0.80, 0.40, 0.50, 0.40, 0.90, 0.40, 0.90, 0.40, 0.90, 0.50, 0.90};
        String[] quellen = {BsnrRecognizer.SOURCE, BsnrRecognizer.SOURCE, BsnrRecognizer.SOURCE,
            KvnrRecognizer.SOURCE_FORM_ABWEICHEND, KvnrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG,
            BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND, BsnrRecognizer.SOURCE,
            BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND, BsnrRecognizer.SOURCE,
            BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND, BsnrRecognizer.SOURCE,
            LanrRecognizer.SOURCE_PRUEFZIFFER_UNGUELTIG, BsnrRecognizer.SOURCE};
        for (int i = 0; i < alt.size(); i++) {
            assertEquals(konfidenz[i], alt.get(i).confidence(), 1e-9, "Konfidenz " + i);
            assertEquals(quellen[i], alt.get(i).source(), "Quelle " + i);
        }
        // Teil A: genau die vier Datumsfunde entfallen, alle anderen Funde bleiben gleich.
        List<Finding> sim = SIMULIERT.scan(text);
        assertEquals(ANLASS_FUNDE - ANLASS_DATUMSFUNDE, sim.size(), "Funde nach Teil A");
        Vergleich v = DatumMessung.vergleiche(text, alt, sim);
        assertEquals(ANLASS_DATUMSFUNDE, v.datumEntfallen(), "entfallene Datumsfunde");
        assertEquals(0, v.sonstigeVerluste() + v.neueFunde() + v.abdeckung(), v.kurz());
        assertTrue(ohneDatum.equals(sim), "alle anderen Funde unveraendert (Typ, Bereich, Konfidenz, Quelle)");
    }

    /** Das Genehmigungsdatum im Anlasstext steht ohne Kontextwort in Reichweite und hat heute keinen Fund. */
    @Test
    void anlasstext_dasGenehmigungsdatumOhneKontextHatKeinenFund() {
        String text = SynthetischeDaten.anlassText();
        int stelle = text.indexOf(SynthetischeDaten.DATUM_GENEHMIGUNG);
        assertTrue(stelle > 0, "Datum im Text");
        for (Finding f : ALT.scan(text)) {
            assertFalse(f.start() < stelle + 10 && f.end() > stelle, "kein Fund ueber dem Datum ohne Kontext");
        }
    }
}
