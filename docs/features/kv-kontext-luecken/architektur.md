# Architektur: Lücken im Kontext der KV-Nummern-Erkennung schließen

Feature-Kurzname: `kv-kontext-luecken`
Grundlage: `anforderung.md` dieses Features (D-1 bis D-7, AK-1 bis AK-29), Vorgänger
`../kv-nummern-erkennung/architektur.md` (Kap. 3, Nachträge vom 22.11.2026 mit R-1 bis R-4, bekannte Grenzen 1–14,
Testdatenregeln), Code in `skillshield-core` (Stand 22.11.2026, `KvNumberScanner` mit Fenster und Teilstück-Bitmaske).

Schreibweise wie in der Anforderung: `N` = synthetische Nummer aus `SynthetischeKvNummern`, `KW` = Kontextwort, `NBSP`
U+00A0, `THIN` U+2009, `NNBSP` U+202F, `ZWSP` U+200B, `SHY` U+00AD.

---

## 1. Lösungsansatz

Beide Lücken werden im bestehenden `KvNumberScanner` geschlossen, ohne neue Produktivklasse und ohne neue Quelle:

1. **Komma/Semikolon (D-5, Grenze 12):** Der Lücken-Merker der offenen Segmente (heute ein Flag `offenTrenner` für
   `,` `;` `/`) wird aufgeteilt in eine Zählung von `,`/`;`, ein Flag für `/` und ein Flag „Klammer vor dem Komma“.
   Beim Kontextwort entscheidet die Lücke hinter dem letzten offenen Segment: sauber (Bestand, 0,80), genau ein
   Komma/Semikolon (neu, Deckel 0,60) oder unzulässig (kein Fund, Bestand).
2. **Sperrschrift (D-1 bis D-4, Grenze 14):** Ein kleiner, zusätzlicher Zustandsautomat („Sperr-Verfolger“) liest
   mit. Er sieht nur ganze Läufe (Bestandsbegriff aus R-1) mit genau einem Buchstaben und die einzelnen Trennzeichen
   dazwischen. Ist die maximale Folge zu Ende und ergibt sie ein Kontextwort der Art, löst er **ein zusätzliches
   Kontextwort-Ereignis** aus: mit Deckel 0,80 (ab drei Buchstaben) bzw. 0,50 und „nur direkt“ (zwei Buchstaben).
   Die Buchstaben selbst werden weiter genau wie heute verarbeitet (je ein Füllwort); das Ereignis kommt nur hinzu.
   Läuft beim Ereignis bereits ein Kontext des Bestands, bleibt dieser unverändert.

Der alte Stand bleibt für den Vergleich als **eingefrorene Kopie im Testcode** verfügbar (Kap. 4). Jeder Fundbereich
des Vorgängers muss im neuen Ergebnis wieder als Fund mit gleichem Bereich und Typ vorkommen.

---

## 2. Betroffene Module, Pakete, Typen

Nur `skillshield-core`, Paket `de.skillshield.core.recognizer.kv`. Kein POM, keine neue Abhängigkeit, keine neue
Produktivklasse, kein neues JDK-Paket (Positivliste und `ArchitekturRegelnTest.BEKANNT` bleiben unverändert).

### 2.1 Geänderte Produktivdateien

| Datei | Änderung |
|-------|----------|
| `recognizer/kv/KvNumberScanner.java` | Lücken-Merker (Kap. 3.1), Sperr-Verfolger (Kap. 3.2), Deckel in `ausgeben`, drei neue Konstanten, Javadoc fortgeschrieben (Zeichenklassen, Zustand, Konfidenz, Linearität, bekannte Grenzen) |
| `recognizer/kv/LanrRecognizer.java`, `IkRecognizer.java` | nur Javadoc: neue Konfidenzen (0,60; 0,80 bei Sperrschrift; 0,50 bei `I K`) |

`KvArt.java` und `KvCheckDigits.java` bleiben **unverändert** (Regel: die eingefrorene Kopie nutzt sie mit; jede
Änderung daran würde den Vergleich verfälschen).

Neue Konstanten (paketintern, neben den bestehenden):

```java
/** Deckel bei Komma oder Semikolon vor dem nachgestellten Kontextwort (D-5). */
static final double CONFIDENCE_KOMMA_NACHGESTELLT = 0.60;
/** Deckel bei gesperrt geschriebenem Kontextwort ab drei Buchstaben (D-4). */
static final double CONFIDENCE_SPERRSCHRIFT = 0.80;
/** Deckel bei gesperrt geschriebenem Kontextwort aus zwei Buchstaben (D-3). */
static final double CONFIDENCE_SPERRSCHRIFT_ZWEI = 0.50;
```

Neue bzw. geänderte Felder der inneren Klasse `Lauf` (Skizze, Namen verbindlich nur sinngemäß):

```java
// Lücke hinter dem letzten offenen Segment (ersetzt offenTrenner)
private int offenKomma;               // Zahl der ',' und ';' (sättigt bei 2)
private boolean offenSchraeg;         // '/' gesehen
private boolean offenKlammerVorKomma; // ',' oder ';' kam nach einer '(' bzw. '['
// Kontextart
private double deckel = 1.0;          // 1.0 = kein Deckel; 0.80 bzw. 0.50 bei Sperrschrift-Kontext
private boolean nurDirekt;            // D-3: Kontext endet, sobald er nicht mehr direkt ist
// Sperr-Verfolger
private int sperrZustand;             // KEINE, BUCHSTABE, TRENNER
private final char[] sperrZeichen = new char[FENSTER - 1]; // 23 = längstes Kontextwort
private int sperrLaenge;              // Zahl der Einzelbuchstaben der laufenden Folge (zählt über 23 hinaus weiter)
private int sperrOffenAnzahl, sperrOffenKlammern, sperrOffenKomma; // Abbild der offenen Segmente
private boolean sperrOffenSchraeg, sperrOffenUnd, sperrOffenKlammerVorKomma; // ... zu Beginn der Folge
```

Neue private Methoden (Skizze): `istSperrTrenner(int cp)`, `einzelbuchstabeVoraus(int p)` (Vorgriff, Kap. 3.2.2),
`sperrSchritt(int cp)` (Zeichen außerhalb eines Laufs), `sperrEnde()`, `sperrKontextwort(int buchstaben)`,
`offenAusgeben(double deckel)` (aus `kontextwort()` herausgelöst), `ausgeben(start, end, anzahl, direkt, deckel)`.

### 2.2 Neue und geänderte Testdateien (Überblick, Einzelheiten in den Tasks)

| Datei (unter `src/test/java/de/skillshield/core/`) | Art |
|------|-----|
| `recognizer/kv/KvNumberScannerVorgaenger.java` | neu: eingefrorene Kopie des Scanners (T-1) |
| `recognizer/kv/KvVorgaenger.java` | neu: öffentliche Recognizer-Hüllen `lanr()`, `ik()` für Tests außerhalb des Pakets (T-1) |
| `recognizer/kv/KvTestHilfen.java` | erweitert: Vorgänger je Art, Differenzprüfung in `scan`, Quelle nach Prüfziffer, Zeichenkonstanten, Sperr-Referenz (T-1, T-5) |
| `KvVorgaengerDifferenzTest.java` | neu: Vergleich alt/neu auf Erkenner- und Scanner-Ebene (T-1, T-6) |
| `recognizer/kv/KvKommaTest.java` | neu (T-2) |
| `recognizer/kv/KvSperrschriftTest.java` | neu (T-3, T-4) |
| `recognizer/kv/KvKontextLueckenEigenschaftTest.java` | neu (T-5) |
| `RoundtripFuzzTest.java` | Hash Teil 1b (T-1), Teil 1c (T-6) |
| `KvNachtragTest`, `KvKontextTest`, `KvErkennungEigenschaftTest` | nur die Grenze-12-Stellen umgestellt (T-2) |
| `RoundtripKorpusTest`, `GrenzlastTest`, `KlartextFreiheitTest`, `recognizer/TextScannerKvTest` | ergänzt (T-6, T-7) |

