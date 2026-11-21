# Architektur: Text-Pseudonymisierung (Fachkern)

Feature-Kurzname: `text-pseudonymisierung-kern`
Grundlage: `docs/features/text-pseudonymisierung-kern/anforderung.md` (Neufassung: AK-1 bis AK-54, D-1 bis D-8,
A-1 bis A-12, OF-4 bis OF-16), `Plan.md` Kap. 2, 5–8, 11, 13, 14, `docs/BLUEPRINT_SkillShield_Java.md`, `CLAUDE.md`,
Parent-`pom.xml`, `skillshield-core/pom.xml`.

Stand des Codes (geprüft): `skillshield-core` enthält nur `de/skillshield/core/package-info.java` und `CoreModulTest`.
Der Enforcer verbietet in `skillshield-core` jede `de.skillshield:*`-Abhängigkeit; Laufzeitabhängigkeiten gibt es keine,
im Test-Scope JUnit 5 (inkl. `junit-jupiter-params`).

**Änderungen gegenüber dem Vorentwurf (15 Tasks):** IK/LANR, `CheckDigit`, `SynthetischeKvNummern` und alle KV-Bezüge
entfallen; der Entwurf dafür ist in `anhang-ik-lanr-entwurf.md` gesichert und wird im Folgefeature wiederverwendet.
Neu ist der Pfad-Erkenner (Kap. 2.4, sieben Tasks T-10 bis T-16). Die Konfliktauflösung ist neu geregelt (Enthaltensein
vor Konfidenz, Kap. 2.5). `Finding` verliert das Feld `note` (kein Nutzer mehr in diesem Feature). Der Rest
(zustandslose Schritte, `PlaceholderSyntax.tokenize` für Hin- und Rückweg, Literalschutz `[[TEXT_n]]`, klartextfreies
`toString()`, Regex-Härtung, Quelltext-Prüftest) bleibt.

**Nachtrag 19.11.2026 (Nutzerentscheidung D-8 nach Sicherheitsbefund zu T-2):** Teilweise überlappende Kandidaten
werden zu einem Fund zusammengelegt statt verworfen; Enthaltensein bleibt (Kap. 2.5, TE-3, TE-19). Stand des Codes:
T-1 committet, T-2 nach alter Regel umgesetzt, nicht abgenommen; T-2 wird nach dieser Fassung nachgearbeitet.
`ConflictResolver.resolve` erhält den Text als Parameter, `Finding` die Quellenkonstante `zusammengelegt`,
`TextScanner` kapselt Ausnahmen der Erkenner klartextfrei (TE-20). Geändert: Kap. 1, 2.1, 2.2, 2.4.3, 2.4.5, 2.5,
2.9, 5, T-2, T-7, T-11, T-16 bis T-20, Zuordnung, Kap. 7, 8, 9.

---

## 1. Lösungsansatz

Der Fachkern besteht aus drei zustandslosen Schritten auf einem `String` im Modul `skillshield-core`:
**Scan** (`TextScanner` ruft die Erkenner auf, `ConflictResolver` macht die Kandidaten überlappungsfrei, indem er
enthaltene Kandidaten im umschließenden aufgehen lässt und teilweise überlappende zusammenlegt),
**Pseudonymisierung** (`Pseudonymizer` ersetzt genau die übergebenen Funde durch `[[TYP_n]]`, ein Pfad wird genau ein
`[[PFAD_n]]`) und **Rückübersetzung** (`Reidentifier` ersetzt einpassig jeden erkannten Platzhalter, auch tolerierte
Varianten, und meldet unbekannte und beschädigte). Platzhalterähnlicher Originaltext wird bei der Pseudonymisierung
selbst durch `[[TEXT_n]]` geschützt; Hin- und Rückweg nutzen dieselbe Platzhaltererkennung.

**Pfad-Erkennung**: ein Erkenner `PathRecognizer` mit einem handgeschriebenen, linearen Zeichendurchlauf (kein Regex,
kein `java.nio.file`, kein Dateisystem). Der Text zerfällt in **Läufe** (Zeichenfolgen zwischen Leerraum und wenigen
Endzeichen). Ein Pfad ohne Anführungszeichen beginnt an einer **Startgrenze** mit dem **Kopf** einer der sieben
Pfadformen (Laufwerk, UNC, Unix, Home, relativ, Umgebungsvariable, `file:`) und reicht bis zum Laufende, abzüglich
abschließender Satzzeichen und unpaariger schließender Klammern (D-5: Ende am ersten Leerzeichen). Steht direkt vor dem
Kopf ein Anführungszeichen und wird es auf derselben Zeile geschlossen, ist der ganze Inhalt der Fund (AK-16). Die
Pfadformen unterscheiden sich nur im Kopf, in der Mindestzahl der Segmente und in der Konfidenz; Laufzerlegung,
Anführungszeichen, Ende und Segmentzählung sind gemeinsam. E-Mail, IPv4 und URL bleiben Regex-Erkenner. Bei
vollständigem Enthaltensein gewinnt der umschließende Fund unabhängig von der Konfidenz (AK-24); teilweise überlappende
Kandidaten werden zu einem Fund über den gesamten Bereich zusammengelegt (D-8, AK-7), sodass keine von einem Erkenner
abgedeckte Position durch die Konfliktauflösung in den Klartext fällt.

Feature-Schnitt: 20 kleine Tasks. Nach T-9 steht der vollständige Roundtrip mit E-Mail, IP, URL und manuellen Funden;
T-10 bis T-16 fügen je eine Pfadform bzw. einen Pfadaspekt hinzu.

---

## 2. Betroffene Module und Pakete

Nur `skillshield-core`, Basispaket `de.skillshield.core` (AK-52). Kein POM wird geändert. Keine `module-info.java`.
Das Paket `recognizer.kv` bleibt in diesem Feature leer (Folgefeature).

### 2.1 Neue Dateien (`skillshield-core/src/main/java/de/skillshield/core/…`)

| Paket | Typ | Art | Zweck |
|-------|-----|-----|-------|
| `model` | `FindingType` | record | Typname (`PFAD`, `EMAIL`, `IP`, `URL`, `TEXT`, freie Namen für manuelle Funde) |
| `model` | `ConfidenceLevel` | enum | `SICHER`, `WAHRSCHEINLICH`, `FRAGLICH`; Zuordnung aus Konfidenz |
| `model` | `Finding` | record | Fund; `toString()` ohne Klartext; Quellenkonstanten `manuell`, `zusammengelegt` |
| `recognizer` | `Recognizer` | interface | ein Erkenner |
| `recognizer` | `TextScanner` | final class | Scan = alle Erkenner + Konfliktauflösung; kapselt Erkenner-Ausnahmen klartextfrei |
| `recognizer.general` | `EmailRecognizer`, `Ipv4Recognizer`, `UrlRecognizer` | final class | Regex-Erkenner |
| `recognizer.general` | `PathRecognizer` | final class | Pfad-Erkenner (Durchlauf, Läufe, Anführungszeichen, Ende) |
| `recognizer.general` | `PathForm` | enum (package-private) | je Pfadform: Kopf, Mindestsegmente, Konfidenz, Quelle |
| `recognizer.general` | `PathChars` | final class (package-private) | Zeichenklassen (Trenner, Endzeichen, Startgrenze, Kürzung) |
| `conflict` | `ConflictResolver` | final class | überlappungsfreie Fundliste: Enthaltensein und Zusammenlegung (Kap. 2.5) |
| `pseudonymize` | `PlaceholderSyntax` | final class | Format und Erkennung von Platzhaltern (Hin- und Rückweg) |
| `pseudonymize` | `PlaceholderMapping` | final class | unveränderliche Zuordnung Platzhalter → Originalwert |
| `pseudonymize` | `PseudonymizationResult` | record | Text + Zuordnung |
| `pseudonymize` | `InvalidFindingException` | final class | eindeutiger Fehler für ungültige Fundlisten |
| `pseudonymize` | `Pseudonymizer` | final class | Ersetzung |
| `reidentify` | `Reidentifier` | final class | Rückübersetzung |
| `reidentify` | `ReidentificationResult` | record | Text + nicht aufgelöste Platzhalter |
| `reidentify` | `UnresolvedPlaceholder` | record | Meldung (nur Platzhalter, Position, Grund) |

### 2.2 Signaturskizzen

```java
// ---------- model ----------
public record FindingType(String name) {
    // Format [A-Z][A-Z0-9]{0,31}: kein Unterstrich, damit [[TYP_n]] eindeutig zerlegbar ist.
    // Verstoß -> IllegalArgumentException; die Meldung wiederholt den Namen NICHT.
    public static final FindingType PFAD  = new FindingType("PFAD");
    public static final FindingType EMAIL = new FindingType("EMAIL");
    public static final FindingType IP    = new FindingType("IP");
    public static final FindingType URL   = new FindingType("URL");
    public static final FindingType TEXT  = new FindingType("TEXT");   // Literalschutz, Kap. 2.7
}

public enum ConfidenceLevel {
    SICHER, WAHRSCHEINLICH, FRAGLICH;
    public static final double SCHWELLE_SICHER = 0.90;          // confidence >= 0.90
    public static final double SCHWELLE_WAHRSCHEINLICH = 0.70;  // 0.70 <= confidence < 0.90, darunter FRAGLICH
    public static ConfidenceLevel of(double confidence);
}

/**
 * Positionen sind UTF-16-Indizes wie bei String.substring: start inklusiv, end exklusiv (AK-1).
 * Invariante gegenüber dem gescannten Text: text.substring(start, end).equals(originalValue).
 */
public record Finding(FindingType type, String originalValue, int start, int end,
                      double confidence, String source) {
    public static final String SOURCE_MANUAL = "manuell";
    public static final String SOURCE_MERGED = "zusammengelegt";   // nur ConflictResolver (Kap. 2.5, TE-19)
    public static final int MAX_SOURCE_LENGTH = 32;                 // Quelle im Format [a-z][a-z0-9-]{0,31}
    public Finding { /* type, originalValue, source != null; Quellenformat; start >= 0; end > start;
                        originalValue.length() == end - start; 0.0 <= confidence <= 1.0, kein NaN
                        -> sonst IllegalArgumentException / NullPointerException, Meldung ohne Werte */ }
    public static Finding manual(FindingType type, String text, int start, int end); // 1.0, "manuell"
    public ConfidenceLevel level();
    @Override public String toString();
    // "Finding[type=PFAD, start=6, end=31, confidence=0.95, level=SICHER, source=pfad-laufwerk]" – ohne originalValue
    // zusammengelegt: "Finding[type=PFAD, start=5, end=83, confidence=0.95, level=SICHER, source=zusammengelegt]"
}

// ---------- recognizer ----------
public interface Recognizer {
    /** Liefert Kandidaten; darf überlappen, verändert nichts. Quelle "zusammengelegt" ist nicht erlaubt. */
    List<Finding> detect(String text);
}

public final class TextScanner {
    public TextScanner(List<? extends Recognizer> recognizers);
    public static TextScanner standard();   // E-Mail, IPv4, URL, Pfad (wächst mit T-3, T-4, T-5, T-10)
    public List<Finding> scan(String text); // NPE("text") bei null; unveränderlich, überlappungsfrei,
                                            // aufsteigend nach start (AK-1, AK-7, AK-8);
                                            // Erkenner wirft RuntimeException -> IllegalStateException
                                            // ohne Originalmeldung, ohne cause (TE-20)
}

// ---------- recognizer.general (Pfade) ----------
public final class PathRecognizer implements Recognizer {
    public List<Finding> detect(String text);           // Algorithmus Kap. 2.4
}
enum PathForm {                                          // package-private, ein Konstantenblock je Pfadform
    LAUFWERK, UNC, UNIX, HOME, RELATIV, UMGEBUNG, FILE_URI;
    /** Ende des Kopfs (exklusiv) oder -1. Prüft nur Zeichen ab i; die Startgrenze prüft der Aufrufer. */
    abstract int headEnd(String text, int i);
    abstract int minSegments();
    abstract double confidence(int segmentsCappedAt3);
    abstract String source();                            // z. B. "pfad-laufwerk"
}
final class PathChars {                                  // package-private, nur static
    static boolean isSeparator(char c);                  // '\\' oder '/'
    static boolean isTerminator(char c);                 // Kap. 2.4.1
    static boolean isStartBoundary(String text, int i);  // Kap. 2.4.1
    static boolean isQuote(char c);                      // '"', '\'', '`'
}

// ---------- conflict ----------
public final class ConflictResolver {
    /** Regeln Kap. 2.5. Jeder Kandidat muss zum Text passen (sonst IAE mit Listenindex, ohne Wert). */
    public static List<Finding> resolve(String text, List<Finding> candidates);
}

// ---------- pseudonymize ----------
public final class PlaceholderSyntax {
    public static String format(FindingType type, int n);          // "[[TYP_n]]", n >= 1
    public static List<Token> tokenize(CharSequence text, int from, int to, Set<String> knownTypes); // ab T-8
    public record Token(int start, int end, Kind kind, String canonical) {
        public enum Kind { PLATZHALTER, BESCHAEDIGT }
    }
}
public final class PlaceholderMapping {
    public static PlaceholderMapping empty();
    public static PlaceholderMapping of(Map<String, String> placeholderToOriginal); // Schlüssel kanonisch, sonst IAE ohne Wert
    public Optional<String> resolve(String canonicalPlaceholder);
    public Set<String> placeholders();             // Reihenfolge = Nummernfolge
    public Set<String> typeNames();
    public int size();
    @Override public String toString();            // "PlaceholderMapping[size=3]"
}
public record PseudonymizationResult(String text, PlaceholderMapping mapping) {
    @Override public String toString();            // "PseudonymizationResult[textLength=…, placeholders=…]"
}
public final class InvalidFindingException extends IllegalArgumentException {
    public enum Reason { NULL_ELEMENT, OUT_OF_RANGE, OVERLAP, VALUE_MISMATCH, SPLITS_SURROGATE_PAIR }
    public InvalidFindingException(Reason reason, int index);   // "Ungültiger Fund an Listenindex 3: OVERLAP"
    public Reason reason();
    public int index();
}
public final class Pseudonymizer {
    public PseudonymizationResult pseudonymize(String text, List<Finding> findings);
}

// ---------- reidentify ----------
public final class Reidentifier {
    public ReidentificationResult restore(String text, PlaceholderMapping mapping);
}
public record ReidentificationResult(String text, List<UnresolvedPlaceholder> unresolved) {
    @Override public String toString();            // ohne text
}
public record UnresolvedPlaceholder(String token, int position, Reason reason) {
    public enum Reason { UNBEKANNT, BESCHAEDIGT }
}
```

Nullbehandlung (AK-47): `null` als Text, Fundliste oder Zuordnung → `NullPointerException` mit dem Parameternamen als
Meldung (`Objects.requireNonNull(text, "text")`). `null` als Listenelement → `InvalidFindingException(NULL_ELEMENT, i)`.

### 2.3 Regex-Erkenner E-Mail, IPv4, URL

Gemeinsame Regeln (AK-49, TE-7):
1. Jedes Muster beginnt mit einem negativen Lookbehind auf die eigene Zeichenklasse; ein Treffer kann nur am Anfang
   eines Zeichenlaufs beginnen (verhindert quadratisches Neuansetzen, z. B. 100 000 × `a`).
2. Wiederholungen nur possessiv (`++`, `{m,n}+`) oder eng begrenzt; keine verschachtelten Quantoren über sich
   überlappende Klassen.
3. Ziffern nur `[0-9]`; Buchstaben in E-Mail/URL Unicode-fähig (`\p{L}\p{M}\p{N}`), damit kein Treffer ein
   Surrogatpaar oder eine Kombinationsfolge trennt (AK-23).
4. Kein `java.net.URL`/`URI`/`InetAddress` (u. a. löst `URL.equals/hashCode` DNS aus).
5. Jeder Erkennertest prüft `text.substring(start, end).equals(originalValue)` für jeden Fund (AK-1).

*(Korrektur 19.11.2026 nach security-Befund T-3, Schwere mittel – gilt für alle drei Muster dieser Tabelle:
Regel 1 darf nicht dazu führen, dass ein gültiger Wert gar nicht erkannt wird, nur weil unmittelbar davor
Füll- oder Satzzeichen aus der eigenen Klasse stehen. Belegter Fall: `E-Mail` + 60 × `.` + Adresse ergab keinen
Fund, weil der Treffer nur am Anfang des Zeichenlaufs beginnen durfte und der lokale Teil auf 64 Zeichen begrenzt
ist. Deshalb: Der Lookbehind schließt nur die Zeichen aus, die den Wert wirklich fortsetzen würden – bei E-Mail
Buchstaben/Marken/Ziffern, nicht aber `. _ % + -`; der Wert selbst beginnt dann mit einem Buchstaben oder einer
Ziffer. Für IP (`(?<![0-9.])`) und URL (`(?<![\p{L}\p{N}+.-])`) ist dieselbe Frage in T-4 bzw. T-5 zu prüfen und zu
testen: Punktführer, Binde- oder Unterstrichfolgen direkt vor dem Wert dürfen den Fund nicht verhindern. Die
Laufzeitgarantie aus Regel 1 muss dabei erhalten bleiben und mit pathologischen Eingaben belegt werden.
Grundsatz für alle Erkenner: Im Zweifel ist ein zu großer oder ein Teilfund besser als kein Fund.)*

*(Nachtrag 19.11.2026 zu T-3 nach security-Befunden der Durchläufe 4 und 5, vom Nutzer entschieden – weicht vom
Muster der Tabelle ab: (1) Hängen Adressen ohne Leerraum aneinander, darf der E-Mail-Erkenner einen Fund am letzten
`.` vor einem weiteren `@` in zwei Funde teilen; zwischen den Teilfunden bleibt dann nur dieser eine Punkt frei.
(2) Ein Rest aus `@` plus Domain hinter einem Fund wird als eigener EMAIL-Fund gemeldet. Ein EMAIL-Fund beginnt
also nicht immer mit einem Zeichen des lokalen Teils, er kann mit `@` beginnen; Folgetasks (T-6 ff.) dürfen das
nicht voraussetzen. (3) Obermengen-Garantie per Konstruktion: Der Erkenner berechnet immer auch das Ergebnis ohne
Aufteilen und gleicht ab. Jede Textposition, die ohne Aufteilen abgedeckt wäre, ist im Ergebnis abgedeckt, außer
einem einzelnen Trenner-Punkt direkt zwischen zwei Funden; sonst bleibt der große Fund. Ein Zufalls-Differenztest
sichert das ab. Wer in T-4/T-5 ebenfalls Funde teilt oder kürzt, übernimmt dieses Vorgehen.)*

| Typ | Muster (Skizze) | Nachbearbeitung | Konfidenz | Quelle |
|-----|-----------------|-----------------|-----------|--------|
| `EMAIL` | `(?<![\p{L}\p{M}\p{N}._%+-])`, lokal `[\p{L}\p{M}\p{N}._%+-]{1,64}+`, `@`, Domain `[\p{L}\p{M}\p{N}-]{1,63}+(?:\.[\p{L}\p{M}\p{N}-]{1,63}+)++` | – (Schlusspunkt/Komma nicht in der Klasse bzw. ohne folgendes Label, AK-3) | 0.95 sicher | `email` |
| `IP` | `(?<![0-9.])`, vier Oktette `25[0-5]\|2[0-4][0-9]\|1[0-9]{2}\|[1-9]?[0-9]` mit `\.`, `(?!\.?[0-9])` | keine führenden Nullen | 0.85 wahrscheinlich | `ipv4` |
| `URL` | `(?<![\p{L}\p{N}+.-])`, `(?i:https?)://`, Rumpf `[^\s\p{Z}\p{C}<>"]++` | am Ende wiederholt entfernen: `. , ; : ! ? ' "`, sowie `)`/`]`, wenn im Treffer mehr schließende als öffnende stehen (dieselbe Klammerregel wie bei Pfaden, OF-5 b); bleibt nur `http(s)://` → kein Fund | 0.95 sicher | `url` |

