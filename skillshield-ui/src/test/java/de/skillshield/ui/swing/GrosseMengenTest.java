package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.text.BadLocationException;
import org.junit.jupiter.api.Test;

/**
 * Große Datenmengen (Sicherheitsvorgabe 6): bis zu eine Million Tabellenzeilen (Einzelzeichen-Markierung) und bis
 * zu rund 125 000 nicht auflösbare Platzhalter, dazu die Zeilenumbruch-Grenze der Textfelder. Das Tabellenmodell
 * liest direkt aus der Sitzung; die Liste "Nicht zurückübersetzt" zeigt höchstens {@link Texte#MAX_LISTENZEILEN}
 * Zeilen mit Zählhinweis. Die Zeitgrenzen sind großzügig bemessen; sie sollen nur ein Aufblähen auf quadratische
 * Laufzeit oder Objekt-je-Zeile-Modelle erkennen.
 *
 * <p>Bewusst {@code assertTimeout} und nicht {@code assertTimeoutPreemptively}: Die Arbeit läuft auf dem
 * Ereignis-Thread (über {@code invokeAndWait}); ein Abbruch des wartenden Test-Threads würde sie nicht beenden und
 * ließe den Ereignis-Thread für die folgenden Tests blockiert zurück. Bei einem Hänger hilft nur das Zeitlimit des
 * Maven-Laufs; der Fehler wird sonst nach Ablauf der Arbeit gemeldet.
 */
class GrosseMengenTest {

    private static final int ZEILEN = 1_000_000;
    private static final Duration GRENZE = Duration.ofSeconds(180);

