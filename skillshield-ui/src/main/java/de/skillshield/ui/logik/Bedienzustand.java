package de.skillshield.ui.logik;

import java.util.Map;
import java.util.Set;

/**
 * Aktivierung je Funktion und deutscher Sperrgrund für gesperrte Funktionen (Tooltip, AK-48).
 *
 * @param aktiv      aktive Funktionen
 * @param sperrgrund Sperrgrund je gesperrter Funktion (feste Texte)
 */
public record Bedienzustand(Set<Funktion> aktiv, Map<Funktion, String> sperrgrund) {

    /** Legt unveränderliche Kopien an. */
    public Bedienzustand {
        aktiv = Set.copyOf(aktiv);
        sperrgrund = Map.copyOf(sperrgrund);
    }

    /** {@code true}, wenn die Funktion bedienbar ist. */
    public boolean istAktiv(Funktion funktion) {
        return aktiv.contains(funktion);
    }
}
