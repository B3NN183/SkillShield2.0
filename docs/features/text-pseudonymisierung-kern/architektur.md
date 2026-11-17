# Architektur: Text-Pseudonymisierung (Fachkern)

Feature-Kurzname: `text-pseudonymisierung-kern`
Grundlage: `docs/features/text-pseudonymisierung-kern/anforderung.md` (Neufassung: AK-1 bis AK-54, D-1 bis D-7,
A-1 bis A-12, OF-4 bis OF-15), `Plan.md` Kap. 2, 5–8, 11, 13, 14, `docs/BLUEPRINT_SkillShield_Java.md`, `CLAUDE.md`,
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

---

## 1. Lösungsansatz

Der Fachkern besteht aus drei zustandslosen Schritten auf einem `String` im Modul `skillshield-core`:
**Scan** (`TextScanner` ruft die Erkenner auf, `ConflictResolver` macht die Kandidaten überlappungsfrei),
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
Überlappung gewinnt der umschließende Fund unabhängig von der Konfidenz (OF-12).

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
| `model` | `Finding` | record | Fund; `toString()` ohne Klartext |
| `recognizer` | `Recognizer` | interface | ein Erkenner |
| `recognizer` | `TextScanner` | final class | Scan = alle Erkenner + Konfliktauflösung |
| `recognizer.general` | `EmailRecognizer`, `Ipv4Recognizer`, `UrlRecognizer` | final class | Regex-Erkenner |
| `recognizer.general` | `PathRecognizer` | final class | Pfad-Erkenner (Durchlauf, Läufe, Anführungszeichen, Ende) |
| `recognizer.general` | `PathForm` | enum (package-private) | je Pfadform: Kopf, Mindestsegmente, Konfidenz, Quelle |
| `recognizer.general` | `PathChars` | final class (package-private) | Zeichenklassen (Trenner, Endzeichen, Startgrenze, Kürzung) |
| `conflict` | `ConflictResolver` | final class | überlappungsfreie Auswahl |
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
    public Finding { /* type, originalValue, source != null; start >= 0; end > start;
                        originalValue.length() == end - start; 0.0 <= confidence <= 1.0, kein NaN
                        -> sonst IllegalArgumentException / NullPointerException, Meldung ohne Werte */ }
    public static Finding manual(FindingType type, String text, int start, int end); // 1.0, "manuell"
    public ConfidenceLevel level();
    @Override public String toString();
    // "Finding[type=PFAD, start=6, end=31, confidence=0.95, level=SICHER, source=pfad-laufwerk]" – ohne originalValue
}

// ---------- recognizer ----------
public interface Recognizer {
    /** Liefert Kandidaten; darf überlappen, verändert nichts. */
    List<Finding> detect(String text);
}

public final class TextScanner {
    public TextScanner(List<? extends Recognizer> recognizers);
    public static TextScanner standard();   // E-Mail, IPv4, URL, Pfad (wächst mit T-3, T-4, T-5, T-10)
    public List<Finding> scan(String text); // NPE("text") bei null; unveränderlich, überlappungsfrei,
                                            // aufsteigend nach start (AK-1, AK-7, AK-8)
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
    public static List<Finding> resolve(List<Finding> candidates);   // Regeln Kap. 2.5
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
geschützt als nötig, kein Leck).

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

#### 2.4.5 Abgrenzung zu URL, IP, E-Mail (AK-24, AK-5)

Die Abgrenzung entsteht aus Startgrenze und Konfliktauflösung, nicht aus Sonderfällen im Pfad-Erkenner:
- (a) `https://example.invalid/a/b`: `/a` hat keine Startgrenze → kein Pfad-Kandidat; nur URL.
- (b) `https://example.invalid/?datei=C:\Tools\a.exe`: Pfad-Kandidat `C:\Tools\a.exe` (Startgrenze `=`) liegt ganz in
  der URL → URL gewinnt (2.5).
- (c) `\\192.0.2.10\freigabe\ordner`: IP-Kandidat liegt ganz im UNC-Pfad → PFAD gewinnt.
- (d) `C:\Benutzer\erika.beispiel@example.invalid\ablage`: E-Mail liegt ganz im Pfad → PFAD gewinnt.
- (e) `file:///C:/Tools/BeispielTool/tool.exe`: `FILE_URI` liefert einen Fund über den ganzen Ausdruck; `/C:` und
  `C:` haben keine Startgrenze; die URL-Regex kennt `file` nicht.

