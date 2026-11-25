package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.TextScannerStandGeheimnis;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Differenztest alt gegen neu fuer das Feature geheimnisse-erkennung (Plan Kapitel 7.2; Anforderung AK-31): Er
 * vergleicht {@code TextScanner.standard()} mit der eingefrorenen Kopie {@link TextScannerStandGeheimnis#ALT} auf allen
 * Bestandsmengen ({@link DatumMengen}: Fuzz-Teile 1 bis 1h, Korpora, Generatoren; dazu die Menge 1i aus
 * {@code DatumFuzzMengen.erzeugeKvI}, 60 000 Texte). Je Text gilt:
 * <ol>
 * <li>jeder Fund von ALT steht feldgleich in der neuen Liste;</li>
 * <li>jeder weitere Fund hat den Typ PASSWORT oder TOKEN und ueberlappt keinen Fund von ALT;</li>
 * <li>die Abdeckung der neuen Liste enthaelt die von ALT; ausserhalb von ALT deckt sie nur zusaetzliche Funde ab.</li>
 * </ol>
 * Die Zahl der zusaetzlichen Funde je Menge ist exakt festgehalten. In T-1 ist sie ueberall 0 (volle Gleichheit);
 * spaetere Tasks tragen die gemessenen Zahlen ein, jeden zusaetzlichen Fund einzeln nennt der Bericht. Zulaessige
 * Ausnahmen gibt es keine. Stand T-4 (PasswortRecognizer in {@code standard()}): in allen Bestandsmengen weiter 0
 * zusaetzliche Funde; dazu kommen die Pruefstand-Mengen (Negativkorpus, Positivkorpus, die ersten 20 000 Texte
 * der Generatoren), in denen die zusaetzlichen Funde genau die erwarteten PASSWORT-Funde sind. Regel 3
 * (Abdeckung neu = alt plus Geheimnis-Kandidaten) ist seit T-3 um die Kandidaten ergaenzt (die Funde der Erkenner
 * von {@code neu}, die {@code ALT} nicht hat, mit Geheimnistyp); die Typerkennung nutzt {@code GeheimnisTypen},
 * {@code GEHEIMNISTYPEN} wird dagegen abgeglichen.
 * Meldungen nennen Menge, Textindex, Bereich, Typ und Quelle, nie Text oder Werte.
 *
 * <p>Die Gegenproben belegen, dass der Vergleich anschlaegt: ein kuenstlicher Erkenner mit einem Fund ausserhalb des
 * Bestands zaehlt als zusaetzlicher Fund, einer mit einem Fund im Bestand veraendert einen Fund von ALT und wird als
 * Verstoss gemeldet.
 */
class GeheimnisDifferenzTest {

    private static final List<String> GEHEIMNISTYPEN = List.of("PASSWORT", "TOKEN");

    /** Name der Menge 1i (Stand {@code dk-t3-teil-a}): {@code DatumFuzzMengen.erzeugeKvI}, nicht in DatumMengen. */
    private static final String NAME_MENGE_1I = "Teil 1i";

    /**
     * Gemessener Stand je Menge in der Reihenfolge von {@link DatumMengen#NAMEN}, danach Menge 1i: Name, Zahl der
     * Texte, Zahl der Funde von ALT, Zahl der zusaetzlichen Funde (T-1: 0).
     */
    private static final List<String> ERWARTET = List.of(
            "Teil 1|texte=1000|funde=4630|zusaetzlich=0",
            "Teil 1b|texte=1000|funde=5425|zusaetzlich=0",
            "Teil 1c|texte=1000|funde=6239|zusaetzlich=0",
            "Teil 1d|texte=1000|funde=6914|zusaetzlich=0",
            "Teil 1e|texte=1000|funde=5144|zusaetzlich=0",
            "Teil 1f|texte=1000|funde=8353|zusaetzlich=0",
            "Teil 1g|texte=1000|funde=6518|zusaetzlich=0",
            "Teil 1h|texte=1000|funde=7624|zusaetzlich=0",
            "Korpus|texte=327|funde=305|zusaetzlich=0",
            "Korpus Teil 2|texte=101|funde=58|zusaetzlich=0",
            "Generatoren|texte=4000|funde=7985|zusaetzlich=0",
            "KVNR-Korpus|texte=143|funde=130|zusaetzlich=0",
            "KVNR-ohne-Kontext-Korpus|texte=139|funde=55|zusaetzlich=0",
            "BSNR-Korpus|texte=98|funde=97|zusaetzlich=0",
            NAME_MENGE_1I + "|texte=60000|funde=120634|zusaetzlich=0");

    /** Ergebnis des Vergleichs einer Menge. */
    record Messung(String menge, int texte, int funde, int zusaetzlich, List<String> verstoesse,
            List<String> zusatzFunde) {

        String kurz() {
            return menge + "|texte=" + texte + "|funde=" + funde + "|zusaetzlich=" + zusaetzlich;
        }
    }

    private static boolean istGeheimnistyp(FindingType typ) {
        return GeheimnisTypen.istGeheimnistyp(typ);
    }

    /**
     * Die Geheimnis-Kandidaten eines Texts: die Funde der Erkenner von {@code neu}, die {@code alt} nicht hat
     * (Klasse nicht in ALT), mit Geheimnistyp. Unbeteiligte Erkenner laufen nicht noch einmal.
     */
    private static List<Finding> geheimnisKandidaten(List<Recognizer> zusatz, String text) {
        List<Finding> kandidaten = new ArrayList<>();
        for (Recognizer r : zusatz) {
            for (Finding f : r.detect(text)) {
                if (istGeheimnistyp(f.type())) {
                    kandidaten.add(f);
                }
            }
        }
        return kandidaten;
    }

    private static List<Recognizer> zusatzErkenner(TextScannerStandGeheimnis alt, TextScanner neu) {
        List<Class<?>> klassenAlt = new ArrayList<>();
        for (Recognizer r : alt.recognizers()) {
            klassenAlt.add(r.getClass());
        }
        List<Recognizer> zusatz = new ArrayList<>();
        for (Recognizer r : neu.recognizers()) {
            if (!klassenAlt.contains(r.getClass())) {
                zusatz.add(r);
            }
        }
        return zusatz;
    }

    private static boolean ueberlappt(Finding a, Finding b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static BitSet abdeckung(List<Finding> funde) {
        BitSet bits = new BitSet();
        for (Finding f : funde) {
            bits.set(f.start(), f.end());
        }
        return bits;
    }

    private static String bereich(Finding f) {
        return f.type().name() + "[" + f.start() + "," + f.end() + ")" + f.source();
    }

    /** Vergleicht beide Scanner auf den Texten einer Menge. */
    static Messung vergleiche(String menge, List<String> texte, TextScannerStandGeheimnis alt, TextScanner neu) {
        int funde = 0;
        int zusaetzlich = 0;
        List<String> verstoesse = new ArrayList<>();
        List<String> zusatz = new ArrayList<>();
        List<Recognizer> zusatzErkenner = zusatzErkenner(alt, neu);
        for (int i = 0; i < texte.size(); i++) {
            List<Finding> a = alt.scan(texte.get(i));
            List<Finding> n = neu.scan(texte.get(i));
            funde += a.size();
            List<Finding> weitere = new ArrayList<>();
            for (Finding f : a) {
                if (!n.contains(f)) {
                    verstoesse.add(menge + "#" + i + ": Fund von ALT veraendert oder entfallen " + bereich(f));
                }
            }
            for (Finding g : n) {
                if (a.contains(g)) {
                    continue;
                }
                weitere.add(g);
                zusatz.add(menge + "#" + i + ": " + bereich(g));
                boolean ueberlappt = a.stream().anyMatch(f -> ueberlappt(f, g));
                if (!istGeheimnistyp(g.type()) || ueberlappt) {
                    verstoesse.add(menge + "#" + i + ": weiterer Fund ist kein Geheimnistyp oder ueberlappt ALT "
                            + bereich(g));
                }
            }
            zusaetzlich += weitere.size();
            // Regel 3 (ab T-3): Abdeckung neu = Abdeckung alt vereinigt die Geheimnis-Kandidaten
            BitSet erwartetAbdeckung = abdeckung(a);
            erwartetAbdeckung.or(abdeckung(geheimnisKandidaten(zusatzErkenner, texte.get(i))));
            BitSet nur = abdeckung(n);
            BitSet ohneAlt = (BitSet) nur.clone();
            ohneAlt.andNot(erwartetAbdeckung);
            // Weitere Funde ohne Geheimnistyp sind schon als Verstoss von Regel 2 gemeldet (nicht doppelt zaehlen).
            ohneAlt.andNot(abdeckung(weitere.stream().filter(w -> !istGeheimnistyp(w.type())).toList()));
            BitSet verloren = (BitSet) erwartetAbdeckung.clone();
            verloren.andNot(nur);
            if (!verloren.isEmpty() || !ohneAlt.isEmpty()) {
                verstoesse.add(menge + "#" + i + ": Abdeckung weicht ab (verloren=" + verloren.cardinality()
                        + ", fremd=" + ohneAlt.cardinality() + ")");
            }
        }
        return new Messung(menge, texte.size(), funde, zusaetzlich, verstoesse, zusatz);
    }

    // ------------------------------------------------------------------ Bestandsmengen

    @Test
    void bestandsmengen_standardGleichAlt_zusaetzlicheFundeJeMengeExakt() {
        List<List<String>> mengen = new ArrayList<>(DatumMengen.mengen());
        List<String> namen = new ArrayList<>(DatumMengen.NAMEN);
        assertEquals(namen.size(), mengen.size());
        namen.add(NAME_MENGE_1I);
        mengen.add(DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I));
        List<String> ist = new ArrayList<>();
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < mengen.size(); i++) {
            Messung m = vergleiche(namen.get(i), mengen.get(i), TextScannerStandGeheimnis.ALT,
                    TextScanner.standard());
            ist.add(m.kurz());
            verstoesse.addAll(m.verstoesse());
        }
        assertEquals(List.of(), verstoesse);
        assertEquals(ERWARTET, ist);
    }

    // ------------------------------------------------------------------ Pruefstand-Mengen (ab T-4)

    private static final int PRUEFSTAND_GENERATORTEXTE = 20_000;

    /** Negativkorpus: keine Geheimnis-Funde, also keine zusaetzlichen Funde (T-4: 372 Saetze, alle 0). */
    @Test
    void negativkorpus_standardGleichAlt_keinZusatzfund() {
        List<String> texte = new ArrayList<>();
        GeheimnisFehlalarmTest.korpus().values().forEach(texte::addAll);
        Messung m = vergleiche("Negativkorpus", texte, TextScannerStandGeheimnis.ALT, TextScanner.standard());
        assertEquals(List.of(), m.verstoesse());
        assertEquals("Negativkorpus|texte=372|funde=26|zusaetzlich=0", m.kurz());
    }

    /**
     * Positivkorpus (199 Faelle): die Funde von ALT bleiben, dazu kommen genau die Geheimnis-Funde der aktiven
     * Kategorien (K1, ZWISCHENTEIL, UMBRUCH, ANFUEHRUNG, K2, K3, K4, MEHRFACH); die Faelle der noch nicht aktiven
     * Kategorien
     * liefern in T-4 nur wenige zusaetzliche Funde der Kontextregel (T-4: Zahl der zusaetzlichen Funde exakt
     * festgehalten, Begruendung unten).
     */
    @Test
    void positivkorpus_standardGleichAlt_zusaetzlicheFundeNurInDenAktivenKategorien() {
        List<String> aktiv = new ArrayList<>();
        List<String> inaktiv = new ArrayList<>();
        int erwartetAktiv = 0;
        for (GeheimnisPositivKorpus.Fall f : GeheimnisPositivKorpus.faelle()) {
            if (GeheimnisPositivKorpusTest.AKTIVE_KATEGORIEN.contains(f.kategorie())) {
                aktiv.add(f.text());
                erwartetAktiv += f.funde().size();
            } else {
                inaktiv.add(f.text());
            }
        }
        Messung a = vergleiche("Positivkorpus aktiv", aktiv, TextScannerStandGeheimnis.ALT, TextScanner.standard());
        assertEquals(List.of(), a.verstoesse());
        assertEquals(erwartetAktiv, a.zusaetzlich(), "zusaetzliche Funde in den aktiven Kategorien");
        // Umgestellt durch geheimnisse-erkennung T-5: alt texte=97 (K1, ZWISCHENTEIL, UMBRUCH, ANFUEHRUNG, MEHRFACH),
        // neu texte=112 (dazu die 15 Faelle der Kategorien K2, K3, K4); funde=21 und die Gleichheit der
        // zusaetzlichen Funde mit den erwarteten bleiben.
        assertEquals("Positivkorpus aktiv|texte=112|funde=21|zusaetzlich=" + erwartetAktiv, a.kurz());
        Messung i = vergleiche("Positivkorpus inaktiv", inaktiv, TextScannerStandGeheimnis.ALT,
                TextScanner.standard());
        assertEquals(List.of(), i.verstoesse());
        // Sieben Faelle mit Passwort-Teil liefern schon in T-4 einen PASSWORT-Fund der Kontextregel (nicht den
        // erwarteten der spaeteren Regel): TOKEN_KONTEXT-17 (Passwort vor dem Token), ODBC-01, ODBC-02, ODBC-07 und
        // KOMMANDOZEILE-01, -03, -06 (Schluessel mit Gleichheitszeichen). Sie sind erwuenscht (mehr Schutz) und
        // aendern sich mit T-6 und T-8.
        // Umgestellt durch geheimnisse-erkennung T-5: alt texte=102, neu texte=87 (die 15 Faelle von K2, K3, K4 sind
        // jetzt aktiv und stehen oben); funde=8 und zusaetzlich=7 bleiben.
        assertEquals("Positivkorpus inaktiv|texte=87|funde=8|zusaetzlich=7", i.kurz());
    }

    /** Positiv-Generator (erste 20 000 Texte): ALT liefert nichts, standard() genau einen Fund je Text. */
    @Test
    void positivGenerator_standardGleichAlt_einZusatzfundJePasswortText() {
        List<String> texte = new ArrayList<>();
        for (GeheimnisPositivGenerator.Text t : GeheimnisPositivGenerator.lauf(GeheimnisPositivGenerator.SEED,
                PRUEFSTAND_GENERATORTEXTE).texte()) {
            texte.add(t.text());
        }
        Messung m = vergleiche("Positiv-Generator", texte, TextScannerStandGeheimnis.ALT, TextScanner.standard());
        assertEquals(List.of(), m.verstoesse());
        assertEquals("Positiv-Generator|texte=20000|funde=0|zusaetzlich=13344", m.kurz());
    }

    /** Negativ- und Verletzend-Generator (je erste 20 000 Texte): keine zusaetzlichen Funde. */
    @Test
    void negativUndVerletzendGenerator_standardGleichAlt_keinZusatzfund() {
        List<String> negativ = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED,
                PRUEFSTAND_GENERATORTEXTE).texte();
        Messung n = vergleiche("Negativ-Generator", negativ, TextScannerStandGeheimnis.ALT, TextScanner.standard());
        assertEquals(List.of(), n.verstoesse());
        assertEquals(0, n.zusaetzlich());
        List<String> verletzend = GeheimnisPositivGenerator.verletzend(PRUEFSTAND_GENERATORTEXTE).texte();
        Messung v = vergleiche("Verletzend-Generator", verletzend, TextScannerStandGeheimnis.ALT,
                TextScanner.standard());
        assertEquals(List.of(), v.verstoesse());
        assertEquals(0, v.zusaetzlich());
        assertEquals("Verletzend-Generator|texte=20000|funde=0|zusaetzlich=0", v.kurz());
    }

    @Test
    void bestandsmengen_keineMengeIstLeer_dieSummeIstGross() {
        int summe = 0;
        for (List<String> menge : DatumMengen.mengen()) {
            assertFalse(menge.isEmpty(), "leere Menge");
            summe += menge.size();
        }
        assertTrue(summe >= 1000, "zu wenige Texte in den Bestandsmengen: " + summe);
    }

    @Test
    void geheimnistypen_listeStimmtMitGeheimnisTypenUeberein() {
        assertEquals(List.of(GeheimnisTypen.PASSWORT.name(), GeheimnisTypen.TOKEN.name()), GEHEIMNISTYPEN);
        for (String name : GEHEIMNISTYPEN) {
            assertTrue(istGeheimnistyp(new FindingType(name)), name);
        }
        assertFalse(istGeheimnistyp(new FindingType("HOST")));
    }

    @Test
    void standard_hatDieAchtBestandserkennerWieAlt_inGleicherReihenfolge() {
        List<Recognizer> standard = TextScanner.standard().recognizers();
        List<Recognizer> alt = TextScannerStandGeheimnis.ALT.recognizers();
        assertEquals(8, alt.size());
        assertTrue(standard.size() >= alt.size(), "standard hat weniger Erkenner als ALT");
        for (int i = 0; i < alt.size(); i++) {
            assertEquals(alt.get(i).getClass(), standard.get(i).getClass(), "Erkenner an Index " + i);
        }
    }

    // ------------------------------------------------------------------ Gegenproben

    /** Kuenstlicher Erkenner: ein Fund je Vorkommen des Worts, mit Typ und Konfidenz nach Wahl. */
    private static Recognizer markiere(String wort, String typ, double konfidenz) {
        return text -> {
            List<Finding> funde = new ArrayList<>();
            for (int p = text.indexOf(wort); p >= 0; p = text.indexOf(wort, p + wort.length())) {
                funde.add(new Finding(new FindingType(typ), wort, p, p + wort.length(), konfidenz, "kuenstlich"));
            }
            return funde;
        };
    }

    private static TextScanner standardPlus(Recognizer zusatz) {
        List<Recognizer> liste = new ArrayList<>(TextScanner.standard().recognizers());
        liste.add(zusatz);
        return new TextScanner(liste);
    }

    @Test
    void gegenprobe_zusaetzlicherFundAusserhalbDesBestands_wirdGezaehltAlsZusatz_keinVerstoss() {
        TextScanner neu = standardPlus(markiere("Testwert", "TOKEN", 0.95));
        Messung m = vergleiche("probe", List.of("Dienst Testwert gestartet", "ohne Fund", "Testwert und Testwert"),
                TextScannerStandGeheimnis.ALT, neu);
        assertEquals(3, m.zusaetzlich());
        assertEquals(List.of(), m.verstoesse());
        assertEquals(3, m.zusatzFunde().size());
    }

    @Test
    void gegenprobe_fundImBestand_veraendertEinenFundVonAlt_wirdAlsVerstossGemeldet() {
        // Der Zusatzfund ragt ueber das Ende des Pfads hinaus (Teilueberlappung): der Pfad wird zusammengelegt.
        // Umgestellt durch geheimnisse-erkennung T-3: alter Typ der kuenstlichen Erkenner TOKEN, neuer Typ HOST.
        // Grund: Ein Kandidat vom Typ TOKEN wird seit der Lueckenregel auf die Luecken zugeschnitten und kann den
        // Pfad nicht mehr veraendern (gezeigt in der folgenden Gegenprobe); die Gegenprobe braucht dafuer einen
        // Typ ausserhalb der Geheimnistypen.
        String pfad = "Datei C:\\Temp\\x.log gelesen";
        Messung hoch = vergleiche("probe", List.of(pfad), TextScannerStandGeheimnis.ALT,
                standardPlus(markiere("x.log gelesen", "HOST", 0.99)));
        assertFalse(hoch.verstoesse().isEmpty(), "Fund mit hoeherer Konfidenz im Pfad wurde nicht bemerkt");
        Messung gleich = vergleiche("probe", List.of(pfad), TextScannerStandGeheimnis.ALT,
                standardPlus(markiere("x.log gelesen", "HOST", 0.95)));
        assertFalse(gleich.verstoesse().isEmpty(), "Fund mit gleicher Konfidenz im Pfad wurde nicht bemerkt");
    }

    @Test
    void gegenprobe_geheimnisKandidatImBestand_veraendertKeinenFund_reststueckStattZusammenlegung() {
        String pfad = "Datei C:\\Temp\\x.log gelesen";
        for (double konfidenz : new double[] {0.95, 0.99, 1.0}) {
            Messung m = vergleiche("probe", List.of(pfad), TextScannerStandGeheimnis.ALT,
                    standardPlus(markiere("x.log gelesen", "TOKEN", konfidenz)));
            assertEquals(List.of(), m.verstoesse());
            assertEquals(1, m.zusaetzlich());
        }
        Messung innen = vergleiche("probe", List.of(pfad), TextScannerStandGeheimnis.ALT,
                standardPlus(markiere("x.log", "PASSWORT", 0.99)));
        assertEquals(List.of(), innen.verstoesse());
        assertEquals(0, innen.zusaetzlich());
    }

    @Test
    void gegenprobe_weitererFundMitAnderemTyp_wirdAlsVerstossGemeldet() {
        TextScanner neu = standardPlus(markiere("Testwert", "HOST", 0.95));
        Messung m = vergleiche("probe", List.of("Dienst Testwert gestartet"), TextScannerStandGeheimnis.ALT, neu);
        assertEquals(1, m.zusaetzlich());
        assertEquals(1, m.verstoesse().size());
    }

    @Test
    void gegenprobe_identischeScanner_keinUnterschied() {
        Messung m = vergleiche("probe", List.of("Pfad C:\\Temp\\x.log und a@b.example", "nichts"),
                TextScannerStandGeheimnis.ALT, TextScanner.standard());
        assertEquals(0, m.zusaetzlich());
        assertEquals(List.of(), m.verstoesse());
        assertEquals(2, m.funde());
    }
}
