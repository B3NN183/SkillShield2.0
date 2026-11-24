package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.ScanZustand;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.TestDialoge;
import de.skillshield.ui.swing.SwingAttrappen.TestZwischenablage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Hintergrund-Scan (AK-10, Sicherheitsvorgabe 4): Im Hintergrund läuft nur {@code ScanAuftrag.ausfuehren()}, genau
 * ein Rückruf je Auftrag kommt auf dem Ereignis-Thread an, Fehler als fester Text. Das Warten geschieht über einen
 * Zähler, den der Beobachter der Sitzung herunterzählt, mit Zeitgrenze, ohne Raten.
 */
class ScanStarterTest {

    private static final long GRENZE_SEKUNDEN = 30;

    private final TestDialoge dialoge = new TestDialoge();
    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final CountDownLatch fertig = new CountDownLatch(1);
    private final List<Boolean> beobachterAufEdt = new ArrayList<>();
    private final List<ScanZustand> zustaende = new ArrayList<>();
    private final AtomicReference<Thread> scanThread = new AtomicReference<>();
    private final List<Throwable> unerwartet = new ArrayList<>();

    private Sitzung sitzung(Function<String, List<Finding>> scanner) {
        return edt(() -> {
            Sitzung s = new Sitzung(text -> {
                scanThread.set(Thread.currentThread());
                return scanner.apply(text);
            }, zwischenablage, dialoge);
            s.beobachten(() -> {
                beobachterAufEdt.add(SwingUtilities.isEventDispatchThread());
                zustaende.add(s.scanZustand());
                if (s.scanZustand() != ScanZustand.LAEUFT) {
                    fertig.countDown();
                }
            });
            return s;
        });
    }

    private void starten(Sitzung s, String text, ScanStarter starter) throws InterruptedException {
        edt(() -> {
            ScanAuftrag auftrag = s.scanAnfordern(text).orElseThrow();
            starter.starte(s, auftrag);
            // Das Fenster blockiert nicht: Der Aufruf kehrt zurück, der Scan läuft noch oder ist gerade fertig.
            assertTrue(s.scanZustand() == ScanZustand.LAEUFT || s.scanZustand() == ScanZustand.GUELTIG);
        });
        assertTrue(fertig.await(GRENZE_SEKUNDEN, TimeUnit.SECONDS), "Scan nicht rechtzeitig fertig");
        edt(() -> { });
    }

    @Test
    void ergebnisKommtAufDemEreignisThreadAnWaehrendDerScanImHintergrundLaeuft() throws InterruptedException {
        Sitzung s = sitzung(TextScanner.standard()::scan);
        starten(s, DREI_FUNDE, ScanStarter.imHintergrund(unerwartet::add));
        edt(() -> {
            assertEquals(ScanZustand.GUELTIG, s.scanZustand());
            assertEquals(3, s.zeilen().size());
        });
        assertFalse(scanThread.get() == null);
        assertFalse(SwingUtilities.isEventDispatchThread());
        assertFalse(scanThread.get().getName().contains("AWT-EventQueue"), "der Scan lief auf dem Ereignis-Thread");
        assertEquals(List.of(), unerwartet);
        assertFalse(beobachterAufEdt.contains(false), "jeder Rückruf der Sitzung kam auf dem Ereignis-Thread");
        assertEquals(1, zustaende.stream().filter(z -> z == ScanZustand.GUELTIG).count(), "genau ein Rückruf");
    }

    @Test
    void fehlerImScanKommenAlsFesterTextOhneMeldungAufDemEreignisThread() throws InterruptedException {
        Sitzung s = sitzung(text -> {
            throw new IllegalStateException("Marker-Scanfehler");
        });
        starten(s, DREI_FUNDE, ScanStarter.imHintergrund(unerwartet::add));
        edt(() -> {
            assertEquals(ScanZustand.KEIN_SCAN, s.scanZustand());
            assertEquals(1, dialoge.fehler.size(), "genau ein Rückruf");
            assertTrue(dialoge.fehler.get(0).startsWith(Texte.FEHLER));
            assertTrue(dialoge.fehler.get(0).contains("java.lang.IllegalStateException"));
            assertFalse(dialoge.fehler.get(0).contains("Marker"));
            assertEquals(List.of(true), dialoge.fehlerAufEdt);
        });
        assertEquals(List.of(), unerwartet);
        assertFalse(beobachterAufEdt.contains(false));
    }

    @Test
    void eineAusnahmeBeimUebergebenBeendetDenEreignisThreadNicht() throws InterruptedException {
        Sitzung s = sitzung(TextScanner.standard()::scan);
        edt(() -> s.beobachten(() -> {
            if (s.scanZustand() == ScanZustand.GUELTIG) {
                throw new IllegalStateException("Marker-Beobachterfehler");
            }
        }));
        starten(s, DREI_FUNDE, ScanStarter.imHintergrund(unerwartet::add));
        assertEquals(1, unerwartet.size());
        assertTrue(unerwartet.get(0) instanceof IllegalStateException);
    }

    @Test
    void panelMitEchtemHintergrundScanLiefertDieFundeDesKerns() throws InterruptedException {
        TestDialoge d = new TestDialoge();
        TestZwischenablage z = new TestZwischenablage();
        Object[] teile = edt(() -> {
            Sitzung s = Sitzung.standard(z, d);
            HauptPanel p = new HauptPanel(s, z, d, () -> { });
            CountDownLatch gueltig = new CountDownLatch(1);
            s.beobachten(() -> {
                if (s.scanZustand() == ScanZustand.GUELTIG) {
                    gueltig.countDown();
                }
            });
            p.eingabeBereich().eingabe().setText(DREI_FUNDE);
            p.scannen();
            return new Object[] {s, p, gueltig};
        });
        assertTrue(((CountDownLatch) teile[2]).await(GRENZE_SEKUNDEN, TimeUnit.SECONDS));
        edt(() -> {
            HauptPanel p = (HauptPanel) teile[1];
            assertEquals(3, p.fundeBereich().tabelle().getRowCount());
            assertTrue(p.eingabeBereich().eingabe().isEditable());
            assertTrue(p.eingabeBereich().scannen().isEnabled());
        });
        assertEquals(List.of(), d.fehler);
    }
}
