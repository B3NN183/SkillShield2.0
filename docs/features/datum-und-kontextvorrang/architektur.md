# Architektur: Datumsangaben sind keine KV-Nummern, das nächste Kontextwort bestimmt den Typ

Feature-Kurzname: `datum-und-kontextvorrang`. Grundlage: `anforderung.md` (D-1 bis D-14, AK-1 bis AK-33, OF-1 bis
OF-6), `Plan.md` Kap. 2, 6, 11, 13, 14 und der Code (Stand nach `oberflaeche-mvp`). Betroffen ist nur
`skillshield-core`.

## 0. Zusammenfassung in normaler Sprache

- **Teil A (Datum):** Der gemeinsame KV-Scanner gibt ein Segment nicht mehr aus, wenn es genau ein gültiges Datum
  (oder der Anfang eines Zeitraums aus zwei Daten) ist. Alles andere am Scanner bleibt, wie es ist: Segmentierung,
  Zustand (direkt, Füllwörter, Aufzählung, offene Segmente) und alle anderen Funde. Eine kleine neue Klasse
  `KvDatum` prüft Form und Kalender ohne regulären Ausdruck in konstanter Zeit je Segment.
- **Teil B (Kontextvorrang):** Die vier Arten laufen weiter getrennt. Der Scanner meldet zusätzlich (nur auf eigene
  Anfrage), welche seiner Funde „direkt“ hinter einem Kontextwort standen. Im `TextScanner` entfernt eine neue
  Nachbearbeitung `KvKontextvorrang` unmittelbar vor der Konfliktauflösung jeden KV-Kandidaten, der nicht direkt ist
  und vollständig im direkten Kandidaten einer anderen Art liegt (Formularfeld `BSNR/LANR` ausgenommen). Der
  `ConflictResolver` bleibt unverändert; die Abdeckung ändert sich dadurch beweisbar nicht, nur der Typ.
- **Erster Task:** Einfrieren des heutigen Stands als vierte Kopie (alle vier Arten), unabhängige Referenzen für
  Datum und Vorrang, Prognose, welche Bestandstests sich ändern, und ein Differenztest, der in T-1 volle Gleichheit
  verlangt und in Teil A bzw. Teil B je genau eine benannte Abweichungsklasse zulässt.

## 1. Lösungsansatz

### 1.1 Teil A: Ort der Datumsprüfung (OF-1)

Jede Ausgabe eines Segments läuft durch genau eine Methode: `KvNumberScanner.Lauf.ausgeben(start, end, anzahl,
mitDirektemKontext, deckel)`. Sie wird von `segment()` (Kontext aktiv: direkt, weiterer Kontext, Aufzählung) und von
`offenAusgeben()` (nachgestellter Kontext, auch Komma-Regel, Sperr- und Weit-Ereignis) gerufen. Die Zustandsänderungen
(`direktVerloren()`, `woerter = 0`, `umbrueche = 0`, Leeren der offenen Segmente) geschehen ausschließlich in den
Aufrufern. Deshalb erfüllt eine einzige Zeile am Anfang von `ausgeben` die Entscheidung D-4 vollständig:

```java
if (anzahl != NEUN && KvDatum.istDatumssegment(text, start, end)) {
    return; // D-1, D-4: nur die Ausgabe entfaellt, der Zustand ist im Aufrufer schon wie bisher gesetzt
}
```

`KvDatum.istDatumssegment` ist `true`, wenn

1. der Ausschnitt `text[start, end)` aus genau drei Ziffernblöcken mit zweimal demselben Trenner besteht und eine
   Form nach D-2 (a) bis (d) hat: `.` mit Tag/Monat ein- oder zweistellig und Jahr vier- oder zweistellig (zweistellig
   nur mit `.`); `/` oder `-` mit Tag/Monat ein- oder zweistellig und Jahr vierstellig; `-` mit Jahr vierstellig
   zuerst, Monat und Tag je zweistellig (ISO); und der Kalender nach D-3 passt; **oder**
2. ab `start` ein Zeitraum nach D-2 (e) steht: Datum der Form (a) mit vierstelligem Jahr, genau ein `-`, zweites Datum
   der Form (a), beide gültig, `end` liegt hinter dem `-` und höchstens am Ende des zweiten Datums, und hinter dem
   zweiten Datum geht die Kette nicht weiter (keine Ziffer, kein Gruppentrenner mit folgender Ziffer).

Ein KVNR-Segment mit Buchstaben beginnt nie mit einer Ziffer und ist damit nie ein Datum (`B 14.03.1958` bleibt Fund,
D-2). Ein Segment mit genau neun Ziffern wird nie geprüft (A-2).

Kalender (D-3): Monat 1 bis 12, Tag 1 bis Monatslänge, 29.02. nur im Schaltjahr (gregorianisch). Vierstelliges Jahr
`KvDatum.JAHR_MIN = 1900` bis `KvDatum.JAHR_MAX = 2099` (pflegbare Konstanten, OF-1). Zweistelliges Jahr 00 bis 99;
**Schaltjahr bei zweistelligem Jahr nur, wenn es in beiden Jahrhunderten eines ist** (`jj % 4 == 0` und `jj != 0`),
also ist `29.02.00` kein Datum (1900 ist kein Schaltjahr; im Zweifel Schutz, Kap. 13 Punkt 7).

Aufwand: Ein Segment hat höchstens 12 Ziffern und 11 Trenner; der Zeitraum-Vorgriff liest hinter `end` höchstens den
Rest des zweiten Datums (`.JJJJ`, fünf Zeichen) und zwei Zeichen für das Kettenende. Konstante Arbeit je Segment,
Segmente überlappen nicht, also O(n) gesamt, ohne regulären Ausdruck, ohne Zurücksetzen der Leseposition der
Hauptschleife (der Vorgriff ist ein lokaler Index). Linearitätsbeleg als Absatz im Javadoc von `KvNumberScanner`.

### 1.2 Teil A: Zerlegung „Datum zuerst“ (OF-2) – Entscheidung: nein, D-5 gilt; Zeitraum ja, ohne Zerlegung

- `14.03.1958 1234`, `14.03.1958 08.30`: Heute ist das Segment die ganze Kette (12 Ziffern). Eine Zerlegung in
  „Datum + Rest“ ließe den Rest (`1234`, `08.30`, unter sechs Ziffern) ohne Fund; diese Positionen sind heute
  geschützt. Das wäre Schutzverlust außerhalb eines Datums und verletzt die harte Randbedingung. Zusätzlich müsste die
  Präfixregel für alle Arten geändert werden. **D-5 gilt, AK-10 bleibt unverändert, die bekannte Grenze bleibt.**
- **Zeitraum** `01.03.2026-31.03.2026`: Die Kette hat 16 Ziffern; der Scanner wählt heute das 12-stellige Präfix
  `01.03.2026-31.03` (Fund 0,40), danach wird `.2026` als Punkt und Vierer-Block gelesen (kein Fund). Die Segmentierung
  bleibt unverändert; erkannt wird der Zeitraum über die Kette ab Segmentanfang (Regel 2 in 1.1, fester Vorgriff).
  Rechnerisch ist der Rest hinter dem Segment bei jeder Zeitraum-Form höchstens die Jahreszahl (vier Ziffern), also nie
  ein Segment; es entsteht damit für den ganzen Zeitraum kein Fund und kein neuer Zustand.
- **Grenze aus A-2 (neu benannt):** Erreichen die Präfixe eines Zeitraums genau neun Ziffern (zum Beispiel
  `01.03.2026-1.3.2026`, `1.3.2026-15.3.2026`: 8+1 bzw. 6+2+1), gilt die Neun-Ziffern-Regel unverändert; der Zeitraum
  bleibt teilweise Fund. Ebenso ein Datum mit genau einer angehängten Ziffer über einen Gruppentrenner
  (`14.03.1958 1`). Je ein Test `bekannteGrenze…`.
- **Lesart von D-5 (zur Kenntnis):** Maßgeblich ist allein das vom Scanner gewählte Segment. Nachbarn, die die
  Präfixregel ohnehin abtrennt, spielen keine Rolle. Das ist für AK-8 nötig: `BSNR Datum <BSNR>` (die Kette
  `Datum<Leerzeichen><9 Ziffern>` ist länger als 12 Ziffern, das Segment ist genau das Datum) und `BSNR <BSNR> Datum`
  (das Datum beginnt hinter dem Neuner-Segment derselben Kette). Folge: In `14.03.1958 123456` ist das Datum frei,
  `123456` bleibt Fund (wie heute).

