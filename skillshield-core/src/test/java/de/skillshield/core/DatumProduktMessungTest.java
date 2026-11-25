package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Messung am Produkt gegen {@code ALT_DATUM} (Nachtrag 2 der Hauptsitzung vom 24.11.2026): Die 14 Bestandsmengen
 * enthalten kein Datumssegment und belegen deshalb nur "sonst aendert sich nichts". Hier stehen Texte, in denen ein
 * Datum neben echten Nummern steht: direkt, weiter, Aufzaehlung vor und nach dem Datum, nachgestellt, Komma-Regel,
 * {@code /}, {@code -}, Zeilenumbruch, KVNR-Buchstabenform, Zeitraum und die Neun-Ziffern-Grenzen.
 *
 * <p>Je Text gilt: genau die erwartete Zahl entfallener Funde ueber ein Datumssegment (groesser 0), kein anderer
 * Fund entfaellt, schrumpft oder wechselt Typ, Konfidenz oder Quelle, es entsteht kein neuer Fund, und die
 * Abdeckung ist um die Datumssegmente kleiner und sonst gleich ({@link DatumMessung#vergleiche} unveraendert). Die
 * Datumswerte kommen aus {@link SynthetischeDaten}, die Nummern aus {@link SynthetischeKvNummern}; Texte mit Datum
 * und Nummer entstehen zur Laufzeit. Meldungen nennen nur Namen und Zahlen.
 */
class DatumProduktMessungTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;

    private static final String D = SynthetischeDaten.DATUM_PUNKT;
    private static final String D2 = SynthetischeDaten.DATUM_LEISTUNG;
    private static final String Z = SynthetischeDaten.ZEITRAUM;
    private static final String B = SynthetischeKvNummern.BSNR_BEKANNT;
    private static final String B2 = SynthetischeKvNummern.BSNR_BEKANNT_ZWEI;
    private static final String L = SynthetischeKvNummern.LANR_GUELTIG;
    private static final String L2 = SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
    private static final String I = SynthetischeKvNummern.IK_GUELTIG;
    private static final String K = SynthetischeKvNummern.KVNR_GUELTIG;

    /** Ein Messtext mit Namen und der erwarteten Zahl entfallener Datumsfunde. */
    private record Fall(String name, String text, int entfallen) {
    }

    private static final List<Fall> FAELLE = List.of(
            // direkt hinter dem Kontextwort, alle vier Arten
            new Fall("direkt LANR", "LANR " + D, 1),
            new Fall("direkt IK", "IK " + D, 1),
            new Fall("direkt KVNR", "KVNR " + D, 1),
            new Fall("direkt BSNR", "BSNR " + D, 1),
            new Fall("direkt mit Doppelpunkt", "BSNR: " + D, 1),
            // weiter: Datum vor der Nummer
            new Fall("weiter BSNR", "BSNR " + D + " " + B, 1),
            new Fall("weiter LANR", "LANR " + D + " " + L, 1),
            new Fall("weiter IK", "IK " + D + " " + I, 1),
            new Fall("weiter KVNR", "KVNR " + D + " " + K, 1),
            new Fall("weiter mit Fuellwort", "BSNR Datum " + D + " " + B, 1),
            // Datum hinter der Nummer
            new Fall("dahinter BSNR", "BSNR " + B + " " + D, 1),
            new Fall("dahinter LANR", "LANR " + L + " " + D, 1),
            new Fall("dahinter KVNR", "KVNR " + K + " " + D, 1),
            // Aufzaehlung vor und nach dem Datum
            new Fall("Aufzaehlung Datum zuerst", "LANR " + D + ", " + L, 1),
            new Fall("Aufzaehlung Datum in der Mitte", "LANR " + L + ", " + D + ", " + L2, 1),
            new Fall("Aufzaehlung zwei Daten", "LANR " + L + ", " + D + ", " + D2 + ", " + L2, 2),
            new Fall("Aufzaehlung Nummer danach", "BSNR " + B + ", " + D, 1),
            // nachgestellter Kontext, Komma-Regel
            new Fall("nachgestellt Klammer", B + " " + D + " (BSNR)", 1),
            new Fall("nachgestellt ohne Klammer", I + " " + D + " IK", 1),
            new Fall("nachgestellt Komma", L + ", " + D + ", LANR", 1),
            new Fall("nachgestellt Semikolon", L + "; " + D + "; LANR", 1),
            new Fall("nachgestellt Datum hinter dem Kontextwort", B + " (BSNR) " + D, 1),
            new Fall("nachgestellt KVNR", K + " " + D + " (KVNR)", 1),
            // Schraegstrich, Bindestrich
            new Fall("Schraegstrich vor der Nummer", "BSNR " + D + "/" + B, 1),
            new Fall("Schraegstrich nach der Nummer", "BSNR " + B + "/" + D, 1),
            new Fall("Bindestrich vor der Nummer", "BSNR " + D + "-" + B, 1),
            new Fall("Bindestrich nach der Nummer", "BSNR " + B + "-" + D, 1),
            new Fall("Datum in Schraegstrichform", "LANR 14/03/1958, " + L, 1),
            new Fall("Datum in Bindestrichform", "LANR 14-03-1958, " + L, 1),
            new Fall("Datum in ISO-Form", "LANR 1958-03-14, " + L, 1),
            // Zeilenumbruch
            new Fall("Zeilenumbruch vor der Nummer", "BSNR " + D + "\n" + B, 1),
            new Fall("Zeilenumbruch nach der Nummer", "BSNR " + B + "\n" + D, 1),
            new Fall("Zeilenumbruch vor dem Kontextwort", D + "\nBSNR " + B + ", " + D2, 1),
            // KVNR-Buchstabenform: B <Datum> bleibt Fund, ein zweites Datum entfaellt
            new Fall("KVNR Buchstabenform und Datum danach", "KVNR " + SynthetischeDaten.BUCHSTABE_UND_DATUM + ", "
                    + "LANR " + D2, 1),
            // Zeitraum
            new Fall("Zeitraum direkt", "BSNR " + Z, 1),
            new Fall("Zeitraum mit Komma und Nummer", "BSNR " + Z + ", " + B, 1),
            new Fall("Zeitraum nach der Nummer", "LANR " + L + ", " + Z, 1),
            new Fall("Datum und Zeitraum", "LANR " + D2 + ", " + Z, 2),
            // Das Zeitraum-Segment entfaellt als Datum; das Einzeldatum und die Nummer dahinter hatten schon vorher
            // keinen Fund (bestehende Luecke, Nachtrag 2)
            new Fall("Zeitraum und danach Datum und Nummer", "LANR " + Z + ", " + D2 + ", " + L, 1),
            // Neun-Ziffern-Grenzen: der Neun-Ziffern-Fund bleibt, das zweite Datum entfaellt
            new Fall("Neun-Ziffern-Zeitraum und Datum", "BSNR " + SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN
                    + "\nLANR " + D, 1),
            new Fall("Neun-Ziffern-Zeitraum einstellig und Datum", "BSNR " + SynthetischeDaten.ZEITRAUM_EINSTELLIG
                    + "\nLANR " + D, 1),
            new Fall("Datum mit einer Ziffer und Datum", "BSNR " + D + " 1" + "\nLANR " + D2, 1),
            new Fall("Datum mit Ziffernblock und Datum", "BSNR " + SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK.get(0)
                    + "\nLANR " + D2, 1),
            new Fall("Datum mit Uhrzeit und Datum", "BSNR " + SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT
                    + "\nLANR " + D2, 1),
            // zwei Nummern verschiedener Arten und mehrere Daten
            new Fall("Mischung", "BSNR " + B + ", NBSNR " + B2 + ", LANR " + D + " " + L + ", Rechnung vom " + D2, 2),
            new Fall("Leistungszeile", "LANR " + L + " " + D2 + ", NBSNR " + B2 + ": GOP", 1));

    private record Ergebnis(List<Finding> alt, List<Finding> neu, Vergleich vergleich) {
    }

    private static Ergebnis messe(Fall fall) {
        List<Finding> alt = ALT.scan(fall.text());
        List<Finding> neu = STANDARD.scan(fall.text());
        return new Ergebnis(alt, neu, DatumMessung.vergleiche(fall.text(), alt, neu));
    }

    /** Je Text: exakte Zahl entfallener Datumsfunde (groesser 0), sonst kein Unterschied. */
    @Test
    void jederText_exakteZahlEntfallenerDatumsfunde_sonstKeinUnterschied() {
        int summe = 0;
        for (Fall fall : FAELLE) {
            Ergebnis e = messe(fall);
            Vergleich v = e.vergleich();
            assertTrue(fall.entfallen() > 0, fall.name() + ": erwartete Zahl groesser 0");
            assertEquals(fall.entfallen(), v.datumEntfallen(), fall.name() + ": entfallene Datumsfunde " + v.kurz());
            assertEquals(0, v.sonstigeVerluste(), fall.name() + ": kein anderer Fund entfaellt oder aendert sich");
            assertEquals(0, v.neueFunde(), fall.name() + ": keine neuen Funde");
            assertEquals(0, v.abdeckung(), fall.name() + ": Abdeckung nur um die Datumssegmente kleiner");
            assertTrue(v.zulaessig(true), fall.name() + ": zulaessig");
            assertEquals(e.alt().size() - fall.entfallen(), e.neu().size(), fall.name() + ": Zahl der Funde");
            summe += v.datumEntfallen();
        }
        assertEquals(46, FAELLE.size(), "Zahl der Texte");
        assertEquals(49, summe, "Summe der entfallenen Datumsfunde");
    }

    /** Je Text: jeder neue Fund ist ein alter Fund (Typ, Bereich, Konfidenz, Quelle gleich), keiner liegt im Datum. */
    @Test
    void jederText_neueFundeSindAlteFunde_keinFundUeberEinemDatumssegment() {
        for (Fall fall : FAELLE) {
            Ergebnis e = messe(fall);
            for (Finding f : e.neu()) {
                assertTrue(e.alt().contains(f), fall.name() + ": neuer Fund ist ein alter Fund");
                assertFalse(DatumReferenz.istDatumssegment(fall.text(), f.start(), f.end()),
                        fall.name() + ": Fund ueber einem Datumssegment");
            }
            List<Finding> erwartet = new ArrayList<>(e.alt());
            erwartet.removeIf(f -> DatumReferenz.istDatumssegment(fall.text(), f.start(), f.end()));
            assertTrue(erwartet.equals(e.neu()), fall.name() + ": alt ohne Datumssegmente gleich neu");
        }
    }

    /** Je Text: Pseudonymisieren und Zurueckuebersetzen ist zeichengleich, die Datumsangaben bleiben im Klartext. */
    @Test
    void jederText_roundtripZeichengleich_datumBleibtImKlartext() {
        Pseudonymizer pseudonymizer = new Pseudonymizer();
        Reidentifier reidentifier = new Reidentifier();
        for (Fall fall : FAELLE) {
            List<Finding> neu = STANDARD.scan(fall.text());
            PseudonymizationResult p = pseudonymizer.pseudonymize(fall.text(), neu);
            assertTrue(fall.text().equals(reidentifier.restore(p.text(), p.mapping()).text()),
                    fall.name() + ": Roundtrip");
            for (String datum : List.of(D, D2)) {
                boolean imFund = false;
                for (Finding f : neu) {
                    imFund |= f.originalValue().contains(datum);
                }
                // Ein Datum, das nicht Teil eines Funds ist (etwa B <Datum> der KVNR-Form), bleibt im Klartext.
                if (fall.text().contains(datum) && !imFund) {
                    assertTrue(p.text().contains(datum), fall.name() + ": Datum im Klartext");
                }
            }
        }
    }

    /** Der Anlasstext: heute 13 Funde, danach 9; die vier Datumsfunde entfallen, alle anderen bleiben gleich. */
    @Test
    void anlasstext_dreizehnFunde_danachNeun() {
        String text = SynthetischeDaten.anlassText();
        List<Finding> alt = ALT.scan(text);
        List<Finding> neu = STANDARD.scan(text);
        assertEquals(13, alt.size(), "Funde heute");
        assertEquals(9, neu.size(), "Funde nach Teil A");
        Vergleich v = DatumMessung.vergleiche(text, alt, neu);
        assertEquals(4, v.datumEntfallen(), "entfallene Datumsfunde");
        assertEquals(0, v.sonstigeVerluste() + v.neueFunde() + v.abdeckung(), v.kurz());
        for (String datum : List.of(D, SynthetischeDaten.DATUM_LEISTUNG, SynthetischeDaten.DATUM_LEISTUNG_SPAETER,
                SynthetischeDaten.DATUM_GENEHMIGUNG)) {
            assertTrue(new Pseudonymizer().pseudonymize(text, neu).text().contains(datum), "Datum im Klartext");
        }
    }
}
