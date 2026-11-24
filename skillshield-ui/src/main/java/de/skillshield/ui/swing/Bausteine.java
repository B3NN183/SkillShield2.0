package de.skillshield.ui.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Kleine Bausteine der Bereiche. Jede Komponente, die Text anzeigt, bekommt {@code html.disable}: Swing deutet Text,
 * der mit {@code <html>} beginnt, sonst als HTML, und {@code <img src=...>} löste einen Netzzugriff aus
 * (Sicherheitsvorgabe 3). Festtexte der Oberfläche sind betroffen, damit keine Ausnahme vergessen wird.
 */
final class Bausteine {

    /** Schlüssel der Client-Property, mit der Swing die HTML-Deutung für eine Komponente abschaltet. */
    static final String HTML_AUS = "html.disable";

    private Bausteine() {
    }

    /** Schaltet die HTML-Deutung der Komponente ab. */
    static <T extends JComponent> T htmlAus(T komponente) {
        komponente.putClientProperty(HTML_AUS, Boolean.TRUE);
        return komponente;
    }

    static JLabel etikett(String text, String name) {
        JLabel etikett = htmlAus(new JLabel(text));
        etikett.setName(name);
        return etikett;
    }

    static JButton knopf(String text, String name, Runnable aktion) {
        JButton knopf = htmlAus(new JButton(text));
        knopf.setName(name);
        knopf.addActionListener(e -> aktion.run());
        return knopf;
    }

    static JScrollPane scroll(Component inhalt, String name) {
        JScrollPane scroll = new JScrollPane(inhalt);
        scroll.setName(name);
        return scroll;
    }

    /** Kopfzeile eines Bereichs: Überschrift links, Schaltflächen rechts. */
    static JPanel kopfzeile(JLabel ueberschrift, JComponent... knoepfe) {
        JPanel rechts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        for (JComponent knopf : knoepfe) {
            rechts.add(knopf);
        }
        JPanel zeile = new JPanel(new BorderLayout(8, 0));
        zeile.add(ueberschrift, BorderLayout.WEST);
        zeile.add(rechts, BorderLayout.EAST);
        return zeile;
    }

    /** Rahmen mit Innenabstand für einen Bereich. */
    static void innenabstand(JComponent bereich) {
        bereich.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    }

    /** Zellendarstellung der Fundtabelle: Text, nie HTML. */
    static final class TabellenText extends DefaultTableCellRenderer {
        TabellenText() {
            htmlAus(this);
        }
    }

    /** Zeilendarstellung der Liste "Nicht zurückübersetzt": Text, nie HTML. */
    static final class ListenText extends DefaultListCellRenderer {
        ListenText() {
            htmlAus(this);
        }
    }
}