### 1.3 Teil B: Ort und Form des Vorrangs (OF-3)

Geprüft wurden drei Wege:

| Weg | Bewertung |
|-----|-----------|
| Vorrang in jedem KV-Erkenner (jeder Erkenner rechnet die direkten Funde der drei anderen Arten mit) | verworfen: 16 statt 4 Scannerläufe; ein Erkenner würde Kandidaten verwerfen in der Annahme, ein anderer Erkenner liefere die Abdeckung – in einer `TextScanner`-Liste ohne diesen Erkenner wäre das Schutzverlust; alle Erkennertests änderten sich |
| Gemeinsamer Vorlauf, der je Position das nächste Kontextwort aller Arten bestimmt | verworfen: neue Zustandsverflechtung zwischen den Arten, eigener zweiter Begriff von „direkt“ neben dem des Scanners (widerspricht A-3) |
| **Nachbearbeitung im `TextScanner` mit dem `direkt`-Merker des Scanners** | **gewählt**: kleinste Änderung, ein Begriff von „direkt“, Abdeckung beweisbar unverändert, Erkenner liefern unverändert |

**Regel (D-8, D-9, D-11, D-12, D-14).** Sei K die Menge der Kandidaten der vier Produkt-Erkenner `LanrRecognizer`,
`IkRecognizer`, `KvnrRecognizer`, `BsnrRecognizer` nach dem Zuschneiden am Bestand (also so, wie sie heute in die
letzte Konfliktauflösung gehen, einschließlich Restfunden). Ein Kandidat `d` ist **direkt**, wenn der Scanner ihn mit
`mitDirektemKontext == true` ausgegeben hat und er unverändert (gleicher Bereich, gleiche Quelle, kein Restfund) in K
steht. Ein Kandidat `c` aus K entfällt genau dann, wenn

1. `c` nicht direkt ist, und
2. es einen direkten Kandidaten `d` einer **anderen** Art gibt mit `d.start <= c.start` und `c.end <= d.end` (D-9), und
3. nicht die Formularfeld-Ausnahme gilt (D-11): Das Kontextwort von `d` steht unmittelbar hinter einem `/`, und
   unmittelbar vor dem `/` steht ein Kontextwort der Art von `c` (`BSNR/LANR`, `LANR/BSNR`, jede Paarung).

Alle übrigen Kandidaten und alle Kandidaten anderer Erkenner (E-Mail, IP, URL, Pfad, auch eingefrorene Kopien in
Tests) bleiben unverändert. Danach läuft die bestehende Konfliktauflösung.

**Warum kein Schutzverlust (AK-23).** Ein entfernter Kandidat `c` liegt ganz in `d`; `d` bleibt in der Liste; der
`ConflictResolver` nimmt nie eine Position eines Kandidaten aus dem Ergebnis (Schutzgarantie seines Javadoc). Also ist
die Vereinigung der Positionen vor und nach dem Entfernen gleich. Die Abdeckung ist in Teil B nicht nur Obermenge,
sondern **gleich**; der Differenztest prüft Gleichheit.

**„Direkt“ (A-3).** Verwendet wird der bestehende Zustand `direkt` des Scanners, also wie `ausgeben(...,
mitDirektemKontext = direkt, ...)` ihn heute schon für die Konfidenz nutzt. Er gilt nach Kontextwort, Vorwort mit
Nachsatzwort, gesperrtem Kontextwort ab drei Buchstaben (`L A N R`), Weit-Kontextwort (`L. A. N. R.`), dem
Zwei-Buchstaben-Wort `I K` und nach einem nachgestellten Kontextwort für die folgende Nummer. Er endet mit Füllwort,
ausgegebenem Segment, Zeilenumbruch und Satzende. Ein Kontextwort einer anderen Art ist im Lauf der Art ein Füllwort;
damit ist „direkt bei Art X“ gleichbedeutend mit „das letzte Wort vor der Nummer ist ein Kontextwort von X“.

**Nachgestellter Kontext und Aufzählung (D-12).** Funde, die nur über Aufzählung oder nachgestellten Kontext
entstehen, sind nie direkt; ohne direkten Kandidaten einer anderen Art bleibt alles wie heute (AK-18, AK-19). Steht
eine Nummer direkt hinter dem Kontextwort von X **und** vor einem nachgestellten Kontextwort von Y (`IK N, LANR`),
gewinnt X (Wortlaut D-8: „das nächste Kontextwort **vor** der Nummer“). Das ändert Ergebnisse, die Vorgänger
ausdrücklich festgelegt hatten; siehe Kap. 8.2 und Kap. 13 Punkt 1.

**Formularfeld (D-11) im Scanner.** Beim Erkennen eines Kontextworts (Wort oder Vorwort mit Nachsatzwort, nicht
gesperrt, nicht weit), das als erstes Teilstück am Anfang seines Laufs steht, merkt der Lauf die Position eines `/`,
das unmittelbar vor dem Laufanfang steht und vor dem unmittelbar ein Buchstabe steht (sonst `-1`). Der Merker wird mit
jedem Kontextbeginn neu gesetzt und je direktem Fund mitgegeben. `KvKontextvorrang` liest dann rückwärts ab dem `/`
höchstens `MAX_KONTEXTWORT + 1` Buchstaben (ASCII und Umlaut A, gefaltet wie im Scanner), verlangt davor eine
Wortgrenze und prüft, ob das Wort in `kontextwoerter()` der Art von `c` steht. Konstanter Aufwand je direktem Fund.

**Kosten.** Ohne Kandidatenpaar „andere Art, einer liegt im anderen“ (Vorprüfung über die nach Start sortierten
Kandidaten, O(k log k)) wird nichts nachgerechnet; das ist der Normalfall. Sonst läuft der Scanner für jede beteiligte
Art (höchstens vier) ein zweites Mal im Aufzeichnungsmodus, O(n) je Art. Je Kandidat eine Suche über die direkten
Kandidaten der anderen Arten (nach Start sortiert, Kandidaten einer Art überlappen nicht), O(k log k). Gesamt O(n +
k log k), Zusatzspeicher O(k). Keine neue Zustandsabhängigkeit zwischen den Arten; Kopplung nur über den
aufgezeichneten Merker.

**Wer gehört dazu.** Nur Kandidaten der vier Produktklassen (alle `final`) werden betrachtet, erkannt per
`instanceof` in `KvKontextvorrang.istKvErkenner(Recognizer)`. Folge: Scanner mit eingefrorenen Kopien
(`KvnrBsnrHilfen.ALT_TRENNER` u. a.) verhalten sich unverändert, alle bestehenden Siegel über deren Ausgabe bleiben
gültig; `new TextScanner(standard().recognizers())` in beliebiger Reihenfolge verhält sich wie `standard()`.

### 1.4 Quelle bei Typwechsel (OF-5)

Der Vorrang entfernt nur Kandidaten; der verbleibende Kandidat der nahen Art geht unverändert in die
Konfliktauflösung. Bei gleichem Bereich ist er danach der einzige maximale Kandidat seiner Gruppe und wird **unverändert
übernommen**: Typ, Konfidenz, Stufe und Quelle der nahen Art (D-10), nicht `zusammengelegt`. `zusammengelegt` entsteht
nur wie heute, wenn ein dritter Kandidat (z. B. Pfad, URL) teilweise überlappt; dann wirkt die bisherige Regel
(Typ des stärksten, Konfidenz des schwächsten maximalen Kandidaten). Solche Fälle zählt und nennt der Differenztest
gesondert (AK-22, AK-24); erwartet sind 0.

### 1.5 Zerlegter Kontext (OF-6)

Am Code geprüft: `sperrEnde()` (ab drei Buchstaben) und `weitEnde()` rufen `kontextBeginnen(...)`, das `direkt = true`
setzt; `I K` ebenfalls (`kontextBeginnen(CONFIDENCE_SPERRSCHRIFT_ZWEI, true)`). Damit gilt D-8 für `L A N R`,
`L.A.N.R.`, `L. A. N. R.` und `I K` ohne Sonderregel; AK-15 ist erfüllbar. **Grenze:** `I K` bei schon laufendem
Kontext einer Art (Grenze 14h, `IK: Praxis I K Nr N`) setzt `direkt` bewusst nicht (`direktNachIk`); dort gibt `I K`
keinen Vorrang. Ebenso gilt die Formularfeld-Ausnahme nur für ungesperrte Kontextwörter direkt am `/`
(`B S N R/LANR`, `Betriebsstätten-Nr./LANR` und Lücken am `/` bekommen den Vorrang nach D-8). Je ein Test
`bekannteGrenze…`.