---

## 3. Erkennungsregeln

### 3.1 Komma/Semikolon vor dem nachgestellten Kontextwort (D-5)

**Lücken-Merker** (gelten nur für die Zeichen zwischen dem letzten offenen Segment und dem nächsten Ereignis; beim
Anhängen eines Segments werden alle zurückgesetzt, wie heute `offenKlammern`, `offenTrenner`, `offenUnd`):

| Zeichen in der Lücke | Wirkung (neu) | bisher |
|------|---------|--------|
| Leerraum (ohne Zeilenumbruch, auch Tab) | nichts | gleich |
| `(` oder `[` | `offenKlammern++`; bei mehr als 1 → `offenLeeren()` | gleich |
| `,` oder `;` | `offenKomma++` (sättigt bei 2); war `offenKlammern > 0` → `offenKlammerVorKomma = true` | `offenTrenner = true` |
| `/` | `offenSchraeg = true` | `offenTrenner = true` |
| Wort `und` | `offenUnd = true` (unverändert, höchstens einmal je Lücke) | gleich |
| alles andere | `offenLeeren()` | gleich |

Die R-2-Regel für das **Anhängen** weiterer Segmente bleibt wörtlich gleich (beliebig viele `,` `;` `/` und einmal
`und` zwischen zwei Segmenten). Neu ist nur die Auswertung beim **Kontextwort** (`offenAusgeben`):

| Lücke hinter dem letzten offenen Segment | Ausgabe aller offenen Segmente |
|------|------|
| `offenKomma == 0`, kein `/`, kein `und` (Bestand) | ja, Deckel 1,0 (gültig 0,80) |
| `offenKomma == 1`, kein `/`, kein `und`, keine Klammer vor dem Komma | **ja, Deckel 0,60** (gültig 0,60; ungültig 0,50; Länge 0,40) |
| sonst | nein (Bestand) |

Damit gelten: `N, KW`, `N; KW`, `N ,KW`, `N,<Tab>KW`, `N, (KW)`, `N ; [KW]`, `N1, N2, KW`, `N1 und N2, KW` → Funde
(AK-14 bis AK-17). Kein Fund: `N,, KW`, `N,; KW`, `N, und KW`, `N, / KW`, `N und KW`, `N / KW`, `N(, KW)`,
`N, ((KW)` (zwei Klammern leeren wie bisher), `N: KW`, `N. KW`, `N, Fax KW`, `N,`+Umbruch+`KW` (AK-18). Ein bereits
ausgegebenes Segment ist nie offen; `KW N, KW N` bleibt Bestand (AK-20). Der Fundbereich bleibt das Segment, das
Komma steht außerhalb (D-7).

### 3.2 Gesperrt geschriebenes Kontextwort (D-1 bis D-4)

#### 3.2.1 Begriffe

- **Einzelbuchstabe:** ein *Lauf* (R-1: maximale Folge aus Buchstaben und unsichtbaren Zeichen) mit genau einem
  Buchstaben im Sinne des Bestands (`istBuchstabe`), der zugleich `Character.isLetter` ist. Unsichtbare Zeichen vor
  oder hinter dem Buchstaben gehören zum Lauf und werden übergangen (D-2 letzter Satz). Ein Lauf aus einem Buchstaben
  mit Kombinationszeichen hat zwei „Buchstaben“ und ist kein Einzelbuchstabe. Ein Lauf nur aus unsichtbaren Zeichen
  ist für den Verfolger **durchsichtig** (er ändert den Zustand nicht).
- **Sperr-Trenner (D-1):** genau eines der Zeichen Leerzeichen, NBSP, THIN, NNBSP, `.`, `-`, U+2011, U+2013.
- **Sperrfolge:** maximale Folge von Einzelbuchstaben, je durch genau einen Sperr-Trenner verbunden. Ein zweiter
  Trenner, jedes andere Zeichen, ein Lauf mit mehr als einem Buchstaben oder nur aus Kombinationszeichen, eine Ziffer,
  ein Zeilenumbruch und das Textende beenden die Folge. Ein Trenner nach dem letzten Buchstaben (`I.K.`, `L A N R-`)
  ist erlaubt und gehört nicht zum Vergleich.
- **Sperr-Kontextwort:** Sperrfolge mit mindestens zwei Buchstaben, deren gefaltete Buchstaben (Bestandsfaltung
  `vergleichszeichen`: nur `A–Z`) genau einem Eintrag aus `KvArt.kontextwoerter()` gleichen. Vorwörter
  (`arzt`) zählen nicht (D-2, bekannte Grenze). Folgen über 23 Buchstaben können keinem Eintrag gleichen.

Folge dieser Lauf-Definition: Die Sperrschrift-Erkennung ist **unabhängig von unsichtbaren Zeichen**: Ein Text und
derselbe Text, aus dem alle unsichtbaren Zeichen außerhalb der Nummern entfernt sind, ergeben dieselben
Sperr-Kontextwörter (Begründung: unsichtbare Zeichen hängen sich immer an einen Nachbarlauf oder bilden einen
durchsichtigen Lauf; sie entfernen und ersetzen nie einen Trenner und verbinden nie zwei Buchstaben, die ohne sie
getrennt wären). Damit kann **kein eingestreutes unsichtbares Zeichen eine neue Lücke öffnen**. Das wird als
Eigenschaft getestet (T-5). Im Einzelnen:

| Text | Ergebnis | Begründung |
|------|----------|-----------|
| `L`+ZWSP+`A`+ZWSP+`N`+ZWSP+`R N` | Fund 0,95 (Bestand) | ein Lauf, R-1 findet `lanr`; kein Sperr-Ereignis (ein Lauf mit vier Buchstaben ist kein Einzelbuchstabe) |
| `I`+SHY+`K N` | Fund 0,92 (Bestand) | wie oben |
| `L`+ZWSP+` A N R N` | Fund 0,80 | Lauf `L`+ZWSP ist Einzelbuchstabe; Folge `lanr` (AK-12) |
| `L`+ZWSP+` `+ZWSP+`A N R N` | Fund 0,80 | ZWSP hängt an `L` bzw. `A` |
| `L `+ZWSP+` A N R N` | kein Fund | durchsichtiger Lauf zwischen zwei Trennern: wie `L  A`, zwei Trenner |
| `L`+ZWSP+`A N R N` | kein Fund | Lauf `L`+ZWSP+`A` hat zwei Buchstaben: wie `LA N R` (bekannte Grenze Teilgruppe) |
| `I K`+ZWSP+`-Nr. N` | Fund 0,50 | wie `I K-Nr.` |

#### 3.2.2 Zustände und Übergänge des Sperr-Verfolgers

Zustand `sperrZustand` ∈ {`KEINE`, `BUCHSTABE` (zuletzt ein Einzelbuchstabe), `TRENNER` (danach genau ein
Sperr-Trenner)}, dazu `sperrZeichen`/`sperrLaenge` und das Abbild der offenen Segmente.

| Zustand | Eingabe (Hauptschleife, vor der Bestandsverarbeitung) | Wirkung |
|---------|------|---------|
| `KEINE` | Laufanfang | Abbild der offenen Segmente speichern (6 Werte); Lauf wie bisher verarbeiten; war er ein Einzelbuchstabe → `BUCHSTABE`, `sperrLaenge = 1` |
| `KEINE` | jedes andere Zeichen | nichts |
| `BUCHSTABE` | Sperr-Trenner | → `TRENNER` (das Zeichen wird danach wie bisher verarbeitet: `punkt()` bzw. `sonstiges()`) |
| `BUCHSTABE` | jedes andere Zeichen (Ziffer, Umbruch, `!`, `:` …) | `sperrEnde()`, → `KEINE`, dann Bestandsverarbeitung |
| `TRENNER` | Laufanfang | Vorgriff `einzelbuchstabeVoraus(p)`: Einzelbuchstabe → Lauf wie bisher verarbeiten, Buchstaben anhängen, → `BUCHSTABE`; durchsichtig → Lauf verarbeiten, Zustand bleibt; sonst → `sperrEnde()`, → `KEINE`, **dann** Abbild speichern und Lauf verarbeiten |
| `TRENNER` | jedes Zeichen außerhalb eines Laufs | `sperrEnde()`, → `KEINE`, dann Bestandsverarbeitung |
| beliebig | Textende | `sperrEnde()` |

