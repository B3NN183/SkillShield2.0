package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.TableCellEditor;

/**
 * Tastatur der Oberfläche (D-14, AK-45): Tastenkürzel, Alt-Kürzel (Mnemonics), Tab-Tasten der Textfelder und die
 * Leertaste der Fundtabelle.
 *
 * <p><b>Kürzel</b> ({@link Kuerzel}) sind in {@code WHEN_IN_FOCUSED_WINDOW} des Hauptpanels und zusätzlich in den
 * {@code WHEN_FOCUSED}-Maps aller fokussierbaren Komponenten eingetragen. Grund der Doppelbindung: Swing wertet zuerst
 * die {@code WHEN_FOCUSED}-Belegung der fokussierten Komponente aus; belegt ein Look-and-Feel dieselbe Taste dort
 * (ein Textfeld oder die Tabelle), gewönne sie vor der Fensterbindung. Die eigene Belegung der Komponente schlägt
 * die des Look-and-Feel, die Fensterbindung deckt den Rest (Komponenten außerhalb der Liste, z. B. ein Zelleneditor
 * oder ein Rollbalken, und ein Fokus im Fenster außerhalb des Panels). Jedes Kürzel hat
 * einen eigenen Aktionsnamen, der keiner der Standardnamen für Kopieren, Ausschneiden oder Einfügen ist
 * ({@link Textaktionen#KOPIEREN} und Verwandte); "Strg+Umschalt+C" ist also nie das Kopieren eines Feldes, sondern
 * genau "Für KI kopieren". Die Aktionen laufen über dieselben Methoden wie die Schaltflächen und sind nur wirksam,
 * wenn die Funktion im {@link Bedienzustand} aktiv ist; sonst tun sie nichts (auch kein Dialog, keine Statusmeldung).
 */
final class Tastatur {

    private static final int STRG = InputEvent.CTRL_DOWN_MASK;
    private static final int UMSCHALT = InputEvent.SHIFT_DOWN_MASK;

    /**
     * Höchstzahl der Zeilen, die eine Betätigung der Leertaste ändert (jede Änderung rechnet in der Sitzung neu; es
     * gibt dort keine Methode, die eine Auswahl in einem Schritt übernimmt, außer "alle anwählen").
     */
    static final int MAX_UMSCHALTEN = 200;

    /** Aktionsname: Fokus zum nächsten Bedienelement (Tab). */
    static final String FOKUS_VOR = "skillshield-fokus-vor";
    /** Aktionsname: Fokus zum vorherigen Bedienelement (Umschalt+Tab). */
    static final String FOKUS_ZURUECK = "skillshield-fokus-zurueck";
    /** Aktionsname: gewählte Fundzeilen umschalten (Leertaste in der Tabelle). */
    static final String FUNDE_UMSCHALTEN = "skillshield-funde-umschalten";

