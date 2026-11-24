package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Aufbau des Hauptpanels (AK-3, AK-4, AK-5, AK-13, AK-16, AK-21, AK-40): Komponenten per Name, Überschriften,
 * Dauerhinweise, Leer-Texte, Startzustand und Layout ohne Überlappung bei 1024 x 738 und bei der Mindestgröße.
 * Alles headless; es entsteht kein Fenster.
 */
class HauptPanelAufbauTest {

    private static final String[] BEREICHE = {"bereich1", "bereich2", "bereich3", "bereich4"};

    private static void layouten(Container container) {
        container.doLayout();
        for (Component kind : container.getComponents()) {
            if (kind instanceof Container unter) {
                layouten(unter);
            }
        }
    }

    private static <T extends Component> T name(Aufbau a, String name, Class<T> typ) {
        Component komponente = a.panel.komponente(name);
        assertNotNull(komponente, "Komponente fehlt: " + name);
        return typ.cast(komponente);
    }

    private static void alle(Component wurzel, Class<? extends Component> typ, List<Component> sammlung) {
        if (typ.isInstance(wurzel)) {
            sammlung.add(wurzel);
        }
        if (wurzel instanceof Container container) {
            for (Component kind : container.getComponents()) {
                alle(kind, typ, sammlung);
            }
        }
    }

