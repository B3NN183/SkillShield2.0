package de.skillshield.ui.swing;

import de.skillshield.core.model.Finding;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.Sitzung;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Startet den Scan eines {@link ScanAuftrag} und gibt das Ergebnis an die {@link Sitzung} zurück. Im Hintergrund
 * läuft ausschließlich {@link ScanAuftrag#ausfuehren()}; {@code scanAbgeschlossen} und {@code scanFehlgeschlagen}
 * werden genau einmal je Auftrag auf dem Ereignis-Thread gerufen (D-11, Sicherheitsvorgabe 4). Im Test lässt sich der
 * Starter durch einen synchron ausführenden ersetzen.
 */
@FunctionalInterface
interface ScanStarter {

    /** Startet den Scan; kehrt sofort zurück. */
    void starte(Sitzung sitzung, ScanAuftrag auftrag);

    /**
     * Starter mit {@link SwingWorker}.
     *
     * @param unerwartet bekommt eine Ausnahme, die beim Übergeben des Ergebnisses an die Sitzung auftritt (auf dem
     *                   Ereignis-Thread); sie darf den Thread nicht beenden
     */
    static ScanStarter imHintergrund(Consumer<Throwable> unerwartet) {
        return (sitzung, auftrag) -> new SwingWorker<List<Finding>, Void>() {
            @Override
            protected List<Finding> doInBackground() {
                return auftrag.ausfuehren();
            }

            @Override
            protected void done() {
                try {
                    uebergeben();
                } catch (RuntimeException e) {
                    unerwartet.accept(e);
                }
            }

            private void uebergeben() {
                List<Finding> funde;
                try {
                    funde = get();
                } catch (ExecutionException e) {
                    sitzung.scanFehlgeschlagen(auftrag, e.getCause() != null ? e.getCause() : e);
                    return;
                } catch (InterruptedException e) {
                    // done() läuft erst nach dem Ende des Auftrags; get() blockiert dann nicht und wird nicht
                    // unterbrochen. Sollte es doch geschehen, gilt der Scan als fehlgeschlagen. Das Unterbrechungs-
                    // zeichen wird nicht erneut gesetzt: Der Ereignis-Thread gehört uns nicht, und ein gesetztes
                    // Zeichen ließe jedes spätere blockierende Warten auf ihm sofort scheitern.
                    sitzung.scanFehlgeschlagen(auftrag, e);
                    return;
                }
                sitzung.scanAbgeschlossen(auftrag, funde);
            }
        }.execute();
    }
}
