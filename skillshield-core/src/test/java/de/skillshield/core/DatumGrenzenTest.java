package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.DatumMessung.Vergleich;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.BsnrRecognizer;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bekannte Grenzen von Teil A des Features datum-und-kontextvorrang (Architektur Kap. 11, Nachtrag 2 vom 24.11.2026):
 * Je Grenze ein Test {@code bekannteGrenze...}. Die Tests halten das tatsaechliche Verhalten fest und sind kein Soll
 * fuer kuenftige Erweiterungen. "Fund" heisst: ein KV-Fund der angegebenen Konfidenz; das Datum bleibt dann geschuetzt.
 *
 * <p>Tabelle "Grenze zu Test":
 * <table>
 * <caption>Grenze zu Test</caption>
 * <tr><td>Datum mit weiterem Ziffernblock (D-5, AK-10)</td><td>{@code bekannteGrenze_datumMitWeiteremBlock}</td></tr>
 * <tr><td>Datum mit Uhrzeit und Doppelpunkt (Nachtrag 2, AK-10 berichtigt)</td>
 * <td>{@code bekannteGrenze_datumMitUhrzeit}</td></tr>
 * <tr><td>Datum mit einer angehaengten Ziffer (Neun-Ziffern-Regel, A-2)</td>
 * <td>{@code bekannteGrenze_datumMitAngehaengterZiffer_neunZiffern}</td></tr>
 * <tr><td>Zeitraum, dessen Praefix neun Ziffern erreicht (A-2)</td>
 * <td>{@code bekannteGrenze_zeitraumMitNeunZiffernPraefix}</td></tr>
 * <tr><td>Zeitraum mit weiterem Kettenteil dahinter</td>
 * <td>{@code bekannteGrenze_zeitraumMitWeiteremKettenteil}</td></tr>
 * <tr><td>{@code 29.02.00} ist kein Datum</td><td>{@code bekannteGrenze_29Februar00}</td></tr>
 * <tr><td>KVNR-Buchstabenform {@code B <Datum>} bleibt Fund</td>
 * <td>{@code bekannteGrenze_kvnrBuchstabeUndDatum}</td></tr>
 * <tr><td>Geburtsdatum bleibt im Klartext</td><td>{@code bekannteGrenze_geburtsdatumBleibtImKlartext}</td></tr>
 * <tr><td>Leerzeichen-, trennerlose, gemischte, Jahr-zuerst-Formen, zweistelliges Jahr mit {@code /} und {@code -}</td>
 * <td>{@code bekannteGrenze_formenOhneDatumscharakter}</td></tr>
 * <tr><td>Ungueltige Kalenderwerte, Jahre ausserhalb 1900 bis 2099</td>
 * <td>{@code bekannteGrenze_ungueltigerKalender}</td></tr>
 * <tr><td>Echte Nummer hinter einem Zeitraum oder mehreren Daten (vor dem Feature vorhanden)</td>
 * <td>{@code bekannteGrenze_nummerHinterZeitraumOderMehrerenDaten}</td></tr>
 * <tr><td>Nummer mit sechs bis acht Ziffern, als Datum geschrieben, bleibt ungeschuetzt (Nachtrag 3)</td>
 * <td>{@code bekannteGrenze_nummerAlsDatumGeschrieben}</td></tr>
 * </table>
 */
class DatumGrenzenTest {

    private static final TextScanner STANDARD = TextScanner.standard();
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final String D = SynthetischeDaten.DATUM_PUNKT;
    private static final List<String> KONTEXTWOERTER = List.of("LANR", "IK", "KVNR", "BSNR", "NBSNR",
            "Versichertennummer", "Arztnummer");

    private static Finding einziger(List<Finding> funde, String name) {
        assertEquals(1, funde.size(), name + ": Zahl der Funde");
        return funde.get(0);
    }

    /** Genau ein Fund 0,40 ueber {@code [start, ende)}; der Scan entspricht dabei der Kopie ohne Datumsregel. */
    private static Finding fund040(String text, int start, int ende, String name) {
        Finding f = einziger(STANDARD.scan(text), name);
        assertEquals(start, f.start(), name + ": Anfang");
        assertEquals(ende, f.end(), name + ": Ende");
        assertEquals(0.40, f.confidence(), 1e-9, name + ": Konfidenz");
        assertTrue(ALT.scan(text).equals(List.of(f)), name + ": wie heute");
        return f;
    }

