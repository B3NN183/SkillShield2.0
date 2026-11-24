package de.skillshield.ui.swing;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Zwischenablage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Attrappen und Hilfen der Ansichtstests (nur synthetische Daten, nie die System-Zwischenablage). */
final class SwingAttrappen {

    /** Je ein Fund: E-Mail, IP-Adresse und KVNR mit Kontextwort. */
    static final String DREI_FUNDE = "Mail mustermann@example.org, Server 192.0.2.10, KVNR "
            + SynthetischeKvNummern.KVNR_GUELTIG + ".";

    private SwingAttrappen() {
    }

    /** Führt die Aktion auf dem Ereignis-Thread aus; Fehler und Assertions werden unverändert weitergegeben. */
    static void edt(Runnable aktion) {
        edt(() -> {
            aktion.run();
            return null;
        });
    }

    static <T> T edt(Supplier<T> aktion) {
        Object[] ergebnis = new Object[1];
        Runnable lauf = () -> ergebnis[0] = aktion.get();
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                lauf.run();
            } else {
                SwingUtilities.invokeAndWait(lauf);
            }
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            if (e.getCause() instanceof Error fehler) {
                throw fehler;
            }
            throw new AssertionError("Unerwartet auf dem Ereignis-Thread", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Unterbrochen", e);
        }
        @SuppressWarnings("unchecked")
        T wert = (T) ergebnis[0];
        return wert;
    }

    /** Alle Komponenten unterhalb der Wurzel (einschließlich ihr selbst), Tiefensuche. */
    static List<java.awt.Component> alleKomponenten(java.awt.Component wurzel) {
        List<java.awt.Component> liste = new ArrayList<>();
        sammeln(wurzel, liste);
        return liste;
    }

    private static void sammeln(java.awt.Component c, List<java.awt.Component> liste) {
        liste.add(c);
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component kind : container.getComponents()) {
                sammeln(kind, liste);
            }
        }
    }

    /** {@code true} für die Pfeil-Schaltflächen der Rollbalken (vom Look-and-Feel, nicht vom Programm). */
    static boolean istRollbalkenTeil(java.awt.Component c) {
        for (java.awt.Component k = c; k != null; k = k.getParent()) {
            if (k instanceof javax.swing.JScrollBar) {
                return true;
            }
        }
        return false;
    }

    /** Starter, der nie fertig wird: Der Scan bleibt im Zustand "läuft". */
    static ScanStarter haengend() {
        return (sitzung, auftrag) -> { };
    }

    /** Starter, der synchron ausführt (kein Hintergrund): Ergebnis oder Fehler gehen sofort an die Sitzung. */
    static ScanStarter synchron() {
        return (sitzung, auftrag) -> {
            try {
                sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
            } catch (RuntimeException e) {
                sitzung.scanFehlgeschlagen(auftrag, e);
            }
        };
    }

    /** Dialoge mit fester Antwort, merken Fragen und Fehlertexte. */
    static final class TestDialoge implements Dialoge {
        final List<Rueckfrage> fragen = new ArrayList<>();
        final List<String> fehler = new ArrayList<>();
        final List<Boolean> fehlerAufEdt = new ArrayList<>();
        final ArrayDeque<Boolean> erneutAntworten = new ArrayDeque<>();
        boolean antwort;

        @Override
        public boolean bestaetigt(Rueckfrage frage) {
            fragen.add(frage);
            return antwort;
        }

        @Override
        public boolean erneutVersuchen(Rueckfrage frage) {
            fragen.add(frage);
            Boolean a = erneutAntworten.poll();
            return a != null && a;
        }

        @Override
        public void fehler(String text) {
            fehler.add(text);
            fehlerAufEdt.add(SwingUtilities.isEventDispatchThread());
        }
    }

    /** Zwischenablage im Arbeitsspeicher; zählt Ablegen und Leeren, kann beim Ablegen scheitern. */
    static final class TestZwischenablage implements Zwischenablage {
        final List<String> gesetzt = new ArrayList<>();
        int leerAufrufe;
        boolean gesperrt;
        String inhalt;
        /** Inhalt, den {@link #leseText()} liefert, unabhängig vom Ablegen (Einfügen aus einem fremden Programm). */
        String fremd;

        @Override
        public boolean setzeText(String text) {
            if (gesperrt) {
                return false;
            }
            gesetzt.add(text);
            inhalt = text;
            return true;
        }

        @Override
        public Optional<String> leseText() {
            return fremd != null ? Optional.of(fremd) : Optional.ofNullable(inhalt);
        }

        @Override
        public boolean leeren() {
            leerAufrufe++;
            if (gesperrt) {
                return false;
            }
            inhalt = null;
            fremd = null;
            return true;
        }
    }

    /** Alles, was ein Panel-Test braucht, auf dem Ereignis-Thread gebaut. */
    static final class Aufbau {
        final TestDialoge dialoge = new TestDialoge();
        final TestZwischenablage zwischenablage = new TestZwischenablage();
        final Sitzung sitzung;
        final HauptPanel panel;
        final List<Integer> beendenAufrufe = new ArrayList<>();

        Aufbau() {
            this(TextScanner.standard()::scan, synchron());
        }

        Aufbau(Function<String, List<Finding>> scanner, ScanStarter starter) {
            Object[] teile = edt(() -> {
                Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);
                HauptPanel p = new HauptPanel(s, zwischenablage, dialoge, () -> beendenAufrufe.add(1), starter);
                return new Object[] {s, p};
            });
            sitzung = (Sitzung) teile[0];
            panel = (HauptPanel) teile[1];
        }

        /** Setzt den Text, scannt synchron und wartet nicht (der Starter arbeitet auf dem Ereignis-Thread). */
        void scannen(String text) {
            edt(() -> {
                panel.eingabeBereich().eingabe().setText(text);
                panel.scannen();
            });
        }
    }
}
