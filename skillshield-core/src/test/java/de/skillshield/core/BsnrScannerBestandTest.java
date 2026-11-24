package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextAusnahme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz auf Scannerebene nach der Einbindung des BSNR-Erkenners in {@code TextScanner.standard()} (Feature
 * kvnr-bsnr-erkennung, T-14; AK-32, AK-33, Kapitel 5.2 und Nachtrag zu Teil 2 Punkt 1: S-1 bis S-3 und die
 * K-Zaehler stehen in dieser neuen Klasse, weil {@code KvStandTrennerDifferenzTest} eingefroren ist). Die Klasse
 * gehoert zum Muster von {@code KvnrScannerBestandTest}.
 *
 * <p>Textmengen: die acht Bestandsmengen (Fuzz 1, 1b, 1c, 1d, 1e, Korpus, Korpus Teil 2, Generatoren; dieselben wie in
 * {@code KvStandTrennerDifferenzTest}) und die drei Mengen der Teile nach der KVNR (Fuzz 1f, KVNR-Korpus, Fuzz 1g).
 * Keine dieser Mengen enthaelt nach der unabhaengigen Referenz ein BSNR-Kontextwort. Auf jeder Menge gilt:
 * <ul>
 * <li>die Fundliste von {@code standard()} ist in allen Feldern gleich der Fundliste desselben Scanners ohne den
 * BSNR-Erkenner; es gibt keinen BSNR-Fund und der BSNR-Erkenner liefert auf Erkennerebene keinen Kandidaten;</li>
 * <li>S-1, S-2 und S-3 (Kapitel 5.2) liefern dieselben Werte wie ohne den BSNR-Erkenner; auf den acht Bestandsmengen
 * sind Verstoesse und S-3-Abweichungen 0, K-2 bis K-4 je 0 und K-1 genau die ersetzten LANR-/IK-Funde der Ausnahme
 * der KVNR ohne Kontextwort ({@code KvnrOhneKontextAusnahme}, Nachtrag 2 zu T-10);</li>
 * <li>Selbsttest: Ein Scanner mit einem zusaetzlichen Erkenner, der einen BSNR-Fund liefert, wird je Menge in einer
 * Mindestzahl von Texten gemeldet.</li>
 * </ul>
 * Meldungen nennen nur Zahlen und Namen der Mengen, nie Text oder Ziffern.
 */
class BsnrScannerBestandTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner OHNE_BSNR = ohneBsnr();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String[] NAMEN = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
        "Korpus Teil 2", "Generatoren", "Teil 1f", "KVNR-Korpus", "Teil 1g"};
    private static final int ACHT_BESTANDSMENGEN = 8;

    private static TextScanner ohneBsnr() {
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.removeIf(r -> r instanceof BsnrRecognizer);
        return new TextScanner(liste);
    }

    /** Ein Scanner mit zusaetzlichem Erkenner: In jedem Text mit mindestens drei Zeichen liefert er einen BSNR-Fund. */
    private static TextScanner mitBsnrMutant() {
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.add(text -> text.length() >= 3
                ? List.of(new Finding(FindingType.BSNR, text.substring(0, 3), 0, 3, 0.90, "mutant"))
                : List.of());
        return new TextScanner(liste);
    }

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

    /** Die acht Bestandsmengen wie in {@code KvStandTrennerDifferenzTest}, danach Teil 1f, KVNR-Korpus, Teil 1g. */
    private static List<List<String>> mengen() {
        return List.of(
                texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE), RoundtripFuzzTest.Fall::text),
                texte(RoundtripFuzzTest.erzeugeKv(RoundtripFuzzTest.SEED_TEXTE_KV), RoundtripFuzzTest.Fall::text),
                texte(RoundtripFuzzTest.erzeugeKvC(RoundtripFuzzTest.SEED_TEXTE_KV_C),
                        RoundtripFuzzTest.FallC::text),
                texte(RoundtripFuzzTest.erzeugeKvD(RoundtripFuzzTest.SEED_TEXTE_KV_D),
                        RoundtripFuzzTest.FallD::text),
                texte(RoundtripFuzzTest.erzeugeKvE(RoundtripFuzzTest.SEED_TEXTE_KV_E),
                        RoundtripFuzzTest.FallE::text),
                KvDifferenzTest.korpus(),
                texte(RoundtripKorpusTest.kvTrennerFaelle(), RoundtripKorpusTest.KvFall::text),
                KvKontextLueckenEigenschaftTest.differenzTexte(),
                texte(RoundtripFuzzTest.erzeugeKvF(RoundtripFuzzTest.SEED_TEXTE_KV_F),
                        RoundtripFuzzTest.FallF::text),
                texte(RoundtripKorpusTest.kvnrFaelle(), RoundtripKorpusTest.KvFall::text),
                texte(RoundtripFuzzTest.erzeugeKvG(RoundtripFuzzTest.SEED_TEXTE_KV_G),
                        RoundtripFuzzTest.FallG::text));
    }

    /** Messwerte einer Textmenge. */
    private record Messung(int texte, int alteFunde, int neueFunde, int bsnrFunde, int bsnrRohkandidaten,
            int texteMitBsnrKontext, int ungleicheListen, Abweichungen abweichungen, Abweichungen abweichungenOhne,
            S3 s3, S3 s3Ohne) {
    }

    private static int bsnrFunde(List<Finding> funde) {
        int n = 0;
        for (Finding f : funde) {
            n += f.type() == FindingType.BSNR ? 1 : 0;
        }
        return n;
    }

    private static Messung messe(List<String> texte) {
        int alteFunde = 0;
        int neueFunde = 0;
        int bsnr = 0;
        int roh = 0;
        int kontext = 0;
        int ungleich = 0;
        Abweichungen abw = KvnrBsnrHilfen.KEINE;
        Abweichungen abwOhne = KvnrBsnrHilfen.KEINE;
        int s3Geprueft = 0;
        int s3Verstoesse = 0;
        int s3GeprueftOhne = 0;
        int s3VerstoesseOhne = 0;
        for (String text : texte) {
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            List<Finding> ohne = OHNE_BSNR.scan(text);
            alteFunde += alt.size();
            neueFunde += neu.size();
            bsnr += bsnrFunde(neu);
            roh += new BsnrRecognizer().detect(text).size();
            kontext += KvnrBsnrHilfen.bsnrKontextReferenz(text) ? 1 : 0;
            ungleich += neu.equals(ohne) ? 0 : 1;
            abw = abw.plus(KvnrBsnrHilfen.bestandsabweichungen(text, alt, neu));
            abwOhne = abwOhne.plus(KvnrBsnrHilfen.bestandsabweichungen(text, alt, ohne));
            S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, alt, neu);
            S3 s3Ohne = KvnrBsnrHilfen.s3Pruefung(text, alt, ohne);
            s3Geprueft += s3.geprueft();
            s3Verstoesse += s3.verstoesse();
            s3GeprueftOhne += s3Ohne.geprueft();
            s3VerstoesseOhne += s3Ohne.verstoesse();
        }
        return new Messung(texte.size(), alteFunde, neueFunde, bsnr, roh, kontext, ungleich, abw, abwOhne,
                new S3(s3Geprueft, s3Verstoesse), new S3(s3GeprueftOhne, s3VerstoesseOhne));
    }

    private static List<List<String>> mengen;
    private static Messung[] messungen;

    private static synchronized List<List<String>> gespeicherteMengen() {
        if (mengen == null) {
            mengen = mengen();
        }
        return mengen;
    }

    private static synchronized Messung[] messungen() {
        if (messungen == null) {
            List<List<String>> alle = gespeicherteMengen();
            Messung[] m = new Messung[alle.size()];
            for (int i = 0; i < m.length; i++) {
                m[i] = messe(alle.get(i));
            }
            messungen = m;
        }
        return messungen;
    }

    // ------------------------------------------------------------------ keine BSNR-Funde auf den Bestandsmengen

    /**
     * Gemessen: Auf keiner der elf Mengen gibt es einen BSNR-Fund, einen BSNR-Kandidaten des Erkenners oder ein
     * BSNR-Kontextwort nach der unabhaengigen Referenz. Die Einbindung des Erkenners aendert deshalb nichts.
     */
    @Test
    void ak32_bestandsmengen_keinBsnrFundKeinBsnrKandidatKeinBsnrKontextwort() {
        Messung[] m = messungen();
        assertEquals(NAMEN.length, m.length, "Zahl der Mengen");
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].bsnrFunde(), NAMEN[i] + ": BSNR-Funde im Scan");
            assertEquals(0, m[i].bsnrRohkandidaten(), NAMEN[i] + ": BSNR-Kandidaten auf Erkennerebene");
            assertEquals(0, m[i].texteMitBsnrKontext(), NAMEN[i] + ": Texte mit BSNR-Kontextwort");
        }
    }

    /** Die Fundliste mit BSNR-Erkenner ist in allen Feldern und in jedem Text gleich der ohne ihn. */
    @Test
    void ak32_bestandsmengen_fundlisteIstGleichDerFundlisteOhneBsnrErkenner() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].ungleicheListen(), NAMEN[i] + ": Texte mit geaenderter Fundliste");
        }
    }

    // ------------------------------------------------------------------ S-1 bis S-3 und K-Zaehler

    /**
     * S-1 bis S-3 und K-Zaehler auf den acht Bestandsmengen: Verstoesse 0, K-2 bis K-4 je 0, S-3-Abweichungen 0;
     * K-1 ist genau die Zahl der ersetzten LANR-/IK-Funde der Ausnahme der KVNR ohne Kontextwort (Teil 1b, Nachtrag 2
     * zu T-10; 0 / 0 / 5 / 1 / 5 / 0 / 0 / 1 je Menge), unveraendert durch den BSNR-Erkenner.
     */
    @Test
    void ak32_achtBestandsmengen_s1BisS3_kZaehlerKvnrAusnahmeAbgesehenNull() {
        Messung[] m = messungen();
        List<List<String>> alle = gespeicherteMengen();
        for (int i = 0; i < ACHT_BESTANDSMENGEN; i++) {
            Abweichungen a = m[i].abweichungen();
            assertEquals(0, a.verstoesse(), NAMEN[i] + ": Verstoesse; " + a.kurz());
            KvnrOhneKontextAusnahme.pruefe(NAMEN[i], alle.get(i), ALT, NEU);
            assertEquals(KvnrOhneKontextAusnahme.erwartet(NAMEN[i]).ersetzt(), a.k1(), NAMEN[i] + ": K-1; "
                    + a.kurz());
            assertEquals(0, a.k2(), NAMEN[i] + ": K-2; " + a.kurz());
            assertEquals(0, a.k3(), NAMEN[i] + ": K-3; " + a.kurz());
            assertEquals(0, a.k4(), NAMEN[i] + ": K-4; " + a.kurz());
            assertEquals(0, m[i].s3().verstoesse(), NAMEN[i] + ": S-3-Abweichungen bei " + m[i].s3().geprueft()
                    + " geprueften Funden");
        }
    }

    /**
     * Auf allen elf Mengen liefern die Pruefregel (S-1, S-2, K-1 bis K-4) und S-3 mit dem BSNR-Erkenner dieselben Werte
     * wie ohne ihn; damit bleiben auch die fensterfernen Faelle von Teil 1f und 1g (vom BSNR-Erkenner unabhaengig, in
     * {@code KvnrFuzzBestandTest} und {@code KvnrOhneKontextFuzzBestandTest} exakt festgehalten) unveraendert.
     */
    @Test
    void ak32_alleMengen_pruefregelUndS3_gleichWieOhneBsnrErkenner() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].abweichungen().equals(m[i].abweichungenOhne()), NAMEN[i]
                    + ": Pruefregel weicht ab; mit " + m[i].abweichungen().kurz() + ", ohne "
                    + m[i].abweichungenOhne().kurz());
            assertTrue(m[i].s3().equals(m[i].s3Ohne()), NAMEN[i] + ": S-3 weicht ab");
        }
    }

    /** Die Pruefung laeuft nicht ins Leere. Messwerte (alte Funde je Menge): siehe {@link #MIN_ALTE_FUNDE}. */
    @Test
    void ak32_bestandsmengen_mindestzahlenDerPruefungen() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].texte() >= MIN_TEXTE[i], NAMEN[i] + ": zu wenig Texte " + m[i].texte());
            assertTrue(m[i].alteFunde() >= MIN_ALTE_FUNDE[i], NAMEN[i] + ": zu wenig alte Funde " + m[i].alteFunde());
            assertTrue(m[i].neueFunde() >= m[i].alteFunde(), NAMEN[i] + ": weniger neue als alte Funde");
        }
    }

    /**
     * Texte je Menge (Istwerte 1 000 / 1 000 / 1 000 / 1 000 / 1 000 / 327 / 101 / 4 000 / 1 000 / 143 / 1 000) und
     * alte Funde je Menge (Istwerte 4 630 / 5 425 / 6 239 / 6 914 / 5 144 / 305 / 58 / 7 984 / 5 751 / 23 / 3 145);
     * Untergrenzen jeweils 70 % darunter (abgerundet).
     */
    private static final int[] MIN_TEXTE = {700, 700, 700, 700, 700, 228, 70, 2_800, 700, 100, 700};
    private static final int[] MIN_ALTE_FUNDE = {3_241, 3_797, 4_367, 4_839, 3_600, 213, 40, 5_588, 4_025, 16, 2_201};

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Selbsttest: Ein Scanner mit einem zusaetzlichen Erkenner, der in jedem Text einen BSNR-Fund liefert, wird auf
     * jeder Menge gemeldet: BSNR-Funde im Scan und Texte mit geaenderter Fundliste erreichen die Mindestzahlen.
     * Istwerte (BSNR-Funde im Scan, Texte mit geaenderter Liste): 340 / 523, 334 / 483, 646 / 646, 905 / 905,
     * 735 / 735, 299 / 299, 97 / 97, 3 999 / 3 999, 833 / 842, 136 / 136, 736 / 737; Untergrenzen 70 % darunter.
     */
    @Test
    void selbsttest_scannerMitZusaetzlichemBsnrFund_wirdAufJederMengeGemeldet() {
        TextScanner mutant = mitBsnrMutant();
        List<List<String>> alle = gespeicherteMengen();
        for (int i = 0; i < alle.size(); i++) {
            int bsnr = 0;
            int ungleich = 0;
            for (String text : alle.get(i)) {
                List<Finding> funde = mutant.scan(text);
                bsnr += bsnrFunde(funde);
                ungleich += funde.equals(NEU.scan(text)) ? 0 : 1;
            }
            assertTrue(bsnr >= MIN_MUTANT_BSNR[i], NAMEN[i] + ": Mutant zu selten als BSNR-Fund gemeldet " + bsnr);
            assertTrue(ungleich >= MIN_MUTANT_UNGLEICH[i], NAMEN[i] + ": Mutant zu selten als Abweichung gemeldet "
                    + ungleich);
        }
    }

    private static final int[] MIN_MUTANT_BSNR = {238, 233, 452, 633, 514, 209, 67, 2_799, 583, 95, 515};
    private static final int[] MIN_MUTANT_UNGLEICH = {366, 338, 452, 633, 514, 209, 67, 2_799, 589, 95, 515};
}
