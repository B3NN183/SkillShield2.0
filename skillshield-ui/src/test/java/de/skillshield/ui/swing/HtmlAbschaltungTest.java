package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JTable;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.Test;

/**
 * HTML ist abgeschaltet (Sicherheitsvorgabe 3): Swing deutet Text, der mit {@code <html>} beginnt, in Beschriftungen,
 * Tabellenzellen und Listen als HTML, und {@code <img src=...>} löste einen Netzzugriff aus. Geprüft wird mit
 * Eingabe, Fundwert und KI-Antwort der Form {@code <html><img src=...>} (Adresse nur example.org): Die
 * Renderer-Komponenten haben {@code html.disable}, und es entsteht keine HTML-View.
 */
class HtmlAbschaltungTest {

    private static final String HTML = "<html><img src=\"http://example.org/bild.png\">";

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

    private static void keineHtmlView(JComponent komponente) {
        assertEquals(Boolean.TRUE, komponente.getClientProperty("html.disable"), komponente.getName());
        assertNull(komponente.getClientProperty(BasicHTML.propertyKey), komponente.getName());
    }

    @Test
    void fundtextUndPlatzhalterZellenOhneHtml() {
        Aufbau a = new Aufbau();
        String eingabe = HTML + " Ende";
        a.scannen(eingabe);
        edt(() -> {
            a.panel.eingabeBereich().eingabe().select(0, HTML.length());
            a.panel.markieren();
            JTable tabelle = a.panel.fundeBereich().tabelle();
            int zeile = -1;
            for (int i = 0; i < tabelle.getRowCount(); i++) {
                if (String.valueOf(tabelle.getValueAt(i, 2)).startsWith("<html>")) {
                    zeile = i;
                }
            }
            assertTrue(zeile >= 0, "ein Fundtext beginnt mit <html>");
            for (int spalte = 0; spalte < tabelle.getColumnCount(); spalte++) {
                Component zelle = tabelle.prepareRenderer(tabelle.getCellRenderer(zeile, spalte), zeile, spalte);
                if (spalte > 0) {
                    keineHtmlView((JComponent) zelle);
                }
            }
            JLabel text = (JLabel) tabelle.prepareRenderer(tabelle.getCellRenderer(zeile, 2), zeile, 2);
            assertTrue(text.getText().startsWith("<html>"), "der Text wird als Text gezeigt");
        });
    }

    @Test
    void listenzeilenOhneHtmlAuchBeiHtmlText() {
        Aufbau a = new Aufbau();
        edt(() -> {
            JList<String> liste = a.panel.antwortBereich().liste();
            liste.setListData(new String[] {HTML + "</html>", "[[X_1]] an Position 1: unbekannt"});
            for (int i = 0; i < 2; i++) {
                Component zelle = liste.getCellRenderer()
                        .getListCellRendererComponent(liste, liste.getModel().getElementAt(i), i, false, false);
                keineHtmlView((JComponent) zelle);
            }
        });
    }

    @Test
    void kiAntwortMitHtmlLandetNurAlsTextImErgebnis() {
        Aufbau a = new Aufbau();
        a.scannen("Mail mustermann@example.org");
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText(HTML + " [[EMAIL_1]]");
            a.panel.zurueckuebersetzen();
            assertEquals(HTML + " mustermann@example.org", a.panel.antwortBereich().ergebnis().getText());
            JList<String> liste = a.panel.antwortBereich().liste();
            for (int i = 0; i < liste.getModel().getSize(); i++) {
                keineHtmlView((JComponent) liste.getCellRenderer().getListCellRendererComponent(
                        liste, liste.getModel().getElementAt(i), i, false, false));
            }
        });
    }

    @Test
    void jedeEtikettUndSchaltflaecheHatHtmlAbgeschaltetUndKeinEditorPaneIstDa() {
        Aufbau a = new Aufbau();
        List<JComponent> alle = new ArrayList<>();
        edt(() -> sammeln(a.panel, alle));
        int geprueft = 0;
        for (JComponent c : alle) {
            if ((c instanceof JLabel || c instanceof JButton || c instanceof JList)
                    && !(c.getParent() instanceof JScrollBar)) {
                keineHtmlView(c);
                geprueft++;
            }
            assertFalse(c instanceof JEditorPane, c.getName());
        }
        assertTrue(geprueft >= 12, "zu wenige Komponenten geprüft: " + geprueft);
    }

    @Test
    void renderMitHtmlEingabeInDerStatusleisteBleibtText() {
        // Die Statusleiste zeigt nur feste Texte; selbst ein erzwungener HTML-Text würde nicht gedeutet.
        Aufbau a = new Aufbau();
        edt(() -> {
            JLabel meldung = (JLabel) a.panel.komponente("statusMeldung");
            meldung.setText(HTML);
            assertNull(meldung.getClientProperty(BasicHTML.propertyKey));
        });
    }

    @Test
    void dialogtexteSindFesteTexteOhneHtml() {
        for (Rueckfrage frage : Rueckfrage.values()) {
            assertFalse(frage.text().contains("<"), frage.name());
        }
    }
}
