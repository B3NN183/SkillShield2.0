package de.skillshield.ui.logik;

import de.skillshield.core.pseudonymize.PlaceholderMapping;
import java.util.HashMap;
import java.util.Map;

/**
 * Platzhalter je Fund aus der Zuordnung (Architektur-Plan Kap. 3.3). Der Kern vergibt für jedes Paar (Typ,
 * Originalwert) genau einen Platzhalter; hier wird die Tabelle (Typ, Wert) zu Platzhalter aus der Zuordnung
 * gelesen, die Nummerierung wird nicht nachgebaut.
 */
final class PlatzhalterJeFund {

    private static final String ANFANG = "[[";
    private static final String ENDE = "]]";

    private PlatzhalterJeFund() {
    }

    /**
     * Baut die Tabelle (Typ, Originalwert) zu Platzhalter.
     *
     * @throws IllegalStateException wenn die Zuordnung der Konsistenzregel widerspricht (Meldung ohne Werte)
     */
    static Map<FundSchluessel, String> aus(PlaceholderMapping mapping) {
        Map<FundSchluessel, String> tabelle = new HashMap<>();
        for (String platzhalter : mapping.placeholders()) {
            int unterstrich = platzhalter.indexOf('_');
            if (!platzhalter.startsWith(ANFANG) || !platzhalter.endsWith(ENDE) || unterstrich < ANFANG.length()) {
                throw new IllegalStateException("Unerwartete Platzhalterform");
            }
            String typ = platzhalter.substring(ANFANG.length(), unterstrich);
            String wert = mapping.resolve(platzhalter).orElseThrow(
                    () -> new IllegalStateException("Platzhalter ohne Originalwert"));
            String vorher = tabelle.put(new FundSchluessel(typ, wert), platzhalter);
            if (vorher != null) {
                throw new IllegalStateException("Zwei Platzhalter für dasselbe Paar aus Typ und Wert");
            }
        }
        return tabelle;
    }
}
