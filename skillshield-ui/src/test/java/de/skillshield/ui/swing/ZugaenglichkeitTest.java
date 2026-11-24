package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.alleKomponenten;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleTable;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;
import org.junit.jupiter.api.Test;

/**
 * Zugänglichkeit und Tooltips (AK-47, AK-48, OF-5, T-7): feste deutsche Namen für alle Bedienelemente, Spaltenköpfe
 * der Tabelle, Sperrgrund als Tooltip und Beschreibung gesperrter Schaltflächen, und in keinem Zustand ein Name,
 * eine Beschreibung oder ein Tooltip mit Eingabe-, Fund- oder KI-Text (auch nicht über den Renderer der Zellen).
 * Nur mit Hilfstechnik oder am Fenster prüfbar (nicht hier): ob ein Screenreader die Namen vorliest (S-9), ob ein
 * Tooltip bei der Maus erscheint (S-8). Der Inhalt der Textfelder geht bestimmungsgemäß über die
 * Textschnittstelle der Zugänglichkeit an Screenreader; er ist kein Name, keine Beschreibung und kein Tooltip.
 */
class ZugaenglichkeitTest {

    private static final String MAIL = "quarzmarker@example.org";
    private static final String NAME = "Quarzfalke";
    private static final String IP = "192.0.2.10";
    private static final String ANTWORT_MARKER = "Antwortmarker";
    private static final List<String> MARKER = List.of("quarzmarker", NAME, IP, SynthetischeKvNummern.KVNR_GUELTIG,
            ANTWORT_MARKER);
    private static final String EINGABE = "Kontakt: " + MAIL + ", Name " + NAME + ", Server " + IP + ", KVNR "
            + SynthetischeKvNummern.KVNR_GUELTIG + ". " + NAME + " ruft an.";
    private static final String ANTWORT = ANTWORT_MARKER + " [[EMAIL_1]] [[EMAIL_9]] [[Name_1]] [[TEXT_1]]";

    /** Alle festen Texte der Fachlogik: Namen, Beschreibungen und Tooltips dürfen nur diese sein. */
    private static final Set<String> FESTE = feste();

