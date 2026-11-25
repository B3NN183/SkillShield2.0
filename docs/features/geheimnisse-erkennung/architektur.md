# Architektur: Erkennung von Passwörtern und Geheimnissen

Feature-Kurzname: `geheimnisse-erkennung` (Feature 1 der Übersicht `docs/features/uebersicht-it-infrastruktur.md`).
Grundlage: `anforderung.md` (D-1 bis D-18, AK-1 bis AK-52, G-1 bis G-17, OF-1 bis OF-10), `Plan.md` (Nachtrag vom
24.11.2026), Code-Stand nach `oberflaeche-mvp` (Feature 0 `datum-und-kontextvorrang` ist geplant, aber noch nicht
umgesetzt). Betroffen: `skillshield-core`, in `skillshield-ui` nur `Beschriftungen` samt Test.

## 0. Zusammenfassung in normaler Sprache

- Zwei neue Erkenner, `PasswortRecognizer` und `TokenRecognizer`, im neuen Paket
  `de.skillshield.core.recognizer.geheimnis`. Sie lesen den Text von Hand (kein regulärer Ausdruck) und teilen sich
  eine gemeinsame Kontextregel (Kontextwort, Verbinder, Wertgrenzen) und einen gemeinsamen Wert-Test (W1 bis W6).
- **Bestand bleibt unverändert, beweisbar.** Der `TextScanner` rechnet zuerst genau wie heute das Ergebnis ohne
  Geheimnis-Kandidaten. Geheimnis-Kandidaten füllen danach nur die Lücken zwischen diesen Funden (**Lückenregel**).
  Ein bestehender Fund wird dadurch nie verändert, verschluckt, verkleinert oder umgetypt; jede Position eines
  Geheimnis-Kandidaten bleibt trotzdem geschützt. `ConflictResolver`, `Recognizer`, `Finding`, `FindingType`,
  `Pseudonymizer`, `Reidentifier` bleiben unverändert.
- **Prüfstand vor der Logik.** Task T-1 friert den Scanner von heute als Testkopie ein, misst das Referenz-Ticket
  und die Konfliktfälle und legt die Testdaten-Regeln für Geheimnisse an. T-2 baut Negativkorpus, Positivkorpus und
  beide Zufallsgeneratoren und friert sie ein, bevor eine Zeile Erkennerlogik existiert.
- Passwort nach Kontextwort mit `:`/`=` (das, was das Referenz-Ticket braucht) ist mit T-4 fertig; danach folgen
  schwächere Verbinder, Tokens, kontextfreie Formen, Verbindungszeichenfolgen und der Gesamtnachweis.

## 1. Lösungsansatz

### 1.1 Einordnung in den Scan: Lückenregel (OF-7, D-13, AK-31)

Heute: `TextScanner.scan` sammelt Kandidaten, schneidet nachrangige (KVNR, BSNR) am Bestand zu und löst auf.
Neu, als einzige Änderung am Ablauf:

1. Kandidaten vom Typ `PASSWORT` oder `TOKEN` (`GeheimnisTypen.istGeheimnistyp`) werden beim Einsammeln beiseite
   gelegt, gleich von welchem Erkenner sie kommen.
2. Über alle übrigen Kandidaten läuft der **bisherige Ablauf unverändert** (Zuschneiden nachrangiger Erkenner,
   Konfliktauflösung). Ergebnis `alt`. Gibt es keine Geheimnis-Kandidaten, ist `alt` das Ergebnis (bitgleich zu heute).
3. Jeder Geheimnis-Kandidat wird auf die Teile **außerhalb aller Funde von `alt`** zugeschnitten (alle Typen, auch
   KV und zusammengelegte Funde). Liegt er ganz in `alt`, entfällt er. Jedes Reststück bleibt als Fund mit Typ,
   Konfidenz und Quelle des Kandidaten und dem Textausschnitt als Originalwert; es wird **nicht** gekürzt (auch kein
   Leerraum), damit jede Kandidatenposition abgedeckt bleibt (Schutzgarantie der bestehenden Fuzz- und
   Grenzlasttests).
4. `ConflictResolver.resolve(text, alt + Reststücke)`. Weil kein Reststück einen Fund von `alt` berührt und die Funde
   von `alt` überlappungsfrei sind, bildet jeder Fund von `alt` eine eigene Gruppe und wird unverändert übernommen.
   Nur Geheimnis-Reststücke untereinander können zusammengelegt werden.

Eigenschaften (Beweis im Javadoc von `TextScanner`, Test in T-3): (a) `alt` ⊆ Ergebnis, feldgleich; (b) jeder
weitere Fund hat Typ `PASSWORT` oder `TOKEN`; (c) Abdeckung(Ergebnis) = Abdeckung(`alt`) ∪ Abdeckung(aller
Geheimnis-Kandidaten); (d) unabhängig von der Reihenfolge der Erkenner (Typregel, keine Listenposition). Aufwand:
eine zusätzliche Auflösung und je Kandidat eine binäre Suche über die sortierten Bereiche von `alt`, O(k log k).

Folgen für die Fälle aus D-13 (alle ohne Änderung eines bestehenden Funds):

| Fall | Ergebnis |
|------|----------|
| Wert in E-Mail-Form (`Passwort: <Name>@<Domäne>.example`) | E-Mail-Fund wie heute; PASSWORT-Kandidat liegt darin und entfällt |
| Passwort in URL, Pfad, E-Mail-Adresse (AK-28, AK-29) | bestehender Fund wie heute; kein PASSWORT-Fund |
| Oracle-Form mit Punkt im Ziel (G-6, AK-26) | heutiger Fund (vermutet EMAIL über `scott/<P3>@host.domäne`) bleibt unverändert; Passwort darin geschützt |
| KV-Fund neben oder in einem Wert (AK-30) | KV-Fund unverändert; Geheimnis nur in den Lücken |
| Teilüberlappung (Wert reicht über einen Bestandsfund hinaus) | Bestandsfund unverändert, Reststücke als PASSWORT/TOKEN daneben; keine Position frei |
| Bestandsfund liegt ganz im Wert (z. B. PEM-Zeile, die wie ein Unix-Pfad beginnt) | Bestandsfund bleibt, der Geheimnis-Fund zerfällt in Stücke (neue Grenze G-18, Kap. 9) |

Damit gibt es **keine** zulässige Änderung eines bestehenden Funds mehr; die Ausnahme „Typänderung bei G-6“ aus
D-13/AK-31 wird nicht gebraucht (Kap. 11, Punkt 1).

Verworfen: (1) Konflikte allein dem `ConflictResolver` überlassen – ein Wert, der eine E-Mail-Adresse oder einen
KV-Fund ganz enthält oder teilweise überlappt, würde diesen verschlucken oder umtypen (Verstoß gegen AK-31).
(2) Die vorhandene Nachrangigkeit (`Recognizer.nachrangig()`) nutzen – deren Reststück-Regel (mindestens drei
Ziffern, Kürzung auf ASCII-Zeichen) passt nicht zu Geheimnissen, und KV-Funde zählen dort nicht zum Bestand.
(3) Neue Methode in `Recognizer` – wäre eine Schnittstellenänderung (D-16).

### 1.2 Aufbau der Erkenner (OF-1)

Zwei Erkenner, weil D-1 zwei Typen festlegt und AK-43 „die neuen Erkenner“ einzeln in `standard()` erwartet. Beide
nutzen paketinterne Bausteine:

| Baustein | Inhalt |
|----------|--------|
| `GeheimnisZeichen` | Zeichenklassen (OF-10), Zeilenumbrüche, Anführungszeichen-Paare, ASCII-Vergleich ohne Groß/klein |
| `Werttest` | W1 bis W6 (D-8), Fachbegriffsliste, Versions-, Ticket-, Zahlwortform |
| `KontextRegel` | Kontextwörter (D-4, D-5), Zwischenteil, Verbinder K1 bis K4, Zeilenumbruch, Wertgrenzen (D-7), Schema-Formen (D-11), Konfidenz (D-9); ein Durchlauf je Art |
| `Verbindungsregel` | ODBC/ADO, Oracle-Form, Kommandozeile (D-12 Fälle 1 bis 3) |
| `TokenFormen` | JWT, PEM-Körper, Präfix-Token (D-11) |

**Pflegbare Konstanten.** Jede Liste steht genau einmal als `private static final List<String>` mit Javadoc
„geschlossene, pflegbare Liste (D-n)“: Stämme PASSWORT, Stämme TOKEN, ganze Wörter (`pw`, `pwd`, `bearer`,
`authorization`), Zwischenwörter, K2-Wörter, Schema-Wörter (`bearer`, `basic`, `token`), Sonderzeichen W2,
Fachbegriffe W3 (d), Präfixe, Oracle-Anker, Kommandozeilen-Optionen je Typ. Alle in Kleinbuchstaben ASCII; Vergleich
nach OF-10.

**Wortkörper ohne Rückwärtslesen über eine Schranke.** Der Haupt-Durchlauf liest jeden Wortkörper (maximale Folge aus
Buchstaben, Ziffern, kombinierenden Zeichen, `_`, `-`) einmal vorwärts bis zu seinem Ende. Erst am Ende wird
rückwärts verglichen: PASSWORT höchstens 10 Zeichen (längster Stamm `passphrase`); TOKEN höchstens 10 Buchstaben
(`privatekey`) und dabei höchstens drei übersprungene `_`/`-` (mehr Trenner: kein Treffer). `pw`, `pwd`, `bearer`,
`authorization` zählen nur, wenn der ganze Wortkörper gleich ist (bei `pw`/`pwd` zusätzlich: davor Textanfang oder
ein Zeichen, das weder Buchstabe noch Ziffer ist; ein `-` oder `_` davor gehört schon zum Wortkörper, `xpw` und
`db-pw` sind also kein Treffer, so AK-3). `API Key`: Wortkörper endet (ohne Trenner) auf `api`, genau ein
Leerraumzeichen, danach der ganze Wortkörper `key`.

**Zeichenmischung (W2, W6)** in einem Durchlauf über den Wert: Zähler für Buchstabe, Großbuchstabe, Kleinbuchstabe,
Ziffer, Sonderzeichen aus der W2-Liste (das letzte Zeichen zählt für W2 nicht als Sonderzeichen), gleiches Zeichen
überall (W5).

### 1.3 Kontextregel im Einzelnen (D-4 bis D-9)

Ablauf je Kontextwort-Treffer (Art PASSWORT oder TOKEN):

