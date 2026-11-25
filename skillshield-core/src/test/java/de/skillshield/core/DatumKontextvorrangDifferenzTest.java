package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMessung.Messung;
import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Differenztest alt gegen neu fuer Teil A des Features datum-und-kontextvorrang (Architektur Kap. 7, in der
 * verkleinerten Form des Nachtrags vom 24.11.2026): das Produkt gegen die eingefrorene Kopie ({@link KvStandDatum}) auf
 * allen Textmengen aus {@link DatumMengen}, auf Erkennerebene je Art und auf Scannerebene ({@code standard()} gegen
 * {@code ALT_DATUM}).
 *
 * <p>Der Schalter {@link #TEIL_A} steht in T-1 auf {@code false}: Das Produkt ist unveraendert, verlangt wird volle
 * Gleichheit (jeder Fund, alle Felder, gleiche Reihenfolge). T-2 setzt ihn auf {@code true}; dann gilt je Art "Kopie
 * minus Funde, deren Bereich nach {@link DatumReferenz} ein Datumssegment ist" und auf Scannerebene ist die einzige
 * zulaessige Abweichung ein entfallener Fund eines Datumssegments (Klasse A); die Zahl je Menge steht exakt in
 * {@link DatumKontextvorrangPrognoseTest}. Folgeaenderungen (neue Funde, andere Funde, andere Abdeckung) sind in
 * keinem Zustand zulaessig; ihre erwartete Zahl ist 0.
 *
 * <p>Selbsttests belegen, dass die Pruefung nicht ins Leere laeuft: Die Simulation des kuenftigen Verhaltens wird als
 * Klasse A erkannt (und bei {@code TEIL_A = false} als Verstoss), jeder eingebaute Verlust, Zusatzfund,
 * Typwechsel oder jede Abschwaechung wird gemeldet. Meldungen nennen nur Zahlen und Namen der Textmengen.
 */
class DatumKontextvorrangDifferenzTest {

    /** Ob das Produkt Teil A (Datumsangaben sind keine KV-Nummern) schon umsetzt; T-1: nein. */
    static final boolean TEIL_A = false;

    private static final List<String> KONTEXTWOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer", "Arztnummer");
    private static final List<String> TRENNER = List.of(" ", ": ");

    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner SIMULIERT = KvStandDatum.simuliert(
            (text, f) -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));

    // ------------------------------------------------------------------ Produkt gegen Kopie (alle Mengen)

    /** Erkennerebene: je Art entspricht das Produkt der Kopie (T-1) bzw. der Kopie ohne Datumssegmente (Teil A). */
    @Test
    void erkennerebene_jeArt_produktGleichKopieOhneErlaubteAbweichung() {
        List<Messung> m = DatumMessung.messungen();
        for (int i = 0; i < m.size(); i++) {
            for (int a = 0; a < 4; a++) {
                int ungleich = TEIL_A ? m.get(i).ungleichKopieOhneDatum[a] : m.get(i).ungleichKopie[a];
                assertEquals(0, ungleich, DatumMengen.NAMEN.get(i) + " " + DatumMessung.ARTEN.get(a)
                        + ": Texte mit abweichender Fundliste");
            }
        }
    }

    /** Scannerebene: nur zulaessige Abweichungen; entfallene Datumsfunde nur mit {@code TEIL_A}, in exakter Zahl. */
    @Test
    void scannerebene_standardGegenAltDatum_nurZulaessigeAbweichungen() {
        List<Messung> m = DatumMessung.messungen();
        for (int i = 0; i < m.size(); i++) {
            Vergleich v = m.get(i).produkt;
            assertTrue(v.zulaessig(TEIL_A), DatumMengen.NAMEN.get(i) + ": " + v.kurz());
            int erwartetA = TEIL_A ? DatumKontextvorrangPrognoseTest.ENTFALLEN_SCANNER[i] : 0;
            assertEquals(erwartetA, v.datumEntfallen(), DatumMengen.NAMEN.get(i) + ": entfallene Datumsfunde");
            assertEquals(0, v.neueFunde(), DatumMengen.NAMEN.get(i) + ": Folgeaenderungen (Klasse Z)");
        }
    }

    /** Das Produkt liefert auf allen Mengen sortierte, ueberlappungsfreie, deterministische Funde (AK-31). */
    @Test
    void produkt_sortiertUeberlappungsfreiDeterministisch() {
        List<Messung> m = DatumMessung.messungen();
        for (int i = 0; i < m.size(); i++) {
            assertEquals(0, m.get(i).unsortiert, DatumMengen.NAMEN.get(i) + ": Texte mit unsortierter Liste");
        }
    }

    // ------------------------------------------------------------------ Selbsttests der Pruefung

    /** Formen der zufaelligen Daten: Punktform, zweistelliges Jahr, Schraegstrich, Bindestrich, ISO, Zeitraum. */
    private static final int FORM_PUNKT = 0;
    private static final int FORM_ZWEISTELLIG = 1;
    private static final int FORM_SCHRAEG = 2;
    private static final int FORM_STRICH = 3;
    private static final int FORM_ISO = 4;
    private static final int FORM_ZEITRAUM = 5;
    private static final int ZAHL_FORMEN = 6;

    /** Die Zahl, gegebenenfalls mit fuehrender Null auf zwei Stellen aufgefuellt. */
    private static String zweistellig(int zahl, boolean mitNull) {
        return mitNull && zahl < 10 ? "0" + zahl : String.valueOf(zahl);
    }

    /** Ein Datum der Punktform TT.MM.JJJJ mit immer zwei Stellen fuer Tag und Monat. */
    private static String punktDatum(LocalDate d) {
        return zweistellig(d.getDayOfMonth(), true) + "." + zweistellig(d.getMonthValue(), true) + "." + d.getYear();
    }

    private static String datum(Random r, int form) {
        LocalDate d = LocalDate.of(1900, 1, 1).plusDays(r.nextInt(SynthetischeDaten.KALENDERTAGE_1900_BIS_2099));
        int jahr = d.getYear();
        String tag = zweistellig(d.getDayOfMonth(), r.nextBoolean());
        String monat = zweistellig(d.getMonthValue(), r.nextBoolean());
        String tagNull = zweistellig(d.getDayOfMonth(), true);
        String monatNull = zweistellig(d.getMonthValue(), true);
        return switch (form) {
            case FORM_PUNKT -> tag + "." + monat + "." + jahr;
            case FORM_ZWEISTELLIG -> zweistellig(1 + r.nextInt(28), true) + "." + zweistellig(1 + r.nextInt(12), true)
                    + "." + zweistellig(r.nextInt(100), true);
            case FORM_SCHRAEG -> tag + "/" + monat + "/" + jahr;
            case FORM_STRICH -> tag + "-" + monat + "-" + jahr;
            case FORM_ISO -> jahr + "-" + monatNull + "-" + tagNull;
            case FORM_ZEITRAUM -> {
                LocalDate ende = d.plusDays(r.nextInt(400));
                // Liegt das Ende nach 2099, endet der Zeitraum am Anfangsdatum.
                yield punktDatum(d) + "-" + punktDatum(ende.getYear() <= 2099 ? ende : d);
            }
            default -> throw new IllegalArgumentException("Form " + form);
        };
    }

    /**
     * Selbsttest: Auf zufaelligen gueltigen Daten hinter jedem Kontextwort ist die Simulation des kuenftigen Verhaltens
     * genau Klasse A: Jeder Fund von {@code ALT_DATUM} ist ein entfallener Datumsfund, danach bleibt kein Fund, und
     * die Pruefung meldet dabei keinen Verstoss gegen {@code TEIL_A = true}, wohl aber gegen {@code TEIL_A = false}.
     */
    @Test
    void selbsttest_simulationIstKlasseA_undBeiTeilAFalseEinVerstoss() {
        Random r = new Random(20261124L);
        Vergleich summe = Vergleich.KEIN;
        int altFunde = 0;
        int texte = 0;
        for (int n = 0; n < 3000; n++) {
            String kw = KONTEXTWOERTER.get(r.nextInt(KONTEXTWOERTER.size()));
            String text = kw + TRENNER.get(r.nextInt(TRENNER.size())) + datum(r, n % ZAHL_FORMEN);
            List<Finding> alt = ALT.scan(text);
            List<Finding> sim = SIMULIERT.scan(text);
            assertTrue(sim.isEmpty(), "Text " + n + ": nach der Simulation bleibt kein Fund");
            altFunde += alt.size();
            summe = summe.plus(DatumMessung.vergleiche(text, alt, sim));
            texte++;
        }
        assertEquals(3000, texte, "Zahl der Texte");
        assertEquals(3000, altFunde, "jedes Datum hat sechs bis zwoelf Ziffern: heute genau ein Fund je Text");
        assertEquals(altFunde, summe.datumEntfallen(), "jeder alte Fund ist ein entfallener Datumsfund");
        assertEquals(0, summe.sonstigeVerluste() + summe.neueFunde() + summe.abdeckung(), summe.kurz());
        assertTrue(summe.zulaessig(true), "Teil A erlaubt Klasse A");
        assertFalse(summe.zulaessig(false), "ohne Teil A ist Klasse A ein Verstoss");
    }

    private static final String NUMMER_UND_DATUM = "BSNR " + SynthetischeDaten.DATUM_PUNKT + ", LANR "
            + SynthetischeKvNummern.LANR_GUELTIG;

    /** Selbsttest: gleiche Listen sind kein Unterschied. */
    @Test
    void selbsttest_gleicheListen_keinUnterschied() {
        List<Finding> alt = ALT.scan(NUMMER_UND_DATUM);
        assertEquals(2, alt.size(), "ein Datumsfund und ein LANR-Fund");
        assertEquals(Vergleich.KEIN, DatumMessung.vergleiche(NUMMER_UND_DATUM, alt, alt), "gleich");
    }

    /** Selbsttest: Verlust eines Nicht-Datums-Fundes, Zusatzfund, Typwechsel und Abschwaechung werden gemeldet. */
    @Test
    void selbsttest_mutanten_werdenGemeldet() {
        String text = NUMMER_UND_DATUM;
        List<Finding> alt = ALT.scan(text);
        Finding datum = alt.get(0);
        Finding lanr = alt.get(1);
        assertTrue(DatumReferenz.istDatumssegment(text, datum.start(), datum.end()), "erster Fund ist das Datum");
        assertFalse(DatumReferenz.istDatumssegment(text, lanr.start(), lanr.end()), "zweiter Fund ist die LANR");

        Vergleich verlust = DatumMessung.vergleiche(text, alt, List.of(datum));
        assertEquals(1, verlust.sonstigeVerluste(), "Verlust des LANR-Fundes");
        assertTrue(verlust.abdeckung() > 0, "Position ungeschuetzt");
        assertFalse(verlust.zulaessig(true), "Verlust ist nie zulaessig");

        Finding zusatz = new Finding(FindingType.TEXT, text.substring(0, 4), 0, 4, 0.5, "mutant");
        Vergleich mehr = DatumMessung.vergleiche(text, alt, List.of(zusatz, datum, lanr));
        assertEquals(1, mehr.neueFunde(), "Zusatzfund");
        assertFalse(mehr.zulaessig(true), "Zusatzfund ist nie zulaessig");

        Finding anderer = new Finding(FindingType.IK, lanr.originalValue(), lanr.start(), lanr.end(),
                lanr.confidence(), lanr.source());
        Vergleich typ = DatumMessung.vergleiche(text, alt, List.of(datum, anderer));
        assertEquals(1, typ.sonstigeVerluste(), "Typwechsel: der alte Fund fehlt");
        assertEquals(1, typ.neueFunde(), "Typwechsel: ein neuer Fund");
        assertFalse(typ.zulaessig(true), "Typwechsel ist nie zulaessig");

        Finding schwaecher = new Finding(lanr.type(), lanr.originalValue(), lanr.start(), lanr.end(),
                lanr.confidence() - 0.1, lanr.source());
        Vergleich konfidenz = DatumMessung.vergleiche(text, alt, List.of(datum, schwaecher));
        assertEquals(1, konfidenz.sonstigeVerluste(), "Abschwaechung: der alte Fund fehlt");
        assertFalse(konfidenz.zulaessig(true), "Abschwaechung ist nie zulaessig");
    }

    /** Selbsttest: Datumsfund weg und zugleich ein anderer Fund verkuerzt ist ein Verstoss (kein stilles Mitnehmen). */
    @Test
    void selbsttest_datumEntfaelltUndNachbarVerkuerzt_istVerstoss() {
        String text = NUMMER_UND_DATUM;
        List<Finding> alt = ALT.scan(text);
        Finding lanr = alt.get(1);
        Finding kurz = new Finding(lanr.type(), lanr.originalValue().substring(1), lanr.start() + 1, lanr.end(),
                lanr.confidence(), lanr.source());
        Vergleich v = DatumMessung.vergleiche(text, alt, List.of(kurz));
        assertEquals(1, v.datumEntfallen(), "das Datum entfaellt");
        assertEquals(1, v.sonstigeVerluste(), "der LANR-Fund fehlt");
        assertEquals(1, v.neueFunde(), "der verkuerzte Fund ist neu");
        assertEquals(1, v.abdeckung(), "eine Position ungeschuetzt");
        assertFalse(v.zulaessig(true), "Verstoss");
    }

    /** Selbsttest: Ein Fund, der kein Datumssegment ist, geht nicht als Klasse A durch (Zeitraum, neun Ziffern). */
    @Test
    void selbsttest_neunZiffernPraefix_istNichtKlasseA() {
        String text = "BSNR " + SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN;
        List<Finding> alt = ALT.scan(text);
        assertFalse(alt.isEmpty(), "heute ein Fund");
        Vergleich v = DatumMessung.vergleiche(text, alt, List.of());
        assertEquals(0, v.datumEntfallen(), "kein Datumssegment");
        assertTrue(v.sonstigeVerluste() > 0, "als Verlust gemeldet");
        assertFalse(v.zulaessig(true), "kein Verstoss-Freispruch");
    }

    /** Selbsttest auf Mengenebene: Ein Scanner, der keine Funde liefert, faellt auf den echten Mengen sofort auf. */
    @Test
    void selbsttest_leererScanner_faelltAufDenMengenAuf() {
        List<String> texte = DatumMengen.mengen().get(DatumMengen.NAMEN.indexOf("Korpus"));
        Vergleich summe = Vergleich.KEIN;
        int alteFunde = 0;
        for (String text : texte) {
            List<Finding> alt = ALT.scan(text);
            alteFunde += alt.size();
            summe = summe.plus(DatumMessung.vergleiche(text, alt, List.of()));
        }
        assertTrue(alteFunde > 0, "der Korpus hat Funde");
        assertEquals(alteFunde, summe.sonstigeVerluste(), "jeder alte Fund ist ein Verlust: " + summe.kurz());
        assertFalse(summe.zulaessig(true), "leerer Scanner ist ein Verstoss");
    }
}
