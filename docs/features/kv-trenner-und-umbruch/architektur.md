# Architektur: Gesperrte Kontextwörter mit Mehrfachtrennern und CR-Unsichtbar-LF

Feature-Kurzname: `kv-trenner-und-umbruch`
Grundlage: `anforderung.md` dieses Features (D-1 bis D-9, AK-1 bis AK-30), Vorgänger
`../kv-kontext-luecken/architektur.md` (Kap. 3, Kap. 4 mit Nachtrag, Kap. 8, Kap. 11 mit allen Nachträgen, Kap. 13) und
`../kv-nummern-erkennung/architektur.md` (R-1 bis R-4). Code-Stand: `KvNumberScanner` am Ende von `kv-kontext-luecken`
(Sperr-Verfolger, Vorgriff `einzelbuchstabeVoraus`, `direktNachIk`, `zeilenumbruch()`).

Schreibweise wie in der Anforderung: `N` = synthetische Nummer aus `SynthetischeKvNummern`, `KW` = Kontextwort,
`UZ` = unsichtbares Zeichen (Formatzeichen, U+FE00 bis U+FE0F), `CR`, `LF`, `<Tab>`, `NBSP`, `THIN`, `NNBSP`.
„WS4" = die vier Leerraumzeichen der Sperrschrift (Leerzeichen, NBSP, THIN, NNBSP).

---

## 1. Lösungsansatz

Beide Lücken werden im bestehenden `KvNumberScanner` geschlossen, ohne neue Produktivklasse, ohne neue Quelle, ohne
regulären Ausdruck; die Leseposition der Hauptschleife springt nie zurück.

- **Teil 1 (Grenze 15, D-1):** Ein Merker `crOffen` wird gesetzt, wenn ein einzelnes CR als Zeilenumbruch gezählt
  wurde. Er bleibt über Läufe stehen, die **nur** aus unsichtbaren Zeichen bestehen; ein LF, das bei gesetztem Merker
  kommt, ist die Fortsetzung desselben Umbruchs und wird nicht noch einmal gezählt. Jedes andere Zeichen löscht den
  Merker. Damit wirkt `CR UZ* LF` an allen Stellen genau wie `CR LF`, weil alle Folgewirkungen ohnehin in
  `zeilenumbruch()` gebündelt sind.