    static final KeyStroke TAB = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
    static final KeyStroke UMSCHALT_TAB = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, UMSCHALT);
    static final KeyStroke LEERTASTE = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0);

    /** Die Tastenkürzel nach D-14. */
    enum Kuerzel {
        /** Strg+Enter: Scannen. */
        SCANNEN("skillshield-scannen", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, STRG), Funktion.SCANNEN),
        /** Strg+Umschalt+C: Für KI kopieren. */
        FUER_KI_KOPIEREN("skillshield-fuer-ki-kopieren",
                KeyStroke.getKeyStroke(KeyEvent.VK_C, STRG | UMSCHALT), Funktion.FUER_KI_KOPIEREN),
        /** Strg+Umschalt+R: Zurückübersetzen. */
        ZURUECKUEBERSETZEN("skillshield-zurueckuebersetzen",
                KeyStroke.getKeyStroke(KeyEvent.VK_R, STRG | UMSCHALT), Funktion.ZURUECKUEBERSETZEN),
        /** Strg+M: Markierung als Fund hinzufügen. */
        MARKIEREN("skillshield-markieren", KeyStroke.getKeyStroke(KeyEvent.VK_M, STRG),
                Funktion.MARKIERUNG_HINZUFUEGEN);

        final String aktionsname;
        final KeyStroke taste;
        final Funktion funktion;

        Kuerzel(String aktionsname, KeyStroke taste, Funktion funktion) {
            this.aktionsname = aktionsname;
            this.taste = taste;
            this.funktion = funktion;
        }
    }

    private Tastatur() {
    }

    /**
     * Trägt die Tastenkürzel ein: in {@code WHEN_IN_FOCUSED_WINDOW} der Wurzel und in {@code WHEN_FOCUSED} jeder
     * angegebenen Komponente.
     *
     * @param wurzel         das Hauptpanel
     * @param fokussierbare  alle Komponenten, die den Fokus bekommen können
     * @param aktionen       die Aktionsmethoden (dieselben wie die der Schaltflächen)
     * @param zustand        liefert den aktuellen {@link Bedienzustand}
     */
    static void kuerzelBinden(JComponent wurzel, List<? extends JComponent> fokussierbare,
                              Map<Kuerzel, Runnable> aktionen, Supplier<Bedienzustand> zustand) {
        for (Kuerzel kuerzel : Kuerzel.values()) {
            Action aktion = new KuerzelAktion(kuerzel, aktionen.get(kuerzel), zustand);
            eintragen(wurzel, JComponent.WHEN_IN_FOCUSED_WINDOW, kuerzel.taste, kuerzel.aktionsname, aktion);
            for (JComponent komponente : fokussierbare) {
                eintragen(komponente, JComponent.WHEN_FOCUSED, kuerzel.taste, kuerzel.aktionsname, aktion);
            }
        }
    }

    private static void eintragen(JComponent c, int bedingung, KeyStroke taste, String name, Action aktion) {
        c.getInputMap(bedingung).put(taste, name);
        c.getActionMap().put(name, aktion);
    }

    /**
     * Macht Tab und Umschalt+Tab zu Fokustasten des Feldes: kein Tabulatorzeichen ist tippbar, und die Tasten
     * wandern in die Tab-Reihenfolge des Panels. Eingefügte Tabulatoren bleiben im Text. Die Tasten sind zusätzlich
     * in der eigenen {@code InputMap} belegt, falls ein Look-and-Feel "Tab einfügen" bindet.
     */
    static void tabIstFokustaste(JComponent feld, Kontext kontext) {
        feld.setFocusTraversalKeysEnabled(true);
        feld.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                Set.of(TAB, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, STRG)));
        feld.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                Set.of(UMSCHALT_TAB, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, STRG | UMSCHALT)));
        eintragen(feld, JComponent.WHEN_FOCUSED, TAB, FOKUS_VOR,
                new AbstractAction(FOKUS_VOR) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        kontext.sicher(feld::transferFocus);
                    }
                });
        eintragen(feld, JComponent.WHEN_FOCUSED, UMSCHALT_TAB, FOKUS_ZURUECK,
                new AbstractAction(FOKUS_ZURUECK) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        kontext.sicher(feld::transferFocusBackward);
                    }
                });
    }

    /**
     * Tab und Umschalt+Tab verlassen auch die Tabelle (statt in die nächste Zelle zu wandern); die Leertaste
     * wirkt nach der Regel von {@link #umschalten}. Die Pfeiltasten bewegen weiter die Zeilenwahl.
     */
    static void tabelleBelegen(JTable tabelle, Kontext kontext) {
        tabIstFokustaste(tabelle, kontext);
        eintragen(tabelle, JComponent.WHEN_FOCUSED, LEERTASTE, FUNDE_UMSCHALTEN,
                new AbstractAction(FUNDE_UMSCHALTEN) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        kontext.sicher(() -> umschalten(tabelle));
                    }
                });
    }

    /**
     * Die Regel der Leertaste (D-2, AK-16: Abwählen nur einzeln): Ist genau eine Zeile gewählt, wird sie umgeschaltet
     * (an oder ab). Sind mehrere gewählt, werden alle gewählten Zeilen angewählt und keine abgewählt; bereits
     * angewählte bleiben es, höchstens {@link #MAX_UMSCHALTEN} Zeilen ändern sich je Tastendruck. Eine laufende
     * Zellbearbeitung wird zuerst abgebrochen (nicht übernommen), damit kein Editor einen alten Zustand zeigt oder
     * zurückschreibt.
     */
    static void umschalten(JTable tabelle) {
        bearbeitungAbbrechen(tabelle);
        int[] zeilen = tabelle.getSelectedRows();
        int geaendert = 0;
        for (int i = 0; i < zeilen.length && geaendert < MAX_UMSCHALTEN; i++) {
            int zeile = tabelle.convertRowIndexToModel(zeilen[i]);
            if (!tabelle.getModel().isCellEditable(zeile, 0)) {
                continue;
            }
            boolean angewaehlt = Boolean.TRUE.equals(tabelle.getModel().getValueAt(zeile, 0));
            if (zeilen.length == 1) {
                tabelle.getModel().setValueAt(!angewaehlt, zeile, 0);
                geaendert++;
            } else if (!angewaehlt) {
                tabelle.getModel().setValueAt(Boolean.TRUE, zeile, 0);
                geaendert++;
            }
        }
    }

    /** Bricht eine laufende Zellbearbeitung der Tabelle ab, ohne ihren Wert zu übernehmen. */
    static void bearbeitungAbbrechen(JTable tabelle) {
        TableCellEditor editor = tabelle.getCellEditor();
        if (tabelle.isEditing() && editor != null) {
            editor.cancelCellEditing();
        }
    }

    /**
     * Setzt das Alt-Kürzel einer Schaltfläche. Der unterstrichene Buchstabe ist der des ersten Vorkommens von
     * {@code stelle} in der Beschriftung; {@code stelle} beginnt mit dem Buchstaben des Kürzels.
     */
    static void alt(AbstractButton knopf, char buchstabe, String stelle) {
        int index = knopf.getText().indexOf(stelle);
        if (index < 0 || Character.toUpperCase(stelle.charAt(0)) != Character.toUpperCase(buchstabe)) {
            throw new IllegalArgumentException("Kürzel passt nicht zur Beschriftung: " + knopf.getName());
        }
        knopf.setMnemonic(KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(buchstabe)));
        knopf.setDisplayedMnemonicIndex(index);
    }

    /** Setzt das Alt-Kürzel einer Überschrift (Alt+Ziffer springt in das zugehörige Feld). */
    static void alt(JLabel ueberschrift, char zeichen) {
        if (ueberschrift.getText().indexOf(zeichen) < 0) {
            throw new IllegalArgumentException("Kürzel passt nicht zur Beschriftung: " + ueberschrift.getName());
        }
        ueberschrift.setDisplayedMnemonic(KeyEvent.getExtendedKeyCodeForChar(zeichen));
        ueberschrift.setDisplayedMnemonicIndex(ueberschrift.getText().indexOf(zeichen));
    }

    /** Aktion eines Kürzels: wirkt nur, solange die Funktion aktiv ist. */
    private static final class KuerzelAktion extends AbstractAction {
        private final Kuerzel kuerzel;
        private final Runnable aktion;
        private final Supplier<Bedienzustand> zustand;

        KuerzelAktion(Kuerzel kuerzel, Runnable aktion, Supplier<Bedienzustand> zustand) {
            super(kuerzel.aktionsname);
            this.kuerzel = kuerzel;
            this.aktion = aktion;
            this.zustand = zustand;
        }

        /** Gesperrt, solange die Funktion es ist; Swing reicht die Taste dann gar nicht erst an die Aktion. */
        @Override
        public boolean isEnabled() {
            try {
                return zustand.get().istAktiv(kuerzel.funktion);
            } catch (RuntimeException e) {
                // Kein Bedienzustand lesbar: gesperrt; keine Ausnahme in die Tastenverarbeitung.
                return false;
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (isEnabled()) {
                aktion.run();
            }
        }
    }
}
