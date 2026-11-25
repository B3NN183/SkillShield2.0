package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Teil A des Features datum-und-kontextvorrang auf Erkennerebene und auf {@code standard()}: Datumsangaben sind keine
 * KV-Nummern (AK-1 bis AK-6, AK-8, AK-9, AK-10 ohne die bekannten Grenzen, die {@code DatumGrenzenTest} festhaelt).
 * Alle Datumswerte kommen aus {@link SynthetischeDaten}, alle Nummern aus {@link SynthetischeKvNummern}; Texte, in
 * denen ein Datum mit einer Nummer verbunden ist, entstehen zur Laufzeit. Meldungen nennen nur Zahlen.
 *
 * <p>Zuordnung der Kriterien zu den Tests: AK-1 {@code ak01_*}, AK-2 {@code ak02_*}, AK-3 {@code ak03_*}, AK-4
 * {@code ak04_*}, AK-5 {@code ak05_*}, AK-6 {@code ak06_*}, AK-8 {@code ak08_*}, AK-9 {@code ak09_*}, AK-10
 * {@code ak10_*}. Die Messung am Produkt gegen {@code ALT_DATUM} mit exakten Zahlen steht in
 * {@code DatumProduktMessungTest}.
 */
class DatumKeinFundTest {

    /** Eine Art mit Kontextwort, Erkenner, Typ, gueltiger Nummer und der Quelle bei abweichender Laenge. */
    private record Art(String name, String kontextwort, Recognizer erkenner, FindingType typ, String nummer,
            String quelleLaenge) {
    }

    private static final List<Art> ARTEN = List.of(
            new Art("LANR", "LANR", new LanrRecognizer(), FindingType.LANR, SynthetischeKvNummern.LANR_GUELTIG,
                    LanrRecognizer.SOURCE_LAENGE_ABWEICHEND),
            new Art("IK", "IK", new IkRecognizer(), FindingType.IK, SynthetischeKvNummern.IK_GUELTIG,
                    IkRecognizer.SOURCE_LAENGE_ABWEICHEND),
            new Art("KVNR", "KVNR", new KvnrRecognizer(), FindingType.KVNR, SynthetischeKvNummern.KVNR_GUELTIG,
                    KvnrRecognizer.SOURCE_FORM_ABWEICHEND),
            new Art("BSNR", "BSNR", new BsnrRecognizer(), FindingType.BSNR, SynthetischeKvNummern.BSNR_BEKANNT,
                    BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND));

    /** Die Kontextwoerter aus AK-1. */
    private static final List<String> KONTEXTWOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer", "Arztnummer");
    private static final List<String> TRENNER = List.of(" ", ": ");

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();

    /** Alle Funde aller vier Erkenner und von {@code standard()} zusammen (Erkennerebene und Scan). */
    private static int alleFunde(String text) {
        int summe = STANDARD.scan(text).size();
        for (Art a : ARTEN) {
            summe += a.erkenner().detect(text).size();
        }
        return summe;
    }

    private static void keinFund(String text, String name) {
        assertEquals(0, alleFunde(text), name + ": Zahl der Funde");
        assertTrue(text.equals(PSEUDONYMIZER.pseudonymize(text, STANDARD.scan(text)).text()), name + ": byte-gleich");
    }

    // ------------------------------------------------------------------ AK-1

    /** AK-1: {@code KW <Datum>} mit Leerzeichen oder {@code : } hat keinen Fund; der Text bleibt byte-gleich. */
    @Test
    void ak01_kontextwortUndDatum_keinFund_textByteGleich() {
        int texte = 0;
        for (String kw : KONTEXTWOERTER) {
            for (String t : TRENNER) {
                String text = kw + t + SynthetischeDaten.DATUM_PUNKT;
                keinFund(text, "AK-1 Text " + texte);
                assertTrue(ALT.scan(text).size() == 1, "heute genau ein Fund (0,40): Text " + texte);
                texte++;
            }
        }
        assertEquals(14, texte, "Zahl der Texte");
    }

