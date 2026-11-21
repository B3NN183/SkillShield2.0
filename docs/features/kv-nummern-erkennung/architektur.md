# Architektur: Erkennung von KV-Nummern (IK und LANR)

Feature-Kurzname: `kv-nummern-erkennung`
Grundlage: `anforderung.md` dieses Features (AK-1 bis AK-29, A-1 bis A-7, E-1, E-2, OF-1 bis OF-8),
`../text-pseudonymisierung-kern/anhang-ik-lanr-entwurf.md`, `../text-pseudonymisierung-kern/architektur.md`
(Kap. 1–5, T-18 bis T-21 samt Nachträgen), `Plan.md` Kap. 2, 6, 7, 11, 13, Code in `skillshield-core` (Stand 22.11.2026).

Schreibweise: „NBSP-Escape“ meint im Java-Quelltext die sechs Zeichen lange Unicode-Escape-Folge für U+00A0
(Backslash, `u`, `00A0`).

---

## 1. Lösungsansatz

Zwei neue Standard-Erkenner `LanrRecognizer` und `IkRecognizer` im Paket `de.skillshield.core.recognizer.kv`. Beide
nutzen denselben paketinternen, handgeschriebenen Zeichendurchlauf `KvNumberScanner` (kein Regex), konfiguriert über
das paketinterne Enum `KvArt` (Kontextwörter, Fundtyp, Quellen, Konfidenz, Prüfziffernverfahren). Der Durchlauf liest
den Text einmal von links nach rechts, zerlegt ihn in Kontextwörter, Füllwörter, Ziffernketten und Satzzeichen und
führt einen kleinen Zustand „Kontext aktiv“ mit. Eine Ziffernkette wird nur dann ein Fund, wenn ein Kontextwort sie
erschließt (E-2); die Prüfziffer (`KvCheckDigits`) bestimmt **nur Stufe und Quelle**, nie, ob ein Fund entsteht (A-2).
Fundtypen `IK` und `LANR` kommen als Konstanten in `FindingType`. Konfliktauflösung, Pseudonymisierung und
Rückübersetzung bleiben unverändert (Schlüssel Typ + Wert, Enthaltensein vor Konfidenz, Zusammenlegung D-8).

Folge für die Quellenlage: Ist ein Prüfziffernverfahren falsch beschrieben, ändern sich nur Stufe und Quelle eines
Fundes. Fundbereich und Schutz hängen ausschließlich am Kontextwort und an der Ziffernkette.

---

## 2. Betroffene Module, Pakete, Typen

Nur `skillshield-core`. Kein POM, keine neue Abhängigkeit, nur `java.lang` und `java.util` (Positivliste bleibt
unverändert; `java.util.function` wird bewusst **nicht** gebraucht, daher Enum statt Lambda).

### 2.1 Neue Dateien (`src/main/java/de/skillshield/core/recognizer/kv/`)

```java
/** Prüfziffernverfahren LANR und IK (Sekundärquellen-Stand, Vermerk Kap. 4). */
final class KvCheckDigits {                       // package-private, nur static
    /** @param d genau 9 Werte 0..9, sonst IllegalArgumentException("genau neun Ziffern erwartet") ohne Werte */
    static boolean isValidLanr(int[] d);          // Stellen 1-6 abwechselnd x4/x9, Summe mod 10, PZ = (10 - r) mod 10
                                                  // == d[6]; Stellen 8-9 ungeprüft
    static boolean isValidIk(int[] d);            // Stellen 3-8 Gewichte 2,1,2,1,2,1, je Produkt Quersumme,
                                                  // Summe mod 10 == d[8] (Lesart "Rest", OF-1)
}

enum KvArt {                                      // package-private
    LANR(FindingType.LANR, 0.95, "lanr", Set.of("lanr", "lanrnr", "arztnummer", "arztnr"), Set.of("arzt")),
    IK  (FindingType.IK,   0.92, "ik",   Set.of("ik", "iknr", "iknummer", "institutionskennzeichen"), Set.of());
    // Felder: type, confidenceDirekt, source (Grundquelle), kontextwoerter (ASCII klein),
    //         vorwoerter (nur mit Nachsatzwort "nr"/"nummer" ein Kontextwort, z. B. "Arzt-Nr.")
    String sourcePruefzifferUngueltig();          // source + "-pruefziffer-ungueltig"
    String sourceLaengeAbweichend();              // source + "-laenge-abweichend"
    boolean isValid(int[] d);                     // delegiert an KvCheckDigits
}

final class KvNumberScanner {                     // package-private, nur static, Algorithmus Kap. 3
    static final double CONFIDENCE_KONTEXT_ENTFERNT = 0.80;
    static final double CONFIDENCE_PRUEFZIFFER_UNGUELTIG = 0.50;
    static final double CONFIDENCE_LAENGE_ABWEICHEND = 0.40;
    static List<Finding> detect(KvArt art, String text);   // NPE("text") bei null
}

public final class LanrRecognizer implements Recognizer {  // detect -> KvNumberScanner.detect(KvArt.LANR, text)
    public static final String SOURCE = "lanr";
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "lanr-pruefziffer-ungueltig";
    public static final String SOURCE_LAENGE_ABWEICHEND = "lanr-laenge-abweichend";
}
public final class IkRecognizer implements Recognizer {    // analog: "ik", "ik-pruefziffer-ungueltig", "ik-laenge-abweichend"
}
```

Zwei öffentliche Klassen statt einer Klasse mit zwei Fabrikmethoden: Der Abgleichtest (AK-22) vergleicht die
Erkennerliste dann allein über die Klassen, ohne zusätzlichen Zugriff auf die Konfiguration.

### 2.2 Geänderte Dateien (Produktivcode)

| Datei | Änderung |
|-------|----------|
| `model/FindingType.java` | `public static final FindingType IK = new FindingType("IK");` und `LANR` (T-2) |
| `recognizer/TextScanner.java` | `standard()` hängt `new LanrRecognizer(), new IkRecognizer()` **hinten** an (Index 0–3 bleiben E-Mail, IPv4, URL, Pfad); neue Methode `public List<Recognizer> recognizers()` liefert die bereits unveränderliche Liste; Javadoc von `standard()` nachgeführt (T-6) |

Keine weitere Änderung am Produktivcode. `ConflictResolver` bleibt unverändert: `IK` und `LANR` fallen in den Typrang
„übrige“ (4), bei Gleichstand entscheidet der Typname (`IK` vor `LANR`), deterministisch.

**Entscheidung Listenzugriff (Folgepunkt T-18 des Vorgängers):** öffentliche Lesemethode `recognizers()` statt
paketprivatem Zugriff, weil `RoundtripFuzzTest` und `GrenzlastTest` im Paket `de.skillshield.core` liegen. Beide Tests
nehmen die Liste künftig aus `TextScanner.standard().recognizers()`; eine fest verdrahtete Kopie, die veralten kann,
entfällt. Der Abgleichtest pinnt zusätzlich die erwarteten sechs Klassen in fester Reihenfolge, damit ein künftig
hinzukommender Standard-Erkenner auffällt und der Fuzz-Generator bewusst ergänzt wird (der Fuzz-Test greift über
Index 3 auf die Pfad-Kandidaten zu).