### 2.5 Konfliktauflösung (`ConflictResolver`, AK-7, AK-24, OF-12)

**Stufe 1 – Enthaltensein (umschließender Fund gewinnt, unabhängig von der Konfidenz):** Kandidaten sortieren nach
`start` aufsteigend, `end` absteigend, dann (nur für gleiche Bereiche) Konfidenz absteigend, Typrang (`URL` < `PFAD` <
`EMAIL` < `IP` < übrige alphabetisch), `source` alphabetisch. Durchlauf mit `maxEnd`: Kandidat mit `end <= maxEnd` ist in
einem früheren enthalten (oder bereichsgleich mit einem höher eingestuften) und entfällt; sonst behalten,
`maxEnd = end`. Danach sind `start` und `end` streng aufsteigend; es bleiben nur Teilüberlappungen.

**Stufe 2 – Teilüberlappung (kein Fund enthält den anderen):** Priorität Konfidenz absteigend, dann `start`
aufsteigend (früherer gewinnt), dann Länge absteigend, Typrang, `source`. Gierig übernehmen, wenn der Kandidat keinen
übernommenen überlappt (`TreeMap<start, Finding>`). Der Verlierer entfällt ganz; keine Vereinigung (AK-7: höhere
Konfidenz bleibt). Ergebnis nach `start` sortiert. O(n log n), deterministisch (AK-8).

Teilüberlappungen sind mit den Erkennern dieses Features nur konstruiert möglich, z. B.
`https://example.invalid/a'C:\Program Files\b'` (URL bis zum Leerzeichen, Pfad in `'…'`). Bekannte Folge: Der nicht
überlappende Rest des Verlierers bleibt Klartext, und ein in Stufe 1 entfernter, im Verlierer enthaltener Kandidat
lebt nicht wieder auf (TE-3).

### 2.6 Pseudonymisierung (`Pseudonymizer`)

1. Eingaben vollständig prüfen, bevor etwas erzeugt wird (AK-32): Text und Liste nicht `null`; je Element nicht `null`,
   `end <= text.length()`, `text.regionMatches(start, originalValue, 0, len)`, `start`/`end` trennen kein
   Surrogatpaar; nach Sortierung keine Überlappung (Angrenzen erlaubt, AK-34). Negative Position und `start >= end`
   lehnt bereits der `Finding`-Konstruktor ab (TE-10).
2. Funde nach `start` sortieren (AK-27).
3. Literalschutz (ab T-9, Kap. 2.7): Lücken zwischen den Funden tokenisieren; jedes Token wird ein `TEXT`-Fund.
4. In Textreihenfolge Platzhalter vergeben: Schlüssel (Typname, Originalwert) zeichengleich (AK-28, D-7); neuer
   Schlüssel → nächstes `n` (global ab 1, A-4); vorhandener → derselbe Platzhalter. Zähler je Aufruf, kein statischer
   Zustand (AK-29).
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
- JUnit 5 inkl. `junit-jupiter-params` (`@ParameterizedTest`, `@MethodSource`), `assertTimeoutPreemptively`.
- JDK: `java.util.regex`, `java.util.Random` (fester Startwert), `Character`, `Objects`, `List.copyOf`, `Map.copyOf`,
  `TreeMap`, `StringBuilder`.

## 4. Neue Abhängigkeiten

Keine (AK-52). Kein POM wird geändert.

---

## 5. Sicherheits- und Datenschutzaspekte

- **Kein Netzwerk-, DNS- oder Dateisystemzugriff** (AK-51): keine Klassen aus `java.net`, `javax.net`, `java.nio`,
  `java.io` im Produktivcode; IP/URL/UNC nur per Zeichenprüfung. Belegt durch Quelltext-Prüftest (T-20).
- **Kein Klartext** in `toString()`, Ausnahmen, Ausgaben (2.9). Belegt durch Marker-Test über alle Fehlerpfade (T-20).
- **Laufzeitsicherheit**: Regex-Regeln 2.3, linearer Pfad-Durchlauf 2.4.4; Grenzlasttests (T-19) und je Pfad-Task.
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

