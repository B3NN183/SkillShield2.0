package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.Texte;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Bereich 1 "Text eingeben": das Eingabefeld und "Scannen". Jede Änderung des Dokuments (Tippen, Einfügen,
 * Löschen, programmatisches Setzen) meldet {@code eingabeGeaendert()} an die Sitzung (D-4, AK-12). Das Feld ist
 * während des Scans nicht bearbeitbar (D-11).
 */
final class EingabeBereich extends JPanel {

    private final TextBereich eingabe = new TextBereich("eingabe", 8);
    private final JButton scannen;

    EingabeBereich(Kontext kontext, Runnable scannenAktion) {
        super(new BorderLayout(0, 4));
        setName("bereich1");
        Bausteine.innenabstand(this);
        Textaktionen.installiere(eingabe, Textaktionen.Rolle.BEARBEITBAR, kontext);
        eingabe.aenderungenMelden(() -> kontext.sicher(kontext.sitzung::eingabeGeaendert));
        JLabel ueberschrift = Bausteine.etikett(Texte.BEREICH_1, "ueberschrift1");
        ueberschrift.setLabelFor(eingabe);
        scannen = Bausteine.knopf(Texte.SCANNEN, "scannen", scannenAktion);
        add(Bausteine.kopfzeile(ueberschrift, scannen), BorderLayout.NORTH);
        add(Bausteine.scroll(eingabe, "scrollEingabe"), BorderLayout.CENTER);
    }

    TextBereich eingabe() {
        return eingabe;
    }

    JButton scannen() {
        return scannen;
    }

    void aktualisieren(Bedienzustand zustand) {
        eingabe.setEditable(zustand.istAktiv(Funktion.EINGABE_BEARBEITEN));
        scannen.setEnabled(zustand.istAktiv(Funktion.SCANNEN));
    }

    /** Verwirft den Text samt Zeichenpuffer (nach dem Zurücksetzen oder Beenden); die Sitzung ist dann schon leer. */
    void leeren() {
        eingabe.zuruecksetzen();
    }
}