---

## 3. Erkennungsregeln (`KvNumberScanner`)

### 3.1 Zeichenklassen

| Begriff | Definition |
|---------|-----------|
| Ziffer | nur ASCII `0`–`9` (A-4) |
| Buchstabe | Codepunkt mit `Character.isLetter` oder Kombinationszeichen (Typ `NON_SPACING_MARK`, `ENCLOSING_MARK`, `COMBINING_SPACING_MARK`); Iteration per Codepunkt |
| Zeilenumbruch | `\r\n` (zählt als einer), `\n`, `\r`, U+0085, U+2028, U+2029 |
| Leerraum | `Character.isWhitespace` oder `Character.isSpaceChar`, ohne Zeilenumbruch (Leerzeichen, Tab, U+00A0 …) |
| Satzende | `.` (Sonderregel 3.3), `!`, `?` |
| Sonstiges | alles andere (`: = ; , - ( ) [ ] # / @ \ "` …, Emoji); neutral |
| Gruppentrenner | in einer Ziffernkette genau eines von: Leerzeichen, U+00A0, `-`, `.`, `/` (AK-6) |
| Wort | maximale Folge von Buchstaben |
| Kontextwort | Wort, ASCII-fallunabhängig (nur `A–Z` wird gefaltet; kein `toLowerCase`, damit `ı`/`İ` nicht zu `i` werden) gleich einem Eintrag aus `KvArt.kontextwoerter`; oder ein Wort aus `vorwoerter`, dem nach höchstens einem `-`, Leerzeichen oder U+00A0 das Wort `nr` oder `nummer` folgt (Kontextwort reicht dann bis zu dessen Ende). Wortgrenze ergibt sich aus der Wortdefinition: `Technik`, `Fabrik`, `IKEA`, `LANRX` enthalten kein Kontextwort; `Praxis-LANR`, `Praxis-IK`, `?ik=` schon (AK-11, AK-2, AK-4) |
| Nachsatzwort | `nr`, `nummer` (fallunabhängig); unmittelbar nach dem Kontextwort (Zustand `direkt`) zählt es nicht als Füllwort (`IK-Nr.`, `IK Nr.`, `LANR-Nummer`) |

### 3.2 Ziffernketten und Segmente (AK-6, AK-11)

Eine **Kette** beginnt an einer Ziffer und besteht aus Ziffernblöcken, die durch je genau einen Gruppentrenner verbunden
sind, dem wieder eine Ziffer folgt. Zwei Trenner hintereinander oder ein Trenner am Ende beenden die Kette (der Trenner
gehört nicht dazu). Ab dem Kettenanfang werden Präfixe aus ganzen Blöcken betrachtet (Ziffernsumme steigt streng):
1. Gibt es ein Präfix mit **genau 9 Ziffern** → **Segment** = dieses Präfix; Prüfziffer über die 9 Ziffern ohne Trenner.
2. Sonst das **längste Präfix mit 6 bis 12 Ziffern** → Segment mit Längenabweichung (Prüfziffer wird nicht berechnet).
   Weitergehend als AK-11 (dort nur zusammenhängend): auch `0123 456 789` hinter einem Kontextwort wird so geschützt.
3. Sonst kein Segment; der erste Block zählt als Füllwort.

Danach geht der Durchlauf hinter dem Segment (Fall 1, 2) bzw. hinter dem ersten Block (Fall 3) weiter; der Rest der
Kette wird als neue Kette gelesen. Damit gilt: `LANR 001235101 2025` → Fund nur über die neun Ziffern;
`IK 001234561-2025` ebenso; `001 235 101 001 236 201` → zwei Segmente; `IK 1234`, `IK 1234567890123` → kein Segment.
Die Präfixsuche bricht ab, sobald mehr als 12 Ziffern gezählt sind (auch mitten in einem Block).

### 3.3 Zustand und Funde (je Erkenner ein Durchlauf)

Zustand: `aktiv`, `direkt`, `woerter`, `umbrueche`, `offen` (letztes nicht ausgegebenes Segment, für nachgestellten
Kontext). Ereignisse in Textreihenfolge:

| Ereignis | Wirkung |
|----------|---------|
| Kontextwort | Ist `offen` gesetzt → dieses Segment ausgeben (nachgestellt, AK-13). Dann `aktiv = direkt = true`, `woerter = umbrueche = 0`, `offen` leer. |
| Segment, `aktiv` | ausgeben; danach `direkt = false`, `woerter = umbrueche = 0` (Aufzählung, AK-15; Liste je Zeile ebenso). |
| Segment, nicht `aktiv` | `offen` = Segment. |
| Füllwort (auch Ziffernblock ohne Segment; Nachsatzwort außerhalb von `direkt`) | `woerter++`, `direkt = false`; bei `woerter > 3` → `aktiv = false` (AK-12). `offen` leer. |
| Leerraum, Sonstiges | nichts. Für `offen`: erlaubt sind nur Leerraum und **höchstens ein** `(` oder `[` zwischen Segment und Kontextwort; jedes andere Zeichen leert `offen`. |
| `.` | wenn `aktiv` und nicht `direkt` → `aktiv = false` (Satzende). Direkt hinter dem Kontextwort ist `.` ein Abkürzungspunkt (`LANR-Nr. …`). `offen` leer. |
| `!`, `?` | `aktiv = false`, `offen` leer. |
| Zeilenumbruch | wenn `aktiv`: `umbrueche++`, `direkt = false`; bei `umbrueche > 1` → `aktiv = false` (Leerzeile, AK-14). `offen` leer. |

**Konfidenz, Stufe, Quelle** (OF-6, A-5):

| Fall | Konfidenz | Stufe | Quelle |
|------|-----------|-------|--------|
| 9 Ziffern, Prüfziffer gültig, Segment bei `direkt` | LANR 0.95 / IK 0.92 | sicher | `lanr` / `ik` |
| 9 Ziffern, Prüfziffer gültig, sonst (Füllwörter, Umbruch, Aufzählung, nachgestellt) | 0.80 | wahrscheinlich | `lanr` / `ik` |
| 9 Ziffern, Prüfziffer ungültig | 0.50 | fraglich | `lanr-pruefziffer-ungueltig` / `ik-…` |
| 6–8 oder 10–12 Ziffern | 0.40 | fraglich | `lanr-laenge-abweichend` / `ik-…` |

Fundbereich = genau das Segment (Ziffern samt inneren Trennern), nie das Kontextwort (A-3). Ein Erkenner liefert keine
überlappenden Kandidaten. Gleicher Bereich mit IPv4 (`IK 198.51.100.1`) wird nach der bestehenden Regel aufgelöst
(OF-4): IK-Kandidat fraglich 0.50 (Prüfziffer über `198511001` ungültig) < IP 0.85 → genau ein Fund, Typ IP.

