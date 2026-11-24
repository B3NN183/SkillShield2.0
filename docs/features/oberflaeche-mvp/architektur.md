# Architektur: Oberfläche (MVP)

Feature-Kurzname: `oberflaeche-mvp`
Status: **Entwurf zur Freigabe**
Grundlage: `anforderung.md` dieses Features (D-1 bis D-16, AK-1 bis AK-57, G-1 bis G-7, OF-1 bis OF-5), Plan.md
Kap. 3, 4, 5, 10, 11, README.md (Modulrichtung, Enforcer, OWASP-Check).
Code-Stand: Kern vollständig in `skillshield-core` (`TextScanner`, `ConflictResolver`, `Pseudonymizer`,
`PlaceholderMapping`, `PlaceholderSyntax`, `Reidentifier`, `Finding`, `FindingType`, `ConfidenceLevel`);
`skillshield-ui` und `skillshield-app` enthalten nur `package-info.java` und den Modultest.

Abkürzungen: `ui/main/…` = `skillshield-ui/src/main/java/de/skillshield/ui/`, `ui/test/…` =
`skillshield-ui/src/test/java/de/skillshield/ui/`, `app/main/…` und `app/test/…` entsprechend.

---

## 0. Zusammenfassung in normaler Sprache

**Was gebaut wird.** Das erste Programm mit Fenster. Man fügt einen Text ein, drückt „Scannen“, sieht die gefundenen
Stellen in einer Tabelle, kann einzelne abwählen oder fehlende Stellen (z. B. Namen) selbst markieren, kopiert den
pseudonymisierten Text für die KI und kann die Antwort der KI wieder mit den Originalwerten lesbar machen.

**Wie es gestartet wird.** `mvn verify` erzeugt eine einzelne Datei `skillshield-app\target\skillshield.jar` und daneben
`SkillShield.cmd`. Ein Doppelklick auf `SkillShield.cmd` startet das Programm; alternativ der Befehl
`java -jar skillshield-app\target\skillshield.jar`. Voraussetzung ist eine installierte Java-21-Laufzeit.

**Wie es geprüft wird.** Fast alles, was das Programm „denkt“ (welche Schaltfläche gesperrt ist, was in der Statusleiste
steht, was in die Zwischenablage kommt), steckt in Klassen ohne Fenster und wird automatisch getestet. Was man nur mit
Augen prüfen kann (Aussehen, sichtbarer Fokus, Vorlesen durch einen Screenreader), steht am Ende in einer kurzen
Sichtprüf-Liste für Sie (Kap. 9).

**Neu im Produkt.** Die erste Fremdbibliothek: FlatLaf (Aussehen der Oberfläche, Apache-2.0-Lizenz, keine weiteren
Abhängigkeiten). Sie wird fest auf eine Version gepinnt und mit dem OWASP-Check geprüft.

**Drei Punkte zur Freigabe** (Kap. 8): (1) Eine kleine Build-Änderung im Kern-Modul (nur `pom.xml`, kein Code), damit
die Tests der Oberfläche die vorhandenen synthetischen KV-Nummern des Kerns benutzen können. (2) Für die
Rückübersetzung gilt die Zuordnung des zuletzt **kopierten** Standes, nicht die nach dem Kopieren geänderte Auswahl –
sonst könnte eine KI-Antwort mit falschen Originalwerten gefüllt werden. (3) Rückfragen haben immer die Schaltflächen
„Ja“ und „Abbrechen“ (auch beim Beenden).

---

## 1. Lösungsansatz

Die Oberfläche wird in zwei strikt getrennte Schichten geteilt:

1. **Fachlogik der Oberfläche** (`de.skillshield.ui.logik`): reines Java ohne `javax.swing`/`java.awt`. Die zentrale
   Klasse `Sitzung` hält den gesamten Zustand eines Arbeitsgangs (Scan-Zustand, Fundliste mit Auswahl, Live-Ergebnis
   der Pseudonymisierung, aktive Zuordnung, zuletzt kopierter Text) und berechnet daraus alles, was die Oberfläche
   anzeigt: Tabellenzeilen inkl. Platzhalter je Fund, Text für Bereich 3, Statusleiste, Aktivierung jeder Funktion mit
   Sperrgrund, Rückfragen, Rückübersetzung. Zwischenablage und Dialoge sind zwei kleine Schnittstellen
   (`Zwischenablage`, `Dialoge`), die im Test durch Attrappen ersetzt werden. Damit sind alle [L]-Kriterien ohne
   Bildschirm in `mvn verify` testbar.
2. **Swing-Schicht** (`de.skillshield.ui.swing`): dünne Ansicht. `HauptPanel` baut die vier Bereiche und die
   Statusleiste, leitet jedes Ereignis an `Sitzung` weiter und zeichnet nach jeder Änderung den Zustand neu
   (Beobachter-Muster mit einem einzigen `Runnable`). Der Scan läuft in einem `SwingWorker`. `HauptFenster` (JFrame)
   umschließt nur das Panel. Weil `HauptPanel` ein `JPanel` ist, lassen sich Komponentenbaum, Tastenbelegung,
   Mnemonics, zugängliche Namen, Fokusreihenfolge und Aktivierung auch headless prüfen; nur `JFrame`/`JDialog`
   brauchen einen Bildschirm.

`skillshield-app` verdrahtet: Startklasse `SkillShieldApp` (Locale, FlatLaf, deutsche Dialogtexte, zentraler
Fehler-Handler, Fenster, Beenden per `System.exit(0)`). Der Build erzeugt per `maven-shade-plugin` eine einzelne
ausführbare JAR-Datei und kopiert ein Startskript daneben.

Der Kern wird ausschließlich über seine öffentliche API benutzt. Beide kritischen Fragen aus dem Auftrag sind ohne
Kernänderung lösbar (Kap. 3.3).

---

## 2. Betroffene Module und Pakete

| Modul | Paket / Datei | Änderung |
|-------|---------------|----------|
| Wurzel | `pom.xml` | Properties `flatlaf.version`, `maven-shade-plugin.version`; `dependencyManagement` für FlatLaf; `pluginManagement` für Shade |
| `skillshield-core` | `pom.xml` | **nur** zusätzliche Execution `test-jar` des `maven-jar-plugin` (Abweichung A-1). Kein Java-Code. |
| `skillshield-ui` | `pom.xml` | Testabhängigkeit auf `skillshield-core:test-jar`; Enforcer-Positivliste; Surefire headless |
| `skillshield-ui` | `de.skillshield.ui.logik` (neu) | Fachlogik der Oberfläche |
| `skillshield-ui` | `de.skillshield.ui.swing` (neu) | Swing-Ansicht und Adapter |
| `skillshield-app` | `pom.xml` | FlatLaf (Laufzeit); Enforcer-Positivliste; Surefire headless; Shade; Startskript |
| `skillshield-app` | `de.skillshield.app` | `SkillShieldApp` (Start, Verdrahtung) |
| `skillshield-app` | `src/main/start/SkillShield.cmd` (neu) | Startskript für Doppelklick |
| Doku | `README.md` | Abschnitt „Programm starten“, OWASP-Stand mit FlatLaf |

`skillshield-io` und `skillshield-vault` bleiben unberührt. Abhängigkeitsrichtung unverändert (ui → core; app → ui,
core); FlatLaf nur in `app`.

### 2.1 Paket `de.skillshield.ui.logik` (keine Imports aus `javax.swing`, `java.awt`, durch Test gesichert)

