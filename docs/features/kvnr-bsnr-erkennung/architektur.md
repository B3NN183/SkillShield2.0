# Architektur: Erkennung von KVNR und BSNR

Feature-Kurzname: `kvnr-bsnr-erkennung`
Status: **Entwurf zur Freigabe**
Grundlage: `anforderung.md` dieses Features (E-1 bis E-3, D-1 bis D-9, A-1 bis A-7, AK-1 bis AK-36, OF-1 bis OF-4),
Vorgängerpläne `../kv-nummern-erkennung/architektur.md`, `../kv-kontext-luecken/architektur.md`,
`../kv-trenner-und-umbruch/architektur.md` (insbesondere dort Kap. 8 mit 8.2, 9a, 9b, Regel 10 und Kap. 11).
Code-Stand: Ende `kv-trenner-und-umbruch` (`KvNumberScanner` mit Sperr- und Weit-Verfolger, `crOffen`).

Schreibweise wie in der Anforderung: `<gültige KVNR>`, `<BSNR>`, `KW`, `UZ`, `CR`, `LF`. `main/…` =
`skillshield-core/src/main/java/de/skillshield/core/`, `test/…` = `skillshield-core/src/test/java/de/skillshield/core/`.

---

## 0. Zusammenfassung in normaler Sprache

**Was gebaut wird.** SkillShield lernt zwei neue Nummern zu erkennen und durch Platzhalter zu ersetzen:

- die **Krankenversichertennummer (KVNR)** der Patientinnen und Patienten (ein Buchstabe und neun Ziffern), Platzhalter
  `[[KVNR_1]]`, und
- die **Betriebsstättennummer (BSNR)** der Praxen (neun Ziffern), Platzhalter `[[BSNR_1]]`.

Die Erkennung arbeitet wie bei LANR und IK: Ein Wort wie „KVNR“, „Versichertennummer“ oder „BSNR“ in der Nähe zeigt,
dass die folgende Nummer gemeint ist. Alle bisherigen Regeln (bis drei Wörter Abstand, Zeilenumbruch, Aufzählungen,
nachgestelltes Wort, gesperrte Schreibweise „K V N R“) gelten auch für die neuen Nummern.

**In welchen Schritten.** Es gibt drei Abschnitte, nach jedem ist das Programm fertig nutzbar:

1. **Teil 1a – KVNR mit Kontextwort** (Tasks T-1 bis T-8). Vorher wird der heutige Stand als Vergleichskopie
   eingefroren, damit jede Veränderung an LANR, IK, E-Mail, Pfad usw. sofort auffällt.
2. **Teil 1b – KVNR ohne Kontextwort** (T-9 bis T-11). Eine KVNR im Fließtext wird auch ohne das Wort davor gefunden,
   aber nur in sehr enger Form (Großbuchstabe, neun Ziffern, gültige Prüfziffer, freistehend) und nur als „fraglich“.
   Vorher wird ein Prüfstand gebaut, der Fehlalarme misst.
3. **Teil 2 – BSNR** (T-12 bis T-15), nur mit Kontextwort.

**Nebenwirkungen (ehrlich).**

- Teil 1b meldet etwa jede zehnte fremde Nummer aus Buchstabe und neun Ziffern (Artikel-, Vorgangs-, Kennzeichen-
  nummern), bei der die Prüfziffer zufällig passt, als „fraglich“. Steht direkt davor ein Wort wie „Rechnungsnummer“
  oder „Artikelnummer“, wird nichts gemeldet.
- Steht vor einer KVNR das Wort „LANR“ oder „IK“, wurden bisher die neun Ziffern als LANR/IK markiert. Künftig wird
  die ganze KVNR (mit Buchstabe) als KVNR markiert, teils mit niedrigerer Stufe. Die Stelle bleibt immer geschützt.
- Passt eine Nummer gleich gut zu zwei Arten (z. B. BSNR und LANR), entscheidet eine feste Reihenfolge über den Namen
  des Typs; der Typ kann dann „falsch“ sein, geschützt und umkehrbar ist die Stelle trotzdem.

**Wo zurückgenommen wird.** Teil 1b steht unter dem Abbruchkriterium der Anforderung (D-5): Zeigt der Prüfstand mehr
Fehlalarme als erlaubt, wird Teil 1b vollständig wieder entfernt (Task T-R), Teil 1a bleibt unverändert, und Sie
werden informiert. Teil 1b ist dafür in einer eigenen Datei gebaut und mit genau einer Zeile eingebunden.

**Vor dem Start nötig:** die Quelle zur KVNR-Prüfziffer (OF-1, vor T-3) und die Liste der Regionalcodes der BSNR
(OF-2, vor T-12) liefert die Hauptsitzung als Nachtrag zur Anforderung.

---

## 1. Lösungsansatz

**KVNR mit Kontextwort (1a) und BSNR (2)** werden als zwei weitere Arten in den bestehenden `KvNumberScanner`
eingehängt (neue Einträge `KvArt.KVNR` und `KvArt.BSNR`), nicht als eigener Scanner. Begründung:

- Alle Kontextregeln der Vorgänger (Füllwörter, Umbruch inkl. `CR UZ+ LF`, Aufzählung, nachgestellt, Komma-Regel,
  Sperr- und Weit-Verfolger, Vorwort mit Nachsatzwort) sind dort einmal und erprobt umgesetzt. Ein eigener Scanner
  müsste etwa 1 200 Zeilen nachbauen; D-3 und D-8 verlangen ausdrücklich „alle Kontextregeln unverändert“.
- Die BSNR ist reine Konfiguration (neun Ziffern, Kontextwörter, Plausibilität statt Prüfziffer).
- Die KVNR braucht genau drei kleine, art-gebundene Ergänzungen (nur wirksam, wenn die Art die Buchstabenform hat;
  der Code für LANR, IK und BSNR läuft unverändert durch):
  1. **Buchstabenform:** Ein Lauf aus genau einem ASCII-Buchstaben, dem unmittelbar eine Ziffer oder genau ein
     Gruppentrenner und eine Ziffer folgt, ist der Anfang einer Kette (nicht Wort, nicht Einzelbuchstabe der
     Sperrschrift). Die Ziffern dahinter werden mit **derselben** Kettenregel wie bisher zerlegt.
  2. **Angeklebte Ziffern:** Eine reine Ziffernkette, vor der unmittelbar ein Buchstabe steht, ergibt für die KVNR
     kein Segment (`AB` plus Ziffern ist keine KVNR-Form, AK-5).
  3. **`KV-Nr.` nur für die Buchstabenform (D-2):** Ein Kontext aus dem Vorwort `kv` trägt einen Merker; er gibt nur
     Segmente in Buchstabenform aus (direkt und nachgestellt).

**KVNR ohne Kontextwort (1b)** ist ein eigener, kleiner linearer Durchlauf in einer eigenen Klasse
(`KvnrOhneKontext`), den `KvnrRecognizer` mit genau einer Zeile hinzufügt. Er passt nicht in die Kontext-Logik des
Scanners (Wortgrenzen, Negativkontext) und ist so ohne Rest entfernbar (D-5).

**Prüfziffer KVNR und Regionalcodes BSNR** stehen je an genau einer Stelle in einer eigenen kleinen Klasse
(`KvnrPruefziffer`, `BsnrRegionalcodes`) mit Quellenvermerk; `KvCheckDigits` bleibt unberührt.

**Bestandsschutz:** In T-1 wird der heutige Scanner als dritte eingefrorene Kopie abgelegt
(`KvNumberScannerStandTrenner`). Gegen sie gilt auf Erkennerebene für LANR und IK **volle Gleichheit ohne jede
Ausnahme**; auf Scannerebene (alle Erkenner) gilt: keine abgedeckte Position geht verloren, und jeder bisherige Fund
bleibt mit gleichem Typ, Bereich und mindestens gleicher Konfidenz – außer in den vier benannten Kollisionsfällen
K-1 bis K-4 (Kap. 4.3), die nur in Texten mit KVNR-/BSNR-Kontextwort bzw. KVNR-Form zulässig sind.

---

## 2. Klärung der beiden Annahmen des product-owners am Code

### 2.1 Wie der `ConflictResolver` bei verschiedenen Typen über derselben Position entscheidet (D-6, AK-18, AK-30)

Belegt in `main/…/conflict/ConflictResolver.java`:

- **Gleicher Bereich:** Der schwächere Kandidat gilt als im stärkeren enthalten und verschwindet ganz; übernommen wird
  der stärkere **unverändert** (Typ, Konfidenz, Quelle). Stärke (`STAERKE`): Konfidenz absteigend, dann Länge
  absteigend, dann Start aufsteigend, dann Typrang (`URL` 0, `PFAD` 1, `EMAIL` 2, `IP` 3, **alle KV-Typen 4**), dann
  **Typname lexikographisch**, dann Quelle. Bei Gleichstand der Konfidenz zwischen KV-Arten gewinnt also
  `BSNR` vor `IK` vor `KVNR` vor `LANR`. Gegen `IP` (Rang 3) verliert jede KV-Art bei Gleichstand.
- **Verschachtelte Bereiche:** Umschließt ein Kandidat einen anderen, gewinnt der umschließende **unabhängig von der
  Konfidenz**. Das betrifft die KVNR in Buchstabenform: Sie umfasst Buchstabe und Ziffern, ein LANR-/IK-Kandidat nur
  die Ziffern – die KVNR gewinnt auch mit niedrigerer Konfidenz (Kollisionsfall K-1).
- **Teilüberlappung:** Fund über die Vereinigung, Typ des stärksten, Konfidenz des schwächsten Glieds, Quelle
  `zusammengelegt` (Kollisionsfall K-4).

Folge für D-6: „Typ folgt der höheren Konfidenz“ gilt genau bei **gleichem Bereich**. Gleicher Bereich ist
gesichert, weil KVNR (reine Ziffern), BSNR, LANR und IK dieselbe Kettenregel verwenden (Segmente hängen nicht vom
Kontext ab). Die Beispiele aus AK-18 sind gleicher Bereich (IK 0,92 bzw. 0,60 gegen KVNR 0,40: IK gewinnt).

### 2.2 Aufzählung hinter einem Kontextwort einer anderen Art (AK-30)

Belegt in `KvNumberScanner` (`lauf`, `fuellwort`, `segment`, `sonstiges`, `offenNachgestelltAusgeben`): Jede Art läuft
**getrennt** über den Text. Für eine Art ist das Kontextwort einer anderen Art ein **gewöhnliches Füllwort**: Es beendet
`direkt` und zählt zu den drei Füllwörtern, beendet aber den Kontext nicht. Eine Nummer hinter einem fremden
Kontextwort erhält daher von der zuerst genannten Art weiter einen Kandidaten (0,80 gültig, 0,50 ungültig bzw.
unbekannter Regionalcode, 0,40 Länge). Ein `/` zwischen einer offenen Nummer und dem nächsten Kontextwort verhindert
den nachgestellten Kontext (`offenSchraeg`); ein einzelnes `,` erlaubt ihn mit Deckel 0,60.

Ergebnis je AK-30-/AK-18-Fall (`B` = BSNR mit bekanntem Regionalcode, als LANR und IK ungültig; `L` = gültige LANR,
Präfix `00`, also Regionalcode unbekannt; `I` = gültige IK, Präfix `00`):

