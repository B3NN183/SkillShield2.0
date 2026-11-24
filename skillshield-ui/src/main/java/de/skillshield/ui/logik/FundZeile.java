package de.skillshield.ui.logik;

import de.skillshield.core.model.Finding;

/**
 * Eine Tabellenzeile: ein Fund des Kerns, unverändert, mit Auswahl und Platzhalter.
 *
 * @param fund        Fund des Kerns (enthält den Originalwert, nur für die Fundtext-Spalte verwenden)
 * @param angewaehlt  {@code true}, wenn der Fund ersetzt wird
 * @param platzhalter Platzhalter an dieser Stelle im Text für die KI; {@code null}, wenn nicht angewählt
 */
public record FundZeile(Finding fund, boolean angewaehlt, String platzhalter) {

    /** Ohne Fundtext (der Fund selbst gibt seinen Originalwert nicht aus). */
    @Override
    public String toString() {
        return "FundZeile[fund=" + fund + ", angewaehlt=" + angewaehlt + ", platzhalter=" + platzhalter + "]";
    }
}