Hinter einem Lauf folgt immer ein Zeichen außerhalb eines Laufs; im Zustand `BUCHSTABE` kommt daher kein Laufanfang
vor. Die Zeichenfolge im Puffer wird nur bis 23 Zeichen gespeichert; `sperrLaenge` zählt weiter, damit eine längere
Folge sicher kein Kontextwort ergibt.

**Warum der Vorgriff:** Das Ereignis muss *vor* der Verarbeitung des beendenden Laufs wirken, sonst würde ein
Nachsatzwort (`I K-Nr.`, `L A N R Nummer`) nicht als solches erkannt (`lauf()` liest `direkt` zu Beginn) und ein
Füllwort hinter `I K` (`I K EA N`) nicht gegen den neuen Kontext gezählt. Der Vorgriff liest den folgenden Lauf nur
bis zum zweiten Buchstaben oder bis zu seinem Ende und verändert keinen Zustand.

#### 3.2.3 Das Ereignis `sperrKontextwort(k)` (k = Zahl der Buchstaben)

`sperrEnde()` prüft: `2 <= sperrLaenge <= 23` und die Zeichenfolge ist ein Eintrag von `art.kontextwoerter()`. Dann:

1. **Läuft bereits ein Kontext (`aktiv`)**: nichts. Der Bestandskontext bleibt unverändert (gleiche Konfidenz wie
   heute). Das kommt nur bei zwei Buchstaben vor: Ab vier Buchstaben (das kürzeste Kontextwort ab drei Buchstaben,
   `lanr`/`iknr`, hat vier) haben die Einzelbuchstaben als je ein Füllwort den Bestandskontext bereits beendet.
2. Sonst: Offene Segmente aus dem Abbild wiederherstellen (das Feld `offenDaten` wird beim Leeren nicht überschrieben,
   und innerhalb einer Sperrfolge entsteht kein Segment, weil jede Ziffer die Folge beendet).
   - `k >= 3` (D-4): `offenAusgeben(…)` nach Kap. 3.1 (nachgestellt, mit Komma-Regel; Konfidenz 0,80/0,60/0,50/0,40).
   - `k == 2` (D-3): kein nachgestellter Kontext, das Abbild wird verworfen.
3. `offenLeeren()`, `aktiv = direkt = true`, `woerter = umbrueche = 0`, `deckel` = 0,80 (`k >= 3`) bzw. 0,50
   (`k == 2`), `nurDirekt = (k == 2)`.

Das Bestands-Ereignis `kontextwort()` (normales Wort, R-1, Vorwort+Nachsatz) setzt `deckel = 1.0` und
`nurDirekt = false`.

**`nurDirekt` (D-3):** An jeder Stelle, an der der Bestand `direkt = false` setzt, während `aktiv` gilt (Füllwort,
ausgegebenes Segment, Zeilenumbruch), wird bei `nurDirekt` zusätzlich `aktiv = false`. Damit: genau die unmittelbar
folgende Nummer; Nachsatzwort (`I K-Nr.`, `I K Nr.`), `:`, `=` und Leerraum bleiben erlaubt; kein Füllwort, keine
Aufzählung, kein Umbruch.

**Deckel:** `ausgeben` berechnet die Bestandskonfidenz wie bisher und nimmt das Minimum mit dem Deckel (D-6). Für
Segmente bei `aktiv` ist der Deckel `deckel`, für nachgestellt ausgegebene Segmente 1,0 bzw. 0,60 (Komma). Die
Quelle folgt wie bisher **nur** der Prüfziffer/Länge (`ik`, `ik-pruefziffer-ungueltig`, `ik-laenge-abweichend`);
eine gültige IK unter `I K` hat also Konfidenz 0,50 **und** Quelle `ik`.

#### 3.2.4 Grenzfälle (je ein benannter Test)

| Text | Ergebnis | Grund |
|------|----------|-------|
| `L A N R N`, `L.A.N.R. N`, `L-A-N-R N`, `L.A-N R N`, NBSP/THIN/NNBSP/U+2011/U+2013 | 0,80 | D-4 |
| `L A N R001235…` (direkt an Ziffern) | 0,80 | Ziffer beendet die Folge vor der Kette |
| `L A N R-Nummer N`, `L A N R Nr. N` | 0,80 | Vorgriff: `Nummer` ist Nachsatzwort im direkten Kontext |
| `L A N R - N r N` | 0,80 | `lanr` (zwei Trenner beenden), danach `N`, `r` zwei Füllwörter |
| `N (L A N R)`, `N L A N R`, `N, L A N R` | 0,80 / 0,80 / 0,60 | Abbild der offenen Segmente |
| `I K N`, `I.K.: N`, `I-K N`, `I K-Nr. N`, `I.K.-Nr. N` | 0,50 | D-3 |
| `I K der Praxis N`, `I K`+Umbruch+`N`, `N (I K)`, `N I K`, `N, I K` | kein Fund | D-3 |
| `I K N und N2` | ein Fund (N) | Segment beendet den `nurDirekt`-Kontext |
| `A I K N`, `I K E A N`, `I K N N`, `L A N R X N`, `A L A N R N`, `L A N N` | kein Fund | maximale Folge |
| `I  K N`, `I<Tab>K N`, `I,K N`, `I/K N`, `I_K N`, `I. K. N` | kein Fund | kein bzw. doppelter Trenner |
| `I K EA N` | kein Fund | Folge `ik` endet vor `EA`; `EA` ist Füllwort und beendet D-3 |
| `IK: Praxis I K N` | 0,80 | Bestandskontext läuft noch (Kap. 3.2.3 Nr. 1) |
| `I K IK N` | 0,92 | normales `IK` setzt den Kontext neu |
| `Praxis I K N`, `Praxis-I.K. N`, `Rechnung an I.K. N gesendet` | 0,50 | Folge beginnt am Einzelbuchstaben |
| `L A N R` am Textende, `L A N R` allein | kein Fund, keine Ausnahme | |
| `L A N R L A N R N` | kein Fund | eine Folge `lanrlanr` (neue bekannte Grenze) |

### 3.3 Konfidenz, Stufe, Quelle (vollständig)

| Kontext | 9 Ziffern gültig | 9 Ziffern ungültig | 6–8, 10–12 Ziffern |
|---------|-----------------|--------------------|--------------------|
| normales KW, direkt | 0,95 / 0,92 sicher | 0,50 | 0,40 |
| normales KW, sonst (Füllwort, Umbruch, Aufzählung) | 0,80 | 0,50 | 0,40 |
| nachgestellt, Lücke ohne Komma | 0,80 | 0,50 | 0,40 |
| nachgestellt, genau ein `,`/`;` (D-5) | **0,60** fraglich | 0,50 | 0,40 |
| Sperrschrift ab 3 Buchstaben (D-4), jeder Zustand | **0,80** | 0,50 | 0,40 |
| Sperrschrift ab 3 Buchstaben, nachgestellt mit Komma | **0,60** | 0,50 | 0,40 |
| Sperrschrift 2 Buchstaben (D-3), nur direkt | **0,50** fraglich | 0,50 | 0,40 |

Quelle: `lanr`/`ik` bei gültiger Prüfziffer, sonst `…-pruefziffer-ungueltig` bzw. `…-laenge-abweichend` (Entscheidung
aus der Vorgabe: **keine neue Quellenkonstante**, AK-14).