1. Optional ein `"`/`'` direkt hinter dem Kontextwort (D-4).
2. Optional **Zwischenteil**: Leerraum, ein Zwischenwort als ganzes Wort, danach höchstens drei Wörter (je ohne
   Leerraum, ohne `:`/`=`, höchstens 64 Zeichen, nicht über einen Zeilenumbruch). Nach jedem Wort wird geprüft, ob ein
   Verbinder folgt; der **erste** gefundene Verbinder beendet die Suche (kein Weiterprobieren, deterministisch).
3. **Verbinder**, Leerraum davor und danach nur in derselben Zeile:
   K1 `:` oder `=`; K2 ganzes Wort `ist`, `lautet`, `is` (nicht für `pw`/`pwd`); K3 `|` oder, wenn keiner der
   anderen Verbinder steht, ein Tabulator im Leerraum; K4 `<Kontextwort>` mit `<` unmittelbar davor und `>`
   unmittelbar dahinter.
4. Nach K1 darf genau ein Zeilenumbruch (LF, CR LF, CR) samt Einrückung folgen; leere Zeile oder Umbruch nach K2/K3/K4:
   kein Fund.
5. **Wert** (D-7): Anführungszeichen-Paar innerhalb von 256 Zeichen in derselben Zeile ergibt den Inhalt (mit
   Leerzeichen); sonst das erste Wort bis Leerraum/Zeilenende (ohne ein öffnendes Zeichen ohne Partner), höchstens
   4 096 Zeichen; dann wiederholt Satzzeichen am Ende entfernen (`. , ; : ?`, unausgeglichene `)`, `]`, `}`,
   schließendes Anführungszeichen ohne öffnendes), `!` bleibt. K4: Inhalt zwischen `>` und dem nächsten `<` derselben
   Zeile, ohne Leerraum, höchstens 256 Zeichen (Präzisierung, Kap. 11 Punkt 6).
6. **Schema-Formen** (nur TOKEN): Ist der Wert hinter `Authorization` + K1 eines der Wörter `Bearer`, `Basic`,
   `Token`, wird das nächste Wort derselben Zeile der Wert. Das ganze Wort `Bearer` mit Leerraum (ohne Verbinder)
   ist selbst ein Kontext. Für beide: Wert-Test W1 bis W5 und Länge mindestens 16 (ohne Klassenregel von W6).
7. Wert-Test (Kap. 1.4), Konfidenz nach D-9; Kandidat `Finding(typ, wert, start, end, konfidenz, quelle)`.
8. **Weitersuche:** Nach jedem ausgewerteten Wert (Fund oder nicht) wird hinter dem gelesenen Wertbereich
   (Wort bzw. Anführungszeichen-Paar) weitergesucht. Ohne Wert (kein Verbinder) geht es hinter dem Kontextwort weiter.
   Das erfüllt D-7 „mehrere Werte“ und macht den Aufwand linear (OF-3); Folge ist die neue Grenze G-19 (Kap. 9).

Konfidenz (D-9): K1 gleiche Zeile, Wert ab 8 Zeichen 0,95; K1 mit 6 oder 7 Zeichen 0,80; K1 mit Zeilenumbruch, K2,
K3, K4 0,80; `Authorization: <Schema> <Wert>` 0,95 (K1); `Bearer <Wert>` ohne Doppelpunkt 0,80 (D-9 nennt den Fall
nicht; Kap. 11 Punkt 5). Liefern zwei Kontexte denselben Bereich (`Authorization: Bearer <T>`), bleibt im Erkenner
nur der mit der höchsten Konfidenz.

### 1.4 Wert-Test (D-8)

W1 Länge 6 bis 4 096 (in Anführungszeichen und K4 bis 256). W2 Buchstabe und (Ziffer oder Sonderzeichen der Liste,
nicht als letztes Zeichen). W3 nicht (a) Versionsform, (b) Ticketform (2 bis 6 ASCII-Großbuchstaben, optional `-`,
mindestens drei ASCII-Ziffern, sonst nichts), (c) Zahlwort-Verbund (ASCII-Ziffern, `-`, nur Kleinbuchstaben),
(d) Fachbegriff der Liste (ASCII ohne Groß/klein, ganzer Wert), **(e) ISO-Datum mit Uhrzeit** (`JJJJ-MM-TT`, dann
`T` oder Leerraum-frei angehängt `hh:mm`, optional `:ss`, Bruchteile, `Z` oder `+hh:mm`/`-hh:mm`; Präzisierung,
Kap. 11 Punkt 4). W4 nicht mit `/`, `\`, `~`, `./`, `../`, `X:\` beginnend, kein `://` darin. W5 nicht mit `[[`,
`$`, `%`, `{`, `<`, `[` beginnend und nicht ein einziges wiederholtes Zeichen. W6 (K2, K3, K4, Umbruch nach K1):
Länge ab 8 und mindestens drei der vier Klassen Großbuchstabe, Kleinbuchstabe, Ziffer, Sonderzeichen; als
Sonderzeichen zählt dieselbe Liste wie in W2 (Präzisierung, Kap. 11 Punkt 3).

### 1.5 Tokens ohne Kontext (D-10, D-11)

- **JWT:** Start an `eyJ` mit Grenze davor (kein `A-Z a-z 0-9 _ . -`), drei Teile aus `A-Z a-z 0-9 _ -`, genau
  zwei Punkte, Teile 1 und 2 beginnen mit `eyJ`, jeder Teil mindestens 8 Zeichen, Grenze dahinter. Gelesen wird der
  ganze Lauf aus JWT-Zeichen einmal; danach geht es hinter dem Lauf weiter (linear).
- **PEM:** `-----BEGIN ` + Art (höchstens 40 Zeichen aus `A-Z` und Leerzeichen) + `-----`, Art endet auf
  `PRIVATE KEY` oder `PRIVATE KEY BLOCK`. Körper: die folgenden Zeilen bis zur ersten Zeile, die mit `-----` beginnt.
  Ist sie eine `-----END …-----`-Zeile, ist der Körper alles dazwischen; sonst (keine END-Zeile) reicht der Körper von
  der ersten Zeile über optionale Kopfzeilen `Name: Wert` und eine Leerzeile bis zur letzten zusammenhängenden
  Base64-Zeile (`A-Z a-z 0-9 + / =`, höchstens 100 Zeichen). **Bereich:** erstes Zeichen der ersten Körperzeile bis
  Ende der letzten Körperzeile, ohne den Zeilenumbruch hinter BEGIN und vor END; innere LF/CR LF gehören dazu (OF-5).
  Leerer Körper: kein Fund. Jeder Block wird einmal gelesen; die nächste BEGIN-Zeile beendet ihn (linear auch bei
  wiederholtem BEGIN ohne END).
- **Präfix-Token:** Präfix der Liste mit Grenze davor (kein Buchstabe, keine Ziffer, `_`, `-`), Rest aus
  `A-Z a-z 0-9 _ -` mit mindestens einer Ziffer, gesamt mindestens 20 Zeichen; Ende am Laufende.

### 1.6 Verbindungszeichenfolgen und Kommandozeile (D-12)

- **ODBC/ADO:** Je Zeile höchstens einmal (beim ersten Schlüssel `Password`/`Pwd`/`Passwd` mit `=`) wird gezählt, ob
  die Zeile ein weiteres Paar `Name=` enthält (Name: Buchstabe, dann bis 31 Buchstaben, Ziffern, Leerzeichen, `_`,
  `-`, Paare durch `;` getrennt). Ergebnis je Zeile zwischengespeichert, dadurch linear. Wert bis `;`/Zeilenende,
  in `'…'`, `"…"` oder `{…}` bis zum schließenden Zeichen, höchstens 256 Zeichen, Leerraum am Rand gehört nicht dazu;
  halber Wert-Test (nicht leer, W5). Ein solcher Schlüssel wird **nur** nach dieser Regel ausgewertet, auch wenn der
  Wert verworfen wird (sonst läse die Kontextregel `Pwd=x;Database=y` als ein Wort).
- **Oracle:** Anker `jdbc:oracle:thin:`/`jdbc:oracle:oci:` unmittelbar davor, oder ganzes Wort `sqlplus`, `sqlldr`,
  `expdp`, `impdp`, `connect`, `conn`, dann Leerraum, höchstens zwei Optionen (`-` + höchstens 64 Zeichen), Benutzer
  (Buchstabe + bis 29 aus `A-Z a-z 0-9 _ $ #`), `/`, Passwort (1 bis 128 Zeichen ohne Leerraum, `/`, `@`; W5), `@`,
  mindestens ein Zeichen Ziel. Fund nur das Passwort.
- **Kommandozeile:** `--` mit Textanfang, Leerraum oder Anführungszeichen davor, Optionsname exakt aus der Liste
  (danach `=` oder genau ein Leerzeichen), Wertgrenzen und voller Wert-Test wie D-7/D-8, Konfidenz 0,95.
- **Vorrang im Erkenner:** Liefern Kontextregel und Verbindungsregel denselben Bereich (`--password=<P1>`), bleibt der
  Kandidat der Verbindungsregel (Quelle `passwort-verbindung`, 0,95).
- **URLs** (Fall 4) und alle anderen Formen (Fall 5): nichts Neues; die Lückenregel lässt den URL-Fund unberührt.

### 1.7 Zeichenklassen (OF-10)

Gelesen wird nach Codepoints. Buchstabe `Character.isLetter`, Ziffer `Character.isDigit` (für W2, W6),
Großbuchstabe `Character.isUpperCase`, Kleinbuchstabe `Character.isLowerCase` (`ß` ist klein, Umlaute zählen),
kombinierende Zeichen (Mn, Mc, Me) gehören zum Wortkörper. Formen in W3 (a) bis (e), Präfixe, JWT, Base64 und
Ticketnummern nur ASCII. Zeilenumbruch: LF, CR, CR LF (ein Umbruch), U+0085, U+2028, U+2029. Leerraum:
`Character.isWhitespace` oder `Character.isSpaceChar` ohne die Zeilenumbrüche (damit U+00A0, U+2009, U+202F wie ein
Leerzeichen, AK-45). Vergleich mit Stämmen, Listen und Ankern zeichenweise nur ASCII: `A-Z` wird zu `a-z`, jedes
Nicht-ASCII-Zeichen passt nie (kein `toLowerCase`, keine Locale, kein `İ`/`ı`-Fall). Unsichtbare Formatzeichen
(U+200B u. a.) sind kein Wortkörperzeichen und trennen das Kontextwort (G-8). Funde beginnen und enden nie in einem
Surrogatpaar, weil alle Grenzzeichen BMP-Zeichen sind und nach Codepoints gelesen wird.

### 1.8 Linearität (OF-3, AK-46)

