package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.ScanZustand;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;

/**
 * Speicherreste nach dem Zurücksetzen und Beenden (Sicherheitsvorgabe 6): Eingabe, KI-Antwort, Text für die KI und
 * Ergebnis bekommen ein neues Dokument (ein bloßes Leeren ließe den Text im Zeichenpuffer des alten stehen); die
 * alten Dokumente sind danach nicht mehr erreichbar, und alles andere (Hörer, Umbruch, Übertragung, Caret) wirkt am
 * neuen Dokument weiter.
 */
class FelderVerwerfenTest {

    private static final String MARKER = "Beispielname";

    private static Aufbau mitAllenFeldern() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE + " " + MARKER);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText(a.sitzung.textFuerKi() + " " + MARKER);
            a.panel.zurueckuebersetzen();
        });
        return a;
    }

    private static List<TextBereich> felder(Aufbau a) {
        return List.of(a.panel.eingabeBereich().eingabe(), a.panel.antwortBereich().antwort(),
                a.panel.antwortBereich().ergebnis(), a.panel.kiTextBereich().text());
    }

    @Test
    void zuruecksetzenGibtAlleAltenDokumenteFrei() throws InterruptedException {
        Aufbau a = mitAllenFeldern();
        List<WeakReference<Document>> alt = edt(() -> felder(a).stream()
                .map(feld -> new WeakReference<>(feld.getDocument())).toList());
        edt(() -> {
            for (TextBereich feld : felder(a)) {
                assertTrue(feld.getDocument().getLength() > 0, feld.getName());
            }
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            for (TextBereich feld : felder(a)) {
                assertEquals(0, feld.getDocument().getLength(), feld.getName());
            }
        });
        for (int i = 0; i < 100 && alt.stream().anyMatch(r -> r.get() != null); i++) {
            System.gc();
            Thread.sleep(20);
        }
        for (int i = 0; i < alt.size(); i++) {
            assertNull(alt.get(i).get(), "altes Dokument von Feld " + i + " ist noch erreichbar");
        }
    }

    @Test
    void felderVerwerfenGibtEingabeUndAntwortFreiUndLeertDenGescanntenText() throws Exception {
        Aufbau a = mitAllenFeldern();
        List<WeakReference<Document>> alt = edt(() -> List.of(
                new WeakReference<>(a.panel.eingabeBereich().eingabe().getDocument()),
                new WeakReference<>(a.panel.antwortBereich().antwort().getDocument())));
        Field gescannt = HauptPanel.class.getDeclaredField("gescannterText");
        gescannt.setAccessible(true);
        edt(() -> {
            try {
                assertFalse(((String) gescannt.get(a.panel)).isEmpty());
                a.panel.felderVerwerfen();
                assertEquals("", gescannt.get(a.panel));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        });
        for (int i = 0; i < 100 && alt.stream().anyMatch(r -> r.get() != null); i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(alt.get(0).get());
        assertNull(alt.get(1).get());
    }

    @Test
    void zuruecksetzenTauschtDieDokumenteUndLaesstDenRestUnveraendert() {
        Aufbau a = mitAllenFeldern();
        edt(() -> {
            for (TextBereich feld : felder(a)) {
                Document alt = feld.getDocument();
                TransferHandler handler = feld.getTransferHandler();
                Caret caret = feld.getCaret();
                Object kopieren = feld.getActionMap().get("copy");
                feld.zuruecksetzen();
                assertNotSame(alt, feld.getDocument(), feld.getName());
                assertTrue(feld.getDocument() instanceof PlainDocument, feld.getName());
                assertEquals(0, feld.getDocument().getLength(), feld.getName());
                assertSame(handler, feld.getTransferHandler(), feld.getName());
                assertSame(caret, feld.getCaret(), feld.getName());
                assertSame(kopieren, feld.getActionMap().get("copy"), feld.getName());
                assertTrue(feld.getLineWrap(), feld.getName());
                assertEquals(0, feld.getHighlighter().getHighlights().length, feld.getName());
                assertEquals(0, feld.getCaretPosition(), feld.getName());
            }
        });
    }

    @Test
    void dieHoererReagierenNachDemZuruecksetzenAmNeuenDokument() {
        Aufbau a = mitAllenFeldern();
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
        });
        a.scannen(DREI_FUNDE);
        edt(() -> {
            assertEquals(ScanZustand.GUELTIG, a.sitzung.scanZustand());
            a.panel.eingabeBereich().eingabe().append("x");
            assertEquals(ScanZustand.VERALTET, a.sitzung.scanZustand(), "Eingabe-Hörer am neuen Dokument");
        });
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            TextBereich antwort = a.panel.antwortBereich().antwort();
            assertFalse(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
            antwort.setText("Antwort");
            assertTrue(a.panel.antwortBereich().zurueckuebersetzen().isEnabled(), "Antwort-Hörer am neuen Dokument");
            antwort.setText("");
            assertFalse(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
        });
    }

    /** Ein Fundtext der Tabelle: der Originalwert der E-Mail-Adresse in {@link SwingAttrappen#DREI_FUNDE}. */
    private static final String FUNDTEXT = "mustermann@example.org";

    /** Zeichnet jede Zelle der Tabelle über den Renderer und liefert die Renderer-Labels mit ihrem Text. */
    private static List<JLabel> tabelleZeichnen(Aufbau a) {
        JTable tabelle = a.panel.fundeBereich().tabelle();
        for (int zeile = 0; zeile < tabelle.getRowCount(); zeile++) {
            for (int spalte = 0; spalte < tabelle.getColumnCount(); spalte++) {
                tabelle.prepareRenderer(tabelle.getCellRenderer(zeile, spalte), zeile, spalte);
            }
        }
        return List.of((JLabel) tabelle.getDefaultRenderer(String.class),
                (JLabel) tabelle.getDefaultRenderer(Object.class));
    }

    private static JLabel listeZeichnen(Aufbau a, String text) {
        JList<String> liste = a.panel.antwortBereich().liste();
        return (JLabel) liste.getCellRenderer().getListCellRendererComponent(liste, text, 0, false, false);
    }

    @Test
    void zuruecksetzenLaesstKeinenFundtextImRendererDerTabelleStehen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            List<JLabel> renderer = tabelleZeichnen(a);
            assertTrue(renderer.get(0).getText().length() > 0, "Probe: der Renderer hat etwas gezeichnet");
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            for (JLabel label : renderer) {
                assertFalse(label.getText().contains(FUNDTEXT));
                assertEquals("", label.getText());
            }
        });
    }

    @Test
    void einNeuerScanOhneFundeLeertDenRendererDerTabelle() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            List<JLabel> renderer = tabelleZeichnen(a);
            assertTrue(renderer.stream().anyMatch(l -> !l.getText().isEmpty()));
        });
        a.scannen("Text ohne Fund");
        edt(() -> {
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            for (JLabel label : List.of((JLabel) a.panel.fundeBereich().tabelle().getDefaultRenderer(String.class),
                    (JLabel) a.panel.fundeBereich().tabelle().getDefaultRenderer(Object.class))) {
                assertEquals("", label.getText());
            }
        });
    }

    @Test
    void felderVerwerfenLeertDieRendererVonTabelleUndListe() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            List<JLabel> renderer = tabelleZeichnen(a);
            JLabel listenZeile = listeZeichnen(a, "[[A_1]] " + MARKER);
            assertTrue(listenZeile.getText().contains(MARKER), "Probe: der Listenrenderer trägt den Text");
            assertTrue(renderer.stream().anyMatch(l -> !l.getText().isEmpty()));
            a.panel.felderVerwerfen();
            assertEquals("", listenZeile.getText());
            for (JLabel label : renderer) {
                assertEquals("", label.getText());
            }
        });
    }

    @Test
    void zuruecksetzenLeertDenRendererDerListe() {
        Aufbau a = mitAllenFeldern();
        edt(() -> {
            JLabel listenZeile = listeZeichnen(a, "[[A_1]] " + MARKER);
            assertTrue(listenZeile.getText().contains(MARKER));
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
            assertEquals("", listenZeile.getText());
            assertEquals(0, a.panel.antwortBereich().liste().getModel().getSize());
        });
    }

    @Test
    void zuruecksetzenSchaltetDenUmbruchWiederEin() {
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            feld.textSetzen("a".repeat(TextBereich.UMBRUCH_GRENZE + 1));
            assertFalse(feld.getLineWrap());
            feld.zuruecksetzen();
            assertTrue(feld.getLineWrap());
        });
    }
}