### 3.4 Linearität (Fortschreibung des Javadoc-Belegs, AK-24, AK-29)

- Der Sperr-Verfolger macht je Zeichen außerhalb eines Laufs und je Lauf konstante Arbeit; er setzt die Leseposition
  der Hauptschleife nie zurück.
- Puffer fester Größe (23 Zeichen) plus Zähler; der Vergleich mit den Kontextwörtern findet einmal je Folgenende
  statt (höchstens 23 Zeichen, höchstens acht Einträge), also O(1) je Folge.
- Das Abbild der offenen Segmente sind sechs Zahlen bzw. Flags; Wiederherstellen ist O(1). Jedes offene Segment wird
  höchstens einmal ausgegeben: Nach dem Ereignis wird `offen` geleert, und ein späteres Abbild wird aus dem dann
  aktuellen (leeren oder neuen) Zustand genommen.
- Vorgriff `einzelbuchstabeVoraus`: nur am Anfang eines Laufs im Zustand `TRENNER`, liest höchstens diesen einen Lauf
  (bis zum zweiten Buchstaben oder Laufende); die Hauptschleife liest ihn danach noch einmal. Der bestehende Vorgriff
  `nachsatzFolgt` liest nur den Lauf hinter einem Vorwort-Lauf (mehrere Buchstaben, Zustand `KEINE`); beide Vorgriffe
  treffen nie denselben Lauf. Jedes Zeichen wird damit höchstens zweimal gelesen.
- Gesamtaufwand bleibt O(n), Zusatzspeicher O(1) plus Fundliste plus Folge der offenen Segmente.

### 3.5 Warum kein bisheriger Fundbereich verloren geht (Bestandsschutz-Argument)

1. **Segmente** hängen nicht vom Kontext ab (Kettenregel, R-3 nur über `zeilenanfang`): alt und neu zerlegen jeden
   Text in dieselben Segmente.
2. **D-5** gibt nur in Fällen aus, in denen der Bestand nichts ausgab (Lücke mit Komma), und ändert sonst keine
   Zustandsvariable.
3. **Sperr-Ereignis:** Es wirkt nur, wenn der Bestand gerade *nicht* `aktiv` ist. Ab dort kann der Bestand ein Segment
   nur noch ausgeben, (a) nach einem späteren normalen Kontextwort, das in alt und neu `aktiv`, `direkt`, `woerter`,
   `umbrueche` gleich setzt (und `deckel` zurücksetzt), oder (b) als offenes Segment bei einem solchen Kontextwort.
   Im Fall (b) hat der neue Durchlauf das Segment entweder ebenfalls offen oder es schon selbst ausgegeben.
   Offene Segmente gehen im neuen Durchlauf nur verloren, wenn sie vorher ausgegeben wurden.
4. **Konfidenz:** Bei Fall (b) ist die neue Konfidenz gleich (D-4: 0,80 wie nachgestellt) oder – nur bei `I K` – 0,50
   statt 0,80 (bekannte Folge 14g, Kap. 11). Sonst ist sie gleich oder neu vorhanden.

Der Differenztest (Kap. 4) prüft genau das: Jeder Fund des Vorgängers hat im neuen Ergebnis einen Fund mit gleichem
Bereich und Typ; die Konfidenz ist nicht kleiner, außer bei einem IK-Fund mit Quelle `ik` und Konfidenz 0,50.

---

## 4. Vergleich alt gegen neu: eingefrorene Kopie im Testcode

**Wahl:** `KvNumberScannerVorgaenger` = wortgleiche Kopie des heutigen `KvNumberScanner` (nur Klassenname und ein
Kopfvermerk im Javadoc geändert) im Testbaum, Paket `de.skillshield.core.recognizer.kv` (nutzt `KvArt` und
`KvCheckDigits` mit), angelegt in T-1 **vor** jeder Produktänderung und danach nie mehr geändert.

**Begründung gegen gespeicherte Erwartungen:** Gespeicherte Fundlisten decken nur feste Texte ab; der Vergleich
soll aber auch auf zufällig erzeugten Texten laufen (Fuzz 1c, Eigenschaftstests, jeder Text der KV-Testhelfer).
Gespeicherte Listen müssten bei jeder Korpuserweiterung neu erzeugt werden (Gefahr, einen Fehler „einzufrieren“) und
lägen als Dateien mit Positionen im Repository. Die Kopie kostet rund 680 Zeilen Testcode ohne Ziffernliterale.

**Absicherung der Kopie:**
- Fundbild-Siegel: ein Test bildet einen Hash über (Start, Ende, Typname, `Double.doubleToLongBits(Konfidenz)`, Quelle)
  aller Funde des Vorgängers (beide Arten) auf Fuzz-Teil 1 und Teil 1b und vergleicht ihn mit einer Konstante, die in
  T-1 gemessen wird, solange Produkt und Kopie gleich sind. Der Hash wird aus diesen Feldern selbst gebildet (nicht aus
  `hashCode()` von Objekten), damit er über Läufe stabil ist. In T-1 prüft ein Übergangstest zusätzlich, dass das
  Produkt auf denselben Texten **gleiche** Fundlisten liefert; er wird in T-2 entfernt (erster Task mit
  Verhaltensänderung).
- Damit das Siegel nicht durch einen geänderten Generator wandert, wird in T-1 auch Teil 1b per Hash festgehalten
  (`HASH_TEIL_1B`, wie `HASH_TEIL_1`).
- Hash-Konstanten als `int` in Hexadezimalschreibweise (höchstens acht Hexziffern): So entsteht nie eine Folge von
  neun Dezimalziffern, die `TestdatenSynthetischTest` als Verstoß melden würde.

**Differenzprüfungen:**
- Erkennerebene, je Art: (1) jeder Vorgänger-Fund hat einen neuen Fund mit gleichem Start, Ende, Typ; (2) neue
  Konfidenz ≥ alte, außer der neue Fund hat Quelle `ik` und Konfidenz 0,50. Läuft in `KvTestHilfen.scan` (also für
  jeden Text von `KvKontextTest`, `KvNachtragTest` und den neuen Tests) und in `KvVorgaengerDifferenzTest` über Fuzz 1,
  1b, 1c, Korpus und die Generatoren aus T-5. `laufzeit()` prüft nicht gegen den Vorgänger (Zeitmessung).
- Scanner-Ebene: `TextScanner` mit `[Email, Ipv4, Url, Path, KvVorgaenger.lanr(), KvVorgaenger.ik()]` gegen
  `TextScanner.standard()`: jede alt abgedeckte Position ist neu abgedeckt (Zusammenlegung darf Typ und Stufe ändern,
  Lesart AK-19 des Vorgängers).
- Selbsttest: Eine künstlich um einen Fund verkürzte bzw. in der Konfidenz gesenkte Liste wird von der Prüffunktion
  erkannt (die Prüfung läuft nicht ins Leere).

**Nachtrag 22.11.2026 (T-1, Durchlauf 2; Hinweis von security und reviewer):** Die Ausnahme in Prüfung (2) wird enger
gefasst als oben beschrieben. Sie greift nur, wenn der **alte** Fund die Quelle `ik` und eine Konfidenz ≤ 0,80 hat
(nie ein bisher „sicherer“ Fund) **und** der neue Fund die Quelle `ik` und die Konfidenz 0,50 hat. Der vorgesehene
Fall (Kap. 13 Hinweis 7, Grenze 14g) bleibt erlaubt; eine Senkung von 0,92 auf 0,50 oder ein Quellenwechsel wird
gemeldet. Zusätzlich erhält die Scanner-Ebene einen eigenen Selbsttest und eine Mindestzahl an Vorgänger-Funden.

---

## 5. Wiederverwendung

