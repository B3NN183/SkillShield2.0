package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumFolgeMessung.Ergebnis;
import de.skillshield.core.DatumFolgeMessung.Z;
import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Differenznachweis alt gegen neu auf der Menge 1i ({@link DatumFuzzMengen}) fuer Teil A des Features
 * datum-und-kontextvorrang (T-3, Nachtrag 3 der Hauptsitzung vom 25.11.2026).
 *
 * <p><b>Massgeblich</b> ist die exakte Gleichheit: das Produkt ({@code TextScanner.standard()}) liefert je Text
 * dieselbe Fundliste wie {@code KvStandDatum.simuliert(DatumReferenz...)}. Daneben gelten als harte Bedingungen gegen
 * {@code ALT_DATUM}: keine Position ausserhalb der Datumssegmente der Referenz verliert ihren Schutz, keine Konfidenz
 * sinkt an einer weiterhin geschuetzten Position, der Roundtrip ist zeichengleich.
 *
 * <p><b>Klasse A</b> (entfallene Funde ueber Datumssegmenten) wird mit der eingefrorenen {@link DatumMessung}
 * gezaehlt, <b>Klasse Z</b> (Folgeaenderungen neben einem entfallenen Datum) mit {@link DatumFolgeMessung}, getrennt
 * nach Art. Die Zahlen sind exakte Messwerte dieser Menge (fester Seed), keine Schwellen; weicht eine ab, hat sich
 * Produkt oder Erzeugung geaendert und die Abweichung ist zu erklaeren, nicht anzupassen. Meldungen nennen nur Zahlen.
 *
 * <p>Die Klasse Z stammt aus der bestehenden Konfliktaufloesung und dem Zuschnitt am Bestand, nicht aus dem neuen
 * Code: Faellt ein Datumssegment weg, ist ein mit ihm zusammengelegter oder zugeschnittener Fund anders geschnitten;
 * ein vorher verdeckter KV-Kandidat kann ueber einem Teil des Datums neu erscheinen. Geschuetzt wird dadurch nie
 * weniger als zuvor ausserhalb der Datumssegmente.
 */