### T-2: Erkenner-Schnittstelle, Konfliktauflösung, Scanner
- **Dateien**: `recognizer/Recognizer.java`, `recognizer/TextScanner.java` (`standard()` zunächst leer),
  `conflict/ConflictResolver.java`; Tests `ConflictResolverTest`, `TextScannerTest` (Stub-Erkenner im Test).
- **AK**: AK-1, AK-6 (Stufe „fraglich“ bleibt), AK-7, AK-8, AK-24 (Regel mit Stubs), AK-46 (Scan), AK-47 (Scan),
  AK-54.
- **Tests**: enthaltener Fund mit **höherer** Konfidenz verliert gegen umschließenden mit niedrigerer; bereichsgleich →
  höhere Konfidenz, dann Typrang; Teilüberlappung → höhere Konfidenz, Gleichstand → früherer Start; Duplikate;
  Ergebnis überlappungsfrei, nach `start` sortiert, unveränderlich; `FRAGLICH`-Stub-Fund bleibt; zwei Aufrufe gleich;
  Eingaben unverändert; `""`/Leerraum → leer; `null` → NPE „text“.

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

### T-5: URL-Erkenner
- **Dateien**: `recognizer/general/UrlRecognizer.java`, `standard()`; Tests `UrlRecognizerTest`, `TextScannerTest`.
- **AK**: AK-1, AK-3 (URL), AK-5, AK-6, AK-49 (lange Zeichenfolge ohne Leerzeichen).
- **Tests**: AK-5-Beispiel als ein Fund; Satzzeichen am Ende entfernt; `(https://example.invalid/x)` ohne `)`,
  `https://example.invalid/a_(b)` mit `)`; Schema-Schreibung; Scanner: `http://192.0.2.10/status` → nur URL, URL mit
  E-Mail im Query → nur URL; `file:///…` → kein URL-Fund; 100 000 Zeichen ohne Leerzeichen mit/ohne `https://` < 2 s.

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
- **AK**: AK-32, AK-47 (Pseudonymisierung), AK-50 (Meldungen).
- **Tests**: je Fall ein Test mit erwartetem `Reason`: `null`-Element, `end` > Länge, Überlappung (auch unsortiert),
  Wert passt nicht, Fund trennt Surrogatpaar (Anfang und Ende); `null` Text/Liste → NPE mit Parametername; kein
  Teilergebnis; keine Meldung enthält den Marker.

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

### T-11: Pfad-Erkenner – Anführungszeichen
- **Dateien**: `PathRecognizer.java` (Regel 2.4.3 inkl. Rückfall); Test `PathRecognizerAnfuehrungTest`.
- **AK**: AK-16, AK-17 (c), AK-20, AK-23 (in Anführungszeichen), AK-49 (`"` ×100 000, `"C:\ a b c …` nie
  geschlossen).
- **Tests**: `Starte "C:\Program Files\Mein Tool\tool.exe" jetzt` → Inhalt als Fund; dasselbe mit `'…'` und
  `` `…` ``; `"C:\Tools\BeispielTool\tool.exe"` → `"[[PFAD_1]]"` nach Pseudonymisierung;
  `C:\Users\mustermann\Übersicht\Größe ✓.txt` und Emoji in Anführungszeichen ganz; nie geschlossen → Rückfall
  (`C:\Program`); Anführungszeichen erst nach Zeilenende → Rückfall; `|` vor dem Schluss → Rückfall; `"C:\"` → kein
  Fund; `'C:\a' und 'C:\b'` → zwei Funde; Grenzlast: `"`×100 000, `"C:\ a b c ` mit 100 000 Leerzeichen-Wörtern,
  `'C:\a `×100 000, `"C:\a|`×100 000 je < 2 s.

### T-12: Pfad-Erkenner – UNC
- **Dateien**: `PathForm.java` (`UNC`); Test `PathRecognizerUncTest`.
- **AK**: AK-11, AK-17 (b), AK-22 (UNC), AK-49 (`\\`).
- **Tests**: Zeile `UNC` (2.4.2) positiv/negativ; `Ablage \\srv-beispiel\Mein Ordner\datei.txt` → `\\srv-beispiel\Mein`,
  Rest kein Fund; in Anführungszeichen ganz; `\\\\srv-beispiel\\freigabe\\x` mit geprüfter Backslash-Anzahl;
  `\\?\C:\Tools\a.exe` nach Entscheidung N-2; `\\`×100 000 < 2 s.

