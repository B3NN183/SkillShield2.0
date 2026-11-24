package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.alleKomponenten;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.FundZeile;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.ScanZustand;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import de.skillshield.ui.swing.SwingAttrappen.TestZwischenablage;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Tastatur (D-14, AK-45, T-7): Jedes Kürzel löst aus jeder fokussierbaren Komponente genau die eine richtige Aktion
 * aus (Auflösung über InputMap und ActionMap, Auslösen der gebundenen Action; nie mit {@code Robot}), ein
 * gesperrtes Kürzel tut nichts, Tab ist in den Textfeldern und der Tabelle eine Fokustaste, die Leertaste schaltet
 * Fundzeilen um, die Mnemonics sind vollständig und eindeutig, und nach dem Binden zeigt kein Tastenkürzel im Baum
 * auf eine Standard-Swing-Aktion für Kopieren, Ausschneiden oder Einfügen. Nur am sichtbaren Fenster prüfbar (nicht
 * hier): ob die Tasten im echten Fenster ankommen (S-8).
 */
class TastaturTest {

    private static final KeyStroke STRG_ENTER = KeyStroke.getKeyStroke("ctrl ENTER");
    private static final KeyStroke STRG_UMSCHALT_C = KeyStroke.getKeyStroke("ctrl shift C");
    private static final KeyStroke STRG_UMSCHALT_R = KeyStroke.getKeyStroke("ctrl shift R");
    private static final KeyStroke STRG_M = KeyStroke.getKeyStroke("ctrl M");
    /** Zwanzig Worte, von denen keines in einem anderen vorkommt. */
    private static final String WORTE = IntStream.rangeClosed(1, 20).mapToObj(i -> String.format("wort%02d", i))
            .collect(Collectors.joining(" "));

    private static List<JComponent> bedienbare(Aufbau a) {
        return ((Fokusreihenfolge) a.panel.getFocusTraversalPolicy()).reihenfolge().stream()
                .map(c -> (JComponent) c).toList();
    }