### 3.4 Linearität (Pflichtinhalt des Javadoc von `KvNumberScanner`)

Ein Durchlauf, keine Rekursion, kein Regex, kein Zurücksetzen der Leseposition. Jedes Zeichen wird als Teil genau eines
Ereignisses verarbeitet; die Kontextwortprüfung vergleicht nur Wörter bis 23 Zeichen und schaut für das Nachsatzwort
höchstens 8 Zeichen voraus. Die Präfixsuche einer Kette liest den ersten Block ganz und danach höchstens 13 Ziffern und
13 Trenner; da der Durchlauf mindestens hinter dem ersten Block weitergeht, wird jeder Block höchstens 13-mal berührt.
Gesamtaufwand O(n), Zusatzspeicher O(1) plus Fundliste.

### 3.5 Bekannte Grenzen (je ein Test `bekannteGrenze…`, Verhalten wird festgehalten, nicht verändert)

1. Tabelle: Spaltenüberschrift `LANR`, Leerzeile, Nummern darunter → kein Fund (E-2, AK-14).
2. Kurzer Ziffernblock vor der Nummer: `LANR 12 001235101` → ein Fund über `12 001235101`, fraglich (Längenregel).
3. Punkt direkt hinter dem Kontextwort gilt als Abkürzung: `LANR. Vorgang 001235101` → Fund, wahrscheinlich.
4. Weitere Nummer bis drei Wörter hinter einem Fund wird Fund: `LANR 001235101 Fax 0123456789` → zweiter Fund
   fraglich (Längenabweichung); Prüfung durch den Menschen.
5. Nummer zwischen zwei Kontextwörtern verschiedener Art: `IK 001234561 LANR 001235101` → der LANR-Erkenner liefert für
   die erste Zahl einen nachgestellten Kandidaten; der Resolver wählt nach Konfidenz (IK 0.92) – Schutz unverändert.
6. Nicht-ASCII-Ziffern (Vollbreite) → kein Fund (A-4).
7. Nullbreitenzeichen in der Nummer: `LANR 001235` + U+200B + `101` → Fund nur über `001235` (fraglich), Rest bleibt
   Klartext (Abgrenzung der Anforderung).

---

## 4. Quellenlage der Prüfziffernverfahren (OF-1)

Stand: Sekundärquellen, **nicht** gegen die offiziellen Spezifikationen verifiziert (KBV-Richtlinie nach § 75 Abs. 7
SGB V; „Gemeinsames Rundschreiben Institutionskennzeichen“ der ARGE IK). IK-Lesart „Rest“ (AK-8). Pflichtvermerk im
Klassen-Javadoc von `KvCheckDigits`, `KvCheckDigitsTest` und `SynthetischeKvNummern` (AK-29), wörtlich:

> Quelle: <Sekundärquelle mit Titel/URL>, abgerufen am <Datum>. Status: Sekundärquelle, nicht gegen die offizielle
> Spezifikation verifiziert. Offizielle Quelle: <Titel>, <Stand der Einsicht>.

| Verfahren | Sekundärquelle (Titel, URL) | Abrufdatum |
|-----------|-----------------------------|------------|
| LANR | Wikipedia, „Lebenslange Arztnummer“, Abschnitt „Aufbau“, https://de.wikipedia.org/wiki/Lebenslange_Arztnummer; gleichlautend: krankenkasseninfo.de, Krankenkassenlexikon, Eintrag „Arztnummer“, https://www.krankenkasseninfo.de/zahlen-fakten/lexikon/arztnummer | 21.11.2026 |
| IK | Wikipedia, „Institutionskennzeichen“, Abschnitt „Prüfverfahren“, https://de.wikipedia.org/wiki/Institutionskennzeichen | 21.11.2026 |

**Ergebnis der Recherche der Hauptsitzung (21.11.2026), verbindlich für T-1 und T-2:**

- **LANR.** Beide Sekundärquellen nennen dasselbe Verfahren wie AK-7: Ziffern 1 bis 6 von links abwechselnd mit 4
  und 9 multiplizieren, Summe modulo 10, Prüfziffer = 10 − Rest, bei Differenz 10 ist die Prüfziffer 0. Ein
  Rechenbeispiel enthalten die Quellen nicht. Kontrollrechnung der Hauptsitzung für `001235101`:
  0·4 + 0·9 + 1·4 + 2·9 + 3·4 + 5·9 = 79, Rest 9, Prüfziffer 1 (Stelle 7 = 1, passt).
- **LANR, offizielle Quelle eingesehen:** „Richtlinie der Kassenärztlichen Bundesvereinigung nach § 75 Absatz 7
  SGB V zur Vergabe der Arzt-, Betriebsstätten-, Praxisnetz- sowie der Netzverbundnummern“, Stand 1. Januar 2024,
  https://www.kbv.de/documents/infothek/rechtsquellen/weitere-vertraege/praxen/arzt-und-betriebsstaettennummer/Arztnummern_Richtlinie.pdf.
  § 2 Abs. 1 bestätigt den **Aufbau** (Ziffern 1–6 Ziffernfolge, Ziffer 7 Prüfziffer, Ziffern 8–9
  Arztgruppenschlüssel). Die **Berechnung** der Prüfziffer steht dort nicht; § 2 Abs. 2 verweist auf die
  „Vereinbarung ANRV“ (zentrale Arztnummernvergabe), die nicht eingesehen wurde. Wortlaut für den Pflichtvermerk:
  „Offizielle Quelle: Richtlinie der KBV nach § 75 Absatz 7 SGB V (Stand 1. Januar 2024), eingesehen; sie bestätigt
  den Aufbau, enthält aber das Rechenverfahren nicht (Verweis auf die Vereinbarung ANRV, nicht eingesehen).“
- **IK.** Die Sekundärquelle rechnet ein Beispiel vor (Ziffern hier bewusst nicht wiedergegeben): Die ersten
  beiden Stellen (Klassifikation) gehen nicht ein; die Stellen 3 bis 8 werden von links mit 2, 1, 2, 1, 2, 1
  gewichtet, von Produkten über 9 wird die Quersumme gebildet (gleichbedeutend: 9 abziehen), die Summe modulo 10 ist
  die Prüfziffer (Lesart „Rest“, AK-8). Der Fließtext derselben Quelle sagt „von rechts beginnend mit der Gewichtung
  1,2,1,2,1,2“; das ist bei sechs Stellen dieselbe Zuordnung wie 2,1,2,1,2,1 von links, also kein Widerspruch.
  Kontrollrechnung für `001234561`: 2 + 2 + 6 + 4 + 1 + 6 = 21, Rest 1 (Stelle 9 = 1, passt).
  Das Beispiel-IK der Quelle hat eine echte Klassifikation und wird **nicht** in
  Tests oder Kommentare übernommen (AK zu synthetischen Testdaten); als Vektoren dienen nur Nummern mit Präfix `00`.
