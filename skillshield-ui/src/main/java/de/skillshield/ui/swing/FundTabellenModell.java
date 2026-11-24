package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Beschriftungen;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.FundZeile;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import java.util.List;
import java.util.Objects;
import javax.swing.table.AbstractTableModel;

/**
 * Tabellenmodell der Funde (Bereich 2, AK-13): Spalten Übernehmen, Typ, Fundtext, Stufe, Platzhalter. Das Modell liest
 * die Zeilen bei jedem Zugriff direkt aus der {@link Sitzung} und baut keine eigene Kopie auf (bis zu eine Million
 * Zeilen); Änderungen kommen als ein einziges Ereignis. Der Fundtext ist der Originalwert und erscheint nur hier.
 */
final class FundTabellenModell extends AbstractTableModel {

    private final Kontext kontext;
    /** Die zuletzt gemeldete Zeilenliste, nur zum Vergleich (Identität), nie zum Lesen. */
    private List<FundZeile> gemeldet = List.of();

    FundTabellenModell(Kontext kontext) {
        this.kontext = Objects.requireNonNull(kontext, "kontext");
    }

    private List<FundZeile> zeilen() {
        return kontext.sitzung.zeilen();
    }

    /**
     * Meldet der Tabelle eine Änderung der Sitzung: nichts, wenn die Zeilenliste dieselbe ist; nur "aktualisiert"
     * (Auswahl der Zeile bleibt), wenn dieselben Funde nur neu ausgewählt wurden; sonst "Daten geändert".
     */
    void aktualisieren() {
        List<FundZeile> neu = zeilen();
        if (neu == gemeldet) {
            return;
        }
        List<FundZeile> alt = gemeldet;
        gemeldet = neu;
        if (!neu.isEmpty() && gleicheFunde(alt, neu)) {
            fireTableRowsUpdated(0, neu.size() - 1);
        } else {
            fireTableDataChanged();
        }
    }

    private static boolean gleicheFunde(List<FundZeile> alt, List<FundZeile> neu) {
        if (alt.size() != neu.size()) {
            return false;
        }
        for (int i = 0; i < neu.size(); i++) {
            if (alt.get(i).fund() != neu.get(i).fund()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getRowCount() {
        return zeilen().size();
    }

    @Override
    public int getColumnCount() {
        return Texte.SPALTENKOEPFE.size();
    }

    @Override
    public String getColumnName(int spalte) {
        return Texte.SPALTENKOEPFE.get(spalte);
    }

    @Override
    public Class<?> getColumnClass(int spalte) {
        return spalte == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int zeile, int spalte) {
        return spalte == 0 && kontext.sitzung.bedienzustand().istAktiv(Funktion.FUND_UMSCHALTEN);
    }

    @Override
    public Object getValueAt(int zeile, int spalte) {
        List<FundZeile> alle = zeilen();
        if (zeile < 0 || zeile >= alle.size()) {
            return spalte == 0 ? Boolean.FALSE : "";
        }
        FundZeile fundZeile = alle.get(zeile);
        return switch (spalte) {
            case 0 -> fundZeile.angewaehlt();
            case 1 -> Beschriftungen.typ(fundZeile.fund());
            case 2 -> fundZeile.fund().originalValue();
            case 3 -> Beschriftungen.stufe(fundZeile.fund().level());
            case 4 -> fundZeile.platzhalter() == null ? Texte.NICHT_ERSETZT : fundZeile.platzhalter();
            default -> "";
        };
    }

    /** Nur die erste Spalte ist bearbeitbar; die Auswahl geht an die Sitzung, die Tabelle zeichnet sich neu. */
    @Override
    public void setValueAt(Object wert, int zeile, int spalte) {
        if (spalte == 0 && wert instanceof Boolean angewaehlt) {
            kontext.sicher(() -> kontext.sitzung.anwaehlen(zeile, angewaehlt));
        }
    }

    /** Nur Name der Klasse, nie Fundtext. */
    @Override
    public String toString() {
        return "FundTabellenModell[zeilen=" + getRowCount() + "]";
    }
}