```java
/** Zustand und Regeln eines Arbeitsgangs. Nicht threadsicher: alle Aufrufe auf einem Thread (EDT). */
public final class Sitzung {
    public static final int MAX_ZEICHEN = 1_000_000;                       // D-10, gezählt als String.length()

    public Sitzung(Function<String, List<Finding>> scanner, Zwischenablage zwischenablage, Dialoge dialoge);
    public static Sitzung standard(Zwischenablage zwischenablage, Dialoge dialoge);   // TextScanner.standard()::scan
    public void beobachten(Runnable beobachter);                           // nach jeder Zustandsänderung gerufen

    // Scan (T-2)
    public Optional<ScanAuftrag> scanAnfordern(String eingabe);   // prüft leer/zu lang/Rückfrage, setzt LAEUFT
    public void scanAbgeschlossen(ScanAuftrag auftrag, List<Finding> funde);
    public void scanFehlgeschlagen(ScanAuftrag auftrag, Throwable fehler);
    public void eingabeGeaendert();                               // GUELTIG -> VERALTET (D-4)
    public ScanZustand scanZustand();

    // Funde prüfen (T-2, T-3)
    public List<FundZeile> zeilen();                              // unveränderlich, nach Position
    public void anwaehlen(int zeile, boolean angewaehlt);
    public void alleAnwaehlen();                                  // es gibt KEIN alleAbwaehlen (D-2)
    public void markierungHinzufuegen(int start, int ende);       // T-3, D-7 bis D-9
    public String textFuerKi();                                   // "" ohne gültigen Scan

    // Kopieren und Rückübersetzung (T-4)
    public void fuerKiKopieren();                                 // ganzer Text aus Bereich 3, friert Zuordnung ein
    public void textKopieren(String teil);                        // Strg+C/X aus Eingabe, KI-Antwort, Ergebnis
    public void kiAntwortGeaendert(boolean leer);
    public void zurueckuebersetzen(String antwort);
    public Optional<Rueckuebersetzung> rueckuebersetzung();
    public void ergebnisKopieren();

    // Lebenszyklus (T-5)
    public boolean zuruecksetzenAnfordern();                      // true: zurückgesetzt, Ansicht leert ihre Felder
    public boolean beendenAnfordern();                            // true: Zwischenablage-Regel angewandt, beenden

    // Anzeige
    public Bedienzustand bedienzustand();
    public Statusleiste statusleiste();
    @Override public String toString();                           // nur Zustand und Zahlen, nie Text
}

public enum ScanZustand { KEIN_SCAN, LAEUFT, GUELTIG, VERALTET }

/** Momentaufnahme für den Hintergrund-Scan; threadsicher, unveränderlich. toString ohne Text. */
public final class ScanAuftrag { public List<Finding> ausfuehren(); }

/** Tabellenzeile. platzhalter ist null, wenn nicht angewählt. toString ohne Fundtext. */
public record FundZeile(Finding fund, boolean angewaehlt, String platzhalter) { }

public enum Funktion { SCANNEN, EINGABE_BEARBEITEN, ALLE_ANWAEHLEN, FUND_UMSCHALTEN, MARKIERUNG_HINZUFUEGEN,
                       FUER_KI_KOPIEREN, ZURUECKUEBERSETZEN, ERGEBNIS_KOPIEREN, ZURUECKSETZEN, BEENDEN }

/** Aktivierung je Funktion und deutscher Sperrgrund (Tooltip, AK-48). */
public record Bedienzustand(Set<Funktion> aktiv, Map<Funktion, String> sperrgrund) {
    public boolean istAktiv(Funktion f);
}

public record Statusleiste(String meldung, String zuordnung) { }

/** Ergebnis der Rückübersetzung für Bereich 4. toString ohne Text. */
public record Rueckuebersetzung(String text, List<UnresolvedPlaceholder> offen, int platzhalterGesamt) {
    public List<String> listenzeilen();   // "[[KVNR_7]] an Position 121: unbekannt" bzw. Sammeltext AK-38
}

public interface Zwischenablage {
    void setzeText(String text);          // genau ein reiner Text
    Optional<String> leseText();          // leer, wenn kein Text oder nicht lesbar; wirft nie
    void leeren();                        // wirft nie
}

public interface Dialoge {
    boolean bestaetigt(Rueckfrage frage); // true nur bei "Ja"; Standard "Abbrechen"
    void fehler(String text);             // fester Text + Klassenname, OK-Schaltfläche
}

public enum Rueckfrage {                  // Wortlaute aus AK-32, AK-41, AK-42, AK-43
    UNVERAENDERT_KOPIEREN, NEUER_SCAN, ZURUECKSETZEN, BEENDEN;
    public String text();
}

/** Manuelles Hinzufügen nach D-9 als reine Funktion. */
final class ManuellerFund {
    static Ergebnis hinzufuegen(String text, List<FundZeile> bestand, int start, int ende);
    record Ergebnis(List<FundZeile> zeilen, int hinzugefuegt, int uebersprungen, int ersetzt, boolean abgelehnt) { }
}

/** Platzhalter je Fund aus der Zuordnung (Kap. 3.3). */
final class PlatzhalterJeFund { static Map<FundSchluessel, String> aus(PlaceholderMapping m); }

public final class Beschriftungen {       // D-13
    public static String typ(Finding f);  // Pfad, E-Mail, IP-Adresse, Internetadresse, IK, LANR, KVNR, BSNR, Manuell
    public static String stufe(ConfidenceLevel l);  // sicher, wahrscheinlich, fraglich
}

public final class Texte { /* alle festen deutschen Texte und Formatierer, Kap. 4 */ }

/** Zentraler Fehler-Handler (AK-50): zeigt festen Text + Klassennamen, schreibt nichts nach System.out/err. */
public final class Fehlerbehandlung implements Thread.UncaughtExceptionHandler {
    public Fehlerbehandlung(Consumer<String> anzeige);
    public static String meldung(Throwable t);   // Texte.FEHLER + " (" + t.getClass().getName() + ")"
}
```

### 2.2 Paket `de.skillshield.ui.swing`

```java
public final class HauptPanel extends JPanel {               // vier Bereiche + Statusleiste, headless baubar
    public HauptPanel(Sitzung sitzung, Zwischenablage zwischenablage);
    // paketintern für Tests: Zugriff auf Komponenten per Name (setName), z. B. "eingabe", "scannen", "funde" ...
}
final class FundTabellenModell extends AbstractTableModel { } // Spalten Übernehmen|Typ|Fundtext|Stufe|Platzhalter
final class ScanStarter { void starte(Sitzung s, ScanAuftrag a); } // SwingWorker; Ergebnis auf dem EDT zurück
final class Textaktionen { }   // Einfügen nur Text, Kopieren/Ausschneiden über Sitzung, DnD nur Text
final class Tastatur { }       // Kürzel D-14, Mnemonics, Tab-Tasten, Leertaste in Tabelle (T-7)
final class Fokusreihenfolge extends FocusTraversalPolicy { }  // feste Liste nach AK-46 (T-7)
public final class AwtZwischenablage implements Zwischenablage {
    public AwtZwischenablage(Clipboard clipboard);           // System-Zwischenablage oder lokale im Test
}
public final class SwingDialoge implements Dialoge {
    public SwingDialoge(Supplier<Component> eltern);
    static Object[] optionen(Rueckfrage f);                   // {"Ja", "Abbrechen"}, Standard "Abbrechen"
}
public final class HauptFenster extends JFrame {              // Titel "SkillShield", Mindestgröße, Schließen -> Sitzung
    public HauptFenster(HauptPanel panel, Sitzung sitzung, Runnable beenden);
}
```

### 2.3 Paket `de.skillshield.app`

```java
public final class SkillShieldApp {
    public static void main(String[] args);                    // System.exit(starte(...)) nur im Fehlerfall
    static int starte(PrintStream fehlerausgabe);              // 0 = Fenster gestartet, 2 = kein Bildschirm
    static void oberflaecheEinrichten();                       // FlatLaf, deutsche UIManager-Texte
}
```

---

## 3. Fachliche Regeln der Oberflächenlogik (verbindlich für die Engineers)

### 3.1 Scan-Zustand und Gültigkeit (D-4, D-10, D-11)

| Zustand | Bedeutung | Übergänge |
|---------|-----------|-----------|
| `KEIN_SCAN` | Start, nach Zurücksetzen | `scanAnfordern` → `LAEUFT` |
| `LAEUFT` | Hintergrund-Scan | `scanAbgeschlossen` → `GUELTIG`; `scanFehlgeschlagen` → vorheriger Zustand |
| `GUELTIG` | Funde passen zum Text | `eingabeGeaendert` → `VERALTET`; `scanAnfordern` → `LAEUFT` |
| `VERALTET` | Text nach Scan geändert | `scanAnfordern` → `LAEUFT` |

- `scanAnfordern(eingabe)` prüft in dieser Reihenfolge: (1) leer oder nur Leerraum → Meldung „Bitte zuerst Text
  eingeben.“, nichts ändert sich (AK-6); (2) `length() > MAX_ZEICHEN` → Meldung mit Obergrenze, nichts ändert sich
  (AK-7); (3) aktive Zuordnung mit ≥ 1 Platzhalter → Rückfrage `NEUER_SCAN`, bei „Abbrechen“ ändert sich nichts
  (AK-41); (4) Zustand `LAEUFT`, Rückgabe des Auftrags mit Text-Schnappschuss und Generationszähler.
- `scanAbgeschlossen`: Funde unverändert übernehmen (AK-8, keine Filterung, AK-53), alle angewählt (AK-9),
  Live-Ergebnis berechnen, **erst danach** den Zustand übernehmen (Berechnen-dann-Festschreiben; scheitert die
  Pseudonymisierung, gilt `scanFehlgeschlagen`). Neue aktive Zuordnung ersetzt die alte (AK-41). Kam während des
  Scans `eingabeGeaendert` (nur theoretisch, das Eingabefeld ist gesperrt) oder ist die Generation veraltet, wird das
  Ergebnis verworfen.
- `scanFehlgeschlagen`: Dialog mit festem Text und Klassennamen (AK-50), Eingabe, Funde, Zuordnung und Zustand wie vor
  dem Scan. **Auslegung AK-30/AK-50:** War vorher ein gültiger Scan zum selben Text vorhanden, bleibt er gültig
  (AK-50: „vorheriger Zustand bleibt unverändert“); „Für KI kopieren“ ist nach einem Fehlschlag nur dann gesperrt,
  wenn vorher kein gültiger Scan bestand.
- Während `LAEUFT` ist nur `BEENDEN` aktiv; Eingabefeld nicht bearbeitbar (D-11).

### 3.2 Auswahl und Live-Ergebnis (D-2, D-3)

- Nach jeder Auswahländerung und jedem manuellen Hinzufügen: `Pseudonymizer.pseudonymize(text, angewählteFunde)`
  (Funde in Positionsreihenfolge) → Text für Bereich 3 und Platzhalter je Zeile. Abgewählte Zeilen zeigen
  „nicht ersetzt“ (AK-14).
- Keine öffentliche Methode wählt nach Stufe/Konfidenz an oder ab, filtert oder blendet aus; es gibt kein
  `alleAbwaehlen` (AK-16, per Reflexionstest gesichert).

### 3.3 Prüfung „ohne Kernänderung“ (Auftrag, AK-57)

- **Manuelle Funde vom Typ TEXT:** `Finding.manual(FindingType.TEXT, wert, start, ende)` existiert öffentlich und
  liefert Konfidenz 1,0, Stufe SICHER, Quelle `manuell`. Der `Pseudonymizer` akzeptiert TEXT wie jeden Typ; Literalschutz
  und manuelle Funde teilen Nummerierung und Konsistenzregel (gleicher Typ + gleicher Wert → gleicher Platzhalter).
  **Geht ohne Kernänderung.**
