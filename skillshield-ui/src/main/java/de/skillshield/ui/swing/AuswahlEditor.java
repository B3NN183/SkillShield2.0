package de.skillshield.ui.swing;

import java.awt.Component;
import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingConstants;

/**
 * Editor des Kontrollkästchens der Fundtabelle (nur per Maus-Klick oder F2 geöffnet, T-7). Er schreibt nie einen
 * veralteten Wert: Hat sich die Auswahl der Zeile in der Sitzung geändert, seit der Editor geöffnet wurde, wird die
 * Bearbeitung abgebrochen statt übernommen. So kann ein später schließender Editor keine Zeile still abwählen und
 * die Anzeige nicht vom Zustand der Sitzung abweichen. Der Editor ändert immer genau eine Zeile.
 */
final class AuswahlEditor extends DefaultCellEditor {

    private JTable tabelle;
    private int zeile = -1;
    private boolean startwert;

    AuswahlEditor() {
        super(new JCheckBox());
        ((JCheckBox) getComponent()).setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellEditorComponent(JTable t, Object wert, boolean gewaehlt, int z, int spalte) {
        tabelle = t;
        zeile = z;
        startwert = Boolean.TRUE.equals(wert);
        return super.getTableCellEditorComponent(t, wert, gewaehlt, z, spalte);
    }

    @Override
    public boolean stopCellEditing() {
        if (veraltet()) {
            cancelCellEditing();
            return true;
        }
        return super.stopCellEditing();
    }

    /** Die Zeile zeigt in der Sitzung einen anderen Wert als beim Öffnen des Editors. */
    private boolean veraltet() {
        return tabelle == null || zeile < 0 || zeile >= tabelle.getRowCount()
                || startwert != Boolean.TRUE.equals(tabelle.getValueAt(zeile, 0));
    }
}