- **IK, offizielle Quelle:** „Gemeinsames Rundschreiben Institutionskennzeichen“ der ARGE IK, nicht eingesehen. Die
  Internetseiten der ARGE IK (dguv.de/arge-ik) nennen nur, dass es eine Prüfziffer gibt, nicht das Verfahren.

Der engineer recherchiert nicht selbst. Zeigt ein späterer Beleg eine andere Lesart, werden nur `KvCheckDigits`, die
Vektoren und AK-7/AK-8 angepasst; Erkennungsregeln und Fundbereiche bleiben.

---

## 5. Wiederverwendung

`Finding` (Quellenformat `[a-z][a-z0-9-]{0,31}` trägt den Prüfziffer-Hinweis, OF-7), `FindingType`, `ConfidenceLevel`
(Schwellen 0.90/0.70), `Recognizer`, `TextScanner` (Kapselung von Erkennerfehlern), `ConflictResolver` (unverändert),
`Pseudonymizer`/`Reidentifier` (Schlüssel Typname + Originalwert, OF-5 geprüft: Kap. 2.6 Schritt 4 des Vorgängers),
Testhilfen `TestTexte`, Prüftests `ArchitekturRegelnTest`, `KlartextFreiheitTest`, `TestdatenSynthetischTest`,
`RoundtripFuzzTest`, `GrenzlastTest`, `RoundtripKorpusTest`. Vorbild des Durchlaufs: `Ipv4Recognizer`.

## 6. Neue Abhängigkeiten

Keine.

---

## 7. Sicherheits- und Datenschutzaspekte

- **Kein Netz, keine Datei, keine Online-Prüfung** (AK-23): reine Zeichenprüfung; `ArchitekturRegelnTest` erfasst die
  neuen Dateien automatisch; JDK-Positivliste unverändert.
- **Kein Klartext** (AK-24): `Finding.toString()` bleibt ohne Wert; der Prüfziffer-Hinweis ist eine feste Quellkonstante
  ohne Ziffern; `KvCheckDigits` meldet Fehler ohne Werte; der Scanner wirft nur `NullPointerException("text")`.
- **Schutz vor Stufe**: Ungültige Prüfziffer und Längenabweichung liefern Funde (fraglich), nichts wird verworfen
  (A-2, AK-27). Ein Irrtum im Verfahren ändert nur Stufe/Quelle.
- **Schutzgarantie bleibt** (Vorgänger D-8, AK-7 (a)): Scan-Abdeckung = Vereinigung aller Kandidaten aller
  Standard-Erkenner; belegt im Fuzz- und Grenzlasttest mit der Liste aus `recognizers()`. Bestehende Funde werden nie
  kleiner: Differenztest gegen die Erkennerliste vor dem Feature (T-7).
- **Restlücken** (bewusst, Kap. 3.5 und E-2): Nummern ohne Kontextwort, nach Leerzeile, nach mehr als drei Wörtern
  bleiben ungeschützt, wenn sie nicht manuell übergeben werden (Vorgänger AK-31).
- **Testdaten** (AK-28): nur rechnerisch gebildete Nummern mit führendem `00` in `SynthetischeKvNummern`; Restrisiko
  einer real vergebenen Nummer dort vermerkt; nie mit Namen, Adressen oder Diagnosen kombiniert.
- **Laufzeit**: linearer Durchlauf (3.4), harte 2-s-Grenze (OF-8).

---

## 8. Verbindliche Regeln für engineer und Prüfer

1. Kein Regex im neuen Produktivcode; handgeschriebener linearer Durchlauf; Linearitätsbeleg im Javadoc (3.4).
2. Laufzeittests: je Eingabe von 1 000 000 Zeichen `assertTimeoutPreemptively(2 s)` nach einem nicht gemessenen
   Aufwärmlauf (10 000 Zeichen derselben Bauart) **und exakte Fundzahl**; die erwartete Zahl ist im Test als Formel
   mit Rechenweg begründet, nicht aus einem Probelauf übernommen. Überschreitung → Erkenner korrigieren, nie die
   Grenze lockern (OF-8).
3. Jede Regel aus Kap. 3 und jedes Beispiel aus den AK ist ein **eigener benannter Test** (Name nennt AK und Fall,
   z. B. `ak2_kontextwortLanrNr`); geprüft werden Bereich, Typ, Konfidenz, Stufe und Quelle; jeder Fund erfüllt
   `text.substring(start, end).equals(originalValue)`.
4. Eigenschaftstests mit festem Seed und unabhängigem Orakel (Prüfziffer: eigene Referenzimplementierung in
   `SynthetischeKvNummern`; Erkennung: Erwartung aus der Konstruktion des Textes); Meldungen nennen Seed und Iteration,
   nie Text.
5. Bekannte Grenzen als Tests `bekannteGrenze…` (3.5).
6. Kein Klartext in Ausnahmen, Meldungen, `toString()`; keine Ausgabe, kein Logging.
7. Keine neue Abhängigkeit; nur `java.lang`/`java.util`. Bräuchte ein Task ein weiteres JDK-Paket, ist das vorher im
   Plan zu begründen und `ERLAUBTE_JDK_PAKETE` im `ArchitekturRegelnTest` ausdrücklich zu erweitern.
8. Nicht-ASCII-Zeichen im Quelltext nur als Unicode-Escapes (z. B. NBSP-Escape); Zeilen höchstens 120 Zeichen.
9. Neunstellige Ziffernfolgen (auch mit Trennern) und alle IK/LANR-Testnummern (auch abweichende Längen) stehen als
   Literal nur in `SynthetischeKvNummern`; Tests bilden Texte per Verkettung mit deren Konstanten.
10. Bestehende Tests werden nur an den in den Tasks genannten Stellen geändert; keine bestehende Erwartung ändert sich.

---

## 9. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün. Tests unter
`skillshield-core/src/test/java/de/skillshield/core/…`, Methodennamen deutsch.

### T-1: Testdatenregel und zentrale Klasse synthetischer Nummern
- **Vorab (Hauptsitzung)**: Sekundärquellen (Titel, URL, Abrufdatum) für LANR und IK in Kap. 4 eintragen. Ohne Eintrag
  startet T-1 nicht.
- **Dateien**: neu `SynthetischeKvNummern.java` (Paket `de.skillshield.core`); geändert `TestdatenSynthetischTest.java`.
- **`SynthetischeKvNummern`**: Javadoc mit Pflichtvermerk (Kap. 4), Bildungsregel A-6/OF-2 und Restrisiko.
  Konstanten aller AK-Vektoren (LANR gültig `001235101`, `001235199`, `001234001`, `001236201`; ungültig `001235201`,
  `001325101`, `001235001`; IK gültig `001234561`; ungültig `001234569`, `001234560`, `001243561`), Trennerformen aus
  AK-6, Längenproben aus AK-11, je eine Marker-Nummer LANR und IK (gültig, `00…`, von allen anderen verschieden) für
  T-9; Rechenweg je Vektor als Kommentar. Unabhängige Referenz `static boolean lanrGueltigReferenz(String)` und
  `ikGueltigReferenz(String)` (anders formuliert als das Produkt, z. B. über Gewichtstabelle) und
  `static String bildeLanr(Random)`/`bildeIk(Random)` (rechnerisch, Präfix `00`).
