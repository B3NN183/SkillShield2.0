package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_GROSS;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.AE_KLEIN;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.bestandsabweichungen;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.bsnrKontextReferenz;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.kvnrFormEng;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.kvnrFormReferenz;
import static de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.kvnrKontextReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvKontextLueckenEigenschaftTest;
import de.skillshield.core.recognizer.kv.KvStandTrenner;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Abweichungen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.S3;
import de.skillshield.core.recognizer.kv.KvnrOhneKontextAusnahme;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Vergleich des heutigen Standes gegen die dritte eingefrorene Kopie ({@code KvNumberScannerStandTrenner}, Stand
 * Ende kv-trenner-und-umbruch; Plan kvnr-bsnr-erkennung, Kap. 5, T-1, AK-19, AK-32). Die Kopie wird nie geaendert.
 *
 * <p>Pruefungen auf allen Textmengen (Fuzz 1, 1b, 1c, 1d, 1e, Korpus, Korpus Teil 2, Generatoren der
 * Eigenschaftstests):
 * <ul>
 * <li>Siegel: Ein Hash ueber alle Funde der Kopie je Textmenge haelt die Kopie und die Generatoren fest; ein
 * zweiter ueber die vollstaendige Ausgabe von {@code ALT_TRENNER} (alle Typen, alle Felder, auch der Originalwert)
 * die uebrigen Erkenner, den {@code TextScanner} und den {@code ConflictResolver}.</li>
 * <li>E (Erkennerebene, ohne Ausnahme): {@code LanrRecognizer} und {@code IkRecognizer} liefern auf jedem Text
 * dieselbe Fundliste wie die Kopie, in allen Feldern.</li>
 * <li>S-1 und S-2 (Scannerebene): {@link KvnrBsnrHilfen#bestandsabweichungen} meldet keinen Verstoss; die
 * Kollisionsfaelle K-1 bis K-4 kommen auf den Bestandsmengen nicht vor.</li>
 * <li>S-3 (oertlich, Nachtrag 2 zu T-1): Jeder Fund des alten und des neuen Scanners, in dessen Naehe (innerhalb
 * oder hoechstens 120 Zeichen davor oder dahinter) weder ein KVNR- oder BSNR-Kontextwort noch die enge KVNR-Form
 * steht, hat in der anderen Liste einen gleichen Fund (alle Felder).</li>
 * </ul>
 * Selbsttests belegen, dass die Pruefregel nicht ins Leere laeuft: ein eingebauter Verlust, Typwechsel oder eine
 * Abschwaechung wird gemeldet, ebenso ein Kollisionsfall ohne seine Bedingungen (Vergroesserung, Typ, Ausdehnung,
 * Naehe), und auf Mengenebene ein Scanner, der alle Funde zu {@code TEXT}/0,10/{@code zusammengelegt} macht.
 * Meldungen nennen nur Zahlen und Namen der Textmengen, nie Text oder Ziffern.
 */
class KvStandTrennerDifferenzTest {

    /** Siegel der Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1 (hexadezimal, mit Originalwert, Nachtrag 3). */
    static final int SIEGEL_ALT_TEIL_1 = 0xD77BB752;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1b (hexadezimal). */
    static final int SIEGEL_ALT_TEIL_1B = 0x2AB68259;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1c (hexadezimal). */
    static final int SIEGEL_ALT_TEIL_1C = 0xFC8FF91B;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1d (hexadezimal). */
    static final int SIEGEL_ALT_TEIL_1D = 0xD1C277A8;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf Fuzz-Teil 1e (hexadezimal). */
    static final int SIEGEL_ALT_TEIL_1E = 0x8F29F813;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf dem Korpus (hexadezimal). */
    static final int SIEGEL_ALT_KORPUS = 0x937B8824;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf dem Korpus von Teil 2 (hexadezimal). */
    static final int SIEGEL_ALT_KORPUS_TEIL_2 = 0xACF4243B;
    /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER} auf den Generatoren (hexadezimal). */
    static final int SIEGEL_ALT_GENERATOREN = 0x32981D3A;

    /** Siegel der Kopie auf Fuzz-Teil 1 (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_TEIL_1 = 0xE79FC611;
    /** Siegel der Kopie auf Fuzz-Teil 1b (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_TEIL_1B = 0x04D9C620;
    /** Siegel der Kopie auf Fuzz-Teil 1c (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_TEIL_1C = 0xA45C73DD;
    /** Siegel der Kopie auf Fuzz-Teil 1d (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_TEIL_1D = 0x13F57422;
    /** Siegel der Kopie auf Fuzz-Teil 1e (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_TEIL_1E = 0xC3CB0009;
    /** Siegel der Kopie auf dem Korpus (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_KORPUS = 0xE169792A;
    /** Siegel der Kopie auf dem Korpus von Teil 2 (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_KORPUS_TEIL_2 = 0xFB3CBFAE;
    /** Siegel der Kopie auf den Generatoren der Eigenschaftstests (hexadezimal, in T-1 gemessen). */
    static final int SIEGEL_TRENNER_GENERATOREN = 0xAEEA9CA4;

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = KvnrBsnrHilfen.ALT_TRENNER;

    private static final String[] NAMEN = {"Teil 1", "Teil 1b", "Teil 1c", "Teil 1d", "Teil 1e", "Korpus",
        "Korpus Teil 2", "Generatoren"};
    private static final int[] SIEGEL = {SIEGEL_TRENNER_TEIL_1, SIEGEL_TRENNER_TEIL_1B, SIEGEL_TRENNER_TEIL_1C,
        SIEGEL_TRENNER_TEIL_1D, SIEGEL_TRENNER_TEIL_1E, SIEGEL_TRENNER_KORPUS, SIEGEL_TRENNER_KORPUS_TEIL_2,
        SIEGEL_TRENNER_GENERATOREN};
    private static final int[] SIEGEL_ALT = {SIEGEL_ALT_TEIL_1, SIEGEL_ALT_TEIL_1B, SIEGEL_ALT_TEIL_1C,
        SIEGEL_ALT_TEIL_1D, SIEGEL_ALT_TEIL_1E, SIEGEL_ALT_KORPUS, SIEGEL_ALT_KORPUS_TEIL_2,
        SIEGEL_ALT_GENERATOREN};

    // ------------------------------------------------------------------ Textmengen

    private static <T> List<String> texte(List<T> faelle, Function<T, String> text) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (T f : faelle) {
            texte.add(text.apply(f));
        }
        return texte;
    }

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

    /** Messwerte je Textmenge; einmal berechnet und von allen Tests gelesen. */
    private static final class Messung {
        /** Zahl der Texte. */
        int texte;
        /** Siegel der Funde der Kopie (LANR und IK, Erkennerebene). */
        int siegel = SIEGEL_START;
        /** Siegel der vollstaendigen Ausgabe von {@code ALT_TRENNER}. */
        int siegelAlt = SIEGEL_START;
        /** Texte, auf denen LANR oder IK von der Kopie abweichen. */
        int ungleichKopieProdukt;
        /** Funde der Kopie auf Erkennerebene (LANR plus IK). */
        int kopieFundeErkenner;
        /** Alte Funde des Scanners (alle Typen). */
        int alteFunde;
        /** Davon LANR und IK. */
        int alteKvFunde;
        /** Summe der Pruefregel S-1/S-2 und K-1 bis K-4. */
        Abweichungen abweichungen = KvnrBsnrHilfen.KEINE;
        /** Texte ohne KVNR-/BSNR-Kontextwort und ohne enge KVNR-Form im ganzen Text. */
        int streng;
        /** Funde (alt und neu), die S-3 pruefen (ohne Kontextwort und Form in der Naehe). */
        int s3Geprueft;
        /** Davon Funde ohne gleichen Fund in der anderen Liste. */
        int s3Verstoesse;
        /** Texte mit KVNR-Kontextwort (Referenz). */
        int mitKvnrKontext;
        /** Texte mit BSNR-Kontextwort (Referenz). */
        int mitBsnrKontext;
        /** Texte mit der weiten KVNR-Form (Buchstabe und genau neun Ziffern). */
        int mitFormWeit;
        /** Texte mit der engen KVNR-Form. */
        int mitFormEng;
        /** Texte, deren neue Fundliste unsortiert, ueberlappend oder nicht deterministisch ist. */
        int unsortiert;
        /** Texte, die mit vorangestelltem Kontextwort alte Funde haben (Mutanten-Selbsttest). */
        int mutantBetroffen;
        /** Davon Texte, in denen der Mutant keinen Verstoss ausloest. */
        int mutantUnentdeckt;
        /** Alte Funde, deren Originalwert nicht dem Textausschnitt entspricht (oder deren Bereich ungueltig ist). */
        int altOriginalwertUngleich;
        /** Mutant G, Texte mit mindestens einem Nicht-KV-Altfund (mit vorangestelltem Kontextwort). */
        int mutantGNichtKvBetroffen;
        /** Davon Texte ohne Verstoss. */
        int mutantGNichtKvUnentdeckt;
        /** Mutant G, Texte mit alten Funden nur vom Typ LANR oder IK. */
        int mutantGNurKvBetroffen;
        /** Davon Texte ohne Verstoss (plangemaess zulaessig: LANR/IK zu KVNR, echt groesser). */
        int mutantGNurKvUnentdeckt;
        /** Mutant H, Texte mit alten Funden und mehr als 24 von keinem alten Fund abgedeckten Positionen. */
        int mutantHGrossBetroffen;
        /** Davon Texte ohne Verstoss. */
        int mutantHGrossUnentdeckt;
        /** Mutant H, Texte mit alten Funden und hoechstens 24 nicht abgedeckten Positionen (Ausdehnung erlaubt). */
        int mutantHKleinBetroffen;
        /** Davon Texte ohne Verstoss (nur Texte mit ausschliesslich LANR/IK-Altfunden, plangemaess zulaessig). */
        int mutantHKleinUnentdeckt;
    }

    private static final int SIEGEL_START = 17;
    /** Kontextwort, das dem Text im Mutanten-Selbsttest vorangestellt wird. */
    private static final String MUTANT_VORSATZ = "KVNR ";
    /** Typ der neuen Art in den Mutanten (der Typ existiert in T-1 noch nicht im Produkt). */
    private static final FindingType TYP_KVNR = new FindingType("KVNR");

    private static Messung[] messungen;

    private static synchronized Messung[] messungen() {
        if (messungen == null) {
            List<List<String>> mengen = mengen();
            Messung[] m = new Messung[mengen.size()];
            for (int i = 0; i < m.length; i++) {
                m[i] = messe(mengen.get(i));
            }
            messungen = m;
        }
        return messungen;
    }

    /** Hash aus den Feldern der Funde selbst (nicht aus {@code hashCode()} von Objekten), stabil ueber Laeufe. */
    private static int hash(int h, List<Finding> funde) {
        int ergebnis = 31 * h + funde.size();
        for (Finding f : funde) {
            long bits = Double.doubleToLongBits(f.confidence());
            ergebnis = 31 * ergebnis + f.start();
            ergebnis = 31 * ergebnis + f.end();
            ergebnis = 31 * ergebnis + f.type().name().hashCode();
            ergebnis = 31 * ergebnis + (int) (bits ^ (bits >>> 32));
            ergebnis = 31 * ergebnis + f.source().hashCode();
        }
        return ergebnis;
    }

    /**
     * Wie {@link #hash}, zusaetzlich geht der Originalwert jedes Fundes ein (nur fuer das Siegel von
     * {@code ALT_TRENNER}; die Kopie-Siegel bleiben bei {@link #hash}).
     */
    private static int hashMitOriginalwert(int h, List<Finding> funde) {
        int ergebnis = hash(h, funde);
        for (Finding f : funde) {
            ergebnis = 31 * ergebnis + f.originalValue().hashCode();
        }
        return ergebnis;
    }

    /** Ob der Bereich des Fundes im Text liegt und der Originalwert dem Textausschnitt entspricht. */
    private static boolean originalwertIstAusschnitt(String text, Finding f) {
        return f.start() >= 0 && f.start() <= f.end() && f.end() <= text.length()
                && text.substring(f.start(), f.end()).equals(f.originalValue());
    }

    private static boolean sortiertUndUeberlappungsfrei(String text, List<Finding> funde) {
        int voriges = 0;
        for (Finding f : funde) {
            if (f.start() < voriges || !originalwertIstAusschnitt(text, f)) {
                return false;
            }
            voriges = f.end();
        }
        return true;
    }

    private static Messung messe(List<String> texte) {
        Recognizer[] kopien = {KvStandTrenner.lanr(), KvStandTrenner.ik()};
        Recognizer[] produkt = {new LanrRecognizer(), new IkRecognizer()};
        Messung m = new Messung();
        m.texte = texte.size();
        for (String text : texte) {
            for (int a = 0; a < kopien.length; a++) {
                List<Finding> kopie = kopien[a].detect(text);
                m.siegel = hash(m.siegel, kopie);
                m.ungleichKopieProdukt += kopie.equals(produkt[a].detect(text)) ? 0 : 1;
                m.kopieFundeErkenner += kopie.size();
            }
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            m.siegelAlt = hashMitOriginalwert(m.siegelAlt, alt);
            m.altOriginalwertUngleich += (int) alt.stream().filter(f -> !originalwertIstAusschnitt(text, f)).count();
            m.alteFunde += alt.size();
            m.alteKvFunde += (int) alt.stream().filter(KvnrBsnrHilfen::istKv).count();
            m.abweichungen = m.abweichungen.plus(bestandsabweichungen(text, alt, neu));
            S3 s3 = KvnrBsnrHilfen.s3Pruefung(text, alt, neu);
            m.s3Geprueft += s3.geprueft();
            m.s3Verstoesse += s3.verstoesse();
            boolean kvnr = kvnrKontextReferenz(text);
            boolean bsnr = bsnrKontextReferenz(text);
            boolean formEng = kvnrFormEng(text);
            m.streng += !kvnr && !bsnr && !formEng ? 1 : 0;
            m.mitKvnrKontext += kvnr ? 1 : 0;
            m.mitBsnrKontext += bsnr ? 1 : 0;
            m.mitFormWeit += kvnrFormReferenz(text) ? 1 : 0;
            m.mitFormEng += formEng ? 1 : 0;
            m.unsortiert += sortiertUndUeberlappungsfrei(text, neu) && neu.equals(NEU.scan(text)) ? 0 : 1;
            messeMutant(m, text);
        }
        return m;
    }

    /** Alle Funde eines Scanners zu {@code TEXT}, Konfidenz 0,10, Quelle {@code zusammengelegt}, gleicher Bereich. */
    private static List<Finding> mutantAusAlt(List<Finding> alt) {
        return alt.stream().map(f -> new Finding(FindingType.TEXT, f.originalValue(), f.start(), f.end(), 0.10,
                Finding.SOURCE_MERGED)).toList();
    }

    /**
     * Mutant G: jeder alte Fund ein Zeichen groesser (am Ende, am Textende davor), Typ {@code KVNR}, Konfidenz 0,40,
     * Quelle {@code zusammengelegt}.
     */
    private static List<Finding> mutantGAusAlt(String text, List<Finding> alt) {
        List<Finding> neu = new ArrayList<>(alt.size());
        for (Finding f : alt) {
            int von = f.end() < text.length() ? f.start() : f.start() - 1;
            int bis = f.end() < text.length() ? f.end() + 1 : f.end();
            neu.add(new Finding(TYP_KVNR, text.substring(von, bis), von, bis, 0.40, Finding.SOURCE_MERGED));
        }
        return neu;
    }

    /** Mutant H: ein einziger Fund ueber den ganzen Text, Typ {@code KVNR}, Konfidenz 0,40, {@code zusammengelegt}. */
    private static List<Finding> mutantHAusText(String text) {
        return List.of(new Finding(TYP_KVNR, text, 0, text.length(), 0.40, Finding.SOURCE_MERGED));
    }

    /** Mutanten-Selbsttests an einem Text mit vorangestelltem KVNR-Kontextwort. */
    private static void messeMutant(Messung m, String text) {
        String t = MUTANT_VORSATZ + text;
        List<Finding> alt = ALT.scan(t);
        if (alt.isEmpty()) {
            return;
        }
        m.mutantBetroffen++;
        m.mutantUnentdeckt += bestandsabweichungen(t, alt, mutantAusAlt(alt)).verstoesse() == 0 ? 1 : 0;
        boolean ohneVerstossG = bestandsabweichungen(t, alt, mutantGAusAlt(t, alt)).verstoesse() == 0;
        if (alt.stream().allMatch(KvnrBsnrHilfen::istKv)) {
            m.mutantGNurKvBetroffen++;
            m.mutantGNurKvUnentdeckt += ohneVerstossG ? 1 : 0;
        } else {
            m.mutantGNichtKvBetroffen++;
            m.mutantGNichtKvUnentdeckt += ohneVerstossG ? 1 : 0;
        }
        boolean ohneVerstossH = bestandsabweichungen(t, alt, mutantHAusText(t)).verstoesse() == 0;
        if (unabgedeckt(t, alt) > MAX_UNABGEDECKT_TEST) {
            m.mutantHGrossBetroffen++;
            m.mutantHGrossUnentdeckt += ohneVerstossH ? 1 : 0;
        } else {
            m.mutantHKleinBetroffen++;
            m.mutantHKleinUnentdeckt += ohneVerstossH ? 1 : 0;
        }
    }

    /** Obergrenze der nicht von alten Funden abgedeckten Positionen bei K-4 (Nachtrag 3 zu T-1, Punkt 2). */
    private static final int MAX_UNABGEDECKT_TEST = 24;

    /** Zahl der Positionen des Textes, die kein alter Fund abdeckt (unabhaengig vom Hilfscode gerechnet). */
    private static int unabgedeckt(String text, List<Finding> alt) {
        boolean[] abgedeckt = new boolean[text.length()];
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                abgedeckt[i] = true;
            }
        }
        int zahl = 0;
        for (boolean b : abgedeckt) {
            zahl += b ? 0 : 1;
        }
        return zahl;
    }

    // ------------------------------------------------------------------ Siegel

    /** Hash ueber die Funde der Kopie (LANR und IK) je Textmenge; ein Fehlschlag nennt den gemessenen Wert. */
    @Test
    void standTrenner_fundbildIstEingefroren() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(SIEGEL[i], m[i].siegel, "Siegel " + NAMEN[i] + ", gemessen 0x"
                    + Integer.toHexString(m[i].siegel));
        }
    }

    /**
     * Hash ueber die vollstaendige Ausgabe von {@code ALT_TRENNER} (alle Typen, alle Felder) je Textmenge: Eine
     * Aenderung an den uebrigen Erkennern, am {@code TextScanner} oder am {@code ConflictResolver} faellt auf.
     */
    @Test
    void standTrenner_ausgabeDesAltScannersIstEingefroren() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(SIEGEL_ALT[i], m[i].siegelAlt, "Siegel ALT_TRENNER " + NAMEN[i] + ", gemessen 0x"
                    + Integer.toHexString(m[i].siegelAlt));
        }
    }

    /** Bei jedem alten Fund ist der Originalwert der Textausschnitt (Nachtrag 3 zu T-1, Punkt 4). */
    @Test
    void standTrenner_originalwertDerAltenFundeIstDerTextausschnitt() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].altOriginalwertUngleich, NAMEN[i] + ": alte Funde mit abweichendem Originalwert");
        }
    }

    // ------------------------------------------------------------------ E: Erkennerebene

    /**
     * E ohne jede Ausnahme: Auf jedem Text liefern {@code LanrRecognizer} und {@code IkRecognizer} dieselbe
     * Fundliste wie die Kopie, in allen Feldern. Gilt in jedem Task des Features.
     */
    @Test
    void erkennerebene_lanrUndIk_gleichDerKopieInAllenFeldern() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].ungleichKopieProdukt, NAMEN[i] + ": Texte mit abweichender Fundliste");
        }
    }

    // ------------------------------------------------------------------ S-1 bis S-3: Scannerebene

    /**
     * S-1 und S-2 ohne Ausnahme und die Zahl der Kollisionsfaelle je Textmenge: Auf den Bestandsmengen (kein
     * KVNR-/BSNR-Kontextwort, keine KVNR-Form im Sinne des Plans) ist jede Zahl 0, solange {@code standard()} die
     * neuen Erkenner nicht enthaelt und auch danach.
     */
    @Test
    void scannerebene_s1UndS2_keinVerstossUndKeinKollisionsfallAufDenBestandsmengen() {
        Messung[] m = messungen();
        List<List<String>> mengen = mengen(); // Nachtrag 2 zu T-10: fuer die Zaehlung der Ausnahme (neue Zeile)
        for (int i = 0; i < m.length; i++) {
            Abweichungen a = m[i].abweichungen;
            assertEquals(0, a.verstoesse(), NAMEN[i] + ": Verstoesse gegen S-1/S-2; " + a.kurz());
            // Nachtrag 2 zu T-10: Teil 1b ersetzt LANR-/IK-Funde auf gueltiger KVNR-Form; alter Wert: 0 je Menge,
            // jetzt exakt 5 / 1 / 5 / 1 (Teil 1c / 1d / 1e / Generatoren), die Ausnahme zaehlt sie einzeln.
            KvnrOhneKontextAusnahme.pruefe(NAMEN[i], mengen.get(i), ALT, NEU);
            assertEquals(KvnrOhneKontextAusnahme.erwartet(NAMEN[i]).ersetzt(), a.k1(),
                    NAMEN[i] + ": Kollisionsfall K-1; " + a.kurz());
            assertEquals(0, a.k2(), NAMEN[i] + ": Kollisionsfall K-2; " + a.kurz());
            assertEquals(0, a.k3(), NAMEN[i] + ": Kollisionsfall K-3; " + a.kurz());
            assertEquals(0, a.k4(), NAMEN[i] + ": Kollisionsfall K-4; " + a.kurz());
        }
    }

    /**
     * S-3: Jeder Fund des alten und des neuen Scanners, in dessen Naehe weder ein KVNR- oder BSNR-Kontextwort noch
     * die enge KVNR-Form steht (nach den unabhaengigen Referenzen), hat in der anderen Liste einen gleichen Fund, in
     * allen Feldern.
     */
    @Test
    void scannerebene_s3_ohneKontextwortOderFormInDerNaeheSindDieFundeGleich() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].s3Verstoesse, NAMEN[i] + ": streng geprueft, aber ungleicher Fund; geprueft "
                    + m[i].s3Geprueft);
        }
    }

    /** Der neue Scanner liefert deterministische, sortierte, ueberlappungsfreie Funde mit gueltigem Bereich. */
    @Test
    void scannerebene_neuerScannerIstDeterministischSortiertUndUeberlappungsfrei() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].unsortiert, NAMEN[i] + ": Texte mit unsortierter oder wechselnder Fundliste");
        }
    }

    // ------------------------------------------------------------------ Mindestzahlen (kein Leerlauf)

    /**
     * Die Pruefungen laufen nicht ins Leere. Messwerte je Textmenge (Teil 1, 1b, 1c, 1d, 1e, Korpus, Korpus Teil 2,
     * Generatoren; feste Seeds, Kopie und Generatoren sind eingefroren), Untergrenzen jeweils 70 % darunter
     * (abgerundet):
     * <ul>
     * <li>alte Funde des Scanners mit der Kopie (alle Typen) 4 630 / 5 425 / 6 239 / 6 914 / 5 144 / 305 / 58 /
     * 7 984, Untergrenzen {@link #MIN_ALTE_FUNDE};</li>
     * <li>davon LANR und IK 0 / 679 / 5 916 / 6 837 / 5 080 / 240 / 56 / 7 984, Untergrenzen
     * {@link #MIN_ALTE_KV_FUNDE} (Teil 1 enthaelt keine KV-Funde, dort gilt genau 0);</li>
     * <li>Funde der Kopie auf Erkennerebene (LANR plus IK) 0 / 1 065 / 7 217 / 8 558 / 5 859 / 271 / 57 / 7 984,
     * Untergrenzen {@link #MIN_KOPIE_FUNDE};</li>
     * <li>streng geprueften Texte (ohne KVNR- und BSNR-Kontextwort und ohne enge KVNR-Form) 1 000 / 1 000 / 994 /
     * 999 / 995 / 327 / 101 / 3 998, Untergrenzen {@link #MIN_STRENG};</li>
     * <li>Funde, die S-3 pruefen (alt und neu, ohne Kontextwort und Form in der Naehe) 9 260 / 10 850 / 12 424 /
     * 13 812 / 10 246 / 610 / 116 / 15 962, Untergrenzen {@link #MIN_S3_GEPRUEFT};</li>
     * <li>Texte mit alten Funden im Mutanten-Selbsttest (Text mit vorangestelltem Kontextwort) 991 / 1 000 / 999 /
     * 1 000 / 992 / 245 / 53 / 2 225, Untergrenzen {@link #MIN_MUTANT_BETROFFEN}.</li>
     * </ul>
     */
    @Test
    void mindestzahlen_diePruefungenLaufenNichtInsLeere() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].alteFunde >= MIN_ALTE_FUNDE[i], NAMEN[i] + ": zu wenig alte Funde " + m[i].alteFunde);
            assertTrue(m[i].alteKvFunde >= MIN_ALTE_KV_FUNDE[i], NAMEN[i] + ": zu wenig alte KV-Funde "
                    + m[i].alteKvFunde);
            assertTrue(m[i].kopieFundeErkenner >= MIN_KOPIE_FUNDE[i], NAMEN[i] + ": zu wenig Funde der Kopie "
                    + m[i].kopieFundeErkenner);
            assertTrue(m[i].streng >= MIN_STRENG[i], NAMEN[i] + ": zu wenig streng geprueft " + m[i].streng);
            assertTrue(m[i].texte >= m[i].streng, NAMEN[i] + ": mehr streng geprueft als Texte");
            assertTrue(m[i].s3Geprueft >= MIN_S3_GEPRUEFT[i], NAMEN[i] + ": zu wenig Funde mit S-3 geprueft "
                    + m[i].s3Geprueft);
            assertTrue(m[i].mutantBetroffen >= MIN_MUTANT_BETROFFEN[i], NAMEN[i] + ": zu wenig Texte im Mutanten-"
                    + "Selbsttest " + m[i].mutantBetroffen);
        }
    }

    private static final int[] MIN_ALTE_FUNDE = {3_241, 3_797, 4_367, 4_839, 3_600, 213, 40, 5_588};
    private static final int[] MIN_ALTE_KV_FUNDE = {0, 475, 4_141, 4_785, 3_556, 168, 39, 5_588};
    private static final int[] MIN_KOPIE_FUNDE = {0, 745, 5_051, 5_990, 4_101, 189, 39, 5_588};
    private static final int[] MIN_STRENG = {700, 700, 695, 699, 696, 228, 70, 2_798};
    private static final int[] MIN_S3_GEPRUEFT = {6_482, 7_595, 8_696, 9_668, 7_172, 427, 81, 11_173};
    private static final int[] MIN_MUTANT_BETROFFEN = {693, 700, 699, 700, 694, 171, 37, 1_557};

    /** Teil 1 enthaelt keine KV-Funde der Kopie (der Generator hat kein Kontextwort); dort ist jede Zahl genau 0. */
    @Test
    void teil1_enthaeltKeineKvFundeDerKopie() {
        Messung teil1 = messungen()[0];
        assertEquals(0, teil1.alteKvFunde, "Teil 1: alte KV-Funde im Scanner");
        assertEquals(0, teil1.kopieFundeErkenner, "Teil 1: Funde der Kopie auf Erkennerebene");
    }

    /**
     * Kein Text der Bestandsmengen hat nach den Referenzen ein KVNR- oder BSNR-Kontextwort (Plan Kap. 5.2). Die
     * weite KVNR-Form (Grossbuchstabe und genau neun Ziffern, ohne Wortgrenzen und Pruefziffer, eine
     * Ueberschaetzung) kommt dagegen vor: exakte Messwerte 0 / 0 / 58 / 27 / 44 / 1 / 0 / 37 Texte, meist ein
     * einzelnes {@code K} der Sperrschrift vor der Nummer. Die enge Form (Wortgrenzen, gueltige Pruefziffer; sie ist
     * die Bedingung fuer K-1, K-4 und S-3) trifft exakt 0 / 0 / 6 / 1 / 5 / 0 / 0 / 2 Texte; diese Texte sind
     * Kandidaten fuer D-5 (a) und werden nicht ausgenommen.
     * Streng im Sinne der Mindestzahlen sind die Texte ohne Kontextwort und ohne enge Form.
     */
    @Test
    void bestandsmengen_habenKeinKontextwortUndEineBekannteZahlTexteMitKvnrForm() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].mitKvnrKontext, NAMEN[i] + ": Texte mit KVNR-Kontextwort");
            assertEquals(0, m[i].mitBsnrKontext, NAMEN[i] + ": Texte mit BSNR-Kontextwort");
            assertEquals(WEIT[i], m[i].mitFormWeit, NAMEN[i] + ": Texte mit weiter KVNR-Form");
            assertEquals(ENG[i], m[i].mitFormEng, NAMEN[i] + ": Texte mit enger KVNR-Form");
            assertEquals(m[i].texte - m[i].mitFormEng, m[i].streng, NAMEN[i] + ": streng geprueft");
        }
    }

    private static final int[] WEIT = {0, 0, 58, 27, 44, 1, 0, 37};
    private static final int[] ENG = {0, 0, 6, 1, 5, 0, 0, 2};

    /**
     * Mutanten-Selbsttest auf Mengenebene: Ein kuenstlicher Scanner macht aus allen alten Funden einen Fund vom Typ
     * {@code TEXT} mit Konfidenz 0,10 und Quelle {@code zusammengelegt} im gleichen Bereich. Mit vorangestelltem
     * KVNR-Kontextwort muss die Pruefregel auf jedem Text mit alten Funden mindestens einen Verstoss melden.
     */
    @Test
    void selbsttest_mengenebene_mutantTextZusammengelegtMitKontextwort_wirdInJedemTextGemeldet() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertEquals(0, m[i].mutantUnentdeckt, NAMEN[i] + ": Texte ohne Verstoss trotz Mutant, betroffen "
                    + m[i].mutantBetroffen);
        }
    }

    /**
     * Mutant G auf Mengenebene: Jeder alte Fund wird ein Zeichen groesser, Typ {@code KVNR}, Konfidenz 0,40,
     * {@code zusammengelegt}. Auf jedem Text mit mindestens einem Nicht-LANR/IK-Altfund (mit vorangestelltem
     * Kontextwort) meldet die Pruefregel einen Verstoss (Typwechsel bei Nicht-KV-Alttypen, Nachtrag 3, Punkt 1). Texte
     * mit nur LANR-/IK-Altfunden gelten plangemaess als zulaessiges K-4 (LANR/IK zu KVNR, echt groesser), solange das
     * Kontextwort im Fenster liegt; ihre Zahl ist exakt festgehalten (Istwerte 0 / 6 / 289 / 266 / 217 / 197 / 52 /
     * 1 134 von 0 / 6 / 721 / 926 / 929 / 197 / 52 / 2 225 betroffenen; der Rest liegt ausserhalb des Fensters).
     * Betroffene Texte mit Nicht-KV-Altfund: 991 / 994 / 278 / 74 / 63 / 48 / 1 / 0, Untergrenzen
     * {@link #MIN_G_NICHT_KV}.
     */
    @Test
    void selbsttest_mengenebene_mutantGKvnrEinZeichenGroesser_wirdBeiNichtKvAltfundenImmerGemeldet() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].mutantGNichtKvBetroffen >= MIN_G_NICHT_KV[i], NAMEN[i] + ": zu wenig Texte mit "
                    + "Nicht-KV-Altfund " + m[i].mutantGNichtKvBetroffen);
            assertEquals(0, m[i].mutantGNichtKvUnentdeckt, NAMEN[i] + ": Texte ohne Verstoss trotz Mutant G, "
                    + "betroffen " + m[i].mutantGNichtKvBetroffen);
            assertTrue(m[i].mutantGNurKvBetroffen >= MIN_G_NUR_KV[i], NAMEN[i] + ": zu wenig Texte mit nur "
                    + "LANR/IK-Altfunden " + m[i].mutantGNurKvBetroffen);
            assertEquals(G_NUR_KV_ZULAESSIG[i], m[i].mutantGNurKvUnentdeckt, NAMEN[i] + ": zulaessige Texte "
                    + "mit nur LANR/IK-Altfunden, betroffen " + m[i].mutantGNurKvBetroffen);
        }
    }

    /** Betroffene Texte mit Nicht-KV-Altfund bei Mutant G (Istwerte im Kommentar der Testmethode), 70 % darunter. */
    private static final int[] MIN_G_NICHT_KV = {693, 695, 194, 51, 44, 33, 0, 0};
    /** Betroffene Texte mit nur LANR/IK-Altfunden bei Mutant G (0 / 6 / 721 / 926 / 929 / 197 / 52 / 2 225). */
    private static final int[] MIN_G_NUR_KV = {0, 4, 504, 648, 650, 137, 36, 1_557};
    /** Davon zulaessig (plangemaess K-4), exakte Istwerte. */
    private static final int[] G_NUR_KV_ZULAESSIG = {0, 6, 289, 266, 217, 197, 52, 1_134};

    /**
     * Mutant H auf Mengenebene: Ein einziger Fund ueber den ganzen Text, Typ {@code KVNR}, Konfidenz 0,40,
     * {@code zusammengelegt}. Hat der Text mehr als 24 von alten Funden nicht abgedeckte Positionen (Istwerte 619 / 797
     * / 998 / 1 000 / 992 / 28 / 8 / 2 225, Untergrenzen {@link #MIN_H_GROSS}), meldet die Pruefregel auf jedem
     * Text einen Verstoss (Nachtrag 3, Punkt 2). Texte mit hoechstens 24 nicht abgedeckten Positionen (372 / 203 / 1 /
     * 0 / 0 / 217 / 45 / 0) duerfen plangemaess bei reinen LANR-/IK-Altfunden unentdeckt bleiben; exakte Istwerte
     * der unentdeckten Texte {@link #H_KLEIN_ZULAESSIG}.
     */
    @Test
    void selbsttest_mengenebene_mutantHFundUeberDenGanzenText_wirdBeiGrossemZuwachsImmerGemeldet() {
        Messung[] m = messungen();
        for (int i = 0; i < m.length; i++) {
            assertTrue(m[i].mutantHGrossBetroffen >= MIN_H_GROSS[i], NAMEN[i] + ": zu wenig Texte mit grossem "
                    + "Zuwachs " + m[i].mutantHGrossBetroffen);
            assertEquals(0, m[i].mutantHGrossUnentdeckt, NAMEN[i] + ": Texte ohne Verstoss trotz Mutant H, "
                    + "betroffen " + m[i].mutantHGrossBetroffen);
            assertEquals(H_KLEIN_ZULAESSIG[i], m[i].mutantHKleinUnentdeckt, NAMEN[i] + ": zulaessige Texte mit "
                    + "kleinem Zuwachs, betroffen " + m[i].mutantHKleinBetroffen);
        }
    }

    private static final int[] MIN_H_GROSS = {433, 557, 698, 700, 694, 19, 5, 1_557};
    private static final int[] H_KLEIN_ZULAESSIG = {0, 2, 0, 0, 0, 177, 45, 0};

    // ------------------------------------------------------------------ Selbsttests

    private static String text(String... teile) {
        return String.join("", teile);
    }

    private static Finding fund(String typ, String text, int von, int bis, double konfidenz, String quelle) {
        return new Finding(new FindingType(typ), text.substring(von, bis), von, bis, konfidenz, quelle);
    }

    private static Abweichungen pruefe(String text, List<Finding> alt, List<Finding> neu) {
        return bestandsabweichungen(text, alt, neu);
    }

    /** Neun Ziffern einer KVNR mit Buchstabe {@code Z} und gueltiger Pruefziffer (rechnerisch gebildet). */
    private static final String KVNR_ZIFFERN = KvnrBsnrHilfen.kvnrNeunZiffern('Z', "00123510");

    /** Dieselben Ziffern mit um eins verschobener (also ungueltiger) Pruefziffer. */
    private static final String KVNR_UNGUELTIG = KVNR_ZIFFERN.substring(0, 8)
            + (char) ('0' + (KVNR_ZIFFERN.charAt(8) - '0' + 1) % 10);

    /** Das Fenster der oertlichen Bedingung nach Nachtrag 2 zu T-1, hier unabhaengig vom Hilfscode festgehalten. */
    private static final int NAEHE_TEST = 120;

    /** Fuellzeichen ohne Buchstaben und Ziffern, damit weder Kontextwort noch Form entsteht. */
    private static String fuell(int laenge) {
        return ".".repeat(laenge);
    }

    @Test
    void selbsttest_gleicheListeUndZusaetzlicherFund_werdenNichtGemeldet() {
        String t = text("LANR ", LANR_GUELTIG, " IK ", IK_GUELTIG);
        Finding lanr = fund("LANR", t, 5, 14, 0.95, "lanr");
        Finding ik = fund("IK", t, 18, 27, 0.92, "ik");
        assertTrue(pruefe(t, List.of(lanr, ik), List.of(lanr, ik)).keine(), "gleiche Liste");
        assertTrue(pruefe(t, List.of(lanr), List.of(lanr, ik)).keine(), "zusaetzlicher Fund");
        Finding hoeher = fund("LANR", t, 5, 14, 1.0, "lanr");
        assertTrue(pruefe(t, List.of(lanr), List.of(hoeher)).keine(), "hoehere Konfidenz");
    }

    @Test
    void selbsttest_fehlenderFund_wirdGemeldet() {
        String t = text("LANR ", LANR_GUELTIG);
        Abweichungen a = pruefe(t, List.of(fund("LANR", t, 5, 14, 0.95, "lanr")), List.of());
        assertEquals(2, a.verstoesse(), "Abdeckungsverlust und fehlender Fund");
        assertEquals(0, a.kollisionen(), "kein Kollisionsfall");
    }

    @Test
    void selbsttest_gesenkteKonfidenz_wirdGemeldet() {
        String t = text("LANR ", LANR_GUELTIG);
        Finding alt = fund("LANR", t, 5, 14, 0.95, "lanr");
        Abweichungen a = pruefe(t, List.of(alt), List.of(fund("LANR", t, 5, 14, 0.80, "lanr")));
        assertEquals(1, a.verstoesse(), "Abschwaechung bei gleicher Abdeckung");
        Abweichungen kaumGesenkt = pruefe(t, List.of(alt), List.of(fund("LANR", t, 5, 14, 0.94, "lanr")));
        assertEquals(1, kaumGesenkt.verstoesse(), "jede Senkung zaehlt");
    }

    @Test
    void selbsttest_verschobenerOderVerkuerzterBereich_wirdGemeldet() {
        String t = text("LANR ", LANR_GUELTIG, " x");
        Finding alt = fund("LANR", t, 5, 14, 0.95, "lanr");
        Abweichungen kuerzer = pruefe(t, List.of(alt), List.of(fund("LANR", t, 5, 13, 0.95, "lanr")));
        assertTrue(kuerzer.verstoesse() >= 1, "verkuerzter Bereich");
        Abweichungen verschoben = pruefe(t, List.of(alt), List.of(fund("LANR", t, 6, 15, 0.95, "lanr")));
        assertTrue(verschoben.verstoesse() >= 1, "verschobener Bereich");
        assertEquals(0, kuerzer.kollisionen() + verschoben.kollisionen(), "kein Kollisionsfall");
    }

    @Test
    void selbsttest_abdeckungsverlustBeiErsatzDurchKleinerenFund_wirdEinmalJeTextGemeldet() {
        String t = text("LANR ", LANR_GUELTIG, ", IK ", IK_GUELTIG);
        Finding lanr = fund("LANR", t, 5, 14, 0.95, "lanr");
        Finding ik = fund("IK", t, 19, 28, 0.92, "ik");
        Abweichungen a = pruefe(t, List.of(lanr, ik), List.of(lanr, fund("IK", t, 19, 27, 0.92, "ik")));
        assertEquals(2, a.verstoesse(), "ein Verlust der Abdeckung und ein Fund ohne Entsprechung");
    }

    @Test
    void selbsttest_typwechselZuLanrOderIk_wirdOhneKollisionsbedingungGemeldet() {
        String t = text("LANR ", LANR_GUELTIG, " IK ", IK_GUELTIG);
        Finding lanr = fund("LANR", t, 5, 14, 0.95, "lanr");
        Finding ik = fund("IK", t, 18, 27, 0.92, "ik");
        Abweichungen zuIk = pruefe(t, List.of(lanr), List.of(fund("IK", t, 5, 14, 0.95, "ik")));
        assertEquals(1, zuIk.verstoesse(), "LANR wird zu IK");
        Abweichungen zuLanr = pruefe(t, List.of(ik), List.of(fund("LANR", t, 18, 27, 0.92, "lanr")));
        assertEquals(1, zuLanr.verstoesse(), "IK wird zu LANR");
        assertEquals(0, zuIk.kollisionen() + zuLanr.kollisionen(), "kein Kollisionsfall");
    }

    @Test
    void selbsttest_typwechselZuNichtKvTyp_wirdAuchMitKvnrOderBsnrKontextwortGemeldet() {
        for (String kontext : new String[] {"KVNR ", "BSNR ", "x "}) {
            String t = text(kontext, LANR_GUELTIG);
            int von = kontext.length();
            Finding lanr = fund("LANR", t, von, von + 9, 0.80, "lanr");
            for (String typ : new String[] {"TEXT", "URL", "PFAD", "EMAIL", "IP"}) {
                Abweichungen a = pruefe(t, List.of(lanr), List.of(fund(typ, t, von, von + 9, 1.0, "text")));
                assertEquals(1, a.verstoesse(), "Typwechsel zu " + typ + " nach Kontext " + kontext.trim());
                assertEquals(0, a.kollisionen(), "kein Kollisionsfall");
            }
        }
    }

    @Test
    void selbsttest_k1_kvnrUmschliesstMitBuchstabeUndOhneTrenner_nurMitFormOderKontextwortZulaessig() {
        String mitForm = text("x Z", KVNR_ZIFFERN);
        Finding alt = fund("LANR", mitForm, 3, 12, 0.80, "lanr");
        Abweichungen zulaessig = pruefe(mitForm, List.of(alt),
                List.of(fund("KVNR", mitForm, 2, 12, 0.60, "kvnr-ohne-kontext")));
        assertEquals(1, zulaessig.k1(), "K-1 mit KVNR-Form");
        assertEquals(0, zulaessig.verstoesse(), "K-1 mit KVNR-Form: Verstoss");
        String ohneForm = text("x Z ", LANR_GUELTIG);
        Abweichungen verstoss = pruefe(ohneForm, List.of(fund("LANR", ohneForm, 4, 13, 0.80, "lanr")),
                List.of(fund("KVNR", ohneForm, 2, 13, 0.60, "kvnr-ohne-kontext")));
        assertEquals(0, verstoss.k1(), "K-1 ohne Form und ohne Kontextwort");
        assertEquals(1, verstoss.verstoesse(), "K-1 ohne Form und ohne Kontextwort");
    }

    @Test
    void selbsttest_k1_dieFormMussEngSein_weitOderMitFalscherPruefzifferGenuegtNicht() {
        String[] texte = {text("x Z", KVNR_UNGUELTIG), text("xZ", KVNR_ZIFFERN), text("x Z", KVNR_ZIFFERN, "1"),
            text("x z", KVNR_ZIFFERN)};
        int[] buchstabenPosition = {2, 1, 2, 2};
        for (int i = 0; i < texte.length; i++) {
            String t = texte[i];
            int von = buchstabenPosition[i] + 1;
            Abweichungen a = pruefe(t, List.of(fund("LANR", t, von, von + 9, 0.80, "lanr")),
                    List.of(fund("KVNR", t, von - 1, von + 9, 0.60, "kvnr-ohne-kontext")));
            assertEquals(0, a.k1(), "K-1 ohne enge Form, Fall " + i);
            assertEquals(1, a.verstoesse(), "K-1 ohne enge Form, Fall " + i);
        }
    }

    @Test
    void selbsttest_k1_konfidenzUntergrenze_minAlteKonfidenzUndHalb() {
        String t = text("KVNR Z", KVNR_ZIFFERN);
        Finding alt = fund("LANR", t, 6, 15, 0.80, "lanr");
        int[] ein = {60, 50};
        for (int prozent : ein) {
            Abweichungen a = pruefe(t, List.of(alt), List.of(fund("KVNR", t, 5, 15, prozent / 100.0, "kvnr")));
            assertEquals(1, a.k1(), "K-1 mit Konfidenz " + prozent + " Prozent");
            assertEquals(0, a.verstoesse(), "K-1 mit Konfidenz " + prozent + " Prozent: Verstoss");
        }
        int[] aus = {49, 5};
        for (int prozent : aus) {
            Abweichungen a = pruefe(t, List.of(alt), List.of(fund("KVNR", t, 5, 15, prozent / 100.0, "kvnr")));
            assertEquals(0, a.k1(), "K-1 mit Konfidenz " + prozent + " Prozent");
            assertEquals(1, a.verstoesse(), "K-1 mit Konfidenz " + prozent + " Prozent");
        }
        Finding schwach = fund("LANR", t, 6, 15, 0.30, "lanr-pruefziffer-ungueltig");
        Abweichungen a = pruefe(t, List.of(schwach), List.of(fund("KVNR", t, 5, 15, 0.30, "kvnr")));
        assertEquals(1, a.k1(), "alte Konfidenz unter 0,50: die alte Konfidenz ist die Untergrenze");
    }

    @Test
    void selbsttest_k1_zweiBuchstabenDirektVorDerNummer_sindEinVerstoss() {
        String t = text("KVNR ZZ", KVNR_ZIFFERN);
        Abweichungen a = pruefe(t, List.of(fund("LANR", t, 7, 16, 0.80, "lanr")),
                List.of(fund("KVNR", t, 5, 16, 0.95, "kvnr")));
        assertEquals(0, a.k1(), "Abstand 2 ohne Gruppentrenner");
        assertEquals(1, a.verstoesse(), "Abstand 2 ohne Gruppentrenner");
    }

    @Test
    void selbsttest_k1_kontextwortMussInDerNaeheStehen() {
        int[] abstaende = {NAEHE_TEST - 5, 50, 0};
        for (int n : abstaende) {
            String davor = text("KVNR", fuell(n), " Z", KVNR_UNGUELTIG);
            int von = davor.length() - 9;
            Abweichungen a = pruefe(davor, List.of(fund("LANR", davor, von, von + 9, 0.80, "lanr")),
                    List.of(fund("KVNR", davor, von - 1, von + 9, 0.95, "kvnr")));
            assertEquals(1, a.k1(), "Kontextwort davor, Abstand " + n);
        }
        String weit = text("KVNR", fuell(NAEHE_TEST - 4), " Z", KVNR_UNGUELTIG);
        int von = weit.length() - 9;
        Abweichungen a = pruefe(weit, List.of(fund("LANR", weit, von, von + 9, 0.80, "lanr")),
                List.of(fund("KVNR", weit, von - 1, von + 9, 0.95, "kvnr")));
        assertEquals(0, a.k1(), "Kontextwort ausserhalb des Fensters");
        assertEquals(1, a.verstoesse(), "Kontextwort ausserhalb des Fensters");
        String dahinter = text("x Z", KVNR_UNGUELTIG, fuell(50), " KVNR");
        Abweichungen b = pruefe(dahinter, List.of(fund("LANR", dahinter, 3, 12, 0.80, "lanr")),
                List.of(fund("KVNR", dahinter, 2, 12, 0.95, "kvnr")));
        assertEquals(1, b.k1(), "Kontextwort dahinter (die Ziffern sind hier ohne gueltige Pruefziffer)");
    }

    /**
     * Gegenrichtung zum Fenster: Das Kontextwort steht hinter dem neuen Fund (Ende 12). Das Fenster reicht bis zur
     * Position 12 + NAEHE; liegt das Wort ganz darin, ist es nahe, ragt es um ein Zeichen hinaus, nicht.
     */
    @Test
    void selbsttest_k1_kontextwortHinterDemFund_aufDerFenstergrenze() {
        int[] abstaende = {0, 50, NAEHE_TEST - 4};
        for (int n : abstaende) {
            String t = text("x Z", KVNR_UNGUELTIG, fuell(n), "KVNR");
            Abweichungen a = pruefe(t, List.of(fund("LANR", t, 3, 12, 0.80, "lanr")),
                    List.of(fund("KVNR", t, 2, 12, 0.95, "kvnr")));
            assertEquals(1, a.k1(), "Kontextwort dahinter ganz im Fenster, Abstand " + n);
            assertEquals(0, a.verstoesse(), "Kontextwort dahinter ganz im Fenster, Abstand " + n);
        }
        String ragtHinaus = text("x Z", KVNR_UNGUELTIG, fuell(NAEHE_TEST - 3), "KVNR");
        Abweichungen b = pruefe(ragtHinaus, List.of(fund("LANR", ragtHinaus, 3, 12, 0.80, "lanr")),
                List.of(fund("KVNR", ragtHinaus, 2, 12, 0.95, "kvnr")));
        assertEquals(0, b.k1(), "Kontextwort ragt um ein Zeichen aus dem Fenster");
        assertEquals(1, b.verstoesse(), "Kontextwort ragt um ein Zeichen aus dem Fenster");
    }

    @Test
    void selbsttest_k1_mitKvnrKontextwortUndTrenner_istZulaessig() {
        String t = text("KVNR Z ", LANR_GUELTIG);
        Abweichungen a = pruefe(t, List.of(fund("IK", t, 7, 16, 0.80, "ik")),
                List.of(fund("KVNR", t, 5, 16, 0.95, "kvnr")));
        assertEquals(1, a.k1(), "K-1 mit Kontextwort, Buchstabe und Trenner");
        assertEquals(0, a.verstoesse(), "K-1 mit Kontextwort, Buchstabe und Trenner: Verstoss");
    }

    @Test
    void selbsttest_k1_falscheGeometrieOderFalscherAltTyp_wirdAuchMitKontextwortGemeldet() {
        String t = text("KVNR ZZZ", LANR_GUELTIG);
        Finding lanr = fund("LANR", t, 8, 17, 0.80, "lanr");
        Abweichungen dreiDavor = pruefe(t, List.of(lanr), List.of(fund("KVNR", t, 5, 17, 0.95, "kvnr")));
        assertEquals(1, dreiDavor.verstoesse(), "drei Zeichen vor dem alten Fund");
        String ziffer = text("KVNR 7", LANR_GUELTIG);
        Abweichungen keinBuchstabe = pruefe(ziffer, List.of(fund("LANR", ziffer, 6, 15, 0.80, "lanr")),
                List.of(fund("KVNR", ziffer, 5, 15, 0.95, "kvnr")));
        assertEquals(1, keinBuchstabe.verstoesse(), "Ziffer statt Buchstabe vor dem alten Fund");
        String mail = text("KVNR Z", LANR_GUELTIG);
        Abweichungen nichtKv = pruefe(mail, List.of(fund("URL", mail, 6, 15, 0.80, "url")),
                List.of(fund("KVNR", mail, 5, 15, 0.95, "kvnr")));
        assertEquals(1, nichtKv.verstoesse(), "K-1 gilt nur fuer alte LANR- und IK-Funde");
        Abweichungen anderesEnde = pruefe(t, List.of(lanr), List.of(fund("KVNR", t, 7, 16, 0.95, "kvnr")));
        assertTrue(anderesEnde.verstoesse() >= 1, "anderes Ende des neuen Fundes");
        assertEquals(0, dreiDavor.kollisionen() + keinBuchstabe.kollisionen() + nichtKv.kollisionen(), "Kollision");
    }

    @Test
    void selbsttest_k2_gleicheKonfidenzUndTypVorneImAlphabet_nurMitKontextwortDerArtZulaessig() {
        String bsnr = text("BSNR ", LANR_GUELTIG);
        Finding lanr = fund("LANR", bsnr, 5, 14, 0.80, "lanr");
        Abweichungen a = pruefe(bsnr, List.of(lanr), List.of(fund("BSNR", bsnr, 5, 14, 0.80, "bsnr")));
        assertEquals(1, a.k2(), "BSNR statt LANR mit BSNR-Kontextwort");
        assertEquals(0, a.verstoesse(), "BSNR statt LANR mit BSNR-Kontextwort: Verstoss");
        String kvnr = text("KVNR ", LANR_GUELTIG);
        Abweichungen b = pruefe(kvnr, List.of(fund("LANR", kvnr, 5, 14, 0.80, "lanr")),
                List.of(fund("KVNR", kvnr, 5, 14, 0.80, "kvnr")));
        assertEquals(1, b.k2(), "KVNR statt LANR mit KVNR-Kontextwort");
        Abweichungen falscheArt = pruefe(kvnr, List.of(fund("LANR", kvnr, 5, 14, 0.80, "lanr")),
                List.of(fund("BSNR", kvnr, 5, 14, 0.80, "bsnr")));
        assertEquals(1, falscheArt.verstoesse(), "BSNR-Fund mit KVNR-Kontextwort der falschen Art");
        Abweichungen ohneKontext = pruefe(bsnr, List.of(lanr), List.of(fund("BSNR", bsnr, 5, 14, 0.80, "bsnr")));
        assertEquals(0, ohneKontext.verstoesse(), "Gegenprobe mit Kontextwort");
        String keins = text("x ", LANR_GUELTIG);
        Abweichungen ohne = pruefe(keins, List.of(fund("LANR", keins, 2, 11, 0.80, "lanr")),
                List.of(fund("BSNR", keins, 2, 11, 0.80, "bsnr")));
        assertEquals(1, ohne.verstoesse(), "ohne jedes Kontextwort");
    }

    @Test
    void selbsttest_k2_typNamentlichNichtVorDemAltenTyp_wirdAuchMitKontextwortGemeldet() {
        String t = text("KVNR ", IK_GUELTIG);
        Abweichungen a = pruefe(t, List.of(fund("IK", t, 5, 14, 0.80, "ik")),
                List.of(fund("KVNR", t, 5, 14, 0.80, "kvnr")));
        assertEquals(1, a.verstoesse(), "KVNR steht nicht vor IK, der Typ darf bei Gleichstand nicht wechseln");
        assertEquals(0, a.k2(), "kein K-2");
        String b = text("BSNR ", IK_GUELTIG);
        Abweichungen vorn = pruefe(b, List.of(fund("IK", b, 5, 14, 0.80, "ik")),
                List.of(fund("BSNR", b, 5, 14, 0.80, "bsnr")));
        assertEquals(1, vorn.k2(), "BSNR steht vor IK");
    }

    @Test
    void selbsttest_k3_hoehereKonfidenz_nurMitKontextwortDerArtUndNurAufwaertsZulaessig() {
        String t = text("BSNR ", IK_GUELTIG);
        Finding ik = fund("IK", t, 5, 14, 0.50, "ik-pruefziffer-ungueltig");
        Abweichungen a = pruefe(t, List.of(ik), List.of(fund("BSNR", t, 5, 14, 0.90, "bsnr")));
        assertEquals(1, a.k3(), "BSNR 0,90 statt IK 0,50 mit BSNR-Kontextwort");
        assertEquals(0, a.verstoesse(), "K-3: Verstoss");
        String kvnr = text("KVNR ", IK_GUELTIG);
        Abweichungen falscheArt = pruefe(kvnr, List.of(fund("IK", kvnr, 5, 14, 0.50, "ik")),
                List.of(fund("BSNR", kvnr, 5, 14, 0.90, "bsnr")));
        assertEquals(1, falscheArt.verstoesse(), "BSNR-Fund mit KVNR-Kontextwort der falschen Art");
        Abweichungen abwaerts = pruefe(t, List.of(fund("LANR", t, 5, 14, 0.80, "lanr")),
                List.of(fund("BSNR", t, 5, 14, 0.50, "bsnr-regionalcode-unbekannt")));
        assertEquals(1, abwaerts.verstoesse(), "niedrigere Konfidenz beim Typwechsel ist kein K-3");
    }

    @Test
    void selbsttest_k4_zusammengelegterFundUmfasstDenAltenFund_nurMitKontextwortOderFormZulaessig() {
        String mit = text("KVNR ", LANR_GUELTIG, "12");
        Finding lanr = fund("LANR", mit, 5, 14, 0.80, "lanr");
        Finding zusammen = fund("LANR", mit, 5, 16, 0.40, Finding.SOURCE_MERGED);
        Abweichungen a = pruefe(mit, List.of(lanr), List.of(zusammen));
        assertEquals(1, a.k4(), "K-4 mit KVNR-Kontextwort");
        assertEquals(0, a.verstoesse(), "K-4 mit KVNR-Kontextwort: Verstoss");
        String bsnr = text("BSNR ", LANR_GUELTIG, "12");
        assertEquals(1, pruefe(bsnr, List.of(lanr), List.of(zusammen)).k4(), "K-4 mit BSNR-Kontextwort");
        String form = text("x Z", KVNR_ZIFFERN, " y");
        Abweichungen mitForm = pruefe(form, List.of(fund("LANR", form, 3, 12, 0.80, "lanr")),
                List.of(fund("KVNR", form, 2, 14, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(1, mitForm.k4(), "K-4 mit KVNR-Form");
        String ohne = text("x ", LANR_GUELTIG, "12");
        Abweichungen verstoss = pruefe(ohne, List.of(fund("LANR", ohne, 2, 11, 0.80, "lanr")),
                List.of(fund("LANR", ohne, 2, 13, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(0, verstoss.k4(), "K-4 ohne Kontextwort und ohne Form");
        assertEquals(1, verstoss.verstoesse(), "K-4 ohne Kontextwort und ohne Form");
    }

    @Test
    void selbsttest_zusammengelegterFundOhneAbdeckungDesAltenFundes_wirdGemeldet() {
        String t = text("KVNR ", LANR_GUELTIG, "12");
        Abweichungen a = pruefe(t, List.of(fund("LANR", t, 5, 14, 0.80, "lanr")),
                List.of(fund("LANR", t, 8, 16, 0.40, Finding.SOURCE_MERGED)));
        assertTrue(a.verstoesse() >= 1, "der zusammengelegte Fund umfasst den alten Fund nicht");
        assertEquals(0, a.k4(), "kein K-4");
    }

    @Test
    void selbsttest_k4_gleicherBereichOderFremderTypOderZuNiedrigeKonfidenz_wirdAuchMitKontextwortGemeldet() {
        String t = text("KVNR ", LANR_GUELTIG, "12");
        Finding lanr = fund("LANR", t, 5, 14, 0.80, "lanr");
        List<Finding> faelle = List.of(
                fund("TEXT", t, 5, 14, 0.80, Finding.SOURCE_MERGED),
                fund("TEXT", t, 5, 16, 0.80, Finding.SOURCE_MERGED),
                fund("URL", t, 5, 16, 0.80, Finding.SOURCE_MERGED),
                fund("LANR", t, 5, 16, 0.10, Finding.SOURCE_MERGED),
                fund("LANR", t, 5, 16, 0.39, Finding.SOURCE_MERGED),
                fund("TEXT", t, 5, 16, 0.10, Finding.SOURCE_MERGED));
        for (int i = 0; i < faelle.size(); i++) {
            Abweichungen a = pruefe(t, List.of(lanr), List.of(faelle.get(i)));
            assertEquals(0, a.k4(), "kein K-4, Fall " + i);
            assertEquals(1, a.verstoesse(), "Verstoss, Fall " + i);
        }
        Finding schwach = fund("LANR", t, 5, 14, 0.30, "lanr-pruefziffer-ungueltig");
        Abweichungen grenze = pruefe(t, List.of(schwach), List.of(fund("LANR", t, 5, 16, 0.30,
                Finding.SOURCE_MERGED)));
        assertEquals(1, grenze.k4(), "alte Konfidenz unter 0,40: die alte Konfidenz ist die Untergrenze");
        Abweichungen untenAusgenommen = pruefe(t, List.of(lanr), List.of(fund("LANR", t, 5, 16, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(1, untenAusgenommen.k4(), "genau 0,40 bei alter Konfidenz 0,80 ist zulaessig");
    }

    @Test
    void selbsttest_k4_nichtKvAltTypMitGleichemTypUndEchterVergroesserung_istZulaessig() {
        String t = text("KVNR ", IK_FORM_DOKU_IP, "9");
        Finding ip = fund("IP", t, 5, 17, 0.90, "ip");
        Abweichungen a = pruefe(t, List.of(ip), List.of(fund("IP", t, 5, 18, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(1, a.k4(), "alter IP-Fund, neuer zusammengelegter IP-Fund, echt groesser");
        assertEquals(0, a.verstoesse(), "alter IP-Fund, neuer zusammengelegter IP-Fund: Verstoss");
        Abweichungen andererTyp = pruefe(t, List.of(ip), List.of(fund("URL", t, 5, 18, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(1, andererTyp.verstoesse(), "Typ des neuen Fundes ist nicht der eines umfassten alten Fundes");
        Abweichungen gleicherBereich = pruefe(t, List.of(ip), List.of(fund("IP", t, 5, 17, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(1, gleicherBereich.verstoesse(), "gleicher Bereich, nur geringere Konfidenz");
        assertEquals(0, andererTyp.k4() + gleicherBereich.k4(), "kein K-4");
    }

    @Test
    void selbsttest_k4_nichtKvAltTypZuKvnrOderBsnr_istAuchMitKontextwortEinVerstoss() {
        String t = text("KVNR ", IK_FORM_DOKU_IP, "9");
        for (String alterTyp : new String[] {"EMAIL", "IP", "URL", "PFAD", "TEXT"}) {
            Finding alt = fund(alterTyp, t, 5, 17, 0.95, "alt");
            for (String neuerTyp : new String[] {"KVNR", "BSNR"}) {
                Abweichungen a = pruefe(t, List.of(alt), List.of(fund(neuerTyp, t, 5, 18, 0.40,
                        Finding.SOURCE_MERGED)));
                assertEquals(0, a.k4(), "kein K-4: " + alterTyp + " zu " + neuerTyp);
                assertEquals(1, a.verstoesse(), "Verstoss: " + alterTyp + " zu " + neuerTyp);
            }
        }
    }

    @Test
    void selbsttest_k4_altLanrOderIkZuKvnrOderBsnr_echtGroesser_istZulaessig() {
        String t = text("KVNR ", LANR_GUELTIG, "1");
        for (String alterTyp : new String[] {"LANR", "IK"}) {
            Finding alt = fund(alterTyp, t, 5, 14, 0.80, "alt");
            for (String neuerTyp : new String[] {"KVNR", "BSNR"}) {
                Abweichungen a = pruefe(t, List.of(alt), List.of(fund(neuerTyp, t, 5, 15, 0.40,
                        Finding.SOURCE_MERGED)));
                assertEquals(1, a.k4(), "K-4: " + alterTyp + " zu " + neuerTyp);
                assertEquals(0, a.verstoesse(), "K-4 zulaessig: " + alterTyp + " zu " + neuerTyp);
            }
        }
    }

    @Test
    void selbsttest_k4_ausdehnungBisZuVierundzwanzigUnabgedecktenPositionen_zulaessigDarueberVerstoss() {
        for (int zuwachs : new int[] {1, 12, 24, 25, 26, 100}) {
            String t = text("KVNR ", LANR_GUELTIG, fuell(zuwachs));
            Abweichungen a = pruefe(t, List.of(fund("LANR", t, 5, 14, 0.80, "lanr")),
                    List.of(fund("KVNR", t, 5, 14 + zuwachs, 0.40, Finding.SOURCE_MERGED)));
            boolean zulaessig = zuwachs <= 24;
            assertEquals(zulaessig ? 1 : 0, a.k4(), "K-4 bei Zuwachs " + zuwachs);
            assertEquals(zulaessig ? 0 : 1, a.verstoesse(), "Verstoss bei Zuwachs " + zuwachs);
        }
    }

    @Test
    void selbsttest_k4_dieAusdehnungZaehltNurPositionenOhneAltenFund() {
        String t = text("KVNR ", LANR_GUELTIG, fuell(20), IK_GUELTIG, fuell(20));
        Finding lanr = fund("LANR", t, 5, 14, 0.80, "lanr");
        Finding ik = fund("IK", t, 34, 43, 0.80, "ik");
        Abweichungen zwei = pruefe(t, List.of(lanr, ik), List.of(fund("KVNR", t, 5, 63, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(0, zwei.k4(), "zwei alte Funde, 40 nicht abgedeckte Positionen");
        assertEquals(2, zwei.verstoesse(), "40 nicht abgedeckte Positionen sind zu viel");
        Abweichungen genau = pruefe(t, List.of(lanr, ik), List.of(fund("KVNR", t, 5, 47, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(2, genau.k4(), "zwei alte Funde, 24 nicht abgedeckte Positionen");
        assertEquals(0, genau.verstoesse(), "24 nicht abgedeckte Positionen sind zulaessig");
        Abweichungen eineMehr = pruefe(t, List.of(lanr, ik), List.of(fund("KVNR", t, 5, 48, 0.40,
                Finding.SOURCE_MERGED)));
        assertEquals(0, eineMehr.k4(), "25 nicht abgedeckte Positionen");
        assertEquals(2, eineMehr.verstoesse(), "25 nicht abgedeckte Positionen sind ein Verstoss");
    }

    @Test
    void selbsttest_k4_einFundUeberDenGanzenText_istEinVerstoss() {
        String t = text("KVNR ", fuell(60), LANR_GUELTIG);
        Abweichungen a = pruefe(t, List.of(fund("LANR", t, 65, 74, 0.80, "lanr")),
                List.of(fund("KVNR", t, 0, t.length(), 0.40, Finding.SOURCE_MERGED)));
        assertEquals(0, a.k4(), "kein K-4");
        assertEquals(1, a.verstoesse(), "Verstoss");
    }

    @Test
    void selbsttest_k4_kontextwortMussInDerNaeheStehen() {
        String fern = text("KVNR", fuell(500), " ", LANR_GUELTIG, "12");
        int von = fern.length() - 11;
        Abweichungen a = pruefe(fern, List.of(fund("LANR", fern, von, von + 9, 0.80, "lanr")),
                List.of(fund("LANR", fern, von, von + 11, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(0, a.k4(), "Kontextwort 500 Zeichen davor");
        assertEquals(1, a.verstoesse(), "Kontextwort 500 Zeichen davor");
        String davor = text("KVNR", fuell(50), " ", LANR_GUELTIG, "12");
        int vonDavor = davor.length() - 11;
        Abweichungen b = pruefe(davor, List.of(fund("LANR", davor, vonDavor, vonDavor + 9, 0.80, "lanr")),
                List.of(fund("LANR", davor, vonDavor, vonDavor + 11, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(1, b.k4(), "Kontextwort 50 Zeichen davor");
        String dahinter = text(LANR_GUELTIG, "12", fuell(50), " BSNR");
        Abweichungen c = pruefe(dahinter, List.of(fund("LANR", dahinter, 0, 9, 0.80, "lanr")),
                List.of(fund("LANR", dahinter, 0, 11, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(1, c.k4(), "Kontextwort 50 Zeichen dahinter");
        String dahinterFern = text(LANR_GUELTIG, "12", fuell(500), " BSNR");
        Abweichungen d = pruefe(dahinterFern, List.of(fund("LANR", dahinterFern, 0, 9, 0.80, "lanr")),
                List.of(fund("LANR", dahinterFern, 0, 11, 0.40, Finding.SOURCE_MERGED)));
        assertEquals(0, d.k4(), "Kontextwort 500 Zeichen dahinter");
        assertEquals(1, d.verstoesse(), "Kontextwort 500 Zeichen dahinter");
    }

    @Test
    void selbsttest_s3_giltFuerFundeOhneKontextwortOderFormInDerNaehe() {
        List<Finding> alt = List.of();
        String fern = text("KVNR", fuell(500), " ", LANR_GUELTIG);
        int von = fern.length() - 9;
        Finding lanr = fund("LANR", fern, von, von + 9, 0.95, "lanr");
        Finding hoeher = fund("LANR", fern, von, von + 9, 1.0, "lanr");
        S3 ungleich = KvnrBsnrHilfen.s3Pruefung(fern, List.of(lanr), List.of(hoeher));
        assertEquals(2, ungleich.geprueft(), "Kontextwort am Textanfang, Fund 500 Zeichen spaeter: geprueft");
        assertEquals(2, ungleich.verstoesse(), "Kontextwort am Textanfang, Fund 500 Zeichen spaeter: Verstoss");
        S3 gleich = KvnrBsnrHilfen.s3Pruefung(fern, List.of(lanr), List.of(lanr));
        assertEquals(2, gleich.geprueft(), "gleiche Listen: geprueft");
        assertEquals(0, gleich.verstoesse(), "gleiche Listen: Verstoss");
        S3 zusaetzlich = KvnrBsnrHilfen.s3Pruefung(fern, alt, List.of(lanr));
        assertEquals(1, zusaetzlich.verstoesse(), "zusaetzlicher neuer Fund ohne Kontext");
        String nah = text("KVNR", fuell(50), " ", LANR_GUELTIG);
        int vonNah = nah.length() - 9;
        S3 mitKontext = KvnrBsnrHilfen.s3Pruefung(nah, List.of(fund("LANR", nah, vonNah, vonNah + 9, 0.95, "lanr")),
                List.of(fund("LANR", nah, vonNah, vonNah + 9, 1.0, "lanr")));
        assertEquals(0, mitKontext.geprueft(), "Kontextwort 50 Zeichen davor: nicht geprueft");
        String bsnr = text(LANR_GUELTIG, fuell(50), " BSNR");
        S3 mitBsnr = KvnrBsnrHilfen.s3Pruefung(bsnr, List.of(fund("LANR", bsnr, 0, 9, 0.95, "lanr")),
                List.of(fund("LANR", bsnr, 0, 9, 1.0, "lanr")));
        assertEquals(0, mitBsnr.geprueft(), "BSNR-Kontextwort 50 Zeichen dahinter: nicht geprueft");
    }

    @Test
    void selbsttest_s3_dieEngeFormNaheBefreit_eineFormAusserhalbDesFenstersNicht() {
        String nah = text("Z", KVNR_ZIFFERN, fuell(50), " ", LANR_GUELTIG);
        int von = nah.length() - 9;
        S3 mitForm = KvnrBsnrHilfen.s3Pruefung(nah, List.of(fund("LANR", nah, von, von + 9, 0.95, "lanr")),
                List.of(fund("LANR", nah, von, von + 9, 1.0, "lanr")));
        assertEquals(0, mitForm.geprueft(), "enge Form 50 Zeichen davor: nicht geprueft");
        String fern = text("Z", KVNR_ZIFFERN, fuell(500), " ", LANR_GUELTIG);
        int vonFern = fern.length() - 9;
        S3 ausserhalb = KvnrBsnrHilfen.s3Pruefung(fern, List.of(fund("LANR", fern, vonFern, vonFern + 9, 0.95,
                "lanr")), List.of(fund("LANR", fern, vonFern, vonFern + 9, 1.0, "lanr")));
        assertEquals(2, ausserhalb.verstoesse(), "enge Form 500 Zeichen davor: geprueft und ungleich");
        String ungueltig = text("Z", KVNR_UNGUELTIG, fuell(50), " ", LANR_GUELTIG);
        int vonUngueltig = ungueltig.length() - 9;
        S3 ohnePruefziffer = KvnrBsnrHilfen.s3Pruefung(ungueltig, List.of(fund("LANR", ungueltig, vonUngueltig,
                vonUngueltig + 9, 0.95, "lanr")), List.of());
        assertEquals(1, ohnePruefziffer.verstoesse(), "Form mit falscher Pruefziffer befreit nicht");
    }

    // ------------------------------------------------------------------ Selbsttests auf Mengenebene

    private static Abweichungen summe(TextScanner neu, List<String> texte) {
        Abweichungen summe = KvnrBsnrHilfen.KEINE;
        for (String text : texte) {
            summe = summe.plus(bestandsabweichungen(text, ALT.scan(text), neu.scan(text)));
        }
        return summe;
    }

    private static List<String> beispieltexte() {
        return List.of(text("LANR ", LANR_GUELTIG), text("IK ", IK_GUELTIG),
                text("LANR ", LANR_GUELTIG, ", IK ", IK_GUELTIG, " und Mail an erika@example.invalid"));
    }

    private static List<String> einfacheTexte() {
        return List.of(text("LANR ", LANR_GUELTIG), text("IK ", IK_GUELTIG));
    }

    private static TextScanner mitKvErkennern(Recognizer lanr, Recognizer ik) {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), lanr, ik));
    }

    /** Ein Erkenner, der die Funde von {@code basis} abwandelt. */
    private static Recognizer abgewandelt(Recognizer basis, Function<Finding, Finding> aenderung) {
        return text -> basis.detect(text).stream().map(aenderung).toList();
    }

    @Test
    void selbsttest_mengenebene_gleicherScannerWirdNichtGemeldet() {
        assertTrue(summe(ALT, beispieltexte()).keine(), "alter gegen alten Scanner");
    }

    @Test
    void selbsttest_mengenebene_scannerOhneKvErkenner_verliertFundeUndAbdeckung() {
        TextScanner ohneKv = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
                new UrlRecognizer(), new PathRecognizer()));
        Abweichungen a = summe(ohneKv, beispieltexte());
        assertEquals(3 + 4, a.verstoesse(), "drei Texte ohne Abdeckung und vier verlorene KV-Funde");
        assertEquals(0, a.kollisionen(), "kein Kollisionsfall");
    }

    @Test
    void selbsttest_mengenebene_abgeschwaechteKonfidenz_wirdGemeldet() {
        Recognizer lanr = abgewandelt(KvStandTrenner.lanr(), f -> new Finding(f.type(), f.originalValue(),
                f.start(), f.end(), f.confidence() - 0.1, f.source()));
        Recognizer ik = abgewandelt(KvStandTrenner.ik(), f -> new Finding(f.type(), f.originalValue(),
                f.start(), f.end(), f.confidence() - 0.1, f.source()));
        Abweichungen a = summe(mitKvErkennern(lanr, ik), beispieltexte());
        assertEquals(4, a.verstoesse(), "alle vier KV-Funde sind abgeschwaecht");
    }

    @Test
    void selbsttest_mengenebene_vertauschterTyp_wirdGemeldet() {
        Recognizer lanrAlsIk = abgewandelt(KvStandTrenner.lanr(), f -> new Finding(FindingType.IK,
                f.originalValue(), f.start(), f.end(), f.confidence(), f.source()));
        Recognizer ikAlsLanr = abgewandelt(KvStandTrenner.ik(), f -> new Finding(FindingType.LANR,
                f.originalValue(), f.start(), f.end(), f.confidence(), f.source()));
        Abweichungen a = summe(mitKvErkennern(lanrAlsIk, ikAlsLanr), beispieltexte());
        assertEquals(4, a.verstoesse(), "alle vier KV-Funde haben den falschen Typ");
        assertEquals(0, a.kollisionen(), "kein Kollisionsfall");
    }

    @Test
    void selbsttest_mengenebene_nichtKvTyp_wirdGemeldetObwohlDiePositionAbgedecktBleibt() {
        Recognizer alsText = abgewandelt(KvStandTrenner.lanr(), f -> new Finding(FindingType.TEXT,
                f.originalValue(), f.start(), f.end(), 1.0, "text"));
        Abweichungen a = summe(mitKvErkennern(alsText, KvStandTrenner.ik()), einfacheTexte());
        assertEquals(1, a.verstoesse(), "der LANR-Fund wird zu einem Nicht-KV-Typ, der IK-Fund bleibt");
    }

    @Test
    void selbsttest_mengenebene_verkuerzterBereich_wirdGemeldet() {
        Recognizer kuerzer = text -> KvStandTrenner.lanr().detect(text).stream().map(f -> new Finding(f.type(),
                f.originalValue().substring(1), f.start() + 1, f.end(), f.confidence(), f.source())).toList();
        Abweichungen a = summe(mitKvErkennern(kuerzer, KvStandTrenner.ik()), einfacheTexte());
        assertEquals(2, a.verstoesse(), "Abdeckungsverlust und ein Fund ohne Entsprechung, der IK-Fund bleibt");
    }

    @Test
    void selbsttest_mengenebene_dasProduktWirdAufDenBeispieltextenNichtGemeldet() {
        assertTrue(summe(NEU, beispieltexte()).keine(), "Produkt gegen die Kopie");
    }

    // ------------------------------------------------------------------ Selbsttests der Referenzen

    @Test
    void selbsttest_kvnrKontextReferenz_erkenntDieKontextwoerterUndGrenztAb() {
        for (String ja : new String[] {"KVNR", "kvnr:", "K V N R", "K. V. N. R.", "KV-Nr.", "KV-Nummer", "KV Nr.",
            "Versicherten-Nr.", "Krankenversicherten-Nummer", "eGK-Nummer", "eGK-Nr.", "Patienten-KVNR",
            "Versichertennummer", "Krankenversicherungsnummer"}) {
            assertTrue(kvnrKontextReferenz("Patient, " + ja + " folgt"), "Kontextwort wird nicht erkannt: " + ja);
        }
        for (String nein : new String[] {"VKNR", "Versicherungsnummer", "Rentenversicherungsnummer", "Die KV zahlt",
            "KV allein", "Nr. allein", "LANR", "IK", "BSNR"}) {
            assertFalse(kvnrKontextReferenz("Patient, " + nein + " folgt"), "Kein Kontextwort: " + nein);
        }
        assertFalse(kvnrKontextReferenz(""), "leerer Text");
        assertFalse(kvnrKontextReferenz(text("LANR ", LANR_GUELTIG)), "Text ohne Kontextwort");
    }

    @Test
    void selbsttest_bsnrKontextReferenz_erkenntDieKontextwoerterInBeidenSchreibweisen() {
        String mitUmlaut = "Betriebsst" + AE_KLEIN + "tten-Nr.";
        String grossMitUmlaut = "BETRIEBSST" + AE_GROSS + "TTENNUMMER";
        String ohneUmlaut = "Betriebsstaetten-Nr.";
        for (String ja : new String[] {"BSNR", "B S N R", "B. S. N. R.", "N B S N R", "NBSNR", mitUmlaut,
            grossMitUmlaut, ohneUmlaut, "Betriebsst" + AE_KLEIN + "tte Nr.", "Betriebsstaette Nummer",
            "Nebenbetriebsst" + AE_KLEIN + "ttennummer", "Nebenbetriebsstaetten-Nr."}) {
            assertTrue(bsnrKontextReferenz("Praxis " + ja + " folgt"), "Kontextwort wird nicht erkannt: " + ja);
        }
        for (String nein : new String[] {"Betrieb", "Betriebsst" + AE_KLEIN + "tte", "Rueckruf unter", "KVNR",
            "LANR"}) {
            assertFalse(bsnrKontextReferenz("Praxis " + nein + " folgt"), "Kein Kontextwort: " + nein);
        }
        assertFalse(bsnrKontextReferenz(""), "leerer Text");
    }

    @Test
    void selbsttest_kvnrFormReferenz_verlangtGrossbuchstabeUndGenauNeunZiffernHintereinander() {
        assertTrue(kvnrFormReferenz(text("x Z", LANR_GUELTIG, " y")), "Grossbuchstabe und neun Ziffern");
        assertTrue(kvnrFormReferenz(text("Z", IK_GUELTIG)), "am Textanfang");
        assertTrue(kvnrFormReferenz(text("xA", LANR_GUELTIG, "y")), "ohne Wortgrenzen (Ueberschaetzung)");
        assertFalse(kvnrFormReferenz(text("A", LANR_GUELTIG, "1")), "zehn Ziffern");
        assertFalse(kvnrFormReferenz(text("A", LANR_GUELTIG, "12345")), "vierzehn Ziffern");
        assertFalse(kvnrFormReferenz(text("z", LANR_GUELTIG)), "Kleinbuchstabe");
        assertFalse(kvnrFormReferenz(text("Z ", LANR_GUELTIG)), "Trenner zwischen Buchstabe und Ziffern");
        assertFalse(kvnrFormReferenz(text("Z-", LANR_GUELTIG)), "Bindestrich zwischen Buchstabe und Ziffern");
        assertFalse(kvnrFormReferenz(text("Z", LAENGE_8)), "nur acht Ziffern");
        assertFalse(kvnrFormReferenz(text("Z", LANR_GUELTIG.substring(0, 4), " ", LANR_GUELTIG.substring(4))),
                "Ziffern mit Trenner");
        assertFalse(kvnrFormReferenz(LANR_GUELTIG), "Ziffern ohne Buchstabe");
        assertFalse(kvnrFormReferenz(""), "leerer Text");
        assertFalse(kvnrFormReferenz("Z"), "ein Zeichen");
    }

    @Test
    void selbsttest_kvnrPruefziffer_lehrbeispielDerQuelleUndWeitereVektoren() {
        // Lehrbeispiel der Quelle (Anforderung, Nachtrag zu OF-1): A und die Ziffern eins bis acht, Pruefziffer 0.
        assertEquals(0, KvnrBsnrHilfen.kvnrPruefziffer('A', "1234" + "5678"), "Lehrbeispiel der Quelle");
        assertTrue(("1234" + "5678" + "0").equals(KvnrBsnrHilfen.kvnrNeunZiffern('A', "1234" + "5678")),
                "neun Ziffern: acht Ziffern und Pruefziffer");
        // Z (26) mit 00123510: Buchstabenwert 2, 6; Produkte 2, 12, 0, 0, 1, 4, 3, 10, 1, 0; Quersummen 2, 3, 0, 0,
        // 1, 4, 3, 1, 1, 0; Summe 15; Pruefziffer 5.
        assertEquals(5, KvnrBsnrHilfen.kvnrPruefziffer('Z', "00123510"), "Buchstabe Z, Produkte ueber 9");
        assertTrue(KvnrBsnrHilfen.kvnrPruefziffer('A', "1234" + "5678") != KvnrBsnrHilfen.kvnrPruefziffer('B',
                "1234" + "5678"), "der Buchstabe geht in die Rechnung ein");
    }

    @Test
    void selbsttest_kvnrFormEng_verlangtWortgrenzenUndGueltigePruefziffer() {
        String lehr = "A" + KvnrBsnrHilfen.kvnrNeunZiffern('A', "1234" + "5678");
        assertTrue(KvnrBsnrHilfen.kvnrFormEng(lehr), "Lehrbeispiel der Quelle");
        assertTrue(List.of(new KvnrBsnrHilfen.Bereich(2, 12)).equals(
                KvnrBsnrHilfen.kvnrFormEngTreffer(text("x Z", KVNR_ZIFFERN, "."))),
                "Bereich: Buchstabe und neun Ziffern");
        assertTrue(KvnrBsnrHilfen.kvnrFormEng(text("x ", "Z", KVNR_ZIFFERN, ",")), "Satzzeichen dahinter");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("Z", KVNR_UNGUELTIG)), "falsche Pruefziffer");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("xZ", KVNR_ZIFFERN)), "Buchstabe davor");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("7Z", KVNR_ZIFFERN)), "Ziffer davor");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("Z", KVNR_ZIFFERN, "x")), "Buchstabe dahinter");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("Z", KVNR_ZIFFERN, "7")), "Ziffer dahinter");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("z", KVNR_ZIFFERN)), "Kleinbuchstabe");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("Z ", KVNR_ZIFFERN)), "Trenner zwischen Buchstabe und Ziffern");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(text("Z", KVNR_ZIFFERN.substring(1))), "nur acht Ziffern");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(KVNR_ZIFFERN), "Ziffern ohne Buchstabe");
        assertFalse(KvnrBsnrHilfen.kvnrFormEng(""), "leerer Text");
    }

    @Test
    void selbsttest_umlautKonstanten() {
        assertEquals(0xE4, AE_KLEIN, "kleines a mit Umlaut");
        assertEquals(0xC4, AE_GROSS, "grosses A mit Umlaut");
    }
}
