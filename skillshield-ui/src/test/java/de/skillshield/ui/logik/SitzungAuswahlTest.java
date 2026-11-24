package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.ALLE_STUFEN;
import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Auswahl und Live-Ergebnis (AK-14, AK-15, AK-16, AK-20). */
class SitzungAuswahlTest {

    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), dialoge);

    /** Baut den Text fuer die KI allein aus den Tabellenzeilen nach: Platzhalter, wo angewaehlt, sonst Original. */
    private static String ausZeilen(String eingabe, List<FundZeile> zeilen) {
        StringBuilder text = new StringBuilder();
        int position = 0;
        for (FundZeile zeile : zeilen) {
            text.append(eingabe, position, zeile.fund().start());
            text.append(zeile.angewaehlt() ? zeile.platzhalter() : zeile.fund().originalValue());
            position = zeile.fund().end();
        }
        return text.append(eingabe.substring(position)).toString();
    }

    private static PseudonymizationResult kern(String eingabe, boolean[] auswahl) {
        List<Finding> funde = TextScanner.standard().scan(eingabe);
        List<Finding> gewaehlt = new ArrayList<>();
        for (int i = 0; i < funde.size(); i++) {
            if (auswahl[i]) {
                gewaehlt.add(funde.get(i));
            }
        }
        return new Pseudonymizer().pseudonymize(eingabe, gewaehlt);
    }

    // ---------------------------------------------------------------- Platzhalter je Zeile (AK-14)

    @Test
    void platzhalterJeZeileIstDerPlatzhalterAnDieserStelleImText() {
        scannen(sitzung, ALLE_STUFEN);

        assertEquals(sitzung.textFuerKi(), ausZeilen(ALLE_STUFEN, sitzung.zeilen()));
        assertEquals("Mail [[EMAIL_1]], Server [[IP_2]], KVNR [[KVNR_3]], LANR [[LANR_4]].", sitzung.textFuerKi());
    }

    @Test
    void gleicherTypUndFundtextZeigenDenselbenPlatzhalter() {
        scannen(sitzung, DREI_MAILS);

        List<FundZeile> zeilen = sitzung.zeilen();
        assertEquals(3, zeilen.size());
        assertEquals("[[EMAIL_1]]", zeilen.get(0).platzhalter());
        assertEquals("[[EMAIL_2]]", zeilen.get(1).platzhalter());
        assertEquals("[[EMAIL_1]]", zeilen.get(2).platzhalter());
        assertEquals(sitzung.textFuerKi(), ausZeilen(DREI_MAILS, zeilen));
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void abgewaehlteZeileHatKeinenPlatzhalter() {
        scannen(sitzung, DREI_MAILS);

        sitzung.anwaehlen(1, false);

        FundZeile zeile = sitzung.zeilen().get(1);
        assertFalse(zeile.angewaehlt());
        assertNull(zeile.platzhalter());
        assertTrue(sitzung.textFuerKi().contains("bernd@example.org"));
    }

    @Test
    void zeilenSindUnveraenderlich() {
        scannen(sitzung, DREI_MAILS);

        assertThrows(UnsupportedOperationException.class, () -> sitzung.zeilen().remove(0));
    }

    // ---------------------------------------------------------------- Abwahl = Ausgabe des Kerns (AK-15)

    @Test
    void abwahlEntferntGenauDieseErsetzungUndDieNummernEntsprechenDemKern() {
        scannen(sitzung, DREI_MAILS);

        sitzung.anwaehlen(0, false);

        PseudonymizationResult erwartet = kern(DREI_MAILS, new boolean[] {false, true, true});
        assertEquals(erwartet.text(), sitzung.textFuerKi());
        assertEquals("a: anna@example.org, b: [[EMAIL_1]], wieder a: [[EMAIL_2]].", sitzung.textFuerKi());
        assertEquals(sitzung.textFuerKi(), ausZeilen(DREI_MAILS, sitzung.zeilen()));
        assertEquals(2, erwartet.mapping().size());
    }

    @Test
    void jedeTeilmengeDerFundeLiefertDieAusgabeDesKerns() {
        int anzahl = TextScanner.standard().scan(ALLE_STUFEN).size();
        scannen(sitzung, ALLE_STUFEN);

        for (int maske = 0; maske < (1 << anzahl); maske++) {
            boolean[] auswahl = new boolean[anzahl];
            for (int i = 0; i < anzahl; i++) {
                auswahl[i] = (maske & (1 << i)) != 0;
                sitzung.anwaehlen(i, auswahl[i]);
            }
            PseudonymizationResult erwartet = kern(ALLE_STUFEN, auswahl);

            assertEquals(erwartet.text(), sitzung.textFuerKi(), "Maske " + maske);
            assertEquals(ausZeilen(ALLE_STUFEN, sitzung.zeilen()), sitzung.textFuerKi(), "Maske " + maske);
            assertEquals("Zuordnung: " + erwartet.mapping().size() + " Platzhalter (nur im Arbeitsspeicher)",
                    sitzung.statusleiste().zuordnung(), "Maske " + maske);
        }
    }

    @Test
    void wiederanwahlStelltDenVorigenZustandHer() {
        scannen(sitzung, ALLE_STUFEN);
        List<FundZeile> vorher = sitzung.zeilen();
        String textVorher = sitzung.textFuerKi();
        Statusleiste statusVorher = sitzung.statusleiste();

        sitzung.anwaehlen(2, false);
        assertFalse(textVorher.equals(sitzung.textFuerKi()));
        sitzung.anwaehlen(2, true);

        assertEquals(vorher, sitzung.zeilen());
        assertEquals(textVorher, sitzung.textFuerKi());
        assertEquals(statusVorher, sitzung.statusleiste());
    }

    @Test
    void auchAlleEinzelnAbgewaehltenFundeSindErlaubtUndLassenDenTextUnveraendert() {
        scannen(sitzung, ALLE_STUFEN);

        for (int i = 0; i < sitzung.zeilen().size(); i++) {
            sitzung.anwaehlen(i, false);
        }

        assertEquals(ALLE_STUFEN, sitzung.textFuerKi());
        assertEquals("4 Funde, 0 angewählt, 1 fraglich. 4 Funde werden nicht ersetzt.",
                sitzung.statusleiste().meldung());
        assertEquals("Zuordnung: 0 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ALLE_ANWAEHLEN));
    }

    // ---------------------------------------------------------------- Alle anwaehlen (AK-16)

    @Test
    void alleAnwaehlenWaehltAuchEinzelAbgewaehlteWiederAn() {
        scannen(sitzung, ALLE_STUFEN);
        List<FundZeile> ausgangslage = sitzung.zeilen();
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(3, false);

        sitzung.alleAnwaehlen();

        assertEquals(ausgangslage, sitzung.zeilen());
        assertTrue(sitzung.zeilen().stream().allMatch(FundZeile::angewaehlt));
        assertEquals("4 Funde, 4 angewählt, 1 fraglich.", sitzung.statusleiste().meldung());
    }

    // ---------------------------------------------------------------- Statuszaehlung (AK-20)

    @Test
    void statusleisteZaehltNachJederAenderungDerAuswahl() {
        scannen(sitzung, ALLE_STUFEN);

        sitzung.anwaehlen(3, false);
        assertEquals("4 Funde, 3 angewählt, 1 fraglich. 1 Fund wird nicht ersetzt.",
                sitzung.statusleiste().meldung());
        sitzung.anwaehlen(0, false);
        assertEquals("4 Funde, 2 angewählt, 1 fraglich. 2 Funde werden nicht ersetzt.",
                sitzung.statusleiste().meldung());
        sitzung.alleAnwaehlen();
        assertEquals("4 Funde, 4 angewählt, 1 fraglich.", sitzung.statusleiste().meldung());
    }

    // ---------------------------------------------------------------- Grenzen und Sonderfaelle

    @Test
    void ungueltigeZeileWirdAbgewiesenOhneZustandsaenderung() {
        scannen(sitzung, DREI_MAILS);
        List<FundZeile> zeilen = sitzung.zeilen();

        assertThrows(IndexOutOfBoundsException.class, () -> sitzung.anwaehlen(3, false));
        assertThrows(IndexOutOfBoundsException.class, () -> sitzung.anwaehlen(-1, false));
        assertEquals(zeilen, sitzung.zeilen());
    }

    @Test
    void ohneGueltigenScanAendernAuswahlfunktionenNichts() {
        sitzung.anwaehlen(0, false);
        sitzung.alleAnwaehlen();
        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());

        scannen(sitzung, DREI_MAILS);
        sitzung.eingabeGeaendert();
        sitzung.anwaehlen(0, false);
        sitzung.alleAnwaehlen();

        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
    }

    @Test
    void platzhalterAehnlicherOriginaltextWirdVomKernGeschuetztUndStoertDieZeilenNicht() {
        String eingabe = "Beispiel [[EMAIL_1]] und anna@example.org";

        scannen(sitzung, eingabe);

        assertEquals(1, sitzung.zeilen().size());
        String platzhalter = sitzung.zeilen().get(0).platzhalter();
        assertTrue(sitzung.textFuerKi().contains(platzhalter));
        // Der Kern schuetzt den Literaltext zusaetzlich (TEXT-Platzhalter); die Zuordnung hat daher zwei Eintraege.
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    // ---------------------------------------------------------------- PlatzhalterJeFund

    @Test
    void platzhalterTabelleUnterscheidetTypUndWert() {
        PlaceholderMapping mapping = PlaceholderMapping.of(
                Map.of("[[EMAIL_1]]", "wert", "[[TEXT_2]]", "wert", "[[IP_3]]", "anderer"));

        Map<FundSchluessel, String> tabelle = PlatzhalterJeFund.aus(mapping);

        assertEquals(3, tabelle.size());
        assertEquals("[[EMAIL_1]]", tabelle.get(new FundSchluessel("EMAIL", "wert")));
        assertEquals("[[TEXT_2]]", tabelle.get(new FundSchluessel("TEXT", "wert")));
        assertEquals("[[IP_3]]", tabelle.get(new FundSchluessel("IP", "anderer")));
    }

    @Test
    void widerspruechlicheZuordnungWirdOhneKlartextAbgewiesen() {
        PlaceholderMapping mapping = PlaceholderMapping.of(Map.of("[[EMAIL_1]]", "geheim", "[[EMAIL_2]]", "geheim"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> PlatzhalterJeFund.aus(mapping));

        assertFalse(e.getMessage().contains("geheim"));
    }

    @Test
    void schluesselZeigtInToStringKeinenWert() {
        assertEquals("FundSchluessel[typ=EMAIL]", new FundSchluessel("EMAIL", "geheim").toString());
    }
}