Beleg im Javadoc von `KontextRegel`, `Verbindungsregel`, `TokenFormen`: Der Haupt-Durchlauf besucht jedes Zeichen
einmal. Je Kontextwort liest die Regel höchstens fünf Bausteine (Zwischenwort, drei Wörter je höchstens 64 Zeichen,
Verbinder) und den Leerraum dazwischen; ein Leerraum-Lauf wird so von höchstens fünf Treffern gelesen. Die
Anführungszeichen-Suche ist auf 256 Zeichen begrenzt, ein Wert auf 4 097 gelesene Zeichen; durch die Weitersuche
hinter dem Wertbereich (1.3 Punkt 8) wird jedes Zeichen höchstens einmal als Wert gelesen. PEM, JWT, Präfix und
ODBC lesen jeden Block/Lauf/jede Zeile einmal; Oracle-Anker lesen höchstens 64 + 64 + 30 + 129 Zeichen plus
Leerraum. Zusätzlicher Speicher: konstant plus Kandidatenliste. Kein Zurücksetzen der Leseposition über diese festen
Schranken hinaus, keine Rekursion.

### 1.9 Oberfläche (OF-5, OF-9)

`Beschriftungen` wechselt von `Map.of` (höchstens zehn Paare) auf `Map.ofEntries` und bekommt
`GeheimnisTypen.PASSWORT` → „Passwort“, `GeheimnisTypen.TOKEN` → „Token oder Schlüssel“. Sonst keine Änderung in
`ui`/`app`. Mehrzeilige Funde gibt es schon heute (KV-Nummern über Zeilenumbruch, Feature `kv-trenner-und-umbruch`);
`Pseudonymizer` und `Reidentifier` arbeiten bereichsbasiert und brauchen keine Änderung. Die Fundtabelle zeigt einen
PEM-Körper als eine gekürzte Zelle; G-17 bleibt als Grenze, Test in T-7 nur auf Kernebene (Roundtrip LF/CR LF).

## 2. Betroffene Module, Pakete, Typen

### 2.1 Produktivdateien

| Datei | Task | Änderung |
|-------|------|----------|
| `core/…/recognizer/geheimnis/GeheimnisTypen.java` (neu, `public final`) | T-3 | Typkonstanten, `istGeheimnistyp` |
| `core/…/recognizer/geheimnis/package-info.java` (neu) | T-3 | Paketbeschreibung, Grenzen-Übersicht |
| `core/…/recognizer/TextScanner.java` | T-3, T-4, T-6 | Lückenregel (T-3); `standard()` + Javadoc (T-4, T-6) |
| `core/…/recognizer/geheimnis/GeheimnisZeichen.java` (neu, paketintern) | T-4 | 1.7 |
| `core/…/recognizer/geheimnis/Werttest.java` (neu, paketintern) | T-4 | 1.4 |
| `core/…/recognizer/geheimnis/KontextRegel.java` (neu, paketintern) | T-4, T-5, T-6 | 1.3 |
| `core/…/recognizer/geheimnis/PasswortRecognizer.java` (neu, `public final`) | T-4, T-8 | Erkenner PASSWORT |
| `core/…/recognizer/geheimnis/TokenRecognizer.java` (neu, `public final`) | T-6, T-7, T-8 | Erkenner TOKEN |
| `core/…/recognizer/geheimnis/TokenFormen.java` (neu, paketintern) | T-7 | 1.5 |
| `core/…/recognizer/geheimnis/Verbindungsregel.java` (neu, paketintern) | T-8 | 1.6 |
| `ui/…/logik/Beschriftungen.java` | T-4 | `Map.ofEntries`, zwei Einträge |

Unverändert: `Recognizer`, `Finding`, `FindingType`, `ConfidenceLevel`, `ConflictResolver`, `Pseudonymizer`,
`Reidentifier`, `PlaceholderSyntax`, alle bestehenden Erkenner und alles in `io`, `vault`, `app`.

### 2.2 Signaturskizzen

```java
package de.skillshield.core.recognizer.geheimnis;

/** Typen der Geheimnis-Funde (D-1). Einzige Stelle, an der TextScanner und Oberflaeche die Typen kennen. */
public final class GeheimnisTypen {
    public static final FindingType PASSWORT = new FindingType("PASSWORT");
    public static final FindingType TOKEN = new FindingType("TOKEN");
    private GeheimnisTypen() { }
    /** true fuer PASSWORT und TOKEN; null wird mit NullPointerException ("type") abgelehnt. */
    public static boolean istGeheimnistyp(FindingType type) { ... }
}

/** Passwoerter nach Kontextwort (D-4, D-6 bis D-9) und in Verbindungszeichenfolgen (D-12 Faelle 1 bis 3). */
public final class PasswortRecognizer implements Recognizer {
    public static final String QUELLE_KONTEXT = "passwort-kontext";
    public static final String QUELLE_VERBINDUNG = "passwort-verbindung";
    @Override public List<Finding> detect(String text) { ... }   // NullPointerException "text"
}

/** Tokens nach Kontextwort und Schema (D-5, D-11), JWT, PEM-Koerper, Praefix-Token, Kommandozeile. */
public final class TokenRecognizer implements Recognizer {
    public static final String QUELLE_KONTEXT = "token-kontext";
    public static final String QUELLE_JWT = "token-jwt";
    public static final String QUELLE_PEM = "token-pem";
    public static final String QUELLE_PRAEFIX = "token-praefix";
    @Override public List<Finding> detect(String text) { ... }
}

// paketintern
enum Art { PASSWORT, TOKEN }   // Typ, Quelle der Kontextregel, Staemme, ganze Woerter
final class KontextRegel {
    /** Kandidaten der Kontextregel fuer eine Art; ausgenommen: Startpositionen von Schluesseln, die die
     *  ODBC-Regel behandelt (aufsteigend sortiert, leer bis T-8). */
    static void finde(String text, Art art, int[] ausgenommeneSchluessel, List<Finding> ziel) { ... }
}
final class Werttest {
    enum Verbinder { STARK, SCHWACH, SCHEMA, HALB }  // K1 | K2, K3, K4, Umbruch | Bearer/Authorization | ODBC
    static boolean besteht(String text, int start, int end, Verbinder v, boolean inAnfuehrung) { ... }
}
```

`TextScanner` (Skizze): beim Einsammeln `GeheimnisTypen.istGeheimnistyp(f.type())` → Liste `geheim`; sonst wie
heute. `alt` = bisheriger Ablauf. `static List<Finding> luecken(String text, List<Finding> geheim, List<Finding> alt)`
paketintern (testbar wie `zuschneiden`); die vorhandene Bereichsstruktur `Bestand` bekommt dafür eine Variante ohne
Typfilter.

## 3. Wiederverwendung

- Produkt: `Recognizer`, `Finding`, `FindingType`, `ConfidenceLevel`, `ConflictResolver` (unverändert),
  `TextScanner.Bestand` (binäre Suche über vereinigte Bereiche), Muster der handgeschriebenen Erkenner
  (`UrlRecognizer`, `PathRecognizer`: Lauf, Endkürzung, Klammerzählung).
- Tests: `RoundtripFuzzTest` (Generatoren 1 bis 1h, `Lauf`, Schutzgarantie), `RoundtripKorpusTest`,
  `KvDifferenzTest.korpus()`, `KvKontextLueckenEigenschaftTest.differenzTexte()`, `RoundtripKorpusTest.kvTrennerFaelle()`
  (dieselben acht Bestandsmengen wie `KvnrOhneKontextFehlalarmTest.mengen()`), `GrenzlastTest` (Vorlage
  Grenzlastfälle mit Formel), `KvnrOhneKontextFehlalarmTest` (Vorlage Prüfstand mit künstlichen Erkennern und
  Gegenprobe), `KvnrOhneKontextAusnahme` (Vorlage gezählte Ausnahme), `SynthetischeKvNummern` (Vorlage zentrale
  Testklasse, KV-Nummern für AK-30), `TestdatenSynthetischTest` (Ziffernketten-Wächter, prüft auch
  `src/test/resources`), `KlartextFreiheitTest`, `ArchitekturRegelnTest`, `BeschriftungenTest`.

## 4. Neue Abhängigkeiten

Keine. Produkt nur `java.lang` und `java.util` (Positivliste von `ArchitekturRegelnTest`). Tests nutzen zusätzlich
JDK-Klassen (`java.nio.file`, `java.security.MessageDigest` für Siegel, `java.nio.charset`), wie der Bestand. Kein
Build-Plugin.

## 5. Sicherheits- und Datenschutzaspekte

- **Kein Geheimnis in Ausgaben.** `Finding.toString()` enthält den Wert schon heute nicht. Neue Klassen werfen nur
  `NullPointerException("text")`/`("type")`; keine Meldung mit Text, Wert, Länge des Werts oder Position im Wert.
  Keine Ausgabe, kein Logging (`ArchitekturRegelnTest`). `KlartextFreiheitTest` wird je Task ergänzt (AK-42).
- **Schutz geht nie zurück.** Lückenregel: Abdeckung neu = Abdeckung alt ∪ Geheimnis-Kandidaten; kein bestehender
  Fund ändert sich (AK-31 per Differenztest über alle Mengen).
- **Geheimnisse im Speicher und in der Oberfläche** wie jeder Fund: Klartext in der Fundtabelle (G-11), Rückübersetzung
  schreibt sie zurück (G-12), abgewählte Geheimnisse gehen an die KI (D-14). Ein späterer Tresor muss Geheimnisse
  gesondert behandeln (G-15). Diese Punkte sind bewusst nicht Teil des Features und werden im Abschlussbericht
  wiederholt.
- **DoS:** linear, feste Schranken, kein regulärer Ausdruck im Produkt; Grenzlast je Regel (AK-46).
- **Testdaten und Geheimnis-Scanner:** Kap. 6.2.
- Offline: kein Netzwerk-, DNS-, Datei- oder Prozesszugriff im Produkt.

## 6. Verbindliche Regeln für engineer und Prüfer

Die Regeln aus Kap. 9 des Plans `kvnr-bsnr-erkennung` gelten weiter (kein regulärer Ausdruck im Produkt, Zeilen
höchstens 120 Zeichen, keine `\uXXXX`-Escapes in neuen oder geänderten Zeilen – Sonderzeichen als `(char) 0x…`,
kein `System.out`/`System.err`, keine Mindestzahl gesenkt, kein Seed geändert, Umstellung bestehender Tests nur wie
im Task genannt, jede Umstellung mit Kommentar `Umgestellt durch geheimnisse-erkennung T-n:` und alter/neuer
Erwartung). Ergänzungen:

### 6.1 Testmeldungen