- **Neuberechnung bei Auswahländerung:** `Pseudonymizer` ist zustandslos; ein erneuter Aufruf mit der neuen Auswahl
  liefert Text und Zuordnung (AK-15 verlangt genau „Ausgabe des Kerns für die verbleibende Auswahl“). **Geht ohne
  Kernänderung.**
- **Platzhalter je Fund (AK-14):** `PseudonymizationResult` liefert keine Zuordnung Fund → Platzhalter. Lösung ohne
  Kernänderung: Der Kern vergibt nach seiner Konsistenzregel für jedes Paar (Typ, Originalwert) genau einen
  Platzhalter. `PlatzhalterJeFund` durchläuft `mapping.placeholders()`, liest den Wert per `resolve` und den Typ aus dem
  Präfix `"[[" + typ.name() + "_"` (Typnamen enthalten nie `_`, daher eindeutig) und baut eine Tabelle
  (Typ, Wert) → Platzhalter. Aufwand O(Zahl der Platzhalter) je Neuberechnung. Die Nummerierung wird **nicht**
  nachgebaut.
- **„Antwort enthält keine Platzhalter“ (AK-38):** `PlaceholderSyntax.tokenize(antwort, 0, len, mapping.typeNames())`
  ist öffentlich; null Tokens → Sammeltext.
- **Hintergrund-Scan (OF-2):** `TextScanner` hält nur eine unveränderliche Erkennerliste; alle Felder der Erkenner sind
  `final` und unveränderlich (geprüft: nur Konstanten, vorkompilierte Muster, ein statisch befülltes Nur-Lese-Feld in
  `BsnrRegionalcodes`). Es läuft ohnehin höchstens ein Scan gleichzeitig (Schaltfläche gesperrt), `SwingWorker` sorgt
  für sichere Übergabe zwischen den Threads. **Geht ohne Kernänderung.**

### 3.4 Manuelles Hinzufügen (D-7 bis D-9) – Algorithmus `ManuellerFund`

1. Nur bei `GUELTIG` (sonst Funktion gesperrt, AK-26).
2. Surrogat-Erweiterung: Liegt `start` zwischen High- und Low-Surrogat, `start--`; liegt `ende` dazwischen, `ende++`
   (AK-27).
3. Leer (`start == ende`) oder jeder Codepunkt `Character.isWhitespace || Character.isSpaceChar` (deckt NBSP ab) →
   abgelehnt, Meldung „Bitte zuerst im Text eine Stelle markieren.“ (AK-26).
4. `wert = text.substring(start, ende)`.
5. **Markierte Stelle:** alle bestehenden Funde, die `[start, ende)` schneiden, werden entfernt; der neue Fund deckt
   `[min(start, Fundstarts), max(ende, Fundenden))` ab (D-9 b, AK-24), Wert = Textausschnitt dieser Vereinigung.
6. **Weitere Vorkommen** von `wert` (Schritt 4) von links nach rechts, nicht überlappend, zeichengenau
   (`String.indexOf`, ab Ende des vorigen Treffers): Treffer, der einen bestehenden Fund oder den neuen Fund aus
   Schritt 5 schneidet, wird übersprungen und gezählt (D-9 c, AK-25); Treffer, dessen Grenze ein Surrogatpaar teilt,
   wird nicht genommen (nur möglich bei einzelnem Low-Surrogat am Markierungsrand); sonst neuer manueller Fund.
7. Alle neuen Funde angewählt (D-9 e); bestehende Zeilen behalten ihren Auswahlzustand (AK-28); Liste nach Start
   sortiert, überlappungsfrei (Invariante, per Eigenschaftstest gegen `Pseudonymizer` gesichert).
8. Statusmeldung: Zahl hinzugefügter Stellen, ggf. ersetzte Funde (D-9 b) und übersprungene Stellen (D-9 c).

Hinweis: Hat Schritt 5 die Stelle erweitert, ist ihr Wert ein anderer als der der weiteren Vorkommen; diese erhalten
dann einen anderen `TEXT`-Platzhalter als die erweiterte Stelle. Alle Stellen sind geschützt; AK-23 (gleicher
Platzhalter) gilt für den Normalfall ohne Überschneidung.

### 3.5 Zuordnung, Zwischenablage, Rückübersetzung (D-5, D-6, Abweichung A-2)

- **Aktive Zuordnung** (für Rückübersetzung, Statusleiste, Rückfragen):
  - nach erfolgreichem Scan: Zuordnung des Live-Ergebnisses;
  - solange seit dem Scan nicht aus Bereich 3 kopiert wurde: folgt dem Live-Ergebnis;
  - „Für KI kopieren“ (Schaltfläche, Strg+Umschalt+C oder Strg+C/X in Bereich 3) **friert** sie auf das kopierte
    Ergebnis ein; spätere Auswahländerungen ändern sie nicht, bis erneut kopiert, neu gescannt oder zurückgesetzt wird.
    Weicht danach die Live-Zuordnung ab, nennt die Statusleiste das („Auswahl nach dem Kopieren geändert …“);
  - Eingabeänderung (D-4) lässt sie unverändert (AK-12);
  - endet bei neuem Scan, Zurücksetzen, Beenden.
- **Zwischenablage:** `Sitzung` merkt sich den zuletzt selbst gelegten Text (`letzterKopierterText`; Für-KI-Text,
  rückübersetzter Text oder ein per Strg+C/X aus einem Feld kopierter Ausschnitt). Bei Zurücksetzen und Beenden:
  `leseText()` liefert genau diesen Text → `leeren()`; sonst nichts (AK-34). `AwtZwischenablage` fängt jede
  `IllegalStateException`/`IOException`/`UnsupportedFlavorException` ab (Antwort auf OF-3, Kap. 7).
- **Strg+C/X in Bereich 3** löst die ganze Funktion „Für KI kopieren“ aus (mit Rückfrage AK-32), unabhängig von der
  Textmarkierung. Damit gibt es keinen zweiten Weg, unbemerkt unersetzten Text zu kopieren.
- **Tabelle:** Kopieren aus der Tabelle (Strg+C, Ziehen) ist abgeschaltet (`setTransferHandler(null)`, Aktion „copy“
  entfernt): Fundtexte kommen nie in die Zwischenablage (D-6).
- **Einfügen** (Strg+V, Umschalt+Einfg) in Eingabe und KI-Antwort liest über `Zwischenablage.leseText()` nur
  `DataFlavor.stringFlavor` und fügt per `replaceSelection` ein; Ablegen per Maus akzeptiert nur Text (AK-11).
- **Rückübersetzung:** `Reidentifier.restore(antwort, aktiveZuordnung)`; Ergebnis, Liste „Nicht zurückübersetzt“
  (Position ab 1 gezählt, Grund „unbekannt“/„beschädigt“), Statusmeldung mit Anzahl (AK-37, AK-38).

---

## 4. Texte (Klasse `Texte`)

Wortlaute aus der Anforderung sind verbindlich; „Vorschlag“ = Engineer darf sprachlich glätten, Tests prüfen gegen die
Konstanten. Kein Text enthält Eingabe-, Fund- oder Originalwerte; Zahlen und Platzhalter sind erlaubt (AK-49).

| Konstante | Wortlaut | Quelle |
|-----------|----------|--------|
| `FENSTERTITEL` | `SkillShield` | AK-1 |
| `BEREICH_1..4` | `1 Text eingeben`, `2 Funde prüfen`, `3 Für KI kopieren`, `4 KI-Antwort zurückübersetzen` | D-1 |
| `BEREIT`, `KEINE_ZUORDNUNG` | `Bereit`, `Keine Zuordnung` | AK-4 |
| `zuordnung(n)` | `Zuordnung: n Platzhalter (nur im Arbeitsspeicher)` (auch n = 0 nach einem Scan) | AK-40, AK-36 |
| `TEXT_FEHLT` | `Bitte zuerst Text eingeben.` | AK-6 |
| `ZU_LANG` | `Der Text ist zu lang (höchstens 1.000.000 Zeichen). Es wurde nicht gescannt.` | AK-7, Vorschlag |
| `SCAN_LAEUFT` | `Scan läuft …` | AK-10 |
| `NEUER_SCAN_NOETIG` | `Der Text wurde geändert. Bitte erneut scannen.` | AK-12, Vorschlag |
| `zaehlung(...)` | `5 Funde, 4 angewählt, 1 fraglich. 1 Fund wird nicht ersetzt.` (Einzahl/Mehrzahl) | AK-20, Vorschlag |
| `LEER_VOR_SCAN` | `Noch kein Scan. Text eingeben und Scannen wählen.` | AK-5 |
| `LEER_NACH_SCAN` | `Keine Fundstelle erkannt. Das heißt nicht, dass der Text keine personenbezogenen Daten enthält: Namen und Adressen werden noch nicht erkannt. Bitte prüfen Sie den Text und markieren Sie fehlende Stellen von Hand.` | D-16, Vorschlag |
| `HINWEIS_ERKENNUNG` | `Die Erkennung ist nicht vollständig. Namen und Adressen werden noch nicht erkannt. Bitte prüfen Sie den Text und markieren Sie fehlende Stellen von Hand.` | AK-21 |
| `HINWEIS_ZUORDNUNG` | `Die Rückübersetzung ist nur möglich, solange dieses Programm geöffnet ist und kein neuer Scan gestartet wurde. Die Zuordnung wird nicht gespeichert.` | AK-40 |
| `MARKIERUNG_FEHLT` | `Bitte zuerst im Text eine Stelle markieren.` | AK-26 |
| `hinzugefuegt(n, ersetzt, uebersprungen)` | z. B. `3 Stellen als Fund hinzugefügt. 1 Stelle übersprungen (überschneidet einen bestehenden Fund).` | AK-23/24/25, Vorschlag |
| `kopiert(n)`, `UNVERAENDERT_KOPIERT` | `Text für KI kopiert (n Stellen ersetzt)`, `Text unverändert kopiert` | AK-35 |
| `ANTWORT_FEHLT` | `Bitte die Antwort der KI einfügen.` | AK-36 |
| `ALLE_ZURUECK`, `KEINE_PLATZHALTER` | `Alle Platzhalter wurden zurückübersetzt.`, `Die Antwort enthält keine Platzhalter.` | AK-38 |
| `FEHLER` | `Beim Verarbeiten ist ein Fehler aufgetreten. Es wurde nichts kopiert.` | AK-50 |
| `ERST_SCANNEN` | `Erst Text scannen.` (Sperrgrund) | AK-48 |
| Rückfragen | Wortlaute AK-32, AK-41, AK-42 (Vorschlag: `Alle Eingaben und die Zuordnung werden gelöscht. KI-Antworten können danach nicht mehr zurückübersetzt werden. Fortfahren?`), AK-43 | |