    /** AK-1, je Art: der Erkenner der Art selbst liefert nichts, mit dem eigenen Kontextwort und beiden Trennern. */
    @Test
    void ak01_jeArt_eigenerErkennerOhneFund_vorherEinFundMitKonfidenz040() {
        for (Art a : ARTEN) {
            for (String t : TRENNER) {
                String text = a.kontextwort() + t + SynthetischeDaten.DATUM_PUNKT;
                assertEquals(0, a.erkenner().detect(text).size(), a.name() + ": Funde heute");
                List<Finding> alt = ALT.scan(text);
                assertEquals(1, alt.size(), a.name() + ": Funde von ALT_DATUM");
                assertEquals(0.40, alt.get(0).confidence(), 1e-9, a.name() + ": Konfidenz von ALT_DATUM");
            }
        }
    }

    // ------------------------------------------------------------------ AK-2

    /** AK-2: Jede gueltige Datumsform (auch der Zeitraum) hinter jedem Kontextwort hat keinen Fund. */
    @Test
    void ak02_alleDatumsformen_hinterJedemKontextwort_keinFund() {
        int texte = 0;
        for (String form : SynthetischeDaten.GUELTIGE_FORMEN) {
            for (String kw : KONTEXTWOERTER) {
                for (String t : TRENNER) {
                    keinFund(kw + t + form, "AK-2 Text " + texte);
                    texte++;
                }
            }
        }
        assertEquals(11 * 7 * 2, texte, "Zahl der Texte");
    }

    // ------------------------------------------------------------------ AK-3

    private static void genauEinFund040(Art a, String kopf, String form, String name) {
        String text = kopf + form;
        List<Finding> erkenner = a.erkenner().detect(text);
        assertEquals(1, erkenner.size(), name + ": Funde des Erkenners");
        Finding f = erkenner.get(0);
        assertEquals(0.40, f.confidence(), 1e-9, name + ": Konfidenz");
        assertTrue(a.quelleLaenge().equals(f.source()), name + ": Quelle");
        assertTrue(f.type() == a.typ(), name + ": Typ");
        assertTrue(form.equals(text.substring(f.start(), f.end())), name + ": Bereich ist die ganze Form");
        List<Finding> scan = STANDARD.scan(text);
        assertEquals(1, scan.size(), name + ": Funde von standard()");
        assertTrue(f.equals(scan.get(0)), name + ": gleicher Fund");
    }

    /** AK-3: Formen ohne Datumscharakter bleiben ein Fund 0,40 hinter dem Kontextwort jeder Art. */
    @Test
    void ak03_keineDatumsform_fundBleibt() {
        int texte = 0;
        for (Art a : ARTEN) {
            for (String form : SynthetischeDaten.KEINE_DATUMSFORM) {
                genauEinFund040(a, a.kontextwort() + " ", form, "AK-3 " + a.name() + " " + texte);
                texte++;
            }
        }
        assertEquals(4 * 7, texte, "Zahl der Texte");
    }

    // ------------------------------------------------------------------ AK-4, AK-5

    /** AK-4: Kalendarisch unmoegliche Werte und 29.02.1900 bleiben ein Fund; Schaltjahre sind frei. */
    @Test
    void ak04_kalenderGueltigkeit() {
        int fund = 0;
        int frei = 0;
        for (Art a : ARTEN) {
            for (String s : SynthetischeDaten.KALENDERUNGUELTIG) {
                genauEinFund040(a, a.kontextwort() + " ", s, "AK-4 " + a.name() + " " + fund);
                fund++;
            }
            genauEinFund040(a, a.kontextwort() + " ", SynthetischeDaten.SCHALTTAG_1900, "AK-4 1900 " + a.name());
            fund++;
            for (String s : SynthetischeDaten.SCHALTTAGE_GUELTIG) {
                keinFund(a.kontextwort() + " " + s, "AK-4 Schaltjahr " + a.name());
                frei++;
            }
        }
        assertEquals(4 * 10, fund, "Zahl der Texte mit Fund");
        assertEquals(4 * 2, frei, "Zahl der Texte ohne Fund");
    }

    /** AK-5: 01.01.1900, 31.12.2099, 01.01.00 und 31.12.99 sind frei; 31.12.1899 und 01.01.2100 bleiben ein Fund. */
    @Test
    void ak05_jahresgrenzen() {
        int fund = 0;
        int frei = 0;
        for (Art a : ARTEN) {
            for (String s : SynthetischeDaten.JAHRESGRENZEN_GUELTIG) {
                keinFund(a.kontextwort() + " " + s, "AK-5 frei " + a.name() + " " + frei);
                frei++;
            }
            for (String s : SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG) {
                genauEinFund040(a, a.kontextwort() + " ", s, "AK-5 Fund " + a.name() + " " + fund);
                fund++;
            }
        }
        assertEquals(4 * 4, frei, "Zahl der Texte ohne Fund");
        assertEquals(4 * 2, fund, "Zahl der Texte mit Fund");
    }