    @Test
    void eineMillionZeilenInDerTabelle() {
        String text = "a".repeat(ZEILEN);
        List<Finding> funde = IntStream.range(0, ZEILEN)
                .mapToObj(i -> Finding.manual(FindingType.TEXT, "a", i, i + 1)).toList();
        Aufbau a = new Aufbau(t -> funde, SwingAttrappen.synchron());
        assertTimeout(GRENZE, () -> {
            edt(() -> a.panel.eingabeBereich().eingabe().textSetzen(text));
            edt(() -> a.panel.scannen());
        });
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            assertEquals(ZEILEN, tabelle.getRowCount());
            assertEquals("[[TEXT_1]]", tabelle.getValueAt(ZEILEN - 1, 4));
            assertEquals("Manuell", tabelle.getValueAt(0, 1));
            tabelle.setRowSelectionInterval(5, 5);
        });
        assertTimeout(GRENZE, () -> edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            tabelle.getModel().setValueAt(false, 0, 0);
            assertEquals(Texte.NICHT_ERSETZT, tabelle.getValueAt(0, 4));
            assertEquals(5, tabelle.getSelectedRow(), "die Auswahl bleibt bei geänderter Zeile");
            a.panel.fundeBereich().alleAnwaehlen().doClick();
            assertEquals(Boolean.TRUE, tabelle.getValueAt(0, 0));
        }));
    }

    @Test
    void ueberHunderttausendNichtAufloesbarePlatzhalterWerdenGekuerztGezeigt() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        int anzahl = 125_000;
        String antwort = "[[A_1]] ".repeat(anzahl);
        assertEquals(Sitzung.MAX_ZEICHEN, antwort.length());
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().textSetzen(antwort);
        });
        assertTimeout(GRENZE, () -> edt(() -> a.panel.zurueckuebersetzen()));
        edt(() -> {
            JList<String> liste = a.panel.antwortBereich().liste();
            assertEquals(Texte.MAX_LISTENZEILEN + 1, liste.getModel().getSize());
            assertEquals(Texte.undWeitere(anzahl - Texte.MAX_LISTENZEILEN),
                    liste.getModel().getElementAt(Texte.MAX_LISTENZEILEN));
            assertEquals("[[A_1]] an Position 1: unbekannt", liste.getModel().getElementAt(0));
            assertEquals(Texte.nichtZurueckuebersetzt(anzahl),
                    ((JLabel) a.panel.komponente("statusMeldung")).getText());
        });
    }

    @Test
    void sehrLangeZeilenWerdenNichtUmbrochen() {
        assertTrue(TextBereich.umbruchMoeglich("kurz\nzeilen\r\nund mehr"));
        assertTrue(TextBereich.umbruchMoeglich("a".repeat(TextBereich.UMBRUCH_GRENZE)));
        assertTrue(TextBereich.umbruchMoeglich(("a".repeat(TextBereich.UMBRUCH_GRENZE) + "\n").repeat(3)));
        assertFalse(TextBereich.umbruchMoeglich("a".repeat(TextBereich.UMBRUCH_GRENZE + 1)));
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            feld.textSetzen("a".repeat(TextBereich.UMBRUCH_GRENZE + 1));
            assertFalse(feld.getLineWrap());
            feld.textSetzen("kurz");
            assertTrue(feld.getLineWrap());
            feld.einfuegen("b".repeat(TextBereich.UMBRUCH_GRENZE + 1));
            assertFalse(feld.getLineWrap());
        });
    }

    @Test
    void mehrfachesEinfuegenInDieselbeZeileSchaltetDenUmbruchBeiUeberschreitenAus() {
        assertTimeout(GRENZE, () -> edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            int stueck = 10_000;
            for (int i = 1; i <= 5; i++) {
                feld.einfuegen("a".repeat(stueck));
                assertEquals(i * stueck <= TextBereich.UMBRUCH_GRENZE, feld.getLineWrap(), "nach " + i + " Stücken");
            }
            feld.einfuegen("a".repeat(stueck));
            assertFalse(feld.getLineWrap());
            assertEquals(6 * stueck, feld.getDocument().getLength());
        }));
    }

    @Test
    void vieleKleineEinfuegungenInEineZeileBleibenSchnell() {
        assertTimeout(GRENZE, () -> edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            for (int i = 0; i < 2_000; i++) {
                feld.einfuegen("a".repeat(100));
            }
            assertEquals(200_000, feld.getDocument().getLength());
            assertFalse(feld.getLineWrap());
        }));
    }

    @Test
    void einZeichenAnEineZeileKnappUnterDerGrenzeSchaltetDenUmbruchAus() {
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            feld.textSetzen("a".repeat(TextBereich.UMBRUCH_GRENZE - 1));
            assertTrue(feld.getLineWrap());
            tippen(feld, feld.getDocument().getLength(), "b");
            assertTrue(feld.getLineWrap(), "genau an der Grenze wird noch umbrochen");
            tippen(feld, feld.getDocument().getLength(), "c");
            assertFalse(feld.getLineWrap());
        });
    }

    @Test
    void zeilenMitZeilenvorschubWerdenEinzelnGemessen() {
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            String zeile = "a".repeat(TextBereich.UMBRUCH_GRENZE - 1) + "\n";
            feld.textSetzen(zeile.repeat(3));
            assertTrue(feld.getLineWrap(), "mehrere Zeilen unter der Grenze, zusammen darüber");
            tippen(feld, TextBereich.UMBRUCH_GRENZE - 1, "bc");
            assertFalse(feld.getLineWrap(), "die erste Zeile ist jetzt zu lang");
        });
    }

    @Test
    void nachDemLoeschenSchaltetDerUmbruchWiederEinWennDerGanzeTextKurzGenugIst() {
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            feld.textSetzen("a".repeat(60_000));
            assertFalse(feld.getLineWrap());
            loeschen(feld, 0, 5_000);
            assertFalse(feld.getLineWrap(), "55 000 Zeichen: noch über der Grenze");
            loeschen(feld, 0, 5_000);
            assertTrue(feld.getLineWrap(), "50 000 Zeichen: keine Zeile kann zu lang sein");
        });
    }

    /** Zwei Zeilen, die je unter der Grenze liegen, zusammen aber darüber; der Umbruch ist noch ein. */
    private static TextBereich zweiZeilen() {
        TextBereich feld = new TextBereich("probe", 3);
        int laenge = TextBereich.UMBRUCH_GRENZE - 10_000;
        feld.textSetzen("a".repeat(laenge) + "\n" + "b".repeat(laenge));
        assertTrue(feld.getLineWrap());
        return feld;
    }

    private static void aktion(TextBereich feld, String name) {
        feld.getActionMap().get(name).actionPerformed(
                new java.awt.event.ActionEvent(feld, java.awt.event.ActionEvent.ACTION_PERFORMED, name));
    }

    @Test
    void dasLoeschenEinesZeilenumbruchsPerEntfSchaltetDenUmbruchAus() {
        edt(() -> {
            TextBereich feld = zweiZeilen();
            feld.setCaretPosition(TextBereich.UMBRUCH_GRENZE - 10_000);
            aktion(feld, "delete-next");
            assertFalse(feld.getLineWrap());
            assertEquals(2 * (TextBereich.UMBRUCH_GRENZE - 10_000), feld.getDocument().getLength());
        });
    }

    @Test
    void dasLoeschenEinesZeilenumbruchsPerRueckschrittSchaltetDenUmbruchAus() {
        edt(() -> {
            TextBereich feld = zweiZeilen();
            feld.setCaretPosition(TextBereich.UMBRUCH_GRENZE - 10_000 + 1);
            aktion(feld, "delete-previous");
            assertFalse(feld.getLineWrap());
        });
    }

    @Test
    void dasLoeschenEinesZeilenumbruchsPerReplaceSelectionSchaltetDenUmbruchAus() {
        edt(() -> {
            TextBereich feld = zweiZeilen();
            feld.select(TextBereich.UMBRUCH_GRENZE - 10_000, TextBereich.UMBRUCH_GRENZE - 10_000 + 1);
            feld.replaceSelection("");
            assertFalse(feld.getLineWrap());
        });
    }

    @Test
    void dasLoeschenEinesZeilenumbruchsPerDocumentReplaceSchaltetDenUmbruchAus() throws BadLocationException {
        edt(() -> {
            TextBereich feld = zweiZeilen();
            try {
                ((javax.swing.text.AbstractDocument) feld.getDocument())
                        .replace(TextBereich.UMBRUCH_GRENZE - 10_000, 1, "", null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
            assertFalse(feld.getLineWrap());
        });
    }

    @Test
    void dasVerschmelzenZweierKurzerZeilenLaesstDenUmbruchEin() {
        edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            feld.textSetzen("a".repeat(100) + "\n" + "b".repeat(100));
            loeschen(feld, 100, 1);
            assertTrue(feld.getLineWrap());
        });
    }

    @Test
    void dasVerschmelzenInDerMitteVielerZeilenPruftNurDieBetroffeneZeile() {
        assertTimeout(GRENZE, () -> edt(() -> {
            TextBereich feld = new TextBereich("probe", 3);
            int laenge = TextBereich.UMBRUCH_GRENZE - 1;
            feld.textSetzen((("a".repeat(laenge)) + "\n").repeat(5) + "b".repeat(100));
            assertTrue(feld.getLineWrap());
            loeschen(feld, laenge, 1);
            assertFalse(feld.getLineWrap());
        }));
    }

    private static void tippen(TextBereich feld, int stelle, String text) {
        try {
            feld.getDocument().insertString(stelle, text, null);
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    private static void loeschen(TextBereich feld, int stelle, int laenge) {
        try {
            feld.getDocument().remove(stelle, laenge);
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void kuerzenZaehltDenRestUndLaesstKurzeListenUnveraendert() {
        List<String> kurz = List.of("a", "b");
        assertEquals(kurz, List.of(AntwortBereich.gekuerzt(kurz)));
        List<String> genau = IntStream.range(0, Texte.MAX_LISTENZEILEN).mapToObj(String::valueOf).toList();
        assertEquals(genau.size(), AntwortBereich.gekuerzt(genau).length);
        List<String> eineMehr = IntStream.rangeClosed(0, Texte.MAX_LISTENZEILEN).mapToObj(String::valueOf).toList();
        String[] gekuerzt = AntwortBereich.gekuerzt(eineMehr);
        assertEquals(Texte.MAX_LISTENZEILEN + 1, gekuerzt.length);
        assertEquals("… und 1 weitere", gekuerzt[Texte.MAX_LISTENZEILEN]);
        assertTrue(Texte.undWeitere(124_500).startsWith("… und 124"));
    }
}
