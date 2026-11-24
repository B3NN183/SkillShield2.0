package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Zentraler Fehler-Handler (AK-50, AK-51): fester Text mit Klassenname, nie Meldung, Ursache oder Stacktrace. */
class FehlerbehandlungTest {

    private static final String MARKER = "GEHEIM-MARKER-QUARZ";

    private final List<String> angezeigt = new ArrayList<>();

    /** Fuehrt die Aktion mit umgeleiteten Standardstroemen aus und liefert alles, was dorthin geschrieben wurde. */
    private static String ausgabeVon(Runnable aktion) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        PrintStream umleitung = new PrintStream(puffer, true, StandardCharsets.UTF_8);
        System.setOut(umleitung);
        System.setErr(umleitung);
        try {
            aktion.run();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        return puffer.toString(StandardCharsets.UTF_8);
    }

    private static Exception mitMarker() {
        return new IllegalStateException(MARKER + " in der Meldung", new RuntimeException(MARKER + " in der Ursache"));
    }

    @Test
    void dieMeldungIstFesterTextMitKlassennameOhneMeldungOderUrsache() {
        String meldung = Fehlerbehandlung.meldung(mitMarker());

        assertEquals("Beim Verarbeiten ist ein Fehler aufgetreten. Es wurde nichts kopiert. "
                + "(java.lang.IllegalStateException)", meldung);
        assertEquals(Texte.FEHLER + " (java.lang.IllegalStateException)", meldung);
        assertFalse(meldung.contains(MARKER));
    }

    @Test
    void ohneAusnahmeBleibtNurDerFesteText() {
        assertEquals(Texte.FEHLER, Fehlerbehandlung.meldung(null));
    }

    @Test
    void auchEineSelbstDefinierteAusnahmeZeigtNurIhrenKlassennamen() {
        class EigenerFehler extends RuntimeException {
            private static final long serialVersionUID = 1L;

            EigenerFehler() {
                super(MARKER);
            }
        }

        String meldung = Fehlerbehandlung.meldung(new EigenerFehler());

        assertTrue(meldung.startsWith(Texte.FEHLER + " ("));
        assertTrue(meldung.contains("EigenerFehler"));
        assertFalse(meldung.contains(MARKER));
    }

    @Test
    void derHandlerUebergibtDieMeldungDerAnzeigeUndSchreibtNichtAufDieKonsole() {
        Fehlerbehandlung handler = new Fehlerbehandlung(angezeigt::add);

        String konsole = ausgabeVon(() -> handler.uncaughtException(Thread.currentThread(), mitMarker()));

        assertEquals(List.of(Texte.FEHLER + " (java.lang.IllegalStateException)"), angezeigt);
        assertEquals("", konsole);
    }

    @Test
    void einFehlerInDerAnzeigeWirdVerschlucktOhneAusgabe() {
        Fehlerbehandlung handler = new Fehlerbehandlung(text -> {
            throw new IllegalStateException(MARKER + " aus der Anzeige");
        });
        Fehlerbehandlung handlerMitError = new Fehlerbehandlung(text -> {
            throw new AssertionError(MARKER + " aus der Anzeige");
        });

        String konsole = ausgabeVon(() -> {
            assertDoesNotThrow(() -> handler.uncaughtException(Thread.currentThread(), mitMarker()));
            assertDoesNotThrow(() -> handlerMitError.uncaughtException(Thread.currentThread(), mitMarker()));
        });

        assertEquals("", konsole);
    }

    @Test
    void alsHandlerEinesThreadsFaengtErEineNichtAbgefangeneAusnahmeOhneKonsolenausgabe() {
        Fehlerbehandlung handler = new Fehlerbehandlung(angezeigt::add);

        String konsole = ausgabeVon(() -> {
            Thread thread = new Thread(() -> {
                throw new IllegalArgumentException(MARKER);
            });
            thread.setUncaughtExceptionHandler(handler);
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertEquals(List.of(Texte.FEHLER + " (java.lang.IllegalArgumentException)"), angezeigt);
        assertEquals("", konsole);
    }

    @Test
    void dieAnzeigeIstPflicht() {
        assertThrows(NullPointerException.class, () -> new Fehlerbehandlung(null));
    }

    @Test
    void toStringEnthaeltKeinenZustand() {
        String text = new Fehlerbehandlung(angezeigt::add).toString();

        assertNotNull(text);
        assertEquals("Fehlerbehandlung", text);
    }
}