`KvNumberScanner` (Lauf, Fenster, `vergleichszeichen`, `istLeerraum`, `istUnsichtbar`, `istBuchstabe`, Konstanten für
NBSP/THIN/NNBSP/U+2011/U+2013, `kontextwort()`, `fuellwort()`, `offenLeeren()`), `KvArt` (Kontextwörter, Quellen),
`KvCheckDigits`, `Finding`, `ConfidenceLevel`, `TextScanner`, `ConflictResolver` (unverändert; gleicher Bereich →
stärkerer Kandidat), `Pseudonymizer`/`Reidentifier`. Testseitig: `SynthetischeKvNummern`, `KvTestHilfen`
(`Art`, `scan`, `assertFolge`, `laufzeit`, Wächter `neunerfenster`), `RoundtripFuzzTest` (Lauf, Klassen),
`KvDifferenzTest.korpus()`, `RoundtripKorpusTest.kvFaelle()`, `GrenzlastTest.drei`.

## 6. Neue Abhängigkeiten

Keine.

---

## 7. Sicherheits- und Datenschutzaspekte

- **Kein Netz, keine Datei, keine KI:** reine Zeichenverarbeitung in einer bestehenden Klasse; keine neue
  Produktivdatei, `ArchitekturRegelnTest` unverändert grün.
- **Kein Klartext:** keine neuen Ausnahmen oder Meldungen im Produkt; Quellen unverändert ohne Ziffern; Testmeldungen
  nennen Seed, Iteration, Anzahl, Fallname – nie Text oder Ziffern (auch nicht der Differenztest).
- **Im Zweifel schützen:** Beide Erweiterungen fügen nur Funde hinzu; der Bestandskontext gewinnt, wenn er schon läuft;
  der Vergleich mit der eingefrorenen Kopie belegt „kein Fundbereich geht verloren“.
- **Mensch entscheidet:** neue Funde sind „fraglich“ (0,50/0,60) oder „wahrscheinlich“ (0,80), nie „sicher“;
  ersetzt wird nur, was der Aufrufer übergibt (AK-27).
- **Fehlalarme begrenzt:** `I K` nur für die unmittelbar folgende Nummer (D-3); maximale Folge; genau ein Trenner.
- **Unsichtbare Zeichen:** Sperrschrift ist gegen Einstreuen unsichtbarer Zeichen invariant (Kap. 3.2.1), getestet als
  Eigenschaft.
- **Laufzeit:** linear (Kap. 3.4), harte 2-s-Grenzen mit exakter Fundzahl.
- **Testdaten:** nur `00`-Nummern aus `SynthetischeKvNummern`, Wächter vor jedem Scan; Hash-Konstanten hexadezimal.

---

## 8. Verbindliche Regeln für engineer und Prüfer

1. Alle Regeln aus Kap. 8 des Vorgängerplans gelten weiter (kein Regex im Produkt, benannte Tests je AK-Beispiel,
   Eigenschaftstests mit festem Seed und unabhängigem Orakel, keine Ausgabe, Zeilen ≤ 120 Zeichen).
2. **Sonderzeichen in neuem Code nur als `(char) 0x…`-Konstanten** (oder `zeichen(int)` aus `KvTestHilfen`), keine
   `\uXXXX`-Escapes (Schreibwerkzeuge beschädigen sie). Neue Konstanten in `KvTestHilfen`: `GESCH_BINDESTRICH =
   (char) 0x2011`, `HALBGEVIERT = (char) 0x2013`; vorhanden sind `NBSP`, `DUENN`, `SCHMAL_NBSP`, `ZWSP`, `SHY` u. a.
   Im Produkt existieren die Konstanten bereits.
3. Neunstellige Ziffernliterale nur in `SynthetischeKvNummern` (Präfix `00`); jeder Testtext geht vor dem Scan durch
   den Wächter; kein Text bildet zur Laufzeit eine gültige Nummer ohne `00`.
4. `KvNumberScannerVorgaenger`, `KvArt`, `KvCheckDigits` werden nach T-1 nicht mehr geändert.
5. Bestehende Tests werden **nur** an den in T-2 genannten Grenze-12-Stellen geändert (dazu die in T-1 genannte
   Zusatzprüfung in den Helfern und die in T-6/T-7 genannten Ergänzungen ohne geänderte Erwartung). Muss darüber hinaus
   eine bestehende Erwartung geändert werden, hält der engineer an und meldet Test, Fall und Grund (keine stille
   Anpassung). `HASH_TEIL_1` ändert sich in keinem Task (der Generator für Teil 1 wird nicht berührt; es gibt keinen
   Grund für eine Änderung).
6. In neuen Tests die Quelle aus der Nummer ableiten (`quelleNachNummer`, T-1), nicht aus der Konfidenz: eine gültige
   IK unter `I K` hat 0,50 und Quelle `ik`.
7. Jeder umgestellte bestehende Test wird im Bericht einzeln genannt (AK-22).

---

## 9. Tasks

Nach jedem Task ist `mvn verify` im Wurzelverzeichnis grün. Pfade unter `skillshield-core/src/…/de/skillshield/core/`.

### T-1: Vorgänger einfrieren, Differenzwerkzeug, Testhilfen

- **Ziel:** Das alte Verhalten bleibt dauerhaft vergleichbar; die Testhilfen können neue Quellen- und Zeichenfälle
  ausdrücken. Keine Produktänderung.
- **Dateien:** neu `test/…/recognizer/kv/KvNumberScannerVorgaenger.java` (Kopie, Kopfvermerk „eingefroren am Stand vor
  kv-kontext-luecken, nicht ändern“), neu `test/…/recognizer/kv/KvVorgaenger.java` (öffentlich, `static Recognizer
  lanr()`, `ik()`), neu `test/…/KvVorgaengerDifferenzTest.java`; geändert `test/…/recognizer/kv/KvTestHilfen.java`
  (Feld `vorgaenger` in `Art`; `scan` ruft `pruefeGegenVorgaenger`; `quelleNachNummer(String wert)`;
  `assertEin(…, konfidenz, quelle)`; Konstanten U+2011/U+2013), `test/…/RoundtripFuzzTest.java` (nur Konstante
  `HASH_TEIL_1B` + Test, wie `t7_teil1TextfolgeIstUnveraendert`).
- **AK:** AK-22 (Werkzeug), AK-25, AK-26.
- **Tests:** `vorgaenger_fundbildIstEingefroren` (Siegel, Kap. 4); `t1Uebergang_produktGleichVorgaenger` (Fuzz 1, 1b,
  `KvDifferenzTest.korpus()`: Fundlisten je Art gleich; wird in T-2 entfernt); Differenzprüfung Erkenner- und
  Scanner-Ebene über Fuzz 1, 1b, Korpus (alle grün, da noch gleich); Selbsttests der Prüffunktion (fehlender Fund,
  gesenkte Konfidenz, verschobener Bereich → erkannt; erlaubte Ausnahme `ik`/0,50 → nicht gemeldet);
  `t1_teil1bTextfolgeIstUnveraendert`.
- **Abnahme:** Diff der Kopie gegen `KvNumberScanner` zeigt nur Klassenname und Kopfvermerk; alle bestehenden Tests
  unverändert grün; Siegel- und Hash-Konstanten hexadezimal; kein `\u`-Escape in neuen Zeilen.

### T-2: Komma und Semikolon vor nachgestelltem Kontextwort (D-5, Grenze 12)

