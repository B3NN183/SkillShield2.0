package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.Texte;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Dialoge (A-3, AK-2, Sicherheitsvorgabe 7): Optionen, Standardoption und Zuordnung Rückgabewert zu Antwort ohne
 * sichtbaren Dialog, über eine Attrappe der Anzeige. Keine Ausnahme beim Anzeigen wird in eine Antwort umgedeutet.
 */
class SwingDialogeTest {

    /** Aufruf der Anzeige, wie ihn die Attrappe sieht. */
    private record Aufruf(String text, String titel, int art, Object[] optionen, Object standard, boolean auf) { }

    private final List<Aufruf> aufrufe = new ArrayList<>();

    private SwingDialoge dialoge(int rueckgabe) {
        return new SwingDialoge(() -> null, (eltern, text, titel, art, optionen, standard) -> {
            aufrufe.add(new Aufruf(text, titel, art, optionen, standard, SwingUtilities.isEventDispatchThread()));
            return rueckgabe;
        });
    }

    @Test
    void rueckfragenHabenJaUndAbbrechenMitStandardAbbrechen() {
        for (Rueckfrage frage : List.of(Rueckfrage.UNVERAENDERT_KOPIEREN, Rueckfrage.NEUER_SCAN,
                Rueckfrage.ZURUECKSETZEN, Rueckfrage.BEENDEN)) {
            assertArrayEquals(new Object[] {"Ja", "Abbrechen"}, SwingDialoge.optionen(frage), frage.name());
            assertEquals("Abbrechen", SwingDialoge.standardOption(frage), frage.name());
        }
    }

    @Test
    void zwischenablageRueckfrageHatErneutVersuchenAlsStandard() {
        Rueckfrage frage = Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT;
        assertArrayEquals(new Object[] {"Erneut versuchen", "Trotzdem beenden"}, SwingDialoge.optionen(frage));
        assertEquals("Erneut versuchen", SwingDialoge.standardOption(frage));
    }

    @Test
    void nurJaBestaetigt() {
        assertTrue(SwingDialoge.bestaetigtNach(0));
        assertFalse(SwingDialoge.bestaetigtNach(1));
        assertFalse(SwingDialoge.bestaetigtNach(JOptionPane.CLOSED_OPTION));
        assertFalse(SwingDialoge.bestaetigtNach(5));
    }

    @Test
    void nurAusdruecklichesTrotzdemBeendenIstKeinErneutVersuchen() {
        assertTrue(SwingDialoge.erneutVersuchenNach(0));
        assertFalse(SwingDialoge.erneutVersuchenNach(1));
        assertTrue(SwingDialoge.erneutVersuchenNach(JOptionPane.CLOSED_OPTION), "Esc und Schließen: erneut versuchen");
    }

    @Test
    void bestaetigtZeigtFestenTextMitDenOptionen() {
        SwingDialoge dialoge = dialoge(0);
        assertTrue(dialoge.bestaetigt(Rueckfrage.BEENDEN));
        Aufruf aufruf = aufrufe.get(0);
        assertEquals(SwingDialoge.umbrechen(Rueckfrage.BEENDEN.text(), SwingDialoge.UMBRUCH), aufruf.text());
        assertEquals(Rueckfrage.BEENDEN.text(), aufruf.text().replace('\n', ' '));
        assertEquals(Texte.TITEL_RUECKFRAGE, aufruf.titel());
        assertEquals(JOptionPane.QUESTION_MESSAGE, aufruf.art());
        assertArrayEquals(new Object[] {"Ja", "Abbrechen"}, aufruf.optionen());
        assertEquals("Abbrechen", aufruf.standard());
        assertFalse(dialoge(1).bestaetigt(Rueckfrage.ZURUECKSETZEN));
        assertFalse(dialoge(JOptionPane.CLOSED_OPTION).bestaetigt(Rueckfrage.NEUER_SCAN));
    }