Typ- und Stufenbeschriftung: `Beschriftungen` nach D-13; unbekannter Typname wird unverändert angezeigt; `TEXT` →
„Manuell“.

---

## 5. Wiederverwendung

| Vorhandener Typ | Verwendung |
|-----------------|-----------|
| `TextScanner.standard().scan(String)` | Scan (über `Function<String, List<Finding>>`, im Test austauschbar für Fehlerfälle) |
| `Finding`, `Finding.manual`, `Finding.SOURCE_MANUAL` | Fundzeilen, manuelle Funde |
| `FindingType` (inkl. `TEXT`) | Typbeschriftung, manuelle Funde |
| `ConfidenceLevel` | Stufenspalte, Zählung „fraglich“ |
| `Pseudonymizer`, `PseudonymizationResult`, `PlaceholderMapping` | Live-Ergebnis, Platzhalter je Fund, aktive Zuordnung |
| `PlaceholderSyntax.tokenize` | „Antwort enthält keine Platzhalter“ |
| `Reidentifier`, `ReidentificationResult`, `UnresolvedPlaceholder` | Rückübersetzung, Liste „Nicht zurückübersetzt“ |
| `InvalidFindingException` | nur als Fehlerfall (Klassenname im Dialog) |
| Test: `de.skillshield.core.SynthetischeKvNummern` (über core-test-jar) | KVNR/LANR/IK in Oberflächentests (AK-8, AK-9) |

---

## 6. Neue Abhängigkeiten

| Bibliothek | Version | Lizenz | Scope / Modul | Begründung |
|------------|---------|--------|---------------|-----------|
| `com.formdev:flatlaf` | **3.6.1** (fest gepinnt als `flatlaf.version`; ist sie beim Umsetzen nicht auf Maven Central oder hat sie einen bekannten Befund ab CVSS 7, nimmt der Engineer die neueste 3.x-Patchversion ohne Befund und nennt sie im Bericht) | Apache License 2.0 | compile, nur `skillshield-app` | Plan.md Kap. 4 (Swing + FlatLaf). Keine transitiven Abhängigkeiten. |
| `org.apache.maven.plugins:maven-shade-plugin` | **3.6.0** (gepinnt in `pluginManagement`) | Apache License 2.0 | Build-Plugin, nicht im Produkt | Eine einzelne startbare JAR-Datei (OF-1). |
| `de.skillshield:skillshield-core:test-jar` | Projektversion | intern | test, nur `skillshield-ui` | Synthetische KV-Nummern des Kerns statt frei erfundener Ziffern (Abweichung A-1). |

FlatLaf-Besonderheiten, die in T-1/T-8 berücksichtigt werden:
- Die JAR enthält native Windows-/Linux-/macOS-Bibliotheken, die FlatLaf sonst in ein Verzeichnis auf der Platte
  entpackt. Der Start setzt **vor** dem ersten FlatLaf-Zugriff `flatlaf.useNativeLibrary=false` → nichts wird
  entpackt (AK-44), Fensterrahmen kommt vom Betriebssystem.
- Apache 2.0 verlangt bei Weitergabe Lizenz- und NOTICE-Datei: Shade mit `ApacheLicenseResourceTransformer` und
  `ApacheNoticeResourceTransformer`; README nennt FlatLaf in einer Lizenzzeile.
- Multi-Release-JAR: Shade-Manifest mit `Multi-Release: true`, `module-info.class` (auch unter `META-INF/versions/*`)
  ausfiltern.

Der OWASP-Check prüft Laufzeitabhängigkeiten, also FlatLaf; Build-Plugins prüft er nicht (bekannte Grenze, README).

---

## 7. Antworten auf OF-1 bis OF-5

- **OF-1 (startbares Artefakt):** `maven-shade-plugin` erzeugt in `package` (also in `mvn verify`)
  `skillshield-app/target/skillshield.jar` mit `Main-Class: de.skillshield.app.SkillShieldApp` und allen Klassen
  inkl. FlatLaf. Daneben kopiert `maven-resources-plugin` `SkillShield.cmd`
  (`@start "" javaw -jar "%~dp0skillshield.jar"`). Start: Doppelklick auf `SkillShield.cmd` (funktioniert auch ohne
  Dateizuordnung für `.jar`) oder einzeilig `java -jar skillshield-app\target\skillshield.jar`. Für die Weitergabe
  genügt es, beide Dateien in einen Ordner zu kopieren. Ohne Bildschirm (z. B. bei den Prüf-Agenten) beendet sich die
  JAR mit Exit-Code 2 und dem festen Text „SkillShield benötigt eine grafische Oberfläche.“ – damit kann qa die JAR
  ohne Bildschirm als startbar nachweisen.
- **OF-2 (Hintergrund-Scan ohne Kernänderung):** Ja, siehe Kap. 3.3.
- **OF-3 (gesperrte Zwischenablage):** `AwtZwischenablage` versucht Lesen und Leeren je bis zu dreimal mit 50 ms
  Abstand (nur beim Beenden/Zurücksetzen, also ohne spürbare Wartezeit). Bleibt sie gesperrt oder ist der Inhalt
  kein Text: **nicht** leeren (der Inhalt ist nicht als der eigene nachweisbar; AK-34 verbietet das Löschen fremder
  Inhalte), kein Dialog, keine Ausgabe, Beenden läuft weiter. Benannte Grenze G-8 (Kap. 10). Abgelegt wird ein eigenes
  `Transferable`, das **nur** `DataFlavor.stringFlavor` anbietet (nicht `StringSelection`, das zusätzlich das
  veraltete `plainTextFlavor` anbietet; AK-33). Geleert wird durch Ablegen eines `Transferable` ohne Formate.
- **OF-4 (Java 21 auf den Zielrechnern):** Kann die Architektur nicht klären; bleibt offen bis zum
  Installationspaket. README nennt als Voraussetzung eine Temurin-21-JRE oder -JDK (`winget install
  EclipseAdoptium.Temurin.21.JRE`) und das Verhalten bei älterem Java (Fehlermeldung der JVM, englisch, vor dem Start
  des Programms – nicht abfangbar).
- **OF-5 (Screenreader):** Swing ist über die **Java Access Bridge** für NVDA/JAWS zugänglich; sie ist in Temurin
  enthalten, aber unter Windows standardmäßig **aus** (einschalten: `jabswitch -enable`, einmalig je Benutzer).
  FlatLaf ändert die Zugänglichkeit nicht. Das Programm setzt zugängliche Namen für alle Bedienelemente (T-7, headless
  getestet). Ob ein Screenreader sie tatsächlich vorliest, ist nur in der Sichtprüfung (Kap. 9, S-9) feststellbar;
  eine BITV-Abnahme ist nicht Teil des Features.

---

## 8. Abweichungen und Präzisierungen (zur Freigabe)

- **A-1 (Kern-`pom.xml`, AK-57 / Auftrag „Kern unverändert“):** `skillshield-core/pom.xml` erhält eine Execution
  `test-jar` des bereits gepinnten `maven-jar-plugin`. Kein Java-Code, keine Erkennungsregel, kein Test des Kerns
  ändert sich; die Kern-Tests laufen unverändert. Grund: AK-8 verlangt eine gültige KVNR, AK-9 Funde aller Stufen; ohne
  Zugriff auf `SynthetischeKvNummern` müssten im ui-Modul eigene Ziffernketten ab neun Stellen erfunden werden, was
  die Testdatenregel verbietet. Alternative ohne Kernänderung wäre, AK-8/AK-9 nur mit E-Mail/IP/Pfad zu testen –
  dann ist AK-8 nicht wörtlich erfüllt. **Empfehlung: A-1 freigeben.**
- **A-2 (D-3 Satz 3, Zuordnung zur aktuellen Auswahl):** Für die Tabelle und Bereich 3 gilt D-3 unverändert (live).
  Die **für die Rückübersetzung** benutzte Zuordnung wird beim Kopieren eingefroren (Kap. 3.5). Grund: Nach
  Abwahl eines Fundes nummeriert der Kern neu; aus `[[EMAIL_2]]` im kopierten Text würde `[[EMAIL_1]]` im neuen Stand.
  Würde die Rückübersetzung der Live-Auswahl folgen, setzte sie in eine KI-Antwort zum kopierten Text **falsche
  Originalwerte** ein (z. B. eine fremde E-Mail-Adresse oder KVNR) – stillschweigend. D-4 („damit eine KI-Antwort zum
  zuletzt kopierten Text noch zurückübersetzt werden kann“) stützt die Präzisierung. Vor dem ersten Kopieren folgt die
  Zuordnung der Auswahl (AK-37-Roundtrip ohne Kopieren funktioniert).