## 2. Betroffene Module, Pakete, Typen

Nur `skillshield-core`. Keine Änderung an `Finding`, `FindingType`, `Recognizer`, `ConflictResolver`, `Pseudonymizer`,
`Reidentifier`, `PlaceholderSyntax`, an den Signaturen der vier Erkenner und an `TextScanner.standard()`.

### 2.1 Produktivdateien

| Datei | Teil | Änderung |
|-------|------|----------|
| `recognizer/kv/KvDatum.java` (neu, paketprivat) | A | Datumsprüfung nach 1.1 |
| `recognizer/kv/KvNumberScanner.java` | A | eine Zeile in `ausgeben`; Javadoc (Regel 1, Grenzen, Linearität) |
| `recognizer/kv/KvNumberScanner.java` | B | Aufzeichnung direkter Funde, Merker `/` vor dem Kontextwort, `direkteFunde(...)`; Javadoc |
| `recognizer/kv/KvKontextvorrang.java` (neu, `public final`) | B | Nachbearbeitung nach 1.3 |
| `recognizer/TextScanner.java` | B | Herkunft der Kandidaten merken, `KvKontextvorrang.anwenden` vor der letzten Auflösung; Javadoc |
| `recognizer/kv/KvArt.java`, `LanrRecognizer.java`, `IkRecognizer.java`, `KvnrRecognizer.java`, `BsnrRecognizer.java` | A, B | nur Javadoc (AK-33) |

### 2.2 Signaturskizzen

```java
package de.skillshield.core.recognizer.kv;

/** Datumsform und Kalender nach D-2, D-3, D-5 (paketprivat, zustandslos, ohne regulaeren Ausdruck). */
final class KvDatum {
    /** Kleinstes und groesstes vierstelliges Jahr (D-3). */
    static final int JAHR_MIN = 1900;
    static final int JAHR_MAX = 2099;

    private KvDatum() {
    }

    /**
     * Ist das Segment [start, end) ein Datum nach D-2 (a) bis (d) und D-3, oder beginnt dort ein Zeitraum nach
     * D-2 (e), dessen zweites Datum die Kette beendet? Konstanter Aufwand; liest hinter end hoechstens sieben Zeichen.
     */
    static boolean istDatumssegment(String text, int start, int end) { ... }
}
```

```java
// in KvNumberScanner (paketprivat)

/** Ein mit direktem Kontext ausgegebener Fund (Teil B); schraeg: Position des '/' vor dem Kontextwort, sonst -1. */
record DirektFund(int start, int end, String quelle, int schraeg) {
}

/** Wie detect, liefert aber nur die direkt ausgegebenen Funde (Aufzeichnungsmodus; detect bleibt unveraendert). */
static List<DirektFund> direkteFunde(KvArt art, String text) { ... }
```

`DirektFund.toString()` enthält nur Zahlen und die Quelle (kein Textausschnitt); der Record verlässt das Paket nie.

```java
package de.skillshield.core.recognizer.kv;

/**
 * Kontextvorrang (D-8 bis D-14) fuer den TextScanner. Einzige oeffentliche Ergaenzung des Features; nur fuer
 * TextScanner bestimmt. Wirft nur NullPointerException mit festen Meldungen; nennt nie Text oder Ziffern.
 */
public final class KvKontextvorrang {
    private KvKontextvorrang() {
    }

    /** true genau fuer LanrRecognizer, IkRecognizer, KvnrRecognizer und BsnrRecognizer des Produkts. */
    public static boolean istKvErkenner(Recognizer erkenner) { ... }

    /**
     * Entfernt nach 1.3 die Kandidaten ferner Arten. kandidaten: alle Kandidaten der Erkenner mit istKvErkenner,
     * nach dem Zuschneiden am Bestand. Liefert eine neue Liste; ohne betroffenes Paar dieselben Elemente.
     */
    public static List<Finding> anwenden(String text, List<Finding> kandidaten) { ... }
}
```

`TextScanner.scan` (Skizze, Ablauf sonst unverändert): Kandidaten werden beim Einsammeln nach
`KvKontextvorrang.istKvErkenner(r)` in „KV“ und „übrige“ getrennt; die Bestandsbildung (`Bestand.von(resolve(...))`)
und das Zuschneiden laufen unverändert über dieselben Kandidaten wie heute; Restfunde eines KV-Erkenners zählen zu
„KV“. Zum Schluss `ConflictResolver.resolve(text, übrige + KvKontextvorrang.anwenden(text, kv))`.

## 3. Wiederverwendung

- `KvNumberScanner` (Segmentierung, Zustand `direkt`, Quellen, Konfidenzen), `KvArt` (Typ, Kontextwörter, Quellen),
  `KvnrOhneKontext` (unverändert), `TextScanner.Bestand`/Zuschneiden, `ConflictResolver` (unverändert).
- Tests: `SynthetischeKvNummern` (u. a. `BSNR_BEKANNT` als LANR-ungültige Zahl mit bekanntem Regionalcode = Gestalt
  der Anlassnummer; `BSNR_LANR_GUELTIG` als Zahl mit LANR- **und** BSNR-Gestalt für AK-14 und AK-20, keine neuen
  Nummern nötig), die Wächter `TestdatenSynthetischTest`, `KvnrSynthetischWaechter`, `BsnrSynthetischWaechter`,
  `KlartextFreiheitTest`, `ArchitekturRegelnTest`; Muster und Hash-Verfahren von `KvStandTrennerDifferenzTest`;
  Muster der gezählten Ausnahme `KvnrOhneKontextAusnahme`; Generatoren `RoundtripFuzzTest.erzeuge*` (Teile 1 bis 1h),
  `KvDifferenzTest.korpus()`, `RoundtripKorpusTest`, `KvKontextLueckenEigenschaftTest.differenzTexte()`;
  `GrenzlastTest` als Vorlage.

## 4. Neue Abhängigkeiten

Keine. Im Produkt nur `java.util` (Positivliste von `ArchitekturRegelnTest`), kein `java.time`. In Tests darf die
unabhängige Datumsreferenz `java.time.LocalDate` nutzen (Testcode, JDK).

## 5. Sicherheits- und Datenschutzaspekte

- **Bewusste Schutzminderung (Teil A):** Gültige Datumsangaben, auch Geburtsdaten hinter `geb.`, stehen danach im
  Klartext (Anforderung „Bekannte Grenzen“). Ein Geburtsdatum kann mit weiteren Angaben personenbeziehbar sein;
  Hinweis für Oberfläche und Schulung bleibt offen (Kap. 15 Punkt 5). Die Menge der wegfallenden Positionen ist durch
  den Differenztest auf genau die Datumssegmente begrenzt.
- **Teil B ändert die Abdeckung nicht** (Beweis 1.3, Test auf Gleichheit). Konfidenz kann sinken (D-10); fragliche
  Funde werden weiter geliefert, ersetzt wird nur, was der Aufrufer übergibt (AK-27).
- Kein Netzwerk-, DNS-, Datei- oder Prozesszugriff; keine Ausgabe, kein Logging. Meldungen und Ausnahmen von
  `KvDatum`, `KvKontextvorrang`, `DirektFund` und Scanner enthalten weder Text noch Ziffern noch Datumswerte
  (`KlartextFreiheitTest` wird um die neuen Typen ergänzt).
- **Leistung/DoS:** linear, keine Rekursion, kein regulärer Ausdruck; die Nachrechnung in Teil B ist höchstens vier
  zusätzliche lineare Läufe und nur bei Paaren; Grenzlast mit 1 000 000 Zeichen unter 2 s (AK-30).
- **Testdaten:** KV-Nummern nur aus `SynthetischeKvNummern`; Anlassnummern (`123456701`, `159876500`, `159876521`,
  `K123456789`) erscheinen in keinem Testcode, sie werden durch synthetische Konstanten gleicher Gestalt ersetzt
  (Kap. 6, Regel 4).

## 6. Verbindliche Regeln für engineer und Prüfer

Die Regeln aus Kap. 9 des Plans `kvnr-bsnr-erkennung` gelten weiter (kein regulärer Ausdruck im Produkt, Zeilen ≤ 120
Zeichen, keine `\uXXXX`-Escapes in neuen oder geänderten Zeilen, keine `System.out`/`System.err`, Meldungen ohne
Ziffern, keine Mindestzahl gesenkt, kein Seed geändert). Ergänzungen:

