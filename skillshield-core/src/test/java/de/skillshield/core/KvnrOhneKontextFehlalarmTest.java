package de.skillshield.core;

import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.FREMDMUSTER;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kuenstlicherErkenner;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.klein;
import static de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.kvnrOhneKontextReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.Fremdmuster;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextHilfen.Optionen;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.KvnrSynthetischWaechter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Fehlalarm-Pruefstand fuer Teil 1b "KVNR ohne Kontextwort" (Feature kvnr-bsnr-erkennung, T-9; AK-16, D-4, D-5, E-3).
 * Er wird mit T-9 abgenommen und ist danach eingefroren (Plan Regel 13); er laeuft ab T-10 unveraendert gegen das
 * Produkt mit 1b (Abbruchkriterium D-5, Regel 15). Die Texte der Meldungen enthalten nur Zahlen und Namen.
 *
 * <ul>
 * <li>(a) Auf den acht Bestandsmengen (Fuzz 1, 1b, 1c, 1d, 1e, Korpus, Korpus Teil 2, Generatoren; dieselben Mengen
 * wie {@code KvStandTrennerDifferenzTest}, ohne absichtliche KVNR) liegt kein KVNR-Fund von
 * {@code TextScanner.standard()} ausserhalb der Stellen, die
 * {@link KvnrOhneKontextHilfen#kvnrOhneKontextReferenz} trifft (nach Nachtrag zu T-9: die enge Form trifft einzelne
 * Bestandstexte, ein Fund dort ist die gewollte Wirkung von 1b, kein Fehlalarm). Als Fund auf einer Referenzstelle
 * zaehlt nur einer mit Quelle {@code kvnr-ohne-kontext} und Konfidenz 0,60, und je Menge gibt es hoechstens so viele
 * KVNR-Funde wie Referenztreffer. Die Zahl der Referenztreffer je Menge,
 * getrennt nach "alle neun Ziffern schon von einem Fund des alten Scanners {@code ALT_TRENNER} abgedeckt" und
 * "bisher frei", ist als Konstante mit Istwert festgehalten. Vor 1b (Stand nach T-8) ist die Zahl der KVNR-Funde
 * zusaetzlich genau 0 (einmalig gemessen, nicht Teil dieser Pruefung, damit sie ab T-10 unveraendert laeuft).
 * {@code TestTexte} ist eine Hilfsklasse fuer Rueckwaertsstriche und enthaelt keine Textmenge.</li>
 * <li>(b) Ein fester Satz von Fremdmustern ({@link KvnrOhneKontextHilfen#FREMDMUSTER}, mindestens 40) liefert
 * 0 KVNR-Funde, gezaehlt mit {@code KvnrRecognizer} und mit {@code TextScanner.standard()}.</li>
 * <li>(c) 100 000 Saetze mit einer zufaelligen Fremdnummer (fester Seed): hoechstens 10 500 KVNR-Funde von
 * {@code KvnrRecognizer} und ebenso von {@code TextScanner.standard()} (reine Pruefziffer-Erwartung 10 000).</li>
 * </ul>
 * Selbsttests belegen, dass die Pruefungen anschlagen: Die Referenz als kuenstlicher Erkenner liefert in (b) 0 und in
 * (c) etwa 10 000 Funde; ohne Pruefziffer schlaegt (c) an; ohne Negativkontext, ohne Wortgrenzen und mit
 * Kleinbuchstaben, mit reiner ASCII-Wortgrenze, mit jedem Grossbuchstaben als Anfang, mit Negativkontext nur ueber das
 * gewoehnliche Leerzeichen, als "acht bis neun Ziffern mit Pruefziffer" und als "neun Ziffern und beliebige weitere"
 * schlaegt (b) in der jeweiligen Gruppe an; ein kuenstlicher Erkenner fuer die weite Form und einer mit anderer
 * Quelle oder Konfidenz auf einer Referenzstelle schlagen (a) an (Regel 12, Positiv-Gegenprobe).
 */
class KvnrOhneKontextFehlalarmTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;
    private static final String[] NAMEN = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
        "Korpus Teil 2", "Generatoren"};

    // ------------------------------------------------------------------ Istwerte (AK-16 a)

    /** Zahl der Texte je Bestandsmenge (Istwerte; geprueft wird eine Untergrenze von 70 Prozent). */
    private static final int[] TEXTE_IST = {1000, 1000, 1000, 1000, 1000, 327, 101, 4000};
    /** Treffer der Referenz je Menge (exakt). */
    private static final int[] REF_TREFFER = {0, 0, 5, 1, 5, 0, 0, 2};
    /** Davon Treffer, deren neun Ziffern schon ganz von Funden des alten Scanners abgedeckt sind (exakt). */
    private static final int[] REF_ABGEDECKT = {0, 0, 5, 1, 5, 0, 0, 1};
    /** Davon Treffer, die bisher (mindestens teilweise) frei sind (exakt). */
    private static final int[] REF_FREI = {0, 0, 0, 0, 0, 0, 0, 1};
    /** Mindestzahl der Funde ausserhalb im Selbsttest mit weiter Form ueber alle Mengen (Istwert 161, 70 Prozent). */
    private static final int MIN_WEIT_AUSSERHALB = 112;
    /**
     * Mindestzahl der Funde ausserhalb im Selbsttest mit fremder Quelle oder Konfidenz: jeder der 13 Referenztreffer
     * ist dann ein Verstoss (Istwert 13, 70 Prozent).
     */
    private static final int MIN_FREMDE_QUELLE_AUSSERHALB = 9;

    // ------------------------------------------------------------------ Textmengen

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

    /** Dieselben acht Bestandsmengen wie in {@code KvStandTrennerDifferenzTest} (ohne {@code kvnrFaelle()}). */
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

    /**
     * Messwerte einer Textmenge.
     *
     * @param texte      Zahl der Texte
     * @param funde      KVNR-Funde des Scanners
     * @param ausserhalb davon Funde, die nicht ganz in einem Treffer der Referenz liegen
     * @param referenz   Treffer der Referenz
     * @param abgedeckt  davon Treffer, deren neun Ziffern ganz von Funden des alten Scanners abgedeckt sind
     * @param frei       davon alle anderen
     */
    private record MengenMessung(int texte, int funde, int ausserhalb, int referenz, int abgedeckt, int frei) {
    }

    private static boolean istKvnr(Finding f) {
        return f.type().name().equals("KVNR");
    }

    /** Toleranz beim Vergleich der Konfidenz (Gleitkommazahl) mit 0,60. */
    private static final double KONFIDENZ_TOLERANZ = 1e-9;

    /** Ob der Fund von der Regel 1b stammt: Quelle {@code kvnr-ohne-kontext} und Konfidenz 0,60 (Plan T-10). */
    private static boolean istFundDerRegel1b(Finding f) {
        return KvnrOhneKontextHilfen.QUELLE.equals(f.source())
                && Math.abs(f.confidence() - KvnrOhneKontextHilfen.KONFIDENZ) < KONFIDENZ_TOLERANZ;
    }

    /** Ob der Fund ganz in einem Treffer der Referenz liegt und ein Fund der Regel 1b ist (sonst ein Verstoss). */
    private static boolean liegtInEinemTreffer(Finding f, List<Bereich> referenz) {
        if (!istFundDerRegel1b(f)) {
            return false;
        }
        for (Bereich b : referenz) {
            if (f.start() >= b.start() && f.end() <= b.ende()) {
                return true;
            }
        }
        return false;
    }

    /** Ob jede der neun Ziffern des Treffers (alle Positionen hinter dem Buchstaben) ein alter Fund abdeckt. */
    private static boolean ziffernAbgedeckt(Bereich b, List<Finding> alt) {
        for (int pos = b.start() + 1; pos < b.ende(); pos++) {
            boolean abgedeckt = false;
            for (Finding f : alt) {
                if (pos >= f.start() && pos < f.end()) {
                    abgedeckt = true;
                    break;
                }
            }
            if (!abgedeckt) {
                return false;
            }
        }
        return true;
    }

    private static MengenMessung messe(TextScanner scanner, List<String> texte) {
        int funde = 0;
        int ausserhalb = 0;
        int referenz = 0;
        int abgedeckt = 0;
        for (String text : texte) {
            List<Bereich> treffer = kvnrOhneKontextReferenz(text);
            for (Finding f : scanner.scan(text)) {
                if (istKvnr(f)) {
                    funde++;
                    ausserhalb += liegtInEinemTreffer(f, treffer) ? 0 : 1;
                }
            }
            if (!treffer.isEmpty()) {
                List<Finding> alt = ALT.scan(text);
                for (Bereich b : treffer) {
                    referenz++;
                    abgedeckt += ziffernAbgedeckt(b, alt) ? 1 : 0;
                }
            }
        }
        return new MengenMessung(texte.size(), funde, ausserhalb, referenz, abgedeckt, referenz - abgedeckt);
    }

    private static List<List<String>> bestandsTexte;

    private static synchronized List<List<String>> bestandsTexte() {
        if (bestandsTexte == null) {
            bestandsTexte = mengen();
        }
        return bestandsTexte;
    }

    private static MengenMessung[] messeBestand(TextScanner scanner) {
        List<List<String>> mengen = bestandsTexte();
        MengenMessung[] m = new MengenMessung[mengen.size()];
        for (int i = 0; i < m.length; i++) {
            m[i] = messe(scanner, mengen.get(i));
        }
        return m;
    }

    private static MengenMessung[] bestandStandard;

    private static synchronized MengenMessung[] bestandStandard() {
        if (bestandStandard == null) {
            bestandStandard = messeBestand(STANDARD);
        }
        return bestandStandard;
    }

    private static int[] feld(MengenMessung[] m, Function<MengenMessung, Integer> f) {
        int[] r = new int[m.length];
        for (int i = 0; i < m.length; i++) {
            r[i] = f.apply(m[i]);
        }
        return r;
    }

    // ------------------------------------------------------------------ AK-16 (a)

    /**
     * AK-16 (a): Kein KVNR-Fund von {@code standard()} liegt auf den Bestandsmengen ausserhalb der Stellen der
     * Referenz der Regel 1b; auf einer Referenzstelle zaehlt nur ein Fund mit Quelle {@code kvnr-ohne-kontext} und
     * Konfidenz 0,60. Je Menge gibt es hoechstens so viele KVNR-Funde wie Referenztreffer.
     */
    @Test
    void ak16a_bestandsmengen_keinKvnrFundAusserhalbDerReferenzstellen() {
        MengenMessung[] m = bestandStandard();
        assertEquals(NAMEN.length, m.length, "Zahl der Mengen");
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].ausserhalb(), NAMEN[i]
                    + ": KVNR-Funde ausserhalb der Referenzstellen (oder mit anderer Quelle oder Konfidenz), Funde "
                    + m[i].funde());
            assertTrue(m[i].funde() <= m[i].referenz(), NAMEN[i] + ": mehr KVNR-Funde (" + m[i].funde()
                    + ") als Referenztreffer (" + m[i].referenz() + ")");
        }
    }

    /**
     * AK-16 (a): Die Zahl der Referenztreffer je Menge ist exakt festgehalten, getrennt nach "Ziffern schon durch
     * einen Bestandsfund abgedeckt" und "bisher frei". Texte je Menge: Mindestzahl 70 Prozent des Istwerts.
     */
    @Test
    void ak16a_bestandsmengen_referenztrefferJeMengeExakt() {
        MengenMessung[] m = bestandStandard();
        int[] treffer = feld(m, MengenMessung::referenz);
        int[] abgedeckt = feld(m, MengenMessung::abgedeckt);
        int[] frei = feld(m, MengenMessung::frei);
        assertTrue(Arrays.equals(REF_TREFFER, treffer), "Referenztreffer je Menge: " + Arrays.toString(treffer));
        assertTrue(Arrays.equals(REF_ABGEDECKT, abgedeckt), "davon abgedeckt: " + Arrays.toString(abgedeckt));
        assertTrue(Arrays.equals(REF_FREI, frei), "davon bisher frei: " + Arrays.toString(frei));
        int[] texte = feld(m, MengenMessung::texte);
        for (int i = 0; i < m.length; i++) {
            assertTrue(texte[i] * 10 >= TEXTE_IST[i] * 7,
                    NAMEN[i] + ": Texte " + texte[i] + ", Istwert " + TEXTE_IST[i]);
        }
        assertTrue(Arrays.stream(treffer).sum() > 0, "Die Referenz trifft auf den Bestandsmengen nichts");
    }

    /**
     * Selbsttest (a): Ein zusaetzlicher Erkenner fuer die weite Form (ohne Pruefziffer, ohne Negativkontext, ohne
     * Wortgrenzen) erzeugt Funde ausserhalb der Referenzstellen; die Pruefung (a) schlaegt also an. Mindestzahl:
     * 70 Prozent des Istwerts.
     */
    @Test
    void selbsttestAk16a_weitgefassterErkenner_fundeAusserhalbDerReferenzstellen() {
        Optionen weit = Optionen.REFERENZ.ohnePruefziffer().ohneNegativkontext().ohneWortgrenzen();
        List<Recognizer> erkenner = new ArrayList<>(STANDARD.recognizers());
        erkenner.add(kuenstlicherErkenner(weit));
        MengenMessung[] m = messeBestand(new TextScanner(erkenner));
        int ausserhalb = Arrays.stream(feld(m, MengenMessung::ausserhalb)).sum();
        assertTrue(ausserhalb >= MIN_WEIT_AUSSERHALB, "Funde ausserhalb der Referenzstellen: " + ausserhalb);
    }

    /**
     * Selbsttest (a): Ein zusaetzlicher Erkenner, der genau die Referenzstellen trifft, aber mit anderer Quelle oder
     * anderer Konfidenz (hoeher, damit er jede Konfliktregel gewinnt), laesst (a) anschlagen; mit Quelle und Konfidenz
     * der Regel 1b liegt dagegen kein Fund ausserhalb.
     */
    @Test
    void selbsttestAk16a_fremdeQuelleOderKonfidenzAufReferenzstelle() {
        Recognizer andereQuelle = kuenstlicherErkenner(Optionen.REFERENZ, "andere-quelle", 0.99);
        Recognizer andereKonfidenz = kuenstlicherErkenner(Optionen.REFERENZ, KvnrOhneKontextHilfen.QUELLE, 0.99);
        Recognizer regel1b = kuenstlicherErkenner(Optionen.REFERENZ);
        int quelle = ausserhalbMit(andereQuelle);
        int konfidenz = ausserhalbMit(andereKonfidenz);
        assertTrue(quelle >= MIN_FREMDE_QUELLE_AUSSERHALB, "andere Quelle: " + quelle);
        assertTrue(konfidenz >= MIN_FREMDE_QUELLE_AUSSERHALB, "andere Konfidenz: " + konfidenz);
        assertEquals(0, ausserhalbMit(regel1b), "Quelle und Konfidenz der Regel 1b");
    }

    private static int ausserhalbMit(Recognizer zusatz) {
        List<Recognizer> erkenner = new ArrayList<>(STANDARD.recognizers());
        erkenner.add(zusatz);
        return Arrays.stream(feld(messeBestand(new TextScanner(erkenner)), MengenMessung::ausserhalb)).sum();
    }

    // ------------------------------------------------------------------ AK-16 (b)

    private static int zahlKvnr(List<Finding> funde) {
        int n = 0;
        for (Finding f : funde) {
            n += istKvnr(f) ? 1 : 0;
        }
        return n;
    }

    private static int zaehleGruppe(Recognizer erkenner, String gruppe) {
        int n = 0;
        for (Fremdmuster m : FREMDMUSTER) {
            if (m.gruppe().equals(gruppe) && !erkenner.detect(m.text()).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private static int zaehleAlle(Recognizer erkenner) {
        int n = 0;
        for (Fremdmuster m : FREMDMUSTER) {
            n += erkenner.detect(m.text()).isEmpty() ? 0 : 1;
        }
        return n;
    }

    private static int groesse(String gruppe) {
        int n = 0;
        for (Fremdmuster m : FREMDMUSTER) {
            n += m.gruppe().equals(gruppe) ? 1 : 0;
        }
        return n;
    }

    /**
     * Zahl der Fremdmuster (Istwert 159; 10 Nummernarten, 70 Negativkontext, 12 Negativ-Leerraum, 6 Pruefziffer,
     * 16 Wortgrenze, 8 Wortgrenze Nicht-ASCII, 3 klein, 8 Trenner, 2 zwei Buchstaben, 4 Nicht-ASCII-Anfang,
     * 4 Nicht-ASCII-Ziffer, 10 acht Ziffern, 6 zehn Ziffern).
     */
    private static final int ANZAHL_FREMDMUSTER = 159;

    /** AK-16 (b): Der feste Satz der Fremdmuster liefert genau 0 KVNR-Funde (Erkenner und {@code standard()}). */
    @Test
    void ak16b_festerSatzVonFremdmustern_keinKvnrFund() {
        assertTrue(FREMDMUSTER.size() >= 40, "mindestens 40 Fremdmuster, Istwert " + FREMDMUSTER.size());
        assertEquals(ANZAHL_FREMDMUSTER, FREMDMUSTER.size(), "Zahl der Fremdmuster");
        KvnrRecognizer erkenner = new KvnrRecognizer();
        List<String> beanstandet = new ArrayList<>();
        for (int i = 0; i < FREMDMUSTER.size(); i++) {
            Fremdmuster m = FREMDMUSTER.get(i);
            int funde = zahlKvnr(erkenner.detect(m.text())) + zahlKvnr(STANDARD.scan(m.text()));
            if (funde > 0) {
                beanstandet.add("Nr. " + i + " (" + m.gruppe() + ": " + m.beschreibung() + ")");
            }
        }
        assertTrue(beanstandet.isEmpty(), "Fremdmuster mit KVNR-Fund: " + beanstandet);
    }

    /**
     * Die Fremdmuster sind synthetisch und gehoeren zu den verlangten Gruppen: Nummernarten (zehn), je Wort aus D-4
     * fuenf Saetze, Pruefziffer, Wortgrenze (je Zeichen aus AK-13 davor und dahinter, Punkt/Komma mit Ziffer),
     * Kleinbuchstaben, Trenner, zwei Buchstaben, acht und zehn Ziffern, Nicht-ASCII-Zeichen an der Wortgrenze (A-3,
     * AK-13), Nicht-ASCII-Grossbuchstabe als Anfang (A-3) und Tab, Zeilenumbruch oder geschuetztes Leerzeichen
     * zwischen Wort aus D-4 und Nummer (Kap. 4.3, Leerraum). Kein Satz enthaelt ein KVNR-Kontextwort
     * (Referenz); jede gueltige Form beginnt mit den Ziffern 0000 (Waechter).
     */
    @Test
    void ak16b_fremdmuster_gruppenVollstaendigUndOhneKontextwort() {
        assertEquals(10, groesse(KvnrOhneKontextHilfen.G_NUMMERNART), "Nummernarten");
        assertEquals(5 * KvnrOhneKontextHilfen.NEGATIVKONTEXT.size(), groesse(KvnrOhneKontextHilfen.G_NEGATIVKONTEXT),
                "Negativkontext");
        assertEquals(14, KvnrOhneKontextHilfen.NEGATIVKONTEXT.size(), "Woerter aus D-4");
        assertEquals(6, groesse(KvnrOhneKontextHilfen.G_PRUEFZIFFER), "Pruefziffer");
        assertEquals(16, groesse(KvnrOhneKontextHilfen.G_WORTGRENZE), "Wortgrenze");
        assertEquals(3, groesse(KvnrOhneKontextHilfen.G_KLEIN), "Kleinbuchstaben");
        assertEquals(8, groesse(KvnrOhneKontextHilfen.G_TRENNER), "Trenner");
        assertEquals(2, groesse(KvnrOhneKontextHilfen.G_ZWEI_BUCHSTABEN), "zwei Buchstaben");
        assertEquals(10, groesse(KvnrOhneKontextHilfen.G_ACHT_ZIFFERN), "acht Ziffern");
        assertEquals(6, groesse(KvnrOhneKontextHilfen.G_ZEHN_ZIFFERN), "zehn Ziffern");
        assertEquals(8, groesse(KvnrOhneKontextHilfen.G_WORTGRENZE_UNICODE), "Wortgrenze Nicht-ASCII");
        assertEquals(12, groesse(KvnrOhneKontextHilfen.G_NEGATIV_LEERRAUM), "Negativ-Leerraum");
        assertEquals(4, groesse(KvnrOhneKontextHilfen.G_NICHT_ASCII_ANFANG), "Nicht-ASCII-Anfang");
        assertEquals(4, groesse(KvnrOhneKontextHilfen.G_NICHT_ASCII_ZIFFER), "Nicht-ASCII-Ziffer");
        int mitKontextwort = 0;
        int waechterVerstoesse = 0;
        for (Fremdmuster m : FREMDMUSTER) {
            mitKontextwort += KvnrBsnrHilfen.kvnrKontextReferenz(m.text()) ? 1 : 0;
            waechterVerstoesse += KvnrSynthetischWaechter.pruefe(m.text()).verstoesse();
        }
        assertEquals(0, mitKontextwort, "Fremdmuster mit KVNR-Kontextwort laut Referenz");
        assertEquals(0, waechterVerstoesse, "gueltige Formen ohne die Ziffern 0000");
    }

    // ------------------------------------------------------------------ AK-16 (c)

    /** Seed der zufaelligen Fremdnummern (acht Dezimalstellen). */
    private static final long SEED_C = 20261128L;
    private static final int ANZAHL_C = 100_000;
    /** Obergrenze nach AK-16 (c): 10,5 Prozent von 100 000. */
    private static final int GRENZE_C = 10_500;
    /** Untere Grenze des Selbsttests der Referenz: reine Pruefziffer-Erwartung 10 000 minus 500. */
    private static final int UNTERGRENZE_C_REFERENZ = 9_500;
    /** Istwert der Referenz in (c) fuer den festen Seed (haelt Generator und Seed fest). */
    private static final int REFERENZ_C_IST = 9_947;

    private static List<String> texteC;

    private static synchronized List<String> texteC() {
        if (texteC == null) {
            texteC = KvnrOhneKontextHilfen.zufallsFremdnummernTexte(SEED_C, ANZAHL_C);
        }
        return texteC;
    }

    private static int zaehleFunde(Recognizer erkenner, List<String> texte) {
        int n = 0;
        for (String text : texte) {
            n += zahlKvnr(erkenner.detect(text));
        }
        return n;
    }

    /**
     * AK-16 (c): Hoechstens 10 500 von 100 000 zufaelligen Fremdnummern ergeben einen KVNR-Fund, gezaehlt mit
     * {@code KvnrRecognizer} und mit {@code TextScanner.standard()}.
     */
    @Test
    void ak16c_zufallsFremdnummern_hoechstensZehnKommaFuenfProzent() {
        List<String> texte = texteC();
        assertEquals(ANZAHL_C, texte.size(), "Zahl der Texte");
        int funde = zaehleFunde(new KvnrRecognizer(), texte);
        assertTrue(funde <= GRENZE_C, "KVNR-Funde " + funde + " von " + texte.size() + ", Grenze " + GRENZE_C);
        int fundeStandard = 0;
        for (String text : texte) {
            fundeStandard += zahlKvnr(STANDARD.scan(text));
        }
        assertTrue(fundeStandard <= GRENZE_C, "KVNR-Funde von standard() " + fundeStandard + " von " + texte.size()
                + ", Grenze " + GRENZE_C);
    }

    // ------------------------------------------------------------------ Selbsttests (b) und (c)

    /** Selbsttest: Die Referenz als Erkenner liefert in (b) genau 0 und in (c) zwischen 9 500 und 10 500 Funde. */
    @Test
    void selbsttest_referenz_bNull_cImErwartungsbereich() {
        Recognizer referenz = kuenstlicherErkenner(Optionen.REFERENZ);
        assertEquals(0, zaehleAlle(referenz), "Fremdmuster mit Fund (Referenz)");
        int funde = zaehleFunde(referenz, texteC());
        assertTrue(funde >= UNTERGRENZE_C_REFERENZ && funde <= GRENZE_C, "Referenz in (c): " + funde);
        assertEquals(REFERENZ_C_IST, funde, "Referenz in (c), Istwert fuer den festen Seed");
    }

    /** Selbsttest: Ein Erkenner ohne Pruefziffer laesst (c) anschlagen: jeder der 100 000 Saetze hat einen Fund. */
    @Test
    void selbsttest_ohnePruefziffer_cSchlaegtAn() {
        Recognizer ohne = kuenstlicherErkenner(Optionen.REFERENZ.ohnePruefziffer());
        int funde = zaehleFunde(ohne, texteC());
        assertTrue(funde > GRENZE_C, "ohne Pruefziffer in (c): " + funde);
        assertEquals(ANZAHL_C, funde, "ohne Pruefziffer hat jeder Satz einen Fund");
    }

    /** Selbsttest: Ein Erkenner ohne Negativkontext laesst (b) in der Gruppe Negativkontext anschlagen. */
    @Test
    void selbsttest_ohneNegativkontext_bSchlaegtAn() {
        Recognizer ohne = kuenstlicherErkenner(Optionen.REFERENZ.ohneNegativkontext());
        int gruppe = groesse(KvnrOhneKontextHilfen.G_NEGATIVKONTEXT);
        assertEquals(gruppe, zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_NEGATIVKONTEXT), "Gruppe Negativkontext");
        int alle = zaehleAlle(ohne);
        assertTrue(alle >= gruppe, "ohne Negativkontext in (b): " + alle);
        assertEquals(groesse(KvnrOhneKontextHilfen.G_NEGATIV_LEERRAUM),
                zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_NEGATIV_LEERRAUM), "Gruppe Negativ-Leerraum");
    }

    /** Selbsttest: Ein Erkenner ohne Wortgrenzen laesst (b) in der Gruppe Wortgrenze anschlagen. */
    @Test
    void selbsttest_ohneWortgrenzen_bSchlaegtAn() {
        Recognizer ohne = kuenstlicherErkenner(Optionen.REFERENZ.ohneWortgrenzen());
        int gruppe = groesse(KvnrOhneKontextHilfen.G_WORTGRENZE);
        assertEquals(gruppe, zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_WORTGRENZE), "Gruppe Wortgrenze");
        assertTrue(zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_ZWEI_BUCHSTABEN) > 0, "Gruppe zwei Buchstaben");
        assertTrue(zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_ZEHN_ZIFFERN) > 0, "Gruppe zehn Ziffern");
        assertEquals(groesse(KvnrOhneKontextHilfen.G_WORTGRENZE_UNICODE),
                zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_WORTGRENZE_UNICODE), "Gruppe Wortgrenze Nicht-ASCII");
    }

    /** Selbsttest: Eine reine ASCII-Wortgrenze laesst (b) in der Gruppe der Nicht-ASCII-Grenzzeichen anschlagen. */
    @Test
    void selbsttest_asciiWortgrenze_bSchlaegtAn() {
        Recognizer ascii = kuenstlicherErkenner(Optionen.REFERENZ.mitAsciiGrenze());
        assertEquals(groesse(KvnrOhneKontextHilfen.G_WORTGRENZE_UNICODE),
                zaehleGruppe(ascii, KvnrOhneKontextHilfen.G_WORTGRENZE_UNICODE), "Gruppe Wortgrenze Nicht-ASCII");
        assertEquals(0, zaehleGruppe(ascii, KvnrOhneKontextHilfen.G_WORTGRENZE), "ASCII-Grenzzeichen bleiben gesperrt");
    }

    /** Selbsttest: Jeder Grossbuchstabe als Anfang laesst (b) in der Gruppe Nicht-ASCII-Anfang anschlagen (A-3). */
    @Test
    void selbsttest_unicodeGrossbuchstabeAlsAnfang_bSchlaegtAn() {
        Recognizer unicode = kuenstlicherErkenner(Optionen.REFERENZ.mitUnicodeGross());
        assertEquals(groesse(KvnrOhneKontextHilfen.G_NICHT_ASCII_ANFANG),
                zaehleGruppe(unicode, KvnrOhneKontextHilfen.G_NICHT_ASCII_ANFANG), "Gruppe Nicht-ASCII-Anfang");
    }

    /**
     * Selbsttest: Ziffern per {@code Character.isDigit} mit {@code Character.digit} lassen (b) in der Gruppe
     * Nicht-ASCII-Ziffer anschlagen (Kap. 4.3 Schritt 1: nur ASCII-Ziffern); die Referenz findet dort nichts.
     */
    @Test
    void selbsttest_unicodeZiffern_bSchlaegtAn() {
        Recognizer unicode = kuenstlicherErkenner(Optionen.REFERENZ.mitUnicodeZiffern());
        int gruppe = groesse(KvnrOhneKontextHilfen.G_NICHT_ASCII_ZIFFER);
        assertEquals(gruppe, zaehleGruppe(unicode, KvnrOhneKontextHilfen.G_NICHT_ASCII_ZIFFER),
                "Gruppe Nicht-ASCII-Ziffer");
        assertEquals(0, zaehleGruppe(kuenstlicherErkenner(Optionen.REFERENZ),
                KvnrOhneKontextHilfen.G_NICHT_ASCII_ZIFFER), "Referenz in der Gruppe Nicht-ASCII-Ziffer");
        assertEquals(gruppe, zaehleAlle(unicode), "Fremdmuster mit Fund (nur diese Gruppe)");
    }

    /** Selbsttest: Ein Negativkontext, der nur das gewoehnliche Leerzeichen uebergeht, laesst (b) anschlagen. */
    @Test
    void selbsttest_negativkontextNurGewoehnlichesLeerzeichen_bSchlaegtAn() {
        Recognizer einfach = kuenstlicherErkenner(Optionen.REFERENZ.mitEinfachemLeerraum());
        assertEquals(groesse(KvnrOhneKontextHilfen.G_NEGATIV_LEERRAUM),
                zaehleGruppe(einfach, KvnrOhneKontextHilfen.G_NEGATIV_LEERRAUM), "Gruppe Negativ-Leerraum");
        assertEquals(0, zaehleGruppe(einfach, KvnrOhneKontextHilfen.G_NEGATIVKONTEXT), "Leerzeichen, : und = greifen");
    }

    /**
     * Mindestzahl der Treffer der Achter-Selbsttests je Auffuellung (mindestens 3 durch Konstruktion; gemessen 5 vorn
     * und 5 hinten).
     */
    private static final int MIN_ACHT_ZIFFERN_TREFFER = 2;
    /** Mindestzahl der Treffer des Selbsttests "neun Ziffern und beliebige weitere" (Istwert 6, 70 Prozent). */
    private static final int MIN_ZEHN_ZIFFERN_TREFFER = 4;

    /** Selbsttest: "acht bis neun Ziffern mit Pruefziffer" (Null vorn und hinten) laesst (b) anschlagen. */
    @Test
    void selbsttest_achtBisNeunZiffernMitPruefziffer_bSchlaegtAn() {
        for (boolean nullVorn : new boolean[] {true, false}) {
            Recognizer erkenner = KvnrOhneKontextHilfen.kuenstlicherAchtBisNeunZiffernErkenner(nullVorn);
            int treffer = zaehleGruppe(erkenner, KvnrOhneKontextHilfen.G_ACHT_ZIFFERN);
            assertTrue(treffer >= MIN_ACHT_ZIFFERN_TREFFER, "Gruppe acht Ziffern, Null vorn " + nullVorn + ": "
                    + treffer);
            assertTrue(zaehleAlle(erkenner) >= treffer, "Fremdmuster mit Fund");
        }
    }

    /** Selbsttest: "neun Ziffern und beliebige zehnte" laesst (b) in der Gruppe zehn Ziffern anschlagen. */
    @Test
    void selbsttest_neunZiffernUndBeliebigeZehnte_bSchlaegtAn() {
        Recognizer erkenner = KvnrOhneKontextHilfen.kuenstlicherNeunZiffernUndBeliebigeErkenner();
        int treffer = zaehleGruppe(erkenner, KvnrOhneKontextHilfen.G_ZEHN_ZIFFERN);
        assertTrue(treffer >= MIN_ZEHN_ZIFFERN_TREFFER, "Gruppe zehn Ziffern: " + treffer);
    }

    /** Selbsttest: Ein Erkenner mit Kleinbuchstaben laesst (b) in der Gruppe Kleinbuchstaben anschlagen. */
    @Test
    void selbsttest_kleinbuchstaben_bSchlaegtAn() {
        Recognizer mit = kuenstlicherErkenner(Optionen.REFERENZ.mitKleinbuchstaben());
        int gruppe = groesse(KvnrOhneKontextHilfen.G_KLEIN);
        assertEquals(gruppe, zaehleGruppe(mit, KvnrOhneKontextHilfen.G_KLEIN), "Gruppe Kleinbuchstaben");
    }

    /** Selbsttest: Ein Erkenner ohne Pruefziffer laesst auch (b) in den Gruppen mit falscher Pruefziffer anschlagen. */
    @Test
    void selbsttest_ohnePruefziffer_bSchlaegtAnInDenPruefzifferGruppen() {
        Recognizer ohne = kuenstlicherErkenner(Optionen.REFERENZ.ohnePruefziffer());
        assertEquals(groesse(KvnrOhneKontextHilfen.G_PRUEFZIFFER),
                zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_PRUEFZIFFER), "Gruppe Pruefziffer");
        assertEquals(groesse(KvnrOhneKontextHilfen.G_NUMMERNART),
                zaehleGruppe(ohne, KvnrOhneKontextHilfen.G_NUMMERNART), "Gruppe Nummernarten");
    }

    // ------------------------------------------------------------------ Referenz und Hilfen

    /** Grundfaelle der Referenz: Fund bei gueltiger Nummer, kein Fund bei den Verletzungen aus AK-12 bis AK-14. */
    @Test
    void referenz_grundfaelle() {
        String g = SynthetischeKvNummern.KVNR_GUELTIG;
        String u = SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
        assertTrue(kvnrOhneKontextReferenz(g).equals(List.of(new Bereich(0, 10))), "allein");
        assertTrue(kvnrOhneKontextReferenz("Der Patient " + g + " kam gestern")
                .equals(List.of(new Bereich(12, 22))), "im Satz");
        assertTrue(kvnrOhneKontextReferenz("(" + g + "), " + g + ".").size() == 2, "Satzzeichen");
        assertTrue(kvnrOhneKontextReferenz(u).isEmpty(), "falsche Pruefziffer");
        assertTrue(kvnrOhneKontextReferenz(klein(g)).isEmpty(), "Kleinbuchstabe");
        assertTrue(kvnrOhneKontextReferenz("a" + g).isEmpty(), "Buchstabe davor");
        assertTrue(kvnrOhneKontextReferenz(g + "1").isEmpty(), "Ziffer dahinter");
        assertTrue(kvnrOhneKontextReferenz(g + ".5").isEmpty(), "Punkt und Ziffer dahinter");
        assertTrue(kvnrOhneKontextReferenz("x-" + g).isEmpty(), "Bindestrich davor");
        assertTrue(kvnrOhneKontextReferenz("Ticket: " + g).isEmpty(), "Negativkontext mit Doppelpunkt");
        assertTrue(kvnrOhneKontextReferenz("AZ. = " + g).isEmpty(), "Negativkontext Az. gross");
        assertTrue(kvnrOhneKontextReferenz("Rechnung fuer Patient " + g).size() == 1, "Fuellwort");
        assertTrue(kvnrOhneKontextReferenz("MeinTicket " + g).size() == 1, "Wort ist Teil eines laengeren Worts");
    }

    /** Die Fremdnummern der zentralen Klasse: Form, Praefix 0000, Pruefziffer nach der Referenz, Gleichverteilung. */
    @Test
    void fremdnummern_formUndPruefziffer() {
        Random r = new Random(SEED_C);
        int gueltigeZufall = 0;
        for (int i = 0; i < 2000; i++) {
            String g = SynthetischeKvNummern.fremdKvnr(r, true);
            String u = SynthetischeKvNummern.fremdKvnr(r, false);
            String z = SynthetischeKvNummern.zufallsKvnr(r);
            for (String n : List.of(g, u, z)) {
                assertEquals(10, n.length(), "Laenge");
                assertTrue(n.charAt(0) >= 'A' && n.charAt(0) <= 'Z', "Buchstabe");
                assertTrue(n.substring(1, 5).equals("0000"), "Ziffern 0000");
            }
            assertTrue(KvnrBsnrHilfen.kvnrFormEng(" " + g + " "), "gueltige Fremdnummer ist eine enge Form");
            assertFalse(KvnrBsnrHilfen.kvnrFormEng(" " + u + " "), "ungueltige Fremdnummer ist keine enge Form");
            gueltigeZufall += KvnrBsnrHilfen.kvnrFormEng(" " + z + " ") ? 1 : 0;
        }
        assertTrue(gueltigeZufall > 100 && gueltigeZufall < 300, "gueltige Zufallsnummern von 2000: " + gueltigeZufall);
    }

    /** Die Texte fuer (c) sind fuer den Seed deterministisch und enthalten kein KVNR-Kontextwort. */
    @Test
    void textePruefstandC_deterministischOhneKontextwort() {
        List<String> a = KvnrOhneKontextHilfen.zufallsFremdnummernTexte(SEED_C, 500);
        List<String> b = KvnrOhneKontextHilfen.zufallsFremdnummernTexte(SEED_C, 500);
        assertTrue(a.equals(b), "gleicher Seed, gleiche Texte");
        assertTrue(a.equals(texteC().subList(0, 500)), "Anfang der Folge");
        for (String text : a) {
            assertFalse(KvnrBsnrHilfen.kvnrKontextReferenz(text), "KVNR-Kontextwort in einem Satz");
            assertEquals(1, KvnrOhneKontextHilfen.kvnrOhneKontextReferenz(text,
                    Optionen.REFERENZ.ohnePruefziffer()).size(), "genau eine Fremdnummer je Satz");
        }
    }
}