    /** D-5: Ein Datum mit weiterem Ziffernblock in derselben Kette bleibt ein Fund (die Kette ist das Segment). */
    @Test
    void bekannteGrenze_datumMitWeiteremBlock() {
        int geprueft = 0;
        for (String form : SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK) {
            for (String kw : KONTEXTWOERTER) {
                String text = kw + " " + form;
                fund040(text, kw.length() + 1, text.length(), "weiterer Block " + geprueft);
                geprueft++;
            }
        }
        assertEquals(2 * 7, geprueft, "Zahl der Texte");
    }

    /**
     * Nachtrag 2: {@code BSNR <Datum> 08:30} bleibt ein Fund 0,40 ueber das Datum und die Stunde (der Doppelpunkt
     * beendet die Kette erst hinter {@code 08}, das Segment hat zehn Ziffern und ist kein Datum). AK-10 war hier
     * "kein Fund" und gilt insoweit als berichtigt; {@code KvDatum} ist dafuer nicht grosszuegiger gefasst.
     */
    @Test
    void bekannteGrenze_datumMitUhrzeit() {
        String form = SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT;
        for (String kw : KONTEXTWOERTER) {
            String text = kw + " " + form;
            int ende = kw.length() + 1 + form.indexOf(':');
            Finding f = fund040(text, kw.length() + 1, ende, "Uhrzeit " + kw.length());
            assertEquals(13, f.end() - f.start(), "Datum, Leerzeichen und zwei Ziffern");
        }
        // Ohne die Stunde bleibt das Datum frei: der Doppelpunkt hinter dem Datum beendet die Kette.
        assertEquals(0, STANDARD.scan("BSNR " + D + ": 08").size(), "Datum, Doppelpunkt, Stunde");
    }

    /** A-2: Ein Datum mit genau einer angehaengten Ziffer (neun Ziffern) bleibt ein Fund; Segment nie geprueft. */
    @Test
    void bekannteGrenze_datumMitAngehaengterZiffer_neunZiffern() {
        for (String trenner : List.of(" ", ".", "/", "-")) {
            String form = D + trenner + "1";
            String text = "BSNR " + form;
            assertEquals(9, DatumReferenz.ziffern(text, 0, text.length()), "neun Ziffern");
            // Ein Segment mit neun Ziffern wird wie jede Neun-Ziffern-Nummer bewertet (Pruefziffer, Regionalcode),
            // nicht als Laengenabweichung: der Fund bleibt wie heute, ueber das ganze Segment.
            List<Finding> neu = STANDARD.scan(text);
            Finding f = einziger(neu, "angehaengte Ziffer, Trenner " + (int) trenner.charAt(0));
            assertEquals(5, f.start(), "Anfang");
            assertEquals(text.length(), f.end(), "Ende");
            assertTrue(neu.equals(ALT.scan(text)), "wie heute");
        }
        // Mit einer Ziffer weniger ist es das Datum allein; mit zwei Ziffern ist es kein Neun-Ziffern-Segment mehr.
        assertEquals(0, STANDARD.scan("BSNR " + D).size(), "das Datum allein");
        assertEquals(1, STANDARD.scan("BSNR " + D + " 12").size(), "zehn Ziffern bleiben ein Fund");
    }

    /** A-2: Ein Zeitraum, dessen Praefix genau neun Ziffern erreicht, bleibt teilweise ein Fund. */
    @Test
    void bekannteGrenze_zeitraumMitNeunZiffernPraefix() {
        int geprueft = 0;
        for (String form : List.of(SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN, SynthetischeDaten.ZEITRAUM_EINSTELLIG)) {
            for (String kw : KONTEXTWOERTER) {
                String text = kw + " " + form;
                List<Finding> neu = STANDARD.scan(text);
                assertFalse(neu.isEmpty(), "Zeitraum mit Neun-Ziffern-Praefix bleibt Fund " + geprueft);
                assertTrue(neu.equals(ALT.scan(text)), "wie heute " + geprueft);
                assertEquals(9, DatumReferenz.ziffern(text, neu.get(0).start(), neu.get(0).end()),
                        "das Segment hat genau neun Ziffern " + geprueft);
                geprueft++;
            }
        }
        assertEquals(2 * 7, geprueft, "Zahl der Texte");
    }

    /** Ein Zeitraum, hinter dem die Kette weitergeht, ist kein Datum; mit Komma statt Leerzeichen ist er frei. */
    @Test
    void bekannteGrenze_zeitraumMitWeiteremKettenteil() {
        String z = SynthetischeDaten.ZEITRAUM;
        for (String weiter : List.of(" 1", "-1", ".1", "/1")) {
            String text = "BSNR " + z + weiter;
            List<Finding> neu = STANDARD.scan(text);
            assertTrue(neu.equals(ALT.scan(text)), "wie heute, Trenner " + (int) weiter.charAt(0));
            assertFalse(neu.isEmpty(), "Fund bleibt, Trenner " + (int) weiter.charAt(0));
            assertEquals(5, neu.get(0).start(), "das Segment beginnt beim Zeitraum");
        }
        assertEquals(0, STANDARD.scan("BSNR " + z + ", 1").size(), "mit Komma ist der Zeitraum frei");
    }