    // ------------------------------------------------------------------ AK-6

    /** AK-6: Ein Datum ohne Kontextwort in Reichweite hat keinen Fund (das war schon vorher so). */
    @Test
    void ak06_datumOhneKontext_keinFund_wieVorher() {
        String text = "Rechnung vom " + SynthetischeDaten.DATUM_LEISTUNG + " liegt vor.";
        keinFund(text, "AK-6");
        assertEquals(0, ALT.scan(text).size(), "auch ALT_DATUM hat keinen Fund");
    }

    // ------------------------------------------------------------------ AK-8

    private static Finding einziger(List<Finding> funde, String name) {
        assertEquals(1, funde.size(), name + ": Zahl der Funde");
        return funde.get(0);
    }

    /**
     * AK-8: {@code KW <Nummer> <Datum>}, {@code KW <Nummer>, <Datum>} und {@code KW <Nummer> (KW) <Datum>}: genau ein
     * Fund ueber die Nummer, Typ, Bereich, Konfidenz und Quelle wie ohne das Datum.
     */
    @Test
    void ak08_nummerMitDatumDahinter_einFundWieOhneDatum() {
        int texte = 0;
        for (Art a : ARTEN) {
            String ohne = a.kontextwort() + " " + a.nummer();
            Finding erwartet = einziger(STANDARD.scan(ohne), "ohne Datum " + a.name());
            assertTrue(erwartet.type() == a.typ(), a.name() + ": Typ");
            for (String sep : List.of(" ", ", ")) {
                String text = ohne + sep + SynthetischeDaten.DATUM_PUNKT;
                Finding f = einziger(STANDARD.scan(text), a.name() + " Trenner " + sep.length());
                assertTrue(erwartet.equals(f), a.name() + ": Fund wie ohne Datum");
                assertTrue(a.erkenner().detect(text).equals(a.erkenner().detect(ohne)), a.name() + ": Erkenner");
                assertEquals(2, ALT.scan(text).size(), a.name() + ": heute zwei Funde (Nummer und Datum)");
                texte++;
            }
        }
        assertEquals(8, texte, "Zahl der Texte");
    }

    /** AK-8: {@code KW <Datum> <Nummer>}: ein Fund ueber die Nummer mit der Stufe "weiterer Kontext" (0,80). */
    @Test
    void ak08_datumVorDerNummer_einFundUeberDieNummer_weitererKontext() {
        for (Art a : ARTEN) {
            String text = a.kontextwort() + " " + SynthetischeDaten.DATUM_PUNKT + " " + a.nummer();
            Finding f = einziger(STANDARD.scan(text), a.name());
            assertTrue(f.type() == a.typ(), a.name() + ": Typ");
            assertEquals(0.80, f.confidence(), 1e-9, a.name() + ": Konfidenz");
            assertTrue(a.nummer().equals(f.originalValue()), a.name() + ": Bereich ist die Nummer");
            assertEquals(text.length() - a.nummer().length(), f.start(), a.name() + ": Anfang");
        }
    }

    /** AK-8, BSNR: die drei Faelle mit den Konfidenzen 0,90, 0,80 und 0,90 aus der Anforderung. */
    @Test
    void ak08_bsnr_dreiFaelle_konfidenzen() {
        String b = SynthetischeKvNummern.BSNR_BEKANNT;
        String d = SynthetischeDaten.DATUM_PUNKT;
        assertEquals(0.90, einziger(STANDARD.scan("BSNR " + b + ", " + d), "Komma").confidence(), 1e-9, "Komma");
        assertEquals(0.80, einziger(STANDARD.scan("BSNR " + d + " " + b), "davor").confidence(), 1e-9, "davor");
        assertEquals(0.90, einziger(STANDARD.scan("BSNR " + b + " " + d), "dahinter").confidence(), 1e-9, "dahinter");
    }

