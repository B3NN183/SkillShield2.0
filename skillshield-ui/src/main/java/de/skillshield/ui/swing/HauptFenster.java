package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Texte;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import javax.swing.JFrame;

/**
 * Das einzige Fenster des Programms (AK-1): fester Titel {@link Texte#FENSTERTITEL}, Startgröße 1024 x 768,
 * Mindestgröße {@link HauptPanel#MINDESTGROESSE}. Das Schließen-Kreuz und Alt+F4 schließen das Fenster nicht selbst
 * ({@code DO_NOTHING_ON_CLOSE}), sondern rufen denselben Ablauf wie die Schaltfläche "Beenden" ({@code beenden}):
 * Rückfrage, Zwischenablage leeren, Felder verwerfen, Prozess beenden. Es werden keine Größe und keine Position
 * gespeichert. Nur auf dem Ereignis-Thread benutzen; braucht einen Bildschirm.
 */
public final class HauptFenster extends JFrame {

    /** Startgröße des Fensters. */
    public static final Dimension STARTGROESSE = new Dimension(1024, 768);

    private final HauptPanel panel;

    /**
     * @param panel   Inhalt des Fensters
     * @param beenden gemeinsamer Beenden-Ablauf (dieselbe Logik wie die Schaltfläche "Beenden")
     */
    public HauptFenster(HauptPanel panel, Runnable beenden) {
        super(Texte.FENSTERTITEL);
        this.panel = Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(beenden, "beenden");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(panel);
        setMinimumSize(HauptPanel.MINDESTGROESSE);
        setPreferredSize(STARTGROESSE);
        pack();
        setSize(STARTGROESSE);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                beenden.run();
            }
        });
    }

    /** Zeigt das Fenster und setzt den Anfangsfokus auf das Eingabefeld. */
    public void anzeigen() {
        setVisible(true);
        panel.anfangsfokus();
    }

    /** Nur der feste Titel, nie ein Inhalt. */
    @Override
    public String toString() {
        return "HauptFenster";
    }
}