1. **Unverändert in allen Tasks:** alle eingefrorenen Kopien und ihre Hüllen (`KvNumberScannerVorgaenger`,
   `KvNumberScannerStandLuecken`, `KvNumberScannerStandTrenner`, ab Abnahme von T-1 auch `KvNumberScannerStandDatum`
   und `KvStandDatum`), alle `SIEGEL_*`/`HASH_*`-Konstanten, alle bestehenden Generatoren und Seeds,
   `ConflictResolver`, `Finding`, `Recognizer`, Pseudonymizer, Reidentifier.
2. **Umstellregel (AK-11 b, AK-28).** Bestehende Tests werden nur so umgestellt, wie der Task es nennt. Schlägt ein
   anderer bestehender Test an, gilt: Ist der Fall nach der Referenz von T-1 (`DatumReferenz` bzw. `VorrangReferenz`)
   genau ein beabsichtigter Fall (entfallenes Datumssegment bzw. Typwechsel nach D-8), stellt der engineer die
   betroffene Erwartung gezielt um (konkrete neue Erwartung oder gezählte Ausnahme mit exakter Zahl nach dem Muster
   `KvnrOhneKontextAusnahme`; nie „irgendein Fund“, nie gelockerte Schwelle) und nennt sie einzeln im Bericht. Ist er
   es nicht, hält er an und meldet Test und Fall ohne Ziffern. Jede Umstellung trägt den Kommentar
   `Umgestellt durch datum-und-kontextvorrang T-n:` mit alter und neuer Erwartung.
3. **Datumsliterale in Tests (D-7, AK-32).** Erfundene Kalenderdaten stehen als Literal in der neuen zentralen
   Testklasse `de.skillshield.core.SynthetischeDaten` (Vektoren AK-2 bis AK-5, Zeitraum, Grenzfälle) oder an der
   Teststelle, nie zusammen mit Namen oder Adressen. Der Wächter `TestdatenSynthetischTest` prüft Ziffernketten mit
   Leerzeichen, Tab, NBSP, `-`, `.`, `/`, `_` als Trenner: Ein Datum allein ist unkritisch (höchstens acht Ziffern;
   die gleitende Neunerprüfung zerlegt an Punkten). **Verboten** sind Literale, in denen ein Datum über genau einen
   Trenner mit weiteren Ziffern zu neun Ziffern gesamt oder (ohne Punkt) gleitend zu neun Ziffern verbunden ist
   (`14.03.1958 1`, `14/03/1958 1`, `5-3-2026-123`). Solche Texte entstehen nur durch Verkettung mit Konstanten aus
   `SynthetischeKvNummern`/`SynthetischeDaten` zur Laufzeit; Texte mit KV-Nummern laufen wie bisher durch die
   Laufzeitwächter. Keine Ausnahme in `AUSNAHMEN_ZIFFERN` für dieses Feature.
4. **Anlasstext in Tests.** Synthetische Fassung mit festen Ersetzungen: `159876500` → `BSNR_BEKANNT`, `159876521`
   → `BSNR_BEKANNT_ZWEI`, `123456701` → eine BSNR-Form-Konstante mit bekanntem Regionalcode, die nach LANR und IK
   ungültig ist (Vorbedingung im Test per `lanrGueltigReferenz`/`ikGueltigReferenz` und Regionalcode-Referenz
   belegt), `K123456789` → `KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN` (Anlass: Prüfziffer absichtlich ungültig), die
   Auslassungen `…` → feste erfundene Wörter, Datumswerte wie im Anlass (erfunden). Der Text steht einmal in
   `SynthetischeDaten.anlassText()` und wird von T-3, T-4 und T-6 benutzt.
5. **Zeitbudget:** Jede neue Testklasse mit 100 000er-Läufen bleibt unter 60 s; die Läufe AK-11 c/d prüfen auf
   Erkennerebene (der Scan fügt einem Datum keinen Bestandsfund hinzu), mit einer festen Stichprobe von 1 000 Texten je
   Form auch über `TextScanner.standard()`.

## 7. Messung alt gegen neu (OF-4)

**Vorhanden und wiederverwendet:** drei eingefrorene Kopien, aber nur für LANR/IK und älter als KVNR/BSNR
(`KvStandTrenner` = Stand Ende kv-trenner-und-umbruch); `KvnrBsnrHilfen.bestandsabweichungen` (S-1 bis S-3, K-1 bis
K-4 gegen `ALT_TRENNER`); gezählte Ausnahme `KvnrOhneKontextAusnahme`; Textmengen Fuzz 1 bis 1h, Korpus, Korpus Teil
2, KVNR-Korpus, Generatoren der Eigenschaftstests; `GrenzlastTest`; `RoundtripKorpusTest`.

**Fehlt und wird ergänzt:**

- **Vierte Kopie (T-1):** `test/…/recognizer/kv/KvNumberScannerStandDatum.java` – wörtliche Kopie des heutigen
  `KvNumberScanner` (alle vier Arten). Einzige Ergänzung gegenüber dem Original, deutlich markiert: Aufzeichnung
  `(start, end, quelle, direkt, schraeg)` je Ausgabe in eine optionale Liste (ohne Einfluss auf die Funde; per Test
  belegt). `KvStandDatum`: öffentliche Hüllen `lanr()`, `ik()`, `kvnr()` (Kopie + unveränderter `KvnrOhneKontext`),
  `bsnr()` mit gleichen `nachrangig()`/`restQuelle()` wie das Produkt, `ausgabenMitMerker(art, text)` und der Scanner
  `ALT_DATUM` (E-Mail, IPv4, URL, Pfad, die vier Hüllen in der Reihenfolge von `standard()`). Da die Hüllen keine
  Produktklassen sind, wirkt `KvKontextvorrang` auf `ALT_DATUM` nie: Er bleibt der Stand vor dem Feature.
- **Unabhängige Referenzen (T-1):** `DatumReferenz` (Form nach D-2 per Zeichenzerlegung, Kalender per
  `java.time.LocalDate`, zweistelliges Jahr gültig nur, wenn in 19jj **und** 20jj gültig; Zeitraum) und
  `VorrangReferenz` (wendet Regel 1.3 auf die Ausgaben der Kopie mit Merkern an; Formularfeld-Prüfung eigenständig
  geschrieben). Beide mit Selbsttests.
- **Differenztest (T-1, fortgeschrieben in T-2 und T-4):** `DatumKontextvorrangDifferenzTest` über alle Textmengen
  (und ab T-3/T-5 die neuen Mengen). Schalter `TEIL_A` und `TEIL_B` (Konstanten, in T-1 beide `false`):
  - Erkennerebene je Art: Produkt == Kopie, bei `TEIL_A` genau Kopie minus Segmente mit `DatumReferenz` (alle Felder,
    gleiche Reihenfolge). Teil B ändert die Erkennerebene nicht.
  - Scannerebene `standard()` gegen `ALT_DATUM`: jede Abweichung wird klassifiziert als (A) entfallener Fund, dessen
    Bereich ein Datumssegment ist; (B) Fund gleichen Bereichs und Originalwerts mit anderem Typ, wobei nach den Merkern
    der Kopie die neue Art direkt und die alte nicht direkt ist und Konfidenz/Quelle die des direkten Kandidaten sind;
    (Z) Folgeänderung eines zusammengelegten Fundes. Zulässig ist (A) nur bei `TEIL_A`, (B) nur bei `TEIL_B`, (Z)
    nur mit exakter, im Test festgehaltener Zahl (erwartet 0). Jede andere Abweichung ist ein Verstoß.
  - Abdeckung: neu == alt minus Datumspositionen (A); bei (B) exakt gleich.
  - Vollständigkeit: Jedes Datumssegment der Kopie fehlt im Produkt (A); jeder Fall der `VorrangReferenz`, bei dem
    der entfernte Kandidat heute den Typ bestimmt, ist im Produkt umgesetzt (B).
  - Anzahl je Klasse und Textmenge als exakte Werte; der Abnahmebericht nennt jeden Fall einzeln (Textmenge, Index,
    Bereich, alter/neuer Typ, alte/neue Konfidenz; keine Ziffern).
- **Neue Generatoren (T-3, T-5):** `RoundtripFuzzTest.erzeugeKvI` (Datumsformen und Nicht-Daten gleicher Trennerform
  hinter Kontextwörtern aller Arten, mit Nummern daneben, Aufzählung, nachgestellt) und `erzeugeKvJ` (Typkollision
  nach AK-25), je eigener Seed; bestehende Generatoren und Seeds bleiben unberührt.
