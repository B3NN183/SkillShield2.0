package de.skillshield.ui.swing;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.UIManager;

/**
 * Fester, umbrechender Hinweistext (Dauerhinweise AK-21 und AK-40, Leer-Texte): ein nicht bearbeitbares Textfeld
 * ohne Rahmen. Ein Textfeld statt eines {@code JLabel}, damit der Text umbricht und nie als HTML gedeutet wird.
 * Es ist nicht kopierbar ({@link Textaktionen.Rolle#HINWEIS}) und nimmt keinen Fokus (OF-5, T-7: kein eigener
 * Tab-Halt; für Hilfstechnik ist er ein eigenes Objekt mit festem zugänglichem Namen, und die Dauerhinweise stehen
 * zusätzlich als Beschreibung am zugehörigen Bedienelement).
 */
final class Hinweistext extends TextBereich {

    Hinweistext(String name, String text, String zugaenglicherName, Kontext kontext) {
        super(name, 2);
        getAccessibleContext().setAccessibleName(zugaenglicherName);
        setText(text);
        setEditable(false);
        setFocusable(false);
        setOpaque(false);
        setBorder(null);
        Color farbe = UIManager.getColor("Label.foreground");
        if (farbe != null) {
            setForeground(farbe);
        }
        Textaktionen.installiere(this, Textaktionen.Rolle.HINWEIS, kontext);
    }

    /**
     * Höhe passend zur Breite des Elternteils: Ein umbrechendes Textfeld kennt seine Breite sonst nicht und
     * meldet eine riesige Höhe (jedes Zeichen eine Zeile).
     */
    @Override
    public Dimension getPreferredSize() {
        Container eltern = getParent();
        if (eltern != null && eltern.getWidth() > 0) {
            Insets rand = eltern.getInsets();
            int breite = eltern.getWidth() - rand.left - rand.right;
            if (breite > 0) {
                setSize(breite, 1);
            }
        }
        return super.getPreferredSize();
    }
}
