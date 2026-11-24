package de.skillshield.ui.logik;

import de.skillshield.core.SynthetischeKvNummern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Attrappen und Testtexte der Logiktests (nur synthetische Daten). */
final class Attrappen {

    /** Je ein Fund der Stufen sicher (E-Mail, KVNR), wahrscheinlich (IP) und fraglich (LANR, falsche Pruefziffer). */
    static final String ALLE_STUFEN = "Mail mustermann@example.org, Server 192.0.2.10, KVNR "
            + SynthetischeKvNummern.KVNR_GUELTIG + ", LANR "
            + SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN + ".";

    /** Zwei verschiedene E-Mail-Adressen und eine Wiederholung der ersten. */
    static final String DREI_MAILS = "a: anna@example.org, b: bernd@example.org, wieder a: anna@example.org.";

    private Attrappen() {
    }

    /** Scannt synchron mit dem Standard-Scanner und uebergibt das Ergebnis. */
    static void scannen(Sitzung sitzung, String eingabe) {
        ScanAuftrag auftrag = sitzung.scanAnfordern(eingabe).orElseThrow();
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
    }

    /** Dialoge mit fester Antwort; merkt sich Fragen und Fehlertexte. */
    static final class TestDialoge implements Dialoge {
        final List<Rueckfrage> fragen = new ArrayList<>();
        final List<String> fehler = new ArrayList<>();
        boolean antwort;

        @Override
        public boolean bestaetigt(Rueckfrage frage) {
            fragen.add(frage);
            return antwort;
        }

        @Override
        public void fehler(String text) {
            fehler.add(text);
        }
    }

    /**
     * Zwischenablage-Attrappe im Arbeitsspeicher: zaehlt jeden Zugriff, merkt sich den Inhalt und jeden abgelegten
     * Text; gesperrt: nichts wird angenommen, gelesen oder geleert. Nie die Systemzwischenablage.
     */
    static final class TestZwischenablage implements Zwischenablage {
        int zugriffe;
        int leerAufrufe;
        boolean gesperrt;
        /** Wie die echte AWT bei gesperrter Ablage: Lesen und Ablegen klappen, nur das Leeren scheitert. */
        boolean nurLeerenGesperrt;
        /** Aktueller Inhalt; {@code null}, wenn leer. */
        String inhalt;
        final List<String> gesetzt = new ArrayList<>();

        @Override
        public boolean setzeText(String text) {
            zugriffe++;
            if (gesperrt) {
                return false;
            }
            gesetzt.add(text);
            inhalt = text;
            return true;
        }

        @Override
        public Optional<String> leseText() {
            zugriffe++;
            return gesperrt ? Optional.empty() : Optional.ofNullable(inhalt);
        }

        @Override
        public boolean leeren() {
            zugriffe++;
            leerAufrufe++;
            if (gesperrt || nurLeerenGesperrt) {
                return false;
            }
            inhalt = null;
            return true;
        }
    }
}
