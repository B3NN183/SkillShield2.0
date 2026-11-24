package de.skillshield.ui.swing;

import static de.skillshield.ui.swing.SwingAttrappen.DREI_FUNDE;
import static de.skillshield.ui.swing.SwingAttrappen.alleKomponenten;
import static de.skillshield.ui.swing.SwingAttrappen.edt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.swing.SwingAttrappen.Aufbau;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Tab-Reihenfolge (AK-46, T-7): Die feste Liste folgt dem Ablauf; die Richtlinie des Panels liefert über
 * {@code getComponentAfter/Before/First/Last} die Folge der jeweils bedienbaren Komponenten, überspringt gesperrte
 * und ausgeblendete und läuft am Ende um. Das Panel selbst ist die Fokuswurzel, deshalb braucht der Test kein
 * Fenster. Nur am sichtbaren Fenster prüfbar (nicht hier): dass der Fokus jederzeit sichtbar ist (S-8).
 */
class FokusreihenfolgeTest {

    private static final List<String> VOLLSTAENDIG = List.of("eingabe", "scannen", "funde", "alleAnwaehlen",
            "markieren", "textFuerKi", "fuerKiKopieren", "antwort", "zurueckuebersetzen", "ergebnis",
            "ergebnisKopieren", "nichtZurueckuebersetzt", "zuruecksetzen", "beenden");

    private static Fokusreihenfolge politik(Aufbau a) {
        return (Fokusreihenfolge) a.panel.getFocusTraversalPolicy();
    }

    /** Die Namen, die {@code getComponentAfter} nacheinander liefert, ab {@code getFirstComponent}. */
    private static List<String> vorwaerts(Aufbau a) {
        Fokusreihenfolge politik = politik(a);
        Component erste = politik.getFirstComponent(a.panel);
        List<String> namen = new ArrayList<>();
        Component c = erste;
        for (int i = 0; i < 40 && c != null; i++) {
            namen.add(c.getName());
            c = politik.getComponentAfter(a.panel, c);
            if (c == erste) {
                break;
            }
        }
        return namen;
    }

    private static List<String> rueckwaerts(Aufbau a) {
        Fokusreihenfolge politik = politik(a);
        Component letzte = politik.getLastComponent(a.panel);
        List<String> namen = new ArrayList<>();
        Component c = letzte;
        for (int i = 0; i < 40 && c != null; i++) {
            namen.add(c.getName());
            c = politik.getComponentBefore(a.panel, c);
            if (c == letzte) {
                break;
            }
        }
        return namen;
    }

    private static void pruefeBeideRichtungen(Aufbau a, List<String> erwartet) {
        edt(() -> {
            assertEquals(erwartet, vorwaerts(a));
            List<String> umgekehrt = new ArrayList<>(erwartet);
            Collections.reverse(umgekehrt);
            assertEquals(umgekehrt, rueckwaerts(a));
            Fokusreihenfolge politik = politik(a);
            assertEquals(erwartet.get(0), politik.getFirstComponent(a.panel).getName());
            assertEquals(erwartet.get(0), politik.getDefaultComponent(a.panel).getName());
            assertEquals(erwartet.get(erwartet.size() - 1), politik.getLastComponent(a.panel).getName());
            Component letzte = politik.getLastComponent(a.panel);
            assertSame(politik.getFirstComponent(a.panel), politik.getComponentAfter(a.panel, letzte),
                    "hinter dem letzten kommt wieder das erste");
            assertSame(letzte, politik.getComponentBefore(a.panel, politik.getFirstComponent(a.panel)));
        });
    }

    @Test
    void dasPanelIstFokuswurzelMitDerFestenRichtlinie() {
        Aufbau a = new Aufbau();
        edt(() -> {
            assertTrue(a.panel.isFocusCycleRoot());
            assertTrue(a.panel.getFocusTraversalPolicy() instanceof Fokusreihenfolge);
            List<String> namen = politik(a).reihenfolge().stream().map(Component::getName).toList();
            assertEquals(VOLLSTAENDIG, namen);
        });
    }

