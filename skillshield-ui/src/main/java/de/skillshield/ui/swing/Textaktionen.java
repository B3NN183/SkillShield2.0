package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Zwischenablage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;

/**
 * Kopieren, Ausschneiden und Einfügen aller Textfelder, Tabellen und Listen (Sicherheitsvorgabe 1 und 2): Kein Weg
 * führt am Adapter der Zwischenablage vorbei. Der Standard-{@link TransferHandler} von Swing schreibt direkt in die
 * System-Zwischenablage und würde die Leeren-Regel (D-6) aushebeln; er wird durch {@link Uebertragung} ersetzt, und
 * alle Aktionsnamen, auf die Tastenkürzel, Kontextmenü und {@code JTextComponent.copy()} abbilden, sind in der
 * {@link ActionMap} des Feldes selbst belegt (sie überlebt den Wechsel des Look-and-Feel).
 *
 * <ul>
 *   <li>Bereich 3: jedes Kopieren ist {@link Sitzung#fuerKiKopieren()} (mit Rückfrage), Ausschneiden gibt es nicht.
 *   <li>Eingabe, KI-Antwort, Ergebnis: {@link Sitzung#textKopieren(String)} mit der Auswahl; Ausschneiden löscht
 *       nur, wenn das Ablegen gelang.
 *   <li>Einfügen (nur Eingabe und KI-Antwort): nur Text, über {@link Zwischenablage#leseText()}.
 *   <li>Tabelle und Liste: Kopieren, Ausschneiden, Einfügen und Ziehen sind abgeschaltet.
 * </ul>
 */
final class Textaktionen {

    /** Aktionsnamen für Kopieren (Tastenkürzel, COPY-Taste, Strg+Einfg, {@code JTextComponent.copy()}). */
    static final List<String> KOPIEREN = List.of("copy", "copy-to-clipboard");
    /** Aktionsnamen für Ausschneiden (Strg+X, Umschalt+Entf, CUT-Taste). */
    static final List<String> AUSSCHNEIDEN = List.of("cut", "cut-to-clipboard");
    /** Aktionsnamen für Einfügen (Strg+V, Umschalt+Einfg, PASTE-Taste). */
    static final List<String> EINFUEGEN = List.of("paste", "paste-from-clipboard");

    /** Aufgabe eines Textfeldes. */
    enum Rolle {
        /** Eingabe (Bereich 1) und KI-Antwort (Bereich 4): bearbeitbar, Auswahl kopierbar. */
        BEARBEITBAR,
        /** Text für die KI (Bereich 3): Kopieren ist immer "Für KI kopieren". */
        FUER_KI,
        /** Ergebnisfeld: markier- und kopierbar, nicht bearbeitbar. */
        NUR_LESEN,
        /** Hinweistexte: weder Kopieren noch Einfügen. */
        HINWEIS
    }

    private Textaktionen() {
    }

    /** Installiert Aktionen und Übertragung für ein Textfeld. */
    static void installiere(JTextComponent feld, Rolle rolle, Kontext kontext) {
        Runnable kopieren = () -> kontext.sicher(() -> kopieren(feld, rolle, kontext.sitzung));
        Runnable ausschneiden = () -> kontext.sicher(() -> ausschneiden(feld, rolle, kontext.sitzung));
        Runnable einfuegen = () -> kontext.sicher(() -> einfuegen(feld, rolle, kontext.zwischenablage));
        feld.setTransferHandler(new Uebertragung(rolle == Rolle.BEARBEITBAR ? feld : null, kopieren, ausschneiden));
        belegen(feld.getActionMap(), KOPIEREN, kopieren);
        belegen(feld.getActionMap(), AUSSCHNEIDEN, ausschneiden);
        belegen(feld.getActionMap(), EINFUEGEN, einfuegen);
    }

    /** Schaltet Kopieren, Ausschneiden, Einfügen und Ziehen einer Tabelle oder Liste ab. */
    static void sperre(JComponent komponente) {
        Runnable nichts = () -> { };
        komponente.setTransferHandler(new Uebertragung(null, nichts, nichts));
        belegen(komponente.getActionMap(), KOPIEREN, nichts);
        belegen(komponente.getActionMap(), AUSSCHNEIDEN, nichts);
        belegen(komponente.getActionMap(), EINFUEGEN, nichts);
    }