    @Test
    void startzustandEntsprichtAk4() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertEquals("", a.panel.eingabeBereich().eingabe().getText());
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            assertTrue(a.panel.eingabeBereich().scannen().isEnabled());
            assertFalse(a.panel.kiTextBereich().kopieren().isEnabled());
            assertFalse(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
            assertFalse(a.panel.fundeBereich().markieren().isEnabled());
            assertFalse(a.panel.antwortBereich().ergebnisKopieren().isEnabled());
            assertTrue(a.panel.beendenKnopf().isEnabled());
            assertEquals(Texte.BEREIT, name(a, "statusMeldung", JLabel.class).getText());
            assertEquals(Texte.KEINE_ZUORDNUNG, name(a, "statusZuordnung", JLabel.class).getText());
        });
    }

    @Test
    void ueberschriftenUndHinweiseHabenDenWortlaut() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertEquals(Texte.BEREICH_1, name(a, "ueberschrift1", JLabel.class).getText());
            assertEquals(Texte.BEREICH_2, name(a, "ueberschrift2", JLabel.class).getText());
            assertEquals(Texte.BEREICH_3, name(a, "ueberschrift3", JLabel.class).getText());
            assertEquals(Texte.BEREICH_4, name(a, "ueberschrift4", JLabel.class).getText());
            assertSame(a.panel.eingabeBereich().eingabe(), name(a, "ueberschrift1", JLabel.class).getLabelFor());
            assertSame(a.panel.fundeBereich().tabelle(), name(a, "ueberschrift2", JLabel.class).getLabelFor());
            assertSame(a.panel.kiTextBereich().text(), name(a, "ueberschrift3", JLabel.class).getLabelFor());
            assertSame(a.panel.antwortBereich().antwort(), name(a, "ueberschrift4", JLabel.class).getLabelFor());
            assertEquals(Texte.HINWEIS_ERKENNUNG, name(a, "hinweisErkennung", TextBereich.class).getText());
            assertEquals(Texte.HINWEIS_ZUORDNUNG, name(a, "hinweisZuordnung", TextBereich.class).getText());
        });
    }

    @Test
    void spaltenkoepfeInDerReihenfolgeVonAk13() {
        Aufbau a = new Aufbau();
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            List<String> koepfe = new ArrayList<>();
            for (int i = 0; i < tabelle.getColumnCount(); i++) {
                koepfe.add(tabelle.getColumnName(i));
            }
            assertEquals(List.of("Übernehmen", "Typ", "Fundtext", "Stufe", "Platzhalter"), koepfe);
            assertEquals(Boolean.class, tabelle.getColumnClass(0));
            assertNotNull(tabelle.getTableHeader());
            assertFalse(tabelle.getTableHeader().getReorderingAllowed());
            assertEquals(null, tabelle.getRowSorter());
        });
    }

    @Test
    void leerTexteFolgenDemZustand() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich leerFunde = name(a, "leerFunde", TextBereich.class);
            assertEquals(Texte.LEER_VOR_SCAN, leerFunde.getText());
            assertTrue(leerFunde.isVisible());
            assertTrue(name(a, "leerFuerKi", TextBereich.class).isVisible());
            assertEquals(Texte.LEER_BEREICH_3, name(a, "leerFuerKi", TextBereich.class).getText());
            assertEquals(Texte.LEER_BEREICH_4, name(a, "leerErgebnis", TextBereich.class).getText());
            assertTrue(name(a, "leerErgebnis", TextBereich.class).isVisible());
        });
        a.scannen("Text ohne erkennbare Stelle, nur Worte.");
        edt(() -> {
            assertEquals(Texte.LEER_NACH_SCAN, name(a, "leerFunde", TextBereich.class).getText());
            assertTrue(name(a, "leerFunde", TextBereich.class).isVisible());
            assertFalse(name(a, "scrollFunde", Component.class).isVisible());
            // Auch ohne Fund ist "Für KI kopieren" bedienbar (AK-30); Bereich 3 hat dann den unveränderten Text.
            assertTrue(a.panel.kiTextBereich().kopieren().isEnabled());
        });
        edt(() -> a.panel.eingabeBereich().eingabe().setText("anders"));
        edt(() -> assertEquals(Texte.NEUER_SCAN_NOETIG, name(a, "leerFunde", TextBereich.class).getText()));
    }

    @Test
    void leerTextWechseltZurTabelleSobaldFundeDa() {
        Aufbau a = new Aufbau();
        a.scannen(SwingAttrappen.DREI_FUNDE);
        edt(() -> {
            assertTrue(name(a, "scrollFunde", Component.class).isVisible());
            assertFalse(name(a, "leerFunde", TextBereich.class).isVisible());
            assertTrue(name(a, "scrollFuerKi", Component.class).isVisible());
            assertFalse(name(a, "leerFuerKi", TextBereich.class).isVisible());
        });
    }

    @Test
    void esGibtKeineSchaltflaecheAlleAbwaehlenOderEinFilter() {
        Aufbau a = new Aufbau();
        List<Component> knoepfe = new ArrayList<>();
        alle(a.panel, JButton.class, knoepfe);
        List<String> texte = new ArrayList<>();
        for (Component knopf : knoepfe) {
            String beschriftung = ((JButton) knopf).getText();
            if (!beschriftung.isEmpty()) {
                texte.add(beschriftung);
            }
        }
        assertEquals(List.of(Texte.SCANNEN, Texte.ALLE_ANWAEHLEN, Texte.MARKIERUNG_HINZUFUEGEN,
                Texte.FUER_KI_KOPIEREN, Texte.ZURUECKUEBERSETZEN, Texte.ERGEBNIS_KOPIEREN_SCHALTFLAECHE,
                Texte.ZURUECKSETZEN, Texte.BEENDEN), texte);
        for (String text : texte) {
            assertFalse(text.toLowerCase(Locale.ROOT).contains("abw"), text);
        }
    }

    @Test
    void layoutBei1024Mal738OhneUeberlappung() {
        pruefeLayout(1024, 738);
    }

    @Test
    void layoutBeiMindestgroesseOhneUeberlappung() {
        pruefeLayout(HauptPanel.MINDESTGROESSE.width, HauptPanel.MINDESTGROESSE.height);
    }

    private void pruefeLayout(int breite, int hoehe) {
        Aufbau a = new Aufbau();
        edt(() -> {
            a.panel.setSize(breite, hoehe);
            layouten(a.panel);
            Rectangle flaeche = new Rectangle(0, 0, breite, hoehe);
            List<Rectangle> teile = new ArrayList<>();
            List<String> namen = new ArrayList<>(List.of(BEREICHE));
            namen.add("fussleiste");
            namen.add("statusleiste");
            for (String teil : namen) {
                Component komponente = name(a, teil, Component.class);
                assertTrue(komponente.isVisible(), teil);
                Rectangle grenzen = SwingUtilities.convertRectangle(komponente.getParent(),
                        komponente.getBounds(), a.panel);
                assertTrue(grenzen.width > 0 && grenzen.height > 0, teil + " hat keine Fläche");
                assertTrue(flaeche.contains(grenzen), teil + " liegt nicht im Panel: " + grenzen);
                for (Rectangle anderer : teile) {
                    assertFalse(grenzen.intersects(anderer), teil + " überlappt: " + grenzen);
                }
                teile.add(grenzen);
            }
            for (String bereich : BEREICHE) {
                pruefeKinder((Container) name(a, bereich, Container.class));
            }
        });
    }

    /** Die sichtbaren direkten Kinder eines Bereichs liegen darin und überlappen einander nicht. */
    private static void pruefeKinder(Container bereich) {
        Rectangle innen = new Rectangle(0, 0, bereich.getWidth(), bereich.getHeight());
        List<Rectangle> gesehen = new ArrayList<>();
        for (Component kind : bereich.getComponents()) {
            if (!kind.isVisible()) {
                continue;
            }
            Rectangle grenzen = kind.getBounds();
            assertTrue(innen.contains(grenzen), bereich.getName() + ": " + kind.getName() + " ragt heraus: "
                    + grenzen + " in " + innen);
            for (Rectangle anderer : gesehen) {
                assertFalse(grenzen.intersects(anderer), bereich.getName() + ": " + kind.getName() + " überlappt");
            }
            gesehen.add(grenzen);
        }
    }

    @Test
    void mindestgroesseIstFestgelegt() {
        assertTrue(HauptPanel.MINDESTGROESSE.width <= 1024 && HauptPanel.MINDESTGROESSE.height <= 738);
        Aufbau a = new Aufbau();
        assertEquals(HauptPanel.MINDESTGROESSE, edt(a.panel::getMinimumSize));
    }

    @Test
    void keinHtmlFaehigesTextfeldImBaum() {
        Aufbau a = new Aufbau();
        List<Component> editoren = new ArrayList<>();
        alle(a.panel, JEditorPane.class, editoren);
        assertEquals(List.of(), editoren);
    }
}