    /** Architektur Kap. 13 Punkt 7: {@code 29.02.00} ist kein Datum (1900 kein Schaltjahr), {@code 28.02.00} schon. */
    @Test
    void bekannteGrenze_29Februar00() {
        int geprueft = 0;
        for (String kw : KONTEXTWOERTER) {
            String text = kw + " " + SynthetischeDaten.SCHALTTAG_ZWEISTELLIG_NULL;
            fund040(text, kw.length() + 1, text.length(), "29.02.00 " + geprueft);
            assertEquals(0, STANDARD.scan(kw + " " + "28.02." + "00").size(), "28.02.00 ist frei " + geprueft);
            geprueft++;
        }
        assertEquals(7, geprueft, "Zahl der Kontextwoerter");
    }

    /**
     * D-2: {@code B <Datum>} hinter KVNR-Kontextwoertern ist die Buchstabenform der KVNR und bleibt ein Fund 0,40
     * (Quelle {@code kvnr-form-abweichend}); hinter allen anderen Kontextwoertern ist der Buchstabe ein Fuellwort, und
     * das Datum allein ist frei.
     */
    @Test
    void bekannteGrenze_kvnrBuchstabeUndDatum() {
        String form = SynthetischeDaten.BUCHSTABE_UND_DATUM;
        for (String kw : List.of("KVNR", "Versichertennummer")) {
            for (String t : List.of(" ", ": ")) {
                String text = kw + t + form;
                Finding f = fund040(text, kw.length() + t.length(), text.length(), "KVNR-Form " + kw.length());
                assertTrue(f.type() == FindingType.KVNR, "Typ KVNR");
                assertTrue(KvnrRecognizer.SOURCE_FORM_ABWEICHEND.equals(f.source()), "Quelle");
            }
        }
        for (String kw : List.of("LANR", "IK", "BSNR", "NBSNR", "Arztnummer")) {
            String text = kw + " " + form;
            assertEquals(0, STANDARD.scan(text).size(), "Buchstabe ist Fuellwort, Datum frei " + kw.length());
            assertEquals(1, ALT.scan(text).size(), "heute ein Fund " + kw.length());
        }
        assertTrue(BsnrRecognizer.SOURCE_LAENGE_ABWEICHEND.startsWith("bsnr"), "Kontrolle der Quelle");
    }

    /** Anforderung "Bekannte Grenzen": Ein Geburtsdatum hinter {@code geb.} bleibt im Klartext (gewollt). */
    @Test
    void bekannteGrenze_geburtsdatumBleibtImKlartext() {
        String text = "Patientin geb. " + D + ", KVNR " + SynthetischeKvNummern.KVNR_GUELTIG;
        List<Finding> funde = STANDARD.scan(text);
        Finding f = einziger(funde, "nur die KVNR");
        assertTrue(f.type() == FindingType.KVNR, "Typ");
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        assertTrue(p.text().contains(D), "das Geburtsdatum steht im Klartext");
        assertFalse(p.text().contains(SynthetischeKvNummern.KVNR_GUELTIG), "die KVNR ist ersetzt");
        assertTrue(text.equals(new Reidentifier().restore(p.text(), p.mapping()).text()), "Roundtrip zeichengleich");
        String mitKontext = "KVNR " + D;
        assertTrue(mitKontext.equals(new Pseudonymizer().pseudonymize(mitKontext, STANDARD.scan(mitKontext)).text()),
                "auch hinter einem Kontextwort bleibt ein Datum im Klartext");
    }

    /** AK-3: Formen ohne Datumscharakter bleiben ein Fund; hier die Gesamtheit mit Zahl der Texte. */
    @Test
    void bekannteGrenze_formenOhneDatumscharakter() {
        int geprueft = 0;
        for (String form : SynthetischeDaten.KEINE_DATUMSFORM) {
            for (String kw : KONTEXTWOERTER) {
                String text = kw + ": " + form;
                fund040(text, kw.length() + 2, text.length(), "keine Datumsform " + geprueft);
                geprueft++;
            }
        }
        assertEquals(7 * 7, geprueft, "Zahl der Texte");
    }

