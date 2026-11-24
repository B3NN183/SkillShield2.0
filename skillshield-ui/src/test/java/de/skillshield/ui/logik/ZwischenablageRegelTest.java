package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Zwischenablage-Regel (D-6, AK-34): Geleert wird nur, wenn die Zwischenablage noch genau den zuletzt vom Programm
 * kopierten Text enthaelt; sonst bleibt sie unberuehrt. Die Ausloesung durch Zuruecksetzen und Beenden folgt in T-5;
 * hier wird die Regel selbst geprueft.
 */
class ZwischenablageRegelTest {

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    @Test
    void derFuerKiKopierteTextWirdGeleert() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertNull(zwischenablage.inhalt);
        assertEquals(1, zwischenablage.leerAufrufe);
    }

    @Test
    void derRueckuebersetzteTextWirdGeleert() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        sitzung.ergebnisKopieren();
        assertEquals("anna@example.org", zwischenablage.inhalt, "Vorbedingung: das Ergebnis liegt im Klartext vor");

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertNull(zwischenablage.inhalt);
    }

    @Test
    void auchEinPerStrgCKopierterAusschnittWirdGeleert() {
        sitzung.textKopieren("Ausschnitt");

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertNull(zwischenablage.inhalt);
    }

    @Test
    void einFremderInhaltBleibtUnberuehrtUndDerVergleichstextWirdVerworfen() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        zwischenablage.inhalt = "Etwas, das der Anwender selbst kopiert hat";

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals("Etwas, das der Anwender selbst kopiert hat", zwischenablage.inhalt);
        assertEquals(0, zwischenablage.leerAufrufe);
        // Verworfen: ein spaeter wieder gleicher Inhalt gilt nicht mehr als eigener.
        zwischenablage.inhalt = sitzung.textFuerKi();
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals(0, zwischenablage.leerAufrufe);
    }

    @Test
    void nurDerLetzteKopierteTextZaehlt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        String ersterText = zwischenablage.inhalt;
        sitzung.textKopieren("zweiter Text");
        zwischenablage.inhalt = ersterText;

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(ersterText, zwischenablage.inhalt, "der erste Text ist nicht der zuletzt kopierte");
        assertEquals(0, zwischenablage.leerAufrufe);
    }

    @Test
    void derVergleichIstZeichengenau() {
        sitzung.textKopieren("Text");
        zwischenablage.inhalt = "Text ";

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals("Text ", zwischenablage.inhalt);
        assertEquals(0, zwischenablage.leerAufrufe);
    }

    @Test
    void ohneKopierenGreiftDasProgrammNieAufDieZwischenablageZu() {
        zwischenablage.inhalt = "fremd";

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(0, zwischenablage.zugriffe);
        assertEquals("fremd", zwischenablage.inhalt);
    }

    @Test
    void nachDemLeerenIstDerVergleichstextVerworfen() {
        sitzung.textKopieren("Text");
        sitzung.eigenenZwischenablageInhaltLeeren();
        int zugriffe = zwischenablage.zugriffe;
        zwischenablage.inhalt = "Text";

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(zugriffe, zwischenablage.zugriffe, "zweiter Aufruf: kein Zugriff mehr");
        assertEquals("Text", zwischenablage.inhalt);
    }

    @Test
    void eineGesperrteZwischenablageErgibtKeineAusnahmeUndKeinenDialogUndWirdSpaeterGeleert() {
        sitzung.textKopieren("Text");
        zwischenablage.gesperrt = true;

        assertDoesNotThrow(sitzung::eigenenZwischenablageInhaltLeeren);

        assertEquals(0, zwischenablage.leerAufrufe, "nicht lesbar: nicht beweisbar eigener Inhalt");
        assertEquals(0, dialoge.fehler.size());
        assertEquals(0, dialoge.fragen.size());
        // Spaeter wieder erreichbar: der Vergleichstext war noch da.
        zwischenablage.gesperrt = false;
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertNull(zwischenablage.inhalt);
    }

    @Test
    void scheitertNurDasLeerenBleibtDerTextErhaltenUndWirdSpaeterGeleert() {
        sitzung.textKopieren("Text");
        zwischenablage.nurLeerenGesperrt = true;

        assertDoesNotThrow(sitzung::eigenenZwischenablageInhaltLeeren);

        assertEquals("Text", zwischenablage.inhalt, "Lesen klappte, Leeren scheiterte: der Text liegt noch da");
        assertEquals(1, zwischenablage.leerAufrufe);
        assertEquals(0, dialoge.fehler.size());
        zwischenablage.nurLeerenGesperrt = false;
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertNull(zwischenablage.inhalt);
        assertEquals(2, zwischenablage.leerAufrufe);
    }

    @Test
    void eineAusnahmeDerZwischenablageBeimLesenWirdVerschluckt() {
        Zwischenablage kaputt = new Zwischenablage() {
            @Override
            public boolean setzeText(String text) {
                return true;
            }

            @Override
            public Optional<String> leseText() {
                throw new IllegalStateException("GEHEIM-MARKER");
            }

            @Override
            public boolean leeren() {
                throw new IllegalStateException("GEHEIM-MARKER");
            }
        };
        Sitzung s = Sitzung.standard(kaputt, dialoge);
        s.textKopieren("Text");

        assertDoesNotThrow(s::eigenenZwischenablageInhaltLeeren);

        assertEquals(0, dialoge.fehler.size());
    }

    @Test
    void liegtKeinTextInDerZwischenablageGeschiehtNichts() {
        sitzung.textKopieren("Text");
        zwischenablage.inhalt = null;

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(0, zwischenablage.leerAufrufe);
    }
}
