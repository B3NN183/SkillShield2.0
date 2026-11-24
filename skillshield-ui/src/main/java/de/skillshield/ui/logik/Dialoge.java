package de.skillshield.ui.logik;

/** Dialoge der Oberfläche; im Test durch eine Attrappe ersetzbar. */
public interface Dialoge {

    /** Stellt die Rückfrage mit "Ja" und "Abbrechen"; {@code true} nur bei "Ja". */
    boolean bestaetigt(Rueckfrage frage);

    /**
     * Stellt die Rückfrage mit den Antworten {@link Texte#ERNEUT_VERSUCHEN} (Standard) und
     * {@link Texte#TROTZDEM_BEENDEN} (V-2, bewusste Erweiterung gegenüber A-3). Gedacht für
     * {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}.
     *
     * @return {@code true} bei "Erneut versuchen"; {@code false} nur bei einem ausdrücklichen "Trotzdem beenden".
     *         Schließt der Anwender den Dialog anders, gilt "Erneut versuchen": Das Programm beendet sich dann
     *         nicht stillschweigend mit Text aus SkillShield in der Zwischenablage.
     */
    boolean erneutVersuchen(Rueckfrage frage);

    /** Zeigt einen Fehlertext (fester Text mit Klassenname der Ausnahme, nie deren Meldung) mit "OK". */
    void fehler(String text);
}
