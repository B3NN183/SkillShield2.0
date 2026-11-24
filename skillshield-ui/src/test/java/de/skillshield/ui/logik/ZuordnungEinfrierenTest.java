package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import org.junit.jupiter.api.Test;

/**
 * Abweichung A-2 (Kap. 3.5): Fuer die Rueckuebersetzung gilt die beim Kopieren aus Bereich 3 eingefrorene Zuordnung,
 * nicht die danach geaenderte Auswahl; vor dem Kopieren folgt sie der Auswahl. Nach der Abwahl nummeriert der Kern
 * neu, sonst wuerde eine KI-Antwort zum kopierten Text mit falschen Originalwerten gefuellt.
 */
class ZuordnungEinfrierenTest {

    private static final String EINGABE = "a: anna@example.org, b: bernd@example.org.";
    private static final String ANNA = "anna@example.org";
    private static final String BERND = "bernd@example.org";

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    private String zurueck(String antwort) {
        sitzung.zurueckuebersetzen(antwort);
        return sitzung.rueckuebersetzung().orElseThrow().text();
    }

    @Test
    void nachDemKopierenUndAbwahlWirdWeiterMitDenWertenDesKopiertenTextesUebersetzt() {
        scannen(sitzung, EINGABE);
        sitzung.fuerKiKopieren();
        String kopiert = zwischenablage.inhalt;
        assertEquals("a: [[EMAIL_1]], b: [[EMAIL_2]].", kopiert);

        sitzung.anwaehlen(0, false);

        // Der Live-Text nummeriert neu: bernd ist jetzt [[EMAIL_1]] ...
        assertEquals("a: " + ANNA + ", b: [[EMAIL_1]].", sitzung.textFuerKi());
        // ... die Antwort zum kopierten Text wird trotzdem richtig aufgeloest.
        assertEquals(BERND + " und " + ANNA, zurueck("[[EMAIL_2]] und [[EMAIL_1]]"));
        assertEquals(EINGABE, zurueck(kopiert), "Roundtrip des kopierten Textes bleibt zeichengenau");
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void nachDemKopierenNenntDieStatusleisteDieAbweichungUndVorherNicht() {
        scannen(sitzung, EINGABE);
        sitzung.anwaehlen(0, false);
        assertFalse(sitzung.statusleiste().meldung().contains(Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT));
        sitzung.alleAnwaehlen();
        sitzung.fuerKiKopieren();
        assertFalse(sitzung.statusleiste().meldung().contains("geändert"));

        sitzung.anwaehlen(0, false);

        assertTrue(sitzung.statusleiste().meldung().endsWith(Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT),
                sitzung.statusleiste().meldung());
        assertTrue(sitzung.statusleiste().meldung().startsWith("2 Funde, 1 angewählt, 0 fraglich. 1 Fund wird"));

        // Zurueck zur kopierten Auswahl: kein Unterschied mehr, kein Hinweis.
        sitzung.anwaehlen(0, true);
        assertEquals("2 Funde, 2 angewählt, 0 fraglich.", sitzung.statusleiste().meldung());
    }

    @Test
    void vorDemKopierenFolgtDieZuordnungDerAuswahl() {
        scannen(sitzung, EINGABE);
        sitzung.anwaehlen(0, false);
        // Vor dem Kopieren ist Zurueckuebersetzen gesperrt (S-2); die Zuordnung folgt weiter der Auswahl.
        sitzung.kiAntwortGeaendert(false);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        assertTrue(sitzung.rueckuebersetzung().isEmpty());
        sitzung.fuerKiKopieren();

        assertEquals(BERND, zurueck("[[EMAIL_1]]"));
        assertEquals("[[EMAIL_2]]", zurueck("[[EMAIL_2]]"), "der zweite Platzhalter existiert in diesem Stand nicht");
        assertEquals(1, sitzung.rueckuebersetzung().orElseThrow().offen().size());
        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void erneutesKopierenFriertDenNeuenStandEin() {
        scannen(sitzung, EINGABE);
        sitzung.fuerKiKopieren();
        sitzung.anwaehlen(0, false);

        sitzung.fuerKiKopieren();

        assertEquals("a: " + ANNA + ", b: [[EMAIL_1]].", zwischenablage.inhalt);
        assertEquals("Text für KI kopiert (1 Stelle ersetzt) " + Texte.PLATZHALTER_GEAENDERT,
                sitzung.statusleiste().meldung());
        assertEquals(BERND, zurueck("[[EMAIL_1]]"));
        sitzung.anwaehlen(1, false);
        sitzung.anwaehlen(1, true);
        assertFalse(sitzung.statusleiste().meldung().contains(Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT));
    }

    @Test
    void eineEingabeaenderungLaesstDieEingefroreneZuordnungUnveraendert() {
        scannen(sitzung, EINGABE);
        sitzung.fuerKiKopieren();

        sitzung.eingabeGeaendert();

        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        assertEquals(ANNA, zurueck("[[EMAIL_1]]"));
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void einNeuerScanBeendetDasEinfrierenUndErsetztDieZuordnung() {
        scannen(sitzung, EINGABE);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;

        scannen(sitzung, "c: carla@example.org");
        sitzung.fuerKiKopieren();
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(0, true);

        assertEquals("carla@example.org", zurueck("[[EMAIL_1]]"));
        assertEquals("[[EMAIL_2]]", zurueck("[[EMAIL_2]]"), "Platzhalter des vorigen Scans sind nicht mehr aufloesbar");
        assertEquals(1, sitzung.rueckuebersetzung().orElseThrow().offen().size());
    }

    @Test
    void einNeuerScanOhneKopierenFolgtWiederDerAuswahl() {
        scannen(sitzung, EINGABE);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;
        scannen(sitzung, EINGABE);

        sitzung.anwaehlen(0, false);
        sitzung.kiAntwortGeaendert(false);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN), "neuer Scan: erst kopieren");
        sitzung.fuerKiKopieren();

        assertEquals(BERND, zurueck("[[EMAIL_1]]"));
    }

    @Test
    void auchEinManuellHinzugefuegterFundAendertDieEingefroreneZuordnungNicht() {
        scannen(sitzung, EINGABE + " Max Mustermann");
        sitzung.fuerKiKopieren();
        int start = (EINGABE + " Max Mustermann").indexOf("Max");

        sitzung.markierungHinzufuegen(start, start + "Max Mustermann".length());

        assertTrue(sitzung.statusleiste().meldung().endsWith(Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT));
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
        assertEquals("[[TEXT_1]]", zurueck("[[TEXT_1]]"), "der manuelle Fund gehoert nicht zum kopierten Stand");
        assertEquals(1, sitzung.rueckuebersetzung().orElseThrow().offen().size());
    }

    @Test
    void wirdDerKopiervorgangAbgebrochenBleibtDieZuordnungBeweglich() {
        scannen(sitzung, EINGABE);
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(1, false);
        dialoge.antwort = false;

        sitzung.fuerKiKopieren();
        sitzung.alleAnwaehlen();

        assertEquals(Texte.ZUORDNUNG_ERST_NACH_KOPIEREN, sitzung.statusleiste().zuordnung());
        sitzung.kiAntwortGeaendert(false);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        sitzung.fuerKiKopieren();
        assertEquals(ANNA, zurueck("[[EMAIL_1]]"));
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }
}
