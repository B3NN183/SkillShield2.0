package de.skillshield.ui.logik;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Zentraler Fehler-Handler (AK-50): übersetzt jede nicht abgefangene Ausnahme in einen festen deutschen Text mit dem
 * Klassennamen der Ausnahme und übergibt ihn der Anzeige. Nie die Meldung, nie die Ursache, nie ein Stacktrace; die
 * Klasse schreibt nichts auf die Konsole und in keine Datei (AK-51). Als {@link Thread.UncaughtExceptionHandler}
 * einsetzbar; das Installieren als Standard-Handler geschieht beim Start des Programms (T-8).
 *
 * <p>Scheitert die Anzeige selbst, wird das verschluckt: Ein Fehler beim Melden eines Fehlers darf weder den Thread
 * weiter beschädigen noch Klartext nach außen tragen.
 */
public final class Fehlerbehandlung implements Thread.UncaughtExceptionHandler {

    private final Consumer<String> anzeige;

    /** @param anzeige zeigt den festen Text an (im Programm ein Fehlerdialog), nie mit Eingabe- oder Fundtext */
    public Fehlerbehandlung(Consumer<String> anzeige) {
        this.anzeige = Objects.requireNonNull(anzeige, "anzeige");
    }

    /** Fester Text mit dem Klassennamen der Ausnahme; ohne Ausnahme nur der feste Text. */
    public static String meldung(Throwable fehler) {
        return fehler == null ? Texte.FEHLER : Texte.fehlerMeldung(fehler);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable fehler) {
        try {
            anzeige.accept(meldung(fehler));
        } catch (RuntimeException | Error ausDerAnzeige) {
            // Bewusst verschluckt: Die Meldung der Anzeige-Ausnahme könnte Klartext enthalten.
        }
    }

    /** Nur der Name der Klasse, nie ein Zustand. */
    @Override
    public String toString() {
        return "Fehlerbehandlung";
    }
}