### 2.4 Pfad-Erkennung (`PathRecognizer`, `PathForm`, `PathChars`)

Ein Erkenner statt fünf: Alle Pfadformen teilen Laufzerlegung, Anführungszeichen, Endregel, Klammerregel und
Segmentzählung; nur der Kopf unterscheidet sich. Getrennte Erkenner würden diese Logik fünfmal ausführen oder eine
gemeinsame Basisklasse brauchen, und sie würden sich gegenseitig überlappende Kandidaten liefern (z. B. Unix-Kandidat
im Laufwerkspfad), die erst der Resolver aufräumt. Die Formen sind trotzdem getrennt testbar und einzeln einführbar:
Jede Form ist eine Konstante von `PathForm` mit eigenem `headEnd`; ein Task fügt eine Konstante plus Tests hinzu.

Kein `java.nio.file.Path`, kein `java.io.File`, keine Prüfung auf Existenz, keine plattformabhängige Logik
(`File.separator`, `System.lineSeparator()`): Das Ergebnis ist auf jedem Betriebssystem gleich (AK-51).

#### 2.4.1 Zeichenklassen (`PathChars`)

| Begriff | Definition |
|---------|-----------|
| **Trenner** | `\` und `/` – für alle Formen beide (gemischte Trenner, AK-9). Ein Trenner-Lauf (`\\`, `//`, `\/`) zählt wie ein Trenner (doppelte Backslashes, AK-22). |
| **Endzeichen** (beenden einen Lauf) | jedes Zeichen mit `Character.isWhitespace(c) \|\| Character.isSpaceChar(c)` (Leerzeichen, Tab, `\n`, `\r`, NBSP …), jedes ISO-Steuerzeichen (`Character.isISOControl`), sowie `"`, `` ` ``, `<`, `>`, `\|` (OF-5 d). **Nicht**: `'` (kommt in Namen vor), `*`, `?`, `(`, `)`, `[`, `]`, `{`, `}`, `=`, `,`, `;`, `:`, `%`, `$`, `~`, `@`, `#`, `&`, `+`, `!`. |
| **Lauf** | maximale Folge von Nicht-Endzeichen. Ein Pfad ohne Anführungszeichen liegt immer ganz in einem Lauf. |
| **Startgrenze** vor Position i | i = 0, oder das Zeichen i−1 ist Leerraum (wie bei Endzeichen) oder eines von `"` `'` `` ` `` `(` `[` `{` `<` `>` `=` (AK-12; `>` für Umleitungen wie `>C:\Logs\a.log`). **Nicht** `:`, `/`, `\`, Buchstabe, Ziffer, `.`, `-`, `_` – deshalb beginnt in `https://…/a/b`, `12/2025`, `und/oder`, `km/h`, `file:///C:` kein Pfad. |
| **Segment** | maximale Folge von Nicht-Trennern, die mindestens ein Zeichen enthält, das kein Leerraum ist. Gezählt werden nur Segmente hinter dem Kopf. Segmentanfang = Nicht-Trenner, dem ein Trenner vorausgeht. |
| **Kürzungszeichen am Ende** | immer: `.` `,` `;` `:` `!` `?` `'`; bedingt: `)` `]` `}` nur, wenn im Fund mehr schließende als öffnende Klammern dieser Art stehen (AK-19, OF-5 b). Wiederholt von hinten, bis kein Zeichen mehr passt. `*` bleibt (OF-5 c), ein abschließender Trenner bleibt (AK-9). |

#### 2.4.2 Pfadformen

Alle Köpfe beginnen mit einem ASCII-Zeichen; die Formen sind am ersten Zeichen unterscheidbar (Buchstabe → `LAUFWERK`
oder `FILE_URI`, `\` → `UNC`, `/` → `UNIX`, `~` → `HOME`, `.` → `RELATIV`, `%`/`$` → `UMGEBUNG`). Die Startgrenze gilt
für alle Formen. Groß-/Kleinschreibung im Kopf ist frei (Laufwerksbuchstabe, `file:`).

| Form | Kopf (ab Position i) | Mindest-segmente | Konfidenz / Stufe | Positiv (Fund = ganzer Wert) | Negativ (kein Fund) |
|------|---------------------|------------------|-------------------|------------------------------|---------------------|
| `LAUFWERK` | `[A-Za-z]` `:` Trenner | 1 | 0.95 sicher | `C:\Tools\BeispielTool\tool.exe`, `C:/Tools/…`, `C:\Tools/BeispielTool\tool.exe`, `c:\tools\…`, `C:\Tools\BeispielTool\` (mit Trenner), `C:\\Tools\\BeispielTool\\tool.exe`, `C:\Tools\Beispiel(x86)`, `C:\Program` aus `C:\Program Files\…` (AK-17 a) | `C:`, `Teil A: Beschreibung`, `Uhrzeit 10:30`, `C:\` (OF-7), `C:\.` (nach Kürzung kein Segment), `xC:\a` (keine Startgrenze) |
| `UNC` | maximaler Backslash-Lauf der Länge **genau 2 oder 4**, danach ein Nicht-Trenner | 2 (Server, Freigabe) | 0.95 sicher | `\\srv-beispiel\freigabe\ordner\datei.txt`, `\\srv-beispiel\freigabe`, `\\\\srv-beispiel\\freigabe\\x`, `\\192.0.2.10\freigabe\ordner`, `\\srv-beispiel\Mein` (AK-17 b), `\\?\C:\Tools\a.exe` (siehe N-2) | `\\srv-beispiel` (OF-7), `\\` / `\\\\` allein, 3 oder ≥ 5 Backslashes |
| `UNIX` | `/` | 2 | 2 Segmente: 0.80 wahrscheinlich; ≥ 3: 0.90 sicher | `/opt/beispiel-tool/bin/tool`, `/opt/beispiel/a` (aus `…/a, …`), `//srv-beispiel/freigabe` (siehe N-2) | `/etc`, `/s`, `xcopy /s /e`, `tool.exe /quiet`, `/?`, `ja / nein`, `12/2025`, `12/05/2025`, `1/2`, `24/7`, `und/oder`, `km/h`, `Ein/Aus`, `/a/b` in `https://example.invalid/a/b` (keine Startgrenze) |
| `HOME` | `~` `/` | 1 | 0.90 sicher | `~/beispiel/konfig.yml`, `~/x` | `~/` allein, `~benutzer/x` (OF-4), `~\x` |
| `RELATIV` | `./`, `.\`, `../`, `..\` (Punkt-Punkt-Trenner bzw. Punkt-Trenner) | 1 | 0.80 wahrscheinlich | `.\tools\tool.exe`, `../konf/app.yml` | `.\`, `../`, `ordner\datei.txt`, `ordner/datei.txt`, `Files\Mein`, `Tool\tool.exe`, `1.` ×n (keine Startgrenze) |
| `UMGEBUNG` | `%NAME%` Trenner mit `NAME` = `[A-Za-z_][A-Za-z0-9_()]{0,63}`; `$NAME` Trenner bzw. `${NAME}` Trenner mit `NAME` = `[A-Za-z_][A-Za-z0-9_]{0,63}` | 1 | 0.80 wahrscheinlich | `%APPDATA%\BeispielTool\konfig.ini`, `%ProgramFiles(x86)%\x`, `$HOME/beispiel/konfig.yml`, `${HOME}/beispiel/konfig.yml` | `%APPDATA%`, `$HOME`, `${HOME}`, `$1/x`, `%%`, `% x`, `$env:APPDATA\x` (PowerShell, nicht abgedeckt) |
| `FILE_URI` | `file:` (beliebige Schreibung) gefolgt von mindestens `//`; Kopfende hinter `file:` | 2 | 0.95 sicher | `file:///C:/Tools/BeispielTool/tool.exe` (ganz, inkl. `file:///`, OF-8), `file://srv-beispiel/freigabe/x`, `file:///opt/beispiel/x` | `file:`, `file:///`, `file:///C:/`, `profile:///x` (keine Startgrenze) |

Die Konfidenz hängt nur von Form und Segmentzahl ab, nie von Leerzeichen (AK-17) oder Anführungszeichen. Ein Fund in
Anführungszeichen hat dieselbe Form, Quelle und Konfidenz wie ohne.

Folgen der Regeln, die bewusst in Kauf genommen werden (keine AK-Verletzung, im Test dokumentiert):
- `und /oder/beides` ist ein Unix-Pfad (OF-4, bekannte Grenze); ebenso `rd /s/q` (`/s/q`).
- Ein Schalter mit Pfadwert `tool.exe /log=C:\Logs\a.log` ergibt **einen** Fund `/log=C:\Logs\a.log` (Unix-Form,
  weil `\` auch Trenner ist). Der Pfad ist vollständig geschützt; der Schaltername ist mitersetzt.
- Durch Kommas oder Semikolons ohne Leerzeichen verbundene Pfade (`C:\a;C:\b`) sind ein Fund.

#### 2.4.3 Pfad in Anführungszeichen (AK-16, AK-20, D-5)

An einer Position p mit Anführungszeichen q ∈ {`"`, `'`, `` ` ``}, wenn an p+1 ein Kopf einer Form beginnt (die
Startgrenze ist durch q erfüllt):
1. Suche das schließende q ab p+2. Die Suche **endet erfolglos** am ersten Zeilenende (`\n`, `\r`), Tab, sonstigen
   Steuerzeichen, `<`, `>`, `|`, oder – wenn q nicht `"` ist – `"`. Leerzeichen beenden die Suche nicht.
2. Gefunden an c und der Inhalt [p+1, c) hat mindestens die Mindestsegmente der Form (Zählung durch Durchlauf über den
   Inhalt, Segmentdefinition 2.4.1): **Fund = [p+1, c)**, ohne Kürzung (der ganze Inhalt, auch mit abschließendem Punkt
   oder Leerzeichen). Weiter bei c+1. Die Anführungszeichen bleiben im Text (`"[[PFAD_1]]"`).
3. Sonst (nicht geschlossen, Suche abgebrochen, zu wenige Segmente): **Rückfall auf die Regel ohne
   Anführungszeichen** ab p+1 (TE-12, Entscheidung N-1). Beispiel: `Starte "C:\Program Files\x` (nie geschlossen)
   ergibt `C:\Program`.

Folge: `'` vor einem Pfadkopf wirkt als Anführungszeichen. `'C:\a' und 'C:\b'` ergibt zwei Funde; ein Apostroph vor
einem Pfad, dem auf derselben Zeile ein weiteres `'` folgt, kann einen Fund mit Leerzeichen erzeugen (mehr Text
geschützt als nötig, kein Leck). Steht das öffnende `'` innerhalb einer URL (`https://intranet.invalid/?f='C:/Akte
2025/…/x.pdf'`), endet der URL-Kandidat am Leerzeichen, der Pfad-Kandidat reicht darüber hinaus: Teilüberlappung, der
Resolver legt beide zusammen (Kap. 2.5, AK-7 (g)). Das öffnende `'` liegt dann im Inneren des Fundes und geht im
Platzhalter auf (A-12), das schließende `'` bleibt stehen.

#### 2.4.4 Durchlauf (linear, AK-49)

```
pos = 0; lauf = keiner
solange pos < n:
  c = text[pos]
  wenn isQuote(c) und Regel 2.4.3 liefert Fund [p+1, c'):  ausgeben; pos = c'+1; weiter
  wenn isTerminator(c): pos++; weiter
  wenn pos >= lauf.ende: lauf = Laufdaten ab pos berechnen          // je Lauf genau einmal
  wenn isStartBoundary(pos) und form = Kopf an pos und h = form.headEnd(pos) >= 0
       und lauf.segmenteAb(h) >= form.minSegments():
       ende = kürzen(pos, lauf.ende)                                // Kürzungsregel 2.4.1, Klammern ab pos gezählt
       ausgeben [pos, ende), Konfidenz form.confidence(lauf.segmenteAb(h)); pos = lauf.ende; weiter
  pos++
```

Laufdaten (einmal je Lauf, O(Lauflänge)): `ende` (nächstes Endzeichen), `ende0` = `ende` nach Entfernen **aller**
abschließenden Kürzungszeichen einschließlich aller schließenden Klammern (pessimistisch, unabhängig vom Startpunkt),
und die Positionen der **letzten drei Segmentanfänge** vor `ende0`. `segmenteAb(h)` = Anzahl dieser drei Positionen
≥ h (also min(3, tatsächliche Anzahl)), O(1). Die tatsächliche Endposition aus `kürzen` ist ≥ `ende0`, daher ist ein
so geprüfter Fund immer gültig.

Laufzeit: Jeder Lauf wird einmal ausgewertet; jede Position wird höchstens einmal als Startkandidat in O(1) geprüft
(Köpfe sind auf ≤ 67 Zeichen begrenzt); `kürzen` läuft einmal je ausgegebenem Fund über dessen Lauf, danach springt
`pos` ans Laufende. Die Suche nach dem schließenden Anführungszeichen endet spätestens am nächsten gleichen
Anführungszeichen; die Suchbereiche je Anführungszeichenart überlappen nicht, zusammen höchstens 3 n Schritte. Damit
ist der Durchlauf O(n), auch für 100 000 × `"`, `"C:\ a b c …` ohne Ende, `C:\a ` ×100 000 und `C:\a` + 100 000 Wörter.
Keine Rekursion, kein Regex.

Unicode (AK-23): Alle Endzeichen, Trenner, Kopf- und Kürzungszeichen sind BMP-Zeichen außerhalb der Surrogatbereiche
und keine kombinierenden Zeichen. Ein Fund beginnt daher nie in einem Surrogatpaar; ein Fund endet nur vor einem
Endzeichen oder nach dem Kürzen vor einem ASCII-Satzzeichen; folgt einem Kürzungszeichen ein kombinierendes Zeichen,
ist das kombinierende das letzte Zeichen und es wird nicht gekürzt. Steht vor einem Kopf ein kombinierendes Zeichen,
ist keine Startgrenze erfüllt.

#### 2.4.5 Abgrenzung zu URL, IP, E-Mail (AK-24, AK-5, AK-7)

Die Abgrenzung entsteht aus Startgrenze und Konfliktauflösung, nicht aus Sonderfällen im Pfad-Erkenner:
- (a) `https://example.invalid/a/b`: `/a` hat keine Startgrenze → kein Pfad-Kandidat; nur URL.
- (b) `https://example.invalid/?datei=C:\Tools\a.exe`: Pfad-Kandidat `C:\Tools\a.exe` (Startgrenze `=`) liegt ganz in
  der URL → URL-Fund unverändert (Enthaltensein, 2.5).
- (c) `\\192.0.2.10\freigabe\ordner`: IP-Kandidat liegt ganz im UNC-Pfad → PFAD-Fund unverändert.
- (d) `C:\Benutzer\erika.beispiel@example.invalid\ablage`: E-Mail liegt ganz im Pfad → PFAD-Fund unverändert.
- (e) `file:///C:/Tools/BeispielTool/tool.exe`: `FILE_URI` liefert einen Fund über den ganzen Ausdruck; `/C:` und
  `C:` haben keine Startgrenze; die URL-Regex kennt `file` nicht.
- (f) `Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende`: URL-Kandidat bis
  `Akte`, Pfad-Kandidat in `'…'`, E-Mail im Pfad enthalten → **ein zusammengelegter Fund** (2.5, Ergebnis siehe
  TE-19 (c)).
- (g) `https://intranet.invalid/?f="C:/Akte 2025/x.pdf"`: `"` gehört nicht zur URL; URL endet vor `"`, der Pfad beginnt
  nach `"` → keine Überlappung, zwei Funde `[[URL_1]]"[[PFAD_2]]"` (AK-7 (e)).

### 2.5 Konfliktauflösung (`ConflictResolver`, AK-6, AK-7, AK-24, D-8, OF-16)

Signatur `resolve(String text, List<Finding> candidates)`. Der Text wird gebraucht, weil ein zusammengelegter Fund
einen Originalwert hat, den kein einzelner Kandidat enthält.

**Begriffe.** Zwei Kandidaten **überlappen**, wenn sie mindestens ein Zeichen teilen (`a.start < b.end && b.start <
a.end`); aneinandergrenzende (`a.end == b.start`) überlappen nicht (AK-7 (e)). Ein Kandidat ist **enthalten**, wenn ein
anderer Kandidat seinen Bereich ganz umfasst; bei gleichem Bereich gilt der schwächere (Ordnung `STAERKE`) als im
stärkeren enthalten. **Maximal** ist ein Kandidat, der nicht enthalten ist.

**Stärke** (`STAERKE`, totale Ordnung, OF-16 (a)): Konfidenz absteigend, dann Länge absteigend, dann Start
aufsteigend, dann Typrang (`URL` < `PFAD` < `EMAIL` < `IP` < übrige), Typname, Quelle, Originalwert (die letzten drei
nur für Totalität und Determinismus).

**Algorithmus** (eine Sortierung, ein Durchlauf):
0. Prüfen, ohne etwas zu verändern: `text`, `candidates` nicht `null` (NPE `"text"`/`"candidates"`), kein
   `null`-Element (NPE `"candidates enthält null"`), jeder Kandidat passt zum Text (`end <= text.length()` und
   `text.regionMatches(start, originalValue, 0, len)`), sonst `IllegalArgumentException("Kandidat an Listenindex i passt
   nicht zum Text")` – ohne Wert.
1. Kopie sortieren nach `start` aufsteigend, `end` absteigend, dann `STAERKE`.
2. Durchlauf mit einer offenen **Gruppe** (`gStart`, `gEnd`, Liste `M` der maximalen Kandidaten der Gruppe):
   - keine Gruppe offen oder `f.start >= gEnd`: offene Gruppe abschließen (Schritt 3); neue Gruppe mit
     `gStart = f.start`, `gEnd = f.end`, `M = [f]`.
   - sonst, wenn `f.end > gEnd`: `f` ist maximal und überlappt das letzte Glied von `M` teilweise → `M.add(f)`,
     `gEnd = f.end`.
   - sonst: `f` liegt im letzten Glied von `M` (dessen Start ≤ `f.start`, dessen Ende = `gEnd` ≥ `f.end`) → übergehen.
3. Gruppe abschließen:
   - `|M| = 1`: das Kandidatenobjekt **unverändert** übernehmen (kein Konflikt oder reines Enthaltensein; Typ,
     Konfidenz, Stufe und Quelle des umschließenden, AK-24, AK-7 (d), AK-6 letzter Satz).
   - `|M| ≥ 2`: **zusammengelegter Fund** `new Finding(typ, text.substring(gStart, gEnd), gStart, gEnd, konfidenz,
     Finding.SOURCE_MERGED)` mit `typ` = Typ des stärksten Glieds von `M` (`STAERKE`) und `konfidenz` = kleinste
     Konfidenz in `M`.
4. Ergebnis: unveränderliche Liste in Abschlussreihenfolge (= aufsteigend nach `start`).

**Warum das die Kriterien erfüllt** (für Review und Tests):
- Kette (AK-7 (b), (c)): Ein neues Glied `f` hat `f.start < gEnd = last.end` und `f.end > last.end`; gleicher Start mit
  `last` ist ausgeschlossen (bei gleichem Start sortiert `end` absteigend, also `f.end <= last.end`). Also überlappt
  jedes Glied seinen Vorgänger echt teilweise; `M` ist eine Kette beliebiger Länge, die Gruppe ihr gemeinsamer Fund.
- Enthaltensein (AK-7 (d), AK-24): Ein übergangener Kandidat liegt ganz in einem Glied von `M`. Ein Kandidat, der nur
  in der Vereinigung, aber in keinem einzelnen Kandidaten liegt (A = [0,10), B = [5,15), C = [8,20)), ist maximal und
  Glied der Kette.
- Schutzgarantie (AK-7 (a)): Jeder Kandidat gehört zu genau einer Gruppe; der Gruppenbereich `[gStart, gEnd)` ist
  lückenlos die Vereinigung ihrer Kandidaten (jedes neue Glied beginnt vor `gEnd`), und genau dieser Bereich ist ein
  Ergebnisfund. Umgekehrt (AK-7 (e)) enthält kein Ergebnisfund eine Position, die kein Kandidat abdeckt; angrenzende
  Kandidaten (`f.start == gEnd`) beginnen eine neue Gruppe.
- Überlappungsfreiheit (AK-7): Jede neue Gruppe beginnt bei `start >= gEnd` der vorigen.
- Unicode (AK-23): Grenzen eines Ergebnisfundes sind Grenzen von Kandidaten; es entstehen keine neuen Grenzen.
- Determinismus (AK-8): `STAERKE` ist total; die Ausgabe hängt nicht von der Eingabereihenfolge ab (bis auf
  vollständig gleiche Kandidaten, die gleiche Ergebnisse liefern).
- Laufzeit (AK-49): O(n log n) Sortierung, O(n) Durchlauf, `substring` je Gruppe (Gruppen disjunkt, zusammen O(Text)).
  Keine Rekursion, kein `TreeMap`, auch bei 100 000 Gliedern einer Kette.

**AK-24 und AK-7 (f) widerspruchsfrei:** Typ und Konfidenz kommen **nur aus `M`** (den maximalen Gliedern). Enthaltene
Kandidaten bestimmen weder Typ noch Konfidenz noch Quelle – im reinen Enthaltensein (`|M| = 1`, AK-24, unabhängig von
der Konfidenz) ebenso wie in einer Kette. Gemischte Kette „A enthält B, A überlappt C teilweise“: `M = [A, C]`, B wird
übergangen; Typ = stärkerer von A und C; Konfidenz = min(A, C). Damit gibt es keine Konstellation, in der ein
enthaltener Fund mit höherer Konfidenz den umschließenden verdrängt (AK-24) und keine, in der Typ oder Stufe eines
Kettenfundes von einem nicht beteiligten Fund stammt (AK-7 (f)).

**Enthaltene Kandidaten zählen für die Stufe nicht** (OF-16 (b)), Begründung: (1) AK-6 legt für Enthaltensein die Stufe
des umschließenden fest; die Kette ändert an diesem Verhältnis nichts, denn jede Position des enthaltenen Kandidaten
ist bereits vom umschließenden Glied abgedeckt. Die Stufenregel soll Positionen sichtbar machen, die **nur** ein
unsicherer Erkenner abgedeckt hat; ein enthaltener Kandidat fügt keine solche Position hinzu. (2) Sonst wäre die Regel
unstetig: Ein sicherer Nachbar, der A nur am Rand berührt, würde die Stufe von A auf die eines darin enthaltenen
fraglichen Kandidaten senken, ohne dass sich an der Abdeckung etwas ändert. (3) Im Beispiel AK-7 (g) ist die E-Mail
enthalten; sie liefert weder den Typ (gefordert: nicht EMAIL) noch die Stufe.

**Originalwert (Punkt „Konsistenz“):** `text.substring(gStart, gEnd)`. Weil Schritt 0 jeden Kandidaten gegen den Text
prüft, sind die Werte überlappender Kandidaten zwangsläufig konsistent; es gibt keinen eigenen Fehlerfall
„Inkonsistenz“. Verworfen: den Wert aus den Kandidatenwerten zusammensetzen (braucht eine eigene Konsistenzprüfung und
einen Fehlerpfad, ohne Nutzen). `TextScanner` behält seine eigene Prüfung (`IllegalStateException`, Erkennerfehler);
die Prüfung im Resolver schützt die öffentliche Methode.

Die Pseudonymisierung legt nichts zusammen; eine überlappende Fundliste bleibt dort ungültig (AK-32, Kap. 2.6).

### 2.6 Pseudonymisierung (`Pseudonymizer`)

1. Eingaben vollständig prüfen, bevor etwas erzeugt wird (AK-32): Text und Liste nicht `null`; je Element nicht `null`,
   `end <= text.length()`, `text.regionMatches(start, originalValue, 0, len)`, `start`/`end` trennen kein
   Surrogatpaar; nach Sortierung keine Überlappung (Angrenzen erlaubt, AK-34). Negative Position und `start >= end`
   lehnt bereits der `Finding`-Konstruktor ab (TE-10). Überlappende Funde werden nicht zusammengelegt, sondern
   abgelehnt (D-8 gilt nur im Scan).
2. Funde nach `start` sortieren (AK-27).
3. Literalschutz (ab T-9, Kap. 2.7): Lücken zwischen den Funden tokenisieren; jedes Token wird ein `TEXT`-Fund.
4. In Textreihenfolge Platzhalter vergeben: Schlüssel (Typname, Originalwert) zeichengleich (AK-28, D-7); neuer
   Schlüssel → nächstes `n` (global ab 1, A-4; ein zusammengelegter Fund ist ein Fund); vorhandener → derselbe
   Platzhalter. Zähler je Aufruf, kein statischer Zustand (AK-29).
5. Ausgabe per `StringBuilder.append`: Lücken unverändert (AK-33), Funde durch Platzhalter. **Kein** `String.replace*`,
   `Matcher.appendReplacement` oder `Matcher.quoteReplacement` (Originalwerte mit `$1`, `\1`, AK-38).
6. Ergebnis mit Zuordnung nur für tatsächlich ersetzte Werte (AK-35).

### 2.7 Platzhaltersyntax und Literalschutz (AK-30, AK-36, AK-41–AK-44)

`PlaceholderSyntax.tokenize` ist die einzige Stelle, die Platzhalter erkennt; Pseudonymizer (Lücken) und Reidentifier
(ganzer Text) nutzen sie gleich. Ein Matcher mit Alternation `T|D`, links nach rechts:
- **T (Platzhalter, auch tolerierte Varianten)**: `\[\[ {0,8}([A-Za-z][A-Za-z0-9]{0,31})_([1-9][0-9]{0,8}) {0,8}\]\]`.
  Kanonisch: `"[[" + typ.toUpperCase(Locale.ROOT) + "_" + n + "]]"`. Toleriert: Groß-/Kleinschreibung des Typs,
  Leerzeichen (U+0020) innen (A-6).
- **D (beschädigt)**: `\[{1,2} {0,8}([A-Za-z][A-Za-z0-9]{0,31})_[0-9]{0,9} {0,8}\]{0,2}` (z. B. `[[PFAD_1]`,
  `[PFAD_1]`, `[[PFAD_]]`, `[[PFAD_`). Nur ein Token, wenn der Typname (groß) in `knownTypes` steht; sonst verbraucht,
  aber nicht gemeldet (Code wie `arr[idx_0]` erzeugt keine Meldung).

Literalschutz im Pseudonymizer: `K` := Typnamen der übergebenen Funde; Lücken mit `K` tokenisieren; gibt es ein Token,
`K := K ∪ {TEXT}` und einmal erneut tokenisieren; jedes Token wird ein `TEXT`-Fund (Konsistenzregel gilt). Reidentifier:
`knownTypes = mapping.typeNames()`.

Invariante (Fuzz-Test T-18): Im pseudonymisierten Text liefert `tokenize` genau die erzeugten Platzhalter. Folgen:
Roundtrip ohne Fremdbearbeitung ist zeichengleich und meldet nichts; ein originales `[[PFAD_1]]` wird `[[TEXT_1]]` und
kommt unverändert zurück (AK-36); `[[`, `]]`, `[[[[X_1]]]]`, `[[PERSON_1`, ein originales `[[TEXT_1]]` ebenso; ohne
Funde und ohne T-Muster ist die Ausgabe identisch (AK-30).

### 2.8 Rückübersetzung (`Reidentifier`)

Einpassig (AK-44): `tokenize` über den Eingabetext, Ausgabe per `StringBuilder`; eingesetzte Originalwerte werden nie
erneut durchsucht. Je Token: T in der Zuordnung → Originalwert (AK-40; die Zahl wird ganz gelesen, `_1`/`_11` sind
verschiedene Schlüssel); T unbekannt → stehen lassen, `UNBEKANNT` (AK-41); D → stehen lassen, `BESCHAEDIGT` (AK-42).
Fehlende Platzhalter werden nicht gemeldet. `token` ist der Ausschnitt aus dem zurückkommenden Text (nur Klammern,
ASCII-Buchstaben/Ziffern, `_`, Leerzeichen, ≤ ca. 60 Zeichen), enthält also keinen Originalwert.

### 2.9 Klartextfreiheit (AK-50)

- `toString()` überschrieben in `Finding`, `PseudonymizationResult`, `PlaceholderMapping`, `ReidentificationResult`
  (Länge/Anzahl statt Inhalt). `UnresolvedPlaceholder` behält das Record-`toString()` (nur Platzhalter).
- Ausnahmemeldungen nennen nur Parametername, Listenindex, Grund, Position, Typ oder Platzhalter; nie Originalwert,
  Text oder Ausschnitte; keine `cause` mit Klartext.
- **Ausnahmen der Erkenner** (TE-20): `TextScanner` ruft `recognizer.detect(text)` in `try`/`catch (RuntimeException e)`
  auf und wirft `new IllegalStateException("Erkenner an Index " + i + " fehlgeschlagen: " + e.getClass().getName())` –
  ohne `cause`, ohne `addSuppressed`, ohne `e.getMessage()`. Eine Erkenner-Meldung mit Eingabetext kann so nicht nach
  außen gelangen. `Error` (z. B. `StackOverflowError`) wird nicht abgefangen (trägt keinen Eingabetext; Linearität
  verhindert ihn).
- Kein `System.out`/`System.err`, kein `printStackTrace`, kein Logging im Fachkern.

### 2.10 Hinweise für Testdaten mit Backslashes (verbindlich für alle Tasks)

AK-22 und AK-38 unterscheiden bewusst `\` und `\\`. In Java-Stringliteralen und auch in **Text Blocks** ist `\` ein
Escape-Zeichen; Text Blocks lösen das Problem also nicht, normalisieren zusätzlich Zeilenenden auf `\n` und entfernen
abschließende Leerzeichen (gefährlich für `C:\a `-Fälle und `\r\n`-Tests). Daher:
1. Testhilfe `de.skillshield.core.TestTexte` (Testquellen) mit `static String bs(String s)`: ersetzt jedes `¦` (U+00A6)
   durch genau einen Backslash. Pfade in Tests werden so geschrieben: `bs("C:¦Tools¦BeispielTool¦tool.exe")`,
   doppelt `bs("C:¦¦Tools¦¦x")`, UNC `bs("¦¦srv-beispiel¦freigabe")`, vierfach `bs("¦¦¦¦srv-beispiel¦¦freigabe")`.
   `TestTexte` hat einen eigenen Test (Anzahl der Backslashes, kein `¦` im Ergebnis).
2. Wo die Anzahl der Backslashes Prüfgegenstand ist (AK-22, AK-38, UNC), prüft der Test sie zusätzlich explizit
   (`count('\\')`).
3. Zeilenenden (`\r\n`, `\r`) und abschließende Leerzeichen nur mit expliziten Escapes, nie über Text Blocks.
4. Erwartete Ausgaben werden mit derselben Hilfe gebildet wie die Eingaben.
5. Testdaten nur synthetisch (AK-53): `srv-beispiel`, `mustermann`, `BeispielTool`, `tool.exe`, Windows-Standardordner;
   E-Mail `example.invalid`/`.org`/`.net`; IPs aus 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24.

---

## 3. Wiederverwendung

- Vorhandene Modul- und Paketstruktur von `skillshield-core` (`model`, `recognizer/general`, `conflict`,
  `pseudonymize`, `reidentify`); `CoreModulTest` bleibt unverändert; Enforcer-Regel `modulrichtung` sichert AK-52.
- Bereits umgesetzt (T-1, T-2): `Finding` (inkl. Quellenformat `[a-z][a-z0-9-]{0,31}`), `FindingType`,
  `ConfidenceLevel`, `Recognizer`, `TextScanner`, `ConflictResolver` (Sortierschlüssel `byTieBreak`/`typeRank` bleiben
  als Teil von `STAERKE` verwendbar).
- JUnit 5 inkl. `junit-jupiter-params` (`@ParameterizedTest`, `@MethodSource`), `assertTimeoutPreemptively`.
- JDK: `java.util.regex`, `java.util.Random` (fester Startwert), `Character`, `Objects`, `List.copyOf`, `Map.copyOf`,
  `StringBuilder`.

## 4. Neue Abhängigkeiten

Keine (AK-52). Kein POM wird geändert.

---

## 5. Sicherheits- und Datenschutzaspekte

- **Kein Netzwerk-, DNS- oder Dateisystemzugriff** (AK-51): keine Klassen aus `java.net`, `javax.net`, `java.nio`,
  `java.io` im Produktivcode; IP/URL/UNC nur per Zeichenprüfung. Belegt durch Quelltext-Prüftest (T-20).
- **Kein Klartext** in `toString()`, Ausnahmen, Ausgaben (2.9). Belegt durch Marker-Test über alle Fehlerpfade (T-20).
  Ausnahmen von Erkennern werden ohne Meldung und ohne `cause` gekapselt (TE-20, T-2).
- **Schutzgarantie der Konfliktauflösung** (D-8, AK-7 (a)): Jede von einem Kandidaten abgedeckte Position ist im
  Scan-Ergebnis abgedeckt. Belegt durch Eigenschaftstest auf Resolver-Ebene (T-2) und mit echten Erkennern im Fuzz-Test
  (T-18). Die Garantie betrifft nur Positionen, die ein Erkenner gefunden hat (D-5-Reste bleiben Klartext).
- **Größere Funde durch Zusammenlegung**: Ein zusammengelegter Fund kann unkritischen Text mitnehmen und lässt sich im
  Review nur ganz übernehmen oder ganz ablehnen (Abgrenzung D-8). Die Quelle `zusammengelegt` und die Konfidenz des
  schwächsten Glieds machen das im Review sichtbar (AK-6).
- **Laufzeitsicherheit**: Regex-Regeln 2.3, linearer Pfad-Durchlauf 2.4.4, Konfliktauflösung O(n log n) auch bei
  langen Ketten (2.5); Grenzlasttests (T-2, T-19) und je Pfad-Task.
- **Bekannte Schutzlücke D-5**: Pfade mit Leerzeichen ohne Anführungszeichen werden nur bis zum ersten Leerzeichen
  erkannt; Ordner- und Programmnamen dahinter bleiben Klartext (Nutzerentscheidung). Ein Test in T-10 hält das
  Verhalten fest, damit es nicht unbemerkt geändert wird.
- **Pseudonymisierter Text kann Klartext enthalten** (abgelehnte, nicht erkannte Stellen, D-5-Reste, Programmnamen
  ohne Pfad). Daher zeigt auch `PseudonymizationResult.toString()` den Text nicht. Leak-Check folgt in v0.2 (A-8).
- **Zuordnung nur im Speicher** (A-9): Java-Strings lassen sich nicht sicher löschen; Schutz folgt mit dem Tresor.
- **Fremde Zuordnung** (OF-14 d): nicht verhindert; im Tresor-Feature mit Durchlauf-Kennung lösbar.
- **Kein KI-Anteil** (AK-54): nur Regex und Zeichenregeln; KI-Bearbeitung im Test durch feste Umformungen simuliert.

---

## 6. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün (AK-52); ein Commit je Task auf `main`. Tests unter
`skillshield-core/src/test/java/de/skillshield/core/…` im jeweiligen Paket, Methodennamen deutsch wie in
`CoreModulTest`. Jeder Task hält 2.3 (Regex), 2.9 (Klartext) und 2.10 (Testdaten) ein. Zeitgrenzen: je
pathologischer Eingabe und Schritt `assertTimeoutPreemptively(2 s)` (TE-4).

### T-1: Datenmodell
- **Dateien**: `model/FindingType.java`, `model/ConfidenceLevel.java`, `model/Finding.java`; Tests `FindingTypeTest`,
  `ConfidenceLevelTest`, `FindingTest`; Testhilfe `TestTexte` (Paket `de.skillshield.core`) mit `TestTexteTest`.
- **AK**: AK-1 (Felder, Konvention im Javadoc), AK-6 (Schwellen), AK-32 (negative Position, Start nicht kleiner als
  Ende), AK-50 (`Finding.toString`).
- **Tests**: Typname gültig/ungültig (Kleinbuchstaben, Unterstrich, Leerzeichen, leer, 33 Zeichen) ohne Echo;
  `ConfidenceLevel.of` an 0.70/0.90/0.0/1.0; Konstruktor lehnt negative Position, `start >= end`, Längenabweichung,
  Konfidenz außerhalb [0,1]/NaN, `null` ab; `manual(…)`; `toString()` ohne Marker-Originalwert; `bs(…)` liefert die
  erwartete Backslash-Anzahl.
- **Stand**: committet. Die Ergänzung `SOURCE_MERGED` erfolgt in T-2.

### T-2: Erkenner-Schnittstelle, Konfliktauflösung (Enthaltensein und Zusammenlegung, D-8), Scanner
- **Stand und Auftrag**: T-2 ist nach der alten Regel (Stufe 2 „Verlierer entfällt“) umgesetzt und nicht abgenommen.
  Nacharbeit nach Kap. 2.5 dieser Fassung. Die bisherige Stufe 2 (`FOR_PARTIAL_OVERLAP`, `TreeMap`,
  `overlapsAccepted`) entfällt ersatzlos; Tests, die die alte Regel belegen (Teilüberlappung → ein Fund entfällt,
  „Gleichstand → früherer Start gewinnt“ als Auswahl), werden entfernt bzw. auf die neue Regel umgeschrieben, nicht
  ergänzt.
- **Dateien**:
  - `conflict/ConflictResolver.java`: Signatur `resolve(String text, List<Finding> candidates)`; Prüfung, Sortierung,
    Durchlauf und Gruppenabschluss genau nach 2.5 (Schritte 0–4). Ordnung `STAERKE` als eine `Comparator`-Konstante
    (Konfidenz ab, Länge ab, Start auf, Typrang, Typname, Quelle, Originalwert); Sortierschlüssel für Schritt 1 =
    `start` auf, `end` ab, dann `STAERKE`. Javadoc beschreibt Enthaltensein, Gruppe/Kette, Typ, Konfidenz, Quelle,
    Laufzeit und die Ausnahmen; keine Meldung enthält Werte.
  - `model/Finding.java`: Konstante `public static final String SOURCE_MERGED = "zusammengelegt";` mit Javadoc
    („Quelle eines vom ConflictResolver aus teilweise überlappenden Kandidaten zusammengelegten Fundes; Konfidenz =
    schwächstes beteiligtes Glied; Erkenner verwenden diese Quelle nicht“); Javadoc des Parameters `source` nennt
    beide Konstanten. Keine weiteren Änderungen (kein neues Feld, `toString()` unverändert).
  - `recognizer/TextScanner.java`: Aufruf `ConflictResolver.resolve(text, candidates)`; `recognizer.detect(text)` in
    `try`/`catch (RuntimeException e)` mit Indexzählung, Ersatz durch `new IllegalStateException("Erkenner an Index "
    + i + " fehlgeschlagen: " + e.getClass().getName())` ohne `cause`, ohne `addSuppressed` (2.9, TE-20). Bestehende
    Prüfungen (Ergebnis `null`, Element `null`, Fund passt nicht zum Text) bleiben außerhalb des `try`. Javadoc
    `@throws` ergänzen.
  - `recognizer/Recognizer.java`: Javadoc ergänzen: Quelle `zusammengelegt` ist dem Resolver vorbehalten; Ausnahmen
    eines Erkenners werden vom Scanner ohne Meldung weitergegeben, Erkenner sollen trotzdem keinen Text in Meldungen
    schreiben.
  - Tests: `conflict/ConflictResolverTest` (überarbeitet), `conflict/ConflictResolverEigenschaftTest` (neu),
    `recognizer/TextScannerTest` (ergänzt), `model/FindingTest` (ergänzt).
- **AK**: AK-1, AK-6 (Stufe „fraglich“ bleibt; Stufe des zusammengelegten Fundes; Enthaltensein behält Stufe des
  umschließenden), AK-7 (a)–(f) und (g) mit Stub-Erkennern, AK-8 (inkl. zusammengelegter Funde,
  Reihenfolgeunabhängigkeit), AK-23 (keine neuen Fundgrenzen, Eigenschaftstest), AK-24 (Regel mit Stubs), AK-46
  (Scan), AK-47 (Scan, Resolver), AK-49 (Konfliktauflösung, lange Ketten), AK-50 (Meldungen von Resolver und Scanner,
  `toString` mit Quelle `zusammengelegt`), AK-54.
- **Tests** – Grundsatz: Jede Regel hat einen eigenen Test, in dem das nachrangige Kriterium das **Gegenteil** des
  erwarteten Ergebnisses liefern würde; vertauschte Kriterien oder eine fehlende Regel machen den Test rot. Stub-Funde
  werden über eine Testhilfe aus einem festen Text geschnitten (Originalwert = `text.substring(start, end)`), damit die
  Prüfung in Schritt 0 besteht. Jeder Test prüft Bereich, Typ, Konfidenz und Quelle des Ergebnisses.
  - `ConflictResolverTest`, **Enthaltensein**:
    - E-1 Enthaltensein vor Konfidenz: EMAIL [5,10) 1.0 in PFAD [0,20) 0.80 → genau der PFAD-Kandidat (`equals`),
      Konfidenz 0.80, Quelle unverändert (nicht `zusammengelegt`).
    - E-2 Stufe des umschließenden: FRAGLICH-Kandidat (0.30) in SICHER-Kandidat (0.95) → Ergebnis SICHER (AK-6).
    - E-3 Randfälle der Sortierung: [0,10) in [0,20) (gleicher Start) und [10,20) in [0,20) (gleiches Ende) → je nur
      [0,20).
    - E-4 dreifach verschachtelt → nur der äußerste.
  - **Gleicher Bereich**:
    - G-1 Konfidenz vor Typrang: PFAD 0.95 und URL 0.80 auf [0,10) → PFAD.
    - G-2 Typrang bei gleicher Konfidenz: PFAD und URL 0.95 auf [0,10) → URL, auch in umgekehrter Eingabereihenfolge.
    - G-3 vollständig gleiche Duplikate → ein Fund.
  - **Zusammenlegung und Ketten**:
    - Z-1 [0,10) und [5,15) → genau ein Fund [0,15), Originalwert = `text.substring(0,15)`, Quelle `zusammengelegt`.
    - Z-2 AK-7 (c): A [0,10), B [5,15), C [12,20) → genau ein Fund [0,20); ebenso in umgekehrter und gemischter
      Eingabereihenfolge.
    - Z-3 AK-7 (e): [0,5) und [5,10) → zwei unveränderte Funde; [0,5) und [6,10) → zwei Funde, Position 5 in keinem.
    - Z-4 zwei getrennte Ketten im selben Text → zwei zusammengelegte Funde, aufsteigend nach `start`.
    - Z-5 Mitglied nur in der Vereinigung: A [0,10), B [5,15), C [8,20) → ein Fund [0,20), B ist beteiligt (siehe K-3).
  - **Typ** (OF-16 (a), nur aus maximalen Gliedern):
    - T-a Konfidenz vor Länge und Start: IP [0,12) 0.80 (früher, länger) und EMAIL [8,14) 0.95 → Typ EMAIL.
    - T-b Länge vor Start und Typrang: URL [0,6) 0.90 und PFAD [3,15) 0.90 → Typ PFAD (Start und Typrang ergäben URL).
    - T-c Start vor Typrang: IP [0,8) 0.90 und URL [4,12) 0.90 (gleiche Länge) → Typ IP.
    - T-d Enthaltener liefert keinen Typ: URL [0,10) 0.90 enthält EMAIL [2,6) 1.0, PFAD [8,16) 0.80 überlappt URL →
      ein Fund [0,16), Typ URL (nicht EMAIL trotz höchster Konfidenz).
  - **Konfidenz und Stufe** (OF-16 (b)):
    - K-1 Schwächstes Glied, nicht Typgeber: PFAD [0,10) 0.95 und IP [5,12) 0.40 → Typ PFAD, Konfidenz 0.40, Stufe
      FRAGLICH, Quelle `zusammengelegt`.
    - K-2 Enthaltener zählt nicht: URL [0,10) 0.95 enthält X [2,4) 0.30, PFAD [8,15) 0.85 → Konfidenz 0.85, Stufe
      WAHRSCHEINLICH (nicht FRAGLICH).
    - K-3 Glied nur in der Vereinigung zählt: A [0,10) 0.95, B [5,15) 0.50, C [8,20) 0.95 → Konfidenz 0.50.
    - K-4 alle Glieder sicher → Stufe SICHER, Quelle `zusammengelegt`.
  - **Beispiel AK-7 (g) mit Stubs** (Regression des Sicherheitsbefunds): Text
    `Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende`; Kandidaten URL
    [5,41) 0.95 `url`, PFAD [34,83) 0.95 `pfad-laufwerk`, EMAIL [47,77) 0.95 `email` (Positionen im Test per
    `indexOf` herleiten und gegen diese Zahlen prüfen) → genau ein Fund [5,83), Typ PFAD (gleiche Konfidenz, PFAD
    länger), Konfidenz 0.95, Quelle `zusammengelegt`; `text.charAt(83) == '\''`; keine Position aus [5,83) unbedeckt.
  - **Eingaben und Fehler**: Ergebnis unveränderlich; Eingabeliste unverändert; zwei Aufrufe gleich; alle
    Permutationen einer Liste aus vier Kandidaten (Kette mit Enthaltensein) → gleiches Ergebnis; leere Liste → leer;
    `null` Text/Liste/Element → NPE mit Meldung `text`/`candidates`/`candidates enthält null`; Kandidat mit falschem
    Wert und mit `end > text.length()` → `IllegalArgumentException` mit Listenindex, Meldung ohne Marker-Wert.
  - **Grenzlast** (AK-49, je < 2 s): Kette aus 100 000 Kandidaten [2i, 2i+3) → genau ein Fund; dieselbe Kette mit
    fester Mischung (`new Random(Konstante)`); 10 000 verschachtelte [i, N−i) → genau der äußerste; 100 000 disjunkte
    → 100 000 Funde. Gemessen wird nur der Aufruf von `resolve`, nicht der Aufbau der Kandidaten.
    *(Korrektur 19.11.2026 nach reviewer-Befund T-2, Durchlauf 2: Die frühere Vorgabe von 100 000 verschachtelten
    Kandidaten ist nicht durchführbar, weil jeder Kandidat seinen eigenen Originalwert hält und die Verschachtelung
    damit quadratisch Speicher braucht – rund 10 GB. Mit 10 000 sind es rund 100 MB. Die Zeitgrenze von 2 s bleibt.)*
    *(Korrektur 19.11.2026 nach security-Befund T-2, Durchlauf 2: Der Schlüssel „Originalwert“ entfällt aus der
    Stärke-Ordnung. Er ist überflüssig – gleicher Bereich im selben Text ergibt denselben Wert – und kostet je
    Vergleich Zeit proportional zur Fundlänge. Die Ordnung endet bei „Quelle“; das gilt auch für Kap. 2.5 und TE-19.)*
  - `ConflictResolverEigenschaftTest`: 2 000 zufällige Kandidatenmengen (`new Random(Konstante)`, 0–30 Kandidaten auf
    einem Text der Länge 60, Typen URL/PFAD/EMAIL/IP, Konfidenzen aus {0.30, 0.80, 0.85, 0.95, 1.0}); geprüft gegen
    eine naive Referenz im Test (O(n²): Enthaltensein paarweise, Gruppen per Überlappung): (1) überlappungsfrei, nach
    `start` sortiert; (2) Menge der abgedeckten Positionen des Ergebnisses = Vereinigung der Kandidatenpositionen
    (⊇ ist AK-7 (a), ⊆ ist AK-7 (e)); (3) jeder Ergebnisfund ist ein Eingabekandidat oder hat Quelle `zusammengelegt`,
    Originalwert = Teilstring; (4) bei `zusammengelegt`: Typ = Typ des nach `STAERKE` stärksten maximalen Kandidaten
    der Gruppe, Konfidenz = Minimum der maximalen Kandidaten; (5) gemischte Eingabe → gleiches Ergebnis. Meldung bei
    Fehlschlag: Startwert und Iteration, keine Texte.
  - `TextScannerTest` (ergänzt): drei Stub-Erkenner liefern die Kandidaten aus AK-7 (g) → ein Fund wie oben, auch bei
    vertauschter Erkenner-Reihenfolge; Stub-Erkenner wirft `RuntimeException` mit Marker in Meldung und in `cause` →
    `IllegalStateException`, Meldung enthält Index und Klassennamen, nicht den Marker; `getCause() == null`;
    `getSuppressed().length == 0`; bestehende Tests (`FRAGLICH`-Fund bleibt, `""`/Leerraum → leer, `null` → NPE
    „text“, Erkenner liefert `null`/unpassenden Fund) bleiben.
  - `FindingTest` (ergänzt): `SOURCE_MERGED` ist eine gültige Quelle; `toString()` enthält `source=zusammengelegt` und
    nicht den Originalwert.

### T-3: E-Mail-Erkenner
- **Dateien**: `recognizer/general/EmailRecognizer.java`, `TextScanner.standard()`; Test `EmailRecognizerTest`.
- **AK**: AK-1, AK-2, AK-3 (E-Mail), AK-6 (Konstante), AK-8 (`Das Wetter ist heute schön.`), AK-49 (`a`, `a@`).
- **Tests**: AK-2-Beispiel; Schlusspunkt/Komma nicht im Fund; Umlaut im lokalen Teil nie als Teiltreffer; mehrere
  Adressen; Position = Teilstring; 100 000 × `a` und × `a@` je < 2 s.

### T-4: IPv4-Erkenner
- **Dateien**: `recognizer/general/Ipv4Recognizer.java`, `standard()`; Test `Ipv4RecognizerTest`.
- **AK**: AK-1, AK-4, AK-6, AK-49 (`1.`, lange Ziffernreihen).
- **Tests**: `Server 192.0.2.10 antwortet nicht` → ein Fund; `300.1.1.1`, `192.0.2`, `192.0.2.10.5`, `192.0.2.010` →
  keiner; IP vor Satzpunkt; alle drei Dokumentationsbereiche; 100 000 × `1.` und 100 000 Ziffern je < 2 s.
- *(Nachtrag 19.11.2026, Nutzerentscheidung nach security-Hinweisen zu T-4 – ersetzt die Zeile „Tests“ und die
  Zeile `IP` der Tabelle in Kap. 2.3, soweit sie widersprechen:*
  1. *Umsetzung als linearer Durchlauf über Zahlenketten (Ziffernblöcke `[0-9]`, getrennt durch je genau einen Punkt,
     dem eine Ziffer folgt) statt Regex; vom reviewer als planverträglich bewertet.*
  2. *Führende Nullen sind erlaubt: Ein Block ist ein gültiges Oktett, wenn er 1 bis 3 Ziffern hat und sein Wert
     höchstens 255 ist. `192.0.2.010` und `192.000.002.010` → ein Fund.*
  3. *Ketten mit mehr als vier Blöcken: Sind die ersten vier oder die letzten vier Blöcke gültige Oktette, ist die
     GANZE Kette ein Fund (Typ IP, zu großer Fund statt kein Fund). `192.0.2.10.5` und `192.0.2.10.52344` (Adresse
     mit angehängtem Port) → je ein Fund über die ganze Kette. Sonst kein Fund. Bekannte Folge: Versionsnummern wie
     `1.2.3.4.5` werden ebenfalls ersetzt.*
  4. *Unverändert kein Fund: `300.1.1.1`, `192.0.2`, `1.2.3.4567` (genau vier Blöcke, einer ungültig; weniger als
     vier Blöcke).*
  3a. *(Erweiterung von Punkt 3 nach security-Befund T-4, Durchlauf 2, Schwere mittel: „erste oder letzte vier“ ließ
     Adressen in der Mitte einer Kette ohne Fund, z. B. `19.11.2026.192.0.2.10.52344`.) Es gilt stattdessen:
     Enthält eine Kette IRGENDWO mindestens vier aufeinanderfolgende gültige Blöcke, ist die ganze Kette ein Fund.
     `999.192.0.2.10.999`, `300.192.0.2.10.300` → je ein Fund über die ganze Kette. Kein Fund nur, wenn es keine
     vier aufeinanderfolgenden gültigen Blöcke gibt (`192.0.2.300.1.2.300`, `300.300.1.300.300`).*
  4a. *Laufzeitprobe AK-49: `1.` × 100 000 bleibt Pflicht; nach Punkt 3 ist das Ergebnis EIN Fund über die Kette
     (ohne Schlusspunkt), unter 2 s. Ausnahme von Punkt 5 (Nutzerentscheidung nach qa-Befund T-4): Rein künstliche
     Laufzeit-Eingaben aus wiederholten Einzelzeichen (`1.` × n) dürfen von den Dokumentationsbereichen abweichen;
     der `TestdatenSynthetischTest` (T-20) muss diese Ausnahme kennen.*
  5. *Testdaten: gültige Adressen nur aus 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 (Kap. 5 Nr. 5, T-20).*

### T-5: URL-Erkenner
- **Dateien**: `recognizer/general/UrlRecognizer.java`, `standard()`; Tests `UrlRecognizerTest`, `TextScannerTest`.
- **AK**: AK-1, AK-3 (URL), AK-5, AK-6, AK-49 (lange Zeichenfolge ohne Leerzeichen).
- **Tests**: AK-5-Beispiel als ein Fund; Satzzeichen am Ende entfernt; `(https://example.invalid/x)` ohne `)`,
  `https://example.invalid/a_(b)` mit `)`; Schema-Schreibung; Scanner: `http://192.0.2.10/status` → nur URL, URL mit
  E-Mail im Query → nur URL; `file:///…` → kein URL-Fund; 100 000 Zeichen ohne Leerzeichen mit/ohne `https://` < 2 s.
- *(Nachtrag 19.11.2026 zu T-5 – ersetzt die Zeile `URL` der Tabelle in Kap. 2.3 und TE-13 (e), soweit sie
  widersprechen:*
  1. *Umsetzung als am `://` verankerter linearer Durchlauf statt Regex, ohne Lookbehind: Text direkt vor dem Schema
     verhindert den Fund nicht; der Fund beginnt am Schema (vom reviewer als planverträglich bewertet).*
  2. *Rumpf endet bei Leerraum (`\s`, `\p{Z}`), Steuerzeichen (Cc), einzelnem Surrogat, `<`, `>`, `"`.
     Formatzeichen (Cf), nicht zugewiesene (Cn) und Private-Use-Zeichen (Co) beenden ihn NICHT (größerer Fund statt
     Klartext-Rest). Endzeichen `. , ; : ! ? ' "` und überzählige `)`/`]` werden gekürzt; `}`, Backtick und
     typografische Anführungszeichen bleiben im Fund. Zwei URLs ohne Leerraum ergeben einen Fund.*
  3. *Nutzerentscheidung (A-2 geändert): Jedes Schema ist ein URL-Fund. Schema = `[A-Za-z][A-Za-z0-9+.-]*` direkt vor
     `://`, so lang wie möglich nach links gelesen (höchstens 32 Zeichen; ist der Lauf länger, zählen die letzten
     32). Ein vorangestelltes Präfix aus `[A-Za-z][A-Za-z0-9+.-]*:` gehört dazu (`jdbc:postgresql://…`,
     `jdbc:mysql://…`), auch mehrfach. Beispiele: `ftp://nutzer:geheim@host.example/p`, `sftp://…`, `smb://…`,
     `ldap://…`, `mongodb+srv://…`, `wss://…?token=…` → je ein Fund über die ganze Adresse.*
  4. *Ausnahme: Schema `file` (Groß/klein egal) ergibt KEINEN URL-Fund; das regelt T-16 (PFAD, OF-8/AK-24e).*
  5. *Bekannte Grenzen, vom Nutzer am 19.11.2026 NICHT zur Behebung gewählt (Hinweis im späteren Prüfdialog):
     (a) unsichtbares Zeichen im Schema oder im `://` → kein Fund; (b) `"`, `<`, `>` in Zugangsdaten oder Query
     beenden den Fund, der Rest bleibt Klartext; (c) über einen Zeilenumbruch umbrochene URL: der Teil in der
     Folgezeile bleibt Klartext; (d) Adressen ohne `://` (`www.…`, `mailto:`) sind keine URL-Funde.*
  6. *Testdaten: Hosts nur `*.invalid`, `*.example`, `example.com/.org/.net`; IPv6 nur aus `2001:db8::/32`.*

### T-6: Pseudonymisierung (Grundablauf)
- **Dateien**: `pseudonymize/PlaceholderSyntax.java` (nur `format`), `PlaceholderMapping.java`,
  `PseudonymizationResult.java`, `Pseudonymizer.java` (ohne T-7-Prüfung, ohne Literalschutz); Tests
  `PseudonymizerTest`, `PlaceholderMappingTest`.
- **AK**: AK-25 (mit manuellem `PFAD`-Fund), AK-26, AK-27, AK-28, AK-29, AK-30 (ohne `[[`), AK-31, AK-33, AK-34,
  AK-35, AK-46, AK-47, AK-50 (`toString`).
- **Tests**: AK-27-Beispiel mit manuellen Funden, unsortiert übergeben; `Start C:\Tools\BeispielTool\tool.exe jetzt` →
  `Start [[PFAD_1]] jetzt`, Ausgabe enthält weder `Tools`, `BeispielTool`, `tool.exe`, `C:` noch `\`; zwei Pfade mit
  gemeinsamem Anfang und Präfix-Pfad → zwei Platzhalter; gleicher Wert mehrfach → gleicher Platzhalter; Schreibvarianten
  (`c:\…`, `C:/…`, mit/ohne abschließenden Trenner) → verschiedene; gleicher Wert unter zwei Typen → zwei; zwei
  Durchläufe je ab 1; weggelassener Fund bleibt Klartext; `PERSON` „manuell“, manueller `tool.exe`-Fund; Tabs,
  Mehrfach-Leerzeichen, `\n`/`\r\n`/`\r`, gleiche Zeilenzahl; Fund am Anfang, Ende, ganzer Text, aneinandergrenzend;
  Zuordnung nur für Ersetztes; `""`/Leerraum; `toString` ohne Marker.

### T-7: Eingabeprüfung der Pseudonymisierung
- **Dateien**: `pseudonymize/InvalidFindingException.java`, `Pseudonymizer.java`; Test `PseudonymizerValidierungTest`.
- **AK**: AK-32 (inkl. Ergänzung D-8: keine Zusammenlegung in der Pseudonymisierung), AK-47 (Pseudonymisierung),
  AK-50 (Meldungen).
- **Tests**: je Fall ein Test mit erwartetem `Reason`: `null`-Element, `end` > Länge, Überlappung (auch unsortiert;
  ausdrücklich: zwei teilweise überlappende Funde werden mit `OVERLAP` abgelehnt, nicht zusammengelegt), Wert passt
  nicht, Fund trennt Surrogatpaar (Anfang und Ende); `null` Text/Liste → NPE mit Parametername; kein Teilergebnis;
  keine Meldung enthält den Marker.

### T-8: Rückübersetzung
- **Dateien**: `PlaceholderSyntax.java` (T/D, `tokenize`, `Token`), `reidentify/Reidentifier.java`,
  `ReidentificationResult.java`, `UnresolvedPlaceholder.java`; Tests `PlaceholderSyntaxTest`, `ReidentifierTest`.
- **AK**: AK-38 (Einsetzen ohne Umdeutung), AK-40, AK-41, AK-42, AK-43, AK-44, AK-45 (Teil Zufallseingaben, Grundlage),
  AK-46, AK-47, AK-50.
- **Tests**: Platzhalter umgestellt, verdoppelt, eingebettet, weggelassen; ≥ 12 Platzhalter, `_1`/`_11`, `_2`/`_12`;
  `[[PFAD_99]]` → `UNBEKANNT`; `[[PFAD_1]`, `[PFAD_1]`, `[[PFAD_]]` → `BESCHAEDIGT`; `arr[idx_0]` ohne Meldung;
  je ein Test `[[pfad_1]]`, `[[ PFAD_1]]`, `[[PFAD_1 ]]`, `[[ PFAD_1 ]]`; Zuordnungswerte mit `$`, `$1`, `\1`,
  `${HOME}`, `%APPDATA%`, `\\` werden zeichengleich eingesetzt; manueller Wert `[[IP_1]]` nicht erneut aufgelöst;
  `""`, Text ohne Platzhalter mit leerer Zuordnung unverändert; `null` → NPE; Meldungen/`toString` ohne Marker.
- *(Nachtrag 19.11.2026 zu T-8, Abweichung von Kap. 2.2, vom reviewer als vertretbar bewertet: `FindingType` hat
  zusätzlich die öffentlichen Methoden `isNameStart(char)`, `isNameChar(char)`, `isValidName(String)`. Sie sind die
  einzige Definition der Typnamen-Regel; `PlaceholderSyntax` (parseCanonical, tokenize) und der Konstruktor nutzen
  sie. T und D sind als handgeschriebener Scanner umgesetzt, nicht als Regex. Bekannte Grenzen (security, niedrig):
  (a) Die Ausgabe der Rückübersetzung hat keine Obergrenze (TE-4) – eine kleine KI-Antwort mit vielen Platzhaltern
  und sehr langen Originalwerten kann den Speicher erschöpfen; die Grenze gehört an die UI-/IO-Schnittstelle, und
  die App darf keinen Heap-Dump bei Speichermangel schreiben. (b) Platzhalter-ähnliche Formen, die weder T noch D
  sind (Vollbreite-Klammern, unsichtbare Zeichen, mehr als 8 Leerzeichen), bleiben ohne Meldung stehen; die UI
  sollte auf verbleibende `[[`/`]]` hinweisen.)*

### T-9: Literalschutz (Escaping)
- **Dateien**: `Pseudonymizer.java` (Schritt 3 aus 2.6); Test `EscapingRoundtripTest`.
- **AK**: AK-30 (mit `[[`), AK-36, AK-44 (Roundtrip mit `[[IP_1]]` als manuellem Wert).
- **Tests**: `Siehe [[PFAD_1]] und C:\Tools\a.exe` (manueller PFAD-Fund) → `[[TEXT_1]]`/`[[PFAD_2]]`, Roundtrip
  gleich, keine Meldung; je ein Roundtrip für `[[`, `]]`, `[[[[X_1]]]]`, `[[PERSON_1`, `[[ pfad_1 ]]`, originales
  `[[TEXT_1]]`, `[PFAD_1]` mit PFAD-Fund; ohne Funde und ohne T-Muster Ausgabe identisch.

### T-10: Pfad-Erkenner – Grundgerüst und Laufwerkspfade
- **Dateien**: `recognizer/general/PathChars.java`, `PathForm.java` (nur `LAUFWERK`), `PathRecognizer.java`
  (Läufe, Startgrenze, Kürzung, Klammerregel, Segmentzählung nach 2.4.1/2.4.4, **ohne** Anführungszeichen),
  `TextScanner.standard()`; Tests `PathCharsTest`, `PathRecognizerLaufwerkTest`.
- **AK**: AK-1, AK-6 (Konfidenz je Form), AK-9, AK-10, AK-17 (a), AK-18 (Laufwerksbeispiele), AK-19, AK-20 (`(…)`,
  `<…>`), AK-21, AK-22 (Laufwerk), AK-23 (ohne Anführungszeichen), AK-49 (`C:\`, `C:\a `, `C:\a` + 100 000 Wörter,
  lange Zeichenfolge ohne Leerzeichen).
- **Tests**: alle Positiv-/Negativbeispiele der Zeile `LAUFWERK` (2.4.2); AK-17 (a) einschließlich
  Pseudonymisierungsergebnis `Starte [[PFAD_1]] Files\Mein Tool\tool.exe und prüfe das Log.` und kein Fund im Rest;
  Satzzeichen `. , ; : ! ?` am Ende, Pfad am Zeilen- und Textende, Pfad endet an `\n`, `\r`, Tab; `(x86)` ganz,
  `(siehe C:\Tools\a.exe)` ohne `)`; `<C:\Tools\a.exe>`; `>C:\Logs\a.log`; `tool.exe`, `BeispielTool.exe läuft`,
  `Datei konfig.yml` → leer; Umlaute/ß/`e`+U+0301/Emoji im Pfad bis zum Leerzeichen, Fundgrenzen nicht in Paar oder
  Kombinationsfolge; `/log=`-Fall dokumentiert (vor T-13 noch Laufwerksfund `C:\Logs\a.log`, Test in T-13 angepasst);
  Grenzlast je < 2 s, zusätzlich `(`×100 000 + `C:\a` + `)`×100 000.
- *(Nachtrag 19.11.2026 zu T-10 – Anwendung des Grundsatzes aus Kap. 2.3 („zu großer oder Teilfund besser als kein
  Fund", vom Nutzer nach den Befunden zu T-3 bis T-5 bestätigt); ersetzt in Zeile `LAUFWERK` (2.4.2) das
  Negativbeispiel `xC:\a`:*
  1. *Für Laufwerkspfade gilt KEINE Startgrenze. Der Laufwerksbuchstabe ist der ASCII-Buchstabe direkt vor `:` plus
     Trenner; der Fund beginnt dort. Text direkt davor verhindert den Fund nicht und gehört nicht dazu:
     `xC:\a` → `C:\a`; `Pfad:C:\Tools\a.exe`, `x=C:\…`, `abcC:\…` → Fund ab `C`.*
  2. *Bekannte Folge (zu große Funde, kein Leck): Ein Wort, das auf einen Buchstaben endet, gefolgt von `:\` oder
     `:/` und mindestens einem Segment, ergibt einen Fund ab dem letzten Buchstaben (`Verzeichnis:\Tools\a.exe` →
     `s:\Tools\a.exe`). In URLs liegt ein solcher Kandidat (`s://…`) ganz im URL-Fund und wird verdrängt.*
  3. *Unsichtbare Formatzeichen (Cf) beenden den Pfad nicht; Leerraum einschließlich NBSP, Steuerzeichen und
     `"`, Backtick, `<`, `>`, `|` beenden ihn.*
  3a. *Bekannte Grenzen (security T-10, niedrig, als sehr selten eingeschätzt; dem Nutzer am 19.11.2026 berichtet,
     Hinweis im späteren Prüfdialog): (a) unsichtbares Formatzeichen im Kopf (zwischen Laufwerksbuchstabe, `:` und
     Trenner) → kein Fund; (b) Ersatzzeichen statt `:` oder Trenner (Vollbreite U+FF1A/U+FF3C/U+FF23, Yen U+00A5,
     Won U+20A9, Division-Slash U+2215) → kein Fund; (c) NBSP, U+202F, U+3000 beenden den Pfad wie ein Leerzeichen
     (D-5), der Rest bleibt Klartext; (d) `<`, `>`, `|`, `"` beenden den Pfad (in Windows-Namen ungültig).*
  4. *Offen für T-11 bis T-13: Ob die Startgrenze auch für UNC-, Unix- und relative Pfade entfällt, wird dort nach
     demselben Grundsatz entschieden (bei `/`-Pfaden ist das Risiko von Fehltreffern in normalem Text höher; die
     Entscheidung ist je Form zu begründen und zu testen). `file:///C:/…` lässt bis T-16 `fil` sichtbar.*

### T-11: Pfad-Erkenner – Anführungszeichen; Zusammenlegung mit echten Erkennern
- **Dateien**: `PathRecognizer.java` (Regel 2.4.3 inkl. Rückfall); Tests `PathRecognizerAnfuehrungTest`,
  `recognizer/TextScannerZusammenlegungTest` (neu).
- **AK**: AK-7 (e), (g) mit echten Erkennern, AK-16, AK-17 (c), AK-20, AK-23 (in Anführungszeichen), AK-49 (`"`
  ×100 000, `"C:\ a b c …` nie geschlossen).
- **Warum hier**: URL (T-5) und Laufwerkspfad (T-10) gibt es ab T-10 gemeinsam, aber erst mit der
  Anführungszeichenregel entsteht im Beispiel AK-7 (g) der Pfad-Kandidat über das Leerzeichen hinaus und damit die
  Teilüberlappung. In T-10 wäre der Pfad-Kandidat nur `C:/Akte` und läge ganz in der URL.
- **Tests**: `Starte "C:\Program Files\Mein Tool\tool.exe" jetzt` → Inhalt als Fund; dasselbe mit `'…'` und
  `` `…` ``; `"C:\Tools\BeispielTool\tool.exe"` → `"[[PFAD_1]]"` nach Pseudonymisierung;
  `C:\Users\mustermann\Übersicht\Größe ✓.txt` und Emoji in Anführungszeichen ganz; nie geschlossen → Rückfall
  (`C:\Program`); Anführungszeichen erst nach Zeilenende → Rückfall; `|` vor dem Schluss → Rückfall; `"C:\"` → kein
  Fund; `'C:\a' und 'C:\b'` → zwei Funde; Grenzlast: `"`×100 000, `"C:\ a b c ` mit 100 000 Leerzeichen-Wörtern,
  `'C:\a `×100 000, `"C:\a|`×100 000 je < 2 s.
- **Tests `TextScannerZusammenlegungTest`** (mit `TextScanner.standard()`):
  - Regression AK-7 (g): `Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende`.
    Zuerst die Prämisse je Erkenner einzeln: `UrlRecognizer` liefert `https://intranet.invalid/?f='C:/Akte`,
    `PathRecognizer` `C:/Akte 2025/erika.beispiel@example.invalid/x.pdf`, `EmailRecognizer`
    `erika.beispiel@example.invalid`. Dann Scan: genau ein Fund [5,83), Typ PFAD, Konfidenz 0.95, Quelle
    `zusammengelegt`. Pseudonymisierung aller Funde → exakt `Link [[PFAD_1]]' ende`; Ausgabe enthält keinen der
    Bestandteile `intranet.invalid`, `Akte`, `2025`, `erika.beispiel@example.invalid`, `x.pdf`; Roundtrip gleich.
  - AK-7 (e) mit echten Erkennern: `https://intranet.invalid/?f="C:/Akte 2025/x.pdf"` → zwei Funde (URL, PFAD), keine
    Zusammenlegung, `"` zwischen den Platzhaltern bleibt.
- *(Nachtrag 19.11.2026 zu T-11 – Anwendung des Grundsatzes aus Kap. 2.3; ergänzt Kap. 2.4.3/2.4.5:*
  1. *Obermengen-Garantie: Das Ergebnis mit Anführungszeichenregel ist die Vereinigung aus dem Ergebnis ohne sie
     (T-10) und den Zitat-Funden; kein Fund wird durch die Regel kleiner. Differenz-Eigenschaftstest gegen
     `detectWithoutQuotes` (paketprivat).*
  2. *Der Pfadkopf muss nicht direkt nach dem öffnenden Zeichen stehen: Der Fund reicht vom ersten Pfadkopf im Zitat
     bis zum schließenden Zeichen (`"Siehe C:\Mein Ordner\a.txt"` → `C:\Mein Ordner\a.txt`). Mehrere Pfade in einem
     Zitat ergeben einen Fund.*
  3. *Als Anführungszeichen gelten zusätzlich die typografischen Zeichen U+201E, U+201C, U+201D, U+00AB, U+00BB,
     U+2018, U+2019, U+201A, U+2039, U+203A; innerhalb der doppelten bzw. der einfachen Familie schließen sie
     einander auch gemischt. Backtick schließt nur mit Backtick.*
  4. *Die Suche nach dem schließenden Zeichen endet an Steuerzeichen (Zeilenende, Tab), U+2028, U+2029, `<`, `>`,
     `|`; fehlt es, gilt das Ergebnis ohne Regel (Ende am ersten Leerzeichen, D-5).*
  5. *Bekannte Folgen (zu große Funde, kein Leck): Zwei Apostrophe auf einer Zeile bilden ein Paar
     (`Peter's … C:\a.txt … Pauls'`); ein typografisches Schlusszeichen direkt am Pfadende ohne Leerzeichen bleibt
     im Fund (`„C:\a“` → `C:\a“`); bei Verschachtelung gewinnt das äußere Paar. Bekannte Grenze: Apostroph im Pfad
     innerhalb einfacher Anführungszeichen schließt das Paar vorzeitig.*
  5a. *Paarregel (nach reviewer-/security-Befund T-11, Durchlauf 1, Schwere mittel – ersetzt in Kap. 2.4.4 „Paar
     verbrauchen"): Jedes Anführungszeichen wird als öffnendes probiert und mit dem nächsten schließenden Zeichen
     seiner Familie gepaart. KEIN Paar wird verbraucht (Korrektur nach security-Befund Durchlauf 2: auch das
     Verbrauchen nur bei Fund ließ ein unpaariges Zeichen einem späteren Zitat das öffnende Zeichen wegnehmen,
     wenn dazwischen ein weiterer Pfad stand, z. B. `Peter's C:\Temp und 'D:\Mein Ordner\a.txt'`). Alle
     Zitat-Funde werden gesammelt, nach Start sortiert und mit dem Ergebnis ohne Regel vereinigt; überlappende
     Funde verschmelzen, angrenzende bleiben getrennt. Ein einzelnes Zeichen davor (Zoll-Angabe `24"`, Apostroph in `can't`, maskiertes
     `\"` in JSON) verschiebt die Paare damit nicht. Bekannte Folge: `"x" C:\a b "y"` → Fund `C:\a b ` (zu groß).
     Bekannte Grenzen mit dokumentierenden Tests: Zeilenumbruch oder Tab im Zitat, gemischte ASCII-Paare
     (`"…'`) → Schutz nur bis zum ersten Leerzeichen. Die Vereinigung der Fundlisten führt nur Grenzen mit und
     bildet den Teilstring einmal je Fund (lineare Laufzeit auch bei Überlappungsketten).*
  6. *Prämissen zu AK-7 (g) seit dem T-10-Nachtrag: Der Pfad-Kandidat beginnt bei `s://…` aus `https://`, der
     E-Mail-Kandidat schließt `2025/` ein; das Scanner-Ergebnis ist wie geplant ein zusammengelegter Fund.*

### T-12: Pfad-Erkenner – UNC
- **Dateien**: `PathForm.java` (`UNC`); Test `PathRecognizerUncTest`.
- **AK**: AK-11, AK-17 (b), AK-22 (UNC), AK-49 (`\\`).
- **Tests**: Zeile `UNC` (2.4.2) positiv/negativ; `Ablage \\srv-beispiel\Mein Ordner\datei.txt` → `\\srv-beispiel\Mein`,
  Rest kein Fund; in Anführungszeichen ganz; `\\\\srv-beispiel\\freigabe\\x` mit geprüfter Backslash-Anzahl;
  `\\?\C:\Tools\a.exe` nach Entscheidung N-2; `\\`×100 000 < 2 s.
- *(Nachtrag 19.11.2026 zu T-12 – Anwendung des Grundsatzes aus Kap. 2.3; ersetzt in Zeile `UNC` (2.4.2)
  widersprechende Angaben:*
  1. *Kopf: ein maximaler Lauf aus MINDESTENS zwei Backslashes, danach ein Nicht-Trenner (Plan bisher: genau 2 oder
     4). Auch 3, 5, 6 … Backslashes ergeben einen Fund – ein beim Maskieren verlorener oder zusätzlicher Backslash
     ließe den Pfad sonst im Klartext.*
  2. *Keine Startgrenze für UNC (Entscheidung zu T-10 Punkt 4): `x=\\srv…`, `(\\srv…)`, `siehe\\srv…` → Fund ab den
     Backslashes. `Pfad:\\srv…` → Fund ab dem Buchstaben vor `:\` (Laufwerksregel), der UNC-Pfad liegt ganz darin.
     Bekannte Folge (zu große Funde): maskierte Texte wie `a\\nb\\nc` ergeben einen Fund.*
  3. *`\\?\…` und `\\.\…` sind kein Sonderfall; `?` bzw. `.` zählt als erstes Segment (`\\?\C:\Tools\a.exe`,
     `\\?\UNC\srv\freigabe\a.txt`, `\\.\pipe\name` → je ein Fund).*
  4. *Obermengen-Garantie je Form: Das Ergebnis ohne Anführungszeichen ist die Vereinigung aus dem Durchlauf mit
     allen Formen und je einem Durchlauf mit nur einer Form; keine neue Form kann den Fund einer anderen
     verkleinern. Differenztest gegen den Durchlauf nur mit `LAUFWERK`.*
  5. *Schrägstrich-Form `//srv/freigabe/…` gehört zur Form UNIX (T-13) und bleibt bis dahin ohne Fund.*
  6. *Bekannte Grenze: unsichtbares Zeichen zwischen den führenden Backslashes → kein Fund (wie T-10 3a).*
  7. *Nutzerentscheidung 20.11.2026 nach security-Hinweis (ersetzt OF-7 für UNC und das Negativbeispiel
     `\\srv-beispiel`): Ein UNC-Kopf mit nur EINEM Segment (Server ohne Freigabe: `\\fileserver01`,
     `\\fileserver01\`, „liegt auf \\fileserver01.") ist ein Fund vom Typ PFAD mit Konfidenz 0.60 (Stufe
     fraglich), Quelle `pfad-unc`. Ab zwei Segmenten bleibt es bei 0.95. Weiterhin kein Fund: `\\`, `\\\\`
     (kein Segment), `\\.`/`\\?` allein nach Kürzung ohne Segment. Beim Zusammenlegen gilt die niedrigste
     Konfidenz der Beteiligten (D-8).*
  7a. *(Präzisierung nach reviewer-Befund T-12, Durchlauf 2.) Beim Zusammenlegen überlappender Funde (Durchlauf mit
     und ohne Anführungszeichen) gilt: (i) Beginnt der längere Fund am selben Start wie der bisherige Bereich, ist
     der bisherige nur die kürzere Deutung desselben Kopfes (z. B. `\\srv` ohne Zitat, `"\\srv mit\freigabe"` mit
     Zitat); es gilt die Konfidenz des längeren Fundes, gebildet mit seiner eigenen Segmentzahl. Voraussetzung:
     Die Konfidenz jeder Form ist in der Segmentzahl nicht fallend (Vertrag von `PathForm.confidence`, auch für
     die Formen aus T-13 bis T-16). (ii) Verlängert ein Fund mit späterem Start den Bereich, gilt die niedrigste
     Konfidenz der Beteiligten (D-8). (iii) Ein Fund, der vollständig im Bereich liegt, ändert dessen Konfidenz
     nicht. `\\?\` und `\\.\` ohne weiteres Segment sind Funde mit 0.60 (`?` bzw. `.` ist das erste Segment,
     Punkt 3); `\\?` und `\\.` ohne Trenner bleiben kein Fund, weil die Kürzung das Segment entfernt.*
  8. *Nutzerentscheidung 20.11.2026, umzusetzen in T-14 (relative Pfade): Ein einzelner führender Backslash mit
     mindestens zwei Segmenten (`\srv\freigabe\a.txt`, `\Users\erika.mustermann\Desktop\x`) wird ein Fund mit
     Konfidenz 0.75 (Stufe wahrscheinlich). Entsteht, wenn Chat oder Wiki aus `\\` ein `\` machen.*
  9. *Folge-Feature (Nutzerentscheidung 20.11.2026, nicht Teil dieses Features): Erkenner für Zugangsdaten in
     Kommandozeilen (`/user:…`, `DOMÄNE\benutzer`, Passwort-Argument in `net use`, `-p`/`--password`,
     `password=`). Bis dahin bekannte Lücke: In `net use Z: \\srv\freigabe geheim /user:praxis\erika.mustermann`
     wird nur der Pfad ersetzt.*

### T-13: Pfad-Erkenner – Unix und Home
- **Dateien**: `PathForm.java` (`UNIX`, `HOME`); Test `PathRecognizerUnixTest`.
- **AK**: AK-12, AK-13, AK-18 (`/opt/beispiel/a, /opt/beispiel/b`), AK-49 (`/a`, `~/`).
- **Tests**: Zeilen `UNIX` und `HOME` positiv/negativ, insbesondere alle Negativbeispiele aus AK-13; Konfidenz 2 vs. 3
  Segmente; Startgrenze (`a/opt/x` kein Fund, `=/opt/x`, `(/opt/x)` Fund); `//srv-beispiel/freigabe` nach N-2;
  bekannte Grenzen `und /oder/beides`, `/log=C:\Logs\a.log` als ein Fund festgehalten; `/a`×100 000 und `~/`×100 000
  < 2 s.
- *(Nachtrag 20.11.2026 zu T-13 – Nutzerentscheidungen nach dem Bericht des engineer, Durchlauf 1; ergänzt die Zeilen
  `UNIX` und `HOME` (2.4.2) und ersetzt OF-4, soweit sie widersprechen:*
  1. *Die Startgrenze bleibt für UNIX und HOME bestehen (anders als bei LAUFWERK und UNC), damit gewöhnlicher Text
     wie `und/oder`, `01/2026`, `km/h` kein Fund wird. Zusätzliche Startgrenzen für UNIX und HOME: `,` `;` `|`
     (`Datei,/etc/hosts`, `a;/opt/x/y`, `cmd|/opt/x/y` → Fund ab dem `/`).*
  2. *HOME eines anderen Benutzers: `~name/…` mit `name` = `[A-Za-z_][A-Za-z0-9._-]*` direkt hinter `~`, Startgrenze
     wie HOME, mindestens ein Segment hinter dem `/` → Fund ab `~`, Quelle `pfad-home`, Konfidenz 0.80
     (wahrscheinlich). `~erika/akte.pdf` → ein Fund. Weiterhin kein Fund: `~`, `~/`, `~name` ohne `/`, `~30/40`.*
  3. *Rechner vor dem Pfad (neue Form `HOST_PFAD`, Quelle `pfad-host`): Steht direkt vor `:/` ein Lauf aus
     `[A-Za-z0-9._-]` mit mindestens einem ASCII-Buchstaben (höchstens die letzten 64 Zeichen, nicht vor das
     Laufende davor zurück), und folgt hinter `:/` mindestens ein Segment, ist der Fund der ganze Lauf plus `:` plus
     Pfad. Keine Startgrenze. Konfidenz 0.80 bei einem Segment, 0.90 ab zwei. `host.example:/export/home` → ein
     Fund ab `h`; `Pfad:/etc/hosts` → ein Fund ab `P` (bekannte Folge: das Wort vor dem Doppelpunkt wird
     mitersetzt). Der Laufwerksfund (`e:/export/home`) liegt darin; bei gleicher Abdeckung gilt Regel 7a aus
     T-12. Über den Standard-Scanner bleibt eine URL (`https://…`) genau ein URL-Fund, und
     `nutzer@host.example:/pfad` bleibt vollständig abgedeckt (zusammengelegt).*
  3a. *Präzisierungen zu `HOST_PFAD` (Umsetzung Durchlauf 2): (a) Kein Kopf, wenn hinter `:/` sofort ein weiteres
     `/` folgt (`://`) – URLs und AK-7 (g) bleiben unverändert; `file:///C:/x` bleibt bis T-16 beim Laufwerksfund
     ab `e:` („fil" sichtbar). (b) Ein Lauf aus genau einem Zeichen ist kein Host-Kopf (`C:/x` bleibt
     `pfad-laufwerk`, 0.95). (c) Der Host-Lauf ist reines ASCII; `Verhältnis:/x` ergibt den Teilfund `ltnis:/x`.
     (d) Bestehende Laufwerksfunde, deren Wort vor `:/` mehr als ein Zeichen hat, werden größer und tragen die
     Konfidenz der Host-Form (0.80 bei einem Segment, 0.90 ab zwei; Regel 7a (ii)). (e) IP als Rechner
     (`192.0.2.10:/export/home`) ist kein Host-Fund (kein Buchstabe); der Pfad dahinter bleibt ohne Fund –
     bekannte Grenze. (f) Bekannte Fehltreffer (0.80): `Hinweis:/n`, `Montag:/Dienstag`, `http:/x`.*
  3b. *(Nach security-Prüfung T-13, Durchlauf 2.) Startgrenze für UNIX und HOME sind ALLE 13 Anführungszeichen-Arten
     aus T-11 (gerade und typografische), nicht nur `"`, `'`, Backtick – ein unpaariges oder über das Zeilenende
     reichendes typografisches Zeichen ließ den Pfad sonst ganz im Klartext (U+201E + `/home/erika/a.pdf`).
     Bekannte Folge von 3a (d) und 7a (ii): Die Konfidenz eines Funds kann gegenüber dem Stand vor T-13 sinken,
     auch ohne dass seine Grenzen wachsen, wenn ein beteiligter späterer Fund jetzt ein Host-Fund ist
     (`C:\Akten'Pfad:/x y'` → 0.80 statt 0.95); ersetzt wird in jedem Fall. Linearität von `HOST_PFAD`: je
     `:/`-Stelle höchstens 64 gelesene Zeichen (nicht „jedes Zeichen höchstens zweimal").*
  4. *Bekannte Grenzen (kein Fund): Buchstabe direkt vor `/` (`siehe/etc/hosts`), Kurzoption (`-I/usr/include`),
     `host:~/dir`. Bekannte Fehltreffer (0.80): `und /oder/beides`, `rd /s/q`, `/impressum/kontakt`,
     `//kommentar/zeile`. Zwei-Segment-Pfade wie `/etc/hosts`, `/tmp/a` haben 0.80.*
  5. *Die Mindestsegment-Prüfung erfolgt nach der Kürzung mit der tatsächlichen Segmentzahl (reviewer-Auflage aus
     T-12); Zitat-Funde können mit Formen mit Startgrenze unsortiert entstehen und werden sortiert.*

### T-14: Pfad-Erkenner – relative Pfade
- **Dateien**: `PathForm.java` (`RELATIV`); Test `PathRecognizerRelativTest`.
- **AK**: AK-14.
- **Tests**: Zeile `RELATIV` positiv/negativ; Stufe nicht `SICHER`; `..\..\x`; Satzpunkt davor ohne Leerzeichen
  (`Ende../x`) kein Fund.
- *(Nachtrag 20.11.2026 zu T-14:*
  1. *`RELATIV`: Kopf `./`, `.\`, `../`, `..\` mit Startgrenze (wie UNIX/HOME, inkl. `,` `;` `|` und aller 13
     Anführungszeichen-Arten), mindestens ein Segment, Konfidenz konstant 0.80, Quelle `pfad-relativ`. Drei Punkte
     sind kein Kopf. Pfade ohne Kopf (`Ordner\Datei.txt`, `ordner/datei.txt`) bleiben laut Nutzerentscheidung
     ohne Fund.*
  2. *Einzelner führender Backslash (T-12 Punkt 8), eigene Form `EINZEL_BACKSLASH`: Kopf genau ein Backslash (davor
     kein Backslash, dahinter kein Trenner), MIT Startgrenze, mindestens zwei Segmente, Konfidenz 0.75, Quelle
     `pfad-relativ`. Mit Startgrenze, weil sonst jeder Pfad ohne Kopf (`Ordner\Datei\x.txt`) und jeder
     Registry-Schlüssel (`HKEY_LOCAL_MACHINE\SOFTWARE\x`) ab dem ersten Backslash zum Teilfund würde.*
  3. *Bekannte Fehltreffer (0.75, zu viel ersetzt, kein Leck): Regex- und Escape-Schreibweisen mit Startgrenze
     davor (`\d+\s`, `\d\w\s`, `\n\t`, `"\r\n"`), LaTeX (`\textbf{a}\cite{b}`, `\begin{document}\section{x}`).
     Bekannte Folge nach 7a (ii): Ein bestehender Fund, der von einem früher beginnenden Fund der neuen Formen
     verschluckt wird, trägt deren Konfidenz (0.75/0.80).*
  4. *Bekannte Grenzen (kein Fund): `cd ..\..`, `./.` (die Kürzung entfernt das letzte Segment – `..` am Ende
     besteht nur aus Kürzungszeichen und ist kein Segment; kein Name enthalten); scp-/SSH-Schreibweise mit
     relativem Pfad (`git@host.example:erika/repo.git`, `host.example:akte.pdf`) – sehr selten, bleibt bekannte
     Grenze; Domänenkonten `praxis\erika.mustermann` (Folge-Feature Zugangsdaten).*
  5. *Entscheidung der Hauptsitzung am 20.11.2026 (der Nutzer hat am selben Tag festgelegt, dass Detailregeln der
     Erkennung ohne Rückfrage in Richtung „möglichst viel schützen" entschieden werden), nach security-Hinweisen:*
     *(a) `:` ist zusätzliche Startgrenze NUR für `RELATIV` (`Pfad:.\erika\akte.pdf`, `PATH=.:./bin:../erika/bin`
     → Funde); nicht für UNIX, HOME, EINZEL_BACKSLASH.*
     *(b) Neue Form `BENUTZER_WURZEL` (Quelle `pfad-relativ`, Konfidenz 0.60, Stufe fraglich) für abgeschnittene
     Benutzerpfade ohne Laufwerk oder führenden Trenner, mit Startgrenze: Kopf `Users` oder `Benutzer` (Groß/klein
     egal) + Trenner (`\` oder `/`) und mindestens ein Segment; Kopf `home/` (nur kleingeschrieben, nur `/`) und
     mindestens zwei Segmente (`home/office` bleibt ohne Fund). Beispiele: `Users\erika.mustermann\Desktop\akte.pdf`,
     `Benutzer\erika\Desktop`, `home/erika/akte.pdf`. Das ergänzt die Nutzerentscheidung „Pfade ohne Kopf sind kein
     Fund" um diese drei Ordnernamen, weil dort fast immer ein Benutzername folgt.*
     *Umsetzung (Durchlauf 2): zwei Formen `BENUTZER_WURZEL` (Users/Benutzer, mindestens ein Segment) und
     `HOME_WURZEL` (`home/`, mindestens zwei Segmente); Namensvergleich nur ASCII. Ausnahme für das Gendern mit
     Schrägstrich: `Benutzer/in`, `Benutzer/innen` (Groß/klein egal, gefolgt von Segmentende oder höchstens drei
     Satz-/Klammerzeichen) sind kein Fund; `Benutzer\in`, `Benutzer/ina`, `Benutzer/in.pdf` sind Funde. Die
     Startgrenze ist jetzt je Form definiert (`PathForm.isStartBoundary`). Bekannte Fehltreffer (0.60):
     `Users/Groups`, `at home/work/x`; (0.80): `usw.:./x`, `12:./30`. Bekannte Folge: `Users\erika;C:\Tools\a`
     ist ein Fund mit 0.60 über den ganzen Lauf.*
     *Korrektur nach security-Prüfung Durchlauf 2 (häufige Fehltreffer in deutschen Texten: `Benutzer/Passwort`,
     `Benutzer/Kennwort`, `Benutzer/Gruppen`, `Benutzer/-innen`, `„Benutzer/innen“`): Der Kopf `Benutzer` mit
     SCHRÄGSTRICH verlangt mindestens ZWEI Segmente (`Benutzer/erika/Desktop` → Fund; `Benutzer/Passwort` → kein
     Fund); mit Backslash bleibt es bei einem Segment (`Benutzer\erika` → Fund), ebenso `Users` mit beiden
     Trennern. Die Gendern-Ausnahme vereinfacht sich: Ist das erste Segment hinter `Benutzer/` genau `in` oder
     `innen` (Groß/klein egal), gibt es keinen Fund, gleich was folgt. Zusätzlich ist `:` Startgrenze auch für die
     Wurzel-Formen (`Pfad:Users\erika\akte.pdf` → Fund ab `Users`). Bekannte Fehltreffer danach (0.60):
     `Users/Groups`, `Users/Admins`, `Benutzer\Gruppen`, `Benutzer/Rollen/Rechte`. Umsetzung (Durchlauf 3): Die
     Formen heißen `BENUTZER_WURZEL` (ein Segment: `Users` mit `\` oder `/`, `Benutzer` nur mit `\`) und
     `WURZEL_ZWEI_SEGMENTE` (zwei Segmente: `home/`, `Benutzer/`); die Gendern-Ausnahme greift, wenn hinter
     `in`/`innen` direkt ein Trenner folgt. Bekannte Folge: U+201E `Benutzer/innen` U+201C direkt gefolgt von
     `/a/b` ist ein Fund (das typografische Zeichen ist im Lauf kein Segmentende) – zu viel ersetzt, kein Leck.
     Bekannte Grenze der Zwei-Segment-Regel (security Durchlauf 3, als selten eingeschätzt, weil deutsches Windows
     `Benutzer\…` mit Backslash zeigt): `Benutzer/erika`, `Pfad:Benutzer/erika` und `Benutzer/erika mustermann/…`
     ohne Anführungszeichen ergeben keinen Fund; `Benutzer/IN/erika` ebenfalls nicht (Gendern-Ausnahme).*
     *(c) Bekannte Folge nach 7a (ii) in beiden Richtungen: Ein Fund der neuen Formen kann einen bestehenden Fund
     verschlucken ODER als später beginnender Fund verlängern; die Konfidenz des Ergebnisses ist die niedrigste
     der Beteiligten, auch bei gleichen Grenzen. Kein Fund geht verloren.*

### T-15: Pfad-Erkenner – Umgebungsvariablen
- **Dateien**: `PathForm.java` (`UMGEBUNG`); Test `PathRecognizerUmgebungTest`.
- **AK**: AK-15, AK-49 (`%`).
- **Tests**: Zeile `UMGEBUNG` positiv/negativ; Name mit 64 Zeichen ja, 65 nein; `%`×100 000, `%a`×100 000,
  `$`×100 000 < 2 s.
- *(Nachtrag 20.11.2026 zu T-15 – Entscheidungen der Hauptsitzung nach dem Grundsatz „möglichst viel schützen"
  (siehe T-14 Punkt 5); ergänzt bzw. ersetzt die Zeile `UMGEBUNG` (2.4.2), soweit sie widerspricht:*
  1. *Fund ist die Variable samt Pfad dahinter (mindestens ein Segment); Variable allein (`%APPDATA%`, `$HOME`),
     `%windir%\` ohne Segment und Dateiname direkt hinter der Variable ohne Trenner (`%~dp0tool.exe`) sind kein
     Fund. Konfidenz konstant 0.80, Quelle `pfad-umgebung`.*
  2. *Kopfvarianten (je bis einschließlich eines Trenners): `%NAME%` (Name bis 64 Zeichen, auch mit Klammern wie
     `ProgramFiles(x86)`), bis zu vier Gruppen direkt hintereinander (`%HOMEDRIVE%%HOMEPATH%\`); `$NAME`,
     `${NAME}`, `$(NAME)`; PowerShell `$env:NAME`, `${env:NAME}`; Batch `%~` + Modifikatoren + Ziffer
     (`%~dp0..\erika\a`).*
  3. *KEINE Startgrenze für `UMGEBUNG` (Abweichung von „Startgrenze gilt für alle Formen"): Der Kopf ist
     unverwechselbar; mit Startgrenze blieben `-I$HOME/inc`, `Pfad:%APPDATA%\x` ohne Fund. Prozent-, Währungs-
     und Formatangaben haben keinen Kopf und bleiben ohne Fund (`50%/Jahr`, `3%/4%`, `5$/Stück`, `$5/Monat`,
     `%d/%m/%Y`, `%s/%s`, `%20/`, `%1\%2`); URL-Kodierung erzeugt keinen Kopf.*
  4. *Bekannte Fehltreffer (0.80): Vorlagen und Formeln `${x}/${y}`, `$a/$b`, `$x/y$`, `$USD/MWh`, `echo $x\n`.
     Bekannte Grenzen (kein oder nur Teilfund): Ersatzsyntax (`${V:-x}/…`, `%V:~0,5%\…`), `!VAR!\x`, `%1\x`,
     Namen mit Punkt oder Minus oder über 64 Zeichen, mehr als vier `%…%`-Gruppen (die erste bleibt sichtbar).
     Bekannte Folge nach 7a (ii): Ein früher beginnender Variablen-Fund, der einen späteren Fund im selben Lauf
     verschluckt (`%A%\x;C:\a\b`), trägt 0.80.*
  5. *Es wird nichts aufgelöst: kein Zugriff auf Umgebungsvariablen oder Systemeigenschaften.*
  6. *(Nach reviewer-/security-Prüfung Durchlauf 1.) Zusätzliche Kopfvariante: verzögerte Batch-Expansion `!NAME!`
     + Trenner (`!USERPROFILE!\Desktop\x`), Name wie bei `%NAME%`, keine Kette. Bei den bekannten Grenzen aus
     Punkt 4 bleibt der GANZE Ausdruck samt Pfad im Klartext (nicht nur die Variable): `%1\x`,
     `%PATH:~0,5%\x`, `${HOME:-/tmp}/x`, Namen über 64 Zeichen oder mit Punkt/Minus; bei verschachtelter
     Ersatzsyntax (`${X:-$HOME/.config}/app`) entsteht ein Teilfund. Weitere bekannte Grenzen: Variable in
     eigenen Anführungszeichen mit nur einem Segment dahinter (`"$HOME"/erika` – ab zwei Segmenten greifen UNIX
     bzw. EINZEL_BACKSLASH, die Variable bleibt sichtbar); Befehlsersetzung mit Argument (`$(dirname $0)/x`) und
     mit Backticks; Dateiname direkt hinter der Variable (`%TEMP%erika.txt`). Bekannte Fehltreffer zusätzlich:
     Serienbrief-Platzhalter mit Schrägstrich (`%DATUM%/%ORT%`, `%VORNAME%/%NACHNAME%`) – ersetzt wird nur der
     Platzhalter-Ausdruck. Einschränkung der Obermengen-Garantie (reviewer, Durchlauf 2; gilt seit T-12 für
     mehrere Köpfe DERSELBEN Form in einem Lauf): Beginnt ein Fund durch einen früheren Kopf derselben Form
     weiter vorn, kann die Klammerzählung eine schließende Klammer am Fundende kürzen, die bei späterem Beginn im
     Fund läge (`!A!\)%a%\()`, `%a%\)%b%\()`). Betroffen ist nur dieses eine Klammerzeichen, nie ein
     Pfadbestandteil.)*

### T-16: Pfad-Erkenner – `file:` und Abgrenzung zu URL, IP, E-Mail
- **Dateien**: `PathForm.java` (`FILE_URI`); Test `PathRecognizerFileUriTest`, `TextScannerAbgrenzungTest`.
- **AK**: AK-5 (kein Pfad-Fund für `/pfad`, Scannerebene), AK-7 (d) mit echten Erkennern, AK-8 (Standard-Scanner
  deterministisch), AK-24.
- **Tests**: Zeile `FILE_URI`; AK-24 (a)–(e) über `TextScanner.standard()` mit genau einem Fund des erwarteten Typs,
  und zwar dem **unveränderten** Kandidaten des umschließenden Erkenners (Quelle `url` bzw. `pfad-…`, nicht
  `zusammengelegt`; dessen Konfidenz, auch wenn der enthaltene Kandidat eine höhere hätte); IP und E-Mail außerhalb des
  Pfads im selben Satz zusätzlich erkannt; AK-5-Text ergibt nur den URL-Fund; `Mail …, Server 192.0.2.10, Tool
  C:\Tools\a.exe` → drei Funde in Textreihenfolge; zweimal scannen → gleiche Liste; AK-7 (g)-Text zweimal gescannt →
  gleiche Liste.
- **Nachtrag 20.11.2026 (Umsetzung T-16; Detailregeln ohne Rückfrage in Richtung „möglichst viel schützen"
  entschieden, siehe T-14 Punkt 5):**
  1. *Kopf von `FILE_URI`: `file:` (ASCII, Groß/klein egal) + mindestens ein Trenner (`/` und `\` gemischt), ODER
     ohne Trenner direkt gefolgt vom Kopf von LAUFWERK, RELATIV, HOME oder UMGEBUNG (`file:C:\x`, `file:./a`,
     `file:~/x`, `file:%USERPROFILE%/x`). Fund über den ganzen Ausdruck ab `f`, Typ PFAD, Quelle `pfad-file-uri`,
     Konfidenz konstant 0.95. Query, Fragment und URL-Kodierung (`%20`) gehören zum Fund; Ende und Zitatregel wie
     bei den anderen Formen. Keine Startgrenze (`siehe:file:///…`).*
  2. *Mindestsegmente 1 (Abweichung von der Zeile FILE_URI, Kap. 2.4.2): Bis T-15 war `file:/x` ein Host-Fund;
     die Obermengen-Garantie verlangt, dass er bleibt. Folge: `file:///C:/`, `file:///etc`, `file://srv-beispiel`,
     `file:/text` sind Funde (0.95). Ohne Fund bleiben `file:`, `file:///`, `file://`, `file:foo`, `file:C:\`,
     `file:./`.*
  3. *HOST_PFAD schließt den Lauf genau `file` (Groß/klein egal) aus; damit hat ein Start nur eine Form
     (Voraussetzung von 7a (i)). Läufe, die `file` nur enthalten (`xfile:/a/b`, `profile:/x/y`), bleiben
     Host-Funde. Die Obermengen-Garantie für HOST_PFAD gilt seit T-16 nach Abdeckung (jede bis T-15 abgedeckte
     Position bleibt abgedeckt), nicht mehr nach Formzuordnung; Quelle und Konfidenz dieser Funde wechseln
     plangemäß auf `pfad-file-uri` 0.95.*
  4. *Standard-Scanner: Ein `file:`-Ausdruck ist genau ein PFAD-Fund; Zugangsdaten, E-Mail- und IP-Bestandteile
     (`file://nutzer:geheim@srv.example/x`, `file://192.0.2.10/freigabe/a`) gehen darin auf. URLs anderer Schemata
     bleiben unverändert genau ein URL-Fund, auch mit `file:` oder Pfad im Query. `xfile://…` und `profile://…`
     sind URL-Funde (T-5 Punkt 3). Ohne Leerraum aneinandergeklebte URL und `file:`-Ausdruck ergeben einen Fund
     mit dem Typ des umschließenden (früher beginnenden) Kandidaten.*
  5. *Bekannte Grenzen: unsichtbares Zeichen in `file` (`fi` bleibt sichtbar), Vollbreite-Doppelpunkt,
     `file :///…`, `file%3A///…` (jeweils nur der Pfad dahinter als Fund); `profile:///C:/x` im reinen
     Pfad-Erkenner: Fund ab `file:`, `pro` bleibt sichtbar (im Standard-Scanner URL-Fund über alles). Bekannte
     Folge nach 7a (ii): Verbindet ein Zitat-Fund ab `file:` zwei bisher getrennte Funde, gilt die niedrigste
     Konfidenz der Beteiligten. Es wird nichts geöffnet oder aufgelöst.*
  6. *(Nach reviewer-/security-Prüfung Durchlauf 1.) Weitere bekannte Grenze: `file:` ohne Trenner und ohne
     Pfadkopf dahinter (`file:erika/akte`, `file:srv.example/freigabe/akte`, `file:nutzer:geheim@srv.example/x`)
     ergibt keinen FILE_URI-Fund (wie bis T-15; andere Erkenner greifen ggf. auf Teile). Einschränkung der
     Obermengen-Garantie aus Punkt 3 (dieselbe Ursache wie T-15 Punkt 6, mehrere Köpfe derselben Form in einem
     Lauf): Beginnt im selben Lauf weiter vorn ein FILE_URI-Kopf, entsteht für ein späteres `file:/…` kein
     eigener Kandidat mehr; die Klammerzählung ab dem früheren Start kann dann eine schließende Klammer am
     Fundende samt einem davor gekürzten Satzzeichen aus dem Fund nehmen (`FILE:\\a)file:/b(c)` – bis T-15
     Abdeckung bis einschließlich `)`, seit T-16 ohne die letzte `)`). Betroffen sind nur `)`, `]`, `}` und
     gekürzte Satzzeichen, nie ein Pfadbestandteil.*

### T-17: Roundtrip-Korpus
- **Dateien**: Test `RoundtripKorpusTest` (Paket `de.skillshield.core`), Texte als Java-Strings mit `bs(…)`.
- **AK**: AK-7 (g) (im Korpus), AK-25, AK-26, AK-28, AK-37, AK-38, AK-39, AK-54 (KI-Simulation durch feste Umformung).
- **Tests**: Korpus mit allen Pfadformen (AK-9 bis AK-18, mit und ohne Anführungszeichen, doppelte Backslashes,
  `$`, `${HOME}`, `%APPDATA%`), E-Mail, IP, URL, `file:`, manuellen `PERSON`-Funden, dem Text aus AK-7 (g) und einer
  Variante mit `\`-Trennern (`bs(…)`); je Text Scan → alle Funde pseudonymisieren → keine Pfadbestandteile im
  Ausgabetext → Rückübersetzung → `assertEquals` mit Original, keine Meldung; Zeilenenden-Varianten,
  führender/abschließender Leerraum, Tabs, Umlaute, `e`+U+0301, Emoji vor/hinter/zwischen Funden, U+FEFF am Anfang,
  U+200B, RTL-Text; feste Umformung (Zeilen vertauschen, Präfix/Suffix) vor der Rückübersetzung.

### T-18: Fuzz-Test
- **Dateien**: Test `RoundtripFuzzTest`.
- **AK**: AK-45, AK-1, AK-7 (a), (e) mit echten Erkennern.
- **Tests**: 1 000 Texte aus `new Random(<Konstante>)`, Bausteine: synthetische Funde inkl. Pfade aller Formen mit
  Backslashes, Leerzeichen, Anführungszeichen (auch ungeschlossen), URL mit angehängtem `'…'`-Pfad mit Leerzeichen
  (erzeugt Teilüberlappungen), Sonderzeichen, Unicode, Zeilenenden, `[`, `]`, `_`, `[[X_1]]`, `[[TEXT_1]]`,
  `[[PERSON_1`; zufällige Teilmenge der Scan-Funde plus manuelle Funde (surrogatsicher, überlappungsfrei); Prüfung
  Teilstring-Invariante, Roundtrip gleich, keine Meldung. **Schutzgarantie**: je Text die Kandidaten jedes
  Standard-Erkenners einzeln (`new EmailRecognizer().detect(text)` usw.) gegen `TextScanner.standard().scan(text)`:
  abgedeckte Positionen des Scans = Vereinigung der Kandidatenpositionen. Teil 2: 1 000 Zufallseingaben an `restore`
  mit fester Zuordnung → keine Ausnahme. Fehlermeldung nennt Startwert und Iteration, keine Texte.
- **Nachtrag 21.11.2026 (security, Schwere niedrig, Folgepunkt):** *Die Erkennerliste der Schutzgarantie-Prüfung
  steht fest im Test (E-Mail, IPv4, URL, Pfad) und entspricht heute `TextScanner.standard()`. Kommt ein
  Standard-Erkenner hinzu (z. B. IK/LANR), muss die Liste in `RoundtripFuzzTest` ergänzt werden, sonst prüft die
  Schutzgarantie dessen Kandidaten nicht. Folgepunkt für das nächste Erkenner-Feature: Liste paketprivat verfügbar
  machen oder einen Abgleichtest ergänzen.*

### T-19: Grenzlast
- **Dateien**: Test `GrenzlastTest`.
- **AK**: AK-48, AK-49 (alle Eingaben durch alle drei Schritte, inkl. langer Ketten).
- **Tests**: ≥ 1 000 000 Zeichen mit ≥ 10 000 Funden, davon mindestens die Hälfte Pfade aller Formen: Scan,
  Pseudonymisierung, Rückübersetzung je < 10 s, Roundtrip gleich. Jede Eingabe aus AK-49 sowie 100 000 × `[[` und
  × `[[A_` durch alle drei Schritte je < 2 s. **Lange Kette mit echten Erkennern**: `bs("https://e.invalid/''C:¦a ")`
  × 40 000 (jede URL enthält das schließende `'` des vorigen und das öffnende `'` des nächsten Pfad-Kandidaten in
  Anführungszeichen; ergibt eine Kette über fast den ganzen Text) durch alle drei Schritte je < 2 s, Roundtrip gleich,
  Schutzgarantie wie in T-18 (keine feste Fundzahl prüfen). Standard-Heap des Surefire-Laufs.
- **Nachtrag 21.11.2026 (security, Schwere niedrig):** *Der Test enthält unter der 2-s-Grenze keinen Fall mit sehr
  vielen VERSCHIEDENEN Fundwerten (große Zuordnung); überlineares Verhalten, das mit der Größe der Zuordnung wächst,
  fiele erst spät auf. Das heutige Verhalten ist linear (eigene Messung von security: 100 000 bis 175 000
  verschiedene Werte, alle Schritte unter 0,7 s). Die Lücke wird in T-20 geschlossen (T-20 Nachtrag Punkt 5).*

### T-20: Projektregeln (Netz, Dateisystem, Klartext, Testdaten)
- **Dateien**: Tests `ArchitekturRegelnTest`, `KlartextFreiheitTest`, `TestdatenSynthetischTest` (Paket
  `de.skillshield.core`); `README.md` (Satz „derzeit 25 Tests, 5 je Modul“ zählerunabhängig formulieren).
- **AK**: AK-47, AK-50, AK-51, AK-52, AK-53, AK-54.
- **Tests**:
  - `ArchitekturRegelnTest`: liest `src/main/java/**/*.java` (Surefire-Arbeitsverzeichnis = Modulverzeichnis); verbietet
    `java.net`, `javax.net`, `java.nio`, `java.io`, `InetAddress`, `Socket`, `URLConnection`, `HttpClient`,
    `FileSystem`, `Files`, `Paths`, `ProcessBuilder`, `Runtime`, `System.out`, `System.err`, `printStackTrace`,
    `java.util.logging`, `System.Logger` (Wortgrenzen); alle Dateien unter `de/skillshield/core`. Diese Wörter dürfen
    auch in Kommentaren des Produktivcodes nicht stehen. (Hinweis: `RuntimeException` ist von der Wortgrenze bei
    `Runtime` nicht betroffen; der Test muss das berücksichtigen.)
  - `KlartextFreiheitTest`: Marker-Pfad `bs("C:¦Marker¦mustermann¦MarkerTool.exe")` und Marker-E-Mail durch jeden
    Fehlerpfad aus AK-32, AK-41, AK-42, AK-47, zusätzlich: `ConflictResolver.resolve` mit unpassendem Kandidaten,
    `TextScanner` mit einem Erkenner, der eine `RuntimeException` mit Marker in Meldung und `cause` wirft, und
    `toString()` eines zusammengelegten Fundes; prüft Meldung, `cause`-Kette, `getSuppressed()`, alle `toString()` und
    umgeleitete `System.out`/`System.err`.
  - `TestdatenSynthetischTest`: liest `src/test/java/**/*.java`; jede E-Mail-ähnliche Zeichenfolge endet auf
    `example.invalid|example.org|example.net`; jede gültige IPv4 liegt in den RFC-5737-Bereichen; Pfade per Durchsicht.
- **Nachtrag 21.11.2026 (vor der Umsetzung, nach Sichtung des Bestands; Detailregeln ohne Rückfrage entschieden,
  siehe T-14 Punkt 5; geht dem Wortlaut oben vor):**
  1. *`TestdatenSynthetischTest`, E-Mail: Eine E-Mail-ähnliche Zeichenfolge ist ein `@`, direkt gefolgt von einem
     Domain-Lauf aus Labels (Buchstaben, Ziffern, `-`, `_`) mit mindestens einem Punkt. Abschließende Punkte und
     Bindestriche des Laufs zählen nicht; Groß/klein ist egal. Zulässig ist die Zeichenfolge, wenn (a) die Domain
     auf einen reservierten Namen endet – letztes Label `example`, `invalid`, `test` oder `localhost` (RFC 2606,
     RFC 6761) oder Endung `example.com`, `example.org`, `example.net` –, oder (b) das letzte Label kürzer als
     zwei Zeichen ist (Kunstproben der Erkenner-Tests wie `a@b.c`; keine echte Endung ist einbuchstabig), oder
     (c) sie in einer geschlossenen Ausnahmeliste im Test steht: exakte Zeichenfolge, je mit Begründung, nur für
     Grenzfallproben der Erkennerregeln, die erkennbar künstlich sind (z. B. `a@b.invalidC`,
     `…@example.invalid.erika`, `…@praxis.example.max`, überlange Labels). Jede andere Zeichenfolge macht den Test
     rot. Begründung der Erweiterung gegenüber `example.invalid|example.org|example.net`: Der abgenommene
     Testbestand nutzt reservierte `*.example`-/`*.invalid`-Domains und Kunstproben; alle sind garantiert nicht
     vergeben und als synthetisch erkennbar (Zweck von AK-53). Ein Umschreiben von rund 60 abgenommenen
     Testliteralen (darunter Laufzeitproben mit exakten Fundzahlen) wäre ein Risiko ohne Nutzen.*
  2. *`TestdatenSynthetischTest`, IPv4: Geprüft wird jede alleinstehende gültige IPv4 – eine maximale Kette aus
     Ziffern und Punkten mit genau vier Blöcken, jeder numerisch 0–255 (führende Nullen erlaubt). Sie muss
     numerisch in 192.0.2.0/24, 198.51.100.0/24 oder 203.0.113.0/24 liegen. Ketten mit mehr oder weniger Blöcken
     oder mit einem Block über 255 sind keine alleinstehende gültige Adresse (Kettenproben des IPv4-Erkenners,
     Versionsnummern) und werden nicht geprüft. Der Bestand enthält genau eine Verletzung:
     `PathRecognizerHostTest` verwendet `1.2.3.4:/x`; das Literal wird in T-20 auf `192.0.2.4:/x` umgestellt
     (Erwartung des Tests unverändert). Deckt der neue Test weitere Verletzungen auf, werden sie gleichartig auf
     einen Dokumentationsbereich umgestellt und im Bericht einzeln genannt.*
  3. *`ArchitekturRegelnTest`: Im Bestand steht genau ein verbotenes Wort, im Javadoc von `PathForm` (Form
     RELATIV, Beispiel `Files\Mein` als Rest von `Program Files`). Der Kommentar wird in T-20 umformuliert
     (z. B. `Dateien\Mein`). Das ist die einzige zulässige Änderung an Produktivcode in T-20 und betrifft nur
     Kommentartext; der ausführbare Code bleibt unverändert (security belegt das per Bytecode-Vergleich).*
  4. *Die drei Tests müssen selbst Dateien lesen (`src/main/java`, `src/test/java`). Das ist Testcode und von den
     Verboten für den Produktivcode nicht betroffen. `TestdatenSynthetischTest` nimmt die eigene Datei und die
     Ausnahmeliste nicht von der Prüfung aus, außer für die Zeilen der Ausnahmeliste selbst.*
  5. *Zusatz aus T-19 (security, niedrig): `GrenzlastTest` erhält in T-20 zwei weitere pathologische Fälle mit
     mindestens 100 000 VERSCHIEDENEN Fundwerten – 100 000 verschiedene Pfade (`bs("C:¦d¦") + i + " "`) und
     100 000 verschiedene E-Mail-Adressen (`"u" + i + "@e.invalid "`) –, je durch alle drei Schritte mit je < 2 s,
     Roundtrip gleich; im Test wird geprüft, dass die Zuordnung mindestens 100 000 Einträge hat. Das ist neben
     Punkt 2 die einzige zulässige Änderung an bestehenden Tests in T-20.*
  6. *(Nach der Prüfung; security, drei Punkte der Schwere niedrig, alle als Folgepunkte, heute ohne Risiko.)
     (a) `ArchitekturRegelnTest` vergleicht Quelltext mit der Wortliste des Plans; nicht erkannt würden
     Unicode-Escapes im Quelltext, per Reflection zusammengesetzte Klassennamen und Zugänge außerhalb der Liste
     (z. B. `java.util.prefs`, JNDI, `java.sql`, `java.rmi`, `java.awt.Desktop`, `ServiceLoader`,
     Umgebungsvariablen). Stand heute laut `jdeps`: Der Kern nutzt nur `java.lang`, `java.lang.invoke`,
     `java.lang.runtime`, `java.util`, `java.util.function`, `java.util.regex`. Folgepunkt: Positivliste auf
     Bytecode-Ebene ergänzen. (b) `TestdatenSynthetischTest` liest nur den Testbaum von `skillshield-core` und nur
     zusammenhängende Literale; sobald die anderen Module echte Tests bekommen, die Prüfung auf alle Testbäume
     ausdehnen. (c) `TextScanner` kapselt `RuntimeException` eines Erkenners, nicht aber `Error` (bewusste
     Entscheidung, TE-20); ein `Error` mit Klartext in der Meldung käme unverändert beim Aufrufer an. Die
     Standard-Erkenner werfen keine solchen Fehler. Vorgabe für die Oberfläche: Meldungen von `Throwable` nie
     ungefiltert anzeigen oder protokollieren. Ausnahmeliste des `TestdatenSynthetischTest` (neun Einträge) ist
     von reviewer, security und qa als erkennbar künstlich bestätigt; die Durchsicht der Server-, Benutzer- und
     Programmnamen in Pfad-Literalen (AK-53) hat security vorgenommen: alle erfunden oder generisch.*

### Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-1, T-2, T-3, T-4, T-5, T-10, T-18 | AK-19 | T-10 | AK-37 | T-17 |
| AK-2 | T-3 | AK-20 | T-10, T-11 | AK-38 | T-8, T-17 |
| AK-3 | T-3, T-5 | AK-21 | T-10 | AK-39 | T-17 |
| AK-4 | T-4 | AK-22 | T-10, T-12 | AK-40 | T-8 |
| AK-5 | T-5, T-16 | AK-23 | T-2 (Resolver), T-10, T-11 | AK-41 | T-8 |
| AK-6 | T-1, T-2 (zusammengelegt, Enthaltensein), T-3, T-4, T-5, T-10 (je Pfadform T-10–T-16) | AK-24 | T-2, T-16 | AK-42 | T-8 |
| AK-7 | T-2 (a)–(g), T-11 (e), (g), T-16 (d), T-17 (g), T-18 (a), (e) | AK-25 | T-6, T-17 | AK-43 | T-8 |
| AK-8 | T-2, T-3, T-16 | AK-26 | T-6, T-17 | AK-44 | T-8, T-9 |
| AK-9 | T-10 | AK-27 | T-6 | AK-45 | T-8, T-18 |
| AK-10 | T-10 | AK-28 | T-6, T-17 | AK-46 | T-2, T-6, T-8 |
| AK-11 | T-12 | AK-29 | T-6 | AK-47 | T-2, T-6, T-7, T-8, T-20 |
| AK-12 | T-13 | AK-30 | T-6, T-9 | AK-48 | T-19 |
| AK-13 | T-13 | AK-31 | T-6 | AK-49 | T-2 (Ketten), T-3, T-4, T-5, T-10, T-11, T-12, T-13, T-15, T-19 |
| AK-14 | T-14 | AK-32 | T-1, T-7 | AK-50 | T-1, T-2, T-6, T-7, T-8, T-20 |
| AK-15 | T-15 | AK-33 | T-6 | AK-51 | T-10 (Entwurf), T-20 |
| AK-16 | T-11 | AK-34 | T-6 | AK-52 | jeder Task (Build), T-20 |
| AK-17 | T-10 (a), T-12 (b), T-11 (c) | AK-35 | T-6 | AK-53 | T-20 (Durchsicht durch reviewer) |
| AK-18 | T-10, T-13 | AK-36 | T-9 | AK-54 | T-2, T-17, T-20 |

---

## 7. Hinweise für Prüfer

- **qa**: AK-32 „negative Position“ und „Start nicht kleiner als Ende“ werden beim Erzeugen des `Finding` abgelehnt
  (TE-10). AK-52 zusätzlich per Befehl: `mvn -q dependency:tree -pl skillshield-core` (nur JUnit im Test-Scope),
  `mvn clean verify` grün inkl. Enforcer; OWASP: `mvn clean compile org.owasp:dependency-check-maven:check`
  (keine neue Abhängigkeit, Ergebnis unverändert).
- **reviewer/security**: Testdaten mit Backslashes nach 2.10; AK-53 für Pfade per Durchsicht (keine echten Server-,
  Benutzer-, Produkt- oder KV-internen Namen). Die in T-10/T-13 festgehaltenen bekannten Grenzen (D-5, `und /oder/…`,
  `/log=…`) sind gewollt.
- **reviewer (T-2)**: Die Regeltests sind so gebaut, dass das nachrangige Kriterium das Gegenteil ergäbe (E-1, G-1,
  T-a bis T-d, K-1 bis K-3). Prüfprobe: Kriterienreihenfolge in `STAERKE` vertauschen oder Enthaltensein auslassen →
  mindestens ein Test rot. Der Eigenschaftstest vergleicht gegen eine unabhängige O(n²)-Referenz.

---

## 8. Entscheidungen für die Freigabe

Der Plan ist mit den Empfehlungen umsetzbar.

### 8.1 Nutzer muss entscheiden

| Nr. | Frage | Empfehlung | Begründung | Wirkung bei anderer Wahl |
|-----|-------|------------|------------|--------------------------|
| N-1 | **Nie geschlossenes Anführungszeichen** vor einem Pfad (fachlich ungeregelt) | **Regel ohne Anführungszeichen** (Ende am ersten Leerzeichen), TE-12 | Passt zu D-5; ein vergessenes oder als Apostroph gemeintes Zeichen erzeugt keinen Riesenfund bis Zeilenende. | „Bis Zeilenende“: mehr Schutz bei abgeschnittenen Zitaten, aber Fehlfunde über ganze Zeilen; „kein Fund“: schlechter als heute. Eine Zeile in T-11. |
| N-2 | **OF-7: `//srv-beispiel/freigabe` und `\\?\C:\…`** | **Erkennen** (Abweichung vom Vorschlag „kein Fund“). `\\srv-beispiel` allein und `C:\` bleiben kein Fund. | Beide sind echte Pfade mit Server- bzw. Ordnernamen. Bei „kein Fund“ bleibt der ganze Pfad Klartext, denn `C:` hinter `\\?\` und `srv` hinter `//` haben keine Startgrenze. Die befürchteten Verwechslungen treten nicht auf: `// Kommentar` hat kein Segment, `https://…` keine Startgrenze. | Vorschlag des product-owner: je eine Zusatzbedingung im Kopf von `UNIX` (kein zweiter `/`) bzw. `UNC` (Server ≠ `?`/`.`); Server- und Pfadnamen bleiben dann sichtbar. |
| N-3 | **OF-10: Stufen der Pfadformen** (vom PO zur Bestätigung vorgesehen) | Laufwerk, UNC, `file:` **sicher** (0.95); Unix ≥ 3 Segmente und Home **sicher** (0.90); Unix mit 2 Segmenten, relativ, Umgebungsvariable **wahrscheinlich** (0.80); Schwellen sicher ≥ 0.90, wahrscheinlich ≥ 0.70. Kein Review-Flag-Feld. | Entspricht dem PO-Vorschlag; „Home“ ergänzt (eindeutiger Kopf). Funde werden nie verworfen, die Stufe steuert nur die spätere Prüfanzeige. | Nur Konstanten in `PathForm`; teuer wird es erst, wenn die Oberfläche darauf aufbaut. |

Zu D-8/OF-16 ist **keine weitere Nutzerentscheidung nötig**: TE-19 hält AK-6 (Stufe nicht sicherer als das schwächste
beteiligte Glied) ohne Abweichung ein. Zur Kenntnis (keine Entscheidung): Abweichend vom PO-Vorschlag OF-16 (b) trägt
der zusammengelegte Fund als Quelle `zusammengelegt` statt der Quelle des Typgebers und nicht die Typen der Beteiligten
(siehe TE-19 (d), P-6).

### 8.2 Technisch festgelegt (änderbar)

| Nr. | Thema (OF) | Festlegung |
|-----|-----------|------------|
| TE-1 | `Finding` (OF-15) | Record `type, originalValue, start, end, confidence, source`; ohne `replacement` und ohne `note`; `end` exklusiv, UTF-16-Indizes; Validierung im Konstruktor; `toString()` ohne `originalValue`. `RecognitionContext`, `PseudonymMatch`, `PseudonymizedDocument` entfallen. |
| TE-2 | Konfidenz (OF-10) | E-Mail 0.95, URL 0.95, IP 0.85 (Versionsnummern möglich), Pfade nach N-3. Quellen: `email`, `ipv4`, `url`, `pfad-laufwerk`, `pfad-unc`, `pfad-unix`, `pfad-home`, `pfad-relativ`, `pfad-umgebung`, `pfad-file-uri`, `manuell`, `zusammengelegt` (nur Resolver). |
| TE-3 | Konfliktauflösung (OF-12, D-8) | Kap. 2.5: Kandidaten nach Start sortieren und transitiv überlappende zu Gruppen verschmelzen (ein Durchlauf, O(n log n)). Enthaltene Kandidaten gehen im umschließenden auf; bleibt nur ein maximaler Kandidat, ist er das Ergebnis, unverändert und unabhängig von der Konfidenz (AK-24). Bilden mehrere maximale Kandidaten eine Kette, entsteht ein Fund über die Vereinigung (AK-7). Angrenzende Kandidaten werden nicht vereinigt. Pseudonymisierung legt nichts zusammen (AK-32). |
| TE-4 | Grenzen (OF-13) | AK-48: 1 000 000 Zeichen, ≥ 10 000 Funde, je Schritt < 10 s (PO-Vorschlag). AK-49: je Eingabe und Schritt < 2 s (strenger als 10 s, damit quadratisches Verhalten sicher auffällt). Keine Obergrenze für Textlänge oder Pfadlänge. |
| TE-5 | Abbruch, Threads, Tresor (OF-14 a–d) | Kein Abbruch (linear). Alle Klassen zustandslos/unveränderlich, faktisch threadsicher, nicht zugesichert. Keine `Vault`-Schnittstelle; `PlaceholderMapping` ist ein eigener Typ, den das Tresor-Feature ablöst. Fremde Zuordnung nicht verhindert. |
| TE-6 | Quellenwidersprüche (OF-15) | Basispaket `de.skillshield`; Tresor in `skillshield-vault`; Rückübersetzung nach Platzhalter, nicht nach Position; „keine KI im Produkt“ und „keine KI-Autonomie“ beide eingehalten; Plan.md Kap. 7 („strukturerhaltende Pfade“) sollte nach D-4 nachgezogen werden (keine Codewirkung). |
| TE-7 | Laufzeitsicherheit | Regex-Regeln 2.3; Pfade ohne Regex, linear nach 2.4.4; Konfliktauflösung ohne Rekursion, O(n log n) (2.5). |
| TE-8 | Nachweis „kein Netz/Dateisystem“ (AK-51) | Quelltext-Prüftest T-20. Verworfen: SecurityManager (abgekündigt), eigener `InetAddressResolverProvider` (fängt nur DNS). |
| TE-9 | `Pseudonymizer`/`Reidentifier` | Finale Klassen statt Interface + Implementierung; Zuordnung als `PlaceholderMapping`. |
| TE-10 | Prüfort AK-32 | Negative Position und `start >= end` lehnt `Finding` ab; textbezogene Fälle `Pseudonymizer`. |
| TE-11 | Typnamen (OF-11) | `PFAD`, `EMAIL`, `IP`, `URL` (Erkenner), `TEXT` (Literalschutz); manuelle Funde mit beliebigem Namen im Format `[A-Z][A-Z0-9]{0,31}`; Kleinbuchstaben, Unterstrich, Leerzeichen → `IllegalArgumentException` ohne Echo. Nummerierung global (A-4). |
| TE-12 | Ungeschlossenes Anführungszeichen | Rückfall auf die Regel ohne Anführungszeichen (N-1). Schließendes Zeichen nur auf derselben Zeile, vor Tab und vor `<`, `>`, `\|` (bzw. `"` bei `'`/`` ` ``). |
| TE-13 | Pfadende und Pfadzeichen (OF-5) | (a) Satzzeichen `. , ; : ! ?` und `'` am Ende gekürzt; umschließende Zeichen nie am Rand eines Fundes (im Inneren eines zusammengelegten Fundes möglich, A-12). (b) `)`, `]`, `}` am Ende nur, wenn im Fund geöffnet; gleiche Regel für URL (`)`, `]`). (c) `*` gehört zum Pfad, abschließendes `?` nicht. (d) `<`, `>`, `\|`, `"` und Backtick beenden den Pfad. (e) keine weiteren URL-Schemata. |
| TE-14 | Unix (OF-4) | Absolut ≥ 2 Segmente, Home ≥ 1 Segment nach `~/`, `~benutzer/x` kein Fund, Beginn nur an Startgrenze; `/` und `\` sind für alle Formen Trenner. |
| TE-15 | Umgebungsvariablen (OF-6) | Wie PO-Vorschlag; Namen höchstens 64 Zeichen; PowerShell `$env:NAME\…` nicht abgedeckt. |
| TE-16 | `file:` (OF-8) | PFAD-Fund über den ganzen Ausdruck inkl. `file:///` (PO-Vorschlag). |
| TE-17 | Doppelte Backslashes (OF-9) | Wie PO-Vorschlag; Trenner-Läufe zählen als ein Trenner; UNC-Kopf mit genau 2 oder 4 Backslashes. |
| TE-18 | Ein Pfad-Erkenner | Ein `PathRecognizer` mit Formen als Enum-Konstanten (Begründung 2.4); kein `java.nio.file`, kein Dateisystem. |
| TE-19 | Zusammengelegter Fund (OF-16) | (a) **Typ**: vom stärksten maximalen Glied nach `STAERKE` = Konfidenz ab, Länge ab, Start auf, dann Typrang `URL` < `PFAD` < `EMAIL` < `IP` < übrige, Typname, Quelle (PO-Vorschlag, ergänzt um Typrang für Totalität). Gilt ebenso für gleiche Bereiche; enthaltene Kandidaten liefern nie den Typ. (b) **Konfidenz** = kleinste Konfidenz der maximalen Glieder, damit Stufe = Stufe des schwächsten Glieds (AK-6). In einem Glied enthaltene Kandidaten zählen nicht (Begründung 2.5: keine zusätzlich abgedeckte Position, Stetigkeit, Gleichlauf mit AK-24). **Quelle** `zusammengelegt`. (c) **Beispiel AK-7 (g)**: Fund [5,83) = `https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf'` ohne das schließende `'`; das öffnende `'` liegt innen. Typ PFAD (beide 0.95, PFAD mit 49 Zeichen länger als URL mit 36), Konfidenz 0.95, Stufe sicher. Pseudonymisiert: `Link [[PFAD_1]]' ende`. Das schließende `'` gehört zu keinem Kandidaten (URL endet am Leerzeichen, der Pfad in Anführungszeichen endet vor dem `'`); die Zusammenlegung nimmt keinen Text außerhalb der Kandidaten auf (AK-7 (e)). (d) **Kennzeichnung**: über die Quelle `Finding.SOURCE_MERGED = "zusammengelegt"` (passt in das Format `[a-z][a-z0-9-]{0,31}`). Kein neues Feld, kein neuer Konstruktorparameter, `toString()` unverändert (zeigt `source=zusammengelegt`). Die Typen der Beteiligten werden nicht mitgeführt (P-6). |
| TE-20 | Ausnahmen von Erkennern | `TextScanner` kapselt `RuntimeException` aus `detect` in `IllegalStateException` mit Index und Ausnahmeklasse, ohne Meldung des Originals, ohne `cause`, ohne Suppressed (2.9). Umsetzung in T-2 (Scanner ist dort noch offen), Marker-Test zusätzlich in T-20. Ein Marker-Test allein reicht nicht: er deckt nur die vorhandenen Erkenner und nur die getesteten Eingaben ab, künftige Erkenner (Namen, KV) nicht. Preis: der Stacktrace der Ursache fehlt; Erkenner sind deterministisch und per Test reproduzierbar. |

---

## 9. Sonstige offene Punkte

- **P-1 Zeitgrenzen**: auf dem Entwicklungsrechner gemessen; bei Fehlschlag zuerst die Messung prüfen, nicht die
  Grenze stillschweigend erhöhen.
- **P-2 Pseudonymisierter Text ist nicht zwingend klartextfrei** (D-5-Reste, abgelehnte Funde, Programmnamen ohne
  Pfad). Die spätere Oberfläche muss das deutlich machen; bis zum Leak-Check (v0.2) entscheidet der Mensch allein.
- **P-3 Folgefeature IK/LANR**: Der Entwurf der Vorfassung (Prüfziffernklassen, Erkenner, Testvektoren,
  Quellenvermerk) ist in `docs/features/text-pseudonymisierung-kern/anhang-ik-lanr-entwurf.md` gesichert und wird dort
  wiederverwendet (Nutzerentscheidung: Sekundärquellen-Stand mit Vermerk, ungültige Prüfziffer → „fraglich“). Es
  braucht dann ein Feld `note` in `Finding` und die damaligen offenen Punkte (Quellenangaben, offizielle
  Spezifikationen) erneut. Der Anhang kann nach Übernahme gelöscht werden.
- **P-4 Teilüberlappung**: durch D-8 erledigt (Zusammenlegung, TE-3/TE-19). Mit künftigen Erkennern (Namen) kann die
  Zusammenlegung häufiger und größer werden; dann Größe zusammengelegter Funde im Korpus beobachten.
- **P-5 Plan.md Kap. 7** nach D-4 anpassen („strukturerhaltende Pfade“ streichen bzw. auf „ein Platzhalter je Pfad“
  ändern); Aufgabe außerhalb dieses Features.
- **P-6 Beteiligte eines zusammengelegten Fundes**: Will die Review-Oberfläche (Folgefeature) „zusammengelegt aus URL
  und PFAD“ anzeigen, braucht `Finding` dann ein zusätzliches, klartextfreies Feld (z. B. Liste der Typen). Das ist
  eine mechanische Änderung der Konstruktoraufrufe in `skillshield-core`; heute gibt es keinen Nutzer dafür.
- **P-7 Lesart AK-7 (b)**: „Bereich = Vereinigung beider“ gilt für zwei Kandidaten ohne weitere Beteiligte. Umfasst
  ein dritter Kandidat einen der beiden (D = [0,12) enthält A = [2,8), B = [6,15) überlappt A und D), ist das Ergebnis
  die Vereinigung der Gruppe ([0,15)), also größer als A ∪ B. Das folgt aus (a), (c), (d); zur Kenntnis für den
  product-owner, keine Entscheidung.

---

## 10. Abweichungen von Plan.md / Blueprint

1. **Pfade nicht strukturerhaltend** (Plan.md Kap. 2 und 7): ein Platzhalter `[[PFAD_n]]` je Pfad. Nutzerentscheidung
   D-4.
2. **Keine eigenen Erkenner für Benutzer- und Servernamen/FQDN** (Plan.md Kap. 6, Blueprint Kap. 6): nur als Teil von
   Pfad, URL oder E-Mail geschützt. Nutzerentscheidung D-2.
3. **KV-Erkenner (IK, LANR) nicht in diesem Feature** (Plan.md Kap. 6/14): Folgefeature, D-3.
4. **`Pseudonymizer` und `Reidentifier` sind Klassen, keine Interfaces** (Plan.md Kap. 5); `Reidentifier` arbeitet mit
   `PlaceholderMapping` statt `Vault` (TE-9). Je eine Implementierung; Tresor nicht Teil des Features.
5. **`Vault` entsteht nicht** (Plan.md Kap. 5): kommt mit dem Tresor-Feature in `skillshield-vault`.
6. **`Finding` weicht von der Blueprint-Skizze ab** (ohne `replacement`, `start`/`end`, eigenes `toString()`; TE-1):
   AK-50 und Plan.md Kap. 11.
7. **`Recognizer.detect(String)` ohne `RecognitionContext`**: Profile sind ausgegrenzt.
8. **MVP-Umfang (Plan.md Kap. 14)**: Namen nur über manuelle Funde (A-3), „Kennnummern“ im Folgefeature, Leak-Check in
   v0.2 (A-8).
9. **Paketnamen** `pseudonymize`/`reidentify`, Basispaket `de.skillshield` statt Blueprint-Namen: folgt vorhandener
   Struktur und CLAUDE.md.
10. **Abweichung vom PO-Vorschlag OF-7** (nicht von Plan.md): `//server/freigabe` und `\\?\…` werden erkannt, falls
    N-2 so entschieden wird.
11. **Konfliktauflösung (D-8)**: keine Abweichung von Plan.md; Plan.md/Blueprint Kap. 6 nennen nur „Konfliktauflösung“,
    D-8 konkretisiert sie. Abweichung nur vom PO-Vorschlag OF-16 (b) bei der Quelle (TE-19 (d)).
