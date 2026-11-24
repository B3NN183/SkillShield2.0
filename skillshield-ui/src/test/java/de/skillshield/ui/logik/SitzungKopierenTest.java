package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.ALLE_STUFEN;
import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** "Fuer KI kopieren" (AK-30 bis AK-33, AK-35, D-6) und Kopieren aus Feldern. */
class SitzungKopierenTest {

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    private static List<Finding> funde(Sitzung s) {
        return s.zeilen().stream().map(FundZeile::fund).toList();
    }

    private static List<Finding> angewaehlt(Sitzung s) {
        return s.zeilen().stream().filter(FundZeile::angewaehlt).map(FundZeile::fund).toList();
    }

    // ---------------------------------------------------------------- AK-31: Inhalt

    @Test
    void kopiertGenauDenTextAusBereichDreiIdentischMitDemKern() {
        scannen(sitzung, ALLE_STUFEN);
        assertTrue(sitzung.zeilen().size() >= 3, "Vorbedingung: mehrere Funde");

        sitzung.fuerKiKopieren();

        PseudonymizationResult kern = new Pseudonymizer().pseudonymize(ALLE_STUFEN, angewaehlt(sitzung));
        assertEquals(List.of(kern.text()), zwischenablage.gesetzt);
        assertEquals(sitzung.textFuerKi(), zwischenablage.inhalt);
        for (Finding fund : funde(sitzung)) {
            assertFalse(zwischenablage.inhalt.contains(fund.originalValue()),
                    "ein Originalwert eines angewaehlten Fundes steht in der Zwischenablage");
        }
        assertTrue(dialoge.fragen.isEmpty(), "bei angewaehlten Funden keine Rueckfrage");
        assertEquals(1, zwischenablage.zugriffe, "genau ein Ablegen, kein Lesen, kein Leeren");
    }

    @Test
    void nachEinerAbwahlGehoertDerOriginalwertDesAbgewaehltenFundesZumKopiertenText() {
        scannen(sitzung, DREI_MAILS);
        sitzung.anwaehlen(1, false);
        String bernd = sitzung.zeilen().get(1).fund().originalValue();

        sitzung.fuerKiKopieren();

        assertEquals(sitzung.textFuerKi(), zwischenablage.inhalt);
        assertTrue(zwischenablage.inhalt.contains(bernd));
        assertFalse(zwischenablage.inhalt.contains(sitzung.zeilen().get(0).fund().originalValue()));
        assertTrue(dialoge.fragen.isEmpty());
    }

    // ---------------------------------------------------------------- AK-35: Meldungen