Werte aus `SynthetischeGeheimnisse` und Korpus-Texte mit Werten erscheinen nie in einer Testmeldung: kein
`assertEquals` mit `String`-Argumenten, die einen Testwert enthalten (Werte, Texte, pseudonymisierte oder
rückübersetzte Texte), sondern `assertTrue(a.equals(b), "<Fall-ID>")`. `assertEquals` auf Zahlen, Typen, Quellen,
Stufen und auf `Finding`/`List<Finding>` ist erlaubt (deren `toString` ist klartextfrei). Meldungen nennen Fall-ID,
Kategorie, Seed, Iteration, Positionen. Bestehende Tests mit `assertEquals` auf Text bleiben unberührt (sie enthalten
keine Geheimnisse). Die Ausgabe der Wächter nennt Datei und Zeile, nie den Wert (AK-50).

### 6.2 Testdaten für Geheimnisse (D-18, AK-50, AK-51)

- Zentrale Klasse `de.skillshield.core.SynthetischeGeheimnisse` (Testbaum): `P1` bis `P7` aus der Anforderung als
  Literal, `T` (Marke `Testwert`, mindestens 24 Zeichen, Buchstaben und einzelne Ziffern), Werte für 4 096/4 097
  Zeichen, Hex-, Base64- und UUID-Proben für AK-19/AK-20, `jwt()`, `pemBlock(art, zeilenende, mitEnde)`,
  `praefixToken(praefix)`. JWT, PEM-Köpfe und Präfix-Token werden zur Laufzeit aus Teilen gebaut
  (`"-----" + "BEGIN "`, `"eyJ" + …`, `"gh" + "p_" + …`); jeder Token-Wert enthält `Testwert`. Keine Ziffernfolge
  länger als vier, keine Ziffern über einzelne Trenner zu neun verbunden (gilt auch für zur Laufzeit gebaute Werte,
  geprüft in `SynthetischeGeheimnisseTest`). Kein Wert ist für einen echten Anbieter gültig: Die Marke bricht jede
  Herstellerprüfsumme (GitHub-Tokens), JWT-Teile sind kein gültiges Base64-JSON.
- **Wächter `GeheimnisLiteralWaechterTest`** (eigener Test, kein Eingriff in `TestdatenSynthetischTest`): durchsucht
  `src/test/java` und `src/test/resources` des Kerns sowie `src/test` von ui und app. Verstoß ist ein
  zusammenhängendes Java-Literal (in Ressourcen: eine Zeile), das (a) `eyJ` gefolgt von JWT-Zeichen mit zwei Punkten,
  (b) `-----BEGIN` und `PRIVATE KEY`, oder (c) ein Präfix der Liste mit so vielen Folgezeichen, dass die Länge 20
  erreicht wird, enthält. **Keine Ausnahme**, auch nicht für die zentrale Klasse und den Wächter selbst (dort stehen
  die Formen nur zerlegt, Selbsttests verketten). Meldung: Datei, Zeile, Art. Selbsttests belegen jede Art.
- **Kollision mit dem Ziffernketten-Wächter:** Hex- und Base64-Proben haben Ziffern nur einzeln zwischen Buchstaben
  (Buchstaben beenden eine Kette); UUID-Proben haben in jedem Block mindestens einen Buchstaben. Damit bleibt
  `TestdatenSynthetischTest` unverändert und ohne neue Ausnahme.
- **Korpusdateien** unter `skillshield-core/src/test/resources/de/skillshield/core/geheimnis/`: UTF-8 ohne BOM,
  Zeilen mit LF; Leser strikt UTF-8 (`CodingErrorAction.REPORT`), wandelt CR LF und CR nach dem Lesen in LF (unabhängig
  von der Checkout-Einstellung); eine Zeile je Fall, `#` beginnt Kommentar- und Kategoriezeilen
  (`# Kategorie: ZUSTAND`), Leerzeilen zählen nicht; in Korpuszeilen stehen Zeilenumbruch und Tabulator als
  zwei Zeichen `\n` bzw. `\t` und werden beim Lesen ersetzt. Ein Test (`GeheimnisKorpusWaechterTest`) prüft jede
  Datei dort: nur E-Mail-Adressen mit reservierter Endung, IPv4 nur aus den drei Dokumentationsnetzen, keine Form
  nach AK-50; Ziffernketten prüft der bestehende Wächter.
- Teilwörter von Echtdaten (Namen, Domänen der KV) nur, wo die Anforderung sie vorgibt (`kvbsp.local`,
  `kv-beispiel.example`, Ticket).

### 6.3 Eingefroren

- Ab Abnahme T-1: `TextScannerStandGeheimnis` (Kopie), `SynthetischeGeheimnisse` (nur Ergänzungen erlaubt),
  `GeheimnisLiteralWaechterTest`, `referenz-ticket.txt`, die gemessenen Ist-Werte in `GeheimnisVorabMessungTest`.
- Ab Abnahme T-2: `negativkorpus.txt`, `wortliste.txt`, `GeheimnisPositivKorpus`, beide Generatoren samt Seeds, die
  Prüflogik in `GeheimnisFehlalarmTest` und `GeheimnisPositivKorpusTest`, alle `SIEGEL_*`. Erlaubt ist danach nur:
  Kategorien in `AKTIVE_KATEGORIEN` freischalten und Ist-Zahlen eintragen. Ein Treffer im Negativkorpus ändert die
  Regel, nie den Korpus (AK-35); bleibt er bei der Regel nicht vertretbar, wird angehalten und gemeldet.
- Immer: alle eingefrorenen Kopien, Siegel, Generatoren und Seeds der Vorgänger.

### 6.4 Umstellregel für Bestandstests (AK-32)

Nur die in Kap. 8.1 genannten Tests werden umgestellt. Schlägt ein anderer bestehender Test an, prüft der engineer
mit `TextScannerStandGeheimnis`: Ist der einzige Unterschied ein **zusätzlicher** PASSWORT-/TOKEN-Fund nach der
Lückenregel, wird die Erwartung gezielt umgestellt (konkrete neue Erwartung oder gezählte Ausnahme
`GeheimnisAusnahme` mit exakter Zahl je Menge, Muster `KvnrOhneKontextAusnahme`) und im Bericht einzeln genannt. Jeder
andere Unterschied ist ein Befund: anhalten, melden.

## 7. Messung alt gegen neu, Prüfstand

### 7.1 Vorhanden und genutzt

Bestandsmengen (Fuzz 1 bis 1h, Korpus, Korpus Teil 2, KVNR-Korpus, Generator-Mengen), `GrenzlastTest`-Muster,
gezählte Ausnahme, künstliche Erkenner als Gegenprobe. Es gibt **keine** eingefrorene Kopie des `TextScanner`; die
vorhandenen Kopien betreffen nur den KV-Scanner.

### 7.2 Neu

- **`TextScannerStandGeheimnis`** (T-1, Testbaum, Paket `de.skillshield.core.recognizer`): wörtliche Kopie von
  `TextScanner` (Stand T-1) ohne `standard()`; statt dessen `ALT` mit denselben acht Produkt-Erkennern in derselben
  Reihenfolge. Die acht Erkenner ändern sich in diesem Feature nicht (Nachweis über
  `kern-pruefsummen-vorher.txt`/`-nachher.txt`); damit ist `ALT` genau der Scanner vor dem Feature.
- **`GeheimnisDifferenzTest`** (T-1, fortgeschrieben bis T-9): über alle Bestandsmengen, Negativ- und Positivkorpus,
  Generatoren und (ab T-9) den Mischgenerator: (1) jeder Fund von `ALT` steht feldgleich in `standard()`; (2) jeder
  weitere Fund hat Typ PASSWORT oder TOKEN und überlappt keinen Fund von `ALT`; (3) Abdeckung neu = alt ∪
  Geheimnis-Kandidaten. Zahl der zusätzlichen Funde je Menge exakt festgehalten (T-1: alle 0). Zulässige Ausnahmen:
  keine. Der Bericht nennt jeden zusätzlichen Fund in Bestandsmengen einzeln (Menge, Index, Bereich, Typ, Quelle).
- **`GeheimnisMischGenerator`** (T-9): 100 000 Texte (fester Seed) aus Bestandsbausteinen (Pfade aller Formen,
  URLs mit und ohne Zugangsdaten, E-Mail-Adressen, IPv4, KV-Nummern aus `SynthetischeKvNummern`) und
  Geheimnis-Kontexten direkt davor, dahinter, darin und teilweise überlappend.
- **Prüfstand T-2:** Kap. 9, T-2.

## 8. Bestandstests, die sich ändern

### 8.1 Zwangsläufig (Erkennerliste festgeschrieben)

| Test | T-4 | T-6 |
|------|-----|-----|
| `TextScannerTest.ak22_abgleichErkennerlisteStandardEnthaeltAlleErkennerInFesterReihenfolge` | Liste + `PasswortRecognizer` | + `TokenRecognizer` |
| `TextScannerKvnrTest.ak21_standardEnthaeltDenKvnrErkennerAnLetzterStelleIndexSechs` | Zahl 8 → 9, Liste | 9 → 10, Liste |
| `BsnrKlartextFreiheitTest.abgleich_bsnrErkennerSteht_alsLetzterInDerStandardliste` | Zahl 8 → 9 (Index 7 bleibt BSNR) | 9 → 10 |

Namen bleiben, Kommentar `Umgestellt durch geheimnisse-erkennung T-n` (Vorbild Nachtrag T-14). Nur Ergänzungen
(keine geänderte Erwartung): `BeschriftungenTest` (zwei Zeilen), `KlartextFreiheitTest` (neue Fälle).

Geprüft und **unverändert**: `TextScannerKvnrTest.nachrangig_…` (neue Erkenner sind nicht nachrangig, Zahl bleibt
2), `KvnrKlartextFreiheitTest.abgleich_erkennerlisten…` (Indizes 4, 5, 6 bleiben), `RoundtripFuzzTest`
(`ERKENNER_LANR/IK/KVNR/BSNR` = 4, 5, 6, 7 bleiben; die Schutzgarantie über alle Kandidaten gilt dank ungekürzter
Reststücke), `GrenzlastTest`, `BsnrScannerKollisionenTest` (Reihenfolge-Unabhängigkeit gilt), alle Tests in ui und
app (keiner kennt Erkennerzahl oder Typliste; `KernTestJarTest` prüft nur KVNR).

### 8.2 Möglich, gemessen ab T-4