- **Ziel:** Kap. 3.1 umsetzen.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (Lücken-Merker, `offenAusgeben(deckel)`, Deckel in
  `ausgeben`, Konstante `CONFIDENCE_KOMMA_NACHGESTELLT`, Javadoc Abschnitte „Offene Segmente“ und „Konfidenz“),
  `LanrRecognizer.java`/`IkRecognizer.java` (Javadoc 0,60); neu `test/…/recognizer/kv/KvKommaTest.java`.
  **Umgestellt (Grenze 12, einzeln im Bericht):**
  1. `KvNachtragTest.r2_auslegung_kommaUndOderUndDirektVorDemKontextwort_keinFund`: die beiden Komma-Zeilen werden zu
     Funden (0,60); die Zeilen mit `und` und `/ (` bleiben „kein Fund“; Test umbenannt in
     `bekannteGrenze12Rest_undOderSchraegstrichDirektVorDemKontextwort_keinFund`, Komma-Fälle wandern nach `KvKommaTest`.
  2. `KvKontextTest.ak13_anderesZeichenZwischenZahlUndKontextwort_leertOffen_keinFund`: `,` und `;` aus der Liste
     nehmen (dort jetzt Fund 0,60, getestet in `KvKommaTest`); übrige Zeichen und `, 12, (` bleiben.
  3. `KvErkennungEigenschaftTest`, Negativblock X Fall 3, Variante `", "`: wird zur erwarteten Fundstelle mit
     Konfidenz nach D-5 (gültig 0,60, ungültig 0,50, Länge 0,40), außer das gezogene Kontextwort beginnt mit `Praxis`
     oder `Lebenslange` (Wort dazwischen → weiter kein Fund). Zufallsverbrauch unverändert.
  Entfernt: `t1Uebergang_produktGleichVorgaenger` (T-1).
- **AK:** AK-13 (Komma nie im Fund), AK-14, AK-15, AK-16, AK-17, AK-18, AK-20, AK-21 (Erkennerebene), AK-22
  (Umstellung), AK-24 (Erkennerebene g, h), AK-29 (Javadoc).
- **Tests (je Art, benannt nach AK):** alle Beispiele aus AK-14 bis AK-18 und AK-20; AK-21: LANR-Erkenner liefert für
  `IK N, LANR` einen Kandidaten 0,60; Gegenproben aus Kap. 3.1 (`N(, KW)`, `N,, KW`, `N,; KW`, `N, / KW`,
  `N, und KW`, `N, ((KW)`, `N,`+`\n`/`\r\n`/`\r`+`KW`); Lücke mit THIN/NBSP/Tab vor und nach dem Komma; `N , KW`;
  Kontextwort mit unsichtbarem Zeichen hinter dem Komma (`N, `+ZWSP+`KW` → 0,60); Laufzeit (Erkenner, exakte Zahl
  mit Formel): `N, ` wiederholt ohne KW → 0; mit `KW` am Ende → alle 0,60; `N, KW ` wiederholt → 1 × 0,60 und
  r−1 × direkt.
- **Abnahme:** genau die drei Umstellungen, sonst keine geänderte Erwartung; Differenzprüfung grün; Javadoc beschreibt
  die neue Lückenauswertung.

### T-3: Sperrschrift ab drei Buchstaben (D-1, D-2, D-4, D-6, D-7; Grenze 14 für LANR und längere IK-Wörter)

- **Ziel:** Sperr-Verfolger nach Kap. 3.2.1–3.2.3 mit Vorgriff, Abbild der offenen Segmente und Deckel 0,80. Folgen
  mit zwei Buchstaben lösen in diesem Task **kein** Ereignis aus (das folgt in T-4), damit `I K` weiter wie heute
  wirkt und der Build grün bleibt.
- **Dateien:** `main/…/KvNumberScanner.java` (Verfolger, `sperrKontextwort`, Konstante `CONFIDENCE_SPERRSCHRIFT`,
  `deckel`; Javadoc: Zeichenklassen Einzelbuchstabe/Sperr-Trenner/Sperrfolge, Ereignis, Konfidenz, Linearität nach
  Kap. 3.4), Javadoc `LanrRecognizer`/`IkRecognizer`; neu `test/…/recognizer/kv/KvSperrschriftTest.java`.
- **AK:** AK-1, AK-2, AK-3, AK-7, AK-8, AK-9 (LANR), AK-10 (LANR-Teil, gesperrtes Vorwort), AK-12, AK-13, AK-19
  (`N, L A N R`), AK-22, AK-24 (Erkennerebene), AK-29.
- **Tests:** jedes Beispiel aus AK-1, AK-2 (alle acht Trenner und gemischt), AK-3 (auch `I K N R`, `I K N U M M E R`,
  `I N S T I T U T I O N S K E N N Z E I C H E N` für IK), AK-7, AK-8, AK-9 (LANR), AK-10 (`L A N R-Nummer`; Grenze
  `A r z t - N r` → kein Fund), AK-12 (alle Fälle der Tabelle Kap. 3.2.1), AK-13 (Bereich, kein Punkt im Fund),
  AK-19 (LANR); Grenzfälle der Tabelle Kap. 3.2.4 mit drei und mehr Buchstaben; Groß-/Kleinschreibung gemischt
  (`l A n R`); Nicht-ASCII-Einzelbuchstaben und Kelvin-Zeichen in der Folge → kein Kontextwort; Folge mit 24+
  Buchstaben; `L A N R L A N R N` (bekannte Grenze). Laufzeit (Erkenner, exakte Zahl mit Formel): `L ` wiederholt
  (0), `I.` wiederholt (0), `L-A-N-R ` wiederholt (0, eine einzige Folge), `L A N R N ` wiederholt (je 0,80;
  Bausteinlänge 18, r = ⌈1 000 000 / 18⌉ = 55 556), `L` + 1 000 000 Leerzeichen (0), `L A N R` + 1 000 000
  Leerzeichen (0), `L` + `.A` wiederholt (0), Vorgriff über einen Lauf mit sehr vielen unsichtbaren Zeichen
  (`A ` + ZWSP × k + `Bc ` wiederholt → 0).
- **Abnahme:** keine bestehende Erwartung geändert; Differenzprüfung in `KvTestHilfen.scan` grün; Javadoc-Linearität
  nennt Puffer 23, Abbild O(1), Vorgriff „höchstens zweimal gelesen“.

### T-4: Zwei-Buchstaben-Wort `I K` (D-3)

- **Ziel:** Ereignis auch für zwei Buchstaben, mit `nurDirekt`, Deckel 0,50 und ohne nachgestellten Kontext; Regel
  „Bestandskontext läuft noch → nichts“ (Kap. 3.2.3 Nr. 1).
- **Dateien:** `main/…/KvNumberScanner.java` (`nurDirekt` in `fuellwort`, `segment`, `zeilenumbruch`; Konstante
  `CONFIDENCE_SPERRSCHRIFT_ZWEI`; Javadoc), Javadoc `IkRecognizer`; `test/…/recognizer/kv/KvSperrschriftTest.java`
  (ergänzt).
- **AK:** AK-4, AK-5, AK-6, AK-9 (IK), AK-10 (`I K-Nr.`, `I.K.-Nr.`), AK-11, AK-13, AK-19 (`N, I K`), AK-22, AK-24
  (Erkennerebene b, f), AK-29.
- **Tests:** jedes Beispiel aus AK-4 bis AK-6, AK-9 (IK), AK-10, AK-11, AK-19 (IK); Quelle `ik` bei 0,50 (gültig)
  gegen `ik-pruefziffer-ungueltig` bei 0,50 (ungültig); `IK: Praxis I K N` → 0,80; `I K IK N` → 0,92; `I K N (IK)` →
  0,50 (bekannte Folge 14g, Kap. 11); `I K`+ZWSP+`-Nr. N`; im LANR-Erkenner wirkt `I K` nicht. Laufzeit: `I K `
  wiederholt (0), `I ` und `K ` abwechselnd ohne Nummer (0), `I K N ` wiederholt (je 0,50; Bausteinlänge 14,
  r = ⌈1 000 000 / 14⌉ = 71 429).
- **Abnahme:** keine bestehende Erwartung geändert; Differenzprüfung grün (einzige Konfidenzsenkung: Quelle `ik`,
  0,50).

### T-5: Eigenschaftstests (AK-28) und Invarianz gegen unsichtbare Zeichen

