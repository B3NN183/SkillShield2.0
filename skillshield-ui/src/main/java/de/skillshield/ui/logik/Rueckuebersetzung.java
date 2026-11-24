package de.skillshield.ui.logik;

import de.skillshield.core.reidentify.UnresolvedPlaceholder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ergebnis der Rückübersetzung für Bereich 4 (AK-37, AK-38).
 *
 * <p>{@code text} enthält die eingesetzten Originalwerte und damit Klartext; {@link #toString()} zeigt nur Zahlen.
 *
 * @param text              rückübersetzter Text, identisch mit {@code Reidentifier.restore(antwort, zuordnung).text()}
 * @param offen             nicht aufgelöste Platzhalter in Textreihenfolge (Position in {@code char}-Einheiten ab 0)
 * @param platzhalterGesamt Zahl aller Platzhalter der Antwort, aufgelöste und nicht aufgelöste; 0 bei einer Antwort
 *                          ohne jeden Platzhalter
 */
public record Rueckuebersetzung(String text, List<UnresolvedPlaceholder> offen, int platzhalterGesamt) {

    public Rueckuebersetzung {
        Objects.requireNonNull(text, "text");
        offen = List.copyOf(Objects.requireNonNull(offen, "offen"));
    }

    /**
     * Zeilen der Liste "Nicht zurückübersetzt": je nicht aufgelöstem Platzhalter "[[KVNR_7]] an Position 121:
     * unbekannt" (Position ab 1 gezählt), sonst genau ein Sammeltext ({@link Texte#ALLE_ZURUECK} oder, bei einer
     * Antwort ohne Platzhalter, {@link Texte#KEINE_PLATZHALTER}).
     */
    public List<String> listenzeilen() {
        if (offen.isEmpty()) {
            return List.of(platzhalterGesamt == 0 ? Texte.KEINE_PLATZHALTER : Texte.ALLE_ZURUECK);
        }
        List<String> zeilen = new ArrayList<>(offen.size());
        for (UnresolvedPlaceholder platzhalter : offen) {
            String grund = platzhalter.reason() == UnresolvedPlaceholder.Reason.UNBEKANNT
                    ? Texte.GRUND_UNBEKANNT : Texte.GRUND_BESCHAEDIGT;
            zeilen.add(Texte.listenzeile(platzhalter.token(), platzhalter.position() + 1, grund));
        }
        return List.copyOf(zeilen);
    }

    /** Nur Zahlen, nie Text. */
    @Override
    public String toString() {
        return "Rueckuebersetzung[zeichen=" + text.length() + ", offen=" + offen.size()
                + ", platzhalterGesamt=" + platzhalterGesamt + "]";
    }
}