- **`TestdatenSynthetischTest`**, neue Regel „Ziffernketten“ (zeilenweise, wie E-Mail/IPv4):
  - Kette = Ziffernblöcke, verbunden durch je genau ein Zeichen aus Leerzeichen, `-`, `.`, `/` oder durch ein
    NBSP-Escape (Hexziffern groß oder klein). **Verstoß**, wenn die Kette insgesamt genau 9 Ziffern hat oder einen
    Block aus genau 9 Ziffern enthält.
  - Zulässig: (a) Kette direkt hinter `_` (Platzhalternummer, z. B. `[[EMAIL_` + neun Ziffern + `]]`); (b) gültige
    IPv4 im Dokumentationsbereich (bestehende Prüfung, AK-18); (c) Datei `SynthetischeKvNummern.java` – dort muss
    jede Kette mit 9 Ziffern mit `00` beginnen (eigener Test); (d) geschlossene Ausnahmeliste `AUSNAHMEN_ZIFFERN`
    zwischen den vorhandenen Markierungszeilen, exakte Kette mit Begründung, verwaiste Einträge machen den Test rot.
  - Bestand (vom architect per Suche geprüft): erwartet sind genau zwei Einträge für (d): `123456789` (Argument von
    `PlaceholderSyntax.format` in `PlaceholderMappingTest`; Zählfolge, nach beiden Verfahren ungültig) und
    `110.0.0.1234` (Kettenprobe in `Ipv4RecognizerTest`, Block über 255). Neun Ziffern hinter `_` stehen in
    `PlaceholderMappingTest`, `PlaceholderSyntaxTest`, `EscapingRoundtripTest`, `ReidentifierTest` (Regel a).
    `0123456789` (10 Ziffern), Datumsangaben und IP-Ketten erfüllen die Regel ohne Eintrag. Findet der engineer
    weitere Treffer, nennt er sie einzeln; bei mehr als fünf weiteren hält er an und meldet (kein Massenumbau).
  - Selbsttests: Verstoß bei `001 235 101` usw. (nur verkettet geschrieben), kein Verstoß bei 8/10 Ziffern, hinter
    `_`, Doku-IP; Ausnahmeliste geschlossen; Untergrenze geprüfter Ketten.
- **AK**: AK-28, AK-29 (Testklasse).
- **Tests**: `TestdatenSynthetischTest` (erweitert); `SynthetischeKvNummernTest`: jeder Vektor ist nach Referenz
  gültig/ungültig wie benannt; AK-8-Vektor unterscheidet „Rest“ und „Komplement“; `bilde…` liefert 1 000 nach Referenz
  gültige Nummern mit Präfix `00` (fester Seed).

#### Nachtrag der Hauptsitzung zu T-1 (21.11.2026, nach Prüfung von Durchlauf 1)

Die Regel „Ziffernketten“ wird gegenüber dem Wortlaut oben verschärft (Richtung: mehr schützen). Anlass: Hinweise von
reviewer und security. Verbindlich ab Durchlauf 2:

1. **Unterstrich-Freigabe (a) enger:** nur als Teil eines Platzhalters, also unmittelbar hinter `[[` + Typname + `_`,
   als genau ein Ziffernblock und gefolgt von `]]`. Eine Kette hinter einem anderen Unterstrich (Dateiname,
   Bezeichner) ist ein Verstoß.
2. **Escapes:** Unicode-Escapes für Ziffern und Trenner werden vor der Prüfung aufgelöst (nicht nur NBSP).
3. **Trenner:** alle Gruppentrenner aus Kap. 3.1, dazu `/`, `.`, `-` und der Unterstrich zwischen zwei Ziffern
   (Java-Zahlenliteral).
4. **Gleitende Neunerfolge:** In Ketten ohne Punkt ist jede Folge aufeinanderfolgender Blöcke mit zusammen genau
   9 Ziffern ein Verstoß (schließt die Lücke zu AK-28 „jede Folge von neun Ziffern“). Ketten mit Punkt behalten die
   Regel „insgesamt 9 oder Block mit 9“, damit IP-Ketten nicht massenhaft anschlagen; das ist eine bekannte Grenze.
   Dasselbe gilt für reine Java-Zahlenliterale (alle Trenner sind Unterstriche): Sie werden nicht gleitend geprüft,
   weil sonst Zeitgrenzen wie `2_000_000_000L` anschlagen (fünf Stellen im Bestand).
5. **Ausnahmeliste:** Jeder Eintrag ist an genau eine Datei gebunden. Der Bestand braucht sechs statt der erwarteten
   zwei Einträge: drei weitere Doku-IP-Proben aus `Ipv4RecognizerTest` und der Grenzwert aus drei Dreierblöcken
   Neunen in `PlaceholderSyntaxTest` (Zahlenliteral; nach dem LANR-Verfahren zufällig rechnerisch gültig, nach dem
   IK-Verfahren ungültig; im Test ein Zahlen-Grenzwert, keine KV-Nummer).
6. **Prüfbaum:** Die Ziffernketten-Regel liest zusätzlich `skillshield-core/src/test/resources` und die Testbäume der
   Module io, vault, ui, app. E-Mail- und IPv4-Regel bleiben auf dem bisherigen Baum.
7. **Meldungen:** Verstoßmeldungen der Ziffernketten-Regel nennen Datei, Zeile, Spalte und Art, nicht die Ziffern.
8. **Bekannte Grenzen der Regel** (im Javadoc des Tests genannt, durch Durchsicht von reviewer und security gedeckt):
   verkettete Literale, `char`-Arrays, Aufteilung über Zeilen oder Textblöcke, Trenner außerhalb der Liste, Dateien
   außerhalb der geprüften Bäume (`docs/`, `profiles/`).
10. **Nach Prüfung von Durchlauf 2 (security):**
    - Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder IK-Verfahren gültig ist und nicht
      mit `00` beginnt (A-6, AK-28); ein Wächtertest in `SynthetischeKvNummernTest` hält das fest. Das gilt für
      alle Testtexte dieses Features. Im älteren Bestand gibt es Ziffernfolgen in IP-Proben des `Ipv4RecognizerTest`
      (darunter die Ausnahme-Einträge `192.0.2.10.5.7` und `192.0.2.10/24`) und den Zahlen-Grenzwert im
      `PlaceholderSyntaxTest`, die nach dem LANR-Verfahren zufällig rechnerisch gültig sind; sie stehen in IP- bzw.
      Zahlenkontext, haben keinen Personenbezug und bleiben unverändert (Folgepunkt: Begründungen der beiden
      IP-Einträge um diesen Hinweis ergänzen).
    - Punkt 4 gilt jetzt je punktfreier Teilkette: Ketten mit Punkt werden an den Punkten geteilt und jede Teilkette
      gleitend geprüft. Eine Neunerfolge, die über Punkte hinweg entsteht (z. B. als Anfang einer längeren Kette),
      bleibt unerkannt; eine Regel dafür hätte 17 Stellen im `Ipv4RecognizerTest` getroffen. Bekannte Grenze, als
      Test `…bekannteGrenze…` festgehalten.
    - Das Unicode-Escape des Backslashs ist im geprüften Text ein eigener Verstoß.
    - Dateien, die kein gültiges UTF-8 sind, werden als ISO-8859-1 gelesen statt übersprungen; Dateien über 5 MB sind
      ein Testfehler; Symlinks auf Dateien werden nicht verfolgt; die Projektwurzel und die vier Nachbar-Testbäume
      müssen gefunden werden. UTF-16 und Binärformate bleiben ungeprüft (bekannte Grenze).
