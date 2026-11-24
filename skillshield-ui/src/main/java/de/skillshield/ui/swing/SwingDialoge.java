package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.Texte;
import java.awt.Component;
import java.awt.EventQueue;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Dialoge mit {@link JOptionPane} und festen deutschen Beschriftungen (A-3, AK-2, Sicherheitsvorgabe 7). Rückfragen
 * haben "Ja" und "Abbrechen" (Standard "Abbrechen"), die Rückfrage {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}
 * "Erneut versuchen" (Standard) und "Trotzdem beenden", der Fehlerdialog "OK". Dialogtexte sind nur feste Texte aus
 * {@link Texte} und {@link Rueckfrage} (plus der Klassenname einer Ausnahme beim Fehlerdialog), nie Anwenderinhalt.
 *
 * <p>Die Entscheidungslogik (Optionen, Standardoption, Zuordnung Rückgabewert zu Antwort) steckt in statischen
 * Methoden, die ohne sichtbaren Dialog testbar sind. Eine Ausnahme beim Anzeigen einer Rückfrage wird nie in eine
 * Antwort umgedeutet, sondern weitergegeben. Ausgenommen ist {@link #fehler(String)}, wenn der Dialog über
 * {@code invokeLater} auf dem Ereignis-Thread erscheint: Dort wird eine Ausnahme beim Anzeigen still verworfen (kein
 * Stacktrace, kein Inhalt), weil es keinen Aufrufer mehr gibt, der sie bekommen könnte.
 */
public final class SwingDialoge implements Dialoge {

    /** Umbruchbreite des Fragetextes in Zeichen. */
    static final int UMBRUCH = 60;

    /** Zeigt einen Optionsdialog und liefert den Index der gewählten Option oder einen negativen Wert. */
    @FunctionalInterface
    interface Anzeige {
        int zeige(Component eltern, String text, String titel, int meldungsart, Object[] optionen, Object standard);
    }

    private final Supplier<Component> eltern;
    private final Anzeige anzeige;

    /** @param eltern liefert das Elternfenster der Dialoge; darf {@code null} liefern */
    public SwingDialoge(Supplier<Component> eltern) {
        this(eltern, SwingDialoge::mitJOptionPane);
    }

    SwingDialoge(Supplier<Component> eltern, Anzeige anzeige) {
        this.eltern = Objects.requireNonNull(eltern, "eltern");
        this.anzeige = Objects.requireNonNull(anzeige, "anzeige");
    }

    private static int mitJOptionPane(Component eltern, String text, String titel, int meldungsart,
                                      Object[] optionen, Object standard) {
        return JOptionPane.showOptionDialog(eltern, text, titel, JOptionPane.DEFAULT_OPTION, meldungsart, null,
                optionen, standard);
    }

    /** Optionen einer Rückfrage in der Reihenfolge der Schaltflächen. */
    static Object[] optionen(Rueckfrage frage) {
        return frage == Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT
                ? new Object[] {Texte.ERNEUT_VERSUCHEN, Texte.TROTZDEM_BEENDEN}
                : new Object[] {Texte.JA, Texte.ABBRECHEN};
    }

    /** Standardoption: "Abbrechen" bzw. "Erneut versuchen". */
    static Object standardOption(Rueckfrage frage) {
        return optionen(frage)[frage == Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT ? 0 : 1];
    }

    /** {@code true} nur bei "Ja" (Index 0); Abbrechen, Schließen und Esc ergeben {@code false}. */
    static boolean bestaetigtNach(int rueckgabe) {
        return rueckgabe == 0;
    }

    /** {@code false} nur bei ausdrücklichem "Trotzdem beenden" (Index 1); Schließen und Esc gelten als "Erneut". */
    static boolean erneutVersuchenNach(int rueckgabe) {
        return rueckgabe != 1;
    }

    /** Bricht einen Text an Leerzeichen auf Zeilen von höchstens {@code breite} Zeichen um (Wörter bleiben ganz). */
    static String umbrechen(String text, int breite) {
        StringBuilder aus = new StringBuilder(text.length() + 8);
        int zeile = 0;
        for (String wort : text.split(" ")) {
            if (zeile > 0 && zeile + 1 + wort.length() > breite) {
                aus.append('\n');
                zeile = 0;
            } else if (zeile > 0) {
                aus.append(' ');
                zeile++;
            }
            aus.append(wort);
            zeile += wort.length();
        }
        return aus.toString();
    }

    /**
     * @throws IllegalArgumentException für {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}: Diese Rückfrage hat
     *                                  andere Antworten und läuft nur über {@link #erneutVersuchen}
     */
    @Override
    public boolean bestaetigt(Rueckfrage frage) {
        Objects.requireNonNull(frage, "frage");
        if (frage == Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT) {
            throw new IllegalArgumentException("Diese Rückfrage hat andere Antworten");
        }
        return bestaetigtNach(fragen(frage));
    }

    /**
     * Wirft bei einer Ausnahme beim Anzeigen (etwa ohne Bildschirm) die Ausnahme weiter und liefert nie still
     * "erneut versuchen": Das Beenden liefe sonst in eine Endlosschleife ohne Bedienmöglichkeit.
     *
     * @throws IllegalArgumentException für jede andere Rückfrage als {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}
     */
    @Override
    public boolean erneutVersuchen(Rueckfrage frage) {
        Objects.requireNonNull(frage, "frage");
        if (frage != Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT) {
            throw new IllegalArgumentException("Diese Rückfrage hat andere Antworten");
        }
        return erneutVersuchenNach(fragen(frage));
    }

    private int fragen(Rueckfrage frage) {
        return anzeige.zeige(eltern.get(), umbrechen(frage.text(), UMBRUCH), Texte.TITEL_RUECKFRAGE,
                JOptionPane.QUESTION_MESSAGE, optionen(frage), standardOption(frage));
    }

    /** Zeigt den Fehlertext mit "OK"; von einem anderen Thread aus wird der Dialog auf dem Ereignis-Thread gezeigt. */
    @Override
    public void fehler(String text) {
        Objects.requireNonNull(text, "text");
        if (EventQueue.isDispatchThread()) {
            fehlerZeigen(text);
        } else {
            SwingUtilities.invokeLater(() -> fehlerZeigenOhneAusnahme(text));
        }
    }

    /** Scheitert die Anzeige auf dem Ereignis-Thread (etwa ohne Bildschirm), wird das verworfen: kein Stacktrace. */
    private void fehlerZeigenOhneAusnahme(String text) {
        try {
            fehlerZeigen(text);
        } catch (RuntimeException e) {
            // bewusst verworfen; die Meldung der Ausnahme wird nicht ausgegeben
        }
    }

    private void fehlerZeigen(String text) {
        anzeige.zeige(eltern.get(), umbrechen(text, UMBRUCH), Texte.TITEL_FEHLER, JOptionPane.ERROR_MESSAGE,
                new Object[] {Texte.OK}, Texte.OK);
    }
}