    private static Set<String> feste() {
        Set<String> menge = new HashSet<>(Texte.SPALTENKOEPFE);
        for (Field f : Texte.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    menge.add((String) f.get(null));
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        return menge;
    }

    private static Map<JButton, Funktion> knoepfe(Aufbau a) {
        Map<JButton, Funktion> karte = new LinkedHashMap<>();
        karte.put(a.panel.eingabeBereich().scannen(), Funktion.SCANNEN);
        karte.put(a.panel.fundeBereich().alleAnwaehlen(), Funktion.ALLE_ANWAEHLEN);
        karte.put(a.panel.fundeBereich().markieren(), Funktion.MARKIERUNG_HINZUFUEGEN);
        karte.put(a.panel.kiTextBereich().kopieren(), Funktion.FUER_KI_KOPIEREN);
        karte.put(a.panel.antwortBereich().zurueckuebersetzen(), Funktion.ZURUECKUEBERSETZEN);
        karte.put(a.panel.antwortBereich().ergebnisKopieren(), Funktion.ERGEBNIS_KOPIEREN);
        karte.put(a.panel.zuruecksetzenKnopf(), Funktion.ZURUECKSETZEN);
        karte.put(a.panel.beendenKnopf(), Funktion.BEENDEN);
        return karte;
    }

    // ------------------------------------------------------------------ Namen (AK-47)

    @Test
    void jedesBedienelementHatEinenFestenNichtLeerenNamen() {
        Aufbau a = new Aufbau();
        edt(() -> {
            List<Component> bedien = new ArrayList<>(((Fokusreihenfolge) a.panel.getFocusTraversalPolicy())
                    .reihenfolge());
            bedien.add(a.panel.komponente("statusMeldung"));
            bedien.add(a.panel.komponente("statusZuordnung"));
            bedien.add(a.panel.komponente("statusleiste"));
            bedien.add(a.panel.komponente("fussleiste"));
            for (String bereich : List.of("bereich1", "bereich2", "bereich3", "bereich4")) {
                bedien.add(a.panel.komponente(bereich));
            }
            bedien.add(a.panel.fundeBereich().tabelle().getTableHeader());
            for (Component k : alleKomponenten(a.panel)) {
                if (k instanceof Hinweistext) {
                    bedien.add(k);
                }
            }
            assertEquals(14 + 4 + 4 + 1 + 5, bedien.size());
            for (Component k : bedien) {
                assertNotNull(k, "Bedienelement fehlt");
                String name = ((Accessible) k).getAccessibleContext().getAccessibleName();
                assertNotNull(name, k.getName() + " ohne zugänglichen Namen");
                assertFalse(name.isBlank(), k.getName());
                assertTrue(FESTE.contains(name), k.getName() + ": kein fester Text: " + name);
            }
        });
    }

    @Test
    void dieNamenSindVerschiedenInIhrerGruppe() {
        Aufbau a = new Aufbau();
        edt(() -> {
            Set<String> gesehen = new HashSet<>();
            for (Component k : ((Fokusreihenfolge) a.panel.getFocusTraversalPolicy()).reihenfolge()) {
                String name = ((Accessible) k).getAccessibleContext().getAccessibleName();
                assertTrue(gesehen.add(name), "Name doppelt: " + name);
            }
        });
    }

    @Test
    void dieTabelleHatSpaltenkoepfeFuerHilfstechnik() {
        Aufbau a = new Aufbau();
        a.scannen(EINGABE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            JTableHeader kopf = tabelle.getTableHeader();
            assertNotNull(kopf);
            assertTrue(kopf.isVisible());
            assertEquals(Texte.SPALTENKOEPFE.size(), tabelle.getColumnCount());
            for (int i = 0; i < tabelle.getColumnCount(); i++) {
                assertEquals(Texte.SPALTENKOEPFE.get(i), tabelle.getColumnName(i));
                assertEquals(Texte.SPALTENKOEPFE.get(i), tabelle.getColumnModel().getColumn(i).getHeaderValue());
            }
            AccessibleTable zugang = tabelle.getAccessibleContext().getAccessibleTable();
            assertNotNull(zugang);
            assertEquals(Texte.SPALTENKOEPFE.size(), zugang.getAccessibleColumnCount());
            assertNotNull(zugang.getAccessibleColumnHeader(), "Spaltenköpfe sind für Hilfstechnik erkennbar");
            assertEquals(Texte.NAME_FUNDE, tabelle.getAccessibleContext().getAccessibleName());
            assertEquals(Texte.NAME_SPALTENKOEPFE, kopf.getAccessibleContext().getAccessibleName());
        });
    }