class DatumFuzzDifferenzTest {

    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner SIMULIERT = KvStandDatum.simuliert(
            (text, f) -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));

    /** Zahl der Texte der Menge 1i (Istwert, fest durch den Seed). */
    private static final int TEXTE = 60_000;

    /** Klasse A: entfallene Funde ueber Datumssegmenten (Istwert, Menge 1i). */
    private static final int ENTFALLEN = 37_439;

    /** Klasse Z, Art "gleicher Bereich, anderer Typ" (Istwert, Menge 1i). */
    private static final int Z_TYP = 0;
    /** Klasse Z, Art "gleicher Bereich und Typ, andere Konfidenz" (Istwert, Menge 1i). */
    private static final int Z_KONFIDENZ = 0;
    /** Klasse Z, Art "nur die Quelle anders" (zusammengelegt wird zur Quelle des Kandidaten; Istwert, Menge 1i). */
    private static final int Z_QUELLE = 0;
    /** Klasse Z, Art "alter Fund hat in der neuen Liste einen anderen Bereich" (Istwert, Menge 1i). */
    private static final int Z_ALT_BEREICH = 13;
    /** Klasse Z, Art "neuer Fund ganz innerhalb eines entfallenen Datumsfunds" (Istwert, Menge 1i). */
    private static final int Z_UEBER_DATUM = 19;
    /** Klasse Z, Art "neuer Fund ganz innerhalb eines anderen alten Funds" (Reststueck; Istwert, Menge 1i). */
    private static final int Z_RESTSTUECK = 7;
    /** Klasse Z, Art "neuer Fund beruehrt keinen alten Fund" (Istwert, Menge 1i). */
    private static final int Z_NEU_FREI = 0;
    /** Klasse Z, Art "neuer Fund mit anderem Bereich, der in keinen alten Fund passt" (Istwert, Menge 1i). */
    private static final int Z_SONST = 0;

    /** Zahl der Datumssegmente der Referenz in der Menge (Istwert). */
    private static final int DATUM_SEGMENTE = 40_136;
    /** Davon Segmente, die ein KV-Fund des Produkts noch ganz oder teilweise schuetzt (Istwert). */
    private static final int DATUM_SEGMENTE_GESCHUETZT = 68;
    /** Positionen innerhalb der Datumssegmente, die ein KV-Fund des Produkts noch schuetzt (Istwert). */
    private static final int DATUM_POSITIONEN_GESCHUETZT = 645;
    /** Texte, auf denen ALT_DATUM und Produkt verschiedene Listen liefern (Istwert). */
    private static final int TEXTE_MIT_ABWEICHUNG = 22_405;
    /** Funde des Produkts, deren Bereich selbst ein Datumssegment ist (Reststuecke gleich dem Datum; Istwert). */
    private static final int PRODUKT_FUNDE_UEBER_DATUM = 172;

    /** Texte mit mindestens einem IP-Fund des Produkts und mindestens einem Datumssegment (Istwert, Menge 1i). */
    private static final int TEXTE_DATUM_MIT_IP = 3_025;
    /** Texte mit mindestens einem Pfad-Fund des Produkts und mindestens einem Datumssegment (Istwert). */
    private static final int TEXTE_DATUM_MIT_PFAD = 5_636;
    /** Texte mit mindestens einem URL-Fund des Produkts und mindestens einem Datumssegment (Istwert). */
    private static final int TEXTE_DATUM_MIT_URL = 2_367;
    /** Texte mit mindestens einem E-Mail-Fund des Produkts und mindestens einem Datumssegment (Istwert). */
    private static final int TEXTE_DATUM_MIT_EMAIL = 2_269;

    private static List<String> menge;
    private static Lauf lauf;

    /** Summe aller Messungen der Menge. */
    private static final class Lauf {
        Ergebnis summe = Ergebnis.KEIN;
        Vergleich klasseA = Vergleich.KEIN;
        int ungleichSimulation;
        int roundtripUngleich;
        int roundtripUnaufgeloest;
        int produktUnsortiert;
        int produktFundeUeberDatum;
        int funde;
        int datumMitIp;
        int datumMitPfad;
        int datumMitUrl;
        int datumMitEmail;
    }

    private static boolean hatFund(List<Finding> funde, FindingType typ) {
        return funde.stream().anyMatch(f -> f.type() == typ);
    }

    private static synchronized List<String> menge() {
        if (menge == null) {
            menge = DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I);
        }
        return menge;
    }

    private static boolean sortiertUeberlappungsfrei(String text, List<Finding> funde) {
        int voriges = 0;
        for (Finding f : funde) {
            boolean passt = f.start() >= voriges && f.start() < f.end() && f.end() <= text.length()
                    && text.substring(f.start(), f.end()).equals(f.originalValue());
            if (!passt) {
                return false;
            }
            voriges = f.end();
        }
        return true;
    }

    private static synchronized Lauf lauf() {
        if (lauf == null) {
            Pseudonymizer pseudonymizer = new Pseudonymizer();
            Reidentifier reidentifier = new Reidentifier();
            Lauf l = new Lauf();
            for (String text : menge()) {
                List<Finding> alt = ALT.scan(text);
                List<Finding> neu = NEU.scan(text);
                l.funde += neu.size();
                l.ungleichSimulation += neu.equals(SIMULIERT.scan(text)) ? 0 : 1;
                l.produktUnsortiert += sortiertUeberlappungsfrei(text, neu) && neu.equals(NEU.scan(text)) ? 0 : 1;
                Ergebnis e = DatumFolgeMessung.messe(text, alt, neu);
                l.summe = l.summe.plus(e);
                boolean mitDatum = e.datumSegmente() > 0;
                l.datumMitIp += mitDatum && hatFund(neu, FindingType.IP) ? 1 : 0;
                l.datumMitPfad += mitDatum && hatFund(neu, FindingType.PFAD) ? 1 : 0;
                l.datumMitUrl += mitDatum && hatFund(neu, FindingType.URL) ? 1 : 0;
                l.datumMitEmail += mitDatum && hatFund(neu, FindingType.EMAIL) ? 1 : 0;
                l.klasseA = l.klasseA.plus(DatumMessung.vergleiche(text, alt, neu));
                for (Finding f : neu) {
                    boolean datum = DatumMessung.istKv(f) && DatumReferenz.istDatumssegment(text, f.start(), f.end());
                    l.produktFundeUeberDatum += datum ? 1 : 0;
                }
                PseudonymizationResult p = pseudonymizer.pseudonymize(text, neu);
                var zurueck = reidentifier.restore(p.text(), p.mapping());
                l.roundtripUngleich += text.equals(zurueck.text()) ? 0 : 1;
                l.roundtripUnaufgeloest += zurueck.unresolved().isEmpty() ? 0 : 1;
            }
            lauf = l;
        }
        return lauf;
    }

    // ------------------------------------------------------------------ die Menge

    /** Die Menge ist deterministisch, hat den erwarteten Umfang und enthaelt viele Datumssegmente. */
    @Test
    void mengeI_deterministischUndGross_mitVielenDatumssegmenten() {
        List<String> a = menge();
        assertEquals(TEXTE, a.size(), "Zahl der Texte");
        assertEquals(DatumFuzzMengen.ANZAHL_TEXTE, a.size(), "Zahl der Texte laut Generator");
        assertTrue(a.equals(DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I)),
                "gleicher Seed, gleiche Folge");
        assertFalse(a.equals(DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I + 1)),
                "anderer Seed, andere Folge");
        assertEquals(DATUM_SEGMENTE, lauf().summe.datumSegmente(), "Datumssegmente der Referenz");
        assertTrue(lauf().funde > 0, "Funde des Produkts");
    }

    /**
     * Vorbedingung des Nachweises "kein Schutzverlust neben Bestandstypen": Die Menge enthaelt Datumssegmente im selben
     * Text wie Funde der Bestandstypen IP, Pfad, URL und E-Mail (Istwerte je Typ, Menge 1i). Entfielen diese Zahlen,
     * wuerde die Bedingung "kein Verlust" nichts mehr pruefen.
     */
    @Test
    void mengeI_datumsegmenteNebenBestandstypen_vorbedingung() {
        Lauf l = lauf();
        assertEquals(TEXTE_DATUM_MIT_IP, l.datumMitIp, "Texte mit Datumssegment und IP-Fund");
        assertEquals(TEXTE_DATUM_MIT_PFAD, l.datumMitPfad, "Texte mit Datumssegment und Pfad-Fund");
        assertEquals(TEXTE_DATUM_MIT_URL, l.datumMitUrl, "Texte mit Datumssegment und URL-Fund");
        assertEquals(TEXTE_DATUM_MIT_EMAIL, l.datumMitEmail, "Texte mit Datumssegment und E-Mail-Fund");
    }

    // ------------------------------------------------------------------ massgeblich: Produkt gleich Simulation

    /** Je Text ist die Fundliste des Produkts gleich der der Simulation von Teil A (ganze Listen, alle Felder). */
    @Test
    void mengeI_produktGleichSimulation_jeText() {
        assertEquals(0, lauf().ungleichSimulation, "Texte, auf denen Produkt und Simulation verschieden sind");
    }

    /** Die Listen des Produkts sind sortiert, ueberlappungsfrei, passen zum Text und sind deterministisch. */
    @Test
    void mengeI_produktSortiertUeberlappungsfreiDeterministisch() {
        assertEquals(0, lauf().produktUnsortiert, "Texte mit unsortierter oder abweichender Liste");
    }

    // ------------------------------------------------------------------ harte Bedingungen gegen ALT_DATUM

    /** Keine Position ausserhalb der Datumssegmente verliert ihren Schutz; keine Konfidenz sinkt. */
    @Test
    void mengeI_harteBedingungen_keinSchutzverlustKeineSinkendeKonfidenz() {
        Ergebnis e = lauf().summe;
        assertEquals(0, e.verlorenePositionen(), "Positionen ausserhalb der Datumssegmente ohne Schutz: " + e.kurz());
        assertEquals(0, e.konfidenzGesunken(), "Positionen mit gesunkener Konfidenz: " + e.kurz());
        assertEquals(0, e.rohBestandUngeschuetzt(), "Positionen roher Pfad-, E-Mail-, IP-, URL-Funde ohne Schutz: "
                + e.kurz());
        assertTrue(e.hartErfuellt(), "harte Bedingungen");
        assertEquals(0, e.neuGeschuetzt(), "Positionen, die neu geschuetzt sind und alt nicht: " + e.kurz());
    }

    /** Pseudonymisieren und Zurueckuebersetzen ergibt je Text denselben Text, ohne offene Platzhalter. */
    @Test
    void mengeI_roundtripZeichengleich_jeText() {
        assertEquals(0, lauf().roundtripUngleich, "Texte mit abweichendem Roundtrip");
        assertEquals(0, lauf().roundtripUnaufgeloest, "Texte mit nicht aufgeloesten Platzhaltern");
    }

    /** Ohne Datumssegment im Text liefern ALT_DATUM und Produkt dieselbe Liste. */
    @Test
    void mengeI_textOhneDatumssegment_keinUnterschied() {
        assertEquals(0, lauf().summe.abweichungOhneDatum(), "Texte ohne Datumssegment, aber mit Abweichung");
    }

    // ------------------------------------------------------------------ Zahlen: Klasse A und Klasse Z

    /** Klasse A: die entfallenen Funde ueber Datumssegmente, beide Messwege stimmen ueberein. */
    @Test
    void mengeI_klasseA_entfalleneDatumsfunde_exakteZahl() {
        Lauf l = lauf();
        assertEquals(ENTFALLEN, l.klasseA.datumEntfallen(), "entfallene Datumsfunde (DatumMessung)");
        assertEquals(ENTFALLEN, l.summe.datumEntfallen(), "entfallene Datumsfunde (DatumFolgeMessung)");
        assertEquals(TEXTE_MIT_ABWEICHUNG, l.summe.texteMitAbweichung(), "Texte mit Abweichung");
    }

    /** Klasse Z: die Folgeaenderungen neben einem entfallenen Datum, je Art exakt. */
    @Test
    void mengeI_klasseZ_folgeaenderungen_exakteZahlenJeArt() {
        Z z = lauf().summe.z();
        assertEquals(Z_TYP, z.typ(), "gleicher Bereich, anderer Typ: " + z.kurz());
        assertEquals(Z_KONFIDENZ, z.konfidenz(), "gleicher Bereich und Typ, andere Konfidenz: " + z.kurz());
        assertEquals(Z_QUELLE, z.quelle(), "nur die Quelle anders: " + z.kurz());
        assertEquals(Z_ALT_BEREICH, z.altBereich(), "alter Fund mit anderem Bereich: " + z.kurz());
        assertEquals(Z_UEBER_DATUM, z.ueberDatum(), "neuer Fund ueber einem Teil eines Datums: " + z.kurz());
        assertEquals(Z_RESTSTUECK, z.reststueck(), "neues Reststueck eines alten Funds: " + z.kurz());
        assertEquals(Z_NEU_FREI, z.neuFrei(), "neuer Fund ohne alten Fund: " + z.kurz());
        assertEquals(Z_SONST, z.sonst(), "neuer Fund sonstiger Art: " + z.kurz());
    }

    /**
     * Bekannte Grenze (Nachtrag 3): Ein Datumssegment bleibt ganz oder teilweise Fund, wenn ein laengerer
     * KV-Kandidat am Bestand zugeschnitten wird und das Reststueck genau das Datum ist, oder wenn ein vorher
     * verdeckter Kandidat ueber einem Teil des Datums erscheint. Gemessen auf der Menge 1i.
     */
    @Test
    void bekannteGrenze_datumBleibtTeilweiseFund_zahlenAufDerMenge() {
        Ergebnis e = lauf().summe;
        assertEquals(DATUM_SEGMENTE_GESCHUETZT, e.datumSegmenteGeschuetzt(), "Datumssegmente mit KV-Fund: " + e.kurz());
        assertEquals(DATUM_POSITIONEN_GESCHUETZT, e.datumPositionenGeschuetzt(), "Positionen: " + e.kurz());
        assertEquals(PRODUKT_FUNDE_UEBER_DATUM, lauf().produktFundeUeberDatum, "Funde mit Datumsbereich im Produkt");
        assertTrue(e.datumSegmenteGeschuetzt() * 100 < e.datumSegmente(), "unter einem Prozent der Datumssegmente");
    }

    /** Bekannte Grenze (Nachtrag 3), Einzelfall: das Reststueck eines zugeschnittenen Kandidaten ist das Datum. */
    @Test
    void bekannteGrenze_reststueckIstGenauDasDatum_bleibtFund() {
        String ip = "192.0.2.17";
        String text = "KVNR " + SynthetischeDaten.DATUM_PUNKT + " " + ip;
        List<Finding> alt = ALT.scan(text);
        List<Finding> neu = NEU.scan(text);
        int start = text.indexOf(SynthetischeDaten.DATUM_PUNKT);
        int ende = start + SynthetischeDaten.DATUM_PUNKT.length();
        assertTrue(alt.equals(neu), "ALT_DATUM und Produkt liefern dieselbe Liste");
        assertTrue(neu.stream().anyMatch(f -> f.type() == FindingType.KVNR && f.start() == start && f.end() == ende),
                "KVNR-Fund genau ueber dem Datum");
        assertTrue(neu.stream().anyMatch(f -> f.type() == FindingType.IP), "IP-Fund bleibt");
        assertTrue(DatumReferenz.istDatumssegment(text, start, ende), "der Bereich ist ein Datumssegment");
    }

    /**
     * Bekannte Grenze (Neun-Ziffern-Regel, in T-3 an der Grenzlast beobachtet): Erreicht der Praefix eines Zeitraums
     * genau neun Ziffern, bleibt dieser Praefix Fund; der Rest des Zeitraums ist dann ein eigenes Stueck und wird Fund,
     * wenn ein nachgestelltes Kontextwort der naechsten Angabe folgt. Das Produkt verhaelt sich dabei wie die
     * Simulation von Teil A, es entfaellt nur ein Teil der Funde.
     */
    @Test
    void bekannteGrenze_zeitraumMitNeunZiffernPraefix_restWirdUeberNachgestelltesKontextwortFund() {
        String text = "KVNR 09.6.2084-24.02.2085, BSNR";
        List<Finding> neu = NEU.scan(text);
        int praefixEnde = text.indexOf("-24") + "-24".length();
        int restStart = text.indexOf("02.2085");
        assertTrue(neu.stream().anyMatch(f -> f.type() == FindingType.KVNR && f.start() == 5
                && f.end() == praefixEnde), "KVNR-Fund ueber dem Neun-Ziffern-Praefix");
        assertTrue(neu.stream().anyMatch(f -> f.type() == FindingType.BSNR && f.start() == restStart
                && f.end() == restStart + "02.2085".length()), "BSNR-Fund ueber dem Rest des Zeitraums");
        assertTrue(neu.equals(SIMULIERT.scan(text)), "Produkt gleich Simulation");
    }

    // ------------------------------------------------------------------ Selbsttests der Messhilfe

    private static final String FUELLER = "x".repeat(40);

    private static Finding fund(FindingType typ, int start, int end, double konfidenz, String quelle) {
        return new Finding(typ, FUELLER.substring(start, end), start, end, konfidenz, quelle);
    }

    /** Selbsttest: gleiche Listen ergeben keine Abweichung. */
    @Test
    void selbsttest_gleicheListen_keinUnterschied() {
        List<Finding> liste = List.of(fund(FindingType.LANR, 2, 11, 0.8, "lanr"));
        Ergebnis e = DatumFolgeMessung.messe(FUELLER, liste, liste);
        assertTrue(Ergebnis.KEIN.equals(e), e.kurz());
    }

    /** Selbsttest: Typ-, Konfidenz- und Quellenwechsel bei gleichem Bereich werden getrennt gezaehlt. */
    @Test
    void selbsttest_gleicherBereich_typKonfidenzQuelle_getrennt() {
        Finding alt = fund(FindingType.LANR, 2, 11, 0.5, "lanr");
        Z typ = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of(fund(FindingType.IK, 2, 11, 0.5, "ik"))).z();
        Z konfidenz = DatumFolgeMessung.messe(FUELLER, List.of(alt),
                List.of(fund(FindingType.LANR, 2, 11, 0.8, "lanr"))).z();
        Z quelle = DatumFolgeMessung.messe(FUELLER, List.of(alt),
                List.of(fund(FindingType.LANR, 2, 11, 0.5, "lanr-rest"))).z();
        assertEquals(new Z(1, 0, 0, 0, 0, 0, 0, 0), typ, typ.kurz());
        assertEquals(new Z(0, 1, 0, 0, 0, 0, 0, 0), konfidenz, konfidenz.kurz());
        assertEquals(new Z(0, 0, 1, 0, 0, 0, 0, 0), quelle, quelle.kurz());
    }

    /** Selbsttest: ein verkuerzter Fund zaehlt als anderer alter Bereich und als Reststueck. */
    @Test
    void selbsttest_verkuerzterFund_altBereichUndReststueck() {
        Finding alt = fund(FindingType.LANR, 2, 12, 0.5, "lanr");
        Finding neu = fund(FindingType.LANR, 2, 7, 0.5, "lanr-rest");
        Ergebnis e = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of(neu));
        assertEquals(new Z(0, 0, 0, 1, 0, 1, 0, 0), e.z(), e.kurz());
        assertEquals(5, e.verlorenePositionen(), "fuenf Positionen ohne Schutz: " + e.kurz());
    }

    /** Selbsttest: Verlust, sinkende Konfidenz und neu geschuetzte Position werden gemeldet. */
    @Test
    void selbsttest_verlustSinkendeKonfidenzNeuGeschuetzt_werdenGemeldet() {
        Finding alt = fund(FindingType.LANR, 2, 11, 0.8, "lanr");
        Ergebnis verlust = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of());
        assertEquals(9, verlust.verlorenePositionen(), verlust.kurz());
        assertFalse(verlust.hartErfuellt(), "Verlust ist nie zulaessig");
        Ergebnis schwaecher = DatumFolgeMessung.messe(FUELLER, List.of(alt),
                List.of(fund(FindingType.LANR, 2, 11, 0.5, "lanr")));
        assertEquals(9, schwaecher.konfidenzGesunken(), schwaecher.kurz());
        assertFalse(schwaecher.hartErfuellt(), "sinkende Konfidenz ist nie zulaessig");
        Ergebnis mehr = DatumFolgeMessung.messe(FUELLER, List.of(alt),
                List.of(alt, fund(FindingType.IK, 20, 25, 0.4, "ik")));
        assertEquals(5, mehr.neuGeschuetzt(), mehr.kurz());
        assertEquals(new Z(0, 0, 0, 0, 0, 0, 1, 0), mehr.z(), mehr.kurz());
    }

    /** Selbsttest: ein entfallener Datumsfund ist Klasse A, kein Verlust, und kein Z; Datumspositionen sind frei. */
    @Test
    void selbsttest_entfallenerDatumsfund_istKlasseAUndKeinVerlust() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT;
        List<Finding> alt = ALT.scan(text);
        assertEquals(1, alt.size(), "heute ein Fund");
        Ergebnis e = DatumFolgeMessung.messe(text, alt, List.of());
        assertEquals(1, e.datumEntfallen(), e.kurz());
        assertEquals(Z.KEIN, e.z(), e.kurz());
        assertTrue(e.hartErfuellt(), e.kurz());
        assertEquals(1, e.datumSegmente(), e.kurz());
        assertEquals(0, e.datumSegmenteGeschuetzt(), e.kurz());
    }

    /** Selbsttest: ein neuer Fund ganz im entfallenen Datumsfund ist Art "ueberDatum". */
    @Test
    void selbsttest_neuerFundImEntfallenenDatum_istUeberDatum() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT;
        List<Finding> alt = ALT.scan(text);
        Finding teil = new Finding(FindingType.KVNR, text.substring(8, 15), 8, 15, 0.4, "kvnr-form-abweichend");
        Ergebnis e = DatumFolgeMessung.messe(text, alt, List.of(teil));
        assertEquals(new Z(0, 0, 0, 0, 1, 0, 0, 0), e.z(), e.kurz());
        assertEquals(1, e.datumEntfallen(), e.kurz());
        assertEquals(1, e.datumSegmenteGeschuetzt(), e.kurz());
        assertEquals(7, e.datumPositionenGeschuetzt(), e.kurz());
    }

    /** Selbsttest: abweichende Listen ueber einem Text ohne Datumssegment zaehlen als Abweichung ohne Datum. */
    @Test
    void selbsttest_abweichungOhneDatumssegment_wirdGezaehlt() {
        Finding alt = fund(FindingType.LANR, 2, 11, 0.8, "lanr");
        Ergebnis e = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of(fund(FindingType.IK, 2, 11, 0.8, "ik")));
        assertEquals(0, e.datumSegmente(), e.kurz());
        assertEquals(1, e.texteMitAbweichung(), e.kurz());
        assertEquals(1, e.abweichungOhneDatum(), e.kurz());
    }

    /** Selbsttest: abweichende Listen ueber einem Text mit Datumssegment zaehlen nicht als Abweichung ohne Datum. */
    @Test
    void selbsttest_abweichungMitDatumssegment_zaehltNichtOhneDatum() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT;
        Ergebnis e = DatumFolgeMessung.messe(text, ALT.scan(text), List.of());
        assertEquals(1, e.datumSegmente(), e.kurz());
        assertEquals(1, e.texteMitAbweichung(), e.kurz());
        assertEquals(0, e.abweichungOhneDatum(), e.kurz());
    }

    /** Selbsttest: ein neuer Fund, der einen alten nur teilweise ueberdeckt, ist Art "sonst". */
    @Test
    void selbsttest_neuerFundTeilweiseUeberAltem_istSonst() {
        Finding alt = fund(FindingType.LANR, 2, 11, 0.5, "lanr");
        Finding neu = fund(FindingType.IK, 8, 15, 0.5, "ik");
        Ergebnis e = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of(neu));
        assertEquals(new Z(0, 0, 0, 1, 0, 0, 0, 1), e.z(), e.kurz());
    }

    /** Selbsttest: ein alter Fund ohne neuen Fund und ohne Datum ist allein Art "altBereich". */
    @Test
    void selbsttest_alterFundOhneNeuen_istNurAltBereich() {
        Finding alt = fund(FindingType.LANR, 2, 11, 0.5, "lanr");
        Ergebnis e = DatumFolgeMessung.messe(FUELLER, List.of(alt), List.of());
        assertEquals(new Z(0, 0, 0, 1, 0, 0, 0, 0), e.z(), e.kurz());
    }

    /** Selbsttest: das Datumssegment ist genau das Datum, nicht eine Position mehr oder weniger. */
    @Test
    void selbsttest_datumssegment_istGenauDasDatum() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT + " xxxxx";
        List<int[]> segmente = DatumFolgeMessung.datumSegmente(text);
        int start = text.indexOf(SynthetischeDaten.DATUM_PUNKT);
        assertEquals(1, segmente.size(), "ein Datumssegment");
        assertEquals(start, segmente.get(0)[0], "Beginn des Segments");
        assertEquals(start + SynthetischeDaten.DATUM_PUNKT.length(), segmente.get(0)[1], "Ende des Segments");
    }

    /** Selbsttest: Schutzverlust direkt neben dem Datum, im selben Text, wird gemeldet. */
    @Test
    void selbsttest_schutzverlustDirektNebenDatum_wirdGemeldet() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT + " xxxxx";
        int ende = text.indexOf(SynthetischeDaten.DATUM_PUNKT) + SynthetischeDaten.DATUM_PUNKT.length();
        Finding nachbar = new Finding(FindingType.LANR, text.substring(ende), ende, text.length(), 0.8, "lanr");
        List<Finding> alt = List.of(ALT.scan(text).get(0), nachbar);
        Ergebnis e = DatumFolgeMessung.messe(text, alt, List.of());
        assertEquals(1, e.datumEntfallen(), e.kurz());
        assertEquals(text.length() - ende, e.verlorenePositionen(), e.kurz());
        assertFalse(e.hartErfuellt(), "Verlust neben dem Datum ist nie zulaessig");
    }

    /** Selbsttest: Schutzverlust direkt vor dem Datum, im selben Text, wird gemeldet. */
    @Test
    void selbsttest_schutzverlustDirektVorDatum_wirdGemeldet() {
        String text = "BSNR " + SynthetischeDaten.DATUM_PUNKT + " xxxxx";
        int start = text.indexOf(SynthetischeDaten.DATUM_PUNKT);
        Finding nachbar = new Finding(FindingType.LANR, text.substring(0, start), 0, start, 0.8, "lanr");
        List<Finding> alt = List.of(nachbar, ALT.scan(text).get(0));
        Ergebnis e = DatumFolgeMessung.messe(text, alt, List.of());
        assertEquals(1, e.datumEntfallen(), e.kurz());
        assertEquals(start, e.verlorenePositionen(), e.kurz());
        assertFalse(e.hartErfuellt(), "Verlust vor dem Datum ist nie zulaessig");
    }

    /** Selbsttest (Mutante): bleibt eine Position eines rohen IP-Funds in der neuen Liste frei, wird sie gemeldet. */
    @Test
    void selbsttest_rohBestandsfundOhneSchutz_wirdGemeldet() {
        String text = "Host 192.0.2.17 erreichbar";
        List<Finding> richtig = NEU.scan(text);
        Ergebnis gut = DatumFolgeMessung.messe(text, ALT.scan(text), richtig);
        assertEquals(0, gut.rohBestandUngeschuetzt(), gut.kurz());
        assertTrue(gut.hartErfuellt(), gut.kurz());
        Ergebnis mutante = DatumFolgeMessung.messe(text, ALT.scan(text), List.of());
        assertEquals("192.0.2.17".length(), mutante.rohBestandUngeschuetzt(), mutante.kurz());
        assertFalse(mutante.hartErfuellt(), "ein freier roher Bestandsfund ist nie zulaessig");
    }
}
