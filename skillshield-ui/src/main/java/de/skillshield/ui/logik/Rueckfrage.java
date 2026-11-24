package de.skillshield.ui.logik;

/**
 * Rückfragen mit den Schaltflächen "Ja" und "Abbrechen" (Standard "Abbrechen"); Wortlaute aus AK-32, 41, 42, 43.
 * Ausnahme: {@link #ZWISCHENABLAGE_NICHT_GELEERT} mit "Erneut versuchen" und "Trotzdem beenden" (V-2).
 */
public enum Rueckfrage {
    UNVERAENDERT_KOPIEREN("Es ist kein Fund zum Ersetzen angewählt. Der Text wird unverändert kopiert. Fortfahren?"),
    NEUER_SCAN("Die bisherige Zuordnung geht beim neuen Scan verloren. "
            + "KI-Antworten zum vorigen Text können dann nicht mehr zurückübersetzt werden. Fortfahren?"),
    ZURUECKSETZEN("Alle Eingaben und die Zuordnung werden gelöscht. "
            + "KI-Antworten können danach nicht mehr zurückübersetzt werden. Fortfahren?"),
    BEENDEN("Beim Beenden geht die Zuordnung verloren. "
            + "Danach können KI-Antworten nicht mehr zurückübersetzt werden. Beenden?"),
    /**
     * Nur mit {@link Dialoge#erneutVersuchen(Rueckfrage)} zu stellen: "Erneut versuchen" (Standard) und
     * "Trotzdem beenden" statt "Ja" und "Abbrechen" (V-2).
     */
    ZWISCHENABLAGE_NICHT_GELEERT(Texte.ZWISCHENABLAGE_NICHT_GELEERT);

    private final String text;

    Rueckfrage(String text) {
        this.text = text;
    }

    /** Fester deutscher Fragetext. */
    public String text() {
        return text;
    }
}