    /** AK-8: Ein Datum hinter {@code <Nummer> (BSNR)}: die Nummer bleibt ein Fund (0,80), das Datum hat keinen. */
    @Test
    void ak08_nachgestellterKontext_nummerFund080_datumKeiner() {
        for (Art a : ARTEN) {
            String ohne = a.nummer() + " (" + a.kontextwort() + ")";
            String text = ohne + " " + SynthetischeDaten.DATUM_PUNKT;
            Finding f = einziger(STANDARD.scan(text), a.name());
            assertTrue(f.type() == a.typ(), a.name() + ": Typ");
            assertEquals(0.80, f.confidence(), 1e-9, a.name() + ": Konfidenz");
            assertTrue(a.nummer().equals(f.originalValue()), a.name() + ": Bereich ist die Nummer");
            assertTrue(STANDARD.scan(ohne).equals(STANDARD.scan(text)), a.name() + ": wie ohne Datum");
            assertTrue(ALT.scan(text).size() == 2, a.name() + ": heute auch ein Fund ueber das Datum");
        }
    }

    // ------------------------------------------------------------------ AK-9

    private static Vergleich pruefeZustand(String text, String name) {
        List<Finding> alt = ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        Vergleich v = DatumMessung.vergleiche(text, alt, neu);
        assertTrue(v.zulaessig(true), name + ": " + v.kurz());
        assertEquals(1, v.datumEntfallen(), name + ": genau ein Datumsfund entfaellt");
        assertEquals(alt.size() - 1, neu.size(), name + ": Zahl der Funde");
        for (Finding f : neu) {
            assertTrue(alt.contains(f), name + ": jeder neue Fund ist ein alter Fund");
        }
        return v;
    }

    /**
     * AK-9: Der Zustand bleibt wie bisher: Aufzaehlung ueber das Datum hinweg ({@code KW D, N}), nachgestellt
     * ({@code N D (KW)}) und mit Kommas ({@code N, D, KW}); die Fundliste ist bis auf das Datum gleich der heutigen.
     */
    @Test
    void ak09_zustandWieBisher_aufzaehlungUndNachgestellterKontext() {
        int texte = 0;
        for (Art a : ARTEN) {
            String d = SynthetischeDaten.DATUM_PUNKT;
            pruefeZustand(a.kontextwort() + " " + d + ", " + a.nummer(), "Aufzaehlung " + a.name());
            pruefeZustand(a.nummer() + " " + d + " (" + a.kontextwort() + ")", "nachgestellt " + a.name());
            pruefeZustand(a.nummer() + ", " + d + ", " + a.kontextwort(), "Komma " + a.name());
            texte += 3;
        }
        assertEquals(12, texte, "Zahl der Texte");
    }

    /** AK-9: Die Nummer hinter dem Datum behaelt Typ, Bereich, Konfidenz und Quelle des heutigen Funds. */
    @Test
    void ak09_nummerHinterDemDatum_unveraendert() {
        for (Art a : ARTEN) {
            String text = a.kontextwort() + " " + SynthetischeDaten.DATUM_PUNKT + ", " + a.nummer();
            List<Finding> alt = ALT.scan(text);
            Finding nummer = alt.get(alt.size() - 1);
            assertTrue(a.nummer().equals(nummer.originalValue()), a.name() + ": letzter alter Fund ist die Nummer");
            assertTrue(STANDARD.scan(text).equals(List.of(nummer)), a.name() + ": unveraendert");
        }
    }

    // ------------------------------------------------------------------ AK-10

    /** AK-10: Kein Fund bei {@code BSNR 01.03.2026-31.03.2026} (und hinter jedem anderen Kontextwort). */
    @Test
    void ak10_zeitraum_keinFund() {
        int texte = 0;
        for (String kw : KONTEXTWOERTER) {
            for (String t : TRENNER) {
                String text = kw + t + SynthetischeDaten.ZEITRAUM;
                keinFund(text, "AK-10 Text " + texte);
                assertFalse(ALT.scan(text).isEmpty(), "heute ein Fund: Text " + texte);
                texte++;
            }
        }
        assertEquals(14, texte, "Zahl der Texte");
    }

    /** AK-10: Ein Zeitraum, hinter dem ein Komma, ein Zeilenumbruch oder ein Satzende steht, ist frei. */
    @Test
    void ak10_zeitraumMitNachbarzeichen_keinFund() {
        for (String ende : List.of(",", ";", ".", "\n", " x", ")")) {
            keinFund("BSNR " + SynthetischeDaten.ZEITRAUM + ende, "Zeitraum mit Ende " + ende.length());
        }
    }
}