| Text | Kandidaten an Nummer 1 | Kandidaten an Nummer 2 | Ergebnis |
|------|------------------------|------------------------|----------|
| `BSNR B/LANR L` | BSNR 0,90 | LANR 0,95; BSNR 0,50 | BSNR, LANR |
| `LANR L/BSNR B` | LANR 0,95 | BSNR 0,90; LANR 0,50 | LANR, BSNR |
| `BSNR/LANR B/L` | LANR 0,50; BSNR 0,80 | LANR 0,80; BSNR 0,50 | BSNR, LANR |
| `LANR/BSNR L/B` | LANR 0,80; BSNR 0,50 | BSNR 0,80; LANR 0,50 | LANR, BSNR |
| `LANR L, BSNR L` | LANR 0,95; BSNR 0,50 (Komma) | LANR 0,80; BSNR 0,50 | LANR, LANR |
| `LANR B, BSNR B` | LANR 0,50; BSNR 0,60 (Komma) | LANR 0,50; BSNR 0,90 | BSNR, BSNR |
| `IK I (BSNR)` | IK 0,92; BSNR 0,50 | – | IK |
| `IK I, KVNR` | IK 0,92; KVNR 0,40 | – | IK |
| `Versichertennummer I, IK` | KVNR 0,40; IK 0,60 (Komma) | – | IK |

Hinweis: Eine Zahl, die zugleich gültige LANR ist und einen bekannten Regionalcode hat, ist mit synthetischen
Testdaten nicht bildbar (Wächter); „dieselbe Zahl unter LANR und BSNR“ (AK-30) wird deshalb mit `L` und mit `B`
geprüft; in beiden Fällen tragen die Platzhalter den Typ ihres Fundes und führen zum Originalwert zurück.

---

## 3. Betroffene Module, Pakete, Typen

Nur `skillshield-core`. Kein POM, keine neue Abhängigkeit, kein neues JDK-Paket.

### 3.1 Produktivdateien

| Datei | Änderung | Task |
|-------|----------|------|
| `main/…/recognizer/kv/KvNumberScanner.java` | `FENSTER` 24 → 28 (`MAX_KONTEXTWORT` 27), Javadoc Linearität | T-2 |
| `main/…/recognizer/kv/KvnrPruefziffer.java` | **neu**: Prüfziffer KVNR (A-1, OF-1), Quellenvermerk | T-3 |
| `main/…/model/FindingType.java` | Konstante `KVNR` | T-4 |
| `main/…/recognizer/kv/KvArt.java` | Eintrag `KVNR`, neue Felder (Kap. 3.2) | T-4 |
| `main/…/recognizer/kv/KvNumberScanner.java` | Buchstabenform, angeklebte Ziffern, Merker `nurBuchstabenform`, `ausgeben` | T-4 |
| `main/…/recognizer/kv/KvnrRecognizer.java` | **neu** (1a) | T-4 |
| `main/…/recognizer/TextScanner.java` | `standard()` + `KvnrRecognizer` (Index 6), Javadoc | T-6 |
| `main/…/recognizer/kv/KvnrOhneKontext.java` | **neu** (1b), Negativliste D-4 | T-10 |
| `main/…/recognizer/kv/KvnrRecognizer.java` | eine Zeile + Javadoc-Absatz 1b | T-10 |
| `main/…/recognizer/kv/BsnrRegionalcodes.java` | **neu**: Regionalcode-Liste (A-5, OF-2), Quellenvermerk | T-12 |
| `main/…/model/FindingType.java` | Konstante `BSNR` | T-13 |
| `main/…/recognizer/kv/KvArt.java` | Eintrag `BSNR`, `ä`-Formen | T-13 |
| `main/…/recognizer/kv/KvNumberScanner.java` | Faltung `Ä` (U+00C4) → `ä` (U+00E4) in `vergleichszeichen` | T-13 |
| `main/…/recognizer/kv/BsnrRecognizer.java` | **neu** | T-13 |
| `main/…/recognizer/TextScanner.java` | `standard()` + `BsnrRecognizer` (Index 7) | T-14 |

`KvCheckDigits`, `LanrRecognizer`, `IkRecognizer`, `ConflictResolver`, `Pseudonymizer`, `Reidentifier`,
`PlaceholderSyntax` bleiben unverändert.

### 3.2 Signaturskizzen

```java
// model/FindingType.java (T-4, T-13)
/** Krankenversichertennummer (KVNR), Buchstabe und neun Ziffern. */
public static final FindingType KVNR = new FindingType("KVNR");
/** Betriebsstaettennummer (BSNR), neun Ziffern. */
public static final FindingType BSNR = new FindingType("BSNR");

// recognizer/kv/KvnrPruefziffer.java (T-3) – einzige Stelle des KVNR-Verfahrens
final class KvnrPruefziffer {
    /**
     * @param buchstabe A-Z oder a-z (wird fuer die Rechnung auf A-Z gefaltet)
     * @param ziffern   genau neun Werte 0..9 (acht Ziffern und die Pruefziffer)
     * @throws IllegalArgumentException ohne Werte der Eingabe in der Meldung
     */
    static boolean istGueltig(char buchstabe, int[] ziffern) { ... }
}

// recognizer/kv/BsnrRegionalcodes.java (T-12) – einzige Stelle der Liste
final class BsnrRegionalcodes {
    /** Liste aus OF-2 als boolean[100], Quelle und Abrufdatum im Javadoc. */
    static boolean istBekannt(int ersteZiffer, int zweiteZiffer) { ... }
}

// recognizer/kv/KvArt.java (T-4, T-13) – additiv; LANR und IK liefern in allen bestehenden Methoden dasselbe
enum KvArt {
    LANR(...unveraendert...), IK(...unveraendert...),
    KVNR(FindingType.KVNR, 0.95, "kvnr",
         Set.of("kvnr", "versichertennummer", "versichertennr", "krankenversichertennummer",
                "krankenversichertennr", "krankenversicherungsnummer", "krankenversicherungsnr",
                "egknummer", "egknr"),
         Set.of("versicherten", "krankenversicherten", "egk", "kv"),
         /* nurBuchstabenVorwoerter */ Set.of("kv"), /* buchstabenform */ true,
         "-pruefziffer-ungueltig", "-form-abweichend"),
    BSNR(FindingType.BSNR, 0.90, "bsnr",
         mitUmlaut("bsnr", "bsnrnr", "nbsnr", "betriebsstaettennummer", "betriebsstaettennr",
                   "nebenbetriebsstaettennummer", "nebenbetriebsstaettennr"),
         mitUmlaut("betriebsstaette", "betriebsstaetten", "nebenbetriebsstaette", "nebenbetriebsstaetten"),
         Set.of(), false, "-regionalcode-unbekannt", "-laenge-abweichend");

    // bestehende Methoden unveraendert in Signatur und Wert fuer LANR/IK; neu:
    boolean buchstabenform();
    Set<String> nurBuchstabenVorwoerter();
    /** buchstabe = 0 bei reiner Ziffernfolge. */
    boolean isValid(char buchstabe, int[] d);   // LANR/IK: wie isValid(d); KVNR: KvnrPruefziffer; BSNR: Regionalcode
    boolean isValid(int[] d);                   // bleibt; = isValid((char) 0, d)
    String sourcePruefzifferUngueltig();        // source + Suffix (LANR/IK unveraendert)
    String sourceLaengeAbweichend();            // source + Suffix (KVNR: "-form-abweichend")
    /** Ersetzt in jedem Wort "ae" durch U+00E4 und nimmt beide Formen auf; Quelltext bleibt ASCII. */
    private static Set<String> mitUmlaut(String... woerter);
}
```

Die bisherigen LANR-/IK-Konstruktoraufrufe bleiben wortgleich (zweiter Konstruktor für die neuen Einträge mit den
Zusatzfeldern; der alte setzt die Bestandswerte `Set.of()`, `false`, `-pruefziffer-ungueltig`, `-laenge-abweichend`).

```java
// recognizer/kv/KvnrRecognizer.java (T-4; T-10 eine Zeile)
public final class KvnrRecognizer implements Recognizer {
    public static final String SOURCE = "kvnr";
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "kvnr-pruefziffer-ungueltig";
    public static final String SOURCE_FORM_ABWEICHEND = "kvnr-form-abweichend";
    @Override public List<Finding> detect(String text) {
        List<Finding> mitKontext = KvNumberScanner.detect(KvArt.KVNR, text);
        return KvnrOhneKontext.ergaenze(text, mitKontext);   // T-10, Teil 1b: die einzige Einbindung
    }
}

// recognizer/kv/KvnrOhneKontext.java (T-10) – alles von 1b, nichts sonst
final class KvnrOhneKontext {
    static final String SOURCE = "kvnr-ohne-kontext";
    static final double CONFIDENCE = 0.60;
    /** D-4, pflegbar; ASCII, klein, Vergleich mit Faltung A-Z. */
    static final List<String> NEGATIVKONTEXT = List.of("artikelnummer", "art.-nr.", "rechnungsnummer",
            "rechnungs-nr.", "vorgangsnummer", "aktenzeichen", "az.", "kennzeichen", "bestellnummer",
            "auftragsnummer", "kundennummer", "seriennummer", "sendungsnummer", "ticket");
    /** Fuegt Funde ohne Kontextwort hinzu, die keinen Fund aus {@code mitKontext} ueberlappen; sortiert. */
    static List<Finding> ergaenze(String text, List<Finding> mitKontext) { ... }
}

// recognizer/kv/BsnrRecognizer.java (T-13)
public final class BsnrRecognizer implements Recognizer {
    public static final String SOURCE = "bsnr";
    public static final String SOURCE_REGIONALCODE_UNBEKANNT = "bsnr-regionalcode-unbekannt";
    public static final String SOURCE_LAENGE_ABWEICHEND = "bsnr-laenge-abweichend";
    @Override public List<Finding> detect(String text) { return KvNumberScanner.detect(KvArt.BSNR, text); }
}
```

Alle Quellen erfüllen `[a-z][a-z0-9-]{0,31}` (längste: `bsnr-regionalcode-unbekannt`, 27 Zeichen).

---

## 4. Erkennungsregeln

### 4.1 Gemeinsamer Scanner: Fenster (T-2)

Die längsten Wörter sind `nebenbetriebsstaettennummer` (27), `krankenversicherungsnummer` (26),
`krankenversichertennummer` (25). `FENSTER` wird 28, `MAX_KONTEXTWORT` 27 (Puffer von Sperr- und Weit-Verfolger
ebenso; die Bitmaske der Teilstückanfänge bleibt ein `int`). Für LANR und IK ändert sich nichts: Fenster­suffixe und
Folgen mit 24 bis 27 Buchstaben scheitern an `kontextLaenge` (kein LANR-/IK-Wort hat diese Länge), der längste Treffer
bleibt derselbe. Linearität: höchstens 28 Vergleiche je Teilstückende statt 24.

### 4.2 KVNR mit Kontextwort (Teil 1a, T-4)

**Kontextwörter (D-2):** siehe `KvArt.KVNR` in Kap. 3.2. `eGK-Nummer`, `eGK-Nr.`, `Versicherten-Nr.`,
`Krankenversicherten-Nr.`, `KV-Nr.`, `KV-Nummer`, `KV Nr.` laufen über Vorwort + Nachsatzwort; `Patienten-KVNR` über das
Teilstück `KVNR`; `K V N R`, `K. V. N. R.` über Sperr- und Weit-Verfolger (unverändert). `VKNR`, `KVNRX`,
`Versicherungsnummer`, `KV` allein, `Nr.` allein sind keine Kontextwörter.

**Buchstabenform (nur Art mit `buchstabenform()`):** In `durchlaufen()` wird **vor** `laufMitVerfolger` geprüft:

```java
if (art.buchstabenform() && istBuchstabenAnfang(i)) {   // Zeichen i: A-Z oder a-z
    crOffen = false;
    sperrSchritt(cp);                                     // beendet eine laufende Sperrfolge (K V N R Z…)
    weitSchritt(cp);                                      // beendet eine laufende Weit-Folge
    i = ziffernkette(i);                                  // Kette beginnt beim Buchstaben
    continue;
}
```

`istBuchstabenAnfang(i)`: Zeichen `i` ist ASCII-Buchstabe (A-3), und entweder `i+1` ist ASCII-Ziffer oder `i+1` ist
ein Gruppentrenner (`istTrenner`) und `i+2` eine ASCII-Ziffer. Da die Hauptschleife nur an Laufanfängen steht, ist der
Buchstabe ein Lauf aus genau diesem einen Zeichen. Zwei Buchstaben (`AB1…`) sind ein Lauf mit zwei Buchstaben und
kein Buchstabenanfang.