### T-13: Pfad-Erkenner – Unix und Home
- **Dateien**: `PathForm.java` (`UNIX`, `HOME`); Test `PathRecognizerUnixTest`.
- **AK**: AK-12, AK-13, AK-18 (`/opt/beispiel/a, /opt/beispiel/b`), AK-49 (`/a`, `~/`).
- **Tests**: Zeilen `UNIX` und `HOME` positiv/negativ, insbesondere alle Negativbeispiele aus AK-13; Konfidenz 2 vs. 3
  Segmente; Startgrenze (`a/opt/x` kein Fund, `=/opt/x`, `(/opt/x)` Fund); `//srv-beispiel/freigabe` nach N-2;
  bekannte Grenzen `und /oder/beides`, `/log=C:\Logs\a.log` als ein Fund festgehalten; `/a`×100 000 und `~/`×100 000
  < 2 s.

### T-14: Pfad-Erkenner – relative Pfade
- **Dateien**: `PathForm.java` (`RELATIV`); Test `PathRecognizerRelativTest`.
- **AK**: AK-14.
- **Tests**: Zeile `RELATIV` positiv/negativ; Stufe nicht `SICHER`; `..\..\x`; Satzpunkt davor ohne Leerzeichen
  (`Ende../x`) kein Fund.

### T-15: Pfad-Erkenner – Umgebungsvariablen
- **Dateien**: `PathForm.java` (`UMGEBUNG`); Test `PathRecognizerUmgebungTest`.
- **AK**: AK-15, AK-49 (`%`).
- **Tests**: Zeile `UMGEBUNG` positiv/negativ; Name mit 64 Zeichen ja, 65 nein; `%`×100 000, `%a`×100 000,
  `$`×100 000 < 2 s.

### T-16: Pfad-Erkenner – `file:` und Abgrenzung zu URL, IP, E-Mail
- **Dateien**: `PathForm.java` (`FILE_URI`); Test `PathRecognizerFileUriTest`, `TextScannerAbgrenzungTest`.
- **AK**: AK-5 (kein Pfad-Fund für `/pfad`, Scannerebene), AK-7, AK-8 (Standard-Scanner deterministisch), AK-24.
- **Tests**: Zeile `FILE_URI`; AK-24 (a)–(e) über `TextScanner.standard()` mit genau einem Fund des erwarteten Typs;
  IP und E-Mail außerhalb des Pfads im selben Satz zusätzlich erkannt; AK-5-Text ergibt nur den URL-Fund;
  `Mail …, Server 192.0.2.10, Tool C:\Tools\a.exe` → drei Funde in Textreihenfolge; zweimal scannen → gleiche Liste.

### T-17: Roundtrip-Korpus
- **Dateien**: Test `RoundtripKorpusTest` (Paket `de.skillshield.core`), Texte als Java-Strings mit `bs(…)`.
- **AK**: AK-25, AK-26, AK-28, AK-37, AK-38, AK-39, AK-54 (KI-Simulation durch feste Umformung).
- **Tests**: Korpus mit allen Pfadformen (AK-9 bis AK-18, mit und ohne Anführungszeichen, doppelte Backslashes,
  `$`, `${HOME}`, `%APPDATA%`), E-Mail, IP, URL, `file:`, manuellen `PERSON`-Funden; je Text Scan → alle Funde
  pseudonymisieren → keine Pfadbestandteile im Ausgabetext → Rückübersetzung → `assertEquals` mit Original, keine
  Meldung; Zeilenenden-Varianten, führender/abschließender Leerraum, Tabs, Umlaute, `e`+U+0301, Emoji vor/hinter/
  zwischen Funden, U+FEFF am Anfang, U+200B, RTL-Text; feste Umformung (Zeilen vertauschen, Präfix/Suffix) vor der
  Rückübersetzung.

### T-18: Fuzz-Test
- **Dateien**: Test `RoundtripFuzzTest`.
- **AK**: AK-45, AK-1.
- **Tests**: 1 000 Texte aus `new Random(<Konstante>)`, Bausteine: synthetische Funde inkl. Pfade aller Formen mit
  Backslashes, Leerzeichen, Anführungszeichen (auch ungeschlossen), Sonderzeichen, Unicode, Zeilenenden, `[`, `]`,
  `_`, `[[X_1]]`, `[[TEXT_1]]`, `[[PERSON_1`; zufällige Teilmenge der Scan-Funde plus manuelle Funde (surrogatsicher,
  überlappungsfrei); Prüfung Teilstring-Invariante, Roundtrip gleich, keine Meldung. Teil 2: 1 000 Zufallseingaben an
  `restore` mit fester Zuordnung → keine Ausnahme. Fehlermeldung nennt Startwert und Iteration, keine Texte.

