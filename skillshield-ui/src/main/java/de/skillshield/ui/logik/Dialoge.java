package de.skillshield.ui.logik;

/** Dialoge der Oberfläche; im Test durch eine Attrappe ersetzbar. */
public interface Dialoge {

    /** Stellt die Rückfrage mit "Ja" und "Abbrechen"; {@code true} nur bei "Ja". */
    boolean bestaetigt(Rueckfrage frage);

    /** Zeigt einen Fehlertext (fester Text mit Klassenname der Ausnahme, nie deren Meldung) mit "OK". */
    void fehler(String text);
}