    @Test
    void dieFesteListeFolgtDemAblaufNachAk46() {
        Aufbau a = new Aufbau();
        edt(() -> {
            List<Component> liste = politik(a).reihenfolge();
            assertSame(a.panel.eingabeBereich().eingabe(), liste.get(0));
            assertSame(a.panel.eingabeBereich().scannen(), liste.get(1));
            assertSame(a.panel.fundeBereich().tabelle(), liste.get(2));
            assertSame(a.panel.fundeBereich().alleAnwaehlen(), liste.get(3));
            assertSame(a.panel.fundeBereich().markieren(), liste.get(4));
            assertSame(a.panel.kiTextBereich().text(), liste.get(5));
            assertSame(a.panel.kiTextBereich().kopieren(), liste.get(6));
            assertSame(a.panel.antwortBereich().antwort(), liste.get(7));
            assertSame(a.panel.antwortBereich().zurueckuebersetzen(), liste.get(8));
            assertSame(a.panel.antwortBereich().ergebnis(), liste.get(9));
            assertSame(a.panel.antwortBereich().ergebnisKopieren(), liste.get(10));
            assertSame(a.panel.antwortBereich().liste(), liste.get(11));
            assertSame(a.panel.zuruecksetzenKnopf(), liste.get(12));
            assertSame(a.panel.beendenKnopf(), liste.get(13));
        });
    }

    @Test
    void imStartzustandWerdenGesperrteUndAusgeblendeteUebersprungen() {
        Aufbau a = new Aufbau();
        pruefeBeideRichtungen(a, List.of("eingabe", "scannen", "antwort", "nichtZurueckuebersetzt", "zuruecksetzen",
                "beenden"));
    }

