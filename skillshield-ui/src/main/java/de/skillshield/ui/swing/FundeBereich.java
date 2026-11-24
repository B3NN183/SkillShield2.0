package de.skillshield.ui.swing;

import de.skillshield.core.model.Finding;
import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.FundZeile;
import de.skillshield.ui.logik.ScanZustand;
import de.skillshield.ui.logik.Texte;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Bereich 2 "Funde prüfen": Tabelle (AK-13), Leer-Text (AK-5), Dauerhinweis (AK-21), "Alle anwählen" und
 * "Markierung als Fund hinzufügen". Eine gewählte Zeile markiert die Fundstelle im Eingabefeld und scrollt sie
 * sichtbar, ohne den Fokus zu ändern und ohne den Text anzufassen (AK-18). Es gibt keine Funktion, die nach Stufe
 * an- oder abwählt, filtert oder ausblendet, und kein "alle abwählen" (AK-16).
 */
final class FundeBereich extends JPanel {

    private static final String KARTE_LEER = "leer";
    private static final String KARTE_TABELLE = "tabelle";

    private final Kontext kontext;
    private final TextBereich eingabe;
    private final FundTabellenModell modell;
    private final JTable tabelle;
    private final Hinweistext leerText;
    private final CardLayout karten = new CardLayout();
    private final JPanel kartenFlaeche = new JPanel(karten);
    private final JButton alleAnwaehlen;
    private final JButton markieren;
    private final Highlighter.HighlightPainter painter;
    private final Bausteine.TabellenText textRenderer = new Bausteine.TabellenText();
    private final Bausteine.TabellenText objektRenderer = new Bausteine.TabellenText();

