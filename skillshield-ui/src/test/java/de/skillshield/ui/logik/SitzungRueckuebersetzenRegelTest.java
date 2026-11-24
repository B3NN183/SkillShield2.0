package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Regeln des Zurueckuebersetzens aus der zweiten Pruefrunde von T-4: nur nach dem Kopieren im aktuellen Scan (S-2),
 * Hinweis bei geaenderten Platzhaltern (S-3), Laengenbegrenzung der Antwort (S-4) und der Fehlerzweig der
 * Rueckuebersetzung (R-2).
 */
class SitzungRueckuebersetzenRegelTest {

    private static final String TEXT_B = "c: carla@example.org, d: dora@example.org";
    private static final String CARLA = "carla@example.org";

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    // ---------------------------------------------------------------- S-2

    @Test
    void nachNeuemScanIstZurueckuebersetzenBisZumKopierenGesperrtUndEineAntwortZuAWirdNichtUebersetzt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        String antwortZuA = zwischenablage.inhalt;
        sitzung.eingabeGeaendert();
        dialoge.antwort = true;
        scannen(sitzung, TEXT_B);
        sitzung.kiAntwortGeaendert(false);

        Bedienzustand gesperrt = sitzung.bedienzustand();
        assertFalse(gesperrt.istAktiv(Funktion.ZURUECKUEBERSETZEN));
        assertEquals(Texte.ERST_KOPIEREN, gesperrt.sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        assertEquals(Texte.ZUORDNUNG_ERST_NACH_KOPIEREN, sitzung.statusleiste().zuordnung());
        Statusleiste vorher = sitzung.statusleiste();

        sitzung.zurueckuebersetzen(antwortZuA);

        assertTrue(sitzung.rueckuebersetzung().isEmpty(), "Antwort zu A nicht mit der Zuordnung von B uebersetzt");
        assertEquals(vorher, sitzung.statusleiste(), "ein Aufruf im gesperrten Zustand aendert nichts");
        assertEquals(List.of(), dialoge.fehler);

        sitzung.fuerKiKopieren();

        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        assertEquals(CARLA, sitzung.rueckuebersetzung().orElseThrow().text());
    }

    @Test
    void einAufrufImGesperrtenZustandAendertNichtsUndDasErgebnisDesAltenScansIstVerworfen() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        assertTrue(sitzung.rueckuebersetzung().isPresent());
        dialoge.antwort = true;
        scannen(sitzung, TEXT_B);
        // T-5: Das Ergebnis gehoert zur alten Zuordnung und wird mit dem neuen Scan verworfen.
        assertTrue(sitzung.rueckuebersetzung().isEmpty());
        Statusleiste vorher = sitzung.statusleiste();

        sitzung.zurueckuebersetzen("[[EMAIL_2]]");