`ziffernkette(start)` erhält einen Vorspann: Beginnt die Kette mit einem Buchstaben, wird er (und ein Trenner)
übersprungen, dann läuft die **unveränderte** Präfixsuche über die Ziffernblöcke. Segment = Buchstabe bis Ende der
gewählten Blöcke. Gibt es kein Segment (weniger als 6 oder mehr als 12 Ziffern), zählen Buchstabe und erster Block
zusammen als **ein** Füllwort; weiter hinter dem ersten Block (wie bisher). Die Regel „nummerierte Liste“ gilt nicht
für die Buchstabenform.

**Angeklebte Ziffern:** Beginnt eine reine Ziffernkette unmittelbar hinter einem Buchstaben (`istBuchstabe` an
`start - 1`), gibt es für eine Art mit Buchstabenform **kein** Segment; der erste Block zählt als Füllwort.

**`KV-Nr.` (D-2):** Erkennt `lauf()` ein Vorwort aus `nurBuchstabenVorwoerter()` mit Nachsatzwort, beginnt der
Kontext mit `nurBuchstabenform = true` (neuer Parameter von `kontextBeginnen`, sonst `false`). Bei diesem Merker
gilt: Ein Segment ohne Buchstaben wird bei `aktiv` nicht ausgegeben, sondern zählt als Füllwort; beim nachgestellten
Kontext werden nur offene Segmente ausgegeben, deren erstes Zeichen ein Buchstabe ist. Die Buchstabenform ist am
Segment selbst ablesbar (`text.charAt(start)` ist Buchstabe); die offenen Segmente brauchen kein weiteres Feld.

**Konfidenz und Quelle (`ausgeben`, D-3):**

| Segment | Konfidenz | Quelle |
|---------|-----------|--------|
| Buchstabe + 9 Ziffern, Prüfziffer gültig, direkt | 0,95 | `kvnr` |
| ebenso, weiterer Kontext / nachgestellt | 0,80 | `kvnr` |
| ebenso, nachgestellt mit `,`/`;` | 0,60 | `kvnr` |
| gesperrtes Kontextwort (auch Weit-Form) | höchstens 0,80 | nach Nummer |
| Buchstabe + 9 Ziffern, Prüfziffer ungültig | 0,50 | `kvnr-pruefziffer-ungueltig` |
| Buchstabe + 6–12 Ziffern außer 9; reine Ziffernfolge 6–12 (auch 9) | 0,40 | `kvnr-form-abweichend` |

Umsetzung: `boolean neunerForm = anzahl == NEUN && (!art.buchstabenform() || buchstabe != 0)`; Prüfung über
`art.isValid(buchstabe, ziffernwerte(start, end))`. Weder Fund noch Meldung enthält die berechnete Prüfziffer.

**Fundbereich:** Buchstabe, Trenner, Ziffern; nie Kontextwort, Doppelpunkt, Komma, abschließender Trenner.

### 4.3 KVNR ohne Kontextwort (Teil 1b, T-10)

`KvnrOhneKontext.ergaenze(text, mitKontext)` läuft einmal von links nach rechts. Ein Kandidat an Position `i`:

1. `text[i]` ist `A`–`Z` (Großbuchstabe, ASCII), `text[i+1..i+9]` sind neun ASCII-Ziffern (genau zehn Zeichen, keine
   Trenner);