    @Test
    void erneutVersuchenZeigtDieZweiAntworten() {
        assertTrue(dialoge(0).erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        assertFalse(dialoge(1).erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        assertTrue(dialoge(JOptionPane.CLOSED_OPTION).erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        Aufruf aufruf = aufrufe.get(0);
        assertArrayEquals(new Object[] {"Erneut versuchen", "Trotzdem beenden"}, aufruf.optionen());
        assertEquals("Erneut versuchen", aufruf.standard());
    }

    @Test
    void zwischenablageRueckfrageGehtNieUeberBestaetigt() {
        assertThrows(IllegalArgumentException.class,
                () -> dialoge(0).bestaetigt(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        assertEquals(List.of(), aufrufe, "es wurde nichts angezeigt");
    }

    @Test
    void andereRueckfragenGehenNieUeberErneutVersuchen() {
        assertThrows(IllegalArgumentException.class, () -> dialoge(0).erneutVersuchen(Rueckfrage.BEENDEN));
        assertEquals(List.of(), aufrufe);
    }

    @Test
    void eineAusnahmeBeimAnzeigenWirdWeitergegebenNieAlsAntwortGeliefert() {
        SwingDialoge defekt = new SwingDialoge(() -> null, (eltern, text, titel, art, optionen, standard) -> {
            throw new IllegalStateException("kein Bildschirm");
        });
        assertThrows(IllegalStateException.class,
                () -> defekt.erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        assertThrows(IllegalStateException.class, () -> defekt.bestaetigt(Rueckfrage.BEENDEN));
    }

    @Test
    void ohneBildschirmWirftDieEchteAnzeigeStattEineAntwortZuGeben() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "nur ohne Bildschirm");
        SwingDialoge echt = new SwingDialoge(() -> null);
        assertThrows(HeadlessException.class,
                () -> echt.erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT));
        assertThrows(HeadlessException.class, () -> echt.bestaetigt(Rueckfrage.BEENDEN));
    }

    @Test
    void fehlerdialogHatNurOkUndFestenTextAufDemEreignisThread() {
        SwingDialoge dialoge = dialoge(0);
        String text = Texte.FEHLER + " (java.lang.IllegalStateException)";
        edt(() -> dialoge.fehler(text));
        assertEquals(1, aufrufe.size());
        Aufruf aufruf = aufrufe.get(0);
        assertEquals(text, aufruf.text().replace('\n', ' '));
        assertEquals(Texte.TITEL_FEHLER, aufruf.titel());
        assertEquals(JOptionPane.ERROR_MESSAGE, aufruf.art());
        assertArrayEquals(new Object[] {"OK"}, aufruf.optionen());
        assertEquals("OK", aufruf.standard());
        assertTrue(aufruf.auf());
    }

    @Test
    void fehlerVonEinemAnderenThreadWerdenAufDemEreignisThreadGezeigt() {
        SwingDialoge dialoge = dialoge(0);
        dialoge.fehler("Fehler aus dem Hintergrund");
        edt(() -> { });
        assertEquals(1, aufrufe.size());
        assertTrue(aufrufe.get(0).auf());
    }

    @Test
    void umbruchHaeltWoerterGanzUndVeraendertDenInhaltNicht() {
        for (Rueckfrage frage : Rueckfrage.values()) {
            String umbrochen = SwingDialoge.umbrechen(frage.text(), SwingDialoge.UMBRUCH);
            assertEquals(frage.text(), umbrochen.replace('\n', ' '), frage.name());
            for (String zeile : umbrochen.split("\n")) {
                assertTrue(zeile.length() <= SwingDialoge.UMBRUCH, zeile);
            }
            assertFalse(frage.text().toLowerCase(Locale.ROOT).startsWith("<html"));
        }
        assertEquals("", SwingDialoge.umbrechen("", 10));
        assertEquals("langeswortohneumbruch", SwingDialoge.umbrechen("langeswortohneumbruch", 5));
    }

    @Test
    void festeBeschriftungenSindDeutsch() {
        assertEquals("Ja", Texte.JA);
        assertEquals("Abbrechen", Texte.ABBRECHEN);
        assertEquals("OK", Texte.OK);
        assertEquals("Erneut versuchen", Texte.ERNEUT_VERSUCHEN);
        assertEquals("Trotzdem beenden", Texte.TROTZDEM_BEENDEN);
    }

    @Test
    void elternfensterKommtVomLieferanten() {
        Component[] gesehen = new Component[1];
        JPanel eltern = new JPanel();
        SwingDialoge dialoge = new SwingDialoge(() -> eltern, (e, text, titel, art, optionen, standard) -> {
            gesehen[0] = e;
            return 0;
        });
        dialoge.bestaetigt(Rueckfrage.BEENDEN);
        assertSame(eltern, gesehen[0]);
    }

    @Test
    void scheitertDieAnzeigeVonFremdemThreadWirdDasVerworfenStattAnDenEreignisThreadZuGeben() {
        List<Throwable> unbehandelt = new CopyOnWriteArrayList<>();
        Thread.UncaughtExceptionHandler alt = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, fehler) -> unbehandelt.add(fehler));
        try {
            SwingDialoge defekt = new SwingDialoge(() -> null, (eltern, text, titel, art, optionen, standard) -> {
                aufrufe.add(new Aufruf(text, titel, art, optionen, standard, true));
                throw new IllegalStateException("Marker-Anzeige");
            });
            defekt.fehler("Fehler aus dem Hintergrund");
            edt(() -> { });
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(alt);
        }
        assertEquals(1, aufrufe.size(), "die Anzeige wurde versucht");
        assertEquals(List.of(), unbehandelt, "keine Ausnahme auf dem Ereignis-Thread");
    }
}