- **Teil 2 (Grenze 14c, D-2 bis D-7):** Neben dem bestehenden Sperr-Verfolger (unverändert, „streng") läuft ein
  zweiter, kleiner Zustandsautomat, der **Weit-Verfolger**. Er erkennt maximale Folgen von Einzelbuchstaben mit
  Lücken nach D-2 (a) bis (d). Er schreibt in **keinen** Bestandszustand, außer in genau einem Ereignis: Die Folge ist
  zu Ende, enthält mindestens eine erweiterte Lücke (b, c, d), hat mindestens vier Buchstaben und ergibt genau ein
  Kontextwort der Art. Dann wirkt sie wie ein gesperrtes Kontextwort ab drei Buchstaben des Bestands (Abbild der
  offenen Segmente, nachgestellter Kontext, Kontext mit Deckel 0,80). Alle Zwischenzustände sind wirkungsfrei; daraus
  folgt die Fehlalarm-Garantie (Kap. 3.2.6).

Verglichen wird gegen eine **zweite eingefrorene Kopie** des heutigen Scanners (Stand Ende `kv-kontext-luecken`,
Kap. 4). Gegen sie gilt: kein Fund fehlt, keine Konfidenz sinkt, keine Ausnahme; für Teil 2 zusätzlich: kein
zusätzlicher Fund auf Texten ohne erweitertes gesperrtes Kontextwort ab vier Buchstaben.

Die Tasks sind so geschnitten, dass **Teil 1 (T-1 bis T-4) vollständig abgenommen und gesichert ist, bevor Teil 2
(T-5 bis T-10) beginnt**. Der Fehlalarm-Prüfstand (AK-15, AK-16) wird in T-5 als reiner Test-Task gebaut, bevor
das Produkt für Teil 2 geändert wird, und ist Abnahmekriterium des ersten Produkt-Tasks von Teil 2 (T-6).

---

## 2. Betroffene Module, Pakete, Typen

Nur `skillshield-core`, Paket `de.skillshield.core.recognizer.kv` (Produkt) und Testbaum. Kein POM, keine neue
Abhängigkeit, keine neue Produktivklasse, kein neues JDK-Paket (`ArchitekturRegelnTest` und Positivliste unverändert).

### 2.1 Geänderte Produktivdateien

| Datei | Änderung | Task |
|-------|----------|------|
| `recognizer/kv/KvNumberScanner.java` | Teil 1: Merker `crOffen` in `durchlaufen()`; Javadoc „Zeilenumbruch", Grenze 15 | T-2 |
| `recognizer/kv/KvNumberScanner.java` | Teil 2: Weit-Verfolger (Felder, `weitSchritt`, `weitEnde`, `weitAnhaengen`, Abbild), gemeinsamer Vorgriff `vorausArt`, `istKontextwort(char[], int)`; Javadoc | T-6, T-7 |
| `recognizer/kv/KvNumberScanner.java` | nur Javadoc: bekannte Grenzen, Linearität (Abschluss) | T-10 |
| `recognizer/kv/LanrRecognizer.java`, `IkRecognizer.java` | nur Javadoc: 0,80 auch für erweiterte Trenner (ab vier Buchstaben); `I. K.` weiter ohne Fund | T-6 |

`KvArt.java` und `KvCheckDigits.java` bleiben **unverändert** (die eingefrorenen Kopien nutzen sie mit).

### 2.2 Skizze der Produktänderung Teil 1 (T-2)

```java
// Feld in Lauf
/** D-1: ein einzelnes CR wurde als Umbruch gezaehlt; bis zum naechsten Zeichen, das kein unsichtbares ist. */
private boolean crOffen;

// durchlaufen(), sinngemaess
if (istUnsichtbar(cp) || istBuchstabe(cp)) {
    i = laufMitVerfolger(i);
    if (buchstaben > 0) {        // Lauf mit Buchstabe oder Kombinationszeichen: CR ist nicht mehr offen
        crOffen = false;
    }
    continue;
}
if (crOffen && c == '\n') {      // CR UZ+ LF: Fortsetzung desselben Umbruchs (D-1)
    crOffen = false;
    i++;
    continue;
}
crOffen = false;
sperrSchritt(cp);                // ab T-6 zusaetzlich weitSchritt(cp)
...
if (istZeilenumbruch(cp)) {
    i += laenge;
    if (cp == '\r' && i < n && text.charAt(i) == '\n') {
        i++;                     // CR LF wie bisher
    } else if (cp == '\r') {
        crOffen = true;
    }
    zeilenumbruch();
}
```

### 2.3 Skizze der Produktänderung Teil 2 (T-6, T-7)

Neue Konstanten (privat, neben den bestehenden):

```java
/** D-3: erweiterte Trenner gelten erst ab vier Buchstaben. */
private static final int MIN_WEIT_BUCHSTABEN = 4;
/** D-2 (b), (c): hoechstens drei Leerraumzeichen der Sperrschrift je Luecke. */
private static final int MAX_WEIT_LEERRAUM = 3;
// Zustaende des Weit-Verfolgers
private static final int WEIT_KEINE = 0;
private static final int WEIT_BUCHSTABE = 1;   // zuletzt ein Einzelbuchstabe
private static final int WEIT_PUNKT = 2;       // danach genau ein '.'
private static final int WEIT_PUNKT_LEER = 3;  // danach '.' und 1..3 WS4 (Luecke c)
private static final int WEIT_LEER = 4;        // danach 1..3 WS4 (1: Luecke a, 2..3: Luecke b)
private static final int WEIT_STRICH = 5;      // danach genau ein '-', U+2011 oder U+2013 (Luecke a)
private static final int WEIT_TAB = 6;         // danach genau ein Tab (Luecke d)
// Ergebnis des gemeinsamen Vorgriffs
private static final int VORAUS_ANDERS = 0;
private static final int VORAUS_EINZEL = 1;
private static final int VORAUS_UNSICHTBAR = 2;
```

Neue Felder in `Lauf`:

```java
private int weitZustand = WEIT_KEINE;
private int weitLeer;                                         // 0..3, WS4 der laufenden Luecke
private boolean weitErweitert;                                // mindestens eine Luecke nach D-2 (b), (c), (d)
private final char[] weitZeichen = new char[MAX_KONTEXTWORT]; // gefaltete Buchstaben, nur die ersten 23
private int weitLaenge;                                       // zaehlt ueber 23 hinaus weiter
private int weitOffenAnzahl, weitOffenKlammern, weitOffenKomma; // Abbild der offenen Segmente
private boolean weitOffenSchraeg, weitOffenKlammerVorKomma, weitOffenUnd; // ... zu Beginn der Weit-Folge
```

Neue bzw. geänderte private Methoden: `istSperrLeerraum(int cp)` (WS4), `vorausArt(int p)` (ersetzt
`einzelbuchstabeVoraus`, liefert zusätzlich „nur unsichtbar"), `weitInLuecke()`, `weitSchritt(int cp)`,
`weitAnhaengen()`, `weitEnde()`, `weitAbbildSpeichern()`, `weitAbbildWiederherstellen()`,
`istKontextwort(char[] zeichen, int laenge)` (der Sperr-Verfolger ruft sie mit `sperrZeichen` auf), umgebautes
`laufMitVerfolger(int start)`.

### 2.4 Neue und geänderte Testdateien (Überblick, Einzelheiten in den Tasks)

Pfade unter `skillshield-core/src/test/java/de/skillshield/core/`.

| Datei | Art | Task |
|------|-----|------|
| `recognizer/kv/KvNumberScannerStandLuecken.java` | neu: eingefrorene Kopie des heutigen Scanners | T-1 |
| `recognizer/kv/KvStandLuecken.java` | neu: öffentliche Hüllen `lanr()`, `ik()` | T-1 |
| `recognizer/kv/KvTestHilfen.java` | erweitert: `Art.standLuecken`, strenge Differenz, Referenzen, Fehlalarm-Prüfung | T-1, T-3, T-5 |
| `KvStandLueckenDifferenzTest.java` | neu: Siegel, Differenz Erkenner-/Scannerebene, Fehlalarm Scannerebene | T-1, T-4, T-5, T-9 |
| `recognizer/kv/KvUmbruchTest.java` | neu: AK-1 bis AK-5, Grenze 15 Rest | T-2 |
| `recognizer/kv/KvUmbruchEigenschaftTest.java` | neu: AK-24 (b) | T-3 |
| `recognizer/kv/KvTrennerFehlalarmTest.java` | neu: AK-15, AK-16, Selbsttests | T-5 |
| `recognizer/kv/KvTrennerTest.java` | neu: AK-7 bis AK-14, AK-17 bis AK-21, Grenze 14c Rest | T-6, T-7 |
| `recognizer/kv/KvTrennerEigenschaftTest.java` | neu: AK-24 (a), (c), (d), (e) | T-8 |
| `recognizer/kv/KvSperrschriftTest.java` | umgestellt: `bekannteGrenze15_…` (T-2), `bekannteGrenze14c_…` (T-6) | T-2, T-6 |
| `recognizer/kv/KvKontextLueckenEigenschaftTest.java` | AK-6: Aussparung CR/LF entfällt (T-3); Gegenproben (d) mit `L. A. N. R.` (T-6) | T-3, T-6 |
| `RoundtripFuzzTest.java` | neu Teil 1d (T-4) und Teil 1e (T-9); 1, 1b, 1c unverändert | T-4, T-9 |
| `RoundtripKorpusTest.java` | ergänzt; zwei 14c-Gegenproben umgestellt (T-6) | T-4, T-6, T-9 |
| `GrenzlastTest.java`, `KlartextFreiheitTest.java`, `recognizer/TextScannerKvTest.java` | ergänzt | T-4, T-10 |

---

## 3. Erkennungsregeln und Ablauf im Scanner

### 3.1 Teil 1: CR + unsichtbare Zeichen + LF (D-1)

#### 3.1.1 Laufbildung (R-1) zwischen CR und LF – festgeschrieben

Ein Lauf ist die maximale Folge aus Buchstaben (inkl. Kombinationszeichen) und unsichtbaren Zeichen. CR und LF sind
weder Buchstabe noch unsichtbares Zeichen. Daraus folgt zwingend:

- Unsichtbare Zeichen **zwischen** CR und LF bilden immer einen eigenen Lauf, der nur aus unsichtbaren Zeichen besteht
  (`buchstaben == 0`, `hatBuchstabe == false`). Er hängt an keinem Buchstabenlauf; `lauf()` behandelt ihn heute schon
  als neutral (kein Füllwort, `zeilenanfang` bleibt, offene Segmente bleiben).
- Steht zwischen CR und LF ein Buchstabe oder ein Kombinationszeichen (`CR UZ A LF`, `CR UZ U+0301 LF`), hat der Lauf
  `buchstaben > 0`; der Merker wird gelöscht, es bleiben zwei Umbrüche (D-1 zählt nur unsichtbare Zeichen).
- Der Sperr-Verfolger ist beim CR schon beendet (`sperrSchritt(CR)`), der Lauf aus unsichtbaren Zeichen beginnt im
  Zustand `KEINE`: `sperrEnde()` ist wirkungslos, `abbildSpeichern()` überschreibt nur das Abbild ohne laufende Folge
  (wird vor jeder Verwendung neu gesetzt). `laufEnde`/`laufBuchstaben = 0` betreffen nur einen Punkt unmittelbar hinter
  dem Lauf; hinter dem LF steht kein solcher Punkt an dieser Position.

#### 3.1.2 Zählung

| Folge | Zählung | Mechanismus |
|------|---------|-------------|
| `CR LF` | 1 | Bestand (Paar wird zusammen gelesen) |
| `CR UZ+ LF` | **1** (neu) | CR zählt, `crOffen`; UZ-Lauf hält den Merker; LF wird übersprungen |
| `CR UZ CR`, `CR UZ U+0085`, `CR UZ U+2028` | 2 | zweites Zeichen ist kein LF: Merker gelöscht, zählt |
| `LF UZ LF`, `LF UZ CR` | 2 | Merker nur nach CR |
| `CR LF UZ LF` | 2 | `CR LF` setzt keinen Merker |
| `CR UZ LF UZ LF` | 2 | LF löscht den Merker; zweites LF zählt |
| `CR` Leerzeichen/NBSP/sichtbares Zeichen `LF` | 2 | jedes Nicht-Lauf-Zeichen löscht den Merker |
| `CR UZ LF CR UZ LF` | 2 | je eine Folge |
| `CR` + 1 000 000 UZ (ohne LF) | 1 | ein Lauf, Merker bleibt bis Textende |

#### 3.1.3 Folgewirkungen (alle über den einen Aufruf von `zeilenumbruch()`)

`zeilenumbruch()` setzt `zeilenanfang = true` (R-3 nummerierte Liste), leert die offenen Segmente (nachgestellter
Kontext, Komma-Regel D-5 des Vorgängers), zählt `umbrueche` bei `aktiv`, ruft `direktVerloren()` (beendet `direkt`,
`direktNachIk` und einen `nurDirekt`-Kontext von `I K`) und beendet bei mehr als einem Umbruch den Kontext
(Leerzeile). Das Ende einer Sperrfolge geschieht schon im `sperrSchritt(CR)` (bzw. ab T-6 `weitSchritt(CR)`). Da
das übersprungene LF nichts davon wiederholt und der UZ-Lauf neutral ist, gleicht der Zustand nach `CR UZ+ LF` dem
nach `CR LF`. Fundbereich und Text bleiben unverändert (kein Zeichen wird verändert, nur die Zählung).

Damit gelten AK-1 bis AK-5, insbesondere `I K:` CR UZ LF `N` → kein Fund (Umbruch beendet `nurDirekt`), `L A N R`
CR UZ LF `N` → 0,80 (Ereignis beim CR, ein Umbruch), `L` CR UZ LF `A N R N` → kein Fund (CR beendet die Folge).

**Fehlalarm-Abgrenzung Teil 1:** Teil 1 ändert das Verhalten nur auf Texten, in denen ein CR unmittelbar von
mindestens einem unsichtbaren Zeichen und danach von einem LF gefolgt ist (Merker wirkt nur dort). Diese Eigenschaft
nutzt der Prüfstand von Teil 2 als Filter (Kap. 4.3).

### 3.2 Teil 2: Weit-Verfolger (D-2 bis D-7)

#### 3.2.1 Heutiger Ablauf von `L. A. N. R. N` (zur Abgrenzung)

| Zeichen | Sperr-Verfolger (streng) | Bestand |
|--------|-------------------------|---------|
| `L` (Lauf) | `KEINE`: Abbild speichern, Folge `l`, → `BUCHSTABE` | `fuellwort()`: offene Segmente leeren; bei `aktiv` `woerter++` |
| `.` | `BUCHSTABE` + Sperr-Trenner → `TRENNER` | `punkt()`: `zeilenanfang = false`, leeren; Abkürzungspunkt (direkt hinter einem Lauf mit einem Buchstaben) → **kein Satzende** |
| ` ` | `TRENNER` + Nicht-Lauf → `sperrEnde()` (Länge 1, nichts) | `sonstiges()`: neutral |
| `A`, `N`, `R` | je neue Folge der Länge 1 | je ein Füllwort, je Abkürzungspunkt |
| ` ` + Ziffern | – | `segment()`: ohne `aktiv` → offenes Segment; kein Fund |

Ein Einzelbuchstabe mit Punkt ist also heute nie Satzende (Abkürzungspunkt R-4), jeder Buchstabe ist ein Füllwort,
und keine Folge erreicht zwei Buchstaben. Der Weit-Verfolger ändert an keiner dieser Zeilen etwas; er liest nur mit
und löst am Ende ein Ereignis aus.

#### 3.2.2 Begriffe

- **Einzelbuchstabe**, **Lauf**, Faltung `A–Z`: wie im Bestand (Kap. 3.2.1 des Vorgängers).
- **Weit-Lücke** zwischen zwei Einzelbuchstaben (D-2), genau eine von:
  (a) ein Sperr-Trenner des Bestands (WS4, `.`, `-`, U+2011, U+2013);
  (b) zwei oder drei WS4, beliebig gemischt;
  (c) ein `.` und danach ein bis drei WS4;
  (d) genau ein Tab.
  (b), (c), (d) heißen **erweitert**. Läufe nur aus unsichtbaren Zeichen innerhalb der Lücke sind durchsichtig
  (zählen nicht, ändern den Zustand nicht).
- **Weit-Folge:** maximale Folge von Einzelbuchstaben, je durch eine Weit-Lücke verbunden. Eine Lücke hinter dem
  letzten Buchstaben (auch eine unvollständige) gehört nicht zum Vergleich.
- **Weit-Kontextwort:** Weit-Folge mit mindestens einer erweiterten Lücke, 4 bis 23 Buchstaben, deren gefaltete
  Buchstaben genau einem Eintrag von `art.kontextwoerter()` gleichen (`lanr`, `lanrnr`, `arztnummer`, `arztnr`,
  `iknr`, `iknummer`, `institutionskennzeichen`; nie `ik`, D-4).

Eine Weit-Folge ohne erweiterte Lücke ist buchstabengleich mit der strengen Sperrfolge (Kap. 3.2.5) und wird
**nicht** ausgewertet; dafür sorgt der Bestand.

#### 3.2.3 Zustände und Übergänge

Zeichen außerhalb eines Laufs (Aufruf `weitSchritt(cp)` direkt nach `sperrSchritt(cp)`, vor der Bestandsverarbeitung):

| Zustand | WS4 | `.` | `-` U+2011 U+2013 | Tab | jedes andere Zeichen (Ziffer, Umbruch, `,` `;` `:` `/` `_` `!` …) |
|--------|-----|-----|------|-----|------|
| `KEINE` | – | – | – | – | – |
| `BUCHSTABE` | `LEER`, `weitLeer = 1` | `PUNKT` | `STRICH` | `TAB` | `weitEnde()` |
| `PUNKT` | `PUNKT_LEER`, `weitLeer = 1` | `weitEnde()` | `weitEnde()` | `weitEnde()` | `weitEnde()` |
| `PUNKT_LEER` | `weitLeer < 3`: `weitLeer++`, sonst `weitEnde()` | `weitEnde()` | `weitEnde()` | `weitEnde()` | `weitEnde()` |
| `LEER` | `weitLeer < 3`: `weitLeer++`, sonst `weitEnde()` | `weitEnde()` (`L .A`) | `weitEnde()` (`L - A`) | `weitEnde()` | `weitEnde()` |
| `STRICH`, `TAB` | `weitEnde()` | `weitEnde()` | `weitEnde()` | `weitEnde()` | `weitEnde()` |

Laufanfang (in `laufMitVerfolger`):

| Zustand | Vorgriff `vorausArt(p)` | Wirkung |
|--------|------|---------|
| Lücke (`PUNKT`, `PUNKT_LEER`, `LEER`, `STRICH`, `TAB`) | `EINZEL` | Lauf wie bisher verarbeiten, Buchstaben anhängen; `weitErweitert |= (PUNKT_LEER oder LEER mit weitLeer >= 2 oder TAB)`; → `BUCHSTABE` |
| Lücke | `UNSICHTBAR` | Lauf wie bisher verarbeiten (neutral); Zustand und `weitLeer` bleiben (durchsichtig, D-2 letzter Satz, AK-20) |
| Lücke | `ANDERS` | **vor** der Verarbeitung `weitEnde()`, dann wie `KEINE` |
| `KEINE` | – | `weitAbbildSpeichern()`; Lauf verarbeiten; ist er ein Einzelbuchstabe (`laufBuchstaben == 1`): `weitLaenge = 0`, `weitErweitert = false`, anhängen, → `BUCHSTABE` |
| `BUCHSTABE` | – | kommt nicht vor (hinter einem Lauf folgt immer ein Zeichen außerhalb eines Laufs) |

Textende: `sperrEnde()`, dann `weitEnde()`.

Übersprungenes LF aus Teil 1: kein `weitSchritt`; beide Verfolger sind dort bereits `KEINE` (beendet beim CR, der
UZ-Lauf beginnt keine Folge).

**Gemeinsamer Vorgriff:** `vorausArt(p)` liest den Lauf ab `p` höchstens bis zum zweiten Buchstaben oder bis zum
Laufende und liefert `EINZEL` (genau ein Buchstabe, `Character.isLetter`), `UNSICHTBAR` (kein Buchstabe, kein
Kombinationszeichen) oder `ANDERS`. Er wird je Laufanfang höchstens **einmal** aufgerufen, nämlich wenn
`weitInLuecke()` gilt. Invariante: Ist der strenge Verfolger im Zustand `TRENNER`, ist der Weit-Verfolger in einer
Lücke (`PUNKT`, `LEER` mit 1, `STRICH`), denn beide sahen denselben Einzelbuchstaben und genau einen Sperr-Trenner.
Der strenge Verfolger nutzt dasselbe Ergebnis (`EINZEL` → anhängen, sonst `sperrEnde()`), also genau wie heute mit
`einzelbuchstabeVoraus`.

`laufMitVerfolger` (Skizze; die Reihenfolge ist verbindlich):

```java
private int laufMitVerfolger(int start) {
    boolean luecke = weitInLuecke();
    int voraus = luecke ? vorausArt(start) : VORAUS_ANDERS;
    boolean sperrWeiter = sperrZustand == SPERR_TRENNER && voraus == VORAUS_EINZEL;
    boolean weitWeiter = luecke && voraus != VORAUS_ANDERS;
    int lueckeArt = weitZustand;             // fuer weitErweitert beim Anhaengen
    if (!sperrWeiter) {
        sperrEnde();                         // Bestand zuerst
    }
    if (!weitWeiter) {
        weitEnde();
    }
    if (!sperrWeiter) {
        abbildSpeichern();
    }
    if (!weitWeiter) {
        weitAbbildSpeichern();
    }
    int ende = lauf(start);
    // strenger Verfolger: wortgleich zum Bestand
    // Weit-Verfolger: EINZEL -> anhaengen; UNSICHTBAR -> nichts; neu: bei laufBuchstaben == 1 beginnen
    ...
    return ende;
}
```

`weitEnde()` steht zwischen `sperrEnde()` und `abbildSpeichern()`; es verändert den Bestand nur, wenn sein Ereignis
auslöst (Kap. 3.2.4), also nur in Texten mit Weit-Kontextwort.

**Puffergrenzen:** `weitZeichen` 23 Zeichen, `weitLaenge` zählt weiter (längere Folgen sind nie ein Kontextwort);
`weitLeer` höchstens 3, danach endet die Folge; Abbild sechs Werte. Kein weiterer Speicher.

#### 3.2.4 Das Ereignis in `weitEnde()`

```java
private void weitEnde() {
    int laenge = weitLaenge;
    boolean verfolgt = weitZustand != WEIT_KEINE;
    boolean erweitert = weitErweitert;
    weitZustand = WEIT_KEINE;
    weitLaenge = 0;
    weitLeer = 0;
    weitErweitert = false;
    if (!verfolgt || !erweitert || laenge < MIN_WEIT_BUCHSTABEN || laenge > MAX_KONTEXTWORT
            || !kontextLaenge[laenge] || !istKontextwort(weitZeichen, laenge)) {
        return;                              // jeder andere Fall: wirkungslos
    }
    if (aktiv) {
        // T-6: return (D-7 woertlich). T-7: Kontext erneuern, siehe 3.2.5
        return;
    }
    weitAbbildWiederherstellen();
    offenNachgestelltAusgeben();             // Komma-Regel D-5 des Vorgaengers: 0,80 bzw. 0,60
    kontextBeginnen(CONFIDENCE_SPERRSCHRIFT, false);
}
```

Konfidenz (D-6): Deckel `CONFIDENCE_SPERRSCHRIFT` = 0,80 in jedem Zustand; ungültige Prüfziffer 0,50, Länge 0,40,
nachgestellt mit Komma 0,60. `ausgeben` nimmt wie bisher das Minimum; der Deckel hebt nie an. Quelle folgt nur der
Prüfziffer/Länge (D-9); keine neue Quellenkonstante, keine interne Markierung (offene Frage 3 der Anforderung: nicht
nötig).

Fundbereich (D-9): unverändert das Segment; Kontextwort, Trenner und Satzzeichen stehen nie im Fund, weil das
Ereignis nur Zustand setzt und Segmente erst von `ziffernkette()` gebildet werden.

#### 3.2.5 Laufender Kontext und Gleichheit mit der Vergleichsform (D-5, D-7) – Regel für T-7

In der **Vergleichsform** (jede erweiterte Lücke durch ein Leerzeichen ersetzt) ist am Ende eines Kontextworts ab
vier Buchstaben **nie** ein Kontext aktiv: Jeder der mindestens vier Einzelbuchstaben ist ein Füllwort, und der
Bestand gibt Füllwörter nur bei `I K` (genau zwei Buchstaben, 14h) zurück. In der erweiterten Form kann ein Kontext
am Folgenende trotzdem aktiv sein, aber nur durch ein **strenges Teilereignis innerhalb** der Weit-Folge:

1. `L A N R.  N R N` (Weit-Folge `lanrnr`): Das strenge `lanr` endet am Leerzeichen hinter dem Punkt und beginnt
   einen Kontext (0,80, gibt dabei die offenen Segmente aus); `N`, `R` sind zwei Füllwörter.
2. `IK: I K.  N R N` (Weit-Folge `iknr`): Das strenge `I K` bei laufendem Kontext gibt zwei Füllwörter zurück (14h).

Ohne Regel bliebe dann z. B. `L A N R.  N R des der N` ohne Fund, obwohl die Vergleichsform `L A N R N R des der N`
einen Fund 0,80 hat (Verstoß gegen D-5/AK-21). **Regel (T-7):** Ist bei einem Weit-Kontextwort ein Kontext aktiv,
wird er neu begonnen: `kontextBeginnen(CONFIDENCE_SPERRSCHRIFT, false)`, **ohne** Wiederherstellen des Abbilds und
ohne Ausgabe offener Segmente.

Begründung, dass das richtig und bestandssicher ist:
- Offene Segmente gibt es in diesem Fall nicht: Bei aktivem Kontext werden Segmente ausgegeben statt geöffnet. War
  der Kontext schon zu Beginn der Weit-Folge aktiv, war das Abbild leer. Hat ihn das strenge `lanr` gestartet, begann
  dessen Folge am selben Lauf wie die Weit-Folge (das einzige Kontextwort ab vier Buchstaben, das ein anderes ab vier
  Buchstaben enthält, ist `lanrnr` ⊃ `lanr`, und zwar am Anfang); beide Abbilder wurden im selben Moment gespeichert,
  das strenge Ereignis hat die offenen Segmente also schon mit denselben Lücken-Merkern ausgegeben.
- Monoton gegenüber dem Bestand: Am Folgenende ist `direkt` im laufenden Kontext schon verloren (der erste Buchstabe
  war ein Füllwort), alle weiteren Funde dieses Kontexts hätten also höchstens 0,80. Der erneuerte Kontext gibt der
  nächsten Nummer min(direkt, 0,80) = 0,80, setzt `woerter` und `umbrueche` auf 0 und erlaubt das Nachsatzwort; er
  nimmt nichts weg. Ein `nurDirekt`-Kontext (`I K`) kann am Folgenende nicht aktiv sein (er endet am nächsten
  Füllwort, und kein Kontextwort ab vier Buchstaben endet auf `ik`).
- Die Regel betrifft nur Texte mit Weit-Kontextwort; die Fehlalarm-Garantie bleibt unberührt.
- D-7 („Sperrschrift ab vier Buchstaben wirkt nur, wenn kein Kontext (mehr) aktiv ist") ist im Sinne der
  Vergleichsform erfüllt: Dort ist der Kontext nicht mehr aktiv. Siehe Kap. 13 Hinweis 2.

Weitere Abweichungen zwischen erweiterter Form und Vergleichsform gibt es nicht: Die Zeichen der erweiterten Lücken
wirken im Bestand neutral (WS4, Tab: Leerraum; `.` direkt hinter einem Einzelbuchstaben: Abkürzungspunkt, nie
Satzende; die zusätzlichen Leerungen der offenen Segmente überschreibt das Abbild), `zeilenanfang` ist durch die
Buchstaben schon `false`, und ein strenges Teilereignis `I K` ohne laufenden Kontext endet am nächsten Buchstaben,
ehe das Weit-Ereignis alles neu setzt. Das prüft AK-24 (e) als Eigenschaft (T-8).

#### 3.2.6 Fehlalarm-Garantie (Kern von D-8)

1. Der strenge Sperr-Verfolger, `lauf()`, `punkt()`, `satzende()`, `zeilenumbruch()`, `ziffernkette()` und alle
   Bestandsmerker bleiben wortgleich; der gemeinsame Vorgriff liefert für den strengen Verfolger dasselbe wie
   `einzelbuchstabeVoraus`.
2. Der Weit-Verfolger schreibt außerhalb von `weitEnde()` nur seine eigenen Felder; `vorausArt` ändert nichts.
3. `weitEnde()` schreibt nur, wenn die maximale Weit-Folge mit mindestens einer erweiterten Lücke genau ein
   Kontextwort ab vier Buchstaben ergibt.

Daraus folgt: Auf jedem Text ohne Weit-Kontextwort (und ohne `CR UZ+ LF`, Teil 1) liefert der neue Scanner **genau**
die Fundliste der eingefrorenen Kopie. Ein zusätzlicher Fund auf einem solchen Text ist immer ein Umsetzungsfehler
oder ein Fehler dieses Plans, nie eine zulässige Folge. Der Prüfstand aus T-5 prüft genau das bei jedem Testtext.

#### 3.2.7 Grenzfälle (je ein benannter Test in T-6/T-7)

| Text | Ergebnis | Grund |
|------|----------|-------|
| `L. A. N. R. N`, `L. A. N. R.: N`, `l. a. n. r. N`, `L. A. N. R N`, `L.  A.  N.  R. N`, NBSP/THIN/NNBSP-Mischungen | 0,80 | (c) |
| `L  A  N  R N`, `L   A   N   R N`, gemischte WS4 | 0,80 | (b) |
| `L<Tab>A<Tab>N<Tab>R<Tab>N`, `L. A N  R N`, `L.A. N R. N`, `L. A-N R N` | 0,80 | (d), gemischt |
| `L A N R.  N R N` / `… des der N` | 0,80 / 0,80 (T-7) | strenges Teilereignis, Kap. 3.2.5 |
| `I. K. N`, `I  K N`, `I<Tab>K N`, `I. K.-Nr. N`, `Prof. I. K. Mustermann, Rechnung N` | kein Fund | D-4 (zwei Buchstaben) |
| `I K N`, `I.K. N`, `I-K N` | 0,50 | Bestand, unverändert |
| `A. L. A. N. R. N`, `L. A. N. R. X N`, `L. A. N. R. L. A. N. R. N`, `L. A. N. N` | kein Fund | maximale Folge |
| `L    A    N    R N`, `L<Tab><Tab>A…`, `L<Tab> A…`, `L .A…`, `L.. A..`, `L - A - …`, `L.<Tab>A…` | kein Fund | keine Weit-Lücke |
| `L. ` UZ ` A. N. R. N` (UZ zwischen Punkt-Leerzeichen und Leerzeichen) | 0,80 | UZ-Lauf durchsichtig |
| `L` UZ `A. N. R. N` | kein Fund | ein Lauf mit zwei Buchstaben (14a) |
| `N (L. A. N. R.)`, `N L  A  N  R`, `N, L. A. N. R.` | 0,80 / 0,80 / 0,60 | Abbild der offenen Segmente |
| `LANR: L. A. N. R. N` | wie `LANR: L A N R N` (0,80) | Kontext ist nach vier Füllwörtern erloschen |
| `IK: Praxis I. K. Nr N` | kein Fund | zwei Buchstaben, wie heute |
| `L. A. N. R. N.` | 0,80, Punkt nicht im Fund | D-9 |

### 3.3 Konfidenz (vollständig, ergänzt)

| Kontext | 9 Ziffern gültig | ungültig | 6–8, 10–12 |
|---------|-----------------|----------|-----------|
| Bestand (alle Zeilen aus Kap. 3.3 des Vorgängers) | unverändert | unverändert | unverändert |
| Weit-Kontextwort ab 4 Buchstaben, jeder Zustand | **0,80** | 0,50 | 0,40 |
| Weit-Kontextwort, nachgestellt mit genau einem `,`/`;` | **0,60** | 0,50 | 0,40 |
| `KW:` CR UZ+ LF `N` | wie `KW:` CR LF `N` | | |

### 3.4 Linearität (Fortschreibung des Javadoc-Belegs, AK-26)

- Teil 1: ein Merker, O(1) je Zeichen; kein Vorgriff, keine Rückkehr. `CR` + 1 000 000 UZ ist ein einziger Lauf.
- Teil 2: je Zeichen außerhalb eines Laufs O(1) (`weitSchritt`), je Lauf O(1) zusätzlich zum Bestand. Der Vorgriff
  `vorausArt` wird je Laufanfang höchstens einmal ausgeführt (gemeinsam für beide Verfolger) und liest nur diesen
  Lauf bis zum zweiten Buchstaben oder Laufende. Der Nachsatz-Vorgriff (`nachsatzFolgt`) betrifft nur den Lauf hinter
  einem Vorwort-Lauf mit mehreren Buchstaben; dort ist der Weit-Verfolger im Zustand `KEINE` (ein Lauf mit mehreren
  Buchstaben beendet jede Folge), beide Vorgriffe treffen also nie denselben Lauf. Jedes Zeichen wird höchstens
  zweimal gelesen.
- Lücken sind auf drei WS4 begrenzt; danach endet die Folge, weitere Leerzeichen kosten O(1) je Zeichen.
- Vergleich mit den Kontextwörtern einmal je Folgenende (höchstens 23 Zeichen, höchstens acht Einträge).
- Gesamt O(n), Zusatzspeicher O(1) plus Fundliste plus Folge der offenen Segmente.

---

## 4. Vergleichsbasis: zweite eingefrorene Kopie

### 4.1 Wahl und Namen

| Stand | Klasse (Testbaum, Paket `recognizer.kv`) | Hüllen | Feld in `KvTestHilfen.Art` | Prüfregel |
|------|------|------|------|------|
| vor `kv-kontext-luecken` (bestehend) | `KvNumberScannerVorgaenger` | `KvVorgaenger` | `vorgaenger` | `verletztBestandsschutz` (Ausnahme 14g) |
| **Ende `kv-kontext-luecken` (neu, T-1)** | `KvNumberScannerStandLuecken` | `KvStandLuecken` | `standLuecken` | `verletztBestandsschutzOhneAusnahme` |

Die bestehende Kopie wird **nicht** weiterverwendet: Sie kennt weder Sperrschrift noch Komma-Regel und braucht die
Ausnahme 14g; gegen sie wäre „keine Ausnahme" und „keine zusätzlichen Funde" nicht prüfbar. Sie bleibt mit allen
Siegeln unverändert bestehen und läuft weiter in `scan` mit.

`KvNumberScannerStandLuecken` ist eine wortgleiche Kopie des heutigen `KvNumberScanner` (nur Klassenname und
Kopfvermerk „eingefroren am Stand Ende kv-kontext-luecken, nicht ändern"), angelegt in T-1 **vor** jeder
Produktänderung, danach nie geändert. Keine Ziffernliterale ab neun Stellen (das Produkt hat keine).

Eine dritte Kopie (Stand nach Teil 1) wird nicht angelegt: Teil 1 ändert das Verhalten nur auf Texten mit
`CR UZ+ LF` (Kap. 3.1.3); für die Fehlalarm-Prüfung von Teil 2 werden diese Texte ausgefiltert (Kap. 4.3). Das ist
einfacher als eine weitere Kopie mit eigenem Siegel und gleich genau.

### 4.2 Siegel

Neue Klasse `KvStandLueckenDifferenzTest` (Paket `de.skillshield.core`, neben `KvVorgaengerDifferenzTest`), gleiche
Hash-Bildung wie dort (aus Start, Ende, Typname, `doubleToLongBits(Konfidenz)`, Quelle, nicht aus `hashCode()` von
Objekten), aber über `a.standLuecken`:

- `SIEGEL_LUECKEN_TEIL_1`, `SIEGEL_LUECKEN_TEIL_1B`, `SIEGEL_LUECKEN_TEIL_1C` (T-1), `SIEGEL_LUECKEN_TEIL_1D` (T-4),
  `SIEGEL_LUECKEN_TEIL_1E` (T-9); hexadezimal, je im Task gemessen.
- **Nicht** über die Generatoren der Eigenschaftstests (deren Texte ändern sich in T-3 durch AK-6 bzw. sind nicht
  durch einen Hash festgehalten).
- Bestehende Konstanten `SIEGEL_TEIL_1/1B/1C`, `HASH_TEIL_1/1B/1C` bleiben unverändert; die Generatoren der Teile
  1, 1b, 1c werden nicht berührt.
- Übergangstest `t1Uebergang_produktGleichStandLuecken` (Fuzz 1, 1b, 1c, `KvDifferenzTest.korpus()`,
  `KvKontextLueckenEigenschaftTest.differenzTexte()`: Fundlisten je Art in allen Feldern gleich); wird in T-2
  entfernt (erster Task mit Verhaltensänderung).

### 4.3 Prüfungen in `KvTestHilfen.scan` (Reihenfolge)

1. Wächter (bestehend).
2. `a.erkenner.detect(text)`, Invarianten (bestehend).
3. `pruefeGegenVorgaenger` (bestehend, Ausnahme 14g, unverändert).
4. **`pruefeGegenStandLuecken` (T-1):** jeder Fund der Kopie hat einen neuen Fund mit gleichem Start, Ende, Typ und
   Konfidenz ≥ alt; **keine Ausnahme** (`verletztBestandsschutzOhneAusnahme`).
5. **`pruefeKeineZusaetzlichenFunde` (T-5):** Gilt `!enthaeltCrUnsichtbarLf(text)` und findet
   `weitKontextwoerterReferenz(text, a)` kein Weit-Kontextwort ab vier Buchstaben mit erweiterter Lücke, dann hat
   jeder neue Fund einen Fund der Kopie mit gleichem Start, Ende und Typ (Zahl zusätzlicher Funde = 0).

`laufzeit()` prüft nicht gegen Kopien (Zeitmessung). Meldungen nennen nur Art, Textlänge und Zahlen.

Testhelfer (T-5, bewusst unabhängig vom Produkt gebaut, Laufzeit egal):
- `enthaeltCrUnsichtbarLf(String)`: gibt es ein CR, dem ein oder mehr unsichtbare Zeichen (Formatzeichen,
  U+FE00–U+FE0F) und dann ein LF folgen?
- `weitKontextwoerterReferenz(String, Art)`: entfernt zuerst alle unsichtbaren Zeichen (mit Indexabbildung), sucht
  Einzelbuchstaben (Buchstabe ohne Buchstaben/Kombinationszeichen links und rechts), nimmt als Lücke die
  nicht-leere Zeichenfolge bis zum nächsten Buchstaben und prüft sie als Zeichenkette gegen D-2 (a) bis (d); bildet so
  maximale Folgen; liefert je Folge, die mit 4 bis 23 Buchstaben einem Wort der Art gleicht, Start, Ende und das Flag
  „erweitert". Eigene Wortlisten (wie `SPERR_WOERTER_*`).
- `zusaetzlicheFunde(alt, neu)` (Zahl), mit Selbsttest.

Scannerebene in `KvStandLueckenDifferenzTest` (T-1, T-5): `TextScanner` mit `[Email, Ipv4, Url, Path,
KvStandLuecken.lanr(), KvStandLuecken.ik()]` gegen `TextScanner.standard()`: (a) jede alt abgedeckte Position ist neu
abgedeckt, eine alt von KV abgedeckte Position nie nur von Nicht-KV (wie bestehend); (b) ab T-5 auf Texten ohne
Weit-Kontextwort beider Arten und ohne `CR UZ+ LF`: keine neu abgedeckte Position. Über Fuzz 1, 1b, 1c, 1d, 1e,
Korpus, Generatoren der Eigenschaftstests, Mindestzahl alter Funde > 0 (läuft nicht ins Leere).

---

## 5. Wiederverwendung

`KvNumberScanner` (Lauf, `lauf()`, `fuellwort()`, `punkt()`, `zeilenumbruch()`, `kontextBeginnen()`,
`offenNachgestelltAusgeben()`, `ausgeben()` mit Deckel, `istUnsichtbar`, `istBuchstabe`, `vergleichszeichen`,
`kontextLaenge`, Konstanten `CONFIDENCE_SPERRSCHRIFT`, `NBSP`, `SCHMALES_LEERZEICHEN`, `SCHMALES_NBSP`,
`GESCHUETZTER_BINDESTRICH`, `HALBGEVIERTSTRICH`, `MAX_KONTEXTWORT`), `KvArt`, `KvCheckDigits`, `Finding`,
`ConfidenceLevel`, `TextScanner`, `ConflictResolver`, `Pseudonymizer`, `Reidentifier` (alle unverändert).
Testseitig: `SynthetischeKvNummern`, `KvTestHilfen` (`Art`, `scan`, `assertEin`, `assertFolge`, `laufzeit`,
`ceilMillion`, `quelleNachNummer`, Wächter, `sperrKontextwoerterReferenz`, Zeichenkonstanten), `RoundtripFuzzTest`
(Lauf, Klassen, `BlockC`-Markierung, `kvBausteinC` als Baustein für Mischungen), `KvDifferenzTest.korpus()`,
`RoundtripKorpusTest.kvFaelle()`, `KvKontextLueckenEigenschaftTest.differenzTexte()`, Siegel-Hash aus
`KvVorgaengerDifferenzTest` (als Vorlage, nicht geändert).

## 6. Neue Abhängigkeiten

Keine.

---

## 7. Sicherheits- und Datenschutzaspekte

- **Offline, kein Klartext:** reine Zeichenverarbeitung in einer bestehenden Klasse, keine neue Meldung, keine neue
  Ausnahme, keine neue Quelle; `toString()` der Funde unverändert. Testmeldungen nennen Seed, Iteration, Zähler,
  Fallname – nie Text oder Ziffern.
- **Im Zweifel schützen, ohne Fehlalarm-Zuwachs:** Teil 1 schließt eine Lücke, die nur durch ein unsichtbares Zeichen
  entstand (ein unsichtbares Zeichen darf den Schutz nie verringern). Teil 2 wirkt nur bei exakt einem Kontextwort ab
  vier Buchstaben (Kap. 3.2.6), mit Deckel 0,80 („wahrscheinlich", nie „sicher").
- **Bestandsschutz:** Vergleich mit zwei eingefrorenen Kopien in jedem KV-Testtext; gegen den heutigen Stand ohne
  jede Ausnahme.
- **Mensch entscheidet:** Neue Funde werden nur ersetzt, wenn der Aufrufer sie übergibt (AK-29).
- **Unsichtbare Zeichen als Angriffsmittel:** UZ zwischen CR und LF und innerhalb von Trennerfolgen verringern den
  Schutz nicht mehr; als Eigenschaft geprüft (AK-6, AK-20, AK-24).
- **Laufzeit:** linear; harte 2-s-Grenzen mit exakter Fundzahl über die ganze Kette (AK-26), auch für pathologische
  Eingaben (`CR` + 1 000 000 UZ, `L` + 1 000 000 Leerzeichen).
- **Testdaten:** nur `00`-Nummern und Nullfolgen, Wächter vor jedem Scan, Hash-/Siegel-Konstanten hexadezimal.

---

## 8. Verbindliche Regeln für engineer und Prüfer

1. Alle Regeln aus Kap. 8 der Vorgängerpläne gelten weiter (kein Regex im Produkt, benannte Tests je AK-Beispiel,
   Eigenschaftstests mit festem Seed und unabhängigem Orakel, keine Ausgabe).
2. **Zeilen höchstens 120 Zeichen; schließende Klammern mehrzeiliger Blöcke, Aufrufe und Array-Initialisierer auf
   eigener Zeile.**
   **Klarstellung der Hauptsitzung (22.11.2026, nach dem reviewer-Befund in T-1):** Gemeint ist die geschweifte
   Klammer `}` am Blockende – sie steht nie hinter einer Anweisung auf derselben Zeile (Anlass war ein solcher Fall im
   Vorfeature). Für runde Klammern mehrzeiliger Aufrufe und für Array-Initialisierer gilt der im Projekt
   durchgängig verwendete Stil: die schließende Klammer darf am Ende der letzten Argumentzeile stehen (`…);`).
   Maßstab ist die Einheitlichkeit mit dem umgebenden Bestandscode, nicht eine Umformatierung.
3. **Keine `\uXXXX`-Escapes in neuen oder geänderten Zeilen.** Sonderzeichen als `(char) 0x…`-Konstanten aus
   `KvTestHilfen` (vorhanden: `NBSP`, `SHY`, `ZWSP`, `ZWNJ`, `ZWJ`, `WORD_JOINER`, `BOM`, `VS16`, `AKUT`, `DUENN`,
   `SCHMAL_NBSP`, `NEL`, `GESCH_BINDESTRICH`, `HALBGEVIERT`, `LS`, `PS`; neu in T-1: `LRM = (char) 0x200E`,
   `EN_SPACE = (char) 0x2002`) oder `zeichen(int)`. `\r`, `\n`, `\t` in Zeichenketten sind erlaubt.
4. **Keine Ziffernkette ab neun Stellen außerhalb von `SynthetischeKvNummern.java`**, auch nicht in Kommentaren,
   Javadoc, Testnamen oder Seeds; Hash-/Siegel-Konstanten hexadezimal (höchstens acht Hexziffern). Seeds höchstens
   acht Dezimalstellen.
5. Meldungen ohne Klartext und ohne Ziffern des Textes; kein `System.out`/`System.err`; kein Netzwerk-, DNS- oder
   Dateizugriff; keine neue Abhängigkeit.
6. **Unverändert in allen Tasks:** `KvNumberScannerVorgaenger`, `KvVorgaenger`, `KvArt`, `KvCheckDigits`,
   `SIEGEL_TEIL_1/1B/1C`, `HASH_TEIL_1/1B/1C`, die Generatoren und Seeds von Fuzz-Teil 1, 1b, 1c;
   `KvNumberScannerStandLuecken` und `KvStandLuecken` nach T-1. `HASH_TEIL_1D` nach T-4, `HASH_TEIL_1E` nach T-9.
7. **Keine Mindestzahl wird gesenkt**, kein Seed geändert, um einen Test grün zu bekommen.
8. **Umstellung bestehender Tests nur wie in den Tasks genannt** (T-2: Grenze 15; T-3: Aussparung CR/LF; T-6:
   Grenze 14c und die dort aufgezählten 14c-Gegenproben). Meldet ein anderer bestehender Test eine Änderung, hält der
   engineer an und meldet Test, Fall (ohne Ziffern) und Grund; keine stille Anpassung. Jede Umstellung wird im Bericht
   einzeln je Assertion genannt (AK-23).
9. In neuen Tests die Quelle aus der Nummer ableiten (`quelleNachNummer`).
9a. **Klarstellung der Hauptsitzung zum Einfrieren des Prüfstands (22.11.2026, nach reviewer und security in T-5):**
    „T-5-Dateien unverändert“ bezieht sich auf den **Inhalt der Prüfstand-Teile**, nicht auf ganze Dateien.
    Eingefroren sind ab Abnahme von T-5: `KvTrennerFehlalarmTest.java` vollständig; in `KvTestHilfen` die
    Prüfstand-Mitglieder (`WeitFolge`, `weitFolgenReferenz`, `weitKontextwoerterReferenz`,
    `hatErweitertesWeitKontextwort`, `zusaetzlicheFunde`, `fehlalarmZahl`, `vonFehlalarmPruefung*`,
    `pruefeKeineZusaetzlichenFunde` samt der in T-5 Durchlauf 2 ergänzten Bedingungen, `kuenstlicherNeuErkenner`,
    `enthaeltCrUnsichtbarLf`, der zugehörige Schritt in `scan`; aus Durchlauf 2 außerdem `wortVergleichsform`,
    `Vergleichsform`, `erweiterteLuecken`, `lueckenArtReferenz`, `ohneUnsichtbare`/`OhneUz`, die Wortlisten
    `WOERTER_*`/`SPERR_WOERTER_IK`, `fehlalarmZaehlung`, `abweichendeFunde`, `kuenstlicherRichtigerNeuErkenner`,
    `kuenstlicherAbweichenderNeuErkenner`, `findeGleichenFund`, `istWeitKontextwort`,
    `vonFehlalarmPruefungBeiderArtenAusgenommen`, `FEHLALARM_LEERZEILE`/`hinterFehlalarmMuster` – kurz: alles, wovon
    die Fehlalarm-Prüfung abhängt; im Zweifel gilt ein Mitglied als eingefroren) und der Typ `Art`. Kap. 4.3
    Punkt 5 beschreibt noch die erste Fassung der Prüfregel; maßgeblich sind die beiden Verschärfungen unten.
    In `KvStandLueckenDifferenzTest` zusätzlich
    `neuUnerklaert` und `fehlalarmScannerebene`; dort
    die Tests `t5_*` samt ihren Mindestzahlen und Hilfen. **Erlaubt** sind reine Ergänzungen: neue Helfer (bevorzugt
    in einer eigenen Klasse, z. B. `KvTrennerHilfen`), neue Testmethoden (z. B. `t9_…` für Fuzz 1e, die die
    eingefrorenen Hilfen wiederverwenden). qa prüft in T-6 bis T-10, dass im Diff dieser Dateien keine Zeile der
    eingefrorenen Teile entfernt oder geändert ist.
    **Verschärfungen des Prüfstands in T-5 Durchlauf 2 (security):** (1) In Texten, die wegen eines erweiterten
    Weit-Kontextworts von der Prüfung ausgenommen sind, ist ein zusätzlicher Fund nur zulässig, wenn die
    eingefrorene Kopie auf der **Wort-Vergleichsform** (nur die erweiterten Lücken innerhalb der von der Referenz
    gefundenen Weit-Kontextwörter der Art durch je ein Leerzeichen ersetzt; Positionen abgebildet) denselben Fund
    (Bereich, Typ) liefert. (2) In nicht ausgenommenen Texten gilt volle Gleichheit mit der eingefrorenen Kopie
    (Start, Ende, Typ, Konfidenz, Quelle). Schlägt (1) in T-6/T-7 bei einem vom Plan gewollten Fund an (Kap. 13
    Hinweis 2: Kontext-Erneuerung), hält der engineer an und meldet den Fall; die Hauptsitzung entscheidet.
    **Restlücken des Prüfstands (security, T-5 Durchlauf 2) – als reine Ergänzungen in T-6 zu schließen:**
    (a) Fehlermodell „unsichtbares Zeichen trennt zwei Buchstaben“ (`L`+ZWSP+`A. N. R. N`, Grenze 14a) wird vom
    eingefrorenen Satz nicht erzeugt → T-6 ergänzt eigene Nicht-Fall-Tests (neue Methode/Klasse, die
    `fehlalarmZahl` nutzt) mit `L`+ZWSP+`A. N. R. #`, `L`+ZWSP+`A  N  R #`, `I`+ZWSP+`K. N. R. #` und einen kleinen
    Zufallslauf mit Buchstabe+UZ+Buchstabe ohne Lücke. (b) Scannerebene vergleicht nur Abdeckung, nicht den Typ →
    T-6 ergänzt eine neue Testmethode mit Abdeckung je KV-Typ (LANR und IK getrennt). (c) In ausgenommenen Texten
    werden Konfidenz und Quelle erklärter Zusatzfunde nicht verglichen (D-6, Deckel 0,80) → das sichern AK-21 und
    AK-24 (e) in T-7/T-8.
10. **Abbruchkriterium Teil 2 (D-8) – gilt in T-6 bis T-10:** Meldet einer der Fehlalarm-Prüfungen
    (`pruefeKeineZusaetzlichenFunde` in `scan`, `KvTrennerFehlalarmTest` AK-15/AK-16, Scannerebene in
    `KvStandLueckenDifferenzTest`) einen zusätzlichen Fund, dann
    - ändert der engineer **nichts** an Erwartungen, Textlisten, Generatoren, Seeds, Mindestzahlen, der Referenz
      `weitKontextwoerterReferenz`, dem Filter `enthaeltCrUnsichtbarLf` oder der Prüfregel;
    - darf er das Produkt nur dann im selben Durchlauf korrigieren, wenn die Ursache nachweislich eine Abweichung des
      Codes von Kap. 3.2 dieses Plans ist (falscher Übergang, falsche Reihenfolge); er nennt sie im Bericht;
    - in jedem anderen Fall (die Regel selbst erzeugt den Fund, oder die Ursache ist unklar) **hält er an, nimmt alle
      Teil-2-Änderungen an `KvNumberScanner.java` (und an `LanrRecognizer`/`IkRecognizer`) zurück** auf den in T-4
      abgenommenen Stand, lässt den Build grün (die Tests aus T-5 bleiben, sie sind mit dem Teil-1-Stand grün) und
      meldet Seed, Iteration bzw. Fallname, Art und Form ohne Text und Ziffern. Die Hauptsitzung legt den Befund dem
      Nutzer vor; dieser entscheidet über die Streichung nach D-8 (dann entfallen AK-7 bis AK-21 und T-6 bis T-10,
      Grenze 14c bleibt offen).

---

## 9. Tasks

Nach jedem Task ist `mvn verify` im Wurzelverzeichnis grün. Pfade: `main/…` = `skillshield-core/src/main/java/
de/skillshield/core/`, `test/…` = `skillshield-core/src/test/java/de/skillshield/core/`.

### Teil 1 – Grenze 15 (CR + UZ + LF)

#### T-1: Heutigen Stand einfrieren, strenge Differenzprüfung (nur Tests)

- **Ziel:** Vergleichsbasis Kap. 4. Keine Produktänderung.
- **Dateien:** neu `test/…/recognizer/kv/KvNumberScannerStandLuecken.java` (Kopie), neu
  `test/…/recognizer/kv/KvStandLuecken.java` (öffentlich, `lanr()`, `ik()`), neu `test/…/KvStandLueckenDifferenzTest.java`;
  geändert `test/…/recognizer/kv/KvTestHilfen.java` (Feld `standLuecken` in `Art` samt Konstruktor,
  `pruefeGegenStandLuecken` in `scan`, `verletztBestandsschutzOhneAusnahme`, Konstanten `LRM`, `EN_SPACE`).
- **AK:** AK-22 (Werkzeug), AK-27, AK-28.
- **Tests:** `standLuecken_fundbildIstEingefroren` (Siegel 1, 1b, 1c); `t1Uebergang_produktGleichStandLuecken`;
  Differenz Erkenner- und Scannerebene über Fuzz 1, 1b, 1c, Korpus, Generatoren (grün, da gleich); Selbsttests der
  neuen Prüfregel: fehlender Fund, gesenkte Konfidenz, verschobener Bereich, anderer Typ → gemeldet; **die
  14g-Senkung (`ik` 0,80 → 0,50) wird gegen die neue Kopie ebenfalls gemeldet**; gleiche Liste und zusätzlicher Fund →
  nicht gemeldet; Mindestzahl alter Funde > 0 je Textmenge; Konstanten `LRM`, `EN_SPACE` geprüft.
- **Abnahme:** Diff der Kopie gegen `KvNumberScanner` zeigt nur Klassenname und Kopfvermerk; alle bestehenden Tests
  unverändert grün; Siegel hexadezimal; kein `\u`-Escape in neuen Zeilen.

#### T-2: CR + UZ + LF als ein Zeilenumbruch (Produkt)

- **Ziel:** Kap. 3.1 umsetzen.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (Merker `crOffen`, Javadoc: Zeichenklasse
  „Zeilenumbruch" um `CR UZ+ LF` ergänzt, Absatz Zeilenumbruch, bekannte Grenze 15 „geschlossen" mit Rest 15R,
  Linearität ein Satz); neu `test/…/recognizer/kv/KvUmbruchTest.java`; geändert `test/…/recognizer/kv/
  KvSperrschriftTest.java`; `test/…/KvStandLueckenDifferenzTest.java` (Übergangstest entfernen).
  **Umgestellt (einzeln im Bericht):** `KvSperrschriftTest.bekannteGrenze15_crUnsichtbaresZeichenLf_…`: die beiden
  `assertKein` mit `\r` + `ZWSP` + `\n` werden zu `assertEin` mit `WAHRSCHEINLICH` bzw. `SPERR`; Test umbenannt in
  `ak1_grenze15Geschlossen_crUnsichtbaresZeichenLfWieCrLf` (die beiden `\r\n`-Zeilen bleiben).
- **AK:** AK-1, AK-2, AK-3, AK-4, AK-5, AK-22, AK-23 (Grenze 15), AK-26 (Erkennerebene), AK-30 (Teil 1).
- **Tests (je Art):** AK-1 mit U+200B, U+00AD, U+FEFF, U+2060, U+200E, U+FE0F (`ZWSP`, `SHY`, `BOM`, `WORD_JOINER`,
  `LRM`, `VS16`); AK-2; AK-3 (`L A N R:`, `I K N R:` → 0,80; `I K:` → kein Fund); jede Folge aus AK-4 → kein Fund;
  AK-5 (a) bis (f) als Vergleich „Fundliste mit `CR UZ LF` gleich Fundliste mit `CR LF`" (Typ, Bereich verschoben um
  die Zahl der UZ, Konfidenz); `bekannteGrenze15Rest_crLeerraumLfUndNel_zweiUmbrueche` (CR Leerzeichen LF, CR NBSP LF,
  CR U+0085, CR U+2028, CR U+2029, `CR UZ A LF`, `CR UZ` + `AKUT` + `LF` → kein Fund). Laufzeit (Erkenner, exakte
  Zahl mit Formel): `CR` + 1 000 000 `ZWSP` → 0; `CR ZWSP LF` wiederholt → 0; `LANR:` CR ZWSP LF `<Nullnummer>` ` `
  wiederholt → r Funde (Bausteinlänge 18, r = ⌈1 000 000 / 18⌉ = 55 556), IK entsprechend.
- **Abnahme:** genau die eine Umstellung; Differenzprüfung (beide Kopien) grün; Javadoc beschreibt D-1.

#### T-3: Eigenschaftstests Teil 1 (nur Tests)

- **Ziel:** AK-6 und AK-24 (b).
- **Dateien:** geändert `test/…/recognizer/kv/KvKontextLueckenEigenschaftTest.java` (in `streue` entfällt
  `zwischenCrLf`; Klassen-Javadoc „Ausgespart" nachgeführt); neu `test/…/recognizer/kv/KvUmbruchEigenschaftTest.java`;
  `KvTestHilfen.java` (unabhängige Referenz `umbruchZahlReferenz(String folge)`: zählt `CR UZ* LF` als 1, jedes
  andere Umbruchzeichen als 1, nur über eine Zeichenkette aus Umbrüchen, UZ und Leerraum).
  **Umgestellt (einzeln im Bericht):** die Aussparung „zwischen CR und LF" in (e). Die Texte von (e) ändern sich
  dadurch (anderer Zufallsverbrauch); `differenzTexte()` enthält (e) nicht, Siegel sind nicht betroffen.
- **AK:** AK-6, AK-24 (b), AK-22.
- **Tests:** (e) unverändert in seinen Erwartungen, jetzt mit erlaubter Position zwischen CR und LF und Zähler
  „UZ zwischen CR und LF eingefügt" (Mindestzahl); AK-24 (b), je 1 000 Texte, fester Seed: `KW` + Trenner + Folge
  aus zufällig gezogenen CR, LF, UZ (alle sechs aus AK-1 plus U+FE00), Leerzeichen, U+0085 + Nummer; Erwartung aus
  `umbruchZahlReferenz`: 0 → direkt (0,95/0,92), 1 → 0,80, ≥ 2 → kein Fund; dasselbe hinter `L A N R`/`I K N R`
  (0,80/0,80/kein) und hinter `I K` (0,50/kein/kein); nummerierte Liste `1.` hinter der Folge; Zähler je Klasse
  (0, 1, ≥ 2 Umbrüche; Anteil mit `CR UZ+ LF`) mit Mindestzahlen. Jeder Text durch `scan`.
- **Abnahme:** Orakel aus der Referenz, nicht aus dem Produkt; keine Produktänderung.

#### T-4: Teil 1 über die ganze Kette, Abschluss Teil 1 (nur Tests)

- **Ziel:** AK-25, AK-26 (g, h), AK-28, AK-29 für Teil 1. **Nach Abnahme von T-4 ist Teil 1 fertig und wird als
  eigener Stand gesichert (Commit), bevor T-5 beginnt.**
- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1d**: eigene Seeds `SEED_TEXTE_KV_D`,
  `SEED_AUSWAHL_KV_D`, eigener Generator, `HASH_TEIL_1D` hexadezimal; Bausteine: `KW`/`KW:` + CR + 1–3 UZ + LF + N,
  strenge Sperr-KW + CR UZ LF + N, `KW N` CR UZ LF `N2`, gemischt mit Bausteinen aus `kvBausteinC` (eigener
  `Random`); Gegenproben ohne Block: Leerzeile mit `CR UZ LF CR UZ LF`, `I K:` CR UZ LF N, `N` CR UZ LF `KW`, CR +
  Leerzeichen + LF, CR + U+0085; Blöcke, die per Konstruktion an einer bekannten Grenze liegen (14e durch `" x "`),
  werden bei der Konstruktion markiert, ohne Zufallszahlen zu ziehen); `test/…/KvStandLueckenDifferenzTest.java`
  (Siegel 1d, Differenz über 1d); `test/…/RoundtripKorpusTest.java` (KV-Fälle AK-1 bis AK-5 mit erwartetem
  pseudonymisiertem Text, z. B. `LANR:` CR ZWSP LF `[[LANR_1]]`; Gegenproben als `kein`); `test/…/GrenzlastTest.java`
  (neue parametrisierte Methode `ak26_kvTrennerUndUmbruch_eineMillionZeichen_dreiSchritteJeUnterZweiSekunden…`
  mit (g) und (h)); `test/…/KlartextFreiheitTest.java`; `test/…/recognizer/TextScannerKvTest.java`.
- **AK:** AK-22, AK-25, AK-26 (g, h), AK-27, AK-28, AK-29 (Teil 1).
- **Tests:** Fuzz 1d: Roundtrip zeichengleich, keine Ziffernkette ≥ 6 eines Fundes im Ausgabetext, Schutzgarantie,
  Invarianten, Determinismus der Textfolge, Klassen-Mindestzahlen (`kv-umbruch-fund`, je Art, je UZ-Anzahl),
  **`block-ohne-scan-abdeckung` strikt 0** je nicht markiertem Block, markierte Blöcke einzeln gezählt;
  Grenzlast über `TextScanner.standard()` + Pseudonymisierung + Rückübersetzung je < 2 s nach Aufwärmlauf, exakte
  Fundzahl mit Formel: (g) `LANR:` CR ZWSP LF `<Nullnummer>` ` ` → 55 556 bei Bausteinlänge 18; (h) CR + 1 000 000
  ZWSP → 0 und `CR ZWSP LF` wiederholt → 0; Klartextfreiheit: Fund aus `L A N R:` CR ZWSP LF + `LANR_MARKER`,
  `toString()` ohne Marker; AK-29: `L A N R:` CR ZWSP LF `N` liefert Fund, Pseudonymisierung mit leerer Liste →
  Text unverändert, mit Fund → nur die Nummer ersetzt, CR, ZWSP, LF zeichengleich.
- **Abnahme:** `HASH_TEIL_1/1B/1C` und Siegel 1/1B/1C unverändert; Teil 1 vollständig (AK-1 bis AK-6 und Teil-1-Anteile
  von AK-22 bis AK-30); Javadoc Grenze 15 geschlossen. Bericht an die Hauptsitzung: „Teil 1 abgeschlossen".

### Teil 2 – Grenze 14c (Mehrfachtrenner), unter dem Abbruchkriterium D-8

#### T-5: Fehlalarm-Prüfstand (nur Tests, vor jeder Produktänderung für Teil 2)

- **Ziel:** AK-15 und AK-16 als ausführbare Prüfungen, unabhängig vom späteren Produktcode geschrieben; Prüfung
  „keine zusätzlichen Funde" in jedem KV-Testtext. Auf dem Teil-1-Stand ist alles grün (Gegenprobe: Teil 1 erzeugt
  außerhalb von `CR UZ+ LF` keine zusätzlichen Funde).
- **Dateien:** `test/…/recognizer/kv/KvTestHilfen.java` (`weitKontextwoerterReferenz`, `enthaeltCrUnsichtbarLf`,
  `zusaetzlicheFunde`, `pruefeKeineZusaetzlichenFunde` in `scan`); neu
  `test/…/recognizer/kv/KvTrennerFehlalarmTest.java`; `test/…/KvStandLueckenDifferenzTest.java` (Scannerebene: keine
  neu abgedeckte Position auf Texten ohne Weit-Kontextwort, Kap. 4.3).
- **AK:** AK-15, AK-16 (Prüfstand), AK-22, AK-27.
- **Tests:**
  - `ak15_festerSatzVonNichtFaellen_keinZusaetzlicherFund`: jeder Text aus AK-15 (Initialen, Aufzählungen,
    Abkürzungen, Tabellen- und Formularzeilen, Mehrfach-Leerzeichen, die Buchstabenfolgen `L A N`, `I K E A`,
    `L. A. N.`, `I K N`, `N. R. L. A.`, `R. N. A. L.`) je viermal (gültige LANR, gültige IK, `<Zahl>` als Nullfolge
    bzw. `00`-Kette, ohne Zahl) und je Art: Vorbedingung „Referenz findet kein Weit-Kontextwort", dann
    `zusaetzlicheFunde(standLuecken, neu) == 0`. Textliste als Konstante, im Bericht vollständig.
  - `ak16_zufallsvergleich_keinZusaetzlicherFund`: fester Seed, **100 000 Texte**, je 4 bis 14 Bausteine aus:
    Rauschwörtern (auch `Nr`, `Nummer`, `Arzt`, `Praxis`, `der`), **normalen Kontextwörtern beider Arten** (damit
    laufende Kontexte vorkommen), Initialen (`I. K.`, `Dr. M. A. Beispiel`, `J. R. R.`, `Prof. I. K. Mustermann`),
    Einzelbuchstaben (zur Hälfte aus `L A N R I K Z T U M E`, Groß und klein), Lücken aus D-2 (a)–(d) und
    Nicht-Lücken (vier und mehr Leerzeichen, zwei Tabs, Tab+Leerzeichen, ` - `, `/`, `_`, `,`, `:`), Punkten,
    Satzzeichen, Zahlen und Nummern (gültig, ungültig, Längen, gruppiert), Zeilenumbrüchen (LF, CRLF, CR, U+0085),
    unsichtbaren Zeichen (nie unmittelbar hinter einem CR). Jeder gezogene Text, in dem die Referenz für eine Art ein
    Weit-Kontextwort ab vier Buchstaben findet, wird neu gezogen (Zähler `neuGezogen` > 0 belegt die Nähe zum
    Kontextwort). Prüfung je Text und Art: Wächter; `zusaetzlicheFunde == 0` (Summe über alle Texte genau 0);
    Bestandsschutz gegen die Kopie. Mindestzahlen: Texte mit mindestens einem alten Fund, Texte mit einer
    Einzelbuchstabenfolge ab vier Buchstaben mit erweiterter Lücke, Texte mit Beinahe-Kontextwort (ein Buchstabe
    fehlt, zu viel oder vertauscht), Texte mit `I. K.`/`I  K`/`I<Tab>K` vor einer Nummer. Meldungen nur Seed,
    Iteration, Zähler.
  - Selbsttests: ein künstlicher Erkenner mit einem zusätzlichen Fund wird als Verstoß gezählt; die Referenz erkennt
    `L. A. N. R.`, `L  A  N  R`, `L<Tab>A<Tab>N<Tab>R`, `L. A N  R` (erweitert), `L A N R` (nicht erweitert),
    `I. K.` (zwei Buchstaben, kein Treffer), `L    A`, `L .A`, `L - A` (keine Lücke), `L` ZWSP `A` (Teilgruppe), `L. `
    ZWSP ` A. N. R.` (durchsichtig), Start/Ende im Originaltext mit UZ; `enthaeltCrUnsichtbarLf` für
    `CR ZWSP LF` ja, `CR LF`, `CR ZWSP CR`, `CR ZWSP A LF` nein.
- **Abnahme:** keine Produktänderung; alle Tests grün auf dem Teil-1-Stand; Testlaufzeit von `KvTrennerFehlalarmTest`
  im Bericht genannt. Diese Tests werden in T-6 bis T-10 **nicht** verändert (Regel 8.10).

#### T-6: Weit-Verfolger (Produkt, Kern) – erster Produkt-Task von Teil 2, Fehlalarm-Nachweis

- **Ziel:** Kap. 3.2.2 bis 3.2.4 und 3.2.6 umsetzen; das Ereignis wirkt nur ohne laufenden Kontext (die Regel aus
  3.2.5 folgt in T-7). **Abnahmebedingung: alle Prüfungen aus T-5 unverändert grün** (AK-15, AK-16, Prüfung in
  `scan`, Scannerebene). Sind sie nicht erfüllbar: Regel 8.10.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (Felder, Konstanten, `vorausArt`, `weitSchritt`,
  `weitEnde`, `weitAnhaengen`, Abbild, `istKontextwort(char[], int)`, umgebautes `laufMitVerfolger`, `weitEnde()` am
  Textende; Javadoc: Zeichenklassen „Weit-Lücke", „Weit-Folge", Ereignis, Konfidenz, Linearität nach Kap. 3.4,
  Grenze 14c „geschlossen bis auf 14cR"); Javadoc `LanrRecognizer`, `IkRecognizer`; neu
  `test/…/recognizer/kv/KvTrennerTest.java`; geändert `test/…/recognizer/kv/KvSperrschriftTest.java`,
  `test/…/recognizer/kv/KvKontextLueckenEigenschaftTest.java`, `test/…/RoundtripKorpusTest.java`.
  **Umgestellt (einzeln je Assertion im Bericht):**
  1. `KvSperrschriftTest.bekannteGrenze14c_…`: je Art die Zeilen `sperr(w, ". ") + ". "`, `sperr(w, "\t")`,
     `sperr(w, "  ")` werden zu Funden 0,80 (`assertEin` mit `SPERR`); die Zeile `sperr(w, "\n")` und alle vier
     IK-Zeilen (`I. K.`, `I\tK`, `I\nK`, `I  K`) bleiben „kein Fund"; Test umbenannt in
     `bekannteGrenze14cRest_zweiBuchstabenUndZeilenumbruch_keinFund`; die Fund-Fälle wandern nach `KvTrennerTest`.
  2. `RoundtripKorpusTest`: `bekannteGrenze14c-initialen-lanr` (`L. A. N. R. N`) und
     `kl-sperr-gegenprobe-zwei-trenner` (`L  A N R N`) werden zu Fällen mit erwartetem Fund (umbenannt).
  3. `KvKontextLueckenEigenschaftTest` Gegenproben (d), Fall „Initialenform" (`INITIALEN` enthält `L. A. N. R.`) und
     Fall „gebrochene Folge" (zwei Trenner, Tab): Für Texte, in denen `weitKontextwoerterReferenz` ein
     Weit-Kontextwort der Art findet, entfällt die Erwartung „kein Fund" (der Text läuft weiter durch `scan`, also
     Bestandsschutz und Fehlalarm-Prüfung); ein Zähler `d.weitKontextwort` wird geführt. Der Zufallsverbrauch bleibt
     gleich; `differenzTexte()` bleibt textgleich.
  Nicht umgestellt: alle `ak6_…` in `KvSperrschriftTest` (zwei Buchstaben, bleiben grün). Ändert sich ein weiterer
  bestehender Test (zu erwarten nur: Zähler „markiert ohne Abdeckung" in Fuzz 1c, falls ein `L. A. N. R.` aus
  `INITIALEN_C` vor einer Nummer steht), gilt Regel 8.8: anhalten, melden; die Hauptsitzung entscheidet, ob er nach
  AK-23 umgestellt wird – nur wenn jeder neue Fund in einem Text mit Weit-Kontextwort laut Referenz liegt.
- **AK:** AK-7, AK-8, AK-9, AK-10, AK-11, AK-12, AK-13, AK-14, AK-15, AK-16 (Nachweis), AK-17, AK-20, AK-21
  (Bereich, Quelle), AK-22, AK-23 (14c), AK-26 (Erkennerebene), AK-30 (Teil 2).
- **Tests (in `KvTrennerTest`, je Art soweit sinnvoll, benannt nach AK):** jedes Beispiel aus AK-7 bis AK-14 und
  AK-17 (Konfidenz, Stufe, Bereich, Quelle aus der Nummer); AK-12 mit ungültiger Nummer und `LAENGE_7`; AK-13 alle
  Zeilen inkl. Bestand `I K`/`I.K.`/`I-K` 0,50; AK-20: UZ an jedem Buchstaben, vor/hinter, zwischen `.` und
  Leerzeichen, zwischen zwei WS4 (gleiche Fundwerte), `L` ZWSP `A. N. R.` kein Fund, Bestandsfälle 0,92/0,95; AK-21
  Bereich (kein Punkt im Fund bei `L. A. N. R. N.`) und Quelle; Grenzfälle der Tabelle Kap. 3.2.7 ohne die T-7-Zeilen;
  `bekannteGrenze14cRest_erweiterteTrennerAusserhalbD2_keinFund` (vier WS4, zwei Tabs, Tab+Leerzeichen, `.`+Tab,
  ` - `, ` – `, Zeilenumbruch, `,`, `;`, `:`, `/`, `_`, `L .A`, `L.. A..`, `EN_SPACE` als Lücke);
  `bekannteGrenze14cRest_zweiBuchstabenMitErweitertenTrennern_keinFund`; `bekannteGrenze14cFolge_initialenDieGenauEin
  KontextwortErgeben_fund` (`Prof. L. A. N. R. Beispiel, Rechnung <Zahl>` → Fund 0,40; dokumentiert die bekannte
  Folge). Laufzeit (Erkenner, exakte Zahl mit Formel): `L. ` (0), `I.  K.  ` (0), `L` + 1 000 000 Leerzeichen (0),
  `L` + `.  A` (0), `L. A. N. R. <Nullnummer> ` (r, Bausteinlänge 22, r = 45 455), `L<Tab>A<Tab>N<Tab>R<Tab>
  <Nullnummer> ` (r, Bausteinlänge 18, r = 55 556), `L    A    N    R ` (0), `A. ` (0), `I. K. <Nullnummer> ` (0 im
  IK-Erkenner), Vorgriff über durchsichtige Läufe `L. ` + ZWSP × 1 000 + ` A ` wiederholt (0).
- **Abnahme:** T-5-Prüfungen unverändert und grün (qa prüft das Diff der T-5-Dateien: es muss leer sein – siehe Klarstellung unten);
  Differenz gegen beide Kopien grün; genau die genannten Umstellungen; Javadoc nach Kap. 3.2.

#### T-7: Laufender Kontext, nachgestellter Kontext, volle Kontextregeln (Produkt)

- **Ziel:** Regel Kap. 3.2.5 (Kontext erneuern bei aktivem Kontext) und Belege für D-5/D-7 an Beispielen.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (Zweig `aktiv` in `weitEnde()`, Javadoc);
  `test/…/recognizer/kv/KvTrennerTest.java` (ergänzt).
- **AK:** AK-18, AK-19, AK-21 (Gleichheit mit der Vergleichsform an Beispielen), AK-15/AK-16 (laufen mit), AK-22.
- **Tests:** jedes Beispiel aus AK-18 (Füllwörter bis drei/vier, Aufzählung, Umbruch/Leerzeile, `Nr.`, `-Nummer`,
  `I. K. N. R.-Nr.`) und AK-19 (nachgestellt mit Klammer, ohne, mit Komma 0,60; `LANR: L. A. N. R. N` gleich
  `LANR: L A N R N`; `IK: Praxis I. K. Nr N` kein Fund); Kap. 3.2.5: `L A N R.  N R N`, `L A N R.  N R des der N`,
  `IK: I K.  N R N`, `IK: I K.  N R des der N`, `N L A N R.  N R` je gleich der Vergleichsform (Typ, Bereich nach
  Abbildung, Konfidenz); Gegenprobe vier Füllwörter. Bestandsschutz-Belege: `LANR N1 L. A. N. R. N2`,
  `L. A. N. R. N1 LANR N2` (N2 0,95).
- **Abnahme:** T-5-Prüfungen unverändert grün; Differenz gegen beide Kopien grün; keine bestehende Erwartung geändert.

#### T-8: Eigenschaftstests Teil 2 (nur Tests)

- **Ziel:** AK-24 (a), (c), (d), (e) mit konstruktivem Orakel.
- **Dateien:** neu `test/…/recognizer/kv/KvTrennerEigenschaftTest.java`.
- **AK:** AK-24 (a), (c), (d), (e), AK-21 (Eigenschaft), AK-20, AK-22, AK-28 (Determinismus, Sortierung,
  Überlappungsfreiheit, Teilstring-Invariante).
- **Tests (je ≥ 1 000 Texte, neue feste Seeds, Meldungen nur Seed/Iteration):**
  (a) Blöcke „Kontextwort ab vier Buchstaben, jede Lücke einzeln aus (a)–(d) gewürfelt mit mindestens einer
  erweiterten, zufällige Groß-/Kleinschreibung, optional Schlusspunkt, UZ an Buchstaben und zwischen Zeichen einer
  Lücke, Direktzeichen (auch Nachsatzwort), Nummer (gültig, ungültig, Länge, gruppiert)" mit Blocktrennern, die jede
  Folge beenden → genau diese Funde, 0,80/0,50/0,40, Quelle aus der Nummer;
  (c) Rauschtexte ohne Kontextwort (normale Wörter ausgeschlossen; Einzelbuchstabenfolgen, Initialen, Tabs, mehrere
  Leerzeichen, Nummern; Texte mit Treffer der Weit- oder der strengen Referenz werden neu gezogen, Zähler) → kein Fund;
  (d) Gegenproben aus AK-13, AK-14, AK-17 aus denselben Bausteinen → kein Fund;
  (e) Gleichheit mit der Vergleichsform: Der Generator baut erweiterte Form und Vergleichsform gleichzeitig (jede
  erweiterte Lücke ↔ ein Leerzeichen) samt Indexabbildung; Bausteine aus (a) plus Füllwörter, Aufzählung,
  Zeilenumbruch, nachgestellter Kontext mit/ohne Komma, normale Kontextwörter davor (laufender Kontext) und die
  Formen aus Kap. 3.2.5 → gleiche Zahl, gleicher Typ, abgebildeter Bereich, Konfidenz = min(0,80, Konfidenz der
  Vergleichsform) für Funde, die nur das gesperrte Kontextwort erschließt, sonst gleich der Vergleichsform.
  Jeder Text zusätzlich durch `scan`. Mindestzählungen je Lückenart (b, c, d), Wortlänge, Stufe, Schlusspunkt,
  UZ-Position.
- **Abnahme:** Orakel aus der Konstruktion, nicht aus dem Produkt; keine Produktänderung. Ein Befund (Abweichung von
  der Konstruktion) wird gemeldet, nicht durch Anpassen der Erwartung gelöst.

#### T-9: Fuzz Teil 1e, Roundtrip-Korpus, Differenz über die neuen Formen (nur Tests)

- **Ziel:** AK-25 mit den neuen Trennerfolgen, auch gemischt mit `CR UZ LF` und den Vorgänger-Formen.
- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1e**: eigene Seeds, eigener Generator,
  `HASH_TEIL_1E` hexadezimal; Bausteine: Weit-Kontextwörter beider Arten mit zufälligen Lücken, nachgestellt
  `N (L. A. N. R.)`, `N, L  A  N  R`, Gegenproben aus AK-13/AK-14/AK-17 ohne Block, Initialen, Bausteine aus Teil 1c
  und 1d mit eigenem `Random`; Blöcke, die per Konstruktion mit einem davorstehenden oder folgenden Einzelbuchstaben
  über eine Lücke nach D-2 verbunden werden können (14e), werden bei der Konstruktion markiert);
  `test/…/KvStandLueckenDifferenzTest.java` (Siegel 1e; Differenz Erkenner- und Scannerebene über 1e; Zugewinn-Beleg:
  Mindestzahl Texte mit neu abgedeckter Position auf 1e); `test/…/RoundtripKorpusTest.java` (KV-Fälle AK-7 bis
  AK-20, z. B. `L. A. N. R. [[LANR_1]]`; Gegenproben als `kein`).
- **AK:** AK-22, AK-25, AK-27.
- **Tests:** Roundtrip zeichengleich, keine Ziffernkette ≥ 6 eines Fundes im Ausgabetext, Schutzgarantie, Invarianten,
  Determinismus; Klassen-Mindestzahlen `kv-weit-fund`, je Lückenart, `kv-weit-nachgestellt`, `kv-weit-komma` (0,60);
  **`block-ohne-scan-abdeckung` strikt 0** für nicht markierte Blöcke, markierte einzeln gezählt;
  `HASH_TEIL_1/1B/1C/1D` unverändert.
- **Abnahme:** keine Produktänderung; tritt ein nicht markierter Block ohne Abdeckung auf, ist das ein Befund
  (melden, nicht markieren).

#### T-10: Grenzlast, Klartextfreiheit, Mensch entscheidet, Abschluss der Dokumentation

- **Ziel:** AK-26 über die ganze Kette, AK-28, AK-29, AK-30 vollständig.
- **Dateien:** `test/…/GrenzlastTest.java` (Fälle (a)–(f), (i)–(k) in die Methode aus T-4); `test/…/
  KlartextFreiheitTest.java`; `test/…/recognizer/TextScannerKvTest.java`; `main/…/recognizer/kv/
  KvNumberScanner.java` nur Javadoc (Liste der bekannten Grenzen: 15 und 14c „geschlossen, siehe
  kv-trenner-und-umbruch", Restgrenzen Kap. 11; Linearitätsbeleg vollständig).
- **AK:** AK-26, AK-28, AK-29, AK-30.
- **Tests:** Grenzlast je 1 000 000 Zeichen, Scan + Pseudonymisierung + Rückübersetzung je < 2 s nach Aufwärmlauf,
  Wächter, Roundtrip, exakte Fundzahl mit Formel im Kommentar: (a) `L. ` → 0; (b) `I.  K.  ` → 0; (c) `L` +
  1 000 000 Leerzeichen → 0; (d) `L` + `.  A` → 0; (e) `L. A. N. R. <Nullnummer> ` → r = 45 455 (> 40 000);
  (f) `L<Tab>A<Tab>N<Tab>R<Tab><Nullnummer> ` → r = 55 556; (i) `L    A    N    R ` → 0; (j) `A. ` → 0;
  (k) `I. K. <Nullnummer> ` → 0 (jeweils bei Baustein ohne Präfix/Schluss; mit Präfix/Schluss Formel anpassen).
  Klartextfreiheit: Funde aus `L. A. N. R. ` + `LANR_MARKER`, `I  K  N  R ` + `IK_MARKER`, `IK_MARKER` +
  ` (I. K. N. R.)`: `toString()` ohne Marker. AK-29: `L. A. N. R. N` liefert Fund; leere Liste → Text unverändert;
  mit Fund → Kontextwort und Trenner zeichengleich. Vollständigkeit: je Restgrenze aus Kap. 11 ein Test
  `bekannteGrenze…`.
- **Abnahme:** alle Grenzen aus Kap. 11 mit Test; Javadoc von Scanner und Erkennern nennt alle Konfidenzen aus
  Kap. 3.3; T-5-Prüfungen unverändert grün.

---

## 10. Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-2 | AK-11 | T-6 | AK-21 | T-6, T-7, T-8 |
| AK-2 | T-2 | AK-12 | T-6 | AK-22 | T-1 (Werkzeug), jeder Task |
| AK-3 | T-2 | AK-13 | T-6 | AK-23 | T-2, T-3, T-6 |
| AK-4 | T-2 | AK-14 | T-6 | AK-24 | T-3 (b), T-8 (a, c, d, e) |
| AK-5 | T-2 | AK-15 | T-5 (Prüfung), T-6 (Nachweis), T-7–T-10 | AK-25 | T-4, T-9 |
| AK-6 | T-3 | AK-16 | T-5 (Prüfung), T-6 (Nachweis), T-7–T-10 | AK-26 | T-2, T-4, T-6, T-10 |
| AK-7 | T-6 | AK-17 | T-6 | AK-27 | T-1, jeder Task (Wächter) |
| AK-8 | T-6 | AK-18 | T-7 | AK-28 | T-1, T-4, T-8, T-10 |
| AK-9 | T-6 | AK-19 | T-7 | AK-29 | T-4, T-10 |
| AK-10 | T-6 | AK-20 | T-6, T-8 | AK-30 | T-2, T-6, T-10 |

Wird Teil 2 nach D-8 gestrichen: T-6 bis T-10 entfallen (T-5 bleibt als Prüfstand bestehen), AK-7 bis AK-21
entfallen; AK-22 bis AK-30 gelten für Teil 1 und sind mit T-1 bis T-4 erfüllt; Grenze 14c bleibt offen
(`bekannteGrenze14c_…` unverändert).

---

## 11. Bekannte Grenzen nach diesem Feature

**Geschlossen:** 15 (CR + UZ + LF) und 14c (Mehrfachtrenner) in den Grenzen von D-1 bis D-4.

**Verbleibend bzw. neu (je ein Test `bekannteGrenze…`):**

- **15R.** CR + Leerraum oder sichtbares Zeichen + LF, CR + Buchstabe/Kombinationszeichen + LF, jede Kombination mit
  U+0085, U+2028, U+2029 → zwei Umbrüche (T-2).
  **Nachtrag 22.11.2026 (security, T-4):** Dazu gehören auch Zeichen, die man nicht sieht, die aber nicht unter die
  Regel „unsichtbar“ fallen (Kategorie FORMAT oder U+FE00–FE0F): U+E0100–E01EF (Variantenwähler-Ergänzung, Mn),
  U+034F (Combining Grapheme Joiner) und die Hangul-Füller U+3164 und U+115F (Lo). `KW:` CR + eines dieser Zeichen +
  LF + Nummer → kein Fund (wie Grenze 10 des ersten Features). Schwere niedrig, nur bei präpariertem Text; eine
  spätere Erweiterung der Regel „unsichtbar“ um U+E0100–E01EF und U+034F wäre die Abhilfe.
- **14cR.** (1) Zwei-Buchstaben-Wort mit erweiterten Trennern (`I. K. N`, `I  K N`, `I<Tab>K N`) → kein Fund (D-4,
  Nutzervorgabe); (2) vier und mehr WS4, zwei und mehr Tabs, Tab mit Leerraum, Punkt mit Tab, Leerraum vor dem Punkt,
  zwei Punkte, `-`/U+2013 mit Leerraum, Zeilenumbruch, `,`, `;`, `:`, `/`, `_`, andere Unicode-Leerräume (z. B.
  U+2002, U+3000) als Lücke → kein Fund (T-6).
- **14d, 14e mit erweiterten Trennern:** maximale Folge (`L. A. N. R. L. A. N. R. N`, `Herr A. L. A. N. R. N`,
  `L. A. N. R. X N`) → kein Fund (AK-14, T-6).
- **Folge 14cF (dokumentiert, kein Fehler):** Initialen oder Einzelbuchstaben, die zusammengelesen exakt ein
  Kontextwort ab vier Buchstaben ergeben (`Prof. L. A. N. R. Beispiel, Rechnung <Zahl>`), erschließen eine folgende
  Zahl (6 bis 12 Ziffern, auch Telefon- oder Rechnungsnummern) als Fund 0,80/0,50/0,40 (T-6).
- Unverändert: 12R, 14a, 14b, 14f, 14g, Restformen nach 14h, Grenzen 1 bis 11 und 13.

## 12. Risiken

- **Fehlalarm Teil 2 (ehrliche Einschätzung):** Auf Texten **ohne** Weit-Kontextwort ist ein zusätzlicher Fund durch
  die Konstruktion ausgeschlossen (Kap. 3.2.6) und wird bei jedem Testtext, an einem festen Satz und an 100 000
  Zufallstexten geprüft. Zusätzliche Funde entstehen nur, wo Einzelbuchstaben mit D-2-Lücken **exakt** `lanr`,
  `lanrnr`, `arztnr`, `arztnummer`, `iknr`, `iknummer` oder `institutionskennzeichen` ergeben und nicht durch einen
  weiteren Einzelbuchstaben verlängert sind. Realistische Fehlalarm-Quellen sind damit: (1) Personen-Initialen, die
  zufällig `L. A. N. R.` oder `I. K. N. R.` lauten (vier Initialen in genau dieser Folge; sehr selten); (2) Tabellen
  oder Formulare, deren Spaltenköpfe aus einzelnen Buchstaben in genau dieser Folge bestehen (`I<Tab>K<Tab>N<Tab>R`);
  (3) Reichweite: Hinter einem echten oder zufälligen Weit-Kontextwort werden wie im Bestand bis zu drei Füllwörter,
  ein Zeilenumbruch, Aufzählungen und davorstehende offene Zahlen erfasst, auch Zahlen, die keine KV-Nummer sind
  (Telefon, Rechnung, 6 bis 12 Ziffern), als „wahrscheinlich" (gültig) bzw. „fraglich". Ich schätze das Risiko als
  **gering** ein: Es gibt keinen Mechanismus, der auf gewöhnlichem Fließtext mit Initialen, Abkürzungen (`z. B.`,
  `i. d. R.`, `u. v. m.`) oder Aufzählungen (`a. b. c.`, `A  B  C`) wirkt, weil keine dieser Folgen ein Kontextwort ab
  vier Buchstaben ergibt und `I. K.` ausdrücklich ausgeschlossen ist. Null ist das Risiko nicht – Teil 2 erzeugt
  genau dort Funde, wo ein Text die Buchstabenfolge eines Kontextworts enthält, und kann nicht unterscheiden, ob sie
  als Kontextwort gemeint war.
- **Teil 1:** kein Fehlalarm-Risiko im Sinne von D-8; zusätzliche Funde nur hinter einem Kontextwort mit einem
  Umbruch, den der Bestand doppelt zählte.
- **Zweiter Automat:** Zustandskomplexität. Gegenmittel: wirkungsfrei außer im Ereignis, strenger Verfolger
  wortgleich, gemeinsamer Vorgriff, Prüfungen gegen zwei Kopien in jedem KV-Testtext, Eigenschaftstests mit
  konstruktivem Orakel, Fuzz mit Strikt-0-Zähler.
- **Gemeinsamer Vorgriff:** Ein Fehler beim Umbau von `einzelbuchstabeVoraus` würde den Bestand ändern. Gegenmittel:
  Übergangs- und Differenzprüfung gegen die Kopie ohne Ausnahme (jede Abweichung des strengen Verfolgers wäre ein
  fehlender oder zusätzlicher Fund).
- **Bestehende Gegenproben mit `L. A. N. R.`:** In `KvKontextLueckenEigenschaftTest` (d), im Korpus und in Fuzz 1c
  stehen 14c-Formen als „kein Fund". Sie sind in T-6 benannt; weitere Änderungen führen zum Anhalten (Regel 8.8).
- **Testlaufzeit:** 100 000 Texte in AK-16 und zwei neue Fuzz-Teile verlängern `mvn verify` um geschätzt 10 bis
  40 Sekunden. Texte kurz halten (≤ 300 Zeichen); ein Wert über 60 s für eine Testklasse wird gemeldet.
- **Drift der Kopie:** Siegel über Fuzz 1, 1b, 1c, 1d, 1e und Regel 8.6.

---

## 13. Hinweise zur Anforderung (mit Lösungsvorschlag, Richtung „schützen, aber keine vermehrten Fehlalarme")

1. **AK-16 „alt" und Teil 1:** Vergleicht man gegen den Stand vor diesem Feature, erzeugt Teil 1 auf Texten mit
   `CR UZ+ LF` zulässige zusätzliche Funde, die AK-16 wörtlich als Fehlalarm zählen würde. Vorschlag (umgesetzt in
   Kap. 4.3): „alt" in AK-15/AK-16 heißt „Stand vor Teil 2"; Texte mit `CR UZ+ LF` werden aus der Prüfung
   „keine zusätzlichen Funde" ausgefiltert, weil Teil 1 nachweislich nur dort wirkt. Der AK-16-Generator setzt
   ohnehin nie ein UZ direkt hinter ein CR. Für „kein Verlust" (AK-22) gibt es keinen Filter.
2. **D-5 gegen D-7 bei strengen Teilereignissen:** Wörtlich verlangt D-7 „bei laufendem Kontext wirkt die
   Sperrschrift nicht". In `L A N R.  N R des der N` oder `IK: I K.  N R des der N` läuft aber nur deshalb ein
   Kontext, weil der Bestand ein Teilstück (`L A N R`, `I K`) schon erkannt hat; die Vergleichsform liefert einen Fund,
   die erweiterte Form ohne Regel keinen (Verstoß gegen D-5/AK-21). Entscheidung in Richtung Schutz (Kap. 3.2.5): Der
   Kontext wird erneuert. Das senkt keine Bestandskonfidenz, wirkt nur bei Weit-Kontextwort und ist mit D-7 im Sinne
   der Vergleichsform vereinbar. Dem Nutzer wird das berichtet.
3. **AK-21 „Minimum aus 0,80 und der Vergleichsform":** Für Funde, die ein normales Kontextwort im selben Text
   erschließt (`L. A. N. R. N1 LANR N2`: N2 0,95), gilt kein Deckel; dort ist die Konfidenz gleich der Vergleichsform.
   Umgesetzt so in AK-24 (e); das Minimum gilt für Funde, die nur das gesperrte Kontextwort erschließt (dort ist die
   Vergleichsform ohnehin höchstens 0,80, das Minimum also gleich der Vergleichsform).
4. **„Leerraumzeichen der Sperrschrift"** sind nur vier Zeichen. Andere Unicode-Leerräume aus OCR (U+2002 bis U+2008,
   U+3000) beenden die Folge. Vorschlag: so lassen (weniger Fehlalarm-Fläche), als Restgrenze 14cR(2) dokumentiert.
5. **AK-23 „nur Tests zu 14c/15 umstellen":** Neben `bekannteGrenze14c_…` halten auch Gegenproben in
   `KvKontextLueckenEigenschaftTest` (d) und zwei Korpusfälle 14c-Formen als „kein Fund" fest. Sie werden in T-6
   ausdrücklich und einzeln umgestellt (es sind 14c-Tests in der Sache).
6. **Bekannte Folge 14cF:** Initialen, die exakt ein Kontextwort ergeben, werden als Kontextwort gelesen. Das folgt
   unmittelbar aus D-3 und ist nach AK-16 zulässig; es wird als bekannte Folge dokumentiert und getestet, damit die
   Oberfläche später darauf hinweisen kann.
7. **AK-26 (k) `I. K. <Nullnummer>`** und (b) sind im LANR-Erkenner trivial 0; geprüft wird über
   `TextScanner.standard()` (beide Erkenner), Erwartung 0.
8. **Offene Frage 2 der Anforderung (UZ zwischen zwei Zeichen einer Trennerfolge):** Aufwand gering – Läufe nur aus
   unsichtbaren Zeichen sind im Weit-Verfolger durchsichtig (Kap. 3.2.3); der strenge Verfolger bleibt wie er ist.
9. **Offene Frage 3 (interne Markierung der Quelle):** nicht nötig, nicht umgesetzt.
10. Für die spätere Oberfläche vormerken: Funde aus Sperrschrift mit erweiterten Trennern sind von sauberer
    Sperrschrift nicht unterscheidbar (D-6, gewollt).

## 14. Abweichungen von Plan.md

Keine. (Kein neues Modul, keine neue Abhängigkeit, keine Änderung an Pseudonymisierung, Rückübersetzung,
Konfliktauflösung, Escaping und Prüfziffer; Konfidenzstufen nach Plan Kap. 6.)
