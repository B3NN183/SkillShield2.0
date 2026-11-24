package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Beschriftungen;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.ScanZustand;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import org.junit.jupiter.api.Test;

/**
 * Ablauf im Hauptpanel mit synchron ausführendem Scan-Starter (AK-6, AK-8, AK-9, AK-12, AK-14, AK-15, AK-18, AK-22,
 * AK-23, AK-26, AK-31, AK-37, AK-38, AK-42, AK-43): Text ins Dokument, Scannen, Tabelle, Auswahl, Hervorheben,
 * manuelles Hinzufügen, Kopieren, Rückübersetzen, Zurücksetzen und Beenden. Alles auf dem Ereignis-Thread, ohne
 * Fenster und ohne die Zwischenablage des Systems.
 */
class HauptPanelAblaufTest {

    private static String zelle(Aufbau a, int zeile, int spalte) {
        return String.valueOf(a.panel.fundeBereich().tabelle().getModel().getValueAt(zeile, spalte));
    }

    private static String status(Aufbau a) {
        return ((JLabel) a.panel.komponente("statusMeldung")).getText();
    }

    @Test
    void scanLiefertGenauDieFundeDesKerns() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        List<Finding> erwartet = TextScanner.standard().scan(DREI_FUNDE);
        assertEquals(3, erwartet.size());
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertEquals(erwartet.size(), tabelle.getRowCount());
            for (int i = 0; i < erwartet.size(); i++) {
                assertEquals(erwartet.get(i).originalValue(), zelle(a, i, 2));
                assertEquals(Beschriftungen.stufe(erwartet.get(i).level()), zelle(a, i, 3), "Stufe als Text");
                assertEquals(Boolean.TRUE, tabelle.getModel().getValueAt(i, 0), "alle angewählt (AK-9)");
            }
            assertEquals("E-Mail", zelle(a, 0, 1));
            assertEquals(ScanZustand.GUELTIG, a.sitzung.scanZustand());
            assertEquals(a.sitzung.textFuerKi(), a.panel.kiTextBereich().text().getText());
        });
    }

    @Test
    void platzhalterSpalteStehtImTextFuerKi() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            String text = a.panel.kiTextBereich().text().getText();
            for (int i = 0; i < 3; i++) {
                assertTrue(zelle(a, i, 4).startsWith("[["), zelle(a, i, 4));
                assertTrue(text.contains(zelle(a, i, 4)));
                assertFalse(text.contains(zelle(a, i, 2)), "Originalwert nicht mehr im Text für die KI");
            }
        });
    }

    @Test
    void kontrollkaestchenWaehltAbUndAn() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            String mail = zelle(a, 0, 2);
            tabelle.getModel().setValueAt(false, 0, 0);
            assertEquals(Texte.NICHT_ERSETZT, zelle(a, 0, 4));
            assertTrue(a.panel.kiTextBereich().text().getText().contains(mail));
            assertTrue(status(a).contains("2 angewählt"), status(a));
            assertTrue(status(a).contains("1 Fund wird nicht ersetzt"), status(a));
            tabelle.getModel().setValueAt(true, 0, 0);
            assertFalse(a.panel.kiTextBereich().text().getText().contains(mail));
            assertTrue(status(a).contains("3 angewählt"), status(a));
        });
    }

    @Test
    void alleAnwaehlenStelltDieAuswahlHer() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.getModel().setValueAt(false, 1, 0);
            tabelle.getModel().setValueAt(false, 2, 0);
            a.panel.fundeBereich().alleAnwaehlen().doClick();
            for (int i = 0; i < 3; i++) {
                assertEquals(Boolean.TRUE, tabelle.getModel().getValueAt(i, 0));
            }
        });
    }

    @Test
    void auswahlBleibtNachAbwahlBestehen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.setRowSelectionInterval(2, 2);
            tabelle.getModel().setValueAt(false, 0, 0);
            assertEquals(2, tabelle.getSelectedRow(), "die gewählte Zeile bleibt gewählt");
            assertEquals(1, a.panel.eingabeBereich().eingabe().getHighlighter().getHighlights().length);
        });
    }

    @Test
    void aenderungDesEingabetextesLeertTabelleUndTextFuerKi() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            a.panel.fundeBereich().tabelle().setRowSelectionInterval(1, 1);
            try {
                eingabe.getDocument().insertString(0, "x", null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals("", a.panel.kiTextBereich().text().getText());
            assertFalse(a.panel.kiTextBereich().kopieren().isEnabled());
            assertFalse(a.panel.fundeBereich().markieren().isEnabled());
            assertEquals(Texte.NEUER_SCAN_NOETIG, status(a));
            assertEquals(0, eingabe.getHighlighter().getHighlights().length);
            assertEquals(ScanZustand.VERALTET, a.sitzung.scanZustand());
        });
    }

    @Test
    void jedeArtDerAenderungMeldetEingabeGeaendert() {
        for (int art = 0; art < 4; art++) {
            Aufbau a = new Aufbau();
            a.scannen(DREI_FUNDE);
            int welche = art;
            edt(() -> {
                TextBereich eingabe = a.panel.eingabeBereich().eingabe();
                switch (welche) {
                    case 0 -> eingabe.setText("neu");
                    case 1 -> eingabe.replaceSelection("eingefügt");
                    case 2 -> eingabe.append("angehängt");
                    default -> eingabe.setText("");
                }
                assertEquals(ScanZustand.VERALTET, a.sitzung.scanZustand(), "Art " + welche);
            });
        }
    }

    @Test
    void zuordnungBleibtNachAenderungDerEingabeBestehen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> a.panel.fuerKiKopieren());
        String zuordnung = ((JLabel) edt(() -> a.panel.komponente("statusZuordnung"))).getText();
        edt(() -> a.panel.eingabeBereich().eingabe().setText("etwas anderes"));
        assertEquals(zuordnung, ((JLabel) edt(() -> a.panel.komponente("statusZuordnung"))).getText());
        assertEquals(Texte.zuordnung(3), zuordnung);
    }

    @Test
    void leereEingabeScanntNichtUndMeldetEsOhneDialog() {
        Aufbau a = new Aufbau();
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText("  \n\t ");
            a.panel.eingabeBereich().scannen().doClick();
            assertEquals(Texte.TEXT_FEHLT, status(a));
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
            assertEquals(List.of(), a.dialoge.fehler);
            assertEquals(List.of(), a.dialoge.fragen);
        });
    }

    @Test
    void waehrendDesScansSindEingabeUndScannenGesperrt() {
        List<ScanAuftrag> offen = new ArrayList<>();
        Aufbau a = new Aufbau(TextScanner.standard()::scan, (s, auftrag) -> offen.add(auftrag));
        a.scannen(DREI_FUNDE);
        edt(() -> {
            assertEquals(ScanZustand.LAEUFT, a.sitzung.scanZustand());
            assertFalse(a.panel.eingabeBereich().eingabe().isEditable());
            assertFalse(a.panel.eingabeBereich().scannen().isEnabled());
            assertFalse(a.panel.zuruecksetzenKnopf().isEnabled());
            assertTrue(a.panel.beendenKnopf().isEnabled());
            assertEquals(Texte.SCAN_LAEUFT, status(a));
            assertEquals(Texte.SCAN_LAEUFT, ((TextBereich) a.panel.komponente("leerFunde")).getText());
        });
        edt(() -> a.sitzung.scanAbgeschlossen(offen.get(0), offen.get(0).ausfuehren()));
        edt(() -> {
            assertTrue(a.panel.eingabeBereich().eingabe().isEditable());
            assertTrue(a.panel.eingabeBereich().scannen().isEnabled());
            assertEquals(3, a.panel.fundeBereich().tabelle().getRowCount());
        });
    }

    @Test
    void zeilenwahlMarkiertDieFundstelleOhneFokusUndOhneTextaenderung() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            List<Finding> funde = TextScanner.standard().scan(DREI_FUNDE);
            a.panel.fundeBereich().tabelle().setRowSelectionInterval(1, 1);
            Highlighter.Highlight[] markierungen = eingabe.getHighlighter().getHighlights();
            assertEquals(1, markierungen.length);
            assertEquals(funde.get(1).start(), markierungen[0].getStartOffset());
            assertEquals(funde.get(1).end(), markierungen[0].getEndOffset());
            assertEquals(DREI_FUNDE, eingabe.getText());
            assertEquals(0, eingabe.fokusAnfragen(), "Hervorheben darf den Fokus nicht holen");
            a.panel.fundeBereich().tabelle().clearSelection();
            assertEquals(0, eingabe.getHighlighter().getHighlights().length);
            assertEquals(DREI_FUNDE, eingabe.getText());
        });
    }

    @Test
    void markierungVonHandErzeugtEinenFundJeVorkommen() {
        Aufbau a = new Aufbau();
        String text = "Beispielname schreibt an Beispielname";
        a.scannen(text);
        edt(() -> {
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            a.panel.eingabeBereich().eingabe().select(0, "Beispielname".length());
            assertTrue(a.panel.fundeBereich().markieren().isEnabled());
            a.panel.fundeBereich().markieren().doClick();
            assertEquals(2, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals("Manuell", zelle(a, 0, 1));
            assertEquals("[[TEXT_1]]", zelle(a, 0, 4));
            assertEquals("[[TEXT_1]]", zelle(a, 1, 4));
            assertEquals("[[TEXT_1]] schreibt an [[TEXT_1]]", a.panel.kiTextBereich().text().getText());
            assertTrue(status(a).startsWith("2 Stellen als Fund hinzugefügt."), status(a));
        });
    }

    @Test
    void leereMarkierungMeldetNurDenHinweis() {
        Aufbau a = new Aufbau();
        a.scannen("Beispielname schreibt");
        edt(() -> {
            a.panel.eingabeBereich().eingabe().select(3, 3);
            a.panel.markieren();
            assertEquals(Texte.MARKIERUNG_FEHLT, status(a));
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals(List.of(), a.dialoge.fehler);
        });
    }

    @Test
    void markierungMitWagenruecklaufPasstZumGescanntenText() {
        Aufbau a = new Aufbau();
        String text = "Zeile eins\r\nBeispielname\r\nund Beispielname\r\n";
        a.scannen(text);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            String imFeld = eingabe.getText();
            int start = imFeld.indexOf("Beispielname");
            eingabe.select(start, start + "Beispielname".length());
            a.panel.markieren();
            assertEquals(2, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals("Beispielname", zelle(a, 0, 2));
            assertEquals(imFeld.replace("Beispielname", "[[TEXT_1]]"), a.sitzung.textFuerKi());
            assertEquals(List.of(), a.dialoge.fehler);
        });
    }

    @Test
    void markierungOhneGueltigenScanTutNichts() {
        Aufbau a = new Aufbau();
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText("Beispielname");
            a.panel.eingabeBereich().eingabe().select(0, 4);
            a.panel.markieren();
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals(Texte.BEREIT, status(a));
        });
    }

    @Test
    void fuerKiKopierenLegtGenauDenTextAusBereich3AbUndRueckuebersetzenStelltDenTextHer() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            assertFalse(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
            a.panel.kiTextBereich().kopieren().doClick();
            assertEquals(List.of(a.panel.kiTextBereich().text().getText()), a.zwischenablage.gesetzt);
            assertTrue(status(a).startsWith("Text für KI kopiert (3 Stellen ersetzt)"), status(a));
            assertFalse(a.panel.antwortBereich().zurueckuebersetzen().isEnabled(), "Antwort fehlt noch");
            a.panel.antwortBereich().antwort().setText(a.zwischenablage.gesetzt.get(0));
            assertTrue(a.panel.antwortBereich().zurueckuebersetzen().isEnabled());
            a.panel.antwortBereich().zurueckuebersetzen().doClick();
            assertEquals(DREI_FUNDE, a.panel.antwortBereich().ergebnis().getText());
            assertEquals(List.of(Texte.ALLE_ZURUECK), listeninhalt(a));
            assertTrue(a.panel.antwortBereich().ergebnisKopieren().isEnabled());
            a.panel.antwortBereich().ergebnisKopieren().doClick();
            assertEquals(DREI_FUNDE, a.zwischenablage.gesetzt.get(1));
        });
    }

    private static List<String> listeninhalt(Aufbau a) {
        List<String> inhalt = new ArrayList<>();
        var liste = a.panel.antwortBereich().liste();
        for (int i = 0; i < liste.getModel().getSize(); i++) {
            inhalt.add(liste.getModel().getElementAt(i));
        }
        return inhalt;
    }

    @Test
    void nichtAufloesbarePlatzhalterErscheinenInDerListe() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText("Hallo [[EMAIL_9]] und [[Name_1]] und [[EMAIL_");
            a.panel.zurueckuebersetzen();
            List<String> zeilen = listeninhalt(a);
            assertEquals(3, zeilen.size());
            assertTrue(zeilen.get(0).startsWith("[[EMAIL_9]] an Position 7: unbekannt"), zeilen.get(0));
            assertTrue(zeilen.get(1).endsWith("unbekannt"), zeilen.get(1));
            assertTrue(zeilen.get(2).endsWith("beschädigt"), zeilen.get(2));
            assertEquals(Texte.nichtZurueckuebersetzt(3), status(a));
        });
    }

    @Test
    void zuruecksetzenLeertEingabeUndAntwortNurNachZustimmung() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText("Antwort");
            a.dialoge.antwort = false;
            a.panel.zuruecksetzenKnopf().doClick();
            assertEquals(List.of(Rueckfrage.ZURUECKSETZEN), a.dialoge.fragen);
            assertEquals(DREI_FUNDE, a.panel.eingabeBereich().eingabe().getText());
            assertEquals("Antwort", a.panel.antwortBereich().antwort().getText());
            a.dialoge.antwort = true;
            a.panel.zuruecksetzenKnopf().doClick();
            assertEquals("", a.panel.eingabeBereich().eingabe().getText());
            assertEquals("", a.panel.antwortBereich().antwort().getText());
            assertEquals(0, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals("", a.panel.kiTextBereich().text().getText());
            assertEquals("", a.panel.antwortBereich().ergebnis().getText());
            assertEquals(Texte.BEREIT, status(a));
            assertEquals(Texte.KEINE_ZUORDNUNG, ((JLabel) a.panel.komponente("statusZuordnung")).getText());
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
            assertEquals(1, a.zwischenablage.leerAufrufe, "der eigene Text wird geleert (AK-34)");
        });
    }

    @Test
    void beendenRuftNurDasUebergebeneRunnableUndIstImmerBedienbar() {
        Aufbau a = new Aufbau();
        edt(() -> a.panel.beendenKnopf().doClick());
        assertEquals(1, a.beendenAufrufe.size());
        assertEquals(List.of(), a.dialoge.fragen);
    }

    @Test
    void ausnahmenDerSitzungWerdenAlsFesterTextGemeldetNichtGeworfen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fundeBereich().tabelle().getModel().setValueAt(true, 999, 0);
            assertEquals(1, a.dialoge.fehler.size());
            String text = a.dialoge.fehler.get(0);
            assertTrue(text.startsWith(Texte.FEHLER), text);
            assertTrue(text.contains("IndexOutOfBoundsException"), text);
            assertEquals(ScanZustand.GUELTIG, a.sitzung.scanZustand());
        });
    }

    @Test
    void ausnahmeImBeendenRunnableWirdGemeldetStattGeworfen() {
        Aufbau a = new Aufbau();
        Sitzung s = new Sitzung(TextScanner.standard()::scan, a.zwischenablage, a.dialoge);
        HauptPanel panel = edt(() -> new HauptPanel(s, a.zwischenablage, a.dialoge, () -> {
            throw new IllegalStateException("Marker-Fehlertext");
        }, SwingAttrappen.synchron()));
        edt(() -> panel.beenden());
        assertEquals(1, a.dialoge.fehler.size());
        assertFalse(a.dialoge.fehler.get(0).contains("Marker"));
        assertTrue(a.dialoge.fehler.get(0).contains("IllegalStateException"));
    }

    @Test
    void ausnahmeImScannerZeigtDenFehlerdialogUndLaesstAltesStehen() {
        Aufbau a = new Aufbau(text -> {
            throw new IllegalStateException("Marker-Scanfehler");
        }, SwingAttrappen.synchron());
        a.scannen("irgendein Text");
        edt(() -> {
            assertEquals(1, a.dialoge.fehler.size());
            assertFalse(a.dialoge.fehler.get(0).contains("Marker"));
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
            assertEquals("irgendein Text", a.panel.eingabeBereich().eingabe().getText());
            assertTrue(a.panel.eingabeBereich().scannen().isEnabled());
        });
    }

    @Test
    void ausnahmeImScanStarterZeigtDenFehlerdialogUndGibtDieEingabeWiederFrei() {
        Aufbau a = new Aufbau(TextScanner.standard()::scan, (sitzung, auftrag) -> {
            throw new IllegalStateException("Marker-Starterfehler");
        });
        edt(() -> a.panel.eingabeBereich().eingabe().setText("irgendein Text"));
        edt(() -> a.panel.eingabeBereich().scannen().doClick());
        edt(() -> {
            assertEquals(1, a.dialoge.fehler.size());
            String text = a.dialoge.fehler.get(0);
            assertTrue(text.startsWith(Texte.FEHLER), text);
            assertTrue(text.contains("IllegalStateException"), text);
            assertFalse(text.contains("Marker"), text);
            assertEquals(ScanZustand.KEIN_SCAN, a.sitzung.scanZustand());
            assertTrue(a.panel.eingabeBereich().eingabe().isEditable());
            assertTrue(a.panel.eingabeBereich().scannen().isEnabled());
            assertEquals("irgendein Text", a.panel.eingabeBereich().eingabe().getText());
        });
    }

    @Test
    void ausnahmeBeimHervorhebenWirdGemeldetStattGeworfen() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setHighlighter(new DefaultHighlighter() {
                @Override
                public Object addHighlight(int von, int bis, HighlightPainter painter) {
                    throw new IllegalStateException("Marker-Hervorhebung");
                }
            });
            a.panel.fundeBereich().tabelle().setRowSelectionInterval(0, 0);
            assertEquals(1, a.dialoge.fehler.size());
            assertTrue(a.dialoge.fehler.get(0).contains("IllegalStateException"), a.dialoge.fehler.get(0));
            assertFalse(a.dialoge.fehler.get(0).contains("Marker"), a.dialoge.fehler.get(0));
        });
    }

    @Test
    void tabelleHatKeineStandardEditorenUndNurDasKontrollkaestchenIstBearbeitbar() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertNull(tabelle.getDefaultEditor(Object.class));
            assertNull(tabelle.getDefaultEditor(String.class));
            assertNull(tabelle.getDefaultEditor(Number.class));
            assertNotNull(tabelle.getDefaultEditor(Boolean.class));
            assertTrue(tabelle.isCellEditable(0, 0));
            for (int spalte = 1; spalte < tabelle.getColumnCount(); spalte++) {
                assertNull(tabelle.getDefaultEditor(tabelle.getColumnClass(spalte)), "Spalte " + spalte);
                assertFalse(tabelle.isCellEditable(0, spalte), "Spalte " + spalte);
                assertFalse(tabelle.editCellAt(0, spalte), "Spalte " + spalte);
                assertNull(tabelle.getCellEditor(), "Spalte " + spalte);
            }
        });
    }

    @Test
    void abweichenderFeldtextBeiAktiverFunktionFuegtKeineMarkierungHinzu() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            assertTrue(a.panel.fundeBereich().markieren().isEnabled());
            // Das Dokument ersetzt sich ohne Ereignis: Die Sitzung hält den Scan für gültig, der Feldtext weicht ab.
            eingabe.zuruecksetzen();
            assertEquals(ScanZustand.GUELTIG, a.sitzung.scanZustand());
            assertTrue(a.panel.fundeBereich().markieren().isEnabled());
            String vorher = status(a);
            a.panel.markieren();
            assertEquals(vorher, status(a), "ohne Aufruf der Sitzung bleibt auch die Meldung stehen");
            assertEquals(3, a.panel.fundeBereich().tabelle().getRowCount());
            assertEquals(List.of(), a.dialoge.fehler);
        });
    }
}