### T-19: Grenzlast
- **Dateien**: Test `GrenzlastTest`.
- **AK**: AK-48, AK-49 (alle Eingaben durch alle drei Schritte).
- **Tests**: ≥ 1 000 000 Zeichen mit ≥ 10 000 Funden, davon mindestens die Hälfte Pfade aller Formen: Scan,
  Pseudonymisierung, Rückübersetzung je < 10 s, Roundtrip gleich. Jede Eingabe aus AK-49 sowie 100 000 × `[[` und
  × `[[A_` durch alle drei Schritte je < 2 s. Standard-Heap des Surefire-Laufs.

### T-20: Projektregeln (Netz, Dateisystem, Klartext, Testdaten)
- **Dateien**: Tests `ArchitekturRegelnTest`, `KlartextFreiheitTest`, `TestdatenSynthetischTest` (Paket
  `de.skillshield.core`); `README.md` (Satz „derzeit 25 Tests, 5 je Modul“ zählerunabhängig formulieren).
- **AK**: AK-47, AK-50, AK-51, AK-52, AK-53, AK-54.
- **Tests**:
  - `ArchitekturRegelnTest`: liest `src/main/java/**/*.java` (Surefire-Arbeitsverzeichnis = Modulverzeichnis); verbietet
    `java.net`, `javax.net`, `java.nio`, `java.io`, `InetAddress`, `Socket`, `URLConnection`, `HttpClient`,
    `FileSystem`, `Files`, `Paths`, `ProcessBuilder`, `Runtime`, `System.out`, `System.err`, `printStackTrace`,
    `java.util.logging`, `System.Logger` (Wortgrenzen); alle Dateien unter `de/skillshield/core`. Diese Wörter dürfen
    auch in Kommentaren des Produktivcodes nicht stehen.
  - `KlartextFreiheitTest`: Marker-Pfad `bs("C:¦Marker¦mustermann¦MarkerTool.exe")` und Marker-E-Mail durch jeden
    Fehlerpfad aus AK-32, AK-41, AK-42, AK-47; prüft Meldung, `cause`-Kette, alle `toString()` und umgeleitete
    `System.out`/`System.err`.
  - `TestdatenSynthetischTest`: liest `src/test/java/**/*.java`; jede E-Mail-ähnliche Zeichenfolge endet auf
    `example.invalid|example.org|example.net`; jede gültige IPv4 liegt in den RFC-5737-Bereichen; Pfade per Durchsicht.

### Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-1, T-2, T-3, T-4, T-5, T-10, T-18 | AK-19 | T-10 | AK-37 | T-17 |
| AK-2 | T-3 | AK-20 | T-10, T-11 | AK-38 | T-8, T-17 |
| AK-3 | T-3, T-5 | AK-21 | T-10 | AK-39 | T-17 |
| AK-4 | T-4 | AK-22 | T-10, T-12 | AK-40 | T-8 |
| AK-5 | T-5, T-16 | AK-23 | T-10, T-11 | AK-41 | T-8 |
| AK-6 | T-1, T-2, T-3, T-4, T-5, T-10 (je Pfadform T-10–T-16) | AK-24 | T-2, T-16 | AK-42 | T-8 |
| AK-7 | T-2, T-16 | AK-25 | T-6, T-17 | AK-43 | T-8 |
| AK-8 | T-2, T-3, T-16 | AK-26 | T-6, T-17 | AK-44 | T-8, T-9 |
| AK-9 | T-10 | AK-27 | T-6 | AK-45 | T-8, T-18 |
| AK-10 | T-10 | AK-28 | T-6, T-17 | AK-46 | T-2, T-6, T-8 |
| AK-11 | T-12 | AK-29 | T-6 | AK-47 | T-2, T-6, T-7, T-8, T-20 |
| AK-12 | T-13 | AK-30 | T-6, T-9 | AK-48 | T-19 |
| AK-13 | T-13 | AK-31 | T-6 | AK-49 | T-3, T-4, T-5, T-10, T-11, T-12, T-13, T-15, T-19 |
| AK-14 | T-14 | AK-32 | T-1, T-7 | AK-50 | T-1, T-6, T-7, T-8, T-20 |
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