        assertTrue(sitzung.rueckuebersetzung().isEmpty());
        assertEquals(vorher, sitzung.statusleiste());
    }

    @Test
    void dieSperrgruendeUndDieZuordnungsanzeigeSindFesteTexte() {
        sitzung.kiAntwortGeaendert(false);
        assertEquals(Texte.ERST_SCANNEN, sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        scannen(sitzung, DREI_MAILS);
        assertEquals("Erst den Text für die KI kopieren. Zurückübersetzt wird immer die Antwort zum zuletzt "
                + "kopierten Text.", sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        assertEquals("Zuordnung: erst nach dem Kopieren für die KI", sitzung.statusleiste().zuordnung());
    }

    @Test
    void einFehlgeschlagenesKopierenSchaltetZurueckuebersetzenNichtFrei() {
        scannen(sitzung, DREI_MAILS);
        sitzung.kiAntwortGeaendert(false);
        zwischenablage.gesperrt = true;

        sitzung.fuerKiKopieren();

        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
    }

    // ---------------------------------------------------------------- S-3

    @Test
    void identischeZuordnungBeimErneutenKopierenErzeugtKeinenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();

        sitzung.fuerKiKopieren();

        assertEquals("Text für KI kopiert (3 Stellen ersetzt)", sitzung.statusleiste().meldung());
    }

    @Test
    void dasErsteKopierenHatKeinenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.anwaehlen(0, false);

        sitzung.fuerKiKopieren();

        assertFalse(sitzung.statusleiste().meldung().contains(Texte.PLATZHALTER_GEAENDERT));
    }

    @Test
    void anderesNummerierenNachGeaenderterAuswahlErzeugtDenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(2, false);

        sitzung.fuerKiKopieren();

        assertTrue(sitzung.statusleiste().meldung().endsWith(Texte.PLATZHALTER_GEAENDERT));
        assertEquals("Achtung: Die Platzhalter haben sich gegenüber dem zuvor kopierten Text geändert. "
                + "Antworten auf den früheren Text nicht mehr zurückübersetzen.", Texte.PLATZHALTER_GEAENDERT);
    }

    @Test
    void wegfallendePlatzhalterErzeugenDenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.anwaehlen(1, false);

        sitzung.fuerKiKopieren();

        assertEquals("Text für KI kopiert (2 Stellen ersetzt) " + Texte.PLATZHALTER_GEAENDERT,
                sitzung.statusleiste().meldung());
    }

    @Test
    void nurZusaetzlichePlatzhalterErzeugenKeinenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.anwaehlen(1, false);
        sitzung.fuerKiKopieren();
        sitzung.alleAnwaehlen();

        sitzung.fuerKiKopieren();

        assertEquals("Text für KI kopiert (3 Stellen ersetzt)", sitzung.statusleiste().meldung());
    }

    @Test
    void auchDasErsteKopierenNachEinemNeuenScanWarntWennSichPlatzhalterAendern() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;
        scannen(sitzung, TEXT_B);

        sitzung.fuerKiKopieren();

        assertTrue(sitzung.statusleiste().meldung().endsWith(Texte.PLATZHALTER_GEAENDERT));
    }

    @Test
    void einNeuerScanDesselbenTextesErzeugtKeinenHinweis() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;
        scannen(sitzung, DREI_MAILS);

        sitzung.fuerKiKopieren();

        assertEquals("Text für KI kopiert (3 Stellen ersetzt)", sitzung.statusleiste().meldung());
    }

    @Test
    void einGescheitertesKopierenAendertDenVergleichsstandNicht() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(2, false);
        zwischenablage.gesperrt = true;
        sitzung.fuerKiKopieren();
        zwischenablage.gesperrt = false;
        sitzung.alleAnwaehlen();

        sitzung.fuerKiKopieren();

        assertEquals("Text für KI kopiert (3 Stellen ersetzt)", sitzung.statusleiste().meldung());
    }

    // ---------------------------------------------------------------- S-4

    @Test
    void eineAntwortGenauAnDerGrenzeWirdZurueckuebersetzt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        String antwort = "[[EMAIL_1]]" + "x".repeat(Sitzung.MAX_ZEICHEN - "[[EMAIL_1]]".length());
        assertEquals(Sitzung.MAX_ZEICHEN, antwort.length());

        sitzung.zurueckuebersetzen(antwort);

        assertEquals("anna@example.org" + antwort.substring("[[EMAIL_1]]".length()),
                sitzung.rueckuebersetzung().orElseThrow().text());
    }

    @Test
    void eineAntwortEinZeichenUeberDerGrenzeWirdMitFestemTextAbgelehnt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.zurueckuebersetzen("[[EMAIL_2]]");
        Rueckuebersetzung vorher = sitzung.rueckuebersetzung().orElseThrow();
        ScanZustand zustand = sitzung.scanZustand();
        String textFuerKi = sitzung.textFuerKi();

        sitzung.zurueckuebersetzen("x".repeat(Sitzung.MAX_ZEICHEN + 1));

        assertEquals("Die Antwort ist zu lang (höchstens 1.000.000 Zeichen). Es wurde nicht zurückübersetzt.",
                sitzung.statusleiste().meldung());
        assertEquals(Texte.antwortZuLang(Sitzung.MAX_ZEICHEN), sitzung.statusleiste().meldung());
        assertEquals(vorher, sitzung.rueckuebersetzung().orElseThrow());
        assertEquals(zustand, sitzung.scanZustand());
        assertEquals(textFuerKi, sitzung.textFuerKi());
        assertEquals(List.of(), dialoge.fehler);
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
    }

    // ---------------------------------------------------------------- R-2: Fehlerzweig der Rueckuebersetzung

    @Test
    void scheitertDieRueckuebersetzungBleibtAllesWieVorherUndNurFesteTexteWerdenGemeldet() {
        AtomicBoolean scheitern = new AtomicBoolean();
        Reidentifier kern = new Reidentifier();
        Sitzung s = new Sitzung(TextScanner.standard()::scan, zwischenablage, dialoge, (antwort, zuordnung) -> {
            if (scheitern.get()) {
                throw new IllegalStateException("GEHEIM-MARKER anna@example.org");
            }
            return kern.restore(antwort, zuordnung);
        });
        scannen(s, DREI_MAILS);
        s.fuerKiKopieren();
        s.zurueckuebersetzen("[[EMAIL_1]]");
        Rueckuebersetzung vorher = s.rueckuebersetzung().orElseThrow();
        Bedienzustand bedienzustand = s.bedienzustand();
        String textFuerKi = s.textFuerKi();
        String zuordnung = s.statusleiste().zuordnung();
        scheitern.set(true);

        s.zurueckuebersetzen("[[EMAIL_2]]");

        assertEquals(Texte.RUECKUEBERSETZEN_FEHLGESCHLAGEN, s.statusleiste().meldung());
        assertEquals("Beim Zurückübersetzen ist ein Fehler aufgetreten. Die Antwort wurde nicht zurückübersetzt.",
                s.statusleiste().meldung());
        assertEquals(List.of(Texte.RUECKUEBERSETZEN_FEHLGESCHLAGEN + " (java.lang.IllegalStateException)"),
                dialoge.fehler);
        assertFalse(dialoge.fehler.get(0).contains("GEHEIM"), "nie die Meldung der Ausnahme");
        assertFalse(dialoge.fehler.get(0).contains("example.org"));
        assertFalse(s.statusleiste().meldung().contains("kopiert"), "es geht nicht ums Kopieren");
        assertEquals(vorher, s.rueckuebersetzung().orElseThrow(), "Ergebnis unveraendert");
        assertEquals(ScanZustand.GUELTIG, s.scanZustand());
        assertEquals(bedienzustand, s.bedienzustand());
        assertEquals(textFuerKi, s.textFuerKi());
        assertEquals(zuordnung, s.statusleiste().zuordnung());

        scheitern.set(false);
        s.zurueckuebersetzen("[[EMAIL_2]]");
        assertEquals("bernd@example.org", s.rueckuebersetzung().orElseThrow().text());
    }
}