- **Prognose (T-1):** `DatumKontextvorrangPrognoseTest` zählt mit Kopie und Referenzen auf jeder bestehenden Textmenge
  die künftigen Fälle (A) und (B) und hält sie als exakte Zahlen fest. Daraus folgt vor jeder Produktänderung, welche
  bestehenden Mengen-Tests (Kap. 8.3) anschlagen werden. Dort stehen auch die heutigen Werte der Beispiele aus AK-18
  und AK-19 (mit `ALT_DATUM` gemessen).

## 8. Bestandstests, die sich zwangsläufig ändern

### 8.1 Teil A (AK-11 b)

Durch Lesen des Codes: **kein** bestehender Test erwartet ausdrücklich einen Fund über ein Datum nach D-2/D-3. Die
Suche nach allen Formen aus D-2 in `skillshield-core/src/test` trifft nur Datumsliterale ohne KV-Kontextwort
(`PathRecognizer*Test`, `Ipv4RecognizerTest` mit `19.11.2026.192.0.2.10.52344`, eine Kette ohne Datumssegment) und
Quellenvermerke in Kommentaren. `SynthetischeKvNummern.LAENGE_*` sind keine Daten. Möglich bleiben Treffer in
Zufallstexten der Eigenschafts- und Fehlalarmtests (zufällige Ziffern mit `.`, `-`, `/` und Gruppen 2-2-4 oder 2-2-2;
bei 2-2-2 mit Punkt ist etwa jede 27. Kette ein gültiges Datum). Diese werden in T-1 prognostiziert (Mengen-Tests)
bzw. in T-2 nach der Umstellregel behandelt (innen erzeugte Zufallstexte, die T-1 nicht erreicht). Erwartete
Kandidaten: `KvTrennerEigenschaftTest`, `KvErkennungEigenschaftTest`, `KvKontextLueckenEigenschaftTest`,
`BsnrEigenschaftTest`, `KvnrEigenschaftTest`, `KvTrennerFehlalarmTest`, `KvTrennerUzFehlalarmTest` und der
Vergleichshelfer `KvTestHilfen.scan` (volle Gleichheit mit `KvStandLuecken`). Bevorzugte Umstellung dort: in der
Vergleichsregel des Helfers „Kopie-Fund, dessen Bereich nach `DatumReferenz` ein Datumssegment ist, fehlt“ – sonst
volle Gleichheit.

### 8.2 Teil B (sicher, durch Nachrechnen am Code)

| Test | heute | danach (Grund) |
|------|-------|----------------|
| `BsnrScannerKollisionenTest.ak30_zeile5_dieselbeGueltigeLanrUnterLanrUndBsnr_beideFundeLanr` (`LANR L, BSNR L`) | 2. Zahl LANR 0,80 | BSNR 0,50 `bsnr-regionalcode-unbekannt` (BSNR direkt, LANR nur Aufzählung) |
| `…ak30_zeile6_dieselbeBsnrUnterLanrUndBsnr_beideFundeBsnr` (`LANR B, BSNR B`) | 1. Zahl BSNR 0,60 | LANR 0,50 `lanr-pruefziffer-ungueltig` (LANR direkt gegen nachgestelltes BSNR) |
| `…ak30_bsnrKontextwortZaehltNichtGegenEineGueltigeLanr_lanrFundMitKomma` (`BSNR L, LANR`) | LANR 0,60 | BSNR 0,50 `bsnr-regionalcode-unbekannt` |
| `…d6_kvnrKontextwortVorBsnrNummer_bsnrSetztSichGegenDenSchwaechenKvnrKandidatenDurch` (`KVNR B, BSNR`) | BSNR 0,60 | KVNR 0,40 `kvnr-form-abweichend` |
| `…ak30_dieselbeBsnrUnterLanrUndBsnr_einGemeinsamerPlatzhalterFuehrtZumOriginalwert` | `LANR [[BSNR_1]], BSNR [[BSNR_1]]` | `LANR [[LANR_1]], BSNR [[BSNR_2]]` (D-13) |
| `…ak30_dieselbeLanrUnterLanrUndBsnr_einGemeinsamerPlatzhalterFuehrtZumOriginalwert` | `LANR [[LANR_1]], BSNR [[LANR_1]]` | `LANR [[LANR_1]], BSNR [[BSNR_2]]` (D-13) |
| `TextScannerKvTest.kvKontextLuecken_ak21_ikNummerKommaLanrMitLanrKandidat060_typwechselZuLanrPositionAbgedeckt` (`IK L, LANR`) | LANR 0,60 | IK 0,50 `ik-pruefziffer-ungueltig`, `IK [[IK_1]], LANR` |
| `TextScannerKvnrTest.ak18_versichertennummerIkKommaIk_genauEinIkFundUeberDieZahlMitKonfidenz060` (`Versichertennummer I, IK`) | IK 0,60 | KVNR 0,40 `kvnr-form-abweichend` |

Die Testnamen beschreiben danach das Gegenteil; sie werden umbenannt (alter Name im Kommentar), Bereich und Abdeckung
bleiben gleich. Geprüft und **unverändert**: Formularfeld-Tests (`ak30_zeile3/4`, `bekannteGrenzeGD1_formularfeld…`
dank D-11-Ausnahme), `ak30_zeile1/2/7`, die übrigen `d6_*`, `ak33_*`, `ak35_*`, `k1_*`, `bekannteGrenzeGD2_*`,
Oberflächentests (`Attrappen.ALLE_STUFEN`: LANR direkt gewinnt schon heute).

### 8.3 Zu messen in T-1 (Mengen-Tests gegen ältere Kopien)

Diese Tests vergleichen das Produkt mit älteren Kopien auf generierten Mengen; sie schlagen nur an, wenn die Prognose
dort Fälle (A) oder (B) zählt: `KvStandTrennerDifferenzTest` (E, S-1, S-2: ein Typwechsel LANR↔IK ist dort kein
zulässiger K-Fall), `KvStandLueckenDifferenzTest`, `KvVorgaengerDifferenzTest`, `KvDifferenzTest`,
`KvnrScannerBestandTest`, `BsnrScannerBestandTest`, `KvnrFuzzBestandTest`, `BsnrFuzzBestandTest`,
`KvnrOhneKontextBestandTest`, `KvnrOhneKontextFuzzBestandTest`, `KvnrBestandVorrangEigenschaftTest`,
`KvArtBestandTest`, `RoundtripKorpusTest`, `RoundtripFuzzTest`. Siegel über Kopien und `ALT_TRENNER` ändern sich nie
(Kopien bleiben, `KvKontextvorrang` wirkt nicht auf Kopien). Bei Fällen > 0 wird in T-2 bzw. T-4 eine gezählte
Ausnahme `DatumKontextvorrangAusnahme` (Muster `KvnrOhneKontextAusnahme`, exakte Zahl je Menge, nur Klasse A bzw. B)
eingehängt; der jeweilige Test bleibt sonst unverändert.

## 9. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün (alle Module). Jeder Task durchläuft engineer →
reviewer + security → qa. **Teil A = T-2, T-3; Teil B = T-4, T-5.** Beide Teile hängen nur von T-1 ab und sind ohne
den anderen lieferbar (T-4/T-5 setzen T-2/T-3 nicht voraus; der Differenztest klassifiziert in jedem Zustand). T-6
schließt ab und wird an den gelieferten Umfang angepasst, falls nur ein Teil geliefert wird.

### T-1: Einfrieren, Referenzen, Prognose (keine Produktänderung)

- **Ziel:** Stand vor dem Feature messbar machen; vorab wissen, welche Bestandstests sich ändern.
- **Dateien (alle Test, außer Doku):** `test/…/recognizer/kv/KvNumberScannerStandDatum.java` (Kopie mit
  Aufzeichnung, Kap. 7), `test/…/recognizer/kv/KvStandDatum.java` (Hüllen, `ALT_DATUM`, Merker-Zugriff),
  `test/…/core/DatumReferenz.java`, `test/…/core/VorrangReferenz.java`, `test/…/core/SynthetischeDaten.java`
  (Datumsvektoren AK-2 bis AK-5, Zeitraum, Grenzfälle, `anlassText()`), Tests `KvStandDatumTest` (Kopie == Produkt auf
  allen Mengen je Art in allen Feldern; Aufzeichnung ohne Einfluss; Siegel der Kopie je Menge, hexadezimal),
  `DatumReferenzTest`, `VorrangReferenzTest` (Selbsttests mit bekannten Fällen, auch Formularfeld und D-9),
  `DatumKontextvorrangDifferenzTest` (Kap. 7, `TEIL_A = TEIL_B = false`, verlangt volle Gleichheit),
  `DatumKontextvorrangPrognoseTest` (Zahlen je Menge; Ausgangswerte AK-18/AK-19);
  `docs/features/datum-und-kontextvorrang/kern-pruefsummen-vorher.txt` (SHA-256 aller Dateien unter
  `skillshield-core/src`, erzeugt **vor** jeder Änderung dieses Tasks; Format wie bei `oberflaeche-mvp`).