Tests, die `standard()` mit festen Erwartungen über Bestandsmengen vergleichen: `KvnrOhneKontextFehlalarmTest`,
`KvnrScannerBestandTest`, `BsnrScannerBestandTest`, `KvnrFuzzBestandTest`, `BsnrFuzzBestandTest`,
`KvnrOhneKontextBestandTest`, `KvnrOhneKontextFuzzBestandTest`, `KvnrBestandVorrangEigenschaftTest`,
`KvStandTrennerDifferenzTest`, `KvStandLueckenDifferenzTest`, `KvVorgaengerDifferenzTest`, `KvDifferenzTest`,
`RoundtripKorpusTest`, `RoundtripFuzzTest`. Durch Lesen: Kein Bestandstext enthält ein Kontextwort aus D-4/D-5 mit
Verbinder, einen Oracle-Anker, eine Option `--password…`, einen Präfix, `eyJ` oder `-----BEGIN` (Suche über alle
Testquellen; nur `$(pwd)` und `Benutzer/PW` in Pfad-Erkennertests, die ohne Scanner laufen). Erwartet: 0
Umstellungen. Zufallstexte können dennoch treffen; `GeheimnisDifferenzTest` zählt das je Menge, dann Umstellregel
6.4.

## 9. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün (alle Module). Jeder Task durchläuft engineer →
reviewer + security → qa.

### T-1: Messung des heutigen Stands, Einfrieren, Testdaten-Grundlage (keine Produktänderung)

- **Ziel:** Stand vor dem Feature messbar machen; vermutete Ist-Werte durch Messung ersetzen; Testdaten-Regeln für
  Geheimnisse durchsetzen, bevor ein Test einen Wert enthält.
- **Dateien:** `docs/features/geheimnisse-erkennung/kern-pruefsummen-vorher.txt` (SHA-256 aller Dateien unter
  `skillshield-core/src`, **vor** jeder Änderung; Format wie `oberflaeche-mvp`); Test:
  `recognizer/TextScannerStandGeheimnis.java`, `SynthetischeGeheimnisse.java`, `SynthetischeGeheimnisseTest.java`,
  `GeheimnisLiteralWaechterTest.java`, `GeheimnisKorpusWaechterTest.java`, `GeheimnisDifferenzTest.java`,
  `GeheimnisVorabMessungTest.java`, `ReferenzTicketTest.java`; Ressource
  `src/test/resources/de/skillshield/core/geheimnis/referenz-ticket.txt`.
- **Messung (`GeheimnisVorabMessungTest`, gegen `ALT`, als Ist-Tabelle mit Typ, Bereich, Konfidenz, Quelle):**
  Referenz-Ticket; `Passwort: /etc/shadow`, `Passwort: C:\Tresor\pw.kdbx`, `Passwortdatei: /etc/x`,
  `Passwort: https://tresor.kvbsp.local/eintrag`; `Passwort: <Name>@<Domäne>.example`, `C:\x\pw=<P1>\y`,
  `pw=<P1>@example.invalid`; alle Beispiele aus AK-25 und AK-26 (mit und ohne Punkt im Ziel, ohne Anker,
  `jdbc:oracle:thin:@dbsrv-ora01.kvbsp.local:1521/ABRPROD`); AK-28 (drei URLs); AK-24 (ODBC-Zeilen); AK-27
  (`--password=…`, `--password-file /etc/x`); AK-30 (KV-Nummer + Geheimnis); AK-20/AK-21 (Hex, Base64, Fingerprint,
  JWT allein und in URL); ein PEM-Körper, dessen Zeile mit `/` beginnt; AK-1-Zeile.
- **Was bei Abweichung von der Vermutung geschieht:**
  1. Deckt heute ein Fund eine Position von `Sommer2026!` im Ticket oder in der AK-1-Zeile ab: **anhalten** und dem
     Nutzer melden (AK-1/AK-48 b wären ohne Bestandsänderung unerreichbar).
  2. Liefert das Ticket andere als die sechs vermuteten Funde: die gemessene Liste gilt als „heute“ (AK-48 a), wird
     festgehalten und im Bericht neben die Vermutung gestellt; weiter. Ist `Version 4.2`, `#48213`, `CHG0012345`,
     `Export` oder `Dienst neu gestartet` heute schon Fund, bleibt das so (Bestand geht vor); Meldung als Abweichung
     zu AK-48.
  3. Liegt bei einem Positivbeispiel (AK-2 bis AK-30) heute ein Fund auf der Wertposition, wird die Erwartung dieses
     Beispiels in den späteren Tests „bestehender Fund unverändert, Reststücke nach Lückenregel“; der Bericht nennt
     jedes einzeln. Trifft das alle Beispiele eines AK (z. B. alle Oracle-Formen sind heute PFAD), meldet die
     Hauptsitzung das dem Nutzer vor T-8.
  4. G-6/AK-26: der gemessene Fund wird als `bekannteGrenze…` festgehalten, welcher Typ auch immer; ist dort heute
     kein Fund, liefert die Oracle-Regel in T-8 einen PASSWORT-Fund (besser als erwartet).
- **Referenz-Ticket:** Der Wortlaut des Nutzer-Tickets liegt **nicht** im Repository (nur Bruchstücke in der Übersicht,
  Abschnitt 6). Vorrang hat der Originaltext vom Nutzer mit den zwei Ersetzungen aus D-17. Ohne ihn setzt der engineer
  das Ticket aus allen Bausteinen der Tabelle in Übersicht Abschnitt 6 und den „muss stehen bleiben“-Wörtern
  zusammen (Reihenfolge der Tabelle, Verbindungstext knapp, Rufnummer erfunden mit Vorwahl `0000` und Blöcken 4-3-4),
  kennzeichnet die Datei in einer Kommentarzeile als rekonstruiert und meldet das. `ReferenzTicketTest` (T-1-Teil):
  AK-49-Prüfungen und die gemessene Liste „heute“.
- **AK:** AK-31 (Grundlage), AK-48 (Liste heute), AK-49, AK-50, AK-51; Vorbereitung AK-26, AK-28; OF-2.
- **Tests:** wie genannt; `GeheimnisDifferenzTest` verlangt volle Gleichheit `standard()` == `ALT` auf allen
  Bestandsmengen; Selbsttests der Wächter; alle bestehenden Tests unverändert grün.
- **Abnahmehinweis:** qa prüft per Diff die Zeichengleichheit der Kopie mit `TextScanner` (außer `standard()`/`ALT`),
  stellt die gemessenen Werte neben die Vermutungen der Anforderung (Liste „heute“, G-6, Oracle, URL, ODBC, PEM mit
  `/`-Zeile) und nennt, welche Regel aus „bei Abweichung“ gegriffen hat.

### T-2: Prüfstand Fehlalarm und Positiv, eingefroren vor der Logik (keine Produktänderung)

- **Ziel:** Die Messlatte steht, bevor eine Regel existiert.
- **Dateien:** Ressourcen `geheimnis/negativkorpus.txt` (mindestens 300 Sätze, acht Kategorien je mindestens 20:
  Zustand, Aufforderung, Verweis, Richtlinie, Zahl und Dauer, englisch, Token-Sätze, Konfiguration mit Variable oder
  Maske; enthält wörtlich alle Sätze aus AK-33 und AK-34; zusätzlich Tabellenzeilen, XML/JSON-Zeilen, Versionen,
  Ticket-/Change-Nummern, Datumsangaben und Zeitstempel, Hashes, UUIDs, Base64, Pfade und URLs mit Geheimnis-Wörtern),
  `geheimnis/wortliste.txt` (mindestens 200 deutsche und englische Wörter, nur Buchstaben); Test:
  `GeheimnisNegativGenerator.java` (AK-36), `GeheimnisPositivGenerator.java` (AK-38; Vorbedingung je Wert: `ALT`
  liefert dort keinen Fund, sonst verworfen und gezählt), `GeheimnisPositivKorpus.java` (mindestens 100 Fälle mit
  Fall-ID, Kategorie, Text aus `SynthetischeGeheimnisse` und erwarteten Funden; Kategorien `K1`, `ZWISCHENTEIL`,
  `UMBRUCH`, `ANFUEHRUNG`, `K2`, `K3`, `K4`, `TOKEN_KONTEXT`, `SCHEMA`, `JWT`, `PEM`, `PRAEFIX`, `ODBC`, `ORACLE`,
  `KOMMANDOZEILE`, `MEHRFACH`), `GeheimnisFehlalarmTest.java`, `GeheimnisPositivKorpusTest.java`.
- **Prüflogik:** Negativ: Korpus und 100 000 Generator-Sätze (fester Seed; Kontextwort jeder Form aus D-4/D-5 ×
  jeder Verbinder aus D-6 × Wort/Ziffernfolge unter neun Stellen/Datum/Version/Ticketnummer mit mindestens drei
  Ziffern/Zahlwort) liefern 0 PASSWORT/TOKEN mit `standard()` und mit jedem Erkenner, dessen Klasse im Paket
  `…geheimnis` liegt (heute keiner). Positiv: Für jede Kategorie in `AKTIVE_KATEGORIEN` (in T-2 leer) liefert
  `standard()` genau die erwarteten Funde (Typ, Bereich, Konfidenz, Stufe, Quelle); Generator 2 × 100 000 (Treffer
  100 % bzw. 0). Strukturprüfungen schon in T-2: jede Erwartung passt zum Text, jedes Kontextwort und jeder Verbinder
  kommt vor, Mindestzahlen je Kategorie, Vorbedingung „kein `ALT`-Fund auf der Wertposition“ (sonst ist der Fall als
  Konfliktfall nach T-1 Regel 3 markiert). Gegenproben: ein künstlicher Erkenner „erstes Wort hinter Kontextwort und
  `:`/`=` ohne Wert-Test“ trifft im Negativkorpus mindestens die Hälfte der Sätze mit Verbinder (Korpus prüft
  wirklich); ein künstlicher Erkenner, der genau die Erwartungen liefert, besteht den Positivtest, einer mit
  verschobenem Bereich oder anderer Quelle fällt durch. `SIEGEL_*` (SHA-256, acht Hexziffern) über beide Dateien,
  den Positivkorpus und die ersten 1 000 Generatortexte.
- **AK:** AK-33 bis AK-38 (Prüfstand), AK-51.
- **Tests:** wie genannt; Laufzeit je Klasse unter 60 s.
- **Abnahmehinweis:** qa zählt Sätze je Kategorie, prüft die wörtliche Aufnahme von AK-33/AK-34 und nennt die Siegel.
  Ab hier gilt 6.3.

### T-3: Lückenregel im `TextScanner`

