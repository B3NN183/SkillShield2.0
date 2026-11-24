package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Bedienzustand;
import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Funktion;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Statusleiste;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.logik.Zwischenablage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Hauptpanel der Oberfläche: die vier Bereiche (1 Eingabe, 2 Funde, 3 Text für die KI, 4 Rückübersetzung), die
 * Fußleiste mit "Zurücksetzen" und "Beenden" und die Statusleiste (links Meldung, rechts Zuordnung). Das Panel ist
 * eine dünne Ansicht: Jedes Ereignis geht an die {@link Sitzung}, und nach jeder Benachrichtigung der Sitzung
 * zeichnet {@link #aktualisieren()} den Zustand neu. Es erzeugt kein Fenster; ein {@code JFrame} und die
 * System-Zwischenablage kommen erst mit dem Startprogramm (T-8) und werden von außen hereingereicht.
 *
 * <p>Nur auf dem Ereignis-Thread benutzen. Der Scan läuft im Hintergrund ({@link ScanStarter}); alle Aufrufe der
 * Sitzung sind über {@link Kontext#sicher} abgesichert, eine Ausnahme erscheint als fester Text mit Klassennamen.
 */
public final class HauptPanel extends JPanel {

    /** Mindestgröße des Hauptfensters; das Layout überlappt auch bei dieser Größe nicht (AK-3). */
    public static final Dimension MINDESTGROESSE = new Dimension(900, 640);

    private final Kontext kontext;
    private final Sitzung sitzung;
    private final ScanStarter starter;
    private final Runnable beenden;
    private final EingabeBereich bereich1;
    private final FundeBereich bereich2;
    private final KiTextBereich bereich3;
    private final AntwortBereich bereich4;
    private final JButton zuruecksetzen;
    private final JButton beendenKnopf;
    private final JLabel statusMeldung = Bausteine.etikett(Texte.BEREIT, "statusMeldung");
    private final JLabel statusZuordnung = Bausteine.etikett(Texte.KEINE_ZUORDNUNG, "statusZuordnung");
    /** Der Text, der zuletzt zum Scan angenommen wurde; nur gegen diesen Text passen die Fundpositionen. */
    private String gescannterText = "";

    /**
     * @param sitzung        Zustand und Regeln (dieselbe Instanz, die {@code zwischenablage} und {@code dialoge}
     *                       benutzt)
     * @param zwischenablage der Adapter, aus dem Einfügen liest
     * @param dialoge        Anzeige von Fehlern, die aus Aktionen der Oberfläche aufsteigen
     * @param beenden        wird bei "Beenden" gerufen; ruft {@code sitzung.beendenAnfordern()} und beendet das
     *                       Programm, wenn das {@code true} liefert (T-8)
     */
    public HauptPanel(Sitzung sitzung, Zwischenablage zwischenablage, Dialoge dialoge, Runnable beenden) {
        this(sitzung, zwischenablage, dialoge, beenden, null);
    }

    /** Wie der öffentliche Konstruktor, mit austauschbarem Scan-Starter ({@code null}: Hintergrund-Scan). */
    HauptPanel(Sitzung sitzung, Zwischenablage zwischenablage, Dialoge dialoge, Runnable beenden,
               ScanStarter scanStarter) {
        super(new BorderLayout(0, 4));
        this.sitzung = Objects.requireNonNull(sitzung, "sitzung");
        this.beenden = Objects.requireNonNull(beenden, "beenden");
        this.kontext = new Kontext(sitzung, zwischenablage, dialoge);
        this.starter = scanStarter != null ? scanStarter : ScanStarter.imHintergrund(kontext::melden);
        setName("hauptPanel");
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setMinimumSize(MINDESTGROESSE);

        bereich1 = new EingabeBereich(kontext, this::scannen);
        bereich2 = new FundeBereich(kontext, bereich1.eingabe(), this::alleAnwaehlen, this::markieren);
        bereich3 = new KiTextBereich(kontext, this::fuerKiKopieren);
        bereich4 = new AntwortBereich(kontext, this::zurueckuebersetzen, this::ergebnisKopieren);
        zuruecksetzen = Bausteine.knopf(Texte.ZURUECKSETZEN, "zuruecksetzen", this::zuruecksetzen);
        beendenKnopf = Bausteine.knopf(Texte.BEENDEN, "beenden", this::beenden);

        JPanel raster = new JPanel(new GridLayout(2, 2, 8, 8));
        raster.setName("bereiche");
        raster.add(bereich1);
        raster.add(bereich2);
        raster.add(bereich3);
        raster.add(bereich4);
        JPanel fussleiste = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        fussleiste.setName("fussleiste");
        fussleiste.add(zuruecksetzen);
        fussleiste.add(beendenKnopf);
        JPanel status = new JPanel(new BorderLayout(8, 0));
        status.setName("statusleiste");
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        status.add(statusMeldung, BorderLayout.CENTER);
        status.add(statusZuordnung, BorderLayout.EAST);
        JPanel unten = new JPanel(new BorderLayout(0, 2));
        unten.setName("unten");
        unten.add(fussleiste, BorderLayout.NORTH);
        unten.add(status, BorderLayout.SOUTH);
        add(raster, BorderLayout.CENTER);
        add(unten, BorderLayout.SOUTH);

        sitzung.beobachten(this::aktualisieren);
        aktualisieren();
    }

    // ------------------------------------------------------------------ Aktionen (T-7 bindet Tastenkürzel daran)

    /** "Scannen": nimmt den Text des Eingabefeldes und startet den Hintergrund-Scan, wenn die Sitzung zustimmt. */
    void scannen() {
        kontext.sicher(() -> {
            String text = bereich1.eingabe().getText();
            Optional<ScanAuftrag> auftrag = sitzung.scanAnfordern(text);
            auftrag.ifPresent(a -> {
                gescannterText = text;
                starten(a);
            });
        });
    }

    private void starten(ScanAuftrag auftrag) {
        try {
            starter.starte(sitzung, auftrag);
        } catch (RuntimeException e) {
            sitzung.scanFehlgeschlagen(auftrag, e);
        }
    }

    /** "Alle anwählen". */
    void alleAnwaehlen() {
        kontext.sicher(sitzung::alleAnwaehlen);
    }

    /**
     * "Markierung als Fund hinzufügen": gibt die Grenzen der Auswahl im Eingabefeld an die Sitzung, aber nur, solange
     * das Feld genau den gescannten Text zeigt (sonst passen die Positionen nicht). Die Offsets sind die des
     * Dokuments, dessen Text auch gescannt wurde, auch bei Zeilenenden mit Wagenrücklauf.
     */
    void markieren() {
        kontext.sicher(() -> {
            TextBereich eingabe = bereich1.eingabe();
            if (!sitzung.bedienzustand().istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN)
                    || !eingabe.getText().equals(gescannterText)) {
                return;
            }
            sitzung.markierungHinzufuegen(eingabe.getSelectionStart(), eingabe.getSelectionEnd());
        });
    }

    /** "Für KI kopieren". */
    void fuerKiKopieren() {
        kontext.sicher(sitzung::fuerKiKopieren);
    }

    /** "Zurückübersetzen" mit dem Text des Feldes "KI-Antwort". */
    void zurueckuebersetzen() {
        kontext.sicher(() -> sitzung.zurueckuebersetzen(bereich4.antwort().getText()));
    }

    /** "Rückübersetzten Text kopieren". */
    void ergebnisKopieren() {
        kontext.sicher(sitzung::ergebnisKopieren);
    }

    /** "Zurücksetzen": nach der Zustimmung der Sitzung werden Eingabe und KI-Antwort verworfen. */
    void zuruecksetzen() {
        kontext.sicher(() -> {
            if (sitzung.zuruecksetzenAnfordern()) {
                felderVerwerfen();
            }
        });
    }

    /**
     * Verwirft allen Text der Felder Eingabe, KI-Antwort und Ergebnis samt Zeichenpuffer (neue Dokumente) und den
     * gemerkten gescannten Text. Ruft die Sitzung nicht. Das Zurücksetzen tut das selbst; das Startprogramm (T-8)
     * ruft es nach einem erfolgreichen {@code sitzung.beendenAnfordern()}, bevor der Prozess endet (die Bereiche 2
     * und 3 hat die Sitzung dann schon über ihre Benachrichtigung geleert).
     */
    public void felderVerwerfen() {
        bereich1.leeren();
        bereich4.leeren();
        gescannterText = "";
    }

    /** "Beenden": der Weg der Schaltfläche; die Logik und das eigentliche Beenden steckt im {@code Runnable}. */
    void beenden() {
        kontext.sicher(beenden);
    }

    // ------------------------------------------------------------------ Anzeige

    /** Zeichnet den Zustand der Sitzung neu; wird nach jeder Zustandsänderung der Sitzung gerufen. */
    private void aktualisieren() {
        Bedienzustand zustand = sitzung.bedienzustand();
        Statusleiste leiste = sitzung.statusleiste();
        bereich1.aktualisieren(zustand);
        bereich2.aktualisieren(zustand);
        bereich3.aktualisieren(zustand);
        bereich4.aktualisieren(zustand);
        zuruecksetzen.setEnabled(zustand.istAktiv(Funktion.ZURUECKSETZEN));
        beendenKnopf.setEnabled(zustand.istAktiv(Funktion.BEENDEN));
        statusMeldung.setText(leiste.meldung());
        statusZuordnung.setText(leiste.zuordnung());
    }

    // ------------------------------------------------------------------ Zugriff für Tests und T-7

    EingabeBereich eingabeBereich() {
        return bereich1;
    }

    FundeBereich fundeBereich() {
        return bereich2;
    }

    KiTextBereich kiTextBereich() {
        return bereich3;
    }

    AntwortBereich antwortBereich() {
        return bereich4;
    }

    JButton zuruecksetzenKnopf() {
        return zuruecksetzen;
    }

    JButton beendenKnopf() {
        return beendenKnopf;
    }

    /** Sucht eine Komponente anhand ihres Namens im ganzen Baum; {@code null}, wenn es keine gibt. */
    Component komponente(String name) {
        return suche(this, name);
    }

    private static Component suche(Container wurzel, String name) {
        for (Component kind : wurzel.getComponents()) {
            if (name.equals(kind.getName())) {
                return kind;
            }
            if (kind instanceof Container container) {
                Component treffer = suche(container, name);
                if (treffer != null) {
                    return treffer;
                }
            }
        }
        return null;
    }

    /** Nur Zustand, nie Text. */
    @Override
    public String toString() {
        return "HauptPanel[" + sitzung + "]";
    }
}
