package de.skillshield.app;

import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.Sitzung;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;

/**
 * Hauptklasse der zweiten JVM des {@code SpurenfreiheitIT}: richtet die Oberfläche ein wie {@link SkillShieldApp},
 * spielt ohne Bildschirm eine vollständige Sitzung mit synthetischen Markern ab (Scan, Kopieren, Rückübersetzen,
 * Beenden über den gemeinsamen Ablauf, erzwungene Fehler im Hintergrund-Thread und auf dem Ereignis-Thread über den
 * zentralen Handler) und beendet sich mit Code 0, wenn alles wie erwartet lief. Ein Fehlschlag meldet nur den Namen
 * des Schritts (Code 3 oder 4), nie einen Marker. Schreibt sonst nichts auf Konsole oder Platte.
 */
public final class SitzungsProbe {

    /** Marker im Eingabetext und in den erzwungenen Ausnahmen; darf nirgends ausgegeben werden. */
    public static final String MARKER = "probe.marker-xylophon@example.org";

    private SitzungsProbe() {
    }

    /** Wird vom Prüfprozess gestartet. */
    public static void main(String[] args) throws Exception {
        System.setProperty("flatlaf.useNativeLibrary", "false");
        Locale.setDefault(Locale.GERMANY);
        SkillShieldApp.oberflaecheEinrichten();
        SkillShieldApp.fehlerhandlerInstallieren(() -> null);
        AtomicInteger beendet = new AtomicInteger();
        Clipboard ablage = new Clipboard("probe");
        String[] schritt = {"start"};
        try {
            SwingUtilities.invokeAndWait(() -> sitzung(ablage, beendet, schritt));
            schritt[0] = "fehler-im-thread";
            Thread thread = new Thread(() -> {
                throw new IllegalStateException(MARKER);
            });
            thread.start();
            thread.join();
            schritt[0] = "fehler-auf-ereignis-thread";
            SwingUtilities.invokeLater(() -> {
                throw new IllegalStateException(MARKER);
            });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
        } catch (Throwable e) {
            System.err.println("PROBE-FEHLER im Schritt " + schritt[0] + ": " + e.getClass().getName());
            System.exit(3);
        }
        System.exit(0);
    }

    private static void sitzung(Clipboard ablage, AtomicInteger beendet, String[] schritt) {
        Dialoge ja = new Dialoge() {
            @Override
            public boolean bestaetigt(Rueckfrage frage) {
                return true;
            }

            @Override
            public boolean erneutVersuchen(Rueckfrage frage) {
                return false;
            }

            @Override
            public void fehler(String text) {
            }
        };
        SkillShieldApp.Bausatz bausatz = SkillShieldApp.bauen(ablage, ja, beendet::incrementAndGet);
        Sitzung sitzung = bausatz.sitzung();
        schritt[0] = "scan";
        ScanAuftrag auftrag = sitzung.scanAnfordern("Kontakt " + MARKER + ", Server 192.0.2.7.").orElseThrow();
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
        schritt[0] = "kopieren";
        sitzung.alleAnwaehlen();
        sitzung.fuerKiKopieren();
        if (!ablage.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            System.err.println("PROBE-FEHLER im Schritt kopieren: nichts in der Ablage");
            System.exit(4);
        }
        schritt[0] = "rueckuebersetzen";
        sitzung.zurueckuebersetzen(sitzung.textFuerKi());
        schritt[0] = "beenden";
        bausatz.beenden().run();
        if (beendet.get() != 1 || ablage.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            System.err.println("PROBE-FEHLER im Schritt beenden: Ablage nicht geleert oder Prozessende fehlt");
            System.exit(4);
        }
    }
}