- **Ziel:** Einordnung der Geheimnis-Typen nach 1.1, bevor es Geheimnis-Erkenner gibt.
- **Dateien:** `main/…/recognizer/geheimnis/GeheimnisTypen.java`, `…/geheimnis/package-info.java`,
  `main/…/recognizer/TextScanner.java` (Ablauf und Javadoc: Lückenregel, Beweis der Eigenschaften a bis d; `standard()`
  unverändert); Test `recognizer/TextScannerLueckenTest.java` (künstliche Erkenner mit Typ PASSWORT/TOKEN:
  angrenzend, ganz innen, ganz umfassend, Teilüberlappung links/rechts, mehrere Bestandsfunde im Kandidaten,
  KV- und zusammengelegter Fund als Bestand, nachrangiger Erkenner, Kandidaten untereinander überlappend, leere
  Liste; Eigenschaftstest mit festem Seed: `alt` ⊆ Ergebnis feldgleich, Abdeckung = alt ∪ Kandidaten,
  Reihenfolge-Unabhängigkeit, `ALT` == Produkt ohne Geheimnis-Kandidaten), `KlartextFreiheitTest` (Ergänzung
  `GeheimnisTypen`), `GeheimnisDifferenzTest` (weiter volle Gleichheit).
- **AK:** AK-29 (Mechanik), AK-31 (Mechanik), AK-45 (Determinismus der Einordnung); D-13, OF-7.
- **Tests:** wie genannt; alle bestehenden unverändert grün.
- **Abnahmehinweis:** security prüft, dass kein Kandidatenzeichen ohne Abdeckung bleibt und kein Fund von `alt`
  verändert wird; reviewer, dass der Ablauf ohne Geheimnis-Kandidaten zeichengleich zum alten ist.

### T-4: Passwort nach Kontextwort mit `:`/`=` – Kern, Einbindung, Oberfläche, Referenz-Ticket

- **Ziel:** Das Referenz-Ticket ist abgenommen. Kontextwörter D-4, Zwischenteil, K1 mit Anführungszeichen vor dem
  Verbinder, Zeilenumbruch nach K1, Wertgrenzen D-7, Wert-Test W1 bis W6 vollständig, Konfidenz D-9 für K1.
- **Dateien:** `main/…/geheimnis/GeheimnisZeichen.java`, `Werttest.java`, `KontextRegel.java` (nur K1 und Umbruch),
  `PasswortRecognizer.java` (nur Quelle `passwort-kontext`); `TextScanner.standard()` mit `PasswortRecognizer` an
  Index 8 samt Javadoc; `ui/…/logik/Beschriftungen.java`; Tests `geheimnis/WerttestTest.java` (paketintern: jede
  Bedingung W1 bis W6, jede Fachbegriff-, Versions-, Ticket-, Zahlwort- und Zeitstempelform, Grenzlängen),
  `geheimnis/GeheimnisZeichenTest.java` (OF-10: Umlaute, `ß`, `İ`/`ı`, NBSP, U+2009, Surrogate, Zeilenumbrüche),
  `PasswortKontextTest.java` (AK-1 bis AK-3, AK-4/AK-5 für K1, AK-6 ohne K2-Teil, AK-7 bis AK-16 für K1, AK-28, AK-29,
  AK-30 Passwort-Teil, AK-13), `PasswortGrenzenTest.java` (`bekannteGrenze…` G-1, G-2, G-7, G-8, G-9, G-10, G-13,
  G-18, G-19), `ReferenzTicketTest` (AK-48 b: genau ein neuer Fund über `Sommer2026!`, 0,95, sicher, `passwort-kontext`;
  alle Funde „heute“ feldgleich; pseudonymisiert ohne `Sommer2026!`, mit `[[PASSWORT_1]]`, `Version 4.2`, `#48213`,
  `CHG0012345`, `Export`, `Dienst neu gestartet` stehen; Rückübersetzung zeichengleich), `GeheimnisGrenzlastTest.java`
  (AK-46: `Passwort: `, `Passwort: <P1> `, `Passwort für a b c d `, `Passwort: "`, `Passwort:` LF, ein Wort von einer
  Million Zeichen auf `passwort` + `: `, zusätzlich `pw:` ohne Leerraum wiederholt), Umstellungen 8.1 (Zahl 9),
  `BeschriftungenTest` (zwei Zeilen), `KlartextFreiheitTest` (neue Klassen, Marker-Geheimnis), `GeheimnisDifferenzTest`
  (Zahlen je Menge), `GeheimnisPositivKorpusTest` (`AKTIVE_KATEGORIEN` += `K1`, `ZWISCHENTEIL`, `UMBRUCH`,
  `ANFUEHRUNG`, `MEHRFACH` (Passwort-Fälle)), Positivgenerator aktiv.
- **AK:** AK-1, AK-2, AK-3, AK-4 (K1), AK-5 (K1), AK-6 (K1), AK-7 bis AK-15, AK-16 (K1), AK-28, AK-29, AK-30
  (Passwort), AK-33 bis AK-36 (wirksam für PASSWORT), AK-37/AK-38 (Kategorien K1), AK-42, AK-44, AK-46 (Teil),
  AK-48, AK-52 (Teil).
- **Abnahmehinweis:** qa nennt Prüfstand-Ergebnis (0 Treffer im Negativkorpus und Generator), Zahl der
  zusätzlichen Funde je Bestandsmenge, Laufzeiten, und bestätigt in `ui`/`app` nur `Beschriftungen` samt Test geändert.

### T-5: Schwächere Verbinder K2, K3, K4 (OF-4)

- **Ziel:** `ist`/`lautet`/`is`, Tabellenzelle (Tab, `|`), XML-Element; jeweils mit W6 und 0,80.
- **Dateien:** `KontextRegel.java`; Tests `PasswortKontextTest` (AK-4, AK-5, AK-6 `Passwort ist` LF, AK-16
  vollständig, AK-3 `pw ist`/`pwd ist`), `PasswortGrenzenTest` (G-14), `GeheimnisGrenzlastTest` (`Passwort ist `
  wiederholt, `| Passwort | ` wiederholt, `<password>` wiederholt), `GeheimnisPositivKorpusTest` (`K2`, `K3`, `K4`).
- **Entscheidungspunkt OF-4:** Trifft der eingefrorene Prüfstand mit K3 oder K4 und lässt sich das nicht durch eine
  engere Form (z. B. K3 nur, wenn die Zeile mit `|` beginnt) beheben, streicht der engineer den Verbinder **nicht**
  selbst, sondern hält an; die Hauptsitzung legt dem Nutzer Streichen (Beispiele aus AK-4 werden Grenze, Rückmeldung
  an den product-owner) oder Engerfassen vor.
- **AK:** AK-3 (K2-Teil), AK-4, AK-5, AK-6, AK-16, AK-33 bis AK-37 (K2 bis K4), AK-46 (Teil), AK-52 (Teil).
- **Abnahmehinweis:** qa nennt die Treffer der Gegenprobe je Verbinder und bestätigt 0 Fehlalarme.

### T-6: Tokens nach Kontextwort und Schema-Formen

- **Ziel:** Kontextwörter D-5 (mit Trennerregel und `API Key`), `Bearer`/`Authorization` mit Schema, Einbindung.
- **Dateien:** `KontextRegel.java` (Art TOKEN, Schema), `TokenRecognizer.java` (Quelle `token-kontext`),
  `TextScanner.standard()` mit `TokenRecognizer` an Index 9; Tests `TokenKontextTest.java` (AK-9 vollständig, AK-17,
  AK-18, AK-19, AK-30 Token-Teil, AK-43: Text mit je einem Fund aller acht bestehenden Typen, PASSWORT und TOKEN →
  zehn Funde; Typnamen nach `FindingType.isValidName`), `TokenGrenzenTest.java` (G-16, Bearer ohne Doppelpunkt
  0,80), Umstellungen 8.1 (Zahl 10), `GeheimnisGrenzlastTest` (eine Zeile aus einer Million Buchstaben und Ziffern
  hinter `Token: `, `Bearer ` wiederholt), `KlartextFreiheitTest`, `GeheimnisDifferenzTest`,
  `GeheimnisPositivKorpusTest` (`TOKEN_KONTEXT`, `SCHEMA`, `MEHRFACH`).
- **AK:** AK-9, AK-17 bis AK-19, AK-30, AK-33 bis AK-37 (TOKEN), AK-42, AK-43, AK-46 (Teil), AK-52 (Teil).
- **Abnahmehinweis:** wie T-4, zusätzlich Bestätigung der Reihenfolge der zehn Erkenner.

### T-7: Kontextfreie Formen JWT, PEM-Körper, Präfix-Token

- **Ziel:** D-10/D-11 ohne Kontext; keine Funde für Hashes, UUIDs, Base64 ohne Kontext.
- **Dateien:** `TokenFormen.java`, `TokenRecognizer.java`; Tests `TokenFormenTest.java` (AK-20 bis AK-23, PEM mit LF
  und CR LF, ohne END, Kopfzeilen, leerer Körper, öffentliche Arten; Pseudonymisierung BEGIN-Zeile, `[[TOKEN_1]]`,
  END-Zeile; Rückübersetzung zeichengleich), `TokenGrenzenTest` (G-17 nur Kernteil, G-18 PEM mit `/`-Zeile),
  `GeheimnisGrenzlastTest` (`-----BEGIN PRIVATE KEY-----` LF wiederholt, ein PEM-Körper von einer Million Zeichen,
  `eyJ.` wiederholt), `KlartextFreiheitTest`, `GeheimnisPositivKorpusTest` (`JWT`, `PEM`, `PRAEFIX`).
- **AK:** AK-20 bis AK-23, AK-40 (PEM), AK-46 (Teil), AK-52 (Teil).
- **Abnahmehinweis:** security prüft den Wächter AK-50 gegen die neuen Tests (keine zusammenhängenden Formen).

### T-8: Verbindungszeichenfolgen und Kommandozeile

- **Ziel:** D-12 Fälle 1 bis 3; Vorrang Verbindungsregel vor Kontextregel.
- **Dateien:** `Verbindungsregel.java`, `KontextRegel.java` (ausgenommene Schlüssel), `PasswortRecognizer.java`
  (Quelle `passwort-verbindung`), `TokenRecognizer.java` (Kommandozeile TOKEN); Tests `VerbindungTest.java` (AK-24,
  AK-25, AK-26 als `bekannteGrenze…` nach Messung aus T-1, AK-27), `PasswortGrenzenTest` (G-5, G-6),
  `GeheimnisGrenzlastTest` (`Server=a;Pwd=` wiederholt, `jdbc:oracle:thin:` wiederholt, `--password ` wiederholt,
  `sqlplus -a -b ` wiederholt), `KlartextFreiheitTest`, `GeheimnisPositivKorpusTest` (`ODBC`, `ORACLE`,
  `KOMMANDOZEILE`).
