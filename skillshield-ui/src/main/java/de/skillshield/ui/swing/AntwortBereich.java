package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.Rueckuebersetzung;
import de.skillshield.ui.logik.Texte;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

/**
 * Bereich 4 "KI-Antwort zurückübersetzen": das Feld "KI-Antwort", "Zurückübersetzen", das nicht bearbeitbare, aber
 * markier- und kopierbare Ergebnis, "Rückübersetzten Text kopieren" und die Liste "Nicht zurückübersetzt" (AK-36
 * bis AK-39) mit dem Dauerhinweis zur Zuordnung (AK-40). Die Liste zeigt höchstens {@link Texte#MAX_LISTENZEILEN}
 * Zeilen und zählt den Rest ("… und n weitere"); eine Antwort kann bis zu rund 125 000 nicht auflösbare Platzhalter
 * haben.
 */
final class AntwortBereich extends JPanel {

    private static final String KARTE_LEER = "leer";
    private static final String KARTE_TEXT = "text";

    private final Kontext kontext;
    private final TextBereich antwort = new TextBereich("antwort", 4);
    private final TextBereich ergebnis = new TextBereich("ergebnis", 4);
    private final JList<String> liste = Bausteine.htmlAus(new JList<>());
    private final CardLayout karten = new CardLayout();
    private final JPanel kartenFlaeche = new JPanel(karten);
    private final JButton zurueckuebersetzen;
    private final JButton ergebnisKopieren;
    /** Das zuletzt angezeigte Ergebnis (Identität); verhindert unnötiges Neusetzen großer Texte. */
    private Rueckuebersetzung angezeigt;

    AntwortBereich(Kontext kontext, Runnable zurueckuebersetzenAktion, Runnable ergebnisKopierenAktion) {
        super(new GridBagLayout());
        this.kontext = kontext;
        setName("bereich4");
        Bausteine.innenabstand(this);
        Textaktionen.installiere(antwort, Textaktionen.Rolle.BEARBEITBAR, kontext);
        ergebnis.setEditable(false);
        Textaktionen.installiere(ergebnis, Textaktionen.Rolle.NUR_LESEN, kontext);
        liste.setName("nichtZurueckuebersetzt");
        liste.setCellRenderer(new Bausteine.ListenText());
        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Textaktionen.sperre(liste);
        antwort.aenderungenMelden(() ->
                kontext.sicher(() -> kontext.sitzung.kiAntwortGeaendert(antwort.getDocument().getLength() == 0)));

        JLabel antwortTitel = Bausteine.etikett(Texte.BEREICH_4, "ueberschrift4");
        antwortTitel.setLabelFor(antwort);
        zurueckuebersetzen = Bausteine.knopf(Texte.ZURUECKUEBERSETZEN, "zurueckuebersetzen", zurueckuebersetzenAktion);
        JLabel ergebnisTitel = Bausteine.etikett(Texte.RUECKUEBERSETZTER_TEXT, "beschriftungErgebnis");
        ergebnisTitel.setLabelFor(ergebnis);
        ergebnisKopieren = Bausteine.knopf(Texte.ERGEBNIS_KOPIEREN_SCHALTFLAECHE, "ergebnisKopieren",
                ergebnisKopierenAktion);
        JLabel listeTitel = Bausteine.etikett(Texte.NICHT_ZURUECKUEBERSETZT, "beschriftungListe");
        listeTitel.setLabelFor(liste);

        kartenFlaeche.setName("kartenErgebnis");
        kartenFlaeche.add(new Hinweistext("leerErgebnis", Texte.LEER_BEREICH_4, kontext), KARTE_LEER);
        kartenFlaeche.add(Bausteine.scroll(ergebnis, "scrollErgebnis"), KARTE_TEXT);
        karten.show(kartenFlaeche, KARTE_LEER);

        zeile(new Hinweistext("hinweisZuordnung", Texte.HINWEIS_ZUORDNUNG, kontext), 0, 0.0);
        zeile(Bausteine.kopfzeile(antwortTitel, zurueckuebersetzen), 1, 0.0);
        zeile(Bausteine.scroll(antwort, "scrollAntwort"), 2, 1.0);
        zeile(Bausteine.kopfzeile(ergebnisTitel, ergebnisKopieren), 3, 0.0);
        zeile(kartenFlaeche, 4, 1.0);
        zeile(listeTitel, 5, 0.0);
        zeile(Bausteine.scroll(liste, "scrollListe"), 6, 0.6);
    }

    private void zeile(Component komponente, int zeile, double gewicht) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = zeile;
        c.weightx = 1.0;
        c.weighty = gewicht;
        c.fill = gewicht > 0 ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 0);
        add(komponente, c);
    }

    TextBereich antwort() {
        return antwort;
    }

    TextBereich ergebnis() {
        return ergebnis;
    }

    JList<String> liste() {
        return liste;
    }

    JButton zurueckuebersetzen() {
        return zurueckuebersetzen;
    }

    JButton ergebnisKopieren() {
        return ergebnisKopieren;
    }

    /**
     * Verwirft die KI-Antwort und das Ergebnis samt Zeichenpuffer (nach dem Zurücksetzen oder Beenden); die Sitzung
     * ist dann schon leer.
     */
    void leeren() {
        antwort.zuruecksetzen();
        ergebnis.zuruecksetzen();
        angezeigt = null;
    }

    void aktualisieren(Bedienzustand zustand) {
        zurueckuebersetzen.setEnabled(zustand.istAktiv(Funktion.ZURUECKUEBERSETZEN));
        ergebnisKopieren.setEnabled(zustand.istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        Optional<Rueckuebersetzung> neu = kontext.sitzung.rueckuebersetzung();
        Rueckuebersetzung aktuell = neu.orElse(null);
        if (aktuell == angezeigt) {
            return;
        }
        angezeigt = aktuell;
        if (aktuell == null) {
            ergebnis.zuruecksetzen();
            liste.setListData(new String[0]);
            karten.show(kartenFlaeche, KARTE_LEER);
            return;
        }
        ergebnis.textSetzen(aktuell.text());
        ergebnis.setCaretPosition(0);
        liste.setListData(gekuerzt(aktuell.listenzeilen()));
        karten.show(kartenFlaeche, aktuell.text().isEmpty() ? KARTE_LEER : KARTE_TEXT);
    }

    /**
     * Die Zeilen der Liste, auf {@link Texte#MAX_LISTENZEILEN} gekürzt; bei mehr Zeilen steht als letzte
     * {@link Texte#undWeitere(int)} mit der Zahl der nicht gezeigten.
     */
    static String[] gekuerzt(List<String> zeilen) {
        if (zeilen.size() <= Texte.MAX_LISTENZEILEN) {
            return zeilen.toArray(new String[0]);
        }
        String[] gezeigt = new String[Texte.MAX_LISTENZEILEN + 1];
        for (int i = 0; i < Texte.MAX_LISTENZEILEN; i++) {
            gezeigt[i] = zeilen.get(i);
        }
        gezeigt[Texte.MAX_LISTENZEILEN] = Texte.undWeitere(zeilen.size() - Texte.MAX_LISTENZEILEN);
        return gezeigt;
    }
}
