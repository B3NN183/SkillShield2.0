package de.skillshield.ui.logik;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * <p>Stand T-2: Scan, Auswahl, Live-Ergebnis, Statusleiste und Bedienzustand. Manuelles Hinzufügen, Kopieren,
 * Rückübersetzung und die Rückfragen beim Zurücksetzen und Beenden folgen in T-3 bis T-5; bis dahin folgt die
 * aktive Zuordnung immer dem Live-Ergebnis.
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
    // Zwischenablage wird ab T-4 benutzt (Kopieren); der Konstruktor bleibt dadurch stabil.
    private final Zwischenablage zwischenablage;
    private final Dialoge dialoge;
    private final Pseudonymizer pseudonymizer = new Pseudonymizer();
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
    private PlaceholderMapping zuordnung;
    private String meldung = Texte.BEREIT;

    /**
     * @param scanner        Scan-Funktion, threadsicher (im Test austauschbar)
     * @param zwischenablage Zwischenablage
     * @param dialoge        Rückfragen und Fehlerdialog
     */
    public Sitzung(Function<String, List<Finding>> scanner, Zwischenablage zwischenablage, Dialoge dialoge) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.zwischenablage = Objects.requireNonNull(zwischenablage, "zwischenablage");
        this.dialoge = Objects.requireNonNull(dialoge, "dialoge");
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
        if (istLeer(eingabe)) {
            return ablehnen(Texte.TEXT_FEHLT);
        }
        if (eingabe.length() > MAX_ZEICHEN) {
            return ablehnen(Texte.zuLang(MAX_ZEICHEN));
        }
        if (zuordnung != null && zuordnung.size() >= 1 && !dialoge.bestaetigt(Rueckfrage.NEUER_SCAN)) {
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

    /** Text für die KI (Bereich 3): der Text mit den angewählten Funden als Platzhalter; leer ohne gültigen Scan. */
    public String textFuerKi() {
        return zustand == ScanZustand.GUELTIG ? live.text() : "";
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
        // Bis T-4 gibt es keine KI-Antwort und keine Rückübersetzung: beide Funktionen sind gesperrt.
        schalte(aktiv, sperrgrund, Funktion.ZURUECKUEBERSETZEN, false,
                zuordnung == null ? Texte.ERST_SCANNEN : Texte.ANTWORT_FEHLT);
        schalte(aktiv, sperrgrund, Funktion.ERGEBNIS_KOPIEREN, false, Texte.ERST_ZURUECKUEBERSETZEN);
        schalte(aktiv, sperrgrund, Funktion.ZURUECKSETZEN, !laeuft, Texte.SCAN_LAEUFT);
        schalte(aktiv, sperrgrund, Funktion.BEENDEN, true, "");
        return new Bedienzustand(aktiv, sperrgrund);
    }

    /** Statusmeldung links und Zuordnungsanzeige rechts (AK-4, AK-20, AK-40). */
    public Statusleiste statusleiste() {
        String anzeige = zuordnung == null ? Texte.KEINE_ZUORDNUNG : Texte.zuordnung(zuordnung.size());
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

    private static boolean istLeer(String eingabe) {
        return eingabe.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
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
            case GUELTIG -> zaehlung(funde, angewaehlt);
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
        String neueMeldung = zaehlung(funde, neueAuswahl);
        angewaehlt = neueAuswahl;
        live = neuesLive;
        zuordnung = neuesLive.zuordnung();
        meldung = neueMeldung;
        informieren();
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