- **Ziel:** Zufallsbelege mit unabhängigem Orakel.
- **Dateien:** neu `test/…/recognizer/kv/KvKontextLueckenEigenschaftTest.java`; `KvTestHilfen.java` (unabhängige
  Referenz `sperrKontextwoerterReferenz(String text, Art a)`: entfernt zuerst alle unsichtbaren Zeichen mit
  Indexabbildung und sucht dann einfache Folgen „Buchstabe, Trenner, Buchstabe …“; bewusst anders gebaut als das
  Produkt, Laufzeit egal).
- **AK:** AK-28 (a) bis (d), AK-22, AK-26 (Determinismus, Sortierung, Überlappungsfreiheit, Teilstring-Invariante).
- **Tests (je 1 000 Texte, neue feste Seeds, Meldungen nur Seed/Iteration):**
  (a) Blöcke „Sperr-Kontextwort (zufällige Trenner nach D-1, zufällige Groß-/Kleinschreibung, optional Punkt am Ende,
  optional Nachsatzwort) + Direktzeichen + Nummer (gültig, ungültig, Länge, mit Gruppentrennern)“ → genau diese Funde
  mit Konfidenz nach Kap. 3.3 und Quelle nach Nummer; (b) „Nummer + `,`/`;` + zufälliger Leerraum (ohne Umbruch) +
  optional `(`/`[` + Kontextwort“, auch Folgen `N1, N2, KW` → 0,60/0,50/0,40; (c) Rauschen mit zufälligen
  Einzelbuchstabenfolgen, Initialen (`I. K.`, `A. B.`), Nummern – jede Folge, die laut Referenz ein Kontextwort
  ergäbe, wird neu gezogen (Zähler belegt, dass das vorkommt) → kein Fund; (d) Gegenproben aus AK-6, AK-7, AK-18
  zufällig zusammengesetzt → kein Fund; (e, zusätzlich aus Kap. 3.2.1) in Texte aus (a) und (b) werden an zufälligen
  Stellen außerhalb der Nummern unsichtbare Zeichen eingestreut → gleiche Fundwerte und Konfidenzen (Positionen
  verschoben). Jeder Text zusätzlich: Differenzprüfung gegen den Vorgänger, Determinismus, Invarianten, Wächter.
  Mindestzählungen je Blockart, Trennerart, Stufe.
- **Abnahme:** Orakel aus der Konstruktion bzw. Referenz, nicht aus dem Produkt; keine Änderung an Produkt oder
  bestehenden Tests.

### T-6: Fuzz Teil 1c, Roundtrip-Korpus, Differenz auf Scanner-Ebene

- **Ziel:** Roundtrip und Schutzgarantie mit den neuen Formen über den Standard-Scanner.
- **Dateien:** `test/…/RoundtripFuzzTest.java` (Teil 1c: eigene Seeds, eigener Generator bzw. dritter Modus, der für
  Teil 1 und 1b dieselben Zufallszahlen verbraucht – belegt durch `HASH_TEIL_1` und `HASH_TEIL_1B`; Bausteine:
  Sperr-Kontextwörter beider Arten mit allen Trennern, `I K`/`I.K.`, `N, KW`, `N; (KW)`, `N1, N2, KW`, Gegenproben,
  Initialen; Klassen-Mindestzahlen `kv-komma-fund` (Konfidenz 0,60), `kv-sperr-fund`, `kv-sperr-zwei-fund`
  (IK, Quelle `ik`, 0,50); dieselben Eigenschaften wie 1b inkl. Schutzgarantie); `test/…/RoundtripKorpusTest.java`
  (KV-Fälle aus AK-1 bis AK-19 ergänzt, mit erwartetem pseudonymisiertem Text, z. B. `L A N R [[LANR_1]]`,
  `[[IK_1]], IK`; Gegenproben als `kein`); `test/…/KvVorgaengerDifferenzTest.java` (Fuzz 1c und erweiterter Korpus,
  Scanner-Ebene).
- **AK:** AK-22, AK-23, AK-26.
- **Tests:** Roundtrip zeichengleich, keine Ziffernkette ≥ 6 aus einem Fund im Ausgabetext, Schutzgarantie,
  Invarianten, Determinismus der Textfolge 1c; Differenz alt/neu auf Fuzz 1, 1b, 1c, Korpus (Erkenner- und
  Scanner-Ebene); `HASH_TEIL_1` und `HASH_TEIL_1B` unverändert.
- **Abnahme:** Teil 1b-Ergebnisse dürfen zusätzliche Funde zeigen (`N, KW`-Stellen aus `TRENNER_KV`), bestehende
  Mindestzahlen bleiben erfüllt; tritt dort ein Rückgang auf, hält der engineer an und meldet.

### T-7: Grenzlast, Klartextfreiheit, Mensch entscheidet, Abschluss der Dokumentation

- **Ziel:** AK-24 über die ganze Kette, AK-21 im Gesamtergebnis, AK-27, AK-26, AK-29 vollständig.
- **Dateien:** `test/…/GrenzlastTest.java` (neue parametrisierte Methode `ak24_kvKontextLuecken…` mit Erwartung je
  Stufe sicher/wahrscheinlich/fraglich und Typ; bestehende Methoden unverändert); `test/…/recognizer/
  TextScannerKvTest.java` (AK-21, AK-27); `test/…/KlartextFreiheitTest.java` (Funde aus `L A N R`+Marker,
  `I K`+Marker, Marker+`, IK`: `toString()` ohne Marker); `main/…/KvNumberScanner.java` nur Javadoc (Liste der
  bekannten Grenzen: 12 und 14 „geschlossen, siehe kv-kontext-luecken“, Restgrenzen Kap. 11); Tests
  `bekannteGrenze…` für jede Restgrenze in `KvSperrschriftTest`/`KvKommaTest`, soweit nicht schon in T-2 bis T-4.
- **AK:** AK-21, AK-24, AK-26, AK-27, AK-29.
- **Tests:** Grenzlast (je 1 000 000 Zeichen, Scan + Pseudonymisierung + Rückübersetzung je < 2 s nach Aufwärmlauf,
  Wächter, exakte Fundzahl mit Formel im Kommentar, Roundtrip): (a) `L ` → 0; (b) `I K ` → 0; (c) `I.` → 0;
  (d) `L-A-N-R ` → 0; (e) `L A N R N ` → r wahrscheinlich, Typ LANR, r = 55 556 > 50 000; (f) `I K N ` → r fraglich,
  Typ IK; (g) `N, ` ohne KW → 0, mit `IK` am Ende → r fraglich (0,60); (h) `N, IK ` → 1 fraglich + (r−1) sicher;
  (i) `L` + Leerzeichen, `L A N R` + Leerzeichen, `L` + `.A` → 0. AK-21: `IK N, LANR` im Gesamtergebnis genau ein Fund
  Typ IK 0,92 über N. AK-27: `I K N` und `N, IK` → Fund vorhanden; Pseudonymisierung mit leerer Liste → Text
  unverändert.
- **Abnahme:** alle bekannten Grenzen aus Kap. 11 haben einen Test; Javadoc von Scanner und Erkennern nennt alle
  Konfidenzen aus Kap. 3.3.

---

## 10. Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-3 | AK-11 | T-4 | AK-21 | T-2, T-7 |
| AK-2 | T-3 | AK-12 | T-3 | AK-22 | T-1 bis T-6 |
| AK-3 | T-3 | AK-13 | T-2, T-3, T-4 | AK-23 | T-6 |
| AK-4 | T-4 | AK-14 | T-2 | AK-24 | T-2, T-3, T-4, T-7 |
| AK-5 | T-4 | AK-15 | T-2 | AK-25 | T-1 (und jeder Task) |
| AK-6 | T-4 | AK-16 | T-2 | AK-26 | T-1, T-5, T-6, T-7 |
| AK-7 | T-3 | AK-17 | T-2 | AK-27 | T-7 |
| AK-8 | T-3 | AK-18 | T-2 | AK-28 | T-5 |
| AK-9 | T-3, T-4 | AK-19 | T-3, T-4 | AK-29 | T-2, T-3, T-4, T-7 |
| AK-10 | T-3, T-4 | AK-20 | T-2 | | |