2. **Wortgrenze vorn (AK-13):** `i == 0` oder `text[i-1]` ist weder Buchstabe noch Kombinationszeichen (wie
   `istBuchstabe` des Scanners), noch Ziffer, noch `_`, `-`, `/`, `\`, `@`;
3. **Wortgrenze hinten:** `i+10 == n` oder `text[i+10]` ist weder Buchstabe/Kombinationszeichen, Ziffer, `_`, `-`,
   `/`, `\`, `@`, und nicht `.` oder `,` gefolgt von einer ASCII-Ziffer;
4. `KvnrPruefziffer.istGueltig(text[i], neun Ziffern)`;
5. **Negativkontext (D-4):** Rückwärts ab `i-1` über Leerraum (`Character.isWhitespace`/`isSpaceChar`), `:` und `=`;
   endet der Text dort (Faltung A–Z) auf ein Wort aus `NEGATIVKONTEXT` und steht davor kein Buchstabe und keine Ziffer,
   entsteht kein Kandidat. Kein Übergehen von Füllwörtern.

Kandidat: `Finding(KVNR, Zehnerfolge, i, i+10, 0.60, "kvnr-ohne-kontext")`. Ein Kandidat, der einen Fund aus
`mitKontext` überlappt, entfällt (AK-11: nur der Fund 0,95). Ergebnis: Zusammenführen zweier sortierter Listen,
linear.

**Linearität:** Je Startposition höchstens elf Zeichen vorwärts. Zwei Kandidaten liegen mindestens elf Zeichen
auseinander (Ziffern und Grenze hinten), der Rückwärtslauf über Leerraum/`:`/`=` endet spätestens an der letzten
Ziffer des vorigen Kandidaten; die Rückwärtsläufe sind also disjunkt, der Wortvergleich liest höchstens 15 Zeichen.
Gesamt O(n).

**Interne Markierung:** nicht nötig; die Quelle `kvnr-ohne-kontext` genügt (OF-4).

### 4.4 BSNR (Teil 2, T-13)

Konfiguration `KvArt.BSNR` (Kap. 3.2). `isValid(…)` = `BsnrRegionalcodes.istBekannt(d[0], d[1])`; „ungültig“ heißt
hier „Regionalcode unbekannt“ (0,50, `bsnr-regionalcode-unbekannt`). Konfidenz direkt 0,90, sonst wie LANR/IK nach D-8
(0,80; Komma 0,60; Sperr höchstens 0,80; Länge 0,40 `bsnr-laenge-abweichend`). Keine Buchstabenform, kein
`nurBuchstabenform`.

**`ä`/`ae` (OF-4):** Die Wortlisten stehen in ASCII mit `ae`; `KvArt.mitUmlaut` nimmt zu jedem Wort die Form mit
U+00E4 auf (einzige Stelle). `vergleichszeichen` faltet zusätzlich U+00C4 → U+00E4 (`BETRIEBSSTÄTTE`). Für LANR/IK
ohne Wirkung: deren Wörter sind ASCII, ein Lauf mit `ä`/`Ä` gleicht vor und nach der Faltung keinem davon. Zerlegte
Umlaute (`a` + U+0308) bleiben unerkannt (Grenze).

### 4.5 Linearität (Nachweis im Javadoc, AK-22/AK-33)

Buchstabenanfang: O(1) Vorgriff (zwei Zeichen); Kette wie bisher (jeder Block höchstens 13-mal berührt); Merker
`nurBuchstabenform` O(1). 1b: Kap. 4.3. Grenzlast-Fälle in T-8, T-11, T-15 mit exakter Fundzahl.

---

## 5. Bestandsschutz: dritte eingefrorene Kopie und Kollisionsfälle

### 5.1 Kopie (T-1)

| Stand | Klasse (Testbaum, Paket `recognizer.kv`) | Hüllen |
|-------|------------------------------------------|--------|
| vor `kv-kontext-luecken` (bestehend) | `KvNumberScannerVorgaenger` | `KvVorgaenger` |
| Ende `kv-kontext-luecken` (bestehend) | `KvNumberScannerStandLuecken` | `KvStandLuecken` |
| **Ende `kv-trenner-und-umbruch` (neu)** | `KvNumberScannerStandTrenner` | `KvStandTrenner` (`lanr()`, `ik()`) |

Wortgleiche Kopie des heutigen `KvNumberScanner` (nur Klassenname und Kopfvermerk „eingefroren am Stand Ende
kv-trenner-und-umbruch, nicht ändern“), danach nie geändert. Die Kopien nutzen `KvArt` mit; deshalb gilt für `KvArt`
die Regel 11 (Kap. 9).

### 5.2 Prüfungen (neue Klasse `test/…/KvStandTrennerDifferenzTest.java`)

Textmengen: Fuzz 1, 1b, 1c, 1d, 1e, `KvDifferenzTest.korpus()`, Texte von `RoundtripKorpusTest`,
`KvKontextLueckenEigenschaftTest.differenzTexte()`; später zusätzlich Fuzz 1f, 1g, 1h und die Texte der neuen
Tests.

- **E (Erkennerebene, ohne Ausnahme):** Für jeden Text gilt `new LanrRecognizer().detect(t)` gleich
  `KvStandTrenner.lanr().detect(t)` in **allen** Feldern, ebenso IK. Gilt in jedem Task.
- **S-1 (Abdeckung, ohne Ausnahme):** Jede Position, die `ALT` = `TextScanner[Email, Ipv4, Url, Path,
  KvStandTrenner.lanr(), KvStandTrenner.ik()]` abdeckt, deckt `TextScanner.standard()` ab.
- **S-2 (Fund für Fund):** Für jeden Fund `f` aus `ALT` gibt es in `standard()` einen Fund mit gleichem Start, Ende,
  Typ und Konfidenz ≥ `f` – oder der Fall ist genau einer der Kollisionsfälle K-1 bis K-4 und dessen Bedingung gilt.
- **S-3 (keine zusätzlichen Funde):** Findet die unabhängige Referenz im Text weder ein KVNR-Kontextwort noch ein
  BSNR-Kontextwort noch (ab T-10) eine KVNR-Form, sind beide Fundlisten **gleich** (alle Felder).

**Kollisionsfälle (abschließend):**

| Fall | Beschreibung | Bedingung im Text | Wirkung |
|------|--------------|-------------------|---------|
| K-1 | KVNR umschließt: neuer KVNR-Fund `g` mit `g.end == f.end`, `g.start` 1 oder 2 Zeichen vor `f.start` (Buchstabe, ggf. ein Trenner), `f` vom Typ LANR oder IK | KVNR-Kontextwort oder KVNR-Form | Typ wird KVNR; Konfidenz kann sinken (z. B. LANR 0,80 → KVNR 0,60 bei 1b) |
| K-2 | Gleichstand, gleicher Bereich: `g.conf == f.conf`, `g.type` ∈ {KVNR, BSNR} und namentlich vor `f.type` | Kontextwort der Art `g.type` | Typ wechselt, Konfidenz gleich |
| K-3 | Stärkerer neuer Typ, gleicher Bereich: `g.conf > f.conf`, `g.type` ∈ {KVNR, BSNR} | Kontextwort der Art `g.type` | Typ wechselt, Konfidenz steigt (D-6) |
| K-4 | Teilüberlappung mit einem neuen KVNR-/BSNR-Kandidaten: Fund `zusammengelegt`, der `f` umfasst | KVNR-/BSNR-Kontextwort oder KVNR-Form | Typ des stärksten, Konfidenz des schwächsten Glieds |

Jeder Fall wird je Textmenge gezählt. Auf den Bestandsmengen (Fuzz 1 bis 1e, Korpus, Generatoren) ist die Zahl jedes
Falls **0** (sie enthalten weder KVNR-/BSNR-Kontextwort noch KVNR-Form – geprüft); auf den neuen Fuzz-Teilen ist sie
ein exakter Messwert (fester Seed), K-1 bis K-3 kommen dort mit Mindestzahl vor. K-4 wird nur gezählt, ein Auftreten
auf Bestandsmengen ist ein Befund.

**Unabhängige Referenzen** (neue Klasse `test/…/recognizer/kv/KvnrBsnrHilfen.java`, ohne Produktaufruf, Laufzeit
egal):
- `kvnrKontextReferenz(text)` / `bsnrKontextReferenz(text)`: Buchstabenstrom des Textes (alle Nicht-Buchstaben und
  unsichtbaren Zeichen entfernt, A–Z und Ä gefaltet) enthält ein Kontextwort der Art oder ein Vorwort mit `nr`/`nummer`
  (eigene Wortlisten, `ae`- und `ä`-Formen). Bewusst eine Überschätzung (erfasst Sperr- und Weit-Formen); die Zahl
  streng geprüfter Texte je Menge hat eine Mindestzahl.
- `kvnrFormReferenz(text)`: irgendwo ein Großbuchstabe A–Z, unmittelbar gefolgt von genau neun ASCII-Ziffern (ohne
  Prüfziffer, ohne Grenzen) – Überschätzung der 1b-Kandidaten.
- `kvnrOhneKontextReferenz(text)` (T-9): eigenständige Umsetzung von Kap. 4.3 (zeichenkettenbasiert).
- `kvnrGueltigReferenz(String)` (T-3): Prüfziffer nach A-1, unabhängig von `KvnrPruefziffer` geschrieben.

---

## 6. Wiederverwendung

`KvNumberScanner` (Lauf, Kettenregel, Kontextregeln, Sperr- und Weit-Verfolger, `crOffen`, `ausgeben` mit Deckel,
`kontextBeginnen`, `offenNachgestelltAusgeben`), `KvArt`, `Finding`, `FindingType`, `ConfidenceLevel`, `Recognizer`,
`TextScanner`, `ConflictResolver`, `Pseudonymizer`, `Reidentifier`, `PlaceholderSyntax` (alle Platzhalterregeln
gelten für `KVNR`/`BSNR` ohne Änderung). Testseitig: `SynthetischeKvNummern` (erweitert), `TestdatenSynthetischTest`
(erweitert), `KvTestHilfen` (nur lesend: Zeichenkonstanten, `ceilMillion`, `bauen`, Wächter
`assertKeinGueltigesNeunerfensterOhne00`), `RoundtripFuzzTest` (Lauf, Klassen, Bausteine `kvBausteinC` und der Teile
1d/1e als Mischbausteine mit eigenem `Random`), `RoundtripKorpusTest`, `GrenzlastTest`, `KlartextFreiheitTest`,
`TextScannerKvTest`, Siegel-Bildung aus `KvStandLueckenDifferenzTest` (als Vorlage, nicht geändert).

## 7. Neue Abhängigkeiten

Keine.

---

## 8. Sicherheits- und Datenschutzaspekte

- **Patientendaten:** Die KVNR ist eine Patientenkennung (höchster Schutzbedarf). 1a schützt sie bei Kontextwort,
  1b auch ohne – als „fraglich“, also nur bei Übergabe durch den Menschen ersetzt (AK-20).
- **Offline, kein Klartext:** reine Zeichenverarbeitung; keine neue Meldung mit Text; Prüfziffern- und
  Regionalcodeklasse werfen `IllegalArgumentException` ohne Eingabewerte; `toString()` der Funde unverändert. Weder
  Fund, Quelle noch Meldung enthalten berechnete/erwartete Prüfziffer oder die Regionalcode-Liste (AK-7, AK-27, AK-35).
  Testmeldungen nennen nur Seed, Iteration, Zähler, Fallname.
- **Kein Schutzverlust:** S-1 ohne Ausnahme; LANR/IK auf Erkennerebene identisch. K-1 kann die **Stufe** eines
  bisherigen LANR-/IK-Fundes senken (Risiko R-2); die Position bleibt geschützt und wird um den Buchstaben erweitert.
- **Fehlalarm 1b:** eng gefasst (E-3), Prüfstand vor dem Produktcode, Abbruchkriterium D-5.
- **Laufzeit/DoS:** linear, harte 2-s-Grenzen je Schritt mit exakter Fundzahl (AK-22, AK-33).
- **Testdaten:** KVNR nur mit Ziffern `0000…` an Stelle 2 bis 5, BSNR nur mit `00000` an Stelle 3 bis 7 oder
  Regionalcode `00`; zentrale Klasse; statischer und Laufzeit-Wächter (T-3, T-12).

---

## 9. Verbindliche Regeln für engineer und Prüfer

Die Regeln aus Kap. 8 des Plans `kv-trenner-und-umbruch` gelten weiter, mit folgenden Anpassungen und Ergänzungen:

1. Kein regulärer Ausdruck im Produkt; benannte Tests je AK-Beispiel; Eigenschaftstests mit festem Seed und
   unabhängigem Orakel; keine Ausgabe (`System.out`/`System.err`); kein Netzwerk-, DNS- oder Dateizugriff; keine
   neue Abhängigkeit.
2. Zeilen höchstens 120 Zeichen; Klarstellung 8.2 des Vorgängers gilt: Die schließende `}` eines Blocks steht nie
   hinter einer Anweisung auf derselben Zeile; `…);` am Ende der letzten Argumentzeile ist erlaubt; Maßstab ist der
   umgebende Bestandscode.
3. Keine `\uXXXX`-Escapes in neuen oder geänderten Zeilen; Sonderzeichen als `(char) 0x…`-Konstanten
   (`KvTestHilfen` bzw. `KvnrBsnrHilfen`: neu `AE_KLEIN = (char) 0xE4`, `AE_GROSS = (char) 0xC4`). Im Produkt
   ebenso (`KvArt.mitUmlaut`, `vergleichszeichen`).
4. Keine Ziffernkette ab neun Stellen und keine Folge Buchstabe + neun Ziffern (auch mit Trennern) außerhalb von
   `SynthetischeKvNummern.java` – auch nicht in Kommentaren, Javadoc, Testnamen, Seeds. Hash-/Siegel-Konstanten
   hexadezimal (höchstens acht Hexziffern), Seeds höchstens acht Dezimalstellen. Fremdnummern für Prüfstände werden
   zur Laufzeit von einer Methode in `SynthetischeKvNummern` gebildet.
5. Meldungen ohne Klartext und ohne Ziffern des Textes.
6. **Unverändert in allen Tasks:** `KvNumberScannerVorgaenger`, `KvVorgaenger`, `KvNumberScannerStandLuecken`,
   `KvStandLuecken`, `KvCheckDigits`, `LanrRecognizer`, `IkRecognizer`, `ConflictResolver`, alle bestehenden
   `SIEGEL_*`- und `HASH_TEIL_*`-Konstanten, Generatoren und Seeds der Fuzz-Teile 1 bis 1e, die eingefrorenen
   Prüfstände der Vorgänger (`KvTrennerFehlalarmTest`, `KvTrennerUzFehlalarmTest`, die in 9a des Vorgängers genannten
   Mitglieder von `KvTestHilfen`, die `t5_*`-Tests in `KvStandLueckenDifferenzTest`); nach T-1
   `KvNumberScannerStandTrenner` und `KvStandTrenner`; nach T-9 der Prüfstand 1b (Regel 13); jedes neue
   `SIEGEL_*`/`HASH_TEIL_*` nach dem Task, der es misst.
7. Keine Mindestzahl wird gesenkt, kein Seed geändert, um einen Test grün zu bekommen.
8. **Umstellung bestehender Tests nur wie im Task genannt.** Meldet ein anderer bestehender Test eine Änderung, hält
   der engineer an und meldet Test, Fall (ohne Ziffern) und Grund; keine stille Anpassung. Reine Ergänzungen in
   bestehenden Testdateien (neue Methoden, neue Fälle ohne geänderte Erwartung) sind erlaubt, wenn der Task die Datei
   nennt. Jede Umstellung wird im Bericht einzeln je Assertion genannt.
9. In neuen Tests die Quelle aus der Nummer ableiten (`KvnrBsnrHilfen.quelleNachNummerKvnr/Bsnr`), nicht aus der
   Konfidenz.
10. **Bestandsschutz:** Abweichungen gegenüber `KvStandTrenner` sind nur als K-1 bis K-4 unter ihrer Bedingung
    zulässig (Kap. 5.2). Jede andere Abweichung ist ein Befund: anhalten, melden.
11. **`KvArt` (Ausnahme von Regel 4 des Plans `kv-kontext-luecken`):** Änderungen nur additiv. Die LANR-/IK-Zeilen
    und alle Werte, die die eingefrorenen Kopien abfragen (`type`, `confidenceDirekt`, `source`, `kontextwoerter`,
    `vorwoerter`, `sourcePruefzifferUngueltig`, `sourceLaengeAbweichend`, `isValid(int[])`), bleiben für LANR und IK
    gleich; abgesichert durch `KvArtBestandTest` (T-1) und alle Siegel.
12. **Negativbeispiele in Tests von Teil 1a und Teil 2** verwenden `<ungültige KVNR>` oder Formen, die 1b nicht
    trifft (klein, mit Trenner, zwei Buchstaben). Eine gültige, geschlossene, großgeschriebene KVNR steht in diesen
    Tests nur dort, wo 1a einen Fund liefert. Kein Test außerhalb des 1b-Inventars (Regel 14) prüft auf die Quelle
    `kvnr-ohne-kontext` oder hängt von ihr ab.
13. **Einfrieren des Prüfstands 1b (wie 9a des Vorgängers):** Ab Abnahme von T-9 sind `KvnrOhneKontextFehlalarmTest`
    vollständig und in `KvnrBsnrHilfen` alle Mitglieder, von denen er abhängt (`kvnrOhneKontextReferenz`,
    `kvnrGueltigReferenz`, Fremdtext-Bausteine, Zähler, künstliche Erkenner), eingefroren; in
    `SynthetischeKvNummern` die Fremdnummern-Methode. Erlaubt sind reine Ergänzungen. qa prüft in T-10, T-11, T-R, dass
    im Diff keine Zeile der eingefrorenen Teile entfernt oder geändert ist.
14. **1b-Inventar (Rücknahme ohne Rest):** Alles, was nur 1b dient, steht in: `main/…/KvnrOhneKontext.java`, der einen
    Zeile und dem Javadoc-Absatz in `KvnrRecognizer`, den Testklassen `KvnrOhneKontextTest`,
    `KvnrOhneKontextEigenschaftTest`, Fuzz-Teil 1g (Generator, Seeds, Hash, Testmethoden mit `1g` im Namen), Methoden
    mit `OhneKontext` im Namen in `RoundtripKorpusTest`, `GrenzlastTest`, `KlartextFreiheitTest`,
    `KvStandTrennerDifferenzTest`, `KvnrGrenzenTest` und dem Javadoc-Abschnitt „ohne Kontextwort“ in
    `KvNumberScanner`/`KvnrRecognizer`. Jeder 1b-Task führt die Liste im Bericht nach. Der Prüfstand T-9 gehört
    **nicht** dazu (er bleibt nach einer Rücknahme grün und bewacht sie).
15. **Abbruchkriterium Teil 1b (D-5) – gilt in T-10 und T-11:** Meldet eine Prüfung aus T-9 (AK-16 (a), (b), (c)) oder
    S-2/S-3 auf den Bestandsmengen einen zusätzlichen KVNR-Fund, dann
    - ändert der engineer nichts an Erwartungen, Textlisten, Generatoren, Seeds, Mindestzahlen, Referenzen oder
      Schwellen;
    - darf er das Produkt nur korrigieren, wenn die Ursache nachweislich eine Abweichung des Codes von Kap. 4.3 ist
      (und nennt sie);
    - sonst hält er an, nimmt alle 1b-Änderungen am Produkt zurück (Stand nach T-8; Build grün, T-9 bleibt grün) und
      meldet Fallname bzw. Seed/Iteration und Zahl, ohne Text und Ziffern. Die Hauptsitzung legt den Befund dem
      Nutzer vor; bei Streichung folgt T-R.

---

## 10. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün. „Bestandstests, die sich ändern“ nennt
abschließend die bestehenden Tests mit geänderter Erwartung; alle anderen bestehenden Tests bleiben unverändert.

### Teil 0 – Bestandsschutz

#### T-1: Heutigen Stand einfrieren, strenge Differenz, Kollisionsprüfregel (nur Tests)

- **Ziel:** Kap. 5. Keine Produktänderung.
- **Dateien:** neu `test/…/recognizer/kv/KvNumberScannerStandTrenner.java`, `test/…/recognizer/kv/KvStandTrenner.java`,
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (Referenzen `kvnrKontextReferenz`, `bsnrKontextReferenz`,
  `kvnrFormReferenz`; `ALT_TRENNER`; Prüfregel `bestandsabweichungen(text, alt, neu)` mit Kategorien K-1 bis K-4 und
  „Verstoß“; Konstanten `AE_KLEIN`, `AE_GROSS`), `test/…/recognizer/kv/KvArtBestandTest.java`,
  `test/…/KvStandTrennerDifferenzTest.java`.
- **AK:** AK-19 und AK-32 (Werkzeug), AK-35 (Determinismus-Werkzeug).
- **Tests:** Siegel der Kopie (`SIEGEL_TRENNER_TEIL_1`, `_1B`, `_1C`, `_1D`, `_1E`, Korpus, Generatoren; hexadezimal,
  gemessen); E über alle Mengen; S-1 bis S-3 über alle Mengen (heute identisch, da `standard()` unverändert);
  Mindestzahlen alter LANR-/IK-Funde je Menge > 0 und streng geprüfter Texte (S-3) je Menge; `KvArtBestandTest`:
  LANR/IK `type`, `confidenceDirekt`, `source`, beide Wortmengen, beide Quellen-Suffixe, `isValid(int[])` an den
  Vektoren aus `SynthetischeKvNummern`. Selbsttests der Prüfregel: fehlender Fund, gesenkte Konfidenz, verschobener
  Bereich, Typwechsel zu LANR/IK oder zu einem Nicht-KV-Typ, Abdeckungsverlust → Verstoß; K-1 mit KVNR-Form im Text
  → zulässig, ohne → Verstoß; K-2/K-3 mit passendem Kontextwort → zulässig, mit Kontextwort der falschen Art →
  Verstoß; K-4 mit Kontextwort → zulässig. Referenz-Selbsttests: `KVNR`, `K V N R`, `K. V. N. R.`, `KV-Nr.`,
  `Versicherten-Nr.`, `eGK-Nummer`, `Patienten-KVNR` → ja; `VKNR`, `Versicherungsnummer`, `Die KV zahlt` → nein;
  `BSNR`, `B S N R`, `Betriebsstätten-Nr.` (mit `ä` und mit `ae`), `NBSNR` → ja; `Betrieb` → nein; KVNR-Form mit
  verketteten Konstanten → ja, mit Trenner/klein → nein.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Diff der Kopie gegen `KvNumberScanner` zeigt nur Klassenname und Kopfvermerk; alle Siegel gemessen.

### Teil 1a – KVNR mit Kontextwort

#### T-2: Fenster auf 28 Zeichen (Produkt, ohne Verhaltensänderung)

- **Ziel:** Kap. 4.1.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (`FENSTER`, `MAX_KONTEXTWORT`, Javadoc „längstes
  Kontextwort“ und Linearität); neu `test/…/recognizer/kv/KvFensterTest.java`.
- **AK:** AK-35 (Bestand), Vorbereitung AK-2, AK-25.
- **Tests:** Läufe mit 22 bis 30 Buchstaben, die auf `lanr`/`ik`/`arztnummer` enden, mit und ohne unsichtbares Zeichen
  vor dem Wort; Sperr- und Weit-Folgen mit 23 bis 28 Einzelbuchstaben; je Ergebnis gleich `KvStandTrenner`. Laufzeit
  (Erkenner, exakte Zahl): ein Lauf aus 1 000 000 Buchstaben → 0; `a` + ZWSP wiederholt → 0.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** E, S-1 bis S-3 und alle Siegel unverändert grün.

#### T-3: KVNR-Prüfziffer und Testdaten (Voraussetzung: OF-1-Nachtrag liegt vor)

- **Ziel:** einzige Stelle des Verfahrens; zentrale Testnummern; Wächter.
- **Dateien:** neu `main/…/recognizer/kv/KvnrPruefziffer.java` (Quellenvermerk nach AK-24 mit den Angaben aus OF-1);
  neu `test/…/recognizer/kv/KvnrPruefzifferTest.java` (gleicher Vermerk); `test/…/SynthetischeKvNummern.java`
  (Abschnitt KVNR: `KVNR_GUELTIG`, `KVNR_GUELTIG_ZWEI`, mindestens drei ungültige Vektoren nach AK-6, Vektoren für
  Gewichtsbeginn, zweistellige Buchstabenwertung, Produkt über 9, Buchstabe `Z`; `KVNR_NULL` (Buchstabe + Nullfolge),
  Formen mit acht und zehn Ziffern, `KVNR_MARKER`; Rechenweg je Konstante als Kommentar; Restrisiko OF-3);
  `test/…/SynthetischeKvNummernTest.java` (Ergänzung); `test/…/TestdatenSynthetischTest.java`;
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`kvnrGueltigReferenz`, Laufzeit-Wächter `assertKvnrSynthetisch`: jede
  Folge Buchstabe + optional ein Trenner + neun Ziffern aus ganzen Blöcken, die nach Referenz gültig ist, hat die
  Ziffern `0000` an Stelle 2 bis 5).
- **AK:** AK-6, AK-23, AK-24.
- **Tests:** jeder Vektor gegen `KvnrPruefziffer` und gegen `kvnrGueltigReferenz` (beide gleich); Groß/klein gleich;
  Fehlerfälle (`null`, falsche Länge, Wert außerhalb, Nicht-Buchstabe) mit Meldung ohne Werte; Wächter-Selbsttests.
- **Bestandstests, die sich ändern:** `TestdatenSynthetischTest`: neue Verstoßart `KVNR_FORM` (Buchstabe, optional ein
  Trenner, dann eine Kette mit neun Ziffern im Sinne der Regeln (1) bis (3)) außerhalb der zentralen Datei; in der
  zentralen Datei müssen KVNR-Formen die Ziffern `0000` an Stelle 2 bis 5 haben; Selbsttests dazu nur verkettet.
  Keine bestehende Erwartung und keine Mindestzahl ändert sich.
- **Abnahme:** Verfahren genau in einer Produktklasse; Hinweis H-1 umgesetzt (Kap. 13).

#### T-4: KVNR-Erkenner, Kern (Produkt, noch nicht in `standard()`)

- **Ziel:** Kap. 3.2, 4.2.
- **Dateien:** `main/…/model/FindingType.java` (`KVNR`); `main/…/recognizer/kv/KvArt.java`;
  `main/…/recognizer/kv/KvNumberScanner.java` (Buchstabenanfang, Vorspann in `ziffernkette`, angeklebte Ziffern,
  `nurBuchstabenform`, `ausgeben`; Javadoc „KVNR“); neu `main/…/recognizer/kv/KvnrRecognizer.java` (ohne 1b-Zeile);
  neu `test/…/recognizer/kv/KvnrRecognizerTest.java`; `test/…/model/FindingTypeTest.java` (Ergänzung `KVNR`,
  `[[KVNR_1]]`); `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`quelleNachNummerKvnr`, `scanKvnr` mit Wächtern und
  Invarianten).