9. **AK-29:** Der Vermerk steht in `SynthetischeKvNummern`; `SynthetischeKvNummernTest` verweist darauf. Das gilt als
   erfüllt (Kap. 4 nennt die Klassen mit Pflichtvermerk abschließend).

### T-2: Fundtypen und Prüfziffernverfahren
- **Dateien**: `model/FindingType.java` (Konstanten `IK`, `LANR`); neu `recognizer/kv/KvCheckDigits.java`,
  `recognizer/kv/package-info.java`; Test `recognizer/kv/KvCheckDigitsTest.java`, `model/FindingTypeTest` (ergänzt).
- **AK**: AK-7, AK-8 (Verfahren), AK-29, AK-23, AK-25.
- **Tests**: je Vektor aus AK-7/AK-8 ein benannter Test mit Rechenweg; Eigenschaftstest 10 000 Zufallsnummern
  (fester Seed) Produkt = Referenz aus `SynthetischeKvNummern`; IK: Ändern einer Ziffer an Stelle 3–9 macht eine
  gültige Nummer ungültig (Eigenschaft des Verfahrens, belegt die Gewichtung); Fehler bei Länge ≠ 9 oder Wert außerhalb
  0–9 mit Meldung ohne Ziffern der Eingabe; `FindingType.IK/LANR` gültig, Platzhalter `[[IK_1]]`, `[[LANR_1]]`.

### T-3: LANR-Erkenner mit direktem Kontext und Ziffernketten
- **Dateien**: neu `recognizer/kv/KvArt.java`, `KvNumberScanner.java`, `LanrRecognizer.java`; Tests
  `recognizer/kv/LanrRecognizerTest.java`, `KvNumberScannerKettenTest.java`.
- **Umfang**: Kap. 3.1, 3.2, 3.4 vollständig; aus 3.3 nur Kontextwort, `direkt`, Segment bei `aktiv`, `.`/`!`/`?`,
  Zeilenumbruch beendet den Kontext (Füllwörter, Umbruch-Toleranz, Aufzählung, nachgestellt folgen in T-5; bis dahin
  beendet jedes Füllwort und jedes ausgegebene Segment den Kontext). Der IK-Eintrag von `KvArt` darf schon angelegt
  sein.
- **AK**: AK-1, AK-2, AK-6, AK-7 (Stufe), AK-9, AK-10, AK-11, AK-16 (a, c, d, e), AK-21 (Erkennerebene), AK-24,
  AK-26.
- **Tests**: je Beispiel aus AK-1, AK-2, AK-6, AK-7, AK-9, AK-10, AK-11, AK-16 ein benannter Test; Kettenregeln 3.2
  (genau 9, Präfix, 6–12, < 6, > 12, Doppeltrenner, Trenner am Ende, U+00A0); Kontextwortgrenzen (`LANRX`, `xLANR`,
  `Arzt` ohne `Nr` kein Kontext, `Arzt-Nr.`, `Arzt Nummer`, `LANR001235101`, `İ`/`ı`-Varianten kein Kontext);
  Quelle `lanr-pruefziffer-ungueltig` ohne Ziffern; Determinismus; Laufzeit auf Erkennerebene mit exakter Fundzahl:
  `LANR ` + `1 ` wiederholt, `LANR` + Leerzeichen, `LANR ` wiederholt, `LANR ` + 1 Mio. Ziffern;
  `bekannteGrenze` 2, 6, 7.

### T-4: IK-Erkenner
- **Dateien**: neu `recognizer/kv/IkRecognizer.java`; `KvArt.java` (IK-Eintrag, falls noch nicht da); Test
  `recognizer/kv/IkRecognizerTest.java`.
- **AK**: AK-3, AK-4, AK-6, AK-8 (Stufe), AK-9, AK-10, AK-11 (IK-Beispiele), AK-21 (Erkennerebene), AK-24.
- **Tests**: je Beispiel aus AK-3, AK-4, AK-6 (IK-Formen), AK-8, AK-9, AK-10, AK-11 ein benannter Test;
  `Technik`/`Fabrik`/`IKEA` kein Kontext; Konfidenz 0.92 sicher; Laufzeit mit exakter Fundzahl: `IK ` + 1 Mio. Ziffern,
  `IK` + `-` wiederholt, `IK ` + gültige IK + ` ` wiederholt (über 70 000 Funde).

### T-5: Erweiterte Kontextregel (Füllwörter, Zeilenumbruch, Aufzählung, nachgestellt)
- **Dateien**: `KvNumberScanner.java` (Kap. 3.3 vollständig); Tests `recognizer/kv/KvKontextTest.java`,
  `recognizer/kv/KvErkennungEigenschaftTest.java`.
- **AK**: AK-12, AK-13, AK-14, AK-15, AK-16 (b), AK-21 (Erkennerebene), AK-26.
- **Tests**: jedes Beispiel aus AK-12 bis AK-15 und AK-16 (b) benannt, für LANR und IK; Grenzfälle 3 vs. 4 Wörter,
  `\n`/`\r\n`/`\r` einfach vs. doppelt, `.` nach Füllwort vs. direkt, Nachsatzwort; `offen` mit `(`, `[`, ohne Klammer,
  mit zwei Klammern, mit Wort dazwischen; Aufzählung je Zeile bis zur Leerzeile; `bekannteGrenze` 1, 3, 4, 5.
  Eigenschaftstest (Seed fest, 1 000 Texte): Blöcke „Kontextwort-Variante + Direktzeichen + Nummer (gültig, ungültig,
  mit Trennern, aus `bilde…`)“ mit bekannter Position und erwarteter Konfidenz, getrennt durch `. ` und Rauschen ohne
  Kontextwörter (Wörter, Ziffernketten, Satzzeichen, Unicode); Orakel: Fundliste = genau die konstruierten Blöcke;
  zusätzlich Teilstring-Invariante, Sortierung, keine Überlappung, Determinismus, keine Funde in Texten ohne
  Kontextwort. Laufzeit mit exakter Fundzahl: `LANR` + Wörter ohne Satzende, `LANR` + Zeilenumbrüche, gültige LANR +
  ` (LANR) ` wiederholt.