- **A-3 (AK-43 „Bei ‚Beenden‘“ / AK-2):** Alle Rückfragen haben die Schaltflächen „Ja“ und „Abbrechen“ (Standard
  „Abbrechen“, Esc = Abbrechen), auch die Beenden-Frage („… Beenden?“ → „Ja“). Fehlerdialog: „OK“. Grund: AK-2 nennt
  genau diese Beschriftungen.
- **A-4 (AK-43 „Menü Beenden“):** Kein Menü (D-1: ein Fenster, keine weiteren Elemente nötig); „Zurücksetzen“ und
  „Beenden“ sind Schaltflächen mit Mnemonic in einer Fußleiste über der Statusleiste.
- **A-5 (AK-30/AK-50):** Auslegung nach Kap. 3.1: ein fehlgeschlagener erneuter Scan lässt einen gültigen Scan zum
  selben Text bestehen.
- **A-6 (README, OWASP-Aufruf):** Mit der test-jar-Abhängigkeit kann der dokumentierte Aufruf
  `mvn clean compile org.owasp:dependency-check-maven:check -Dodc.skipTestScope=false` das noch nicht gepackte
  core-test-jar nicht auflösen. T-1 prüft beide dokumentierten Aufrufe; scheitert einer, wird in README die Phase auf
  `package -DskipTests` geändert (baut das test-jar, führt keine Tests aus) und das im Bericht genannt.

Gegenüber **Plan.md**: keine Abweichung. Die Statusleisten-Felder „Tresor“ und „Leak-Check“ (Kap. 10) entfallen wie
in der Anforderung festgelegt; FlatLaf und Swing wie Kap. 4; Modulrichtung wie Kap. 5.

---

## 9. Prüfkonzept für [U]-Kriterien

Grundsatz: Surefire läuft in `skillshield-ui` und `skillshield-app` **immer headless** (`systemPropertyVariables`
`java.awt.headless=${ui.headless}`, Vorgabe `true`). Damit beweist jeder `mvn verify`-Lauf AK-54, es blinken keine
Fenster während der Agentenläufe, und Tests mit echtem Fenster (`JFrame`) melden sich per
`Assumptions.assumeFalse(GraphicsEnvironment.isHeadless())` als **übersprungen** (AK-55). Wer einen Bildschirm hat,
kann sie mit `mvn verify -Dui.headless=false` ausführen. qa nennt die Zahl der übersprungenen Tests im Bericht.

| AK | Automatisch (headless) | Rest für die Sichtprüfung |
|----|------------------------|---------------------------|
| AK-1 | `FENSTERTITEL`; `starte()` ohne Bildschirm → Code 2; qa: `java -Djava.awt.headless=true -jar skillshield-app/target/skillshield.jar` → Code 2 und fester Text (beweist Main-Class und Startbarkeit ohne Maven); Fenstertest (übersprungen) | S-1 |
| AK-2 | alle `Texte`-Konstanten, Rückfrage-Optionen `{"Ja","Abbrechen"}`, UIManager-Schlüssel nach `oberflaecheEinrichten()` | S-2 |
| AK-3 | Panel auf 1024 × 738 (Fenster minus Rahmen) gesetzt, Layout rekursiv ausgeführt: vier Bereiche + Statusleiste vorhanden, Grenzen innerhalb des Panels, paarweise überlappungsfrei; Mindestgröße als Konstante | S-3 |
| AK-4, AK-5 | Aktivierung und Texte der Leer-Karten nach Start/Scan | S-4 |
| AK-10 | Logik: während `LAEUFT` nur Beenden aktiv, Meldung „Scan läuft“; `ScanStarter` liefert auf dem EDT zurück | S-5 (kein Einfrieren bei 1 Mio. Zeichen) |
| AK-13, AK-17 | Spaltennamen und -reihenfolge, Spaltenklasse `Boolean` (Kontrollkästchen), Stufentext | S-6 |
| AK-18 | nach Zeilenwahl: Highlighter des Eingabefelds enthält genau den Fundbereich, Text unverändert, kein `requestFocus` (Aufrufzähler an einer Test-Unterklasse) | S-7 (Scrollen, Sichtbarkeit) |
| AK-21, AK-40 | Hinweis-Labels mit Wortlaut im Komponentenbaum, Zuordnungsanzeige | – |
| AK-45 | InputMaps/ActionMaps: Kürzel D-14 in allen fokussierbaren Komponenten wirksam (auch in Textfeldern und Tabelle), Mnemonics paarweise verschieden, Leertaste schaltet Zeile um, Tab/Umschalt+Tab als Fokustasten der Textfelder | S-8 (vollständiger Durchlauf nur mit Tastatur) |
| AK-46 | `Fokusreihenfolge` liefert die Reihenfolge nach AK-46 (überspringt gesperrte Elemente) | S-8 (Fokus sichtbar) |
| AK-47 | jede Komponente hat einen deutschen `AccessibleName`; Tabelle hat `JTableHeader` | S-9 (Screenreader, OF-5) |
| AK-48 | Tooltip = Sperrgrund an jeder gesperrten Schaltfläche | S-8 |

**Sichtprüf-Liste für den Nutzer (am Ende des Features, ca. 15 Minuten):**
- S-1: `SkillShield.cmd` doppelklicken; Fenster erscheint, Titel genau „SkillShield“.
- S-2: Alle sichtbaren Texte und Dialogschaltflächen deutsch (Rückfrage beim Zurücksetzen auslösen, Fehlerfall nicht
  nötig).
- S-3: Fenster auf 1024 × 768 bringen: alle vier Bereiche und Statusleiste sichtbar, nichts überlappt; Größe ändern;
  kleiner als Mindestgröße geht nicht.
- S-4: Startzustand und leere Bereiche zeigen die erklärenden Texte.
- S-5: Einen langen synthetischen Text (z. B. Testtext vielfach eingefügt) scannen: Fenster bleibt bedienbar
  (verschieben, neu zeichnen), „Scannen“ gesperrt, danach wieder frei.
- S-6: Tabelle: Spaltenköpfe sichtbar, Stufe als Wort lesbar, Kontrollkästchen ohne Farbe erkennbar.
- S-7: Tabellenzeile wählen: Fundstelle im Eingabefeld hervorgehoben und ins Bild gescrollt, Fokus bleibt in der
  Tabelle.
- S-8: Gesamter Ablauf nur mit Tastatur (Tab, Leertaste, Strg+Enter, Strg+M, Strg+Umschalt+C, Strg+Umschalt+R,
  Alt-Kürzel); Fokus jederzeit sichtbar; Tooltips gesperrter Schaltflächen per Maus prüfen.
- S-9 (optional, OF-5): mit `jabswitch -enable` und NVDA prüfen, ob Namen der Felder und Schaltflächen vorgelesen
  werden.

---

## 10. Sicherheits- und Datenschutzaspekte

- **Kein Netzwerk-Code (AK-52):** Quelltext-Wächter in ui und app (T-1) verbietet `java.net.`, `javax.net.`,
  `java.net.http`, `java.awt.Desktop`, `.browse(`, `Socket`, `URLConnection`, `HttpClient`, `java.rmi`,
  `javax.naming`. Abhängigkeiten per Enforcer-**Positivliste** (`bannedDependencies`: alles verboten außer
  `de.skillshield:*`, `com.formdev:flatlaf`, JUnit-Familie `org.junit.*`, `org.opentest4j`, `org.apiguardian`), Bruch
  in `validate`.
- **Nichts auf Platte (AK-44):** Quelltext-Wächter verbietet in ui/app `FileOutputStream`, `FileWriter`,
  `Files.write`/`newOutputStream`/`newBufferedWriter`/`createFile`/`createTempFile`, `RandomAccessFile`,
  `java.util.prefs`, `java.util.logging.FileHandler`, `printStackTrace`, `System.out`, `System.err` (Ausnahme: genau
  eine Stelle in `SkillShieldApp.starte` für den festen Headless-Text, per Erlaubnisliste im Wächter). Laufzeitnachweis
  in T-8 durch einen Kindprozess mit leerem Arbeits-, Temp- und Home-Verzeichnis. FlatLaf-Natives abgeschaltet.
  Keine Fenstergröße, keine Einstellungen gespeichert.
- **Kein Klartext in Meldungen (AK-19, AK-49, AK-50, AK-51):** Statustexte und Rückfragen entstehen nur aus `Texte`
  plus Zahlen; Fehlerdialog = fester Text + `getClass().getName()`, nie `getMessage()`/Stacktrace; zentraler
  `UncaughtExceptionHandler` ersetzt die Standardausgabe der JVM; `toString()` aller Logik-Klassen/Records ohne Text.
  Fenstertitel fest. Marker-Tests mit synthetischen, eindeutigen Markern.
- **Zwischenablage (D-6):** nur ein Format (`stringFlavor`); Tabelle nicht kopierbar; Strg+C in Bereich 3 = „Für KI
  kopieren“ mit Rückfrage; eigener Text wird bei Zurücksetzen/Beenden geleert, fremder nie.
- **Zuordnung nur im Arbeitsspeicher (D-5):** einziges Feld in `Sitzung`; endet bei Scan, Zurücksetzen, Beenden;
  Rückfrage vor Verlust.