- **AK:** AK-1, AK-2, AK-3, AK-4 (Erkennerebene), AK-7, AK-8, AK-10, AK-35 (Teil).
- **Tests:** jedes Beispiel aus AK-1 bis AK-4, AK-7, AK-8, AK-10 als benannter Test (Typ, Wert, Bereich, Konfidenz,
  Stufe, Quelle); zusätzlich `KV-Nr.` + reine Ziffern nachgestellt (`<LANR> (KV-Nr.)`) → kein Fund;
  `KVNR` + `AB` + Ziffern → kein Fund; `KVNR AB <Ziffern>` → 0,40 (Hinweis H-5); `LANR <gültige KVNR>` im
  LANR-Erkenner unverändert (Ziffern als LANR). `null` → `NullPointerException` mit Meldung `text`.
- **Bestandstests, die sich ändern:** keine (`FindingTypeTest` nur ergänzt).
- **Abnahme:** E (LANR/IK identisch) grün; `KvArtBestandTest` grün; Siegel unverändert.

#### T-5: Schreibweisen und Kontextregeln KVNR (Tests; Produktkorrektur nur bei Abweichung von Kap. 4.2)

- **Dateien:** neu `test/…/recognizer/kv/KvnrKontextTest.java`, neu `test/…/recognizer/kv/KvnrEigenschaftTest.java`;
  ggf. `main/…/recognizer/kv/KvNumberScanner.java` (nur Korrektur, im Bericht begründet).
- **AK:** AK-5, AK-9, AK-35.
- **Tests:** AK-5 (Kleinbuchstabe; Buchstabe, Trenner, Ziffern; Gruppen 3+3+3, 4+5, 1+8 mit je allen fünf Trennern;
  zwei Trenner, Trenner am Ende; zwei Buchstaben); AK-9 je Regel mit Gegenprobe (`<ungültige KVNR>` in allen
  Negativbeispielen, Regel 12), Sperrschrift `K V N R`, `K. V. N. R.`, Weit-Form `K.  V.  N.  R.`, Umbruch `CR UZ LF`;
  Eigenschaftstest (≥ 1 000 Texte, fester Seed): Kontextwort aus D-2 (zufällige Schreibung) + Trenner + Nummer
  (gültig/ungültig/Form, gruppiert, klein) + Füllwörter/Umbrüche → Erwartung aus der Konstruktion; Determinismus,
  Sortierung, Überlappungsfreiheit, Teilstring-Invariante.
- **Bestandstests, die sich ändern:** keine.

#### T-6: Einbindung KVNR in `TextScanner.standard()` (Produkt)

- **Dateien:** `main/…/recognizer/TextScanner.java`; `test/…/recognizer/TextScannerTest.java`;
  `test/…/recognizer/TextScannerKvTest.java` (Ergänzung); `test/…/KvStandTrennerDifferenzTest.java` (Ergänzung:
  Scannerebene mit KVNR, K-Zähler); `test/…/KlartextFreiheitTest.java` (Ergänzung).
- **AK:** AK-4 (Scannerebene), AK-18, AK-19, AK-20, AK-21 (Einbindung), AK-35.
- **Tests:** AK-21 Text mit E-Mail, IPv4, URL, Pfad, IK, LANR, KVNR → sieben Funde; AK-18 beide Beispiele (je ein
  Fund, Typ, Konfidenz, Wiederholung gleich); Tabelle Kap. 2.2 für die KVNR-Zeilen; K-1 mit `LANR <gültige KVNR>` und
  KVNR-Kontextwort (`KVNR LANR <gültige KVNR>`) als belegter Kollisionsfall; AK-20 (`KVNR <ungültige KVNR>`: Fund,
  leere Übergabe → Text unverändert); Klartextfreiheit mit `KVNR_MARKER` (`toString()`, Meldungen); S-1 bis S-3 über
  alle Bestandsmengen mit K-Zählern = 0.
- **Bestandstests, die sich ändern:** `TextScannerTest.ak22_abgleichErkennerlisteStandardEnthaeltAlleErkennerIn
  FesterReihenfolge`: erwartete Liste um `KvnrRecognizer.class` am Ende erweitert (einzige Umstellung).
- **Abnahme:** alle Vorgänger-Differenztests (`KvVorgaengerDifferenzTest`, `KvDifferenzTest`,
  `KvStandLueckenDifferenzTest`) unverändert grün.