- **Nachtrag der Hauptsitzung (21.11.2026, nach Prüfung von T-3):**
  - T-5 **darf und soll** die Tests anpassen, die das vorläufige Verhalten von T-3 festhalten (jedes Füllwort, jeder
    Zeilenumbruch und jedes ausgegebene Segment beendet den Kontext). Das sind in `LanrRecognizerTest` alle Tests mit
    Präfix `t3Uebergang_` und in `KvNumberScannerKettenTest` mindestens `kette_buchstabeAmKettenanfang…`,
    `kette_erstesBlockUeber12Ziffern…`, `kette_mehrAls12ZiffernAusBloeckenOhneFittendesPraefix…`,
    `kette_zweiNeunerSegmenteHintereinander…`, `ereignisse_zeilenumbruchCrLfZaehltAlsEiner…`,
    `ereignisse_buchstabenAusserhalbDesBmp…` sowie die Fundzahl-Formeln der Laufzeittests aus T-3. Jede angepasste
    Erwartung wird im Bericht einzeln genannt und mit der Regel aus Kap. 3.3 begründet; Kap. 8 Regel 10 gilt für
    diese Stellen als erfüllt.
  - Der in T-3 angelegte, dort unerreichbare Zweig „Konfidenz 0.80“ und der Körper von `punkt()` werden in T-5
    erreichbar und müssen dort getestet werden.
  - Für `offen` (nachgestellter Kontext) muss auch die neutrale Verzweigung des Durchlaufs angepasst werden, nicht
    nur die Ereignismethoden.
  - Fehlende Randfälle aus T-3 mit aufnehmen: Vorwort `Arzt` + Zeilenumbruch + `Nr`; Nachsatzwort mit einem
    Buchstaben außerhalb der BMP.
  - **Erweiterungen der Erkennung aus der Sicherheitsprüfung von T-3** (Entscheidung der Hauptsitzung, Richtung
    „mehr schützen“; ergänzen Kap. 3.1 und werden in T-5 umgesetzt und je Fall getestet, für LANR und IK):
    1. Gruppentrenner zusätzlich: U+2009 (schmales Leerzeichen) und U+202F (schmales geschütztes Leerzeichen), da
       als Zifferngruppentrenner verbreitet. Tab bleibt kein Gruppentrenner (Spaltentrenner in Tabellen). Der Test
       `kette_andereLeerraumzeichenSindKeineTrenner` wird entsprechend angepasst.
    2. Zwischen Vorwort und Nachsatzwort (`Arzt-Nr`) zusätzlich erlaubt: U+2011 (geschützter Bindestrich) und
       U+2013 (Halbgeviertstrich).
    3. Unsichtbare Zeichen im und am Kontextwort werden beim Wortvergleich übergangen: Formatzeichen
       (`Character.FORMAT`, z. B. U+00AD, U+200B, U+200C, U+200D, U+FEFF) und Variantenwähler (U+FE00–U+FE0F). Damit
       sind `LA` + U+00AD + `NR` und `LANR` + U+FE0F Kontextwörter. Die Linearität (Kap. 3.4) bleibt zu belegen.
    4. Ein alleinstehendes Kombinationszeichen (ohne Basisbuchstaben) zählt nicht als Füllwort.
    5. Unverändert bekannte Grenze 7: Nullbreitenzeichen **in der Nummer** (Fund nur über den ersten Teil).
    6. `ak21_laufzeit_lanrGefolgtVonEineMillionZiffern_keinFund` nutzt Nullen statt Einsen.
- **Nachtrag für alle Laufzeittests (T-3 bis T-8):** Neun gleiche Ziffern sind nach dem LANR-Verfahren immer
  rechnerisch gültig (Summe 39·x, Prüfziffer x). Wo der Plan `1 ` wiederholt nennt, wird `0 ` wiederholt verwendet
  (neun Nullen beginnen mit `00`); allgemein darf kein Laufzeittext ein Neuner-Segment ohne Präfix `00` erzeugen, das
  nach einem der Verfahren gültig ist (Nachtrag T-1 Punkt 10).

### T-6: Einbindung in den Standard-Scanner, Abgrenzung, Abgleich der Erkennerliste
- **Dateien**: `recognizer/TextScanner.java` (2.2); Tests `recognizer/TextScannerKvTest.java` (neu),
  `recognizer/TextScannerTest.java` (Abgleichtest ergänzt); **angepasst**: `RoundtripFuzzTest` und `GrenzlastTest`
  (Konstante `STANDARD_ERKENNER` → `TextScanner.standard().recognizers()`; nichts sonst).
- **AK**: AK-5, AK-17, AK-18, AK-19, AK-22 (Liste, Abgleich), AK-27.
- **Tests**: AK-5 (`IK [[IK_1]], LANR [[LANR_2]]`); AK-17 je Beispiel (URL, PFAD, EMAIL, genau ein Fund, Typ des
  umschließenden); AK-18 (`IK 198.51.100.1` → genau ein Fund über die Adresse, Typ IP, Rechenweg im Kommentar; kein
  Ziffernrest im pseudonymisierten Text); AK-19 (Text mit E-Mail, IPv4, URL, Pfad, IK, LANR → sechs Funde);
  AK-27 (`LANR 001235201`: Fund fraglich vorhanden; Pseudonymisierung ohne ihn → Text unverändert);
  Abgleichtest: Klassen von `TextScanner.standard().recognizers()` = `[EmailRecognizer, Ipv4Recognizer,
  UrlRecognizer, PathRecognizer, LanrRecognizer, IkRecognizer]` in dieser Reihenfolge; `recognizers()` ist
  unveränderlich. Alle bestehenden Tests grün ohne geänderte Erwartung.

### T-7: Fuzz mit KV-Formen und Differenztest
- **Dateien**: `RoundtripFuzzTest.java`; neu `KvDifferenzTest.java` (Paket `de.skillshield.core`).
- **Änderung Fuzz**: `erzeugeText(Random, boolean mitKv)`; bei `mitKv = false` verbraucht der Generator genau dieselben
  Zufallszahlen wie bisher. Teil 1 bleibt unverändert; ein neuer Test hält den Hashwert der Teil-1-Textfolge als
  Konstante fest (vor der Änderung gemessen). Neuer Teil 1b: 1 000 Texte mit eigenem Seed und KV-Bausteinen
  (alle Kontextwörter, Trennerformen, U+00A0, Umbrüche, Aufzählung, nachgestellt, ungültige Prüfziffer,
  Längenabweichung, KV-Nummer in URL/Pfad/E-Mail, `IK ` + Doku-IP); dieselben Eigenschaften wie Teil 1 inkl.
  Schutzgarantie mit `recognizers()`; Klassen-Mindestzahlen `kv-fund-lanr`, `kv-fund-ik`, `kv-fraglich`,
  `kv-in-anderem-fund`, `kv-gleicher-bereich-ip`.