    FundeBereich(Kontext kontext, TextBereich eingabe, Runnable alleAnwaehlenAktion, Runnable markierenAktion) {
        super(new BorderLayout(0, 4));
        this.kontext = kontext;
        this.eingabe = eingabe;
        setName("bereich2");
        Bausteine.innenabstand(this);
        Color farbe = UIManager.getColor("TextArea.selectionBackground");
        painter = new DefaultHighlighter.DefaultHighlightPainter(farbe != null ? farbe : Color.YELLOW);

        modell = new FundTabellenModell(kontext);
        tabelle = new JTable(modell) {
            /** Nie ein Tooltip aus dem Zelleninhalt: Swing fragt sonst den Renderer, und die Zelle zeigt Fundtext. */
            @Override
            public String getToolTipText(MouseEvent e) {
                return null;
            }
        };
        tabelle.setName("funde");
        tabelle.getAccessibleContext().setAccessibleName(Texte.NAME_FUNDE);
        tabelle.getAccessibleContext().setAccessibleDescription(Texte.HINWEIS_ERKENNUNG);
        tabelle.getTableHeader().getAccessibleContext().setAccessibleName(Texte.NAME_SPALTENKOEPFE);
        tabelle.setAutoCreateRowSorter(false);
        tabelle.setFillsViewportHeight(true);
        tabelle.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tabelle.getTableHeader().setReorderingAllowed(false);
        tabelle.setDefaultRenderer(String.class, textRenderer);
        tabelle.setDefaultRenderer(Object.class, objektRenderer);
        tabelle.getColumnModel().getColumn(0).setPreferredWidth(80);
        tabelle.getColumnModel().getColumn(0).setMaxWidth(120);
        tabelle.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                kontext.sicher(this::hervorheben);
            }
        });
        // Keine Standard-Editoren (ein JTextField mit Standard-TransferHandler): nur das Kontrollkästchen ist
        // bearbeitbar.
        tabelle.setDefaultEditor(Object.class, null);
        tabelle.setDefaultEditor(String.class, null);
        tabelle.setDefaultEditor(Number.class, null);
        tabelle.setDefaultEditor(Boolean.class, new AuswahlEditor());
        // Kein Editor durch eine beliebige Taste; ein Editor, der den Fokus verliert, wird übernommen und endet.
        tabelle.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        tabelle.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        Textaktionen.sperre(tabelle);

        JLabel ueberschrift = Bausteine.etikett(Texte.BEREICH_2, "ueberschrift2");
        ueberschrift.setLabelFor(tabelle);
        alleAnwaehlen = Bausteine.knopf(Texte.ALLE_ANWAEHLEN, "alleAnwaehlen", alleAnwaehlenAktion);
        markieren = Bausteine.knopf(Texte.MARKIERUNG_HINZUFUEGEN, "markieren", markierenAktion);
        add(Bausteine.kopfzeile(ueberschrift, alleAnwaehlen, markieren), BorderLayout.NORTH);

        leerText = new Hinweistext("leerFunde", Texte.LEER_VOR_SCAN, Texte.NAME_LEER_FUNDE, kontext);
        kartenFlaeche.add(leerText, KARTE_LEER);
        kartenFlaeche.add(Bausteine.scroll(tabelle, "scrollFunde"), KARTE_TABELLE);
        kartenFlaeche.setName("kartenFunde");
        add(kartenFlaeche, BorderLayout.CENTER);
        add(new Hinweistext("hinweisErkennung", Texte.HINWEIS_ERKENNUNG, Texte.NAME_HINWEIS_ERKENNUNG, kontext),
                BorderLayout.SOUTH);
    }

    JTable tabelle() {
        return tabelle;
    }

    JButton alleAnwaehlen() {
        return alleAnwaehlen;
    }

    JButton markieren() {
        return markieren;
    }

    void aktualisieren(Bedienzustand zustand) {
        // Ein offener Editor zeigt sonst einen Zustand von vor der Änderung (und JTable würde ihn bei einer
        // Datenänderung noch übernehmen).
        Tastatur.bearbeitungAbbrechen(tabelle);
        modell.aktualisieren();
        boolean leer = modell.getRowCount() == 0;
        if (leer) {
            leerText.setText(leerText(kontext.sitzung.scanZustand()));
        }
        karten.show(kartenFlaeche, leer ? KARTE_LEER : KARTE_TABELLE);
        alleAnwaehlen.setEnabled(zustand.istAktiv(Funktion.ALLE_ANWAEHLEN));
        Bausteine.sperrgrund(alleAnwaehlen, zustand, Funktion.ALLE_ANWAEHLEN);
        markieren.setEnabled(zustand.istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));
        Bausteine.sperrgrund(markieren, zustand, Funktion.MARKIERUNG_HINZUFUEGEN);
        if (leer) {
            hervorheben();
            rendererZuruecksetzen();
        }
    }

    /** Die Renderer der Tabelle behalten den Text der zuletzt gezeichneten Zelle; hier wird er vergessen. */
    void rendererZuruecksetzen() {
        textRenderer.zuruecksetzen();
        objektRenderer.zuruecksetzen();
    }

    private static String leerText(ScanZustand zustand) {
        return switch (zustand) {
            case KEIN_SCAN -> Texte.LEER_VOR_SCAN;
            case LAEUFT -> Texte.SCAN_LAEUFT;
            case GUELTIG -> Texte.LEER_NACH_SCAN;
            case VERALTET -> Texte.NEUER_SCAN_NOETIG;
        };
    }

    /** Markiert die Fundstelle der gewählten Zeile im Eingabefeld und scrollt sie ins Bild, ohne Fokuswechsel. */
    private void hervorheben() {
        Highlighter markierungen = eingabe.getHighlighter();
        markierungen.removeAllHighlights();
        int zeile = tabelle.getSelectedRow();
        List<FundZeile> zeilen = kontext.sitzung.zeilen();
        if (zeile < 0 || zeile >= zeilen.size()) {
            return;
        }
        Finding fund = zeilen.get(zeile).fund();
        if (fund.end() > eingabe.getDocument().getLength()) {
            return;
        }
        try {
            markierungen.addHighlight(fund.start(), fund.end(), painter);
            Rectangle2D stelle = eingabe.modelToView2D(fund.start());
            if (stelle != null) {
                eingabe.scrollRectToVisible(stelle.getBounds());
            }
        } catch (BadLocationException e) {
            // Die Stelle liegt nicht im Dokument: nichts hervorheben. Kein Text in der Meldung.
            markierungen.removeAllHighlights();
        }
    }
}
