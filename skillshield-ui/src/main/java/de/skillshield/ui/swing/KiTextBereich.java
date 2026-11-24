package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.Texte;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Bereich 3 "Für KI kopieren": der pseudonymisierte Text (nur lesbar) und die Schaltfläche "Für KI kopieren". Jedes
 * Kopieren aus dem Feld, auch Strg+C, ist "Für KI kopieren" mit Rückfrage bei null angewählten Funden; Ausschneiden
 * gibt es hier nicht. Ohne Inhalt zeigt der Bereich einen erklärenden Text (AK-5).
 */
final class KiTextBereich extends JPanel {

    private static final String KARTE_LEER = "leer";
    private static final String KARTE_TEXT = "text";

    private final Kontext kontext;
    private final TextBereich text = new TextBereich("textFuerKi", 6);
    private final CardLayout karten = new CardLayout();
    private final JPanel kartenFlaeche = new JPanel(karten);
    private final JButton kopieren;
    /** Der zuletzt gesetzte Text; ein Vergleich per Gleichheit spart das Neusetzen bei unverändertem Inhalt. */
    private String angezeigt = "";

    KiTextBereich(Kontext kontext, Runnable kopierenAktion) {
        super(new BorderLayout(0, 4));
        this.kontext = kontext;
        setName("bereich3");
        Bausteine.innenabstand(this);
        text.setEditable(false);
        text.getAccessibleContext().setAccessibleName(Texte.NAME_TEXT_FUER_KI);
        Textaktionen.installiere(text, Textaktionen.Rolle.FUER_KI, kontext);
        JLabel ueberschrift = Bausteine.etikett(Texte.BEREICH_3, "ueberschrift3");
        ueberschrift.setLabelFor(text);
        kopieren = Bausteine.knopf(Texte.FUER_KI_KOPIEREN, "fuerKiKopieren", kopierenAktion);
        add(Bausteine.kopfzeile(ueberschrift, kopieren), BorderLayout.NORTH);
        kartenFlaeche.setName("kartenFuerKi");
        kartenFlaeche.add(new Hinweistext("leerFuerKi", Texte.LEER_BEREICH_3, Texte.NAME_LEER_TEXT_FUER_KI, kontext),
                KARTE_LEER);
        kartenFlaeche.add(Bausteine.scroll(text, "scrollFuerKi"), KARTE_TEXT);
        add(kartenFlaeche, BorderLayout.CENTER);
        karten.show(kartenFlaeche, KARTE_LEER);
    }

    TextBereich text() {
        return text;
    }

    JButton kopieren() {
        return kopieren;
    }

    void aktualisieren(Bedienzustand zustand) {
        String neu = kontext.sitzung.textFuerKi();
        if (!neu.equals(angezeigt)) {
            if (neu.isEmpty()) {
                text.zuruecksetzen();
            } else {
                text.textSetzen(neu);
                text.setCaretPosition(0);
            }
            angezeigt = neu;
        }
        karten.show(kartenFlaeche, neu.isEmpty() ? KARTE_LEER : KARTE_TEXT);
        kopieren.setEnabled(zustand.istAktiv(Funktion.FUER_KI_KOPIEREN));
        Bausteine.sperrgrund(kopieren, zustand, Funktion.FUER_KI_KOPIEREN);
    }
}