- **Differenztest**: für alle Texte aus Teil 1 und 1b sowie den Korpus: `alt` = Scan mit `[Email, Ipv4, Url, Path]`,
  `neu` = `standard()`; jede von `alt` abgedeckte Position ist in `neu` abgedeckt; liefern LANR- und IK-Erkenner keinen
  Kandidaten, ist `neu.equals(alt)`.
- **AK**: AK-19, AK-20 (Fuzz-Roundtrip), AK-22, AK-26.
- **Tests**: wie beschrieben; Meldungen nur mit Seed, Iteration, Anzahl.

### T-8: Grenzlast über den Standard-Scanner
- **Dateien**: `GrenzlastTest.java` (neue parametrisierte Methode `ak21_kvEingabe…`; bestehende Fälle unverändert).
- **AK**: AK-21.
- **Tests**: alle zehn Eingaben aus AK-21 mit je 1 000 000 Zeichen durch Scan, Pseudonymisierung, Rückübersetzung je
  < 2 s nach Aufwärmlauf, exakte Fundzahl (Formel im Kommentar; `IK 001234561 ` wiederholt > 70 000 Funde;
  `IK 198.51.100.1 ` wiederholt: je Wiederholung ein Fund Typ IP), Roundtrip gleich.

### T-9: Roundtrip-Korpus, Klartextfreiheit, Architekturregeln
- **Dateien**: `RoundtripKorpusTest.java` (KV-Texte ergänzt), `KlartextFreiheitTest.java`, `ArchitekturRegelnTest.java`
  (nur `BEKANNT` um die vier Klassen unter `recognizer/kv` ergänzt).
- **AK**: AK-20, AK-23, AK-24, AK-25.
- **Tests**: Korpus mit allen Formen aus AK-1 bis AK-16 (Trenner, U+00A0, `\r\n`, Aufzählung, nachgestellt) → Scan,
  alle Funde pseudonymisieren, keine Ziffernkette mit 6+ Ziffern im Ausgabetext, Rückübersetzung zeichengleich;
  Konsistenz: gleiche Zahl zweimal → gleicher Platzhalter; `001235101` vs. `001 235 101` → verschiedene; gleiche Zahl
  unter LANR und IK → `[[LANR_n]]` und `[[IK_m]]`, beide zurück zum Original (OF-5). `KlartextFreiheitTest`: Marker-
  Nummern aus `SynthetischeKvNummern` (voll und erste sechs Ziffern) in `MARKER_TEILE` und `TEXT`; neue Fehlerpfade
  `L01 LanrRecognizer.detect(null)`, `L02 IkRecognizer.detect(null)`; `toString()` aller Funde mit Typ IK/LANR und aller
  drei Quellen je Typ ohne Marker; Quellen enthalten keine Ziffer.

### Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-3 | AK-11 | T-3, T-4 | AK-21 | T-3, T-4, T-5, T-8 |
| AK-2 | T-3 | AK-12 | T-5 | AK-22 | T-6, T-7 |
| AK-3 | T-4 | AK-13 | T-5 | AK-23 | T-2–T-5 (je Datei), T-9 |
| AK-4 | T-4 | AK-14 | T-5 | AK-24 | T-3, T-4, T-9 |
| AK-5 | T-6 | AK-15 | T-5 | AK-25 | jeder Task (Build), T-2, T-9 |
| AK-6 | T-3, T-4 | AK-16 | T-3 (a, c–e), T-5 (b) | AK-26 | T-3, T-5, T-7 |
| AK-7 | T-2, T-3 | AK-17 | T-6 | AK-27 | T-6 |
| AK-8 | T-2, T-4 | AK-18 | T-6 | AK-28 | T-1 (Regel), jeder Task |
| AK-9 | T-3, T-4 | AK-19 | T-6, T-7 | AK-29 | T-1, T-2 |
| AK-10 | T-3, T-4 | AK-20 | T-7, T-9 | | |

---

## 10. Entscheidungen zu OF-1 bis OF-8 (ohne Rückfrage, Richtung „möglichst viel schützen“)

- **OF-1**: Sekundärquellen-Stand, Lesart „Rest“; Quelle trägt die Hauptsitzung vor T-1 ein (Kap. 4). Begründung:
  Nutzerentscheidung; ein Irrtum ändert nur die Stufe.
- **OF-2**: Bildung mit Präfix `00`, rechnerisch, Restrisiko vermerkt, keine Neunerfolgen als KV-Testnummer; der
  Testdaten-Test erzwingt `00` in der zentralen Klasse.
- **OF-3**: wie in den AK, an drei Stellen weiter: Längenregel auch über Trenner hinweg (3.2), Aufzählung auch je Zeile
  bis zur Leerzeile, Kontextwort direkt vor Ziffern ohne Abstand (`LANR001235101`). Zusätzliche Kontextwörter:
  `lanrnr`, `arztnr`, `iknummer`, `Arzt`/`LANR`/`IK` + `Nr`/`Nummer` (auch mit Leerzeichen). Keine weiteren ohne Bedarf.
- **OF-4**: kein Sonderfall; bestehende Resolver-Regel, ganzer Bereich geschützt.
- **OF-5**: geprüft: Pseudonymizer-Schlüssel ist (Typname, Originalwert); keine Änderung nötig.
- **OF-6**: 0.95 / 0.92 / 0.80 / 0.50 / 0.40 wie empfohlen.
- **OF-7**: Quellenkonstanten `…-pruefziffer-ungueltig` und `…-laenge-abweichend`, kein neues Feld.
- **OF-8**: harte Grenze 2 s nach Aufwärmlauf, exakte Fundzahl.

## 11. Abweichungen von Plan.md

1. Plan Kap. 6 verlangt die Verifikation der Prüfziffern gegen die offizielle KBV-/GKV-Spezifikation; umgesetzt wird
   der Sekundärquellen-Stand mit Vermerk (Nutzerentscheidung A-1/OF-1). Folge nur für die Stufe, nicht den Schutz.
2. Plan Kap. 6 nennt BSNR und KVNR; sie folgen in einem eigenen Feature (E-1, zur Freigabe).
3. Kontextwörter sind Konstanten, nicht über Profile/YAML konfigurierbar (Abgrenzung der Anforderung).
4. Kleine öffentliche Erweiterung `TextScanner.recognizers()` (Lesezugriff, nicht in Plan Kap. 5 genannt), Begründung
   Kap. 2.2.

## 12. Entscheidungen für die Freigabe

- **E-1 – BSNR und KVNR nicht in diesem Feature.** Empfehlung: zustimmen. Folge: Bis zum Folgefeature werden
  Versicherten- und Betriebsstättennummern nicht automatisch gefunden und müssen von Hand markiert werden.
- **E-2 – Ohne Kontextwort kein Fund.** Empfehlung: zustimmen. Folge: Eine neunstellige Zahl ohne Wort wie „LANR“
  oder „IK“ davor oder dahinter bleibt im Text stehen, dafür werden Telefon- und Vorgangsnummern nicht massenhaft
  ersetzt.