    /** Löst die Aktion aus, die in der WHEN_FOCUSED-Map der Komponente an der Taste hängt. */
    private static void tasteIn(JComponent c, KeyStroke taste) {
        Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(taste);
        assertNotNull(name, c.getName() + ": keine Aktion für " + taste);
        Action aktion = c.getActionMap().get(name);
        assertNotNull(aktion, c.getName() + ": Aktion fehlt für " + name);
        aktion.actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, "taste"));
    }

    /** Löst die Aktion aus, die im Fenster (WHEN_IN_FOCUSED_WINDOW des Panels) an der Taste hängt. */
    private static void tasteImFenster(Aufbau a, KeyStroke taste) {
        Object name = a.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(taste);
        assertNotNull(name, "keine Fensterbindung für " + taste);
        a.panel.getActionMap().get(name).actionPerformed(
                new ActionEvent(a.panel, ActionEvent.ACTION_PERFORMED, "taste"));
    }

    // ------------------------------------------------------------------ Bindung

    @Test
    void jedesKuerzelIstInAllenFokussierbarenKomponentenUndImFensterGebunden() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertEquals(14, bedienbare(a).size());
            for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
                assertEquals(kuerzel.aktionsname,
                        a.panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(kuerzel.taste));
                assertNotNull(a.panel.getActionMap().get(kuerzel.aktionsname));
                for (JComponent c : bedienbare(a)) {
                    assertEquals(kuerzel.aktionsname, c.getInputMap(JComponent.WHEN_FOCUSED).get(kuerzel.taste),
                            c.getName() + " " + kuerzel);
                    assertNotNull(c.getActionMap().get(kuerzel.aktionsname), c.getName() + " " + kuerzel);
                }
            }
        });
    }

    @Test
    void dieKuerzelHabenDieTastenNachD14() {
        assertEquals(STRG_ENTER, Tastatur.Kuerzel.SCANNEN.taste);
        assertEquals(STRG_UMSCHALT_C, Tastatur.Kuerzel.FUER_KI_KOPIEREN.taste);
        assertEquals(STRG_UMSCHALT_R, Tastatur.Kuerzel.ZURUECKUEBERSETZEN.taste);
        assertEquals(STRG_M, Tastatur.Kuerzel.MARKIEREN.taste);
        assertEquals(Funktion.SCANNEN, Tastatur.Kuerzel.SCANNEN.funktion);
        assertEquals(Funktion.FUER_KI_KOPIEREN, Tastatur.Kuerzel.FUER_KI_KOPIEREN.funktion);
        assertEquals(Funktion.ZURUECKUEBERSETZEN, Tastatur.Kuerzel.ZURUECKUEBERSETZEN.funktion);
        assertEquals(Funktion.MARKIERUNG_HINZUFUEGEN, Tastatur.Kuerzel.MARKIEREN.funktion);
        Set<String> namen = new HashSet<>();
        for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
            assertTrue(namen.add(kuerzel.aktionsname), "Aktionsnamen sind verschieden");
            for (List<String> standard : List.of(Textaktionen.KOPIEREN, Textaktionen.AUSSCHNEIDEN,
                    Textaktionen.EINFUEGEN)) {
                assertFalse(standard.contains(kuerzel.aktionsname), "kein Standardname fürs Kopieren");
            }
        }
    }

    // ------------------------------------------------------------------ Wirkung aus jedem Fokus

    @Test
    void strgEnterScanntAusJederKomponenteUndAusDemFenster() {
        AtomicInteger scans = new AtomicInteger();
        Aufbau a = new Aufbau(t -> {
            scans.incrementAndGet();
            return TextScanner.standard().scan(t);
        }, SwingAttrappen.synchron());
        a.dialoge.antwort = true;
        int erwartet = 0;
        for (JComponent c : bedienbare(a)) {
            String text = DREI_FUNDE + " Lauf " + erwartet;
            edt(() -> a.panel.eingabeBereich().eingabe().setText(text));
            edt(() -> tasteIn(c, STRG_ENTER));
            assertEquals(++erwartet, scans.get(), c.getName() + ": genau ein Scan");
            assertEquals(ScanZustand.GUELTIG, a.sitzung.scanZustand(), c.getName());
        }
        edt(() -> a.panel.eingabeBereich().eingabe().setText(DREI_FUNDE + " Fenster"));
        edt(() -> tasteImFenster(a, STRG_ENTER));
        assertEquals(++erwartet, scans.get());
    }

    @Test
    void strgUmschaltCKopiertFuerDieKiAusJederKomponenteUndNieAnderes() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        int erwartet = 0;
        for (JComponent c : bedienbare(a)) {
            edt(() -> {
                a.panel.eingabeBereich().eingabe().select(0, 4);
                a.panel.antwortBereich().antwort().setText("Antwort");
                a.panel.antwortBereich().antwort().select(0, 3);
                a.panel.kiTextBereich().text().select(0, 2);
                a.panel.fundeBereich().tabelle().selectAll();
                tasteIn(c, STRG_UMSCHALT_C);
            });
            erwartet++;
            assertEquals(erwartet, a.zwischenablage.gesetzt.size(),
                    c.getName() + ": genau ein Ablegen, kein zusätzliches Kopieren des Feldes");
            assertEquals(a.sitzung.textFuerKi(), a.zwischenablage.gesetzt.get(erwartet - 1), c.getName());
        }
        edt(() -> tasteImFenster(a, STRG_UMSCHALT_C));
        assertEquals(erwartet + 1, a.zwischenablage.gesetzt.size());
        assertEquals(List.of(), a.dialoge.fragen, "alle Funde sind angewählt: keine Rückfrage");
        assertEquals(List.of(), a.dialoge.fehler);
    }

    @Test
    void strgUmschaltCFragtNachWennKeinFundAngewaehltIst() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            for (int i = 0; i < tabelle.getRowCount(); i++) {
                tabelle.getModel().setValueAt(false, i, 0);
            }
            a.dialoge.antwort = false;
            tasteIn(a.panel.eingabeBereich().eingabe(), STRG_UMSCHALT_C);
            assertEquals(1, a.dialoge.fragen.size(), "Rückfrage bei keinem angewählten Fund");
            assertEquals(List.of(), a.zwischenablage.gesetzt, "bei Abbrechen wird nichts abgelegt");
        });
    }

    @Test
    void strgUmschaltRUebersetztAusJederKomponenteZurueck() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText(a.sitzung.textFuerKi() + " Ende");
            assertTrue(a.sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        });
        for (JComponent c : bedienbare(a)) {
            Object vorher = edt(() -> a.sitzung.rueckuebersetzung().orElse(null));
            edt(() -> tasteIn(c, STRG_UMSCHALT_R));
            Object nachher = edt(() -> a.sitzung.rueckuebersetzung().orElse(null));
            assertNotNull(nachher, c.getName());
            assertTrue(vorher != nachher, c.getName() + ": jedes Kürzel übersetzt einmal zurück");
        }
        edt(() -> assertTrue(a.sitzung.rueckuebersetzung().get().text().contains("mustermann@example.org")));
    }

    @Test
    void strgMFuegtDieMarkierungAusJederKomponenteHinzu() {
        Aufbau a = new Aufbau(t -> List.of(), SwingAttrappen.synchron());
        a.scannen(WORTE);
        int wort = 0;
        for (JComponent c : bedienbare(a)) {
            String gesucht = WORTE.split(" ")[wort];
            int start = WORTE.indexOf(gesucht);
            int zeilenVorher = edt(() -> a.sitzung.zeilen().size());
            edt(() -> {
                a.panel.eingabeBereich().eingabe().select(start, start + gesucht.length());
                tasteIn(c, STRG_M);
            });
            assertEquals(zeilenVorher + 1, (int) edt(() -> a.sitzung.zeilen().size()), c.getName());
            wort++;
        }
        edt(() -> tasteImFenster(a, STRG_M));
    }

    @Test
    void kuerzelLaufenUeberKontextSicherAlsoOhneAusnahmeBeiFehlern() {
        List<String> fehler = new ArrayList<>();
        Dialoge dialoge = new Dialoge() {
            @Override
            public boolean bestaetigt(Rueckfrage frage) {
                throw new IllegalStateException("Fehlermarker");
            }

            @Override
            public boolean erneutVersuchen(Rueckfrage frage) {
                return false;
            }

            @Override
            public void fehler(String text) {
                fehler.add(text);
            }
        };
        TestZwischenablage ablage = new TestZwischenablage();
        edt(() -> {
            Sitzung sitzung = new Sitzung(TextScanner.standard()::scan, ablage, dialoge);
            HauptPanel panel = new HauptPanel(sitzung, ablage, dialoge, () -> { }, SwingAttrappen.synchron());
            panel.eingabeBereich().eingabe().setText(DREI_FUNDE);
            panel.scannen();
            JTable tabelle = panel.fundeBereich().tabelle();
            for (int i = 0; i < tabelle.getRowCount(); i++) {
                tabelle.getModel().setValueAt(false, i, 0);
            }
            // Die Rückfrage wirft: das Kürzel darf die Ausnahme nie bis zum Ereignis-Thread aufsteigen lassen.
            tasteIn(panel.eingabeBereich().eingabe(), STRG_UMSCHALT_C);
            assertEquals(1, fehler.size());
            assertTrue(fehler.get(0).contains("IllegalStateException"));
            assertFalse(fehler.get(0).contains("Fehlermarker"));
            assertEquals(List.of(), ablage.gesetzt);
        });
    }

    // ------------------------------------------------------------------ gesperrte Kürzel

    @Test
    void gesperrteKuerzelTunNichts() {
        Aufbau a = new Aufbau();
        edt(() -> {
            for (Tastatur.Kuerzel kuerzel : List.of(Tastatur.Kuerzel.FUER_KI_KOPIEREN,
                    Tastatur.Kuerzel.ZURUECKUEBERSETZEN, Tastatur.Kuerzel.MARKIEREN)) {
                assertFalse(a.sitzung.bedienzustand().istAktiv(kuerzel.funktion));
                for (JComponent c : bedienbare(a)) {
                    Action aktion = c.getActionMap().get(kuerzel.aktionsname);
                    assertFalse(aktion.isEnabled(), c.getName() + " " + kuerzel);
                    tasteIn(c, kuerzel.taste);
                }
                a.panel.getActionMap().get(kuerzel.aktionsname).actionPerformed(
                        new ActionEvent(a.panel, ActionEvent.ACTION_PERFORMED, "taste"));
            }
            assertEquals(List.of(), a.dialoge.fragen);
            assertEquals(List.of(), a.dialoge.fehler);
            assertEquals(List.of(), a.zwischenablage.gesetzt);
            assertEquals(Texte.BEREIT, a.sitzung.statusleiste().meldung(), "auch keine Statusmeldung");
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
        });
    }

    @Test
    void strgEnterWaehrendDesScansTutNichts() {
        AtomicInteger scans = new AtomicInteger();
        Aufbau a = new Aufbau(t -> {
            scans.incrementAndGet();
            return TextScanner.standard().scan(t);
        }, SwingAttrappen.haengend());
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText(DREI_FUNDE);
            tasteIn(a.panel.eingabeBereich().eingabe(), STRG_ENTER);
            assertEquals(ScanZustand.LAEUFT, a.sitzung.scanZustand());
            String meldung = a.sitzung.statusleiste().meldung();
            for (JComponent c : bedienbare(a)) {
                assertFalse(c.getActionMap().get(Tastatur.Kuerzel.SCANNEN.aktionsname).isEnabled());
                tasteIn(c, STRG_ENTER);
            }
            tasteImFenster(a, STRG_ENTER);
            assertEquals(ScanZustand.LAEUFT, a.sitzung.scanZustand());
            assertEquals(meldung, a.sitzung.statusleiste().meldung());
            assertEquals(List.of(), a.dialoge.fehler);
        });
    }

    @Test
    void aktiveKuerzelSindFreigegeben() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            for (Tastatur.Kuerzel kuerzel : List.of(Tastatur.Kuerzel.SCANNEN, Tastatur.Kuerzel.FUER_KI_KOPIEREN,
                    Tastatur.Kuerzel.MARKIEREN)) {
                assertTrue(a.panel.getActionMap().get(kuerzel.aktionsname).isEnabled(), kuerzel.toString());
            }
            assertFalse(a.panel.getActionMap().get(Tastatur.Kuerzel.ZURUECKUEBERSETZEN.aktionsname).isEnabled());
        });
    }

    // ------------------------------------------------------------------ Kopierwege bleiben unangetastet

    private static final List<String> SECHS_NAMEN = List.of("copy", "copy-to-clipboard", "cut", "cut-to-clipboard",
            "paste", "paste-from-clipboard");

    @Test
    void nachDemBindenZeigtKeinTastenkuerzelImBaumAufEineStandardAktionFuerKopierenAusschneidenEinfuegen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            int geprueft = 0;
            for (Component k : alleKomponenten(a.panel)) {
                if (!(k instanceof JComponent c)) {
                    continue;
                }
                for (int bedingung : new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                    JComponent.WHEN_IN_FOCUSED_WINDOW}) {
                    InputMap karte = c.getInputMap(bedingung);
                    KeyStroke[] tasten = karte.allKeys();
                    if (tasten == null) {
                        continue;
                    }
                    for (KeyStroke taste : tasten) {
                        Object name = karte.get(taste);
                        Action aktion = name == null ? null : c.getActionMap().get(name);
                        if (aktion == null) {
                            continue;
                        }
                        geprueft++;
                        assertFalse(aktion.getClass().getName().contains("TransferHandler"),
                                c.getName() + ": " + taste + " -> Standard-Übertragung");
                        if (SECHS_NAMEN.contains(name)) {
                            assertFalse(aktion instanceof UIResource, c.getName() + ": " + taste + " -> " + name);
                            assertFalse(aktion.getClass().getName().contains("DefaultEditorKit"),
                                    c.getName() + ": " + taste + " -> " + name);
                        }
                    }
                }
            }
            assertTrue(geprueft > 100, "es wurden Bindungen geprüft: " + geprueft);
            for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
                for (JComponent c : bedienbare(a)) {
                    assertFalse(SECHS_NAMEN.contains(c.getInputMap(JComponent.WHEN_FOCUSED).get(kuerzel.taste)));
                }
            }
        });
    }

    @Test
    void dieSechsKopiernamenSindNachDemBindenWeiterEigeneAktionen() {
        Aufbau a = new Aufbau();
        edt(() -> {
            for (JComponent c : bedienbare(a)) {
                if (!(c instanceof JTextComponent) && !(c instanceof JTable)
                        && !(c instanceof javax.swing.JList<?>)) {
                    continue;
                }
                for (String name : SECHS_NAMEN) {
                    assertTrue(List.of(c.getActionMap().keys()).contains(name), c.getName() + " " + name);
                    assertFalse(c.getActionMap().get(name) instanceof UIResource, c.getName() + " " + name);
                }
            }
        });
    }

    // ------------------------------------------------------------------ Tab

    /** Textfeld, das die Fokuswechsel zählt (ohne sichtbares Fenster bewirkt {@code transferFocus} nichts). */
    private static final class ZaehlendesFeld extends TextBereich {
        int vor;
        int zurueck;

        ZaehlendesFeld() {
            super("zaehlend", 3);
        }

        @Override
        public void transferFocus() {
            vor++;
        }

        @Override
        public void transferFocusBackward() {
            zurueck++;
        }
    }

    @Test
    void tabUndUmschaltTabSindFokustastenKeinTabulatorIstTippbar() {
        edt(() -> {
            Aufbau a = new Aufbau();
            ZaehlendesFeld feld = new ZaehlendesFeld();
            Tastatur.tabIstFokustaste(feld, new Kontext(a.sitzung, a.zwischenablage, a.dialoge));
            assertEquals(Tastatur.FOKUS_VOR, feld.getInputMap(JComponent.WHEN_FOCUSED).get(Tastatur.TAB));
            assertEquals(Tastatur.FOKUS_ZURUECK,
                    feld.getInputMap(JComponent.WHEN_FOCUSED).get(Tastatur.UMSCHALT_TAB));
            assertTrue(feld.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
                    .contains(Tastatur.TAB));
            assertTrue(feld.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
                    .contains(Tastatur.UMSCHALT_TAB));
            assertTrue(feld.getFocusTraversalKeysEnabled());
            tasteIn(feld, Tastatur.TAB);
            tasteIn(feld, Tastatur.UMSCHALT_TAB);
            tasteIn(feld, Tastatur.TAB);
            assertEquals(2, feld.vor);
            assertEquals(1, feld.zurueck);
            assertEquals("", feld.getText(), "Tab fügt keinen Tabulator ein");
            feld.dispatchEvent(new KeyEvent(feld, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, '\t'));
            assertEquals("", feld.getText(), "auch ein getippter Tabulator landet nicht im Text");
        });
    }

    @Test
    void alleTextfelderUndDieTabelleVerlassenDenFokusMitTab() {
        Aufbau a = new Aufbau();
        edt(() -> {
            int geprueft = 0;
            for (JComponent c : bedienbare(a)) {
                if (!(c instanceof TextBereich) && !(c instanceof JTable)) {
                    continue;
                }
                geprueft++;
                assertEquals(Tastatur.FOKUS_VOR, c.getInputMap(JComponent.WHEN_FOCUSED).get(Tastatur.TAB),
                        c.getName());
                assertEquals(Tastatur.FOKUS_ZURUECK,
                        c.getInputMap(JComponent.WHEN_FOCUSED).get(Tastatur.UMSCHALT_TAB), c.getName());
                assertTrue(c.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
                        .contains(Tastatur.TAB), c.getName());
                assertTrue(c.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
                        .contains(Tastatur.UMSCHALT_TAB), c.getName());
            }
            assertEquals(5, geprueft, "vier Textfelder und die Tabelle");
        });
    }

    @Test
    void keinTextfeldBindetTabAnEinfuegenEinesTabulators() {
        Aufbau a = new Aufbau();
        edt(() -> {
            for (JComponent c : bedienbare(a)) {
                if (c instanceof TextBereich) {
                    for (KeyStroke taste : List.of(Tastatur.TAB, Tastatur.UMSCHALT_TAB)) {
                        Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(taste);
                        assertFalse("insert-tab".equals(name), c.getName());
                    }
                }
            }
        });
    }

    @Test
    void eingefuegteTabulatorenBleibenErhalten() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            a.zwischenablage.fremd = "Spalte A\tSpalte B\tSpalte C";
            eingabe.getActionMap().get("paste").actionPerformed(
                    new ActionEvent(eingabe, ActionEvent.ACTION_PERFORMED, "paste"));
            assertEquals("Spalte A\tSpalte B\tSpalte C", eingabe.getText());
            eingabe.einfuegen("\t");
            assertTrue(eingabe.getText().endsWith("\t"));
            eingabe.setText("a\tb");
            assertEquals("a\tb", eingabe.getText());
        });
    }

    // ------------------------------------------------------------------ Leertaste in der Tabelle

    @Test
    void dieLeertasteSchaltetDieGewaehlteZeileUm() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertEquals(Tastatur.FUNDE_UMSCHALTEN, tabelle.getInputMap(JComponent.WHEN_FOCUSED)
                    .get(Tastatur.LEERTASTE));
            assertEquals(3, tabelle.getRowCount());
            assertEquals(Boolean.TRUE, tabelle.getValueAt(1, 0));
            tabelle.setRowSelectionInterval(1, 1);
            tasteIn(tabelle, Tastatur.LEERTASTE);
            assertEquals(Boolean.FALSE, tabelle.getValueAt(1, 0));
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0), "andere Zeilen bleiben");
            assertEquals(Boolean.TRUE, tabelle.getValueAt(2, 0));
            assertEquals(1, tabelle.getSelectedRow(), "die Zeilenwahl bleibt");
            tasteIn(tabelle, Tastatur.LEERTASTE);
            assertEquals(Boolean.TRUE, tabelle.getValueAt(1, 0));
        });
    }

    /** Die Auswahl, die Renderer oder offener Editor zeigen, stimmt mit der Sitzung überein. */
    private static void pruefeAnzeige(Aufbau a) {
        JTable tabelle = a.panel.fundeBereich().tabelle();
        List<FundZeile> zeilen = a.sitzung.zeilen();
        assertEquals(zeilen.size(), tabelle.getRowCount());
        for (int r = 0; r < zeilen.size(); r++) {
            Component c = tabelle.isEditing() && tabelle.getEditingRow() == r
                    ? tabelle.getEditorComponent()
                    : tabelle.prepareRenderer(tabelle.getCellRenderer(r, 0), r, 0);
            assertEquals(zeilen.get(r).angewaehlt(), ((JCheckBox) c).isSelected(), "Anzeige der Zeile " + r);
        }
    }

    @Test
    void strgAUndLeertasteWaehltNichtsAbAuchBeiWiederholung() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.selectAll();
            for (int i = 0; i < 3; i++) {
                tasteIn(tabelle, Tastatur.LEERTASTE);
                for (int r = 0; r < 3; r++) {
                    assertEquals(Boolean.TRUE, tabelle.getValueAt(r, 0), "Zeile " + r + " Druck " + i);
                }
                pruefeAnzeige(a);
            }
        });
    }

    @Test
    void beiMehrfachauswahlWaehltDieLeertasteAlleGewaehltenAnUndLaesstDieAnderenUnveraendert() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.sitzung.anwaehlen(0, false);
            a.sitzung.anwaehlen(2, false);
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.setRowSelectionInterval(0, 1);
            tasteIn(tabelle, Tastatur.LEERTASTE);
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0), "gewählt, war ab: jetzt an");
            assertEquals(Boolean.TRUE, tabelle.getValueAt(1, 0), "gewählt, war an: bleibt an");
            assertEquals(Boolean.FALSE, tabelle.getValueAt(2, 0), "nicht gewählt: unverändert");
            pruefeAnzeige(a);
            tasteIn(tabelle, Tastatur.LEERTASTE);
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0));
            assertEquals(Boolean.TRUE, tabelle.getValueAt(1, 0));
            assertEquals(Boolean.FALSE, tabelle.getValueAt(2, 0));
        });
    }

    @Test
    void einMausKlickAufDasKontrollkaestchenSchaltetNurDieseZeileUmAuchBeiMehrfachauswahl() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.selectAll();
            assertTrue(tabelle.editCellAt(1, 0));
            assertTrue(tabelle.isEditing());
            ((JCheckBox) tabelle.getEditorComponent()).doClick();
            assertFalse(tabelle.isEditing());
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0));
            assertEquals(Boolean.FALSE, tabelle.getValueAt(1, 0));
            assertEquals(Boolean.TRUE, tabelle.getValueAt(2, 0));
            pruefeAnzeige(a);
            assertTrue(tabelle.editCellAt(1, 0));
            ((JCheckBox) tabelle.getEditorComponent()).doClick();
            assertEquals(Boolean.TRUE, tabelle.getValueAt(1, 0));
            pruefeAnzeige(a);
        });
    }

    @Test
    void eineLeertasteBrichtEinenOffenenEditorAbUndAnzeigeUndSitzungStimmen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.setRowSelectionInterval(1, 1);
            assertTrue(tabelle.editCellAt(1, 0));
            assertTrue(((JCheckBox) tabelle.getEditorComponent()).isSelected());
            tasteIn(tabelle, Tastatur.LEERTASTE);
            assertFalse(tabelle.isEditing(), "die Bearbeitung ist abgebrochen");
            assertEquals(Boolean.FALSE, tabelle.getValueAt(1, 0));
            pruefeAnzeige(a);
            a.zwischenablage.gesetzt.clear();
            a.panel.fuerKiKopieren();
            String klartext = a.sitzung.zeilen().get(1).fund().originalValue();
            assertEquals(1, a.zwischenablage.gesetzt.size());
            assertEquals(a.sitzung.textFuerKi(), a.zwischenablage.gesetzt.get(0));
            assertTrue(a.zwischenablage.gesetzt.get(0).contains(klartext),
                    "die abgewählte Zeile bleibt im Klartext: so, wie die Tabelle sie anzeigt");
        });
    }

    @Test
    void einSpaetSchliessenderEditorSchreibtKeinenAltenWertZurueck() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertTrue(tabelle.editCellAt(0, 0));
            TableCellEditor editor = tabelle.getCellEditor();
            a.sitzung.anwaehlen(0, false);
            assertFalse(tabelle.isEditing(), "jede Modellmeldung bricht den Editor ab");
            editor.stopCellEditing();
            assertEquals(Boolean.FALSE, tabelle.getValueAt(0, 0), "der alte Wert kommt nicht zurück");
            a.sitzung.anwaehlen(0, true);
            editor.stopCellEditing();
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0));
            pruefeAnzeige(a);
        });
    }

    @Test
    void derEditorSchreibtAuchOhneAbbruchKeinenVeraltetenWert() {
        edt(() -> {
            DefaultTableModel modell = new DefaultTableModel(new Object[][] {{Boolean.TRUE}, {Boolean.TRUE}},
                    new Object[] {"x"}) {
                @Override
                public Class<?> getColumnClass(int spalte) {
                    return Boolean.class;
                }
            };
            JTable tabelle = new JTable(modell);
            tabelle.setDefaultEditor(Boolean.class, new AuswahlEditor());
            assertTrue(tabelle.editCellAt(0, 0));
            TableCellEditor editor = tabelle.getCellEditor();
            modell.setValueAt(Boolean.FALSE, 0, 0);
            assertTrue(editor.stopCellEditing());
            assertEquals(Boolean.FALSE, modell.getValueAt(0, 0), "der Editor übernimmt seinen alten Haken nicht");
            assertEquals(Boolean.TRUE, modell.getValueAt(1, 0));
        });
    }

    @Test
    void dieTabelleStartetKeinenEditorDurchEineBeliebigeTaste() {
        Aufbau a = new Aufbau();
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertEquals(Boolean.FALSE, tabelle.getClientProperty("JTable.autoStartsEdit"));
            assertEquals(Boolean.TRUE, tabelle.getClientProperty("terminateEditOnFocusLost"));
            assertTrue(tabelle.getDefaultEditor(Boolean.class) instanceof AuswahlEditor);
        });
    }

    @Test
    void eineKuerzelAktionMitDefektemBedienzustandIstGesperrtUndTutNichts() {
        edt(() -> {
            JPanel wurzel = new JPanel();
            JButton knopf = new JButton();
            AtomicInteger aufrufe = new AtomicInteger();
            Map<Tastatur.Kuerzel, Runnable> aktionen = new EnumMap<>(Tastatur.Kuerzel.class);
            for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
                aktionen.put(kuerzel, aufrufe::incrementAndGet);
            }
            Tastatur.kuerzelBinden(wurzel, List.of(knopf), aktionen, () -> {
                throw new IllegalStateException("Fehlermarker");
            });
            for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
                Action aktion = wurzel.getActionMap().get(kuerzel.aktionsname);
                assertFalse(aktion.isEnabled());
                aktion.actionPerformed(new ActionEvent(wurzel, ActionEvent.ACTION_PERFORMED, "taste"));
                tasteIn(knopf, kuerzel.taste);
            }
            assertEquals(0, aufrufe.get());
        });
    }

    @Test
    void dieLeertasteOhneGewaehlteZeileTutNichts() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.clearSelection();
            tasteIn(tabelle, Tastatur.LEERTASTE);
            for (int i = 0; i < 3; i++) {
                assertEquals(Boolean.TRUE, tabelle.getValueAt(i, 0));
            }
            assertEquals(List.of(), a.dialoge.fehler);
        });
    }

    @Test
    void eineLeertasteWaehltHoechstensDieGrenzeAnZeilenAn() {
        List<de.skillshield.core.model.Finding> funde = new ArrayList<>();
        String text = "x".repeat(Tastatur.MAX_UMSCHALTEN + 50);
        for (int i = 0; i < text.length(); i++) {
            funde.add(de.skillshield.core.model.Finding.manual(de.skillshield.core.model.FindingType.TEXT, "x", i,
                    i + 1));
        }
        Aufbau a = new Aufbau(t -> funde, SwingAttrappen.synchron());
        a.scannen(text);
        edt(() -> {
            for (int i = 0; i < text.length(); i++) {
                a.sitzung.anwaehlen(i, false);
            }
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.selectAll();
            tasteIn(tabelle, Tastatur.LEERTASTE);
            int angewaehlt = 0;
            for (int i = 0; i < tabelle.getRowCount(); i++) {
                if (Boolean.TRUE.equals(tabelle.getValueAt(i, 0))) {
                    angewaehlt++;
                }
            }
            assertEquals(Tastatur.MAX_UMSCHALTEN, angewaehlt);
        });
    }

    // ------------------------------------------------------------------ Mnemonics

    @Test
    void mnemonicsSindVollstaendigEindeutigUndInDerBeschriftung() {
        Aufbau a = new Aufbau();
        edt(() -> {
            List<AbstractButton> knoepfe = new ArrayList<>();
            for (Component k : alleKomponenten(a.panel)) {
                if (k instanceof JButton knopf && !SwingAttrappen.istRollbalkenTeil(k)) {
                    knoepfe.add(knopf);
                }
            }
            assertEquals(8, knoepfe.size(), "alle Schaltflächen");
            Set<Integer> codes = new HashSet<>();
            for (AbstractButton knopf : knoepfe) {
                int code = knopf.getMnemonic();
                assertTrue(code != 0, knopf.getName() + " ohne Mnemonic");
                assertTrue(codes.add(code), knopf.getName() + ": Mnemonic doppelt");
                int index = knopf.getDisplayedMnemonicIndex();
                assertTrue(index >= 0, knopf.getName());
                assertEquals(Character.toUpperCase(knopf.getText().charAt(index)),
                        Character.toUpperCase((char) code), knopf.getName() + ": Buchstabe in der Beschriftung");
            }
            for (int i = 1; i <= 4; i++) {
                JLabel etikett = (JLabel) a.panel.komponente("ueberschrift" + i);
                int code = etikett.getDisplayedMnemonic();
                assertEquals(KeyEvent.VK_0 + i, code, etikett.getName());
                assertTrue(codes.add(code), etikett.getName() + ": Mnemonic doppelt");
                assertEquals(0, etikett.getDisplayedMnemonicIndex());
                assertEquals((char) ('0' + i), etikett.getText().charAt(0));
            }
            assertEquals(12, codes.size());
        });
    }

    @Test
    void dieMnemonicsHabenDieFestgelegteBelegung() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertEquals(KeyEvent.VK_S, a.panel.eingabeBereich().scannen().getMnemonic());
            assertEquals(KeyEvent.VK_A, a.panel.fundeBereich().alleAnwaehlen().getMnemonic());
            assertEquals(KeyEvent.VK_M, a.panel.fundeBereich().markieren().getMnemonic());
            assertEquals(KeyEvent.VK_K, a.panel.kiTextBereich().kopieren().getMnemonic());
            assertEquals(KeyEvent.VK_R, a.panel.antwortBereich().zurueckuebersetzen().getMnemonic());
            assertEquals(KeyEvent.VK_T, a.panel.antwortBereich().ergebnisKopieren().getMnemonic());
            assertEquals(KeyEvent.VK_Z, a.panel.zuruecksetzenKnopf().getMnemonic());
            assertEquals(KeyEvent.VK_E, a.panel.beendenKnopf().getMnemonic());
            String ergebnis = a.panel.antwortBereich().ergebnisKopieren().getText();
            assertEquals("Text", ergebnis.substring(a.panel.antwortBereich().ergebnisKopieren()
                    .getDisplayedMnemonicIndex(), a.panel.antwortBereich().ergebnisKopieren()
                    .getDisplayedMnemonicIndex() + 4), "das T von \"Text\" ist unterstrichen");
        });
    }

    @Test
    void dieMnemonicsSindImFensterAlsAltTastenEingetragen() {
        Aufbau a = new Aufbau();
        edt(() -> {
            List<AbstractButton> knoepfe = List.of(a.panel.eingabeBereich().scannen(),
                    a.panel.fundeBereich().alleAnwaehlen(), a.panel.fundeBereich().markieren(),
                    a.panel.kiTextBereich().kopieren(), a.panel.antwortBereich().zurueckuebersetzen(),
                    a.panel.antwortBereich().ergebnisKopieren(), a.panel.zuruecksetzenKnopf(),
                    a.panel.beendenKnopf());
            for (AbstractButton knopf : knoepfe) {
                assertTrue(hatAltTaste(knopf, knopf.getMnemonic()), knopf.getName());
            }
            for (int i = 1; i <= 4; i++) {
                JLabel etikett = (JLabel) a.panel.komponente("ueberschrift" + i);
                assertTrue(hatAltTaste(etikett, etikett.getDisplayedMnemonic()), etikett.getName());
            }
        });
    }

    private static boolean hatAltTaste(JComponent c, int code) {
        InputMap karte = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        KeyStroke[] tasten = karte == null ? null : karte.allKeys();
        if (tasten == null) {
            return false;
        }
        for (KeyStroke taste : tasten) {
            int alt = KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_MASK;
            if (taste.getKeyCode() == code && (taste.getModifiers() & alt) != 0) {
                return true;
            }
        }
        return false;
    }

    private static Component ziel(Aufbau a, String etikett) {
        return ((JLabel) a.panel.komponente(etikett)).getLabelFor();
    }

    @Test
    void dieUeberschriftenSpringenInIhrFeld() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertSame(a.panel.eingabeBereich().eingabe(), ziel(a, "ueberschrift1"));
            assertSame(a.panel.fundeBereich().tabelle(), ziel(a, "ueberschrift2"));
            assertSame(a.panel.kiTextBereich().text(), ziel(a, "ueberschrift3"));
            assertSame(a.panel.antwortBereich().antwort(), ziel(a, "ueberschrift4"));
            assertSame(a.panel.antwortBereich().ergebnis(), ziel(a, "beschriftungErgebnis"));
            assertSame(a.panel.antwortBereich().liste(), ziel(a, "beschriftungListe"));
        });
    }

    @Test
    void dasMnemonicEinerGesperrtenSchaltflaecheWirktNicht() {
        Aufbau a = new Aufbau();
        edt(() -> {
            JButton knopf = a.panel.kiTextBereich().kopieren();
            assertFalse(knopf.isEnabled());
            KeyStroke alt = KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.ALT_DOWN_MASK);
            Object name = knopf.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(alt);
            assertNotNull(name, "Alt+K ist als Mnemonic gebunden");
            Action aktion = knopf.getActionMap().get(name);
            assertNotNull(aktion);
            KeyEvent ereignis = new KeyEvent(knopf, KeyEvent.KEY_PRESSED, 0L, KeyEvent.ALT_DOWN_MASK,
                    KeyEvent.VK_K, 'k');
            assertFalse(SwingUtilities.notifyAction(aktion, alt, ereignis, knopf, KeyEvent.ALT_DOWN_MASK),
                    "die Aktion des gesperrten Knopfes läuft nicht");
            knopf.doClick();
            assertEquals(List.of(), a.zwischenablage.gesetzt);
            assertEquals(List.of(), a.dialoge.fragen);
        });
    }

    /** Textfeld, das die Tastenverarbeitung von Swing ohne Fokus und Fenster zugänglich macht. */
    private static final class TastenFeld extends TextBereich {
        TastenFeld() {
            super("tastenfeld", 3);
        }

        void taste(int art, int code, char zeichen) {
            processKeyEvent(new KeyEvent(this, art, System.currentTimeMillis(), KeyEvent.CTRL_DOWN_MASK, code,
                    zeichen));
        }
    }

    @Test
    void strgEnterImTextfeldScanntUndLaesstDenTextUnveraendertUeberDieEchteTastenverarbeitung() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TastenFeld feld = new TastenFeld();
            feld.setText("Zeile");
            AtomicInteger scans = new AtomicInteger();
            Map<Tastatur.Kuerzel, Runnable> aktionen = new EnumMap<>(Tastatur.Kuerzel.class);
            for (Tastatur.Kuerzel kuerzel : Tastatur.Kuerzel.values()) {
                aktionen.put(kuerzel, kuerzel == Tastatur.Kuerzel.SCANNEN ? scans::incrementAndGet : () -> { });
            }
            Tastatur.kuerzelBinden(new JPanel(), List.of(feld), aktionen, a.sitzung::bedienzustand);
            feld.taste(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n');
            feld.taste(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n');
            feld.taste(KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n');
            assertEquals("Zeile", feld.getText(), "kein Zeilenumbruch durch Strg+Enter");
            assertEquals(1, scans.get(), "genau ein Scan durch den Tastendruck");
        });
    }
}