    @Test
    void nachDemScanKommenTabelleUndBereich3Dazu() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        pruefeBeideRichtungen(a, List.of("eingabe", "scannen", "funde", "alleAnwaehlen", "markieren", "textFuerKi",
                "fuerKiKopieren", "antwort", "nichtZurueckuebersetzt", "zuruecksetzen", "beenden"));
    }

    @Test
    void nachRueckuebersetzenSindAlleVierzehnBedienbar() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.panel.fuerKiKopieren();
            a.panel.antwortBereich().antwort().setText(a.sitzung.textFuerKi());
            a.panel.zurueckuebersetzen();
        });
        pruefeBeideRichtungen(a, VOLLSTAENDIG);
    }

    @Test
    void waehrendDesScansSindScannenUndZuruecksetzenUebersprungen() {
        Aufbau a = new Aufbau(TextScanner.standard()::scan, SwingAttrappen.haengend());
        edt(() -> {
            a.panel.eingabeBereich().eingabe().setText(DREI_FUNDE);
            a.panel.scannen();
        });
        pruefeBeideRichtungen(a, List.of("eingabe", "antwort", "nichtZurueckuebersetzt", "beenden"));
    }

    @Test
    void nachDemZuruecksetzenGiltWiederDieStartfolge() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            a.dialoge.antwort = true;
            a.panel.zuruecksetzen();
        });
        pruefeBeideRichtungen(a, List.of("eingabe", "scannen", "antwort", "nichtZurueckuebersetzt", "zuruecksetzen",
                "beenden"));
    }

    @Test
    void einUnbekanntesInneresElementWirdUeberSeinenVorfahrenEingeordnet() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            JTable tabelle = a.panel.fundeBereich().tabelle();
            Fokusreihenfolge politik = politik(a);
            JPanel editor = new JPanel();
            tabelle.add(editor);
            assertSame(a.panel.fundeBereich().alleAnwaehlen(), politik.getComponentAfter(a.panel, editor),
                    "ein Kind der Tabelle (z. B. ein Zelleneditor) steht an der Stelle der Tabelle");
            assertSame(a.panel.eingabeBereich().scannen(), politik.getComponentBefore(a.panel, tabelle));
            tabelle.remove(editor);
            assertEquals("eingabe", politik.getComponentAfter(a.panel, new JPanel()).getName(),
                    "eine fremde Komponente führt zum ersten Element");
        });
    }

    @Test
    void istNichtsBedienbarLiefernAlleAbfragenNullUndEndenDennoch() {
        edt(() -> {
            JPanel wurzel = new JPanel();
            JButton gesperrt = new JButton();
            gesperrt.setEnabled(false);
            JButton unsichtbar = new JButton();
            unsichtbar.setVisible(false);
            wurzel.add(gesperrt);
            wurzel.add(unsichtbar);
            Fokusreihenfolge politik = new Fokusreihenfolge(List.of(gesperrt, unsichtbar));
            assertNull(politik.getFirstComponent(wurzel));
            assertNull(politik.getLastComponent(wurzel));
            assertNull(politik.getDefaultComponent(wurzel));
            assertNull(politik.getComponentAfter(wurzel, gesperrt));
            assertNull(politik.getComponentBefore(wurzel, gesperrt));
            assertNull(politik.getComponentAfter(wurzel, unsichtbar));
            assertNull(politik.getComponentBefore(wurzel, new JPanel()));
        });
    }

    @Test
    void istNurDasAktuelleElementBedienbarLiefertEsSichSelbst() {
        edt(() -> {
            JPanel wurzel = new JPanel();
            JButton gesperrt = new JButton();
            gesperrt.setEnabled(false);
            JButton einziger = new JButton();
            wurzel.add(gesperrt);
            wurzel.add(einziger);
            Fokusreihenfolge politik = new Fokusreihenfolge(List.of(gesperrt, einziger));
            assertSame(einziger, politik.getComponentAfter(wurzel, einziger));
            assertSame(einziger, politik.getComponentBefore(wurzel, einziger));
            assertSame(einziger, politik.getFirstComponent(wurzel));
            assertSame(einziger, politik.getLastComponent(wurzel));
        });
    }

    @Test
    void derAnfangsfokusGehtAnDasErsteElementDerReihenfolge() {
        Aufbau a = new Aufbau();
        edt(() -> {
            TextBereich eingabe = a.panel.eingabeBereich().eingabe();
            assertSame(eingabe, politik(a).getDefaultComponent(a.panel));
            int vorher = eingabe.fokusAnfragen();
            a.panel.anfangsfokus();
            assertEquals(vorher + 1, eingabe.fokusAnfragen());
        });
    }

    @Test
    void keinHinweistextIstEinTabHalt() {
        Aufbau a = new Aufbau();
        a.scannen(DREI_FUNDE);
        edt(() -> {
            int hinweise = 0;
            for (Component k : alleKomponenten(a.panel)) {
                if (k instanceof Hinweistext hinweis) {
                    hinweise++;
                    assertFalse(hinweis.isFocusable(), hinweis.getName());
                    assertFalse(politik(a).reihenfolge().contains(hinweis), hinweis.getName());
                }
            }
            assertEquals(5, hinweise, "zwei Dauerhinweise und drei Leer-Texte");
        });
    }

    @Test
    void jedeBedienbareKomponenteStehtInDerListe() {
        Aufbau a = new Aufbau();
        edt(() -> {
            List<Component> liste = politik(a).reihenfolge();
            for (Component k : alleKomponenten(a.panel)) {
                boolean bedienbar = (k instanceof AbstractButton && !SwingAttrappen.istRollbalkenTeil(k))
                        || k instanceof JTable || k instanceof JList<?>
                        || (k instanceof JTextComponent && !(k instanceof Hinweistext));
                if (bedienbar) {
                    assertTrue(liste.contains(k), "fehlt in der Tab-Reihenfolge: " + k.getName());
                }
            }
            for (Component k : liste) {
                assertTrue(k.isFocusable(), k.getName());
            }
        });
    }
}