    private static void belegen(ActionMap karte, List<String> namen, Runnable aktion) {
        for (String name : namen) {
            karte.put(name, new AbstractAction(name) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    aktion.run();
                }
            });
        }
    }

    private static void kopieren(JTextComponent feld, Rolle rolle, Sitzung sitzung) {
        switch (rolle) {
            case FUER_KI -> sitzung.fuerKiKopieren();
            case HINWEIS -> { }
            case BEARBEITBAR, NUR_LESEN -> sitzung.textKopieren(feld.getSelectedText());
        }
    }

    private static void ausschneiden(JTextComponent feld, Rolle rolle, Sitzung sitzung) {
        if (rolle != Rolle.BEARBEITBAR || !feld.isEditable()) {
            return;
        }
        String auswahl = feld.getSelectedText();
        if (auswahl != null && !auswahl.isEmpty() && sitzung.textKopieren(auswahl)) {
            feld.replaceSelection("");
        }
    }

    private static void einfuegen(JTextComponent feld, Rolle rolle, Zwischenablage zwischenablage) {
        if (rolle != Rolle.BEARBEITBAR || !feld.isEditable()) {
            return;
        }
        zwischenablage.leseText().filter(text -> !text.isEmpty()).ifPresent(text -> einsetzen(feld, text));
    }

    private static void einsetzen(JTextComponent feld, String text) {
        if (feld instanceof TextBereich bereich) {
            bereich.einfuegen(text);
        } else {
            feld.replaceSelection(text);
        }
    }

    /**
     * Ersatz für den Standard-{@link TransferHandler}: nichts davon schreibt in eine {@code Clipboard}, nichts lässt
     * sich ziehen. Eingefügt oder abgelegt wird nur Text ({@link DataFlavor#stringFlavor}), und nur in ein
     * bearbeitbares Feld; Dateilisten und andere Formate werden abgelehnt.
     */
    static final class Uebertragung extends TransferHandler {

        private final JTextComponent ziel;
        private final Runnable kopieren;
        private final Runnable ausschneiden;

        /**
         * @param ziel         das Feld, das Text aufnehmen darf; {@code null}, wenn nichts abgelegt werden darf
         * @param kopieren     Aktion für Kopieren (statt der Standardimplementierung)
         * @param ausschneiden Aktion für Ausschneiden
         */
        Uebertragung(JTextComponent ziel, Runnable kopieren, Runnable ausschneiden) {
            this.ziel = ziel;
            this.kopieren = kopieren;
            this.ausschneiden = ausschneiden;
        }

        /** Nichts darf gezogen werden. */
        @Override
        public int getSourceActions(JComponent c) {
            return NONE;
        }

        /** Kein Ziehen, auch nicht programmatisch. */
        @Override
        public void exportAsDrag(JComponent comp, InputEvent e, int action) {
            // bewusst leer
        }

        /** Schreibt nie in die übergebene {@code Clipboard}; leitet an die Aktionen der Sitzung weiter. */
        @Override
        public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
            if (action == COPY) {
                kopieren.run();
            } else if (action == MOVE) {
                ausschneiden.run();
            }
        }

        @Override
        public boolean canImport(JComponent comp, DataFlavor[] flavors) {
            return ziel != null && ziel.isEditable() && nurText(flavors);
        }

        @Override
        public boolean importData(JComponent comp, Transferable inhalt) {
            if (ziel == null || !ziel.isEditable()) {
                return false;
            }
            try {
                if (!nurText(inhalt.getTransferDataFlavors())) {
                    return false;
                }
                Object daten = inhalt.getTransferData(DataFlavor.stringFlavor);
                if (daten instanceof String text && !text.isEmpty()) {
                    einsetzen(ziel, text);
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException | RuntimeException e) {
                // Kein Text lesbar: ablehnen. Die Meldung der Ausnahme könnte Inhalt enthalten und wird nicht gezeigt.
                return false;
            }
            return false;
        }

        /** Nur Text: {@code stringFlavor} ist angeboten, eine Dateiliste nicht. */
        private static boolean nurText(DataFlavor[] formate) {
            boolean text = false;
            for (DataFlavor format : formate) {
                if (format.isFlavorJavaFileListType()) {
                    return false;
                }
                if (DataFlavor.stringFlavor.equals(format)) {
                    text = true;
                }
            }
            return text;
        }

        /** Nur der Klassenname. */
        @Override
        public String toString() {
            return "Uebertragung";
        }
    }
}