#### T-7: Roundtrip, Fuzz-Teil 1f, Korpus (nur Tests)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1f**: `SEED_TEXTE_KV_F`, `SEED_AUSWAHL_KV_F`, eigener
  Generator, `HASH_TEIL_1F` hexadezimal; Blöcke: jedes Kontextwort aus D-2, Sperr-/Weit-Formen, alle Schreibweisen,
  Aufzählung, nachgestellt mit/ohne Komma, Umbruch, gemischt mit LANR-/IK-Blöcken und Bausteinen aus 1c/1d/1e mit
  eigenem `Random`; Gegenproben ohne Block nur nach Regel 12; Grenzfälle bei der Konstruktion markiert);
  `test/…/RoundtripKorpusTest.java` (KVNR-Fälle AK-1 bis AK-10 mit erwartetem pseudonymisiertem Text);
  `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1f; S-1 bis S-3 über 1f; K-1 bis K-4 als exakte Messwerte).
- **AK:** AK-17, AK-19, AK-21 (Fuzz).
- **Tests:** Roundtrip zeichengleich; gleiche KVNR zeichengleich → gleicher Platzhalter, mit/ohne Trenner →
  verschieden; im Ausgabetext keine Folge Buchstabe + ≥ 6 Ziffern aus einem Fund; Schutzgarantie über
  `STANDARD_ERKENNER`; `block-ohne-scan-abdeckung` strikt 0; Klassen-Mindestzahlen je Kontextwortgruppe und Stufe;
  `kvnrOhneKontextReferenz`-Vorgriff: in 1f liegt außerhalb der Blöcke keine KVNR-Form (Zähler 0; sichert, dass 1b
  später nichts an 1f ändert).
- **Bestandstests, die sich ändern:** keine; `HASH_TEIL_1` bis `HASH_TEIL_1E` unverändert.

#### T-8: Grenzlast, bekannte Grenzen, Dokumentation – Abschluss Teil 1a

- **Dateien:** `test/…/GrenzlastTest.java` (neue Methode `ak22_kvnr_eineMillionZeichen_…`); neu
  `test/…/recognizer/kv/KvnrGrenzenTest.java`; Javadoc `KvNumberScanner`, `KvnrRecognizer`, `KvArt`.
- **AK:** AK-22 (1a), AK-23 (Abschluss), AK-36 (1a).
- **Tests:** je 1 000 000 Zeichen, Scan + Pseudonymisierung + Rückübersetzung je < 2 s nach Aufwärmlauf, exakte
  Fundzahl mit Formel: Buchstabe + nur Ziffern → 0; `A1` wiederholt → 0; `KVNR ` + `Z` wiederholt → 0;
  `KVNR ` + (`KVNR_NULL` + ` `) wiederholt → ⌈10⁶ / 11⌉ (über 70 000; Aufzählung) und (`KVNR ` + `KVNR_NULL` + ` `)
  wiederholt → ⌈10⁶ / 16⌉; `K V N R ` wiederholt → 0; `KVNR` + nur Leerzeichen → 0. Je bekannte Grenze aus Kap. 11
  (Teil 1a) ein Test `bekannteGrenze…`.
- **Abnahme:** Teil 1a vollständig (AK-1 bis AK-10, AK-17 bis AK-24 ohne 1b-Anteile, AK-35, AK-36). Bericht an die
  Hauptsitzung: „Teil 1a abgeschlossen“ (eigener gesicherter Stand).

### Teil 1b – KVNR ohne Kontextwort (unter D-5)

#### T-9: Fehlalarm-Prüfstand 1b (nur Tests, vor jeder Produktänderung für 1b; danach eingefroren)

- **Dateien:** neu `test/…/recognizer/kv/KvnrOhneKontextFehlalarmTest.java`; `test/…/recognizer/kv/KvnrBsnrHilfen.java`
  (`kvnrOhneKontextReferenz`, Fremdtext-Bausteine, künstliche Erkenner); `test/…/SynthetischeKvNummern.java`
  (Methode `fremdKvnr(Random, boolean gueltig)`: Buchstabe A–Z, Ziffern `0000` + vier zufällige + Prüfziffer passend
  bzw. sicher falsch; Methode `zufallsKvnr(Random)`: letzte Ziffer gleichverteilt).
- **AK:** AK-16 (Prüfstand).
- **Tests:**
  - `ak16a_bestandsmengen_keinKvnrFund`: Fuzz 1 bis 1e, Korpus, Generatoren, `TestTexte` → Zahl der KVNR-Funde in
    `standard()` = 0 (Liste der Mengen im Bericht).
  - `ak16b_festerSatzVonFremdmustern_keinKvnrFund`: mindestens 40 synthetische Sätze, Textliste als Konstante, im
    Bericht vollständig: Artikel-, Rechnungs-, Vorgangs-, Kennzeichen-, Serien-, Sendungs-, Kunden-, Bestell-,
    Auftrags-, Ticketnummern; je Wort aus D-4 mit gültiger Fremdnummer direkt dahinter (mit ` `, `: `, ` = `, groß,
    klein); Fremdnummern mit falscher Prüfziffer in neutralen Sätzen; gültige Fremdnummern mit verletzter Wortgrenze
    (jedes Zeichen aus AK-13), klein, mit Trenner, mit zwei Buchstaben, mit acht und zehn Ziffern → 0 KVNR-Funde.
  - `ak16c_zufallsFremdnummern_hoechstensZehnKommaFuenfProzent`: fester Seed, 100 000 Texte, je ein Satz aus
    Rauschwörtern (ohne KVNR-Kontextwort laut Referenz, ohne Wort aus D-4 direkt davor) mit `zufallsKvnr` an
    zufälliger Stelle; Zahl der KVNR-Funde von `KvnrRecognizer` ≤ 10 500. Messwert in der Meldung.
  - Selbsttests: Referenz als künstlicher Erkenner: (b) 0, (c) zwischen 9 500 und 10 500; künstlicher Erkenner ohne
    Prüfziffer → (c) schlägt an; ohne Negativkontext → (b) schlägt an; ohne Wortgrenzen → (b) schlägt an; mit
    Kleinbuchstaben → (b) schlägt an.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** auf dem Stand nach T-8 grün (0 Funde); Testlaufzeit im Bericht. Danach Regel 13.

#### T-10: KVNR ohne Kontextwort (Produkt) – Fehlalarm-Nachweis

- **Dateien:** neu `main/…/recognizer/kv/KvnrOhneKontext.java`; `main/…/recognizer/kv/KvnrRecognizer.java` (eine
  Zeile, Javadoc-Absatz); neu `test/…/recognizer/kv/KvnrOhneKontextTest.java`, neu
  `test/…/recognizer/kv/KvnrOhneKontextEigenschaftTest.java`; `test/…/KvStandTrennerDifferenzTest.java` (Methode
  `…OhneKontext…`: S-3 jetzt auch mit `kvnrFormReferenz`).
- **AK:** AK-11, AK-12, AK-13, AK-14, AK-15, AK-16 (Nachweis), AK-20 (1b).
- **Tests:** jedes Beispiel aus AK-11 bis AK-15 benannt (Bereich zehn Zeichen, 0,60, fraglich, Quelle
  `kvnr-ohne-kontext`; AK-15 über `standard()`); AK-13 je Zeichen der Liste davor und dahinter; AK-14 je Wort aus
  D-4 mit ` `, `:`, `=`, Groß/klein; AK-20 `<gültige KVNR>` allein: leere Übergabe → Text unverändert;
  Eigenschaftstest (10 000 Texte, fester Seed): Funde mit Quelle `kvnr-ohne-kontext` je Text gleich
  `kvnrOhneKontextReferenz` minus Überlappung mit 1a-Funden; Laufzeit Erkennerebene: `<gültige KVNR> ` wiederholt →
  ⌈10⁶ / 11⌉.
- **Bestandstests, die sich ändern:** keine. Ändert sich ein bestehender Test, gilt Regel 15.
- **Abnahme:** T-9 unverändert grün (Diff der eingefrorenen Teile leer); S-1 bis S-3 auf den Bestandsmengen ohne
  Abweichung; Zahlen (a), (b), (c) im Bericht.

#### T-11: 1b über die ganze Kette – Abschluss Teil 1b (nur Tests)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1g**: eigene Seeds, Generator, `HASH_TEIL_1G`; Blöcke
  KVNR ohne Kontextwort mit allen zulässigen Grenzzeichen, Gegenproben aus AK-12 bis AK-14 ohne Block, LANR-/IK-
  Kontext direkt vor einer KVNR-Form (K-1), Mischbausteine aus 1f mit eigenem `Random`);
  `test/…/RoundtripKorpusTest.java` (Methode `kvnrOhneKontextFaelle`); `test/…/GrenzlastTest.java` (Methode
  `ak22_kvnrOhneKontext_…`: `<gültige KVNR> ` wiederholt → ⌈10⁶ / 11⌉, über 80 000);
  `test/…/KlartextFreiheitTest.java` (Methode `…OhneKontext…`); `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1g,
  K-1 als exakter Messwert mit Mindestzahl); `test/…/recognizer/kv/KvnrGrenzenTest.java` (Methoden
  `bekannteGrenzeOhneKontext…`); Javadoc.
- **AK:** AK-16 (Bericht), AK-17 (1b), AK-22 (1b), AK-36 (1b).
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Inventar (Regel 14) im Bericht vollständig; Bericht „Teil 1b abgeschlossen“ mit den Prüfstandszahlen.

#### T-R: Rücknahme Teil 1b (nur bei Streichung nach D-5, auf Entscheidung des Nutzers)

- **Dateien:** alle Einträge des 1b-Inventars entfernen; neu in `KvnrGrenzenTest`:
  `bekannteGrenzeKvnrOhneKontextwort_keinFund` (gültige KVNR allein, im Satz → kein Fund); Javadoc „bekannte Grenze“.
- **Abnahme:** `KvnrRecognizer.detect` = `KvNumberScanner.detect(KvArt.KVNR, text)`; T-9 unverändert grün; alle
  Tests von Teil 1a unverändert grün; `grep` nach `kvnr-ohne-kontext` findet nur den Prüfstand.

### Teil 2 – BSNR

#### T-12: Regionalcodes und BSNR-Testdaten (Voraussetzung: OF-2-Nachtrag liegt vor)

- **Dateien:** neu `main/…/recognizer/kv/BsnrRegionalcodes.java` (Liste aus OF-2, Quellenvermerk nach AK-24
  sinngemäß); neu `test/…/recognizer/kv/BsnrRegionalcodesTest.java` (jeder Listencode bekannt, `00` und mindestens drei
  weitere unbekannt, Grenzwerte, Fehlerfälle ohne Werte in der Meldung); `test/…/SynthetischeKvNummern.java`
  (Abschnitt BSNR: `BSNR_BEKANNT`, `BSNR_BEKANNT_ZWEI`, `BSNR_UNBEKANNT` (Regionalcode `00`), Länge acht und zehn,
  `BSNR_NULL`, `BSNR_MARKER`; Bildung nach A-6; Regionalcode so gewählt, dass die Zahl nach LANR- **und** IK-Verfahren
  ungültig ist (Wächter; z. B. Code mit `4·z1 + 9·z2` nicht durch 10 teilbar, Stellen 8 und 9 verschieden));
  `test/…/SynthetischeKvNummernTest.java`; `test/…/TestdatenSynthetischTest.java`;
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`assertBsnrSynthetisch`: in Teil-2-Testtexten hat jede Neunerfolge ohne
  Präfix `00` die Ziffern `00000` an Stelle 3 bis 7).
- **AK:** AK-27 (Liste), AK-34.
- **Bestandstests, die sich ändern:** `TestdatenSynthetischTest`, Zulässigkeit (c): In der zentralen Datei ist eine
  Neunerfolge auch zulässig, wenn sie an Stelle 3 bis 7 `00000` hat (BSNR-Form nach A-6); Verstoßart
  `ZENTRALE_OHNE_PRAEFIX_00` bleibt für alle anderen; Selbsttest dazu. Keine Mindestzahl sinkt.

#### T-13: BSNR-Erkenner (Produkt, noch nicht in `standard()`)

- **Dateien:** `main/…/model/FindingType.java` (`BSNR`); `main/…/recognizer/kv/KvArt.java` (`BSNR`, `mitUmlaut`);
  `main/…/recognizer/kv/KvNumberScanner.java` (Faltung U+00C4 → U+00E4, Javadoc); neu
  `main/…/recognizer/kv/BsnrRecognizer.java`; neu `test/…/recognizer/kv/BsnrRecognizerTest.java`;
  `test/…/model/FindingTypeTest.java` (Ergänzung); `test/…/recognizer/kv/KvFensterTest.java` (Ergänzung: Läufe mit
  `Ä`/`ä` und LANR-/IK-Wort → gleich `KvStandTrenner`).