- **AK:** Vorbereitung von AK-11 (a, b), AK-18 (Ausgangswerte), AK-22 bis AK-24 (Messgrundlage), AK-28 (Prüfsummen),
  AK-32 (Testdaten-Weg).
- **Tests:** wie genannt; alle bestehenden Tests unverändert grün.
- **Abnahmehinweis:** qa prüft per Diff, dass die Kopie bis auf den markierten Aufzeichnungsblock zeichengleich zu
  `KvNumberScanner` ist; nennt die Prognosezahlen je Menge und daraus die Liste der Mengen-Tests, die in T-2 bzw. T-4
  umzustellen sind (ergänzt 8.1/8.3). Liegt eine Prognose für Teil B > 0 bei einer Menge ohne KVNR/BSNR-Wörter, wird
  das der Hauptsitzung gesondert gemeldet (Typwechsel LANR↔IK).

### T-2: Teil A – Datumsprüfung im Scanner

- **Ziel:** Regel 1 (D-1 bis D-6) umsetzen und alle Einzelkriterien von Teil A belegen.
- **Dateien:** `main/…/kv/KvDatum.java` (neu), `main/…/kv/KvNumberScanner.java` (eine Zeile in `ausgeben`, Javadoc
  Regel 1, Grenzen, Linearität), Javadoc in `KvArt`, `LanrRecognizer`, `IkRecognizer`, `KvnrRecognizer`,
  `BsnrRecognizer`; Tests `KvDatumTest` (paketintern: jede Form, jede Grenze aus D-3, Schaltjahre, zweistelliges Jahr
  einschließlich `29.02.00`, Zeitraum, Kettenende, Buchstabe vorn, Neuner-Segment wird nicht geprüft, Vorgriff bricht am
  Textende ab), `DatumKeinFundTest` (Erkennerebene und `standard()`: AK-1 bis AK-6, AK-8 bis AK-10 je Art und je
  Kontextwort aus AK-1 mit Leerzeichen und `: `), `DatumGrenzenTest` (`bekannteGrenze…` je Grenze aus Kap. 11, Teil A;
  Tabelle „Grenze zu Test“); `DatumKontextvorrangDifferenzTest`: `TEIL_A = true`, exakte Zahlen je Menge; Umstellungen
  nach 8.1/8.3 und Prognose; `KlartextFreiheitTest` um `KvDatum` ergänzt (keine Ziffern in Meldungen).
- **AK:** AK-1 bis AK-6, AK-8, AK-9, AK-10, AK-11 b, AK-31 (Teil A), AK-33 (Teil A).
- **Tests:** wie genannt; jede Umstellung einzeln im Bericht.
- **Abnahmehinweis:** qa vergleicht die Liste der umgestellten Tests mit der Prognose aus T-1; jede Abweichung davon
  ist zu begründen. AK-1 „byte-gleich“: `pseudonymize(text, scan(text))` liefert den Text unverändert.

### T-3: Teil A – Nachweis alt gegen neu, Fuzz, Grenzlast, Anlass (nur Tests)

- **Ziel:** Kein Schutzverlust außer Datumssegmenten, gemessen über große Mengen; Anlass Teil A.
- **Dateien:** `RoundtripFuzzTest` (nur neuer Generator `erzeugeKvI`, eigener Seed, eigene Roundtrip-Methode),
  `DatumFuzzTest` (AK-11 c: 100 000 gültige Daten je Form (a) mit ein- und zweistelligem Tag/Monat, (b), (c) mit `/`
  und `-`, (d), (e), fester Seed, je hinter einem Kontextwort jeder Art → 0 Funde über dem Datum; AK-11 d: 100 000
  Nicht-Daten derselben Trennerformen mit ungültigem Tag/Monat oder Jahr außerhalb 1900–2099 → Fundliste gleich der
  Kopie; Anzahlen als exakte Werte), `DatumKontextvorrangDifferenzTest` (Menge 1i aufgenommen),
  `DatumGrenzlastTest` (AK-30, Datumsfälle: `BSNR ` + Datum wiederholt (0 Funde), `BSNR <BSNR> ` + Datum wiederholt,
  `14.03.` wiederholt, `14.03.1958-` wiederholt, `1.` wiederholt, ein Datum mit einer Million angehängter Ziffern;
  Formel je Fall, Scan + Pseudonymisierung + Rückübersetzung je < 2 s), `DatumAnlassTest` (AK-7 gegen `ALT_DATUM`:
  keine Datumsfunde, alle anderen Funde gleich in Typ, Bereich, mindestens gleicher Konfidenz), `RoundtripKorpusTest`
  (nur neue Fälle mit Datumsformen, AK-26 Teil A).
- **AK:** AK-7, AK-11 a, c, d, AK-26 (Teil A), AK-29 (Teil A), AK-30 (Teil A).
- **Tests:** wie genannt.
- **Abnahmehinweis:** qa nennt im Bericht die Zahl der entfallenen Funde je Menge und listet jeden einzeln (Menge,
  Index, Bereich, Typ, Quelle, ohne Ziffern); jede Laufzeit. **Damit ist Teil A abnehmbar.**

### T-4: Teil B – Aufzeichnung im Scanner, `KvKontextvorrang`, Einbindung im `TextScanner`

- **Ziel:** Regel 2 (D-8 bis D-14) umsetzen und alle Einzelkriterien von Teil B belegen.
- **Dateien:** `main/…/kv/KvNumberScanner.java` (Aufzeichnung, Merker `/`, `DirektFund`, `direkteFunde`; `detect`
  unverändert), `main/…/kv/KvKontextvorrang.java` (neu), `main/…/recognizer/TextScanner.java`; Javadoc in
  `KvNumberScanner`, `KvArt`, den vier Erkennern, `TextScanner`; Tests `KvNumberScannerDirektTest` (paketintern:
  `direkteFunde` ist genau die Teilmenge der `detect`-Funde mit direktem Kontext, für alle Mengen aus T-1; Merker `/`),
  `KvKontextvorrangTest` (Vorprüfung, D-9 Teilabdeckung, Formularfeld mit allen Paarungen, Wortgrenze vor dem
  Formularwort, Kandidaten fremder Erkenner unberührt, `null`, Klartextfreiheit), `KontextvorrangTest` (Scannerebene:
  AK-12 mit `anlassText()`, AK-13 alle zwölf geordneten Paare je mit Zeilenumbruch und mit Füllwörtern, AK-14 bis
  AK-21), `KontextvorrangGrenzenTest` (`bekannteGrenze…`: 14h-`I K`, Formularfeld mit Vorwort oder Sperrschrift,
  nachgestelltes Kontextwort einer anderen Art verliert gegen direktes, unbekannte Kurzformen), Umstellungen nach
  8.2 und 8.3; `DatumKontextvorrangDifferenzTest`: `TEIL_B = true`, exakte Zahlen je Menge; `KlartextFreiheitTest`
  um `KvKontextvorrang` und `DirektFund` ergänzt.
- **AK:** AK-12 bis AK-21, AK-31 (Teil B), AK-33 (Teil B).
- **Tests:** wie genannt; AK-13 zusätzlich mit höchstens zwei Füllwörtern (Kap. 13 Punkt 4), AK-18/AK-19 gegen die
  in T-1 festgehaltenen Ausgangswerte.
- **Abnahmehinweis:** security prüft besonders, dass kein Kandidat ohne vollständige Abdeckung durch einen
  verbleibenden direkten Kandidaten entfällt und dass `KvKontextvorrang` auf Kopien nicht wirkt (Siegel unverändert).

### T-5: Teil B – Nachweis alt gegen neu, Typkollisions-Fuzz, Grenzlast (nur Tests)

