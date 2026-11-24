package de.skillshield.ui.logik;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PlaceholderSyntax;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Zustand und Regeln eines Arbeitsgangs: Scan-Zustand, Fundliste mit Auswahl, Live-Ergebnis der Pseudonymisierung
 * und aktive Zuordnung (Architektur-Plan Kap. 2.1, 3.1, 3.2). Die Klasse kennt weder Swing noch AWT. Nicht
 * threadsicher: alle Aufrufe kommen von einem Thread (dem Ereignis-Thread); nur {@link ScanAuftrag#ausfuehren()}
 * läuft im Hintergrund.
 *
 * <p>Schutz geht vor (D-2): Nach jedem Scan sind alle Funde angewählt, es gibt keine Funktion, die nach Stufe oder
 * Konfidenz an- oder abwählt, filtert oder ausblendet, und kein "alle abwählen". Die Klasse ruft nur den Kern auf
 * und bewertet nichts selbst (AK-53). Meldungen entstehen nur aus {@link Texte} und Zahlen; {@link #toString()}
 * enthält nie Text.
 *
 * <p>Stand T-5: Scan, Auswahl, Live-Ergebnis, manuelles Hinzufügen, Kopieren, Rückübersetzung, Statusleiste,
 * Bedienzustand sowie Zurücksetzen und Beenden mit ihren Rückfragen. Die aktive Zuordnung (für die
 * Statusleiste und Rückfragen) folgt dem Live-Ergebnis, bis aus Bereich 3 kopiert wird; dann ist sie auf das kopierte
 * Ergebnis eingefroren (Abweichung A-2, Kap. 3.5), bis neu gescannt wird. Zurückübersetzt wird nur mit einer
 * eingefrorenen Zuordnung (S-2), also nur die Antwort zum zuletzt kopierten Text.
 *
 * <p>Invariante von {@code zuletztKopierteZuordnung}: Sie überlebt einen neuen Scan und wird nur für den Hinweis
 * "Platzhalter geändert" verglichen, nie zur Rückübersetzung benutzt; Zurücksetzen und Beenden verwerfen sie.
 */
public final class Sitzung {

    /** Obergrenze des Eingabetextes, gezählt als {@code String.length()} (D-10). */
    public static final int MAX_ZEICHEN = 1_000_000;

    /**
     * Fertig berechnetes Ergebnis für eine Auswahl: Text für die KI, Zuordnung und die Tabellenzeilen. Alles, was
     * scheitern kann, ist beim Anlegen erledigt; das Festschreiben besteht nur noch aus Zuweisungen.
     */
    private record Live(String text, PlaceholderMapping zuordnung, List<FundZeile> zeilen) {
        static final Live LEER = new Live("", PlaceholderMapping.empty(), List.of());

        /** Nur Länge und Zahl der Platzhalter, nie der Text. */
        @Override
        public String toString() {
            return "Live[zeichen=" + text.length() + ", platzhalter=" + zuordnung.size() + "]";
        }
    }

    private final Function<String, List<Finding>> scanner;
    private final ZwischenablageRegel zwischenablage;
    private final Dialoge dialoge;
    private final Pseudonymizer pseudonymizer = new Pseudonymizer();
    /** Rückübersetzung; im Test austauschbar, im Programm der {@link Reidentifier} des Kerns. */
    private final BiFunction<String, PlaceholderMapping, ReidentificationResult> restore;
    private final List<Runnable> beobachter = new ArrayList<>();

    private ScanZustand zustand = ScanZustand.KEIN_SCAN;
    private ScanZustand vorherigerZustand = ScanZustand.KEIN_SCAN;
    private long generation;
    /** Der laufende Auftrag (Identität); Rückrufe anderer Aufträge werden ignoriert. */
    private ScanAuftrag laufenderAuftrag;
    private String text = "";
    private List<Finding> funde = List.of();
    private boolean[] angewaehlt = new boolean[0];
    private Live live = Live.LEER;
    /** Aktive Zuordnung; {@code null}, solange kein Scan erfolgreich war. */
    private PlaceholderMapping zuordnung;
    /**
     * {@code true}, wenn {@link #zuordnung} beim Kopieren aus Bereich 3 eingefroren wurde (A-2). Nur dann ist
     * "Zurückübersetzen" möglich (S-2); ein neuer Scan hebt das Einfrieren auf.
     */
    private boolean zuordnungEingefroren;
    /**
     * Die zuletzt beim Kopieren für die KI eingefrorene Zuordnung, auch über einen neuen Scan hinweg; nur für den
     * Hinweis, dass sich Platzhalter geändert haben (S-3). Nur im Arbeitsspeicher, nie ausgegeben.
     */
    private PlaceholderMapping zuletztKopierteZuordnung;
    private boolean antwortLeer = true;
    private Rueckuebersetzung rueckuebersetzung;
    private String meldung = Texte.BEREIT;

    /**
     * @param scanner        Scan-Funktion, threadsicher (im Test austauschbar)
     * @param zwischenablage Zwischenablage
     * @param dialoge        Rückfragen und Fehlerdialog
     */
    public Sitzung(Function<String, List<Finding>> scanner, Zwischenablage zwischenablage, Dialoge dialoge) {
        this(scanner, zwischenablage, dialoge, new Reidentifier()::restore);
    }

    /** Wie der öffentliche Konstruktor, mit austauschbarer Rückübersetzung (für Tests des Fehlerzweigs). */
    Sitzung(Function<String, List<Finding>> scanner, Zwischenablage zwischenablage, Dialoge dialoge,
            BiFunction<String, PlaceholderMapping, ReidentificationResult> restore) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.zwischenablage = new ZwischenablageRegel(Objects.requireNonNull(zwischenablage, "zwischenablage"));
        this.dialoge = Objects.requireNonNull(dialoge, "dialoge");
        this.restore = Objects.requireNonNull(restore, "restore");
    }

    /** Sitzung mit dem Standard-Scanner des Kerns ({@code TextScanner.standard()}). */
    public static Sitzung standard(Zwischenablage zwischenablage, Dialoge dialoge) {
        return new Sitzung(TextScanner.standard()::scan, zwischenablage, dialoge);
    }

    /** Meldet einen Beobachter an, der nach jeder Zustandsänderung aufgerufen wird. */
    public void beobachten(Runnable beobachter) {
        this.beobachter.add(Objects.requireNonNull(beobachter, "beobachter"));
    }

    // ------------------------------------------------------------------ Scan

    /**
     * Prüft die Eingabe und startet gegebenenfalls einen Scan: (1) leer oder nur Leerraum, (2) länger als
     * {@link #MAX_ZEICHEN}, (3) Rückfrage bei bestehender Zuordnung mit mindestens einem Platzhalter. Bei einer
     * Ablehnung ändern sich Funde, Zuordnung und Zustand nicht (nur die Statusmeldung bei (1) und (2)). Läuft schon
     * ein Scan, geschieht nichts.
     *
     * @return der Auftrag für den Hintergrund-Scan; leer, wenn nicht gescannt wird
     */
    public Optional<ScanAuftrag> scanAnfordern(String eingabe) {
        Objects.requireNonNull(eingabe, "eingabe");
        if (zustand == ScanZustand.LAEUFT) {
            return Optional.empty();
        }
        if (Leerraum.istLeer(eingabe)) {
            return ablehnen(Texte.TEXT_FEHLT);
        }
        if (eingabe.length() > MAX_ZEICHEN) {
            return ablehnen(Texte.zuLang(MAX_ZEICHEN));
        }
        if (hatZuordnungMitPlatzhalter() && !dialoge.bestaetigt(Rueckfrage.NEUER_SCAN)) {
            return Optional.empty();
        }
        if (zustand == ScanZustand.GUELTIG && !eingabe.equals(text)) {
            eingabeGeaendert();
        }
        vorherigerZustand = zustand;
        zustand = ScanZustand.LAEUFT;
        generation++;
        meldung = Texte.SCAN_LAEUFT;
        laufenderAuftrag = new ScanAuftrag(scanner, eingabe, generation);
        informieren();
        return Optional.of(laufenderAuftrag);
    }

    /**
     * Übernimmt das Ergebnis des Hintergrund-Scans: Funde unverändert (keine Filterung), alle angewählt, neue aktive
     * Zuordnung. Die Berechnung erfolgt vor dem Festschreiben; scheitert sie, gilt {@link #scanFehlgeschlagen}.
     * Ein veraltetes Ergebnis (Eingabe zwischenzeitlich geändert) wird verworfen; der Rückruf eines anderen als des
     * laufenden Auftrags wird still ignoriert.
     */
    public void scanAbgeschlossen(ScanAuftrag auftrag, List<Finding> ergebnis) {
        Objects.requireNonNull(auftrag, "auftrag");
        Objects.requireNonNull(ergebnis, "ergebnis");
        if (!istLaufenderAuftrag(auftrag)) {
            return;
        }
        if (auftrag.generation() != generation) {
            zurueckZumVorherigenZustand();
            informieren();
            return;
        }
        List<Finding> neueFunde;
        boolean[] alle;
        Live neuesLive;
        String neueMeldung;
        try {
            neueFunde = List.copyOf(ergebnis);
            alle = new boolean[neueFunde.size()];
            Arrays.fill(alle, true);
            neuesLive = berechne(auftrag.text(), neueFunde, alle);
            neueMeldung = zaehlung(neueFunde, alle);
        } catch (RuntimeException e) {
            scanFehlgeschlagen(auftrag, e);
            return;
        }
        // Festschreiben: nur noch Zuweisungen, nichts kann mehr scheitern.
        text = auftrag.text();
        funde = neueFunde;
        angewaehlt = alle;
        live = neuesLive;
        zuordnung = neuesLive.zuordnung();
        zuordnungEingefroren = false;
        // Das Ergebnis der Rückübersetzung gehört zur alten Zuordnung und hält Originalwerte: Es wird mit ihr
        // verworfen, damit es nach einem neuen Scan nicht mehr als "Ergebnis kopieren" bedienbar ist (T-5).
        rueckuebersetzung = null;
        laufenderAuftrag = null;
        zustand = ScanZustand.GUELTIG;
        meldung = neueMeldung;
        informieren();
    }

    /**
     * Meldet einen gescheiterten Scan: Eingabe, Funde, Zuordnung und Zustand bleiben wie vor dem Scan (ein vorher
     * gültiger Scan bleibt gültig, Auslegung A-5), der Dialog zeigt festen Text und Klassennamen, nie die Meldung
     * der Ausnahme. Der Rückruf eines anderen als des laufenden Auftrags wird still ignoriert.
     */
    public void scanFehlgeschlagen(ScanAuftrag auftrag, Throwable fehler) {
        Objects.requireNonNull(auftrag, "auftrag");
        Objects.requireNonNull(fehler, "fehler");
        if (!istLaufenderAuftrag(auftrag)) {
            return;
        }
        zurueckZumVorherigenZustand();
        meldung = Texte.FEHLER;
        informieren();
        dialoge.fehler(Texte.fehlerMeldung(fehler));
    }

    /**
     * Der Eingabetext wurde geändert: ein gültiger Scan wird {@link ScanZustand#VERALTET} (D-4). Tabelle und Text
     * für die KI sind danach leer, die aktive Zuordnung bleibt (AK-12). Ein laufender Scan wird verworfen.
     */
    public void eingabeGeaendert() {
        switch (zustand) {
            case GUELTIG -> {
                zustand = ScanZustand.VERALTET;
                verwerfeFunde();
                meldung = Texte.NEUER_SCAN_NOETIG;
                informieren();
            }
            case LAEUFT -> {
                generation++;
                if (vorherigerZustand == ScanZustand.GUELTIG) {
                    vorherigerZustand = ScanZustand.VERALTET;
                }
                verwerfeFunde();
                informieren();
            }
            case KEIN_SCAN, VERALTET -> { }
        }
    }

    /** Aktueller Scan-Zustand. */
    public ScanZustand scanZustand() {
        return zustand;
    }

    // ------------------------------------------------------------------ Funde prüfen

    /** Tabellenzeilen aller Funde in Positionsreihenfolge; leer ohne gültigen Scan. Unveränderlich. */
    public List<FundZeile> zeilen() {
        return zustand == ScanZustand.GUELTIG ? live.zeilen() : List.of();
    }

    /**
     * Wählt einen Fund an oder ab und berechnet das Live-Ergebnis neu. Ohne gültigen Scan geschieht nichts.
     *
     * @throws IndexOutOfBoundsException bei einer Zeile außerhalb der Tabelle
     */
    public void anwaehlen(int zeile, boolean neuAngewaehlt) {
        if (zustand != ScanZustand.GUELTIG) {
            return;
        }
        Objects.checkIndex(zeile, angewaehlt.length);
        if (angewaehlt[zeile] == neuAngewaehlt) {
            return;
        }
        boolean[] neu = angewaehlt.clone();
        neu[zeile] = neuAngewaehlt;
        auswahlUebernehmen(neu);
    }

    /** Wählt alle Funde an, auch einzeln abgewählte (AK-16). Ohne gültigen Scan geschieht nichts. */
    public void alleAnwaehlen() {
        if (zustand != ScanZustand.GUELTIG) {
            return;
        }
        boolean[] neu = new boolean[angewaehlt.length];
        Arrays.fill(neu, true);
        auswahlUebernehmen(neu);
    }

    /**
     * Fügt die markierte Stelle {@code [start, ende)} des Eingabetextes und alle weiteren zeichengleichen Vorkommen
     * als manuelle Funde hinzu (D-7 bis D-9, Algorithmus in {@link ManuellerFund}). Die neuen Funde sind
     * angewählt, die Auswahl der übrigen Zeilen bleibt. Ohne gültigen Scan geschieht nichts (die Funktion ist
     * gesperrt, AK-26). Eine leere oder nur aus Leerraum bestehende Markierung ändert nur die Statusmeldung.
     * Berechnet wird vor dem Festschreiben: Scheitert die Neuberechnung, bleibt der bisherige Zustand vollständig
     * erhalten und die Ausnahme erreicht den Aufrufer.
     *
     * @throws IndexOutOfBoundsException wenn die Markierung nicht im Text liegt
     */
    public void markierungHinzufuegen(int start, int ende) {
        if (zustand != ScanZustand.GUELTIG) {
            return;
        }
        ManuellerFund.Ergebnis hinzu = ManuellerFund.hinzufuegen(text, live.zeilen(), start, ende);
        if (hinzu.abgelehnt()) {
            meldung = Texte.MARKIERUNG_FEHLT;
            informieren();
            return;
        }
        List<Finding> neueFunde = new ArrayList<>(hinzu.zeilen().size());
        boolean[] neueAuswahl = new boolean[hinzu.zeilen().size()];
        for (int i = 0; i < neueAuswahl.length; i++) {
            FundZeile zeile = hinzu.zeilen().get(i);
            neueFunde.add(zeile.fund());
            neueAuswahl[i] = zeile.angewaehlt();
        }
        Live neuesLive = berechne(text, neueFunde, neueAuswahl);
        // Hinzufüge-Meldung und aktuelle Zählung (AK-20) stehen zusammen in der Meldungszeile.
        String neueMeldung = Texte.hinzugefuegt(hinzu.hinzugefuegt(), hinzu.bereitsGeschuetzt(), hinzu.ersetzt())
                + " " + zaehlung(neueFunde, neueAuswahl) + auswahlHinweis(neuesLive);
        // Festschreiben: nur noch Zuweisungen, nichts kann mehr scheitern.
        funde = List.copyOf(neueFunde);
        angewaehlt = neueAuswahl;
        live = neuesLive;
        zuordnungFolgtLive(neuesLive);
        meldung = neueMeldung;
        informieren();
    }

    /** Text für die KI (Bereich 3): der Text mit den angewählten Funden als Platzhalter; leer ohne gültigen Scan. */
    public String textFuerKi() {
        return zustand == ScanZustand.GUELTIG ? live.text() : "";
    }

    // ------------------------------------------------------------------ Kopieren und Rückübersetzung

    /**
     * "Für KI kopieren": legt den ganzen Text aus Bereich 3 ({@link #textFuerKi()}) als reinen Text in die
     * Zwischenablage (AK-31) und friert die aktive Zuordnung auf dieses Ergebnis ein (A-2). Ohne gültigen Scan
     * geschieht nichts (AK-30). Ist kein Fund angewählt (auch bei null Funden), wird vorher mit
     * {@link Rueckfrage#UNVERAENDERT_KOPIEREN} gefragt (AK-32); bei "Abbrechen" ändert sich nichts. Nimmt die
     * Zwischenablage den Text nicht an, meldet das Programm es und ändert nichts weiter. Die Rückfrage ist modal;
     * hat sich währenddessen der Text für die KI geändert, wird nicht kopiert.
     */
    public void fuerKiKopieren() {
        if (zustand != ScanZustand.GUELTIG) {
            return;
        }
        String kopiertText = textFuerKi();
        boolean unveraendert = keinFundAngewaehlt();
        if (unveraendert) {
            if (!dialoge.bestaetigt(Rueckfrage.UNVERAENDERT_KOPIEREN)) {
                return;
            }
            if (zustand != ScanZustand.GUELTIG || !kopiertText.equals(textFuerKi())) {
                return;
            }
        }
        int ersetzt = angewaehlteZahl();
        if (!ablegen(kopiertText)) {
            return;
        }
        boolean geaendert = platzhalterGeaendert(zuletztKopierteZuordnung, live.zuordnung());
        zuordnung = live.zuordnung();
        zuordnungEingefroren = true;
        zuletztKopierteZuordnung = zuordnung;
        meldung = (unveraendert ? Texte.UNVERAENDERT_KOPIERT : Texte.kopiert(ersetzt))
                + (geaendert ? " " + Texte.PLATZHALTER_GEAENDERT : "");
        informieren();
    }

    /**
     * {@code true}, wenn ein Platzhalter der früher kopierten Zuordnung in der neuen fehlt oder dort für einen
     * anderen Wert steht (S-3). Zusätzliche Platzhalter allein ändern nichts an früheren Antworten.
     */
    private static boolean platzhalterGeaendert(PlaceholderMapping frueher, PlaceholderMapping neu) {
        if (frueher == null) {
            return false;
        }
        for (String platzhalter : frueher.placeholders()) {
            if (!neu.resolve(platzhalter).equals(frueher.resolve(platzhalter))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strg+C/X aus der Eingabe, der KI-Antwort oder dem Ergebnis: legt den markierten Ausschnitt ab und merkt ihn
     * für die Zwischenablage-Regel (D-6). Ein leerer Ausschnitt tut nichts. Aus Bereich 3 kopiert die Ansicht über
     * {@link #fuerKiKopieren()}, nie über diese Methode.
     *
     * @return {@code true}, wenn der Ausschnitt abgelegt wurde; die Ansicht löscht beim Ausschneiden nur dann
     *         (T-6: sonst ginge der Text verloren, wenn die Zwischenablage gesperrt ist)
     */
    public boolean textKopieren(String teil) {
        if (teil == null || teil.isEmpty()) {
            return false;
        }
        return ablegen(teil);
    }

    /** Die Ansicht meldet, ob das Feld "KI-Antwort" leer ist; davon hängt "Zurückübersetzen" ab (AK-36). */
    public void kiAntwortGeaendert(boolean leer) {
        if (antwortLeer != leer) {
            antwortLeer = leer;
            informieren();
        }
    }

    /**
     * Übersetzt die KI-Antwort mit der beim Kopieren eingefrorenen Zuordnung zurück (AK-37, AK-38). Möglich ist das
     * nur, wenn im aktuellen Scan bereits für die KI kopiert wurde (S-2); sonst, und während des Scans, geschieht
     * nichts. Bei leerer Antwort meldet die Statusleiste {@link Texte#ANTWORT_FEHLT}, bei einer Antwort über
     * {@link #MAX_ZEICHEN} {@link Texte#antwortZuLang} (S-4); beides ändert nur die Meldung. Berechnet wird vor dem
     * Festschreiben; scheitert es, bleibt alles wie vorher, die Meldung ist
     * {@link Texte#RUECKUEBERSETZEN_FEHLGESCHLAGEN} und der Dialog zeigt diesen Text mit dem Klassennamen der
     * Ausnahme.
     */
    public void zurueckuebersetzen(String antwort) {
        Objects.requireNonNull(antwort, "antwort");
        if (!zuordnungEingefroren || zustand == ScanZustand.LAEUFT) {
            return;
        }
        if (antwort.isEmpty()) {
            meldung = Texte.ANTWORT_FEHLT;
            informieren();
            return;
        }
        if (antwort.length() > MAX_ZEICHEN) {
            meldung = Texte.antwortZuLang(MAX_ZEICHEN);
            informieren();
            return;
        }
        Rueckuebersetzung neu;
        try {
            ReidentificationResult ergebnis = restore.apply(antwort, zuordnung);
            int gesamt = PlaceholderSyntax.tokenize(antwort, 0, antwort.length(), zuordnung.typeNames()).size();
            neu = new Rueckuebersetzung(ergebnis.text(), ergebnis.unresolved(), gesamt);
        } catch (RuntimeException e) {
            meldung = Texte.RUECKUEBERSETZEN_FEHLGESCHLAGEN;
            informieren();
            dialoge.fehler(Texte.rueckuebersetzenFehlerMeldung(e));
            return;
        }
        String neueMeldung;
        if (!neu.offen().isEmpty()) {
            neueMeldung = Texte.nichtZurueckuebersetzt(neu.offen().size());
        } else {
            neueMeldung = neu.platzhalterGesamt() == 0 ? Texte.KEINE_PLATZHALTER : Texte.ALLE_ZURUECK;
        }
        rueckuebersetzung = neu;
        meldung = neueMeldung;
        informieren();
    }

    /** Ergebnis der letzten Rückübersetzung für Bereich 4; leer vor der ersten Rückübersetzung. */
    public Optional<Rueckuebersetzung> rueckuebersetzung() {
        return Optional.ofNullable(rueckuebersetzung);
    }

    /** "Rückübersetzten Text kopieren": legt genau den Ergebnistext ab (AK-39). Ohne Ergebnis geschieht nichts. */
    public void ergebnisKopieren() {
        if (rueckuebersetzung == null || rueckuebersetzung.text().isEmpty() || zustand == ScanZustand.LAEUFT) {
            return;
        }
        if (ablegen(rueckuebersetzung.text())) {
            meldung = Texte.ERGEBNIS_KOPIERT;
            informieren();
        }
    }

    // ------------------------------------------------------------------ Lebenszyklus (T-5)

    /**
     * "Zurücksetzen" (AK-42): Bei bestehender Zuordnung mit mindestens einem Platzhalter wird vorher mit
     * {@link Rueckfrage#ZURUECKSETZEN} gefragt (Standard "Abbrechen"); bei "Abbrechen" ändert sich nichts. Dann gilt
     * die Zwischenablage-Regel für das Zurücksetzen (nur nachweislich eigener Inhalt wird geleert, kein Dialog), und
     * der gesamte Zustand mit allem Klartext wird verworfen: Die Sitzung ist wie neu erzeugt (Statusmeldung
     * {@link Texte#BEREIT}). Konnte die Zwischenablage nicht geleert werden, bleibt nur der Vergleichstext der Regel
     * für einen späteren Versuch erhalten, und die Meldung ist {@link Texte#ZWISCHENABLAGE_NICHT_GELEERT}. Während
     * eines Scans ist die Funktion gesperrt und tut nichts.
     *
     * @return {@code true}, wenn zurückgesetzt wurde; die Ansicht leert dann ihre Felder
     */
    public boolean zuruecksetzenAnfordern() {
        if (zustand == ScanZustand.LAEUFT) {
            return false;
        }
        if (hatZuordnungMitPlatzhalter() && !dialoge.bestaetigt(Rueckfrage.ZURUECKSETZEN)) {
            return false;
        }
        boolean geleert = eigenenZwischenablageInhaltLeeren();
        verwerfeAlles();
        meldung = geleert ? Texte.BEREIT : Texte.ZWISCHENABLAGE_NICHT_GELEERT;
        informieren();
        return true;
    }

    /**
     * Der einzige Einstieg zum Beenden (AK-43, Schaltfläche, Fenster schließen und Alt+F4 laufen alle hierher), in
     * fester Reihenfolge: (1) Rückfrage {@link Rueckfrage#BEENDEN} bei bestehender Zuordnung mit mindestens einem
     * Platzhalter (Standard "Abbrechen"; dann {@code false}, nichts ändert sich); (2) Zwischenablage-Regel für das
     * Beenden (V-2: geleert wird alles, was nicht nachweislich fremd ist); scheitert das Leeren, folgt die Rückfrage
     * {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}: "Erneut versuchen" wiederholt (unbegrenzt oft), "Trotzdem
     * beenden" fährt fort; (3) aller Klartext der Sitzung wird verworfen. Die Methode beendet nichts selbst.
     *
     * @return {@code true}, wenn das Programm beendet werden darf
     */
    public boolean beendenAnfordern() {
        if (hatZuordnungMitPlatzhalter() && !dialoge.bestaetigt(Rueckfrage.BEENDEN)) {
            return false;
        }
        while (!zwischenablage.beimBeendenLeeren()) {
            if (!dialoge.erneutVersuchen(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT)) {
                zwischenablage.vergleichstextVerwerfen();
                break;
            }
        }
        verwerfeAlles();
        informieren();
        return true;
    }

    private boolean hatZuordnungMitPlatzhalter() {
        return zuordnung != null && zuordnung.size() >= 1;
    }

    /**
     * Verwirft allen Klartext und stellt den Startzustand her: Funde, Text, Live-Ergebnis, aktive und zuletzt
     * kopierte Zuordnung (Originalwerte), Rückübersetzung. Ein laufender Scan gilt als überholt, sein Rückruf wird
     * ignoriert. Der Vergleichstext der Zwischenablage-Regel bleibt der Regel überlassen.
     */
    private void verwerfeAlles() {
        verwerfeFunde();
        zustand = ScanZustand.KEIN_SCAN;
        vorherigerZustand = ScanZustand.KEIN_SCAN;
        generation++;
        laufenderAuftrag = null;
        zuordnung = null;
        zuordnungEingefroren = false;
        zuletztKopierteZuordnung = null;
        antwortLeer = true;
        rueckuebersetzung = null;
        meldung = Texte.BEREIT;
    }

    /** Zwischenablage-Regel für das Zurücksetzen, siehe {@link ZwischenablageRegel#eigenenInhaltLeeren()}. */
    boolean eigenenZwischenablageInhaltLeeren() {
        return zwischenablage.eigenenInhaltLeeren();
    }

    /** Legt Text ab und merkt ihn; scheitert das, melden Statusleiste und Dialog es ohne Textinhalt. */
    private boolean ablegen(String inhalt) {
        if (!zwischenablage.ablegen(inhalt)) {
            meldung = Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR;
            informieren();
            dialoge.fehler(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR);
            return false;
        }
        return true;
    }

    private boolean keinFundAngewaehlt() {
        return angewaehlteZahl() == 0;
    }

    private int angewaehlteZahl() {
        int anzahl = 0;
        for (boolean b : angewaehlt) {
            if (b) {
                anzahl++;
            }
        }
        return anzahl;
    }

    // ------------------------------------------------------------------ Anzeige

    /** Aktivierung jeder Funktion mit Sperrgrund für gesperrte (AK-4, AK-10, AK-30, AK-48). */
    public Bedienzustand bedienzustand() {
        Set<Funktion> aktiv = EnumSet.noneOf(Funktion.class);
        Map<Funktion, String> sperrgrund = new EnumMap<>(Funktion.class);
        boolean laeuft = zustand == ScanZustand.LAEUFT;
        boolean gueltig = zustand == ScanZustand.GUELTIG;
        String ohneScan = sperrgrundOhneGueltigenScan();

        schalte(aktiv, sperrgrund, Funktion.SCANNEN, !laeuft, Texte.SCAN_LAEUFT);
        schalte(aktiv, sperrgrund, Funktion.EINGABE_BEARBEITEN, !laeuft, Texte.SCAN_LAEUFT);
        boolean hatFunde = gueltig && !funde.isEmpty();
        String ohneFunde = gueltig ? Texte.KEINE_FUNDE : ohneScan;
        schalte(aktiv, sperrgrund, Funktion.ALLE_ANWAEHLEN, hatFunde, ohneFunde);
        schalte(aktiv, sperrgrund, Funktion.FUND_UMSCHALTEN, hatFunde, ohneFunde);
        schalte(aktiv, sperrgrund, Funktion.MARKIERUNG_HINZUFUEGEN, gueltig, ohneScan);
        schalte(aktiv, sperrgrund, Funktion.FUER_KI_KOPIEREN, gueltig, ohneScan);
        // Rückübersetzen: im aktuellen Scan wurde für die KI kopiert (eingefrorene Zuordnung, auch ohne Platzhalter,
        // S-2) und die Antwort ist nicht leer (AK-36).
        String ohneAntwort = laeuft ? Texte.SCAN_LAEUFT : zuordnung == null ? Texte.ERST_SCANNEN
                : !zuordnungEingefroren ? Texte.ERST_KOPIEREN : Texte.ANTWORT_FEHLT;
        schalte(aktiv, sperrgrund, Funktion.ZURUECKUEBERSETZEN, zuordnungEingefroren && !laeuft && !antwortLeer,
                ohneAntwort);
        boolean hatErgebnis = rueckuebersetzung != null && !rueckuebersetzung.text().isEmpty();
        String ohneErgebnis = rueckuebersetzung == null ? Texte.ERST_ZURUECKUEBERSETZEN : Texte.ERGEBNIS_LEER;
        schalte(aktiv, sperrgrund, Funktion.ERGEBNIS_KOPIEREN, hatErgebnis && !laeuft,
                laeuft ? Texte.SCAN_LAEUFT : ohneErgebnis);
        schalte(aktiv, sperrgrund, Funktion.ZURUECKSETZEN, !laeuft, Texte.SCAN_LAEUFT);
        schalte(aktiv, sperrgrund, Funktion.BEENDEN, true, "");
        return new Bedienzustand(aktiv, sperrgrund);
    }

    /** Statusmeldung links und Zuordnungsanzeige rechts (AK-4, AK-20, AK-40). */
    public Statusleiste statusleiste() {
        String anzeige = zuordnung == null ? Texte.KEINE_ZUORDNUNG
                : zuordnungEingefroren ? Texte.zuordnung(zuordnung.size()) : Texte.ZUORDNUNG_ERST_NACH_KOPIEREN;
        return new Statusleiste(meldung, anzeige);
    }

    /** Nur Zustand und Zahlen, nie Text. */
    @Override
    public String toString() {
        return "Sitzung[zustand=" + zustand + ", funde=" + funde.size() + ", zuordnung="
                + (zuordnung == null ? 0 : zuordnung.size()) + "]";
    }

    // ------------------------------------------------------------------ intern

    private static void schalte(Set<Funktion> aktiv, Map<Funktion, String> sperrgrund, Funktion funktion,
                                boolean bedienbar, String grund) {
        if (bedienbar) {
            aktiv.add(funktion);
        } else {
            sperrgrund.put(funktion, grund);
        }
    }

    private String sperrgrundOhneGueltigenScan() {
        return switch (zustand) {
            case LAEUFT -> Texte.SCAN_LAEUFT;
            case VERALTET -> Texte.NEUER_SCAN_NOETIG;
            case KEIN_SCAN, GUELTIG -> Texte.ERST_SCANNEN;
        };
    }

    private Optional<ScanAuftrag> ablehnen(String grund) {
        meldung = grund;
        informieren();
        return Optional.empty();
    }

    private boolean istLaufenderAuftrag(ScanAuftrag auftrag) {
        return zustand == ScanZustand.LAEUFT && auftrag == laufenderAuftrag;
    }

    private void zurueckZumVorherigenZustand() {
        zustand = vorherigerZustand;
        laufenderAuftrag = null;
        meldung = switch (zustand) {
            case VERALTET -> Texte.NEUER_SCAN_NOETIG;
            case GUELTIG -> zaehlung(funde, angewaehlt) + auswahlHinweis(live);
            case KEIN_SCAN, LAEUFT -> Texte.BEREIT;
        };
    }

    private void verwerfeFunde() {
        text = "";
        funde = List.of();
        angewaehlt = new boolean[0];
        live = Live.LEER;
    }

    /**
     * Berechnet Live-Ergebnis, Zeilen und Meldung für die Auswahl und schreibt sie erst danach fest. Scheitert die
     * Berechnung, bleibt der bisherige Zustand vollständig erhalten und die Ausnahme erreicht den Aufrufer.
     */
    private void auswahlUebernehmen(boolean[] neueAuswahl) {
        Live neuesLive = berechne(text, funde, neueAuswahl);
        String neueMeldung = zaehlung(funde, neueAuswahl) + auswahlHinweis(neuesLive);
        angewaehlt = neueAuswahl;
        live = neuesLive;
        zuordnungFolgtLive(neuesLive);
        meldung = neueMeldung;
        informieren();
    }

    /** Die aktive Zuordnung folgt dem Live-Ergebnis, solange sie nicht eingefroren ist (A-2). */
    private void zuordnungFolgtLive(Live neuesLive) {
        if (!zuordnungEingefroren) {
            zuordnung = neuesLive.zuordnung();
        }
    }

    /**
     * Hinweis für die Statusmeldung, wenn die eingefrorene Zuordnung nicht mehr zur Live-Auswahl passt (A-2);
     * sonst leer. Nur ein fester Text.
     */
    private String auswahlHinweis(Live neuesLive) {
        return zuordnungEingefroren && !neuesLive.zuordnung().equals(zuordnung)
                ? " " + Texte.AUSWAHL_NACH_KOPIEREN_GEAENDERT : "";
    }

    /** Berechnet alles, was aus Text, Funden und Auswahl folgt, ohne den Zustand zu ändern. */
    private Live berechne(String eingabe, List<Finding> alleFunde, boolean[] auswahl) {
        List<Finding> gewaehlt = new ArrayList<>();
        for (int i = 0; i < alleFunde.size(); i++) {
            if (auswahl[i]) {
                gewaehlt.add(alleFunde.get(i));
            }
        }
        PseudonymizationResult ergebnis = pseudonymizer.pseudonymize(eingabe, gewaehlt);
        Map<FundSchluessel, String> platzhalter = PlatzhalterJeFund.aus(ergebnis.mapping());
        List<FundZeile> neueZeilen = new ArrayList<>(alleFunde.size());
        for (int i = 0; i < alleFunde.size(); i++) {
            Finding fund = alleFunde.get(i);
            String zeilenPlatzhalter = null;
            if (auswahl[i]) {
                zeilenPlatzhalter = platzhalter.get(new FundSchluessel(fund.type().name(), fund.originalValue()));
                if (zeilenPlatzhalter == null) {
                    throw new IllegalStateException("Kein Platzhalter für einen angewählten Fund");
                }
            }
            neueZeilen.add(new FundZeile(fund, auswahl[i], zeilenPlatzhalter));
        }
        return new Live(ergebnis.text(), ergebnis.mapping(), List.copyOf(neueZeilen));
    }

    private static String zaehlung(List<Finding> alleFunde, boolean[] auswahl) {
        int gewaehlt = 0;
        int fraglich = 0;
        for (int i = 0; i < alleFunde.size(); i++) {
            if (auswahl[i]) {
                gewaehlt++;
            }
            if (ConfidenceLevel.of(alleFunde.get(i).confidence()) == ConfidenceLevel.FRAGLICH) {
                fraglich++;
            }
        }
        return Texte.zaehlung(alleFunde.size(), gewaehlt, fraglich);
    }

    private void informieren() {
        for (Runnable b : List.copyOf(beobachter)) {
            b.run();
        }
    }
}