    @Test
    void dieDauerhinweiseStehenAlsBeschreibungAmZugehoerigenBedienelement() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertEquals(Texte.HINWEIS_ERKENNUNG,
                    a.panel.fundeBereich().tabelle().getAccessibleContext().getAccessibleDescription());
            assertEquals(Texte.HINWEIS_ZUORDNUNG,
                    a.panel.antwortBereich().antwort().getAccessibleContext().getAccessibleDescription());
        });
    }

    // ------------------------------------------------------------------ Tooltips (AK-48)

    private static void pruefeSperrgruende(Aufbau a) {
        edt(() -> {
            Bedienzustand zustand = a.sitzung.bedienzustand();
            for (Map.Entry<JButton, Funktion> e : knoepfe(a).entrySet()) {
                JButton knopf = e.getKey();
                Funktion funktion = e.getValue();
                assertEquals(zustand.istAktiv(funktion), knopf.isEnabled(), knopf.getName());
                if (zustand.istAktiv(funktion)) {
                    assertNull(knopf.getToolTipText(), knopf.getName() + ": aktiv, also kein Sperrgrund");
                    assertNull(knopf.getAccessibleContext().getAccessibleDescription(), knopf.getName());
                } else {
                    String grund = zustand.sperrgrund().get(funktion);
                    assertNotNull(grund, knopf.getName());
                    assertFalse(grund.isBlank(), knopf.getName());
                    assertEquals(grund, knopf.getToolTipText(), knopf.getName());
                    assertEquals(grund, knopf.getAccessibleContext().getAccessibleDescription(), knopf.getName());
                    assertTrue(FESTE.contains(grund), knopf.getName());
                }
                assertEquals(Boolean.TRUE, knopf.getClientProperty("html.disable"), knopf.getName());
                assertEquals(Boolean.TRUE, knopf.createToolTip().getClientProperty("html.disable"),
                        knopf.getName() + ": auch der Tooltip selbst deutet nie HTML");
            }
        });
    }

    @Test
    void geperrteSchaltflaechenZeigenInJedemZustandIhrenSperrgrund() {
        Aufbau a = new Aufbau();
        pruefeSperrgruende(a);
        a.scannen(EINGABE);
        pruefeSperrgruende(a);
        edt(() -> a.panel.fuerKiKopieren());
        pruefeSperrgruende(a);
        edt(() -> a.panel.antwortBereich().antwort().setText(ANTWORT));
        pruefeSperrgruende(a);
        edt(() -> a.panel.zurueckuebersetzen());
        pruefeSperrgruende(a);
        edt(() -> a.panel.eingabeBereich().eingabe().append("!"));
        pruefeSperrgruende(a);
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
        });
        pruefeSperrgruende(a);
    }

    @Test
    void waehrendDesScansZeigenGesperrteSchaltflaechenDenGrundScanLaeuft() {
        Aufbau a = new Aufbau(TextScanner.standard()::scan, SwingAttrappen.haengend());
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText(EINGABE);
            a.panel.scannen();
        });
        pruefeSperrgruende(a);
        edt(() -> {
            assertEquals(Texte.SCAN_LAEUFT, a.panel.eingabeBereich().scannen().getToolTipText());
            assertEquals(Texte.SCAN_LAEUFT, a.panel.zuruecksetzenKnopf().getToolTipText());
        });
    }

    @Test
    void nachFreigabeVerschwindetDerSperrgrund() {
        Aufbau a = new Aufbau();
        edt(() -> assertEquals(Texte.ERST_SCANNEN, a.panel.kiTextBereich().kopieren().getToolTipText()));
        a.scannen(EINGABE);
        edt(() -> assertNull(a.panel.kiTextBereich().kopieren().getToolTipText()));
    }

    // ------------------------------------------------------------------ kein Klartext in Namen und Tooltips

    private static void keinMarker(String text, String wo) {
        if (text == null) {
            return;
        }
        for (String marker : MARKER) {
            assertFalse(text.contains(marker), wo + " enthält einen Marker");
        }
        assertTrue(FESTE.contains(text), wo + ": kein fester Text");
    }

    private static MouseEvent maus(Component quelle, Rectangle zelle) {
        return new MouseEvent(quelle, MouseEvent.MOUSE_MOVED, 0L, 0, zelle.x + zelle.width / 2,
                zelle.y + zelle.height / 2, 0, false);
    }

    /**
     * Zellen- und Zeilen-Renderer hängen (nach dem Zeichnen) an einem {@code CellRendererPane} im Baum. Ihr
     * zugänglicher Name ist bestimmungsgemäß der Inhalt der Zelle: Darüber liest Hilfstechnik die Tabelle und die
     * Liste vor (wie die Textfelder ihren Inhalt über die Textschnittstelle). Tooltips der Renderer prüft der Test
     * trotzdem.
     */
    private static boolean imRendererBereich(Component k) {
        for (Component p = k; p != null; p = p.getParent()) {
            if (p instanceof javax.swing.CellRendererPane) {
                return true;
            }
        }
        return false;
    }

    private static void pruefeBaum(Aufbau a) {
        edt(() -> {
            for (Component k : alleKomponenten(a.panel)) {
                String wo = k.getClass().getSimpleName() + " " + k.getName();
                if (k instanceof JComponent c) {
                    keinMarker(c.getToolTipText(), wo + " Tooltip");
                    if (c.getToolTipText() != null) {
                        assertEquals(Boolean.TRUE, c.getClientProperty("html.disable"), wo);
                    }
                }
                if (SwingAttrappen.istRollbalkenTeil(k)) {
                    continue;
                }
                if (k instanceof Accessible zugang && !imRendererBereich(k)) {
                    AccessibleContext kontext = zugang.getAccessibleContext();
                    if (kontext != null) {
                        keinMarker(kontext.getAccessibleName(), wo + " Name");
                        keinMarker(kontext.getAccessibleDescription(), wo + " Beschreibung");
                    }
                }
            }
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.setSize(900, 400);
            tabelle.doLayout();
            for (int zeile = 0; zeile < tabelle.getRowCount(); zeile++) {
                for (int spalte = 0; spalte < tabelle.getColumnCount(); spalte++) {
                    Rectangle zelle = tabelle.getCellRect(zeile, spalte, false);
                    assertNull(tabelle.getToolTipText(maus(tabelle, zelle)), "Tooltip aus einer Tabellenzelle");
                    Component renderer = tabelle.prepareRenderer(tabelle.getCellRenderer(zeile, spalte), zeile,
                            spalte);
                    if (renderer instanceof JComponent c) {
                        assertNull(c.getToolTipText(), "Tooltip am Zellen-Renderer");
                    }
                }
            }
            JTableHeader kopf = tabelle.getTableHeader();
            kopf.setSize(900, 20);
            for (int spalte = 0; spalte < tabelle.getColumnCount(); spalte++) {
                keinMarker(kopf.getToolTipText(maus(kopf, kopf.getHeaderRect(spalte))), "Spaltenkopf");
            }
            JList<String> liste = a.panel.antwortBereich().liste();
            liste.setSize(400, 400);
            for (int i = 0; i < liste.getModel().getSize(); i++) {
                assertNull(liste.getToolTipText(maus(liste, liste.getCellBounds(i, i))), "Tooltip aus einer Zeile");
                Component zeile = liste.getCellRenderer().getListCellRendererComponent(liste,
                        liste.getModel().getElementAt(i), i, false, false);
                assertNull(((JComponent) zeile).getToolTipText(), "Tooltip am Zeilen-Renderer");
            }
        });
    }

    @Test
    void keinNameKeineBeschreibungKeinTooltipTraegtEingabeFundOderKiText() {
        Aufbau a = new Aufbau();
        pruefeBaum(a);
        a.scannen(EINGABE);
        pruefeBaum(a);
        edt(() -> {
            int start = EINGABE.indexOf(NAME);
            a.panel.eingabeBereich().eingabe().select(start, start + NAME.length());
            a.panel.markieren();
            assertTrue(a.panel.fundeBereich().tabelle().getRowCount() > 3);
            assertTrue(((JLabel) a.panel.komponente("statusMeldung")).getText() != null);
        });
        pruefeBaum(a);
        edt(() -> a.panel.fuerKiKopieren());
        pruefeBaum(a);
        edt(() -> a.panel.antwortBereich().antwort().setText(ANTWORT));
        pruefeBaum(a);
        edt(() -> {
            a.panel.zurueckuebersetzen();
            assertTrue(a.panel.antwortBereich().liste().getModel().getSize() > 0, "Liste hat Zeilen");
            assertTrue(a.panel.antwortBereich().ergebnis().getText().contains(MAIL));
        });
        pruefeBaum(a);
        edt(() -> a.panel.antwortBereich().ergebnisKopieren().doClick());
        pruefeBaum(a);
        edt(() -> {
            a.zwischenablage.gesperrt = true;
            a.panel.fuerKiKopieren();
            assertFalse(a.dialoge.fehler.isEmpty());
        });
        pruefeBaum(a);
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
        });
        pruefeBaum(a);
    }

    @Test
    void auchWaehrendDesScansTraegtKeinNameEinenMarker() {
        Aufbau a = new Aufbau(TextScanner.standard()::scan, SwingAttrappen.haengend());
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText(EINGABE);
            a.panel.scannen();
        });
        pruefeBaum(a);
    }

    @Test
    void htmlTextAlsFundtextWirdNieZumTooltip() {
        String html = "<html><img src=\"http://example.org/bild.png\">";
        Aufbau a = new Aufbau();
        a.scannen(html + " " + MAIL);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.setSize(900, 400);
            tabelle.doLayout();
            assertTrue(tabelle.getRowCount() > 0);
            for (int zeile = 0; zeile < tabelle.getRowCount(); zeile++) {
                for (int spalte = 0; spalte < tabelle.getColumnCount(); spalte++) {
                    Rectangle zelle = tabelle.getCellRect(zeile, spalte, false);
                    assertNull(tabelle.getToolTipText(maus(tabelle, zelle)));
                }
            }
        });
    }
}