- **Ziel:** Unterschiede auf die beabsichtigten Typwechsel begrenzt, Abdeckung gleich, Laufzeit.
- **Dateien:** `RoundtripFuzzTest` (nur neuer Generator `erzeugeKvJ`, eigener Seed), `KontextvorrangFuzzTest` (AK-25:
  100 000 Texte `KW1 Nummer` LF `KW2 Nummer`, Arten und Kontextwortformen zufällig, Nummern aus
  `SynthetischeKvNummern` gültig/ungültig, auch gleiche Nummer; Orakel: Die Art von KW2 liefert für `KW2 Nummer` allein
  einen Fund über die Nummer ⇒ Typ der zweiten Nummer ist die Art von KW2; Buchstabenformen der KVNR nur hinter
  KVNR-Wörtern bzw. mit Orakel nach D-9; sonst Typ wie bei `ALT_DATUM`; Abdeckung vollständig und überlappungsfrei),
  `DatumKontextvorrangDifferenzTest` (Menge 1j; AK-22: jede gesunkene Konfidenz ist ein Fall B; AK-23: Abdeckung
  gleich; AK-24: Liste und Anzahl), `KontextvorrangGrenzlastTest` (AK-30: `BSNR <BSNR>` LF `LANR <ungültige LANR>` LF
  wiederholt, über 30 000 Funde; `L A N R ` wiederholt; Mischung, die die Nachrechnung für alle vier Arten auslöst),
  `RoundtripKorpusTest` (nur neue Fälle Typkollision, AK-26 Teil B).
- **AK:** AK-22, AK-23, AK-24, AK-25, AK-26 (Teil B), AK-29 (Teil B), AK-30 (Teil B).
- **Tests:** wie genannt.
- **Abnahmehinweis:** qa nennt je Menge Anzahl und Liste der Typwechsel (alter/neuer Typ, alte/neue Konfidenz), die
  Zahl der Fälle (Z) (erwartet 0) und die Laufzeiten. **Damit ist Teil B abnehmbar.**

### T-6: Abschluss – Anlass als Ganzes, Nachbarn, Oberfläche, Prüfsummen

- **Ziel:** Gesamtergebnis am Anlasstext und Nachweis, dass nur der Kern an den vorgesehenen Stellen geändert ist.
- **Dateien:** `AnlassGesamtTest` (Anlasstext: alle Datumsangaben im Klartext; beide Stellen der LANR-Nummer Typ LANR
  0,50 mit **demselben** Platzhalter; alle BSNR/NBSNR- und die KVNR-Stelle ersetzt; Rückübersetzung zeichengleich;
  ohne Übergabe bleibt der Text unverändert, AK-27), Bestätigung des bestehenden Abgleichs der Erkennerliste
  (dieselben acht Erkenner in derselben Reihenfolge, AK-28), `RoundtripKorpusTest` (gemischter Fall mit allen
  Formen aus AK-1 bis AK-25, AK-26), `docs/features/datum-und-kontextvorrang/kern-pruefsummen-nachher.txt` und
  `…/geaenderte-dateien.txt` (Liste der gegenüber „vorher“ geänderten Dateien unter `skillshield-core/src`).
- **AK:** AK-26, AK-27, AK-28, AK-31, AK-32, AK-33 (Gesamtprüfung), AK-7 und AK-12 gemeinsam.
- **Tests:** wie genannt; vollständiges `mvn clean verify` mit `skillshield-ui` und `skillshield-app`.
- **Abnahmehinweis:** qa prüft: geänderte Produktivdateien sind genau die aus Kap. 2.1; keine Datei in
  `skillshield-ui`, `skillshield-app`, `skillshield-io`, `skillshield-vault` geändert; deren Tests grün (es gibt
  **keinen** automatischen Test, der `docs/features/oberflaeche-mvp/kern-pruefsummen.txt` liest – die Datei war ein
  qa-Nachweis für AK-57 jenes Features und bleibt als historischer Stand unverändert; maßgeblich für dieses Feature sind
  die neuen Dateien „vorher/nachher“). Javadoc-Tabelle „Grenze zu Test“ vollständig.

### Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| 1–6 | T-2 | 15–21 | T-4 | 28 | T-1 (Vorbereitung), T-6 |
| 7 | T-3, T-6 | 22–24 | T-1 (Messgrundlage), T-5 | 29 | T-3, T-5 |
| 8–10 | T-2 | 25 | T-5 | 30 | T-3, T-5 |
| 11 | T-1 (Prognose), T-2 (b), T-3 (a, c, d) | 26 | T-3, T-5, T-6 | 31 | T-2, T-4, T-6 |
| 12 | T-4, T-6 | 27 | T-6 | 32 | T-1, T-6 |
| 13, 14 | T-4 | | | 33 | T-2, T-4, T-6 |

## 10. Antworten auf OF-1 bis OF-6 (Kurzform)

1. **OF-1:** Eine Zeile am Anfang von `KvNumberScanner.Lauf.ausgeben`, Prüfung in `KvDatum` (Konstanten
   `JAHR_MIN`, `JAHR_MAX`), konstanter Aufwand je Segment, fester Vorgriff ≤ 7 Zeichen, kein regulärer Ausdruck.
2. **OF-2:** „Datum zuerst“ nein (Schutzverlust am Rest, Präfixregel); D-5 und AK-10 bleiben. Zeitraum ja, ohne
   Zerlegung, über die Kette ab Segmentanfang; Restgrenze bei Neuner-Präfix.
3. **OF-3:** Nachbearbeitung im `TextScanner` über die neue Klasse `KvKontextvorrang` mit dem aufgezeichneten
   `direkt`-Merker des Scanners; Entfernen nur bei vollständiger Umfassung (D-9), Formularfeld-Ausnahme über einen
   `/`-Merker (D-11); O(n + k log k); `ConflictResolver` und Erkenner-Signaturen unverändert.
4. **OF-4:** Vierte Kopie aller Arten mit Merker-Aufzeichnung, Referenzen, Prognose und Differenztest in T-1; neue
   Generatoren in T-3/T-5; Liste der umzustellenden Tests: Kap. 8, ergänzt durch die Prognose aus T-1.
5. **OF-5:** Typ, Konfidenz und Quelle der nahen Art, unverändert übernommen; `zusammengelegt` nur wie bisher bei
   Teilüberlappung mit einem dritten Kandidaten, gezählt als Fall (Z), erwartet 0.
6. **OF-6:** `L A N R`, `L.A.N.R.`, `L. A. N. R.` und `I K` setzen `direkt` → D-8 gilt. Grenze: `I K` bei laufendem
   Kontext (14h) und Formularfeld mit gesperrtem Wort oder Vorwort; je ein Test.

## 11. Bekannte Grenzen nach diesem Feature (je ein Test `bekannteGrenze…`)

Teil A: alle Grenzen aus der Anforderung (Geburtsdatum im Klartext; Leerzeichen-, trennerlose, gemischte, Jahr-zuerst-
Formen mit `.`/`/`, zweistelliges Jahr mit `/`/`-`, ungültige Kalenderwerte, Jahre außerhalb 1900–2099 bleiben Fund;
`14.03.1958 1234` und `14.03.1958 08.30`; `B 14.03.1958`; `BSNR 14.03.1958` ungeschützt), dazu neu:
Neuner-Präfix (`14.03.1958 1`, `01.03.2026-1.3.2026`), Zeitraum mit weiterem Kettenteil dahinter, `29.02.00`.

Teil B: alle Grenzen aus der Anforderung (unerkannte Kurzformen geben keinen Vorrang; falsches nahes Wort bestimmt den
Typ; Konfidenzabsenkung 0,80 → 0,50; zwei Platzhalter für dieselbe Zeichenfolge bei zwei nahen Wörtern), dazu neu:
`I K` bei laufendem Kontext (14h) ohne Vorrang; Formularfeld-Ausnahme nur für ungesperrte Kontextwörter direkt am `/`;
ein direkt vorangestelltes Kontextwort schlägt ein nachgestelltes einer anderen Art, auch mit Konfidenzabsenkung
(z. B. `KVNR N, BSNR`: 0,60 → 0,40); „direkt“ gilt im Sinne des Scanners auch über `(`, `,`, `-`, `/`, `#` und `.`
hinweg (`LANR (N)`, `LANR, N`).

Alle Grenzen der Vorgänger gelten unverändert.

## 12. Risiken

1. **Umfang der Umstellungen.** Zufallsbasierte Bestandstests können Datumssegmente oder LANR↔IK-Typwechsel enthalten,
   die erst beim Lauf sichtbar werden. Gegenmittel: Prognose in T-1, Umstellregel (Kap. 6 Punkt 2), gezählte
   Ausnahmen statt Lockerung. Risiko eines zusätzlichen Engineer-Durchlaufs in T-2/T-4.
