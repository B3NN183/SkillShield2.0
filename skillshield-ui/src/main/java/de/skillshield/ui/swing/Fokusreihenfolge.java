package de.skillshield.ui.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.util.List;
import java.util.Objects;

/**
 * Feste Tab-Reihenfolge des Hauptpanels (AK-46): Eingabe, Scannen, Tabelle, Alle anwählen, Markieren, Text für die
 * KI, Für KI kopieren, KI-Antwort, Zurückübersetzen, Ergebnis, Ergebnis kopieren, Liste, Zurücksetzen, Beenden. Das
 * Panel ist die Fokuswurzel; hinter dem letzten Element kommt wieder das erste (und umgekehrt).
 *
 * <p>Übersprungen wird, was nicht bedienbar ist: gesperrte Komponenten und solche, die selbst oder über einen
 * Elternteil unsichtbar sind (die ausgeblendeten Karten der Leer-Texte). Die nicht fokussierbaren Hinweistexte
 * kommen in der Liste nicht vor. Eine Komponente, die nicht in der Liste steht (z. B. der Editor einer
 * Tabellenzelle), wird über ihren nächsten Vorfahren in der Liste eingeordnet.
 */
final class Fokusreihenfolge extends FocusTraversalPolicy {

    private final List<Component> reihenfolge;

    /** @param reihenfolge die Komponenten in der Tab-Reihenfolge (wird kopiert) */
    Fokusreihenfolge(List<? extends Component> reihenfolge) {
        this.reihenfolge = List.copyOf(Objects.requireNonNull(reihenfolge, "reihenfolge"));
    }

    /** Die feste Liste, auch mit gesperrten Elementen. */
    List<Component> reihenfolge() {
        return reihenfolge;
    }

    @Override
    public Component getComponentAfter(Container wurzel, Component aktuell) {
        return nachbar(wurzel, aktuell, 1);
    }

    @Override
    public Component getComponentBefore(Container wurzel, Component aktuell) {
        return nachbar(wurzel, aktuell, -1);
    }

    @Override
    public Component getFirstComponent(Container wurzel) {
        return ersteBedienbare(wurzel, -1, 1);
    }

    @Override
    public Component getLastComponent(Container wurzel) {
        return ersteBedienbare(wurzel, reihenfolge.size(), -1);
    }

    @Override
    public Component getDefaultComponent(Container wurzel) {
        return getFirstComponent(wurzel);
    }

    private Component nachbar(Container wurzel, Component aktuell, int richtung) {
        int start = indexVon(aktuell);
        if (start < 0) {
            return richtung > 0 ? getFirstComponent(wurzel) : getLastComponent(wurzel);
        }
        return ersteBedienbare(wurzel, start, richtung);
    }

    /** Sucht ab {@code start} (ausgeschlossen) in der Richtung, mit Umlauf; {@code null}, wenn nichts bedienbar ist. */
    private Component ersteBedienbare(Container wurzel, int start, int richtung) {
        int n = reihenfolge.size();
        int index = start;
        for (int i = 0; i < n; i++) {
            index = Math.floorMod(index + richtung, n);
            Component kandidat = reihenfolge.get(index);
            if (bedienbar(wurzel, kandidat)) {
                return kandidat;
            }
        }
        return null;
    }

    private int indexVon(Component komponente) {
        for (Component c = komponente; c != null; c = c.getParent()) {
            for (int i = 0; i < reihenfolge.size(); i++) {
                if (reihenfolge.get(i) == c) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Aktiviert, fokussierbar und samt aller Elternteile bis zur Wurzel sichtbar. */
    private static boolean bedienbar(Container wurzel, Component c) {
        if (!c.isEnabled() || !c.isFocusable()) {
            return false;
        }
        for (Component k = c; k != null; k = k.getParent()) {
            if (!k.isVisible()) {
                return false;
            }
            if (k == wurzel) {
                return true;
            }
        }
        return true;
    }
}