---

## 11. Bekannte Grenzen

**Geschlossen:** 12 (Komma/Semikolon) und 14 (Sperrschrift) des Vorgängers, in den Grenzen von D-1 bis D-5.

**Neu bzw. verbleibend (je ein Test `bekannteGrenze…`):**

- 12R. `und` und `/` vor dem nachgestellten Kontextwort (`N und KW`, `N / KW`, `N, und KW`) → kein Fund (D-5).
- 14a. Teilgruppen: `LA NR`, `L ANR`, auch über unsichtbare Zeichen gebildet (`L`+ZWSP+`A N R` = `LA N R`).
- 14b. Gesperrtes Vorwort und gesperrtes Nachsatzwort (`A r z t - N r`). Hinweis: `L A N R - N r N` ergibt trotzdem
  einen Fund 0,80, weil `N`, `r` als Füllwörter zählen.
- 14c. Initialenform mit Punkt und Leerzeichen (`I. K. N`, `L. A. N. R. N`), Tab, Zeilenumbruch, zwei Trenner →
  keine Sperrschrift (D-1).
- 14d. Unmittelbar wiederholte Sperrschrift (`L A N R L A N R N`) ist eine einzige Folge → kein Kontextwort.
- 14e. Einzelbuchstabe direkt vor der Folge (`Herr A I K N`, `X Y I K N`) → maximale Folge ist kein Kontextwort.
- 14f. `I K` ohne Füllwörter, Aufzählung, Umbruch, nachgestellten Kontext (D-3, bewusst).
- 14g. Bekannte Folge: Eine Nummer, die von `I K` direkt und zugleich von einem nachgestellten Kontextwort erschlossen
  wird (`I K N (IK)`), hat 0,50 statt bisher 0,80. Abdeckung unverändert.
- Alle übrigen Grenzen des Vorgängers (1–11, 13) unverändert.

## 12. Risiken

- **Zustandskomplexität:** zweiter Automat neben dem Bestand. Gegenmittel: Ereignis nur additiv, Bestand gewinnt bei
  laufendem Kontext, Differenzprüfung in jedem KV-Testtext, Eigenschaftstests mit unabhängiger Referenz.
- **Vorgriff:** falscher Laufbegriff im Vorgriff würde Nachsatzwörter verfehlen. Gegenmittel: Tests `I K-Nr.`,
  `I K`+ZWSP+`-Nr.`, `L A N R-Nummer`, `I K EA N`; Laufzeittest mit langen unsichtbaren Läufen.
- **Fehlalarme `I K`:** in Fließtext mit Einzelbuchstaben (Aufzählungen „Teil I K…“) möglich, aber nur für die direkt
  folgende Nummer mit 6–12 Ziffern und nur „fraglich“.
- **Typwechsel im Gesamtergebnis:** D-5 erzeugt LANR-Kandidaten 0,60 über Nummern hinter `IK …` (`IK N, LANR M`).
  Bei ungültiger IK (0,50) kann der Typ auf LANR wechseln; Abdeckung bleibt (Konfliktregel unverändert).
- **Fuzz Teil 1b:** zusätzliche Funde ändern Klassenzählungen; Mindestzahlen haben Spielraum, Rückgang wird gemeldet.
- **Drift der Kopie:** durch Siegel und Regel „nicht ändern“ abgedeckt; `KvArt`/`KvCheckDigits` bleiben unverändert.
- **Escapes in Quelltexten:** durch Regel 8.2 vermieden.

---

## 13. Hinweise zur Anforderung (mit Lösungsvorschlag, Richtung „möglichst viel schützen“)

1. **0,50 bei gültiger IK (AK-4) ist derselbe Wert wie „Prüfziffer ungültig“.** Unterscheidbar nur über die Quelle
   (`ik` gegen `ik-pruefziffer-ungueltig`). Vorschlag: so umsetzen (AK-9 „Quellen wie im Bestand“); Tests leiten die
   Quelle aus der Nummer ab (Regel 8.6). Für die spätere Oberfläche vormerken: Quelle anzeigen oder „fraglich“ nicht
   standardmäßig abwählen.
2. **AK-12 „ein Fund ist erlaubt“** (`L`+ZWSP+` A N R`): umgesetzt als **Pflicht**-Fund mit 0,80.
3. **D-2 „unsichtbare Zeichen werden übergangen“** wird wörtlich als „Einzelbuchstabe = Lauf mit genau einem
   Buchstaben“ umgesetzt. Folge: `L`+ZWSP+`A N R` ist eine Teilgruppe wie `LA N R` (kein Fund, Grenze 14a). Vorteil:
   Die Erkennung ist gegen Einstreuen unsichtbarer Zeichen invariant (keine neue Lücke). Eine weitergehende Lösung auf
   Teilstück-Ebene wäre möglich, bräuchte aber ein Kontextwort-Ereignis mitten in einem Lauf; nicht empfohlen.
4. **AK-6 „`I. K. Mustermann` … erst nach mehr als einem Wort“** legt nahe, `I. K.` sei ein Kontextwort. Nach D-1
   (zwei Trenner) ist es keines; auch `I. K. N` liefert keinen Fund (Grenze 14c). Vorschlag: D-1 beibehalten, weil
   der Product Owner Doppeltrenner wegen Initialen ausdrücklich ausgeschlossen hat; AK-6 ist damit erfüllt.
5. **D-4 „ab drei Buchstaben“:** Es gibt kein Kontextwort mit drei Buchstaben; praktisch gilt D-4 ab vier. Umgesetzt
   als `k >= 3`.
6. **Bestandskontext gegen `I K`:** Läuft bereits ein normaler Kontext (`IK: Praxis I K N`), bleibt dessen Konfidenz
   (0,80) – der Deckel aus D-3 gilt nur für einen Kontext, den `I K` selbst eröffnet. Das ist mit D-6 vereinbar und
   schützt nicht schlechter.
7. **`I K N (IK)`:** Die Nummer wird von `I K` verbraucht und hat 0,50 statt bisher 0,80 (Grenze 14g). Abdeckung
   bleibt (AK-22 verlangt Abdeckung). Eine Lösung ohne Senkung bräuchte das Zurückhalten des Fundes bis zum nächsten
   Ereignis; nicht empfohlen (Aufwand, Risiko) für einen konstruierten Fall.
8. **AK-22 „Tests, die Grenze 14 festhalten“:** Im Bestand gibt es keinen solchen Test (Nachtrag T-9 Punkt 2 des
   Vorgängers: „bisher ohne Test“; Suche bestätigt). Umzustellen sind nur die drei Grenze-12-Stellen aus T-2.
9. **AK-24 (i)** ist zweideutig (`L A N R` + viele Trenner bzw. `L` + 1 000 000 Leerzeichen): beide Varianten werden
   getestet. Der Vorgriff liest einen Lauf ein zweites Mal (wie der bestehende Nachsatz-Vorgriff); die Hauptleseposition
   wird nie zurückgesetzt. Das erfüllt die Forderung „kein Zurücksetzen über eine feste Zahl Zeichen hinaus“ im Sinne
   des Bestands; die Einzelbuchstabenkette selbst nutzt einen Puffer fester Größe.
10. **AK-21:** Der LANR-Kandidat entsteht schon auf Erkennerebene (T-2); im Gesamtergebnis gewinnt IK 0,92 nach der
    bestehenden Regel.

## 14. Abweichungen von Plan.md

Keine. (Kein neues Modul, keine neue Abhängigkeit, keine Änderung an Pseudonymisierung, Rückübersetzung,
Konfliktauflösung; Konfidenzstufen nach Plan Kap. 6.)