- **Falsche Rückübersetzung vermeiden:** Abweichung A-2.
- **Testdaten (AK-56):** Wächtertest über `src/test/java` von ui und app: keine Ziffernkette mit ≥ 9 Ziffern (auch
  mit Leerzeichen/Punkt/Bindestrich/Schrägstrich gruppiert), keine echten Namen (nur Muster wie `Max Mustermann`,
  `Beispielname`, `example.org`). KV-Nummern nur aus `SynthetischeKvNummern`.
- **Bekannte Restrisiken:** Klartext liegt als `String` im Heap und kann nicht gezielt überschrieben werden;
  Auslagerungsdatei und Absturzabbilder (`hs_err_pid*.log` im Arbeitsverzeichnis bei JVM-Absturz) liegen außerhalb
  der Kontrolle des Programms (G-6-ähnlich, nur benannt).

---

## 11. Tasks

Jeder Task endet mit grünem `mvn verify` (ca. 8–9 Minuten) und durchläuft engineer → reviewer + security → qa. Tests
der Oberfläche laufen headless. Testdaten: KV-Nummern nur aus `SynthetischeKvNummern`, sonst klar synthetische Texte
(`Max Mustermann`, `mustermann@example.org`, `192.0.2.10` aus dem Dokumentationsnetz).

### T-1: Build-Grundlage, FlatLaf, Wächtertests

- **Ziel:** Alle Build-Änderungen vorab, damit spätere Tasks nur Code bringen; FlatLaf wird früh vom OWASP-Check
  erfasst.
- **Dateien:** `pom.xml` (Wurzel: `flatlaf.version`, `dependencyManagement`); `skillshield-core/pom.xml` (test-jar,
  A-1); `skillshield-ui/pom.xml` (Testabhängigkeit core test-jar, Surefire headless, Enforcer-Positivliste neben der
  vorhandenen Regel); `skillshield-app/pom.xml` (FlatLaf compile, Surefire headless, Positivliste);
  `ui/test/QuelltextWaechterTest.java`, `app/test/QuelltextWaechterTest.java` (Netzwerk- und Platten-APIs, Kap. 10);
  `ui/test/TestdatenWaechterTest.java`, `app/test/TestdatenWaechterTest.java`; `ui/test/KernTestJarTest.java`
  (Nachweis: `SynthetischeKvNummern.KVNR_GUELTIG` mit Kontextwort wird von `TextScanner.standard()` als KVNR erkannt);
  `docs/features/oberflaeche-mvp/kern-pruefsummen.txt` (SHA-256 aller Dateien unter `skillshield-core/src`, **vor**
  jeder Änderung erzeugt, da das Projekt kein Git-Repository ist); README (FlatLaf in Lizenz-/OWASP-Abschnitt, ggf.
  A-6).
- **AK:** AK-52, AK-56, AK-57 (Prüfsummen, Kern-Tests unverändert grün), AK-54/AK-55 (Headless-Konfiguration),
  AK-44 (statischer Teil).
- **Tests:** Wächtertests ui/app (bestehen auch auf leerem Quelltext; je ein Selbsttest mit einem verbotenen
  Beispielstring als Zeichenkette, damit der Wächter nachweislich anschlägt); `KernTestJarTest`; vorhandene Modultests.
- **Abnahmehinweis:** security führt beide OWASP-Aufrufe aus (FlatLaf muss als geprüftes Artefakt erscheinen, 0
  Befunde ab CVSS 7). Engineer belegt die Enforcer-Positivliste mit einer einmaligen, wieder entfernten Negativprobe
  (im Bericht). `mvn dependency:tree` für ui und app im Bericht.

### T-2: Sitzung – Scan, Auswahl, Live-Pseudonymisierung, Statusleiste

- **Ziel:** Kern der Oberflächenlogik ohne manuelles Hinzufügen, Kopieren und Lebenszyklus.
- **Dateien:** `ui/main/logik/` `Sitzung`, `ScanZustand`, `ScanAuftrag`, `FundZeile`, `Funktion`, `Bedienzustand`,
  `Statusleiste`, `PlatzhalterJeFund`, `Beschriftungen`, `Texte`, `Zwischenablage`, `Dialoge`, `Rueckfrage`
  (Schnittstellen und Enum vollständig, damit sich der Konstruktor später nicht ändert), `package-info.java`.
- **AK:** AK-6, AK-7, AK-8, AK-9, AK-12, AK-14, AK-15, AK-16, AK-20, AK-30, AK-53; Logikteil von AK-4, AK-10, AK-40
  (Zuordnungsanzeige).
- **Tests:** `SitzungScanTest` (leer/Leerraum, 1 000 000 und 1 000 001 Zeichen, Ablehnung ändert nichts, Gleichheit
  mit `TextScanner.standard().scan` in Zahl/Reihenfolge/Typ/Wert/Stufe mit `KVNR_GUELTIG` + Kontextwort, E-Mail,
  IP; alle Stufen angewählt – Text mit Vorbedingungs-Assertion, dass je Stufe mindestens ein Fund vorliegt;
  Eingabeänderung → `VERALTET`, Zuordnung bleibt; Aktivierung „Für KI kopieren“ auch bei 0 Funden);
  `SitzungAuswahlTest` (Platzhalter je Zeile = Platzhalter an dieser Stelle im Text, gleicher Typ+Wert → gleicher
  Platzhalter, Abwahl = Ergebnis des Kerns für Restauswahl, Wiederanwahl stellt her, Alle anwählen, Statuszählung);
  `KeinStufenfilterTest` (Reflexion über öffentliche Methoden von `Sitzung`: kein `alleAbwaehlen`, keine Methode mit
  `ConfidenceLevel`/`double`-Parameter zur Auswahl, Tabelle immer alle Funde); `BeschriftungenTest`;
  `LogikArchitekturTest` (Quelltext `ui/main/logik` importiert nur `java.*` außer `java.awt`/`java.net`, und
  `de.skillshield.core.*`).
- **Abnahmehinweis:** alle Tests headless; Startzustand = `KEIN_SCAN`, „Bereit“, „Keine Zuordnung“.

### T-3: Manuelles Hinzufügen

- **Ziel:** D-7 bis D-9 nach Algorithmus Kap. 3.4.
- **Dateien:** `ui/main/logik/ManuellerFund.java`, `Sitzung` (Methode `markierungHinzufuegen`), `Texte`.
- **AK:** AK-22 bis AK-29.
- **Tests:** `ManuellerFundTest` (Typ TEXT, Konfidenz 1,0, SICHER, Quelle `manuell`, Typ „Manuell“, sortiert;
  `Beispielname schreibt an Beispielname` → 2 Funde, gleicher Platzhalter, Meldung „2 Stellen“; Überschneidung mit
  einem und mit zwei automatischen Funden → genau ein manueller Fund über die Vereinigung; übersprungene Vorkommen
  gezählt, bestehender Fund unverändert; leer/Leerraum/NBSP abgelehnt ohne Ausnahme; gesperrt ohne gültigen Scan;
  Emoji-Mitte links/rechts erweitert; Auswahlzustand anderer Zeilen bleibt; Ab- und Wiederanwahl);
  `ManuellerFundEigenschaftTest` (fester Seed, ≥ 2 000 zufällige Markierungen über synthetische Texte mit E-Mail,
  IP, Emoji, Wiederholungen: Liste überlappungsfrei und sortiert, `Pseudonymizer` wirft nie, Roundtrip zeichengenau);
  `ManuellRoundtripTest` (AK-29).
- **Abnahmehinweis:** Verhalten bei erweiterter Stelle (Hinweis Kap. 3.4) ist dokumentiert und getestet.

### T-4: Kopieren, Zwischenablage, Rückübersetzung

- **Ziel:** D-6 und Bereich 4 in der Logik, dazu der AWT-Adapter der Zwischenablage (headless mit lokaler
  `Clipboard`-Instanz testbar).
- **Dateien:** `Sitzung` (`fuerKiKopieren`, `textKopieren`, `kiAntwortGeaendert`, `zurueckuebersetzen`,
  `ergebnisKopieren`, aktive Zuordnung mit Einfrieren nach A-2), `ui/main/logik/Rueckuebersetzung.java`, `Texte`,
  `ui/main/swing/AwtZwischenablage.java` (eigenes `Transferable` nur mit `stringFlavor`, Wiederholung bei Sperre,
  alle Ausnahmen abgefangen).
- **AK:** AK-31, AK-32, AK-33, AK-35, AK-36, AK-37, AK-38, AK-39; AK-11 (Leseseite: nur Text); AK-34 (Regel und
  Adapter, Auslösung folgt in T-5).
- **Tests:** `SitzungKopierenTest` (Inhalt = Bereich 3 = `pseudonymize(text, angewählt)`, kein Originalwert eines
  angewählten Fundes enthalten; 0 angewählt → Rückfrage mit Standard Abbrechen, bei Abbrechen Zwischenablage
  unverändert; Meldungen AK-35; nur ein `setzeText`-Aufruf); `SitzungRueckuebersetzungTest` (Aktivierung, Meldung bei
  leerer Antwort, Gleichheit mit `Reidentifier.restore`, Roundtrip, unbekannt/beschädigt mit Position ab 1, beide
  Sammeltexte, Ergebnis-Kopieren gesperrt vor Rückübersetzung und bei leerem Ergebnis);
  `ZuordnungEinfrierenTest` (zwei E-Mails, kopieren, erste abwählen: Antwort mit `[[EMAIL_2]]` wird weiter zur
  zweiten Adresse zurückübersetzt, Statushinweis erscheint; vor dem Kopieren folgt die Zuordnung der Auswahl);
  `AwtZwischenablageTest` (lokale `Clipboard`: nur `stringFlavor` angeboten, Bild-/HTML-only → leer, leeren → keine
  Formate, Unterklasse mit `IllegalStateException` beim Lesen/Schreiben → keine Ausnahme, kein Leeren).
