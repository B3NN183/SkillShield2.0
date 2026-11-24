package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Fehlerbehandlung;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Zwischenablage;
import java.util.Objects;

/**
 * Gemeinsame Bindung der Bereiche an die {@link Sitzung}: Jede Aktion der Oberfläche (Schaltfläche, Tastenkürzel,
 * Kopier- und Einfügeweg, Dokument- und Auswahl-Hörer) läuft über {@link #sicher}, damit eine Ausnahme den
 * Ereignis-Thread nicht mit Stacktrace beendet, sondern über {@link Fehlerbehandlung} als fester Text mit
 * Klassennamen gemeldet wird (nie die Meldung der Ausnahme). Nicht darüber laufen die lesenden Zugriffe der
 * Beobachter-Aktualisierung ({@code aktualisieren()}, Tabellenmodell) und {@link ScanStarter#imHintergrund}, das
 * {@link #melden} über ein eigenes {@code catch} ruft. Nur auf dem Ereignis-Thread benutzen.
 */
final class Kontext {

    final Sitzung sitzung;
    final Zwischenablage zwischenablage;
    private final Fehlerbehandlung fehler;

    Kontext(Sitzung sitzung, Zwischenablage zwischenablage, Dialoge dialoge) {
        this.sitzung = Objects.requireNonNull(sitzung, "sitzung");
        this.zwischenablage = Objects.requireNonNull(zwischenablage, "zwischenablage");
        this.fehler = new Fehlerbehandlung(Objects.requireNonNull(dialoge, "dialoge")::fehler);
    }

    /** Führt die Aktion aus; eine Ausnahme wird gemeldet und nicht weitergegeben. */
    void sicher(Runnable aktion) {
        try {
            aktion.run();
        } catch (RuntimeException e) {
            melden(e);
        }
    }

    /** Meldet eine Ausnahme als festen Text mit Klassennamen; scheitert die Anzeige, wird das verschluckt. */
    void melden(Throwable ausnahme) {
        fehler.uncaughtException(Thread.currentThread(), ausnahme);
    }
}