- **AK:** AK-24 bis AK-27, AK-46 (Teil), AK-52 (Teil); Rest von AK-37.
- **Abnahmehinweis:** qa stellt für jedes Oracle-Beispiel das Ergebnis neben die Messung aus T-1.

### T-9: Gesamtnachweis (nur Tests und Doku)

- **Ziel:** Bestand unverändert über große Mengen, Pseudonymisierung und Roundtrip, Vollständigkeit der Grenzen.
- **Dateien:** `GeheimnisMischGenerator.java`, `GeheimnisDifferenzTest` (Mischmenge, Endzahlen),
  `GeheimnisRoundtripTest.java` (AK-39 Strukturerhaltung byte-gleich und Platzhalter-Konsistenz, gleicher Wert als
  PASSWORT und TOKEN; AK-40 Roundtrip über den ganzen Positivkorpus; AK-41 abgewählter Fund, Antwort mit
  `[[PASSWORT_1]]`; AK-45 leer, Leerraum, `null`, Emoji im Wert, NBSP/U+2009, Determinismus), `GeheimnisGrenzlastTest`
  (Vollständigkeit der 14 Fälle aus AK-46 mit Formel, Scan + Pseudonymisierung + Rückübersetzung je unter 2 s),
  `GeheimnisJavadocTest.java` (jede Grenze G-1 bis G-19 hat einen Test `bekannteGrenze…` und steht im Javadoc;
  Tabelle „Grenze → Test“ im `package-info`), `RoundtripKorpusTest` (nur neue Fälle mit Geheimnissen),
  `docs/features/geheimnisse-erkennung/kern-pruefsummen-nachher.txt` und `geaenderte-dateien.txt`.
- **AK:** AK-31 (Abschluss), AK-32, AK-39 bis AK-41, AK-45, AK-46, AK-47, AK-51, AK-52.
- **Abnahmehinweis:** qa prüft: geänderte Produktivdateien genau die aus 2.1; außerhalb des Kerns nur
  `Beschriftungen` samt Test; `docs/features/oberflaeche-mvp/kern-pruefsummen.txt` unverändert (kein automatischer
  Test liest sie; sie ist der abgeschlossene Nachweis jenes Features); Abschlussbericht mit allen Abweichungen,
  Messungen und den Folgethemen aus Kap. 5.

### Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| 1, 2 | T-4 | 20–23 | T-7 | 39 | T-9 |
| 3 | T-4, T-5 | 24, 25, 27 | T-8 | 40 | T-7, T-9 |
| 4–6 | T-4, T-5 | 26 | T-1, T-8 | 41 | T-9 |
| 7–15 | T-4 | 28 | T-1, T-4 | 42 | T-3, T-4, T-6–T-8 |
| 16 | T-4, T-5 | 29 | T-3, T-4 | 43 | T-6 |
| 17–19 | T-6 | 30 | T-4, T-6 | 44 | T-4 |
| 9 | T-4, T-6 | 31 | T-1, T-3, T-4–T-9 | 45 | T-3, T-9 |
| 32 | alle | 33–36 | T-2, T-4–T-6 | 46 | T-4–T-9 |
| 37 | T-2, T-4–T-8 | 38 | T-2, T-4 | 47 | T-9 |
| 48 | T-1, T-4 | 49–50 | T-1 | 51 | T-1, T-2, T-9 |
| 52 | T-4–T-9 | | | | |

## 10. Antworten auf OF-1 bis OF-10

1. **OF-1:** Zwei öffentliche Erkenner mit gemeinsamen paketinternen Bausteinen (1.2); Listen als geschlossene
   Konstanten; Wortkörper vorwärts, Stammvergleich rückwärts höchstens 10 Zeichen (TOKEN plus höchstens drei Trenner);
   Zeichenmischung in einem Durchlauf mit Zählern.
2. **OF-2:** Messung in T-1 gegen die eingefrorene Kopie, mit festen Regeln bei Abweichung (Anhalten nur, wenn
   `Sommer2026!` heute schon Fund ist).
3. **OF-3:** Feste Schranken je Treffer, Weitersuche hinter dem gelesenen Wert, Blöcke/Läufe einmal gelesen; Beleg im
   Javadoc, Grenzlast je Regel ab T-4.
4. **OF-4:** K3 und K4 bleiben, mit W6 und 0,80; Entscheidungspunkt in T-5 bei Prüfstand-Treffern (Nutzer entscheidet).
5. **OF-5:** PEM-Fund ist der Körper ohne die äußeren Zeilenumbrüche; `Pseudonymizer`/`Reidentifier` unverändert
   (mehrzeilige Funde gibt es schon); Fundtabelle zeigt eine gekürzte Zelle, G-17 bleibt.
6. **OF-6:** Feature 3; durch die Lückenregel bleibt der heutige Fund unverändert und das Passwort geschützt; gemessen
   in T-1, Grenze in T-8.
7. **OF-7:** Lückenregel im `TextScanner` statt Vereinigung im `ConflictResolver`: kein Bestandsfund ändert sich,
   keine Position wird frei.
8. **OF-8:** Eigener Test `GeheimnisLiteralWaechterTest`, keine Änderung an `TestdatenSynthetischTest`; Ticket als
   `src/test/resources/de/skillshield/core/geheimnis/referenz-ticket.txt`, AK-49 durch `ReferenzTicketTest` und
   `GeheimnisKorpusWaechterTest`, Ziffernketten durch den bestehenden Wächter.
9. **OF-9:** `Map.ofEntries` mit elf Einträgen über `GeheimnisTypen`; sonst nichts in `ui`/`app`.
10. **OF-10:** 1.7; Vergleich nur ASCII ohne Locale; Umlaute und `ß` sind Buchstaben (klein), U+00A0/U+2009 Leerraum.

## 11. Abweichungen von der Anforderung und Präzisierungen (zur Freigabe)

1. **Lückenregel statt `ConflictResolver`-Regel (D-13, OF-7, AK-26, AK-31).** Geheimnis-Kandidaten füllen nur Lücken.
   Folgen: (a) kein bestehender Fund ändert sich, auch nicht im G-6-Fall – die in AK-31 zugelassene Ausnahme wird
   nicht gebraucht; (b) liegt ein Bestandsfund ganz in einem Geheimnis (PEM-Zeile, die mit `/` beginnt und als
   Unix-Pfad erkannt wird), zerfällt der Geheimnis-Fund in Stücke um den Bestandsfund (neue Grenze **G-18**; AK-22
   „genau ein Fund“ gilt für Körper ohne solche Zeilen). Alternative zur Wahl: PEM-Körper dürfen Bestandsfunde, die
   ganz in ihnen liegen, verschlucken (ein PEM-Fund, aber AK-31 bekäme eine neue Ausnahmeklasse).
2. **`TextScanner.scan` ändert sich** (D-16 nennt `TextScanner` unter „genutzt, nicht geändert“). Schnittstellen und
   Signaturen bleiben; nur der Ablauf bekommt die Lückenregel. Ohne das ist AK-31 nicht erfüllbar.
3. **W6 „Sonderzeichen“** ist dieselbe Liste wie in W2 (`-`, `_`, `.`, `,` zählen nicht).
4. **W3 (e) ISO-Zeitstempel** zusätzlich ausgeschlossen (`Token: 2026-03-12T10:15:00Z` wäre sonst ein Fund).
   Entschieden in Richtung Leitlinie; ein Passwort in Zeitstempel-Form bleibt sichtbar (Grenze, Test).
5. **`Bearer <Wert>` ohne Doppelpunkt: 0,80**; Schema-Formen prüfen W1 bis W5 und Länge 16, ohne die Klassenregel
   von W6.
6. **K4** nur für Werte ohne Leerraum bis 256 Zeichen; mit Leerraum kein Fund (Erweiterung von G-13).
7. **Weitersuche hinter jedem ausgewerteten Wert** (Linearität). Neue Grenze **G-19**: Ein Kontextwort innerhalb eines
   verworfenen Werts ohne Leerraum wird nicht mehr ausgewertet (`token:${T},password:<P1>` ohne Leerzeichen und ohne
   ODBC-Form).
8. **Quelle bei Gleichstand:** `--password=<P1>` und `--client-secret=<T>` liefern Kontext- und Verbindungsregel
   denselben Bereich; es bleibt die Verbindungsregel (`passwort-verbindung` bzw. `token-kontext`). ODBC-Schlüssel
   werden nur von der ODBC-Regel ausgewertet.
9. **Reststücke** nach der Lückenregel behalten Quelle und Konfidenz des Kandidaten (keine neuen Quellnamen) und
   werden nicht gekürzt.
10. **AK-31 „über alle bestehenden Tests“** wird umgesetzt als: alle Bestandsmengen, beide Korpora, Generatoren,
    Mischgenerator; die übrigen Bestandstests sichern AK-32.
11. **Typkonstanten** stehen in `GeheimnisTypen`, nicht in `FindingType` (Abgrenzung der Anforderung).
12. **Referenz-Ticket:** Original fehlt im Repository (T-1).

## 12. Bekannte Grenzen nach diesem Feature

G-1 bis G-17 der Anforderung, dazu G-18 (Zerstückelung eines Geheimnisses durch einen ganz darin liegenden
Bestandsfund), G-19 (keine Auswertung von Kontextwörtern innerhalb eines verworfenen Werts), ISO-Zeitstempel als Wert
(Kap. 11 Punkt 4) und Werte mit Leerraum in K4. Je ein Test `bekannteGrenze…`.

## 13. Risiken

1. **Ticket-Wortlaut fehlt** (T-1): Eine Rekonstruktion kann vom echten Ticket abweichen; AK-48 belegt dann nur die
   Rekonstruktion.
2. **Fehlalarme in echten Tickets** über den Korpus hinaus, vor allem K2/K3 (G-14) und die ODBC-Regel mit halbem
   Wert-Test (`Pwd=siehe Tresor;` ist ein Fund, so D-12). Gegenmittel: Prüfstand vor der Logik, Entscheidungspunkt T-5.
3. **Zusammenspiel mit Feature 0.** Kommt `datum-und-kontextvorrang` Teil B nach diesem Feature, ändert es
   `TextScanner` erneut und muss die Lückenregel erhalten; seine Differenztests vergleichen `standard()` mit Scannern
   ohne Geheimnis-Erkenner und müssen PASSWORT/TOKEN ausnehmen. Kommt es vorher, nimmt T-1 dessen Stand in die Kopie
   auf. Hinweis an den Plan von Feature 0 nötig.
4. **Zufallstexte der Bestandsmengen** können zusätzliche Funde ergeben (erwartet 0); dann Umstellregel 6.4 und ein
   weiterer Engineer-Durchlauf.