- **Abnahmehinweis:** Strg+C aus Bereich 3 ist in T-6 an `fuerKiKopieren` gebunden; hier nur Logik.

### T-5: Lebenszyklus, Rückfragen, Fehlerbehandlung, Klartext-Prüfung

- **Ziel:** Zuordnung endet kontrolliert; Fehler werden klartextfrei gemeldet.
- **Dateien:** `Sitzung` (`scanAnfordern` Rückfrage, `zuruecksetzenAnfordern`, `beendenAnfordern`,
  `scanFehlgeschlagen`), `ui/main/logik/Fehlerbehandlung.java`, `Texte`, `Rueckfrage`.
- **AK:** AK-34 (Auslösung bei Zurücksetzen/Beenden), AK-41, AK-42, AK-43 (Logik), AK-49, AK-50 (Logik und
  Handler), AK-51 (Logikteil), AK-19.
- **Tests:** `SitzungLebenszyklusTest` (Rückfrage nur bei ≥ 1 Platzhalter; Abbrechen ändert nichts; nach neuem Scan
  alte Platzhalter „unbekannt“; Zurücksetzen = Startzustand; Beenden ohne Zuordnung ohne Rückfrage;
  Zwischenablage-Regel: eigener Text geleert, fremder nicht, gesperrte Zwischenablage ohne Ausnahme);
  `SitzungFehlerTest` (Scanner-Attrappe wirft `IllegalStateException` mit Marker; Scanner liefert überlappende Funde
  → `InvalidFindingException` im `Pseudonymizer`: Dialogtext = fester Text + Klassenname, Zustand vorher = nachher);
  `FehlerbehandlungTest` (Ausnahme mit Marker in Meldung und Ursache: Anzeige ohne Marker, `System.out`/`err`
  umgeleitet und leer, Fehler in der Anzeige selbst wird verschluckt); `KlartextFreiheitOberflaecheTest` (scripted
  Durchlauf aller Zustände mit Markern in E-Mail, manuellem Namen, KI-Antwort und erzwungenen Fehlern: kein Marker in
  Statusleiste, Rückfragen, Fehlertexten, Sperrgründen, `toString()` von `Sitzung`, `FundZeile`, `ScanAuftrag`,
  `Bedienzustand`, `Statusleiste`, `Rueckuebersetzung`; umgeleitete Standardströme ohne Marker).
- **Abnahmehinweis:** Damit sind alle [L]-Kriterien der Logik abgedeckt; ab hier nur noch Ansicht und Start.

### T-6: Hauptpanel (Ansicht, Bindung, Hintergrund-Scan)

- **Ziel:** Bedienbare Oberfläche als `JPanel`, vollständig an `Sitzung` gebunden.
- **Dateien:** `ui/main/swing/` `HauptPanel`, `FundTabellenModell`, `ScanStarter`, `Textaktionen`, `SwingDialoge`.
  Inhalt: vier Bereiche mit Überschriften (`JLabel` mit `setLabelFor`), Eingabe (`JTextArea`, DocumentListener →
  `eingabeGeaendert`, gesperrt während Scan), Tabelle (Spalten nach AK-13, Typ/Stufe als Text, Kopieren/Ziehen aus;
  Zeilenwahl → `Highlighter` im Eingabefeld + `scrollRectToVisible`, ohne Fokus), Leer-Karten (`CardLayout`) für
  Tabelle, Bereich 3 und Ergebnis, Hinweise AK-21 und AK-40, KI-Antwort, Ergebnis (nicht bearbeitbar, markierbar),
  Liste „Nicht zurückübersetzt“, Schaltflächen inkl. „Zurücksetzen“ und „Beenden“ (Beenden-Aktion ruft einen
  übergebenen `Runnable`), Statusleiste links Meldung, rechts Zuordnung; Einfügen nur Text über `Zwischenablage`;
  Strg+C/X: Bereich 3 → `fuerKiKopieren`, andere Felder → `textKopieren(Auswahl)`; Zurücksetzen leert die Felder
  nach `zuruecksetzenAnfordern() == true`. `SwingDialoge` mit `JOptionPane.showOptionDialog`, Optionen
  `{"Ja","Abbrechen"}` bzw. `{"OK"}`, Standard „Abbrechen“.
- **AK:** AK-3, AK-4, AK-5, AK-11, AK-12 (Verdrahtung), AK-13, AK-17, AK-18, AK-19 (Ansicht), AK-21, AK-40, AK-10
  (SwingWorker), AK-16 (keine solche Schaltfläche), AK-2 (Dialogoptionen).
- **Tests (headless):** `HauptPanelAufbauTest` (Komponenten per Name, Überschriften, Hinweise, Leer-Texte, Layout bei
  1024 × 738 ohne Überlappung); `HauptPanelAblaufTest` (Text ins Dokument → Scan über `ScanStarter`, Warten per
  `invokeAndWait`; Tabelle zeigt Zeilen; Kontrollkästchen per `setValueAt` → Bereich 3 ändert sich; Tippen nach Scan
  leert Tabelle und Bereich 3; Highlighter nach Zeilenwahl; Text unverändert); `TextaktionenTest` (Einfügen mit
  Attrappe: Text eingefügt, leere Zwischenablage ändert nichts; Ablegen per `TransferHandler.importData` mit
  HTML-/Bild-`Transferable` abgelehnt; Tabelle hat keinen `TransferHandler`; Strg+C in Bereich 3 löst Rückfrage aus bei
  0 Funden); `SwingDialogeTest` (Optionen und Standardwert, ohne Dialog anzuzeigen); `ScanStarterTest` (Ergebnis und
  Fehler kommen auf dem EDT an).
- **Abnahmehinweis:** keine `JFrame`-Instanz in diesem Task; Tastatur folgt in T-7.

### T-7: Tastatur und Barrierefreiheit

- **Ziel:** D-14, AK-45 bis AK-48 im Panel.
- **Dateien:** `ui/main/swing/Tastatur.java`, `Fokusreihenfolge.java`, `HauptPanel` (Einbindung, zugängliche Namen,
  Tooltips aus `Bedienzustand.sperrgrund`).
- **Belegung:** Strg+Enter Scannen, Strg+Umschalt+C Für KI kopieren, Strg+Umschalt+R Zurückübersetzen, Strg+M
  Markierung als Fund hinzufügen – registriert in `WHEN_IN_FOCUSED_WINDOW` **und** in den `WHEN_FOCUSED`-Maps von
  Textfeldern und Tabelle (damit keine Standardbelegung sie verschluckt). Mnemonics (Vorschlag): Scannen Alt+S, Alle
  anwählen Alt+A, Markierung als Fund hinzufügen Alt+M, Für KI kopieren Alt+K, Zurückübersetzen Alt+R, Rückübersetzten
  Text kopieren Alt+T, Zurücksetzen Alt+Z, Beenden Alt+E; Überschriften Alt+1 bis Alt+4 springen in den Bereich.
  Tab/Umschalt+Tab als Fokustasten in allen `JTextArea`. Leertaste in der Tabelle schaltet die gewählte(n) Zeile(n)
  um. Fokusreihenfolge: Eingabe, Scannen, Tabelle, Alle anwählen, Markieren, Bereich 3, Für KI kopieren, KI-Antwort,
  Zurückübersetzen, Ergebnis, Ergebnis kopieren, Liste, Zurücksetzen, Beenden. Fokusrahmen: FlatLaf-Standard (in T-8),
  keine Unterdrückung von `focusPainted`.
- **AK:** AK-45, AK-46, AK-47, AK-48 (automatischer Teil; Rest S-8, S-9).
- **Tests (headless):** `TastaturTest` (jedes Kürzel löst in jeder fokussierbaren Komponente die richtige Aktion
  aus – Auflösung über InputMap/ActionMap; Mnemonics vorhanden, paarweise verschieden, Buchstabe in der Beschriftung;
  Leertaste toggelt; Tab ist Fokustaste, kein Tabulator-Einfügen; eingefügter Text mit Tab bleibt erhalten);
  `FokusreihenfolgeTest` (`getComponentAfter`/`Before` über die ganze Liste, gesperrte übersprungen);
  `ZugaenglichkeitTest` (jede Komponente mit nicht leerem deutschem `AccessibleName`, Statusleiste eingeschlossen;
  Tooltip jeder gesperrten Schaltfläche = Sperrgrund, nach Freigabe ohne Sperrgrund).
- **Abnahmehinweis:** qa vermerkt S-8/S-9 als „Sichtprüfung durch den Nutzer“, nicht als bestanden.

### T-8: Startprogramm, Fenster, startbare JAR, Spurenfreiheit