- **AK:** AK-25, AK-26, AK-27, AK-28, AK-29.
- **Tests:** jedes Beispiel aus AK-25 bis AK-29 benannt (auch `Betriebsstaettennummer`, `BETRIEBSSTÄTTE Nr.`,
  `Nebenbetriebsstaettennummer` mit 27 Buchstaben, Sperrschrift `B S N R`, `B. S. N. R.`, `N B S N R`, Weit-Form);
  Eigenschaftstest (≥ 1 000 Texte, fester Seed) wie T-5.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** E grün (LANR/IK identisch trotz Faltung); `KvArtBestandTest` grün.

#### T-14: Einbindung BSNR, Kollisionen (Produkt)

- **Dateien:** `main/…/recognizer/TextScanner.java`; `test/…/recognizer/TextScannerTest.java`;
  `test/…/recognizer/TextScannerKvTest.java` (Ergänzung); `test/…/KvStandTrennerDifferenzTest.java` (Ergänzung);
  `test/…/KlartextFreiheitTest.java` (Ergänzung `BSNR_MARKER`).
- **AK:** AK-30, AK-31, AK-32 (Bestandsschutz, Mensch entscheidet), AK-33 (Einbindung), AK-35.
- **Tests:** jede Zeile der Tabelle Kap. 2.2 mit BSNR; AK-30 Platzhalter je Typ und Rückübersetzung; AK-31 URL,
  Pfad, E-Mail; AK-32 fraglich-Fund nur bei Übergabe ersetzt; Text mit allen Funden der Standard-Erkenner; S-1 bis
  S-3 über alle Mengen, K-Zähler auf Bestandsmengen 0.
- **Bestandstests, die sich ändern:** `TextScannerTest.ak22_abgleichErkennerliste…`: Liste um `BsnrRecognizer.class`
  am Ende erweitert.

