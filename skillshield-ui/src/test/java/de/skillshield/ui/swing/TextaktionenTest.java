package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Kopier-, Ausschneide-, Einfüge- und Ziehwege (Sicherheitsvorgaben 1 und 2, AK-11): (a) über den Komponentenbaum
 * kein Standardweg mehr, (b) mit einer Zwischenablage, die beim Ablegen scheitert, (c) die Aktionen rufen wirklich die
 * Sitzung. Nie die Zwischenablage des Systems; für Swing-Standardaktionen dient eine eigene, zählende
 * {@link Clipboard}.
 */
class TextaktionenTest {

    private static final int STRG = KeyEvent.CTRL_DOWN_MASK;
    private static final int UMSCHALT = KeyEvent.SHIFT_DOWN_MASK;
    private static final KeyStroke[] KOPIERTASTEN = {
        KeyStroke.getKeyStroke(KeyEvent.VK_C, STRG), KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, STRG),
        KeyStroke.getKeyStroke(KeyEvent.VK_COPY, 0)};
    private static final KeyStroke[] AUSSCHNEIDETASTEN = {
        KeyStroke.getKeyStroke(KeyEvent.VK_X, STRG), KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, UMSCHALT),
        KeyStroke.getKeyStroke(KeyEvent.VK_CUT, 0)};
    private static final KeyStroke[] EINFUEGETASTEN = {
        KeyStroke.getKeyStroke(KeyEvent.VK_V, STRG), KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, UMSCHALT),
        KeyStroke.getKeyStroke(KeyEvent.VK_PASTE, 0)};

    /** Eine lokale Zwischenablage, die jedes Ablegen zählt (sie darf nie beschrieben werden). */
    private static final class ZaehlendeAblage extends Clipboard {
        int ablagen;

        ZaehlendeAblage() {
            super("zaehlend");
        }

        @Override
        public synchronized void setContents(Transferable inhalt, ClipboardOwner besitzer) {
            ablagen++;
            super.setContents(inhalt, besitzer);
        }
    }

    private static void sammeln(Component wurzel, List<JComponent> aus) {
        if (wurzel instanceof JComponent komponente) {
            aus.add(komponente);
        }
        if (wurzel instanceof Container container) {
            for (Component kind : container.getComponents()) {
                sammeln(kind, aus);
            }
        }
    }

    private static List<JComponent> alle(Aufbau a) {
        List<JComponent> liste = new ArrayList<>();
        sammeln(a.panel, liste);
        return liste;
    }

    private static Action aktion(JComponent c, KeyStroke taste) {
        Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(taste);
        return name == null ? null : c.getActionMap().get(name);
    }

    private static void ausloesen(Action aktion, JComponent quelle) {
        aktion.actionPerformed(new ActionEvent(quelle, ActionEvent.ACTION_PERFORMED, "test"));
    }

    // ------------------------------------------------------------------ (a) Komponentenbaum

    @Test
    void keineKomponenteBesitztMehrEinenStandardKopierweg() {
        pruefeKopierwege(new Aufbau());
    }

    @Test
    void keineKomponenteBesitztNachDemZuruecksetzenEinenStandardKopierweg() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            a.panel.felderVerwerfen();
        });
        pruefeKopierwege(a);
    }

    @Test
    void einfuegenUndKopierenWirkenNachDemZuruecksetzenWeiter() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            a.zwischenablage.fremd = "Einfügetext";
            ausloesen(eingabe.getActionMap().get("paste"), eingabe);
            assertEquals("Einfügetext", eingabe.getText());
            eingabe.select(0, 3);
            ausloesen(eingabe.getActionMap().get("copy"), eingabe);
            assertEquals(List.of("Ein"), a.zwischenablage.gesetzt);
        });
    }

    private static void pruefeKopierwege(Aufbau a) {
        edt(() -> {
            int textfelder = 0;
            for (JComponent c : alle(a)) {
                TransferHandler handler = c.getTransferHandler();
                assertTrue(handler == null || (handler instanceof Textaktionen.Uebertragung
                        && !(handler instanceof UIResource)), c.getName() + ": Standard-TransferHandler");
                if (handler != null) {
                    assertEquals(TransferHandler.NONE, handler.getSourceActions(c), c.getName());
                }
                if (c instanceof JTextComponent text) {
                    textfelder++;
                    assertFalse(text.getDragEnabled(), c.getName());
                    assertNotNull(handler, c.getName());
                    pruefeActionMap(c);
                } else if (c instanceof JTable tabelle) {
                    assertFalse(tabelle.getDragEnabled());
                    assertNotNull(handler);
                    pruefeActionMap(c);
                } else if (c instanceof JList<?> liste) {
                    assertFalse(liste.getDragEnabled());
                    assertNotNull(handler);
                    pruefeActionMap(c);
                }
            }
            assertTrue(textfelder >= 9, "zu wenige Textfelder gefunden: " + textfelder);
        });
    }

    private static void pruefeActionMap(JComponent c) {
        List<Object> eigene = Arrays.asList(c.getActionMap().keys());
        for (List<String> namen : List.of(Textaktionen.KOPIEREN, Textaktionen.AUSSCHNEIDEN, Textaktionen.EINFUEGEN)) {
            for (String name : namen) {
                assertTrue(eigene.contains(name), c.getName() + ": Aktion " + name + " nicht selbst belegt");
                Action belegt = c.getActionMap().get(name);
                assertFalse(belegt instanceof UIResource, c.getName() + ": " + name);
                assertFalse(belegt.getClass().getName().contains("TransferHandler"), c.getName() + ": " + name);
            }
        }
    }

    @Test
    void jedesTastenkuerzelDerKopierwegeLandetBeiEigenenAktionen() {
        Aufbau a = new Aufbau();
        edt(() -> {
            for (JComponent c : alle(a)) {
                if (!(c instanceof JTextComponent) && !(c instanceof JTable) && !(c instanceof JList)) {
                    continue;
                }
                for (KeyStroke[] gruppe : List.of(KOPIERTASTEN, AUSSCHNEIDETASTEN, EINFUEGETASTEN)) {
                    for (KeyStroke taste : gruppe) {
                        Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(taste);
                        if (name != null) {
                            assertTrue(Arrays.asList(c.getActionMap().keys()).contains(name),
                                    c.getName() + ": " + taste + " -> " + name + " nicht selbst belegt");
                        }
                    }
                }
            }
        });
    }

    @Test
    void swingStandardAktionenLeitenNurAnDieSitzungWeiter() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            eingabe.select(0, 4);
            ausloesen(TransferHandler.getCopyAction(), eingabe);
            assertEquals(List.of(DREI_FUNDE.substring(0, 4)), a.zwischenablage.gesetzt);
            eingabe.copy();
            eingabe.cut();
            assertEquals(3, a.zwischenablage.gesetzt.size());
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.selectAll();
            ausloesen(TransferHandler.getCopyAction(), tabelle);
            ausloesen(TransferHandler.getCutAction(), tabelle);
            tabelle.getActionMap().get("copy").actionPerformed(
                    new ActionEvent(tabelle, ActionEvent.ACTION_PERFORMED, "copy"));
            assertEquals(3, a.zwischenablage.gesetzt.size(), "aus der Tabelle wird nie kopiert");
        });
    }

    @Test
    void exportToClipboardSchreibtNieInEineClipboard() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            ZaehlendeAblage ablage = new ZaehlendeAblage();
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            eingabe.select(0, 4);
            eingabe.getTransferHandler().exportToClipboard(eingabe, ablage, TransferHandler.COPY);
            eingabe.getTransferHandler().exportToClipboard(eingabe, ablage, TransferHandler.MOVE);
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.selectAll();
            tabelle.getTransferHandler().exportToClipboard(tabelle, ablage, TransferHandler.COPY);
            a.panel.kiTextBereich().text().getTransferHandler()
                    .exportToClipboard(a.panel.kiTextBereich().text(), ablage, TransferHandler.COPY);
            assertEquals(0, ablage.ablagen);
            assertFalse(ablage.isDataFlavorAvailable(DataFlavor.stringFlavor));
        });
    }

    // ------------------------------------------------------------------ (b) Ablegen scheitert

    @Test
    void scheiterndesAblegenMeldetUndSchneidetNichtAus() {
        Aufbau a = new Aufbau();
        edt(() -> {
            a.zwischenablage.gesperrt = true;
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            eingabe.setText("Beispieltext zum Ausschneiden");
            eingabe.select(0, 8);
            eingabe.copy();
            assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), a.dialoge.fehler);
            eingabe.cut();
            assertEquals("Beispieltext zum Ausschneiden", eingabe.getText(), "nichts gelöscht, wenn nichts abgelegt");
            assertEquals(2, a.dialoge.fehler.size());
            assertEquals(List.of(), a.zwischenablage.gesetzt);
        });
    }

    @Test
    void ausschneidenLoeschtNurNachErfolgreichemAblegen() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            eingabe.setText("Beispieltext zum Ausschneiden");
            eingabe.select(0, 12);
            for (KeyStroke taste : AUSSCHNEIDETASTEN) {
                Action aktion = aktion(eingabe, taste);
                if (aktion != null) {
                    eingabe.setText("Beispieltext zum Ausschneiden");
                    eingabe.select(0, 12);
                    a.zwischenablage.gesetzt.clear();
                    ausloesen(aktion, eingabe);
                    assertEquals(List.of("Beispieltext"), a.zwischenablage.gesetzt, taste.toString());
                    assertEquals(" zum Ausschneiden", eingabe.getText(), taste.toString());
                }
            }
        });
    }

    @Test
    void gesperrteZwischenablageBeimKopierenAusBereich3LegtNichtsAb() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.zwischenablage.gesperrt = true;
            a.panel.kiTextBereich().text().selectAll();
            a.panel.kiTextBereich().text().copy();
            assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), a.dialoge.fehler);
            assertEquals(List.of(), a.zwischenablage.gesetzt);
        });
    }

    // ------------------------------------------------------------------ (c) Aktionen rufen die Sitzung

    @Test
    void kopierenInBereich3IstFuerKiKopierenMitRueckfrage() {
        Aufbau a = new Aufbau();
        a.scannen("Text ohne erkennbare Stelle, nur Worte.");
        edt(() -> {
            a.dialoge.antwort = false;
            TextBereich feld = a.panel.kiTextBereich().text();
            feld.select(0, 4);
            feld.copy();
            assertEquals(List.of(Rueckfrage.UNVERAENDERT_KOPIEREN), a.dialoge.fragen);
            assertEquals(List.of(), a.zwischenablage.gesetzt, "Abbrechen: nichts abgelegt, auch nicht die Auswahl");
            a.dialoge.antwort = true;
            for (KeyStroke taste : KOPIERTASTEN) {
                Action aktion = aktion(feld, taste);
                if (aktion != null) {
                    a.zwischenablage.gesetzt.clear();
                    ausloesen(aktion, feld);
                    assertEquals(List.of(a.sitzung.textFuerKi()), a.zwischenablage.gesetzt, taste.toString());
                }
            }
        });
    }

    @Test
    void ausschneidenInBereich3GibtEsNicht() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich feld = a.panel.kiTextBereich().text();
            String vorher = feld.getText();
            feld.selectAll();
            feld.cut();
            for (KeyStroke taste : AUSSCHNEIDETASTEN) {
                Action aktion = aktion(feld, taste);
                if (aktion != null) {
                    ausloesen(aktion, feld);
                }
            }
            assertEquals(vorher, feld.getText());
            assertEquals(List.of(), a.zwischenablage.gesetzt);
            assertEquals(List.of(), a.dialoge.fragen);
        });
    }

    @Test
    void kopierenAusAntwortUndErgebnisLaeuftUeberDieSitzungUndDieLeerenRegelKenntDenText() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText("Antwort der KI");
            a.panel.antwortBereich().antwort().select(0, 7);
            a.panel.antwortBereich().antwort().copy();
            assertEquals("Antwort", a.zwischenablage.gesetzt.get(a.zwischenablage.gesetzt.size() - 1));
            a.panel.antwortBereich().antwort().setText(a.zwischenablage.gesetzt.get(0));
            a.panel.zurueckuebersetzen();
            TextBereich ergebnis = a.panel.antwortBereich().ergebnis();
            ergebnis.select(0, 4);
            ergebnis.copy();
            assertEquals(DREI_FUNDE.substring(0, 4), a.zwischenablage.inhalt);
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            assertEquals(1, a.zwischenablage.leerAufrufe, "die Regel kannte den kopierten Ausschnitt");
            assertEquals(null, a.zwischenablage.inhalt);
        });
    }

    @Test
    void ergebnisfeldIstNichtBearbeitbarAberKopierbar() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText(a.zwischenablage.gesetzt.get(0));
            a.panel.zurueckuebersetzen();
            TextBereich ergebnis = a.panel.antwortBereich().ergebnis();
            assertFalse(ergebnis.isEditable());
            ergebnis.select(0, 4);
            ergebnis.cut();
            assertEquals(DREI_FUNDE, ergebnis.getText());
            a.zwischenablage.fremd = "fremd";
            ergebnis.paste();
            assertEquals(DREI_FUNDE, ergebnis.getText());
        });
    }

    // ------------------------------------------------------------------ Einfügen (AK-11)

    @Test
    void einfuegenUebernimmtNurDenTextDesAdapters() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            a.zwischenablage.fremd = "Eingefügter Text";
            eingabe.paste();
            assertEquals("Eingefügter Text", eingabe.getText());
            for (KeyStroke taste : EINFUEGETASTEN) {
                Action aktion = aktion(eingabe, taste);
                if (aktion != null) {
                    eingabe.setText("");
                    ausloesen(aktion, eingabe);
                    assertEquals("Eingefügter Text", eingabe.getText(), taste.toString());
                }
            }
            a.zwischenablage.fremd = null;
            a.zwischenablage.inhalt = null;
            eingabe.setText("bleibt");
            eingabe.paste();
            assertEquals("bleibt", eingabe.getText(), "ohne Text in der Zwischenablage ändert sich nichts");
            assertEquals(List.of(), a.dialoge.fehler, "keine Ausnahme");
        });
    }

    @Test
    void einfuegenInDieAntwortMeldetDieAenderung() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.zwischenablage.fremd = "[[EMAIL_1]]";
            a.panel.antwortBereich().antwort().paste();
            assertTrue(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
        });
    }

    @Test
    void einfuegenInEinGesperrtesFeldAendertNichts() {
        Aufbau a = new Aufbau();
        edt(() -> {
            a.zwischenablage.fremd = "Fremdtext";
            a.panel.kiTextBereich().text().paste();
            assertEquals("", a.panel.kiTextBereich().text().getText());
            a.panel.eingabeBereich().eingabe().setEditable(false);
            a.panel.eingabeBereich().eingabe().paste();
            assertEquals("", a.panel.eingabeBereich().eingabe().getText());
        });
    }

    // ------------------------------------------------------------------ Ablegen per Maus

    /** Transferable mit frei wählbaren Formaten und einem Textinhalt. */
    private static Transferable angebot(String inhalt, DataFlavor... formate) {
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return formate;
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor format) {
                return Arrays.asList(formate).contains(format);
            }

            @Override
            public Object getTransferData(DataFlavor format) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(format)) {
                    throw new UnsupportedFlavorException(format);
                }
                return format.equals(DataFlavor.javaFileListFlavor) ? List.of(new File("example.txt")) : inhalt;
            }
        };
    }

    @Test
    void ablegenNimmtNurTextAn() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            TransferHandler handler = eingabe.getTransferHandler();
            for (DataFlavor format : List.of(DataFlavor.allHtmlFlavor, DataFlavor.fragmentHtmlFlavor,
                    DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor)) {
                Transferable nichtText = angebot("<b>x</b>", format);
                assertFalse(handler.canImport(new TransferHandler.TransferSupport(eingabe, nichtText)));
                assertFalse(handler.importData(eingabe, nichtText), format.getMimeType());
                assertFalse(handler.importData(new TransferHandler.TransferSupport(eingabe, nichtText)));
            }
            Transferable dateiMitText = angebot("Pfadtext", DataFlavor.stringFlavor, DataFlavor.javaFileListFlavor);
            assertFalse(handler.importData(eingabe, dateiMitText), "Dateien werden nie abgelegt");
            assertEquals("", eingabe.getText());
            Transferable text = angebot("Abgelegter Text", DataFlavor.stringFlavor, DataFlavor.allHtmlFlavor);
            assertTrue(handler.canImport(new TransferHandler.TransferSupport(eingabe, text)));
            assertTrue(handler.importData(new TransferHandler.TransferSupport(eingabe, text)));
            assertEquals("Abgelegter Text", eingabe.getText());
        });
    }

    @Test
    void ablegenInNichtBearbeitbareFelderUndTabelleIstAbgelehnt() {
        Aufbau a = new Aufbau();
        edt(() -> {
            Transferable text = angebot("Text", DataFlavor.stringFlavor);
            for (JComponent c : List.of(a.panel.kiTextBereich().text(), a.panel.antwortBereich().ergebnis(),
                    a.panel.fundeBereich().tabelle(), a.panel.antwortBereich().liste())) {
                assertFalse(c.getTransferHandler().canImport(new TransferHandler.TransferSupport(c, text)),
                        c.getName());
                assertFalse(c.getTransferHandler().importData(c, text), c.getName());
            }
            a.panel.eingabeBereich().eingabe().setEditable(false);
            assertFalse(a.panel.eingabeBereich().eingabe().getTransferHandler()
                    .importData(a.panel.eingabeBereich().eingabe(), text));
            assertEquals("", a.panel.eingabeBereich().eingabe().getText());
        });
    }

    @Test
    void ablegenMeldetDieAenderungDerEingabe() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            assertTrue(eingabe.getTransferHandler().importData(eingabe,
                    angebot("mehr", DataFlavor.stringFlavor)));
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
        });
    }

    @Test
    void stringSelectionAlsFremdeQuelleWirdAlsTextAkzeptiert() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            assertTrue(eingabe.getTransferHandler().importData(eingabe, new StringSelection("Auswahltext")));
            assertEquals("Auswahltext", eingabe.getText());
            assertInstanceOf(Textaktionen.Uebertragung.class, eingabe.getTransferHandler());
        });
    }
}