5. **PEM-Zerstückelung** (G-18) bei echten Schlüsseln mit Zeilen, die wie Pfade beginnen: geschützt, aber unschön.
6. **Laufzeit:** zwei weitere Erkenner und eine zusätzliche Auflösung; Testlaufzeit steigt um voraussichtlich 1 bis 2
   Minuten (mehrere 100 000er-Läufe, Budget je Klasse 60 s).
7. **Geheimnis-Scanner beim Push:** durch Zusammensetzen zur Laufzeit und die Marke `Testwert` sehr unwahrscheinlich;
   `anforderung.md` und dieses Dokument enthalten nur Formbeschreibungen, keine vollständigen Formen.
8. **Restrisiko D-14:** Rückübersetzung und Abwählen geben Geheimnisse wieder frei; Folgethemen.

## 14. Abweichungen von Plan.md

Plan.md Kap. 6 nennt „Secrets/Entropie“: erkannt wird ohne Entropiebewertung, mit Kontextwörtern und wenigen
eindeutigen Formen (schon in der Anforderung begründet, D-10). Sonst keine; der Nachtrag vom 24.11.2026 (Priorität
Geheimnisse, Leitlinie Fehlalarme, keine Fremdbibliothek) ist eingehalten.

## 15. Offene Punkte für die Freigabe

1. **Wortlaut des Referenz-Tickets** bereitstellen, oder die Rekonstruktion aus Übersicht Abschnitt 6 erlauben (T-1).
2. **Lückenregel** (Kap. 11 Punkt 1) mit Folge G-18, oder PEM-Ausnahme (PEM verschluckt innenliegende Bestandsfunde).
3. Präzisierungen Kap. 11 Punkte 3 bis 9 zur Kenntnis, besonders ISO-Zeitstempel (Punkt 4) und `Bearer` 0,80
   (Punkt 5).
4. Reihenfolge zu Feature 0 (Risiko 3): Dieses Feature zuerst ist ohne Nachteil möglich; der Plan von Feature 0 braucht
   dann einen Nachtrag.
5. `docs/features/oberflaeche-mvp/kern-pruefsummen.txt` bleibt unverändert; für dieses Feature gelten neue Dateien
   „vorher/nachher“.

## Nachtrag der Hauptsitzung (25.11.2026): Freigabe und Entscheidungen

Der Nutzer hat das Feature freigegeben. Die offenen Punkte aus Kap. 15 hat die Hauptsitzung entschieden (der Nutzer
überlässt ihr solche Detailentscheidungen):

1. **Referenz-Ticket:** Der Wortlaut des Nutzers liegt vor. Die Hauptsitzung hat ihn mit den zwei Ersetzungen aus
   D-17 (`kv-beispiel.de` → `kv-beispiel.example`, `10.20.30.47` → `192.0.2.47`) als
   `skillshield-core/src/test/resources/de/skillshield/core/geheimnis/referenz-ticket.txt` abgelegt, sonst wörtlich.
   Es wird nicht rekonstruiert; der engineer ändert den Wortlaut nicht. Beanstandet ein Wächter eine Stelle des
   Tickets (z. B. die Rufnummer), hält der engineer an und meldet es.
2. **Lückenregel** wie in Kap. 11 Punkt 1, mit der Grenze G-18. Keine PEM-Ausnahme.
3. Die Präzisierungen in Kap. 11 Punkte 3 bis 12 gelten wie beschrieben.
4. **Reihenfolge:** `datum-und-kontextvorrang` Teil A ist abgeschlossen (Tag `dk-t3-teil-a`) und liegt vor diesem
   Feature. T-1 nimmt diesen Stand in die Kopie und in `kern-pruefsummen-vorher.txt` auf. Teil B ist zurückgestellt;
   kommt er später, gilt Risiko 3 (Lückenregel erhalten, PASSWORT/TOKEN in dessen Differenztests ausnehmen).
5. Die Messhilfe `DatumFolgeMessung` wählt die Bestandserkenner nach Klasse aus, nicht nach Stelle; sie verträgt
   zusätzliche Erkenner in `standard()`.

### Entscheidung nach der Vorabmessung T-1 (25.11.2026): Passwort am Ende einer URL (AK-28, G-4)

Gemessen in T-1 und von security bestätigt: Der URL-Erkenner kürzt am Ende Satzzeichen (`!`, `.`, `)`, `,`). Endet
ein Passwort in einer URL-Abfrage (`…&password=<Wert>`) auf solche Zeichen, liegen diese Zeichen heute außerhalb des
URL-Funds und stehen im Klartext. Entscheidung in Richtung „möglichst viel schützen“: Der URL-Fund bleibt wie heute,
und die Lückenregel ergänzt die Restzeichen als PASSWORT-Reststück (auch wenn es nur ein Zeichen ist). AK-28 gilt
für diesen Fall in der Fassung „genau ein URL-Fund wie heute, dazu ein PASSWORT-Reststück über die Restzeichen des
Werts; kein Zeichen des Werts im Klartext“. Die Zeile der Tabelle in Kap. 1.1 („kein PASSWORT-Fund“) gilt nur, wenn
der URL-Fund den Wert ganz abdeckt. Dasselbe gilt für Verbindungszeichenfolgen in T-8. Die Fälle werden in T-4 bzw.
T-8 mit eigenem Test belegt und im Bericht einzeln genannt.

Der URL-Erkenner entfernt am Ende alle Zeichen aus `. , ; : ! ? ' "` sowie `)` und `]` bei Klammerüberschuss; die
Tests in T-4 und T-8 decken alle diese Endzeichen ab, nicht nur die oben genannten.

Offene Härtungen des Wächters `GeheimnisLiteralWaechterTest` (security, Schwere niedrig, nach T-1 nicht umgesetzt;
beide setzen eine absichtlich gebaute Datei voraus): Dateien mit Binärendung und Nullbyte zusätzlich als Text
prüfen; bei UTF-16 ohne BOM zusätzlich die Ein-Byte-Dekodierung prüfen. Nachzuholen in T-2, bevor Korpusdateien
dazukommen.

### Entscheidungen nach der Prüfung von T-2 (25.11.2026), gültig ab T-4

1. **Platzhalterartige Werte bleiben Funde.** `password: !vault |`, `password: @@DBPASS@@` und
   `password: REPLACE_ME_123` sind nach D-4 bis D-8 Funde und bleiben es (Richtung „möglichst viel schützen“; ein
   solcher Wert kann ein echtes schwaches Passwort sein, der Nutzer kann den Fund abwählen). Der Wert-Test wird
   dafür nicht erweitert, die Korpora bleiben unverändert. Neue Grenze **G-20** (Überschutz bei Platzhaltern mit
   Sonderzeichen oder Ziffern), in T-4 mit einem Test `bekannteGrenze…` festgehalten.
2. **W3 (c)** (Zahl mit Wortteil wie `90-tägig`): Der Kleinbuchstabenteil wird mit `Character.isLowerCase`
   geprüft, Umlaute und `ß` eingeschlossen. Kap. 1.7 („nur ASCII“) gilt für diesen Teil nicht.
3. **Generator-Treffer 100 % (AK-38):** Die Ist-Konstanten des Positiv-Generators dürfen nur 0 (Kategorie noch
   nicht aktiv) oder die volle Zahl der Texte des Typs annehmen (66 663 PASSWORT ab T-4, 33 337 TOKEN ab T-6). Der
   Test prüft das fest.
4. **K3/K4 (T-5, OF-4):** Wird ein Verbinder gestrichen, bleiben seine Fälle im Positivkorpus stehen und werden nie
   in `AKTIVE_KATEGORIEN` aufgenommen; das Streichen bekommt einen eigenen Test `bekannteGrenze…`.
5. **JWT hinter `Authorization: Bearer`:** Der erwartete Fund mit Quelle `token-jwt` ist in T-7 ausdrücklich zu
   bestätigen und zu begründen (nicht dem letzten Gleichstandskriterium überlassen).

6. **Auszeichnungszeichen hinter dem Verbinder (Markdown/Wiki), neu, gilt ab T-4.** Nach D-7 beginnt der Wert am
   ersten Zeichen hinter dem Verbinder; bei `**Passwort:** <Wert>` wäre das `**`, und ein echtes Passwort in
   fett gesetzter Dokumentation bliebe ungeschützt (Hinweis des reviewers). Entscheidung in Richtung „möglichst viel
   schützen“, weil Betriebsdokumentation der Hauptanwendungsfall ist: Zwischen Kontextwort, Verbinder und Wert
   werden die Auszeichnungszeichen `*`, `_` und der Gravis (jeweils ein bis drei in Folge, auch direkt vor dem
   Verbinder wie in `**Kennwort**: <Wert>`) übersprungen; sie gehören nicht zum Fund. Umschließt dieselbe
   Zeichenfolge den Wert (`` `<Wert>` ``, `**<Wert>**`), endet der Fund vor ihr. Der Wert-Test gilt unverändert für
   den Wert ohne Auszeichnung; `**Passwort:** siehe Tresor` bleibt kein Fund (Negativkorpus unverändert). T-4 belegt
   das mit eigenen Tests (Positiv- und Negativfälle außerhalb des eingefrorenen Korpus), misst die Wirkung am
   Negativkorpus und an beiden Generatoren (weiter 0 Fehlalarme) und nennt die Fälle im Bericht. Führt die Regel zu
   Fehlalarmen im Prüfstand, wird angehalten und gemeldet.
7. **Offene Härtung des Wächters** `GeheimnisLiteralWaechterTest` (security, niedrig): UTF-16-Text, der um ein Byte
   versetzt hinter einem BOM steht, wird nicht geprüft (nur mit absichtlich gebauter Datei erreichbar). Nicht
   umgesetzt; bei Gelegenheit zusätzlich ab Versatz 1 dekodieren.

8. **Überlappungsfreie Kandidaten je Geheimnis-Erkenner (security, T-3, niedrig), gilt ab T-4.** Die Lückenregel ist
   linear, solange jeder Geheimnis-Erkenner überlappungsfreie Kandidaten liefert; bei vielen einander überlappenden
   Kandidaten über vielen Bestandsfunden wächst die Zahl der Reststücke quadratisch (heute nur mit künstlichen
   Erkennern erreichbar). Jeder Geheimnis-Erkenner (T-4, T-6, T-7, T-8) bekommt deshalb einen Test „Kandidaten
   untereinander überlappungsfrei“ über den Prüfstand und einen Zufallslauf.

`SynthetischeGeheimnisse`: Die Anforderung definiert nur `P1`, `P2`, `P3`, `P6` und `P7`; „P1 bis P7“ in Kap. 6.2
ist ungenau.