#### T-15: Fuzz-Teil 1h, Korpus, Grenzlast, Dokumentation – Abschluss Teil 2 (nur Tests und Javadoc)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1h**: eigene Seeds, Generator, `HASH_TEIL_1H`; BSNR-Blöcke
  mit allen Kontextwörtern, `ä`/`ae`, Schreibweisen, Kontextregeln, Formularfeld `BSNR/LANR`; gemischt mit LANR-,
  IK- und KVNR-Blöcken (nur 1a-Formen, Regel 12); kein Text enthält eine KVNR-Form außerhalb eines KVNR-Blocks mit
  Kontextwort – geprüft); `test/…/RoundtripKorpusTest.java` (BSNR-Fälle); `test/…/GrenzlastTest.java` (Methode
  `ak33_bsnr_…`); `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1h, K-2/K-3 als Messwert mit Mindestzahl);
  neu `test/…/recognizer/kv/BsnrGrenzenTest.java`; Javadoc `KvNumberScanner`, `BsnrRecognizer`, `KvArt`.
- **AK:** AK-32 (Roundtrip), AK-33, AK-34, AK-36 (Teil 2).
- **Tests (Grenzlast, je < 2 s, exakte Zahl):** `BSNR ` + nur Ziffern → 0; `BSNR ` + `1 ` wiederholt → 0; `BSNR` + nur
  `-` → 0; `BSNR ` + (`BSNR_NULL` + ` `) wiederholt → ⌈10⁶ / 10⌉ (über 70 000); `B S N R ` wiederholt → 0;
  (`BSNR_BEKANNT` + ` (BSNR) `) wiederholt → ⌈10⁶ / 17⌉.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Bericht „Teil 2 abgeschlossen“; Javadoc nennt alle Konfidenzen aus D-8 und die Grenzen aus Kap. 11.

---

## 10a. Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-4 | AK-13 | T-10 | AK-25 | T-13 |
| AK-2 | T-4 | AK-14 | T-10 | AK-26 | T-13 |
| AK-3 | T-4 | AK-15 | T-10 | AK-27 | T-12, T-13 |
| AK-4 | T-4, T-6 | AK-16 | T-9 (Prüfstand), T-10 (Nachweis), T-11 | AK-28 | T-13 |
| AK-5 | T-5 | AK-17 | T-7, T-11 | AK-29 | T-13 |
| AK-6 | T-3 | AK-18 | T-6 | AK-30 | T-14 |
| AK-7 | T-4 | AK-19 | T-1 (Werkzeug), T-6, T-7 | AK-31 | T-14 |
| AK-8 | T-4 | AK-20 | T-6, T-10 | AK-32 | T-1 (Werkzeug), T-14, T-15 |
| AK-9 | T-5 | AK-21 | T-6, T-7 | AK-33 | T-14, T-15 |
| AK-10 | T-4 | AK-22 | T-8, T-10, T-11 | AK-34 | T-12, T-15 |
| AK-11 | T-10 | AK-23 | T-3, T-8 | AK-35 | T-1, T-2, T-4 bis T-6, T-14 |
| AK-12 | T-10 | AK-24 | T-3 | AK-36 | T-8, T-11, T-15 |

Wird 1b nach D-5 gestrichen: T-10 und T-11 entfallen bzw. werden durch T-R zurückgenommen; AK-11 bis AK-16 entfallen
(der Prüfstand T-9 bleibt), die 1b-Anteile von AK-17, AK-20, AK-22 entfallen; Teil 1a und Teil 2 bleiben vollständig.

---

## 11. Bekannte Grenzen nach diesem Feature (je ein Test `bekannteGrenze…`)

**KVNR**
- **G-K1** (nur mit 1b): KVNR ohne Kontextwort mit ungültiger Prüfziffer, kleinem Buchstaben, Trennern, unsichtbarem
  Zeichen in der Nummer oder unmittelbar vorangestelltem Wort aus D-4 bleibt Klartext (E-3, D-4). Ohne 1b: jede KVNR
  ohne Kontextwort.
- **G-K2** (nur mit 1b, Folge): Fremdmuster aus Großbuchstabe + neun Ziffern mit zufällig passender Prüfziffer und
  ohne Negativkontext werden „fraglich“ gemeldet (etwa 10 %).
- **G-K3:** zwei Buchstaben, Nicht-ASCII-Buchstaben oder -Ziffern, Wörter außerhalb D-2 (`KVID`, `Versicherungsnummer`,
  `Krankenversicherungs-Nr.`) → kein KVNR-Fund.
- **G-K4:** Ziffern direkt an einem Wort (`KVNR` + Ziffern ohne Leerzeichen, `AB` + Ziffern) → kein KVNR-Fund.
- **G-K5:** Folgt einem Einzelbuchstaben der Sperrschrift direkt eine Ziffer (`K V N R` ohne Lücke vor den Ziffern),
  gilt er als Buchstabe einer Nummer; die Folge endet vorher (kein Kontextwort).
- **G-K6** (Folge, kein Fehler): `KVNR AB <Ziffern>` (zwei Buchstaben, dann Trenner) ergibt 0,40 über die reinen
  Ziffern (D-3).
- **G-K7:** `KV-Nr.` vor einer reinen Ziffernfolge → kein Fund (D-2, gewollt).
- **G-K8:** Unsichtbare Zeichen zwischen Buchstabe und Ziffern oder in der Nummer beenden die Kette (wie Bestand).

**BSNR**
- **G-B1:** BSNR ohne Kontextwort, in Tabellen unter Spaltenüberschrift oder hinter Leerzeile bleibt Klartext (E-2).
- **G-B2:** zerlegte Umlaute (`a` + U+0308) in `Betriebsstätte…` → kein Kontextwort.
- **G-B3:** Haupt- und Nebenbetriebsstätte sind nicht unterscheidbar (beide `BSNR`).

**Gemeinsam (D-6)**
- **G-D1:** Gleichstand der Konfidenz bei gleichem Bereich: `BSNR` gewinnt vor `IK`, `KVNR`, `LANR`; `KVNR` vor
  `LANR`; `IP` vor allen KV-Arten. Im Formularfeld `BSNR/LANR <B>/<L>` kann eine reale LANR, deren erste zwei Ziffern
  ein bekannter Regionalcode sind, als BSNR typisiert werden (beide 0,80). Die Position bleibt geschützt.
- **G-D2:** K-1: LANR-/IK-Kontextwort vor einer KVNR-Form ergibt einen KVNR-Fund über Buchstabe und Ziffern, mit 1b
  teils „fraglich“ statt bisher „wahrscheinlich“.
- Unverändert gelten alle Grenzen der Vorgänger (12R, 14a, 14b, 14cR, 14d, 14e, 14f, 14g, 14h-Rest, 15R, Grenzen 1 bis
  11 und 13) sinngemäß auch für KVNR und BSNR.

## 12. Risiken

- **R-1 Fehlalarm 1b:** ehrlich etwa 10 % fremder Buchstaben-Ziffern-Muster ohne Negativkontext. Gegenmittel: enge
  Regel, Prüfstand vor dem Code, D-5. Restrisiko: Fremdkontexte außerhalb der D-4-Liste (z. B. `Ticket-Nr.`,
  `Beleg`), die erst im Echtbetrieb auffallen.
- **R-2 Stufenverlust durch K-1:** Ein bisher als LANR 0,80 („wahrscheinlich“) erkannter Wert wird mit 1b zu KVNR 0,60
  („fraglich“), wenn er als KVNR-Form mit gültiger Prüfziffer dasteht. Das betrifft nur Texte mit LANR-/IK-Kontextwort
  direkt vor Buchstabe + neun Ziffern. Die Oberfläche ersetzt „fraglich“ nur nach Bestätigung; der Schutz hängt dann
  am Menschen. Ich halte das für vertretbar (die Form ist eindeutig eine KVNR-Form; die Stelle wird breiter geschützt),
  benenne es aber ausdrücklich.
- **R-3 Gemeinsamer Scanner:** Fenstergröße, Buchstabenform und Faltung ändern gemeinsamen Code. Gegenmittel: E
  (volle Gleichheit LANR/IK) gegen die neue Kopie in jedem Task, eigene Fenster-Tests, alle Siegel.
- **R-4 `KvArt` trotz früherer Sperre:** Die Kopien hängen an `KvArt`. Gegenmittel: Regel 11, `KvArtBestandTest`,
  Siegel.
- **R-5 Quellen (OF-1, OF-2):** Ein Irrtum im Prüfziffernverfahren ändert nur Stufen (1a) bzw. die Reichweite von 1b;
  ein Irrtum in der Regionalcode-Liste nur die Stufe der BSNR. Korrektur jeweils an einer Stelle plus Vektoren.
- **R-6 Gleichstand-Typen (G-D1):** In realen Formularen sind falsch typisierte, aber geschützte Positionen möglich.
- **R-7 Testlaufzeit:** 100 000 Texte (T-9), 10 000 (T-10), drei Fuzz-Teile → geschätzt +40 bis 90 s für
  `mvn clean verify`. Texte kurz halten (≤ 200 Zeichen), in (c) nur `KvnrRecognizer` aufrufen; eine Testklasse über
  60 s wird gemeldet.
- **R-8 Überschätzende Referenzen:** `kvnrKontextReferenz` nimmt auch zufällige Buchstabenfolgen als Kontext an und
  nimmt solche Texte aus S-3 aus. Gegenmittel: Mindestzahlen streng geprüfter Texte je Menge.

---

## 13. Hinweise zur Anforderung (entschieden in Richtung „möglichst viel schützen“, zur Kenntnis)

- **H-1 (AK-6 gegen A-6):** Ein Vektor, der die zweistellige Buchstabenwertung (A = 01) von einer einstelligen
  unterscheidet, braucht einen Buchstaben A bis I; mit `Z` allein ist das nicht prüfbar. Entscheidung: Die
  Prüfziffern-Vektoren dürfen andere Buchstaben verwenden, die Ziffern beginnen immer mit `0000` (A-6 sinngemäß); der
  Wächter prüft genau das. Restrisiko in der Testklasse vermerkt.
- **H-2 (AK-30 „dieselbe Zahl“):** siehe Kap. 2.2 – mit synthetischen Daten ergeben sich zwei gleiche Typen; geprüft
  wird, dass jeder Platzhalter den Typ seines Fundes trägt und zurückführt.
- **H-3 (`<BSNR ungültig>`):** Regionalcode `00` mit `00000` ergibt rechnerisch immer eine gültige LANR (Stellen 1 bis 7
  null). Ohne LANR-Kontextwort ohne Wirkung; in Kollisionstests wird das berücksichtigt.
- **H-4 (AK-16 (a) „alle bestehenden Tests“):** umgesetzt als Messung über die Textmengen aus T-9 plus „alle bestehenden
  Tests laufen unverändert grün“.
- **H-5 (AK-5 „zwei Buchstaben“):** gilt für direkt angeklebte Buchstaben. `KVNR AB <Ziffern>` liefert nach D-3 einen
  Fund 0,40 über die reinen Ziffern (Schutz geht vor), als Folge G-K6 dokumentiert.
- **H-6 (AK-22/AK-33 „über 70 000 Funde“):** Mit wiederholtem Kontextwort ergibt `KVNR <Nullnummer> ` nur 62 500
  Funde je Million Zeichen; die Zahl über 70 000 entsteht als Aufzählung (Kontextwort einmal vorn). Beide Varianten
  werden geprüft, jeweils mit exakter Formel.
- **H-7 (D-6 und Verschachtelung):** „Höhere Konfidenz gewinnt“ gilt nur bei gleichem Bereich; bei KVNR-Form gewinnt
  der umschließende KVNR-Fund (K-1). Die Anforderung schließt Änderungen am `ConflictResolver` aus; daher als
  Kollisionsfall benannt statt umgangen.
- **H-8 (OF-4):** `ä`/`ae` über `KvArt.mitUmlaut` und Faltung `Ä` → `ä`; Negativliste als Konstante in
  `KvnrOhneKontext` (fällt bei Rücknahme mit weg); keine interne Markierung der Quelle; 1b als eigene Klasse mit einer
  Einbindungszeile.
- **H-9 (Reihenfolge):** 1a → 1b → 2 nach E-1 (Patientendaten zuerst). Teil-2-Tests hängen nicht von 1b ab (Regel 12),
  eine spätere Rücknahme von 1b berührt Teil 2 nicht.

## 14. Abweichungen von Plan.md

Keine. (Kein neues Modul, keine neue Abhängigkeit, kein Netzwerk-Code; KVNR und BSNR sind in Plan.md Kap. 1/6 als
KV-Kennnummern vorgesehen.) Abweichung von einer Regel eines Vorgängerplans: `KvArt` wird additiv geändert (Regel 11,
Begründung Kap. 1 und R-4).

## 15. Offene Punkte für die Freigabe

1. **OF-1:** Quelle (Titel/URL, Abrufdatum) der KVNR-Prüfziffer als Nachtrag zur Anforderung **vor T-3**.
2. **OF-2:** Regionalcode-Liste der BSNR mit Quelle und Bestätigung „keine Prüfziffer“ **vor T-12**.
3. **K-1/R-2:** Zustimmung, dass ein LANR-/IK-Fund über den Ziffern einer KVNR-Form zum KVNR-Fund wird, auch wenn die
   Stufe dadurch von „wahrscheinlich“ auf „fraglich“ sinken kann (nur mit 1b).
4. **H-1:** Zustimmung zu Prüfziffer-Vektoren mit anderen Buchstaben als `Z` (Ziffern `0000…`).
5. **Reihenfolge 1a → 1b → 2** (H-9) und die Rücknahmeregel T-R.

## Nachtrag der Hauptsitzung vom 23.11.2026: Entscheidungen zu den offenen Punkten
- **OF-1, OF-2:** erledigt, siehe Nachtrag in `anforderung.md` (Quelle der KVNR-Prüfziffer mit Testvektor; BSNR nach
  KBV-Richtlinie Stand 1. Januar 2024: keine Prüfziffer, 78 Schlüssel aus Anlage 1, Sonderkennungen `35`/`75` mit
  Schlüssel an den Stellen drei und vier, `99` = Netzverbundnummer gilt als unbekannter Code).
- **H-1:** angenommen. Die Prüfziffer-Vektoren (AK-6) dürfen andere Buchstaben als `Z` verwenden; die acht Ziffern
  beginnen weiter mit `0000`. Das Lehrbeispiel der Quelle (Buchstabe `A`, Ziffern eins bis acht aufsteigend,
  Prüfziffer 0) ist als einziger Vektor ohne `0000` zugelassen und steht nur in der zentralen Testklasse.
- **K-1 / R-2:** angenommen. Mit Teil 1b darf ein LANR-/IK-Fund über den Ziffern einer KVNR-Form zum breiteren
  KVNR-Fund werden, auch wenn die Stufe dabei von 0,80 auf 0,60 sinkt; die Position bleibt geschützt. Voraussetzung
  bleibt, dass die Oberfläche „fragliche" Funde nicht automatisch abwählt (Hinweis für das UI-Feature).
- **Reihenfolge:** 1a → 1b → 2 wie vorgeschlagen; Rücknahme von 1b nach D-5 über T-R, der Nutzer wird informiert.
- **Nachtrag zu T-1 (Befund des engineers, für T-9/T-10 verbindlich):** Die Annahme aus Kap. 5.2, die Bestandsmengen
  enthielten keine KVNR-Form, trifft für die weit gefasste `kvnrFormReferenz` (Großbuchstabe direkt vor genau neun
  Ziffern, ohne Wortgrenzen) nicht zu: 173 Texte (Teil 1c 58, 1d 27, 1e 44, Korpus 1, Generatoren 43), meist ein
  einzelnes `K` aus der Sperrschrift vor der Nummer. Entscheidung: Die weite Referenz und ihre Messwerte aus T-1
  bleiben unverändert. T-9 ergänzt in `KvnrBsnrHilfen` eine **enge** Referenz nach E-3/AK-12/AK-13 (Großbuchstabe
  mit Wortgrenze davor, genau neun Ziffern, Wortgrenze dahinter, gültige Prüfziffer, unabhängig vom Produkt
  geschrieben) und hält fest, wie viele Bestandstexte sie trifft. Ab T-10 gilt für S-3 und K-1/K-4 die enge
  Referenz als Bedingung „KVNR-Form"; Texte, die nur die weite Referenz trifft, bleiben streng geprüft. Trifft die
  enge Referenz Bestandstexte, sind das Kandidaten für D-5 (a) („0 zusätzliche KVNR-Funde auf den Bestandskorpora")
  und werden in T-9 einzeln ausgewiesen, nicht ausgenommen.
- **Nachtrag 2 zu T-1 (security-Befund „mittel", Entscheidung der Hauptsitzung; geht dem Nachtrag davor und der
  Tabelle in Kap. 5.2 vor, wo er strenger ist):** Die Prüfregel `bestandsabweichungen` wird verschärft.
  1. **K-4** gilt nur, wenn alle drei Bedingungen erfüllt sind: der neue Fund ist echt größer als der alte
     (`start` kleiner oder `ende` größer); sein Typ ist KVNR, BSNR oder der Typ eines alten Fundes, der ganz im
     neuen liegt; seine Konfidenz ist mindestens `min(alte Konfidenz, 0,40)`.
  2. **K-1** gilt nur, wenn die neue Konfidenz mindestens `min(alte Konfidenz, 0,50)` ist.
  3. **Örtliche Bedingung statt „im Text":** K-1 bis K-4 und die Ausnahme von S-3 verlangen das Kontextwort bzw. die
     KVNR-Form in der Nähe der Fundstelle: innerhalb des neuen Fundes oder höchstens 120 Zeichen vor seinem Anfang
     oder hinter seinem Ende. S-3 (volle Gleichheit) gilt für jeden alten und neuen Fund, in dessen Nähe (gleiches
     Fenster) weder Kontextwort noch Form steht. Erweist sich das Fenster später an einem plangemäßen Fall als zu
     eng, hält der engineer an und berichtet; er ändert es nicht selbst.
  4. **Enge KVNR-Form schon jetzt:** `KvnrBsnrHilfen` bekommt neben der weiten Referenz (die auf **genau** neun
     Ziffern korrigiert wird: keine zehnte Ziffer dahinter) eine enge Referenz nach E-3: Großbuchstabe A–Z, davor
     kein Buchstabe und keine Ziffer, genau neun Ziffern, dahinter kein Buchstabe und keine Ziffer, Prüfziffer nach
     A-1 gültig (eigene, vom Produkt unabhängige Rechnung im Testcode, abgesichert mit dem Lehrbeispiel der Quelle).
     Als Bedingung „KVNR-Form" für K-1, K-4 und S-3 gilt ab sofort die enge Referenz. Die weite Referenz bleibt nur
     als Messwert (Zahl der Bestandstexte) erhalten. Die Treffer der engen Referenz auf den Bestandsmengen werden
     je Menge exakt festgehalten.
  5. **Siegel über `ALT_TRENNER`:** je Bestandsmenge ein Siegel über die vollständige Ausgabe (alle Typen, alle
     Felder), damit eine Änderung an den übrigen Erkennern, am `TextScanner` oder am `ConflictResolver` auffällt.
  6. Die Prüfung „K-1 bis K-4 = 0 auf den Bestandsmengen" bleibt in allen Tasks bestehen. Auf den neuen Fuzz-Teilen
     1f bis 1h werden Kollisionsfälle nicht nur gezählt: jeder gezählte Fall muss zusätzlich die Bedingungen 1 bis 3
     erfüllen (das leistet die Prüfregel selbst).
- **Nachtrag 3 zu T-1 (security-Hinweis „niedrig" und reviewer-Mangel aus Durchlauf 2; verbindlich):**
  1. **K-4, Typ:** Ist der alte Fund kein KV-Typ (EMAIL, IP, URL, PFAD, TEXT …), muss der neue zusammengelegte Fund
     den Typ eines alten Fundes haben, der ganz in ihm liegt; KVNR/BSNR als neuer Typ ist dann kein K-4, sondern ein
     Verstoß. Für alte LANR-/IK-Funde bleibt KVNR/BSNR zulässig.
  2. **K-4, Ausdehnung:** Höchstens 24 Positionen des neuen Fundes dürfen von keinem alten Fund abgedeckt sein
     (Obergrenze für eine KVNR-/BSNR-Schreibweise mit Trennern). Größere Zuwächse sind ein Verstoß.
  3. **Ab dem ersten Task mit neuen Erkennern im Scanner (T-6, T-14) und auf den Fuzz-Teilen 1f bis 1h** wird jeder
     K-4-Fall zusätzlich unabhängig nachgerechnet: Kandidaten des alten Scanners und der neuen Erkenner mit
     `ConflictResolver.resolve` zusammenführen und Bereich, Typ und Konfidenz mit dem Produktfund vergleichen.
     Zusätzlich wird dort gezählt, wie viele Kollisionsfälle nur über verstreute Buchstaben im Fenster als „nahe"
     gelten (Kontextwort nicht als zusammenhängendes Wort im Fenster); der Wert wird exakt festgehalten.
  4. **Siegel über `ALT_TRENNER`:** Der Originalwert jedes Fundes geht in den Hash ein (eigene Hash-Variante, damit
     die Kopie-Siegel unverändert bleiben); zusätzlich wird die Invariante „Originalwert = Textausschnitt" auch für
     die alten Funde geprüft.
  5. Erweist sich eine dieser Grenzen später an einem plangemäßen Fall als zu eng, hält der engineer an und
     berichtet; er ändert sie nicht selbst.