---

## 8. Entscheidungen für die Freigabe

Der Plan ist mit den Empfehlungen umsetzbar.

### 8.1 Nutzer muss entscheiden

| Nr. | Frage | Empfehlung | Begründung | Wirkung bei anderer Wahl |
|-----|-------|------------|------------|--------------------------|
| N-1 | **Nie geschlossenes Anführungszeichen** vor einem Pfad (fachlich ungeregelt) | **Regel ohne Anführungszeichen** (Ende am ersten Leerzeichen), TE-12 | Passt zu D-5; ein vergessenes oder als Apostroph gemeintes Zeichen erzeugt keinen Riesenfund bis Zeilenende. | „Bis Zeilenende“: mehr Schutz bei abgeschnittenen Zitaten, aber Fehlfunde über ganze Zeilen; „kein Fund“: schlechter als heute. Eine Zeile in T-11. |
| N-2 | **OF-7: `//srv-beispiel/freigabe` und `\\?\C:\…`** | **Erkennen** (Abweichung vom Vorschlag „kein Fund“). `\\srv-beispiel` allein und `C:\` bleiben kein Fund. | Beide sind echte Pfade mit Server- bzw. Ordnernamen. Bei „kein Fund“ bleibt der ganze Pfad Klartext, denn `C:` hinter `\\?\` und `srv` hinter `//` haben keine Startgrenze. Die befürchteten Verwechslungen treten nicht auf: `// Kommentar` hat kein Segment, `https://…` keine Startgrenze. | Vorschlag des product-owner: je eine Zusatzbedingung im Kopf von `UNIX` (kein zweiter `/`) bzw. `UNC` (Server ≠ `?`/`.`); Server- und Pfadnamen bleiben dann sichtbar. |
| N-3 | **OF-10: Stufen der Pfadformen** (vom PO zur Bestätigung vorgesehen) | Laufwerk, UNC, `file:` **sicher** (0.95); Unix ≥ 3 Segmente und Home **sicher** (0.90); Unix mit 2 Segmenten, relativ, Umgebungsvariable **wahrscheinlich** (0.80); Schwellen sicher ≥ 0.90, wahrscheinlich ≥ 0.70. Kein Review-Flag-Feld. | Entspricht dem PO-Vorschlag; „Home“ ergänzt (eindeutiger Kopf). Funde werden nie verworfen, die Stufe steuert nur die spätere Prüfanzeige. | Nur Konstanten in `PathForm`; teuer wird es erst, wenn die Oberfläche darauf aufbaut. |

### 8.2 Technisch festgelegt (änderbar)

