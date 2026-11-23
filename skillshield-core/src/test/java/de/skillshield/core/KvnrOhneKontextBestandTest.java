package de.skillshield.core;

import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kuenstlicherErkenner;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kvnrOhneKontextReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.Optionen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz nach Einfuehrung der KVNR ohne Kontextwort (Teil 1b, Feature kvnr-bsnr-erkennung, Task T-10;
 * Nachtrag zu T-10, Punkt 1: S-1 bis S-3 mit der engen Referenz stehen in dieser Klasse, weil
 * {@code KvStandTrennerDifferenzTest} eingefroren ist). Verglichen werden {@code TextScanner.standard()} und der
 * eingefrorene Stand {@code ALT_TRENNER} auf den acht Bestandsmengen (dieselben wie im Pruefstand 1b: Fuzz 1, 1b, 1c,
 * 1d, 1e, Korpus, Korpus Teil 2, Generatoren; ohne absichtliche KVNR).
 *
 * <ul>
 * <li>S-1: Jede Position, die {@code ALT_TRENNER} abdeckt, deckt auch {@code standard()} ab (eigene Zaehlung).</li>
 * <li>S-2 und K-1 bis K-4: {@link KvnrBsnrHilfen#bestandsabweichungen} meldet keinen Verstoss; K-2 bis K-4 kommen
 * nicht vor; K-1 kommt je Menge genau so oft vor, wie ein breiterer KVNR-Fund einen LANR- oder IK-Fund auf denselben
 * Ziffern ersetzt (Istwerte unten, R-2).</li>
 * <li>S-3 mit der engen Referenz: Jeder Fund beider Listen, in dessen Naehe weder Kontextwort noch die enge Form steht,
 * hat in der anderen Liste einen gleichen Fund.</li>
 * <li>Alle Funde der Regel 1b auf den Bestandsmengen liegen auf den Treffern der Referenz der Regel 1b (Nachtrag zu
 * T-9, Punkt 3: hoechstens 13).</li>
 * </ul>
 * Selbsttests: Ein zusaetzlicher Erkenner fuer die weite Form laesst S-3 anschlagen; ein Scanner, der einen alten Fund
 * verliert, laesst S-1 anschlagen. Meldungen nennen nur Namen und Zahlen.
 */
class KvnrOhneKontextBestandTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String[] NAMEN = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
        "Korpus Teil 2", "Generatoren"};
    private static final String QUELLE_1B = "kvnr-ohne-kontext";

    /** Zahl der Faelle K-1 je Menge (exakt gemessen, zusammen 12: der KVNR-Fund ersetzt einen LANR- oder IK-Fund). */
    private static final int[] K1_IST = {0, 0, 5, 1, 5, 0, 0, 1};
    /** Zahl der Funde der Regel 1b je Menge (exakt gemessen, zusammen 13). */
    private static final int[] FUNDE_1B_IST = {0, 0, 5, 1, 5, 0, 0, 2};
    /** Mindestzahl der in S-3 gepruefte Funde (Istwert 73280, 70 Prozent). */
    private static final int MIN_S3_GEPRUEFT = 51000;
    /** Mindestzahl der Verstoesse im Selbsttest S-3 mit weiter Form (Istwert 308, 70 Prozent). */
    private static final int MIN_S3_SELBSTTEST = 215;
    /** Mindestzahl der Texte mit Verlust im Selbsttest S-1 (Istwert 7505, 70 Prozent). */
    private static final int MIN_S1_SELBSTTEST = 5200;

    // ------------------------------------------------------------------ Textmengen

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

    /** Dieselben acht Bestandsmengen wie der Pruefstand 1b und {@code KvStandTrennerDifferenzTest}. */
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
                KvKontextLueckenEigenschaftTest.differenzTexte());
    }

    private static List<List<String>> mengenTexte;

    private static synchronized List<List<String>> mengenTexte() {
        if (mengenTexte == null) {
            mengenTexte = mengen();
        }
        return mengenTexte;
    }

    // ------------------------------------------------------------------ Messungen

    private static boolean abdeckungVerloren(int laenge, List<Finding> alt, List<Finding> neu) {
        boolean[] imNeuen = new boolean[laenge];
        for (Finding g : neu) {
            Arrays.fill(imNeuen, g.start(), g.end(), true);
        }
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                if (!imNeuen[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean istKvnr(Finding f) {
        return f.type() == FindingType.KVNR;
    }

    // ------------------------------------------------------------------ S-1

    @Test
    void s1_jedePositionDesAltenStandsBleibtAbgedeckt() {
        List<List<String>> mengen = mengenTexte();
        for (int m = 0; m < mengen.size(); m++) {
            int verloren = 0;
            for (String text : mengen.get(m)) {
                verloren += abdeckungVerloren(text.length(), ALT.scan(text), NEU.scan(text)) ? 1 : 0;
            }
            assertEquals(0, verloren, NAMEN[m] + ": Texte mit verlorener Abdeckung");
        }
    }

    @Test
    void selbsttestS1_einVerlorenerAlterFund_wirdGemeldet() {
        int verloren = 0;
        for (List<String> menge : mengenTexte()) {
            for (String text : menge) {
                List<Finding> alt = ALT.scan(text);
                if (!alt.isEmpty()) {
                    verloren += abdeckungVerloren(text.length(), alt, alt.subList(1, alt.size())) ? 1 : 0;
                }
            }
        }
        assertTrue(verloren >= MIN_S1_SELBSTTEST, "Texte mit eingebautem Verlust: " + verloren);
        assertTrue(verloren > 0, "Der eingebaute Verlust wird nie gemeldet");
    }

    // ------------------------------------------------------------------ S-2, K-1 bis K-4

    @Test
    void s2_keinVerstoss_nurErwarteteKollisionenK1() {
        List<List<String>> mengen = mengenTexte();
        int[] k1 = new int[mengen.size()];
        for (int m = 0; m < mengen.size(); m++) {
            Abweichungen summe = KvnrBsnrHilfen.KEINE;
            for (String text : mengen.get(m)) {
                summe = summe.plus(KvnrBsnrHilfen.bestandsabweichungen(text, ALT.scan(text), NEU.scan(text)));
            }
            assertEquals(0, summe.verstoesse(), NAMEN[m] + ": Verstoesse, " + summe.kurz());
            assertEquals(0, summe.k2() + summe.k3() + summe.k4(), NAMEN[m] + ": K-2 bis K-4, " + summe.kurz());
            k1[m] = summe.k1();
        }
        assertTrue(Arrays.equals(K1_IST, k1), "K-1 je Menge: " + Arrays.toString(k1));
    }

    // ------------------------------------------------------------------ S-3

    @Test
    void s3_mitEngerReferenz_keineZusaetzlichenFunde() {
        int geprueft = 0;
        List<List<String>> mengen = mengenTexte();
        for (int m = 0; m < mengen.size(); m++) {
            int verstoesse = 0;
            for (String text : mengen.get(m)) {
                S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, ALT.scan(text), NEU.scan(text));
                geprueft += s3.geprueft();
                verstoesse += s3.verstoesse();
            }
            assertEquals(0, verstoesse, NAMEN[m] + ": Funde ohne Entsprechung in S-3");
        }
        assertTrue(geprueft >= MIN_S3_GEPRUEFT, "Zahl der in S-3 gepruefte Funde: " + geprueft);
    }

    @Test
    void selbsttestS3_weiteForm_wirdGemeldet() {
        Optionen weit = Optionen.REFERENZ.ohnePruefziffer().ohneNegativkontext().ohneWortgrenzen();
        List<Recognizer> erkenner = new ArrayList<>(NEU.recognizers());
        erkenner.add(kuenstlicherErkenner(weit));
        TextScanner mitWeit = new TextScanner(erkenner);
        int verstoesse = 0;
        for (List<String> menge : mengenTexte()) {
            for (String text : menge) {
                verstoesse += KvnrBsnrHilfen.s3Pruefung(text, ALT.scan(text), mitWeit.scan(text)).verstoesse();
            }
        }
        assertTrue(verstoesse >= MIN_S3_SELBSTTEST, "Verstoesse im Selbsttest: " + verstoesse);
        assertTrue(verstoesse > 0, "S-3 schlaegt mit der weiten Form nie an");
    }

    // ------------------------------------------------------------------ Funde der Regel 1b auf dem Bestand

    @Test
    void fundeDerRegel1b_liegenNurAufReferenzstellen_undSindExakt() {
        List<List<String>> mengen = mengenTexte();
        int[] funde = new int[mengen.size()];
        for (int m = 0; m < mengen.size(); m++) {
            for (String text : mengen.get(m)) {
                List<Bereich> referenz = kvnrOhneKontextReferenz(text);
                List<Bereich> eng = KvnrBsnrHilfen.kvnrFormEngTreffer(text);
                for (Finding f : NEU.scan(text)) {
                    if (!istKvnr(f)) {
                        continue;
                    }
                    funde[m]++;
                    assertTrue(QUELLE_1B.equals(f.source()), NAMEN[m] + ": KVNR-Fund mit anderer Quelle");
                    assertEquals(0.60, f.confidence(), 1e-9, NAMEN[m] + ": Konfidenz");
                    assertTrue(liegtIn(f, referenz), NAMEN[m] + ": KVNR-Fund ausserhalb der Referenzstellen");
                    assertTrue(liegtIn(f, eng), NAMEN[m] + ": KVNR-Fund ausserhalb der engen Form");
                }
            }
        }
        assertTrue(Arrays.equals(FUNDE_1B_IST, funde), "Funde der Regel 1b je Menge: " + Arrays.toString(funde));
    }

    private static boolean liegtIn(Finding f, List<Bereich> bereiche) {
        for (Bereich b : bereiche) {
            if (f.start() >= b.start() && f.end() <= b.ende()) {
                return true;
            }
        }
        return false;
    }
}