    /** AK-4, AK-5: Kalendarisch Unmoegliches und Jahre ausserhalb 1900 bis 2099 bleiben ein Fund. */
    @Test
    void bekannteGrenze_ungueltigerKalender() {
        int geprueft = 0;
        for (String s : SynthetischeDaten.KALENDERUNGUELTIG) {
            fund040("IK " + s, 3, 3 + s.length(), "ungueltiger Kalender " + geprueft);
            geprueft++;
        }
        for (String s : SynthetischeDaten.JAHRESGRENZEN_UNGUELTIG) {
            fund040("LANR " + s, 5, 5 + s.length(), "Jahr ausserhalb " + geprueft);
            geprueft++;
        }
        fund040("BSNR " + SynthetischeDaten.SCHALTTAG_1900, 5, 15, "29.02.1900");
        assertEquals(9 + 2, geprueft, "Zahl der Texte");
    }

    /**
     * Vor dem Feature vorhanden und unveraendert (Nachtrag 2): Eine echte Nummer hinter einem Zeitraum oder hinter
     * mehreren Datumsangaben hat nicht immer einen Fund. Das Produkt verhaelt sich wie {@code ALT_DATUM}.
     */
    @Test
    void bekannteGrenze_nummerHinterZeitraumOderMehrerenDaten() {
        String z = SynthetischeDaten.ZEITRAUM;
        String d2 = SynthetischeDaten.DATUM_LEISTUNG;
        String b = SynthetischeKvNummern.BSNR_BEKANNT;
        String l = SynthetischeKvNummern.LANR_GUELTIG;
        String i = SynthetischeKvNummern.IK_GUELTIG;
        List<String> texte = List.of("BSNR " + z + " " + b, "LANR " + D + " " + d2 + " " + D + " " + l,
                "IK " + D + " " + d2 + ", " + i);
        int ohneNummernfund = 0;
        for (String text : texte) {
            List<Finding> neu = STANDARD.scan(text);
            Vergleich v = DatumMessung.vergleiche(text, ALT.scan(text), neu);
            assertTrue(v.zulaessig(true), "zulaessig " + v.kurz());
            assertEquals(0, v.sonstigeVerluste() + v.neueFunde() + v.abdeckung(), "keine Folgeaenderung");
            boolean nummerGeschuetzt = false;
            for (Finding f : neu) {
                nummerGeschuetzt |= f.originalValue().contains(b) || f.originalValue().contains(l)
                        || f.originalValue().contains(i);
            }
            ohneNummernfund += nummerGeschuetzt ? 0 : 1;
        }
        // Gemessen: in allen drei Texten hat die Nummer heute keinen eigenen Fund (bestehende Luecke).
        assertEquals(3, ohneNummernfund, "Zahl der Texte ohne Fund ueber die Nummer");
    }

    /**
     * Nachtrag 3: Eine Nummer mit sechs bis acht Ziffern, die als gueltiges Datum geschrieben ist, bleibt ungeschuetzt
     * (Kompromiss nach D-1). Hier mit den Ziffern des Datums {@code D}: in Punktgruppierung kein Fund (acht Ziffern
     * zwei-zwei-vier, sechs Ziffern mit zweistelligem Jahr); in Leerzeichengruppierung dieselben Ziffern ein Fund
     * 0,40, ebenso als kalendarisch ungueltiges Datum.
     */
    @Test
    void bekannteGrenze_nummerAlsDatumGeschrieben() {
        String achtPunkt = D;
        String sechsPunkt = D.substring(0, 6) + D.substring(8);
        for (String kw : KONTEXTWOERTER) {
            assertEquals(8, DatumReferenz.ziffern(achtPunkt, 0, achtPunkt.length()), "acht Ziffern");
            assertEquals(6, DatumReferenz.ziffern(sechsPunkt, 0, sechsPunkt.length()), "sechs Ziffern");
            assertEquals(0, STANDARD.scan(kw + " " + achtPunkt).size(), "acht Ziffern als Datum, kein Fund");
            assertEquals(0, STANDARD.scan(kw + " " + sechsPunkt).size(), "sechs Ziffern als Datum, kein Fund");
            for (String form : List.of(achtPunkt.replace('.', ' '), sechsPunkt.replace('.', ' '))) {
                String text = kw + " " + form;
                fund040(text, kw.length() + 1, text.length(), "andere Gruppierung " + form.length());
            }
            // Das Datum mit Monat 13 ist kalendarisch ungueltig, also ein Fund.
            String ungueltig = D.substring(0, 3) + "13" + D.substring(5);
            String text = kw + " " + ungueltig;
            fund040(text, kw.length() + 1, text.length(), "ungueltiges Datum " + kw.length());
        }
    }
}