2. **Fachliche Wirkung von D-8 auf nachgestellte Kontextwörter** (Kap. 8.2): Vorgänger hatten diese Fälle bewusst
   anders entschieden; Konfidenz sinkt dort teils auf 0,40/0,50. Abdeckung unverändert.
3. **Größe der vierten Kopie** (~1 700 Zeilen Testcode). Bewährtes Muster; qa prüft Zeichengleichheit per Diff.
4. **Neue öffentliche Klasse** `KvKontextvorrang` (Kap. 13 Punkt 6).
5. **Laufzeit:** Nachrechnung verdoppelt im ungünstigsten Fall den KV-Anteil des Scans; Grenzlast in T-5 belegt.
6. **Geburtsdaten im Klartext** (gewollt, aber datenschutzrelevant); Hinweis an Oberfläche/Schulung noch offen.
7. **Testlaufzeit:** neue 100 000er-Läufe; Budget je Klasse 60 s (Kap. 6 Punkt 5), Gesamtlauf voraussichtlich
   9 bis 10 Minuten.

## 13. Abweichungen von der Anforderung und Präzisierungen (zur Kenntnis bzw. Freigabe)

1. **D-8 gegen nachgestellten Kontext (Freigabepunkt).** Wörtlich umgesetzt: Steht eine Nummer direkt hinter dem
   Kontextwort von X, entfällt auch der Kandidat einer Art Y, die die Nummer nur über ein **nachgestelltes**
   Kontextwort erschließt (`IK N, LANR` → IK). Folge: acht Bestandstests ändern ihre Erwartung (Kap. 8.2), teils mit
   Konfidenzabsenkung (0,60 → 0,40 oder 0,50). Alternative mit gleichem Aufwand: nachgestellt erschlossene Kandidaten
   vom Entfernen ausnehmen (zusätzlicher Merker „nachgestellt“ in der Aufzeichnung); dann ändern sich von den acht
   Tests nur noch `ak30_zeile5_…` und `ak30_dieselbeLanrUnterLanrUndBsnr_…` (die zweite Zahl in `LANR L, BSNR L` ist
   über Aufzählung erschlossen), die sechs übrigen bleiben wie heute. Empfehlung: Wortlaut D-8 (eine Regel ohne
   Unterfall, „das Wort vor der Nummer sagt, was sie ist“).
2. **„Direkt“ ist breiter als der Prosatext von D-8.** D-8 nennt Leerraum, `:`, `=`, Nachsatzwort; der Scanner hält
   `direkt` auch über jedes andere neutrale Zeichen außer `!`, `?` und einem Punkt mit Satzende-Wirkung (also `(`, `,`,
   `-`, `/`, `#`). Nach A-3 gilt der Begriff des Scanners; als Grenze dokumentiert.
3. **D-5 Lesart:** Datum = gewähltes Segment ist genau ein Datum, unabhängig von abgetrennten Nachbarblöcken (für
   AK-8 nötig). Zeitraum verlangt, dass die Kette mit dem zweiten Datum endet (strenger als D-2 e, im Zweifel Schutz).
4. **AK-13 Beispiel** `BSNR <BSNR> von Dr. Beispiel, LANR <Nummer>`: Mit `von`, `Dr.`, `Beispiel` und `LANR` sind es
   vier Füllwörter für die BSNR; ihr Kontext ist erloschen, ein Konflikt entsteht schon heute nicht (Ergebnis LANR,
   Kriterium erfüllt). Der Test enthält zusätzlich eine Variante mit höchstens zwei Füllwörtern vor `LANR`
   (`BSNR <BSNR> von Praxis, LANR <Nummer>`), damit der Vorrang wirklich geprüft wird.
5. **AK-18 Wert „0,80“** für `LANR <gültige LANR>, <BSNR>`: Synthetische BSNR-Nummern sind nach LANR ungültig, der
   LANR-Fund hat dort heute 0,50. Der Test prüft gegen den in T-1 gemessenen heutigen Wert (so sieht es AK-18 vor).
6. **Neue öffentliche Klasse** `KvKontextvorrang` (eine Klasse, zwei statische Methoden). AK-28 verlangt
   unveränderte öffentliche Typen und Signaturen; das bleibt erfüllt, es kommt aber ein öffentlicher Typ hinzu, weil
   `TextScanner` in einem anderen Paket liegt. Ohne ihn ginge es nur über eine Änderung von `Recognizer` oder der
   Erkenner, also über eine echte Signaturänderung.
7. **Zweistelliges Jahr und 29. Februar:** gültig nur, wenn in beiden Jahrhunderten ein Schaltjahr (`29.02.00` bleibt
   Fund). In der Anforderung nicht geregelt; Richtung Schutz entschieden.
8. **AK-25 Orakel:** Buchstabenformen der KVNR hinter fremden Kontextwörtern folgen D-9 (die größere KVNR-Form bleibt);
   der Generator hält das auseinander, statt das Kriterium zu verwässern.

## 14. Abweichungen von Plan.md

Keine. (Geburtsdaten als eigener Typ bleiben Folgefeature nach Plan Kap. 14.)

## 15. Offene Punkte für die Freigabe

1. Kap. 13 Punkt 1: D-8 auch gegen nachgestellte Kontextwörter einer anderen Art (Empfehlung) oder Ausnahme für
   nachgestellt erschlossene Kandidaten.
2. Kap. 13 Punkt 6: Eine neue öffentliche Klasse `KvKontextvorrang` im Kern ist in Ordnung.
3. Reihenfolge: Teil A zuerst (T-1 → T-2 → T-3), dann Teil B (T-4 → T-5), dann T-6; auf Wunsch umgekehrt oder nur ein
   Teil (T-6 wird dann auf den gelieferten Teil zugeschnitten).
4. `docs/features/oberflaeche-mvp/kern-pruefsummen.txt` bleibt unverändert (historischer Nachweis jenes Features);
   für dieses Feature gelten neue Dateien „vorher/nachher“. Alternativ auf Wunsch Aktualisierung jener Datei in T-6.
5. Hinweis „Geburtsdaten stehen im Klartext“ in Oberfläche/Schulung: nicht Teil dieses Features; Folgeaufgabe?

## Nachtrag der Hauptsitzung vom 24.11.2026: Umfang auf Teil A begrenzt

Der Nutzer hat am 24.11.2026 den Schwerpunkt auf IT-Tickets und Betriebsdokumentation verlegt; die
KV-Erkenner werden vorerst nicht weiterentwickelt (siehe `docs/features/uebersicht-it-infrastruktur.md`,
Abschnitt 3). Freigegeben ist deshalb nur **Teil A (Datumsangaben sind keine KV-Nummern)**.

- **Umgesetzt werden T-1, T-2 und T-3**, jeweils nur mit dem, was Teil A braucht.
- **T-4 und T-5 (Teil B, Kontextvorrang) sind zurückgestellt** und werden ohne neuen Auftrag des Nutzers
  nicht begonnen. Die offenen Punkte 1 und 2 der Zusammenfassung (D-8 gegen nachgestellte Kontextwörter,
  öffentliche Klasse `KvKontextvorrang`) bleiben damit unentschieden.
- **T-1 in verkleinerter Form:** eingefrorene Kopie des heutigen Scanners für alle vier Arten, Hüllen und
  `ALT_DATUM`, `DatumReferenz`, `SynthetischeDaten`, Differenztest und Prognose **nur für Teil A**. Es
  entfallen: der markierte Block, der „direkte" Funde aufzeichnet, `VorrangReferenz` und die Prognose- und
  Abweichungsklassen für Teil B. Die eingefrorene Kopie ist dadurch wörtlich gleich dem heutigen Scanner.
- **T-6 in verkleinerter Form als Teil von T-3:** Der Anlasstext wird nur für Teil A geprüft (Datumsangaben
  im Klartext, alle bisherigen Nummernfunde unverändert, Roundtrip zeichengleich); die Aussage „beide
  LANR-Stellen derselbe Platzhalter" gehört zu Teil B und entfällt. `mvn clean verify` mit ui und app,
  `kern-pruefsummen-vorher.txt` (T-1) und `kern-pruefsummen-nachher.txt` (T-3) wie geplant.
- **Prüfsummendatei von `oberflaeche-mvp`** bleibt unverändert; sie war der Nachweis jenes Features.
- **Geburtsdaten im Klartext** nach Teil A: bekannte Grenze; der Schutz als eigener Typ ruht mit den
  übrigen KV-Themen. Bis dahin markiert der Anwender sie von Hand.