    @Test
    void dieMeldungNenntDieZahlDerErsetztenStellen() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        assertEquals("Text für KI kopiert (3 Stellen ersetzt)", sitzung.statusleiste().meldung());

        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(2, false);
        sitzung.fuerKiKopieren();
        // Der zweite Stand nummeriert neu (S-3): die Meldung trägt zusätzlich den Hinweis.
        assertEquals("Text für KI kopiert (1 Stelle ersetzt) " + Texte.PLATZHALTER_GEAENDERT,
                sitzung.statusleiste().meldung());
    }

    @Test
    void dieMeldungEnthaeltKeinenOriginalwert() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();

        for (FundZeile zeile : sitzung.zeilen()) {
            assertFalse(sitzung.statusleiste().meldung().contains(zeile.fund().originalValue()));
            assertFalse(sitzung.statusleiste().zuordnung().contains(zeile.fund().originalValue()));
        }
        assertFalse(sitzung.toString().contains("anna"));
    }

    // ---------------------------------------------------------------- AK-32: Rueckfrage bei keinem angewaehlten Fund

    @Test
    void ohneFundFragtDasProgrammMitStandardAbbrechenUndBeiAbbrechenAendertSichNichts() {
        String eingabe = "Ein Text ohne erkennbare Stelle.";
        scannen(sitzung, eingabe);
        assertEquals(0, sitzung.zeilen().size(), "Vorbedingung: kein Fund");
        String meldungVorher = sitzung.statusleiste().meldung();
        dialoge.antwort = false;

        sitzung.fuerKiKopieren();

        assertEquals(List.of(Rueckfrage.UNVERAENDERT_KOPIEREN), dialoge.fragen);
        assertEquals("Es ist kein Fund zum Ersetzen angewählt. Der Text wird unverändert kopiert. Fortfahren?",
                Rueckfrage.UNVERAENDERT_KOPIEREN.text());
        assertEquals(0, zwischenablage.zugriffe, "die Zwischenablage bleibt unberuehrt");
        assertEquals(meldungVorher, sitzung.statusleiste().meldung());
    }

    @Test
    void ohneFundUndMitBestaetigungWirdUnveraendertKopiertUndGemeldet() {
        String eingabe = "Ein Text ohne erkennbare Stelle.";
        scannen(sitzung, eingabe);
        dialoge.antwort = true;

        sitzung.fuerKiKopieren();

        assertEquals(List.of(eingabe), zwischenablage.gesetzt);
        assertEquals("Text unverändert kopiert", sitzung.statusleiste().meldung());
    }

    @Test
    void auchWennAlleFundeEinzelnAbgewaehltSindWirdGefragtNichtNachDerZahlDerPlatzhalter() {
        scannen(sitzung, DREI_MAILS);
        for (int i = 0; i < sitzung.zeilen().size(); i++) {
            sitzung.anwaehlen(i, false);
        }
        assertTrue(sitzung.zeilen().size() > 0 && angewaehlt(sitzung).isEmpty(), "Vorbedingung");
        dialoge.antwort = false;

        sitzung.fuerKiKopieren();
        assertEquals(List.of(Rueckfrage.UNVERAENDERT_KOPIEREN), dialoge.fragen);
        assertTrue(zwischenablage.gesetzt.isEmpty());

        dialoge.antwort = true;
        sitzung.fuerKiKopieren();
        assertEquals(List.of(DREI_MAILS), zwischenablage.gesetzt);
        assertEquals("Text unverändert kopiert", sitzung.statusleiste().meldung());
    }

    @Test
    void eineEinzigeAnwahlGenuegtUmOhneRueckfrageZuKopieren() {
        scannen(sitzung, DREI_MAILS);
        for (int i = 0; i < sitzung.zeilen().size(); i++) {
            sitzung.anwaehlen(i, false);
        }
        sitzung.anwaehlen(0, true);

        sitzung.fuerKiKopieren();

        assertTrue(dialoge.fragen.isEmpty());
        assertEquals(1, zwischenablage.gesetzt.size());
    }

    @Test
    void aendertSichDerTextWaehrendDerRueckfrageWirdNichtKopiert() {
        String eingabe = "Ein Text ohne erkennbare Stelle.";
        Sitzung[] ziel = new Sitzung[1];
        Dialoge verstellend = new Dialoge() {
            @Override
            public boolean bestaetigt(Rueckfrage frage) {
                ziel[0].eingabeGeaendert();
                return true;
            }

            @Override
            public boolean erneutVersuchen(Rueckfrage frage) {
                return dialoge.erneutVersuchen(frage);
            }

            @Override
            public void fehler(String text) {
                dialoge.fehler(text);
            }
        };
        Sitzung s = Sitzung.standard(zwischenablage, verstellend);
        ziel[0] = s;
        scannen(s, eingabe);

        s.fuerKiKopieren();

        assertEquals(0, zwischenablage.zugriffe);
        assertEquals(ScanZustand.VERALTET, s.scanZustand());
    }

    // ---------------------------------------------------------------- AK-30: gesperrt ohne gueltigen Scan

    @Test
    void ohneGueltigenScanTutKopierenNichts() {
        sitzung.fuerKiKopieren();
        assertEquals(0, zwischenablage.zugriffe);
        assertTrue(dialoge.fragen.isEmpty());

        scannen(sitzung, DREI_MAILS);
        sitzung.eingabeGeaendert();
        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        sitzung.fuerKiKopieren();

        assertEquals(0, zwischenablage.zugriffe);
        assertTrue(dialoge.fragen.isEmpty());
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
    }

    @Test
    void einUngueltigerScanIstKeineQuelleAuchBeiLaufendemScan() {
        scannen(sitzung, DREI_MAILS);
        dialoge.antwort = true;
        sitzung.scanAnfordern("Neuer Text anna@example.org").orElseThrow();
        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());

        sitzung.fuerKiKopieren();

        assertEquals(0, zwischenablage.zugriffe);
    }

    // ---------------------------------------------------------------- Zwischenablage nimmt nichts an

    @Test
    void nimmtDieZwischenablageNichtsAnWirdEsGemeldetUndNichtsMerktSichDasProgramm() {
        scannen(sitzung, DREI_MAILS);
        sitzung.anwaehlen(1, false);
        assertEquals(Texte.ZUORDNUNG_ERST_NACH_KOPIEREN, sitzung.statusleiste().zuordnung());
        zwischenablage.gesperrt = true;

        sitzung.fuerKiKopieren();

        assertEquals(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR, sitzung.statusleiste().meldung());
        assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), dialoge.fehler);
        // Die Zuordnung wurde nicht eingefroren: Zurueckuebersetzen bleibt gesperrt, sie folgt weiter der Auswahl.
        sitzung.anwaehlen(1, true);
        assertEquals(Texte.ZUORDNUNG_ERST_NACH_KOPIEREN, sitzung.statusleiste().zuordnung());
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        // Das Programm hat nichts kopiert und merkt sich nichts: ein gleicher fremder Inhalt wird nie geleert.
        zwischenablage.gesperrt = false;
        zwischenablage.inhalt = sitzung.textFuerKi();
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals(0, zwischenablage.leerAufrufe);
    }

    @Test
    void wirftDieZwischenablageEineAusnahmeGiltDasAlsNichtAbgelegtOhneDenTextWeiterzugeben() {
        Zwischenablage kaputt = new Zwischenablage() {
            @Override
            public boolean setzeText(String text) {
                throw new IllegalStateException("GEHEIM-MARKER " + text);
            }

            @Override
            public Optional<String> leseText() {
                return Optional.empty();
            }

            @Override
            public boolean leeren() {
                return false;
            }
        };
        Sitzung s = Sitzung.standard(kaputt, dialoge);
        scannen(s, DREI_MAILS);

        s.fuerKiKopieren();

        assertEquals(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR, s.statusleiste().meldung());
        assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), dialoge.fehler);
    }

    // ---------------------------------------------------------------- Kopieren aus Feldern

    @Test
    void einAusschnittAusEinemFeldWirdAbgelegtOhneDieZuordnungEinzufrieren() {
        scannen(sitzung, DREI_MAILS);
        sitzung.anwaehlen(1, false);

        assertTrue(sitzung.textKopieren("anna@example.org"), "true: abgelegt, die Ansicht darf ausschneiden");
        sitzung.anwaehlen(1, true);

        assertEquals(List.of("anna@example.org"), zwischenablage.gesetzt);
        assertEquals(Texte.ZUORDNUNG_ERST_NACH_KOPIEREN, sitzung.statusleiste().zuordnung());
        assertFalse(sitzung.statusleiste().meldung().contains(Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT));
    }

    @Test
    void textKopierenBeiGesperrterZwischenablageMeldetEsUndMerktSichNichts() {
        zwischenablage.gesperrt = true;

        assertFalse(sitzung.textKopieren("Ausschnitt"), "false: Die Ansicht schneidet dann nicht aus (T-6)");

        assertEquals(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR, sitzung.statusleiste().meldung());
        assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), dialoge.fehler);
        zwischenablage.gesperrt = false;
        zwischenablage.inhalt = "Ausschnitt";
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals("Ausschnitt", zwischenablage.inhalt, "nichts gemerkt: ein gleicher fremder Inhalt bleibt");
        assertEquals(0, zwischenablage.leerAufrufe);
    }

    @Test
    void einLeererAusschnittTutNichts() {
        assertFalse(sitzung.textKopieren(""));
        assertFalse(sitzung.textKopieren(null));

        assertEquals(0, zwischenablage.zugriffe);
    }

    @Test
    void kopierenEinesAusschnittsFragtNieNachUndLiestNieDieZwischenablage() {
        scannen(sitzung, DREI_MAILS);
        sitzung.textKopieren("Auswahl");

        assertEquals(List.of("Auswahl"), zwischenablage.gesetzt);
        assertTrue(dialoge.fragen.isEmpty());
        assertEquals(1, zwischenablage.zugriffe);
    }
}