| Nr. | Thema (OF) | Festlegung |
|-----|-----------|------------|
| TE-1 | `Finding` (OF-15) | Record `type, originalValue, start, end, confidence, source`; ohne `replacement` und ohne `note`; `end` exklusiv, UTF-16-Indizes; Validierung im Konstruktor; `toString()` ohne `originalValue`. `RecognitionContext`, `PseudonymMatch`, `PseudonymizedDocument` entfallen. |
| TE-2 | Konfidenz (OF-10) | E-Mail 0.95, URL 0.95, IP 0.85 (Versionsnummern möglich), Pfade nach N-3. Quellen: `email`, `ipv4`, `url`, `pfad-laufwerk`, `pfad-unc`, `pfad-unix`, `pfad-home`, `pfad-relativ`, `pfad-umgebung`, `pfad-file-uri`, `manuell`. |
| TE-3 | Konfliktauflösung (OF-12) | Kap. 2.5: Enthaltensein gewinnt immer (umschließender Fund), bereichsgleich → Konfidenz, Typrang URL > PFAD > EMAIL > IP; Teilüberlappung → höhere Konfidenz, dann früherer Start; Verlierer entfällt, keine Vereinigung (AK-7). Alternative „Vereinigung“ würde den Rest des Verlierers schützen, braucht aber eine AK-7-Änderung. |
| TE-4 | Grenzen (OF-13) | AK-48: 1 000 000 Zeichen, ≥ 10 000 Funde, je Schritt < 10 s (PO-Vorschlag). AK-49: je Eingabe und Schritt < 2 s (strenger als 10 s, damit quadratisches Verhalten sicher auffällt). Keine Obergrenze für Textlänge oder Pfadlänge. |
| TE-5 | Abbruch, Threads, Tresor (OF-14 a–d) | Kein Abbruch (linear). Alle Klassen zustandslos/unveränderlich, faktisch threadsicher, nicht zugesichert. Keine `Vault`-Schnittstelle; `PlaceholderMapping` ist ein eigener Typ, den das Tresor-Feature ablöst. Fremde Zuordnung nicht verhindert. |
| TE-6 | Quellenwidersprüche (OF-15) | Basispaket `de.skillshield`; Tresor in `skillshield-vault`; Rückübersetzung nach Platzhalter, nicht nach Position; „keine KI im Produkt“ und „keine KI-Autonomie“ beide eingehalten; Plan.md Kap. 7 („strukturerhaltende Pfade“) sollte nach D-4 nachgezogen werden (keine Codewirkung). |
| TE-7 | Laufzeitsicherheit | Regex-Regeln 2.3; Pfade ohne Regex, linear nach 2.4.4. |
| TE-8 | Nachweis „kein Netz/Dateisystem“ (AK-51) | Quelltext-Prüftest T-20. Verworfen: SecurityManager (abgekündigt), eigener `InetAddressResolverProvider` (fängt nur DNS). |
| TE-9 | `Pseudonymizer`/`Reidentifier` | Finale Klassen statt Interface + Implementierung; Zuordnung als `PlaceholderMapping`. |
| TE-10 | Prüfort AK-32 | Negative Position und `start >= end` lehnt `Finding` ab; textbezogene Fälle `Pseudonymizer`. |
| TE-11 | Typnamen (OF-11) | `PFAD`, `EMAIL`, `IP`, `URL` (Erkenner), `TEXT` (Literalschutz); manuelle Funde mit beliebigem Namen im Format `[A-Z][A-Z0-9]{0,31}`; Kleinbuchstaben, Unterstrich, Leerzeichen → `IllegalArgumentException` ohne Echo. Nummerierung global (A-4). |
| TE-12 | Ungeschlossenes Anführungszeichen | Rückfall auf die Regel ohne Anführungszeichen (N-1). Schließendes Zeichen nur auf derselben Zeile, vor Tab und vor `<`, `>`, `\|` (bzw. `"` bei `'`/`` ` ``). |
| TE-13 | Pfadende und Pfadzeichen (OF-5) | (a) Satzzeichen `. , ; : ! ?` und `'` am Ende gekürzt; umschließende Zeichen nie im Fund. (b) `)`, `]`, `}` am Ende nur, wenn im Fund geöffnet; gleiche Regel für URL (`)`, `]`). (c) `*` gehört zum Pfad, abschließendes `?` nicht. (d) `<`, `>`, `\|`, `"` und Backtick beenden den Pfad. (e) keine weiteren URL-Schemata. |
| TE-14 | Unix (OF-4) | Absolut ≥ 2 Segmente, Home ≥ 1 Segment nach `~/`, `~benutzer/x` kein Fund, Beginn nur an Startgrenze; `/` und `\` sind für alle Formen Trenner. |
| TE-15 | Umgebungsvariablen (OF-6) | Wie PO-Vorschlag; Namen höchstens 64 Zeichen; PowerShell `$env:NAME\…` nicht abgedeckt. |
| TE-16 | `file:` (OF-8) | PFAD-Fund über den ganzen Ausdruck inkl. `file:///` (PO-Vorschlag). |
| TE-17 | Doppelte Backslashes (OF-9) | Wie PO-Vorschlag; Trenner-Läufe zählen als ein Trenner; UNC-Kopf mit genau 2 oder 4 Backslashes. |
| TE-18 | Ein Pfad-Erkenner | Ein `PathRecognizer` mit Formen als Enum-Konstanten (Begründung 2.4); kein `java.nio.file`, kein Dateisystem. |

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
- **P-4 Teilüberlappung** (TE-3): mit künftigen Erkennern (Namen) neu bewerten.
- **P-5 Plan.md Kap. 7** nach D-4 anpassen („strukturerhaltende Pfade“ streichen bzw. auf „ein Platzhalter je Pfad“
  ändern); Aufgabe außerhalb dieses Features.

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