- **Ziel:** Das Programm ist für den Nutzer startbar; Endnachweise AK-44 und AK-51 im echten Startpfad.
- **Dateien:** `ui/main/swing/HauptFenster.java` (Titel aus `Texte.FENSTERTITEL`, Mindestgröße, Startgröße
  1024 × 768, `DO_NOTHING_ON_CLOSE` + `WindowListener` → `sitzung.beendenAnfordern()`; auch die Schaltfläche
  „Beenden“ nimmt diesen Weg); `app/main/SkillShieldApp.java` (`Locale.setDefault(Locale.GERMANY)`,
  `flatlaf.useNativeLibrary=false` vor jedem FlatLaf-Zugriff, Headless → fester Text + Code 2,
  `Thread.setDefaultUncaughtExceptionHandler(new Fehlerbehandlung(...))` mit Anzeige über `SwingDialoge`,
  `FlatLightLaf.setup()`, deutsche `UIManager`-Texte für `OptionPane`, Verdrahtung, Beenden = `dispose` +
  `System.exit(0)`); `skillshield-app/pom.xml` (Shade nach Kap. 6, Startskript kopieren);
  `skillshield-app/src/main/start/SkillShield.cmd`; Wurzel-`pom.xml` (`pluginManagement` Shade);
  README Abschnitt „Programm starten“ (Voraussetzung Java 21, beide Startwege, Hinweis `jabswitch`, Grenzen G-5/G-8).
- **AK:** AK-1, AK-2, AK-43 (Fensterweg), AK-44, AK-50 (zentraler Handler installiert), AK-51 (vollständiger
  Durchlauf), AK-55, AK-57 (Prüfsummen-Vergleich).
- **Tests:** `SkillShieldAppTest` (headless: `starte` liefert 2, Ausgabe genau der feste Text; nach
  `oberflaecheEinrichten()` sind die `OptionPane`-Texte deutsch und FlatLaf aktiv); `HauptFensterTest` (mit
  `assumeFalse(isHeadless())`, also im Standardlauf übersprungen: Titel, Mindestgröße, Schließverhalten, Fenster nie
  sichtbar gemacht); `SpurenfreiheitTest` (startet per `ProcessBuilder` eine zweite JVM aus `java.home` mit
  `-Djava.awt.headless=true -XX:-UsePerfData`, leerem Arbeitsverzeichnis, `-Djava.io.tmpdir` und `-Duser.home` auf
  leere Verzeichnisse, Klassenpfad des Tests; die Testklasse `SitzungsProbe` richtet die Oberfläche wie
  `SkillShieldApp` ein (FlatLaf, Locale, Handler), baut `HauptPanel` mit Zwischenablage- und Dialog-Attrappen und
  spielt eine vollständige Sitzung mit Markern ab, inkl. erzwungenem Fehler über den zentralen Handler; danach: alle
  drei Verzeichnisse leer, Standardausgabe und -fehler ohne Marker, Exit-Code 0).
- **Abnahmehinweis:** qa führt zusätzlich aus: `java -Djava.awt.headless=true -jar
  skillshield-app/target/skillshield.jar` → Exit-Code 2 und fester Text; prüft, dass `SkillShield.cmd` neben der JAR
  liegt; vergleicht `kern-pruefsummen.txt` (nach A-1 aktualisiert) mit dem Ist-Stand; nennt die Zahl übersprungener
  Tests. Danach Sichtprüf-Liste (Kap. 9) an den Nutzer.

### Zuordnung der Akzeptanzkriterien

| AK | Task(s) | AK | Task(s) | AK | Task(s) |
|----|---------|----|---------|----|---------|
| 1 | T-8 | 20 | T-2 | 39 | T-4 |
| 2 | T-6, T-8 | 21 | T-6 | 40 | T-2, T-6 |
| 3 | T-6 | 22–29 | T-3 | 41 | T-5 |
| 4 | T-2, T-6 | 30 | T-2 | 42 | T-5 |
| 5 | T-6 | 31 | T-4 | 43 | T-5, T-8 |
| 6–9 | T-2 | 32 | T-4 | 44 | T-1, T-8 |
| 10 | T-2, T-6 | 33 | T-4 | 45–48 | T-7 |
| 11 | T-4, T-6 | 34 | T-4, T-5 | 49 | T-5 |
| 12 | T-2, T-6 | 35 | T-4 | 50 | T-5, T-8 |
| 13 | T-6 | 36 | T-4 | 51 | T-5, T-8 |
| 14–16 | T-2 (16 auch T-6) | 37 | T-4 | 52 | T-1 |
| 17, 18 | T-6 | 38 | T-4 | 53 | T-2 |
| 19 | T-5, T-6 | | | 54 | T-1 bis T-5 |
| | | | | 55 | T-1, T-8 |
| | | | | 56 | T-1 |
| | | | | 57 | T-1, T-8 (und jeder Task: Kern-Tests grün) |

---

## 12. Risiken und bekannte Grenzen

- **R-1 test-jar im Reactor:** Auflösung des core-test-jar klappt in `mvn verify` (Phase `package` erreicht), kann
  aber Teilaufrufe (`mvn compile …`, `mvn test -pl skillshield-ui`) stören; Gegenmaßnahme A-6, Prüfung in T-1.
- **R-2 Shade und FlatLaf:** Multi-Release-Einträge und `module-info` erfordern die Filter aus Kap. 6; Fehler zeigen
  sich erst beim Start der JAR (Abnahme per Headless-Start in T-8).
- **R-3 FlatLaf headless:** `FlatLightLaf.setup()` wird in T-8 headless aufgerufen. Sollte das in der Zielversion
  scheitern, wird FlatLaf im Test übersprungen und das als Grenze gemeldet (nicht stillschweigend).
- **R-4 Headless-Tests sind kein Sichtbeweis:** Fokus sichtbar, Scrollen, Screenreader, echtes Layout mit
  Systemschriften und Bildschirmskalierung (125 %/150 %) nur in der Sichtprüfung.
- **R-5 Startvoraussetzung:** Java 21 muss installiert und `javaw` im `PATH` sein (OF-4); sonst startet
  `SkillShield.cmd` nicht (Fehlermeldung von Windows).
- **R-6 Große Texte:** Suche aller Vorkommen beim manuellen Hinzufügen nutzt `String.indexOf`; bei künstlichen
  Extremfällen (lange Markierung aus sich wiederholenden Zeichen in 1 Mio. Zeichen) kann der EDT kurz stocken.
  Live-Neuberechnung je Klick ist linear und unkritisch.
- **R-7 Einfrieren der Zuordnung (A-2):** Wer nach dem Kopieren die Auswahl ändert, muss erneut kopieren, damit die
  Rückübersetzung zum neuen Stand passt; die Statusleiste sagt das.
- **G-8 (neu, zu G-5):** Ist die Zwischenablage beim Beenden/Zurücksetzen von einem anderen Programm gesperrt, wird
  sie nicht geleert.
- **G-9 (neu):** Bei Überschneidung der Markierung mit einem Fund (D-9 b) erhalten weitere Vorkommen einen anderen
  `TEXT`-Platzhalter als die erweiterte Stelle (Kap. 3.4); alle Stellen bleiben geschützt.
- Die Grenzen G-1 bis G-7 der Anforderung gelten unverändert.

---

## 13. Offene Punkte für die Freigabe

1. **A-1** Kern-`pom.xml` erhält eine `test-jar`-Execution (kein Code). Freigabe?
2. **A-2** Rückübersetzung mit der beim Kopieren eingefrorenen Zuordnung. Freigabe?
3. **A-3/A-4** Rückfragen nur mit „Ja“/„Abbrechen“; „Beenden“ als Schaltfläche statt Menü. Freigabe?
4. **A-5** Auslegung „fehlgeschlagener Scan lässt gültigen Scan bestehen“. Freigabe?
5. **A-6** Mögliche Änderung des OWASP-Aufrufs im README (`package -DskipTests` statt `compile`).
6. **OF-4** Java 21 auf den Zielrechnern bleibt offen (Voraussetzung bis zum Installationspaket).
7. **FlatLaf-Version** 3.6.1 als Vorschlag; endgültige Version nennt T-1 nach OWASP-Check.

---

## Nachtrag zu T-1 (Durchlauf 2, Ergänzung zu Kap. 10)

Der Text von Kap. 10 bleibt unverändert; die Wächter in ui und app sind wie folgt verschärft:

- **Quelltext-Wächter, zusätzlich verboten:** `ProcessBuilder`, `Runtime.getRuntime`, `.exec(`,
  `Desktop.getDesktop`, `FileHandler`, `java.util.logging`, `System.getLogger`, `File.createTempFile`,
  `Files.copy`, `Files.move`, `newByteChannel`, `FileChannel`, `ImageIO.write`, `new PrintWriter(`,
  `new PrintStream(`, `newFileSystem`, `dumpStack` sowie gegen Umgehung per Reflection `Class.forName`,
  `MethodHandles`, `getDeclaredMethod`. Unicode-Escapes werden vor dem Abgleich aufgelöst. Der Wächter läuft nur,
  wenn `pom.xml` im Arbeitsverzeichnis liegt (sonst rot); nur ein fehlendes `src/main/java` gilt als leer.
- **Testdaten-Wächter:** Unterstrich zählt als Trenner in Ziffernketten (Zahlliterale); zusätzlich wird
  `src/test/resources` geprüft (fehlend = leer); gleiche `pom.xml`-Prüfung.
- **Positivliste (Enforcer):** SkillShield-Module einzeln statt `de.skillshield:*`; JUnit-Familie nur mit Scope
  `test`; FlatLaf nur in der festen Version aus der Property `flatlaf.version` (3.6.1).
- **Grenzen:** Der Quelltext-Wächter erkennt keine bewusste Verschleierung und keine zusammengesetzten
  Zeichenketten. Der Testdaten-Wächter erkennt keine zusammengesetzten Zeichenketten, keine anderen Trenner, kein
  IPv6 und keine Namen, Geburtsdaten oder Adressen. Die Positivliste erfasst keine Build-Plugins und Extensions.
  Diese Restlücken deckt die Prüfung durch reviewer und security ab.
